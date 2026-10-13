package com.freshflow.insight.application;

import com.freshflow.contracts.DiscountTier;

import java.time.Instant;

/**
 * What the agent proposes, expressed exactly as spec-02 FR14's {@code proposeMarkdown} tool
 * declares it: {@code proposeMarkdown(batchId, tier, effectiveFrom, rationale)}.
 *
 * <p>The component list is that argument list and nothing else. That is the point of the record: a
 * proposal is precisely what the agent was able to say through the one tool it has for saying it,
 * and no field can enter the recommendation without having come through a recorded tool call.
 *
 * <p><b>What is deliberately absent.</b> No price. No margin. No discounted amount. spec-02 FR14
 * and spec-01 &sect;2.5 are categorical that the agent proposes only and that the margin floor is
 * enforced downstream, in the Pricing context's domain layer; a proposal that carried a computed
 * price would be a decision wearing a suggestion's clothes.
 *
 * <p>{@code sku} is absent for a duller reason: it is carried by the triggering
 * {@code sell-through-risk-detected} event, and taking it from the trigger rather than from the
 * model removes one field the model could get wrong.
 *
 * <p>{@code effectiveTo} is absent because spec-02's tool signature does not offer it. The
 * promotion window's end is derived by {@link MarkdownAgentOrchestrator} from the batch's expiry,
 * which is a policy and not a judgement &mdash; see spec-06 &sect;4 (deviation D-3).
 */
public record MarkdownProposal(String batchId,
                               DiscountTier discountTier,
                               Instant effectiveFrom,
                               String rationale) {
}
