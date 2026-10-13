# Refactor — repository interfaces moved from `domain` to `infrastructure.repository`

| Field | Value |
|---|---|
| Date | 29 September 2026 |
| Milestone | Architecture correction, outside the FR14 phase sequence |
| Authorised by | Subject coordinator Dr Guoxin Su — email of 17 September 2026 and consultation of 22 September 2026 — carried into the project by the team's task instruction of 29 September 2026, which also authorised the change to CLAUDE.md |
| Tool / model | Claude Code (Opus 5.5) |
| Baseline | `6b60595` — 64 tests run, 64 pass |
| Scope | Two interfaces moved (package declaration and imports only), seven import lines, CLAUDE.md one sentence, spec-03 two headings, ADR 0005, this log. **No test file, no `pom.xml`, no `.yml`, no JPA adapter, no signature touched** |
| Result | 64 tests run, 64 pass, `BUILD SUCCESS` — with the LLM variables set and with them unset |

---

## 0. What this was

The team asked the coordinator where repository interfaces belong. The 8 September reference
study had found the course material and the reference repositories giving four different answers,
and the 10 September reference-adoption log carried that forward as an open item. The coordinator
answered, in writing and in person:
repositories belong to the persistence layer, and persistence is part of infrastructure. This
change applies that ruling to the two repositories that exist and records it as ADR 0005.

An earlier run of this task **stopped before making any change**, to flag a self-contradiction in
the instruction it had been given. The team accepted its correction and reissued the instruction in
the form this run carried out. The replacement CLAUDE.md sentence is now worded to "describe the
code as it will actually be". That run's own report is not in the repository. This log records the
halt on the team's account and does not try to reconstruct the exact wording of the contradiction
it flagged.

The instruction also said explicitly **not** to halt over the contradiction with CLAUDE.md's
existing "Repository interfaces are declared in domain" rule. That was the right call: the
contradiction was the thing being fixed, and the authority for fixing it is the coordinator's.

---

## 1. What I did

### 1.1 Established the facts before changing anything

- **Importers.** Seven main-source files imported the two interfaces, which matches the count given
  in the instruction:

  | Service | Layer | File |
  |---|---|---|
  | inventory | application | `BatchApplicationService`, `ApplyItemSoldHandler` |
  | inventory | infrastructure | `JpaStockBatchRepository` |
  | pricing | application | `PriceApplicationService`, `PriceRulePublicationService` |
  | pricing | infrastructure | `JpaPriceRuleRepository`, `PriceRuleSeeder` |

- **Tests.** No test file names either interface, and there are no wildcard imports of either
  `domain` package anywhere. Neither `inventory-service` nor `pricing-service` has a package-scanning
  layering test that the move could trip. `AntiCorruptionLayerGuardTest` scans `insight-service`
  only. So the move could be made without touching a test, as the instruction required.
- **Spring wiring.** Neither service uses `@EnableJpaRepositories`, `@EntityScan` or explicit
  `basePackages`, and neither interface extends Spring Data's `Repository`. Moving them cannot
  change what Spring Data scans or instantiates.
- **Line endings.** Every touched file is LF in the index and CRLF in the working tree
  (`core.autocrlf=true`). Every edit preserved that, so the diff is free of whole-file line-ending
  churn.

### 1.2 Moved the two interfaces

`git mv`, so that `git log --follow` keeps their history:

- `inventory/domain/StockBatchRepository.java` → `inventory/infrastructure/repository/StockBatchRepository.java`
- `pricing/domain/PriceRuleRepository.java` → `pricing/infrastructure/repository/PriceRuleRepository.java`

In each file the package declaration changed. Because the interfaces are no longer in the same
package as the domain types they name, each also gained explicit imports for them: `BatchId`, `Sku`
and `StockBatch` for inventory, `PriceRule` and `Sku` for pricing. Nothing else in either file
changed. Names, method signatures and Javadoc are as they were.

### 1.3 Updated the seven imports

One line per file. The old `…domain.XRepository` import was removed and
`…infrastructure.repository.XRepository` was added at the end of the file's `com.freshflow` import
block, so the block stays in alphabetical order, as it was. In `BatchApplicationService` and
`JpaStockBatchRepository` that position is the same line. In the other five the line moves down by
one place. No other line in any of the seven files changed.

