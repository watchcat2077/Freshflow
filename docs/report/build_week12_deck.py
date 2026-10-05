"""Builds docs/report/FreshFlow-Week12-Presentation.pptx from docs/report/presentation-week12.md.

Twelve slides, 16:9, one speaker and one time each, following the CSCI318 presentation guideline
(12 minutes; business domain, analysis, design, architecture, implementation, a short demo; members
on the first slide). Diagrams are the PNGs in docs/diagrams. Re-run after editing:

    python docs/report/build_week12_deck.py
"""
import os
import shutil

from pptx import Presentation
from pptx.dml.color import RGBColor
from pptx.enum.shapes import MSO_SHAPE
from pptx.enum.text import MSO_ANCHOR, PP_ALIGN
from pptx.util import Emu, Inches, Pt

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.abspath(os.path.join(HERE, "..", ".."))
DIAGRAMS = os.path.join(REPO, "docs", "diagrams")
OUT = os.path.join(HERE, "FreshFlow-Week12-Presentation.pptx")
MIRROR = os.path.join(os.path.dirname(REPO), "Group Document", "FreshFlow-Week12-Presentation.pptx")

# A fresh-produce palette: one dominant green, a moss support, one orange accent for "markdown".
GREEN = RGBColor(0x1F, 0x4D, 0x2E)
MOSS = RGBColor(0x6B, 0x8F, 0x3A)
ORANGE = RGBColor(0xE0, 0x7A, 0x1F)
PALE = RGBColor(0xF1, 0xF6, 0xEE)
INK = RGBColor(0x1E, 0x1E, 0x1E)
MUTED = RGBColor(0x5B, 0x6B, 0x5E)
WHITE = RGBColor(0xFF, 0xFF, 0xFF)
FONT = "Calibri"

W, H = Inches(13.333), Inches(7.5)
MARGIN = Inches(0.6)

prs = Presentation()
prs.slide_width, prs.slide_height = W, H
BLANK = prs.slide_layouts[6]


# ----------------------------------------------------------------------------------- helpers
def fill(shape, rgb):
    shape.fill.solid()
    shape.fill.fore_color.rgb = rgb
    shape.line.fill.background()


def textbox(slide, x, y, w, h, lines, size=16, color=INK, bold=False, align=PP_ALIGN.LEFT,
            anchor=MSO_ANCHOR.TOP, bullets=False, space_after=6, font=FONT):
    """lines: str or list of str | (str, dict) where dict may set size, bold, color, bullet."""
    tb = slide.shapes.add_textbox(x, y, w, h)
    tf = tb.text_frame
    tf.word_wrap = True
    tf.vertical_anchor = anchor
    tf.margin_left = tf.margin_right = Inches(0.08)
    tf.margin_top = tf.margin_bottom = Inches(0.04)
    if isinstance(lines, str):
        lines = [lines]
    first = True
    for line in lines:
        opts = {}
        if isinstance(line, tuple):
            line, opts = line
        p = tf.paragraphs[0] if first else tf.add_paragraph()
        first = False
        p.alignment = align
        p.space_after = Pt(space_after)
        use_bullet = opts.get("bullet", bullets)
        run = p.add_run()
        run.text = ("•  " + line) if use_bullet else line
        f = run.font
        f.name = font
        f.size = Pt(opts.get("size", size))
        f.bold = opts.get("bold", bold)
        f.color.rgb = opts.get("color", color)
    return tb


def title(slide, text, speaker=None, minute=None, dark=False):
    # 28 pt keeps every title in the deck on one line at this width; the speaker tag sits below it.
    textbox(slide, MARGIN, Inches(0.3), W - 2 * MARGIN, Inches(0.7), text, size=28, bold=True,
            color=WHITE if dark else GREEN, anchor=MSO_ANCHOR.MIDDLE)
    if speaker:
        textbox(slide, MARGIN, Inches(0.95), W - 2 * MARGIN, Inches(0.3),
                f"{speaker}  ·  {minute}", size=12, color=PALE if dark else MUTED,
                align=PP_ALIGN.LEFT, anchor=MSO_ANCHOR.MIDDLE)


