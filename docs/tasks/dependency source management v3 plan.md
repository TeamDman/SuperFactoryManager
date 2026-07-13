# Dependency and Source Management V3 Plan

**Plan status:** Active

**Primary implementation branch:** `1.19.2`

**Last updated:** 2026-07-12

**Primary implementation root:** `D:\Repos\Minecraft\SFM\repos2\1.19.2`

## How to update this plan

Each work item carries its status in its heading. Update the heading and the completion notes together so progress is visible where the work is described.

- `[ ]` Not started
- `[~]` In progress
- `[x]` Complete
- `[!]` Blocked

A phase is complete only when every work item in that phase is marked `[x]`. Do not add a detached work log at the end of this document. Record commit IDs, implementation decisions, validation results, and follow-up notes directly under the relevant work item.

## Purpose

Make `sfm-propagate-changes` the source of truth for Minecraft platform dependencies, loader dependencies, mod dependencies, resolved artifacts, and source acquisition. Gradle remains a compatibility consumer of the lockfile rather than an independent dependency declaration system.

The public UX is dependency-first:

```text
sfm-propagate-changes.exe dependency list --branch 1.19.2
sfm-propagate-changes.exe dependency show cc-tweaked --branch 1.19.2
sfm-propagate-changes.exe dependency source acquire cc-tweaked --provider any --branch 1.19.2
sfm-propagate-changes.exe dependency source search IPeripheralProvider --branch 1.19.2
```

Minecraft, the active loader, mods, libraries, and tools can have different acquisition implementations while still appearing through one dependency-oriented command surface.

## Confirmed design decisions

1. Commands that resolve an SFM worktree require an explicit `--branch`. Dependency commands initially require a selector that resolves exactly one worktree.
2. Schema v3 is added through the existing versioned Rust schema modules. V1 and v2 remain readable as legacy inputs, but an explicit migration command is required before they become authoritative v3 files.
3. The existing `sfm-toolchain.lock.json` becomes the single file containing maintained dependency intent and generated resolution state. No separate `sfm-sources.json` is introduced.
4. Dependency intent is loader-neutral. V3 stores semantic scopes such as `compile`, `runtime`, and `gametest-runtime`, not Gradle configuration names. Gradle syntax such as `fg.deobf(...)`, `minecraft`, or plain `implementation` is emitted by a loader/version-specific adapter and is not stored as raw Groovy.
5. Mods are excluded from data runs by default. A dependency must explicitly opt into a data run.
6. `dependency source search` never acquires sources. It searches available roots, clearly warns when results are incomplete, and renders the typed command needed to acquire missing sources.
7. `jar sources` is removed during the cutover. It is not retained as an alias.
8. `source audit` is flattened to the top-level `audit` command.
9. Managed Git source acquisition uses `gix`. The toolchain does not discover or depend on user-specific clones such as `G:\Programming\Repos\CC-Tweaked`.
10. Git repositories share a managed bare object database across revisions. Searchable source trees are materialized by exact commit without independent full clones for each mod version.
11. CurseForge discovery is separate from dependency mutation. `dependency add` requires exact project and file IDs and never chooses a file on the user's behalf.
12. Work begins on `1.19.2` and propagates forward according to `docs/AGENTS.md`.
13. Human/CLI-maintained declaration fields and CLI-generated checks live in clearly separated objects inside the same lockfile. The provisional names are `declaration` and `derived_checks`.
14. Maven source archives are source payloads, not build dependency artifacts. They may reuse low-level HTTP, hash, cache-lock, and archive code, but they never enter build classpaths or the binary artifact inventory.
15. Source providers are inspectable and selectable. `dependency source provider list` describes providers, and `dependency source acquire --provider <selector>` chooses one without changing the locked revision.
16. SFM-owned Rust serialization uses Facet, not Serde. Clippy forbids known Serde/serde_json API entry points, and the quality gate rejects direct `serde` or `serde_json` dependencies while tolerating unavoidable transitive use by third-party crates.

## Proposed v3 concepts

The exact Rust names may change during implementation, but schema v3 must preserve these concepts.

```text
ArtifactLockfileV3
  platform
    minecraft
    loader
  repositories
  dependencies
    logical dependency id
    kind and role
    components
      declaration
        requested coordinate or CurseForge identity
        semantic scopes
        loader-managed mod or plain artifact treatment
        run inclusion policy
      derived_checks
        resolved coordinate
        artifact identity and expected hash
        portable cache path
    source provider declarations
    source payload derived checks
  artifacts
    build/runtime URL, repository, hash, cache path, and provenance
```

Logical dependencies support multiple components. For example, Mekanism may have a CurseForge runtime component and a Maven API component while appearing as one dependency in `dependency list`.

Recommended dependency roles:

- `platform`: Minecraft and the active loader
- `integration`: optional mod integration used by SFM
- `build`: code generation, annotation processing, or packaging
- `test`: unit-test and game-test support
- `library`: non-mod runtime or compile library

Avoid an ambiguous `optional` boolean. An integration can be optional to players while its compile component is required to build SFM.

Recommended source kinds:

- `minecraft-pipeline`
- `loader-pipeline`
- `maven-sources`
- `git`
- `decompile`

Recommended semantic scopes:

- `compile`
- `runtime`
- `gametest-compile`
- `gametest-runtime`
- `test-compile`
- `test-runtime`
- `bundle`

The initial artifact treatment has two semantic values:

- `loader-managed-mod`: the component is a mod artifact and must be declared through the normal development-mapping mechanism for the selected loader. On 1.19.2 ForgeGradle this produces `fg.deobf(...)`; on later NeoGradle branches it may produce a plain dependency declaration because the loader already handles mod artifacts appropriately.
- `plain`: the component is an ordinary library, annotation processor, or already development-mapped API artifact and must not be passed through a mod remapping wrapper.

Do not add a third `required` treatment unless a concrete artifact demonstrates an invariant that the two-value model cannot express.

These fields answer separate questions: scopes say where code is visible, artifact treatment says how the selected loader must prepare the artifact, and run policy says which generated run classpaths include it. In particular, `loader-managed-mod` replaces a stored `deobfuscate` flag; it does not imply that every loader adapter literally emits a deobfuscation wrapper.

## Proposed command surface

```text
sfm-propagate-changes.exe audit --branch <selector>

sfm-propagate-changes.exe dependency list --branch <branch>
sfm-propagate-changes.exe dependency show <dependency[/component]> --branch <branch>
sfm-propagate-changes.exe dependency add <dependency> --branch <branch> <source> <scopes>
sfm-propagate-changes.exe dependency component add <dependency> <component> --branch <branch> <source> <scopes>
sfm-propagate-changes.exe dependency remove <dependency[/component]> --branch <branch>
sfm-propagate-changes.exe dependency refresh --branch <branch>
sfm-propagate-changes.exe dependency migrate --branch <branch> [--check]
sfm-propagate-changes.exe dependency artifact accept <dependency[/component]> --branch <branch> [validation options]

sfm-propagate-changes.exe dependency source configure <dependency[/component]> --branch <branch> <source options>
sfm-propagate-changes.exe dependency source provider list [dependency[/component]] --branch <branch>
sfm-propagate-changes.exe dependency source acquire <dependency[/component]> --provider <selector> --branch <branch>
sfm-propagate-changes.exe dependency source acquire --all --provider <selector> --branch <branch>
sfm-propagate-changes.exe dependency source search <pattern> --branch <branch> [--require-complete]
sfm-propagate-changes.exe dependency source search <pattern> --dependency <id> --provider <selector> --branch <branch> [--require-complete]

sfm-propagate-changes.exe curseforge mod search <query> --minecraft <version> --loader <loader>
sfm-propagate-changes.exe curseforge mod files <project-id> --minecraft <version> --loader <loader>
```

Provider selectors are typed values. `any` asks the dependency's ordered provider set for the first usable locked provider. Other selectors include stable kinds such as `maven-sources`, `git`, `decompile`, `minecraft-pipeline`, and `loader-pipeline`, plus a stable provider ID when a dependency has multiple providers of one kind. CurseForge/CurseMaven are binary origins unless a specific source payload is actually available; they must not be presented as source providers merely because the binary came from CurseForge.

## Execution order and phase dependencies

The critical path is:

```text
Phase 1 Facet guardrails and Facet/Figue
  -> Phase 2 schema v3 and migration
  -> Phase 3 dependency read model
  -> Phase 4 mutation commands
  -> Phase 5 Rust planner cutover
  -> Phase 6 Gradle consumer cutover
```

Source work proceeds after the v3 source-provider shape is stable:

```text
Phase 2 source schema
  -> Phase 7 provider/cache architecture
  -> Phase 8 gix repository management
  -> Phase 9 source CLI
  -> Phase 11 CC:Tweaked acceptance
```

Phase 10 CurseForge discovery can proceed after the v3 dependency declaration shape and HTTP client boundaries are stable. Phase 12 begins only after all earlier phase gates pass on `1.19.2`.

## Assistive assets

### SFM guidance and current implementation

- `D:\Repos\Minecraft\SFM\repos2\1.19.2\docs\AGENTS.md`
- `D:\Repos\Minecraft\SFM\repos2\1.19.2\platform\cli\sfm-propagate-changes\Cargo.toml`
- `D:\Repos\Minecraft\SFM\repos2\1.19.2\platform\cli\sfm-propagate-changes\check-all.ps1`
- `D:\Repos\Minecraft\SFM\repos2\1.19.2\platform\cli\sfm-propagate-changes\src\cli\cli.rs`
- `D:\Repos\Minecraft\SFM\repos2\1.19.2\platform\cli\sfm-propagate-changes\src\cli\dependency`
- `D:\Repos\Minecraft\SFM\repos2\1.19.2\platform\cli\sfm-propagate-changes\src\cli\source`
- `D:\Repos\Minecraft\SFM\repos2\1.19.2\platform\cli\sfm-propagate-changes\src\cli\jar\jar_sources_cli.rs`
- `D:\Repos\Minecraft\SFM\repos2\1.19.2\platform\cli\sfm-propagate-changes\src\toolchain_lockfile_schema`
- `D:\Repos\Minecraft\SFM\repos2\1.19.2\platform\cli\sfm-propagate-changes\src\jar_build\engine_model.rs`
- `D:\Repos\Minecraft\SFM\repos2\1.19.2\platform\cli\sfm-propagate-changes\src\jar_build\engine_plan.rs`
- `D:\Repos\Minecraft\SFM\repos2\1.19.2\platform\cli\sfm-propagate-changes\src\jar_build\engine_run.rs`
- `D:\Repos\Minecraft\SFM\repos2\1.19.2\platform\cli\sfm-propagate-changes\src\jar_build\engine_sources.rs`
- `D:\Repos\Minecraft\SFM\repos2\1.19.2\platform\cli\sfm-propagate-changes\src\jar_build\resolve.rs`
- `D:\Repos\Minecraft\SFM\repos2\1.19.2\platform\minecraft\sfm-toolchain.lock.json`
- `D:\Repos\Minecraft\SFM\repos2\1.19.2\platform\minecraft\gradle\dependencies`
- `D:\Repos\Minecraft\SFM\repos2\1.19.2\platform\minecraft\gradle\repositories.gradle`

### Facet and Figue references

- Facet fork: `G:\Programming\Repos\facet`
- Figue package: `G:\Programming\Repos\facet\figue`
- Typed argument rendering: `G:\Programming\Repos\facet\figue\src\to_args.rs`
- Reference pinned revision: `585826b51ae771950970849e616e28c6c7207f45`
- Reference manifest: `D:\Repos\Azure\Cloud-Terrastodon\Cargo.toml`
- Reference manifest: `G:\Programming\Repos\teamy-mft\Cargo.toml`

### Rust serialization guardrail references

- Existing SFM Clippy configuration: `D:\Repos\Minecraft\SFM\repos2\1.19.2\platform\cli\sfm-propagate-changes\clippy.toml`
- Existing SFM lint levels: `D:\Repos\Minecraft\SFM\repos2\1.19.2\platform\cli\sfm-propagate-changes\Cargo.toml`
- Existing SFM quality gate: `D:\Repos\Minecraft\SFM\repos2\1.19.2\platform\cli\sfm-propagate-changes\check-all.ps1`
- Reference configuration: `G:\Programming\Repos\teamy-rust-cli\clippy.toml`
- Reference lint levels: `G:\Programming\Repos\teamy-rust-cli\Cargo.toml`

### Gitoxide references

- Gitoxide checkout: `G:\Programming\Repos\gitoxide`
- Bare clone entry point: `G:\Programming\Repos\gitoxide\gix\src\lib.rs`
- Worktree streaming/archive APIs: `G:\Programming\Repos\gitoxide\gix\src\repository\worktree.rs`
- Feature definitions: `G:\Programming\Repos\gitoxide\gix\Cargo.toml`

### Decompiler references and observed artifacts

- Locked 1.19.2 tool: `net.minecraftforge:forgeflower:1.5.605.9` in `platform\minecraft\sfm-toolchain.lock.json`
- SFM MCP execution: `platform\cli\sfm-propagate-changes\src\jar_build\engine_execute.rs`
- SFM MCP planning: `platform\cli\sfm-propagate-changes\src\jar_build\engine_plan.rs`
- ForgeFlower checkout: `G:\Programming\Repos\Minecraft\MinecraftForge\ForgeFlower`
- FernFlower checkout: `G:\Programming\Repos\Minecraft\MinecraftForge\FernFlower`
- Vineflower checkout: `G:\Programming\Repos\Minecraft\ToolchainResearch\vineflower`
- NeoForge Vineflower plugins: `G:\Programming\Repos\Minecraft\NeoForged\VineflowerPlugins`
- ForgeFlower upstream: `https://github.com/MinecraftForge/ForgeFlower`
- Vineflower upstream and releases: `https://github.com/Vineflower/vineflower`, `https://github.com/Vineflower/vineflower/releases`
- NeoForge plugins upstream: `https://github.com/neoforged/VineflowerPlugins`
- Observed cached ForgeFlower versions: `1.5.605.9`, `2.0.627.2`, and `2.0.629.0`
- Observed cached Vineflower versions: `1.9.3`, `1.10.1`, and `1.11.2`; NeoForge plugin version `0.1.5`

