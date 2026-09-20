# Next release: puppet stabilisation, preview matrix, and release plan

**Plan status:** Active
**Primary implementation root:** `D:\Repos\Minecraft\SFM\repos2\1.19.2`
**Last updated:** 2026-08-23

## How to update this plan

- `[ ]` Not started
- `[~]` In progress
- `[x]` Complete
- `[!]` Blocked

Update a work item's heading and completion notes together. Record durable
decisions, commits, command output, and intentional exceptions immediately
below the task they prove. A phase is complete only when every work item in it
is `[x]`; use `[!]` only with the exact blocker and its unblocking condition.

## Purpose

Take the current SFM release candidate from its committed 1.19.2 puppet baseline
through a publishable, supportable release. This includes propagating and
proving the shared puppet foundation, making artifacts easy to inspect,
establishing a cross-version Move 1 Stack visual matrix, and completing the
repository's formal release process: metadata, datagen, tests, jars, installed
artifact verification, tagging, publishing, and post-publication checks.

The outcome is one deliberate release with a documented user-facing scope,
proven version support, release artifacts whose remote hashes are verified, and
a clear support commitment for the released CC:Tweaked contract. It is not
enough for the baseline to compile or for developer-only screenshots to exist.

Cooperative GameTest checkpoints, text-editor walkthrough transcripts,
ComputerCraft showcase choreography, and synthetic inventory rendering remain
future work. They should build on this released, cross-version preview
foundation rather than create their own timing and artifact conventions.

## Scope

In scope:

- finalising and committing the current 1.19.2 title-puppet/overlay work as a
  propagation-safe baseline change;
- a constrained CLI surface for printing or opening existing puppet artifact
  directories;
- collecting Move 1 Stack preview results from multiple version worktrees into
  a durable, browsable matrix with per-target status and copied artifacts;
- baseline-first propagation, version-surface audit, targeted adapter fixes,
  and focused compile/preview validation;
- documenting real supported, excluded, and blocked targets;
- release-scope review, known-issues/credits review, version/changelog
  preparation, datagen, all-target tests, and jar collection;
- installation-level verification of the built jars, Git tagging/pushing,
  GitHub/CurseForge/Modrinth publication, remote hash validation, and
  milestone cleanup after explicit publishing authorization.

Out of scope:

- changing normal GameTest correctness semantics;
- freezing arbitrary GameTest time or introducing server/client checkpoint
  acknowledgement;
- text-editor transcript/footer rendering;
- synthetic container/inventory rendering or capability-slot-to-GUI-slot
  mapping;
- image-difference snapshot gates, pixel-equality requirements, or a
  replacement of the project's formal release process;
- propagating changes into feature worktrees.

Developer-only puppet infrastructure is not itself a user-facing changelog
entry. It is release evidence. User-visible changes admitted by the release
scope gate, including the CC:Tweaked mutable-handle and Labeler turtle-upgrade
contract where it is supported, require user-facing changelog and compatibility
documentation.

## Established foundation

- The active [client execution cleanup plan](client%20execution%20cleanup%20plan.md)
  owns the client command, property, static-catalog, and Java puppet lifecycle
  contract. It remains the authority for those surfaces.
- `puppet run <selector>` accepts a required branch selector, supports bounded
  parallel target execution, produces captioned native screenshots, and
  publishes `preview-manifest.json` under each target's
  `platform/minecraft/build/sfm-toolchain/artifacts/game-test-preview` root.
- `puppet list|show` statically discovers the class-derived puppet ids before a
  client launches. The current 1.19.2 catalog includes
  `sfm:move_1_stack_direct_walkthrough`, `sfm:game_test_orbit_capture`, and
  `sfm:title_screen_capture`, plus `sfm:title_screen_command_palette`.
- The committed 1.19.2 title puppet was live-validated: it produced captioned
  loading-overlay, fading-title, and settled-title figures. Its manifest says
  `clearTransientOverlays: false`; no broad "no overlays" capture policy is
  being introduced.
- The CLI already uses `open::that` for `cache open` and `repo-root open`, which
  is the established Explorer-launch mechanism to reuse for a constrained
  artifact directory.
- The current normal version worktrees are 1.19.2, 1.19.4, 1.20, 1.20.1,
  1.20.2, 1.20.3, 1.20.4, 1.21.0, 1.21.1, and 26.1.2. The `feat/1.19.2/draw`
  and `feat/1.19.2/mount` worktrees are intentionally excluded from
  propagation.
- The completed [CC:Tweaked mutable-handles plan](cc%20tweaked%20mutation%20plan.md)
  records real-mod focused GameTest evidence for supported runtimes: 1.19.2,
  1.19.4, 1.20, 1.20.1, 1.20.4, and 1.21.1. It records deliberate source
  exclusions where no compatible locked CC:Tweaked runtime exists: 1.20.2,
  1.20.3, 1.21.0, and 26.1.2.
- `gradle.properties` currently declares `mod_version=4.34.0`, while the
  current changelog begins with `4.35.0 PRE`. This is release-candidate intent,
  not proof that 4.35.0 is the final approved version.
- [RELEASE_PROCESS.md](../RELEASE_PROCESS.md) is the authoritative existing
  publication workflow. It already specifies datagen, GameTest, unit-test,
  jar, Prism/dedicated-server, tagging, GitHub, CurseForge, Modrinth, and
  milestone-cleanup steps.

### Viewport/control-surface extension recorded 2026-08-23

The completed Phase 3.3 proves definition-owned **recommended evidence
variants** and one-process matrix expansion. It does not imply that the finite
matrix is also the complete set of exact viewports a responsive puppet may
accept. The
[puppet control surface and rich command arguments plan](puppet%20control%20surface%20and%20rich%20command%20arguments%20plan.md)
owns PV-1/PV-2, which separate a versioned support constraint from finite
declared/preferred evidence cells, audit all definitions, and prove the exact
reported `2000x2000@4` ordinary-document-history run. This plan continues to
own process launch, manifests, copied artifacts, and matrix composition.

That extension also owns the development-only in-game puppet catalog and run
journey. A live interactive run must produce the same viewport/artifact
identity fields as a Rust-CLI bootstrap run, while retaining origin-specific
lifecycle semantics: CLI bootstrap may complete/exit its process, whereas an
interactive title-screen run restores the client and remains usable. Neither
surface may invent a second artifact schema or silently widen a puppet's
support claim.

## Confirmed constraints

- Implement common behavior on 1.19.2 first. Propagate only through
  `sfm-propagate-changes.exe git merge`; preserve newer-version behavior during
  conflict resolution.
- Use `gix` for CLI repository operations. Do not add `git.exe` calls to the
  CLI.
- Do not invoke Gradle. Use `sfm-propagate-changes.exe` commands for Java
  compilation and userdev runs; run `check-all.ps1` after Rust changes.
- A genuine Minecraft-version difference belongs behind an
  `@MCVersionDependentBehaviour` adapter. Do not delete shared puppet sources
  simply because an optional dependency or API is unavailable on one version;
  use the existing source-exclusion/toolchain policy where appropriate.
- A visual matrix records evidence and supports human comparison. It must not
  treat PNG hashes as cross-version snapshot assertions: hashes establish
  artifact identity only.
- `open` is an interactive local-desktop operation. The non-interactive
  `path` operation must remain available for CI, scripts, and remote sessions.
- Publishing, pushing tags/commits, issue/milestone mutation, and remote
  release uploads are external state changes. They require an explicit final
  release approval after all local release gates have passed; this plan does
  not treat implementation authority as publication authority.
- The final release notes describe only gameplay-visible, user-facing,
  localization, documentation, or compatibility changes. Puppets, static
  catalogs, and matrix tooling appear in internal release evidence unless they
  directly change a supported user workflow.
- The unrelated untracked `.antlr` directory on 1.20 and the dirty feature/mount
  worktree must be preserved; this work must not clean, reset, or otherwise
  alter them.

## Design questions to close before implementation

