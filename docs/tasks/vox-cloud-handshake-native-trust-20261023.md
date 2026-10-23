# Native trust and real Minecraft control qualification — 2026-10-23

**PASS:** the freshly built Rust control client connected to the actual
Minecraft 1.19.2 Java control service and completed `sfm:echo`. The game reached
`net.minecraft.client.gui.screens.TitleScreen`. This closes the acquisition,
producer and real-game handshake gates left open in the
[initial qualification](vox-cloud-handshake-qualification-20261023.md) and
[published cutover handoff](vox-cloud-consumer-cutover.md).

The only implementation change is the specifically approved two-file native
root selection patch. All task-owned game/display processes have stopped.
There are no remaining blockers for this bounded qualification.

## Source and executable identities

Task branch: `task/parchment-handshake-20261023`. Requested SFM base:
`0918604ac558906c93a9c4ab449400e8f75792ce`. The approved patch was committed as
`2e8e6141dd2a339289ef91a1781c2a63cecfcfac`; that is the native launcher build and
game launch source checkpoint. Later evidence commits change only documentation.
Minecraft, Rust control, generated authored inputs and their locks are unchanged
from the requested base. `origin/main` remains
`122e4b31ea2875a0894d648c51c5ee5f78c589e6`; no local main was created.

| Component | Exact identity |
| --- | --- |
| Native launcher | `tools/sfm-propagate-changes-native`; SHA256 `7e7fb12ed75251ff9c48b61467793ee94d44e1aab528774a00b93acea74476ff` |
| Launcher version | 0.1.1, revision `2e8e6141dd2a339289ef91a1781c2a63cecfcfac`, built 2026-10-23 15:37:41 UTC |
| Rust control client | `tools/sfm`; SHA256 `1ec4f40fbf21640f567f49dfb6dd5c58cd144e7be12070b805f66e744dad1ef4` |
| Vox source | `5628c00d9f3816199f324a9ccc081de8a3a1b1bc` |
| facet-format source | `642d268fc01eced0a861bded044342ff88733eec` |
| Vox JAR | `teamy-vox-java-0.10.0-rc.7.jar`, 614,530 bytes, SHA256 `be441a57e11d4981d283306fe471874e625bf2f423c83a91ce8f44dde623d062` |
| Producer SDK | Approved Temurin `Eclipse Adoptium\|21.0.8+9-LTS`, Linux x64 |
| Game/build SDK | Existing Temurin 17.0.20.1+1; native SDK identity `sha256:ff39c728235ea0dc28c1b1609890cb88a38a9eea4647a1a451444a4e76949ffa` |
| Game | Minecraft 1.19.2, Forge 43.4.0, SFM 4.34.0, projection `sfm-dev/mc-1.19.2` |
| Parchment | `2022.11.27-1.19.2`; first 20 BLAKE3 bytes `75697f5f30848c78fed5ade00ca26f3acc19e7d6` |

Tool paths above are relative to `/workspace/sfm-handshake-20261023/`.
The initial report records the verified HTTPS acquisition chains, byte counts,
full archive hashes, ZIP checks and deterministic Vox producer tests. Those
verified bytes remain in the task and canonical caches. The exact Vox JAR was
also rehashed from the **actual launched Java classpath**, as recorded in
[actual-launch-vox.json](vox-cloud-handshake-native-trust-20261023/actual-launch-vox.json).

## Approved trust change and pre-network audit: PASS

Applied exactly the existing environment patch, SHA256
`dc91a5cbcd6d957dae6e2365a3025343c64f001a914c7eab1b202c1fb699b2f9`:

- `platform/cli/sfm-propagate-changes/Cargo.toml`: replace Reqwest feature
  `rustls-tls` with `rustls-tls-native-roots`; retain `default-features = false`,
  `blocking` and `multipart`.
- Its `Cargo.lock`: remove only unused `webpki-roots 1.0.6`; replace the
  Reqwest 0.12.28 and hyper-rustls 0.27.7 root dependency edges with the already
  present `rustls-native-certs 0.8.3`. Package count 591 → 590. **No packages
  added; no retained versions, sources or checksums changed.**

