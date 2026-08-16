Towards the idea of getting a new release for the mod published, I want to augment my review process. Historically, I have leveraged IntelliJ IDEA for complicated cross-branch diffing to identify changes between the MC version code, and I have used IntelliJ and VSCode for reviewing uncommitted changes. However, another modality of change review is necessary and we must introduce it ourselves.

Sometimes I try and do code review by staging with git the parts that I have approved so the unstaged changes are the unreviewed parts but this runs into concurrency issues where sometimes we want to keep iterating in a way that we are committing as we are working which means that committed code doesn't equal reviewed code. 
There's 3(4?) copies of the code
- uncommitted code
- committed code
- reviewed code
but really, code has aspects
- committed
- reviewed
- approved
our sfm audit command helps differentiate approved from unapproved code
unapproved code is like using `@EventBusSubscriber` instead of `@SFMSubscribeEvent`
code can be committed but not yet have had eyes-on by myself.

review is effectively done at the substring/AST level
line by line diff algorithms are popular
AST diff operations are more powerful but not popular
Code is best viewed as code rather than trying to present some generic tree-view over the AST
but that may be useful for some of the refactoring operations I want to be able to perform.

we have arborium in rust, javaparser in java, but my understanding is that our rust AST stuff is more featureful with regard to type inference than the java stuff is.

We have the `mount` feature branch which aims to address desires to be able to edit in-game disks with vscode, seamlessly passing back and forth.

We have the need to develop some sophisticated tooling for code review.
We have the in-game text editor whose functionality we own and seek to improve.
Diff viewing is not yet an in-game feature.
The in-game text editor, its latest "draw" iteration is confused. The naming is draw which inspires canvas notations but really it is nothing more than a thing that takes an initial text document and user inputs to produce a new text document. There is no room for graphical representations in a text manipulator.

Issues regarding "how do we present the .g4 file" and the picture-in-picture stuff we have tried, is really begging us to create a better windowing solution.

We will need to create our own in-game window manager for us to be able to use our text editor widget to create multipanel layouts where we can observe the game code and stuff.

If I want to be viewing my game code in game, then we will need a file tree to browse the files.
Paneling
Repositioning panels
Snap behaviours

We have egui-tiles as a reference for how we can do panels.

Mitchell Hashimoto on x.com has some semi-recent tweets about his own that we can dig into if we want visual inspiration, but his source code is not available.

We are effectively asking how we can display multiple Minecraft Screen classes on the screen at once, gracefully pumping inputs to the "focused" one and such.
Minecraft has the Minecraft.getInstance().screen or whatever for the current screen, but forge/neoforge has overlays.
Our multi-screen stuff is probably best made as its own SFMScreenMultiplexer class that we can manage, giving us our own surface area to do things like hyprland or whatever. A window manager.

Ctrl+1 for screen 1, ctrl+2 for screen 2, some kind of tiling.

We have this command palette+console thing we are making.
We have text editors
We need a file explorer that supports the native stuff
We have the mount feature we want to gracefully join in

It is unclear if the lwjgl or whatever stuff we have (glfw?) lets us add file drag-and-drop support to the minecraft window, that would be cool.
Please create a docs/tasks doc and begin by including this message verbatim. Then we can plan next steps.

## Follow-up panel-composition note (verbatim, 2026-07-20)

how are our panels composed? if I want 3 panels split vertically, is that VerticalSplit[Left, VerticalSplit[Left, Right]] or does our screen compose the layout more complicatedly than nesting split instructions?

