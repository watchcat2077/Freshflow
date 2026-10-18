# Prompts

The prompts that started the larger agent sessions, kept verbatim so that a reader can see what the
agent was asked and compare it with what the matching entry in `docs/ai-log/` says it did. Day-to-day
sessions were driven by shorter instructions that are quoted inside their log entries rather than
kept here.

| Prompt | Session it started | Log entry |
|---|---|---|
| `2026-09-10-fr14-phase-a.md` | FR14 Markdown Decision Agent, Phase A: design, spec-06 and the failing tests, no implementation | `docs/ai-log/2026-09-10-fr14-phase-a.md` |
| `2026-10-13-fr13-final-hour-fix.md` | FR13 final-hour fix, test-first; halted at step 2 because an existing test asserts the old truncation | `docs/ai-log/2026-10-13-fr13-final-hour-fix.md` |
| `2026-10-18-fr13-ruling.md` | Team ruling: the fix is not merged for submission and is recorded as spec-05 G-8; documentation and working-tree clean-up only | `docs/ai-log/2026-10-13-fr13-final-hour-fix.md`, section 5 |

The standing instructions every session ran under are in `CLAUDE.md` at the repository root and in
`specs/dev-agent-prompt.md`, the four-phase pipeline.
