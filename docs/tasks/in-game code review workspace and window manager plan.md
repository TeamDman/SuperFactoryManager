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

---

# In-game code review workspace and window manager plan

**Plan status:** Intake; planning decisions not yet made  
**Primary planning root:** `D:\Repos\Minecraft\SFM\repos2\1.19.2`  
**Related reference worktrees:** `feat/1.19.2/draw`, `feat/1.19.2/mount`  
**Last updated:** 2026-07-20

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

## Immediate next step

### [ ] 0.1 Establish vocabulary, ownership boundaries, and the first release slice

Before implementation, inspect the existing audit, Arborium/JavaParser, Draw,
mount, console/palette, editor, and Minecraft screen/overlay seams. Turn that
evidence into explicit design choices for:

1. what artifact stores reviewed state and how it survives new commits;
2. the granularity and identity of a reviewable unit across edits;
3. which operations live outside Minecraft versus in the in-game workspace;
4. whether panels host full `Screen` instances or narrower panel/content
   interfaces adapted from screens; and
5. which one end-to-end workflow proves enough value to belong in the next
   release.

## Parallel experiment tracks

The track names below are durable coordination handles. A track may investigate
and commit independently without implying that its result is accepted into the
release. Cross-track dependencies should be expressed as contracts and small
integration commits rather than by allowing multiple tracks to edit one
worktree concurrently.

### [~] Track 1 — Screen multiplexer and typed screen-opening actions

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

Track 1 remains in progress rather than accepted. It still needs a live
client/puppet visual and lifecycle proof, a host-intent contract for panel
close/split/open requests, and a decision on whether any audited full-screen
adapter belongs in scope.

### [~] Track 2 — Native file drag-and-drop feasibility

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

### [~] Track 3 — Responsive full-screen file explorer

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

### [ ] Track 4 — Dynamic hotkeys and typed command completion

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

#### [ ] 5.1 Synchronize the maintained Facet fork

The local Facet checkout was inspected read-only on 2026-07-20. It is clean on
local `main`, which tracks `mine/main`, at `5fd9cfaa4`. The cached remote refs
show `mine/main` 75 commits ahead of and zero behind `origin/main`, whose cached
tip is `3b20e02a2`. Those numbers are not evidence about the latest upstream
until both remotes are fetched.

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

#### [ ] 5.2 Re-survey Java support after synchronization

Current local evidence says Vox does not yet have an implemented Java target:

- `vox/README.md` lists Rust, TypeScript, and Swift support;
- `vox/rust/vox-codegen/src/targets/mod.rs` exports only `swift` and
  `typescript`;
- `vox/DEVELOP.md` documents `cargo xtask codegen --java`, and
  `vox-codegen/src/lib.rs` claims Java support, but no corresponding target or
  Java runtime is present in the checkout.

After merging the latest upstream, repeat the survey across runtime, wire
codec, transports, code generation, generated fixtures, conformance tests, and
published artifacts. Treat stale documentation as a discrepancy to correct,
not as proof of support. If upstream now supplies Java, evaluate and extend it;
otherwise begin the experiment below.

#### [ ] 5.3 Implement the smallest conforming Java experiment

Build from the Vox specification and golden vectors rather than translating
the Rust implementation by intuition. The first vertical slice should include:

- Java DTO/service generation from the same Rust `ServiceDescriptor` and Facet
  shapes used by existing TypeScript and Swift targets;
- the minimum Vox wire codec and connection/runtime needed for unary,
  bidirectional request/response;
- one loopback transport chosen from source evidence—prefer an existing
  cross-language TCP or WebSocket protocol over JNI for the first proof;
- Java↔Rust conformance tests using Vox schema compatibility, wire fixtures,
  correlation ids, errors, cancellation/timeouts, and version negotiation; and
- a publishable Java artifact boundary suitable for Minecraft 1.19.2's Java
  runtime constraints without leaking Rust implementation details into SFM.

Channels, file-descriptor passing, every transport, Android-specific packaging,
and full language parity may follow after the unary bidirectional proof. The
experiment must state its supported subset rather than silently accepting
unsupported service shapes.

#### [ ] 5.4 Prove shaped intent fulfillment across Java and Rust

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
capability/token, version-negotiated, bounded, and explicit about which actions
may cross the process boundary. Network callbacks never directly mutate
Minecraft state; they enqueue onto the appropriate game/client thread. eframe
owns its own event loop and does not borrow Minecraft's GLFW context.

#### [ ] 5.5 Explore coordinated Minecraft-window behavior

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

## Track dependencies and integration order

```text
Track 1: multiplexer + typed screen registry ─────┐
                                                  ├─> first integrated workspace
Track 3: responsive explorer + source provider ───┘

Track 2: drag/drop evidence ──> optional source-provider adapter

Track 4: dynamic hotkeys + command drafts ──> shared command/prompt surface
                                             └─> optional Track 1 panel adapter

Track 5: Vox Java + shaped intent UI ──> optional external fulfillment for Track 4
                                      └─> bidirectional SFM action bridge
```

Tracks 1 and 3 are intentionally parallel: Track 3 targets a normal `Screen`
and publishes a narrow responsive-content contract; Track 1 proves whether and
how such content can be embedded. Track 2 is independent research and must not
block either track. Track 4 depends only on the existing client action and
command-palette substrate; its completion UI is a normal responsive screen
until Track 1 supplies an accepted embedding contract. Track 5 has its own
Facet repository lifecycle and does not block the in-game prompt implementation;
it later supplies an optional external fulfillment adapter and action bridge.

Integration should occur on a dedicated integration branch after each track
has a coherent commit. Prefer merging Tracks 1, 3, and 4 into that branch over
merging one experimental track into another: no experiment should silently
become another's historical base. Track 2 contributes only its report and, if
accepted, a later adapter commit.

Track 5 is not merged into the SFM integration branch. Its Facet/Vox commits are
reviewed and integrated in the maintained Facet fork first. SFM then consumes a
specific reviewed artifact or source revision through its dependency workflow
and implements the Minecraft adapter as an explicit integration task.

## Worktree and branch plan

Create worktrees only after this plan and the common client-action baseline are
committed. Branch every experiment from that same reviewed baseline:

| Track | Proposed branch | Proposed worktree |
| --- | --- | --- |
| Track 1 | `feat/1.19.2/screen-multiplexer` | `worktrees/1.19.2-screen-multiplexer` |
| Track 2 | `feat/1.19.2/file-drop-research` | `worktrees/1.19.2-file-drop-research` |
| Track 3 | `feat/1.19.2/file-explorer` | `worktrees/1.19.2-file-explorer` |
| Track 4 | `feat/1.19.2/action-hotkeys` | `worktrees/1.19.2-action-hotkeys` |
| Integration | `feat/1.19.2/review-workspace` | `worktrees/1.19.2-review-workspace` |

Track 5 uses a separate Facet worktree whose final path and branch are chosen
after `mine/main` is synchronized and pushed. Proposed names are branch
`teamy/vox-java` and worktree `G:\Programming\Repos\facet-worktrees\vox-java`.
The recorded post-merge commit SHA—not a moving branch name—is its creation
base, as required by the Facet repository instructions.

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
