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

**Status at submission (13 October 2026).** Built and tested: FR1, FR2, FR4, FR5, FR6a, FR6b,
FR8, FR9, FR11, FR12, FR13, FR14 and FR15, thirteen of the sixteen rows. Out of scope by team
decision: FR3 write-offs, FR7 transaction history and FR10 automatic expiry, with the
`batch-written-off` and `markdown-expired` topics. The tags on each requirement record the
original plan and are kept as written; the Requirements to Tactics table at the end of this
document shows what was built.

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

**Acceptance criteria** (added 4 October 2026)
- `GET /api/batches/{batchId}` returns one batch with `remainingShelfLifeHours` computed at
  the moment of the request; 404 when the batch does not exist
- `GET /api/batches?sku=` returns the ACTIVE batches of a SKU in first-expired-first-out
  order, the same order FR2 deducts them in; an empty array is a valid answer
- Both are read-only and publish nothing

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
- Consumes `markdown-applied` and maintains the markdown in the local price snapshot
- The price recorded on the line item is the price in force at `occurredAt`
- `markdownApplied` is `true` when a markdown was in force at `occurredAt`
- A markdown is in force only inside its promotion window; a scan after `effectiveTo`
  reverts to the base price without any further event

**Residual B-7 — ruled 4 October 2026.** `markdown-expired` is produced by FR10, which
stays `[POST-MVP]` and unbuilt. FR6b does not need it: the promotion window travels on
`markdown-applied`, so sales-service can tell on its own whether a markdown is in force at
`occurredAt`. Consuming `markdown-expired` is therefore removed from this requirement's
criteria and belongs to FR10 if FR10 is ever built.

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

**Acceptance criteria** (extended 4 October 2026)
- `GET /api/prices/{sku}` returns the rule in force with its margin floor and, when a
  markdown plan is ACTIVE inside its window, the discounted `effectivePrice` and the
  `activeDiscountTier`
- `PUT /api/prices/{sku}` creates or replaces a rule; the domain rejects a base price below
  unit cost and a floor outside [0, 1]
- Every create or replace is announced on `price-rule-published`
- Category-level floors remain out of scope; the floor is per SKU

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
- Publishes `sell-through-risk-detected`, on the **transition** into risk rather than on
  every re-assessment. A detection happens once; the standing list of what is currently
  at risk is `GET /api/insight/risks`
- All time arithmetic uses the producer-assigned `occurredAt`, never Kafka ingestion time
  (spec-01 §2.6). "Now" is the end of the closed velocity window the assessment was made
  from
- The projected shortfall must exceed a configured threshold before a batch is reported,
  and an expired or depleted batch is never reported — FR13 exists to catch batches early
  enough that a markdown can still change the outcome, and an expired batch is FR3's
  write-off

**KNOWN LIMITATION — a SKU with no sales at all is not re-assessed** (team ruling
7 September 2026, accepted on Week 7 part 2 finding G-4; **still open at submission**).

Risk assessment is currently triggered by a **closing velocity window**, and a velocity
window only closes because sales are arriving. The consequence is stated plainly because it
cuts against this requirement's own user story:

- A batch that becomes at risk **because its SKU stopped selling entirely** may never be
  flagged. That is exactly the "slow day or a genuine problem" case FR12 and FR13 exist to
  separate, and it is the case the current trigger cannot see.
- A SKU still selling, however slowly, is assessed normally. Slowing down is detected;
  stopping dead is not.
- Batches **already** flagged remain flagged and remain listed by
  `GET /api/insight/risks`, because the read model is a stored assessment rather than a
  live recomputation. The endpoint does not go stale or empty when sales stop; what does
  not happen is a *new* alarm.

This is accepted as a known limitation, not as the intended design; the punctuator below was
not built and the limitation stands at submission (13 October 2026). **Intended fix**: a stream-time punctuator that re-assesses every live batch on a
schedule, independent of whether any sale arrived. That is a design decision with its own
event-time complications — a punctuator advancing on stream time still needs records to
advance it, so a wall-clock schedule and its interaction with `occurredAt` must be settled
first — which is why it is scheduled rather than improvised.