def card(slide, x, y, w, h, heading, body, head_color=GREEN, bg=PALE, body_size=13, head_size=15):
    box = slide.shapes.add_shape(MSO_SHAPE.ROUNDED_RECTANGLE, x, y, w, h)
    box.adjustments[0] = 0.06
    fill(box, bg)
    box.shadow.inherit = False
    # a heading longer than the card is wide wraps to two lines; give it the room
    chars_per_line = max(1, int((w / 914400 - 0.24) / (head_size * 0.5 / 72)))
    head_h = Inches(0.5) if len(heading) <= chars_per_line else Inches(0.85)
    textbox(slide, x + Inches(0.12), y + Inches(0.08), w - Inches(0.24), head_h, heading,
            size=head_size, bold=True, color=head_color)
    textbox(slide, x + Inches(0.12), y + head_h + Inches(0.05), w - Inches(0.24), h - head_h - Inches(0.1),
            body, size=body_size, color=INK, bullets=isinstance(body, list), space_after=4)
    return box


def stat(slide, x, y, w, number, label, color=GREEN):
    textbox(slide, x, y, w, Inches(0.9), number, size=40, bold=True, color=color, align=PP_ALIGN.CENTER,
            anchor=MSO_ANCHOR.MIDDLE)
    textbox(slide, x, y + Inches(0.85), w, Inches(0.7), label, size=12, color=MUTED, align=PP_ALIGN.CENTER)


def picture(slide, name, x, y, w=None, h=None):
    path = os.path.join(DIAGRAMS, name)
    if w is not None:
        return slide.shapes.add_picture(path, x, y, width=w)
    return slide.shapes.add_picture(path, x, y, height=h)


def notes(slide, text):
    slide.notes_slide.notes_text_frame.text = text


def background(slide, rgb):
    bg = slide.shapes.add_shape(MSO_SHAPE.RECTANGLE, 0, 0, W, H)
    fill(bg, rgb)
    bg.shadow.inherit = False


def new_slide(dark=False):
    s = prs.slides.add_slide(BLANK)
    if dark:
        background(s, GREEN)
    return s


# ----------------------------------------------------------------------------- slide 1: title
s = new_slide(dark=True)
textbox(s, MARGIN, Inches(0.9), W - 2 * MARGIN, Inches(1.2), "FreshFlow", size=54, bold=True, color=WHITE)
textbox(s, MARGIN, Inches(2.0), W - 2 * MARGIN, Inches(0.9),
        "Real-time expiry risk detection and agentic markdown decision support for grocery retail",
        size=20, color=PALE)
textbox(s, MARGIN, Inches(2.8), W - 2 * MARGIN, Inches(0.7),
        "CSCI318 Software Engineering Practices & Principles  ·  Spring 2026  ·  Week 12 project presentation  ·  Group 06 “318 Hero”",
        size=14, color=PALE)
members = [
    ("Zihua (Andy) Zeng", "7164695", "AI and streaming lead: insight-service, Kafka Streams topology, the two agents, specifications, AI process"),
    ("Reuben Kruger", "8973878", "Domain services lead: inventory and pricing domain models, use cases, Week 8 video, Postman collection"),
    ("Modhuri (Madz) Dey Sithi", "9184843", "Platform and integration lead: sales-service, Kafka and Docker, architecture and UML diagrams"),
]
cw = (W - 2 * MARGIN - Inches(0.6)) / 3
for i, (name, num, role) in enumerate(members):
    x = MARGIN + i * (cw + Inches(0.3))
    card(s, x, Inches(3.6), cw, Inches(2.2), f"{name}  ·  {num}", role, head_color=GREEN,
         bg=WHITE, body_size=12, head_size=13)
