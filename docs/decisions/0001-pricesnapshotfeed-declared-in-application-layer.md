# ADR 0001 — `PriceSnapshotFeed` is declared in the application layer of `sales-service`

| Field | Value |
|---|---|
| Status | **Accepted** |
| Date | 26 August 2026 |
| Deciders | Project team |
| Raised as | Phase 2 finding J-2 (`docs/ai-log/2026-08-26-phase-2.md` §4) |
| Affects | `sales-service`, spec-01 §1.3, spec-03 §2, FR6a |
| Supersedes | Nothing |

---

## Context

spec-01 §1.3 rules that `sales-service` MUST NOT call `pricing-service` synchronously to
obtain a price. Instead it maintains its own local `PriceSnapshot`, fed through an inbound
port it owns, `PriceSnapshotFeed`:

- **Week 5** — the bound adapter seeds the snapshot from static configuration in
  `application.yml`. No broker runs.
- **Week 6 onward** — a Kafka adapter subscribing to `price-rule-published` replaces it
  behind the same, unchanged port signature.

§1.3 states that the port is "declared in `sales-service`" and stops there. It does not say
**which layer** declares it. That is not a cosmetic question:

- spec-01 §2.3 designates `sales-service` **Transaction Script** and explicitly forbids
  repository abstractions in its domain layer.
- An inbound port abstracting an external data source is close enough to a repository that
  its placement is not self-evident.
- Getting it wrong is a layering violation, and spec-01 opens by stating that the
  architecture is the assessed artefact.

The decision had to be made before Phase 4 implements FR6a, which reads the base price
through this port.

## Decision

**`PriceSnapshotFeed` is declared in `com.freshflow.sales.application`.**

Not in `com.freshflow.sales.domain`.

Its adapters — the Week 5 static-configuration seeder and the Week 6 Kafka consumer — are
implemented in `com.freshflow.sales.infrastructure`, as adapters always are.

## Rationale

**1. §2.3 forbids repository abstractions in this service's domain layer.**
`PriceSnapshotFeed` is an abstraction over an external source of data. Declaring it in
`domain` would breach that ruling in substance even if the type is not literally named a
repository. The prohibition is about the shape of the abstraction, not its name.

**2. L3 slide 11 places these interfaces in the application layer.**
The subject material is the governing authority where the specs are silent, and it puts
inbound port interfaces in the application layer.

**3. Under Transaction Script, the application layer is where behaviour lives.**
spec-01 §2.5 makes the application service the invariant-enforcement point for
`sales-service` precisely because §2.3 denies it an aggregate. The port belongs beside the
transaction script that drives it, not in a domain layer that holds only plain data.

## Consequences

**Positive**

- No repository-shaped abstraction appears in the sales domain package, so §2.3 holds.
- The dependency rule of §2.2 is satisfied unchanged: `infrastructure` depends inward on
  `application`, never the reverse. The Week 5 → Week 6 adapter swap still changes no
  application-layer code, which is the whole point of §1.3's Ports and Adapters framing.
- The sales `domain` package stays what §2.3 intends it to be — `SaleTransaction`,
  `LineItem`, `PriceSnapshot` as plain data structures with no behaviour.

**Negative / accepted trade-off**

- This departs from the textbook hexagonal-architecture convention of declaring ports in
  the innermost layer. Accepted knowingly: that convention presumes a Domain Model service,
  and `sales-service` is not one. The convention is not abandoned globally — see below.

**Explicitly NOT affected**

- `inventory-service` and `pricing-service` are both **Domain Model** (§2.3). Their
  repository interfaces — `StockBatchRepository`, `MarkdownPlanRepository` — remain declared
  in `domain` per §2.2 and spec-03 §§1 and 3. This ADR is scoped to `sales-service` and does
  not generalise.
  *(Superseded on this point by ADR 0005, 29 September 2026: those interfaces now live in
  `infrastructure.repository`. The scope statement, that this ADR concerns `sales-service`
  only, still holds.)*

## Compliance

- spec-01 §1.3 amended: a **Layer** row added to the ruling table, plus a subsection stating
  the placement and its reasoning.
- spec-01 §2.3 amended: notes that inbound ports for a Transaction Script service are
  declared in `application`.
- `com.freshflow.sales.application` `package-info.java` records the ruling at the point of
  use.

## Verification

A reviewer can confirm compliance by checking that
`sales-service/src/main/java/com/freshflow/sales/domain/` contains no interface type at all,
and that `PriceSnapshotFeed` appears under `.../sales/application/`.
