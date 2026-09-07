# Week 7 part 1 — FR12, rolling sell-through velocity

| Field | Value |
|---|---|
| Date | 7 September 2026 |
| Milestone | Week 7 part 1, ends Sunday 20 September 2026 |
| Authorised by | Project team, 7 September 2026 ("Week 6 is complete and pushed (6e2762d). Authorising Week 7, part 1. Execute FR12 ONLY") |
| Tool / model | Claude Code (Opus 5) |
| Baseline | 6e2762d — Week 6 complete, sixteen tests passing |
| Scope | FR12 only. FR13, the agent and everything else are explicitly NOT in this milestone |

---

## 1. What I did

### 1.1 Test-first, and the tests failed first

Written before any behaviour existed, and run to prove it:

```
[ERROR] Tests run: 4, Failures: 4, Errors: 0

  getVelocity_whenWindowedDataExists_returns200AndVelocityResponse  Status expected:<200> but was:<404>
  getVelocity_whenNoBaselineStored_returns200AndOmitsTheComparison  Status expected:<200> but was:<404>
  getVelocity_whenNoWindowedData_returns404FromAControllerNot...    handler is not a HandlerMethod
  getVelocity_whenNewestWindowIsStale_returns404RatherThanAStale... handler is not a HandlerMethod
```

Committed in that state (5989eda) before the implementation commit, so the ordering is in the
history and not merely asserted.

Both 404 tests carry the handler guard the batch and price tests use. That guard earned its
keep here: on the first run the stale-window test **passed vacuously**, because an unmapped
path answers 404 too. It was strengthened before the failing-test commit, which is why all
four fail above rather than three.

### 1.2 The topology

`VelocityTopologyConfiguration` — one function, two inputs, so both state stores live in one
topology under one `application.id` and Interactive Query can reach both:

| Input | Type | Destination | Purpose |
|---|---|---|---|
| `velocityTopology-in-0` | `KStream` | `item-sold` | The event stream, keyed by SKU, aggregated over a hopping window |
| `velocityTopology-in-1` | `KTable` | `velocity-baseline-published` | FR12's "stored velocity baseline held in a KTable" |

The aggregate is materialised as `velocity-windows`; the KTable as `velocity-baselines`. Both
are read by Interactive Query, which is what spec-04 names for this endpoint.

### 1.3 Event time — the part that is not negotiable

`OccurredAtTimestampExtractor`, wired to the `item-sold` binding through
`timestampExtractorBeanName`. Kafka Streams' default extractor reads the broker record
timestamp; spec-01 §2.6 requires the producer-assigned `occurredAt` and forbids relying on
ingestion time. Without that one line of configuration the topology would silently satisfy
neither, and nothing about its output would look wrong.

Section 2 phase 3 demonstrates the difference against the real broker, and it is not subtle:
a backdated sale of 50 units left the current velocity **untouched at 60/h**, where
ingestion-time windowing would have reported roughly 3060/h.

On a record with no usable `occurredAt` the extractor returns `-1`, which makes Kafka Streams
drop it. The alternatives — falling back to the record timestamp or to partition time — would
quietly reintroduce ingestion time for exactly the events whose event time is unknown. Refusing
them loudly is the honest reading of §2.6.

### 1.4 Layering

`insight-service` has no `domain` package and gains none. Per spec-01 §2.3's table, its ports
are declared in `application`:

| File | Layer | Role |
|---|---|---|
| `SkuVelocityView`, `WindowedQuantity` | `application` | spec-03 §4 read model, and the raw window carrier |
| `VelocityWindowStore`, `VelocityBaselineStore` | `application` | Outbound ports |
| `VelocityQueryService` | `application` | The use case, and the window-selection rule |
| `VelocityController`, `VelocityResponseDto` | `presentation` | spec-04 mapping; decides nothing |
| `VelocityTopologyConfiguration`, `OccurredAtTimestampExtractor`, the two serdes | `infrastructure` | The topology |
| `InteractiveQueryVelocityWindowStore`, `InteractiveQueryVelocityBaselineStore` | `infrastructure` | Interactive Query adapters |
| `VelocityBaselineSeeder`, `VelocityProperties`, `VelocityBaselinePublishedEvent` | `infrastructure` | The configuration-seeded baseline |

