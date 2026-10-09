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

The accumulated batch passed the complete `check-all.ps1` gate: formatting,
production Clippy, all-feature/default builds, 1,668 active library tests,
70 integration tests, and binary/doc harnesses. Test execution was **101.782 s**;
the immutable Forge input sharing does not establish a whole-suite speedup.
Checkpoint this batch and refresh the installed CLI. Continue on release
preflight's remaining HEAD/index/tag subprocesses; the under-minute goal is open.

The staged-index full run passes 1,668 library and 70 integration tests in
**100.921 s**; it does not establish a total-time improvement over 97.031 s.
A fresh per-test diagnostic run passes in 90.411 s and now puts named Forge JAR
matrix (43.247 s), release Modrinth loader policy (41.176 s), local tag preflight
(40.017 s), and catalog twenty-context matrix (38.169 s) at the top under load.
Named Forge fixtures now parse their immutable authored catalog/metadata once
per process; each writable temporary project remains independently constructed.
All 18 named-Forge tests pass in 6.36 s. Validate and checkpoint this accumulated
batch next; then profile/reduce the release-preflight subprocess work. Do not
infer a whole-suite speedup from the focused fixture result.

Promotion HEAD/blob gix reads reduce the normal complete test phase to
**97.031 s**, all 1,667 active library and 70 integration tests passing.
The subsequent staged-index replacement reads fresh index entries and HEAD
tree entries at every checkpoint, comparing path/mode/object IDs without
subprocesses. It preserves scope filtering and ignores intent-to-add placeholders
like native `git diff --cached`; a direct Git parity regression proves those
cases. All 49 promotion tests pass in 9.07 s, including transaction races and
rollback. Full-suite timing for the index change is pending; dependencies and
the default 32-thread budget are unchanged.

The capture-fix normal test phase passes all 1,667 active library and 70
integration tests in **111.305 s**. The next change replaces promotion's
per-checkpoint HEAD and committed-file subprocess reads with isolated gix and
the existing bounded oracle reader. It retains every checkpoint and the native
index comparison. Promotion tests, including concurrent HEAD/index edits and
rollback, must pass before measuring the next complete suite. This change was
made after the preceding gate compiled its test executable and is not covered
by that 111.305-second result.
All 48 promotion tests pass in 11.59 s, including concurrent HEAD changes,
staged edits, path replacement and rollback. Current-source full validation
and a new complete timing remain pending before checkpoint/install.

Current completed normal gate: **112.742 s**, 1,666 active library tests and
70 integration tests pass (22 explicitly ignored). This includes the frozen
authoring gix readers and smaller catalog fixtures. All check-all stages passed.

The 128-thread reproduction now proves the capture failure is a deadline:
both the copied-review assertion and direct capture freshness test reported
`working-tree observation exceeded its deadline`. Discovery succeeded for the
copied review; its five-second fallback was not responsible for this occurrence.
The library finished in 112.64 s with 1,664 passing and two failing tests.
Do not describe this as proven shared-state corruption or suppress the timeout.

Repeated capture HEAD/pinned-object resolution now uses isolated gix reads,
retaining the 20-second freshness deadline and double-observation checks.
Arbitrary user revision expressions retain the native parser. Seven focused
capture tests pass in 4.43 s, including Git-result parity and deterministic
expired-budget rejection. The 128-thread stress rerun passes all 1,667 active
library tests in 100.45 s (22 ignored), with the same deadlines. This is one
successful stress run, not proof of immunity to arbitrary machine load. The
under-minute objective remains unmet; the normal 32-thread default is unchanged.

Latest completed gate: 137.151 seconds after compilation, with 1,664 active
library tests and all 70 integration tests passing. The staging HEAD/index
subprocess replacement preserves all 11 focused staging tests, including a new
index-flag mutation regression, but did not improve full-suite time. Java
scenario snapshot policy now uses gix without Git subprocesses (15 tests pass).

Current focus: generated-project JDK resolution ignored the thread-scoped
scenario fixture although branch resolution honoured it. The fix and regression
passed focused validation. The regression supplies no real project or SDK and
resolves only the scoped source tree without creating SDK/source caches (0.02 s).
All five generated-project symbol tests pass in 0.09 s in isolation. The initial
new regression used the wrong synthetic JDK layout; adding its required
`java.base/` module directory corrected that fixture. Measure the complete suite;
do not claim the one-minute goal from these isolated results.

