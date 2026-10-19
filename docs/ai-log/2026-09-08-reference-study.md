# Reference study: FreshFlow against the subject's reference repositories

Date: 2026-09-08
Type: read-only research. No FreshFlow code or specification was modified.
Outcome (added 13 October 2026): see the closing table, which records which of the follow-ups
were adopted and which were not.

## Scope and method

Two of the subject's reference repositories were cloned to `../_reference/`, outside this
repository:

- `https://github.com/gxshub/book-and-library-management-system-v1`, the AI-driven development
  case study used in L3 (called "the reference" below)
- `https://github.com/gxshub/rest-services-with-spring-v2`, the Spring Boot REST getting-started
  project (called "the teaching demo" below)

The reference has five branches, not two. This matters, because the branch named in the task
(`iter2-spec`) is not the one that holds the mature code:

| Branch | Content |
|---|---|
| `main` = `spec-only` | `specs/` and `agent-prompts/dev-agent.md` only. No code. The starting point students run their agent against. |
| `post-implementation` | Iteration 1 specifications and the Iteration 1 implementation. |
| `iter2-spec` | Iteration 2 specifications (`specs/iter1/` and `specs/iter2/`) with the Iteration 1 code unchanged. |
| `iter2-impl` | Iteration 2 specifications and the Iteration 2 implementation. This is the DDD, CQRS and event-sourcing reference. |

`iter2-spec` was checked out as instructed; `iter2-impl` and `post-implementation` were read with
`git show`, since the substantive comparison is against the code, and the code on `iter2-spec` is
still Iteration 1. Every claim below names its branch.

---

## 1. Package layout and layer naming

### The reference

`specs/iter2/1-technical-architecture-v2.md` states the layers:

> Each microservice module is structured into four distinct layers:
> 1. **Presentation Layer** (Controllers)
> 2. **Service Layer** (Business Logic)
> 3. **Domain Layer** (Core Domain Models & Rules)
> 4. **Data Access Layer** (Repositories)

and binds each layer to a Spring stereotype:

> - **Controller Layer**: `@RestController`
> - **Service Layer**: `@Service`
> - **Domain Layer**: `@Entity`
> - **Data Access Layer**: `@Repository`

