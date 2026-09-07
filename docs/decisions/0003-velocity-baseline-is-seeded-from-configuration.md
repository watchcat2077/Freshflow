# ADR 0003 — The FR12 velocity baseline is seeded from configuration, behind a port

| Field | Value |
|---|---|
| Status | **Accepted** |
| Date | 7 September 2026 |
| Deciders | Project team |
| Raised as | Week 7 part 1 finding G-1 (`docs/ai-log/2026-09-07-week-7-fr12-velocity.md` §5) |
| Affects | `insight-service`, FR12, FR13, spec-01 §2.6, spec-01 §2.7, spec-02 FR12 |
| Relates to | ADR 0001 — same Ports and Adapters argument, applied to a different port |
| Supersedes | Nothing |

---

## Context

spec-02 FR12 requires the current sell-through rate to be compared "against a stored velocity
baseline held in a `KTable`". spec-03 §4 lists `baselineVelocity` as a field of
`SkuVelocityView`; spec-04's `VelocityResponse` returns it and `velocityRatio` alongside it.

**No specification says where the baseline comes from.** Not spec-02, not spec-03, not spec-04.
The requirement names the comparison and the structure that holds it, and stops.

That gap is not cosmetic, and it does not stay contained in FR12:

- FR12's user story asks for the current rate "compared with its normal rate". A number typed
  into a configuration file is not a measurement of normal. It is an assertion about normal.
- FR13 multiplies by this number. Projected sales over the remaining trading hours is
  `velocity × hours`, and the risk decision is a comparison of that projection against remaining
  quantity. A fabricated baseline therefore makes every downstream risk figure fabricated too,
  and FR13 is the system's central insight.
- Week 9's agent will read the ratio through `getSellThroughRate` and reason about it in a
  prompt. An unexplained constant becomes an unexplained recommendation.

The decision had to be made before Week 7 part 2 implemented FR13.

## Decision

**The velocity baseline is seeded from configuration, and that is accepted as an explicit MVP
simplification rather than as a design.**

Concretely, and the ordering of these three clauses is the decision:

1. The baseline enters `insight-service` **through a port** — `VelocityBaselineStore`, declared
   in the application layer (spec-01 §2.3's rule for a service with no domain package).
2. The configuration seed is **one legitimate implementation** of that port's data source, not a
   placeholder wedged in front of it. `VelocityBaselineSeeder` publishes
   `freshflow.insight.velocity.baselines` onto the `velocity-baseline-published` topic at
   startup; the topology builds its `KTable` from that topic; the port's adapter reads the
   table's state store by Interactive Query.
3. A **measured** baseline — computed from a longer window over the same `item-sold` stream —
   replaces `VelocityBaselineSeeder` and nothing else.

## Rationale

**1. It is the same argument as ADR 0001, applied to a different port.**
ADR 0001 accepted a static-configuration adapter behind `PriceSnapshotFeed` in Week 5, on the
grounds that the Week 5 adapter was "a legitimate implementation of the port" and that swapping
it in Week 6 would change no application-layer code. Week 6 paid that claim off: the adapter was
replaced by a Kafka subscription and `git diff` over every `application` package was empty. The
same structure is in place here, for the same reason, and it should be trusted for the same
reason.

**2. The seam is at the source, not at the port.**
The important property is not that a port exists — it is *where* the seam falls. Everything
downstream of the topic reads the topic: the topology, the `KTable`, its state store, the
`VelocityBaselineStore` adapter, `VelocityQueryService`, the endpoint, and FR13's projection.
None of them can tell how a row arrived on `velocity-baseline-published`. The configuration is
visible to exactly one class.

**3. A measured baseline is the same computation FR12 already performs.**
A trailing average per SKU is a windowed aggregate over `item-sold` — a longer window over the
stream the topology is already consuming. It is not new infrastructure and it is not a new data
source; it is the existing aggregate with a different window size, published to the same topic.
That is why the upgrade is genuinely one class and not a rewrite.

**4. Deciding it properly now would be guessing.**
A real baseline needs a window length, a treatment of time-of-day and day-of-week seasonality,
a cold-start answer for a SKU with no history, and a policy for excluding the anomalies it is
meant to detect. None of that is specified, and inventing it during Week 7 would produce a more
elaborate fabrication, not a measurement.

## Consequences

**Positive**

- FR12 and FR13 proceed on a stated simplification rather than on an undeclared one.
- The comparison in the demonstration is real end to end: the number is read from the `KTable`'s
  state store, not from a field in the service.
- The upgrade path is small, known, and written down before it is needed.

**Negative / accepted trade-off**

- `velocityRatio` is currently a ratio against an assertion. **It must be described that way in
  the report and in the demonstration**, not presented as a measured comparison. The seeded value
  for `SKU-YOG-1KG` — 120.0 units per hour — was chosen to make the demonstration legible, and is
  not a measurement of anything.
- FR13's `projectedSales` and `projectedShortfall` inherit that caveat in full.

**Explicitly NOT affected**

- The port, the topic, the `KTable`, the state store and every consumer of them. That is the
  point of the decision.

## Upgrade path — from asserted to measured

Recorded now so the eventual work is scoped rather than rediscovered.

| Step | Change | Touches |
|---|---|---|
| 1 | Add a second, long windowed aggregate over `item-sold` — a trailing average per SKU, window measured in days rather than seconds | `insight-service` topology |
| 2 | Publish each computed average onto `velocity-baseline-published`, keyed by SKU, exactly as the seeder does today | the topology's output |
| 3 | Delete `VelocityBaselineSeeder`, `VelocityProperties.baselines` and the `freshflow.insight.velocity.baselines` block | one class, one property |
| 4 | Decide the cold-start rule for a SKU with insufficient history | new; needs a team ruling |

Steps 1 to 3 change nothing else. **Step 4 is the part that still needs a decision**, and it is
the honest reason this ADR does not simply schedule the work: "what is the baseline before there
is enough history to measure one" is the same question as "where does the baseline come from",
asked at a smaller scale, and it will still be open when the aggregate is built.

Until step 3 lands, `spec-01 §2.7`'s `freshflow.insight.velocity.baselines` block stands as the
recorded configuration contract, and the seeded number must be described as an assumption
wherever a result derived from it is presented.

## Compliance

- spec-01 §2.7 records the property keys and states explicitly that the block is a stop-gap.
- spec-02 FR12's acceptance criteria note the simplification.
- `VelocityBaselineSeeder`'s javadoc names this ADR and states that a measured baseline replaces
  that class alone.

## Verification

A reviewer can confirm the seam by checking that `freshflow.insight.velocity.baselines` is read
by exactly one class:

```
$ grep -rln "getBaselines()" --include=*.java insight-service/src/main
insight-service/src/main/java/com/freshflow/insight/infrastructure/VelocityBaselineSeeder.java
insight-service/src/main/java/com/freshflow/insight/infrastructure/VelocityProperties.java
```

Two files, and only one of them is a reader: `VelocityProperties` *declares* the accessor as the
binding of the configuration block, and `VelocityBaselineSeeder` is the only class that calls it. Nothing else in the service obtains a baseline from configuration -- everything
else reads the topic, or the state store built from it.

(Grepping for the property name rather than the accessor also matches two javadoc references,
in `VelocityTopologyConfiguration` and the infrastructure `package-info`. Both are comments
pointing at this decision; neither reads a value.)
