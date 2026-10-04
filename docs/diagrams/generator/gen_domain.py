"""domain-model.uxf: class diagram of the four bounded contexts (spec-03)."""
import os
from uxf import Diagram, render, wrap

OUT = os.environ.get("UML_OUT", os.path.dirname(os.path.abspath(__file__)))

BG = {"ar": "#CFE2F3", "entity": "#DDEBF7", "vo": "#FFFFFF", "enum": "#EFEFEF",
      "svc": "#FCE5CD", "event": "#FFF2CC", "port": "#E7E6E6", "rm": "#E2F0D9", "pkg": "#FAFAFA"}


def cls(d, x, y, w, name, stereo=None, attrs=(), ops=(), kind="vo", h=None):
    lines = (2 if stereo else 1) + len(attrs) + len(ops)
    seps = (1 if attrs or ops else 0) + (1 if ops else 0)
    if h is None:
        h = 16 * lines + 12 * seps + 16
        h = (h + 9) // 10 * 10
    return d.klass(x, y, w, h, name, stereotype=stereo, attrs=attrs, ops=ops, bg=BG[kind])


def note(d, x, y, w, h, text):
    return d.note(x, y, w, h, wrap(text, w), bg="#FFFBE6")


def build():
    d = Diagram()
    d.text(0, 0, 1400, 30, "FreshFlow domain model: four bounded contexts, one package each (spec-03). "
                           "Money, Sku and Quantity are re-declared per context.", wrap=False)

    # ------------------------------------------------------------- Inventory
    d.package(0, 40, 940, 640, "Inventory & Batch  (inventory-service, Domain Model)", bg=BG["pkg"], extra="layer=-1")
    sb = cls(d, 40, 100, 300, "StockBatch", "Aggregate Root",
             ["batchId: BatchId", "sku: Sku", "receivedQuantity: Quantity", "remainingQuantity: Quantity",
              "expiryAt: ExpiryDate", "unitCost: Money", "status: BatchStatus"],
             ["receive()", "deduct(Quantity)", "remainingShelfLife(now): Duration"], kind="ar")
    fefo = cls(d, 440, 100, 280, "FefoDeductionPolicy", "Domain Service",
               [], ["plan(batches, quantity): DeductionPlan"], kind="svc")
    dp = cls(d, 760, 100, 160, "DeductionPlan", "Value Object", ["lines: batch -> qty"], kind="vo")
    bs = cls(d, 440, 200, 160, "BatchStatus", "enum", ["ACTIVE", "DEPLETED", "WRITTEN_OFF"], kind="enum")
    note(d, 620, 200, 300, 80, "Invariants inside the aggregate: remaining >= 0; no deduct on an expired or "
                               "written-off batch; DEPLETED at zero (spec-03 s1).")
    vos = []
    names = ["BatchId", "Sku", "Quantity", "ExpiryDate", "Money"]
    attrs = [["value: UUID"], ["value: String"], ["units: int >= 0"], ["at: Instant"], ["amount: BigDecimal(2)", "currency"]]
    for i, (n, a) in enumerate(zip(names, attrs)):
        vos.append(cls(d, 40 + i * 180, 400, 160, n, "Value Object", a, kind="vo"))
    br = cls(d, 40, 520, 220, "BatchReceived", "Domain Event",
             ["batchId, sku, quantity,", "expiryAt, unitCost"], kind="event")
    slc = cls(d, 290, 520, 220, "StockLevelChanged", "Domain Event",
              ["batchId, sku,", "remainingQuantity, changedAt"], kind="event")
    dep = cls(d, 560, 520, 300, "DomainEventPublisher", "Port",
              [], ["publish(BatchReceived)", "publish(StockLevelChanged)"], kind="port")
    d.relation([fefo.left_at(130), sb.right_at(130)], lt=".>", label="FEFO over")
    d.relation([fefo.right_at(130), dp.left_at(130)], lt=".>", label="creates")
    d.relation([sb.right_at(245), bs.left_at(245)], lt="-")
    d.relation([sb.bottom_at(120), vos[0].top], lt="<<<<<-")
    d.relation([sb.bottom_at(300), vos[1].top], lt="<<<<<-")
    d.relation([sb.right_at(300), (vos[2].cx, 300), vos[2].top], lt="<<<<<-")
    d.relation([sb.right_at(315), (vos[3].cx, 315), vos[3].top], lt="<<<<<-")
    d.relation([sb.right_at(330), (vos[4].cx, 330), vos[4].top], lt="<<<<<-")
    d.relation([dep.left_at(560), slc.right_at(560)], lt=".>", label="publishes")
    d.relation([dep.bottom_at(620), (620, 650), (150, 650), br.bottom], lt=".>")

    # ----------------------------------------------------------------- Sales
    d.package(960, 40, 940, 560, "Sales  (sales-service, Transaction Script)", bg=BG["pkg"], extra="layer=-1")
    st = cls(d, 1000, 100, 260, "SaleTransaction", None,
             ["transactionId: UUID", "tillId: String", "occurredAt: Instant",
              "lineItems: List<LineItem>", "totalAmount: Money"], kind="entity")
    li = cls(d, 1320, 100, 260, "LineItem", None,
             ["lineItemId: UUID", "sku: Sku", "quantity: int", "unitPrice: Money",
              "markdownApplied: boolean"], kind="entity")
    ps = cls(d, 1000, 280, 300, "PriceSnapshot", "Value Object",
             ["sku: Sku", "basePrice: Money", "effectivePrice: Money", "unitCost: Money",
              "category: String", "discountTier: DiscountTier?", "effectiveFrom: Instant?",
              "effectiveTo: Instant?"], ["priceAt(instant): Money"], kind="vo")
    smoney = cls(d, 1340, 280, 200, "Money", "Value Object", ["amount: BigDecimal(2)", "currency"], kind="vo")
    ssku = cls(d, 1340, 400, 200, "Sku", "Value Object", ["value: String"], kind="vo")
    note(d, 1620, 100, 260, 170, "Transaction Script: no aggregate root, no repository (ADR 0002). "
                                 "The application service holds the behaviour; these are plain data "
                                 "structures. PriceSnapshot is fed only through the PriceSnapshotFeed port "
                                 "(application layer).")
    d.relation([st.right_at(140), li.left_at(140)], lt="<<<<<-", m1="1", m2="1..*")
    d.relation([ps.right_at(320), smoney.left_at(320)], lt="-")
    d.relation([ps.right_at(430), ssku.left_at(430)], lt="-")
    note(d, 960, 620, 620, 50, "Money, Sku and Quantity are declared separately in each context: no shared kernel (spec-03 s6).")

    # --------------------------------------------------------------- Pricing
    d.package(0, 700, 940, 940, "Pricing & Markdown  (pricing-service, Domain Model)", bg=BG["pkg"], extra="layer=-1")
    mp = cls(d, 40, 760, 320, "MarkdownPlan", "Aggregate Root",
             ["markdownPlanId: MarkdownPlanId", "batchId: BatchId", "sku: Sku", "discountTier: DiscountTier",
              "window: PromotionWindow", "status: MarkdownStatus", "origin: ProposalOrigin",
              "agentTraceId: String?", "rationale: String?", "rejectionReason: String?",
              "discountedPrice: Money?"],
             ["activate(MarginFloorAssessment)", "expire()", "reject(reason)"], kind="ar")
    pr = cls(d, 420, 760, 240, "PriceRule", "Entity",
             ["sku: Sku", "basePrice: Money", "unitCost: Money", "category: String", "marginFloor: MarginFloor"],
             ["effectivePrice(): Money"], kind="entity")
    mfp = cls(d, 700, 760, 220, "MarginFloorPolicy", "Domain Service",
              [], ["assess(rule, tier):", "  MarginFloorAssessment"], kind="svc")
    mfa = cls(d, 700, 900, 220, "MarginFloorAssessment", "Value Object",
              ["resultingPrice: Money", "resultingMarginRatio", "floor: MarginFloor"],
              ["breached(): boolean"], kind="vo")
    d.relation([mfp.left_at(800), pr.right_at(800)], lt=".>", label="reads")
    d.relation([mfp.bottom, mfa.top], lt=".>", label="produces")
    pvo = {}
    for n, a, x, w in [("MarkdownPlanId", ["value: UUID"], 40, 160),
                       ("DiscountTier", ["PERCENT_20 | 30 | 50 | 60"], 220, 200),
                       ("PromotionWindow", ["effectiveFrom, effectiveTo"], 440, 190),
                       ("MarginFloor", ["ratio: BigDecimal"], 650, 150)]:
        pvo[n] = cls(d, x, 1120, w, n, "Value Object", a, kind="vo")
    pvo["Sku"] = cls(d, 40, 1220, 140, "Sku", "Value Object", ["value: String"], kind="vo")
    pvo["BatchId"] = cls(d, 200, 1220, 140, "BatchId", "Value Object", ["value: UUID"], kind="vo")
    pvo["Money"] = cls(d, 360, 1220, 160, "Money", "Value Object", ["amount(2), currency"], kind="vo")
    cls(d, 540, 1220, 170, "MarkdownStatus", "enum", ["PROPOSED", "ACTIVE", "EXPIRED", "REJECTED"], kind="enum")
    cls(d, 730, 1220, 150, "ProposalOrigin", "enum", ["HUMAN", "AGENT"], kind="enum")
    d.relation([mp.bottom_at(120), pvo["MarkdownPlanId"].top], lt="<<<<<-")
    d.relation([mp.bottom_at(320), pvo["DiscountTier"].top], lt="<<<<<-")
    d.relation([mp.right_at(1040), (pvo["PromotionWindow"].cx, 1040), pvo["PromotionWindow"].top], lt="<<<<<-")
    d.relation([pr.bottom_at(640), (640, 1090), (pvo["MarginFloor"].cx, 1090), pvo["MarginFloor"].top], lt="<<<<<-")
    prp = cls(d, 40, 1380, 220, "PriceRulePublished", "Domain Event",
              ["sku, basePrice,", "unitCost, category"], kind="event")
    prep = cls(d, 340, 1380, 300, "PriceRuleEventPublisher", "Port", [], ["publish(PriceRulePublished)"], kind="port")
    ma = cls(d, 40, 1500, 220, "MarkdownApplied", "Domain Event",
             ["markdownPlanId, batchId, sku,", "discountTier, window,", "discountedPrice"], kind="event")
    mep = cls(d, 340, 1500, 300, "MarkdownEventPublisher", "Port", [], ["publish(MarkdownApplied)"], kind="port")
    d.relation([prep.left_at(prep.cy), prp.right_at(prep.cy)], lt=".>", label="publishes")
    d.relation([mep.left_at(mep.cy), ma.right_at(mep.cy)], lt=".>", label="publishes")
    note(d, 660, 1380, 260, 150, "MarginFloorPolicy is the Anti-Corruption Layer: it decides identically for a "
                                 "HUMAN and an AGENT proposal, and activate() takes its assessment so the aggregate "
                                 "never recomputes a margin (spec-03 s3, B-3/B-6).")

    # --------------------------------------------------------------- Insight
    d.package(960, 700, 940, 940, "Insight  (insight-service, CQRS read side + domain policy, ADR 0006)", bg=BG["pkg"], extra="layer=-1")
    rap = cls(d, 1000, 760, 320, "RiskAssessmentPolicy", "Domain Service",
              [], ["assess(batchId, sku, remainingQuantity,", "  expiryAt, velocityPerHour, now):", "  BatchRiskView"], kind="svc")
    brv = cls(d, 1380, 760, 260, "BatchRiskView", "Value Object",
              ["batchId: BatchId", "sku: Sku", "remainingQuantity: int", "hoursToExpiry: long",
               "currentVelocity: BigDecimal", "projectedSales: BigDecimal", "projectedShortfall: BigDecimal",
               "detectedAt: Instant"], kind="vo")
    d.relation([rap.right_at(800), brv.left_at(800)], lt=".>", label="produces")
    cls(d, 1000, 1000, 260, "SkuVelocityView", "Read Model (CQRS)",
        ["sku", "windowStart, windowEnd", "quantitySold", "velocityPerHour", "baselineVelocity",
         "velocityRatio"], kind="rm")
    atr = cls(d, 1280, 1000, 260, "AgentTraceRecord", "Read Model (CQRS)",
              ["agentTraceId", "triggerEventId", "batchId", "toolCalls: List<ToolCall>",
               "finalRecommendation", "rationale", "completedAt"], kind="rm")
    tc = cls(d, 1580, 1000, 220, "ToolCall", "Value Object",
             ["toolName", "arguments", "result", "timestamp"], kind="vo")
    cls(d, 1000, 1240, 260, "MarkdownProposal", "Read Model (CQRS)",
        ["batchId", "discountTier", "effectiveFrom", "rationale"], kind="rm")
    eod = cls(d, 1280, 1240, 260, "EndOfDayBriefing", "Read Model (CQRS)",
              ["briefingId", "businessDate", "generatedAt", "summary", "riskCount",
               "recommendationCount", "toolCalls: List<ToolCall>"], kind="rm")
    d.relation([atr.right_at(1060), tc.left_at(1060)], lt="<<<<<-", m2="*  ordered")
    d.relation([eod.right_at(1300), (tc.cx, 1300), tc.bottom], lt="<<<<<-", m2="*  ordered")
    note(d, 1580, 1420, 300, 160, "No aggregates: SkuVelocityView and BatchRiskView are materialised by the "
                                  "Kafka Streams topology (state stores); AgentTraceRecord, MarkdownProposal and "
                                  "EndOfDayBriefing are written by the two agents and persisted for audit.")
    note(d, 1000, 1450, 540, 70, "MarkdownProposal is what the agent's proposeMarkdown tool hands the "
                                 "orchestrator (spec-06); it carries no price. The margin floor is never evaluated here (R5).")
    path = os.path.join(OUT, "domain-model.uxf")
    d.save(path)
    return render(path)


if __name__ == "__main__":
    print(build())
