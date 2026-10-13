# Specification 6 — Agent Orchestration

**Project**: FreshFlow
**Version**: 1.0 — 10 September 2026; aligned with the code as built on 13 October 2026
**Status**: Authoritative for FR14 and, from 4 October 2026, for FR15 (§11). §2 and §3 were open
when this document was written and were **ruled on by the team on 10 September 2026**. No clause
is open.

---

## 0. Purpose and scope

spec-05 gathered the stream processing that sits between the architecture and the API. This
document does the same job for FR14's Markdown Decision Agent: the type surface, the tool
contracts, the idempotency rule, the trace, and the boundary the agent may not cross.

Unlike spec-05 this document is **not descriptive**. spec-05 was written after the topology it
describes and every rule in it was already tested. This one is written before the implementation,
in the phase that produces the design, the type surface and the failing tests. What it settles now
is settled. Two clauses it could not settle, §2 and §3, were put to the team and ruled on the same
day; each now records the ruling in place of the marker. **Neither was implemented in Phase A** —
the rulings decide what Phase B builds, and Phase B is separately authorised.

Scope: `insight-service` only, and within it FR14 only. FR15's end-of-day briefing is a second
agentic requirement with a different shape and is out of scope here.

> **Amended 4 October 2026.** FR15 is now built, to the same shape as FR14, and is described in
> §11. §§1–10 remain FR14's and are unchanged by it.
>
> **Status at submission, 13 October 2026.** Everything §§1–10 specify for FR14 is built, tested,
> and has run end to end against a live model (`docs/ai-log/2026-10-04-live-run.md`,
> `docs/evidence/`). The Phase wording below is kept as the record of the order in which it was
> built; §8 says what each phase left and when it was done.

Where a rule originates elsewhere the originating clause is cited and remains authoritative.

**Six team rulings govern this document and are not re-opened in it.** Three were taken with it on
10 September 2026; three more were taken on **11 September 2026** on flags Phase B1 raised.

| Ruling | Substance | Recorded in |
|---|---|---|
| R1 | The LLM sits behind a port declared in `application`. Two adapters: a deterministic stub for every test, a LangChain4j adapter for runtime. No test calls a live model or touches the network | ADR 0004 |
| R4 | The MVP tool set is three tools: `getBatchDetails`, `getSellThroughRate`, `proposeMarkdown`. The other three appear nowhere | §4, spec-02 FR14 |
| R5 | The agent proposes only. No price writes, no call to `pricing-service`, and no margin-floor logic anywhere in `insight-service` | §7 |
| R7 | Validating that a proposal names the trigger's batch is the **orchestrator's**, not the tool's. A proposal naming another batch is refused and recorded as declined | §4.3, §5 |
| R8 | The save-then-publish window is a **known limitation**, G-7, not a defect to fix. No transactional outbox is built | §10 |
| R9 | The trace gains a third `finalRecommendation` value, `PROPOSED_BUT_UNRESOLVABLE`, for a proposal that could not be published because the batch could not be resolved | §6 |

Two further rulings were taken on **10 September 2026**, on the two questions this document raised:
the trigger is consumed by a second Spring Cloud Stream function (§2), and `getBatchDetails` reads
unit cost over synchronous REST (§3). Both are recorded in the sections that asked them.

---

## 1. The type surface

Every new type, its package and its layer. `insight-service`'s `domain` package holds
`RiskAssessmentPolicy` and `BatchRiskView` only (ADR 0006, 4 October 2026); the agent has no
business rule to place there, so its types live in `presentation`, `application` and
`infrastructure`, and the ports go in `application` — spec-01 §2.3's port-placement table and
ADR 0002 Decision 2 both say so.

### 1.1 `application`

| Type | Kind | Role | Traced to |
|---|---|---|---|
| `MarkdownDecisionAgent` | interface | The R1 port. One method: `AgentDeliberation deliberate(SellThroughRiskDetectedEvent, AgentToolRegistry)` | ADR 0004; spec-01 §2.3 |
| `AgentDeliberation` | record | What one ReAct run produced: `toolCalls`, `proposal` (nullable), `rationale` | §6 |
| `MarkdownProposal` | record | `batchId`, `discountTier`, `effectiveFrom`, `rationale` — spec-02 FR14's `proposeMarkdown` argument list, exactly | spec-02 FR14 |
| `ToolCall` | record | `sequence`, `toolName`, `arguments`, `result`, `timestamp` — spec-04's `toolCalls` item, exactly | spec-04 |
| `AgentTraceRecord` | record | spec-04's `AgentTraceResponse`, field for field | spec-03 §4; spec-04 |
| `AgentTraceStore` | interface | Outbound port. `save`, `findByAgentTraceId`, `findByTriggerEventId` | spec-01 §2.3; ADR 0002 |
| `BatchDetailsProvider` | interface | Outbound port for `getBatchDetails`. Returns `Optional<BatchDetails>` | §3 |
| `BatchDetails` | record | `batchId`, `sku`, `remainingQuantity`, `expiryAt`, `unitCost` | §3; spec-02 FR14 |
| `MarkdownRecommendationPublisher` | interface | Outbound port. `void publish(MarkdownRecommendedEvent)` | spec-01 §2.6 Table A |
| `AgentToolRegistry` | class | The three tools and nothing else. `Set<String> toolNames()`, `String invoke(String, String)` | R4; §4 |
| `MarkdownAgentOrchestrator` | class | The application service. `AgentTraceRecord onRiskDetected(SellThroughRiskDetectedEvent)` | spec-01 §2.1 |

Three types were added to the eight names the authorisation proposed, each because a signature
needed one and none carrying behaviour:

- **`AgentDeliberation`** is the port's return type. Without it the port would have to return an
  `AgentTraceRecord`, which would mean the LLM adapter minting the trace identifier and stamping
  `completedAt` — putting two facts that must be reliable on the far side of the least reliable
  boundary in the system.
- **`BatchDetails`** is what `BatchDetailsProvider` returns. §3 depends on this shape being fixed
  independently of where the data comes from.
