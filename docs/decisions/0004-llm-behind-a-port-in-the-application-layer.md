# ADR 0004 — The LLM sits behind a port declared in the application layer of `insight-service`

| Field | Value |
|---|---|
| Status | **Accepted** |
| Date | 10 September 2026 |
| Deciders | Project team |
| Raised as | FR14 Phase A, team ruling R1 (`docs/ai-log/2026-09-10-fr14-phase-a.md` §1) |
| Affects | `insight-service`, FR14, spec-01 §1.1, spec-01 §2.3, spec-06 |
| Relates to | [ADR 0001](0001-pricesnapshotfeed-declared-in-application-layer.md) — same Ports and Adapters argument; [ADR 0002](0002-transaction-script-ports-and-invariant-placement.md) Decision 2, whose port-placement table this applies rather than extends; [ADR 0003](0003-velocity-baseline-is-seeded-from-configuration.md) — the same argument applied to a different port |
| Supersedes | Nothing |
| Amended | **11 September 2026** — team ruling R10. The test-classpath *mechanism* cited in the Decision and in Verification check 1 is superseded; see the **Amendment** at the end of this document. The decision itself, the rationale, the consequences and the rule "no test may call a live model or touch the network" are all unchanged. **13 October 2026** — the two checks of that amendment are renamed Check A and Check B (CLAUDE.md reused the identifier R10 on 4 October 2026 for the margin-ratio ruling) and restated after the 14 September defect; see **Second amendment** |

---

## Context

FR14 introduces a Large Language Model into the system. spec-01 §1.1 lists "LangChain4j" as the LLM
integration technology, with no version, no coordinates and no statement of where in the
architecture it may appear. spec-01 §2.7 requires the provider to be injectable through
`application.yml` "so hosted API and local Ollama are interchangeable without code change", which
implies a seam but does not say what kind or where.

That gap has to be closed before any FR14 code is written, because an LLM is not an ordinary
dependency:

- **It is non-deterministic.** The same prompt yields different text on different calls. A test
  that reaches one cannot assert an outcome, only that something came back.
- **It is remote, rate-limited and paid for.** A test suite that calls one is slow, flaky, costs
  money per run, and fails when someone else is using the quota.
- **It needs a credential.** spec-01 §2.7 requires API keys to come from environment variables and
  never to be committed. A test that needs a key cannot run on a clean checkout.
- **It is the least trustworthy component in the system.** spec-01 §1.5 says so in as many words:
  Pricing treats its output as "an untrusted proposal, never as an authoritative command".

Everything FR14 must be trusted to do correctly — persisting the trace, deduplicating the trigger,
publishing exactly one recommendation — sits around that component. If those live on the same side
of the boundary as the model, none of them can be tested exactly.

`insight-service` also has no `domain` package (spec-01 §2.3), so the usual instinct to declare a
port in the innermost layer has nowhere to point.

## Decision

**`MarkdownDecisionAgent` is declared in `com.freshflow.insight.application`.**

Two adapters implement it:

| Adapter | Where | Used by |
|---|---|---|
| LangChain4j adapter | `com.freshflow.insight.infrastructure` | Runtime only. Phase B |
| `StubMarkdownDecisionAgent` | `insight-service/src/test/java/...` | **Every test** |

**No test may call a live model or touch the network.** The stub lives in test sources precisely so
that LangChain4j never reaches the test classpath: a test cannot resolve a model if the library
that would resolve one is not on the classpath the tests compile against.

> **The first sentence stands; the mechanism given for it was superseded on 11 September 2026**
> (team ruling R10). LangChain4j is a compile-scope dependency and is therefore on the test
> classpath. The rule "no test may call a live model or touch the network" is unchanged and still
> holds — see the **Amendment** at the end of this document for how it is now checked.

The port returns an `AgentDeliberation` — the tool calls made and the proposal reached — and has no
side effects. Persisting the trace, deduplicating the trigger and publishing the recommendation
belong to `MarkdownAgentOrchestrator`, on the deterministic side of the boundary.

## Rationale

**1. ADR 0002 Decision 2 already rules where this service's ports go.**
Its port-placement table assigns `insight-service`'s ports to `application`, for the plain reason
that there is no `domain` package to declare them in. That ruling was made for outbound
persistence and messaging ports and it is not being extended here — it is being applied. spec-01
§2.3 carries the same table, and spec-05 §4.2 already places `VelocityWindowStore`,
`VelocityBaselineStore` and `RiskViewStore` there. This ADR adds a fourth port to an existing rule
rather than inventing a rule for one type.

**2. spec-01 §2.3 designates this service the CQRS read side with no domain layer.**
`application` is the innermost layer it has. A port declared anywhere else would either put an
interface in a package the specification says does not exist, or put it in `infrastructure` and
invert the dependency arrow §2.2 makes a HARD CONSTRAINT.

