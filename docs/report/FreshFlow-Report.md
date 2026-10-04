# FreshFlow

## Real-time expiry risk detection and agentic markdown decision support for grocery retail

**CSCI318 Software Engineering Practices & Principles, Spring 2026**
**Group 06, "318 Hero"**

| Member | Student number | Role |
|---|---|---|
| Zihua (Andy) Zeng | 7164695 | AI and streaming lead: insight-service, Kafka Streams topology, the two agents, specifications and AI process |
| Reuben Kruger | 8973878 | Domain services lead: inventory-service and pricing-service domain models, use cases, Week 8 progress video, Postman collection |
| Modhuri (Madz) Dey Sithi | 9184843 | Platform and integration lead: sales-service, Kafka and Docker environment, architecture and UML diagrams |

### Member contribution

[[TEAM TO CONFIRM in the Week 11 meeting. The coordinator confirmed on 22 September 2026 that equal ratings are acceptable when the group agrees. Replace the bracketed text with the agreed rating per member and a justification of what each member did, naming artefacts in the repository: for example commits, diagrams in docs/diagrams, use cases, slides, minutes.]]

| Member | Rating | Justification |
|---|---|---|
| Zihua (Andy) Zeng | [[contributed / NN%]] | [[...]] |
| Reuben Kruger | [[contributed / NN%]] | [[...]] |
| Modhuri (Madz) Dey Sithi | [[contributed / NN%]] | [[...]] |

I agree with my group member contribution report and ratings. ______________________ (Zihua Zeng)

I agree with my group member contribution report and ratings. ______________________ (Reuben Kruger)

I agree with my group member contribution report and ratings. ______________________ (Modhuri Dey Sithi)

---

## Abstract

FreshFlow is a prototype of an AI-powered, cloud-native application for a grocery retailer's perishable stock. It watches point-of-sale scans as a Kafka event stream, computes the rolling sell-through velocity of each product, joins that velocity against the live state of every stock batch, and flags batches that are unlikely to clear before their expiry date. For each flagged batch an LLM-backed agent gathers the facts through tools, proposes a discount tier with a written rationale, and leaves a persisted trace of every tool call. A second agent writes a plain-language end-of-day briefing from the same evidence. The agents only propose. Whether a markdown is allowed is decided by a deterministic business rule, the margin floor, inside the pricing service's domain layer, and that rule applies in the same way to a proposal from a person and to one from the agent.

The system is built as four bounded contexts, each a Spring Boot microservice with its own H2 database, integrated through Apache Kafka with Spring Cloud Stream, with Kafka Streams for the two real-time analyses and LangChain4j for the agentic components. The project was developed spec-first with an AI coding agent working under a four-phase gated pipeline, and the specifications, prompts, decision records and session logs that governed that process are part of the submitted repository.

Repository: https://github.com/watchcat2077/Freshflow (private until submission).

---

## Table of Contents

[[Generate in Word after the final edit.]]

---

## 1. Introduction

### 1.1 The business problem

A supermarket loses money on short-dated stock in two directions. If it discounts a batch too early it gives away margin on units that would have sold at full price anyway. If it discounts too late, or not at all, the batch reaches its expiry date on the shelf and is written off in full. The decision between the two sits with floor staff, and it depends on three facts that nobody can reasonably hold in their head across a whole store: how fast this product is selling right now compared with its normal rate, how many units remain in this particular batch (not the product line, since two deliveries of the same yoghurt expire on different days), and how many trading hours are left before that batch expires. In most stores the decision is made by walking the aisle and guessing.

FreshFlow is a decision-support system for that problem. It is not a pricing engine and it does not change prices on its own. It makes the three facts visible as they change, identifies the batches that need a decision, and asks an agent to propose one.

### 1.2 Objectives

The requirements in Section 2 are traced to five objectives. They were written down at the start of the project and have not changed.

| Objective | Statement |
|---|---|
| O1 | Reduce the value of perishable stock written off at expiry |
| O2 | Protect gross margin: no automated discount may sell below the margin floor set for the product |
| O3 | Remove the judgement burden from floor staff by surfacing which batches need action, and when |
| O4 | Make every markdown decision auditable, whether a person or an agent made it |
| O5 | Give the store owner end-of-day visibility without a dashboard |

The stakeholders behind them are the store manager who receives deliveries, the checkout operator at the till, the shift supervisor who decides on markdowns during the day, and the store owner who sets price rules and reviews the day. O2 and O4 are the ones that shape the architecture most: an automated discount that destroys margin is worse than no automation, and a recommendation that cannot be explained afterwards will not be trusted by the people who have to act on it.

### 1.3 Core capabilities

The system does six things, and Section 2 states them as requirements.

1. It records deliveries as stock batches with their own expiry date and cost, and deducts stock from the batch that expires soonest when a sale happens (first-expired, first-out).
2. It records sales at the till and prices each line at the price in force at the moment of scan, including any markdown that has been activated for that product.
3. It maintains price rules and a margin floor per product.
4. It computes, in real time from the sales stream, a rolling sell-through velocity per product and compares it with a baseline.
5. It joins that velocity with the live state of every batch and flags the batches that will not clear before expiry.
6. It runs two agents: one that proposes a markdown for a flagged batch after gathering the facts through tools, and one that writes the store owner's end-of-day briefing. Both leave a persisted trace of what they looked at.

### 1.4 How AI adds value, and where it is not allowed to

The value of the agent is in the judgement it supplies between two numbers. The stream processing can say that a batch of 400 yoghurts has 9 trading hours left and is selling at 2.1 units an hour. It cannot say whether a 20 per cent or a 30 per cent markdown is the right response, whether the start time should be now or at the evening peak, or how to explain the choice to a supervisor in two sentences. A language model is good at exactly that kind of bounded reasoning over a small set of facts, provided the facts are supplied to it rather than recalled by it, which is why the agent reads them through tools rather than from its training data.

What the agent is not allowed to do is decide whether the markdown is permitted. A model is probabilistic; the same prompt can produce a different answer on a different day, and no prompt can be unit-tested. The margin floor is therefore enforced as a business invariant in the pricing service's domain model, and the pricing service treats the agent's recommendation as an untrusted proposal, in the same way it treats a proposal typed in by a supervisor. The lectures' framing fits this closely: AI should amplify architectural thinking rather than replace it, and the developer's job is to define the domains, events, constraints and invariants within which an agent operates (CSCI318 L1, slide 20). Combining a rule-based check with a model's recommendation is also one of the diversity techniques the dependability lecture recommends for AI components (L8, slide 9).

That split is the central argument of this report, and most of the design decisions in Section 3 exist to make it visible and testable.

---

## 2. Analysis, Requirements and Domain Modelling

### 2.1 Functional requirements

Sixteen functional requirements were specified in `specs/spec-02-requirements.md`, each with a user story, a scope tag and acceptance criteria. Twelve of them are presented here, each specified as a use case in `docs/report/section-2-1-use-cases.md` and illustrated with the UML activity diagram of the same number in `docs/diagrams/`. The four left out (FR3 write-offs, FR7 transaction history, FR10 automatic markdown expiry, and the `markdown-expired` half of FR6b) are reporting and housekeeping functions that would not demonstrate a further architectural pattern, and they were deliberately not built; where their absence matters it is stated, for example the briefing agent says that waste figures are unavailable rather than inventing them.

| UC | FR | Requirement (as a user story) | Objective | Built |
|---|---|---|---|---|
| UC-01 | FR1 | As a store manager I want to record a delivery as a stock batch with SKU, quantity, expiry and unit cost, so that expiry risk is tracked per batch | O1 | Yes |
| UC-02 | FR2 | As a store manager I want batch stock to decrease automatically when items are sold, FEFO and idempotently, so that remaining quantity is accurate without counting | O1, O3 | Yes |
| UC-03 | FR4 | As a shift supervisor I want to query current stock and remaining shelf life for a batch or SKU, so that I can verify what the system tells me | O3, O4 | Yes |
| UC-04 | FR5 | As a checkout operator I want sales recorded with their line items, so that every downstream analysis starts from a real sale | O1, O3 | Yes |
| UC-05 | FR6a, FR6b | As a checkout operator I want each line priced at the price in force at the moment of scan, including a live markdown, so that a markdown decision reaches the till | O2 | Yes |
| UC-06 | FR8 | As a store owner I want to maintain price rules and margin floors per SKU, so that discounting never destroys profitability | O2 | Yes |
| UC-07 | FR9 | As a shift supervisor (or the agent) I want to activate a markdown for a batch at a discount tier and window, so that the price change takes effect | O1, O2 | Yes |
| UC-08 | FR11 | As a store owner I want any markdown that would breach the margin floor to be rejected and the rejection recorded, so that no automated component can discount below profitability | O2, O4 | Yes |
| UC-09 | FR12 | As a shift supervisor I want a rolling sell-through velocity per SKU from the live sales stream, so that I can see demand as it changes (real-time analysis) | O1, O3 | Yes |
| UC-10 | FR13 | As a shift supervisor I want batches that will not sell out before expiry flagged as they are detected, so that I act in time (real-time analysis) | O1, O3 | Yes |
| UC-11 | FR14 | As a shift supervisor I want an agent to recommend a markdown tier for a flagged batch with a written rationale and an auditable trace, so that the decision is informed and explainable (agentic AI) | O1 to O4 | Yes |
| UC-12 | FR15 | As a store owner I want a plain-language end-of-day briefing of what was marked down, what sold through and what is at risk, so that I can review the day without a dashboard (agentic AI) | O4, O5 | Yes |

