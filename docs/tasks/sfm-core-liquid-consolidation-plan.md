# Shared Liquid sources and named Minecraft projections

Plan status: Active; goal approved and implementation started.
Primary implementation branch: `feat/sfm-main-source-projection`.
Last updated: 2026-09-30.
Intent audit: Passed 2026-09-30 against the available source-projection requests, decisions D1 through D7 and the latest layout correction.

## Outcome and completion boundary

Finish shared-template authoring consolidation across all ten supported Minecraft versions. Produce every declared release and development projection from one authored Liquid tree and explicit feature selections. Directory renaming or generation from historical source snapshots cannot satisfy this goal.

The authored root is `platform/minecraft/core-liquid-template/`, including `src/`. The authoritative projection catalog is `platform/minecraft/projections.json`. Each catalog key identifies its nested project directory under `platform/minecraft/projections/`. For example, `sfm-4.34.0/mc-1.19.2` identifies a checked-in release-compatible project; `sfm-dev/mc-1.19.2` identifies a separate ignored development project. Every generated project contains ordinary Java, resources and version-appropriate Gradle inputs.

The [original migration plan](sfm-main-source-projection-plan.md) and [30 September acceptance record](sfm-main-migration-acceptance-20260930.md) establish tested projection infrastructure. They do not establish completed shared-source consolidation. Preserve their historical evidence; this plan supersedes their source-layout and optional-consolidation disposition for new implementation.

## How to update this plan

- `[ ]` means not started; `[~]` means in progress; `[x]` means complete; `[!]` means blocked by an identified external or authority condition
- update a work item's heading and completion notes together; record decisions, exact commands, commits, skipped or cached tests and remaining limits beside the affected task
- keep one main implementation focus; name independent owners before parallel implementation; do not let agents edit the same template or public contract concurrently
- complete every required work item before closing this goal; do not substitute a smaller pilot or optional continuation for the ten-version requirement

## Authoritative user guidance ledger

| ID | Active guidance | Required plan consequence | Superseded by |
| --- | --- | --- | --- |
| CL-01 | Use `minecraft/core-liquid-template/src/`, `minecraft/projections/{projection key}/` and `minecraft/projections.json`. | Tasks 1.1, 3.1 and 3.2 implement these names under the existing `platform/` root. | — |
| CL-02 | A projection key may manifest nested directories. | Validate nested relative keys and use them as project identities, not flat preset identifiers. Task 1.1. | — |
| CL-03 | Each entry supplies Minecraft version, release/dev environment and enabled feature names. | Keep one authoritative selector catalog; expose typed values and explicit feature booleans. Tasks 1.1 and 1.2. | — |
| CL-04 | Templates select version-dependent Java directly, including grouped `case`/`when` import choices. | Extend the scanner and consolidate actual API differences, not just imports. Tasks 1.2 and 2.1. | — |
| CL-05 | Template every Java file without requiring `.liquid` filenames. | Every authored `.java` is Liquid input. Generated files alone need to parse as Java. Tasks 1.2 and 2.2. | — |
| CL-06 | Complete all ten Minecraft versions, not a representative pilot. | Required matrix covers 1.19.2, 1.19.4, 1.20, 1.20.1, 1.20.2, 1.20.3, 1.20.4, 1.21.0, 1.21.1 and 26.1.2. Tasks 2.2 and 4.1. | — |
| CL-07 | Start from current development HEAD while retaining a previously released baseline. | Preserve the current worktree's authored features and target-specific development behavior; isolate unreleased changes behind compilation flags. Tasks 2.2 and 2.3. | — |
| CL-08 | Match released behavior and compatibility; seek identical files and contents, but slight compiler/JAR differences are acceptable. | Compare against actual 4.34.0 tags and preserved JAR evidence; record every difference rather than treating compile success as parity. Tasks 2.3 and 4.1. | — |
| CL-09 | Checked-in generated sources show the most recent release; development runs do not check in intermediate outputs. | Track release projects only; keep dev outputs ignored and separate. Building a JAR is separate from syncing checked-in sources. Tasks 3.1 and 3.2. | — |
| CL-10 | Different feature sets may be manifested at the same time. | Support all 20 initial release/dev keys with independent provenance and build/cache identities. Tasks 1.1, 3.1 and 4.1. | — |
| CL-11 | Contributors can use normal Gradle, edit generated sources and submit PRs. Sync fails closed; backpropagation is explicit. | Preserve wrapper compatibility, edit detection, trace and byte-identical reconciliation. No claim of arbitrary automatic inverse templating. Tasks 3.1 and 4.2. | — |
| CL-12 | Prefer separate version-specific Gradle implementation files over large inline conditional bodies. | Keep explicitly selected authored build inputs inside the core root. Preserve wrappers and source-set conventions. Tasks 2.2 and 3.1. | — |
| CL-13 | Rust owns preprocessing; use Liquid and Facet without an application Serde bridge. | Reuse the installed locked dependencies and existing reflection bridge. Tasks 1.1 and 1.2. | — |
| CL-14 | Generated Java should look natural; crashes can be inspected against committed generated source. | Preserve readable outputs and generated-file warnings; do not pad output merely to mimic template line numbers. Tasks 1.2 and 4.2. | — |
| CL-15 | Java source analysis/refactoring must remain useful against generated code. | Update root resolution and read-only Java analysis; keep unimplemented rename/move behavior accurately documented. Tasks 3.2 and 4.2. | — |
| CL-16 | Preserve earlier feature, incident, SDK, JDK and container ideas while changing trajectory. | Keep their source and living plans; this goal changes source ownership, not their separate functional acceptance or publication authority. Tasks 2.2, 2.3 and 4.2. | — |
| CL-17 | Stop if disk space runs out; the user resolves it. | Stop on observed disk-space errors. Do not delete or relocate caches as a recovery measure. All tasks. | — |
| CL-18 | Use checkpoints and best judgment, without calendar-effort predictions or expanded authority. | Make local reviewable checkpoints; retain frozen dependencies and separate merge, default-branch, tag, push and upload decisions. Task 4.2. | — |

