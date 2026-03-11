# SFM IDE V3 — Architecture Notes, Taxonomy, and Prototype Plan

## 1. Why V3 Exists

We are no longer designing "a better manager screen." We are designing a **general in-game IDE surface** for interacting with SFM concepts, world capabilities, documents, queries, and commands.

The current friction is real:

- `AbstractContainerScreen` gives us vanilla inventory sync and interaction semantics.
- `AbstractContainerScreen` also invites JEI and other mods to treat the screen as inventory territory.
- Our IDE ambitions exceed what a container GUI wants to be.

So the core V3 shift is:

> Treat the IDE as a sovereign client screen with explicit data/session plumbing, not as a disguised chest GUI.

That does **not** mean abandoning server authority. It means separating:

1. **screen ownership**
2. **data synchronization**
3. **inventory mutation authority**

Those are currently entangled by the menu/container pattern.

---

## 2. Mechanisms At Play

### 2.1 Vanilla/Forge container mechanism

What the menu/container stack gives us:

- authoritative server-side inventories
- automatic slot synchronization
- built-in click semantics
- compatibility with existing block `openMenu(...)` flows

What it also does:

- marks the UI as a container-like surface
- encourages overlay mods to attach themselves
- couples our screen identity to inventory affordances
- pushes us toward slot-first UI thinking instead of IDE-first UI thinking

### 2.2 Screen sovereignty

For an IDE, we want a plain `Screen` that:

- can be opened from a keybind, tool, command, or interaction
- is not automatically treated as a container by third-party mods
- owns its own layout, focus, drag, and rendering model
- can inspect arbitrary blocks, not only manager blocks

### 2.3 Authority split

We should explicitly model three layers:

1. **Client presentation layer**
	- layout
	- focus
	- command palette
	- rendering
	- scrolling
	- editor state

2. **Client IDE session layer**
	- current target block/entity
	- current document/query
	- cached capability snapshots
	- subscriptions / refresh requests

3. **Server authority layer**
	- inventory mutations
	- capability reads that require authoritative access
	- command execution
	- save/apply/rebuild actions

This is the key reframing: **the menu is one possible transport, not the ontology of the IDE.**

### 2.4 Acquisition of context

A general IDE can be opened by:

- keybind toggle
- using the manager block
- using `NetworkToolItem`
- command
- clicking an in-IDE link to inspect another block

The client already knows enough to seed a session:

- player position
- look vector
- raycast hit
- dimension
- held item
- optionally targeted `BlockPos`

That means "open IDE" and "bind IDE to a target" should be separate operations.

---

## 3. Hyprland Lessons That Matter Most

Hyprland is useful here less because it is a Linux compositor and more because it has a clean ontology for layout.

### 3.1 Targets, not widgets

Hyprland's strongest lesson is that the layout engine should manage abstract **targets**, not concrete windows.

For SFM:

- a text editor panel
- a terminal panel
- a capability inspector
- a metrics plot
- a tab group
- a query result table
- a floating popup

...should all be layout targets.

The layout system should only care about things like:

- bounds
- minimum size
- maximum size
- floating/docked state
- focusability
- visibility

It should not care whether the content is text, slots, charts, or block capability trees.

### 3.2 Separate tiled and floating paths

Hyprland keeps tiled and floating in distinct algorithm families. We should do the same.

For SFM:

- **Docked/tiled** panels are the main IDE structure.
- **Floating** panels are transient tools: autocomplete, quick docs, rename box, recipe viewer, detached inspector.

Do not try to unify both with one pile of conditionals.

### 3.3 Algorithm hot-swapping is a feature, not a luxury

Hyprland supports multiple tiling algorithms. That maps extremely well to an IDE.

SFM should plan for at least:

- **Dock** / edge-carving layout for familiar IDE framing
- **Master** layout for editor + side stack
- **Monocle** for focus mode
- **Scrolling columns** for notebook / browser-like workflows
- potentially **Dwindle** for exploratory layouts

Even if V3 ships with one algorithm, the interfaces should assume more than one.

### 3.4 Work area vs full screen

Hyprland's `CSpace` distinction is important.

SFM also needs a concept of:

- full game viewport
- reserved HUD/overlay space
- usable IDE work area
- floating work area

This matters when chat, subtitles, debug overlays, or future IDE chrome take space.

