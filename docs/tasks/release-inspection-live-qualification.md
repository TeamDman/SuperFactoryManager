# Release inspection: cloud client qualification

This continuation supersedes the SDK/TLS/build blockers in the earlier
`release-inspection-recording-integration.md` checkpoint. Work remains on
`feature/release-inspection-combined-20261024`; no main merge or package publication.

## Source preservation and combination

- Original Explorer branch: `feature/release-inspection-explorer-20261024`, remotely
  verified at `dd020cbd8e998063aa9815840eba35ef734290b6`.
- Actual Nucleo integration: `feature/release-inspection-recording-20261024`, remotely
  verified at `3d9214d58d8180a34ab3913bdca2b542eeba4bc1`.
- Combined branch was remotely verified at `c5fd329f19c0280cbe4c375c240a783e73ee2224`.
  It retains migration `0918604ac558906c93a9c4ab449400e8f75792ce`, exact native-roots
  correction in `220eb2da2d447be2a5a88047dbdb140621678de1`, and picker-only integration
  from reviewed `59b1b3e8efb143995446f1add14f5de411e6c71a` in `c5fd329f`.
- `357a3a9728665843dadc60546f344edca50cdf67` adds complete contributed search domains,
  bounded edge traversal, explicit truncated-match evidence, topology-preserving
  ordinary/domain merging, asynchronous comparison status, and the corrected scroll
  test coordinate. The old test clicked the scrollbar track; production pointer
  selection was already preserving scroll.
- `2c9bca6e` moves comparison capture scratch to the game workspace filesystem,
  preserving the producer's 12 GiB free-space check. Directory comparisons reject
  scratch inside either input, including symlinked ancestor aliases, before creating
  files. Both real release-JAR and source-tree comparisons passed the actual Java
  runtime method after this correction; scratch cleanup left no artifacts.
- `33ce46abd30a20bc5e83c2f0048b47a2f6aa6a50` gives comparison containers directory identities so external Find/reveal can traverse their ancestor chain. `e192e234e0a3019537cb3e23240ac8e5ff886480` disambiguates two input roots with the same basename in both context-menu directions and comparison labels.
- Optional modal editor was not imported. No lock pin/version upgrade was introduced.

## Exact approved SDK and trust

Temurin 21.0.8+9 is installed under
`/workspace/.sfm-env/approved-temurin21/jdk-21.0.8+9`.
The official Linux x64 HotSpot archive SHA-256 is
`f2dc5418092c43003db8f9005c4a286e1c0104fea96ccdd49e8ebd037cac9219`.
It was independently matched to the official GitHub expanded-assets page before use;
archive extraction and runtime identity (`Eclipse Adoptium|21.0.8+9-LTS`) passed.

The previously denied GitHub API and `.sha256.txt` requests were not retried. The
separate official expanded-assets endpoint returned 200, and the official archive
redirected from `github.com` to `release-assets.githubusercontent.com` and returned
200. API metadata was unnecessary. No network rule, CA certificate, root filter,
TLS validation setting or global environment configuration was changed.

The explicitly authorized native-roots source correction uses the already-provisioned
filtered trust store. Its SHA-256 remains
`ee7d8a9353d49d866898ce0d49091bcdf207d71e323ebfe0af9d2020e691c99c`.
Pinned Vox rc7 was built with that exact JDK; its artifact matched the existing lock.
Minecraft runs with the existing JDK 17 at `/workspace/.sfm-env/jdk17`.

SDK receipts: `/workspace/scratch/release-inspection/sdk-official-direct/`.
Native binary/trust receipts: `combined-final-native-binary-audit.json` and
`combined-native-trust-review.json` in the same scratch root.

## Real release inputs

Actual catalog-rendered releases `sfm-4.34.0/mc-1.19.2` and
`sfm-4.35.0/mc-1.19.2` were built using the supported frozen recipe
`sfm:released-native-inputs/4.34.0/1.19.2@1`. These are source-built release inputs;
there is no claim of byte identity with a historical published download.

JARs are under each projection's `build/libs/` with names
`Super Factory Manager (SFM)-MC1.19.2-<version>-rust.jar`.
Build, tool, checksum and path receipts are in `combined-release-434-artifact.json`
and `combined-release-435-artifact.json`.

Both release builds used this command shape (run once for each projection):

```bash
source /workspace/.sfm-env/env.sh
/workspace/.sfm-env/target/debug/sfm-propagate-changes source project jar \
  --repo-root . --projection sfm-4.34.0/mc-1.19.2 \
  --released-recipe sfm:released-native-inputs/4.34.0/1.19.2@1 \
  --java-home /workspace/.sfm-env/jdk17
```