## Intent audit evidence

- pass 1, extraction: reread the available original migration request, answer batch D1 through D7, disk-space instruction, source-layout questions and exact core/catalog layout correction. Captured their independent constraints as CL-01 through CL-18
- pass 2, traceability: mapped every active requirement to tasks, validation and final acceptance. Read current scanner/context, catalog, contributor workflow, readiness policy and relevant original-plan ownership/decision sections. Independent read-only review confirmed that no further material user choice blocks the goal
- pass 3, adversarial omission: rechecked full ten-version scope, all Java as template input, nested keys, concurrent feature sets, release-compatible checked-in defaults, ephemeral dev roots, Gradle-only contributions, disabled-code absence and preservation of unreleased work. Explicitly removed historical whole-file overrides from final acceptance and preserved the disk-space stop rule
- known source limitation: intermediate implementation conversation may be compacted. The available original user requirements and existing D1 through D7/requirement ledgers supply earlier intent. Historical implementation evidence is referenced, not claimed as freshly rerun validation

## Verified foundation and source references

The planning checkpoint starts at `f3ff2f6425434f36c7c680fa909c977b158e1860`. The checkout was clean before these planning edits. No build, installer, generation, move or cleanup ran during readiness review.

Current entry points are under `platform/cli/sfm-propagate-changes/src/source_projection/`: `manifest.rs`, `context.rs`, `directive_scanner.rs`, `mod.rs`, `development_gradle.rs` and `promotion.rs`. Find the public selector/build CLI with `rg --files platform/cli/sfm-propagate-changes/src | rg 'source_cli|source_projection'` rather than assuming a filename.

At the planning checkpoint, the renderer exposed `minecraft_version`, `preset`, `features` and `targets`, and its Java scanner accepted only whole-line `if`, `elsif`, `else` and `endif`. Task 1.2 has since added grouped `case`/`when` without a dependency change. Ordinary Java array braces remain opaque rather than being parsed as Liquid interpolation.

Current `source-projection.json` has only three registered source features. `features: []` does not recover release behavior from the current primary tree. Release and development roots select many frozen divergent implementations, including 26.1.2 `ItemResourceType`. Those snapshots remain useful comparison evidence, but cannot remain production generation inputs at final acceptance.

Read [goal execution and testing readiness](goal%20execution%20and%20testing%20readiness%20guidelines.md) before autonomous build work. Read the [current contributor guide](../source-projection-contributor-guide.md) for existing conflict and provenance behavior. Keep previous [feature planning](../architecture/sfm-touch-display-and-client-manager-plan.md) separate.

## Confirmed design and bounded assumptions

Use the user-provided object keyed by projection paths. Each value has `minecraft_version`, `environment` and a `features` array. Resolve known absent features to false; reject unknown names. Environment is explicit template metadata, not an implicit enable-all policy. Do not retain `source-projection.json` as a competing public selector after migration.

