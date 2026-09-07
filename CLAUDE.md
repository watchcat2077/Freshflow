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
- specs/dev-agent-prompt.md       — the four-phase execution pipeline

If a spec is ambiguous, incomplete or self-contradictory: HALT and report. Do not guess.

## Current milestone

Week 6, ends Sunday 13 September 2026. Authorised 7 September 2026.

Week 5 is complete and committed (187262f): the four services build, start on 8081-8084, and
the four MVP-CORE endpoints work against H2 with ten passing tests.

Target: Kafka wiring via Spring Cloud Stream. The Week 5 logging adapters are replaced by Kafka
adapters behind the SAME outbound ports, so no application-layer code changes. Concretely:

- `spring-cloud-stream` plus the Kafka binder, both already listed in spec-01 s1.1
- inventory publishes `batch-received`; sales publishes `item-sold` (spec-01 s2.6)
- pricing becomes the producer of `price-rule-published`
- sales' `PriceSnapshotFeed` adapter swaps from the static-config seed to the Kafka
  subscription. The port signature does not change - that is the whole point of ADR 0001,
  and `freshflow.sales.price-snapshot-seed` is retired per spec-01 s2.7
- consumers deduplicate on `eventId` and apply last-write-wins on `occurredAt` per key

Kafka runs at localhost:9092 via docker-compose.yml. All ten Week 5 tests must still pass,
unweakened.

Out of scope this week, explicitly: Kafka Streams, the insight-service consumers and its
stream topology, POST /api/markdown-plans, all three /api/insight endpoints, and the agent.

Kafka Streams is Week 7. The agent is Week 9. Do not build ahead of the current milestone.

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