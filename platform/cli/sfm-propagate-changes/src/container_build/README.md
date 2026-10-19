# Local container workflow

`container build --projection KEY --context NEW_DIRECTORY --prepare-only` performs
read-only catalog and frozen-lock checks before staging anything. It never acquires
dependencies, rewrites a source lock, launches Minecraft, pushes an image or changes
Azure/Kubernetes state. Missing prerequisites produce a typed report with exit 2;
no usable context is published.

The first admitted target is `linux/amd64`. The default native transport uses
Hyper HTTP/1 over an explicitly supplied local `--engine-endpoint` named pipe or
Unix socket; it never discovers personal engine configuration or uses TCP. Only
reviewed Podman 6.0.2 linux/amd64 with rootless and seccomp enabled is admitted.
`--offline` disables pulls and build networking. Normal mode permits a missing
public base pull and private build networking, but context preparation remains
cache-first and reports missing inputs; Without `--toolchain-lock`, the native API can bootstrap both Linux CLIs from
checksum-verified frozen Cargo inputs and the explicitly reviewed local graphics
base digest in bootstrap.rs. It uses no network and admits no Windows binaries.
This is a warm-cache workflow; it does not establish cold Linux acquisition.
An explicit contract remains supported.

Toolchain contract schema `sfm:linux_toolchain@1` has exactly these fields:
`schema`, `platform`, `source_lock_sha256`, `base_image`, `java_major`, `java_identity`,
`cli_path`, `cli_sha256`, and `cli_source_identity`. The CLI path is relative to the
contract directory. Its SHA-256 and amd64 ELF header must match. The CLI source
identity is SHA-256 of the canonical JSON map from repository-relative CLI source
filenames to their SHA-256 hashes (Cargo manifests, build.rs and src/**/*.rs and included src/**/*.java and src/**/*.py).

The contract is an explicit reviewed dependency boundary, not automatic acquisition
approval. Its digest-pinned local base must contain the exact Linux SDK/compiler(s),
Xvfb/Xauth, Mesa GL/GLX/DRI, required X libraries, null audio support, fonts, Git,
Rust/Cargo and already-pinned source-build/Cargo inputs. It must contain no accounts,
tokens, enrolled identity or unrelated private caches. A digest identifies bytes;
it does not by itself establish that this review occurred. Prepare-only verifies
the contract and exported inputs; the offline image build checks tools/Java identity
and invokes canonical native compilation. The reviewed base must set `JAVA_HOME`;
both compilation and puppet execution use the existing explicit `--java-home`
override, so a Windows-only SDK catalog is never treated as a Linux archive pin. Unknown cold Linux prerequisites fail.

The exporter copies only checked selected projection/authored inputs, fixed catalog
metadata, control-CLI source, frozen cache rows with admitted provenance, authenticated
Minecraft descendants and the pinned Linux CLI. Artifact hashes are checked while
copying from the same open handle. Windows/macOS native classifiers are excluded;
Windows SDKs, EXEs and DLLs, user homes, .minecraft, credentials, worlds, runtime
descriptors and wholesale caches are not context roots. Schema 4 uses the existing
`rust-toolchain` profile API; schema 2 is preserved rather than migrated.

Publication reserves a new output directory. `context-manifest.json` is published
last; partial output has no valid completion manifest. The backend rechecks all
manifest-listed file hashes and refuses extra files. Source-lock, ownership,
toolchain and metadata-parent identities are recorded without local absolute paths.

`context_prepared` means a verified input snapshot; `image_built` additionally
requires engine success including native compilation. Neither means gameplay was
validated. The multistage builder prepares Java outputs at stable `/work` paths using the
canonical native executor. Separate Rust dependency and authored-source stages
retain dependency artifacts; the final scratch runtime copies reviewed OS/JRE,
graphics, launch-file dependencies, content-addressed assets and the control CLI.
Builder Cargo caches, targets, Git trees and source are not inherited. The
entrypoint accepts the baked exact projection and an `sfm:` GameTest ID and
launches the prepared Java argument file without compiling. The runtime packer
rejects unreviewed path roots and saved-world/capture directories. No volume or host bind mount is required. Runtime controls
(network none, nonroot, dropped capabilities, seccomp, PID/CPU/memory and timeout
limits) belong to the separately reviewed run command. Retrieve allowlisted evidence
with `podman cp`; never export descriptors or homes.

