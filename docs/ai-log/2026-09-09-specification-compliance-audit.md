# Specification Compliance Audit — FreshFlow vs. the CSCI318 Group Project Specification

**Date**: 9 September 2026 (Week 7)
**Type**: Read-only audit. No source file was modified, no code was written.
**Audited against**: `CSCI318 Group project specification.docx` (Group_Work_claude_code/),
CLAUDE.md, and `specs/spec-01` … `spec-04`.
**Repository state audited**: `0f49675` local, plus `438d16f` on `origin/main`
(`docs: add use case UC-01 for FR1`, authored by Reuben Kruger, 9 September 2026),
which this audit accounts for.

---

## 0. Method and evidence base

Every status below is anchored to something countable in the repository. Where a
claim is "we have X", a path or a number follows it. Where a claim is "we do not
have X", it means a search of the whole tree returned nothing.

Facts established by direct inspection on 9 September 2026:

| Measurement | Value | How obtained |
|---|---|---|
| Maven modules | 6 (`contracts`, 4 services, `pos-simulator`) | `pom.xml` `<modules>` |
| Microservices | 4 | `spec-01 §1.2`; one Spring Boot application class each |
| Java source files | 156 (main + test) | `find . -name '*.java' -not -path '*/target/*'` |
| Test classes / test methods | 9 / 41 | `grep -c '@Test'` per file |
| Test result | **41 run, 0 failures, 0 errors, 0 skipped** | `./mvnw -o test`, exit code 0, this session |
| spec-04 endpoints implemented | **6 of 8** | `grep '@GetMapping\|@PostMapping'` |
| Kafka topics specified / wired | **10 / 6** | spec-01 §2.6 Tables A+B vs. `destination:` in the four `application.yml` |
| Functional requirements specified | 16 (FR1–FR15, FR6 split into 6a/6b) | spec-02 |
| Functional requirements implemented | **7 fully, 2 partially** | see §2.1 |
| Use cases written | **1** (UC-01) | `docs/report/section-2-1-use-cases.md` on `origin/main` |
| UML diagrams of any kind | **0** | `docs/diagrams/` is empty; no `.png/.svg/.puml/.mmd/.drawio` in the tree; no `mermaid`/`@startuml` fence in any `.md` |
| README.md | **3 lines** ("# FreshFlow" + one sentence) | `cat README.md` |
| Weekly meeting minutes | **1 file** (Week 3), with unfilled `_[Name 1]_` placeholders | `docs/minutes/` |
| AI development log documents | 11 files, ~3,200 lines | `docs/ai-log/` |
| Architecture decision records | 3 | `docs/decisions/` |
| Commits | 34 | `git rev-list --count` + 1 remote |

**One structural fact worth stating up front.** There is no project report document
in the repository. `docs/report/` contains a single file holding a single use case,
created yesterday. Every "Section N" below is therefore audited as *"what raw material
exists that a report section could be written from"*, not as *"is the section written"*
— because none of them are.

---

## PART A — Section-by-section compliance audit

Requirements are quoted from the specification's "Detailed Requirements and Project
Report Structure" and from its "README.md File" and submission clauses.

---

### Cover Page

> "The application name … The project group's ID and name … all group members …
> Accountability information (member contribution)." Each member must write
> "I agree with my group member contribution report and ratings" and insert a signature.

**What we have**: Nothing. No cover page artefact exists. Member names appear
nowhere in the repository — `docs/minutes/week03-minutes.md` still carries
`_[Name 1]_`, `_[Name 2]_`, `_[Name 3]_` placeholders. Git shows two distinct
author identities (`watchcat2077`, `Reuben Kruger <rk831@uowmail.edu.au>`).

**Status**: **NOT MET**

**Smallest closing work**: A one-page cover with names, student numbers, the
percentage rating per member, a paragraph justifying each rating, and three signed
agreement statements. This cannot be produced by an agent — it needs the three
members. It is also a **gating** item: the specification makes the contribution
report the basis on which the group mark is scaled per member.

---

### Abstract

> "An executive summary of the application, in one or two paragraphs. A link to the
> GitHub repository of this project is included."