### CC:Tweaked acceptance fixture

- Assistive checkout only: `G:\Programming\Repos\CC-Tweaked`
- Repository: `https://github.com/cc-tweaked/CC-Tweaked.git`
- Release tag: `v1.19.2-1.101.3`
- Locked commit: `f9bb1b497964cccab6cde34e8948333210275f93`
- Maven artifact: `org.squiddev:cc-tweaked-1.19.2:1.101.3`
- Maven sources artifact: `org.squiddev:cc-tweaked-1.19.2:1.101.3:sources`
- Maven directory: `https://squiddev.cc/maven/org/squiddev/cc-tweaked-1.19.2/1.101.3/`
- SFM integration issue: `https://github.com/TeamDman/SuperFactoryManager/issues/437`
- Turtle/label-gun issue: `https://github.com/TeamDman/SuperFactoryManager/issues/261`
- Existing smoke test: `platform/minecraft/src/gametest/java/ca/teamdman/sfm/gametest/tests/compat/computercraft/ComputerCraftDependencySmokeGameTest.java`

## Phase 0: Established baseline

### [x] 0.1 Add and verify the CC:Tweaked binary dependency

**Completion notes:** Merged into `1.19.2` by merge commit `47fc5437c`. The feature commit is `6bb45979e`. The feature worktree and branch were removed after merging.

**Completed work:**

- Added the SquidDev Maven repository.
- Locked `org.squiddev:cc-tweaked-1.19.2:1.101.3`.
- Added a focused game test that places a turtle and disk drive and checks the installed API version.

**Validation completed:**

```pwsh
cargo run -- run compile --branch 1.19.2
cargo run -- run game-test-server --branch 1.19.2 --filter computer_craft_dependency_smoke
```

### [x] 0.2 Identify authoritative CC:Tweaked source inputs

**Completion notes:** Confirmed that the SquidDev Maven publication includes a sources JAR. Cloned the upstream repository as an assistive reference and checked out the exact release tag and commit listed above.

**Completion criteria:**

- [x] Maven source availability known.
- [x] Git URL known.
- [x] Exact Git commit known.
- [x] Source roots observable under `src/main/java` and `src/main/resources`.

### [x] 0.3 Confirm existing data-run behavior

**Completion notes:** `run_dependency_configurations(RunKind::Data)` currently returns only `jarJar`, and `datagen_launch_uses_only_bundled_library_dependency_configurations` verifies that `implementation` and `runtimeOnly` are excluded. Schema v3 must preserve this behavior as a default policy rather than carrying forward the old Mouse Tweaks-only Gradle condition.

**Relevant paths:**

- `platform/cli/sfm-propagate-changes/src/jar_build/engine_run.rs`
- `platform/cli/sfm-propagate-changes/src/jar_build/engine_tests.rs`

### [x] 0.4 Inventory existing Flower decompiler inputs

**Completion notes:** The local artifact inventory and current lockfile were inspected on 2026-07-09. The 1.19.2 MCP pipeline locks and invokes ForgeFlower `1.5.605.9`. Other SFM branch outputs contain ForgeFlower `2.0.627.2`/`2.0.629.0` and Vineflower `1.9.3`/`1.10.1`/`1.11.2`; the 26.1.2 output also contains NeoForge Vineflower plugins `0.1.5`. Local source checkouts for ForgeFlower, FernFlower, Vineflower, and the NeoForge plugins are available at the paths above. Vineflower `1.12.0` was the latest upstream release observed during this review and must be rechecked when Phase 7 begins.

**Established constraints:**

- Minecraft decompilation is part of a loader-authored pipeline whose patches and recompilation may depend on exact decompiler output.
- A mod-source fallback is a separate use case and does not need to inherit the Minecraft pipeline's older decompiler.
- Vineflower is the leading candidate for standalone mod fallback because it is the actively released continuation focused on output quality, but selection remains subject to the deterministic comparison in Phase 7.

## Phase 1: Establish Facet guardrails and upgrade Facet/Figue

### [x] 1.1 Forbid Serde APIs and direct dependencies

**Completion notes:** Completed on 2026-07-09. Extended the existing `clippy.toml` restrictions with the planned serde_json methods, Serde traits, and `serde_json::json`, using `allow-invalid = true` so a Serde-free direct dependency graph remains valid. Set `disallowed_methods`, `disallowed_types`, and `disallowed_macros` to `forbid` in `Cargo.toml`. Added a `cargo metadata --no-deps` policy check to `check-all.ps1` that inspects actual package names, including renamed, development, build, and target-specific direct dependencies.

**Validation completed:**

- An isolated manifest containing direct `serde` and `serde_json` dependencies was rejected with `Direct Serde dependencies are forbidden; use Facet instead: serde, serde_json`.
- An isolated Clippy probe emitted the configured forbidden-type diagnostics for `serde::Serialize` and `serde::Deserialize`, the forbidden-method diagnostic for `serde_json::from_str`, and the forbidden-macro diagnostic for `serde_json::json`, including the configured Facet replacement reasons.
- `check-all.ps1` passed formatting, Clippy, build, and all 157 tests in the real SFM CLI crate.

**Affected paths:**

- `platform/cli/sfm-propagate-changes/clippy.toml`
- `platform/cli/sfm-propagate-changes/Cargo.toml`
- `platform/cli/sfm-propagate-changes/check-all.ps1`

**Work:**

- Preserve the existing `std::env::set_var` and `std::ptr::addr_of_mut` restrictions.
- Disallow `serde_json::from_str`, `from_slice`, `to_string`, `to_string_pretty`, `to_writer`, and `to_writer_pretty`, with reasons naming their `facet_json` replacements.
- Disallow the `serde::Serialize` and `serde::Deserialize` traits and the `serde_json::json` macro.
- Set `allow-invalid = true` on Serde paths that are intentionally unresolved when Serde is absent. The independent direct-dependency check remains authoritative for preventing those crates from entering the graph.
- Explicitly set Clippy's `disallowed_methods`, `disallowed_types`, and `disallowed_macros` lint levels to `forbid`; configuring lists in `clippy.toml` is not sufficient unless the restriction lints are enabled.
- Add a quality-gate check based on Cargo metadata that rejects direct package dependencies named `serde` or `serde_json`, including renamed dependency keys and normal, development, build, and target-specific dependency tables.
- Do not reject transitive Serde dependencies required internally by third-party crates. The policy governs SFM's direct dependency and API surface.
- Record isolated or temporary negative probes proving a forbidden serde_json call, Serde trait use, and direct dependency each fail for the intended reason; bypass the direct-dependency check only for the API-lint probes, and remove all probe code before completion.

**Completion criteria:**

- `cargo clippy --all-features -- -D warnings` enforces every configured restriction.
- `check-all.ps1` rejects direct `serde`/`serde_json` dependencies even when no forbidden API is called.
- Existing Facet JSON call sites remain accepted.
- The Rust quality gate passes with no tracked Serde probe code or direct Serde dependency.

### [x] 1.2 Pin all Facet-family dependencies to the maintained fork

**Completion notes:** Completed on 2026-07-11 and advanced on 2026-07-12. `facet`, `facet-json`, `facet-styx`, and `figue` are pinned to TeamDman's Facet fork at `f1e1eed9a7d91c6bbb4dcb54c985617d6c914fad`, with Figue's `arbitrary` feature enabled and `Cargo.lock` regenerated. `facet`/`facet-json` resolve as `0.50.0-rc.5` and `facet-styx`/`figue` as `5.0.0-rc.5`, all from that one Git revision. The newer Figue revision fixes typed rendering for an omitted `Option<String>` positional and scalar provider selectors; focused source-command round-trip tests pass without SFM workarounds. No registry `teamy-figue` remains.

**Affected paths:**

- `platform/cli/sfm-propagate-changes/Cargo.toml`
- `platform/cli/sfm-propagate-changes/Cargo.lock`

**Work:**

- Replace registry-pinned `facet`, `facet-json`, and `facet-styx` dependencies with Git dependencies from `https://github.com/TeamDman/facet.git`.
- Replace the `teamy-figue` package dependency with the `figue` package from the same Git revision.
- Initially pin revision `585826b51ae771950970849e616e28c6c7207f45`, matching Cloud-Terrastodon, teamy-mft, and the current local Facet checkout.
- Enable Figue's `arbitrary` feature if required by the planned round-trip tests.
- Keep every directly used Facet-family crate on one revision to avoid duplicate incompatible type universes.

**Completion criteria:**

- `cargo tree` shows one intended Facet/Figue Git revision.
- No registry `teamy-figue` remains.
- `Cargo.lock` contains the exact Git revision.

**Validation:**

```pwsh
cargo check
cargo tree
```

### [x] 1.3 Migrate SFM CLI code to the updated Facet/Figue APIs

**Completion notes:** Completed on 2026-07-11. Replaced Figue's removed `args::long_alias` annotation with `args::alias`. Declared `BranchSelector` as a `String` proxy and `SourceLineLimit` as a `usize` proxy, with bidirectional conversions, because current Figue intentionally rejects struct-shaped values in single CLI fields. All 36 focused CLI parser tests pass, including required branch selectors, aliases, optional-value flags, subcommands, and logging options. Existing v1/v2 lockfile tests also pass under the full suite.

**Affected paths:**

- `platform/cli/sfm-propagate-changes/src/cli`
- `platform/cli/sfm-propagate-changes/src/toolchain_lockfile_schema`
- Facet-derived models throughout `src`

**Work:**

- Resolve compilation changes caused by the newer derives, schema inspection, defaults, aliases, and Figue parser behavior.
- Keep `--branch` required for commands that resolve worktrees.
- Confirm help, completions, flattened arguments, optional subcommands, aliases, positional arguments, and default-true booleans still behave intentionally.
- Do not mix the schema v3 redesign into this item beyond changes required for the dependency upgrade.

**Completion criteria:**

- Existing CLI parser tests pass without silently weakening assertions.
- Existing v1/v2 lockfiles still deserialize exactly as before.
- Existing commands retain their documented behavior until their planned cutover phase.

### [x] 1.4 Add typed CLI rendering and round-trip coverage

**Completion notes:** Completed on 2026-07-11. Added a dedicated CLI rendering test module. The top-level typed `Cli::Audit` value now renders with `ToArgs`, round-trips through Figue parsing, preserves a branch selector containing spaces, and exercises both display-only and current-executable command rendering. A focused positional probe verifies that dash-prefixed values emit `--` and round-trip. Figue's arbitrary consistency and round-trip helpers pass for generated positional values. Acquire-command recommendation coverage remains under 9.5, after that command exists.

**Assistive APIs:**

- `figue::to_args_string`
- `figue::to_args_string_with_current_exe`
- `figue::ToArgs`
- `figue::assert_to_args_roundtrip`
- `figue::assert_to_args_consistency`

**Work:**

- Add focused tests proving typed CLI values render back into parseable argument vectors.
- Add representative round trips for dependency and source command structs as those commands are introduced.
- Use `to_args_string_with_current_exe` for user-facing recommended commands.
- Treat rendering as display-oriented. Do not execute rendered shell strings; execute typed operations directly.

**Completion criteria:**

- A representative existing typed source command round-trips through Figue.
- Values containing spaces and dash-prefixed positionals are rendered and parsed correctly.
- Arbitrary consistency and round-trip helpers execute successfully in SFM's test suite.

### [x] 1.5 Pass the Rust quality gate after the dependency upgrade

**Completion notes:** Completed on 2026-07-11. `check-all.ps1` passed the direct-dependency policy, formatting, Clippy, all-feature build, and all 160 tests against the pinned Facet/Figue graph, including the typed-rendering coverage from 1.4.

**Completion criteria:**

```pwsh
.\check-all.ps1
```

passes from `platform/cli/sfm-propagate-changes` before schema v3 work begins.

## Phase 2: Define and implement lockfile schema v3

### [x] 2.1 Finalize the v3 declaration and resolution model

**Completion notes:** Completed 2026-07-11. Introduced `ENGINE_SCHEMA_VERSION = 2` so the legacy engine cannot serialize its v2 model with a false version while the schema layer advances independently. The strict v3 model uses logical dependencies with one or more components, semantic scopes, explicit artifact treatment and data-run policy, and matched acquisition/source-provider variants. Maintained intent lives under `declaration`; reproducible assertions live under `derived_checks`. Root artifacts retain provenance, hashes, portable cache paths, and weak-validation state. Source declarations identify managed Maven sources, Git revisions, decompilation inputs, or platform pipelines without machine-specific checkout paths.

**Required semantics:**

- Platform Minecraft dependency and loader dependency.
- Stable logical dependency IDs.
- Dependency kind and role.
- One or more components per logical dependency.
- Requested and resolved artifact identities.
- Maven and CurseForge acquisition declarations.
- Repeatable semantic compile/runtime/game-test scopes.
- Loader-managed-mod or plain artifact treatment.
- Default-excluded data-run policy with explicit opt-in.
- Source declarations and exact source locks.
- Artifact provenance, hash, cache path, and weak validation from v2.
- Clear `declaration` and `derived_checks` ownership boundaries.
- Optional display metadata such as project URL and notes when it materially replaces useful comments from Gradle files.

**Completion criteria:**

- The model represents CC:Tweaked, AE2's API classifier, and Mekanism's CurseForge runtime plus Maven API without duplicate logical dependencies.
- No field embeds raw Gradle/Groovy syntax.
- No field embeds a machine-specific source checkout path.
- Required v3 declaration fields are not optional merely to accommodate incomplete legacy data.

### [x] 2.2 Add the v3 schema module and legacy-version dispatch

