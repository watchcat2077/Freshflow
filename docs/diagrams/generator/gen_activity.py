"""Twelve activity diagrams, one per use case in docs/report/section-2-1-use-cases.md."""
import os
import sys
from activity import Flow

OUT = os.environ.get("UML_OUT", os.path.dirname(os.path.abspath(__file__)))

ACTOR = lambda t, w=240, bw=200: (t, w, {"m": (w // 2, bw)})
SERVICE = lambda t, w=540, bw=220, aw=220: (t, w, {"m": (130, bw), "a": (410, aw)})
SERVICE_W = lambda t: (t, 540, {"m": (130, 220), "a": (410, 220)})
KAFKA = lambda: ("Kafka", 240, {"m": (120, 200)})


def fork_join(f, fk, jn, branches):
    for b in branches:
        f.edge([fk.bottom_at(b.cx), b.top])
        f.edge([b.bottom, jn.top_at(b.cx)])


# ---------------------------------------------------------------------------
def uc01():
    f = Flow("UC-01 Register a goods receipt (FR1)",
             [ACTOR("Store manager", 260, 220), SERVICE("inventory-service"), KAFKA()])
    i = f.initial(0, "m", 0)
    s1 = f.action(0, "m", 1, "Submit delivery: SKU, quantity,\nexpiry, unit cost, supplier ref")
    c1 = f.action(1, "m", 2, "Check all required\nfields are present")
    d1 = f.decision(1, "m", 3)
    r1 = f.action(1, "a", 3, "Answer 400, naming\nthe missing field")
    f.final_below(r1)
    c2 = f.action(1, "m", 4, "Check the quantity\nis positive")
    d2 = f.decision(1, "m", 5)
    r2 = f.action(1, "a", 5, "Answer 400\nQUANTITY_NOT_POSITIVE")
    f.final_below(r2)
    c3 = f.action(1, "m", 6, "Check the expiry date\nis in the future")
    d3 = f.decision(1, "m", 7)
    r3 = f.action(1, "a", 7, "Answer 400\nEXPIRY_IN_PAST")
    f.final_below(r3)
    cr = f.action(1, "m", 8, "Create the batch: new id,\nremaining = received, ACTIVE")
    fk = f.bar(1, "m", "a", 9)
    p1 = f.action(1, "m", 10, "Persist the batch")
    p2 = f.send(1, "a", 10, "Publish batch-received")
    ev = f.event(2, "m", 10, "batch-received", extra_line="(id, sku, qty, expiry, cost)")
    jn = f.bar(1, "m", "a", 11)
    rt = f.action(1, "m", 12, "Return the batch with 201")
    rc = f.action(0, "m", 13, "Receive the batch identifier")
    fn = f.final(0, "m", 14)
    f.v(i, s1); f.vh(s1, c1); f.v(c1, d1)
    f.h(d1, r1, m1="[field missing]"); f.v(d1, c2, m1="[all present]")
    f.v(c2, d2); f.h(d2, r2, m1="[quantity <= 0]"); f.v(d2, c3, m1="[positive]")
    f.v(c3, d3); f.h(d3, r3, m1="[expiry in past]"); f.v(d3, cr, m1="[in the future]")
    f.v(cr, fk); fork_join(f, fk, jn, [p1, p2]); f.h(p2, ev)
    f.edge([jn.bottom_at(rt.cx), rt.top]); f.vh(rt, rc); f.v(rc, fn)
    return f


def uc02():
    f = Flow("UC-02 Deduct stock on sale (FR2)",
             [KAFKA(), SERVICE("inventory-service")])
    i = f.initial(0, "m", 0)
    ev = f.event(0, "m", 1, "item-sold", extra_line="(eventId, sku, quantity)")
    rc = f.receive(1, "m", 1, "Receive item-sold event")
    c1 = f.action(1, "m", 2, "Check the eventId has\nnot been consumed")
    d1 = f.decision(1, "m", 3)
    r1 = f.action(1, "a", 3, "Ignore the redelivery;\ndeduct and publish nothing")
    f.final_below(r1)
    c2 = f.action(1, "m", 4, "Load ACTIVE batches of the\nSKU, soonest expiry first")
    d2 = f.decision(1, "m", 5)
    r2 = f.action(1, "a", 5, "Record eventId consumed;\nlog sale unmatched to stock")
    f.final_below(r2)
    c3 = f.action(1, "m", 6, "Plan the deduction across\nthe batches (FEFO)")
    d3 = f.decision(1, "m", 7)
    r3 = f.action(1, "a", 7, "Deduct all available, mark\nDEPLETED, log the shortfall")
    c4 = f.action(1, "m", 8, "Reduce each affected batch;\nat zero it becomes DEPLETED")
    c5 = f.action(1, "m", 9, "Record eventId as consumed\n(same transaction)")
    c6 = f.send(1, "m", 10, "Publish stock-level-changed\nper affected batch")
    ev2 = f.event(0, "m", 10, "stock-level-changed", extra_line="(one per affected batch)")
    fn = f.final(1, "m", 11)
    f.v(i, ev); f.h(ev, rc); f.v(rc, c1); f.v(c1, d1)
    f.h(d1, r1, m1="[already consumed]"); f.v(d1, c2, m1="[new event]")
    f.v(c2, d2); f.h(d2, r2, m1="[no ACTIVE batch]"); f.v(d2, c3, m1="[batches found]")
    f.v(c3, d3); f.h(d3, r3, m1="[qty exceeds stock]"); f.v(d3, c4, m1="[covered]")
    f.vh(r3, c5)
    f.v(c4, c5); f.v(c5, c6); f.h(c6, ev2); f.v(c6, fn)
    return f


def uc03():
    f = Flow("UC-03 Query stock position and remaining shelf life (FR4)",
             [("Shift supervisor\n(or the agent's getBatchDetails tool)", 260, {"m": (130, 220)}),
              ("inventory-service", 880,
               {"m": (130, 220), "b": (330, 140), "a": (560, 220), "c": (790, 140)})])
    i = f.initial(0, "m", 0)
    s1 = f.action(0, "m", 1, "Request a batch by id, or\nthe batches of a SKU")
    d0 = f.decision(1, "m", 2)
    d1 = f.decision(1, "a", 2)
    r1 = f.action(1, "c", 2, "Answer 400")
    f.final_below(r1)
    l1 = f.action(1, "m", 3, "Load the batch by id")
    l2 = f.action(1, "a", 3, "Load ACTIVE batches of the\nSKU, soonest expiry first")
    d2 = f.decision(1, "m", 4)
    r2 = f.action(1, "b", 4, "Answer 404")
    f.final_below(r2)
    s2 = f.action(1, "a", 4, "Compute each batch's\nremaining shelf life")
    s3 = f.action(1, "m", 5, "Compute remaining shelf life\nin whole hours to expiry")
    s4 = f.action(1, "a", 5, "Return the list, soonest\nexpiry first (empty = none)")
    s5 = f.action(1, "m", 6, "Return batch: quantities, expiry,\ncost, status, shelf life")
    rc = f.action(0, "m", 7, "Receive the answer")
    fn = f.final(0, "m", 8)
    f.v(i, s1); f.vh(s1, d0)
    f.h(d0, d1, m1="[by SKU]"); f.v(d0, l1, m1="[by id]")
    f.h(d1, r1, m1="[SKU blank]"); f.v(d1, l2, m1="[SKU given]")
    f.v(l1, d2); f.h(d2, r2, m1="[not found]"); f.v(d2, s3, m1="[found]")
    f.v(l2, s2); f.v(s2, s4)
    f.v(s3, s5); f.vh(s5, rc); f.vh(s4, rc)
    f.v(rc, fn)
    f.note(f.lanes[0]["x"] + 20, f.ry(3), 220, 110,
           "The query changes nothing and publishes nothing. The agent's getBatchDetails tool takes the by-id path over GET /api/batches/{batchId}.")
    return f


def uc04():
    f = Flow("UC-04 Record a sale transaction (FR5)",
             [("Checkout operator\n(POS terminal; pos-simulator)", 260, {"m": (130, 220)}),
              SERVICE("sales-service"), KAFKA()])
    i = f.initial(0, "m", 0)
    s1 = f.action(0, "m", 1, "Scan the basket: till id, time\nof sale, lines of SKU and qty")
    c1 = f.action(1, "m", 2, "Check till id present and\nline items with positive qty")
    d1 = f.decision(1, "m", 3)
    r1 = f.action(1, "a", 3, "Answer 400\nEMPTY_LINE_ITEMS")
    f.final_below(r1)
    d2 = f.decision(1, "m", 4)
    r2 = f.action(1, "a", 5, "Answer 400\nQUANTITY_NOT_POSITIVE")
    f.final_below(r2)
    c2 = f.action(1, "m", 5, "Read the time of sale")
    d3 = f.decision(1, "m", 6)
    r3 = f.action(1, "a", 6, "Stamp the current time\n(never broker ingestion time)")
    c3 = f.action(1, "m", 7, "Price each line item (UC-05)")
    d4 = f.decision(1, "m", 8)
    r4 = f.action(1, "a", 8, "Answer 400\nUNKNOWN_SKU; record nothing")
    f.final_below(r4)
    c4 = f.action(1, "m", 9, "Sum the line totals into\nthe transaction total")
    fk = f.bar(1, "m", "a", 10)
    p1 = f.action(1, "m", 11, "Record the transaction\nwith a new identifier")
    p2 = f.send(1, "a", 11, "Publish item-sold per line\n(producer-assigned time)", w=240)
    ev = f.event(2, "m", 11, "item-sold", extra_line="(one per line item)")
    jn = f.bar(1, "m", "a", 12)
    rt = f.action(1, "m", 13, "Return the transaction\nwith 201")
    rc = f.action(0, "m", 14, "Receive the receipt")
    fn = f.final(0, "m", 15)
    f.v(i, s1); f.vh(s1, c1); f.v(c1, d1)
    f.h(d1, r1, m1="[no line items]"); f.v(d1, d2, m1="[lines present]")
    f.hv(d2, r2, m1="[a quantity < 1]"); f.v(d2, c2, m1="[all quantities >= 1]")
    f.v(c2, d3); f.h(d3, r3, m1="[time omitted]"); f.v(d3, c3, m1="[time given]")
    f.vh(r3, c3)
    f.v(c3, d4); f.h(d4, r4, m1="[unknown SKU]"); f.v(d4, c4, m1="[all lines priced]")
    f.v(c4, fk); fork_join(f, fk, jn, [p1, p2]); f.h(p2, ev)
    f.edge([jn.bottom_at(rt.cx), rt.top]); f.vh(rt, rc); f.v(rc, fn)
    return f


def uc05():
    f = Flow("UC-05 Apply the price in force at the point of scan (FR6a, with FR6b)",
             [("sales-service (pricing one line item during UC-04)", 580,
               {"m": (130, 220), "a": (440, 240)})])
    i = f.initial(0, "m", 0)
    s1 = f.action(0, "m", 1, "Look up the SKU in the local\nprice snapshot (event-fed)")
    d1 = f.decision(0, "m", 2)
    r1 = f.action(0, "a", 2, "Refuse the sale (UC-04 3a):\n400 UNKNOWN_SKU,\nnever a silent zero price")
    f.final_below(r1)
    s2 = f.action(0, "m", 3, "Check whether an activated\nmarkdown's promotion window\ncontains the time of sale")
    d2 = f.decision(0, "m", 4)
    r2 = f.action(0, "a", 4, "FR6b: record the discounted\nprice carried on markdown-\napplied, markdownApplied = true")
    f.final_below(r2)
    s3 = f.action(0, "m", 5, "Record the base price,\nmarkdownApplied = false")
    fn = f.final(0, "m", 6)
    f.v(i, s1); f.v(s1, d1)
    f.h(d1, r1, m1="[SKU absent]"); f.v(d1, s2, m1="[SKU present]")
    f.v(s2, d2); f.h(d2, r2, m1="[markdown in force]"); f.v(d2, s3, m1="[none in force]")
    f.v(s3, fn)
    f.note(20, f.ry(7), 540, 90,
           "The snapshot is fed only by price-rule-published and markdown-applied events from pricing-service, "
           "through the PriceSnapshotFeed port; sales-service makes no synchronous call to pricing-service "
           "(spec-01 s1.3). A window that has ended (2b) falls into [none in force]: the base price returns "
           "without any further event.")
    return f


def uc06():
    f = Flow("UC-06 Maintain price rules and margin floors (FR8)",
             [("Store owner", 260, {"m": (130, 220)}),
              ("pricing-service", 520, {"m": (120, 200), "a": (400, 220)}), KAFKA()])
    i = f.initial(0, "m", 0)
    d0 = f.decision(0, "m", 1)
    rd = f.action(1, "a", 1, "Return the rule: margin floor,\neffective price, active tier")
    f.final_below(rd)
    s1 = f.action(0, "m", 2, "Submit base price, unit cost,\ncategory, margin floor ratio")
    c1 = f.action(1, "m", 3, "Check all fields present\nand currencies match")
    d1 = f.decision(1, "m", 4)
    r1 = f.action(1, "a", 5, "Answer 400, store nothing")
    f.final_below(r1)
    c2 = f.action(1, "m", 5, "Check base price is not\nbelow the unit cost")
    d2 = f.decision(1, "m", 6)
    r2 = f.action(1, "a", 6, "Answer 400\nBASE_PRICE_BELOW_COST")
    f.final_below(r2)
    c3 = f.action(1, "m", 7, "Check margin floor ratio\nlies between 0 and 1")
    d3 = f.decision(1, "m", 8)
    r3 = f.action(1, "a", 8, "Answer 400\nMARGIN_FLOOR_OUT_OF_RANGE", w=240)
    f.final_below(r3)
    fk = f.bar(1, "m", "a", 9)
    p1 = f.action(1, "m", 10, "Store the rule, replacing\nany earlier rule for the SKU")
    p2 = f.send(1, "a", 10, "Publish price-rule-published")
    ev = f.event(2, "m", 10, "price-rule-published", extra_line="(to sales-service's snapshot)")
    jn = f.bar(1, "m", "a", 11)
    rt = f.action(1, "m", 12, "Return the rule in force:\n201 created, 200 replaced")
    rc = f.action(0, "m", 13, "Receive the rule in force")
    fn = f.final(0, "m", 14)
    f.v(i, d0); f.h(d0, rd, m1="[read only]"); f.v(d0, s1, m1="[create or change]")
    f.vh(s1, c1); f.v(c1, d1)
    f.hv(d1, r1, m1="[field missing or currencies differ]"); f.v(d1, c2, m1="[valid]")
    f.v(c2, d2); f.h(d2, r2, m1="[below cost]"); f.v(d2, c3, m1="[not below cost]")
    f.v(c3, d3); f.h(d3, r3, m1="[ratio out of range]"); f.v(d3, fk, m1="[in range]")
    fork_join(f, fk, jn, [p1, p2]); f.h(p2, ev)
    f.edge([jn.bottom_at(rt.cx), rt.top]); f.vh(rt, rc); f.v(rc, fn)
    return f


def uc07():
    f = Flow("UC-07 Create and activate a markdown plan (FR9)",
             [KAFKA(), SERVICE("pricing-service"),
              ("Shift supervisor", 260, {"m": (130, 220)})])
    i = f.initial(2, "m", 0)
    s1 = f.action(2, "m", 1, "Submit batch, SKU, tier,\nwindow, origin HUMAN, rationale")
    ev = f.event(0, "m", 1, "markdown-recommended", extra_line="(from UC-11)")
    rc = f.receive(1, "m", 1, "Receive\nmarkdown-recommended\n(origin AGENT, agentTraceId)", w=240)
    d0 = f.decision(1, "m", 2)
    r0 = f.action(1, "a", 2, "Ignore the redelivery;\nstore nothing")
    f.final_below(r0)
    c1 = f.action(1, "m", 3, "Check fields present, tier is\none of four, window end > start")
    d1 = f.decision(1, "m", 4)
    r1 = f.action(1, "a", 5, "Answer 400, store nothing")
    f.final_below(r1)
    c2 = f.action(1, "m", 5, "Load the price rule\nfor the SKU")
    d2 = f.decision(1, "m", 6)
    r2 = f.action(1, "a", 6, "Answer 404: no floor\nto check against")
    f.final_below(r2)
    c3 = f.action(1, "m", 7, "Margin floor policy assesses the\ntier against the rule (UC-08)", w=240)
    d3 = f.decision(1, "m", 8)
    r3 = f.action(1, "a", 8, "Store the plan as REJECTED;\nanswer 422 (UC-08)")
    f.final_below(r3)
    c4 = f.action(1, "m", 9, "Plan becomes ACTIVE with the\ncomputed discounted price")
    fk = f.bar(1, "m", "a", 10)
    p1 = f.send(1, "m", 11, "Publish markdown-applied\n(plan, window, price)")
    ev2 = f.event(0, "m", 11, "markdown-applied", extra_line="(to UC-05 and UC-10)")
    p2 = f.action(1, "a", 11, "Store the plan")
    jn = f.bar(1, "m", "a", 12)
    d4 = f.decision(1, "m", 13)
    r4 = f.action(1, "a", 13, "Return the ACTIVE plan\nwith 201")
    rc2 = f.action(2, "m", 14, "Receive the active plan")
    fn2 = f.final(2, "m", 15)
    c5 = f.action(1, "m", 14, "No HTTP answer; the stored\nplan is readable by its id")
    fn = f.final(1, "m", 15)
    f.v(i, s1); f.vh(s1, c1)
    f.h(ev, rc); f.v(rc, d0); f.h(d0, r0, m1="[trace id seen before]"); f.v(d0, c1, m1="[new trace id]")
    f.v(c1, d1); f.hv(d1, r1, m1="[field missing or window inverted]"); f.v(d1, c2, m1="[valid]")
    f.v(c2, d2); f.h(d2, r2, m1="[no rule for SKU]"); f.v(d2, c3, m1="[rule found]")
    f.v(c3, d3); f.h(d3, r3, m1="[floor breached]"); f.v(d3, c4, m1="[assessment passes]")
    f.v(c4, fk); fork_join(f, fk, jn, [p1, p2]); f.h(p1, ev2)
    f.edge([jn.bottom_at(d4.cx), d4.top])
    f.h(d4, r4, m1="[origin HUMAN]"); f.vh(r4, rc2); f.v(rc2, fn2)
    f.v(d4, c5, m1="[origin AGENT]"); f.v(c5, fn)
    return f


def uc08():
    f = Flow("UC-08 Reject a markdown that breaches the margin floor (FR11)",
             [("pricing-service: application layer", 520, {"m": (130, 220), "a": (410, 200)}),
              ("pricing-service: domain layer\n(MarginFloorPolicy, MarkdownPlan)", 540,
               {"m": (130, 220), "a": (410, 220)})])
    i = f.initial(0, "m", 0)
    s1 = f.action(0, "m", 1, "Pass the price rule and the\nrequested tier to the policy")
    c1 = f.action(1, "m", 1, "Compute the resulting price:\nbase x (1 - tier %), to cents")
    c2 = f.action(1, "m", 2, "Compute the resulting margin:\n(price - unit cost) / price")
    c3 = f.action(1, "m", 3, "Compare the ratio with\nthe SKU's margin floor")
    d1 = f.decision(1, "m", 4)
    r1 = f.action(1, "a", 4, "Assessment passes: UC-07\ncontinues at its step 5")
    f.final_below(r1)
    c4 = f.action(1, "m", 5, "Plan records itself REJECTED,\nnaming tier, margin and floor")
    s2 = f.action(0, "m", 6, "Store the rejected plan")
    d2 = f.decision(0, "m", 7)
    r2 = f.action(0, "a", 7, "No HTTP answer: the rejected\nplan is the audit record")
    f.final_below(r2)
    s3 = f.action(0, "m", 8, "Answer 422: SKU, tier, price,\nunit cost, margin ratio, floor")
    fn = f.final(0, "m", 9)
    f.v(i, s1); f.h(s1, c1); f.v(c1, c2); f.v(c2, c3); f.v(c3, d1)
    f.h(d1, r1, m1="[ratio >= floor]"); f.v(d1, c4, m1="[ratio below floor]")
    f.vh(c4, s2)
    f.v(s2, d2); f.h(d2, r2, m1="[origin AGENT]"); f.v(d2, s3, m1="[origin HUMAN]"); f.v(s3, fn)
    f.note(f.lanes[1]["x"] + 300, f.ry(6), 220, 100,
           "The same deterministic rule decides for a human and for the agent: the Anti-Corruption Layer of spec-01 s1.5 and s2.5.")
    return f


def uc09():
    f = Flow("UC-09 Observe the rolling sell-through velocity of a SKU (FR12, real-time analysis)",
             [KAFKA(), ("insight-service (Kafka Streams topology; Interactive Query)", 560,
                        {"m": (140, 240), "a": (430, 220)}),
              ("Shift supervisor\n(or the agent's getSellThroughRate tool)", 280, {"m": (140, 220)})])
    i = f.initial(0, "m", 0)
    ev = f.event(0, "m", 1, "item-sold", extra_line="(occurredAt set by producer)")
    c1 = f.receive(1, "m", 1, "Consume item-sold;\nassign it to windows\nby its occurredAt")
    d1 = f.decision(1, "m", 2)
    r1 = f.action(1, "a", 2, "Count it in the window its\noccurredAt belongs to (1a)")
    c2 = f.action(1, "m", 3, "Group by SKU; sum quantity per\nhopping window (60 s, step 5 s)")
    c3 = f.action(1, "m", 4, "Join the windowed total with\nthe SKU's baseline (KTable)")
    c4 = f.action(1, "m", 5, "Keep the latest closed window\nper SKU in the state store\n(velocity/h, ratio to baseline)")
    q1 = f.action(2, "m", 6, "Request the velocity\nfor a SKU")
    c5 = f.action(1, "m", 7, "Look up the SKU in the\nvelocity state store")
    d2 = f.decision(1, "m", 8)
    r2 = f.action(1, "a", 8, "Answer 404: no closed window,\nor it has aged out")
    f.final_below(r2)
    c6 = f.action(1, "m", 9, "Return window bounds, quantity\nsold, velocity/h, ratio")
    q2 = f.action(2, "m", 10, "Receive the velocity")
    fn = f.final(2, "m", 11)
    f.v(i, ev); f.h(ev, c1); f.v(c1, d1)
    f.h(d1, r1, m1="[late, within grace]"); f.v(d1, c2, m1="[on time]")
    f.vh(r1, c2)
    f.v(c2, c3); f.v(c3, c4); f.vh(c4, q1); f.vh(q1, c5)
    f.v(c5, d2); f.h(d2, r2, m1="[no closed window]"); f.v(d2, c6, m1="[window found]")
    f.vh(c6, q2); f.v(q2, fn)
    f.note(f.lanes[2]["x"] + 30, f.ry(3), 220, 80,
           "Each window that closes also triggers UC-10 for the SKU's batches.")
    return f


def uc10():
    f = Flow("UC-10 Detect a batch at risk of expiring unsold (FR13, real-time analysis)",
             [KAFKA(), ("insight-service (Kafka Streams topology; RiskAssessmentPolicy)", 560,
                        {"m": (140, 240), "a": (430, 220)}),
              ("Shift supervisor", 260, {"m": (130, 220)})])
    i = f.initial(0, "m", 0)
    ev = f.event(0, "m", 1, "batch-received", extra_line="stock-level-changed")
    c1 = f.receive(1, "m", 1, "Maintain the live batch-state\ntable per SKU")
    c2 = f.action(1, "m", 2, "Wait for the SKU's velocity\nwindow to close (UC-09)")
    d0 = f.decision(1, "m", 3)
    r0 = f.action(1, "a", 3, "No window closes: batch not\nre-assessed (limitation G-4)")
    f.final_below(r0)
    c3 = f.action(1, "m", 4, "Join the velocity with every\nACTIVE batch of the SKU")
    c4 = f.action(1, "m", 5, "Project sales to expiry:\nvelocity/h x trading hours left")
    c5 = f.action(1, "m", 6, "Compute the shortfall:\nremaining - projected sales")
    d1 = f.decision(1, "m", 7)
    r1 = f.action(1, "a", 7, "Leave it to write-off;\nno event (5b)")
    f.final_below(r1)
    d2 = f.decision(1, "m", 8)
    r2 = f.action(1, "a", 9, "Record not at risk;\nno event (5a)")
    f.final_below(r2)
    c6 = f.action(1, "m", 9, "Mark the batch at risk\nin the risk view")
    d3 = f.decision(1, "m", 10)
    r3 = f.action(1, "a", 10, "Update the view;\nno new event")
    f.final_below(r3)
    p1 = f.send(1, "m", 11, "Publish sell-through-risk-\ndetected (triggers UC-11)")
    ev2 = f.event(0, "m", 11, "sell-through-risk-detected", extra_line="(consumed in UC-11)")
    q1 = f.action(2, "m", 12, "List the current risks")
    c7 = f.action(1, "m", 13, "Return the risks, largest\nshortfall first")
    q2 = f.action(2, "m", 14, "Receive the risk list")
    fn = f.final(2, "m", 15)
    f.v(i, ev); f.h(ev, c1); f.v(c1, c2); f.v(c2, d0)
    f.h(d0, r0, m1="[sales stopped]"); f.v(d0, c3, m1="[a window closes]")
    f.v(c3, c4); f.v(c4, c5); f.v(c5, d1)
    f.h(d1, r1, m1="[already expired]"); f.v(d1, d2, m1="[not expired]")
    f.hv(d2, r2, m1="[shortfall within threshold]"); f.v(d2, c6, m1="[shortfall exceeds threshold]")
    f.v(c6, d3); f.h(d3, r3, m1="[already at risk]"); f.v(d3, p1, m1="[transition into risk]")
    f.h(p1, ev2); f.vh(p1, q1)
    f.vh(q1, c7); f.vh(c7, q2); f.v(q2, fn)
    return f


def uc11():
    f = Flow("UC-11 Recommend a markdown for an at-risk batch (FR14, agentic AI)",
             [KAFKA(), ("inventory-service", 240, {"m": (120, 200)}),
              ("insight-service (MarkdownAgentOrchestrator and its tools)", 560,
               {"m": (140, 240), "a": (430, 220)}),
              ("Markdown Decision Agent (LLM)", 540, {"m": (160, 220), "a": (420, 160)})])
    LOOP_X = f.lanes[3]["x"] + 520
    i = f.initial(0, "m", 0)
    ev = f.event(0, "m", 1, "sell-through-risk-detected", extra_line="(from UC-10)")
    rc = f.receive(2, "m", 1, "Receive the trigger")
    dh = f.decision(2, "m", 2)
    rh = f.action(2, "a", 2, "Log as not deliberated;\ndrop the trigger (Pre)")
    f.final_below(rh)
    c1 = f.action(2, "m", 3, "Check the trigger's eventId\nwas not deliberated before")
    di = f.decision(2, "m", 4)
    ri = f.action(2, "a", 4, "Deliberate nothing;\npublish nothing (1a)")
    f.final_below(ri)
    c2 = f.action(2, "m", 5, "Hand the trigger to the agent\nwith the three tools")
    sel = f.action(3, "m", 5, "Select the next tool call")
    da = f.decision(3, "m", 6)
    ta = f.action(2, "m", 6, "Tool getBatchDetails: GET\n/api/batches/{batchId}")
    inv = f.action(1, "m", 6, "Return remaining quantity,\nexpiry and unit cost")
    db = f.decision(3, "m", 7)
    tb = f.action(2, "m", 7, "Tool getSellThroughRate: read\nthe velocity state store")
    pr = f.action(3, "m", 8, "Tool proposeMarkdown (once):\nflagged batch, tier, start,\nrationale citing the numbers")
    rd = f.action(3, "m", 9, "Read the tool result")
    dc = f.decision(3, "m", 10)
    cc = f.action(3, "a", 10, "Correct the call, retry\n(bounded steps) (2a)")
    dd = f.decision(3, "m", 11)
    fin = f.action(3, "m", 12, "Finish: a proposal, or no\nmarkdown warranted (5a)")
    de = f.decision(2, "m", 13)
    re_ = f.action(2, "a", 13, "Persist the trace with\nNO_MARKDOWN;\npublish nothing")
    f.final_below(re_)
    c3 = f.action(2, "m", 14, "Check the proposal names\nthe flagged batch")
    df = f.decision(2, "m", 15)
    rf = f.action(2, "a", 15, "Refuse it; record the trace\nas declined (6a, R7)")
    f.final_below(rf)
    c4 = f.action(2, "m", 16, "Derive the window end\nfrom the batch's expiry")
    dg = f.decision(2, "m", 17)
    rg = f.action(2, "a", 17, "Record PROPOSED_BUT_\nUNRESOLVABLE (7a, R9)")
    f.final_below(rg)
    c5 = f.action(2, "m", 18, "Persist the trace: ordered tool\ncalls, recommendation, rationale")
    p1 = f.send(2, "m", 19, "Publish markdown-recommended")
    ev2 = f.event(0, "m", 19, "markdown-recommended", extra_line="(consumed in UC-07)")
    fn = f.final(2, "m", 20)
    # edges
    f.v(i, ev); f.h(ev, rc); f.v(rc, dh)
    f.h(dh, rh, m1="[no LLM configured]"); f.v(dh, c1, m1="[LLM configured]")
    f.v(c1, di); f.h(di, ri, m1="[already deliberated]"); f.v(di, c2, m1="[new trigger]")
    f.h(c2, sel); f.v(sel, da)
    f.h(da, ta, m1="[getBatchDetails]"); f.h(ta, inv)
    f.v(da, db, m1="[other tool]")
    f.h(db, tb, m1="[getSellThroughRate]"); f.v(db, pr, m1="[proposeMarkdown]")
    f.v(pr, rd)
    f.edge([inv.bottom, (inv.cx, rd.cy), rd.left])
    f.edge([tb.bottom, (tb.cx, rd.cy), rd.left])
    f.v(rd, dc); f.h(dc, cc, m1="[tool error]"); f.v(dc, dd, m1="[result read]")
    f.edge([cc.top, (cc.cx, sel.cy + 10), sel.right_at(sel.cy + 10)])
    f.question(dd, "more tool calls?")
    f.edge([dd.right, (LOOP_X, dd.cy), (LOOP_X, sel.cy - 10), sel.right_at(sel.cy - 10)],
           m1="[yes]")
    f.v(dd, fin, m1="[no]")
    f.hv(fin, de)
    f.h(de, re_, m1="[no markdown]"); f.v(de, c3, m1="[proposal made]")
    f.v(c3, df); f.h(df, rf, m1="[different batch]"); f.v(df, c4, m1="[flagged batch]")
    f.v(c4, dg); f.h(dg, rg, m1="[batch unresolvable]"); f.v(dg, c5, m1="[resolved]")
    f.v(c5, p1); f.h(p1, ev2); f.v(p1, fn)
    f.note(f.lanes[1]["x"] + 20, f.ry(13), 200, 130,
           "The agent proposes only. No price is written here; pricing-service's margin floor decides in UC-07 / UC-08 (ruling R5).")
    return f


def uc12():
    f = Flow("UC-12 Produce the end-of-day briefing (FR15, agentic AI)",
             [("Store owner", 260, {"m": (130, 220)}),
              ("insight-service (briefing orchestrator and its tools)", 560,
               {"m": (140, 240), "a": (430, 220)}),
              ("Briefing Agent (LLM)", 560, {"m": (140, 240), "a": (430, 220)})])
    i = f.initial(0, "m", 0)
    s1 = f.action(0, "m", 1, "Request the briefing for a\nbusiness date (default today)")
    d1 = f.decision(1, "m", 2)
    r1 = f.action(1, "a", 2, "Answer 503\nAGENT_NOT_CONFIGURED\n(Pre)")
    f.final_below(r1)
    c1 = f.action(1, "m", 3, "Hand the request to the agent\nwith three read-only tools")
    a1 = f.action(2, "m", 3, "Call listCurrentRisks")
    t1 = f.action(1, "m", 4, "Return the batches\ncurrently at risk")
    a2 = f.action(2, "m", 5, "Call listRecommendations\nfor the date")
    t2 = f.action(1, "m", 6, "Return the day's markdown\nrecommendations and outcomes")
    d2 = f.decision(2, "m", 7)
    a3 = f.action(2, "a", 7, "Call getSellThroughRate\nfor a SKU to comment on")
    t3 = f.action(1, "a", 8, "Return the SKU's velocity\nand baseline ratio")
    d3 = f.decision(2, "m", 9)
    a4 = f.action(2, "a", 10, "Say the day needed no\nmarkdown action (3a)")
    w1 = f.action(2, "m", 10, "Write a short plain-language\nsummary: markdowns, sell-through,\nrisks; write-offs not recorded")
    c2 = f.action(1, "m", 11, "Count the risks and the\nrecommendations from the tool\nresults, not the model's text")
    c3 = f.action(1, "m", 12, "Persist the briefing with\nits ordered tool calls")
    c4 = f.action(1, "m", 13, "Return the briefing with 201")
    o1 = f.action(0, "m", 14, "Read the briefing")
    d4 = f.decision(0, "m", 15)
    fn1 = f.final(0, "m", 16)
    l1 = f.action(1, "m", 15, "Load the stored briefing\nby its identifier (1a)")
    d5 = f.decision(1, "m", 16)
    r5 = f.action(1, "a", 16, "Answer 404")
    f.final_below(r5)
    l2 = f.action(1, "m", 17, "Return it with its tool calls")
    o2 = f.action(0, "m", 18, "Read the stored briefing")
    fn2 = f.final(0, "m", 19)
    f.v(i, s1); f.vh(s1, d1)
    f.h(d1, r1, m1="[no LLM configured]"); f.v(d1, c1, m1="[LLM configured]")
    f.h(c1, a1); f.vh(a1, t1); f.vh(t1, a2); f.vh(a2, t2); f.hv(t2, d2)
    f.question(d2, "more tool calls?")
    f.h(d2, a3, m1="[yes]"); f.vh(a3, t3)
    f.edge([t3.top, (t3.cx, d2.cy), d2.left])
    f.v(d2, d3, m1="[no]")
    f.hv(d3, a4, m1="[no risks and no recommendations]"); f.v(d3, w1, m1="[something to report]")
    f.h(a4, w1)
    f.vh(w1, c2); f.v(c2, c3); f.v(c3, c4); f.vh(c4, o1); f.v(o1, d4)
    f.v(d4, fn1, m1="[done]"); f.h(d4, l1, m1="[asks by id (1a)]")
    f.v(l1, d5); f.h(d5, r5, m1="[not found]"); f.v(d5, l2, m1="[found]")
    f.vh(l2, o2); f.v(o2, fn2)
    return f


ALL = {"01": uc01, "02": uc02, "03": uc03, "04": uc04, "05": uc05, "06": uc06,
       "07": uc07, "08": uc08, "09": uc09, "10": uc10, "11": uc11, "12": uc12}

if __name__ == "__main__":
    wanted = sys.argv[1:] or sorted(ALL)
    for k in wanted:
        f = ALL[k]()
        png = f.finish(os.path.join(OUT, f"activity-UC-{k}.uxf"))
        print(png)
