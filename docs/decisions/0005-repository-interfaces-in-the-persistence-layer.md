# ADR 0005 — Repository interfaces live in the persistence layer, `infrastructure.repository`

| Field | Value |
|---|---|
| Status | **Accepted** |
| Date | 29 September 2026 |
| Deciders | Subject coordinator (Dr Guoxin Su); recorded and applied by the project team |
| Raised as | Open question 1 of `docs/ai-log/2026-09-10-reference-adoption.md` ("Already-open items"), first set out in `docs/ai-log/2026-09-08-coordinator-reference-comparison.md` §2 |
| Affects | `inventory-service`, `pricing-service`, CLAUDE.md "Layering", spec-03 §§1 and 3, spec-01 §§2.2 and 2.4 (text not yet amended — see **Compliance**) |
| Relates to | [ADR 0001](0001-pricesnapshotfeed-declared-in-application-layer.md) and [ADR 0002](0002-transaction-script-ports-and-invariant-placement.md), whose "Explicitly NOT affected" sections kept these interfaces in `domain`; [ADR 0004](0004-llm-behind-a-port-in-the-application-layer.md), unaffected |
| Supersedes | **In part.** spec-01 §2.2's sentence "Repository interfaces are declared in `domain`"; ADR 0002 Decision 2's phrase "alongside the repository interfaces of §2.2"; ADR 0002's Consequences claim that "no `application` package imports `infrastructure`"; and the repository-interface clauses of ADR 0001's and ADR 0002's "Explicitly NOT affected" sections. Nothing else in those documents |

---

## Context

Until this decision, FreshFlow declared its repository interfaces in the domain layer and
implemented them in infrastructure — spec-01 §2.2, a HARD CONSTRAINT, in so many words. It is the
classic Evans / Ports-and-Adapters arrangement: the domain states what it needs from persistence in
its own language, and the outer layer adapts to it.

`docs/ai-log/2026-09-08-coordinator-reference-comparison.md` §2 found that the coordinator's own
artefacts took four different positions on where a repository interface belongs — application
(L3 slide 11), a separate data-access layer (his architecture spec), `domain` (his agent prompt)
and persistence (his shipped code) — and recommended asking him rather than arguing it in the
report. `docs/ai-log/2026-09-10-reference-adoption.md` carried it forward as an open question: "A
one-line ruling from him is worth more than any amount of argument in section 5 of the report."

He has now ruled.

## Sources of authority

