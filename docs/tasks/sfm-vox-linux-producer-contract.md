# Vox producer platform identity and external trust

This change distinguishes the declaration of a producer from its host-specific
execution. It does not qualify Linux Vox recovery or change a dependency's bytes.

The selected development locks retain `VOX_JAVA_BUILD_JDK` as their original
Windows reference and declare both exact platform values:

- Windows x64: `JetBrains s.r.o.|21.0.11+1-b1163.116`;
- Linux x64: `JetBrains s.r.o.|21.0.11+10-b1163.116`.

The existing archive SHA-512 pins, source revision
`5628c00d9f3816199f324a9ccc081de8a3a1b1bc`, tasks, output path and JAR hashes
remain unchanged. Only the 13 selected development lock environments change.
Frozen release recipes and the immutable test-dependency authority are untouched.

SFM admits only the exact legacy map or complete three-key platform map. The
legacy map still authorizes its Windows execution; it is not reinterpreted as
Linux. New execution selects the declared host runtime, validates its exact
platform/archive/vendor/version/build tuple, checks the managed SDK, and
compares its authenticated release properties before launch. Unreviewed source,
tasks, output paths, partial declarations and ambient JVM/loader overrides fail.
Linux uses the existing full archive/tree comparison. Windows preserves its
existing sealed-reuse and held launch-critical-file checks; this is not a new
full Windows recursive audit. Controlled Linux audits observe cancellation.

The pinned Vox xtask really supports `VOX_JAVA_BUILD_JDK`: `package_java` compares
it with `IMPLEMENTOR|JAVA_RUNTIME_VERSION` from `JAVA_HOME/release`. SFM passes
that supported key with the selected exact host value and the verified
`JAVA_HOME`; the two declaration-only keys are not forwarded. Declared lock
provenance stays unchanged. Prepared-selection logs describe this distinction;
they are neither producer-completion receipts nor proof of matching JAR bytes.

The reviewed exact-byte cached-JAR runtime contract accepts either declaration
without depending on the current host. Its same BLAKE3, full SHA-256, length,
source and recipe checks still apply. A previously produced JAR can remain
portable; cache-sidecar recipe reconciliation is not a Linux execution witness.

## Remaining cross-repository trust blocker

Pinned Vox has no explicit external Java trust mechanism. Its xtask at the
current `928e0937` head has the same Git blob
`31211a3beee5039d7d44a7ebd8e7e1cdc49246cd` as the pinned source. Merely changing
to that revision would not fix the problem.

`package_java` requires jdeps stdout and stderr to be empty. `JAVA_TOOL_OPTIONS`
prints a startup banner, so SFM refuses a selected major-21 external trust policy
before this Cargo build function prepares its checkout or acquires/probes its
SDK. It does not discard the policy, send ignored Vox trust variables, accept
arbitrary stderr, change SDK cacerts, or weaken TLS. Generic non-Vox Cargo trust
handling is unchanged.

A reviewed Vox change must authenticate the external store and forward explicit
properties to every Java tool, including `-J-D...` for JDK tools where required,
while preserving the strict output checks. Its published source must then be
reviewed and separately pinned, and its actual Linux JAR must match the required
artifact identity or undergo the separate dependency-admission process. This
change neither adopts unpublished `1315e444` nor imports the held SFM bridge.

Full CLI compilation/integration tests, real pinned producer execution and JAR
byte equivalence must be reported separately from standalone policy/metadata
tests. Do not use a cache hit as evidence of source miss recovery.
