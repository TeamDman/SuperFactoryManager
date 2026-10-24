# Picker follow-up and remaining prerequisites — 2026-10-24

This is a local validation continuation, not a replacement recording bundle.
Repository: `/workspace/SuperFactoryManager`, branch
`feature/release-inspection-combined-20261024`, HEAD
`704ddb5485173093cad42c07975fa5aabfc247ed`.
Qualified production implementation remains
`e192e234e0a3019537cb3e23240ac8e5ff886480`. No production source, dependency pin,
network policy, trust store, or main branch was changed. Nothing was pushed or
merged in this continuation.

## Actual client checks

Native Forge 43.4.0 / Minecraft 1.19.2, private task-owned cloud Xorg display :94,
existing JDK 17, exact approved Temurin 21.0.8+9 for the pinned SDK build. The
client uses the already-qualified production build plus the local test fixture
under `fixture/`. No new dependency was installed.

The fixture is a normal Forge mod with two DeferredRegister editor registrations,
`sfm_picker_fixture:external` and `sfm_picker_fixture:external_alt`. They share
the display name `Fixture editor` and delegate to the existing V1 renderer.
F8 on the title screen opens a file-backed production editor context through
the actual preferred-editor factory. A read-only tick observer records text,
screen/editor identity, V1 widget/cursor/selection, V2 context/cursors, preference,
saved-file hash, and factory calls. V3 observation starts after normal loading.
All picker selections, Apply/Cancel/Escape, typing, and save/close operations
were actual X input; the fixture does not select or apply a picker choice.

Passed observations:

- The real picker displays all three built-ins and both extension IDs; duplicate
  display names remain distinguishable. Searching an absent ID shows the actual
  empty-result state; searching the V3 ID selects its real registration.
- Repeated V1 -> external -> V2 -> external_alt -> V1 -> V3 -> external opens
  use the selected registrations. Extension factories receive exact saved text
  and run only on a subsequent open, not while selecting or applying a choice.
- Picker Cancel, Escape, and Apply preserve the open document and all observed
  object/cursor state; applying only changes the preference. Saved bytes remain
  unchanged until an actual save. Multiline text includes Greek and non-BMP
  characters, even though the Minecraft font renders the emoji as a missing glyph.
- V1 save and extension reopen preserve exact bytes. V3's Done saves its actual
  current text. V2 close-discard preserves the previously saved file; Ctrl+S did
  not produce a save callback and is not counted as a passing V2 save test.
- A clean quit/restart retained `external_alt`; its factory was invoked with the
  exact saved document before any preference edit in the new process. The test
  slot was then restored to V1 through the picker and quit cleanly again.

Final trace: 134 events, 13 completed picker cycles (9 preference changes and
4 cancellations), 10 opens, 4 extension factory invocations, and 9 save callbacks.
All 13 picker cycles preserve the fields actually observed. Initialization
preservation fails on 3 opens (V2 once, V3 twice); the separate unedited V3 save
case proves the final-LF loss reaches disk.

The assertions and exact line references are in `event-assertions.json`, generated
by `analyze_events.py` over the actual `events.jsonl`. Passing these assertions
does not mean the separately reported known preservation failures passed.

Failed preservation checks, independently reviewed:

- V2 initialization removes a terminal LF. `TextEditContext` uses
  `initialContent.split("\n")`, then rejoins lines without the discarded empty
  trailing field. Input SHA `79def94567ed45a1fc7f71c4c462947e3269efe75d73da2bdc820207d04faf86`
  becomes `d34a124d1218442d3e2cf06f3a123d4db2c57777a7d6a043372e29811e8db074`.
- V3 initialization and unedited Done also remove a terminal LF. Its geometric
  model/index emits newlines between glyph rows, losing the empty final row.
  The untouched input `67884ddd4056d41c0ae39e75e3e4582f26222acb99eddfaa41d2ef5e257aacbe`
  is saved as `8e4e403a0309f6bb7b21b7eb889b53369f94285d63721ce9707793fb16948b56`.
  Some edited paths recover LF through the retained exact baseline; that does
  not invalidate the reproduced unedited loss.
- The relevant V2/V3/context/model/index source files are unchanged from the
  migration baseline `0918604ac558906c93a9c4ab449400e8f75792ce`. These are existing
  editor behavior defects, not picker overlay mutations. No fix was mixed into
  the already-qualified Explorer/picker integration.

Not covered: undo history, V3 cursor/selection identity, actual third-party editor
implementations beyond normal registration with a V1 delegate, server-backed
Manager inventory saving, newer Minecraft runtime, full aggregate JUnit runner,
or full `check-all.ps1` (PowerShell/rustfmt/clippy remain unavailable).

## Aggregate native-test prerequisite

The native test resolver builds the test dependency closure before applying the
test-name filter. Dev's locked `javaparser-symbol-solver-core:3.26.4` embeds a POM
whose parent is `com.github.javaparser:javaparser-parent:3.26.4@pom`. That parent
defines already-locked Javassist 3.30.2-GA and Guava 33.4.0-jre versions.

Both the parent POM and the entire origin catalog are absent at:

```
/workspace/.sfm-env/cache/minecraft-toolchain/maven/com/github/javaparser/javaparser-parent/3.26.4/javaparser-parent-3.26.4.pom
/workspace/.sfm-env/cache/minecraft-toolchain/maven/maven-metadata-pins.json
```

The exact Maven Central origin works with current network/trust:
`https://repo1.maven.org/maven2/com/github/javaparser/javaparser-parent/3.26.4/javaparser-parent-3.26.4.pom`
returned HTTP 200, 25,473 bytes, SHA-256
`0316ad7574a30bdacae02e45d88edda46578bb5a469cbd65d783787eeb82cca8`.
No allowlist or trust change is required for this URL.

The cache-only resolver requires a schema `sfm:maven_metadata_provision@1`
origin receipt with coordinate, configured HTTPS repository URL, canonical path,
bounded size, SHA-256, and retrieval time. Otherwise unreviewed parent/BOM metadata
could control dependency expansion outside the frozen embedded-POM evidence.
This is an origin/hash admission requirement, not a version upgrade request.

`metadata/proposed-entry-NOT-APPLIED.json` and the fetched POM are scratch-only.
Neither the cache metadata nor locks were modified. Status: **pending reviewed
metadata provisioning**. Further parent/BOM/artifact misses may follow; this is
the first proven blocker, not a claim that one POM completes the entire closure.

## Library screenshots

The current Library skill and prepared-upload reference were read. Its official
three-file helper was obtained through the skill resources and invoked once for
the requested two-file batch. It failed before preparation with:

```
library upload failed: hosted apps tools/list request failed with HTTP status 401
```

A harmless direct Library list succeeded, so this is specifically the supported
helper's hosted-apps bridge authorization; a general Library disconnection is
not established. No retry, direct-write fallback, custom transfer system, or
guessed endpoint was used. Status: **blocked pending helper bridge authorization**.
Confirmed `library_file_ids: []`.

The preserved repository screenshots are:

- `docs/tasks/evidence/release-inspection-20261024/69-accepted-changed-java-lines.png`:
  actual in-game Java diff, syntax colors and removed/added lines with Explorer.
  SHA-256 `6f1efebe10241a002de27e423b928047985e63214fdb0a37efcfe6b3858bc111`.
- `docs/tasks/evidence/release-inspection-20261024/76-accepted-source-changes-clear.png`:
  actual release source comparison, expanded All changes and added test/resource
  entries. SHA-256 `9169086204893865309ba11a2c277ed1c601b7192ce6cd3d41ccf922b977d73a`.

The existing qualified ZIP remains
`/workspace/scratch/release-inspection/recording-workspace-qualified.zip`,
10,029,860 bytes, SHA-256
`e3fa10f11af037133398b832714b3fb98362a1e6371392b596d8c1338102e80e`.

## Reproduction

The normal recording launch remains documented and tested in
`docs/tasks/release-inspection-live-qualification.md`. This follow-up used a
separate runtime slot 2 so the fixture never entered recording slot 1.
After starting the same private cloud display, the test command was:

```bash
source /workspace/.sfm-env/env.sh
DISPLAY=:94 \
XAUTHORITY=/workspace/scratch/release-inspection/cloud-display/display.Xauthority \
MESA_SHADER_CACHE_DIR=/workspace/scratch/release-inspection/cloud-display/shader-cache \
JAVA_HOME=/workspace/.sfm-env/approved-temurin21/jdk-21.0.8+9 \
/workspace/.sfm-env/target/debug/sfm-propagate-changes run client \
  --repo-root /workspace/SuperFactoryManager --projection sfm-dev/mc-1.19.2 \
  --runtime-slot 2 --java-home /workspace/.sfm-env/jdk17 \
  --log-file /workspace/scratch/release-inspection/picker-followup/replay.ndjson \
  --log-filter info
```

It requires the locally compiled fixture JAR in slot 2's `run/mods` and the seed
in `run/picker-fixture/program.sfml`; the final cleanup receipt states whether
the owned test JAR was removed. The fixture bytes and seed remain here for replay.
Never print or package the display authentication file.

The explicit CLI is built at c5fd329f19c0280cbe4c375c240a783e73ee2224; native source
is unchanged through HEAD. Its SHA-256 is
`f7cd93ef9f561b2c9ceaca8bbd635a94bacc31d810b55bd75a93bdbb77996dc7`.
The old installed executable was preserved. User must run install.ps1: **no** for
the explicit commands above. Source/dependency posture remains frozen; the local
fixture is a test input, not a production dependency or substitute artifact.

Final restart, cleanup, preserved-artifact checks and screenshot hashes are in
`final-state.json`; the input event stream remains in the task's `ui-evidence`.
The two completed fixture client sessions exited 0 after actual Quit clicks.
One premature restart launcher was stopped with SIGTERM during preparation,
before Java spawned; it is not counted as a successful launch. The first fixture
attempt lacked pack.mcmeta and was intentionally stopped before UI qualification;
v2 fixed only that local fixture resource. The actual tested fixture v2 JAR is
SHA-256 `408878eace1675e7cd7e325093039bc5f674999f57c2f647f481027a3f30cd44`.
