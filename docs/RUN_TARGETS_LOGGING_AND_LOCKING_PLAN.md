# Run Targets, Logging, Parallelism, And Locking Plan

## Purpose

This document captures the implementation plan for evolving `sfm-propagate-changes` run/build orchestration beyond single-version commands.

The goals are:

- Replace `--mc` with a richer `--branch` selector.
- Distinguish core publishable Minecraft-version worktrees from feature worktrees.
- Make multi-target command output readable before adding parallel execution.
- Add process-safe artifact locking so multiple CLI processes or parallel targets do not corrupt shared caches.
- Keep the plan status explicit and resumable across fresh context windows.

## Current Status

Status values used below:

- `Not Started`: no committed implementation yet.
- `In Progress`: implementation has started but is not fully validated.
- `Done`: implemented, committed, propagated, and validated.
- `Blocked`: cannot proceed without a decision or external change.

Overall status: `In Progress`

## Decisions Locked In

Status: `Done`

- The default branch selector is `core`.
- `--mc` will be removed, not kept as a compatibility alias.
- All commands that currently support `--mc` will move to `--branch`.
- Multi-target commands run sequentially by default.
- `--parallel N` will be added later; when supplied without `N`, it defaults to `10`.
- Parallel graphical `run client` is allowed as a planned capability after scheduler, logging, locking, and cancellation behavior are solid.
- `--error-action continue|bail` will control whether multi-target commands collect failures or stop at the first failure.
- Minecraft version identity comes from each worktree's `platform/minecraft/gradle.properties` `minecraft_version` value.
- A branch is core only if the branch name itself is a valid numeric dotted version and has no `-patch` suffix.
- `26.1.2` is a Minecraft version and compares greater than `1.21.1`.
- Version predicates such as `>=1.19.2` may match feature branches through their inferred `minecraft_version`.
- Core-scoped version predicates use the compact form `core>=1.19.2`.
- We want strong Rust types for parsed branch query rules, not stringly typed matching.
- Rust command progress should flow through structured `tracing`, not direct `println!` or `eprintln!`.
- The one direct stderr exception is Ctrl+C echoing, which should immediately write a red `^C` using the `owo-colors` re-export from `color-eyre`.
- Tests must not depend on the installed `$env:PATH` CLI being up to date.
- Do not run `install.ps1` as part of normal validation unless explicitly requested.
- This plan doc should be committed before Rust implementation begins. Future implementation changes should stay unstaged/uncommitted for human review unless explicitly requested otherwise.
- Discard the Log4j JSONL injection idea for this plan. Build the foundation around line-level subprocess capture and wide tracing events.
- Prefer one primary Rust type per file for new orchestration/query/locking modules. Small helper functions may live beside the type they serve; broad mixed-type modules should be avoided.
- Prefer ergonomic single-field newtypes for domain identifiers and paths: public tuple field plus `Deref` and `AsRef` implementations for the wrapped view type, such as `str` or `Path`.
- CLI structs should hold typed values where `figue` can deserialize them directly. Prefer Facet enums for closed option sets and transparent typed newtypes for string-backed CLI grammars; convert those wrappers into domain request objects at the CLI boundary. Avoid raw `String` fields when the field has domain meaning.
- Build/run plan structs should use branch/version domain types internally. JSON output may use explicit Facet proxy wrappers to preserve stable string-shaped wire formats.

## Core Concepts

### Worktree Target Identity

Status: `Done`

Introduce a target model that represents a Git worktree independently from a Minecraft version:

```rust
struct WorktreeTarget {
    branch: BranchName,
    worktree_path: WorktreePath,
    core: bool,
    mc_version: Option<MinecraftVersion>,
}
```

Rules:

- A core target is a worktree whose branch name is exactly a publishable Minecraft version branch, such as `1.19.2`, `1.21.1`, or `26.1.2`.
- A core branch name must parse as a dotted numeric version and must not include a `-patch` suffix.
- `26.1.2` is a Minecraft version under the later versioning scheme and sorts after `1.21.1`.
- `mc_version` must be read from `platform/minecraft/gradle.properties`, using the `minecraft_version` property.
- Feature branches, such as `feat/1.19.2/draw`, infer `mc_version` from `gradle.properties`, but `core` must be `false`.
- Publishing and release commands must default to core targets only.
- Run/build/test commands may allow feature worktrees when explicitly selected.
- Display and logging should use `BranchName`.

