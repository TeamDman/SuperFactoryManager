# Native projection sync, build, and matrix

Use the current Rust CLI from a directory inside the SFM checkout. Until a qualified release is installed, invoke the checkout's `target/debug/sfm-propagate-changes.exe`.

```powershell
sfm-propagate-changes sync --projection 'sfm-dev/mc-1.20.1'
sfm-propagate-changes build --projection 'sfm-dev/mc-1.20.1'
sfm-propagate-changes matrix --projection '1.21.1' --plan-only
sfm-propagate-changes matrix --projection 'sfm-4.35.0-issue610-visual/mc-1.21.1' --stage check --stage headless --headless-filter 'sfm:circle_redstone'
```

`sync` only updates generated projection files through the existing guarded `source project sync` implementation. `build` requires current generated sources, incrementally compiles with the exact dependency lock, and packages a JAR. `matrix` runs the stricter optional check, compile, create-only JAR, and headless test stages. The previous `check-everything` spelling remains a matrix alias. Each command accepts a catalog-key glob, Minecraft version glob, or matrix ID such as `1.21.0`. Sync and build require an explicit selector; matrix defaults to the full catalog. `--plan-only` shows the selection without writing receipts. A custom `--stage` list narrows build or matrix work; sync is deliberately only generation.

The commands share one durable stage engine and a global orchestration lock. Successful stages have passing receipts keyed to relevant core and projection sources, catalog, exact dependency lock and JDK pin, current CLI binary, selected stage, and applicable test selector or released recipe. Sync receipts use the generated post-state identity; changed authoring inputs cause regeneration. A failed or interrupted stage has a separate retained attempt, never a passing receipt. Repeating the same command skips valid stages, retries failed or incomplete stages, and invalidates dependent stages after relevant edits. Sync and build stop at the first failure; matrix reports the remaining selected validation rows.

Receipts, logs, and run summaries live in `sfm-toolchain-data/check-everything-native`. Only matrix's read-only source checks run in parallel, capped by `--read-workers` (default 2); mutable stages run one at a time with the child CLI's producer and cache locks. A 2,000,000,000-byte free-space floor applies before compilation, packaging, or headless tests. It is a floor, not a cold-build peak estimate. No windowed Minecraft client launches. The old PowerShell runner's receipts remain history in `sfm-toolchain-data/check-everything`.

Packaging remains create-only. Before a new build or matrix package stage, an occupied canonical generated JAR is hash-recorded and moved to a same-volume `.pre-native-...` sibling; its restoration manifest is retained in `check-everything-native/preserved-jars`. The new package is then created at the canonical path. If packaging fails, the old JAR stays recoverable under that recorded sibling. Historical plain projections require one explicit `--released-recipe`; no recipe is inferred. Unbounded `*` headless selection is blocked for extended or visual projections because it can select manual tests. Full visual acceptance and permanent old-cache retirement are separate operations.