Each use case in the appendix file follows the same shape: actor, relevance to the objectives, preconditions, trigger, a numbered main flow written as what the system does for the actor, alternative flows derived from the requirement's own rejection rules, and postconditions. The activity diagrams were drawn from those flows, so the main flow is the straight path and each alternative flow is a decision diamond. Figure 2.1 shows UC-01 as the representative example; the other eleven are in the appendix and in the repository.

[[Figure 2.1: docs/diagrams/activity-UC-01.png. Figures 2.2 to 2.12: activity-UC-02 to activity-UC-12.]]

### 2.2 Real-time analysis requirements

Two of the user stories are real-time analyses over the sales stream, and both are implemented as one Kafka Streams topology in insight-service (Section 3.5).

FR12, rolling sell-through velocity (UC-09), windows the `item-sold` events by SKU on the producer-assigned time of sale, sums the quantity per hopping window, joins the result with a baseline velocity held in a table, and keeps the latest fully elapsed window per SKU in a state store that the REST endpoint `GET /api/insight/velocity/{sku}` reads through an interactive query. The value moves while you watch it, which is the point of the requirement.

FR13, expiry risk detection (UC-10), maintains a second table of the live state of every batch from `batch-received` and `stock-level-changed`, and when a velocity window closes it joins the velocity with every active batch of that SKU. A domain rule projects the sales achievable before expiry and compares them with the remaining quantity; a batch whose projected shortfall exceeds a threshold is marked at risk, and the transition into risk publishes a `sell-through-risk-detected` event, which is what triggers the agent. The risk list is served by `GET /api/insight/risks`.

Both analyses use event time rather than ingestion time. A sale that reaches Kafka late still lands in the window its `occurredAt` belongs to, and replaying the topic at a different speed produces the same velocities. That choice is a hard constraint in the architecture specification and is enforced by a single timestamp extractor shared by every input topic.

### 2.3 Agentic AI requirements

Two user stories use an agentic component, and they are served by two distinct agents behind two ports of the same shape.

FR14, the Markdown Decision Agent (UC-11), is triggered by the risk event. It is a tool-using agent in the ReAct style the lectures describe (L7, slides 19 to 20): the model reasons, asks for a tool, observes the result, and reasons again, inside a bounded loop. It has three tools and no more: `getBatchDetails`, which reads the batch's remaining quantity, expiry and cost from inventory-service; `getSellThroughRate`, which reads the current velocity from the FR12 state store; and `proposeMarkdown`, which records a recommendation for the flagged batch and nothing else. The orchestrator persists every tool call with its arguments and result, refuses a proposal that names a different batch, derives the end of the promotion window from the batch's expiry, and publishes `markdown-recommended` for pricing-service to validate.

FR15, the end-of-day briefing (UC-12), is a second agent with three read-only tools over the Insight context's own views: the current risk list, the markdown recommendations made that day, and the sell-through rate of any SKU the model wants to comment on. It writes a short summary for the store owner. The counts that accompany the summary are computed by the orchestrator from the tool results rather than taken from the model's text, so a number the model gets wrong cannot reach the caller as a fact, and the briefing is persisted with its tool calls so the narrative can be checked against its evidence.

The coordinator confirmed on 22 September 2026 that two user stories served by one agent would be acceptable; two agents were built because the second story has a different trigger (a request rather than an event), different tools and a different output, and because a second concrete example made the pattern easier to explain in Section 3.6.

### 2.4 Domain modelling

#### 2.4.1 Domain classes by sub-domain

Domain distillation (L2, slides 10 to 13) asks which parts of the problem are the reason the system exists, which support that core, and which are generic enough that any retailer solves them the same way. For FreshFlow the answer came out as follows.

| Sub-domain | Kind | Why | Domain classes |
|---|---|---|---|
| Expiry risk detection and the markdown decision | Core | This is the competitive value: knowing which batch to mark down, by how much, and proving the decision was sound. Nothing off the shelf does it for a store's own batches | `RiskAssessmentPolicy`, `BatchRiskView`, `SkuVelocityView`, `AgentTraceRecord`, `MarkdownProposal`, `EndOfDayBriefing`; `MarkdownPlan`, `MarginFloorPolicy`, `MarginFloorAssessment`, `DiscountTier`, `PromotionWindow` |
| Batch-level inventory tracking | Supporting | Necessary for the core and specific to perishables (two deliveries of one product are two batches), but not where the value is created | `StockBatch`, `BatchId`, `Quantity`, `ExpiryDate`, `BatchStatus`, `FefoDeductionPolicy`, `DeductionPlan`, `BatchReceived`, `StockLevelChanged` |
| Price-rule maintenance | Supporting | The margin floor is the constraint the core must respect; maintaining rules is routine | `PriceRule`, `MarginFloor`, `PriceRulePublished` |
| Sale transaction recording | Generic | Every retailer records a basket the same way; it could be bought rather than built | `SaleTransaction`, `LineItem`, `PriceSnapshot` |

`Money` and `Sku` appear in every sub-domain and are modelled separately in each, for a reason given below.

#### 2.4.2 The domain model diagram

Figure 2.13 is the UML domain model, drawn in UMLet from the code. It is grouped by bounded context rather than by sub-domain because the contexts are where the classes actually live; the mapping between the two is in the table above.

[[Figure 2.13: docs/diagrams/domain-model.png]]

Three things in the diagram are deliberate and are worth pointing out. `StockBatch` and `MarkdownPlan` are the only aggregate roots, because they are the only objects with invariants that span several values and a lifecycle to protect. The Sales context has no aggregate at all; its classes are plain records handled by a transaction script, which Section 3.2 explains. And `Money` and `Sku` are drawn inside every context, three and four times respectively, because there is no shared kernel.

#### 2.4.3 Why these are the bounded contexts

A bounded context is a linguistic boundary that defines where a model applies (L3, slides 30 and 35). The strongest evidence that FreshFlow needs four of them is that the word "product" means a different thing in each. In Inventory a product is a physical batch with a quantity and an expiry date, and the batch, not the product line, is the thing whose state matters. In Sales a product is a barcode scanned at a price at a moment in time; the batch it came from is unknown at the till and irrelevant to the receipt. In Pricing a product is a cost-and-margin constraint set: a base price, a unit cost and a floor. In Insight a product is a read-side projection, a velocity and a list of batches at risk, assembled from the other three contexts' events and never written back to them. Modelling those four as one `Product` class would have given it attributes and rules that contradict each other, which is the single-model failure the lectures warn about (L3, slides 23 to 29).

The mapping from sub-domains to contexts is close to one-to-one but not exactly so. Inventory, Sales and Insight each hold one sub-domain. The Pricing context holds the supporting price-rule sub-domain and the enforcement half of the core markdown sub-domain, because the margin floor and the plan that must respect it belong in the same transaction. The lectures describe one-to-one as the ideal rather than a rule (L3, slide 31), and the alternative, a fifth context for markdown plans that would need the price rule synchronously, would have created exactly the coupling bounded contexts exist to prevent.

The decision not to share `Money`, `Sku` and `Quantity` through a shared kernel follows the same reasoning. A shared kernel is a shared portion of two models that both teams must agree to change (L5, slide 20). Here the three contexts do not even agree on what the types mean: Inventory's `Quantity` must allow zero because a depleted batch has zero left, while a sale's quantity must be at least one; Pricing's `Money` carries margin arithmetic that Sales must never perform. The only shared artefact is the `contracts` module, which holds the integration event records and nothing else. It is the published language of the system, in the sense Evans gives the term, and its one-way nature is what keeps the contexts independently changeable.

---

## 3. Design, Architecture and Implementation

### 3.1 Microservice architecture

Figure 3.1 is the microservice architecture diagram and Figure 3.2 the event flow. Four services, one per bounded context, each with its own H2 database and no shared schema. The `contracts` module is a compile-time dependency of all four and contains only event records; `pos-simulator` is a command-line client used for demonstrations and is not a service.

[[Figure 3.1: docs/diagrams/A2-microservice-architecture.png (Madhuri). Figure 3.2: docs/diagrams/event-flow.png. Figure 3.3: docs/diagrams/context-map.png]]

| Service | Port | Publishes | Consumes | Synchronous calls |
|---|---|---|---|---|
| inventory-service | 8081 | `batch-received`, `stock-level-changed` | `item-sold` | none |
| sales-service | 8082 | `item-sold` | `price-rule-published`, `markdown-applied` | none |
| pricing-service | 8083 | `price-rule-published`, `markdown-applied` | `markdown-recommended` | none |
| insight-service | 8084 | `sell-through-risk-detected`, `markdown-recommended`, `velocity-baseline-published` (internal) | `item-sold`, `batch-received`, `stock-level-changed`, `sell-through-risk-detected` | `GET /api/batches/{id}` on inventory-service, from the agent's tool only; the LLM API |