### 3.5 Focus history matters

When closing or hiding a panel, the next focus target should usually be MRU-based, not arbitrary adjacency.

This will make the IDE feel dramatically more competent.

### 3.6 Drag controller separate from layout engine

Hyprland splits drag state from layout logic. We should too.

Separate systems:

- layout reducer / algorithm
- pointer gesture interpreter
- resize/drag transaction controller

This avoids corrupting layout state during in-progress drag gestures.

### 3.7 Grouping and tabs as first-class citizens

Hyprland's group target model is a strong precedent.

For SFM, tab groups should not be a decoration on top of panels. They should be part of the model:

- a tile may host a single panel
- or a tab group containing multiple documents/views

### 3.8 Command bus / `layoutMsg`

Hyprland's layout-specific message dispatch is highly relevant.

SFM should support commands like:

- `panel.toggle terminal`
- `layout.set master`
- `layout.preselect right`
- `focus.explorer`
- `inspect.capabilities east`

The terminal, keybinds, command palette, and buttons should all route into the same action bus.

### 3.9 Predictive sizing and constraints

Before opening a panel, know where it probably goes and how large it should be.

This is especially important once we support:

- split previews
- drag ghosts
- tab detach/attach
- animated openings

### 3.10 Override priority system

Hyprland's override priority idea maps well to IDE layout hints.

Example priority order for SFM:

1. layout default
2. workspace preset
3. panel type hint
4. user explicit pin/resize
5. temporary gesture override

This gives us sane behavior without losing user intent.

---

## 4. Excalidraw Lessons and the Infinite Canvas Angle

Excalidraw solves a different problem, but the feel is important:

- direct manipulation
- low-friction box and connector creation
- infinite canvas navigation
- strong spatial memory
- content arranged in a scene, not only in panes

That suggests the IDE should not be purely panel-docked.

### 4.1 Two spatial modes, one IDE

We should explicitly support both:

1. **Dockspace mode**
	- traditional IDE framing
	- editor / explorer / terminal / inspector

2. **Canvas mode**
	- infinite 2D scene
	- boxes, notes, query nodes, block nodes, arrows, recipes, flow diagrams

This mirrors Hyprland's separation mindset: different spatial models deserve different logic.

### 4.2 The canvas is not a replacement for docks

The canvas should complement docks.

Best framing:

- docks are for **working**
- canvas is for **thinking**

Examples:

- sketching factory topology
- pinning capability inspectors as nodes
- showing item/fluid/energy relationships
- visualizing query pipelines
- laying out notes beside code and metrics

### 4.3 Shared primitives across dockspace and canvas

To avoid building two separate IDEs, both spatial modes should share:

- `IdeAction`
- `IdeResult`
- target/view identity
- tab/document model
- inspector widgets
- syntax-highlighted text views

Only the placement model changes.

---

## 5. Proposed Ontology / Taxonomy

We need stable terms.

### 5.1 Core nouns

#### `IdeSession`
The currently open client IDE instance.

Contains:

- focused target
- active workspace/layout
- open documents
- panel graph
- command history
- cached inspections
- subscriptions to server data

#### `IdeTarget`
An inspectable or editable thing.

Examples:

- manager block at `BlockPos`
- arbitrary block at `BlockPos`
- virtual disk file
- selector query result
- recipe
- terminal result
- metrics stream

#### `IdeView`
A renderable presentation of a target.

Examples:

- text editor view of a disk
- table view of inventory contents
- graph view of capabilities
- metrics chart view
- tree view of block faces and handlers

#### `IdePanel`
A concrete layout target on screen.

Panels host views.

#### `IdeGroup`
A tabbed collection of panels/views occupying one slot.

#### `IdeWorkspace`
The current arrangement of panels, groups, floating tools, and optionally canvas state.

#### `IdeDockspace`
The tiled/docked layout surface.

#### `IdeCanvas`
The infinite scene surface.

#### `IdeTool`
A transient or floating utility view.

Examples:

- autocomplete
- quick open
- recipe popup
- hover docs

#### `IdeAction`
An intent that can be invoked from keyboard, command palette, terminal, button, or script.

#### `IdeResult`
A typed result object returned from actions, terminal commands, or queries.

### 5.2 Important distinctions

#### Tile vs Panel

