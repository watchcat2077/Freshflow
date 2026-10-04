# FR15 end-of-day briefing agent, and the Insight domain package (ADR 0006)

| Field | Value |
|---|---|
| Date | 4 October 2026 |
| Milestone | Two tasks in one session: (A) apply ADR 0006 — give `insight-service` a `domain` package; (B) build FR15, the end-of-day briefing agent, as a second agentic use case |
| Authorised by | Project team, 4 October 2026 task instruction, on the subject coordinator's ruling of 22 September 2026 (ADR 0006) and spec-02 FR15's acceptance criteria of the same date |
| Tool / model | Claude Code (Fable 5.1) |
| Baseline | `1e9015f` — 64 tests run, 64 pass (inventory 17, sales 9, pricing 2, insight 36) |
| Scope | `insight-service` only, plus spec-06 §11 and this log. **No `pom.xml`, no `application.yml`, no other module, no CLAUDE.md, README or spec-04 touched.** No dependency added. No live model call |
| Result | **96 tests run, 96 pass, `BUILD SUCCESS`** (inventory 17, sales 9, pricing 2, insight 68), verified with `LLM_API_KEY` and `LLM_MODEL` set and again with both removed from the environment |

Three commits on the task branch, in this order, none pushed:

| Commit | Message | Tests |
|---|---|---|
| `369ccc3` | `refactor(insight): move RiskAssessmentPolicy and BatchRiskView into a domain package` | insight-service 37 run, 37 pass (the other modules were not touched and were not rerun at this commit) |
| `4927d98` | `test(insight): FR15 briefing type surface and failing tests` | insight-service 68 run, 31 fail — every failure an `UnsupportedOperationException("FR15")` or a 404 from a missing route; the 37 existing tests pass |
| (this one) | `feat(insight): implement FR15, the end-of-day briefing agent` | whole build 96 run, 96 pass |

---

## 0. What this was

ADR 0006 records the coordinator's ruling that a CQRS context has a domain layer, and names the
two types that belong in it. Task A applied it. FR15 was specified the same day; Task B built it to
the same shape as FR14 — port in `application`, deterministic stub for every test, LangChain4j
adapter for runtime, bounded tool loop, persisted tool calls — plus the one thing FR14 does not
have and FR15's acceptance criteria require: a grounding step in the orchestrator, so that no number
in the response comes from the model.