**3. spec-01 §1.5 makes the LLM the untrusted end of an Anti-Corruption Layer.**
Pricing independently validates the margin floor and treats agent output as a proposal. A boundary
that another bounded context defends itself against is a boundary this service should be able to
name, substitute and test in isolation — and an interface is what naming it consists of. The port
is the Anti-Corruption Layer's near side.

**4. It is ADR 0001's argument, applied to the least deterministic dependency in the system.**
ADR 0001 put `PriceSnapshotFeed` behind a port so a static seed could become a Kafka subscription
without touching application code; ADR 0003 did the same for the velocity baseline. The point here
is not that the model might be swapped, though spec-01 §2.7 requires that it can be. The point is
that everything FR14 must get right lives on the other side of this interface and can therefore be
asserted exactly, by tests that run in milliseconds with no broker, no key and no network.

## Consequences

**Positive**

- Every FR14 rule worth testing is testable: one trace per trigger, a trace even when the agent
  declines, tool calls in recorded order, exactly one publication. All of them are properties of
  ordinary code, and none could be asserted at all with the model inline.
- The test suite stays offline, free, fast and deterministic, and runs on a clean checkout with no
  API key. spec-01 §2.7's "never commit a key" needs no test-time exception.
- spec-01 §2.7's provider interchangeability falls out for free: swapping hosted API for local
  Ollama changes the adapter's configuration and no application-layer code.
- spec-01 §2.2 holds unbroken. `application` imports nothing from `infrastructure`, and
  `dev.langchain4j` appears in exactly one package.

**Negative / accepted trade-off**

- The stub is not the model. Tests prove that the orchestration around the agent is correct; they
  prove nothing about whether the agent's *judgement* is any good. That is accepted knowingly and
  is the right split — the quality of a recommendation is a matter for the demonstration and for
  the report, not for a unit test, and pretending otherwise is what a live-model test would do.
- One extra type, `AgentDeliberation`, exists only so the port can return evidence without
  performing effects. Accepted: the alternative is the LLM adapter minting the trace identifier and
  stamping `completedAt`, which puts two facts that must be reliable on the far side of the least
  reliable boundary in the system.

**Explicitly NOT affected**

- **The margin floor.** It stays in `pricing-service`'s domain layer (spec-01 §1.5 and §2.5, spec-02
  FR11). This ADR is about where the LLM is declared, not about what it is permitted to decide;
  team ruling R5 and spec-06 §7 govern that, and nothing here relaxes them.
- **`inventory-service` and `pricing-service`.** Both are Domain Model (spec-01 §2.3) and their
  ports remain in `domain` per ADR 0002 Decision 2. Nothing here generalises to them.
- **The FR12/FR13 topology.** Its stages, state stores and `application.id` are untouched
  (spec-05 §1). How the agent is triggered is spec-06 §2 and is a separate, still-open ruling.

## Compliance

- spec-06 §1.1 records the port, its adapters and its return type; spec-06 §8 records that the
  LangChain4j adapter is Phase B work.
- `MarkdownDecisionAgent`'s javadoc states the two-adapter rule and the no-live-model rule at the
  point of use, as `com.freshflow.sales.application`'s `package-info.java` does for ADR 0001.
- `insight-service/pom.xml` is unchanged in Phase A. The coordinates that would be added in Phase B
  are recorded in `docs/ai-log/2026-09-10-fr14-phase-a.md` §5 for approval against spec-01 §1.1.

## Verification

A reviewer can confirm compliance mechanically:

1. **No application-layer type imports LangChain4j.** From the repository root:
   `grep -r "import dev.langchain4j" insight-service/src/main/java/com/freshflow/insight/application/`
   must return nothing. The same grep over `insight-service/src/test/` must also return nothing —
   that is the stronger half, and after Phase B it is what keeps the library off the test
   classpath's reach.

   > **Superseded 11 September 2026, team ruling R10** — the clause about the test classpath only,
   > from "and after Phase B" onward. The grep over `src/test/` still returns nothing and is still
   > worth running; the *reason* given for it stopped being true when LangChain4j became a
   > compile-scope dependency. See the **Amendment** at the end of this document for the two checks
   > that replace it. The first sentence of this check is unaffected.

   The check matches `import dev.langchain4j` rather than the bare package name deliberately.
   `MarkdownDecisionAgent`'s javadoc names the package in the course of stating this very rule, and
   a check that a rule's own statement trips is a check nobody will trust for long.
2. **No test resolves a live model.** `insight-service/src/test/java` contains exactly one
   implementation of `MarkdownDecisionAgent`, `StubMarkdownDecisionAgent`, and it has no network
   client, no HTTP call and no reference to any provider. `mvn test` passes with no API key set and
   with no network access.
3. **The port is where this ADR says.** `MarkdownDecisionAgent` is under
   `.../insight/application/`, and `insight-service/src/main/java/com/freshflow/insight/` contains
   no `domain` directory (spec-01 §2.3).