- **Tile**: a slot in a layout algorithm
- **Panel**: the actual UI target occupying it

#### Panel vs View

- **Panel**: placement/container concern
- **View**: rendering of content concern

#### Document vs Inspection

- **Document**: durable editable content, e.g. disk program, notes
- **Inspection**: live world-derived read model, e.g. block capabilities

#### Docked vs Floating vs Canvas-Pinned

- **Docked**: participates in tiling layout
- **Floating**: free-position window/tool
- **Canvas-pinned**: positioned in infinite scene coordinates

---

## 6. Proposed System Architecture

## 6.1 Opening model

Stop thinking "manager menu opens manager IDE." Think:

- player opens IDE
- IDE binds to a target
- target may be manager, chest, machine, cable, disk, or world query

More accurately, the IDE is becoming a **shell**:

- it has ambient context
- it has commands/actions
- it has variables/bindings
- it can inspect and mutate external sources through authority boundaries
- it may or may not currently be centered on one target

Suggested entry points:

1. keybind: toggle IDE
2. keybind: inspect looked-at block in IDE
3. manager block interaction: open IDE pre-bound to manager
4. network tool: "open rich inspector"
5. slash command: `/sfm ide open`
6. command palette: invoke an open/focus/inspect action directly

### 6.1.1 Session bootstrap packet

The client should request an IDE session bootstrap for a target.

Response should contain enough initial state to render immediately:

- target type
- target `BlockPos` and dimension
- display label
- capability summary by direction
- manager program if applicable
- inventory summaries if applicable
- metrics/state if applicable
- available actions

This avoids menu-driven identity while preserving server authority.

### 6.1.2 Binding model: shell context, not only one target

"Bind IDE to a target" should not mean there is only ever one thing in scope.

The better mental model is:

- the IDE session has an **ambient shell context**
- actions and views can introduce additional local bindings
- documents, inspectors, and queries may each carry their own bound context

That matches existing SFM thinking better than a single-target worldview.

Existing precedent already exists in SFML:

- label identifiers resolve through `LabelPositionHolder`
- globals already exist conceptually through `GLOBAL`
- the program language already has a notion of symbolic lookup

That means the IDE can naturally expose shell bindings like:

- current player position
- current look vector
- current raycast hit
- current focused block
- current manager
- current selection set
- current document
- current face / direction context

### 6.1.3 Binding precedence and shadowing

We should explicitly define precedence rules instead of letting them emerge accidentally.

Example motivating case:

```sfm
INPUT FROM chest1
OUTPUT TO chest2
```

and future cases like:

```sfm
INPUT FROM minecraft:chest
OUTPUT TO chest2
```

Suggested resolution layers, highest precedence first:

1. local command/query bindings
2. document-local aliases/imports
3. manager/disk label bindings
4. session globals / shell variables
5. world-derived symbolic bindings (block ids, block entity kinds, tags)
6. explicit namespaced resource identifiers

The important rule is that **labels should win over broad world names**, because labels are the user's intentional naming mechanism.

Resource-location strings should remain the escape hatch for exact addressing.

### 6.1.4 Everything is a set

The shell should bias toward Alloy-style set semantics.

Instead of assuming one current target, model:

- a focused target
- a selected set
- optionally a watched set

This helps unify:

- selector/query results
- multi-block inspection
- multi-cursor editing
- batch actions
- canvas node selections

The default set may contain one element, but the API should not require singularity.

## 6.2 Data flow model

### Read path

1. client asks for bootstrap / refresh
2. server inspects block or manager
3. server sends typed snapshot packets
4. client updates session cache
5. views re-render from cache

### Write path

1. user invokes action
2. client validates local preconditions
3. client sends command packet
4. server applies authoritative mutation
5. server emits updated snapshot / diff

### Subscription path

For live panels:

- terminal output
- manager metrics
- watched inventory/capability state

...the session should support subscriptions with explicit throttling.

## 6.3 Screen sovereignty rule

The main IDE screen should be a plain `Screen`.

If inventory interaction is needed:

- represent it as an IDE view
- route mutations through explicit packets
- do not inherit container-screen semantics unless a specialized fallback view truly requires it

This is the cleanest route around JEI-style hijacking.

## 6.4 Layout subsystem

We should formalize two layout subsystems that share target abstractions.

### A. Dock layout