### Branch Selector

Status: `Done`

Replace `--mc` with `--branch`. Do not preserve `--mc` as a compatibility holdback.

Default:

```pwsh
--branch core
```

Selector examples:

```pwsh
--branch core
--branch *
--branch ">=1.20"
--branch "core>=1.20"
--branch "core && >=1.20"
--branch "feat/*"
--branch "feat/1.19.2/draw"
```

Rules:

- `core` matches only core publishable worktrees.
- `*` matches every discovered worktree, including feature branches.
- Version comparisons such as `>=1.20` match every target whose inferred `mc_version` satisfies the comparison, including feature branches.
- Compact core-scoped version comparisons such as `core>=1.20` match only core targets whose inferred `mc_version` satisfies the comparison.
- Exact branch strings match the exact branch name.
- Glob-style branch patterns may match feature branches.
- Boolean composition must support aliases:
  - OR: `OR`, `,`, `|`, `||`
  - AND: `AND`, `&`, `&&`
  - All targets: `*`
- The query should parse into a disjunctive-normal-form style typed representation: top-level OR terms, each containing AND rules.
- Follow the spirit of `G:\Programming\Repos\teamy-mft\src\query\query_string.rs`: parse into structured Rust types first, then evaluate typed rules.

Suggested typed model:

```rust
struct BranchQuery {
    alternatives: Vec<BranchConjunction>,
}

struct BranchConjunction {
    rules: Vec<BranchRule>,
}

enum BranchRule {
    All,
    Core,
    ExactBranch(ExactBranch),
    BranchGlob(BranchGlob),
    Version {
        scope: VersionScope,
        op: VersionOp,
        version: MinecraftVersion,
    },
}

enum VersionScope {
    AnyTargetWithMinecraftVersion,
    CoreOnly,
}

enum VersionOp {
    Lt,
    Lte,
    Eq,
    Gte,
    Gt,
}
```

`BranchQuery` should implement `Display` using canonical operator text (`OR`, `AND`) so parsed selectors can be rendered in a stable form. Unit tests should verify that displayed queries parse back to the same typed representation. Generated arbitrary query tests should use constrained `ExactBranch` and `BranchGlob` newtypes so random values stay inside the accepted grammar.

Migration:

- Replace CLI fields named `mc` with `branch`.
- Replace help text and docs that mention `--mc`.
- Update tests so old `--mc` invocations fail to parse.
- Update internal request objects to carry one or more `WorktreeTarget`s rather than a bare MC version string.
- `jar compare` may remain single-target until compare reporting is designed for multi-target summaries.

### Multi-Target Scheduling

Status: `Done`

Commands that resolve more than one target should run sequentially by default.

Initial behavior:

- Omitted branch selector means `--branch core`.
- `run client --branch core`: run each core target in worktree order.
- `run client --branch *`: run every worktree, including feature worktrees.
- `jar plan --branch core`: plan each core target in worktree order.
- `jar build --branch core`: build publishable core targets.
- Per-target `last-plan.json` remains under that worktree's `build/sfm-toolchain/state`.
- A single-target `--plan-json <path>` keeps the historic single-plan JSON shape; multi-target selectors write an array of resolved plans.
- Release/publish commands should reject non-core targets unless an explicit future flag says otherwise.
- `--error-action bail`: stop after the first target failure.
- `--error-action continue`: continue running remaining targets, then emit a target summary and return failure if any target failed.
- Default error action is `bail`.

Parallel behavior:

- Add `--parallel` after logging and cache locking are ready.
- `--parallel` with no value means `--parallel 10`.
- `--parallel N` limits concurrent target executions to `N`.
- First allow `--parallel` for dry-run or resolution-heavy commands.
- Expand to `run game-test-server` after validation.
- Expand to graphical `run client` after scheduler, prefixed logging, locking, and Ctrl+C behavior are validated.
- A future implementation may use a Tokio `JoinSet`, but the public behavior should be target-level parallelism first, not unbounded internal parallelism.