| Question | Required decision | Acceptance consequence |
| --- | --- | --- |
| Artifact CLI | Confirm `puppet artifacts path|open --branch <selector>` as the public surface, or document a better command that follows current CLI naming. | `path` emits only SFM-owned preview roots; `open` opens that exact existing root and never creates or accepts arbitrary destinations. |
| Matrix target set | Treat all ten normal version branches as candidates, then determine eligibility from static discovery, source exclusions, and compile evidence. | The final matrix explicitly labels every candidate as supported, excluded, or blocked with evidence; feature worktrees never appear as matrix targets. |
| Parallelism | Determine the highest reliable local client concurrency, beginning at two parallel clients rather than assuming the existing bare `--parallel` default of ten is GPU-safe. | The matrix command exposes bounded configurable parallelism, preserves all completed target results after failures, and documents the validated default. |
| Matrix presentation | Confirm a copied-artifact collection plus `matrix-manifest.json` and a browsable HTML/Markdown index as the first matrix format. | Each cell links to a valid immutable copied PNG and its source manifest; composite/snapshot image generation remains a later feature. |
| Failure policy | Continue independent targets, report a non-zero overall result when any eligible target fails, and retain logs/artifacts for both successes and failures. | One broken target cannot erase successful captures or conceal its diagnostic state. |
| Release version and scope | Decide whether the prepared `4.35.0 PRE` changelog becomes 4.35.0 and enumerate the exact release-worthy user changes, target branches, and deferred work. | `gradle.properties`, the final changelog heading, release tags, platform metadata, and support statement agree; no developer-only work is marketed as a player feature. |
| CC:Tweaked support commitment | Confirm the supported-runtime set and the stable Lua/turtle contract from the completed mutation plan. | Release notes and manual verification state that regular SFM releases cover all selected targets while CC-specific functionality is present only where its locked runtime is packaged. |
| Release verification breadth | Decide the representative installed-client/server targets for manual CC verification in addition to the formal all-target automated suite. | Each selected artifact is exercised outside userdev; the core manager workflow, changelog display, and supported CC workflow have named evidence. |
| Publication authority and credentials | Confirm the release approver, GitHub/CurseForge/Modrinth credentials, project defaults, and desired visibility/channel immediately before Phase 9. | No tag, push, issue mutation, or upload occurs on stale scope, wrong project, or missing approval. |

## Source and implementation references

| Area | References |
| --- | --- |
| Client/puppet contract | [client execution cleanup plan](client%20execution%20cleanup%20plan.md), `platform/minecraft/src/gametest/java/ca/teamdman/sfm/gametest/puppet/` |
| Puppet CLI | `platform/cli/sfm-propagate-changes/src/cli/puppet.rs`, `platform/cli/sfm-propagate-changes/src/cli/run/` |
| Preview artifacts | `platform/cli/sfm-propagate-changes/src/jar_build/engine_run.rs` |
| Existing Explorer behavior | `platform/cli/sfm-propagate-changes/src/cli/cache/cache_open_cli.rs`, `platform/cli/sfm-propagate-changes/src/cli/repo_root/repo_root_open_cli.rs` |
| Multi-target execution | `platform/cli/sfm-propagate-changes/src/jar_build/engine.rs` |
| Repository workflow | `docs/AGENTS.md` |
| Release procedure | `docs/RELEASE_PROCESS.md` |
| Release metadata | `platform/minecraft/gradle.properties`, `platform/minecraft/src/main/resources/assets/sfm/template_programs/changelog.sfml`, `known_issues.sfml`, and `thank_you.sfml` |
| Release commands | `platform/cli/sfm-propagate-changes/src/cli/github/`, `cli/curseforge/release/`, `cli/modrinth/`, `cli/jar/`, and `platform/pwsh/github-release.ps1` |

## Execution order

```text
commit and baseline proof
  -> artifact path/open contract
  -> matrix collection contract
  -> pre-propagation audit and merge
  -> per-version adapter/exclusion proof
  -> parallel Move 1 Stack matrix and human review
  -> close release scope and prepare metadata
  -> datagen, automated suite, and jar collection
  -> installed-artifact verification
  -> tag, publish, validate remotely, and close the milestone
```

The matrix command is implemented before propagation so its common Rust source
can travel with the rest of the puppet stack. Cross-version client runs occur
only after the baseline has a committed, audit-ready change set. The release
scope closes only after that evidence is available; version bumping, datagen,
and publication cannot run ahead of the chosen scope.

## Phase 1 — Establish a propagation-safe baseline

### [x] 1.1 Finalise and commit the current 1.19.2 title-puppet change

**Work:**

- Review the current 1.19.2 puppet and manifest changes as one logical change.
- Preserve the existing concrete `LoadingOverlay` expectations: capture while
  present, capture immediately after it is absent, then capture after the
  vanilla 20-tick title fade.
- Commit only the reviewed baseline work; do not fold unrelated user changes
  into the commit.

**Validation:**

```powershell
sfm-propagate-changes.exe puppet list --branch 1.19.2
sfm-propagate-changes.exe puppet run title_screen_capture --branch 1.19.2 --wait-for-build-lock
cd platform\cli\sfm-propagate-changes
.\check-all.ps1
git diff --check
```

**Completion criteria:** The baseline commit contains the title puppet and its
typed overlay/wait support; the run produces exactly the three ordered figures
and a manifest with `clearTransientOverlays: false`.

**Completion notes (2026-07-15):** Committed `17189b2cf`
(`feat: add title screen capture puppet`). The change passed static catalog
checks; a live three-figure run; `git diff --check`; and `check-all.ps1`
(`278 passed, 0 failed, 1 ignored`). The artifact set contains the expected
captioned loading-overlay, fading-title, and settled-title PNGs, and its
manifest sets `clearTransientOverlays` to `false`.

### [x] 1.2 Establish merge and worktree preconditions

**Work:**

- Record the baseline commit id and run the version-surface audit before any
  merge.
- Inspect all normal version worktrees with the SFM CLI. Preserve the known
  unrelated 1.20 `.antlr` untracked directory and feature-worktree changes;
  do not reset or remove them.
- Resolve any normal-version worktree condition that would make propagation
  unsafe before starting a merge.

**Validation:**

```powershell
sfm-propagate-changes.exe git status
cd platform\cli\sfm-propagate-changes
cargo run -- audit --branch core --version-surfaces
```

**Completion criteria:** The plan names the baseline commit, the audit output
is recorded, and every normal version target is either safe to merge or has a
specific documented blocker.

**Completion notes (2026-07-15):** Baseline `17189b2cf` is clean. The SFM CLI
status command found 1.19.4, 1.20.1, 1.20.2, 1.20.3, 1.20.4, 1.21.0, 1.21.1,
and 26.1.2 clean. 1.20 has the preserved untracked
`platform/minecraft/src/main/antlr/sfml/.antlr/` directory. The merge
preflight treats untracked files as dirty, so 1.20 is a documented
propagation blocker until its owner either removes it safely or the merge
workflow gains an explicit, safe generated-cache exception. The dirty
`feat/1.19.2/mount` worktree remains excluded.

`audit --branch core --version-surfaces` exited successfully with a baseline
of 1.19.2 and nine target branches. It reported nine existing CLI-surface
warnings (one on each target) and 6,163 Java-surface warnings: 1.19.4=119,
1.20=314, 1.20.1=320, 1.20.2=490, 1.20.3=577, 1.20.4=627, 1.21.0=1,007,
1.21.1=1,033, and 26.1.2=1,676. These are retained Phase 4 audit findings,
not a reason to overwrite newer-version source during propagation.

## Phase 2 — Make preview artifacts discoverable

### [x] 2.1 Close and implement the artifact path/open contract

**Work:**

- Close the Artifact CLI decision gate, preferring
  `puppet artifacts path|open --branch <selector>` if it remains consistent
  with the existing `cache` and `repo-root` command families.
