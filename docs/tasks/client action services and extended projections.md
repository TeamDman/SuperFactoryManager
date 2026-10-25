# Action services and extended projections — cloud checkpoint

Base inspected: `e2ba833b2b235835d69475b641e24b22bfd9551a` (origin/main verified
2026-10-25). Work is isolated on `codex/action-services-all-targets`; no push,
main merge, release, game launch, desktop/GPU use or toolchain install occurred.
This is a source implementation checkpoint, **not compiled or runtime acceptance**.

## Architecture and implemented behavior

“File Action Server” and “Vox Action Server” describe the transports. “Rust Action
Server” would describe the client implementation inaccurately: `sfm.exe` is the
Rust client; the game serves authenticated Vox over loopback TCP. The separate
`sfm-propagate-changes.exe` generates/builds projections and does not become this
controller. Neither CLI's source or wire schema changed here.

| Responsibility | Source and behavior |
| --- | --- |
| Startup/lifetime | `SFMClientControlLifecycle` owns one client runtime, not a puppet or screen. Both transports default off. `SFM_ACTION_FILES` and `SFM_ACTION_VOX` accept `true`/`1`, `false`/`0` or unset; invalid settings fail closed and log. Startup settings apply once; later palette changes persist for that process. |
| Dispatch | Existing `SFMClientControlFileBridge`, `SFMClientControlServer`, `SFMClientActionExecutor` and bounded 32-task `SFMClientThreadGate` are reused. File polling is on a single I/O worker; action execution is on Minecraft's thread; completion runs on a worker. |
| Files | Existing `sfm.client-control-files/1` numbered request/response protocol retains `command` and `action_tokens`. Each enable creates `gameDirectory/sfm-control/<instance>/<session>/ready.json`; sequence state cannot replay across re-enable. Files alone never call Vox startup. |
| Vox | Existing loopback host, ephemeral port, authentication, protocol/version validation, limits, descriptors and Rust exact-instance selection remain. The shared dispatcher survives Vox disable. Existing typed Explorer capability remains gated to its original feature/version scope; portable projections expose registered actions and capture. |
| Authority | `SFMActionArbiter` tracks an exact execution UUID and monotonically increasing puppet revision. An active puppet must acknowledge a cooperative handoff before a 1–300 second exact-UUID pause lease permits transport mutation. Actions default to refusing handoff; ManagedSession opts in while idle. Tracked server mutations and pending render interactions must drain. The other transport is rejected; expiry, resume, yield/switch and owner disable release the lease. Only runner ticks and tick-count timeout suspend; wall-clock deadlines and world simulation continue. |
| Races | The client-owned work tracker survives puppet yield and blocks mutations until previously dispatched tracked server work drains. Queued mutation captures puppet/scene revisions. Puppet switch, yield, idle→puppet→idle and GUI close/reopen invalidate it. Lifecycle/status remain accessible; capture validates its admitted target. A transport session is one controller principal; concurrent clients sharing it share authority. Without an active puppet, commands serialize through the shared queue. |
| Disable/cancellation | Disabling one transport invalidates only its session and queued requests, cancels its pending captures, and releases its lease. Already-running side effects cannot be rolled back. File receipts retry publication without re-executing the action; interrupted receipts explicitly say outcome unknown. Self-disable is rejected with an instruction to use the palette or the other transport, avoiding loss of the request's own acknowledgement. |
| Shutdown | The JVM hook closes the gate and transports, then waits at most five seconds for file cleanup. Receipt/ready-marker cleanup errors and timeout are logged. Palette disable remains asynchronous. |
| Capture | `SFMClientCaptureService` uses the existing Minecraft framebuffer screenshot APIs. A request requires subsequent actual screen/world render content and a later frame-end event, unchanged scene/size, then successful asynchronous PNG publication. It bounds pending/retained requests, expires after ten seconds, and reports render-unavailable/stale/cancelled/write failures. Its instance-scoped output and lifetime do not depend on a puppet. |

Primary implementation paths under `platform/minecraft/core-liquid-template/`:

- `src/main/java/ca/teamdman/sfm/client/control/`: runtime, lifecycle, arbiter,
  settings, capture, existing File/Vox servers and thread gate.
- `src/main/java/ca/teamdman/sfm/client/action/SFMActionServicesAction.java`,
  `SFMCommandPaletteActions.java`, `SFMClientActionExecutor.java` and source/result types.
- `src/gametest/java/ca/teamdman/sfm/gametest/puppet/SFMGamePuppetHarness.java`:
  feature-guarded transition notifications and cooperative pause gate; no wholesale replacement.
  `SFMPuppetAction`, `SFMGamePuppetHelper`, `ActivePuppet`, `SFMGamePuppetRenderHarness`
  and `SFMGamePuppetMinecraftRuntime` add explicit suspension readiness and tracked
  server callbacks. Existing actions remain non-cooperative until explicitly adapted. The tracker covers
  shared MinecraftRuntime callbacks, not arbitrary worker futures in every custom
  puppet; an opt-in action must certify those additional effects itself.
- `platform/cli/sfm/src/{cli,discovery}.rs`: unchanged Rust action/discovery path.