The corrected-fixture complete test phase passed in **125.786 s** (1,665 active
library tests, 21 explicitly ignored, all 70 integration tests). Library process
wall time was 116.513 s and Java integration 8.963 s. This removes accidental host
JDK work but is not the under-minute solution. The next investigation must focus
on the remaining full-suite bottlenecks, especially repeated status/fixture work.

The frozen-matrix fixture now builds one immutable repository seed per process
and copies bytes into separate temporary repositories. Its isolation regression
proves source/index mutation in one copy does not affect another. Seven matrix
tests pass in 5.04 s and all eleven staging tests in 12.05 s (isolated runs).
Current-source production Clippy passed before this test-only seed change.
The seed change still needs a full-suite measurement; no under-minute claim.

The follow-up library timing run passes 1,666 tests in 111.269 s. Staging remains
the largest individual cost (55.376 s under contention). Enabling gix's status
feature needs new transitive lockfile entries and has been requested separately;
approval is pending. Meanwhile release-source tree enumeration now uses the
existing gix oracle reader instead of `git ls-tree`. All ten release-baseline
tests pass in 2.21 s, including symlink rejection (the assertion now names the
shared reader's diagnostic). This last change awaits broad validation.

The eight-library-thread experiment passed the complete suite in 145.534 s
(library 136.42 s), slower than 32 threads. Keep the existing 32-thread default;
neither the eight-thread nor 128-thread experiment justifies a scheduling change.
This full run also validates the gix release-tree replacement. Seven single-target
catalog contract fixtures were then narrowed to their actual 1.19.2 target;
all 19 catalog tests pass in 6.42 s, including the unchanged twenty-context matrix
tests. These final fixture reductions still need whole-suite measurement.

Tagged Gradle inventory now also uses the shared gix oracle reader; the obsolete
`ls-tree` output parser was removed. Ten release-baseline tests pass in 2.22 s
and seven frozen-matrix tests in 4.86 s. An experimental full test run uses
process-local `CARGO_PROFILE_TEST_OPT_LEVEL=1`, `DEBUG_ASSERTIONS=true` and
`OVERFLOW_CHECKS=true` (the latter two with the same `CARGO_PROFILE_TEST_` prefix).
No default profile was changed. Compare complete coverage, correctness and
post-compilation time before deciding whether this compilation/runtime tradeoff
belongs in the normal test configuration. Dependency versions remain unchanged.

The opt-level-1 experiment passed all 1,666 active library and 70 integration
tests in 99.773 s after a 5m34s build. It does not meet the target; the default
profile remains unchanged. Next fixture cleanup removes three Git processes per
working-tree-review fixture: gix initializes the repository, and the test helper
passes its synthetic author identity as command configuration instead of writing
it with two extra subprocesses. All six focused working-tree review tests pass
in 6.05 s under the unchanged default profile. The accumulated gix tree-reading
and fixture-sizing changes now need the complete normal validation gate.

Staging Tracy capture identified a missed subprocess loop inside frozen-authoring
preview: each target used `ls-tree` and `cat-file --batch`. The outer fifteen
staging Git queries cost only 0.708 s of an 8.08 s isolated test. Replacing the
preview tree/blob readers with gix reduced the identical capture to 2.87 s;
outer Git queries remain about 0.720 s. All eleven frozen-authoring tests pass
(3.32 s), including replacement-ref and inherited-environment isolation. The
profiling-only duplicate is explicitly ignored; the original test remains active.
Full-gate validation follows; the preceding gate stopped on an implicit-clone
Clippy error in the Gradle inventory conversion, now corrected.

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

## Follow-up investigation

Per-test JSON timings (diagnostic libtest invocation, not a compiler change)
showed the 32-thread library baseline at 246.891 s. The longest cases repeatedly
prepare ten or twenty contexts, rather than testing one small operation. The
first follow-up passed all 1,660 active library tests in 234.834 s; the legacy
ten-root candidate test alone took 234.565 s. This is not the final measurement.

The all-feature library previously imposed Tracy's allocation profiler on
integration executables. Allocator selection now belongs to the CLI executable;
unit tests use the normal application's existing mimalloc allocator. Java
snapshot policy checks now read the Git index once with gix and batch ignore
queries into one native Git call, instead of four processes per scenario.
All 15 Java-analysis tests passed in 21.07 s, versus 32.110 s previously, although
the follow-up overlapped the library run and is not an isolated comparison.

A focused Tracy capture of frozen-recipe preparation took 2.25 s. It exposed
22 metadata validations (374.697 ms self time), four parses (366.106 ms), three
project rechecks (332.014 ms), and 328 selected-input reads. Fixture role updates
are now batched into one metadata validation/write. Production recheck boundaries
remain in place. Coarse spans and an explicitly ignored capture test retain a
repeatable way to measure this workflow.

Frozen replay was still launching three Git processes per file. It now opens
one isolated gix reader per replay, retaining commit/path, file mode, object ID,
128 MiB size bound and SHA-256 checks. Root/commit validation and release-tag
preflight blob reads also use gix. A regression checks pinned reads despite
replacement refs and verifies mode rejection. These latest changes are awaiting
the full gate; do not treat compilation or earlier runs as their acceptance.

The subsequent full gate's test phase passed in **158.700 s**: 1,661 active
library tests (148.74 s), 20 explicitly ignored tests, and all 70 integration
tests. Java analysis took 9.04 s without library contention. The extra active
test covers the gix reader; the extra ignored test is a profiling-only duplicate.
Formatting, Clippy, all-feature build, binary and doc tests also passed. The
default-feature rebuild passed. The under-60-second objective remains
open; the next target is repeated fixture input acquisition and validation,
not more processes or less correctness coverage.

The follow-up focused capture confirms fixture metadata validations fell from
22 to 9. It took 2.55 s while the default-feature build was running, so this is
operation-count evidence, not a clean latency comparison. Large real metadata
parses and repeated selected-file checks still dominate its remaining work.

Checkpoint `31def7adf` commits the green 158.700 s gate and is installed on PATH.
Nothing was pushed. The next test-only pass narrowed role acquisition to requested
inputs, added single-target fixtures, used gix for fixture initialization, and
parallelized two independent context matrices without removing aggregate checks.
All 1,661 library tests passed, but execution remained 153.015 s. This did not
improve the overall time meaningfully, so it is not the performance solution.

The new ranking identified a production bottleneck missed by the initial capture:
`catalog_projection_root` repeats native Git worktree, index and ignore queries
on every retained-project recheck. The next change replaces these with gix while
retaining system/user ignore configuration and excluding environment-based Git
redirection. Tests compare ignore results to native Git, including negation,
deleted-but-indexed ignore files, info/exclude and configured excludes. This
change is still under validation. The existing dependency feature set already
includes gix excludes; no dependency or lockfile change is required.

The gix policy replacement passed nine focused tests in 0.60 s. The focused
preparation capture fell to 1.21 s (seven metadata validations, three parses).
The next complete test phase passed in **123.576 s**: 1,663 active library tests
(113.22 s), 20 explicit ignored tests, and all 70 integration tests. Formatting,
Clippy, all-feature build, binary and doc tests passed. This is another real
reduction, but still above the objective. A live sample from the preceding run
showed 292 CPU-seconds over 60 elapsed seconds at 32 test threads; evaluate a
higher bounded concurrency budget after the current default rebuild completes.

The default rebuild passed. Increasing concurrency to 128 test threads made the
library slower (126.99 s) and exposed one unavailable review-capture result.
Its diagnostic was missing, so timeout is a hypothesis, not a confirmed cause.
The same test passed alone in 4.69 s; the assertion now prints its diagnostic.
Keep the green 32-thread configuration. A direct Tracy capture of the six-target
native preparation matrix took 4.88 s alone: 60 catalog rechecks consumed 1.735 s
of self time, while 30 native profile selections consumed 0.443 s. Contention
and repeated fixture/process work remain targets; do not infer that profile
parsing dominates from the earlier contended matrix duration.

Checkpoint `2707098d5` commits the gix policy and fixture improvements. A fresh
32-thread diagnostic run passed all 1,663 active library tests in 131.606 s
(21 ignored, including the new profiling-only duplicate). The slowest current
test is frozen-preset staging at 69.31 s under contention, followed by generated
symbol selectors at 61.50 s. Staging repeatedly starts Git for HEAD, index flags
and status during its ten-file write transaction. Replace redundant subprocess
queries without weakening its concurrent-edit checks; this is the next concrete
target. The latest full gate remains 123.576 s, not an under-minute result.

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
