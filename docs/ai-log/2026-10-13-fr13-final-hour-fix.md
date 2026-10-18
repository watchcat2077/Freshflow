# 2026-10-13 — FR13: a batch in its final hour before expiry is still assessed (HALTED at step 2)

| | |
|---|---|
| Session | Tuesday 13 October 2026, afternoon |
| Prompt | `docs/prompts/2026-10-13-fr13-final-hour-fix.md` (team lead's authorisation) |
| Status | **Halted before the code change; team ruling 18 October 2026: not merged for submission (spec-05 G-8)** |

## 1. What was done

1. The prompt was saved verbatim to `docs/prompts/`.
2. The problem was confirmed in the code: `RiskAssessmentPolicy.assess` computes
   `hoursToExpiry = Duration.between(now, expiryAt).toHours()`, which truncates, projects sales over
   `max(0, hoursToExpiry)` and requires `hoursToExpiry > 0` for `atRisk`. A batch with 59 minutes
   left is therefore assessed as 0 hours, projected sales 0, and never at risk. spec-02 FR13 and
   spec-05 section 2.5 exclude only expired or depleted batches.
3. Four tests were added to `RiskAssessmentPolicyTest`, step 1 of the prompt:
   (a) `assessesABatchInItsFinalHour`: 397 units, 59 minutes, 120/h, expects projected sales 118,
   shortfall 279, at risk, `hoursToExpiry` 0;
   (b) `flagsAShortfallWithThirtyMinutesLeft`: 100 units, 30 minutes, 60/h, expects 30 sold, 70 short, at risk;
   (c) `doesNotFlagABatchExpiringNowOrEarlier`: expiry at `now` and one minute earlier, not at risk;
   (d) `projectsOverFractionalHours`: 40 units, 90 minutes, 2/h, expects projected sales 3.0 (1.5 hours), `hoursToExpiry` 1.
4. They were run before any code change. Three fail for the right reason, the projected sales
   under truncation: (a) `Expecting actual: 0.0` where 118.0 was expected (line 160); (b)
   `Expecting actual: 0.0` where 30.0 was expected (line 171); (d) `Expecting actual: 2.0` where
   3.0 was expected (line 198). Test (c) passes before and after: it is the boundary the fix must
   not move. `Tests run: 11, Failures: 3`.
5. Before step 2, the other tests that depend on the computation were read.
   `InsightTopologyTest` (added this morning) asserts the old truncation in two places, and says
   so in its own words:
   - line 89, the fixture comment: "With now inside the first minute, hoursToExpiry truncates to 2";
   - `riskDetected_firesExactlyOnce_onTheTransitionIntoRisk`: `event.projectedShortfall()` is
     `380.0`, described as "500 remaining minus 60/h over 2 hours";
   - `riskDetected_staysSilent_forABatchThatWillClear`: `stored.view().projectedShortfall()` is
     `-110.0`, described as "10 remaining minus 60/h over 2 hours".
   With the fix, the remaining time at the first window's end is 2 h 59 min 45 s, so those two
   values become **321.0 and -169.0** if the remaining time is taken in whole minutes
   (`toMinutes()`, 179 min, as the prompt words it) or **320.25 and -169.75** if taken to the
   second. The `hoursToExpiry` assertion of `2L` stays true under rounding down.
   `BriefingToolRegistryTest` and `RiskApiIntegrationTest` construct `BatchRiskView` directly with a
   fixed `hoursToExpiry` of 10 and do not depend on the policy.
6. The CONSTRAINTS clause says: if an existing test asserts the old truncation, HALT and report
   instead of editing it. Halted. The four new tests stay in the working tree, uncommitted, with
   the saved prompt; `RiskAssessmentPolicy`, spec-02 and spec-05 are unchanged.

## 2. What was assumed that the specifications did not state

- That "remaining time in minutes" means whole minutes (`Duration.toMinutes()`), so a batch with
  59 minutes and 30 seconds left projects over 59 minutes. The alternative, seconds divided by
  3600, changes the topology test's expected values by 0.75 units. Either is defensible; the
  ruling should say which.
- That `hoursToExpiry` in the view and the API stays the whole number of hours rounded down, as the
  prompt instructs and spec-04's `integer` requires, so a batch in its final hour reports
  `hoursToExpiry: 0` while being at risk. The README's and the report's description of the
  field ("hours to expiry") still reads correctly; the demonstration output will show 0 for the
  last hour rather than the batch vanishing from the list.

## 3. Deviations from the specifications

None yet. No source or specification line was changed.

## 4. Where a document was judged wrong (not acted on)

- `InsightTopologyTest`'s two shortfall expectations were derived from the truncating formula
  rather than from the requirement, and its fixture comment records the truncation as a fact of
  the system. They are the only obstacle to the fix. Updating them to the fractional-hour values
  is not a weakening of the test (the assertions stay exact and the scenario unchanged), but the
  prompt reserves that edit for the team, so it is reported here with the exact replacement
  values for a one-line ruling.
- spec-05 section 2.5's formula block states `hoursToExpiry = hours between now and expiryAt` and
  `atRisk ... AND hoursToExpiry > 0`, which describes the truncating code exactly and therefore
  contradicts the sentence two lines below it that excludes only expired and depleted batches.
  Step 3 of the prompt corrects it once step 2 is authorised.

## 5. Team ruling (18 October 2026)

The team does not merge the FR13 final-hour fix before submission. It would change two expected
values in `InsightTopologyTest` and the stated test totals on the day before submission, and the
demonstration is not affected: `scripts/demo.ps1` reads the risk list a few minutes after it
registers a batch that expires in 70 minutes. The problem is recorded as known limitation **G-8**
in spec-05 section 7, with a pointer from spec-02 FR13 and the README. The fix stays specified in
this log, with its four tests and the two replacement values for the topology test (321.0 and
-169.0 in whole minutes; 320.25 and -169.75 to the second).

Where the tests are kept: the four tests were removed from the working tree, not deleted. They are
in a git stash tagged `fr13-final-hour-tests (not merged, see G-8)` (stash commit `047e632b`), and
`RiskAssessmentPolicyTest.java` is back at HEAD, so the build has 207 tests. `git stash list` shows
the entry and `git stash apply` restores the four tests when the fix is taken up after submission.

Correction: the 13 October prompt and sections 1 and 4 of this log say "spec-05 section 2.5". The
formula block is in spec-05 section 3.2 ("The join and the assessment"). The prompt file is kept
verbatim; the references above are to be read as section 3.2.

Prompt: `docs/prompts/2026-10-18-fr13-ruling.md`.
