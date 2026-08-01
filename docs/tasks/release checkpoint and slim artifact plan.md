# Release checkpoint and feature graduation plan

**Plan status:** Active  
**Primary implementation root:** `D:\Repos\Minecraft\SFM\repos2\1.19.2`  
**Last updated:** 2026-08-01  
**Update rules:** Keep this plan executable. Record decisions and evidence beside the affected work item, keep at most one current implementation focus, and update this file after every release-scope or artifact-policy change. Do not mark a phase complete from compilation alone; attach the command and the artifact or runtime evidence that proves it.

## Purpose

Checkpoint the unpublished 1.19.2 work into a releasable product scope and graduate the prospective command-palette, Rust/Vox terminal, and related tooling features to a complete, distributable implementation. The release must have an explicit support statement, a known dependency footprint, and an artifact that can be installed and exercised outside userdev. The lockfile will provide feature-aware entry-point profiles so the Rust CLI can build the full feature set while legacy Gradle remains a low-friction Java-only contributor path by default.

This plan coordinates the existing terminal-bridge, dependency-lock, and cross-version propagation plans. It does not replace them and does not authorize publishing or pushing by itself.

## Scope

In scope:

- Decide which unpublished 4.35-era functionality is release-ready, deferred, or dev-only.
- Graduate the player-facing features and close their acceptance, packaging, and fallback gaps.
- Make Rust artifact planning and legacy Gradle dependency/source-set behavior agree.
- Bundle the Java Vox runtime with loader-native Jar-in-Jar and prove the final artifact contains it.
- Define the external `teamy-terminal` companion-server distribution and graceful absence behavior.
- Keep the full Rust-toolchain feature set as the release target while preserving a Java-only Gradle contributor profile by default.
- Add declarative lockfile feature membership and entry-point defaults; do not encode Rust/Vox or project-specific dependency names in Gradle scripts.
- Produce release artifacts, clean-install evidence, release notes, and a PR/issue disposition matrix.
- Propagate only the approved baseline changes oldest-first according to `docs/AGENTS.md`.

Out of scope for this checkpoint:

- Cloud Terrastodon changes.
- Replacing the legacy Gradle build or requiring Gradle for normal work.
- Treating the incomplete Rust/Vox terminal bridge as release-ready without its remaining acceptance and packaging proof.
- Requiring Cargo/Xtask during ordinary Gradle configuration or IntelliJ synchronization.
- Treating `--slim` as the feature model. If a compatibility alias is ever added, it must select a declarative profile rather than hard-code exclusions.
- A GPU/Vulkan renderer rewrite or adopting the Teamy Studio slug renderer.
- Automatically merging or pushing open PRs.

## Established foundation

- The canonical maintained worktree is the 1.19.2 branch at `D:\Repos\Minecraft\SFM\repos2\1.19.2`. `docs/AGENTS.md` requires work to start there and propagate forward with `sfm-propagate-changes.exe`; newer version worktrees must not be clobbered.
- The canonical branch contains the local terminal, palette, CLI-diagnostic, dependency-lock, and plan checkpoint commits and remains intentionally unpublished. This is a checkpoint/publishing concern, not permission to push.
- `platform/minecraft/gradle.properties` still reports `mod_version=4.34.0`, while `platform/minecraft/src/main/resources/assets/sfm/template_programs/changelog.sfml` starts with `4.35.0 PRE`. The version and scope are therefore not release-locked.
- `docs/tasks/puppet propagation and preview matrix plan.md` already contains the broad release sequence: release scope, metadata, version resources, automated tests, artifact collection, isolated installs, and player-like packaged verification. This plan supplies the missing feature-profile and release-triage contract.
- `docs/tasks/vox terminal bridge and graceful degradation plan.md` remains active. Batch 1 and Batch 2 now have focused tests and live puppet proof; remaining release work is packaging, companion-server distribution, and final clean-install/runtime acceptance.
- `docs/tasks/teamy terminal repository and Vulkan renderer plan.md` remains active for the Teamy Terminal side. The current PNG bridge is the release candidate path; GPU/Vulkan and slug-renderer work remain later optimization tracks.
- `docs/tasks/cc tweaked integration plan.md` is marked Complete. Parking CC for a release is therefore a new product-scope decision; it must not silently rewrite the historical completion record.
- `docs/tasks/dependency source management v3 plan.md` is active and is the source of truth for lockfile projection. The 1.19.2 lock classifies `cc-tweaked` as a loader-managed optional mod and now classifies `vox-java` as compile/runtime/bundle with an explicit bounded policy.
- `docs/architecture/vox-java-jar-in-jar-packaging-research.md` records the existing Jar-in-Jar precedent. Forge 1.19.2 and the Rust builder both have the packaging path; the remaining release gates are source provenance/reachability, Gradle resolution of the source-built artifact, and clean-install runtime proof.
- The recent Rust/SFM terminal commits are local unpublished functionality, including Rust input/frame routing and solo bridge actions. The normal Java-local fallback must remain usable when the Rust service is absent.
- The existing `--solo` behavior is a run-time classpath selection, not a feature profile: it may omit loader-managed integration mod jars and deobfuscated project dependencies while retaining required plain libraries. It must not become the Gradle contributor default or silently disable JEI, CC:Tweaked, Mekanism, or other independently declared integrations.

