# ADR 0006 — `insight-service` carries a small domain package

| Field | Value |
|---|---|
| Status | **Accepted** |
| Date | 4 October 2026 |
| Deciders | Subject coordinator (Dr Guoxin Su), consultation of 22 September 2026; recorded and applied by the project team |
| Supersedes | Team ruling **R3** of 10 September 2026 ("`insight-service` gets no `domain` package"), in part. The CQRS read-model part of R3 stands: `AgentTraceRecord`, the velocity and risk views, and the stores that serve them remain in `application` and `infrastructure` |
| Affects | `insight-service`, spec-01 §2.1 and §2.3, CLAUDE.md "Layering" and ruling R3, the Section 3.2 layered-architecture diagram |

## Context

spec-01 §2.3 assigned no domain layer to the Insight context, on the argument that a CQRS read
side holds no transactional business data and therefore has nothing for a Domain Model to
protect. The course specification, however, says that *each* service is expected to include a
presentation layer, a service layer, a domain layer and an infrastructure layer (Section 3.2),
and when the team put the question to the subject coordinator on 22 September 2026 the answer
was direct: a CQRS context has a domain layer.

The question was never whether `insight-service` has domain logic. It does. `RiskAssessmentPolicy`
decides, from remaining quantity, hours to expiry and sell-through velocity, whether a batch is at
risk of expiring unsold. That is a business rule of the Insight context, it imports only `java.*`,
and it was sitting in `application` only because the package did not exist.

## Decision

`insight-service` gains `com.freshflow.insight.domain`, holding the types that are pure business
rules or the values those rules produce:

| Type | From | To | Why it belongs in the domain |
|---|---|---|---|
| `RiskAssessmentPolicy` | `application` | `domain` | A domain service in the L4 sense: a stateless rule that spans data from two contexts (batch state and velocity) and belongs to neither aggregate |
| `BatchRiskView` | `application` | `domain` | The value the policy produces; a value object with no identity of its own |

Everything else stays where it is. The read-side ports (`RiskViewStore`, `VelocityWindowStore`,
`VelocityBaselineStore`, `AgentTraceStore`) stay in `application` under ADR 0002 Decision 2 and
ADR 0004. The agent orchestration stays in `application` under ADR 0004. The FR15 briefing added
on the same date follows the same split: its summarisation rules are in `domain` only where they
are pure; its ports and orchestration are in `application`.

The dependency rule of spec-01 §2.2 applies to the new package unchanged: it imports nothing but
`java.*`. `AntiCorruptionLayerGuardTest` scans it like every other main source of this service, so
no margin-floor or pricing vocabulary can enter it.

## Consequences

- All four services now show the same four layers, and Section 3.2 of the report needs no
  paragraph of apology for the fourth.
- Thirteen import lines change across the service and its tests; no behaviour changes; the test
  count is unchanged.
- R3's read-model argument survives: the Insight context still has no aggregate, no repository
  in the DDD sense, and no transactional write model. What it has is a domain service and a
  value object, which is what a CQRS read side that computes something is expected to have.

## Verification

1. `grep -rnE "^import (com\.freshflow\.insight\.(presentation|application|infrastructure)|org\.springframework|jakarta|com\.fasterxml|org\.apache\.kafka|dev\.langchain4j)" insight-service/src/main/java/com/freshflow/insight/domain` returns nothing.
2. `.\mvnw.cmd -o test` passes with no test removed or weakened.
