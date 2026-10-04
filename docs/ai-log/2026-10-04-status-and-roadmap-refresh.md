# 2026-10-04 — Status and roadmap refresh; diagrams removed pending the team's own UML

**Session type**: documentation and verification. No source file, test or POM changed.

## What was done

1. Re-verified the repository against the specification PDF, the subject outline, Dr Su's
   email of 17 September and the consultation notes of 22 September.
2. Ran `.\mvnw.cmd -o test` with `LLM_API_KEY` and `LLM_MODEL` unset: 64 tests, 0 failures,
   0 errors (inventory 17, sales 9, pricing 2, insight 36), `BUILD SUCCESS`.
3. Rewrote `docs/STATUS-2026-10-04.md` and `docs/ROADMAP-2026-10-04.md`.
4. Removed `docs/diagrams/` (the draw.io A2 architecture diagram) at Andy's instruction,
   because the team is drawing every diagram in a different UML tool. Recoverable with
   `git checkout d0e9b75 -- docs/diagrams/`.

## Corrections to the morning's documents

- **Week numbering.** The subject outline places the mid-session recess in the week of
  28 September. This week (5–9 Oct) is Week 10, not Week 11. Only the Week 9 minute
  (23 Sep) is missing; nothing was due in the recess week.
- **Dependencies.** The three artifacts Dr Su approved on 17 September are in no POM. The
  gap is that spec-01 §1.1 still calls validation "pending", not that an unapproved
  dependency is present.
- **"SQL-like" question.** Already answered in Dr Su's 17 September email ("you should use
  Kafka DSL to implement SQL-like analysis"). Removed from the Wednesday question list.
- **CQRS domain layer.** Dr Su's 22 September ruling is that a CQRS context has a domain
  layer; promoted from "optional refactor" to a Phase 2 task.
- **Report authorship.** The specification forbids an AI-generated report. The roadmap now
  separates Claude-built evidence packs from team-written prose.

## Assumed, not stated anywhere

- The tutor review on Wednesday 7 October is informal (the lab), not a marked submission.
- The Week 12 presentation date will be confirmed by the separate guidelines document.

## Nothing in the specifications was judged wrong in this session.
