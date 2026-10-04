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
