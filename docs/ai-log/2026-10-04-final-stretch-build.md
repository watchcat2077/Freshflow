# 2026-10-04 — Final-stretch build: FR4, FR6b, FR8, FR9/FR11, FR15, diagrams, report draft

| Field | Value |
|---|---|
| Date | Sunday 4 October 2026, afternoon and evening (Week 10 starts the next day) |
| Authorised by | The team lead, 4 October 2026: complete the code for the specification's requirements so a live demo can be run; produce the UML diagrams with UMLet; write the use cases and the report draft; update the task allocation |
| Tool / model | Claude Code as the lead session, with four background sub-sessions: three in isolated git worktrees for code, one for diagrams |
| Baseline | `84f50bd`: 64 tests run, 64 pass |
| Result | **177 tests run, 177 pass**, `BUILD SUCCESS`, offline, with `LLM_API_KEY` and `LLM_MODEL` unset |

## 1. What was done

**Specifications first.** spec-04 gained `GET /api/batches?sku=`, `PUT /api/prices/{sku}`,
`GET /api/markdown-plans/{id}`, `POST` and `GET /api/insight/briefings`, the margin arithmetic
for `POST /api/markdown-plans`, and `markdownPlanId` on the 422 body. spec-03 closed the open
items B-3 (`MarginFloorPolicy` returns a `MarginFloorAssessment`) and B-6
(`MarkdownPlan.activate` takes the assessment), added `PERCENT_60`, and recorded the Insight
domain package. spec-02 gained acceptance criteria for FR4, FR8 and FR15 and ruled on residual
B-7 (FR6b does not need `markdown-expired`). spec-01 §2.6 Table A carries `discountedPrice` on
`markdown-applied`; §2.1 to §2.4 were brought in line with ADR 0005 and ADR 0006. ADR 0006 was
written. `contracts` gained `MarkdownAppliedEvent`. These were committed (`1e9015f`) before any
code session started, so the three worktrees branched from an agreed contract.

**Code, three worktree sessions, each test-first.**

| Session | Delivered | Tests before → after |
|---|---|---|
| pricing-service | FR8 upsert, FR9 and FR11 with `MarkdownPlan`, `MarginFloorPolicy`, `MarginFloorAssessment`, `PromotionWindow` and friends in `domain`; `MarkdownPlanRepository` in `infrastructure.repository`; the `markdown-recommended` consumer (idempotent on `agentTraceId`) and the `markdown-applied` producer; the price query now reports an active markdown | 2 → 54 |
| sales-service, inventory-service, pos-simulator | FR6b (`PriceSnapshot.priceAt(Instant)` in `domain`, the `markdown-applied` consumer behind the unchanged `PriceSnapshotFeed`); FR4 list endpoint in FEFO order; a JDK-only simulator with an `Options` record and a weighted `SkuMix` | 9 → 19; 17 → 21; 0 → 15 |
| insight-service | `com.freshflow.insight.domain` with `RiskAssessmentPolicy` and `BatchRiskView` moved by `git mv`, plus a dependency-rule test; FR15: `BriefingAgent` port, `BriefingToolRegistry` with three read-only tools, `BriefingOrchestrator` computing the counts from the stores, `EndOfDayBriefing` persisted with its tool calls, `LangChain4jBriefingAgent`, `BriefingAgentConfiguration` under the same two guards as FR14, `BriefingController` with 201/503/404 | 36 → 68 |

Each session fast-forwarded its branch onto `1e9015f` before starting, committed tests before
features, ran the suite through PowerShell (the repository path contains an ampersand, which
breaks `cmd.exe` when invoked from bash), and wrote its own log:
`2026-10-04-fr9-fr11-pricing.md`, `2026-10-04-fr6b-fr4-pos-simulator.md`,
`2026-10-04-fr15-briefing-and-insight-domain.md`. The three branches were merged with merge
commits and the suite re-run on `main` after each merge.

