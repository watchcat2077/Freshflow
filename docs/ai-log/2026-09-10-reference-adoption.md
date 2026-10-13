# Reference adoption — three specification changes from the reference study

Date: 10 September 2026
Type: specification and documentation only. **No Java source was changed.**
Input: `docs/ai-log/2026-09-08-reference-study.md`, read but not re-run.
Authorised: the three items below, in the order given, plus a report on what else is worth
adopting.

## Team rulings this work was carried out under

Settled before this session, and treated as closed:

- `application` and `infrastructure` are kept. No package renaming.
- Domain classes stay persistence-ignorant POJOs. No `@Entity` on a domain type.

Both are trade-offs to be argued in the report, not defects to fix. Nothing below reopens
either. The layer-mapping table in item 3 is the mitigation the comparison report recommended
for the first of them — a mapping, not a rename.

---

## 1. What I did

### Item 1 — `specs/spec-05-stream-processing.md` (new, 491 lines)

A per-concept specification document, in the shape of the coordinator's
`5-event-sourcing.md` and `6-cqrs-command-query.md`: one architectural concept, mostly tables,
with the design rationale stated once rather than scattered.

It is **descriptive**. Every clause describes what is already built and tested at Week 7
part 2. Nothing in it is new design, and each rule cites the clause it comes from, which
remains authoritative.

| § | Covers |
|---|---|
| 1 | The topology — one curried function, four inputs, one output; the binding table; a stage diagram; why FR12 and FR13 share a function; the explicit re-keying by SKU |
| 2 | Windowing — the four configured parameters; **which window construct is actually built**; event time and `OccurredAtTimestampExtractor`; the read-side window-selection rule; suppression |
| 3 | The FR13 `KStream`-`KTable` join — why the table is keyed by SKU and not by batch; how the two source topics fold together; the assessment arithmetic; detection-versus-standing-state and why `reduce` rather than `aggregate` |
| 4 | The four state stores; Interactive Query as the CQRS read side; the port-per-store table; query shapes; why an unavailable store is not an error; why the record cache is disabled |
| 5 | Internal topics — spec-01 §2.6 Table B; why `velocity-baseline-published` has to exist at all; the Kafka-generated changelog and repartition topics, which belong to neither table |
| 6 | Serialisation, and the two-`ObjectMapper` split that caused G-6 |
| 7 | Known limitations G-3, G-4, G-6, each with status and intended fix |
| 8 | Traceability — requirement to section to test |

`CLAUDE.md`'s authoritative-sources list and spec-01 §2.6 both now point at it.

### Item 2 — requirements-to-tactics table in `specs/spec-02-requirements.md`

A new `## Requirements to Tactics` section before the existing Traceability Summary, modelled
on the coordinator's story-to-command/query/aggregate table in `6-cqrs-command-query.md` §5.

One row per requirement, FR1 to FR15, grouped by bounded context, with columns: command or
query, layer entry point, aggregate or read model, events produced, events consumed, and a
built/partial/not-built status. Prose between the four tables draws out the three things the
table exists to make visible:

- which requirements are commands and which are queries, and why the Insight read side is not
  merely a query over a write model;
- which requirements touch an aggregate and which cannot, because spec-01 §2.3 assigns a
  different domain-layer pattern per context — the column is empty of aggregate roots for
  `sales-service` by design, and names a read model for `insight-service`;
- where the event-driven seams are, which line up with the context map in spec-01 §1.5.

This is the bridge between report sections 2.1 and 3.3 the task asked for. Two rows carry
argument rather than fact and are called out as such: FR6a has no command of its own because
it is the pricing step *inside* FR5's transaction script, and FR11 has no command of its own
because it **is** the invariant FR9's command must satisfy — which is the point of the
requirement and of the Anti-Corruption Layer.

### Item 3 — package-to-layer mapping in `specs/spec-01-architecture.md` §2.1

A four-row table mapping the course specification's Presentation / Service / Domain / Data
Access onto our `presentation` / `application` / `domain` / `infrastructure`, plus one sentence
on why the names were kept.

Three of the four correspond exactly. The fourth is marked **wider**, not different:
`infrastructure` holds the repository implementations the course's Data Access layer names, and
also the Kafka bindings, serdes, adapters, mappers and configuration. That is the honest
statement of the relationship, and it is also the reason for keeping the name.

### Verification

`./mvnw -o test` — **BUILD SUCCESS, 41 tests, 0 failures, 0 errors, 0 skipped.**

| Module | Tests |
|---|---|
| inventory-service | 17 (`FefoDeductionPolicyTest` 5, `ItemSoldConsumptionTest` 7, `BatchApiIntegrationTest` 5) |
| sales-service | 9 (`PriceRulePublishedSnapshotFeedTest` 6, `TransactionApiIntegrationTest` 3) |
| pricing-service | 2 (`PriceApiIntegrationTest`) |
| insight-service | 13 (`RiskAssessmentPolicyTest` 7, `RiskApiIntegrationTest` 2, `VelocityApiIntegrationTest` 4) |
| **Total** | **41** |