Correct the example's release value `6.1.2` to `26.1.2`. Keep `mc-1.21.0` as the target/key name and use actual upstream Minecraft version `1.21`. Confirm loader/API boundaries from current sources; 1.20.1 uses the older Forge item-handler namespace despite its NeoForge loader designation.

All authored Java, resources, conditional file-membership decisions and Gradle selection inputs live under the core template root. Large genuine API differences may use explicit authored fragments/adapters there. Do not manufacture a common tree that merely chooses an entire historical version dump. If a bounded include/file-selection mechanism is needed, define and test it before use; no unrestricted filesystem includes.

The simple JSON catalog must remain readable. Internal feature definitions, support predicates and build metadata may live in typed tooling or clearly named core-owned metadata, without duplicating projection selectors. Record the chosen form under task 1.1 before its implementation.

Retain snapshots, imported ledgers and promotion journals as evidence during migration. Removing them is not required for readiness. Do not silently discard ignored recovery state, contributor edits or user saves when moving generated roots.

Dependency posture is frozen: preserve Cargo, Gradle and toolchain dependency identities and checked-in lockfile bytes. Deterministic acquisition from current locks remains allowed. Relocating/copying identical lock inputs as part of layout changes is allowed; changing dependency versions, provenance or adding dependencies is not. If a required path correction changes a dependency declaration or lockfile, request a specific authority amendment first.

## Execution order and parallel ownership

Proceed through contract/scanner, one real class, complete authored consolidation, build/root integration, then twenty-projection acceptance and handoff. The orchestrator owns this plan and shared public catalog changes. Scanner tests, version-API reconnaissance and feature-difference inventory can run in parallel with distinct owners. Assign implementation groups only after their shared metadata and file ownership are settled.

The entire plan is required core. No optional stretch item is claimed. Do not stop at task 2.1's demonstration and call the ten-version goal complete.

## Tasks

### [~] 1.1 Define the projection-key catalog and safe root contract

Completion notes: The user approved the full goal on 30 September. The orchestrator owns public CLI/context integration and this plan; `core_projection_catalog` owns the new pure catalog module. Start with read-only `source list`, `source show --projection <key>` and `source render --projection <key> --file <core-relative-java>` to prove explicit contexts and core-owned rendering without moving generated roots. Key-based sync/build/run integration remains required in tasks 3.1 and 3.2; the existing target/preset route stays explicitly transitional. Core-owned `feature-definitions.json` declares registered flags and target support, not another projection-selector catalog.

The initial twenty-entry catalog carries only the three already established flags, matching the existing default development selections. It is a read-only template-proof checkpoint, not a claim that empty release features reproduce the complete mod. Task 2.3 must expand the inventory before full production generation. Arbitrary safe nested keys are allowed; context values, not directory names, determine the Minecraft target.

Work: introduce typed Facet parsing for the user's `projections.json` map, explicit version/environment/features context, feature registration and all 20 initial keys. Choose core-owned build metadata, nested key validation, immutable release context fingerprints and independent per-key cache identity. Reject traversal, absolute/device paths, backslashes, empty segments, case collisions and ancestor/descendant output overlaps. Decide and document the new key-based CLI before implementation; no invented CLI command is treated as existing evidence.

Validation: add manifest/CLI unit tests for valid keys and every rejected collision, unknown flag, unsupported target and invalid context; verify the future public syntax with the rebuilt binary's `source --help` and subcommand help.

Completion criteria: one authoritative catalog resolves all 20 keys without changing dependencies or conflating a release project with development output.

### [x] 1.2 Support the requested Liquid version and feature decisions

Completion notes: The scanner now supports typed nested `case`/`when`/`else`/`endcase`, grouped comma or `or` alternatives and the explicit environment/key fields. Direct SFM renderer tests and the twenty-context `ItemResourceType` golden test passed. Unknown conditions in inactive branches, mismatched control blocks, invalid selectors, literal delimiters, CRLF and opaque Java braces retain fail-closed coverage. No dependency changed. Selected non-Java core text will use this same controlled renderer; its input-selection integration remains task 2.2.

Validation evidence, 30 September: `cargo test --offline --locked --lib source_projection::` passed 250 tests with four existing import-only tests ignored; `cargo test --offline --locked --lib cli::source::projection_catalog_cli` passed ten Windows-applicable tests, including all twenty contexts. The pure catalog tests passed 14 tests. Fresh worktree-binary `source --help`, `source render --help`, JSON `source list` and `source show` passed. A live loop of `source render` passed all twenty catalog keys, producing three distinct body hashes and reporting zero writes. These are source-render proofs, not Java compilation or complete-mod release acceptance.