Acceptance requires raw GameTest and puppet completion, all eight decoded orbit
PNGs, and visual inspection of a ready late frame. A graphics probe or container
startup alone is insufficient. Keep any game-containing image private; separately
review distribution rights and registry/access/cost decisions before pushing.

The canonical authenticated and legacy library acquisition paths now share the
Linux/amd64 policy with this exporter. Authenticated rules remain attached to
their immutable metadata children; selection preserves order and Windows behavior.
Focused library/authentication/platform tests passed. The native engine smoke
build passed with UID 10001, CapEff 0, seccomp 2, loopback-only offline network,
8 GiB memory and two-CPU quota. It recorded an OCI manifest digest separately
from the config ID. It did not compile SFM or launch a game.

Native requests explicitly create private network/PID/IPC/UTS namespaces, omit
mounts/devices/secrets/security overrides, drop all capabilities, limit jobs to one
and bound stream/time/context bytes. Capacity checks use live Linux and Windows
memory, never resizing the machine. The server native build handler does not enforce no-new-privileges. Generated
recipes therefore use `/usr/bin/setpriv --no-new-privs /bin/sh -c` for every RUN;
the reviewed base must include setpriv. This narrower shell profile is separately
checked by the opt-in probe. No engine or host security configuration is changed.
Docker execution is rejected pending a reviewed isolation profile. The explicit
Podman command fallback uses the same resource/namespace/network restrictions.
API cancellation closes its connection; engine completion is unconfirmed.
Image-build receipt identity does not replace gameplay evidence.

No installed executable is updated by these source changes. Use a source-built CLI
only after the focused Rust checks and full required gate/installer decision are
complete. Preparation/testing of the new source is separate from historical images.


Optimization validation is distinct from the earlier accepted image. Inspect the
current build and launch receipts before claiming cache hits, runtime size,
no-compilation capture or managed-session acceptance.

`--java-max-heap-mib` accepts 512..8192 (default4096).
`--session-seconds` accepts 0..28800 (default0 means one orbit and clean exit).
The optional managed session starts with an actual GameTest orbit, then services
up to128 bounded local file requests in the same disposable world. Requests live
under `<game_directory>/managed-session/000001.request.json`, with matching
response filenames; each request carries `sequence` and `op` (`run_game_test`,
`reset`, `capture`, `stop`). Test names omit `sfm:`; capture angle is finite and
within +/-2pi. Reset means freshly preparing the named fixture, not erasing or
exporting a world. Read the ready/response files before advancing sequence.

Context admission reserves8GiB; staging reserves2GiB; native API checks reserve
20GiB in the engine store and monitor progress. Runtime reserves2GiB and has a
startup deadline plus a bounded session lifetime. Exhaustion aborts without
pruning images or unrelated data. Closing a failed build API connection does not
prove an engine-side build has stopped. Java compile diagnostics are retained;
JUnit compilation has a separate explicit success/failure receipt and is never
inferred from GameTest success.


The exact native control-executable alias in the launch arguments is checked
against the packaged ELF, then rewritten at launch; its builder target directory
is not exported. Task-generated bare Facet stores are repacked with one thread,
no deltas and no reused compressed objects; this normalization was verified
against two semantically identical stores that initially differed in pack bytes.

JUnit uses the canonical cache-only effective-POM resolver, including parent
properties, dependency management and ordered BOM imports. Metadata reads have
depth, visit, model and byte limits; missing metadata/artifacts fail precisely.
The approved test graph adds exact frozen pins without upgrading game-runtime
dependencies. Repeated scalar POM properties follow Maven3.9.9 last-value rules;
nested property values and DTD/entities remain rejected.

Windows native validation compiled all four source sets, including479 test files.
The subsequent actual JUnit run started2550 tests:2545 passed,0 failed,5 aborted
and0 skipped. JBR17 Windows symlink canonicalization failed with "More data is
available"; terminal symlinks now fail closed as unavailable, while per-child
handling retains a visible nonexpandable blocked-link entry. Demo discovery now
walks to filesystem root instead of stopping before the existing sixth-ancestor
examples directory. Original assertions were retained; only the dev projection
was regenerated. No test disables or release-projection edits were added.
The five prerequisite aborts require the itemstack repository coverage audit,
installed symbol-worker executable/branch properties, a release-review scale
fixture, an installed release-ledger companion, and a review-projection fixture.
Linux results and actual game orbit capture require their own evidence.