The ports return raw windows rather than "the current one" on purpose. Which window represents
the velocity right now is a decision, and putting it behind a state-store lookup would place it
where no test can reach it without a broker. It lives in `VelocityQueryService`:

> Consider only windows that have **fully elapsed**; of those take the **latest**; and if even
> that one ended more than a window length ago, there is no current velocity — answer 404 rather
> than present a stale number as a live one.

Rule 3 is the one that matters for FR13 next week: a risk detector fed a stale velocity projects
sales that are not happening, and concludes a batch is fine when it is not.

### 1.5 Tests

All 16 existing tests still pass, unweakened — nothing outside `insight-service` was touched at
all. Four new tests:

```
[INFO] BatchApiIntegrationTest              Tests run: 5, Failures: 0, Errors: 0
[INFO] PriceRulePublishedSnapshotFeedTest   Tests run: 6, Failures: 0, Errors: 0
[INFO] TransactionApiIntegrationTest        Tests run: 3, Failures: 0, Errors: 0
[INFO] PriceApiIntegrationTest              Tests run: 2, Failures: 0, Errors: 0
[INFO] VelocityApiIntegrationTest           Tests run: 4, Failures: 0, Errors: 0
[INFO] BUILD SUCCESS
```

---

## 2. Evidence — end to end against the real broker

Clean broker (`docker compose down -v`), then `pricing-service`, `sales-service` and
`insight-service`. Every "sold" line is a real `POST /api/transactions`; every reading is a real
`GET /api/insight/velocity/SKU-YOG-1KG`. Baseline 120.0 units/hour, 60-second window advancing
every 5 seconds. Times are UTC.

```
=== PHASE 0 - before any sale
  [05:36:01] 404  no windowed data for SKU-YOG-1KG

=== PHASE 1 - selling 1 unit every 12s (5 per minute)
  [05:36:01] sold 1
  [05:36:13] sold 1
  [05:36:25] sold 1
  [05:36:38] sold 1
  [05:36:50] sold 1
  [05:36:50] 200  window 05:35:50..05:36:50  qty=4  velocity=240.0/h  baseline=120.0/h  ratio=2.00
  [05:37:02] sold 1
  [05:37:14] sold 1
  [05:37:14] 200  window 05:36:10..05:37:10  qty=5  velocity=300.0/h  baseline=120.0/h  ratio=2.50
  [05:37:27] sold 1
  [05:37:39] sold 1
  [05:37:39] 200  window 05:36:35..05:37:35  qty=5  velocity=300.0/h  baseline=120.0/h  ratio=2.50
-- after a full window at the fast rate:
  [05:37:52] 200  window 05:36:50..05:37:50  qty=5  velocity=300.0/h  baseline=120.0/h  ratio=2.50

=== PHASE 2 - slowing to 1 unit every 30s (2 per minute)
  [05:37:52] sold 1 (slow)
  [05:38:22] sold 1 (slow)
  [05:38:52] sold 1 (slow)
  [05:39:22] sold 1 (slow)
  [05:39:52] sold 1 (slow)
-- after a full window at the slow rate:
  [05:40:23] 200  window 05:39:20..05:40:20  qty=2  velocity=120.0/h  baseline=120.0/h  ratio=1.00

=== PHASE 3 - event time, not ingestion time (spec-01 s2.6)
  posting a transaction of 50 units with occurredAt=2026-09-07T05:34:23Z (six minutes ago)
  under ingestion-time windowing this would land in the CURRENT window and spike the rate
-- current velocity after the backdated sale of 50 units:
  [05:40:31] 200  window 05:39:30..05:40:30  qty=1  velocity=60.0/h  baseline=120.0/h  ratio=0.50
```

