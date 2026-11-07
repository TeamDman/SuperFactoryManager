# Managed Linux SDK-only installation

Use a newly built, audited CLI explicitly. This command selects one SDK from a
current catalog projection's exact authored/emitted lock, installs it into an
external SDK cache and performs full archive/tree verification. It never runs
Java, compiles SFM, builds a dependency, synchronizes sources or changes a pin.
Existing Windows acquisition paths are unchanged; this new command is Linux-only.

```sh
"$CLI" --output-format json jdk install \
  --project-root "$REPO/platform/minecraft/projections/sfm-dev/mc-26.1.2" \
  --major 25 --cache-root "$SDK_ROOT" --offline \
  --extract-bytes-per-second 2097152
```

The projection must already pass current source ownership checks. The external
root must be an absolute normal path outside the source repository and outside
any marked native build root or installed SDK. Existing linked ancestors or markers in the actual
SDK write directories fail closed. This command does not create/adopt a native
cache marker or merge SDK and native build roots.

The SDK cache is `$SDK_ROOT/minecraft-toolchain/jbrsdk`. The exact pinned archive
is `archives/<full-lowercase-sha512>.tar.gz`; installs use the existing
`installs/v2-<first-64-sha512-hex>` naming. With `--offline`, a missing archive
fails without a fetch. An existing matching archive is held and reused without
another archive payload copy. Without `--offline`, the unchanged pinned downloader
may acquire that exact archive through the inherited TLS/proxy policy.

The existing Rust manager owns the SHA-keyed lock, private extraction, generated
receipt, full byte/link/mode comparison, install publication and completion seal.
Direct helper extraction is not installation. Corrupt archives, changed installed
bytes, conflicting receipts/seals and unsafe paths fail; published entries are not
repaired. Existing failure/staging cleanup conventions remain unchanged. A failure
after publication may leave a fully verified install without a seal; later normal
managed verification can finish it. No partial install is reported as successful.

`--extract-bytes-per-second` is optional, decimal 1..1073741824. It caps regular
file payload writes in chunks of at most 64 KiB. Each chunk waits before writing
and flushes afterward; read/fsync stalls provide no catch-up credit. Directories,
link metadata, final receipt/seal writes, reads and OS writeback are not governed
by this payload rate. Keep the external disk/inode/rate supervisor unchanged:
this is an operational control, not a hard free-space guarantee.

Every helper has a one-hour runtime limit. A paced payload whose minimum delay
alone exceeds one hour is refused before extraction; parsing, verification and
filesystem stalls can still exhaust the limit at a higher rate. Cancellation
kills/reaps the exact no-descendant helper before private-stage teardown. Original
archive/hash/tar/tree/link/mode checks and final full verification remain enabled.
The rate is excluded from SDK receipts, install names, seals and consumer keys.

The typed `sfm:managed_sdk_install@1` report identifies the projection, raw lock,
selected major/platform/archive digest, external root and persistent home. It
records that runtime execution was not performed. A successful installation is
not a Java HTTPS probe, game-build qualification, prepared-image gate or image
publication. Follow `docs/codex-cloud-environment.md` for external trust and layout
audits after installation. Do not install/redirect another task's active CLI.
