# Codex cloud environment for SFM

This is the repository-owned setup entrypoint for a prepared SFM Codex cloud image. The saved Codex environment instruction can be only:

> In the SFM checkout, read `docs/codex-cloud-environment.md` and follow its declared scope and publication gate. Do not treat an undeclared target as pre-cached.

The canonical fresh-start qualification is a **new task in the published `SuperFactoryManager` Codex Cloud environment**. The networkless container check below is a preparation gate when available; it does not replace evidence from that actual saved environment. Do not create a second development workspace merely to simulate the saved environment.

The image publisher must select its supported projections explicitly. A successful warm build on a desktop, or an old `matrix` pass receipt, is not evidence that a new image contains their dependencies. Never copy desktop `G:` archives into the cloud image or make the image depend on them.

## Tools and cache preparation

1. Start from a clean, exact SFM Git commit. Keep the Rust CLI `Cargo.lock`, generated projection locks and [source projection contributor guide](source-projection-contributor-guide.md) together at that commit. Install the Rust toolchain, Python 3, Git, a Linux container runtime and the JDKs required by the selected locks in the image preparation phase. Use the CLI's pinned JDK resolution; do not put a local `JAVA_HOME` path in tracked locks. Build `platform/cli/sfm-propagate-changes` with `cargo build --locked --release`. Network access during preparation is limited to the established sources declared by the locks and toolchain catalogs.
   Linux JBRSDK acquisition supports the exact pinned `.tar.gz` artifact and requires `python3` for bounded extraction and installed-tree verification. The lock must still contain the Linux artifact's independently verified SHA-512; adding tar support does not supply or infer that pin.
2. Choose every projection the image promises to support. From the checkout root, create a scope draft with the built CLI:

   ```sh
   python3 platform/cli/sfm-propagate-changes/scripts/cloud-offline-readiness.py draft \
     --repo-root . --cli platform/cli/sfm-propagate-changes/target/release/sfm-propagate-changes \
     --scope /tmp/sfm-cloud-scope.json --projection 'sfm-dev/*'
   ```

   The pattern is an **example**, not a claim that all ten development projections are ready. Review every generated target against the intended environment contract. Add other exact catalog keys as needed, including historical targets only with their exact `released_recipe`. Record the reviewed scope with the image build materials and change `state` from `draft` to `declared`. The draft binds the Git commit, catalog SHA-256, CLI SHA-256, and each emitted lock SHA-256. The verifier refuses an empty, duplicate, stale or changed scope.
3. Populate the image's managed cache using the exact declared locks and the ordinary Rust source/build commands. `dependency add --maven` and the exact CurseForge project/file route are for **published** versions: they acquire established published bytes and update the lock before the scope is frozen. `dependency source acquire` handles source providers selected by a lock. The [dependency and source workflow](dependency%20and%20source%20workflow.md) explains the existing commands. Do not alter a historical lock or choose a nearby version to make a missing dependency disappear.
4. For an unpublished dependency, obtain the exact JARs through a separately documented manual producer, then use `dependency artifact import` with the selected projection, each supplied JAR, its producer receipt and independently stated SHA-256. [Mekanism 26.1.2](mekanism-26.1.2-manual-artifact.md) gives the exact current-pin commands and the explicit `--update-lock` route for a newly receipt-backed generation of an existing development component. The latter updates the selected authored lock, then requires normal source sync and requalification before image publication. A different coordinate or new dependency still requires reviewed declaration/acquisition. The manual inputs must be present during image preparation. A copy in one desktop cache does not prove unattended empty-cache reconstruction, and an image requiring these outputs remains **not publishable** until its own networkless gate passes.

## Mandatory offline publication gate

Run the verifier on the candidate image in a **disposable Linux container with `--network none`**, no host cache/archive mounts and only the declared image contents. Do not bake prior `check-everything-native/receipts` into the candidate; the verifier refuses existing pass receipts. The script independently requires a loopback-only network namespace and no IPv4/IPv6 default route. Preserve the gate's JSON result and the matrix receipts with the image digest. For each declared projection it runs fresh `check`, `compile` and `jar` stages rather than accepting existing matrix pass receipts, and refuses a new file in SFM's managed dependency/tool roots. Source/build outputs and new validation receipts are allowed in the disposable container. A successful gate means these normal native operations needed no external acquisition with the image's exact inputs; it does not attest to undeclared projections or windowed client behavior.

Inside that networkless container:

```sh
python3 platform/cli/sfm-propagate-changes/scripts/cloud-offline-readiness.py verify \
  --repo-root . --cli platform/cli/sfm-propagate-changes/target/release/sfm-propagate-changes \
  --scope /path/in/container/sfm-cloud-scope.json > /path/in/container/offline-readiness.json
```

Publish only if the command exits zero and the result has `status: passed` for the complete declared target list. A failed, blocked, reused, interrupted, or absent row is not a pass. The gate is intentionally strict; if a target needs a new artifact, perform acquisition or manual admission during **image preparation**, rebuild the candidate image, then rerun the networkless gate. Do not let a post-deployment normal build fetch it. The current gate covers the native Rust projection build path. If the image also promises standalone Gradle wrapper builds, qualify those exact wrapper tasks offline with the image's pinned Gradle distributions and caches as an additional publication gate; do not infer that from the native matrix result.

## Refresh and recovery

After publication, start a fresh Codex Cloud task selecting the saved `SuperFactoryManager` environment. Record the task's source commit, image digest, CLI, catalog, declared scope, lock and tool identities before running fresh declared `check`, `compile` and `jar` operations. Confirm all declared dependency/tool inputs are present at task start and that normal validation publishes no new managed dependency/tool bytes. Record the network policy or other enforced egress-denial witness separately; zero logged requests alone is an observation, not enforced isolation. A task using an older source/image, missing one declared target or acquiring an artifact is a failed publication qualification. Do not infer this fresh-task outcome from a desktop repeat or the container check alone.

Any changed source commit, projection catalog, CLI binary, target lock, JDK/tool pin or declared target set needs a refreshed scope, cache preparation, networkless gate and a **new image publication**. A changed lock must not be served by an older image. Keep the prior image for its original pins until the new one passes. Retain failure receipts for diagnosis. `matrix` remains the everyday resumable validation command; its default reuse is appropriate after publication, while `matrix --fresh` is used by the offline publication gate to execute the selected stages again.

At the start of each deployed session, run the lightweight `cloud-offline-readiness.py check` with the image's declared scope, repo and CLI paths. It rejects changed source/catalog/CLI/lock identities without rebuilding. Keep normal build traffic offline after deployment; new dependency acquisition belongs to a new prepared image, not a running session.
