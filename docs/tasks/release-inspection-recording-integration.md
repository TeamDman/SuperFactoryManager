# Recording integration continuation

> Historical checkpoint. See [live cloud qualification](release-inspection-live-qualification.md)
> for the resolved SDK/TLS prerequisites, real builds, combined branch and client evidence.

The Explorer checkpoint is preserved remotely. The recording workspace remains **not
ready for in-game acceptance**: SDK retrieval and the native Minecraft download have
separate network/trust prerequisites. No substitute screenshots or invented release
JARs are provided.

## Preserved source and approval

The user explicitly approved non-force feature-branch pushes and installation of the
exact official Temurin 21.0.8+9 SDK. No main merge, dependency pin change, desktop access,
or network/trust configuration change was authorized or performed.

After outgoing path/content, secret-pattern and size review, the original four Explorer
commits were pushed to `TeamDman/SuperFactoryManager`:

- Branch: `feature/release-inspection-explorer-20261024`
- Remote SHA verified with `git ls-remote`: `dd020cbd8e998063aa9815840eba35ef734290b6`
- Review: 59 new blobs, 4,116,037 total uncompressed bytes; largest blob was the existing
  1.37 MB source catalog. No binary payload or secret-pattern match was found. This is
  scoped review evidence, not a guarantee from a comprehensive secret scanner.
- Receipts: `/workspace/scratch/release-inspection/push-review.json` and `push-receipt.json`.

This continuation is on `feature/release-inspection-recording-20261024`, based on that
published checkpoint. The actual matcher was fetched from remotely verified
`feature/nucleo-java-parity` at `9378a217e254bfd601d8cc47a9b7e8a7839d8084`.
Import commit `757f9ff75bfc1abf2543541e559fc6d0173a1daa` retains its three Java sources,
MPL/Unicode notices, API, core checks and native tests. Catalog additions were reconciled
individually; the migration baseline and pinned dependencies were preserved.

## Integration behavior

The Explorer has a distinct Nucleo `N` mode and `nucleo` command argument. Literal,
legacy fuzzy, regex and independent Find/Filter highlight controls remain. Nucleo
uses explicit respect/ignore case, SMART normalization, DEFAULT boundaries and no
prefix preference. Whole-word plus Nucleo reports unsupported. Native scores cannot
reorder the tree; normal hierarchy/flat ordering and original UTF-16 spans are retained.

Materialized filtering runs on a worker with a matcher local to that worker. Bounded
row work checks cancellation before/after matching; it does not interrupt the native
algorithm inside a candidate. Publication rechecks roots, ordering, expansion, options,
loader generation and closure. Compatible previous rows remain while pending; changed
roots show a pending placeholder. Complete-domain pending state takes precedence over
cache hits. Compaction anchoring waits for the accepted layout and yields to subsequent
user scrolling. Existing review-domain matching was moved outside its publication
monitor; cache materialization still uses that monitor.

Independent review identified and corrected budget-exhaustion aggregation, canonical
tie sorting, render-thread matching, changed-root stale rows, complete-domain timing,
command grammar and deferred compaction anchoring. Review is not client execution.

## Verification

The actual reviewed matcher and adapter pass the dependency-free binding/core checks.
The shared production `SFMTextMatcher` and `SFMExplorerProjection` compile with javac;
their probe verifies legacy modes, hierarchy/flat order, field-specific highlights,
Unicode, cancellation and retention of known matches when the aggregate budget expires.
Native tests additionally cover pending selection/scroll, root replacement, complete
domain timing and compaction anchoring, but those tests remain unrun pending the full
Minecraft classpath. A direct panel-model compile confirmed that classpath is absent;
it is not treated as a successful full compile.

The Commons Codec 1.15 input used by the shared-matcher probe was acquired from the exact
artifact URL in the existing cached Minecraft 1.19.2 manifest. Its expected size and
SHA-1 were verified before use; SHA-256 is
`b3e9f6d63a790109bf0d056611fbed1cf69055826defeb9894a71369d246ed63`.
This is an existing Minecraft dependency, not a new version or declaration. The receipt
is `commons-codec-receipt.json` in the evidence directory; no dependency lockfile changed.

