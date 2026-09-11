# FreshFlow — Week 3 Execution Runbook

**From**: Wednesday 12 August 2026, 12:00
**To**: Monday 17 August 2026, consultation with Dr. Su
**Method**: Spec-First Engineering → Agentic Coding → Continuous Auto-Validation (Lecture 3)

Steps are atomic. Each has a **DONE** criterion. Do not start the next step until the
current one is DONE. Chinese explanation blocks follow each phase.

---

## PHASE 0 — Today, Wednesday 12 August

### S1. Hold the Week 3 meeting `12:00–13:00` · All members

Open `docs/minutes/week03-minutes.md`. Fill in attendance, D4 (LLM runtime), D5 (coding
agent), and assign Name 1 / 2 / 3 in the action items.

Decide these four things and nothing else:

1. Who is Member 1 (Insight + agents), Member 2 (Inventory + Pricing), Member 3 (Sales + infra)
2. LLM runtime — hosted API or local Ollama (see S2)
3. Confirm the four-day schedule below
4. Next meeting time

**DONE**: minutes file filled in and committed.

---

### S2. Decide the LLM runtime `during S1` · All members

| Option | Advantage | Risk |
|---|---|---|
| Hosted API | Tool calling is reliable — this is what FR14 depends on | Requires an API key, may cost money |
| Local Ollama | No key, no cost, works offline | Small models are often unreliable at tool calling |

**Recommendation**: hosted API as primary, Ollama as fallback. Verify current free-tier
terms yourself on the provider's site before committing.

Whichever you pick, the provider must be injectable via `application.yml` so both paths
stay open. Never commit an API key.

**DONE**: D4 recorded in the minutes with a rationale.

---

### S3. Create the repository `13:00` · Member 3

```bash
mkdir freshflow && cd freshflow
git init
mkdir -p specs docs/ai-log docs/minutes docs/decisions
printf "# FreshFlow\n\nCSCI318 Spring 2026 Group Project\n" > README.md
printf "target/\n*.iml\n.idea/\n.env\n*.log\n" > .gitignore
git add -A && git commit -m "chore: scaffold repository"
```

Create the private GitHub repo, add the other two members as collaborators, push.

**DONE**: all three members have cloned and can push.

---

### S4. Drop in the specification set `13:00` · Member 3

Copy these five files into the repo:

```
specs/spec-01-architecture.md
specs/spec-02-requirements.md
specs/spec-03-domain-model.md
specs/spec-04-api-contract.yaml
specs/dev-agent-prompt.md
docker-compose.yml
docs/minutes/week03-minutes.md
```

```bash
git add -A && git commit -m "docs: add specification set (spec-first, L3 methodology)"
git push
```

**DONE**: pushed. All members can read the specs.

---

### S5. Bring up Kafka `13:30` · All three members, on their own machines

```bash
docker compose up -d
docker compose ps
```

Wait for `healthy`, then verify:

```bash
docker exec -it freshflow-kafka \
  /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list
```

Empty output is success — it means the broker answered.

Smoke test round trip:

```bash
docker exec -it freshflow-kafka /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server localhost:9092 --create --topic smoke-test \
  --partitions 1 --replication-factor 1

docker exec -it freshflow-kafka /opt/kafka/bin/kafka-console-producer.sh \
  --bootstrap-server localhost:9092 --topic smoke-test
# type: hello
# Ctrl-C

docker exec -it freshflow-kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 --topic smoke-test --from-beginning --timeout-ms 5000
```

> If `apache/kafka:3.9.0` is unavailable, check Docker Hub for the current tag and pin it.
> Record whichever version you use in `spec-01-architecture.md` §1.1.

**DONE**: all three members see `hello` come back. **This is today's hard gate.**

---

### S6. Verify LLM tool calling `14:30` · Member 1

Smallest possible test. One tool, one call. Do not build anything else yet.

```java
interface WeatherAssistant {
    String chat(String userMessage);
}

class TestTools {
    @Tool("Returns the current stock quantity for a batch")
    int getBatchQuantity(String batchId) {
        return 42;
    }
}
```

Ask it: *"How many units are left in batch BAT-0001?"* — the model must call the tool
and answer 42.

**DONE**: the tool is called and the answer is correct. Run it five times; it must work
every time. Record the result in `docs/ai-log/`.

> If it fails on a local model, switch to a hosted API today. Do not carry this risk
> into the weekend.

---

### S7. Start the AI collaboration log `today` · All members

`docs/ai-log/2026-08-12.md`:

```markdown
## 2026-08-12 — [name] — [task]
**Context**: what you were doing
**Tool / model**: Claude Code (Opus 4.6) / Codex / ...
**AI proposal**: what it suggested
**Disposition**: accepted / rejected / modified
**Rationale**: why. If rejected or modified, state the conflict with our
architectural decisions
**Output**: link to commit or file
```

**DONE**: one entry exists per member.

---