Since we have our "push screen" logic centralized in our helper, we could possibly clarify
- open screen (clobbering)
- push screen (there's a stack already is my understanding, overlays are a different thing entirely iirc)
- open multiplexer
where "open screen" when the current screen is SFM multiplexer screen would have a different behaviour; any open/push operation creates a new panel in the multiplexer where the multiplexer composes units like "tab list" and "split panel" each with an along-axis and across-axis

similar to ratatui, we divide the area of the screen. a horizontal list of tabs would be a TabList along the horizontal axis.
A vertical list of tabs would be a TabList along the vertical axis as primary.
the question becomes that of area allocation

we can imagine that "panel" is our unit for the multiplexing. A panel may be "on another desktop" and therefore not visible at all. It may be a tab in a panel of tabs

we can have the subagent explore more

G:\Programming\Repos\egui_tiles
what designs does that tell us we may want to adopt?

Does egui_tiles support a vertical tab list?

The area for the tabs vs the area for the tab body, that's just a Split along either the horizontal or vertical axis
The area for the tabs is kinda just Split[Head, Split[head, tail...]]
if we design our interface to gracefully be composed of Splits
then we can let the user reshape the interface by saying panels can be rearranged and resized, changing the aspects of any Split's needed to accomplish. If we consider the intersection of a 4-corners layout, the middle point resizes all 4 panels, which in a nested split layout involves changing the split percentage/units at multivarious levels of the split hierarchy.

If a "tab" is a thing that presents and occupies the full panel, then that is simply a button that focuses the other panel. How do we know when a panel is "behind" another if tabs are not a coherent unit but are instead disjoint buttons? If we have a

ABC
D

layout where ABC take the top 100px and D takes the rest how do we know what panel the content area of D is

so in addition to the Split we have the Flip
in
ABC
D

there may be panels ABCDEF
where DEF all occupy a Flip[D, Flip[E,F]] with D being what is shown, but when E or F get focused that makes the flip show that focused panel instead.
If we have a concept of virtual desktop-like things, then does that mean we have a top-level Flip[Workspace1,Flip[Workspace2, Workspace3]] so focusing workspace2 makes that occupy the full screen. This message should be persisted verbatim in the plan near the others.

---

# In-game code review workspace and window manager plan

**Plan status:** Active; multiplexer/explorer foundation integrated, review slice planning in progress, and Track 1b now owns pointer-driven divider resizing
**Primary planning root:** `D:\Repos\Minecraft\SFM\repos2\1.19.2`  
**Related reference worktrees:** `feat/1.19.2/draw`, `feat/1.19.2/mount`  
**Last updated:** 2026-08-16
**Intent audit:** Passed and post-compaction re-audited 2026-08-16 for the divider-border/intersection/cursor extension while preserving the prior layout-algebra constraints and the user's verbatim VS Code-like resize expectations

## How to update this plan

- `[ ]` Not started
- `[~]` In progress
- `[x]` Complete
- `[!]` Blocked

Preserve the original request above verbatim. Record decisions, implementation
evidence, commit ids, validation output, and intentional exclusions beneath the
work item they affect. Work from the oldest supported Minecraft version and use
the repository propagation workflow for changes intended for core branches.

## Planning checkpoint

No architecture or release-scope decision is accepted merely by creating this
document. The next planning pass should separate and relate these concerns:

- a review-state model independent of Git's index, worktree, and commit graph;
- source presentation and substring, line, and AST-aware review operations;
- the boundary between audit approval and human eyes-on review;
- a reusable in-game window manager, focus/input multiplexer, and tiled layout;
- editor, diff viewer, console, command palette, file explorer, and reference
  document panels;
- integration with the mount workflow and native filesystem affordances; and
- the smallest coherent release slice, its persistence model, tests, and
  cross-version propagation strategy.

## Authoritative user guidance ledger — 2026-08-16 divider interaction extension

| ID | Active guidance | Required plan consequence | Superseded by |
| --- | --- | --- | --- |
| WRESIZE-1 | A user should be able to resize panels by dragging the border/divider between them, with behavior familiar from VS Code. | Track 1b adds explicit divider identities/hit regions, continuous share updates with minimum constraints, pointer capture/cancel, and live resizing. | — |
| WRESIZE-2 | Hovering a resizable divider must change the OS cursor to communicate horizontal or vertical resizing before the drag begins. | Track 1b adds a version-adapted owned GLFW standard-cursor seam with horizontal/vertical shapes, deterministic reset, and no per-frame cursor allocation. | — |
| WRESIZE-3 | At the intersection of three or more panels/dividers, one drag should resize both axes and use a crosshair/four-direction resize cursor. | Track 1b hit-tests a set of orthogonal divider identities at one point and captures both deltas; a true linked row/column grid still requires explicit Grid/linked-divider semantics rather than coincidence. | — |
| WRESIZE-4 | Resizing must preserve the layout model's minimums, proportions, panel identities, stacks, focus, and content state. | Track 1b mutates track shares through one constrained layout operation and proves nested Linear/Stack and three/four-pane cases without rebuilding panels. | — |
| WRESIZE-5 | Mouse behavior should remain action/automation addressable instead of becoming pointer-only hidden state. | Track 1b exposes divider descriptions and an equivalent registered resize intent carrying explicit divider selector/delta or resulting shares; existing keyboard `panel/resize/...` remains semantically consistent. | — |
| WRESIZE-6 | `G:\Programming\Repos\vscode` may be used as a local behavior reference. | Track 1b records VS Code's sash/split/grid files as evidence for cursor/hit/linked-resize behavior, but copies neither TypeScript/DOM code nor a dependency. | — |

## Guidance traceability — 2026-08-16 extension

| Guidance | Plan coverage | Evidence when complete |
| --- | --- | --- |
| WRESIZE-1, WRESIZE-2 | Track 1b geometry/cursor/host lanes | Divider hit tests, horizontal/vertical cursor lifecycle, continuous drag/share/minimum tests, cancel/focus/lifecycle proof, and live pointer artifacts |
| WRESIZE-3, WRESIZE-4 | Track 1b pure layout lane | T-junction and four-pane intersection fixtures, orthogonal delta application, explicit linkage rules, stable panel/stack/focus identities, and responsive resize screenshots |
| WRESIZE-5 | Track 1b action/automation contract | Action-registry/completion tests, explicit divider/result identity, keyboard/pointer semantic parity, and machine-readable before/during/after shares/bounds |
| WRESIZE-6 | Track 1b source evidence | References to local VS Code `sash.ts`, `splitview.ts`, and `gridview.ts`, with an original SFM implementation and license/dependency non-adoption note |

## Intent audit evidence — 2026-08-16 divider interaction extension

- **Pass 1 — extraction:** Preserved border dragging, VS Code-like behavior,
  horizontal/vertical cursor changes, intersections of three or more panels,
  simultaneous two-axis resize, and the crosshair/four-direction visual cue as
  distinct WRESIZE requirements. Kept this separate from the earlier Alt+drag
  relocation and explorer-node drag proposals.
- **Pass 2 — traceability:** Mapped every id to Track 1b and inspected the
  current foundation. `SFMWorkspaceLayout` already has normalized n-ary Linear
  shares/minimums and directional five-percent resize; `SFMScreenMultiplexer`
  routes mouse events only into panels and exposes no divider hover/drag; the
  earlier algebra deliberately leaves Grid/divider dragging/linking deferred.
  Local VS Code sources provide `Sash`, SplitView constraints, orthogonal
  sashes, linked sashes, and GridView linked width/height nodes as behavioral
  evidence.
- **Pass 3 — adversarial omission:** Checked that a coincident line does not
  silently become permanently linked, a T-junction can still capture both
  actual dividers under the pointer, minimums cannot produce negative bounds,
  resize does not recreate panel content or alter stack selection, the OS
  cursor cannot remain stuck after focus/screen loss, pointer capture does not
  leak clicks into child panels, and automation can reproduce the result
  without screen-coordinate `SendInput`.
- **Fresh-agent resumption check:** A new agent can start Track 1b from the
  existing Linear/share/minimum and keyboard-resize foundation, freeze
  DividerId/geometry/delta/cursor interfaces, run the three disjoint lanes,
  and integrate one two-/three-/four-pane puppet without beginning relocation,
  explorer drag, virtual workspaces, or review-surface work.
- **Known source limitation:** None. Current SFM layout/host source, the earlier
  layout-algebra checkpoint, the complete user requirement, and local VS Code
  source were available. Exact GLFW cursor shape availability across later
  Minecraft/LWJGL versions remains an adapter concern with crosshair fallback.

The versioned snapshot, episode, raw-event, action-trace, replay, calculator
environment, and future amalgamation contracts are owned by the separate
[snapshot episodes and deterministic action environments plan](snapshot%20episodes%20and%20deterministic%20action%20environments%20plan.md).
This workspace is a consumer of those models rather than their only host.

## User-testing checkpoint — 2026-08-02

The release plan's P-1/P-2/P-3 items are the executable work breakdown. This
document owns the panel-state and composition laws those items consume.

### Superseded review surface

`sfm:review/open_bundle` and the managed bundle-review implementation are to be
deleted, including the Rust producer and dedicated repository-review workspace.
They were a completed experiment that proved real repository bytes could reach
the comment kernel, but the managed inbox and fixed bundle workspace are not
the desired product. Preserve the independent review-session/comment model and
record the synchronous completion incident as a palette-wide audit requirement.

`sfm:developer/open_source_review` and
`sfm:developer/open_comment_review` are temporary migration witnesses only.
Their fixture shells and action ids must disappear after the panel/explorer
review story has equivalent passing puppets. The review-session/comment kernel,
stores, selectors, persistence, editing components, styles, and useful visual
behavior survive the migration.

**Migration completed — 2026-08-02:** The legacy review action ids, ledger and
comment workspaces, and fixture-only puppets are deleted. The shared review
explorer projections now provide changes, comments, and hashtags through the
panel workspace; the review-session/comment kernel remains in place.

### Canonical action hierarchy

Action ids use slash-separated concepts. The panel family is
`panel/open[/<direction>]`, `panel/close`,
`panel/move/<direction>`, `panel/scale/{set|increase|decrease|clear}`,
`panel/rotate/content/{left|right}`, and
`panel/rotate/scale/{left|right}`. Do not introduce flattened forms such as
`rotate_content_right`. The unpublished `workspace/open_to_side` action is
replaced after its call sites and puppets migrate; it is not a permanent alias.

`sfm:panel/open sfm:terminal` is the sole terminal-opening action.
`sfm:terminal/open` is retired. `sfm:terminal/server/start` and
`sfm:terminal/server/connect` manage lifecycle only and never implicitly open
or replace a panel. The Java-local `sfm:repl/open` action remains separate.

Scene ids and scene arguments remain typed children of this action family. At
minimum the registry exposes `sfm:size_display`, `sfm:terminal`,
`sfm:explorer/changes`, `sfm:explorer/comments`, and
`sfm:explorer/comments/hashtags`. The command palette must distinguish a
missing scene or scene argument from an executable action and must not insert a
separator after reporting no candidates.

### Normative panel-state model

- A **slot** owns one visible layout region and an ordered stack of entries.
- A **panel entry** owns stable identity, scene/content state, optional GUI
  scale override, and provenance/role metadata.
- The **visible entry**, **focused slot**, and **focused child component** are
  independent state.
- Pushing an entry makes it visible. It moves slot focus only when the invoking
  operation explicitly requests focus.
- Moving an entry removes the visible entry from its source stack and pushes
  that same identity and state onto the neighboring destination stack. The
  destination slot is created when absent; an emptied source slot collapses.
  Moving never creates a second entry or aliases panel state.
- Content and scale rotations operate only across the currently visible entry
  in each slot. Hidden entries remain unchanged in their owning stacks.
- Explorer previews carry `explorer-preview(owner=<explorer-id>)`. Only a slot
  carrying that ownership is eligible for later previews from that explorer.
- Closing the visible entry reveals the next entry; closing the final entry
  collapses the slot and repairs focus deterministically.

### Normative keyboard traversal

Plain `Tab` is sent to the focused child. `Ctrl+number` focuses the numbered
visible slot without rotating its stack. `Ctrl+Tab` visits each panel entry in
visible slot order; entering another entry in the same slot makes that entry
visible while the slot stays focused. `Ctrl+Shift+Tab` is the exact inverse.
The required transition witness is:

```text
[>1, [2,3], 4]
[1, >[2,3], 4]
[1, >[3,2], 4]
[1, [3,2], >4]
```

Terminal/editor-specific Tab behavior therefore remains available without
removing keyboard access to the surrounding workspace.

### Normative explorer-open behavior

For a selected file, Space and `Ctrl+Enter` find the most recent preview slot
owned by that explorer; if no such slot exists, create one to the right. Each
open pushes a new typed preview entry into that slot—“clobber” means changing
the visible entry, not deleting the prior entry. Space keeps explorer focus;
`Ctrl+Enter` focuses the preview. A selected directory uses Space to
expand/collapse. Terminals and unrelated panels are never eligible preview
targets.

### Scale and rotation behavior

GUI scale is an optional per-entry view setting. In equal-sized slots, a
size-display entry at scale 2 must report more logical width/height than one at
scale 4. Content and scale are independently rotatable fields: rotating
content preserves region geometry and scale assignments, while rotating scale
preserves geometry and content. Binding both operations to one key composes the
transformations. Each rotation includes only the currently visible entry from
each slot; hidden entries remain unchanged in their owning stacks.

Slots with stacks show compact numbered boxes in the bottom-right corner and
identify the visible entry. Entries with scale overrides show `gui scale N`.
Automation exposes slot order, stack order, visible entry, focus, ownership,
dimensions, and effective scale as structured/text artifacts in addition to
screenshots.

### Review explorer composition

The change explorer is parameterized by independent before/after selectors and
projects `file → revision lane → before|after`. Its default lane set is every
maintained Minecraft-version worktree known to the SFM toolchain, ordered
oldest to newest; an optional lane filter can narrow the view. It unifies those
branches rather than opening one fixed two-pane diff. Both leaves remain
present for every participating file/lane; an absent added/deleted side is a
typed tombstone rather than a missing tree node. An unresolved selector keeps
its lane visible with a diagnostic placeholder while resolved lanes remain
usable. Comment and
hashtag explorers project the existing comment-session kernel as
`comment → file → region` and `hashtag → file → region`. Opening any leaf uses
the explorer-owned preview behavior above. Before/after leaves present one
immutable source revision with applicable comment styles; diff colors are
produced through `#removed`/`#added` comments rather than a special-purpose
diff panel.

## Current integrated baseline (2026-07-21)

Tracks 1 and 3 were integrated through
`feat/1.19.2/review-workspace` and merged into canonical `1.19.2` as
`10efa6326` (`Merge integrated file explorer workspace`). The baseline now
contains the n-ary multiplexer, typed screen-opening actions, composable
responsive file explorer, instance-directory action, read-only preview panel,
file-presentation styles, drop-to-replace behavior, focused tests, and
captioned puppets.

The canonical full Java suite and compilation passed; the two portable symlink
tests were skipped because Windows denied symlink creation. Java audit reported
zero warnings. The integrated
`sfm:title_screen_integrated_file_explorer` puppet passed and captured the
explorer/preview workspace. The old instructions below about creating and
merging the first integration branch are retained as coordination history;
future tracks branch from the current reviewed baseline and return through a
fresh explicitly named integration step when concurrent work requires it.

## Current execution order

The earlier “Open Review Workspace” first slice was completed as the historical
managed-bundle experiment recorded below and is no longer current direction.
The global comment/session plan now owns durable selectors, relocation,
approval, and colorization. Execute the numbered release-plan batches in order:

1. P-1 removes the bundle experiment, audits completion latency, establishes
   hierarchical panel actions, and corrects terminal presentation;
2. P-2 implements slot stacks, focus traversal, explorer-owned previews,
   per-entry scale, rotations, and observable badges; and
3. P-3 composes multi-lane changes and comment/hashtag explorers over the
   preserved comment-session kernel.

The historical track records below remain evidence about code already present;
they are not instructions to restore deleted bundle or `workspace/open_to_side`
surfaces.

## Parallel experiment tracks

The track names below are durable coordination handles. A track may investigate
and commit independently without implying that its result is accepted into the
release. Cross-track dependencies should be expressed as contracts and small
integration commits rather than by allowing multiple tracks to edit one
worktree concurrently.

### [x] Track 1 — Screen multiplexer and typed screen-opening actions

Build the smallest useful `SFMScreenMultiplexer` experiment and integrate it
with the existing client action registry, Brigadier dispatcher, and command
palette.

The proving workflow is:

1. the user presses Ctrl+K;
2. the user selects an **open screen to the side** action;
3. Brigadier resolves a registered screen type and that screen type's strongly
   typed arguments;
4. selecting `test_screen` with a string such as `test screen 1` creates a
   simple screen/panel that displays that string; and
5. the multiplexer places it beside the existing focused content and routes
   rendering and input to the appropriate child.

The experiment must answer whether the reusable unit is a complete Minecraft
`Screen`, a narrower SFM panel/content interface, or an adapter supporting both.
Do not assume that arbitrary screens can be embedded safely: lifecycle,
initialization, dimensions, narration, focus, dragging, tooltips, overlays,
pause behavior, and close behavior all need explicit ownership.

The proposed screen registry owns stable screen-type ids and typed factories.
It must mesh with the client action system rather than introduce a second
command parser. Because different screen types require different arguments,
the screen registration contract contributes its own Brigadier argument
subtree/factory instead of forcing every screen through one stringly typed
argument bag.

Initial experiment limits:

- one horizontal split and deterministic side placement;
- one focused child at a time;
- mouse focus plus Ctrl+1/Ctrl+2 focus selection;
- a test screen with one required display-string argument;
- no persisted layout, arbitrary nesting, resizing, or production-screen
  embedding until the lifecycle experiment succeeds; and
- no direct Forge/NeoForge event or registry APIs outside narrow SFM seams.

**Delegation started 2026-07-20:** Assigned to subagent
`track1_screen_multiplexer` in
`D:\Repos\Minecraft\SFM\worktrees\1.19.2-screen-multiplexer` on branch
`feat/1.19.2/screen-multiplexer`. The worktree was created and verified clean at
immutable baseline `246dddbc812644e3ca199e2bec85d8d152954b69`. The agent was
instructed to use PATH CLI epoch E1, commit forward without pushing, leave this
plan untouched, and report evidence and proposed plan wording to the
coordinator.

**Checkpoint completed 2026-07-20:** Subagent commit
`e7622e9524b1e5adf8dfddd4a10c6700fd8cea0d` (`Add typed two-panel screen
workspace`) adds the client screen-type registry, typed `sfm:test_screen`
factory, `sfm:workspace/open_to_side` action, narrow `SFMScreenPanel` contract,
two-column `SFMScreenMultiplexer`, mouse and Ctrl+1/Ctrl+2 focus routing,
narration/lifecycle hooks, changelog entry, and Brigadier tests. The proving
command is:

```text
sfm action invoke sfm:workspace/open_to_side sfm:test_screen test screen 1
```

PATH CLI epoch E1 compilation, focused action tests, and the full Java test
suite passed; `git diff --check` passed and the feature worktree is clean.

The architectural result is to use `SFMScreenPanel` as the reusable embedded
surface with `SFMScreenMultiplexer` as the sole vanilla `Screen`. The previous
screen is currently parked and represented by a placeholder rather than being
live-rendered inside the left panel. Arbitrary `Screen` adaptation remains
deferred because viewport initialization, widget ownership, close/removal, and
other global lifecycle behavior are not safely contained.

At this checkpoint Track 1 remained in progress rather than accepted. It still
needed a live client/puppet visual and lifecycle proof, a host-intent contract
for panel close/split/open requests, and a decision on whether any audited
full-screen adapter belonged in scope.

**Layout-algebra follow-up started 2026-07-20:** The Track 1 subagent was
resumed from clean checkpoint `e7622e9524b1e5adf8dfddd4a10c6700fd8cea0d`
for a design-only comparison with `G:\Programming\Repos\egui_tiles`. It will
evaluate n-ary horizontal/vertical allocation, binary/nested splits, grids,
tab/flip containers, tab-strip orientation, virtual workspaces, stable panel
identity, active/hidden lifecycle, divider shares and linked four-corner resize,
tree simplification, persistence, and the semantics of centralized open, push,
and multiplexer operations. No implementation change is authorized in this
follow-up.

**Layout-algebra checkpoint completed 2026-07-20:** Commit
`9224715c627af3e7cf74942bc1e4a41c7b21e99b` (`Document multiplexer layout
algebra`) adds `docs/architecture/screen-multiplexer-layout-algebra.md` on the
Track 1 branch. The worktree is clean; this follow-up changed documentation only.

The proposed algebra is `Panel(panelInstanceId)` leaves; n-ary
`Linear(axis, orderedChildren, tracks)` with shares and minimum constraints;
first-class `Stack(orderedChildren, activeChild, selectorPresentation)` as the
semantic Flip with tabs generated as chrome; first-class
`Grid(rows, columns, cells)` for linked two-axis dividers and four-corner grips;
and `WorkspaceSet(activeWorkspace, independently rooted trees)` as a separate
domain/persistence boundary for virtual desktops.

Structural arity and divider constraints are separate concerns. N-ary Linear
preserves peer relationships, simple shares, insertion/removal/reordering, and
same-axis normalization. Spatially aligned dividers in distinct branches do not
communicate ad hoc: a true shared row/column intersection is represented as a
Grid, or later by explicit linked divider identities. Coincident geometry never
silently creates semantic linkage.

The inspected `egui_tiles` checkout supports n-ary horizontal/vertical Linear,
Tabs, Grid, shares, active/inactive tiles, drag insertion, simplification, and
persistence. Its tab strip is horizontally rendered at the top; vertical tabs
are explicitly future work. The report also specifies clobber-global,
push/pop-modal, enter-workspace, open-to-side, open-as-tab, focus/move, stable
identity, lifecycle, focus/drop routing, normalization, persistence, and the
Track 3 adapter boundary.

**Implementation and visual checkpoint completed 2026-07-20:** Commit
`2111a50719b22ffcdbe691809680b966a5603f00` (`Implement n-ary screen workspace
intents`) adds stable panel
instance ids, normalized n-ary horizontal and vertical `Linear` layout with
shares and minimum constraints, deterministic allocation, insert/remove and
focus preservation, and typed `Close`, `OpenToSide`, and `OpenAsTab` panel host
intents. Unhosted intents return `UNAVAILABLE`; tabs return explicit
`UNSUPPORTED` until `Stack` exists. The multiplexer uses this model and supports
ordered Ctrl+1 through Ctrl+9 focus traversal.

The live `sfm:title_screen_workspace` puppet exercises the real command palette,
Brigadier, client action, workspace creation, panel focus, natural close/back,
and fresh reopening. Runs at 1280x720 and 960x540 passed and were visually
inspected; a discovered narrow-layout text overflow and a puppet palette-reopen
state bug were fixed. The full Java suite passed, Java audit reported zero
warnings, Git checks passed, and the worktree is clean.

Track 1's bounded experiment is accepted. Arbitrary vanilla `Screen` embedding
is rejected from this track in favor of intentional `SFMScreenPanel` content.
`Stack`, `Grid`, persistence, divider dragging/linking, and the Track 3 adapter
remain later capabilities or integration work rather than blockers to this
checkpoint.

### [ ] Track 1b — Pointer-driven divider resizing and cursor affordances

This follow-up completes WRESIZE-1 through WRESIZE-6 without implementing
Alt+drag relocation, explorer root/node drag, virtual workspaces, or arbitrary
vanilla `Screen` embedding.

**Verified starting point:** `SFMWorkspaceLayout` already stores normalized
n-ary horizontal/vertical `LinearNode` tracks with positive shares and minimums,
allocates stable panel bounds, and supports discrete directional resize by a
five-percent step. `SFMScreenMultiplexer` currently forwards mouse movement,
click, drag, release, and scroll to panel content; it has no first-class divider
identity, divider hit region, pointer capture, continuous share mutation, or OS
cursor ownership. The accepted algebra reserves `Grid`/linked dividers and says
coincident geometry alone does not create semantic linkage.

**Work — pure divider model:** Derive stable `DividerId` values from layout
node identity/path plus axis and adjacent track identities. Expose each divider's
logical/physical hit rectangle, movement interval, adjacent minima/current
shares, and optional explicit link group. Add a pure constrained operation that
applies a pixel/logical delta (or final share pair) without recreating panels,
then normalizes shares deterministically. Resize all divider ids captured at a
pointer intersection in one logical transaction: x affects vertical dividers,
y affects horizontal dividers. At a T-junction or nested three-panel layout,
only dividers whose real hit regions contain the pointer participate. Persisted
cross-branch synchronization requires explicit Grid/linked ids; visual
coincidence is not enough.

**Work — host gesture and cursor lifecycle:** Hit-test dividers before child
panel dispatch. Hovering one axis selects horizontal/vertical standard resize
cursor; hovering an orthogonal set selects the best available resize-all cursor
or a documented crosshair fallback. Create standard cursor handles once per
Minecraft window/lifecycle, restore the prior/default cursor on exit, and
destroy owned handles safely. Mouse-down captures exact divider ids, starting
shares/bounds, pointer, workspace generation, and button. Drag applies deltas
continuously even if the pointer leaves the original narrow hit region;
release commits, Escape/screen close/focus loss/layout replacement cancels or
ends according to a tested policy. Captured divider gestures consume input so
child panels do not also click/drag.

**Work — semantic action and observability:** Add an internal typed
`ResizeDividers` intent and a registered hierarchical action that can select
divider ids and set/adjust shares or deltas without ambient pointer coordinates.
Keep `sfm:panel/resize/{left|right|above|below}` as the keyboard-friendly
panel-relative operation; both paths call the same constrained share model.
Describe dividers, links, hit bounds, shares/minima, active hover/capture, and
before/during/after panel bounds in structured puppet artifacts.

**Local behavior references:** Inspect, cite, and behaviorally adapt—without
copying or depending on—the following VS Code sources:

- `G:\Programming\Repos\vscode\src\vs\base\browser\ui\sash\sash.ts`
- `G:\Programming\Repos\vscode\src\vs\base\browser\ui\splitview\splitview.ts`
- `G:\Programming\Repos\vscode\src\vs\base\browser\ui\grid\gridview.ts`

Relevant concepts include orientation-specific cursor state, enlarged hit
areas, drag start/change/end, minimum/maximum constraints, orthogonal boundary
sashes, linked sashes, and linked width/height nodes. SFM remains an original
Minecraft/LWJGL implementation over its own layout/action model.

**Validation:** Pure tests cover two panels, unequal shares, minima/clamping,
reverse drag, viewport/GUI-scale conversion, nested same/orthogonal splits,
T-junctions, four-pane intersections, explicit versus coincident links,
stacks/hidden entries, focus/identity preservation, deterministic serialization,
cancel, and layout mutation during capture. Host tests cover hover entry/exit,
horizontal/vertical/intersection cursor shape, no per-frame handle creation,
screen/focus lifecycle reset, pointer capture outside bounds, child-event
suppression, registered-action parity, and keyboard resize regression.

Add a self-contained puppet with two-, three-, and four-pane layouts. It pauses
on each cursor/drag state, writes machine-readable divider/share/bounds data,
and captures before/during/after images at representative GUI scales. The
three/four-pane case must visibly resize both axes from one intersection drag.

```pwsh
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMWorkspaceDividerTests --wait-for-build-lock
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMScreenMultiplexerDividerInteractionTests --wait-for-build-lock
sfm-propagate-changes.exe run compile --branch 1.19.2 --wait-for-build-lock
sfm-propagate-changes.exe puppet run title_screen_workspace_divider_resize --branch 1.19.2 --variant declared --wait-for-build-lock
```

**Completion criteria:** Every resizable border advertises itself before drag;
horizontal/vertical and intersection cursor states are correct and never stick;
one intersection gesture resizes every explicitly hit orthogonal divider while
respecting minima; panels/stacks/focus/content identities survive; keyboard,
pointer, action, and automation paths agree; and artifacts make the share/bounds
transition verifiable without computer vision.

**Parallel topology:** Once `DividerId`, geometry, delta, and cursor-adapter
interfaces are frozen, a pure layout/test lane, a GLFW cursor-lifecycle lane,
and a puppet/artifact lane may proceed in parallel. One integration owner alone
edits `SFMScreenMultiplexer`, central action registration, shared layout wiring,
plan/changelog bookkeeping, and final live proof.

### [x] Track 2 — Native file drag-and-drop feasibility

Investigate the complete path from the Minecraft window's GLFW/LWJGL handle to
a safe SFM file-drop event. Determine what callbacks Minecraft already installs,
whether registering another callback replaces vanilla behavior, which thread
receives it, and how callback chaining and cleanup work across supported
versions.

The output is initially an evidence report, not a production feature. If the
API is viable, add the smallest isolated proof that logs normalized dropped
paths while an explicit SFM test screen is active. The proof must not expose
arbitrary filesystem contents, follow dropped directories recursively, mutate
files, or displace an existing Minecraft callback.

Record:

- relevant GLFW/LWJGL and Minecraft source/API evidence per version boundary;
- callback lifetime, thread, ownership, and chaining behavior;
- whether Forge/NeoForge already exposes an appropriate event;
- sandbox/path-normalization and multiplayer implications; and
- a recommendation: adopt, adapt behind a version seam, or reject.

**Delegation started 2026-07-20:** Assigned to subagent
`track2_file_drop_research` in
`D:\Repos\Minecraft\SFM\worktrees\1.19.2-file-drop-research` on branch
`feat/1.19.2/file-drop-research`. The worktree was created and verified clean at
immutable baseline `246dddbc812644e3ca199e2bec85d8d152954b69`, with PATH CLI
epoch E1 verified. The agent is authorized to commit a research report but must
request follow-up authorization before implementing even an isolated callback
proof. It leaves this canonical plan untouched and reports evidence here.

**Research checkpoint completed 2026-07-20:** Subagent commit
`99df31e4dd7e7b0b0e6003de9fe727b36c7efbd3` (`Document native file drop
feasibility`) adds `docs/tasks/native file drop feasibility report.md` on the
Track 2 branch. The worktree is clean and `git diff --check` passed.

The recommendation is to adopt vanilla `Screen.onFilesDrop(List<Path>)` and
reject direct `glfwSetDropCallback` replacement or chaining. Minecraft 1.19.2
already deep-copies native UTF-8 path names, schedules through
`minecraft.execute`, and calls the active screen. The public screen seam is
evidenced across all ten supported SFM branches, and local Forge/NeoForge source
searches found no preferable loader event. No current version adapter, global
subscriber, callback setup, or callback cleanup is indicated.

The optional proof remains unimplemented pending authorization. Its accepted
shape is an explicit SFM test screen override that boundedly copies and
lexically normalizes paths for local display/logging only, without existence
checks, `toRealPath`, directory traversal, symlink following, reads, writes,
packets, or server logging. A production multiplexer should forward one
`DroppedPathsIntent` only to the focused panel when that panel explicitly
accepts drops.

**Pre-drop hover follow-up started 2026-07-20:** The Track 2 subagent was
resumed at clean commit `99df31e4dd7e7b0b0e6003de9fe727b36c7efbd3` to determine
whether SFM can truthfully detect files hovering over the Minecraft window
before the final `onFilesDrop` call. The investigation covers the GLFW public
API and Win32/Cocoa/X11/Wayland backends, LWJGL native access, Minecraft window
handles, loader seams, native ownership/cleanup, and the cost of a patched GLFW
or platform-specific hook. It is documentation-only; any native or
platform-specific proof requires separate authorization.

**Pre-drop hover follow-up completed 2026-07-20:** Commit
`4efcef2323266d2724b229b26aa5a82462e440ef` (`Document pre-drop hover
feasibility`) adds a 206-line addendum to the Track 2 report. The worktree is
clean and the documentation commit passes Git whitespace checks.

There is no reliable public pre-drop file-hover signal through
GLFW/LWJGL/Minecraft or the loaders. Win32 GLFW uses `WM_DROPFILES`, which has
only a final-drop phase. Cocoa, X11, and Wayland receive richer drag lifecycle
events internally but do not expose them. Native-window handles do not solve
listener, event-queue, ownership, or cleanup conflicts, and cursor/focus/button
heuristics cannot truthfully distinguish an OS file drag.

The accepted product behavior is a restrained persistent capability cue on the
focused drop-capable panel, final delivery through
`Screen.onFilesDrop(List<Path>)`, and explicit accepted/rejected feedback after
drop. Pre-drop hover is deferred as optional upstream/forked GLFW lifecycle
work. Win32 would require moving from `WM_DROPFILES` to OLE `IDropTarget`; a
dev-only Windows proof remains research-only and is not a file-explorer release
blocker. Track 2 is complete without native proof code.

### [x] Track 3 — Responsive full-screen file explorer

Create a new file explorer as an ordinary full-screen experience first, while
keeping its domain model and layout responsive enough to be hosted later in a
multiplexer panel.

Separate filesystem/domain behavior from Minecraft layout and rendering. The
first slice should provide a navigable tree/list, selection, open intent, empty
and error states, keyboard and mouse focus, and graceful behavior from a large
viewport down to a deliberately specified minimum panel size. It should not
depend on Track 1 internals.

The source-provider boundary must allow later adapters for:

- SFM-owned workspace files;
- mounted in-game disks and conflict state from `feat/1.19.2/mount`;
- repository/game source presented read-only; and
- native dropped paths, but only if Track 2 demonstrates a safe contract.

The initial explorer is read-only. Editing, deletion, arbitrary host filesystem
roots, and mount synchronization remain separate decisions.

File entries have extension-aware presentation inspired by VSCode. A central,
extensible presentation registry maps directories, known extensions, compound
extensions, unknown extensions, and extensionless files to an icon plus text
style. Matching order and case behavior are deterministic. Initial mappings
cover SFM/SFML, grammar, Java, structured configuration/data, and ordinary text
formats. Presentation metadata does not determine filesystem semantics, and
icons or color are never the sole file-type cue: visible names and accessible
narration remain sufficient. Prefer small repository-native assets over
adopting a large third-party icon set in the first slice.

**Delegation started 2026-07-20:** Assigned to subagent
`track3_file_explorer` in
`D:\Repos\Minecraft\SFM\worktrees\1.19.2-file-explorer` on branch
`feat/1.19.2/file-explorer`. The worktree was created and verified clean at
immutable baseline `246dddbc812644e3ca199e2bec85d8d152954b69`, with PATH CLI
epoch E1 verified. The agent owns the standalone responsive slice and reports
its future Track 1 adapter contract without depending on Track 1 internals. It
commits forward, leaves this canonical plan untouched, and reports evidence to
the coordinator.

**First vertical-slice checkpoint completed 2026-07-20:** Subagent commit
`360c246d055cc2f13493d24f745ea528c1ff99a0` (`Add responsive read-only file
explorer experiment`) adds the standalone development explorer. Its worktree is
clean. PATH CLI epoch E1 was verified; the exact branch compile and diff checks
passed, and the branch test run passed all 252 tests.

The slice has explicit source, immutable snapshot, model, layout, and
presentation boundaries. `SFMFileExplorerModel` owns expansion, selection,
keyboard semantics, flattening, and typed read-only open intents without a
Minecraft rendering dependency. `SFMFileExplorerLayout` accepts an arbitrary
host rectangle, documents a 180x120 minimum, switches to compact treatment
below 320x180, and returns bounded geometry suitable for a future Track 1
adapter. The development screen uses a safe in-memory fixture rather than host
paths or mounted disks.

`SFMFilePresentationRegistry` performs deterministic, case-insensitive,
longest-suffix-first matching and covers directories, `.sfml`, `.sfmp`, `.g4`,
`.java`, `.json`, `.toml`, `.properties`, `.md`, `.txt`, `.tar.gz`, `.gz`, and
unknown or extensionless files. Each row pairs its color/style metadata with a
text icon and visible kind label; narration includes the selected name, kind,
and list position.

At this checkpoint Track 3 remained in progress. Remaining work included visual
puppet QA, a real bounded source-provider adapter, a consumer for file open
intents, the Track 1 content-panel adapter, and separately scoped mount or
native-drop integration. The fixture reported an open intent but intentionally
did not open an editor or mutate files.

**Visual and bounded-source checkpoint completed 2026-07-20:** Commits
`99e05aa5283d02bedbe53f04c546af637a314c04` (`Add file explorer visual puppet
coverage`) and `b7e56a1b1ad1a0438a507c9adc15226f9467b0da` (`Add bounded
explorer capture scenarios`) add deterministic live puppet coverage and a
bounded read-only path source. The standard explorer was captured at 1280x720
and 640x480 in ready, expanded/file-type, loading, empty, and error states.

The `sfm:title_screen_instance_file_explorer` puppet browses the isolated
`runGameTestPreview` instance with `NOFOLLOW_LINKS`, explicit root ownership,
depth 3, at most 256 total nodes and 64 children per directory, plus exclusions
for screenshots, logs, saves, crash/download data, and known account/server
history names. Root and expanded captures passed and show representative
instance content without browsing the capture output itself.

The `sfm:title_screen_large_file_explorer` puppet uses a deterministic 1,002-file
hierarchy containing `0000.txt` through `1001.txt`, grouped into `0000-0999` and
`1000-1999`. Its captures prove both groups, first-entry navigation, and a
virtualized boundary view containing `0999.txt`, `1000.txt`, and selected
`1001.txt`; only visible rows render from the 1,004-node flattened view. All 256
tests, exact-branch compilation, both final puppet runs, and Git checks passed;
the worktree is clean. Track-owned commands, artifacts, safety bounds, and
observations are recorded in `docs/tasks/track3 file explorer visual qa.md`.

The CLI currently cannot assemble two comma-separated puppet ids when each
scenario emits the same local figure number; it reports a duplicate figure
number. Running the stable ids separately succeeds. No CLI change was made.

At this checkpoint Track 3 remained in progress. Its remaining product work was
a consumer that opened file intents in an editor, the Track 1 content-panel
adapter on an integration branch, and separately accepted mount or native-drop
source adapters.

**Integrated explorer workspace completed 2026-07-21:** The dedicated
`feat/1.19.2/review-workspace` branch merged Track 1 at
`5f7224e6752c5f270d8270bc328eb61da918a191`, merged Track 3 at
`bb42285658309e0ec2f2edc6218d3cfc8ed7f379`, and implements the integrated
behavior in commit `90c8fc26b1415ab6c30c43206b1f3d9efe4733bd` (`Integrate file
explorer workspace previews`). The worktree is clean and no version propagation
has run.

The user-facing developer action now opens a single-panel multiplexer containing
the explorer, so the explorer occupies the complete viewport. Final Java file
drops are routed through `Screen.onFilesDrop` to the focused drop-capable panel.
Exactly one existing, readable, non-link directory replaces the root; invalid,
missing, multiple, file, link, or inaccessible drops retain the previous source
and show structured feedback. The read-only path adapter uses bounded-memory
directory selection, excludes configured descendant locations, validates path
components and root containment, limits previews to 1 MiB, and decodes UTF-8
strictly. It remains best-effort against hostile path-replacement races on file
providers without `SecureDirectoryStream`.

The explorer is an `SFMScreenPanel`. Activating the first text-like file inserts
one reusable read-only text panel to the right with equal layout shares. A later
single-click on another text-like file changes the path and content in that same
viewer instance and panel id; it does not add a third panel. Drops made while
the viewer is focused delegate to its paired explorer. Dropping a new root while
a preview exists intentionally leaves the old preview visible until a file under
the new root is selected.

The `sfm:title_screen_integrated_file_explorer` puppet proves the complete
sequence at 1200x720: explorer-only, deterministic directory delivery through
`Screen.onFilesDrop`, root replacement, first `alpha.txt` preview, then
single-click replacement with `beta.txt`. Semantic assertions prove panel counts
`1 -> 2 -> 2`, equal-share allocation with at most the unavoidable one-pixel
remainder, stable viewer identity, and changed path/content. The exact run exited
with `SFM_GAME_PUPPET_SUCCEEDED` and `failed=0 total=1`; its four inspected
captures are under
`platform/minecraft/build/sfm-toolchain/artifacts/game-test-preview/runs/title_screen_integrated_file_explorer-20260721-000413-343`.

The full Java test suite and all-source compilation passed; two portable symlink
tests were skipped because Windows denied symlink creation. Java audit reported
zero warnings and Git checks passed. Track 3 and its Track 1 integration target
are complete. Mounted-disk synchronization and editable previews remain separate
future scope rather than missing requirements of this read-only workspace.

### [~] Track 4 — Dynamic hotkeys and typed command completion

Investigate and prototype user-defined keyboard shortcuts whose target is a
Brigadier command draft, normally below the client-local `/sfm action` surface.
The same command may have multiple bindings, and bindings must be addable,
editable, disableable, and removable at runtime without treating Forge's or
Minecraft's statically registered key-mapping collection as mutable unless
source evidence proves that lifecycle safe.

The proposed SFM shortcut layer listens through the existing universal input
seam, resolves configurable keys, chords, or sequences, and submits the bound
command draft to the same dispatcher used by the command palette. It does not
introduce a second action executor. Vanilla `KeyMapping` remains a candidate
for the one stable entry-point shortcut, while dynamic user bindings may need
an SFM-owned registry/configuration and input state machine.

A binding target is not assumed to be executable merely because it is valid
text. For example:

```text
/sfm action invoke sfm:help
/sfm action invoke echo 
```

The first may be complete. The second is a valid partial command awaiting an
argument. Activating an incomplete binding opens a parameter-completion flow
rather than reporting an opaque syntax error. The experiment should distinguish
at least:

- complete and executable;
- syntactically valid but incomplete, with one or more arguments remaining;
- invalid at a known range, with Brigadier diagnostics and suggestions; and
- unavailable in the current client action context.

The completion UI is initially a dedicated full-screen experience with a
responsive content model that can later be hosted by the multiplexer. It should
guide the user through missing parameters in declaration order, then show a
builder/properties summary containing each parameter's name, declared type,
rendered value, validation state, and provenance. The same summary shows the
final command string and requires explicit confirmation before execution unless
the binding is both complete and configured for immediate execution.

The final command string is clickable and can be opened as text in the user's
preferred SFM editor. Returning from text editing reparses the entire draft
through Brigadier, reconstructs the parameter/property view where possible,
and publishes parse errors or command feedback through the shared console/log
surface. Editing the string and editing typed properties are two projections of
one command draft, not independently synchronized documents.

The experiment must determine how much typed argument metadata Brigadier alone
can recover. If argument names, editors, display types, defaults, or safe
serialization cannot be derived reliably from the command tree, contextual
action and screen registrations may contribute explicit parameter descriptors
while Brigadier remains authoritative for parsing, suggestions, and execution.

Initial experiment limits:

- one persisted profile of SFM-owned bindings;
- keyboard keys plus a deliberately bounded chord/sequence grammar;
- conflict detection against vanilla and other SFM bindings;
- one complete no-argument action and one incomplete string-argument action;
- typed draft, confirmation, and text-edit round-trip screens;
- no OS-global shortcuts, arbitrary macros, automatic chat/server command
  execution, or silent execution of an incomplete/invalid command; and
- no runtime mutation of vanilla's key registry without lifecycle and
  cross-version proof.

#### Accepted dynamic-binding and presentation direction (2026-07-21)

Source inspection established that Forge's registration event and Minecraft's
vanilla key-mapping collection are startup-oriented and do not provide a safe,
complete runtime unregister lifecycle. Dynamic SFM bindings will therefore not
attempt to appear as dynamically registered rows in the vanilla Controls
screen. One or a small number of stable entry-point mappings may remain vanilla
`KeyMapping` instances, while SFM owns its dynamic registry, persistence,
conflict detection, matching engine, and configuration screens.

The primary relationship is one registered client action to zero or more
bindings. A binding contains a bounded key, modified key, chord, or sequence and
targets that action's invocation or a typed/partial invocation draft. The
action's existing contextual availability determines whether it is currently
valid; bindings do not introduce an unrelated VS Code-style expression
language. An unavailable action may keep its configured bindings, but matching
one must not bypass the same availability check used by the palette and direct
command execution.

Use unambiguous matching terms:

```text
KeyStroke   = modifiers plus one trigger key
KeySequence = one or more ordered KeyStrokes
```

Ctrl+R is a one-stroke sequence; Ctrl+K followed by Ctrl+E is a two-stroke
sequence. Modifier presses/releases remain ordinary recorded input events even
when the matcher presents them compactly as part of a stroke.

The dedicated SFM key-mapping screen lists actions and their zero-or-more
bindings, supports search and conflict presentation, and permits bindings to be
added, edited, disabled, and removed at runtime.

Keep command-palette rows single-line. An action with no bindings shows no
binding text; one binding is static; more than one binding uses the same fixed
right-side area and cycles through one binding at a time, initially once per
second. Cycling order is stable and deterministic. It changes presentation
only—every binding remains active simultaneously—and it must not resize the row
or cause suggestion reordering. The details tooltip/screen lists all bindings
at once. Narration must not announce every timed cycle; it announces that the
action has N bindings and provides a deliberate way to inspect them.

Multiline palette rows and a user preference selecting multiline versus cycling
are deferred. They may be reconsidered after the compact cycling presentation
is exercised, but are not part of the first surface.

A right-side question-mark/details affordance provides a hover summary and
opens an action-details screen when activated. The action-details screen owns
the expanded description, current availability and unavailable reason, action
id, argument/presentation metadata, and a binding section with add/edit/remove
operations. It is the focused configuration surface for one action rather than
overloading the compact palette row with every control.

The matching engine is deliberately stateful. It receives normalized input
events incrementally as they occur, maintains pressed-key and partial-sequence
state, applies sequence timing/cancellation rules, and emits action-invocation
intents with their source binding and event provenance. Its host-facing surface
should cover at least key press, key release, focus loss/reset, and time/tick
progress without depending on a concrete Minecraft `Screen`. Replaying the same
ordered event stream into a fresh engine with the same binding snapshot and
timing policy must produce the same invocation stream. The engine does not need
to accept the complete key history on every call or avoid ordinary internal
state merely to be replayable.

The conceptual host surface includes incremental `accept(event)`,
`advanceTime(tick)`, `reset(reason)`, and
`replaceBindings(bindingSnapshot)` operations. Replacing bindings or losing
focus cancels partial matches and advances the recorded binding revision.
`ActionInvocationIntent` retains action id, typed arguments, binding id,
binding revision, and source-event range. Minecraft input capture is an adapter:
the pure matcher must not depend on a concrete `Screen`. Track 4 must verify a
global press/release seam because tick polling alone may lose ordering when
multiple transitions occur inside one tick.

The first visible acceptance walkthrough is:

1. open the SFM key-mapping screen from the command palette;
2. inspect an action with no bindings and add one chord or sequence;
3. return to the palette and see the binding on that action's row;
4. hover the details affordance to see its description and bindings;
5. click through to the action-details screen and add a second binding;
6. return to the one-line palette row and observe the two bindings cycle in its
   fixed right-side area without changing layout or narration repeatedly;
7. activate both bindings and observe the same contextual action executor;
8. disable or remove one binding without restarting Minecraft; and
9. replay the captured input events through a fresh engine and obtain the same
   action-invocation trace.

### [ ] Track 5 — Vox Java support and external shaped-intent UI

Develop the cross-language foundation for Java/Minecraft and Rust tools to
exchange typed intents and invoke capabilities over Vox. The motivating proof
is an intent such as “ask the user to provide the colour for this purpose”:
Minecraft receives or creates the intent, delegates fulfillment to a Rust
eframe UI, and receives a validated typed result. The reverse direction allows
a Rust tool to request an SFM client action from a running Minecraft instance.

This is an independent upstream track in the maintained Facet fork at
`G:\Programming\Repos\facet`, not an SFM feature worktree. Its results enter SFM
only through an accepted Vox Java artifact/protocol version and a later narrow
Minecraft adapter.

The verified protocol, historical-Java, SFM-packet, packaging and future
generated-payload research is recorded in
[`vox-java-phon-and-generated-packets.md`](../architecture/vox-java-phon-and-generated-packets.md).
That document is the durable technical companion to this checklist and should
be consulted before repeating repository or loader research.

#### [x] 5.1 Synchronize the maintained Facet fork

Completed 2026-07-23. Both remotes were fetched, current `origin/main`
(`8edaa1639`) was merged without rewriting maintained history, and combined
Rust 1.96 formatter/lint drift plus one Figue `IpAddr` default incompatibility
were repaired. The supported Windows CI matrix passed 8,071/8,071 tests with
the repository's explicit container, Swift, and TypeScript subject exclusions;
full workspace/all-feature/all-target clippy passed with warnings denied.
Maintained `main` and `mine/main` both resolve to the clean published checkpoint
`05263dd6d119d271a21883636b23db6d45a2466f`.

Before Vox design or delegation:

1. fetch `origin` and `mine`;
2. verify the integration checkout remains clean and local `main` equals its
   intended `mine/main` base;
3. merge the fetched `origin/main` into local `main` without rewriting the
   maintained fork's history;
4. run the Facet/Vox-required gates and resolve upstream/fork intent rather
   than mechanically preferring one side;
5. commit and push the clean integration checkpoint to `mine/main`; and
6. record the immutable resulting commit before creating a delegated worktree.

Facet's repository instructions require every delegated worktree to start from
a clean, pushed integration checkpoint. Create the worktree as a separate
operation from agent creation, using the exact commit SHA; verify the worktree's
`HEAD` equals that SHA before attaching an agent. Agents commit forward and do
not push unless explicitly assigned publication ownership.

#### [x] 5.2 Re-survey Java support after synchronization

Current local and upstream-source evidence says Vox does not yet have an
implemented Java target:

- `vox/README.md` lists Rust, TypeScript, and Swift support;
- `vox/rust/vox-codegen/src/targets/mod.rs` exports only `swift` and
  `typescript`;
- `vox/DEVELOP.md` documents `cargo xtask codegen --java`, and
  `vox-codegen/src/lib.rs` claims Java support, but no corresponding target or
  Java runtime is present; and
- Phon has no Java implementation either. Vox Java is therefore blocked on a
  conforming Java Phon schema/value/compatibility baseline, not merely a new
  Vox target renderer.

A historical Java subject/runtime existed at
`b0593a9f6fec57737508e575b01e4bb079a828cf` and was removed by
`bd6265411e21deffa5598c4f2719dcaddacf6d7a` during the Phon/schema-aware wire
rewrite. Reuse only API naming, `CompletableFuture` and build scaffolding ideas.
Do not restore its COBS framing, legacy handshake, ad hoc codecs, String-only
dispatch or 32-bit-truncated method ids.

The post-merge survey confirmed the same result across runtime, wire codec,
transports, generators, fixtures, conformance and artifacts: neither Phon nor
Vox has a current Java implementation. `vox/DEVELOP.md` was stale, and its
unimplemented Java command was corrected as part of the pushed contract
checkpoint rather than treated as support evidence.

#### [ ] 5.3 Implement the smallest conforming Java experiment

Build from the Vox specification and golden vectors rather than translating
the Rust implementation by intuition. First freeze a pushed specification and
public-interface checkpoint for Java 17, TCP, Phon codec/adapters, generated
bindings, runtime ownership, artifact layout and the deliberately unsupported
surface.

The implementation divides into three upstream ownership boundaries:

1. **Phon Java:** schema/value models, canonical encoding, schema closures,
   schema ids, compatibility plans, typed adapters and bounded failure behavior;
2. **Java generation:** `phon-codegen::java`, `vox-codegen::targets::java`,
   generated DTO/caller/handler/dispatcher/descriptors, embedded canonical
   schemas, `cargo xtask codegen --java`, drift checks and
   `javac --release 17`; and
3. **Vox Java runtime:** TCP framing and prologues, self-describing handshake,
   explicit connection driver, service lanes, schema binding, unary
   request/response correlation, cancellation, timeouts, shutdown and a hosted
   Java subject.

The first vertical slice is pure Java 17, TCP-only, unary and bidirectional. It
must prove Rust-server/Java-client, Java-server/Rust-client and both directions
on one connection for `echo(String)`, a nested DTO and a fallible method. It
also proves compatible evolution, incompatible call behavior, schema-binding
reuse, unknown method, invalid payload, cancellation, timeout and disconnect.

Channels, file descriptors, WebSocket, Unix/shared-memory/Iroh transports,
dynamic Facet values, automatic retry and optimized/JIT paths are explicitly
unsupported. Their shapes fail code generation with useful diagnostics.

Current Vox evolves its message protocol through schema exchange and
compatibility plans rather than one global semantic version field. The Java
runtime must still implement the versioned transport prologue, while an SFM
application service may expose a separate capability/revision contract.

#### [ ] 5.4 Prove a distributable Java artifact

Produce one small Java 17 Phon/Vox artifact, preferably with no third-party
runtime dependencies, and prove that SFM can compile against and ship it.

The SFM schema-v3 lockfile already maps dependency scope `bundle` to Gradle's
`jarJar` configuration. The completed packaging audit is recorded in
[`vox-java-jar-in-jar-packaging-research.md`](../architecture/vox-java-jar-in-jar-packaging-research.md).
It found loader-native JarJar support through ForgeGradle 5 on 1.19.2 and
NeoGradle on 1.20.4, 1.21.1 and 26.1.2, but also found three SFM toolchain gaps:
publication currently selects a bundled output only for 26.1.2, the Rust
packager skips ForgeGradle's JarJar task, and schema-v3 cannot preserve the
loader-specific Maven compatibility range required by ForgeGradle 5. Existing
26.1.2 ANTLR packaging proves that the nested dependency can be byte-identical
to the declared artifact, but it is not yet a cross-version acceptance proof.

Use the dependency lock/CLI workflow rather than handwritten Gradle edits or
direct Gradle commands. Preferred fallback order is loader-native Jar-in-Jar,
then shaded/relocated classes, then source vendoring. Generated service bindings
may live in SFM source, but vendoring the complete runtime is a last resort.

The artifact is not accepted until the published 1.19.2 mod launches and
completes a real Vox call in a clean instance without a separately installed
Vox JAR.

#### [ ] 5.5 Prove shaped intent fulfillment across Java and Rust

Define a language-neutral request/result contract for soliciting a shaped piece
of information. Facet's Rust `Shape` and partial-struct builder can drive the
eframe form, but raw Rust reflection pointers or layouts cannot cross the wire.
The Vox descriptor/codegen layer must project the necessary field identity,
type, optionality, constraints, documentation, defaults, and validation into a
portable schema or generated typed request.

The proof should:

1. originate a colour-input intent on the Minecraft Java side;
2. discover and connect to the local Rust UI peer;
3. render an eframe form derived from the known shape and allow partial
   construction until required fields are satisfied;
4. return a typed value or explicit cancellation/error to Java; and
5. resume the originating Minecraft action on the correct client thread.

Then prove the reverse direction: the Rust peer requests one allowlisted SFM
client action. If the Minecraft Vox endpoint is unreachable, the Rust UI
reports that state and may offer an explicit launch/retry workflow; it does not
pretend the action succeeded or launch arbitrary commands silently.

The connection is loopback-only by default, authenticated with a per-session
capability/token, schema/capability-negotiated, bounded, and explicit about
which actions may cross the process boundary. Loopback is not authentication.
Network callbacks never directly mutate Minecraft state; they enqueue onto the
appropriate game/client thread. eframe owns its own event loop and does not
borrow Minecraft's GLFW context.

The first reverse call is a structured fixed allowlist entry such as Echo, not
an arbitrary Brigadier command string. The puppet proof shows connected,
authenticated and pending states, the returned colour swatch, one accepted
reverse action, one rejected action and cancellation/timeout/disconnect. A
separate real eframe capture and the Minecraft captures are published together
in one HTML report.

#### [ ] 5.6 Explore coordinated Minecraft-window behavior

Treat Minecraft and eframe as separately owned windows/surfaces first. Build on
the existing puppet lifecycle ideas to discover, launch, focus, position, and
exchange intent with the Minecraft process, but keep OS-window manipulation
behind an explicit platform adapter. The first proof coordinates the windows;
it does not claim arbitrary rendering into Minecraft, input injection, or
portable compositor control.

Only after the RPC and lifecycle contracts are reliable should this track
consider side-by-side placement, returning focus, detecting a closed game,
launch suggestions, or treating the Minecraft window as one surface in a
larger developer workspace.

#### [ ] 5.7 Explore Rust-defined generated SFM payloads later

Do not rewrite the existing Minecraft packet channel during the first Vox
integration. Today SFM keeps the same `SFMPacketDaddy` record/encoder/decoder/
handler abstraction while adapting its registration shell from Forge
`SimpleChannel` and `FriendlyByteBuf` on 1.19.2 to custom payloads and
`StreamCodec<RegistryFriendlyByteBuf, T>` on newer versions.

After Phon/Vox Java is stable, investigate Rust/Facet as the build-time source
of truth for loader-neutral SFM payload schemas. Generate Java payload records,
bounded field codecs, stable ids and registration descriptors, then adapt them
to old `FriendlyByteBuf` and modern `StreamCodec` surfaces. Keep handlers,
permission/sender/world/menu validation, game-thread scheduling and side
effects hand-written.

Minecraft-specific types require an explicit versioned adapter catalog.
Prototype a portable packet first, then a bounded string/enum packet such as the
disk-program mutation, and only later registry-aware values such as
`ItemStack`. Compare direct generated Minecraft buffer operations with carrying
a Phon envelope inside one custom payload; do not assume either representation
without captured-vector tests and measurements.

### [~] Track 6 — Source comparison viewer and human review ledger

#### Comment-substrate architecture revision — 2026-07-22

The fixed `reviewed`/`approved` fields in the current fixture-driven
`SFMReviewLedger` are now explicitly a prototype projection. The authoritative
next design is the [global comment selection and review sessions
plan](global%20comment%20selection%20and%20review%20sessions%20plan.md).

Review state becomes a global session containing ordinary string comments and
persistable selection rules. Hashtags such as `#approved`, `#problem`,
`#needs-change`, `#added`, and `#removed` are derived from comment text rather
than stored as a competing tag field. Rules may select overlapping, disjoint
glyph sets across before/after documents, files, repositories, and Minecraft
versions. Compiler, audit, diff, human, and trusted transformation-rule outputs
share this substrate while retaining structural provenance.

The complete before/after surface remains selectable: an after document is the
primary proposed state, but removed methods and deleted files in before
documents must also accept comments and approval. Diff colouring is generated
through comment-style rules over comparison-produced comments. Advancing a
worktree reevaluates comment rules against the new snapshot and reports exact,
relocated, transformed, ambiguous, missing, invalid, and changed results;
approval never silently crosses an ambiguous or changed match.

Do not extend the boolean ledger before implementing the comment/session
interchange, literal selectors, derived hashtag parser, overlap evaluation, and
legacy projection described by that plan. The later real-repository browser
consumes the new substrate.

Build the first review-specific product on the integrated multiplexer and file
explorer. The viewer accepts immutable before/after snapshot identities and a
structured SourceComparison; it does not require its central artifact to be a
.diff or .patch file. Line/range comparison is the required fallback.
Rust/Arborium may later supply semantic operations through the same provider
boundary.

The code remains presented as code. Comparison annotations may identify file
add/delete/rename, range insertion/deletion/replacement, symbol rename,
formatting-only change, unchanged body under rename, renamed-and-modified body,
and ambiguous/unknown correspondence. An AST tree view may be offered for
specialized refactoring inspection but is not the normal source-review
presentation.

Keep these dimensions independent:

- Git/repository state: tracked, committed, staged, or uncommitted;
- human review state: unseen or reviewed;
- human decision: undecided, approved, or rejected; and
- audit/policy approval: permitted, warned, or forbidden by SFM audit rules.

A canvas rectangle, text selection, hunk, or AST annotation is an interaction
that resolves to durable source anchors or comparison-operation ids before a
decision is stored. Durable records include the snapshot pair, target spans or
operation id, before/after hashes, and bounded excerpts/witnesses. Re-rendering
derives highlight rectangles from the source anchors. A source change makes an
old decision stale; relocation/reconciliation is explicit and cannot silently
transfer approval.

Distinguish approving resulting content from approving a change operation.
Approving a region in B alone does not prove that a deletion from A was
reviewed. The UI should make the chosen target visible and allow a review mark
to cover one or more comparison operations intersected by a canvas selection.

The first visible walkthrough is:

1. invoke **Open Review Workspace** from the palette;
2. choose fixture or real before/after snapshots;
3. browse to a changed Java source file;
4. open its comparison to the side;
5. inspect source-oriented annotations;
6. select a range or operation and mark it reviewed;
7. independently mark it approved while audit status remains visible;
8. close and reopen with the ledger restored; and
9. change a source hash and show the old decision as stale rather than applied.

Add a captioned puppet for unchanged, insertion, deletion, rename, modified
body, reviewed, approved, audit-forbidden, restored, and stale-decision states.
The initial viewer may consume deterministic JSON fixtures while the CLI
comparison producer is developed.

### [ ] Track 7 — Multiplexer observation, recording, and panel ownership

Build recording and agent-control as a layer around composable multiplexer
panels rather than embedding bespoke trace machinery into each application.
A normal calculator, editor, explorer, or future panel should receive ordinary
input through the multiplexer router; an observation wrapper can record,
replay, or supply those same inputs without the child panel knowing whether a
human or agent originated them.

The generic envelope records at least:

- panel/workspace identity, size, lifecycle, focus, and configuration revision;
- normalized pointer position/move/button/scroll events in panel-local
  coordinates;
- key press/release/repeat, character input, focus loss, and tick/time events;
- input origin such as human, puppet, replay, or named agent session;
- routing/consumption result and the panel that received each event;
- optional structured observations published by panels; and
- optional visual captures of the panel region or Minecraft frame when pixels
  are needed as the observation.

Do not require every panel to expose semantic state. The base layer can record
input routing and visual frames; an optional observation-provider interface may
add calculator display/state, editor document/cursor, explorer selection, or
other structured values. Structured providers supplement rather than redefine
the normal panel behavior.

Agent input enters through the same multiplexer routing contract using a
virtual panel-local cursor and typed keyboard/controller events. It does not
need to seize or inject the operating-system cursor. Replaying a recording uses
the same router into a fresh compatible panel/workspace configuration.

Introduce a per-panel interaction-ownership lease:

- human-owned is the normal state;
- agent-owned remains visible to the human but rejects ordinary human mutation
  input for that panel;
- agent events are accepted only from the owning named session/capability;
- global observation, emergency revoke, and an explicit release affordance
  remain available to the human;
- ownership changes, rejected inputs, expiry/disconnection, and release are
  recorded events; and
- losing an agent session cannot leave keyboard modifiers, buttons, focus, or
  ownership stuck.

Use “read lock” only as user-facing shorthand if desired; internally this is an
interaction/input ownership lease, not a Java read/write lock. Human viewing is
allowed while human mutation input is gated.

#### Reusable timeline-panel direction

Add a generic timeline host to the composable panel surface. It accepts one
seekable inner panel, reserves a compact MPV-like horizontal transport, and
instructs the inner panel which bounded integer timestep to present. The wrapper
owns play/pause, track click/drag, thumb, current/final label, single-step, and
first/final controls. The child owns only its visualization at the requested
timestep.

Seeking is idempotent random access rather than “replay from zero until this
point.” Timeline control input is consumed by the wrapper and not forwarded to
the child. Playback advances on deterministic client ticks. Both the timeline
and child remain ordinary multiplexer panels; a full-screen host is only a
compatibility wrapper.

The first timeline fixture is a falsified, read-only chest/player-inventory
panel. It displays one cobblestone moving through these states:

1. chest slot occupied, player inventory and cursor empty;
2. chest empty and cobblestone held by the virtual cursor;
3. virtual cursor plus held stack moving through panel-local positions;
4. held stack over the destination player slot; and
5. destination slot occupied and cursor empty.

The fixture renders copied stacks and never opens/mutates a live menu or sends a
container click. Its puppet seeks forward, backward, and out of playback order,
capturing pickup, transit, pre-place, and placed frames. This establishes visual
item-movement replay before attempting live-container observation.

After that timeline proof, the first agent-ownership proof wraps a deliberately
ordinary calculator panel:

1. open the calculator beside another normal panel;
2. record human pointer and keyboard interaction through the generic wrapper;
3. replay the recording into a fresh calculator panel;
4. acquire the calculator for an agent and show an ownership badge;
5. let the agent manipulate its virtual cursor and keyboard inputs;
6. reject a human click on the owned calculator without blocking interaction
   with the neighboring human-owned panel;
7. revoke/release ownership and immediately restore human control; and
8. inspect the recording in the generic Episode Inspector.

The calculator itself owns only calculator behavior and optional structured
observation. It does not own the recorder, timeline, replay engine, agent
session, cursor injection, or ownership policy.

## Track dependencies and integration order

```text
Track 1: multiplexer + typed screen registry ─────┐
                                                  ├─> first integrated workspace
Track 3: responsive explorer + source provider ───┘

Track 2: drag/drop evidence ──> optional source-provider adapter

Track 4: dynamic hotkeys + command drafts ──> shared command/prompt surface
                                             └─> optional Track 1 panel adapter

Track 5: Phon Java ─> Vox Java/codegen ─> distributable artifact ─> SFM bridge
                                                           ├─> external fulfillment for Track 4
                                                           └─> bidirectional SFM action bridge

Track 5 packaging research ────────────────────────────────┘

Track 5 stable codegen foundation ──> future generated SFM payload research

Snapshot/episode plan ──> immutable snapshots + action provenance ──┐
CLI AST plan ───────────> optional structured comparison JSON ───────┼─> Track 6
Tracks 1 + 3 ───────────> multiplexer + explorer + source preview ───┘

Track 1 multiplexer/input router ──> Track 7 observation + ownership envelope
Snapshot/episode plan ─────────────> Track 7 recording/replay interchange
Normal calculator panel ───────────> first Track 7 proving application
```

Tracks 1 and 3 are intentionally parallel: Track 3 targets a normal `Screen`
and publishes a narrow responsive-content contract; Track 1 proves whether and
how such content can be embedded. Track 2 is independent research and must not
block either track. Track 4 depends only on the existing client action and
command-palette substrate; its completion UI is a normal responsive screen
until Track 1 supplies an accepted embedding contract. Track 5 has its own
Facet repository lifecycle and does not block the in-game prompt implementation;
it later supplies an optional external fulfillment adapter and action bridge.
Track 6 builds directly on the now-integrated Tracks 1 and 3. It may begin with
full snapshot and comparison fixtures; it does not wait for semantic AST
comparison, episode compression, or Vox.

Track 7 belongs above the multiplexer routing seam and below application
panels. It can begin independently of semantic source comparison and Vox; the
Episode Inspector consumes its recordings.

### Historical Track 6 real-repository bundle slice — 2026-07-22 (superseded)

Track 6 advanced from its frozen comparison fixture to the now-superseded
`repository-review-bundle-v1` contract.
The host prepared immutable before/after repository snapshots and comparison
operations; Minecraft opened a named bundle from its managed inbox. The first
observable slice used real SFM revisions `d07bef66c` and `8e9946d9f` and
showed:

1. command-palette selection of **Open review session**;
2. the real changed-file tree with themed ItemStack identities;
3. before/after source for a selected changed file;
4. creation of a literal review comment through the global comment kernel;
5. closing and reopening the deterministic session with that comment restored.

The bundle producer, loader/session lifecycle, and workspace were independent
feature tracks sharing one frozen fixture. The coordinator owned their merge
order and final puppet. Semantic Java correspondence, structural selectors,
comment migration to a third snapshot, AST refactoring operations, and release
coverage were subsequent slices; the baseline comparison exposed stable UTF-8
byte selections.

#### Track 6 real-repository result — 2026-07-22

Track 6 consumed a real, immutable repository-review bundle rather than the
comparison fixture. The integrated surface opened a named managed-inbox bundle
from the command palette, displayed ItemStack file identities and changed-file
search, rendered responsive before/after source, made a nonempty after-side
UTF-8 selection, persisted a literal comment through the shared review kernel,
and restored it after close/reopen. Six inspected 1200x720 frames covered open,
browse, search, selection, comment creation, and restored session state.

This slice deliberately kept comparison production textual. The bundle seam is
now removed by release-plan P-1.2. Subsequent structural correspondence,
durable selector, migration, coverage, and jump-list work reuses the preserved
comment/session authority while explicit revision selectors supply the source
documents; it must not recreate the managed inbox or a second comment model.

### Responsive evidence and composition wave — completed 2026-07-22

This wave precedes structural selector migration. The latest six
real-repository frames prove integration and persistence, but they do not yet
prove a usable review surface: source, paths, status, and comments are heavily
truncated; the changed glyphs are not visible; the command-palette and actual
close/reopen transitions are asserted by captions rather than shown; and the
walkthrough changes selected files between search and commenting.

#### Current layout debt

`SFMRepositoryReviewPanel` is currently one multiplexer leaf. Inside its
`render` method it manually reserves roughly one third for changed files and
splits the remainder into two source rectangles. Changed files, before source,
after source, and comment details are not independent `SFMScreenPanel` leaves
in `SFMWorkspaceLayout`. The multiplexer therefore cannot resize, maximize,
stack, rearrange, or responsively reveal them, and its Linear allocation/minimum
logic cannot correct the panel-local split. Calling the current result
"responsive" means only that arithmetic is recomputed for arbitrary bounds;
it does not mean the composition remains usable.

Refactor to one shared `RepositoryReviewWorkspaceModel` with distinct views:

```text
RepositoryReviewWorkspaceModel
|- ChangedFilesPanel
|- BeforeSourcePanel
|- AfterSourcePanel
`- ReviewCommentDetailsPanel
```

Selection, search, documents, byte ranges, comments, status, and persistence
belong to the shared model/controller. Each view owns only its presentation,
local scrolling, focus/input behavior, narration, and minimum/preferred sizing.
No view may create a second review-session authority.

#### Responsive compositions

The workspace host, not application-panel coordinate arithmetic, chooses a
composition from logical GUI bounds after Minecraft GUI scale:

```text
Wide:   Horizontal[ChangedFiles, BeforeSource, AfterSource]
Medium: Horizontal[ChangedFiles, Stack(BeforeSource, AfterSource)]
Narrow: Stack(ChangedFiles, BeforeSource, AfterSource, CommentDetails)
```

Narrow mode behaves like an Azure-blade drill-down: choose a changed file,
advance to comparison/source, then inspect or edit comment details, with a
visible back path and preservation of selection/scroll state. All modes support
maximize/restore of the focused content panel. Medium/narrow depend on the
planned first-class `Stack`; do not reproduce tabs or hidden panels inside the
repository renderer. Opening the application may therefore require a typed
`OpenPanelGroup`/`OpenLayout` intent that inserts one validated layout subtree
whose leaves share a model, rather than four unrelated global open operations.

Breakpoints must be selected from measured logical dimensions in the viewport
contact sheet, not guessed from physical pixels. If minima cannot fit, the
layout chooses a documented compact/stacked mode or a visible degraded state;
it never silently emits negative, overlapping, or border-obscuring rectangles.

#### Required visual story

The revised puppet declares the common responsive viewport profile defined in
the interactive preview plan. Each `(window size, GUI scale)` variant is a
fresh full scenario in the same Minecraft process. Within every scenario it
must keep one real changed file selected throughout and visibly show:

1. command palette with the real **Open review session** action;
2. changed-file choice and the complete path/details affordance;
3. the actual changed region with unambiguous before/after or added/removed
   emphasis, horizontal/vertical navigation, and a maximize path;
4. a nonempty selection whose complete content can be inspected;
5. complete literal comment text in the details panel without source overlap;
6. an explicit closed/background state and the reopen action; and
7. the same file, selection evidence, and exactly one restored user comment.

The contact sheet must make wide, medium, narrow, and any degraded modes
obvious. Auto and every supported numeric GUI scale remain separate requested
columns. A preferred/exact variant CLI override provides the fast development
path without weakening the declared full proof.

#### Proposed subagent/worktree wave

No worktree is created and no agent is dispatched until the user accepts this
plan. At goal start, the coordinator records one reviewed canonical `1.19.2`
baseline, freezes the typed viewport/variant marker fixture and the narrow
panel-group contract, installs that baseline's CLI, and creates:

| Agent track | Branch | Worktree | Exclusive scope and deliverable |
| --- | --- | --- | --- |
| A — Viewport sweep framework | `feat/1.19.2/puppet-viewport-sweep` | `D:\Repos\Minecraft\SFM\worktrees\1.19.2-puppet-viewport-sweep` | Rust CLI selection plus Java definition/harness/runtime loop; GLFW resize probe; requested/actual geometry; fresh full run per variant in one process; restoration; variant-aware manifest/contact sheet; no application layout changes |
| B — Size-display panel | `feat/1.19.2/viewport-calibration-panel` | `D:\Repos\Minecraft\SFM\worktrees\1.19.2-viewport-calibration-panel` | Historical branch name retained for provenance; deliverable is the reusable solid-colour `SFMScreenPanel`, pure allocation/contrast tests, and full/half/third/nested puppet proof |
| C — Responsive review composition | `feat/1.19.2/repository-review-responsive` | `D:\Repos\Minecraft\SFM\worktrees\1.19.2-repository-review-responsive` | Shared review workspace model; distinct file/before/after/comment panels; first-class Stack and typed panel-group insertion needed by those views; wide/medium/narrow/maximize behavior; preferred-variant puppet initially; no viewport harness or persistence-format changes |

Track A owns the cross-language viewport contract to avoid Rust and Java agents
independently designing the same marker schema. Track B is initially independent
and must use the existing panel boundary; after A merges, it receives a bounded
follow-up to adopt the declared profile and capture the full size-display sheet.
Track C may extract the model/views and pure-test layout policy in parallel, but
must not guess Track A's runtime API. Its live full-profile proof waits for A.

The coordinator alone updates canonical plans, reviews contract drift, merges
Track A first, integrates/rebases B and C onto the accepted interfaces, resolves
shared helper changes, installs the final CLI, and captures the merged evidence.
Subagents commit only their owned worktree changes and report commit ids, tests,
screenshots, assumptions, and proposed plan wording. They do not edit these
canonical plans, install a shared PATH CLI, propagate Minecraft versions, or
change `.g4` files.

#### Wave acceptance and stop conditions

- One Minecraft PID/process covers every declared viewport variant; each cell
  contains one fresh complete puppet outcome and stable logical figure ids.
- `preferred` and one exact variant run only one scenario for fast iteration.
- Original window/GUI scale and fixture/session state restore after success,
  failure, and cancellation; no variant accumulates another variant's comment.
- The size-display panel makes full, half, third, and nested allocated regions
  immediately visible through caller-selected solid colours and centered logical
  dimensions across the accepted profile.
- Repository review uses shared workspace nodes rather than a replacement
  manual three-column calculation, and responsive modes are chosen from logical
  bounds.
- The real review story exposes complete changed content and comment text and
  visibly demonstrates palette open, close, reopen, and exact restoration.
- Focused tests, canonical compile/full Java suite, Rust `check-all.ps1`, source
  audit, inspected contact sheets, unchanged `.g4`, no later-version
  propagation, exact final CLI installation, and a clean canonical worktree are
  required before the goal completes.
- Structural Java correspondence, selector migration, and approval coverage do
  not begin in this wave unless the user explicitly expands scope after
  reviewing the responsive evidence.

#### Wave completion record — 2026-07-22

All three tracks were merged into canonical `1.19.2`: viewport framework
`c33e576532246d349ab85ba2f16011a9ffda50b1`, historical calibration-panel
`bb2867e24247db765e6242357580436d86ba978a`, responsive review workspace
`3cd93ddfd89901ddacf38956a7cef4a1951334c9`, and its hidden-Stack focus fix
`4db084c3ab56fd8a8e099805e54479c88256152b`. The conflict resolution preserves
both already-composed-layout and typed-panel-group multiplexer constructors.
Focusing an inactive Stack leaf now activates every Stack on its path before
input or narration dispatch.

The repository screen now owns one shared model and four real workspace leaves:
changed files, before source, after source, and comment details. Logical bounds
select wide, medium, or narrow composition; `Ctrl+M` maximizes/restores the
focused leaf. The merged declared-profile puppet visibly exercises palette
opening, browsing/search, changed-range and nonempty selection evidence,
complete comment details, close, reopen, and exactly one restored user comment.
The size-display puppet supplies full, half, equal-thirds, and nested allocation
proof. Together they completed 30 scenarios and 210 captures in one Minecraft
process; the browsable contact sheet is the generated
`platform/minecraft/build/sfm-toolchain/artifacts/game-test-preview/index.html`.
At that checkpoint, structural correspondence and durable selector migration
had not started. Current P-3 work reaches those capabilities through explicit
revision selectors and the comment/session model, not the deleted bundle seam.

#### Size-display redesign — 2026-07-25

The former verbose viewport-calibration test card is replaced by the reusable
`SFMSizeDisplayPanel`, `SFMSizeDisplayGeometry`, and
`SFMSizeDisplayWorkspace` surface. Each leaf paints its complete host bounds in
a caller-selected opaque solid colour and centers only the live logical width ×
height. Its foreground uses the better-contrasting opaque black or white value
from the documented relative-luminance rule. The repeated window/framebuffer/
GUI diagnostics, colour bars, checkerboard, pointer coordinates, markers and
misleading diagnostic-mode presentation are removed.

The workspace remains a composable split fixture: full, half, equal-thirds and
nested horizontal/vertical allocations use distinct colours, while a narrow
dimensions source receives each leaf's allocated bounds (with deterministic
overrides available to tests). `title_screen_size_display` now captures all
four shapes with stable `size-display-*` figure ids. The preferred 1280×720 Auto run
produced a fresh four-capture proof under
`platform/minecraft/build/sfm-toolchain/artifacts/game-test-preview/runs/title_screen_siz-20260725-114601-896/`;
the captures were visually inspected for region distinction, centered text,
contrast and logical dimensions. Each leaf must report its own allocation—for
example, a half or nested leaf must not repeat the full 427 × 240 window size.

Focused size-display tests pass, and the full Java suite passes with only the
repository's existing Windows symlink assumptions aborted. Before the Vox
colour-picker flow, this leaf is the intended allocation-debug surface for
reviewing nested header-slot composition without introducing remote UI or
changing the Vox wire boundary.

#### Allocated-dimensions correction — 2026-07-25

The size-display leaf now reads the bounds allocated to that leaf. The focused
test and refreshed preferred puppet prove that half leaves show `210 × 238`
and `211 × 238`, while nested leaves show `210 × 238`, `211 × 117`, and
`211 × 117`; they no longer repeat the full `427 × 240` viewport. The proof is
under
`platform/minecraft/build/sfm-toolchain/artifacts/game-test-preview/runs/title_screen_siz-20260725-123242-832/`.

The historical first integration branch merged Tracks 1 and 3 and is now part
of canonical 1.19.2. Future concurrently developed Tracks 4, 6, or 7 should
still meet on an explicitly named integration branch after each has a coherent
commit rather than using one experimental track as another's historical base.
Track 2 contributes only its report and, if accepted, a later adapter commit.

Track 5 is not merged into the SFM integration branch. Its Facet/Vox commits are
reviewed and integrated in the maintained Facet fork first. SFM then consumes a
specific reviewed artifact or source revision through its dependency workflow
and implements the Minecraft adapter as an explicit integration task.

## Worktree and branch plan

The original worktrees below were created from their recorded common baseline.
Tracks 4, 6, and 7 branch from the current reviewed canonical 1.19.2 commit
after this planning checkpoint is committed:

| Track | Proposed branch | Proposed worktree |
| --- | --- | --- |
| Track 1 | `feat/1.19.2/screen-multiplexer` | `worktrees/1.19.2-screen-multiplexer` |
| Track 2 | `feat/1.19.2/file-drop-research` | `worktrees/1.19.2-file-drop-research` |
| Track 3 | `feat/1.19.2/file-explorer` | `worktrees/1.19.2-file-explorer` |
| Track 4 | `feat/1.19.2/action-hotkeys` | `worktrees/1.19.2-action-hotkeys` |
| Track 6 | `feat/1.19.2/review-diff` | `worktrees/1.19.2-review-diff` |
| Track 7 | `feat/1.19.2/panel-observation` | `worktrees/1.19.2-panel-observation` |
| Integration | `feat/1.19.2/review-workspace` | `worktrees/1.19.2-review-workspace` |

### Completed integration wave — 2026-07-22

Three fresh follow-up worktrees were created from reviewed canonical commit
`9860e924b`. Their agents own implementation, tests, isolated puppet evidence,
and clean feature commits; the coordinator owns this plan, review, canonical
merges, conflict resolution, and merged-head proof.

| Follow-up | Branch | Worktree | Acceptance target |
| --- | --- | --- | --- |
| Source-review ledger v2 | `feat/1.19.2/review-ledger-v2` | `worktrees/1.19.2-review-ledger-v2` | Current multiplexer comparison UI, independent reviewed/approved/audit states, persisted and stale ledger decisions, captioned puppet |
| Structured Theme Settings | `feat/1.19.2/theme-settings` | `worktrees/1.19.2-theme-settings` | Colour and ItemStack role editing through the merged pickers, live preview, atomic TOML persistence, invalid-theme retention, captioned puppet |
| Key-mapping settings | `feat/1.19.2/keymap-settings` | `worktrees/1.19.2-keymap-settings` | Search and runtime binding management, compact multi-binding palette presentation, typed incomplete-command flow, deterministic replay, captioned puppet |

The wave completes only after all accepted branches are merged into canonical
`1.19.2`, canonical compile and full tests pass, merged-head puppets are
visually inspected, and the canonical worktree is clean. It does not authorize
Minecraft-version propagation.

#### Integration result — 2026-07-22

The three accepted feature heads were merged into canonical `1.19.2` through
`c81cdc4dd` (source-review ledger), `27788dcf3` (key-mapping workflow), and
`7e730b206` (Theme Settings). Independent branch validation and puppets passed
before integration.

- Track 4 now has responsive runtime binding management, deterministic compact
  multi-binding cycling, shared contextual execution, replay tests, and a
  Brigadier-derived prompt/properties/confirmation flow. It remains partial
  because bespoke typed widgets/defaults, literal-choice prompting, and the
  preferred-editor round trip are deferred.
- Track 6 now has the fixture-driven comparison surface, conservative line
  fallback, independent review/approval/audit presentation, atomic local JSON
  persistence, SHA-256 witnesses, restoration, and visible stale invalidation.
  It remains partial until real Git/snapshot adapters, semantic Java
  correspondence, span-level identities, and scalable source navigation land.
- The Theme Settings follow-up connects semantic colour, syntax, file-icon,
  and action-icon drafts to the reusable colour and ItemStack pickers, live
  preview, validated atomic TOML persistence, reset/default controls, and
  last-valid-theme retention.

Merged-head compile and the full Java suite passed. The only two aborted tests
were the existing Windows symlink privilege assumptions. Fresh canonical
source-review, Theme Settings, and dynamic-key-binding puppets passed at
1200x720, and their restored/stale, invalid-retention, and typed-confirmation
frames were visually inspected. The wave is complete. Later Minecraft-version
propagation remains out of scope.

Track 5 created the coordinator-owned Facet integration branch
`teamy/vox-java` and worktree
`G:\Programming\Repos\facet-worktrees\vox-java` from published Facet checkpoint
`05263dd6d119d271a21883636b23db6d45a2466f`. The frozen Java 17 contract is
`vox/docs/design/java-17-vertical-slice.md`; its clean pushed delegation
checkpoint is `58c47981a1c171b07a30449db5a550a9029b0709`.

Every delegated Facet worktree starts separately from that exact immutable
checkpoint and verifies `HEAD` before an agent is attached:

| Facet boundary | Branch | Worktree | Status |
| --- | --- | --- | --- |
| Phon Java | `teamy/vox-java-phon` | `G:\Programming\Repos\facet-worktrees\vox-java-phon` | Integrated as `a2ed54681` |
| Java generators | `teamy/vox-java-codegen` | `G:\Programming\Repos\facet-worktrees\vox-java-codegen` | Integrated through `31018976c` and `1b0b255d0` |
| Vox Java runtime | `teamy/vox-java-runtime` | `G:\Programming\Repos\facet-worktrees\vox-java-runtime` | Integrated as `2bbd7e32f`, then reconciled with the generated APIs |

The independent SFM packaging-capability investigation uses branch
`feat/1.19.2/vox-packaging-research` and worktree
`D:\Repos\Minecraft\SFM\worktrees\1.19.2-vox-packaging-research`. It may inspect
and prove loader behavior before a final Vox artifact exists by using a harmless
probe library. It reports per-version Jar-in-Jar support, metadata, selected
publication artifact, clean-instance loading and fallback recommendations; it
does not silently change production packaging.

After upstream conformance and artifact freeze, the Minecraft adapter uses
branch `feat/1.19.2/vox-bridge` and worktree
`D:\Repos\Minecraft\SFM\worktrees\1.19.2-vox-bridge`.

### Active Track 5 delegation briefs — 2026-07-23

The synchronization and contract gates were satisfied at the immutable SHAs
above. V1, V2 and V3 were attached only after each separate worktree was
verified clean and exactly at the contract checkpoint.

#### Integration result — 2026-07-23

V1, V2 and V3 are complete and integrated on `teamy/vox-java`. The maintained
Facet fork's reviewed code and frozen-artifact commit is
`5e719ed9f5d1f36ba41242c2c057e74d67ecb3c9`. The branch's current pushed head is
documentation-only correction `21f71915a`, which clarifies that the Java 17
baseline permits records and sealed types while excluding virtual threads and
language/library APIs introduced after Java 17. The integration sequence
includes the three delegated heads plus full generated-response adapters,
packaging/test xtasks, both wire directions, a Java-hosted service, runtime
wire schemas, and negative and schema-evolution conformance.

`cargo xtask package-java` proves 72 Phon assertions, stream framing, the Vox
runtime, generated responses, deterministic assembly, a clean consumer smoke
and `jdeps`. It creates one Java 17 artifact:

```text
G:\Programming\Repos\facet-worktrees\vox-java\vox\java\target\vox-java-0.10.0-rc.5.jar
SHA-256 E714A48080D453097F1E819DEFBE42412B73F8E17C1B5D0AA4A6BEA52DF734C3
```

The artifact contains the runtime-generated `HandshakeWireSchemas` and
`MessageWireSchemas` classes but excludes Testbed application services.
Package-scoped formatting and focused generator/xtask Clippy with warnings
denied pass. Full-workspace formatting remains subject to the existing Windows
path-length limitation. Run `package-java` before the wire suite because the
wire subjects intentionally consume its generated `java/target/test-classes`
rather than maintaining a second generated fixture surface.

The real Rust/Java TCP suite passes ten behavioral cases:

1. Java caller to Rust echo;
2. Rust caller to Java-hosted echo;
3. bidirectional calls on one connection;
4. cancellation/timeout aborting the Rust handler;
5. disconnect terminalizing a pending call and letting Java exit;
6. invalid payload rejected before dispatch;
7. application errors in both directions;
8. the normative unknown-method outcome;
9. compatible schema evolution; and
10. incompatible argument schema rejected before dispatch.

Channels/streaming, a separately isolated incompatible-response-schema case,
richer authorization policy, and the Minecraft bridge remain later slices.
The unary Java 17 vertical slice and its artifact boundary are frozen.

The SFM cross-loader packaging foundation is canonical commit
`31d788969b743b774d86b451f83a0136612ef14f`. It introduces schema-v3 bundle
policy fields for accepted ranges, exact artifact version and obfuscation,
preserves those fields in Gradle and Rust packaging, fingerprints policy, and
audits the installed JAR. The Rust CLI gate passes with 344 tests and one
ignored test. It propagated cleanly to every maintained Minecraft branch:

| Branch | Propagated head |
| --- | --- |
| `1.19.2` | `31d788969b743b774d86b451f83a0136612ef14f` |
| `1.19.4` | `8fedc8ee0` |
| `1.20` | `b1daa4e03` |
| `1.20.1` | `84a04ff49` |
| `1.20.2` | `4ad6a3e2a` |
| `1.20.3` | `a47aecbb2` |
| `1.20.4` | `c5bbe031f` |
| `1.21.0` | `19323ef9a` |
| `1.21.1` | `ef0f8feb5` |
| `26.1.2` | `7742f3c4b` |

The 26.1.2 ANTLR declaration is explicitly `[4.13.1]`, version `4.13.1`,
unobfuscated. Post-propagation version-surface audit has zero warnings and all
named worktrees are clean.

The branch-only SFM artifact probe
`teamy/vox-java-artifact-probe` freezes the Facet artifact above as
`org.facet:vox-java:0.10.0-rc.5` with exact accepted range
`[0.10.0-rc.5]`. Its definitive Rust-built SFM JAR has SHA-256
`8E2B3D9789B0AC089EB660A0888C53B1F0FF9CC5DFE8A4D7A345F6838A34B733`;
the single nested JAR has the exact input SHA-256 above and locked BLAKE3
`7fe683de7c2400366b8dfa42d777fcb5aefdb9b4`. Artifact audit verifies 109
entries with no errors and one expected non-portable-source warning, and the
userdev smoke directly invokes the nested public API.

The artifact probe's lock, runtime marker and `LocalImport` implementation must
not be merged to canonical. They add a new semantic variant without the
required schema bump, and the lock-path parser is still host-dependent:
portable lock paths must use `/` and lexically reject backslashes, drives, UNC,
absolute, empty, `.` and `..` components before conversion to a host
`PathBuf`. Promote this only as schema v4, with platform-independent tests,
explicit empty-cache rehydration and hosted/source-build provenance. The
canonical architecture note records the useful evidence without exposing the
experimental command surface.

The hardened clean production Forge loader proof is complete and its generic
harness is canonical through `70cffb5ce`. The command launched Forge 43.4.0 in
an isolated instance containing exactly the final SFM JAR. It verified both
expected hashes, rejected loose Vox copies and direct/inherited classpaths,
observed exactly one Forge Jar-in-Jar dependency, and correlated
`org.facet.vox.VoxResult` with both the nested `union:` source and Forge's
`TransformingClassLoader`. The process exited zero without timing out.

The full schema-v2 report is
`docs/architecture/evidence/clean-loader-jarjar-probe-1.19.2.full.json`,
SHA-256
`76909EE81439977A49CDDE0C47356E42D8D7C9B693F631FFC7944AC17CFE1844`.
Its adjacent evidence manifest has SHA-256
`07EE0B05E5D57D234ABA5F196C4AE12629BE31E4CA4191BA3D1F6A19B3048710`
and records the installer and launch-log hashes. The harness requires a known
CLI source revision, the exact release and installer hashes, a successful
non-timeout exit, and bounded process-tree cleanup, so an incomplete or
unattributed run cannot be mistaken for proof.

This completes the Java 17 unary artifact and production-loader wave. It does
not merge the branch-only probe code, lock, or schema-v3 `LocalImport`.
Concrete next implementation work is schema-v4 portable acquisition or the
narrow Minecraft Vox bridge (endpoint lifecycle, one generated service, an
action/panel, and puppet-visible success/failure states). The preferred first
user-facing bridge is now the terminal capability rather than the colour-picker
demo: it must provide a Java-local virtual-terminal fallback, then use the same
typed service through Vox/Rust when available. The detailed scope, Teamy Studio
reference seams, Java/Rust ownership boundary, capability matrix, and puppet
proof are recorded in [Vox Terminal Bridge and Graceful Degradation Plan](vox%20terminal%20bridge%20and%20graceful%20degradation%20plan.md).
The bridge must consume the frozen artifact rather than copying its protocol by
hand.

Before either implementation begins, perform a deliberate post-implementation
review of the completed Phon/Vox Java slice. This is a fresh-eyes review rather
than another feature pass. Check the Java 17 contract and documentation for
incorrect platform assumptions like the corrected records/sealed-types claim;
review public API ergonomics, generated-versus-handwritten ownership, protocol
fidelity, schema negotiation, bounds, concurrency, cancellation, shutdown,
error surfaces, deterministic generation and packaging; and identify missing
negative, lifecycle and clean-consumer tests. Record findings by severity and
separate required corrections from future enhancements. Rebuild and republish
the frozen artifact only if code or generated output changes.

The bridge planning that follows must preserve a strict capability boundary:

- SFM gameplay and ordinary in-game functionality remain self-sufficient on
  the Java/Minecraft side. They must not require a Rust process.
- The mount workflow should work with only the mod and Java so a player can
  move seamlessly between an in-game disk and VSCode without installing or
  launching the Rust development toolchain.
- Vox is an optional development-environment bridge for capabilities that
  genuinely cross the game/process boundary: building SFM source from an
  in-game command-palette action, invoking repository/compiler/audit tooling,
  presenting richer external interfaces, and returning structured progress,
  diagnostics and results.
- Every bridged action declares availability and requirements. An unavailable
  endpoint produces an explanatory disabled or launch-suggestion state rather
  than weakening unrelated mod functionality.
- Shared intent/result schemas may be Java-native and reusable locally; only
  the adapter that transports them out of process depends on Vox.

The capability matrix is now the first phase of the terminal bridge plan. It
has rows for mount, disk editing, formatting, parsing, compiling, testing,
auditing and source builds, and columns for Java-only implementation, optional
Vox enhancement, external prerequisites, failure behavior and observable
in-game proof. This keeps the bridge additive instead of quietly turning the
Rust CLI into a runtime dependency of the mod.

The canonical clean-loader commits and completed evidence were propagated
through the maintained version chain. The clean propagation checkpoint heads
are `90a89d692` (1.19.2), `6bc6347e3` (1.19.4), `3355a19b9` (1.20), `1dd5a5974`
(1.20.1), `8e2179256` (1.20.2), `f37703eb0` (1.20.3), `b9685f7f2`
(1.20.4), `3f38cc2ea` (1.21.0), `e566751ea` (1.21.1), and `929fdf38c`
(26.1.2). The version-surface audit reports zero CLI-source divergence
warnings; its Java warnings are the existing cross-version Java differences,
not changes introduced by this CLI/docs-only propagation.

#### Coordinator preflight — Facet synchronization and contract freeze

Owner: primary agent; no delegation.

1. fetch `origin` and `mine` in the clean maintained Facet checkout;
2. merge current `origin/main` into maintained `main`, resolve by project intent,
   run required gates, commit and push `mine/main`;
3. create and verify the `teamy/vox-java` integration worktree from that exact
   pushed SHA;
4. re-survey upstream Java/Phon support;
5. write the Java 17 supported-subset, module/package, runtime-interface,
   generated-API, artifact and conformance contracts in Facet-owned docs/spec;
6. correct stale Java documentation as appropriate; and
7. gate, commit, push and publish the immutable delegation SHA.

Do not attach implementation agents before step 7.

#### Agent V1 — Phon Java and conformance

Own only `phon/java`, its Java test harness and the minimum Rust-side fixture
emission explicitly assigned by the frozen contract. Implement canonical
schemas/values, schema closures, schema ids, compatibility plans, typed adapters
and bounds. Prove relevant conformance cases byte-for-byte, matching ids,
evolution and malformed inputs under `javac --release 17`. Do not implement Vox
connections, SFM code or change the frozen generated public interface without a
coordinator decision.

#### Agent V2 — Java source generation

Own the Rust Java targets, deterministic generated fixtures and generator
documentation. Implement Phon DTO/schema generation and Vox service caller,
handler, dispatcher and descriptor generation against the frozen Java runtime
interfaces. Add a real xtask flag, drift checks, Java 17 compilation and clear
unsupported-shape diagnostics. Do not invent a second Java runtime or edit SFM.

#### Agent V3 — Vox Java runtime and hosted subject

Own `vox/java` runtime/subject code and focused interop scaffolding. Implement
bounded TCP framing/prologues, self-describing handshake, explicit connection
driver, service lanes, schema binding, unary request/response correlation,
cancellation, timeout, shutdown and subject inactivity/disconnect exit. Keep
connection/lane/schema state machines together. Use hand-written fixture DTOs
until V1/V2 land; do not fork their codec or generated APIs.

#### [x] Agent P1 — SFM Jar-in-Jar capability research

Research commit `c2504223bb9c4b2dd66d1b3de6155a9fec1bd54d` was completed
from canonical SFM commit `1bad3a53e7c888f91b83e2048ba2d6ae2d6f9eaf`
and integrated into 1.19.2 as `71d203a2a`. It produced the cross-version
capability and gap analysis linked from section 5.4 without changing production
ANTLR or Vox declarations. The next packaging work is coordinator-owned:
teach schema-v3 and the Rust packager to preserve loader-compatible dependency
ranges, select bundled publication artifacts on every supported loader, and add
installed-JAR inspection plus clean-instance launch/call acceptance.

#### Coordinator integration and proof

Integrate V1, then V2, then V3 into `teamy/vox-java`, resolving API mismatches in
the integration branch rather than making agents rewrite history. Run the
complete focused Rust/Java conformance matrix and freeze one reviewed artifact.
Use P1's result to implement SFM bundling through the lockfile workflow. Only
then dispatch the SFM Vox bridge/action/panel/puppet work.

Each worktree has one agent owner at a time. Agents may commit within their own
track so progress is durable and reviewable. They must not modify, clean, or
merge the existing `feat/1.19.2/draw` or `feat/1.19.2/mount` worktrees. The
mount worktree is clean after checkpoint commits `ee7c14b44` and `3d3cad055`.
Those branches remain reference evidence until explicitly brought into scope.

Normal Minecraft-version propagation begins only after an integrated 1.19.2
slice is reviewed and accepted. Experimental feature branches are not passed to
`sfm-propagate-changes.exe git merge`.

## Coordinated subagent operating model

This task remains the user-facing coordination thread. The primary agent owns
the plan, shared decisions, dependency contracts, integration, validation, and
status reporting. Bounded track work can be delegated to subagents, with one
subagent assigned to one worktree and explicit deliverables.

### Canonical plan and single-writer rule

The canonical copy of this plan is
`D:\Repos\Minecraft\SFM\repos2\1.19.2\docs\tasks\in-game code review workspace and window manager plan.md`
on the core `1.19.2` branch. The user and the coordinating agent are its only
writers. The coordinator preserves and reconciles direct user edits rather than
overwriting them.

Subagents must not edit this plan in their feature worktrees, even though the
file exists there from the common baseline. Those copies are read-only context
and may become stale. Subagents report status, commit ids, changed files,
validation and CLI epoch evidence, decisions, blockers, cross-track assumptions,
and proposed plan wording through the coordination thread. The coordinator
verifies that evidence and updates the canonical `1.19.2` plan.

Plan changes found in a track branch are not merged into the integration branch
as authoritative state. If a track needs implementation-local documentation,
it may edit a separate document owned by that implementation; cross-track task
state still belongs here. The same rule applies to Track 5: its agent may update
Facet/Vox-owned technical documentation, but only the coordinator records its
status in this SFM plan.

Subagents should report findings and commit ids back here. The primary agent
reviews cross-track assumptions, resolves interface disagreements before
integration, and records durable outcomes in this document. User input is
requested here when a choice changes product behavior, release scope, security,
or architecture; routine implementation details remain delegated.

Do not start all tracks merely because they are parallelizable. Before dispatch,
record the common baseline commit, create the named worktrees, give each track
an acceptance target, and decide whether Track 2 is research-only or authorized
to add a proof-of-concept callback. The current agent pool permits three
subagents alongside the coordinator, so five tracks require deliberate waves
or research retained by the coordinator; parallelizable does not mean all five
start simultaneously. Track 5 also has a mandatory upstream-sync gate before it
is eligible for delegation.

## Shared CLI epoch

### [x] CLI epoch E1 — Canonical 1.19.2 CLI installed

**Established:** 2026-07-20  
**Canonical source commit:** `706933b0521288317e70591cc9f5133d6d25fcc4`  
**CLI source tree:** `900a173ff3a05d23ecdf4b94172d67e7d5a35959`  
**Reported version:** `sfm-propagate-changes.exe 0.1.1 (rev 706933b05, built 2026-07-20 18:54:36 -04:00)`  
**Installed executable:** `G:\Programming\Caches\CARGO_HOME\bin\sfm-propagate-changes.exe`  
**SHA-256:** `391D762D09228F1B19C686C29CB25000CAC0F168CA6FE4C300A0B1DCA73CDD81`

The canonical `check-all.ps1` gate passed its dependency policy, formatter,
Clippy, build, and test stages: 312 tests passed, zero failed, and one was
intentionally ignored. `install.ps1` then replaced the PATH executable using
`cargo install --path . --locked --offline`. The canonical CLI source tree
remained clean.

Java-only experimental tracks use this PATH executable and verify its reported
revision or hash before validation. Only the coordinator replaces the shared
installation.

The older `feat/1.19.2/mount` branch demonstrated the exception policy. Epoch
E1 rejects that branch's pre-schema-v3 lockfile before Java compilation, while
the branch-local CLI successfully runs its older `run compile` and `run test`
surfaces through `cargo run -- ...`. A track with deliberately older or locally
modified Rust CLI sources therefore uses its local CLI for validation without
installing it globally, reports the incompatibility here, and waits for an
accepted canonical CLI update before changing the shared epoch.