- **`DiscountTier`** is an enum in the `contracts` module, not in `insight-service`. spec-04
  enumerates exactly four tiers; a `String` on `MarkdownRecommendedEvent` could not hold the
  contract, and the producer of that topic is an agent driven by a language model. See §8 for the
  note that spec-01 §2.4 also lists `DiscountTier` as a Pricing value object — these are two
  different types by design, because spec-03 §6 rules out a shared kernel.

### 1.2 `infrastructure`

| Type | Kind | Role |
|---|---|---|
| `AgentTraceEntity` | JPA `@Entity` | `agent_trace`. Unique constraint on `trigger_event_id` (§5) |
| `ToolCallEntity` | JPA `@Entity` | `agent_tool_call`. `@OrderBy("sequence ASC")` from the parent (§6) |
| `AgentTraceJpaRepository` | interface | Spring Data, package-private. Not the port |
| `AgentTraceMapper` | final class | Entity ↔ record. Package-private, static methods, as `StockBatchMapper` is |
| `JpaAgentTraceStore` | `@Repository` | The adapter implementing `AgentTraceStore` |

Added in Phase B2, 11 September 2026:

| Type | Kind | Role |
|---|---|---|
| `LangChain4jMarkdownDecisionAgent` | class | The R1 adapter. The ReAct loop over a LangChain4j `ChatModel` |
| `MarkdownToolArguments` | final class | Package-private. Translates the model's JSON tool arguments into the flat encoding `AgentToolRegistry.invoke` reads, and back. Pinned by `MarkdownToolArgumentsTest` |
| `RestBatchDetailsProvider` | class | The §3 adapter. Synchronous `GET /api/batches/{batchId}` against `inventory-service` |
| `StreamBridgeMarkdownRecommendationPublisher` | class | `StreamBridge` onto `markdown-recommended`, in the shape `VelocityBaselineSeeder` already uses |
| `MarkdownAgentConfiguration` | `@Configuration` | FR14's wiring, including the `markdownAgent` consumer of §2. `@Profile("!test")` |
| `LlmProperties` | `@ConfigurationProperties` | `freshflow.llm` — spec-01 §2.7 |
| `InventoryClientProperties` | `@ConfigurationProperties` | `freshflow.insight.inventory` — spec-01 §2.7 |

The entity and the record are two types with a mapper between them because spec-01 §2.2 forbids
`application` from importing JPA. This is the same construction `inventory-service` uses for
`StockBatch`, applied to a read model rather than to an aggregate; R3 requires it explicitly.

**`dev.langchain4j` reaches exactly two of these**, the adapter and the configuration that builds
the client. No type under `application` imports it, which is ADR 0004's first verification check.

### 1.3 `presentation`

| Type | Kind | Role |
|---|---|---|
| `AgentTraceController` | `@RestController` | spec-04 `getAgentTrace` — `GET /api/insight/agent-traces/{agentTraceId}` |
| `AgentTraceResponseDto` | record | spec-04's `AgentTraceResponse`, with `ToolCall` nested as spec-04 nests it |

### 1.4 `src/test/java`

| Type | Role |
|---|---|
| `StubMarkdownDecisionAgent` | The deterministic agent every test uses (R1, ADR 0004) |
| `InMemoryAgentTraceStore` | Test adapter for `AgentTraceStore` |
| `RecordingMarkdownRecommendationPublisher` | Test adapter that keeps what it was asked to publish |
| `StubBatchDetailsProvider` | Test adapter for `BatchDetailsProvider` (§3) |
| `InsightTestAgentTraceConfiguration` | Test-profile `@Primary` `AgentTraceStore`, seeded, beside `InsightTestVelocityConfiguration` |

Added in Phase B2, 11 September 2026:

| Type | Role |
|---|---|
| `MarkdownToolArgumentsTest` | Pins the tool-call encoding B1 recorded as load-bearing and unasserted |
| `MarkdownAgentScopeAndResolutionTest` | Pins R7's scope refusal and R9's third trace value |

**The stub is in test sources, and it is the only `MarkdownDecisionAgent` any test sees** (R1,
ADR 0004).

**Corrected 11 September 2026.** This clause previously read "so that LangChain4j never reaches the
test classpath", and after Phase B2 that is no longer the mechanism, because a compile-scope
dependency is on the test classpath by Maven's own rules. **The guarantee is unchanged and its
enforcement moved**, from one accident of packaging to three things a reviewer can check:

1. No test source references `dev.langchain4j` — `grep -r "dev.langchain4j" insight-service/src/test/`
   returns nothing, which is ADR 0004's second verification check, unweakened.
2. Everything that could construct a model is in `MarkdownAgentConfiguration`, annotated
   `@Profile("!test")`, and every test in this service runs under `@ActiveProfiles("test")`.
3. The model bean is additionally conditional on a non-empty `freshflow.llm.api-key`, so it cannot
   be built on a machine with no credential. `mvn test` passes with `LLM_API_KEY` and `LLM_MODEL`
   both unset, which is the property that check was always for.

The first is the one that matters: a test cannot resolve a model it has no way to name.

---

## 2. How the trigger is consumed — **RULED 10 September 2026**

> **Ruling — option A, approved 10 September 2026.** `sell-through-risk-detected` is consumed by a
> second Spring Cloud Stream function, a `Consumer<SellThroughRiskDetectedEvent>` bound through the
> **message-channel** Kafka binder, giving
> `spring.cloud.function.definition: insightTopology;markdownAgent` in `application.yml` with
> `application-test.yml`'s `""` unchanged.
>
> **Reasoning**: it is the only option that keeps the LLM call off a Kafka Streams thread and
> leaves the FR12/FR13 topology's stages, stores and `application.id` untouched, while staying
> inert under the test profile without a second profile.

FR14 is "triggered by `sell-through-risk-detected`". That topic is currently `insightTopology-out-0`
— an *output* of the FR12/FR13 topology (spec-05 §1.1). Something must now consume it.

**Four constraints bound the answer, and together they eliminate three of the four options.**

1. The FR12/FR13 topology's stages, state stores and `application.id` must not change. spec-05 is
   descriptive of a tested topology and a change to `application.id` discards every state store.