Asynchronous messaging over Kafka is the primary and, with one exception, the only inter-service mechanism. The exception is the agent's `getBatchDetails` tool, which reads a batch over REST at the moment the agent asks for it. That call was ruled on explicitly (spec-06, ruling of 10 September 2026): the agent needs the unit cost and the batch state as they are now, a read of a fact with a 404 path and no write, which is a different thing from the one synchronous call the architecture forbids. Sales-service may never ask pricing-service for a price, because that would put a pricing decision on a synchronous dependency at the till; it keeps a local price snapshot fed only by `price-rule-published` and `markdown-applied` events (ADR 0001).

The context map in Figure 3.3 names the integration pattern on each relationship, following the vocabulary of L5, slides 19 to 22. Sales to Inventory is customer-supplier: Sales publishes the sale and Inventory conforms to it. Pricing to Sales is a published language: both sides agree to the event contract in `contracts`. The three producing contexts to Insight are conformist: Insight accepts the upstream event models without negotiation and projects them into its own views. The relationship from the agent to Pricing is an anti-corruption layer. Pricing re-validates every proposal against its own rule, so the model's output never enters the Pricing model as an authoritative command. That last pattern is the one most of the rest of this section is about.

Events are of two kinds, after a ruling recorded in spec-01 section 2.6: integration events, which cross a context boundary and are declared in `contracts`, and internal events, which one context both produces and consumes and which stay in that service's own packages. The distinction matters because putting an internal topic's payload in `contracts` would advertise an implementation detail as shared vocabulary. Every event of either kind carries an `eventId` for idempotent consumption and a producer-assigned `occurredAt` for windowing.

### 3.2 Layered architecture

Every service has the same four packages, and the package structure is the layer structure:

```
com.freshflow.<context>
├── presentation      REST controllers and request/response DTOs
├── application       application services, command handlers, read models, ports
├── domain            aggregates, entities, value objects, domain services, domain events
└── infrastructure    JPA entities and mappers, repository implementations, Kafka bindings,
    └── repository    the repository interfaces (ports) that application depends on
```

Figure 3.4 shows inventory-service as the worked example with the real classes in each layer, and Figure 3.5 the four services side by side.

[[Figure 3.4: docs/diagrams/layered-architecture.png. Figure 3.5: docs/diagrams/layered-architecture-all-services.png]]

**Naming.** The course specification calls the second layer the service layer; our package is named `application`. They are the same layer. The lecture that introduces the layering calls it the application service layer and describes it as representing the use cases of the application and hiding the complexity of the domain behind a façade (L3, slide 12), which is the description our package-info files carry. We kept the name because `application` is the vocabulary of the layered and ports-and-adapters literature the specifications cite, and renaming the package would have made the code disagree with its own specifications for no gain. The mapping is one-to-one:

| Course specification layer | FreshFlow package | Note |
|---|---|---|
| Presentation | `presentation` | Controllers map HTTP to commands and views to DTOs; they decide nothing |
| Service | `application` | Use-case orchestration, commands and handlers, read models, ports |
| Domain | `domain` | The model; imports only `java.*` |
| Infrastructure (data access) | `infrastructure` and `infrastructure.repository` | Persistence, messaging, external clients, configuration |

**Responsibilities and the dependency rule.** The presentation layer exposes the system's functions and translates between HTTP and the application layer's commands and views. The application layer represents the use cases: it validates the shape of a request, retrieves domain objects from a store, delegates the business decision to them, saves the result and publishes what happened. It contains no business rule, which is the lecturer's own description of application services (L4, slide 4, and L4, slide 42). The domain layer holds the model and every invariant, and depends on nothing: it is agnostic to the technicalities of the system (L3, slide 12). The infrastructure layer holds the technical details that let the application function, from JPA entities to Kafka bindings (L3, slide 13).

Dependencies face inward (L3, slide 11). The domain package of every service imports only `java.*`; no Spring, no JPA, no Jackson, no Kafka, and nothing from the other three packages. This is checked mechanically, by a test in each service that scans the domain sources and fails the build on any other import, so the rule cannot drift while nobody is looking. The application layer imports the domain and, for persistence, the repository interfaces; the infrastructure layer implements those interfaces and the domain's outbound ports (`DomainEventPublisher`, `PriceRuleEventPublisher`) and so points inward too.

**Two decisions about placement that the coordinator ruled on.** Repository interfaces were originally declared in `domain`, the Evans arrangement. The subject coordinator's answer to our question, by email on 17 September 2026 and in consultation on 22 September, was that repositories belong to the persistence layer, in a dedicated sub-package, and the corresponding lecture slide says the same (L4, slide 34). They were moved to `infrastructure.repository` and the decision, including the cost in dependency-inversion terms, is recorded as ADR 0005. The second ruling concerned insight-service, which was built as a CQRS read side with no domain package on the argument that it holds no transactional business data. The coordinator's view was that a CQRS context still has a domain layer, and in fact the service did have domain logic: the policy that decides whether a batch is at risk. It now lives in `com.freshflow.insight.domain` with the value it produces (ADR 0006), and all four services show the same four packages.

**A different domain-layer pattern per context.** The specifications assign the pattern per bounded context rather than uniformly (L3, slides 15 to 22). Inventory and Pricing use the Domain Model pattern: persistence-ignorant POJOs with behaviour, separate JPA entities and mappers in infrastructure. Sales uses Transaction Script: one procedure records a transaction, and the context deliberately has no aggregate root and no repository abstraction (ADR 0002), because recording a basket has little business logic and a full domain model there would have been over-engineering. Insight is a CQRS read side whose views are materialised by Kafka Streams and by the agents, with a small domain package for the rule that computes the risk view. Choosing the lighter pattern where the logic is light is itself a design decision the lectures ask for, and it is one the team would defend rather than apologise for.

### 3.3 Domain-Driven Design patterns

The tactical patterns realise the ubiquitous language in code, and each has a single responsibility (L4, slides 3 and 4). This section takes one representative of each pattern, shows the code, and says why it is that pattern and not another. Every snippet is from the repository as submitted; Javadoc is omitted for space.

#### Value object: `Money`

A value object has no identity, is defined by its attributes, is immutable and validates itself (L4, slides 5 and 8). `Money` is a Java record, so equality is attribute-based and the fields are final by construction. The compact constructor is where the self-validation lives: a blank currency or a negative amount is refused, and the amount is normalised to two decimal places so that 2.10 and 2.1000 compare equal, which `BigDecimal` would otherwise deny.

```java
public record Money(BigDecimal amount, String currency) {
    public static final int SCALE = 2;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    public Money {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
        if (currency.isBlank()) {
            throw new DomainRuleViolation("CURRENCY_REQUIRED", "Currency must not be blank.");
        }
        if (amount.signum() < 0) {
            throw new DomainRuleViolation("NEGATIVE_AMOUNT", "Money amount must not be negative.");
        }
        amount = amount.setScale(SCALE, ROUNDING);
    }
}
```

The lecture's example of a sub-optimal design is representing a bid amount as a bare integer (L4, slide 7). The same argument applies to `Sku`, `Quantity`, `ExpiryDate` and `BatchId`, which wrap a primitive so that the rules about it have somewhere to live: `Quantity` knows it may be zero but never negative, and `ExpiryDate` knows how to compute remaining shelf life.

#### Aggregate root: `StockBatch`

An aggregate is a consistency boundary around a group of objects, and the root is the only entry point through which the group is changed (L4, slides 21 and 22). `StockBatch` holds a batch's quantities, expiry, cost and status, and every change goes through a method that enforces an invariant. The factory method `receive` refuses a non-positive quantity and an expiry that is not in the future, which are FR1's two rejection rules; `deduct` refuses to go below zero, and to touch an expired or written-off batch.

```java
public static StockBatch receive(BatchId batchId, Sku sku, Quantity quantity, ExpiryDate expiryAt,
                                 Money unitCost, Instant receivedAt) {
    if (!quantity.isPositive()) {
        throw new DomainRuleViolation("QUANTITY_NOT_POSITIVE", "Received quantity must be greater than zero.");
    }
    if (!expiryAt.isAfter(receivedAt)) {
        throw new DomainRuleViolation("EXPIRY_NOT_IN_FUTURE", "Expiry must be after the receipt timestamp.");
    }
    return new StockBatch(batchId, sku, quantity, quantity, expiryAt, unitCost, BatchStatus.ACTIVE, receivedAt);
}

public void deduct(Quantity quantity, Instant now) {
    if (status == BatchStatus.WRITTEN_OFF) {
        throw new DomainRuleViolation("BATCH_WRITTEN_OFF", "A written-off batch cannot be deducted from.");
    }
    if (isExpired(now)) {
        throw new DomainRuleViolation("BATCH_EXPIRED", "An expired batch cannot be deducted from.");
    }
    if (remainingQuantity.isLessThan(quantity)) {
        throw new DomainRuleViolation("INSUFFICIENT_STOCK", "Stock on hand must never become negative.");
    }
    remainingQuantity = remainingQuantity.minus(quantity);
    if (remainingQuantity.isZero()) {
        status = BatchStatus.DEPLETED;
    }
}
```