textbox(s, MARGIN, Inches(6.2), W - 2 * MARGIN, Inches(0.6),
        "Contribution: equal across the three members, as agreed by the group and stated on the report's cover page.",
        size=13, color=PALE)
notes(s, "0:00, Reuben (30 s). Names, student numbers, roles. The contribution line must match the "
         "signed cover page of the report (Week 11 meeting, 14 October). If the group prefers not to "
         "state percentages on the slide, delete the line and send them to Dr Su privately, as the "
         "guideline allows.")

# ---------------------------------------------------------------------------- slide 2: problem
s = new_slide()
title(s, "The problem: short-dated stock loses money in two directions", "Reuben", "0:30")
card(s, MARGIN, Inches(1.4), Inches(5.9), Inches(2.0), "Discount too early",
     "Margin is given away on units that would have sold at full price anyway.", head_color=ORANGE)
card(s, MARGIN, Inches(3.6), Inches(5.9), Inches(2.0), "Discount too late, or never",
     "The batch reaches its expiry date on the shelf and is written off in full.", head_color=ORANGE)
card(s, Inches(6.9), Inches(1.4), Inches(5.8), Inches(4.2), "Three facts nobody holds in their head at once",
     ["How fast this product is selling right now, against its normal rate",
      "How many units are left in this particular batch (two deliveries of one yoghurt expire on different days)",
      "How many trading hours remain before that batch expires",
      "",
      "Today the decision is made by walking the aisle and guessing. FreshFlow makes the three facts visible as they change, flags the batches that need a decision, and asks an agent to propose one."],
     body_size=14)
textbox(s, MARGIN, Inches(6.0), W - 2 * MARGIN, Inches(0.8),
        "Stakeholders: store manager (deliveries), checkout operator (till), shift supervisor (markdowns), store owner (price rules, end of day).",
        size=13, color=MUTED)
notes(s, "0:30 to 1:30. Two ways to lose money; three facts; who the stakeholders are. One sentence on "
         "objectives O1 to O5 from Section 1.2 if time allows.")

# ------------------------------------------------------------------------------- slide 3: idea
s = new_slide()
title(s, "The idea: stream finds risk, agent proposes, domain decides", "Reuben", "1:30")
steps = [
    ("POS scans", "sales-service publishes item-sold; inventory deducts FEFO", GREEN),
    ("Kafka Streams", "velocity per SKU on event time; join with live batch state; risk detected", GREEN),
    ("Agent proposes", "LLM with three tools reads the facts and proposes a discount tier with a rationale", MOSS),
    ("Domain decides", "pricing-service's margin floor accepts or rejects, identically for a human or the agent", ORANGE),
    ("Till applies", "the next scan is priced with the live markdown; the owner gets a briefing", GREEN),
]
sw = (W - 2 * MARGIN - Inches(0.8)) / 5
for i, (head, body, col) in enumerate(steps):
    x = MARGIN + i * (sw + Inches(0.2))
    chev = s.shapes.add_shape(MSO_SHAPE.CHEVRON if i else MSO_SHAPE.PENTAGON, x, Inches(1.5), sw, Inches(0.7))
    fill(chev, col)
    chev.shadow.inherit = False
    tf = chev.text_frame
    tf.text = head
    tf.paragraphs[0].alignment = PP_ALIGN.CENTER
    r = tf.paragraphs[0].runs[0]
    r.font.size, r.font.bold, r.font.name, r.font.color.rgb = Pt(15), True, FONT, WHITE
    textbox(s, x, Inches(2.35), sw, Inches(1.8), body, size=13, color=INK)
