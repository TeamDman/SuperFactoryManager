# Next release: puppet stabilisation, preview matrix, and release plan

**Plan status:** Active
**Primary implementation root:** `D:\Repos\Minecraft\SFM\repos2\1.19.2`
**Last updated:** 2026-07-15

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
  `sfm:title_screen_capture`.
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

### [ ] 3.1 Define and implement a multi-version preview matrix command

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

### [ ] 3.2 Calibrate safe client parallelism

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

## Phase 4 — Propagate and prove the version matrix

### [ ] 4.1 Propagate the committed baseline through normal version branches

**Work:**

- Run the SFM merge command interactively with `--no-auto-abort` after Phase 1
  preconditions are met.
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
sfm-propagate-changes.exe git merge --no-auto-abort
cd platform\cli\sfm-propagate-changes
cargo run -- audit --branch core --version-surfaces
```

**Completion criteria:** Every normal version branch has either received the
baseline commit through the SFM merge workflow or is explicitly marked blocked
or excluded with a technical reason and next action.

### [ ] 4.2 Validate static discovery, compilation, and previews per eligible target

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

### [ ] 4.3 Produce and review the all-version Move 1 Stack matrix

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

## Phase 5 — Close the release contract and prepare metadata

### [ ] 5.1 Decide the release scope, version, and support statement

**Work:**

- Reconcile the committed baseline changes, the current `4.35.0 PRE`
  changelog, and the completed CC:Tweaked mutation plan into one explicit
  release candidate. Decide the final version rather than inferring it from
  the preliminary heading.
- Enumerate each user-facing change that will ship and map it to its changelog
  entry, source/test evidence, and affected Minecraft versions. Remove or
  defer any changelog promise that lacks a committed, supportable change.
- State the release support policy: normal SFM artifacts for each selected
  version target, plus CC:Tweaked mutable handles and the `sfm_labeler` turtle
  upgrade only on the six locked-runtime targets recorded in the acceptance
  matrix.
- Review related issues and the release milestone with the project maintainer.
  Fixed issues must be closed or marked `implemented awaiting release`; the
  release scope must not silently absorb unrelated feature-worktree work.
- Freeze scope before version bumping. A discovered release blocker returns to
  the relevant implementation phase and reopens this task's review.

**Validation:**

```powershell
git log --oneline origin/1.19.2..1.19.2
rg -n '^---- |TODO|PRE' platform\minecraft\src\main\resources\assets\sfm\template_programs\changelog.sfml
sfm-propagate-changes.exe git status
```

**Completion criteria:** The maintainer has approved one final version and a
written release scope. Every player-facing entry is backed by committed code
and evidence; developer-only puppet work is listed only as release evidence.

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
sfm-propagate-changes.exe git merge --no-auto-abort
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
sfm-propagate-changes.exe git merge --no-auto-abort
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
- Install and use the `sfm_labeler` turtle upgrade with a selected label gun to
  perform a player-equivalent label action. Verify a turtle inventory can take
  part in an SFM transfer.
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
sfm-propagate-changes.exe git merge --no-auto-abort
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
| 1.19.2 | Baseline candidate | Packaged and supported | catalog, compile, preview matrix, datagen, full suite, jar, installed core workflow, CC endpoint verification | Title preview proven; remaining proof pending |
| 1.19.4 | Candidate | Packaged and supported | catalog, compile, preview matrix, datagen, full suite, jar, installed core workflow; CC suite evidence and representative-endpoint coverage | Pending |
| 1.20 | Candidate; unrelated `.antlr` directory preserved | Packaged and supported | catalog, compile, preview matrix, datagen, full suite, jar, installed core workflow; CC suite evidence and representative-endpoint coverage | Pending |
| 1.20.1 | Candidate | Packaged and supported | catalog, compile, preview matrix, datagen, full suite, jar, installed core workflow; CC suite evidence and representative-endpoint coverage | Pending |
| 1.20.2 | Candidate | Deliberately source-excluded: no compatible locked runtime | catalog, compile, preview matrix, datagen, full suite, jar, installed core workflow; no CC release claim | Pending |
| 1.20.3 | Candidate | Deliberately source-excluded: no compatible locked runtime | catalog, compile, preview matrix, datagen, full suite, jar, installed core workflow; no CC release claim | Pending |
| 1.20.4 | Candidate | Packaged and supported | catalog, compile, preview matrix, datagen, full suite, jar, installed core workflow; CC suite evidence and representative-endpoint coverage | Pending |
| 1.21.0 | Candidate | Deliberately source-excluded: no compatible locked runtime | catalog, compile, preview matrix, datagen, full suite, jar, installed core workflow; no CC release claim | Pending |
| 1.21.1 | Candidate | Packaged and supported | catalog, compile, preview matrix, datagen, full suite, jar, installed core workflow; CC suite evidence and representative-endpoint coverage | Pending |
| 26.1.2 | Candidate | Deliberately source-excluded: no compatible locked runtime | catalog, compile, preview matrix, datagen, full suite, jar, installed core workflow; no CC release claim | Pending |
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
- [ ] A matrix command produces validated copied artifacts, machine-readable
  status, and a browsable index without interpreting cross-version hash changes
  as failures.
- [ ] The committed 1.19.2 foundation has been propagated through normal
  version branches using the SFM CLI, with audits and adapters/exclusions
  recorded.
- [ ] Every normal version branch is represented as supported, excluded, or
  blocked with discovery/compile/preview evidence.
- [ ] A reviewed Move 1 Stack matrix exists for every eligible target at a
  validated safe concurrency.
- [ ] Release scope, target support, final version, changelog, known issues,
  credits, issue/milestone status, and the CC compatibility statement have
  been deliberately approved and propagated.
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
