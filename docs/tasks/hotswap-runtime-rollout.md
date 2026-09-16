# Repeated hotswap runtime rollout

Plan status: Implemented; runtime/GameTest acceptance passed, full-suite caveat below.
Primary lane: 1.19.2. Updated: 2026-09-15.

## Contract and intent audit

The user approved acquiring the fixed release and completing the next steps of
the runtime investigation. Preserve existing source edits and worlds. Do not
force static initialization or replace ordinary constants throughout the code.
Use virtual puppet input rather than the OS cursor. No public runtime fork is
needed unless the fixed complete SDK fails the real game acceptance test.

- R1: Acquire an exact fixed JBR17 SDK and verify its official checksum.
- R2: Prefer the newer installed patch and refuse affected JBR17 at launch and
  attach. A newer helper is not a substitute for restarting an old target.
- R3: Enable disposable puppet execution with JDWP, without retaining the build
  lock that a concurrent compilation/reload needs.
- R4: Run the AE2/Mekanism fixture and normal/crafting/normal terminal round trip
  in one JVM. Record native captures and actual terminal class identities.
- R5: Run the required Rust validation and installer, verify the installed binary,
  and leave the fixture source in its normal-terminal state.

Intent audit: extraction maps the user's acquisition/next-step instruction and
earlier static-state and virtual-input concerns to R1-R5. Traceability maps each
to the tasks below. Omission review retains the conditional fork, single-process
game proof, all active main/GameTest classpaths, and frozen unrelated dependencies.
Earlier investigation details are carried from its durable evidence rather than
claiming the original closed game process was inspected again.

Dependency boundary: only the named runtime acquisition is authorized. Do not
upgrade Cargo, Minecraft mod, or other locks. Preserve pre-existing lock changes.
The acquired SDK is side-by-side with the old installation; no global Java
environment variables are changed. Validate only the 1.19.2 lane here, not other
Minecraft versions or every form of enhanced redefinition.

## [x] T1 / R1: acquire the fixed SDK

