# No-Gradle Minecraft Toolchain Investigation

This note is the starting map for replacing Gradle in the SFM Minecraft build path. The north star command is:

```powershell
sfm-propagate-changes jar build --branch 1.19.2
```

For `1.19.2`, that command takes `platform/minecraft/src`, generated sources, resources, mappings, Forge userdev inputs, and dependency declarations, then emits the production mod jar under `platform/minecraft/build/libs` without invoking Gradle.

## Current 1.19.2 Inputs

The current Gradle build describes these inputs:

- Minecraft: `1.19.2`
- Forge: `net.minecraftforge:forge:1.19.2-43.4.0`
- Java target: `17`
- Mappings: `parchment:2022.11.27-1.19.2`
- Mod version: `4.33.0`
- Main sources: `platform/minecraft/src/main/java`
- Main resources: `platform/minecraft/src/main/resources` and `platform/minecraft/src/generated/resources`
- Generated ANTLR sources: `platform/minecraft/build/generated-src/antlr/main/ca/teamdman/langs`
- Resource templates: `META-INF/mods.toml` and `pack.mcmeta`
- Access transformer: `src/main/resources/META-INF/accesstransformer.cfg`
- Mixin config/refmap: `sfm.mixins.json` and `sfm.refmap.json`
- Main Java excludes for this version:
  - `ca/teamdman/sfm/common/block/BatteryBlock.java`
  - `ca/teamdman/sfm/common/blockentity/BatteryBlockEntity.java`
  - `ca/teamdman/sfm/common/capabilityprovidermapper/ae2/InterfaceCapabilityProviderMapper.java`

The expected Gradle jar name is:

```text
Super Factory Manager (SFM)-MC1.19.2-4.33.0.jar
```

## What Gradle Is Doing Today

The simple mental model is mostly right: download assets, download jars, apply mappings, apply patches, compile, and jar. The expensive part is that ForgeGradle wraps those steps in several layers of generated Maven artifacts and cache directories.

For this project, Gradle currently does at least the following:

1. Resolves the build plugins from `gradle/plugins/1.19.2/plugin-classpath.txt`.
2. Resolves `net.minecraftforge:forge:1.19.2-43.4.0:userdev`.
3. Resolves MCPConfig `de.oceanlabs.mcp:mcp_config:1.19.2-20220805.130853`.
4. Runs MCPConfig steps for `joined` Minecraft.
5. Builds a Forge userdev compile target mapped to Parchment.
6. Deobfuscates compile/runtime mod dependencies declared through `fg.deobf(...)`.
7. Runs ANTLR 4.9.1 for SFM grammar sources.
8. Runs `javac` with Mixin annotation processing.
9. Expands resource templates.
10. Builds the development jar.
11. Reobfuscates the mod jar from development names back to production names.

The local cache already shows the shape of the target artifacts:

```text
platform/minecraft/build/fg_cache/net/minecraftforge/forge/1.19.2-43.4.0_mapped_parchment_2022.11.27-1.19.2/
  forge-1.19.2-43.4.0_mapped_parchment_2022.11.27-1.19.2-recomp.jar
  forge-1.19.2-43.4.0_mapped_parchment_2022.11.27-1.19.2-sources.jar

platform/minecraft/build/fg_cache/de/oceanlabs/mcp/mcp_config/1.19.2-20220805.130853/
  obf_to_srg.tsrg2
  srg_to_parchment_2022.11.27-1.19.2.tsrg

platform/minecraft/build/classpath/
  runClient_minecraftClasspath.txt
  runClient_runtimeClasspath.txt
```

The original MCPConfig zip is in the Gradle cache:

```text
C:\Users\Teamy\.gradle\caches\forge_gradle\maven_downloader\de\oceanlabs\mcp\mcp_config\1.19.2-20220805.130853\mcp_config-1.19.2-20220805.130853.zip
```

Its `joined` pipeline is:

```text
downloadManifest
downloadJson
downloadClient
downloadServer
extractServer
downloadClientMappings
mergeMappings
stripClient
stripServer
merge
listLibraries
rename
decompile
inject
patch
```

ForgeGradle inserts access-transformer and side-stripper functions before decompile when userdev requires them. Then the Forge userdev layer applies Forge's binary/source patch data and recompiles a mapped Forge/Minecraft jar for the mod project to compile against.

## Snowblower Relationship

Snowblower is useful, but it is not a drop-in replacement for ForgeGradle.

Snowblower covers the vanilla side well:

- version manifest lookup
- client/server jar downloads
- server bundle extraction
- client/server merge
- Mojang mapping merge/remap
- Vineflower decompile
- generated vanilla source output across versions

It does not handle the parts that make a Forge mod build work:

- Forge `userdev` artifacts
- Forge binpatches and injected sources
- Forge access transformers and side-stripper data
- Parchment-on-top-of-SRG remapping for the development classpath
- `fg.deobf(...)` dependency remapping
- Mixin annotation processor mapping/refmap wiring
- final development-to-production reobfuscation

So Snowblower is a good reference for vanilla download/decompile mechanics. ForgeGradle FG5 is the source of truth for the `1.19.2` Forge mod build.

## Research Repos Collected

Reference repositories were cloned under:

```text
G:\Programming\Repos\Minecraft\ToolchainResearch
```

Current inventory:

| Directory | Branch | Commit | Why it matters |
| --- | --- | --- | --- |
| `forgegradle-fg5` | `FG_5.0` | `468dbba` | Source of truth for SFM's current ForgeGradle 5.x behavior. |
| `minecraftforge-1.19.2` | `1.19.2` | `66e4ae5` | Reference for Forge patches, userdev config shape, and published Forge artifacts. |
| `mcpconfig` | `master` | `683c1ee` | MCPConfig formats, steps, mappings, patches, and Mojang-license context. |
| `access-transformers` | `master` | `15fa1e5` | Forge access transformer tool used in MCP/userdev setup. |
| `installer-tools` | `master` | `d83018a` | Tool used by MCPConfig for mapping merge and bundled server extraction. |
| `mergetool` | `master` | `efa0631` | Client/server jar merge implementation. |
| `forgeflower` | `master` | `b94dade` | Decompiler used by MCPConfig for 1.19.2. |
| `snowblower-source` | `main` | `47edc95` | Vanilla version download/remap/decompile reference. |
| `parchment-librarian` | `main` | `4e1c718` | Parchment mapping integration reference. |
| `mixin-gradle-0.7` | `0.7` | `f800b26` | Mixin AP/refmap wiring used with ForgeGradle 3+. |
| `mixin-0.8` | `0.8` | `6f6cee9` | Mixin annotation processor and supported AP options. |
| `moddevgradle-main` | `main` | `846b2d7` | Modern NeoForge Gradle behavior and recompilation-disabled path. |
| `neogradle-ng71` | `NG_7.1` | `f0a4eea` | NeoForge-era Gradle behavior for comparison. |
| `neoform-main` | `main` | `a56dbd0` | Modern NeoForm pipeline, a cleaner successor conceptually. |
| `neoforge-1.21.1` | `1.21.1` | `89dbba3` | Modern NeoForge project structure and patches. |
| `vineflower` | `master` | `b827398` | Current decompiler lineage for comparison. |

## Available Local Assets

The narrow `ToolchainResearch` clones above are still useful because they pin the specific repos and branches that looked most relevant during the first investigation. We also now have broad local mirrors for both upstream organizations:

```text
G:\Programming\Repos\Minecraft\NeoForged
G:\Programming\Repos\Minecraft\MinecraftForge
```

The mirrors were cloned through `gh api /orgs/{org}/repos?type=all`, including archived repos and forks owned by those organizations:

| Mirror | Repo count | Notes |
| --- | ---: | --- |
| `G:\Programming\Repos\Minecraft\NeoForged` | 67 | Includes `NeoForge`, `NeoForm`, `NeoFormRuntime`, `NeoGradle`, `ModDevGradle`, `snowblower`, and related tools. |
| `G:\Programming\Repos\Minecraft\MinecraftForge` | 72 | Includes `MinecraftForge`, `ForgeGradle`, `MCPConfig`, `InstallerTools`, `MergeTool`, `ForgeFlower`, `BinaryPatcher`, `SpecialSource`-adjacent tooling, and legacy tools. |

Additional local reference:

```text
G:\Programming\Repos\PrismLauncher
G:\Programming\Repos\Minecraft\CurseMaven
```

PrismLauncher is useful as a launcher/install/runtime reference, not as a primary code-copy source. Its launcher code is GPL-3.0-only, while this toolchain is planned as Rust code in the SFM CLI. Treat Prism implementation details as reference material unless we deliberately decide to import or port a concrete implementation and carry the required notices.

CurseMaven is useful as the behavioral reference for `curse.maven:<descriptor>-<projectid>:<fileid>` coordinates. Its Maven endpoint validates the Maven path and redirects jar requests through CurseForge's project/file download URL. For SFM's active 1.19.2 dependency set, the coordinates use plain main file IDs without classifier variants.

Current license posture for the org mirrors:

| License bucket | Count | Practical use |
| --- | ---: | --- |
| LGPL-2.1 | 72 | Good for study; copying/linking needs LGPL compliance. |
| MIT | 26 | Low-friction reuse with notices. |
| No license detected | 23 | Do not copy code without manual review or permission. |
| Other/custom | 10 | Manual review required before copying code. |
| Apache-2.0 | 4 | Generally compatible with GPLv3 and permissive use with notices. |
| CC0-1.0 | 2 | Usually fine for non-code metadata/docs, but still preserve context. |
| CC-BY-4.0 | 1 | Attribution required; avoid mixing into code unless clearly appropriate. |
| Unlicense | 1 | Low-friction reuse. |

For now, the safe working rule is: use the cloned Java/C#/C++ repositories as specifications and behavioral references, write original Rust, and add explicit notices whenever we copy, translate closely, or embed meaningful implementation material.

## Discovery And Navigation Tools

Two local tools should be part of this workflow.

`locate-git-projects-on-my-computer.exe` resolves from:

```text
G:\Programming\Caches\CARGO_HOME\bin\locate-git-projects-on-my-computer.exe
```

It locates git projects and emits structured discovery results. Useful examples:

```powershell
locate-git-projects-on-my-computer.exe --name ForgeGradle --url github.com/MinecraftForge
locate-git-projects-on-my-computer.exe --name NeoForm --url github.com/neoforged
locate-git-projects-on-my-computer.exe --author LexManos --activity 3650d
```

`teamy-mft.exe` resolves from:

```text
G:\Programming\Caches\CARGO_HOME\bin\teamy-mft.exe
```

It queries cached filesystem indexes quickly. Prefer it when looking for repo/file locations before falling back to recursive filesystem scans:

```powershell
teamy-mft.exe query --in G:\Programming\Repos\Minecraft "MCPConfig config.json" --limit 50
teamy-mft.exe query --in G:\Programming\Repos\Minecraft "MinecraftUserRepo.java" --limit 20
teamy-mft.exe query --in G:\Programming\Repos\Minecraft "SpecialSource" --limit 50
teamy-mft.exe query --in G:\Programming\Repos\PrismLauncher "metadata instance" --limit 50
```

Use `rg` inside a selected repository once the likely repo/path is known.

## Proposed Toolchain Architecture

The CLI should own a cache model instead of delegating cache decisions to Gradle. A practical layout:

```text
platform/minecraft/build/sfm-toolchain/
  manifests/
  maven/
  minecraft/
  mcp/
  forge/
  mappings/
  dependencies/
  project/
  state/
```