## User-testing evidence — 2026-08-01

These observations are release-correctness inputs, not requests to paper over the test harness:

- The command palette opens with `sfm action invoke`. Typing `open` does not currently fuzzy-rank the available action results. The implementation calls the Brigadier-backed completion path directly, while existing SFML intellisense tests already use string-distance/ranking algorithms. The likely fix is a hybrid candidate layer: Brigadier remains authoritative for parse ranges, availability, typed arguments, and execution; an existing fuzzy scorer ranks action-id candidates when the cursor is in the action-id slot.
- The Rust-backed SFM terminal still renders the Java-local `> _` input strip below the PNG blit. `SFMTerminalPanel` currently calls `renderInput(...)` on the Rust frame path, even though input is sent directly to the Rust service. The Rust path should give the frame the full terminal content region and must not display or submit Java-buffered input.
- Triple-Escape closes the terminal correctly, but the sequence has no visible progress cue. `SFMTerminalFocusSequence` currently exposes only `FORWARD`, `EXIT`, and `JAVA_FOCUS`; the panel needs a non-invasive status indicator such as “Press Esc 2 more times within 1.5 seconds to close” while the sequence is active. The same discoverability treatment should cover triple-Tab focus traversal.

These findings should be included in the acceptance matrix before a release candidate is called user-ready.

## Confirmed constraints

1. The full Rust-toolchain feature set is the primary release target; the Java-only Gradle default exists for contributor compatibility and must not reduce the released product scope.
2. Prospective player-facing features graduate only with focused regression tests, live acceptance proof, documented fallback behavior, and release notes.
3. Dependency inclusion must be driven by lock/projection policy, with explicit classification for optional integrations and future dev-only tooling.
4. The Rust CLI is the normal build/run entry point. Legacy Gradle remains a compatibility path and must continue to produce a coherent artifact when invoked directly.
5. Vox is a runtime dependency of the terminal feature and must be bundled through loader-native Jar-in-Jar in the distributable SFM artifact.
6. Both Gradle and Rust builders must agree on Vox bundle policy, version range, artifact version, and `is_obfuscated=false`, and the final artifact must contain the nested JAR and metadata.
7. `teamy-terminal.exe` is a separate companion server. The release must document how it is supplied/launched, while SFM must retain a graceful Java-local/unavailable-server path.
8. CC:Tweaked is not nested in the SFM jar merely because SFM has optional CC integration. Its completed optional integration remains external unless a separate product decision changes that.
9. Feature selection is declarative. Components may require named features, feature definitions may include transitive feature requirements and source-set ownership, and entry-point profiles select defaults. Components without a feature requirement remain selected, preserving the existing external-mod contributor experience.
10. The Gradle entry point defaults to a Java-only profile with the Rust feature disabled. The Rust/SFM CLI entry point defaults to the full profile with the Rust feature enabled. Explicit feature selection may override either default.
11. A Gradle sync or ordinary Java compile must not require Cargo, `vox-xtask`, or a source-built Vox artifact when the Rust feature is disabled. Enabling the Rust feature is an explicit opt-in that may require the managed source-build artifact to exist.
12. `--solo` remains an explicit launch-mode control and is orthogonal to feature selection. It must continue to retain required plain runtime libraries even when it excludes integration mod jars.
13. Do not change Cloud Terrastodon, merge unrelated PRs, or push a checkpoint until the user explicitly asks for that release operation.

## Release decision gates

These decisions are required before implementation is considered release-directed:

