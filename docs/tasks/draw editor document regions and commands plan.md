# Draw editor layers, commands, and canvas workspace plan

**Plan status:** Active
**Primary implementation root:** `D:\Repos\Minecraft\SFM\repos2\1.19.2`
**Reference-only worktrees:** `feat/1.19.2/draw`, `feat/1.19.2/mount`
**Last updated:** 2026-07-18

## How to update this plan

- `[ ]` Not started
- `[~]` In progress
- `[x]` Complete
- `[!]` Blocked

Update a work item's heading and completion notes together. Record durable
decisions, commit IDs, validation output, and intentional exclusions directly
below the task they affect. A phase is complete only when every task in it is
`[x]`. Use `[!]` only with the exact blocker, latest evidence, and unblocking
condition.

Keep one current implementation focus. Work from 1.19.2, commit a coherent
baseline slice, and use `sfm-propagate-changes.exe git merge` for normal
Minecraft-version branches. The feature worktrees above are evidence to
inspect, not branches to merge or edit.

## Purpose

Turn the current experimental Draw text editor into a canvas workspace whose
content roles are explicit and editable. The program being edited lives in the
**primary layer**. Each grammar or template reference lives in its own named,
read-only reference layer; scratch material, drawing primitives, commands, and
canvas controls use other typed layers. Every element retains canonical global
canvas coordinates. Layer membership is persistent semantic metadata, not a
parent transform or a geometric containment inference. Saving projects only
eligible primary-layer glyphs into SFML, regardless of where other layers are
positioned or overlap visually.

The first observable slice establishes canvas-native controls and commands:

- remove the fixed-screen SFML and Done buttons and seed default canvas-native
  Commands, Save, and Cancel controls in a controls layer;
- panning the camera can move those controls out of view because their anchors
  are canvas coordinates rather than absolute screen coordinates;
- a configurable universal hotkey or the Commands control opens one command
  palette above the originating screen, prefilled with the canonical local
  `/sfm action ` surface in Draw;
- a client-only SFM custom registry supplies namespaced contextual action/command
  contributions, which compile into one Brigadier dispatcher and palette model;
- Brigadier completes and executes `list`, `help`, and `invoke` below
  `/sfm action`, including the initial document save/close action entries;
- palette input/suggestions and optional Draw command history are UI state
  rather than SFML program text and are excluded from disk projection; and
- ordinary `/` glyphs typed into the program remain ordinary SFML text, so the
  command system does not conflict with comments or future division syntax.

The old SFML button's click-versus-drag behavior is therefore removed rather
than refined. Grammar insertion becomes a later command that uses a deliberate
default or explicit placement argument. Its reference layer uses the same font
scale and global coordinate model as the primary layer; no bounding rectangle
shrinks, owns, or determines the reference content.

The release outcome is broader. SFM has a universal contextual command palette;
Draw has user-configurable canvas-native buttons driven by that action system,
an explicit primary-layer save projection, named/ordered/visible/locked layers,
persistent workspace state for non-SFML canvas material, basic point/edge-based
drawing tools, focused automated tests, and cross-version visual evidence. Draw
is intended to be the headline of the next SFM release.
The parent [release plan](puppet%20propagation%20and%20preview%20matrix%20plan.md)
retains ownership of version bumping, packaged-artifact verification, tagging,
and publication.

## Scope

### In scope for the Draw release floor

- Replace ad-hoc `EmbeddedDocument` state with a typed editor session, one
  global glyph model, and dynamic typed layers with stable workspace-local ids.
- Represent the current disk program as one distinguished primary layer.
- Insert runtime `SFML.g4` and bundled templates/examples into separate named,
  read-only reference layers.
- Support active layer, ordering, visibility/muting, locking, naming, and
  explicit copy/move-to-layer operations without layer-local coordinates.
- Preserve one font scale across all document text. Canvas zoom may scale the
  whole scene uniformly; layers and reference presentation may not shrink their
  own text.
- Support default/explicit reference placement, layer movement, focus, copy,
  and z-order without confusing text selection with layer operations.
- Replace fixed SFML and Done controls with canvas-native Commands, Save, and
  Cancel controls. They pan with the canvas and use the same registered actions
  as keyboard and command entry points.
- Add one configurable universal hotkey that pushes a contextual command palette
  over the originating screen. Make Commands open that same palette with Draw's
  `/sfm action` surface selected/prefilled. Suggestions must be searchable,
  clickable, keyboard-operable, and available without typing a shortcut.
- Let users pin viable no-argument or fully bound actions as buttons when the
  current screen implements a button host. Draw materializes those bindings as
  movable canvas controls and persists their layout in the workspace.
- Begin with stable `sfm:document/save`,
  `sfm:document/close_without_saving`, and
  `sfm:document/save_and_close` action ids invoked through
  `/sfm action invoke <action-id>`; then add grammar/template insertion, layer
  operations such as clobber, geometry, and tools. Friendly direct command
  aliases remain an explicit policy gate and never become a second executor.
- Define contextual client actions as typed entries in an SFM-owned client-only
  custom registry. Registry entries contribute Brigadier nodes, contextual
  availability, button capability, and presentation metadata; they do not
  implement a second parser or bypass Brigadier.
- Save the ordered eligible glyphs assigned to the primary layer to the disk.
  References, scratch content, controls, command output, and shapes from other
  layers are never concatenated implicitly, even when visually overlapping.
- Warn before an operation would abandon changed workspace material.
- Add a versioned local canvas-workspace format for placement, camera, shapes,
  layers, controls, and other non-disk material, with round-trip and migration
  tests.
- Add text/select, move, rectangle/polygon, line/arrow, and freehand tools based
  on stable points and edges. A rectangle starts with four points/four edges;
  moving a point or edge may make it no longer rectangular.
- Validate the real editor through a dedicated puppet and propagate the
  supported behavior across normal Minecraft-version branches.

### Out of scope unless a gate deliberately adds it

- Literal merge, cherry-pick, or wholesale copy of `feat/1.19.2/draw`.
- Modification, cleanup, reset, commit, or merge of dirty
  `feat/1.19.2/mount`.
- Networked collaboration or server-authoritative canvas files.
- Migrating every existing SFM keybinding or screen button to the palette in
  this release. The shared substrate and Draw actions ship first; other screens
  can adopt it incrementally.
- AI/Ollama tools from the old Draw branch.
- Arbitrary filesystem access from a canvas command; storage remains below an
  SFM-owned, path-normalized workspace root.
- Treating rectangles/polygons as document ownership boundaries or inferring
  layer membership from geometric containment.
- Persisted layer-local origins or parent transforms. The reference branch's
  layer origins are evidence only; all shipped element positions stay global.
- Pixel-perfect snapshot comparison. Captioned captures and human matrix
  review are required; automated image-diff policy remains follow-up work.

## Established foundation

- `SFMDrawCanvasScreen` already owns the glyph/cursor model, camera transforms,
  syntax-coloured rendering, runtime grammar loading, reference rendering, and
  save projection. `SFMDrawCanvasModel` and
  `SFMDrawCanvasSyntaxHighlightingHelper` have focused JUnit coverage.
- Runtime `.g4` acquisition already exists. This plan changes its canvas
  representation; it does not reopen the build-time grammar migration.
- Current `EmbeddedDocument.fitContent` computes private `contentZoom`, and the
  renderer multiplies it by canvas zoom. That is the verified shrink-to-fit
  cause; the layer model removes per-document scaling rather than tuning clamps.
- Current grammar insertion cancels on release over the SFML button and
  otherwise ignores its stored press position. This path is superseded: the
  fixed buttons and their grammar drag state are removed by the canvas-control
  and command bootstrap, and later grammar insertion is a Brigadier command.
- The clean `feat/1.19.2/draw` worktree is useful reference for typed canvas
  persistence, virtual paths, shapes, endpoint bindings, selection, layers,
  and undo. Its large nullable `Element` record, manual command switch, movable
  chrome, and AI scope are not accepted automatically.
- Its fixed `ELEMENTS`, `CHROME`, and `HISTORY` enum demonstrates layer
  selection, visibility, locking, storage, and interaction routing. The new
  model needs dynamic named layers and must not copy `layerOrigins`: assigning
  an element to a layer cannot change or indirectly own its global coordinates.
- That reference also proves slash-prefixed text elements can be selected,
  edited, executed, and followed by canvas output. The new implementation may
  reuse the interaction concept, but uses a dedicated command-element kind and
  Brigadier rather than “all text beginning with slash” plus a manual parser.
- `SFMResourceTypes`, `SFMProgramLinters`, and the client-only `SFMTextEditors`
  registry establish the reusable `SFMDeferredRegisterBuilder` pattern. Draw
  commands can use the same seam without directly spreading Forge registry API
  differences through the editor or command implementation.
- Existing `SFMTextEditorActions` is a client-only registry of low-level
  keyboard impulses over `TextEditContext`; it is useful evidence but is not the
  universal contextual-action registry. The new registry may adapt those
  operations later without conflating keystroke matching with palette identity,
  contextual availability, presentation, or button persistence.
- `SFMDeferredRegisterBuilder` can target an existing registry without calling
  `createNewRegistry()`. This permits one central registry creator and multiple
  screen/capability-owned contributors. Each contributor must still attach its
  deferred register to the mod event bus before registry freeze.
- The current builder records a requested namespace but its 1.19.2 `build()`
  passes `SFM.MOD_ID` to Forge's `DeferredRegister.create`. That is sufficient
  for SFM-owned screen contributors but not honest third-party registration.
  Phase 0.3 must either correct that seam with cross-version proof before
  advertising external action contributors or explicitly keep the first
  registry SFM-internal.
