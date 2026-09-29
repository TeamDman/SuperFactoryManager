# Build and edit a projected Minecraft version

This guide applies to the experimental `feat/sfm-main-source-projection` branch. The generated projects are **4.34.0 release-baseline candidates**. They are not yet approved as release-equivalent builds or a replacement for the current version branches.

## Build with Gradle only

Choose the project under `platform/minecraft/mc-version/<target>`. Each is a standalone Gradle project with ordinary Java sources, resources, build scripts and its own wrapper. You do not need Rust to build it.

| Targets | JDK major version |
| --- | ---: |
| `1.19.2`, `1.19.4`, `1.20`, `1.20.1`, `1.20.2`, `1.20.3`, `1.20.4` | 17 |
| `1.21.0`, `1.21.1` | 21 |
| `26.1.2` | 25 |

From the selected project directory, run `./gradlew jar` on Unix or `.\gradlew.bat jar` on Windows. The first build may download Minecraft, Gradle, loader and plugin artifacts. A later offline build can usually use `--offline` once those inputs are cached. The current 1.21.1 NeoGradle task invalidates and removes its cached client JAR when switching to offline mode, so use an online build for that target until the plugin issue is resolved. `jar` proves compilation and packaging; it is not the same as the production reobfuscated release artifact. Use the version's `reobfJar` task where applicable when comparing release JARs.

The `.sfm-source-projection-manifest.json` in each project names its Minecraft target, feature preset, preset identity and the source and output hashes of every generated file. The checked-in projects use `released-4.34.0`. Do not treat a different local projection as that release preset just because it has the same target version.

## Contribute to generated source

You may edit a generated Java file and submit a normal pull request. The header says it is generated so maintainers know the change must also be brought back to the canonical source or a version-specific overlay. Do not edit the provenance manifest by hand. Ordinary Gradle builds continue to work with your edit.

Before the next generated-source update, a maintainer must:

1. Review the contributor's change. `sfm-propagate-changes source trace --project-root <absolute-generated-project> --file src/main/java/Example.java` reports the file's recorded logical source path, overlay and whether its current bytes still match provenance. For a release-tag fallback, that logical path belongs to the pinned tag snapshot and may not name today's editable canonical file; choose the canonical source or a new version overlay deliberately. Do not edit the pinned tag import. The trace is not an automatic backpropagation tool.
2. Render the same target and preset. `source sync` refuses to overwrite a generated file that has changed since its last recorded hash.
3. Run `source reconcile` only if the newly rendered bytes exactly match the contributor-edited file. Reconciliation updates provenance but never overwrites that file.
4. Run `source check` and a relevant Gradle build before committing the updated source and manifest.

If rendered bytes differ, resolve the authored-source discrepancy manually. There is no automatic inversion of arbitrary Java edits or templates.

If a file has been intentionally removed from the authored source, `source sync` will also stop rather than delete the old generated file. Review the removal, delete that generated file explicitly, then run `source reconcile` to remove its provenance entry. Reconciliation refuses to delete it on your behalf.

## Develop from the canonical source

The canonical tree is `platform/minecraft/src`. Every `.java` file passes through the projection pipeline, even if it contains no Liquid control lines. The current limited template syntax permits whole-line `{% if features.name %}`, `{% elsif ... %}`, `{% else %}` and `{% endif %}` directives. Larger version differences should live in separately selected implementation files. Do not use a runtime boolean to hide code that must be absent from a JAR: Java still compiles both sides of an ordinary `if`.

Use `sfm-propagate-changes source dry-run`, `sync` and `check` with explicit `--repo-root`, `--target`, `--preset` and `--output-root` values. Put development outputs in a separate local root, never over a checked-in `mc-version` project. `current-development-head-<target>` selects the pinned committed development tree for each noncanonical target; the 1.19.2 canonical tree uses `current-development-pilot`. The `current-development-no-echo` and `current-development-no-touch-terminal` presets demonstrate compiling specific unreleased code out of 1.19.2. The 1.19.2 development presets currently need Gradle's `-PsfmProfile=rust-toolchain` profile because the newer canonical Java source imports Vox/Phon classes excluded by its default Gradle profile. This is a development-profile mismatch, not a property of the 4.34.0 release preset.

The integrated `source build` and `source run` commands first project the selected preset, then invoke the projected Gradle wrapper. Both require an absolute `--output-root` **outside the repository**. `source build` defaults to `jar`; `source run` defaults to `runClient` and keeps the output root, including any world saves or settings the client writes. For the current 1.19.2 development pilot, add `--gradle-profile rust-toolchain`. A development build passes a distinct `-dev.<projection-hash>` mod version to Gradle, so its JAR cannot be mistaken for the published 4.34.0 file by name.

For a selected GameTest, set `sfm.gametestSelection` in the game process and run `source run --task runGameTestServer` with the same target, preset and external output root. On 1.19.2 and 1.19.4, `source run` applies the repository's test-only `platform/cli/sfm-propagate-changes/gradle/forge-game-test-no-force-exit.init.gradle` so ForgeGradle can report the passing server's exit normally; no generated or release Gradle file is changed. When invoking those versions' Gradle wrappers directly, pass that file with `--init-script` yourself. The selection Gradle property works on most versions; the pinned 1.20.1 GameTest run does not forward it, so use `JAVA_TOOL_OPTIONS=-Dsfm.gametestSelection=<test-id>` for that version. Test results are evidence for the selected test only, not for the whole GameTest catalog.