> ### 中文说明 — PHASE 0
>
> 今天只有两个硬性目标：**Kafka 起得来**（S5）和 **LLM 工具调用能用**（S6）。
>
> 这两个是整个项目仅有的单点风险。Kafka 起不来，三个人后面全部堵住；tool calling 不
> 稳，FR14 整条作废，Agentic AI 那一项就等于没做。今天排掉，周末才有意义。
>
> 规格文件我已经写好了，S4 直接复制进去即可。这不是偷懒——L3 的 Spec-First Engineering
> 意思就是**规格是被审阅的对象，不是被创作的仪式**。明天 S8 你们三个人要逐条评审并按
> 自己的理解改，那才是真正的工作。
>
> S6 特别提醒：**跑五次，五次都要成功**。跑一次成功就下结论，是这周最容易犯的错。

---

## PHASE 1 — Thursday 13 August · Specification Inspection

### S8. Review the specs as a team `morning` · All members

Read all four spec documents together. For each, ask:

- Is anything here that we cannot justify to Dr. Su?
- Is anything missing that the demo path needs?
- Do the domain invariants in Spec 3 match the requirements in Spec 2?

Edit freely. **These are your specs now, not mine.** You must be able to defend every
line of them on Monday.

**DONE**: all four specs reviewed, edited and committed.

---

### S9. Run development agent Phase 1 `late morning` · Member 1

In Claude Code, paste `specs/dev-agent-prompt.md` as the opening message, then:

```
Execute Phase 1 only. Do not proceed to Phase 2.
```

The agent inspects the specs for mutual consistency and halts on any problem.

Fix everything it reports. Re-run until Phase 1 passes clean.

**Copy its full report into `docs/ai-log/`.** This is direct evidence for the project
report's human–AI collaboration section, and a strong talking point on Monday.

**DONE**: Phase 1 passes with no unresolved inconsistencies; report archived.

---

### S10. Run development agent Phase 2 `afternoon` · Member 3

```
Phase 1 is complete and approved. Execute Phase 2 only:
create the Maven multi-module structure per spec-01 §1.2 with only the
mandatory dependencies. Do not implement any business logic.
```

Expected modules: `contracts`, `inventory-service`, `sales-service`, `pricing-service`,
`insight-service`, `pos-simulator`.

Verify:

```bash
mvn -q clean install
```

Then start each service and confirm the port:

```bash
cd inventory-service && mvn spring-boot:run    # expect 8081
```

**DONE**: `mvn clean install` succeeds; all four services start on 8081–8084.

---

### S11. Run development agent Phase 3 `afternoon` · Member 3

```
Execute Phase 3 only: generate MockMvc integration tests from
specs/spec-04-api-contract.yaml. Cover happy path and every error path,
including the 422 margin floor rejection. Do not implement production code.
Tests must fail.
```

```bash
mvn test
```

**Tests must FAIL.** If they pass, the agent implemented something — reject and re-run.

**DONE**: tests exist and fail for the right reason (missing implementation).

---

> ### 中文说明 — PHASE 1
>
> S9 是本周性价比最高的一步。让 dev agent 去找四份规格里的自相矛盾，几分钟就能发现人工
> 评审半天都看不出来的问题。**而且它报出来的每一条，都是报告 Section 5 的现成素材。**
>
> S11 最反直觉：**测试必须失败**。这就是 Continuous Auto-Validation 的顺序——契约先于
> 实现，测试先于代码。如果 Agent 顺手把功能实现了，要退回重来，因为那就失去了"测试驱动"
> 的证据。周一给 Dr. Su 看"我们的测试是从 OpenAPI 契约生成的，写在实现之前"，比给他看
> 代码有说服力得多。

---

## PHASE 2 — Friday 14 August · MVP-CORE

**Target**: `POST /api/transactions` → `item-sold` → Kafka → windowed aggregation →
`GET /api/insight/velocity/{sku}` returns a number that changes.

### S12. Inventory FR1 `morning` · Member 2

```
Execute Phase 4 for inventory-service, FR1 only (MVP-CORE).
Domain Model pattern per spec-01 §2.3: StockBatch is a POJO aggregate root,
persistence ignorant, no JPA annotations. Separate JPA entity plus mapper in
infrastructure. Enforce the invariants in spec-03 §1 inside the aggregate.
Publish batch-received.
```

**DONE**: `POST /api/batches` returns 201; its test passes; event visible on the topic.

---

### S13. Sales FR5 + FR6 `morning` · Member 3

```
Execute Phase 4 for sales-service, FR5 and FR6 (MVP-CORE).
Transaction Script pattern per spec-01 §2.3 — no aggregates, no repository
abstraction. Publish one item-sold event per line item, each carrying
producer-assigned occurredAt.
```

**DONE**: `POST /api/transactions` returns 201; one `item-sold` per line item on the topic.

---

### S14. Insight FR12 `afternoon` · Member 1

```
Execute Phase 4 for insight-service, FR12 only (MVP-CORE).
Kafka Streams topology: consume item-sold, key by SKU, aggregate quantity over a
60-second sliding window. Use occurredAt as event time, not ingestion time.
Expose via Interactive Query at GET /api/insight/velocity/{sku}.
No domain layer in this service — read model only.
```

**DONE**: the endpoint returns a value; it changes as transactions are posted.

---

### S15. POS simulator `afternoon` · Member 3

A script that POSTs transactions at a configurable rate, with a `--slow` flag that drops
the rate. The slow mode is what makes the velocity drop visible during the demo.

**DONE**: running it moves the velocity number; `--slow` makes it fall.

