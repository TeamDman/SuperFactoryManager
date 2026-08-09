# No-Gradle Run Profiling Plan

This plan tracks the Tracy profiling pass for the Rust-owned Minecraft toolchain in `platform/cli/sfm-propagate-changes`.

## Goal

Make the non-Gradle setup work performed before and around `sfm-propagate-changes run client|client-smoke|client-puppet|server|data|game-test-server` visible in Tracy captures. The immediate target is not optimization; it is repeatable measurement that can identify cache misses, slow Java tool invocations, unnecessary rebuilds, artifact resolution delays, and launch setup costs on successive stochastic runs.

## Completion Criteria

- `platform/cli/sfm-propagate-changes/run-profiler.ps1` can build a Tracy-enabled CLI, run a selected SFM command, and save a `.tracy` capture.
- `--dry-run` is available on `jar build` and top-level `run` commands so profiling can stop before build execution or before the Minecraft JVM launch.
- The CLI has feature flags for:
  - `tracy`: compile in the Tracy tracing subscriber support.
  - `tracing_detailed`: enable noisier diagnostic spans that are useful only during profiling.
  - `tracy_memory`: enable Tracy allocation tracking through `tracy_client::ProfiledAllocator`.
- The Tracy subscriber is independent from `--log-filter`; when enabled it receives trace-level data even if console/file logging is filtered to info/debug.
- The run/build executor has enough spans and events to distinguish:
  - CLI command entry.
  - plan creation and lockfile/provenance handling.
  - Maven/Minecraft artifact resolution, including cached versus downloaded artifacts.
  - each Rust-owned build node.
  - Java tool invocations.
  - run classpath/source-root/assets setup.
  - run JVM argfile construction.
  - handoff into the launched Minecraft JVM.
  - game-test/client-puppet validation.
- A docs note records lessons that should be backported into `G:\Programming\Repos\teamy-rust-cli`.
- `G:\Programming\Repos\teamy-profiler` is documented as a future path for faster `.tracy` analysis when `tracy-csvexport.exe` is too slow.

## Reference Findings

- `G:\Programming\Repos\teamy-rust-cli` already has a simple `run-profiler.ps1`, a `tracy` feature, and `tracing-tracy` subscriber wiring.
- `G:\Programming\Repos\Teamy-Studio` has a newer `run-profiler.ps1` pattern:
  - builds a profiler-enabled binary first;
  - uses an environment variable to enable the Tracy layer at runtime;
  - waits for `tracy-capture.exe` to finish writing before opening/exporting;
  - supports a no-open-profiler mode for non-interactive capture generation.
- The local skill `G:\Programming\Repos\skills\.github\skills\add-tracy-spans` recommends coarse always-on spans first, and gating high-volume inner spans behind a profiling feature.
- `tracy-client` exposes `ProfiledAllocator`, which lets `tracy_memory` be a real feature flag instead of a placeholder.
- `G:\Programming\Repos\teamy-profiler` contains a native `.tracy` CPU-zone summary exporter that may be useful later when captures are too large for `tracy-csvexport.exe`.

## Implementation Plan

1. Add SFM Cargo features and dependencies for Tracy capture.
2. Update logging initialization so stderr/json filters are per-layer and the Tracy layer is not constrained by `--log-filter`.
3. Add a gated global allocator for `tracy_memory`.
4. Add `run-profiler.ps1` beside `check-all.ps1`.
5. Add coarse spans around `jar_build::engine` planning, build execution, resolver, Java tools, run setup, and launch validation.
6. Add detailed spans behind `tracing_detailed` around repeated artifact and classpath/resource collection loops only where useful.
7. Run formatting, clippy, tests, and at least a no-open-profiler smoke invocation if local Tracy tools are available.

## Progress

- 2026-06-13: Created this tracking plan after reviewing SFM CLI logging, Teamy template profiling support, Teamy-Studio's newer profiler wrapper, the local Tracy span skill, and `teamy-profiler`.
- 2026-06-13: Added SFM Tracy features, runtime Tracy-layer enabling through `SFM_ENABLE_TRACY_LAYER`, optional Tracy allocation profiling, `run-profiler.ps1`, and generated capture ignore rules.
- 2026-06-13: Added coarse spans/events around command entry, plan creation, resolver/artifact decisions, downloads, build node execution, Java tool calls, run classpath/assets setup, run setup completion, Minecraft JVM launch, packaging, and lockfile writes. Per-dependency and download-request detail is gated behind `tracing_detailed`.
- 2026-06-13: Verified `cargo check --all-features`, `cargo clippy --all-features -- -D warnings`, `cargo test --all-features`, and `platform/cli/sfm-propagate-changes/check-all.ps1`.
- 2026-06-13: Ran `platform/cli/sfm-propagate-changes/run-profiler.ps1 -NoOpenProfiler jar plan --branch 1.19.2`. It produced `platform/cli/sfm-propagate-changes/tracy/2026-06-13_23-49-54.tracy`.
- 2026-06-13: Ran `platform/cli/sfm-propagate-changes/run-profiler.ps1 -NoOpenProfiler run game-test-server --branch 1.19.2`. It produced `platform/cli/sfm-propagate-changes/tracy/2026-06-13_23-51-46.tracy` and validated 219 required game tests passed.
- 2026-06-14: Added `--dry-run` to shared jar build options. `jar build --dry-run` resolves the plan and lockfile without executing build nodes; `run ... --dry-run` builds and prepares launch files, then skips the Minecraft JVM. The profiler wrapper now defaults to `run game-test-server --branch 1.19.2 --dry-run`.

## First Capture Observation

The `jar plan --branch 1.19.2` smoke capture proved the harness and showed immediately useful data:

- `sfm_jar_build_command` took about 2.6 seconds in the captured command.
- `create_build_plan` took about 0.73 seconds.
- `write_artifact_lockfile` took about 1.86 seconds and dominated the plan command.
- Resolver spans showed individual cached artifact lookups, with `tool-access-transformers` around 166 ms and several Forge/MCP artifacts in the 18-60 ms range.

This is not an optimization pass, but the result suggests lockfile/provenance serialization and artifact-state collection should be one of the first later investigations.

The `run game-test-server --branch 1.19.2` capture proved the run setup path and showed these coarse timings from the console/csv summary:

- Rust-owned build replay took about 31 seconds before run setup.
- MCPConfig joined and Forge userdev nodes were cache hits at about 62 ms and 229 ms.
- Dependency deobf took about 6.1 seconds even with caches present.
- Project compile took about 18.2 seconds, including about 10.4 seconds for main javac and 5.5 seconds for gametest javac.
- Packaging/reobf took about 6.5 seconds.
- The launched Minecraft JVM span was separate and took about 82.4 seconds; that is game runtime, not Rust setup.

The next profiling pass should run the same command twice and compare the same spans, with special attention to dependency deobf, project compile, packaging/reobf, and lockfile writing.
