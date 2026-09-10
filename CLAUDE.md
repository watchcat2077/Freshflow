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

## Current milestone

FR14 Markdown Decision Agent. Pulled forward from Week 9; authorised 10 September 2026.

**Phase A is complete.** Design, type surface and failing tests, no implementation. Fifty-three
tests run: the 41 pre-existing pass unweakened, three new ones pass (the R5 source-tree guard and
the two `markdown-recommended` contract tests), and nine fail on
`UnsupportedOperationException("FR14 Phase B")` exactly as intended. See
docs/ai-log/2026-09-10-fr14-phase-a.md.

Week 7 is complete: FR12 velocity, FR2 FEFO deduction, FR13 expiry risk detection, all windowed on
producer-assigned `occurredAt`, with `GET /api/insight/velocity/{sku}` and `GET /api/insight/risks`
served by Interactive Query. spec-05 describes the topology as built.

Five team rulings govern this milestone:
- R1 - the LLM sits behind a port in `insight-service`'s application layer, with a deterministic
  stub for every test and a LangChain4j adapter for runtime. No test calls a live model or touches
  the network. Recorded as ADR 0004
- R2 - the approved LangChain4j entry is `langchain4j-core` plus exactly one provider module. More
  than one provider artifact is a HALT, not a choice
- R3 - `insight-service` gets no `domain` package. `AgentTraceRecord` is a CQRS read model in
  `application`; the JPA entity and its repository are in `infrastructure`, with a mapper between
- R4 - three tools only: `getBatchDetails`, `getSellThroughRate`, `proposeMarkdown`.
  `getMarkdownHistory`, `getMarginFloor` and `queryStorePolicy` appear nowhere
- R5 - the agent proposes only. No price writes, no call to `pricing-service`, and no margin-floor
  logic anywhere in `insight-service` - not a constant, not a check, not a sentence in a prompt
  template. Enforced by `AntiCorruptionLayerGuardTest`

**Two rulings are open and block Phase B**, both set out with options and a recommendation in
spec-06:
- spec-06 s2 - how `sell-through-risk-detected` is consumed. Recommended: a second Spring Cloud
  Stream function on the message-channel binder, giving
  `spring.cloud.function.definition: insightTopology;markdownAgent`, with the test profile's `""`
  unchanged. The FR12/FR13 topology must not change and an LLM call must not run on a Kafka Streams
  thread
- spec-06 s3 - where `getBatchDetails` obtains `unitCost`, which exists nowhere in
  `insight-service` today. Recommended: synchronous `GET /api/batches/{batchId}`, which spec-01
  s1.3, s1.4 and spec-04 all already anticipate. Deferrable: every test runs against a stub of
  `BatchDetailsProvider`, so the ruling decides only which adapter is written

Next: Phase B - implementation until the nine tests pass. It needs both rulings above, and it is
the phase that adds the LangChain4j dependency, which requires subject coordinator approval against
spec-01 s1.1. Do not start it without being told Phase A is approved.

Out of scope, explicitly: FR15, FR9's `POST /api/markdown-plans` and its consumption of
`markdown-recommended`, FR3 write-offs and `batch-written-off`, the `markdown-applied` and
`markdown-expired` topics, and the three later FR14 tools.

## Hard constraints — never violate

Dependencies: only what spec-01 section 1.1 lists. Anything else needs subject
coordinator approval. If you think one is necessary, HALT and report.

Layering: presentation -> application -> domain <- infrastructure
The domain package imports NOTHING: not presentation, not application, not
infrastructure, not Spring, JPA, Jackson or Kafka. Repository interfaces are declared
in domain; JPA implementations live in infrastructure.

Domain layer pattern is per service:
- inventory-service: Domain Model. POJOs, persistence ignorant. No JPA annotations
  on domain classes. Separate JPA entities plus mappers in infrastructure.
- pricing-service: Domain Model. Same rules.
- sales-service: Transaction Script. Application-service-centric.
  Do NOT introduce aggregate roots or repository abstractions.
- insight-service: no domain layer. CQRS read models and agent traces only.

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