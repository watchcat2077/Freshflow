# Specification 1 — Technical Architecture, Design Principles, Patterns and Constraints

**Project**: FreshFlow — Real-Time Expiry Risk Detection and Agentic Markdown Decision Support
**Subject**: CSCI318 Software Engineering Practices & Principles, Spring 2026
**Version**: 1.0 — 12 August 2026
**Status**: Authoritative. Implementation MUST conform to this document.

---

## Part 1 — Technical Architecture

### 1.1 Technology Stack

| Concern | Technology | Version |
|---|---|---|
| Language | Java | JDK 21 |
| Framework | Spring Boot | 3.x |
| Web layer | Spring Web | — |
| Persistence | Spring Data JPA + H2 | in-memory, one instance per service |
| Messaging | Apache Kafka (KRaft mode) | single-node, Docker |
| Event binding | Spring Cloud Stream (Kafka binder) | — |
| Stream processing | Kafka Streams | — |
| LLM integration | LangChain4j | — |
| Build | Maven multi-module | — |
| Testing | spring-boot-starter-test (MockMvc) | bundled with Spring Boot |

**Dependency constraint**: NO dependency outside this list may be added. Additional
dependencies require explicit approval from the subject coordinator.

**Request validation — PENDING COORDINATOR DECISION (Phase 1 finding M-9).**
`spring-boot-starter-validation` (Jakarta Bean Validation) is NOT in the table above, and
is NOT supplied transitively by `spring-boot-starter-web` in Spring Boot 3.x. Until the
subject coordinator approves adding it, the request validation behind every `400` response
in spec-04 — required fields, `minimum`, `minItems` — MUST be hand-rolled in the
**application layer**. No validation annotations. No new dependency. A validation failure
is returned to the presentation layer as a typed result which the controller maps to `400`
with the `ErrorResponse` schema; the controller itself decides nothing (see §2.5).

| Field | Value |
|---|---|
| Status | **Open — awaiting subject coordinator** |
| Raised | 26 August 2026, Phase 1 inspection, finding M-9 |
| Options | (a) approve `spring-boot-starter-validation`; (b) hand-roll in the application layer |
| Interim ruling | (b). Hand-rolled validation is the specification until told otherwise, not a workaround |
| On approval | This note is withdrawn and the dependency is added to the table above |

### 1.2 Service Topology

Four microservices, one per bounded context.

| Service | Port | Bounded Context | Role |
|---|---|---|---|
| `inventory-service` | 8081 | Inventory & Batch | Batch lifecycle, expiry, stock levels |
| `sales-service` | 8082 | Sales | Transaction capture, price application |
| `pricing-service` | 8083 | Pricing & Markdown | Price rules, markdown plans, margin floor |
| `insight-service` | 8084 | Insight | Stream processing, read models, AI agents |

Supporting modules:

| Module | Purpose |
|---|---|
| `contracts` | Shared domain event definitions — implements the **Published Language** integration pattern |
| `pos-simulator` | Generates the scan event stream for demonstration |

### 1.3 Communication Architecture

- **Primary**: asynchronous, event-driven, over Apache Kafka via Spring Cloud Stream.
- **Secondary**: synchronous REST, used ONLY for agent tool calls from `insight-service`
  to the other services, and for external API access.
- Services MUST NOT share a database. Each service owns an independent H2 instance.

**No exception is granted for Sales -> Pricing** (Phase 1 finding B-5, team ruling
26 August 2026). The rule above stands unchanged. `sales-service` MUST NOT call
`pricing-service` synchronously to obtain a price, in Week 5 or in any later week.

`sales-service` instead maintains its own local `PriceSnapshot` (spec-03 §2), fed through an
**inbound port** that it owns:

| Aspect | Ruling |
|---|---|
| Port | `PriceSnapshotFeed` — inbound port declared in `sales-service`. Its signature does NOT change between weeks |
| Week 5 adapter | Seeds the snapshot from static configuration in `application.yml`. No broker involved |
| Week 6 adapter | Subscribes to `price-rule-published` (§2.6). Replaces the Week 5 adapter behind the same port |
| Layer | **`application`.** Declared in `sales-service`'s application layer, NOT its domain layer (team ruling 26 August 2026, Phase 2 finding J-2) |
| Constraint | The port is the only channel by which price data enters `sales-service`. Introducing a synchronous Pricing client is a specification violation |