| Gate | Decision required | Default working assumption | Evidence to record |
|---|---|---|---|
| Version | Is this release 4.35.0, or another version? | 4.35.0 is the candidate because the changelog has a 4.35.0 PRE section. | Approved version, changelog scope, `gradle.properties`, generated resources. |
| CC support | Is CC:Tweaked retained as an optional player-facing integration, or parked behind the dev profile? | Retain until explicitly parked; it is not current SFM Jar-in-Jar bloat. | Final `mods.toml`, lock scopes, source boundary, clean install with and without CC. |
| Vox/terminal | Is Vox and the Rust terminal a release feature, a dev-only feature, or deferred entirely? | Graduate it into the player-facing release after the remaining packaging, companion-server, and clean-install gates pass. | Feature matrix, default launch behavior, changelog/release notes. |
| Feature/profile defaults | Which features are selected by each entry point? | Gradle defaults to Java-only; the Rust CLI defaults to the full Rust/Vox feature set; explicit selection overrides defaults. | Lockfile feature/profile model, Gradle sync/compile without Cargo, Rust full build, opt-in Gradle parity build. |
| Artifact names | Which artifact is published as the release artifact? | Publish the full Rust-toolchain artifact with Vox nested. Gradle’s default Java-only output is a contributor compatibility artifact, not a replacement release artifact. | Artifact inventory, nested-JAR metadata, publication task output, profile-specific manifests. |
| Version surface | Which maintained Minecraft worktrees receive the shared release changes? | Implement and prove on 1.19.2, then propagate the proven baseline oldest-first. | Propagation log and per-version validation matrix. |
| Change intake | Which open PRs/issues are included in this release? | Require explicit triage; do not absorb old or non-mergeable work by default. | PR/issue matrix with include, defer, fix, or close decision. |

## Feature profiles and legacy Gradle compatibility

The previous `--slim` idea is superseded by a declarative feature/profile model.
“Slim” now describes the default Gradle contributor experience—Rust is not
selected and Cargo/Xtask/Vox source acquisition is not required—not a second
hand-maintained dependency list or an artifact-size switch.

The schema-v4 direction is:

- A component can declare feature requirements. Existing dependencies without
  requirements remain selected, so external integrations such as JEI,
  CC:Tweaked, and Mekanism are not accidentally treated as `--solo` or hidden
  by the Rust profile.
- Feature definitions can express transitive feature requirements and the
  source sets/resources owned by the feature. The initial `rust` feature owns
  the Rust/Vox bridge and its source-built runtime closure.
- Entry-point profiles declare defaults, for example `gradle: []` and
  `rust-toolchain: [rust]`. The profile is selected by the tool invoking the
  lockfile, not by a hardcoded module list in Groovy.
- Explicit feature selection is an override with validation: unknown features,
  missing required artifacts, and incompatible source-set combinations fail
  clearly rather than silently producing a partial runtime.

The exact field names belong in the schema migration, but the model must keep
three concerns separate: dependency feature selection, run-time `--solo`
classpath trimming, and Jar-in-Jar publication. A feature-disabled Gradle
compile must not load Rust-owned Java sources that import Vox types; those
sources need a feature-owned source boundary or an equivalent adapter boundary.

### Legacy Gradle compatibility

The immediate Gradle work is generic feature/profile projection and preserved
legacy compatibility. The compatibility work should be concentrated in the
existing projection and publication seams:

- `platform/minecraft/gradle/dependencies-from-lock.gradle`: select the
  entry-point profile before configurations are populated, then project every
  component whose feature requirements are satisfied. It must not contain
  Facet/Vox-specific conditionals.
- `platform/minecraft/gradle/repositories.gradle`: register exclusive local
  content only for enabled source-built modules; disabled source-built
  components must not cause Cargo/Xtask or local-cache requirements during
  sync.
- `platform/minecraft/gradle/jar-jar.gradle`: select the nested artifact from the projected `jarJar` configuration and publish it as the unclassified release artifact.
- `platform/minecraft/gradle/publishing.gradle`: publish the full artifact containing the nested Vox runtime.
- `platform/minecraft/gradle/dependencies/1.19.2/dependencies.gradle`: preserve legacy direct-dependency compatibility and document which lock projection wins when both mechanisms are active.

The default Gradle build must remain a valid Java-only fallback and must still
load the other declared integration mods. An explicit Gradle Rust-feature
profile must remain available for parity testing and must use the same lockfile
selection and Jar-in-Jar policy as the Rust toolchain. Any profile that excludes
classes must first introduce a stable registration boundary or equivalent
source-safe adapter; otherwise the main SFM class can fail to load before an
optional-mod check runs.

