# Specification 2 — Functional Requirements

**Project**: FreshFlow
**Version**: 1.0 — 12 August 2026

---

## Legend

| Tag | Meaning |
|---|---|
| `[MVP-CORE]` | Must be working by **Sunday 30 August 2026** (end of Week 5). Non-negotiable. |
| `[MVP-PLUS]` | Target for **Sunday 13 September 2026** (end of Week 7). Cut if at risk. |
| `[POST-MVP]` | **Weeks 8–11.** Specified now, built later. |

**Re-baselined 26 August 2026** (Phase 1 finding C-1). The legend previously anchored to
15–16 August 2026, dates that had passed. The anchors above are the authoritative
milestone dates and supersede every date stated elsewhere in this document.

Requirement count: 16 after the 26 August 2026 split of FR6 into FR6a/FR6b (course minimum
is 12). The count previously read 14, which was already inconsistent with the FR1-FR15
numbering before that split.
Real-time analysis requirements: FR12, FR13 (course minimum is 2).
Agentic AI requirements: FR14, FR15 (course minimum is 2).

---

## Inventory & Batch Context

### FR1 — Register goods receipt `[MVP-CORE]`

> As a **store manager**, I want to record a delivery as a stock batch with its SKU,
> quantity, expiry date and unit cost, so that expiry risk can be tracked at the
> level of the physical batch rather than the product line.

**Relevance to objectives**: Supports O1. Batch-level granularity is the precondition
for every downstream capability — two deliveries of the same SKU expire on different
days and must be treated as different things.

**Acceptance criteria**
- POST creates a batch and returns its identifier
- Quantity must be positive; expiry must be in the future
- Publishes `batch-received`

---

### FR2 — Deduct stock on sale `[POST-MVP]`

> As a **store manager**, I want batch stock to decrease automatically when items are
> sold, so that remaining quantity is accurate without manual counting.

**Relevance**: Supports O1. Risk detection is meaningless if remaining quantity is stale.

**Acceptance criteria**
- Consumes `item-sold`
- Deducts using FEFO (first-expired-first-out) across batches of the same SKU
- Stock on hand must never become negative
- Publishes `stock-level-changed`
- Idempotent with respect to `eventId`

---

### FR3 — Write off an expired batch `[POST-MVP]`

> As a **store owner**, I want expired batches written off with the waste quantity and
> cost value recorded, so that waste cost is measurable.

**Relevance**: Supports O1 and O5. Provides the baseline against which the system's
value is judged.

---

### FR4 — Query stock position and remaining shelf life `[POST-MVP]`

> As a **shift supervisor**, I want to query current stock and remaining shelf life for
> any batch or SKU, so that I can verify what the system is telling me.

**Relevance**: Supports O3 and O4. Also serves as an agent tool.

---

## Sales Context

### FR5 — Record a sale transaction `[MVP-CORE]`

> As a **checkout operator**, I want to record a transaction containing one or more
> scanned line items, so that sales are captured as they happen.

**Relevance**: Supports O1. This is the source of the high-frequency event stream on
which all real-time analysis depends.

**Acceptance criteria**
- POST accepts a transaction with one or more line items
- Publishes one `item-sold` event per line item
- Each event carries producer-assigned `occurredAt`

---

### FR6a — Apply the base price at point of scan `[MVP-CORE]`

> As a **checkout operator**, I want the current base price for a scanned SKU applied at
> the moment of scan, so that customers are charged correctly when no markdown is running.

**Relevance**: Supports O2. Base-price application is the part of the loop that must work
before any markdown can be layered on top of it.

**Acceptance criteria**
- The price recorded on the line item is the base price in force for that SKU at the
  transaction's `occurredAt`
- `markdownApplied` is `false`
- `unitPrice` on each line item equals `PriceSnapshot.basePrice` for that SKU; `totalAmount`
  is the sum of `unitPrice x quantity` across all line items
- The base price is read from `sales-service`'s own local `PriceSnapshot`. `sales-service`
  does NOT call `pricing-service` — see spec-01 §1.3
- The snapshot is populated through the `PriceSnapshotFeed` inbound port. In Week 5 the bound
  adapter seeds it from static configuration in `application.yml`; from Week 6 the adapter
  subscribes to `price-rule-published`. The port signature is identical in both cases
- A scan of a SKU absent from the snapshot is a `400`, never a silent zero price
- Satisfiable without any `[MVP-PLUS]` or `[POST-MVP]` requirement being built, and without a
  running broker