Core components:

- `MavenResolver`: resolve Maven coordinates, POMs, classifiers, dynamic versions like `+`, repository order, and checksums.
- `MinecraftResolver`: resolve Mojang version manifests, client/server jars, libraries, natives, assets, and library OS rules.
- `McpRuntime`: execute MCPConfig JSON steps with input hashing and explicit outputs.
- `ForgeUserdevRuntime`: parse Forge `userdev`, run FG5-equivalent patch/binpatch/remap/recompile behavior, and emit the mapped Forge/Minecraft compile jar.
- `DependencyDeobfuscator`: implement the `fg.deobf(...)` behavior for external mod jars.
- `ProjectCompiler`: generate ANTLR sources, compile Java with the Mixin AP, and write class/resource outputs.
- `JarBuilder`: package resources/classes, generate manifest, and reobfuscate the jar.

Every output should have a small state file recording:

- tool version
- input files and hashes
- Maven coordinates and resolved artifact hashes
- command line used for Java tools
- Java runtime used
- output path and hash

That state is the answer to "why did this rebuild?"

## Superseded Prototype Slice

The first planned implementation slice was deliberately smaller than the clean-slate target: compile and package SFM without invoking Gradle while temporarily reusing already-populated ForgeGradle cache outputs. That prototype mode is now superseded and the live CLI no longer exposes `--use-existing-fg-cache`.

The old prototype would have:

1. Read `platform/minecraft/gradle.properties`.
2. Read `gradle/source-excludes/1.19.2/*.txt`.
3. Find the mapped Forge/Minecraft dev jar in `build/fg_cache`.
4. Read existing classpath files from `build/classpath` as a temporary dependency source.
5. Run ANTLR 4.9.1 with `-visitor` and `-Xexact-output-dir`.
6. Run `javac` with Java 17.
7. Pass Mixin AP options matching MixinGradle:
   - `-AreobfTsrgFile=...`
   - `-AoutTsrgFile=...`
   - `-AoutRefMapFile=...`
   - `-AmappingTypes=tsrg`
   - `-AdefaultObfuscationEnv=searge`
8. Copy resources and expand `META-INF/mods.toml` plus `pack.mcmeta`.
9. Package a development jar with the same manifest attributes Gradle uses.
10. Run SpecialSource with the MCP-to-SRG mapping to reobfuscate the jar in place, matching ForgeGradle's `RenameJarInPlace`.

That slice was useful as an intermediate design checkpoint, but it is no longer the supported path. The live implementation uses the clean-slate Rust-owned planner/build pipeline and rejects ForgeGradle-generated build outputs as inputs.

## Clean-Slate Milestones

The clean-slate implementation removed Gradle assumptions in this order:

1. Generate our own project classpath state instead of reading `build/classpath/*.txt`.
2. Resolve normal Maven dependencies ourselves.
3. Resolve CurseMaven artifacts used in `dependencies.gradle`.
4. Implement `fg.deobf(...)` dependency remapping and cache it by input jar plus mapping hash.
5. Download and execute MCPConfig from scratch.
6. Download and interpret Forge `userdev` from scratch.
7. Build the mapped Forge/Minecraft development jar without Gradle.
8. Generate MCP-to-SRG and SRG-to-Parchment mappings without Gradle.
9. Make clean-slate `jar build --branch 1.19.2` the default mode.
10. Add `--explain-rebuild` to report exactly which input invalidated an output.

## Findings From First Mirror Pass

The exact Forge `1.19.2-43.4.0` userdev artifact already exists in the local Gradle download cache:

```text
C:\Users\Teamy\.gradle\caches\forge_gradle\maven_downloader\net\minecraftforge\forge\1.19.2-43.4.0\forge-1.19.2-43.4.0-userdev.jar
```

Its top-level contract is small enough for the Rust CLI to parse directly:

| Entry | Meaning |
| --- | --- |
| `config.json` | Forge userdev configuration, spec `2`. |
| `joined.lzma` | Binary patch payload consumed by `net.minecraftforge:binarypatcher:1.1.1:fatjar`. |
| `ats/accesstransformer.cfg` | Forge access transformer data. |
| `sas/forge.sas` | Forge side-stripper data. |
| `patches/` | 634 source patch files with `a/` and `b/` prefixes. |

Important `config.json` values:

- MCPConfig: `de.oceanlabs.mcp:mcp_config:1.19.2-20220805.130853@zip`
- Sources artifact: `net.minecraftforge:forge:1.19.2-43.4.0:sources@jar`
- Universal artifact: `net.minecraftforge:forge:1.19.2-43.4.0:universal@jar`
- Binary patcher: `net.minecraftforge:binarypatcher:1.1.1:fatjar`
- Patch path: `patches/`
- Access transformers: `ats/accesstransformer.cfg`
- Side-stripper data: `sas/forge.sas`
- Java/module/runtime library declarations for BootstrapLauncher, ModLauncher, FML, Mixin, ASM, securejarhandler, JarJar, and related Forge runtime pieces.
- Run configs for `client`, `server`, `data`, and `gameTestServer`, all using `cpw.mods.bootstraplauncher.BootstrapLauncher` with userdev launch targets.

ForgeGradle 5's relevant behavior is concentrated in these files from the pinned `FG_5.0` clone:

```text
G:\Programming\Repos\Minecraft\ToolchainResearch\forgegradle-fg5\src\common\java\net\minecraftforge\gradle\common\config\UserdevConfigV1.java
G:\Programming\Repos\Minecraft\ToolchainResearch\forgegradle-fg5\src\common\java\net\minecraftforge\gradle\common\config\UserdevConfigV2.java
G:\Programming\Repos\Minecraft\ToolchainResearch\forgegradle-fg5\src\userdev\java\net\minecraftforge\gradle\userdev\MinecraftUserRepo.java
G:\Programming\Repos\Minecraft\ToolchainResearch\forgegradle-fg5\src\userdev\java\net\minecraftforge\gradle\userdev\util\DependencyRemapper.java
G:\Programming\Repos\Minecraft\ToolchainResearch\forgegradle-fg5\src\userdev\java\net\minecraftforge\gradle\userdev\util\DeobfuscatingRepo.java
G:\Programming\Repos\Minecraft\ToolchainResearch\forgegradle-fg5\src\userdev\java\net\minecraftforge\gradle\userdev\util\Deobfuscator.java
```

The FG5 userdev path is:

1. Resolve `net.minecraftforge:forge:1.19.2-43.4.0:userdev`.
2. Parse `config.json` into `UserdevConfigV2`.
3. Build a parent chain of `Patcher` objects until the MCP parent is reached.
4. Run the MCPConfig `joined` pipeline, with parent access-transformer and side-stripper data injected before decompile.
5. For source artifacts, execute `findDecomp -> findPatched -> findSource -> findRecomp`.
6. For raw binary artifacts, apply Forge binpatches, inject compiled helper classes, apply access transformers, then run the FART remapper/source fixer.
7. For `fg.deobf(...)` dependencies, rewrite dependency versions to append `_mapped_<mappings>`, then remap binary/source jars in `DeobfuscatingRepo`.

The important architectural lesson is that ForgeGradle models this as generated Maven artifacts. Our Rust CLI should model the same work as an explicit artifact graph with named inputs, output paths, hashes, and state files.

Modern `NeoForged\NeoFormRuntime` is not the source of truth for Forge `1.19.2`, but it is a better design reference than ForgeGradle's implicit repository behavior:

- `NeoFormConfig` parses an explicit config with steps, functions, data, libraries, Java version, and encoding.
- `NeoFormEngine` builds an `ExecutionGraph` and exposes named outputs such as `gameJar`, `gameSources`, `clientResources`, and `serverResources`.
- `CacheKeyBuilder` hashes typed inputs and records path annotations, which is close to the rebuild-explanation behavior we want.
- `nfrt run --artifact-manifest ...` is a useful pattern for separating dependency resolution from pipeline execution.

SFM's active `fg.deobf(...)` inventory in `platform/minecraft/gradle/dependencies/1.19.2/dependencies.gradle` is:

| Bucket | Count |
| --- | ---: |
| Active `fg.deobf(...)` declarations | 47 |
| Distinct active coordinates | 38 |
| CurseMaven declarations | 37 |
| Normal Maven declarations | 10 |
| Distinct CurseMaven coordinates | 32 |
| Distinct normal Maven coordinates | 6 |
| `runtimeOnly` declarations | 29 |
| `implementation` declarations | 8 |
| `gametestImplementation` declarations | 8 |
| `compileOnly` declarations | 2 |

That means the clean-slate build needs a deterministic CurseMaven resolver, normal Maven resolver, dynamic-version lock handling for `com.teamcofh:*:1.19.2-10.2.0.+`, and cached remapping for mod dependency jars.

`teamy-mft.exe` is available, but the `G:` index is not currently built. It reported:

```text
Fast query requires G:\Programming\Caches\MFT_FILES\G.mft_search_index.
Run `teamy-mft sync index --drive-pattern G` first.
```

Until that index exists, use `rg` in the selected mirror. Once indexed, `teamy-mft` should become the first stop for repo/file discovery across `G:\Programming\Repos`.

## Refined Next Steps

Before implementing the full clean-slate Forge setup, add a smaller extractor/planner command that proves we can read the real inputs and produce a stable graph:

```powershell
sfm-propagate-changes jar plan --branch 1.19.2
```

The planner should:

1. Locate `forge-1.19.2-43.4.0-userdev.jar`.
2. Parse userdev `config.json`.
3. Locate MCPConfig `1.19.2-20220805.130853`.
4. Emit a JSON graph of MCP, Forge userdev, mapping, dependency-deobf, project-compile, resource, jar, and reobf steps.
5. Include input paths, Maven coordinates, expected outputs, and cache keys, but do not run expensive Java tools yet.
6. Fail clearly when required cache inputs are missing, with the Maven coordinate or file path that would need to be resolved.

Then the practical implementation order became:

1. Build `jar plan --branch 1.19.2`.
2. Replace classpath-file reads with our own dependency resolver and lock/state files.
3. Add cached `fg.deobf(...)` remapping for external mod jars.
4. Add clean MCPConfig execution.
5. Add clean Forge userdev execution.
6. Make clean-slate `jar build --branch 1.19.2` the default path.

That sequence is now implemented for the current clean-slate path described below.

## Current CLI Implementation Status

The first executable slice now exists in `platform/cli/sfm-propagate-changes`:

```powershell
sfm-propagate-changes jar plan --branch 1.19.2
sfm-propagate-changes jar build --branch 1.19.2 --explain-rebuild
sfm-propagate-changes jar compare --branch 1.19.2
```

Implemented behavior:

- Adds `jar plan` and `jar build` under the existing `jar` command namespace.
- Uses SFM-owned paths under `platform/minecraft/build/sfm-toolchain`.
- Reads `gradle.properties` and versioned dependency declarations directly.
- Downloads/parses core clean-slate inputs without using Gradle build outputs:
  - Mojang version manifest and `1.19.2` version JSON
  - Forge `1.19.2-43.4.0:userdev`
  - MCPConfig `1.19.2-20220805.130853`
  - Forge sources/universal, BinaryPatcher, Parchment data, Mixin AP, and ANTLR tool artifacts