- `SFMKeyMappings` already registers GUI/universal mappings and works around
  key-state behavior while screens are open. Existing `InputEvent.Key` handlers
  gate actions with direct screen checks; the palette can replace that pattern
  incrementally with structured contextual availability.
- `SFMScreenChangeHelpers.setOrPushScreen` can layer a palette above the current
  screen. The palette context must retain the originating/host screen because
  `Minecraft.screen` becomes the palette after it is pushed.
- The dirty `feat/1.19.2/mount` worktree is useful reference for program/label
  import, export, conflict snapshots, and packets. It is not a prerequisite for
  initial Draw slices and remains untouched until explicit approval.
- The puppet pipeline can open client screens, produce captioned native
  captures, and collect cross-version matrices.

## Confirmed constraints and working invariants

1. **Primary layer is explicit.** The workspace stores a stable primary layer
   id and `PRIMARY` role rather than inferring the program from position, draw
   order, display name, or list index.
2. **Glyph coordinates are canonical and global.** A glyph stores its position
   directly in canvas coordinates. Layers, shapes, selections, and indexes
   never become its persisted parent transform.
3. **Layer membership is explicit semantic metadata.** Every persisted canvas
   element belongs to exactly one layer. Moving an element spatially never
   changes its layer; copying or moving between layers is an explicit action.
4. **Layers do not have coordinate origins.** Visibility, locking, ordering,
   source, role, and edit policy may live on a layer, but position does not.
   A move-layer operation captures member ids/original global positions for the
   transaction and writes the resulting global coordinates back to elements.
5. **Disk remains SFML.** Shapes, camera, reference placement, and history live
   in the canvas workspace, never in the disk program string.
6. **The primary layer determines disk inclusion.** Saving selects eligible
   glyphs by primary layer id and then uses spatial ordering only to reconstruct
   SFML text. Geometric overlap with another layer or shape is irrelevant.
7. **Reference policy belongs to reference layers.** Source identity/hash and
   read-only policy live on the layer. Copying selected reference glyphs into
   the primary or scratch layer creates editable glyphs with explicit provenance
   policy; merely moving them does not change membership or mutability.
8. **Text scale is global.** Primary and reference glyphs share font metrics and
   base scale. Only the scene camera zoom scales them.
9. **Normal Draw action controls are canvas elements.** Commands, Save, and Cancel
   have global canvas anchors, participate in camera pan/zoom and layer order,
   and can leave the viewport. Their action bindings resolve the same registered
   actions used by commands and shortcuts. Commands binds the registered palette
   open action with `/sfm action ` as its typed initial-query argument.
10. **Command input is explicit palette state.** Program glyphs are never
    scanned or executed merely because their spatial projection begins with
    `/`. The universal hotkey opens an ordered palette input buffer; Draw's
    Commands control opens the same palette prefilled with `/sfm action `.
11. **The initial document actions have stable ids.** The registry contains
    `sfm:document/save`, `sfm:document/close_without_saving`, and
    `sfm:document/save_and_close`. Each delegates to the same named
    screen/session operation used by other UI or shortcuts.
12. **Palette commands are client-local.** SFM uses a client action source,
    never a server `/sfm` source or chat execution. Individual actions may call
    existing explicit client/server workflows, but palette parsing is local.
13. **Registry lifecycle is central; definitions are distributed.** Client-only
    `SFMClientActions` creates the custom registry exactly once, owns lookup and
    compilation, and exposes a non-creating deferred-register factory. The
    narrowest behavior owner—such as a screen's nested `Actions` class or a
    shared editor/session capability—declares its own namespaced registrations
    and attaches that contributor to the mod bus through the client bootstrap.
    A contributor must never call `createNewRegistry()`.
14. **One registry entry is one contextual action.** Its registry key is the
    stable action id used by invocation, help, buttons, shortcuts, persistence,
    and diagnostics. The entry owns presentation metadata, typed arguments,
    structured availability, execution, and optional button capability. Its
    executor delegates to a named operation on the resolved screen/session
    owner rather than copying that behavior into registry infrastructure.
15. **One compiled Brigadier tree owns command behavior.** Registry entries are
    compiled deterministically after registration. Palette search, buttons,
    keyboard, completion, help, and any optional Draw command-history surface
    resolve the same actions/dispatcher; the custom registry is not another
    parser. The universal hotkey invokes the registered palette-open action
    rather than maintaining a separate open callback.
16. **Availability is contextual and explainable.** An action evaluates against
    an `SFMClientActionContext` that retains the originating screen plus optional
    client/player/level state. A typed requirement resolves that context to the
    executor's required target (for example `SFMDrawCanvasScreen`) or returns an
    unavailable reason. Brigadier `.requires` receives the boolean projection
    of that result, while the palette/list/help UI retains the styled reason.
    Execution re-resolves the requirement so a context that changed after parse
    fails closed rather than invoking stale state. No consumer repeats
    `instanceof` checks/casts.
17. **Pinned buttons are action bindings, not copied handlers.** A configured
    button stores a stable action id plus any fully bound typed arguments and
    presentation/layout data. Required unbound arguments make an action
    non-pinnable until configured. Context changes may hide/disable the instance
    without deleting the user's binding.
18. **Command and control UI is not disk text.** Draw controls and optional
    command history use non-primary layers. The transient palette input,
    suggestions, and diagnostics are outside the canvas document entirely.
    None can enter primary SFML projection.
19. **Spatial acceleration is transparent.** Brute-force geometric queries
    define semantics. A quadtree, R-tree, BVH, volume hierarchy, or other index
    may cache candidates, but is derived, invalidatable, non-serialized, and
    equivalence-tested against brute force.
20. **Screen does not own domain state.** Layers, geometry, placement, dirty
    tracking, command execution, and persistence are extracted for pure tests.
21. **Version seams stay narrow.** Widgets, rendering, narration, and input API
    differences use `@MCVersionDependentBehaviour`; shared logic does not fork.
22. **Repository workflow is mandatory.** No Gradle; implement on 1.19.2, use
    SFM CLI validation, audit version surfaces, and propagate through SFM.

## Design gates that must close before dependent work

