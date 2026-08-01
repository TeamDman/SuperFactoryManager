# Release checkpoint and feature graduation plan

**Plan status:** Active  
**Primary implementation root:** `D:\Repos\Minecraft\SFM\repos2\1.19.2`  
**Last updated:** 2026-08-01  
**Update rules:** Keep this plan executable. Record decisions and evidence beside the affected work item, keep at most one current implementation focus, and update this file after every release-scope or artifact-policy change. Do not mark a phase complete from compilation alone; attach the command and the artifact or runtime evidence that proves it.

## Purpose

Checkpoint the unpublished 1.19.2 work into a releasable product scope and graduate the prospective command-palette, Rust/Vox terminal, and related tooling features to a complete, distributable implementation. The release must have an explicit support statement, a known dependency footprint, and an artifact that can be installed and exercised outside userdev. A reduced `slim` profile is deferred until the full release path is mature and there is a demonstrated product need for it.

This plan coordinates the existing terminal-bridge, dependency-lock, and cross-version propagation plans. It does not replace them and does not authorize publishing or pushing by itself.

## Scope

In scope:

- Decide which unpublished 4.35-era functionality is release-ready, deferred, or dev-only.
- Graduate the player-facing features and close their acceptance, packaging, and fallback gaps.
- Make Rust artifact planning and legacy Gradle dependency/source-set behavior agree.
- Bundle the Java Vox runtime with loader-native Jar-in-Jar and prove the final artifact contains it.
- Define the external `teamy-terminal` companion-server distribution and graceful absence behavior.
- Keep the full/default artifact as the release target; revisit a reduced profile only after this checkpoint.
- Produce release artifacts, clean-install evidence, release notes, and a PR/issue disposition matrix.
- Propagate only the approved baseline changes oldest-first according to `docs/AGENTS.md`.

Out of scope for this checkpoint:

- Cloud Terrastodon changes.
- Replacing the legacy Gradle build or requiring Gradle for normal work.
- Treating the incomplete Rust/Vox terminal bridge as release-ready without its remaining acceptance and packaging proof.
- Implementing `jar build --slim` or `run client --slim` as a release prerequisite.
- A GPU/Vulkan renderer rewrite or adopting the Teamy Studio slug renderer.
- Automatically merging or pushing open PRs.

## Established foundation

- The canonical maintained worktree is the 1.19.2 branch at `D:\Repos\Minecraft\SFM\repos2\1.19.2`. `docs/AGENTS.md` requires work to start there and propagate forward with `sfm-propagate-changes.exe`; newer version worktrees must not be clobbered.
- The canonical branch is 211 commits ahead of `origin/1.19.2` and currently has the terminal, palette, CLI-diagnostic, dependency-lock, and plan changes uncommitted. This is a checkpoint/publishing concern, not permission to push.
- `platform/minecraft/gradle.properties` still reports `mod_version=4.34.0`, while `platform/minecraft/src/main/resources/assets/sfm/template_programs/changelog.sfml` starts with `4.35.0 PRE`. The version and scope are therefore not release-locked.
- `docs/tasks/puppet propagation and preview matrix plan.md` already contains the broad release sequence: release scope, metadata, version resources, automated tests, artifact collection, isolated installs, and player-like packaged verification. This plan supplies the missing slim/profile and release-triage contract.
- `docs/tasks/vox terminal bridge and graceful degradation plan.md` remains active. Batch 1 and Batch 2 now have focused tests and live puppet proof; remaining release work is packaging, companion-server distribution, and final clean-install/runtime acceptance.
- `docs/tasks/teamy terminal repository and Vulkan renderer plan.md` remains active for the Teamy Terminal side. The current PNG bridge is the release candidate path; GPU/Vulkan and slug-renderer work remain later optimization tracks.
- `docs/tasks/cc tweaked integration plan.md` is marked Complete. Parking CC for a release is therefore a new product-scope decision; it must not silently rewrite the historical completion record.
- `docs/tasks/dependency source management v3 plan.md` is active and is the source of truth for lockfile projection. The 1.19.2 lock classifies `cc-tweaked` as a loader-managed optional mod and now classifies `vox-java` as compile/runtime/bundle with an explicit bounded policy.
- `docs/architecture/vox-java-jar-in-jar-packaging-research.md` records the existing Jar-in-Jar precedent. Forge 1.19.2 and the Rust builder both have the packaging path; the remaining release gates are source provenance/reachability, Gradle resolution of the source-built artifact, and clean-install runtime proof.
- The recent Rust/SFM terminal commits are local unpublished functionality, including Rust input/frame routing and solo bridge actions. The normal Java-local fallback must remain usable when the Rust service is absent.