The [graph receipt](vox-cloud-handshake-native-trust-20261023/approved-patch-verification.json)
records the complete package/edge comparison. The Linux normal/build graph
contains neither `webpki-roots` nor `webpki-root-certs`. The fresh executable
passes the existing environment audit and contains none of the four excluded
emSign root public keys: ECC C3, ECC G3, C1 and G1, issued by eMudhra Inc or
eMudhra Technologies Limited. Their full certificate fingerprints and binary
results are in the
[audit receipt](vox-cloud-handshake-native-trust-20261023/launcher-native-trust-audit.json).

An offline probe linked against the existing `rustls-native-certs 0.8.3`
loaded exactly the existing filtered store's 150 roots with no loader errors;
its fingerprint set equalled the filtered bundle. All four exclusions were
absent. OpenSSL rejected each excluded root and accepted the positive-control
ISRG Root X1. The original system store has 154 roots; no roots were added.
The filtered bundle's SHA256 remained
`ee7d8a9353d49d866898ce0d49091bcdf207d71e323ebfe0af9d2020e691c99c`.

The trust source is the pre-existing `SSL_CERT_FILE`/`SSL_CERT_DIR` configured
by `/workspace/.sfm-env/trust-env.sh`, not an appended public bundle. The native
loader uses those explicit paths without system fallback. Reqwest's normal
certificate and hostname verification remains enabled; no bypass API or
environment security setting changed. See
[trust-exclusion-tests.json](vox-cloud-handshake-native-trust-20261023/trust-exclusion-tests.json)
and the retained probe source/output. This tests the saved local exclusion
policy, not a claim that these roots are globally revoked or compromised.

The launcher was rebuilt **after** the patch, before network preparation:

```sh
source /workspace/.sfm-env/env.sh
SFM_PROPAGATE_CHANGES_INSTALL_GIT_REVISION=2e8e6141dd2a339289ef91a1781c2a63cecfcfac \
  timeout 1200 cargo build --locked --offline --bin sfm-propagate-changes \
  --manifest-path platform/cli/sfm-propagate-changes/Cargo.toml
```

Build passed in 41.86 seconds with existing warnings. The resulting binary was
copied to the task's `tools/sfm-propagate-changes-native` and audited there.
The old installed launcher and initial bundled-root task binary were not used
for network preparation or this launch.

## Native Minecraft build and acquisition: PASS

The synchronized projection retained context identity
`blake3:ef1e2120e5790c9bc34de8b2a2be2c21bd935e792121346e9eae148698095471`.
Native preparation acquired 42 locked Maven artifacts with no TLS/hash failure
or denied host. The launch acquired and checked all 3,364 unique asset objects
represented by 3,390 entries in the official 1.19 asset index. No network rules
were changed, no alternate mirror was substituted, and no Gradle was used.

With the approved SDK21 in the parent environment for the Vox producer and
the existing SDK17 selected for Minecraft:

```sh
export JAVA_HOME=/workspace/sfm-handshake-20261023/sdk/jdk-21.0.8+9
export PATH="$JAVA_HOME/bin:$PATH"
export VOX_JAVA_BUILD_JDK='Eclipse Adoptium|21.0.8+9-LTS'
export CARGO_NET_RETRY=0 RAYON_NUM_THREADS=1
timeout --signal=INT --kill-after=20s 1800 \
  /workspace/sfm-handshake-20261023/tools/sfm-propagate-changes-native \
  --output-format json --log-filter info --log-file /path/to/fresh-build.ndjson \
  source project jar --repo-root /workspace/SuperFactoryManager \
  --projection sfm-dev/mc-1.19.2 --dependency-profile rust-toolchain \
  --java-home /workspace/.sfm-env/jdk17
```

The native source checks, compilation of 1,573 Java sources, remapping and JAR
packaging completed successfully in 354,124 ms. The packaged JAR is 9,224,594
bytes, SHA256
`f9c7994dca1b8f34e79d9362a4cf9245d54e020cc56c140e21950bc7e25f1139`, with passing
ZIP integrity. Its path and control class hashes are in
[minecraft-built-jar.json](vox-cloud-handshake-native-trust-20261023/minecraft-built-jar.json).

