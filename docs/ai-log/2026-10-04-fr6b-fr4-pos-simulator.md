# FR6b, FR4 and the POS simulator — three modules, one session

| Field | Value |
|---|---|
| Date | 4 October 2026 |
| Milestone | FR6b (apply live markdowns at point of scan), FR4 (query batches by SKU), `pos-simulator` (spec-01 §1.2 supporting module, roadmap T24) |
| Authorised by | Project team, 4 October 2026 — "FR6b in sales-service, FR4 in inventory-service, pos-simulator; tests first; no new dependency; do not touch pricing-service, insight-service, contracts, any pom" |
| Tool / model | Claude Code (Fable 5.1), in an isolated worktree branch; nothing pushed |
| Baseline | `1e9015f` — 64 tests run, 64 pass (inventory 17, sales 9, pricing 2, insight 36, pos-simulator none) |
| Scope | `sales-service`, `inventory-service`, `pos-simulator`, this log. **No `pom.xml`, no `contracts`, no spec text changed** |
| Result | **93 tests run, 93 pass** (inventory 21, sales 19, pricing 2, insight 36, pos-simulator 15). `./mvnw -o package` green |

---

## 0. What this was

The three pieces of the demonstration path that were still stubs or `[POST-MVP]` placeholders on
the morning of 4 October, built against the criteria the specs gained that day: FR6b's four
acceptance criteria and the B-7 ruling that removed `markdown-expired` from them, FR4's two
endpoints, and spec-04's new `GET /api/batches` with the `sku` query. `pricing-service` — the
producer of `markdown-applied` — was being built concurrently by another engineer and was not
touched; `sales-service` consumes the contract that `1e9015f` fixed in `contracts`, and nothing
else.

Six commits on the branch, tests before features in each service:

| Commit | Module | What |
|---|---|---|
| `6661eed` `test(sales)` | sales | FR6b tests, written to fail; type surface stubbed |
| `36bc5fa` `test(inventory)` | inventory | FR4 list-endpoint tests, written to fail |
| `5266956` `feat(sales)` | sales | FR6b |
| `ee69656` `feat(inventory)` | inventory | FR4 |
| `feat(pos-simulator)` | pos-simulator | the CLI and its tests, one commit |
| `docs` | — | this log |

---

## 1. What I did

### 1.1 FR6b — `sales-service`

**Tests first (`6661eed`).** The Phase A convention: the tests plus the type surface they need,
with `PriceSnapshot#priceAt` throwing `UnsupportedOperationException` and the feed's
`onMarkdownApplied` a logged no-op. At that commit sales ran 19 tests — the Week 6 nine green and
unchanged, nine of the ten new ones red (seven assertion failures, two
`UnsupportedOperationException`), and `postTransaction_afterMarkdownWindow_revertsToBasePrice`
green vacuously, because a script that ignores markdowns answers the base price everywhere. It is
kept because it is the regression guard for FR6b's fourth criterion once the feature exists.

**The feature (`5266956`).** By layer:

| Layer | File | Change |
|---|---|---|
| `domain` | `PriceSnapshot` | `priceAt(Instant)` returning the nested record `PriceInForce(Money unitPrice, boolean markdownApplied)`; `withMarkdown(...)`; `hasMarkdown()`. The window is half-open, `[effectiveFrom, effectiveTo)`. Imports `java.time.Instant` and `java.util.Objects` only |
| `domain` | `LineItem`, `package-info` | Javadoc only: the script obtains both line values by asking the snapshot; the package still declares no interface |
| `application` | `TransactionApplicationService` | One step of the script changed: `snapshot.priceAt(occurredAt)` replaces the unconditional `basePrice()`. Same validation, same ports, still no call to `pricing-service` |
| `infrastructure` | `PriceRulePublishedSnapshotFeed` | `onMarkdownApplied(MarkdownAppliedEvent)`; a second map, `markdownsBySku`; `findBySku` composes base and markdown on every read |
| `infrastructure` | `SalesMessagingConfiguration` | Second consumer bean `markdownApplied` (`Consumer<Message<MarkdownAppliedEvent>>`), same shape as `priceRulePublished` |
| `infrastructure` | `package-info` | Javadoc: the adapter now consumes two topics |
| `presentation` | `TransactionController` | Javadoc only (FR5, FR6a and FR6b) |
| resources | `application.yml` | `spring.cloud.function.definition: priceRulePublished;markdownApplied`; binding `markdownApplied-in-0` → `markdown-applied`; anonymous group, `start-offset: earliest`, with the same rationale as the price-rule binding |
| test resources | `application-test.yml` | **Unchanged** — `spring.cloud.function.definition: ""` stays |

