# Vox Java Jar-in-Jar packaging research

Research snapshot: 2026-08-01. This document retains the original research and
records the current implementation/validation status below.

This report is the implementation-local result of Track 5 packaging agent P1.
It supplements `vox-java-phon-and-generated-packets.md`. It does not change the
canonical task plan or any production ANTLR/Vox dependency declaration.

## Outcome

Loader-native Jar-in-Jar is the preferred way to ship a small, pure-Java
Phon/Vox runtime. All four investigated version families have a loader and
build plugin capable of consuming and producing the same
`META-INF/jarjar/metadata.json` layout:

| Minecraft | Loader/build plugin in SFM | JarJar implementation | Task support | SFM full-artifact verification status |
| --- | --- | --- | --- | --- |
| 1.19.2 | Forge 43.4.0 / ForgeGradle 5.1.77 | Forge JarJar 0.3.16 | Yes | Rust and Gradle full artifacts verified through the SFM-managed local Maven cache |
| 1.20.4 | NeoForge 20.4.231 / NeoGradle 7.0.57 | NeoForge JarJar 0.4.0 | Yes | No |
| 1.21.1 | NeoForge 21.1.206 / NeoGradle 7.0.192 | NeoForge JarJar 0.4.1 | Yes | No |
| 26.1.2 | NeoForge 26.1.2.72 / NeoGradle 7.1.27 | NeoForge JarJar 0.5.0 | Yes | Yes |

The remaining blockers are in SFM's source-artifact and compatibility plumbing,
not an absence of loader support:

1. The 1.19.2 lock now projects Vox to `jarJar` with an explicit bounded range,
   and the Rust builder emits the nested JAR for both ForgeGradle and NeoGradle.
2. The current Vox bytes came from two local Facet `main` commits newer than
   the locked published revision; those commits must become reachable from the
   locked remote or the artifact must be published.
3. The Vox coordinate is not currently available from the configured remote
   Maven repositories. Legacy Gradle now resolves the materialized artifact
   through a self-discovered SFM-managed local Maven repository, but a clean
   external build still needs a publication or source-build materialization.
4. A portable Rust release build must be rerun after the source revision and
   cache provenance agree; an explicit local artifact build is useful proof of
   packaging but is not portable-release proof.

No source vendoring is needed to begin. Native JarJar should be implemented and
proved first. Shading remains a fallback for a dependency that cannot safely
share a common classloader namespace; it is not needed merely because 1.19.2
uses Forge.

## Repository evidence

### Schema-v3 dependency projection

`platform/minecraft/gradle/dependencies-from-lock.gradle` maps scopes as
follows:

- `compile` plus `runtime` becomes `implementation`;
- `compile` alone becomes `compileOnly`;
- `runtime` alone becomes `runtimeOnly`; and
- `bundle` becomes `jarJar`.

A runtime library used directly by SFM should therefore declare all of
`compile`, `runtime` and `bundle`. `bundle` by itself expresses packaging, not
the complete compile/runtime API contract. The 26.1.2 ANTLR component currently
uses only `bundle`; it is evidence that JarJar can package a library, not the
recommended declaration for Vox.

The old generated 26.1.2 Gradle declaration was:

```groovy
jarJar(implementation('org.antlr:antlr4-runtime:4.13.1')) {
    version {
        strictly '[4.13.1]'
    }
}
```

That construct simultaneously placed ANTLR on the development classpath,
enabled the JarJar task and supplied an explicit accepted range. The schema-v3
projection separates `implementation` and `jarJar`, which is reasonable, but
it presently loses the explicit range.

### ForgeGradle 5 on 1.19.2

The locally retained ForgeGradle source is at
`G:\Programming\Repos\Minecraft\ToolchainResearch\forgegradle-fg5`.

`UserDevPlugin.configureJarJarTask` creates:

- a `jarJar` configuration;
- a disabled `jarJar` task with default classifier `all`; and
- a JarJar extension that enables the task when configured.

The plugin also creates `reobfJarJar`, guarded by whether `jarJar` is enabled.
Thus ForgeGradle has a supported reobfuscation path for the project classes in
the bundled artifact. A plain Java library is recorded as
`isObfuscated: false` and is not remapped.

`JarJar.createDependencyMetadata` requires a restricted Maven range. Its
validation rejects Gradle selectors containing `+` and rejects a lone
recommended version. The SFM projection must therefore set an attribute/range
through the plugin's `jarJar.ranged(...)` or equivalent dependency constraint;
adding `jarJar 'group:artifact:1.2.3'` is insufficient on this version.

Before Vox was projected to `jarJar`, the generated file
`repos2/1.19.2/platform/minecraft/build/jarjar/jarJar/metadata.json` contained:

```json
{
  "jars": []
}
```

Its presence is artifact evidence that the task exists and has executed in the
SFM project. The current Gradle run reaches the packaging step through the
self-discovered SFM-managed Maven cache. The local repository is conditional on
the cache existing and does not alter the published artifact's dependency
metadata.

### NeoGradle on 1.20.4, 1.21.1 and 26.1.2

The locally retained NeoGradle 7.1 source is at
`G:\Programming\Repos\Minecraft\ToolchainResearch\neogradle-ng71`.
`DefaultJarJarFeature` creates a resolvable, non-consumable, non-transitive
`jarJar` configuration and a disabled `jarJar` task. Adding a dependency
enables the feature, makes `assemble` depend on it and substitutes the JarJar
artifact into `runtimeElements`.

The task copies the ordinary project JAR and nested libraries, writes
`META-INF/jarjar/metadata.json`, and defaults to an `all` classifier until the
project overrides it. If no explicit range attribute exists, NeoGradle derives
an open range beginning at the selected version. SFM should not rely on that
default; an accepted compatibility range is release policy and must be locked.

The existing
`repos2/1.20.4/platform/minecraft/build/jarjar/jarJar/metadata.json` is also an
empty successful task artifact. The 1.21.1 plugin sources expose the same
feature even though that worktree currently has no retained task output.

### Selected Gradle artifact

`platform/minecraft/gradle/jar-jar.gradle` now selects the bundled artifact when
the projected dependency set contains a `jarJar` entry, independent of the
Minecraft version. It assigns:

- classifier `slim` to `jar`;
- an empty classifier to `jarJar`; and
- `sfmPublishedJarTask = jarJar`.

Selection should be based on whether the schema-v3 projection contains a
`jarJar` dependency, not a hard-coded Minecraft version. The ordinary JAR may
retain a `slim` classifier for diagnostics, while the unclassified release
artifact must be the nested one.

### Gradle local Maven discovery

`gradle/repositories.gradle` discovers the SFM-managed Maven cache without a
property supplied by the Rust harness. It honors the CLI's
`SFM_PROPAGATE_CHANGES_CACHE` override, then mirrors `directories_next`'s
platform cache locations and checks:

```text
<cache-root>/minecraft-toolchain/maven
```

When that directory exists it is added as `sfm-local` before the locked remote
repositories. The repository uses `metadataSources { artifact() }` because SFM
stores the content-addressed JAR and provenance sidecar rather than a Maven
POM. If the cache is absent, Gradle's normal locked remote repository behavior
is unchanged.

### Rust-built artifact

`add_loader_jarjar_entries` in
`platform/cli/sfm-propagate-changes/src/jar_build/engine_execute.rs` writes:

```text
META-INF/jarjar/<artifact filename>
META-INF/jarjar/metadata.json
```

for every projected `jarJar` dependency on both `ForgeGradleForge` and
`NeoGradleUserdev`. The deterministic Rust tests and the 2026-08-01 full
artifact inspection cover the Forge path.

The metadata shape written by the Rust builder matches the loader format and
now consumes the locked policy. The 2026-08-01 artifact contains:

```text
range = [resolved-version]
artifactVersion = resolved-version
isObfuscated = false
```

Exact ranges are safe for a first unique Phon/Vox coordinate, but they prevent
JarSelector from sharing another compatible version. The range must eventually
come from a reviewed lockfile declaration. `isObfuscated: false` is correct for
a pure Java Phon/Vox library with no Minecraft-mapped symbols.

### Positive 26.1.2 artifact

The retained Gradle artifact
`Super Factory Manager (SFM)-MC26.1.2-4.33.0.jar` is 1,925,681 bytes with
SHA-256:

```text
25B16A6852C1FE37C3667F8789F0F5221FC10E6B9DA47CD0F52988B0DDAAA74C
```

It contains:

```text
META-INF/jarjar/antlr4-runtime-4.13.1.jar  326305 bytes
META-INF/jarjar/metadata.json                 326 bytes
```

The nested JAR has SHA-256:

```text
54665D2838CC66458343468EFC539E454FC95B46A8A04B13C6AC43FC9BE63505
```

which is byte-identical to the locked ANTLR artifact. The sibling slim artifact
is 1,625,809 bytes and has no `META-INF/jarjar` library.

The Rust-built 26.1.2 4.34.0 artifact also contains the same nested ANTLR JAR
and metadata. This proves that Gradle and Rust packaging both implement the
positive NeoGradle case.

The retained 26.1.2 userdev log discovers
`JarInJarDependencyLocator` and reports dependency selection, but the userdev
classpath also supplies loader libraries independently. It is not accepted as
a clean-install runtime proof for the nested ANTLR bytes. Final Vox acceptance
must launch the produced release JAR in an isolated instance.