**Mechanism named for Phase 3** (finding B-5, resolved 26 August 2026). The `201` assertion for
`POST /api/transactions` is now writable: seed the snapshot from test configuration, post the
`singleScan` example, assert `unitPrice` equals the seeded base price and `markdownApplied` is
`false`.

---

### FR6b — Apply live markdowns at point of scan `[MVP-PLUS]`

> As a **checkout operator**, I want any live markdown included in the price at the moment
> of scan, so that a markdown decision actually reaches the till.

**Relevance**: Supports O2. Closes the loop — a markdown decision has no effect until
it reaches the till.

**Acceptance criteria**
- Consumes `markdown-applied` and `markdown-expired`, maintains a local price snapshot
- The price recorded on the line item is the price in force at `occurredAt`
- `markdownApplied` is `true` when a markdown was in force at `occurredAt`

**Open item — residual B-7.** `markdown-expired` is produced by FR10, which is
`[POST-MVP]`. Consuming it is therefore still an `[MVP-PLUS]` criterion depending on a
`[POST-MVP]` requirement. The split above removes the `[MVP-CORE]` dependency, which was
the blocking half; this remainder is flagged for a team ruling, not resolved here.

---

### FR7 — Query transaction history `[POST-MVP]`

> As a **store manager**, I want to query transactions by SKU, till or time range, so
> that I can investigate what happened.

**Relevance**: Supports O4 and O5.

---

## Pricing & Markdown Context

### FR8 — Maintain price rules and margin floors `[MVP-CORE, minimal]`

> As a **store owner**, I want to maintain price rules and margin floors per SKU and
> per category, so that discounting never destroys profitability.

**Relevance**: Supports O2. The margin floor is the business invariant that constrains
the agent.

**MVP scope note**: For MVP, a read endpoint plus seeded configuration data is
sufficient. Full CRUD is `[POST-MVP]`.

---

### FR9 — Create and activate a markdown plan `[MVP-PLUS]`

> As a **shift supervisor**, I want to activate a markdown for a batch at a given
> discount tier and effective time, so that the price change takes effect at the till.

**Relevance**: Supports O1 and O2.

**Acceptance criteria**
- Validates against the margin floor before activation (see FR11)
- Publishes `markdown-applied` on success

---

### FR10 — Expire a markdown plan automatically `[POST-MVP]`

> As a **store owner**, I want markdowns to end automatically at the close of their
> promotion window, so that discounts do not persist unintentionally.

**Relevance**: Supports O2.

---

### FR11 — Reject a markdown that breaches the margin floor `[MVP-PLUS]`

> As a **store owner**, I want any proposed markdown that would breach the margin floor
> to be rejected and the rejection recorded, so that no automated component can
> discount below profitability.

**Relevance**: Supports O2 and O4. **This requirement is the Anti-Corruption Layer in
practice** — it is what makes the LLM agent safe to deploy against a real business rule.

**Acceptance criteria**
- Returns HTTP 422 with a machine-readable rejection reason
- Rejection is recorded and queryable
- Applies identically whether the proposal originated from a human or from the agent

---

## Insight Context

### FR12 — Rolling sell-through velocity `[MVP-PLUS]` — REAL-TIME ANALYSIS

> As a **store manager**, I want to see how fast each SKU is selling right now compared
> with its normal rate, so that I can tell the difference between a slow day and a
> genuine problem.

**Relevance**: Supports O1 and O3. Converts a raw event stream into a decision signal.

**Acceptance criteria**
- Kafka Streams topology consumes `item-sold`, keys by SKU
- Aggregates quantity over a **sliding window**
- Windowing uses the producer-assigned `occurredAt`, never Kafka ingestion time (spec-01 §2.6)
- Compares against a stored velocity baseline held in a `KTable`
- Result exposed through an Interactive Query REST endpoint
- **MVP simplification**: 60-second window for demonstration; production target is a
  60-minute window advancing every 5 minutes

**Which window is reported — ratified 7 September 2026** (team ruling 2, on Week 7 part 1
finding G-4). Neither this requirement nor spec-04 said which of the overlapping windows
represents "right now", nor when a velocity stops being current. Both are user-visible
decisions, so they are specified here rather than left to an implementation:

1. Only windows that have **fully elapsed** are eligible. A window that has started but not
   finished has had only part of its span happen, so its count understates the rate; reporting
   it makes velocity appear to collapse and recover on every advance.
2. Of those, the **latest** is reported. With the window advancing every 5 seconds, the value
   is at most one advance-interval behind real time.
3. If even that window ended **more than one window length ago**, there is no current velocity.
   `GET /api/insight/velocity/{sku}` answers `404`, as it does before any sale. It MUST NOT
   report the last window it happens to hold.

