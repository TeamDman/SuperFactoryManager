# Vox Java artifact packaging probe

This feature branch experiments with the artifact boundary between the
Facet/Vox Java work and SFM 1.19.2. The Vox lock and runtime probe are
branch-only evidence and are not canonical-ready production changes.

Do not merge or publish this lock as production configuration: the local-import
schema surface still needs a proper schema-version bump before canonical use.

## Frozen input

- Coordinate: `org.facet:vox-java:0.10.0-rc.5`
- Facet commit: `5e719ed9f5d1f36ba41242c2c057e74d67ecb3c9`
- Source artifact: `vox/java/target/vox-java-0.10.0-rc.5.jar`
- SHA-256: `E714A48080D453097F1E819DEFBE42412B73F8E17C1B5D0AA4A6BEA52DF734C3`
- Locked BLAKE3: `7fe683de7c2400366b8dfa42d777fcb5aefdb9b4`

The experimental declaration assigns compile, runtime, and bundle scopes, with
the exact accepted bundle range `[0.10.0-rc.5]`, artifact version
`0.10.0-rc.5`, and `is_obfuscated = false`.

The artifact is imported into the managed SFM cache. The lockfile does not
persist the importing machine's absolute path. A local import is intentionally
reported as non-portable: another fresh machine must receive the exact bytes
through `dependency add --local-artifact`, `jar build --artifact-source`, or a
future hosted/source-build acquisition before it can reproduce the build.
An existing lock is rehydrated with `dependency artifact import`; that command
verifies the locked hash before atomically writing or repairing the cache.

## Verified commands

Run from `platform/cli/sfm-propagate-changes`:

```text
cargo run -- run compile --branch teamy/vox-java-artifact-probe --wait-for-build-lock
cargo run -- jar build --branch teamy/vox-java-artifact-probe --wait-for-build-lock
cargo run -- --debug jar audit-artifacts --branch teamy/vox-java-artifact-probe
cargo run -- run client --branch teamy/vox-java-artifact-probe --smoke --wait-for-build-lock
```

The artifact audit verified 109 artifacts with zero errors and one expected
local-import portability warning. Adding `--require-portable-artifacts` fails,
as designed, until Vox has portable acquisition provenance.

The Rust-built SFM JAR contains exactly one copy of each:

- `META-INF/jarjar/vox-java-0.10.0-rc.5.jar`
- `META-INF/jarjar/metadata.json`
- `ca/teamdman/sfm/common/vox/VoxJavaPackagingProbe.class`

The nested Vox JAR has the same SHA-256 as the frozen input. Its JarJar metadata
records group `org.facet`, artifact `vox-java`, exact range
`[0.10.0-rc.5]`, artifact version `0.10.0-rc.5`, and
`isObfuscated: false`.

The final Rust-built SFM JAR SHA-256 is
`8E2B3D9789B0AC089EB660A0888C53B1F0FF9CC5DFE8A4D7A345F6838A34B733`.

The userdev client smoke reaches both markers:

```text
SFM_VOX_JAVA_PACKAGING_PROBE_READY input_bytes=16777216 max_frame_bytes=16777216
SFM_CLIENT_SMOKE_READY title_screen
```

The first marker is emitted from SFM construction after directly calling
public classes from the Vox artifact.

## Remaining proof boundary

The current CLI's client/server runners use Forge userdev. They prove Java
compilation, runtime linkage, and direct SFM-to-Vox invocation, but they do not
prove that a clean production Forge loader discovers the nested JarJar entry.
That last proof needs a CLI-owned isolated Forge installation/launch command,
or a tracked clean instance launched with only the built SFM JAR.

A Gradle-versus-Rust artifact comparison is also deferred. The frozen
coordinate is not hosted, and the existing Gradle JarJar resolver expects
normal module metadata. The experiment does not create a false repository
solely to make that comparison appear complete.