**Documents.** Twelve use cases (`docs/report/section-2-1-use-cases.md`, also exported to Word);
README rewritten in the format of the reference repositories with one sample input per use case, the
agent class map and troubleshooting; Postman collection; `scripts/demo.ps1`; IntelliJ `.run/`
configurations; the Week 12 presentation plan; the report draft `docs/report/FreshFlow-Report.md`
with sections 1 to 5 and references, leaving placeholders only for the live-run evidence and
the cover-page contribution; `task allocation.docx` in the parent folder rewritten; Week 9
minutes mirrored from the Moodle submission; STATUS and ROADMAP rewritten twice (morning plan,
evening state). The CLAUDE.md milestone section now describes this state.

**Diagrams.** A fourth session generated the UMLet diagrams (`.uxf` sources and `.png`) into
`docs/diagrams/` with copies in the parent folder `UML diagram`; the team's draw.io A2 diagram
was restored after an earlier removal and was kept at the time as the Section 3.1 architecture figure.

**Reference material.** Two further sessions produced a citation index of the nine lecture
decks (slide numbers verified against the extracted text) and an analysis of the
coordinator's five reference repositories; both live in the session scratchpad and their
conclusions are folded into the report (Sections 3.5, 3.6, 3.7, 5) and into ADR 0005's
sources table. The analysis recommended tagging the pre-implementation commit as the
coordinator's case study does; tags `specs-complete` and `mvp-core-complete` were added.

## 2. What was assumed that the specifications did not state

- The margin ratio is gross margin over selling price, rounded half-up to four places; the
  promotion window is half-open; two active plans on one SKU report the deeper discount; a
  newer price rule displaces an attached markdown in sales-service while an older one keeps it.
  Each is recorded in the respective session log and in spec-03 or spec-02.
- The business day for FR15 is the UTC calendar day; `recommendationCount` counts only traces
  whose outcome is a published tier.
- The twelve functional requirements presented in the report are FR1, FR2, FR4, FR5, FR6a and
  FR6b (one use case), FR8, FR9, FR11, FR12, FR13, FR14, FR15; FR3, FR7, FR10 and
  `markdown-expired` are out of scope and said to be.
- The governing specification is the `.docx` in `Group_Work_claude_code/`, per the team lead's
  instruction; the PDF in `Group project/` carries an additional note about generative AI that
  the `.docx` does not.

## 3. Deviations from the specifications, with justification

- spec-04's `MarginFloorRejection` gained `markdownPlanId` (additive). Without it the FR11
  criterion "recorded and queryable" had no way for the caller to find the record.
- `MarkdownPlanRepository` gained `findByAgentTraceId`, not in spec-03, so the consumer can be
  idempotent on the trace rather than on a Kafka offset.
- The simulator's `pom.xml` still declares `contracts`, which the simulator no longer imports;
  POM files were off limits to the sessions and it is harmless.

## 4. Where the specifications were judged wrong, not acted on

- spec-04's `PriceResponse.activeDiscountTier` enum and the `MarginFloorRejection` example were
  out of date; both corrected as documentation, no behaviour involved.
- spec-02's Requirements-to-Tactics table still lists `markdown-expired` against FR6b; left for
  the team to tidy with the other post-build spec reads.
- No unique index on `agent_trace_id` in `markdown_plan`; a concurrent double delivery across
  two instances is a theoretical race. Left for a ruling because the obvious fix turns a human
  re-POST into a 500.
- Docker Desktop did not start on the lead's machine (WSL distribution stuck in `Stopped`), so
  the live run is still the first open item; nothing in the code depends on it, and the
  procedure is in README and the roadmap.

## 5. Process notes

- Four sub-sessions were interrupted by an API usage limit mid-task; each had committed its
  failing tests first, so nothing was lost, and each resumed from its own transcript and
  finished. The habit of committing tests before implementation paid for itself here.
- Three worktrees branched from one specification commit meant no merge conflicts; the only
  shared files were specifications, and each session edited a different section.