No test was added, removed, weakened or re-run under different conditions. The count and the
pass/fail state are identical to before this session, as they must be for a documentation-only
change.

---

## 2. What I assumed that the specifications did not state

1. **That spec-05 is authoritative on the same footing as spec-01 to spec-04.** Nothing said
   where a fifth specification sits in the hierarchy. It is written as authoritative but
   subordinate: every clause cites its origin, and where it restates a rule from spec-01 or
   spec-02, that document still governs. `CLAUDE.md` was amended to list it, because a
   specification no one is told to read is not a specification.
2. **That "the three known limitations G-3, G-4 and G-6" are the ones carried in spec-02 FR13
   and spec-01 §2.6.** G-3 is the undefined trading day, G-4 is the no-sales-no-reassessment
   limitation, G-6 is the unenforced `occurredAt` wire format. There is an earlier, unrelated
   G-4 in the Week 7 *part 1* findings (window selection), which was closed by team ruling 2
   and is not what §7 describes. The numbering collision is noted here because it will confuse
   someone reading both logs.
3. **That the built/partial/not-built column belongs in spec-02 at all.** A specification
   states what must be true, not what currently is. It is included because the table's value in
   the report is partly as evidence of what was delivered, and it is explicitly labelled a
   status column that does not govern — the `[MVP-CORE]`/`[MVP-PLUS]`/`[POST-MVP]` tags still
   do. If the team disagrees, deleting the column costs nothing.
4. **That FR4 counts as partially built.** `GET /api/batches/{batchId}` exists and is tested;
   FR4 also asks for a SKU-level query and remaining shelf life, which do not exist. Called ◐
   rather than ✅ or ✗.
5. **That FR11's status is "value object built, rejection path not".** `MarginFloor` exists as
   a domain value object on `PriceRule`; `MarginFloorPolicy` (spec-01 §2.4) and `MarkdownPlan`
   do not exist yet, so the rejection path arrives with FR9.

---

## 3. Deviations from the specifications

None. No Java source, resource or configuration file was modified. The three edits are
additive: one new specification document, one new section in spec-02, one new table in
spec-01 §2.1. No existing clause was deleted or reworded.

Two cross-references were added beyond the three authorised items, both one-liners, both a
direct consequence of item 1 and neither changing any rule:

- `CLAUDE.md` authoritative-sources list now includes spec-05.
- spec-01 §2.6 now points at spec-05 for the topology detail.

---

## 4. Where I judged a specification wrong — flagged, NOT acted on

### spec-02 FR12 says "sliding window"; what is built is a hopping window

FR12's acceptance criteria say "aggregates quantity over a **sliding window**". The
implementation is `TimeWindows.ofSizeAndGrace(60s, 5s).advanceBy(5s)` — in Kafka Streams'
vocabulary a **hopping** window. Kafka Streams reserves `SlidingWindows` for a different
construct: windows defined relative to record timestamps rather than to a fixed epoch-aligned
grid.

This is a vocabulary collision, not a defect. What is built is what the general
stream-processing literature calls a sliding window, and it satisfies every criterion FR12
actually states — overlapping fixed-size windows, on event time, advancing faster than the
window size. Recorded in spec-05 §2.2 and again in §7 under "Also flagged".

**Recommendation, for a team ruling and not acted on here**: make FR12's wording Kafka-specific
("a hopping window — fixed size, advancing by a shorter interval"). The cost of leaving it is
that a reviewer who holds Kafka's narrower definition reads a mismatch between the requirement
and the code where there is none. The cost of changing it is one line and a note that the
requirement's substance is unchanged.

### spec-01 §2.4 lists a Domain Service that does not exist

§2.4's DDD building-blocks table lists `MarginFloorPolicy` as the project's one Domain Service.
No such class exists; `FefoDeductionPolicy` (inventory) and `RiskAssessmentPolicy` (insight,
`application` layer since that context has no domain layer) do exist and are not listed. The
table has been out of date since FR2 landed.

Not acted on: §2.4 was not in scope for this session, and correcting it is a two-line edit
someone should make deliberately rather than as a side effect of a documentation task. Flagged
here so it is not discovered by a reviewer first.

---

## 5. What else in the reference is worth adopting

Excluded by the task: anything that is a naming or a persistence question. Excluded by me:
anything over roughly two hours. Ranked by value per hour.

### 5.1 Tag the pre-implementation commit — 5 minutes, do it now