- Parses Forge userdev `config.json` for MCP coordinate, binpatcher, patches, ATs, SAS, libraries, modules, and run configs.
- Parses MCPConfig `config.json` for joined steps, functions, data keys, and libraries.
- Classifies all 47 active `fg.deobf(...)` dependencies and resolves dynamic CoFH versions through Maven metadata.
- Writes `build/sfm-toolchain/state/last-plan.json` and optional `--plan-json` output.
- Tracks both final artifact paths:
  - Gradle baseline: `build/libs/Super Factory Manager (SFM)-MC1.19.2-4.33.0.jar`
  - Rust output: `build/libs/Super Factory Manager (SFM)-MC1.19.2-4.33.0-rust.jar`
- Adds `jar compare --branch 1.19.2` for normalized Gradle-vs-Rust jar comparison.
- `jar compare` ignores ZIP timestamps, entry order, compression method, directory entries, and `Implementation-Timestamp` manifest differences by default.
- `jar compare --strict-manifest` includes manifest timestamp differences.
- `jar compare --report-json <path>` writes a structured report with missing, extra, changed, and manifest differences.

Current implementation point:

- `jar plan --branch 1.19.2` succeeds and writes `build/sfm-toolchain/state/last-plan.json`.
- `jar build --branch 1.19.2 --explain-rebuild` now produces the Rust-built jar:

```text
platform/minecraft/build/libs/Super Factory Manager (SFM)-MC1.19.2-4.33.0-rust.jar
```

- The command does not invoke Gradle and does not read `build/fg_cache`, `build/classpath`, `build/classes`, `build/resources`, or `build/tmp/jar` as build inputs.
- `platform/cli/sfm-propagate-changes/check-all.ps1` passes.
- `jar compare --branch 1.19.2` passes against a fresh Gradle baseline jar.

## Verified Baseline And Compare 2026-06-12

The Gradle baseline can be produced separately from:

```powershell
cd D:\Repos\Minecraft\SFM\repos2\1.19.2\platform\minecraft
.\gradlew.bat build
```

Observed result:

```text
BUILD SUCCESSFUL in 2m 46s
```

Then the Rust jar build and compare were verified from:

```powershell
cd D:\Repos\Minecraft\SFM\repos2\1.19.2\platform\cli\sfm-propagate-changes
cargo run -- jar build --branch 1.19.2 --refresh --explain-rebuild
cargo run -- jar compare --branch 1.19.2 --report-json D:\Repos\Minecraft\SFM\repos2\1.19.2\platform\minecraft\build\sfm-toolchain\state\last-compare.json
.\check-all.ps1
```

Observed compare result:

```text
Compared entries: 1053
Gradle entries:   1053
Rust entries:     1053
Missing entries:  0
Extra entries:    0
Changed entries:  0
Manifest changed: no
Jar comparison passed: normalized jars match.
```

The produced artifacts were:

```text
platform/minecraft/build/libs/Super Factory Manager (SFM)-MC1.19.2-4.33.0.jar
platform/minecraft/build/libs/Super Factory Manager (SFM)-MC1.19.2-4.33.0-rust.jar
```

This confirms the `-rust.jar` is a real Rust-orchestrated output that normalizes to the Gradle jar. It is not a repackaged Gradle jar.

The refreshed build was also rerun after tightening artifact resolution so local `.m2` and Gradle module cache fallback is disabled by default. The SFM-owned Maven cache then contained:

```text
artifacts=92 sidecars=92
provenance source counts:
remote-maven=92
local-m2-cache=0
local-gradle-module-cache=0
existing-sfm-cache-unknown=0
```

That means the current 1.19.2 build no longer needs Maven/Gradle-created user cache files as artifact inputs after `--refresh`.

The refreshed build also writes a source-adjacent artifact lockfile:

```text
platform/minecraft/sfm-toolchain.lock.json
```

The lockfile is outside `build/`, so it is visible to git and can be reviewed/committed. It records:

- schema version and Minecraft version
- SFM Maven cache root
- repository list
- all `47` active `fg.deobf(...)` dependency declarations with exact resolved notation
- all `92` Maven artifact cache entries with source, coordinate, repository, URL, relative cache path, optional local fallback path, and SHA-1

Lockfile generation validates every locked artifact against its provenance sidecar before writing. A hash mismatch fails the command instead of updating the lock with bad data.

When `sfm-toolchain.lock.json` exists and `--refresh` is not set, the resolver now consumes and enforces it:

- dynamic dependency declarations such as `com.teamcofh:*:1.19.2-10.2.0.+` resolve to the exact `resolved_notation` recorded in the lockfile
- resolving a Maven artifact not present in the lockfile fails with instructions to use `--refresh` intentionally
- resolved artifact bytes must match the lockfile SHA-1
- `--refresh` bypasses lock enforcement, resolves current remote metadata/artifacts, and rewrites the lockfile

Observed locked dynamic resolutions:

```text
com.teamcofh:cofh_core:1.19.2-10.2.0.+         -> 1.19.2-10.2.0.38
com.teamcofh:thermal_core:1.19.2-10.2.0.+      -> 1.19.2-10.2.0.5
com.teamcofh:thermal_expansion:1.19.2-10.2.0.+ -> 1.19.2-10.2.0.21
```

## Implementation Snapshot 2026-06-12

The clean-slate executor has moved from planner/prototype to first end-to-end Rust-orchestrated jar output.

Public CLI status:

- `jar plan --branch 1.19.2` resolves the graph.
- `jar build --branch 1.19.2 --explain-rebuild` runs execution nodes.
- `jar compare --branch 1.19.2` remains available for normalized Gradle-vs-Rust jar comparison.
- `run client --branch 1.19.2` builds the Rust-owned outputs, then launches Forge's `client` userdev run config.
- `run server --branch 1.19.2` builds the Rust-owned outputs, then launches Forge's `server` userdev run config.
- `run data --branch 1.19.2` builds the Rust-owned outputs, then launches Forge's `data` userdev run config.
- `game-test run-server --branch 1.19.2` builds the Rust-owned outputs, then launches Forge's `gameTestServer` userdev run config.
- `jar plan` and `jar build` now accept `--java-home <path>`.
- `jar plan` and `jar build` now write `platform/minecraft/sfm-toolchain.lock.json`.
- `jar plan` and `jar build` enforce `platform/minecraft/sfm-toolchain.lock.json` unless `--refresh` is supplied.
- Java selection defaults to `JAVA_HOME`, then `java` on `PATH`, and validates Java 17.

