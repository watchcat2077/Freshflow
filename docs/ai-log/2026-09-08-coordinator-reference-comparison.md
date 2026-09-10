# Coordinator Reference Comparison — FreshFlow vs. gxshub reference repositories

Date: 2026-09-08
Type: read-only research. No FreshFlow code or spec was modified.

## Scope and method

Two coordinator repositories were cloned to `../_reference/`, outside this repository:

- `https://github.com/gxshub/book-and-library-management-system-v1` — the AI-driven
  development case study (this is the L3 reference)
- `https://github.com/gxshub/rest-services-with-spring-v2` — the Spring Boot REST
  "getting started" teaching project

The first repository has **five** branches, not two. This matters, because the branch
the task named (`iter2-spec`) is not the one that holds the mature code:

| Branch | Content |
|---|---|
| `main` = `spec-only` | `specs/` + `agent-prompts/dev-agent.md` only. No code. The starting point students are told to run their agent against. |
| `post-implementation` | Iteration 1 specs + Iteration 1 implementation. |
| `iter2-spec` | Iteration 2 specs (`specs/iter1/` + `specs/iter2/`) + **Iteration 1 code, unchanged**. |
| `iter2-impl` | Iteration 2 specs + the Iteration 2 implementation. **This is the DDD/CQRS/event-sourcing reference.** |

`iter2-spec` was checked out as instructed; `iter2-impl` and `post-implementation` were
read via `git show`, since the substantive comparison is against the code, and the code
on `iter2-spec` is still Iteration 1. Every claim below is attributed to a branch.

---

## 1. Package layout and layer naming

### His layers

`specs/iter2/1-technical-architecture-v2.md` states them verbatim:

> Each microservice module is structured into four distinct layers:
> 1. **Presentation Layer** (Controllers)
> 2. **Service Layer** (Business Logic)
> 3. **Domain Layer** (Core Domain Models & Rules)
> 4. **Data Access Layer** (Repositories)

and then binds each layer to a Spring stereotype:

> - **Controller Layer**: `@RestController`
> - **Service Layer**: `@Service`
> - **Domain Layer**: `@Entity`
> - **Data Access Layer**: `@Repository`

**Answer to the question asked: he uses `service`, not `application`.** The word
"application" appears in his vocabulary only as a qualifier — "command application
service", "query application service" — never as a package name.