2. An LLM call must not run on a Kafka Streams thread. A call taking seconds on a StreamThread
   stalls `poll`, and exceeding `max.poll.interval.ms` triggers a rebalance — in the middle of the
   windowed aggregate FR12 depends on.
3. `application-test.yml` sets `spring.cloud.function.definition: ""`, and whatever is chosen must
   stay inert under the test profile **without a second profile**. The 41 existing tests run with
   no broker; a consumer that tried to connect would take them all down.
4. spec-01 §1.1 names Spring Cloud Stream as the event-binding technology. Reaching past it is a
   dependency-level decision, not a wiring convenience.

| Option | Mechanism | Verdict |
|---|---|---|
| **A** | A second Spring Cloud Stream function — `Consumer<SellThroughRiskDetectedEvent>`, bound through the **message-channel** Kafka binder, calling `MarkdownAgentOrchestrator.onRiskDetected` | **Ruled** |
| B | A `@KafkaListener` on the topic | Rejected — bypasses Spring Cloud Stream (constraint 4), and `spring.cloud.function.definition: ""` does not make it inert, so it would need a second profile or a property guard (constraint 3) |
| C | A terminal `peek`/`foreach` on the existing topology's output branch | Rejected — runs the LLM on a StreamThread (constraint 2) and changes the topology (constraint 1) |
| D | A fifth input binding on `insightTopology`, re-reading the topic | Rejected — changes the curried signature and the topology (constraint 1), and is still a StreamThread (constraint 2) |

**Why A satisfies all four.** It runs on a binder consumer thread, so the Streams threads are
untouched and a slow model delays only the agent. It adds a function beside the topology rather
than inside it, so no stage, store or `application.id` moves. It is Spring Cloud Stream, which
spec-01 §1.1 names. And both binders already coexist in this service — `velocityBaseline-out-0`
is a message-channel producer alongside the Kafka Streams topology (spec-05 §5.1) — so this is a
pattern the service already runs, not a new one.

**The resulting configuration value.**

| File | `spring.cloud.function.definition` |
|---|---|
| `insight-service/src/main/resources/application.yml` | `insightTopology;markdownAgent` |
| `insight-service/src/test/resources/application-test.yml` | `""` — **unchanged** |

The semicolon composes two independent functions; Spring Cloud Stream routes each to the binder its
signature implies. Under the test profile the definition is empty, so neither binds, and no test
needs a broker or a second profile. That property is why option A is recommended rather than merely
possible.

**Two consequences to carry into Phase B.** The consumer blocks a binder thread for the duration of a
model call, so its binding will want its own `concurrency` and a considered `max.poll.interval.ms`;
and the trigger topic is consumed by the same service that produces it, which is unusual but is
exactly what spec-01 §2.6 anticipated when it kept `sell-through-risk-detected` in Table A.

**Not implemented in Phase A.** No `application.yml` change and no consumer bean was written; the
ruling above decides what Phase B builds, and Phase B is separately authorised. The orchestrator's
`onRiskDetected` is the entry point either way — nothing above changes its signature, and no test
depends on the answer.

---

## 3. Where `getBatchDetails` obtains unit cost — **RULED 10 September 2026**

> **Ruling — option (b), approved 10 September 2026.** `getBatchDetails` obtains unit cost from a
> synchronous `GET /api/batches/{batchId}` against `inventory-service`, behind the
> `BatchDetailsProvider` port.
>
> **Reasoning**: spec-01 §1.3 permits synchronous REST for agent tool calls and for nothing else,
> spec-01 §1.4 repeats the permission and spec-04 already names that endpoint as backing this tool
> — three clauses anticipate this exact call, and it costs the FR13 topology nothing.

spec-02 FR14 says `getBatchDetails(batchId)` returns "remaining quantity, expiry timestamp, unit
cost". The first two exist in this service. **The third does not exist anywhere in it.**
`BatchReceivedEvent` carries `unitCost`, but `SkuBatchStates.BatchState` (spec-05 §3.1) keeps only
`batchId`, `sku`, `expiryAt`, `remainingQuantity` and `updatedAt`, so the cost is dropped at the
first stage of the fold.

| Option | Mechanism | Cost |
|---|---|---|
| **a** | Carry `unitCost` through the batch-state `KTable` | Changes the value type of the `batch-states` store, which is a state-store schema change under a running `application.id` (constraint 1 of §2 in spirit). `stock-level-changed` carries no `unitCost`, so the merge needs a **parallel treatment to the one `expiryAt` already gets** — cost established by `batch-received`, preserved across every stock update — and spec-05 §3.1 calls that encoding deliberate, so a second field riding on the same trick makes it less legible, not more. It also puts data into the FR13 join path that FR13 never reads |
| **b** | Synchronous `GET /api/batches/{batchId}` against `inventory-service` | **Ruled.** Adds a network hop to the agent run and makes `inventory-service` availability a dependency of the agent — but not of the topology, which keeps running either way. Needs an HTTP client, which `spring-web` already supplies, so no new dependency |
| c | Drop `unitCost` from the MVP tool | Deviates from spec-02 FR14's stated return, and costs the agent the one number that makes a discount tier a commercial judgement rather than a percentage picked in the abstract |

**Why (b) was chosen.** spec-01 §1.3 permits synchronous REST "ONLY for agent tool calls from
`insight-service` to the other services". This *is* an agent tool call — the single case the clause
was written for, and leaving it unused while inventing a stream-borne alternative would be odd.
spec-01 §1.4 repeats the permission, and spec-04 already documents the endpoint as
"Implements FR4. **Also backs the `getBatchDetails` agent tool (FR14)**". Three clauses anticipate
this exact call. There is also a substantive advantage: a REST read returns the batch as it is
**now**, whereas the batch-state table holds it as of the last event the topology folded, and an
agent deciding what to discount should see current stock.

**Unit cost is evidence, not enforcement.** Returning the cost is not margin-floor logic and does
not weaken R5. The agent is given the number so its written rationale can be about a real
commercial situation; §7 is what forbids this service from *checking* anything against it.