box = s.shapes.add_shape(MSO_SHAPE.ROUNDED_RECTANGLE, MARGIN, Inches(4.3), W - 2 * MARGIN, Inches(1.9))
box.adjustments[0] = 0.08
fill(box, PALE)
box.shadow.inherit = False
textbox(s, MARGIN + Inches(0.3), Inches(4.35), W - 2 * MARGIN - Inches(0.6), Inches(1.8),
        [("The agent can only propose. Whether a markdown is allowed is decided by a deterministic rule, the margin floor, inside the pricing service's domain layer, and it decides the same way for a person and for the agent.",
          {"size": 18, "bold": True, "color": GREEN}),
         ("A prompt is a request to a probabilistic model; a business invariant has to be deterministic, testable and the same every time.", {"size": 14, "color": MUTED})],
        anchor=MSO_ANCHOR.MIDDLE)
textbox(s, MARGIN, Inches(6.4), W - 2 * MARGIN, Inches(0.6),
        "Twelve functional requirements, twelve use cases with activity diagrams; two real-time analyses (FR12, FR13); two agentic stories (FR14, FR15).",
        size=13, color=MUTED)
notes(s, "1:30 to 2:30. Walk the chain left to right, then read the green sentence aloud: it is the "
         "answer to the question the marker is most likely to ask.")

# ------------------------------------------------------------------- slide 4: bounded contexts
s = new_slide()
title(s, "Four bounded contexts, because “product” means four things", "Madhuri", "2:30")
pic = picture(s, "context-map.png", MARGIN, Inches(1.3), w=Inches(7.4))
textbox(s, Inches(8.3), Inches(1.3), Inches(4.5), Inches(5.6),
        [("Inventory & Batch", {"bold": True, "color": GREEN}),
         "a physical batch with a quantity and an expiry; Domain Model, StockBatch aggregate",
         ("Sales", {"bold": True, "color": GREEN}),
         "a barcode scanned at a price at a moment in time; Transaction Script, no aggregate",
         ("Pricing & Markdown", {"bold": True, "color": GREEN}),
         "a cost-and-margin constraint set; Domain Model, MarkdownPlan aggregate, the margin floor",
         ("Insight", {"bold": True, "color": GREEN}),
         "a read-side projection: velocity, batches at risk, agent traces; CQRS read side with a small domain package (ADR 0006)",
         ("Integration", {"bold": True, "color": ORANGE}),
         "customer-supplier, published language (the contracts module), conformist, and an anti-corruption layer in front of pricing. No shared kernel: Money and Sku are declared per context."],
        size=13, space_after=3)
notes(s, "2:30 to 3:30. One model of 'product' would carry contradictory rules (L3). Name the four "
         "contexts and the pattern each uses, then the integration patterns on the map.")

# ------------------------------------------------------------------------ slide 5: architecture
s = new_slide()
title(s, "Architecture: four services over Kafka, one synchronous call", "Madhuri", "3:30")
picture(s, "microservice-architecture.png", Inches(1.85), Inches(1.2), w=Inches(9.6))
textbox(s, MARGIN, Inches(6.55), W - 2 * MARGIN, Inches(0.8),
        "Eight topics; every event carries eventId (idempotent consumption) and occurredAt (event-time windowing). One H2 database per service, no shared schema. The only service-to-service REST call is the agent's read of a batch; sales never calls pricing.",
        size=12, color=MUTED)
notes(s, "3:30 to 4:30. Point at the Kafka bus, the four services, the POS simulator and the LLM. Say "
         "why sales must not call pricing synchronously (ADR 0001: a local price snapshot fed by events).")

# ------------------------------------------------------------------ slide 6: layers and DDD
s = new_slide()
title(s, "Layered architecture and the tactical DDD patterns", "Madhuri", "4:30")
picture(s, "layered-architecture-all-services.png", MARGIN, Inches(1.3), w=Inches(7.6))
code = ("public void activate(MarginFloorAssessment a) {\n"
        "    requireNotRejected(\"activated\");          // REJECTED is terminal\n"
        "    if (status != MarkdownStatus.PROPOSED) throw ...;\n"
        "    if (a.breached()) {                       // the margin floor\n"
        "        status = MarkdownStatus.REJECTED;\n"
        "        rejectionReason = a.message();\n"
        "        throw new MarginFloorBreach(a);\n"
        "    }\n"
        "    status = MarkdownStatus.ACTIVE;\n"
        "    discountedPrice = a.resultingPrice();\n"
        "}")