**Does he use all four layers?** Yes, but his fourth layer is *Data Access*, not
*Infrastructure*. He has no `infrastructure` package anywhere, on any branch.

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
│                          LibraryPolicyEntity
│   └── repository/        LoanRepository, InventoryItemRepository, …
├── client/                BookServiceClient
├── config/                DataInitializer
├── dto/                   14 request/response records
└── exception/             6 exceptions + GlobalExceptionHandler
```

Iteration 1 (`post-implementation`, and the code still sitting on `iter2-spec`) is much
flatter — `controller / service / domain / repository / dto / exception`, with a flat
`domain/` holding four JPA entities and no sub-packaging at all.

Repository 2 (`rest-services-with-spring-v2`) is flatter still: `controller / model /
repository`, with **no service layer whatsoever** — `LibraryController` injects
`LibraryRepository` and `AddressRepository` directly and returns JPA entities as
response bodies. It is a Spring Data teaching demo, not an architecture reference. It
should not be read as normative for anything the project is assessed on.

### Ours

```
com.freshflow.<service>
├── presentation/
├── application/
├── domain/
└── infrastructure/
```

Flat inside each layer, one package-info.java per layer stating the layer's rule.

### Difference

| | Coordinator (`iter2-impl`) | FreshFlow |
|---|---|---|
| Presentation | `controller` | `presentation` |
| Application | `service` | `application` |
| Domain | `domain` + 5 sub-packages by DDD stereotype | `domain`, flat |
| Fourth layer | `persistence` (data access only) | `infrastructure` (persistence + messaging + config + adapters) |
| DTOs | own top-level `dto` package | `presentation` (`…ResponseDto`) / `application` (`…View`) |
| Exceptions | own top-level `exception` package | `DomainRuleViolation` in `domain`; no exception package |
| Cross-service client | `client` package | `infrastructure` (Kafka adapters) |

Two structural notes worth recording:

- His `dto`, `exception`, `client` and `config` packages sit *beside* the four layers,
  not inside them. So the "four layers" are a conceptual statement, not a package
  partition. Ours is a strict partition — every class lives in exactly one of four
  packages.
- He sub-packages `domain` by DDD stereotype (`aggregateroot`, `entity`,
  `valueobject`, `domainevent`, `domainservice`). This is *required* by his
  `agent-prompts/dev-agent-v2.md`: "The subpackages must reflect the domain class
  types." We do not do this — our domain packages are flat, and the stereotype of each
  class is carried in Javadoc and in `spec-03`. His scheme is more legible to a marker
  skimming a file tree.

---

## 2. Where repository interfaces are declared

**This is the most important finding of the exercise, and it is not a clean answer,
because the coordinator's own artefacts disagree with each other.**

Three positions are in evidence:

**(a) His architecture spec — repositories are a peer fourth layer, outside the domain.**
`1-technical-architecture-v2.md` names "Data Access Layer (Repositories)" as layer 4,
distinct from "Domain Layer" as layer 3. His domain-model spec
(`3-domain-models-library-service-v2.md`) catalogues Entities, Value Objects, Aggregate
Roots, Domain Events and Domain Services — and **never mentions a repository at all**.
Repositories are not a domain concept in his specification.

**(b) His agent prompt — repositories are a domain sub-package.**
`agent-prompts/dev-agent-v2.md`, Phase 4:

> Implement domain classes strictly following `specs/iter2/*-domain-models-*.md`. The
> subpackages must reflect the domain class types (e.g., `aggregate`, `entity`,
> `valueobject`, **`repository`**, `domainservice`, `event`).

That instruction places `repository` inside `domain`. It is the same position FreshFlow
takes in spec-01 §2.2.

**(c) His actual code — repositories live in infrastructure.**
`iter2-impl` put every repository in `persistence/repository/`:

```java
package com.csci318.libraryservice.persistence.repository;

import com.csci318.libraryservice.domain.aggregateroot.InventoryItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InventoryItemRepository extends JpaRepository<InventoryItem, String> {
}
```

There is no domain-declared interface and no infrastructure implementation of it. There
is one Spring Data JPA interface, in the persistence package, typed directly on the
aggregate root. Iteration 1 is the same shape in a package called `repository/`.

So his agent was told to put repositories in `domain` and did not, and he shipped the
result. **The code is the artefact students are pointed at, so (c) is what a marker
calibrated on this repository will have in their head.**

Against the L3 slide 11 question: slide 11 places these interfaces in the **application**
layer, which is what our ADR 0001 and ADR 0002 record and follow for `sales-service` and
`insight-service`. That is a *fourth* position. Summarising all four:

| Source | Repository interface declared in |
|---|---|
| L3 slide 11 | application |
| His architecture spec (§ Layered Architecture) | data access (its own layer) |
| His agent prompt (dev-agent-v2 Phase 4) | domain |
| His code (`iter2-impl`) | persistence / infrastructure |
| **FreshFlow spec-01 §2.2 + code** | **domain** |

Our position matches his written instruction to his own agent, and matches the classic
Evans/DDD and Ports-and-Adapters convention. It does not match his shipped code. This
is worth raising with him directly, because the contradiction is his, not ours, and it
is the kind of thing that is much cheaper to settle in Week 8 than to argue in the
report.

One further observation: because his repositories are Spring Data interfaces typed on
the aggregate root, his domain classes are *forced* to be JPA entities. Position (c) and
finding 3 below are the same decision, seen twice.

---

## 3. How aggregates, entities and value objects are expressed in code

**His domain classes are JPA entities. This is deliberate and specified, not an
accident.** `1-technical-architecture-v2.md` says, in the Technology Stack section,
"**Domain Layer**: `@Entity`".

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

Value objects are `@Embeddable` (`Money`, `Isbn`, `CopyCount`, `LoanPeriod`,
`PolicySnapshot`, `Contact`, `LibraryId`, `CustomerId`), with a `protected` no-arg
constructor for Hibernate, validation in the public constructor, and `equals`/`hashCode`
by value. Entities (`Library`, `Customer`) are `@Entity` with `@EmbeddedId`.

So the DDD stereotypes are real and well done — invariants genuinely live inside the
aggregate (`InventoryItem.reserveCopyForLoan` throws
`InventoryInvariantViolationException` when `availableCopies <= 0`; `Loan.ensureRenewable`
enforces the renewal policy; `Money` rejects negative amounts and currency mismatch) —
but they are expressed *on top of* JPA, not independently of it. There is no mapper and
no separate persistence entity for any aggregate. The only classes in
`persistence/entity/` are `InventoryEventEntity` (event store row),
`InventoryReadModelEntity` (CQRS projection) and `LibraryPolicyEntity` (config table) —
none of which is an aggregate.

Two consequences visible in his code that are worth naming, because they are precisely
what persistence ignorance is meant to prevent:

- `domain/domainservice/CirculationPolicyService` is annotated `@Service` and imports
  `persistence.entity.LibraryPolicyEntity` and
  `persistence.repository.LibraryPolicyRepository`. **His domain layer depends on both
  Spring and his infrastructure layer.** Under our spec-01 §2.2 this would be a hard
  failure; in his codebase it is normal.
- `InventoryEventSourcingService` returns `InventoryReadModelEntity` from public
  methods, so persistence types leak upward into the application layer and into
  `LibraryQueryService`.

**Ours**: domain classes are POJOs with no framework imports at all. `StockBatch`,
`PriceRule`, `Money`, `Sku`, `Quantity`, `ExpiryDate`, `BatchId` import only `java.*`.
Persistence is a parallel set of classes in `infrastructure` —
`StockBatchJpaEntity` + `StockBatchMapper` + `JpaStockBatchRepository`,
`PriceRuleJpaEntity` + `PriceRuleMapper` + `JpaPriceRuleRepository`. Each domain
aggregate costs three infrastructure classes that his costs zero.

This is the single largest structural divergence between the two codebases.

---

## 4. Specification documents

### His, on `iter2-spec`

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

### Ours

```
specs/spec-01-architecture.md    509 lines
     spec-02-requirements.md     377
     spec-03-domain-model.md     311
     spec-04-api-contract.yaml   452
     dev-agent-prompt.md         131
```

Totals are comparable (his ~2,135 lines / 9,428 words; ours 1,780 / 11,151). The
*distribution* is not.

**Structural correspondence is close.** His 1/2/3/4 maps almost exactly onto our
spec-01/02/03/04 — architecture, requirements, domain model, API contract, plus an agent
prompt. Our numbering scheme, our contract-first OpenAPI file, and our
read-only-specs-plus-phased-agent-prompt discipline are all recognisably modelled on
this. That alignment is an asset and should be stated in the report.

**Four real differences:**

1. **He versions specs by iteration folder; we version in place.** `specs/iter1/` is
   frozen and `specs/iter2/` supersedes it, and `dev-agent-v2.md` explicitly scopes the
   agent to one folder ("You must not inspect other spec folder"). We edit spec-01 to
   -04 in place and let git history carry the evolution, with ADRs in `docs/decisions/`
   recording rulings. His scheme makes the *delta between iterations* readable without
   git; ours makes the *current truth* unambiguous. His also opens each v2 document
   with an explicit "Evolution from iter1" list — a very cheap device we do not use.

2. **He splits specs per service; we do not.** He has separate user-story and
   domain-model documents for `book-service` and `library-service`. With four services
   plus a contracts module, our single spec-03 covering all four domain layers is
   denser than anything he ships.

3. **He devotes a whole document to each new architectural concept.**
   `5-event-sourcing.md` and `6-cqrs-command-query.md` exist purely because Iteration 2
   introduced event sourcing and CQRS. They are short (45 and 95 lines) and mostly
   tables: an event-field table, an event-type table, a user-story-to-command/query
   classification table, a command catalog and a query catalog. **We have nothing
   equivalent for Kafka Streams, our topology, or our CQRS read side** — that material
   is spread through spec-01 §2.6 and spec-02 FR12/FR13. This is the clearest
   actionable gap in our specs.

4. **His specs are thinner and more tabular; ours are more prescriptive prose.** His
   architecture spec is 33 lines of bullet points. Ours is 509 lines with HARD
   CONSTRAINT blocks, per-service pattern rulings and a dependency allowlist. Neither is
   wrong. Ours contains far more binding decisions, which is why we need ADRs and he
   does not.

**On the agent prompt:** his four phases are Specification Audit → Environment &
Dependency Audit → Contract-First Test Generation → Implementation & Verification Loop.
Ours are Phase 1 specification inspection → Phase 2 skeleton → Phase 3 tests that must
FAIL → Phase 4 implement until green. Same pipeline. Two of his instructions we have
matched almost word for word: "HALT IMMEDIATELY, report the discrepancy, and request
human clarification", and "Retain all integration tests from previous milestones to
prevent regressions". Our "tests must fail first" and "never weaken a test" are
additions of ours; he says neither.

---

## 5. What he does that we do not, and vice versa

### He does; we do not

| | Detail | Assessment |
|---|---|---|
| **Event sourcing** | `InventoryAggregate` is event-sourced: an `inventory_events` append-only table with `eventId`/`aggregateId`/`aggregateType`/`eventType`/`aggregateVersion`/`occurredAt`/JSON `payload`, plus a projected `inventory_read_model`. Specified in `5-event-sourcing.md`. | **The one real capability gap.** L5 is titled "Event-sourcing, CQRS and bounded context integration". We have CQRS read models and event-driven integration, but no event store and no replay. |
| **`domain/` sub-packaged by DDD stereotype** | `aggregateroot`, `entity`, `valueobject`, `domainevent`, `domainservice` | Cheap, purely cosmetic, and makes the DDD content of the codebase visible in a file listing. Worth considering. |
| **`@RestControllerAdvice` global exception handler** | Both services, both iterations. Maps `ResourceNotFoundException`→404, `ValidationFailedException`→400, `MethodArgumentNotValidException`→400 with per-field detail. | We have no `@ControllerAdvice` anywhere. We return errors by mapping `ApplicationResult` to `ResponseEntity` inside each controller. Ours works and keeps invariant violations out of the presentation layer, but his is the idiomatic Spring answer and is less code. |
| **Bean validation** | `spring-boot-starter-validation`, `@Valid @RequestBody` on every write endpoint. | **Note the conflict:** our spec-01 §1.1 dependency allowlist *forbids* `spring-boot-starter-validation`, and `CreateTransactionRequestDto` and `CreateBatchRequestDto` both carry Javadoc saying so. The coordinator's own reference uses it. This is a strong candidate for the coordinator-approval route CLAUDE.md contemplates. |
| **Branch-pair as the AI-development evidence** | `spec-only` vs `post-implementation`; the README says the teaching method is "review and explain progress using a **before/after branch comparison**". | **Our evidence method is different in kind.** We have `docs/ai-log/` prose logs and `docs/decisions/` ADRs on a single `main` branch. His is a diff you can run. If Section 5 of the report is assessed against his method, a spec-only tag or branch would make our evolution equally inspectable at near-zero cost. |
| **Per-service user stories with IDs (`L1`–`L11`, `B1`–`Bn`)** and a story→CQRS-type→aggregate mapping table | `6-cqrs-command-query.md` §5 | We have FR1–FR15 but no equivalent per-requirement mapping table showing which requirement is a command, which is a query, and which passes through an aggregate. That table is a very effective one-page argument that CQRS was applied deliberately. |
| **Optimistic locking** | `@Version` on `InventoryItem` | We have no concurrency control on `StockBatch`. FEFO deduction across batches is exactly the place a marker might ask about it. |
| **Policy captured as an immutable snapshot at transaction time** | `PolicySnapshot` frozen into `Loan` at checkout, so a later policy change cannot retroactively alter an existing loan | Genuinely good domain modelling. Our `PriceSnapshot` in `sales-service` is the same idea, so we are aligned here. |

### We do; he does not

| | Detail | Assessment |
|---|---|---|
| **Event-driven integration over Kafka** | He has **no** messaging of any kind. `library-service` calls `book-service` over synchronous REST via `BookServiceClient` (`RestClient`). His `pom.xml` files contain only `web`, `data-jpa`, `validation`, `h2`, `test`. | We are materially ahead on L6. His system has no stream processing, no topics, no consumers. |
| **A shared `contracts` module for integration events** | `IntegrationEvent`, `BatchReceivedEvent`, `ItemSoldEvent`, `StockLevelChangedEvent`, `PriceRulePublishedEvent`, `SellThroughRiskDetectedEvent`, each with `eventId` + `occurredAt` | He has domain events, but they never cross a service boundary and there is no shared contract module. Our internal-vs-integration event distinction (spec-01 §2.6, Table A/B) has no counterpart in his work. |
| **Domain events that are actually published** | — | In `LibraryCommandService.borrowBook`, the line `loan.borrowEvent(now);` constructs a `BookBorrowed` and **discards the return value**. Same for `markReturned` and the rest. Only `InventoryItem`'s events reach the event store. His `Loan` and `FineLedger` domain events are, in practice, dead code. Ours are published. |
| **Ports and Adapters / dependency inversion** | `StockBatchRepository`, `DomainEventPublisher`, `PriceRuleRepository`, `PriceRuleEventPublisher` declared inward; adapters in `infrastructure` | He has no ports at all. Every collaborator is a concrete Spring Data interface or `@Service`. |
| **Persistence-ignorant domain** | POJOs + separate JPA entities + mappers | Discussed in §3. |
| **Deliberately different domain patterns per bounded context** | Domain Model in `inventory`/`pricing`, Transaction Script in `sales`, no domain layer in `insight` (spec-01 §2.3) | He applies one uniform shape to every service. Our variation is a direct application of L4's "domain design patterns" and is a genuine differentiator — but it only reads as deliberate if the report says so. |
| **Unit tests, not just integration tests** | `FefoDeductionPolicyTest`, `RiskAssessmentPolicyTest`, `PriceRulePublishedSnapshotFeedTest`, `ItemSoldConsumptionTest` alongside four API integration tests. 41 `@Test` methods. | He has exactly two test classes in the whole project — `BookControllerIntegrationTest` (10 tests) and `LibraryControllerIntegrationTest` (23) — both `@SpringBootTest` + `MockMvc`. **He has no unit test of any domain class.** His agent prompt only ever asks for contract-derived integration tests. |
| **ADRs** | `docs/decisions/0001`–`0003` | He has no decision record of any kind. |
| **Idempotent consumption** | `eventId` + a `ConsumedEventJpaEntity` dedup table | His event store has a `unique` constraint on `eventId` and his spec names it "used for idempotency deduplication", but nothing in his code actually deduplicates on it. |
| **Event-time semantics** | `OccurredAtTimestampExtractor`, all windowing on producer-assigned `occurredAt` | He has `occurredAt` fields, but with no stream processing there is nothing to window and no ingestion-vs-event-time distinction to get wrong. |
| **Injected `Clock`** | `ClockConfiguration` per service | He hardcodes `private final Clock clock = Clock.systemDefaultZone();` as a field initialiser in three services — not injected, not overridable in tests. |

---

## 6. Direct answer: reading his code as the reference implementation, which of *our*
## choices would look wrong or unusual to a marker?

Ranked by how likely they are to actually cost marks.

### 1. Persistence-ignorant POJOs with separate JPA entities and mappers — highest risk

His architecture spec says "**Domain Layer**: `@Entity`" and his code does exactly that.
A marker calibrated on this reference opens `inventory-service/domain/StockBatch.java`,
finds no `@Entity`, then finds `infrastructure/StockBatchJpaEntity.java` and
`StockBatchMapper.java`, and has to decide whether that is rigour or duplication. Three
classes where the reference has one, times two aggregates.

It is not wrong — it is the stricter reading, it is what Evans and Fowler prescribe, and
it is what our own spec-01 §2.2 mandates in writing. But it is **the** conspicuous
divergence, and it will not defend itself. The report needs a short, explicit passage
naming persistence ignorance, saying why we chose it, and — this is the persuasive part
— pointing at what it buys: his `CirculationPolicyService` sits in
`domain/domainservice/` and imports `persistence.repository.LibraryPolicyRepository` and
Spring's `@Service`, so his domain layer depends on his infrastructure layer. Our
`FefoDeductionPolicy` cannot do that, by construction. That is a concrete defect our
choice prevents in the reference implementation itself.

### 2. Repository interfaces declared in `domain` — second highest, and contested

His code puts them in `persistence/repository`. His architecture spec makes Data Access
a peer fourth layer. L3 slide 11 puts them in `application`. His agent prompt puts them
in `domain`. Four artefacts, three answers.

Our choice is defensible from the strongest of those four (his own written instruction
to his agent, plus the DDD literature), but it is not what the reference implementation
shows. **Recommendation: ask him.** Frame it as what it is — a contradiction between
`dev-agent-v2.md` Phase 4 and `persistence/repository/` on `iter2-impl` — and ask which
he intends to mark against. A one-line answer from him is worth more than any amount of
argument in Section 5.

Second-order risk here: our answer is not even *uniform*. spec-01 §2.2 says domain, but
ADR 0001/0002 put ports in `application` for `sales-service` and `insight-service`,
citing L3 slide 11. That is well reasoned and documented, but "we do it three different
ways in four services" needs the one-paragraph justification that ADR 0002's table
already contains, restated in the report itself rather than left in `docs/decisions/`.

### 3. `application` instead of `service` — low risk, but the most visible

Purely a name. His four layers are Presentation / Service / Domain / Data Access; ours
are presentation / application / domain / infrastructure. A marker scanning our file
tree for the layer that holds business logic will not find a `service` package in any of
our four services.

Not worth renaming — `application` is standard DDD vocabulary, and our
command-plus-handler classes (`ApplyItemSoldCommand` / `ApplyItemSoldHandler`) are more
precisely "application layer" than "service layer". But **one sentence in the report
mapping our names onto the subject's names** removes the friction entirely: "our
`presentation`/`application`/`infrastructure` correspond to the subject's
Presentation/Service/Data Access layers." Cheapest possible mitigation.

### 4. No global exception handler — low risk, easily fixed

Every one of his controllers is backed by a `@RestControllerAdvice`. We have none. Ours
returns errors by threading `ApplicationResult` through the controller, which keeps the
error path explicit and testable, but a Spring-idiom marker will read the absence of
`@ControllerAdvice` as something we did not know about rather than something we chose.
Worth a sentence, or worth adding.

### 5. No bean validation — low risk, but a self-inflicted constraint

Our spec-01 §1.1 forbids `spring-boot-starter-validation`, and two DTOs carry Javadoc
explaining that manual validation is a consequence. The coordinator's reference uses
`@Valid` throughout. Since our own house rule already routes dependency additions
through coordinator approval, and since the coordinator's own reference uses this exact
dependency, this is close to a free approval to ask for.

### 6. No event sourcing — a gap, not a wrong choice

`iter2-impl` event-sources one aggregate and specifies it in a dedicated document. We
event-source nothing. Our Kafka Streams read models are a legitimate and arguably more
advanced CQRS read side, and FR13's stream-table join has no counterpart in his work at
all — but "CQRS" and "event sourcing" are named together in L5, and we can currently
evidence only one of them.

Not something to build in Week 8 on the strength of this comparison alone; the
milestone is FR2/FR13 and the agent is Week 9. But it should be a conscious, recorded
decision rather than an omission, and the report should say plainly which half of L5 we
implemented and why.

### 7. Single-branch history with prose AI logs — low risk, worth a cheap hedge

His README states the teaching method: "review and explain progress using a
**before/after branch comparison**". Our evidence is nine `docs/ai-log/` entries and
three ADRs on one branch. That is *more* evidence, and better evidence, but it is not in
the shape his method produces. Tagging the commit where the specs were complete and
implementation had not begun would make our before/after inspectable by `git diff` for
almost no effort.

### What would *not* look wrong

Worth stating, because these are the places where deviating from him is the point:

- **Kafka, stream processing, event-time windowing, the `contracts` module.** He has
  none of it. This is L6 material and we are the only one of the two codebases that
  demonstrates it.
- **Different domain patterns per bounded context.** Directly out of L4. He applies one
  shape uniformly.
- **Unit tests of domain logic.** He has zero. `FefoDeductionPolicyTest` and
  `RiskAssessmentPolicyTest` test invariants in isolation, which is only possible
  *because* our domain classes are POJOs — the persistence-ignorance choice and the
  unit-testability argument are the same argument, and should be made together.
- **ADRs.** No counterpart in his work; unambiguously additive.

---

## Recommended follow-ups (none actioned — this is a report only)

1. Ask the coordinator to resolve the repository-placement contradiction between
   `dev-agent-v2.md` Phase 4 and `persistence/repository/` on `iter2-impl`.
2. Ask whether `spring-boot-starter-validation` may be added, citing his own use of it.
3. Add a name-mapping sentence (presentation/application/infrastructure ↔
   Presentation/Service/Data Access) to the report and/or README.
4. Consider a `specs-complete` tag to give a before/after diff comparable to his
   branch pair.
5. Consider a requirement→command/query/aggregate mapping table for FR1–FR15, modelled
   on his `6-cqrs-command-query.md` §5.
6. Record a deliberate position on event sourcing rather than leaving it absent.
7. Consider `@Version` on `StockBatch`, given FEFO deduction spans batches.

Items 1 and 2 are questions for the coordinator; the rest are Week 8+ decisions for the
team. Nothing in this report was acted on.

## Assumptions made that the sources did not state

- That `iter2-impl` — not `iter2-spec` — is the code a marker would treat as the
  reference implementation. `iter2-spec` carries Iteration 2 *specs* over Iteration 1
  *code*, which appears to be an intermediate teaching state rather than an endpoint.
  The task named `iter2-spec`; all four branches were read so the conclusion does not
  depend on that reading.
- That `rest-services-with-spring-v2` is a Spring/JPA onboarding demo and not an
  architectural reference. It has no service layer and returns JPA entities from
  controllers; treating it as normative would contradict the case-study repository.
- That L3 slide 11 places port/repository interfaces in the application layer. This was
  taken from our own ADR 0001 and ADR 0002, which cite it; the PDF could not be rendered
  in this environment to verify the slide directly. **Worth confirming by eye before the
  claim is repeated in the report.**

## Deviations from the specs

None. No FreshFlow source file, spec or configuration was modified. The reference
repositories were cloned to `../_reference/`, outside the repository root, per
instruction.

## Where I judged a source wrong — flagged, not acted on

- The coordinator's `dev-agent-v2.md` and his `iter2-impl` code disagree on repository
  placement (§2). Not acted on; team/coordinator decision.
- In his `LibraryCommandService`, domain events returned by `Loan` and `FineLedger`
  behaviours are constructed and discarded. This is a defect in the reference, not in
  our work, and is noted only because it bears on how much weight his event handling
  should carry as a model for ours.
- His `domain/domainservice/CirculationPolicyService` imports from `persistence` and
  from Spring. Under our spec-01 §2.2 this is a hard layering violation. Noted as
  evidence for the persistence-ignorance argument, not as something to imitate.
