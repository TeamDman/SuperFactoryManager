# Codex cloud environment for SFM

This is the repository-owned setup entrypoint for a prepared SFM Codex cloud image. The saved Codex environment instruction can be only:

> In the SFM checkout, read `docs/codex-cloud-environment.md` and follow its declared scope and publication gate. Do not treat an undeclared target as pre-cached.

The canonical fresh-start qualification is a **new task in the published `SuperFactoryManager` Codex Cloud environment**. Isolated offline preparation and an actual fresh Codex task are separate gates. A container is one supported isolation mechanism, not a substitute fresh task. If no supported mechanism can satisfy the offline gate, publication stays blocked; do not waive the gate or create another development workspace to simulate it. Do not create a second development workspace merely to simulate the saved environment.

The image publisher must select its supported projections explicitly. A successful warm build on a desktop, or an old `matrix` pass receipt, is not evidence that a new image contains their dependencies. Never copy desktop `G:` archives into the cloud image or make the image depend on them.

## Tools and cache preparation

The repository entrypoint is
`platform/cli/sfm-propagate-changes/scripts/cloud-prepare.py`. It uses existing
Git, Python 3.11+, OpenSSL, a C toolchain, the selected Rust/Cargo toolchain and
already verified JBR17/21/25 inputs. Missing tools or cache entries are explicit
preparation blockers; it neither installs system packages nor silently changes
pins. Provision missing tools through the supported cloud environment workflow.
Do not use Gradle in this checkout (`docs/AGENTS.md`).

Keep the graph frozen: no lock refresh, dependency upgrades, arbitrary local
artifacts or alternate producer source. Use existing supported acquisition or
manual-import commands during separately authorized preparation, never the gate.
Exact Mekanism inputs still require the documented
[manual artifact handoff](mekanism-26.1.2-manual-artifact.md). Vox publication and
qualification must remain held until its separate authorization and source pin
are available. No preparation command overrides either blocker.

### Reviewed preparation layout

Keep a machine-specific `layout.json` with the image build materials, outside
tracked locks. Paths must be absolute, existing and free of linked ancestors.
The layout records these required inputs (no secrets):

- `schema`: `sfm:cloud-preparation-layout@1`;
- `native_cache_root`: the guarded build/dependency root;
- `application_cache_root`: the root selected by `SFM_PROPAGATE_CHANGES_CACHE`;
  it may equal the native root. Distinct managed roots must not overlap;
- `target_java`: every selected exact projection key mapped to its reviewed Java
  compiler major. Native preparation tools can require a different major;
  include all required SDKs, not just the compiler SDK;
- `sdks`: records with `major`, `home`, `archive`, `sdk_cache_root`, full `sha512`,
  tracked repository-relative `authority_lock` and its `authority_lock_sha256`,
  external `trust_store`, `trust_sha256`, `trust_receipt`, and `proxy_ca_sha256`.
  Archive/home must belong to that SDK root's `minecraft-toolchain/jbrsdk`
  `archives`/`installs`. The exact authored Linux pin authenticates the archive;
  the repository's existing `src/jdk_tar.py` verifies every installed file,
  link target and mode. Explicit SDK homes must be outside the native build root;
- `tls_ca_file` / `tls_ca_sha256`, `tls_ca_dir`, `tls_source_file` /
  `tls_source_sha256`, `proxy_ca_sha256`, and `denied_certificates`: the four
  original eMudhra/emSign certificate witnesses. The native filtered bundle
  must preserve every unrelated source anchor and the approved proxy CA;
- `stage_timeout_seconds`: a positive bounded per-stage timeout (default 1800).

For the currently verified preparation, native compilation uses Java17 for
1.19.2–1.20.1, Java21 for 1.20.2–1.21.1, and Java25 for 26.1.2. Some Java17
compilations require Java21 preparation tools. Targets without `jdk_pins` retain
that fact: explicit reviewed SDK selection is recorded in scope rather than
silently adding a pin or changing frozen product locks.