cb = s.shapes.add_shape(MSO_SHAPE.ROUNDED_RECTANGLE, Inches(8.5), Inches(1.3), Inches(4.3), Inches(2.75))
cb.adjustments[0] = 0.04
fill(cb, RGBColor(0xF5, 0xF5, 0xF5))
cb.shadow.inherit = False
textbox(s, Inches(8.6), Inches(1.35), Inches(4.1), Inches(2.65), code, size=9.5, font="Consolas", space_after=0)
textbox(s, Inches(8.5), Inches(4.2), Inches(4.3), Inches(2.9),
        [("MarkdownPlan (aggregate root)", {"bold": True, "color": ORANGE}),
         "the invariant the project is built around, enforced in the aggregate, never in a controller or a prompt",
         ("Money, Sku, Quantity (value objects)", {"bold": True, "color": GREEN}),
         "records that validate themselves",
         ("FefoDeductionPolicy, MarginFloorPolicy (domain services)", {"bold": True, "color": GREEN}),
         "logic spanning several objects",
         ("The domain package imports only java.*", {"bold": True, "color": GREEN}),
         "a guard test fails the build on any Spring, JPA, Jackson or Kafka import; repositories live in infrastructure.repository (coordinator's ruling, ADR 0005)"],
        size=11, space_after=2)
notes(s, "4:30 to 5:30. presentation -> application -> domain <- infrastructure in every service; the "
         "pattern per context; read the activate() method: this is where the agent's proposal is judged.")

# ----------------------------------------------------------------- slide 7: stream processing
s = new_slide()
title(s, "Stream processing: two real-time queries, one topology", "Andy", "5:30")
rows = [
    ("Kafka Streams DSL", "SQL-like reading", True),
    ("itemSold.groupBy(sku).windowedBy(60 s, advance 5 s, grace 5 s).aggregate(sum)", "SELECT sku, SUM(quantity) FROM item_sold WINDOW HOPPING ... GROUP BY sku", False),
    ("windowed.suppress(untilWindowCloses).toStream()", "EMIT FINAL: one value per closed window, not one per update", False),
    ("velocity.join(batchStates, RiskAssessmentPolicy::assess)", "SELECT ... FROM velocity v JOIN batch_states b ON v.sku = b.sku WHERE shortfall > 0", False),
    ("InteractiveQueryService.getQueryableStore(...)", "the GET endpoints read the state stores directly; no database", False),
]
tbl = s.shapes.add_table(len(rows), 2, MARGIN, Inches(1.3), W - 2 * MARGIN, Inches(2.9)).table
tbl.columns[0].width = Inches(6.3)
tbl.columns[1].width = W - 2 * MARGIN - Inches(6.3)
for r, (a, b, head) in enumerate(rows):
    for c, text in enumerate((a, b)):
        cell = tbl.cell(r, c)
        cell.text = text
        cell.fill.solid()
        cell.fill.fore_color.rgb = GREEN if head else (PALE if r % 2 else WHITE)
        p = cell.text_frame.paragraphs[0]
        p.runs[0].font.size = Pt(12 if not head else 13)
        p.runs[0].font.bold = head
        p.runs[0].font.name = "Consolas" if (c == 0 and not head) else FONT
        p.runs[0].font.color.rgb = WHITE if head else INK
textbox(s, MARGIN, Inches(4.4), Inches(6.2), Inches(2.7),
        [("Event time, never ingestion time", {"bold": True, "color": GREEN}),
         "one timestamp extractor reads occurredAt on every input topic; a late sale lands in the window it belongs to, and a replay gives the same velocities",
         ("Stream-table join", {"bold": True, "color": GREEN}),
         "batch-received and stock-level-changed are folded into a KTable of live batch state per SKU; each closed window is joined against it and RiskAssessmentPolicy projects the shortfall"],
        size=12, space_after=3)