### Rust build/run compatibility

The Rust path should first gain Forge Jar-in-Jar parity in the existing artifact planning/execution modules. It must consume the lockfile’s Vox bundle policy, emit `META-INF/jarjar/<artifact>.jar` and `META-INF/jarjar/metadata.json`, and include those entries in artifact audits. Add tests for exact nested bytes, metadata, Forge toolchain selection, and a full external-style launch. Feature selection must be threaded through the same layers rather than special-cased in a command handler; the Rust entry point defaults to the full feature profile.

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
- Lockfile: `D:\Repos\Minecraft\SFM\repos2\1.19.2\platform\minecraft\sfm-toolchain.lock.json`
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

### Lockfile acquisition-strategy correction — next

The current schema-v3 Vox declaration says
`declaration.acquisition.maven.repository_id = maven-central` even though its
artifact provenance is `source-build` and no Vox Maven publication exists.
This is a misleading legacy declaration and must not become the basis for
repository fallback behavior.

The next lockfile slice will represent source-build acquisition explicitly. If
the strict v3 acquisition enum cannot express that without overloading Maven,
bump the lockfile schema and add a migration. Keep the source-build recipe and
exact Git provenance in the artifact evidence, while making the component
declaration point to that source-built artifact rather than to a fictional
remote publication.

Gradle should then project source-built modules through an exclusive local
repository content rule. The following is only a demonstration of the Gradle
mechanism, not the implementation target:

```groovy
exclusiveContent {
    forRepository {
        maven {
            name = 'sfm-source-builds'
            url = uri(sfmLocalMavenRepository)
            metadataSources { artifact() }
        }
    }
    filter { includeModule('<group from lockfile>', '<artifact from lockfile>') }
}
```

The actual Groovy projection must read the new lock schema, enumerate every
source-build acquisition, derive its Maven module identity from the locked
coordinate, and generate the `includeModule` filters. It must not contain
Facet, Vox, or any other project-specific module names. The generated
source-build content filters must prevent those modules from being searched in
any remote repository. This is stronger than putting the local repository
first. Remote repositories remain available for dependencies whose lock
acquisition is genuinely remote.

### Feature/profile model — next

The source-acquisition migration must be designed together with feature
selection. A source-built dependency is not merely an alternative download
location: it may also belong to a feature that is intentionally absent from a
legacy Gradle contributor build.

The v4 lock model should therefore provide, in declarative data:

- feature definitions and transitive feature requirements;
- component feature requirements, with an empty requirement meaning the
  component remains part of every normal projection;
- feature-owned source sets/resources or source-boundary declarations; and
- entry-point profiles whose defaults are selected by the invoking toolchain,
  with Gradle defaulting to no Rust feature and the Rust/SFM CLI defaulting to
  the full Rust feature set.

The initial Rust feature should own the Rust/Vox bridge and its source-built
runtime closure. Existing external integrations must remain independently
selected. In particular, disabling Rust must not be implemented as `--solo`,
and must not remove JEI, CC:Tweaked, Mekanism, or other ordinary lockfile
components from the Gradle projection.

The first implementation must also move or boundary Rust-owned Java sources
that import Vox types. Merely omitting the Vox dependency while compiling the
same source set would make the Java-only profile fail at compile time. Gradle
configuration and IntelliJ sync must be able to select the Java-only profile
without invoking Cargo, `vox-xtask`, or source acquisition. An explicit Rust
feature selection may require a previously materialized managed artifact and
must report a clear missing-artifact error.

**Validation:** Use an isolated machine/cache profile to prove that ordinary
Gradle configuration, IDE model generation, and Java compilation succeed with
no Cargo/Xtask/Vox source artifact available; verify that the same projection
still includes ordinary integration mods. Then enable the Rust feature
explicitly in Gradle and through the Rust CLI, prove the full Vox/Jar-in-Jar
closure, and run the existing `--solo` classpath tests to show that launch-mode
trimming remains independent.

**Completion criteria:** The lockfile, Gradle projection, and Rust planner
agree on feature closure and entry-point defaults; Java-only Gradle users have
no Rust setup requirement; Rust-toolchain users receive the full feature set by
default; and explicit feature selection is tested in both directions.

### Goal batch R-3A — Lockfile-driven entry-point profiles [x]