For the fixed input, replace only the projection with `sfm-4.35.0/mc-1.19.2`;
the existing supported frozen native recipe is shared.

The prepared workspace is:

```
/workspace/scratch/release-inspection/release-built-combined/release.sfm-inspection.json
```

It has two roots and no missing-input errors. The JAR comparison has 1,021 exact
entries, 5 ordinary changed entries, 23 added, 23 removed, 1 printed-debug-only pair,
and 3 evidenced instruction-view changes. The projection has 1,113 exact pairs,
9 changed, 6 added, 22 moved and 1 removed. Exact bytes, printed instruction equality,
and source formatting observations do not assert semantic equivalence.

The 59 MB workspace passed the production parser with `-Xmx256m`. Shared strategy
aliases retain source mappings and provenance without duplicating parsed surfaces.

Release JAR SHA-256 values:

- 4.34.0: `e719d3999e23c19a6289290157d5cc24813972f0c6d438fb976c3e4bacdf2541`
- 4.35.0: `95f394db8e824815d60d865bbb9bb56be52974bf7d3c5baa38b29b11f241d36f`
- Prepared workspace: `7c3df317f9f71d8f763618d95b661e9284f0ef917f8129bb0a1fe78a74608f78`

The local-only `recording-workspace.zip` in the scratch root packages the embedded
workspace, both built JARs and provenance receipts. It is not committed or published.
Recreate a workspace in a fresh output directory with the checked-in producer:

```bash
python3 scripts/release-inspection/prepare.py \
  --cli /workspace/.sfm-env/target/debug/sfm-propagate-changes \
  --output /workspace/scratch/release-inspection/another-recording-workspace \
  --old-jar "platform/minecraft/projections/sfm-4.34.0/mc-1.19.2/build/libs/Super Factory Manager (SFM)-MC1.19.2-4.34.0-rust.jar" \
  --new-jar "platform/minecraft/projections/sfm-4.35.0/mc-1.19.2/build/libs/Super Factory Manager (SFM)-MC1.19.2-4.35.0-rust.jar" \
  --javap /workspace/.sfm-env/jdk17/bin/javap
```

The executed argument list is `release-built-prepare-command.json`; projection inputs
come from the repository catalog. This preparation command passed for the existing
`release-built-combined` output directory.

## Launch and recording workflow

From this checkout, use the freshly built native tool explicitly. The older installed
CLI was preserved; its installer is not required for this command.

```bash
source /workspace/.sfm-env/env.sh
DISPLAY=:94 \
XAUTHORITY=/workspace/scratch/release-inspection/cloud-display/display.Xauthority \
MESA_SHADER_CACHE_DIR=/workspace/scratch/release-inspection/cloud-display/shader-cache \
JAVA_HOME=/workspace/.sfm-env/approved-temurin21/jdk-21.0.8+9 \
/workspace/.sfm-env/target/debug/sfm-propagate-changes run client \
  --repo-root /workspace/SuperFactoryManager \
  --projection sfm-dev/mc-1.19.2 \
  --java-home /workspace/.sfm-env/jdk17 \
  --inspection-workspace /workspace/scratch/release-inspection/release-built-combined/release.sfm-inspection.json
```

This command was run in the task's authenticated cloud Xorg display `:94`, using the
existing dummy driver and Mesa software rendering. It did not attach to a desktop.
Display authentication is private and excluded from evidence bundles. Display
creation and termination records are retained locally.

The task-owned game (PID 32968), syntax worker (33332), and original display (16137)
were intentionally stopped after acceptance; no remaining PID or display socket was
found. The launcher exit 1 followed requested SIGTERM and is not a startup failure.
An authenticated restart smoke for the same private cloud display also passed and
was stopped. In this same cloud environment, start it in a separate terminal/session:

```bash
/usr/bin/Xorg :94 \
  -config /workspace/scratch/release-inspection/cloud-display/xorg-dummy.conf \
  -auth /workspace/scratch/release-inspection/cloud-display/display.Xauthority \
  -nolisten tcp -noreset \
  -logfile /workspace/scratch/release-inspection/cloud-display/replay-xorg.log
```

Run the client command above in a second terminal/session. The private authentication
file remains local; it is not in the repository or artifact bundle. A different machine
needs its own available display and exact pinned tools; no desktop setup or access is
implied by these cloud commands.

