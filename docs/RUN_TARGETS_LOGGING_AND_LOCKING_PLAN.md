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

Current working-tree review status: implementation and validation are complete for the current review set, but sections with uncommitted work remain `In Progress` until the review set is committed and propagated.

Known remaining scope gap: no live Rust CLI surface is intentionally kept on `--mc`. The current working tree migrates `jar plan`, `jar build`, `jar compare`, top-level `run ...`, Gradle worktree orchestration, server registry commands, GitHub release commands, CurseForge release/metadata commands, and Modrinth release commands to typed `--branch` selection. The obsolete standalone `mc_version_filter` module has been removed. Any new MC-version-specific workflow should either consume `BranchSelector` directly or explicitly justify why it is not a worktree/release target selector.

Known feature-worktree gap: explicit `--branch feat/1.19.2/draw` selection reaches `D:/Repos/Minecraft/SFM/worktrees/1.19.2-draw`, but that feature worktree is stale relative to the current toolchain layout and lacks `platform/minecraft/gradle/dependencies/1.19.2/dependencies.gradle`. Core/default selectors still exclude the feature worktree, so publishable branch validation is unaffected.

## Decisions Locked In

Status: `Done`

- The default branch selector is `core`.
- `--mc` will be removed, not kept as a compatibility alias.
- All commands that select Git worktrees will move from `--mc` to `--branch`.
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
- Commands whose purpose is to return a machine-readable path/list/report may still write raw stdout, but that output must flow through the explicit `terminal_output` helper contract rather than scattered `println!` calls.
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
- `jar compare` supports multi-target branch selectors and `--parallel [N]`. Single-target `--report-json <path>` keeps the historic single-report JSON shape; multi-target selectors write an array of branch-scoped comparison reports. `--gradle-jar` and `--rust-jar` overrides are allowed only when the selector matches one target.

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
- Parallel scheduler tests cover cancellation being observed between targets: no additional target starts, and execution returns an `Operation cancelled by Ctrl+C` error instead of an empty successful summary.
- Java tool, `javac`, ANTLR, and launched Minecraft JVM waits poll the cancellation flag and kill the active child before returning a cancellation error.

### Locking Testability

Status: `In Progress`

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

Current implementation notes:

- Artifact lock unit tests cover uncontended acquisition, non-blocking contention, blocking wait, and stale lock files.
- Artifact read lock unit tests cover multiple concurrent readers and writer exclusion while a reader is active.
- Jar build engine tests cover corrupt final artifacts being quarantined, valid finals being reused, temp checksum failures not replacing finals, bad final cleanup after successful replacement, unique sibling temp files, lock waiters reusing an artifact completed by another lock holder, resolver cache hits waiting for an active writer lock before reading, and run-only Maven cache artifacts being written to the lockfile from provenance sidecars.
- Download tests cover retry after a temp-file checksum failure before publishing the final artifact.
- Existing-destination replacement is covered under an active artifact writer lock, matching the Windows-safe remove-then-rename behavior used by the implementation now that readers coordinate through shared locks.
- No known locking testability gaps remain in the working tree; section status stays `In Progress` until the changes are committed and propagated.

## Implementation Sequence

