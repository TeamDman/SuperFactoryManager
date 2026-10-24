# Published consumer and partial live Terminal qualification

The reviewed SFM branch was pushed normally, without force, to
[`task/parchment-handshake-20261023`](https://github.com/TeamDman/SuperFactoryManager/tree/task/parchment-handshake-20261023).
The first published head was `103d8bff2bf1f17e3c08a2076c7b23ff0aaba052`, containing
all eight previously unpublished commits since the migration checkpoint
`0918604ac558906c93a9c4ab449400e8f75792ce`. Remote readback matched that head;
remote main remained `e2ba833b2b235835d69475b641e24b22bfd9551a`. This report is a
subsequent evidence-only continuation, not a new SFM runtime implementation.

## Publication review

All outgoing historical blobs were inspected, including files later replaced
and decompressed gzip evidence. The initial unpublished history contained 154
unique blobs totaling 5,055,533 bytes. The largest stored blob was 581,951 bytes;
all gzip expansions were readable text. No credential candidates, signed
redirect parameters, private keys, launch credential values, or opaque binary
payloads were found by the recorded pattern checks and launch-receipt review.
Only the approved Cargo trust patch, Terminal consumer/test/changelog changes,
and task documentation/evidence were outgoing. SDKs, mapping ZIPs, built JARs,
executables, raw authentication descriptors, and Xauthority files are not in the
commit range. No history rewrite, merge, or package publication was performed.

## Verified sources and builds

- SFM runtime implementation remains `f9b3e7e5737967d5f40aed1f6dfd187af8f7db8b`;
  its compiled 1.19.2 consumer classes from the successful full Java aggregate
  were used directly in the live missing-shell probe. No Minecraft instance or
  GPU/display was launched.
- Terminal branch
  [`task/terminal-input-readiness-20261024`](https://github.com/TeamDman/teamy-terminal/tree/task/terminal-input-readiness-20261024)
  was fetched and verified at `44aa0a052d24615f85d475e14874918a0090d795`, containing
  tested implementation `63a03c0a3f43e541860b7a31597d44e4bbba2c69`. The checkout
  `/workspace/sfm-terminal-live-20261024/teamy-terminal` remains clean.
- Vox fix branch
  [`task/malformed-first-response-schema-20261024`](https://github.com/TeamDman/vox/tree/task/malformed-first-response-schema-20261024)
  was fetched and verified at `86b218a192fbe10d59377a715cee039022214cb7`, containing
  implementation `e469b86cdbab46502b913fb2af48dfacbd8e26f0`. Its checkout remains
  clean. The macro parser and dependency manifests are unchanged from the
  production Vox pin.
- Production Terminal and SFM retain Vox
  `5628c00d9f3816199f324a9ccc081de8a3a1b1bc`, facet-format
  `642d268fc01eced0a861bded044342ff88733eec`, and all existing lock versions.
  Terminal's additional locked Git input is teamy-cancellation
  `7c77848e759079e21f2039b25b147d780807725b`.
- Exact checked-in locked inputs missing from the local cache were restored with
  `CARGO_NET_RETRY=0 cargo fetch --locked --target x86_64-unknown-linux-gnu` for
  both the prototype manifest and the main workspace. These have distinct
  checked-in lock graphs (for example, arboard 3.7.0 versus 3.6.1); neither was
  normalized or upgraded. All subsequent builds/tests used `--locked --offline`.
- Existing Rust/Cargo 1.99.1 and Temurin 17.0.20.1+1 were used. The producer's
  earlier report used Rust 1.96.0; this task does not claim byte reproduction of
  its server. The unchanged Java runtime JAR is 614,530 bytes, SHA256
  `be441a57e11d4981d283306fe471874e625bf2f423c83a91ce8f44dde623d062`.

The frozen server built successfully using
`cargo build --locked --offline --manifest-path tools/vox-terminal-server/Cargo.toml`
and passed `--help`. Retained binary:
`/workspace/sfm-terminal-live-20261024/bin/terminal-frozen`, SHA256
`261b9ca5a4871503d6abbaaaa75a919083be8ebdccc0a5e981e133cb44322dea`.
Existing compiler warnings were recorded, not fixed through unrelated source or
dependency changes.

## Executed checks

| Gate | Exact result and scope |
| --- | --- |
| Terminal `shell_input` | 4 passed, 0 failed; supplied monotonic times exercise startup deadline, closure and session independence |
| Terminal `startup_tests` | 5 passed, 0 failed; controlled real `/bin/sh` PTYs exercise rejection, repeated accepted sends, child exit, cancellation, timeout and session/connection isolation; the fixture injects the readiness marker, so this is not a real PowerShell-hook result |
| Actual SFM consumer to actual frozen Rust server | Passed the **missing-shell failure** assertion: an accepted paste completes exactly once as structured `FAILED`/`DISCONNECTED`, retryable=false, no confirmation content; closing afterward does not duplicate completion |
| Frozen Vox malformed-first probe | Expected failure reproduced: INVALID_PAYLOAD is decoded first, then the following typed missing-session response fails with `method payload schema is incompatible`, caused by `trailing bytes: 84` |
| Isolated fixed-macro overlay | Passed the same schema-only probe: INVALID_PAYLOAD followed by two typed SESSION_NOT_FOUND replies on the same open connection/lane |

The real SFM failure probe reached Terminal's PTY creation and observed:

```text
failed to spawn the configured shell in the pseudoterminal:
Unable to spawn pwsh.exe ... No viable candidates found in PATH
```

This proves the connection-failure/paste completion path through real SFM Java
and Rust Terminal. It does **not** establish a successful Terminal session or
the startup `INVALID_REQUEST && retryable=true` -> same-session success path.
Those remain blocked as described below. The earlier 2,570-pass SFM aggregate
and controlled consumer tests remain separately attributed in the
[review report](vox-terminal-readiness-review-20261024.md).

## Separate Vox overlay boundary

The scratch Terminal worktree `/workspace/sfm-terminal-live-20261024/terminal-overlay`
uses two command-line Cargo path patches to the fetched Vox source:
`teamy-vox-macros-core` and its unchanged `teamy-vox-macros-parse` dependency.
Its scratch prototype lock differs by exactly two removed Git source lines;
the full retained package graph, versions, dependencies and checksums match.
The original Terminal and SFM manifests/locks are unchanged. The scratch lock
is intentionally local overlay state, not a production repin or a published fix.

Retained overlay binary:
`/workspace/sfm-terminal-live-20261024/bin/terminal-overlay`, SHA256
`6f9921e3d41b6877e7c11fffc8975d9c76f0e593f941b08e6fbe82efea255d27`.
The focused Java probe never creates a PTY; its two response checks qualify
`schema.tracking.bindings` and `rpc.error.scope` without pretending the missing
PowerShell requirement has been satisfied. Tracey tooling is unavailable here;
the canonical spec sections were read directly, not machine-audited.

## Remaining blocker and operational handoff

**Pending prerequisite, not an approval denial:** the production PTY default in
`crates/teamy-terminal-pty/src/lib.rs` is `pwsh.exe`, and the production server
uses `PtyConfig::interactive()`. No existing PowerShell executable was found in
PATH or the inspected installed runtime locations. The reviewed Terminal runs
used **Linux x64 PowerShell 7.5.4**, exposed as `pwsh.exe`. Installing or provisioning
that runtime is a new dependency action outside the current approval. It was
not attempted, and no substitute shell or altered production readiness gate
was used to bypass it.

After approval/provisioning, remaining live checks are startup input/paste
rejection before forwarding, same-session recovery and transient clearing,
guarded/approved paste, cancellation, stream retention and session isolation
against the real PowerShell hook. The built frozen server and pinned Java/SFM
artifacts are retained for that continuation. No reason arose to repeat the
unrelated already-qualified Minecraft `sfm:echo` run.

All server processes were bound to loopback, bounded, and cleanly stopped by
the probe supervisor. CPU tests exited. No game, display, server or test process
is intentionally left running. Builds used two Cargo jobs in the existing
four-CPU/16-GiB environment; the frozen build had a 20-minute limit, the overlay
15 minutes, and each Java probe 45 seconds plus five-second server shutdown.

No SDK/toolchain installation, security/network configuration change, production
dependency upgrade, desktop/GPU action, main merge, or package publication
occurred. Installer responsibility: **no SFM install.ps1 run is required**;
the native launcher and production Java implementation are unchanged. The
separate PowerShell prerequisite remains explicit.

[Evidence](vox-terminal-live-qualification-20261024-evidence/) includes source,
binary and lock identities; publication review/readback; exact commands; the
two Java probes and supervisor; full compressed build/test logs; overlay graph
audit; and per-run exit/cleanup receipts. Raw task data and binaries remain in
`/workspace/sfm-terminal-live-20261024`.