4. **The dependency arrow points inward.** No type under `.../insight/application/` imports
   `com.freshflow.insight.infrastructure`.

---

## Amendment — 11 September 2026, team ruling R10

**Nothing above is deleted, and the original wording of check 1 stands as written.** It is the
record of what was decided on 10 September 2026 and of the reasoning available at that time. What
follows is the correction, dated, in the manner an ADR is corrected.

### What was wrong

Check 1 said that a `grep` over `insight-service/src/test/` "is what keeps the library off the test
classpath's reach", and the Decision section states the same thing more directly: "the stub lives in
test sources precisely so that LangChain4j never reaches the test classpath".

**That statement is false**, and became false the moment FR14 Phase B2 added LangChain4j to
`insight-service/pom.xml` at compile scope. Maven puts every compile-scope dependency on the test
classpath; there is no arrangement of source directories that changes it. Phase B2 found this,
reported it in `docs/ai-log/2026-09-10-fr14-phase-b2.md` §4.2, and deliberately did **not** quietly
edit the ADR to match — which is why this amendment exists rather than a silent diff.

### Why it matters more than a wording slip

The property this ADR protects is **that no test ever calls a live model**. Where the library sits
on a classpath was never that property; it was a *proxy* for it, and a good one while it held,
because a library that cannot be named cannot be called. When the dependency moved to compile scope
the proxy stopped measuring the thing it stood for, while continuing to look like a check.

**A check nobody can trust is worse than no check at all.** A reviewer who runs check 1, finds the
library on the test classpath, and concludes the ADR has been breached will have been misled by the
ADR; a reviewer who notices the check no longer measures anything will stop trusting the other
three. Either outcome costs more than the check was ever worth.

### The replacement — two checks that measure the property itself

Check 1's test-classpath clause is superseded by the following. Its first clause — no
application-layer type imports LangChain4j — is **unaffected and still stands**, as do checks 2, 3
and 4.

**Check A (formerly R10.1) — no test constructs a model client, from configuration or from an API key.**

Everything capable of building one lives in `MarkdownAgentConfiguration`, which is annotated
`@Profile("!test")`; every test in this service runs under `@ActiveProfiles("test")`. The model bean
is additionally nested inside a configuration conditional on a non-empty `freshflow.llm.api-key`, so
it cannot be constructed on a machine with no credential. Two independent guards, either sufficient.

**Check B (formerly R10.2) — `mvn test` passes with `LLM_API_KEY` and `LLM_MODEL` unset, and with no network access.**

This is the observable form of Check A and the one a reviewer can run on a clean checkout. It was
verified at Phase B2 and again at Phase B2b: 62 tests run, 62 pass, both variables unset.

Both already held before this amendment was written. **Nothing about the system changes here; what
changes is what the ADR claims to be checking**, so that the claim and the property are the same
thing again.

### One consequence, noted and NOT acted on

The superseded clause forbade any LangChain4j type in test sources, and so forbade a **test-source
fake `ChatModel`**. Phase B2 reported the coverage gap that follows from it: the tool-call encoding
is pinned by `MarkdownToolArgumentsTest`, but the line in `LangChain4jMarkdownDecisionAgent` that
*uses* the encoder is exercised for the first time in the B3 live run. Closing that gap means
scripting a fake model through the loop, which Check A and Check B permit and the superseded clause did
not.

**It is not being done here.** B2b is a documentation phase, and adding a test is a code change that
needs its own authorisation. Recorded so that whoever authorises it knows the obstacle is already
out of the way.

---

## Second amendment — 13 October 2026

**Why.** `docs/ai-log/2026-09-14-fr14-test-profile-guard-fix.md` §4 found that Check A, as written
on 11 September, asserted "two independent guards, either sufficient" without the context having
been started under the test profile with a key set. The `@Profile("!test")` on the outer
configuration did not reach the nested `@Configuration`, so with `LLM_API_KEY` set a test context
could have constructed a model client. The fix repeated the profile annotation on the nested class
and added `MarkdownAgentTestProfileGuardTest`. The two checks are restated here so that they say
what is now checked, and renamed because CLAUDE.md reused the identifier R10 on 4 October 2026 for
the margin-ratio ruling.

**Check A, restated.** The `@Profile("!test")` guard appears on every `@Configuration` class that
can construct a model client, nested classes included. The condition on a non-empty
`freshflow.llm.api-key` is a runtime-availability guard, not a test guard, and counts for nothing
here. `MarkdownAgentTestProfileGuardTest` verifies the property: it starts the context under the
test profile with `freshflow.llm.api-key` set to a dummy value and asserts that no model bean and no
LLM-backed bean exists. `BriefingAgentConfiguration` (spec-06 §11) follows the same rule.

**Check B, restated.** `mvn test` passes with `LLM_API_KEY` and `LLM_MODEL` unset and with no network
access, **and** passes with both variables set. The second environment is the one in which the
14 September defect was visible; the first alone cannot see it. Both were run on 13 October 2026.