### Wide Tracing Events

Status: `Done`

All orchestration and subprocess output should be represented as structured tracing events with enough fields for subscribers to render useful output.

Suggested fields:

```rust
tracing::info!(
    branch = %target.branch,
    mc_version = target.mc_version.as_deref().unwrap_or(""),
    core = target.core,
    source = "minecraft",
    process = "minecraft",
    stream = "stderr",
    line = %line,
    "subprocess output"
);
```

Source values:

- `rust`: SFM CLI orchestration and cache/build steps.
- `java-tool`: Java tools invoked by the build pipeline.
- `minecraft`: launched Minecraft client/server/game-test process output.

`process` should carry the more specific child/tool identity, such as `minecraft`, `javac-main`, `antlr`, or a Forge tool id.

Stream rules:

- Track `stdout` and `stderr` internally.
- Terminal output may omit `stream=stdout` decoration.
- Terminal output should show `stream=stderr` when useful.
- Subprocess lines are `INFO` by default in the first pass.
- Existing direct `println!`/`eprintln!` progress output should be replaced with tracing events as affected code is touched.
- The tracing subscriber is responsible for rendering prefixes such as `[rust]`, `[1.19.2 mc]`, or `[feat/1.19.2/draw mc stderr]`.

Current implementation notes:

- Build plans now carry `branch_name` so subprocess events do not have to infer their target from Minecraft version.
- Branch context is carried by tracing spans around target/build/run/launch/lockfile work instead of repeating `branch` on every Rust event.
- Rust-origin orchestration events use the actual log text as the tracing event message and omit `source`, `process`, and `stream`; the subscriber can infer `source=rust`, `process=sfm`, and `stream=stdout` defaults when those fields are absent.
- Captured Java tool, `javac`, ANTLR, and launched Minecraft stdout/stderr lines are emitted as structured tracing events with explicit `source`, `process`, and `stream` fields. Their branch context comes from the surrounding span, including dedicated stream-reader spans for launched Minecraft output threads.
- Terminal prefix rendering is implemented for normal terminal output.

### Per-Line Subprocess Logging

Status: `Done`

First pass should not parse Minecraft Log4j lines. Treat each stdout/stderr line as content.

Terminal rendering examples:

```text
[rust] resolving worktrees
[1.19.2 rust] compile-project reused cached outputs
[1.19.2 mc] [Render thread/INFO] Minecraft reached title screen
[feat/1.19.2/draw mc stderr] stacktrace line
```

Rules:

- Preserve line order per subprocess stream.
- Do not attempt multi-line stacktrace grouping in the first pass.
- In parallel mode, line interleaving is acceptable as long as every line is tagged.
- Preserve raw lines in tracing fields for later diagnosis.
- JSONL log files are emitted only when the user supplies `--log-file`, matching current logging expectations.

Explicitly rejected for this plan:

- Do not inject custom Log4j configs for JSONL output.
- Do not depend on Minecraft/Forge/NeoForge Log4j configuration behavior for correctness.
- Keep the foundation version-agnostic by capturing subprocess stdout/stderr line-by-line and emitting structured tracing events from those lines.

### Process-Safe Artifact Locking

Status: `In Progress`

Shared cache writes must be safe across:

- `--parallel` targets in one SFM CLI process.
- Multiple independently running `sfm-propagate-changes.exe` processes.
- Core and feature worktrees sharing the same artifact cache.

Use Cargo's locking model as the reference behavior.

Cargo reference:

- Cargo has a `cargo::util::flock` wrapper around locked files.
- It opens or creates a stable lock file, then acquires an OS-level lock on the file handle.
- It first attempts a non-blocking lock.
- If the lock would block, it prints a waiting message and then waits on the blocking lock call.
- The lock is released when the lock guard is dropped.
- On current Rust, this can be implemented with `std::fs::File::try_lock`, `File::lock`, and `File::unlock`.
- Rust documents `File::lock` as mapping to `flock` on Unix and `LockFileEx` on Windows.

Important rule:

- The presence of a `.lock` file is not the lock.
- The OS-held lock on the open file handle is the lock.
- A stale `.lock` file after a crash should be harmless.

