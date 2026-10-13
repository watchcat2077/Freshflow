package com.freshflow.pricing.application;

import java.time.Instant;

/**
 * Command (spec-01 s2.1, L5 slide 7: a simple DTO handled in the application service layer, which does not enter
 * the domain layer) &mdash; propose a markdown for a batch and have it activated or refused (FR9,
 * FR11).
 *
 * <p>Primitive-typed on purpose: it carries what arrived &mdash; over REST from a supervisor, or
 * over {@code markdown-recommended} from FR14's agent &mdash; before translation into this
 * context's value objects. {@link MarkdownPlanApplicationService} does that translation, and the
 * domain does the deciding. The two entry points build the same command, which is what makes
 * "applies identically whether the proposal originated from a human or from the agent" a
 * structural fact rather than a promise.
 *
 * <p>It deliberately carries no {@code eventId}. Deduplicating a redelivered recommendation is a
 * property of the inbound adapter, not of the use case.
 *
 * @param discountTier one of spec-04's tier names; parsed and validated by the domain
 * @param origin       {@code HUMAN} or {@code AGENT}
 * @param rationale    optional; FR14's written justification when the agent proposed
 * @param agentTraceId optional; the key to the agent's tool-call trace
 */
public record CreateMarkdownPlanCommand(String batchId,
                                        String sku,
                                        String discountTier,
                                        Instant effectiveFrom,
                                        Instant effectiveTo,
                                        String origin,
                                        String rationale,
                                        String agentTraceId) {
}
