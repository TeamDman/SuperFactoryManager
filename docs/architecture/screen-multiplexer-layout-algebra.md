# Screen multiplexer layout algebra

Status: Track 1 design plus first Linear-only implementation slice
Initial branch checkpoint: `e7622e9524b1e5adf8dfddd4a10c6700fd8cea0d`
Reference checkout inspected read-only: `G:\Programming\Repos\egui_tiles`
at the supplied commit `f86273ba8ff9f44a9817067abbf977ba5cdcb9fa`

## Recommendation

Replace the experimental multiplexer’s fixed pair of panels with a persistent
layout model whose leaves and containers have stable identities:

```text
LayoutNode =
    Panel(panelInstanceId)
  | Linear(axis, orderedChildren, tracks)
  | Stack(orderedChildren, activeChild, selectorPresentation)
  | Grid(rows, columns, cells)
```

`Linear` is n-ary. `Stack` is the semantic operation tentatively called
`Flip`: its children share one content area and exactly one is active. Tabs are
a selector presentation generated for a `Stack`, not children in the content
tree. `Grid` is first-class because linked row and column constraints cannot be
faithfully represented by unrelated nested splits.

Virtual desktops use the same selected-child idea, but should be represented by
a top-level `WorkspaceSet` containing independently rooted layout trees rather
than pretending a workspace is an ordinary draggable panel. This preserves a
useful domain and persistence boundary while allowing the selector/focus logic
to be shared with `Stack`.

The current `SFMScreenPanel` remains the correct initial content boundary.
Minecraft `Screen` remains an outer host or an explicitly audited adapter; an
arbitrary `Screen` is not automatically a panel.

## Implemented first slice

The current branch now replaces the fixed two-slot calculation with stable
`SFMWorkspacePanelId` leaves and normalized n-ary horizontal/vertical Linear
nodes. Tracks implement positive shares plus per-child minimum GUI pixels.
Insertion preserves the source region's share: splitting the right half of a
two-panel layout creates shares equivalent to `[1, 0.5, 0.5]`, not three equal
panels. Removal collapses singleton containers and preserves focus by stable id,
falling to the adjacent traversal entry only when the focused panel is removed.

Embedded panels receive a narrow `SFMWorkspacePanelContext`. Its typed intents
are `Close`, `OpenToSide(side, panel)`, and `OpenAsTab(panel)`. An unhosted
context returns `UNAVAILABLE` for every request. The Linear-only multiplexer
applies close and side insertion; it returns `UNSUPPORTED` for `OpenAsTab`
until the proposed Stack node exists. Panels never need to call Minecraft's
global `setScreen` to express these operations.

This slice intentionally does not implement Stack, Grid, persistence, divider
dragging/linking, drag/drop, or arbitrary Screen embedding. Those remain design
work below rather than implicit behavior in the Linear model.

## Findings from `egui_tiles`

The checkout provides a useful structural reference, not a Java API to copy.

- A `Tree` stores one root id and a `Tiles` arena. Each `Tile` is either a
  generic `Pane` leaf or a `Container`; `TileId` is separate from pane data.
- Containers are `Tabs`, n-ary horizontal/vertical `Linear`, or `Grid`.
- A linear container stores ordered child ids and per-child `Shares`. Invisible
  children retain both their order and their share.
- A grid stores cells, column shares, and row shares. Moving one column divider
  affects every row and moving one row divider affects every column.
- `Tabs` stores ordered children and one active child. Only the active child is
  laid out and rendered.
- Visibility is orthogonal to containment. Invisible tiles remain in the tree;
  active traversal excludes invisible tiles and inactive tabs recursively.
- Drag/drop proposes typed insertion points for tab order, linear positions,
  and grid cells. Dropping a tab makes it active.
- A simplification pass can prune empty/single-child containers and join nested
  linears with the same axis. These rules are configurable through
  `SimplificationOptions`.
- The optional serde feature serializes the tree, tile ids, visibility,
  containers, shares, and pane data while excluding transient rectangles.
- `Behavior<Pane>` separates pane painting and policy/chrome choices from the
  stored layout. It owns tab titles and rendering, close policy, edit
  notifications, drag appearance, gap styling, simplification choices, and a
  global minimum tile size.
