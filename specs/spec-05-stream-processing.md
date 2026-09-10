# Specification 5 — Stream Processing

**Project**: FreshFlow
**Version**: 1.0 — 10 September 2026
**Status**: Authoritative. Describes the topology as built at Week 7 part 2.

---

## 0. Purpose and scope

spec-01 states the architecture, spec-02 states the requirements and spec-04 states the
API. The stream processing that sits between them — the Kafka Streams topology, its
windowing, its join, its state stores and the Interactive Query read path — was specified
only in fragments across spec-01 §2.6, §2.7 and spec-02 FR12/FR13. This document gathers
it in one place.

It is **descriptive**. Every rule here is already implemented and tested; nothing in it is
new design. Where a rule originates elsewhere, the originating clause is cited and that
clause remains authoritative. Where something is not yet settled, it is recorded as a known
limitation in §7 rather than resolved here.

Scope: `insight-service` only. It is the only service that runs a Kafka Streams topology.
The other three services bind message channels through Spring Cloud Stream and are out of
scope for this document.

---

## 1. The topology

### 1.1 One function, four inputs, one output

`insight-service` runs a **single** Kafka Streams topology, declared as one curried
`java.util.function.Function` bean in
`infrastructure/InsightTopologyConfiguration.insightTopology()` and bound by
`spring.cloud.function.definition: insightTopology`.

| Binding | Topic | Kind | Role |
|---|---|---|---|
| `insightTopology-in-0` | `item-sold` | `KStream` | FR12 — the sales event stream |
| `insightTopology-in-1` | `velocity-baseline-published` | `KTable` | FR12 — the stored baseline |
| `insightTopology-in-2` | `batch-received` | `KStream` | FR13 — batch expiry and opening quantity |
| `insightTopology-in-3` | `stock-level-changed` | `KStream` | FR13 — what remains of a batch |
| `insightTopology-out-0` | `sell-through-risk-detected` | `KStream` | FR13 — the detection |

`application.id` is `freshflow-insight-velocity`.

**Why FR12 and FR13 are one function and not two.** FR13 is specified as a join between the
velocity stream and a batch-state `KTable`. The velocity stream is FR12's output, and a
`KStream` cannot cross from one Spring Cloud Stream function to another without being
written back to a topic and re-read. Keeping both in one function keeps the join in memory,
keeps all four state stores under one `application.id` where a single
`InteractiveQueryService` can reach them, and avoids inventing an internal topic purely to
pass a stream between two halves of the same service. The cost is the four-level curried
signature, which is Spring Cloud Stream's idiom for more than two inputs.

### 1.2 Stages, in order

```
item-sold --filter--groupBy(sku)--windowedBy(60s/5s)--aggregate(+quantity)
                                                          |
                                                    [velocity-windows] --IQ--> GET /api/insight/velocity/{sku}
                                                          |
                                                    suppress(untilWindowCloses)
                                                          |
                                                    VelocitySnapshot stream (one per closed window)
                                                          |
batch-received ------map------>+                          |
                               +--merge--groupByKey--aggregate--> [batch-states] (KTable, keyed by sku)
stock-level-changed --map----->+                          |
                                                          v
                                              KStream-KTable join --> RiskAssessmentPolicy.assess()
                                                          |            (one closed window assesses
                                                          |             every batch of that SKU)
                                                    flatMap to batchId
                                                          |
                                                    groupByKey--reduce(succeeding)
                                                          |
                                                    [batch-risks] --IQ--> GET /api/insight/risks
                                                          |
                                                    filter(newlyAtRisk)
                                                          v
                                              sell-through-risk-detected
```

### 1.3 Re-keying

`item-sold` is re-keyed by `sku` with an explicit `groupBy((key, event) -> event.sku())`
rather than trusting the producer's message key. FR12 says "keys by SKU"; keying in the
topology makes the aggregate correct for any producer on the topic, including
`pos-simulator`. `batch-received` and `stock-level-changed` are likewise mapped onto `sku`
before the merge, because their join partner is a per-SKU velocity stream (§3.1).

---

## 2. Windowing

