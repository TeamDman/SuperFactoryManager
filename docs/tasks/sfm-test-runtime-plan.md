# CLI test runtime

Status: complete, 2026-10-09. Starting commit: `05fa0d5a6`.

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
- [x] Run the complete post-compilation suite and record time and failures;
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

### Follow-up Git subprocess audit

The next user-requested batch replaced 12 subprocess call sites with gix:
worktree discovery, merge-state and index-conflict reads, release-tag enumeration,
source revision and provenance reads, and historical fixture blob reads.
Native Git remains for status, mutation, transport, archive filtering and
revision-expression workflows. This is not a claim that every remaining read
has been converted. Tree/blob batch readers and review revision resolution still
merit further review; the current dependency feature set lacks gix revision parsing.

Four new tests cover packed tags, nested discovery, detached and unborn HEADs,
unique unquoted conflict paths, corrupt indexes, and linked-worktree merge state.
A truncated-index test exposed a panic in the pinned gix-index version; the
new conflict reader rejects undersized indexes before decoding.

The full follow-up gate passed 1,687 library and 106 integration tests in
**63.323 s**. This exceeds the earlier timing target; do not represent the
under-minute result as stable across runs. Formatting, Clippy, all-feature and
default-feature builds, and the binary/doc harnesses also passed. Dependencies
and existing ignored tests remain unchanged.

### Original goal completion measurements

Complete gate: **58.785 s**; independent complete repeat: **59.049 s**.
Both passed 1,683 library tests and 106 integration tests with no failures.
The 22 previously ignored entries remain unchanged. The integration count grew
by exposing the same 36 Java scenarios as individual harness tests, not by
adding or removing scenarios. Directory/registration equality and the shared
snapshot policy check guard coverage. Each scenario retains its private cache,
thread-local context and original assertions.

Commands: `check-all.ps1 -TestWorkers 4 -TestThreads 8`, followed by
`scripts/test-bounded.ps1 -Workers 4 -TestThreads 8`. These match this host's
default 32-thread library budget. Timings include the complete library and all
four integration binaries, excluding compilation and artifact discovery.
Binary/doc harnesses contain no tests. Formatting, Clippy, all-feature build,
binary/doc harnesses and the final default-feature build all passed. These are
measured results, not a guarantee under arbitrary machine load; the margin is
less than two seconds.

Final source checkpoint: `1fe07fe9c` on `main`. `install.ps1` completed using
locked, offline dependencies; the PATH executable reports revision `1fe07fe9c`.
Its SHA-256 is `8EFF6FEAC6B9BDA25B1E4F24816304D2CDCC0F0E05FF4EC4BF2A6680E34CC85B`.
User install required: no. All owned gate, test and installer processes exited
successfully; no game was started or stopped. No dependency declarations or
lockfiles changed, no new repositories were acquired, and nothing was pushed.

To repeat the measurement, run from `platform/cli/sfm-propagate-changes`:

```powershell
.\scripts\test-bounded.ps1 -Workers 4 -TestThreads 8
```

Expect all five test processes to pass. The runner prints the post-compilation
elapsed time and the location of its machine-readable timing receipt. Compilation
and artifact discovery are reported separately and excluded from that measurement.

Fixture profiling identified repeated directory creation and loose-object
copies. Private seed repositories are now packed once; each test still receives
independent files, index and refs. Deduplicated parent creation and avoiding
redundant Windows writable-attribute calls reduced eight materializations from
1.027 s to 0.656 s of traced self-time. The full gate after this was 65.246 s.
Parallelizing the formerly serial Java scenario loop then reduced its integration
phase from about 8.6 s to 4.17/4.29 s in the complete runs (2.16 s alone).

The preceding complete gate was **66.683 s**. This batch also replaces the remaining release
preflight committed-tree Git diff with gix object/mode comparison. It descends
only along the generated-root exclusion and preserves authored changes and mode
checks. Thirteen focused tests pass, including added/deleted generated trees,
authored changes and file/directory replacement. Full formatting, Clippy, build,
binary/doc tests and default-feature build pass.

A 64-library-thread experiment on the preceding checkpoint passed at 69.666 s,
slower than the 32-thread baseline; defaults remain unchanged.

Installed checkpoint `507ead6eb`: **68.343 s**, 1,682 active library tests and 70 integration
tests, all passing (library process 59.487 s, slowest integration 8.628 s).
The preceding bounded run was 68.317 s. Formatting, Clippy, all-feature build,
bin/doc tests and default-feature build pass. PATH revision was verified.