Container preparation traces test POM reads through the canonical cache-only
resolver, including consumed parents and BOMs. It exports exact pinned POM bytes
and a reduced provenance catalog, then rechecks the consumed identity before
publication. Missing, corrupt or weakly pinned inputs fail before Linux bootstrap.
Context reuse compares the consumed identity; unrelated catalog rows do not
invalidate it. Synthetic metadata regressions passed in the required Rust gate;
the exact current-cache subset and Linux validation still need runtime evidence.

The scratch runtime explicitly copies OS files as UID0 and game files as UID10001.
It includes xkbcomp plus X11 keyboard data and a root-owned1777 X11 socket directory.
A final-stage nonroot software-GL preflight must report llvmpipe before image admission;
this checks the packaged runtime, not the richer SDK. Game acceptance remains separate.

## Public build profiles and bounded Linux JUnit jobs

`container build --profile runtime|tests|developer` prepares one immutable,
allowlisted input context and selects a target in the common cached FROM graph.
The default `runtime` target is the lean precompiled game/JRE/software-rendering
image. `developer` retains the pinned Linux SDK, Rust/Cargo and SFM executables,
canonical sources, compiled Java/test prerequisites and exact public repository
fixtures. `tests` shares those layers and defaults to the fixed JUnit workload.
No world or enrolled identity is baked. SDK profiles copy reviewed paths from
preparation rather than inheriting the duplicate packaged runtime OS tree.

```powershell
$cli = '<candidate-or-installed-current-sfm-propagate-changes.exe>'
$pipe = '\\.\pipe\podman-machine-default'
& $cli container build --repo-root '<SFM-checkout>' --projection sfm-dev/mc-1.19.2 --profile developer --context '<new-context>' --engine-endpoint $pipe --tag sfm-golden:developer --offline --timeout 45m
# Another target reuses the same rechecked context and preparation graph.
& $cli container build --repo-root '<SFM-checkout>' --projection sfm-dev/mc-1.19.2 --profile tests --context '<same-context>' --reuse-context --engine-endpoint $pipe --tag sfm-golden:tests --offline --timeout 45m
& $cli container test --engine-endpoint $pipe --image '<exact-image-config-id-from-build-report>' --projection sfm-dev/mc-1.19.2 --context '<same-context>' --output '<new-receipt-directory>' --timeout-seconds 900
```

Image cache receipts and live labels bind the selected profile separately from
the shared input-context identity. Internal tooling and dependency images keep
their own graph/cache identities. A context can build a missing profile without
source mutation; a cached profile skips the engine build only after context,
source, pinned cache and live immutable-image checks. No timestamp-only shortcut
replaces artifact freshness hashing.

`container test` requires an exact local Linux/amd64 configuration ID with a
matching `tests` or `developer` profile and verified context. It verifies the
immutable context bytes; the build command is responsible for checking those
inputs against the current checkout. Rebuild/reuse-check after source changes
before claiming a test is current. It runs the canonical `test run` executor,
never Gradle or an alternate downloader. The image contains a hash manifest of
canonical/rendered source and the finite public fixture allowlist; the job checks
those hashes before and after testing. There are no host fixture overlays.

Jobs use the same fixed sandbox as typed sessions: UID10001, private mapped
namespaces, networknone, no mounts/devices/ports/host environment/proxy, dropped
ALL capabilities, no-new-privileges, default seccomp, 8GiB memory/swap, two CPU,
512PIDs and 64MiB shared memory. Existing engine/version/security and memory/disk
admission must pass. A bounded live MemAvailable probe handles reclaimable-cache
ambiguity. No machine settings or resources are created or resized. Time and
disk floors are checked during execution; cancellation/timeout/capacity failure
stops only the immutable worker created by this invocation.