Tooling gate: `check-all.ps1` passed dependency policy, formatting, all-feature Clippy/build and all 42 library-module shards. It then stopped at Java-analysis integration: ten tests passed and three failed because the sandbox blocked acquisition of the existing pinned JBRSDK with socket error 10013. The host rerun of `scripts/test-bounded.ps1 -Shard integration:java_analysis_scenarios` passed all 13 tests. Host continuations passed the other three integration shards (12, 40 and 3 tests), the standalone refusal test and the dedicated ten-target fixture. All-feature binary/doc commands passed but contained zero tests. Thus the initial 48-shard coverage gate completed by recorded continuation, not by pretending the sandbox invocation exited successfully. No JDK identity or lockfile changed. The next registered consolidation/collector changes need their own current-source gate.

Work: extend whole-line scanning with typed `case`/`when`/`else`/`endcase`, comma-separated version alternatives and nested feature conditions. Expose environment and projection identity. Preserve Java strings, braces and text as opaque chunks, full-line directive diagnostics, deterministic bytes and unknown-variable refusal. Apply the same explicit context to selected non-Java templates where needed.

Validation from `platform/cli/sfm-propagate-changes`: `cargo test --offline --locked --lib source_projection::directive_scanner` and `cargo test --offline --locked --lib source_projection::context`. Add grouped-version, nested-block, mismatch, literal-delimiter, feature false/unknown and environment fixtures.

Completion criteria: the user's grouped import example renders correctly through SFM's scanner, not just raw Liquid; invalid directives fail with source lines.

### [~] 2.1 Consolidate ItemResourceType as the first real source proof

Completion notes: `core_feature_inventory` authored this class under the new core root, with shared bodies and three API groups: Forge through 1.20.1, older NeoForge handlers through 1.21.1, and 26.1.2 transfer handlers. The actual SFM renderer now matches original LF-normalized bytes for all ten release/dev pairs, both in the CLI golden test and live rendering. This is not yet Java compiler evidence or full-project provenance migration. Release/pinned-development sources match within each target for this class. Preserve the existing 26.1.2 buffer predicate unchanged; semantic fixes are not part of consolidation.

Work: move authored ownership into `core-liquid-template/src`, use visible version conditions/adapters for imports, generic handler types and methods, and render this class for every supported release/dev context. Preserve 26.1.2 transfer API semantics. Stop selecting this production source from release/development baseline overlays.

Validation: compare generated source/API shape with the recorded originals across ten versions, compile every affected project with fresh-source evidence, and exercise representative item-handler simulation/execution tests. Record exact final commands after task 1.1 defines the key-based interface.

Completion criteria: provenance for every ItemResourceType projection points to core-owned authored inputs; one intentional common edit reaches all intended targets without per-target hash adoption.

### [~] 2.2 Consolidate the remaining authored source and build inputs

Reconnaissance notes: A read-only inventory at starting HEAD found 1,605 distinct main-Java paths across twenty historical release/development contexts, with 14,291 path occurrences. Of these, 1,136 paths have a single blob wherever present, 469 have variant bodies, and 1,085 have membership variation. These historical counts guide work grouping, not current-source completion. Compare actual current bytes as well as ledgers: current HEAD versus the 1.19.2 release tag changes 1,918 source paths, including 1,114 main Java paths. No version-owned behavior may be replaced merely because an old classification says it was shared.

In-progress ownership: `core_liquid_scanner` prepared the pure migration-time `variant_consolidation.rs` authoring helper. It factors shared lines, uses reviewed named feature owners and grouped Minecraft cases, handles absence separately and internally checks reconstruction. It rejects ambiguity, unsupported EOF changes and no-anchor whole-file dispatch; it is not a production historical-input route. `core_projection_catalog` prepared `core_inputs.rs`, a core-only selector/collector with sparse file membership and selected Gradle inputs. Its predicates use targets and explicit features only, never environment/preset/key dispatch. The orchestrator extracted central `core_features.rs` and `core_catalog.rs` loading and wired the CLI to that common service.

Second-slice evidence, 30 September after local checkpoint `a8b9028b3`: all four helpers are registered. `cargo test --offline --locked --lib source_projection::core_` passed 17 tests; the initial consolidator passed nine tests, including actual-renderer common-edit propagation and byte-exact reconstruction; the refactored catalog CLI retained its ten passing tests and twenty-context golden proof. The next host `check-all.ps1` stopped at a Clippy panic-documentation finding for an unchecked public-path assertion. That assertion is removed in the expanded, fingerprint-bound region-review API; its six additional tests and the named-sync integration await their combined current-source gate. No real complete project has been manifested from these helpers yet, no generated root moved, and no source/build membership metadata was substituted for the outstanding ownership review.