textbox(s, Inches(7.1), Inches(4.4), Inches(5.7), Inches(2.7),
        [("Only the transition into risk fires", {"bold": True, "color": GREEN}),
         "a reduce keeps one standing assessment per batch, so the agent is woken once, not every five seconds",
         ("Served by interactive query", {"bold": True, "color": GREEN}),
         "GET /api/insight/velocity/{sku} and GET /api/insight/risks read the state stores; the coordinator confirmed the DSL is the expected SQL-like analysis"],
        size=12, space_after=3)
notes(s, "5:30 to 6:30. Read the DSL beside the SQL it stands for. Mention the defect the live run found "
         "(an extractor that matched one topic) and that the topology is what cargo-tracker's "
         "StreamProcessor does, plus event time, grace and a join.")

# --------------------------------------------------------------------------- slide 8: agents
s = new_slide()
title(s, "Two agents, two ports, three tools each, every step traced", "Andy", "6:30")
cw = (W - 2 * MARGIN - Inches(0.5)) / 3
card(s, MARGIN, Inches(1.3), cw, Inches(4.3), "Markdown Decision Agent (FR14)",
     ["Trigger: the sell-through-risk-detected event",
      "ReAct loop with LangChain4j + Gemini, at most 8 steps",
      "Tools: getBatchDetails (REST to inventory), getSellThroughRate (FR12 store), proposeMarkdown",
      "Orchestrator: idempotent on eventId, refuses a proposal for another batch, derives the window end from expiry, publishes markdown-recommended",
      "Trace persisted: every call, argument, result; GET /api/insight/agent-traces/{id}"], body_size=12)
card(s, MARGIN + cw + Inches(0.25), Inches(1.3), cw, Inches(4.3), "Briefing Agent (FR15)",
     ["Trigger: POST /api/insight/briefings",
      "Tools: listCurrentRisks, listRecommendations, getSellThroughRate (read-only)",
      "Writes four to eight plain sentences for the store owner",
      "riskCount and recommendationCount are computed by the orchestrator from the stores, never parsed from the prose",
      "Persisted with its tool calls, readable by id"], body_size=12)
card(s, MARGIN + 2 * (cw + Inches(0.25)), Inches(1.3), cw, Inches(4.3), "Guardrails, in code not in prompts",
     ["The margin floor lives in pricing-service's domain; the agent's proposal takes the same path as a supervisor's",
      "A guard test scans every insight-service source, prompts included, and fails the build on the words margin floor or pricing",
      "No tool reads the floor, no tool writes a price",
      "Model down: adapters translate to AgentUnavailableException; nothing stored; 503 AGENT_UNAVAILABLE; FR12 and FR13 keep running",
      "Every test runs against a deterministic stub (ADR 0004)"], head_color=ORANGE, body_size=12, head_size=14)
textbox(s, MARGIN, Inches(5.8), W - 2 * MARGIN, Inches(1.1),
        "From the 4 October run: the agent proposed PERCENT_50 for a yoghurt batch (217 units projected unsold); pricing refused it (margin 0.0667 below floor 0.15) and kept the plan as REJECTED, linked to the trace. A minute later, re-flagged at a higher velocity, the agent declined to propose anything.",
        size=12, color=MUTED)
notes(s, "6:30 to 7:30. Lecture 7's ReAct loop; why the loop is explicit (the trace); why the floor is "
         "not in the prompt. The bottom line is the project's central argument in data.")