Two details follow the lecture's advice closely. The entity exposes behaviour, `receive` and `deduct`, rather than setters (L4, slide 16), and the invariants are enforced in the methods and the factory rather than in the controller or the application service (L4, slides 17 and 18). The class carries no JPA annotation. Persistence is handled by a separate `StockBatchJpaEntity` and a mapper in the infrastructure layer, so the aggregate can be tested with no database and the compiler, not convention, keeps infrastructure out of it. The coordinator pointed out that `@Entity` is only a JPA mapping and says nothing about whether a class is a DDD entity; we took the stricter route of keeping the two apart, at the cost of a mapper per aggregate, and that trade-off is recorded in the Week 7 comparison with the coordinator's own reference code.

#### Aggregate root: `MarkdownPlan`, and the invariant that constrains the agent

`MarkdownPlan` is the second aggregate, in the Pricing context, and it carries the invariant the whole project is built around: a plan may only be activated if the discounted price keeps the margin at or above the floor. The aggregate does not compute the margin itself; it receives a `MarginFloorAssessment` produced by the domain service below and decides from it, which keeps the arithmetic in exactly one place.

```java
public void activate(MarginFloorAssessment assessment) {
    Objects.requireNonNull(assessment, "assessment");
    requireNotRejected("activated");                       // invariant 4: REJECTED is terminal
    if (status != MarkdownStatus.PROPOSED) {               // invariant 3
        throw new DomainRuleViolation("PLAN_NOT_PROPOSED",
                "Only a PROPOSED plan may be activated; this plan is " + status + ".");
    }
    if (assessment.requestedTier() != discountTier) {
        throw new DomainRuleViolation("ASSESSMENT_TIER_MISMATCH", ...);
    }
    if (assessment.breached()) {                           // invariant 1: the margin floor
        status = MarkdownStatus.REJECTED;
        rejectionReason = assessment.message();
        throw new MarginFloorBreach(assessment);
    }
    status = MarkdownStatus.ACTIVE;
    discountedPrice = assessment.resultingPrice();
}
```

The application service catches `MarginFloorBreach` inside its transaction, so the plan that just recorded itself REJECTED is committed rather than rolled back, and the controller maps the exception's assessment to the 422 body. The same service is called by the Kafka consumer for `markdown-recommended`, with origin AGENT, so the HTTP endpoint and the agent's recommendation are two entry points to one rule.

The other invariants listed in the domain specification are enforced in the same methods: only a PROPOSED plan may be activated, a REJECTED plan is terminal, the promotion window must end after it starts, and the origin (HUMAN or AGENT) is immutable after creation. A rejected plan is kept rather than discarded, because FR11 requires the rejection to be recorded and queryable, and an auditor reading it later should see what was asked for and why it was refused.

#### Domain service: `FefoDeductionPolicy` and `MarginFloorPolicy`

A domain service holds logic that does not belong to any one entity or value object, is stateless, and often orchestrates several domain objects (L4, slides 28 to 30). First-expired-first-out deduction is such a rule: a sale of three units may span two batches, so the decision of how much to take from which batch belongs to neither batch. `FefoDeductionPolicy` plans the allocation over the batches in expiry order and reports any shortfall as a fact rather than a negative allocation, and each `StockBatch` still gets to refuse its own part of the plan.

```java
public DeductionPlan planDeduction(List<StockBatch> batchesInExpiryOrder, Quantity soldQuantity, Instant now) {
    List<DeductionPlan.Allocation> allocations = new ArrayList<>();
    int outstanding = soldQuantity.value();
    for (StockBatch batch : batchesInExpiryOrder) {
        if (outstanding == 0) break;
        if (batch.isExpired(now) || batch.status() != BatchStatus.ACTIVE) continue;
        int available = batch.remainingQuantity().value();
        if (available == 0) continue;
        int taken = Math.min(available, outstanding);
        allocations.add(new DeductionPlan.Allocation(batch, Quantity.of(taken)));
        outstanding -= taken;
    }
    return new DeductionPlan(allocations, Quantity.of(outstanding));
}
```

`MarginFloorPolicy` is the Pricing context's domain service and the enforcement point of the anti-corruption layer. It spans `PriceRule` and `MarkdownPlan`, which is why it is a service and not a method of either. It returns a value object rather than a boolean, because the HTTP 422 response has to report the resulting price and margin, and recomputing those outside the domain would have put the rule in two places.

```java
public final class MarginFloorPolicy {
    public MarginFloorAssessment assess(PriceRule rule, DiscountTier tier) {
        Money resultingPrice = rule.priceUnder(tier);
        BigDecimal resultingMarginRatio = marginRatio(resultingPrice, rule.unitCost());
        return new MarginFloorAssessment(rule.basePrice(), rule.unitCost(), tier,
                resultingPrice, resultingMarginRatio, rule.marginFloor());
    }

    private static BigDecimal marginRatio(Money sellingPrice, Money unitCost) {
        BigDecimal price = sellingPrice.amount();
        if (price.signum() == 0) {
            return BigDecimal.ZERO.setScale(MarginFloorAssessment.RATIO_SCALE, Money.ROUNDING);
        }
        return price.subtract(unitCost.amount()).divide(price, MarginFloorAssessment.RATIO_SCALE, Money.ROUNDING);
    }
}

public record MarginFloorAssessment(Money basePrice, Money unitCost, DiscountTier requestedTier,
                                    Money resultingPrice, BigDecimal resultingMarginRatio, MarginFloor floor) {
    public boolean breached() {
        return resultingMarginRatio.compareTo(floor.ratio()) < 0;
    }
    public String message() {
        return "Discount tier " + requestedTier.name() + " yields margin " + resultingMarginRatio.toPlainString()
                + ", " + (breached() ? "below" : "at or above") + " floor " + floor.ratio().stripTrailingZeros().toPlainString();
    }
}
```

The margin is gross margin as a fraction of the selling price: resulting price less unit cost, divided by resulting price. With the seeded yoghurt rule (base 4.50, cost 2.10, floor 0.15) a 30 per cent markdown gives 3.15 and a margin of 0.333, which passes, while a 60 per cent markdown gives 1.80 and a negative margin, which is refused. Those two cases are the two examples in the API contract and the two requests in the demonstration.

#### Entity: `PriceRule`

`PriceRule` is an entity rather than a value object because it has an identity, the SKU, that stays fixed while its attributes change: a store owner may replace the base price or the floor and it is still the rule for that product (L4, slides 12 and 13). Its factory refuses a base price below cost and a currency mismatch between price and cost, so an impossible rule cannot be constructed.

```java
public static PriceRule of(Sku sku, Money basePrice, Money unitCost, String category, MarginFloor marginFloor) {
    if (!basePrice.currency().equals(unitCost.currency())) {
        throw new DomainRuleViolation("CURRENCY_MISMATCH", "Base price and unit cost must be in the same currency.");
    }
    if (basePrice.amount().compareTo(unitCost.amount()) < 0) {
        throw new DomainRuleViolation("BASE_PRICE_BELOW_COST", "Base price must not be below unit cost.");
    }
    return new PriceRule(sku, basePrice, unitCost, category, marginFloor);
}
```

#### Domain events: `BatchReceived` and its integration counterpart

A domain event records something that happened in the domain that other parts of the system care about (L4, slide 35), and the lecture distinguishes internal events, which stay inside a model, from external ones handled by other contexts (L4, slide 37). FreshFlow keeps that distinction in the type system. `BatchReceived` is a record in the Inventory domain package, expressed in the domain's own types; `BatchReceivedEvent` in `contracts` is its external form, with primitives, an `eventId` and an `occurredAt`. The application layer raises the domain event through a port, and the infrastructure adapter translates it to the integration event and publishes it, which is the division of labour the lecture describes when it says event handling is an application concern (L4, slide 38).

```java
public record BatchReceived(BatchId batchId, Sku sku, Quantity quantity, ExpiryDate expiryAt,
                            Money unitCost, Instant occurredAt) { }
```

#### Repositories, and where they live

Each aggregate has one repository whose interface speaks only the domain's language, `save(StockBatch)` and `findById(BatchId)`, and whose JPA implementation hydrates the aggregate as a whole (L4, slides 27 and 34). As Section 3.2 explained, the interfaces are declared in `infrastructure.repository` on the coordinator's ruling; the application services depend on the interface and never on Spring Data. The Sales context has no repository on purpose: its persistence port is a single-method `SaleTransactionRecorder`, which is what a transaction script needs and no more (ADR 0002).

#### Commands and handlers

Commands are plain DTOs handled in the application layer and they never enter the domain (L5, slide 7). `ApplyItemSoldCommand` is a record of SKU, quantity and time; `ApplyItemSoldHandler` loads the batches, asks the policy for a plan, applies it through the aggregate and publishes the resulting events. The project preferred one command and one handler per complex use case over a bloated application service, as the CQRS lecture recommends (L5, slide 9), and the same shape recurs for `RegisterGoodsReceiptCommand` and `CreateMarkdownPlanCommand`.

### 3.4 Data persistence in microservices

Each service owns one H2 database, created from its JPA entities at start-up, and no service reads another's tables. Figure 3.6 is the database design, grouped by service.