**The ruling was deferrable, and that was a design property rather than a hope.**
`BatchDetailsProvider` returns `batchId`, `sku`, `remainingQuantity`, `expiryAt` and `unitCost`
regardless of source. Every test runs against `StubBatchDetailsProvider`, so no test asserts
anything about where the data comes from. **The ruling therefore decides only which adapter is
written in Phase B, and no test changes either way.**

That is ADR 0001's argument used a third time — after `PriceSnapshotFeed` and after ADR 0003's
velocity baseline. Worth naming as a pattern now rather than as three coincidences: a port at every
boundary whose implementation is genuinely undecided is what has let this project settle questions
late without rewriting what depends on them.

---

## 4. Tool contracts

Three tools (R4). `getMarkdownHistory`, `getMarginFloor` and `queryStorePolicy` appear nowhere —
not in the registry, not in the port, not in the prompt text. Two of those absences are
architectural rather than scheduling: `getMarginFloor` would put the Pricing context's invariant in
front of a language model, which spec-01 §2.5 forbids in terms, and `queryStorePolicy` needs
retrieval infrastructure no specification has approved.

A tool's result is a `String`, because spec-04 types `ToolCall.result` as one and because that is
what a ReAct loop hands back to a model. The rendering is one line of `key=value` pairs: stable
enough to assert, plain enough to read in a trace.

### 4.1 `getBatchDetails(batchId)`

| | |
|---|---|
| Reads through | `BatchDetailsProvider` (§3) |
| Returns | `batchId=… sku=… remainingQuantity=… expiryAt=… unitCost=… <currency>` |
| Data absent | `error=BATCH_NOT_FOUND batchId=…` |

### 4.2 `getSellThroughRate(sku)`

| | |
|---|---|
| Reads through | the existing `VelocityQueryService.findCurrentVelocity(String)` |
| Returns | `velocityPerHour=…`, plus `baselineVelocity=… velocityRatio=…` when a baseline is stored |
| `Optional` empty | `error=NO_CURRENT_VELOCITY sku=…` |

**What the empty `Optional` means, and why it is not zero.** spec-02 FR12's ruling 2 (spec-05 §2.4)
says an empty result covers two cases: no window has closed yet, and the newest closed window is
older than one window length. Neither is "the SKU is selling at zero". Rendering it as
`velocityPerHour=0.0` would tell the agent that nothing is moving, which is a fabricated
measurement of exactly the kind FR12's 404 exists to prevent — and it would push the agent towards
the deepest discount on the least evidence. `error=` says what is true: the rate is unknown.

**No baseline is likewise not a ratio of zero.** Same rule as spec-01 §2.7 and spec-05 §5.1 apply
to the endpoint: the fields are omitted, never fabricated.

### 4.3 `proposeMarkdown(batchId, tier, effectiveFrom, rationale)`

| | |
|---|---|
| Validates | `batchId` is a batch this service can see; `tier` parses as a `DiscountTier`; `effectiveFrom` parses as an `Instant`; `rationale` is non-blank |
| Returns | `recorded batchId=… tier=…` |
| Invalid arguments | `error=INVALID_ARGUMENTS …` — a tool failure the agent may correct, not an exception |
| Publishes | **Nothing.** See §5 |
| Scope | **Not checked here.** That the `batchId` is the *trigger's* batch is the orchestrator's check — R7, below |

> **Ruling R7 — approved 11 September 2026.** The check that a proposal names **the trigger's**
> batch belongs to `MarkdownAgentOrchestrator`, not to this tool. The orchestrator refuses to
> publish, and records as declined, any proposal whose `batchId` is not the trigger's.
>
> **Why the tool cannot do it.** `AgentToolRegistry` has no trigger. Its constructor is
> `(BatchDetailsProvider, VelocityQueryService)` and is pinned by two test files. This clause
> previously stated the check as the tool's and Phase B1 found it unimplementable there; the tool
> checks what it can — that the batch exists — and reports `error=INVALID_ARGUMENTS` otherwise.
>
> **Why the orchestrator can.** It is the only object holding both the trigger and the proposal.
> This is the second application of the argument that already moved publication out of the tool
> (§5): **the tool stays stateless, the orchestrator holds the trigger context**, so anything
> needing that context is the orchestrator's.
>
> **It is a second boundary beside R5, and they are the same kind of rule.** R5 says the agent may
> not write prices. R7 says the agent may not propose outside the batch it was woken for. Both are
> enforced in ordinary code on the near side of the port, because both are statements about what
> the system does with a model's output rather than requests to the model to behave.
>
> **What it prevents is an audit record that contradicts itself**, not a wrong price. The
> orchestrator publishes the *trigger's* `batchId` (Phase B1 assumption A4), so a wrong batch could
> never have been marked down; what could happen is a trace whose recorded proposal names one batch
> and whose published event names another. The refusal stays visible: the `proposeMarkdown` call
> the agent made is still recorded, with the result the tool gave it.

**Two deviations from spec-02 FR14's tool table are recorded here, and neither is acted on beyond
what this section states.**

1. **The table writes `getSellThroughRate(sku, window)`; the tool takes `sku` only.** The window is
   configuration (`freshflow.insight.velocity.window-seconds`, spec-01 §2.7) and *which* window
   counts as current was settled by team ruling in spec-02 FR12 — there is no choice left for a
   caller to express. A `window` argument would be an argument the implementation must ignore, or
   else a second window-selection rule beside the ratified one.
2. **The table's `proposeMarkdown` has no `effectiveTo`; `markdown-recommended` requires one**
   (spec-01 §2.6 Table A). The gap is real. It is resolved by deriving `effectiveTo` from the
   batch's `expiryAt`: a markdown meant to clear a batch before it expires has no work left to do
   afterwards. The derivation is done by `MarkdownAgentOrchestrator`, not asked of the model,
   because it is a policy rather than a judgement — and because a window end the model could choose
   is a window end the model could choose badly.

Both are flagged for the team in `docs/ai-log/2026-09-10-fr14-phase-a.md`.

---

## 5. Idempotency

**The rule.** One trigger `eventId` yields **at most one trace** and **at most one
`markdown-recommended` publication**.