- Resolve the target's existing preview artifact root from the build plan; do
  not accept a user-provided filesystem destination and do not create an empty
  artifact directory solely for `open`.
- Make `path` print a stable absolute path suitable for scripting. Make `open`
  invoke the established `open::that` integration only after validating that
  root exists.
- Add focused Rust tests for target/path resolution and missing-artifact
  diagnostics. Document that `open` is interactive while `path` is CI-safe.

**Validation:**

```powershell
cd platform\cli\sfm-propagate-changes
cargo test puppet_artifacts
.\check-all.ps1
sfm-propagate-changes.exe puppet artifacts path --branch 1.19.2
sfm-propagate-changes.exe puppet artifacts open --branch 1.19.2
```

**Completion criteria:** The CLI prints and opens only the actual 1.19.2
preview artifact directory, errors clearly before a preview exists, and no
arbitrary-path launcher has been added.

**Completion notes (2026-07-15):** Committed `a72f69576`
(`feat: add puppet artifact commands`), which also records the Phase 1.2
precondition evidence. Implemented
`puppet artifacts path|open --branch <single-target-selector>`. The shared
`game_puppet_preview_artifact_root` function now defines the same persistent
root for the run pipeline and CLI. Both commands require exactly one selected
worktree, a pre-existing artifact directory, and its
`preview-manifest.json`; neither accepts a user filesystem path or creates a
directory. `path --branch 1.19.2` printed the absolute root, the deliberately
missing 1.19.4 case emitted its recovery diagnostic, and `open --branch
1.19.2` opened the existing root in Explorer. Focused path/manifest and parser
tests pass; `check-all.ps1` passed with 280 passed, 0 failed, and 1 ignored.

## Phase 3 — Build the durable matrix collector

### [x] 3.1 Define and implement a multi-version preview matrix command

**Work:**

- Add a `puppet matrix <selector>` command (or the closed equivalent) that
  reuses static selector preflight and existing preview execution rather than
  reimplementing the launcher.
- Accept the existing branch selector and preview options, plus an explicit
  bounded parallelism setting and an error-continue policy for independent
  targets.
- Copy each valid target preview into an SFM-owned matrix root, preserving
  branch, puppet, figure number, capture name, source manifest, dimensions,
  BLAKE3 hash, run result, and diagnostic/log locations in
  `matrix-manifest.json`.
- Generate a browsable index with a branch-by-figure grid linking copied PNGs.
  It is a review aid, not a pixel-comparison oracle.
- Reject duplicate branch/figure identities, invalid PNGs, mismatched source
  manifests, and unsafe copy destinations without deleting source artifacts.

**Validation:**

```powershell
cd platform\cli\sfm-propagate-changes
cargo test puppet_matrix
.\check-all.ps1
sfm-propagate-changes.exe puppet matrix move_1_stack_direct_walkthrough --branch 1.19.2 --parallel 1
```

**Completion criteria:** A one-target matrix has a manifest, an index, copied
valid figures, and an explicit successful target record. A deliberately bad
target preserves completed entries and reports its own failure.

**Completion notes (2026-07-15):** Added `puppet matrix <selector>`, which
reuses the existing preview launcher one exact branch at a time, validates its
static selection through that path, then copies validated captures and the raw
source manifest into a timestamped SFM-owned matrix root. `matrix-manifest.json`
records the branch, Minecraft version, source root, copied manifest, capture
dimensions, BLAKE3 hashes, result, and diagnostic log location; `index.html`
renders a branch-by-figure grid. The collector rejects unsafe paths, invalid
PNGs, duplicate/zero figures, and branch/version/selection/test manifest
mismatches without deleting the source artifacts. Until Phase 3.2 provides
two-client calibration evidence, `--parallel 1` is the only accepted matrix
parallelism. Focused collector tests passed (4 passed); `check-all.ps1` passed
with 284 passed, 0 failed, and 1 ignored. A live 1.19.2 Move 1 Stack run wrote
`move_1_stack_direct_walkthrough-20260715-222300` with a successful target,
raw source manifest, browsable index, and 12 copied 1280x807 PNGs.
Implementation commit: `fcfec4fed` (`feat: add puppet preview matrix`).

### [!] 3.2 Calibrate safe client parallelism

**Build-isolation investigation (2026-07-21):** Independent feature worktrees
already use distinct `build/sfm-toolchain/.locks/build-cache.lock` paths, but a
three-way compile probe failed while concurrently opening the shared Forge
`userdev.jar.lock`. This is an artifact lock-file open/retry defect, not proof
that all 1.19.2 builds require serialization. Resolve and validate the lock
hierarchy in the
[parallel worktree build isolation plan](parallel%20worktree%20build%20isolation%20plan.md)
before using build contention to choose client parallelism. GPU, focus, and
memory constraints remain separate reasons to cap simultaneous live clients.

**Blocker (2026-07-15):** `puppet list --branch 1.19.4` completed with an
empty catalog, while the same command on 1.19.2 found the three baseline
puppets. Two concurrent target runs cannot calibrate the shared walkthrough
before Phase 4 propagates it and Phase 4.2 establishes at least two eligible
branches. Keep `puppet matrix` restricted to `--parallel 1` until that
condition is met.

**Work:**

- Start with two concurrent clients on known eligible targets.
- Record CPU/GPU/memory behavior, cursor/focus interference, cache-lock
  behavior, and whether each artifact remains valid.
- Set and document the conservative matrix default. Retain an explicit
  override for capable machines; do not implicitly launch ten clients merely
  because the general engine supports that default.

**Validation:**

```powershell
sfm-propagate-changes.exe puppet matrix move_1_stack_direct_walkthrough --branch <two-eligible-branches> --parallel 2
```

**Completion criteria:** The chosen default is supported by recorded two-target
evidence, and a resource/contention failure produces recoverable matrix status
rather than lost artifacts or hung launches.

### [x] 3.3 Compose the version matrix with definition-owned viewport variants

This is a second matrix axis, not a request to launch another Minecraft client
for every resolution/GUI-scale cell. The authoritative lifecycle is specified
in the
[interactive GameTest preview capture plan](interactive%20gametest%20preview%20capture%20plan.md):
each puppet definition declares a viewport profile, and the Java harness repeats
the complete fresh puppet scenario for the selected variants inside the one
client already launched for that branch target.

The Rust `puppet matrix` collector continues to own branch/version targets and
at most one live process per such target. It consumes each target's
variant-aware preview manifest and produces a composed identity:

```text
(branch, minecraftVersion, puppet, logicalFigure, capture, viewportVariant)
```

Its index should allow a reviewer to fix one logical capture and compare
version rows against viewport/scale columns, or fix one viewport variant and
retain the existing branch-by-figure view. It must display requested window
size, actual GLFW size, framebuffer size, requested/effective GUI scale, and
logical Minecraft screen size. Auto remains a distinct requested column even
when its effective scale equals a numeric column.

Selection is passed once at client startup (`declared`, `preferred`, or one
exact variant). Rust must not duplicate Java profile expansion or send per-cell
REST messages. A target fails on missing/duplicate variant identities,
unsupported silent clamping, inconsistent logical figures, invalid PNGs, or a
failed environment restoration, while retaining valid completed cells.

**Completion criteria:** a one-branch responsive puppet yields one recorded
client process and a browsable multi-variant contact sheet; `--variant
preferred` yields exactly one cell; the existing singleton and multi-version
paths remain backward compatible; and adding a second branch composes axes
without multiplying client launches by viewport count.

Completed on canonical `1.19.2` on 2026-07-22. `puppet run` and `puppet
matrix` accept `declared`, `preferred`, and exact viewport selections; Java
expands the definition-owned profile inside one client and Rust publishes the
variant-aware contact sheet. The merged two-puppet proof produced 30 complete
scenarios and 210 PNGs in one Minecraft process. The generated latest HTML is
`platform/minecraft/build/sfm-toolchain/artifacts/game-test-preview/index.html`.
Cross-version propagation remains a separate Phase 4/release decision.

