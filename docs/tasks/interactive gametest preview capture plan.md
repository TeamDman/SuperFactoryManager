# Interactive GameTest Preview Capture Plan

**Plan status:** Initial 1.19.2 implementation in progress; the first
`Move1StackDirect` visual acceptance run is complete (2026-07-15). Broader
failure-path and cross-version coverage remains planned.

**Superseded for command and lifecycle ownership:**
[Client execution, developer-world, and static test catalog cleanup plan](client%20execution%20cleanup%20plan.md).
This document remains the implementation history for the original preview
capture work.

## Goal

Add a dedicated SFM run command that launches one visible, isolated
single-player client and executes selected, declarative *game puppets*. A
puppet can create a world, run one or more ordinary GameTests, position the
player/camera, interact with real blocks and GUIs, and request native Minecraft
screenshots. The harness returns to the title screen between puppets, so a
single client launch can produce a collection of independent visual artifacts.

The first puppet is a thorough walkthrough of `Move1StackDirectGameTest`: an
elevated eight-angle overview of the completed transfer, followed by real GUI
captures of the source barrel, destination barrel, manager, and disk/program
views. This is a developer-preview/artifact workflow first; image snapshot
comparison remains future work.

## Existing foundation

- `sfm-propagate-changes run client-puppet` is the existing high-level
  correctness mechanism: it creates a flat integrated world, runs the selected
  ordinary GameTests, evaluates their result, and exits after its keep-open
  delay.
- `SFMClientRunHarness` already owns title-screen detection, deterministic
  superflat-world creation, integrated GameTest execution, pause suppression,
  and the client exit lifecycle.
- `@SFMGameTest` and `SFMAnnotationUtils` already use Forge scan data. The scan
  data contains both the annotated class and `memberName`, so an independently
  discovered method annotation is supported by the current infrastructure.
- Local Minecraft 1.19.2 sources show that F2 invokes
  `Screenshot.grab(minecraft.gameDirectory, minecraft.getMainRenderTarget(),
  ...)`. Preview capture must invoke that same native render-target path, never
  desktop/window capture. Captioned captures compose a temporary render target
  and hand that target to `Screenshot.grab`, preserving the native write path
  and screenshot hooks.

The new system is a second client-puppet behavior, not a replacement for the
existing all-GameTest correctness run.

## Public contract

The initial command is deliberately puppet-oriented, rather than taking a
GameTest filter directly:

```powershell
sfm-propagate-changes run game-test-preview `
  --puppet 'move_one_stack_direct_walkthrough'
