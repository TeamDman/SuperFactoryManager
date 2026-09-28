# SFM 4.34.0 release baseline inventory

This inventory fixes the source inputs against which an `sfm-main` release-compatible preset must be checked. It is evidence from local Git tags, not a claim that the current development branches or a future generated JAR already match the published release.

- Evidence date: 28 September 2026
- Scope: the 10 `4.34.0-*` version tags and their corresponding version branches
- Status: source inventory complete; 1.19.2 official GitHub artifact identity verified; remaining published artifacts and parity tests pending

The official [GitHub 4.34.0 release](https://github.com/TeamDman/SuperFactoryManager/releases/tag/4.34.0-26.1.2) lists the 1.19.2 asset `Super.Factory.Manager.SFM.-MC1.19.2-4.34.0.jar` (asset ID 466461227, 1,623,810 bytes, SHA-256 `f2c0242a984b8b782cc995e20a59e71bbdb48981873ab31a4531f0d1db3af038`). A local reference JAR has exactly that size and hash, so it is byte-identical to the GitHub asset by digest. The [CurseForge file page](https://www.curseforge.com/minecraft/mc-mods/super-factory-manager/files/8370233) confirms the separate 1.19.2 Forge publication but does not expose a hash, so byte identity with the CurseForge upload remains unverified. No asset was downloaded for this audit.

## Tagged build inputs

Each SHA is the commit resolved by `git rev-parse "<tag>^{commit}"`. The Gradle and Java values come from the wrapper and `gradle/java-toolchain/<minecraft_version>/java-toolchain.gradle` in that same tag. The `neo_version` column quotes the Gradle property; it does not by itself identify whether the project applies ForgeGradle or NeoGradle.

| Version branch and tag suffix | Exact tag commit | `minecraft_version` | Gradle wrapper | Java major | `neo_version` | Plugin set |
| --- | --- | --- | --- | --- | --- | --- |
| `1.19.2` | `31135b8e86801b862d5cb2283c7c5878b7cc5bb4` | `1.19.2` | 7.5 | 17 | `43.4.0` | FG5 |
| `1.19.4` | `23785b63e3e1fe35e5be6a6d89b6638ee0ff0daa` | `1.19.4` | 7.5 | 17 | `45.0.9` | FG5 |
| `1.20` | `3df18123a19535fd0e5d1dc81aa302105c3fd2f6` | `1.20` | 8.1.1 | 17 | `46.0.10` | FG6 |
| `1.20.1` | `bb5babf12f467235b3a44ad5098666ee3ed171ec` | `1.20.1` | 8.1.1 | 17 | `47.1.65` | NG6 |
| `1.20.2` | `cfbbafaeda4a006ae32743a92de330711983056b` | `1.20.2` | 8.1.1 | 17 | `20.2.86` | NG7.0.57 |
| `1.20.3` | `1b7f9605da0ef13c7601daf3786545868dfc3c78` | `1.20.3` | 8.1.1 | 17 | `20.3.8-beta` | NG7.0.57 |
| `1.20.4` | `a637581b5e1078d7cc0ca68333add568e3e387ff` | `1.20.4` | 8.1.1 | 17 | `20.4.231` | NG7.0.57 |
| `1.21.0` | `6bfab8a21e7a5bbeb0bb136bf4e5b26f6ce03e25` | `1.21` | 8.8 | 21 | `21.0.143` | NG7.0.145 |
| `1.21.1` | `f5366c79c823ff52712130e69dd9c8166c70bd14` | `1.21.1` | 8.14.3 | 21 | `21.1.206` | NG7.0.192 |
| `26.1.2` | `fe32b29453b13b4f3050ad441677c7eb79e80814` | `26.1.2` | 9.5.0 | 25 | `26.1.2.72` | NG7.1.27 |

Plugin sets above refer to the tagged `gradle/plugins/<minecraft_version>/plugin-classpath.txt` and `plugins.gradle` files:

| Set | Applied loader plugin and marker coordinate | Other applied external plugins |
| --- | --- | --- |
| FG5 | `net.minecraftforge.gradle`; `net.minecraftforge.gradle:net.minecraftforge.gradle.gradle.plugin:5.1.+` | Parchment ForgeGradle `1.+`, Sponge Mixin `0.7.+` |
| FG6 | `net.minecraftforge.gradle`; `net.minecraftforge.gradle:net.minecraftforge.gradle.gradle.plugin:[6.0,6.2)` | Parchment ForgeGradle `1.+`, Sponge Mixin `0.7.+` |
| NG6 | `net.neoforged.gradle`; `net.neoforged.gradle:net.neoforged.gradle.gradle.plugin:[6.0.18,6.2)` | Parchment ForgeGradle `1.+`, Sponge Mixin `0.7.+` |
| NG7.0.57 | `net.neoforged.gradle.userdev`; `net.neoforged.gradle.userdev:net.neoforged.gradle.userdev.gradle.plugin:7.0.57` | none in the tagged marker list |
| NG7.0.145 | `net.neoforged.gradle.userdev`; same marker ending `:7.0.145` | none in the tagged marker list |
| NG7.0.192 | `net.neoforged.gradle.userdev`; same marker ending `:7.0.192` | none in the tagged marker list |
| NG7.1.27 | `net.neoforged.gradle.userdev`; same marker ending `:7.1.27` | none in the tagged marker list |

Where listed, the other marker coordinates are `org.parchmentmc.librarian.forgegradle:org.parchmentmc.librarian.forgegradle.gradle.plugin:1.+` and `org.spongepowered.mixin:org.spongepowered.mixin.gradle.plugin:0.7.+`. The tagged `settings.gradle` also applies `org.gradle.toolchains.foojay-resolver-convention`: absent for 1.19.2 and 1.19.4, version 0.5.0 for 1.20 through 1.21.0, and version 0.9.0 for 1.21.1 and 26.1.2.

All 10 tags set `mod_version=4.34.0` and contain a version-specific `platform/minecraft/sfm-toolchain.lock.json` with `schema_version=2`. The current 1.19.2 development lockfile instead uses schema 4, including feature and profile fields. Do not assume that a current lockfile is an interchangeable release input. The 1.21.0 branch and tag deliberately use `minecraft_version=1.21` in the Gradle property and versioned Gradle path.

## Tracked source overlap at the release tags

The comparison below uses tracked blobs from `git ls-tree -r 4.34.0-<version> -- platform/minecraft/src`. “Shared path” means the same repository-relative path exists at the 1.19.2 tag. “Same blob” also requires identical Git blob IDs. Main Java counts cover only `platform/minecraft/src/main/java/**/*.java`; all-source counts also include gametests, tests, datagen, resources and checked-in generated files.

| Version | All `src` files | Paths shared with 1.19.2 | Same blobs as 1.19.2 | Main Java files | Shared main Java paths | Same main Java blobs |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| `1.19.2` | 995 | 995 | 995 | 526 | 526 | 526 |
| `1.19.4` | 995 | 971 | 889 | 526 | 526 | 487 |
| `1.20` | 995 | 971 | 854 | 526 | 526 | 470 |
| `1.20.1` | 997 | 971 | 857 | 528 | 526 | 471 |
| `1.20.2` | 997 | 966 | 739 | 528 | 521 | 375 |
| `1.20.3` | 997 | 965 | 725 | 528 | 520 | 362 |
| `1.20.4` | 997 | 968 | 721 | 528 | 524 | 361 |
| `1.21.0` | 999 | 917 | 639 | 530 | 524 | 303 |
| `1.21.1` | 1000 | 917 | 634 | 531 | 524 | 301 |
| `26.1.2` | 1008 | 892 | 454 | 533 | 518 | 220 |

Across all 10 tags, 892 `src` paths and 518 main Java paths are common. Only 453 `src` files and 220 main Java files have identical blobs across all tags. These numbers show where a common source tree could start; they do not prove that a changed file needs a version-specific template. Formatting, names and generated outputs can differ without changing behaviour.

Every release tag is an ancestor of its corresponding current version branch. At this snapshot, those branches contain 448 to 979 commits after their tags. The 1.19.2 branch contains 532. A release preset based on current development source therefore needs explicit feature and compatibility analysis; it cannot be inferred from tag ancestry alone.

## Evidence still needed for release parity

Before calling any generated preset release-compatible, capture these inputs for each version:

1. The published 4.34.0 JAR, its source URL and SHA-256, its entry names and entry-content hashes, and its mod metadata and manifest. Keep the published artifact separate from locally rebuilt tag output.
2. The tag's Gradle wrapper URL and distribution hash, resolved plugin and dependency artifacts, mapping inputs, exact JDK vendor and build, and versioned lockfile. The marker selectors containing `+` or ranges do not identify one immutable plugin artifact.
3. The release tag's generated resources, datagen inputs, source-set exclusions and build task settings. Record which inputs are checked in and which are build outputs.
4. A comparison of the preset JAR with the published JAR: mod metadata, class/resource entry coverage and contents, public API and serialization formats, followed by focused runtime tests for persisted worlds and gameplay. Record every justified difference.
5. A plain Gradle build from the checked-in generated source root for each version, without requiring the Rust CLI. Run development-only feature sets through a separate generated or ephemeral output root so contributor-facing release sources stay stable.

Byte-for-byte JAR equality is an aspiration, not the acceptance rule. At least the tagged `gradle/jar-manifest.gradle` adds an `Implementation-Timestamp` at build time. Archive ordering, compiler/JDK changes, datagen and dynamic plugin resolution can also alter bytes. Content and behaviour differences must still be explained; a successful compile alone is not parity evidence.

The planned `platform/minecraft/mc-version/<version>` roots cannot simply inherit the current Gradle scripts unchanged. The tagged `build.gradle` resolves `gradle.properties` and versioned plugin files relative to its own project directory, while `settings.gradle` derives a project name from parent directories. Generated roots need a tested, self-contained Gradle layout or deliberate path adaptation.