**OPEN — "remaining trading hours" is undefined** (Week 7 part 2 finding G-3; **open at
submission: the default of 24 stands**). No specification describes the trading day, so the projection is configured through
`freshflow.insight.risk.trading-hours-per-day` (spec-01 §2.7), defaulting to 24. This is not
a cosmetic default: a shop open twelve hours a day sells half as much before expiry as the
clock suggests, so the setting changes which batches are reported. Until the team sets real
opening hours, every figure derived from it projects over clock hours.

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
| `getSellThroughRate(sku)` | ✅ | Current velocity and baseline ratio |
| `getMarkdownHistory(sku)` | Not built | Past markdowns and observed effect |
| `getMarginFloor(sku)` | Rejected by design | Minimum acceptable margin: the margin floor is `pricing-service`'s rule (FR11, R5) and `insight-service` may not hold it |
| `queryStorePolicy(category)` | Not built | Retrieval over store SOP documents |

**`getSellThroughRate` pulled into the MVP tool set — 10 September 2026** (team ruling R4, with the
FR14 authorisation). It was tagged Week 8. FR12 is built, tested and queryable through
`VelocityQueryService`, so the tool is a thin read over work already done; and without it the agent
would be recommending a discount depth with no measurement of how fast the SKU is actually selling,
which is the evidence FR14's user story assumes. The MVP tool set is therefore **exactly three**:
`getBatchDetails`, `getSellThroughRate`, `proposeMarkdown`. The other three rows were not built and
are out of scope at submission. R4 requires that they appear nowhere in the implementation — not in
the registry, not in a port, not in the prompt text — and `getMarginFloor` could not be built in this
service at all: the margin floor is `pricing-service`'s rule and `insight-service` may not hold it
(R5, spec-06 §7), so that row is rejected by design rather than deferred. The "Re-tagged
26 August 2026" note below refers to those three rows.

**Settled 13 October 2026**: the signature above read `getSellThroughRate(sku, window)`, and
spec-06 §4 implements it as `getSellThroughRate(sku)`. The window is configuration (spec-01 §2.7)
and which window counts as current was settled by team ruling 2 above, so a `window` argument would
be one the implementation must ignore. The table shows the implemented signature.

**Acceptance criteria**
- Triggered by `sell-through-risk-detected`
- Full tool-call trace persisted and queryable by `agentTraceId`
- Output includes a written rationale
- The agent **proposes only**. It MUST NOT write prices directly. Enforcement of the
  margin floor belongs to `pricing-service` (see FR11).

**Re-tagged 26 August 2026** (fix N-B). Previously `[MVP-PLUS]`. The agent is Week 9 per
CLAUDE.md, which falls outside the `[MVP-PLUS]` anchor of Sunday 13 September 2026 (end of
Week 7) in the re-baselined legend above. `[POST-MVP]` (Weeks 8-11) is the tag consistent
with that schedule. The three deferred tool rows in the table above are unchanged and are
consistent with the requirement tag rather than ahead of it.

---

### FR15 — End-of-day waste and markdown briefing `[POST-MVP]` — AGENTIC AI

> As a **store owner**, I want a plain-language end-of-day summary of what was marked
> down, what sold through and what was wasted, so that I can review the day without
> reading a dashboard.

**Relevance**: Supports O5.

**Acceptance criteria** (added 4 October 2026)
- `POST /api/insight/briefings` runs a second agent, behind its own port, with three
  read-only tools: the current risk list, the markdown recommendations made on the
  business date, and the sell-through rate of a SKU
- The agent returns a plain-language summary; the orchestrator computes `riskCount` and
  `recommendationCount` itself from the tool results so no number in the response depends
  on the model
- The briefing and its ordered tool calls are persisted and served by
  `GET /api/insight/briefings/{briefingId}`
- The agent proposes nothing and writes nothing; it reads the Insight context's own views
  and never touches Pricing (team ruling R5 applies unchanged)
- With no LLM configured the endpoint answers 503 and the service otherwise runs normally
- Every test runs against a deterministic stub (ADR 0004)

**Agentic pattern**: tool-using agent with a bounded loop, the same shape as FR14, plus a
deterministic grounding step in the orchestrator. Waste figures depend on FR3 write-offs,
which are not built; the briefing states that explicitly rather than inventing a number.

---

## Requirements to Tactics

**Added 10 September 2026.** This table is the bridge between the requirements above and the
design tactics that realise them. For each requirement it names the **command or query** that
carries it, the **aggregate or read model** it touches, and the **events** it produces or
consumes. It is derived from the requirements above, spec-03 and spec-04; where the three
disagree, they remain authoritative and this table is wrong.