This is the first proposed implementation goal for the feature/profile
direction. It groups the work above into a single verifiable boundary; it is
not a top-k selection of unrelated incomplete tasks.

#### R-3A.1 — Migrate the lock schema and 1.19.2 fixture [x]

Add the typed source-build acquisition and feature/profile model to the next
lockfile schema, migrate v3 documents, and migrate the 1.19.2 fixture so Vox
is owned by the Rust feature rather than falsely declared as a Maven Central
publication. Preserve all existing non-Rust dependency declarations and
provide schema/migration fixtures for both old and new documents.

#### R-3A.2 — Implement generic feature closure [x]

Implement one lockfile-driven feature resolver used by both projections. It
must resolve transitive feature requirements, reject unknown features, retain
components with no feature requirement, and expose the selected source-set and
artifact closure without hardcoded dependency names.

#### R-3A.3 — Make Gradle Java-only by default [x]

Make the Gradle entry point select the Java-only profile unless explicitly
overridden. Gradle configuration, IntelliJ synchronization, and Java
compilation must not require Cargo, `vox-xtask`, Vox source acquisition, or a
managed Vox artifact. Existing JEI, CC:Tweaked, Mekanism, and other ordinary
integration projections must remain present.

#### R-3A.4 — Make the Rust entry point full-featured by default [x]

Make the Rust/SFM CLI select the full Rust profile by default, with an
explicit feature/profile override. `jar plan` must include the Vox closure in
the default Rust projection, while the source-build resolver remains the
authoritative acquisition path for that feature.

#### R-3A.5 — Establish source and launch compatibility proof [x]

Move or boundary Rust-owned Java sources that import Vox types so the
Java-only Gradle profile compiles. Add regression tests proving the two entry
point defaults, ordinary integration retention, explicit Rust opt-in, and
the existing `--solo` classpath behavior. Record isolated-cache Gradle and
Rust-plan evidence in this plan.

**R-3A completion boundary:** Stop after the schema, projections, source
boundary, and profile tests pass. Do not include the portable source-build
artifact proof, explicit Gradle Jar-in-Jar parity build, cross-version
propagation, or release publication in this goal; those are subsequent
release-plan work.

### R-3A completion evidence — 2026-08-01

- `platform/cli/sfm-propagate-changes/src/toolchain_lockfile_schema/version/v4.rs`
  adds schema v4 feature/profile closure, source-boundary declarations, v3
  migration, cycle/unknown-reference validation, and effective v3 projection
  for the existing resolver. `read_current` selects `rust-toolchain` by
  default, so the Rust/SFM entry point remains full-featured.
- `platform/minecraft/sfm-toolchain.lock.json` is schema v4. Vox is a
  `source-build` acquisition referencing its source-build artifact, owned by
  the declarative `rust` feature; it is no longer described as Maven Central.
  `gradle` selects no Rust feature, while `rust-toolchain` selects `rust`.
- Gradle feature selection is generic and lock-driven in
  `platform/minecraft/gradle/lockfile-features.gradle`. Default Gradle
  projection excludes feature-owned Vox source/artifacts; `-PsfmProfile=rust-toolchain`
  explicitly restores them. Repository projection uses exclusive local
  content for selected source-built modules, so it cannot fall through to a
  remote Maven repository.
- The shared terminal panel/PNG renderer now depend on a transport-neutral
  interface; only `SFMVoxTerminalService.java` and its test are feature-owned
  Vox source files. Ordinary Gradle builds can compile the Java path without
  the Vox jar while retaining CC:Tweaked, Mekanism, and other integrations.
- Validation: `cargo test --manifest-path platform/cli/sfm-propagate-changes/Cargo.toml toolchain_lockfile_schema --lib`
  passed 24/24; `cargo test ... solo_client --lib` passed 1/1; and
  `cargo run --manifest-path platform/cli/sfm-propagate-changes/Cargo.toml -- dependency migrate --branch 1.19.2 --check`
  reported canonical schema v4. Default `gradlew compileJava` and
  `gradlew testClasses` passed, with no Vox main/test classes emitted;
  explicit `gradlew compileJava -PsfmProfile=rust-toolchain` passed and
  emitted `SFMVoxTerminalService.class`.
- Boundary respected: no portable source-build proof, final Jar-in-Jar parity,
  propagation, Cloud Terrastodon changes, or publication was attempted.

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

### Proposed goal batch R-4A — Portable Vox source-build and artifact parity [ ]