### 2.1 Parameters

Configured under `freshflow.insight.velocity` (spec-01 §2.7), bound by
`infrastructure/VelocityProperties`:

| Property | Default | Meaning |
|---|---|---|
| `window-seconds` | 60 | Window size |
| `advance-seconds` | 5 | How far each successive window advances |
| `grace-seconds` | 5 | How long a closed window still accepts late records |
| `retention-minutes` | 10 | How long a window stays in the state store and is queryable |

The 60/5 pair is spec-02 FR12's stated MVP simplification; the production target is
60 minutes advancing every 5 minutes. Both are configuration, so the production shape is a
YAML change and not a code change, and the 12:1 window-to-advance ratio holds at both
scales. `retention-minutes` must exceed the range a query looks back over (§4.2).

Grace is non-zero because event time and arrival order are not the same thing — which is
the entire reason spec-01 §2.6 insists on `occurredAt` in the first place.

### 2.2 Which window construct is built

What is built is `TimeWindows.ofSizeAndGrace(window, grace).advanceBy(advance)` — in Kafka
Streams' vocabulary a **hopping window**: fixed size, fixed advance, epoch-aligned,
overlapping. With `advance < size`, twelve windows overlap at any instant.

spec-02 FR12 says "sliding window". Kafka Streams reserves `SlidingWindows` for a different
construct — windows defined relative to record timestamps rather than to a fixed grid. The
overlapping fixed-size window built here is what the general stream-processing literature
calls a sliding window and what Kafka Streams calls a hopping window. **This is a
vocabulary collision, not a deviation**: FR12's acceptance criteria — aggregate over
overlapping windows, on event time, advancing faster than the window size — are satisfied
exactly. It is recorded here so that a reader holding Kafka's narrower definition is not
left to wonder, and is flagged for a team ruling in §7.

### 2.3 Event time — HARD CONSTRAINT

Every window is placed by the **producer-assigned `occurredAt`** carried in the event
payload, never by Kafka ingestion time. This is spec-01 §2.6, and it is not negotiable.

The mechanism is `infrastructure/OccurredAtTimestampExtractor`, named on bindings `in-0`,
`in-2` and `in-3` by `timestampExtractorBeanName` in `application.yml`.

| Aspect | Behaviour |
|---|---|
| Matches on | `contracts.IntegrationEvent`, the interface that declares `occurredAt` — **not** on any concrete payload type, so a topic added later is correctly windowed by being consumed rather than by someone remembering to extend an `instanceof` chain |
| Returns | `event.occurredAt().toEpochMilli()` |
| Missing `occurredAt` | Returns `-1`, which makes Kafka Streams **drop** the record, and logs a warning naming topic, partition and offset |

**Why the drop, and not a fallback.** The alternatives Kafka Streams offers are to fall back
to the record timestamp or to current partition time. Both reintroduce ingestion time for
precisely the events whose event time is unknown — the ones most likely to distort a
window. An event that does not carry the field §2.6 makes mandatory is not windowable;
refusing it loudly is the honest answer.

**Why event time matters here concretely.** A till that buffers while offline and then
flushes ten minutes of scans at once would, under ingestion time, appear as one enormous
spike in the current window. Velocity would read at roughly ten times reality, and FR13
would conclude that a batch is selling fine when it is not. Under event time those scans
land in the windows they actually happened in. The same property is what makes the whole
chain replayable: re-running the topology over the same records produces the same answers.

`in-1` (`velocity-baseline-published`) names no extractor. It feeds a `KTable` keyed by SKU
with no windowing, so no timestamp decision is made from it.

### 2.4 Which window is "now" — the read-side selection rule

Recorded in spec-02 FR12 (team ruling 2, 7 September 2026) and implemented in
`application/VelocityQueryService`. Restated here because it is the read half of the
windowing decision:

1. Only windows that have **fully elapsed** are eligible. A partially elapsed window has had
   only part of its span happen, so its count understates the rate.
2. Of those, the **latest** is reported — at most one advance interval behind real time.
3. If even that window ended **more than one window length ago**, there is no current
   velocity and the endpoint answers `404`. It must not present the last window it happens
   to hold as a live figure.