This is a Ports and Adapters substitution, not an interim workaround. The Week 5 adapter is a
legitimate implementation of the port; swapping it in Week 6 changes no application-layer code.

**Layer placement of `PriceSnapshotFeed` — RESOLVED 26 August 2026** (Phase 2 finding J-2).
The interface is declared in `sales-service`'s **application layer**. Two reasons, and they
agree:

1. §2.3 designates `sales-service` **Transaction Script** and forbids repository abstractions
   in its domain layer. `PriceSnapshotFeed` is an abstraction over an external data source —
   near enough to a repository that declaring it in `domain` would violate that ruling in
   substance if not in name.
2. Under Transaction Script the application layer is where behaviour lives (§2.5), so the port
   belongs beside the script that drives it.

This overrides the general ports-and-adapters convention of declaring ports in the innermost
layer. That convention presumes a Domain Model service; `sales-service` is not one. In
`inventory-service` and `pricing-service` — both Domain Model — repository interfaces remain
in `domain` per §2.2, unchanged.

The adapters implementing it stay in `infrastructure` either way, so the dependency direction
of §2.2 is satisfied: `infrastructure` depends inward on `application`, never the reverse.

### 1.4 Data Ownership

| Data | Owner Service | How others obtain it |
|---|---|---|
| `StockBatch`, `GoodsReceipt`, `WriteOffRecord` | inventory-service | Subscribe to events / REST query |
| `SaleTransaction`, `LineItem` | sales-service | Subscribe to `item-sold` |
| `PriceRule`, `MarkdownPlan`, `MarginFloor` | pricing-service | Subscribe to `price-rule-published` and the markdown events. Synchronous REST query is permitted for agent tool calls only (§1.3) |
| Velocity view, risk view, agent traces | insight-service | Interactive Query REST endpoints |

### 1.5 Bounded Context Integration Patterns

| Relationship | Integration Pattern | Notes |
|---|---|---|
| Sales → Inventory | Customer–Supplier | Sales publishes `item-sold`; Inventory deducts stock |
| Pricing → Sales | Published Language | Shared event contract in `contracts` module |
| Inventory / Sales / Pricing → Insight | Conformist | Insight accepts upstream event models without negotiation |
| Agent → Pricing | **Anti-Corruption Layer** | Pricing independently validates the margin floor. LLM output is treated as an untrusted proposal, never as an authoritative command. |

---

## Part 2 — High-Level Design Principles, Patterns and Constraints

### 2.1 Layered Application Architecture

Every service follows the four-layer structure. Package structure MUST mirror the layers.

```
com.freshflow.<context>
├── presentation      REST controllers, DTOs, API endpoints
├── application       application services, use case orchestration
├── domain            domain model — entities, value objects, aggregates,
│                     domain events, domain services, repository interfaces
└── infrastructure    JPA entities, repository implementations, Kafka bindings,
                      external service clients, mappers
```

§2.3 designates **two** exceptions to the package structure above. They are different in
kind, and are not to be conflated:

1. **`insight-service` omits the `domain` package entirely.** It has `presentation`,
   `application` and `infrastructure` only.
2. **`sales-service` retains the `domain` package** but declares no aggregate roots and no
   repository interfaces within it. The package holds plain data structures only.

No other service is excepted. (Reworded 26 August 2026, Phase 2 finding J-1: the previous
single sentence bundled both exceptions together and read as though `sales-service` might
also lose the package.)

**Layer responsibilities:**

- **Presentation layer** — exposes the system's functions to external users.
- **Application service layer** — represents the use cases and behaviour of the
  application; hides the complexity of the domain behind a façade; coordinates
  retrieval of domain objects from a data store, delegates work to them, then
  saves the updated state.
- **Domain layer** — contains domain models representing conceptual abstract views
  of the problem domain, created to fulfil the needs of the business use cases.
  **Does not depend on anything else. Agnostic to the technicalities of the system.**
- **Infrastructure layer** — persistence, messaging, external integration.

### 2.2 Dependency Inversion — HARD CONSTRAINT

