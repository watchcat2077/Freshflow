# Specification 6 — Agent Orchestration

**Project**: FreshFlow
**Version**: 0.1 — 10 September 2026
**Status**: Authoritative for FR14. §2 and §3 were open when this document was written and were
**ruled on by the team on 10 September 2026**. No clause is open.

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

Where a rule originates elsewhere the originating clause is cited and remains authoritative.

**Three team rulings govern this document and are not re-opened in it.**

| Ruling | Substance | Recorded in |
|---|---|---|
| R1 | The LLM sits behind a port declared in `application`. Two adapters: a deterministic stub for every test, a LangChain4j adapter for runtime. No test calls a live model or touches the network | ADR 0004 |
| R4 | The MVP tool set is three tools: `getBatchDetails`, `getSellThroughRate`, `proposeMarkdown`. The other three appear nowhere | §4, spec-02 FR14 |
| R5 | The agent proposes only. No price writes, no call to `pricing-service`, and no margin-floor logic anywhere in `insight-service` | §7 |

Two further rulings were taken on **10 September 2026**, on the two questions this document raised:
the trigger is consumed by a second Spring Cloud Stream function (§2), and `getBatchDetails` reads
unit cost over synchronous REST (§3). Both are recorded in the sections that asked them.

---

## 1. The type surface

Every new type, its package and its layer. `insight-service` has **no `domain` package** (spec-01
§2.3), so the layers available are `presentation`, `application` and `infrastructure`, and the
ports go in `application` — spec-01 §2.3's port-placement table and ADR 0002 Decision 2 both say
so, for the plain reason that there is nowhere else to declare them.

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

The entity and the record are two types with a mapper between them because spec-01 §2.2 forbids
`application` from importing JPA. This is the same construction `inventory-service` uses for
`StockBatch`, applied to a read model rather than to an aggregate; R3 requires it explicitly.

The LangChain4j adapter belongs here too and is **not** written in Phase A (§8).

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

**The stub is in test sources so that LangChain4j never reaches the test classpath.** That is the
mechanical half of ADR 0004: a test cannot accidentally resolve a live model if the library that
would resolve one is not on the classpath the tests compile against.

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
| Validates | `batchId` is the trigger's batch; `tier` parses as a `DiscountTier`; `effectiveFrom` parses as an `Instant`; `rationale` is non-blank |
| Returns | `recorded batchId=… tier=…` |
| Invalid arguments | `error=INVALID_ARGUMENTS …` — a tool failure the agent may correct, not an exception |
| Publishes | **Nothing.** See §5 |

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
| Proposed | `<TIER> from <effectiveFrom> until <effectiveTo>` — e.g. `PERCENT_30 from 2026-09-10T09:30:00Z until 2026-09-10T18:30:00Z` |
| Declined | `NO_MARKDOWN` |

`rationale` is FR14's written justification, and is present in both cases. Neither field is ever
computed from a price.

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
