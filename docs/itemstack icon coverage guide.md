# Repository icon coverage loop

From the **1.19.2 repository root**, using PowerShell 7:

```powershell
./scripts/analyze-itemstack-icon-coverage.ps1 -Run before
```

This compiles current Java and runs the coverage tests without launching the
game. It scans the whole worktree, including ignored build outputs and caches,
not only `platform/minecraft/run`. It does not follow links or read file contents.
`.git` entries and the report directory are excluded and counted. Scan errors or
the 250,000-entry cap fail the test and leave an explicitly incomplete report.

Reports are under `platform/minecraft/build/itemstack-icon-coverage/before/`:

- `summary.json`: separate file/directory coverage counts, requested-icon counts,
  exclusions/errors, and uncovered groups ranked by count with example paths.
- `entries.ndjson`: one result per inspected path, including kind, requested
  item, winning rule, predicate, and why it did or did not count as covered.

Paper, chest and barrel remain generic even when an extension rule explicitly
chooses them. A changed generic fallback (for example every file → apple) does
not count either. Ambiguity, unavailable facts and missing rules are uncovered.
This measures requested default icons, **not** whether an ItemStack renders in
the current title-screen/world context. User theme files are not loaded or edited.

## Improve the defaults

1. Inspect the largest uncovered groups and their examples. Generated artifacts
   can dominate totals; directory coverage is reported separately for this reason.
2. Add a meaningful suffix default in
   `platform/minecraft/src/main/java/ca/teamdman/sfm/client/theme/SFMClientTheme.java`,
   or a name-and-kind predicate in
   `platform/minecraft/src/main/java/ca/teamdman/sfm/client/theme/preview/SFMItemstackPreviewDefaultNames.java`.
3. Add an example and precedence assertion to `SFMItemstackPreviewCoverageTests`.
   Defaults must remain below explicit user preferences. Name predicates on
   files also include the appropriate suffix/extensionless guard so they are
   provably more specific than the default file rule, not an ambiguous peer.
4. Run again with a separate label:

```powershell
./scripts/analyze-itemstack-icon-coverage.ps1 -Run after
```

Compare both summaries. Live files can be added/removed by compilation or other
processes, so compare denominators as well as percentages. Reports retain actual
paths; the scan is not an atomic filesystem snapshot. Reusing a label replaces
that label's generated report, not source files or review documents.

Examples of intended meaning: compiled objects → brick, Java classes → nether
brick, native libraries → netherite ingot, debug symbols → spyglass, timestamps →
clock, source → crafting table, tests → target, review → bell, explorer → compass,
recipes → knowledge book. Unknown names deliberately remain gaps.

The script accepts `-Tool <path-to-sfm-propagate-changes.exe>` if it is not on PATH.
No Rust installer update is needed for these Java-only defaults and tests.
