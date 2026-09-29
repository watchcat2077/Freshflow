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

### `GoodsReceipt` `<<Entity>>`
`receiptId`, `batchId`, `receivedAt`, `supplierRef`

### `WriteOffRecord` `<<Entity>>`
`writeOffId`, `batchId`, `quantity`, `costValue` (`Money`), `writtenOffAt`, `reason`

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
| `discountTier` | `DiscountTier` `<<VO>>` | Enum-like: 20/30/50 percent |
| `window` | `PromotionWindow` `<<VO>>` | effectiveFrom, effectiveTo |
| `status` | `MarkdownStatus` enum | PROPOSED, ACTIVE, EXPIRED, REJECTED |
| `origin` | `ProposalOrigin` enum | HUMAN, AGENT |
| `agentTraceId` | `String` | Nullable; set when origin is AGENT |
| `rejectionReason` | `String` | Nullable |

**Behaviour**
```
activate(MarginFloor floor, Money unitCost) : void   // throws MarginFloorBreach
expire()  : void
reject(String reason) : void
```

> **OPEN ITEM — Week 7 (Phase 1 finding B-6).** `activate(...)` does not receive
> `basePrice`, so as declared it cannot evaluate invariant 1 below. The signature is
> deliberately left unchanged by the 26 August 2026 amendment. Do not implement
> `activate(...)` until this is settled.

**Invariants — enforced inside the aggregate**
1. A plan may only be activated if the discounted price is at or above the margin floor.
2. `effectiveTo` must be after `effectiveFrom`.
3. Only a PROPOSED plan may be activated. Only an ACTIVE plan may expire.
4. A REJECTED plan is terminal and cannot be reactivated.
5. `origin` is immutable after creation.

### `PriceRule` `<<Entity>>`

| Field | Type | Notes |
|---|---|---|
| `sku` | `Sku` `<<VO>>` | Identity |
| `basePrice` | `Money` `<<VO>>` | |
| `unitCost` | `Money` `<<VO>>` | |
| `category` | `String` | |
| `marginFloor` | `MarginFloor` `<<VO>>` | Minimum acceptable margin for this SKU. Exposed as `marginFloorRatio` by `GET /api/prices/{sku}` |

`marginFloor` was added on 26 August 2026 to resolve Phase 1 finding B-1 **for the
`[MVP-CORE]` read path only** — it gives `GET /api/prices/{sku}` a value to return.

**Deliberately still open after that amendment:**

- **Category-level floors.** FR8 requires floors "per SKU **and** per category". Only the
  SKU-level floor is modelled. There is no `CategoryMarginFloor` entity and no
  SKU-over-category precedence rule. **Week 7.**
- **B-3** — `MarginFloorPolicy` return type. Unchanged below. **Week 7.**
- **B-6** — `MarkdownPlan.activate(...)` signature. Unchanged above. **Week 7.**

### Value Objects
`MarkdownPlanId`, `DiscountTier`, `PromotionWindow`, `MarginFloor` (minimum acceptable
margin as a ratio), `Money`, `Sku`, `BatchId`

### Domain Service
`MarginFloorPolicy`
```
isBreached(Money basePrice, Money unitCost, DiscountTier tier, MarginFloor floor) : boolean
```

> **OPEN ITEM — Week 7 (Phase 1 finding B-3).** A `boolean` cannot carry the
> `resultingPrice`, `resultingMarginRatio` and `marginFloorRatio` that spec-04's
> `MarginFloorRejection` requires, and recomputing them outside the domain would breach
> spec-01 §2.5. The return type is deliberately left unchanged by the 26 August 2026
> amendment. Do not implement the 422 path until this is settled.
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
  findActiveBySku(Sku) : List<MarkdownPlan>
```

---

## 4. Sub-domain: Insight

**Implementation pattern**: no domain layer — CQRS read models only

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
