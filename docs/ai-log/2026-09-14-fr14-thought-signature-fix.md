# FR14 — defect fix: Gemini rejected the second ReAct turn for a missing thought signature

| Field | Value |
|---|---|
| Date | 14 September 2026 |
| Milestone | FR14 Phase B3 (live end-to-end run) — runtime defect found during the run |
| Authorised by | Project team, 14 September 2026 — "This task is a single configuration fix to a runtime defect found during the live run. It is authorised." |
| Tool / model | Claude Code (Opus 5) |
| Baseline | `722e03f` — 64 tests run, 64 pass |
| Scope | `MarkdownAgentConfiguration.java` (two builder calls), this log. **No `pom.xml`, no `.yml`, no test, no `LangChain4jMarkdownDecisionAgent.java`, no `SYSTEM_PROMPT` touched** |
| Result | 64 tests run, 64 pass |

---

## 0. The defect

During the B3 live run, the Gemini API returned **HTTP 400** on the **second** turn of the ReAct
loop — the first request carrying a `functionCall` part back to the model:

```
Function call is missing a thought_signature in functionCall parts. This is required
for tools to work correctly... Additional data, function call
`default_api:getBatchDetails`, position 2.
status: INVALID_ARGUMENT
```

Stack:

```
LangChain4jMarkdownDecisionAgent.deliberate(LangChain4jMarkdownDecisionAgent.java:149)
  -> MarkdownAgentOrchestrator.onRiskDetected(MarkdownAgentOrchestrator.java:122)
```

Line 149 is `chatModel.chat(...)` inside the step loop. Turn 1 succeeded, and the model asked for
`getBatchDetails`. The tool ran and its result was appended. Turn 2 re-sent the conversation,
including the model's own `functionCall`, and was refused.

The payload above is the excerpt the team supplied from the live run; the `...` is theirs. The full
response body was not given to me.

---

## 1. What I did

### 1.1 Diagnosis — supplied by the team, not re-investigated

As authorised, taken as given:

> Gemini 3-generation models require the client to echo back the opaque `thoughtSignature` that
> accompanied each functionCall part. langchain4j-google-ai-gemini 1.20.0 (already on the
> classpath) implements this, but BOTH flags default to false (BaseGeminiChatModel.java:77).
> LangChain4j's own documentation states that for Gemini 3 models it is MANDATORY to configure
> sendThinking() and returnThinking() to true.

With both flags false, the adapter drops the signature when it maps the response to an `AiMessage`
(`returnThinking`) and would not send one back even if it held one (`sendThinking`). The model's
earlier `functionCall` therefore goes back unsigned, and Gemini 3 refuses it.

### 1.2 Checked that the builder methods exist in the version on the classpath

The authorisation said to stop if either method was missing. I ran `javap` against
`~/.m2/repository/dev/langchain4j/langchain4j-google-ai-gemini/1.20.0/langchain4j-google-ai-gemini-1.20.0.jar`,
class `BaseGeminiChatModel$GoogleAiGeminiChatModelBaseBuilder`, which
`GoogleAiGeminiChatModel.builder()` inherits from:

```
public B returnThinking(java.lang.Boolean);
public B sendThinking(java.lang.Boolean);
```

Both are present and return the self type `B`, so they chain on the existing builder with no
cast. No substitution or version change was needed.

### 1.3 The change — two lines

`insight-service/src/main/java/com/freshflow/insight/infrastructure/MarkdownAgentConfiguration.java`,
`LlmBackedAgentConfiguration.markdownAgentChatModel`:

```java
return GoogleAiGeminiChatModel.builder()
        .apiKey(properties.getApiKey())
        .modelName(properties.getModel())
        .timeout(properties.getTimeout())
        .sendThinking(true)       // added
        .returnThinking(true)     // added
        .build();
```

That is the whole change. The model name, the provider and every dependency version are
unchanged, so R2 holds. The bean stays under both `@Profile("!test")` and the api-key condition,
so no test context builds it. `MarkdownAgentTestProfileGuardTest` still asserts that, and it is
green.

---

## 2. What I assumed that the specs did not state

1. **That the diagnosis is correct as supplied.** I checked only that the two builder methods exist
   (§1.2). I did not re-read `BaseGeminiChatModel.java:77` or
   `PartsAndContentsMapper.java:421`, and I did not consult the LangChain4j documentation. The
   authorisation said not to re-investigate. Whether the fix works is decided by the resumed B3 run,
   not by the suite: no test drives a real `GoogleAiGeminiChatModel`, so none can observe this
   change (see §5).