- The current allocation model is deliberately simple: shares plus one global
  minimum size. The source comments leave room for future per-tile min/max
  sizing, so SFM should not mistake the current limitation for the desired
  final constraint model.

### Vertical-tabs answer

This checkout does **not** support a vertical tab strip. `Tabs.layout` reserves
`tab_bar_height` at the top, `tab_bar_ui` takes the top rectangle, and the
selectors use a horizontal `ScrollArea`. “Vertical tab bar” is explicitly
listed under future improvements. The behavior hooks can restyle individual
tabs, but do not change this top-horizontal allocation.

## Algebra and terminology

An axis has two dimensions:

- **along-axis** is the dimension in which a container sequences and allocates
  its children: x/width for `HORIZONTAL`, y/height for `VERTICAL`;
- **across-axis** is the perpendicular dimension: y/height for `HORIZONTAL`,
  x/width for `VERTICAL`.

An initial `Linear` fills the available across-axis area for every child. Its
along-axis space is the parent extent minus container chrome and inter-child
gaps. Tracks should support these policies without storing Minecraft framebuffer
pixels:

```text
Flex(share, minimum, optionalMaximum)
Fixed(preferredLogicalSize, minimum, optionalMaximum)
Content(minimum, preferred, optionalMaximum)
```

Sizes are logical GUI units interpreted after Minecraft GUI scale, not physical
pixels. Fractions are a convenient input form for `Flex`; persisted positive
shares are better for n-ary editing because adding a child does not require
rewriting every fraction.

Allocation should:

1. subtract chrome and gaps;
2. establish each visible child’s minimum;
3. reserve clamped fixed/content preferences;
4. distribute the remainder by flex shares;
5. clamp maxima and redistribute any remainder; and
6. report a constrained/overflow result if the minima cannot fit, allowing the
   view policy to compact, scroll, or visibly degrade rather than create
   negative rectangles.

The first implementation only needs `Flex(share, minimum)`, but the serialized
shape should be versioned so fixed/content constraints can be added without
reinterpreting old numbers.

### Three vertical panels

```text
Vertical[A, B, C] shares [1, 1, 1]
```

allocates three equal heights, exposes two sibling dividers, and clearly means
“three peers.” By contrast:

```text
Vertical[A, Vertical[B, C]]
```

with default equal shares allocates A one half and B/C one quarter each. It
also means the B/C boundary is subordinate to the A-versus-rest boundary. To
make all three equal, the outer shares must be `[1, 2]` and the inner shares
`[1, 1]`. Same-axis nested linears should normally normalize to an n-ary linear
while preserving effective shares; nesting remains meaningful when a boundary
has distinct constraints, chrome, identity, or persistence intent.

### Tabs A/B/C over content D/E/F

The semantic tree is:

```text
Stack(
  children = [Panel(D), Panel(E), Panel(F)],
  active = Panel(D),
  selector = Tabs(edge = TOP, labels = [A, B, C])
)
```

A/B/C are selector views for D/E/F, not layout children competing with the
content for focus or drag/drop. E and F remain attached but hidden. Selecting B
changes `active` to E; it does not rebuild the tree.

`Stack`/`Flip` is therefore a first-class container equivalent to the semantic
part of tabs without assuming tab placement. `Tabs`, `CompactDropdown`,
`Hidden`, or a future left/right vertical strip are selector presentations.
The chosen presentation reserves chrome around the shared content rectangle.
Tab chrome should not normally be a layout child: doing so gives a selector an
unwanted panel identity, makes it draggable as content, complicates focus and
normalization, and can recursively acquire its own tabs. A genuinely independent
navigator is still allowed as an ordinary sibling panel, but that is a different
composition.

### Four corners and one central intersection

```text
Grid(
  rows = [1, 1],
  columns = [1, 1],
  cells = [[A, B],
           [C, D]]
)
```

is required when the vertical divider must be shared by both rows and the
horizontal divider by both columns. At their intersection, a two-axis grip can
update both the adjacent row and column shares during one diagonal drag, thereby
resizing all four areas. A nested form such as
`Vertical[Horizontal[A,B], Horizontal[C,D]]` has two independent vertical
dividers; keeping them aligned would require an external linked-divider
constraint, which is a disguised grid. Binary splits remain useful for
asymmetric trees, but they are not a substitute for grid constraints.