## Phase 4 — Propagate and prove the version matrix

### [x] 4.1 Propagate the committed baseline through normal version branches

**Work:**

- Run the SFM merge command after Phase 1 preconditions are met. Its normal
  non-interactive behavior must not wait indefinitely for terminal input.
- Resolve conflicts by retaining newer-version behavior and grafting common
  puppet intent. Add narrow `@MCVersionDependentBehaviour` adapters only where
  Minecraft/loader APIs genuinely differ.
- Do not propagate into the two feature worktrees. Do not delete shared source
  to make an unsupported version compile; configure an exclusion and record
  that target status where the source/toolchain policy requires it.
- Run the version-surface audit before and after propagation and record any
  remaining warnings with their ownership.

**Validation:**

```powershell
sfm-propagate-changes.exe git merge
cd platform\cli\sfm-propagate-changes
cargo run -- audit --branch core --version-surfaces
```

**Completion criteria:** Every normal version branch has either received the
baseline commit through the SFM merge workflow or is explicitly marked blocked
or excluded with a technical reason and next action.

**Completion notes (2026-07-15):** `cargo run -- git merge`
completed the normal merge chain from `2bcdc2f8a` (1.19.2 → 1.19.4) through
`4513809eb` (1.21.1 → 26.1.2). All ten normal version worktrees are clean
afterward. The unrelated dirty `feat/1.19.2/mount` worktree remains excluded.
The generated 1.20 `.antlr` cache was preserved and is now ignored by the
narrow propagation preflight exception introduced in `b469689d7`.

Newer-version behavior was retained at each conflict. The propagated puppet
surface is explicitly adapted where the external API changed: 1.20.2/1.20.4+
world-creation and disconnect APIs, 1.21 GameTest builders and render state,
and 26.1.2 registry-backed `GameTestInstance` execution, client input/camera,
world settings/rules, and extracted GUI rendering. The 26.1.2 caption path is
isolated in `PuppetCaptionedScreenshotComposer`; its
`@MCVersionDependentBehaviour` methods use screenshot readback plus
Minecraft's bundled bitmap font rather than removed immediate-mode rendering.
`cargo run -- run compile --branch 26.1.2 --wait-for-build-lock` passed.

The post-merge `cargo run -- audit --branch core --version-surfaces` reports
zero CLI warnings on every normal target. Its retained Java-surface backlog is
1.19.4=103, 1.20=299, 1.20.1=305, 1.20.2=488, 1.20.3=575, 1.20.4=619,
1.21.0=999, 1.21.1=1,025, and 26.1.2=1,674. The focused audit emitted no
unbounded warnings for `SFMGamePuppet*`, `PuppetCaptionedScreenshotComposer`,
or `SFMDeveloperWorldGameTestRunner`; the remaining warnings are the wider
pre-existing version-surface backlog to be addressed separately.

**Follow-up audit (2026-07-16):** The post-preview
`cargo run -- audit --branch core --version-surfaces` pass exited successfully.
All ten normal targets still report zero CLI warnings. Its Java warnings are
the separate established cross-version backlog; the completed puppet preview
work did not introduce a new CLI surface warning.

### [x] 4.2 Validate static discovery, compilation, and previews per eligible target

**Work:**

- For every candidate branch, record whether `puppet list` discovers
  `sfm:move_1_stack_direct_walkthrough`, whether source exclusions deliberately
  remove it, or whether an adapter/fix is required.
- Compile every eligible target via the SFM CLI, then run the focused Move 1
  Stack walkthrough with the standard 1280×720 source viewport.
- Diagnose target failures from their own launch/build logs. Do not reclassify
  a target as unsupported merely because a transient cache, artifact lock, or
  local dependency issue occurred.

**Validation:**

```powershell
sfm-propagate-changes.exe puppet list --branch <branch>
sfm-propagate-changes.exe run compile --branch <branch> --wait-for-build-lock
sfm-propagate-changes.exe puppet run move_1_stack_direct_walkthrough --branch <branch> --width 1280 --height 720 --wait-for-build-lock
```

**Completion criteria:** Each eligible row in the acceptance matrix has
discovery, compile, live preview, and manifest evidence, or an exact retained
failure diagnostic.

**Progress notes (2026-07-15):** Static discovery completed on all ten normal
branches: `puppet list` found
`sfm:game_test_orbit_capture`, `sfm:move_1_stack_direct_walkthrough`, and
`sfm:title_screen_capture` on every target. The compile matrix is now green:
1.19.2, 1.19.4, 1.20, 1.20.1, 1.20.2, 1.20.3, 1.20.4, 1.21.0, 1.21.1, and
26.1.2 all passed `run compile` through the SFM CLI.

The initial compile sweep identified real API transitions rather than target
exclusions. They are retained behind `@MCVersionDependentBehaviour` adapters:
the developer-world and puppet flat-world APIs move to `WorldOptions` in
1.19.4–1.20.1; 1.20.3 requires the parent `Screen`; renderer-backed captions
use JOML/`GuiGraphics` where needed; 26.1.2 retains its GPU screenshot
composer and newer time/game-rule APIs; and CC:Tweaked turtle lookups/equip
operations are hidden behind helpers for direct, descriptor, and
registry-aware API generations. The ordinary puppet flow remains common.

Commits `9ced00f73`, `60330c6f5`, `58c0411c3`, and `909e70738` establish the
baseline boundaries; version-specific adapter commits were resolved through
the normal propagation chain without overwriting newer behavior. Audit
precision commits `d23ab6793` and `c37274f32` ignore import-only hunks and
correctly cover a hunk spanning adjacent annotated methods. The latter passed
`cargo clippy --all-features -- -D warnings` and the full Rust suite (287
passed, 0 failed, 1 ignored). The final focused audit reports zero CLI
warnings and no unbounded warning for the developer-world launcher, puppet
runtime, or CC:Tweaked turtle GameTest. Its separate historical Java backlog
is 3,816 warnings: 1.19.4=51, 1.20=221, 1.20.1=224, 1.20.2=289,
1.20.3=329, 1.20.4=352, 1.21.0=615, 1.21.1=637, and 26.1.2=1,098.

All normal worktrees are clean. The unrelated dirty `feat/1.19.2/mount`
worktree remains excluded.

**Completion notes (2026-07-16):** The live proof was collected by the final
matrix run in 4.3 rather than inferred from compilation. Every normal target
discovered the walkthrough, compiled through the SFM CLI, and completed the
live puppet successfully with a 12-figure captioned artifact set and preview
manifest. The 1280×720 requested viewport produces 1280×807 PNGs because the
caption compositor adds an 87-pixel top strip.

### [x] 4.3 Produce and review the all-version Move 1 Stack matrix

**Work:**

- Run the matrix command over all eligible normal version branches at the
  calibrated concurrency.
- Open the matrix root using the Phase 2 command and review the index/figures.
- Record intentional renderer/UI/version differences separately from failures
  such as missing figures, incorrect caption numbering, a wrong screen, failed
  GameTest, or missing manifest metadata.
- Link the final matrix root and review outcome below this task.

**Validation:**

```powershell
sfm-propagate-changes.exe puppet matrix move_1_stack_direct_walkthrough --branch <eligible-version-selector> --parallel <validated-count>
sfm-propagate-changes.exe puppet artifacts open --branch 1.19.2
```

**Completion criteria:** One durable matrix contains a status row and copied
Move 1 Stack artifact set for every eligible target. Human review has recorded
whether differences are intentional, adapter defects, or test/launcher
failures.

**Completion notes (2026-07-16):**
`cargo run -- puppet matrix move_1_stack_direct_walkthrough --branch core
--width 1280 --height 720 --parallel 1 --error-action continue
--wait-for-build-lock` completed in 668.6 seconds with exit code 0. The
durable matrix root is
`platform/minecraft/build/sfm-toolchain/artifacts/game-test-preview-matrices/move_1_stack_direct_walkthrough-20260716-171552`;
it contains `matrix-manifest.json`, `index.html`, and one copied 12-figure
artifact set for each of 1.19.2, 1.19.4, 1.20, 1.20.1, 1.20.2, 1.20.3,
1.20.4, 1.21.0, 1.21.1, and 26.1.2 (120 figures total). All status rows are
`succeeded`.