[[Figure 3.6: docs/diagrams/database-design.png]]

| Microservice | Database | Tables it owns | How other services obtain the data |
|---|---|---|---|
| inventory-service | `inventorydb` | `stock_batch`, `consumed_event` | `batch-received` and `stock-level-changed` events; `GET /api/batches/{id}` for the agent's tool |
| sales-service | `salesdb` | `sale_transaction`, `sale_line_item` | `item-sold` events |
| pricing-service | `pricingdb` | `price_rule`, `markdown_plan` | `price-rule-published` and `markdown-applied` events |
| insight-service | `insightdb` | `agent_trace`, `agent_tool_call`, `briefing`, `briefing_tool_call` | `GET /api/insight/agent-traces/{id}` and `GET /api/insight/briefings/{id}` |

Three design points are worth stating. The only foreign keys are inside a service (`sale_line_item` to `sale_transaction`, `agent_tool_call` to `agent_trace`, `briefing_tool_call` to `briefing`); nothing references a row in another service's database, which is what makes the services independently deployable and is the shared-nothing boundary the lectures describe (L5, slide 27). Money is stored at a precision of 19 and a scale of 4 and normalised to two decimal places by the `Money` value object on the way out, so the database never becomes the place where rounding happens. And the FR12 and FR13 read models are not tables at all: they live in four Kafka Streams state stores (`velocity-windows`, `velocity-baselines`, `batch-states`, `batch-risks`), are rebuilt from their changelog topics on restart, and are served to the REST layer by interactive queries. Only the agent trace and the briefing are persisted relationally, because they are audit records that must survive a topology restart and be readable by id.

The `consumed_event` table deserves a sentence of its own. It is the idempotency ledger for FR2: every `item-sold` event's identifier is recorded in the same transaction as the stock deduction it caused, so a redelivered event is recognised and ignored, and either a sale is applied and remembered or neither happens.

### 3.5 Event-driven architecture and stream processing

#### 3.5.1 The event-driven design

Services collaborate by publishing facts and reacting to them, which is the event collaboration and choreography style the lecture contrasts with orchestration (L6, slide 6). No service commands another. A sale is published by sales-service without knowing that inventory-service will deduct stock or that insight-service will window it; a risk is published by the topology without knowing that an agent will deliberate on it; a recommendation is published by the agent without knowing how pricing-service will judge it. That last point is the one the lecture makes about agents in particular: an agent should publish its findings as an event and move on rather than wait on a blocking response (L6, slide 9).

Every topic is a Spring Cloud Stream binding declared in `application.yml`, so no adapter hard-codes a topic name. Producers use `StreamBridge` behind a domain port, and consumers are `Consumer<Message<T>>` function beans named in `spring.cloud.function.definition`. The producer side, from inventory-service:

```java
@Override
public void publish(BatchReceived event) {
    BatchReceivedEvent payload = new BatchReceivedEvent(UUID.randomUUID(), event.occurredAt(),
            event.batchId().value(), event.sku().value(), event.quantity().value(),
            event.expiryAt().value(), new MoneyPayload(event.unitCost().amount(), event.unitCost().currency()));
    Message<BatchReceivedEvent> message = MessageBuilder.withPayload(payload)
            .setHeader("sku", payload.sku())
            .setHeader("eventId", payload.eventId().toString())
            .build();
    boolean sent = streamBridge.send(BINDING, message);
    ...
}
```

Messages are keyed by SKU so that every event about one product lands on one partition and stays ordered, which the Kafka Streams topology depends on when it groups by SKU. The consumer side, also from inventory-service, shows the idempotency rule in code: the event identifier is checked and then recorded in the same transaction as the deduction.

```java
@Transactional
public boolean handle(ItemSoldEvent event) {
    String eventId = event.eventId().toString();
    if (consumedEvents.existsById(eventId)) {
        log.info("[duplicate] topic={} eventId={} sku={} already applied; stock not deducted again", ...);
        return false;
    }
    consumedEvents.save(new ConsumedEventJpaEntity(eventId, ItemSoldEvent.TOPIC, Instant.now()));
    applyItemSoldHandler.handle(new ApplyItemSoldCommand(event.sku(), event.quantity(), event.occurredAt()));
    return true;
}
```

Consumer groups are chosen per purpose. inventory-service joins a durable group for `item-sold`, because a deduction is a change that must be applied exactly once and must not be replayed on restart. sales-service joins an anonymous group for `price-rule-published` and `markdown-applied` and replays both topics from the beginning, because its price snapshot is a materialised view of the whole topic rather than a work queue, and replay is safe because the adapter deduplicates on `eventId` and resolves conflicts by `occurredAt`.

#### 3.5.2 The stream processing queries

The two real-time analyses are one Kafka Streams topology with four input streams and one output stream, built with the DSL (L6, slides 24 to 26). The coordinator confirmed in writing that the DSL is the expected way to implement SQL-like analysis in this project, and the lecture's framing is that a live stream can be treated as a continuously appended table to which the usual relational operations apply (L6, slide 12). Each query is therefore given here twice: as the SQL-like statement it stands for, and as the DSL that implements it.

**Query 1, FR12: units sold per SKU over a hopping window, compared with a baseline.**

```sql
-- what the topology computes, in ksqlDB-style terms
SELECT sku, WINDOWSTART, WINDOWEND, SUM(quantity) AS quantitySold
FROM item_sold
WINDOW HOPPING (SIZE 60 SECONDS, ADVANCE BY 5 SECONDS, GRACE PERIOD 5 SECONDS)
GROUP BY sku
EMIT FINAL;

SELECT v.sku, v.quantitySold / 60.0 * 3600 AS velocityPerHour,
       b.baselineVelocityPerHour, velocityPerHour / b.baselineVelocityPerHour AS velocityRatio
FROM velocity_windows v JOIN velocity_baselines b ON v.sku = b.sku;
```

```java
KTable<Windowed<String>, Long> windowed = itemSold
        .filter((key, event) -> event != null && event.sku() != null)
        .groupBy((key, event) -> event.sku(), Grouped.with(Serdes.String(), new ItemSoldEventSerde()))
        .windowedBy(TimeWindows.ofSizeAndGrace(velocity.window(), velocity.grace()).advanceBy(velocity.advance()))
        .aggregate(() -> 0L, (sku, event, total) -> total + event.quantity(),
                Materialized.<String, Long, WindowStore<Bytes, byte[]>>as(WINDOW_STORE)
                        .withKeySerde(Serdes.String()).withValueSerde(Serdes.Long())
                        .withRetention(velocity.retention()));
```

The correspondence is one to one: `filter` is the `WHERE`, `groupBy` the `GROUP BY`, `windowedBy` the `WINDOW HOPPING`, `aggregate` the `SUM`, and `Materialized.as(WINDOW_STORE)` names the state store that the interactive query reads. The baseline join is a table-table join against `velocity-baselines`, a `KTable` built from an internal topic that is seeded from configuration (ADR 0003). The window is 60 seconds advancing every 5 for the demonstration; the production target of 60 minutes advancing every 5 is a change to two configuration values.

**Query 2, FR13: join the closed velocity window with the live state of every batch and project the shortfall.**

```sql
SELECT b.batchId, b.sku, b.remainingQuantity,
       TIMESTAMPDIFF(HOUR, v.WINDOWEND, b.expiryAt)               AS hoursToExpiry,
       v.velocityPerHour * hoursToExpiry                          AS projectedSales,
       b.remainingQuantity - projectedSales                       AS projectedShortfall
FROM velocity_stream v JOIN batch_states b ON v.sku = b.sku
WHERE b.remainingQuantity > 0 AND hoursToExpiry > 0
  AND projectedShortfall > 0;   -- threshold, configured
```

```java
KTable<String, SkuBatchStates> batchStates = receivedUpdates.merge(stockUpdates)
        .groupByKey(Grouped.with(Serdes.String(), updateSerde))
        .aggregate(SkuBatchStates::empty, (sku, update, states) -> states.apply(update),
                Materialized.<String, SkuBatchStates, KeyValueStore<Bytes, byte[]>>as(BATCH_STATE_STORE)
                        .withKeySerde(Serdes.String()).withValueSerde(statesSerde));

KStream<String, VelocitySnapshot> velocityStream = windowed
        .suppress(Suppressed.untilWindowCloses(Suppressed.BufferConfig.unbounded()))
        .toStream()
        .map((windowedKey, total) -> KeyValue.pair(windowedKey.key(), new VelocitySnapshot(...)));

KTable<String, RiskAssessment> assessments = velocityStream
        .join(batchStates, this::assessEveryBatch, Joined.with(Serdes.String(), velocitySerde, statesSerde))
        .flatMap((sku, views) -> ...)              // one record per batch
        .groupByKey(Grouped.with(Serdes.String(), assessmentSerde))
        .reduce((previous, candidate) -> candidate.succeeding(previous),
                Materialized.<String, RiskAssessment, KeyValueStore<Bytes, byte[]>>as(RISK_STORE)...);

return assessments.toStream()
        .filter((batchId, assessment) -> assessment != null && assessment.newlyAtRisk())
        .mapValues(this::toEvent);                 // sell-through-risk-detected
```

