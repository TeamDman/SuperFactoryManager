# Client execution, developer-world, and static test catalog cleanup plan

**Plan status:** Active  
**Primary implementation root:** `D:\Repos\Minecraft\SFM\repos2\1.19.2`  
**Last updated:** 2026-07-15

## How to update this plan

- `[ ]` Not started
- `[~]` In progress
- `[x]` Complete
- `[!]` Blocked

Update a work item's heading and completion notes together. Record durable evidence beneath the work item it proves; do not add a detached work log.

## Purpose

Unify client automation behind `run client`, separate ordinary GameTest execution from visual puppet previews, expose persistent developer worlds from the IDE-only title menu, and catalog Java tests before the game launches.

## Scope and constraints

In scope: CLI command consolidation, the Arborium-backed static Java catalog, typed client-run properties, developer-world menu actions, documentation, and baseline-first propagation. Out of scope: changing ordinary GameTest semantics, image snapshot comparison, deleting shared sources for unsupported optional dependencies, and replacing runtime annotation discovery.

- Implement first on 1.19.2 and propagate only with `sfm-propagate-changes git merge`.
- Do not invoke Gradle; use `sfm-propagate-changes.exe` for Java validation.
- Use `gix` rather than `git.exe` for CLI repository operations.
- All Java system-property access belongs in `SFMProperties`.
- Normal interactive and developer-world launches must not inherit puppet input, pause, focus, save, or exit side effects.
- Legacy commands are removed rather than aliased.

## Established foundation

- Captioned native puppet screenshot capture, artifact manifests, `@SFMGamePuppet`, and the first walkthrough puppet already exist.
- Current legacy commands are `run client`, `run client-smoke`, `run client-puppet`, and `run game-test-preview`.
- The Java preview fallback is one second and the legacy client GameTest fallback is ten seconds, while Rust defaults to 25 seconds.
- `engine_sources.rs` already enumerates source sets while honoring per-version source exclusions; the catalog must reuse it.
- Generated `@SFMGameTestGenerator` children are runtime-owned and cannot be statically enumerated authoritatively.
- Use published `arborium-java` and its matching patched parser crate, pinned in Cargo; the local Arborium checkout is not a usable path dependency.

## Closed design decisions

| Area | Decision | Acceptance consequence |
| --- | --- | --- |
| Client commands | `run client`, `run client --smoke`, and `run client --puppet <selector>` replace legacy client launch commands. | Old commands are absent from help and rejected. |
| Puppet commands | `puppet run <selector>` is exactly equivalent to `run client --puppet <selector>`. | Both paths construct one typed request and produce identical markers/artifacts. |
| Parameterized puppet | `sfm:game_test_orbit_capture` accepts one exact `--game-test sfm:<id>` and captures an eight-angle overview around the completed structure bounds. | The existing `sfm:move_1_stack_direct_walkthrough` remains the concrete interaction/GUI reference. |
| Title-screen puppet | `sfm:title_screen_capture` captures the loading overlay, the fading title screen, and the settled title screen. | It creates no world, runs no GameTest, and does not open a developer screen. |
| Test commands | `test run|list|show`; `game-test run-client|run-server|list|show`; `puppet list|show`. | Each category has stable, documented discovery output. |
| Timing | First restore Java fallbacks to 25 seconds. Rust then sends explicit typed timing: GameTests hold in-world for 25 seconds; no-flag previews return to title and exit after one second. | Mode tests prove properties and lifecycle. |
| Preview keep-open | `--keep-open N` holds the final preview world for N seconds then returns to title; bare `--keep-open` holds there indefinitely. | Preview scenarios prove omitted, finite, and indefinite values. |
| Catalog | Java is parsed statically; dynamic GameTest generators are identified as dynamic; only puppet selector preflight can reject before launch. | Invalid puppet selection exits before build/userdev. |
| Developer worlds | Timestamp-plus-suffix persistent saves use the existing flat creative hard fixed-weather preset. | No normal save is overwritten or cleaned. |
| Source boundary | Main source creates the world and posts a main-defined request; gametest source subscribes to run all tests. | Main source has no direct gametest dependency. |

## Execution order

```text
timing/property contract
  -> static catalog and puppet preflight
  -> CLI command replacement
  -> Java automation lifecycle
  -> developer-world menu bridge
  -> end-to-end validation, docs, audit, propagation
```

## Phase 1 — Contract and catalog

### [x] 1.1 Write this plan and normalize the timing/property contract

**Work:**

- Create this file, mark the previous interactive-preview plan superseded for command ownership, and preserve it as implementation history.
- Change existing Java preview and client-GameTest delay fallbacks to 25 seconds.
- Define `SFMProperties` types for client mode, validated selectors, finite/indefinite delays, world hold, and title exit. Replace direct Java property reads elsewhere.

**Validation:**