Human review inspected the ordinary overview/manager flow plus the 1.20.4
manager and 26.1.2 source-barrel figures. The screenshots show the expected
screens, Figure numbering, and post-transfer inventory state. Follow-up review
found two caption-rendering defects, tracked by 4.4: the 1.20–1.21.1
`GuiGraphics.drawString` default overload adds an unwanted shadow, while the
26.1.2 bitmap compositor advances an empty space glyph by only one pixel.

### [ ] 4.4 Contain caption font behavior and prove the renderer seam

**Work:**

- Add a single tracked `platform/minecraft/sfm.audit_rules` policy file, kept
  byte-identical through normal propagation. The Rust audit reads this file on
  every target; policy owners, Minecraft classes, member names, and descriptors
  must not be baked into Rust. Rules use source/development names only—never
  obfuscated names—even when the per-version mapping/classpath implementation
  differs.
- Use an access-transformer-like, line-oriented grammar with comments and one
  rule per line. `DENY CALL <owner> <member> <descriptor-or-*>` selects a
  forbidden callee; `PERMIT CALLER <owner> <member-or-*> <descriptor-or-*>`
  selects a trusted caller context that may invoke a matching denied callee.
  A permit is intentionally a caller rule: permitting an invocation of an SFM
  wrapper would not authorize that wrapper's own direct call to `Font` or
  `GuiGraphics`. Exact JVM descriptors are supported when resolvable;
  `*` is required for shared rules whose overload differs between Minecraft
  versions. Example initial policy shape:

  ```text
  DENY CALL net.minecraft.client.gui.Font draw *
  DENY CALL net.minecraft.client.gui.Font drawShadow *
  DENY CALL net.minecraft.client.gui.Font drawInBatch *
  DENY CALL net.minecraft.client.gui.GuiGraphics drawString *
  DENY CALL net.minecraft.client.gui.GuiGraphicsExtractor text *
  PERMIT CALLER ca.teamdman.sfm.client.screen.SFMFontUtils * *
  ```

- Make `SFMFontUtils` the sole home for version-specific text drawing and
  caption-font metric behavior. Puppet screenshot code may request a caption
  render, but must not select a Minecraft text-rendering overload or implement
  a version-specific glyph advance itself.
- Extend the Rust `audit` command's Arborium Java analysis with a purpose-built
  lexical type resolver; do not introduce a JavaParser/JDK helper. The resolver
  owns only the source-level facts needed by this policy: package/imports,
  declared field and parameter types, lexical local scopes, explicit local
  types, and recursively simple `var` initializers such as an identifier,
  `this.field`, a qualified field, a cast, or `new Type(...)`. It deliberately
  does not attempt reflection, arbitrary method-return inference, macro-like
  generation, or a complete Java compiler model.
- Derive suspicious method names from `DENY CALL` rules, then first collect
  only matching method invocations from the AST. Resolve a receiver lazily only
  after its method name is suspicious, using imports/qualified names and the
  lexical resolver. Do not flag unrelated `GuiGraphics` primitives such as
  `fill` or `blit`, because they have no deny rule.
- A resolved call matching a denied callee outside a permitted caller is a
  violation. An unresolved receiver of a denied method name is also a distinct
  warning, rather than an escape hatch through `var`, aliasing, or an
  incomplete external classpath. The warning must identify branch, path, line,
  invoked method, rule, and either the resolved receiver type or its unresolved
  expression.
- Add focused Rust fixtures for imported and fully-qualified receiver types,
  static/instance call forms, method parameters, local fields, shadowing,
  `var a = this.font`, `var a = graphics`, allowed calls inside
  `SFMFontUtils`, unrelated same-named methods, unresolved suspicious calls,
  rule parsing, descriptor wildcards, and permit precedence. These fixtures
  define the intentionally bounded inference contract.
- Run the audit before the refactor and retain its warnings as the expected
  failing baseline. It must report the direct legacy `Font.draw` caption path
  and the 1.20–1.21.1 direct `GuiGraphics.drawString` paths.
- Move the pre-26.1.2 `FormattedCharSequence` caption draw seam into
  `SFMFontUtils`, passing `shadow=false` explicitly. This removes the shadow
  introduced by the five-argument `GuiGraphics.drawString` overload, whose
  default is `true`.
- Move the 26.1.2 raw-image bitmap caption rasterizer and its advances into the
  26.1.2 `SFMFontUtils` seam. Use the same effective font metrics as the
  wrapped caption layout, including the real blank-space advance; do not infer
  a space width from opaque pixels. The current empty-glyph fallback of one
  pixel is the cause of compressed word spacing.
- Propagate the common baseline change normally, retaining each version's
  narrow `@MCVersionDependentBehaviour` implementation. Do not use raw Git or
  direct Gradle for verification.

**Validation:**

```powershell
cd platform\cli\sfm-propagate-changes
cargo test font_render_audit --no-fail-fast
cargo run -- audit --branch core
sfm-propagate-changes.exe puppet matrix move_1_stack_direct_walkthrough --branch core --width 1280 --height 720 --parallel <validated-count> --wait-for-build-lock
cargo run -- audit --branch core
```

**Completion criteria:** The pre-refactor audit has recorded the known direct
calls; the post-refactor audit emits no disallowed `Font`/`GuiGraphics` text
draw warnings on normal branches; the caption implementation has no
version-specific font behavior outside `SFMFontUtils`; and refreshed previews
visually confirm unshadowed legacy captions and correctly spaced 26.1.2 text.

**Known defect baseline (2026-07-16):** `SFMFontUtils` already forwards its
explicit `shadow` parameter correctly. The defects occur because the caption
renderers bypass it. 1.19.2/1.19.4 call `Font.draw` directly; 1.20–1.21.1
construct `GuiGraphics` directly and select the no-boolean `drawString`
overload, which defaults to shadowed text; and 26.1.2 directly scans
`ascii.png`, returning an advance of one for every empty glyph, including a
space. The audit must deliberately show the first two categories before the
refactor; the 26.1.2 compositor requires the separate ownership review above
because it does not invoke `Font` or `GuiGraphics` directly.

**Audit architecture decision (2026-07-16):** The audit remains Rust-native
alongside the project's mapping/classpath knowledge. Arborium supplies syntax,
not general Java semantics; the existing static catalog resolves imports only
for annotation discovery. The renderer rule therefore adds a small,
policy-specific JavaSymbolSolver equivalent instead of claiming comprehensive
type inference or paying for an external Java semantic runtime. Its work is
lazy: method names select candidate invocations first, and only candidates
receive scoped receiver resolution. `sfm.audit_rules` is the authoritative
declarative policy; Rust implements only its grammar, matching, scoped
resolution, and diagnostics.

**Audit foundation (2026-07-16):** `platform/minecraft/sfm.audit_rules` now
declares the Font/GuiGraphics policy, which `audit` loads for every SFM Java
source set, including GameTest sources. This policy is
enabled by default for `audit`; `--no-font-render-surface` exists only for a
focused diagnostic run. The Rust resolver
handles imports, fields, parameters, lexical locals, `var` aliases,
same-package/static types, and casts; it reports an unresolved receiver
expression rather than treating incomplete source knowledge as permission.
Focused fixtures cover rule parsing, permits, descriptor matching, imported
and static types, shadowing, aliases, unrelated same-named methods, and
GameTest scanning. The initial 1.19.2 audit reports 26 expected direct or
unresolved renderer calls, including the known caption bypass at
`SFMGamePuppetMinecraftRuntime.java:372` (`minecraft.font.draw`). Caption
refactoring remained pending while the audit foundation was propagated.
The post-propagation `--branch core` pre-refactor audit initially reported 263 warnings:
it records the legacy caption calls on 1.19.2/1.19.4 and resolved
`GuiGraphics.drawString` caption violations on 1.20 through 1.21.1. Arborium
also reports two visible parse gaps instead of aborting the audit—CC:Tweaked's
1.21.0 `ComputerCraftLuaNetworkPeripheralGameTest.java:148` and 26.1.2's
`TestBarrelTankContainerMenu.java:70`. The audit retains those warnings but
continues walking Arborium's recovered AST, so a parse gap cannot silently
skip any recoverable denied call in that source file. The final fixture set
also proves fully-qualified owners, source-declared qualified fields,
shadowing, exact-descriptor versus wildcard matching, and permit precedence.
The renderer refactor must leave no direct-renderer findings.

