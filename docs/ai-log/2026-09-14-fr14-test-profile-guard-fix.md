# FR14 — defect fix: the test-profile guard did not reach the nested configuration

| Field | Value |
|---|---|
| Date | 14 September 2026 |
| Milestone | FR14, defect fix between Phase B2b and the B3 live run |
| Authorised by | Project team, 14 September 2026 — "DEFECT FIX: the insight-service test suite is environment-dependent. Fix the guard, then prove it with a test" |
| Tool / model | Claude Code (Opus 5) |
| Baseline | `0a4fa49` — 62 tests run, 62 pass **with `LLM_API_KEY` unset**; 8 errors with it set |
| Scope | `MarkdownAgentConfiguration.java` (one annotation, one javadoc block), one new test file, this log. **No `pom.xml`, no `.yml`, no existing test touched** |
| Result | 64 tests run, 64 pass, **both** with the LLM variables unset and with them set |

---

## 0. What this was

A guarantee stated in a comment that the code did not enforce, found by running the build in an
environment the verification had never used.

`MarkdownAgentConfiguration`'s class javadoc said, in terms:

> Every test in this service runs under `@ActiveProfiles("test")`, so **nothing declared in this
> class is built by any test context.**

and, of the profile guard and the api-key guard:

> **Two guards, and each would be sufficient on its own**

ADR 0004's R10.1 amendment, written three days earlier, repeated the same claim in the same words —
"Two independent guards, either sufficient." Both statements were false, and had been false since
the nested class was written in Phase B2. Nothing in the suite could tell, because the suite had only
ever been run in the one environment where the falsehood is invisible.

---

## 1. What I did

### 1.1 Reproduced the defect before changing anything

On this machine, with `LLM_API_KEY` and `LLM_MODEL` set to real-looking values:

```
Tests run: 2, Failures: 0, Errors: 2  -- AgentTraceApiIntegrationTest
Tests run: 2, Failures: 0, Errors: 2  -- RiskApiIntegrationTest
Tests run: 4, Failures: 0, Errors: 4  -- VelocityApiIntegrationTest
```

all eight with the same cause:

```
Error creating bean with name 'markdownAgentChatModel' defined in class path resource
[.../MarkdownAgentConfiguration$LlmBackedAgentConfiguration.class]:
  ... parameter 0: No qualifying bean of type
  'com.freshflow.insight.infrastructure.LlmProperties' available
```

With both variables unset, the same command was green. The authorisation's account of the root cause
is confirmed exactly.

### 1.2 The mechanism, stated precisely

`MarkdownAgentConfiguration` carries three annotations at the class level: `@Configuration`,
`@Profile("!test")` and `@EnableConfigurationProperties({LlmProperties, InventoryClientProperties})`.
Its nested `static class LlmBackedAgentConfiguration` carried `@Configuration` and
`@ConditionalOnExpression` on a non-empty api-key, and nothing else.

`@Configuration` is meta-annotated `@Component`, and Spring's component scanner accepts *independent*
nested classes — `static` nested types — as candidates in their own right. So the nested class is
registered on its own merits. `@Profile` is evaluated per registered candidate; it is **not**
inherited from an enclosing type. The consequence under `@ActiveProfiles("test")`:

| | Outer `MarkdownAgentConfiguration` | Nested `LlmBackedAgentConfiguration` |
|---|---|---|
| `@Profile("!test")` | present → **excluded** | absent → **not excluded** |
| api-key condition | n/a | true whenever `LLM_API_KEY` is set → **registered** |
| `LlmProperties` bean | would have come from the outer class's `@EnableConfigurationProperties` — **gone with it** | required by `markdownAgentChatModel` — **unsatisfiable** |

The two guards were not independent and were not each sufficient. They were guarding two *different*
sets of beans, and the set that most needed the profile guard — the one that builds a live model —
was the set that did not have it. The api-key condition was carrying the whole load on its own, and
it is not a test guard: it is true on precisely the machines that have a credential.

### 1.3 The fix — one line

```java
@Configuration
@Profile("!test")                                                      // added
@ConditionalOnExpression("'${freshflow.llm.api-key:}'.trim().length() > 0")
static class LlmBackedAgentConfiguration {
```

That is the whole change to behaviour. The api-key condition is **untouched**: it is doing a
different job, and it is the job RUNBOOK §B3.1 depends on — a runtime `insight-service` with no key
must still start and still serve FR12 and FR13, with FR14 simply not configured. Removing it to
"simplify to one guard" would have traded this defect for a service that will not boot without a
credential.