The job exports bounded test/security/source-verification logs before removing
that owned worker. Output is a fresh directory, never overwritten. `receipt.json`
records image/context/worker identities, elapsed time, child exit code, actual
found/started/pass/fail/skip/abort and failed-container counts, artifact hashes,
cleanup confirmation and errors. Missing/duplicate summaries, unaccounted tests,
failed containers, missing security/source evidence and nonzero child exit all
fail acceptance. Test failure returns a typed receipt and nonzero command status.
Export errors still attempt owned cleanup; an unsafe/unverifiable worker is
retained with its identity and explicit cleanup-unconfirmed evidence.

No task-local composition/fixture-overlay/acceptance orchestration script is
required by these public operations. The fixed image workload is an included
repository Python module invoked under the typed host lifecycle. It performs
only canonical test execution, security/hash verification and local receipt
collection; engine creation, admission, resource caps, polling, export, failure
accounting and cleanup remain in reusable Rust code.

Typed `container session start/status/reset/run-test/capture/stop` operates on
runtime/developer images independently of the JUnit job. Rendering/orbit capture
must still be validated as a separate actual client workload. A JUnit result is
not proof of graphics/gameplay. A successful retry does not erase preserved
third-party initial-config failures. Keep any game-containing image private and
review distribution/access/cost changes before any push. These commands do not
push, deploy, install, alter identities/security, prune caches or invoke Gradle.

Build admission treats low Podman `MemFree` as potentially reclaimable cache.
After exact image-cache reuse checks, a missing profile can use the existing
bounded, private capacity probe against the digest-pinned executable base to
verify Linux `MemAvailable`. The shared probe requires 8GiB available plus a
2GiB reserve, stricter than the unchanged 6GiB build cap. Windows headroom,
engine identity/rootless/seccomp checks and disk floors remain required. A
missing/unverifiable base or failed capacity probe fails closed. This does not
resize the VM, clear page caches, change settings or relax resource limits.


## Pinned first-start configs

Public builds include a reviewed default seed only for `sfm-dev/mc-1.19.2`
with source lock `sha256:198c141c2e45ed29a90935e84565e10f861aa0895233bb9b4a10cb8d2c109d41`.
Other projection/lock pairs fail closed until their own defaults are generated,
reviewed and validated; 1.19.2 defaults must never be applied to another version.
`config_seed_data.py` records145 files (297,390 decoded bytes),117 pinned Maven
jar hashes, schema and generation provenance. The CLI source identity and full
context manifest bind both seed Python resources; seed changes invalidate reuse.
The config-seed stage runs after runtime packing and writes an independent COPY
layer:131 global files under `config/`,14 world defaults under `defaultconfigs/`.
No world, home or personal config is imported. Before Java, the launcher verifies
the binding and every staged byte; after Java it records allowlisted config hashes
and correction counts in `config-seed-receipt.json`, exported by session stop.
Server receipt hashes come from the single disposable world's `serverconfig/`.
Staging avoids observed startup corrections but does not disable Forge saves or
watcher callbacks and is not a fix for mod/Forge reload races. Dynamic
SophisticatedCore77-item defaults apply only to this exact pinned graph.
Focused offline synthetic validation: `python config_seed_tests.py` from this
directory. No engine/game is required. Required Rust gates remain `check-all.ps1`.


## Support-agent catalog profile (incremental Linux capability)

`container build --profile support-agent --projection sfm-dev/mc-1.19.2 ...`
retains the reviewed developer toolchain and shared content-addressed dependency
cache, exports the complete authored Liquid tree (including inputs unselected by
the seed projection), and starts `/opt/sfm/support_agent.py`. The initial image
construction still requires the existing accepted 1.19.2 config-seed lock. The
profile does not add unpinned SDKs or bypass the native Linux adapter admission.

The controller accepts `list`, `select`, `generate`, `status`, `build`, `launch`,
`start`, `run-game-test`, `capture`, and `stop`. In one running container, for example:

```sh
python3 /opt/sfm/support_agent.py list
python3 /opt/sfm/support_agent.py select --session a --projection sfm-dev/mc-1.19.2
python3 /opt/sfm/support_agent.py select --session b --projection sfm-dev/mc-1.20.1
python3 /opt/sfm/support_agent.py generate --session c --projection sfm-dev/mc-1.19.2 --name diagnostic
python3 /opt/sfm/support_agent.py build --session c
python3 /opt/sfm/support_agent.py launch --session c --game-test sfm:move_1_stack_direct
```