**What we have**: Nothing written. Raw material exists: `spec-01` line 4 carries the
one-line system description ("Real-Time Expiry Risk Detection and Agentic Markdown
Decision Support") and `pom.xml` `<description>` repeats it. The repository URL is
`https://github.com/watchcat2077/Freshflow.git`.

**Status**: **NOT MET**

**Smallest closing work**: Two paragraphs plus the URL. Perhaps 30 minutes once
Section 1 exists.

---

### Table of Contents

**What we have**: Nothing (no report document).

**Status**: **NOT MET**

**Smallest closing work**: Automatic once the report is drafted in Word/Docs.

---

### Section 1 — Introduction

> "Address the business case … What problem does it solve? What are its primary
> objectives (e.g. business needs, stakeholders' expectations)? Describe the system's
> core capabilities. Explain how these system capabilities meet the stated objectives,
> specifically highlighting how AI empowers the business and adds value."

**What we have**: **The objectives are referenced but never defined anywhere in this
repository.** `spec-02` invokes `O1`–`O5` twenty-plus times as the justification for
each requirement ("Relevance: Supports O1 and O3"), but the only place they are ever
named is a five-row traceability table on the last page (`spec-02:373-377`), giving
one-line labels: *Reduce write-off value; Protect gross margin; Remove judgement burden
from staff; Make decisions auditable; End-of-day visibility*. There is no problem
statement, no stakeholder list, no quantification, and no business case. That material
exists only in `FreshFlow_Project_Proposal.md.pdf`, which lives **outside** the
repository.

**Status**: **NOT MET** (materially — the labels exist; the section's actual content
does not)

**Smallest closing work**: Lift the business case from the project proposal PDF into
`docs/report/section-1-introduction.md`, and while doing so promote O1–O5 from a
trailing table into a stated objectives list at the head of `spec-02`. That second
half is cheap and repairs a real specification defect: today every FR justifies itself
by pointing at an identifier the reader cannot resolve without reading to the end.

---

### Section 2.1 — Functional Requirements

> "Identify and clearly state **at least 12 functional requirements**. For each
> requirement, explain its relevance to the objectives … **Specify these requirements
> using use cases and illustrate them with UML activity diagrams.**"

This clause has three separable obligations. They are at very different levels of
completion and must not be reported as one.

#### 2.1(a) — At least 12 functional requirements, clearly stated

**What we have**: **16 requirements** in `specs/spec-02-requirements.md` (377 lines):
FR1–FR15 with FR6 split into FR6a/FR6b. Each carries a user-story statement, a
`[MVP-CORE]`/`[MVP-PLUS]`/`[POST-MVP]` scope tag, and acceptance criteria. Exceeds
the minimum of 12 by four.

**Status**: **MET**

#### 2.1(b) — Relevance of each requirement to the stated objectives

**What we have**: Every FR carries a `**Relevance**:` line naming the objectives it
serves, plus a traceability matrix at `spec-02:371-377`. The mechanism is complete and
well built. It is undermined only by the Section 1 gap above: the objectives it traces
*to* are one-line labels rather than stated objectives.

**Status**: **PARTIAL**

**Smallest closing work**: Define O1–O5 properly (same work as Section 1). No change
to the FR text is needed.

#### 2.1(c) — Use cases

**What we have**: **1 use case of 16 requirements.** `docs/report/section-2-1-use-cases.md`
(on `origin/main`, commit `438d16f`) contains UC-01 for FR1 — actor, preconditions, a
six-step main flow, two alternative flows, postconditions. The template is a good one and
should be reused verbatim.

Two defects in that file, both cheap to fix and both worth fixing before it is copied
fifteen times:
1. Every markdown character is backslash-escaped (`\# Section 2.1`, `\### Main flow`,
   `1\.`, `\-`). It will render as literal backslashes rather than as headings and lists.
   This looks like a paste through an editor that escaped the source.
2. Lines are double-spaced throughout, which will produce large gaps in the rendered
   output.

**Status**: **NOT MET** (1 of 16; the specification requires at least 12)

**Smallest closing work**: Fix the escaping in the existing file, then write 11 more
use cases against the same template. Prioritise the seven requirements that are actually
implemented (FR1 ✓, FR2, FR5, FR6a, FR8, FR12, FR13) because their flows can be read
straight out of working code and passing tests; then five more from the specified-but-
unbuilt set (FR4, FR9, FR11, FR14, FR15) to clear twelve. Estimated 4–6 hours.

#### 2.1(d) — UML activity diagrams

**What we have**: **Nothing.** `docs/diagrams/` exists and is empty. A whole-tree search
found no `.png`, `.svg`, `.puml`, `.mmd` or `.drawio` file, and no `mermaid` or
`@startuml` fence in any markdown file.

**Status**: **NOT MET**

**Smallest closing work**: Twelve activity diagrams, one per use case. Author them as
Mermaid `flowchart` blocks committed as `.mmd` under `docs/diagrams/` — they are diffable,
they render natively on GitHub, and each one is a direct transcription of the numbered
main flow and alternative flows already written in the corresponding use case. Doing the
use case first makes its diagram close to mechanical. Estimated 4–6 hours for twelve
once the use cases exist.

This is the single largest mark-bearing gap in the report. See Part B.

---

### Section 2.2 — Real-time Analysis Requirements

> "**At least two (2)** of the identified user stories must involve real-time analysis
> based on streaming data processing."

**What we have**: **Two, both specified and both implemented and tested.**

| | FR12 — Rolling sell-through velocity | FR13 — Expiry risk detection |
|---|---|---|
| Specified | `spec-02:213-266` | `spec-02:268-330` |
| Topology | `insight-service/.../infrastructure/InsightTopologyConfiguration.java` | same file, one topology |
| Technique | Sliding window aggregation over `item-sold`, keyed by SKU, compared against a baseline `KTable` | **Stream-table join** between the velocity stream and a live batch-state `KTable` built from `batch-received` + `stock-level-changed` |
| Event time | `OccurredAtTimestampExtractor` — producer-assigned `occurredAt`, never ingestion time | same |
| Exposed by | `GET /api/insight/velocity/{sku}` via Interactive Query | `GET /api/insight/risks` |
| Emits | — | `sell-through-risk-detected`, on transition into risk |
| Tests | `VelocityApiIntegrationTest` (4) | `RiskAssessmentPolicyTest` (7) + `RiskApiIntegrationTest` (2) |

This is genuine streaming work, not a threshold check dressed up as one. The windowing
is event-time throughout, the join is a real `KStream`–`KTable` join, and the
"which window is current" rule (fully-elapsed, latest, 404 if aged out) is specified
rather than left to the implementation.

**Status**: **MET** — for the implementation. The *report section* describing it does
not exist.

**Two known limitations are recorded in spec-02 and must be disclosed in the report
rather than discovered by a marker**:
- A SKU that stops selling **entirely** is never re-assessed, because assessment is
  triggered by a closing velocity window and a window only closes because sales arrive.
  Scheduled as the first Week 8 item, with a punctuator as the intended fix.
- "Remaining trading hours" is undefined by any specification and defaults to 24 via
  `freshflow.insight.risk.trading-hours-per-day`, so every projection currently runs
  over clock hours.

**Smallest closing work**: Write the section. The substance is done.

---

### Section 2.3 — Agentic AI Requirements

> "**At least two (2)** of the identified user stories must involve the use of an
> agentic AI component."
> (With §3.6: "Explain how the LLM models are integrated … how their APIs are utilised.
> Present sample code.")

**What we have**: Two requirements **specified**, zero **implemented**.

- FR14 — Agentic markdown recommendation (`spec-02:332-372`). Well specified: ReAct /
  tool-use pattern named, a six-row tool table, trace persistence, and the constraint
  that the agent proposes only and never writes prices.
- FR15 — End-of-day waste and markdown briefing (`spec-02:374-379`). Three lines. Not
  developed to the same depth as FR14.

Against that:

| Check | Result |
|---|---|
| `langchain4j` in any `pom.xml` | **absent** — searched all seven POMs |
| `ChatLanguageModel`, `AiService`, `@Tool` in any source file | **absent** |
| `freshflow.llm.*` block in any `application.yml` | **absent** (specified at `spec-01 §2.7`, never bound) |
| `AgentTraceRecord` class (spec-03 §4) | **absent** |
| `GET /api/insight/agent-traces/{agentTraceId}` (spec-04:262) | **absent** |
| `markdown-recommended` topic wired | **absent** |

**Status**: **NOT MET**

This is a **mandatory dependency** of the course specification ("mandatory dependencies:
Spring Web, Spring Data JPA, H2, Spring Cloud Stream **and LangChain4j**") and one of
the four named "Key Architectural Requirements". It is currently the largest single
risk in the project. CLAUDE.md schedules the agent for Week 9, which is on plan — but
it means the plan has no slack, and Section 3.6 cannot be written at all until it lands.

**Smallest closing work**: FR14 only, minimal shape: add LangChain4j, bind
`freshflow.llm.*`, implement the two `✅`-marked MVP tools (`getBatchDetails`,
`proposeMarkdown`), trigger from `sell-through-risk-detected`, persist `AgentTraceRecord`,
expose `GET /api/insight/agent-traces/{id}`. FR15 can then reuse the same
`ChatLanguageModel` wiring with no tools at all — it is a summarisation call over the
day's events, and is the cheapest possible way to reach the required count of two.

---

### Section 2.4 — Domain Modeling

> "Identify the domain classes … and **structure those domain classes into sub-domains**.
> **Develop a comprehensive UML domain model diagram.** Justify how these domain classes
> and sub-domains accurately reflect the problem domain and align with the concept of
> bounded contexts."

#### 2.4(a) — Domain classes structured into sub-domains

**What we have**: `specs/spec-03-domain-model.md` (311 lines), explicitly organised into
four numbered sub-domains — §1 Inventory & Batch, §2 Sales, §3 Pricing & Markdown,
§4 Insight — each with its implementation pattern named. Field-level tables, behaviour
signatures, and numbered invariants per aggregate.

**Status**: **MET**

#### 2.4(b) — Justification against the problem domain and bounded contexts

**What we have**: This is the strongest single piece of writing in the repository.
`spec-03 §0` argues the boundaries from the observation that "product" means three
genuinely different things with different lifecycles and invariants; `§5` is a
cross-context concept mapping table (Product / Quantity / Price / Time × three contexts)
presented as the evidence for the boundaries; `§6` records the deliberate absence of a
shared kernel and explains why `Money`, `Sku` and `Quantity` are re-declared per context.

**Status**: **MET** — and this should be quoted directly into the report, not paraphrased.

#### 2.4(c) — Comprehensive UML domain model diagram

**What we have**: **Nothing.** No diagram file of any kind exists.

**Status**: **NOT MET**

**Smallest closing work**: One Mermaid `classDiagram` per sub-domain plus one overview,
transcribed from the tables already in `spec-03`. The stereotypes are already assigned
in the spec (`<<Aggregate Root>>`, `<<VO>>`, `<<Entity>>`), so this is transcription, not
design. Estimated 2–3 hours. **Highest value-per-hour item in this audit.**

---

### Section 3.1 — Microservice Architecture

> "Present an architectural diagram … clearly depict all services, their inter-service
> communication mechanisms … and data flows."

Underlying requirement: *"at least four (4) bounded contexts, each implemented as a
separate microservice."*

**What we have — the services**: **4, exceeding the minimum of 4 exactly.**

| Service | Port | Bounded context | Source root |
|---|---|---|---|
| `inventory-service` | 8081 | Inventory & Batch | `inventory-service/src/main/java/com/freshflow/inventory` |
| `sales-service` | 8082 | Sales | `sales-service/src/main/java/com/freshflow/sales` |
| `pricing-service` | 8083 | Pricing & Markdown | `pricing-service/src/main/java/com/freshflow/pricing` |
| `insight-service` | 8084 | Insight | `insight-service/src/main/java/com/freshflow/insight` |

Each is a separate Maven module with its own Spring Boot application class and its own
H2 instance. No shared database. `spec-01 §1.4` carries a data-ownership table and
`§1.5` a context map naming the integration pattern on each relationship
(Customer–Supplier, Published Language, Conformist, Anti-Corruption Layer).

**Status of the four-microservice requirement**: **MET**

**Status of the architectural diagram**: **NOT MET** — no diagram exists.

**Smallest closing work**: One Mermaid `flowchart LR`: four service boxes, the six wired
Kafka topics as labelled arrows between them, the REST endpoints as external arrows in,
and the agent's REST tool calls drawn as a distinct arrow style. Every fact it needs is
already in `spec-01 §1.2`, `§1.4`, `§1.5` and `§2.6`. Estimated 1–2 hours.

---

### Section 3.2 — Layered Architecture

> "Illustrate the layered architecture adopted for each microservice. Each service is
> expected to include a presentation layer, a service layer, a domain layer and an
> infrastructure layer. Explain the purpose and responsibilities of each layer.
> **Demonstrate the alignment of these architectural layers with the namespace (i.e.
> package) structure in your software project's codebase of each service.**"

**What we have**: The package structure mirrors the layers exactly, and this was
verified by inspection rather than assumed:

```
com.freshflow.inventory   presentation  application  domain  infrastructure
com.freshflow.sales       presentation  application  domain  infrastructure
com.freshflow.pricing     presentation  application  domain  infrastructure
com.freshflow.insight     presentation  application          infrastructure
```

Verification performed this session:
- **Domain layer imports nothing.** `grep '^import'` across all three `domain` packages
  returns only `java.*` imports. Zero imports of Spring, JPA, Jackson, Kafka,
  `presentation`, `application` or `infrastructure`. `spec-01 §2.2`'s hard constraint
  holds in fact, not just on paper.
- **No inward-pointing violations.** `application` and `domain` packages import nothing
  from `infrastructure` or `presentation` in any service.
- Persistence ignorance is real: `StockBatch` (POJO, `domain`) is distinct from
  `StockBatchJpaEntity` + `StockBatchMapper` (`infrastructure`). Same in `pricing`.
- Each layer package carries a `package-info.java` stating its responsibility — 16 of
  them across the four services.

Two deliberate exceptions, both documented at `spec-01 §2.1` and justified at `§2.3`:
`insight-service` omits `domain` (CQRS read side, no transactional business data);
`sales-service` retains `domain` but declares no aggregates or repositories there
(Transaction Script).

**Naming note.** The specification says "a **service** layer"; we call it `application`.
These are the same layer, and `spec-01 §2.1` labels it "Application service layer".
`docs/ai-log/2026-09-08-coordinator-reference-comparison.md §3` flags that the
coordinator's own reference repositories use `service`, and rates the divergence
low-risk but highly visible. The report should state the equivalence in one sentence
in Section 3.2 rather than leave a marker to infer it.

**Status**: **PARTIAL** — the substance is complete and demonstrably correct; the
required *illustration* does not exist.

**Smallest closing work**: One layer diagram (identical for the three Domain Model
services, plus one variant for `insight-service`), annotated with the actual package
names and one arrow showing the dependency direction. Then a table mapping layer →
package → two example classes per cell. The table can be generated straight from the
file listing. Estimated 1–2 hours.

---

### Section 3.3 — Domain-Driven Design Patterns

> "Discuss the design patterns applied to your domain classes, specifically focusing on
> **entities, value objects, aggregates, domain events and domain services** … explain
> why a particular domain class belongs to a specific pattern. Include relevant sample
> code snippets."

**What we have**: Every named building block exists in code, not only in the spec:

| Block | Implemented | Specified but not built |
|---|---|---|
| Aggregate Root | `StockBatch` (`inventory/domain`) | `MarkdownPlan` |
| Entity | `PriceRule` (`pricing/domain`) | `GoodsReceipt`, `WriteOffRecord` |
| Value Object | `Money`, `Sku`, `Quantity`, `ExpiryDate`, `BatchId` (inventory); `Money`, `Sku`, `MarginFloor` (pricing) | `DiscountTier`, `PromotionWindow` |
| Domain Event | `BatchReceived`, `StockLevelChanged`, `PriceRulePublished` (in `domain`); 6 integration events in `contracts` | markdown events |
| Domain Service | **`FefoDeductionPolicy`** (`inventory/domain`) — spans multiple `StockBatch` aggregates, therefore belongs to none | `MarginFloorPolicy` |
| Repository (interface in `domain`) | `StockBatchRepository`, `PriceRuleRepository` | `MarkdownPlanRepository` |

The pattern selection is itself argued per bounded context at `spec-01 §2.3` — Domain
Model for Inventory and Pricing, Transaction Script for Sales, no domain layer for
Insight — with the justification that applying Domain Model uniformly to a context with
little business logic is over-engineering. That argument is exactly what this clause
asks for, and it is already written.

**Status**: **PARTIAL**

Two gaps:
1. `MarkdownPlan` and `MarginFloorPolicy` are unbuilt, blocked on two open specification
   items that the team has deliberately not guessed at: **B-6** (`activate(...)` does not
   receive `basePrice`, so it cannot evaluate its own invariant 1) and **B-3**
   (`MarginFloorPolicy.isBreached` returns `boolean`, which cannot carry the
   `resultingPrice`, `resultingMarginRatio` and `marginFloorRatio` that spec-04's
   `MarginFloorRejection` requires). Both are marked "Week 7" in `spec-03` and are now
   overdue.
2. The report section is unwritten.

**Smallest closing work**: Settle B-3 and B-6 as a team ruling (both are one-line
signature changes: pass `basePrice` into `activate`; return a result record rather than
a boolean), then build `MarkdownPlan` + `MarginFloorPolicy` + FR11's 422 path. That also
unblocks the Anti-Corruption Layer story, which is the most distinctive architectural
claim the project makes and which currently has no enforcement code behind it.

---

### Section 3.4 — Data Persistence in Microservices

> "**Illustrate your database design.** Clearly define the data ownership for each
> microservice (e.g. using a table to illustrate which microservice owns which data
> entities)."

#### 3.4(a) — Data ownership table

**What we have**: `spec-01 §1.4` is exactly the table the clause asks for — data,
owner service, and how other services obtain it. Backed in code by one H2 instance per
service and no shared datasource.

**Status**: **MET**

#### 3.4(b) — Database design illustration

**What we have**: **Nothing.** No ERD, no schema diagram, no DDL listing. The JPA
entities exist and are the raw material:

| Service | JPA entities |
|---|---|
| inventory | `StockBatchJpaEntity`, `ConsumedEventJpaEntity` (the FR2 idempotency ledger) |
| sales | `SaleTransactionJpaEntity`, `LineItemEmbeddable` |
| pricing | `PriceRuleJpaEntity` |
| insight | none — Kafka Streams state stores, not JPA (correct for a CQRS read side, but it must be *explained*, not left as an apparent omission) |

`spec-03 §7` additionally fixes the monetary storage convention (columns
`precision = 19, scale = 4`; normalised to scale 2 in the `Money` value object on the
way out) — a good detail that belongs in this section.

**Status**: **PARTIAL** — ownership met, illustration not met.

**Smallest closing work**: One Mermaid `erDiagram` per service, transcribed from the
five JPA entity classes, plus a paragraph explaining why `insight-service` has no
relational schema at all. Estimated 1–2 hours.

---

### Section 3.5 — Event-Driven Architecture and Stream Processing

> "Explain the design and implementation of your event-driven architecture. **Detail
> what stream processing queries are considered (e.g. using SQL-like language)** and how
> they are implemented to achieve real-time analysis. Present sample code."

Underlying requirements: *"Adopts an event-driven architecture for inter-service
communication"* and *"Supports real-time data queries using stream processing techniques."*

#### 3.5(a) — Event-driven inter-service communication

**What we have**: Asynchronous Kafka over Spring Cloud Stream is the **primary and
only** inter-service mechanism. No service calls another synchronously — `spec-01 §1.3`
forbids it, and the one place it would have been natural (Sales needing a price) was
explicitly ruled against on 26 August and solved with the `PriceSnapshotFeed` inbound
port instead. That ruling is honoured in code: `sales-service` has no HTTP client for
`pricing-service`.

Ten topics are specified (`spec-01 §2.6`, Table A integration + Table B internal);
**six are wired**:

| Topic | Producer | Consumer | Wired |
|---|---|---|---|
| `batch-received` | inventory | insight | ✅ |
| `stock-level-changed` | inventory | insight | ✅ |
| `item-sold` | sales | inventory, insight | ✅ |
| `price-rule-published` | pricing | sales | ✅ |
| `sell-through-risk-detected` | insight | insight | ✅ |
| `velocity-baseline-published` (internal) | insight | insight | ✅ |
| `batch-written-off` | inventory | insight | ✗ FR3 unbuilt |
| `markdown-recommended` | insight | pricing | ✗ FR14 unbuilt |
| `markdown-applied` | pricing | sales, insight | ✗ FR9 unbuilt |
| `markdown-expired` | pricing | sales, insight | ✗ FR10 unbuilt |

Every event carries `eventId` (UUID, for idempotent consumption) and `occurredAt`
(producer-assigned event time). The `contracts` module implements the Published
Language pattern, and `§2.6` distinguishes integration topics from internal ones —
a distinction added on 7 September after finding that a single-context topic had
nowhere lawful to be declared.

Idempotency is real, not asserted: `ConsumedEventJpaEntity` is a persisted
`eventId` ledger, and `ItemSoldConsumptionTest` (7 tests) covers redelivery.

**Status**: **MET** for the architecture; the report section is unwritten.

**Known defect, already recorded**: `spec-01 §2.6` documents that nothing enforces the
wire format of `eventId`/`occurredAt`, and that `sell-through-risk-detected` shipped
`occurredAt` as `1788762590.000000000` — a valid instant in a format the clause forbids —
because the Kafka Streams serde uses a different `ObjectMapper` from the message-channel
binder. No test caught it because no test serialises that topic. The intended fix (a
contract test over the `contracts` module asserting the wire shape of one instance of
every event) is specified and scheduled for Week 8. It needs no broker and runs in
milliseconds.

#### 3.5(b) — Stream processing queries

**What we have**: One Kafka Streams topology,
`insight-service/.../infrastructure/InsightTopologyConfiguration.java`, with four inputs
and one output, three named state stores (`velocity-windows`, `velocity-baselines`,
`batch-states`), a sliding window aggregation, a `KTable` join and a `KStream`–`KTable`
join, all on event time via `OccurredAtTimestampExtractor`. Exposed through Interactive
Query on two REST endpoints.

**What is missing**: the clause asks the queries to be **detailed, "e.g. using SQL-like
language"**. We have the Java topology; we have never written the queries down as
queries. That is a documentation gap, not an implementation gap.

**Status**: **PARTIAL**

**Smallest closing work**: Write each of the two analyses as a ksqlDB-style statement
beside the Java that implements it — roughly:

```sql
-- FR12
SELECT sku, COUNT(*) AS qty
  FROM item_sold
  WINDOW HOPPING (SIZE 60 SECONDS, ADVANCE BY 5 SECONDS)
  GROUP BY sku;
-- FR13
SELECT v.sku, b.batchId, b.remainingQuantity - (v.velocityPerHour * b.hoursToExpiry)
  FROM velocity v JOIN batch_state b ON v.sku = b.sku
  WHERE (...) > threshold;
```

Two code blocks and a paragraph. Perhaps an hour, and it converts a PARTIAL into a MET
on a clause the specification calls out by name.

---

### Section 3.6 — Agentic AI Component

> "Provide a detailed description of the agentic design patterns used. Explain how the
> LLM models are integrated … and how their APIs are utilised. Present sample code."

**What we have**: The *design* is specified — ReAct / tool use named at `spec-02` FR14,
a tool table, `AgentTraceRecord` modelled at `spec-03 §4`, the Anti-Corruption Layer
argued at `spec-01 §1.5` and `§2.5`, and the LLM configuration contract fixed at
`spec-01 §2.7`. The *implementation* is zero: no LangChain4j dependency, no LLM code,
no config binding, no trace record, no endpoint. There is no sample code to present.

**Status**: **NOT MET**

**Smallest closing work**: As per Section 2.3. This section cannot be written at all
until FR14 exists.

---

### Section 3.7 — Other Important Aspects

> "Include any other significant aspects … that contribute to the project's success or
> demonstrate advanced understanding."

**What we have**: Unwritten, but unusually well stocked. Candidates, all evidenced:
- Per-bounded-context domain layer pattern selection with an explicit anti-over-engineering
  argument (`spec-01 §2.3`).
- Persistence-ignorant POJOs with separate JPA entities and mappers — deliberate, and
  already risk-assessed against the coordinator's reference repositories in
  `docs/ai-log/2026-09-08-coordinator-reference-comparison.md`.
- Event-time-only windowing, with the replayability argument.
- Idempotent consumption backed by a persisted ledger.
- Ports and Adapters used to substitute a configuration-seeded adapter for a Kafka one
  behind an unchanged port (`PriceSnapshotFeed`), with no application-layer change.
- Three ADRs recording decisions the specifications did not settle.

**Status**: **NOT MET** (unwritten) — but this is the cheapest section in the report to
write well, and it is where the project's genuinely above-average work can be banked.

---

### Section 4.1 — Configuration and Running Instructions

> "Provide clear, step-by-step instructions on how to configure and run the entire
> software project, including Spring Boot applications, Apache Kafka, and any LLM API
> configurations."

**What we have**:
- `docker-compose.yml` — single-node Kafka in KRaft mode, with a healthcheck and usage
  comments. Genuinely runnable.
- `mvnw` / `mvnw.cmd` — Maven wrapper committed for reproducible builds (`0f49675`).
- `scripts/kafka-smoke-test.ps1`.
- `RUNBOOK.md` (553 lines) — **but this is not running instructions.** It is a Week 3
  hour-by-hour execution plan for 12–16 August, organised by member and checkpoint
  ("S12. Inventory FR1 `morning` · Member 2"). It is a historical planning artefact.
  A marker following it would not start the system.
- LLM configuration: **nothing**, because there is no LLM component.

**Status**: **NOT MET**

**Smallest closing work**: A `docs/` runbook (or the README itself) with the literal
sequence: `docker compose up -d` → wait for health → `./mvnw -pl contracts install` →
`./mvnw -pl <service> spring-boot:run` × 4 → the port table → the environment variables
for the LLM. Perhaps 2 hours, and it must be tested from a clean clone by a member who
did not write it.

---

### Section 4.2 — User Story Input and Output

> "For each implemented user story, present at least one sample input and its
> corresponding output … Consider implementing a lightweight client (e.g. a simple
> command-line script or a Postman collection) to automate some REST requests or
> pre-populate the database, **especially for demonstrating stream processing scenarios**."

**What we have**:
- `specs/spec-04-api-contract.yaml` contains request `examples:` at three endpoints
  (lines 60, 120, 184), and the integration tests exercise them — so real inputs exist.
- **No output samples are recorded anywhere.**
- `pos-simulator` — the module the specification's "lightweight client" clause is
  describing — is a **stub**. `PosSimulator.main` prints
  `"pos-simulator: skeleton, not yet implemented (Phase 2)"` and nothing else.

This matters more than it looks. FR12 and FR13 are the two requirements the project is
built around, and neither can be demonstrated without a stream of sales arriving over
time. Without the simulator there is no way to show them working, in the report or in
the Week 12 presentation.

**Status**: **NOT MET**

**Smallest closing work**: Implement `pos-simulator` to POST transactions to
`sales-service` at a configurable rate with configurable SKU weights, then capture one
input/output pair per implemented user story into `docs/report/section-4-2-samples.md`
by actually running it. This is the item that most directly de-risks the final
presentation.

---

### Section 5 — AI-Driven Development Process

> "5.1 Describe how team members interacted with AI tools and agents throughout the
> software development lifecycle … Provide clear references to related artefacts in the
> repository … Include representative examples where AI-generated proposals were
> **accepted, rejected, or modified** by the team, and explain the rationale.
> 5.2 Reflect on the strengths and limitations of AI-assisted software engineering …
> the impact on productivity, software quality, architectural decision-making and team
> collaboration, with concrete examples."

**What we have**: This is the project's strongest deliverable by a wide margin, and it
is the section most likely to distinguish it from other groups.

| Artefact | Count | What it evidences |
|---|---|---|
| `docs/ai-log/*.md` | 11 files, ~3,200 lines | Phase-by-phase agent sessions from Phase 1 specification inspection through Week 7 part 2, each ending in the four-part report CLAUDE.md mandates: what was done, what was assumed, deviations, where the spec was judged wrong |
| `docs/decisions/000{1,2,3}-*.md` | 3 ADRs | Decisions the specs did not settle, taken by the team |
| `CLAUDE.md` | 1 | The governing constraint document — the mechanism by which architecture was enforced on the agent |
| `specs/dev-agent-prompt.md` | 1 | The four-phase gated pipeline |
| Git history | 34 commits | `test:` commits consistently precede their `feat:` commits — the tests-first gate is visible in the history, not merely claimed |

Concrete accept / reject / modify examples are already documented and can be lifted
directly:

- **Rejected**: the Phase 1 agent run **halted with 7 blocking findings rather than
  building** (`a27ab8a`, "phase 1 specification inspection report — HALT"). The team
  amended the specifications and re-ran (`e846dfd`, `a51e4db`). This is the single best
  illustration available of specification-as-control.
- **Rejected**: the proposal that `sales-service` call `pricing-service` synchronously —
  ruled against on 26 August, `PriceSnapshotFeed` adopted instead (`spec-01 §1.3`).
- **Modified**: `spec-03 §2` had specified that the Sales application service orchestrate
  directly against JPA entities. That was found to contradict the §2.2 hard constraint
  and was corrected to a write-only application-layer port (ADR 0002).
- **Accepted with a recorded limitation**: the config-seeded velocity baseline
  (ADR 0003) — accepted as an MVP simplification behind a port, with the replacement
  path specified.
- **Still open, deliberately**: B-3 and B-6 were left unimplemented rather than guessed,
  and are documented as open.
- **Limitation of AI-assisted work, self-found**: the `occurredAt` serialisation defect
  in §3.5 above — passing tests did not catch a wire-format break because no test
  serialised that topic. This is exactly the "limitations" material 5.2 asks for, and it
  is honest.
- `docs/ai-log/2026-09-08-coordinator-reference-comparison.md` (547 lines) additionally
  assesses our own choices against the coordinator's reference repositories and rates
  the risk of each divergence.

**Status**: **PARTIAL** — the evidence base is complete and unusually good; the report
section itself is unwritten, and §5.2's reflection has not been attempted at all.

**Smallest closing work**: Write 5.1 as a narrative over the artefacts above with direct
file references (the specification explicitly asks for those references, and we can give
them). Write 5.2 from the accept/reject/modify list plus the two self-found defects.
Estimated 4–6 hours, entirely from existing material, no code required.

---

### README.md (root directory)

> "A README.md file **must** be included in the root directory … This file should
> contain: Configuration and running instructions (as detailed in Section 4.1);
> Sample inputs to demonstrate the use cases."

**What we have**: `README.md`, three lines:

```
# FreshFlow

CSCI318 Spring 2026 Group Project
```

No configuration instructions. No running instructions. No sample inputs.

**Status**: **NOT MET**

This is the most conspicuous gap in the repository — it is the first file any marker
opens, and it currently gives the impression of an empty project. The work behind it is
real and substantial; the README says none of it.

**Smallest closing work**: Prerequisites (JDK 21, Docker, Maven wrapper) → `docker
compose up -d` → build → run the four services with their ports → LLM environment
variables → three or four `curl` sample inputs (create a batch, post a transaction, get
velocity, get risks) → a link to the specs and the AI log. Two to three hours. **Highest
impression-per-hour item in this audit.**

---

### Weekly Meeting Minutes (3 marks, separate from the 27)

> "Submitted to Moodle **every week from Week 3 – Week 11 inclusively**. Should cover
> at least: meeting date, attendance, progress reports, discussion summaries, and
> action plans/items. In particular, report what has been done in the previous week,
> and what has been planned in the next week."

**What we have**: **One file** — `docs/minutes/week03-minutes.md` (84 lines). Its
*structure* is excellent and exceeds the required fields: date, time, location,
attendance table, progress since last meeting, discussion summary, a seven-row decisions
table with rationale, an eleven-row action item table with owners and due dates, a risks
table, and next meeting. It is a very good template.

Two problems:
1. **It is one of five that should exist by now.** Today is in Week 7. Weeks 3, 4, 5, 6
   and 7 are all due. Four are missing.
2. **It is not filled in.** Attendance is three unticked boxes against `_[Name 1]_`,
   `_[Name 2]_`, `_[Name 3]_`. Location, scribe, next meeting date and the D4 LLM-runtime
   decision are all unfilled placeholders. As it stands it reads as a template rather
   than as the "genuine group meeting records" the specification asks for.

**Status**: **NOT MET**

**Smallest closing work**: Fill in the Week 3 placeholders, then write Weeks 4–7 against
the same template. The content is recoverable from the git history and the AI logs, which
timestamp what was actually done each week — but it must reflect real meetings, and
reconstructing minutes for meetings that did not happen is not something this audit
recommends or can do. If meetings were held and not minuted, minute them now from
members' recollection. **This is 3 marks, it is separate from the 27, and it decays —
Week 3's minutes cannot be submitted late for full credit.**

---

## PART B — Mark estimate

### Assumptions

These must be stated because the specification does **not** publish a rubric breakdown
of the 27 marks. Every number below is my allocation, not the coordinator's.

1. The 27 marks for Final Deliverables are distributed across the report structure the
   specification itself sets out, weighted by the amount of text each section is given.
   My allocation: report hygiene 2, S1 2, S2 7, S3 10, S4 3, S5 3.
2. Implementation is assessed *through* the report sections that require it (S2.2, S2.3,
   S3.5, S3.6), not as a separately marked artefact. The repository is evidence for those
   sections.
3. A section requiring an explicit artefact ("a UML domain model diagram", "an
   architectural diagram", "illustrate your database design") earns close to nothing for
   that clause when the artefact does not exist, however good the accompanying prose.
4. "Estimate today" means: what would be earned if the deliverable were submitted on
   9 September 2026 in its current state. It is **not** a prediction for Week 12.
5. Marks for Project Plan (2), Progress Report (3), Weekly Minutes (3) and Final
   Presentation (5) are outside the 27 and are not estimated here — except that the
   Minutes gap is flagged above because it is decaying now.
6. I have graded conservatively, as asked. Where a section has strong substance but no
   written report, I have credited the substance only partially, on the basis that an
   unwritten section cannot be marked.

### Estimate as at 9 September 2026

| Section | Available | Estimate | Reasoning |
|---|---:|---:|---|
| Cover / Abstract / TOC / References | 2 | **0.0** | None exist. Member names appear nowhere in the repository |
| S1 Introduction | 2 | **0.0** | Unwritten; objectives O1–O5 are referenced 20+ times but defined only as five one-line labels |
| S2.1 FRs, use cases, activity diagrams | 3 | **0.5** | 16 FRs stated and traced (strong); 1 use case of 12 required; **0 activity diagrams** |
| S2.2 Real-time analysis | 1 | **0.5** | Two requirements implemented and tested — full substance, no written section |
| S2.3 Agentic AI | 1 | **0.2** | Two specified, **zero implemented**; LangChain4j absent from the build |
| S2.4 Domain modeling | 2 | **0.8** | Sub-domains and justification are excellent; **no UML domain model diagram** |
| S3.1 Microservice architecture | 1.5 | **0.4** | Four services genuinely separate; **no architecture diagram** |
| S3.2 Layered architecture | 1.5 | **0.9** | Layering verified clean by inspection; packages mirror layers; **no illustration** |
| S3.3 DDD patterns | 2 | **1.0** | Aggregate, VOs, domain service, repository-in-domain all real; `MarkdownPlan`/`MarginFloorPolicy` unbuilt; unwritten |
| S3.4 Data persistence | 1.5 | **0.6** | Ownership table met; **no database design illustration** |
| S3.5 EDA and stream processing | 2 | **1.2** | Strongest implementation area; queries never expressed as queries; unwritten |
| S3.6 Agentic AI component | 1.5 | **0.0** | Nothing to describe. No LLM code exists |
| S4.1 Configuration and running | 1.5 | **0.2** | Compose file and wrapper exist; RUNBOOK is a Week-3 plan, not instructions; README empty |
| S4.2 Sample input / output | 1.5 | **0.1** | Inputs exist in spec-04; no outputs recorded; `pos-simulator` is a stub |
| S5 AI-driven development process | 3 | **1.5** | Outstanding evidence base (11 logs, 3 ADRs, tests-first history); section unwritten, 5.2 not attempted |
| **Total** | **27** | **≈ 7.9** | |

**Estimate today: 7–9 out of 27.** Call it **8**.

### The second, more useful number

The figure above is dominated by "no report exists", which is a Week 10–11 activity and
not by itself alarming in Week 7. The more informative question is: **if the team wrote
the report perfectly next week from what is already in the repository, adding no code and
no diagrams, where would it cap out?**

Roughly **14–16 out of 27**. The ceiling is set by four things that writing cannot
supply:

| Blocker | Marks it caps | Can writing fix it? |
|---|---:|---|
| No diagrams (activity ×12, domain model, architecture, layered, ERD) | ~4.5 | No — must be drawn |
| No agentic AI implementation | ~2.5 | No — must be built |
| No README / running instructions / sample I/O / working simulator | ~2.5 | Partly — but the simulator must be built |
| `MarkdownPlan` / `MarginFloorPolicy` / FR11 unbuilt | ~0.7 | No — must be built |

### Where we lose marks today, ranked by marks at stake

| # | Gap | Marks at stake | Assessment |
|---:|---|---:|---|
| 1 | **No diagrams of any kind.** 12 activity diagrams, the domain model diagram, the architecture diagram, the layered-architecture illustration and the database design illustration are all missing. Five separate specification clauses use the words "diagram" or "illustrate" | **~4.5** | Largest single loss. Almost entirely transcription from specs that are already written — the highest value-per-hour work available |
| 2 | **Agentic AI not implemented.** Zero LangChain4j. Kills S3.6 outright, guts S2.3, and leaves a mandatory dependency and one of four Key Architectural Requirements unmet | **~2.5** | Highest *risk*, because it is the only item on this list that could still fail in Week 9 and leave no time to recover |
| 3 | **Section 5 unwritten.** The best evidence base in the project is earning nothing | **~1.5** | Pure writing. No dependencies. Could be finished this week |
| 4 | **README empty and no runnable instructions.** Three lines. The first file a marker opens | **~1.3** | 2–3 hours. Also the biggest gap between what the project *is* and what it *looks like* |
| 5 | **11 use cases missing.** 1 of the 12 required | **~1.2** | 4–6 hours; also the input to gap 1, so it must come first |
| 6 | **No sample outputs and `pos-simulator` is a stub.** FR12/FR13 cannot be demonstrated | **~1.2** | Also the main risk to the Week 12 presentation, which is 5 further marks outside the 27 |
| 7 | **Section 1 unwritten; O1–O5 undefined.** Every FR justifies itself against identifiers the reader cannot resolve | **~1.0** | Material exists in the proposal PDF, outside the repository |
| 8 | **`MarkdownPlan` / `MarginFloorPolicy` / FR11 unbuilt**, blocked on open items B-3 and B-6 marked "Week 7" and now overdue. The Anti-Corruption Layer — the project's most distinctive architectural claim — has no enforcement code | **~0.7** | Two one-line signature decisions unblock it |
| 9 | **Cover page and contribution report absent** | **~0.7** | Cannot be done by an agent. Gates the per-member scaling of the whole group mark |

**Outside the 27, and decaying now:** weekly minutes are 3 separate marks and we have
one file of five due, unfilled. Proportionally this is the worst-performing deliverable
in the project.

---

## PART C — The five highest-value things to do next, in order

Ordered by marks recovered per hour, with dependency order respected.

### 1. Draw the diagrams — all of them

**Recovers ~4.5 marks. Estimated 8–12 hours. No blockers.**

Five clauses require an artefact we do not have. Every one of them can be transcribed
from a specification that is already written:

| Diagram | Source it transcribes | Effort |
|---|---|---|
| UML domain model (per sub-domain + overview) | `spec-03` §1–§4 — stereotypes already assigned | 2–3 h |
| Microservice architecture | `spec-01` §1.2, §1.4, §1.5, §2.6 | 1–2 h |
| Layered architecture (×2 variants) | `spec-01` §2.1, §2.3 + the actual package listing | 1–2 h |
| Database ER diagrams | the five JPA entity classes | 1–2 h |
| 12 activity diagrams | the use cases from item 2 — **do those first** | 4–6 h |

Commit them as Mermaid `.mmd` under `docs/diagrams/`: diffable, reviewable, and they
render natively on GitHub, so the repository itself starts showing the architecture.

### 2. Write the remaining 11 use cases

**Recovers ~1.2 marks directly, and unblocks the 12 activity diagrams in item 1.
Estimated 4–6 hours.**

Reuse UC-01's template exactly — and first fix its backslash-escaping and double-spacing,
before the defect is copied fifteen times. Do the seven implemented requirements first
(their flows can be read out of passing tests), then five from the specified-but-unbuilt
set to clear twelve.

### 3. Build the FR14 agent

**Recovers ~2.5 marks and clears the largest project risk. Estimated 1–2 days.
This is Week 9 in CLAUDE.md — do not start it without authorisation.**

It is third on value-per-hour but it is the only item on this list that can still
*fail*. Everything above it is transcription of work already done; this is new
engineering against an external LLM, and if the model proves unreliable at tool calling
there is no slack left to discover that in Week 11. Minimum viable shape: LangChain4j
added, `freshflow.llm.*` bound, the two MVP tools (`getBatchDetails`, `proposeMarkdown`),
trigger from `sell-through-risk-detected`, `AgentTraceRecord` persisted,
`GET /api/insight/agent-traces/{id}` exposed. FR15 then reuses the same wiring with no
tools and is nearly free — the cheapest route to the required count of two.

Note the risk flagged in the Week 3 minutes and never closed: decision **D4 (LLM
runtime) is still recorded as `_[record decision]_`.** Settle it now, not in Week 9.

### 4. Write the README, and write Section 5

**Recovers ~2.8 marks combined. Estimated 6–9 hours. Pure writing, no dependencies —
can run in parallel with items 1–3 on a different member.**

- **README** (2–3 h): prerequisites, `docker compose up -d`, build, run four services,
  port table, LLM environment variables, four `curl` sample inputs, links to `specs/`
  and `docs/ai-log/`. Test it from a clean clone with a member who did not write it.
- **Section 5** (4–6 h): 5.1 as a narrative over the 11 AI logs and 3 ADRs with direct
  file references — the specification explicitly asks for those references and we can
  give them. Lead with the Phase 1 HALT (`a27ab8a`): an agent that refused to build
  against inconsistent specifications is the strongest illustration of
  specification-as-control anyone in the cohort is likely to have. For 5.2, use the two
  self-found defects — the `occurredAt` wire-format break that passing tests did not
  catch, and the FR13 no-sales blind spot — as the honest limitations. Do not write 5.2
  as an advertisement; the marks are in the reflection.

### 5. Implement `pos-simulator` and capture sample inputs and outputs

**Recovers ~1.2 marks, and materially de-risks the Week 12 presentation (a further 5
marks outside the 27). Estimated 4–6 hours.**

The specification names this artefact — "a simple command-line script … especially for
demonstrating stream processing scenarios" — and it is currently a stub that prints one
line. FR12 and FR13 are the two requirements the whole project is built around, and
neither can be shown working without a stream of sales arriving over time. Make it POST
transactions to `sales-service` at a configurable rate with configurable SKU weights,
run it, and capture one input/output pair per implemented user story into
`docs/report/section-4-2-samples.md` from the real run.

**Also, immediately and in parallel (3 marks, outside the 27, and decaying):** fill in
the Week 3 minutes placeholders and write Weeks 4–7. The git history and AI logs
timestamp what was done each week, but the minutes must record meetings that actually
happened.

---

## PART D — Required reporting (CLAUDE.md)

### 1. What I did

Read the course specification (`CSCI318 Group project specification.docx`, extracted
from its OOXML), CLAUDE.md and all four documents in `specs/`. Inspected the repository
directly: package structure, all 156 Java files by path, controller mappings, Kafka
bindings in all four `application.yml`, JPA entities, `docs/` in full, `README.md`,
`RUNBOOK.md`, `docker-compose.yml`, `pom.xml` and the git history. Ran `./mvnw -o test`
to establish the test result by measurement rather than by reading a previous log:
**41 tests, 0 failures, exit code 0**. Fetched `origin/main` and audited the one commit
we were behind (`438d16f`, UC-01) as part of current state. Produced this audit. No
source file was modified and no code was written.

### 2. What I assumed that the specification did not state

- **The 27-mark breakdown.** The specification publishes no rubric. The allocation in
  Part B (hygiene 2, S1 2, S2 7, S3 10, S4 3, S5 3) is mine, derived from the relative
  weight the specification gives each section. A different but equally defensible
  allocation would move the total by roughly ±1.5 marks. The *ranking* of gaps in Part B
  is far more robust than the absolute total, and should be the part relied on.
- **That implementation is marked through the report sections requiring it**, rather than
  as a separately weighted artefact. The specification lists "a zip file of a software
  repository" as a deliverable but attaches no separate mark to it.
- **That a missing required artefact scores near zero for its clause** regardless of the
  quality of surrounding prose. This is the conservative reading requested.
- **That `Group_Work_claude_code/CSCI318 Group project specification.docx` is the
  authoritative version.** A second document,
  `Group project/CSCI318 Project Specification Spring 2026.pdf`, exists in the parent
  directory and was not read, because the task named the `.docx` explicitly. If the PDF
  is a later revision, this audit should be re-run against it — **worth five minutes to
  confirm.**

### 3. Deviations from the specifications

None. This was a read-only audit; no specification was applied or departed from.

The one procedural note: `./mvnw -o test` was run, which writes to the gitignored
`target/` directories. No tracked file was touched.

### 4. Where I judged something wrong — flagged, not acted on

These are reported for a team decision. Nothing here has been changed.

- **`spec-02` never defines O1–O5.** They are used as the justification for all 16
  requirements and appear only as five one-line labels in a table on the last page. The
  fix is to promote them to a stated objectives section at the head of the document. This
  is also Section 1's missing input, so it is worth doing once and using twice.
- **`RUNBOOK.md` is misnamed for what it now is.** It is a Week 3 hour-by-hour execution
  plan for 12–16 August, and a reader looking for running instructions will open it and
  find a schedule. Suggest renaming to `docs/week-03-execution-plan.md` and letting a new
  runbook or the README own the running instructions. Not done: renaming a tracked file
  is a change, and the task was read-only.
- **`spec-03` marks B-3 and B-6 "Week 7" and they are now overdue.** They block
  `MarkdownPlan`, `MarginFloorPolicy` and FR11's 422 path — and therefore the
  Anti-Corruption Layer, which is the project's most distinctive architectural claim and
  currently has no enforcement code behind it. Both are one-line signature decisions:
  pass `basePrice` into `activate(...)`; return a result record rather than a `boolean`.
  They need a team ruling, not an implementation guess.
- **`docs/report/section-2-1-use-cases.md` has escaped markdown and double-spaced lines.**
  Cosmetic, but it should be fixed before the file is used as the template for eleven more.
- **The Week 3 minutes record decision D4 (LLM runtime) as `_[record decision]_`,** with
  the associated risk "small local LLM models may be unreliable at tool calling, which
  would invalidate FR14" marked as mitigated by a test "by 13 August". Neither the
  decision nor the verification appears anywhere in the repository. With FR14 due in
  Week 9, this is an unclosed risk on the critical path.

---

*Audit produced 9 September 2026. Read-only: no source file, specification or
configuration was modified.*