```

- `--puppet` is required. It accepts the same qualified-or-unqualified,
  comma-separated `*`/`?` selector grammar as GameTests, but selects puppet
  names—not GameTests. Zero matches and duplicate discovered puppet ids are
  errors.
- A puppet itself declares the GameTests it needs. This prevents a CLI request
  from accidentally running every GameTest and gives the author control of the
  camera, timing, interactions, and captures around the test.
- All selected puppets start at the title screen. The harness invokes one
  puppet, waits for its declarative action queue to finish, returns to the
  title screen, resets transient client state, and then invokes the next one.
  The game exits only after all selected puppets have reached a terminal result.
- A puppet failure is recorded with its action and diagnostic, returns to title
  where safe, and does not prevent later selected puppets from running. The
  command returns failure at the end if any puppet failed.
- The command owns working worlds and generated output. Puppets may choose
  capture *names*, but never arbitrary filesystem destinations.

`run client-puppet --filter ...` remains the existing GameTest correctness
command. It continues to select GameTests and exit from its test result; it
  does not discover or invoke `@SFMGamePuppet` classes.

## `@SFMGamePuppet` model

Add a marker class annotation and discovery layer in the GameTest source set.
Each puppet definition lives in its own descriptively named class, just as an
ordinary `SFMGameTestDefinition` does:

```java
@SFMGamePuppet
public final class Move1StackDirectWalkthroughGamePuppet {
    public static void run(SFMGamePuppetHelper puppet) {
        // Declare actions; do not drive Minecraft synchronously here.
    }
}
```

The implementation must enforce this exact contract before launching a world:

- `@SFMGamePuppet` has no fields. Its canonical puppet id is computed from the
  owning class name, exactly following the `SFMGameTestDefinition.testName()`
  convention: strip the `GamePuppet` suffix, convert the remaining simple class
  name to snake case, and qualify it as `sfm:<name>`. Thus
  `Move1StackDirectWalkthroughGamePuppet` becomes
  `sfm:move_1_stack_direct_walkthrough` without a manually maintained id.
- A definition class contains exactly one `public static void
  run(SFMGamePuppetHelper)` method. Overloads, instance methods, inaccessible
  methods, duplicate computed ids, and malformed signatures fail discovery
  with the class/method location in the diagnostic.
- Discovery considers only `ElementType.TYPE` scan entries, loads the annotated
  class through `SFMAnnotationUtils`, resolves its `run` method, verifies that
  method's signature, derives the class-based id, and invokes it once to
  *declare* a puppet action plan. It must not instantiate arbitrary definition
  classes.
- The annotation and discovery code are shared source. Only low-level
  Minecraft client operations beneath the helper may need
  `@MCVersionDependentBehaviour` adapters on later branches.

Suggested initial files are `SFMGamePuppet.java` (the annotation),
`SFMGamePuppetDiscovery.java`, `SFMDiscoveredGamePuppet.java`, and
`SFMGamePuppetHelper.java` beside the existing GameTest infrastructure.

## Puppet lifecycle

```text
Title screen
  -> discover and select puppet methods
  -> invoke one method to declare its action queue
  -> create that puppet's fresh world
  -> run requested GameTest(s) and await their result
  -> camera / interaction / screenshot actions
  -> harness disconnects back to title screen
  -> next selected puppet
  -> write result markers and exit
