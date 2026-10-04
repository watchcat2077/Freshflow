"""event-flow.uxf: services, Kafka topics, producer -> topic -> consumer arrows, REST and LLM calls."""
import os
from uxf import Diagram, render, wrap

OUT = os.environ.get("UML_OUT", os.path.dirname(os.path.abspath(__file__)))


def service(d, x, y, w, h, name, lines, bg):
    return d.klass(x, y, w, h, name, attrs=lines, bg=bg)


def build():
    d = Diagram()
    d.text(0, 0, 1400, 30, "Event flow: producers, Kafka topics and consumers (spec-01 s2.6 Tables A and B); "
                           "dashed = synchronous call", wrap=False)
    # system boundary
    d.add("UMLClass", 20, 50, 1180, 790, "FreshFlow system boundary\nvalign=top\nhalign=left\nlt=.\nlayer=-2")
    # kafka lane
    d.lane(440, 90, 320, 680, "Apache Kafka (Spring Cloud Stream)", bg="#F7F7F7")
    inv = service(d, 40, 100, 240, 180, "inventory-service", ["Inventory & Batch", "port 8081", "", "produces: batch-received,", "stock-level-changed", "consumes: item-sold"], "#DDEBF7")
    sal = service(d, 40, 580, 240, 200, "sales-service", ["Sales", "port 8082", "", "produces: item-sold", "consumes: price-rule-published,", "markdown-applied"], "#E2F0D9")
    ins = service(d, 900, 100, 260, 440, "insight-service", ["Insight", "port 8084", "", "Kafka Streams topology:", "velocity, batch state, risk", "Markdown Decision Agent (FR14)", "Briefing Agent (FR15)", "", "consumes: batch-received,", "stock-level-changed, item-sold,", "markdown-applied,", "sell-through-risk-detected", "produces: sell-through-risk-", "detected, markdown-recommended"], "#FFF2CC")
    pri = service(d, 900, 580, 260, 200, "pricing-service", ["Pricing & Markdown", "port 8083", "", "produces: price-rule-published,", "markdown-applied", "consumes: markdown-recommended"], "#FCE5CD")

    topics = ["batch-received", "stock-level-changed", "item-sold", "velocity-baseline-published\n(internal, Table B)",
              "sell-through-risk-detected", "markdown-applied", "markdown-recommended", "price-rule-published"]
    ys = [110, 190, 270, 350, 430, 510, 590, 670]
    tb = {}
    for name, y in zip(topics, ys):
        key = name.split("\n")[0]
        tb[key] = d.klass(460, y, 280, 50, "<<topic>> " + name if "\n" not in name else "<<topic>> " + name,
                          bg="#FFFFFF")
    T = lambda k: tb[k]
    # 1, 2: inventory -> insight
    d.relation([inv.right_at(135), T("batch-received").left_at(135)])
    d.relation([T("batch-received").right_at(135), ins.left_at(135)])
    d.relation([inv.right_at(215), T("stock-level-changed").left_at(215)])
    d.relation([T("stock-level-changed").right_at(215), ins.left_at(215)])
    # 3: sales -> item-sold -> inventory, insight
    d.relation([sal.top_at(160), (160, 305), T("item-sold").left_at(305)])
    d.relation([T("item-sold").left_at(285), (380, 285), (380, 250), inv.right_at(250)])
    d.relation([T("item-sold").right_at(295), ins.left_at(295)])
    # 4: velocity baseline, internal
    d.relation([ins.left_at(365), T("velocity-baseline-published").right_at(365)])
    d.relation([T("velocity-baseline-published").right_at(385), ins.left_at(385)], label="KTable")
    # 5: sell-through-risk-detected, insight -> insight (FR14 trigger)
    d.relation([ins.left_at(445), T("sell-through-risk-detected").right_at(445)])
    d.relation([T("sell-through-risk-detected").right_at(465), ins.left_at(465)], label="FR14 trigger")
    # 6: markdown-applied: pricing -> sales, insight
    d.relation([pri.left_at(600), (820, 600), (820, 545), T("markdown-applied").right_at(545)])
    d.relation([T("markdown-applied").right_at(525), ins.left_at(525)])
    d.relation([T("markdown-applied").left_at(535), (400, 535), (400, 600), sal.right_at(600)])
    # 7: markdown-recommended: insight -> pricing
    d.relation([ins.left_at(535), (800, 535), (800, 605), T("markdown-recommended").right_at(605)])
    d.relation([T("markdown-recommended").right_at(625), pri.left_at(625)])
    # 8: price-rule-published: pricing -> sales
    d.relation([pri.left_at(695), T("price-rule-published").right_at(695)])
    d.relation([T("price-rule-published").left_at(695), sal.right_at(695)])
    # REST: insight -> inventory (agent tool), over the top
    d.relation([ins.top_at(1030), (1030, 70), (160, 70), inv.top_at(160)], lt=".>",
               label="REST  GET /api/batches/{batchId}  (agent tool getBatchDetails, R6)")
    # LLM outside the boundary
    llm = d.klass(1260, 240, 200, 90, "LLM API (Gemini)", stereotype="external system",
                  attrs=["LangChain4j (R2)", "key from the environment"], bg="#F2F2F2")
    d.relation([ins.right_at(285), llm.left_at(285)], lt=".>", label="HTTPS")
    d.note(20, 860, 760, 60, wrap("Closed loop: sale -> deduction -> risk -> recommendation -> activation -> "
                                   "discounted scan  (UC-04 -> UC-02 -> UC-10 -> UC-11 -> UC-07 -> UC-05).", 760),
           bg="#FFFBE6")
    d.note(800, 860, 400, 60, wrap("Every event carries eventId (UUID) and occurredAt (producer time); windows use "
                                    "occurredAt, never ingestion time.", 400), bg="#FFFBE6")
    path = os.path.join(OUT, "event-flow.uxf")
    d.save(path)
    return render(path)


if __name__ == "__main__":
    print(build())
