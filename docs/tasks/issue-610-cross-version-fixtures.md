# Issue 610 across supported Minecraft targets

Source checkpoint branch: `feature/issue610-cross-version-fixtures-20261024`,
based on reviewed `e85c68e4e9ec562d1b0921b9eb95e637fed6b5c1`. The verified remote
main at task start was `e2ba833b2b235835d69475b641e24b22bfd9551a`, an ancestor of
that reviewed source. Desktop main, launcher, puppet and JBRSDK staging remain
with the desktop owner.

## Implemented scope

Twenty new development projections provide the same deterministic fixture for
both releases and all ten supported targets:

- `sfm-4.34.0-issue610-portable/mc-<target>` expects second-pass starvation.
- `sfm-4.35.0-issue610-portable/mc-<target>` expects second-pass refill.

The test is `sfm:issue_610_portable_controls`. It reuses the existing pure
`Issue610VisualControls` scheduler unchanged. The actual manager executes SFML
through the actual `OutputStatement` and energy resource adapter. A narrowly
selected SFM-local capability provider attaches bounded energy stores to test
barrels. It is absent from ordinary release projections; unbound barrels gain no
energy handler. Weak owner references and a closed/removed-owner check prevent
the fixture from retaining worlds or transferring through a removed endpoint.
This provider is not registered as a general Forge/NeoForge energy integration.

The two-FE extraction quantum is deliberately deterministic. Starting the first
machine at 99,975 FE represents a prior 25-FE operation; no actual external
machine operation is claimed. Source energy starts at 1,000,000 FE and is never
refilled. Pass one transfers 24 FE to the first machine and 100,000 FE to the
second. After the explicit drain, baseline leaves the second empty, while fixed
refills it. The first stays at 99,999 FE in both cases. Every completed pass checks
raw FE conservation and counts an iron-shovel marker moved by the same SFML body.

The existing real Mekanism/JDT `issue_610_visual_controls` and frozen capture
fixture remain unchanged in the 1.21.1 visual projections. The portable fixture
is regression coverage of SFM's response to a quantity-sensitive energy source,
not proof that each pinned external provider exhibits the same rounding behavior.

## Manual controls

The original control semantics remain: latch Manual Mode within the first 60
server ticks, or let automation press the same physical buttons. Automation waits
for observed execution/drain acknowledgement and button release. It does not run
a separate transfer shortcut. The manual fixture can remain active for 24 hours
of server ticks; a pressed manager still has a bounded acknowledgement deadline.

1. Press **Run Manager** and wait for one counted body.
2. Press **Drain Second**, which extracts exactly 100,000 FE from the second
   store and checks that the source and first store did not change.
3. Press **Run Manager** again. Read the final baseline/fixed result and FE signs.

Button/lever coordinates relative to the structure are `(2,1,2)`, `(7,1,2)` and
`(9,1,2)`. Run instructions are at `(2,1,1)`; first/second FE signs are at `(1,1,1)`
and `(3,1,1)`. Lamps count completed bodies, not button taps. Source and machine
signs show measured FE; a separate sign says this is not Mekanism/JDT. The fixture
uses a version adapter for pre-1.20 sign messages versus immutable front/back
`SignText` and explicitly sends updates. Completion disarms the manager and
leaves the final state visible. Reset by rerunning the test.

## Coverage matrix

This table describes the **new portable fixture**, not historical client runs.
“Generated” includes exact `OutputStatement` byte identity against the canonical
matching plain release, correct expectation selection, unchanged scheduler,
provider registration and capability-relay boundary checks for both projections.

