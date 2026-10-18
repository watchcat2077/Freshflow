FreshFlow — fix: a batch in its final hour before expiry is still assessed for risk (FR13).

AUTHORISATION. Andy (team lead), 13 October 2026. Small, test-first domain fix. Every Hard
constraint and House rule in CLAUDE.md applies. Save this prompt verbatim as
docs/prompts/2026-10-13-fr13-final-hour-fix.md.

PROBLEM. insight-service/src/main/java/com/freshflow/insight/domain/RiskAssessmentPolicy.java
computes hoursToExpiry with Duration.toHours(), which truncates, and requires hoursToExpiry > 0.
A batch with 59 minutes left therefore counts as 0 hours and is never reported. spec-05 section
2.5 (lines 254-255) and spec-02 FR13 (lines 303-305) exclude only expired or depleted batches.
Observed live on 13 October: the demo batch dropped out of GET /api/insight/risks once less than
an hour remained.

CHANGE
1. First add failing tests to RiskAssessmentPolicyTest: (a) 59 minutes left, remaining 397,
   velocity 120/h -> at risk; (b) 30 minutes left with a shortfall -> at risk; (c) expiry now or
   in the past -> not at risk; (d) 90 minutes left -> projected sales use 1.5 hours, not 1.
   Show them failing for the right reason.
2. Then in RiskAssessmentPolicy compute the remaining time in minutes and use fractional hours
   (minutes / 60.0) for tradingHoursRemaining and projectedSales; exclude only when expiryAt is
   not after now. Keep BatchRiskView.hoursToExpiry an integer of whole hours, rounded down,
   because spec-04's RiskResponse declares it an integer.
3. Update the formula block in spec-05 section 2.5, and spec-02 FR13 if needed, so the spec and
   the code say the same thing.

CONSTRAINTS. No other behaviour change. The domain package imports only java.*. Never weaken,
skip or delete a test; if an existing test asserts the old truncation, HALT and report instead
of editing it.

VERIFY. Clean build and all tests: report the total (the previous total plus the new tests) and
0 failures.

COMMIT AND LOG. docs/ai-log/2026-10-13-fr13-final-hour-fix.md with the four report headings.
Commit: "fix(insight): a batch in its final hour is still assessed for risk". git push.

STOP. Print git log --oneline -3 and the per-module test counts.