Three things it is meant to make visible at a glance:

- **Which requirements are commands and which are queries.** CQRS is a deliberate choice here,
  not an accident of having a read endpoint. The read side of the Insight context is a
  materialised view maintained by a stream topology (spec-05), not a query over a write model.
- **Which requirements touch an aggregate and which do not.** spec-01 §2.3 assigns a different
  domain-layer pattern per bounded context, so the "Aggregate / read model" column is empty for
  `sales-service` by design (Transaction Script — no aggregate roots) and names a read model
  rather than an aggregate for `insight-service` (a CQRS read side whose domain package holds one
  policy and one view, ADR 0006).
- **Where the event-driven seams are.** A requirement that consumes an event it does not itself
  produce is a bounded-context integration point, and the pairs line up with the context map in
  spec-01 §1.5.

`Built` reflects the state at submission, 13 October 2026. It is a status column, not a
specification: the tags in each requirement above record the original plan.

### Inventory & Batch

| FR | Command / Query | Layer entry point | Aggregate / read model | Produces | Consumes | Built |
|---|---|---|---|---|---|---|
| FR1 | Command `RegisterGoodsReceiptCommand` | `BatchApplicationService.registerGoodsReceipt` | `StockBatch` `<<AR>>` | `batch-received` | — | ✅ |
| FR2 | Command `ApplyItemSoldCommand` | `ApplyItemSoldHandler` | `StockBatch` `<<AR>>` (many), via domain service `FefoDeductionPolicy` | `stock-level-changed` | `item-sold` | ✅ |
| FR3 | Command — write off a batch | — | `StockBatch` `<<AR>>`, `WriteOffRecord` `<<E>>` | `batch-written-off` | — | ✗ out of scope |
| FR4 | Query `GET /api/batches/{batchId}` and `GET /api/batches?sku=` | `BatchApplicationService.findBatch`, `findActiveBatches` | `StockBatch` → `BatchView` | — | — | ✅ |

FR2 is the only requirement in this context whose rule spans **more than one aggregate**: FEFO
selects across every batch of a SKU, so it is a domain service and not a method on `StockBatch`
(spec-03 §1). FR2 is also the producer of `stock-level-changed`, which is why it had to be built
before FR13 could join against live batch state.

### Sales

`sales-service` is designated **Transaction Script** (spec-01 §2.3). It declares no aggregate
roots and no repository interfaces; the `domain` package holds plain data structures only, and
the invariants live in the application service (spec-01 §2.5). The "Aggregate / read model"
column is therefore deliberately empty of aggregate roots.

| FR | Command / Query | Layer entry point | Aggregate / read model | Produces | Consumes | Built |
|---|---|---|---|---|---|---|
| FR5 | Command `RecordTransactionCommand` | `TransactionApplicationService.recordTransaction` | none — `SaleTransaction` / `LineItem` are plain data | `item-sold`, one per line item | — | ✅ |
| FR6a | Same command; the price is applied inside it | `TransactionApplicationService`, reading `PriceSnapshot` through the `PriceSnapshotFeed` port | `PriceSnapshot` (local, non-transactional) | — | `price-rule-published` | ✅ |
| FR6b | Same command, markdown-aware | `TransactionApplicationService` | `PriceSnapshot` | — | `markdown-applied` (`markdown-expired` is out of scope) | ✅ |
| FR7 | Query — transactions by SKU, till or time range | — | a read-side abstraction, **not** the FR5 write port (spec-01 §2.3) | — | — | ✗ out of scope |

FR6a is not a separate command. It is the pricing step *inside* FR5's transaction script, which
is why the two share a row entry point: a scan and its price are one use case, and separating
them would put the price in the presentation layer or in a second round trip.

### Pricing & Markdown