**What each phase proves.**

- **Phase 0** — spec-04's 404, "no windowed data for this SKU yet", from a real handler.
- **Phase 1** — the window fills and the number settles. 240/h at 05:36:50 is the window that is
  one sale short of full; from 05:37:14 onward it holds a steady 5 units per 60 seconds = 300/h,
  which against the stored baseline of 120/h is a ratio of 2.50. That ratio is the whole point of
  FR12: the manager is told this SKU is selling at two and a half times its normal rate.
- **Phase 2** — halving the rate halves the number. 2 units per 60 seconds = 120/h, ratio 1.00.
  The value tracks reality rather than accumulating.
- **Phase 3** — the decisive one. The backdated event genuinely reached the broker; it is the last
  record on `item-sold`, delivered at 05:40:23 and carrying an event time six minutes earlier:

```
{"eventId":"6701dfea-...","occurredAt":"2026-09-07T05:34:23Z","transactionId":"TXN-7B1AF7A4",
 "sku":"SKU-YOG-1KG","quantity":50,"unitPrice":{"amount":4.50,"currency":"AUD"},
 "soldAt":"2026-09-07T05:34:23Z"}
```

  The current window did not move: 1 unit, 60.0/h. Under ingestion-time windowing that window
  would have held 51 units and reported about 3060/h — a fifty-fold error, and precisely the
  failure spec-01 §2.6 exists to prevent. This is the till-flushes-after-going-offline case, and
  it is why the requirement is written the way it is.

**Supporting startup evidence:**

```
c.f.i.i.VelocityTopologyConfiguration : FR12 topology: window=60s advancing every 5s, grace 5s,
                                        retention 10min; windowing on producer-assigned
                                        occurredAt (spec-01 s2.6)
c.f.i.i.VelocityBaselineSeeder        : [published] topic=velocity-baseline-published
                                        eventId=2548e78e-... sku=SKU-YOG-1KG
                                        baselineVelocityPerHour=120.0
org.apache.kafka.streams.KafkaStreams : stream-client [freshflow-insight-velocity-9fa3381a-...]
                                        State transition from REBALANCING to RUNNING
```

Topics on the broker after the run — the two changelogs and the repartition topic are Kafka
Streams' own, created because the topology re-keys by SKU and materialises two stores:

```
freshflow-insight-velocity-velocity-baselines-changelog
freshflow-insight-velocity-velocity-windows-changelog
freshflow-insight-velocity-velocity-windows-repartition
item-sold
price-rule-published
velocity-baseline-published
```

---

## 3. What I assumed that the specs did not state

