package com.freshflow.pricing.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * JPA entity for {@code MarkdownPlan}.
 *
 * <p>This class exists precisely so that the aggregate does not have to. spec-01 &sect;2.3: for a
 * service designated Domain Model, "domain classes are POJO, persistence ignorant. NO JPA
 * annotations on domain classes. A separate set of JPA entities plus mappers lives in
 * infrastructure."
 *
 * <p>Every field is a primitive or a JDBC-friendly type. {@code discountTier}, {@code status} and
 * {@code origin} are stored as Strings rather than as the domain enums so that the persistence
 * model carries no domain type at all. The monetary columns follow spec-03 &sect;7's storage note
 * ({@code precision 19, scale 4}); nothing reads them without passing through {@code Money}.
 *
 * <p>The table and column names are the ones the team's database diagram is drawn from; change
 * them and the diagram together or not at all.
 */
@Entity
@Table(name = "markdown_plan")
public class MarkdownPlanJpaEntity {

    @Id
    @Column(name = "markdown_plan_id", nullable = false, updatable = false)
    private String markdownPlanId;

    @Column(name = "batch_id", nullable = false)
    private String batchId;

    @Column(name = "sku", nullable = false)
    private String sku;

    @Column(name = "discount_tier", nullable = false, length = 16)
    private String discountTier;

    @Column(name = "effective_from", nullable = false)
    private Instant effectiveFrom;

    @Column(name = "effective_to", nullable = false)
    private Instant effectiveTo;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "origin", nullable = false, length = 16)
    private String origin;

    @Column(name = "agent_trace_id")
    private String agentTraceId;

    @Column(name = "rationale", length = 2000)
    private String rationale;

    @Column(name = "rejection_reason", length = 1000)
    private String rejectionReason;

    @Column(name = "discounted_price_amount", precision = 19, scale = 4)
    private BigDecimal discountedPriceAmount;

    @Column(name = "discounted_price_currency", length = 3)
    private String discountedPriceCurrency;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected MarkdownPlanJpaEntity() {
        // required by JPA
    }

    public MarkdownPlanJpaEntity(String markdownPlanId, String batchId, String sku, String discountTier,
                                 Instant effectiveFrom, Instant effectiveTo, String status, String origin,
                                 String agentTraceId, String rationale, String rejectionReason,
                                 BigDecimal discountedPriceAmount, String discountedPriceCurrency,
                                 Instant createdAt) {
        this.markdownPlanId = markdownPlanId;
        this.batchId = batchId;
        this.sku = sku;
        this.discountTier = discountTier;
        this.effectiveFrom = effectiveFrom;
        this.effectiveTo = effectiveTo;
        this.status = status;
        this.origin = origin;
        this.agentTraceId = agentTraceId;
        this.rationale = rationale;
        this.rejectionReason = rejectionReason;
        this.discountedPriceAmount = discountedPriceAmount;
        this.discountedPriceCurrency = discountedPriceCurrency;
        this.createdAt = createdAt;
    }

    public String getMarkdownPlanId() {
        return markdownPlanId;
    }

    public String getBatchId() {
        return batchId;
    }

    public String getSku() {
        return sku;
    }

    public String getDiscountTier() {
        return discountTier;
    }

    public Instant getEffectiveFrom() {
        return effectiveFrom;
    }

    public Instant getEffectiveTo() {
        return effectiveTo;
    }

    public String getStatus() {
        return status;
    }

    public String getOrigin() {
        return origin;
    }

    public String getAgentTraceId() {
        return agentTraceId;
    }

    public String getRationale() {
        return rationale;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public BigDecimal getDiscountedPriceAmount() {
        return discountedPriceAmount;
    }

    public String getDiscountedPriceCurrency() {
        return discountedPriceCurrency;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
