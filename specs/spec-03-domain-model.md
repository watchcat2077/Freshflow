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

### Repository interface — declared in `domain`
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
Introducing aggregate roots and repositories would be over-design. The application
service orchestrates directly against JPA entities.

### `SaleTransaction`
`transactionId`, `tillId`, `occurredAt`, `lineItems`, `totalAmount`

### `LineItem`
`lineItemId`, `sku`, `quantity`, `unitPrice` (`Money`), `markdownApplied` (boolean)

### `PriceSnapshot`
Local read cache maintained from `markdown-applied` and `markdown-expired`.
`sku`, `effectivePrice`, `basePrice`, `discountTier`, `effectiveFrom`, `effectiveTo`

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

### Repository interface — declared in `domain`
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