Use the existing local engine worker isolation: no network or mounts, nonroot,
capabilities dropped, no-new-privileges, 512 PIDs, two CPUs and 8 GiB memory.
Keep a worker alive with an explicitly overridden sleep entrypoint when issuing
multiple `exec` requests. No install, registry push, host volume or daemon socket
is needed inside it. Source and catalog are root-owned under `/opt/sfm/catalog`;
each session receives a writable copy and canonical Git ignore policy under
`/work/agent-sessions/ID`. An atomic operation lock prevents concurrent builds or
games. An interrupted lock requires operator inspection before removal. Existing
sessions are never overwritten. The inherited `.sfm-cache` is shared; generated
sources, native output paths and worlds remain session-local. An 8 GiB free-space
reserve is checked before source staging or building. Commands time out at 900s
and terminate their process group. Receipts and full compiler diagnostics remain
inside the session, with gameplay acceptance false until independently reviewed.

A derivative copies an exact catalog entry into a new dev key, optionally adding
repeatable `--feature NAME` values. The canonical generator validates features,
prerequisites and target support. Unknown versions or incompatible features fail;
this does not promise support for future Minecraft versions. Use a fresh session
to make another derivative. Release projections can be generated and inspected,
but puppet builds require their declared game-puppet features; a bare released
recipe is not silently converted into a support world.

Current Linux admission covers all ten supported targets using the existing
Forge recipes and exact captured NeoForm recipes. Compiler and tool SDKs are
selected separately: Java 17 is retained from the reviewed base, and verified
Linux Temurin 21/25 archives are provisioned by the support profile. The catalog
cache union is content-addressed and exported once; `list` and `status` include
per-projection missing-input diagnostics. Missing cache rows do not make the
whole catalog ready, and no cold-download fallback is implied.

`build` uses the portable-only canonical puppet dry-run. `launch` uses the
bounded one-shot GameTest and eight-capture lifecycle. `start --session ID
--seconds 1800` creates the existing managed superflat puppet; `run-game-test
--session ID --game-test sfm:move_1_stack_direct`, `capture --session ID --angle
0.0`, and `stop --session ID` use its bounded request/response protocol. One
active process identity is recorded for the container, so a second game cannot
start concurrently. Managed sessions permit at most 128 requests and 28,800
seconds. A fixed root-owned manifest pins the prebuilt Linux control executable;
this opt-in route verifies its ELF and SHA-256 and avoids Cargo during launch.

Historical accepted GameTest/eight-capture evidence remains unchanged. New
runtime acceptance must be backed by separate receipts: catalog generation,
SDK provisioning and successful compilation do not establish gameplay acceptance.

Validation checkpoint (2026-10-11): one bounded, network-free container selected
and ran a generated 1.19.2 Forge derivative and the 1.20.4 NeoForge dev projection
sequentially. Both passed the initial and requested `move_1_stack_direct`
GameTests, produced eight reviewed orbit captures plus one requested capture,
and completed the managed stop protocol. The corrected 1.20.4 teardown also
exited its JVM with status 0. Earlier failed builds and its title-screen config
crash are retained separately; they are not accepted receipts. Gameplay used a
mutable validation worker with verified cached inputs and authored source fixes;
the final public image must have its own immutable-image smoke receipt.

The remaining runtime matrix is 1.19.4, 1.20, 1.20.1, 1.20.2, 1.20.3, 1.21
(catalog alias 1.21.0), 1.21.1 and 26.1.2. These targets are admitted by the
catalog/toolchain rules but are not runtime-accepted by this checkpoint. The
26.1.2 cache currently contains an unapproved/weak Mekanism input; it must be
separately pinned before those rows can be called ready. Runtime validation is
never inferred from a cache-ready flag. Linux directory leases retain checked
file descriptors and detect namespace replacement before consumption; they do
not provide Windows' deny-rename lease semantics.