Tracy isolated candidate verification showed `candidate_authored_checkout`
at 236.6 ms self-time. Replacing five native Git probes with gix HEAD/index
reads and one path-scoped native status reduced it to 58.0 ms. Status retains
staged, unstaged and untracked checks; selected-input membership still walks
the selected trees. Existing full candidate and release regressions pass.

Test-only release role selectors now load/validate checkout metadata once per
process; every caller still reads and renders requested role bytes afresh. This
alone measured 71.490 s versus 71.247 s before, not a demonstrated throughput
gain. Do not infer further cache benefits without evidence.

The results below are earlier checkpoints, not the current completion evidence.

Fresh per-test timing identified two scheduling tests scanning the real developer
cache through the example `minimal_plan_for_paths` fixture. Both now override
their diagnostic paths with private temporary roots. The ordering test uses a
bounded channel wait rather than a sleep and checks its own fixture log warnings.
Both tests pass together in 0.01 s. That correction alone did not materially
improve full throughput: its complete gate passed at 73.123 s.

Lockfile-only prepared/released dependency tests also rendered project role
inputs and discarded them. They now use a separate raw-lock helper; full role
tests retain the original setup and all assertions. The 71.247 s measurement
includes this change. Next measure repeated catalog/metadata parsing in the
remaining full role fixture calls; avoid assuming the latest bottleneck is Git.

Committed and installed checkpoint `119b6e38c`: **72.289 s**, 1,682 active library tests and 70
integration tests, all passing. Formatting, Clippy, all-feature build, bin/doc
tests and final default-feature build passed as well. PATH revision verified;
the one-minute requirement remains unfulfilled.

Earlier bounded run in this batch: **78.933 s**, 1,682 active library tests and 70
integration tests, all passing, with the same 22 ignored entries. Command:
`scripts/test-bounded.ps1 -Workers 4 -TestThreads 8`. Library process: 70.337 s;
slowest integration: 8.370 s. This is still above the 60-second requirement.
Release tag preflight now uses gix revision traversal instead of spawning
`git merge-base --is-ancestor`. Twelve focused tests pass in 6.18 s, including
new merge-parent, unrelated-history and missing-object cases. No dependency
or concurrency change. The preceding metadata-cache batch passed in 82.318 s;
do not attribute all timing variation to this one subprocess removal.

Target planning now shares one invocation-local verified package between provider
review and local tag preflight, rather than reading and hashing all ten JARs
twice. Standalone commands retain their own verification. Cross-invocation reuse
is not introduced; selected upload bytes are still separately checked and owned.
All 59 release CLI regressions pass in 16.13 s. The complete current test phase
passes at **72.289 s**, with unchanged coverage and concurrency. This small
timing difference is not sufficient to isolate this optimization's contribution.
Next inspect remaining release-preflight Git subprocesses and repeated fixture
preparation, using measurements rather than expanding caches speculatively.
The preceding `check-all.ps1 -TestWorkers 4 -TestThreads 8` gate
passed: dependency policy, formatting, Clippy, all-feature build, all tests,
and final default-feature build. Its post-compilation test phase took
**73.542 s** (library process 66.369 s, slowest integration 7.130 s), with the
same coverage.

### Earlier measurements (historical statuses, superseded above)

The split ownership matrix passes the complete suite at **86.361 s** (1,681
library and 70 integration tests). Its isolated speedup did not improve total
throughput; do not expand serial-matrix partitioning on that assumption.
The frozen-recipe and NeoForm test fixture builders now retain one parsed
read-only workspace catalog/metadata snapshot, as the Forge fixture already
does. Mutable temporary projects and production checks remain independent.
Seventeen focused frozen-recipe tests pass in 4.64 s (one existing ignored
profiling test); its full-suite timing was 82.318 s. The shared
project-input metadata is approximately 1.2 MB. Further opportunities include
repeated validation/selection of that immutable metadata, but need measurement.

Generated inventory now checks each no-follow directory entry once, matching
core-source discovery, rather than walking all ancestors per leaf. Authored
metadata shares the invocation-local reader with selected inputs. All 21
ownership regressions pass, including junction/case/size/mutation checks.
The complete gate passes at **85.897 s** for 1,671 library and 70 integration
tests. This does not establish a material full-suite improvement.

Next experiment addresses scheduling: the twenty independent ownership
contexts now run as ten per-target tests (both environments each), plus a
test requiring exact coverage of `SUPPORTED_TARGETS`. No assertions or target
cases were dropped; cross-projection isolation keeps its full fixture. The
focused matrix passes in **1.48 s** at eight threads versus **6.92 s** for the
serial per-target-fixture loop. Full-suite measurement is pending. Active
library test count rises by ten to 1,681; this is partitioning, not extra
version coverage. Installed tooling remains checkpoint `123793441`.