Current integration focus: the reviewed shared-source seed contains 195 universally shared paths (193 Java files and two vendored auxiliary files) and 11 target-membership paths. Preserve raw Git bytes, including CRLF, during their one-time import. The seed ledger is migration evidence only; normal rendering reads the resulting authored core files, not Git. Region review separately owns membership and content hunks and refuses stale reviews or ambiguous functional owners. Named sync/provenance uses explicit catalog ownership rather than disguising nested keys as flat presets.

Actual shared import, 30 September: `source seed-shared --repo-root .` independently verified all 206 paths and twenty pinned trees before `--apply` created the absent core files. An independent SHA-256 pass rechecked every created file against the ledger. The authored tree now has 205 Java files including the previously consolidated `ItemResourceType`, plus the two vendored auxiliary files. Its metadata records 11 exact target-membership rules; standalone build inputs remain pending. No generated project root moved. Raw CRLF and historical vendored whitespace are intentionally retained rather than silently normalized.

Combined gate history: named-project CLI focused tests passed six cases, including contributor edit refusal/reconciliation, release-check versus ignored-development-sync behavior and refusal to fall back to historical inputs. An intermediate `check-all.ps1` passed dependency policy, formatting, all-feature Clippy/build and the first 37 library shards, then failed the Windows junction fixture because `cmd.exe` interpreted a forward-slash fixture path as a switch. That fixture now creates a literal PowerShell junction in a path containing spaces; its seven focused tests passed. The fresh complete gate below supersedes the intermediate failure. Version-only seed focused tests separately passed ten cases; the Minecraft matrix is still pending.

Version/build import evidence, 30 September: the actual renderer reconstructed 319 raw-byte Java templates and 109 exact logical resource outputs across the pinned twenty-context matrix. Apply created 612 inputs, all independently SHA-256 checked. For exactly 43 otherwise blocked Java paths, the reviewed `sfm:java_lf_final_newline@1` migration policy changes CRLF to LF and appends a missing final LF; it cannot trim, reindent, remove BOMs, accept lone CR or change tokens. The ledger binds 116 raw/normalized witness hashes and newline counts. Actual preview and Apply then reconstructed all 362 Java templates, created those 43 additional files and rechecked all 655 version-seed inputs. Resources stay byte-exact. The source membership metadata adds five further target rules, without changing the existing 120 version-seed rules.

The build seed created 229 deduplicated Gradle payloads, including 123 identical across all contexts and the exact historical/current lock copies. All 229 authored hashes were independently verified. Metadata currently selects the 151 released project inputs; nine proposed locked-build alternatives and 24 other newer inputs remain explicitly unselected pending their functional ownership. No dependency identity or lock content was changed. Settings use core-owned Minecraft cases for a literal project name, not projection-directory inference. All twenty live `source project dry-run` calls passed with zero writes and explicit `compilation: not_performed` / `release_compatibility: not_performed`. This validates partial core generation, not complete source membership or a build.

Focused current-source results: the normalized version-seed module passed 15 tests, core/build helpers passed 40 tests before normalization, the expanded region-review consolidator passed 17, and the repaired named-root module passed seven including spaces and mixed path separators. The compatible JDK reader passed eight tests: preserved schemas 1/2/3 retain exact-major discovery, while schema-4 pins retain their checksum validation. A complete current-source gate and grouped checkpoint remain pending. Windows Git staging required command-scoped `core.longpaths=true`; no machine-wide configuration changed. Historical whitespace review is still pending before checkpoint.

Auxiliary import, 30 September: `source seed-auxiliary --repo-root .` independently verified 346 paths against all twenty frozen trees, then `--apply` created those absent core inputs (691,076 bytes). A separate SHA-256 pass checked all 346 with zero mismatches against ledger `sha256:bf29ed5dcd036120bb50a355f709ba03db702d3f8923d04ded3247e202fbe4d8`. The cohort contains 222 universally shared inputs and 124 whose membership is genuinely Minecraft-target-specific, with no release/development differences. Core metadata now has 260 source rules and 151 selected project files. The auxiliary helper's 13 focused tests passed, and all-feature Clippy passed after bounded lint repairs. The fresh complete gate is recorded below.

