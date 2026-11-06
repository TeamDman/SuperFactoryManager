# Linux NeoForm warm-cache reuse

Linux native builds with an explicit managed cache root can reuse authenticated
Minecraft/NeoForge classes and source archives without creating another NFRT
invocation. The existing immutable `/opt/sfm/derived-toolchains` profile remains
a separate authority. Windows keeps its existing cache and lease implementation.

The dependency key still binds the exact recipe, raw lock, ordered artifacts and
tools, access transformer, SDK and execution image, platform, and explicit Java
trust store. Invocation IDs and irrelevant SFM source-owner labels are excluded.
SFM source edits still change project compilation fingerprints. Linux project
compilation additionally binds the complete dependency key and verifies its
recorded output inventory; existence alone never admits a javac hit.

## Storage and integrity

`local-dependencies-linux-v1` under the selected native root has a Linux ownership
marker and private per-key directories. The Windows `local-dependencies-v1`
namespace is preserved without adoption or migration. An anchored, no-follow, inode-checked
producer lock serializes cooperating producers. Complete read-only output
pairs and their historical producer manifest precede a create-only head. Files
and directories are synced before publication is reported. Identical warm
consumption reuses the same paths and stable consumption receipt, without
another payload copy. Refresh cannot replace an existing historical entry.

Lookup checks canonical metadata, full hashes, types, identities, ownership,
permissions and link counts. Namespace and full-content checks repeat around
consumption, including compiler-cache checks and writes. Corrupt heads, changed
held inputs, unsafe ownership, publication failures and cancellation remain
errors; old entries and partial evidence are preserved. There is no pruning,
retirement, migration or automatic repair in this change.

These are trusted-local developer-cache checks, not Linux mandatory write
exclusion or OS filesystem attestation. They detect changes at verification
boundaries. A malicious same-user writer that changes and restores data between
checks is outside this authority, as is forgery of both payload and manifest.
No descriptor is described as preventing writes. Historical consumption never
becomes a fresh producer execution receipt.

Linux declines dependency lookup/publication and project compilation reuse when
`LD_PRELOAD`, `LD_LIBRARY_PATH`, `LD_AUDIT`, `JAVA_TOOL_OPTIONS`,
`JDK_JAVA_OPTIONS` or `JDK_JAVAC_OPTIONS` is present, including empty values.
Their external execution effects are not covered by the image/key. The existing
`_JAVA_OPTIONS`/`JDK_ALTERNATE_VM` decline policy also remains. This does not scrub
or change fresh child execution, and it does not defend against an interposer
already injected into the CLI itself. Windows policy is unchanged.

The Linux SDK image hashes core `java`, `javac`, `modules` and `release` bytes as
well as selected native/configuration files and permission modes. Its checks
run before recording compiler output, including changes made during javac;
an outer post-consumer SDK check alone would be too late. It rechecks content,
membership and identity. Direct contained SDK links
are bound to their text and regular target; contained directory aliases are
traversed with cycle and size limits. Escapes, unsupported link chains and
hardlink aliases fail closed. Exact pinned JBR layout compatibility still needs
runtime validation with the approved archive/installation, not an assumption
based on another local SDK.

## Sources

The durable `sources.jar` remains separately verified from `classes.jar` and is
a usable archive after the command exits. Explicit filetree requests publish a
checked, grep-able tree beneath that cache entry. Extraction uses anchored
directories, create-only leaves, existing archive validation/limits, a per-entry
lock, and a complete-tree-before-head protocol. No generic advisory marker is
written and no existing tree is replaced. Source-catalog readers continue to
consume the verified source archive. Invocation `.bin` snapshots remain distinct
from these archive and filetree interfaces.

## Validation and manual handoff

From the existing CLI checkout, compile the library test executable and run it
directly from the normal caller environment, as the repository's bounded runner
does. Cargo injects `LD_LIBRARY_PATH` into executables it launches; that is an
unsupported cache profile and correctly causes cache-positive cases to decline.
Do not add a production exception or silently strip real launch controls.

```sh
cargo test --lib --offline --locked --no-run
# Set TEST_BIN to the printed executable for unittests src/lib.rs.
"$TEST_BIN" local_dependency --test-threads=4
"$TEST_BIN" project_compilation_cache
SFM_LINUX_CACHE_TEST_JAVA_HOME=/already/installed/sdk \
  "$TEST_BIN" linux_existing_javac_warm_output_admission --ignored --nocapture
```

The optional local-javac test proves real class-output admission and source-edit
invalidation. It is not a pinned Minecraft/NFRT qualification. Store fixtures do
not manufacture production admission or fresh execution evidence.

For actual cold/warm validation, use an existing synchronized projection,
already admitted exact dependencies, verified JBR21 and an approved managed
cache. Select the newly built CLI explicitly; preserve all old evidence. For
example, run this twice with unchanged inputs and without `--refresh`:

```sh
"$CLI" --log-filter info --log-file "$RUN_LOG" --output-format json \
  source project compile --repo-root "$REPO" \
  --projection sfm-4.34.0/mc-1.20.4 --cache-root "$CACHE" \
  --java-home "$JBR21" \
  --released-recipe sfm:released-native-inputs/4.34.0/1.20.4@1
```

Use separate retained logs for each run. Check that the warm trace reports
historical reuse and verified project compilation hits, has no NFRT
host/adapter/tool launch or new invocation directory, and preserves output
bytes/timestamps. Exercise the existing source-output filetree and source-catalog
commands and inspect their returned paths. Repeat after SFM-only and real
dependency/tool/SDK/trust/transformer changes to distinguish the two caches.
Do not acquire missing inputs implicitly or reuse an old qualification receipt.
Windows execution and the full pinned cold/warm route require their own evidence.