### 1.4 Corrected the javadoc to describe what the code now guarantees

The `<h2>Two guards, and each would be sufficient on its own</h2>` section is replaced by
`<h2>Two guards, doing two different jobs</h2>`. The new text:

- states that the profile guard has to be repeated on every `@Configuration` class in the file,
  including the nested one, and why — a `static` nested `@Configuration` is its own scan candidate
  and does not inherit `@Profile`;
- **says plainly that the earlier claim was false**, and what it cost: the suite passed or failed on
  the developer's environment;
- keeps the api-key rationale, now labelled as a different job rather than a second copy of the
  first, with the RUNBOOK §B3.1 reason attached;
- ends with the guarantee the code actually enforces and the name of the test that pins it.

A second, shorter note sits on the nested class itself, where someone deleting the "redundant"
annotation would read it.

### 1.5 The regression test

`insight-service/src/test/java/com/freshflow/insight/infrastructure/MarkdownAgentTestProfileGuardTest.java`
— two tests, one new file, no existing file edited.

```java
@SpringBootTest(properties = "freshflow.llm.api-key=not-a-credential-profile-guard-probe")
@ActiveProfiles("test")
class MarkdownAgentTestProfileGuardTest {
    assertThat(context.containsBean("markdownAgentChatModel")).isFalse();
```

Three decisions worth recording.

**It sets the api-key property itself rather than reading the environment.** A test that consults the
ambient `LLM_API_KEY` would reproduce the original fault in the test that exists to prevent it: green
or red depending on the developer's shell. Setting `freshflow.llm.api-key` through `@SpringBootTest`
satisfies the `@ConditionalOnExpression` on **every** machine, so the profile guard is the only thing
left between the test context and a live model client. The value is not a credential, is never used
as one, and nothing reaches the network — the assertion is exactly that the bean which would have
done so was never built.

**It asserts by bean name.** Asking for `ChatModel.class` would put a `dev.langchain4j` import into
test sources; a test that must name the library in order to prove the library is absent is a poor
instrument, and would tie the file to whichever provider module R2 admits.
`ApplicationContext.containsBean` needs only the name.

**The second test names the other two beans of the nested group** — `markdownDecisionAgent`,
`markdownAgentOrchestrator` — so that a partial fix, one that stops the model being built but leaves
its collaborators wired, is still a failure.

Verified in both directions, with `LLM_API_KEY` **unset** both times, which is the point:

| Source state | Result |
|---|---|
| `@Profile("!test")` removed from the nested class | `Failed to load ApplicationContext ... 'markdownAgentChatModel' ... No qualifying bean of type 'LlmProperties'` — **fails** |
| `@Profile("!test")` present | `Tests run: 2, Failures: 0, Errors: 0` — **passes** |

This is the coverage ADR 0004's R10 amendment recorded as unblocked and did not act on. It closes the
*profile-guard* half of R10.1. The other gap R10 named — a scripted fake `ChatModel` exercising the
tool-call encoder inside `LangChain4jMarkdownDecisionAgent` — is **not** addressed here and remains
first exercised in the B3 live run.

### 1.6 Audited the service for other configurations of the same shape

Every `@Configuration` in `insight-service`, main and test, and every nested class under
`*/src/main/java` across all seven modules:

| Class | Nested? | Parent guard | Verdict |
|---|---|---|---|
| `MarkdownAgentConfiguration.LlmBackedAgentConfiguration` | **yes, `static`** | `@Profile("!test")` + `@EnableConfigurationProperties` | **the defect — fixed** |
| `MarkdownAgentConfiguration` | no | — | top level; `@Profile` applies to itself |
| `InsightTopologyConfiguration` | no | — | `@ConditionalOnProperty` at top level, applies to itself |
| `ClockConfiguration`, `RiskPolicyConfiguration` | no | — | unguarded and intended to be |
| `InsightTestVelocityConfiguration`, `InsightTestAgentTraceConfiguration` | no | — | `@Profile("test")` at top level |

`grep -rn "    static class\|    class " --include=*.java */src/main/java` returns **exactly one
hit** across the whole repository — the class fixed here. There is no second instance of this shape,
in this service or any other. Nothing else was changed.

---

## 2. What I assumed that the specs did not state

