# Issue 610: manual and automatic recording controls

The new `issue_610_visual_controls` GameTest is available only in the two MC1.21.1
visual projections. It leaves the existing `issue_610_visual_fixture`, its capture
puppet, saved evidence and production transfer code unchanged.

## Controls and sequence

After setup, there is a 60-server-tick selection window (three seconds at20TPS).
Flick the labeled MANUAL MODE lever during that window to latch manual mode;
turning it off again does not resume automation. Otherwise automatic mode is
selected. Later lever changes do not alter the selected mode. Wait for the status
sign before using the manager button.

Controls are relative to the structure origin: manager button(2,1,2), drain
button(7,1,2), manual lever(9,1,2), and lamps(7,2,4)/(9,2,4). Start within reach
of the lever when selecting manual mode for a recording.

1. Press **RUN MANAGER** once. It is a normal stone button attached to the manager,
   running one `EVERY REDSTONE PULSE` body on its rising edge. Holding it produces
   no extra bodies; wait for it to release before another press.
2. After the first run, inspect both droppers. The first is99999/100000FE and the
   second is full. Press **DRAIN SECOND**. This changes only the second dropper to
   zero, representing intervening work while the first machine stays idle.
3. Press **RUN MANAGER** again. On the baseline the second stays empty; with the
   fix it fills to100000FE. The first remains99999FE in both cases.

Automation calls the same vanilla `helper.pressButton` actions in this order,
not an alternate hidden energy-reset path. The drain is handled by the same
powered-button edge observer in both modes. It never refills the source or
changes the first dropper. An early/repeated drain is ignored until the first
completed pass. Release an early press and press again after run one; holding
the drain button does not defer the action. A second manager pass before draining is a fixture failure.

Automatic actions wait for actual execution/drain acknowledgement, then another
40server ticks (two seconds at20TPS) and release of both buttons. This exceeds a
stone button's20tick pulse; acknowledgement is not inferred from a delay.
Each issued automatic action must acknowledge within100ticks (five seconds at
20TPS), with no implicit retry. Manual mode has no short idle/selection timeout;
the GameTest remains active for up to24hours of server ticks. After an actual
manager press, missing execution acknowledgement still fails within100ticks.
Tick durations expand if the server is running below20TPS.

## What the indicators prove

The same SFML body transfers one iron-shovel marker from RunTokens to RunCounter
after its energy statements. The test counts the actual counter inventory,
checks marker conservation, and rejects multiple completed bodies between polls.
The lamps are powered from those counts, not the input button circuit:

| First lamp (`>=1`) | Second lamp (`>=2`) | Completed bodies |
| --- | --- | --- |
| Off | Off | None |
| On | Off | One |
| On | On | Two or more |

The measured scenario accepts exactly two. The donor starts with three markers
and the counter has spare capacity; do not edit either inventory or add item
transport. Marker counts witness completed bodies, not a body that began and
threw before its marker statement. Program errors and missing markers are
terminal diagnostic failures rather than successful counts. Multiple queued
pulses are rejected; the existing per-manager-tick hook is not used as a body
counter.

At completion the manager changes to an empty redstone-trigger program and the
test succeeds, leaving the energy states, lamps and result sign visible. On
failure the manager is also disarmed, the test fails immediately and the sign
points to the server log. Reset by rerunning `issue_610_visual_controls`; the
structure and all inputs are recreated. The lever is not a reset control.

## Mechanics and raw evidence

Setup performs the real25FE drop operation, leaving99975FE. The pinned source
simulates25FE as24FE and1FE as0; simulation is checked to be non-mutating. The
first program pass leaves99999FE and fills the second dropper. The source is
large enough for both passes and never refilled between them. Idle states are
checked on every poll. Each completed pass checks exact raw FE/joule conservation
and logs `ISSUE610_CONTROLS pass=... actualRuns=... firstFE=... laterFE=...
sourceJ=... movedFE=...`. The drain is separately logged as simulated intervening
work, not actual captured gameplay work.

The baseline expects second-pass0FE; the fixed projection expects100000FE via
`quantity_sensitive_extraction_progress`. Both tests should pass their own
characterization assertions. Existing red/green progress-regression tests keep
their prior semantics. No fix for the unrepresentable1FE top-up is claimed.

## Non-graphical checks and later runtime plan