| # | Source | What it says |
|---|---|---|
| 1 | Email from Dr Guoxin Su, **17 September 2026** | "Repositories belong a persistency layer. You may have a dedicated sub-package named 'repository' for them." |
| 2 | Consultation with Dr Guoxin Su, **22 September 2026** | "infra 包含 persistent，repo 是 persistent 的一部分." — in English (the team's rendering): *infrastructure contains persistence; the repository is part of persistence* |
| 3 | CSCI318 lecture L4, **slide 34** | Repositories are "often not in the domain layer but the data access layer" |
| 4 | His reference implementation, **`gxshub/cargo-tracker-0.1`** | Repositories under `infrastructure/repositories/` |
| 5 | His reference implementations **`gxshub/customer-support-agent`** and **`gxshub/book-and-library-management-system-v1`** (branch `iter2-impl`), re-read 4 October 2026 | Repositories under `infrastructure.repository` and `persistence.repository` respectively; none of the five reference code bases declares a repository interface in `domain`. `cargo-tracker` is also the one reference that names its use-case layer `application`, so FreshFlow's package name has a precedent in his own code |

Sources 1 and 2 are a direct ruling on this project. Sources 3 and 4 are the course material and
reference code that the ruling is consistent with. Source 4 was cited by the team and was not
re-inspected for this ADR; it agrees with what the 8 September comparison found in the other
reference repository, which placed its repositories in `persistence/repository/` (iteration 2) and
`repository/` (iteration 1).

Taken together, sources 1 and 2 decide the question. Source 1 names the layer and permits the
sub-package; source 2 places persistence inside infrastructure. That fixes both the layer and the
package: `infrastructure.repository`.

## Decision

**Repository interfaces are declared in `com.freshflow.<service>.infrastructure.repository`.**

| Interface | Before | After |
|---|---|---|
| `StockBatchRepository` | `com.freshflow.inventory.domain` | `com.freshflow.inventory.infrastructure.repository` |
| `PriceRuleRepository` | `com.freshflow.pricing.domain` | `com.freshflow.pricing.infrastructure.repository` |
| `MarkdownPlanRepository` (spec-03 §3, not yet built) | would have been `com.freshflow.pricing.domain` | `com.freshflow.pricing.infrastructure.repository`, when built |

The sub-package name is **singular**, `repository`, following the wording of source 1. Source 4
uses `repositories`; the ruling addressed to this project takes precedence over a reference
repository.

What does **not** move or change:

- **The interfaces' names and method signatures.** They still speak only the domain's language —
  `StockBatch`, `BatchId`, `Sku`, `PriceRule` — and say nothing about how anything is stored.
- **The JPA adapters.** `JpaStockBatchRepository` and `JpaPriceRuleRepository` stay in
  `infrastructure`, next to the Spring Data interfaces, the JPA entities and the package-private
  mappers they use.
- **The other outbound ports.** `DomainEventPublisher` and `PriceRuleEventPublisher` stay in
  `domain`. The ruling is about repositories; it says nothing about messaging ports, and this ADR
  does not stretch it to cover them.
- **The domain rule.** The `domain` package still imports nothing — not `presentation`,
  `application` or `infrastructure`, not Spring, JPA, Jackson or Kafka.

## Rationale

**1. The subject coordinator has ruled, directly and twice.** CLAUDE.md makes the specifications
authoritative over the team's own judgement, and the coordinator is the authority over the
specifications. When his email and consultation answer the exact question we put to him, the
ruling is the decision. The team's earlier preference for `domain` rested on a convention, not on
a requirement of the course, and it gives way.

**2. It matches the course's own four-layer vocabulary.** spec-01 §2.1 already maps the course's
Data Access layer onto our `infrastructure` — the "wider" row added in the reference adoption of
10 September. The repositories now sit in the package that mapping names as their layer, so the
mapping and the code agree.

**3. It closes an open item cheaply and without changing behaviour.** Two files move, seven
imports change, no signature changes, no test changes. The same 64 tests pass before and after.

## Consequences

**Positive**

- A reader calibrated on the coordinator's lectures and reference code will find the repositories
  where that material says they belong.
- The `domain` packages of `inventory-service` and `pricing-service` now contain only the model —
  aggregates, entities, value objects, domain events, domain services — plus the two event
  publisher ports.
- The question the 8 and 10 September logs left open is settled, with the settlement written down
  here rather than argued in the report.

**Negative / accepted trade-off — dependency inversion, before and after**

Before, persistence was a textbook application of the Dependency Inversion Principle:

```
application ──► domain.StockBatchRepository ◄── infrastructure.JpaStockBatchRepository
                (interface, owned by the inner layer; implemented by the outer)
```

Every arrow pointed inward. The layer that needed persistence owned the abstraction, and the layer
that provided it adapted to it.

After:

```
application ──► infrastructure.repository.StockBatchRepository ◄── infrastructure.JpaStockBatchRepository
                (interface, owned by the outer layer)
```

**The application layer now depends on infrastructure for persistence access.** Four application
classes import `infrastructure.repository`: `BatchApplicationService`, `ApplyItemSoldHandler`,
`PriceApplicationService` and `PriceRulePublicationService`. This is the cost of the decision,
and it is accepted knowingly. Specifically:

- **What survives.** The application layer still depends on an *interface*, never on JPA. It
  imports nothing from `jakarta.persistence` or Spring Data, and the adapter behind the interface
  can still be substituted without changing application code. The *substitution* benefit of the
  port is intact.
- **What is lost is ownership.** The abstraction no longer belongs to the layer that needs it. The
  statement "outer layers depend on inner layers", which spec-01 §2.2 makes a HARD CONSTRAINT, no
  longer holds for persistence. A change to the `infrastructure` package can now, in principle,
  force a change inward to `application`, which the previous arrangement ruled out.
- **A compile-time guard becomes a review-time guard.** While the interfaces sat in `domain`, the
  domain's import ban made it impossible for a repository signature to mention a persistence type.
  `domain` could not import `StockBatchJpaEntity`, so no method could return one. In
  `infrastructure.repository` that ban no longer applies. `StockBatchJpaEntity` and
  `PriceRuleJpaEntity` are `public`, so the compiler would accept a signature that leaks them. The
  rule is now enforced by the grep in **Verification**, check 3, and by review.
- **A mechanical check is narrowed.** ADR 0002 claimed "no `application` package imports
  `infrastructure`" as a one-line check. It is replaced by a narrower check: `application` may
  import from `infrastructure` **only** the `infrastructure.repository` sub-package (check 2
  below). A narrower check admits more than the old one did, and it has to be stated precisely to
  mean anything.
- **Two kinds of outbound port now live in two layers.** In `inventory-service` and
  `pricing-service`, persistence ports are in `infrastructure.repository`, while the messaging
  ports `DomainEventPublisher` and `PriceRuleEventPublisher` stay in `domain`. That asymmetry
  follows from the scope of the ruling, which is about repositories. It is recorded here so that
  no one "tidies" it in either direction without a ruling of their own.

**Explicitly NOT affected**

- **`sales-service`.** Its persistence port, `SaleTransactionRecorder`, is declared in
  `application` under ADR 0002 Decision 1. That decision defines it as a permitted Transaction
  Script port and **not** a repository abstraction. Nothing here moves it.
- **`insight-service`.** It has no `domain` package. Its stores (`AgentTraceStore`,
  `VelocityWindowStore`, `VelocityBaselineStore`, `RiskViewStore`) are CQRS read-side ports in
  `application` under ADR 0002 Decision 2, ADR 0004 and team ruling R3. They are unchanged, and
  ADR 0004's check 4 — no `insight` application type imports `infrastructure` — still holds.
- **Invariant placement.** Business invariants, the margin floor among them, stay in the domain
  layer (spec-01 §2.5). Moving an interface that has no behaviour changes no rule about where
  behaviour lives.
- **The Domain Model pattern** of `inventory-service` and `pricing-service` (spec-01 §2.3). Domain
  classes remain persistence-ignorant POJOs, with separate JPA entities and mappers in
  infrastructure.

## Compliance

Changed in this decision:

- The two interfaces, moved with `git mv` so that history follows them; their package declarations
  and imports only.
- The seven files that imported them: one import line each.
- CLAUDE.md, "Layering": the repository sentence replaced, and the rest of the rule kept.
- spec-03 §1 and §3: the two "Repository interface — declared in `domain`" headings corrected, each
  with a dated note of what they previously read.

**Stale wording, and its status.** Each item below stated that repository interfaces are in
`domain` after the move. Updating them was outside the 29 September task's scope; they were
listed here so the drift was on record. **Resolved 4 October 2026** where marked:

| Where | What it said | Status |
|---|---|---|
| spec-01 §2.2 | "Repository interfaces are declared in `domain`" and the dependency arrows | Resolved: sentence replaced with a dated amendment, a third arrow `application → infrastructure.repository` added |
| spec-01 §2.4 | Repository row: "(interfaces in `domain`)"; the ports table row for Domain Model services | Resolved |
| spec-01 §1.3 | "repository interfaces remain in `domain` per §2.2, unchanged" | Resolved with a dated note |
| spec-01 §2.1 | The package tree listed repository interfaces under `domain` | Resolved: `infrastructure.repository` added to the tree |
| `specs/dev-agent-prompt.md` | "Repository interfaces are declared in `domain`; JPA implementations live in …" | Resolved |
| ADR 0001, ADR 0002 | The "Explicitly NOT affected" clauses and ADR 0002's Consequences | Resolved with italic superseded notes, the ADRs otherwise left as written |
| `inventory` and `pricing` `domain/package-info.java` | Lists "repository interfaces" among the domain's contents | Resolved 4 October 2026, comments only |
| `inventory` and `pricing` `infrastructure/package-info.java` | "implements the interfaces the domain declares" | Resolved 4 October 2026, comments only |
| Javadoc of `JpaStockBatchRepository`, `JpaPriceRuleRepository`, `StockBatchJpaRepository`, `PriceRuleSeeder`, `PriceRuleEventPublisher` | "declared in `domain`" / "the domain's `…Repository`" | Resolved 4 October 2026, comments only |
| CLAUDE.md, "Layering" first line | `presentation -> application -> domain <- infrastructure`, kept by instruction; the sentence below it records the exception | Accepted as is |

## Verification

From the repository root, each of these can be checked mechanically:

1. **The domain still imports nothing outward.**
   `grep -rnE "^import (com\.freshflow\.[a-z]+\.(presentation|application|infrastructure)|org\.springframework|jakarta|com\.fasterxml|org\.apache\.kafka)" inventory-service/src/main/java/com/freshflow/inventory/domain pricing-service/src/main/java/com/freshflow/pricing/domain`
   must return nothing.
2. **`application` reaches into `infrastructure` only through `infrastructure.repository`.**
   `grep -rnE "^import com\.freshflow\.[a-z]+\.infrastructure\." --include=*.java */src/main/java/com/freshflow/*/application | grep -v "\.infrastructure\.repository\."`
   must return nothing.
3. **The repository interfaces speak only the domain's language.** Every import in
   `*/infrastructure/repository/*.java` is from `com.freshflow.<service>.domain` or `java.*`:
   `grep -rhE "^import " */src/main/java/com/freshflow/*/infrastructure/repository | grep -vE "^import (com\.freshflow\.[a-z]+\.domain\.|java\.)"`
   must return nothing.
4. **No repository interface is left in `domain`.**
   `grep -rlE "interface [A-Za-z]+Repository" */src/main/java/com/freshflow/*/domain`
   must return nothing.
5. **Behaviour is unchanged.** `.\mvnw.cmd -o test` runs 64 tests with 0 failures and 0 errors,
   the same count as before the move.