```

The annotated method is not called repeatedly from client ticks. Its helper
records a validated, serial action plan; the harness advances that plan on the
proper client or integrated-server thread and owns timeouts, errors, and title
screen return. An empty plan is invalid. A successful queue implicitly requests
the title-screen return—puppet authors must not call `Minecraft.stop()`, manage
world saves, or hand-navigate menus to finish their definition.

Each puppet creates a fresh, deterministic world with a puppet-id-derived save
name under the dedicated `runGameTestPreview` working directory. The helper
offers the high-level setup requested for this workflow:

- `createFreshFlatWorld(...)` with creative mode, hard difficulty, a fixed
  seed/preset, fixed daytime, clear weather, no weather/daylight cycle, and no
  mob spawning;
- `runGameTest("move_1_stack_direct")`, which resolves exactly one normal
  `SFMGameTestDefinition`, starts it in the integrated server, and returns a
  handle containing its result and absolute structure origin;
- bounded waits for world, GameTest, render, and screen conditions;
- deterministic player/camera placement relative to that GameTest origin;
- real block/item use and screen recognition; and
- named native screenshot requests.

World cleanup is controlled by the Rust launcher and only ever deletes the
known preview world/staging roots after validating their paths. No puppet may
touch an ordinary developer save.

## `SFMGamePuppetHelper` action vocabulary

The helper is a narrow, typed action builder rather than a client-side escape
hatch. Its initial operations should cover:

1. create the fresh preview world and await the local player/server;
2. run an ordinary GameTest by exact id and await a required success;
3. obtain positions relative to the completed GameTest's absolute origin;
4. place/fly the observer at an explicit position and set yaw/pitch or look at
   an explicit target;
5. wait for chunk/world state and a fixed render-settle barrier;
6. use a block through Minecraft's normal client interaction/controller path,
   then await a screen predicate with a bounded timeout;
7. close a screen, restore a defined HUD/camera profile, and capture a named
   screenshot with a styled `Component` caption.

The helper must serialise these actions. It may schedule server work through
the integrated server and client work through the client/render thread, but
definition code never performs raw thread hopping. It must not expose operating
system input injection, raw mouse events, arbitrary filesystem writes, or direct
assignment to `Minecraft.screen`.

The harness, not the definition, disconnects to title after the plan succeeds
or fails. This makes multiple puppets composable without relaunching Minecraft
and prevents a bad definition from stranding the command in a world.

## First puppet: `move_one_stack_direct_walkthrough`

`Move1StackDirectGameTest` already gives a compact, meaningful scene:

- manager: local `(1, 2, 0)`;
- source `SFMBlocks.TEST_BARREL`: local `(2, 2, 0)`, initially loaded with
  64 dirt and labelled `a`;
- destination `SFMBlocks.TEST_BARREL`: local `(0, 2, 0)`, labelled `b`;
- manager disk program: `INPUT FROM a` then `OUTPUT TO b` every 20 ticks.

The first puppet runs that existing test to required success, so its world-state
evidence is the actual end state: source barrel empty, destination barrel
containing 64 dirt, and manager program/labels configured by the normal test.
It then declares this capture sequence:

1. eight elevated, clean world captures around the structure at 45-degree
   increments (`overview-00` through `overview-07`), looking toward the
   structure centre rather than from ground level;
2. a real interaction with the source test barrel, waiting for its inventory
   screen and capturing `source-barrel`;
3. the equivalent destination-barrel interaction and `destination-barrel`
   capture, visibly demonstrating the transferred stack;
4. a real manager-block interaction and `manager` capture; and
5. ordinary in-game navigation from the manager/disk UI to the program editor
   or disk view, captured as `disk-program`.

The exact camera offsets and screen predicates live in the puppet definition,
so they remain readable alongside the test-specific knowledge. The test itself
remains a correctness test: no production code, desktop automation, or
hand-authored fake GUI is introduced to make the pictures look right.

The initial walkthrough documents the completed transfer. If a later visual
story needs pre-transfer or intermediate frames, add explicit, opt-in GameTest
checkpoint coordination as a separate extension; do not freeze arbitrary
GameTest execution or infer milestones from timing.

## Rendering, screenshots, and artifacts

The preview client starts windowed at 1280x720 by default, with validated
`--width` and `--height` overrides. It owns `options.txt` values for
pause-on-focus-loss, narrator, onboarding, tutorial, audio, debug overlays, and
the capture HUD profile. The current implementation does **not** set or record
the requested `guiScale`; an isolated/default `options.txt` may resolve to Auto,
but that is not yet declared evidence. The responsive-viewport extension below
closes this gap. The manifest records the rendering settings it currently owns.

For each `puppet.capture("name", caption)` action, the client:

- waits for its world/screen condition plus the render-settle barrier;
- assigns the next figure number and prepends `Figure <n>: ` to the supplied
  `Component`, retaining its styles and colours;
- wraps that component with the Minecraft font at the fixed client width,
  renders it in a white band above the game frame, and keeps the game frame at
  exactly the requested `--width` and `--height`;
- calls the F2-equivalent `Screenshot.grab` overload on that captioned native
  render target with a sanitized, unique staging filename;
- waits for the asynchronous native write before continuing; and
- emits a structured capture-written marker containing the puppet id, capture
  id, staging filename, camera pose, UI/HUD profile, and action result.

Native screenshots first land in the isolated working directory's
`screenshots/` folder. After the client exits, the Rust launcher accepts only
the reported files, validates PNG signature/dimensions, hashes them, and copies
them to this generated artifact root:

```text
platform/minecraft/build/sfm-toolchain/artifacts/game-test-preview/
  preview-manifest.json
  move_one_stack_direct_walkthrough/
    figure_01_overview-00.png ... figure_08_overview-07.png
    figure_09_source-barrel.png
    figure_10_destination-barrel.png
    figure_11_manager.png
    figure_12_disk-program.png
