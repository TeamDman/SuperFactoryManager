# Clean production-loader JarJar probe

This document defines the acceptance boundary implemented by
`sfm-propagate-changes jar clean-loader-probe`. The command exists because a
Forge userdev launch can put a library on the development classpath and thereby
hide a broken release JarJar artifact.

## What the command proves

The command accepts an already-built SFM release JAR and an explicit Forge
installer JAR. It:

1. records the SHA-256 of both exact inputs;
2. parses `META-INF/jarjar/metadata.json`;
3. requires selected Maven identities and Java classes to exist in valid nested
   JAR entries;
4. refuses an existing instance directory, then creates a new one;
5. runs the production Forge server installer, without Gradle;
6. places exactly one file in `mods`: the SFM release JAR;
7. reads the installer's production `user_jvm_args.txt` and
   `win_args.txt`/`unix_args.txt`;
8. rejects a launch argument file that directly mentions any nested JAR
   filename;
9. enables JVM class-load tracing and Forge debug logging;
10. launches the finite dedicated server, sends `stop` after the requested SFM
    success marker, and forcibly cleans up after the timeout; and
11. writes installer output, loader output, exact hashes, class-load diagnostic
    lines, locator evidence, and outcome to a Facet-generated JSON report.

The success marker must be emitted by SFM only after it directly invokes a
class from the nested library. JVM `class+load=trace` output records the class
source and loader data in the same launch log. Together with the one-mod
instance and absence of a direct nested path in the production argument files,
this distinguishes loader discovery from userdev or cache leakage.

This is intentionally general: multiple `--expected-nested group:artifact` and
`--required-nested-class binary.Name` options may be supplied for future
bundled libraries.

## Commands

Artifact and plan inspection, with no installation:

```powershell
cargo run -- jar clean-loader-probe `
  --release-jar <release.jar> `
  --expected-release-sha256 <64-hex-sha256> `
  --forge-installer <forge-installer.jar> `
  --expected-forge-installer-sha256 <64-hex-sha256> `
  --instance-dir <new-absent-directory> `
  --success-marker SFM_VOX_JAVA_PACKAGING_PROBE_READY `
  --expected-nested org.facet:vox-java `
  --required-nested-class org.facet.vox.VoxResult `
  --report-json <plan-report.json> `
  --plan-only
```

Production Forge launch:

```powershell
cargo run -- jar clean-loader-probe `
  --release-jar <release.jar> `
  --expected-release-sha256 <64-hex-sha256> `
  --forge-installer <forge-installer.jar> `
  --expected-forge-installer-sha256 <64-hex-sha256> `
  --instance-dir <new-absent-directory> `
  --success-marker SFM_VOX_JAVA_PACKAGING_PROBE_READY `
  --expected-nested org.facet:vox-java `
  --required-nested-class org.facet.vox.VoxResult `
  --timeout 5m `
  --install-timeout 15m `
  --report-json <launch-report.json>
```

Both inputs require explicit expected SHA-256 values and execution stops before
installation if either differs. The installer is explicit rather than silently
downloaded so the report can identify the exact loader input. Forge's installer
may still fetch missing Minecraft and runtime libraries from the repositories
declared by Forge. A fully offline run therefore requires those installer
inputs to have already been cached by the installer.

## Negative gates

Unit tests construct small release artifacts and prove:

- a production argument file mentioning the nested JAR is rejected as direct
  classpath leakage;
- a production argument file containing only loader libraries is accepted;
- metadata naming a missing nested entry fails;
- corrupt nested bytes fail ZIP/JAR validation; and
- a valid nested class and required Maven identity pass inspection.

The command never deletes or reuses an instance. A caller must supply a new
absent path for each proof. This makes the loader/runtime boundary auditable and
prevents an earlier mod or manually installed library from satisfying the
probe.

## Durable evidence

The command writes the complete schema-v2 report requested by `--report-json`.
Beside it, the command writes a generated `*.evidence.json` manifest containing
the SHA-256 of that exact full report and every installer/launch log that
exists. The full report records the source commit, complete invocation,
operating system, architecture, Java executable and version, all input and
installed-copy hashes, numeric locator result, exact per-required-class source
and loader evidence, exit status, and timeout state.

Checked-in evidence must consist of that generated full report and generated
hash manifest. A manually summarized JSON file is not authoritative. Full logs
may remain ignored when impractically large, but their exact hashes remain in
the generated manifest.
