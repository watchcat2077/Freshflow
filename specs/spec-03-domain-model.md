# Specification 3 — Domain Model

**Project**: FreshFlow
**Version**: 1.0 — 12 August 2026

---

## 0. Bounded Context Rationale

The single most important modelling decision in this system is that **"product" means
three different things**, with genuinely different lifecycles and invariants:

- To **Inventory**, a product is a *physical batch* — a specific delivery of a specific
  quantity with a specific expiry date. Two cartons of the same yoghurt received on
  different days are different things.
- To **Sales**, a product is a *SKU with a price at the moment of scan*. The batch is
  invisible; what matters is the barcode and the price in force when the item crossed
  the scanner.
- To **Pricing**, a product is a *set of cost and margin constraints* — unit cost,
  retail price, margin floor, applicable discount tiers. Physical quantity is irrelevant.

Forcing these into one shared model would produce an anaemic `Product` class carrying
every attribute for every purpose, with invariants that contradict one another.
Each context therefore maintains its own ubiquitous language.

**Insight** is a read-side context in the CQRS sense: it owns no transactional business
data, subscribes to events from the other three, and maintains materialised read models.

---

## 1. Sub-domain: Inventory & Batch

**Implementation pattern**: Domain Model (POJO, persistence ignorant)

### `StockBatch` `<<Aggregate Root>>`

| Field | Type | Notes |
|---|---|---|
| `batchId` | `BatchId` `<<VO>>` | Identity |
| `sku` | `Sku` `<<VO>>` | |
| `receivedQuantity` | `Quantity` `<<VO>>` | Immutable after creation |
| `remainingQuantity` | `Quantity` `<<VO>>` | Mutable |
| `expiryAt` | `ExpiryDate` `<<VO>>` | |
| `unitCost` | `Money` `<<VO>>` | |
| `status` | `BatchStatus` enum | ACTIVE, DEPLETED, WRITTEN_OFF |

**Behaviour**
```
deduct(Quantity q) : void
writeOff()         : WriteOffRecord
isExpired(Instant now) : boolean
remainingShelfLife(Instant now) : Duration
```

**Invariants — enforced inside the aggregate**
1. `remainingQuantity` must never be negative.
2. `deduct` on a WRITTEN_OFF or expired batch is rejected.
3. `receivedQuantity` and `unitCost` are immutable after creation.
4. `expiryAt` must be after the receipt timestamp.
5. Transition to DEPLETED occurs automatically when `remainingQuantity` reaches zero.

### `GoodsReceipt` `<<Entity>>` — out of scope
`receiptId`, `batchId`, `receivedAt`, `supplierRef`. Not built: the receipt is represented by the
`StockBatch` it creates; `supplierRef` is accepted by `POST /api/batches` for compatibility with
delivery documents and is not persisted in this iteration.

### `WriteOffRecord` `<<Entity>>` — out of scope
`writeOffId`, `batchId`, `quantity`, `costValue` (`Money`), `writtenOffAt`, `reason`. Not built
(FR3 is out of scope); `StockBatch.writeOff()` and the `WRITTEN_OFF` status remain as the aggregate's
own transition, with no record of their own.

### Value Objects
`BatchId`, `Sku`, `Quantity` (non-negative integer), `ExpiryDate`, `Money` (amount + currency)