---

### S16. CHECKPOINT — Friday 22:00 `hard gate` · All members

Run the full chain end to end in front of all three members.

- **PASS** → continue to Phase 3.
- **FAIL** → Saturday morning is spent entirely on MVP-CORE. Agent work slips to next
  week. Monday's demo shows the specs plus stream processing. **This is still a good
  consultation.**

**DONE**: decision made and recorded in the minutes. No "let's try one more night".

---

> ### 中文说明 — PHASE 2
>
> 今天的目标是**一条最细的线穿过全部四个服务**，业务逻辑能多假就多假。风险判定就是个
> `if`，窗口只有 60 秒，Inventory 只有收货一个接口。全都无所谓。
>
> **跑通比正确重要**——跑通证明的是架构，而架构才是被评分的东西。
>
> 注意 S12 和 S13 用的是**不同的领域层模式**。这不是不一致，这是 L3 讲的按复杂度选择模式。
> 周一 Dr. Su 大概率会问这个，三个人都要能答。
>
> S16 是硬关卡。**周五 22:00 到点就做决定**，不要"再试一晚"。带一个跑得稳的 MVP-CORE
> 加四份扎实的规格去见 Dr. Su，远好过带一个半通不通的完整链路。

---

## PHASE 3 — Saturday 15 August · MVP-PLUS

### S17. Insight risk detection FR13 (simplified) `morning` · Member 1

```
Execute Phase 4 for insight-service, FR13 MVP simplification only.
Threshold test on the windowed count. Publish sell-through-risk-detected.
The stream-table join is the Week 7 target — do not build it now.
```

**DONE**: dropping the simulator to slow mode produces a risk event.

---

### S18. Markdown Decision Agent FR14 `morning–afternoon` · Member 1

```
Execute Phase 4 for insight-service, FR14 with the two MVP tools only:
getBatchDetails and proposeMarkdown. ReAct / tool-use pattern via LangChain4j.
Persist the full tool-call trace as AgentTraceRecord and expose it at
GET /api/insight/agent-traces/{agentTraceId}.
The agent proposes only — it must not write prices directly.
```

**DONE**: a risk event produces a `markdown-recommended` event and a queryable trace.

---

### S19. Pricing FR9 + FR11 `afternoon` · Member 2

```
Execute Phase 4 for pricing-service, FR8 (read only), FR9 and FR11.
Domain Model pattern. MarginFloorPolicy is a domain service. The margin floor
check happens in the domain layer, never in the controller. Return 422 with the
MarginFloorRejection schema on breach. Publish markdown-applied on success.
```

**DONE**: the 30% proposal returns 201; the 60% proposal returns 422 with a reason.

---

### S20. Architecture diagrams `afternoon` · Member 3

Three diagrams for Monday: service topology with Kafka topics; the context map with the
four integration patterns named; the event loop.

**DONE**: exported to `docs/diagrams/`.

---

### S21. CHECKPOINT — Saturday 22:00 `hard gate` · All members

Is the agent stable? Run it ten times.

- **STABLE** → continue to Phase 4.
- **UNSTABLE** → **cut it tonight.** Sunday is spent polishing MVP-CORE, the specs and
  the demo. Do not carry an unstable agent into Monday.

**DONE**: decision made and recorded.

---

## PHASE 4 — Sunday 16 August · Close-out

### S22. Sales consumes markdown-applied `morning` · Member 3

```
Execute Phase 4 for sales-service: consume markdown-applied and markdown-expired,
maintain the local PriceSnapshot, apply the effective price at scan time per FR6.
```

**DONE**: after a markdown, the next scan of that SKU records the discounted price.

---

### S23. Write the README `morning` · Member 3

Prerequisites, start-up sequence, the demo steps, and how to reset state.

**DONE**: a fresh clone can be brought up by following it alone.

---

### S24. Rehearse twice, timed `afternoon` · All members

Follow the demo script in §Appendix A. Target 8–10 minutes.

**DONE**: two clean runs.

---

### S25. Record the backup video `afternoon` · Member 3

Screen recording of the full demo. Insurance against live failure, and reusable material
for the Week 8 Progress Report video.

**DONE**: video in `docs/`.

---

### S26. Finalise the log and questions `evening` · All members

- AI collaboration log up to date for all three members
- ADRs written for D1–D7 in `docs/decisions/`
- Monday question list finalised (§Appendix B)

**DONE**: everything committed and pushed.

---

> ### 中文说明 — PHASE 3 & 4
>
> S18 的关键是 **agent 只能"提议"，不能直接写价格**。毛利底线的强制执行在 pricing-service
> 的领域层——这就是 Anti-Corruption Layer 应用在 AI 集成上。S19 的 422 拒绝是整场演示最
> 有价值的一步，它把报告里的设计论证变成了可见的行为。
>
> S21 同样是硬关卡。周六晚上 agent 还不稳就当晚砍掉，别拖到周日。

---

# PART B3 — FR14 End-to-End Run (Week 9)

**Added 11 September 2026 at the end of FR14 Phase B2; corrected for Windows PowerShell the same
day (Phase B2b).** Everything below is written to be **executed at a keyboard**, one command at a
time, not skimmed. Nothing in it has been run: Phase B2 was code, configuration and documentation
only, and this section is the procedure the B3 session and the Week 8 progress video follow.