> Outer layers depend on inner layers. Inner layers expose interfaces that outer
> layers must adapt to and implement.

Concretely:

- `presentation` → `application` → `domain`
- `infrastructure` → `domain` (implements interfaces the domain declares)
- The `domain` package MUST NOT import from `presentation`, `application`,
  `infrastructure`, Spring, JPA, Jackson, or Kafka.

Repository interfaces are declared in `domain`. Their JPA implementations live in
`infrastructure`.

### 2.3 Domain Layer Implementation Pattern — per Bounded Context

Three implementation patterns for the domain layer are available: **Domain Model**,
**Transaction Script**, and **Table Module**. The Domain Model pattern is suitable
for complex domains with rich business logic. Applying it uniformly to contexts
with little business logic is over-engineering.

The pattern is therefore selected per bounded context according to domain complexity:

| Service | Domain complexity | Pattern | Justification |
|---|---|---|---|
| `inventory-service` | High | **Domain Model** | Real invariants: stock cannot go negative; expired batches cannot be sold; deduction follows FEFO |
| `pricing-service` | High | **Domain Model** | The margin floor is a business invariant that must be enforced by the domain layer, not by a controller and not by trusting LLM output |
| `sales-service` | Low | **Transaction Script** | Essentially records a transaction and applies the currently active price. Few business rules. Aggregates and repositories here would be over-design. |
| `insight-service` | None (read side) | **No domain layer** (CQRS read model) | Owns no transactional business data. Maintains materialised views and agent traces only. Complexity lives in the stream topology, not the domain. |

**Implementation consequence.**
For services designated **Domain Model**: domain classes are Plain Old Java Objects
(POJO), **persistence ignorant**. NO JPA annotations on domain classes. A separate
set of JPA entities plus mappers lives in `infrastructure`.

For services designated **Transaction Script**: an application-service-centric
structure is correct and intended. The `domain` package is **retained** and holds plain data
structures only; what is forbidden there is aggregate roots and repository interfaces, not the
package itself. Do NOT introduce aggregates there. This is the second of the two exceptions
§2.1 lists.

**What "no repository abstractions" forbids — clarified 26 August 2026** (ADR 0002, team ruling
on Phase 4 finding V-2). The prohibition was previously stated only as "Aggregates and
repositories here would be over-design", which was read as banning every persistence interface
and left `sales-service` with no lawful way to store anything at all. It bans a *kind* of
abstraction, not persistence:

| | |
|---|---|
| **Forbidden** | An aggregate-oriented repository. Concretely: any interface with query methods (`findById`, `findActiveBySku`, `findAll`), and any interface whose signature returns an aggregate. These presuppose the aggregate that §2.3 denies this service |
| **Permitted** | A **write-only, single-method persistence port** declared in the **application** layer and implemented by an adapter in `infrastructure`. It takes a plain data structure and returns nothing |

The permitted form is not a concession, it is a requirement of §2.2. The JPA entities cannot
live in `domain`, and `application` may not depend on `infrastructure`; a narrow outbound port is
the only construction that stores data while keeping the dependency arrow pointing inward. It
also matches L3 slide 11, which places these interfaces in the application layer.

When FR7 (query transaction history, `[POST-MVP]`) is built, a read path will be needed. It must
not be added to this port. Adding `findById` to it converts it into exactly the aggregate-oriented
repository forbidden above; a separate read-side abstraction is the correct answer, and the
decision belongs to the week that builds FR7.

**Outbound port placement follows the service's domain layer pattern — recorded 26 August 2026**
(ADR 0002, team ruling on Phase 4 finding V-1). Ports are declared in the layer where that
service's behaviour lives, which the pattern determines:

| §2.3 pattern | Services | Ports declared in |
|---|---|---|
| **Domain Model** | `inventory-service`, `pricing-service` | `domain` — both inbound and outbound, alongside the repository interfaces of §2.2 |
| **Transaction Script** | `sales-service` | `application` — outbound ports beside the inbound `PriceSnapshotFeed` of §1.3 and ADR 0001 |
| **No domain layer** | `insight-service` | `application` — there is no `domain` package to declare them in |

A service's ports are therefore never split across two layers. Adapters implementing them live in
`infrastructure` in every case, so §2.2's dependency direction holds regardless of pattern.