**Completion notes:** Completed 2026-07-11. Added `version/v3.rs`, raised `LATEST_SCHEMA_VERSION` to 3, and added explicit `ToolchainLockfileDocument::V1`, `V2`, and `V3` dispatch. V3 parsing performs strict structural and cross-reference validation. The legacy build engine remains explicitly pinned to v2 and rejects a valid v3 document with a focused diagnostic until the Phase 5 cutover. Schema round-trip and invalid duplicate/mismatched-derived-check tests pass; the full gate passes 163 tests.

**Affected paths:**

- `src/toolchain_lockfile_schema/version/v3.rs` (new)
- `src/toolchain_lockfile_schema/version/mod.rs`
- `src/toolchain_lockfile_schema/api.rs`
- `src/toolchain_lockfile_schema/preflight_document.rs`
- `src/jar_build/engine_model.rs`

**Work:**

- Add `v3.rs` beside the existing v1 and v2 modules.
- Change `LATEST_SCHEMA_VERSION` from 2 to 3.
- Add explicit v1, v2, and v3 dispatch arms.
- Keep v1/v2 readable as legacy models without inventing authoritative v3 values that cannot be derived.
- Permit read-only diagnostics and migration on legacy lockfiles; require v3 for normal mutation after the cutover.
- Preserve v2 weak artifact validation.
- Continue rejecting unsupported future versions with an actionable error.

**Completion criteria:**

- V1 and v2 load into explicit legacy inputs suitable for diagnostics/migration.
- V3 loads into the strict current model.
- A valid v3 document round-trips through the strict current model while the legacy v2 engine rejects it explicitly rather than misinterpreting it.
- V1/v2-only malformed-field checks remain covered.

### [x] 2.3 Add optional legacy migration hints and strict diagnostics

**Completion notes:** Completed 2026-07-11. Added an optional v2-only `migration_hints` object with platform dependency IDs and logical dependency/component hints. Component hints identify legacy dependency rows by index and use strict v3 enums for kind, role, semantic scopes, artifact treatment, data-run policy, and optional explicit acquisition/artifact evidence when v2 has no corresponding dependency row. V1 normalizes losslessly to v2 in memory and receives an actionable materialization/hints diagnostic. Deterministic structured diagnostics carry a field path, message, legacy row index/configuration/coordinate where applicable, candidate values, and remediation. Validation accumulates missing fields, duplicate IDs, duplicate/out-of-range row ownership, uncovered rows, empty/duplicate scopes, invalid platform references, inconsistent grouped coordinates/cache paths, malformed CurseMaven IDs, missing/ambiguous artifacts, and invalid repository provenance in one pass. Source providers remain empty when legacy evidence cannot prove them rather than inventing metadata. A complete report constructs, serializes, reparses, and strictly validates v3 with no migration-only fields. The real 1.19.2 migration iterated from missing hints to zero diagnostics. The full gate passes 169 tests.

**Work:**

- Add a backwards-compatible optional `migration_hints` object to the v2 lockfile/parser without weakening v3.
- Permit hints for logical dependency IDs, component grouping, semantic scopes, artifact treatment, roles, and any other facts unavailable from v2.
- Infer what can be proven from the v2 lock and current versioned Gradle declarations.
- For a v1 input, first perform the existing lossless v1-to-v2 normalization in memory; if hints are required, emit an actionable diagnostic that materializes or requests a v2 intermediate before v3 migration.
- Emit one structured diagnostic for every missing or ambiguous required v3 field.
- Include the dependency coordinate, candidate values, affected path, and remediation in each diagnostic.
- Allow an agent/developer to populate hints and rerun migration until diagnostics are empty.
- Keep migration hints out of the final strict v3 model.

**Completion criteria:**

- Migration fails before writing when any required v3 value remains unknown.
- Diagnostics are sufficient to update the v2 migration hints without reading migration code.
- A successful migration writes a strict v3 file with no migration-only optional fields.

### [x] 2.4 Implement `dependency migrate`

**Completion notes:** Completed 2026-07-11. Added the typed `dependency migrate --branch <selector> [--check]` command with exact-one-worktree selection. It reads through versioned dispatch, normalizes v1 to v2 in memory, reports every correction with candidates and row context, treats v3 as already current, and never writes in check mode. Complete evidence constructs a strict v3 candidate and prints deterministic pretty JSON as the check preview. Write mode uses a synced sibling temporary file, verifies the source lockfile has not changed concurrently, and atomically persists the validated candidate; no side backup is created because Git is authoritative, while every failure preserves the original. Migrated the real 1.19.2 lockfile from 52 legacy rows to 41 logical dependencies and 106 inventory artifacts. CC:Tweaked appears once with compile/runtime/game-test scopes; AE2 and Mekanism have main/API components; API artifacts are plain; all mod components default to data-run exclusion. A subsequent check reports the lockfile already uses v3. Parser, round-trip, atomic replacement, and concurrent-change coverage pass; the full gate passes 169 tests.

**Command:**

```pwsh
sfm-propagate-changes.exe dependency migrate --branch 1.19.2 --check
sfm-propagate-changes.exe dependency migrate --branch 1.19.2
```

**Work:**

- Read the legacy lockfile, optional migration hints, and the current Gradle declarations as one-time migration evidence.
- Produce a deterministic preview of logical dependencies, components, scopes, artifact treatment, and policies.
- Make `--check` perform all validation without writing.
- Write v3 atomically only when no migration diagnostics remain.
- Never partially rewrite a v2 file after a failed migration.
- After all branches migrate, remove production dependence on Gradle parsing while retaining fixtures that prove the old inputs migrate correctly.

**Completion criteria:** Every supported branch can reach a strict v3 lock by iterating on informative diagnostics rather than weakening the v3 schema. Successful migration serializes schema version 3.

### [x] 2.5 Implement deterministic v3 writing and declaration preservation

**Completion notes:** Completed 2026-07-11. Added schema-layer canonical JSON writing used by migration and future mutation commands. Canonicalization sorts repositories, logical dependencies, components, semantic scopes, source providers, provider roots, artifacts, and artifact purposes by stable IDs/enum order and removes duplicate repeatable values. Existing v3 lockfiles can be checked or atomically canonicalized through `dependency migrate`; two successive writes of the 1.19.2 lockfile retained SHA-256 `CB784CF909BCB2DB4A75B8F614576E0783357593F109318D94AF8C8AF8F8AC7C`. Added declaration-preserving derived refresh: dependency metadata, roles, notes, policies, component declarations, requested revisions, and provider declarations remain from maintained state while component/provider `derived_checks` and the artifact inventory are replaced from validated resolution. Missing or changed dependency/component/provider topology is rejected before replacement. Tests prove unrelated AE2 source metadata survives a CC:Tweaked refresh and manually changed requested revisions are not overwritten by resolved state. The full gate passes 172 tests.

**Work:**

- Store maintained intent under `declaration` and generated assertions under `derived_checks`, or final names with equally clear ownership.
- Make refresh preserve IDs, source declarations, requested revisions, roles, notes, scopes, artifact treatment, and policy fields.
- Replace `derived_checks` atomically from current resolution inputs.
- Canonically order dependencies, components, scopes, sources, repositories, and artifacts.
- Write lockfiles atomically.
- Avoid broad unrelated lockfile churn during a single dependency mutation.

**Completion criteria:**

- Two refreshes with unchanged remote inputs produce byte-identical JSON.
- Adding or refreshing one dependency does not discard another dependency's source metadata.
- No separate declaration file is required.
- Manual edits to `declaration` are validated; manual edits to `derived_checks` are either replaced or rejected with an explicit diagnostic.

### [x] 2.6 Add schema fixtures, migration, and round-trip tests

**Completion notes:** Completed 2026-07-11. Readable fixtures are the minimal inline v1 document in `src/toolchain_lockfile_schema/api.rs`, focused v2 builders/JSON rows in `version/v2_migration.rs`, the compact v3 builder in `version/v3.rs`, and the checked-in real `platform/minecraft/sfm-toolchain.lock.json` consumed by `version/v3_write.rs`. Explicit tests cover v1 normalization with actionable missing-hints diagnostics, v1 rejection of v2-only weak fields, v2 no-hints and complete-hints paths, weak validation preservation into v3, strict minimal round trips, the real AE2/Mekanism multi-component shape, exact Git requested revision/commit replacement behavior, Maven sources archive/tree paths, semantic annotation/compile/runtime/game-test scopes, loader-managed versus plain API treatment, default mod exclusion and explicit platform inclusion for data runs, future schema rejection, canonical byte stability, and rejection of absolute machine-specific paths while `$sfm-cache` paths validate. The schema-focused suite has 14 tests and the full gate passes 177 tests.

**Required tests:**

- Minimal v1 legacy parse and migration diagnostic.
- V1 rejection of v2-only weak fields.
- V2 weak validation preservation during successful migration.
- V2 migration with no hints and actionable failures.
- V2 migration with sufficient hints.
- Minimal v3 round trip.
- Multi-component dependency round trip.
- Git source request plus exact locked commit.
- Maven sources artifact.
- Semantic scopes and loader-managed/plain artifact treatment.
- Default and explicit data-run policies.
- Future schema rejection.
- Path portability using `$sfm-cache`.

**Completion criteria:** All schema tests pass under `cargo test` and the fixtures are readable enough to act as documentation.

## Phase 3: Introduce the dependency domain model and read-only CLI

### [x] 3.1 Build a normalized dependency inventory

**Completion notes:** Completed 2026-07-11. Added a strict schema-v3 `DependencyInventory` loaded through an exact branch query. Inventory entries come only from declared logical dependencies; the separate 106-entry artifact inventory remains provenance for direct, transitive, and toolchain artifacts and does not masquerade as optional mods. Stable v3 IDs are authoritative. Minecraft and Forge are reserved platform entries, CC:Tweaked appears once, and AE2/Mekanism retain explicit main/API components. Portable paths resolve through an injected `CacheHome` object. Production resolves that object once at `DependencyArgs::invoke`; inventory methods and unit tests never resolve the global cache or environment themselves. Tests inject an isolated cache and prove missing, stale-hash, and acquired byte states.

**Work:**

- Project `minecraft` and `loader` as reserved dependencies.
- Group repeated legacy configuration rows for the same artifact into one component with semantic scopes.
- Support explicit grouping of distinct artifacts into one logical dependency.
- Distinguish direct declared dependencies from transitive artifacts and toolchain artifacts.
- Define stable ID derivation and collision handling.

**Completion criteria:**

- CC:Tweaked appears once with semantic compile, runtime, and game-test scopes.
- Minecraft and the loader appear as required platform dependencies.
- Toolchain libraries do not masquerade as optional mods.
- Mekanism and AE2 can expose multiple components under one ID.

### [x] 3.2 Implement `dependency list`

**Completion notes:** Completed 2026-07-11. Added deterministic plain-text `dependency list --branch 1.19.2` output with lockfile path, ID, kind, role, resolved version/file ID, per-component semantic scopes, hash-validated binary status, and source status. Rows sort platform Minecraft/loader first, then mods/libraries/tools by stable ID. The real output contains one CC:Tweaked row with version `1.101.3` and compile/runtime/game-test scopes; AE2 and Mekanism summarize named API/main components. Output uses no terminal-only layout state and is suitable for logs.

**Command:**

```pwsh
sfm-propagate-changes.exe dependency list --branch 1.19.2
```

**Required output:**

- ID
- kind
- role
- resolved version
- semantic scopes or a compact summary
- binary acquisition status
- source acquisition status

**Completion criteria:**

- `--branch` is required and resolves exactly one worktree.
- Output is deterministic and useful in non-interactive logs.
- Required platform dependencies and integration mods are visually distinguishable.

### [x] 3.3 Implement `dependency show`

**Completion notes:** Completed 2026-07-11. Added `dependency show <id> --branch <selector>` with maintained metadata, acquisition request, semantic scopes, artifact treatment, data-run policy, resolved coordinate/version, repository ID/URL, artifact URL/hash, portable cache path, hash-validated local binary status, and source-provider declarations/checks/materialized paths. Missing, stale, partial, and acquired states are explicit and read-only. Paths are prefixed with their status; transformed JARs are explicitly reported as not tracked by schema v3 rather than fabricated. Real CC:Tweaked output reports the acquired SquidDev JAR and no declared source strategy; tests cover missing paths and AE2's plain API versus loader-managed main component.

**Command:**

```pwsh
sfm-propagate-changes.exe dependency show cc-tweaked --branch 1.19.2
```

**Required output:**

- Maintained declaration fields.
- Resolved coordinates and versions.
- Repository and URL.
- Artifact hash.
- Binary JAR cache path.
- Deobfuscated/transformed JAR path when applicable.
- Available source strategies.
- Sources JAR path, extracted tree path, Git cache path, and decompiled tree path when materialized.
- Missing/stale/acquired status without acquiring anything.

**Completion criteria:** All printed paths exist when marked acquired, and missing paths are clearly labeled rather than printed as if valid.

### [x] 3.4 Centralize exact branch resolution for dependency commands

**Completion notes:** Completed 2026-07-11. Dependency list/show share `dependency_context::load_inventory`, reuse required `BranchSelector`, and call the existing exact-one-worktree resolver. Extracted the cardinality check to `require_single_worktree_target(query, targets)` so tests inject target slices without Git discovery. Parser tests cover omitted/explicit branch; pure tests cover zero/one/multiple targets; existing query tests cover invalid syntax. Real invocations verified exact `1.19.2`, zero-match (`No worktrees match`), multi-match (lists matches and requests one explicit branch), and invalid `AND` selectors. The full gate passes 183 tests.

**Work:**

- Reuse the existing `BranchSelector` syntax.
- Require the flag instead of deriving the current Git branch.
- Require exactly one target for dependency list/show/mutation/source commands.
- Emit a clear error when a selector matches zero or multiple worktrees.

**Completion criteria:** Parser and invocation tests cover omitted branch, invalid branch, zero matches, multiple matches, and one exact match.

