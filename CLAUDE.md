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

Week 7 part 1, ends Sunday 20 September 2026. Authorised 7 September 2026.

Week 6 is complete and pushed (6e2762d): Kafka is wired through Spring Cloud Stream,
inventory publishes `batch-received`, sales publishes `item-sold`, pricing produces
`price-rule-published`, and sales materialises its `PriceSnapshot` from that topic behind
the unchanged `PriceSnapshotFeed` port. Sixteen tests pass.

Target: FR12 ONLY - rolling sell-through velocity, in insight-service.

- Kafka Streams topology consuming `item-sold`, keyed by SKU
- Aggregate quantity over a sliding window. MVP simplification per spec-02: a 60-second
  window for demonstration; the production target is 60 minutes advancing every 5 minutes
- Windowing MUST use the producer-assigned `occurredAt`, never Kafka ingestion time.
  spec-01 s2.6, not negotiable
- Compare against a stored velocity baseline held in a `KTable`
- Expose through `GET /api/insight/velocity/{sku}` by Interactive Query, per spec-04:
  200 with the velocity view, 404 when no windowed data exists yet for that SKU

insight-service has NO domain layer (spec-01 s2.3). CQRS read models only. Its ports are
declared in `application`, per s2.3's table for a service with no domain package.

Tests are written before the implementation and must fail first. All 16 existing tests
must still pass, unweakened.

Out of scope this week, explicitly: FR13 and `sell-through-risk-detected`, the
stream-table join against batch state, `GET /api/insight/risks`, the agent and its traces,
`POST /api/markdown-plans`, and inventory consuming `item-sold` to deduct stock.

FR13 is Week 7 part 2. The agent is Week 9. Do not build ahead of the current milestone.

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