| Gate | Decision required | Proposed default | Acceptance consequence |
| --- | --- | --- | --- |
| Release floor | Whether release needs only layers/commands or also basic drawing and persistence | Require the full in-scope floor; mount and the final destructive `/clobber` behavior may defer independently | Changelog can honestly call this Draw's introduction |
| Layer model | Whether document identity is spatial containment or explicit layer membership | **Resolved 2026-07-17:** dynamic typed layers; each element has one layer id and global coordinates; rectangles have no ownership semantics | Primary/reference separation remains stable under arbitrary movement and overlap |
| Layer transforms | Whether layers have movable origins | **Resolved 2026-07-17:** no persisted layer origins/transforms; move-layer is a transaction over captured member ids/global positions | Layer organization cannot constrain independent element placement |
| Default reference placement | Exact inset meant by top-left reference glyph at top-centre | Transform viewport centre x and first usable content y into canvas coordinates | Placement tests cover resolution, pan, and zoom changes |
| Layer movement | How a whole layer is moved without a parent origin | Freeze current member ids and their global positions at gesture/command start, apply one delta, then discard transaction state | Added/removed/reassigned elements during a move have deterministic behavior |
| Reference mutability | How a read-only reference becomes editable content | Reference layer remains read-only; Copy to Layer creates editable copies in primary/scratch while preserving only selected provenance | Position remains free and edit policy is explicit |
| Canvas controls | Which actions are seeded and how they transform/recover | Seed Commands, Save, and Cancel action bindings in a controls layer; let users add/remove/reposition viable bindings; pan and zoom them with the scene; provide a tested frame-controls/Home recovery action | Controls can intentionally leave view without trapping keyboard-only or zoomed-out users |
| Save/Cancel mapping | Which document operations the default controls invoke | Save invokes `sfm:document/save_and_close`; Cancel invokes `sfm:document/close_without_saving`; `sfm:document/save` remains save-without-close | Button, command, and shortcut behavior share named session operations |
| Command activation | Whether ordinary slash-prefixed program text executes | **Resolved 2026-07-17:** only the palette's ordered input buffer dispatches; program `/` remains SFML text; Commands opens the palette with `/sfm action ` | Slash/division/comment glyphs cannot become commands through movement or projection |
| Action definition ownership | Whether the central registry class or each contextual owner declares actions | **Proposed:** `SFMClientActions` alone creates/compiles the registry; screens or shared capability/session owners declare one entry per action through non-creating deferred registers registered by the client bootstrap | Save behavior can stay beside its owner while identity and lifecycle remain centralized; duplicate registry creation and dedicated-server client loading are tested |
| Action extension scope | Whether the release promises third-party action registration | Default to SFM-owned distributed contributors; advertise third-party registration only after `SFMDeferredRegisterBuilder` honors contributor namespaces and an external-namespace fixture passes across supported versions | The custom registry does not imply a broken extension API |
| Canonical action command surface | How registered actions manifest as Brigadier commands | **Proposed:** local `/sfm action list [available\|all]`, `/sfm action help [<action-id>]`, and `/sfm action invoke <action-id> [typed args]`; each action id is a literal branch generated from its registry entry | The registry id is the single durable identity, contextual nodes can use `.requires`, and action operands retain Brigadier types |
| Friendly alias policy | Whether actions also contribute paths such as `/document save` or `/clobber ...` | Default to no aliases in the first slice; add aliases later only as redirects to stable action entries with collision tests | Friendly syntax cannot create a second catalog/executor or weaken contextual checks |
| Palette hotkey | Exact universal binding and conflict behavior | **Resolved 2026-07-18:** register one configurable universal SFM key mapping with default `Ctrl+K`; still require in-game/GUI conflict and repeat testing | The palette opens consistently from title, GUI, Draw, and in-world contexts without repeats |
| Palette context | Which screen contextual actions inspect after the palette is pushed | **Resolved 2026-07-17:** capture the originating/host screen and relevant client state in `SFMClientActionContext`; never treat the palette screen itself as the host | Draw-only actions remain viable while the palette overlays Draw and become unavailable if the host closes/changes |
| Version-sensitive API seam audit | Which direct GUI/entity calls must be contained behind helpers, and which user-facing text must be localized | **Deferred:** add declarative `DENY`/`PERMIT` audit rules for direct `Minecraft` screen transitions outside `SFMScreenChangeHelpers`, direct entity-to-level access outside `SFMEntityUtils`, and user-facing `Component.literal(...)` construction; run the audit, classify intentional internal/debug literals with explicit permits, then clean the remaining violations in a later hardening pass | Version-specific GUI/entity access remains centralized and user-facing text has an enforceable localization seam without diverting the current palette/layer implementation |
| Availability presentation | Hide unavailable actions or show reasons | Palette shows viable actions by default with an optional “show unavailable” view/reason; pinned buttons remain configured but are hidden when not viable | Context changes do not delete customization and unavailable actions cannot execute |
| Button hosts | Where a user may pin an action as a button | Introduce a host contract; implement Draw canvas first and defer other screen hosts | The shared action system is universal without requiring every screen to support custom buttons now |
| Button persistence scope | Whether Draw bindings/layout are workspace-specific, client-profile defaults, or both | Persist concrete buttons in each Draw workspace for this release; defer reusable client-profile templates until the format and UX are proven | Customization is durable without expanding the release into a cross-screen layout/profile system |
| Button argument binding | Whether argument-taking actions can be pinned | Pin no-argument actions immediately; require a fully typed saved invocation before an argument-taking action is pinnable | Buttons never execute partial/ad-hoc command strings |
| Suggestion presentation | How universal completions/search are rendered | Use one transient pushed palette screen with keyboard/mouse/narration; do not persist its bounds as canvas state | Every context has the same accessible discovery UI |
| Command history | Whether submitted command/result history appears in Draw | Keep concise optional history in a non-primary Draw layer after palette execution; persistence remains a later explicit decision | Users can understand what happened without making editable canvas glyphs the parser input |
| Clobber contract | Whether `/clobber` targets layers, geometric selectors, or both, plus exact source/target semantics | Start with typed layer operands; do not implement until copy-versus-move, target replacement, alignment, provenance, ordering, and undo are specified | Typed layer completion can land first; destructive behavior cannot be guessed |
| Workspace binding | How a disk session finds its local canvas after moves/copies | Select a typed binding before persistence; never key only by menu slot | Reopen/move/missing/stale cases have deterministic tests |
| Mount relationship | Whether manager mount ships in this release | Exclude unless explicitly approved after workspace format stabilizes | Dirty feature work remains untouched |
| Primary projection | Ordering and eligibility within the primary layer | Filter by stable primary layer id, exclude non-SFML element kinds, then use the existing spatial document projection | Save tests cover overlap, arbitrary positions, layer reassignment, ordering, and reference copies |

Phase 0.2 records final decisions. Pure placement/layer bootstrap work may use
proposed defaults, but persistence, control recovery/action mapping,
suggestion/history presentation, destructive commands, and release claims may
not pass their gates implicitly.

## Proposed architecture

```text
SFMClientActions (client-only SFM custom registry owner)
  -> creates registry exactly once + exposes lookup/compiler
  -> exposes non-creating deferred-register factory for contributors
SFMDrawCanvasScreen.Actions / shared editor capability Actions / other owners
  -> one ResourceLocation-keyed SFMClientAction entry per operation
  -> metadata + typed arguments + availability + executor + button capability
  -> delegates execution to named operation on resolved screen/session target
SFMClientActionTreeCompiler
  -> deterministic action-id order + duplicate/collision diagnostics
  -> one CommandDispatcher<SFMClientActionSource>
  -> /sfm action list|help|invoke
  -> each invoke child is the stable action-id literal plus typed arguments
  -> `.requires` is the boolean view of structured action availability
SFMCommandPaletteController
  -> configurable universal SFM key mapping
  -> captures/validates SFMClientActionContext(originating screen/client state)
  -> pushes transient SFMCommandPaletteScreen over origin
       -> ordered input buffer + Brigadier parse/completion/search
       -> viable actions by default + unavailable reason view
SFMDrawCanvasScreen implements SFMCommandButtonHost
  -> SFMDrawEditorSession
       -> camera / active tool / focus / dirty state
       -> SFMDrawCanvasModel
            -> all glyphs in canonical canvas coordinates + layer id
       -> primaryLayerId
       -> List<SFMDrawLayer>
            -> stable id + role (PRIMARY/REFERENCE/SCRATCH/CONTROLS/DRAWING)
            -> name + order + visible/muted + locked + source/edit policy
            -> no origin or transform
       -> transient SFMDrawLayerMoveTransaction
            -> captured member ids + original global positions + drag delta
       -> SFMDrawScene
            -> stable points / edges / paths / freehand strokes + layer id
            -> selection, ordering, undo actions
       -> List<SFMDrawCanvasControl>
            -> global canvas anchor + layer id + SFMClientActionBinding
            -> stable action id + fully bound typed arguments + presentation
             -> Commands binds sfm:palette/open(initialQuery="/sfm action ")
            -> Save / Cancel bind contextual sfm:document actions
       -> optional List<SFMDrawCommandHistoryElement>
            -> global canvas anchor + layer id + submitted command/result
            -> presentation only; never parser input or program projection
  -> SFMDrawWorkspaceStore
       -> versioned typed document
       -> normalized SFM-owned path

disk save = project(eligible glyphs whose layer id == primaryLayerId)
          != concatenate(all visible canvas text)
```

Layer membership never changes from pan, drag, overlap, or containment. A whole
layer move freezes its current member ids and their original global positions,
applies one canvas-space delta, and then discards the temporary capture. The
transaction may contribute a non-serialized undo action, but it never creates a
layer origin, parent transform, or persistent group.

Canvas controls are ordinary positioned/interactable scene elements in the
controls layer, not Minecraft widgets clamped to the viewport. Camera transforms
therefore determine both rendering and hit testing. A recovery action must be
available and tested so a user can frame the controls after intentionally
panning or zooming away from them.

An action registration is the single authority for identity, label/description,
typed arguments, structured contextual availability, execution, and
whether/how it may be pinned. The palette, keyboard shortcuts, and buttons do
not copy handlers. A button binding refers to that stable action id and stores
only validated bound arguments plus presentation/layout. If context later makes
the action unavailable, the binding remains persisted but does not execute.
Typed helpers such as an `SFMClientActionRequirement<SFMDrawCanvasScreen>`
return either the resolved Draw screen/session or a styled unavailable result;
the action executor consumes that resolved value and performs no second cast.

Brigadier's `CommandNode.canUse(source)` evaluates the predicate supplied by
`.requires`. Its parser and usage generation skip nodes that cannot be used,
which produces the familiar unknown-command behavior. Minecraft's server also
filters unusable nodes before sending a command tree to a player, so the client
normally never sees those literals. Empirical 1.19.2 tests show that a raw local
dispatcher can still suggest unavailable literal children; therefore
`SFMClientActionCommandTree` filters Brigadier completion results through the
same structured availability contract. It keeps the complete registered action
catalog so `list all`, `help`, and optional disabled-action presentation can
explain why an entry is unavailable. The server `/sfm` dispatcher cannot inspect
or authorize a client GUI screen, so it is not the authority for these actions.

The palette captures its context before `pushGuiLayer`: its originating screen
is the Draw screen, manager screen, title screen, or `null` in-world, never the
palette itself. It revalidates that origin before suggestions and execution so
stale screen/session actions fail closed with a useful unavailable reason.

Polygons and rectangles remain useful drawing and spatial-selection geometry,
but they do not designate the program, own nearby glyphs, or affect disk save.
If profiling later justifies a spatial index for hit testing or geometric
commands, it remains a derived, invalidatable cache equivalent to brute force.

Geometry should use typed point/edge/path records rather than the reference
branch's nullable all-element record. An edge references two point ids; moving
it translates both points. An arrow is a point path with an arrowhead policy.

## Source and implementation references

