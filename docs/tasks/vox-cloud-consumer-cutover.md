# SFM standalone Vox consumer cutover — cloud checkpoint

**Later qualification:** the mapping/CA prerequisites and actual 1.19.2
Rust-to-Minecraft handshake described as pending in this historical handoff
were completed in a separate task. See the
[2026-10-23 qualification report](vox-cloud-handshake-native-trust-20261023.md)
for its exact approved trust patch, runtime identities, successful `sfm:echo`
and cleanup evidence. The original checkpoint results below are unchanged.

Base: `capture/sfm435-issue610-cloud` at
`8e349578d18511d8d5993b66a3ad3c7c957e5db4`. Work is isolated on
`migration/vox-sfm-cloud-cutover-20261023`; no public push, merge, package
publication, desktop update or network-policy change is included.

The runtime-tested source checkpoint is
`9857bca7b2f8b5b2ef917092a8d6f4c190e2075b`. This documentation-only follow-up
records the provenance clarification; its commit is a report revision, not a
newly built or game-tested runtime revision. The GameTests ran in the working
tree subsequently committed at that checkpoint, with the same Java inputs.
The final native launcher was rebuilt after the source commit, as distinguished
below.

The authorized mutable dependency boundary is the SFM control crate's Vox and
coherent Facet/Phon graph, the authored per-version development Java Vox pins,
and the native tool's exact source-built runtime contract. Other Minecraft
artifacts, scopes, policies and release snapshots remain unchanged.

## Source and artifact

- Vox Rust/codegen/Java source: `TeamDman/vox` commit
  `5628c00d9f3816199f324a9ccc081de8a3a1b1bc`.
- Producer validation read at documentation successor
  `be6813e7a08c06376a8416eb3368bc97c6de1651`,
  `docs/teamy-migration/cloud-cutover-validation.md` and its receipt.
- Independently reproduced `teamy-vox-java-0.10.0-rc.7.jar`: 614,530 bytes;
  SHA256 `be441a57e11d4981d283306fe471874e625bf2f423c83a91ce8f44dde623d062`;
  SFM identifier `blake3:4ecea4778148a5b04dd5e2c6c0e970bfbe97bc4c`.
- Exact Java producer SDK: Temurin 21.0.8+9,
  `VOX_JAVA_BUILD_JDK='Eclipse Adoptium|21.0.8+9-LTS'`.
  Compilation targets Java 17. Cloud Rust is 1.99.1; producer used 1.96.0.
  The independently built Java bytes match despite that Rust-toolchain difference.
- CSV adapter uses `TeamDman/facet-format` at
  `642d268fc01eced0a861bded044342ff88733eec`, whose manifest supports the
  required format rc.9 graph. Its CSV implementation differs from the previous
  source only in fork documentation; package/dependency declarations changed.
  Resolved graph has one registry `teamy-facet-core` rc.7 and `taxon` rc.5,
  with no old git Facet identity or `teamy-taxon`.

## Projection boundary

`sfm-codegen` now writes/checks the authored
`platform/minecraft/core-liquid-template/src/main/java/.../control/generated`
root. Regenerated Java uses the new schema bundle wire representation. There is
no old-wire compatibility path. The application-level SFM protocol remains 2;
its operations/fields have not changed.

Only the Vox dependency/artifact objects changed in the ten schema-4 version
locks and the canonical lock. Each version retains its own scopes and bundling.
All ten schema-2 release snapshots are unchanged. The original capture checkout,
installed capture CLI, old runtime and evidence archives remain available.

JUnit's historical provision receipt is unchanged. Its exact original authority
bytes are preserved as `schema-4.test-dependency-authority.json` and selected
only by the JUnit authority reader; this file is not a runtime projection lock.
Its SHA256 remains `198c141c2e45ed29a90935e84565e10f861aa0895233bb9b4a10cb8d2c109d41`.
This avoids rewriting historical evidence when the live development lock changes.

## Verification

- Independent Vox package: Java 1,934 schema-bundle plus 86 conformance
  assertions, runtime/framing/generated-response tests, deterministic assembly,
  fresh consumer and dependency-closure checks passed. `jdeps`: `java.base`.
- SFM Rust all-feature check and all 69 tests passed. Codegen drift check passed.
  All 29 generated Java binding files compile with `--release 17 -Xlint:all -Werror`.
- Real loopback Rust CLI ↔ generated Java SFM fixture: identity ping/describe,
  Unicode action round trip, authentication rejection and protocol rejection
  passed. This fixture explicitly identifies itself as **not Minecraft**.
