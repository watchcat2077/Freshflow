package com.freshflow.pricing.infrastructure;

import com.freshflow.contracts.MarkdownRecommendedEvent;
import com.freshflow.pricing.application.CreateMarkdownPlanCommand;
import com.freshflow.pricing.application.MarkdownPlanApplicationService;
import com.freshflow.pricing.application.MarkdownPlanOutcome;
import com.freshflow.pricing.domain.MarkdownPlan;
import com.freshflow.pricing.domain.ProposalOrigin;
import com.freshflow.pricing.infrastructure.repository.MarkdownPlanRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * FR9/FR11's inbound adapter &mdash; turns a {@code markdown-recommended} integration event into the
 * application layer's command, once per agent deliberation.
 *
 * <p>Deliberately separate from {@link PricingMessagingConfiguration}, which holds the Kafka binding
 * and no logic. This class holds the logic and imports no Kafka type, so what it does can be
 * exercised without a broker. Same split as {@code inventory-service}'s
 * {@code ItemSoldMessageHandler}.
 *
 * <p><b>One application service, one domain rule, two entry points.</b> The command built here is
 * the same {@link CreateMarkdownPlanCommand} that {@code POST /api/markdown-plans} builds from a
 * request body, with {@code origin} fixed to {@code AGENT}. Nothing about the margin floor is
 * known here; the Pricing domain applies it to this proposal exactly as it would to a
 * supervisor's (FR11: "applies identically whether the proposal originated from a human or from
 * the agent").
 *
 * <p><b>Idempotent on {@code agentTraceId}</b>, not on {@code eventId}. A redelivery of the same
 * message and a re-publication under a fresh {@code eventId} both describe the one deliberation
 * FR14 recorded (spec-06 &sect;5 makes the agent idempotent per trigger), so the trace is the
 * natural key: one deliberation, one plan. The lookup and the record share one transaction.
 * A recommendation with no trace id is unauditable &mdash; FR14's acceptance criteria require the
 * trace &mdash; and is ignored rather than recorded.
 *
 * <p><b>It never throws on a refusal.</b> A plan rejected by the floor is a normal outcome of FR11,
 * recorded as REJECTED and logged as {@code [fr11-rejected]}; a recommendation the application
 * layer cannot accept at all (malformed, or for a SKU with no rule) is logged and dropped. An
 * exception here would make Spring Cloud Stream retry and then block the partition behind a
 * message that will never be accepted.
 *
 * <p>The inbound side of the Anti-Corruption Layer between the agent and this context (spec-01
 * s1.5): the counterpart of the {@code application/outboundservices/acl} package in the
 * coordinator's {@code cargo-tracker-0.1}. This handler only translates the integration event
 * into the application command; the enforcement stays in the domain's
 * {@code MarginFloorPolicy}, by the same path a supervisor's request takes.
 */
@Component
public class MarkdownRecommendedMessageHandler {

    private static final Logger log = LoggerFactory.getLogger(MarkdownRecommendedMessageHandler.class);

    private final MarkdownPlanRepository markdownPlanRepository;
    private final MarkdownPlanApplicationService markdownPlanApplicationService;

    public MarkdownRecommendedMessageHandler(MarkdownPlanRepository markdownPlanRepository,
                                             MarkdownPlanApplicationService markdownPlanApplicationService) {
        this.markdownPlanRepository = markdownPlanRepository;
        this.markdownPlanApplicationService = markdownPlanApplicationService;
    }

    /**
     * Consumes one {@code markdown-recommended} event.
     *
     * @return true if a plan was recorded for this recommendation (activated or rejected), false if
     *         it was skipped as a duplicate, ignored as malformed, or dropped as unacceptable
     */
    @Transactional
    public boolean handle(MarkdownRecommendedEvent event) {
        if (event == null || event.eventId() == null
                || event.agentTraceId() == null || event.agentTraceId().isBlank()) {
            log.warn("[ignored] topic={} malformed or untraceable event: {}",
                    MarkdownRecommendedEvent.TOPIC, event);
            return false;
        }

        Optional<MarkdownPlan> existing = markdownPlanRepository.findByAgentTraceId(event.agentTraceId());
        if (existing.isPresent()) {
            MarkdownPlan plan = existing.get();
            log.info("[duplicate] topic={} eventId={} agentTraceId={} already recorded as {} plan {}; skipped",
                    MarkdownRecommendedEvent.TOPIC, event.eventId(), event.agentTraceId(),
                    plan.status(), plan.markdownPlanId().value());
            return false;
        }

        CreateMarkdownPlanCommand command = new CreateMarkdownPlanCommand(
                event.batchId(),
                event.sku(),
                event.discountTier() == null ? null : event.discountTier().name(),
                event.effectiveFrom(),
                event.effectiveTo(),
                ProposalOrigin.AGENT.name(),
                fitRationale(event.rationale()),
                event.agentTraceId());

        MarkdownPlanOutcome outcome = markdownPlanApplicationService.create(command);
        return switch (outcome) {
            case MarkdownPlanOutcome.Activated activated -> {
                log.info("[fr9-activated] topic={} eventId={} agentTraceId={} plan={} batchId={} sku={} "
                                + "tier={} discountedPrice={} {}",
                        MarkdownRecommendedEvent.TOPIC, event.eventId(), event.agentTraceId(),
                        activated.plan().markdownPlanId(), activated.plan().batchId(),
                        activated.plan().sku(), activated.plan().discountTier(),
                        activated.plan().discountedPriceAmount(), activated.plan().discountedPriceCurrency());
                yield true;
            }
            case MarkdownPlanOutcome.Rejected rejected -> {
                log.info("[fr11-rejected] topic={} eventId={} agentTraceId={} plan={} batchId={} sku={} "
                                + "tier={} reason=\"{}\"",
                        MarkdownRecommendedEvent.TOPIC, event.eventId(), event.agentTraceId(),
                        rejected.plan().markdownPlanId(), rejected.plan().batchId(),
                        rejected.plan().sku(), rejected.plan().discountTier(),
                        rejected.rejection().message());
                yield true;
            }
            case MarkdownPlanOutcome.Invalid invalid -> {
                log.warn("[dropped] topic={} eventId={} agentTraceId={} not a valid proposal: {} {}",
                        MarkdownRecommendedEvent.TOPIC, event.eventId(), event.agentTraceId(),
                        invalid.code(), invalid.message());
                yield false;
            }
            case MarkdownPlanOutcome.PriceRuleNotFound notFound -> {
                log.warn("[dropped] topic={} eventId={} agentTraceId={} sku={}: {}",
                        MarkdownRecommendedEvent.TOPIC, event.eventId(), event.agentTraceId(),
                        notFound.sku(), notFound.message());
                yield false;
            }
        };
    }

    /**
     * The rationale column holds 2000 characters. A model-written rationale that runs longer loses
     * its tail here rather than costing the whole recommendation; the full text is in the agent
     * trace, which is the audit record and is keyed by the same {@code agentTraceId}.
     */
    private static String fitRationale(String rationale) {
        int max = MarkdownPlanApplicationService.RATIONALE_MAX_LENGTH;
        if (rationale == null || rationale.length() <= max) {
            return rationale;
        }
        log.warn("[truncated] rationale of {} characters cut to {}", rationale.length(), max);
        return rationale.substring(0, max);
    }
}