Removed four per-path Git diff subprocesses from successful release tag
preflight. Whole-worktree status and concealed-index checks still bracket the
read-only inspection; selected committed bytes, modes and provenance are still
checked. The removed staged/unstaged probes duplicated that enclosing contract.
All 11 focused regressions pass in 6.44 s. The full bounded suite passes
1,671 library and 70 integration tests in **86.415 s** (library 77.092 s
process wall). Current changes still require the final complete gate and
checkpoint/install; this remains above 60 s. No active tests or deadlines were
removed. Next prioritize reducing repeated work in the ownership/build matrix,
not increasing concurrency or broadening the fixture cache without evidence.

Checkpoint `123793441` is committed and installed; the PATH CLI reports that
revision. Subsequent test-only work caches completed tag/target-plan package
setup as immutable fixture bytes, with independent files, Git indexes and refs
per caller. A parallel-copy isolation regression was added. All six focused
target-plan tests pass in 5.97 s. The full gate passes 1,671 active library and
70 integration tests, but takes **95.164 s** (library process 86.050 s).
This does not establish a speedup over the preceding 78.596 s run. Do not
extend the fixture cache on that assumption. The bounded 64-library-thread
run passes the same coverage in **82.652 s**, with unchanged deadlines and no
default change. This also misses 60 s. Fresh 32-thread diagnostics pass all
1,671 library tests in 82.075 s. Largest concurrent durations remain the
twenty-context ownership matrix (29.57 s), changed-input tag preflight
(27.27 s), transitional-loader Modrinth checks (26.96 s), and eight-context
Forge JAR matrix (25.44 s). Cached setup has not removed the bottleneck.
Next inspect repeated production validation/derivation inside these scenarios,
especially retained immutable profile parsing and nested package verification;
do not remove live-input checks around effects. Fixture caching remains an
uncommitted experiment, not a demonstrated whole-suite optimization.

Latest complete gate passes: formatting, production Clippy, all-feature and
default builds, 1,670 active library tests, 70 integration tests, and bin/doc
harnesses. Post-compilation execution is **78.596 s** (library 70.495 s process
wall). This covers the validation consolidation and gix wrapper-index fixture
changes described below. The 60-second goal remains unmet. No dependencies,
active-test exclusions, deadlines or concurrency defaults changed. Checkpoint
and install this batch before continuing release-fixture profiling.

Current focus: repeated filesystem validation, not more concurrency. Tracy on
the isolated six-target development matrix measured 1,968 checked-directory
calls and 4,470 checked-file calls, together about 41% of measured time (5.63 s
test duration). Repeated ancestor walks are the next optimization candidate;
do not cache safety decisions across filesystem mutations.

Removed the redundant `sync(Check)` output read pass from catalog ownership:
the ownership verifier already performs bounded path-checked byte comparisons.
Pure artifact/identity validation and before/after authored-source and generated
inventory checks remain. All 19 ownership regressions pass in 7.68 s, including
same-size edits, case aliases and junction rejection. The matrix recapture took
5.91 s, so this is reduced duplicated work, not a demonstrated wall-time win.
The normal test phase passed 1,668 active library and 70 integration tests in
102.586 s. This covers the duplicate-read removal and gix release-preflight
changes, not the subsequent reader below. No new dependency or timeout/concurrency
change was made. Goal remains unmet.

Next change under validation: `CheckedInputReader` reuses existing Windows
`DirectoryLeases` for one catalog byte-comparison pass. It never caches file
contents. Each leaf is opened no-follow, inspected and bounded; non-Windows
retains per-read path validation. Tests added for observing edits/missing files,
limits, ancestor replacement refusal and lease release between passes. This
change was made after the preceding library test executable was compiled.
Its 21 focused ownership tests pass in 6.51 s. The same matrix trace takes
5.53 s, with checked-file calls reduced from 4,470 to 1,164. It also reveals
7,386 new directory leases and 44,394 held-ancestor attribute queries; scope
setup costs matter. The unchanged 1,968 checked-directory calls remain the
largest self-time cost (1.204 s). Full current-source validation remains pending;
The subsequent full gate stopped at a block-semicolon Clippy finding (fixed).