### 1.4 CLAUDE.md

In "Hard constraints — Layering", the sentence "Repository interfaces are declared in domain; JPA
implementations live in infrastructure." is replaced with the instruction's text, word for word,
wrapped to the file's line width:

> Repository interfaces live in infrastructure.repository, per the subject coordinator's ruling
> (Dr Guoxin Su, 17 and 22 September 2026; L4 slide 34; ADR 0005). Their JPA implementations live
> in infrastructure.

The rest of the rule is unchanged, including "The domain package imports NOTHING".

### 1.5 spec-03

Both "Repository interface — declared in `domain`" headings — §1 (`StockBatchRepository`) and §3
(`MarkdownPlanRepository`) — now read "declared in `infrastructure.repository`". Each carries a
one-line dated note, "**Corrected 29 September 2026** (ADR 0005 …). This heading previously read
…". That is the form spec-03 already uses for its 26 August correction in §2, so the change is
visible in the document rather than only in `git log`.

### 1.6 ADR 0005

`docs/decisions/0005-repository-interfaces-in-the-persistence-layer.md` records:

- the four sources of authority, with the Chinese original of the 22 September consultation and
  the team's English rendering;
- the decision and what it does *not* move;
- that the application layer now depends on infrastructure for persistence access;
- the trade-off against the previous dependency-inversion arrangement, set out as what survives
  (substitution) and what is lost (ownership, a compile-time guard, and a one-line mechanical
  check);
- every stale reference left behind;
- four grep checks. I ran each of them against the finished tree, and each returns nothing. Each
  also has a positive control, to show it is not trivially empty.

### 1.7 Two process slips during this run, both corrected

Recorded because this log is the evidence base for how the AI tooling was used.

1. **A scripted edit truncated the seven importer files.** The first pass used `perl -i` with an
   explicit `print` after reading to end of file. At that point Perl had already switched output
   back to standard output, so the rewritten text went to the console and the seven files were left
   empty. I spotted it from the tool output before anything was compiled or staged. The files had
   no uncommitted changes of their own, so I restored them from the git index and redid the edit
   with an explicit read-then-write. The final diff (§1.3) is one line per file.
2. **An environment variable's value was echoed to the session transcript.** A check intended to
   report only *whether* `LLM_API_KEY` and `LLM_MODEL` were set used `${VAR:-no}`, which expands to
   the value when the variable is set. The value appears nowhere in the repository — not in this
   log, not in any file, not in the commit. The team was told so that it can decide whether to
   rotate the key.

---

## 2. What I assumed that the specs did not state

1. **That new imports in the moved interfaces fall within "package declarations and imports".**
   The instruction limits the diff to package declarations, imports and the listed documentation.
   An interface moved out of `domain` cannot compile without importing the domain types it names,
   so those imports are a necessary part of the move and not an extra change.
2. **That re-positioning an import line to keep the block sorted is "updating the import", not
   reformatting.** Every one of the seven files keeps its `com.freshflow` imports in alphabetical
   order. Replacing the line in place would have broken that order in five files, which would have
   been a visible change of its own.
3. **That the dated correction notes in spec-03 are part of "correct both".** spec-03 already
   records its corrections that way (§2, 26 August 2026). If the team would rather have only the
   headings changed, each note is two lines and can be removed on its own.
4. **That `MarkdownPlanRepository`, which is specified but not built, will be declared in
   `pricing`'s `infrastructure.repository` when it is built.** The instruction said to correct
   spec-03 in both places. That only makes sense if the ruling covers future repositories as well as
   existing ones, and ADR 0005 says so.
5. **That the ruling covers repositories only.** `DomainEventPublisher` and `PriceRuleEventPublisher`
   are outbound ports too, but neither the email nor the consultation mentions them. They stay in
   `domain`, and ADR 0005 records the resulting asymmetry as deliberate.

---

## 3. Deviation from the specs

**Deliberate and authorised:** spec-01 §2.2 — "Repository interfaces are declared in `domain`" —
and its dependency arrows (`presentation → application → domain`, `infrastructure → domain`) no
longer describe the code. Four application-layer classes now import `infrastructure.repository`.
The authority is the coordinator's ruling, and ADR 0005 records that it supersedes that sentence.
The spec-01 text itself is **not** amended, because the instruction explicitly excluded spec-01
from this task. Until it is amended, spec-01 and the code disagree on a HARD CONSTRAINT, and ADR
0005 is what resolves the disagreement.

