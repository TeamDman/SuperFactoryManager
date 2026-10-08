# CLI test runtime

Status: in progress, 2026-10-08. Starting commit: `05fa0d5a6`.

## Contract

Reduce the complete default test execution phase to under 60 seconds after
compilation. Retain machinery correctness coverage; do not conceal expensive
tests by silently skipping them. Keep dependency versions unchanged. Stop on
disk-space errors. No push.

## Work

- [x] Commit the validated enum refactor (`05fa0d5a6`).
- [x] Identify serialization: every harness uses one thread; the process pool
  permits overlap only for three retired module names.
- [x] Profile fixture construction and candidate verification, using tracing.
- [x] Replace candidate blob/index subprocesses with gix (18 launches removed
  per verification). Remaining native status probes retain their semantics.
- [x] Reduce oversized environment-isolation fixture to two empty Git repos;
  cache candidate seed bytes while copying independent files/indexes per test.
- [x] Enable measured concurrency without unbounded process/memory growth.
- [ ] Run the complete post-compilation suite and record time and failures;
  iterate on remaining costs until the target is met.
- [x] Run the remaining Rust checks, install and commit this improvement batch.

## Baseline

The previous green run had 1,677 library entries and 70 integration tests.
The CLI shard alone took 549.83 seconds (343 tests), JAR-build 270.01 seconds,
and the dedicated historical promotion fixture 282.63 seconds. These timings
exclude compilation. One representative package test took 4.45 seconds.

Git environment isolation prevents inherited repository/index variables from
redirecting commands. Preserve that boundary while replacing its unnecessarily
large fixtures. The ten-target candidate fixture generates tiny synthetic
projects, but repeated generation and process launches multiply across callers.

## Measurements and next gate

Tracy captures summarized with teamy-profiler measured candidate blob reads at
735.886 ms before, 21.657 ms after gix (five reads). The same cold fixture/verify
test fell from 2.10 s to 1.38 s, before seed reuse. Structured logs were captured.

The first 4-process/8-thread run passed the candidate shard (22 tests, 11.07 s)
but exposed a Windows loopback fixture bug: accepted sockets inherited
nonblocking mode. Explicitly restore blocking mode before applying timeouts.
No production socket behavior changed. The full gate is not yet green.

An empty all-feature harness took 4.086 s outside the runner. Consolidate the
full library into one process using the total thread budget; then run the four
integration binaries concurrently. Keep focused module selectors, exact test-list
validation, process cleanup and disk-error stops. Record process wall time and
write a machine-readable timings report. A live observation of the consolidated
library was about 501 MiB; post-exit .NET peak-working-set readings returned zero
and are deliberately not reported as a valid memory measurement.

The corrected consolidated run passed: 1,660 library tests, 19 explicit ignored
tests, and all 70 integration tests. Execution took 294.773 seconds: 262.380 s
library, followed by concurrent integrations whose longest was Java-analysis at
32.110 s. This improves the previous roughly half-hour gate, but **does not meet
the under-60-second target**. The first failed concurrent run is superseded by
this corrected green run. Sixteen planner cases and process-pool lifecycle,
stream, queue-stop and synthetic disk-error cases passed.

Next: profile remaining fixture-heavy library groups and the Java-analysis
integration independently. Reduce repeated native-build/legacy fixture setup
and remaining Git subprocess reads. Do not treat higher thread counts or moving
active contract tests out of the gate as sufficient completion.

Formatting, current-source all-feature Clippy, binary tests and doc tests also
passed. Validation was completed in stages after repairing the socket fixture;
it was not one uninterrupted check-all invocation. The initial all-feature
build passed. Checkpoint `83cb22118` contains the improvements; the default-feature
release executable was installed successfully after a 2m11s build. The installed
version reports that revision. Nothing was pushed. The one-minute performance
objective remains open.

The 282.63-second real-tag legacy promotion rehearsal is now explicitly opt-in:
it clones this repository and checks out the retired layout. Default synthetic
repair/immutable/bundled promotion tests remain. It is retained as historical
evidence, not counted as an optimized machinery test. To run it deliberately:

```powershell
cargo test --locked --offline --all-features --lib real_pinned_tag_inputs_flow -- --ignored
```

Profiling-only fixture (run alone with a Tracy capture connected and
`SFM_ENABLE_TRACY_LAYER=1`; optional `SFM_TEST_LOG_FILE` writes NDJSON):

```powershell
cargo test --locked --offline --all-features --lib profile_candidate_workflow -- --ignored --nocapture
```