**Where it is keyed.** On `SellThroughRiskDetectedEvent.eventId()`, stored on the trace as
`triggerEventId`. `MarkdownAgentOrchestrator.onRiskDetected` looks it up through
`AgentTraceStore.findByTriggerEventId` before anything else happens; a redelivery returns that
trace and does nothing further — no second deliberation, no second trace, no second publication.
This is spec-01 §2.6's "used for idempotent consumption", applied to FR14.

**Three mechanisms, deliberately layered.**

| Mechanism | What it stops |
|---|---|
| The `findByTriggerEventId` check in the orchestrator | The ordinary redelivery, without a database round trip failing |
| `agentTraceId` = `"TRC-" + triggerEventId` | A concurrent double delivery: the two runs collide on the **primary key**, not merely on a secondary constraint. The identifier is also traceable back to its trigger by inspection |
| The unique constraint on `agent_trace.trigger_event_id` | The same collision from any other route. A rule enforced only by a read-then-write is a rule with a race in it |

**Why the publication is the orchestrator's and not the tool's.** spec-02 FR14's table says
`proposeMarkdown` "Publishes `markdown-recommended`". The effect is the same and the actor is not:
the tool records a proposal, and the orchestrator publishes once per trace. Two reasons, and the
first is the one that matters. **How many times a language model chooses to call a tool must not
decide how many events cross into another bounded context.** An agent that called `proposeMarkdown`
twice — a perfectly ordinary thing for a ReAct loop to do while revising itself — would otherwise
put two proposals in front of Pricing for one batch. Second, the event carries `agentTraceId`, and
under a rule where the tool published, the tool would need the trace identifier before the trace
exists.

**Declining publishes nothing.** The trace is still written. FR14 makes the trace the audit record
of the decision, and a decision to leave a batch alone is a decision — the question a supervisor
actually asks is why nothing was done about a flagged batch, and a store that recorded only actions
could not answer it.

**Out-of-scope proposals publish nothing either — R7.** The orchestrator compares the proposal's
`batchId` with the trigger's before anything is published, and a mismatch is recorded as declined.
The reasoning is §4.3's and is the same reasoning that made publication the orchestrator's job in
the first place: the tool is stateless and the orchestrator holds the trigger context, so a check
that needs both values belongs here. It is the third thing this section stops one flagged batch
from turning into something unintended, alongside the redelivery check and the once-per-trace
publication.

---

## 6. The trace

**Capture.** The agent records one `ToolCall` per invocation as it makes it, numbering them from 1
and increasing by one. The numbering is the agent's, not the store's, because it is a fact about
the order the calls were made in.

**Ordering, end to end.** spec-04 declares `sequence` on every call, so a consumer is entitled to
sort by it, and the order must therefore survive every hop:

| Hop | What preserves the order |
|---|---|
| Agent → `AgentDeliberation` | `toolCalls` is built in call order and carries `sequence` |
| Record → database | `AgentTraceEntity` declares `@OrderBy("sequence ASC")` on the collection |
| Database → record | The mapper preserves what it is given and re-orders nothing |
| Record → DTO → JSON | `AgentTraceController` maps and does **not** sort. A controller that re-sorted would hide a store that had lost the order |

`ToolCallEntity` stores `sequence` in a column named `call_sequence`, because `SEQUENCE` is
reserved in SQL and a column of that name fails on some databases and not others. The attribute
keeps spec-04's name.

**`finalRecommendation`.** A short machine-readable summary of the outcome:

| Outcome | Value |
|---|---|
| Proposed, in scope, batch resolved | `<TIER> from <effectiveFrom> until <effectiveTo>` — e.g. `PERCENT_30 from 2026-09-10T09:30:00Z until 2026-09-10T18:30:00Z` |
| Proposed, but the batch could not be resolved | `PROPOSED_BUT_UNRESOLVABLE` — **R9** |
| Declined, or proposed out of scope (R7) | `NO_MARKDOWN` |

> **Ruling R9 — approved 11 September 2026.** A third value, `PROPOSED_BUT_UNRESOLVABLE`, for the
> case where the agent proposed and the proposal could not become a recommendation because
> `BatchDetailsProvider` could not resolve the batch. spec-01 §2.6 Table A requires `effectiveTo`
> on the event and the only source of it is the batch's expiry, so there is no contract-complete
> event to publish.
>
> **The reasoning is this section's own.** §5 says the trace exists because "the question a
> supervisor actually asks is why nothing was done about a flagged batch". Recording "decided not
> to" and "tried and could not" with the same word cannot answer that question. Phase B1 recorded
> the collapse as flag 4.3 — "true of what left this service and misleading about what the agent
> did" — and this is the ruling on it. It costs one constant.

**A note on the third row.** An out-of-scope proposal (R7) is recorded as `NO_MARKDOWN` because the
ruling says so, and it is worth naming the tension: the agent tried there too, so the same argument
that produced R9 could be read as covering it. The cases differ in one respect — an unresolvable
batch is an *environmental* failure this service suffered, and an out-of-scope proposal is a
*refusal* this service made — and the team may wish to revisit whether that difference is worth two
values or three. The tool call the agent made is recorded either way, so no information is lost;
what differs is how quickly a reader sees it.

`rationale` is FR14's written justification, and is present in all three cases. No field here is
ever computed from a price.

**`completedAt`** is wall-clock, from an injected `Clock`, and is the one time field in this
service that legitimately is. spec-01 §2.6's event-time rule governs windowing and the arithmetic
derived from it; when a decision was recorded is a fact about this service's own operation. The
same instant is used as the published event's `occurredAt` — the producer-assigned event time
spec-01 §2.6 requires, assigned by the producer, which here is the orchestrator.

---

## 7. The Anti-Corruption Layer — R5, stated so it can be checked

spec-01 §1.5 makes Pricing an Anti-Corruption Layer against this service: "Pricing independently
validates the margin floor. LLM output is treated as an untrusted proposal, never as an
authoritative command." spec-01 §2.5 places the check in Pricing's domain layer and forbids
delegating any invariant to an LLM prompt. spec-02 FR11 makes the check apply identically whether
the proposal came from a human or from the agent. spec-02 FR14: the agent "proposes only. It MUST
NOT write prices directly."

