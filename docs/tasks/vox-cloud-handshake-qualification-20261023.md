# Parchment acquisition and Vox producer qualification — 2026-10-23

Parchment acquisition **passed** in this task. The exact approved Temurin SDK
was installed in the task workspace, and the pinned Vox Java JAR reproduced
exactly. **Minecraft compilation, launch and the Rust-to-Minecraft handshake
remain unrun** because the fresh launcher conflicts with the environment's
existing CA distrust policy. This is not a network denial or a failed handshake.

## Source and scope

- SFM source: `0918604ac558906c93a9c4ab449400e8f75792ce`, fetched from
  `TeamDman/SuperFactoryManager` branch
  `migration/vox-sfm-cloud-cutover-20261023`.
- Task branch: `task/parchment-handshake-20261023`. The existing
  `origin/main` remains `122e4b31ea2875a0894d648c51c5ee5f78c589e6`;
  no local `main` branch existed at task start.
- Requested environment version:
  `7d196139-cf87-422a-8f2b-69aebb9c4645~cecfgver_6adb7526433081998b1cfd90869595d9`.
  This records the supplied version, not an independently queried platform ID.
- Read `docs/AGENTS.md`, goal execution/readiness guidelines, the published
  `vox-cloud-consumer-cutover.md` handoff, environment setup/trust instructions,
  and pinned Vox `AGENTS.md`. No checkout `SKILL.md` files were found in SFM
  or pinned Vox. No implementation code was changed.
- Dependencies frozen: all SFM Cargo manifests, Cargo locks and Minecraft
  locks are unchanged. Exact locked Cargo inputs were rehydrated; no version
  upgrade, SDK beyond the authorized Temurin pin, settings change, desktop
  action, push, merge or publication occurred.

## Parchment: PASS

Coordinate: `org.parchmentmc.data:parchment-1.19.2:2022.11.27@zip`.
Source: <https://maven.parchmentmc.org/org/parchmentmc/data/parchment-1.19.2/2022.11.27/parchment-1.19.2-2022.11.27.zip>.

| Check | Result |
| --- | --- |
| HTTPS chain | `maven.parchmentmc.org` 302 → `ldtteam.jfrog.io` 302 → `storage.googleapis.com` 200 |
| TLS | curl verification result 0 at every hop, filtered CAfile and CApath |
| Bytes | 712,951 |
| SFM content pin | `blake3:75697f5f30848c78fed5ade00ca26f3acc19e7d6` — exact match |
| Full SHA256 | `52cfb5f6c62c04a9cc870a8eaa937f3952b5076529400c633d0248217812ec9c` |
| ZIP integrity | PASS; `parchment.json` passes decompression/CRC checks |

The content pin is the **first 20 bytes of BLAKE3 XOF over the entire ZIP**.
The freshly built SFM `hash file --algorithm blake3 --length 20` performed the
verification. It is neither SHA1 nor a full 32-byte BLAKE3 comparison.
Redirects were checked individually against the three authorized hosts; there
were no retries or alternate mirrors. Signed URLs and raw redirect headers
were held in memory and not saved in evidence or printed.

Verified bytes remain at both:

- `/workspace/sfm-handshake-20261023/downloads/parchment-1.19.2-2022.11.27.zip`
- `/workspace/.sfm-env/cache/minecraft-toolchain/maven/org/parchmentmc/data/parchment-1.19.2/2022.11.27/parchment-1.19.2-2022.11.27.zip`

## Approved SDK and Vox JAR: PASS