Initial algorithm:

- ordered edge carving / dock reducer

Future algorithms:

- master
- monocle
- scrolling columns
- dwindle-like splits

### B. Canvas layout

Scene graph with:

- pan/zoom
- node bounds in world-space coordinates
- selection box
- connectors/arrows
- snapping/grid guides
- pinned inspectors/documents

### Shared layout interfaces

Suggested concepts:

- `IdeLayoutTarget`
- `IdeDockAlgorithm`
- `IdeFloatingManager`
- `IdeCanvasNode`
- `IdeDragController`
- `IdeWorkArea`

## 6.5 Panel families

Minimum panel/view families:

1. **Editor**
	- syntax highlighting
	- virtual scrolling
	- multiple cursors
	- undo/redo

2. **Terminal**
	- slash command execution
	- implicit `/sfm ide ...` prefix
	- output as `IdeResult`

3. **Explorer / Query Lens**
	- documents
	- targets
	- saved queries
	- world resources / labels / disks

4. **Inspector**
	- block state
	- block entity state summary
	- capabilities per face and null direction
	- slots/tanks/energy buffers

5. **Metrics**
	- tick times
	- timings
	- throughput
	- recent events

6. **Problems / Diagnostics**
	- parser errors
	- lint
	- capability access failures
	- execution problems

7. **Canvas / Whiteboard**
	- boxes
	- connectors
	- pinned inspector nodes
	- notes

8. **Recipes / Knowledge**
	- recipe viewer
	- item metadata
	- capability docs

## 6.6 Action system

All interaction should route through `IdeAction`.

Triggers:

- keybind
- command palette
- terminal command
- button click
- explorer selection gesture
- canvas gesture completion

Action categories:

- session
- layout
- focus
- editor
- terminal
- inspection
- navigation
- world mutation

Strong recommendation:

- actions return optional `IdeResult`
- actions can emit undo units
- actions can declare enablement/context requirements

### 6.6.1 Action identifiers

Commands/actions should use canonical resource-location-style ids.

Examples:

- `sfm:panel.toggle_terminal`
- `sfm:panel.focus_explorer`
- `sfm:layout.split_down`
- `sfm:layout.set_master`
- `sfm:view.scale_increase`
- `sfm:inspect.target_at_cursor`

This gives us:

- registry-friendly identifiers
- i18n lookup friendliness
- scriptability
- future plugin extensibility

### 6.6.2 Command surface: both slash and shell-native

The current terminal bridge via Minecraft slash commands is still useful.

Recommended long-term shape:

1. commands under `/sfm ide ...`
2. shell-native action execution inside the IDE
3. translation between the two where practical

So, for example:

- command palette invokes an action id
- hotkey invokes the same action id
- terminal may invoke that action id directly
- slash commands may forward into the same registry

This keeps the registry central and the input surfaces plural.

### 6.6.3 Universal undo tree

We should target an undo **tree**, not only a linear stack.

Reasons:

- layout experiments branch
- document edits branch
- command-driven state changes branch
- canvas manipulation branch

At minimum, every action should be able to declare:

- whether it is undoable
- how to undo
- how to redo
- whether it merges with adjacent actions

Long term, a unified history across layout + text + selection + canvas is a major differentiator.

## 6.7 Input, hotkeys, and command palette

If the IDE is a shell, it also needs a serious input system.

### 6.7.1 Hotkey manager

We will need a hotkey manager more like VS Code than a handful of fixed `KeyMapping`s.

Responsibilities:

- map key chords to action ids
- allow context-sensitive bindings
- support defaults plus user overrides
- support dispatch based on focused panel/mode
- expose bindings in the command palette and help UI

There are still two layers to respect:

1. vanilla/Forge persisted key mapping mechanisms
2. SFM IDE internal binding resolution

We should acknowledge the vanilla persistence model even if the IDE eventually adds richer chord/context logic on top.

### 6.7.2 Canonical keybinding strings

We need a canonical string representation for bindings.

Examples of the shape we probably want:

- `Ctrl+K`
- `Ctrl+Shift+P`
- `Alt+Left`
- `Ctrl+=`
- `Ctrl+MouseWheelUp`

This representation should be:

- human-readable
- serializable
- stable enough for config files
- convertible to and from Minecraft key mapping data where possible