### Structural arity is not divider topology

The need to coordinate aligned dividers does not remove the value of an n-ary
`Linear`. Structural arity answers “are A, B, and C peers in one ordered
allocation?” Divider identity answers “which resize variables or constraints
are shared across allocations?” They are independent questions.

Replacing `Vertical[A,B,C]` with binary `Split(A, Split(B,C))` does not avoid
cross-tree communication. It merely bakes one grouping and one allocation
order into the tree, makes equal thirds indirect, and makes insertion/reordering
produce avoidable wrapper nodes. An n-ary linear gives all peer children one
allocation pass, one ordered set of adjacent dividers, and one share vector.
Binary split nodes remain appropriate when the hierarchy itself is meaningful,
for example “top tools versus a lower workspace whose internal division is
independent.”

Divider topology should be explicit where it crosses container boundaries:

- an ordinary linear divider is local to one `Linear` and can have a stable
  `DividerId` for drag capture, command targeting, and persistence;
- an explicit linked-divider constraint may bind two or more `DividerId`s when
  a small non-grid composition intentionally keeps distant boundaries aligned;
  links are constraint records, not containers discovering neighbors and
  messaging them during drag; and
- when neighboring linears form rectangular rows/columns whose aligned
  dividers are expected to stay linked, normalization should recognize or offer
  conversion to `Grid` rather than maintain an ad-hoc communication graph.

For the four-corner case, `Grid` owns one column-divider identity and one
row-divider identity directly. The central grip updates those two variables;
there is no communication between two horizontal linears. A general
`DividerLink` facility may still be useful later for synchronized editors or
non-rectangular layouts, but it should not be required to express a rectangle.
Automatic linear-to-grid normalization must be conservative: convert only when
row/column cardinality, order, alignment intent, spans, and constraints are
unambiguous. Merely touching edges on one rendered frame is insufficient,
because transient geometric alignment does not establish shared semantic
identity.

### Virtual workspaces

```text
WorkspaceSet(
  order = [work, debug, reference],
  active = work,
  roots = {
    work: Horizontal[...],
    debug: Grid[...],
    reference: Stack[...]
  }
)
```

Only the active workspace tree is allocated, rendered, narrated, and offered
input/drop targets. Switching desktops preserves every tree’s active panel,
shares, and internal focus path. A desktop selector may use number shortcuts,
tabs, or a command palette, but desktops are not ordinary `Stack` children that
can accidentally be dropped into a panel tab group.

## Identity, ownership, and persistence

Use distinct durable ids:

- `WorkspaceId` identifies a virtual desktop;
- `LayoutNodeId` identifies a structural node;
- `PanelInstanceId` identifies one open content instance; and
- registered `ScreenTypeId` identifies the factory capable of reconstructing a
  panel from versioned arguments/state.

Ids must not derive from Java object identity or a child’s current path. A node
has exactly one structural parent; a panel instance has exactly one owning leaf.
Moving changes parentage without changing panel identity. Copying creates a new
panel instance explicitly. Runtime rectangle caches, drag state, hover state,
Minecraft objects, and decoded file handles are transient.

Persist a versioned workspace document containing ids, roots, ordered children,
tracks/shares, active selections, visibility, focus paths, and typed panel
descriptors. Do not serialize a Minecraft `Screen`. On load, an unknown screen
type becomes a recoverable placeholder retaining the original type id and
payload. Persistence should be transactional and reject cycles, multiple
parents, dangling roots, invalid active ids, and non-finite/negative shares.

## Lifecycle, activity, focus, and input

Panel lifecycle should distinguish ownership from visibility:

```text
constructed -> attached -> visible(bounds) <-> hidden -> detached -> closed
```

Changing tabs or desktops sends a visibility transition; it does not call
`closed`. Hidden panels receive no rendering, pointer/keyboard input, tooltips,
drop events, or narration. Default ticking is active-only. A later explicit
background-work policy may allow bounded model work, but hidden UI widgets must
not consume Minecraft input or rely on stale rectangles. Resizing notifies only
newly allocated visible panels, with an initial notification when a hidden panel
becomes visible.