Rule 3 carries downstream: FR13 multiplies velocity by remaining trading hours, so a stale
velocity presented as live projects sales that are not happening.

The rule lives in the application layer, not in the state-store adapter, so that it can be
tested without a broker. The adapter returns every window overlapping the requested range
and makes no judgement.

### 2.5 Suppression — the FR13 velocity stream

FR13 does not consume the window store; it consumes a stream of **closed** windows, produced
by `suppress(Suppressed.untilWindowCloses(BufferConfig.unbounded()))` and mapped to a
`VelocitySnapshot(sku, windowStart, windowEnd, quantitySold, velocityPerHour)`.

Suppression matters for FR13 in a way it does not for FR12's endpoint. Without it, every
arriving sale would emit a dozen partial windows, each under-reporting the rate, and each
would trigger a fresh risk assessment on a figure that was never true. With it, each window
contributes exactly one assessment, when it closes in event time.

`velocityPerHour` is `quantitySold / windowHours` — for the 60-second default, a count of 2
becomes 120 units per hour.

---

## 3. The FR13 stream-table join

### 3.1 The table side: live batch state, keyed by SKU

`batch-received` and `stock-level-changed` are mapped to a common
`SkuBatchStates.BatchStateUpdate`, merged into one stream, grouped by SKU and aggregated
into a `KTable<String, SkuBatchStates>` materialised as `batch-states`.

`SkuBatchStates` holds **every** batch of one SKU, as a `Map<batchId, BatchState>`.

**Why keyed by SKU and not by batch.** The join partner is a per-SKU velocity stream. A
table keyed by `batchId` could not be joined against it at all; a table keyed by SKU holding
a single batch would silently discard every batch but the last. Holding the whole set is
what makes the join both possible and correct — and it is why one closing velocity window
assesses every batch of that SKU at once.

**How the two topics fold together.** Neither is sufficient alone, which is the whole reason
FR2 had to be built before FR13:

| Source | `expiryAt` present | Effect |
|---|---|---|
| `batch-received` | Yes | Establishes or replaces the batch state — expiry and opening quantity |
| `stock-level-changed` | No — the topic does not carry it | Updates `remainingQuantity` on an existing batch, preserving its expiry |
| `stock-level-changed` for an unseen batch | No | **Dropped.** Without `batch-received` there is no expiry, and a batch with no expiry cannot be assessed for expiry risk. Recording it with a guessed expiry would produce a risk figure that looks authoritative and is invented |

The presence or absence of `expiryAt` is what distinguishes the two sources after the merge.
This is a deliberate encoding, not an accident of the payloads.

### 3.2 The join and the assessment

`velocityStream.join(batchStates, ...)` is an inner `KStream`-`KTable` join on SKU: each
closed velocity window is joined against the current batch state for that SKU, and the join
function calls `RiskAssessmentPolicy.assess(...)` once per batch, returning a
`List<BatchRiskView>`.

`application/RiskAssessmentPolicy` is FR13's arithmetic and threshold in one place:

```
hoursToExpiry         = hours between now and expiryAt
tradingHoursRemaining = max(0, hoursToExpiry) x (tradingHoursPerDay / 24)
projectedSales        = velocityPerHour x tradingHoursRemaining
projectedShortfall    = remainingQuantity - projectedSales
atRisk                = projectedShortfall > shortfallThreshold
                        AND remainingQuantity > 0
                        AND hoursToExpiry > 0
```

| Term | Source |
|---|---|
| `now` | **The end of the closed velocity window** — event time, never a wall clock |
| `shortfallThreshold` | `freshflow.insight.risk.shortfall-threshold`, default 5.0 |
| `tradingHoursPerDay` | `freshflow.insight.risk.trading-hours-per-day`, default 24.0 (§7, G-3) |

`projectedShortfall` is **not** clamped at zero: a negative shortfall says the batch clears
with room to spare, which is a different fact from clearing exactly.