This batch is prepared for activation after user approval. It is the next
bounded slice after R-3A: turn the declarative source-build/profile model into
reproducible artifact evidence. It does not change the Java-only Gradle
default, propagate to other Minecraft versions, publish anything, or modify
Cloud Terrastodon.

#### R-4A.1 — Reconcile the reachable Vox source pin [x]

Confirm which Facet/Vox commit is reachable from the locked remote and contains
the `TerminalContentRequest`/`TerminalContentResult` API required by the current
SFM sources. Update the schema-v4 source-build pin, artifact identity, and
expected BLAKE3 evidence only after the source commit is reachable and the
artifact can be rebuilt from it. Do not use an unpushed workstation commit as
portable provenance.

**Completion criteria:** The lockfile names a reachable source revision and
exact source-build output; the rebuilt Vox JAR has the locked coordinate and
hash; the old API-incompatible pin is no longer selected.

#### R-4A.2 — Make source-build resolution strict and lock-directed [x]

Fix the resolver path exposed by the hypothesis experiment. Source-built
components must materialize from their locked source recipe rather than probe
unrelated Maven repositories first. Remote repository selection for genuinely
remote artifacts must use canonical lockfile repository IDs, not display-label
guesses. Add regression coverage proving a source-built Vox request cannot fall
through to ModMaven or another remote repository, and retain a clear diagnostic
when the locked source revision or build output is unavailable.

**Completion criteria:** A source-build request takes the source-build path in
an isolated cache; a remote miss cannot be mistaken for a successful artifact;
and repository candidates are selected from lock-directed canonical IDs.

#### R-4A.3 — Prove an isolated portable Rust build and audit [x]

Run the SFM CLI against a fresh cache with no injected Gradle property and no
pre-existing Vox artifact. Plan/build the 1.19.2 Rust profile, materialize the
locked Facet source, verify the output hash and provenance, and run the
portable-artifact audit. Use `--log-file` when observing a long-running CLI
command so lock acquisition and source-build diagnostics remain inspectable
under the Codex harness.

**Completion criteria:** `jar plan`, `jar build`, and
`jar audit-artifacts --require-portable-artifacts` succeed from the isolated
cache, with recorded commands, artifact paths, hashes, source revision, and
any environment-only limitations separated from product failures.

#### R-4A.4 — Reprove full-profile Gradle Jar-in-Jar parity [x]

With the managed source-built Maven repository produced by R-4A.3, run the
explicit `rust-toolchain` Gradle packaging path. Inspect the final unclassified
artifact and confirm it contains the nested Vox JAR and Forge Jar-in-Jar
metadata, while the default Gradle profile remains Java-only. Compare the
nested Vox bytes and coordinates against the Rust artifact rather than merely
checking that both builds compile.

**Completion criteria:** The explicit Gradle full artifact and Rust artifact
contain byte-identical Vox JARs with matching metadata and bundle policy; the
default Gradle compile/test path still works without Cargo, Xtask, or Vox.

#### R-4A.5 — Record the release-artifact handoff boundary [x]

Record the portable-build and parity evidence in this plan and update the
artifact inventory/release gates. If an external clean Forge-style launch
still needs a separate environment or companion-server step, record it as the
next batch rather than silently expanding R-4A.

**R-4A completion boundary:** Stop after reachable source provenance,
strict source-build resolution, isolated Rust artifact proof, and explicit
Gradle/Rust nested-JAR parity are complete. Do not include cross-version
propagation, release version/changelog decisions, PR/issue intake, publication,
or Cloud Terrastodon work in this goal.

**Proposed goal text after approval:** `Complete Goal Batch R-4A in
docs/tasks/release checkpoint and slim artifact plan.md: R-4A.1 through
R-4A.5.`

### R-4A progress evidence — 2026-08-01

- R-4A.1 is complete. The Facet commits were pushed to `mine/main` (`https://github.com/teamdman/facet`), and `git ls-remote mine refs/heads/main` now reports the reachable full revision
  `973318f727128f5ec17c67ab9498cb9e9be2a5e6`. The lockfile pins that revision,
  the `org.facet:vox-java:0.10.0-rc.5` source-build output, and
  `blake3:ff6894bafc65fd9d24cafd60a967bd1c3182a377`. The rebuilt 403,625-byte
  Vox JAR contains the required `TerminalContentRequest`/`TerminalContentResult`
  and `TerminalGetContent*` generated classes.