**The rule for `insight-service`, in full:** no margin-floor logic anywhere in it — not a constant,
not a check, not a sentence in a prompt template — and no call to `pricing-service`, synchronous or
otherwise. The only channel from this service towards Pricing is `markdown-recommended`.

**It is enforced mechanically**, by `AntiCorruptionLayerGuardTest`, which scans every
`.java` file under `insight-service/src/main/java` for a reference to the Pricing packages or
service, to a margin floor in any spelling, or to a Pricing endpoint. Comments are stripped before
the scan and string literals are not: the production code may explain why the rule exists and cite
the clause it comes from, and a prompt template is a string, which is how R5's "not a sentence in a
prompt template" becomes enforceable at all.

**Why a test rather than a design note.** This is the project's central architectural argument, and
an argument that rests on everyone remembering it is an argument that expires. Every other way of
breaching it is loud — a new dependency appears in a `pom.xml`, a new endpoint appears in spec-04 —
but a margin constant added to a prompt string, or a quiet REST call to port 8083, breaks nothing
and no test. This one does.

---

## 8. What Phase A deliberately leaves undone

So that the next authorisation can be a short message. Nothing below was started.

**Both rulings are now in hand** (10 September 2026), so nothing in this table is blocked on the
team any longer. What remains is Phase B, which is separately authorised.

**Status after Phase B2, 11 September 2026.** Every row above the FR9 line is now **done** —
B1 filled the nine method bodies, B2 added the LangChain4j adapter and the ReAct loop, the trigger
binding and the `application.yml` change, the `BatchDetailsProvider` adapter, the
`MarkdownRecommendationPublisher` adapter and the dependency, whose coordinates are now recorded
in spec-01 §1.1 rather than only proposed. The table is left standing as the record of what Phase A
deliberately deferred and to whom; the last two rows, `POST /api/markdown-plans` (FR9) and FR15,
were out of that milestone's scope and were built on 4 October 2026. The end-to-end run against a
live model (the B3 session) was performed on 4 October 2026 and repeated on 5 October
(`docs/ai-log/2026-10-04-live-run.md`, `docs/ai-log/2026-10-05-review-and-hardening.md`,
`docs/evidence/`); `scripts/start-all.ps1` and `scripts/demo.ps1` hold the procedure.

| Left undone | Blocked on |
|---|---|
| The LangChain4j adapter for `MarkdownDecisionAgent` | Nothing but Phase B, plus the dependency coordinates below |
| The ReAct loop body | The adapter |
| `AgentToolRegistry.toolNames()` and `invoke()` bodies | Nothing but Phase B |
| `MarkdownAgentOrchestrator.onRiskDetected` body | Nothing but Phase B |
| `AgentTraceMapper` and `JpaAgentTraceStore` bodies | Nothing but Phase B |
| `AgentTraceController.getAgentTrace` body | Nothing but Phase B |
| The Kafka binding for the trigger, and the `application.yml` change | Nothing but Phase B — §2 ruled |
| The `BatchDetailsProvider` adapter | Nothing but Phase B — §3 ruled |
| The `MarkdownRecommendationPublisher` adapter | Nothing but Phase B |
| Any `pom.xml` change, including the LangChain4j dependency | Phase A adds none by constraint; coordinates are proposed in the Phase A log |
| `POST /api/markdown-plans` (FR9) consuming `markdown-recommended` | The Pricing context, and FR9's own week |
| FR15 | Its own requirement |

**Also noted, not acted on.** spec-01 §2.4 lists `DiscountTier` as a Pricing value object, and this
phase declared a `DiscountTier` enum in `contracts`. These are two different types on purpose:
spec-03 §6 rules out a shared kernel, so the Published Language carries a tier across the boundary
and each context keeps its own model of what a tier *means*. It is recorded here so that a reader
finding two types of one name knows it was decided rather than duplicated.

---

## 9. Traceability

| Requirement or ruling | Realised by | Verified by |
|---|---|---|
| FR14 — agentic markdown recommendation | §1, §4, §5, §6 | `MarkdownAgentOrchestratorTest`, `AgentToolRegistryTest` |
| FR14 — trace persisted and queryable | §6, §1.2 | `JpaAgentTraceStoreTest`, `AgentTraceApiIntegrationTest` |
| spec-04 `getAgentTrace` | §1.3, §6 | `AgentTraceApiIntegrationTest` |
| spec-01 §2.6 — `markdown-recommended` contract | `contracts.MarkdownRecommendedEvent` | `MarkdownRecommendedEventContractTest` |
| spec-01 §2.6 — idempotent consumption | §5 | `MarkdownAgentOrchestratorTest` |
| spec-01 §1.5, §2.5, spec-02 FR11 — Anti-Corruption Layer | §7 | `AntiCorruptionLayerGuardTest` |
| R1 — the LLM behind a port | §1.1, ADR 0004 | ADR 0004's verification checks |
| R4 — three tools only | §4 | `AgentToolRegistryTest` |
| G-6 (spec-05 §7) — `occurredAt` wire format, for this event only | `MarkdownRecommendedEvent` | `MarkdownRecommendedEventContractTest` |
| R2 — two LangChain4j artifacts, one version | Root `pom.xml` `<dependencyManagement>`; spec-01 §1.1 | `mvn dependency:tree` |
| R7 — a proposal names the trigger's batch | §4.3, §5; `MarkdownAgentOrchestrator.inScope` | `MarkdownAgentScopeAndResolutionTest` |
| R8 / G-7 — save-then-publish is a known limitation | §10 | Nothing. That is what "known limitation" means, and §10 says so |
| R9 — `PROPOSED_BUT_UNRESOLVABLE` | §6 | `MarkdownAgentScopeAndResolutionTest` |
| B1-A3 — the tool-call encoding between model and registry | `MarkdownToolArguments` | `MarkdownToolArgumentsTest` |
| spec-06 §2 — the trigger consumer, off the Streams threads | `MarkdownAgentConfiguration.markdownAgent`; `application.yml` | The live runs of 4 and 5 October 2026 (`docs/evidence/`) |
| spec-06 §3 — unit cost over synchronous REST | `RestBatchDetailsProvider` | The live runs of 4 and 5 October 2026 (`docs/evidence/`) |