Final image validation (2026-10-11): `localhost/sfm-support-agent:public-8-fixed`
(OCI SHA-256 `b381ed667c325be3b87fc48fc10c736a1bb11bcdc63e3c75f6ff2f84a919ee37`)
contains 34 catalog entries, 31 cache-ready rows, and Java 17/21/25. A fresh
nonroot, network-free worker selected 1.19.2 and 1.20.4 and generated a new
`agent-final-image-smoke/mc-1.19.2` derivative. In that same worker, the 1.20.4
NeoForge projection and generated 1.19.2 Forge projection both built, passed
initial and requested GameTests, produced eight reviewed orbit captures plus
one requested capture, completed managed stop, and exited their JVMs with
status 0. No runtime source or binary patches were applied to this worker.
This is separate from the earlier mutable-worker checkpoint. The remaining
eight-target runtime matrix above still applies; cache readiness is not runtime
acceptance. The 26.1.2 Mekanism provenance blocker remains unchanged.

For a release recipe without puppet features, explicitly create a support
derivative and let the canonical generator validate it:

```sh
python3 /opt/sfm/support_agent.py generate --session release-support \
  --projection sfm-4.35.0/mc-1.20.4 --name release-support \
  --feature game_puppet_runtime --feature client_properties
```

This release-based derivative was generated successfully; it has its own
unvalidated build/runtime state. The controller does not silently rewrite a
selected released recipe. The final image's ownership correction uses
`COPY --chown=0:0` for its pinned control executable and requires no Linux
capabilities. Its frozen CLI/tooling inputs precede that host recipe correction;
refresh the exact Linux toolchain contract when rebuilding from newer CLI source.


Additional checkpoint (2026-10-12): the unchanged public-8-fixed image also built
and ran `sfm-dev/mc-1.21.1` with Java 21 in a separate bounded, network-free worker.
It compiled 748 main, 276 GameTest and 15 datagen sources, passed the initial and
requested GameTests, produced eight reviewed orbit captures plus one requested
capture, completed managed stop, and exited its JVM with status 0. No runtime
patches were applied. This adds 1.21.1 to the accepted targets; the remaining
runtime matrix is 1.19.4, 1.20, 1.20.1, 1.20.2, 1.20.3, 1.21 (target 1.21.0) and
26.1.2. The three 26.1.2 dependency-readiness blockers remain unchanged.

Storage admission distinguishes VM free blocks from host context/export space.
After authorized image cleanup, this native build and game reused freed blocks
inside the existing VHD; its file length remained unchanged. Put future host
contexts, TEMP/TMP and exports on D/G and monitor actual host free space and VHD
allocation independently. Do not automatically charge the whole VM build peak
to C:. The previous blanket 24 GiB host admission estimate is superseded.


Cache-ready primary-target qualification (2026-10-12): the same immutable
public-8-fixed image has now built and completed managed initial/requested
GameTests, capture requests, clean stop and JVM status 0 for one dev/derived
recipe on each of 1.19.2, 1.19.4, 1.20, 1.20.1, 1.20.2, 1.20.3, 1.20.4,
1.21 (projection target 1.21.0) and 1.21.1. The six additional targets ran
sequentially in one bounded worker. This is nine primary targets, not runtime
acceptance of every one of the 34 catalog release/feature variants. The three
26.1.2 rows remain dependency-blocked and runtime-unaccepted.

All exported captures were checked for PNG CRC and 1280x807 dimensions. Four
representative accepted renders per added target were viewed individually.
Fast initial 1.19.4 and 1.20.3 frames showed incomplete nearby chunk meshes;
they are preserved and excluded from the accepted sets. Eight normal managed
capture requests after the world settled, plus a post-test capture, provide
replacement sets without source/image changes. The original incomplete frames
are not presented as accepted renders.

The worker's cgroup peaked at its fixed 8 GiB limit with memory reclaim and
zero OOM/kill events. Its container exit 137 is the engine stopping PID1 sleep
after all game JVMs had exited 0; it is not a game failure. Existing VHD file
length and allocated size did not grow. There was no further pruning,
compaction, install, commit, push or publication during this qualification.

The remaining Mekanism main JAR matches the output beside a clean checkout of
official commit f33ff1f438caa55d58ef1f0a08091997353afcb8. Matching bytes and
checkout state do not supply an admitted source-producer proof or explicit
trusted-byte approval. The exporter's explicit weak-marker refusal stays in
force. Any admission must be reviewed and scoped to the exact artifact,
coordinate and digest, rather than relaxing the general provenance policy.