# ------------------------------------------------------------------------- slide 9: live demo
s = new_slide()
title(s, "Live demonstration (backup video ready)", "Andy", "7:30")
demo = [
    ("1", "Risk appears", "GET /api/insight/risks: one batch, projected shortfall, hours to expiry (FR13)"),
    ("2", "The agent's trace", "GET /api/insight/agent-traces/{id}: three tool calls in order, the proposal, the rationale citing the numbers (FR14)"),
    ("3", "Pricing decides", "POST /api/markdown-plans: 201 ACTIVE for PERCENT_30; 422 MARGIN_FLOOR_BREACH for PERCENT_60 (FR9, FR11)"),
    ("4", "The till applies it", "POST /api/transactions: unitPrice 3.15, markdownApplied: true (FR6b)"),
    ("5", "The briefing", "POST /api/insight/briefings: a few sentences, with counts the code computed (FR15)"),
]
for i, (n, head, body) in enumerate(demo):
    y = Inches(1.3) + i * Inches(0.86)
    circ = s.shapes.add_shape(MSO_SHAPE.OVAL, MARGIN, y, Inches(0.6), Inches(0.6))
    fill(circ, ORANGE if n == "3" else GREEN)
    circ.shadow.inherit = False
    circ.text_frame.text = n
    circ.text_frame.paragraphs[0].alignment = PP_ALIGN.CENTER
    rr = circ.text_frame.paragraphs[0].runs[0]
    rr.font.size, rr.font.bold, rr.font.color.rgb, rr.font.name = Pt(16), True, WHITE, FONT
    textbox(s, MARGIN + Inches(0.8), y - Inches(0.05), Inches(7.2), Inches(0.8),
            [(head, {"bold": True, "size": 14, "color": GREEN}), (body, {"size": 12})], space_after=0)
stat(s, Inches(9.0), Inches(1.4), Inches(1.9), "4", "services, one per context")
stat(s, Inches(10.9), Inches(1.4), Inches(1.9), "8", "Kafka topics")
stat(s, Inches(9.0), Inches(3.1), Inches(1.9), "13", "REST endpoints")
stat(s, Inches(10.9), Inches(3.1), Inches(1.9), "187", "tests, no network, no key", color=ORANGE)
textbox(s, Inches(9.0), Inches(4.9), Inches(3.8), Inches(1.8),
        "Before the session: Kafka up, four services up with the key set, scripts/demo.ps1 run once so a risk and a trace exist. Sales rate 4 a minute so the 400-unit batch stays at risk.",
        size=11, color=MUTED)
notes(s, "7:30 to 10:00, two and a half minutes, rehearsed. If the network or the model is slow, play "
         "the backup recording of the same run. Use a model whose daily quota is intact (rehearse on a "
         "different model name or a second key).")

# ------------------------------------------------------------- slide 10: AI-driven development
s = new_slide()
title(s, "AI-driven development: a gated pipeline, logged end to end", "Reuben", "10:00")
phases = [("1  Inspect", "read the specifications; halt on any inconsistency (28 found, 7 blocking)"),
          ("2  Skeleton", "Maven multi-module, four services, contracts"),
          ("3  Tests first", "integration tests from the OpenAPI contract; they must fail"),
          ("4  Implement", "until the tests pass; never weaken a test")]
pw = (W - 2 * MARGIN - Inches(0.6)) / 4
for i, (head, body) in enumerate(phases):
    card(s, MARGIN + i * (pw + Inches(0.2)), Inches(1.3), pw, Inches(1.55), head, body, body_size=12, head_size=14)
card(s, MARGIN, Inches(3.1), Inches(3.9), Inches(2.6), "Accepted",
     "The Phase 1 halt: sales-service had no permitted way to obtain a price. Resolved with the PriceSnapshotFeed port, which later made the swap to Kafka a zero-line change (ADR 0001).", body_size=12)
card(s, MARGIN + Inches(4.15), Inches(3.1), Inches(3.9), Inches(2.6), "Rejected",
     "The agent wanted kafka-streams-test-utils and a second LangChain4j provider. Both outside the approved list: a halt, not a choice. The coordinator later approved three test dependencies; none was needed.", body_size=12, head_color=ORANGE)