Implementation pattern:

```rust
let file = OpenOptions::new()
    .read(true)
    .write(true)
    .create(true)
    .open(lock_path)?;

match file.try_lock() {
    Ok(()) => {}
    Err(TryLockError::WouldBlock) => {
        tracing::info!(
            artifact = %artifact_id,
            lock = %lock_path.display(),
            "waiting for artifact lock"
        );
        file.lock()?;
    }
    Err(TryLockError::Error(err)) => return Err(err.into()),
}

// Keep `file` alive in a guard until protected work is complete.
```

Protected artifact-write sequence:

1. Open or create `<artifact>.lock`.
2. Acquire exclusive OS lock on the lock file.
3. Re-check whether the final artifact already exists and matches the expected checksum.
4. If valid, skip download/work and release the lock.
5. If missing or invalid, write to `<artifact>.tmp.<pid>.<random>`.
6. Verify checksum on the temp file.
7. Atomically rename temp path to final artifact path.
8. Release lock by dropping the guard.

Notes:

- Use standard-library file locking first because the project is on Rust 1.96.
- Avoid adding a locking crate unless std locking proves insufficient.
- If we do add a crate later, likely candidates are `fs4`, `fs2`, or `fd-lock`, but std should be the initial implementation.
- Add tracing fields for lock waits so parallel dry-runs can explain contention.
- Lock waits may be indefinite, like Cargo, but they must emit periodic tracing events so the user knows progress is blocked on a lock.
- Reads of shared cache artifacts should also coordinate with locks when a partially written file or directory could otherwise be observed.
- Simple immutable file reads may rely on checksum verification and atomic replace only where that is demonstrably safe.

Corrupt final artifact behavior:

1. Acquire the artifact lock.
2. If the final artifact exists but checksum verification fails, rename it to a `.bad` path that includes enough uniqueness to avoid clobbering an older bad file.
3. Retry the download/build up to a bounded retry count.
4. If a retry succeeds, remove `.bad` files associated with that artifact when safe.
5. If all retries fail, leave the `.bad` file for diagnosis and return a clear error.

Windows atomicity:

- Prefer a platform abstraction for final-file replacement.
- If `std::fs::rename` is insufficient on Windows when the destination exists, use `windows-rs` behind the abstraction.
- Final replacement still occurs while holding the artifact lock, so less-atomic remove-then-rename behavior is acceptable only if no unlocked reader can observe the gap.
- Tests must cover the chosen Windows path.

### Shared Cache Layout

Status: `In Progress`

The common cache should live under `sfm-propagate-changes.exe cache path`, grouped by purpose. Worktree-local build products stay under each worktree's `platform/minecraft/build/sfm-toolchain`.

Expected common cache candidates:

- Minecraft version manifest and per-version metadata JSON.
- Minecraft client/server jars.
- Minecraft server bundle contents and extracted server jars.
- Mojang client/server mappings.
- Minecraft asset indexes and asset object blobs.
- Maven artifacts and metadata.
- CurseMaven artifacts and metadata.
- MCPConfig artifacts and extracted immutable data keyed by artifact hash.
- Forge and NeoForge userdev/sources/universal artifacts.
- Forge/NeoForge/NeoForm tool jars and immutable tool outputs keyed by input/tool/mapping hashes.
- Parchment data.
- Mixin, ANTLR, Forge tools, NeoForge tools, and normal dependency jars.
- Remapped/deobfuscated external dependency jars when they can be keyed solely by input artifact hash, mapping hash, and tool identity.

Expected worktree-local cache/build products:

- Project generated ANTLR sources.
- Project compiled main/gametest/datagen/test classes.
- Project generated resources and staged resources.
- Mixin refmaps and project-specific mapping outputs.
- Development jars and final `*-rust.jar` artifacts.
- Run directories, launch argfiles, run logs, and run-specific state.
- Node state files describing worktree-local graph execution.

Rule of thumb:

- Anything immutable and keyed only by upstream artifact content, mappings, tool identity, and Minecraft version can move to the common cache.
- Anything depending on the current worktree's source/resources/properties stays worktree-local.

### Ctrl+C Handling