It is the first time any of the following happens for real: a live model call, the `markdownAgent`
Kafka binding, the synchronous REST call from `insight-service` to `inventory-service`, and a
`markdown-recommended` publication.

> **The primary shell here is Windows PowerShell**, because that is what the machine recording the
> demonstration runs. Every step gives the PowerShell form first. A **bash** form follows for
> teammates on macOS or Linux; the two do the same thing and either may be used, but do not mix a
> bash line into a PowerShell terminal.

> **Stop conditions.** If a step's DONE criterion fails, stop and diagnose it before continuing.
> §B3.9 lists the failure modes that are expected to be possible on a first run and what each
> looks like.

### Three PowerShell facts this section depends on

These are the reasons the commands below look the way they do. All three were verified on the
machine this will run on (Windows PowerShell **5.1**).

| Fact | Consequence here |
|---|---|
| `curl` is an **alias for `Invoke-WebRequest`**, which takes entirely different parameters | Every request below calls **`curl.exe`**, the real curl that ships at `C:\Windows\System32\curl.exe`. The `.exe` bypasses the alias, and all the ordinary flags then work unchanged |
| PowerShell 5.1 **strips double quotes** when passing an argument to a native program | JSON bodies are written with `\"` for every inner quote. Without the backslashes curl receives `{sku:X}`, which is not JSON, and the endpoint answers `400` for a reason nothing on screen explains |
| `&&` is **not a valid statement separator** in PowerShell 5.1, and `\` is not a line continuation | Commands are chained with `;` and **each one is on a single line**. Long lines are deliberate: a single line is what survives being pasted, and a continuation character that is wrong for the shell is the failure this correction exists to prevent |

---

## B3.0 Prerequisites

| | |
|---|---|
| Docker Desktop | running |
| JDK 21 | `java -version` reports 21 |
| An LLM API key | Google AI Studio, for the `google-ai-gemini` provider (spec-01 §1.1, §2.7) |
| Six terminals | Kafka, four services, and one for `curl.exe` |

Confirm the working tree is the reviewed one. This one is the same in both shells:

```powershell
git log --oneline -1
git status --short          # expect no modified tracked files
```

---

## B3.1 Environment variables

**Two variables, both read only from the environment. Never put a key in a file, and never commit
one** (spec-01 §2.7).

**PowerShell** — in **every** terminal that will start `insight-service`:

```powershell
$env:LLM_API_KEY = "<paste your key here>"
$env:LLM_MODEL   = "gemini-3.6-flash"
```

**bash**, for macOS or Linux:

```bash
export LLM_API_KEY='<paste your key here>'
export LLM_MODEL='gemini-3.6-flash'
```

> **On the model name.** `gemini-3.6-flash` is the model verified working against the live API on
> 11 September 2026. It is **configuration, not code**: it reaches the service as `${LLM_MODEL}`
> and nothing else, `application.yml` names only the placeholder, and no `.java` file mentions any
> model. If a future reader finds this name deprecated in turn — which has already happened once to
> this project, see `docs/ai-log/2026-09-11-fr14-phase-b2b.md` — **the fix is to set a different
> environment variable here.** It is not a code change, it is not a rebuild, and it is not a
> specification change. That is what ADR 0004's port is for.

| Variable | Meaning | If unset |
|---|---|---|
| `LLM_API_KEY` | The provider credential | **FR14 is not wired at all.** `insight-service` still starts and still serves FR12 and FR13; the trigger consumer logs `[fr14-not-configured]` and drops the event |
| `LLM_MODEL` | The model name | The model client is built with an empty model name and the first call fails. Set it |
| `LLM_PROVIDER` | Optional; defaults to `google-ai-gemini` | — |
| `INVENTORY_BASE_URL` | Optional; defaults to `http://localhost:8081` | — |
| `FRESHFLOW_KAFKA_BROKERS` | Optional; defaults to `localhost:9092` | — |

Confirm the key is set and **do not echo it**:

```powershell
if ($env:LLM_API_KEY) { "LLM_API_KEY is set ($($env:LLM_API_KEY.Length) chars)" } else { "NOT SET" }
"LLM_MODEL = $env:LLM_MODEL"
```

```bash
if [ -n "$LLM_API_KEY" ]; then echo "LLM_API_KEY is set (${#LLM_API_KEY} chars)"; else echo "NOT SET"; fi
echo "LLM_MODEL = $LLM_MODEL"
```

**DONE**: the key reports as set, the model name prints, and the key itself has not appeared on
screen or in any file.

---

## B3.2 Bring up Kafka

Same in both shells:

```powershell
docker compose up -d
docker compose ps
```