## Phase 4: Add dependency mutation commands

### [x] 4.1 Redefine `dependency add` as declaration creation

**Completion notes:** Completed 2026-07-11. Added `dependency add <id> --branch <branch> --maven <exact-coordinate> --scope <scope>... [--repository <id>] [--artifact-treatment <treatment>] [--display-name <name>] [--project-url <url>] [--notes <text>]`. The command resolves exactly one worktree, rejects malformed/dynamic Maven versions and duplicate dependency/coordinate identities, validates an explicit repository ID or deterministically probes configured repositories, downloads the artifact, writes it under the injected `$sfm-cache/minecraft-toolchain/maven/...` root, computes its BLAKE3 hash, and creates one logical mod dependency with a `main` component plus owned artifact evidence. Mod treatment defaults to `loader-managed-mod`; data-run policy defaults to `exclude`; semantic scopes derive artifact purposes. Canonical schema v3 is concurrency-checked and atomically replaced. Source providers may be configured later without weakening the strict v3 declaration. Parser tests cover the documented CC:Tweaked syntax. An isolated-cache mutation fixture removes CC:Tweaked, adds it through the domain operation using controlled fetched bytes, and verifies the declaration, component, repository, artifact owner/URL/hash/path, and cached bytes without editing Gradle. `check-all.ps1` passes all policy, format, strict Clippy, build, and 189 test checks.

**Maven example:**

```pwsh
sfm-propagate-changes.exe dependency add cc-tweaked --branch 1.19.2 `
  --maven org.squiddev:cc-tweaked-1.19.2:1.101.3 `
  --scope compile `
  --scope runtime `
  --scope gametest-compile `
  --scope gametest-runtime `
  --artifact-treatment loader-managed-mod
```

**Work:**

- Add a logical dependency with a default `main` component.
- Require exact coordinates unless a deliberately supported dynamic version is supplied and locked.
- Validate repositories before mutation.
- Resolve, hash, and atomically update schema v3.
- Accept semantic scopes and let Rust/Gradle adapters choose concrete configurations.
- Default mod components to `loader-managed-mod`; require plain libraries/APIs to declare `plain` when the default would be wrong.
- Permit source metadata to be supplied atomically or configured afterward.

**Completion criteria:** Adding CC:Tweaked to a fixture produces the expected declaration, artifact lock, and repository relationship without editing Gradle.

### [x] 4.2 Add component and removal operations

**Completion notes:** Completed 2026-07-11. Added `dependency component add <dependency> <component> --branch <branch> --maven <exact-coordinate> --scope <scope>...` with the same optional repository and artifact-treatment controls as dependency creation. It reuses the Phase 4.1 Maven resolver, injected cache, hash/path derivation, artifact ownership, canonical serialization, and atomic writer. Added `dependency remove <dependency[/component]> --branch <branch>`. Whole required platform dependencies are protected; removing the last component requires whole-dependency addressing; unknown and malformed targets fail before writing. Removal deletes only artifacts made unreferenced by the mutation, retains artifacts referenced by remaining components or decompile source providers, and clears a removed owner when a shared artifact survives. Fixture tests remove AE2's API while preserving its main component/artifact and add Mekanism's plain API component while preserving main. Parser tests cover both nested component addition and component removal. The full quality gate passes 194 tests.

**Work:**

- Add `dependency component add` for API/runtime variants.
- Add `dependency remove <dependency[/component]>`.
- Refuse to remove required platform dependencies through the generic mod command.
- Detect references from source locks, artifact locks, and policies before removal.
- Remove generated artifacts only when no remaining component references them.

**Completion criteria:** Fixtures cover adding/removing an AE2 API component and a Mekanism API component without damaging their main components.

### [x] 4.3 Rename the current artifact-byte acceptance behavior

**Completion notes:** Completed 2026-07-11. Replaced the v2-oriented meaning of `dependency add` with `dependency artifact accept <dependency[/component]> --branch <branch>`. Acceptance now targets schema v3 logical IDs, hashes bytes from the component's locked portable cache path, updates the referenced artifact plus every component-derived expected hash, preserves optional weak Forge/NeoForge metadata validation, canonicalizes v3 JSON, and uses the shared concurrency-checked atomic lockfile writer. `DependencyArgs` resolves `CacheHome` once at the command boundary and passes it through artifact/list/show operations; unit tests inject a temporary `CacheHome` and prove acceptance reads controlled bytes only from that cache. Removed `dependency_add_cli.rs`, `dependency_add_command.rs`, `dependency_add_options.rs`, and the old v2 engine entry point. Parser coverage proves the new route parses and the old `dependency add <coordinate>` route is rejected. `cargo test --lib` passes 186 tests and strict all-feature Clippy passes.

**Implementation:** `src/cli/dependency/dependency_artifact_accept_cli.rs`

**Target command:**

```pwsh
sfm-propagate-changes.exe dependency artifact accept <dependency[/component]> --branch 1.19.2
```

**Work:**

- Move current hash-drift acceptance and `--weak-mod-metadata` behavior under `dependency artifact accept`.
- Target logical IDs/components while permitting an exact coordinate for diagnostics if useful.
- Remove the old meaning of `dependency add` in the same cutover.

**Completion criteria:** Existing acceptance tests pass through the new command and help text cannot confuse declaration creation with hash acceptance.

### [x] 4.4 Implement explicit dependency refresh

**Completion notes:** Completed 2026-07-11. Added `dependency refresh [dependency[/component]] --branch <branch>`. Omitting the target deterministically selects all refreshable remote artifacts; dependency and component targets scope the operation. Selected Maven, CurseForge/CurseMaven, and HTTP artifacts are reacquired only by this explicit command from their locked URLs, written beneath the injected cache, rehashed with BLAKE3, and propagated to every component derived check that references the artifact. Duplicate shared artifact references are fetched once. Weak acceptance is cleared after exact bytes are refreshed. Toolchain-generated/source-build-only selections with no refreshable remote artifacts fail explicitly rather than being silently skipped for targeted refresh. Current mutation commands require exact versions, so no dynamic Maven version is accepted or advanced implicitly; future deliberate dynamic-version support must resolve only through this refresh boundary. The command uses canonical, concurrency-checked atomic v3 writing. List/show remain read-only and future source acquire/search retain the no-lockfile-mutation requirement. Parser tests cover all and targeted syntax; an isolated-cache fixture proves targeted CC:Tweaked refresh updates only its artifact/checks while preserving unrelated hashes. The full quality gate passes 197 tests.

**Command:**

```pwsh
sfm-propagate-changes.exe dependency refresh --branch 1.19.2
```

**Work:**

- Resolve dynamic versions and generated artifact/source metadata only during explicit refresh.
- Support refreshing one dependency and all dependencies.
- Never advance a Git branch/tag implicitly during source search or source acquire.
- Reuse this operation from build planning where an explicit `--refresh` remains appropriate.

**Completion criteria:** Normal build, show, acquire, and search operations do not alter the lockfile.

## Phase 5: Cut the Rust planner over to schema v3 declarations

### [x] 5.1 Make the Rust build plan consume v3 dependency intent

**Completion notes:** Completed 2026-07-12. Build planning, compile, runtime, test, game-test, code generation, and packaging selection now project schema v3 declarations into the existing resolver. Repositories come from v3. Added a read-only v3-to-legacy resolver view so locked coordinates, hashes, cache paths, weak checks, and runtime POM closure remain available while resolver internals are incrementally typed. The legacy writer detects strict v3 and cannot overwrite declaration-owned state. Added canonical v3 declarations for ANTLR, JavaParser, JUnit Jupiter, and grouped JMH components by attaching their existing locked artifact evidence. On 2026-07-13, restricted the Forge `antlr` adapter projection to `org.antlr:antlr4`; the Vineflower standalone decompiler remains a source-provider tool despite its semantic `codegen` scope. This restored successful `dependency source acquire minecraft --provider platform-pipeline --branch 1.19.2` without placing Vineflower on the ANTLR tool classpath. `cargo run -- jar plan --branch 1.19.2` and all Phase 5 behavior commands succeed without lockfile mutation.

**Affected paths:**

- `src/jar_build/engine_plan.rs`
- `src/jar_build/engine_model.rs`
- `src/jar_build/resolve.rs`
- `src/jar_build/engine_sources.rs`
- `src/jar_build/engine_run.rs`

**Work:**

- Build compile, runtime, test, game-test, and packaging classpaths from v3 declarations.
- Use resolved coordinates during non-refresh operations.
- Preserve Maven runtime dependency closure handling.
- Keep loader-specific transformation in the loader adapter/toolchain layer.

**Completion criteria:** Rust compile and run plans no longer need `gradle/dependencies/<version>/dependencies.gradle` as an input.

### [x] 5.2 Implement loader-neutral artifact treatment

**Completion notes:** Completed 2026-07-12. Replaced the planner's `fg_deobf` boolean with schema v3 `plain`/`loader-managed-mod` treatment and propagate it into resolved dependency plans. Early Forge and transitional NeoForge-on-ForgeGradle take the mapping/remap path only for loader-managed mods; plain APIs use direct compile inputs and are never remapped. NeoGradle uses its existing normal copied-dependency path rather than Forge remapping. Tests cover all loader-toolchain classifications, CC:Tweaked as loader-managed, AE2 API as plain, and the Forge transform predicate.

**Work:**

- Store `loader-managed-mod` or `plain`, not whether a particular Gradle wrapper function is called.
- Treat `loader-managed-mod` as "declare this through the selected loader's normal mod dependency path." Map that through the existing loader toolchain kind.
- Treat `plain` as "never pass this artifact through a loader mod-remapping wrapper."
- Cover ForgeGradle, transitional NeoForge-on-ForgeGradle, and NeoGradle/NeoForge behavior.
- Keep ordinary libraries and already development-mapped API artifacts plain.
- Add a third treatment only after a concrete dependency proves these two cannot express the required behavior.

**Completion criteria:** Early Forge loader-managed mod dependencies receive the same transformed classpath behavior as `fg.deobf`, while later NeoGradle dependencies use their loader's normal declaration and plain dependencies are never remapped.

### [x] 5.3 Make mod exclusion the default data-run policy

**Completion notes:** Completed 2026-07-12. V3 data-run policy survives projection, deduplication, resolution, and run selection. Data runs admit only compile/runtime/bundle configurations explicitly marked `include`; ordinary client/server/test/game-test runs retain their semantic scope behavior. Mods default to `exclude` in mutation commands, while platform/build/test library declarations can opt in. The old Mouse Tweaks special case is no longer visible to Rust planning. Focused tests cover exclude/include behavior, and the local data workflow passes with all integration mods excluded by policy.

**Work:**

- Model run inclusion explicitly, with mod components excluded from `data` by default.
- Preserve required platform/toolchain inputs and bundled `jarJar` libraries.
- Remove the special Mouse Tweaks condition once both Rust and Gradle consume the generic policy.
- Add an explicit per-component opt-in for the rare dependency needed during data generation.

**Completion criteria:**

- The existing data-run configuration test remains true in v3 terms.
- `run data --branch 1.19.2` does not load CC:Tweaked, Mekanism, Mouse Tweaks, or other integration mods by default.
- Client, server, tests, and game tests still receive their intended dependencies.

### [x] 5.4 Remove the line-oriented Gradle dependency parser

**Completion notes:** Completed 2026-07-12. Removed `parse_dependency_script`, dependency-configuration recognition, quote extraction, Gradle property interpolation, and their parser-specific tests. Removed all Rust jar-planner/run/source references to `dependencies.gradle`; graph/state inputs now name `sfm-toolchain.lock.json`. `rg` finds no remaining parser or versioned dependency-script reference under `src/jar_build`.

**Current entry point:** `parse_dependency_script` in `src/jar_build/engine_plan.rs`.

**Work:**

- Remove production planning calls to `parse_dependency_script`.
- Delete the parser after lock migration no longer depends on it, or isolate it in a one-time migration command.
- Remove fragile handling that cannot represent arbitrary Groovy conditions.

**Completion criteria:** Changing a versioned `dependencies.gradle` file cannot change a Rust build plan after the cutover.

### [x] 5.5 Validate Rust build and run parity on 1.19.2

**Completion notes:** Completed 2026-07-12 from the local Cargo source tree. `cargo run -- run compile --branch 1.19.2`, `cargo run -- run test --branch 1.19.2`, `cargo run -- run data --branch 1.19.2`, and `cargo run -- run game-test-server --branch 1.19.2 --filter computer_craft_dependency_smoke` all pass against schema v3. Compile and the game-test smoke were rerun after the final plain-vs-loader-managed filter. The complete Rust quality gate passes 198 tests, formatting, strict Clippy, build, and direct-dependency policy.

**Validation:**

```pwsh
cargo run -- run compile --branch 1.19.2
cargo run -- run test --branch 1.19.2
cargo run -- run data --branch 1.19.2
cargo run -- run game-test-server --branch 1.19.2 --filter computer_craft_dependency_smoke
```

**Completion criteria:** All commands pass using schema v3 without parsing Gradle dependency declarations.

## Phase 6: Make Gradle a schema v3 compatibility consumer

### [~] 6.1 Inventory and test the Gradle dependency dialects

**Completion notes:** The consumer now distinguishes ForgeGradle from NeoGradle by the applied `net.neoforged.gradle.userdev` plugin rather than by Minecraft version. The `1.19.2` ForgeGradle path is exercised by real Gradle builds and the projection verification task. A read-only worktree inventory on 2026-07-13 classifies every supported branch: ForgeGradle through `1.20.1`, then NeoGradle from `1.20.2` onward. Every non-primary branch still has a schema-v2 lockfile, so propagation and representative dialect execution remain Phase 12.1/12.5 gates rather than safe actions on the current dirty worktree.

**Current dialect matrix:**