From the repository root, use the current installed native CLI. No Gradle or CLI
installation is needed for these Java/test-only changes. Dependencies and lock
files remain frozen.

```powershell
sfm-propagate-changes test run --repo-root . --projection sfm-4.35.0-issue610-visual/mc-1.21.1 --filter Issue610VisualControlsTests --log-filter info --log-file <fresh-unit-log>
sfm-propagate-changes source project compile --repo-root . --projection sfm-4.35.0-issue610-visual/mc-1.21.1 --dependency-profile rust-toolchain --java-home C:\Users\Teamy\.jdks\jbr-21.0.11 --log-filter info --log-file <fresh-fixed-compile-log>
sfm-propagate-changes source project compile --repo-root . --projection sfm-4.34.0-issue610-visual/mc-1.21.1 --dependency-profile rust-toolchain --java-home C:\Users\Teamy\.jdks\jbr-21.0.11 --log-filter info --log-file <fresh-baseline-compile-log>
```

The native-only `source project compile` route retains source/SDK/lock checks and
does not compile or execute JUnit. The broader `run compile` route also prepares
JUnit and is currently blocked, like `test run`, because the checked-in1.21.1
lock lacks `org.javassist:javassist:3.30.2-GA`. Do not refresh the lock as part of
this fixture task. Seven JUnit cases are added but execution is unverified.

## Verified source checkpoint, 2026-10-21

- Native-only compilation passed for both baseline and fixed visual profiles,
  using Java21. Compilation receipts say application execution and JAR packaging
  were not performed.
- An independent JDK-only probe of the exact pure scheduler passed8 behavioral
  scenarios/48 assertions, without Minecraft, JUnit or external dependencies.
- Both fixture templates fully expand and differ only in the intended fixed
  expectation. No unit framework or world acceptance is implied by that proof.
- The old capture fixture, projections catalog and dependency lock are unchanged.
  One stale unowned368B generated fixed-profile Buffer loot file was preserved
  with SHA256 outside the compiled source tree so the ownership guard could
  remain enabled; no projection directory was deleted.
- CLI source was not changed. Installed CLI revision122e4b31e remained valid;
  user install required:no. No game/server/client was launched.

Raw build/probe/error/preservation receipts are in the local task evidence
directory `C:\Users\Teamy\Documents\Codex\2026-10-13\task-9\fe-visual-controls-20261021`.

## Linux cloud handoff

The fixture requires no captured images, saved worlds, generated structures,
report assets or unpublished baseline JAR. Its Java inputs construct the complete
world, and both named visual profiles are already in the repository. Keep cloud
test work on a feature branch based on the fixture commit on `main`.

Prerequisites are a full Linux JDK 21 (not a JRE), Git, Cargo/Rust with edition
2024 support, and a C/C++ compiler/linker for native dependencies. The desktop
CLI was built with Rust 1.96.0; a minimum supported Rust version is not asserted.
Cold preparation needs network access to crates/Git, Maven/Curse, Mojang and
NeoForge. No graphical client, display server, GPU or Xvfb is needed for the
dedicated GameTest server. Select the lever only in a later graphical recording;
the headless run uses automatic mode after the initial 60 ticks.

### Portable scheduler check

The committed probe is the same eight-scenario, 48-assertion JDK-only check used
on the desktop. It compiles only the pure scheduler into a temporary directory
and removes that directory afterwards. It needs neither Minecraft nor JUnit:

```bash
export JAVA_HOME=/absolute/path/to/full-jdk-21
bash scripts/issue610-control-probe/run.sh
```

### Existing cold-Linux blocker, not repaired by this fixture

Fresh Linux toolchain preparation has not been run. The pinned Vox source-build
recipe records its output as `vox\java\target\vox-java-0.10.0-rc.5.jar`.
The schema-4 adapter currently preserves that value and the resolver joins it
directly to the checkout path. On Linux, backslashes are filename characters;
output discovery after a cold build is therefore expected to fail. Resolve this
tooling issue separately, preserving the pinned artifact identity, before
claiming a portable headless result. Do not refresh dependency locks or substitute
an arbitrary cached JAR to hide the failure.