| # | Assumption | Why, and what it would cost to change |
|---|---|---|
| A | `spring-cloud-stream-binder-kafka-streams` is the artefact for spec-01 §1.1's "Kafka Streams" row | §1.1 names technologies, not Maven coordinates. This artefact is the intersection of the two rows already listed — it *is* the Spring Cloud Stream Kafka binder for Kafka Streams — brings `org.apache.kafka:kafka-streams` transitively, keeps all four services on one programming model, and supplies the `InteractiveQueryService` spec-04 asks for. The alternative, raw `kafka-streams` plus `spring-kafka` and a hand-managed lifecycle, adds an artefact no row names at all |
| B | The message-channel Kafka binder is also added to `insight-service` | Only to seed the baseline topic through `StreamBridge`, the same mechanism the other three services publish with. Already approved in Week 6 |
| C | A **hopping** window: 60 seconds advancing every 5 | spec-02 says "sliding window" and gives the production target as "60 minutes advancing every 5 minutes", which describes a hopping window. Kafka Streams' `SlidingWindows` is a different construct — a window per distinct record timestamp — which would make "the current window" ill-defined for a point query. Same 12:1 ratio at both scales. See G-3 |
| D | The window-selection rule of §1.4 | Nothing in spec-02 or spec-04 says which window is "current", nor when a velocity goes stale. See G-4 |
| E | `velocityPerHour = quantitySold ÷ window length in hours` | The only reading that makes the MVP and production window sizes comparable |
| F | The baseline is expressed in **units per hour**, and `SKU-YOG-1KG` is seeded at 120.0 | Per-hour so the ratio is dimensionless. 120.0 is an invented demonstration number — two units a minute — chosen so the demo's rates land on 2.50 and 1.00. It is not a measurement of anything |
| G | A SKU with no baseline returns 200 with `baselineVelocity` and `velocityRatio` **omitted** | spec-04 marks no property of `VelocityResponse` required. A zero would assert a comparison that was never made — the same silent-zero failure FR6a forbids in Sales |
| H | New topic `velocity-baseline-published`, with its payload declared in `insight-service` rather than in `contracts` | A KTable is built from a topic, so FR12's KTable requires one. It never leaves the Insight context, so putting it in the Published Language would advertise it as a cross-context contract. It still carries `eventId` and `occurredAt`, because §2.6 says every event must. See G-2 |
| I | Grace 5s, retention 10min, `application.id` `freshflow-insight-velocity`, `statestore.cache.max.bytes` 0 | Retention must exceed the range a query looks back over or the endpoint would 404 on windows the store had dropped. Zero cache is what makes the number move while you watch it rather than in commit-interval steps |
| J | A record with no usable `occurredAt` is dropped, not placed | §1.3. Any fallback silently reintroduces ingestion time |

---

## 4. Deviations from the specs, with justification

**D1 — the MockMvc tests do not run against a broker, and the topology itself is not covered by
an automated test.** Same mechanism as Week 6: under the `test` profile
`freshflow.messaging.kafka.enabled` is false and the two read-side ports are supplied by
test-scope adapters, with a fixed clock.

- *Why:* the canonical tool is `org.apache.kafka:kafka-streams-test-utils` (`TopologyTestDriver`),
  which is not in spec-01 §1.1, and this milestone's authorisation said to HALT rather than add
  one. So the selection rule, the arithmetic, the ratio and both 404 paths are tested; the
  topology, the serdes and the timestamp extractor are covered only by the section 2 run.
- *A concrete cost, paid this week:* the first live start failed because the `@Configuration`
  class `VelocityTopology` and its `@Bean` method `velocityTopology` resolved to the same bean
  name. **The test suite could not catch it, because the test profile switches that class off.**
  It cost one restart to find and is fixed (`VelocityTopologyConfiguration`, with the reason
  recorded in its javadoc), but it is exactly the class of defect an untested topology hides.
- *Action:* **recommend asking Dr. Su for `kafka-streams-test-utils` (test scope) alongside
  `spring-cloud-stream-test-binder`**, which the Week 6 log already requests, and alongside
  RUNBOOK Appendix B question 9 on `testcontainers`. `TopologyTestDriver` needs no broker and
  would let the extractor and the window boundaries be asserted directly — including the phase 3
  case, which is currently demonstrated by hand.

**D2 — a topic not listed in spec-01 §2.6.** `velocity-baseline-published`. §2.6's table is the
authority on topics and has no row for it. It exists because FR12 mandates a KTable and a KTable
comes from a topic. Flagged as G-2 rather than added to §2.6 unilaterally.

**D3 — that topic's payload is not in the `contracts` module**, against §2.6's "All events are
defined in the `contracts` module". Justification in G-2.

**D4 — the comparison is made at read time, not by a stream-table join in the topology.** FR12's
criterion is "Compares against a stored velocity baseline held in a `KTable`". The KTable is
real, the baseline is read from its state store, and the comparison happens — but in
`VelocityQueryService` rather than inside the topology. A join would emit a view per window per
SKU whether or not anyone asks, and would still have to decide at query time which of the twelve
overlapping windows a given emission belonged to. Note that spec-02 attaches the words "stream-
table join" to **FR13**, not FR12, and FR13 is where one genuinely belongs: there the output is
an event that must be published whether or not anyone is watching.