## User-testing evidence — 2026-08-01

These observations are release-correctness inputs, not requests to paper over the test harness:

- The command palette opens with `sfm action invoke`. Typing `open` does not currently fuzzy-rank the available action results. The implementation calls the Brigadier-backed completion path directly, while existing SFML intellisense tests already use string-distance/ranking algorithms. The likely fix is a hybrid candidate layer: Brigadier remains authoritative for parse ranges, availability, typed arguments, and execution; an existing fuzzy scorer ranks action-id candidates when the cursor is in the action-id slot.
- The Rust-backed SFM terminal still renders the Java-local `> _` input strip below the PNG blit. `SFMTerminalPanel` currently calls `renderInput(...)` on the Rust frame path, even though input is sent directly to the Rust service. The Rust path should give the frame the full terminal content region and must not display or submit Java-buffered input.
- Triple-Escape closes the terminal correctly, but the sequence has no visible progress cue. `SFMTerminalFocusSequence` currently exposes only `FORWARD`, `EXIT`, and `JAVA_FOCUS`; the panel needs a non-invasive status indicator such as “Press Esc 2 more times within 1.5 seconds to close” while the sequence is active. The same discoverability treatment should cover triple-Tab focus traversal.

These findings should be included in the acceptance matrix before a release candidate is called user-ready.

## Confirmed constraints

1. The full/default artifact is the primary release target; do not reduce scope to avoid unfinished work.
2. Prospective player-facing features graduate only with focused regression tests, live acceptance proof, documented fallback behavior, and release notes.
3. Dependency inclusion must be driven by lock/projection policy, with explicit classification for optional integrations and future dev-only tooling.
4. The Rust CLI is the normal build/run entry point. Legacy Gradle remains a compatibility path and must continue to produce a coherent artifact when invoked directly.
5. Vox is a runtime dependency of the terminal feature and must be bundled through loader-native Jar-in-Jar in the distributable SFM artifact.
6. Both Gradle and Rust builders must agree on Vox bundle policy, version range, artifact version, and `is_obfuscated=false`, and the final artifact must contain the nested JAR and metadata.
7. `teamy-terminal.exe` is a separate companion server. The release must document how it is supplied/launched, while SFM must retain a graceful Java-local/unavailable-server path.
8. CC:Tweaked is not nested in the SFM jar merely because SFM has optional CC integration. Its completed optional integration remains external unless a separate product decision changes that.
9. A reduced `--slim` profile is deferred and must not become an implicit alias for Jar-in-Jar.
10. Do not change Cloud Terrastodon, merge unrelated PRs, or push a checkpoint until the user explicitly asks for that release operation.

## Release decision gates

These decisions are required before implementation is considered release-directed:

| Gate | Decision required | Default working assumption | Evidence to record |
|---|---|---|---|
| Version | Is this release 4.35.0, or another version? | 4.35.0 is the candidate because the changelog has a 4.35.0 PRE section. | Approved version, changelog scope, `gradle.properties`, generated resources. |
| CC support | Is CC:Tweaked retained as an optional player-facing integration, or parked behind the dev profile? | Retain until explicitly parked; it is not current SFM Jar-in-Jar bloat. | Final `mods.toml`, lock scopes, source boundary, clean install with and without CC. |
| Vox/terminal | Is Vox and the Rust terminal a release feature, a dev-only feature, or deferred entirely? | Graduate it into the player-facing release after the remaining packaging, companion-server, and clean-install gates pass. | Feature matrix, default launch behavior, changelog/release notes. |
| Artifact names | Which artifact is published as the release artifact? | Publish the full/default artifact with Vox nested; no slim artifact is required for this checkpoint. | Artifact inventory, nested-JAR metadata, publication task output. |
| Version surface | Which maintained Minecraft worktrees receive the shared release changes? | Implement and prove on 1.19.2, then propagate the proven baseline oldest-first. | Propagation log and per-version validation matrix. |
| Change intake | Which open PRs/issues are included in this release? | Require explicit triage; do not absorb old or non-mergeable work by default. | PR/issue matrix with include, defer, fix, or close decision. |