### 6.7.3 Meta/layout modifier

We likely need a layout-manipulation modifier similar in spirit to Hyprland's main modifier.

Because the OS already owns the Windows key, the pragmatic current candidate is:

- **Alt** as the layout/meta modifier

Examples:

- `Alt+Arrow` move focus between panels
- `Alt+Shift+Arrow` move panel in direction
- `Alt+Drag` rearrange or detach panel
- `Alt+R` enter resize mode

This should remain configurable, but designing around a dedicated layout modifier is sensible.

### 6.7.4 Command palette

The command palette should be treated as a first-class shell surface, not a bolt-on.

It should support:

- action search by label and id
- recent actions
- context-aware filtering
- showing current hotkeys
- invoking layout commands without menus

## 6.8 Scaling model

The IDE should respect vanilla GUI scale while also having its own internal scaling controls.

We need at least:

1. **Vanilla GUI scale**
	- global MC environment constraint

2. **IDE global scale factor**
	- applies to the whole IDE shell

3. **Per-target or per-view scale factor**
	- useful for terminals, graphs, inspectors, or canvas nodes

This suggests actions like:

- `sfm:view.scale_increase`
- `sfm:view.scale_decrease`
- `sfm:view.scale_reset`
- `sfm:workspace.scale_increase`

The key point is to maintain crisp, intentional rendering while respecting vanilla settings rather than fighting them.

## 6.9 Result system

`IdeResult` should be central.

Result types likely include:

- text
- rich text/log lines
- table
- tree
- item stack list
- world selection
- capability report
- metrics series
- graph / node-connector scene
- document open request

This is how the terminal becomes more than chat-in-a-box.

## 6.10 Query model

We likely want a DSL, but not all at once.

Three layers are sensible:

1. **Direct actions**
	- `inspect target`
	- `open disk`

2. **Structured selectors**
	- block/resource/capability queries

3. **Relational / Kusto-like pipeline layer**
	- filter
	- project
	- join
	- summarize

Recommendation: start with a typed selector/query AST and only later grow full language surface.

## 6.11 Inspection model for arbitrary blocks

Given a `BlockPos` and `SFMResourceTypes`, the IDE should be able to ask:

- what capabilities are exposed?
- for which directions plus null direction?
- what resource types do they correspond to?
- how many slots/tanks/buffers exist?
- what are the current contents?
- what operations are permitted?

This suggests a canonical data model like:

- target block header
- faces: `UP`, `DOWN`, `NORTH`, `SOUTH`, `EAST`, `WEST`, `NONE`
- per-face capability entries
- per-entry resource handler summary
- per-handler detailed contents

The inspector should render the same data in multiple views:

- tree view
- slot grid
- table view
- canvas node

## 6.12 Document and VFS model

Treat manager scripts and related notes as a virtual filesystem layer.

Potential document sources:

- manager-attached disk
- disks found by query
- transient scratch buffer
- generated capability report
- saved canvas/notes documents

This keeps editor concepts coherent even when the backing data is world-based.

## 6.13 Spatial navigation model

Spatial navigation needs to be a first-class behavior, not an afterthought.

### Dockspace navigation

In a split/tiled layout, the obvious primitives are:

- focus nearest panel up/down/left/right
- move focused panel up/down/left/right
- resize focused split toward a direction

This maps well to Hyprland-style directional semantics.

### Canvas navigation

The canvas is trickier because "the thing down-right" is ambiguous.

We should likely support:

- 4-direction navigation by default
- optional 8-direction navigation
- a small timing window to disambiguate diagonal intent if desired
- geometric nearest-neighbor search by angle cone + distance

The important thing is that spatial navigation should feel deterministic and learnable.

## 6.14 Document source, buffer, and view model

We also need to separate three things clearly:

### `DocumentSource`

The origin or destination of truth.

Examples:

- a disk in a manager
- a disk found in the world
- a generated report target
- a saved note file

### `DocumentBuffer`

An editable in-memory representation.

This is what cursors, undo, syntax highlighting, and edits operate on.

### `DocumentView`

The current rendering/editor presentation of that buffer.

This lets us describe states more clearly than "borrowed" vs "owned."

Suggested document lifecycle states:

1. **Live read-through**
	- source is being observed
	- buffer may mirror source updates
	- not yet detached for editing