The feed holds `markdown-applied` to five rules, stated in its javadoc and each pinned by a test:

1. Deduplicate on `eventId`, through the same seen-set as `price-rule-published`.
2. Last-write-wins on `occurredAt` per SKU among markdowns.
3. The markdown is held apart from the base snapshot and composed on read, so the result is a
   function of event times alone — the two topics replay on separate bindings and either may
   arrive first. A markdown whose rule has not arrived is held, and the SKU stays absent (FR6a's
   400) until it does.
4. **A markdown applies only to a rule at least as old as itself.** A `price-rule-published`
   whose `occurredAt` is after the markdown's displaces it; an older rule — even one new enough to
   replace the base price — keeps it. Strict comparison; a tie keeps the markdown.
5. No `markdown-expired` is consumed (B-7). The window stays on the snapshot and `priceAt`
   answers for the instant asked about, so a late-arriving scan inside a window that has since
   closed is still priced at the discounted price, and a scan after `effectiveTo` reverts with no
   further event. The feed never prunes by wall clock.

The class keeps its Week 6 name, `PriceRulePublishedSnapshotFeed`. Renaming it would have touched
every ai-log line since Week 6 for no behavioural gain; the javadoc says it consumes both topics.

### 1.2 FR4 — `inventory-service`

**Tests first (`36bc5fa`).** Four tests in `BatchApiIntegrationTest`, all answering 405 at that
commit because `GET /api/batches` was not mapped: FEFO order with the later-expiring batch created
first so insertion order cannot pass by accident; `[]` for an unknown SKU; a batch FR2 has
depleted drops out (ACTIVE only, applied through `ItemSoldMessageHandler` exactly as
`ItemSoldConsumptionTest` does); a missing or blank `sku` answers 400 with spec-04's
`ErrorResponse` **from a `HandlerMethod`**, the same vacuous-pass guard the 404 test uses.

**The feature (`ee69656`).**

| Layer | File | Change |
|---|---|---|
| `domain` | — | **No change** |
| `application` | `BatchApplicationService` | `findActiveBatches(String sku)` → `ApplicationResult<List<BatchView>>`; `VALIDATION_FAILED` when `sku` is null or blank; otherwise `findActiveBySkuOrderByExpiryAsc` mapped through the existing `toView(batch, now)` |
| `infrastructure.repository` | `StockBatchRepository` | Javadoc on the FEFO query only: it now has two callers, so FR4's order and FR2's cannot disagree |
| `presentation` | `BatchController` | `listBatchesBySku` on `@GetMapping` with `@RequestParam(required = false)`, so a missing `sku` still reaches the handler; 400 translates the typed result, 200 maps the list |

### 1.3 `pos-simulator`

One commit, two main classes, two test classes, JDK 21 only — `java.net.http.HttpClient`,
`java.util.Random`, hand-built JSON. No Spring, no Jackson, and no `contracts` class either: the
simulator speaks spec-04's REST contract, not the Published Language, so it runs from
`target/classes` with nothing else on the classpath.

| File | Role |
|---|---|
| `PosSimulator` | `main`, the `Options` record (`--key=value` parsing, defaults, `transactionCount`, `interval`), batch seeding, the paced transaction loop, the per-request line and the summary, exit codes |
| `SkuMix` | The one helper: parses `SKU:weight,...`, draws a SKU in proportion to weight from the `Random` it is handed |
| `PosSimulatorTest` | Defaults, overrides, rejected arguments, the quantity draw, both JSON shapes, identifier extraction, and three whole runs against a JDK `com.sun.net.httpserver` stand-in: a seeded run of ten transactions, a run of 400s, a refused connection |
| `SkuMixTest` | Order, round-trip, repeatability from the seed, proportion to weight, single SKU, rejected specs |