Producer proof profile (2026-10-12): `localhost/sfm-support-agent:producer-26-proof-3`, OCI
`sha256:76c7fa4b338801f8f7a2ec42e5ead9163cf888658d9895807066b6448855f466`, is a scoped derivative of the accepted public-8 image.
It has all 34 catalog cache rows ready, including 26.1.2. The fresh Mekanism main/API artifacts were built from upstream
`https://github.com/mekanism/Mekanism/` at `f33ff1f438caa55d58ef1f0a08091997353afcb8`, in a separate bounded nonroot Linux producer,
using the verified Java 25 and Gradle 9.5 distributions. All 11,500 tracked source files were checked unchanged; imported task outputs were excluded.
The final offline pass executed all 11 tasks with `--no-build-cache --rerun-tasks`, producing new strong hashes rather than admitting the old weak artifact.

The existing source-build environment record carries a reserved metadata reference, `SFM_PRODUCER_RECEIPT_SHA256`.
This refers to `producer-receipts/<64 lowercase hex>.json` under the portable Minecraft toolchain cache.
The local execution receipt binds repository/revision, source archive, producer image/container, SDK/distribution, dependency inventory,
build log, recipe/environment, clean-source result, exit code, and both exact output hashes/sizes. It is local build evidence, not an upstream publisher signature.
Container export stages and verifies that receipt. The support controller rechecks it and the actual artifact SHA256 before native build/launch/start;
the existing native CLI independently enforces the lock's canonical BLAKE3 hash. Missing receipts, tampered outputs, contradictory metadata and weak substitution fail closed.

This exact unchanged image and one container sequentially qualified a newly generated `agent-producer-proof/mc-26.1.2` (Java 25)
and the full `sfm-dev/mc-1.19.2` catalog projection (Java 17; 38 mod jars): native builds, two actual GameTests each, managed captures/stops, both JVM exits 0.
All 18 exported PNGs passed CRC/dimension checks;17 captures accepted and four accepted representatives per target reviewed visually.
The initial 1.19.2 frame has incomplete near-field terrain; it is retained and excluded. Receipts are in
`D:/Repos/Minecraft/SFM/support-agent-task2-mekanism-proof/{neo26proof,forge1192proof}/acceptance.json` and `producer-profile-handoff.json`.

The original repository 26.1.2 lock, old host JARs, golden image and public-8 acceptance are preserved.
The strong candidate is packaged only in this explicit profile. Promotion into the canonical repository lock, receipt checking in the shared native engine,
and a fresh current-source Linux CLI pin remain pending coordination. Other primary targets retain their earlier public-8 receipts; the full 34 release/feature runtime matrix is not qualified.
Use the existing public `python3 /opt/sfm/support_agent.py list|select|generate|build|start|run-game-test|capture|stop` commands inside this profile.
All projections means the supported catalog plus valid generated derivatives, with pinned prerequisites; it does not promise arbitrary future Minecraft versions.


Canonical native integration checkpoint (2026-10-12, after the profile checkpoint above): the repository26.1.2 schema4 lock now carries the fresh main/API pins,
the unchanged upstream repository URL and immutable revision, and the digest-bound producer receipt reference. The receipt is preserved alongside the lock.
Only the Mekanism dependency object and its two artifact objects were promoted; all other canonical lock objects remain unchanged.
Native resolution now uses the locked isolated `producer-outputs/<receipt digest>/...` paths, retaining the historical Maven cache bytes.
The common verifier is in `jar_build/source_build_receipt.rs`; both container export and native resolution use it.
Proof is checked before cache-sidecar writes/fallback, before strict prepared-input returns, and for direct cache-plan calls. Implicit refresh and weak substitution are refused.

A freshly built public Windows CLI preserved repository/revision/receipt fields through projection generation and resolved a26.1.2 native dry-run with65/65 portable artifacts.
It rejected five separate provenance mutations; fresh native tests also consumed both real producer outputs and covered missing/corrupt proof, output tampering,
recipe/URL/revision mismatch, unsafe paths, direct-cache bypass and refresh. Resolver regressions13 passed, container regressions38 passed, schema tests33 passed,
the real producer integration test passed, and strict Clippy passed. The test logs and `canonical-native-producer-handoff.json` are under the same D evidence root.
Earlier image/report/acceptance receipts remain historical and were not rewritten. Published energy evidence remains scoped to MC1.21.1 / Mekanism10.7.4.60.
The accepted Linux profile still embeds its frozen historical CLI; a fresh current-source Linux pin/image and the full release/feature runtime matrix remain separate work.