Focus is a path, not just an integer:

```text
workspace -> active layout branches -> panel instance -> panel-local widget
```

Selecting a stack child or desktop restores its last valid descendant focus.
Removing/hiding the focused node chooses a deterministic nearby visible leaf
(next sibling, previous sibling, then ancestor fallback). Ctrl+number desktop
selection is handled before panel keys; panel-number focus shortcuts operate
within the active workspace and must not steal text input when their modifier is
absent.

Narration describes only the active workspace and visible branch. Structural
changes announce concise results such as “Reference opened to the right” or
“Workspace 2 of 3”; then the focused panel supplies its own narration. Hidden
panels are never included in recurring narration.

Pointer/drop routing follows the allocated tree from chrome to the deepest
visible rectangle. Divider and selector chrome gets first refusal, then the
visible panel. A pointer capture established by a drag continues to its owner
until release/cancellation even if it crosses rectangles. Native file drops are
routed to the deepest willing visible target under the drop point, or the
focused panel if the platform supplies no meaningful point; hidden panels never
receive them.

## Tree edits and normalization

Every move/removal is one validated transaction followed by normalization:

1. remove dangling references and repair an invalid active child by deterministic
   visible order;
2. remove empty containers, except an intentionally retained empty workspace
   root which renders an empty-state target;
3. collapse single-child linears and stacks unless retained chrome, constraints,
   or a durable command target makes that container semantically significant;
4. flatten nested linears with the same axis when no semantic boundary is lost,
   multiplying/renormalizing shares so effective sizes remain unchanged;
5. retain stacks and grids as distinct container kinds; compact grid holes only
   under an explicit grid policy; and
6. preserve panel ids while emitting node-id replacements/tombstones for stale
   commands, selection history, and diagnostics.

Normalization must be deterministic and independently testable. It should not
run arbitrary panel code.

## Explicit screen and workspace operations

`SFMScreenChangeHelpers.setOrPushScreen` currently chooses between two very
different operations based only on whether `Minecraft.screen` is null. The
helpers should expose intent explicitly:

### `clobberGlobalScreen(screen)`

Call Minecraft `setScreen`. In Forge 1.19.2, the patch first clears every GUI
layer, calls `removed`/closing hooks for replaced screens, installs the new
screen, and initializes it. This is a destructive navigation boundary and must
not be used as a synonym for “open something.” If the current screen is a
multiplexer, this exits the workspace unless the target is that same host.

### `pushModalScreen(screen)` / `popModalScreen()`

Use Forge’s GUI-layer stack. In 1.19.2 `pushGuiLayer` retains the current screen
in a private stack, makes the new full-screen layer current, initializes and
narrates it; rendering draws retained layers back-to-front while only the top
screen receives normal input. `popGuiLayer` removes the top and restores the
previous screen without rebuilding its content. `Screen.onClose` is patched to
pop. This is a stack of full Minecraft `Screen`s, not the same structure as SFM
panels, tabs, virtual desktops, or Minecraft’s separate loading `Overlay` slot.

When a multiplexer is current, a confirmation, palette, or other modal remains
a full-screen Forge layer above it. It is not inserted into the layout tree.
An action invoked from that modal must retain an explicit originating
`WorkspaceHost` because Forge’s private layer stack cannot be reliably queried
to discover the multiplexer underneath.

### `enterWorkspace(initialPanel)` / `openWorkspace(workspaceId)`

- If the current host is already an SFM multiplexer, focus the requested
  workspace/panel and do not call `setScreen` or nest another multiplexer.
- If a modal is current over a known multiplexer, close/pop that modal according
  to its continuation policy, then mutate/focus the existing host.
- Otherwise create one multiplexer, retain an explicit return destination, and
  install it at the global boundary. Whether the previous arbitrary screen is
  parked, adapted, or replaced remains a product decision; it must not be
  silently treated as an embeddable panel.

### `openPanelToSide(factoryRequest, side)`

- In a multiplexer, create one panel instance and insert it adjacent to the
  focused layout node. If the parent is a same-axis `Linear`, insert into that
  n-ary container; otherwise wrap the focused node and new leaf in a new
  `Linear`. Focus the new panel.
- Through a modal over a multiplexer, pop/complete the modal then apply the same
  host intent.
