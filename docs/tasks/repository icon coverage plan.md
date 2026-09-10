# Repository ItemStack icon coverage

**Status:** Complete · **Root:** `D:/Repos/Minecraft/SFM/repos2/1.19.2`
**Updated:** 2026-09-07 · **Focus:** Ready for further rule iterations
**Intent audit:** Passed against the repository-wide coverage request.

Use `[ ]` pending, `[~]` active, `[x]` verified; evidence belongs beside its task.
This scoped iteration does not complete the unfinished split-diff/navigation
runtime work in `explorer navigation and review diff presentation plan.md`.

## Requirements and three-pass audit

| ID | User atom | Implementation/proof |
| --- | --- | --- |
| IC-U1 | Test each file/directory beneath the repo root, not just Minecraft run. | IC-1 real filesystem audit and fixture tests |
| IC-U2 | Coverage means more specific than generic chest directories/paper files. | IC-1 classification, separate uncovered groups and ambiguity |
| IC-U3 | Easy analysis → rule → analysis loop, with many sensible agent-authored icons. | IC-2 script, baseline/after reports, default rules and rationale |

Extraction retained the exact root correction and both generic icons.
Traceability maps each atom to implementation and proof above.
Adversarial review: don't count a paper-producing suffix as custom coverage;
don't hide build artifacts from the denominator; don't invent 100% coverage by
assigning one arbitrary icon to everything. Report files/directories separately.
No unavailable earlier discussion is required for these three requirements.

## Decisions and boundaries

Use the production Java rule resolver, not a separate PowerShell imitation.
Scan all physical descendants, including ignored/generated files; exclude `.git`
internals, links/reparse-point traversal, and the report output directory.
Read metadata only. Report exclusions, errors and the entry cap explicitly;
incomplete scans fail rather than claiming whole-root coverage. Requested icon
coverage is not GPU/renderability proof. Preserve user overrides and lockfiles.
Default-rule tests are repeatable without Minecraft; real-tree audit is opt-in.

### [x] IC-1 Add reusable classification and repository audit

**Evidence:** `SFMItemstackPreviewCoverage` classifies the real production
decision; `SFMItemstackPreviewThemeResolver.prepare` reuses immutable lowering
without caching path-dependent decisions. `SFMItemstackPreviewCoverageTests`
proves generic icons/rules, ambiguity, named/suffix specificity, user overrides
and scans physical paths. Baseline real scan: 94,087 files (24,489 covered),
9,921 directories (21 covered), no errors or cap hit. Exact evidence is in
`platform/minecraft/build/itemstack-icon-coverage/baseline/entries.ndjson` and
`summary.json`. The first wrapper display failed on case-distinct directory
keys (`mekanism`/`Mekanism`); fixed using PowerShell 7 case-preserving hashtable
JSON parsing. The next wrapper run completed successfully with all four tests.

Prepare the production resolver once, classify every inspected path, emit JSON
with exact path/kind/item/winning predicate/status plus summary and uncovered
name/extension groups. Fixture tests must cover generic icons, explicit rules,
ambiguity and user authority. Real audit must assert complete traversal.

### [x] IC-2 Iterate defaults and document one-command use

**Implementation/evidence:** Added semantic named-container and exact filename
rules in `SFMItemstackPreviewDefaultNames`; name-and-kind guards preserve
specificity and user authority. Added compiled-code, native-library, debug,
web-source and build-metadata suffix defaults in `SFMClientTheme`. The after
pass covered 86,314 / 94,089 files (91.74%) and 1,619 / 9,921 directories (16.32%),
versus baseline 26.03% / 0.21%. Requested item variety rose from 22 to 56.
Both scans completed; source generation added two files between passes.
Unknown/extensionless and ordinary paper text remain explicit gaps.

Four focused tests passed. The first full-suite pass had 1,940 successes, one
intentional expectation mismatch (`src` now crafting table, not chest), and
three opt-in aborts. Updated that assertion and retained barrel fallbacks for
named containers. Final full-suite plus real-root audit is recorded in
`platform/minecraft/build/itemstack-icon-coverage/full-suite-final.ndjson`.

**Final validation:** 1,942 passed, zero failed, two unrelated opt-in aborts
(installed symbol-worker integration and captured release-review scale test),
1,944 found. The real repository audit ran within this full suite and completed
without errors/cap truncation: **86,317 / 94,092 files**, **1,619 / 9,921 directories**.
The after report was refreshed against final source. All owned test processes
exited normally; no Minecraft client or OS input was used.

Guide: `docs/itemstack icon coverage guide.md`. Repeatable command:

```powershell
./scripts/analyze-itemstack-icon-coverage.ps1 -Run after
```

The changelog is updated. Rust/installed CLI unchanged (SHA-256
`C7259EB80429EBE5A4CDB335660165BE60752A7FF88D500E589349FD5C6ECCB0`), no
installer/Gradle/dependency/lockfile/review mutation. No new localized entries
were introduced, so existing generated localization does not need regeneration.

Run baseline, inspect uncovered groups, add explainable name/suffix rules,
rerun, record actual before/after counts and residual gaps. Add changelog and
guide. Run the full Java suite; no Rust edits/install, no game/OS input needed.

## Acceptance

- [x] One command audits this repo root using actual production rules.
- [x] Both exact per-entry evidence and actionable uncovered groups exist.
- [x] Baseline/after reports show measured improvement; no blanket rule hides gaps.
- [x] Fixture and full-suite results recorded; dependency graph unchanged.
