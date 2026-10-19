# SFM 4.35.0 release projections — local implementation and validation

Status: authored and generated; lightweight regressions and an explicitly approved standalone Gradle visitor build for Minecraft 1.19.2 passed. Datagen execution, gameplay, cold-cache validation, and builds for the other nine targets remain unperformed. Nothing was committed, pushed, tagged, published, or posted to GitHub.

## Catalog and source changes

The new entries copy their corresponding 4.34.0 contexts and add exactly `recipe_namespace` and `release_version_4_35_0`. The first feature fixes recipe identity; the second selects version metadata independently. Both are explicit all-target features with no prerequisites. No development feature is imported into the new releases.

| Exact projection key | Upstream Minecraft | Java toolchain | Gradle wrapper |
| --- | --- | --- | --- |
| `sfm-4.35.0/mc-1.19.2` | 1.19.2 | 17 | 7.5 |
| `sfm-4.35.0/mc-1.19.4` | 1.19.4 | 17 | 7.5 |
| `sfm-4.35.0/mc-1.20` | 1.20 | 17 | 8.1.1 |
| `sfm-4.35.0/mc-1.20.1` | 1.20.1 | 17 | 8.1.1 |
| `sfm-4.35.0/mc-1.20.2` | 1.20.2 | 17 | 8.1.1 |
| `sfm-4.35.0/mc-1.20.3` | 1.20.3 | 17 | 8.1.1 |
| `sfm-4.35.0/mc-1.20.4` | 1.20.4 | 17 | 8.1.1 |
| `sfm-4.35.0/mc-1.21.0` | 1.21 | 21 | 8.8 |
| `sfm-4.35.0/mc-1.21.1` | 1.21.1 | 21 | 8.14.3 |
| `sfm-4.35.0/mc-26.1.2` | 26.1.2 | 25 | 9.5.0 |

`projections.json` supplies context, not implicit behavior or version selection. `project-inputs.json` explicitly selects new property inputs containing `mod_version=4.35.0`. The original property files remain unchanged. Shared datagen and changelog templates use standalone Liquid control lines; the Java renderer intentionally protects Java string literals from template processing.