**Inherited screen-call resolution (2026-07-16):** The initial lexical
resolver treated unqualified `drawString(...)` calls in `Screen` subclasses
as an unresolved receiver. That was not an acceptable diagnostic: in 1.19.x
the inherited `GuiComponent.drawString` surface calls `Font.drawShadow`, so it
is a real bypass of the `SFMFontUtils` seam. The shared policy now denies the
declared `net.minecraft.client.gui.screens.Screen drawString` surface. The
resolver captures each SFM class's declared superclass and its direct method
signatures, then resolves unqualified, `this.`, and `super.` calls to that
superclass when no compatible local method hides the name. It emits a definite
violation with a wildcard descriptor when overload details are unnecessary to
the rule, rather than labelling the call unresolved. Focused fixtures cover
all three receiver forms and prevent a local same-named helper from being
misclassified. The audit therefore identifies both the input-diagnostics and
draw-canvas legacy screen helpers as remediation work, without claiming a
general Java type solver.

## Phase 5 — Close the release contract and prepare metadata

### [~] 5.1 Decide the release scope, version, and support statement

**Work:**

- Reconcile the committed baseline changes, the current `4.35.0 PRE`
  changelog, and the completed CC:Tweaked mutation plan into one explicit
  release candidate. Decide the final version rather than inferring it from
  the preliminary heading.
- Enumerate each user-facing change that will ship and map it to its changelog
  entry, source/test evidence, and affected Minecraft versions. Remove or
  defer any changelog promise that lacks a committed, supportable change.
- State the release support policy: normal SFM artifacts for each selected
  version target, plus CC:Tweaked mutable handles and the `sfm` turtle
  upgrade only on the six locked-runtime targets recorded in the acceptance
  matrix.
- Review related issues and the release milestone with the project maintainer.
  Fixed issues must be closed or marked `implemented awaiting release`; the
  release scope must not silently absorb unrelated feature-worktree work.
- Freeze scope before version bumping. A discovered release blocker returns to
  the relevant implementation phase and reopens this task's review.
- Treat the dedicated [Draw editor layers, commands, and canvas
  workspace plan](draw%20editor%20document%20regions%20and%20commands%20plan.md)
  as a release-scope dependency. Either complete its approved release floor or
  explicitly defer it and rewrite the release headline/changelog before this
  task can close.

**Validation:**

```powershell
git log --oneline origin/1.19.2..1.19.2
rg -n '^---- |TODO|PRE' platform\minecraft\src\main\resources\assets\sfm\template_programs\changelog.sfml
sfm-propagate-changes.exe git status
```

**Completion criteria:** The maintainer has approved one final version and a
written release scope. Every player-facing entry is backed by committed code
and evidence; developer-only puppet work is listed only as release evidence.

**Progress notes (2026-07-16):** The release candidate is prepared as
`4.35.0`, while `gradle.properties` deliberately remains at `4.34.0` until
scope is frozen. The prepared user-facing scope is the documented CC:Tweaked
surface (mutable disk/label-gun handles, cable-network peripherals, structured
  item details, turtle inventory support, and the `sfm` turtle peripheral) plus
the committed draw-canvas improvements listed in the prepared changelog.

The three developer-only puppet entries were removed from the 4.35 changelog:
persistent developer-world menu actions, captioned preview captures, and
cursor behavior. They remain release evidence, not player-facing release
notes. The candidate support table is ordinary SFM on all ten normal targets,
with the CC:Tweaked surface packaged only on 1.19.2, 1.19.4, 1.20, 1.20.1,
1.20.4, and 1.21.1. The scope, final version, and issue/milestone disposition
still require explicit maintainer approval before the version bump and later
release steps.

The maintainer subsequently designated the Draw editor as the intended
headline of this release and requested dynamic primary/reference layers,
one universal contextual command palette, user-configurable canvas-native
action buttons, grammar/template references, drawing tools, and a persistent
canvas/disk projection boundary. That reopens the prepared scope: Phase 5.1
cannot be completed until the linked Draw plan closes its release-floor gates
and provides committed cross-version evidence, or the maintainer explicitly
defers that work and approves narrower release wording.

### [ ] 5.2 Update player-facing release metadata and propagate it

**Work:**

- Update `mod_version` on 1.19.2 to the approved version and make the first
  changelog heading match exactly. Remove the `PRE` marker and every TODO from
  the released heading/body.
- Deliberately review `known_issues.sfml`: remove fixed issues and add any
  release-relevant caveat discovered by the matrix or verification work.
- Deliberately review `thank_you.sfml` against its donor sources before making
  release credits; this is a human review, not an inference from local state.
- Commit the reviewed release-preparation change on 1.19.2, then propagate it
  through normal version branches using the SFM merge workflow. Preserve each
  branch's generated files and newer-version behavior.

**Validation:**

```powershell
rg -n '^mod_version=' platform\minecraft\gradle.properties
rg -n '^---- |TODO|PRE' platform\minecraft\src\main\resources\assets\sfm\template_programs\changelog.sfml
sfm-propagate-changes.exe git merge
sfm-propagate-changes.exe git status
```

**Completion criteria:** Every selected normal version branch has the approved
version/changelog/known-issues metadata through propagation, and the release
heading contains no pre-release or TODO text.

## Phase 6 — Regenerate and execute automated release proof

### [ ] 6.1 Regenerate version-specific resources and preserve merge stability

**Work:**

- Run datagen for the release target set through the SFM CLI. Review the
  generated-only changes proposed by its auto-commit workflow; accept only
  expected generated resources.
- If datagen exposes source/resource drift, correct the source on 1.19.2,
  propagate, and rerun datagen rather than manually staging arbitrary generated
  files.
- Run a follow-up merge so every branch retains its own generated resources
  and the release graph is merge-stable.

**Validation:**

```powershell
sfm-propagate-changes.exe run data --parallel --branch core
sfm-propagate-changes.exe git status
sfm-propagate-changes.exe git merge
sfm-propagate-changes.exe git status
```

**Completion criteria:** All expected generated resources are committed by the
SFM-generated-change workflow, no unexpected generated diff remains, and a
post-datagen merge leaves every normal version worktree clean.

### [ ] 6.2 Run the release automated test suite on the final sources

**Work:**

- Run server GameTests and Java unit tests for all core targets after datagen
  and the final release-preparation merge.
- Treat the completed CC plan's focused tests as historical evidence only;
  rerun the final all-target GameTest suite so its supported and deliberately
  excluded CC configurations are exercised by the release sources.
- Run Rust formatter/lint/build/test checks after any CLI change introduced by
  the artifact or matrix work.
- Record target-specific failures and return to the owning task; do not build
  or publish jars from a suite with untriaged failures.

**Validation:**

```powershell
sfm-propagate-changes.exe game-test run-server --parallel --branch core
sfm-propagate-changes.exe test run --parallel --branch core
cd platform\cli\sfm-propagate-changes
.\check-all.ps1
```

**Completion criteria:** The final source revision has passing required
GameTests and JUnit tests for every release target, with intentional
source-excluded compatibility cases recorded rather than mistaken for a test
failure.

## Phase 7 — Build and stage distributable artifacts