### Build and guarded activation

Start from a clean exact commit, initialize the existing filtered preparation
trust/tool environment, and use a **new** output directory whose parent exists:

```sh
python3 -B platform/cli/sfm-propagate-changes/scripts/cloud-prepare.py build \
  --repo-root "$PWD" --layout /absolute/layout.json \
  --output /absolute/new-release-installation
```

The entrypoint checks native-only source TLS policy before an offline locked
release build, supplies the full Git revision to the supported build override,
verifies the resulting executable's denied-key scan before execution, and checks
its exact full revision. It preserves the previous target executable, copies a
new versioned installation, and records `build-receipt.json`, CLI SHA-256,
layout/Cargo.lock identities and build logs. It does not replace the previous
active wrapper or installation. A failed build has no successful receipt.

After qualification and coordination, select this installation through the
repository's guarded `exec` entrypoint below. Merely putting a raw executable in
PATH does not activate the trust contract. Do not replace the saved embedded
installer/start instructions with pointers until this executable setup workflow
has been qualified in the actual environment.

### Declare the complete target/stage contract

```sh
python3 -B platform/cli/sfm-propagate-changes/scripts/cloud-offline-readiness.py draft \
  --repo-root "$PWD" --cli /absolute/new-release-installation/sfm-propagate-changes.native \
  --build-receipt /absolute/new-release-installation/build-receipt.json \
  --layout /absolute/layout.json --scope /absolute/new-scope.json --projection '*'
```

`'*'` inventories all **74** currently registered targets; it is not a readiness
claim. The catalog consists of 10 dev, 20 extended 4.34/4.35, 20 issue610-portable,
four 1.21.1-only issue610/visual, and 20 historical plain targets. A deliberately
narrower publication scope must list every exclusion; it cannot be called full
74-target coverage. All 74 remain in the overall qualification goal.

Review the generated scope and change only `state` from `draft` to `declared`
once its complete contract is accepted. Scope schema `sfm:cloud-offline-scope@2`
binds source commit, catalog, emitted locks, CLI, release build receipt, the
complete immutable cache inventory hash, cache/SDK/
trust layout and the ordered `check`, `compile`, `jar` stages. Historical recipes
are selected by exact emitted-lock hash from the tracked production
`released-native.json` and `released-native-historical-2612.json` configurations.
Their original source locks are rehashed; a nearby recipe, ambiguous match or
changed source lock is refused. In particular, retained 4.34/26.1.2 keeps
`@historical-retained1` while current 4.35/26.1.2 keeps `@1`; the earlier review
document is not their live acquisition authority.
Old schema-1 scopes must be regenerated, not relabelled.

JUnit, applicable headless GameTests, GUI, Gradle, actual fresh-task restoration
and G-unavailable proof are recorded as **not attested** by this scope. They need
separate actual receipts; historical warmed compiles and four empty-root family
proofs are not full coverage. The existing 54 Vox-dependent holds and exact
Mekanism blockers are not waived by generating a scope.

## External Java trust stores

Keep downloaded JBR archives and installed SDK trees unchanged. If the environment needs a filtered CA policy or its proxy CA, derive a separate store during image preparation with `platform/cli/sfm-propagate-changes/scripts/prepare-java-trust.py`. Supply the selected SDK's `lib/security/cacerts`, its `bin/keytool`, each approved `--deny-sha256`, and a new `--output-dir`. An additional environment CA requires both `--extra-ca-cert` and its independently checked `--extra-ca-sha256`. The helper refuses an existing output directory, checks that every other original certificate remains, and writes `cacerts` plus `receipt.json` with source and output SHA-256 identities. Archive authentication remains the existing JBR acquisition step. A failed preparation has no successful receipt and must not be selected.