```

`preview-manifest.json` is written atomically on both success and failure. It
records the command/branch/game/loader versions, selected puppets, action and
GameTest outcomes, each figure number, capture's relative path, dimensions,
BLAKE3 content hash, camera and UI profile, and all useful error/log paths. The command prints every
validated artifact path and its manifest path. A missing, cancelled, unreadable,
zero-byte, or invalid PNG is a puppet failure.

The artifact root is generated and ignored by Git. This command never creates
or updates snapshot baselines.

## Implemented responsive viewport sweep extension — 2026-07-22

### Planning-baseline capability and terminology

At the start of this wave, the Rust CLI accepted one startup `--width` and `--height`, launched
one client, and records only that requested viewport in the preview manifest.
The Java harness currently declares and runs each selected puppet once. There
is no GUI-scale CLI field, definition contract, runtime viewport action,
variant identity, or requested-versus-effective scale evidence.

A **viewport variant** is the pair:

```text
ViewportVariant(requestedWindowSize, requestedGuiScale)
requestedGuiScale = Auto | Explicit(positive integer)
```

It is not complete evidence until the runtime also reports actual GLFW window
size, native framebuffer size, effective GUI scale, and resulting logical
Minecraft screen size. Compact manifests may render `Auto -> 2`; visible
captions should say `GUI scale: Auto (effective 2)`. An explicit scale that is
unsupported, clamped, or produces the wrong actual viewport is not an ordinary
successful cell.

A **viewport profile** is a named ordered set or generator of variants plus one
preferred variant. The initial shared names should include:

- `CURRENT`: the existing one-point startup behavior for undeclared puppets;
- `COMMON_RESPONSIVE`: candidate sizes 640x480, 854x480, 1280x720, and
  1920x1080, with Auto plus every numeric scale supported at each stabilized
  framebuffer, preferred variant 1280x720 at Auto; and
- an explicitly requested larger profile may add 2560x1440 after the resize
  probe establishes that the automation display can produce it exactly.

The common profile is a named shorthand, not a long repeated CLI list. The
profile's exact sizes remain subject to the first live calibration, but changing
an accepted profile later is a versioned evidence-contract change.

### Ownership and execution model

The puppet definition declares the supported/default viewport profile. It does
not enqueue resize actions and does not loop over variants. Each selected
variant receives a fresh declaration and complete run of the ordinary puppet
scenario.

| Owner | Responsibility |
| --- | --- |
| Puppet definition | Declare a named profile or explicit typed variants and one preferred variant; declare product actions and captures only |
| Java puppet harness | Resolve the declaration plus launcher selection, repeat the full puppet lifecycle once per selected variant in one Minecraft process, isolate/reset scenario state, and restore the original environment |
| Java Minecraft runtime adapter | Resize the GLFW window on the client/render thread, set temporary GUI scale, wait for callbacks/render stability, measure requested and actual geometry, and restore on success/failure/cancellation |
| Rust CLI | Select `declared`, `preferred`, or one exact variant; launch one Minecraft process per branch target; validate reported variants and publish artifacts without independently reenacting the Java variant loop |
| Rust multi-version matrix collector | Compose branch/version with the already-collected in-process viewport axis; never launch one client per viewport cell |

The default for a puppet with a responsive declaration is all declared
variants. Existing puppets without a declaration retain their current singleton
behavior. Proposed focused-iteration surfaces are:

```powershell
# Complete definition-owned sweep in one Minecraft launch
sfm-propagate-changes.exe puppet run title_screen_repository_review

# One definition-owned preferred variant
sfm-propagate-changes.exe puppet run title_screen_repository_review --variant preferred