Wait for `healthy`, then — **one line**, because `\` does not continue a line in PowerShell:

```powershell
docker exec freshflow-kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list
```

```bash
docker exec freshflow-kafka /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server localhost:9092 --list
```

**DONE**: the command answers. A clean broker lists nothing, or lists the topics from a previous
run — either is fine.

> **To start from nothing**, note the `;` rather than `&&`:
>
> ```powershell
> docker compose down -v; docker compose up -d
> ```
>
> Do this if a previous run left the Kafka Streams state stores holding batch state you do not
> want; the H2 databases are in-memory and are discarded when a service stops either way.

---

## B3.3 Build

```powershell
.\mvnw.cmd clean install
```

```bash
./mvnw clean install
```

**DONE**: `BUILD SUCCESS`, 62 tests run, 62 pass. The build must be green **before** a service is
started; a failure here is a code problem, and diagnosing it against four running services is
harder for no reason.

---

## B3.4 Start the four services, in this order

One terminal each. **Order matters**, and each reason is different.

| # | Service | Port | Why here |
|---|---|---|---|
| 1 | `inventory-service` | 8081 | Owns batches. `insight-service` calls it synchronously during a deliberation (spec-06 §3), so it must be up before any risk event fires |
| 2 | `sales-service` | 8082 | Produces `item-sold`, which is what drives everything |
| 3 | `pricing-service` | 8083 | Not consumed by FR14. Started so the topology of §1.2 is complete and `markdown-recommended` has somewhere to go in a later milestone |
| 4 | `insight-service` | 8084 | **Last.** It binds the Kafka Streams topology, the baseline seeder and the FR14 consumer, and starting it after the producers keeps the first run's logs readable |

```powershell
# terminal 1
.\mvnw.cmd -pl inventory-service spring-boot:run

# terminal 2
.\mvnw.cmd -pl sales-service spring-boot:run

# terminal 3
.\mvnw.cmd -pl pricing-service spring-boot:run

# terminal 4 -- the one with LLM_API_KEY set
.\mvnw.cmd -pl insight-service spring-boot:run
```

```bash
./mvnw -pl inventory-service spring-boot:run
./mvnw -pl sales-service     spring-boot:run
./mvnw -pl pricing-service   spring-boot:run
./mvnw -pl insight-service   spring-boot:run     # the one with LLM_API_KEY set
```

### What to look for in `insight-service`'s startup log

Four lines, and all four must be there. **This is the gate for the whole run**; everything after it
assumes FR14 is wired.

```
FR14: LLM provider=google-ai-gemini model=gemini-3.6-flash timeout=PT1M maxSteps=8 (api key supplied from the environment, never logged)
FR12 topology: window=60s advancing every 5s, grace 5s, retention 10min; windowing on producer-assigned occurredAt (spec-01 s2.6)
FR13 topology: shortfall threshold 5.0 unit(s), 24.0 trading hours per day
Seeded 1 of 1 velocity baseline(s) from freshflow.insight.velocity.baselines
```

**If the first line is absent**, `LLM_API_KEY` was not visible to that process. The service will
still run; FR14 will not. Stop it, set the variable in that terminal, start it again.

Confirm both functions bound:

```powershell
docker exec freshflow-kafka /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 --list
```

```bash
docker exec freshflow-kafka /opt/kafka/bin/kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 --list
```

**DONE**: the list contains `freshflow-insight-markdown-agent` (the FR14 consumer, spec-06 §2) and
`freshflow-insight-velocity` (the Kafka Streams topology). Two groups, two binders, one service —
which is the arrangement spec-06 §2 ruled for.

---

## B3.5 Open the two observation windows

Leave these running for the rest of the procedure. Run each in its own terminal. These keep `-it`,
because they are long-running consumers that need a terminal attached; the one-shot `docker exec`
commands above do not, and dropping the flag there avoids a TTY complaint on some hosts.

```powershell
# 1. the trigger -- FR13's output and FR14's input
docker exec -it freshflow-kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic sell-through-risk-detected

# 2. the result -- FR14's output, and what pricing-service will consume in FR9
docker exec -it freshflow-kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic markdown-recommended
```

```bash
docker exec -it freshflow-kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 --topic sell-through-risk-detected

docker exec -it freshflow-kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 --topic markdown-recommended
```

The third thing to watch is `insight-service`'s own log, already open from §B3.4.

---

## B3.6 Inject a batch

First generate the expiry timestamp — do not type one by hand:

```powershell
[DateTime]::UtcNow.AddMinutes(70).ToString("yyyy-MM-ddTHH:mm:ssZ")
```

```bash
date -u -d '+70 minutes' +%Y-%m-%dT%H:%M:%SZ      # GNU date
date -u -v+70M +%Y-%m-%dT%H:%M:%SZ                # BSD/macOS date
```

Then post the batch, pasting that timestamp in place of `<EXPIRY>`. **One line. Every inner quote
is backslash-escaped** — that is not decoration, it is what makes PowerShell hand curl valid JSON:

```powershell
curl.exe -s -X POST http://localhost:8081/api/batches -H "Content-Type: application/json" -d '{\"sku\":\"SKU-YOG-1KG\",\"quantity\":400,\"expiryAt\":\"<EXPIRY>\",\"unitCost\":{\"amount\":2.10,\"currency\":\"AUD\"},\"supplierRef\":\"B3-RUN\"}'
```

If you would rather stay in PowerShell's own idiom, this is equivalent and needs no escaping,
because nothing is handed to a native program:

```powershell
$body = '{ "sku": "SKU-YOG-1KG", "quantity": 400, "expiryAt": "<EXPIRY>", "unitCost": { "amount": 2.10, "currency": "AUD" }, "supplierRef": "B3-RUN" }'
Invoke-RestMethod -Method Post -Uri http://localhost:8081/api/batches -ContentType 'application/json' -Body $body
```

```bash
curl -s -X POST http://localhost:8081/api/batches \
  -H "Content-Type: application/json" \
  -d '{
        "sku": "SKU-YOG-1KG",
        "quantity": 400,
        "expiryAt": "<EXPIRY>",
        "unitCost": { "amount": 2.10, "currency": "AUD" },
        "supplierRef": "B3-RUN"
      }'