Expand a comparison, a strategy, a pair, then select a representation. Java structured
and textual views use the existing source-mapped syntax worker. Plain javap instruction
text currently has no language syntax provider; it still has actual change decoration
and reports that limitation. Close unwanted preview panels with **Done**. The existing
canvas defaults to wheel zoom; click **Z** to switch to **S** scrolling, then wheel
through a long diff. Shift+wheel scrolls horizontally in that mode. PageDown did not
scroll this canvas during the attempted shortcut check; the wheel control passed.
These are panel-local controls; no saved global pointer preference was changed.

For arbitrary selected inputs, add their exact native paths as Explorer roots, Ctrl-click
two rows and right-click. **Show diff** offers both explicit old/new orientations.
Native roots can be added through the existing action (use canonical native URIs;
for these paths the accepted spelling has no trailing slash):

```
sfm action invoke sfm:explorer/root/add focused file:///workspace/SuperFactoryManager/platform/minecraft/projections/sfm-4.34.0/mc-1.19.2/src
sfm action invoke sfm:explorer/root/add focused file:///workspace/SuperFactoryManager/platform/minecraft/projections/sfm-4.35.0/mc-1.19.2/src
```

The command palette also accepts:

```
sfm action invoke sfm:explorer/diff compare "/absolute/old.jar" "/absolute/new.jar"
sfm action invoke sfm:explorer/diff compare "/absolute/old/tree" "/absolute/new/tree"
sfm action invoke sfm:explorer/diff open "/absolute/workspace.sfm-inspection.json"
```

Directory comparisons are literal directory inputs (excluding Git administration),
not inferred source-only projections. Select the desired source subtree if build
outputs are present; the prepared release workspace uses the repository's projection
catalog to select its source files precisely.

Find and Filter have independent `N` buttons for Nucleo and `H` highlight toggles.
Literal, legacy fuzzy, regex and case controls remain. Nucleo preserves tree order;
it does not rank/reorder entries. Whole-word plus Nucleo reports unsupported.

## Evidence and test boundaries

All paths below are under `/workspace/scratch/release-inspection/`.

- `ui-evidence/`: actual X display PNG captures and timestamped input events. No mock
  screenshots, generated imagery, HTML report or rendered film is used.
- `accepted-dev-jar.*` and `accepted-dev-artifact.json`: full native main/gametest/datagen compilation and JAR packaging passed at `e192e234`; `accepted-focused-junit/` passed all 60 focused tests against those compiled production classes with no production overlays.
- `combined-dev-jar.*`: full native Dev main/gametest/datagen compilation and JAR
  packaging passed at c5fd329f.
- `combined-focused-junit/`: 55 actual focused JUnit tests passed with compiled
  production classes and Minecraft language resources. The initial harness omitted
  those resources; a subsequent single failure was the test's scrollbar coordinate.
- `reviewed-search-junit/`: 60 focused tests passed with the changed production classes,
  including the existing match-domain tests.
- `combined-picker-tests/`: 13 isolated picker JUnit tests passed.
- `qualification-navigation/`: actual native/JAR/source fixtures, production
  parser/resolver, real Nucleo adapter and core checks, 60,001 coordinate endpoints,
  archive admission, FIFO/symlink rejection, and 22 complete search-domain assertions
  including the actual loader merge and real lazy ancestor reveal. `replay-inputs.zip` contains reusable inputs.
- Earlier compilation interrupted by source immutability guards is retained as failed
  evidence, not counted as a successful build.

Final client `session-8NVSTT` ran `e192e234`. Its captures verify:

- `53`: exactly two initial comparison roots; `55`: Nucleo Find reveals a nested
  class pair from collapsed roots (61 matches, complete domain).
- `58`/`59`: turning highlighting off retains filtered rows; `60`/`61`: the same
  abbreviated query has zero literal matches, while the full name matches literally.
- `62`/`63`: expanding a bottom row leaves the upper Explorer rows pixel-identical.
  `55`/`56` verifies pointer selection likewise. Rectangle checks are recorded in
  `scroll-pixel-checks.json` alongside the untouched PNGs.
- `65`/`69`: real Java syntax and colored changed extraction lines in an 825-line
  structured diff. The syntax worker published 1,812 projected spans / 21,615 glyphs.
  The Explorer body stayed pixel-identical while the preview scrolled.
- `72`/`74`/`76`: two actual `src` inputs, distinct direction labels, and a successful
  appended source comparison with 47 literal-directory changes. This differs from
  the catalog-prepared projection's 38 changes because its input selection differs.

