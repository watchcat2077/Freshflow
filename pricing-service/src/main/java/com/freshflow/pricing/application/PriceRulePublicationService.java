package com.freshflow.pricing.application;

import com.freshflow.pricing.domain.PriceRule;
import com.freshflow.pricing.domain.PriceRuleEventPublisher;
import com.freshflow.pricing.domain.Sku;
import com.freshflow.pricing.infrastructure.repository.PriceRuleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

/**
 * Application service (spec-01 &sect;2.1) &mdash; the use case "announce the price rules in force"
 * that makes {@code pricing-service} the producer of {@code price-rule-published} (spec-01
 * &sect;2.6).
 *
 * <p>A separate class from {@link PriceApplicationService} rather than another method on it:
 * The layering rule (spec-01 s2.1, L5 slide 9) prefers one handler per use case over a bloated application service, and reading a
 * price is a different use case from announcing one.
 *
 * <p>It orchestrates and decides nothing. It retrieves the {@link PriceRule} from the domain's own
 * repository interface, asks the rule for its event, and hands that to an outbound port. Which
 * fields the event carries is the domain's answer, not this layer's; where the event goes is the
 * adapter's answer, not this layer's. This service does not know a broker exists.
 *
 * <p>{@code occurredAt} is stamped here from the injected {@link Clock} &mdash; producer-assigned
 * event time, as spec-01 &sect;2.6 requires. It is never left to the broker, and never read inside
 * the domain.
 */
@Service
public class PriceRulePublicationService {

    private static final Logger log = LoggerFactory.getLogger(PriceRulePublicationService.class);

    private final PriceRuleRepository priceRuleRepository;
    private final PriceRuleEventPublisher priceRuleEventPublisher;
    private final Clock clock;

    public PriceRulePublicationService(PriceRuleRepository priceRuleRepository,
                                       PriceRuleEventPublisher priceRuleEventPublisher,
                                       Clock clock) {
        this.priceRuleRepository = priceRuleRepository;
        this.priceRuleEventPublisher = priceRuleEventPublisher;
        this.clock = clock;
    }

    /**
     * Announces the rule currently in force for one SKU. A SKU with no rule is not an error here:
     * there is simply nothing to announce, and saying so is the whole of the response.
     *
     * @return true if an event was published
     */
    @Transactional(readOnly = true)
    public boolean announce(String sku) {
        if (sku == null || sku.isBlank()) {
            return false;
        }
        return priceRuleRepository.findBySku(Sku.of(sku))
                .map(rule -> {
                    priceRuleEventPublisher.publish(rule.toPriceRulePublishedEvent(clock.instant()));
                    return true;
                })
                .orElseGet(() -> {
                    log.warn("No price rule in force for SKU {}; nothing announced.", sku);
                    return false;
                });
    }

    /** Announces several SKUs, and reports how many events actually went out. */
    public int announceAll(List<String> skus) {
        int published = 0;
        for (String sku : skus) {
            if (announce(sku)) {
                published++;
            }
        }
        return published;
    }
}