2. **Detached snapshot**
	- buffer cloned from source
	- source no longer mutates buffer automatically

3. **Modified buffer**
	- buffer differs from source snapshot

4. **Written/published**
	- buffer contents pushed back to a source

5. **Auto-saved draft**
	- buffer state persisted somewhere, not necessarily written to original source

This framing is much cleaner for features like:

- watch windows over live text
- editing borrowed content by detaching it
- unsaved indicators
- auto-save
- conflict handling if the source changes while editing

---

## 7. Recommended Design Principles

1. **Plain `Screen` first.**
2. **Server authority always.**
3. **Actions over ad-hoc handlers.**
4. **Targets/views/layout separated.**
5. **Dockspace and canvas are peers.**
6. **Everything inspectable is linkable.**
7. **Typed results, not raw strings.**
8. **Panel state retained across renders.**
9. **Float internally, snap at render time.**
10. **All user-facing strings go through i18n.**
11. **Shell context and binding precedence are explicit.**
12. **Assume sets, not only singular targets.**
13. **Input dispatch is contextual and user-remappable.**
14. **Undo spans more than text editing.**

---

## 8. Concrete Prototype Direction

## 8.0 Current implementation snapshot

The current `1.19.2` prototype has moved beyond a static playground sketch.

Implemented so far:

- sovereign plain `Screen` playground, opened in-game via `Alt+I`
- dock reducer with four concrete regions:
	- left: shell context
	- top/center: workspace
	- bottom: terminal
	- right: layout
- explicit `IdeSession` model with:
	- shell-context snapshot
	- focused target
	- selected target set
- action bus / registry using canonical ids such as:
	- `sfm:panel.toggle_shell`
	- `sfm:panel.toggle_layout`
	- `sfm:panel.toggle_terminal`
	- `sfm:selection.select_focused`
- terminal action surface that can run canonical ids, shorthand aliases, and `/sfm ide ...`-style forms
- panel focus and visibility state retained on the client side instead of being recomputed ad hoc each frame
- directional panel navigation with:
	- `Alt+Arrow` = move focus by spatial direction
	- `Alt+Shift+Arrow` = resize the focused edge/panel
- panel visibility toggles with:
	- `Ctrl+M` = toggle left shell panel
	- existing right/bottom panel toggles preserved

This means Slice A is no longer just a concept; a usable shell/layout/input scaffold already exists and is being refined in-game.

## 8.1 What to keep from current prototype

- virtual scrolling work
- terminal command bridge
- action registry momentum
- plain-screen rendering experiments
- basic paneling experiments
- session-backed shell context rendering
- selected-set model instead of single-target-only state
- directional focus navigation as a first-class layout concern
- canonical action ids shared by keybinds, terminal input, and future command palette routing

## 8.2 What to stop coupling to

- `AbstractContainerScreen` identity
- manager-only ontology
- slot-grid-as-root-of-all-things thinking
- hardcoded one-off panel rectangles

## 8.3 MVP for V3

### Slice A: Sovereign IDE shell

- plain `Screen`
- open via keybind and manager interaction
- session bootstrap for target block
- dock layout reducer
- focus manager
- initial shell context object
- hotkey to toggle/open the IDE

Status:

- substantially underway
- plain `Screen`, keybind open/toggle, dock reducer, focus manager, shell context object, and playground hotkeys now exist
- still missing the more authoritative bootstrap/bind path for arbitrary target inspection

### Slice B: Arbitrary block inspector

- raycast target acquisition
- bootstrap packet for capability snapshot
- inspector panel with per-face capability tree
- item/fluid/energy summaries

### Slice C: Editor + terminal core

- editor panel
- terminal panel
- action palette panel
- `IdeResult` plumbing
- action registry with resource-location ids
- shell variable exposure for player/look/hit context

Status:

- partially underway
- terminal panel exists as an action shell surface
- registry with resource-location-style action ids exists
- shell context already exposes player/look/hit/dimension/focused-target summary in the playground
- typed `IdeResult` plumbing and a true editor surface are still pending

### Slice D: Tabs and groups

- tabbed document groups
- detach/reattach to floating
- MRU focus history
- document source/buffer state model

### Slice E: Canvas seed

- pan/zoom surface
- box creation
- arrow connectors
- pin inspector result to canvas