| Branches | Dialect | Loader declaration | Mod treatment | Status |
| --- | --- | --- | --- | --- |
| 1.19.2, 1.19.4, 1.20, 1.20.1 | ForgeGradle | `minecraft` | `fg.deobf` for `loader-managed-mod` only | 1.19.2 tested; later branches await v3 propagation |
| 1.20.2, 1.20.3, 1.20.4, 1.21.0, 1.21.1, 26.1.2 | NeoGradle userdev | `implementation` | Plain Gradle notation | Adapter implemented; branch execution awaits v3 propagation |

**Known samples:**

- 1.19.2 ForgeGradle uses the `minecraft` configuration and `fg.deobf(...)`.
- 1.20.2, 1.20.3, 1.20.4, 1.21.0, 1.21.1, and 26.1.2 use NeoForge/NeoGradle-style plain `implementation` for loader and mod coordinates.
- The plugin scripts, not the Minecraft-version label, determine the dialect; the inventory was recorded explicitly to prevent `fg.deobf` assumptions across the 1.20.1/1.20.2 boundary.

**Work:**

- Inventory each versioned dependency file.
- Map syntax to the existing `LoaderToolchainKind` or a new `GradleDependencyDialect`.
- Add fixtures/tests for each distinct dialect, not necessarily every Minecraft version.

**Completion criteria:** Every supported branch maps to a tested dialect and no adapter assumes `fg.deobf` exists universally.

### [x] 6.2 Add a Groovy lockfile consumer

**Completion notes:** Added `platform/minecraft/gradle/dependencies-from-lock.gradle`. It requires schema v3, reads exact `derived_checks.resolved_coordinate` values, maps semantic component scopes, uses remote coordinates, and records an immutable `sfmProjectedDependencies` projection for verification. `repositories.gradle` now reads the same lockfile and adds only repository IDs referenced by Maven or CurseForge acquisitions, including the CurseMaven content restriction.

**Implemented path:** `platform/minecraft/gradle/dependencies-from-lock.gradle`

**Work:**

- Parse `sfm-toolchain.lock.json` with `groovy.json.JsonSlurper`.
- Read maintained dependency/component declarations and exact values from `derived_checks`.
- Build a shrinkwrapped Gradle projection containing only repositories referenced by active Gradle dependency components.
- Exclude Maven repositories used only by the Rust/Minecraft toolchain.
- Map semantic scopes through the selected Gradle dialect.
- Use remote coordinates and Gradle's own cache, not Rust `$sfm-cache` filesystem paths.

**Completion criteria:** Gradle obtains all active dependency and repository declarations from schema v3.

### [~] 6.3 Implement version/loader-specific Gradle adapters

**Completion notes:** Implemented two small plugin-selected paths in `dependencies-from-lock.gradle`: ForgeGradle uses `minecraft` for the loader and applies `fg.deobf` only to `loader-managed-mod` components; NeoGradle userdev uses `implementation` and never references `fg.deobf`. Shared scope mapping covers compile/runtime, test, game-test, annotation processors, ANTLR code generation, and `jarJar`. Data projection drops excluded mods from runtime/game-test configurations while retaining compile scope as `compileOnly`, which lets SFM's compatibility sources compile without loading those mods during data generation. The remaining completion gate is executing this adapter on each distinct branch dialect in Phase 12.

**Required adapter responsibilities:**

- Loader dependency declaration.
- Minecraft/loader configuration name.
- Mod deobfuscation syntax or absence thereof.
- Semantic compile/runtime/game-test scope mapping.
- API classifier handling.
- Data-run inclusion policy.

**Completion criteria:** The lockfile contains semantic intent only, and all raw Gradle syntax is isolated in small adapter functions/scripts.

### [x] 6.4 Remove handwritten active dependency declarations

**Completion notes:** `gradle/versioned-dependencies.gradle` now applies only `gradle/dependencies-from-lock.gradle`. The historical files under `gradle/dependencies/*/dependencies.gradle` remain as inactive migration references, but they are no longer an active declaration source. The old Mouse Tweaks-only data condition is therefore inactive; schema v3 component policies control the projection.

**Affected paths:** `platform/minecraft/gradle/dependencies/*/dependencies.gradle`

**Work:**

- Replace active dependency lists with the common lockfile consumer plus minimal dialect selection if needed.
- Migrate useful comments into structured project URLs/notes where they remain valuable.
- Remove the Mouse Tweaks conditional in favor of the default data-run policy.
- Avoid leaving a second active declaration source that can drift from the lockfile.

**Completion criteria:** Adding a dependency through the Rust CLI is sufficient for both Rust and Gradle toolchains.

### [x] 6.5 Evaluate Gradle/IDE source attachment

**Completion notes:** Intentionally deferred IDE source attachment. Gradle can perform ordinary Maven classifier lookup from the projected remote coordinates, but `gradle/idea-excludes.gradle` deliberately sets `downloadSources = false` because ForgeGradle source lookups repeatedly contact remote repositories during IDEA sync. Managed Maven and Git source trees remain a CLI concern and are not exposed as fake Gradle artifacts or coupled to `$sfm-cache` paths. Phase 7/9 source acquisition and search are not blocked by this decision.

**Work:**

- Test standard Maven source attachment for locked `sources` classifiers.
- Revisit `gradle/idea-excludes.gradle` and current `downloadSources` settings.
- Do not expose managed Git snapshots as fake Maven artifacts merely for IDE attachment.
- Keep CLI source search independent of IDE support.

**Completion criteria:** Maven source attachment either works and is enabled intentionally, or is documented as deferred without blocking source management.

### [x] 6.6 Verify Gradle compatibility against Rust resolution

**Completion notes:** Both consumers now read direct coordinates and repository IDs from the same canonical schema v3 lockfile. On `1.19.2`, `compileJava`, `test`, and `verifySfmDependencyProjection` pass; the normal projection contains 59 configuration/component entries. `verifySfmDependencyProjection -PsfmDependencyProjectionMode=data` passes with 22 entries and proves excluded mods do not enter runtime or game-test configurations. A real `runData` compiled and all data providers completed with only Minecraft, Forge, and SFM discovered; Gradle then reported a single-use daemon disappearance during shutdown, so the deterministic projection task is the non-flaky data-policy gate. AE2's API classifier is loader-managed because it shares the mapped main module, while Mekanism's independent API component remains plain. The Rust quality gate passes all 198 tests and `dependency migrate --branch 1.19.2 --check` confirms the lockfile is canonical.

**Completion criteria:**

- Gradle and Rust resolve the same direct dependency versions and repositories.
- Representative compile/runtime/game-test classpaths contain the same intended mod components.
- Gradle data runs follow the same default mod exclusion policy.

## Phase 7: Build the source-provider and cache architecture

Keep the loader-authored Minecraft source pipeline and standalone mod decompilation as separate policies. The former must execute the exact locked MCP/NeoForm tool recipe; the latter may use a newer independently locked decompiler selected for source-search quality.

### [x] 7.1 Define source provider contracts

**Completion notes:** Added `source_provider::SourceProviderView`, which binds a schema `SourceProviderV3` declaration to an explicitly injected `DependencyInventory`. The view is the runtime contract for provider ID, typed kind, declaration-order priority, materialization status, tree path, and searchable roots. `DependencyInventory::source_status` and `dependency show` now consume this contract rather than maintaining independent status matches. Provider precedence is stable declaration order, starting at priority zero. A temporary-cache test proves Git provider status and roots use the injected cache rather than global resolution.

**Providers:**

- Minecraft transformed source pipeline.
- Loader source pipeline, potentially sharing the platform bundle.
- Maven sources JAR.
- Exact Git revision.
- Decompilation fallback.

**Required operations:**

- Describe availability without network access.
- Enumerate a stable provider ID, provider kind, priority, lock status, and materialization status.
- Acquire into the managed cache.
- Validate cached materialization.
- Return one or more searchable roots.
- Explain provenance and precedence.
- Match typed selectors such as `any`, `maven-sources`, `git`, and `decompile`.

**Completion criteria:** `dependency show` and source commands use the provider contract rather than source-kind conditionals scattered through CLI code.

### [x] 7.2 Establish a portable source cache layout

**Completion notes:** Added the public `source_cache::SourceCacheLayout` path builder. All paths are rooted at portable `$sfm-cache/sources`; human-readable keys are normalized ASCII prefixes plus a 16-character BLAKE3-derived suffix, preventing URLs, revisions, and fingerprints from introducing path components. Maven trees are keyed by coordinate and source payload hash, Git uses one bare-repository path per remote plus revision-specific trees, and decompile/platform trees are keyed by immutable input hashes or fingerprints. Tests prove stability, revision sharing, content addressing, portability, and rejection-by-construction of path traversal in arbitrary fingerprints.

**Proposed layout:**

```text
$sfm-cache/sources/
  platform/<minecraft-version>/<pipeline-fingerprint>/
  maven/<coordinate>/<source-hash>/
  git/repositories/<remote-key>.git/
  git/trees/<remote-key>/<commit>/
  decompiled/<binary-hash>/<decompiler-fingerprint>/
```

**Work:**

- Keep portable paths in the lockfile.
- Key extracted trees by immutable input content.
- Store state/fingerprint files for derived outputs.
- Reuse the existing artifact path locking and cancellation infrastructure.

**Completion criteria:** Cache paths are deterministic across machines and contain no `G:\Programming\Repos` references.

### [x] 7.3 Harden archive extraction for untrusted mod sources

**Completion notes:** Added `source_archive::extract_zip_atomically` as the managed-source extractor. It streams from a file-backed `ZipArchive`, extracts into a temporary sibling tree, and publishes only after every entry succeeds. It rejects empty, absolute, drive-prefixed, backslash, `.`/`..`, link, and special-file entries; enforces entry-count, per-entry-size, and total-expanded-size limits; and verifies copied lengths. Security tests cover traversal, absolute, drive, and backslash entries and prove a failed archive neither escapes the cache root nor replaces an existing tree. The older Minecraft source-output extractor remains isolated because loader-authored pipeline compatibility is a separate policy.

**Current helper:** `src/jar_build/source_output_filetree.rs`

**Work:**

- Reject absolute paths, drive prefixes, `..`, and entries escaping the destination.
- Define handling for symlinks and non-regular entries.
- Extract through a temporary directory and atomically publish completed trees.
- Avoid reading an entire arbitrarily large archive into memory when streaming is practical.
- Preserve deterministic file timestamps/permissions only where needed for searching.

**Completion criteria:** Tests include malicious ZIP traversal entries and prove no output can escape the cache root.

### [x] 7.4 Implement Maven source-payload acquisition

**Completion notes:** Core resolution and acquisition are implemented. `source_maven` derives `sources` for an unclassified exact coordinate and `<classifier>-sources` for a classified coordinate, always as a JAR; `dependency source configure --maven-coordinate` permits an exact override. Configuration uses the component's locked repository, the shared injectable HTTP fetcher, BLAKE3 hashing, `SourceCacheLayout`, an artifact lock, atomic payload writing, and the hardened extractor. Declared roots must be normalized relative paths that exist in the extracted payload. Locked acquisition validates URL/hash/root evidence, is idempotent, and never mutates the lockfile or build artifact inventory. CC:Tweaked's published source JAR is now locked and acquired at `org.squiddev:cc-tweaked-1.19.2:1.101.3:sources`, hash `blake3:a72c68f5a37bf67fa8965fc8a1bbf33416223684`, with root `dan200/computercraft`. A second real acquisition preserved lockfile SHA-256 `8D1734049F1E7907DE29A537FF6C388D02E464E303C942D90D97309310FF001E`. The final no-fetch search CLI found `IPeripheralProvider` in the managed extracted tree on 2026-07-12.

**Work:**

- Derive a `sources` classifier candidate from an exact Maven component.
- Reuse low-level HTTP, repository selection, content hashing, cache locking, and provenance helpers without adding the source archive to the build artifact inventory.
- Store source URL, repository, source-cache path, and content hash under the source provider's `derived_checks`.
- Keep Maven source payloads under `$sfm-cache/sources/maven`, separate from build dependency JARs.
- Ensure source archives can never enter compile/runtime/game-test classpaths.
- Treat missing sources classifiers as an expected absence, not a broken binary dependency.
- Permit an explicit alternate source coordinate for unusual publications.

**Completion criteria:** CC:Tweaked's Maven sources JAR can be acquired, validated, extracted, shown, and searched without Git, and artifact/classpath reports do not count it as a build dependency.

### [x] 7.5 Move Minecraft/loader source output behind dependency source providers

**Completion notes:** Completed on 2026-07-12. The existing Forge/NeoForge source pipeline remains internally behind `SourceOutputCommand`, and `dependency source acquire minecraft --provider platform-pipeline --branch 1.19.2` and the corresponding `forge` command materialize its `filetree` output. The v3 lockfile exposes `minecraft-pipeline` and `loader-pipeline` views over the shared Forge combined-source tree at `build/sfm-toolchain/forge/1.19.2/sources/combined-deobfuscated.filetree`. Both provider-list and acquire commands were exercised live; no-fetch search rendered `forge/userdev/loader-pipeline/...` provenance for `ServerLifecycleHooks`. `check-all.ps1` passed with 214 tests.

**Current implementation:**

- `src/cli/jar/jar_sources_cli.rs`
- `src/jar_build/engine_sources.rs`
- `src/jar_build/source_output_*`

**Work:**

- Preserve the existing Forge/NeoForge source-generation pipeline.
- Expose its JAR and filetree through `minecraft` and `loader` dependency source views.
- Model shared platform source bundles when Minecraft and loader sources are physically combined.
- Keep expensive build/materialization behavior in `acquire`, never in `search`.

**Completion criteria:** `dependency source acquire minecraft --branch 1.19.2` replaces the useful behavior of `jar sources`.

### [x] 7.6 Select and lock the standalone mod decompiler