Selected official release:
[jbr-release-17.0.14b1367.22](https://github.com/JetBrains/JetBrainsRuntime/releases/tag/jbr-release-17.0.14b1367.22).
Artifact: `jbrsdk-17.0.14-windows-x64-b1367.22.tar.gz`.
SHA-512, verified against the official companion checksum:

```text
d787fdb48cf28886738428621d8f400ca8d95f88aa98f0995997c755b2da94fb0b2997d876bdbe6826002cd09d973d45ae71a4871fad1b36e2afdebe1202b8b3
```

Upstream fix: `9bea6a0004787c3f0a5fbd57e645761240f7f624` (JBR-6648),
confirmed as an ancestor of this release. All nine isolated runtime/agent
matrix cases matched expectations at 20 requested cycles. Fixed-runtime body
and structural changes passed with explicit GC between fresh debugger attaches;
the old runtime failed after one successful enhanced redefine. Moving only the
JDWP library moved the failure in both directions. Mixed DLLs are diagnostic
controls, not a supported installation.

The exact historical ObjectCollectedException was not reproduced byte-for-byte:
the reduced failing control produced broken identities/JDWP 113 while its class
remained rooted. This strongly identifies the defect without promising that all
hotswap failures, static-state issues or mod compatibility are solved.

## [x] T2 / R2-R3: guard and compose the launch

Changes: `src/jdk.rs`, `src/cli/run/sfm_hotswap_helper.java`, client/puppet
adapters, and `src/jar_build/engine{,_run}.rs` in `platform/cli/sfm-propagate-changes`.
Launch rejects affected/unknown JBR17 build families. Target validation reads
`java.lang.VersionProps` through JDI; JDWP's ordinary version response omits the
vendor/build, so it is insufficient. No target methods are invoked for this check.
Unknown target identity fails explicitly rather than bypassing validation.

Validation so far: guard policy rejects four bad/unknown cases and accepts three
fixed/stock cases. The actual old target is rejected before the first redefine,
with its return value unchanged. New helper + fixed target passed five reload/GC
cycles; new helper + stock Microsoft Java17 passed five body-only cycles.
Rust runtime-selection, launch-port and build-lock regressions are included.
Formatter, Clippy, build and 723 Rust unit tests passed (three ignored).
Full-suite completion and the snapshot exception are recorded under T4.

## [x] T3 / R4: real game round trip

Passed in one Minecraft JVM on the complete fixed SDK:

1. Normal terminal: `appeng.parts.reporting.ItemTerminalPart`, fixture 40,492 ms.
2. Method-body reload: `CraftingTerminalPart`, fixture 40,449 ms.
3. Explicit `jcmd GC.run`, then normal-terminal reload: `ItemTerminalPart`,
   fixture 40,491 ms.

Both helper invocations reported one redefined class from two active class
directories. The puppet completed with zero failures and exited normally.
Native captures were retained under
`platform/minecraft/build/sfm-toolchain/artifacts/game-test-preview/runs/game_test_hotswa-20260915-014129-481/`.
No Mekanism invalid-tile update warnings occurred. Existing unrelated worktree
clients were not stopped. The final fixture source uses `AEParts.TERMINAL`.

```powershell
sfm-propagate-changes.exe run client --branch 1.19.2 --hotswap --hotswap-port 5006 --java-home "$env:USERPROFILE/.jdks/jbrsdk-17.0.14-windows-x64-b1367.22" --puppet game_test_hotswap_round_trip --game-test ae_2_mekanism_infusion_bank_autocrafting
sfm-propagate-changes.exe run hotswap --branch 1.19.2 --port 5006 --class-name ca.teamdman.sfm.gametest.tests.compat.multiple.Ae2MekanismInfusionBankAutocraftingGameTest
```

The puppet creates a fresh flat world, runs the exact fixture, captures it, then
waits in the existing exploration request-file protocol. After a reload, submit
the next numbered `{"op":"finish"}` request to the current control session to
advance. Repeat once, restoring `AEParts.TERMINAL` before the final run. Do not
reuse a previous session directory or signal completion before a successful
reload. Each GameTest action resets only a completed previous test's tracking;
it refuses to replace a running test. Native captures and fixture terminal-type
logs establish what actually ran, not just whether JDWP returned success.

Completion requires three passing fixture runs and normal/crafting/normal
terminal identities, without restarting the process. Broader server-command,
block-inspection and chat-tail protocol operations remain separate planned work.

## [x] T4 / R5: install and hand off, with full-suite exception

Installed outside the sandbox using `install.ps1` after the final CLI source
edit. Version: 0.1.1, revision 8794d96fd, build 2026-09-15 01:38:19 -04:00.
Installed and release binary SHA-256:
`5fab81dc99ef2befe8e636ee229c8cf8a51dca15b49f95c28547686c065addad`.
Installed `jdk list` places JBR17.0.14 first; an installed-CLI dry-run launch
with old JBR17.0.6 fails with JBR-6648 before compilation or game launch.

Validation caveat: the `java_analysis_scenarios` integration test compares
snapshots to a moving JDK-source checkout. 34 of 36 actual snapshots differ
only in source identities/derived index fingerprints (or are unchanged).
`String.java` also changed source length/hash; the partial-index scenario now
observes a missing cache rather than the expected partially populated cache.
These expected snapshots were not overwritten. Pin the test's source corpus
and construct its partial-index fixture explicitly as a separate follow-up.
An intermediate rerun also hit Windows' executable lock while our debug CLI
owned the game. After that process exited, the final `check-all.ps1` passed
formatting, Clippy, build, 723 unit tests and nine integration cases before
failing the same `java_analysis_scenarios` snapshot test. The follow-up
`cargo test --all-features --locked --offline --quiet -- --skip java_analysis_scenarios`
passed all 784 remaining tests (three ignored, one explicitly filtered out).
This is not an all-green full suite. Expected snapshots and dependency locks
were not changed to mask the failure. `git diff --check` passed. Runtime and
GameTest acceptance are complete; deterministic source/cache fixtures remain
a separately recorded validation follow-up.

Run `check-all.ps1`, then `install.ps1` outside the restricted environment from
`platform/cli/sfm-propagate-changes`. Verify installed path, version and hash
against the release output. Stop only the disposable process owned by this run;
do not stop another worktree's client. Preserve failed evidence and explain any
unproven acceptance item rather than declaring the game reliable from unit tests.