Fresh tooling gate, 30 September: host `pwsh -NoProfile -File .\check-all.ps1` passed dependency policy, formatting, all-feature Clippy/build, all 48 bounded test shards, then binary and documentation tests. The library partition covered 1,284 tests; the source-projection shard passed 335 tests with four intentionally ignored, and the Java-analysis shard passed 221 with three ignored. Integration shards passed 13 Java-analysis, 12 release-review Git, 40 release-review materialization and three JAR-absence tests. The dedicated ten-target fixture passed. Binary/documentation harnesses contained zero tests. Existing pinned downloads and caches were reused; no dependency identity or lockfile changed. This gate covers the registered tooling/import implementation, not the newly prepared unregistered feature-slice test modules or Minecraft compilation through the new core route. No client was launched. The authored `core-liquid-template/build/` inputs have a narrow Git-ignore exception; all 229 prepared authored hashes matched, including the five explicitly specialized settings templates. The upstream 1.20 wrapper retains its two witnessed trailing spaces under one exact-path whitespace exception.

Historical whitespace disposition: imported bytes remain unchanged. `.gitattributes` preserves raw line endings and has narrow trailing-space exceptions for the vendored Simmetrics subtree and exact witnessed source/fixture paths, plus exact final-blank-line exceptions. Other paths retain whitespace checks. After independently checking the imported hashes, staged `git diff --cached --check` passed; this is not evidence that feature reconstruction or Java compilation has completed. A new all-twenty core-only dry-run pass is underway before the first execution-side/interval templates are added.

The auxiliary-stage dry-run pass completed for all twenty entries, with no writes and no compilation or compatibility claim. Artifacts per release/dev target were: 1.19.2/1.19.4/1.20 1,029 each; 1.20.1/1.20.2/1.20.3/1.20.4 1,031 each; 1.21.0 1,034; 1.21.1 1,035; 26.1.2 1,045. The following bounded authoring slices register `client_actions`, `sfml_execution_side` and `sfml_worded_intervals` as explicit selectors, initially disabled in all catalog entries. Their declarations reflect witnessed source targets, not independent combination or build acceptance. Basic action carriers and four language files are being authored; the complete parser, runtime, registrations, assets and dependency closure still require the remaining tasks. Do not enable those partial features in a user run merely because their names parse.

First feature-template evidence: four language files are authored with functional side/interval guards and genuine 26.1.2 Identifier adapters. Actual `source render` calls produced 40 raw-byte matches against released feature-off witnesses for `Interval` and `ProgramBuilder` across all twenty catalog entries. The two action carriers preserve 1,551 raw bytes and are explicitly omitted unless `client_actions` is enabled. Two cross-target Java templates separate `tick_graph_null_samples` and `test_barrel_constructor_delegation`; 22 actual renderer calls produced 20 raw-exact tank bodies and two Tick bodies matching the approved one-terminal-LF normalization. Root-authored `ProblemTracker` separately guards `unloaded_config_defaults`; its twenty actual feature-off outputs match the released witness after precisely recorded CRLF-to-LF normalization. These normalization exceptions have raw/normalized hashes and do not authorize token changes. Feature-on reconstruction from the independent expander is recorded separately; real feature-on renderer tests are being prepared as unregistered test modules. No complete Java project or feature combination is claimed compiled.

Work: inventory every current release/dev input by path and target. Fold ordinary divergence into shared templates; isolate genuine API differences in bounded core-owned adapters/fragments. Cover main, gametest, datagen, generated assets, resources, membership/exclusions, wrappers, Gradle scripts, settings/properties and fixtures. Prefer selected build files over long inline Gradle switches. Preserve all current target-specific behavior and unreleased features.

Validation: maintain a complete owner/membership matrix and test representative primary edits across versions. Audit generation read sets: no production source may resolve from historical overlays, Git blobs or legacy version-branch refs. Generate from a fresh branch checkout with audited historical input trees unavailable to the renderer.

Completion criteria: every authored production input is core-owned; all ten versions are reproducible without historical source overrides. Copying ten entire source trees into the core root does not qualify as consolidation.

### [~] 2.3 Gate the complete unreleased feature difference

Reconnaissance notes: Required feature families include workspace/actions/editor/explorer/history/review/theme, terminal/control, packet computation and SFML, touch display/client manager/consent/signing/inbox/raster, multiplayer authorization, manager tooling, ComputerCraft, redstone/buffer and compatibility/event-bus behavior. The 1.19.2 release network channel is `1.0.0`, versus `1.5.0` in current development with appended registrations and direction changes. Feature exclusion must restore protocol and changed existing-class semantics, not only omit new packet classes. This is an inventory witness, not a completed feature taxonomy.