Run command status:

- The command names use the CLI's kebab-case style, but map directly to the familiar Gradle/IDEA names: `run client`, `run server`, `run data`, and `game-test run-server`.
- Each run command executes the existing Rust `jar build` path first, so launched code comes from SFM-owned outputs instead of Gradle `build/classes` or `build/resources`.
- Forge userdev `config.json` remains the source of truth for launch target, main class, module path, JVM opens/exports, properties, and environment placeholders.

### Client, test, and puppet commands

- `run client` opens an ordinary interactive Forge client. `run client --smoke` verifies it reaches the title screen and exits. `run client --puppet <selector>` is identical to `puppet run <selector>`.
- `sfm:game_test_orbit_capture` is a reusable eight-angle overview puppet: run `puppet run game_test_orbit_capture --game-test sfm:move_1_stack_direct --branch 1.19.2` to capture one exact GameTest. Its camera derives from the completed test structure's bounds. It complements, rather than replaces, purpose-built walkthrough puppets that interact with blocks or screens.
- `test run` runs JUnit. `game-test run-client` runs the SFM GameTest suite in an integrated client; `game-test run-server` runs it in Forge's headless GameTest server.
- `test list|show`, `game-test list|show`, and `puppet list|show` inspect the selected branch's Java source before a userdev launch. The catalog honors the version's source exclusions and marks `@SFMGameTestGenerator` entries dynamic, because generated children only exist at runtime.
- A puppet selector is statically preflighted before build/userdev. `--keep-open N` holds only its final world for `N`; bare `--keep-open` leaves that final world open. Without it, the puppet returns to the title screen and exits after one second.
- In an IDE normal client, **SFM Dev** on the title screen exposes **Create Dev World** and **Create Dev World + Run GameTests**. These create a new timestamped persistent superflat creative/hard world; they are not puppet automation and do not change cursor, pause, saving, or exit behavior.
- Runtime launch state is written under:

```text
platform/minecraft/build/sfm-toolchain/run/
```

- Working directories match the existing IDEA/Gradle convention:

```text
platform/minecraft/run
platform/minecraft/runServer
platform/minecraft/runData
platform/minecraft/runGameTest
```

- The generated `legacyClassPath.file` points at `build/sfm-toolchain/run/<command>/minecraftClasspath.txt`, not `build/classpath`.
- Minecraft assets are downloaded into SFM-owned cache on demand for runs that need them:

```text
platform/minecraft/build/sfm-toolchain/assets/
  indexes/
  objects/
```

- `MOD_CLASSES` points at `build/sfm-toolchain/project/staged-resources` and `build/sfm-toolchain/project/classes`.
- If future Rust-owned `datagen` or `gametest` class/resource directories exist under `build/sfm-toolchain/project`, the matching run command includes them automatically.
- `run data` filters the known Mouse Tweaks runtime jar from its launch classpath, preserving the existing Gradle note that Mouse Tweaks crashes datagen by touching the Minecraft client during mod init.
- Debug-agent support is intentionally out of scope for this slice; these commands launch normally and leave debugger attachment as a later explicit feature.
- Warm-cache run commands now print build-node progress and Java tool start/finish messages before launching.
- Warm-cache builds reuse the existing Rust-owned MCP joined source jar and Forge dev compile jar unless `--refresh` is supplied, so a normal run command does not immediately redo the expensive MCP/Forge FART/decompile/setup chain.
- `--debug` enables Rust/tracing debug logs, including low-level HTTP client connection pool messages. Normal progress output is clearer without `--debug`.
- Forge's 1.19.2 userdev launch handler expects a legacy classpath entry whose filename contains `forge-<mc>-<forge>` and a separate `client-extra` jar. The Rust launch path now generates both from SFM-owned inputs:

```text
platform/minecraft/build/sfm-toolchain/run/forge-1.19.2-43.4.0-dev-compile.jar
platform/minecraft/build/sfm-toolchain/minecraft/client-extra.jar
```

- The Forge runtime alias jar is built from `forge/1.19.2/classes/dev-compile.jar` plus selected Forge universal manifest metadata. Manifest digest attributes are stripped because the jar contents have been merged/transformed.
- `client-extra.jar` is generated from the vanilla client jar's non-class resources, excluding the vanilla manifest and signature files, with `Minecraft-Dists: server client`.
- Forge coremods also expect `fields.csv` and `methods.csv` through `MCPNamingService`. The Rust launch path generates those from SFM-owned mappings and adds the directory to the launch classpath:

```text
platform/minecraft/build/sfm-toolchain/run/mcp-mappings/
  fields.csv
  methods.csv
```

- Project classes/resources are intentionally not placed on the Java classpath for userdev runs. They are supplied only through `MOD_CLASSES`, avoiding Java module split-package conflicts with Forge dependencies.
- Ctrl+C resume is output/node-level rather than process-checkpoint-level. Completed downloads, Maven artifacts, lockfile entries, MCP outputs, Forge outputs, dependency cache entries, project classes, and jars are reused on the next invocation when present and valid. An interrupted Java tool does not resume midway; if its final output is missing or stale, the owning node reruns. Some cheap run shims, such as the Forge runtime alias and MCP CSV mapping directory, are regenerated each launch to avoid stale runtime state.

Observed successful planner output:

- Java: `C:\Users\Teamy\.jdks\jbrsdk-17.0.6-windows-x64-b829.9\bin\java.exe`
- Resolved artifacts: `18`
- Active `fg.deobf(...)` declarations: `47`
- Graph nodes: `7`
- Rust output path remains `platform/minecraft/build/libs/Super Factory Manager (SFM)-MC1.19.2-4.33.0-rust.jar`.

Observed `run client` status:

- The earlier `Could not find client-extra in classpath` failure was fixed by generating and classpathing `client-extra.jar`.
- The earlier Forge package metadata failure was fixed by using the Forge-named runtime alias jar with Forge universal manifest metadata.
- The earlier Forge coremod SRG field lookup failures were fixed by generating runtime MCP CSV mappings.
- A local `run client --branch 1.19.2` run reached Forge/Minecraft startup with SFM discovered and exited with code `0`.
- Remaining runtime investigation: the dev run still reports a Mixin target lookup warning for `StructureTemplateManagerMixin.onTryLoad`, apparently tied to refmap/runtime mapping behavior. The jar compare path can still be byte-identical while this dev-run mapping path needs follow-up.

Implemented execution foundations:

- `ExecutionContext` writes per-node JSON state under `build/sfm-toolchain/state`.
- Node state records status, Java executable/version, inputs, outputs, and output hashes.
- The hard forbidden-input guard rejects project Gradle outputs:
  - `build/fg_cache`
  - `build/classpath`
  - `build/classes`
  - `build/resources`
  - `build/tmp/jar`
- The executor uses input fingerprints and `*.inputs.sha1` state to reuse ANTLR output, Java compile output, package/reobf output, and several run shims when inputs are unchanged.

Completed MCPConfig joined node:

- Downloads Mojang client jar, server bundle, and client mappings into SFM-owned cache.
- Extracts server jar using InstallerTools.
- Merges MCP and Mojang mappings using InstallerTools `MERGE_MAPPING`.
- Strips client/server jars from MCPConfig class mappings.
- Merges stripped client/server jars using MergeTool.
- Downloads Mojang libraries and writes the ForgeFlower library config file.
- Runs FART rename.
- Runs ForgeFlower decompile.
- Injects MCPConfig `config/inject` sources.
- Applies MCP joined patches with CodeChicken/Forge DiffPatch in `OFFSET` mode.
- Output exists at:

```text
platform/minecraft/build/sfm-toolchain/mcp/1.19.2/joined/patch/joined-patched-sources.jar
```

That jar currently contains `4481` source entries.

Completed Forge userdev first pass:

- Applies Forge userdev source patches using DiffPatch `OFFSET` mode.
- Merges patched Minecraft sources with Forge sources.
- Generates mapping files under:

```text
platform/minecraft/build/sfm-toolchain/forge/1.19.2/mappings/
  obf_to_official.tsrg
  srg_to_official.tsrg
  official_to_srg.tsrg
```

- Remaps combined sources to official names with FART.
- Applies Forge `joined.lzma` binpatches with BinaryPatcher against the MCP named jar.
- Layers the patch-only BinaryPatcher output over the clean MCP named jar and Forge universal jar.
- Runs Forge AccessTransformers with both Forge userdev `ats/accesstransformer.cfg` and SFM's `src/main/resources/META-INF/accesstransformer.cfg`.
- Applies a focused `InnerClasses` access patch for `MultilineTextField$StringView`, which Forge AT leaves protected in the outer class attribute.
- Remaps the Forge/Minecraft dev compile jar to official names with FART.
- Current Forge dev compile output path:

```text
platform/minecraft/build/sfm-toolchain/forge/1.19.2/classes/dev-compile.jar
```

Completed dependency cache first pass:

- CurseMaven URL construction is implemented directly from the Maven coordinate shape documented by the local `G:\Programming\Repos\Minecraft\CurseMaven` clone.
- The hosted CurseMaven endpoint currently resolves SFM's active 1.19.2 CurseMaven jars through normal redirects, so direct unauthenticated resolution is enough for this dependency set.
- Resolver fallback to user-level `.m2` or Gradle module caches is disabled by default.
- `jar plan` and `jar build` now expose `--allow-local-artifact-cache` as an explicit bootstrap escape hatch when a remote artifact cannot be resolved.
- Every Maven artifact downloaded into the common SFM Maven cache now gets a sibling provenance sidecar:

```text
<artifact>.sfm-provenance.json
```

- The sidecar records source kind, coordinate, repository, URL, original local path if a fallback was used, and SHA-1.
- A refreshed build currently produces `92` Maven artifact files and `92` provenance sidecars, all with `source=remote-maven`.
- `jar plan` and `jar build` fold those sidecars into `platform/minecraft/sfm-toolchain.lock.json`.
- Published 1.19.2 Forge mod jars inspected so far appear to already use official-style Minecraft names, so the current dependency node copies jars into SFM's dependency cache as-is, keyed by input hash plus mapping hash.
- Current dependency cache path:

```text
platform/minecraft/build/sfm-toolchain/dependencies
```

Project compiler status:

- ANTLR 4.9.1 execution works when run with its required classpath:
  - `org.antlr:antlr4:4.9.1`
  - `org.antlr:antlr-runtime:3.5.2`
  - `org.antlr:antlr4-runtime:4.9.1`
  - `org.antlr:ST4:4.3`
  - `org.abego.treelayout:org.abego.treelayout.core:1.0.3`
  - `org.glassfish:javax.json:1.0.4`
- Generated sources are written to:

```text
platform/minecraft/build/sfm-toolchain/project/generated-src/antlr/main/ca/teamdman/langs
```

- `javac` starts with Java 17 and Mixin AP.
- Mixin AP loads `official_to_srg.tsrg`, writes `sfm.refmap.json`, and writes `compileJava-mappings.tsrg`.
- Missing annotation jars were added to the planned compile classpath:
  - `org.jetbrains:annotations:24.0.1`
  - `com.google.code.findbugs:jsr305:3.0.2`