| Target | Required Java release | Energy API | Generated | Compiled | Automated world passed | Manual/live verified |
| --- | ---: | --- | --- | --- | --- | --- |
| 1.19.2 | 17 | Forge `IEnergyStorage` | Both | Pending JBRSDK | Unrun | Unrun |
| 1.19.4 | 17 | Forge `IEnergyStorage` | Both | Pending JBRSDK | Unrun | Unrun |
| 1.20 | 17 | Forge `IEnergyStorage` | Both | Pending JBRSDK | Unrun | Unrun |
| 1.20.1 | 17 | Forge-namespace `IEnergyStorage` | Both | Pending JBRSDK | Unrun | Unrun |
| 1.20.2 | 17 | NeoForge `IEnergyStorage` | Both | Pending JBRSDK | Unrun | Unrun |
| 1.20.3 | 17 | NeoForge `IEnergyStorage` | Both | Pending JBRSDK | Unrun | Unrun |
| 1.20.4 | 17 | NeoForge `IEnergyStorage` | Both | Pending JBRSDK | Unrun | Unrun |
| 1.21.0 | 21 | NeoForge `IEnergyStorage` | Both | Pending JBRSDK | Unrun | Unrun |
| 1.21.1 | 21 | NeoForge `IEnergyStorage` | Both | Pending JBRSDK | Unrun | Unrun |
| 26.1.2 | 25 | `EnergyHandler` / transactions | Both | Pending JBRSDK | Unrun | Unrun |

The catalog calls the 1.21 target `1.21.0`; its template context is `1.21`.
1.20.1's selected loader is NeoForge but its energy imports still use the Forge
namespace. 26.1.2 follows the existing release regression's `SimpleEnergyHandler`
and `TransactionContext`, with the production resource adapter committing or
rolling back transfers. Actual Java/API compatibility still requires compilation.

The existing `ReleaseEnergyExtractionProgressGameTest` remains selected for all
ten fixed-release targets. The new portable test adds passing baseline
characterization and visible controls; it does not turn the fixed-only regression
into a baseline success test.

## External provider inventory and prerequisites

These are existing locked identities; no dependency declaration or pin changed.
Portable projections select the already-reviewed `locked_dependency_projection`
profile, including its existing Vox Java rc.7 migration. The ordinary release
locks are preserved. Just Dire Things is selected by the existing locked 1.21.1
profile as `curse.maven:just-dire-things-1002348:7463040` (1.5.7).

| Target | Existing Mekanism identity | Adapter evidence / prerequisite |
| --- | --- | --- |
| 1.19.2 | Curse `4644795`; API `1.19.2-10.3.8.477` | Existing source uses `FloatingLong`; actual 1-FE refusal unmeasured here |
| 1.19.4 | None pinned | Exact compatible provider/version and admission required for real-provider proof |
| 1.20 | None pinned | Same missing provider prerequisite |
| 1.20.1 | Curse `4807067`; API `1.20.1-10.4.2.16` | `FloatingLong`; actual quantum unmeasured |
| 1.20.2 | None pinned | Exact compatible provider/version and admission required |
| 1.20.3 | None pinned | Same missing provider prerequisite |
| 1.20.4 | Curse `5316226`; API `1.20.4-10.5.20.41` | `FloatingLong`; actual quantum unmeasured |
| 1.21.0 | Curse `5552293`; API `1.21-10.6.6.53` | Integer-joule source API; actual quantum unmeasured |
| 1.21.1 | Curse `5656332`; API `1.21.1-10.7.4.60` | Existing separate real-provider fixture checks simulated 1 FE → 0, 25 FE → 24 |
| 26.1.2 | `mekanism:Mekanism:26.1.2-10.8.0.86` and `:api` | Existing source-build pin `f33ff1f438caa55d58ef1f0a08091997353afcb8`; transaction-era integration unrun here |

No equivalent JDT setup is pinned for the other nine targets. Availability of a
compatible JDT release is not assumed. Do not infer external rounding behavior
from the Java numeric type alone. The deterministic scenario remains useful even
when an older provider can satisfy a one-FE request. A future provider-specific
run must first characterize the actual pinned adapter and distinguish a source
that exhibits the defect from one that does not.

## Tunnelled capability boundary

The position-invalidation correction applies **starting with 1.20.3**, including
1.20.3 itself, through 26.1.2 in this catalog. It is not propagated to earlier
versions.

- Through 1.20.2, `TunnelledCableBlockEntity.getCapability` forwards the far
  endpoint's `LazyOptional`. The forwarding test attaches a listener and checks
  removal invalidates it once. This is not a position-cache relay.
- From 1.20.3, `BlockEntityCapabilityProvider` queries `ILevelExtension`, and
  `TunnelledBlockCapabilityRelay` watches the far position with
  `ServerLevel.registerCapabilityListener`, then defers
  `invalidateCapabilities(tunnelPos)` to the end-of-tick hook. It also watches
  absent providers so cached nulls can recover.
