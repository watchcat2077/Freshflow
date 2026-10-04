# Section 2.1 — Use Cases

FreshFlow, Group 06. Twelve use cases, one per functional requirement selected for the
report (ruling of 4 October 2026). Each use case is written at the level of what the
system does for its actor, not how the code does it. The numbered main flow is the
straight path of the matching UML activity diagram; each alternative flow is a branch off
a decision in that diagram. Objectives O1 to O5 are the ones stated in Section 1.

| Objective | Statement |
|---|---|
| O1 | Reduce the value of perishable stock written off at expiry |
| O2 | Protect gross margin: no automated discount may sell below the margin floor |
| O3 | Remove the judgement burden from floor staff by surfacing which batches need action |
| O4 | Make every markdown decision auditable, whether a person or an agent made it |
| O5 | Give the store owner end-of-day visibility without a dashboard |

Actors: **Store manager** (receives deliveries), **Checkout operator** (scans items at a
till), **Shift supervisor** (checks stock and activates markdowns), **Store owner** (sets
price rules and reads briefings), **Markdown Decision Agent** and **Briefing Agent** (the
two LLM-backed agents inside insight-service), and the four services themselves where one
service reacts to another's event.

---

## UC-01 — Register a goods receipt (FR1)

| | |
|---|---|
| Actor | Store manager |
| Relevance | O1. Expiry risk can only be tracked at the level of the physical batch, because two deliveries of the same product expire on different days |
| Preconditions | inventory-service is running |
| Trigger | A delivery arrives and the manager records it |

**Main flow**
1. The store manager submits the delivery: SKU, quantity, expiry date, unit cost, and the supplier's reference.
2. The system checks that every required field is present.
3. The system checks that the quantity is positive.
4. The system checks that the expiry date is in the future.
5. The system creates a stock batch with a new batch identifier, remaining quantity equal to the received quantity, and status ACTIVE.
6. The system publishes a `batch-received` event carrying the batch identifier, SKU, quantity, expiry and unit cost.
7. The system returns the batch, including its identifier, with status 201.

**Alternative flows**
- 2a. A required field is missing: the system answers 400 with the field named, and creates nothing.
- 3a. The quantity is zero or negative: the system answers 400 `QUANTITY_NOT_POSITIVE`, and creates nothing.
- 4a. The expiry date is in the past: the system answers 400 `EXPIRY_IN_PAST`, and creates nothing.

**Postconditions** One ACTIVE batch exists; one `batch-received` event has been published; insight-service's batch-state view will include the batch.

---

## UC-02 — Deduct stock on sale (FR2)

| | |
|---|---|
| Actor | sales-service (as the producer of `item-sold`); no human |
| Relevance | O1, O3. Remaining quantity must be accurate without manual counting, and the batch closest to expiry must be the one that sells first |
| Preconditions | At least one ACTIVE batch of the SKU exists |
| Trigger | An `item-sold` event arrives on Kafka |

**Main flow**
1. The system receives an `item-sold` event with its event identifier, SKU and quantity.
2. The system checks whether the event identifier has already been consumed.
3. The system loads the ACTIVE batches of the SKU in order of soonest expiry first (FEFO).
4. The system plans the deduction across those batches, taking from the earliest-expiring batch until the quantity is covered.
5. The system reduces each affected batch; a batch that reaches zero becomes DEPLETED.
6. The system records the event identifier as consumed, in the same transaction as the deduction.
7. The system publishes one `stock-level-changed` event per affected batch.

**Alternative flows**
- 2a. The event identifier was consumed before (a redelivery): the system ignores the event and deducts nothing. Nothing is published.
- 3a. No ACTIVE batch exists for the SKU: the system records the event as consumed and logs that the sale could not be matched to stock; nothing is deducted.
- 4a. The quantity exceeds all remaining stock: the system deducts everything available, marks those batches DEPLETED, and logs the shortfall.

**Postconditions** Remaining quantities reflect the sale; the event cannot be applied twice; insight-service's batch-state view is updated by the published events.