2. **That `returnThinking(true)` is acceptable in the trace.** With it on, the adapter may put the
   model's thinking text on the returned `AiMessage`. `LangChain4jMarkdownDecisionAgent` reads
   `answer.text()` for the closing rationale, not the thinking field. So I expect the persisted
   `rationale` to be unchanged. This has not been observed against a live model and should be
   checked on the first trace of the resumed run.

---

## 3. Deviation from the specs

None. No spec, ADR or ruling was contradicted. No dependency, `.yml` or test was touched. R4 and R5
are unaffected. The change adds no string literal, so `AntiCorruptionLayerGuardTest` has nothing new
to scan, and it is green.

---

## 4. Anywhere I judged the spec wrong — flagged, NOT acted on

### 4.1 Carry-forward caveat, as supplied by the team (verbatim)

> "langchain4j-google-ai-gemini attaches the concatenated thought signature to only the
>  FIRST function call in a turn (PartsAndContentsMapper.java:421, i == 0). If the agent
>  ever issues parallel tool calls in a single turn, this 400 will reappear. The system
>  prompt currently constrains it to one tool call per turn — that constraint is now
>  load-bearing, not stylistic."

### 4.2 The caveat's last sentence does not match the code

**The system prompt does not constrain the agent to one tool call per turn.**
`LangChain4jMarkdownDecisionAgent.SYSTEM_PROMPT` (lines 94–124) says "Work in this order: 1. Call
getBatchDetails … 2. Call getSellThroughRate … 3. Then decide", and "call proposeMarkdown exactly
once". That sets an order and caps the proposals. It does not say one call per turn, and nothing
else in the prompt does. A search of the file for "one tool", "single tool", "per turn", "at a
time" and "parallel" finds nothing.

Two facts make parallel calls a live risk, not a theoretical one:

- **The model does not need turn 1's result to make both evidence calls.** `describe(trigger)`
  (line 263) puts `batchId` **and `sku`** in the opening user message. `getBatchDetails` takes the
  batch and `getSellThroughRate` takes the SKU, so the model has both arguments before calling
  anything. A Gemini model is free to request both in one turn, and the numbered order in the prompt
  is a request, not a mechanism.
- **The loop accepts parallel calls.** Line 162 iterates over
  `answer.toolExecutionRequests()`, so a two-call turn is executed and recorded without complaint.
  The failure would come one turn later, on the next `chat(...)`, as the same HTTP 400 as §0.

So the constraint the caveat calls load-bearing does not exist. If the caveat's premise about
`PartsAndContentsMapper` is right, this fix protects a single-call turn only, and whether B3 passes
depends on how the model happens to batch its calls on a given run.

**Not acted on.** The authorisation forbids touching `LangChain4jMarkdownDecisionAgent.java` and
`SYSTEM_PROMPT`. Options for the team, none taken here:

- add an explicit "call one tool per reply" instruction to `SYSTEM_PROMPT`. This is still a request
  to the model rather than an enforcement, and `AntiCorruptionLayerGuardTest` would scan the new
  text;
- set the provider's parallel-function-calling control off, if the 1.20.0 builder or the Gemini
  API offers one. I did not check;
- accept the risk for B3, watch for a turn with two `functionCall` parts, and record it as a known
  limitation in spec-06 §10 next to G-7.

Recorded for the team.

---

## 5. Test results

Command as specified: `.\mvnw.cmd -q -pl insight-service -am test`

**The specified command runs 36 tests, not 64.** `-pl insight-service -am` builds `insight-service`
and its upstream modules (`contracts`, which has no tests). The 64 figure is the whole reactor, and
it matches the table in `2026-09-14-fr14-test-profile-guard-fix.md` §5. To report the 64 faithfully,
I also ran the whole reactor (`.\mvnw.cmd -q test`) before and after the change. Counts come from
`target/surefire-reports/TEST-*.xml`, and I checked the report timestamps to confirm each count
came from that run.

| Module | Before (`722e03f`) | After |
|---|---|---|
| contracts | — | — |
| inventory-service | 17 | 17 |
| sales-service | 9 | 9 |
| pricing-service | 2 | 2 |
| **insight-service** (the specified command) | **36 / 36 pass** | **36 / 36 pass** |
| pos-simulator | — | — |
| **Total (full reactor)** | **64 / 64 pass** | **64 / 64 pass** |

0 failures, 0 errors and 0 skipped in all four runs, and every run exited 0.

The count is unchanged, which is expected. The changed bean is excluded from every test context by
design (ADR 0004), so the suite proves the change broke nothing, but it cannot prove the change
fixes anything. Only the resumed B3 live run can.

---

## 6. One line for the team

The fix makes a single tool call per turn survive a Gemini 3 round trip. Nothing in the code
currently stops the model from making two in one turn.