- Compile classpath order is preserved instead of alphabetically sorted, so core Minecraft/Forge libraries win before mod dependency jars.
- `javac` now succeeds.

Completed packaging/reobf first pass:

- Stages resources under `build/sfm-toolchain/project/staged-resources`.
- Copies `src/generated/resources` first, then `src/main/resources`, matching Gradle's duplicate-exclude behavior.
- Excludes `.cache`.
- Expands `META-INF/mods.toml` and `pack.mcmeta`.
- Includes generated `sfm.refmap.json`.
- Writes an inspectable development jar at:

```text
platform/minecraft/build/sfm-toolchain/project/dev.jar
```

- Reobfuscates the development jar with SpecialSource, matching ForgeGradle's `RenameJarInPlace` behavior:
  - `--in-jar build/sfm-toolchain/project/dev.jar`
  - `--out-jar build/libs/Super Factory Manager (SFM)-MC1.19.2-4.33.0-rust.jar`
  - `--srg-in build/sfm-toolchain/forge/1.19.2/mappings/official_to_srg.tsrg`
  - `--srg-in build/sfm-toolchain/project/compileJava-mappings.tsrg`
  - `--live`
- Runs SpecialSource with the Rust-resolved compile classpath so inheritance-aware remapping matches ForgeGradle.
- Writes only the final Rust jar to:

```text
platform/minecraft/build/libs/Super Factory Manager (SFM)-MC1.19.2-4.33.0-rust.jar
```

- The Java tool runner now writes Java argfiles before invocation, which avoids Windows command-line length failures when tools need the full compile classpath.
- Parchment parameter data is layered onto MCPConfig TSRG parameter rows while generating SFM-owned mapping files. This is required for local variable table parity in classes that extend Minecraft/Forge types, and was the final difference before compare reached zero changed entries.

Version-aware toolchain status:

- `jar plan` now records a detected loader toolchain:
  - `ForgeGradleForge` for `net.minecraftforge:forge:<mc>-<forge>:userdev`
  - `ForgeGradleNeoForgeGroup` for transitional `net.neoforged:forge:<mc>-<forge>:userdev`
  - `NeoGradleUserdev` for modern `net.neoforged:neoforge:<neo>:userdev`
- `https://maven.neoforged.net/releases` is part of the resolver and `net.neoforged` artifacts prefer it.
- Parchment coordinates support both `YYYY.MM.DD-MC` and `MC-YYYY.MM.DD-targetMC` property formats.
- MCPConfig-declared Java tool versions are now honored. This matters for `1.19.4+`, where MCPConfig moves ForgeFlower from `1.5.605.9` to `2.0.627.2`.
- ForgeGradle-compatible Rust jar builds have been verified for the Forge-era and transitional branches:
  - `1.19.2`
  - `1.19.4`
  - `1.20`
  - `1.20.1`
- `1.20.1` is hosted under `net.neoforged:forge` but still executes through the ForgeGradle-style pipeline.
- Modern `NeoGradleUserdev` branches now execute through the Rust-owned NeoForm/NeoForge path. Current core validation covers `1.20.2`, `1.20.3`, `1.20.4`, `1.21.0`, `1.21.1`, and `26.1.2`.
- Branch `1.21.0` selects worktree `1.21.0`, but its `gradle.properties` uses `minecraft_version=1.21`; the planner now warns and uses the property value for artifact coordinates.
- Source excludes now match both exact Java files and Gradle-style bare directory excludes, which is required for version branches that exclude Mekanism compat sources.

Current multi-version validation snapshot:

1. `jar build --branch core --dry-run --parallel --error-action continue` resolves and prepares every core worktree without invoking Gradle.
2. `game-test run-server --branch core --dry-run --parallel --error-action continue` resolves run setup for every core worktree.
3. `run client --branch core --dry-run --parallel --error-action continue` resolves graphical launch setup for every core worktree.
4. `game-test run-client --branch core --parallel --error-action continue` has passed live across the core worktrees with non-zero SFM game-test counts.
5. `jar audit-artifacts --branch core --parallel --error-action continue --require-portable-artifacts` verifies that locked artifacts are fresh-slate portable.

Immediate next review checklist:

1. Review the pending Rust/docs/lockfile/harness changes in the `1.19.2` worktree.
2. Commit the accepted review set.
3. Run `sfm-propagate-changes.exe git merge` after the oldest-branch commit.
4. Re-run the core dry-run/audit checks after propagation.
5. Keep Gradle baseline compare as the regression oracle until repeated clean builds and smoke tests are uneventful.

## Hard Parts That Shaped The Design

- Forge `userdev` is the center of the problem, not vanilla Minecraft decompilation.
- `fg.deobf(...)` matters because SFM compiles against many mod jars, not just Forge and Minecraft.
- Dynamic Maven versions and CurseMaven file IDs need deterministic lock/state files; refreshed remote resolution now records exact versions and provenance.
- Mixin AP wiring must be exact enough to produce a valid `sfm.refmap.json`; compare checks and live run checks remain the regression guard.
- Reobfuscation must use the same mapping direction as ForgeGradle or the jar will compile but fail at runtime; normalized Gradle-vs-Rust jar compare remains the oracle.
- A "clean slate" build must avoid relying on `C:\Users\Teamy\.gradle\caches`; local Gradle/Maven caches remain only a last-resort bootstrap/debug source behind explicit flags.

## Working Conclusion

Replacing Gradle is feasible, but the unit of work is not "write javac wrapper." The real unit is "own the Forge/NeoForge userdev artifact graph and make rebuild decisions explicit." The current Rust toolchain owns the clean-slate path; the remaining work is review, propagation, repeated validation, and reducing dependency risk by replacing more external Java tool behavior with Rust where it is worth the maintenance cost.