For `insight-service`, designated **No domain layer**: the `domain` package is omitted
entirely. The service has `presentation`, `application` and `infrastructure` only. This is
the first of the two exceptions §2.1 lists.

Where the business invariants of each pattern live is governed by §2.5.

### 2.4 DDD Building Blocks in Use

| Building block | Instances |
|---|---|
| Aggregate Root | `StockBatch`, `MarkdownPlan` |
| Entity | `GoodsReceipt`, `WriteOffRecord`, `PriceRule` |
| Value Object | `Money`, `Sku`, `Quantity`, `ExpiryDate`, `DiscountTier`, `PromotionWindow`, `MarginFloor` |
| Domain Event | See §2.6 |
| Domain Service | `MarginFloorPolicy` |
| Repository | `StockBatchRepository`, `MarkdownPlanRepository` (interfaces in `domain`) |

### 2.5 Invariant Placement — HARD CONSTRAINT

Business invariants live in the innermost layer the service's §2.3 pattern provides.
They MUST NOT be implemented in controllers, and MUST NOT be delegated to the LLM prompt.

| §2.3 pattern | Services | Invariants live in |
|---|---|---|
| **Domain Model** | `inventory-service`, `pricing-service` | The `domain` layer — inside the aggregate, or in a domain service when the rule spans aggregates |
| **Transaction Script** | `sales-service` | The **application service** layer. §2.3 forbids aggregates here, so the transaction script is the enforcement point |
| **No domain layer** | `insight-service` | Not applicable. The context owns no transactional business data and therefore no business invariants |

**Transaction Script services — stated explicitly, 26 August 2026** (ADR 0002, team ruling on
Phase 4 finding V-3). For a service designated Transaction Script, **the transaction script
itself is the invariant enforcement point**. This is not a weaker arrangement tolerated for
convenience; it is the only one available, because §2.3 denies the service an aggregate to hold
the rules. The application service is the innermost layer that service has, so "invariants live
in the innermost layer the service's §2.3 pattern provides" resolves to the application service
for `sales-service` and to the `domain` layer for `inventory-service` and `pricing-service`.

Concretely, for `sales-service`: the empty-line-items rule, the non-positive-quantity rule and
FR6a's "a scan of a SKU absent from the snapshot is a `400`, never a silent zero price" are all
enforced in the transaction script. Pushing them into the `domain` package would require the
aggregate §2.3 forbids; pushing them into the controller is forbidden below.

In no case, in any service, under any pattern, may an invariant live in a controller. In no case
may one be delegated to an LLM prompt. This clause, §2.1 and §2.3 are to be read together.

Specifically: the margin floor check is enforced inside `pricing-service`'s domain
layer. The Markdown Decision Agent may only *propose*. This is the Anti-Corruption
Layer applied to AI integration.

### 2.6 Event Contracts

Topic naming: kebab-case.

**Integration topics and internal topics are distinguished — added 7 September 2026** (team
ruling 3, on Week 7 part 1 finding G-2). L4 distinguishes internal domain events, which stay
inside one bounded context, from external integration events, which cross a context boundary.
This section previously recognised only the second kind, and said flatly that "all events are
defined in the `contracts` module". A topic that a single context both produces and consumes
therefore had nowhere to be declared.

| | **Integration topic** | **Internal topic** |
|---|---|---|
| Crosses a context boundary | Yes | No — one context produces and consumes it |
| Defined in | The `contracts` module — the Published Language of §1.5 | The owning service's own packages |
| Listed in | Table A below | Table B below |
| May another context consume it | Yes; that is what it is for | **No.** It is an implementation detail of the owning context, and depending on one recreates the coupling bounded contexts exist to prevent |
| Carries `eventId` and `occurredAt` | Yes | Yes — the rule below admits no exception |

The distinction is about the Published Language, not about the transport. Both kinds travel
through Kafka, and both are subject to every rule in this section. What differs is who is
entitled to depend on the contract: putting an internal topic's payload in `contracts` would
advertise an implementation detail as shared vocabulary and invite exactly the coupling the
context map of §1.5 is drawn to avoid.

A topic is promoted from Table B to Table A by moving its payload into `contracts` and adding a
row here. That is a specification change and requires a team decision, not merely an import.