**Completion notes:** Completed on 2026-07-12. The standalone default is Vineflower `org.vineflower:vineflower:1.12.0`, locked as the `vineflower` build tool with artifact ID `org-vineflower-vineflower-1-12-0-865bc756` and BLAKE3 `865bc756e48b1c6bae2fa0adb4be10b7cd7c5a46`. It requires Java 17. The policy is the regular bundled-plugin JAR, default CLI options, and no external library inputs; Java runtime classes are included by Vineflower's default runtime scan. The Minecraft MCP pipeline remains pinned to ForgeFlower `1.5.605.9`.

**Comparison (Java 17, `-Xmx4G`, clean output directories, default options):**

| Candidate | CC:Tweaked repeated output | Mekanism repeated output | Notes |
| --- | --- | --- | --- |
| Vineflower 1.11.2 | identical normalized trees; 1,396 files / 676 Java | identical normalized trees; 9,075 files / 1,972 Java | 7.6-7.8s CC:Tweaked; 13.2-13.8s Mekanism |
| Vineflower 1.12.0 | identical normalized trees; 1,396 files / 676 Java | identical normalized trees; 9,075 files / 1,972 Java | 8.1-8.2s CC:Tweaked; 14.4-15.0s Mekanism; current upstream release |

Normalized SHA-256 tree fingerprints were `c255da0c1385c21acef85778ce12803f01923fc5973f29247f3e5a41d67d5275` (CC:Tweaked 1.12.0) and `83b845dbdeaf30e70e9328dd63f45f0703f076f697f66e14158d88e54076a7e6` (Mekanism 1.12.0). Both fixtures exited successfully with no observed errors. ForgeFlower and FernFlower remain controls rather than the standalone default; ForgeFlower stays exclusively in loader-authored Minecraft pipelines.

**Candidates and evidence:**

- The 1.19.2 Minecraft pipeline's locked ForgeFlower `1.5.605.9` as a compatibility baseline.
- Cached ForgeFlower `2.0.627.2` and `2.0.629.0` where their publication and runtime requirements can be reproduced.
- Cached Vineflower `1.9.3`, `1.10.1`, and `1.11.2`.
- The latest stable Vineflower release at implementation time; `1.12.0` was current when this plan was updated.
- Plain FernFlower only as a historical/control implementation, not the presumed default.
- NeoForge Vineflower plugins only when their Minecraft-specific transformations apply; do not add them automatically to arbitrary mod decompilation.

**Evaluation procedure:**

- Use exact downloaded coordinates and hashes, never an unversioned local build.
- Decompile representative locked mod binaries including CC:Tweaked and at least one larger mod such as Mekanism.
- Run each candidate twice from clean output directories and compare normalized tree hashes.
- Record success/failure, elapsed time, peak memory where practical, Java runtime requirement, warnings/crashes, searchable source coverage, line mapping, and obvious invalid Java output.
- Verify whether supplying dependency/library JARs improves type recovery without making acquisition recursive or nondeterministic.
- Prefer Vineflower for standalone fallback if it passes reproducibility and compatibility checks; retain a different choice only with the comparison recorded here.
- Do not change the decompiler selected by any existing MCP/NeoForm Minecraft pipeline as part of this decision.

**Completion criteria:** A comparison table identifies one exact default mod decompiler coordinate, artifact hash, Java requirement, arguments, library-input policy, and known fallback behavior. The selected tool reproduces byte-identical normalized output on repeated runs for the acceptance fixtures.

### [x] 7.7 Implement deterministic decompilation fallback

**Completion notes:** Completed on 2026-07-12. `dependency source configure <dependency/component> --decompile --branch <branch>` registers a `vineflower` provider using only the locked binary and the locked Vineflower `1.12.0` artifact. Acquisition validates or hash-downloads those exact artifacts, resolves the local Java runtime (17+), invokes Vineflower with `--folder`, stages the generated tree, records its content-addressed fingerprint, and atomically publishes the result under `$sfm-cache/sources/decompiled`. The BLAKE3 fingerprint includes binary hash, the explicit `no-external-mappings` policy, decompiler hash, exact Java version output, `--folder`, and the `no-external-libraries` policy. It uses no Minecraft pipeline code or loader-pipeline inputs. Re-running an identical acquisition validates the fingerprint marker and reuses the tree without fetching or re-decompiling. The locked CurseForge Mekanism `main` and `api` binaries now exercise this fallback; `dependency source search Mekanism --dependency mekanism --branch 1.19.2 --require-complete` returns results from both providers.

**Work:**

- Prefer the development-remapped binary where appropriate.
- Acquire and invoke the exact standalone mod decompiler selected in 7.6 independently from the loader's Minecraft source pipeline.
- Include binary hash, mappings, decompiler coordinate and artifact hash, Java runtime identity, options, and library-input hashes in the fingerprint.
- Pass explicitly locked dependency/library inputs only according to the policy selected in 7.6.
- Treat decompiled output as derived cache state rather than a manually maintained source lock.
- Clearly label decompiled results as lower-authority than published or Git sources.
- Keep decompiled trees local to the managed cache; never package or publish them as dependency source artifacts.

**Completion criteria:** A mod without Maven or Git sources can be searched reproducibly from a locked binary.

## Phase 8: Replace independent Git clones with managed gix repositories

### [x] 8.1 Enable and wrap the required gix features

**Completion notes:** Completed on 2026-07-12. The pinned `gix 0.84.0` dependency enables `blocking-http-transport-reqwest-rust-tls`, `index`, `sha1`, and `worktree-stream`. `source_git` is the sole Git-source wrapper boundary and `check-all.ps1` passed with 222 tests.

**Current dependency:** `gix 0.84.0` with `blocking-http-transport-reqwest-rust-tls`, `index`, `sha1`, and `worktree-stream`.

The local gitoxide checkout may be newer than the crate used by SFM. Before implementing against it, check out `gix-v0.84.0`, the tag corresponding to the selected `gix` crate version. Upgrade the crate intentionally if newer APIs are required; do not accidentally code against local `main` while retaining an older Cargo version.

**Expected additional capabilities:**

- Blocking HTTPS transport, preferably the reqwest/rustls feature matching the CLI's network stack.
- Bare clone/fetch.
- Revision peeling and commit validation.
- Worktree streaming or archive generation.

**Assistive APIs:**

- `gix::prepare_clone_bare`
- repository remote/fetch APIs
- `Repository::worktree_stream`

**Completion criteria:** Git source operations do not invoke the system `git` executable.

### [x] 8.2 Implement one managed bare repository per canonical remote

**Completion notes:** Completed on 2026-07-12. HTTPS remotes are canonicalized by lowercasing the host, eliding port 443, and treating a trailing `.git` as equivalent; credentials and non-HTTPS remotes are rejected. The canonical URL feeds `SourceCacheLayout::git`, producing one readable BLAKE3-suffixed bare-repository path. Clone/open/fetch mutations are protected by `ArtifactLock`, and gix validates the exact locked commit. A real Mekanism provider at `f33ff1f438caa55d58ef1f0a08091997353afcb8` clones and validates successfully. An ignored network acceptance test starts with an empty existing bare repository, fetches the commit, and verifies it is present without using the system Git executable.

**Work:**

- Canonicalize the remote sufficiently to avoid duplicate HTTPS spellings while preserving meaningful distinctions.
- Hash the canonical remote into a safe cache key and retain readable provenance metadata.
- Clone bare once and fetch additional refs/commits into the same repository.
- Lock clone/fetch mutations so parallel branches do not corrupt the object database.
- Verify the resolved object is the exact locked commit.

**Completion criteria:** Two Minecraft branches using different CC:Tweaked revisions share one Git object database and have independent searchable trees.

### [x] 8.3 Materialize immutable searchable Git trees

**Completion notes:** Completed on 2026-07-12. The gix implementation streams a validated commit through `Repository::worktree_stream` into a temporary sibling tree, rejects non-UTF-8 paths, links, and submodules, validates declared roots, writes a commit completion marker, and publishes atomically. It never registers a Git worktree or runs repository build logic/hooks. The real Mekanism provider materializes `src/main/java` and is searchable with `dependency source search --provider git`.

**Work:**

- Stream the exact commit tree into a content-addressed cache directory.
- Apply source-root filters only after commit validation.
- Avoid registered Git worktrees for read-only search materializations.
- Publish atomically and validate a completion marker/fingerprint.
- Keep submodules and Git LFS disabled by default unless explicitly modeled and locked later.

**Completion criteria:** Source search operates on plain files without an active Git worktree and without running repository build logic or hooks.

### [x] 8.4 Migrate existing source-build checkout behavior

**Completion notes:** Completed on 2026-07-12. Source-build fallback now materializes a disposable plain checkout from the managed gix bare repository before invoking the Gradle wrapper; it no longer runs system `git clone`, `fetch`, `config`, or `checkout`. New staging checkouts use `$sfm-cache/source-builds-gix/<remote-and-commit-hash>` while the legacy `$sfm-cache/source-builds` directory is preserved untouched. The focused source-build fallback test passes using a local Git fixture, proving the Gradle wrapper can build from the gix-materialized checkout and records the new portable provenance path.

**Current implementation:**

- `source_build_checkout_key(remote_url, commit)`
- `source_build_checkout_paths(...)`
- `prepare_source_build_checkout(...)`
- `$sfm-cache/source-builds/<url-and-commit-hash>` independent clones

**Work:**

- Reuse the new managed bare repository for source builds.
- Materialize a disposable or managed build checkout only when the build requires a real worktree.
- Keep build outputs and dirty state separate from immutable search trees.
- Preserve current source-build fallback behavior during migration.
- Do not delete old clones until the equivalent new materialization validates successfully.

**Completion criteria:** Different commits no longer require independent full clones, and source builds remain reproducible.

### [x] 8.5 Add Git cache audit and cleanup behavior

**Completion notes:** Completed on 2026-07-13. `dependency source cache audit --branch <branch>` reports the selected lockfile's Git providers while aggregating references from every discovered readable v3 lockfile. It names any legacy/unreadable lockfiles that prevent a safe global decision. `dependency source cache cleanup --branch <branch>` is report-only until `--confirm` is supplied, then removes only unreferenced direct non-symlink directories below `$sfm-cache/sources/git`; it refuses cleanup altogether while any discovered lockfile is unverified. Focused tests prove referenced repository/tree entries survive and stale entries are removed only after the safety check. The live 1.19.2 audit reports Mekanism as referenced and blocks cleanup until the remaining branches migrate to v3.

**Work:**

- Report repository remotes, locked commits, materialized trees, and stale/unreferenced entries.
- Add cleanup only under the verified source cache root.
- Never remove objects/trees referenced by any discovered SFM lockfile.
- Keep cleanup explicit rather than coupling it to search.

**Completion criteria:** A user can understand and safely reclaim source cache space without manual Git administration.

## Phase 9: Implement dependency source commands and full CLI cutover

### [x] 9.1 Implement `dependency source configure`

**Completion notes:** Completed on 2026-07-13. Maven configuration is implemented with explicit `--maven-sources` or `--maven-coordinate` and repeatable validated `--root` values. `--decompile [--decompiler <dependency/component>]` creates or replaces the stable `vineflower` provider from locked artifacts and the selected Java runtime fingerprint. `--git-url <https-url> --git-revision <exact-commit-or-refs/tags/...>` configures the canonical `git` provider, materializes its locked commit tree, and validates the configured roots before the v3 write. Explicit tags are fetched and resolved through gix during configuration; the declaration retains the requested tag while `derived_checks` holds its exact resolved commit. `--prefer` moves the configured provider to the front of the component's explicit cross-kind provider order. All implemented paths write canonical v3 atomically.

**Git example:**

```pwsh
sfm-propagate-changes.exe dependency source configure cc-tweaked --branch 1.19.2 `
  --git-url https://github.com/cc-tweaked/CC-Tweaked.git `
  --git-revision refs/tags/v1.19.2-1.101.3 `
  --root src/main/java `
  --root src/main/resources