Configure `SFM_JAVA_TRUST_STORE_17`, `SFM_JAVA_TRUST_STORE_21`, and `SFM_JAVA_TRUST_STORE_25` with the corresponding derived stores, and each matching `SFM_JAVA_TRUST_SHA256_<major>` from that store's receipt. This lets the Java 21 Vox producer use its own policy when SFM compiles with Java 17 or 25. A deliberately shared approved policy can instead use the unsuffixed `SFM_JAVA_TRUST_STORE` and `SFM_JAVA_TRUST_SHA256` pair. A complete major-specific pair takes precedence; a partial pair fails rather than falling back. With no configured selection the existing JDK defaults apply. Use absolute paths without quotes or control characters, and keep published stores immutable and readable by the task user.

Native Java and javac launches verify the selected file identity and digest and pass explicit JVM properties. Build plans, named launch contracts, NeoForm dependency keys and matrix inputs record the selected trust identities. Generic Cargo source builds can receive controlled `JAVA_TOOL_OPTIONS` only in their child environment. Configured child launches remove ambient JVM option overrides. The pinned Vox producer at `5628c00d` requires empty `jdeps` stderr, which is incompatible with the standard `JAVA_TOOL_OPTIONS` startup banner. A source build of that producer with an external trust policy therefore refuses before launching Cargo. Completing this path requires a reviewed Vox producer that accepts explicit trust arguments for every Java tool, followed by a reviewed source-pin update and actual JBR 21 package qualification. Do not remove the stderr check, modify verified SDK bytes, or run its JVMs with the unfiltered store as a fallback. A selected digest identifies approved store bytes; qualification must still exercise the actual selected JBRs and Vox recipe.

`platform/cli/sfm-propagate-changes/scripts/JavaTrustProbe.java` accepts a comma-separated denied-fingerprint list, an expected CA fingerprint, and an allowed HTTPS URL. Run it with each selected JBR and explicit store to verify the default trust manager and HTTPS. The Python `test_prepare_java_trust.py` suite verifies preservation, failure behavior and actual retained/denied TLS handshakes. Rust `java_trust` tests cover file mutation and contract identity; its explicit ignored HTTPS test requires the `SFM_TEST_TRUST_*` inputs named in that test. These checks supplement the complete publication gate.

## Mandatory offline publication gate

Use a disposable candidate layer with enforced loopback-only networking and no
IPv4/IPv6 default route, **no host cache/archive mounts**, and only declared image
contents. Docker `--network none` is supported when its managed local daemon is
available; an actual Codex environment with a supported equivalent isolation
mechanism still needs the same checks. Do not create another development
checkout/workspace, substitute restricted Internet for isolation, change
security settings, or suppress a failed namespace/route predicate. The strict
IPv6 predicate also rejects a kernel default-route sentinel; if the isolation
runtime exposes one, report it for reviewed coordination rather than edit routes.

Prepare the candidate without old matrix pass receipts or prior projection
`build/libs` directories. **Exclude these when constructing a new candidate;
never delete the setup VM's receipts, outputs, caches or backups.** The verifier
refuses preexisting pass receipts and output directories.

```sh
python3 -B platform/cli/sfm-propagate-changes/scripts/cloud-offline-readiness.py verify \
  --repo-root "$PWD" --cli /absolute/new-release-installation/sfm-propagate-changes.native \
  --scope /absolute/new-scope.json --evidence /absolute/new-gate-evidence
```

The gate executes the supported native `source project check`, `compile`, and
`jar` commands anew for each exact target, with explicit cache root, SDK and
historical recipe. It does not reuse matrix pass receipts. Direct native stage
commands are necessary because `matrix` currently cannot forward `--cache-root`
and `--java-home`. Normal verified compilation caches remain permitted;
`completed_or_valid_compile_cache` is not an independent empty-root compile.
Each invocation retains stdout, stderr, command, exit status and a new receipt;
a failure/timeout cannot produce a passing aggregate. No source sync, dependency
refresh, JUnit, GameTest, GUI or Gradle stage is implicitly added.

