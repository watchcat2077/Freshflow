package com.freshflow.insight.infrastructure;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * JPA entity for FR14's agent trace.
 *
 * <p>It exists so that {@code AgentTraceRecord} does not have to carry JPA annotations. spec-01
 * &sect;2.2 forbids the {@code application} package from importing JPA, and this service keeps its
 * read models there because spec-01 &sect;2.3 gives it no {@code domain} package. The rule that
 * produces a separate entity plus a mapper for {@code inventory-service}'s aggregate produces one
 * here for a read model, for the same reason and by the same route.
 *
 * <p><b>{@code triggerEventId} is unique, and the constraint is the idempotency rule made
 * physical.</b> spec-01 &sect;2.6 requires deduplication on {@code eventId};
 * {@code MarkdownAgentOrchestrator} checks for an existing trace before it does anything, and this
 * constraint is what makes that check hold even if two deliveries are handled concurrently. A rule
 * enforced only by a read-then-write is a rule with a race in it.
 *
 * <p><b>{@code toolCalls} is ordered by {@code sequence}, in the mapping.</b> spec-04 declares
 * {@code sequence} on each call and a consumer may sort by it, so the order must survive a
 * round trip through the database. Without {@code @OrderBy} the order would be whatever the
 * database happened to return, which is not an order at all &mdash; and the endpoint would be
 * correct in testing and wrong in production for no visible reason.
 */
@Entity
@Table(name = "agent_trace",
        uniqueConstraints = @UniqueConstraint(name = "uk_agent_trace_trigger_event",
                columnNames = "trigger_event_id"))
public class AgentTraceEntity {

    @Id
    @Column(name = "agent_trace_id", nullable = false, updatable = false)
    private String agentTraceId;

    @Column(name = "trigger_event_id", nullable = false, updatable = false)
    private String triggerEventId;

    @Column(name = "batch_id", nullable = false)
    private String batchId;

    @OneToMany(mappedBy = "trace", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sequence ASC")
    private List<ToolCallEntity> toolCalls = new ArrayList<>();

    @Column(name = "final_recommendation", length = 512)
    private String finalRecommendation;

    @Lob  // text a language model writes or reads; no natural upper bound (5 October 2026)
    @Column(name = "rationale")
    private String rationale;

    @Column(name = "completed_at", nullable = false)
    private Instant completedAt;

    protected AgentTraceEntity() {
        // required by JPA
    }

    public AgentTraceEntity(String agentTraceId, String triggerEventId, String batchId,
                            String finalRecommendation, String rationale, Instant completedAt) {
        this.agentTraceId = agentTraceId;
        this.triggerEventId = triggerEventId;
        this.batchId = batchId;
        this.finalRecommendation = finalRecommendation;
        this.rationale = rationale;
        this.completedAt = completedAt;
    }

    /**
     * Adds a tool call and sets both ends of the association, which is the only way the
     * {@code mappedBy} side is ever written.
     */
    public void addToolCall(ToolCallEntity toolCall) {
        toolCall.setTrace(this);
        this.toolCalls.add(toolCall);
    }

    public String getAgentTraceId() {
        return agentTraceId;
    }

    public String getTriggerEventId() {
        return triggerEventId;
    }

    public String getBatchId() {
        return batchId;
    }

    public List<ToolCallEntity> getToolCalls() {
        return toolCalls;
    }

    public String getFinalRecommendation() {
        return finalRecommendation;
    }

    public String getRationale() {
        return rationale;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