The batch-state table is the lecture's fourth scenario, a topic replicated into a local table held inside the service (L6, slide 15): `batch-received` and `stock-level-changed` are merged, re-keyed by SKU and aggregated into one `SkuBatchStates` per product. `suppress(untilWindowCloses)` makes the velocity stream emit once per closed window rather than on every update, which is the `EMIT FINAL` in the SQL. The stream-table join then runs the `RiskAssessmentPolicy` from the domain package over every batch of the SKU; the `reduce` keeps one standing assessment per batch so that only the transition into risk, `newlyAtRisk`, produces an event. That is what stops the agent being triggered every five seconds for the same batch.

**Event time.** Both queries window on the producer-assigned `occurredAt`, never on the time Kafka received the record. One timestamp extractor serves every input topic by matching on the `IntegrationEvent` interface that every contract implements:

```java
@Override
public long extract(ConsumerRecord<Object, Object> record, long partitionTime) {
    if (record.value() instanceof IntegrationEvent event && event.occurredAt() != null) {
        return event.occurredAt().toEpochMilli();
    }
    log.warn("[dropped] topic={} ... carries no usable occurredAt; it will NOT be placed using ingestion time", ...);
    return -1;
}
```

An earlier version of this class matched only `ItemSoldEvent` and silently dropped every batch event; the defect was found when the risk table stayed empty in a live run and is recorded in the Week 7 log. Event time is what makes the analysis reproducible: replaying the topic at any speed gives the same windows, and a sale that arrives late within the grace period lands in the window it belongs to.

**Interactive queries as the read side.** The REST endpoints do not query a database. `VelocityController` and `RiskController` call application-layer ports (`VelocityWindowStore`, `RiskViewStore`) whose infrastructure adapters read the state stores through `InteractiveQueryService`. The read-side selection rule is specified rather than left to chance: the velocity served is the latest fully elapsed window, and a SKU whose last window has aged out of retention answers 404. The coordinator's reference `cargo-tracker` presents the same shape, a `StreamProcessor` and an `InteractiveQuery` class behind a query controller; FreshFlow's equivalents are `InsightTopologyConfiguration`, the `InteractiveQuery*Store` adapters and the two controllers, with three additions the reference does not have: a hopping window with a grace period, event-time extraction, and a stream-table join.

### 3.6 The agentic AI component

#### 3.6.1 The pattern

Both agents are tool-using agents in the ReAct style: the model reasons about what it needs, requests a tool, observes the result and reasons again, until it answers or a step limit stops it (L7, slides 19 and 20). The lectures' list of what turns a reactive model into an agent is memory, tools and retrieval (L7, slide 10); FreshFlow uses tools, keeps short-term memory as the conversation of one deliberation, and does no retrieval, because every fact the agent needs is a live number from a state store or a service rather than a document. The coordinator's two agent repositories name the same building blocks, tools and code-side orchestration around the model's output, and never use a framework agent loop; FreshFlow builds the loop explicitly for the same reason, so that every step is visible and the trace is complete.

Three design rules make the pattern safe to run inside a business system, and each is enforced in code rather than in the prompt.

1. The model never sees a credential, a price write or a pricing rule. The Markdown Decision Agent's tools are `getBatchDetails`, `getSellThroughRate` and `proposeMarkdown`; there is no tool that reads the margin floor and no tool that writes a price. A guard test scans every main source file of insight-service, including the system prompt, and fails the build if the words "margin floor", "pricing" or the markdown-plans path appear.
2. The orchestrator, not the model, decides what counts. A proposal is taken only from a `proposeMarkdown` call the registry accepted; a proposal naming a different batch from the one flagged is refused and recorded as declined; a model that stops without concluding produces a trace that says so.
3. Every deliberation is persisted as a trace with its tool calls, arguments and results, and is idempotent on the trigger's event identifier, so a redelivered risk event is not deliberated twice.

#### 3.6.2 LLM integration

The model sits behind a port in the application layer (ADR 0004). `MarkdownDecisionAgent` is an interface with one method; the runtime implementation uses LangChain4j's `ChatModel` against Google's Gemini API, and every test uses a deterministic stub, so no test touches the network and the suite passes with no API key set. The dependency surface is `langchain4j-core` plus exactly one provider module, a team ruling that keeps the approved list short and the version pinned in the root POM. The coordinator's references wire their models through the LangChain4j Spring Boot starter; FreshFlow constructs the model explicitly in a configuration class, which the `book-recommendaton-agent` reference also does with `AiServices.builder`, and keeps the construction behind two guards: a Spring profile that excludes it from every test context, and a condition on the API key being present.

The loop itself, from `LangChain4jMarkdownDecisionAgent`:

```java
for (int step = 1; step <= maxSteps; step++) {
    ChatResponse response = chatModel.chat(ChatRequest.builder()
            .messages(conversation).toolSpecifications(specifications).build());
    AiMessage answer = response.aiMessage();
    conversation.add(answer);
    if (!answer.hasToolExecutionRequests()) {
        closing = answer.text();
        break;
    }
    for (ToolExecutionRequest request : answer.toolExecutionRequests()) {
        String arguments = MarkdownToolArguments.encode(request.name(), request.arguments());
        String result = tools.invoke(request.name(), arguments);
        calls.add(new ToolCall(calls.size() + 1, request.name(), arguments, result, clock.instant()));
        conversation.add(ToolExecutionResultMessage.from(request, result));
        if (AgentToolRegistry.PROPOSE_MARKDOWN.equals(request.name()) && result.startsWith(ACCEPTED)) {
            proposal = MarkdownToolArguments.decodeProposal(request.arguments());
        }
    }
}
```

The tools are declared to the model as `ToolSpecification`s with JSON schemas, so the model's arguments arrive typed; the discount tier is an enum property, which is how the contract's four tiers reach the model without being spelled out in prose. The system prompt tells the model the order to work in, that it recommends a tier and a start time and nothing else, and that a tool answer beginning `error=` is to be read and corrected. `maxSteps` bounds the loop, the lecture's "maximum number of loop" (L7, slide 20), and the Gemini API's requirement to echo thought signatures on the second turn was the one integration defect found in the live run; it is recorded with its fix in the 14 September log.

The orchestrator around the agent is where the business rules of the use case live:

```java
public AgentTraceRecord onRiskDetected(SellThroughRiskDetectedEvent trigger) {
    String triggerEventId = trigger.eventId().toString();
    Optional<AgentTraceRecord> alreadyHandled = traceStore.findByTriggerEventId(triggerEventId);
    if (alreadyHandled.isPresent()) {
        return alreadyHandled.get();                       // a redelivered trigger is not deliberated twice
    }
    AgentDeliberation deliberation = agent.deliberate(trigger, tools);
    Instant completedAt = clock.instant();
    MarkdownProposal proposal = inScope(deliberation.proposal(), trigger);   // refuses a foreign batchId (R7)
    Instant effectiveTo = proposal == null ? null : promotionEnds(trigger);  // from the batch's expiry
    boolean recommends = proposal != null && effectiveTo != null;
    AgentTraceRecord trace = new AgentTraceRecord(TRACE_ID_PREFIX + triggerEventId, triggerEventId,
            trigger.batchId(), List.copyOf(deliberation.toolCalls()),
            finalRecommendation(proposal, effectiveTo),    // NO_MARKDOWN | PROPOSED_BUT_UNRESOLVABLE | "<TIER> from .. until .."
            deliberation.rationale(), completedAt);
    traceStore.save(trace);
    if (recommends) {
        publisher.publish(new MarkdownRecommendedEvent(UUID.randomUUID(), completedAt, trigger.batchId(),
                trigger.sku(), proposal.discountTier(), proposal.effectiveFrom(), effectiveTo,
                proposal.rationale(), trace.agentTraceId()));
    }
    return trace;
}
```

Three outcomes are possible and each leaves a trace: the agent declined (`NO_MARKDOWN`), the agent proposed but the batch could not be resolved to derive the window (`PROPOSED_BUT_UNRESOLVABLE`, ruling R9), or a recommendation was published. The trace is saved before the event is published; a crash between the two loses a recommendation but never duplicates one, and that window is recorded as known limitation G-7 rather than closed with a transactional outbox.

The second agent, the end-of-day briefing, follows the same shape with its own port, `BriefingAgent`, three read-only tools over the Insight context's views, and its own persisted record. Its orchestrator adds a grounding step: the risk count and recommendation count returned to the caller are computed from the same services the tools read rather than parsed from the model's prose.

```java
public EndOfDayBriefing brief(LocalDate businessDate) {
    Instant now = clock.instant();
    LocalDate day = businessDate != null ? businessDate : LocalDate.ofInstant(now, ZoneOffset.UTC);
    BriefingRequest request = new BriefingRequest(day, now);
    BriefingDeliberation deliberation = agent.brief(request, tools);          // the model writes the narrative
    int riskCount = riskQueryService.listCurrentRisks().size();              // the service counts the facts
    int recommendationCount = (int) agentTraceStore
            .findCompletedBetween(request.businessDayStart(), request.businessDayEnd())
            .stream().filter(BriefingOrchestrator::recordsAPublishedRecommendation).count();
    EndOfDayBriefing briefing = new EndOfDayBriefing(newBriefingId(), day, now,
            summaryOf(deliberation), riskCount, recommendationCount, toolCallsOf(deliberation));
    briefingStore.save(briefing);
    return briefing;
}
```