The session was interrupted once by an API usage limit after the FR15 files had been written to
disk but before they were committed. The coordinator reported a stray editor temp file
(`BriefingOrchestratorTest.java.tmp.…`) in the test folder; on resumption no such file existed
(the editor's atomic write had completed) and a `find` over `insight-service/src` for non-source
files returned nothing. Nothing was lost and nothing was duplicated; the sequence below is the
sequence that was committed.

---

## 1. What I did

### 1.1 Task A — the domain package (commit `369ccc3`)

- `git mv` of `RiskAssessmentPolicy` and `BatchRiskView` from `application` to `domain`, so that
  `git log --follow` keeps their history. In each file the package declaration changed and nothing
  else. `BatchRiskView` imports `java.time.Instant`; the policy imports `java.time.Duration` and
  `java.time.Instant`. The package imports `java.*` only.
- Eleven import lines in nine files: `RiskController`, `InsightTopologyConfiguration` (two),
  `InteractiveQueryRiskViewStore`, `RiskAssessment`, `RiskPolicyConfiguration` (replaced in place,
  which keeps each import block sorted); `RiskQueryService` and `RiskViewStore` (an import added —
  they were in the same package as the moved types and now are not);
  `InsightTestVelocityConfiguration` (replaced) and `RiskAssessmentPolicyTest` (two added). With the
  two package declarations that is ADR 0006's "thirteen import lines".
- `domain/package-info.java`, modelled on `inventory` and `pricing`'s: the dependency rule stated
  as a HARD CONSTRAINT, and a paragraph on what is deliberately *not* in the package (the read
  models, the ports, the orchestration — R3's read-model half survives).
- `InsightDomainDependencyRuleTest`, in the style of `AntiCorruptionLayerGuardTest`: strips
  comments, then fails if any import under `src/main/java/com/freshflow/insight/domain` is outside
  `java.*` or the package itself, or if any file there declares another package. It also asserts the
  scan found the two moved files, so an empty directory cannot pass it vacuously. It is ADR 0006's
  verification check 1 as a test rather than a grep.
- The 36 existing insight tests passed unchanged apart from import lines; the new guard makes 37.
  The diff was 11 files, 14 insertions, 9 deletions, with no line-ending churn (the working tree is
  CRLF under `core.autocrlf=true`; the in-place edits preserved it).

### 1.2 Task B — FR15, `application` (commits `4927d98` and the feat commit)

| Type | Role |
|---|---|
| `BriefingRequest(LocalDate businessDate, Instant now)` | The agent's input. Owns the one piece of arithmetic FR15 has — a business date is a UTC day, `[businessDayStart, businessDayEnd)` — so the tool and the count read the same bounds |
| `BriefingAgent` | The port (ADR 0004, R1): `BriefingDeliberation brief(BriefingRequest, BriefingToolRegistry)` |
| `BriefingDeliberation(List<ToolCall> toolCalls, String summary)` | Evidence only, no effects. `ToolCall` reused from FR14 unchanged |
| `BriefingToolRegistry` | Exactly three read-only tools, `String` in and out, `error=…` for an unknown tool or a bad argument, `none` for an empty list. No `proposeMarkdown` exists on it |
| `EndOfDayBriefing` | spec-03 §4 and spec-04 `BriefingResponse`, field for field |
| `BriefingStore` | `save`, `findById` |
| `BriefingOrchestrator.brief(LocalDate)` | Defaults the date to today in UTC from the injected `Clock`, calls the agent, then computes `riskCount` and `recommendationCount` from `RiskQueryService` and `AgentTraceStore`, replaces a blank summary with a fixed sentence, mints `BRF-` + 8 upper-case hex, persists, returns |
| `BriefingQueryService` | `@Service`; `findById` for the GET. Exists whether or not a model is configured |
| `AgentTraceStore.findCompletedBetween(from, to)` | FR14's port gains the day-range read; `[from, to)`, ordered by `completedAt` then id |

Two changes to FR14 code, both visibility only: `AgentToolRegistry.render(SkuVelocityView)` became
package-private `renderVelocity`, so the briefing's `getSellThroughRate` answers with the same
text as FR14's (one rendering, two agents); and `MarkdownToolArguments.parse`/`text` became
package-private so `BriefingToolArguments` shares one JSON parser and its "nothing throws"
guarantee. No FR14 behaviour changed; every FR14 test passes as before.

### 1.3 `infrastructure`

- `BriefingEntity` (table `briefing`: `briefing_id` PK, `business_date` DATE, `generated_at`,
  `summary` length 4000, `risk_count`, `recommendation_count`) and `BriefingToolCallEntity`
  (table `briefing_tool_call`: generated `id`, `briefing_id` FK, `call_sequence`, `tool_name` 64,
  `arguments` 2000, `result` 4000, `called_at`), `@OrderBy("sequence ASC")` on the collection;
  `BriefingJpaRepository`, `BriefingMapper`, `JpaBriefingStore` — the shape of FR14's trace
  persistence exactly. `AgentTraceJpaRepository` gains the derived range query.
- `LangChain4jBriefingAgent`: the same bounded loop as `LangChain4jMarkdownDecisionAgent` over the
  same `ChatModel` bean, with its own system prompt (reproduced in §1.7) and three tool
  specifications built from the registry's names. The closing answer is the summary; if the loop
  ends at `max-steps` without one, the summary is blank and the orchestrator substitutes its
  sentence. The loop was not extracted into a shared class: FR14's reads a proposal out of an
  accepted tool call, FR15's reads a closing narrative, and a loop abstract enough for both would
  be harder to review than two loops of forty lines each.
- `BriefingToolArguments`: JSON → the registry's flat encoding, pinned by its own test.
- `BriefingAgentConfiguration`: `@Profile("!test")` outer class declaring `briefingToolRegistry`;
  nested `@Profile("!test") @ConditionalOnExpression(api-key non-empty)` class declaring
  `briefingAgent` and `briefingOrchestrator`, over FR14's `ChatModel` bean and
  `freshflow.llm.max-steps`. No new property; `application.yml` is untouched.

### 1.4 `presentation`

`BriefingController` at `/api/insight/briefings`: `POST` (body optional, `businessDate` optional)
→ **201** with spec-04's `BriefingResponse` and a `Location` header, or **503** with
`ErrorResponse {error: "AGENT_NOT_CONFIGURED", message}` when `ObjectProvider<BriefingOrchestrator>`
has nothing to give — the FR14 trigger consumer's device; `GET /{briefingId}` → **200** or **404**
from a real handler via `BriefingQueryService`. `BriefingRequestDto`, `BriefingResponseDto` (with
spec-04's inline `ToolCall`), and `ErrorResponseDto` as `inventory` and `sales` have it.

### 1.5 Tests (31 new; insight 37 → 68)

| Test | Count | What it pins |
|---|---|---|
| `BriefingToolRegistryTest` | 11 | Exactly three read-only tools and no `proposeMarkdown`; each tool's rendering; `none`; both UTC-day boundaries; bad date, blank SKU, unknown tool |
| `BriefingOrchestratorTest` | 6 | Counts from the stores while the stub's text says ninety-nine and forty; default date from the clock; explicit date and boundaries; blank summary → fixed sentence; persisted with ordered calls; distinct ids |
| `BriefingToolArgumentsTest` | 5 | The encoding, fed to a real registry |
| `JpaBriefingStoreTest` | 2 | Round trip with calls handed in as 3, 1, 2; unknown id empty |
| `JpaAgentTraceStoreDayRangeTest` | 1 | The derived query's inclusivity and order against H2 |
| `BriefingApiIntegrationTest` | 4 + 2 | spec-04 field by field; today-in-UTC default; GET after POST; 404 with the `HandlerMethod` guard; nested context with the stub switched off → 503 and GET still served |
| `InsightDomainDependencyRuleTest` (Task A) | 1 | The domain package imports `java.*` only |

Test-side types: `StubBriefingAgent` (calls all three tools through the real registry, then writes
a summary whose numbers are wrong on purpose; a `silent` mode writes nothing), `InMemoryBriefingStore`,
`BriefingTestFixtures` (five FR14 traces across three days, including one at the last second of the
day before and one at the first instant of the day after), and `InsightTestBriefingConfiguration`
(`@Profile("test")`, switched off by `freshflow.insight.briefing.stub-agent=false`, beans named
`testBriefingOrchestrator` and friends so that the profile guard's assertion is about the production
names). `MarkdownAgentTestProfileGuardTest.LLM_BACKED_BEANS` gained `briefingAgent` and
`briefingOrchestrator`. `AntiCorruptionLayerGuardTest` is unchanged and green over every new source,
the prompt included.

### 1.6 Documentation

- spec-06: status line amended; a dated note under §0's scope sentence; new **§11** describing
  FR15 — type surface, the three tool contracts, the deterministic counts, the record, the 503
  behaviour, what FR15 does not do, traceability.
- spec-03 §4 was not changed: every name it gives `EndOfDayBriefing` is the name in the code.
- The `application` and `infrastructure` `package-info.java` files no longer say the service has
  no `domain` package; each carries a dated note of what it previously read.

### 1.7 The system prompt, verbatim

```
You write the end-of-day briefing for the owner of a small grocery store. The store
sells perishable stock in batches. During the day an automated markdown agent examines
batches that are not selling fast enough to clear before they expire and records, for
each one, whether it recommended a markdown and why. Your briefing summarises that day
for the owner.

Gather the evidence first, then write. Work in this order:
1. Call listCurrentRisks to see which batches are still at risk of expiring unsold
   right now.
2. Call listRecommendations with the business date you were given to see which
   batches the markdown agent examined that day, what it recommended for each, and
   why. A line whose finalRecommendation is NO_MARKDOWN was examined and left alone;
   one that reads PROPOSED_BUT_UNRESOLVABLE could not be acted on.
3. Optionally call getSellThroughRate for any SKU you want to say something specific
   about. If the rate is reported as unknown, that means no measurement is
   available, NOT that nothing is selling.
4. Then write the briefing.

The briefing is four to eight sentences of plain language, with no headings, no
bullet points and no markdown formatting. Cover, in this order: what was recommended
for markdown today and why; what is selling through; and what is still at risk and
may need attention tomorrow. Name the batches and cite the numbers you actually saw,
and never invent a figure the tools did not give you. If a tool answers none, there
is nothing in that category, and say so plainly.

State plainly that write-off totals are not available in this briefing, because
write-offs are not recorded by the system yet.

If a tool answers with text beginning error= then read it, correct your call and try
again.

You recommend nothing and change nothing. You are summarising decisions that have
already been made and recorded.
```

### 1.8 Two process notes

1. A `perl -p` edit meant to drop an old import line used `next`, which in a `-p` loop still
   prints the line. Three files kept the old import beside the new one; the compiler caught it on
   the first run and a `perl -n` pass removed the three lines. The committed diff is one line per
   file, as intended.
2. The session's shell has `LLM_API_KEY` and `LLM_MODEL` set. The suite was therefore run twice at
   the end: once as found, and once with both removed from that process's environment. Both runs:
   96 run, 96 pass. No value was echoed anywhere.

---

## 2. What I assumed that the specs did not state

1. **`recommendationCount` counts published recommendations, not every trace of the day.**
   spec-04 says "Markdown recommendations made on businessDate"; spec-06 §6 defines three outcome
   values, two of which record that nothing was recommended. The tool lists all three kinds (a store
   owner wants to know what was looked at and left alone); the count counts the published form,
   `<TIER> from … until …`, recognised by the four `DiscountTier` names so a fourth outcome value
   would count as "not a recommendation". Pinned by `BriefingOrchestratorTest` and spec-06 §11.3.
2. **A business date is a UTC calendar day, `[00:00, 24:00)`.** spec-04 says "today in UTC" for
   the default; the half-open range is the only reading under which a trace belongs to exactly one
   day. Both boundaries are tested on both sides.
3. **`listCurrentRisks` ignores any argument** rather than rejecting one; a tool that fails on a
   harmless extra argument costs a model a round trip for nothing.
4. **The empty-list answer is `none`**, a successful answer, not `error=`. "Nothing is at risk" is
   information, as `RiskController`'s `[]` is.
5. **The identifier format `BRF-` + 8 upper-case hex** is ours; spec-04 types `briefingId` as a
   string and shows no example. Asserted by regex, since it is random.
6. **A POST is a new briefing every time.** FR15 has no trigger event and therefore no `eventId`
   to deduplicate on; spec-04 says "generated and persisted". Nothing was invented to make it
   idempotent.
7. **The 201 carries a `Location` header.** spec-04 does not mention one; it is the conventional
   companion of a 201 and costs nothing. Only its presence is asserted.
8. **FR15's `max-steps` is FR14's `freshflow.llm.max-steps`** (8). Two mandatory reads, an
   optional velocity read or two and the closing answer fit inside it; a separate property would be
   configuration nobody asked for.
9. **The test-profile switch** `freshflow.insight.briefing.stub-agent` is a test-only property that
   no main source reads; it exists so one `@Nested` context can run without an orchestrator bean.

---

## 3. Deviations from the specs, with justification

- **None from spec-04.** Paths, status codes, request and response shapes are as written.
- **spec-02 FR15 says the orchestrator computes the counts "from the tool results".** It computes
  them from the *services the tools read*, after the agent returns, not by parsing the tool result
  strings. That is the stronger reading of the same requirement — a count parsed out of text is a
  count that depends on the rendering — and it is what spec-04's field descriptions say ("counted
  by the orchestrator from the tool result" is satisfied by reading the same source). Recorded here
  in case the team meant the literal one.
- **FR14 code was touched in four places**, none behavioural: two method visibilities
  (§1.2), one new port method on `AgentTraceStore` with its two adapters, and two bean names added
  to `MarkdownAgentTestProfileGuardTest`. Every FR14 test is unchanged and green.
- **The moved classes' own Javadoc was left as it was**, per the instruction "nothing else about
  them". `RiskAssessmentPolicy`'s class comment still says "This service has no `domain` package
  … this is where such a rule belongs", which is now wrong in the very file it sits in. See §4.1.

---

## 4. Anywhere I judged something wrong or suboptimal — flagged, NOT acted on

1. **Stale text left behind by the move.** `RiskAssessmentPolicy`'s class Javadoc (above);
   `BatchRiskView`'s is fine; `AgentTraceRecord`'s `{@link BatchRiskView}` now points at a type
   in another package without an import (javac does not care; javadoc would warn);
   `AgentToolRegistry`'s Javadoc still says the registry's bean "will sit in `infrastructure`
   beside `RiskPolicyConfiguration`", which is true but describes Phase A. One-line fixes each,
   outside the instruction's scope.
2. **spec-01 still says the opposite of ADR 0006 in three places.** §2.3's pattern table
   ("`insight-service` | None (read side) | **No domain layer**"), §2.3's port-placement table
   ("No domain layer — there is no `domain` package to declare them in") and its closing
   paragraph ("the `domain` package is omitted entirely"), and §2.5's invariant table. §2.1 was
   amended on 4 October 2026; these were not. CLAUDE.md's "insight-service: no domain layer" line
   likewise. The ADR lists spec-01 §2.1 and §2.3 and CLAUDE.md as affected, so this is known drift
   rather than new; it is listed so it is not forgotten.
3. **`summary` is not clipped to its 4000-character column.** A narrative longer than that would
   fail the insert — the one way model text could still make the orchestrator throw. FR14's
   `rationale`, `arguments` and `result` carry the same exposure and it was accepted there; the
   prompt asks for four to eight sentences. If the team wants the guarantee absolute, the mapper is
   the place to clip, with the truncation recorded in the stored text.
4. **No live run.** As with FR14 before Phase B3, the LangChain4j adapter, the shared model bean
   under two nested configurations, and the 201 path through a real model have not been exercised.
   The B3 runbook's shape applies; FR15 needs a section of its own there. Two things to watch in
   that run: whether Gemini accepts a tool specification with no parameters (`listCurrentRisks`),
   and whether `max-steps` of 8 is enough when the model looks up several SKUs.
5. **`getSellThroughRate` is one name in two registries.** Deliberate — spec-02 FR15 names it so,
   and the rendering is shared by code — but a reader of a tool call in a stored record cannot
   tell from the name alone which agent made it. The record it belongs to says.
6. **Surefire's accounting for the nested context.** The report lists
   `BriefingApiIntegrationTest$WithoutAnAgent: 6` and `BriefingApiIntegrationTest: 0`; the four
   outer and two nested tests all ran and all passed, but the per-class line is misleading at a
   glance. A separate top-level class would report cleanly; the nested form was chosen because the
   brief suggested it and it keeps the 503 case beside the behaviour it contrasts with.
7. **ADR 0006's "its summarisation rules are in `domain` only where they are pure"** is satisfied
   vacuously: FR15's only pure rule is two lines of UTC arithmetic on `BriefingRequest`, and its
   counting rule reads an `application` type. A `BusinessDay` value object was considered and not
   built, because the brief gave a precise type list and the gain was two lines. spec-06 §11.1
   says so.

---

## 5. Test results

`.\mvnw.cmd -o test`, from the worktree root, after every change was final.

| Module | Before | After | Failures | Errors |
|---|---|---|---|---|
| contracts | — | — | — | — |
| inventory-service | 17 | 17 | 0 | 0 |
| sales-service | 9 | 9 | 0 | 0 |
| pricing-service | 2 | 2 | 0 | 0 |
| insight-service | 36 | **68** | 0 | 0 |
| pos-simulator | — | — | — | — |
| **Total** | **64** | **96** | **0** | **0** |

`BUILD SUCCESS` with `LLM_API_KEY` and `LLM_MODEL` set, and with both removed from the process
environment. No test touched the network; the only `BriefingAgent` in test sources is
`StubBriefingAgent`; `grep -r "import dev.langchain4j" insight-service/src/main/java/com/freshflow/insight/application/`
and the same over `src/test` return nothing; no `application` type imports `infrastructure`; the
`domain` package imports `java.*` only.

---

## 6. One line for the team

FR15 proves the FR14 design generalises: a second agent cost a port, a registry, an orchestrator
and a controller, reused the model bean, the guards, the trace persistence pattern and the tool
encoding idea, and needed one new rule — the counts are the service's, not the model's — which is
the rule the acceptance criteria asked for.