The reference uses the package name `service`, not `application`. The word "application" appears
in its vocabulary only as a qualifier ("command application service", "query application
service"), never as a package name. Its fourth layer is data access, not infrastructure; there is
no `infrastructure` package on any branch.

Concrete package structure, `iter2-impl`, `library-service`:

```
com.csci318.libraryservice
├── controller/            LibraryController
├── service/               LibraryCommandService, LibraryQueryService,
│                          InventoryEventSourcingService
├── domain/
│   ├── aggregateroot/     InventoryItem, Loan, FineLedger
│   ├── entity/            Library, Customer
│   ├── valueobject/       LibraryId, Isbn, CustomerId, Contact, CopyCount,
│                          LoanPeriod, Money, PolicySnapshot
│   ├── domainevent/       BookBorrowed, InventoryCopyReserved, … (13 records)
│   └── domainservice/     CirculationPolicyService, FinePolicyService
├── persistence/
│   ├── entity/            InventoryEventEntity, InventoryReadModelEntity,
│   │                      LibraryPolicyEntity
│   └── repository/        LoanRepository, InventoryItemRepository, …
├── client/                BookServiceClient
├── config/                DataInitializer
├── dto/                   14 request/response records
└── exception/             6 exceptions + GlobalExceptionHandler
```

Iteration 1 (`post-implementation`, and the code still on `iter2-spec`) is flatter:
`controller / service / domain / repository / dto / exception`, with a flat `domain/` holding four
JPA entities.

The teaching demo is flatter still: `controller / model / repository`, with no service layer.
`LibraryController` injects `LibraryRepository` and `AddressRepository` directly and returns JPA
entities as response bodies. It is a Spring Data onboarding project and was not read as normative
for anything the project is assessed on.

### FreshFlow

```
com.freshflow.<service>
├── presentation/
├── application/
├── domain/
└── infrastructure/
```

Flat inside each layer, one `package-info.java` per layer stating the layer's rule.

### Difference

| | Reference (`iter2-impl`) | FreshFlow |
|---|---|---|
| Presentation | `controller` | `presentation` |
| Application | `service` | `application` |
| Domain | `domain` with five sub-packages by DDD stereotype | `domain`, flat |
| Fourth layer | `persistence` (data access only) | `infrastructure` (persistence, messaging, configuration, adapters) |
| DTOs | own top-level `dto` package | `presentation` (`…ResponseDto`) and `application` (`…View`) |
| Exceptions | own top-level `exception` package | `DomainRuleViolation` in `domain`; no exception package |
| Cross-service client | `client` package | `infrastructure` (Kafka adapters) |

Two structural notes:

- The reference's `dto`, `exception`, `client` and `config` packages sit beside the four layers,
  not inside them, so its "four layers" are a conceptual statement rather than a package
  partition. FreshFlow's is a strict partition: every class lives in exactly one of four packages.
- The reference sub-packages `domain` by DDD stereotype (`aggregateroot`, `entity`,
  `valueobject`, `domainevent`, `domainservice`), as its `agent-prompts/dev-agent-v2.md` requires:
  "The subpackages must reflect the domain class types." FreshFlow's domain packages are flat and
  carry each class's stereotype in Javadoc and in spec-03. The reference scheme is more legible to
  a reader skimming a file tree.

---

## 2. Where repository interfaces are declared

This was the most important finding of the exercise, and it did not yield one answer, because the
course material and the reference artefacts place repository interfaces in different layers.

**(a) The reference architecture specification: repositories are a peer fourth layer, outside the
domain.** `1-technical-architecture-v2.md` names "Data Access Layer (Repositories)" as layer 4,
distinct from "Domain Layer" as layer 3. The domain-model specification
(`3-domain-models-library-service-v2.md`) catalogues entities, value objects, aggregate roots,
domain events and domain services and does not mention a repository.

**(b) The reference agent prompt: repositories are a domain sub-package.**
`agent-prompts/dev-agent-v2.md`, Phase 4:

> Implement domain classes strictly following `specs/iter2/*-domain-models-*.md`. The
> subpackages must reflect the domain class types (e.g., `aggregate`, `entity`,
> `valueobject`, **`repository`**, `domainservice`, `event`).

That instruction places `repository` inside `domain`, the position FreshFlow's spec-01 §2.2 took
at the time.

**(c) The reference code: repositories live in the persistence package.** `iter2-impl` puts every
repository in `persistence/repository/`:

```java
package com.csci318.libraryservice.persistence.repository;

import com.csci318.libraryservice.domain.aggregateroot.InventoryItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InventoryItemRepository extends JpaRepository<InventoryItem, String> {
}
```

There is no domain-declared interface and no separate implementation: one Spring Data JPA
interface, in the persistence package, typed directly on the aggregate root. Iteration 1 has the
same shape in a package called `repository/`. The code is the artefact students are pointed at,
so (c) is the arrangement a reader calibrated on the reference will expect.

L3 slide 11 places these interfaces in the application layer, which is what ADR 0001 and ADR 0002
record and follow for `sales-service` and `insight-service`. That is a fourth position:

| Source | Repository interface declared in |
|---|---|
| L3 slide 11 | application |
| Reference architecture specification | data access (its own layer) |
| Reference agent prompt (dev-agent-v2 Phase 4) | domain |
| Reference code (`iter2-impl`) | persistence |
| **FreshFlow spec-01 §2.2 and code, at the time** | **domain** |

FreshFlow's position matched the reference agent prompt and the Evans and Ports-and-Adapters
convention, and did not match the reference code. The recommendation was to put the question to
the subject coordinator directly, so that the team could follow a ruling rather than an
assumption. (It was settled on 17 and 22 September 2026; see ADR 0005.)

One further observation: because the reference's repositories are Spring Data interfaces typed on
the aggregate root, its domain classes have to be JPA entities. Position (c) and finding 3 below
are one decision seen twice.

---

## 3. How aggregates, entities and value objects are expressed in code

The reference's domain classes are JPA entities. This is specified, not accidental:
`1-technical-architecture-v2.md` says, in the Technology Stack section, "**Domain Layer**:
`@Entity`".

`iter2-impl`, `domain/aggregateroot/InventoryItem.java`:

```java
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "inventory_items")
public class InventoryItem {

    @Id
    private String inventoryId;

    @Embedded
    @AttributeOverride(name = "value", column = @jakarta.persistence.Column(name = "library_id"))
    private LibraryId libraryId;
    ...
    @Version
    private long version;

    protected InventoryItem() { }
```

Value objects are `@Embeddable` (`Money`, `Isbn`, `CopyCount`, `LoanPeriod`, `PolicySnapshot`,
`Contact`, `LibraryId`, `CustomerId`), with a protected no-argument constructor for Hibernate,
validation in the public constructor, and `equals`/`hashCode` by value. Entities (`Library`,
`Customer`) are `@Entity` with `@EmbeddedId`.

The DDD stereotypes are real and carefully done. Invariants live inside the aggregate:
`InventoryItem.reserveCopyForLoan` throws `InventoryInvariantViolationException` when
`availableCopies <= 0`; `Loan.ensureRenewable` enforces the renewal policy; `Money` rejects
negative amounts and currency mismatch. They are expressed on top of JPA rather than independently
of it: there is no mapper and no separate persistence entity for any aggregate. The only classes
in `persistence/entity/` are `InventoryEventEntity` (event store row), `InventoryReadModelEntity`
(CQRS projection) and `LibraryPolicyEntity` (configuration table), none of which is an aggregate.

Two consequences of that arrangement are visible in the reference code, and they are what
persistence ignorance is meant to prevent:

- `domain/domainservice/CirculationPolicyService` is annotated `@Service` and imports
  `persistence.entity.LibraryPolicyEntity` and `persistence.repository.LibraryPolicyRepository`,
  so the domain package depends on Spring and on the persistence package. FreshFlow's spec-01
  §2.2 does not permit that arrangement; in the reference it is the specified shape.
- `InventoryEventSourcingService` returns `InventoryReadModelEntity` from public methods, so
  persistence types reach the service layer and `LibraryQueryService`.

FreshFlow's domain classes are POJOs with no framework imports. `StockBatch`, `PriceRule`,
`Money`, `Sku`, `Quantity`, `ExpiryDate` and `BatchId` import only `java.*`. Persistence is a
parallel set of classes in `infrastructure`: `StockBatchJpaEntity`, `StockBatchMapper` and
`JpaStockBatchRepository`; `PriceRuleJpaEntity`, `PriceRuleMapper` and `JpaPriceRuleRepository`.
Each domain aggregate costs three infrastructure classes that the reference does not need.

This is the largest structural divergence between the two code bases.

---

## 4. Specification documents

### The reference, on `iter2-spec`

```
specs/iter1/  1-technical-architecture.md      35 lines
              2-user-stories.md                42
              3-domain-models.md               87
              4-api-endpoints.md               22
              openapi-contract.yaml           441
specs/iter2/  1-technical-architecture-v2.md   33
              2-user-stories-book-service-v2.md      21
              2-user-stories-library-service-v2.md   85
              3-domain-models-book-service-v2.md     24
              3-domain-models-library-service-v2.md 369
              4-api-endpoints-v2.md            50
              5-event-sourcing.md              45
              6-cqrs-command-query.md          95
              openapi-contract-v2.yaml        730
agent-prompts/dev-agent.md                     24
              dev-agent-v2.md                  32
```

### FreshFlow, at the time

```
specs/spec-01-architecture.md    509 lines
     spec-02-requirements.md     377
     spec-03-domain-model.md     311
     spec-04-api-contract.yaml   452
     dev-agent-prompt.md         131
```

Totals are comparable (reference about 2,135 lines and 9,428 words; FreshFlow 1,780 and 11,151).
The distribution is not.

**Structural correspondence is close.** The reference's 1/2/3/4 maps almost exactly onto
spec-01/02/03/04: architecture, requirements, domain model, API contract, plus an agent prompt.
FreshFlow's numbering, its contract-first OpenAPI file and its read-only-specifications-plus-phased
agent prompt are modelled on the reference, and the report should say so.

**Four real differences:**

1. **Versioning.** The reference versions specifications by iteration folder: `specs/iter1/` is
   frozen, `specs/iter2/` supersedes it, and `dev-agent-v2.md` scopes the agent to one folder
   ("You must not inspect other spec folder"). FreshFlow edits spec-01 to spec-04 in place and lets
   git history and the ADRs in `docs/decisions/` carry the evolution. The reference scheme makes
   the delta between iterations readable without git; FreshFlow's makes the current truth
   unambiguous. Each reference v2 document also opens with an "Evolution from iter1" list, a cheap
   device FreshFlow did not use.

2. **Per-service split.** The reference has separate user-story and domain-model documents for
   `book-service` and `library-service`. With four services plus a contracts module, FreshFlow's
   single spec-03 covering all four domain layers is denser.

3. **One document per new architectural concept.** `5-event-sourcing.md` and
   `6-cqrs-command-query.md` exist because Iteration 2 introduced event sourcing and CQRS. They
   are short (45 and 95 lines) and mostly tables: an event-field table, an event-type table, a
   user-story to command/query classification, a command catalogue and a query catalogue. At the
   time FreshFlow had nothing equivalent for Kafka Streams, the topology or the CQRS read side;
   that material was spread through spec-01 §2.6 and spec-02 FR12 and FR13. This was the clearest
   actionable gap. (spec-05 was written two days later; see `2026-09-10-reference-adoption.md`.)

4. **Density.** The reference specifications are thinner and more tabular; its architecture
   specification is 33 lines of bullet points. FreshFlow's spec-01 is 509 lines with HARD
   CONSTRAINT blocks, per-service pattern rulings and a dependency allowlist. Neither is wrong.
   FreshFlow's contains far more binding decisions, which is why it needs ADRs.

**On the agent prompt:** the reference's four phases are Specification Audit, Environment and
Dependency Audit, Contract-First Test Generation, Implementation and Verification Loop.
FreshFlow's are Phase 1 specification inspection, Phase 2 skeleton, Phase 3 tests that must fail,
Phase 4 implement until green. Same pipeline. Two reference instructions are matched almost word
for word: "HALT IMMEDIATELY, report the discrepancy, and request human clarification", and
"Retain all integration tests from previous milestones to prevent regressions". "Tests must fail
first" and "never weaken a test" are FreshFlow additions.

---

## 5. What each code base has that the other does not

### In the reference, not in FreshFlow

| | Detail | Assessment |
|---|---|---|
| **Event sourcing** | `InventoryAggregate` is event-sourced: an `inventory_events` append-only table with `eventId`, `aggregateId`, `aggregateType`, `eventType`, `aggregateVersion`, `occurredAt` and a JSON `payload`, plus a projected `inventory_read_model`. Specified in `5-event-sourcing.md`. | The one real capability gap. L5 is titled "Event-sourcing, CQRS and bounded context integration". FreshFlow has CQRS read models and event-driven integration, but no event store and no replay. |
| **`domain/` sub-packaged by DDD stereotype** | `aggregateroot`, `entity`, `valueobject`, `domainevent`, `domainservice` | Cosmetic, cheap, and makes the DDD content visible in a file listing. Worth considering. |
| **`@RestControllerAdvice` global exception handler** | Both services, both iterations. Maps `ResourceNotFoundException` to 404, `ValidationFailedException` to 400, `MethodArgumentNotValidException` to 400 with per-field detail. | FreshFlow had no `@ControllerAdvice`; errors are returned by mapping `ApplicationResult` to `ResponseEntity` inside each controller. That keeps invariant violations out of the presentation layer; the reference's is the idiomatic Spring answer and is less code. |
| **Bean validation** | `spring-boot-starter-validation`, `@Valid @RequestBody` on every write endpoint. | spec-01 §1.1's allowlist excluded `spring-boot-starter-validation` at the time, and `CreateTransactionRequestDto` and `CreateBatchRequestDto` carried Javadoc saying so. Since the reference uses it, this was a strong candidate for the approval route CLAUDE.md contemplates. |
| **Branch pair as the AI-development evidence** | `spec-only` versus `post-implementation`; the README describes the teaching method as "review and explain progress using a before/after branch comparison". | FreshFlow's evidence is different in kind: `docs/ai-log/` prose logs and `docs/decisions/` ADRs on a single `main`. A spec-only tag would make FreshFlow's evolution inspectable by the same gesture at near-zero cost. |
| **Per-service user stories with IDs (`L1` to `L11`, `B1` to `Bn`)** and a story to CQRS-type to aggregate mapping table | `6-cqrs-command-query.md` §5 | FreshFlow has FR1 to FR15 but no per-requirement table showing which is a command, which a query, and which passes through an aggregate. Such a table is an effective one-page argument that CQRS was applied deliberately. |
| **Optimistic locking** | `@Version` on `InventoryItem` | FreshFlow has no concurrency control on `StockBatch`. FEFO deduction across batches is where a reader might ask about it. |
| **Policy captured as an immutable snapshot at transaction time** | `PolicySnapshot` frozen into `Loan` at checkout, so a later policy change cannot alter an existing loan | Good domain modelling. FreshFlow's `PriceSnapshot` in `sales-service` is the same idea, so the two are aligned here. |

### In FreshFlow, not in the reference

| | Detail | Assessment |
|---|---|---|
| **Event-driven integration over Kafka** | The reference has no messaging. `library-service` calls `book-service` over synchronous REST through `BookServiceClient` (`RestClient`); its `pom.xml` files contain `web`, `data-jpa`, `validation`, `h2` and `test`. | FreshFlow is materially ahead on L6: topics, consumers and stream processing have no counterpart in the reference. |
| **A shared `contracts` module for integration events** | `IntegrationEvent`, `BatchReceivedEvent`, `ItemSoldEvent`, `StockLevelChangedEvent`, `PriceRulePublishedEvent`, `SellThroughRiskDetectedEvent`, each with `eventId` and `occurredAt` | The reference has domain events, but they never cross a service boundary and there is no shared contract module. FreshFlow's internal-versus-integration event distinction (spec-01 §2.6, Tables A and B) has no counterpart. |
| **Domain events that are published** | | In the reference's `LibraryCommandService.borrowBook`, the line `loan.borrowEvent(now);` constructs a `BookBorrowed` whose return value is not used; the same holds for `markReturned` and the rest. Only `InventoryItem`'s events reach the event store. FreshFlow's events are published. |
| **Ports and adapters, dependency inversion** | `StockBatchRepository`, `DomainEventPublisher`, `PriceRuleRepository`, `PriceRuleEventPublisher` declared inward; adapters in `infrastructure` | The reference has no ports; every collaborator is a concrete Spring Data interface or `@Service`. |
| **Persistence-ignorant domain** | POJOs, separate JPA entities, mappers | Discussed in §3. |
| **Different domain patterns per bounded context** | Domain Model in `inventory` and `pricing`, Transaction Script in `sales`, a CQRS read side in `insight` (spec-01 §2.3) | The reference applies one uniform shape to every service. FreshFlow's variation is a direct application of L4's domain design patterns and is a genuine differentiator, provided the report says so. |
| **Unit tests, not only integration tests** | `FefoDeductionPolicyTest`, `RiskAssessmentPolicyTest`, `PriceRulePublishedSnapshotFeedTest`, `ItemSoldConsumptionTest` alongside four API integration tests; 41 `@Test` methods at the time | The reference has two test classes, `BookControllerIntegrationTest` (10 tests) and `LibraryControllerIntegrationTest` (23), both `@SpringBootTest` with `MockMvc`, and no unit test of a domain class. Its agent prompt asks only for contract-derived integration tests. |
| **ADRs** | `docs/decisions/0001` to `0003` at the time | No decision records in the reference. |
| **Idempotent consumption** | `eventId` plus a `ConsumedEventJpaEntity` deduplication table | The reference's event store has a unique constraint on `eventId` and its specification names it "used for idempotency deduplication"; the code does not otherwise deduplicate on it. |
| **Event-time semantics** | `OccurredAtTimestampExtractor`; all windowing on producer-assigned `occurredAt` | The reference has `occurredAt` fields, but with no stream processing there is nothing to window. |
| **Injected `Clock`** | `ClockConfiguration` per service | The reference initialises `private final Clock clock = Clock.systemDefaultZone();` as a field in three services; it is not injected and cannot be overridden in tests. |

---

## 6. Reading the reference as the expected shape: which FreshFlow choices would look unusual?

Ranked by how much each difference affects the design.

### 1. Persistence-ignorant POJOs with separate JPA entities and mappers: highest risk

The reference architecture specification says "**Domain Layer**: `@Entity`" and the reference
code does exactly that. A reader calibrated on it opens `inventory-service/domain/StockBatch.java`,
finds no `@Entity`, then finds `infrastructure/StockBatchJpaEntity.java` and
`StockBatchMapper.java`, and has to decide whether that is rigour or duplication: three classes
where the reference has one, times two aggregates.

It is not wrong. It is the stricter reading, it is what Evans and Fowler prescribe, and it is what
spec-01 §2.2 mandates in writing. It is the conspicuous divergence, though, and it will not defend
itself. The report needs a short passage naming persistence ignorance, saying why it was chosen,
and pointing at what it buys: a domain policy such as `FefoDeductionPolicy` cannot import a
repository or a Spring annotation, by construction, whereas §3 shows what happens when the domain
package is allowed to.

### 2. Repository interfaces declared in `domain`: second highest, and contested

The reference code puts them in `persistence/repository`; the reference architecture
specification makes data access a peer fourth layer; L3 slide 11 puts them in `application`; the
reference agent prompt puts them in `domain`. The sources place them in different layers.

FreshFlow's choice at the time was defensible from the reference agent prompt and the DDD
literature, but it was not what the reference code shows. **Recommendation: ask the coordinator**,
framing it as the difference between `dev-agent-v2.md` Phase 4 and `persistence/repository/` on
`iter2-impl`, and asking which arrangement the project should follow.

A second-order point: FreshFlow's own answer was not uniform. spec-01 §2.2 said domain, but ADR
0001 and ADR 0002 put ports in `application` for `sales-service` and `insight-service`, citing L3
slide 11. That is reasoned and documented, but "three arrangements in four services" needs the
one-paragraph justification that ADR 0002's table already contains, restated in the report.

### 3. `application` instead of `service`: low risk, most visible

A name. The reference's four layers are Presentation, Service, Domain and Data Access; FreshFlow's
are presentation, application, domain and infrastructure. A reader scanning the file tree for the
layer that holds business logic will not find a `service` package.

Not worth renaming: `application` is standard DDD vocabulary, and the command-plus-handler classes
(`ApplyItemSoldCommand`, `ApplyItemSoldHandler`) are more precisely "application layer" than
"service layer". One sentence mapping the names onto the subject's names removes the friction.

### 4. No global exception handler: low risk, easily fixed

Every reference controller is backed by a `@RestControllerAdvice`. FreshFlow had none. Threading
`ApplicationResult` through the controller keeps the error path explicit and testable, but a
Spring-minded reader may read the absence of `@ControllerAdvice` as an omission rather than a
choice. Worth a sentence, or worth adding.

### 5. No bean validation: low risk, a self-imposed constraint

spec-01 §1.1 excluded `spring-boot-starter-validation`, and two DTOs carried Javadoc explaining
that manual validation is the consequence. The reference uses `@Valid` throughout. Since the house
rule already routes dependency additions through coordinator approval, this was a reasonable
approval to ask for.

### 6. No event sourcing: a gap, not a wrong choice

`iter2-impl` event-sources one aggregate and specifies it in a dedicated document. FreshFlow
event-sources nothing. Its Kafka Streams read models are a CQRS read side built on Kafka
Streams, and FR13's stream-table join has no counterpart in the reference, but L5 names
CQRS and event sourcing together and FreshFlow can evidence one of them.

Not something to build in Week 8 on the strength of this study; the milestone is FR2 and FR13 and
the agent is Week 9. It should be a conscious, recorded position rather than an omission, and the
report should say which half of L5 was implemented and why.

### 7. Single-branch history with prose AI logs: low risk, cheap hedge

The reference README states the teaching method: "review and explain progress using a
before/after branch comparison". FreshFlow's evidence is nine `docs/ai-log/` entries and three
ADRs on one branch. That evidence has a different shape from a before/after branch comparison.
Tagging the commit where the specifications were complete and implementation had not
begun makes the before/after inspectable by `git diff` for almost no effort.

### What would not look unusual

Areas that go beyond the reference by design:

- **Kafka, stream processing, event-time windowing, the `contracts` module.** L6 material not
  present in the reference.
- **Different domain patterns per bounded context.** Directly out of L4.
- **Unit tests of domain logic.** `FefoDeductionPolicyTest` and `RiskAssessmentPolicyTest` test
  invariants in isolation, which is possible because the domain classes are POJOs. The
  persistence-ignorance argument and the unit-testability argument are the same argument and
  should be made together.
- **ADRs.** No counterpart in the reference; additive.

---

## Recommended follow-ups (none actioned in this session)

1. Ask the coordinator where repository interfaces belong, citing the difference between
   `dev-agent-v2.md` Phase 4 and `persistence/repository/` on `iter2-impl`.
2. Ask whether `spring-boot-starter-validation` may be added, citing the reference's use of it.
3. Add a name-mapping sentence (presentation/application/infrastructure to
   Presentation/Service/Data Access) to the report and the README.
4. Consider a `specs-complete` tag to give a before/after diff comparable to the branch pair.
5. Consider a requirement to command/query/aggregate mapping table for FR1 to FR15, modelled on
   `6-cqrs-command-query.md` §5.
6. Record a deliberate position on event sourcing rather than leaving it absent.
7. Consider `@Version` on `StockBatch`, given FEFO deduction spans batches.

Items 1 and 2 are questions for the coordinator; the rest are Week 8 and later decisions for the
team.

## Assumptions made that the sources did not state

- That `iter2-impl`, not `iter2-spec`, is the code a reader would treat as the reference
  implementation. `iter2-spec` carries Iteration 2 specifications over Iteration 1 code, which
  appears to be an intermediate teaching state. The task named `iter2-spec`; all four branches
  were read so the conclusion does not depend on that reading.
- That the teaching demo is a Spring and JPA onboarding project and not an architectural
  reference.
- That L3 slide 11 places port and repository interfaces in the application layer. This was
  taken from ADR 0001 and ADR 0002, which cite it; the slide could not be rendered in this
  environment. Worth confirming by eye before the claim is repeated in the report.

## Deviations from the specifications

None. No FreshFlow source file, specification or configuration was modified. The reference
repositories were cloned outside the repository root, as instructed.

## Observations recorded for the team, not acted on

- The reference agent prompt and the reference code differ on repository placement (§2). A
  question for the coordinator.

---

## Outcome, as of 13 October 2026

| Follow-up | Outcome |
|---|---|
| 1. Repository placement | Ruled by the coordinator on 17 and 22 September 2026: repositories belong to the persistence layer, inside infrastructure. Applied as `infrastructure.repository` in ADR 0005 and `2026-09-29-repository-relocation.md`. |
| 2. `spring-boot-starter-validation` | Approved on 17 September 2026 together with `kafka-streams-test-utils` and `spring-cloud-stream-test-binder`. Not added: the hand-written request checks already existed and are covered by tests. `kafka-streams-test-utils` was added in test scope on 13 October 2026 for the topology tests. |
| 3. Layer name mapping | Adopted: the mapping table in spec-01 §2.1 and a sentence in the README. |
| 4. `specs-complete` tag | Adopted: the tag exists, with `mvp-core-complete` after it. |
| 5. Requirement to command/query table | Adopted in another form on 10 September 2026: the Requirements to Tactics table in spec-02 names, for each requirement, the command or query and the design tactic that realises it. spec-05 §8 adds a traceability table for the stream side. |
| 6. Position on event sourcing | Not written up as an ADR. The read side rebuilds its state stores from Kafka Streams changelog topics; event sourcing of an aggregate was left out of scope. |
| 7. `@Version` on `StockBatch` | Not adopted. The demonstration runs one instance of each service. |
| Global exception handler (§6 item 4) | Adopted on 13 October 2026 as `GlobalExceptionHandler` in each service's presentation layer, limited to malformed request bodies (`MALFORMED_REQUEST`). Domain and application outcomes still travel through `ApplicationResult`. |
| spec-05 (§4 difference 3) | Adopted on 10 September 2026: `specs/spec-05-stream-processing.md`. |
| "Evolution from" preamble (§4 difference 1) | Not adopted. Dated rulings stay inline and the ADRs carry the history. |
