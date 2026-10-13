# FreshFlow — Project Memory

CSCI318 Software Engineering Practices & Principles, Spring 2026.
The architecture is the assessed artefact. Code that works but violates the
specified layering is a failure, not a shortcut.

## Authoritative sources

Read these before doing anything. They override your own judgement.

- specs/spec-01-architecture.md   — stack, topology, layering, event contracts
- specs/spec-02-requirements.md   — FR1-FR15, scope tags
- specs/spec-03-domain-model.md   — aggregates, entities, value objects, invariants
- specs/spec-04-api-contract.yaml — the only endpoints in scope
- specs/spec-05-stream-processing.md — the Kafka Streams topology, windowing, join,
                                    state stores and known limitations
- specs/spec-06-agent-orchestration.md — FR14: the type surface, the tool contracts, idempotency,
                                    the trace, and the Anti-Corruption Layer stated as a rule
- specs/dev-agent-prompt.md       — the four-phase execution pipeline

If a spec is ambiguous, incomplete or self-contradictory: HALT and report. Do not guess.

## Current state (13 October 2026)

The code is complete for the twelve functional requirements the project presents: FR1, FR2, FR4,
FR5, FR6a and FR6b, FR8, FR9, FR11, FR12, FR13, FR14, FR15. **207 tests run, 207 pass**, both with
`LLM_API_KEY` and `LLM_MODEL` unset and with both set (inventory 23, sales 21, pricing 57,
insight 91, pos-simulator 15). Every increment was built test-first and is logged in
`docs/ai-log/` (index in `docs/ai-log/README.md`).

Added on 13 October 2026 (`docs/ai-log/2026-10-13-submission-review.md`): `InsightTopologyTest`
drives the real topology under `TopologyTestDriver` (`kafka-streams-test-utils`, test scope,
insight-service only); a `GlobalExceptionHandler` in each service's presentation layer answers an
unparseable body with 400 `MALFORMED_REQUEST` and nothing else; `freshflow.llm.temperature` is 0.0
and `GEMINI_API_KEY` is read when `LLM_API_KEY` is unset; the H2 console is on at runtime; the
specifications state their status at submission; the README is a walkthrough with the team's
diagrams in `assets/`.

| Service | Holds |
|---|---|
| inventory-service | `POST /api/batches`, `GET /api/batches/{id}`, `GET /api/batches?sku=` (FR1, FR4); FEFO deduction on `item-sold` with an idempotent event ledger (FR2); `StockBatch` aggregate, `FefoDeductionPolicy`; `GlobalExceptionHandler` for malformed bodies (every service has one) |
| sales-service | `POST /api/transactions` (FR5, FR6a, FR6b); a local price snapshot fed by `price-rule-published` and `markdown-applied` through the `PriceSnapshotFeed` port; Transaction Script |
| pricing-service | `PUT` and `GET /api/prices/{sku}` (FR8); `POST` and `GET /api/markdown-plans` (FR9, FR11); `MarkdownPlan` aggregate and `MarginFloorPolicy` in the domain layer; a `markdown-recommended` consumer and a `markdown-applied` producer |
| insight-service | The Kafka Streams topology (FR12 velocity, FR13 risk) served by interactive queries; the Markdown Decision Agent (FR14) with its persisted trace; the Briefing Agent (FR15) at `POST` and `GET /api/insight/briefings`; a small domain package (`RiskAssessmentPolicy`, `BatchRiskView`, ADR 0006); `AgentUnavailableException` and 503 `AGENT_UNAVAILABLE` when the model cannot be used |
| contracts, pos-simulator | The integration event records (Published Language); a JDK-only command-line client that drives the demonstration |

The whole loop has been run against Kafka and the Gemini API on 4 and 5 October 2026
(`docs/ai-log/2026-10-04-live-run.md`, `docs/ai-log/2026-10-05-review-and-hardening.md`); the
requests and responses per use case are in `docs/evidence/`. Operational facts those runs
established: a per-module `spring-boot:run` needs `mvnw install` first; the demonstration sales rate
stays at 4 a minute so the seeded batch is flagged; the demonstration model is `gemini-3.5-flash-lite`;
`scripts/start-all.ps1 -Fresh`, `scripts/demo.ps1` and `scripts/stop-all.ps1` run the loop end to end.

**Repository scope.** The repository holds what the specification asks the submitted zip to hold:
source code, specification files, README, and the artefacts Section 5.1 of the report refers to
(ADRs, AI logs, prompts, scripts, the Postman collection, live-run evidence). The report, the use
cases, the diagrams, the slides and the minutes are the team's own deliverables and are kept outside
the repository.

Week 7 is complete: FR12 velocity, FR2 FEFO deduction, FR13 expiry risk detection, all windowed on
producer-assigned `occurredAt`, with `GET /api/insight/velocity/{sku}` and `GET /api/insight/risks`
served by Interactive Query. spec-05 describes the topology as built.

Team rulings in force:
- R1 - the LLM sits behind a port in `insight-service`'s application layer, with a deterministic
  stub for every test and a LangChain4j adapter for runtime. No test calls a live model or touches
  the network. Recorded as ADR 0004. FR15's `BriefingAgent` follows the same rule
- R2 - the approved LangChain4j entry is `langchain4j-core` plus exactly one provider module. More
  than one provider artifact is a HALT, not a choice. The coordinates are
  `dev.langchain4j:langchain4j-core:1.20.0` and `dev.langchain4j:langchain4j-google-ai-gemini:1.20.0`,
  managed in the root pom and recorded in spec-01 s1.1