```

**Work:**

- Store requested Git URL/ref/source roots in v3.
- Resolve and lock the exact commit only during explicit configuration/refresh.
- Support Maven source-coordinate overrides and provider preference.
- Validate roots against the acquired source before marking configuration complete.

**Completion criteria:** CC:Tweaked source intent and exact commit are represented in the lockfile without a separate file.

### [x] 9.2 Implement `dependency source provider list`

**Completion notes:** Completed on 2026-07-13. The read-only command tree covers Maven, Git, decompile, and platform-pipeline providers. Output includes dependency/component, stable ID, typed kind, declaration-order priority, whether `any` selects it or treats it as a fallback, locked status, local status, resolved searchable roots, and a provider-specific unavailable reason. The live Mekanism listing reports acquired `git` and `vineflower` providers, while CC:Tweaked reports both acquired Git and Maven-source providers. `dependency source configure --prefer` now moves a configured provider to the front of this explicit order.

**Commands:**

```pwsh
sfm-propagate-changes.exe dependency source provider list --branch 1.19.2
sfm-propagate-changes.exe dependency source provider list cc-tweaked --branch 1.19.2
```

**Required output:**

- Dependency/component ID.
- Stable provider ID.
- Provider kind.
- Configured preference/order.
- Whether sufficient lock data exists to acquire without refresh.
- Whether the provider is materialized and valid locally.
- Searchable roots when materialized.
- Reason the provider is unavailable when it cannot be used.

**Provider semantics:**

- `any` selects the first usable provider in the dependency's explicit provider order.
- `maven-sources`, `git`, `decompile`, `minecraft-pipeline`, and `loader-pipeline` are built-in provider kinds.
- A stable provider ID can select one provider when multiple providers have the same kind.
- CurseForge/CurseMaven binary origin does not automatically create a source provider. A real CurseForge source archive may be modeled as its own provider if one exists and is locked.

**Completion criteria:** A developer can inspect exactly what `--provider any` would select without causing network or cache writes.

### [x] 9.3 Implement `dependency source acquire`

**Completion notes:** Completed on 2026-07-13. Targeted Maven, Git, platform-pipeline, and Vineflower decompile acquisition are implemented and print validated searchable roots. Built-in selection uses the typed `DependencySourceProviderSelector` (`any`, `maven-sources`, `git`, `decompile`, or `platform-pipeline`); arbitrary stable IDs use the distinct `--provider-id` option, and combining both is rejected. Tests prove an already validated Maven cache performs no HTTP; decompile tests prove fingerprinted cache reuse with no fetch or rerun. The real CC:Tweaked acquisition, locked Mekanism Git tree, and Mekanism decompile fallback are idempotent. `--all` selects the first matching declared provider for every configured component, rejects target/`--all` ambiguity, skips components with no matching provider, and runs the shared platform pipeline once. `--parallel[=<workers>]` uses the shared bounded-parallelism model for independent Maven, Git, and decompile providers; it halts new work after a failure, preserves cancellation, and prints roots only after successful acquisition in declaration order. Focused tests prove the worker bound and failure stop behavior. A live `dependency source acquire --all --provider git --parallel 2 --branch 1.19.2` reused the CC:Tweaked and Mekanism managed Git trees.

**Commands:**

```pwsh
sfm-propagate-changes.exe dependency source acquire cc-tweaked --provider any --branch 1.19.2
sfm-propagate-changes.exe dependency source acquire cc-tweaked --provider git --branch 1.19.2
sfm-propagate-changes.exe dependency source acquire --all --provider any --branch 1.19.2
```

**Work:**

- Acquire one dependency/component or `--all` for the selected branch.
- Accept a typed provider kind, stable provider ID, or `any` selector.
- Default an omitted provider selector to `any` only if Figue help makes that behavior obvious.
- Follow locked provider metadata without refreshing revisions.
- Dispatch Minecraft, loader, Maven, Git, and decompile providers appropriately.
- Print the resulting searchable roots.
- Support cancellation and existing parallelism conventions.

**Completion criteria:** Acquisition is idempotent and a second run reuses validated cached sources without network or expensive regeneration.

### [x] 9.4 Implement no-fetch `dependency source search`

**Completion notes:** Completed on 2026-07-12. `dependency source search` performs a read-only preflight over `DependencyInventory` and `SourceProviderView`, then invokes ripgrep only for acquired validated roots. Repeatable `--dependency`, typed `--provider`, stable `--provider-id`, and `--require-complete` are supported; combining the provider selectors is rejected. Missing roots produce one complete warning before available roots are searched, while `--require-complete` bails before ripgrep. Tests cover parser behavior, missing roots, unknown dependency preflight, no cache mutation, warning composition, require-complete, match, and no-match behavior. The real CC:Tweaked search found `IPeripheralProvider`; `check-all.ps1` passed with 214 tests.

**Work:**

- Preflight selected dependency source roots without network access.
- Search every available root with ripgrep.
- Warn once with the complete list of missing dependencies/source roots.
- Clearly state that search results are incomplete when anything is missing.
- Continue searching available roots after the warning.
- Preserve normal search match/no-match status in warning mode.
- Add `--require-complete`; when selected, bail before searching if any selected source root is missing.
- Accept an optional provider selector to search only roots from matching acquired providers.
- Never invoke acquire, refresh, Maven HTTP, Git fetch, or decompilation.

**Completion criteria:** Tests prove search does not perform network/cache mutations, does not silently report a complete no-match result when sources are missing, and `--require-complete` fails with acquisition recommendations before invoking ripgrep.

### [x] 9.5 Render typed source-acquisition recommendations

**Completion notes:** Completed on 2026-07-12. Missing-root warnings now construct one typed `Cli`/`dependency source acquire` value containing the explicit branch, provider selector or provider ID, and target. Updated Figue rendering supports omitted positional targets and provider enums, so no command fragments are appended manually. When every source is missing from an unfiltered branch-wide search, warnings render one typed `dependency source acquire --all --provider any --branch 1.19.2` recommendation; dependency-filtered searches instead render the exact missing target so they never broaden acquisition scope. Targeted and `--all` recommendations round-trip through Figue.

**Work:**

- Construct typed `Cli`/dependency-source-acquire values for missing source targets, including the selected provider and explicit branch.
- Render recommendations with `figue::to_args_string_with_current_exe` or the `ToArgs` trait.
- Prefer one `--all` recommendation when every declared source is missing; otherwise render targeted commands.
- Keep recommendation rendering independent of execution.

**Completion criteria:** Copying a rendered recommendation invokes the intended typed acquire command with the same explicit branch.

### [x] 9.6 Make search output identify dependency provenance

**Completion notes:** Completed on 2026-07-12 with a ripgrep integration that searches each validated extracted root independently and prefixes every match as `dependency/component/provider/path:line:column`. A real `IPeripheralProvider` search produced CC:Tweaked `maven-sources` provenance paths, including `cc-tweaked/main/maven-sources/api/peripheral/IPeripheralProvider.java`.

**Work:**

- Label paths with stable dependency IDs such as `minecraft/...` and `cc-tweaked/...` instead of opaque cache hashes.
- Preserve file, line, column, context, and color behavior where practical.
- Support filtering by repeatable `--dependency`.
- Decide and test fixed-string versus regex defaults.
- Avoid searching JAR/ZIP bytes directly; use validated extracted trees.

**Completion criteria:** Searching `IPeripheralProvider` identifies CC:Tweaked source files and produces paths a developer can locate through `dependency show`.

### [x] 9.7 Remove `jar sources` and flatten `source audit`

**Completion notes:** Completed on 2026-07-13. Removed `JarCommand::Sources`, `JarSourcesArgs`, and `src/cli/jar/jar_sources_cli.rs`; platform source acquisition now constructs `SourceOutputOptions` internally behind `dependency source acquire`. Removed the top-level `source` command and its CLI module, moving source-size auditing to top-level `AuditArgs`/`audit`. Parser coverage rejects `jar sources` and `source audit` without aliases, while typed `audit` rendering round-trips through Figue. Live `dependency source acquire minecraft --provider platform-pipeline --branch 1.19.2` materialized the expected combined source filetree. Updated `docs/AGENTS.md` to point developers to that replacement command.

**Work:**

- Remove `JarCommand::Sources`, `JarSourcesArgs`, and associated parser tests.
- Move reusable source-generation internals behind dependency source providers.
- Remove the top-level `source` command after source search/acquire lives under `dependency source`.
- Move `SourceAuditArgs` to top-level `AuditArgs` and preserve explicit `--branch`.
- Remove stale imports, exports, command help, and documentation.

**Completion criteria:**

- `jar sources` and `source audit` are rejected as unknown commands.
- `dependency source acquire minecraft` and `audit` provide the intended replacements.
- No compatibility alias remains.

## Phase 10: Add CurseForge discovery and exact dependency acquisition

### [x] 10.1 Add CurseForge mod search API models and client operation

**Completion notes:** Completed on 2026-07-13. `curseforge mod search` uses the Core
`GET /v1/mods/search` endpoint with Minecraft game ID `432`, exact `gameVersion`,
`searchFilter`, `modLoaderType`, and deterministic `index`/`pageSize` pagination. The Facet
models retain project ID, slug, name, summary, download count, modified time, and popularity
rank; results are sorted by project ID before rendering. It resolves credentials through the
existing `CurseforgeApiSecret` / `CurseforgeHttpClient` boundary and mutates neither lockfile nor
cache. Help and rendering tests pass. A live read-only
`curseforge mod search Mekanism --minecraft 1.19.2 --loader forge` returned the stable Mekanism
project ID `268560` through a process-local Core API credential.

**Affected paths:**

- `src/curseforge`
- `src/cli/curseforge`

**Work:**

- Add Minecraft mod search with query, Minecraft version, and loader filtering.
- Reuse `CurseforgeApiSecret` and `CurseforgeHttpClient`.
- Handle pagination deterministically.
- Print project ID, slug, name, summary, and useful popularity/update metadata.

**Completion criteria:** Searching for ComputerCraft or Mekanism returns stable project IDs suitable for the next command without mutating an SFM lockfile.

### [x] 10.2 Add filtered CurseForge file listing

**Completion notes:** Completed on 2026-07-13. `curseforge mod files <project>` requests
`GET /v1/mods/{project}/files` with exact version and loader filters, drains paginated responses,
then locally retains only files whose `gameVersions` advertise the requested Minecraft version
and supported loader. It prefers the structured numeric loader when Core supplies it, falling
back to its advertised loader label when that field is absent. Output contains the exact file ID,
file and display names, release type, publication time, reported versions, and loaders; it
intentionally makes no recommendation. Unit tests prove the version/loader pair is required, and
command help passes. A credentialed read-only Mekanism listing returned all nine matching files,
including file `4644795`, as `forge` candidates.

**Work:**

- Build on existing `fetch_project_files` support.
- Filter by exact Minecraft version and loader.
- Print file ID, filename, display name, release type, publication date, and supported versions/loaders.
- Do not label one result as automatically recommended by dependency mutation code.

**Completion criteria:** A user can deliberately choose an exact file ID before invoking `dependency add`.

### [x] 10.3 Add exact CurseForge dependency declarations

**Completion notes:** Completed on 2026-07-13. `dependency add` now accepts exactly one source:
an exact `--maven` coordinate, or both `--curseforge-project` and `--curseforge-file`. The
CurseForge path resolves the authoritative project slug and exact file through the Core API,
checks response IDs, project ownership, the active branch's Minecraft version/loader pair, and
the portable slug. It derives only `curse.maven:<slug>-<project>:<file>`, resolves only the
configured `cursemaven` repository, hashes and caches the exact JAR, and writes the v3
`curse-forge` acquisition plus derived coordinate/hash/cache checks atomically. It rejects mixed
source flags, incomplete IDs, wrong repositories, and duplicate locked coordinates. An
isolated-cache fixture reproduces Mekanism project `268560`, file `4644795`, and asserts the
generated v3 declaration, CurseMaven URL, and cached bytes.

**Example:**

```pwsh
sfm-propagate-changes.exe dependency add mekanism --branch 1.19.2 `
  --curseforge-project 268560 `
  --curseforge-file 4644795 `
  --scope compile `
  --scope runtime `
  --artifact-treatment loader-managed-mod
```

**Work:**

- Require both project and file ID.
- Validate that the file belongs to the project.
- Resolve/validate the project slug used in the CurseMaven coordinate.
- Store project ID, file ID, slug, effective coordinate, and artifact hash in v3.
- Keep discovery/search out of this mutation command.

**Completion criteria:** The command cannot silently select a newer/different file and reproduces the current locked Mekanism artifact in a fixture.

### [x] 10.4 Test CurseForge failure and offline cases

**Completion notes:** Completed on 2026-07-13. Focused tests cover Core pagination shape
failures, exact source-flag pairing, cross-project file rejection, wrong Minecraft version, wrong
loader, and the isolated exact-add fixture. Loopback Core API fixtures return exact `404 Not
Found` responses for both project and file metadata endpoints, proving their error text reaches
the mutation boundary without a real API key. A read-only command with no configured credential
returns the focused missing-Core-key error before any request. Finally, a locked Mekanism
CurseMaven binary in the validated shared cache resolves successfully while its configured
repository is deliberately unreachable, proving offline cache reuse and no required fetch.

**Required tests:**

- Unknown project.
- Unknown file.
- File belongs to a different project.
- File does not support selected Minecraft version/loader.
- Missing API credentials.
- API pagination.
- Offline acquisition from an already validated cache.

## Phase 11: Use CC:Tweaked as the end-to-end acceptance fixture

### [x] 11.1 Migrate CC:Tweaked into the v3 logical dependency model

**Completion notes:** Completed on 2026-07-12. The canonical 1.19.2 v3 lockfile contains one `cc-tweaked` integration mod with a `main` Maven component at `org.squiddev:cc-tweaked-1.19.2:1.101.3`, semantic `compile`, `runtime`, `gametest-compile`, and `gametest-runtime` scopes, `loader-managed-mod` treatment, and default data-run exclusion. The live dependency list reports the expected single row with acquired binary and sources.

**Required result:**

- Logical ID `cc-tweaked`.
- Role `integration` and kind `mod`.
- Main Maven component at version `1.101.3`.
- Semantic `compile`, `runtime`, `gametest-compile`, and `gametest-runtime` scopes.
- `loader-managed-mod` artifact treatment, which the 1.19.2 Forge adapter renders using `fg.deobf(...)`.
- Excluded from data runs by default.

**Completion criteria:** `dependency list` shows one CC:Tweaked row and `dependency show` explains its semantic scopes and their active loader projection.

### [x] 11.2 Lock both Maven and Git source options

**Completion notes:** Completed on 2026-07-13. The canonical 1.19.2 lockfile retains the existing Git-first, Maven-second provider order for `cc-tweaked/main`. The Maven classifier declaration and source-payload hash remain locked (`blake3:a72c68f5a37bf67fa8965fc8a1bbf33416223684`). Explicit configuration fetched `refs/tags/v1.19.2-1.101.3` from `https://github.com/cc-tweaked/CC-Tweaked`, retained that requested tag in the Git declaration, and resolved its derived commit to `f9bb1b497964cccab6cde34e8948333210275f93`; its validated roots are `src/main/java` and `src/main/resources`. Provider listing, Git-only acquire, and complete Git-only `IPeripheralProvider` search all passed. The lockfile stores only portable managed-cache paths and contains no user checkout path.

**Required result:**

- Maven `sources` classifier locked when available.
- Git URL and requested tag stored.
- Exact Git commit `f9bb1b497964cccab6cde34e8948333210275f93` locked.
- Search roots include `src/main/java` and relevant resources.
- Provider order selected explicitly, with Maven sources and Git alternatives visible through `dependency source provider list` and `dependency show`.

**Completion criteria:** No local `G:\Programming\Repos\CC-Tweaked` path appears in the lockfile or required runtime configuration.

### [x] 11.3 Acquire and search CC:Tweaked sources through the CLI