card(s, MARGIN + Inches(8.3), Inches(3.1), Inches(3.85), Inches(2.6), "Modified",
     "Repository interfaces were declared in domain (Evans). The coordinator ruled they belong in the persistence layer (L4 slide 34); moved with git mv, cost recorded in ADR 0005.", body_size=12)
textbox(s, MARGIN, Inches(5.9), W - 2 * MARGIN, Inches(0.9),
        "Evidence in the repository: 7 specifications, 6 ADRs, 25 AI session logs with assumptions and deviations, prompts, guard tests, two live-run logs. Humans made the architectural decisions; the agent implemented them.",
        size=12, color=MUTED)
notes(s, "10:00 to 11:00. The four phases (L3, the coordinator's own case study), then one example each.")

# ------------------------------------------------------------- slide 11: limitations, lessons
s = new_slide()
title(s, "Limitations stated, and what we learned", "Reuben", "11:00")
card(s, MARGIN, Inches(1.3), Inches(5.9), Inches(5.4), "Known limitations",
     ["G-3: trading hours are configuration (24 clock hours by default)",
      "G-4: a SKU that stops selling is not re-assessed; the fix is a punctuator, not built",
      "G-7: trace saved and recommendation published in two steps; a crash between them loses one, never duplicates",
      "FR3 write-offs, FR7 history, FR10 automatic expiry: specified, not built, and the briefing says so",
      "5 October: the model's daily free quota ran out mid-demo; a 500 became a 503 AGENT_UNAVAILABLE with ten new tests"],
     body_size=13)
card(s, Inches(6.9), Inches(1.3), Inches(5.8), Inches(5.4), "Lessons",
     ["Specifications were the leverage: the halt on 28 inconsistencies was worth more than any generated code",
      "Constraints survive only when mechanical: every rule that was only a document drifted once; every rule that became a test stayed true",
      "Quality rose where the human knew what to look for; the coordinator's three rulings settled more than argument",
      "A port makes the model replaceable, and hides its failures until the boundary case is written down"],
     head_color=ORANGE, body_size=13)
notes(s, "11:00 to 11:40. Name the limitations plainly; a marker who finds them unstated would ask what "
         "else was missed. One lesson each, with the example.")

# ------------------------------------------------------------------------- slide 12: thank you
s = new_slide(dark=True)
textbox(s, MARGIN, Inches(0.8), W - 2 * MARGIN, Inches(1.0), "Thank you. Questions?", size=40, bold=True, color=WHITE)
qa = [
    ("Why not put the margin floor in the prompt?", "A prompt cannot be unit-tested and a model can ignore it. The floor is a domain rule with tests; a guard test fails the build if the words even appear in insight-service."),
    ("Does insight-service count as a bounded context?", "It has its own model (risk, velocity, recommendation), its own data, its own rule (RiskAssessmentPolicy), and talks to the others only through events. Dr Su confirmed a CQRS read side carries a domain layer."),
    ("Where is the SQL?", "The coordinator confirmed the Kafka Streams DSL is the expected SQL-like analysis; Section 3.5 writes every query both ways."),
    ("What if the LLM is down?", "The service starts, FR12 and FR13 keep running, FR14 logs the trigger as not deliberated, FR15 answers 503. Nothing in inventory, sales or pricing depends on the model."),
]
for i, (q, a) in enumerate(qa):
    y = Inches(2.0) + i * Inches(1.25)
    textbox(s, MARGIN, y, W - 2 * MARGIN, Inches(1.2),
            [(q, {"bold": True, "size": 15, "color": PALE}), (a, {"size": 12, "color": WHITE})], space_after=1)
textbox(s, MARGIN, Inches(6.9), W - 2 * MARGIN, Inches(0.4),
        "Repository: github.com/watchcat2077/Freshflow (private)  ·  Group 06 “318 Hero”", size=11, color=PALE)
notes(s, "11:40 to 12:00. The four answers the plan prepared; every member answers for the part they presented.")

prs.save(OUT)
shutil.copy2(OUT, MIRROR)
print(OUT)
print(MIRROR)