Use `sfm-propagate-changes symbol project-list <pattern> --project-root <generated-project>` to query Java declarations in a generated project. The read-only `symbol list`, `symbol show-definition` and `symbol list-usages` commands also accept `--project-root <generated-project>` instead of `--branch <version>`; provide exactly one of those selectors. For schema-v4 lockfiles, the project uses its exact, checksum-verified JDK SDK from the local cache without downloading it; older lockfiles select an installed JDK of the required major version. If the default JDK source is unavailable, project symbols remain queryable and the report includes a warning. Pass `--java-home <jdk-directory>` to select an exact-major JDK explicitly; an invalid explicit selection fails the command. These queries do not acquire branch dependencies or write into the generated project, though JDK source selection may populate a local cache. A selector-targeted lookup can resolve JDK source types and members; exact external types, fields and descriptor-specific methods can resolve from checksum-pinned local dependency JARs. Broader external-member enumeration and inherited-member resolution remain limited, and rename/move commands remain unsupported rather than modifying a generated project.

Do not sync development features into the checked-in release roots during ordinary iteration. A release decision advances those roots to a new, immutable preset identity. The exact 4.34.0 tag imports under `platform/minecraft/release-baselines` are pinned evidence and should not be edited to make a build pass.

## Advancing the checked-in release preset

`source promote --request <reviewed.json>` performs a read-only dry run by default. Its `sfm:source_promotion_request@2` request binds the reviewed HEAD and source definition to all ten candidate roots. It also records each provenance manifest, production JAR hash, Gradle task, JDK identity and one hash-bound compatibility document. The JDK identity is a reviewed assertion; the request cannot prove which Java process ran Gradle. The report lists changed paths and counts. A future release needs a new, immutable preset ID and definition identity. The promotion core stages changes, keeps a recovery journal and checks that the compatibility document is committed unchanged. A new immutable preset can be applied only through the guarded CLI route with `--apply`, `--ack-new-immutable-preset`, an external candidate lock and its independently reviewed SHA-256. The route verifies all ten candidates before staging and again before changing checked-in roots. It has passed synthetic tests but has not been used for a real future release. Do not bypass this boundary by editing a manifest or copying a temporary project over `mc-version`.

The checked-in 4.34.0 roots received one narrow pre-acceptance repair in commit `dffb9369b`. It added the five empty refmaps present in the published JARs for 1.20.2 to 1.21.1 and replaced ten provenance manifests. The guarded Apply used a reviewed external candidate lock and allowed no other file changes. All ten checked-in roots then passed `source check`. Their release-preset identity is `blake3:c72d2eb42418abd568df22ed60a4c233cbd8a3a6c06eaf2448c94a25f84e02b6`. This local repair did not approve the projects as release-equivalent and did not tag, push or publish anything.

`source release-inventory` is a separate read-only step for a future candidate. Give it a portable candidate lock and all ten external candidate roots. It verifies the locked JAR bytes and deterministic source output, then reports relative JAR paths and hashes without including local root paths. It does not promote the checked-in projects, choose a Git tag or call an upload service. Freeze a new lock against the current authored commit before using the report; the lock from the 4.34.0 repair is historical and no longer matches HEAD.

`source release-package` copies only the ten inventory-locked production JARs into a new local output directory and writes its completion manifest last. It does not replace checked-in sources or publish. Independently record the SHA-256 of that `release-package.json` before calling `source release-package-verify --package-root <absolute-package> --completion-manifest-sha256 <reviewed-sha256>`. The read-only `source release-plan` accepts the same two arguments, re-verifies the complete package in that invocation and reports exact target, Minecraft version, loader, filename and JAR hash mappings without local paths. The plan can be based on a historical package: its `package_source_commit` is recorded and `current_head_checked` is false. Neither command chooses Git tags, provider metadata or uploads. Keep the package unchanged while it is verified.

Do not copy a verified package into the legacy shared JAR directory to feed `github`, `modrinth` or `curseforge release now`. Those commands still select branch-era tags and metadata by filename, outside the package's hash-bound inventory. A projection-native provider preflight and separately authorized publisher are still required; the package's `neoforge` category for transitional 1.20.1 is not by itself Modrinth's reviewed dual-loader metadata.

For a future release, freeze candidates from reviewed canonical inputs, build and test all ten in separate roots, and record exact source, JAR and toolchain hashes. Review compatibility before applying promotion, then rerun `source check` and production builds on the checked-in result. Promotion changes source projects only. Tagging, pushing, uploading JARs, changing the default branch and retiring old version branches remain separate decisions. Until this path and its evidence are accepted, use the existing tag-based process for production or emergency work.

## Emergency fixes

The existing version branches and release tags remain available. For an urgent fix that must not include unreleased features, branch from the relevant published release tag, apply only the fix, and validate the normal release path. The source-projection branch does not yet replace that route. Publishing a JAR, making `sfm-main` the default branch, and retiring the old merge workflow each require separate acceptance.