```

**DONE**: `201 Created`, and the response body's `batchId` is noted down — call it `BAT-x`. A
`batch-received` event is now on the topic, and `insight-service`'s batch-state `KTable` has a row
for `SKU-YOG-1KG`.

### Why 400 units and 70 minutes — the arithmetic FR13 will do

FR13 flags a batch when `remainingQuantity − (velocityPerHour × tradingHoursRemaining)` exceeds
`shortfall-threshold`, which is 5.0 (spec-01 §2.7). Two things about the numbers are easy to get
wrong on a first run and both are worth understanding before §B3.7:

- **`hoursToExpiry` is truncated to whole hours.** At 70 minutes out it is `1`. At 50 minutes out it
  is `0`, and a batch with `hoursToExpiry` of 0 is **never** flagged — FR13 exists to catch batches
  early enough that a markdown can still change the outcome. **Do not let the batch get within an
  hour of expiry before §B3.7 produces a risk event.**
- **A 60-second window makes velocity look enormous.** One unit sold inside a 60-second window is
  60 units per hour. Selling one unit every 30 seconds gives roughly 120/h.

So at 120/h with one hour remaining, projected sales are about 120 and the shortfall is about
`400 − 120 = 280`, comfortably over the threshold of 5. The 400 is chosen to keep the run flagging
even if the sales pace in §B3.7 is faster than intended — anything under about 395 units per hour
still flags.

---

## B3.7 Drive sales until a window closes and the risk fires

Post one small transaction roughly every 30 seconds, for **at least three minutes**.

A single sale, one line, escaped as above:

```powershell
curl.exe -s -X POST http://localhost:8082/api/transactions -H "Content-Type: application/json" -d '{\"tillId\":\"TILL-1\",\"lineItems\":[{\"sku\":\"SKU-YOG-1KG\",\"quantity\":1}]}'
```

The whole three minutes, unattended. Written without backtick continuations on purpose — every
statement is complete on its own line, so the block can be pasted in one go:

```powershell
$body = '{ "tillId": "TILL-1", "lineItems": [ { "sku": "SKU-YOG-1KG", "quantity": 1 } ] }'
foreach ($i in 1..8) {
    Invoke-RestMethod -Method Post -Uri http://localhost:8082/api/transactions -ContentType 'application/json' -Body $body | Out-Null
    Write-Host "posted $i of 8"
    Start-Sleep -Seconds 30
}
```

```bash
for i in $(seq 1 8); do
  curl -s -X POST http://localhost:8082/api/transactions \
    -H "Content-Type: application/json" \
    -d '{ "tillId": "TILL-1", "lineItems": [ { "sku": "SKU-YOG-1KG", "quantity": 1 } ] }' > /dev/null
  echo "posted $i of 8"
  sleep 30