Temurin was downloaded from the exact approved GitHub release archive over
verified HTTPS, following only GitHub and `release-assets.githubusercontent.com`.
The [official expanded-assets row](https://github.com/adoptium/temurin21-binaries/releases/expanded_assets/jdk-21.0.8%2B9)
was independently downloaded and checked for the exact archive name and SHA256.

- Archive: `OpenJDK21U-jdk_x64_linux_hotspot_21.0.8_9.tar.gz`, 207,098,019 bytes.
- SHA256: `f2dc5418092c43003db8f9005c4a286e1c0104fea96ccdd49e8ebd037cac9219`.
- Extracted release identity: `Eclipse Adoptium|21.0.8+9-LTS`, Linux x86_64.
- Full SDK: `/workspace/sfm-handshake-20261023/sdk/jdk-21.0.8+9`;
  both `java -version` and `javac -version` passed.
- Vox source: `5628c00d9f3816199f324a9ccc081de8a3a1b1bc`, unchanged tracked files.
- Build: `cargo run --locked --package teamy-vox-xtask -- package-java`, with
  `JAVA_HOME` pointing to that SDK and
  `VOX_JAVA_BUILD_JDK='Eclipse Adoptium|21.0.8+9-LTS'`.
- Existing Rust: `1.99.1 (58cf8f9c3 2026-10-13)`.
- JAR: `teamy-vox-java-0.10.0-rc.7.jar`, **614,530 bytes**.
- SHA256: `be441a57e11d4981d283306fe471874e625bf2f423c83a91ce8f44dde623d062`.
- SFM pin: `blake3:4ecea4778148a5b04dd5e2c6c0e970bfbe97bc4c`.
- Packaging passed codegen drift, 1,934 schema assertions, 86 Phon conformance
  assertions, stream framing, runtime and generated-response tests, repeated
  deterministic JAR assembly, fresh consumer smoke and dependency closure.
  ZIP integrity passed; `jdeps --multi-release 17 --print-module-deps` reports
  only `java.base`.

The first offline package attempt stopped at a missing locked `rustls-pemfile`
index entry. A subsequent locked build restored the required graph through
the configured filtered-CA Cargo path, with `CARGO_NET_RETRY=0`, then passed.
The Vox lockfile was unchanged.

The JAR remains in the task `downloads/` directory and its canonical SFM cache
path under `maven/org/facet/teamy-vox-java/0.10.0-rc.7/`.

## Fresh tools, projection and blocker

Native launcher build completed in 41.33 seconds with the existing warnings:

```sh
source /workspace/.sfm-env/env.sh
SFM_PROPAGATE_CHANGES_INSTALL_GIT_REVISION=0918604ac558906c93a9c4ab449400e8f75792ce \
  cargo build --locked --offline --features reqwest/rustls-tls-native-roots \
  --bin sfm-propagate-changes \
  --manifest-path platform/cli/sfm-propagate-changes/Cargo.toml
```

The task-local executable is
`/workspace/sfm-handshake-20261023/tools/sfm-propagate-changes`, SHA256
`9bc34b8df215aee5b2e98dbf9dc36b03741ce78c914ee73aea0a3e1c58debf11`.
It reports the full requested source revision, built 2026-10-23 15:05:31 UTC.
It was used only for help/version, local hashing and source projection operations.

`sfm-dev/mc-1.19.2` sync and subsequent drift check passed. The context identity
is `blake3:ef1e2120e5790c9bc34de8b2a2be2c21bd935e792121346e9eae148698095471`.
This source-generation evidence does not establish Minecraft compilation.

The Rust control client also built with `cargo build --locked --offline
--manifest-path platform/cli/sfm/Cargo.toml --bin sfm`. Its executable is
`/workspace/sfm-handshake-20261023/tools/sfm`, SHA256
`1ec4f40fbf21640f567f49dfb6dd5c58cd144e7be12070b805f66e744dad1ef4`.
Its graph uses the specified Vox commit and facet-format
`642d268fc01eced0a861bded044342ff88733eec`. Help and `instance list` passed;
the latter reported zero live instances. No fixture test was substituted for
the actual Minecraft handshake.

**Blocked action:** network-capable native preparation and `run client` for
`sfm-dev/mc-1.19.2`, followed by Rust `sfm:echo` against the Java game service.
**Reason:** the pinned native launcher's manifest enables Reqwest `rustls-tls`.
Adding `rustls-tls-native-roots` unions the trust sources; it leaves
`webpki-roots 1.0.6` and all four environment-distrusted emSign/eMudhra public
keys in the new executable. The feature tree and binary-key audit confirm this.
The saved `/workspace/.sfm-env/CA-DISTRUST.md` policy explicitly requires
native roots only and fails closed for unsafe CLI network operations.

**Status: pending authorization**, not denied by a host, TLS warning or
automatic approval review. No network-capable command was attempted with the
fresh launcher. The existing environment remediation patch applies cleanly
(`git apply --check` exit 0) and is retained as
`vox-cloud-handshake-qualification-20261023/required-native-ca-only.patch`.
It replaces the manifest feature with native roots and adjusts only the lock's
feature edges/removes the unused WebPKI-root package; it upgrades no version.
It is **unapplied** because this task explicitly prohibits manifest/lock edits.

After that narrow boundary is authorized and remediated, rebuild and verify
the new launcher's trust audit, hash and source revision before continuing.
Use the exact producer SDK in the parent environment and the existing JDK17
for the 1.19.2 game. The intended launch command remains:

```sh
export JAVA_HOME=/workspace/sfm-handshake-20261023/sdk/jdk-21.0.8+9
export PATH="$JAVA_HOME/bin:$PATH"
# Requires the remediated, freshly audited launcher and a qualified dummy display.
/workspace/sfm-handshake-20261023/tools/sfm-propagate-changes run client \
  --repo-root /workspace/SuperFactoryManager --projection sfm-dev/mc-1.19.2 \
  --java-home /workspace/.sfm-env/jdk17 --require-portable-artifacts \
  --control-cli-source-root /workspace/SuperFactoryManager \
  --log-filter info --log-file /workspace/sfm-handshake-20261023/evidence/game.ndjson
/workspace/sfm-handshake-20261023/tools/sfm --output-format json instance list
/workspace/sfm-handshake-20261023/tools/sfm action invoke sfm:echo cutover-check
```

Do not execute the retained unremediated launcher for this launch. The initial
cache inventory still contains missing Minecraft inputs; later acquisition
and display/launch success are unproven. Xorg and the dummy driver are present,
but no display was started and no GL capability is claimed for this task.

## Operational readiness and evidence

- User must run install script: **no** for completed hashing/producer work;
  task-local fresh executables were built and smoke-checked. Game readiness
  remains blocked, and the old installed launcher was not overwritten.
- No Rust/Minecraft source or generated authored input was edited; the only
  final repository changes are this report and evidence. Generated development
  projection output is ignored. PowerShell installer/check-all, rustfmt/Clippy,
  broad SFM tests, GameTests and game handshake are skipped in this task.
- All task-owned build/Java processes have exited. No game/display process
  started; no cleanup kill or restart was necessary. There are no running
  task-owned helpers or live game instances to inherit.
- Full task logs, downloaded bytes and binaries remain in
  `/workspace/sfm-handshake-20261023/`. Small receipts, build transcripts, source
  hashes, a sanitized downloader and the unapplied remediation patch accompany
  this report. `evidence-manifest.json` records full retained evidence hashes.
- This documentation/evidence commit is a reporting successor, not a new
  runtime build identity. Main is preserved; there is no push or publication.