---

## 10. Known limitations

One, in the format spec-05 §7 uses for G-3, G-4 and G-6, and numbered in the same G-series. It is
not a defect in the code as specified; it is a place where the implementation is knowingly narrower
than perfect delivery, and where saying so is more honest than building machinery to hide it.

### G-7 — the trace is saved and the recommendation published in two steps that are not atomic `[accepted]`

**Statement.** `MarkdownAgentOrchestrator.onRiskDetected` writes the trace through
`AgentTraceStore.save` and then publishes `markdown-recommended` through
`MarkdownRecommendationPublisher.publish`. The two are separate operations against two different
systems, and nothing makes them one.

**Consequence.** A crash, or a broker unavailable, in the window between them leaves a trace
recording a recommendation that the Pricing context never received. The redelivery that
at-least-once semantics would otherwise provide is then **suppressed**, because §5's first
idempotency mechanism finds the trace and returns it without deliberating or publishing again. The
outcome is a batch that the audit record says was recommended for markdown and that Pricing was
never told about. It is silent: nothing fails, and `GET /api/insight/agent-traces/{id}` answers
normally.

The reverse ordering does not help. Publishing first and saving second trades a lost recommendation
for a recommendation Pricing acts on with no trace to justify it, which FR14's own purpose makes the
worse of the two.

**Intended fix.** The transactional outbox pattern: the orchestrator writes the trace and the
outbound event in one local transaction, and a separate relay publishes from the outbox and marks
each row sent. That converts the dual write into a single write plus an at-least-once relay, which
is the standard answer and is why it is named here rather than left as an exercise.

**Why it is not being built — team ruling R8, 11 September 2026.** No specification mentions an
outbox, nothing in FR14's scope requires one, and introducing one would add a table, a relay, a
scheduler and a delivery-state machine to a service whose assessed property is its layering. It is
an honest boundary of at-least-once delivery between a database and a broker, and the project
report says so rather than implying a guarantee the system does not make. Building it unasked would
be architecture by stealth; recording it is the alternative that a reviewer can check.

**Status: accepted limitation, ruled 11 September 2026.** Raised as flag 4.2 in
`docs/ai-log/2026-09-10-fr14-phase-b1.md`.

---

## 11. FR15 — the end-of-day briefing agent (added 4 October 2026)

spec-02 FR15 gained acceptance criteria on 4 October 2026 and was built the same day, as the second
agentic use case in this service. It has the same shape as FR14 — a port in `application`, a
deterministic stub for every test and a LangChain4j adapter for runtime (ADR 0004, R1), a bounded
tool-use loop, a persisted record of the tool calls — plus one thing FR14 does not have: a
deterministic grounding step in the orchestrator, because a briefing is prose and prose can be
wrong about numbers. Unlike §§1–10 this section is descriptive: it was written with the code and
every rule in it is tested.

### 11.1 The type surface

| Type | Layer | Role |
|---|---|---|
| `BriefingAgent` | `application` | The port. `BriefingDeliberation brief(BriefingRequest, BriefingToolRegistry)` |
| `BriefingRequest` | `application` | `businessDate` (a UTC calendar day) and `now`. Owns the day-to-instant arithmetic, `[businessDayStart, businessDayEnd)`, used by the tool and the count alike |
| `BriefingDeliberation` | `application` | `toolCalls`, `summary` — evidence only, no effects. `summary` may be blank |
| `BriefingToolRegistry` | `application` | The three tools and nothing else (§11.2) |
| `EndOfDayBriefing` | `application` | spec-03 §4 and spec-04 `BriefingResponse`, field for field |
| `BriefingStore` | `application` | Outbound port. `save`, `findById` |
| `BriefingOrchestrator` | `application` | The use case. `EndOfDayBriefing brief(LocalDate businessDateOrNull)` |
| `BriefingQueryService` | `application` | `findById`, behind the GET; present whether or not a model is configured |
| `AgentTraceStore.findCompletedBetween(from, to)` | `application` | FR14's port gains the day-range read FR15 needs |
| `BriefingEntity`, `BriefingToolCallEntity` | `infrastructure` | Tables `briefing` and `briefing_tool_call`; `@OrderBy("sequence ASC")` as the trace has |
| `BriefingJpaRepository`, `BriefingMapper`, `JpaBriefingStore` | `infrastructure` | The adapter behind `BriefingStore`, in the shape of FR14's |
| `LangChain4jBriefingAgent` | `infrastructure` | The runtime adapter: FR14's loop over the same `ChatModel` bean, with its own prompt |
| `BriefingToolArguments` | `infrastructure` | JSON tool arguments to the registry's flat encoding; pinned by `BriefingToolArgumentsTest` |
| `BriefingAgentConfiguration` | `infrastructure` | `@Profile("!test")` outer class, api-key-conditional nested class — the structure of `MarkdownAgentConfiguration` |
| `BriefingController`, `BriefingRequestDto`, `BriefingResponseDto`, `ErrorResponseDto` | `presentation` | spec-04 `createBriefing` and `getBriefing` |
| `StubBriefingAgent`, `InMemoryBriefingStore`, `InsightTestBriefingConfiguration`, `BriefingTestFixtures` | `src/test/java` | The only agent and store any test sees |

Nothing in `domain` is FR15's. The only pure rule it has — a business date is a UTC day — is two
lines of arithmetic on `BriefingRequest`, and the rule for what counts as a recommendation reads
`AgentTraceRecord`, which is an `application` type. ADR 0006's sentence "its summarisation rules
are in `domain` only where they are pure" is therefore satisfied vacuously.

### 11.2 Tool contracts — three, all read-only

The briefing agent summarises decisions FR14 already made. It has no `proposeMarkdown`, no
`getBatchDetails`, and nothing that writes; R5 applies to it as it applies to FR14, and because its
tool set is a separate class there is no flag to flip that would give it a writing tool.