- The 1.20.3/1.20.4 hook is `TickEvent.LevelTickEvent` at END; 1.21 onward uses
  `LevelTickEvent.Post`. Those adapters and all production relay code are
  unchanged by this task. The generated fixed portable projections contain the
  relay only for the five supported targets at or beyond 1.20.3.

Source locations: `common/capability/BlockEntityCapabilityProvider.java`,
`common/capability/TunnelledBlockCapabilityRelay.java`,
`common/blockentity/TunnelledCableBlockEntity.java`, and the existing
`TunnelledCapabilityForwardingGameTestGenerator` /
`TunnelledCapabilityCacheRecoveryGameTestGenerator` mappings. This is an
applicability audit, not new runtime proof of tunnel recovery on every version.

## Commands and verification boundary

Tested generation/audit, no SDK, dependency retrieval or game launch:

```bash
source /workspace/.sfm-env/env.sh
python3 scripts/issue610-cross-version/audit.py \
  --cli /workspace/.sfm-env/target/debug/sfm-propagate-changes \
  --repo-root /workspace/SuperFactoryManager \
  --output /workspace/scratch/issue610-cross-version/source-audit-reviewed
```

After the SDK owners provide the exact approved JBRSDK/runtime and producer
identities, the following are the current CLI syntax. They are **not executed
qualification commands for this new fixture yet**:

```bash
sfm-propagate-changes source project compile --repo-root . \
  --projection sfm-4.35.0-issue610-portable/mc-1.21.1 \
  --dependency-profile rust-toolchain --java-home "$approved_jbrsdk"
sfm-propagate-changes game-test run-server --repo-root . \
  --projection sfm-4.35.0-issue610-portable/mc-1.21.1 \
  --filter issue_610_portable_controls --java-home "$approved_jbrsdk"
sfm-propagate-changes game-test run-client --repo-root . \
  --projection sfm-4.35.0-issue610-portable/mc-1.21.1 \
  --filter issue_610_portable_controls --java-home "$approved_jbrsdk" --keep-open
```

Repeat with 4.34.0 and each target using its qualified Java/runtime selection.
Both release-characterization tests should pass, but show different second-pass
results. The client command still needs live qualification and coordination with
the desktop owner; it does not claim the Explorer/launcher port exists on all ten
versions. Manual mode uses the lever's initial selection window.

Static `game-test show` also discovered `sfm:issue_610_portable_controls` in all
twenty projections (exit 0), without compiling or launching.

Two executed Rust tests passed: the current catalog selection census and the
item-resource rendering comparison against historical release witnesses. They
cover 54 catalog entries after adding the twenty fixture projections. An initial
short test filter with `--exact` executed zero tests; that invocation is not
counted. The full `check-all.ps1` is unrun: PowerShell and the required nightly
formatter are absent. An existing-toolchain formatter attempt failed because
`rustfmt` is not installed. No component was installed. Other existing compiler
warnings were retained rather than fixed outside this task.

The cloud inventory contains Temurin 17/21 and a Debian 21 runtime, but no JBRSDK
in the checked SDK roots. Current compilation and runtime validation are pending
that coordinated toolchain handoff, not denied or silently substituted with
Temurin. Earlier Temurin-based Explorer qualification remains historical evidence
only. The eighteen unapproved Maven POMs remain unadmitted.

No production Rust CLI code changed: only two catalog tests were updated, so no
CLI installer is needed for generation. The existing CLI is built from
`c5fd329f19c0280cbe4c375c240a783e73ee2224`, SHA-256
`f7cd93ef9f561b2c9ceaca8bbd635a94bacc31d810b55bd75a93bdbb77996dc7`.
No game was launched, no SDK/network/trust configuration changed, and no main
merge or package publication is part of this checkpoint. Generated fixture trees
and large scratch logs are ignored; source and compact receipts are preserved in
Git.

Compact evidence is in [evidence/issue610-cross-version-20261024](evidence/issue610-cross-version-20261024).
The completed audit is `source-audit.json`; earlier scratch audit attempts failed
on the audit's generated-header handling and are not accepted evidence.
Independent read-only review checked the final source and all sixty generated
energy/fixture/provider hashes, with no remaining concrete source issue.
