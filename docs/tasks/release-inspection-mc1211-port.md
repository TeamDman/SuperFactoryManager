# Release inspection on Minecraft 1.21.1

Branch: `feature/explorer-mc1211-port-20261024`, based on reviewed
`704ddb5485173093cad42c07975fa5aabfc247ed`. This is the cloud Explorer port;
launcher/puppet deferred opening and desktop human handoff remain owned by the
desktop demo task. No main merge or publication is part of this branch.

## Scope

The explicit `inspection_workspace` feature is selected only by
`sfm-4.34.0-issue610-visual/mc-1.21.1` and
`sfm-4.35.0-issue610-visual/mc-1.21.1`. It brings the existing comparison runtime,
source-map syntax highlighting, Nucleo matching, Explorer, read-only V3 document
panels and owned-preview policy to the NeoForge/GuiGraphics APIs. It does not
enable the mutable release-review/comment/history workspace.

The comparison hierarchy and classifiers are unchanged: comparison root →
strategy → file pair → representation. Exact bytes, printed debug-only changes
and instruction differences retain their existing evidence boundaries. No new
semantic-equivalence claim is introduced.

The native inspection choice menu executes the same registered actions as the
existing typed palette. It supports keyboard focus, PageUp/PageDown and Cancel.
Comparison progress/errors remain in the Explorer status strip and narration.
The native 1.21 keyboard profile format remains unchanged; Explorer-local Ctrl+F,
Ctrl+Shift+F, F3/Shift+F3 and Find Enter/Shift+Enter use its registered search
commands. Ordinary 1.21 Dev retains its original Canvas path and DRAW registration;
inspection adds the panel-capable V3 registration.

Rendering uses GuiGraphics directly, with four-argument scroll adapters. Nested
clip bounds are transformed from panel coordinates into screen coordinates before
entering GuiGraphics' own scissor stack. Mouse selection retains the existing
scroll-preservation policy. External reveal and keyboard navigation retain their
existing reveal behavior.

## Energy semantics

Catalog-rendered `OutputStatement.java` is byte-identical between each visual
overlay and its matching vanilla release. The path is
`src/main/java/ca/teamdman/sfml/ast/OutputStatement.java` within each projection.

| Release | Bytes | SHA-256, plain and visual |
| --- | ---: | --- |
|4.34.0|34602|`38c34a06467ee9f693716f40a630f2b242237fdfa857b4f1863f804bf87a4de2`|
|4.35.0|34685|`15a99b59b2408c9e45e5a12abe76f409cd789f11785eacf0ed91f0a8add888ab`|

No OutputStatement, launcher, puppet, lock-pin, dependency, trust or network-policy
source changes are included. The offline metadata catalog remains SHA-256
`bd9ce82e5f34a251b472178cea292d3739faaf1595682e9371cdf124dd2b05fd`;
the eighteen additional POMs have not been admitted.

## Validation and commands

The focused 1.21.1 suite passed **46/46 tests** against the final fixed-release
native-built main classes (and earlier against the supplementary direct probe). It exercises preview ownership, continuation identity, viewport/selection/
scroll behavior, Nucleo cancellation and stale results, read-only chrome, syntax
sessions and scissor intersection. The shortcut test proves canonical command
emission and immediate scroll/selection preservation; live dispatch remains a
client check. Direct Java 21 compilation passed 850 main sources plus 18 generated
ANTLR sources, and 220 GameTest sources, against the existing locked native
classpath with annotation processing disabled. These are supplementary checks.

The actual prepared workspace loaded through `SFMInspectionRuntime.read` under
`-Xmx256m`: two comparison roots, 412 representations, and a missing workspace
rejected. The real Rust syntax worker returned eleven Java token spans with the
matching source hash and the Arborium 2.18.1 parser fingerprint. This checks the
worker protocol, not visual rendering or new 1.21.1 release-JAR provenance.

Both final canonical compile-only commands exited **0**, reporting
`compilation: completed` and `immutable_source_lock: true`. They compiled:

| Visual projection | Main, including generated ANTLR | GameTest | Datagen |
| --- | ---: | ---: | ---: |
| 4.34.0 / MC 1.21.1 | 868 | 220 | 15 |
| 4.35.0 / MC 1.21.1 | 870 | 224 | 15 |

JAR packaging and application execution are explicitly `not_performed` in both
reports. The command selects the existing native dependency profile without
resolving the unit-test metadata graph:

```bash
source /workspace/.sfm-env/env.sh
/workspace/.sfm-env/target/debug/sfm-propagate-changes source project compile \
  --repo-root /workspace/SuperFactoryManager \
  --projection sfm-4.34.0-issue610-visual/mc-1.21.1 \
  --dependency-profile rust-toolchain \
  --java-home /workspace/.sfm-env/approved-temurin21/jdk-21.0.8+9
```

Repeat with `sfm-4.35.0-issue610-visual/mc-1.21.1`. No Gradle is used.

Reusable focused harness (all arguments refer to existing files; no download):

```bash
python3 scripts/release-inspection/test-workspace-port.py \
  --projection "$projection_root" \
  --native-javac-args "$native_project/javac-main.args" \
  --main-classes "$native_project/classes" \
  --java-home /workspace/.sfm-env/approved-temurin21/jdk-21.0.8+9 \
  --junit-console /workspace/scratch/release-inspection/combined-picker-tests/junit-platform-console-standalone-1.8.1.jar \
  --vanilla-client /workspace/.sfm-env/cache/minecraft-toolchain/minecraft/versions/1.21.1/client.jar \
  --output /workspace/scratch/release-inspection/port-mc1211/native-focused \
  --inspection-workspace /workspace/scratch/release-inspection/release-built-combined/release.sfm-inspection.json \
  --syntax-worker /workspace/.sfm-env/target/debug/sfm-propagate-changes
```