| Step | Status | Scope | Completion Criteria |
| --- | --- | --- | --- |
| 1 | `Done` | Add `WorktreeTarget` and classify core vs feature worktrees. | Committed in `ee92ebd6f`; unit tests cover core version branches, feature branches, and inferred MC versions. |
| 2 | `Done` | Implement typed `BranchQuery`, DNF-style `BranchConjunction`, and `BranchRule` parser/evaluator. | Committed in `ee92ebd6f`; selector tests cover aliases, core, all, exact branch, feature glob, version comparisons, display round-trips, arbitrary generated selectors, and `core>=1.20`. |
| 3 | `Done` | Replace `--mc` with `--branch` in run/build/plan/compare and helper/release surfaces. | Committed in `2d88a2b79` for jar/run surfaces. Working tree additionally migrates Gradle worktree orchestration, server registry commands, GitHub release commands, CurseForge release/metadata commands, and Modrinth release commands to typed `--branch`; CLI tests show migrated surfaces parse `--branch`, default to `core` where omitted, and reject old `--mc` invocations. |
| 4 | `Done` | Convert single-target run/build requests into multi-target scheduling. | Committed in `a260d22ab`; `jar plan`, `jar build`, and `run ...` iterate matching targets sequentially. Working tree extends `jar compare` to iterate matching branch targets too, with `--error-action continue\|bail` support, `--parallel [N]`, and branch-scoped multi-target JSON reports. |
| 5 | `Done` | Add `--error-action continue\|bail` to multi-target commands. | Committed in `cf0925311`; default is `bail`, `continue` records per-target failures and returns failure after the target summary. |
| 6 | `Done` | Add wide tracing fields for branch/source/stream/subprocess lines. | Build/run/launch/lockfile spans carry branch context; forwarded Java tool, `javac`, ANTLR, and Minecraft lines carry explicit source/process/stream fields. |
| 7 | `Done` | Replace affected `println!`/`eprintln!` progress with tracing events and centralize raw stdout output. | Jar build/run plan summary, target summaries, build node timing, launch setup/validation, Java tool, javac, ANTLR, subprocess echo paths, and jar compare report output use tracing events. No direct `println!`/`eprintln!` calls remain in `jar_build` or `artifact_lock`. Working tree additionally routes client/server registry output, jar collect/update summaries, Gradle status/log summaries, merge-conflict progress, git tag/push progress, and GitHub/Modrinth/CurseForge release/status/table output through tracing. Raw result output for commands such as `cache path`, `home path`, `repo-root show`, `client get-launcher`, `jdk list`, `git status`, `jar dir`, `jar list`, and Gradle log reports uses the `terminal_output` helper. Gradle `--show-logs` raw stream echo uses `terminal_output::stderr_text` so arbitrary chunks still stream without direct terminal macros. Focused `rg` scan shows the only remaining direct terminal macro is the explicit Ctrl+C `^C` echo required by the plan. |
| 8 | `Done` | Add prefixed terminal rendering for line events. | Terminal tracing layer renders branch/source/process/stream-aware prefixes; explicit `--log-filter`/`--debug` override `RUST_LOG`; validated with `jar plan --branch 1.19.2`. |
| 9 | `Done` | Keep JSONL logging opt-in via `--log-file` and ensure raw subprocess lines are represented. | Working tree makes JSONL append all events and include current span/span list context; validated with `jar plan --branch 1.19.2 --log-file`. |
| 10 | `Done` | Implement std-based artifact lock guard. | Committed in `433a01e98`; `ArtifactLock` uses std file locking with wait policy, non-blocking try-acquire, blocking wait loop with tracing, and contention/stale-file tests. |
| 11 | `Done` | Wrap artifact downloads/cache writes with lock + temp + checksum + atomic replace. | Committed in `0618896cc`; Maven artifact cache reads/writes, local artifact fallback copies, generic downloads, and known-SHA Minecraft asset downloads use artifact locks, unique temp files, checksum validation, and `.bad` quarantine. |
| 12 | `Done` | Move eligible immutable artifacts into the common SFM cache. | Working tree adds explicit common-cache plan paths and routes Maven artifacts, Mojang manifests/version metadata, Minecraft jars/mappings, libraries, and assets through the CLI cache path while keeping project outputs worktree-local. Lockfile generation now preserves the existing artifact lock graph while migrating matching entries to `$sfm-cache` paths, and refresh writes provenance-bearing run-time Maven cache artifacts into the lockfile so non-refresh run commands do not fail one transitive launch module at a time. Runtime `--refresh` regenerates the lockfile while still reusing valid shared cache artifacts, preventing parallel graphical runs from replacing a jar after another launched JVM has it open. Minecraft library launch/compile classpaths are selected from the active version JSON instead of recursively scanning the shared cache. Userdev libraries/modules and project compile annotation artifacts are now planned artifacts, so refresh planning records NeoForge launch modules such as `night-config` and hardcoded compiler helpers such as `org.jetbrains:annotations` before run setup. `cargo run -- jar build --branch core --dry-run --parallel --error-action continue` passes without local artifact fallback after the 26.1.2 Mekanism artifacts are present in SFM's common cache. The working tree also adds repeatable `--artifact-source <path>` flags so local project outputs or Maven repository roots can be searched in order and imported explicitly with `explicit-artifact-source` provenance instead of depending on implicit `.m2`/Gradle cache fallback. `BuildPlan` now includes an `artifact_portability` audit and `--require-portable-artifacts` turns any local-only or unknown provenance into a hard failure for clean-slate checks. The working tree adds `jar audit-artifacts` to verify lockfile cache/source provenance after planning, including cache SHA-1 checks, provenance sidecar checks, explicit source artifact checks, source Git metadata checks, source build metadata checks, optional JSON reports, `--parallel`, `--error-action`, and `--require-portable-artifacts`. Lockfile generation no longer performs a global shared-cache sidecar sweep because that polluted every branch lockfile with unrelated provenanced artifacts; run-only extras are still recorded explicitly from the current plan's extra cache paths. The artifact audit report/enums were split out of `engine.rs` into focused one-primary-type files so the new audit surface follows the module-shape preference before further provenance work builds on it. Explicit-source provenance records `source_relative_path` and `source_build`, and the resolver can now materialize recorded Gradle-wrapper source builds into SFM's common Maven cache when remote Maven resolution fails. Source-build checkouts live under `$sfm-cache/source-builds/<sha1(remote,commit)>`, enable Git long paths on Windows, run the recorded wrapper tasks/environment, and write `source-build` provenance instead of absolute `original_path` provenance. `cargo run -- jar audit-artifacts --branch core --parallel --error-action continue --require-portable-artifacts` passes across all core lockfiles. |
| 13 | `In Progress` | Add `--parallel [N]` for dry-run/resolution-heavy commands, defaulting to 10. | Working tree adds typed `Parallelism`, argv normalization for bare `--parallel`, and worker-pool dispatchers for multi-target jar/build/run/compare execution. Parallel results are re-ordered back into target order before summaries and JSON outputs are written. `cargo run -- jar build --branch core --dry-run --parallel --error-action continue` and `cargo run -- run game-test-server --branch core --dry-run --parallel --error-action continue` pass across all core worktrees. |
| 14 | `In Progress` | Add Ctrl+C graceful/force shutdown behavior. | Working tree installs a Ctrl+C handler, echoes red `^C`, stops new target starts, kills active Java tool, `javac`, ANTLR, and Minecraft JVM children on graceful cancellation, and force-exits on a second Ctrl+C within one second. Unit coverage includes the Ctrl+C timing state machine and parallel scheduler behavior after cancellation is observed. |
| 15 | `Done` | Expand parallel support to `run game-test-server` if logs and locks hold up. | `cargo run -- run game-test-server --branch "core>=1.21.0" --parallel --error-action continue` passes. Logs show 1.21.0 ran 190 tests, 1.21.1 ran 206 tests, and 26.1.2 ran 191 tests with all required tests passing. |
| 16 | `In Progress` | Expand parallel support to graphical `run client`. | Working tree allows live graphical `run client --parallel`; `cargo run -- run client-smoke --branch "core>=1.21.0" --parallel --error-action continue` passes, and `cargo run -- run client-puppet --branch core --parallel --error-action continue` passes across all 10 core worktrees. Client automation options seed `onboardAccessibility:false`, `narrator:0`, `pauseOnLostFocus:false`, and `tutorialStep:none`; the Java client puppet harness prevents `PauseScreen` from opening in puppet mode and also dismisses `PauseScreen` during puppet ticks as a fallback, so tests keep advancing if the OS launches the window unfocused. The harness also forces `minecraft.options.pauseOnLostFocus = false` during puppet ticks, which is the in-process equivalent of keeping F3+P enabled if a version reloads or rewrites options after launcher seeding. The harness logs `SFM_CLIENT_PUPPET_PREVENTING_PAUSE_SCREEN` for the event-level guard, `SFM_CLIENT_PUPPET_DISMISSING_PAUSE_SCREEN` for the tick fallback, and `SFM_CLIENT_PUPPET_DISABLING_PAUSE_ON_LOST_FOCUS` if the runtime F3+P guard has to correct the option. Unit coverage includes `client_automation_options_disable_onboarding_and_focus_pause` and `graphical_client_runs_use_relaxed_program_timing`. Game-test server and server runs keep `sfm.gametest.maxProgramRunMillis=150`; graphical client runs use `1000` because the SFM helper measures wall-clock program runtime and parallel desktop rendering can otherwise trip false performance failures. Full-core puppet validation passed with: 1.19.2 `219/219`, 1.19.4 `190/190`, 1.20 `190/190`, 1.20.1 `204/204`, 1.20.2 `190/190`, 1.20.3 `190/190`, 1.20.4 `190/190`, 1.21.0 `190/190`, 1.21.1 `206/206`, and 26.1.2 `190/190`, all before the 10 second `/sfm keep_open` countdown exit. A focused follow-up live parallel puppet run for 1.21.1 and 26.1.2 also passed after the event-level pause guard was added. Another follow-up live parallel puppet run for 1.21.1 and 26.1.2 passed after adding the runtime F3+P guard, with 1.21.1 `206/206` and 26.1.2 `190/190`. A later focused live parallel puppet run with `--refresh` also passed after the run-time shared-cache refresh race was fixed; logs show 1.21.1 `206/206` and 26.1.2 `190/190`, both reaching the 10 second `/sfm keep_open` countdown and exiting. Direct interactive `run client --parallel` remains manually validated because it intentionally does not auto-exit. |