## Deferred reduced-profile work

The previously proposed `--slim` profile is not part of the current release path. No implementation should add profile-specific exclusions merely to make the artifact smaller. After the full feature-bearing artifact is mature, we may revisit a shared `Full`/`Slim` model if a concrete distribution or development need justifies it. Any future profile must preserve the complete runtime closure of retained features and must remain independent from Jar-in-Jar selection.

### Legacy Gradle compatibility

The immediate Gradle work is full/default release compatibility and Vox Jar-in-Jar packaging. The compatibility work should be concentrated in the existing projection and publication seams:

- `platform/minecraft/gradle/dependencies-from-lock.gradle`: apply the lock policy before configurations are populated.
- `platform/minecraft/gradle/jar-jar.gradle`: select the nested artifact from the projected `jarJar` configuration and publish it as the unclassified release artifact.
- `platform/minecraft/gradle/publishing.gradle`: publish the full artifact containing the nested Vox runtime.
- `platform/minecraft/gradle/dependencies/1.19.2/dependencies.gradle`: preserve legacy direct-dependency compatibility and document which lock projection wins when both mechanisms are active.

The full/default Gradle build must remain a valid fallback. Any later profile that excludes classes must first introduce a stable registration boundary or equivalent source-safe adapter; otherwise the main SFM class can fail to load before an optional-mod check runs.

### Rust build/run compatibility

The Rust path should first gain Forge Jar-in-Jar parity in the existing artifact planning/execution modules. It must consume the lockfile’s Vox bundle policy, emit `META-INF/jarjar/<artifact>.jar` and `META-INF/jarjar/metadata.json`, and include those entries in artifact audits. Add tests for exact nested bytes, metadata, Forge toolchain selection, and a full external-style launch. A future reduced profile must be threaded through the same layers rather than special-cased in a command handler.

## Source references

- Repository rules: `D:\Repos\Minecraft\SFM\repos2\1.19.2\docs\AGENTS.md`
- Existing release workflow: `D:\Repos\Minecraft\SFM\repos2\1.19.2\docs\tasks\puppet propagation and preview matrix plan.md`
- Terminal bridge: `D:\Repos\Minecraft\SFM\repos2\1.19.2\docs\tasks\vox terminal bridge and graceful degradation plan.md`
- Teamy Terminal integration: `D:\Repos\Minecraft\SFM\repos2\1.19.2\docs\tasks\teamy terminal repository and Vulkan renderer plan.md`
- Dependency projection: `D:\Repos\Minecraft\SFM\repos2\1.19.2\docs\tasks\dependency source management v3 plan.md`
- Jar-in-Jar research: `D:\Repos\Minecraft\SFM\repos2\1.19.2\docs\architecture\vox-java-jar-in-jar-packaging-research.md`
- Version metadata: `D:\Repos\Minecraft\SFM\repos2\1.19.2\platform\minecraft\gradle.properties`
- Mod dependency declarations: `D:\Repos\Minecraft\SFM\repos2\1.19.2\platform\minecraft\src\main\resources\META-INF\mods.toml`
- Release/changelog candidate: `D:\Repos\Minecraft\SFM\repos2\1.19.2\platform\minecraft\src\main\resources\assets\sfm\template_programs\changelog.sfml`
- Lockfile: `D:\Repos\Minecraft\SFM\repos2\1.19.2\dependencies.lock.json`
- Rust terminal repository: `G:\Programming\Repos\teamy-terminal`
- Resumable-plan instructions used for this document: `G:\Programming\Repos\skills\.github\skills\resumable-implementation-plans\SKILL.md`

