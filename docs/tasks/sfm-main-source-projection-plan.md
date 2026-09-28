# One-branch Minecraft source projection

Status: active design and baseline audit, with an isolated exploratory branch. Last reviewed: 2026-09-28.

## Outcome

Maintain one SFM development branch that can produce buildable, checked-in Minecraft projects for every supported version. A Rust sync command treats the primary Java source tree as Liquid input and projects it, version-specific implementations and feature presets into ordinary Java, resources and Gradle files. Contributors can build a generated version project with Gradle without installing the Rust toolchain. The generated projects remain reviewable and debuggable in their own right.

The checked-in version projects represent the **most recent released feature set**. At the first migration baseline that is 4.34.0. Developing a feature does not sync it into these projects: the Rust toolchain can build and run from a temporary projection of a development preset. Building a JAR and publishing or checking in generated source are separate operations. A later release deliberately advances the checked-in projection and its immutable preset identity.

This is a new track, not completion of the Touch Display and Client Manager track. That track remains recorded in `docs/architecture/sfm-touch-display-and-client-manager-plan.md`, with its release gates still open. The Codex-assisted workflows, JDK, container and tunnel investigations remain in their own plans. Do not delete or silently absorb their work while changing source ownership.

## Current checkpoint and branch policy

- Exploratory branch: `feat/sfm-main-source-projection`, created from committed 1.19.2 HEAD `7cc64ea9` to isolate planning. The existing 1.19.2 working tree had extensive uncommitted work at branch creation. None of that work was copied into this worktree or abandoned. Review and group-commit it first, then rebase this exploratory branch onto those checkpoints before making it the integration line.
- Candidate long-lived branch: `sfm-main`, only after the projection and contributor workflow pass the acceptance gates below. Changing GitHub's default branch, publishing releases and retiring the version branches are separate decisions.
- Release references: each version's 4.34.0 Git tag is the immutable shipped-source reference. The 1.19.2 reference is `4.34.0-1.19.2` (`31135b8e`). The development branches are hundreds of commits newer; a release-compatible preset cannot be assumed to reproduce the old build merely by setting a few booleans.
- Existing version branches and `sfm-propagate-changes git merge` remain supported until replacement is demonstrated across all relevant versions. Keep emergency release branches based on release tags possible throughout the transition.
- Changes in the dirty 1.19.2 checkout need ownership review and small, themed commits before any transfer to this branch. Rebase or cherry-pick only reviewed checkpoints; do not make a blanket snapshot commit or reset that checkout.

## Requirements and acceptance evidence

| ID | Requirement | Acceptance evidence |
| --- | --- | --- |
| SP-01 | One primary source tree selects code by Minecraft version and explicit build-time feature preset. Disabled feature code and assets are absent from the output JAR, not merely unreachable at runtime. | Inspect a generated source tree and JAR; tests assert absence of a disabled class, registration and resource. |
| SP-02 | Checked-in generated project for each version reflects the most recent release and uses ordinary Java and version-appropriate Gradle files, with no Rust process required for a normal contributor build. | Fresh checkout of generated project builds with its wrapper and documented JDK, with Rust unavailable; release preset identity is recorded. |
| SP-03 | Projection is deterministic and hermetic, and refuses unknown flags, missing inputs, unsupported template constructs and conflicting contributor edits. | Repeat sync byte-for-byte; negative tests fail with actionable paths and do not overwrite edits. |
| SP-04 | The source of every generated file is traceable; contributors can edit generated files and submit ordinary PRs without losing their work at the next sync. | Manifest records provenance and hashes; reconciliation procedure and conflict test. |
| SP-05 | A `released-4.34.0` baseline preset reproduces shipped behaviour and compatible data formats while development presets retain unreleased work. Ideally JAR files and contents match; byte identity is not a gate when compiler or packaging inputs are not reproducible. | Per-version JAR entry, API, data-format, registry, resource and representative gameplay comparisons against 4.34.0 tags; differences listed and approved. |
| SP-06 | Current Touch Display, Client Manager, CLI, incident-fix and toolchain plans and code survive the transition. | Traceability ledger and reviewed commit transfer; no open release gate marked complete without evidence. |
| SP-07 | Existing Rust build, Java analysis and refactoring tools resolve generated per-version roots explicitly. | Toolchain compile/index tests over two generated roots, then the full supported matrix. |
| SP-08 | All ten currently supported Minecraft versions migrate to the single-branch projection while remaining testable and independently releasable. | Full matrix build, Gradle-only proof and focused tests; release process documented before retiring old merge flow. |
| SP-09 | Development builds can use an uncommitted, temporary projection without changing checked-in release projects. Multiple named feature sets may coexist without overwriting each other's manifests or outputs. | Test run from development preset leaves release tree clean; independent manifests identify version and preset. |

