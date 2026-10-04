# ADR 0002 — Ports and invariant placement for a Transaction Script service

| Field | Value |
|---|---|
| Status | **Accepted** |
| Date | 26 August 2026 |
| Deciders | Project team |
| Raised as | Phase 4 findings V-1, V-2, V-3 (`docs/ai-log/2026-08-26-phase-4.md` §2) |
| Affects | `sales-service`, spec-01 §§2.3 and 2.5, spec-03 §2 |
| Relates to | [ADR 0001](0001-pricesnapshotfeed-declared-in-application-layer.md), which this generalises |
| Supersedes | Nothing. It corrects spec-03 §2 and clarifies spec-01 §2.3 |

---

## Context

Phase 4 implemented `sales-service` and hit three points where the specification, read
literally, contradicted either itself or spec-01 §2.2 — the dependency rule, which is a HARD
CONSTRAINT. The implementation resolved each in favour of §2.2 and flagged all three rather than
proceeding silently. The team has now accepted all three as implemented. This ADR records the
reasoning and the resulting spec amendments so that the specification and the code no longer
disagree.

The common root is that spec-01 §2.3 describes `sales-service` mostly by what it must *not*
have — "Aggregates and repositories here would be over-design" — without saying what it may have
instead. Three separate questions fell into that gap.

## Decision 1 (V-2) — what "no repository abstractions" actually forbids

**Forbidden in `sales-service`:** an aggregate-oriented repository. Concretely, any interface
with query methods (`findById`, `findActiveBySku`, `findAll`), and any interface whose signature
returns an aggregate. Both presuppose the aggregate that §2.3 denies this service.

**Permitted:** a **write-only, single-method persistence port**, declared in the **application**
layer, implemented by an adapter in `infrastructure`. It takes a plain data structure and returns
nothing.

Implemented as `SaleTransactionRecorder`, with the single method `void record(SaleTransaction)`.

### Why the permitted form is required, not merely tolerated

spec-03 §2 said "The application service orchestrates directly against JPA entities." That is not
achievable under spec-01 §2.2:

- The JPA entities cannot live in `domain` — §2.2 forbids a domain package from importing JPA.
- So they live in `infrastructure`.
- An application service importing them would make `application` depend on `infrastructure`,
  which §2.2 permits in no service.

The two clauses cannot both be satisfied. §2.2 is the HARD CONSTRAINT and wins; spec-03 §2 was
simply wrong and has been corrected. A narrow outbound port is the only construction that stores
data while keeping the dependency arrow pointing inward. It also matches L3 slide 11, which
places these interfaces in the application layer.

### The boundary this draws, and where it will be tested

The distinction is a *kind* of abstraction, not a count of interfaces. When FR7 — query
transaction history, `[POST-MVP]` — is built, a read path will be needed. **It must not be added
to this port.** Adding `findById` converts `SaleTransactionRecorder` into exactly the
aggregate-oriented repository forbidden above. A separate read-side abstraction is the correct
answer, and that decision belongs to the week that builds FR7.

## Decision 2 (V-1) — outbound ports go where the pattern puts the behaviour

Port placement follows the service's §2.3 domain layer pattern:

| §2.3 pattern | Services | Ports declared in |
|---|---|---|
| **Domain Model** | `inventory-service`, `pricing-service` | `domain` — inbound and outbound alike, alongside the repository interfaces of §2.2 *(amended by ADR 0005, 29 September 2026: the repository interfaces moved to `infrastructure.repository`; the event ports stay in `domain`)* |
| **Transaction Script** | `sales-service` | `application` — outbound ports beside the inbound `PriceSnapshotFeed` of ADR 0001 |
| **No domain layer** | `insight-service` | `application` — there is no `domain` package to declare them in |

So `inventory-service` declares `DomainEventPublisher` in `domain`, while `sales-service`
declares `SaleEventPublisher` in `application`. Both are implemented by logging adapters in
`infrastructure`.

**Rationale.** ADR 0001 already placed this service's *inbound* port in `application`. Applying a
different rule to its *outbound* ports would split one service's ports across two layers on no
principle, and would put an interface into a domain package whose agreed description — spec-01
§2.1, as reworded for finding J-1 — is "plain data structures only". The generalisation is
simply: a port lives where that service's behaviour lives, and §2.3 is what decides where that
is.

## Decision 3 (V-3) — the transaction script is the enforcement point

For a Transaction Script service the **transaction script itself enforces the business
invariants**. This is not a weaker arrangement tolerated for convenience: it is the only one
available, because §2.3 denies the service an aggregate to hold the rules. §2.5's principle —
invariants live in the innermost layer the service's pattern provides — resolves to the
application service for `sales-service`, and to `domain` for `inventory-service` and
`pricing-service`.

For `sales-service` that covers the empty-line-items rule, the non-positive-quantity rule, and
FR6a's "a scan of a SKU absent from the snapshot is a `400`, never a silent zero price".

**Unchanged and absolute:** in no service, under any pattern, may an invariant live in a
controller, and none may be delegated to an LLM prompt. The margin floor in particular remains in
`pricing-service`'s domain layer, which is the Anti-Corruption Layer of spec-01 §1.5.

## Consequences

**Positive**

- The specification and the code agree. Phase 4 shipped with three flagged divergences; there are
  now none.
- §2.2 holds unbroken in all four services, verifiable mechanically: no `application` package
  imports `infrastructure`, and no `domain` package imports anything but the JDK.
- `sales-service`'s domain package still declares no interface at all, which remains the
  one-line reviewer check from ADR 0001.
- The rules are now stated positively — what a Transaction Script service *may* have — so the
  next service designated Transaction Script does not rediscover the same gap.

**Negative / accepted trade-off**

- `sales-service` now has three interfaces in its application layer where a naive reading of
  "Transaction Script" expects none. Accepted: each is a port at a boundary the service does not
  own, and none is aggregate-shaped.
- The forbidden/permitted line is a judgement about the *shape* of an interface rather than a
  mechanical count, so it needs a reviewer rather than a grep. The FR7 note above marks the exact
  place it will be tested.

**Explicitly NOT affected**

- `inventory-service` and `pricing-service` are Domain Model. Their repository interfaces
  (`StockBatchRepository`, `PriceRuleRepository`) and their outbound ports stay in `domain` per
  §2.2 and spec-03 §§1 and 3. Nothing here generalises to them.
  *(Superseded in part by ADR 0005, 29 September 2026: the two repository interfaces now live
  in `infrastructure.repository`; the outbound event ports are still in `domain`. The claim in
  Consequences that "no `application` package imports `infrastructure`" is narrowed by ADR 0005
  to "imports nothing from `infrastructure` except `infrastructure.repository`".)*

## Compliance

- spec-01 §2.3 — forbidden/permitted table for "no repository abstractions", plus the port
  placement table and the FR7 warning.
- spec-01 §2.5 — the Transaction Script enforcement point stated explicitly, and the
  never-in-a-controller rule restated as applying to every service and pattern.
- spec-03 §2 — "orchestrates directly against JPA entities" corrected, with the reason it was
  wrong recorded rather than quietly deleted.

## Verification

- `sales-service/src/main/java/com/freshflow/sales/domain/` declares no interface.
- `SaleTransactionRecorder` has exactly one method, returning `void`, taking a domain record.
- No `application` package imports `com.freshflow.*.infrastructure`.
- `SaleEventPublisher` and `PriceSnapshotFeed` are both under `.../sales/application/`;
  `DomainEventPublisher` is under `.../inventory/domain/`.
