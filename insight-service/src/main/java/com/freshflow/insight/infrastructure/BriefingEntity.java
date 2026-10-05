package com.freshflow.insight.infrastructure;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * JPA entity for FR15's end-of-day briefing.
 *
 * <p>It exists so that {@code EndOfDayBriefing} does not have to carry JPA annotations: spec-01
 * &sect;2.2 forbids the {@code application} package from importing JPA, and the read model lives
 * there (ADR 0006 keeps the persisted records of what the agents did on the read side). The same
 * construction as {@link AgentTraceEntity}, for the same reason and by the same route.
 *
 * <p><b>{@code toolCalls} is ordered by {@code sequence}, in the mapping.</b> spec-04 declares
 * {@code sequence} on each call of {@code BriefingResponse.toolCalls} exactly as it does on the
 * agent trace, so the order must survive the round trip through the database; without
 * {@code @OrderBy} it would be whatever the database happened to return (spec-06 &sect;6).
 *
 * <p>{@code businessDate} is a {@code DATE} column: it is a calendar day, not an instant, and
 * storing it as one keeps the UTC-day rule in {@code BriefingRequest} the only place that turns a
 * date into instants.
 */
@Entity
@Table(name = "briefing")
public class BriefingEntity {

    @Id
    @Column(name = "briefing_id", nullable = false, updatable = false)
    private String briefingId;

    @Column(name = "business_date", nullable = false)
    private LocalDate businessDate;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    @Lob  // text a language model writes or reads; no natural upper bound (5 October 2026)
    @Column(name = "summary")
    private String summary;

    @Column(name = "risk_count", nullable = false)
    private int riskCount;

    @Column(name = "recommendation_count", nullable = false)
    private int recommendationCount;

    @OneToMany(mappedBy = "briefing", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sequence ASC")
    private List<BriefingToolCallEntity> toolCalls = new ArrayList<>();

    protected BriefingEntity() {
        // required by JPA
    }

    public BriefingEntity(String briefingId, LocalDate businessDate, Instant generatedAt,
                          String summary, int riskCount, int recommendationCount) {
        this.briefingId = briefingId;
        this.businessDate = businessDate;
        this.generatedAt = generatedAt;
        this.summary = summary;
        this.riskCount = riskCount;
        this.recommendationCount = recommendationCount;
    }

    /**
     * Adds a tool call and sets both ends of the association, which is the only way the
     * {@code mappedBy} side is ever written.
     */
    public void addToolCall(BriefingToolCallEntity toolCall) {
        toolCall.setBriefing(this);
        this.toolCalls.add(toolCall);
    }

    public String getBriefingId() {
        return briefingId;
    }

    public LocalDate getBusinessDate() {
        return businessDate;
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }

    public String getSummary() {
        return summary;
    }

    public int getRiskCount() {
        return riskCount;
    }

    public int getRecommendationCount() {
        return recommendationCount;
    }

    public List<BriefingToolCallEntity> getToolCalls() {
        return toolCalls;
    }
}