## Source and projection contract

Keep `platform/minecraft/src/{main,gametest,datagen,generated}` as the initial primary source layout. Every primary `.java` file is Liquid input, retains its `.java` filename, and may contain directives. It need not be valid Java before projection. Introduce `platform/minecraft/mc-version/<version>/` as checked-in, release-preset standalone Gradle projects. Other named feature sets use separate staging/output roots and manifests, never the same output path. Do not rewrite the primary tree or existing version projects in place during the pilot. The final primary layout may change after evidence from the pilot.

Use three mechanisms, chosen per file, while running every primary Java file through the projection pipeline:

1. Render a common `.java` file without directives when it is valid for all selected versions; keep its bytes unchanged apart from the generated-file banner.
2. Use dedicated, full-line Liquid control directives inside ordinary `.java` files for a small conditional region. Other text formats may be selected for templating explicitly.
3. Select separate, named version implementation files when the difference is substantial. Prefer selecting a file path over embedding two long implementations in one template.

Gradle wrappers, `settings.gradle`, `gradle.properties`, version-keyed dependency scripts, access transformers, mixins, resources and the toolchain lockfile are project inputs too. Some can be copied, some selected, and some templated. The current Gradle scripts assume paths relative to `platform/minecraft`, and `settings.gradle` derives its project name from parent directories; the new roots need explicit handling and tests. Preserve legal `src/main`, `src/gametest`, `src/datagen` and `src/generated` source sets. Do not project build, run, cache or IDE output directories.

The feature manifest must give each flag a stable ID, default per preset, supported-version predicate and exact source/resource/dependency effects. Preset identities are immutable once used for a release. Evaluate version predicates in Rust and give Liquid booleans and strings, not lexicographic Minecraft version comparisons. Detect incompatible and missing flags before rendering. Keep the existing lockfile feature/profile semantics compatible while adding source-level selection; do not accidentally change the default Gradle dependency profile. Manifest entries are keyed by both Minecraft version and feature-set identity; a development projection must never masquerade as a release projection.

Implement a narrow Facet-to-Liquid value conversion, initially for the typed projection context. Use `facet-value` to walk `Facet` values into Liquid's public `Object` and `Value` types; no application-level Serde or JSON conversion is needed. Validate numbers and reject unsupported values. Pin and review Liquid and `facet-value` additions as a specific authorized dependency change; unrelated dependency upgrades remain out of scope. A template must not load arbitrary partials or read the filesystem beyond declared inputs. Test missing variables and delimiter collisions.

Liquid hardcodes `{{...}}` and `{%...%}` parsing, and existing Java files contain ordinary `{{` array initializers. To make every `.java` file safely templatable, use a small line-aware scanner for a deliberately limited set of **whole-line** directives (`{% if ... %}`, `{% elsif ... %}`, `{% else %}`, `{% endif %}` initially). The scanner turns Java text between directives into opaque values in the Liquid context and renders a synthetic Liquid control skeleton that references those values. Liquid never reparses the Java chunks. A directive-free file takes an identity fast path. Reject misplaced, unknown or unbalanced directives with source line numbers. This avoids both a Liquid fork and fragile `{% raw %}` wrappers around arbitrary Java. Inline interpolation can be added later with an explicit escape contract, not by treating every Java `{{` as a template expression.

An `if (@feature flag)` is not Java syntax. `if (SFMFeatureFlags.X)` is legal but javac still type-checks both arms, so it cannot hide absent version APIs; a runtime getter also leaves code in the JAR. Use source projection before Java parsing for such differences. Generated code should be formatted for normal review, not padded with blank lines merely to preserve template line numbers. Runtime errors map to committed generated sources. Author-side diagnostics can gain a manifest or source map later.