Consolidated current-source support profile (2026-10-13): `localhost/sfm-support-agent:consolidated-current-3`, OCI `sha256:7a13b2372a87975c2e59d7e6e61a8e840ad341b62ed45ef1595c6b534b89418a`, is 6,690,447,999 bytes. It includes a freshly compiled current-source native CLI with the canonical Mekanism receipt verifier, pinned Java 17/21/25, the catalog/generator, shared pinned input caches, the preserved puppet control executable, and Linux `teamy-discord`. Frozen catalog inputs are at `/opt/sfm/catalog-current`; generated sessions are isolated under `/work/agent-sessions`.

Inside a running container, `/usr/local/bin/sfm-support-agent` supports `list`, `select`, `generate`, `status`, `build`, `start`, `launch`, `run-game-test`, `capture`, and `stop`. Select a catalog projection with a fresh session name; generate derives a new dev projection and validates its requested features through the canonical generator. Operations return JSON with explicit dependency/version diagnostics. Per-session locks permit independent selection/generation during another managed game. A separate atomic game-start reservation retains the one-live-game limit.

For example, use `sfm-support-agent generate --session support26 --projection sfm-dev/mc-26.1.2 --name support`, then `build --session support26`, `start --session support26 --seconds 1800`, `run-game-test --session support26 --game-test sfm:move_1_stack_direct`, `capture --session support26`, and `stop --session support26`. Build/start default to `sfm:move_1_stack_direct`. Managed sessions that have already run require a new session name. Keep container CPU/memory/PID limits explicit; acceptance used one CPU, 8 GiB, 512 PIDs, UID 10001, no host mounts, dropped capabilities and no-new-privileges. Offline acceptance used network disabled.

Use `sfm-support-agent discord --help` or `/usr/local/bin/teamy-discord` for the Linux Discord CLI. Final-image offline profile/planning smoke passed. Windows broker acquisition is unsupported on Linux and returns a typed error. No token is baked into the image and no live Discord connection was exercised; runtime token handling remains with the credential boundary.

This exact image qualified a generated `agent-consolidated/mc-26.1.2` projection with Java 25. It compiled 738 main, 268 GameTest and 15 datagen sources, passed two actual GameTests, completed capture/stop requests and exited the JVM with code zero. Nine exported PNGs passed integrity checks; eight were accepted, with four accepted representatives reviewed visually. The first frame is a loading screen and is retained but excluded. See `D:/Repos/Minecraft/SFM/support-agent-task2-consolidated/current26/acceptance.json`.

Performance remains a material limitation: initial / unchanged / one-Java-edit build preparation measured 321.779 / 325.005 / 328.148 seconds at one CPU. NeoForm replay dominates; ANTLR reuse works. The first capture file appeared after 388.422 seconds and was not usable gameplay evidence. Managed readiness took 398.423 seconds. Do not treat the first-file measurement as first-usable latency. The Java edit probe was restored byte-for-byte before gameplay.

`cache-reuse-findings.md` under the same evidence root describes the fresh-only native producer contract and required separate authenticated derived-cache authority. No shortcut that replays fresh receipts or skips provenance checks was added. Fast warm/no-op and Java-only reuse are still unfinished.

Catalog enumeration and cached-file presence do not qualify every runtime combination. Supported catalog entries and generator-valid derivatives are the scope; arbitrary future versions are not promised. Earlier public-8 nine-primary-target receipts and the producer-profile 1.19.2/26.1.2 receipts remain historical. Published energy evidence stays scoped to MC 1.21.1 / Mekanism 10.7.4.60. Existing accepted JARs, images, dirty work and report evidence are preserved.

The same unchanged image and container then qualified `sfm-dev/mc-1.21.1` with Java 21: 748 main, 276 GameTest and 15 datagen sources; 81/81 portable inputs; two passing actual tests (601 ms / 1000 ms); eight accepted captures from nine integrity-checked PNGs; four accepted representative views; clean stop and JVM exit zero. Its first file was also a loading screen and is excluded. See `current1211/acceptance.json`. Both sequential sessions are terminal; no concurrent game was launched.