### Repository interface — declared in `infrastructure.repository`
**Corrected 29 September 2026** (ADR 0005, subject coordinator's ruling). This heading
previously read "declared in `domain`".
```
StockBatchRepository
  save(StockBatch) : void
  findById(BatchId) : Optional<StockBatch>
  findActiveBySkuOrderByExpiryAsc(Sku) : List<StockBatch>   // FEFO
```

### Domain Service
`FefoDeductionPolicy` — selects which batches to deduct from across multiple batches of
the same SKU. Spans multiple aggregates, therefore does not belong to any single one.

---

## 2. Sub-domain: Sales

**Implementation pattern**: Transaction Script — no aggregates

Domain complexity here is low: the context records what was scanned and at what price.
Introducing aggregate roots and aggregate-oriented repositories would be over-design. The
application service holds the behaviour, and the types below are plain data structures it
assembles and passes on.

**Corrected 26 August 2026** (ADR 0002, team ruling on Phase 4 finding V-2). This paragraph
previously read "The application service orchestrates directly against JPA entities." That was
wrong and it contradicted spec-01 §2.2, which is a HARD CONSTRAINT. The JPA entities cannot live
in `domain`, because §2.2 forbids a domain package from importing JPA; they therefore live in
`infrastructure`, and an application service importing them would make `application` depend on
`infrastructure`, which §2.2 does not permit in any service.

What `sales-service` does instead: the application service depends on a **write-only,
single-method persistence port declared in the application layer**, implemented by a JPA adapter
in `infrastructure`. spec-01 §2.3 sets out precisely what this may and may not be — no query
methods, no method returning an aggregate. The dependency arrow points inward throughout, and no
aggregate is introduced.

### `SaleTransaction`
`transactionId`, `tillId`, `occurredAt`, `lineItems`, `totalAmount`

### `LineItem`
`lineItemId`, `sku`, `quantity`, `unitPrice` (`Money`), `markdownApplied` (boolean)

### `PriceSnapshot`
Local read cache owned by `sales-service`. `sales-service` never queries `pricing-service`
synchronously (spec-01 §1.3).

`sku`, `effectivePrice`, `basePrice`, `unitCost`, `category`, `discountTier`,
`effectiveFrom`, `effectiveTo`

Populated through the `PriceSnapshotFeed` inbound port declared in `sales-service`:

| Source | Fields it supplies | Week |
|---|---|---|
| Static configuration in `application.yml` | `sku`, `basePrice`, `unitCost`, `category` | 5 |
| `price-rule-published` | `sku`, `basePrice`, `unitCost`, `category` | 6 onward — replaces the static seed behind the same port |
| `markdown-applied` | `effectivePrice`, `discountTier`, `effectiveFrom`, `effectiveTo` | 6-7, FR6b |
| `markdown-expired` | clears `discountTier` and the window | FR10, `[POST-MVP]` |

Where no markdown is in force, `effectivePrice` equals `basePrice`, `discountTier` is null and
the window is empty. This is the only case that exists in Week 5.

Amended 26 August 2026 (Phase 1 finding B-5). Before this amendment the snapshot was fed only
by the two markdown events, neither of which carries a monetary amount, so `basePrice` had no
source at all. `unitCost` and `category` are carried so the snapshot is a complete projection
of the published price rule.

**Rule**: the price recorded on a `LineItem` is the price in force at the transaction's
`occurredAt`, not the price at the time of processing.

---

## 3. Sub-domain: Pricing & Markdown

**Implementation pattern**: Domain Model (POJO, persistence ignorant)

### `MarkdownPlan` `<<Aggregate Root>>`

| Field | Type | Notes |
|---|---|---|
| `markdownPlanId` | `MarkdownPlanId` `<<VO>>` | Identity |
| `batchId` | `BatchId` `<<VO>>` | |
| `sku` | `Sku` `<<VO>>` | |
| `discountTier` | `DiscountTier` `<<VO>>` | Enum-like: 20/30/50/60 percent (60 added 4 October 2026) |
| `window` | `PromotionWindow` `<<VO>>` | effectiveFrom, effectiveTo |
| `status` | `MarkdownStatus` enum | PROPOSED, ACTIVE, EXPIRED, REJECTED |
| `origin` | `ProposalOrigin` enum | HUMAN, AGENT |
| `agentTraceId` | `String` | Nullable; set when origin is AGENT |
| `rejectionReason` | `String` | Nullable |

**Behaviour** (B-6 ruled 4 October 2026 — see the note below)
```
activate(MarginFloorAssessment assessment) : void   // throws MarginFloorBreach when assessment.breached()
expire()  : void
reject(String reason) : void
discountedPrice() : Money                            // set on activation, from the assessment
```

> **B-6 — RULED 4 October 2026.** `activate(...)` receives the `MarginFloorAssessment` that
> `MarginFloorPolicy` produced for this plan's tier against the SKU's `PriceRule`. The
> assessment carries `basePrice`, `unitCost`, `resultingPrice`, `resultingMarginRatio` and
> the `MarginFloor`, so invariant 1 is evaluated inside the aggregate from numbers the
> domain service computed, and the aggregate never needs a `PriceRule` reference of its own.
> If the assessment is breached the aggregate records itself REJECTED with the assessment's
> message and throws `MarginFloorBreach`; the rejected plan is persisted so the refusal is
> queryable (FR11). Additional fields: `rationale` (String, nullable) and `createdAt`.

**Invariants — enforced inside the aggregate**
1. A plan may only be activated if the discounted price is at or above the margin floor.
2. `effectiveTo` must be after `effectiveFrom`.
3. Only a PROPOSED plan may be activated. Only an ACTIVE plan may expire.
4. A REJECTED plan is terminal and cannot be reactivated.
5. `origin` is immutable after creation.

### `PriceRule` `<<Aggregate Root>>` (a single-entity aggregate)

An entity by nature (its identity, the `sku`, stays fixed while base price, cost and floor change),
and the root of an aggregate that contains nothing else. It is modelled as a root because it has its
own consistency boundary and its own repository, `PriceRuleRepository`, and a repository belongs to
an aggregate (L4 slide 27). `MarkdownPlan` never holds a reference to it; `MarginFloorPolicy` reads
both.

| Field | Type | Notes |
|---|---|---|
| `sku` | `Sku` `<<VO>>` | Identity |
| `basePrice` | `Money` `<<VO>>` | |
| `unitCost` | `Money` `<<VO>>` | |
| `category` | `String` | |
| `marginFloor` | `MarginFloor` `<<VO>>` | Minimum acceptable margin for this SKU. Exposed as `marginFloorRatio` by `GET /api/prices/{sku}` |

`marginFloor` was added on 26 August 2026 to resolve Phase 1 finding B-1 **for the
`[MVP-CORE]` read path only** — it gives `GET /api/prices/{sku}` a value to return.

**Status of the items that amendment left open (updated 4 October 2026):**

- **Category-level floors.** FR8 requires floors "per SKU **and** per category". Only the
  SKU-level floor is modelled; there is no `CategoryMarginFloor` entity and no
  SKU-over-category precedence rule. **Ruled out of scope on 4 October 2026** and stated as
  such in FR8's acceptance criteria; the SKU-level floor is what the agent is checked against.
- **B-3** — `MarginFloorPolicy` return type. **Ruled 4 October 2026**, see the Domain Service
  section below. Implemented.
- **B-6** — `MarkdownPlan.activate(...)` signature. **Ruled 4 October 2026**, see the Behaviour
  section above. Implemented.

### Value Objects
`MarkdownPlanId`, `DiscountTier` (PERCENT_20, PERCENT_30, PERCENT_50, PERCENT_60 — the
fourth tier was added on 4 October 2026 to match spec-04 and the `contracts` enum; it exists
so that a breach can be demonstrated against the seeded rule), `PromotionWindow`,
`MarginFloor` (minimum acceptable margin as a ratio), `MarginFloorAssessment`, `Money`,
`Sku`, `BatchId`

### Domain Service
`MarginFloorPolicy` (B-3 ruled 4 October 2026)
```
assess(PriceRule rule, DiscountTier tier) : MarginFloorAssessment

MarginFloorAssessment <<VO>>
  basePrice            : Money
  unitCost             : Money
  requestedTier        : DiscountTier
  resultingPrice       : Money        // basePrice x (1 - tier/100), half-up to cents
  resultingMarginRatio : BigDecimal   // (resultingPrice - unitCost) / resultingPrice, scale 4
  floor                : MarginFloor
  breached()           : boolean      // resultingMarginRatio < floor.ratio()
  message()            : String       // "Discount tier PERCENT_60 yields margin -0.1667, below floor 0.15"
```

> **B-3 — RULED 4 October 2026.** The policy returns a value object rather than a boolean,
> so the numbers spec-04's `MarginFloorRejection` reports are the numbers the domain
> computed, and nothing outside the domain recomputes a margin. The margin ratio is gross
> margin as a fraction of the selling price, the usual retail definition. With the seeded
> SKU-YOG-1KG (base 4.50, cost 2.10, floor 0.15): PERCENT_20 gives 3.60 and 0.4167;
> PERCENT_30 gives 3.15 and 0.3333; PERCENT_50 gives 2.25 and 0.0667; PERCENT_60 gives
> 1.80 and -0.1667. The first two pass, the last two breach.
Spans `PriceRule` and `MarkdownPlan`, therefore modelled as a domain service.

**This is the enforcement point of the Anti-Corruption Layer.** It applies identically
regardless of whether the proposal originated from a human or from the LLM agent.

### Repository interface — declared in `infrastructure.repository`
**Corrected 29 September 2026** (ADR 0005, subject coordinator's ruling). This heading
previously read "declared in `domain`".
```
MarkdownPlanRepository
  save(MarkdownPlan) : void
  findById(MarkdownPlanId) : Optional<MarkdownPlan>
  findActiveBySku(Sku) : List<MarkdownPlan>            // status ACTIVE, whatever the window
  findByAgentTraceId(String) : Optional<MarkdownPlan>   // added 4 October 2026, see below
```

**`findByAgentTraceId` added 4 October 2026** (FR9/FR11 implementation,
`docs/ai-log/2026-10-04-fr9-fr11-pricing.md`). The `markdown-recommended` consumer must be
idempotent on the agent's trace id: the same recommendation may be redelivered, or republished
under a fresh `eventId`, and must yield one plan. The trace is the natural key because FR14 records
at most one recommendation per deliberation (spec-06 §5). `findActiveBySku` returns plans in status
ACTIVE whether or not their window contains now; whether a plan is in force at an instant is the
aggregate's own answer, `MarkdownPlan.isInForceAt(Instant)`, which the application layer asks with
its clock.

---

## 4. Sub-domain: Insight

**Implementation pattern**: CQRS read models, with a small `domain` package for the rules
that compute them (ADR 0006, 4 October 2026, on the subject coordinator's ruling that a CQRS
context has a domain layer). Until that date this section read "no domain layer".

### Domain service and value object (in `domain`)
`RiskAssessmentPolicy` — `assess(batchId, sku, remainingQuantity, expiryAt, velocityPerHour, now) : BatchRiskView`.
Spans batch state and velocity, which belong to two different contexts' events, so it is a
domain service and not a method of either. `BatchRiskView` is the value it produces.

### `EndOfDayBriefing` (FR15, added 4 October 2026)
`briefingId`, `businessDate`, `generatedAt`, `summary`, `riskCount`,
`recommendationCount`, `toolCalls` (ordered list of `{toolName, arguments, result,
timestamp}`). Persisted like `AgentTraceRecord`, for the same reason: a briefing that cannot
be checked against the evidence it was written from is not auditable.

### `SkuVelocityView` (materialised by Kafka Streams)
`sku`, `windowStart`, `windowEnd`, `quantitySold`, `velocityPerHour`, `baselineVelocity`,
`velocityRatio`

### `BatchRiskView`
`batchId`, `sku`, `remainingQuantity`, `hoursToExpiry`, `currentVelocity`,
`projectedSales`, `projectedShortfall`, `detectedAt`

### `AgentTraceRecord`
`agentTraceId`, `triggerEventId`, `batchId`, `toolCalls` (ordered list of
`{toolName, arguments, result, timestamp}`), `finalRecommendation`, `rationale`,
`completedAt`

`AgentTraceRecord` is what makes FR14 auditable. It is persisted, not merely logged.

---

## 5. Cross-Context Concept Mapping

The same word deliberately carries different meaning per context. This table is the
evidence for the bounded context boundaries.

| Concept | Inventory & Batch | Sales | Pricing & Markdown |
|---|---|---|---|
| Product | Physical batch with expiry and cost | SKU plus price at scan time | Cost and margin constraint set |
| Quantity | Remaining stock on hand | Units on a line item | Not modelled |
| Price | Unit cost (acquisition) | Effective price charged | Base price, discount tier, margin floor |
| Time | Expiry instant | Transaction instant | Promotion window |

---

## 6. Shared Kernel — none

There is deliberately no shared kernel. `Money`, `Sku` and `Quantity` are re-declared
independently in each context that needs them. The only shared artefact is the
`contracts` module holding event definitions, which implements the **Published Language**
integration pattern rather than a shared domain model.

Because `Money` is re-declared three times, the representation convention below must be applied
identically in all three. It is a convention, not a shared type: no context imports another's
`Money`.

---

## 7. Monetary Representation Convention

**Added 26 August 2026** (Phase 4 finding F-1).

| Rule | Value |
|---|---|
| Scale | **2 decimal places**, always |
| Rounding | **`RoundingMode.HALF_UP`** |
| Where applied | In the `Money` value object's constructor, in **every** context that declares one |
| Applies to | Every monetary amount: `basePrice`, `effectivePrice`, `unitCost`, `unitPrice`, `totalAmount`, `costValue`, and any amount on an event payload |

`Money` normalises its amount on construction. Every `Money` is therefore already at scale 2 by
the time anything reads it, so no caller has to remember to round, and there is no path by which
an unnormalised amount reaches an API response, a persisted row or an event.

**The problem this fixes.** Before this convention, the same amount rendered differently
depending on which path produced it. `POST /api/batches` returned `"amount": 2.10`, taken
straight from the request, while `GET` of the same batch returned `"amount": 2.1000`, because the
value had round-tripped through a `scale=4` column. Numerically identical, visibly inconsistent,
and exactly the sort of thing that reads as a defect in an API.

**Secondary benefit.** `BigDecimal.equals` is scale-sensitive: `2.10` does not equal `2.1000`.
Since `Money` is a record, and records derive `equals` from their components, normalising on
construction is what makes two equal amounts actually compare equal.

**Deliberately out of scope.** `MarginFloor.ratio` is a ratio, not a monetary amount, and is NOT
covered by this convention. Forcing it to scale 2 would be wrong — it would silently destroy a
floor such as `0.125`. Its rendering scale follows its column definition, and choosing a
precision for it is a separate decision that belongs with the Week 7 margin-floor work
(findings B-3 and B-6).

**Storage note.** Monetary columns are declared `precision = 19, scale = 4`. That is deliberate
and is not a contradiction: the database keeps headroom, and normalisation happens in the domain
on the way out. Nothing reads a raw column value without passing it through `Money`.
