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
- specs/dev-agent-prompt.md       — the four-phase execution pipeline

If a spec is ambiguous, incomplete or self-contradictory: HALT and report. Do not guess.

## Current milestone

Week 7 part 2, ends Sunday 20 September 2026. Authorised 7 September 2026.

Week 7 part 1 is complete and pushed (969d96b): FR12 rolling sell-through velocity, windowed
on producer-assigned `occurredAt`, with a baseline in a KTable and
`GET /api/insight/velocity/{sku}` served by Interactive Query. Twenty tests pass.

Three team rulings landed with this milestone:
- ADR 0003 - the config-seeded velocity baseline is an accepted MVP simplification behind a
  port; a measured baseline replaces the seeder alone
- spec-02 FR12 - the window-selection and staleness rule is now specified, not just implemented
- spec-01 s2.6 - internal topics are now distinguished from integration topics (Table A / B)

Target: FR2 then FR13. FR2 is a precondition, because FR13 needs a KTable of live batch state
built from `batch-received` AND `stock-level-changed`, and `stock-level-changed` had no producer.

FR2 - deduct stock on sale, in inventory-service:
- consumes `item-sold`, deducts FEFO across batches of the same SKU
- stock on hand never becomes negative
- publishes `stock-level-changed`
- idempotent with respect to `eventId`
- FEFO selection spans multiple aggregates, so it is a domain service `FefoDeductionPolicy`
  (spec-03 s1). Domain classes stay POJOs - no Spring, no JPA imports

FR13 - expiry risk detection, in insight-service:
- stream-table join between the velocity stream and the live batch-state KTable
- project sales over remaining trading hours; compare against remaining quantity
- emit `sell-through-risk-detected` where the shortfall exceeds a configured threshold
- expose `GET /api/insight/risks` per spec-04
- windowing and time arithmetic use `occurredAt`, never ingestion time

FR2 is tagged `[POST-MVP]` in spec-02 and is built here only because FR13 depends on it, on
explicit authorisation. Tests before implementation, confirmed failing first. All 20 existing
tests must still pass, unweakened.

Out of scope, explicitly: the agent and every FR14 tool, `AgentTraceRecord`,
`GET /api/insight/agent-traces/{id}`, `POST /api/markdown-plans`, FR3 write-offs and
`batch-written-off`, and the markdown topics.

The agent is Week 9. Do not build ahead of the current milestone.

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