For the passing cloud fixed-release run:

```bash
projection_root=/workspace/SuperFactoryManager/platform/minecraft/projections/sfm-4.35.0-issue610-visual/mc-1.21.1
native_project="$projection_root/build/sfm-toolchain/native-project/d2f060d92194e74dd7ed950641c3b76a70b308a945920bcfa02f7125aa6d2ccd/project"
```

For a rebuild, set these paths from the selected projection and its new native
build report. `--main-classes` must match that build's `javac-main.args`
`-d` value. The optional final two arguments enable the actual workspace/worker
smoke test. Receipts include input source hashes, the main-class tree digest,
JUnit and vanilla-client hashes, and command exit codes. The existing JUnit
console SHA-256 is
`e15e28559eb4ced29562693355bf7874c55a6cf006c96ae198f7fc75ba914f9f`.

The 1.19.2 Dev regression `run compile` compiled 1,585 main sources with native
source validation enabled, then exited 1 during unit-test metadata preparation.
It requires the still-unapproved `com.google.errorprone:error_prone_parent:2.36.0@pom`
through the already-pinned `error_prone_annotations:2.36.0` test root. The missing
metadata is 15,958 bytes, SHA-256
`3a4cfc8a6bed61eb48e969796fc31ea0d270b63e670599946a61883adb7094dc`,
from `https://repo1.maven.org/maven2/com/google/errorprone/error_prone_parent/2.36.0/error_prone_parent-2.36.0.pom`.
No admission was attempted. Full aggregate tests remain pending authorization;
this is not a successful aggregate run.

Earlier failures are retained in local evidence: the first native GameTest
compile exposed two missing existing size-display mappings (fixed here); one
focused-test assertion assumed the old remappable keyboard path (made conditional
and supplemented by the inspection shortcut test); and the first real-worker
smoke assertion omitted the protocol's `sha256:` prefix (test corrected, worker
unchanged). Initial headless setup lacked the vanilla language resource. Two
compile-only attempts correctly rejected concurrent authored-catalog edits;
final builds run with that catalog frozen.

Ordinary Dev 1.21.1 generated Canvas and multiplexer code remains identical to the
reviewed basis after ignoring blank lines. No ordinary Dev 1.21.1 full build,
1.21.1 client launch, screenshot, GameTest execution, release-JAR packaging, or
full release matrix is claimed by these checks.

Once the owning launcher has entered the world and opened its action surface:

```text
sfm action invoke sfm:explorer/diff open "/absolute/path/release.sfm-inspection.json"
```

The direct integration seam is `SFMInspectionRuntime.read(Path)` on
`SFMInspectionRuntime.WORKER`, followed by `SFMInspectionRuntime.open(prepared,
context, false)` on the Minecraft client thread. Passing `true` appends roots to
an originating Explorer. The loader must retain continuation/host identity and
report failures before yielding to the human.

**Pending desktop prerequisite:** the current launcher property branch for
`sfm.inspection.workspace` requires absent release-review/launch features. The
desktop owner must connect this inspection-only seam to its deferred in-world
opening and explicit handoff. This cloud branch intentionally does not edit
launcher/puppet files. It cannot yet claim a tested single-command in-world demo.

Independent source review identified and checked fixes for scroll signatures,
scissor transforms, ordinary-Dev isolation, choice/continuation ownership, action
registration, and recipe-less read-only preview metadata. Actual nested rendering,
preview replacement/cancellation and menu-overlay continuation still require the
live 1.21.1 client check. The existing 1.19.2 screenshots/recording ZIP remain separate
evidence; they are not relabelled as1.21.1 qualification.

## Operational readiness

The CLI source is unchanged from reviewed production. The command used is
`/workspace/.sfm-env/target/debug/sfm-propagate-changes`, built from
`c5fd329f19c0280cbe4c375c240a783e73ee2224`, SHA-256
`f7cd93ef9f561b2c9ceaca8bbd635a94bacc31d810b55bd75a93bdbb77996dc7`.
User must run install script: **no** for this Java-only port; no CLI rebuild is
needed. The exact previously authorized SDK is Temurin **21.0.8+9**. Dependencies,
lockfiles, trust and network policy remain frozen. The native pipeline uses the
existing locked assets and deterministic generated caches.

Desktop syntax-worker configuration must point at its existing matching CLI:
`-Dsfm.syntax.workerExecutable=<absolute CLI path>` (the default executable name
is `sfm-propagate-changes.exe`). The cloud smoke explicitly supplies this property
and waits boundedly for `CLOSED` with `processAlive=false`; the final smoke log
confirms both. The two native build processes and smoke worker have exited. No
cloud client was launched or left running, and no desktop process was accessed.

The original 1.19.2 recording artifacts and screenshot identities remain in
`release-inspection-live-qualification.md`; this port does not replace that
provenance. Ignored native caches and scratch logs are local evidence, not Git
preservation. The reviewed source checkpoint is `1c122afc96c0616eb51427f5f176c10685e04e74`
on `feature/explorer-mc1211-port-20261024`; the qualification changes follow it.

Qualification evidence is preserved in
[evidence/release-inspection-mc1211-port-20261024](evidence/release-inspection-mc1211-port-20261024):
`qualification.json` contains native report/source/SDK/cache identities and energy
hashes; `focused-receipt.json`, `focused-tests.txt` and `runtime-smoke.txt` identify
the executed tests and worker; `receipt-guard-verification.json` checks that a
simulated process-start failure or timeout cannot retain an old success receipt.
Independent read-only review found no remaining production-source blocker and
verified these evidence boundaries. Live 1.21.1 qualification remains pending with
the desktop owner.