Before and after execution it audits SDKs against their exact pinned archives,
checks native and Java trust, rechecks source/scope identities, and compares
SHA-256, size, type, mode and link inventories of **every selected managed cache
root**, including distinct SDK roots. Legitimate SDK links are accepted only
after full archive-backed installation verification; other cache links fail.
Modified or removed existing files fail just as new files do. `projects`, `nfrt`
and `check-everything-native` are explicitly mutable derived-output/session/
receipt directories, not dependency admission roots. Archive bytes, SDK trees,
external trust stores and all other managed bytes remain immutable.

Preserve `result.json` and every stage receipt with the externally recorded
candidate image digest and isolation/mount witness. The script proves only the
executed declared native stages in that isolated candidate; it does not discover
or authenticate an image digest from inside the guest. Publish only after a zero
exit and `status: passed` for the complete declared target list, followed by the
separately coordinated actual Codex fresh-task qualification. Publication itself
is a user/platform action, never performed by this script.

## Refresh and recovery

After publication, start a fresh Codex Cloud task selecting the saved `SuperFactoryManager` environment. Record the task's source commit, image digest, CLI, catalog, declared scope, lock and tool identities before running fresh declared `check`, `compile` and `jar` operations. Confirm all declared dependency/tool inputs are present at task start and that normal validation publishes no new managed dependency/tool bytes. Record the network policy or other enforced egress-denial witness separately; zero logged requests alone is an observation, not enforced isolation. A task using an older source/image, missing one declared target or acquiring an artifact is a failed publication qualification. Do not infer this fresh-task outcome from a desktop repeat or the container check alone.

Any changed source commit, projection catalog, CLI binary, target lock, JDK/tool pin or declared target set needs a refreshed scope, cache preparation, networkless gate and a **new image publication**. A changed lock must not be served by an older image. Keep the prior image for its original pins until the new one passes. Retain failure receipts for diagnosis. `matrix` remains the everyday resumable validation command; its default reuse is appropriate after publication, while the offline publication gate executes supported native stages directly with the exact declared cache/SDK inputs.

At the start of each deployed session, run the lightweight `cloud-offline-readiness.py check` with the image's declared scope, repo and CLI paths. It rejects changed source/catalog/CLI/lock identities without rebuilding. Keep normal build traffic offline after deployment; new dependency acquisition belongs to a new prepared image, not a running session.


## Thin saved instructions and session start

After executable setup qualification, the saved install instruction can invoke
`cloud-prepare.py build` with the reviewed retained layout and a new versioned
output. The saved start instruction should point here and name the retained
scope/installation paths. Keep the saved draft unchanged until that handoff is
qualified; do not copy machine checkpoints or stale hashes into a new installer.

At session start, run the identity-only check and obtain checked shell exports:

```sh
python3 -B platform/cli/sfm-propagate-changes/scripts/cloud-offline-readiness.py check \
  --repo-root "$PWD" --cli /absolute/installation/sfm-propagate-changes.native \
  --scope /absolute/declared-scope.json
python3 -B platform/cli/sfm-propagate-changes/scripts/cloud-prepare.py start \
  --repo-root "$PWD" --cli /absolute/installation/sfm-propagate-changes.native \
  --scope /absolute/declared-scope.json
```

Review/source only the exports emitted after these checks. To invoke the
selected CLI with the guarded native/Java trust contract without installing a
new global wrapper:

```sh
python3 -B platform/cli/sfm-propagate-changes/scripts/cloud-prepare.py exec \
  --repo-root "$PWD" --cli /absolute/installation/sfm-propagate-changes.native \
  --scope /absolute/declared-scope.json -- --version
```

Existing checkout only; no automatic clone/worktree, service restart, cache
cleanup, trust mutation, network-policy change or publication. A changed source,
CLI, lock, catalog, layout or trust identity invalidates the scope and needs
preparation/requalification, not an automatic refresh during a task.