The namespace fix addresses [#621](https://github.com/TeamDman/SuperFactoryManager/issues/621) and [#527](https://github.com/TeamDman/SuperFactoryManager/issues/527), both observed open during this task. Named recipe saves now use explicit `sfm:` strings, including the two special reset recipes. Default saves already derive SFM-owned registered result IDs and stay unchanged. Printing-press recipes already had explicit SFM IDs and stay unchanged.

Each target through 1.21.1 moves 12 recipe resources and 10 advancement resources from `data/minecraft` to `data/sfm`. The 26.1.2 target moves 15 recipes and 13 advancements, including its three additional conversion recipes. In total: 123 recipe moves and 103 advancement moves. Existing recipe path suffixes and advancement categories stay unchanged; singular/plural directory names follow each target. Recipe JSON bytes stay identical. Only corresponding advancement recipe criteria and rewards change namespace. Vanilla recipe types, item/tag ingredients, triggers, and `minecraft:recipes/root` remain intact. There are no stale recipe/advancement counterparts in the new generated resources. Vanilla `data/minecraft/tags/blocks` resources remain unchanged and packaged.

The new authored inputs include 63 advancement JSON variants and 11 property variants, all referenced by selection metadata. Generated roots contain 11,473 files in total. Per matching old tree, the only other differences are datagen recipe-ID expressions, the 4.35.0 property, the gameplay changelog addition, and one new feature-gated JUnit class. Entire trees were compared, including unchanged build scripts and native lockfiles.

## Compatibility

Datapacks overriding the affected `minecraft:<path>` recipes must move their overrides to `data/sfm` and update references to `sfm:<path>`. Commands, advancement rewards/criteria, and integrations referring to old recipe IDs must also update. Previously unlocked changed recipe IDs may need unlocking again. Ingredients, outputs, and crafting patterns are unchanged. Old 4.34.0 and development projections retain their original resources and IDs. The gameplay changelog contains this warning.

## Completed verification

- Native `source project check` passed sequentially for all 30 entries, with zero created/updated outputs on the final checks. All 20 old contexts still render identical existing outputs.
- Before edits, 29,718 old-family files were hashed, excluding root build/run/.gradle runtime directories. The final path inventory and every SHA-256 matched. Canonical hash-map SHA-256: `1396e884533599afcdd9344371e3696be5918a334a89de30d99fff60f3039c55`.
- All 20 original catalog contexts are unchanged. The existing dirty `platform/cli/sfm-propagate-changes/src/jar_build/projection_selection.rs` hash is unchanged; commits `d45acac6b` and `342a73160` were preserved.
- `python scripts/test_release_recipe_namespaces.py`: five tests passed across all ten targets. They verify namespace/resources, advancement references, explicit feature/version selection, exact datagen namespace-only changes, exact changelog insertion, and whole-tree parity. Last run: 4.064 seconds.
- The two resource-only JUnit methods in `Issue621RecipeNamespaceTests` compiled and ran separately for each target. Each of the ten old resource sets failed both tests as expected; each of the ten new resource sets passed both tests. Test-class compilation used Java 17, `--release 17`, a 192 MiB cap and two active processors. Each test JVM used a 128 MiB cap and two active processors. This compiled only the test class; it does not establish full mod/datagen compilation or validate a Minecraft runtime on any target.
- `git diff --check` passed. The later adapter follow-up edits four owned Rust modules; its full Rust gate remains deferred, as described below. Rustfmt was run only on those owned modules.

Reproduce the lightweight checks from the repository root:

```powershell
python scripts/test_release_recipe_namespaces.py
# Expected failure, documenting the preserved baseline defect:
python scripts/test_release_recipe_namespaces.py --family sfm-4.34.0
sfm-propagate-changes source project check --repo-root . --projection sfm-4.35.0/mc-1.19.2
```

The machine's `python` alias may need replacing with an installed Python executable. These tests use only the standard library. JUnit and Gson were read from existing caches; no dependency was installed or changed.

## Approved standalone visitor validation: Minecraft 1.19.2

The user explicitly approved the one-target Gradle exception after reviewing the scope. The parent handed over the compilation slot before execution. Only ordinary Gradle wrapper/JDK commands were used in an isolated copy of `sfm-4.35.0/mc-1.19.2`: 1,144 copied files matched their source hashes, with no build/run/.gradle output or user configuration copied. PATH contained only JDK 17 and Windows system directories; neither Rust/Cargo nor `sfm-propagate-changes` was available. No captured development dependency profile or native-Rust prebuilt artifact was substituted.

JDK: JetBrains OpenJDK 17.0.6; checked-in Gradle wrapper: 7.5. `GRADLE_USER_HOME` was the existing warm cache at `C:\Users\Teamy\.gradle`. Gradle build-cache reuse was disabled. All invocations used one worker, a 2 GiB Gradle heap and two active processors; the test fork was limited to one worker and 256 MiB. No global Gradle settings or credentials were edited/copied.

The first `clean compileJava compileDatagenJava` invocation failed in `compileJava` after 88.135 seconds. Forge generated its transformed dependency JAR under `build/fg_cache` during configuration; then `clean` removed that directory before compilation, producing thousands of missing Minecraft/Forge-class errors. A new invocation of the same `compileJava compileDatagenJava` tasks recreated the classpath and passed in 111.693 seconds. This required no source patch, dependency change or cache replacement. Run `clean` separately for this Forge visitor path.

- Main and datagen Java compilation: passed. Datagen was compiled, not executed.
- `test --tests ca.teamdman.sfm.test.Issue621RecipeNamespaceTests`: passed; exactly two tests, no failures/errors/skips. The ordinary test source set compiled; other tests were not executed.
- `processResources jar`: passed. The requested task graph included normal grammar/Mixin/resource prerequisites, but no game, datagen run or publication.
- Archive: `Super Factory Manager (SFM)-MC1.19.2-4.35.0.jar`, 1,625,728 bytes; SHA-256 `b4afc3ba59890b5481169f5d48ed8e9753cb1248349218ede7300002c6b6f07f`. `mods.toml` and manifest contain 4.35.0; implementation title is `sfm-1.19.2`. All 837 packaged classes use Java 17 bytecode (major 61).
- All 52 packaged SFM recipe/recipe-advancement resources match declared generated bytes. No Minecraft recipe/recipe-advancement JSON remains; both legitimate vanilla block-tag JSON files match their unchanged sources.

The built `jar` retains named development Minecraft references; the selected scripts do not attach `reobfJar` to `jar`, and the observed task graph did not run reobfuscation. This is verified visitor compilation/resource packaging, not a game-ready or published artifact claim. Forge production reobfuscation requires separate scoped validation; it was not silently added to this exception.

This establishes the visitor path without Rust for this one warm-cache target. It does not establish a fresh machine/cold dependency cache, the other nine targets, gameplay, datagen output, or native CLI build/run readiness. Existing dependency artifacts and Forge's cached transformation inputs were used. The approved experiment also left local compilation outputs from its first failed attempt; the successful retry compiled main/datagen and later test code normally, with Gradle build-cache reuse disabled.

Evidence logs, source-copy hashes, command/limit receipts, JUnit XML and JAR inventory are retained under `C:\Users\Teamy\Documents\Codex\2026-10-09\task-9`. No missing wrapper/source file or Rust prerequisite was found in the copied checkout; external JDK and wrapper/plugin/Minecraft/mod dependencies are still visitor prerequisites.

## Build constraints and next steps

[docs/AGENTS.md](../AGENTS.md) explicitly says “NO GRADLE” for automated work. The user approved only the one-target visitor exception documented above. The broader matrix, cold-cache, datagen and game commands below remain unexecuted handoff steps, not additional build evidence.

The native adapter gap is now addressed in source, with no shared CLI dispatch, crate declaration, dependency configuration, or manifest edits:

- `source_projection/frozen_recipe_project.rs` resolves the unique reviewed dependency recipe from the selected target, upstream Minecraft value, Java release, loader, and exact source-lock path/hash. A projection family name never selects dependencies.
- Its private `frozen_recipe_metadata.rs` helper proves that an alternative selected raw properties input differs from the pinned properties in only the literal `mod_version` value. Every other byte must match, including dependency versions, mappings, comments, whitespace, and line endings. Missing, duplicate, empty, escaped, or malformed version values fail.
- Only the precise reviewed properties role may use that separation. All other authored roles and source-lock provenance retain their existing exact selection requirements. Preparation still verifies the original properties checksum and gives those original bytes to the dependency recipe. The binding records the selected authored input/hash and original dependency-role hash separately. Every recheck repeats the proof and both identities.
- `FrozenRecipeProjectReceipt.project_mod_version` binds the selected metadata and changes the preparation/cache identity when appropriate. This additive receipt field invalidates older preparation identities; it does not alter dependency recipes or generated old projects.
- `jar_build/engine_projection_operation.rs` uses that dependency resolver rather than constructing a recipe ID with a hard-coded release version.
- `jar_build/engine_catalog_release_packaging.rs` requires the plan's mod version to equal the receipt's selected version rather than the literal 4.34.0. Exact loader/JAR dependency admission and existing manifest titles remain unchanged.

The ten 4.35.0 targets reuse their matching original 4.34.0 dependency recipe IDs because their dependencies are unchanged. This is explicit dependency provenance, independent of the new mod version. No old recipe, lockfile, or dependency pin was changed.

Eight pure metadata/recipe-selection Rust tests passed using a small standalone `rustc --test` wrapper, one test thread, and one codegen unit. Nine tests of the actual packaging-policy source passed in an isolated synthetic-receipt harness, including four persistent metadata-policy regressions; its dependency admission is stubbed to the original fixture identity, so this is not a full pipeline test. The pure proof also passed against the checksum-verified original properties and source lock plus old/new generated properties for all twenty release contexts. The real all-feature crate subsequently passed 27 adapter tests (one manual profiling test ignored) and all four packaging metadata regressions, serially. This includes the two new owned-project preparation/recheck fixtures and the eight metadata tests. Their initial run exposed dirty temporary-output setup in the two new fixtures; staging only their declared temporary output bytes corrected the tests without changing production guards. Existing unrelated test-only dead-code warnings remain.

Integrated all-feature type-check/strict lint and focused real-crate tests passed. `cargo clippy --locked --offline --all-features -- -D warnings` passed after the final corrections. The two documentation backticks and function-length findings were fixed with a small role-verification helper extraction; no broad suppression was added. The complete repository-wide `check-all.ps1` gate, installation, and actual native Minecraft compilation remain pending and are not reported as passed. All Cargo work used one build job; focused tests used one test thread within the explicitly handed-over compilation slot. The installed revision remains unchanged and predates this adapter patch. Do not use its native build/run commands as evidence for the new adapter until its gate and authorized installation complete. The approved standalone Gradle visitor commands ran; no Minecraft application or datagen execution command ran.

The one-target, warm-cache visitor exception was approved and completed within its stated task scope. Additional targets, cold-cache resolution, `runData`, game execution, and Forge production reobfuscation remain separate scope decisions.

Exact active instruction text from `docs/AGENTS.md`:

> `## !!!🚫🐘🚫 NO GRADLE 🚫🐘🚫!!!`
>
> Instead of `./gradlew build`, use `sfm-propagate-changes run compile`  
> Instead of `./gradlew runClient_teamy`, use `sfm-propagate-changes run client`  
> Instead of `./gradlew runDatagen`, use `sfm-propagate-changes run data`  
> Instead of `./gradlew runGameTestServer`, use `sfm-propagate-changes game-test run-server`  
> Instead of `./gradlew test`, use `sfm-propagate-changes test run`

The broader per-target commands below document later prerequisites; they do not broaden this proposed one-target exception.

For later standalone validation, use an isolated copy of one generated root containing its sources, resources, Gradle scripts, lockfile, and wrapper, without build/run/.gradle directories. Set the table's matching Java SDK. For a fresh-checkout test, explicitly choose an existing Gradle dependency cache. For a cold-cache test, create a separate empty `GRADLE_USER_HOME`; do not describe a fresh source copy with a warm cache as cold. Network access to the wrapper/plugin/dependency repositories is needed for the latter. Inspect the installed matching SDKs, use the checked-in wrapper, and preserve dependency declarations/lockfiles.

Example in a **separate PowerShell process**, from that isolated generated root:

```powershell
$env:JAVA_HOME = '<matching-existing-sdk>'
$env:GRADLE_USER_HOME = '<chosen-warm-cache-or-new-empty-cold-cache>'
$env:PATH = "$env:JAVA_HOME\bin;$env:SystemRoot\System32;$env:SystemRoot"
if (Get-Command sfm-propagate-changes -ErrorAction SilentlyContinue) {
    throw 'Standalone verification must not resolve sfm-propagate-changes'
}
.\gradlew.bat --no-daemon --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx2G -XX:ActiveProcessorCount=2' clean
.\gradlew.bat --no-daemon --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx2G -XX:ActiveProcessorCount=2' compileJava compileDatagenJava
.\gradlew.bat --no-daemon --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx2G -XX:ActiveProcessorCount=2' test --tests ca.teamdman.sfm.test.Issue621RecipeNamespaceTests
.\gradlew.bat --no-daemon --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx2G -XX:ActiveProcessorCount=2' processResources jar
# 26.1.2 production artifact instead of its slim jar:
.\gradlew.bat --no-daemon --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx2G -XX:ActiveProcessorCount=2' jarJar
# Only after separate datagen-process authorization and scheduling:
.\gradlew.bat --no-daemon --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx2G -XX:ActiveProcessorCount=2' runData
```

Use `jarJar` only for 26.1.2; `gradle/jar-jar.gradle` selects `jar` for the others. All ten versioned run-configuration scripts declare the `data` run, giving `runData`. The 26.1.2 run uses `clientData`, and its ordinary `jar` is classified `slim`. Inspect the production archive's recipes/advancements and mod metadata afterward, then compare datagen output with the declared resources. Never run `runData` in the preserved old roots to validate this fix.

## Operational readiness

- Branch/commit: `main`, `342a73160d95cf8377bba955eeaf592676e53278`, plus this task's uncommitted release changes and the preserved unrelated CLI edit.
- Installed tool: `sfm-propagate-changes` 0.1.1, revision `342a73160`, built 2026-10-09 19:41:47 -04:00. SHA-256: `C73535DF5FD395BAFB06544C9588ADABD6B9D607347204CD646DF782655AC59A`. It handled the native generation/check commands. Its parity with the unrelated dirty CLI edit or the new adapter patch is not claimed. It remains valid evidence for the earlier rendering/check stage only.
- Installer: not run, per task scope. The adapter now changes executable source. Before operational native validation, complete `check-all.ps1 -TestWorkers 1 -TestThreads 1` with bounded Cargo jobs and then the authorized `platform/cli/sfm-propagate-changes/install.ps1` installation and final path/hash/smoke checks. Neither the full gate nor installation is claimed here.
- Dependency posture: frozen. No existing lockfile/dependency declaration changed; new property inputs change only mod_version. No new repository, download, or cache rehydration was required.
- Resource/process state: bounded sequential visitor Gradle compilation/test/packaging plus Rust checks. No Minecraft application, datagen execution, GPU/inference workload, broad process termination or background test service was started.
- Runtime testing remains pending full CLI verification/installation and separate runtime authorization. Source integration and narrow proofs are complete; full native build and gameplay readiness is not certified. The resource-only tests remain immediately repeatable.