Usage, exactly as the javadoc states it:

```
java -cp pos-simulator/target/classes com.freshflow.possimulator.PosSimulator \
    [--sales-url=http://localhost:8082] [--inventory-url=http://localhost:8081] \
    [--rate=30] [--duration=120] [--seed=42] \
    [--skus=SKU-YOG-1KG:0.7,SKU-STR-250G:0.3] [--till=TILL-01] [--seed-batches=false]
```

With `--seed-batches=true` it first `POST`s one batch per SKU to `inventory-service` — 400 units,
expiring 70 minutes out, unit cost 2.10 AUD, `supplierRef SIM-n` — and prints each `batchId`. Then
it `POST`s `/api/transactions` at `--rate` per minute for `--duration` seconds, one line item per
transaction, SKU drawn from the mix, quantity 1–3 weighted 60/30/10, `occurredAt` stamped from the
clock at send time in ISO-8601 UTC. One line per request with the HTTP status and the returned
identifier; a summary line `sent= created= failed=`. Exit `0` when every transaction was a 201,
`1` otherwise or on a rejected argument, `2` when a service refused the connection, reported with
the URL. Smoke-tested from the classpath: `--help` exits 0; `--sales-url=http://localhost:1`
prints `cannot connect to http://localhost:1/api/transactions ... Exiting with code 2`;
`--rate=fast` prints the reason and the usage and exits 1.

The module's description could not go in its `pom.xml` (poms are off limits), so it is the class
javadoc of `PosSimulator`.

### 1.4 Verification

`./mvnw -o test` through PowerShell before each feature commit, and `./mvnw -o package` as the
final run, which runs every test and leaves `pos-simulator/target/classes` in place. The counts
are in §5. The two `domain` packages were checked by inspection: every import under
`sales-service/.../domain` and `inventory-service/.../domain` is `java.*` or the package itself.
`sales-service` gained no interface, no repository and no aggregate; the only new type in its
`domain` is the nested value record `PriceSnapshot.PriceInForce`. No module gained a dependency.

---

## 2. What I assumed that the specs did not state

1. **The promotion window is half-open, `[effectiveFrom, effectiveTo)`.** FR6b says a scan "after
   `effectiveTo`" reverts and that a markdown is in force "only inside its promotion window";
   neither says what happens at the boundary instants. FR10 describes a markdown as ending "at the
   close of its promotion window", and a window that still applied at its own closing instant
   would never quite close. In force from `effectiveFrom` inclusive, over at `effectiveTo`.

2. **A price rule newer than the markdown displaces it; an older one keeps it.** The brief asked for
   this choice and asked for it to be documented. The reasoning: `discountedPrice` was authorised
   by Pricing against the base price in force at the time — spec-03 §3 assesses the floor against
   the SKU's `PriceRule` — and the contract carries it "so that the price a till charges is the one
   the Pricing context authorised". A newer rule is a different base price; the old authorisation
   no longer describes it, and charging it would be charging a discount Pricing has not authorised
   against the rule in force. The base price is the conservative failure. I made the rule
   symmetric — a markdown *older* than the rule in force is stale on arrival too — because spec-01
   §2.6 forbids arrival-order dependence, and applying the rule only when the rule arrives second
   would have made the outcome depend on which binding replayed first.

3. **The markdown in force for a SKU is the latest by `occurredAt`.** Sales does not know batches
   (spec-03 §0), two batches of one SKU may each have an ACTIVE plan, and the specs do not say
   which price the till charges then. Last-write-wins by event time is the rule the sibling topic
   already follows.

4. **A markdown that arrives before its rule is held, and the SKU stays a 400 until the rule
   arrives.** A markdown alone carries no base price, unit cost or category; selling at a
   discounted price with no rule in force would be FR6a's silent-zero-price failure in a
   different coat.

