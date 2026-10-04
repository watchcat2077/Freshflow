"""database-design.uxf: one H2 schema per service, FK relations only inside a service."""
import os
from uxf import Diagram, render, wrap

OUT = os.environ.get("UML_OUT", os.path.dirname(os.path.abspath(__file__)))


def table(d, x, y, w, name, cols, bg="#FFFFFF"):
    h = 40 + 16 * len(cols) + 32
    h = (h + 9) // 10 * 10
    return d.klass(x, y, w, h, name, stereotype="table", attrs=cols, bg=bg)


def build():
    d = Diagram()
    d.text(0, 0, 1300, 30, "Database design: four independent H2 databases, one per service (spec-01 s1.3, s1.4). "
                           "Column names as declared by the JPA entities.", wrap=False)
    # inventory
    d.package(0, 40, 620, 310, "inventory-service: inventorydb", bg="#FAFAFA", extra="layer=-1")
    table(d, 20, 90, 280, "stock_batch",
          ["batch_id  PK", "sku", "received_quantity", "remaining_quantity", "expiry_at", "unit_cost_amount",
           "unit_cost_currency", "status", "received_at"])
    table(d, 320, 90, 280, "consumed_event",
          ["event_id  PK  (idempotency)", "topic", "consumed_at"])
    # sales
    d.package(660, 40, 620, 310, "sales-service: salesdb", bg="#FAFAFA", extra="layer=-1")
    st = table(d, 680, 90, 240, "sale_transaction",
               ["transaction_id  PK", "till_id", "occurred_at", "total_amount", "currency"])
    sl = table(d, 980, 90, 280, "sale_line_item",
               ["transaction_id  FK -> sale_transaction", "line_item_id", "sku", "quantity",
                "unit_price_amount", "unit_price_currency", "markdown_applied"])
    d.relation([sl.left_at(140), st.right_at(140)], lt="->", m1="*", m2="1")
    # pricing
    d.package(0, 390, 620, 430, "pricing-service: pricingdb", bg="#FAFAFA", extra="layer=-1")
    table(d, 20, 440, 260, "price_rule",
          ["sku  PK", "base_price_amount", "unit_cost_amount", "currency", "category", "margin_floor_ratio"])
    table(d, 320, 440, 280, "markdown_plan",
          ["markdown_plan_id  PK", "batch_id", "sku", "discount_tier", "effective_from", "effective_to",
           "status", "origin", "agent_trace_id", "rationale", "rejection_reason", "discounted_price_amount",
           "discounted_price_currency", "created_at"])
    # insight
    d.package(660, 390, 620, 510, "insight-service: insightdb", bg="#FAFAFA", extra="layer=-1")
    at = table(d, 680, 440, 260, "agent_trace",
               ["agent_trace_id  PK", "trigger_event_id  UNIQUE", "batch_id", "final_recommendation",
                "rationale", "completed_at"])
    atc = table(d, 980, 440, 280, "agent_tool_call",
                ["id  PK", "agent_trace_id  FK -> agent_trace", "call_sequence", "tool_name", "arguments",
                 "result", "called_at"])
    d.relation([atc.left_at(490), at.right_at(490)], lt="->", m1="*", m2="1")
    br = table(d, 680, 680, 260, "briefing",
               ["briefing_id  PK", "business_date", "generated_at", "summary", "risk_count", "recommendation_count"])
    btc = table(d, 980, 680, 280, "briefing_tool_call",
                ["id  PK", "briefing_id  FK -> briefing", "call_sequence", "tool_name", "arguments", "result",
                 "called_at"])
    d.relation([btc.left_at(730), br.right_at(730)], lt="->", m1="*", m2="1")
    d.note(0, 920, 1280, 70, wrap(
        "No foreign key crosses a service boundary; batch_id, sku and agent_trace_id in another service's table are "
        "plain values copied from events. FR12/FR13 read models are Kafka Streams state stores (velocity-windows, "
        "velocity-baselines, batch-states, batch-risks), not tables. Monetary columns are DECIMAL(19,4); Money "
        "normalises to scale 2 on the way out (spec-03 s7).", 1280), bg="#FFFBE6")
    path = os.path.join(OUT, "database-design.uxf")
    d.save(path)
    return render(path)


if __name__ == "__main__":
    print(build())