| Tool | Argument | Reads through | Answers |
|---|---|---|---|
| `listCurrentRisks` | none; any argument is ignored | `RiskQueryService.listCurrentRisks()`, the service behind `GET /api/insight/risks` | One batch per line, worst first: `batchId=… sku=… remainingQuantity=… hoursToExpiry=… currentVelocity=… projectedShortfall=…`. `none` when nothing is flagged |
| `listRecommendations` | `businessDate`, an ISO-8601 date | `AgentTraceStore.findCompletedBetween` over the UTC day | One FR14 trace per line, in completion order: `batchId=… finalRecommendation=… rationale=…`. Declined and unresolvable runs are listed too, with their outcome. `none` when the agent decided nothing that day |
| `getSellThroughRate` | `sku` | `VelocityQueryService.findCurrentVelocity`, by the same rendering as §4.2 | `velocityPerHour=…` plus the baseline fields where a baseline is stored; `error=NO_CURRENT_VELOCITY sku=…` when no current window exists, never zero |

A missing or unparseable argument answers `error=INVALID_ARGUMENTS …`, and an unknown tool
`error=UNKNOWN_TOOL toolName=…`, exactly as §4 fixes for FR14: tool failures are answers the model
may correct, not exceptions. `none` is not an error; "nothing is at risk" is a successful answer.

### 11.3 The counts are the orchestrator's, not the model's

spec-02 FR15: "the orchestrator computes `riskCount` and `recommendationCount` itself from the
tool results so no number in the response depends on the model". `BriefingOrchestrator.brief`
reads both **after** the agent returns, from the same two services the tools read:

- `riskCount` = `RiskQueryService.listCurrentRisks().size()`.
- `recommendationCount` = the traces returned by `AgentTraceStore.findCompletedBetween` over the
  business day whose `finalRecommendation` is §6's published form, `<TIER> from … until …`.
  `NO_MARKDOWN` and `PROPOSED_BUT_UNRESOLVABLE` are decisions the agent made that day and the tool
  lists them, but nothing was recommended, so they are not counted. The check is the positive form
  over the four `DiscountTier` names so that a fourth outcome value, if §6 ever gains one, is
  counted as "not a recommendation" rather than silently as one.

`StubBriefingAgent`'s summary claims ninety-nine batches at risk and forty markdowns; the tests
assert two and one. A blank summary — a model that stopped without writing, or ran out of steps —
is stored as a fixed sentence saying so; the counts and the tool calls are persisted either way.
Nothing a model writes makes the orchestrator throw. A model that cannot be reached does, as in
FR14, and no briefing is written.

### 11.4 The record

`briefingId` is `BRF-` plus eight upper-case hexadecimal characters, minted per request; a POST is
a new briefing each time, generated from the records as they stand, and there is deliberately no
idempotency key. The briefing is written through `BriefingStore` with its tool calls numbered
from 1 in call order, and the order survives every hop for the reasons §6 gives: `@OrderBy` on the
entity, a mapper that re-orders nothing, a controller that does not sort. `generatedAt` is
wall-clock from the injected `Clock`, like `completedAt`.

### 11.5 With no LLM configured: 503, and the service otherwise runs normally

`BriefingAgentConfiguration` nests its model-dependent beans under the same two guards as
`MarkdownAgentConfiguration` — `@Profile("!test")` and `@ConditionalOnExpression` on a non-empty
`freshflow.llm.api-key` — and reuses FR14's `ChatModel` bean, so the two agents share one
credential and stand or fall together. With no key there is no `BriefingOrchestrator` bean.
`BriefingController` resolves it through an `ObjectProvider`, as the FR14 trigger consumer does,
and answers `POST /api/insight/briefings` with **503** and spec-04's `ErrorResponse`
`{error: "AGENT_NOT_CONFIGURED", message: …}`. `GET /api/insight/briefings/{briefingId}` goes
through `BriefingQueryService`, which needs no model, and keeps answering 200 or 404.
`MarkdownAgentTestProfileGuardTest` asserts that no test context holds a `briefingAgent` or
`briefingOrchestrator` bean, even with an API key configured (ADR 0004, Check A).

### 11.6 What FR15 does not do

- **No waste figure.** spec-02 FR15: waste depends on FR3's write-offs, which are not built. The
  system prompt tells the model to state that write-off totals are not available because write-offs
  are not recorded, rather than to estimate one.
- **No proposal, no publication, no Pricing.** R5, §7 and `AntiCorruptionLayerGuardTest` apply to
  the prompt and to every FR15 source as they apply to FR14's.
- **No new property and no new dependency.** The briefing reuses `freshflow.llm.*`, including
  `max-steps`.
- **No live run.** As with FR14 before B3, the LangChain4j adapter has not been exercised against
  a model; every test runs against `StubBriefingAgent`.

### 11.7 Traceability

| Requirement | Realised by | Verified by |
|---|---|---|
| FR15 — a second agent behind its own port, three read-only tools | §11.1, §11.2 | `BriefingToolRegistryTest`, `BriefingOrchestratorTest` |
| FR15 — counts computed by the orchestrator, not the model | §11.3 | `BriefingOrchestratorTest`, `BriefingApiIntegrationTest` |
| FR15 — briefing and ordered tool calls persisted and served by GET | §11.4 | `JpaBriefingStoreTest`, `BriefingApiIntegrationTest` |
| FR15 — 503 with no LLM, service otherwise normal | §11.5 | `BriefingApiIntegrationTest.WithoutAnAgent`, `MarkdownAgentTestProfileGuardTest` |
| FR15 — every test against a deterministic stub (ADR 0004) | §11.1 | `StubBriefingAgent` is the only `BriefingAgent` in test sources |
| spec-04 `createBriefing`, `getBriefing` | `BriefingController` | `BriefingApiIntegrationTest` |
| The day-range read on FR14's traces | `AgentTraceStore.findCompletedBetween` | `JpaAgentTraceStoreDayRangeTest` |
| The tool-argument encoding | `BriefingToolArguments` | `BriefingToolArgumentsTest` |

---