5. **A `markdown-applied` whose `effectiveTo` is not after `effectiveFrom` is malformed** and
   dropped, like an event without an `eventId`. Pricing's own invariant forbids it (spec-03 §3,
   invariant 2), so it cannot be a real authorisation.

6. **One seen-set serves both topics.** Event ids are UUIDs minted per publication; a collision
   across topics is not a case worth a second set.

7. **FR4's 400 is the application layer's, so `sku` is `required = false` on the controller.**
   spec-01 §1.1 (M-9) puts request validation in the application layer and its body in spec-04's
   `ErrorResponse`; with `required = true` Spring would answer first, with its own body, and the
   test's `HandlerMethod` guard would fail. A blank `sku` is the same failure as a missing one,
   which is what spec-04's "missing or blank" says. The error code is `VALIDATION_FAILED`, the
   code FR1 already uses for shape failures.

8. **`remainingShelfLifeHours` on the list is computed exactly as on the single-batch read** —
   whole hours, truncated, against the service clock at the moment of the request — because
   spec-04 describes both the same way and one `toView` serves both.

9. **The simulator's numbers that no spec states**: quantity 1–3 weighted 60/30/10; the number of
   transactions is `ceil(rate × duration / 60)` so a short run still sends one; pacing is against
   an absolute schedule from the start instant, so a slow response does not drift the rate;
   `--duration=0` with `--seed-batches=true` seeds and stops; a non-201 answer is counted and the
   run continues, ending with exit 1; a refused or timed-out *connection* ends the run with exit 2
   because every later request would fail the same way. 400 units, 70 minutes and 2.10 AUD are the
   brief's values — 70 minutes so that FR13 has something to find within a demonstration.

10. **"`item-sold` carries that unit price" is asserted at the `SaleEventPublisher` port.** In the
    test profile the Kafka adapter is not bound (`freshflow.messaging.kafka.enabled: false`), so
    the test reads the `SaleTransaction` handed to the port and asserts the discounted price and
    `markdownApplied` on its line. `KafkaSaleEventPublisher` copies `LineItem.unitPrice` onto
    `ItemSoldEvent.unitPrice` verbatim and was not changed; the transport itself is verified
    against the real broker in the Week 6 log, as before.

---

## 3. Deviation from the specs

**None from any requirement, contract, ruling or ADR.** No dependency was added, no `pom.xml`
touched, `contracts` untouched, `application-test.yml` untouched, and every pre-existing test
passes with its assertions as written.

One placement decision sits at the edge of spec-01 §2.3's wording and is recorded here rather
than left for a reviewer to find. §2.3 says the `sales-service` `domain` package "holds plain data
structures only", and `PriceSnapshot#priceAt(Instant)` is a method with a conditional in it. It
is placed there on the brief's instruction, and on the same reasoning the Week 5 code already
recorded for `Money#multiply`: a query over a value's own fields — which of the snapshot's two
prices is in force at an instant it is handed — is value arithmetic, not a business invariant.
The business decisions remain in the transaction script, where spec-01 §2.5 and ADR 0002 put
them: that a line is priced at the transaction's `occurredAt` rather than at processing time, that
an absent SKU is a 400, and which instant to ask the snapshot about. The reviewer's check from
ADR 0001 still holds — the package declares no interface — and it still imports nothing outside
`java.*`. If the team reads §2.3's "plain data structures only" more strictly, the method moves to
the script unchanged in behaviour; the tests that pin it are on the feed and the endpoint, not on
the method's location.

The test commits stub the type surface they need, Phase A style, so that each `test(...)` commit
compiles and shows the new tests red and the old ones green. That is commit hygiene, not a
deviation; it is noted so that nobody reads a throwing `priceAt` in `6661eed`'s history as a
half-finished feature.

---

## 4. Anywhere I judged the spec wrong — flagged, NOT acted on