Rule 3 is the one that carries weight downstream. spec-04 phrases the 404 as "no windowed data
for this SKU **yet**", which covers the warm-up case and is silent on the aged-out case; the
same answer is owed in both. FR13 multiplies velocity by remaining trading hours, so a stale
velocity presented as a live one projects sales that are not happening and reports a batch as
safe when it is not.

**Where the baseline comes from — ADR 0003, 7 September 2026** (team ruling 1, on Week 7 part 1
finding G-1). No requirement said. It is seeded from configuration
(`freshflow.insight.velocity.baselines`, spec-01 §2.7) as an **explicit MVP simplification**,
entering the service through the `VelocityBaselineStore` port; a measured baseline computed
from a longer window over the same `item-sold` stream replaces the seeding adapter alone. Until
it does, `velocityRatio` is a ratio against an asserted normal rate rather than a measured one,
and must be described that way wherever a result derived from it is presented.

A SKU with no stored baseline is not an error: the endpoint returns the velocity and omits
`baselineVelocity` and `velocityRatio`. It MUST NOT report a baseline of zero, which would read
as "selling at zero times its normal rate" when the normal rate is merely unknown.

---

### FR13 — Expiry risk detection `[MVP-PLUS]` — REAL-TIME ANALYSIS

> As a **store manager**, I want to be told which batches will not sell out before they
> expire, early enough that a markdown can still change the outcome.

**Relevance**: Supports O1. This is the system's central insight.

**Acceptance criteria**
- **Target implementation**: stream-table join between the velocity stream and a
  `KTable` of live batch state built from `batch-received` and `stock-level-changed`;
  computes projected sales over remaining trading hours and compares against
  remaining quantity
- **MVP simplification**: a threshold test on windowed count is acceptable for the
  17 August demonstration. The join is the Week 7 target.
- Publishes `sell-through-risk-detected`

---

### FR14 — Agentic markdown recommendation `[POST-MVP]` — AGENTIC AI

> As a **shift supervisor**, when a batch is flagged at risk I want a specific
> recommendation — what discount, starting when, and why — so that I do not have to guess.

**Relevance**: Supports O2, O3 and O4. Replaces tacit individual judgement with a
consistent, explainable, auditable decision.

**Agentic design pattern**: ReAct / tool use. The agent decides which evidence it needs
and retrieves it through tool calls, rather than being handed a fixed input.

**Tools**

| Tool | MVP | Returns |
|---|---|---|
| `getBatchDetails(batchId)` | ✅ | Remaining quantity, expiry timestamp, unit cost |
| `proposeMarkdown(batchId, tier, effectiveFrom, rationale)` | ✅ | Publishes `markdown-recommended` |
| `getSellThroughRate(sku, window)` | Week 8 | Current velocity and baseline ratio |
| `getMarkdownHistory(sku)` | Week 8 | Past markdowns and observed effect |
| `getMarginFloor(sku)` | Week 9 | Minimum acceptable margin |
| `queryStorePolicy(category)` | Week 9 | Retrieval over store SOP documents |

**Acceptance criteria**
- Triggered by `sell-through-risk-detected`
- Full tool-call trace persisted and queryable by `agentTraceId`
- Output includes a written rationale
- The agent **proposes only**. It MUST NOT write prices directly. Enforcement of the
  margin floor belongs to `pricing-service` (see FR11).

**Re-tagged 26 August 2026** (fix N-B). Previously `[MVP-PLUS]`. The agent is Week 9 per
CLAUDE.md, which falls outside the `[MVP-PLUS]` anchor of Sunday 13 September 2026 (end of
Week 7) in the re-baselined legend above. `[POST-MVP]` (Weeks 8-11) is the tag consistent
with that schedule. The Week 8 / Week 9 tool rows in the table above are unchanged and are
now consistent with the requirement tag rather than ahead of it.

---

### FR15 — End-of-day waste and markdown briefing `[POST-MVP]` — AGENTIC AI

> As a **store owner**, I want a plain-language end-of-day summary of what was marked
> down, what sold through and what was wasted, so that I can review the day without
> reading a dashboard.

**Relevance**: Supports O5.

---

## Traceability Summary

| Objective | Requirements |
|---|---|
| O1 Reduce write-off value | FR1, FR2, FR3, FR9, FR12, FR13 |
| O2 Protect gross margin | FR6a, FR6b, FR8, FR9, FR10, FR11, FR14 |
| O3 Remove judgement burden from staff | FR4, FR12, FR14 |
| O4 Make decisions auditable | FR4, FR7, FR11, FR14 |
| O5 End-of-day visibility | FR3, FR7, FR15 |
