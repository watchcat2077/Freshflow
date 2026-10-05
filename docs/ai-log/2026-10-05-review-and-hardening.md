# 2026-10-05 — Week 10 review: build verified, live run repeated, the model-down path hardened

| | |
|---|---|
| Session | Monday 5 October 2026, 12:40 to about 14:00, Week 10 |
| Driver | Andy, asking for a tutor-style review of the whole project against the specification, with every defect fixed and every change explained |
| Tool / model | Claude Code (Fable 5.1) driving PowerShell, Bash and Python; two read-only subagents (lecture-citation check; comparison with the coordinator's GitHub references); Google Gemini through LangChain4j for the live checks (`gemini-3.6-flash`, then `gemini-3.5-flash-lite`; key from the environment, never logged) |
| Inputs | The specification docx, the presentation guideline, `docs/STATUS-2026-10-04.md`, `docs/ROADMAP-2026-10-04.md`, the report draft, the use cases, the diagrams, Dr Su's email reply and consultation notes, the eight lecture decks, the three reference repositories on GitHub |
| Outcome | 187 tests green (was 177); the loop ran end to end a second time against Kafka and a live model; one real defect found and fixed test-first; one diagram replaced; the Week 12 deck produced; README, report, spec-04, ADR 0005 and the diagrams README corrected |

## 1. What was done

### Verification first

- `mvnw -o test` with `LLM_API_KEY` and `LLM_MODEL` unset: 177 run, 0 failures (inventory 21, sales 19,
  pricing 54, insight 68, pos-simulator 15), then `mvnw -o clean install -DskipTests`.
- Kafka from `docker-compose.yml` (healthy in 25 s); the four services started from their jars with
  `java -jar`; `scripts/demo.ps1` run end to end with a transcript. Steps 1 to 10 passed as on
  4 October: 201 and 400 for FR1, FEFO deduction, velocity and risk, 201 then 422 for FR9/FR11, the
  discounted scan with `markdownApplied: true`.
- **Step 11 failed and the FR14 consumer threw.** `POST /api/insight/briefings` answered 500, and
  insight-service logged three `MessageHandlingException` stacks for `sell-through-risk-detected`.
  Cause, from the stack: `dev.langchain4j.exception.RateLimitException`, Gemini 429,
  `GenerateRequestsPerDayPerProjectPerModel-FreeTier`, quota 20 requests a day for `gemini-3.6-flash`.
  The 4 October run plus the morning's deliberations had spent it.

### The defect, and the fix (test-first)

The provider's exception crossed the agent ports unchanged. The application layer had no word for
"the model is down", so the controller could only answer 500 and the consumer could only let the
binder print the stack.

Ten tests were written first and seen to fail (compile failure: the class did not exist), then:

| Layer | Change | Test |
|---|---|---|
| application | `AgentUnavailableException` (new): agent name, one-line bounded reason naming the provider exception class, `key=` values redacted, cause kept | `AgentUnavailableExceptionTest` (4) |
| application | The two orchestrators let it propagate and store nothing: an outage is not a decision and does not belong in the audit record (spec-06 §5) | `MarkdownAgentOrchestratorTest`, `BriefingOrchestratorTest` (1 each) |
| infrastructure | `LangChain4jMarkdownDecisionAgent` and `LangChain4jBriefingAgent` wrap the one model call in `ask(...)`, translating any `RuntimeException` from the provider into the application exception; tool failures stay `error=` answers as spec-06 §4 fixes | `LangChain4jAdapterUnavailableTest` (2, with a `ChatModel` that throws `RateLimitException` / `TimeoutException`) |
| presentation | `BriefingController` maps it to 503 `AGENT_UNAVAILABLE` with the reason, distinct from `AGENT_NOT_CONFIGURED` | `BriefingApiIntegrationTest.WithAnUnavailableModel` (2, via `InsightTestUnavailableBriefingConfiguration`) |
| infrastructure | The FR14 consumer logs one `[fr14-agent-unavailable]` line and rethrows, so the binder's retry policy stays in force | covered by the live check below |

Then: insight-service 78 tests green; the jar rebuilt; insight-service restarted on the spent model;
`POST /api/insight/briefings` answered **503 `AGENT_UNAVAILABLE`** with the real 429 text
(`docs/report/evidence/UC-12-503-agent-unavailable-2026-10-05.json`). Then restarted on
`gemini-3.5-flash-lite` (the model the coordinator's `customer-support-agent` uses): the briefing
answered 201; a fresh batch seeded at 4 sales a minute produced three FR14 deliberations (one
`NO_MARKDOWN`, two proposals, both carried to pricing); evidence saved beside the 4 October files.
Full suite: **187 run, 0 failures**.

### Other corrections

- **README**: the simulator example and the troubleshooting row said the seeded batch is "sized to be
  at risk at 30 sales a minute"; at 30 a minute it clears in nine minutes and is never flagged.
  Corrected to 4 a minute everywhere, including `.run/pos-simulator.run.xml`. The model name
  guidance replaced (`gemini-2.5-flash` is restricted and listed for retirement this month). Two
  troubleshooting rows added (the quota 503; the harmless Windows `kafka-streams` permissions error).
  Alignment with the coordinator's references made explicit (layer names, `shareddomain`,
  `democlient`, `GEMINI_API_KEY`), the agent and stream-processing classes linked, the new
  architecture diagram embedded.
- **Report**: the lecture-citation check found 39 of 40 citations correct. The one wrong attribution
  (conformist and published language are Evans's terms, not L5's) fixed in 3.1; two paraphrases
  softened to what the slide says (L2 s22, L4 s38 with s40). The claim that
  `book-recommendaton-agent` constructs its model by hand corrected in 3.6.2 (both references take
  the `ChatModel` from the starter; FreshFlow is the only one that builds it, because of R2). The log
  count corrected (25). The quota incident added to 3.6.3, 4.1 and 5.1. Figure 3.1 re-pointed.
- **Diagrams**: `layered-architecture-all-services` was missing `MarkdownPlanController`,
  `BriefingController`, `BriefingOrchestrator`, the briefing adapter and store, and its note said
  insight-service has no invariant; regenerated. The draw.io A2 still says insight-service has no
  domain layer, lists `markdown-expired` and shows two of five insight endpoints; a UMLet
  `microservice-architecture.uxf` generated from the code replaces it as Figure 3.1 (A2 kept for the
  record; `docs/diagrams/README.md` says why).
- **spec-04**: the 503 of `createBriefing` now names both causes. **ADR 0005**: source 4
  (cargo-tracker) recorded as verified against its GitHub source. Three javadoc paragraphs name the
  cargo-tracker counterparts (`StreamProcessor`, `InteractiveQuery`, the `acl` package).
- **Week 12 deck**: `docs/report/build_week12_deck.py` produces
  `FreshFlow-Week12-Presentation.pptx` (12 slides, speaker and minute on each, notes from the plan),
  copied to the parent `Group Document` folder.

## 2. What was assumed that the specifications did not state

- That an unusable model is the second meaning of spec-04's 503 on `createBriefing`, rather than a
  new status code. spec-04 listed only 201 and 503; HTTP semantics and the existing
  `AGENT_NOT_CONFIGURED` pattern made 503 the natural home, and the description was extended rather
  than a 502 added.
- That the FR14 consumer should rethrow after logging (keeping spec-06 §2's "the binder's retry and
  error handling are then in force") rather than swallow, even though for a daily quota the retries
  are pointless. Swallowing would have changed a documented decision for a marginal gain.
- That `gemini-3.5-flash-lite` is an acceptable demonstration model. It is the coordinator's own
  choice in `customer-support-agent`, it completed FR14 and FR15 today, and its free quota is large.

## 3. Deviations from the specifications, with justification

- spec-04 `createBriefing` 503 description extended (above). No endpoint, field or status added.
- spec-06 §11.5 ("with no LLM configured: 503") is unchanged; the new case is "with an LLM configured
  that cannot be used", recorded here and in the report rather than by editing spec-06, which Phase A
  closed.

## 4. Where the specification or the plan was judged wrong (not acted on without the team)

- The draw.io A2 diagram is Madhuri's and was replaced in the report by a generated UMLet diagram
  without her review. If she corrects and re-exports the A2, the report can use it again; the three
  factual errors in it are listed in `docs/diagrams/README.md`.
- The cover-page roles say Reuben led the inventory and pricing domain models and Madhuri built
  sales-service. The commit record does not show that. The coordinator accepts equal ratings when the
  group agrees, but the justification column must describe what each member actually did, with
  artefacts; the team should write it honestly in the Week 11 meeting.
- `pos-simulator`'s built-in default of 30 transactions a minute is the wrong default for a demo
  (the batch never goes at risk). Changing it would change `PosSimulatorTest`; left as is, documented.
- Spring Boot 3.4.1 reached the end of open-source support in December 2025. Upgrading two weeks
  before submission is not worth the risk; a sentence in 3.7's limitations would be honest.

## 5. For the Week 10 meeting on Wednesday

Bring: the PDF regenerated from this Markdown; the 503 body and today's trace as the new evidence;
the question list in `docs/ROADMAP-2026-10-04.md` T5. Do not re-ask what Dr Su has already answered.

**Result of the scripted run (14:52).** `start-all.ps1 -Fresh` had Kafka healthy and the four
services answering in 40 s; `demo.ps1` ran all eleven steps; step 8 found the trace and the plan
from the logs without help. The agent proposed PERCENT_20 for a shortfall of 31 units and, for the
first time across the runs, the margin floor accepted an agent proposal (3.60, margin 0.4167,
ACTIVE, origin AGENT); saved as evidence. One script defect found and fixed on the way: under
`$ErrorActionPreference = "Stop"`, Windows PowerShell 5.1 turns `java -version` and `docker
compose` stderr output into terminating errors, so the start script runs under "Continue" and
checks every failure explicitly; `demo.ps1`'s `Try-Rest` now reads a 4xx body itself when
PowerShell leaves `ErrorDetails` empty, so the 422's margin figures are printed.

## 7. 15:08: Andy's first run of the scripts, and what it taught the scripts

Andy's transcript (`live_demo_trying1.txt`, kept outside the repository) shows `start-all.ps1
-Fresh` succeeding at 15:01, then a second `start-all.ps1 -Fresh`, which refused because the ports
were busy and told him to run `stop-all.ps1`; he did, and `demo.ps1` then found no services. The
code was fine; the script's message sent a first-time user down the wrong path.

Changed so the scripts cannot be used in the wrong order by accident:

- `start-all.ps1` now looks for running FreshFlow service jars by command line. Already running
  and no `-Fresh`: it prints "already running, next step is demo.ps1" and returns. Already running
  with `-Fresh`: it stops them itself and restarts. A busy port that is not a FreshFlow jar
  (an IntelliJ run, another program) is still an error, now saying so.
- The `Ready` block says to leave the windows open and not to run start-all again.
- `stop-all.ps1` ends by naming the command that starts again; `demo.ps1`'s health-check failure
  says that the services are not running and that a stop-all run stops them.
- README: the order is always start, demo, stop.

Billing: Andy enabled billing on the project at about 15:00 (AI Studio shows "Paid 1, $250
billing account tier cap", prepaid credit A$8.00). The free tier's per-model daily cap no longer
applies; at the flash-lite prices a demonstration costs a few cents, so A$8 covers the rest of the
subject. The team's convention stays `gemini-3.5-flash-lite`.

## 8. 15:20: Andy's second run found the day's second real defect

Andy ran `demo.ps1` on the services my 15:11 test had left running. Steps 1 to 10 passed (the
FEFO deduction showed on an older batch from the earlier run, which is correct and is now
explained by the script). Step 11 answered **500**, not the new 503: the log shows
`Value too long for column "RESULT CHARACTER VARYING(4000)"` on `briefing_tool_call.result`.
After a day of runs the `listRecommendations` tool answered 4,070 characters (one line per FR14
trace, each carrying the model's full rationale, as spec-06 §11 renders it), and the column was
a fixed `VARCHAR(4000)`. H2 refused the insert and the exception reached the controller.

Test-first again. Three failing tests were written: a 23,000-character narrative with a
26,000-character tool result round-trips through `JpaBriefingStore`; a 19,000-character
rationale with 12,000-character tool arguments and result round-trips through
`JpaAgentTraceStore`; and in pricing a 9,000-character agent rationale arriving on
`markdown-recommended`. The first two failed on the column length and now pass after
`arguments`, `result`, `summary` and `rationale` in the four insight entities became `@Lob`
(CLOB): text a language model writes or reads has no natural upper bound, so the column may not
have one either. The third did not fail for the reason expected: pricing already has a
documented rule, `RATIONALE_MAX_LENGTH = 2000`, applied as a 400 on the HTTP path and as a
truncation with a `[truncated]` log line on the consumer, with the full text kept in the agent
trace under the same `agentTraceId`. That rule was untested; the test now pins it instead of
contradicting it, and the pricing entity was left as it was. Suites: pricing 55, insight 80,
total 190.

Also from that run: `start-all.ps1` wrote the logs as UTF-16 (Windows PowerShell 5.1's
`Tee-Object`), which `grep` cannot read; it now writes UTF-8 through a `StreamWriter`. And
`demo.ps1` step 3 warns when older batches of the SKU exist, step 5 lists the SKU's batches in
FEFO order instead of re-reading only the new batch.

Lesson for Section 5: the first defect of the day was a provider limit the tests had never
asked about; the second was a storage limit the tests had never pushed against. Both were found
only by running the system for a whole day, and both are now tests.