```powershell
rg -n 'System\.(getProperty|setProperty)|Boolean\.getBoolean|Integer\.getInteger|Long\.getLong' platform\minecraft\src
sfm-propagate-changes.exe run compile --branch 1.19.2
```

**Completion notes (2026-07-15):** Created this living plan. `SFMProperties` now owns the only `System.getProperty` call under `platform/minecraft/src`, with `ClientRunMode` as the typed finite mode. Both Java GameTest/puppet fallback delays are 25 seconds. Rust sends explicit preview values: final-world hold `0`/finite/forever plus a one-second title exit. `rg` confirms the property boundary. Java compilation is still pending because an existing userdev Java process holds the Forge artifact lock.

### [x] 1.2 Build the Arborium-backed static Java catalog

**Work:**

- Add pinned published Arborium Java grammar/parser dependencies.
- Reuse source-set enumeration and source exclusions from `engine_sources.rs`.
- Parse package/import/type/method/annotation structure; resolve SFM GameTest, generator, puppet, and JUnit annotations by qualified identity.
- Record canonical ID, category, declaring class/method, source location, and static/dynamic state.
- Derive puppet IDs using the runtime `GamePuppet` suffix and snake-case convention. Diagnose parse errors, malformed puppet definitions, and duplicate IDs with locations.

**Validation:**

```powershell
cd platform\cli\sfm-propagate-changes
cargo test catalog
.\check-all.ps1
```

**Completion notes (2026-07-15):** Added pinned `arborium-java` and its patched parser dependency. The catalog reuses source-set enumeration/source exclusions, resolves imported or qualified annotations, marks generators dynamic, and rejects duplicate IDs or parse errors. Focused fixtures cover import resolution, annotation-looking strings, exclusions, duplicate definitions, malformed Java, and runtime-compatible wildcard matching. `cargo test static_java_catalog --no-fail-fast` passed (4 tests).

### [x] 1.3 Expose catalog commands and preflight puppet selectors

**Work:**

- Add stable sorted `test list|show`, `game-test list|show`, and `puppet list|show`.
- Use `package.Class#method` for JUnit; use SFM-qualified computed IDs for GameTests and puppets.
- List generators as dynamic and leave their run-filter authority to runtime.
- Validate qualified/unqualified comma-separated wildcard puppet selectors before build or launch.

**Validation:**

```powershell
sfm-propagate-changes.exe puppet list --branch 1.19.2
sfm-propagate-changes.exe puppet show sfm:move_1_stack_direct_walkthrough --branch 1.19.2
sfm-propagate-changes.exe puppet run does_not_exist --branch 1.19.2
```

**Completion notes (2026-07-15):** Added `test list|show`, `game-test list|show`, and `puppet list|show`. The rebuilt CLI listed `sfm:move_1_stack_direct_walkthrough`, showed `sfm:move_1_stack_direct`, and rejected `puppet run does_not_exist --branch 1.19.2` before build/userdev.

## Phase 2 — CLI and Java lifecycle

### [x] 2.1 Replace legacy CLI commands with typed execution requests

**Work:**

- Implement `run client [--smoke|--puppet <selector>]`, `puppet run`, test/game-test subcommands, and shared typed request conversion.
- Route both puppet entry points through one engine path.
- Preserve preview dimensions, isolation, artifact validation, and markers; reject incompatible flags rather than ignoring them.
- Remove legacy variants and update their help/documentation references.

**Validation:**

```powershell
sfm-propagate-changes.exe run --help
cd platform\cli\sfm-propagate-changes
cargo test run
.\check-all.ps1
```

**Completion notes (2026-07-15):** `run client` now owns `--smoke` and `--puppet`; `puppet run` delegates through the same request path. `test run` and `game-test run-client|run-server` own their former run variants. `run --help` shows only compile/client/server/data/hotswap. The required formatter/Clippy/build/test checks pass with 278 tests passed and one intentionally ignored network test.

**Parameterized-capture follow-up (2026-07-15):** Added `sfm:game_test_orbit_capture`, supplied by `--game-test sfm:<id>` through the typed `sfm.gamePuppet.gameTest` property. It leaves the Move 1 Stack walkthrough as the explicit reference puppet while making its eight-angle overview pattern reusable for any exact GameTest id.

### [~] 2.2 Refactor Java automation around explicit modes and lifecycle policies

**Work:**

- Dispatch smoke, client GameTests, and puppet previews through typed `SFMProperties.ClientRunMode` values.
- Retain isolated GameTest world creation and 25-second in-world result visibility.
- Apply finite/indefinite preview hold before title return; use a one-second post-title exit by default.
- Restrict cursor release, pause suppression, focus changes, and temporary option changes to automation; restore state without persisting user-option changes.
- Add `sfm:title_screen_capture` as a three-figure title lifecycle/rendering fixture: loading overlay, fading title screen, and settled title screen. Keep title-screen text-editor capture as later work.

**Validation:**

