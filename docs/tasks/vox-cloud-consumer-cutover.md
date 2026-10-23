# SFM standalone Vox consumer cutover — cloud checkpoint

Base: `capture/sfm435-issue610-cloud` at
`8e349578d18511d8d5993b66a3ad3c7c957e5db4`. Work is isolated on
`migration/vox-sfm-cloud-cutover-20261023`; no public push, merge, package
publication, desktop update or network-policy change is included.

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

## Remaining qualification blocker

The actual 1.19.2 control-client game launch is unrun. Native preparation failed
at the pinned Parchment dependency; a normal filtered-CA connectivity check
followed its 302 redirect to `ldtteam.jfrog.io`, where the proxy returned
CONNECT 403. That acquisition stopped. No alternate mirror, trust relaxation,
network change or skipped source check was used. 1.19.4 full-game compilation
and a real Minecraft control handshake remain unverified.

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

Cloud logs, hash audits and receipts are under
`/workspace/sfm-vox-cutover-evidence`. Operational tools are installed separately
under `/workspace/sfm-vox-cutover-tools/bin`; the final build/hash receipt records
the committed source revision. The Linux build uses the same existing
`reqwest/rustls-tls-native-roots` feature as the capture tool and the existing
filtered CA store. No Cargo declaration/lock change was made to that native tool.