Current 26.1.2 artifact note:

- Direct checks against the configured Maven repositories return 404 for `mekanism:Mekanism:26.1.2-10.8.0.86` and its `api` classifier.
- The exact jars are available in `G:\Programming\Repos\Mekanism\build\libs`.
- `cargo run -- jar plan --branch 26.1.2 --refresh --artifact-source G:\Programming\Repos\Mekanism` imports `Mekanism-26.1.2-10.8.0.86.jar` and `Mekanism-26.1.2-10.8.0.86-api.jar` into SFM's common cache and records `explicit-artifact-source` provenance in both the SFM cache sidecars and `platform/minecraft/sfm-toolchain.lock.json`, including source Git metadata, relative output paths, and source build metadata.
- A subsequent `jar build --branch "26.1.2" --dry-run` can pass without `--artifact-source` because the artifacts are then SFM-cache-backed and lockfile-backed.
- `--allow-local-artifact-cache` remains only a last-resort bootstrap/debug route for `.m2`/Gradle-created caches.
- A truly fresh environment can now recreate the two Mekanism jars from the recorded source-build provenance when the public Maven repositories return 404. The first source-build refresh exposed a Windows Git long-path checkout failure; the materializer now sets `core.longpaths=true` during clone and before checkout.
- Direct HEAD checks for both expected ModMaven paths and both `https://modmaven.dev/artifactory/local-releases/...` paths return 404 for the main and API Mekanism jars.
- The local `.m2` artifacts byte-match `G:\Programming\Repos\Mekanism\build\libs`; both main jars have SHA-1 `38d0a96f71852c103a57853cebb088da71446689`, and both API jars have SHA-1 `17a1ea79dd8e32d61f33f354b819ce1a2d61b571`.
- Explicit source provenance now records source Git metadata and relative artifact paths in the SFM cache sidecars, lockfile, and `last-plan.json`; the current Mekanism artifacts came from `G:\Programming\Repos\Mekanism`, branch `26.1`, commit `f33ff1f438caa55d58ef1f0a08091997353afcb8`, dirty `false`, remote `https://github.com/mekanism/Mekanism/`, relative outputs `build/libs/Mekanism-26.1.2-10.8.0.86.jar` and `build/libs/Mekanism-26.1.2-10.8.0.86-api.jar`.
- `cargo run -- jar plan --branch 26.1.2 --refresh` now succeeds without `--artifact-source`; the two Mekanism artifacts are built from source into SFM's common cache and recorded with `source-build` provenance.
- `cargo run -- jar plan --branch 26.1.2 --require-portable-artifacts` now passes because all locked 26.1.2 artifacts are fresh-slate portable.
- Normal `jar plan --branch 26.1.2` still succeeds and writes `artifact_portability` to `build/sfm-toolchain/state/last-plan.json`.
- `cargo run -- jar audit-artifacts --branch 26.1.2` verifies every locked artifact and exits successfully.
- `cargo run -- jar audit-artifacts --branch 26.1.2 --require-portable-artifacts` verifies every locked artifact and exits successfully.
- `cargo run -- jar audit-artifacts --branch core --parallel --error-action continue` verifies all core lockfiles and exits successfully.
- `cargo run -- jar audit-artifacts --branch core --parallel --error-action continue --require-portable-artifacts` verifies all core lockfiles and exits successfully.
- The refreshed core lockfiles no longer contain unrelated 26.1.2 Mekanism entries outside the `26.1.2` worktree after removing the global shared-cache sidecar sweep.
- Source Git provenance now includes an optional `remote_url` field, and explicit-source/source-build artifact provenance includes optional `source_relative_path` and `source_build` fields. Older lockfiles remain readable through Facet defaults, refreshed explicit-source artifacts include `origin`, source-relative output paths, Gradle wrapper tasks (`jar` and `apiJar`), and `BUILD_NUMBER=86`, and refreshed source-build artifacts replace absolute local `original_path` data with `$sfm-cache/source-builds/<checkout>` provenance.