Status: `In Progress`

Interactive and multi-target commands should handle cancellation predictably:

- First Ctrl+C requests graceful shutdown of active child processes and prevents new targets from starting.
- A second Ctrl+C within one second forces process exit.
- Every Ctrl+C immediately writes a red `^C` to stderr using the `owo-colors` re-export from `color-eyre`.
- Graceful shutdown should try to terminate launched Minecraft/JVM child processes before returning.
- Parallel implementation must share one cancellation signal across target tasks.

Current implementation notes:

- The working tree installs a process-wide Ctrl+C handler from the CLI entrypoint.
- First Ctrl+C sets a shared cancellation flag, emits a red `^C`, prevents new parallel targets from starting, and asks active Java/Minecraft child processes to stop.
- A second Ctrl+C within one second emits another red `^C` and force-exits with status 130.
- Java tool, `javac`, ANTLR, and launched Minecraft JVM waits poll the cancellation flag and kill the active child before returning a cancellation error.

### Locking Testability

Status: `Not Started`

The lock layer must be designed so contention is testable without shelling out to the installed CLI.

Testing requirements:

- Use temporary directories for lock and artifact paths.
- Unit tests should be able to contend on the same lock from multiple handles in the same process.
- Where PID appears in temp names, inject a testable `ProcessIdentity` or temp-name strategy so tests can spoof identities without spawning separate processes.
- Include tests for:
  - lock acquisition with no contention
  - wait path when another handle owns the lock
  - final artifact becomes valid while waiting
  - corrupt final artifact renamed to `.bad`
  - temp file checksum failure does not replace final artifact
  - retry succeeds after an initial corrupt artifact
  - stale `.lock` file with no OS lock does not block
  - Windows replacement behavior, where practical
  - concurrent readers do not observe half-written artifacts

## Implementation Sequence

| Step | Status | Scope | Completion Criteria |
| --- | --- | --- | --- |
| 1 | `Done` | Add `WorktreeTarget` and classify core vs feature worktrees. | Committed in `ee92ebd6f`; unit tests cover core version branches, feature branches, and inferred MC versions. |
| 2 | `Done` | Implement typed `BranchQuery`, DNF-style `BranchConjunction`, and `BranchRule` parser/evaluator. | Committed in `ee92ebd6f`; selector tests cover aliases, core, all, exact branch, feature glob, version comparisons, display round-trips, arbitrary generated selectors, and `core>=1.20`. |
| 3 | `Done` | Replace `--mc` with `--branch` in run/build/plan/compare surfaces. | Committed in `2d88a2b79`; CLI tests show `--branch` parses, default is `core`, and `--mc` no longer parses. |
| 4 | `Done` | Convert single-target run/build requests into multi-target scheduling. | Committed in `a260d22ab`; `jar plan`, `jar build`, and `run ...` iterate matching targets sequentially. |
| 5 | `Done` | Add `--error-action continue\|bail` to multi-target commands. | Committed in `cf0925311`; default is `bail`, `continue` records per-target failures and returns failure after the target summary. |
| 6 | `Done` | Add wide tracing fields for branch/source/stream/subprocess lines. | Build/run/launch/lockfile spans carry branch context; forwarded Java tool, `javac`, ANTLR, and Minecraft lines carry explicit source/process/stream fields. |
| 7 | `Done` | Replace affected `println!`/`eprintln!` progress with tracing events. | Jar build/run plan summary, target summaries, build node timing, launch setup/validation, Java tool, javac, ANTLR, and subprocess echo paths use tracing events; compare/report output and older helper output remain direct for later slices. |
| 8 | `Done` | Add prefixed terminal rendering for line events. | Terminal tracing layer renders branch/source/process/stream-aware prefixes; explicit `--log-filter`/`--debug` override `RUST_LOG`; validated with `jar plan --branch 1.19.2`. |
| 9 | `Done` | Keep JSONL logging opt-in via `--log-file` and ensure raw subprocess lines are represented. | Working tree makes JSONL append all events and include current span/span list context; validated with `jar plan --branch 1.19.2 --log-file`. |
| 10 | `Done` | Implement std-based artifact lock guard. | Committed in `433a01e98`; `ArtifactLock` uses std file locking with wait policy, non-blocking try-acquire, blocking wait loop with tracing, and contention/stale-file tests. |
| 11 | `Done` | Wrap artifact downloads/cache writes with lock + temp + checksum + atomic replace. | Committed in `0618896cc`; Maven artifact cache reads/writes, local artifact fallback copies, generic downloads, and known-SHA Minecraft asset downloads use artifact locks, unique temp files, checksum validation, and `.bad` quarantine. |
| 12 | `In Progress` | Move eligible immutable artifacts into the common SFM cache. | Working tree adds explicit common-cache plan paths and routes Maven artifacts, Mojang manifests/version metadata, Minecraft jars/mappings, libraries, and assets through the CLI cache path while keeping project outputs worktree-local. |
| 13 | `In Progress` | Add `--parallel [N]` for dry-run/resolution-heavy commands, defaulting to 10. | Working tree adds typed `Parallelism`, argv normalization for bare `--parallel`, and a worker-pool dispatcher for multi-target jar/build/run execution. Parallel results are re-ordered back into target order before summaries and plan JSON are written. |
| 14 | `In Progress` | Add Ctrl+C graceful/force shutdown behavior. | Working tree installs a Ctrl+C handler, echoes red `^C`, stops new target starts, kills active Java tool, `javac`, ANTLR, and Minecraft JVM children on graceful cancellation, and force-exits on a second Ctrl+C within one second. |
| 15 | `In Progress` | Expand parallel support to `run game-test-server` if logs and locks hold up. | Working tree allows live `run game-test-server --parallel`; runtime validation across core targets is still pending. |
| 16 | `In Progress` | Expand parallel support to graphical `run client`. | Working tree allows live graphical `run client --parallel`; runtime validation with multiple client windows is still pending. |