The last two conjuncts are FR13's own scope: nothing left to sell is nothing to worry about,
and an expired batch cannot be rescued by a markdown — that is FR3's write-off.

The policy is a plain object with no Spring annotation and no Kafka import, constructed by a
bean method in `infrastructure`. `insight-service` has no `domain` package (spec-01 §2.3),
so `application` is the innermost layer available and spec-01 §2.5 places the rule there. It
is unit-tested with no broker, no Spring context and no clock.

**Why the threshold is applied once, in the topology, and not again on the read side.** Two
callers need the same answer: the topology, which publishes the alarm, and the endpoint,
which lists what is currently flagged. If each applied its own threshold at its own instant,
a batch could be reported at risk by one and safe by the other. The assessment is computed
once and stored; `RiskQueryService` filters on the stored `atRisk` flag and re-assesses
nothing.

### 3.3 Detection versus standing state

`sell-through-risk-detected` fires on the **transition** into risk, not on every
re-assessment (spec-02 FR13). With a window advancing every 5 seconds, a single slow-selling
batch would otherwise emit an event several times a minute for as long as it remained at
risk, and the topic would carry alarm volume rather than alarm information.

The mechanism: the joined views are `flatMap`ped so the key becomes `batchId`, grouped by
that key, and `reduce`d. `reduce` is used rather than `aggregate` precisely because a reducer
is handed the **previous** value for the key:

| Case | `newlyAtRisk` |
|---|---|
| First assessment of a batch | `view.atRisk()` — becoming at risk is itself the transition |
| Later assessment | `view.atRisk() && !previous.view().atRisk()` |

The reduced `KTable` is materialised as `batch-risks` and is the read model behind
`GET /api/insight/risks`. Only records where `newlyAtRisk` is true are mapped to a
`SellThroughRiskDetectedEvent` and emitted.

Both at-risk and safe assessments are stored. A batch that has stopped being at risk must be
able to say so; a store that only ever held alarms could not distinguish "recovered" from
"never evaluated".

---

## 4. State stores and Interactive Query

### 4.1 The four stores

| Store | Type | Key | Value | Built by | Read by |
|---|---|---|---|---|---|
| `velocity-windows` | `WindowStore` | `sku` | `Long` units sold | FR12 windowed aggregate | `InteractiveQueryVelocityWindowStore` |
| `velocity-baselines` | `KeyValueStore` | `sku` | `VelocityBaselinePublishedEvent` | `KTable` over `velocity-baseline-published` | `InteractiveQueryVelocityBaselineStore` |
| `batch-states` | `KeyValueStore` | `sku` | `SkuBatchStates` | FR13 fold of two topics | not read externally — join input only |
| `batch-risks` | `KeyValueStore` | `batchId` | `RiskAssessment` | FR13 `reduce` | `InteractiveQueryRiskViewStore` |

All four are materialised by name because a store must be named to be queryable. All four
live under one `application.id`, which is what lets one `InteractiveQueryService` reach all
of them (§1.1).

### 4.2 Interactive Query as the CQRS read side

spec-01 §2.3 designates `insight-service` **no domain layer — CQRS read model**. Interactive
Query is the read half of that: the topology writes the views continuously, and the
endpoints read them straight out of the local state store with no round trip to the broker.
This is why an endpoint can answer in a millisecond from a stream that never stops moving.

The dependency direction is preserved by a port per store. Ports are declared in
`application` — the innermost layer this service has (spec-01 §2.3) — and the
`InteractiveQuery*` adapters implementing them live in `infrastructure`:

| Port (`application`) | Adapter (`infrastructure`) |
|---|---|
| `VelocityWindowStore` | `InteractiveQueryVelocityWindowStore` |
| `VelocityBaselineStore` | `InteractiveQueryVelocityBaselineStore` |
| `RiskViewStore` | `InteractiveQueryRiskViewStore` |

No Kafka Streams type crosses into `application`: the ports speak `WindowedQuantity`,
`OptionalDouble` and `BatchRiskView`.

**Query shapes.**