```powershell
sfm-propagate-changes.exe game-test run-client --branch 1.19.2
sfm-propagate-changes.exe puppet run move_1_stack_direct_walkthrough --branch 1.19.2
sfm-propagate-changes.exe puppet run title_screen_capture --branch 1.19.2
```

**Progress notes (2026-07-15):** Implemented explicit puppet final-world hold versus post-title exit timing; omitted preview keep-open returns to title then exits after one second, finite values hold only the final world, and bare values hold indefinitely. Automation temporarily changes `pauseOnLostFocus` without persisting it and restores it after completion. Added `sfm:title_screen_capture`, which declares loading-overlay, fading-title, and settled-title figures without creating a world, running a GameTest, or launching a title-screen developer tool. The title puppet explicitly requires the initial `LoadingOverlay`, captures it, then explicitly requires that same overlay to be absent before it captures the fading title screen. It waits 20 client ticks—the vanilla title-screen fade-in duration—before the settled capture. The helper accepts only an `Overlay` subtype and leaves unrelated overlays available to puppets that intentionally need to document them. `puppet list` and `puppet show sfm:title_screen_capture` discover it successfully. A live `puppet run title_screen_capture --branch 1.19.2 --wait-for-build-lock` passed with all three captioned figures and the root `preview-manifest.json`; it records `clearTransientOverlays: false` and exits after the normal one-second post-title delay.

## Phase 3 — Developer worlds and release proof

### [~] 3.1 Add persistent developer-world menu actions

**Work:**

- Add **Create Dev World** and **Create Dev World + Run GameTests** to the IDE-only developer chooser.
- Generate a unique `sfm_dev_YYYYMMDD_HHmmss[-N]` save with the deterministic developer preset.
- Keep world creation in main source and use a main-defined readiness request consumed by gametest source for the all-GameTests action.
- Preserve normal pause, cursor, saving, and exit behavior.

**Validation:**

```powershell
sfm-propagate-changes.exe run client --branch 1.19.2
sfm-propagate-changes.exe run compile --branch 1.19.2
```

**Progress notes (2026-07-15):** Added the IDE-only **Create Dev World** and **Create Dev World + Run GameTests** actions. They create collision-safe `sfm_dev_YYYYMMDD_HHmmss[-N]` saves with the fixed superflat creative/hard/game-rule preset. Main source posts `SFMDeveloperWorldReadyEvent`; gametest source subscribes and launches all discovered tests, maintaining the one-way source-set boundary. Java compile/manual verification is pending the existing Forge artifact lock.

### [~] 3.2 Validate, document, audit, and propagate

**Work:**

- Validate smoke, JUnit, client/server GameTests, catalog/preflight, preview artifacts, and developer-world behavior.
- Update run-target/toolchain documentation, command examples, and static-catalog limitations.
- Record durable command output and artifact evidence in this plan.
- Run audit and propagate through the SFM CLI only.

**Validation:**

```powershell
sfm-propagate-changes.exe run client --smoke --branch 1.19.2
sfm-propagate-changes.exe test run --branch 1.19.2
sfm-propagate-changes.exe puppet run move_1_stack_direct_walkthrough --branch 1.19.2
cd platform\cli\sfm-propagate-changes
.\check-all.ps1
cargo run -- audit --branch core --version-surfaces
```

**Progress notes (2026-07-15):** Updated project command documentation, release instructions, profiler defaults, active CC plan commands, and marked the original interactive preview plan superseded for command ownership. `audit --branch core --version-surfaces` now handles a tracked-but-deleted working-tree file and completed successfully; it reports the expected unpropagated CLI drift and pre-existing Java surface warnings on later branches. No propagation was attempted with uncommitted baseline work. Java launch validation remains blocked by the active Forge artifact lock.

## Acceptance matrix

| Target | Support status | Required validation | Evidence |
| --- | --- | --- | --- |
| 1.19.2 baseline | Supported | CLI checks, compile, focused client/GameTest/puppet runs | Pending |
| Later propagated versions | Supported where source/toolchain configuration admits the source set | Audit, target compile, focused adapter validation | Pending |
| Version excluding optional source | Shared source retained; feature unavailable through configured exclusion | Exclusion-aware catalog and ordinary compile | Pending |

## Risk register

| Risk | Guardrail |
| --- | --- |
| Static parser diverges from reflection | Catalog is advisory except puppet preflight; fixture-test ID rules against runtime conventions. |
| Dynamic GameTests create false negatives | Label generators dynamic and keep GameTest filtering runtime-owned. |
| Rust/Java timing drifts | One typed property boundary, explicit Rust values, and mode tests. |
| Automation changes user settings or traps input | Automation-only scope, temporary-state restoration, manual normal-client check. |
| Main source gains gametest coupling | Main-defined event/request and gametest-only subscriber. |
| Save collisions or deletion | Timestamp-plus-suffix IDs; no cleanup of ordinary developer saves. |
| Cross-version merge damage | Baseline-first work, source exclusions, SFM CLI propagation, version-surface audit. |
