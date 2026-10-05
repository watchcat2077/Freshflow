"""microservice-architecture.uxf: the four services, the Kafka bus, the two clients and the LLM.

Report Figure 3.1 (Section 3.1). Drawn from spec-01 s1.2 to s1.5 and s2.6 and from the code on main:
every endpoint, topic, pattern and class named here exists in the repository. Supersedes the
draw.io A2 of 10 September, which still said insight-service had no domain layer, listed the
unbuilt markdown-expired topic and showed only two of the five insight endpoints.
"""
import os
from uxf import Diagram, render, wrap

OUT = os.environ.get("UML_OUT", os.path.dirname(os.path.abspath(__file__)))

BG = {"inventory": "#DDEBF7", "sales": "#E2F0D9", "pricing": "#FCE5CD", "insight": "#FFF2CC",
      "kafka": "#F2F2F2", "external": "#FFFFFF", "note": "#FFFBE6"}


def service(d, x, y, w, h, title, lines, bg):
    return d.klass(x, y, w, h, title, attrs=lines, bg=bg, extra="halign=left")


def build():
    d = Diagram()
    d.text(0, 0, 1500, 30,
           "Microservice architecture: four bounded contexts, one Spring Boot service each, integrated over "
           "Apache Kafka (spec-01 s1.2 to s1.5). Solid arrows: down = publishes to a topic, up = consumes; "
           "dashed = synchronous REST or HTTPS", wrap=False)

    # --- clients and external systems -------------------------------------------------------
    pos = d.klass(20, 70, 180, 150, "<<client>>\npos-simulator",
                  attrs=["JDK-only command line", "that drives the demo:", "seeds batches, then",
                         "POST /api/transactions", "to sales-service over", "REST at a chosen rate"],
                  bg=BG["external"], extra="halign=left")
    llm = d.klass(1320, 70, 170, 150, "<<external system>>\nGoogle Gemini API",
                  attrs=["HTTPS from insight-", "service only, through", "LangChain4j (R2); key",
                         "and model name from", "the environment", "(ADR 0004)"],
                  bg=BG["external"], extra="halign=left")

    # --- the four services, one per bounded context ------------------------------------------
    W, H, Y = 250, 280, 70
    sal = service(d, 230, Y, W, H, "sales-service  :8082\nSales context",
                  ["pattern: Transaction Script", "TransactionApplicationService", "PriceSnapshot (local, event-fed)",
                   "H2: salesdb", "", "REST: POST /api/transactions", "", "publishes: item-sold",
                   "consumes: price-rule-published,", "  markdown-applied"], BG["sales"])
    inv = service(d, 500, Y, W, H, "inventory-service  :8081\nInventory & Batch context",
                  ["pattern: Domain Model", "StockBatch <<AR>>,", "FefoDeductionPolicy", "H2: inventorydb", "",
                   "REST: POST /api/batches,", "  GET /api/batches/{id}, ?sku=", "", "publishes: batch-received,",
                   "  stock-level-changed", "consumes: item-sold"], BG["inventory"])
    pri = service(d, 770, Y, W, H, "pricing-service  :8083\nPricing & Markdown context",
                  ["pattern: Domain Model", "MarkdownPlan <<AR>>, PriceRule,", "MarginFloorPolicy (FR11 invariant)",
                   "H2: pricingdb", "", "REST: PUT/GET /api/prices/{sku},", "  POST/GET /api/markdown-plans", "",
                   "publishes: price-rule-published,", "  markdown-applied", "consumes:", "  markdown-recommended"],
                  BG["pricing"])
    ins = service(d, 1040, Y, W, H, "insight-service  :8084\nInsight context",
                  ["pattern: CQRS read side +", "  domain package (ADR 0006)", "Kafka Streams: velocity (FR12),",
                   "  batch state, risk (FR13)", "Markdown Decision Agent (FR14)", "Briefing Agent (FR15)",
                   "H2: insightdb (traces, briefings)", "", "REST: GET /api/insight/", "  velocity/{sku}, /risks,",
                   "  /agent-traces/{id};", "  POST/GET /api/insight/briefings"], BG["insight"])

    # --- the Kafka bus ------------------------------------------------------------------------
    bus = d.klass(230, 470, 1060, 110, "Apache Kafka 3.9 (KRaft, one broker)  +  Spring Cloud Stream Kafka binder",
                  attrs=[wrap("integration topics (contracts module, Published Language): batch-received, "
                              "stock-level-changed, item-sold, price-rule-published, sell-through-risk-detected, "
                              "markdown-recommended, markdown-applied", 1040),
                         "internal topic (insight-service only): velocity-baseline-published",
                         "every event carries eventId (UUID, idempotent consumption) and occurredAt (producer event time)"],
                  bg=BG["kafka"], extra="halign=left")

    # publishes (down) and consumes (up) per service; the topic names are inside each box
    for box in (sal, inv, pri, ins):
        cx = box.cx
        d.relation([box.bottom_at(cx - 40), bus.top_at(cx - 40)])
        d.relation([bus.top_at(cx + 40), box.bottom_at(cx + 40)])

    # --- synchronous calls (dashed) ----------------------------------------------------------
    d.relation([pos.right_at(130), sal.left_at(130)], lt=".>")
    d.relation([pos.top_at(110), (110, 50), (inv.cx - 40, 50), inv.top_at(inv.cx - 40)], lt=".>",
               label="REST POST /api/batches (seed stock)")
    d.relation([ins.top_at(ins.cx), (ins.cx, 40), (inv.cx + 40, 40), inv.top_at(inv.cx + 40)], lt=".>",
               label="REST GET /api/batches/{id}: the agent's getBatchDetails tool (R6), the only service-to-service call")
    d.relation([ins.right_at(150), llm.left_at(150)], lt=".>")

    # --- notes --------------------------------------------------------------------------------
    d.note(20, 620, 700, 190, wrap(
        "Bounded-context integration patterns (spec-01 s1.5; L5). "
        "Sales -> Inventory: Customer-Supplier over item-sold. "
        "Pricing -> Sales: Published Language, the event records in the contracts module. "
        "Inventory, Sales, Pricing -> Insight: Conformist; Insight projects the upstream events into its own views. "
        "Agent -> Pricing: Anti-Corruption Layer; markdown-recommended is an untrusted proposal and MarginFloorPolicy "
        "decides, by the same code path as a supervisor's POST /api/markdown-plans (FR11, R5, R10). "
        "No shared kernel: Money, Sku and Quantity are declared per context.", 700), bg=BG["note"])
    d.note(740, 620, 750, 190, wrap(
        "Communication rules (spec-01 s1.3). Asynchronous messaging over Kafka is the primary and, with one "
        "exception, the only inter-service mechanism. Each service owns one H2 database; no table is read across a "
        "service boundary. Synchronous REST is used by external clients and the simulator, and by exactly one "
        "service-to-service call: the agent's read of a batch (a fact with a 404 path, no write). sales-service never "
        "calls pricing-service; it prices from a local snapshot fed by price-rule-published and markdown-applied "
        "(ADR 0001). The LLM is reached only from insight-service behind the MarkdownDecisionAgent and BriefingAgent "
        "ports; every test runs against a stub (ADR 0004). The agents propose or summarise; they never write a price.",
        750), bg=BG["note"])

    path = os.path.join(OUT, "microservice-architecture.uxf")
    d.save(path)
    return render(path)


if __name__ == "__main__":
    print(build())