- Velocity: `store.fetch(sku, from, to)` ranges over window **start** times and yields the
  start as the iterator key; the end is start plus the configured size. The service looks
  back two window lengths — enough to see the newest elapsed window and to recognise when it
  is too old to report (§2.4 rule 3).
- Risks: a full `store.all()` scan. Correct here and not in a larger system: the store holds
  one row per batch, and `GET /api/insight/risks` is defined as a list rather than a lookup,
  so there is no key to query by.

**An unavailable store is not an error.** During startup, rebalancing or state restoration
the store is genuinely not queryable. Both adapters catch that and return empty, which the
endpoints render as spec-04's `404` for velocity and `[]` for risks — never a `500`. During
restoration "nothing is currently flagged" is the honest answer.

### 4.3 Caching disabled deliberately

```yaml
'[statestore.cache.max.bytes]': 0
'[commit.interval.ms]': 500
```

The default record cache holds state-store updates back for up to the commit interval.
FR12's entire point is that the number moves while you watch it, so the cache is disabled and
the commit interval shortened. This is a demonstration-scale setting, stated here so it is a
recorded decision rather than a stray line of YAML.

---

## 5. Internal topics

spec-01 §2.6 distinguishes **integration topics** (Table A — cross-context, defined in
`contracts`, the Published Language) from **internal topics** (Table B — one context produces
and consumes them, defined in the owning service's own packages). The topology touches one
of each:

| Topic | Table | Payload defined in | Why |
|---|---|---|---|
| `velocity-baseline-published` | **B** | `insight-service`'s `infrastructure.VelocityBaselinePublishedEvent` | Produced and consumed by `insight` alone. Putting it in `contracts` would advertise an implementation detail as shared vocabulary |
| `sell-through-risk-detected` | **A** | `contracts.SellThroughRiskDetectedEvent` | Both ends are `insight` today, but FR14's agent consumes it and `markdown-recommended` carries the result to `pricing`, so it is part of the shared vocabulary already |

### 5.1 Why `velocity-baseline-published` exists at all

FR12 requires the current rate to be compared "against a stored velocity baseline **held in a
`KTable`**". A `KTable` is built from a topic. The baseline therefore needs a topic even
though, today, nothing outside `insight-service` produces one.

`infrastructure/VelocityBaselineSeeder` publishes the configured baselines
(`freshflow.insight.velocity.baselines`) onto that topic on `ApplicationReadyEvent` — after
the binder is up, because publishing crosses a process boundary. Messages are keyed by SKU
via `messageKeyExpression: headers['sku']`, which is what makes the `KTable` a table: one row
per SKU, last write winning, rather than a growing log. Republishing on every restart is
therefore an idempotent overwrite.

This is ADR 0003's accepted MVP simplification, and the topic is what makes it cheap to
reverse: the topology, the store, the port and the endpoint all read the **topic**, never the
configuration, so a measured baseline replaces the seeder class and nothing else.

A SKU with no stored baseline is not an error. The endpoint returns the velocity and omits
`baselineVelocity` and `velocityRatio`. It must not report a baseline of zero, which would
read as "selling at zero times its normal rate" when the normal rate is merely unknown.

### 5.2 Changelog and repartition topics

Kafka Streams creates its own internal topics for the four materialised stores and for the
re-keyed streams of §1.3, named from the `application.id`. They are not FreshFlow contracts,
no FreshFlow code names them, and they belong to neither Table A nor Table B. They are noted
only so that a reader inspecting the broker is not surprised to find topics no specification
mentions.

---

## 6. Serialisation

| Direction | Mechanism |
|---|---|
| Topology inputs | One `Serde` per payload type in `infrastructure` — `ItemSoldEventSerde`, `BatchReceivedEventSerde`, `StockLevelChangedEventSerde`, `VelocityBaselineSerde` — named per binding in `application.yml` |
| Topology output | `SellThroughRiskDetectedEventSerde` |
| Internal state | `JsonSerde` over `SkuBatchStates`, `BatchStateUpdate`, `VelocitySnapshot`, `RiskAssessment`; `Serdes.String()` and `Serdes.Long()` for keys and the window aggregate |

The Kafka Streams serdes use a **different `ObjectMapper`** from the message-channel binder
that produces every other topic. That difference is the cause of G-6 (§7).

---

## 7. Known limitations

Three, all recorded against Week 7 findings and all carried forward. None is a defect in the
code as specified; each is a place where the specification is silent or the implementation is
knowingly narrower than the requirement.

### G-3 — "remaining trading hours" is undefined `[Week 8]`

No specification describes the trading day, so FR13's projection is configured through
`freshflow.insight.risk.trading-hours-per-day`, defaulting to 24.

This is not a cosmetic default. A shop open twelve hours a day sells half as much before
expiry as the clock suggests, so a batch that looks safe against clock hours can be at risk
against trading hours. Until the team sets real opening hours, every figure derived from it
projects over clock hours and must be described that way.

Recorded in spec-01 §2.7 and spec-02 FR13. **Status: open, Week 8.**

### G-4 — a SKU with no sales at all is not re-assessed `[Week 8, first item]`

Risk assessment is triggered by a **closing velocity window**, and a velocity window closes
because sales are arriving. The consequence cuts against FR13's own user story and is stated
plainly:

- A batch that becomes at risk **because its SKU stopped selling entirely** may never be
  flagged. That is exactly the "slow day or a genuine problem" case FR12 and FR13 exist to
  separate.
- A SKU still selling, however slowly, is assessed normally. Slowing down is detected;
  stopping dead is not.
- Batches **already** flagged remain flagged and remain listed by `GET /api/insight/risks`,
  because the read model is a stored assessment rather than a live recomputation. The
  endpoint does not go stale or empty when sales stop; what does not happen is a *new* alarm.

**Intended fix**: a stream-time punctuator re-assessing every live batch on a schedule,
independent of whether a sale arrived. Scheduled rather than improvised because a punctuator
advancing on stream time still needs records to advance it, so the interaction between a
wall-clock schedule and `occurredAt` must be settled first — and settling it badly would
breach §2.3.

Accepted as a known limitation for the Week 8 demonstration by team ruling, 7 September 2026.
Recorded in spec-02 FR13. **Status: accepted limitation; fix scheduled Week 8.**

### G-6 — the `occurredAt` wire format is not enforced `[Week 8]`

spec-01 §2.6 requires `occurredAt` to be an ISO-8601 timestamp. Nothing enforces it. The type
system enforces that the field *exists*; nothing enforces how it is *serialised*.

Week 7 part 2 found this the expensive way. `sell-through-risk-detected` shipped `occurredAt`
as `1788762590.000000000` — the correct instant, in a format §2.6 does not allow — because
the Kafka Streams output serde uses a different `ObjectMapper` from the message-channel binder
that produces every other topic (§6). A consumer written against the other topics would not
have parsed it. No test failed, because no test serialises that topic.

**Intended fix**: a contract test over the `contracts` module that serialises one instance of
every event and asserts the wire shape — `eventId` present and a UUID string, `occurredAt`
present and matching an ISO-8601 instant. No broker, no Spring context, milliseconds to run,
and it would have caught the defect at the moment it was introduced.

Recorded in spec-01 §2.6. **Status: open, Week 8.**

### Also flagged, not a limitation — window vocabulary

§2.2: spec-02 FR12 says "sliding window"; what is built is a Kafka Streams **hopping**
window, which is what the general literature calls sliding. The requirement's acceptance
criteria are met exactly. Raised for a team ruling on whether FR12's wording should be made
Kafka-specific; **not acted on**.

---

## 8. Traceability

| Requirement | Realised by | Verified by |
|---|---|---|
| FR12 — rolling sell-through velocity | §1, §2, §4 | `VelocityApiIntegrationTest` |
| FR13 — expiry risk detection | §1, §2.5, §3, §4 | `RiskAssessmentPolicyTest`, `RiskApiIntegrationTest` |
| spec-01 §2.6 — event time | §2.3 | `OccurredAtTimestampExtractor`; **not** yet covered by a wire-format test (G-6) |
| ADR 0003 — seeded baseline behind a port | §5.1 | `VelocityApiIntegrationTest` |