## Execution order

Do not begin implementation in a later phase while an earlier decision gate is unresolved, except for read-only inventory and tests that clarify the gate.

### Phase 0 — Checkpoint inventory and change intake [in progress]

#### 0.1 Record the baseline [in progress]

**Work:** Record the current branch/ref, worktree cleanliness, version metadata, changelog candidate, existing artifacts, active plans, and the Rust/Gradle build entry points. Preserve the current local commits as the starting checkpoint.

**Validation:** Re-run `sfm-propagate-changes.exe git status`, `git status --short`, version-resource inspection, and artifact inventory from the canonical 1.19.2 worktree. Record any environment-only failures separately from product failures.

**Completion criteria:** This plan contains enough evidence to identify the exact pre-release baseline and no untracked source change is mistaken for release work. The feature-worktree safe-directory ownership issue is documented rather than “fixed” by changing global Git configuration.

#### 0.2 Triage open PRs and issues [ ]

**Work:** Create a release intake table with each candidate’s base version, mergeability, behavior risk, test evidence, and decision: include, repair then include, defer, or close as obsolete.

**Validation:** Review the current GitHub state and link each accepted/deferred item. The initial relevant candidates are [PR #582](https://github.com/TeamDman/SuperFactoryManager/pull/582), a new 1.19.2 performance PR requiring correctness/benchmark review, and [PR #486](https://github.com/TeamDman/SuperFactoryManager/pull/486), an old non-mergeable NBT feature requiring an explicit defer/repair decision. [Issue #469](https://github.com/TeamDman/SuperFactoryManager/issues/469) and [Issue #473](https://github.com/TeamDman/SuperFactoryManager/issues/473) appear housekeeping/documentation-oriented rather than automatic release blockers.

**Completion criteria:** Every included change has a validation owner and test plan; every deferred change has a reason and a follow-up location. No PR is merged solely because it is open or has a favorable mergeable flag.

#### 0.3 Convert user-testing findings into acceptance cases [x]

**Work:** Completed the deterministic cases for fuzzy action discovery from `sfm action invoke`, Rust-terminal layout without a Java input strip, and visible triple-Escape/triple-Tab progress. The command-palette parser and Rust terminal protocol remain separate: ranking does not change Brigadier execution semantics, and layout/status changes do not alter key delivery.

**Validation:** `sfm-propagate-changes.exe run compile --branch 1.19.2` passed. `sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMTerminalFocusSequenceTests` passed 3/3, and `--filter SFMClientActionPaletteSuggestionTests` passed 2/2. The real puppets `title_screen_rust_terminal` and `title_screen_command_palette` both passed with `failed=0`; captures are archived under `platform/minecraft/build/sfm-toolchain/artifacts/game-test-preview/runs/` and `platform/minecraft/runGameTestPreview/screenshots/`.

**Completion criteria:** Each reported behavior has a regression test plus a human-readable acceptance statement, and the release matrix distinguishes Java-local terminal behavior from Rust-backed behavior.

### Batch 1 completion evidence — 2026-08-01

- `SFMTerminalPanel` now gives the Rust/Vox PNG the full terminal content area and does not render the Java-local `> _` input strip on that path; the Java-local REPL prompt remains unchanged.
- `SFMTerminalFocusSequence` exposes localized, time-bounded progress state for Escape and Tab. The Rust-terminal puppet visibly captured “Press Esc 2 more times within 1.5 seconds to close terminal” and “Press Tab 2 more times within 1.5 seconds to return focus to Minecraft.”
- `SFMClientActionCommandTree.getPaletteSuggestions(...)` adds fuzzy action-id/title discovery only in the palette action-id slot. Brigadier remains authoritative for availability, parse ranges, typed arguments, and execution; the JUnit regression proves unavailable actions stay out and the selected action executes through Brigadier.
- The command-palette puppet visibly captured the live `sfm action invoke open` query with ranked results including `sfm:repl/open`, `sfm:terminal/open`, `sfm:palette/open`, and `sfm:theme/open_file`.
- The installed propagation CLI also now reports Windows `os_error=5` lock failures explicitly, warns immediately, and caps the ambiguous retry at 10 seconds instead of waiting 15 minutes. Its `check-all.ps1` gate passed with 363 tests passing and 1 ignored.

### Jar-in-Jar packaging evidence — 2026-08-01

- The Vox component now projects to `compile`, `runtime`, and `bundle` with accepted range `[0.10.0-rc.5]`, artifact version `0.10.0-rc.5`, and `is_obfuscated=false`.
- `cargo test --manifest-path platform/cli/sfm-propagate-changes/Cargo.toml loader_jarjar --offline` passed 3/3, including deterministic Forge and NeoGradle metadata coverage.
- `sfm-propagate-changes.exe jar plan --branch 1.19.2 --artifact-source G:\Programming\Repos\facet\vox\java\target --dry-run` produced a plan with `org.facet:vox-java:0.10.0-rc.5` in `jarJar` and the locked bundle policy.
- The Rust full artifact build passed with the explicit Facet/Vox source. `platform/minecraft/build/libs/Super Factory Manager (SFM)-MC1.19.2-4.34.0-rust.jar` is 2,926,764 bytes and contains `META-INF/jarjar/vox-java-0.10.0-rc.5.jar` (403,625 bytes) plus `META-INF/jarjar/metadata.json`. The nested JAR SHA-256 is byte-identical to the source artifact: `2d0a45e8339be3229fe657c465470dfa10df34d040b43f6cd2cd5e6360419007`.
- The legacy ForgeGradle `jarJar` task now passes through `sfm-propagate-changes.exe gradle run --branch 1.19.2 --show-logs jarJar` without any injected Gradle property. `repositories.gradle` discovers the SFM-managed Maven cache itself, and the 49.4-second run completed `:jarJar` and `:reobfJarJar` successfully.
- The Gradle full artifact `platform/minecraft/build/libs/Super Factory Manager (SFM)-MC1.19.2-4.34.0.jar` is 2,948,745 bytes and contains `META-INF/jarjar/vox-java-0.10.0-rc.5.jar` (403,625 bytes) plus `META-INF/jarjar/metadata.json`. Its nested Vox JAR SHA-256 is the same as the Rust artifact: `2d0a45e8339be3229fe657c465470dfa10df34d040b43f6cd2cd5e6360419007`.
- This proves the Gradle compatibility path through the managed local repository. A clean checkout still needs either the locked source-build materialization or a published Maven coordinate; the local cache is not a substitute for external distribution.
- The lock currently records Facet commit `aa75598da`, while the byte-accurate Vox artifact came from the two local `main` commits `8c3c23c31` and `973318f72`. Those commits must become reachable from the locked remote (or the artifact must be published) before a `--require-portable-artifacts` release build can pass. Do not claim this gate complete from the explicit-source build.

### Source-build/API hypothesis experiment — complete (2026-08-01)

Before changing the Vox pin, the existing source/build behavior was tested from
isolated caches with no Gradle property injection. The experiment answered
whether the current lock naturally materializes the pinned Facet revision and
whether the resulting older Vox API is actually incompatible with the current
SFM Java sources.

```pwsh
$env:SFM_PROPAGATE_CHANGES_CACHE = 'C:\tmp\sfm-vox-lock-hypothesis-20260801'
sfm-propagate-changes.exe jar build --branch 1.19.2 `
  --require-portable-artifacts --wait-for-build-lock
```

- A fresh `jar build --require-portable-artifacts` first failed during remote
  artifact resolution: `modmaven.dev` returned bytes with hash
  `d4736e46...` instead of the locked `ff6894ba...`. The resolver probed
  ModMaven because `candidate_repositories` prefers display labels such as
  `Maven Central`, while the schema-v3 projection supplies canonical IDs such
  as `maven-central`; the failed preference lookup caused every repository to
  be tried. This is a repository-selection bug, not evidence that ModMaven
  hosts Vox, and it stopped the run before source materialization.
- A controlled Gradle `jarJar` run against the known older `aa75598da` Vox JAR
  failed at `:compileJava` with four missing-symbol errors for
  `TerminalContentRequest` and `TerminalContentResult` in
  `SFMVoxTerminalService.java`.
- The hypothesis is confirmed: the current SFM sources require the newer Vox
  API. Update the source-build pin and expected hash before rerunning the
  portable build.
- After repository preference is corrected, rerun the clean-cache experiment
  to verify that Maven Central's miss proceeds to the existing source-build
  fallback. Keep any remaining fallback behavior as a separate CLI hardening
  item; do not confuse it with the now-confirmed Vox pin mismatch.

### Phase 1 — Freeze the release scope [in progress]

**Work:** Decide the release version, supported Minecraft versions, player-facing feature list, CC policy, Vox/terminal policy, and whether the 4.35.0 PRE changelog is the candidate scope. The current direction is to graduate the terminal and command-palette work rather than hide it behind `--slim`.

**Validation:** Compare the approved matrix against the changelog, `mods.toml`, dependency lock, active plans, and current generated/version resources. Verify that every advertised integration has a clean-install test or is explicitly labeled experimental/dev-only.

**Completion criteria:** The version and feature matrix is approved, the support statement is written, and the full artifact’s dependency closure—including Vox Jar-in-Jar and the companion-server requirement—is explicit.

### Phase 2 — Commit the current baseline [complete]

**Work:** Commit the current 1.19.2 terminal, command-palette, puppet, CLI-diagnostic, dependency-lock, and plan changes in an intentional checkpoint. Keep Facet/Vox’s existing local commits and Teamy Terminal’s source changes separate from generated build output.

**Validation:** Focused Teamy Terminal Vox tests pass; CLI artifact-lock tests pass; SFM compile and puppet evidence are recorded; each committed repository is clean except for intentionally ignored generated output.

**Completion criteria:** The current work is recoverable from local commits with no source changes silently left outside the checkpoint. This is satisfied by the clean SFM, Facet/Vox, and Teamy Terminal worktrees and the local checkpoint commits.

### Phase 3 — Bundle Vox through Jar-in-Jar [in progress]

**Work:** Add Vox’s explicit bundle policy to the lockfile, preserve compile/runtime projection, and make Gradle select the nested artifact for Forge 1.19.2. Preserve legacy direct dependency compatibility while ensuring the lock projection is authoritative.

**Validation:** Rust packaging, lock projection, and legacy Gradle packaging through the self-discovered managed Maven cache are proven as recorded above. First complete the source-build/API hypothesis experiment. Then make the locked Vox source/artifact reproducible from a clean checkout, inspect both outputs, and run a clean Forge-style launch without an external Vox Java dependency.

**Completion criteria:** The full Gradle artifact is deterministic, unclassified, and contains the required nested Vox runtime with valid Forge metadata; the source/artifact provenance is reproducible from a clean checkout.

### Phase 4 — Match the Rust artifact builder [in progress]

**Work:** Keep the existing Forge/NeoGradle Rust Jar-in-Jar emission covered by the lock policy, and close the portable source-build/provenance path. The Forge emission and deterministic unit tests are already present.

**Validation:** Targeted Rust JarJar tests and the explicit-source full artifact pass. Re-run the artifact audit with `--require-portable-artifacts` after the Facet revision is reachable, compare nested entries and metadata with Gradle, and run an external-style launch.

**Completion criteria:** Rust and Gradle agree on Vox bundle contents, metadata, artifact naming, dependency closure, and runtime availability from reproducible inputs. A missing Rust service does not break Java-local behavior.

### Phase 5 — Graduate feature acceptance and companion distribution [ ]

**Work:** Close the remaining terminal and command-palette release gates, document how `teamy-terminal.exe` is installed or launched, retain the Java-local fallback, and keep CC as an external optional integration. Do not use a reduced profile to conceal missing functionality.

**Validation:** Exercise the default Java-local path, clean launch without the Rust service, launch with the Rust service when enabled, the relevant optional-mod combinations, and the packaged artifact with nested Vox.

**Completion criteria:** Each advertised feature has deterministic behavior, tests, a release-note/support statement, and no hidden development-only dependency. The terminal bridge is either fully released with its companion-server story or explicitly deferred by a recorded decision.

The user-testing fixes above are prerequisites for calling the Rust terminal or command palette release-ready; the current direction is to finish the remaining gates and promote them, not to exclude them from the artifact.

### Phase 6 — Propagate deliberately [ ]

**Work:** Implement and validate on 1.19.2 first, then propagate the shared CLI/build changes oldest-first with `sfm-propagate-changes.exe`. Resolve version-specific dependency and packaging differences explicitly; do not overwrite newer worktree changes.

**Validation:** Run the repository’s compile/test/preview matrix per maintained version, inspect each propagated diff, and re-run status. Do not treat a clean merge as feature validation.

**Completion criteria:** Every supported target has either a passing release validation result or a written reason it is excluded from this release. The propagation log identifies any target-specific adaptation.

### Phase 7 — Release proof and housekeeping [ ]

**Work:** Update version resources and changelog, build the full artifact, collect and audit it, stage isolated installs, verify clean loader launches, run gameplay/puppet/smoke tests, prepare release notes, and update the selected PR/issue records.

**Validation:** Compare artifact contents and sizes against the approved budget; inspect manifests, nested JARs, dependency closure, and classifier. Verify the full artifact outside userdev and capture the exact commands/results. Check the documented companion-server behavior and Java-local fallback.

**Completion criteria:** The release candidate is reproducible from a clean checkout, has a complete support/dependency statement, passes the agreed cross-version matrix, and has a clearly documented rollback/checkpoint ref. Publication remains a separate explicit user-approved action.

## Current risks and mitigations

- **Scope/version drift:** 4.35.0 PRE changelog versus `mod_version=4.34.0`. Resolve Phase 1 before changing metadata.
- **CC source loading:** CC is optional at runtime but directly referenced by SFM initialization code. Use an adapter/source boundary and clean no-CC launch proof before excluding it.
- **Jar size misconception:** Vox is a small pure-Java runtime; it must be measured, but its size is not a reason to omit required runtime classes.
- **Vox source reachability:** Facet `main` is ahead of the locked `aa75598da` revision and the current Vox coordinate is not published in configured Maven repositories. Publish/reach the exact revision, update the lock, and rerun the portable build before release.
- **Gradle resolution:** The lock projection correctly requests `jarJar`, and legacy Gradle now consumes the source-built artifact through the self-discovered SFM-managed Maven repository. Clean external builds still require source-build materialization.
- **Repository candidate selection:** The resolver's preferred repository table currently uses display names while schema-v3 projected repositories use canonical IDs. Correct the mapping and add a test proving `org.facet` only probes `maven-central`; then repeat the source-build fallback experiment before changing the Vox pin.
- **Companion-server distribution:** Bundling Vox does not bundle `teamy-terminal.exe`; document and test the external server path and graceful fallback.
- **Local branch divergence:** 1.19.2 is 211 commits ahead of origin. Create a reviewed checkpoint ref before publishing; do not push as part of this plan without approval.
- **Open change intake:** PR #582 needs correctness and benchmark review; PR #486 is non-mergeable and should not enter the release by default.
- **Environment bookkeeping:** One feature worktree could not be inspected because Git safe-directory ownership differs for the sandbox user. Do not alter global Git configuration just to make the release appear clean.

## Overall completion criteria

This plan is complete only when:

1. The release version, support statement, and feature/dependency matrix are approved.
2. Full/default behavior is preserved and separately verified.
3. The full artifact contains the required Vox nested JAR and valid loader metadata.
4. Gradle and Rust artifact builders agree on nested-JAR contents, dependency closure, and publication rules.
5. The companion Rust server has a documented distribution path and graceful absence behavior.
6. The release candidate passes clean-install, external-style launch, gameplay/puppet, and cross-version checks required by the approved matrix.
7. PRs/issues have explicit disposition, release notes are ready, and a checkpoint ref is recorded.
8. Publishing or pushing has been separately approved and is not implied by completing the plan.