- Projection drift checks passed for development 1.19.2, 1.19.4 and both
  issue-610 visual 1.21.1 contexts. All ten development locks were synchronized.
- Both actual 1.21.1 dedicated-server `sfm:issue_610_visual_controls`
  characterizations passed with the new JAR verified in each launch classpath.
  Both have first pulse `99999 / 100000 FE`, independent drain `99999 / 0 FE`;
  pulse two is baseline `99999 / 0 FE`, fixed `99999 / 100000 FE`.
  Baseline source energy stays at 255,749,940 J on pulse two; fixed ends at
  255,499,940 J. These are variant-specific characterizations. A common
  refill assertion would fail on the baseline. Manual controls were not rerun.
- Native all-feature library gate: 2,007 passed, 48 failed, 23 ignored.
  A forced fresh build at the unchanged base has 2,006 passed and the exact
  same 48 failures. Two exact-artifact contract tests passed after final formatting.
  Four integration suites passed (12 + 40 + 3 + 6); Java scenarios have 10
  passes and 41 failures because no legacy `1.19.2` worktree is configured.
  Bin/doc targets compile and have zero tests. Direct dependency policy passed.
- PowerShell, rustfmt and Clippy are absent. The literal `check-all.ps1` and
  formatting/lint gates are unrun; available Cargo gates were invoked directly.
  The attempted commands and availability evidence are recorded below.

## GameTest and control provenance

The two FE characterizations used freshly compiled Java dedicated GameTest
servers and the new Vox JAR. They were launched by a newly built native
`sfm-propagate-changes`, not by the Rust `sfm` control client, and did not reuse
previously compiled capture clients. The new JAR's presence on the classpath
does not prove that a Vox RPC or the Minecraft control service was exercised.

| Projection | Launch session | Build snapshot | Result |
| --- | --- | --- | --- |
| `sfm-4.34.0-issue610-visual/mc-1.21.1` | `session-Rt9bFT` | `snapshot-DTTfIQ` | Exit 0; baseline characterization passed |
| `sfm-4.35.0-issue610-visual/mc-1.21.1` | `session-bcXjNe` | `snapshot-LEOYFV` | Exit 0; fixed characterization passed |

Both launches used Temurin 21.0.8+9 and the independently reproduced Vox JAR
identified above. Its actual launch copy was rehashed in each session. The
compiled `OutputStatement.class` and `Issue610VisualControlsGameTest.class`
were checked against the recorded snapshot and actual launch bytes.

```text
Baseline snapshot receipt SHA256:
df8a493972795b7a4d33ac2070db8233348a9e66bf555a8ce9059928f57ee0d3
Fixed snapshot receipt SHA256:
257765bf674139319df438ff8e4fd03783843b4407c8bb50c06b99d299f947a3
Baseline actual payload inventory SHA256:
60441bc50a74a48a095e2982128513519bcba4f1f7f9766af3c720f834a4d24e
Fixed actual payload inventory SHA256:
69c8fbb0a4cfadea8413a1ec1cd1dc93b9f1327cb6627b1da7ed529a29385b3b
```

The payload inventories describe the actual session `mod` directories: 1,282
files / 4,784,837 bytes for baseline and 1,294 files / 4,889,076 bytes for fixed.
Each inventory is a sorted array of `{path,bytes,sha256}` records with POSIX
relative paths, compact JSON separators and one terminating newline. The
hashes above identify those manifests, **not release JARs**. Snapshot receipts
use full BLAKE3 for their own per-file digests; those are distinct from these
SHA256 inventory and receipt hashes.

The Rust `sfm` control executable has SHA256
`1ec4f40fbf21640f567f49dfb6dd5c58cd144e7be12070b805f66e744dad1ef4`.
It was exercised only by the separate generated-Java loopback TCP fixture
described above. Its four passing checks do not establish a real Minecraft
control handshake.

**Intermediate launcher hash limitation:** the native launcher used for the
two GameTests was rebuilt from the cutover worktree, but its executable hash
was not recorded before the final post-commit reinstall overwrote it. The
intermediate build log and launch receipts are retained. Its exact executable
hash is unknown and must not be inferred from the final binary.

The final native `sfm-propagate-changes` executable has SHA256
`071df7b200a3cbd4f340ee27066796eaba534f77f16fd10b832eb64bdd30a1ab`
and reports version 0.1.1, revision
`9857bca7b2f8b5b2ef917092a8d6f4c190e2075b`, built
2026-10-23 06:18:02 UTC. This is a post-game build, not a hash of the earlier
GameTest launcher.

## Unavailable gate evidence