Available palette IDs (also registered action IDs for either transport):
`sfm:control/status`, `sfm:control/files/enable`, `sfm:control/files/disable`,
`sfm:control/vox/enable`, `sfm:control/vox/disable`, `sfm:control/capture`,
`sfm:control/capture/status <id>`, `sfm:control/puppet/pause <execution-id> <seconds>`,
and `sfm:control/puppet/resume <execution-id>`.

The old exploration puppet's `op` JSON mouse/key/type/fault-injection protocol in
`ExploreReviewInteractivelyPuppetAction` remains unchanged. This checkpoint
promotes the existing registered-action file bridge into the client service;
it does **not** claim to have ported every legacy exploration operation to all
versions. If retaining that raw-input protocol as the new service surface is
required, extracting its shared input adapters is remaining implementation,
not covered by the all-target generation result. Test-only worker/navigation
fault injection must remain in GameTests.

## Projection contract and compatibility

Metadata inventory contains exactly ten canonical targets. `mc-1.21.0` uses
`minecraft_version: "1.21"`; it is not an eleventh target.

- Plain `sfm-4.34.0`: every tracked byte and file membership matches the pinned
  base, across the entire generated trees. Fixed two pre-existing generator
  drifts: leaked Managed puppet files and the old SFML directory-search loop.
  The 26.1.2 historical lock is an exact authored snapshot of that committed
  release, selected only for that historical projection. Its dependency values
  were not refreshed; current/extended schema-4 locks remain unchanged.
- Plain `sfm-4.35.0`: every remaining byte/path matches the pinned fixed release;
  exactly 80 reviewed regression/Managed-puppet output files move into extended
  tooling membership. Release fixes, resources and dependency values are unchanged.
- Twenty new `sfm-4.34.0-extended/mc-*` and `sfm-4.35.0-extended/mc-*` development
  projections add equivalent palette, services, capture and shared regression
  fixtures. Red excludes release-fix features; green includes them. The Label Gun
  test that requires the new API is green-only. Energy/recipe/loot/cable tests
  retain their existing target adapters and assertions; actual red/green runs
  have not occurred.
- Existing issue610 names are preserved, not deleted or redirected to new
  directories. The two green legacy selectors retain their ancillary regression
  fixtures via `extended_release_regressions`. They are compatibility selectors,
  not equivalent aliases for the entire new extended feature set. No existing
  desktop projection directory or world was touched.

## Coverage

Generation covers each plain and extended family. Generation is not compilation.

| Target | Plain red/green generation | Extended red/green generation | SFM compilation | Minecraft/runtime |
| --- | --- | --- | --- | --- |
| 1.19.2 | Passed | Passed | Blocked before javac by pinned Vox producer test | Not run |
| 1.19.4 | Passed | Passed | Not attempted after shared producer blocker | Not run |
| 1.20 | Passed | Passed | Not attempted after shared producer blocker | Not run |
| 1.20.1 | Passed | Passed | Not attempted after shared producer blocker | Not run |
| 1.20.2 | Passed | Passed | Not attempted after shared producer blocker | Not run |
| 1.20.3 | Passed | Passed | Not attempted after shared producer blocker | Not run |
| 1.20.4 | Passed | Passed | Not attempted after shared producer blocker | Not run |
| 1.21.0 | Passed | Passed | JDK 21 compiler absent; installed Java 21 is a JRE | Not run |
| 1.21.1 | Passed | Passed | JDK 21 compiler absent | Not run |
| 26.1.2 | Passed | Passed | JDK 25 absent | Not run |

Available tests: `scripts/test_action_services.py` passes five tests, including
34 assertions against the real pure Java settings/arbiter/gate, target feature
closure and pinned whole-tree comparisons. Three existing recipe/resource
checks pass after updating the migrated fixture path. Three additional JUnit
file-bridge tests cover receipt retry, interrupted disable and instance isolation;
they are authored but **not run**, because the Minecraft build prerequisites
are unresolved. The old release test script's broader pre-existing parity
assumptions are not certified by the targeted recipe/resource run.

Independent read-only review found and prompted fixes for stale admissions,
feature closure, logs, receipt loss, capture writer cleanup, shutdown, legacy
selector membership and the absence of a quiescent puppet handoff contract. It found no further blocking source defect in its final
pass. This does not establish loader API compatibility or runtime success.

Evidence: `/workspace/action-services-evidence/final-generation-matrix.json`,
`action-service-tests.log`, `release-resource-tests.log`, `compile-1192.err`,
`moved-release-fixtures.json`; native per-projection generation receipts are beside them.

## Exact blockers and remaining work

1. **Desktop handoff — failed transfer; integration pending.** The supported
   Library resolved-ID preparation succeeded twice. The current materialization
   helper exited 1 twice with `library file transfer failed: download failed`.
   No access-denied or approval-rejection result was returned; the underlying
   download cause was not surfaced. No consumer file exists, so archive or entry hashes are
   unverified. Requested file: `libfile_98757a7c15788191b3f997eb948378aa`, version 0,
   `sfm-cloud-action-runtime-handoff-20261025.zip`; expected archive SHA256
   `3ec28722f5bf9788303b6791aac70d9fe7d1aa0c183379391ccd444e7bf95234`.
   No further transfer retries or guessed URLs were used.
