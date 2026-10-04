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
| LLM integration | LangChain4j | 1.20.0 — see below |
| Build | Maven multi-module | — |
| Testing | spring-boot-starter-test (MockMvc) | bundled with Spring Boot |

**Dependency constraint**: NO dependency outside this list may be added. Additional
dependencies require explicit approval from the subject coordinator.

**LangChain4j coordinates — recorded 11 September 2026, FR14 Phase B2.** This row said
"LangChain4j" with no version until FR14 needed one. Team ruling R2 admits **`langchain4j-core`
plus exactly one provider module**, and more than one provider artifact is a HALT rather than a
choice. The two artifacts are:

| Group | Artifact | Version | Role |
|---|---|---|---|
| `dev.langchain4j` | `langchain4j-core` | 1.20.0 | Chat, message and tool-specification types |
| `dev.langchain4j` | `langchain4j-google-ai-gemini` | 1.20.0 | The one provider module |

Both resolve at the same version, which was verified against Maven Central before any adapter code
was written; LangChain4j has shipped periods with the core module on a release version and
integration modules on `-beta` versions, and R2 exists partly to stop that skew entering the build
unnoticed. The version is managed in the **root `pom.xml`'s `<dependencyManagement>`** under the
`langchain4j.version` property, so neither module pom can pin a version of its own and the two
cannot drift apart. `insight-service/pom.xml` declares both without a version, exactly as it
already declares `contracts`.

The provider module brings `langchain4j-http-client` and `langchain4j-http-client-jdk`
transitively. Neither is a provider module and neither is declared; they are the HTTP plumbing of
the one approved provider, and are recorded here so that a reader of the dependency tree knows they
were expected.

`langchain4j-core` declares Jackson 2.22; Spring Boot 3.4.1 manages Jackson to 2.18.2 and wins.
Every Jackson class and method the two LangChain4j artifacts reference exists in 2.18.2, checked
with `jdeps` and by inspection of their constant pools. Recorded because the resolution is
deliberate rather than accidental.

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
`inventory-service` and `pricing-service` — both Domain Model — repository interfaces were
declared in `domain` when this was written; since 29 September 2026 they are declared in
`infrastructure.repository` per the subject coordinator's ruling (ADR 0005). The reasoning
above about `PriceSnapshotFeed` is unaffected.

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
│                     domain events, domain services, outbound event ports
└── infrastructure    JPA entities, repository implementations, Kafka bindings,
    │                 external service clients, mappers
    └── repository    repository interfaces (ADR 0005, coordinator's ruling of
                      17 and 22 September 2026; previously listed under domain)
