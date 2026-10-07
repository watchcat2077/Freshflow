# 2026-10-06 — Repository scope reduced to what the specification asks for

| | |
|---|---|
| Session | Tuesday 6 October 2026, 11:50, Week 10 |
| Driver | Team decision: keep in the repository only what the project specification requires; the report, the slides and the diagrams are the team's own deliverables and are not kept in the repository |
| Tool / model | Claude Code |
| Outcome | `docs/report`, `docs/diagrams`, `docs/minutes`, the status and roadmap documents removed from the repository and archived outside it; the live-run evidence kept at `docs/evidence/`; README and CLAUDE.md updated; nothing in `src/` changed |

## 1. What was done

The specification says the submitted zip holds "the source code, specification files, README file
and other artefacts (if any)", and Section 5.1 of the report must point at repository artefacts
such as specifications, prompts, design documents, source code, tests and agent workflows. Its
Generative AI clause (PDF edition) says the report and the presentation must be authored by the
team. The repository therefore keeps:

- the six Maven modules and their tests (193 green on 5 October), `pom.xml`, the Maven wrapper,
  `docker-compose.yml`, `.run/` and `scripts/`;
- `specs/` (spec-01 to spec-06 and `dev-agent-prompt.md`);
- `docs/decisions/` (ADR 0001 to 0006), `docs/ai-log/` (27 entries with this one), `docs/prompts/`,
  `RUNBOOK.md`, `CLAUDE.md`: the record of how the agent was used, which Section 5.1 cites;
- `docs/postman/` (the lightweight client the specification suggests) and `docs/evidence/` (requests
  and responses captured from the live runs of 4 and 5 October, one file per use case; moved from
  `docs/report/evidence/`).

Removed from the repository, and archived first in the parent folder's
`Group Document/repo-archive-2026-10-06/` with their paths preserved:

| Removed | Why | Where it lives now |
|---|---|---|
| `docs/report/` (report draft `.md` and `.docx`, use cases `.md` and `.docx`, `md2docx.py`, `presentation-week12.md`, `build_week12_deck.py`, the generated `.pptx`) | AI-written drafts; the report and the slides must be the team's own work and are submitted as separate files, not in the zip | archive, as reference material for the rewrite |
| `docs/diagrams/` (12 activity, domain model, two layered, database, event flow, context map, the generated architecture diagram, the draw.io A2, the Python generators) | the diagrams belong in the report; the team redraws them in UMLet from the code | archive; `.uxf` files open in UMLet 15.1 |
| `docs/minutes/` | minutes are submitted to Moodle weekly, not part of the zip | archive; the 7 October draft is there to be completed in the meeting |
| `docs/STATUS-2026-10-04.md`, `docs/ROADMAP-2026-10-04.md` | internal planning documents | archive |

Before the removal the check that mattered: the parent folder's `UML diagram/` copy no longer
existed and `Group Document/` held no copy of the report, so the repository was the only copy of
both. The archive was made first, and the removal second.

README changes: the embedded architecture image is gone, the use-case sentence no longer names a
repository file, the outputs sentence points at `docs/evidence/`, and the repository layout lists
`docs/evidence/` and `docs/postman/` under `docs/`. CLAUDE.md records the new scope.

## 2. What was assumed that the specification did not state

- That "other artefacts (if any)" means the artefacts Section 5.1 asks the report to reference, and
  not the report's own figures and prose. The evidence files are kept because they are outputs of the
  running system, not writing.

## 3. Deviations from the specifications

None in code or specifications. `specs/` is unchanged.

## 4. Judgements for the team

- Historical AI logs still cite `docs/report/...` and `docs/diagrams/...` paths. They are left as
  written: they describe the repository as it was on those dates, and the archive preserves those paths.
- The Week 12 deck and the report draft in the archive are reference material. The specification's
  Generative AI clause is explicit that the deliverables themselves must be the team's.

## 5. 7 October: five specification-versus-code mismatches, settled in the documents

A second read of the specifications against the code found five mismatches. None is a code
change; each is settled where the specification or the report says the wrong thing.

| Finding | Settlement |
|---|---|
| spec-01 §2.6 Table A listed insight-service as a consumer of `markdown-applied`; the service binds no such destination | Table A now lists `sales` only. The report's architecture figure must show the same |
| Table A listed `batch-written-off` and `markdown-expired` as if wired | Both rows marked out of scope (FR3, FR10 not built) |
| spec-05 §3.2 still placed `RiskAssessmentPolicy` in `application` | Corrected to `domain`, citing ADR 0006 |
| spec-03 called `PriceRule` an `<<Entity>>` while it has its own repository | Restated as a single-entity aggregate root, because a repository belongs to an aggregate (L4 slide 27); the report's 3.3 should say the same in one sentence |
| spec-03 described `GoodsReceipt` and `WriteOffRecord`, which were never built; `POST /api/batches` accepts `supplierRef` and does not persist it | Both marked out of scope in spec-03; README states that `supplierRef` is accepted and not stored |

One further observation needs no change: `RiskController` in insight-service's presentation layer
reads the domain type `BatchRiskView` to render it. The dependency rule of spec-01 §2.2 constrains
what the domain imports and keeps every dependency pointing inward; an outer layer reading an inner
type is within it, and the coordinator's `cargo-tracker-0.1` does the same in its `interfaces/rest`
assemblers. The report's 3.2 should say so in a sentence rather than leave a reader to wonder.