- Outside a multiplexer, enter a workspace and perform the insertion against
  its initial/placeholder leaf.

`side` maps left/right to horizontal and above/below to vertical; it is not a
pixel coordinate.

### `openPanelAsTab(factoryRequest)`

- If focus is already within the intended `Stack`, append/select the new leaf.
- Otherwise replace the focused node with a `Stack[old, new]`, preserving the
  old node identity below the new container and selecting the new leaf.
- Outside a multiplexer, enter a workspace first.

### `movePanel`, `focusPanel`, and `focusWorkspace`

These are model transactions addressed by stable ids. A move removes then
inserts the same panel leaf and normalizes both old and new ancestry. Focus
operations activate every containing stack/workspace along the target path and
reject missing or non-visible targets with structured feedback. They never call
Minecraft `setScreen` while the existing workspace host is valid.

### Registered “open screen” actions

The Track 1 registry should be understood as typed **panel/content factories**
despite its initial screen-oriented name. A registered command yields a panel
request plus a placement intent. A factory that truly requires a full Minecraft
`Screen` must declare `CLOBBER` or `MODAL` hosting explicitly and cannot be used
with `to side`/`as tab` unless an audited adapter exists.

## Relationship to Track 3

Track 3 already separates file data (`SFMFileExplorerSource` and model) from a
full-screen host, emits a typed `OpenIntent`, and computes pure responsive
geometry from an arbitrary x/y/width/height. Its tested thresholds are a
comfortable layout, a compact panel-sized layout, and a visible degraded state
below a documented 180x120 logical-unit minimum.

Integration should extract an explorer content/panel host that receives the
multiplexer’s allocated rectangle and delegates to the existing pure layout and
model. The multiplexer consumes the panel’s minimum/preferred constraints; it
does not duplicate explorer layout rules. The explorer’s `previousScreen` and
global `onClose` belong only to its standalone host. Its `OpenIntent` should be
translated by the workspace host into an explicit `openPanelToSide` or
`openPanelAsTab` intent rather than calling Minecraft navigation directly.

## Cross-version boundary

Keep the algebra, normalization, persistence, allocation, hit-testing, and host
intents free of Minecraft/Forge types. A thin `SFMScreenMultiplexer` adapter owns
the version-dependent `Screen` lifecycle and converts the current version’s
render/input objects into SFM logical rectangles and events. Forge/NeoForge
push/pop behavior and transitions such as `PoseStack` to `GuiGraphics` belong
behind methods annotated with `@MCVersionDependentBehaviour`; they must not fork
the layout model across Minecraft branches.

## Unresolved decisions

- Name the first-class selection container `Stack` (conventional) or `Flip`
  (visually descriptive). Recommendation: model name `Stack`, allow “flip” as a
  user action/command.
- Decide whether a first workspace parks the prior screen as the current proof
  does, starts from an empty workspace, or supports a small allowlist of audited
  full-screen adapters.
- Choose persistence scope and location: per client profile, per world/server,
  or named shareable workspace documents.
- Define which panel models may perform background work while hidden and how
  budgets/cancellation are enforced.
- Decide whether the first grid supports only rectangular dense cells or also
  spans and persistent holes. Dense 2x2 without spans is sufficient for the
  four-corner proof.
- Decide which container ids are durable command targets strongly enough to
  prevent single-child normalization.
- Specify conflict behavior when a requested fixed/content minimum cannot fit:
  compaction, scrolling, temporary overflow, or refusing the split.

## Suggested next proof

Implement and pure-test only the model before replacing rendering:

1. n-ary equal-share `Linear` allocation and same-axis normalization;
2. `Stack` active/hidden traversal with a top-horizontal selector view;
3. dense 2x2 `Grid` with linked row/column shares and a central two-axis grip;
4. stable-id move/remove transactions and versioned serialization round trips;
5. host-intent dispatch proving that opening beside an active multiplexer mutates
   it without nesting or calling `setScreen`; and
6. an adapter of Track 3’s pure explorer layout as the first non-test panel.

Drag/drop rearrangement, vertical tab chrome, spans, named persistence profiles,
and arbitrary Minecraft `Screen` adapters should follow those invariants rather
than shape the first implementation.