```

§2.3 designates **one** remaining exception to the package structure above:

1. **`sales-service` retains the `domain` package** but declares no aggregate roots and no
   repository interfaces within it. The package holds plain data structures only.

**`insight-service` is no longer excepted — 4 October 2026, ADR 0006.** Until that date it
omitted the `domain` package entirely. On the subject coordinator's ruling of 22 September
2026 that a CQRS context has a domain layer, `RiskAssessmentPolicy` and the `BatchRiskView`
it produces moved into `com.freshflow.insight.domain`. The read-side ports and the agent
orchestration stay in `application`. All four services now show the same four packages.

(Reworded 26 August 2026, Phase 2 finding J-1, and again 4 October 2026.)

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

**Package-to-layer mapping — added 10 September 2026.** The course specification names the four
layers Presentation / Service / Domain / Data Access. Our packages are named
`presentation` / `application` / `domain` / `infrastructure`. They are the same four layers; only
two of the names differ, and the correspondence is one-to-one:

| Course specification layer | FreshFlow package | Correspondence |
|---|---|---|
| Presentation Layer (Controllers) | `presentation` | Exact |
| Service Layer (Business Logic) | `application` | Exact — same layer, different name |
| Domain Layer (Core Domain Models & Rules) | `domain` | Exact |
| Data Access Layer (Repositories) | `infrastructure` | **Wider.** Holds the repository implementations the course layer names, and also the Kafka bindings, serdes, adapters, mappers, JPA entities and configuration |

We kept our names because `application` is the standard vocabulary for the use-case layer in the
layered and Ports-and-Adapters literature this project follows, and because our fourth layer
holds messaging adapters and stream-processing configuration as well as repositories — calling it
"data access" would misdescribe most of what is in it. Nothing about the *structure* differs:
the dependency rule of §2.2 is the same rule. Repository interfaces are declared in
`infrastructure.repository` with their JPA implementations beside them in `infrastructure`,
per the subject coordinator's ruling of 17 and 22 September 2026 (ADR 0005); this sentence
previously said `domain`, which was true until 29 September 2026.

### 2.2 Dependency Inversion — HARD CONSTRAINT

> Outer layers depend on inner layers. Inner layers expose interfaces that outer
> layers must adapt to and implement.

Concretely:

- `presentation` → `application` → `domain`
- `infrastructure` → `domain` (implements the event ports the domain declares)
- `application` → `infrastructure.repository` only (the persistence ports; ADR 0005)
- The `domain` package MUST NOT import from `presentation`, `application`,
  `infrastructure`, Spring, JPA, Jackson, or Kafka.

Repository interfaces are declared in `infrastructure.repository`; their JPA implementations
live beside them in `infrastructure`. **Amended 4 October 2026**: until 29 September 2026 this
sentence read "declared in `domain`". The subject coordinator ruled (17 and 22 September 2026)
that repositories belong to the persistence layer, and ADR 0005 records the move and the
narrowed dependency check that replaces "`application` never imports `infrastructure`".

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
| **Domain Model** | `inventory-service`, `pricing-service` | `domain` for the event ports (`DomainEventPublisher`, `PriceRuleEventPublisher`, `MarkdownEventPublisher`); `infrastructure.repository` for the repository interfaces since ADR 0005 (this row previously said "alongside the repository interfaces of §2.2") |
| **Transaction Script** | `sales-service` | `application` — outbound ports beside the inbound `PriceSnapshotFeed` of §1.3 and ADR 0001 |
| **CQRS read side** (`domain` package added by ADR 0006) | `insight-service` | `application` — the read-side stores and the two agent ports; the `domain` package holds `RiskAssessmentPolicy` and `BatchRiskView` and declares no ports |

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
| Repository | `StockBatchRepository`, `PriceRuleRepository`, `MarkdownPlanRepository` (interfaces in `infrastructure.repository`, ADR 0005; JPA adapters beside them) |

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

The topology that consumes and produces these topics — its windowing, its join, its state
stores and its known limitations — is specified in **spec-05-stream-processing.md**.

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
| `markdown-applied` | pricing | sales, insight | markdownPlanId, batchId, sku, discountTier, effectiveFrom, effectiveTo, discountedPrice (added 4 October 2026: the till charges the price Pricing authorised, never one it recomputes) |
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

**This rule is not currently enforced by anything — Week 8 item** (recorded 7 September
2026, Week 7 part 2 finding G-6). It is stated here, restated in `IntegrationEvent`'s
javadoc, and satisfied by convention in each producer. The type system enforces that the two
fields *exist*; nothing enforces that they are *serialised in the required format*.

Week 7 part 2 found this the expensive way. `sell-through-risk-detected` shipped
`occurredAt` as `1788762590.000000000` — the correct instant in a format this clause does not
allow — because the Kafka Streams output serde uses a different `ObjectMapper` from the
message-channel binder that produces every other topic. A consumer written against the other
topics would not have parsed it. No test failed, because no test serialises that topic.

**Intended fix**: a contract test over the `contracts` module that serialises one instance of
every event and asserts the wire shape — `eventId` present and a UUID string, `occurredAt`
present and matching an ISO-8601 instant. It needs no broker and no Spring context, it runs
in milliseconds, and it would have caught the defect above at the moment it was introduced.

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

**The provider in force — recorded 11 September 2026, FR14 Phase B2.** The example below
previously defaulted the provider to `ollama`, which no longer matched what `insight-service`
actually runs. It now states the chosen provider, and `insight-service/src/main/resources/
application.yml` is identical to it:

```yaml
freshflow:
  llm:
    provider: ${LLM_PROVIDER:google-ai-gemini}
    model: ${LLM_MODEL:}
    api-key: ${LLM_API_KEY:}
    timeout: 60s
    max-steps: 8
```

| Key | Source | Note |
|---|---|---|
| `provider` | `${LLM_PROVIDER}`, default `google-ai-gemini` | Names which adapter is in force. Ollama remains the documented alternative |
| `model` | `${LLM_MODEL}` | Empty default, so the service starts on a machine that has no model configured |
| `api-key` | `${LLM_API_KEY}` **only** | Never a literal, never logged, never committed. An empty value means FR14 is not configured here |
| `timeout` | configuration | How long one model call may take |
| `max-steps` | configuration | Model round trips per deliberation, bounding a ReAct loop that never concludes |

**Ollama stays the alternative this clause promises, and the promise is real rather than
aspirational.** The interchangeability is delivered by the `MarkdownDecisionAgent` port
(ADR 0004), not by a branch on `provider`: moving to a local Ollama is a second adapter in
`insight-service`'s `infrastructure` package plus a value here, and no application-layer type
changes, because the whole of FR14's orchestration sits on the other side of that interface.
Team ruling R2 admits one provider artifact, so the move is a **substitution** in §1.1's table
and not an addition to it.

**An absent key is not a startup failure.** With `api-key` empty, `insight-service` starts, serves
FR12 and FR13, builds no model and wires no agent; the FR14 trigger consumer logs that it is not
configured and drops the event. This is what lets `mvn test` run on a clean checkout with no
environment variable set, which §1.1's testing row and ADR 0004 both require.

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

**FR14 addition — where `insight-service` finds `inventory-service`** (recorded 11 September
2026, same terms as the A-1 keys above).

```yaml
freshflow:
  insight:
    inventory:
      base-url: ${INVENTORY_BASE_URL:http://localhost:8081}
```

spec-06 §3's ruling sends `getBatchDetails` to `GET /api/batches/{batchId}` over synchronous REST,
which §1.3 permits for agent tool calls and for nothing else. The base URL is configuration rather
than a literal so that a containerised run can point at a service name without a rebuild, and the
default is §1.2's port for `inventory-service`.

**There is deliberately no such key for `pricing-service`.** Team ruling R5 and spec-06 §7 forbid
this service to call that context at all; the only channel towards it is `markdown-recommended`.

### 2.8 Explicit Non-Goals

Not in scope for this project: web or mobile frontend; custom-trained ML models;
real POS or supplier integration; authentication, RBAC, multi-tenancy; Kubernetes
or cloud deployment; OCR.