---

## UC-03 — Query stock position and remaining shelf life (FR4)

| | |
|---|---|
| Actor | Shift supervisor; also the Markdown Decision Agent through its `getBatchDetails` tool |
| Relevance | O3, O4. Staff need to verify what the system tells them, and the agent needs the same facts |
| Preconditions | inventory-service is running |
| Trigger | The supervisor asks for a batch by identifier, or for all batches of a SKU |

**Main flow**
1. The shift supervisor requests a batch by its identifier.
2. The system loads the batch.
3. The system computes the remaining shelf life in whole hours from the current time to the expiry.
4. The system returns the batch with its received and remaining quantities, expiry, unit cost, status and remaining shelf life.

**Alternative flows**
- 1a. The supervisor asks for a SKU instead: the system returns the ACTIVE batches of that SKU in soonest-expiry order, each with its remaining shelf life; an empty list means no active stock.
- 1b. The SKU parameter is missing or blank: the system answers 400.
- 2a. No batch has that identifier: the system answers 404.

**Postconditions** None; the query changes nothing and publishes nothing.

---

## UC-04 — Record a sale transaction (FR5)

| | |
|---|---|
| Actor | Checkout operator (in practice the POS terminal, simulated by `pos-simulator`) |
| Relevance | O1, O3. Every downstream capability, from stock deduction to sell-through velocity, starts from a recorded sale |
| Preconditions | sales-service holds a price snapshot for each scanned SKU |
| Trigger | A basket is scanned at a till |

**Main flow**
1. The checkout operator submits the transaction: the till identifier, the time of sale, and one or more line items of SKU and quantity.
2. The system checks that the till identifier is present and that there is at least one line item with a positive quantity.
3. For each line item the system prices it (UC-05).
4. The system sums the line totals into the transaction total.
5. The system records the transaction with a new transaction identifier.
6. The system publishes one `item-sold` event per line item, stamped with the producer-assigned time of sale.
7. The system returns the recorded transaction with status 201.

**Alternative flows**
- 2a. The line-item list is empty: the system answers 400 `EMPTY_LINE_ITEMS`.
- 2b. A quantity is below one: the system answers 400 `QUANTITY_NOT_POSITIVE`.
- 3a. A SKU has no price snapshot: the system answers 400 `UNKNOWN_SKU`; no line is priced at zero and nothing is recorded.
- 1a. The time of sale is omitted: the system stamps the current time, never the broker's ingestion time.

**Postconditions** The transaction is stored; one `item-sold` event per line is on Kafka; inventory-service (UC-02) and insight-service (UC-09) react to it.

---

## UC-05 — Apply the price in force at the point of scan (FR6a, with FR6b)

| | |
|---|---|
| Actor | sales-service, during UC-04 |
| Relevance | O2. The till must charge the price the Pricing context authorised, and never a price it computed for itself |
| Preconditions | A `price-rule-published` event for the SKU has been consumed into the local price snapshot |
| Trigger | A line item is priced in UC-04 |

**Main flow**
1. The system looks up the SKU in its local price snapshot, which is fed only by events from pricing-service.
2. The system checks whether an activated markdown's promotion window contains the time of sale.
3. No markdown is in force, so the system records the base price on the line with `markdownApplied` false.

**Alternative flows**
- 1a. The SKU is absent from the snapshot: UC-04 step 3a applies; the sale is refused rather than priced at zero.
- 2a. A markdown is in force at the time of sale (FR6b): the system records the discounted price carried on the `markdown-applied` event, with `markdownApplied` true.
- 2b. The markdown's window has ended: the base price is recorded; no further event is needed for the reversion.

**Postconditions** The line carries the price in force at the moment of scan; sales-service has made no synchronous call to pricing-service.

---

## UC-06 — Maintain price rules and margin floors (FR8)

| | |
|---|---|
| Actor | Store owner |
| Relevance | O2. The margin floor is the business rule that constrains every markdown, including the agent's |
| Preconditions | pricing-service is running |
| Trigger | The owner creates or changes the rule for a SKU |

