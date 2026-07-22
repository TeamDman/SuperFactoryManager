# Parallel worktree build isolation plan

## Purpose

Allow independent 1.19.2 feature worktrees to compile and test concurrently.
Only genuinely shared mutable artifacts should serialize, and their lock scope
should end as soon as the shared mutation is complete. Worktree-local source,
classes, test output, run directories, logs, and puppet artifacts must not be
guarded by a version-wide or repository-wide lock.

## Investigation record — 2026-07-21

The coarse build lock is already worktree-local:

```text
BuildPlan.cache_dir
  = <selected worktree>/platform/minecraft/build/sfm-toolchain

build_cache_lock_path(plan)
  = <cache_dir>/.locks/build-cache.lock
```

The three active feature worktrees each had a distinct cache tree and lock:

- `worktrees/1.19.2-action-hotkeys/.../sfm-toolchain/.locks/build-cache.lock`
- `worktrees/1.19.2-panel-observation/.../sfm-toolchain/.locks/build-cache.lock`
- `worktrees/1.19.2-review-diff/.../sfm-toolchain/.locks/build-cache.lock`

Therefore the earlier assumption that `--wait-for-build-lock` inherently
serialized all three builds was incorrect.

A three-way parallel `run compile` probe reproduced a real cross-worktree
failure in the shared immutable artifact cache instead:

```text
Failed to open artifact lock
C:\Users\Teamy\AppData\Local\teamdman\sfm-propagate-changes\cache\
minecraft-toolchain\maven\net\minecraftforge\forge\1.19.2-43.4.0\
forge-1.19.2-43.4.0-userdev.jar.lock
```

The artifact layer distinguishes exclusive writer locks from shared reader
locks, which is the correct design. However, both paths call `open_lock_file`
once before entering their retry loops. A transient Windows sharing/create
race while opening the `.lock` file therefore escapes immediately; the
`ArtifactLockWaitPolicy` only retries `try_lock`/`try_lock_shared` after a file
handle has already been opened. `--wait-for-build-lock` does not repair this
failure because it controls the separate worktree build-cache lock.

The probe also showed why elapsed compile time alone was misleading: concurrent
`javac` processes can survive a short shell wrapper timeout, and cold parallel
builds compete for CPU, memory, and disk even when no mutual-exclusion lock is
held. Process ownership and lock-wait telemetry must be reported separately.

## Required lock hierarchy

1. **Worktree build lock:** exclusive per `BuildPlan.cache_dir`; protects only
   that worktree's generated state and may remain held through its compile/test
   operation.
2. **Shared artifact writer lock:** exclusive per exact artifact path; protects
   download, replacement, quarantine, provenance, and source materialization.
3. **Shared artifact reader lock:** shared per exact artifact path; held only
   while validating or copying/reading an artifact that a writer could replace.
4. **Source repository/checkout lock:** exclusive only for fetch, checkout, or
   mutation of the corresponding shared source cache. Compiling ordinary SFM
   Java sources must not retain it.
5. **Live client/server ownership:** worktree-local run directories may run in
   parallel subject to explicit GPU/memory limits; this is capacity management,
   not an artifact-consistency lock.

Never solve this by duplicating the whole Maven/Minecraft cache per worktree.
Shared immutable artifacts are useful; their synchronization must support many
readers and narrowly scoped writers.

## Phase 1 — Make the failure deterministic

- Add a Windows-capable test that starts multiple processes or handles against
  the same absent lock path and proves concurrent readers can create/open then
  share-lock it.
- Add a writer-versus-reader test proving readers wait while replacement is in
  progress and then all proceed.
- Add a three-target integration fixture with distinct worktree cache dirs and
  one common artifact cache. Assert that no common coarse lock exists.
- Preserve the underlying OS error code and full lock path in diagnostics.

## Phase 2 — Retry lock-file opening safely

- Move lock-file opening under the same bounded/cancellable wait policy as OS
  lock acquisition. Retry only errors classified as transient sharing/access
  races; fail immediately for invalid path, missing permission, or disk errors.
- Log `waiting_to_open`, `waiting_for_shared_lock`, or
  `waiting_for_exclusive_lock` distinctly, including artifact, path, elapsed
  time, PID, and operation.
- Ensure simultaneous creators converge on the same persistent lock file.
  Never delete a lock file merely because no lock is presently held.
- Make cancellation interrupt both open retry and lock retry.

### Phase 2 completion — 2026-07-22

- Lock-file open/create now uses the same bounded, cancellable wait policy as
  lock acquisition and emits distinct `waiting_to_open` telemetry.
- Windows sharing violations are retried. Access-denied results are retried only
  when the target is a normal writable lock file, or is absent beneath a
  writable parent directory; directory targets, read-only targets, invalid
  paths, and disk errors remain immediate failures with their path and OS error.
- The lock file remains persistent. Deterministic tests cover concurrent
  first-open readers, transient open recovery, terminal open classification,
  cancellation, and preserved diagnostics.
- `check-all.ps1` passed (334 tests passed, 1 ignored), and the worktree-local
  CLI completed `run compile --branch 1.19.2 --wait-for-build-lock` against the
  shared cache.

## Phase 3 — Audit writer scope and artifact reads

- Inventory every `acquire_artifact_path_lock` call. A valid cache hit should
  normally require a shared read lock for hash verification, not an exclusive
  writer lock.
- Drop exclusive locks immediately after atomic replacement/provenance writes.
- Ensure source acquisition locks are not retained through project compilation.
- Confirm lockfile generation reads shared artifacts under shared locks but
  does not serialize independent worktree output writes.

## Phase 4 — Prove real parallel worktrees

Run compile and focused tests concurrently for at least:

```text
feat/1.19.2/action-hotkeys
feat/1.19.2/panel-observation
feat/1.19.2/review-diff
```

Acceptance evidence records per process:

- branch, worktree, PID, start/end time, and exit status;
- worktree build-lock path;
- every shared-artifact wait and its duration;
- compile/test summary and surviving descendant-process audit; and
- aggregate wall time compared with the sum of individual durations.

The test passes when all targets succeed concurrently, no process fails during
lock-file open, shared cache readers overlap, writers serialize only their exact
artifact, and output/log/artifact paths remain worktree-local.

## CLI usability follow-up

- `--wait-for-build-lock` should not suggest that it governs every artifact or
  source-cache lock. Either document the hierarchy explicitly or introduce a
  general `--wait-for-locks` policy shared by build, artifact, and source locks.
- A waiting command must emit periodic structured status instead of remaining
  silent long enough to look hung.
- Wrapper timeouts should cancel and reap the launched process tree, or print a
  durable continuation handle. A timed-out shell must not leave ownership of a
  compiler ambiguous.