No other deviation. No dependency was added, removed or upgraded. No test was touched. The `domain`
packages still import nothing (ADR 0005, Verification check 1).

---

## 4. Anywhere I judged something wrong — flagged, NOT acted on

1. **The follow-up list is longer than "spec-01 and dev-agent-prompt.md".** The instruction names
   those two as carrying the same stale wording. There are more, all listed in ADR 0005
   "Compliance":
   - spec-01 §2.4's Repository row;
   - ADR 0001 and ADR 0002 — their "Explicitly NOT affected" clauses, and ADR 0002's Consequences
     claim that "no `application` package imports `infrastructure`", which is now false for two
     services;
   - the `domain` and `infrastructure` `package-info.java` of both services;
   - the Javadoc of both moved interfaces, which still says "declared in `domain`";
   - the Javadoc of `JpaStockBatchRepository`, `JpaPriceRuleRepository` and
     `StockBatchJpaRepository`.

   The Javadoc on the two moved files is the most visible of these: a reader opening
   `infrastructure/repository/StockBatchRepository.java` sees a first sentence saying it is declared
   in `domain`. It was left alone only because the instruction limited the diff to package
   declarations and imports.

2. **CLAUDE.md's arrow line is now incomplete.** `Layering: presentation -> application -> domain
   <- infrastructure` was kept, as instructed. It does not show the new `application →
   infrastructure.repository` edge, so the line and the sentence under it now describe two
   different dependency graphs. A one-word annotation would reconcile them, such as
   `application -> infrastructure.repository (persistence only)`. That is the team's decision to
   make.

3. **One factual slip in the instruction, which does not change its conclusion.** It says the JPA
   adapters' "mappers and entities are package-private". The mappers (`StockBatchMapper`,
   `PriceRuleMapper`) are package-private. The entities (`StockBatchJpaEntity`, `PriceRuleJpaEntity`)
   are `public`. Moving the adapters would still need visibility changes, because they call the
   mappers, so the constraint was right. But the entities being public matters elsewhere: nothing at
   compile time now stops a repository interface from naming a JPA entity in a signature. ADR 0005
   records that and adds Verification check 3 to catch it.

4. **Source 4 was not independently checked.** `gxshub/cargo-tracker-0.1` is cited as the team gave
   it. The 8 September comparison inspected a different reference repository, the library service,
   which is consistent with the ruling. Nobody has recorded looking at cargo-tracker itself in this
   repository. The ruling does not depend on it, because sources 1 and 2 decide the question.

5. **Whether the ruling reaches `sales-service`'s `SaleTransactionRecorder`** is left as it was.
   ADR 0002 Decision 1 defines it as a Transaction Script port, **not** a repository abstraction, so
   the ruling as worded does not apply to it. If the coordinator meant "anything that persists", the
   question would reopen. It is worth one sentence the next time the team speaks to the coordinator.

---

## 5. Test results

`.\mvnw.cmd -o test`, run after every code change was final.

| Module | Tests run | Failures | Errors | Skipped |
|---|---|---|---|---|
| contracts | — | — | — | — |
| inventory-service | 17 | 0 | 0 | 0 |
| sales-service | 9 | 0 | 0 | 0 |
| pricing-service | 2 | 0 | 0 | 0 |
| insight-service | 36 | 0 | 0 | 0 |
| pos-simulator | — | — | — | — |
| **Total** | **64** | **0** | **0** | **0** |

`BUILD SUCCESS`, identical in both environments the 14 September log established as the standard:
`LLM_API_KEY` and `LLM_MODEL` **set**, and both **unset** (removed from that process's environment
only). 64 is the baseline count; the move added and removed no test. No test touched the network.

---

## 6. One line for the team

The code change was mechanical. The real change is to our dependency rule: until today, "no
`application` imports `infrastructure`" was a sentence you could check with grep. It is now a
sentence with an exception. ADR 0005 states the exception precisely enough to be checked with grep
too, and spec-01 should say the same thing before a reviewer reads it.