The reference README states the teaching method plainly: "review and explain progress using a
**before/after branch comparison**", and the repository ships `spec-only` and
`post-implementation` as a branch pair. Our evidence is eleven `docs/ai-log/` entries and three
ADRs on a single `main`. That is *more* evidence and better evidence, but it is not in the
shape that method produces, and it cannot be inspected with one `git diff`.

`git tag specs-complete <the Phase 2 commit>` gives us the same before/after for the price of a
tag. It changes nothing, breaks nothing, and makes our evolution inspectable by the exact
gesture that method expects. This is the cheapest single item on the list.

The one judgement call is which commit to tag — the end of Phase 2, where the specs were
complete and the skeleton existed but no requirement was implemented, is the honest choice.
Worth confirming by eye before tagging, because the tag is the claim.

### 5.2 An ADR recording our position on event sourcing — 45 minutes

The comparison report's finding 6: `iter2-impl` event-sources one aggregate and devotes a
document to it; we event-source nothing. L5 is titled "Event-sourcing, CQRS and bounded context
integration", and we can currently evidence one half of that title.

Building event sourcing on the strength of a comparison is not the recommendation — the
milestone is FR2/FR13 and the agent is Week 9. The recommendation is that the *absence* be a
recorded decision rather than a silence. An ADR 0004 stating which half of L5 we implemented,
why our Kafka Streams read side is a legitimate and arguably more advanced CQRS read side, and
what event sourcing would have cost here, converts a gap into a defended position. It is pure
prose, needs no code, and it is the difference between "they did not do it" and "they decided
not to, for these reasons".

### 5.3 The G-6 contract test — 90 minutes, needs Java

Already specified as the intended fix in spec-01 §2.6 and now in spec-05 §7, already scheduled
for Week 8, and worth pulling forward if there is an hour spare. A test over the `contracts`
module that serialises one instance of every event and asserts the wire shape — `eventId` a
UUID string, `occurredAt` an ISO-8601 instant. No broker, no Spring context, milliseconds to
run.

Two reasons it earns its place beyond fixing the bug: it is the only test in the project that
would have caught the defect that actually shipped, and it is a **contract test on the
Published Language** — precisely the artefact L6's bounded-context integration material asks
for, and something the reference has no counterpart to. It is out of scope for this session
because of the no-Java-changes instruction, not because of its cost.

### 5.4 An "Evolution from" preamble on each specification — 30 minutes

Each reference v2 document opens with an explicit list of what changed from iteration 1. The
reference versions specs by folder; we version in place and let git history and the ADRs carry the
evolution. Ours makes the *current truth* unambiguous, which is the more important property and
should not change — but it makes the *delta* invisible without reading git log.

A short "Changes since v1.0" block at the head of spec-01 and spec-02, listing the dated
rulings already recorded inline throughout them, costs half an hour and gives a reader the delta
without the prose archaeology. The material already exists; it is currently distributed through
the body of each document as parenthetical dates.

### 5.5 A global `@RestControllerAdvice` — 90 minutes, needs Java, and genuinely contested

Every controller in the reference is backed by a `@RestControllerAdvice` mapping exception types to
status codes. We have none: our controllers thread an `ApplicationResult` through and map it to
a `ResponseEntity` explicitly.

I do not recommend adopting this uncritically, and it is listed last for that reason. Ours
keeps the error path explicit, keeps invariant violations out of the presentation layer, and is
testable without a Spring context; the reference's is idiomatic Spring and is less code. The real risk is
not that ours is worse — it is that the *absence* of `@ControllerAdvice` reads as ignorance
rather than as a choice. **A paragraph in the report costs ten minutes and removes that risk
entirely; the refactor costs ninety and gives up something.** Recommend the paragraph.

### Already-open items this session did not touch

Both are questions for the coordinator, both are in the comparison report's follow-ups, and
both remain unanswered:

1. The repository-placement question: the reference `dev-agent-v2.md` Phase 4 puts repositories in
   `domain` (our position at the time) and the reference `iter2-impl` code puts them in
   `persistence`. Four artefacts, three answers. A one-line ruling from the coordinator is worth more than any amount of
   argument in section 5 of the report.
2. Whether `spring-boot-starter-validation` may be added. The reference uses `@Valid`
   throughout; spec-01 §1.1 forbids it pending exactly this decision, and two of our DTOs carry
   Javadoc explaining the hand-rolled consequence.

---

## 6. Files changed

| File | Change |
|---|---|
| `specs/spec-05-stream-processing.md` | New — 491 lines |
| `specs/spec-02-requirements.md` | +104 lines — `## Requirements to Tactics` before the Traceability Summary |
| `specs/spec-01-architecture.md` | +22 lines — package-to-layer mapping in §2.1; one cross-reference line in §2.6 |
| `CLAUDE.md` | +2 lines — spec-05 in the authoritative-sources list |
| `docs/ai-log/2026-09-10-reference-adoption.md` | This log |

No file under any `src/` directory was touched.
