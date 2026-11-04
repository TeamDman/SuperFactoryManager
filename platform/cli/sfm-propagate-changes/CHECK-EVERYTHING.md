# Resumable projection checks

From `platform/cli/sfm-propagate-changes`:

```powershell
./check-everything.ps1 -PlanOnly
./check-everything.ps1 -Projection 'sfm-dev/mc-1.20.*' -Stage Check,Compile
./check-everything.ps1 -Projection '1.21.1' -Stage Check -ReadWorkers 3
./check-everything.ps1 -Projection 'sfm-4.35.0-issue610-visual/mc-1.21.1' -Stage Check,Headless -HeadlessFilter 'sfm:circle_redstone'
```

With no selection flags, the command plans all registered catalog projections and all four stages: source check, native compile, create-only JAR, and headless GameTest server. Run `-PlanOnly` first to inspect the exact keys. A projection or Minecraft-version glob selects a subset. `-Stage` limits the operations; stages always run in dependency order. `-ReleasedRecipe` supplies an exact historical plain recipe for one selected plain projection. It never guesses one.

Read-only source checks use at most `-ReadWorkers` parallel workers (default 2). Compilation, packaging, and headless servers run one at a time. A global SFM orchestration lock prevents two invocations, even with different state directories, from racing. Existing CLI/cache locks still protect the underlying producers. A 2 GB D: free-space floor applies before mutable operations; override `-MinFreeDBytes` only after an operation-specific capacity assessment.

Receipts and attempt logs are kept under `sfm-toolchain-data/check-everything` by default, or `-StateRoot`. Every operation records source HEAD for provenance and keys reuse to the committed core tree, selected generated project sources, selected lock, catalog, project inputs, relevant dirty/untracked source content, CLI, worker, stage and selector identity. A documentation-only commit does not invalidate the receipt. A matching successful receipt is reused; failed or interrupted attempts are retained and retried on the next invocation after process preflight. Each invocation writes a summary JSON and prints passed, reused, failed, blocked, and downstream-skipped counts. Nonzero exit means at least one failure or safety block; inspect the named receipt and log. The command does not install a CLI, move caches, or delete historical outputs.

An occupied canonical JAR is reported as blocked because packaging is create-only. Preserve or explicitly relocate that generated JAR before a fresh package; the orchestrator will not overwrite it. Extended/visual projections with the `*` GameTest filter are blocked because some declare a manual recording test. Supply a bounded `-HeadlessFilter` for those projections. A historical expected-red suite remains a failure with its actual names in the receipt; compare it with its approved baseline outside this generic runner. Graphical/client tests and M6 retirement remain separate.
