# Fresh-checkout Gradle wrapper matrix, 28 September 2026

This is SP-02 build evidence for the checked-in `released-4.34.0` generated projects, not release-behaviour acceptance. A new sparse local checkout was detached at source commit `567ce8ed44372d09e66f0f7a69caeb43a40087a2`, before later documentation-only commits. It contained the ten `platform/minecraft/mc-version/<target>` projects and no existing project build outputs. The checkout remained Git-clean after the builds.

For each target, its checked-in `gradlew.bat --version` and then `gradlew.bat --no-daemon --console=plain assemble` exited zero. The build shell could not find the Rust CLI, so no Rust projection or build process drove Gradle. The appropriate explicit JBR installation supplied Java. A first sandboxed 1.19.2 wrapper download was denied network access; a scoped host-access retry succeeded. Remaining wrapper builds ran sequentially. All ten provenance manifests retained preset `released-4.34.0` and identity `blake3:c72d2eb42418abd568df22ed60a4c233cbd8a3a6c06eaf2448c94a25f84e02b6`.

| Target | Wrapper / JVM | `compileJava` | Production task | JAR bytes | JAR SHA-256 |
| --- | --- | --- | --- | ---: | --- |
| 1.19.2 | Gradle 7.5 / JBRSDK 17.0.14 | Executed | `reobfJar` | 1,642,066 | `8d6d2bf66d1119e355866f21de97224773270a953e6d6318437bfc355e5eb1ca` |
| 1.19.4 | Gradle 7.5 / JBRSDK 17.0.14 | Executed | `reobfJar` | 1,594,374 | `716350efc90c13c300066b27b3b5833b762d3ab7b943652986c1c55e3b9b3b54` |
| 1.20 | Gradle 8.1.1 / JBRSDK 17.0.14 | Executed | `reobfJar` | 1,597,313 | `a489cc7585156fe1051c3dff7596d4910d8712f31e5d43c7ed684d65c4af9d45` |
| 1.20.1 | Gradle 8.1.1 / JBRSDK 17.0.14 | Executed | `reobfJar` | 1,641,448 | `fd62f8826421f750c93ea5a8a9b2e3249a84409752a837f92c5d388b72c303e6` |
| 1.20.2 | Gradle 8.1.1 / JBRSDK 17.0.14 | Executed | `jar` | 1,571,978 | `3320c30621b9cf28655e6960844079c3b7347eb28991b0030a4047da6d11cf7c` |
| 1.20.3 | Gradle 8.1.1 / JBRSDK 17.0.14 | Executed | `jar` | 1,573,079 | `ad71cd3680a2349a9de54479b5b216645fba533472ec3df650510cd366ed2a4f` |
| 1.20.4 | Gradle 8.1.1 / JBRSDK 17.0.14 | Executed | `jar` | 1,601,332 | `78c39aeb24a17e0aba5e87451bfdc0f9bd09631c7b8e3cde6caa203e65986fb4` |
| 1.21.0 | Gradle 8.8 / JBR 21.0.11 | Executed | `jar` | 1,623,885 | `a3fe32a1df334eea8ab0601cbe3ad1e0326d462d9c3f0bfd0fddf0da1771e622` |
| 1.21.1 | Gradle 8.14.3 / JBR 21.0.11 | Executed | `jar` | 1,614,498 | `1f6e46fe51fedb4f35a90b6edee713b1ce88b9319652c1892765c010d617d3d5` |
| 26.1.2 | Gradle 9.5.0 / JBR 25.0.3 | From cache | `jarJar` | 1,926,793 | `523de2cc29b6f4391b648f5870f7ff08b4b06a2593b396234abd0521f33f27b6` |

The 26.1.2 build also made a slim JAR; the table records the production `jarJar` output. Nine targets executed `compileJava`; 26.1.2 reused a compile cache entry and did not freshly invoke javac. Shared Gradle dependency and task-input caches were warm, so this is a fresh committed-checkout and checked-in-wrapper proof, not cold-cache reproducibility. The hashes need not match earlier builds because ZIP and manifest timestamps vary; this table alone does not establish byte, gameplay or save compatibility with the published JARs. No disk-space error, dependency change, release action or source edit occurred.

The wider [source-projection plan](sfm-main-source-projection-plan.md) retains the separate release-parity and promotion gates.