done
```

**Three minutes is not padding.** FR12's windows are placed on producer-assigned `occurredAt` and
are suppressed until they close, and a window closes only when **stream time** passes its end plus
the 5-second grace. Stream time advances because later records arrive. Stop posting and the last
window never closes, so FR13 never assesses and FR14 is never triggered. This is known limitation
G-4 in spec-05 §7 seen from the operator's side.

Watch the velocity move while you wait. Run this in the spare terminal, not the one running the
loop:

```powershell
curl.exe -s http://localhost:8084/api/insight/velocity/SKU-YOG-1KG
```

```bash
curl -s http://localhost:8084/api/insight/velocity/SKU-YOG-1KG
```

A `404` means no window has closed yet — keep posting. A body with `velocityPerHour` means FR12 is
live.

Then:

```powershell
curl.exe -s http://localhost:8084/api/insight/risks
```

```bash
curl -s http://localhost:8084/api/insight/risks
```

**DONE**: observation window 1 shows a `sell-through-risk-detected` event for `BAT-x`, and
`GET /api/insight/risks` lists it with `atRisk` true. **Copy the event's `eventId`** — it is the
key to everything in §B3.8.

---

## B3.8 Watch the agent deliberate, then read the trace

Within a few seconds of the risk event, `insight-service`'s log shows the deliberation:

```
[fr14-deliberation] batchId=BAT-x toolCalls=3 proposed=true
[published] topic=markdown-recommended eventId=... batchId=BAT-x tier=PERCENT_30 agentTraceId=TRC-<the risk eventId>
[fr14-trace] agentTraceId=TRC-<the risk eventId> batchId=BAT-x finalRecommendation=PERCENT_30 from ... until ...
```

**The trace identifier is `TRC-` followed by the trigger event's `eventId`** (spec-06 §5), so it can
be constructed from §B3.7's copied value without reading the log at all. Fetch it:

```powershell
curl.exe -s http://localhost:8084/api/insight/agent-traces/TRC-<the risk eventId>
```

```bash
curl -s http://localhost:8084/api/insight/agent-traces/TRC-<the risk eventId>
```

PowerShell will pretty-print it if you would rather read it than squint at it:

```powershell
Invoke-RestMethod -Uri http://localhost:8084/api/insight/agent-traces/TRC-<the risk eventId> | ConvertTo-Json -Depth 6
```

### What to check in the response — this is the demonstration

| Field | What it must show | What it proves |
|---|---|---|
| `toolCalls[].sequence` | `1, 2, 3`, ascending, no gaps | spec-04 declares `sequence` per call; the order survives agent → record → database → DTO → JSON, and nothing re-sorts on the way out (spec-06 §6) |
| `toolCalls[].toolName` | Only `getBatchDetails`, `getSellThroughRate`, `proposeMarkdown` | **R4: three tools, and no fourth exists to be called** |
| `toolCalls[].arguments` | A bare `BAT-x`, a bare `SKU-YOG-1KG`, and a four-part comma string | The encoding `MarkdownToolArgumentsTest` pins, working against a real model for the first time |
| `toolCalls[].result` | `batchId=… unitCost=2.10 AUD`, `velocityPerHour=…`, `recorded batchId=… tier=…` | The tools answered; the `unitCost` line is the synchronous REST call to `inventory-service` having succeeded (spec-06 §3) |
| `finalRecommendation` | `PERCENT_nn from … until …` | The promotion window's end was derived from the batch's expiry, not asked of the model (spec-06 §4) |
| `rationale` | The model's own sentences, citing the numbers it actually saw | FR14's written justification |
| `batchId` | `BAT-x` | R7: the proposal was for the batch the agent was woken for |

And in observation window 2, one `markdown-recommended` event carrying `agentTraceId`,
`effectiveFrom`, `effectiveTo` and the rationale.

### Prove the idempotency, which is the part that has to be right

Re-deliver the same trigger and confirm nothing new happens. The reset requires the consumer group
to be idle, so **stop `insight-service` first, run the reset, then start it again.**

```powershell
docker exec freshflow-kafka /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 --group freshflow-insight-markdown-agent --topic sell-through-risk-detected --reset-offsets --to-earliest --execute
```

```bash
docker exec freshflow-kafka /opt/kafka/bin/kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 --group freshflow-insight-markdown-agent \
  --topic sell-through-risk-detected --reset-offsets --to-earliest --execute
```

**DONE**: on restart the log shows the trigger arriving and **no** second `[fr14-deliberation]`,
**no** second `markdown-recommended` in observation window 2, and the same
`TRC-<eventId>` returned. One trigger, one trace, one publication (spec-06 §5).

---

## B3.9 Failure modes to expect on a first run

Nothing below has been observed; each is a place where B2's code meets something it has never met.
The first three rows are shell problems rather than system problems, and are listed first because
they are the ones that will waste time while looking like something else.

| Symptom | Likely cause | What to do |
|---|---|---|
| `Invoke-WebRequest : A parameter cannot be found that matches parameter name 's'` | You typed `curl`, not `curl.exe`. In PowerShell `curl` is an alias for `Invoke-WebRequest` | Add the `.exe`. See the three PowerShell facts at the top of this part |
| `400 Bad Request` on a request whose JSON looks right on screen | The inner double quotes were not backslash-escaped, so PowerShell handed curl `{sku:X}` | Escape every inner `"` as `\"`, or use the `Invoke-RestMethod` form |
| `The token '&&' is not a valid statement separator in this version` | PowerShell 5.1 has no `&&` | Use `;` |
| No `FR14: LLM provider=` line at startup | `LLM_API_KEY` not set in that terminal | §B3.1, then restart `insight-service` |
| `[fr14-not-configured]` on a risk event | Same | Same |
| A model error on the first call, e.g. `404 ... is no longer available` or a quota message | The model name is wrong for this key, withdrawn by the vendor, or over quota | **Change `LLM_MODEL` and restart the service.** It is an environment variable and nothing else — no code, no rebuild. This has already happened to this project more than once; see `docs/ai-log/2026-09-11-fr14-phase-b2b.md` |
| The trace has many `error=INVALID_ARGUMENTS` results | The model is emitting tool arguments in a shape `MarkdownToolArguments` does not read | The trace itself shows exactly what was sent and what came back. Read the `arguments` and `result` of the failing calls before changing anything |
| `finalRecommendation` is `PROPOSED_BUT_UNRESOLVABLE` | The agent proposed, but `inventory-service` could not resolve the batch, so there is no `effectiveTo` (R9, spec-06 §6) | Is `inventory-service` up? Is `INVENTORY_BASE_URL` right? |
| `finalRecommendation` is `NO_MARKDOWN` with a `proposeMarkdown` call in the trace | Either the model decided against a markdown — a legitimate outcome — or it proposed for a different batch and R7 refused it (spec-06 §4.3) | Compare the call's `batchId` with the trace's |
| The consumer group rebalances mid-deliberation | A model call exceeded `max.poll.interval.ms` (set to 10 minutes) | Raise `timeout` or lower `max-steps` in `application.yml` |
| No risk event however long sales run | `hoursToExpiry` has reached 0, or velocity is high enough to clear the batch | Recompute against §B3.6's arithmetic and inject a fresh batch |
| A trace exists but `markdown-recommended` is empty | Known limitation **G-7** (spec-06 §10): save and publish are not atomic, and the redelivery is suppressed by the idempotency check | Expected behaviour, not a defect. Say so on camera if it happens; it is the honest boundary of at-least-once delivery and the report documents it |

