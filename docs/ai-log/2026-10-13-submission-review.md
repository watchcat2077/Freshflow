# 2026-10-13 — Submission review: topology tests, the error contract, and the documents aligned with the code

| | |
|---|---|
| Session | Tuesday 13 October 2026, Week 11 |
| Driver | A written review of the repository against the project specification, worked through item by item before the 19 October submission |
| Tool / model | Claude Code; three parallel sub-sessions in git worktrees for the independent code tasks, merged by cherry-pick |
| Outcome | 205 tests run, 205 pass, with `LLM_API_KEY` and `LLM_MODEL` unset and with both set; no new runtime dependency; eight commits on `main` |

## 1. What was done

### 1.1 Code, test-first

| Change | Where | Tests |
|---|---|---|
| The Kafka Streams topology is tested on its own under `TopologyTestDriver`: windows placed by `occurredAt` while the record timestamps are decades away; the baseline table keeps the last write; one `sell-through-risk-detected` per transition into risk, none while the window is open, none on later windows; a batch that will clear stays silent with an unclamped negative shortfall; `stock-level-changed` folds into the batch state and is dropped for an unseen batch | `insight-service/src/test/java/.../infrastructure/InsightTopologyTest.java`; `kafka-streams-test-utils` in test scope (approved 17 September 2026; version 3.8.1 managed by Spring Boot) | 5, all failing first with the assertions deliberately wrong |
| A `GlobalExceptionHandler` (`@RestControllerAdvice`) in each service's presentation layer answers a body that cannot be parsed, or a path or query value of the wrong type, with spec-04's `ErrorResponse` and the code `MALFORMED_REQUEST`. Nothing else is handled there: business outcomes still travel through `ApplicationResult` | `{inventory,sales,pricing,insight}-service/.../presentation/GlobalExceptionHandler.java` | 4, one per service, failing first with "Content type not set" |
| `freshflow.llm.temperature` fixed at 0.0 so a demonstration gives the same answer for the same facts; `GEMINI_API_KEY` read when `LLM_API_KEY` is unset | `LlmProperties`, `MarkdownAgentConfiguration`, insight `application.yml` | 3 (`LlmPropertiesTest`), failing first at compile time |
| The H2 console enabled at runtime in every service, test profile untouched | the four `application.yml` files | the four `@SpringBootTest` suites log the console path |
| `pos-simulator` no longer declares the `contracts` dependency it never imported; `RiskAssessmentPolicyTest` moved beside its class in `domain` | `pos-simulator/pom.xml`; `insight-service/src/test/.../domain/` | unchanged, 15 and 89 |
| Comments and Javadoc under `src/main/java` describe the code as built: no milestone numbers, finding ids, pending rulings or references to removed files. Fifty-six files, every changed line a comment line (checked with a diff filter) | all five Java modules | 205, unchanged behaviour |

### 1.2 Repository hygiene

- `.gitignore` rewritten without a byte-order mark; the Postman workspace files, `.vscode`, zips and `.DS_Store` ignored; the Postman collection JSON restored after a tool had replaced it with its own folder layout.
- `scripts/`: no offline flag in any command the scripts print or run; `stop-all.ps1 -Kafka` now runs `docker compose stop` (the broker's data is kept) and `-Kafka -Wipe` removes the container and its data; the comment in `docker-compose.yml` says the same; `demo.ps1` no longer names a removed file in its example.
- `assets/` holds the three team diagrams the README shows (context map, microservices and topics, layered architecture).
- README rewritten as a walkthrough: the three diagrams, the one-command and step-by-step starts, every use case with both `Invoke-RestMethod` and `curl`, the error contract, the H2 console, the tests, where to look, troubleshooting, the layout as `git ls-files` shows it. References to reference repositories outside this project removed.
- `docs/ai-log/README.md` indexes the logs; `docs/prompts/README.md` explains the prompt kept there.

### 1.3 Documents aligned with the code

- The 8 September reference study rewritten as a study of reference material, with an outcome table saying which of its follow-ups were adopted; ADR 0005 and the 10 and 29 September logs reworded to match. Passages about team members' contributions and about billing removed from the 5 October log.
- spec-01: M-9 closed (validation approved on 17 September, hand-rolled validation retained, malformed bodies answered by the handler); the testing row names `kafka-streams-test-utils`; G-6's status at submission.
- spec-02: a status-at-submission paragraph; the `Built` column as of 13 October; the three deferred FR14 tools marked not built, with `getMarginFloor` rejected by design under R5; `getSellThroughRate(sku)` as implemented; `insight-service` described per ADR 0006.
- spec-04: `ErrorResponse` describes the codes and `MALFORMED_REQUEST`.
- spec-05: §1.4, the same topology read as SQL, stage by stage; the risk policy in `domain`; the serde fix recorded under §6; statuses at submission for G-3, G-4 and G-6; `InsightTopologyTest` in the traceability table.
- spec-06: version 1.0 with a status-at-submission note; §8 and §9 point at the live runs instead of a removed runbook; the ADR 0004 checks by their new names.
- ADR 0004: a second amendment renames the 11 September checks Check A and Check B (CLAUDE.md reused the identifier R10 on 4 October) and restates them as the 14 September defect report asked: the profile guard must appear on every configuration class, the api-key condition is not a test guard, `MarkdownAgentTestProfileGuardTest` verifies it, and the suite must pass with the variables set as well as unset. Both runs were made today.
- ADR 0005 and ADR 0006 wording; `insight-service/pom.xml` description; the insight test profile's comment.

## 2. What was assumed that the specifications did not state

- That "the state at submission" is the right thing for spec-02's `Built` column and spec-05's limitation statuses to record, in place of the week they were scheduled for. The scope tags and the finding ids are kept as the record of the plan.
- That spec-02's `getSellThroughRate` row should show the implemented signature, `getSellThroughRate(sku)`, since the window is configuration and spec-06 §4 has carried that signature since 10 September. Recorded as "settled 13 October 2026"; the team can reverse it.
- That the "sliding window" wording of FR12 may stand, with spec-05 as the Kafka-specific description, rather than being changed to "hopping".
- That `MALFORMED_REQUEST` is the right code for a body the framework cannot read. spec-04 named no code for that case.
- That the G-6 contract test over every event in `contracts` is a known limitation at submission rather than something to add now: `MarkdownRecommendedEventContractTest` covers one event, the serde defect that raised the finding is fixed, and the remaining topics are produced by one binder with one `ObjectMapper`.

## 3. Deviations from the specifications

None in behaviour. One dependency was added, `kafka-streams-test-utils`, in test scope only, from the list the subject coordinator approved on 17 September 2026; spec-01 §1.1 records it. The `GlobalExceptionHandler` is a presentation-layer class that decides nothing about the domain, consistent with spec-01 §2.5.

## 4. Where a document was judged wrong, and what was done about it

- ADR 0004's first amendment claimed "two independent guards, either sufficient" three days before the defect that showed the profile guard alone had not reached the nested configuration. The 14 September log asked for the ADR to be corrected and nothing had been done; the second amendment does it.
- spec-01 §1.1 still said the validation question was awaiting the coordinator, four weeks after the ruling. Closed.
- spec-05 §3.2 and §4.2 still said `insight-service` had no domain package in the paragraph that recorded the move to `domain`. Corrected.
- spec-06 §8 and §9 pointed at `RUNBOOK.md`, which left the repository on 6 October. They now point at the live-run logs and the scripts.
- Observed, not changed: FR13's G-4 limitation (a SKU that stops selling entirely is not re-assessed) is still the most visible gap in the stream side and is stated in spec-02, spec-05 and the README's known limitations.