## Validation Plan

For each implementation step:

- Run `platform/cli/sfm-propagate-changes/check-all.ps1` from the CLI crate directory.
- Run focused CLI parse/unit tests for changed selector behavior.
- Run `sfm-propagate-changes.exe git merge` after committing the oldest-branch change.
- Confirm all version worktrees are clean after propagation.

For target selection:

```pwsh
sfm-propagate-changes.exe run game-test-server --branch core --dry-run
sfm-propagate-changes.exe run client --branch "feat/1.19.2/draw" --dry-run
sfm-propagate-changes.exe jar plan --branch "core && >=1.21.0"
sfm-propagate-changes.exe jar plan --branch "core>=26.0.0"
```

For locking:

```pwsh
# In two terminals, against overlapping branches:
sfm-propagate-changes.exe run game-test-server --branch core --dry-run
sfm-propagate-changes.exe run game-test-server --branch "*" --dry-run
```

Expected locking behavior:

- One process acquires an artifact lock.
- The other process logs that it is waiting.
- The waiting process re-checks the artifact after acquiring the lock and skips work if checksum now matches.
- No partial or corrupt artifact remains after interruption.

## Open Decisions

| Decision | Status | Notes |
| --- | --- | --- |
| Exact branch selector grammar | `Decided` | Typed DNF-ish representation; OR aliases are `OR`, `,`, `|`, `||`; AND aliases are `AND`, `&`, `&&`; `*` means all targets. |
| Default branch selector | `Decided` | Default is `core`. |
| Version predicate feature-branch behavior | `Decided` | Plain version predicates match any target with inferred `minecraft_version`; `core>=...` scopes to core. |
| Core definition | `Decided` | Branch name parses as dotted numeric version with no `-patch` suffix. |
| `26.1.2` ordering | `Decided` | It is a Minecraft version and sorts after `1.21.1`. |
| JSONL log default | `Decided` | Only emit JSONL when `--log-file` is supplied. |
| Error action | `Decided` | Add `--error-action continue\|bail`. |
| PATH install during validation | `Decided` | Do not install automatically; tests should not depend on installed PATH binary. |
| Parallel graphical clients | `Decided` | Allow eventually, after scheduler, logging, locking, and Ctrl+C behavior are validated. |
| Log4j JSONL injection | `Rejected For This Plan` | Use raw line-level subprocess capture and wide tracing events instead. |