Detailed inventory, 30 September: the [feature ownership inventory](sfm-core-feature-ownership-inventory.md) records twenty witnessed trees containing 3,001 unique source paths, 1,856 never present in any release context. There are 195 main-Java paths identical across all twenty contexts and 11 identical paths whose membership is genuinely version-specific. Of the 1,028 additions versus the 1.19.2 release, a preliminary classifier covers 756; 272 remain unresolved. The 86 changed existing main-Java classes contain 356 zero-context diff hunks, requiring declaration/hunk ownership. `SFMClientAction` needs separately gated programmatic descriptor methods; `LabelGunActions` needs separately gated Client Manager overloads to preserve broader ComputerCraft support. The current `echo_action` support declaration does not yet describe all witnessed development implementations. Do not accept independent feature combinations from class presence alone or register the proposed taxonomy as proven.

Work: inventory current-development versus 4.34.0 differences, including changed behavior inside existing classes, assets, registrations, commands, data/network formats and dependency use. Define named compilation features and supported targets. Render release behavior from the same core templates while retaining authored development work. Do not treat the existing three flags or empty features as a complete release preset.

Validation: enabled/disabled pairs prove expected gameplay/API behavior and absence of disabled classes, registrations and assets in sources and JARs. Compare all release contexts with actual tag membership/bytes and documented compatibility evidence; record deliberate compiler/packaging differences.

Completion criteria: explicit flags account for all unreleased production differences. Release fidelity comes from templating, never a hidden frozen-source replacement.

### [~] 3.1 Manifest named projects safely and preserve contributors

Implementation notes: the new `source project` route is being wired to core-only collection and exact catalog destinations. Its planned dry-run/check/sync/reconcile operations expose no source-overlay, historical-preset or arbitrary-output-root override. Named provenance has a separate schema and owner; legacy workflows retain their existing bytes and explicitly refuse named owners until their migration is reviewed. Trace exposes the actual nested owner. The `sfm-dev/` output prefix is ignored, but policy checks use the catalog environment and Git's real ignore/index rules rather than inferring an environment from directory spelling. These changes are not yet current-source gate evidence or complete project generation.

Work: migrate generated projects into the catalog-keyed `projections/` paths with ordinary version-correct Gradle layouts. Track release roots and ignore development roots. Adapt sync, trace, reconciliation, promotion and provenance to the new owner model. Preserve edited outputs, file-removal review and transaction recovery. Release/dev projections coexist without overwriting each other. Keep build/JAR creation separate from checked-in source sync.

Validation: synthetic contributor-edit, partial-write/recovery, preset/context-change, omitted-file and overlapping-root tests. Run deterministic repeat-generation checks. Use a guarded isolated wrapper-only harness to prove contributor builds without requiring Rust in those projects.

Completion criteria: a contributor can build/edit a checked-in release projection; the next sync refuses conflicting edits until explicit backpropagation and reconciliation.

### [~] 3.2 Integrate build, run and Java tools with the new roots

Implementation notes: named build/run entry points reuse the existing bounded Gradle/JDK execution path. Release build/run preparation checks current sources without synchronizing them. Development preparation may sync only the exact ignored destination and must retain contributor-edit refusal. Invalid task/profile arguments fail before writes or JDK acquisition. Production compile, launch and Java-analysis root evidence remain pending.

Work: update source catalog, root discovery, Minecraft compilation/testing/runs, lock/profile resolution, Java analysis and package verification. Current build/run requires external output roots; add narrowly guarded support for declared ignored in-repo dev roots without enabling arbitrary or release-root overwrite. Preserve version-appropriate Java 17/21/25 and cache separation. Inspect or move only known goal-owned outputs; do not modify user worlds.

Validation: prove title-screen/client launch and selected headless/client-participating tests using file-driven puppets where appropriate, not computer-use control. Query generated Java roots across old/new APIs. Verify release trees remain unchanged during dev build/run and record updated commands from actual help/output.

Completion criteria: Rust build/run/test/analysis resolves every declared projection unambiguously; development runs do not alter checked-in release projects or unrelated output roots.

### [ ] 4.1 Validate the complete release and development matrix

Work: render all 20 initial contexts, perform repeated deterministic checks, production compilation/packaging, relevant unit/GameTests, enabled/disabled feature checks and release compatibility comparisons. Reuse historical evidence only as labelled foundation; record new-source validation separately and disclose cache reuse.

