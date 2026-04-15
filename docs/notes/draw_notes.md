# Draw Roadmap

Last updated: 2026-04-15

## Goals

- [x] Add z-order shortcuts for selected draw elements.
- [x] Add persistent undo/redo backed by an undo tree with a dedicated hidden-by-default history layer.
- [x] Make layer origins draggable so each canvas layer can use a floating origin.
- [x] Add arrow endpoint snapping/binding so arrows stay attached when bound objects move.
- [x] Expand slash-command targeting/help so local draw commands can target elements by selector or name.
- [x] Make relative-selector guide arrows persistent, always visible by default, and editable like user-drawn arrows.
- [x] Add cursor/arrow workflows for inserting new arrow control nodes.

## Current Plan

### 1. Ordering shortcuts

- [x] Wire `[` to send the current selection backward by one paint-order slot.
- [x] Wire `]` to bring the current selection forward by one paint-order slot.
- [x] Wire `Shift+[` to send the current selection to the back.
- [x] Wire `Shift+]` to bring the current selection to the front.
- [x] Make ordering operate on the owning element set so arrow-anchor selections behave sensibly.
- [x] Record ordering mutations in the undo tree.

### 2. Undo tree core

- [x] Introduce a persistent scene-snapshot model that can be recorded without recursively snapshotting the undo tree itself.
- [x] Introduce undo-tree nodes with parent/children relationships and a current-pointer node.
- [x] Persist undo-tree state in the draw canvas document.
- [x] Add `Ctrl+Z` to move to the parent snapshot.
- [x] Add `Ctrl+Shift+Z` to move to a child snapshot.
- [x] Add a hidden-by-default history layer that renders the undo graph.
- [x] Allow branch choice when multiple redo children exist.
- [x] Record all editor commits through one tracked-action helper so new actions automatically land in history.

### 3. Layer origins

- [x] Persist per-layer origin positions.
- [x] Replace the fixed origin marker with draggable origin handles for canvas layers.
- [x] Draw origin axes from the active layer's stored origin instead of hard-coded `0,0`.
- [x] Update cursor/camera readouts to use the active layer origin as the coordinate reference.
- [x] Record origin moves in the undo tree.

### 4. Arrow snapping / binding

- [x] Add arrow endpoint binding metadata for start/end anchors.
- [x] Snap arrow creation endpoints to nearby bindable elements.
- [x] Recompute bound endpoints when their target elements move or resize.
- [x] Rebind or detach endpoints when anchors are explicitly edited.
- [x] Persist arrow binding metadata in the draw canvas document.
- [x] Cover binding math with focused tests.

### 5. History / chrome polish

- [x] Keep the history-layer label legible in the layer window even while the layer is muted.
- [x] Flip the undo-tree layout so depth flows vertically and branches fan out horizontally.
- [x] Add `/reset chrome` to restore chrome widget positions.
- [x] Restrict draggable origin handles to the history layer.
- [x] Coalesce repeated zoom commits into one recent history node when zooming continuously.

### 6. Command targeting / help polish

- [x] Rename `/box` to `/rectangle` in help, suggestions, and local execution paths.
- [x] Rename `@rel[...]` to `@relative[...]` in formatting, help, and suggestions while keeping old documents readable.
- [x] Add an optional trailing delimiter parameter to `/concatenate`, defaulting to the empty string.
- [x] Add `/width {target}` for reporting the resolved element width.
- [x] Add `/name {target} [{new name}]` for printing and setting element names, including quoted names with spaces.
- [x] Allow named elements to be used anywhere local target resolution already accepts selectors.
- [x] Add `/help [topic-or-target]` locally so it can print command help or describe a resolved target element.
- [x] Increase the top gap between a command text element and its first emitted output block.
- [x] Make `/sfm draw help` derive its output from shared command data or the registered command tree so it cannot silently drift.

### 7. Relative selector arrows / arrow editing

- [x] Keep the green `@relative[...]` guide arrows visible even outside text-edit mode, while still allowing `x` to hide them.
- [x] Let relative-selector guide arrows snap to element boundaries the same way authored arrows snap/bind.
- [x] Allow the command-side end of a relative-selector arrow to slide along the command node without detaching.
- [x] Allow double-clicking an arrow segment with the cursor tool to insert a control node.
- [x] When arrow anchors are selected, pressing `a` should enter an insert-anchor mode that adds a control node on click.

## Implementation Notes

- The current draw screen is still the main integration point, but the long-term state should live in dedicated draw-model helpers where possible.
- The history layer should be rendered from the undo-tree model rather than stored as normal user-authored elements.
- The initial origin implementation will target canvas-space layers. The chrome layer remains screen-space.
- History keeps the only draggable origin handle; the other layers fall back to their fixed/default coordinate references.
- Command help/usage should come from a shared source so draw-screen help, chat completion, and `/sfm draw help` stay in sync.
- Relative-selector arrows should reuse as much of the authored-arrow editing model as possible so snapping and control-node workflows behave consistently.

## Verification

- [x] `./gradlew compileJava`
- [x] `./gradlew compileGameTestJava`
- [x] `./gradlew compileTestJava`
- [x] `./gradlew test --tests "ca.teamdman.sfm.test.SFMDrawVirtualFileSystemTests" --tests "ca.teamdman.sfm.test.SFMDrawBindingUtilTests"`
- [x] `./gradlew compileDatagenJava`
- [x] `cd platform/minecraft && .\gradlew.bat compileJava`
- [x] `cd platform/minecraft && .\gradlew.bat compileJava test --tests "ca.teamdman.sfm.test.SFMDrawLocalCommandExecutorTests" --tests "ca.teamdman.sfm.test.SFMDrawSpatialQueriesTests" --tests "ca.teamdman.sfm.test.SFMDrawVirtualFileSystemTests"`
- [ ] Manually exercise the new guide-arrow editing flows in-client.

## Backlog

- Keyboard shortcuts should be customizable.
- Item-driven color picking / eyedropper ideas.
- Rectangle coloring ideas.
- `minecraft AND #dye`
- `itemstack repr`