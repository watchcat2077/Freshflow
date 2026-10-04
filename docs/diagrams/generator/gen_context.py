"""context-map.uxf: bounded contexts and integration patterns (spec-01 s1.5)."""
import os
from uxf import Diagram, render, wrap

OUT = os.environ.get("UML_OUT", os.path.dirname(os.path.abspath(__file__)))


def ctx(d, x, y, w, h, name, lines, bg):
    return d.package(x, y, w, h, name, lines=lines, bg=bg, extra="halign=left")


def build():
    d = Diagram()
    d.text(0, 0, 1200, 30, "Context map: four bounded contexts and their integration patterns (spec-01 s1.5, spec-03 s0)", wrap=False)
    inv = ctx(d, 40, 60, 400, 200, "Inventory & Batch",
              ["inventory-service :8081  |  Domain Model", "Product = a physical batch with expiry and cost",
               "StockBatch <<AR>>, FefoDeductionPolicy", "Owns: batches, expiry, stock levels",
               "Sub-domain: supporting"], "#DDEBF7")
    ins = ctx(d, 760, 60, 400, 200, "Insight",
              ["insight-service :8084  |  CQRS read side (ADR 0006)", "Materialised views and agent traces",
               "SkuVelocityView, BatchRiskView, AgentTraceRecord", "Markdown Decision Agent, Briefing Agent",
               "Sub-domain: core (expiry risk, markdown decision)"], "#FFF2CC")
    sal = ctx(d, 40, 500, 400, 200, "Sales",
              ["sales-service :8082  |  Transaction Script", "Product = a SKU with the price at scan time",
               "SaleTransaction, LineItem, PriceSnapshot", "Owns: transactions, price application",
               "Sub-domain: generic"], "#E2F0D9")
    pri = ctx(d, 760, 500, 400, 200, "Pricing & Markdown",
              ["pricing-service :8083  |  Domain Model", "Product = cost and margin constraints",
               "MarkdownPlan <<AR>>, PriceRule, MarginFloorPolicy", "Owns: price rules, markdown plans, margin floor",
               "Sub-domain: core (markdown decision, margin floor)"], "#FCE5CD")
    con = d.package(480, 320, 240, 110, "contracts", lines=["Published Language", "integration events (Table A)",
                                                             "eventId + occurredAt on every event"],
                    bg="#F2F2F2", extra="halign=left")
    # Sales -> Inventory: Customer-Supplier
    d.relation([sal.top_at(240), inv.bottom_at(240)], lt="->", m1="U", m2="D",
               label="Customer-Supplier\nitem-sold")
    # Pricing -> Sales: Published Language
    d.relation([pri.left_at(620), sal.right_at(620)], lt="->", m1="U", m2="D",
               label="Published Language (contracts)\nprice-rule-published, markdown-applied")
    # Inventory -> Insight: Conformist
    d.relation([inv.right_at(160), ins.left_at(160)], lt="->", m1="U", m2="D",
               label="Conformist\nbatch-received, stock-level-changed")
    # Sales -> Insight: Conformist
    d.relation([sal.top_at(400), (400, 300), (780, 300), ins.bottom_at(780)], lt="->", m1="U", m2="D",
               label="Conformist: item-sold")
    # Pricing -> Insight: Conformist
    d.relation([pri.top_at(1080), ins.bottom_at(1080)], lt="->", m1="U", m2="D",
               label="Conformist\nmarkdown-applied")
    # Agent (Insight) -> Pricing: ACL
    d.relation([ins.bottom_at(860), pri.top_at(860)], lt="->", m1="U (agent)", m2="D + ACL",
               label="Anti-Corruption Layer\nmarkdown-recommended is an\nuntrusted proposal; MarginFloor-\nPolicy decides (R5)")
    d.note(40, 740, 1120, 70, wrap("Sub-domains: core = expiry risk detection and markdown decision (Insight, Pricing); "
                                    "supporting = batch inventory tracking, price-rule maintenance; generic = sale "
                                    "transaction recording. No shared kernel: Money, Sku and Quantity are re-declared per "
                                    "context; the only shared artefact is the contracts module (spec-03 s6).", 1120),
           bg="#FFFBE6")
    d.note(480, 440, 240, 50, "Synchronous REST is used only\nfor agent tool calls (spec-01 s1.3).", bg="#FFFBE6")
    path = os.path.join(OUT, "context-map.uxf")
    d.save(path)
    return render(path)


if __name__ == "__main__":
    print(build())
