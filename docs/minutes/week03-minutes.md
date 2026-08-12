# CSCI318 Group Project — Weekly Meeting Minutes

## Week 3

| | |
|---|---|
| **Project** | FreshFlow — Real-Time Expiry Risk Detection and Agentic Markdown Decision Support |
| **Date** | 12 August 2026 |
| **Time** | 12:00 – 13:00 |
| **Location** | _[online / on campus]_ |
| **Scribe** | _[name]_ |

**Attendance**

| Member | Present |
|---|---|
| _[Name 1]_ | ☐ |
| _[Name 2]_ | ☐ |
| _[Name 3]_ | ☐ |

---

## 1. Progress Since Last Meeting

- Project proposal completed and confirmed by the tutor as meeting course requirements.
- Project scope frozen: 4 bounded contexts, 14 functional requirements, no frontend.

## 2. Discussion Summary

- Adopted the spec-first AI-driven development methodology presented in Lecture 3.
  Four specification documents are to be produced before any implementation begins:
  Technical Architecture, Functional Requirements, Domain Model, and Integration Test
  Contract.
- Reviewed the domain layer implementation patterns from Lecture 3 (Domain Model,
  Transaction Script, Table Module) and agreed the pattern should be selected per
  bounded context according to domain complexity, rather than applied uniformly.
- Agreed the MVP scope to be demonstrated at the consultation with Dr. Su on
  17 August 2026.
- Discussed LLM runtime options, noting that tool-calling reliability is the critical
  factor for FR14 (agentic markdown recommendation).

## 3. Decisions Made

| # | Decision | Rationale |
|---|---|---|
| D1 | Adopt spec-first development: four specification documents authored before implementation | Follows the AI-Driven Development methodology in Lecture 3; architectural constraints are enforced up front rather than retrofitted |
| D2 | Domain layer implementation pattern selected per bounded context: Domain Model for Inventory and Pricing; Transaction Script for Sales; read model only for Insight | Lecture 3 states the Domain Model pattern suits complex domains with rich business logic. Sales has few business rules; uniform application would be over-engineering |
| D3 | Apache Kafka in KRaft mode, no ZooKeeper | Single container, roughly half the configuration surface, fewer failure modes |
| D4 | LLM runtime: _[record decision]_ | _[record rationale]_ |
| D5 | Coding agent: Claude Code / Codex, with tool and model recorded in the AI collaboration log | Corresponds to the Agentic Coding Systems pillar in Lecture 3; transparency required for the project report |
| D6 | No dependency outside the mandatory list will be added without coordinator approval | Project specification requirement |
| D7 | MVP-CORE defined as the vertical slice: transaction → item-sold → Kafka → windowed aggregation → queryable velocity endpoint | Proves the microservice, event-driven and stream processing pillars end to end before adding breadth |

## 4. Action Items

| # | Action | Owner | Due |
|---|---|---|---|
| A1 | Create private GitHub repository with `/specs`, `/docs/ai-log`, `/docs/minutes`, `/docs/decisions` | _[Name 3]_ | 12 Aug |
| A2 | Bring up single-node Kafka (KRaft) via Docker Compose; all three members verify locally | _[Name 3]_ | 12 Aug |
| A3 | Author Spec 1 — Technical Architecture and Design Constraints | _[Name 1]_ | 13 Aug |
| A4 | Author Spec 2 — Functional Requirements with MVP tiering | _[Name 2]_ | 13 Aug |
| A5 | Author Spec 3 — Domain Model with invariants per aggregate | _[Name 2]_ | 13 Aug |
| A6 | Author Spec 4 — OpenAPI integration test contract | _[Name 3]_ | 13 Aug |
| A7 | Run the development agent Phase 1 specification inspection; resolve reported inconsistencies | _[Name 1]_ | 13 Aug |
| A8 | Verify LLM tool-calling reliability with a single-tool test case | _[Name 1]_ | 13 Aug |
| A9 | Deliver MVP-CORE vertical slice | All | 15 Aug |
| A10 | Rehearse demonstration and record backup video | All | 16 Aug |
| A11 | Begin AI collaboration development log; every member records each substantive agent session | All | ongoing from 12 Aug |

## 5. Risks Raised

| Risk | Owner | Mitigation |
|---|---|---|
| Kafka environment configuration is a single point of failure for all three members | _[Name 3]_ | Resolved as a team on 12–13 August before any business logic is written |
| Small local LLM models may be unreliable at tool calling, which would invalidate FR14 | _[Name 1]_ | Verified with a single-tool test case by 13 August; hosted API used if local proves unreliable |
| Four and a half days to MVP is aggressive | All | Hard checkpoints Friday 22:00 and Saturday 22:00; agreed fallback is to demonstrate MVP-CORE plus the specification set |

## 6. Next Meeting

_[date and time]_

---

*Submitted to Moodle: _[date]_*
