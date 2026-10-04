package com.freshflow.insight.presentation;

import com.freshflow.insight.application.BriefingOrchestrator;
import com.freshflow.insight.application.BriefingQueryService;
import com.freshflow.insight.application.EndOfDayBriefing;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.LocalDate;

/**
 * spec-04 {@code createBriefing} and {@code getBriefing} &mdash;
 * {@code POST /api/insight/briefings} and {@code GET /api/insight/briefings/&#123;briefingId&#125;}
 * (FR15).
 *
 * <p>Presentation only. It maps a request to the orchestrator's one argument, a briefing to a
 * DTO, an absent briefing to 404, and an absent orchestrator to 503. It decides nothing else
 * (spec-01 &sect;2.5 forbids putting a rule in a controller in every service, under every
 * pattern); which day is "today", what the counts are and what a blank narrative becomes are all
 * the orchestrator's.
 *
 * <p><b>The orchestrator is resolved through an {@link ObjectProvider}</b>, as FR14's trigger
 * consumer resolves its orchestrator, because the bean exists only when an LLM is configured
 * ({@code BriefingAgentConfiguration}). With no key there is no bean, and spec-02 FR15 says what
 * the endpoint must then do: answer 503, while the service otherwise runs normally. The GET needs
 * only {@link BriefingQueryService}, which always exists, and is that "otherwise".
 *
 * <p>Both answers are given by a handler and never by the absence of a mapping; the endpoint
 * tests check the handler type on the 404 for exactly that reason.
 */
@RestController
@RequestMapping("/api/insight/briefings")
public class BriefingController {

    /** spec-04's {@code ErrorResponse.error} for the 503, named so a client can match on it. */
    static final String AGENT_NOT_CONFIGURED = "AGENT_NOT_CONFIGURED";

    private final ObjectProvider<BriefingOrchestrator> orchestrators;
    private final BriefingQueryService briefingQueryService;

    public BriefingController(ObjectProvider<BriefingOrchestrator> orchestrators,
                              BriefingQueryService briefingQueryService) {
        this.orchestrators = orchestrators;
        this.briefingQueryService = briefingQueryService;
    }

    /**
     * spec-04 {@code createBriefing}: 201 with the briefing, or 503 when no agent is wired.
     *
     * @param request optional; its {@code businessDate} is optional too and defaults to today in
     *                UTC inside the orchestrator
     */
    @PostMapping
    public ResponseEntity<?> createBriefing(
            @RequestBody(required = false) BriefingRequestDto request) {

        BriefingOrchestrator orchestrator = orchestrators.getIfAvailable();
        if (orchestrator == null) {
            // spec-02 FR15, spec-04: no LLM is configured in this environment, so no briefing
            // agent is wired. The rest of the service is unaffected, and so is the GET below.
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new ErrorResponseDto(AGENT_NOT_CONFIGURED,
                            "No LLM is configured in this environment (freshflow.llm.api-key is "
                                    + "empty), so no briefing agent is wired. Set LLM_API_KEY and "
                                    + "LLM_MODEL and restart insight-service to enable "
                                    + "POST /api/insight/briefings."));
        }

        LocalDate businessDate = request == null ? null : request.businessDate();
        EndOfDayBriefing briefing = orchestrator.brief(businessDate);

        return ResponseEntity
                .created(URI.create("/api/insight/briefings/" + briefing.briefingId()))
                .body(toDto(briefing));
    }

    /** spec-04 {@code getBriefing}: 200 with the briefing, or 404 when no briefing has that id. */
    @GetMapping("/{briefingId}")
    public ResponseEntity<BriefingResponseDto> getBriefing(@PathVariable String briefingId) {
        return briefingQueryService.findById(briefingId)
                .map(BriefingController::toDto)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * A rename, and nothing else. The tool calls arrive ordered by {@code sequence} from the store
     * and are not re-sorted here, for the reason {@code AgentTraceController} gives.
     */
    private static BriefingResponseDto toDto(EndOfDayBriefing briefing) {
        return new BriefingResponseDto(
                briefing.briefingId(),
                briefing.businessDate(),
                briefing.generatedAt(),
                briefing.summary(),
                briefing.riskCount(),
                briefing.recommendationCount(),
                briefing.toolCalls().stream()
                        .map(call -> new BriefingResponseDto.ToolCall(
                                call.sequence(),
                                call.toolName(),
                                call.arguments(),
                                call.result(),
                                call.timestamp()))
                        .toList());
    }
}