### [ ] 7.1 Build, collect, and inventory release jars

**Work:**

- Build each release target without Gradle, using the final clean-slate
  toolchain plan and portable locked artifacts.
- Clear the SFM-owned collection directory, collect the newly built jars, and
  inspect the resulting set against the approved version/target scope.
- Retain build diagnostics, jar paths, and checksums with the release evidence.
  A jar from an earlier source revision, a wrong version, or an omitted target
  is a release blocker.

**Validation:**

```powershell
sfm-propagate-changes.exe jar build --parallel --branch core
sfm-propagate-changes.exe jar dir clean
sfm-propagate-changes.exe jar collect
sfm-propagate-changes.exe jar list
```

**Completion criteria:** The collected jar directory contains exactly the
approved release artifacts, each derived from the final target source and
version metadata.

### [ ] 7.2 Stage built jars in isolated verification installations

**Work:**

- Configure the Prism instances directory if it has not been configured on the
  release machine, then synchronize tracked verification instances with the
  pinned loader chosen by the clean-slate build plans.
- Update tracked dedicated servers from the collected jars.
- Keep verification instances and servers separate from user development
  worlds. Record loader/JDK fallback decisions if pinned Prism metadata is not
  resolvable.

**Validation:**

```powershell
sfm-propagate-changes.exe client sync --branch core --loader pinned
sfm-propagate-changes.exe jar update-servers
```

**Completion criteria:** Every selected installed verification client and
server contains the matching final jar and an explicitly recorded loader/JDK
selection.

## Phase 8 — Verify the packaged mod as a player would

### [ ] 8.1 Exercise the core SFM workflow outside userdev

**Work:**

- Launch the staged Prism clients and dedicated servers according to the
  repository release process. Join a local dedicated server from the matching
  client for each selected verification target.
- Build the normal manager/chest/disk/label-gun setup from scratch, use the
  bundled **A Simple Program** example, and prove one real item transfer.
- Run `/sfm changelog` and verify the final heading/content in-game. Confirm
  that no TODO/pre-release marker is visible and that new known-issue text is
  accurate.
- Record the tested client/server version, jar filename/hash, loader, result,
  and any issue found. A manual failure reopens the corresponding release
  task.

**Validation:**

```powershell
sfm-propagate-changes.exe client launch --branch popular
sfm-propagate-changes.exe server launch --branch popular
```

**Completion criteria:** The packaged jars—not userdev classes—have passed the
core manager workflow and in-game release-text review on every selected
verification target.

### [ ] 8.2 Exercise the released CC:Tweaked contract on its supported runtimes

**Work:**

- Use the Phase 5 representative target decision to test at least the oldest
  and newest supported CC runtime from real packaged client/server instances.
- Attach a computer to an SFM cable network, obtain managers through the
  released peripheral, acquire a disk/label-gun handle, mutate and save a
  program/labels, and confirm normal SFM state/persistence behavior.
- Install and use the `sfm:labeler` turtle upgrade with a selected label gun to
  exercise the flat `sfm` peripheral, inspect contiguous discovery, and bulk
  edit labels. Verify a turtle inventory can take part in an SFM transfer.
- Confirm that CC-specific functionality is absent only on the documented
  source-excluded target set, while the ordinary SFM release continues to work
  there.

**Validation:**

```powershell
sfm-propagate-changes.exe client launch --branch <approved-cc-verification-selector>
sfm-propagate-changes.exe server launch --branch <approved-cc-verification-selector>
```

**Completion criteria:** The stable CC Lua/turtle contract has packaged-artifact
evidence on the approved supported endpoints, and the documented unsupported
versions have no misleading release claim.

## Phase 9 — Tag and publish the approved release

### [ ] 9.0 Control guard — acquire explicit release authority

**Work:**

- Stop at this guard after Phases 1–8 have their recorded validation evidence.
  Do not run any Phase 9 command that creates or moves a tag, changes a remote,
  creates a GitHub release, or uploads to CurseForge or Modrinth.
- Present the maintainer with the exact release packet: final version and
  selected branches, changelog/known-issues scope, final commit(s), tag names,
  collected jar manifest and BLAKE3 checksums, Phase 6–8 evidence, and every
  external mutation proposed by Phases 9.1 and 9.2.
- Obtain an explicit affirmative authorization that identifies which of these
  actions are approved: local final merge/tag preparation, remote tag/commit
  push, GitHub release creation, CurseForge upload, and Modrinth upload. A
  partial authorization limits all following work to its approved actions;
  missing authorization is not implicit approval to continue.
- Record the maintainer's authorization, its exact scope, the approved release
  commit/version, and any withheld platform in this task's completion notes.
  If approval is absent or withdrawn when this guard is reached, mark this
  task `[!]` with that exact condition and stop before external mutation.

**Validation:**

- Human authorization is explicit in the release conversation and matches the
  release packet recorded here; no command output substitutes for approval.

**Completion criteria:** A maintainer has explicitly authorized the named
release packet and each approved external mutation. No Phase 9.1 or 9.2 action
starts before this task is `[x]`.

### [ ] 9.1 Perform the final local release gate and tag the exact source

**Work:**

- Re-run merge/status/audit after all generated changes and verification
  corrections. Confirm every normal release worktree is clean and points at
  the approved version.
- Use only the authority recorded by completed task 9.0. Create tags using the
  SFM CLI's `<mod_version>-<mc_version>` policy, then push tags and commits
  only if those remote mutations were explicitly approved.
- Record the final commit, tag set, jar manifest/checksums, approval, and
  release scope in this task's completion notes before publishing artifacts.

**Validation:**

```powershell
sfm-propagate-changes.exe git merge
sfm-propagate-changes.exe git status
cd platform\cli\sfm-propagate-changes
cargo run -- audit --branch core --version-surfaces
sfm-propagate-changes.exe git tag
sfm-propagate-changes.exe git push --tags
sfm-propagate-changes.exe git push
```

**Completion criteria:** Explicit authorization is recorded, tags identify the
exact validated source, tags and commits are visible on the intended remote,
and no uncommitted release material remains.

### [ ] 9.2 Publish and validate GitHub, CurseForge, and Modrinth releases

**Work:**

- Confirm project defaults, release channel/visibility, and credentials without
  printing secrets. Use the collected jar directory only after Phase 9.1 and
  only for the platforms explicitly approved by task 9.0.
- Create or update the GitHub release and upload the collected jars.
- Run remote metadata checks before uploading to CurseForge and Modrinth, then
  publish each approved jar through their release commands.
- Validate remote downloadable files against local jars by hash. A failed
  upload or hash mismatch is a release incident: stop further publication,
  preserve the evidence, and correct/amend deliberately.

**Validation:**

```powershell
sfm-propagate-changes.exe github release now --branch core
sfm-propagate-changes.exe curseforge release check --branch core
sfm-propagate-changes.exe curseforge release now --branch core
sfm-propagate-changes.exe curseforge release validate --branch core
sfm-propagate-changes.exe modrinth release check --branch core
sfm-propagate-changes.exe modrinth release now --branch core
sfm-propagate-changes.exe modrinth release validate --branch core
```

**Completion criteria:** Every approved jar has an intended GitHub asset and
remote CurseForge/Modrinth release entry, and remote hashes match the local
collected release jars.

## Phase 10 — Close the milestone and begin support

### [ ] 10.1 Complete release follow-through and preserve support evidence

**Work:**

- Run the milestone cleanup workflow only after all intended publications are
  publicly available. Close or relabel issues according to the reviewed scope.
- Archive the final matrix root, test/verification records, jar inventory,
  release URLs, remote hash-validation output, and CC compatibility statement
  in this plan or a linked release record.
- Treat the documented CC Lua/turtle contract and the release target matrix as
  ongoing support commitments. Triage post-release reports against those
  declared surfaces rather than expanding the contract implicitly.

**Validation:**