### Slice F: Input system seed

- canonical keybinding string representation
- context-sensitive hotkey dispatch
- initial layout/meta modifier decisions
- focused-target scaling actions

---

## 9. Suggested Package/Module Direction

Illustrative only, but this separation would age well:

- `client.ide.session`
- `client.ide.action`
- `client.ide.layout`
- `client.ide.layout.dock`
- `client.ide.layout.float`
- `client.ide.canvas`
- `client.ide.panel`
- `client.ide.view`
- `client.ide.result`
- `client.ide.inspect`
- `client.ide.query`
- `client.ide.document`
- `common.net.ide`

---

## 10. Immediate Next Decisions

These need resolution before heavy implementation.

### 10.1 Is the IDE always target-bound?

Recommended answer:

- the IDE session may be unbound
- individual tabs/views may be target-bound
- binding can be changed at runtime

### 10.2 Is inventory interaction part of the first-class IDE?

Recommended answer:

- yes, but via explicit inspector/view actions
- not via inheriting container screen behavior

### 10.3 Which layout algorithm ships first?

Recommended answer:

- first: ordered edge carving dock reducer
- second: master layout
- third: canvas scene

### 10.4 Do we support both dock and canvas immediately?

Recommended answer:

- architect for both now
- ship dock first
- seed canvas early with pinned notes/inspectors

### 10.5 What is the first layout/meta modifier?

Recommended answer:

- design around `Alt` initially
- keep it configurable
- reserve it for spatial/layout manipulation semantics

### 10.6 Are commands singular or set-oriented?

Recommended answer:

- actions may operate on a focused target and/or a selected set
- APIs should default to set-capable data models

### 10.7 What is the canonical document model?

Recommended answer:

- separate source, buffer, and view
- support live-read and detached-edit states explicitly

---

## 11. Proposed Vocabulary We Should Standardize On

Use these terms consistently:

- **IDE session**: one open instance of the IDE
- **target**: thing being inspected or edited
- **view**: a rendering of a target
- **panel**: a placed UI host for a view
- **group**: tabbed collection in one place
- **dockspace**: traditional tiled work area
- **canvas**: infinite spatial scene
- **tool**: transient floating surface
- **action**: invokable intent
- **result**: typed output from an action/query/command
- **binding**: association between a view and a world target/document
- **shell context**: the ambient bindings and variables available to commands/actions
- **source**: authoritative origin/destination of a document
- **buffer**: mutable in-memory content
- **focused target**: the primary current element
- **selected set**: the current possibly-many target collection

Avoid overloading "window" unless we intentionally mean floating top-level UI.

---

## 12. Initial Implementation Order

1. add the hotkey to open/toggle the IDE
2. open a sovereign plain `Screen` instead of relying on container-screen identity
3. create a playground area for Hyprland-inspired layout primitives
4. define ontology interfaces: session, action, target, view, panel, result
5. define shell context and binding precedence model
6. implement session bootstrap packet for arbitrary target inspection
7. implement dock layout abstractions (`Area`, target, layout result)
8. implement inspector panel for capability snapshots
9. unify terminal/actions/results around resource-location action ids
10. add hotkey manager seed and canonical keybinding strings
11. integrate editor as a view with source/buffer separation
12. add groups/tabs, focus history, and undo units
13. add pinned floating tools
14. add canvas scene prototype

Progress against this order:

- done: 1, 2, 3
- substantially started: 4, 5, 7, 9, 10
- not started in earnest yet: 6, 8, 11, 12, 13, 14

More concretely, the prototype now has:

- hotkey-driven open/toggle
- sovereign screen identity
- dock playground with retained panel state
- session/action/target scaffolding
- shell-context capture and selected-set semantics
- canonical action ids with terminal dispatch
- context-sensitive navigation and resizing controls

---

## 13. Bottom Line

Hyprland suggests that we should be rigorous about layout abstractions, target identity, mode separation, and command routing.

Excalidraw suggests that we should preserve direct manipulation and spatial thinking, not reduce everything to docks and trees.

The synthesis for SFM is:

> Build a sovereign in-game IDE with abstract targets, typed results, action-driven behavior, a docked workspace for traditional editing, and a canvas workspace for thinking and inspection.

That is a much stronger direction than continuing to stretch `AbstractContainerScreen` past its natural limits.