The only installed Rust toolchain is `1.99.1-x86_64-unknown-linux-gnu`.
`rustup component list --installed` lists only Cargo, rustc and rust-std.
The rustfmt/Clippy names found on PATH are rustup shims, not installed
components. Searches of the installed toolchain, standard executable and
optional software locations found no standalone alternatives.

The following commands were attempted from the cutover checkout:

```sh
cargo fmt --all --manifest-path platform/cli/sfm/Cargo.toml -- --check
cargo clippy --locked --offline --all-features --manifest-path platform/cli/sfm/Cargo.toml -- -D warnings
pwsh -NoLogo -NonInteractive -Command '$PSVersionTable.PSVersion.ToString()'
```

The first two exited 1 with, respectively, `cargo-fmt` and `cargo-clippy`
not installed for that toolchain. PowerShell process creation failed because
`pwsh` was absent (recorded as exit 127). These gates are **unrun due to missing
tools**, not passing or source-code failures. No tools were installed. The
PowerShell `check-all.ps1` and `install.ps1` scripts were not executed; the
earlier available Cargo checks and direct native builds remain the separately
reported evidence.

## Remaining qualification blocker

The actual 1.19.2 control-client game launch is unrun. Native preparation failed
at the pinned Parchment dependency; a normal filtered-CA connectivity check
followed its 302 redirect to `ldtteam.jfrog.io`, where the proxy returned
CONNECT 403. That acquisition stopped. No alternate mirror, trust relaxation,
network change or skipped source check was used. 1.19.4 full-game compilation
and a real Minecraft control handshake remain unverified.

The cached 1.21.1 assets do not provide an existing control-handshake route.
In `platform/minecraft/core-liquid-template/feature-definitions.json`,
`client_control_cli` supports only 1.19.2 and 1.19.4. The authored
`project-inputs.json` rules likewise restrict `SFMClientControlLifecycle`,
`SFMClientControlServer` and generated control bindings to those targets.
Neither tested 1.21.1 payload contains the control service classes. A 1.21.1
endpoint would require a version port beyond this bounded consumer cutover.
Thus both FE characterizations can pass while the real Minecraft control
handshake remains unverified and the existing 1.19.2 path remains blocked at
the denied mapping hostname. The provenance follow-up made no acquisition
retry or network request.

## Reproduction

From this checkout, with a full exact producer JDK in `JAVA_HOME`:

```sh
cargo check --locked --all-features --manifest-path platform/cli/sfm/Cargo.toml
cargo test --locked --all-features --manifest-path platform/cli/sfm/Cargo.toml
cargo run --locked --manifest-path platform/cli/sfm/Cargo.toml --features codegen --bin sfm-codegen -- --check
python3 scripts/vox-control-probe/run.py --java-home "$JAVA_HOME" \
  --runtime-jar /path/to/teamy-vox-java-0.10.0-rc.7.jar \
  --sfm /path/to/rebuilt/sfm --output /path/to/new-probe-directory
sfm-propagate-changes game-test run-server --repo-root "$PWD" \
  --projection sfm-4.35.0-issue610-visual/mc-1.21.1 \
  --filter sfm:issue_610_visual_controls --java-home "$JAVA_HOME" \
  --require-portable-artifacts --log-filter info --log-file /path/to/fixed.ndjson
```

Use `sfm-4.34.0-issue610-visual/mc-1.21.1` for baseline characterization.
Use `run client --projection sfm-dev/mc-1.19.2 --java-home /path/to/jdk17
--require-portable-artifacts` after the pinned dependency is available through
permitted tooling, then `sfm --output-format json instance list` and
`sfm action invoke sfm:echo cutover-check` for the remaining Minecraft handshake.
Keep the exact producer JDK in the parent environment for source-built Vox.

The retained evidence directory is named `sfm-vox-cutover-evidence`; it is
separate from the repository. Its `provenance-followup/assessment.json`,
`installed-gate-probes.json`, `installed-executable-search.json` and two
`*-actual-mod-payload.json` files preserve the detailed follow-up receipts.
The separate tool directory is named `sfm-vox-cutover-tools/bin`. Absolute
executor paths are omitted here for a portable handoff. The final build/hash
receipt records the source revision, subject to the explicit intermediate
launcher limitation above. The Linux build uses the same existing
`reqwest/rustls-tls-native-roots` feature as the capture tool and the existing
filtered CA store. No Cargo declaration/lock change was made to that native tool.

For this documentation-only follow-up, user install required: **not applicable**.
Runtime source, generated inputs, dependencies and installed tools are unchanged;
no rebuild, new game test, process lifecycle action, acquisition, network-policy
change or consumer push was performed. The remaining runtime qualification is
still blocked as described above.
