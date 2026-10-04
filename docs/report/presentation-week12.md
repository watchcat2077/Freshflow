# Week 12 presentation plan

Guideline (CSCI318 project presentation guideline 2026): all members attend; 12 minutes in
total, strictly timed; slides identical to the ones submitted to Moodle; content must cover
the business domain, analysis, design, architecture and implementation, plus a short
demonstration; the first slide carries the members and, if the group agrees, the contribution
percentages.

## What FreshFlow is, in the words to use on stage

A supermarket loses money twice on perishable stock: once when it discounts too early and
gives away margin on units that would have sold anyway, and once when it discounts too late
and writes the batch off. The decision depends on three things nobody holds in their head at
once: how fast the product is selling right now against normal, how many units are left in
this particular batch, and how many trading hours remain before it expires.

FreshFlow computes those three things from the live sales stream and asks an LLM agent to
propose a markdown for the batches that will not clear. The agent can only propose. A
deterministic rule in the pricing service, the margin floor, decides whether a proposal is
allowed, and it decides the same way for a human and for the agent. That is the sentence the
whole architecture exists to make true, and it is the answer to the question the marker is
most likely to ask: why not put the margin floor in the prompt? Because a prompt is a request
to a probabilistic model and a business invariant has to be deterministic, testable and the
same every time.

## Timing (12 minutes)

| Min | Slide | Speaker | Content |
|---|---|---|---|
| 0:00 | 1 Title and team | Reuben | names, roles, contribution split |
| 0:30 | 2 The problem | Reuben | the two ways a supermarket loses money on short-dated stock |
| 1:30 | 3 The idea | Reuben | stream -> risk -> agent proposes -> domain rule decides |
| 2:30 | 4 Bounded contexts and context map | Madhuri | why "product" means three things; the four services; integration patterns |
| 3:30 | 5 Architecture diagram (A2) and event flow | Madhuri | topics, the one synchronous call, no shared database |
| 4:30 | 6 Layered architecture and DDD patterns | Madhuri | the four packages; StockBatch aggregate, Money VO, FefoDeductionPolicy, MarkdownPlan |
| 5:30 | 7 Stream processing | Andy | event time, hopping window, stream-table join, interactive query; the SQL-like reading of the DSL |
| 6:30 | 8 The two agents | Andy | ReAct tool loop, three tools each, persisted trace, the guard test that keeps the margin floor out of insight-service |
| 7:30 | 9 Live demonstration (pre-recorded backup video ready) | Andy | risk appears, agent trace, 201 then 422, discounted scan |
| 10:00 | 10 AI-driven development | Reuben | four-phase gated pipeline, 20+ logs, 6 ADRs, an accepted / rejected / modified example each |
| 11:00 | 11 Limitations and what we learned | Reuben | G-3, G-4, G-6, G-7 stated plainly; one lesson each |
| 11:40 | 12 Thank you | all | questions |

## Demonstration script (2.5 minutes, rehearsed)

Before the session: Kafka up, four services up with the LLM key set, `scripts/demo.ps1`
already run once so a risk and an agent trace exist, and the backup video of the same run
recorded in case the network or the model is slow on the day.

1. Show `GET /api/insight/risks`: one batch, projected shortfall, hours to expiry (FR13).
2. Show the agent trace at `GET /api/insight/agent-traces/{id}`: three tool calls in order,
   the proposal, the rationale citing the numbers (FR14).
3. Show the plan pricing-service created from the recommendation: status ACTIVE, discounted
   price (FR9), and then the 422 for PERCENT_60 with the resulting margin ratio (FR11).
4. Show one sale at the discounted price with `markdownApplied: true` (FR6b).
5. Show the briefing (FR15), four to eight sentences, and point at the counts that come from
   the orchestrator rather than the model.

## Questions to expect, with the short answer

- **Why not put the margin floor in the agent's prompt?** A prompt cannot be unit-tested and
  a model can ignore it. The floor is a domain rule in pricing-service, covered by tests, and
  a guard test fails the build if the words even appear in insight-service.
- **Does insight-service count as a bounded context?** Yes: it has its own model of the
  business (risk, velocity, recommendation), its own data, its own domain rule
  (`RiskAssessmentPolicy`), and it talks to the others only through events. Dr Su confirmed
  that a CQRS read side still carries a domain layer, so it now has one (ADR 0006).
- **Where is the SQL?** The coordinator confirmed in writing that the Kafka Streams DSL is
  the expected way to implement SQL-like analysis. Section 3.5 of the report writes each
  query both ways: `groupByKey().windowedBy(...)` beside the `GROUP BY ... WINDOW` it stands for.
- **Why event time and not processing time?** Because the velocity must be the same whether
  the events are replayed at a different speed, and because a late event must land in the
  window it belongs to.
- **Why is the agent's REST call to inventory allowed when sales may not call pricing?**
  The agent's call is a read of a fact (batch state) inside a tool, with a 404 path and no
  write; the forbidden call would have put a price decision on a synchronous dependency.
- **What happens if the LLM is down?** The service starts, FR12 and FR13 keep running, the
  trigger is logged as not deliberated, and FR15 answers 503. Nothing in inventory, sales or
  pricing depends on the model.
- **What did AI get wrong?** The perl script that emptied seven files; a nested configuration
  that lost its profile guard; a timestamp serialised as an epoch number on one topic. Each
  is in `docs/ai-log/` with how it was caught.
- **Who did what?** Answer from the cover page; every member must be able to explain the
  part they present.

## Slides to produce (reuse the Week 8 deck's style; it scored 3/3)

Title; Problem; Idea; Bounded contexts + context map (`docs/diagrams/context-map.png`);
Architecture (`A2-microservice-architecture.png`) + event flow (`event-flow.png`); Layers +
DDD (`layered-architecture.png`, one snippet of `MarkdownPlan.activate`); Stream processing
(topology snippet + the DSL-to-SQL table); Agents (tool loop diagram from `activity-UC-11.png`,
trace JSON); Demo; AI-driven development (the four phases, one accepted/rejected/modified
example); Limitations and lessons; Thank you.