Latest working-tree validation evidence:

- `cargo fmt --check`
- `cargo clippy --all-targets --all-features -- -D warnings`
- `cargo test` (`103` tests)
- `platform/cli/sfm-propagate-changes/check-all.ps1`
- Direct terminal output is centralized: `rg "\b(print!|println!|eprintln!)" src -g "*.rs"` reports only `src/cancellation/mod.rs` for the explicit red Ctrl+C `^C` echo. Raw stdout result commands use `terminal_output::{stdout_line, stdout_blank_line, stdout_prompt}` instead of direct macros; the subscriber-init fallback uses `terminal_output::stderr_line` because tracing is unavailable at that point.
- `cargo run -- jar build --branch core --dry-run --parallel --error-action continue`
- `cargo run -- jar plan --branch 26.1.2 --refresh --artifact-source G:\Programming\Repos\Mekanism`
- `cargo run -- jar plan --branch 26.1.2`
- `cargo run -- jar plan --branch 26.1.2 --refresh` exits successfully without `--artifact-source`, after cloning Mekanism source into `$sfm-cache/source-builds`, running the recorded Gradle wrapper source-build tasks, and writing `source-build` provenance for the main/API Mekanism jars.
- `cargo run -- jar plan --branch 26.1.2 --require-portable-artifacts` exits successfully.
- `cargo run -- jar audit-artifacts --branch 26.1.2` exits successfully.
- `cargo run -- jar audit-artifacts --branch 26.1.2 --require-portable-artifacts` exits successfully.
- `cargo run -- jar audit-artifacts --branch 26.1.2 --report-json build/sfm-toolchain/state/artifact-audit-26.1.2.json` writes a typed artifact audit report with every artifact verified and `fresh_slate_portable: true`.
- `cargo run -- jar audit-artifacts --branch core --parallel --error-action continue --require-portable-artifacts` exits successfully across all core worktrees.
- `cargo test artifact_audit -- --nocapture`
- `cargo check --all-targets --all-features`
- `cargo run -- jar plan --branch 26.1.2 --refresh --artifact-source G:\Programming\Repos\Mekanism` refreshes the explicit-source Mekanism artifacts and records `remote_url` for `origin`, `source_relative_path`, and `source_build` for both Mekanism output jars.
- `cargo run -- run client-puppet --branch "1.21.1|26.1.2" --refresh --parallel --error-action continue --artifact-source G:\Programming\Repos\Mekanism` exits successfully after the runtime refresh resolver fix; logs show 1.21.1 `206/206` and 26.1.2 `190/190`.
- `cargo run -- run client-puppet --branch "1.21.1|26.1.2" --dry-run --parallel --error-action continue` exits successfully after userdev modules and project compile annotations were promoted into planned artifacts and the lockfiles were refreshed.
- `cargo run -- jar audit-artifacts --branch "1.21.1|26.1.2" --parallel --error-action continue` exits successfully and reports warnings for only the two 26.1.2 Mekanism explicit-source artifacts.
- `cargo test source -- --nocapture` passes the explicit-source, source Git, and source build provenance tests.
- `cargo test source_build -- --nocapture` passes the source-build materializer and source-build portability tests.
- `cargo test source_git_provenance -- --nocapture`
- `cargo test resolver_imports_from_explicit_project_artifact_source -- --nocapture`
- `cargo test` (`103` tests)
- `cargo run -- jar audit-artifacts --branch core --parallel --error-action continue` exits successfully and reports warnings for only 26.1.2 after remote URL provenance was added.
- `cargo run -- run client-puppet --branch core --dry-run --parallel --error-action continue`
- `cargo run -- run client-puppet --branch "1.21.1|26.1.2" --dry-run --error-action continue`
- `cargo run -- run client-puppet --branch "1.21.1|26.1.2" --parallel --error-action continue`
- `cargo run -- run client-puppet --branch "1.21.1|26.1.2" --parallel --error-action continue` exits successfully after the runtime F3+P/pause-screen guard; client-puppet fails if the `SFM_CLIENT_PUPPET_TESTS_PASSED` marker is missing or below the required count.
- `cargo run -- run client-puppet --branch core --parallel --error-action continue`
- `cargo test forge_project_dependency_planning_includes_plain_compile_inputs` locks in the Forge planner behavior that plain project compile inputs such as `mekanism:Mekanism:1.19.2-10.3.8.477:api` are planned instead of being discovered only at compile/run setup time.
- `cargo run -- jar plan --branch "1.19.2|1.20.1" --refresh --parallel --error-action continue` refreshes the affected lockfiles after plain Forge project dependencies were added to planning; both Mekanism API classifier artifacts are now lockfile-backed.
- `cargo run -- run game-test-server --branch core --dry-run --parallel --error-action continue` exits successfully across all core worktrees after the Mekanism API classifier lockfile refresh.
- `cargo run -- run client --branch "feat/1.19.2/draw" --dry-run` selects the feature worktree and fails because that worktree is stale and missing the current versioned dependency script path.
- `cargo run -- jar plan --branch "core && >=1.21.0" --parallel --error-action continue`
- `cargo run -- jar plan --branch "core>=26.0.0" --parallel --error-action continue`
- `cargo run -- run client --branch core --dry-run --parallel --error-action continue`
- `cargo run -- jar audit-artifacts --branch core --parallel --error-action continue --require-portable-artifacts`
- `cargo fmt --check` exits successfully; rustfmt reports only the existing stable-channel warnings for unstable import-format options.
- `cargo clippy --all-targets --all-features -- -D warnings`
- `cargo test` (`104` tests)
- `platform/cli/sfm-propagate-changes/check-all.ps1` exits successfully and runs format, clippy, build, and tests.
- `rg -n "print!|println!|eprintln!|eprint!" src -g "*.rs"` reports only `src/cancellation/mod.rs` for the explicit red Ctrl+C `^C` echo.
- `rg -n --glob "*.md" --glob "!RUN_TARGETS_LOGGING_AND_LOCKING_PLAN.md" -- "--mc" docs` returns no matches; current-facing docs now use `--branch`, while this plan keeps `--mc` references only to document the migration and rejection tests.
- `rg --glob "*.md" -n -- "--hide-logs|hide stdout|--mc <STRING>|Minecraft version filter expression" docs` returns no matches; current-facing help snippets match the live Gradle/server selector and logging options.
- `rg -n -- "should eventually|still reruns most expensive nodes|fingerprint-based up-to-date skipping remains future work|intentionally refuse|missing-executor|After the cache-backed prototype works|Likely Hard Parts|--hide-logs|Minecraft version filter expression" docs/NO_GRADLE_TOOLCHAIN.md docs/AGENTS.md docs/RELEASE_PROCESS.md` returns no matches; the no-Gradle toolchain doc now reflects the current clean-slate path, NeoForm support, fingerprint reuse, and review checklist instead of the old FG-cache prototype.
- After the terminal-output and doc-selector audit fixes, `cargo fmt --check`, `cargo clippy --all-targets --all-features -- -D warnings`, `cargo test`, `platform/cli/sfm-propagate-changes/check-all.ps1`, `git diff --check`, the direct terminal macro scan, and the non-plan docs `--mc` scan all pass.
- CLI parser tests cover branch-based server/GitHub/Modrinth/CurseForge command surfaces and old `--mc` rejection.
- `cargo run -- jar compare --branch 1.19.2 --gradle-jar <synthetic.jar> --rust-jar <synthetic.jar> --report-json <path>` writes a matching single-target report.
- `cargo run -- jar compare --branch "1.19.2|1.19.4" --parallel --error-action continue --gradle-jar <synthetic.jar> --rust-jar <synthetic.jar>` returns the expected multi-target override error.

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