A first preparation attempt stopped because this task injected
`JAVA_TOOL_OPTIONS` for resource limits and the SDK's clean-environment check
rejected it. Those task-added options were removed; the normal check then
passed. There was no skipped SDK/source check or trust relaxation. Limits were
the existing 16 GiB/4 CPU container, sequential preparation, and bounded wall
times. Full failed-attempt diagnostics remain in the external continuation
directory as `native-jar.*`; the successful build is `native-jar-clean-env.*`.

## Actual game payload and Rust RPC: PASS

The runtime used the named development classes in session `session-4FBh8m`,
snapshot `snapshot-G2p4Z8`, rather than the separately packaged reobfuscated
JAR. Snapshot receipt SHA256:
`5dc80a2188530a3d84abe467358e06620141c6d7320b83d31ec989f3f099aa03`.
The actual session mod directory has 4,869 files / 25,393,591 bytes; its sorted
`{path,bytes,sha256}` compact JSON inventory has SHA256
`0af5ca1fd088222db49ea3b9afdd4700d65c5e320218dc5853fb01008d87fd2d`.
Post-run verification confirmed the entire payload and snapshot receipt were
unchanged. These inventory hashes identify the runtime directory, not a JAR.

Existing Xorg with its dummy driver provided authenticated local display `:93`,
1280×720, with TCP disabled. This task independently verified Mesa llvmpipe
(LLVM 19.1.7, 256 bits), OpenGL 4.5 Compatibility, Mesa 25.0.7. No desktop,
Xvfb installation, driver installation or system display setting was involved.

The bounded supervisor invoked:

```sh
/workspace/sfm-handshake-20261023/tools/sfm-propagate-changes-native \
  --output-format json --log-filter info --log-file /path/to/fresh-game.ndjson \
  run client --repo-root /workspace/SuperFactoryManager \
  --projection sfm-dev/mc-1.19.2 --java-home /workspace/.sfm-env/jdk17 \
  --require-portable-artifacts
/workspace/sfm-handshake-20261023/tools/sfm --output-format json instance list
/workspace/sfm-handshake-20261023/tools/sfm --output-format json action invoke \
  --instance-id 58cb88d2-1184-4b1b-bc54-aa11d71a44db \
  sfm:echo cutover-check-1792771086428394851
```

The supervisor gave launch/readiness 1,200 seconds and RPC 30 seconds, used
isolated task XDG directories, and verified the responding Java PID belonged
to its own launched process group. The readiness/RPC sequence was:

| Evidence | Result |
| --- | --- |
| 15:58:05 UTC game log | `SFM_CLIENT_CONTROL_READY`, instance `58cb88d2-1184-4b1b-bc54-aa11d71a44db`, PID 11444, endpoint `127.0.0.1:45087` |
| Rust discovery | One live compatible instance; Minecraft 1.19.2 / SFM 4.34.0; actual TitleScreen |
| Request ID | `sfm-cli-11581-1792771086573564918` |
| Rust process exit | 0 |
| Action result | `result_code: 1`, feedback `echo: cutover-check-1792771086428394851` |
| Identity match | Response instance and PID match the actual launched Java process |

See the complete [RPC response](vox-cloud-handshake-native-trust-20261023/echo.json),
[live instance](vox-cloud-handshake-native-trust-20261023/live-instance.json),
[supervisor receipt](vox-cloud-handshake-native-trust-20261023/handshake-result.json)
and [game log excerpt](vox-cloud-handshake-native-trust-20261023/game-console-key-lines.txt).
This is an actual Minecraft Java service response, not the generated Java TCP
fixture or the prior 1.21.1 GameTests.

## Cleanup, scope and manual handoff

After the response, the supervisor sent SIGTERM to its verified Java PID
11444. The JVM exited 143; the native launcher consequently returned 1. Those
are intentional shutdown results **after** the successful RPC, not a failed
handshake. No force-kill was needed. Xorg PID 9489 received SIGTERM and exited
0; its socket, lock and task authentication cookie were removed. The runtime
session is inactive, the control port has no listener, Rust discovery reports
zero live instances, and neither owned process group remains. The checked
runtime payload, build outputs, downloads and caches were preserved.

