# AI collaboration logs

One entry per session in which the coding agent (Claude Code) was used, written at the end of the
session. Each entry records what was done, what was assumed that the specifications did not state,
any deviation from the specifications with its justification, and anything the agent judged wrong
in a specification but did not act on. Together they are the evidence Section 5.1 of the project
report refers to. The prompts that started the larger sessions are in `docs/prompts/`.

| Date | Entry | What it records |
|---|---|---|
| 26 Aug 2026 | `2026-08-26-phase-1.md` | Phase 1: inspection of spec-01 to spec-04 and the consistency report (findings B-1 to B-7, M-1 to M-9, S-1) |
| 26 Aug 2026 | `2026-08-26-phase-1-rerun.md` | Phase 1 re-inspection after the specification amendments |
| 26 Aug 2026 | `2026-08-26-phase-1-rerun-2.md` | Phase 1 re-inspection after the B-5 ruling |
| 26 Aug 2026 | `2026-08-26-phase-2.md` | Phase 2: the Maven multi-module skeleton and the environment |
| 26 Aug 2026 | `2026-08-26-phase-3.md` | Phase 3: integration tests generated from spec-04, failing by design |
| 26 Aug 2026 | `2026-08-26-phase-4.md` | Phase 4: implementation until the Phase 3 tests pass |
| 7 Sep 2026 | `2026-09-07-week-6-kafka.md` | Kafka wiring through Spring Cloud Stream; the first integration events |
| 7 Sep 2026 | `2026-09-07-week-7-fr12-velocity.md` | FR12: the hopping window on event time and the velocity query |
| 7 Sep 2026 | `2026-09-07-week-7-fr13-risk.md` | FR2 FEFO deduction (precondition) and FR13 expiry risk detection |
| 8 Sep 2026 | `2026-09-08-reference-study.md` | Read-only study of the subject's reference repositories against FreshFlow, with the outcome of each follow-up |
| 10 Sep 2026 | `2026-09-10-reference-adoption.md` | Three specification devices adopted from the study: spec-05, the requirements-to-tactics table, the layer-mapping table |
| 10 Sep 2026 | `2026-09-10-fr14-phase-a.md` | FR14 Phase A: spec-06, the type surface and the failing tests |
| 10 Sep 2026 | `2026-09-10-fr14-phase-b1.md` | FR14 Phase B1: the orchestrator, the tool registry and the trace |
| 10 Sep 2026 | `2026-09-10-fr14-phase-b2.md` | FR14 Phase B2: the LangChain4j adapter, the ReAct loop and the runtime wiring |
| 11 Sep 2026 | `2026-09-11-fr14-phase-b2b.md` | FR14 Phase B2b: documentation corrections and the ADR 0004 amendment |
| 14 Sep 2026 | `2026-09-14-fr14-test-profile-guard-fix.md` | Defect fix: the test-profile guard did not reach the nested configuration |
| 14 Sep 2026 | `2026-09-14-fr14-thought-signature-fix.md` | Defect fix: the second ReAct turn was rejected for a missing thought signature |
| 29 Sep 2026 | `2026-09-29-repository-relocation.md` | Repository interfaces moved to `infrastructure.repository` under ADR 0005 |
| 4 Oct 2026 | `2026-10-04-fr9-fr11-pricing.md` | FR8, FR9 and FR11: price rule upsert, markdown plans and the margin floor rejection |
| 4 Oct 2026 | `2026-10-04-fr6b-fr4-pos-simulator.md` | FR6b, FR4 and the POS simulator |
| 4 Oct 2026 | `2026-10-04-fr15-briefing-and-insight-domain.md` | FR15 briefing agent and the insight-service domain package (ADR 0006) |
| 4 Oct 2026 | `2026-10-04-final-stretch-build.md` | Summary of the 4 October sessions |
| 4 Oct 2026 | `2026-10-04-live-run.md` | First run of the whole loop against Kafka and a live model |
| 5 Oct 2026 | `2026-10-05-review-and-hardening.md` | Build verified, live run repeated, the model-unavailable path hardened (503 `AGENT_UNAVAILABLE`), CLOB columns, the demonstration scripts |
| 6 Oct 2026 | `2026-10-06-repository-cleanup.md` | Repository scope reduced to what the specification asks for; five specification-versus-code mismatches settled on 7 October |
| 13 Oct 2026 | `2026-10-13-submission-review.md` | Submission review: topology tests under `TopologyTestDriver`, global exception handler, comment sweep, specifications aligned with the code |
| 13 Oct 2026 | `2026-10-13-fr13-final-hour-fix.md` | FR13 final-hour finding: four policy tests written and watched failing, halted before the code change because `InsightTopologyTest` asserts the truncation; team ruling of 18 October 2026: not merged, recorded as spec-05 G-8 |