```powershell
pwsh -File ./platform/pwsh/milestone-cleanup.ps1
sfm-propagate-changes.exe curseforge release validate --branch core
sfm-propagate-changes.exe modrinth release validate --branch core
```

**Completion criteria:** The release milestone accurately reflects public
availability, durable evidence is linked, and support owners can determine
what behavior/version combinations are promised by this release.

## Acceptance matrix

Every normal version branch remains a release candidate until Phase 5 closes
scope. "CC packaged" means the completed mutable-handles plan has a compatible
locked CC:Tweaked runtime; it does not replace ordinary SFM verification on
the other targets.

| Target | Initial status | CC:Tweaked release surface | Required release proof | Evidence |
| --- | --- | --- | --- | --- |
| 1.19.2 | Baseline candidate | Packaged and supported | catalog, compile, preview matrix, datagen, full suite, jar, installed core workflow, CC endpoint verification | Catalog, compile, and 12-figure preview matrix succeeded (2026-07-16); release proof remains |
| 1.19.4 | Candidate | Packaged and supported | catalog, compile, preview matrix, datagen, full suite, jar, installed core workflow; CC suite evidence and representative-endpoint coverage | Catalog, compile, and 12-figure preview matrix succeeded (2026-07-16); release proof remains |
| 1.20 | Candidate; unrelated `.antlr` directory preserved | Packaged and supported | catalog, compile, preview matrix, datagen, full suite, jar, installed core workflow; CC suite evidence and representative-endpoint coverage | Catalog, compile, and 12-figure preview matrix succeeded (2026-07-16); release proof remains |
| 1.20.1 | Candidate | Packaged and supported | catalog, compile, preview matrix, datagen, full suite, jar, installed core workflow; CC suite evidence and representative-endpoint coverage | Catalog, compile, and 12-figure preview matrix succeeded (2026-07-16); release proof remains |
| 1.20.2 | Candidate | Deliberately source-excluded: no compatible locked runtime | catalog, compile, preview matrix, datagen, full suite, jar, installed core workflow; no CC release claim | Catalog, compile, and 12-figure preview matrix succeeded (2026-07-16); release proof remains |
| 1.20.3 | Candidate | Deliberately source-excluded: no compatible locked runtime | catalog, compile, preview matrix, datagen, full suite, jar, installed core workflow; no CC release claim | Catalog, compile, and 12-figure preview matrix succeeded (2026-07-16); release proof remains |
| 1.20.4 | Candidate | Packaged and supported | catalog, compile, preview matrix, datagen, full suite, jar, installed core workflow; CC suite evidence and representative-endpoint coverage | Catalog, compile, and 12-figure preview matrix succeeded (2026-07-16); release proof remains |
| 1.21.0 | Candidate | Deliberately source-excluded: no compatible locked runtime | catalog, compile, preview matrix, datagen, full suite, jar, installed core workflow; no CC release claim | Catalog, compile, and 12-figure preview matrix succeeded (2026-07-16); release proof remains |
| 1.21.1 | Candidate | Packaged and supported | catalog, compile, preview matrix, datagen, full suite, jar, installed core workflow; CC suite evidence and representative-endpoint coverage | Catalog, compile, and 12-figure preview matrix succeeded (2026-07-16); release proof remains |
| 26.1.2 | Candidate | Deliberately source-excluded: no compatible locked runtime | catalog, compile, preview matrix, datagen, full suite, jar, installed core workflow; no CC release claim | Catalog, compile, and 12-figure preview matrix succeeded (2026-07-16); release proof remains |
| `feat/1.19.2/draw` | Excluded feature worktree | Not assessed by this release plan | No propagation, matrix, or release inclusion without a new scope decision | Intentionally excluded |
| `feat/1.19.2/mount` | Excluded dirty feature worktree | Not assessed by this release plan | No propagation, matrix, or release inclusion without a new scope decision | Intentionally excluded |

## Risks and guardrails

| Risk | Guardrail |
| --- | --- |
| Dirty worktrees cause accidental loss or merge contamination | Preflight with `sfm-propagate-changes git status`; commit only baseline-owned changes; never reset/clean unrelated worktrees. |
| Newer-version code is overwritten during propagation | Baseline-first SFM merge, keep-existing conflict policy, and before/after version-surface audits. |
| A version lacks an API or optional dependency | Preserve shared source; use existing source exclusions or a narrow annotated adapter, then mark the target accurately. |
| Parallel clients exhaust GPU/memory, steal focus, or contend on artifacts | Start at two clients, make concurrency explicit, wait for SFM build locks, and retain per-target diagnostics/results. |
| Matrix hides partial failures | Continue independent targets, emit a non-zero overall status, and write a status record even when a target produces no figure. |
| Artifact opener launches an unsafe path | Resolve only known SFM-owned artifact roots from the build plan; provide `path` for non-interactive environments. |
| Visual differences become false snapshot failures | Preserve hashes for identity, require human review for cross-version visual differences, and defer pixel comparison to a later plan. |
| Release scope grows while verification is underway | Close scope/version in Phase 5 before metadata, datagen, and builds; a new feature reopens scope and repeats affected proof. |
| Player-facing metadata disagrees with the built jars | Derive the final version from the approved `gradle.properties` value, require matching changelog heading, and inspect the collected jar inventory before tagging. |
| Generated resources differ per branch | Use the SFM datagen auto-commit workflow, review generated-only changes, and merge again without overwriting branch-owned `src/generated` content. |
| A userdev pass masks a packaged-jar defect | Synchronize collected jars into Prism and dedicated-server verification installations, then perform the core and CC manual workflows outside userdev. |
| CC support is overclaimed on excluded targets | Carry the completed locked-runtime matrix into changelog/release notes and test supported endpoint behavior from packaged artifacts. |
| Publication targets, credentials, or hashes are wrong | Require explicit release authorization, run platform metadata checks before upload, and validate CurseForge/Modrinth downloads by hash after upload. |
| A tag or push identifies unreviewed source | Re-run merge/status/audit at the final gate and record the commit/tag/jar manifest before any remote mutation. |

## Overall completion criteria

- [x] The baseline title-puppet change is committed independently and remains
  live-validated (`17189b2cf`).
- [ ] `puppet artifacts path|open` (or the documented final equivalent) safely
  locates existing preview artifacts.
- [x] A matrix command produces validated copied artifacts, machine-readable
  status, and a browsable index without interpreting cross-version hash changes
  as failures.
- [x] The committed 1.19.2 foundation has been propagated through normal
  version branches using the SFM CLI, with audits and adapters/exclusions
  recorded.
- [x] Every normal version branch is represented as supported, excluded, or
  blocked with discovery/compile/preview evidence.
- [x] A reviewed Move 1 Stack matrix exists for every eligible target at a
  validated safe concurrency.
- [ ] Caption text rendering is centrally owned by `SFMFontUtils`, guarded by
  the font-render audit, and visually revalidated on the affected branches.
- [ ] Release scope, target support, final version, changelog, known issues,
  credits, issue/milestone status, and the CC compatibility statement have
  been deliberately approved and propagated.
- [ ] The linked Draw editor plan's approved release floor is complete with
  cross-version evidence, or its deferral and replacement release wording are
  explicitly approved before Phase 5.1 closes.
- [ ] Final datagen, GameTest, JUnit, Rust-tooling, and version-surface checks
  pass on the stated target set; exclusions and any manual exceptions are
  documented.
- [ ] Collected jars match the approved version/target scope and have passed
  the staged Prism/dedicated-server core workflow plus the approved packaged
  CC verification endpoints.
- [ ] Explicit approval preceded tagging, pushing, and publication; GitHub,
  CurseForge, and Modrinth assets have been created and remote platform hashes
  match the local release jars.
- [ ] Milestone cleanup, release evidence archival, and ongoing support
  ownership are complete.
- [ ] No feature-worktree or unrelated user change was modified and no direct
  Gradle command was used. Developer-only puppet infrastructure is not listed
  as a player-facing feature unless its public behavior is separately approved.