The briefing agent's system prompt tells the model to call `listCurrentRisks` and `listRecommendations` first, to call `getSellThroughRate` only for a SKU it wants to comment on, to write four to eight plain sentences naming batches and citing only numbers the tools returned, to state that write-off totals are unavailable because write-offs are not recorded, and that it "recommends nothing and changes nothing". The prompt contains no pricing vocabulary, which the guard test confirms on every build.

What the agent produces in practice is shown in Section 4.2: the trace of a real deliberation against a batch of 400 yoghurts, with the three tool calls, the proposal and the rationale citing the numbers the tools returned.

#### 3.6.3 Guardrails and failure behaviour

The lectures list rule-based guardrails, human-in-the-loop review and monitoring as the controls for hallucination (L7, slide 31), and the dependability lecture lists human review of AI recommendations and the combination of rules with models among its redundancy and diversity techniques (L8, slide 9). FreshFlow uses all three. The rule-based guardrail is the margin floor in pricing-service, which judges the agent's proposal by the same code path as a supervisor's. The human-in-the-loop is the supervisor who can read the trace and the rejected plan. The monitoring is the trace itself, and the counts in the briefing that do not come from the model. When no model is configured the service starts, serves the two real-time analyses, logs each trigger as not deliberated, and answers 503 for a briefing; nothing in the other three contexts depends on the model being available.

### 3.7 Other important aspects

**Mechanical enforcement of the architecture.** Several of the rules in this report are checked by tests rather than by review. The domain packages' import rule fails the build on any non-`java.*` import. `AntiCorruptionLayerGuardTest` fails it if insight-service mentions pricing or a margin floor anywhere in its sources, prompt included. Every 404 assertion in the API tests also asserts that the request reached a real controller method, after two tests early in the project passed for the wrong reason because an unimplemented endpoint happened to return 404. `MarkdownAgentTestProfileGuardTest` starts a test context with an API key deliberately set and asserts that no model client was built, after a nested configuration was found to have escaped its profile guard. A contract test over the `contracts` module checks that every event serialises `eventId` as a UUID and `occurredAt` as an ISO-8601 instant, after one topic shipped its timestamp as an epoch number because the Kafka Streams serde used a different object mapper from the binder. None of these tests exists in the coordinator's reference repositories; they are where the project's own defects taught it something.

**Ports and adapters as a substitution mechanism.** sales-service's price snapshot entered through a `PriceSnapshotFeed` port in Week 5 from static configuration, and in Week 6 from the `price-rule-published` topic, behind the same interface; the application-layer diff for that change was empty. The same port now also receives `markdown-applied`. The LLM port follows the same idea: a deterministic stub in every test, a LangChain4j adapter at runtime, and a local Ollama adapter would be a second class in infrastructure and a configuration value, not a change to the application layer.

**An injected clock.** Every service takes a `java.time.Clock` bean. Expiry, remaining shelf life, promotion windows and the agents' timestamps are computed against it, which is what lets the integration tests use a fixed clock and assert on values that would otherwise move while the test runs.

**Decision records.** Six architecture decision records capture the choices the specifications did not settle: the placement of `PriceSnapshotFeed` (0001), the ports a transaction script may own (0002), the configuration-seeded velocity baseline (0003), the LLM behind a port (0004), repositories in the persistence layer (0005) and the Insight domain package (0006). Each records the alternatives, the lecture or ruling it rests on and the cost accepted.

**Where FreshFlow departs from the coordinator's reference code, and why.** The `cargo-tracker` reference keeps command objects in the domain and lets the aggregate's constructor handle them; FreshFlow keeps commands as DTOs in the application layer with explicit handlers, following the CQRS lecture (L5, slide 7), and gives the aggregates intention-revealing methods instead. The reference annotates aggregates with `@Entity` and publishes events from an after-commit listener; FreshFlow keeps the domain persistence-ignorant with mappers, publishes through a port, and records the resulting save-then-publish window as a known limitation (G-7) rather than closing it with an outbox, which is also the position the reference's listener is in. The reference's shared event classes carry neither an event identifier nor a timestamp; FreshFlow's carry both, because idempotent consumption and event-time windowing need them.

**Known limitations, stated.** G-3: trading hours are configuration and default to 24 clock hours. G-4: risk assessment is triggered by a closing velocity window, so a SKU that stops selling entirely is not re-assessed; the fix is a stream-time punctuator and it was not built. G-6: the event wire format was unenforced until the contract test above was added. G-7: the trace is saved and the recommendation published in two steps that are not atomic; a crash between them loses a recommendation but never duplicates one, and that was accepted. FR3, FR7 and FR10 were not built. Naming these in the report is deliberate; a marker who finds them unstated would be right to ask what else was missed.

---

## 4. Instruction and user story examples

### 4.1 Configuration and running instructions

The repository's `README.md` carries the full instructions and is kept in step with the code; this section summarises them. Prerequisites are JDK 21, Docker Desktop and Git. Maven is not required because the wrapper is committed.

1. Start Kafka: `docker compose up -d` in the repository root starts a single-node broker in KRaft mode; `docker compose ps` should show it healthy.
2. Build and test: `.\mvnw.cmd clean package` (or `./mvnw` on Linux and macOS). The build ends with `BUILD SUCCESS` and runs every test without a broker or network connection.
3. Configure the LLM, in the shell that will start insight-service only: set `LLM_API_KEY` to a Google AI Studio key and `LLM_MODEL` to the model name (for example `gemini-2.5-flash`). The key is read from the environment and is never written to a file. Without it the service still starts and serves FR12 and FR13.
4. Start the four services, each in its own terminal, in the order inventory, pricing, sales, insight: `.\mvnw.cmd -pl <service> spring-boot:run`. Each logs `Started ...Application` on ports 8081 to 8084. IntelliJ users can use the run configurations in `.run/`, including one that starts all four.
5. Drive the demonstration: `.\scripts\demo.ps1` seeds a price rule, registers batches, runs the POS simulator for a few minutes and prints each read model as it changes; or use the Postman collection in `docs/postman/`, which has one request per endpoint in demonstration order.
6. Stop with Ctrl+C in each terminal and `docker compose down`.

The configuration that a marker may want to vary is in `insight-service/src/main/resources/application.yml`: the velocity window, advance and grace (60, 5 and 5 seconds for the demonstration), the risk shortfall threshold and trading hours per day, and the LLM timeout and step limit.

### 4.2 User story input and output

Each implemented user story has at least one sample input and its output below. Inputs are the requests in the README and the Postman collection; outputs were captured from a live run of the system on [[DATE of the capture run]] and are reproduced unedited apart from line wrapping.

[[TO FILL after the live run: for each of UC-01 to UC-12 a request and the actual JSON response, including the 400 for a zero quantity, the 422 MarginFloorRejection body, the agent trace JSON with its three tool calls, the risk list, the velocity view, a transaction with markdownApplied true, and the briefing. Source: docs/report/evidence/.]]

---

## 5. AI-driven development process

### 5.1 Human-AI collaborative development

The project was built with Claude Code as the development agent, and the way it was used is itself a designed artefact. The lectures describe the AI shift as engineers authoring specifications, context and constraints while agents synthesise the implementation (L1, slide 12), with the human acting as the manager who reviews the plan and the artefacts (L1, slide 28). FreshFlow followed that division of labour literally, and the repository holds the evidence.

| Lifecycle phase | How the team worked with the agent | Artefacts in the repository |
|---|---|---|
| Requirements engineering | The team wrote the business case and objectives; the agent drafted sixteen requirements as user stories with acceptance criteria and a traceability matrix, and the team cut, split and re-tagged them (FR6 was split into FR6a and FR6b after a Phase 1 finding) | `specs/spec-02-requirements.md`; `docs/ai-log/2026-08-26-phase-1.md` |
| Domain modelling | The team decided the four bounded contexts and the per-context pattern; the agent produced the domain model specification with invariants per aggregate and the team ruled on every open item it raised | `specs/spec-03-domain-model.md`; ADR 0002, 0005, 0006 |
| Architecture design | The architecture specification, the context map and the event contracts were written with the agent and are the governing documents; `CLAUDE.md` is the constraint set the agent worked under in every session | `specs/spec-01-architecture.md`, `specs/spec-05-stream-processing.md`, `specs/spec-06-agent-orchestration.md`, `CLAUDE.md` |
| Implementation | A four-phase gated pipeline, from the coordinator's own case study (L3, slide 8): inspect the specifications and halt on inconsistency; build the skeleton; write the tests from the OpenAPI contract and watch them fail; implement until they pass. No phase started without the team's written authorisation, and the agent was told to halt rather than guess | `specs/dev-agent-prompt.md`; `docs/prompts/`; every `docs/ai-log/` entry ends with what was assumed, what deviated and where the agent judged the spec wrong |
| Testing | Tests were written before implementation from the contract and the agent was forbidden to weaken one to make it pass; the team added guard tests after each defect | `*ApiIntegrationTest`, `AntiCorruptionLayerGuardTest`, `MarkdownAgentTestProfileGuardTest`, `MarkdownRecommendedEventContractTest` |
| Documentation | Specifications, ADRs, logs, the runbook and the README were produced in the same sessions as the code they describe; the report was written by the team from them | `docs/decisions/`, `RUNBOOK.md`, `README.md` |