- R-4A.2 is complete in `resolve.rs`: explicit `--artifact-source` takes
  precedence, then a locked source-build recipe, then locked remote candidates,
  then local cache fallback. Source-built artifacts therefore cannot fall
  through to ModMaven or another remote repository. Canonical repository IDs are
  used for candidate selection, with regressions for source-build isolation,
  explicit-source precedence, and `maven-central`/`blamejared`/`jei` ordering.
- The schema-v4 audit reader was corrected to project the selected current
  profile into the existing audit model while preserving schema-v1/v2 audit
  compatibility. The checked-in lockfile's volatile Mojang version-manifest
  identity was refreshed to the bytes actually consumed by this build
  (`version-manifest-v2-867e9731`, `blake3:867e97315bd3e7100125db20739d0c1ab40ef0a8`).
- R-4A.3 is complete from the fresh cache
  `build/sfm-r4a-cache-20260801-new`, with no injected Gradle property and no
  pre-existing Vox artifact. `jar plan`, `jar build --require-portable-artifacts`,
  and `jar audit-artifacts --require-portable-artifacts` succeeded. The Rust
  artifact is
  `platform/minecraft/build/libs/Super Factory Manager (SFM)-MC1.19.2-4.34.0-rust.jar`
  (2,929,365 bytes); the audit verified 109/109 artifacts with zero errors,
  warnings, or non-portable artifacts. The portable cache also records the Vox
  source-build provenance and exact locked hash.
- R-4A.4 is complete. Default Gradle validation passed with
  `gradlew compileJava testClasses -PsfmProfile=gradle`, and explicit full-profile
  packaging passed with `gradlew jarJar -PsfmProfile=rust-toolchain`. The Gradle
  artifact is
  `platform/minecraft/build/libs/Super Factory Manager (SFM)-MC1.19.2-4.34.0.jar`.
  Its nested Vox JAR and the Rust artifact's nested Vox JAR are both 403,625
  bytes with SHA-256
  `2D0A45E8339BE3229FE657C465470DFA10DF34D040B43F6CD2CD5E6360419007`, and
  their `META-INF/jarjar/metadata.json` entries match exactly for
  `org.facet:vox-java` at `[0.10.0-rc.5]`. The whole-archive comparator reports
  one intentional Gradle-only `SFMClientSmokeRunHarness.class`, with no changed
  or extra entries and identical normalized manifests; deciding whether the
  Rust executor should include that test harness is deferred beyond R-4A.
- R-4A.5 is complete at the release-artifact handoff boundary: the reachable
  source pin, strict acquisition behavior, isolated Rust artifact, full-profile
  Gradle Jar-in-Jar bytes, default Java-only Gradle path, and audit evidence are
  recorded here. The next release work may address clean Forge-style launch
  validation and the deferred smoke-harness difference, but this batch does not
  propagate across Minecraft versions, publish SFM, change release metadata,
  or modify Cloud Terrastodon.
- Final CLI validation: `cargo fmt --all -- --check` passed and the elevated
  `cargo test --offline --lib` suite passed 370 tests with 1 ignored. The
  sandbox-only ripgrep subprocess test also passes when run outside the Codex
  sandbox; its in-sandbox `os_error=5` is an environment limitation.

### Phase 1 — Freeze the release scope [in progress]

**Work:** Decide the release version, supported Minecraft versions, player-facing feature list, CC policy, Vox/terminal policy, feature/profile defaults, and whether the 4.35.0 PRE changelog is the candidate scope. The current direction is to graduate the terminal and command-palette work while keeping the Java-only Gradle profile as contributor compatibility rather than hiding product behavior behind an unmodeled `--slim` switch.

**Validation:** Compare the approved matrix against the changelog, `mods.toml`, dependency lock, active plans, and current generated/version resources. Verify that every advertised integration has a clean-install test or is explicitly labeled experimental/dev-only.

**Completion criteria:** The version and feature matrix is approved, the support statement is written, and the full artifact’s dependency closure—including Vox Jar-in-Jar and the companion-server requirement—is explicit.

### Phase 2 — Commit the current baseline [complete]

**Work:** Commit the current 1.19.2 terminal, command-palette, puppet, CLI-diagnostic, dependency-lock, and plan changes in an intentional checkpoint. Keep Facet/Vox’s existing local commits and Teamy Terminal’s source changes separate from generated build output.

**Validation:** Focused Teamy Terminal Vox tests pass; CLI artifact-lock tests pass; SFM compile and puppet evidence are recorded; each committed repository is clean except for intentionally ignored generated output.