**Completion notes:** Completed on 2026-07-12. The managed Maven source tree is acquired idempotently from the locked `sources` payload. `dependency source search IPeripheralProvider --dependency cc-tweaked --branch 1.19.2 --require-complete` returns provenance-prefixed matches including `cc-tweaked/main/maven-sources/api/peripheral/IPeripheralProvider.java`; `ComputerCraftAPI` search is supported through the same root.

**Validation:**

```pwsh
cargo run -- dependency source provider list cc-tweaked --branch 1.19.2
cargo run -- dependency source acquire cc-tweaked --provider any --branch 1.19.2
cargo run -- dependency source search IPeripheralProvider --dependency cc-tweaked --branch 1.19.2
cargo run -- dependency source search ComputerCraftAPI --dependency cc-tweaked --branch 1.19.2
```

**Completion criteria:** Search resolves the expected CC:Tweaked API definitions from managed source roots.

### [x] 11.4 Verify no-fetch search behavior with CC:Tweaked missing

**Completion notes:** Completed on 2026-07-12 against isolated absent source-cache homes. Normal and `--require-complete` searches both named `cc-tweaked/main/maven-sources` as missing, rendered the exact typed command `dependency source acquire --provider any --branch 1.19.2 cc-tweaked/main`, and performed no cache mutation or Maven/Git request. `--require-complete` then failed before ripgrep. Running that rendered command fetched the locked SquidDev sources payload into a fresh isolated cache; the subsequent complete search found `IPeripheralProvider`, including `api/peripheral/IPeripheralProvider.java`.

**Work:**

- Test against an isolated empty source cache while preserving the binary cache.
- Confirm search performs no Maven or Git network access.
- Confirm the warning names `cc-tweaked` and renders an exact acquire command with `--provider any --branch 1.19.2`.
- Confirm `--require-complete` fails before ripgrep and renders the same typed recommendation.

**Completion criteria:** The rendered command round-trips through Figue and acquiring sources makes the subsequent search complete.

### [x] 11.5 Preserve the existing CC:Tweaked binary smoke game test

**Completion notes:** Completed on 2026-07-13. The repository CLI completed
`cargo run -- run game-test-server --branch 1.19.2 --filter computer_craft_dependency_smoke`
successfully after the source-management cutovers. The initial sandboxed attempt could not open
the managed user-profile artifact-cache lock; the same command with normal cache access exited
successfully, so the outcome is a passing CC:Tweaked binary smoke rather than a cache-isolated
false negative.

**Validation:**

```pwsh
cargo run -- run game-test-server --branch 1.19.2 --filter computer_craft_dependency_smoke
```

**Completion criteria:** Source-management changes do not regress loading the CC:Tweaked API, turtle, or disk drive.

### [x] 11.6 Keep gameplay integration work outside this infrastructure acceptance phase

**Completion notes:** Completed on 2026-07-13. This phase added no gameplay-facing peripheral,
disk, label, printing-form, or turtle behavior. The passing binary smoke and managed authoritative
CC:Tweaked source search establish the required infrastructure boundary; the deferred gameplay
items remain intentionally unimplemented and require a separately scoped gameplay plan before
they are started.

**Deferred gameplay work includes:**

- SFM peripherals on managers/cables.
- Printing-form item detail reads.
- Program disk program reads/writes.
- Label holder reads/writes for disks and label guns.
- Turtle inventory and cable-network game tests.

**Completion criteria:** Infrastructure completion is not blocked on gameplay API design, but its managed source search and dependency metadata are sufficient to begin that work with authoritative CC:Tweaked sources.

## Phase 12: Propagate, document, and remove obsolete surfaces

### [~] 12.1 Propagate the v3 implementation through supported branches

**Completion notes:** The primary 1.19.2 implementation is complete and its full Rust gate
passes. A 2026-07-13 propagation-readiness check found all nine newer version worktrees clean;
they still contain schema-v2 lockfiles and await the intentional v3 migration. The 1.19.2
worktree contains the uncommitted v3 implementation, so no merge has been attempted. The dirty
feature worktree is intentionally non-versioned and excluded from propagation. Record the
primary commit plus each resulting merge and dialect-specific adaptation here once propagation is
authorized.

**Work:**

- Complete the primary implementation on `1.19.2`.
- Use `sfm-propagate-changes.exe git merge` according to `docs/AGENTS.md`.
- Preserve newer-version loader behavior during every merge.
- Add `@MCVersionDependentBehaviour` where Java differences are required; use an equally explicit Rust adapter boundary for CLI/toolchain differences.
- Upgrade each branch lockfile to schema v3 intentionally.

**Completion criteria:** Every supported branch reads/writes v3 and selects a tested dependency/Gradle dialect.

### [~] 12.2 Update repository guidance and command documentation

**Completion notes:** Primary 1.19.2 guidance was updated on 2026-07-13. `docs/AGENTS.md` now
uses the typed platform-source acquisition command instead of the removed `jar sources` command;
the CLI README documents explicit branch selection, portable/cache behavior, source
acquisition-versus-search, Gradle's schema-v3 consumer role, and deliberate CurseForge discovery
before exact-ID mutation. It now also documents the one-prompt, process-local
`CURSEFORGE_CORE_API_KEY` workflow for a sequence of read-only discovery commands. A repository
documentation scan finds no stale `jar sources` or
`source audit` primary workflow outside this historical plan. Keep this item in progress until the
supported-branch propagation and final documentation review in Phase 12 are complete.

**Affected paths:**

- `docs/AGENTS.md`
- CLI README/help tests
- Any task/docs references to `jar sources` or `source audit`

**Work:**

- Replace `jar sources` guidance with dependency source commands.
- Document explicit branch requirements.
- Document source cache paths and acquisition/search separation.
- Document Gradle as a compatibility consumer of schema v3.
- Document CurseForge discovery versus exact dependency mutation.

**Completion criteria:** Repository instructions contain no stale primary workflow using removed commands.

### [~] 12.3 Remove obsolete code and compatibility artifacts

**Completion notes:** The primary branch was re-audited on 2026-07-13. The removed `jar sources`
and top-level `source audit` commands have no remaining CLI/help references; the only retained
audit implementation is the intentional top-level `audit` backend. The production Gradle
dependency parser is absent, managed Git source acquisition contains no clone/checkout subprocess,
and the remaining source `DependencySourceCommand` is the active nested `dependency source`
command rather than a compatibility wrapper. Two stale client `--solo` help strings that named
`dependencies.gradle` now correctly describe schema-v3 lockfile dependencies. Retained
historical versioned dependency files remain inactive migration references until cross-branch
propagation is completed; finalize this item after that Phase 12 review.

**Candidates:**

- `src/cli/jar/jar_sources_cli.rs`
- `src/cli/source/source_cli.rs`
- old source output command wrappers made redundant by providers
- production Gradle dependency parser
- independent source-build clone implementation
- handwritten active versioned dependency declarations

**Completion criteria:** `rg` finds no obsolete command variants, help text, parser tests, or source-build Git subprocess labels.

### [~] 12.4 Run final Rust quality and behavior gates

**Completion notes:** The primary 1.19.2 gate set was re-run from the current Cargo source tree
on 2026-07-13. `check-all.ps1` passed with 249 tests passed, 0 failed, and 1 ignored. The
lockfile-facing commands `dependency list`, `dependency show cc-tweaked`, provider list,
idempotent source acquire, and complete `IPeripheralProvider` search all succeeded; the latter
returned both managed Git and Maven-source roots. `run compile`, `run test`, `run data`, and the
filtered `computer_craft_dependency_smoke` GameTest all exited successfully. The corrected
`run client --help` describes `--solo` in terms of schema-v3 lockfile dependencies. Live,
read-only Core API discovery returned Mekanism project `268560` and all nine matching 1.19.2
Forge files, including `4644795`. Retain this item as in progress until the same behavior is
demonstrated for the propagated branch dialects.

**Required validation:**

```pwsh
.\check-all.ps1
cargo run -- dependency list --branch 1.19.2
cargo run -- dependency show cc-tweaked --branch 1.19.2
cargo run -- dependency source provider list cc-tweaked --branch 1.19.2
cargo run -- dependency source acquire cc-tweaked --provider any --branch 1.19.2
cargo run -- dependency source search IPeripheralProvider --branch 1.19.2
cargo run -- run compile --branch 1.19.2
cargo run -- run test --branch 1.19.2
cargo run -- run data --branch 1.19.2
cargo run -- run game-test-server --branch 1.19.2 --filter computer_craft_dependency_smoke
```

**Completion criteria:** All commands pass from the local Rust CLI source tree, not a potentially stale PATH installation.

### [ ] 12.5 Run cross-version acceptance gates

**Completion notes:** _Not started. Record each branch result and adapter-specific failures/fixes here._

**Work:**

- Compile every supported branch.
- Run representative data runs for every distinct loader dialect.
- Validate dependency list/show for every branch.
- Validate Minecraft source acquisition for every distinct source pipeline.
- Validate no-fetch search behavior with complete and incomplete caches.
- Spot-check Gradle compatibility on every distinct Gradle dialect.

**Completion criteria:** No supported branch relies on early-Forge `fg.deobf` behavior where its loader no longer provides it.

### [~] 12.6 Confirm repository and cache portability

**Completion notes:** Primary 1.19.2 isolated-cache acceptance was completed on 2026-07-13.
With an absent `SFM_PROPAGATE_CHANGES_CACHE`, `dependency source search --require-complete`
reported both CC:Tweaked providers as missing, made no network request, and did not create a cache
directory. A fresh temporary cache then acquired the locked CC:Tweaked Git tree directly from its
public remote and a complete Git-only `IPeripheralProvider` search succeeded, without any user
checkout. A separate cache was populated with only the two locked Mekanism binaries and locked
Vineflower binary; its initially incomplete search made no source state, then explicit `api` and
`main` Vineflower acquisition recreated searchable source trees without a network fetch. The ten
current supported-worktree lockfiles contain no absolute Windows path value. The remaining gate is
to prove one managed bare repository satisfies multiple propagated branch revisions after every
supported branch has its intentional v3 lockfile.

**Work:**

- Test with no user Git clones.
- Test with an empty common source cache.
- Test with populated binary cache but missing source cache.
- Audit lockfiles for absolute local paths.
- Verify managed Git repositories can satisfy multiple branch revisions.

**Completion criteria:** A second developer can clone SFM, use the v3 lockfiles, acquire sources, and search them without recreating the original machine's directory layout.

## Overall completion criteria

This plan is complete only when:

- Every work item above is marked `[x]` with completion notes.
- The Rust crate forbids SFM-owned Serde APIs and rejects direct `serde`/`serde_json` dependencies while allowing third-party transitive dependencies.
- Schema v3 is the latest schema and all supported branches have intentional v3 lockfiles.
- Legacy lockfiles migrate only after all required semantic scopes, component groupings, artifact treatments, and policies are known.
- Rust and Gradle consume the same dependency intent and exact resolved coordinates.
- Loader/version adapters correctly handle early ForgeGradle and later NeoGradle dependency syntax.
- Mods are excluded from data runs by default in both Rust and Gradle behavior.
- `dependency list`, `show`, `add`, `remove`, `refresh`, and `artifact accept` are implemented and tested.
- Dependency declarations use semantic scopes rather than Gradle configuration names.
- Minecraft, loader, Maven, Git, and decompile source providers are managed through dependency source commands.
- Standalone mod decompilation uses an exact, benchmarked, fingerprinted tool without altering loader-authored Minecraft decompiler recipes.
- Maven source payloads remain separate from build dependency artifacts and classpaths.
- Provider listing and typed provider selection make acquisition behavior inspectable before it runs.
- Git source storage uses shared managed gix repositories rather than independent clones.
- Source search performs no acquisition and renders typed Figue acquire recommendations for missing sources.
- CurseForge search/file discovery is separate from exact-ID dependency mutation.
- `jar sources` and `source audit` have been fully removed and replaced.
- CC:Tweaked passes the binary, source acquisition, source search, and no-fetch warning acceptance cases.
- Rust quality checks, representative build/run commands, cross-version adapters, and portability tests pass.

## Risk register

### Serde guardrail coverage

Clippy disallowed-API lints only inspect compiled code and cannot reject an unused dependency declaration. The direct-dependency metadata check and explicit `forbid` lint levels are both required. Transitive Serde use by third-party crates is expected and must not make the quality gate unusable.

### Facet/Figue API movement

Pinning one fork revision limits drift, but the upgrade may affect derives, defaults, serialization, help, and argument rendering simultaneously. Complete Phase 1 before schema v3 to isolate this risk.

### Lockfile declarations plus derived checks

Maintained declarations and generated `derived_checks` can overwrite each other if ownership is not carefully enforced. Deterministic writing, strict legacy migration, and declaration-preservation tests are mandatory before mutation commands become authoritative.

### Gradle dialect differences

Early ForgeGradle and later NeoGradle do not share dependency syntax. The lockfile must stay semantic and adapters must be selected from the loader toolchain, not guessed from a single Minecraft version threshold.

### Git transport and authentication

Moving from the system Git executable to gix changes credential, proxy, TLS, cancellation, and progress behavior. Public HTTPS acceptance is required first; private repository support should be tested without weakening credential handling.

### Source archive safety

Third-party sources JARs are untrusted archives. The current source filetree helper must be hardened before it extracts arbitrary dependency sources.

### Decompiler output compatibility

Minecraft patches can depend on the output shape of a particular ForgeFlower or Vineflower invocation. Loader-authored MCP/NeoForm recipes must retain their locked tools. A newer standalone mod decompiler may be selected only for the independent fallback provider, with its artifact, Java runtime, options, and library inputs included in the cache fingerprint.

### Cache size and lifecycle

Minecraft sources, Git trees, Maven sources, and decompiled output can consume substantial space. Audit and explicit cleanup must understand references from all discovered lockfiles.

### Cross-version propagation

Build and Gradle files differ materially between branches. Propagation must preserve newer loader behavior and use explicit adapter boundaries rather than accepting broad conflict resolutions.