Twenty-three session logs and six decision records are in `docs/ai-log/` and `docs/decisions/`. Three examples show how proposals were accepted, rejected or modified, and why.

**Accepted: the Phase 1 halt.** The first agent run was told to inspect the four specifications and halt on any inconsistency. It halted with 28 findings, 7 of them blocking, among them that `sales-service` had no permitted way to obtain a price (the architecture forbade both a shared database and a synchronous call to pricing) and that `MarkdownPlan.activate` could not evaluate its own invariant because it did not receive the base price. The team accepted the halt, ruled on each finding over two days (recorded in `docs/ai-log/2026-08-26-phase-1*.md`), and only then authorised the skeleton. The price problem was resolved with the `PriceSnapshotFeed` port rather than an exception to the rule, which is the decision that later made the Week 6 swap to Kafka a zero-line change in the application layer (ADR 0001).

**Rejected: a dependency the agent wanted.** During the Week 7 stream-processing work the agent proposed adding `kafka-streams-test-utils` so the topology could be tested with `TopologyTestDriver`, and separately noted that a second LangChain4j provider module would make the local-model alternative easier. Both were outside the approved dependency list, and the team's rule, recorded as R2, was that an unapproved dependency is a halt, not a choice. The agent was told to proceed without them; the topology was verified against the real broker and logged instead. The team then asked the coordinator, who approved three test and validation dependencies by email on 17 September; the approval is recorded in the specification and none of the three has been added, because the work was already done without them.

**Modified: where repository interfaces live.** The agent followed the coordinator's own development prompt and declared repository interfaces in the domain packages, the Evans arrangement. The 8 September comparison of the coordinator's reference repositories found four different placements across his slides, specification, prompt and shipped code, and recommended asking rather than arguing. The coordinator's answer, on 17 and 22 September, was the persistence layer in a dedicated sub-package, matching his lecture (L4, slide 34). The agent's placement was reversed, with `git mv` so that history follows the files, and the cost in dependency-inversion terms was written down rather than hidden (ADR 0005). During that relocation the agent's first scripted edit truncated seven files to zero bytes; it noticed from its own output before anything was staged, restored them from the index and redid the edit by hand, and the incident is in the log because the log is the evidence base for how the tooling was used.

Two further incidents are worth a sentence each. A claim in a Javadoc that two guards protected the test suite from building a live model turned out to be false for a nested configuration class; the team authorised a one-annotation fix and a test that would have caught it, and the test is now part of the suite. And the first live run of the agent failed on the second turn because the Gemini API requires thought signatures to be echoed back; the agent diagnosed it from the error, the team authorised a two-line configuration change, and the next run completed.

### 5.2 Lessons learned

**Specifications were the leverage, and the halt was worth more than the code.** The most valuable single output of the agent was a list of 28 contradictions in documents the team had thought were finished. An agent that builds through an inconsistency produces plausible code for the wrong system (L2, slide 22); one instructed to halt turned the specifications into something that could be built from. The team's productivity gain came less from the speed of writing code and more from never having to rewrite it.

**Constraints had to be mechanical to survive.** Every rule that lived only in a document drifted at least once: the profile guard that a comment promised, the event timestamp format the specification required, the timestamp extractor that handled one topic. Every rule that was turned into a test stayed true. The lesson the team took from the dependability lecture is that a repeatable process depends less on the individual, or the individual prompt engineer, than a careful one (L8, slide 11); for an AI-assisted process the way to make it repeatable was to encode the architecture as tests the agent could not pass by accident.

**Quality went up where the human knew what to look for, and nowhere else.** The agent's code was consistently clean and its logs unusually honest, but it could not tell the team where a repository interface belongs in this subject, whether a CQRS read side needs a domain layer, or whether the Kafka Streams DSL satisfies a requirement worded around SQL. Those were questions for the coordinator, and the three consultations settled more than any amount of generated argument would have. The architectural decisions in this project were made by people and recorded as ADRs; the agent implemented them.

**Collaboration changed shape.** With one member driving the agent, the repository's commit record became lopsided, and the other members' contributions, diagrams, use cases, slides and minutes, had to be made visible in the repository on purpose rather than assumed. The team also found that reviewing an agent's output line by line does not scale; what scaled was reviewing its specifications, its tests and its logs, and the coordinator's advice on exactly this point, that the reviewer needs a firm grasp of the lecture material to know what to check, matched the team's experience.

**Limits.** The agent's confidence did not track its correctness: the two comments that were wrong were written with the same assurance as the hundreds that were right. A live model is a dependency with its own failure modes, from a missing thought signature to a rate limit, and the architecture had to be designed so that the rest of the system does not care whether the model is there. Finally, the cost of a dependable AI-assisted process was real: the logs, the ADRs and the guard tests took time that a team optimising for lines of code would not have spent, and that time is what the team would defend most readily.

---

## References

Anthropic (2024) *Building effective agents*. Available at: https://www.anthropic.com/research/building-effective-agents (Accessed: 4 October 2026).

Apache Software Foundation (2026) *Kafka Streams Developer Guide*. Available at: https://kafka.apache.org/documentation/streams/ (Accessed: 4 October 2026).

Evans, E. (2003) *Domain-Driven Design: Tackling Complexity in the Heart of Software*. Boston: Addison-Wesley.

Fowler, M. (2002) *Patterns of Enterprise Application Architecture*. Boston: Addison-Wesley.

Google (2026) *Gemini API documentation*. Available at: https://ai.google.dev/gemini-api/docs (Accessed: 4 October 2026).

LangChain4j (2026) *LangChain4j documentation*. Available at: https://docs.langchain4j.dev/ (Accessed: 4 October 2026).

Millett, S. and Tune, N. (2015) *Patterns, Principles, and Practices of Domain-Driven Design*. Indianapolis: Wrox.

Seymour, M. (2021) *Mastering Kafka Streams and ksqlDB*. Sebastopol: O'Reilly.

Sommerville, I. (2016) *Software Engineering*. 10th edn. Harlow: Pearson.

Stopford, B. (2018) *Designing Event-Driven Systems*. Sebastopol: O'Reilly.

Su, G. (2026a) *CSCI318 Lecture 1: Introduction to Modern Software Engineering*. University of Wollongong.

Su, G. (2026b) *CSCI318 Lecture 2: Domain Distillation*. University of Wollongong.

Su, G. (2026c) *CSCI318 Lecture 3: AI-Driven Development Case Study and Application Architecture*. University of Wollongong.

Su, G. (2026d) *CSCI318 Lecture 4: Tactical Design Patterns for Domain Models*. University of Wollongong.

Su, G. (2026e) *CSCI318 Lecture 5: Event Sourcing, CQRS and Bounded Context Integration*. University of Wollongong.

Su, G. (2026f) *CSCI318 Lecture 6: Event-Driven Architecture and Stream Processing*. University of Wollongong.

Su, G. (2026g) *CSCI318 Lecture 7: Agentic AI-Powered Applications*. University of Wollongong.

Su, G. (2026h) *CSCI318 Lecture 8: Trustworthy and Dependable Systems*. University of Wollongong.

Su, G. (2026i) *cargo-tracker-0.1* [source code]. Available at: https://github.com/gxshub/cargo-tracker-0.1 (Accessed: 4 October 2026).

Su, G. (2026j) *book-and-library-management-system-v1* [source code]. Available at: https://github.com/gxshub/book-and-library-management-system-v1 (Accessed: 4 October 2026).

VMware (2026) *Spring Cloud Stream Reference Documentation*. Available at: https://docs.spring.io/spring-cloud-stream/reference/ (Accessed: 4 October 2026).

Vernon, V. (2013) *Implementing Domain-Driven Design*. Boston: Addison-Wesley.

Yao, S., Zhao, J., Yu, D., Du, N., Shafran, I., Narasimhan, K. and Cao, Y. (2023) 'ReAct: Synergizing reasoning and acting in language models', *International Conference on Learning Representations (ICLR 2023)*.

---

## Appendix A. Use cases

See `docs/report/section-2-1-use-cases.md`, reproduced in full in the submitted PDF.

## Appendix B. Figures

| Figure | File | Section |
|---|---|---|
| 2.1 to 2.12 | `docs/diagrams/activity-UC-01.png` to `activity-UC-12.png` | 2.1 |
| 2.13 | `docs/diagrams/domain-model.png` | 2.4 |
| 3.1 | `docs/diagrams/A2-microservice-architecture.png` | 3.1 |
| 3.2 | `docs/diagrams/event-flow.png` | 3.1, 3.5 |
| 3.3 | `docs/diagrams/context-map.png` | 3.1, 2.4 |
| 3.4 | `docs/diagrams/layered-architecture.png` | 3.2 |
| 3.5 | `docs/diagrams/layered-architecture-all-services.png` | 3.2 |
| 3.6 | `docs/diagrams/database-design.png` | 3.4 |