### Positive 1.19.2 Rust artifact — 2026-08-01

The Rust full artifact
`Super Factory Manager (SFM)-MC1.19.2-4.34.0-rust.jar` was built through
`sfm-propagate-changes.exe jar build` with the explicit Facet/Vox artifact
source. It is 2,926,764 bytes and contains:

```text
META-INF/jarjar/vox-java-0.10.0-rc.5.jar  403625 bytes
META-INF/jarjar/metadata.json
```

The nested JAR SHA-256 is
`2d0a45e8339be3229fe657c465470dfa10df34d040b43f6cd2cd5e6360419007`, exactly
matching the source-built Vox JAR. The metadata records the `org.facet:vox-java`
identifier, range `[0.10.0-rc.5]`, artifact version `0.10.0-rc.5`, and
`isObfuscated: false`. This proves the Rust packaging bytes and metadata; it is
not yet clean-install or portable-provenance proof because the current Facet
commits are local and the Maven coordinate is not published in the configured
repositories.

### Positive 1.19.2 Gradle artifact — 2026-08-01

The legacy ForgeGradle `jarJar` task was run through
`sfm-propagate-changes.exe gradle run --branch 1.19.2 --show-logs jarJar`, with
no Gradle property supplied by the harness. It completed `:jarJar` and
`:reobfJarJar` successfully after Gradle discovered the SFM-managed local Maven
cache. The resulting artifact
`Super Factory Manager (SFM)-MC1.19.2-4.34.0.jar` is 2,948,745 bytes and
contains:

```text
META-INF/jarjar/vox-java-0.10.0-rc.5.jar  403625 bytes
META-INF/jarjar/metadata.json
```

The nested Vox SHA-256 is the same as the Rust-built artifact. This proves the
Gradle compatibility path using materialized local bytes; it does not replace
the clean-checkout source-build or public Maven publication gate.

## Loader and conflict behavior

Forge 1.19.2 registers `JarInJarDependencyLocator` as an
`IDependencyLocator`. It asks `JarSelector.detectAndSelect` to inspect every
loaded mod's metadata, resolves one artifact version satisfying the requested
ranges, opens the nested JAR through the `jij:` filesystem and adds a plain
library as a `GAMELIBRARY`. NeoForge retains the equivalent locator and nested
library reader.

Consequences:

- Native JarJar does not relocate packages.
- Repeated `group:artifact` identifiers participate in version selection
  instead of loading arbitrary duplicate copies.
- Incompatible requested ranges fail loading rather than silently choosing an
  unsafe version.
- Different coordinates that nevertheless contain the same Java packages or
  module names can still conflict in the common game layer.
- An exact range maximizes reproducibility but prevents compatible deduplication.
- An open range maximizes deduplication but can accept an API-breaking future
  version unless the library follows and SFM records a trustworthy
  compatibility policy.

The initial Vox artifact should therefore:

- use unique, stable Maven coordinates and Java package names;
- have an `Automatic-Module-Name`;
- contain no Minecraft-mapped classes;
- minimize or eliminate third-party runtime dependencies;
- publish a reviewed bounded compatibility range;
- bound all wire input independently of packaging; and
- be tested when another mod supplies the same artifact at a compatible and an
  incompatible version.

Shading/relocation is appropriate if Vox acquires a transitive dependency whose
packages collide with Minecraft or common mods, or if a target loader's clean
launch fails despite valid metadata. It should not be the default: relocation
obscures upstream provenance and complicates generated bindings. Vendoring the
runtime source is the least desirable fallback.

## Required implementation changes

### 1. Extend schema v3 packaging policy

Add reviewed component fields equivalent to:

```json
{
  "scopes": ["compile", "runtime", "bundle"],
  "bundle": {
    "accepted_version_range": "[0.1.0,0.2.0)",
    "artifact_version": "0.1.3",
    "is_obfuscated": false
  }
}
```

The exact schema spelling should be designed with the dependency-lock owner.
The essential distinction is between the exact bytes selected and the range
that the loader may share with other enclosing mods.

Validation must require:

- `bundle` policy only when the `bundle` scope is present;
- a Maven-compatible restricted range;
- the selected version to lie within the range;
- `is_obfuscated: false` for the initial pure Java Vox artifact; and
- a unique nested output path for each selected coordinate.

### 2. Fix Gradle projection and output selection

The Gradle projection must attach the locked range in both ForgeGradle 5 and
NeoGradle 7 forms. `jar-jar.gradle` must select the enabled/bundled task based
on projected bundle presence rather than Minecraft version.