**Completion criteria:** The current work is recoverable from local commits with no source changes silently left outside the checkpoint. This is satisfied by the clean SFM, Facet/Vox, and Teamy Terminal worktrees and the local checkpoint commits.

### Phase 3 — Bundle Vox through Jar-in-Jar [in progress]

**Work:** Add Vox’s explicit bundle policy to the lockfile, preserve compile/runtime projection, and make Gradle select the nested artifact for Forge 1.19.2. Correct the acquisition model so source-built Vox is not represented as a Maven Central publication; add the declarative feature/profile model and bump/migrate the lock schema if required. Gradle’s default profile must exclude Rust-owned sources and artifacts without excluding unrelated integration dependencies.

**Validation:** Rust packaging, lock projection, and legacy Gradle packaging through the self-discovered managed Maven cache are proven as recorded above. Complete the acquisition/profile migration, prove a Java-only Gradle sync/compile with no Cargo/Xtask/Vox artifact, then make the locked Vox source/artifact reproducible from a clean checkout, inspect both outputs, and run a clean Forge-style launch without an external Vox Java dependency.

**Completion criteria:** The explicitly Rust-enabled Gradle artifact is deterministic, unclassified, and contains the required nested Vox runtime with valid Forge metadata; the default Java-only Gradle artifact remains compilable without Rust setup; and the source/artifact provenance is reproducible from a clean checkout.

### Phase 4 — Match the Rust artifact builder [in progress]

**Work:** Keep the existing Forge/NeoGradle Rust Jar-in-Jar emission covered by the lock policy, close the portable source-build/provenance path, and make the Rust entry point select the full feature profile by default. The Forge emission and deterministic unit tests are already present.

**Validation:** Targeted Rust JarJar tests and the explicit-source full artifact pass. Re-run the artifact audit with `--require-portable-artifacts` after the Facet revision is reachable, compare nested entries and metadata with Gradle, and run an external-style launch.

**Completion criteria:** Rust and Gradle agree on Vox bundle contents, metadata, artifact naming, dependency closure, and runtime availability from reproducible inputs. A missing Rust service does not break Java-local behavior.

### Phase 5 — Graduate feature acceptance and companion distribution [ ]

**Work:** Close the remaining terminal and command-palette release gates, document how `teamy-terminal.exe` is installed or launched, retain the Java-local fallback, and keep CC as an external optional integration. Verify that the Gradle Java-only profile is a contributor path while the Rust CLI full profile remains the release path; do not use profile selection to conceal missing functionality.

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
- **Gradle resolution:** The lock projection correctly requests `jarJar`, and legacy Gradle now consumes the source-built artifact through the self-discovered SFM-managed Maven repository. The final projection must use exclusive source-build content so a missing local artifact cannot fall through to unrelated remotes.
- **Repository candidate selection:** The resolver's preferred repository table currently uses display names while schema-v3 projected repositories use canonical IDs. Correct the mapping and add a test proving `org.facet` only probes `maven-central`; then repeat the source-build fallback experiment before changing the Vox pin.
- **Companion-server distribution:** Bundling Vox does not bundle `teamy-terminal.exe`; document and test the external server path and graceful fallback.
- **Local branch divergence:** 1.19.2 contains unpublished local checkpoint commits. Create a reviewed checkpoint ref before publishing; do not push as part of this plan without approval.
- **Open change intake:** PR #582 needs correctness and benchmark review; PR #486 is non-mergeable and should not enter the release by default.
- **Environment bookkeeping:** One feature worktree could not be inspected because Git safe-directory ownership differs for the sandbox user. Do not alter global Git configuration just to make the release appear clean.

## Overall completion criteria

This plan is complete only when:

1. The release version, support statement, and feature/dependency matrix are approved.
2. Rust-toolchain full behavior and Gradle Java-only contributor behavior are separately verified.
3. The Rust full artifact contains the required Vox nested JAR and valid loader metadata.
4. Gradle and Rust artifact builders agree on nested-JAR contents, dependency closure, and publication rules when the Rust feature is explicitly enabled.
5. The companion Rust server has a documented distribution path and graceful absence behavior.
6. The release candidate passes clean-install, external-style launch, gameplay/puppet, and cross-version checks required by the approved matrix.
7. PRs/issues have explicit disposition, release notes are ready, and a checkpoint ref is recorded.
8. Publishing or pushing has been separately approved and is not implied by completing the plan.