| FR | Command / Query | Layer entry point | Aggregate / read model | Produces | Consumes | Built |
|---|---|---|---|---|---|---|
| FR8 | Command `PUT /api/prices/{sku}` (`UpsertPriceRuleCommand`); Query `GET /api/prices/{sku}` | `PriceApplicationService.upsert`, `findPrice` | `PriceRule` `<<AR>>` (single-entity aggregate) → `PriceView` | `price-rule-published` (via `PriceRulePublicationService`) | — | ✅ |
| FR9 | Command `POST /api/markdown-plans` (`CreateMarkdownPlanCommand`) | `MarkdownPlanApplicationService.create`, shared by the endpoint and the `markdown-recommended` consumer | `MarkdownPlan` `<<AR>>` | `markdown-applied` | `markdown-recommended` (FR14) | ✅ |
| FR10 | Command — expire a plan on its window closing | — | `MarkdownPlan` `<<AR>>` | `markdown-expired` | — | ✗ out of scope |
| FR11 | Invariant on FR9's command, not a command of its own | domain — `MarginFloorPolicy` over `MarginFloor` | `MarkdownPlan` `<<AR>>`, `PriceRule` `<<AR>>` | — | — | ✅ a breaching plan is persisted `REJECTED` and answered 422 |

FR11 has no row of its own in the command catalogue because it **is** not a command: it is the
business invariant that FR9's command must satisfy. That placement is the point of the
requirement. It is enforced in `pricing-service`'s domain layer and applies identically whether
the proposal came from a human (FR9) or from the agent (FR14) — the Anti-Corruption Layer of
spec-01 §1.5, expressed as a domain rule rather than as a guard on an endpoint.

### Insight

`insight-service` is a **CQRS read side** (spec-01 §2.3); its domain package holds
`RiskAssessmentPolicy` and `BatchRiskView` only (ADR 0006). FR12 and FR13 name read models
maintained by the Kafka Streams topology and answered from a state store by Interactive Query,
never from a write model; FR14 and FR15 name the persisted records of the two agents. spec-05
specifies the topology in full and spec-06 the agents.

| FR | Command / Query | Layer entry point | Aggregate / read model | Produces | Consumes | Built |
|---|---|---|---|---|---|---|
| FR12 | Query `GET /api/insight/velocity/{sku}` | `VelocityQueryService` | `SkuVelocityView` — stores `velocity-windows`, `velocity-baselines` | — | `item-sold`, `velocity-baseline-published` (internal, Table B) | ✅ |
| FR13 | Query `GET /api/insight/risks` | `RiskQueryService`; the assessment itself is made in the topology by `RiskAssessmentPolicy` | `BatchRiskView` — stores `batch-states`, `batch-risks` | `sell-through-risk-detected`, on the transition into risk only | `item-sold`, `batch-received`, `stock-level-changed` | ✅ |
| FR14 | Query `GET /api/insight/agent-traces/{agentTraceId}`; the agent's own action is the `proposeMarkdown` tool call | `MarkdownAgentOrchestrator.onRiskDetected` | `AgentTraceRecord` | `markdown-recommended` | `sell-through-risk-detected` | ✅ |
| FR15 | Command `POST /api/insight/briefings`; Query `GET /api/insight/briefings/{briefingId}` | `BriefingOrchestrator`; `BriefingQueryService` | `EndOfDayBriefing`, grounded on this service's own stores through three read-only tools | — | — (reads the risk, velocity and trace stores) | ✅ |

FR12 and FR13 are both *queries* in this table, which understates them and is worth naming: the
work is done continuously by the topology as events arrive, and the query merely reads the
answer. That asymmetry — a write path made of streams rather than of commands — is what
distinguishes this context's CQRS read side from a read endpoint over a repository.

FR13's `sell-through-risk-detected` is produced by `insight` and consumed by `insight` today,
and is nonetheless an **integration** event (spec-01 §2.6 Table A): FR14's agent consumes it and
carries the result to `pricing`.

### Legend

| Symbol | Meaning |
|---|---|
| ✅ | Built and covered by tests |
| ◐ | Partially built; the remainder is tagged in the requirement above |
| ✗ | Not built |
| `<<AR>>` / `<<E>>` | Aggregate Root / Entity, per spec-03 |

---

## Traceability Summary

| Objective | Requirements |
|---|---|
| O1 Reduce write-off value | FR1, FR2, FR3, FR9, FR12, FR13 |
| O2 Protect gross margin | FR6a, FR6b, FR8, FR9, FR10, FR11, FR14 |
| O3 Remove judgement burden from staff | FR4, FR12, FR14 |
| O4 Make decisions auditable | FR4, FR7, FR11, FR14 |
| O5 End-of-day visibility | FR3, FR7, FR15 |
