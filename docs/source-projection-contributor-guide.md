# Build and edit a projected Minecraft version

## Use the core Liquid workflow

The authored source is `platform/minecraft/core-liquid-template/`. The catalog is `platform/minecraft/projections.json`. Generated projects live under `platform/minecraft/projections/<projection-key>/`. Java files in the core use Liquid syntax without a `.liquid` extension. Edit these templates, not a release-baseline overlay.

The catalog selects the Minecraft version and fine-grained features explicitly. A key's spelling does not select behavior. For example, `sfm-4.34.0/mc-1.19.2` reproduces the pinned previous release; `sfm-dev/mc-1.19.2` reproduces that version's pinned unpublished development state. Development features differ across versions. They are not all replaced with the 1.19.2 feature set.

From the repository root, use the current installed CLI:

```powershell
sfm-propagate-changes --output-format json source list --repo-root .
sfm-propagate-changes --output-format json source show --repo-root . --projection sfm-dev/mc-1.19.2
sfm-propagate-changes --output-format json source oracle next --repo-root .
sfm-propagate-changes --output-format json source oracle status --repo-root .
```

`next` uses the incremental index. `status` performs the full comparison against pinned Git oracles. Git is comparison evidence, not a production rendering fallback. Use `source render` to inspect a selected template and `source project dry-run` to inspect output changes before writing.

Generate and check one exact catalog project:

```powershell
sfm-propagate-changes --output-format json source project dry-run --repo-root . --projection sfm-dev/mc-1.19.2
sfm-propagate-changes --output-format json source project sync --repo-root . --projection sfm-dev/mc-1.19.2
sfm-propagate-changes --output-format json source project check --repo-root . --projection sfm-dev/mc-1.19.2
```

For contributor changes, review the generated diff and manually apply its intent to the appropriate core templates and selection rules. Render the affected contexts. `sync` fails closed on edited outputs; it does not overwrite them. Only run `source project reconcile --repo-root . --projection <key>` after the core renders exactly the edited output. Reconciliation updates provenance without overwriting contributor content. Never edit the provenance manifest to bypass a conflict. Check both enabled and disabled feature contexts before accepting a change.

Each generated root has ordinary Java, resources, build scripts and a Gradle wrapper. Contributors can use that standalone Gradle project without Rust. Automated work in this repository must follow `docs/AGENTS.md`: use native Rust build tooling rather than executing Gradle. Native `source project compile` and `jar` take exactly one of `--released-recipe <exact-recipe-id>` or `--dependency-profile rust-toolchain`, plus `--java-home <approved-sdk-directory>`. Release recipes retain their original locks. Development builds use the selected project's captured schema-4 profile; they do not borrow a release recipe or fabricate a version branch.

The development native route has passed compilation and packaging for all ten supported Minecraft targets using the validated installed CLI. Older installed executables may lack these routes. The first NeoForm development build exposed a graph-boundary mismatch. Its narrow correction passes retained 1.20.2 and 26.1.2 graph checks, the full v41 Rust gate, fresh installation and all six NeoForm development JAR builds. Consult `source project jar --help` and the active plan before choosing a route. A successful JAR build does not establish a named native application-launch contract.

All 20 required text comparisons and generated-output checks pass. All ten previous-release native JARs pass practical published-JAR inventory checks, and all ten development JARs compile and package. Three representative partial-feature builds, the final Rust gate and CLI installation also pass. The installed CLI's twenty unchanged launches measure 963 ms p95; ten earlier real edit cycles measure 972 ms p95. Explicit content-index initialization remains separate at 4.76 seconds. Text matching does not prove compilation, GameTest execution, gameplay compatibility or publication readiness. Follow the [completed oracle workflow plan](tasks/sfm-core-liquid-oracle-workflow-plan.md) for per-target evidence and operational readiness.

### Build a current projection without Gradle

From the repository root, replace the SDK placeholder with an existing approved Java 17 SDK directory:

```powershell
sfm-propagate-changes --output-format json source project jar --repo-root . --projection sfm-4.34.0/mc-1.19.2 --released-recipe sfm:released-native-inputs/4.34.0/1.19.2@1 --java-home '<approved-java-17-sdk>'
sfm-propagate-changes --output-format json source project jar --repo-root . --projection sfm-dev/mc-1.19.2 --dependency-profile rust-toolchain --java-home '<approved-java-17-sdk>'
```

These are separate alternatives, not two required steps. Release builds check existing generated outputs; synchronize them first if the check reports missing or stale files. Development builds can prepare their declared ignored destination, but still refuse contributor conflicts. Neither command launches Minecraft or runs GameTests. Inspect the returned build receipt rather than treating a preflight receipt as compilation evidence.

### Review one feature at a time

The current feature registry is `platform/minecraft/core-liquid-template/feature-definitions.json`. It declares supported targets and prerequisites. `project-inputs.json` in the same directory controls selected source, resource and build-file membership. Liquid conditions control the contents of selected text files. Disabling a feature must remove its registrations and references as well as its implementation.

1. Add a separate development entry in `platform/minecraft/projections.json`, using a nested key under `sfm-dev/`. Copy the relevant version's context, then change its explicit feature list. Keep the existing 20 oracle contexts unchanged.
2. Run `source show`, `source project dry-run`, `sync` and `check` with the new exact key. Unknown flags, unsupported targets and missing prerequisites fail validation; do not widen prerequisites simply to silence a failure.
3. Compare the generated Java and resource files with the corresponding release or dev-all projection. Review ordinary output, not just Liquid branches. Exclude provenance and build outputs from the code review diff.
4. Compile or package the experimental context through its supported native route. A successful render does not prove that providers, registrations and imports compile together.

Changing the catalog invalidates retained build receipts that bind its full hash. Generated ownership instead binds the exact entry's context: adding a separate entry does not change an existing entry's context identity. Changing an existing entry's feature set cannot be reconciled over its old owner; use a separate key. Do not change the catalog or authored inputs during a running build. Experimental contexts do not add to the 20 required release and dev-all acceptance rows.

For a contributor edit, keep the edited generated file intact. Apply the intended change to its core owner, check the affected feature combinations, and reconcile only when the rendered bytes match. There is no automatic inverse transform from arbitrary Java edits to Liquid templates.

## Standalone Gradle projects

Contributors can build the ordinary generated project under `platform/minecraft/projections/<projection-key>/` with its own Gradle wrapper. Rust is not required to edit or build those generated files. Automated work in this repository must not execute Gradle.

| Minecraft targets | JDK major |
| --- | ---: |
| 1.19.2 through 1.20.4 | 17 |
| 1.21.0 and 1.21.1 | 21 |
| 26.1.2 | 25 |

From the selected generated root, contributors can run `./gradlew jar` or `.\gradlew.bat jar`. Development projections may require `-PsfmProfile=rust-toolchain` for their captured bundled dependencies. The native development builds use that explicit profile; they do not prove compilation with the default Gradle profile.

The twenty-project static audit verifies required scripts, wrapper entry points and literal script references. It does not execute Gradle or prove dynamic dependency resolution. Use the wrapper and profile selected by that exact projection; do not copy build files from another Minecraft version.

## Adoption and historical reference

This work remains on the source-projection feature branch. Native packaging does not authorize publication, changing the default branch or retiring the existing version branches. A future release requires its own review and compatibility evidence.

The manifested release roots are currently untracked working-tree artifacts, eligible for version control; they have not been staged or committed by this goal. Development roots under `sfm-dev/` are ignored. The checked-in-release workflow describes the intended contribution surface after a separately authorized review and commit, not a claim that these outputs are already in Git history.

The [archived contributor workflow](source-projection-contributor-guide-history-through-20261007.md) preserves the earlier frozen imports, overlays, candidate and publication notes without deleting their decisions or evidence. Its old paths, progress counts and owner-selection instructions are historical, not the current generation workflow.
