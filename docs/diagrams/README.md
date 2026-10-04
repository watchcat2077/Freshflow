# Diagrams for the project report

Every UML diagram here is an UMLet 15.1 file (`.uxf`) with its rendered `.png` beside it.
Open a `.uxf` in UMLet to edit it; re-render with the command at the end of this page.
`A2-microservice-architecture.drawio` / `.png` is Madhuri's A2 diagram (draw.io, not UMLet) and is
kept unchanged.

| File | Diagram | Report section |
|---|---|---|
| `activity-UC-01.uxf` / `.png` | Activity: UC-01 Register a goods receipt (FR1) | 2.1 |
| `activity-UC-02.uxf` / `.png` | Activity: UC-02 Deduct stock on sale (FR2) | 2.1 |
| `activity-UC-03.uxf` / `.png` | Activity: UC-03 Query stock position and remaining shelf life (FR4) | 2.1 |
| `activity-UC-04.uxf` / `.png` | Activity: UC-04 Record a sale transaction (FR5) | 2.1 |
| `activity-UC-05.uxf` / `.png` | Activity: UC-05 Apply the price in force at the point of scan (FR6a, FR6b) | 2.1 |
| `activity-UC-06.uxf` / `.png` | Activity: UC-06 Maintain price rules and margin floors (FR8) | 2.1 |
| `activity-UC-07.uxf` / `.png` | Activity: UC-07 Create and activate a markdown plan (FR9) | 2.1 |
| `activity-UC-08.uxf` / `.png` | Activity: UC-08 Reject a markdown that breaches the margin floor (FR11) | 2.1 |
| `activity-UC-09.uxf` / `.png` | Activity: UC-09 Observe the rolling sell-through velocity of a SKU (FR12) | 2.1 |
| `activity-UC-10.uxf` / `.png` | Activity: UC-10 Detect a batch at risk of expiring unsold (FR13) | 2.1 |
| `activity-UC-11.uxf` / `.png` | Activity: UC-11 Recommend a markdown for an at-risk batch (FR14) | 2.1 |
| `activity-UC-12.uxf` / `.png` | Activity: UC-12 Produce the end-of-day briefing (FR15) | 2.1 |
| `domain-model.uxf` / `.png` | Class diagram: the four bounded contexts' domain models (spec-03) | 2.4 |
| `context-map.uxf` / `.png` | Context map: bounded contexts, integration patterns, sub-domains (spec-01 s1.5) | 2.4 and 3.1 |
| `A2-microservice-architecture.drawio` / `.png` | Microservice architecture (A2, by Madhuri) | 3.1 |
| `layered-architecture.uxf` / `.png` | Layered architecture of inventory-service with the real classes per package (spec-01 s2.1, s2.2) | 3.2 |
| `layered-architecture-all-services.uxf` / `.png` | The four layers in every service and the domain-layer pattern per context (spec-01 s2.3) | 3.2 |
| `database-design.uxf` / `.png` | Database design: one H2 schema per service, tables and columns | 3.4 |
| `event-flow.uxf` / `.png` | Event flow: producers, Kafka topics, consumers, REST and LLM calls (spec-01 s2.6) | 3.5 |

## Conventions used in the activity diagrams

- One swimlane per actor and per service that acts; a `Kafka` lane where an event is published or
  consumed. Events are drawn as `<<event>>` object nodes in the Kafka lane; send-signal and
  receive-signal shapes mark the publishing and consuming actions.
- The numbered main flow of the use case (docs/report/section-2-1-use-cases.md) is the straight
  path; each alternative flow is a decision with its guard in square brackets, ending in its own
  final node. Guards quote the use case's error codes (`400 QUANTITY_NOT_POSITIVE`, `422`, `503`).
- A fork/join bar shows the two things the service does together after validation: persisting and
  publishing. UC-11 and UC-12 show the agent's tool-call loop as a `more tool calls?` decision.

## Re-rendering a diagram

UMLet's command line converts a `.uxf` to a `.png` next to it (log4j warnings on stderr are harmless):

```
java -jar "E:\UOW 2026 spring\ISIT312 Big Data Management\UMLet-15.1\umlet.jar" -action=convert -format=png -filename="<absolute path>\docs\diagrams\activity-UC-01.uxf" -output="<absolute path>\docs\diagrams\activity-UC-01"
```

`-output` takes the path without the extension; UMLet appends `.png`. Use the same command for any
of the `.uxf` files above. A copy of every `.uxf` and `.png` is also kept in the course folder
`UML diagram` next to the repository.