**Main flow**
1. The store owner submits, for a SKU, the base price, unit cost, category and margin floor ratio.
2. The system checks that every field is present and that both money values are in the same currency.
3. The system checks that the base price is not below the unit cost.
4. The system checks that the margin floor ratio lies between 0 and 1.
5. The system stores the rule, replacing any earlier rule for the SKU.
6. The system publishes a `price-rule-published` event so sales-service's snapshot follows the change.
7. The system returns the rule in force: 201 when created, 200 when replaced.

**Alternative flows**
- 2a. A field is missing or the currencies differ: the system answers 400 and stores nothing.
- 3a. The base price is below cost: the system answers 400 `BASE_PRICE_BELOW_COST`.
- 4a. The ratio is out of range: the system answers 400 `MARGIN_FLOOR_OUT_OF_RANGE`.
- 1a. The owner only reads the rule: the system returns the rule with its margin floor, the effective price, and the active discount tier if a markdown is in force.

**Postconditions** The rule is stored in pricing-service alone; sales-service learns of it only through the published event.

---

## UC-07 — Create and activate a markdown plan (FR9)

| | |
|---|---|
| Actor | Shift supervisor (origin HUMAN) or the Markdown Decision Agent through its `markdown-recommended` event (origin AGENT) |
| Relevance | O1, O2. A markdown clears stock before expiry, and only takes effect at the till once the Pricing context has activated it |
| Preconditions | A price rule exists for the SKU (UC-06) |
| Trigger | A markdown is proposed for a batch at a discount tier with a promotion window |

**Main flow**
1. The proposer submits the batch, SKU, discount tier, promotion window, origin and rationale.
2. The system checks that every field is present, that the tier is one of the four, and that the window ends after it starts.
3. The system loads the price rule for the SKU.
4. The system asks the margin floor policy to assess the tier against the rule (UC-08).
5. The assessment passes, so the plan becomes ACTIVE with the discounted price the policy computed.
6. The system stores the plan.
7. The system publishes a `markdown-applied` event carrying the plan, its window and the discounted price.
8. The system returns the ACTIVE plan with status 201.

**Alternative flows**
- 2a. A field is missing or the window is inverted: the system answers 400 and stores nothing.
- 3a. No price rule exists for the SKU: the system answers 404; there is no floor to check against.
- 5a. The assessment is breached: UC-08 takes over; the plan is stored as REJECTED and the system answers 422.
- 1a. The proposal arrives as a `markdown-recommended` event: the same steps run with origin AGENT and the agent's trace identifier attached; the outcome is a stored plan rather than an HTTP answer, and a redelivered event with a trace identifier already seen is ignored.

**Postconditions** An ACTIVE plan exists; sales-service applies the discounted price at the next scan inside the window (UC-05); the price query shows the active tier (UC-06).

---

## UC-08 — Reject a markdown that breaches the margin floor (FR11)

| | |
|---|---|
| Actor | pricing-service's domain layer, on behalf of the store owner; the proposer may be a person or the agent |
| Relevance | O2, O4. This is the rule that makes the agent safe to run: an LLM may propose, but a deterministic rule decides, and the refusal is kept |
| Preconditions | UC-07 has reached its assessment step |
| Trigger | A discount tier is assessed against a price rule |

**Main flow**
1. The margin floor policy computes the resulting price: the base price reduced by the tier's percentage, rounded to cents.
2. The policy computes the resulting margin ratio: the resulting price less the unit cost, divided by the resulting price.
3. The policy compares the ratio with the SKU's margin floor.
4. The ratio is below the floor, so the plan records itself REJECTED with a reason naming the tier, the resulting margin and the floor.
5. The system stores the rejected plan.
6. The system answers 422 with the machine-readable rejection: the SKU, requested tier, resulting price, unit cost, resulting margin ratio and the floor.