# One exact variant when supported by the definition
sfm-propagate-changes.exe puppet run title_screen_repository_review --variant 640x480@auto
```

CLI names are provisional until the argument/manifest fixture is reviewed. A
single startup system property may communicate the selection to Java. There is
no per-cell REST/RPC exchange: Java owns the live loop and reports authoritative
observations back through structured markers.

For each variant the harness must return to a documented baseline, apply the
viewport, create a new helper/action sequence, run the whole puppet, clean up,
and only then advance. Variant repetition must not accumulate persistent review
comments, reuse a prior selected screen accidentally, or make later results
depend on iteration order. Fixture/session roots therefore need a variant-aware
isolation or explicit idempotent reset contract.

### Runtime resize investigation and settle barrier

Minecraft 1.19.2 exposes `Window.calculateScale`, `Window.setGuiScale`, and
`Minecraft.resizeDisplay`. Its public `Window.setWindowed` reaches a mode-setting
path guarded for initialization and must not be assumed safe during an active
client. The first implementation is an experiment using
`GLFW.glfwSetWindowSize` on the client thread, allowing the installed window and
framebuffer callbacks to update Minecraft, followed by temporary
`options.guiScale().set(...)` and `Minecraft.resizeDisplay()`.

The action completes only after requested/actual window size, framebuffer size,
effective scale, logical screen bounds, active screen resize, render target,
and a bounded number of rendered frames are stable. It times out with all
measurements. Fullscreen is rejected for the first slice. High-DPI systems must
retain separate GLFW-window and framebuffer measurements rather than pretending
they are interchangeable.

### Variant-aware captures and artifacts

Every capture keeps one logical figure/capture identity across variants. A
variant id is an additional key, not another unrelated figure number. Captions
include human-readable requested/effective scale and physical/logical size.
Filenames and manifests remain collision-free, for example:

```text
repository-review-browse__1280x720__gui-auto__effective-3.png
```

The manifest adds requested window size, actual window size, framebuffer size,
requested scale, effective scale, logical screen size, responsive layout mode
when reported by the panel, variant result, and restoration result. The HTML
index can select a logical capture and show resolution rows against scale
columns. Missing, duplicated, silently clamped, or cross-variant captures fail
that target while preserving completed artifacts.

### Viewport calibration panel

Add a reusable `SFMScreenPanel` test card rather than diagnosing allocation
through application screens alone. It should render TV-style colour bars,
one-logical-unit/checkerboard rulers, exact corner and centre markers, the panel
bounds supplied by its host, GLFW window and framebuffer measurements, logical
screen dimensions, requested/effective GUI scale, responsive mode, and
screen/local mouse coordinates. Its puppet captures full-screen, one-half,
one-third, and a nested allocation. This exposes border overlap, off-by-one
allocation, input transforms, minimum-size failure, and high-DPI disagreement.

### Implementation slices and acceptance

1. **Resize probe:** one static diagnostic screen runs complete fresh puppet
   repetitions at 640x480, 1280x720, then 854x480, alternating Auto and an
   explicit supported scale, in one OS process. Prove cleanup and restoration.
2. **Typed declaration/selection:** add named profiles, preferred and exact
   selection, discovery validation, structured variant markers, and tests for
   undeclared/backward-compatible puppets.
3. **Variant artifacts:** extend manifest parsing, validation, paths, and the
   contact sheet without conflating the existing Minecraft-version matrix with
   the in-process viewport loop.
4. **Calibration proof:** capture and inspect the diagnostic panel across the
   accepted common profile and nested allocations.
5. **Repository-review adoption:** opt the review puppet into the profile only
   after its fixture/session lifecycle is variant-isolated; use the resulting
   evidence to drive the responsive composition work in the workspace plan.

Acceptance requires one recorded client PID/process lifetime for the whole
profile, one fresh complete puppet result per variant, exact requested/actual
measurements, deterministic figure/variant identities, restored original
window/scale, no persistent-state multiplication, focused `preferred` and exact
fast paths, full Rust and Java tests, source audit, and inspected contact-sheet
evidence. Do not propagate to later Minecraft versions until the 1.19.2 runtime
boundary is reviewed; later API differences belong behind
`@MCVersionDependentBehaviour` adapters.

### Completion record — 2026-07-22

The responsive extension is implemented on canonical `1.19.2`. Track A landed
at `c33e576532246d349ab85ba2f16011a9ffda50b1`; the merged CLI was installed
from canonical revision `a4d7f85ef`. The final combined invocation selected
`title_screen_viewport_calibration,title_screen_repository_review` with
`--variant declared`. One Minecraft process completed 30 fresh scenarios:
15 accepted viewport/GUI-scale variants for each puppet. The accepted profile
contains 640x480, 854x480, 1280x720, and 1920x1080; Auto resolved to effective
scales 2, 2, 3, and 4 respectively, and supported numeric scales remained
separate columns. Requested window and framebuffer sizes matched actual values,
and the harness restored 1280x720 Auto/effective 3 after the run.

The run published 210 valid PNGs: 60 calibration captures and 150 repository
review captures. Its immutable report is
`platform/minecraft/build/sfm-toolchain/artifacts/game-test-preview/runs/title_screen_viewport_calibration-title_screen_r-20260722-195214-678/index.html`;
the generated `game-test-preview/index.html` alias points at the latest evidence.
The coordinator inspected smallest-Auto, wide scale-1, largest-Auto, and nested
calibration cells. Variant-isolated fixture roots retained exactly one restored
review comment per scenario. `preferred` and exact selection remain available
as the single-scenario fast paths. Later Minecraft branches and `.g4` files
were intentionally untouched in this wave.

## Implementation phases

### [ ] 1. Add puppet discovery and selection

- Add `@SFMGamePuppet`, discovery types, strict signature validation,
  canonical-id uniqueness checks, and focused discovery tests.
- Add a separate `sfm.gamePuppetSelection` JVM property. Share only selector
  parsing/matching utilities with `sfm.gametestSelection`; keep their result
  domains distinct.
- Add `RunGameTestPreviewArgs` and
  `sfm-propagate-changes run game-test-preview --puppet <selector>`, including
  `--width`, `--height`, and `--keep-open`. Add a dedicated `RunKind` and
  preview working/log/artifact paths.

**Completion criteria:** malformed methods, duplicate ids, zero selector
matches, invalid dimensions, and an empty puppet action plan fail with clear
diagnostics before a world is created. Existing `client-puppet` behavior is
unchanged.

### [ ] 2. Generalise the client harness into independently selectable modes

- Retain `puppet` mode for the existing all-selected-GameTests correctness
  execution and add a separate `game-puppet` mode for definition execution.
- Factor title-screen, deterministic-world, pause prevention, game-rule, and
  exit utilities without changing existing correctness markers.
- Implement the serial lifecycle: title ready, declaration validation, fresh
  world, queued actions, return-to-title confirmation, next definition, final
  result/exit. Give every transition a structured log marker and timeout.
- Keep a failure per puppet, return to title where safe, and continue later
  selected puppets; only fatal client/title-screen failure aborts the process.

**Completion criteria:** two minimal dummy puppets create separate worlds and
run in one Minecraft launch, each starting and ending at title screen.

### [ ] 3. Implement the typed helper and native capture plumbing

- Implement the action queue and the helper's fresh-world, selected-GameTest,
  absolute-origin, camera, wait, block-use, screen-predicate, and capture
  operations.
- Ensure server/client/render work occurs on the correct thread, with bounded
  timeout diagnostics containing puppet/action ids.
- Use native `Screenshot.grab`, wait for the asynchronous write, and emit
  authoritative capture markers. Do not add a parallel OpenGL readback or OS
  screenshot implementation.
- Teach the Rust launcher safe staging cleanup, marker parsing, PNG validation,
  artifact copy, hash generation, atomic manifest generation, and nonzero
  failure handling.

**Completion criteria:** a simple puppet can create a world, point the camera,
emit a valid named F2-equivalent PNG, return to title, and produce a manifest.

### [x] 4. Implement the `Move1StackDirect` walkthrough

- Add the first class-level `@SFMGamePuppet` in the GameTest source
  set. It runs exactly `move_1_stack_direct` and requires its existing normal
  GameTest success before visual actions begin.
- Use the returned GameTest origin to declare the eight elevated overview
  poses. Stabilise each frame before capture and give every capture an explicit,
  reviewed name.
- Interact with each `TEST_BARREL`, manager, and disk/program route through
  normal client gameplay code. Wait for the expected real inventory/menu/editor
  screen before each GUI capture.
- Keep visual coordinates and UI navigation in this puppet; do not contaminate
  the normal test's production behavior or emulate GUI content.

**Completion criteria:** one command produces the twelve reviewed artifacts
listed above. The destination-barrel image visibly has 64 dirt, the source is
empty, and manager/disk captures are genuine SFM screens.

### [ ] 5. Verify failure behavior and developer ergonomics

- Add Rust unit tests for CLI parsing, selector forwarding, safe path cleanup,
  capture-name sanitisation, marker parsing, PNG validation, and manifest
  serialization.
- Add Java tests for method discovery/signature validation and action-plan state
  transitions where feasible.
- Exercise real client acceptance runs for the walkthrough, because only they
  prove integrated-server interaction and rendering. Verify the launcher
  reports clickable artifact and manifest paths.
- Verify unmatched puppets, GameTest failure, bad camera/screen condition,
  cancelled screenshot, missing staged PNG, client cancellation, and title
  return failure. No process may report success solely from Java exit code zero.

**Completion criteria:** success and all stated failure paths have evidence,
and no ordinary developer save or source-controlled file is changed.

### [ ] 6. Prepare snapshot and cross-version follow-up without enabling it

- Keep future baseline storage and pixel comparison as a separate opt-in plan.
  It will consume compatible manifests and must never update baselines as a
  side effect of preview generation.
- After 1.19.2 acceptance, run the surface audit and propagate oldest-first.
  Keep the Rust CLI and annotation/discovery surface common; sequester only
  genuine client API differences behind `@MCVersionDependentBehaviour`.
- Validate CLI/unit tests on every maintained line and a real puppet preview on
  every compatible client API family before calling it cross-version ready.

**Completion criteria:** no snapshot comparison/gating is silently introduced,
and cross-version variation remains bounded to annotated adapter methods.

## Non-goals for the first delivery

- Replacing `run client-puppet` or the server GameTest command.
- Running every GameTest from the preview command.
- Headless rendering, desktop input automation, or manual click requirements.
- Arbitrary paths chosen by Java puppet code.
- Pixel baselines, image-diff tolerances, or CI snapshot gates.
- General intermediate GameTest checkpoint freezing; add it later only through
  an explicit opt-in test contract.

## Files expected to change

- `platform/cli/sfm-propagate-changes/src/cli/run/` — preview command and
  argument tests.
- `platform/cli/sfm-propagate-changes/src/jar_build/run_kind.rs`,
  `run_options.rs`, and `engine_run.rs` — preview launch kind, selection
  property, safe work/artifact roots, markers, manifest, and validation.
- `platform/minecraft/src/gametest/java/ca/teamdman/sfm/gametest/` — puppet
  annotation, discovery, definition, helper/action queue, and
  `SFMClientRunHarness` game-puppet mode.
- A first `Move1StackDirect` puppet definition in the GameTest source set.
- Versioned run configuration only if a client argument cannot stay in the
  Rust-owned launcher.

## Acceptance command examples

```powershell
# The first scripted visual walkthrough
sfm-propagate-changes run game-test-preview `
  --puppet 'move_one_stack_direct_walkthrough'

# Run several independently title-screen-bounded walkthroughs
sfm-propagate-changes run game-test-preview `
  --puppet 'move_one_stack_direct_walkthrough,*_gui_walkthrough' `
  --width 1600 --height 900 --keep-open 30s
```

On success, each command reports every PNG artifact and
`preview-manifest.json`; it does not alter snapshot baselines or user worlds.