1. **spec-02's "Requirements to Tactics" table is now wrong in two rows.** The FR4 row reads
   "◐ single-batch read only; SKU-level query is `[POST-MVP]`" and the FR6b row reads "Consumes
   `markdown-applied`, `markdown-expired` ... ✗ `[MVP-PLUS]`, blocked on FR9/FR10". After B-7 the
   consumption column is wrong on its own terms, and after this session the Built column is stale
   for both. The table calls itself "a status column, not a specification", and the brief said to
   touch FR6b/FR4 text only where behaviour differs — it does not — so it is left for the team's
   next status refresh.

2. **spec-01 §2.6 Table A omits `discountedPrice` from `markdown-applied`'s key fields.** The
   contract carries it, FR6b depends on it, and the contract's own javadoc explains why it is
   carried rather than recomputed. The row should list it.

3. **No spec states the consumption rules for `markdown-applied`.** §2.6 states deduplication and
   last-write-wins per SKU for `price-rule-published` only; the contract's javadoc adds
   deduplication for this topic; the per-SKU last-write-wins among markdowns and the
   newer-rule-displaces rule (§2, items 2 and 3) exist only in the adapter's javadoc and tests.
   They are user-visible — they decide what the till charges — and belong in spec-03 §2 or
   spec-01 §2.6 by a team decision, the way the B-7 ruling was recorded.

4. **A `pricing-service` restart will displace every attached markdown.** `PriceRuleSeeder`
   re-announces the seeded rules on `ApplicationReadyEvent` with `occurredAt` stamped from the
   clock, so each restart publishes rules newer than every markdown Sales holds, and rule 4
   displaces them until `markdown-applied` is published again. The behaviour is the conservative
   one and is logged (`[displaced] ...`), but for the B3-style end-to-end run it means: start
   `pricing-service` before activating a plan, or have it re-announce ACTIVE plans after its rules.
   This is a design point for the engineer building FR9, raised here rather than solved by
   weakening rule 4.

5. **Neither FR6b nor spec-04 says which boundary of the promotion window is inclusive.** §2 item 1
   records the choice; the spec should state it so that `pricing-service`'s `PromotionWindow` and
   this snapshot agree by rule rather than by coincidence.

6. **Two javadocs in `inventory-service` still say the repository interface is "declared in
   `domain`"** — the class comment of `StockBatchRepository` and of `JpaStockBatchRepository` —
   when ADR 0005 moved it to `infrastructure.repository` on 29 September. I edited the method
   comment on the FEFO query and deliberately left the class comments alone: they are the
   relocation's loose end, not FR4's, and the 29 September log is where that fix belongs.

7. **`pos-simulator/pom.xml` and the `contracts` dependency.** The pom's comment says "the scan
   stream is expressed in the shared contract", and the module depends on `contracts`. The
   simulator as built needs nothing from it: it speaks spec-04's REST contract, which is the only
   way FR5 admits a scan, and the Published Language is what `sales-service` then puts on the
   wire. The dependency is harmless and the pom is out of scope; the comment is slightly
   misleading and could go when the pom is next touched.

Recorded for the team.

---

## 5. Test results — `./mvnw -o package`, `LLM_API_KEY` and `LLM_MODEL` unset

| Module | Before (`1e9015f`) | After | Added |
|---|---|---|---|
| contracts | — | — | — |
| inventory-service | 17 | **21** | 4 — `BatchApiIntegrationTest` 5 → 9 |
| sales-service | 9 | **19** | 10 — `PriceRulePublishedSnapshotFeedTest` 6 → 14, `TransactionApiIntegrationTest` 3 → 5 |
| pricing-service | 2 | 2 | — |
| insight-service | 36 | 36 | — |
| pos-simulator | — | **15** | 15 — `PosSimulatorTest` 10, `SkuMixTest` 5 |
| **Total** | **64** | **93** | **29** |

0 failures, 0 errors, 0 skipped. `BUILD SUCCESS`. No test touches the network beyond the loopback
stand-in in `PosSimulatorTest`, and no test calls a model.

---

## 6. One line for the team

A markdown now reaches the till, a supervisor can see the shelf in the order it will be sold, and
there is something to press that makes the sales stream happen — and the only rule anyone will
argue about is the one in §2 item 2, which is why it is written down three times.
