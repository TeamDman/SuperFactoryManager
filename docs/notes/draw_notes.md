# Draw Roadmap

Last updated: 2026-04-15

## Goals

- [x] Add z-order shortcuts for selected draw elements.
- [x] Add persistent undo/redo backed by an undo tree with a dedicated hidden-by-default history layer.
- [x] Make layer origins draggable so each canvas layer can use a floating origin.
- [x] Add arrow endpoint snapping/binding so arrows stay attached when bound objects move.

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

## Implementation Notes

- The current draw screen is still the main integration point, but the long-term state should live in dedicated draw-model helpers where possible.
- The history layer should be rendered from the undo-tree model rather than stored as normal user-authored elements.
- The initial origin implementation will target canvas-space layers. The chrome layer remains screen-space.
- History keeps the only draggable origin handle; the other layers fall back to their fixed/default coordinate references.

## Verification

- [x] `./gradlew compileJava`
- [x] `./gradlew compileGameTestJava`
- [x] `./gradlew compileTestJava`
- [x] `./gradlew test --tests "ca.teamdman.sfm.test.SFMDrawVirtualFileSystemTests" --tests "ca.teamdman.sfm.test.SFMDrawBindingUtilTests"`
- [x] `./gradlew compileDatagenJava`
- [x] `cd platform/minecraft && .\gradlew.bat compileJava`

## Backlog

- Keyboard shortcuts should be customizable.
- Item-driven color picking / eyedropper ideas.
- Rectangle coloring ideas.
- `minecraft AND #dye`
- `itemstack repr`