**Alternative flows**
- 3a. The ratio is at or above the floor: the assessment passes and UC-07 continues at its step 5.
- 1a. The proposal came from the agent: steps 1 to 5 are identical; there is no HTTP answer, and the rejected plan is the auditable record of what the agent asked for and why it was refused.

**Postconditions** No price changed; no `markdown-applied` event was published; the rejected plan can be read back by its identifier.

---

## UC-09 — Observe the rolling sell-through velocity of a SKU (FR12, real-time analysis)

| | |
|---|---|
| Actor | Shift supervisor; also the Markdown Decision Agent through its `getSellThroughRate` tool |
| Relevance | O1, O3. Whether a batch will clear depends on how fast it is selling now, compared with normal |
| Preconditions | insight-service's stream topology is running against Kafka; a velocity baseline has been seeded for the SKU |
| Trigger | `item-sold` events arrive; a supervisor asks for the current velocity |

**Main flow**
1. The system consumes each `item-sold` event and assigns it to time windows by the producer's `occurredAt`, never by the time Kafka received it.
2. The system groups events by SKU and sums the quantity sold in each hopping window (60 seconds wide, advancing every 5 seconds in the demonstration configuration).
3. The system joins the windowed total with the SKU's baseline velocity held in a table.
4. The system keeps the latest fully elapsed window per SKU in a queryable state store, with the velocity per hour and its ratio to the baseline.
5. The supervisor requests the velocity for a SKU, and the system answers from the state store with the window bounds, quantity sold, velocity and ratio.

**Alternative flows**
- 5a. No window has closed for the SKU yet, or the last one has aged out of retention: the system answers 404.
- 1a. An event arrives late but inside the grace period: it is counted in the window its `occurredAt` belongs to.

**Postconditions** None beyond the state store; the query is read-only. Each window closing also triggers UC-10 for the SKU's batches.

---

## UC-10 — Detect a batch at risk of expiring unsold (FR13, real-time analysis)

| | |
|---|---|
| Actor | insight-service's stream topology; the shift supervisor reads the result |
| Relevance | O1, O3. This is the moment the system turns data into a decision prompt: which batches need attention now |
| Preconditions | UC-09 is producing velocities; `batch-received` and `stock-level-changed` events have built the batch-state table |
| Trigger | A velocity window closes for a SKU |

**Main flow**
1. The system maintains a table of live batch state per SKU from `batch-received` and `stock-level-changed`.
2. When a velocity window closes, the system joins the velocity with every ACTIVE batch of that SKU.
3. For each batch the risk policy projects the sales until expiry: velocity per hour multiplied by the trading hours remaining.
4. The policy computes the projected shortfall: remaining quantity less projected sales.
5. The shortfall exceeds the threshold, the batch has stock left, and it has not yet expired, so the batch is marked at risk in the risk view.
6. On the transition into risk the system publishes a `sell-through-risk-detected` event, which triggers UC-11.
7. The supervisor lists current risks and the system returns them, largest shortfall first.

**Alternative flows**
- 5a. The shortfall is within the threshold: the batch is recorded as not at risk and no event is published.
- 5b. The batch has already expired: it is not a risk the system can act on; it is left to write-off and no event is published.
- 2a. The SKU has stopped selling entirely: no window closes, so the batch is not re-assessed. This is known limitation G-4 and is stated in the report.

**Postconditions** The risk view reflects the latest assessment; at most one trigger event per transition into risk.

---

## UC-11 — Recommend a markdown for an at-risk batch (FR14, agentic AI)

| | |
|---|---|
| Actor | Markdown Decision Agent; the shift supervisor and auditor read its trace |
| Relevance | O1, O2, O3, O4. The agent gathers evidence and proposes; it never sets a price, so O2 is protected by UC-08 and not by the prompt |
| Preconditions | An LLM API key and model are configured for insight-service; inventory-service is reachable for the batch lookup |
| Trigger | A `sell-through-risk-detected` event arrives |

