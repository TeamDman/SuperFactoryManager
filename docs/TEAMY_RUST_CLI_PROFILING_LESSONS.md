# Teamy Rust CLI Profiling Lessons

This note records lessons from adding Tracy profiling to `sfm-propagate-changes` that should be considered when refreshing `G:\Programming\Repos\teamy-rust-cli`.

## Template Gaps To Backport Later

- Prefer `run-profiler.ps1` over `run-tracing.ps1` for the wrapper name.
- Build the target binary first, then run the compiled executable under `tracy-capture.exe`; this keeps Cargo build work separate from the profiled command.
- Use a runtime environment variable to enable the Tracy layer only during profiler runs. For SFM this is `SFM_ENABLE_TRACY_LAYER`; a template should use a generated-project-specific name.
- Keep Tracy independent from console/file log filters. The subscriber layer should not sit behind the same `EnvFilter` used for `--log-filter`.
- Include both normal and detailed profiling feature flags:
  - `tracy` for the subscriber.
  - `tracing_detailed` for high-volume spans.
  - `tracy_memory` for `tracy_client::ProfiledAllocator`.
- Add `-NoOpenProfiler` to the wrapper so automation can produce captures without opening the GUI.
- Wait for `tracy-capture.exe` to finish saving after the traced process exits. Force-close only after a timeout.
- Print a timing summary that separates build time, capture launch, traced command time, capture cleanup, profiler launch, and total wrapper time.

## SFM-Specific Choices Worth Generalizing

- The default profiled command is non-interactive: `run game-test-server --branch 1.19.2`.
- The wrapper accepts arbitrary remaining CLI arguments, so users can profile `run client-smoke`, `run client-puppet`, `jar build`, or `jar plan` without script edits.
- Coarse spans are always compiled in because they are useful for regular diagnostics too.
- Repeated or potentially high-volume spans are behind `tracing_detailed`.
- Cache decisions are emitted as events (`artifact cache hit`, `dependency_deobf cache miss`, `run_setup_complete`) so they are easy to find in Tracy messages and CSV exports.

## Future Analysis Tooling

`G:\Programming\Repos\teamy-profiler` is a local native `.tracy` analysis experiment. It can export top CPU zone summaries without relying on `tracy-csvexport.exe`, and should be revisited if SFM captures become too large or slow for Tracy's stock CSV exporter.