**D5 — four production files were written before the tests**: the `SkuVelocityView` record, the
`WindowedQuantity` carrier and the two port interfaces. They are declarations, not behaviour —
no line of them decides anything — and the tests had to be written against something. Reported
because the Phase 3 convention is "no production code", and this is a departure from the letter
of it.

**D6 — `window-seconds` is read in two places**: by `VelocityQueryService` through `@Value`, and
by the topology through `VelocityProperties`. Same property key, so they cannot disagree in
configuration, but they could in code. The application layer cannot import the infrastructure
properties class without breaking §2.2, and a duplicated `@ConfigurationProperties` class would
be worse.

---

## 5. Where I judge the specs incomplete — NOT acted on, for team decision

**G-1. FR12 requires a velocity baseline and nothing says where it comes from.** *This is the gap
the milestone asked me to flag.* spec-02 FR12 says "Compares against a stored velocity baseline
held in a `KTable`"; spec-03 §4 lists `baselineVelocity` as a field of `SkuVelocityView`;
spec-04 returns it. No document says how the number is arrived at. It is seeded from
configuration for now (spec-01 §2.7, recorded this week), which is the same treatment FR8's
price rules get, but a *configured* baseline cannot answer the question FR12's user story
actually asks — "compared with its normal rate" — because a hand-typed constant is not a
measurement of normal. The obvious answer is a trailing historical average per SKU and
time-of-day, which is a materialised view of the same `item-sold` stream and therefore squarely
in Insight's remit. **Recommend the team decide before FR13**, because FR13's risk projection
multiplies by this number, and a fabricated baseline makes every downstream risk figure
fabricated too. The seeding arrangement is deliberately built so the answer replaces one class.

**G-2. §2.6 has no room for a context-internal Kafka topic.** Its table lists cross-context
integration topics, and it says "All events are defined in the `contracts` module".
`velocity-baseline-published` is produced and consumed by Insight alone. Putting it in
`contracts` would advertise an implementation detail as a Published Language term and invite
another context to depend on it; leaving it out means §2.6 is, strictly read, violated. CLAUDE.md
ties the internal/external distinction to "via Kafka", which does not anticipate this case.
Recommend §2.6 gain a short second table for context-internal topics. FR13 will need one too, for
the batch-state KTable.

**G-3. spec-02 FR12 says "sliding window" but describes a hopping one.** "60-minute window
advancing every 5 minutes" is the definition of a hopping window; Kafka Streams reserves
`SlidingWindows` for something else. The implementation follows the description rather than the
label. Recommend the wording change to "hopping"; no behaviour changes.

**G-4. Neither spec-02 nor spec-04 defines which window is "current", nor when a velocity becomes
stale.** spec-04's 404 says "No windowed data for this SKU **yet**", which covers the warm-up
case and is silent on the aged-out case. The rule in §1.4 is mine. It is a user-visible decision —
it determines whether a manager sees "no current velocity" or a five-minute-old number — and it
should be ratified rather than inherited from an implementation.

**G-5. spec-04's `VelocityResponse` declares no `required` properties**, so every field is
optional by omission rather than by intent. Convenient for G above, but it means the contract
cannot distinguish "always present" from "present when known". Worth fixing across all schemas
in one pass rather than piecemeal.

**G-6. §1.1 still has no test-scope provision for the messaging or streaming stack.** Raised in
Week 6 as S-4 for Spring Cloud Stream; it now applies to Kafka Streams as well. See D1.

---

## 6. Not built, deliberately

FR13 and `sell-through-risk-detected`; the stream-table join against a KTable of live batch state
built from `batch-received` and `stock-level-changed`; `GET /api/insight/risks`; the agent, its
tools and `AgentTraceRecord`; `GET /api/insight/agent-traces/{id}`; `POST /api/markdown-plans`;
`inventory-service` consuming `item-sold` to deduct stock; and the POS simulator, which is still
the Week 5 skeleton — the demonstration above drives the API directly instead.