Generated Java and Gradle files get a short `GENERATED; edit the primary source or reconcile this file` comment where the format allows comments. JSON and other comment-free formats use a sidecar provenance manifest; never invalidate a format to insert a banner. The generated tree contains a manifest with input path/hash, selected overlay, flags/preset, tool version and last generated output hash for each file. Sync writes into a temporary staging tree and replaces only clean generated outputs after validation. If a contributor changed an output, sync reports a conflict and preserves it. Contributors may still build and submit changes to generated Java with plain Gradle; an explicit, reviewed reconciliation workflow maps that change back to primary source or a version overlay before the next sync. Arbitrary template inversion and automatic AST refactor backpropagation are not initial promises.

## Work phases

| Phase | Deliverable and checkpoint | Exit evidence | State |
| --- | --- | --- | --- |
| M0 | Inventory release tags, current branch deltas, dirty work ownership, versions, Gradle/JDK matrices and existing feature profiles. Record preset questions and baseline differences. | Inventory table and reviewed transfer commits. | In progress |
| M1 | Define typed projection manifest, presets, overlay order, file ownership, provenance and conflict policy. Add focused fixtures before changing production files. | Golden-output, unknown-flag, collision and overwrite-refusal tests. | Planned |
| M2 | Add `sfm-propagate-changes source sync/check` using Liquid and Facet, with deterministic staged writes. Add temporary `source build/run` projection support separate from sync and an explicit dry run. | Double-run identical output; malformed inputs fail without writes; development run leaves checked-in release tree clean. | Planned |
| M3 | Project two representative versions, starting with 1.19.2 and one later API/Gradle boundary. Include one true version difference and one disabled feature. | Both generated Gradle projects build without Rust; disabled code/resource absent from JAR; generated root identifies release preset. | Planned |
| M4 | Adapt Rust source catalog, build, Java analysis and relevant tests to generated roots. Define contributor edit/reconcile commands or documented manual process. | CLI compile/index tests; edited-output conflict is demonstrated. | Planned |
| M5 | Transfer reviewed current development work and expand the projection across the supported version matrix. Keep per-version source overlays where needed. | Matrix builds, focused GameTests and release-preset comparison reports. | Planned |
| M6 | Update contributor, release and emergency-hotfix guidance. Decide whether `sfm-main` can become the default branch and whether old merge command can be deprecated. | Explicit acceptance; no implicit default-branch change or release. | Planned |

M3 is the first meaningful proof, not a substitute for M5. Each phase records exact commands, output artifacts, skipped tests and unresolved differences here before being marked complete.

## Release-compatible preset audit

For every supported version, compare its 4.34.0 tag with the generated `released-4.34.0` project. Catalogue changes to JAR entries and contents, registrations, assets, commands, config, network codecs, saves, public Java API and gameplay. Mark each difference as guarded, intentionally retained for compatibility/safety, or unresolved. A source diff or successful compile alone does not prove behaviour parity. Keep a current-feature preset distinct, so the unreleased Touch Display and Client Manager work is not deleted to obtain release compatibility. After the next release, a new immutable released preset becomes the checked-in output baseline.

The old release tags use toolchain lockfile schema v2 while current 1.19.2 development uses v4. Record dependency resolution, wrapper/plugin versions and packaging rules from each tag; source flags alone cannot make a newer build release-equivalent. The existing `sfm-propagate-changes jar compare` command can compare normalized JAR entry names and content hashes, ignoring only the manifest implementation timestamp unless strict mode is selected. Use it as one measure, not as a substitute for gameplay and save compatibility tests.

| Minecraft branch | Commits since its 4.34.0 tag at audit | Gradle/JDK pilot role |
| --- | ---: | --- |
| 1.19.2 | 524 | ForgeGradle, Gradle 7.5, Java 17; first baseline |
| 1.19.4 | 597 | Matrix |
| 1.20 | 448 | Matrix |
| 1.20.1 | 523 | Matrix |
| 1.20.2 | 597 | Matrix |
| 1.20.3 | 670 | Matrix |
| 1.20.4 | 744 | Matrix |
| 1.21.0 | 821 | Matrix; Gradle property and fragments use `1.21`, not branch spelling |
| 1.21.1 | 899 | Intermediate Java 21 / Gradle 8.14.3 gate |
| 26.1.2 | 979 | NeoGradle userdev, Gradle 9.5, Java 25; second pilot boundary |

