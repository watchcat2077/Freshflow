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