**Main flow**
1. The system receives the trigger and checks that its event identifier has not been deliberated before.
2. The orchestrator hands the trigger to the agent with three tools: `getBatchDetails`, `getSellThroughRate` and `proposeMarkdown`.
3. The agent calls `getBatchDetails` for the flagged batch and receives its remaining quantity, expiry and unit cost over a synchronous request to inventory-service.
4. The agent calls `getSellThroughRate` for the SKU and receives the current velocity and baseline ratio.
5. The agent calls `proposeMarkdown` once, naming the flagged batch, a discount tier, a start time and a one or two sentence rationale citing the numbers it saw.
6. The orchestrator checks that the proposal names the flagged batch and no other.
7. The orchestrator derives the end of the promotion window from the batch's expiry.
8. The orchestrator persists the trace: every tool call in order with its arguments and result, the final recommendation and the rationale.
9. The orchestrator publishes a `markdown-recommended` event, which pricing-service consumes in UC-07.

**Alternative flows**
- 1a. The trigger was deliberated before (a redelivery): nothing is deliberated and nothing is published.
- 5a. The agent decides no markdown is warranted and says why: the trace is persisted with `NO_MARKDOWN` and nothing is published.
- 6a. The proposal names a different batch: the orchestrator refuses it, records the trace as declined, and publishes nothing (ruling R7).
- 7a. The batch cannot be resolved to derive the window: the trace records `PROPOSED_BUT_UNRESOLVABLE` and nothing is published (ruling R9).
- 2a. A tool answers with an error: the agent reads it, corrects its call and tries again, within a bounded number of steps.
- Pre. No LLM is configured: the trigger is logged as not deliberated and dropped; the service keeps serving UC-09 and UC-10.

**Postconditions** An auditable trace exists and is readable at `GET /api/insight/agent-traces/{id}`; at most one recommendation per trigger has reached pricing-service.

---

## UC-12 — Produce the end-of-day briefing (FR15, agentic AI)

| | |
|---|---|
| Actor | Store owner; the Briefing Agent does the work |
| Relevance | O5, O4. The owner reviews the day in plain language, and the briefing can be checked against the evidence it was written from |
| Preconditions | An LLM API key and model are configured for insight-service |
| Trigger | The owner asks for the briefing for a business date (today by default) |

**Main flow**
1. The store owner requests a briefing, optionally naming the business date.
2. The orchestrator hands the request to the briefing agent with three read-only tools: `listCurrentRisks`, `listRecommendations` for the date, and `getSellThroughRate` for any SKU.
3. The agent calls `listCurrentRisks` and receives the batches currently at risk.
4. The agent calls `listRecommendations` and receives the markdown recommendations made that day with their outcomes.
5. The agent may call `getSellThroughRate` for SKUs it wants to comment on.
6. The agent writes a short plain-language summary of what was marked down, what is selling through, and what is still at risk, stating that write-off figures are not available because write-offs are not recorded.
7. The orchestrator counts the risks and recommendations from the tool results itself, so the numbers in the response do not depend on the model's text.
8. The orchestrator persists the briefing with its ordered tool calls.
9. The system returns the briefing with status 201.

**Alternative flows**
- Pre. No LLM is configured: the system answers 503 `AGENT_NOT_CONFIGURED`.
- 3a. There are no risks and no recommendations: the agent says the day needed no markdown action; the counts are zero.
- 1a. The owner later asks for a stored briefing by its identifier: the system returns it with its tool calls, or 404.

**Postconditions** A briefing with its evidence is stored and readable; nothing in any other context has changed.

---

### Deliberately out of scope

FR3 (write off an expired batch), FR6b's `markdown-expired` consumption, FR7 (transaction
history) and FR10 (automatic markdown expiry) are specified in spec-02 and were not built.
They would add reporting and housekeeping rather than demonstrate a further architectural
pattern, and the time went to the twelve above. Their absence is stated where it matters:
UC-12 says that waste figures are unavailable, and UC-05 reverts to the base price at the
end of a promotion window without needing FR10's event.