At the 4.34.0 tag baseline, 548 logical main-Java paths exist across the matrix: 222 are byte-identical in all ten versions, 298 exist in all ten but differ, and 28 are version-specific. This calls for a content-hash inventory and explicit overlays before attempting broad template consolidation. Use resolved `minecraft_version` and loader identity from each generated project's own configuration; do not infer filenames or loader from branch spelling or the mere presence of a property.

Plain Gradle builds the checked-in release projection. Rust build/run may select a development preset in temporary output without a checked-in sync. Until release parity is evidenced, the generated baseline is labelled a candidate rather than advertised as release-compatible.

## Risks and controls

- **Large development delta:** hundreds of commits and a dirty checkout make automated feature inventory unreliable. Review commit families and use tests; do not use a single generated diff as evidence of parity.
- **Generated edits:** projection is lossy. A hash conflict is safer than clobbering a contributor's PR. Reconciliation remains explicit until a trustworthy reverse mapping exists.
- **Gradle independence:** a checked-in generated project may still rely on unavailable artifacts, JDKs or assets. Verify its wrapper in a clean environment, not only through the Rust launcher.
- **Template ambiguity:** every primary Java file enters the scanner, but Java text must remain opaque to Liquid's hardcoded `{{` parser. Two existing files contain double-brace array initializers. Golden tests must prove those bytes and Java text blocks survive unchanged; full-line directives must fail clearly when malformed.
- **Release drift:** compare data formats and registrations, not just tests that happen to pass. Preserve tag-based hotfix paths.
- **Refactoring drift:** current symbol rename/move commands are not implemented. Source analysis must distinguish primary, generated and version overlay ownership before mutating source.
- **Concurrency:** the 1.19.2 working tree and other branches may be active in parallel. Do not clean, commit, rebase or merge another worker's files without ownership review.

## Author decisions recorded on 2026-09-28

| ID | Decision | Resolution |
| --- | --- | --- |
| D1 | Start from committed 1.19.2 development HEAD or release tag? | Development HEAD with release preset; rebase after reviewed dirty-work commits. |
| D2 | Behaviour/API parity or byte-identical JAR? | Behaviour and compatibility parity; seek identical JAR files and contents where reproducible, but slight compiler/packaging differences are acceptable. |
| D3 | Plain Gradle default preset? | Checked-in sources show the most recent released feature set. Development builds use temporary Rust projection; JAR builds and source sync are separate. Multiple feature sets may be manifested concurrently. |
| D4 | How to transfer the dirty 1.19.2 work? | Grouped, reviewable commits first. |
| D5 | What to do with contributor edits to generated files? | Fail closed and backpropagate explicitly. |
| D6 | Canonical template density? | Every Java file is Liquid input and keeps its `.java` filename; use separate implementation files for large version differences. |
| D7 | Unattended goal end point: pilot or all versions? | Full ten-version migration. |

## Progress log

- 2026-09-28: Read-only audits found current 1.19.2 HEAD `7cc64ea9`, extensive uncommitted work, release tag `4.34.0-1.19.2`, existing whole-file feature profiles, hard-coded Java source roots and no implemented reverse refactoring. This exploratory worktree was created without changing the original checkout.
- 2026-09-28: Liquid and Facet source audit found that Cloud-Terrastodon's current `ct pick` uses Serde-backed Liquid conversion, but SFM can build a direct `Facet` → `facet-value` → Liquid adapter. No prototype or build evidence yet.
- 2026-09-28: Liquid's delimiters are hardcoded. Chosen prototype direction is an all-`.java`, full-line directive scanner with opaque Java chunks, so existing Java `{{` is not interpreted as Liquid.
- 2026-09-28: Ten release tags and branch heads audited; all tags are ancestors. Selected 1.19.2 and 26.1.2 as the outer Gradle/JDK pilot, with 1.21.1 as an intermediate gate. Release lockfile/schema and build drift require more than Java flags.