ForgeGradle's `reobfJarJar` path must be included in the acceptance evidence.
For NeoGradle, the JarJar task copies the normal project JAR, so the normal
NeoGradle remapping/publication behavior must be compared against the produced
artifact rather than inferred from the task name.

### 3. Give the Rust builder Forge parity

Generalize `add_neogradle_jarjar_entries` to loader-native JarJar packaging for
both `ForgeGradleForge` and `NeoGradleUserdev`. Include bundle inputs in the
package fingerprint on both families and consume locked range/obfuscation
policy instead of synthesizing it.

Add unit tests for:

- Forge and Neo metadata generation;
- deterministic coordinate ordering;
- exact selected bytes;
- version/range mismatch rejection;
- duplicate nested filename rejection; and
- no metadata when no bundle exists.

### 4. Add artifact and clean-loader acceptance

For every target version, inspect both slim and release artifacts and assert:

- release contains the expected nested JAR exactly once;
- metadata path, identifier, range, artifact version and obfuscation flag match
  the lock;
- nested SHA-256 equals the locked artifact;
- slim omits the nested JAR;
- the artifact selected by collection/publication is the release artifact; and
- Gradle and Rust artifacts agree after accepted manifest normalization.

Then launch a clean client or server with only the loader and SFM release JAR.
A proving SFM class must load and invoke a method from the nested probe/Vox
library. A mere successful build or a userdev launch is insufficient.

## Reproducible commands

All project operations must go through `sfm-propagate-changes.exe`; do not call
Gradle directly.

Inspect the clean-slate plan and projected `jarJar` dependencies:

```powershell
sfm-propagate-changes.exe jar plan --branch 1.19.2 --plan-json "$env:TEMP\sfm-jij-1.19.2.json" --dry-run
sfm-propagate-changes.exe jar plan --branch 1.20.4 --plan-json "$env:TEMP\sfm-jij-1.20.4.json" --dry-run
sfm-propagate-changes.exe jar plan --branch 1.21.1 --plan-json "$env:TEMP\sfm-jij-1.21.1.json" --dry-run
sfm-propagate-changes.exe jar plan --branch 26.1.2 --plan-json "$env:TEMP\sfm-jij-26.1.2.json" --dry-run
```

After the schema/projection fixes, add the eventual Vox artifact to an isolated
feature worktree:

```powershell
sfm-propagate-changes.exe dependency add vox-java-runtime `
  --branch feat/1.19.2/vox-packaging-probe `
  --maven <group>:<artifact>:<exact-version> `
  --kind library `
  --role library `
  --scope compile `
  --scope runtime `
  --scope bundle `
  --artifact-treatment plain
```

The future CLI must also accept the reviewed bundle range; do not hand-edit it
after acquisition.

Build, verify provenance and compare packaging:

```powershell
sfm-propagate-changes.exe jar audit-artifacts --branch feat/1.19.2/vox-packaging-probe --require-portable-artifacts
sfm-propagate-changes.exe run compile --branch feat/1.19.2/vox-packaging-probe
sfm-propagate-changes.exe jar build --branch feat/1.19.2/vox-packaging-probe --require-portable-artifacts
sfm-propagate-changes.exe jar compare --branch feat/1.19.2/vox-packaging-probe --report-json "$env:TEMP\sfm-jij-compare.json"
```

After carefully propagating the implementation to the named version branches:

```powershell
sfm-propagate-changes.exe jar build --branch 1.19.2 --require-portable-artifacts
sfm-propagate-changes.exe jar build --branch 1.20.4 --require-portable-artifacts
sfm-propagate-changes.exe jar build --branch 1.21.1 --require-portable-artifacts
sfm-propagate-changes.exe jar build --branch 26.1.2 --require-portable-artifacts
```

Use a CLI-owned artifact inspection/clean-launch command when it is added. Until
then, inspection commands may be read-only PowerShell/ZIP operations, but build
and launch orchestration remains in the SFM CLI.

## Recommendation and next owner

Proceed with loader-native JarJar, not vendoring.

The next packaging implementation should be one coordinator-owned change on
oldest branch 1.19.2 that:

1. adds schema-v3 bundle range policy;
2. projects it correctly for ForgeGradle and NeoGradle;
3. selects the bundled Gradle publication based on bundle presence;
4. adds Forge support to the Rust packager; and
5. provides automated artifact inspection and a clean-loader probe.

That implementation touches the Rust CLI and shared Gradle scripts and should
not be split across agents. After it is validated and the CLI is reinstalled,
the normal guarded propagation workflow can carry it to newer Minecraft
branches. A later agent may independently run the version matrix and collect
the clean-launch evidence.