| Area | Reference |
| --- | --- |
| Current screen/grammar embedding | `platform/minecraft/src/main/java/ca/teamdman/sfm/client/screen/SFMDrawCanvasScreen.java` |
| Text/layout model | `platform/minecraft/src/main/java/ca/teamdman/sfm/client/screen/SFMDrawCanvasModel.java` |
| Projection/styling | `platform/minecraft/src/main/java/ca/teamdman/sfm/client/screen/SFMDrawCanvasSyntaxHighlightingHelper.java` |
| Editor open contract | `platform/minecraft/src/main/java/ca/teamdman/sfm/client/text_editor/SFMDrawCanvasTextEditorRegistration.java` and `client/screen/text_editor/ISFMTextEditScreenOpenContext.java` |
| Focused tests | `platform/minecraft/src/test/java/ca/teamdman/sfm/test/draw/` and `platform/minecraft/src/test/java/ca/teamdman/sfm/client/screen/SFMDrawCanvasScreenTests.java` |
| Templates/examples | `platform/minecraft/src/main/resources/assets/sfm/template_programs/` and `ca.teamdman.sfm.client.examples` |
| Grammar migration | [dependency source management v3 plan](dependency%20source%20management%20v3%20plan.md) |
| Reference Draw model/storage | `D:\Repos\Minecraft\SFM\worktrees\1.19.2-draw\platform\minecraft\src\main\java\ca\teamdman\sfm\client\draw\` |
| Reference Draw interaction | `D:\Repos\Minecraft\SFM\worktrees\1.19.2-draw\platform\minecraft\src\main\java\ca\teamdman\sfm\client\screen\SfmDrawScreen.java` |
| Reference mount | `D:\Repos\Minecraft\SFM\worktrees\1.19.2-mount\platform\minecraft\src\main\java\ca\teamdman\sfm\client\mount\SFMClientMountManager.java` |
| Puppet infrastructure | `platform/minecraft/src/gametest/java/ca/teamdman/sfm/gametest/puppet/` and [interactive preview plan](interactive%20gametest%20preview%20capture%20plan.md) |
| Workflow/release | `docs/AGENTS.md`, `docs/RELEASE_PROCESS.md`, and the parent [release plan](puppet%20propagation%20and%20preview%20matrix%20plan.md) |

## Execution order

```text
close UX/data gates
  -> central client action registry + distributed owner-defined actions
  -> contextual local `/sfm action list|help|invoke` Brigadier surface
  -> universal palette hotkey and originating-screen context
  -> Draw canvas button host + customizable Commands/Save/Cancel defaults
  -> global glyph, dynamic layer, and placement model
  -> command-driven grammar reference layer
  -> primary program layer and layer interaction
  -> extend commands to references, layers, geometry, and tools
  -> primary-layer projection and dirty warnings
  -> point/edge/path tools and undo
  -> versioned workspace persistence
  -> puppet and focused tests
  -> audit, propagate, and matrix review
  -> parent release plan