1. **That the defect is the environment-dependence, not the failure.** Two readings were available:
   "the suite must not break when a key is present" and "a test context must never build a model
   client". They are satisfiable separately — deleting the api-key condition would have fixed the
   first and left the second as exposed as before. I took the second as the property, because it is
   the one ADR 0004 exists to protect, and fixed the first as its consequence. The authorisation's
   instruction not to weaken the api-key condition points the same way.

2. **That `MarkdownAgentTestProfileGuardTest` should force the condition true rather than observe the
   environment.** No spec says how a regression test for an environment-dependent defect should be
   written. A test that only fails when `LLM_API_KEY` happens to be set would have been faithful to
   the reproduction and useless as a guard on a clean checkout or in CI.

3. **That a fake-`ChatModel` test was out of scope.** R10 unblocked two things; the authorisation
   asked for one of them by name. §1.5 records that the other is still open.

---

## 3. Deviation from the specs

None. No spec, ADR or ruling was contradicted; no dependency, no `.yml` and no existing test was
touched. R3, R4 and R5 are unaffected, and `AntiCorruptionLayerGuardTest` is green — it scans
`src/main/java` only, so the new test file is outside its reach in any case, and the one-line source
change adds no forbidden literal.

---

## 4. Anywhere I judged the spec wrong — flagged, NOT acted on

**ADR 0004's R10.1 still contains the false claim.** Its text reads:

> The model bean is additionally nested inside a configuration conditional on a non-empty
> `freshflow.llm.api-key`, so it cannot be constructed on a machine with no credential. **Two
> independent guards, either sufficient.**

The last sentence was wrong when it was written on 11 September and is wrong on the page now. After
this fix the *code* satisfies it — the guards genuinely are two now — but the ADR arrived at a true
statement by asserting it rather than by checking it, and its reasoning for it is still the reasoning
that missed the nesting.

The irony is worth recording in full, because it is the same failure twice. The R10 amendment exists
because check 1 had become "a check nobody can trust" — a proxy that stopped measuring the property
while continuing to look like a check. R10.1 replaced it with a claim about `@Profile` coverage that
**also** did not measure the property, and for the same structural reason: it was verified by reading
the annotation rather than by running the context. R10.2, the observable form, ran only the
environment in which the gap cannot appear.

Two things follow, and **neither is done here**, because amending an ADR is a documentation change
needing its own authorisation:

- R10.1's "either sufficient" should be corrected to say that the profile guard must appear on each
  `@Configuration` class, that the api-key condition is a runtime-availability guard and not a test
  guard, and that `MarkdownAgentTestProfileGuardTest` is now what checks it.
- R10.2 should be restated as *both* environments — variables unset **and** variables set. As written
  it specifies the run that cannot see this class of defect, which is why the defect survived two
  verification passes.

Recorded for the team.

---

## 5. Test results — both environments, full `clean install`

Run (a) — `LLM_API_KEY` and `LLM_MODEL` **unset**:

| Module | Tests run | Failures | Errors | Skipped |
|---|---|---|---|---|
| contracts | — | — | — | — |
| inventory-service | 17 | 0 | 0 | 0 |
| sales-service | 9 | 0 | 0 | 0 |
| pricing-service | 2 | 0 | 0 | 0 |
| **insight-service** | **36** | **0** | **0** | **0** |
| pos-simulator | — | — | — | — |
| **Total** | **64** | **0** | **0** | **0** |

`BUILD SUCCESS`.

Run (b) — `LLM_API_KEY = AIzaSyD-not-a-real-key-…`, `LLM_MODEL = gemini-2.5-flash`:

| Module | Tests run | Failures | Errors | Skipped |
|---|---|---|---|---|
| contracts | — | — | — | — |
| inventory-service | 17 | 0 | 0 | 0 |
| sales-service | 9 | 0 | 0 | 0 |
| pricing-service | 2 | 0 | 0 | 0 |
| **insight-service** | **36** | **0** | **0** | **0** |
| pos-simulator | — | — | — | — |
| **Total** | **64** | **0** | **0** | **0** |

`BUILD SUCCESS`.

64 = the 62 of the B2b baseline plus the two tests added here. Run (b) had never been performed
before today; on the baseline commit it produces 8 errors. The key used is a syntactically plausible
string and not a credential — no key is committed anywhere in this change, and no test in either run
touched the network.

---

## 6. One line for the team

The verification we had asked whether the suite passes on a clean machine. It does, and it did all
along. What it never asked was whether the suite passes on a machine that is *ready to run FR14* —
and that is the machine every one of us will be sitting at for Phase B3.