Both profiles share the checked-in MC 1.21.1 lock with SHA-256
`3e9ad80f6a8ffda904a732529c0babeaace6c6d946a97e5845144b9275af9163`.
It pins NeoForge 21.1.206 and Just Dire Things 1.5.7 (Curse file 7463040).
Of 96 artifacts, 93 are Maven, two are HTTP, and Vox is source-built from
`https://github.com/TeamDman/facet` at
`f2afdece6c79e64085d2f8c047e22fe16b2c8c54`, using
`cargo run --locked --package vox-xtask -- package-java`. Reproducing the locked
Vox artifact hash on Linux remains unverified. No private cache is required by
the fixture itself; cold dependency/toolchain reproducibility is a separate gate.

The missing locked `org.javassist:javassist:3.30.2-GA` blocks the regular JUnit
route described above. Source inspection confirms `game-test run-server` uses
the run pipeline, not that JUnit stage, so that omission does not itself prevent
the focused server command. The seven JUnit cases remain unexecuted.

### Conditional headless commands

After the Vox preparation issue is resolved, from a fresh checkout root:

```bash
set -euo pipefail
export JAVA_HOME=/absolute/path/to/full-jdk-21
tools_dir="$(mktemp -d /tmp/sfm-fe-cloud.XXXXXX)"
export SFM_PROPAGATE_CHANGES_CACHE="$tools_dir/cache"
log_dir="$tools_dir/logs"
mkdir -p "$log_dir"

cargo build --locked --release -j 2 \
  --manifest-path platform/cli/sfm-propagate-changes/Cargo.toml \
  --target-dir "$tools_dir/target" --bin sfm-propagate-changes
cli="$tools_dir/target/release/sfm-propagate-changes"

bash scripts/issue610-control-probe/run.sh
for variant in fixed baseline; do
  if [ "$variant" = fixed ]; then
    profile=sfm-4.35.0-issue610-visual/mc-1.21.1
  else
    profile=sfm-4.34.0-issue610-visual/mc-1.21.1
  fi
  "$cli" source project sync --repo-root "$PWD" --projection "$profile"
  "$cli" source project compile --repo-root "$PWD" --projection "$profile" \
    --dependency-profile rust-toolchain --java-home "$JAVA_HOME" \
    --log-filter info --log-file "$log_dir/$variant-compile.ndjson"
  timeout --signal=INT --kill-after=30s 10m \
    "$cli" game-test run-server --repo-root "$PWD" --projection "$profile" \
    --java-home "$JAVA_HOME" --require-portable-artifacts \
    --filter issue_610_visual_controls --log-filter info \
    --log-file "$log_dir/$variant-server.ndjson"
done
```

Cold bootstrap/compilation is separate from the ten-minute server-command bound;
monitor its actual progress and record its own resource/time budget. Retain the
temporary directory and raw logs as evidence. Run the profiles sequentially,
require exactly one selected/completed test per successful server attempt, and
check the distinct baseline/fixed assertions rather than process exit alone.
The current CLI allows up to three GameTestServer launch attempts with no public
override. This shell timeout bounds the total command, not a single launch;
report actual attempts. Verify the owned process tree has stopped after any
timeout and stop only proven owned children if necessary. No server or graphical
client was launched on the desktop for this handoff.

## Proposed world validation, not performed

World validation is a separate controlled step. Proposed first: one headless
server at a time, selector `issue_610_visual_controls`, first fixed then baseline,
using `sfm-propagate-changes game-test run-server --repo-root . --projection
<visual-projection> --filter issue_610_visual_controls`. Bound each entire run to
ten minutes, track the owned CLI/Java process tree and logs, require exactly one
discovered/completed test, and stop only owned processes on timeout. The automatic
sequence normally needs about eight seconds of server ticks after setup. Report
that plan before launching; no game runtime is authorized merely by this file.

A later human video should validate each graphical instance separately before a
deliberate bounded dual-client session. Use identical pinned MC/mod/config inputs
and separate game directories/worlds, then match camera/framerate settings.
Given the previous freeze, do not launch two clients concurrently without that
separate controlled plan and resource/PID monitoring.

Once a controlled graphical session has been agreed, the single-profile command
is `sfm-propagate-changes game-test run-client --repo-root . --projection
<visual-projection> --filter issue_610_visual_controls --keep-open`. Do not launch
both profiles concurrently from this instruction alone. The game must stay open
for the user to choose the lever and press the controls; reset by rerunning the
same test. Physical interaction, indicator appearance and manual world timing
remain unverified until that controlled game validation.
