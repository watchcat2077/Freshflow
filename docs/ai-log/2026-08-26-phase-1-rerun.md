# Phase 1 — Re-inspection after the 26 Aug 2026 spec amendments

**Scope of this pass**: only the Week 5 scope defined in CLAUDE.md — `POST /api/batches`,
`GET /api/batches/{batchId}`, `POST /api/transactions`, `GET /api/prices/{sku}`, H2, no broker.
**Verdict**: **HALT. One blocker remains (B-5).**

## Blockers cleared by the amendments
- B-1 — `PriceRule.marginFloor` added; `GET /api/prices/{sku}` can now return `marginFloorRatio`.
- B-4 — `effectiveTo` added to `markdown-recommended`. (Out of Week 5 scope anyway.)
- B-7 — FR6 split into FR6a `[MVP-CORE]` / FR6b `[MVP-PLUS]`; the MVP-CORE-on-POST-MVP dependency is gone.
- B-2, B-3, B-6 — untouched, but all sit on the `/api/markdown-plans` + 422 path, which CLAUDE.md
  puts out of scope this week. Correctly deferred to Week 7.
- M-9 — interim ruling recorded (hand-rolled validation in the application layer). Actionable.
- C-1 — milestone legend re-baselined.

## Remaining blocker

### B-5 — `sales-service` still has no legal source of `basePrice`
`POST /api/transactions` is in Week 5 scope. `TransactionResponse` requires `totalAmount` and
per-line `unitPrice`, and FR6a requires "the base price in force for that SKU at `occurredAt`".

There is still no permitted channel that carries a base price into Sales:
- spec-03 §2 `PriceSnapshot` is "maintained from `markdown-applied` and `markdown-expired`" — neither
  event carries a monetary amount (spec-01 §2.6, unchanged).
- spec-01 §1.3 permits synchronous REST **only** for agent tool calls from `insight-service`; a
  Sales→Pricing call is forbidden.
- spec-01 §1.3 forbids a shared database; §1.4 makes `PriceRule` Pricing-owned.
- No `price-rule-published` topic was added.
- This week there is no broker at all, so an event-based fix is not Week-5-viable either.

FR6a's new criterion "satisfiable without any `[MVP-PLUS]` or `[POST-MVP]` requirement being built"
is therefore not met by any construction the specifications currently permit. The amendment asserts
the property without supplying the mechanism.

**Why blocking, not deferrable**: unlike B-2/B-3/B-6, this sits on an endpoint CLAUDE.md names as a
Week 5 deliverable. Phase 3 cannot write the `POST /api/transactions` 201 assertion without knowing
where `unitPrice` comes from, and Phase 4 cannot implement it without violating spec-01 §1.3.

**Needs a team ruling. Options (not applied):**
(a) seed a `PriceSnapshot` table in `sales-service` from static configuration for Week 5, and record
    a Week 6 obligation to replace it with a `price-rule-published` subscription;
(b) grant an explicit, documented §1.3 exception for a Sales→Pricing REST read;
(c) add `price-rule-published` to spec-01 §2.6 now and accept that FR6a slips to Week 6 with the broker.
Option (a) is the only one that keeps the Week 5 target intact; it needs to be written into spec-01
§1.3 or §1.4 as a stated interim, not adopted silently.

## Non-blocking, but flag for the team
- **FR4 is still `[POST-MVP]`** while CLAUDE.md makes `GET /api/batches/{batchId}` a Week 5 deliverable.
  The endpoint is fully specified, so it is implementable — but the tag contradicts CLAUDE.md's
  "never implement POST-MVP unprompted" rule. Retag FR4 `[MVP-CORE]` or note the authorisation.
- **The re-baselined legend now says `[MVP-CORE]` is due Sunday 30 August**, which pulls FR12
  (velocity, `[MVP-CORE]`) into Week 5 — yet CLAUDE.md puts all `/api/insight` endpoints out of
  scope. Either FR12 is retagged `[MVP-PLUS]` or the legend needs a Week-5 carve-out.
- `GET /api/prices/{sku}` returns `effectivePrice` and `activeDiscountTier`. With no markdown plans
  in Week 5 these degenerate to `basePrice` and `null`. Assumed, not stated.

## Phase gate
Phase 2 remains **BLOCKED** pending a ruling on B-5.