---

## B3.10 What this run is evidence of

For the progress video and for Section 3 of the report:

| Shown | Requirement or clause |
|---|---|
| Velocity moving as sales arrive | FR12, spec-05 |
| A batch flagged without anyone asking | FR13, the stream-table join |
| An ordered, queryable tool-call trace | **FR14** — agentic AI, and auditable rather than merely present |
| The agent proposing and stopping | R5, spec-01 §1.5 — the Anti-Corruption Layer's near side |
| One trigger, one trace, one publication | spec-06 §5, and spec-01 §2.6's `eventId` rule |
| A model name changed by setting an environment variable | ADR 0004 — the provider is configuration, and the port is why |

**What this run does not show**, and should not be claimed to: that the agent's *judgement* is any
good. The tests prove the orchestration around it; a demonstration proves the loop closes. The
quality of a recommendation is a matter for the report's evaluation section, and ADR 0004 says so in
as many words.

The step that makes the architecture visible — a 60% markdown submitted by hand and rejected with
`422` by `pricing-service`'s domain layer — is **FR9 and FR11 work, not FR14's**, and is a separate
milestone. Until it exists, the Anti-Corruption Layer is demonstrated by what `insight-service` is
shown *not* to contain: `AntiCorruptionLayerGuardTest`, green over the whole source tree including
the system prompt.

---

## Appendix A — Monday Demonstration Script

Target 8–10 minutes. Lead with the method, not the code.

| # | Action | What it proves |
|---|---|---|
| 1 | "We followed the spec-first process from Lecture 3." Show `/specs/` — four documents. | Method, not just output |
| 2 | Open Spec 1 §2.3 — domain layer pattern selected per bounded context | Read and applied L3, did not just recite it |
| 3 | Show `dev-agent-prompt.md` — Role & Persona plus four-phase pipeline | Agentic Coding pillar |
| 4 | Show the Phase 1 inconsistency report and what it caught | Spec-first has real value; also Section 5 evidence |
| 5 | Show the generated tests and that they were written before implementation | Continuous Auto-Validation pillar |
| 6 | `docker compose up`, start four services | Microservice architecture is real |
| 7 | POST a batch: 50 units, expires tomorrow 22:00 | FR1, batch-level modelling |
| 8 | Start the simulator; refresh the velocity endpoint twice | **FR12 — stream processing is live** |
| 9 | Switch to `--slow`; velocity falls; risk event fires | FR13 |
| 10 | Show the agent trace — the ordered tool calls | **FR14 — agentic AI, auditable** |
| 11 | **Submit a 60% markdown by hand → 422 rejection** | **Anti-Corruption Layer, visibly** |
| 12 | Scan again — the discounted price applies | The loop closes |
| 13 | Show `/docs/ai-log/` | Report Section 5 evidence is already accumulating |

---

## Appendix B — Questions for Dr. Su

Ask about specific decisions. Do not ask "are we doing this right?"

**Architecture**

1. We select the domain layer implementation pattern per bounded context — Domain Model
   for Inventory and Pricing, Transaction Script for Sales, read model only for Insight.
   Does this match your expectation, or would you prefer all four contexts demonstrate
   the full set of DDD building blocks?
2. Does the Insight service count towards the four required bounded contexts, or do you
   expect four transactional business contexts plus a separate analytics service?
3. Is the Kafka Streams DSL sufficient for the "SQL-like language" requirement in the
   project specification, or do you want ksqlDB?
4. Is Kafka in KRaft mode (no ZooKeeper) acceptable?

**AI**

5. We use Claude Code / Codex as the coding agent rather than Antigravity or Copilot.
   Is that acceptable, and how would you like it attributed in the report?
6. Our LLM runtime is _[X]_, chosen because small local models proved unreliable at tool
   calling. Do you have a recommendation?
7. What granularity do you expect in the AI collaboration log? We currently record one
   entry per agent session.

**Scope**

8. Is 14 functional requirements across four services appropriate for a three-member group?
9. Would `testcontainers` or `springdoc-openapi` be approved if needed later?

**Process**

10. The lecture schedule lists "Project progress evaluation" in Week 7, while the project
    specification states the Progress Report is submitted in Week 8. Which applies?

---

## Appendix C — Role Assignment

| | Primary | Report sections |
|---|---|---|
| **Member 1** | insight-service — Kafka Streams topology, CQRS read models, LangChain4j agents and tools | §3.5, §3.6 |
| **Member 2** | inventory-service and pricing-service — domain models, aggregates, invariants, JPA mapping | §2.4, §3.3, §3.4 |
| **Member 3** | sales-service, POS simulator, Kafka and Docker infrastructure, OpenAPI contract, diagrams | §3.1, §3.2, Section 4 |

Member 1 owns Insight alone because it carries both the Kafka Streams and LangChain4j
learning curves. Splitting it would mean two people climbing the same curve.