Reproduce all current headless checks, retaining inputs and receipts:

```bash
source /workspace/.sfm-env/env.sh
python3 scripts/release-inspection/qualify.py \
  --cli /workspace/.sfm-env/target/debug/sfm-propagate-changes \
  --java-home /workspace/.sfm-env/jdk17 \
  --gson-jar /workspace/.sfm-env/cache/minecraft-toolchain/maven/net/minecraftforge/installertools/1.3.0/installertools-1.3.0-fatjar.jar \
  --codec-jar /workspace/scratch/release-inspection/commons-codec-1.15.jar \
  --output /workspace/scratch/release-inspection/new-recording-qualification
```

The scripts download nothing. The full Rust parity harness and its original evidence
remain on the matcher branch; the local core/binding checks are not a fresh Rust parity
run. Live filter/highlight behavior, UI navigation, rendering, scroll and screenshots
remain unrun.

## Exact blockers and remaining actions

1. **Approved SDK installation — pending download/verification route.** Host architecture
   is x86_64. The official release is
   `https://github.com/adoptium/temurin21-binaries/releases/tag/jdk-21.0.8%2B9`.
   HTTPS CONNECT to `api.github.com` for that release metadata returned `403 Forbidden`.
   Opening the official `OpenJDK21U-jdk_x64_linux_hotspot_21.0.8_9.tar.gz.sha256.txt`
   download was also reported as a restricted URL. The archive was not downloaded or
   installed, and the parent-supplied expected SHA-256 has not been independently
   matched against an accessible official checksum. No alternate proxy, TLS bypass,
   trust-store update or policy change was used. See `sdk-network-blocker.json`.

2. **Release JAR build — blocked by native TLS trust.** The actual catalog paths are
   `sfm-4.34.0/mc-1.19.2` and `sfm-4.35.0/mc-1.19.2`. Dry runs protected existing
   content; the 4.34 projection was synchronized through its normal ownership checks
   to restore two missing generated files and update one generated test. The frozen
   dependency recipe comes from `platform/minecraft/build-configuration/released-native.json`.
   This command reached the real native build:

   ```bash
   /workspace/.sfm-env/target/debug/sfm-propagate-changes source project jar \
     --repo-root /workspace/SuperFactoryManager --projection sfm-4.34.0/mc-1.19.2 \
     --released-recipe sfm:released-native-inputs/4.34.0/1.19.2@1 \
     --java-home /workspace/.sfm-env/jdk17
   ```

   It failed downloading the pinned Minecraft client at
   `https://piston-data.mojang.com/v1/objects/055b30d860ead928cba3849ba920c88b6950b654/client.jar`
   with `invalid peer certificate: UnknownIssuer`. That fetch was stopped; no trust
   fix was inferred from JDK installation approval. A reviewed, explicitly authorized
   native-roots fix or approved trust path is needed. The 4.35 named JAR packaging
   command has the same Minecraft prerequisite and has not been retried around it.
   No release JAR was created or substituted. Earlier `run compile` additionally
   reported missing pinned JUnit artifacts; it is not the release-packaging command.

3. **Dev/client acceptance — pending the SDK, native classpath and authorized cloud
   display/capture path.** Native JUnit and actual UI screenshots have not run.
   PowerShell/rustfmt/clippy are still absent. The existing precise launch command is
   in `release-inspection-explorer-cloud.md`; the original prepared workspace remains
   valid and explicitly marks missing release JARs.

4. **Picker combination — pending core compilation/UI qualification.** Parent supplied
   picker-only `feat/dynamic-editor-picker-only` at
   `59b1b3e8efb143995446f1add14f5de411e6c71a`, optional modal branch at
   `3d11405d15546b6b7fc8cb9e6771bd21670ed05a`, and portable-output fix at
   `f9831eb1aa4ba440845a1002aa055de2acbda081`. These are parent handoff identities,
   not locally verified integration results. Picker/modal changes were not copied
   over the migration baseline. Follow the requested order: core first, then picker;
   modal remains optional.

Network failures are confirmed operation failures, not automatic approval rejections.
Installation and trust-path questions remain pending; no uncertain mutation was retried.