```

## Phase 0 — Close the contract and establish contextual client actions

### [x] 0.1 Inventory current implementation and reference branches

**Completion notes (2026-07-16):**

- Current screen has one unframed editable model, a separate grammar model,
  and `EmbeddedDocument`. Its `fitContent` stores per-frame `contentZoom`.
- SFML insertion begins on press, cancels on release over the button, and
  creates a fixed-size reference at the release point elsewhere. Press
  coordinates do not define the frame.
- Reference frames resize at corners but cannot move; main content has no frame.
- `feat/1.19.2/draw` is clean and was inspected read-only.
- `feat/1.19.2/mount` is dirty with tracked/untracked user work and was inspected
  read-only. No file in it was modified.
- The 1.19.2 root was clean at planning start, at commit `82d5c2369`.

**Validation:**

```powershell
sfm-propagate-changes.exe git status
rg -n "EmbeddedDocument|contentZoom|beginGrammarInsertDrag|finishGrammarInsertDrag" platform\minecraft\src\main\java\ca\teamdman\sfm\client\screen\SFMDrawCanvasScreen.java
```

**Completion criteria:** Coordinates, membership, rendering, insertion, save, tests, and
reference boundaries are recorded without changing a feature worktree.

### [~] 0.2 Close interaction, persistence, and release-floor gates

**Completion notes (updated 2026-07-18):**

- Glyph positions remain canonical global canvas coordinates.
- The earlier proposed primary/reference spatial-region ownership model is
  superseded. Dynamic typed layers now provide explicit semantic membership;
  rectangles and polygons are ordinary geometry and cannot affect disk save.
- Every persisted element has one layer id. Layers have no origin or transform;
  moving a layer is a temporary transaction over captured member ids/global
  positions.
- Primary, per-reference, scratch/drawing, controls, command, and history roles
  are represented by layers rather than inferred containment.
- Commands, Save, and Cancel are default canvas controls, not fixed screen
  widgets. Camera pan can move them out of view, with a required recovery path.
- Command activation remains explicit: the universal hotkey opens a palette and
  Draw's Commands control opens it prefilled with `/sfm action `;
  slash-prefixed program text is inert.
- Action viability uses the originating screen retained before the palette is
  pushed. Draw canvas buttons are configurable bindings to viable action ids,
  not independent callbacks.
- Registry lifecycle and action-definition ownership are now separated in the
  proposed contract: `SFMClientActions` creates/compiles once, while screens or
  shared capability/session owners declare individual action entries through
  non-creating deferred registers. `/sfm action list|help|invoke` is the proposed
  canonical local surface; friendly direct aliases remain gated.
- Reference edit/copy policy, control action mapping/recovery, workspace
  binding, command history, clobber semantics, mount scope, and final release
  floor remain open in the gate table.

**Work:**

- Review the gate table with the maintainer and record accepted decisions or
  replacements here.
- Name the Draw release floor. If drawing or persistence is deferred, replace
  “introduce Draw” with exact narrower release wording.
- Choose workspace identity/lifecycle before writing persistent player data.
- Decide reference-layer copy/edit interaction without destroying text
  selection and close whole-layer move transaction behavior.
- Close canvas-control mapping/recovery, palette hotkey, button persistence,
  command-history presentation, clobber semantics, and mount scope
  independently; none follows implicitly from the command bootstrap.

**Validation:** The gate table and completion notes contain no unresolved item
required by Phases 2, 4, or 5.

**Completion criteria:** Downstream tasks have a testable interaction, storage,
and release contract without guessing.

### [~] 0.3 Introduce distributed contextual actions and `/sfm action`

**Implementation notes (started 2026-07-18):**

- The first vertical slice is the context/availability/action/source kernel, the
  sole `SFMClientActions` registry creator, a non-creating document-action
  contributor, and `sfm:document/save_and_close` compiled beneath
  `/sfm action invoke`.
- Existing Draw Done/Shift+Enter behavior remains present while it is extracted
  behind the same named save-and-close target used by the registered action.
- `list`, `help`, the remaining document actions, palette UI, and canvas-control
  replacement remain part of this phase but follow after this executable seam is
  proven.

**First-slice evidence (2026-07-18):**

- Added the typed context, availability, requirement, source, action, compiler,
  and command-tree wrapper. `SFMClientActions` creates the registry once;
  `SFMDocumentActionTarget.Actions` contributes through a non-creating register.
  Client setup eagerly compiles the finalized registry so an accidental early
  empty-tree cache cannot survive into palette use.
- `SFMDrawCanvasScreen` implements the document target. Its existing Done and
  Shift+Enter paths now call the same public `saveDocumentAndClose()` operation
  used by `sfm:document/save_and_close`.
- Pure action metadata was kept outside the deferred-register holder after a
  headless test proved that referencing holder metadata initialized Forge state.
- A raw local Brigadier dispatcher suggested an unavailable literal despite
  `.requires`; `SFMClientActionCommandTree` now filters completions through the
  same requirement and rechecks the requirement immediately before execution.
- `sfm-propagate-changes.exe run compile --branch 1.19.2` passed;
  `sfm-propagate-changes.exe test run --branch 1.19.2` passed all 239 tests; and
  `sfm-propagate-changes.exe run client --branch 1.19.2 --smoke --solo` exited
  successfully.
- `sfm-propagate-changes.exe run data --branch 1.19.2` now passes after the
  CC:Tweaked lockfile component was changed to `data_run_policy: include`.
  Datagen loads `SFMLabelerTurtleUpgrade`, discovers
  `upgrade.sfm.labeler.adjective`, and writes the generated translation.
- Added the first palette vertical slice: `sfm:palette/open` is a registered
  client action, `SFMCommandPaletteKeyHandler` routes the universal Ctrl+K
  mapping through that action, and `SFMCommandPaletteScreen` preserves the
  originating screen context while offering an input field, Brigadier
  completion, keyboard/mouse selection, execution, and Escape/pop behavior.
  The palette uses `SFMScreenChangeHelpers` for screen transitions and is
  available over both the title screen and an in-world view.
- The palette slice was validated with
  `sfm-propagate-changes.exe run compile --branch 1.19.2`,
  `sfm-propagate-changes.exe test run --branch 1.19.2`, and
  `sfm-propagate-changes.exe run data --branch 1.19.2`.
- Completed the first command-surface pass: the local dispatcher now exposes
  `sfm action list [available|all]`, `sfm action help <action-id>`, and
  `sfm action invoke <action-id>`. List/help write structured feedback through
  `SFMClientActionSource`, which the palette can present without a second
  output mechanism.
- Registered `sfm:document/save` and
  `sfm:document/close_without_saving` beside
  `sfm:document/save_and_close`. The three actions share the typed
  `SFMDocumentActionTarget` seam, preserve the writable-document gate, and
  delegate to named Draw operations rather than duplicating screen logic.
- Added focused tests for list/help feedback, distinct save/close operations,
  and the existing stale-origin/availability checks. Compile, test, and data
  generation pass after this action-surface expansion.
- Registered the always-available `sfm:dump_registries` and `sfm:help` actions.
  Registry dumping writes `SFM/registries/<namespace>/<registry>/entries.txt`,
  per-registry summaries, and a root `summary.txt`; client-level registries are
  included when a world exists, while unavailable level-bound registries are
  recorded instead of aborting the whole dump. The help action opens the
  preferred text editor with a local command-palette guide.
- The palette’s initial `sfm` candidate was Brigadier’s root literal. The
  palette now starts at `sfm action invoke ` and preserves the trailing space
  while requesting completions, so action ids—not the root literal—appear as
  candidates. Execution still trims the command normally.
  Phase 0.3 remains in progress for its remaining actions and UI.

**Work:**

- Add `SFMClientActionContext`, `SFMClientActionAvailability`,
  typed `SFMClientActionRequirement<T>`, `SFMClientActionSource`, and one client-local
  `CommandDispatcher<SFMClientActionSource>`; never dispatch through chat or the
  server `/sfm` command tree. Context retains the originating screen and returns
  structured available/unavailable results with styled reasons.
- Add a client-only `SFMClientActions` custom registry through
  `SFMDeferredRegisterBuilder`, following the `SFMTextEditors` and
  `SFMResourceTypes` lifecycle. `SFMClientActions` is the sole registry creator,
  lookup owner, and compiler entry point; it calls `createNewRegistry()` exactly
  once and exposes a factory for non-creating contributor registers targeting
  the same registry key.
- Define one stateless `SFMClientAction` entry per operation. Its registry key
  is its stable action id; the value supplies label/description, typed argument
  registration and bound-argument serialization, requirement, executor, and pin
  capability. Do not register a coarse contribution that privately owns a
  second set of leaf action ids.
- Co-locate SFM-owned action declarations with the narrowest behavior owner.
  Draw-only operations may live in a nested `SFMDrawCanvasScreen.Actions` holder;
  operations shared by multiple editors belong to a shared editor/session
  capability owner. Each holder owns a non-creating deferred register and a
  client-bootstrap hook that attaches it to the mod event bus before registry
  freeze. Keep action suppliers stateless and prevent client screen classes from
  being loaded on a dedicated server. Permit other mods to target the same
  registry through their own namespace once the deferred-register namespace
  seam is verified.
- Register stable entries `sfm:palette/open`, `sfm:document/save`,
  `sfm:document/close_without_saving`, and
  `sfm:document/save_and_close`. The palette action has an optional typed
  initial-query argument and is the common target for the universal hotkey and
  Commands button; repeated invocation while the palette is active follows one
  tested toggle/focus policy. Document entries delegate to named session
  operations so Shift+Enter or future UI controls cannot acquire different save
  semantics.
- After registry initialization, compile actions in deterministic
  `ResourceLocation` order below local `/sfm action`. Provide
  `list [available|all]`, `help [<action-id>]`, and
  `invoke <action-id> [typed action arguments]`; generate each action-id child as
  a literal branch whose descendants come from that action's typed argument
  contract. Do not parse a greedy string with a second dispatcher.
- Derive `list`, `help`, palette search, completion, button identity, persisted
  bindings, and diagnostics from the registry. If friendly paths such as
  `/document save` are approved later, compile redirects to stable actions and
  reject alias/node collisions with both action ids in the diagnostic; never
  use last-writer-wins.
- Mark the document actions available only when the originating screen exposes
  the required Draw editor session/open context. Provide typed screen/session
  requirement helpers so the executor receives the resolved Draw target. Derive
  Brigadier `.requires`, command-tree completion filtering, palette viability,
  list filtering, help reasons, and button viability from the same availability
  result. Recheck at execution so a closed/replaced origin fails safely after
  parsing. `list all` and the palette's unavailable view retain the styled reason
  even though Brigadier itself only receives a boolean predicate.
- Make the three initial no-argument document actions pinnable; reject duplicate
  registry ids and generated command-path conflicts with actionable diagnostics.
- Define `save` as writing the current primary projection and remaining open;
  `close_without_saving` as using the existing dirty confirmation before
  closing; and `save_and_close` as saving successfully before closing.
- Parse only explicit palette/dispatcher input. Do not search the canvas,
  reconstruct nearby rows, or interpret an SFML projection as command input.
- Test sole registry creation, multiple non-creating owner contributors,
  client-only bootstrap/dedicated-server safety, deterministic compilation,
  duplicate/collision failure, stable action identity, `list`/`help`/`invoke`,
  typed arguments, structured availability, `.requires` parse filtering,
  command-tree completion filtering, unavailable reasons, execution-time
  recheck, save-writer invocation counts, confirmation, failure to save,
  originating-screen validation, and per-session source isolation. Prove
  arbitrary program glyphs cannot affect explicit command input.

**Validation:**

```powershell
sfm-propagate-changes.exe test run --branch 1.19.2
sfm-propagate-changes.exe run compile --branch 1.19.2
```

**Completion criteria:** The sole central registry creator and distributed
owner-defined action entries compile into the sole client dispatcher;
`/sfm action list|help|invoke`, the universal key/button, palette, and all three
document actions share stable ids and one availability/executor contract while
ordinary slash glyphs remain inert.

### [ ] 0.4 Add the universal palette and customizable Draw canvas controls

**Implementation notes (2026-07-18):**

- Registered `SFMKeyMappings.COMMAND_PALETTE_KEY` as a universal configurable
  `Ctrl+K` mapping and added `key.sfm.command_palette` localization.
- The mapping is present in `getSFMKeyMappings()` and has a regression test for
  its default key code and `KeyModifier.CONTROL` value.
- The handler and initial UI now exist: the handler polls the physical
  `Ctrl+K` state on the client tick (the same GUI-safe path used by the title
  screen editor shortcut), edge-debounces it, toggles an active palette, and
  invokes the registered `sfm:palette/open` action otherwise. The screen uses the current screen as
  its action origin, pushes over an existing GUI, replaces the in-world view,
  and restores the previous view on close. Rich action list/help/invoke
  commands, Draw canvas controls, narration, and persistence remain in this
  phase.
- Help now opens the preferred text editor as a true pushed layer, preserving
  the palette/title-screen stack when it closes. The read-only help document
  has no synthetic trailing newline, so Escape does not show a discard dialog
  unless the user actually edits it. Palette execution failures are logged
  through `SFM.LOGGER` and clipped to the panel width; Tab restores input focus
  when completion focus was lost. SFM-owned action ids are registered under
  their canonical resource locations (for example, `sfm:help`); presentation-
  only aliases can be layered onto completion later without duplicating the
  Brigadier command tree.
- The palette empty state now says `No available sub-actions`, distinguishing
  a valid terminal command from a command with more completion branches. An
  Execute button sits beside the input and is enabled only when Brigadier has
  parsed an executable terminal command.
- Preferred Draw-editor help now honors the overlay context as a pushed GUI
  layer and pops back to the palette, preserving the title-screen layer. The
  palette reclaims its active instance during reinitialization so completion
  results are still accepted after returning from help.
- Successful palette execution now restores the canonical `sfm action invoke `
  query and refreshes its completion list; failed commands remain in place for
  correction.

**Work:**

- Remove the fixed SFML button, Done button, grammar-button drag state, and
  their direct handlers. Do not spend implementation effort fixing the
  superseded SFML release gesture.
- Register one configurable universal palette key in `SFMKeyMappings` and handle
  key press/debounce through `@SFMSubscribeEvent`. Route it through the registered
  palette-open action. That action captures current screen/client context before
  pushing `SFMCommandPaletteScreen` with `SFMScreenChangeHelpers`; revalidate
  the origin before action execution.
- Implement palette text/search, Brigadier completion, viable-action filtering,
  optional unavailable reasons, keyboard/mouse navigation, narration, execute,
  and cancel without copying the command catalog.
- Define `SFMCommandButtonHost` and `SFMClientActionBinding`. Implement the Draw
  host with the minimum controls-layer model and seed localized Commands, Save,
  and Cancel bindings at default global coordinates. Render/hit-test them through
  the camera transform so panning can move them out of view.
- Bind Save and Cancel to the gate-selected registered document actions rather
  than duplicating close/save logic. Bind Commands to palette-open with the
  typed initial query `/sfm action ` and preserve the Draw screen as origin.
- Let the palette pin/unpin viable pinnable actions through its current button
  host. Persist stable action id, validated bound arguments, position, size, and
  presentation; when unavailable, retain configuration but hide the button.
- Add a keyboard-accessible frame-controls/Home recovery operation that brings
  the default controls back into view without resetting document coordinates.
- Execute with Enter, cancel/pop without modifying SFML, and restore focus to
  the originating screen/canvas location. Preserve Shift+Enter as a direct
  shortcut to the same `save_and_close` session operation.
- Add focused input/placement/action tests, camera-transform hit tests, pan-out
  and recovery tests, context-switch/stale-origin tests, viable/unavailable
  palette tests, pin/unpin persistence tests, and a live smoke capture of the
  hotkey, Commands, Save, Cancel, `/sfm action list|help|invoke`, and the three
  initial document action ids.

**Validation:**

```powershell
sfm-propagate-changes.exe test run --branch 1.19.2
sfm-propagate-changes.exe run compile --branch 1.19.2
sfm-propagate-changes.exe run client --branch 1.19.2
```

**Completion criteria:** The universal hotkey opens one contextual palette over
the correct origin; Draw users can pin/unpin viable action buttons; default
Commands, Save, and Cancel controls can pan out of view and be recovered;
Commands opens `/sfm action ` plus usable completions, and no fixed SFML/Done
button or grammar-drag behavior remains.

## Phase 1 — Establish dynamic layers and correct grammar behavior

### [ ] 1.1 Extract pure global-glyph, dynamic-layer, and placement models

**Work:**

- Keep one global `SFMDrawCanvasModel` whose glyphs store canonical canvas
  coordinates and a stable layer id. Add dynamic typed layer ids/roles
  (`PRIMARY`, `REFERENCE`, `SCRATCH`, `CONTROLS`, `DRAWING`, `COMMANDS`), names,
  order, visibility, lock state, source, and edit policy outside the screen.
- Enforce exactly one layer per persisted element and no layer origin/transform.
  Add explicit create/rename/reorder/mute/lock/reassign/copy-to-layer operations.
- Define reusable default and explicit placement requests for command-created
  content. Transform the top-centre usable-screen anchor into canvas coordinates.
- Add a layer-move transaction that freezes member ids/original global positions
  at start, applies one delta, and discards its capture on commit/cancel.
- Test empty/large layers, arbitrary overlap, reordering, muting, locking,
  reassignment, whole-layer movement, resolution/zoom, and explicit placement.

**Validation:**

```powershell
sfm-propagate-changes.exe test run --branch 1.19.2
sfm-propagate-changes.exe run compile --branch 1.19.2
```

**Completion criteria:** Layout, explicit layer membership, and placement are
deterministic without a screen; glyphs remain global, and no layer field can
shrink text or impose a coordinate hierarchy.

### [ ] 1.2 Implement command-driven grammar reference-layer placement and movement

**Work:**

- Register grammar insertion through Brigadier. With no placement arguments it
  uses the top-centre default; explicit coordinates/layer placement use typed
  arguments or a separately entered placement mode, never an SFML-button drag.
- Create a named read-only grammar reference layer and insert its glyphs into
  the global model with that layer id; do not create a child text model or a
  semantic bounding rectangle.
- Render grammar at the same base font scale as primary content. Its extent is
  whatever its globally positioned glyphs occupy; no fit-content zoom exists.
- Move selected grammar glyphs or the whole reference layer through explicit
  selection/layer operations over canonical global coordinates.
- Preserve grammar copy selection and provide Copy to Primary/Scratch actions
  that create editable copies under the resolved provenance policy.
- Preserve canvas pan/zoom and prove overlap with primary does not affect save.

**Validation:**

```powershell
sfm-propagate-changes.exe test run --branch 1.19.2
sfm-propagate-changes.exe run client --branch 1.19.2
```

**Completion criteria:** Command insertion uses top-centre by default, explicit
placement is typed/cancellable, grammar lives in a distinct read-only layer,
copy/move behavior is explicit, and grammar text matches primary font scale.

### [ ] 1.3 Assign current program and default canvas elements to typed layers

**Work:**

- Load `openContext.initialValue()` into the global glyph model with the stable
  primary layer id. Seed scratch/drawing/controls/commands layers as required.
- Make active-layer editing, focus, visibility, locking, and cross-layer draw
  order explicit. The default text cursor inserts into the primary layer unless
  a command/reference/tool interaction owns input.
- Keep one global cursor/glyph surface rather than one text model per layer.
- Add explicit copy/move-to-layer actions; spatial movement alone must never
  change primary/reference inclusion.

**Validation:**

```powershell
sfm-propagate-changes.exe test run --branch 1.19.2
sfm-propagate-changes.exe run compile --branch 1.19.2
```

**Completion criteria:** Program and grammar glyphs share one global coordinate
space with explicit primary/reference layer identities, and no layer hierarchy
restricts their positions.

## Phase 2 — Extend contextual actions to references, layers, geometry, and tools

### [ ] 2.1 Extend distributed action ownership and Brigadier presentation metadata

**Work:**

- Register one namespaced `SFMClientAction` entry per reference insertion,
  template, layer lookup/manipulation, geometry selection, or tool operation.
  Declare each entry beside the screen/session/capability that owns its behavior
  through a non-creating deferred register; the originating Draw session arrives
  only through `SFMClientActionSource`/context at
  parse/suggestion/execution time.
- Have entries supply typed Brigadier arguments plus styled label, description,
  optional aliases, structured availability, shortcut, and button-capability
  metadata through the central contract. The compiler mounts them beneath their
  stable ids under `/sfm action invoke` and derives list/help from the registry.
- Add typed layer-name/id arguments and dynamic layer suggestions so commands
  such as `/sfm action invoke sfm:layer/clobber layer1 layer2` never parse their
  operands ad hoc. A friendly `/clobber` alias may redirect only if the alias
  gate approves it. Geometric selectors use a separate explicit argument type
  if later admitted.
- Derive suggestions, searchable entries, help, and execution from the compiled
  tree and associated metadata, without a parallel switch/catalog.
- Test registry identity/provenance, definition-owner bootstrap,
  parse/completion, list/help/invoke, alias/collision diagnostics,
  originating-screen availability changes, unavailable reasons, execution
  recheck, pin capability, errors, and session isolation.

**Validation:**

```powershell
sfm-propagate-changes.exe test run --branch 1.19.2
sfm-propagate-changes.exe run compile --branch 1.19.2
```

**Completion criteria:** One tested client tree owns all Draw actions, exposes
the same contextual metadata to palette/buttons, and cannot accidentally invoke
a server/player command parser.

### [ ] 2.2 Evolve palette search, customizable buttons, and Draw history

**Work:**

- Refine the Phase 0 palette with prefix/fuzzy search, styled descriptions,
  keyboard navigation, mouse selection, narration, cancel, execution, focus
  restoration, and an optional unavailable-action/reason view.
- Add pin/unpin/configure affordances for the current `SFMCommandButtonHost`.
  Allow safe presentation customization (canvas position, size above an
  accessible minimum, label/icon/style override, and z-order) without allowing
  users to replace the registered action handler.
- Persist Draw button bindings/layout under the selected button-persistence
  scope. Re-evaluate availability on context/session changes; hide unavailable
  instances while preserving their configuration.
- Permit argument-taking actions only through fully typed, validated bound
  invocation records. Reject stale/missing layer ids with an unavailable reason
  instead of retaining a raw command string.
- Define submitted command/result history, selection, movement, clearing, and
  whether it survives workspace reopen. Optional Draw history uses a non-primary
  layer and remains presentation only; it is never parser input.
- Route Insert Grammar Reference and later actions through the same palette,
  action, and result path as the initial `sfm:document/*` entries.

**Validation:**

```powershell
sfm-propagate-changes.exe test run --branch 1.19.2
sfm-propagate-changes.exe run client --branch 1.19.2
```

**Completion criteria:** Palette discovery is universal, Draw buttons are
user-customizable views of viable registered actions, optional history is
canvas-native, and none creates a duplicate parser or path into disk SFML.

### [ ] 2.3 Add template and example reference commands

**Work:**

- Extend the grammar-source catalog from Phase 1 with bundled templates and
  examples as typed reference sources.
- Add insertion, completion, naming/collision policy, and useful load failures.
- Give each inserted reference its own named, read-only layer and globally
  positioned glyphs. Do not copy it into primary implicitly.
- Make clipboard and Copy to Layer create deliberate editable copies in the
  selected destination layer.
- Cache metadata and load large content only on execution.

**Validation:**

```powershell
sfm-propagate-changes.exe test run --branch 1.19.2
sfm-propagate-changes.exe run client --branch 1.19.2
```

**Completion criteria:** Grammar, templates, and examples can be found,
inserted, moved, read, and copied from through the common command surface.

### [ ] 2.4 Specify and implement typed layer-operation commands

**Work:**

- Define shared layer argument resolution, preview, confirmation, mutation,
  result, and undo conventions before adding destructive commands.
- Close the clobber gate: specify whether `/clobber <source> <target>` copies or
  moves layer members, whether target contents are deleted or overwritten, how
  global coordinates align, and how reference policy, element kinds, and order
  are handled.
- Require destructive commands to preview affected layer/element ids and obtain
  confirmation when data would be removed. Capture affected member ids at
  execution time without creating a layer origin or lasting group.
- Add parser/completion/preview/undo tests for named/id layers, missing or same
  source/target, visually overlapping layers, empty/read-only layers, mixed
  element kinds, and cancellation.
- Keep geometric-selector commands separate and unavailable until their own
  typed argument and mutation semantics are specified.

**Validation:**

```powershell
sfm-propagate-changes.exe test run --branch 1.19.2
```

**Completion criteria:** `/clobber` and any other enabled layer operation have
documented, typed, previewable, undoable semantics; unspecified destructive
commands remain unavailable rather than guessing.

## Phase 3 — Make save and scratch semantics deliberate

### [ ] 3.1 Keep editing global and layer membership deliberate

**Work:**

- Route text input through the global cursor/glyph model and an explicit active
  destination layer. No input path may introduce layer-local coordinates.
- Enforce reference-layer read-only policy according to Gate 0.2; an editable
  copy is a new globally positioned glyph set assigned to the destination
  layer.
- Define paste anchor, overlap/z-order hit tests, spatial selection, and
  explicit Copy/Move to Layer actions without a destination document object.
- Permit glyphs to move freely anywhere on the canvas without changing layer.
  Reassignment is a separate undoable semantic operation.

**Validation:**

```powershell
sfm-propagate-changes.exe test run --branch 1.19.2
```

**Completion criteria:** Every glyph has one canonical global position and one
explicit layer id; spatial movement needs no coordinate conversion and cannot
silently change save or edit policy.

### [ ] 3.2 Save the primary-layer projection and guard abandoned changes

**Work:**

- Filter eligible glyphs by the stable primary layer id in
  `getCurrentText()`/save writer, then order/project them through the existing
  canvas text projection. Regression-test overlap and arbitrary coordinates.
- Prove glyphs from every non-primary layer are absent regardless of position,
  while explicit copies/reassignments into primary follow the resolved
  reference-provenance policy.
- Track primary dirty state separately from workspace dirty state.
- Define save, workspace-save, discard, and close commands with precise
  confirmations.
- Warn for genuinely unpersisted non-primary material.
- Preserve open-context/server synchronization for disk programs.

**Validation:**

```powershell
sfm-propagate-changes.exe test run --branch 1.19.2
sfm-propagate-changes.exe run client --branch 1.19.2
```

**Completion criteria:** Disk contains exactly the ordered projection of
eligible primary-layer glyphs, and no changed workspace material is silently
discarded.

## Phase 4 — Add a coherent tool and geometry model

### [ ] 4.1 Introduce points, edges, paths, polygons, and scene actions

**Work:**

- Add typed stable ids/records for points, straight edges, arrow paths, closed
  polygons, and freehand strokes; avoid a nullable all-element record. Every
  persisted shape has a layer id and canonical global coordinates.
- Rectangle action creates four points and edges. Point/edge motion updates
  incident geometry without preserving a rectangle constraint.
- Polygons may support geometric hit-testing/selection, but never determine
  document ownership, layer membership, mutability, or disk inclusion.
- Add create/delete/move/order actions and undo/redo.
- Add move actions whose selected/captured set is fixed at operation start;
  undo stores affected ids/positions for the action, not a parent transform.
- Reimplement bounded behavior from old Draw reference with focused tests.

**Validation:**

```powershell
sfm-propagate-changes.exe test run --branch 1.19.2
```

**Completion criteria:** Pure tests deform a rectangle, move an edge, edit an
arrow, query overlapping polygons, assign shapes to layers, and undo/redo all
operations without making geometry an ownership boundary.

### [ ] 4.2 Add text/select, move, rectangle/polygon, line/arrow, freehand tools

**Work:**

- Add explicit active-tool input router. Text remains default and printable
  characters edit the primary layer unless visible command/tool mode owns input.
- Implement left-click/drag/cancel/cursors/selection and keyboard access.
- Implement the gated right-click chooser near pointer, not movable hotbar.
  Commands remains the complete alternate route.
- Define priority among camera, canvas controls, text, shape creation, and
  context menus.
- Support dragging lines to move endpoints and points to reshape paths.

**Validation:**

```powershell
sfm-propagate-changes.exe test run --branch 1.19.2
sfm-propagate-changes.exe run client --branch 1.19.2
```

**Completion criteria:** Release-floor tools work by mouse and Commands, do not
turn typing into shortcuts, and produce Task 4.1 geometry.

### [ ] 4.3 Integrate selection, ordering, movement, and undo

**Work:**

- Distinguish layer/point/edge/path selection from text ranges and individual
  global-glyph selection.
- Move shapes and whole layers with consistent snapping and z-order. Layer
  movement uses an operation capture rather than a persistent origin.
- Make create/delete/move/resize/projection/paste undoable; saving forms a clean
  checkpoint without corrupting history.
- Test mixed selections before enabling them; visibly defer unsupported mixes.

**Validation:**

```powershell
sfm-propagate-changes.exe test run --branch 1.19.2
```

**Completion criteria:** Enabled transforms have comprehensible selection and
complete undo.

## Phase 5 — Persist canvas state without changing disk semantics

### [ ] 5.1 Define and test a versioned workspace format

**Work:**

- Define typed schema for version, primary binding, camera, document
  layers/roles/source/edit policy, canonical global element positions,
  controls, stable action bindings/typed bound arguments, geometry, ordering,
  and chosen history policy.
- Explicitly exclude layer origins/transforms, move-operation capture sets, and
  spatial indexes from serialization. Persist each element's stable layer id.
- Decide reference source id/hash/snapshot behavior so mod-resource changes are
  visible and old workspaces remain reproducible.
- Use structured serialization, never JSON string concatenation. Validate ids,
  finite coordinates, sizes, source kinds, and versions.
- Write atomically below SFM-owned instance storage and reject traversal/symlink
  escape.
- Test golden round trips, malformed/oversized input, missing resources, future
  version refusal, explicit migrations, dangling/duplicate layer ids,
  missing/renamed action ids, invalid bound arguments, and proof that load
  preserves layer membership without changing global coordinates. Missing
  actions retain recoverable configuration but remain unavailable.

**Validation:**

```powershell
sfm-propagate-changes.exe test run --branch 1.19.2
sfm-propagate-changes.exe run compile --branch 1.19.2
```

**Completion criteria:** Workspace survives reopen without altering disk SFML,
malformed data fails safely, and migrations are documented/tested.

### [ ] 5.2 Bind editor contexts and resolve disk/workspace conflicts

**Work:**

- Implement Gate 0.2 binding for manager/disk, standalone developer editor,
  and template-preview contexts.
- Handle first open, moved/copied disks, missing file, stale primary hash,
  external edit, and resource upgrade.
- Never overwrite newer disk SFML because workspace has an old snapshot; prompt
  or create recoverable branch/snapshot when both changed.
- Expose save/open/rebind/recover through the command registry.

**Validation:**

```powershell
sfm-propagate-changes.exe test run --branch 1.19.2
sfm-propagate-changes.exe run client --branch 1.19.2
```

**Completion criteria:** Supported reopen/move/conflict cases are deterministic
and cannot silently lose disk or canvas work.

### [ ] 5.3 Control gate — decide manager mount integration

**Work:**

- Stop before modifying/importing `feat/1.19.2/mount`. Present stable workspace
  format, binding, evidence, and exact proposed mount functionality.
- Obtain explicit approval before config, packets, import/export, polling,
  snapshots, or changelog claims.
- If approved, reimplement required behavior on current baseline without
  merging/cleaning the dirty reference worktree.
- If deferred, record local workspace support and mount-sync exclusion.

**Validation:** Completion notes record explicit approval plus focused proof,
or explicit deferral with no mount-source change in normal branches.

**Completion criteria:** Mount scope is deliberate and cannot contaminate Draw
or the dirty feature worktree implicitly.

## Phase 6 — Prove the player workflow

### [ ] 6.1 Expand model, command, screen, and storage tests

**Work:**

- Cover action registry/compilation, universal hotkey debounce, originating
  context/availability, palette navigation, button pinning/customization,
  layer creation/identity/order/visibility/locking/placement, primary-layer
  projection, references, dirty decisions, geometry, movement capture, undo,
  serialization, migration, and binding.
- Prove arbitrary movement/overlap never changes layer membership. If geometric
  spatial acceleration is introduced, run identical fixtures through brute
  force and accelerated queries while asserting identical results.
- Keep pure logic outside screen; screen tests cover input priority, coordinate
  conversion, widget lifecycle, and rendering seams.
- Preserve regressions for spacing, blank lines, selection, clipboard, camera,
  and keyboard behavior.
- Audit and isolate version-dependent Minecraft APIs.

**Validation:**

```powershell
sfm-propagate-changes.exe test run --branch 1.19.2
sfm-propagate-changes.exe run compile --branch 1.19.2
cd platform\cli\sfm-propagate-changes
cargo run -- audit --branch 1.19.2 --version-surfaces
```

**Completion criteria:** Each layer has focused proof and shared logic contains
no unnecessary version branch.

### [ ] 6.2 Add a Draw editor walkthrough puppet

**Work:**

- Open Draw from the title-screen developer entry point without a world where
  possible.
- Open the universal palette on a non-Draw screen and show Draw-only actions as
  unavailable with reasons; then open Draw and show those actions becoming
  viable without relaunching.
- Capture default Commands/Save/Cancel controls; pin another viable action,
  reposition/customize it, pan controls out of view, and use the recovery
  action; Commands opening the palette at `/sfm action `; list/help/invoke and
  document-action suggestions; successful save while remaining open;
  close-without-save confirmation; the layer presentation; default grammar in
  its own read-only layer; equal font scale; visually overlapped primary and
  reference content that still saves correctly; whole-layer movement; copied
  template text into primary; rectangle/arrow/freehand; and a destructive
  layer-command preview if `/clobber` is in release scope.
- Add caption transcripts when supported, without blocking first proof.
- Assert artifact manifest and no modification of real saves/arbitrary paths.

**Validation:**

```powershell
sfm-propagate-changes.exe puppet list --branch 1.19.2
sfm-propagate-changes.exe puppet run draw_editor_walkthrough --branch 1.19.2
sfm-propagate-changes.exe puppet artifacts open --branch 1.19.2
```

**Completion criteria:** One client run creates a captioned visual story of the
Draw release floor and exits successfully.

### [ ] 6.3 Document commands, storage, projection, and recovery

**Work:**

- Document the universal palette hotkey, contextual action availability,
  pinning/customizing viable buttons, primary/reference/scratch/controls layers,
  Commands, tools, storage, disk projection, conflicts, and recovery.
- State that glyphs are globally positioned, each element has one explicit
  layer, layers have no coordinate origin, and only the primary layer determines
  disk projection. Grammar/templates remain excluded even when visually
  overlapping primary until the player explicitly copies/reassigns content.
- Rewrite the long `4.35.0 PRE` Draw implementation log as concise player
  outcomes suitable for a Draw-headline release.
- Keep puppets/audits/internal types out of player notes.

**Validation:**

```powershell
rg -n "TODO|PRE|Draw|grammar|workspace" docs platform\minecraft\src\main\resources\assets\sfm\template_programs\changelog.sfml
sfm-propagate-changes.exe run data --branch 1.19.2
```

**Completion criteria:** Docs/localized UI agree with shipped commands, data
boundary, and recovery behavior.

## Phase 7 — Propagate, review the matrix, and resume release

### [ ] 7.1 Commit an audit-ready 1.19.2 baseline

**Work:**

- Review changes against this plan and separate unrelated work.
- Run JUnit, compile, puppet, and source audit through SFM; record commits and
  artifact root.
- Confirm feature worktrees retain original status and normal branches contain
  no merge from either.

**Validation:**

```powershell
sfm-propagate-changes.exe test run --branch 1.19.2
sfm-propagate-changes.exe run compile --branch 1.19.2
sfm-propagate-changes.exe puppet run draw_editor_walkthrough --branch 1.19.2
cd platform\cli\sfm-propagate-changes
cargo run -- audit --branch core --version-surfaces
sfm-propagate-changes.exe git status
```

**Completion criteria:** Intentional baseline commits contain only approved Draw
scope and have passing 1.19.2 evidence.

### [ ] 7.2 Propagate with version seams and review a Draw matrix

**Work:**

- Merge from baseline with SFM, preserving newer-version behavior and moving
  genuine GUI/font/input differences behind annotated adapters.
- Audit before/after corrections; never copy the entire baseline screen over a
  later branch.
- Extend `sfm.audit_rules` with deny/permit rules for direct Minecraft screen
  transitions outside `SFMScreenChangeHelpers` and direct entity-to-level access
  outside `SFMEntityUtils`; add the later `Component.literal(...)` audit for
  unlocalized user-facing text, with explicit permits for intentional
  internal/debug literals; run the audit, then repair the cleanly mappable
  violations before final matrix sign-off. Keep this hardening pass after the
  current palette/layer implementation work.
- Compile/test core and run Draw walkthrough matrix at safe concurrency.
- Review font scale/spacing, placement, layer controls, Commands, canvas-native
  Save/Cancel behavior, shapes, captions, and confirmations for every target.

**Validation:**

```powershell
sfm-propagate-changes.exe git merge
sfm-propagate-changes.exe run compile --parallel --branch core
sfm-propagate-changes.exe test run --parallel --branch core
sfm-propagate-changes.exe puppet matrix draw_editor_walkthrough --branch core --parallel=2
cd platform\cli\sfm-propagate-changes
cargo run -- audit --branch core --version-surfaces
sfm-propagate-changes.exe git status
```

**Completion criteria:** Supported targets have clean source, automated proof,
reviewed figures, and annotated version seams.

### [ ] 7.3 Close Draw scope and resume parent release plan

**Work:**

- Record final public contract, support matrix, limitations, commits, tests,
  matrix root, and workspace format version here.
- Update parent Phase 5.1 with completed Draw evidence and approved scope.
- Continue parent metadata, datagen, full GameTest/JUnit, jars, installed
  verification, release approval, publication, hash validation, and cleanup.
- Do not duplicate or bypass parent Phase 9 control guard.

**Validation:**

```powershell
sfm-propagate-changes.exe git status
rg -n "Draw|workspace|Commands" "docs\tasks\draw editor document regions and commands plan.md" "docs\tasks\puppet propagation and preview matrix plan.md"
```

**Completion criteria:** Parent plan carries durable Draw evidence and no
release mutation occurs before its explicit approval gate.

## Cross-version acceptance matrix

| Target group | Targets | Required proof | Evidence |
| --- | --- | --- | --- |
| Baseline Forge | 1.19.2 | focused tests, compile, walkthrough, human figures | pending |
| Legacy Forge GUI | 1.19.4, 1.20, 1.20.1, 1.20.2, 1.20.3, 1.20.4 | compile/JUnit, annotated seams, walkthrough matrix | pending |
| Modern transition | 1.21.0, 1.21.1 | compile/JUnit, widget/font/resource seams, matrix | pending |
| 26-series | 26.1.2 | compile/JUnit, extractor/font/input/storage seams, spacing review | pending |
| Reference branches | `feat/1.19.2/draw`, `feat/1.19.2/mount` | status unchanged; no merge/propagation/artifact | pending final check |

## Risk register

| Risk | Guardrail |
| --- | --- |
| Grammar text shrinks again | No per-document/layer scale; matrix comparison with primary text. |
| Layer movement surprises saved SFML | Explicit stable membership plus frozen member-id/global-position move transactions and primary projection tests. |
| Layer transforms constrain glyph freedom or corrupt coordinates | Canonical global coordinates; no serialized layer origin/parent transform; independent movement tests. |
| Spatial index becomes observable or stale | Restrict it to derived geometry/hit-test acceleration with brute-force equivalence and invalidation tests. |
| Visually overlapping layers blur document ownership | Primary inclusion uses explicit layer id only; overlap/reorder/mute tests prove geometry is irrelevant. |
| Reference editing silently affects source or disk | Read-only reference layers and explicit Copy/Move to Layer actions with undo/provenance tests. |
| Canvas controls are panned away and cannot be recovered | Keyboard-accessible frame-controls/Home action, direct document shortcuts, and pan/zoom recovery tests. |
| Command entry points diverge | One client-only action registry supplies stable ids, metadata, availability, and pin capability to one compiled Brigadier dispatcher used by palette/buttons/shortcuts. |
| A screen accidentally creates a second action registry | `SFMClientActions` alone calls `createNewRegistry()`; contributor factories are non-creating and tests attach several owner registers to the same key. |
| Distributed contributors load client screens on a dedicated server | Register owner contributors through an explicit client-only bootstrap and include dedicated-server classloading/compile proof. |
| Two action ids or aliases silently replace each other | Deterministic compilation rejects duplicate registry ids and generated command/alias collisions with both owners in the diagnostic. |
| Palette evaluates itself instead of the originating screen | Capture host context before pushing the palette, revalidate it for suggestions/execution, and fail stale contexts closed with a reason. |
| Universal hotkey repeats or conflicts | Standard configurable key mapping, press/debounce tests across GUI/in-world contexts, and an explicit default-binding gate. |
| Slash-prefixed program text executes accidentally | Only the palette input dispatches; program glyph tests cover comments, `/sfm action`, `/document`, `a/b`, and `a / b` as inert SFML text. |
| A pinned button bypasses context or drifts from its command | Store stable action id plus typed bound arguments; derive viability/execution from the registered action and preserve unavailable bindings without execution. |
| A registry action disappears after a mod/update | Keep recoverable button configuration, display a missing-action reason in customization, and never invoke stale raw command text. |
| Large references render/allocate too much | Lazy load, limits, viewport culling, performance fixtures; never font shrink. |
| Workspace overwrites newer disk | Separate hashes, explicit conflict resolution, atomic writes, recovery. |
| Path escapes instance root | Normalized SFM root, traversal/symlink checks, path tests. |
| Persisted format cannot evolve | Typed variants, version, strict validation, migrations, future refusal. |
| Old Draw complexity is copied | Reference-only inventory; bounded reimplementation and tests. |
| Dirty mount work is lost/released | Phase 5.3 approval gate; never edit/clean/merge it. |
| Refactor creates version drift | Pure logic, narrow annotated seams, before/after audits. |
| Scope grows after verification | Close release floor in Phase 0; additions reopen tests/matrix/docs/scope. |

## Overall completion criteria

- [ ] Fixed SFML and Done buttons are gone; default Commands, Save, and Cancel
  controls live at global coordinates in a controls layer, pan/zoom with the
  canvas, invoke registered document actions, and have a tested recovery path.
- [ ] One configurable universal hotkey opens an accessible palette over the
  retained originating screen. Draw's Commands control opens the same palette
  at `/sfm action ` with list/help/invoke and the registered document actions.
- [ ] `SFMClientActions` creates the client-only registry exactly once; screens
  and shared capability/session owners declare namespaced action entries through
  non-creating deferred registers without unsafe dedicated-server classloading.
- [ ] One registry entry is one stable action id. Deterministic compilation
  produces the sole local `/sfm action list|help|invoke` Brigadier dispatcher and
  rejects duplicate ids and ambiguous generated paths/aliases.
- [ ] Palette, shortcuts, and buttons share structured availability/execution.
  Draw users can pin/unpin and safely customize viable action buttons; stale or
  unavailable bindings remain recoverable but cannot execute.
- [ ] Grammar is inserted through the shared command system, uses top-centre as
  its no-argument placement, has typed explicit placement alternatives, and
  creates one named read-only reference layer per inserted reference.
- [ ] Primary/grammar/template text share base font scale; layers never shrink
  text to fit.
- [ ] Every persisted element has a stable layer id and canonical global
  position; layers have no origin/parent transform, and whole-layer movement is
  a transient captured-member transaction.
- [ ] Spatial movement/overlap never changes layer membership. Copy/Move to
  Layer is explicit, undoable, and honors read-only reference policy.
- [ ] The stable primary layer identifies program glyphs; rectangles/polygons
  have no document-ownership semantics.
- [ ] Disk writes exactly the eligible primary-layer glyph projection, never
  controls, references, shapes, or other workspace metadata.
- [ ] Rectangle/polygon, line/arrow, and freehand use stable geometry and undo.
- [ ] Versioned path-safe workspace persists advertised non-disk state and
  resolves conflicts without silent loss while omitting layer origins,
  spatial indexes, parent pointers, and transient move captures.
- [ ] Mount is explicitly approved/proven or excluded; dirty worktree untouched.
- [ ] Tests, compile, audit, live puppet, and human matrix pass on all supported
  normal targets.
- [ ] Player docs/localization/changelog match shipped behavior.
- [ ] Parent plan records evidence and retains all publication authority.
- [ ] No Gradle or literal feature-branch merge was used.