- R3 - **amended 4 October 2026 by ADR 0006** on the subject coordinator's ruling that a CQRS
  context has a domain layer: `insight-service` has a `domain` package holding
  `RiskAssessmentPolicy` and `BatchRiskView` only. `AgentTraceRecord`, the views, the briefing
  and the stores stay CQRS read models in `application` and `infrastructure`; no aggregate, no
  repository in the DDD sense
- R4 - three tools only for FR14: `getBatchDetails`, `getSellThroughRate`, `proposeMarkdown`.
  `getMarkdownHistory`, `getMarginFloor` and `queryStorePolicy` appear nowhere. FR15 has its own
  three read-only tools (`listCurrentRisks`, `listRecommendations`, `getSellThroughRate`)
- R5 - the agents propose or summarise only. No price writes, no call to `pricing-service`, and no
  margin-floor logic anywhere in `insight-service` - not a constant, not a check, not a sentence in
  a prompt template. Enforced by `AntiCorruptionLayerGuardTest`, which scans string literals, so
  both system prompts are inside the guard
- R6 (spec-06 s2 and s3, 10 September) - the trigger is consumed by a second Spring Cloud Stream
  function on the message-channel binder, giving
  `spring.cloud.function.definition: insightTopology;markdownAgent`, with the test profile's `""`
  unchanged; and `getBatchDetails` reads unit cost over synchronous `GET /api/batches/{batchId}`
- R7 - a proposal whose `batchId` is not the trigger's is refused by the **orchestrator** and
  recorded as declined. The tool cannot make that check; it has no trigger. spec-06 s4.3 and s5
- R8 - the save-then-publish window is known limitation **G-7** in spec-06 s10, not a defect to fix.
  No transactional outbox
- R9 - a proposal that cannot be published because the batch could not be resolved records
  `PROPOSED_BUT_UNRESOLVABLE`, not `NO_MARKDOWN`. spec-06 s6
- R10 (4 October 2026) - the margin ratio is gross margin as a fraction of the selling price,
  computed once in `MarginFloorPolicy`; a breaching plan is persisted as REJECTED and answered
  with 422; the HTTP endpoint and the `markdown-recommended` consumer share one application
  service and one domain rule. spec-03 s3, spec-04

Coordinator rulings already in hand (do not re-ask): the Kafka Streams DSL is the expected way to
implement SQL-like analysis; repositories live in `infrastructure.repository`; a CQRS read side has
a domain layer; one agent may serve two user stories;
`spring-boot-starter-validation`, `kafka-streams-test-utils` and `spring-cloud-stream-test-binder`
are approved (17 September 2026); only `kafka-streams-test-utils` has been added, in test scope, on
13 October 2026.

Out of scope, explicitly: FR3 write-offs and `batch-written-off`, FR7, FR10 and the
`markdown-expired` topic, category-level margin floors, and the three later FR14 tools.

## Hard constraints — never violate

Dependencies: only what spec-01 section 1.1 lists. Anything else needs subject
coordinator approval. If you think one is necessary, HALT and report.

Layering: presentation -> application -> domain <- infrastructure
The domain package imports NOTHING: not presentation, not application, not
infrastructure, not Spring, JPA, Jackson or Kafka. Repository interfaces live in
infrastructure.repository, per the subject coordinator's ruling (Dr Guoxin Su, 17 and
22 September 2026; L4 slide 34; ADR 0005). Their JPA implementations live in
infrastructure.

Domain layer pattern is per service:
- inventory-service: Domain Model. POJOs, persistence ignorant. No JPA annotations
  on domain classes. Separate JPA entities plus mappers in infrastructure.
- pricing-service: Domain Model. Same rules.
- sales-service: Transaction Script. Application-service-centric.
  Do NOT introduce aggregate roots or repository abstractions.
- insight-service: CQRS read side. A small domain package (RiskAssessmentPolicy,
  BatchRiskView; ADR 0006). Read models, agent traces and briefings live in application and
  infrastructure. No aggregate roots, no repositories in the DDD sense.

Invariant placement: business invariants live in the domain layer. Never in
controllers. Never in an LLM prompt. The margin floor check is enforced inside
pricing-service's domain layer and applies identically whether the proposal came
from a human or from the agent.

Application layer: commands are simple DTOs handled in the application service layer.
They do not enter the domain layer. Prefer one command plus one handler per complex
use case over a bloated application service.

Events: defined in the contracts module. Every event carries eventId (UUID, for
idempotent consumption) and occurredAt (producer-assigned event time). Windowing uses
occurredAt, never Kafka ingestion time. Distinguish internal events (within one
bounded context) from external integration events (across contexts, via Kafka).

Configuration: LLM provider injected via application.yml. API keys from environment
variables only. Never commit a key.

## Phase gate

The pipeline in specs/dev-agent-prompt.md is sequential and gated:
1. Phase 1 — specification inspection, consistency report
2. Phase 2 — environment plus Maven multi-module skeleton
3. Phase 3 — integration tests from spec-04. They must FAIL at this stage.
4. Phase 4 — implementation until tests pass

Never start a phase without being told the previous one is approved. If asked to
"just build it", stop and ask which phase is authorised.

## Reporting

After every phase, output:
1. What you did
2. What you assumed that the specs did not state
3. Any deviation from the specs, with justification
4. Anywhere you judged the spec wrong — but do NOT act on it without team approval

Save this to docs/ai-log/YYYY-MM-DD-phase-N.md. It is the evidence base for Section 5
of the project report. If the user forgets, remind them once.

## House rules

- Work only inside the repository root. Do not create files outside it.
- Small, reviewable commits. Conventional commit messages: feat, docs, test, chore.
- Never weaken a test to make it pass.
- Never implement POST-MVP requirements unprompted.