Validation: run `check-all.ps1` from `platform/cli/sfm-propagate-changes` after Rust changes, including its bounded test runner. Use the new key-based source commands established in task 1.1 for each matrix row. Prove Gradle-only compatibility through the isolated harness; inspect actual source/JAR membership and behavior, not only task exit codes.

Completion criteria: every required matrix row has evidence, no unexplained semantic release differences remain and the production renderer has zero historical source overrides.

### [ ] 4.2 Finish documentation, local checkpoints and operational handoff

Work: update the contributor guide and old-plan navigation to the actual root/catalog workflow. Document feature names, keys, source ownership, backpropagation, release/dev policies and manual launch/test commands. Keep earlier feature plans discoverable. Make grouped local commits and final source/tool checks; do not merge, push, tag, upload, change the default branch or retire version branches.

Validation: check docs against real CLI help and verified outputs. Run `git diff --check`. After the last relevant source change, rebuild/install affected tooling with `platform/cli/sfm-propagate-changes/install.ps1`, verify PATH resolution, revision/hash and read-only smoke behavior, then provide a copyable test handoff.

Completion criteria: all tasks are complete with checkpoint evidence; installed tooling is current and the user can immediately inspect/run a named projection. Report final process state and any manual-only limitation.

## Required target matrix

Each row requires both `sfm-4.34.0/mc-<target>` and `sfm-dev/mc-<target>`, with independent provenance, deterministic generation, compile/package, supported tests and contributor build proof. Enabled features retain their recorded target support; this goal does not promise every unfinished feature on every version.

| Target key | Actual Minecraft version | Java major | Release evidence | Development evidence |
| --- | --- | --- | --- | --- |
| 1.19.2 | 1.19.2 | 17 | Pending | Pending |
| 1.19.4 | 1.19.4 | 17 | Pending | Pending |
| 1.20 | 1.20 | 17 | Pending | Pending |
| 1.20.1 | 1.20.1 | 17 | Pending | Pending |
| 1.20.2 | 1.20.2 | 17 | Pending | Pending |
| 1.20.3 | 1.20.3 | 17 | Pending | Pending |
| 1.20.4 | 1.20.4 | 17 | Pending | Pending |
| 1.21.0 | 1.21 | 21 | Pending | Pending |
| 1.21.1 | 1.21.1 | 21 | Pending | Pending |
| 26.1.2 | 26.1.2 | 25 | Pending | Pending |

## Overall completion criteria

- every required task is complete with current-source evidence, and both contexts in every target-matrix row pass their declared validation
- all production generation inputs are core-owned; grouped Liquid version/feature decisions work, representative common edits propagate, and no historical source override or ten-tree snapshot substitute remains
- checked-in projects remain release-compatible; independent development/feature contexts preserve unreleased work without checking in intermediate development outputs
- ordinary Gradle contribution, generated-file provenance, conflict refusal, explicit backpropagation and generated-root tooling work in the new layout
- final installed-tool freshness, dependency preservation, local checkpoints and a copyable manual-testing handoff are recorded; no merge, push, tag, upload or default-branch change was performed

## Risks and controls

| Risk | Required control |
| --- | --- |
| Snapshot selection disguised as source consolidation | Audit physical generation reads and require shared primary-edit proofs; no baseline source reads at final acceptance. |
| Release flags account for only obvious new classes | Complete path/behavior/membership inventory and enabled/disabled artifact tests in task 2.3. |
| Newer transfer APIs or target-specific behavior are overwritten | Preserve target output/API witnesses; adapter tests and all-ten compilation. |
| Nested keys clobber contributors, saves or another context | Strict key/root validation, conflict tests, independent caches and staged transactions. |
| Passing cached builds, stale tools or historical evidence overstate acceptance | Label evidence freshness; perform conclusive current-source checks and installer verification after final mutation. |

## Operational readiness

Target branch/checkpoint: `feat/sfm-main-source-projection` at `f3ff2f6425434f36c7c680fa909c977b158e1860` before planning edits. Tooling and runtime inputs will change during implementation; final installer evidence is pending. No install is required merely for this documentation checkpoint. Dependency posture is frozen, with no dependency changes or new reference clones performed in readiness review. Process-lifecycle authority follows the bounded repository guide when implementation starts; no processes were stopped or restarted in this review. Final build/cache state, installed revision/hash, manual test command and runtime state must be recorded under task 4.2. Stop immediately on a disk-space error and wait for the user; do not mark that user-requested wait as goal completion.