- **Passed:** pinned Parchment, approved SDK identity/checksum, exact Vox JAR
  reproduction, approved lock-graph comparison, fresh native-only TLS audit,
  projection sync/check, native 1.19.2 build, real game startup, Rust discovery
  and unique `sfm:echo`, complete process cleanup, final dependency/source diff.
- **Recovered diagnostic:** the task-added Java options described above.
  The launcher's final nonzero status is intentional game shutdown.
- **Unrun:** broader Rust test suites, rustfmt/Clippy, PowerShell
  `check-all.ps1`/`install.ps1`, other Minecraft versions, GameTests, world/server
  gameplay and manual visual inspection. No previous task's results are claimed
  as reruns here. This single successful action is not exhaustive RPC coverage.
- **User must run install script: no for this task's handoff.** The rebuilt,
  audited task-local executable is used by absolute path. The global installed
  tool remains untouched; its freshness is not claimed. PowerShell was not used.
- Dependency posture remained frozen except the exact approved manifest/lock
  change and exact SDK installation. No retained dependency version was upgraded;
  no unrelated security setting, network rule, desktop, push, merge or publication
  was changed. There is no unstarted stretch item required by this scope.

To repeat in this retained cloud workspace, keep the source/platform tree at
the checkpoint above (documentation-only successors are equivalent), source
`/workspace/.sfm-env/env.sh`, and export the producer variables shown earlier.
Start from no task game/display. Use a fresh evidence directory: the saved
`run_display.py` and `run_handshake.py` are the exact scripts used here, but their
original output directory must not be reused. A copyable preparation is:

```sh
export SFM_REPLAY_DIR=$(mktemp -d /workspace/sfm-handshake-20261023/replay.XXXXXX)
python3 - <<'PY'
import os
from pathlib import Path
src = Path('/workspace/SuperFactoryManager/docs/tasks/vox-cloud-handshake-native-trust-20261023')
dst = Path(os.environ['SFM_REPLAY_DIR'])
for name in ['run_display.py', 'run_handshake.py', 'xorg-dummy.conf', 'glx_probe.py']:
    text = (src / name).read_text()
    text = text.replace("Path('/workspace/sfm-handshake-20261023/continuation')", 'Path(' + repr(str(dst)) + ')')
    text = text.replace("out = root / 'continuation'", 'out = Path(' + repr(str(dst)) + ')')
    (dst / name).write_text(text)
PY
```

Run `python3 "$SFM_REPLAY_DIR/run_display.py"` in one terminal; it is bounded
to 30 minutes. In a second terminal with the same `SFM_REPLAY_DIR`, filtered
environment and producer exports, verify display readiness and run:

```sh
DISPLAY=:93 XAUTHORITY="$SFM_REPLAY_DIR/display.Xauthority" \
  LIBGL_ALWAYS_SOFTWARE=1 python3 "$SFM_REPLAY_DIR/glx_probe.py"
python3 "$SFM_REPLAY_DIR/run_handshake.py"
cat "$SFM_REPLAY_DIR/handshake-result.json"
```

Expected result: `PASS: Rust sfm:echo against actual Minecraft 1.19.2 Java
service`, then no remaining owned game processes. The supervisor generates a
new instance ID/nonce and shuts down its game automatically. Stop the verified
Xorg PID recorded in that replay's `display-process.json` with SIGTERM after
the run and wait for its runner; it also has its own 30-minute bound. There is
no required manual gameplay action. Readiness and RPC evidence are captured
automatically; visual/gameplay inspection is outside this qualification.

All detailed raw logs remain under
`/workspace/sfm-handshake-20261023/continuation/`. The accompanying evidence
directory contains receipts, scripts, and deterministic gzip copies of the
full successful build, launch and game console logs. Its
[manifest](vox-cloud-handshake-native-trust-20261023/evidence-manifest.json)
records stored and uncompressed SHA256 hashes. Raw authentication descriptors,
Xauthority credentials and signed redirect URLs are excluded.
