package com.freshflow.insight.presentation;

import com.freshflow.insight.application.AgentTraceStore;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * spec-04 {@code getAgentTrace} &mdash;
 * {@code GET /api/insight/agent-traces/&#123;agentTraceId&#125;} (FR14).
 *
 * <p>This endpoint is FR14's auditability criterion in its entirety: "full tool-call trace
 * persisted and queryable by {@code agentTraceId}". It is also what makes an automated markdown
 * reviewable after the fact &mdash; {@code markdown-recommended} carries the trace id, so a
 * proposal that Pricing accepted, and one it rejected, can each be traced back to the evidence the
 * agent gathered and the reasoning it gave.
 *
 * <p>Presentation only. It maps a stored trace to a DTO and an absent one to 404, and decides
 * nothing (spec-01 &sect;2.5 forbids putting a rule in a controller in every service, under every
 * pattern).
 *
 * <p><b>Phase A: signature only.</b> The body is Phase B work.
 */
@RestController
@RequestMapping("/api/insight/agent-traces")
public class AgentTraceController {

    private final AgentTraceStore agentTraceStore;

    public AgentTraceController(AgentTraceStore agentTraceStore) {
        this.agentTraceStore = agentTraceStore;
    }

    /**
     * @param agentTraceId the identifier carried on {@code markdown-recommended}
     * @return 200 with the trace, or spec-04's 404 when no trace has that identifier
     */
    @GetMapping("/{agentTraceId}")
    public ResponseEntity<AgentTraceResponseDto> getAgentTrace(@PathVariable String agentTraceId) {
        throw new UnsupportedOperationException("FR14 Phase B");
    }
}
