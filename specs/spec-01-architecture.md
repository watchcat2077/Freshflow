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

### 1.4 Data Ownership

| Data | Owner Service | How others obtain it |
|---|---|---|
| `StockBatch`, `GoodsReceipt`, `WriteOffRecord` | inventory-service | Subscribe to events / REST query |
| `SaleTransaction`, `LineItem` | sales-service | Subscribe to `item-sold` |
| `PriceRule`, `MarkdownPlan`, `MarginFloor` | pricing-service | Subscribe to markdown events / REST query |
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
structure is correct and intended. Do NOT introduce aggregates there.

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

Business invariants live in the domain layer. They MUST NOT be implemented in
controllers, and MUST NOT be delegated to the LLM prompt.

Specifically: the margin floor check is enforced inside `pricing-service`'s domain
layer. The Markdown Decision Agent may only *propose*. This is the Anti-Corruption
Layer applied to AI integration.

### 2.6 Event Contracts

Topic naming: kebab-case. All events are defined in the `contracts` module.

| Topic | Producer | Consumers | Key fields |
|---|---|---|---|
| `batch-received` | inventory | insight | batchId, sku, quantity, expiryAt, unitCost |
| `stock-level-changed` | inventory | insight | batchId, sku, remainingQuantity, changedAt |
| `batch-written-off` | inventory | insight | batchId, sku, quantity, costValue, writtenOffAt |
| `item-sold` | sales | inventory, insight | transactionId, sku, quantity, unitPrice, soldAt |
| `sell-through-risk-detected` | insight | insight | batchId, sku, remainingQuantity, hoursToExpiry, currentVelocity, projectedShortfall |
| `markdown-recommended` | insight | pricing | batchId, sku, discountTier, effectiveFrom, rationale, agentTraceId |
| `markdown-applied` | pricing | sales, insight | markdownPlanId, batchId, sku, discountTier, effectiveFrom, effectiveTo |
| `markdown-expired` | pricing | sales, insight | markdownPlanId, sku, expiredAt |

**Every event MUST carry:**
- `eventId` (UUID) — used for idempotent consumption
- `occurredAt` (ISO-8601 timestamp) — event time, written by the producer, used for
  windowing. MUST NOT rely on Kafka ingestion time.

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

### 2.8 Explicit Non-Goals

Not in scope for this project: web or mobile frontend; custom-trained ML models;
real POS or supplier integration; authentication, RBAC, multi-tenancy; Kubernetes
or cloud deployment; OCR.