Boundary audit took priority over faster per-path checking: removed entry
whole-project checks from `NativeProjectTarget::from_checked_project`,
`recheck_with_project`, and `DevelopmentNfrtDependencies::from_checked`.
Their intervening work derives identities/indexes from retained bytes (plus
read-only cache-path inspection); exit live-input checks remain, as do all
acquisition/execution checkpoints. Fifty selected development tests pass in
7.66 s (four existing ignored). Matrix profiling falls from 5.53 s to 4.19 s:
catalog rechecks 60 to 24, checked directories 1,968 to 954, authored snapshot
checks 144 to 72. This is an isolated result, not whole-suite acceptance.
Next run the complete current-source gate; continue auditing duplicated
validation boundaries before adding further per-directory optimizations.

Latest gate's library/integration phase: 1,670 active library and 70 integration
tests pass in 114.701 s (library 99.625 s process wall). No whole-suite speedup
is established. Binary/doc/default-build stages are still pending completion.
During this gate, a further unverified change consolidated entry/exit scans
inside `CatalogOwnedProject::check_current` and `recheck`: selected output
bytes are checked first, then authored bytes and exact generated membership
once before returning. There are no writes between these observations. Do not
claim the earlier gate covers this final edit; run a fresh focused build/test,
inspect any changed diagnostic expectations, then measure before another gate.

Fresh focused validation of that final edit passes all 21 ownership tests in
6.48 s. The missing-source regression now requires the exact missing path and
underlying NotFound error (the read rejects it before inventory enumeration),
and retains no-repair assertions. Matrix recapture: 3.27 s, checked-directory
calls 564, generated inventories 30 and authored snapshots 42. This compares
with 4.19 s / 954 directories before lower-level consolidation and 5.63 s /
1,968 directories before the boundary audit. The complete bounded test phase
is now running against this source; do not infer full-suite timing from the
isolated matrix. Prior full gate completed, but predates this final edit.

The first whole-suite measurement after consolidation was interrupted: its
process disappeared and log ended mid-test without a receipt. Do not count it.
The unchanged-source rerun passes all 1,670 active library and 70 integration
tests in **79.647 s** (library process 71.667 s). This is progress but still
above 60 s. Unrelated host compiler activity was observed around these runs;
do not attribute all timing variance to implementation changes. A fresh
per-test timing run of the already-built library is collecting the remaining
hot tests. Current-source formatting/Clippy and final checkpoint/install remain
pending; no new dependency, skipped active test or relaxed timeout was used.

Current per-test diagnostic passes 1,670 tests in 79.643 s (library only,
JSON timing overhead/load differs). Longest under concurrency: changed-input
release-tag preflight 30.16 s, transitional-loader Modrinth policy 27.36 s,
absent/exact tag preflight 26.99 s, twenty-context catalog 25.98 s, four-recipe
JAR matrix 25.17 s. Next target is release preparation: tag/Modrinth fixtures
repeatedly verify/package complete candidates, and production release checks
still contain native Git probes. Audit shared setup and operation boundaries;
do not remove meaningful release-state assertions or widen dependency authority.

Release-fixture optimization: both tag-preflight and target-plan promotion
helpers launched Git once per wrapper to stage executable mode (ten processes
per fixture). They now share `Fixture::stage_executable_wrappers`, opening the
private index with gix, updating exact staged paths, discarding the tree cache
and writing once. Native Git commits and subsequent production tree checks
remain. Eleven tag-preflight tests pass in 8.78 s; thirteen target-plan/Modrinth
tests pass in 6.53 s. Full current-source gate/checkpoint is next; no whole-suite
speed claim for this latest fixture change yet.

The opt-level-2 experiment passed all 1,668 active library and 70 integration
tests in **97.291 s**, after **7m27s** compilation. It does not meet the target
or justify changing the default profile. Debug assertions were verified enabled
in the compiler command; overflow checks were explicitly requested through the
Cargo profile environment. Other host Cargo work was observed, so this is not
an isolated profile comparison. Defaults remain unchanged. The next measurement
should distinguish filesystem/fixture costs from computation; do not continue
tuning optimisation levels. Release-preflight changes still need the normal
full validation gate before their next checkpoint.

Checkpoint `7fdb5f990` is committed and installed; the PATH executable reports
that revision. Nothing was pushed. Release preflight now uses fresh isolated
gix reads for HEAD, common directory, index flags and tag identities, retaining
the existing status/ancestry checks. All eleven focused tests pass in 9.70 s,
including annotated tags, retargeting, corrupt refs, hidden flags and grafts.
This latest change is not yet covered by a complete gate.

A process-local test-profile experiment uses opt-level 2 with debug assertions
and overflow checks explicitly enabled. No Cargo profile/default or dependency
version was changed. Measure all active library and integration tests; do not
adopt the compile/runtime tradeoff without whole-suite correctness and timing.

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
