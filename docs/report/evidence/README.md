# Live-run evidence, Sunday 4 October 2026 (19:44 to 19:48 AEDT)

Captured from one run of `scripts/demo.ps1 -Rate 4 -DurationSeconds 150` against the four
services, a fresh single-node Kafka (`docker compose up -d`) and Google Gemini through
LangChain4j (model name supplied from the environment, key never written anywhere). Nothing is
edited except line wrapping; timestamps are UTC inside the JSON and AEDT in the log excerpts.
Each `UC-*.json` holds the request that was sent and the response that came back.

| File | Use case | What it shows |
|---|---|---|
| `UC-01-post-batch-201.json`, `UC-01-400-quantity-not-positive.json` | UC-01, FR1 | A goods receipt accepted (201) and one refused for quantity 0 (400) |
| `UC-02-batch-after-fefo-deduction.json` | UC-02, FR2 | Remaining quantity 400 to 398 after an `item-sold` event |
| `UC-03-get-batch-by-id.json`, `UC-03-batches-by-sku.json` | UC-03, FR4 | A batch by id, and the active batches of a SKU in FEFO order |
| `UC-04-post-transaction-201.json` | UC-04, FR5 | A sale recorded at the base price |
| `UC-05-transaction-inside-markdown-window.json`, `UC-06-price-with-active-markdown.json` | UC-05, FR6a and FR6b | The strawberries sold at 4.20 with `markdownApplied: true` once the plan was active |
| `UC-06-put-price-rule-201.json`, `UC-06-get-price-rule-seeded.json` | UC-06, FR8 | A rule created by PUT, and the seeded rule |
| `UC-07-post-markdown-plan-201.json` | UC-07, FR9 | A human PERCENT_30 proposal activated, discounted price 4.20 |
| `UC-08-422-margin-floor-rejection.json` | UC-08, FR11 | PERCENT_60 refused with the resulting margin (-0.1667) and the floor (0.15) |
| `UC-08-agent-origin-plan-1.json`, `UC-08-agent-origin-plan-2.json` | UC-08, FR11 | The agent's own two PERCENT_50 recommendations refused by the Pricing domain and kept as REJECTED plans linked to their traces |
| `UC-09-velocity-at-detection.json`, `UC-09-velocity.json` | UC-09, FR12 | Rolling velocity, 180 units an hour against a baseline of 120 |
| `UC-10-risk-at-detection.json`, `UC-10-risks.json` | UC-10, FR13 | The yoghurt batch flagged with a projected shortfall of 207 units |
| `UC-11-agent-trace-1.json` to `-3.json` | UC-11, FR14 | Three real deliberations: PERCENT_50 proposed; NO_MARKDOWN after the velocity rose; PERCENT_50 proposed for the strawberries |
| `UC-12-post-briefing-201.json` | UC-12, FR15 | The briefing with three tool calls; the counts were computed by the orchestrator |
| `*-log-excerpt.txt` | all | The `[published]`, `[detected]`, `[fr14-trace]`, `[fr11-rejected]` and `[consumed]` lines from each service's console |
| `demo-run-console.txt` | all | The simulator's own lines from the run (the script's headings go to the host, so use `-TranscriptPath` next time to keep everything) |

## Added from the second live run, Monday 5 October 2026 (13:05 to 13:25 AEDT)

The same loop, run again on a fresh start of the four services. The first attempt used the model of
4 October and ran into the Gemini free tier's daily quota for it (twenty requests); the second used
`gemini-3.5-flash-lite`, the model the coordinator's own agent reference uses.

| File | Use case | What it shows |
|---|---|---|
| `UC-12-503-agent-unavailable-2026-10-05.json` | UC-12, FR15 | The provider's real 429 surfacing as 503 `AGENT_UNAVAILABLE` with a one-line reason: the behaviour added on 5 October, confirmed before the model was switched |
| `UC-12-post-briefing-201-flash-lite-2026-10-05.json` | UC-12, FR15 | A briefing on `gemini-3.5-flash-lite`: three tool calls, counts computed by the orchestrator |
| `UC-11-agent-trace-flash-lite-2026-10-05.json` | UC-11, FR14 | A deliberation on the same model: three tool calls, a PERCENT_50 proposal for a fresh batch |
| `UC-08-agent-origin-plan-flash-lite-MDP-*-2026-10-05.json` | UC-08, FR11 | The Pricing domain refusing today's two agent proposals on the seeded rule, kept as REJECTED and linked to their traces |