#### Table A — Integration topics (Published Language, defined in `contracts`)

| Topic | Producer | Consumers | Key fields |
|---|---|---|---|
| `batch-received` | inventory | insight | batchId, sku, quantity, expiryAt, unitCost |
| `stock-level-changed` | inventory | insight | batchId, sku, remainingQuantity, changedAt |
| `batch-written-off` | inventory | insight | batchId, sku, quantity, costValue, writtenOffAt |
| `item-sold` | sales | inventory, insight | transactionId, sku, quantity, unitPrice, soldAt |
| `sell-through-risk-detected` | insight | insight | batchId, sku, remainingQuantity, hoursToExpiry, currentVelocity, projectedShortfall |
| `markdown-recommended` | insight | pricing | batchId, sku, discountTier, effectiveFrom, effectiveTo, rationale, agentTraceId |
| `price-rule-published` | pricing | sales | sku, basePrice, unitCost, category |
| `markdown-applied` | pricing | sales, insight | markdownPlanId, batchId, sku, discountTier, effectiveFrom, effectiveTo |
| `markdown-expired` | pricing | sales, insight | markdownPlanId, sku, expiredAt |

#### Table B — Internal topics (owned by one context, NOT in `contracts`)

| Topic | Owning context | Purpose | Key fields |
|---|---|---|---|
| `velocity-baseline-published` | insight | Carries the FR12 velocity baseline into the topology's `KTable`. A `KTable` is built from a topic, so FR12's "stored velocity baseline held in a `KTable`" requires one. Seeded from configuration per ADR 0003 | sku, baselineVelocityPerHour |

`sell-through-risk-detected` stays in Table A although both its producer and its consumer are
`insight` today. It is listed there deliberately: FR14's Markdown Decision Agent consumes it,
and `markdown-recommended` carries the result to `pricing`, so the risk signal is part of the
vocabulary the contexts share even while the loop is still internal.

**Every event MUST carry:**
- `eventId` (UUID) — used for idempotent consumption
- `occurredAt` (ISO-8601 timestamp) — event time, written by the producer, used for
  windowing. MUST NOT rely on Kafka ingestion time.

`price-rule-published` therefore carries `eventId`, `occurredAt`, `sku`, `basePrice`,
`unitCost` and `category`. Like every other event it is defined in the `contracts` module.

**`price-rule-published` is the Week 6 replacement for the Week 5 static seed.** It is recorded
in this table now so the contract is fixed and reviewable, not so that it is built now. In
Week 5 no broker runs and no producer for this topic exists; `sales-service` populates its
`PriceSnapshot` through the `PriceSnapshotFeed` port's static-configuration adapter (§1.3). In
Week 6 `pricing-service` becomes the producer and the Kafka adapter replaces the static one
behind the unchanged port. Consumers MUST deduplicate on `eventId` and MUST apply
last-write-wins on `occurredAt` per `sku`.

### 2.7 Configuration Constraints

- LLM provider MUST be injected through `application.yml`, so hosted API and local
  Ollama are interchangeable without code change.
- API keys MUST come from environment variables. NEVER commit a key.

```yaml
freshflow:
  llm:
    provider: ${LLM_PROVIDER:ollama}
    model: ${LLM_MODEL}
    api-key: ${LLM_API_KEY:}
```

**Seeded configuration contract — agreed 26 August 2026** (Phase 3 finding A-1).

Two `[MVP-CORE]` read paths are fed from static configuration rather than from a broker,
because no broker runs in Week 5. spec-03 fixes *which* fields each carries; it did not fix
the property keys, and Phase 3's tests had to name them before Phase 4 could bind them. The
keys below are now the contract. Implementation and test resources MUST use them, and a
change to either MUST change both together.

`sales-service` — the `PriceSnapshotFeed` port's Week 5 static-configuration adapter (§1.3,
spec-03 §2). This is the ONLY channel by which price data enters `sales-service`:

```yaml
freshflow:
  sales:
    price-snapshot-seed:
      - sku: SKU-YOG-1KG
        base-price: 4.50
        unit-cost: 2.10
        category: DAIRY
        currency: AUD
```