2. **Native compile — failed, not an approval denial.** Command:
   `sfm-propagate-changes --output-format json source project jar --repo-root . --projection sfm-4.34.0-extended/mc-1.19.2 --dependency-profile rust-toolchain --java-home /workspace/.sfm-env/jdk17`.
   Pinned `org.facet:vox-java:0.10.0-rc.5` from `TeamDman/facet` commit
   `f2afdece6c79e64085d2f8c047e22fe16b2c8c54` (confirmed by the lock and
   materialized `.sfm-git-commit`; artifact ID retains suffix `ff6894ba`) failed in
   `VoxRuntimeTest.generatedChannelRoundTripHonorsCreditAndCancellation` with
   `lane is not open: CLOSED` (`VoxRuntimeTest.java:193`, caused by
   `ServiceLane.java:153` through `JavaFixtureClient.java:69` and the test at
   line 186), before SFM javac. The pinned producer command was
   `cargo run --locked --package vox-xtask -- package-java`. Its upstream tests unexpectedly
   create a temporary socket listener. The producer process exited and no Java
   process remained. This was an ordinary failed build, not an access denial. This path was not repeated; tests, hashes and guards were
   not bypassed. No SFM game endpoint was started.
3. **Newer compilation — unavailable prerequisites.** JDK 21/25 installation is
   outside authorization. No substitute toolchains or relaxed guards were used.
4. **Runtime acceptance — pending by task boundary.** No game, locked-screen,
   framebuffer, GUI, world or simultaneous live transport test was performed.

Desktop is at unpushed `ba81a01fd93147b6595c90a841af2aebc71eb789`, 40 commits ahead
of the inspected base. Do not import the whole range or claim desktop equivalence.
The requested minimal handoff is manual-ready yield
`029a601b6e52e027e4034695722ca453eac568a2`, post-yield Explorer
`0aa8bd7dd887ac71ebe990aef7d887dab4a3d74b`, inspection-port stat
`1c122afc96c0616eb51427f5f176c10685e04e74`, and portable projection metadata
`c969392e9e4b1eb77e70755778fad5dd43f01f06`. Apply/check those against the base,
then graft the harness transition/pause hooks and cooperative action/work-tracking hooks into that result. The seven-file
reopen-preset commit remains desktop-owned; this branch does not reimplement it.

## Manual acceptance after prerequisites are authorized

Use disposable instances/worlds and exact `--instance-id` selection. Never use
window focus as a substitute for identity when two games run.

1. Start with both environment flags unset. Confirm no control descriptor,
   service threads or listener; palette status says both disabled. Enable files
   alone and confirm numbered request/response and no listening socket.
2. Enable Vox from the palette, then test both flags at startup. Discover with
   `sfm.exe instance list --output-format json`; invoke
   `sfm.exe action invoke sfm:control/status --instance-id <id>`. Confirm both
   transports coexist in each of two isolated instances.
3. While a puppet runs, read its exact execution ID, acquire a short pause lease,
   verify `puppet_pause_available` for the idle ManagedSession action, exercise registered actions, then resume. Require busy rejection for non-cooperative actions and while tracked server/render work is pending. Verify competing transport rejection,
   lease expiry, queued switch/yield rejection and old-session rejection after
   disable/re-enable. Test interrupted responses without blindly retrying effects.
4. Request capture through each transport, poll its returned capture ID, and
   verify PNG existence only on completed status. Repeat after puppet yield,
   GUI close/reopen and resize. Queue a request before screen replacement and
   require stale-target failure; cancel/disable during readback/write.
5. Lock the desktop. If Minecraft produces new screen/world render content,
   require a later-frame capture; if it suppresses rendering, require bounded
   render-unavailable failure rather than a stale screenshot. Repeat after unlock.
6. Disable one service while the other works; then disable both. Confirm queued
   work/captures are cancelled, leases released, descriptors/ready markers removed,
   and no control listener remains. Exercise normal exit and abrupt-exit stale
   descriptor handling separately.
7. Run unchanged red/green regression assertions across every supported target;
   report generation, javac/JUnit, GameTests and visual evidence independently.

Names/env flags and optional deferred self-disable can be finalized before
publication. Whether to expose the legacy raw-input JSON protocol needs an
explicit product decision; its fault-injection operations are not service APIs.

## Tool readiness

Frozen dependency posture retained; no dependency refresh or declaration edits.
Installed CLI `/workspace/.sfm-env/libexec/sfm-propagate-changes` is revision
`122e4b31`, SHA256 `35374239ffa5562ee5452d884f09ea1fef07f2121fccdd0575c44d9e3472e405`.
Its source is unchanged between that revision and the inspected base, and this
branch changes no CLI source. Existing JDK: `/workspace/.sfm-env/jdk17`.
No installer ran. User must run `install.ps1`: no; the unresolved producer/SDK
prerequisites above need a separate authorized validation step, not an implicit install.