`qualification.json` and the release/Dev artifact receipts are checked in with the
screenshots. The complete chronological raw capture set and input events stay in
scratch. The Dev catalog currently declares version 4.34.0; it was not silently bumped.
The compared release artifacts are separately built 4.34.0 and 4.35.0 inputs.

Actual interactive capture `38-interactive-jar-success.png` shows a third comparison
root added after selecting the two real release JARs; `39-interactive-jar-strategies.png`
shows the instruction/debug/exact groups. `46-working-tree-changes.png` shows an
added directory-comparison root expanded through All changes to real projection
source/generated-resource changes. These captures ran production commit `2c9bca6e`.

The native aggregate `test run` remains blocked before test execution by missing
reviewed public Maven metadata for
`com.github.javaparser:javaparser-parent:3.26.4@pom` and its origin pin in the offline
Maven metadata cache. The exact missing file is
`/workspace/.sfm-env/cache/minecraft-toolchain/maven/com/github/javaparser/javaparser-parent/3.26.4/javaparser-parent-3.26.4.pom`,
with its reviewed origin entry in `maven-metadata-pins.json`. Provisioning remains
pending; denied metadata routes were not retried. No metadata pin was fabricated. Direct focused JUnit is a
separate check, not a claim that the aggregate runner passed.

PowerShell, rustfmt and clippy are unavailable; full `check-all.ps1` is unrun. No
installation of those tools was inferred. Java 1.19.4 and newer client runtime
qualification remain unrun. The optional modal editor remains separate. Actual picker-screen interaction is unrun;
its registration/model/guard integration compiled and its 13 focused tests passed.

Optional control-descriptor publication failed at `/home/agent/.local/share/teamdman`
because that location is read-only. The client and Explorer still ran; UI qualification
used the task-owned X display. No home-directory configuration was changed, and this
is not a claim of a separate control-protocol handshake.

The original general JAR comparison failed because system `/tmp` had 7.8 GiB free,
below the reused producer's unchanged 12 GiB admission requirement. This was an
actual runtime failure, not a network or permission denial. Production-method
replays are retained under `runtime-compare-probe/`, including successful real JAR
and actual projection `src` tree comparisons and scratch-containment rejection checks.

Screenshot copies and their SHA-256 manifest are checked in under
`docs/tasks/evidence/release-inspection-20261024/`. These show the source commit
identified by each receipt. The complete chronological capture set remains in scratch.

Independent source review of the final four code commits found no release blocker.
Two nonblocking follow-ups remain: search and document reads share one read worker,
so a large query can delay opening a representation; process/temp cleanup could be
made more resilient when a descendant outlives its parent or an early deletion fails.
No forced-termination or exceptional cleanup-failure claim is made by the happy-path
interactive qualification.

## Operational readiness

- Tested code: `e192e234e0a3019537cb3e23240ac8e5ff886480` on
  `feature/release-inspection-combined-20261024`; subsequent evidence commit is docs only.
- Handoff native executable: `/workspace/.sfm-env/target/debug/sfm-propagate-changes`,
  revision `c5fd329f19c0280cbe4c375c240a783e73ee2224`, SHA-256
  `f7cd93ef9f561b2c9ceaca8bbd635a94bacc31d810b55bd75a93bdbb77996dc7`.
  Final `--version` passed and native source diff from that build to tested HEAD is empty.
- User must run `install.ps1`: **no for the explicit handoff command**. The CLI was
  built with locked/offline Cargo; the existing installed wrapper was preserved.
  Its older executable is `/workspace/.sfm-env/libexec/sfm-propagate-changes`, SHA-256
  `35374239ffa5562ee5452d884f09ea1fef07f2121fccdd0575c44d9e3472e405`.
- Dependency posture: frozen, with the expressly approved two-file native-roots
  correction in Cargo.toml/Cargo.lock. No package versions or toolchain pins changed.
  Exact approved Temurin and already-pinned Vox/Forge/mod inputs were verified; no
  new developer/reference repository or unpinned substitute was introduced.
- Final Dev cache: `207d064b51debfb765ddd274da971ed6fa41f9a3ec821bf0a1417af4de930c39`
  under the selected projection's `build/sfm-toolchain/native-project/`.
- Final runtime state: all task-owned game/display/syntax processes stopped. No broad
  process termination, repository reset or user cache deletion occurred.
- Core workspace, actual matcher integration and picker-only source integration are
  checkpointed. Optional modal editor remains outside this branch; no new stretch
  work is claimed. Remaining manual/test prerequisites are listed above.