`pricing-service` — `PriceRule` seed (FR8 `[MVP-CORE, minimal]`: "a read endpoint plus seeded
configuration data is sufficient", spec-03 §3):

```yaml
freshflow:
  pricing:
    price-rules:
      - sku: SKU-YOG-1KG
        base-price: 4.50
        unit-cost: 2.10
        category: DAIRY
        currency: AUD
        margin-floor-ratio: 0.15
```

`currency` is carried explicitly because spec-03 types `basePrice` and `unitCost` as `Money`,
which is amount plus currency, while the static-source row breaks out neither. Seeding it
beats defaulting it silently in code.

**Week 6 consequence.** `freshflow.sales.price-snapshot-seed` is retired when the
`price-rule-published` adapter replaces the static one behind the unchanged port. The
`pricing-service` block is not retired: it remains the source of price rules until FR8's full
CRUD arrives, which is `[POST-MVP]`.

**Week 7 addition — FR12 velocity configuration** (recorded 7 September 2026, on the same
terms as the A-1 keys above: implementation and test resources MUST use these keys, and a
change to either MUST change both together).

`insight-service` — the windowing parameters and the FR12 velocity baseline:

```yaml
freshflow:
  insight:
    velocity:
      window-seconds: 60
      advance-seconds: 5
      grace-seconds: 5
      retention-minutes: 10
      baselines:
        - sku: SKU-YOG-1KG
          baseline-velocity-per-hour: 120.0
```

`window-seconds` and `advance-seconds` are spec-02 FR12's MVP simplification — "60-second
window for demonstration; production target is a 60-minute window advancing every 5 minutes".
Both are configuration, so the production shape is a YAML change and not a code change. The
12:1 ratio between window and advance holds at both scales. `retention-minutes` governs how
long a window stays in the state store and must exceed the range a query looks back over.

**`baselines` is a stop-gap, and is recorded here as one.** spec-02 FR12 requires the current
rate to be compared "against a stored velocity baseline held in a `KTable`", and no requirement
in any specification says where that baseline comes from — see Week 7 finding G-1. Until the
team rules, it is seeded from the configuration above onto the `velocity-baseline-published`
topic at `insight-service` startup, and the topology's `KTable` is built from that topic. The
consequence of that arrangement is deliberate: whatever eventually supplies the number — a
computed historical average is the obvious candidate — replaces the seeder alone. The topology,
the state store, the ports and the endpoint all read the topic, never the configuration.

A SKU with no configured baseline is not an error. `GET /api/insight/velocity/{sku}` returns
the velocity and omits `baselineVelocity` and `velocityRatio`, because there is no comparison
to report. It MUST NOT report a baseline of zero: spec-04 marks no property of
`VelocityResponse` required, and a fabricated zero would read as "selling at zero times its
normal rate" when the normal rate is simply unknown.

**Week 7 part 2 addition — FR13 risk configuration** (recorded 7 September 2026, same terms).

`insight-service` — the two numbers FR13's decision turns on:

```yaml
freshflow:
  insight:
    risk:
      shortfall-threshold: 5.0
      trading-hours-per-day: 24.0
```

`shortfall-threshold` is the projected shortfall, in units, above which a batch is reported at
risk. FR13 says to compare projected sales against remaining quantity but never says how large a
gap is worth reporting. Zero would alarm on a batch expected to miss clearing by a single unit,
which is noise; the value is configuration precisely because the right number is a merchandising
judgement rather than an engineering one.

`trading-hours-per-day` exists because FR13 projects over "remaining **trading** hours" and no
specification defines the trading day. At 24 every remaining hour counts as a selling hour, which
is the assumption in force until the team sets real opening hours. It is not a cosmetic default:
a shop open twelve hours a day sells half as much before expiry as the clock suggests, so a batch
that looks safe against clock hours can be at risk against trading hours.

Both are flagged as specification gaps in `docs/ai-log/2026-09-07-week-7-fr13-risk.md`. Neither
is derivable from anything the specifications state, and both change which batches are reported.

### 2.8 Explicit Non-Goals

Not in scope for this project: web or mobile frontend; custom-trained ML models;
real POS or supplier integration; authentication, RBAC, multi-tenancy; Kubernetes
or cloud deployment; OCR.
