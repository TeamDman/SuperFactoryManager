# Spatial semantic surfaces, outlinks, and capability presenters plan

**Plan status:** Active; SS-0 through SS-6, NX-3a, NX-4, NX-4a, and CP-3 are
complete; NX-1/NX-2/full NX-3, CP-1+, SS-7, and SS-8 remain
**Primary implementation root:** `D:\Repos\Minecraft\SFM\repos2\1.19.2`
**Primary implementation target:** Minecraft 1.19.2
**Last updated:** 2026-08-18
**Authoritative scope:** Spatial interaction classification, semantic regions,
outlink relations, source-navigation coverage, generalized implementation
selection, and rich action previews
**Related contextual-action plan:**
`docs/tasks/contextual input actions and addressable explorer plan.md`
**Related selection/explorer plan:**
`docs/tasks/typed selections relations and lazy explorers plan.md`
**Related window-manager plan:**
`docs/tasks/in-game code review workspace and window manager plan.md`
**Related editor plan:**
`docs/tasks/draw editor document regions and commands plan.md`
**Related comment plan:**
`docs/tasks/global comment selection and review sessions plan.md`
**Related episode/preview plan:**
`docs/tasks/snapshot episodes and deterministic action environments plan.md`
**Related release plan:**
`docs/tasks/release checkpoint and slim artifact plan.md`

## How to update this plan

- `[ ]` Not started
- `[~]` In progress
- `[x]` Complete
- `[!]` Blocked

Keep stable ids. Do not mark a task complete from compilation alone. Each
completed task must retain the exact command, result, artifact, source commit,
and any intentionally unsupported classification. Work on 1.19.2 first. Do
not invoke Gradle directly, propagate, publish, push, or create a release tag
merely because a slice is complete.

This plan follows the resumable-implementation-plans protocol. A fresh agent,
including a smaller implementation model, must be able to distinguish verified
foundation, active decisions, reversible recommendations, unresolved gates,
work, validation, and completion evidence without reconstructing this
conversation.

## Purpose

Make spatial interaction a first-class, testable relation rather than a chain
that happens to begin with a string offset. A person points at a rendered
canvas location. The system must be able to explain:

1. which geometric and textual regions contain that point;
2. which semantic objects those regions represent;
3. which zero or more outlinks/actions are offered and why;
4. which destination region an outlink names;
5. how that region should be framed or projected when opened; and
6. whether every relevant part of the rendered document has been explicitly
   classified and tested; and
7. whether the strict navigation profile gives every rendered Java glyph a
   deliberate Ctrl-click navigation route, rather than merely some unrelated
   contextual action.

Java is the first supported semantic language and Rust is a later adapter. The
model must still preserve useful one-dimensional byte, code-point, glyph,
UTF-16, row/column, and source-span projections. They are derived views of the
canvas/document relationship, not substitutes for the two-dimensional user
interaction that actually occurred.

The same foundation should support more than source navigation. Paths, URLs,
registry entries, comments, editor selections, explorer locations, future
screen rays, and contributed mod domains can expose contextual actions through
small registered providers. Text-editor choice is one instance of a general
capability-provider system. Command actions can also contribute bounded rich
previews, including later animated or virtual-world presentations, without
letting preview code take over the palette or render loop.

## Observable long-term end position

- Every rendered non-whitespace glyph of a successfully parsed Java document
  has an explicit semantic classification and at least one deliberate
  navigation outlink under the strict Java-navigation coverage profile, unless
  an individually witnessed typed exception has been approved. A contextual
  action which cannot navigate does not satisfy navigation coverage.
  Whitespace, padding, malformed/unindexed text, and canvas space outside the
  document have explicit typed outcomes; nothing silently falls through as
  “not a Java identifier.”
- Holding Ctrl over a navigable region underlines the complete applicable
  region and uses the link pointer. Ctrl+click follows the sole preferred
  outlink or opens the shared constrained command-palette choice when multiple
  viable destinations remain. Right-click and Alt+Enter expose the same
  provider results rather than maintaining separate callback systems.
- Braces, punctuation, declarations, annotations, imports, qualified names,
  fields, methods, variables, literals, comments, and other Java syntax are
  covered by deliberate rules. For example, an opening method-body brace may
  offer the containing method, matching closing brace, body region, and
  statement children. This is never an accidental nearest-token fallback.
- Coverage can be computed exhaustively on bounded fixtures and sampled
  reproducibly on large canvases. A versioned machine report and visual heatmap
  separately show classification, navigation-outlink, gesture-route, branch,
  boundary, and reciprocal-link coverage, plus the next highest-value untested
  region. A workspace report inventories every supported-language file so a
  missing, skipped, stale, or failed file cannot disappear from the
  denominator.
- Definitions and references are checked bidirectionally. A reference that
  links to a definition is represented in that definition's reference relation,
  subject only to explicit ambiguity/dynamic/unsupported exceptions.
- Editor content displays its exact address. Navigation has mouse back/forward
  support and a branching, event-sourced history that does not destroy an old
  forward branch when the user goes back and navigates elsewhere.
- Successful navigation records its viewport decision. When the destination
  landmark and the document/line left edge fit together, both remain visible
  with positive inset; when they cannot fit, the target remains usable and the
  renderer preserves the maximum deterministic leading context while reporting
  why the left edge could not be shown. Mid-document jumps do not force the
  document's top edge into view.
- Explorer files and directories expose contextual actions such as Copy as
  Path and Open as Root in New Explorer. Java files may expand lazily into a
  semantic hierarchy rather than remaining terminal leaves.
- User feedback uses a generic actionable toast surface whose lifetime is
  readable and controllable rather than flashing for a frame; a user can copy,
  pin, or dismiss the exact addressed toast without suppressing later messages.
- Capability implementations and action previews are contributed through
  bounded registries. Preferences select defaults, explicit commands can name
  an implementation, and an ask/choose path remains available.

## Ownership and cross-plan boundaries

| Concern | Authoritative owner | This plan's relationship |
| --- | --- | --- |
| Canvas/document region algebra, outlink relation, coverage oracle, target projection | This plan | Defines and implements the shared contracts. |
| Keyboard gestures, constrained palette, contextual action drafts, hotkey lookup | `contextual input actions and addressable explorer plan.md` | Consumes semantic outlinks through the existing action/palette path. K-8 remains independent. |
| Typed paths, set-valued entity selectors, named selection revisions, lazy explorer membership | `typed selections relations and lazy explorers plan.md` | Adds typed spatial/semantic-region adapters; does not create a second path or selection ledger. X-9 and X-10 are join points. |
| Text Editor v3 canvas, glyph storage, camera, and editor commands | `draw editor document regions and commands plan.md` | Uses its canvas/index and adds interaction classification; does not replace the editor model. |
| Pane, stacked entry, component identity, layout and panel hosting | typed-selection X-10 plus the window-manager plan | Adds spatial pane selectors and per-pane navigation history after identity terminology is frozen. |
| Global comments, review selectors, and approvals | `global comment selection and review sessions plan.md` | Comments consume pinned region selections/outlinks; this plan does not invent another comment schema. |
| Java source indexing, definitions/usages, refactoring reports | `cli ast refactoring suite plan.md` | Extends/consumes the CLI semantic index; Java parsing and dependency resolution stay CLI-owned. |
| File/explorer ItemStack presentation | Contextual-plan C-4a | NX-3a adapts reference-result rows to the completed chest/paper/extension presenter and must not create a second icon registry or change the generic fallback for unrelated domains. |
| Workspace toast baseline and scale-feedback producer | Release-plan P-2.6 plus contextual K-7 | NX-4 owns the generic addressable queue/input/lifecycle evolution. It migrates the existing scale toast without regressing auto/effective text, fade, or boundary shake. |
| Generic recorded episodes, seekable timeline panels, deterministic replay | `snapshot episodes and deterministic action environments plan.md` | Reuses episode/time contracts for animated previews instead of inventing a second timeline. |
| Release guardrails and human approval | `release checkpoint and slim artifact plan.md` | Navigation/coverage evidence becomes a release input; it never grants human approval. |

No existing task is silently superseded. If a task in another plan becomes a
join point, both plans must link the same id and only one plan may own its
completion status.

## Verified foundation — 2026-08-17

- `SFMContextSpatialProjection` already preserves a provider-neutral coordinate
  space, optional screen point, arbitrary ray, and optional hit/address. The
  ray is not forced through screen centre.
- `SFMContextCanvasTextMap` maps rectangular canvas hit regions to
  `SFMTextDocumentPosition`.
- `SFMContextPosition.Canvas` preserves true canvas x/y and an optional text
  hit. `SFMContextSelectionProjection` currently stores named lists of text
  ranges.
- `SFMContextSpatialProjectionTests` and
  `SFMTextEditorContextProjectionTests` cover arbitrary rays, Unicode/CRLF
  mapping, true canvas whitespace without an invented text hit, and multiple
  cursors/selections.
- `SFMDrawCanvasDocumentIndex` already exposes immutable content bounds,
  ordered glyphs, exact `glyphAt(x,y)` lookup, glyph ordinals, UTF-16 offsets,
  and row-bounded visible queries. Its focused tests cover Unicode/CRLF and
  glyph-edge hit behavior.
- `SFMDrawCanvasScreen.symbolHitAtScreen` starts from the real canvas hit, but
  then accepts only `Character.isJavaIdentifierPart` spans plus a narrow `@`
  case. Braces, semicolons, dots, commas, operators, whitespace, and other
  regions cannot reach a semantic provider through the current hover path.
- Workspace pointer coordinates pass through `SFMScreenMultiplexer.localMouse`
  before the panel/canvas transform. Existing index tests do not exercise that
  complete screen -> scaled panel -> camera/zoom -> canvas path.
- `SFMTextEditorPanel.refreshHoverTarget` and `captureHoverAt` depend on that
  identifier-only `SymbolHit`, explaining the present underlining/navigation
  gaps even when the underlying analyzer might understand the source.
- Current Ctrl+click interception begins only after hover state is already
  `ACTIONABLE`, and “actionable” currently means exactly one definition. A
  Ctrl+click while lookup is pending/unresolved can fall through to ordinary
  canvas multi-cursor input; ambiguity is currently presented as non-link
  rather than a destination choice.
- `SFMSymbolHoverStateMachineTests` thoroughly cover cancellation, stale
  results, bounded cache, modifiers, Unicode spans, and click/drag fallback,
  but they inject target ranges directly. They are not screen-coordinate
  coverage. `SFMDrawCanvasDocumentIndexTests` cover model coordinates and
  half-open glyph edges, not workspace scale/camera routing.
- `SFMDefinitionResult` schema 3 has symbols, definitions, diagnostics, and
  source spans. It does not yet expose a general semantic-region graph,
  outlink provenance, reciprocal relation, or whole-document interaction map.
- `SFMClientAction` already exposes title, description, optional ItemStack icon,
  requirements, and command nodes. The command palette renders the icon. There
  is no general rich-preview contribution contract yet.
- `SFMScreenMultiplexer.WorkspaceToast` is a single narrow 2200 ms message used
  for scale and navigation feedback. It fades/shakes, but has no hover pause,
  progress lifetime, copy, pin, contextual actions, queue, durable id, or
  narration.
- `SFMTextEditorPanelOpenContext` carries document path/snapshot, read-only
  state, title, and optional target range. The panel does not provide a visible
  canonical document-location control.
- The explorer already has a contributed file-presentation registry and
  ItemStack icon renderer. Directory/file/reference views must reuse it rather
  than reintroducing `[D]`/`[F]` text markers.

These statements are evidence about the current 1.19.2 source, not claims that
the planned behavior is already complete.

### Current live-proof boundary

`title_screen_output_statement_source_navigation` is the one strong existing
real-pointer seam: its probe derives a point from glyph/camera/zoom state, maps
it through workspace geometry, verifies `symbolHitAtScreen`, and drives native
Ctrl-hover/click. The latest local artifact was successful for one ASCII token
at `1280x720@auto`, but its manifest records revision `ca5f08417`; current HEAD
at planning time is `586147882`, including later hover/panel-focus changes.
That artifact is historical evidence and must be rerun from a freshly built and
installed worker before it can close any current acceptance gate.

The existing pointer proof does not cover Unicode canvas positions, glyph
boundaries, whitespace/outside, arbitrary pan/zoom, panel scale, ambiguity, or
click-before-actionable behavior. The separate F12 puppet covers exact target
ranges, panel reuse, and ambiguous choice without pointer hit testing. Model
and analyzer tests cannot be cited as substitutes for these missing canvas
paths.

Reference results currently declare several incomplete classes, including
read/write classification, override/implementation relationships, Javadoc,
strings, reflection, and dynamic dispatch. SS-3 reciprocal coverage must keep
that incompleteness typed and visible rather than claim a complete Java graph.
Installed-worker integration tests can skip via assumptions unless executable
and branch properties are supplied; a goal must record executed rather than
merely discovered tests.

## Create/Ponder and Panopticon reference evidence — 2026-08-17

The clean local Create checkout is
`87b3c6a65fd00c023a07b37b0353144bc7e6a5bf` on `mc1.21.1/dev`: Create 6.0.11
for Minecraft 1.21.1 declaring Ponder 1.0.82. Current Ponder engine code is a
separate dependency/repository; the local checkout contains Create's
registration, scenes, extension types, and integration hooks rather than the
complete current engine. The clean local Panopticon checkout is
`6ce1a962ecbbe68b1300a9ba1b13e6e3bafc4f7c` on `main`.

Verified transferable Ponder patterns:

- Create registers `CreatePonderPlugin` from `CreateClient`; the plugin
  registers scene callbacks/tags/shared text/restore hooks, and
  `AllCreatePonderScenes` maps item/block keys to storyboard callbacks.
- Storyboards such as `KineticsScenes.shaftAsRelay` declaratively emit world
  reveals, delays, camera changes, effects, text, and completion. Providers own
  a scene description; the host owns the UI.
- Current Ponder uses an in-memory `SchematicLevel`-derived `PonderLevel` with a
  `ClientLevel` façade for client-only APIs. It is a controlled simulation
  sandbox, not a second normally ticking vanilla client world.
- `PonderScene.begin` restores the snapshot and reschedules its instruction
  timeline. Its tick advances the virtual level, elements, transform, and
  blocking/non-blocking instructions. Backward seek replays from reset.
- Scene rendering and 2D overlay rendering are separate passes. The host owns
  projection, transitions, input, replay, picking, and lifecycle. Individual
  preview instructions do not steal palette input.
- Create marks smart block entities virtual and repairs controller relations
  after restore, evidence that block-entity lifecycle cannot be assumed to work
  automatically merely because blocks were copied.

Verified Panopticon boundary:

- Panopticon is server-only and has no applicable preview renderer or widget
  composition system.
- Its useful spatial-workload patterns are tiled region identity, an LRU cache,
  one active plus bounded queued scans, roughly 2 ms work slices, power-of-two
  sampling levels, request coalescing, bounded pending work, and compact
  palette/run-length network encoding.
- These are candidates for SS-2 large-surface sampling/backpressure, not CP-2
  rendering or palette composition.

Version/licensing guardrails:

- Create's code is generally MIT but its assets are All Rights Reserved; do not
  assume schematics, textures, screenshots, or other assets are reusable.
- The separate Ponder repository is MIT. The official `mc1.21.1/dev` source
  corroborates behavior but may differ from the exact 1.0.82 artifact.
- Panopticon is LGPL-3.0-only. Its scheduling/tiling ideas may inform design;
  copying implementation code requires a separate compliance decision.
- Historical Create Ponder code at parent commit
  `1ec63c1511d1fa03f39556e3914306e221c08439` is readable behavioral evidence,
  not a drop-in 1.19.2 API.

The smallest SFM preview experiment should therefore be host-owned and
deliberately less ambitious than Ponder: one inert isolated block/ItemStack
scene with explicit create, render, optional bounded tick, reset, and dispose;
one separate 2D overlay; and a deterministic observation artifact. It should
prove lifecycle and palette input ownership before introducing block entities,
TNT, or an SFM program simulation.

## Authoritative user-guidance ledger

### Spatial domains and coverage

| ID | Active guidance | Required consequence |
| --- | --- | --- |
| SURF-1 | The authoritative interaction is a point on a rendered 2D canvas (and may later generalize to 3D), not merely an integer index into a string. | The request and coverage schemas begin with a typed domain/coordinate and preserve the actual canvas transform/hit. |
| SURF-2 | Byte, code-point, glyph-ordinal, UTF-16, row/column, and linear source offsets remain useful. | Provide explicit loss-aware adapters and round-trip evidence; never erase them merely because canvas space is primary. |
| SURF-3 | Everything for which a rendered glyph exists must have deliberate behavior. | Every rendered glyph receives an explicit classification with provenance; an unclassified region fails coverage. SURF-7 separately imposes the stronger strict Java navigation requirement so classification cannot be confused with navigability. |
| SURF-4 | Space outside the document's laid-out bounds also needs an expected outcome. | Classify whitespace, padding, clipping, and outside-canvas regions explicitly; do not treat absence of a hit as absence of a test. |
| SURF-5 | Braces, semicolons, punctuation, and similar syntax need explicit rules, not a nearest-symbol accident. | Java region providers own syntax-kind rules and provenance; nearest-token fallback is prohibited in the strict profile. |
| SURF-6 | If one query can report the larger homogeneous region it covers, tests should not need a separate semantic lookup at every pixel. | Provider results include applicable source/canvas regions; the coverage engine partitions and skips already classified interiors while still probing boundaries. |
| SURF-7 | “Every glyph has behavior” means every supported Java glyph guides somewhere through Ctrl-click, not merely that it has any contextual action. | Track navigation-outlink coverage independently from generic action/classification coverage. In the strict parsed-Java profile, every rendered non-whitespace glyph must expose at least one deliberate `NAVIGATE` relation or an individually witnessed, approved typed exception. |
| SURF-8 | A point query should reveal how far its answer soundly extends so coverage does not require one expensive semantic query per canvas pixel. | Return the queried witness plus the largest provider-certified homogeneous source/canvas region available for that answer. The oracle validates interiors and boundaries, subdivides/bisects on disagreement, and never treats an unverified nearest-token expansion as certified coverage. |
| COV-1 | Add explicitly surface-based testing in addition to existing broad fixtures. | Exercise actual screen-to-canvas-to-region routing and compare it with pure semantic fixtures. |
| COV-2 | Small finite fixtures should be covered completely. | Exhaustively enumerate region cells/boundaries for bounded documents and fail on any unclassified cell/glyph. |
| COV-3 | Large surfaces need seeded randomness plus fair exploration when the budget is smaller than the candidate set. | Add reproducible seed/budget, stratified and maximin/farthest-next sampling, and a recorded policy id. |
| COV-4 | Exploration and exploitation should consider geometric, documentary, semantic, and decision-tree distance. | Sampling features include canvas distance, source distance, syntax/provider/branch novelty, and prior failure density; no single power law is silently declared optimal. |
| COV-5 | Important decision branches and boundaries must not be left to chance. | Deterministic token-kind/provider/outcome strata and edge/corner/transform probes precede optional stochastic samples. |
| COV-6 | Coverage state must be inspectable and useful for choosing the next test. | Emit versioned JSON plus a heatmap/overlay and a deterministic next-uncovered/highest-value candidate list. |
| COV-7 | Random failures must reproduce. | Record source/index/layout fingerprints, viewport, GUI scale, camera transform, seed, policy, budget, and sampled positions. |
| COV-8 | Java comes first and Rust later. | Language adapters are registered by stable id; Java acceptance cannot be weakened by postponing Rust. |
| COV-9 | Coverage must answer “what is covered in each supported file in this workspace?”, not only report fixtures or whichever files happened to load. | Inventory every file selected by the active workspace/source-set snapshot. Emit one row per supported-language file with adapter, source hash, layout status, denominators, coverage dimensions, and `covered`/`partial`/`skipped`/`failed`/`stale` state. Unsupported extensions remain separately visible and no omitted file counts as success. |
| COV-10 | A single “100% actionable” number can hide the absence of Ctrl-click navigation or reciprocal evidence. | Publish distinct denominators and percentages for rendered-glyph classification, deliberate navigation outlinks, real gesture routing, provider/decision branches, canvas boundaries, and reciprocal definition/reference relations. Every `100%` claim names its profile, corpus, snapshot, and denominator. |
| COV-11 | Region-aware coverage is also an efficiency contract. | Artifacts record semantic-query count, certified-region reuse, subdivisions/bisections, cache hits, and fallback point probes. Small fixtures prove equivalence to exhaustive per-point truth; large-corpus budgets fail if an implementation silently degenerates into one remote semantic query per pixel/glyph. |
| COV-12 | Coverage must be runnable against a selected document or whole code workspace, not exist only as private unit-test helpers. | Provide one canonical registered action/service with document/workspace selector, profile, layout/GUI-scale matrix, seed/budget, and artifact destination. It is invokable by tests, puppets, and the remoting CLI; exact action/CLI spelling is frozen in SS-0 and the handoff always gives a copyable invocation plus artifact paths. |

### Semantic regions, outlinks, and navigation

| ID | Active guidance | Required consequence |
| --- | --- | --- |
| LINK-1 | A location/region may have zero, one, or many candidate outlinks/actions. | Providers return typed candidate sets, never a single hard-coded callback. |
| LINK-2 | A method definition contains meaningful nested surfaces: annotations, modifiers, signature/name/parameters, body/block, statements, braces, and punctuation. | The Java adapter publishes a containment hierarchy with stable semantic kinds and source/canvas spans. |
| LINK-3 | Opening and closing braces/delimiters can meaningfully link to their containing definition, matching delimiter, enclosed body, or semantic children. | Preserve direction-aware candidates with reasons for both delimiter ends; do not collapse either glyph to one arbitrary cursor offset or an accidental nearest identifier. |
| LINK-4 | A navigation destination is a region/surface, not inherently one point. | Target projections include start/end/centre/percentage, nth source row/line (for example line 100 within the region), and nth semantic child (for example statement 10) with bounds validation. Parameterized projections remain action arguments/generators; do not emit one eager palette row for every possible percentage/line/child. |
| LINK-5 | Ambiguous destinations should use the familiar command-palette choice mechanism. | Zero candidates gives explicit feedback, one follows the preferred candidate, and many open a constrained choice of canonical action drafts. |
| LINK-6 | Ctrl+click, right-click, Alt+Enter, go-to-definition, and find-references should share provider truth. | Gestures differ only in requested intent/default policy; they do not maintain separate symbol parsers. |
| LINK-7 | Path-like text, URLs, registry values, and future contributed mod domains should participate. | Use a provider registry over typed context/regions; do not bake Java-only `instanceof` ladders into the canvas. |
| LINK-8 | Candidate discovery can be dynamic in space/time. | Providers may be asynchronous, cancellable, generation-tagged, and lazy, with stale-result rejection. |
| LINK-9 | The user must be able to understand why a candidate exists. | Record provider id/version, source region, destination region, relation kind, reason, confidence/completeness, and index fingerprints. |
| LINK-10 | Definition/reference navigation should agree in both directions. | Add reciprocal consistency checks and typed exceptions for ambiguity, generated/dynamic code, unresolved dependencies, and intentionally unsupported relations. |
| LINK-11 | Hover or picture-in-picture previews may help inspect candidates before navigating. | Preserve preview hooks on candidates without making hover rendering part of the core relation model. |
| LINK-12 | External mods and small internal modules should be able to contribute spatial/contextual behavior without editing one central Java-only switch. | Expose a stable registered-provider boundary with owner/mod id, deterministic ordering/conflict diagnostics, availability/failure isolation, and provenance in every result. Core Java providers use the same boundary as third-party providers. |

### Selections, domains, and addressing

| ID | Active guidance | Required consequence |
| --- | --- | --- |
| DOM-1 | A selection can be understood as a membership predicate over a domain. | Represent finite materialization separately from the selection predicate/expression and its domain identity. |
| DOM-2 | Linear text, 2D canvas, future 3D/time, filesystem, registry, and explorer domains are all useful. | Define typed domain adapters and coordinate projections; do not force every domain into text offsets. |
| DOM-3 | Bounds need explicit inclusion/exclusion semantics. | Region schemas state edge policy and normalize/validate empty, finite, and unbounded cases. |
| DOM-4 | Existing string/path selections are composable and should remain usable. | Adapt the existing path/set ledger; do not replace it with an incompatible spatial-only store or enumerate every pixel as a path. |
| DOM-5 | Comments label selected surfaces and should reuse this model. | Selection/explorer X-9 and the comment plan consume pinned semantic/spatial region expressions with source snapshot evidence. |
| DOM-6 | Explorer/file existence is determined by an authority/resolver predicate. | Region/outlink providers carry resolver capability and cannot turn display text into filesystem authority. |
| DOM-7 | The user compared the desired extensibility to “XCD has its own value that enables larger systems to emerge.” | Retain this as an unresolved reference note; do not guess or encode an unexplained XCD-specific abstraction. |
| DOM-8 | Panels/explorers/content can be selected spatially (for example bottom-left) as well as by stable identity. | X-10 freezes pane/entry/component identity; spatial selectors resolve to sets and report captured ids before mutation/navigation. |

### General capabilities and rich presentations

| ID | Active guidance | Required consequence |
| --- | --- | --- |
| CAP-1 | Text-editor alternatives are one instance of a system-wide implementation/capability choice. | Introduce a general capability request/provider registry with typed inputs/outputs; adapt text-editor selection as the first migration. |
| CAP-2 | Preferences may select a default, but users can name an implementation or ask to choose. | Resolution supports preferred, explicit provider id, and ask modes with availability reasons. |
| CAP-3 | Even command-palette implementations may eventually be replaceable. | Close a bootstrap gate: a built-in safe chooser/fallback must remain available when resolving the palette capability itself. |
| CAP-4 | Actions should expose localized text, aliases such as jump/navigate, active keybindings/chords, icons, and searchable terminology. | Extend presentation metadata/contributors without changing canonical action ids; reuse existing ItemStack icons and keybinding-presentation truth. |
| CAP-5 | A selected action can render richer information into an allocated screen region. | Define a clipped preview host and context rather than allowing arbitrary whole-screen drawing. |
| CAP-6 | Preview intent matters: input, output, simulated final state, and other purposes are distinct. | A typed preview-purpose value is part of selection and rendering. |
| CAP-7 | Previews may be raster/image, widgets/screens, animated/timeline, or a virtual client world. | Start with one simple bounded preview; adapt episode/timeline contracts for time and research Create Ponder before world-preview design is frozen. |
| CAP-8 | A “place TNT” action could preview an empty input world, placed TNT output, or primed TNT after simulated ticks; SFM programs may later use the same idea. | Preserve deterministic duration/tick limits, seed/world setup, and GameTest integration as later proof items. |
| CAP-9 | Preview contributors must not destabilize the palette. | Enforce clipping/scissor, render-state restoration, thread ownership, resource disposal, cancellation, bounded time/allocation, and isolated error presentation. |
| CAP-10 | Create and Panopticon are local reference material. | Record exact source/version/license findings and transferable patterns; do not copy an API or assume block entities tick until verified. |

### Concrete user-testing requirements

| ID | Active guidance | Required consequence |
| --- | --- | --- |
| UX-ADDR-1 | An open editor must visibly show the exact address of its document. | Add a focusable, narrated location control analogous to the explorer header, with exact tooltip/copy content. |
| UX-ADDR-2 | Clicking the address should make it easy to copy/edit, and the address document must itself have an address. | Open a typed virtual location document through the preferred editor; define a non-recursive canonical address for that virtual document. |
| UX-ADDR-3 | A text editor is one presenter for address-bearing pane content; navigation should later encompass settings/screens and other presenters like a browser rather than treating source files as the only addressable history. | NX-1 records generic pane-content addresses and capability/provider identity; NX-2 proves the editor adapter first without hard-coding the history ledger to files. |
| UX-CTX-1 | Right-clicking `LexerAdapter.java` or its `langs` directory should offer Copy as Path and Open as Root in New Explorer. | Explorer hit context emits canonical action drafts for the exact clicked node, independent of focused-row races. |
| UX-EXP-1 | Java files need not remain leaves; a file can expose class/nested-class/member interpretations and can be opened as an explorer root. | Add a lazy semantic-child relation backed by the Java index and a file-as-root action. |
| UX-SYM-1 | Ctrl+click/underline currently fails on `LexerAdapter` and fields in `LexerAdapter.java`; right-click definition reports no symbol. | Reproduce with exact fixture/range and repair the canvas-region/provider path. |
| UX-SYM-2 | `@Mod` in `SFM.java` currently fails while `LocalizationEntry` succeeds. | Keep both as positive/negative regression witnesses so a broad fix cannot regress the working path. |
| UX-SYM-3 | `String` works, while fully qualified `java.io.Serializable`, the `java.io.Serial` annotation, and the `serialVersionUID` area fail after navigation into platform source. | Cover JDK/dependency documents, qualified-name subregions, annotations, members, and source-root authorization in one matrix. |
| UX-FRAME-1 | A successful jump currently places the target at top-left such that text to its left is outside the visible area. | Navigation frames a destination region through an explicit landmark and viewport policy rather than assigning that landmark directly to the panel origin. |
| UX-FRAME-2 | In easy cases the user should see the destination and the left edge/start of its document line, with the normal editor inset; the document's top edge is not required for a mid-document target. | If the destination landmark plus line/document left edge fit horizontally, both are visible with positive inset and no negative camera overscroll. A first-line destination also preserves the top inset; a mid-document destination uses a sensible vertical reveal without forcing line 1 visible. |
| UX-FRAME-3 | A long line or destination region may be wider than the viewport, so “show the whole left edge and target” is sometimes impossible and must not become an undefined exception. | Prefer a fully visible target landmark/meaningful target subregion when it fits, then maximize deterministic leading context. Emit a framing observation containing document/line bounds, destination region/landmark, viewport, chosen camera/scroll, visible intersection, inset, and a typed reason whenever the left edge or target region cannot both fit. |
| UX-HIST-1 | Mouse back/forward buttons should navigate source/location history. | Register semantic back/forward actions and map supported mouse buttons through the ordinary binding system. |
| UX-HIST-2 | Going back and navigating elsewhere must not destroy the old forward branch. | Store append-only navigation events and a branching cursor/tree; expose branch choices rather than linear-history truncation. |
| UX-TOAST-1 | Definition feedback disappears too quickly to read. | Replace one-frame/short feedback with a generic toast policy and visible remaining-lifetime bar. |
| UX-TOAST-2 | Hovering a toast pauses expiry; left click copies it; right click offers actions including Stop Timer Forever and Dismiss for that instance. | Toasts have stable ids, paused/pinned state, copy text, and contextual action drafts. Pin is the canonical per-instance state shown as “Stop Timer Forever”; Unpin/“Resume Timer” continues the previously remaining lifetime rather than resetting it. `sfm:toast/dismiss <toast-id>` immediately removes only the addressed instance, pinned or not; it neither silences future messages nor changes timer preferences. Expired/stale ids fail visibly without touching another toast. Future silence/preferences remain extensible. |
| UX-ICON-1 | Find-references results for `MutableComponentType` work but show `[D]`. | Reuse the shared ItemStack presentation/icon pipeline and assert no legacy text marker. |

## Guidance traceability

| Guidance ids | Owning work | Required evidence |
| --- | --- | --- |
| SURF-1..SURF-8, DOM-1..DOM-4 | SS-1, SS-2 | Versioned domain/region/probe schema, transform/property tests, certified-region partitions, exhaustive-truth equivalence, and actual canvas-hit evidence |
| COV-1..COV-12 | SS-2, SS-3, SS-4, SS-8 | Exhaustive fixtures, seeded sampler artifacts, canonical document/workspace invocation, per-workspace-file inventory, separate classification/navigation/gesture/reciprocal dimensions, query-efficiency counters, heatmaps, reproducibility, Java matrix, Rust deferral statement |
| LINK-1..LINK-12 | SS-1, SS-3, SS-4 | Provider/result contracts, Java nested regions, bidirectional delimiter fixtures, palette choices, third-party registration/conflict tests, provenance, reciprocal definition/reference report |
| DOM-5..DOM-8 | SS-7 plus selection X-9/X-10 | Shared adapter round trips, pinned comment selection proof, resolver authority, spatial-selector capture evidence |
| CAP-1..CAP-10 | CP-1..CP-4 | Capability resolution tests, presentation metadata, bounded preview witness, Create/Panopticon report, lifecycle/error budgets |
| UX-ADDR-1, UX-ADDR-2, UX-ADDR-3 | NX-1, NX-2 | Live editor location control, self-addressed location-document round trip, and generic pane-content history record |
| UX-CTX-1, UX-EXP-1 | NX-3 | Right-click constrained palette, semantic Java tree/file root, and navigable semantic rows |
| UX-ICON-1 | NX-3a | Reference-result source-kind adaptation through the completed shared ItemStack presenter, exact cocoa/chest/paper ids, and no legacy marker |
| UX-SYM-1..UX-SYM-3, UX-FRAME-1..UX-FRAME-3 | SS-3, SS-4 | Exact source fixtures, GUI-scale/navigation matrix, working-regression retention, framing geometry and machine-readable viewport observations |
| UX-HIST-1, UX-HIST-2 | NX-1 | Pure branching history tests and live mouse back/forward branch witness |
| UX-TOAST-1, UX-TOAST-2 | NX-4 | Lifetime/hover/copy/pin/dismiss/stale-id/action tests and readable live failure toast |

## Proposed contract vocabulary

The names below communicate boundaries; SS-1 may adjust exact Java spelling
before public or serialized use.

```text
SFMRegionDomain
  id, dimensions, coordinate kinds, authority/snapshot identity

SFMRegion
  domain, membership/bounds representation, edge policy, semantic kind,
  stable provenance, optional projections

SFMRegionProjection
  from-domain, to-domain, loss/completeness, transform/fingerprint

SFMOutlink
  source region, destination region/query, relation kind, provider,
  reason, confidence/completeness, available action draft(s)

SFMInteractionProbeResult
  queried domain point, provider-certified applicable source/canvas region,
  classification by requested intent, outlinks/actions, provenance/generation

SFMInteractionClassification
  actionable(intent -> outlinks/action drafts), explicit-no-action(reason),
  unsupported(reason), or unclassified

SFMNavigationProjection
  start, end, centre, percentage, nth-source-row/line, nth-child,
  or provider-named landmark

SFMSpatialCoverageReport
  workspace/document/layout/index fingerprints, corpus file inventory,
  policy/seed/budget, certified region partitions, sampled witnesses,
  classification/navigation/gesture/branch/boundary/reciprocity coverage,
  semantic-query/reuse/subdivision counters, exceptions, next candidates

SFMNavigationFramingObservation
  pane/document identity, destination region and chosen landmark,
  document/line bounds, viewport and inset, previous/chosen camera transform,
  visible intersection, left/top-edge visibility, typed clipping reason

SFMCapabilityRequest / SFMCapabilityProvider
  capability id, typed context, preferred/explicit/ask resolution,
  availability explanation, provider result

SFMActionPreviewContribution
  action/context identity, preview purpose, allocated bounds, lifecycle,
  deterministic observation/diagnostics
```

Regions must not be serialized as an unbounded list of pixels. A rectangular
glyph footprint, source interval, syntax node, predicate-backed selection, and
finite materialization are different representations behind one typed domain
contract. Membership must remain queryable, and finite domains must support a
deterministic partition for coverage.

## Confirmed design constraints

1. Canvas coordinates are authoritative for pointer interaction; text offsets
   remain derived, explicit projections.
2. `UNCLASSIFIED` is a coverage defect. `EXPLICIT_NO_ACTION` and `UNSUPPORTED`
   require reason codes and remain visible in reports.
3. Under the strict parsed-Java navigation profile, every non-whitespace
   rendered glyph is expected to have a deliberate navigation outlink unless
   an individually witnessed typed exception is explicitly approved. A copy,
   inspect, or other non-navigation action is reported separately and cannot
   satisfy this requirement.
4. Provider output is zero-to-many and becomes canonical action drafts. UI
   gestures never receive provider-specific callbacks.
5. Semantic relation discovery and destination framing are separate. A method
   region can be projected to its start, end, midpoint, percentage, or child.
6. Nearest-token fallback is not a semantic rule. Recovery from malformed text
   must name itself as recovery and never masquerade as an exact result.
7. Exact finite fixture coverage comes before stochastic coverage. Randomness
   is seeded, supplemental, and reproducible.
8. Coverage tests must cross the real canvas hit path at representative scales,
   zooms, pans, clips, and glyph boundaries; AST-only tests are insufficient.
9. Existing typed paths/selections, contextual palette, ItemStack presentation,
   Java index, and timeline contracts are reused rather than forked.
10. Preview/render contributors are bounded guests. They cannot own the whole
    screen, render indefinitely, perform unbounded synchronous work, or leak
    render/world resources.
11. No implementation slice broadens filesystem/world authority merely because
    text resembles a path or a preview requests a world.
12. Human release approval remains external to any coverage percentage or
    automated report.
13. Whole-workspace coverage begins with an explicit file inventory. Parse,
    layout, index, timeout, stale, and unsupported outcomes remain rows in the
    report rather than disappearing from its denominator.
14. Post-navigation framing is part of correctness evidence. The target
    landmark, horizontal line/document origin when it fits, panel inset,
    viewport, and any unavoidable clipping are machine-observable.
15. Registered third-party providers use the same bounded, provenance-bearing
    contract as built-in providers and cannot gain filesystem/world authority
    merely by matching display text.

## Reversible working recommendations

- Use half-open source/text intervals and half-open integer canvas rectangles
  internally, while allowing adapters to expose other edge conventions
  explicitly. Probe exact edges on both sides in tests.
- Treat canvas, text, Java syntax, file/registry, and world/time as distinct
  domain ids joined by versioned projections rather than one universal
  coordinate enum.
- Use a deterministic partition of finite fixture space plus maximin
  farthest-point sampling for the exploratory portion. Rank candidate novelty
  by a weighted vector of geometry, source distance, syntax kind, provider,
  branch, and prior-failure neighborhood. Store the vector and weights in the
  artifact rather than declaring one permanent metric.
- Establish a seeded uniform-random baseline before comparing stratified,
  maximin, adaptive/failure-seeking, or power-law-weighted policies. A policy
  must be judged against exhaustive small-fixture truth, not accepted because
  adjacent samples merely look diverse.
- Treat provider-certified regions as query accelerators, not unquestioned
  truth. Probe interiors and both sides of boundaries and recursively subdivide
  or bisect contradictory claims; publish query-count and reuse evidence.
- Frame navigation per axis. Horizontally clamp toward the line/document left
  origin whenever the chosen destination landmark remains visible; otherwise
  preserve that landmark and as much leading context as the viewport permits.
  Vertically reveal the target with normal inset, preserving the document top
  only when it naturally fits (for example a first-line destination).
- Maintain per-pane branching location history backed by a workspace-wide
  append-only event ledger. Pane selectors resolve/capture identity before
  navigation. This keeps Back intuitive while preserving global provenance.
- Make the location document a virtual read-only document at first. It can
  expose exact copy/open behavior without letting an address edit silently
  retarget an editor session before authority/revision semantics are frozen.
- Implement a queue of independently addressable toasts; pinning stops expiry
  for one toast and dismiss removes only that addressed instance. Neither
  operation silently becomes silence-by-kind, which remains a later preference
  action.
- Keep a built-in command palette as the bootstrap chooser even after a general
  capability-provider registry exists. An alternate palette cannot be required
  to choose itself.
- Begin rich previews with a static, bounded ItemStack/text or image preview.
  Virtual-world ticking is a later proof after Create/Panopticon research and
  deterministic episode integration.

## Design gates

| Gate | Question | Working recommendation | Closure evidence |
| --- | --- | --- | --- |
| SD-1 Region algebra | What minimal representation supports text, canvas, syntax nodes, selections, and later world/time without enumerating the domain? | Typed domain plus pluggable membership/bounds/materialization and explicit projections. | SS-1 fixtures cover rectangles, source intervals, nested nodes, empty/outside, Unicode, and non-lossless projections. |
| SD-2 Coverage status | Does explicit no-action or a non-navigation contextual action count as navigation success? | Both count toward truthful classification, but neither satisfies strict Java navigation coverage. That profile permits missing navigation only for whitespace/outside or an individually witnessed approved exception; publish classification and navigation denominators separately. | SS-2 policy fixtures and a zero-unapproved-navigation-exception report. |
| SD-3 Region partition | How can one provider response cover many points safely? | Provider names its applicable region; coverage validates homogeneous classification at centre/boundaries and subdivides on disagreement. | SS-2 adversarial overlapping/boundary fixtures. |
| SD-4 Sampling | Which random/distance policy is authoritative? | No universal winner. Version policy/weights and compare exhaustive truth on small fixtures before using it on large files. | SS-2 sampler-quality tests and stored seeds. |
| SD-5 Java region source | Does Java structure come from the game, Rust CLI, or both? | Rust/CLI index is semantic authority; Java keeps a generation-tagged compact interaction map needed for immediate hit/hover. | SS-3 cross-language schema fixtures and stale-generation rejection. |
| SD-6 Destination projection | Who chooses start/end/midpoint/nth child? | Relation identifies destination region and recommended landmark; action/palette lets the user override with registered projections. | SS-3/SS-4 projection and framing tests. |
| SD-7 Reciprocal links | Must every relation have an exact inverse? | Require inverse-consistency for resolved static Java definition/reference edges; permit typed witnessed exceptions elsewhere. | SS-3 reciprocal report over fixtures and selected SFM files. |
| SD-8 History scope | Is navigation history global, per pane, per editor, or per panel entry? | Per-pane cursor/tree over one workspace event ledger; content replacements remain events. | NX-1 nested pane/stack tests and mouse witness after X-10 identity freeze. |
| SD-9 Spatial selector grammar | How are bottom-left and set-valued pane targets spelled? | Defer exact public grammar to X-10; require pure selector resolution and captured ids. | X-10 plus NX-1 parser/layout matrix. |
| SD-10 Address-document recursion | What address does the editor of an editor address have? | A typed virtual object/session address containing source editor identity and revision, not its own displayed text recursively. | NX-2 round-trip and reopen tests. |
| SD-11 Toast policy | Queue, replace, coalesce, persist, dismiss, or silence? | Addressable workspace-scoped queue; coalescing only by explicit key; hover/choice interaction pauses remaining lifetime; pin one instance and resume from its remaining lifetime; dismiss only the addressed instance; stale dismiss cannot target a replacement. Closing the owning workspace disposes its queue; opening a constrained choice over that workspace does not. Cross-session persistence and silence-by-kind are deferred. | NX-4 overlap, hover/choice lease, copy, pin/resume, dismiss/stale-id, replace, workspace-close, and constrained-choice tests. |
| SD-12 Capability resolution | How do preferred/explicit/ask and unavailable providers compose? | Deterministic typed resolver with reasons; explicit unavailable fails, preferred may use a declared fallback, ask lists all viable providers. | CP-1 matrix and text-editor migration. |
| SD-13 Palette recursion | How can the palette itself be selected? | Permanent built-in bootstrap chooser and escape path; alternate palette selection never replaces recovery. | CP-1 failure/recovery tests before public registration. |
| SD-14 Preview contract | What can a preview draw or simulate? | Allocated clipped region, typed purpose, lifecycle/cancellation, observation budget; no raw unrestricted screen callback. | CP-2 malicious/slow/error contributor tests. |
| SD-15 Ponder transfer | Does Create use a virtual client world and tick block entities as hypothesized? | **Closed for planning:** Ponder uses a controlled `SchematicLevel` plus client façade, explicit scene ticking/instructions, reset/replay, and separate scene/overlay passes; it is not a fully ticking second client world. SFM begins with an inert lifecycle proof. | CP-3 evidence above; CP-4 must still prove the 1.19.2 experiment and cannot assume automatic block-entity behavior. |
| SD-16 Java explorer hierarchy | Which semantic levels appear and how are they paged? | File -> package/type -> nested type/member -> signature/body/statement landmarks, lazily backed by one index snapshot. | NX-3 tree fixture and large-file lazy counters. |
| SD-17 Release boundary | How much becomes a release blocker? | SS-1 through SS-4 plus concrete UX regressions are correctness candidates; generalized previews/world simulation remain non-blocking until explicitly promoted. | Release-plan update after live human inspection. |
| SD-18 Pending Ctrl-click ownership | What happens when Ctrl+click arrives before an asynchronous hover lookup becomes actionable? | Capture the exact region and consume the link-intent gesture while a viable lookup is pending; on resolution follow/choose, and on explicit non-action return clear feedback. Never reinterpret the already completed Ctrl+click as an editor multi-cursor mutation. | SS-4 pending/timeout/cancel/ambiguous/non-action tests plus a live rapid-click witness. |
| SD-19 Whole-workspace denominator | What files constitute “all supported code in the workspace”? | Snapshot the active explorer/workspace roots and configured source sets, canonicalize/deduplicate paths, then inventory every file before analysis. Every supported file receives a terminal report state; unsupported extensions are counted separately. | SS-2 inventory fixtures and SS-3 report over every Java source set in the selected 1.19.2 workspace. |
| SD-20 Certified-region efficiency | How does a point result safely cover a larger area without querying every pixel? | Providers return a certified applicable region; the oracle validates representative interiors/boundaries and deterministically subdivides/bisects disagreement. Record query counts and prove equivalence with exhaustive truth on small canvases. | SS-1 probe schema plus SS-2 adversarial narrow-island and complexity-budget tests. |
| SD-21 Destination framing | What if the destination, line start, and target region cannot all fit? | Choose a semantic landmark within the destination region. If landmark and line/document left edge fit, show both with inset; otherwise keep the landmark usable and maximize deterministic leading context. Emit a typed framing observation instead of silently clipping. | SS-4 pure geometry matrix and live Auto/1..8 GUI-scale artifacts over narrow/wide panels, long lines, Unicode, tabs, first-line and mid-file targets. |
| SD-22 Provider extensibility | Can another mod contribute outlinks without core edits or nondeterministic conflicts? | Register owner-qualified providers with deterministic priority/tie diagnostics, per-provider failure isolation, typed capability/authority checks, and provenance on results. | SS-1 two-provider fixtures and SS-4 constrained-palette conflict/failure proof. |
| SD-23 Coverage invocation | Which surface owns whole-workspace spatial coverage when Rust owns Java semantics but the game owns actual canvas layout? | Define one Java-side coverage service/action over the real editor layout and let tests, puppets, and the `sfm.exe` remoting client invoke it. Rust/CLI supplies generation-tagged semantic maps; it must not counterfeit Java canvas coordinates. Freeze exact action/argument/output spelling in SS-0. | SS-2 pure service invocation, SS-4 registered-action/remoting/puppet witness, copyable command, and colocated JSON/heatmap/framing artifacts. |

## Execution order and parallel topology

```text
SS-0 contract freeze and reference evidence
  -> SS-1 region/outlink schemas
      -> SS-2 spatial coverage engine -----------+
      -> SS-3 Java semantic region adapter -------+-> SS-4 live canvas navigation slice
                                                   |
X-10 identity freeze -> NX-1 branching history ---+
                    -> NX-2 editor address --------+
SS-3 + explorer X-7 -> NX-3 semantic explorer ----+
completed C-4a -----> NX-3a reference icons -------+
P-2.6 baseline -----> NX-4 actionable toasts ------+

SS-1 -> CP-1 generalized capability resolution -> CP-2 bounded previews
CP-3 Create/Panopticon reference evidence [complete] -------------------+
episodes/timeline + CP-2 + CP-3 ---------------------------------------> CP-4 deterministic simulated preview

SS-1/SS-3 + selection X-9 -> SS-7 comments/domain adapters -> SS-8 Rust/release matrix
```

After SS-0 closes shared schemas, the following work is deliberately parallel:

- SS-2 pure coverage/partition/sampling engine and fixture artifacts;
- SS-3 Rust CLI Java semantic-region/index work and generated Java DTO parity;
- NX-4 generic toast model/render/input tests;
- NX-3a reference-result source-kind presentation through the existing icon
  pipeline;
- NX-1 pure history ledger after X-10 terminology is available.

Completed CP-3 reference evidence is an input to later CP work, not a parallel
production lane.

The coordinator owns schema fingerprints, generated-code integration, the
actual canvas/palette join, plan bookkeeping, and final live artifacts. No two
parallel workers edit the same contract file after the freeze.

## Phase SS — Spatial semantic navigation

### [x] SS-0 Freeze the executable contract and reference evidence

**Completion notes (2026-08-18):** The executable v1 freeze is recorded in
`docs/architecture/spatial-semantic-contract-v1.md`. The production Java
mirror and explicit Gson-2.8.9-compatible codec live under
`platform/minecraft/src/main/java/ca/teamdman/sfm/client/semantic/`; their
constructors enforce schema identity, half-open finite bounds, exact one-of
outlink destinations, intent/classification separation, generation identity,
per-file terminal states, separate coverage dimensions, and framing evidence.
The adjacent `docs/architecture/fixtures/spatial-semantic-v1/` corpus freezes
the hand-authored Java/malformed/layout/golden JSON witnesses and ignores only
generated `*-actual.json`/heatmaps. `SFMSpatialSemanticContractTests` passed
through `sfm-propagate-changes.exe test run --branch 1.19.2 --filter
SFMSpatialSemanticContractTests --wait-for-build-lock` after the expected Codex
artifact-lock denial was rerun with normal access to the existing pinned cache.
No dependency declaration or lockfile changed. Any incompatible contract
change now requires a schema bump and golden-fixture migration.

**Work:**

- Finalize the names, schema versions, exact edge semantics, fingerprints, and
  Java/Rust ownership for domains, regions, projections, classifications,
  probe results, intent-specific outlinks, target projections, framing
  observations, workspace-file inventory, and coverage reports.
- Build small hand-authored Java scenarios beside their tests. Include a class,
  annotation, import/qualified name, field, local, method, braces, semicolon,
  operators, literals, comment, nested class, malformed fragment, Unicode, and
  CRLF. Include both ends of delimiters and layout fixtures with tabs, wide
  Unicode glyphs, first-line/mid-file targets, narrow/wide panels, long lines,
  clipping, pan/zoom, and GUI scale. Expected output is checked in next to
  actual output under precise gitignore rules.
- Freeze separate coverage profiles/denominators for classification,
  navigation outlinks, real gesture routing, branch/boundary behavior, and
  reciprocity. Define the active workspace/source-set snapshot and terminal
  per-file states before a corpus can claim `100%`.
- Freeze the canonical Java-side coverage service/action and its document or
  workspace selector, profile, layout/GUI-scale matrix, seed/budget, artifact
  destination, remoting shape, and output schema. Rust may provide semantic
  maps but does not invent Java canvas coordinates.
- Retain the completed Create/Panopticon findings without reopening preview API
  design in this navigation goal. Exact Ponder 1.0.82 source acquisition is a
  CP-4 precondition, not an SS-0/SS-1 navigation-contract blocker.
- Update cross-plan backlinks and mark one authoritative owner per task.

**Validation:** schema round-trip/golden tests and plan/source-reference audit.

```pwsh
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMSpatialSemanticContractTests --wait-for-build-lock
rg -n "SURF-|COV-|LINK-|DOM-|CAP-|UX-|SS-|NX-|CP-" "docs\tasks\spatial semantic surfaces outlinks and capability presenters plan.md"
```

**Completion criteria:** A Luna implementation agent can implement SS-1/SS-2
from typed fixture inputs and expected outputs without inventing coordinate,
edge, intent, corpus, framing, classification, or ownership semantics. Schema
changes after this point require an explicit version bump and fixture
migration.

### [x] SS-1 Implement typed domains, regions, projections, and outlinks

**Completion notes (2026-08-18):** The frozen values are implemented by
`SFMSpatialSemanticContract`; `SFMCanvasTextRegionAdapter` preserves the
existing `SFMContextCanvasTextMap` while projecting immutable canvas/UTF-8
regions; `SFMRegionGraph` owns generation-scoped region/outlink identity,
containment, children, and explicit destination projections; and
`SFMOutlinkRepository` owns mod-qualified registration, descending-priority /
ascending-id order, stable generation, cancellation, availability, duplicate
rejection, contributor-failure isolation, close lifecycle, and explicit
equal-priority diagnostics without hiding tied results. Navigation intent
remains distinct from generic action drafts, and a provider-certified region
must contain the retained exact query point. `SFMRegionDomainTests` and
`SFMOutlinkRepositoryTests` both passed through their plan-listed
`sfm-propagate-changes.exe test run` filters with normal access to the existing
pinned cache. No dependency or lockfile changed.

**Work:**

- Add immutable domain/region/projection values and normalize/validate their
  bounds. Adapt current canvas-text maps and text ranges rather than deleting
  them.
- Add an interaction-probe result which preserves the exact queried point and
  the provider-certified source/canvas region over which the result is claimed
  to remain homogeneous. Do not synthesize that region from nearest-token
  fallback.
- Add provider/result repositories with stable provider identity, generation,
  cancellation, provenance, relation kind, confidence/completeness, and
  canonical action drafts. Provider ownership is mod-qualified; ordering and
  ties are deterministic and contributor failure is isolated.
- Support target landmarks and containment/child relations without reducing a
  destination region to one offset.
- Keep navigation relations distinct from inspect/copy/other contextual
  actions so the strict profile cannot pass on an action-only glyph.
- Preserve resolver authority and source/index fingerprints across every
  projection.

**Validation:** focused pure Java tests, Rust/Facet round trips where the CLI
owns data, Unicode/CRLF/bounds/property tests, overlap and stale-generation
tests.

```pwsh
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMRegionDomainTests --wait-for-build-lock
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMOutlinkRepositoryTests --wait-for-build-lock
sfm-propagate-changes.exe run compile --branch 1.19.2 --wait-for-build-lock
```

**Completion criteria:** The fixture can name a canvas point, recover its glyph
and source/syntax regions, enumerate zero-to-many outlinks, and round-trip every
identity and bound without an identifier-only helper or ambient authority. It
also states the soundly certified region, separates navigation from other
intents, and identifies/order-resolves two independently registered providers.

### [x] SS-2 Build the spatial coverage oracle and sampling policies

**Completion notes (2026-08-18):** `SFMSpatialCoverageService` is a public,
test/puppet/action-ready Java canvas service rather than fixture-only logic. It
snapshots every supplied workspace document, preserves terminal failure rows,
retains exact sampled canvas points, validates provider-certified rectangles at
interior/corner/inside/outside witnesses, rejects contradictory certificates,
records query/reuse/cache/subdivision/fallback counters, and publishes separate
classification, navigation, action-only, real-gesture, provider-branch,
boundary, and reciprocity dimensions. `SFMSpatialSamplingPolicies` implements
versioned exhaustive, seeded uniform, stratified, maximin/farthest-next, and
adaptive failure-seeking orders with duplicate/out-of-bounds checks.
`SFMSpatialCoverageArtifacts` atomically emits adjacent versioned report JSON,
compact map/exception/partition/next-candidate JSON, and a bounded PNG heatmap.
The golden service test proves a 100-cell exhaustive result agrees with oracle
truth while using fewer than 20 semantic queries, and a contradictory broad
certificate remains visible as subdivision plus uncovered evidence.
`SFMSpatialCoverageTests` and `SFMCanvasInteractionCoverageTests` passed via
their plan-listed SFM CLI filters. No dependency or lockfile changed.

**Work:**

- Partition bounded fixture canvases into homogeneous interaction regions. Test
  representative interiors, exact edges/corners, just-inside/outside points,
  clipping, camera transforms, GUI scales, and overlap precedence.
- Implement exhaustive finite coverage plus seeded uniform-random,
  stratified, maximin/farthest-next, and adaptive/failure-seeking sampling.
  Preserve geometry/source/syntax/provider/branch novelty separately in the
  score and artifact; record any power-law weighting rather than assuming it.
- Validate provider-certified regions with interior and boundary witnesses,
  recursively subdividing/bisecting disagreement. Record semantic-query count,
  certified-region reuse, cache hits, subdivisions, and fallback point probes.
- Inventory the active workspace/source-set snapshot before analysis and emit
  one terminal state for every supported-language file. Keep missing, stale,
  parse/layout/index failure, timeout, skipped, and unsupported-extension states
  visible rather than dropping them from the corpus.
- Implement the coverage engine as an invokable service rather than test-only
  code. Its canonical request can target one open document or an entire
  snapshotted workspace and can be driven by focused tests before live action
  wiring exists.
- For oversized canvases/corpora, evaluate Panopticon-like tiled identities,
  power-of-two sampling levels, LRU reuse, request coalescing, bounded pending
  work, and short work slices. Record fairness/starvation/backpressure evidence
  before adopting any one policy; do not copy LGPL implementation code.
- Emit versioned JSON, optional compact region map, PNG/overlay heatmap, branch
  summary, exceptions, reproducible sample list, and next-uncovered/highest-
  value candidates. Publish independent classification, navigation-outlink,
  real-gesture, branch, boundary, and reciprocity dimensions with explicit
  denominators.
- Fail on `UNCLASSIFIED`, stale/inconsistent region claims, missing required
  branches, missing workspace-file terminal states, and unapproved strict-Java
  navigation exceptions.

**Validation:** compare every sampling policy and certified-region optimization
against exhaustive per-point truth on small fixtures; inject narrow missed
regions and prove deterministic subdivision plus branch/boundary detection;
assert a bounded query-count reduction; run actual `symbolHitAtScreen`/panel
context routing at a scale matrix; and prove that an unreadable/failed Java file
remains a visible failed corpus row.

```pwsh
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMSpatialCoverageTests --wait-for-build-lock
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMCanvasInteractionCoverageTests --wait-for-build-lock
```

**Completion criteria:** A report answers what percentage is actionable,
navigation-covered, action-only, explicitly non-actionable, unsupported, and
unclassified for each file and the whole snapshotted corpus; reproduces every
sample; exposes every unclassified or navigation-uncovered region; proves its
optimized partition agrees with exhaustive fixture truth; records query-cost
evidence; and chooses the same next candidate for the same
source/layout/index/seed/policy.

### [x] SS-3 Publish Java semantic regions and reciprocal outlinks

**Completion notes (2026-08-18):** `sfm-propagate-changes` now owns the
versioned `sfm.java-interaction-map/1` semantic authority and serves compact,
generation-tagged, fingerprinted pages containing nested Java regions,
classifications, definitions, references, delimiter/containment relations,
action drafts, typed exceptions, workspace inventory, and reciprocal static
edge evidence. The semantic-matrix scenario deliberately covers
`LexerAdapter`, `@Mod`, `LocalizationEntry`, imports, qualified names, both
delimiter ends, punctuation, literals, comments, malformed recovery, Unicode,
and CRLF. JDK and acquired-dependency documents use the same resolver-issued
span contract as SFM source; the live JDK `StringBuilder.java` interaction map
proved reciprocal definition relations for `java.io.Serializable`,
`java.io.Serial`, and `serialVersionUID`.

The Java bridge pages large maps without unbounded messages, rejects stale
generation/layout identity, and projects semantic byte regions into the real
EditorV3 canvas. The final `sfm-propagate-changes/check-all.ps1` run passed
dependency policy, format, Clippy, build, 620 runnable unit tests (3 explicitly
ignored) and all 10 Java-analysis scenario tests. The installed-worker JUnit
was separately supplied the final release worker and branch properties so it
executed rather than skipped; it resolved definitions and decoded the paged
large `OutputStatement.java` map. Reference incompleteness and the partial
dependency index remain typed diagnostics rather than a false complete-Java
claim. Rust-language navigation remains deliberately deferred to SS-8. No
dependency declaration or lockfile changed.

**Work:**

- Extend the CLI Java index/output with nested semantic regions and relations
  for declarations, annotations, modifiers, imports, qualified names,
  references, fields, variables, methods, parameters, signatures, bodies,
  braces, statements, punctuation, literals, comments, and malformed recovery.
- Produce a compact generation-tagged per-document interaction map for Java.
  Keep large-file transfer/query bounded and incrementally refreshable.
- Add definitions, references/usages, matching delimiter, containing region,
  child landmark, file/path, and applicable contextual relations.
- Give every strict-profile non-whitespace glyph a deliberate navigation
  relation, including both delimiter ends and punctuation, or emit an
  individually witnessed typed exception which cannot be mistaken for
  success. Keep non-navigation contextual actions as a separate dimension.
- Verify inverse definition/reference edges, retaining typed exception records.
- Include SFM source, all source sets, JDK `src.zip`, and dependency-source
  index domains according to the CLI analysis plan. Start from a complete
  workspace/source-set file inventory and preserve every file's terminal state.

**Validation:** scenario commands/expected JSON beside fixtures; CLI check-all;
reciprocity property tests; exact regressions for `LexerAdapter`, `@Mod`,
`LocalizationEntry`, `String`, `java.io.Serializable`, and `serialVersionUID`.

```pwsh
cd platform\cli\sfm-propagate-changes
.\check-all.ps1
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMJavaInteractionMapProtocolTests --wait-for-build-lock
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMJavaInteractionMapSpatialAdapterTests --wait-for-build-lock
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMJavaCanvasInteractionRegionsTests --wait-for-build-lock
```

**Completion criteria:** Every strict-profile glyph in the scenario matrix has
an explicit Java classification and at least one deliberate navigation outlink
or an approved witnessed exception; all resolved static
definition/reference edges pass reciprocal checks; every selected workspace
Java file has a terminal report row; and JDK/dependency and SFM-source regions
share the same result contract.

### [x] SS-4 Integrate region-aware hover, gestures, palette choices, and framing

**Completion notes (2026-08-18):** EditorV3 now captures the exact painted
canvas region and provider generation for Ctrl-hover, Ctrl+click, right-click,
Alt+Enter, F12, and Alt+F7. The full region is underlined with link-cursor
feedback, pending Ctrl+click is consumed rather than becoming a multi-cursor
edit, one definition follows directly, genuine ambiguity uses the constrained
palette, and references remain in a persistent explorer. Region destinations
use `SFMNavigationFramingPolicy` and emit machine-readable observations: every
one of the 90 observations across Auto plus GUI scales 1 through 8 kept its
landmark visible; all 88 fit-capable cases retained the line/document left
edge, while Auto and scale 8 each emitted one explicit
`line-left-does-not-fit` fallback.

The canonical Java-owned `sfm:spatial/coverage/run` action is shared by tests,
puppets, and `sfm.exe` remoting. The final declared source-navigation puppet
completed all nine layouts. Every layout exercised nine definition fixtures,
three persistent reference-row opens, actual Minecraft mouse-callback input,
context actions, reveal, coverage, performance, and actionable toasts. The
isolated desktop could not update GLFW's polled cursor, but the posted native
mouse message updated Minecraft's real callback coordinates exactly; the
artifact records both observations rather than claiming GLFW evidence. Every
layout met its frame/input/allocation budgets.

The Auto coverage report sampled 1,203 rendered points and recorded
classification 1203/1203, navigation 495/495, real gesture 1203/1203,
provider branch 15/15, boundary 1203/1203, and reciprocity 272/272, with zero
unclassified/navigation gaps and zero typed exceptions. Its file state is
honestly `partial` only because the configured 4,096-semantic-query budget left
ranked next candidates; it records 322 certified-region reuses/cache hits and
does not relabel budget incompleteness as complete corpus proof. Auto also ran
the out-of-process control CLI successfully and wrote
`platform/minecraft/runGameTestPreview/sfm-artifacts/spatial-coverage/remoting-output-statement-auto/{coverage-report.json,coverage-map.json,coverage-heatmap.png}`.

**Work:**

- Replace identifier-only `SymbolHit` gating with a canvas-region capture that
  asks the registered providers. Preserve fast cached hover and generation
  invalidation.
- Close SD-18 so an immediate Ctrl+click cannot mutate editor cursors merely
  because the asynchronous hover result has not arrived. Captured region,
  document generation, pointer gesture, and action intent remain stable; stale
  completion still fails closed.
- Underline the full applicable region and show the link pointer for the
  preferred Ctrl+click candidate. One target follows directly; multiple targets
  use the constrained command palette with reason/landmark metadata.
- Make Ctrl+click, right-click, Alt+Enter, F12, and Alt+F7 share captured region
  and provider output while retaining their intended default action.
- Register the canonical coverage action and expose it through the existing
  remoting/puppet invocation path so an agent or user can produce document or
  workspace evidence without modifying a test. Keep canvas ownership in Java
  and reject semantic-map/layout generation mismatches.
- Open a destination region using explicit framing. Keep the target and left
  document/line edge visible with positive horizontal inset whenever they fit
  together. If they do not, keep the chosen landmark usable and maximize
  deterministic leading context. Preserve the top inset for first-line
  destinations, but choose sensible vertical reveal for mid-document targets
  without pretending line 1 must remain visible. Emit the full framing
  observation and typed clipping reason.
- Preserve the working `LocalizationEntry` and `String` journeys while fixing
  every UX-SYM regression.

**Validation:** focused controller/panel/geometry tests, synthetic canvas hit
tests, full Java suite, and one self-orchestrating source-navigation puppet at
Auto plus GUI scales 1 through 8. The framing matrix includes first-line and
mid-file destinations, narrow/wide panels, short/long lines, tabs, wide Unicode
glyphs, pan/zoom, reused/new entries, and a target region larger than the
viewport. The puppet exports its spatial coverage report, exact chosen
relations, and framing observations.

```pwsh
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMJavaCanvasInteractionRegionsTests --wait-for-build-lock
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMCanvasSpatialCoverageSnapshotTests --wait-for-build-lock
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMNavigationFramingPolicyTests --wait-for-build-lock
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMSymbolHoverStateMachineTests --wait-for-build-lock
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMJumpToDefinitionActionTests --wait-for-build-lock
sfm-propagate-changes.exe run compile --branch 1.19.2 --wait-for-build-lock
sfm-propagate-changes.exe puppet run title_screen_output_statement_source_navigation --branch 1.19.2 --variant declared --wait-for-build-lock
```

**Completion criteria:** The exact failing and working symbols behave correctly
through real canvas input; punctuation/braces expose deliberate candidates;
ambiguous choices use the palette; target framing cannot place the relevant
horizontal document region off-panel when it fits, reports the deterministic
fallback when it cannot, and never hides a fit-capable left edge; and the live
coverage artifact has zero unclassified or navigation-uncovered strict-Java
glyphs for its fixtures.

### [x] SS-5 Repair the exact post-goal Java navigation and stale-result regressions

**Completion notes (2026-08-18):** Navigation requests now carry an
immutable witness for the document address/content, panel entry, semantic and
provider generations, and request generation. Cursor, selection, hover, and
mouse-release changes no longer discard an otherwise valid explicit request;
changed document bytes reject with the precise
`DOCUMENT_CONTENT_CHANGED` reason, while removed/replaced panels, stale
generations, and superseding requests remain guarded. Reusing an editor tab
atomically focuses and publishes the exact destination range, including when a
deferred editor acquires its delegate.

Focused Java tests and the full suite pass (1,187 passed, zero failed, one
intentionally property-gated installed-worker integration aborted). The
stale-document puppet proves zero navigation and zero disk mutation at
`platform/minecraft/runGameTestPreview/puppet-artifacts/`
`title_screen_stale_document_navigation__stale-document-navigation__1280x720_auto.json`.
The natural Auto plus GUI-scales 1 through 8 matrix passed 9/9 at
`platform/minecraft/build/sfm-toolchain/artifacts/game-test-preview/runs/`
`title_screen_man-20260818-184051-229`; every variant resolves the exact
qualified owner/member, Forge loader, and varargs-constructor targets through
EditorV3 and records the canonical source/destination evidence. The shared
closure is implementation commit `d6947fa84` plus the installed revision/hash
recorded under CLI-AST 0.12.4.

**Ownership join:** CLI-AST 0.12.4 owns corrected Java facts, locked Forge
source derivation, and direct/worker parity. This item owns Java consumption,
request-validity policy, real EditorV3 behavior, and natural-use evidence. It
does not duplicate the Rust parser/index.

**Work:** Add the exact `TranslatableContents`, fully qualified
`SFMModCompat.isComputerCraftLoaded()`, and `FMLJavaModLoadingContext` locations
from the 2026-08-18 manual report to the live source-navigation matrix. Require
Ctrl-hover, Ctrl+click, F12, Alt+Enter, and right-click to use the corrected
region target over every target glyph and to open the same canonical
definition.

Replace whole-`SFMContextContribution` equality as the asynchronous result
validity test. Capture one immutable request witness containing originating
workspace/panel entry, document resolver/address/content hash, semantic region
and provider generation, and request generation. Ordinary cursor movement,
selection change, hover repaint, or mouse release after submission must not
invalidate an explicit request over the unchanged witness. Panel removal,
document/address/content replacement, provider-generation mismatch, or a newer
superseding request must still cancel/reject the old result. The message
`Jump to definition ignored because the editor document or cursor changed`
must be replaced with precise typed reasons that do not blame the cursor when
the document changed.

**Validation:** Focused Java tests cover cursor/selection movement, unchanged
document, changed bytes at the same path, panel-stack replacement, removed
panel, stale semantic generation, out-of-order completions, and exact
supersession. A natural-use puppet opens both production files and proves all
three targets plus a deliberate stale-document rejection through actual canvas
input.

```pwsh
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMJumpToDefinitionActionTests --wait-for-build-lock
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMJavaCanvasInteractionRegionsTests --wait-for-build-lock
sfm-propagate-changes.exe puppet run title_screen_manual_symbol_regressions --branch 1.19.2 --variant declared --wait-for-build-lock
```

**Completion criteria:** All reported expressions navigate in game; harmless
cursor/selection movement cannot discard a valid result; genuinely stale
results cannot navigate; every rejection names the changed witness dimension;
and the puppet records source/destination addresses, regions, bounds, request
identity, and final framing.

### [x] SS-5a Preserve exact reopened source identity and CRLF hover coordinates

**Completion notes (2026-08-19):** Manual follow-up found two post-navigation
regressions. Opening the acquired `TranslatableContents` source discarded its
worker root identity, so Alt+F7/Find References saw the same Forge/Minecraft
combined-deobfuscated physical tree through two equally deep semantic roots
and correctly refused to guess. Holding Ctrl over source after a normalized
line break also applied an LF-projection UTF-16 offset directly to the exact
CRLF baseline, allowing the offset to land inside the CRLF pair and crash the
key handler.

Addressed text documents now retain the exact worker-negotiated resolver,
address scheme, root id, source set, and report prefix selected while opening
a definition. Later definition/reference requests authenticate that identity
against the current handshake and use only the exact matching semantic root;
stale identity fails with `ROOT_METADATA_MISMATCH`, and documents that truly
lack provenance remain ambiguous rather than guessing. Navigation refuses to
reuse the same physical dependency document under a different semantic root.
Canvas hover capture now translates normalized projection offsets through
logical line plus Unicode-scalar column before addressing the exact baseline;
an actually incompatible projection yields an absent text hit instead of an
input-handler exception.

**Validation:** `SFMDrawCanvasScreenTests` covers the historical first glyph
after LF-to-CRLF normalization, astral Unicode, and incompatible projection.
`SFMDefinitionContextAdapterTests` models the real Forge userdev and Minecraft
roots sharing one physical source tree, proves exact retained selection,
identity-less ambiguity, and stale-identity rejection.
`SFMSymbolDefinitionPaletteTests` proves navigation carries the identity into
the deferred read and will not reuse bytes opened under the other semantic
root. The full Java suite passes with only its intentional property-gated
installed-worker test aborted. No dependency or lockfile changed.

**Completion criteria:** Ctrl-hover cannot address the interior of CRLF or
crash key handling; a definition-opened acquired source can immediately drive
Find References through its original semantic root; stale/missing provenance
is rejected explicitly; and equal physical paths never authorize a semantic
guess.

### [x] SS-5b Invalidate provisional hover answers when semantic evidence changes

**Completion notes (2026-08-19):** Manual follow-up found that holding Ctrl,
visiting several symbols, and returning to an earlier symbol could lose both
its underline and Ctrl+click route even though a fresh right-click definition
action could succeed. A cold Java interaction map allowed the first hover to
query from a lexical fallback region and cache `UNRESOLVED`; when the richer
map arrived, the hover identity still contained only document plus glyph
range, so the provisional negative answer was incorrectly terminal for the
mapped region.

Hover identity now includes the semantic kind, navigation offset, optional map
region id, map fingerprint, and semantic generation. Lexical fallback and a
mapped semantic region therefore cannot share a terminal result merely because
they paint the same glyphs. A changed map fingerprint/generation also rejects
answers from older semantic evidence, while revisiting an unchanged mapped
symbol still restores its bounded cached positive result immediately.

**Validation:** `SFMSymbolHoverStateMachineTests` reproduces lexical misses on
two symbols, upgrades both to one semantic-map snapshot, leaves and revisits
them, and proves that mapped evidence bypasses the negative cache while later
revisits restore the actionable underline without another lookup. The focused
state-machine and text-editor-panel suites pass. The complete Java suite exits
successfully with only its intentional property-gated installed-worker test
aborted, and canonical 1.19.2 compilation passes. No CLI/Rust tool, dependency,
or lockfile changed; user installation is not required.

**Completion criteria:** An answer derived from lexical fallback cannot hide a
later interaction-map link over the same glyph range; changing any semantic
map identity dimension invalidates the old terminal answer; revisiting a known
mapped link restores its underline and Ctrl+click ownership; and cancelled or
stale completions remain unable to repaint the current target.

### [x] SS-6 Add action-backed symbol inspection and deterministic copy projections

**Completion notes (2026-08-18):** One immutable symbol-inspection
snapshot now feeds hierarchical aggregate and granular copy actions for
details, file, line, column, bounds, logical path, and Access Transformer
reference. Reports preserve the captured resolver/address/source/content and
semantic evidence, deterministic field ordering, and a copyable CLI replay;
unresolved or unrepresentable values remain explicit and are never guessed.
The Java symbol context provider no longer initializes Minecraft global
language state in headless tests: branch evidence comes only from the explicit
worker-branch property, otherwise replay truthfully reports that it is absent.

Snapshot, formatter, context-provider, action, clipboard, and race tests pass
within the 1,187-test Java acceptance run. The 9/9 manual-symbol matrix at
`platform/minecraft/build/sfm-toolchain/artifacts/game-test-preview/runs/`
`title_screen_man-20260818-184051-229` opens the real contextual surface,
copies exact symbol evidence, and visibly receives the shared confirmation
toast. The shared closure is implementation commit `d6947fa84` plus the
installed revision/hash recorded under CLI-AST 0.12.4.

**Work:** Capture one immutable `SymbolInspectionSnapshot` from the exact
clicked/cursor semantic region. It must remain useful even when definition
resolution is incomplete and contain, where available: document
resolver/address/root/source set/path/content hash; line, Unicode column, byte
offset, text span and selected glyphs; document/canvas/screen 2D bounds;
semantic region id/kind and logical ancestor path; symbol kind/owner/name/
descriptor/canonical selector; confidence/completeness/diagnostics; definition
and reference outlinks; and a copyable direct CLI replay command.

Register hierarchical contextual actions that format projections from that one
snapshot rather than recapturing mutable focus:

- `sfm:symbol/copy/details`
- `sfm:symbol/copy/file`
- `sfm:symbol/copy/line`
- `sfm:symbol/copy/column`
- `sfm:symbol/copy/bounds`
- `sfm:symbol/copy/logical-path`
- `sfm:symbol/copy/access-transformer-reference`

The aggregate details report composes those same projections in a versioned,
deterministic, paste-friendly format. Access Transformer output uses the
existing typed selector grammar only for representable static symbols;
locals/punctuation/unresolved syntax report an explicit unavailable reason and
are never guessed. Right-click and Alt+Enter offer the same actions for the
captured region. Clipboard success flows through NX-4a feedback.

**Validation:** Unit and context-provider tests cover resolved types/fields/
methods/constructors, overloaded descriptors, locals, punctuation, Unicode,
CRLF, unresolved and ambiguous targets, logical ancestors, all coordinate
projections, exact clicked-versus-later-focused races, AT representability,
stable aggregate ordering, replay command quoting, and clipboard bytes. The
manual-regression puppet copies and artifacts details for each of the three
reported failures before and after resolution.

**Completion criteria:** A user can right-click any captured Java semantic
region and copy a truthful diagnostic/replay report; every granular action and
the aggregate agree byte-for-byte on shared fields; AT references are exact or
explicitly unavailable; and unresolved symbols remain inspectable rather than
becoming information-free `NO_SYMBOL` toasts.

## Phase NX — Navigation and explorer experience

### [ ] NX-1 Add spatially targetable branching navigation history

**Work:** After selection/explorer X-10 freezes pane/entry/component identity,
add an append-only location event ledger and per-pane branch cursor. Register
back, forward, list-branches, and choose-branch actions over explicit set-valued
pane selectors. Map mouse Back/Forward through the normal input-binding system.
Opening a new destination after Back creates a branch and retains the old
forward lineage. Events carry generic content address and capability/provider
identity, so source editors prove the first journey without preventing settings,
screens, explorers, or other presenters from participating later.

**Validation:** pure event/tree tests, nested split/stack/spatial-selector
tests, restart-persistence decision assertion, and a live back/new-branch/
choose-old-branch journey.

```pwsh
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMNavigationHistoryTests --wait-for-build-lock
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMWorkspacePaneSelectorTests --wait-for-build-lock
sfm-propagate-changes.exe puppet run title_screen_branching_source_navigation --branch 1.19.2 --wait-for-build-lock
```

**Completion criteria:** Mouse and palette navigation agree; history never
silently truncates; every event records source/destination region, projection,
pane/entry identity, provider/relation, and timestamp/order.

### [ ] NX-2 Add a truthful editor location control

**Work:** Replace ambiguous title-only editor chrome with a full-width exact
location control that truncates only visually. Tooltip, narration, copy, and
the opened virtual location document preserve the canonical value. Mouse and
keyboard activation use one registered action and preferred-editor capability.
The location document has its own non-recursive virtual address and starts
read-only until retarget/revision authority is designed.

**Validation:** literal file, dependency/JDK source, virtual document, Unicode,
read-only, narrow panel, GUI-scale, preferred/explicit editor, exact copy, and
self-address round trips.

```pwsh
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMTextEditorLocationControlTests --wait-for-build-lock
sfm-propagate-changes.exe puppet run title_screen_editor_location --branch 1.19.2 --variant declared --wait-for-build-lock
```

**Completion criteria:** A user can always identify and copy the open document
address, including the address of its address document, without title guessing
or recursive text-derived authority.

### [x] NX-3a Adapt reference-result rows to the shared ItemStack presenter

**Completion notes (2026-08-18):** Reference-result nodes now publish typed,
presentation-only metadata from `SFMSymbolReferenceResultRepository`, and the
ordered `SFMSymbolReferenceExplorerPresenter` resolves that metadata through
the existing active theme: hierarchy/category rows use chest, Java file/span
rows use cocoa beans, and ordinary/unknown source or information rows use
paper. The source address is retained only as display metadata and does not
grant resolver/filesystem authority. Unrelated contributed schemes with no
repository-owned metadata continue through the generic `[D]`/`[F]` fallback,
and earlier registered presenters retain precedence. Both
`SFMExplorerFilePresentationTests` and
`SFMSymbolReferenceExplorerResolverTests` passed, and the final complete JUnit
suite retained them. All nine source-navigation layouts captured a persistent
references panel; visual inspection of the Auto artifact confirmed cocoa-bean
ItemStacks on Java rows with no `[D]`/`[F]` marker, while focused tests retain
the exact chest/container and paper/ordinary fallback ids and presenter
precedence. No dependency or lockfile changed.

**Dependency/boundary:** Contextual-plan C-4a and its theme-backed
file/extension presenters are complete. The reference-result schema and source
addresses remain owned by contextual C-8 and CLI-AST Phase 0.12.2/0.12.3.
This task is a bounded Java presentation adapter, not a new icon registry,
reference schema, or semantic explorer.

**Work:** Teach reference-result category/container, source-file, and source-span
rows to expose enough typed source-kind/address metadata for the completed
ordered presenter pipeline. Use the theme-backed chest ItemStack for hierarchy
containers, cocoa-beans for Java source/file spans, and paper for ordinary or
unknown file spans. Keep disclosure geometry independent from icon choice,
preserve labels/narration/provider precedence, and do not grant filesystem
authority merely because a reference row points at a file address. Truly
unclaimed unrelated schemes may retain the generic marker; the
`MutableComponentType` reference journey may not.

**Validation:** Extend `SFMExplorerFilePresentationTests` and reference-result
resolver/presentation tests for category/file/span rows, Java/ordinary files,
unavailable theme fallbacks, contributor precedence, narration, and
expandable-versus-leaf parity. The existing source-navigation puppet must
capture a persistent references panel whose visible rows contain real
ItemStacks and no `[D]`/`[F]` marker.

```pwsh
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMExplorerFilePresentationTests --wait-for-build-lock
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMSymbolReferenceExplorerResolverTests --wait-for-build-lock
sfm-propagate-changes.exe puppet run title_screen_output_statement_source_navigation --branch 1.19.2 --variant declared --wait-for-build-lock
```

**Completion criteria:** Natural find-references usage displays themed
ItemStack icons for every visible built-in reference-result row, including the
reported `MutableComponentType` journey, without changing the generic fallback
for unrelated contributed domains or introducing a parallel presentation
system.

### [ ] NX-3 Add explorer contextual actions and semantic Java children

**Work:** Route right-click through the shared contextual palette for the exact
clicked node. Add Copy as Path, Open as Root in New Explorer, and appropriate
reveal/open actions for files and directories. Add a resolver-backed lazy Java
semantic relation so a `.java` file can expand into package/type/nested type/
member/signature/body/statement landmarks and can itself become an explorer
root.

**Validation:** clicked-versus-focused races, file/directory actions,
authorization, lazy query counts, stale index, large file, semantic child
navigation, and file-as-root. Live proof uses `LexerAdapter.java` and `langs`;
NX-3a separately owns reference-result presentation.

```pwsh
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMExplorerContextActionTests --wait-for-build-lock
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMJavaSemanticExplorerTests --wait-for-build-lock
sfm-propagate-changes.exe puppet run title_screen_java_semantic_explorer --branch 1.19.2 --wait-for-build-lock
```

**Completion criteria:** The explorer is a general hierarchy view, not a
filesystem-only leaf list; actions target exactly what was clicked; and Java
structure appears lazily with themed icons and navigable region addresses.

### [x] NX-4 Replace transient feedback with actionable toasts

**Completion notes (2026-08-18):** The former transient workspace message is
now one addressable queue with stable non-reused ids, bounded text, reverse
lifetime progress, hover/choice leases, exact copy text, per-instance pin and
resume-from-remaining-time, action-backed exact dismiss, stale-id safety,
keyboard/narration routes, and workspace-close disposal. Scale feedback was
migrated into this queue and retains effective-auto text, fade, boundary repeat,
and shake instead of using a second renderer. `SFMWorkspaceToastTests` passed
inside the complete JUnit suite. In every Auto/1..8 live journey,
`copied_exact_text`, positive preserved pinned lifetime,
`dismissed_exact_instance`, and `later_message_visible` were all true; the
later failed lookup remained independently readable after dismissal. Nine JSON
artifacts and nine screenshots are colocated under
`platform/minecraft/runGameTestPreview/{puppet-artifacts,screenshots}` with the
`output-statement-source-navigation-actionable-toast` stem.

**Work:** Introduce a generic toast queue/model with stable ids, bounded text,
visible reverse-lifetime bar along the bottom edge, hover pause, left-click
copy, right-click contextual actions, per-instance Pin/Stop Timer Forever and
Dismiss, and explicit screen-close policy. Back Dismiss with the canonical
`sfm:toast/dismiss <toast-id>` action so mouse, keyboard, constrained palette,
and automation share one semantic path. Dismiss removes the addressed toast
immediately, closes any choice surface owned by that toast, and does not silence
future messages or alter timer preferences; an expired/stale id must not dismiss
a newer replacement. Opening its right-click choice holds an interaction lease
so the toast cannot expire underneath the user. Pin is the state presented as
Stop Timer Forever; Unpin/Resume Timer continues the previously remaining
lifetime rather than resetting it. Closing the owning workspace disposes its
queue, while an overlay/constrained choice does not. Controllers publish
semantic messages without owning timers or hit tests. Evolve/migrate the
existing P-2.6 workspace scale toast
rather than layering a second renderer, preserving its auto/effective text,
fade, repeat-at-boundary, and shake behavior. Preserve shake/fade as optional
presentation for other producers.

**Validation:** timer/pause/progress, monotonic time, overlap/coalescing,
copy text, pin/unpin, dismiss by mouse/keyboard/action, stale/expired dismiss,
right-click interaction lease, remaining-lifetime resume, workspace-close
disposal versus constrained-choice retention, future same-kind message after
dismiss, keyboard access/narration, screen resize, GUI scale, message
replacement, and retained P-2.6 scale-toast fade/effective-auto/boundary-shake
behavior. Live proof
deliberately produces a failed definition lookup and leaves enough time to
read, copy, pin/unpin, and dismiss it, then proves a later message still appears.

```pwsh
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMWorkspaceToastTests --wait-for-build-lock
sfm-propagate-changes.exe puppet run title_screen_actionable_toasts --branch 1.19.2 --wait-for-build-lock
```

**Completion criteria:** Failure feedback never flashes for one unreadable
frame; a user can pause, copy, pin, inspect, and immediately dismiss exactly
the intended toast; dismiss never becomes persistent silence or targets a
replacement; existing scale feedback retains its proven behavior; and future
silence preferences can be added without replacing the toast contract.

### [x] NX-4a Confirm clipboard-copy actions through the shared toast queue

**Completion notes (2026-08-18):** Toast left-click and symbol-copy
actions now use the shared action-backed copy-feedback path. A successful copy
publishes one distinct bounded confirmation without replacing, dismissing,
repinning, or resetting the source toast; failures cannot claim success and
rendering cannot recursively invoke copying. Focused queue/action tests pass.
The natural puppet at
`platform/minecraft/build/sfm-toolchain/artifacts/game-test-preview/runs/`
`title_screen_act-20260818-184609-885` proves exact clipboard bytes, source
toast preservation, visible `Copied notification 2 to the clipboard`
confirmation, pinned lifetime behavior, exact dismissal, and a later identical
toast remaining independently visible. The shared closure is implementation
commit `d6947fa84` plus the installed revision/hash recorded under CLI-AST
0.12.4.

**Work:** Route a toast's direct left-click through the registered
`sfm:toast/copy <toast-id>` action instead of bypassing action feedback with a
raw clipboard call. On success, enqueue a distinct bounded confirmation such as
`Copied notification <id> to the clipboard`; do not replace, dismiss, repin, or
reset the copied source toast. Use a dedicated replacement/coalescing key for
repeated clipboard confirmations so rapid copies remain readable without an
unbounded confirmation pile. Programmatic rendering or toast publication must
never recursively trigger a copy; copying a confirmation is merely another
explicit user action. Apply the same confirmation contract to SS-6 symbol-copy
actions.

**Validation:** Test pointer/action/keyboard parity, exact clipboard bytes,
source-toast lifetime and pin state, stale ids, repeated copies, confirmation
replacement, copying the confirmation itself, later unrelated messages,
narration, and workspace disposal. Extend the natural puppet to visibly click
a failure toast, assert the clipboard, and capture both source and confirmation.

**Completion criteria:** Every successful user-initiated toast or symbol copy
produces one readable confirmation through the same queue; failures do not
claim success; the source notification is untouched; and no recursive or
unbounded toast behavior exists.

## Phase CP — General capability selection and previews

### [ ] CP-1 Generalize implementation selection beyond text editors

**Work:** Define typed capability requests/providers, availability reasons,
preferred/explicit/ask resolution, and safe fallbacks. Adapt existing preferred
text-editor behavior without changing user-visible defaults. Prove a second
small capability. Retain a built-in palette/chooser bootstrap even if palette
implementations become contributed later.

**Validation:** deterministic precedence, unavailable preferred/explicit,
ask list, persisted preference, explicit override, provider unload/failure,
bootstrap recovery, and existing editor-opening regressions.

```pwsh
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMCapabilityProviderRegistryTests --wait-for-build-lock
sfm-propagate-changes.exe run compile --branch 1.19.2 --wait-for-build-lock
```

**Completion criteria:** Text editor is no longer a one-off implementation
registry, and no provider can make the command palette needed for recovery
unavailable.

### [ ] CP-2 Add bounded action metadata and preview contributions

**Work:** Add localized aliases/search terms and a separate preview-contributor
registry keyed by action/context/purpose. Allocate a clipped region; isolate
render state; support loading, unavailable, error, cancellation, and disposal;
record render/update budgets. Prove one static ItemStack/text/image preview and
reuse existing action icons.

**Validation:** alias ranking, localization, clipping/scissor, malicious bounds,
slow/error/stale contributor, resource disposal, narration, no selection, and
rapid selection changes.

```pwsh
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMActionPreviewHostTests --wait-for-build-lock
sfm-propagate-changes.exe puppet run title_screen_action_preview --branch 1.19.2 --wait-for-build-lock
```

**Completion criteria:** The selected palette action can explain itself with a
bounded rich preview, while a broken contributor cannot block input or corrupt
the rest of the screen.

### [x] CP-3 Research Create Ponder and Panopticon before virtual-world design

**Work:** On the locally cloned exact revisions, identify scene/world creation,
block placement, block-entity lifecycle/ticking, render-target/camera handling,
animation timeline, cleanup, threading, and extension points. Record licenses
and separate transferable patterns from version-specific APIs. Compare
Panopticon's applicable inspection/observation model. Do not copy production
code during this task.

**Validation:** a source-linked research note with a smallest-possible proposed
SFM experiment and explicit falsification of any incorrect initial hypothesis.

**Completion criteria:** CP-4 can be estimated from evidence rather than the
unverified assumption that Ponder is simply a ticking client world in a widget.

**Completion evidence (2026-08-17):** The source-linked audit recorded above
identified Create's exact local registration/scene extension seams, current
Ponder's controlled schematic-level lifecycle, instruction scheduling,
reset/replay behavior, separate 3D/overlay passes, host-owned input, and
block-entity restoration caveats. It also established that Panopticon is a
server-only sampling/cache/backpressure reference rather than a renderer. The
version and MIT/All-Rights-Reserved/LGPL boundaries are explicit, and CP-4's
smallest inert-scene experiment is defined without copying reference code.

### [ ] CP-4 Prove one deterministic simulated preview

**Work:** First acquire and fingerprint the exact Ponder 1.0.82 engine source
artifact before using any version-specific API; do not freeze behavior from the
newer local branch. Then reuse the episode/timeline plan to run one bounded seeded preview with
input/output/final-state purposes, fixed tick/time limits, observation
artifacts, and cleanup. Start with an inert block or existing GameTest fixture;
only then consider placed versus primed TNT and an SFM program episode.

**Validation:** deterministic replay hash, tick/resource bounds, cancellation,
screen close, world isolation, and visual timeline artifact.

```pwsh
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMActionSimulatedPreviewTests --wait-for-build-lock
sfm-propagate-changes.exe puppet run title_screen_simulated_action_preview --branch 1.19.2 --wait-for-build-lock
```

**Completion criteria:** A palette preview can show a deterministic simulated
state without touching the live world, leaking entities/block entities, or
inventing a second replay/timeline format.

## Phase SS-7 — Shared selections, comments, languages, and release

### [ ] SS-7 Adapt semantic/spatial regions to shared selections and comments

**Work:** Join selection/explorer X-9. Project ordered editor ranges and
semantic/canvas regions into typed path expressions and immutable named
selection revisions while preserving direction, primary identity, source hash,
domain, and projection. Adapt comments to pinned regions and outlink-derived
selectors without storing pixel enumerations.

**Validation:** multi-cursor, cross-document, source/canvas round trip,
selection algebra, comment migration, stale source/layout, and visualization.

```pwsh
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMSpatialSelectionAdapterTests --wait-for-build-lock
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMReviewSelectionCompatibilityTests --wait-for-build-lock
```

**Completion criteria:** Explorer, editor, navigation coverage, and comments use
one selection/path ledger with domain-specific adapters and reproducible pins.

### [ ] SS-8 Add Rust and close the release evidence matrix

**Work:** Add a Rust semantic-region/outlink adapter after Java contracts are
stable. Run representative SFM Java and Rust coverage; record unsupported
language/features honestly. Feed reports into the release review plan without
equating automated coverage with human approval.

**Validation:** Java/Rust fixture suites, source/index fingerprints, live Java
journey, full required SFM test/audit gates, and human inspection handoff.

```pwsh
cd platform\cli\sfm-propagate-changes
.\check-all.ps1
sfm-propagate-changes.exe test run --branch 1.19.2 --no-capture --wait-for-build-lock
sfm-propagate-changes.exe run compile --branch 1.19.2 --wait-for-build-lock
sfm-propagate-changes.exe puppet run title_screen_output_statement_source_navigation --branch 1.19.2 --variant declared --wait-for-build-lock
sfm-propagate-changes.exe audit --branch 1.19.2
```

**Completion criteria:** Language support is explicit and versioned, release
artifacts retain the reports, and no missing adapter is hidden as an empty
successful result.

## Completed expanded implementation goal — 2026-08-18

The completed goal covered **SS-0 through SS-4, NX-3a, and NX-4** in this plan.
Its precise objective was:

> Complete SS-0, SS-1, SS-2, SS-3, SS-4, NX-3a, and NX-4 in
> `docs/tasks/spatial semantic surfaces outlinks and capability presenters plan.md`:
> freeze and implement versioned spatial-semantic contracts and coverage;
> publish Java semantic regions/outlinks; integrate natural in-game source
> navigation and framing; adapt reference-result icons to the shared ItemStack
> presenter; replace the transient single-toast baseline with an addressable,
> dismissible actionable queue; validate, install affected tooling, update
> plans/changelog, commit all scoped work, and leave an immediately testable
> handoff.

SS-0 is the coordination barrier. Once its schemas and fixture expectations are
committed, SS-2, SS-3, NX-3a, and NX-4 may proceed in parallel; SS-4 is the
coordinator-owned integration lane. Completion requires all of the following:

1. The current planning changes are checkpointed before production edits, and
   every completed task records commands, results, artifacts, source commit,
   and intentional exceptions.
2. Checked-in Java fixtures cover names, annotations, qualified names, both
   delimiter ends, punctuation, literals, comments, Unicode, CRLF, long/tabbed
   lines, first-line/mid-file framing, and outside-canvas space.
3. Versioned domain/region/probe/outlink/framing/coverage contracts round-trip;
   navigation-outlink coverage remains distinct from action-only coverage.
4. Exhaustive fixture truth agrees with certified-region reuse, query-cost
   evidence is emitted, seeded sampling reproduces, and per-file inventory
   cannot omit failed/skipped/stale files. JSON and heatmap artifacts publish
   explicit classification/navigation/gesture/branch/boundary/reciprocity
   denominators.
5. The Java interaction map covers declarations, imports, annotations,
   qualified names, fields, variables, methods, delimiters, punctuation,
   JDK/dependency sources, and reciprocal static definition/reference edges.
6. Natural EditorV3 use underlines the full navigable region and shows the link
   pointer. Ctrl+click, right-click, Alt+Enter, F12, and Alt+F7 share provider
   truth; immediate Ctrl+click cannot fall through to a cursor mutation while
   lookup is pending.
7. `LexerAdapter`, its fields, `@Mod`, `java.io.Serializable`,
   `java.io.Serial`, and `serialVersionUID` navigate correctly while the working
   `LocalizationEntry` and `String` journeys remain regressions.
8. Both delimiter ends and punctuation expose deliberate navigation choices;
   genuine ambiguity uses the constrained command palette with relation and
   landmark reasons.
9. Navigation framing shows the destination and line/document left edge with
   positive inset whenever they fit, and emits the specified deterministic
   fallback/observation when they do not.
10. A canonical coverage action is invokable through tests, puppets, and
    remoting, with exact JSON/heatmap/framing artifact locations in the handoff.
11. Failed-navigation feedback is readable and addressable: reverse lifetime,
    hover pause, left-click copy, right-click Pin/Stop Timer Forever and
    Dismiss, resume-from-remaining-time, choice interaction lease,
    keyboard/action parity, exact-instance stale safety, workspace-close
    disposal, and no suppression of later messages. Existing scale-toast
    behavior is retained.
12. Persistent find-references rows use the shared themed ItemStack pipeline
    (Java cocoa-beans, hierarchy chest, ordinary/unknown file paper) with no
    `[D]`/`[F]` marker in the `MutableComponentType` journey.
13. A self-orchestrating natural-use puppet opens source from Explorer, uses
    hover/Ctrl+click/F12/Alt+F7 and constrained choices, navigates repeated
    definitions/references with correct framing, exercises toast copy/pin/
    dismiss, and captures reference ItemStacks at Auto plus GUI scales 1..8.
14. Focused Java/Rust checks, the complete applicable suites, canonical 1.19.2
    compilation, plan/changelog bookkeeping, final affected-tool installation,
    and operational-readiness evidence pass. Scoped changes are committed and
    the worktree is clean.

**Completion evidence:** Production and test work is retained by commit range
`56ecccc8b^..11508e519`; this plan/changelog ledger follows as a docs/resource
checkpoint. The complete Java JUnit command exited 0, and the installed-worker
integration was then explicitly executed against the final release worker
rather than counted as its default assumption skip. Canonical 1.19.2 compile
exited 0. `platform/cli/sfm/check-all.ps1` passed 47 tests plus generated-Java
verification. `platform/cli/sfm-propagate-changes/check-all.ps1` passed direct
dependency policy, format, Clippy, build, 620 runnable tests (3 intentionally
ignored), and all 10 Java-analysis scenario tests. The final installed-binary
audit exited 0 with 99 known unresolved audit-rule warnings in the two existing
`GuiGraphicsExtractor.text` and `Font.draw` groups; no audit error was hidden.

The dependency graph remained frozen: the goal range contains no changed
Cargo manifest/lock, Gradle dependency declaration/lock, or SFM toolchain
lockfile, and no new repository was cloned or unpinned source acquired.
Deterministic materialization used only existing locked Java/JDK/dependency
sources and caches.

Both affected tools were installed after the final CLI-source commit:

- `G:/Programming/Caches/CARGO_HOME/bin/sfm-propagate-changes.exe` reports
  `0.1.1 (rev 11508e519, built 2026-08-18 09:19:24 -04:00)` and has SHA-256
  `99a952881c5c19b23986bfca8f97371782256cf44a7708302ff01a1d5908dc66`,
  exactly matching the worktree release binary.
- `G:/Programming/Caches/CARGO_HOME/bin/sfm.exe` reports `0.1.0` and has
  SHA-256
  `ceaccf5cfeaa4f9e0a7c554eed219d0cf376dc1a69d2bcc7b191a02fead6c569`,
  exactly matching the worktree release binary.

The user does **not** need to run either installer. To begin manual visual
approval from a closed-game state, run:

```pwsh
sfm-propagate-changes.exe run client --branch 1.19.2 --wait-for-build-lock
```

At the title screen, open an SFM source explorer, open a Java document in
Text Editor v3, then exercise Ctrl-hover/Ctrl+click, F12, Alt+Enter, Alt+F7,
reference rows, and a failed lookup toast. Automation proves routing and
artifacts; subjective readability, icon appearance, pointer feel, and final
human release approval remain intentionally manual. The unresolved next-plan
surface begins with NX-1/NX-2/full NX-3, CP-1+, SS-7, and SS-8; this completed
goal does not silently advance any of them.

**Explicit exclusions:** This goal does not complete X-10, branching Back/
Forward history (NX-1), editor-address chrome (NX-2), full explorer contextual/
semantic hierarchy (NX-3), capability previews (CP-1/CP-2/CP-4), shared comment
adapters (SS-7), Rust-language navigation/release closure (SS-8), propagation,
publication, release tagging, or human visual approval. A cheap painted editor
path is not substituted for the truthful NX-2 design.

## Completed post-goal manual-testing repair batch — 2026-08-18

The completed executable goal was:

> Complete CLI-AST 0.12.4, SS-5, SS-6, X-8c, B-5a, and NX-4a: repair the
> exact `TranslatableContents`, fully qualified `SFMModCompat`, and
> `FMLJavaModLoadingContext` definition failures; make asynchronous navigation
> survive harmless cursor/selection movement while rejecting truly stale
> witnesses; add action-backed aggregate and granular symbol-detail copying;
> preserve materialized root-to-match ancestry in explorer filtering; add a
> visible action-backed Cancel button to every palette-derived surface; and
> confirm clipboard copies through the shared toast queue. Prove the behavior
> with direct CLI scenarios, focused Rust/Java tests, natural in-game puppets,
> GUI-scale evidence, final tool installation, committed bookkeeping, and a
> clean handoff. Keep the dependency graph and checked-in lockfiles frozen;
> use only already-pinned artifacts/source machinery and do not clone new
> repositories or run lockfile-mutating commands.

Observable completion was judged against all of the following:

1. The three exact one-based production probes navigate through direct CLI,
   reusable engine, supervised worker, interaction map, and natural EditorV3
   input; target spans are actionable across all their glyphs.
2. `FMLJavaModLoadingContext` opens reproducible source derived from the
   already-locked `javafmllanguage` artifact, with artifact/tool/source/index
   identity and no ambient-cache guessing.
3. Moving a cursor or selection after submission does not discard a valid
   explicit request; changed document bytes/address, removed/replaced panel,
   stale provider generation, and superseding requests remain safely rejected
   with precise reasons.
4. Right-click/Alt+Enter offers `sfm:symbol/copy/*`; the aggregate details and
   granular projections share one captured snapshot, include replay evidence,
   and remain informative for unresolved/ambiguous syntax.
5. Filtering for `SFM` visibly retains the complete already-materialized
   ancestry of `SFM.java`, distinguishes context rows from matches, reports
   honest counts, performs zero resolver I/O, and restores pre-filter state.
6. Full and constrained palettes expose one Vanilla-like mouse/keyboard Cancel
   button backed by `sfm:palette/close`, with exactly-once cleanup and no action
   execution/history mutation.
7. Clicking a toast to copy uses `sfm:toast/copy`, preserves the source toast,
   and emits one bounded copy-confirmation toast; symbol-copy actions use the
   same confirmation path.
8. The final relevant CLIs are rebuilt and installed after their last source
   mutation; the handoff explicitly says no manual install is required. All
   scoped changes and plan/changelog evidence are committed and the worktree is
   clean.

**Completion notes (2026-08-18):** Implementation commit `d6947fa84` satisfies
items 1 through 7 through the direct scenarios, full Rust/Java suites, focused
installed-worker integration, and natural puppet matrices recorded under the
owning tasks. The installed `sfm-propagate-changes.exe` reports that revision,
has SHA-256
`5427E3F3357FF6ADD12529820515D9B51B303DCFD069D3EA41BC1F2CEFE44855`,
and returned success for all three exact production probes. This bookkeeping
closure satisfies item 8; user install required: no. The next-plan surface is
still NX-1/NX-2/full NX-3, CP-1+, SS-7, and SS-8 and was not silently advanced.

Parallel lanes are intentionally available after fixtures freeze the expected
contracts: CLI-AST 0.12.4 owns Rust analysis/source derivation; X-8c owns lazy
projection; B-5a/NX-4a own small Java UI surfaces; SS-6 owns the pure snapshot
and formatters. One integration owner joins action registration, SS-5 request
validity, puppets, validation, installation, and bookkeeping.

## Operational readiness for each implementation goal

Follow `docs/tasks/goal execution and testing readiness guidelines.md`.

- Target branch/commit must be recorded.
- If `sfm-propagate-changes` or generated runtime tooling changes, rebuild,
  install via `platform/cli/sfm-propagate-changes/install.ps1`, resolve the
  installed executable, and record its revision/hash **after the last relevant
  source/generated-input mutation**. The final handoff must explicitly say
  whether the user needs to run the installer.
- Inspect and, within the bounded testing authority, stop/restart in-scope game,
  Cargo, worker, or cache-lock owners rather than waiting indefinitely.
- Use `--log-file` when buffered stdio hides progress.
- Give one exact manual-test command, expected initial state, artifacts, and
  known manual-only limits.

## Overall completion criteria

- [ ] Every guidance id maps to one owning task and one validation story.
- [ ] Domains, regions, projections, classifications, outlinks, and coverage
  reports are versioned and round-trip across their implementation boundary.
- [ ] Strict parsed-Java coverage has zero unclassified rendered glyphs and
  zero unapproved navigation-uncovered non-whitespace glyphs on its declared
  corpus; non-navigation contextual actions cannot satisfy this line.
- [ ] The declared workspace snapshot inventories every supported Java file
  with a terminal status and separate classification/navigation/gesture/
  branch/boundary/reciprocity denominators.
- [ ] Exhaustive fixtures and seeded fair sampling both exercise the real
  canvas hit path, prove certified-region optimization against exhaustive truth,
  publish query-cost evidence, and emit reproducible artifacts.
- [ ] One canonical document/workspace coverage action/service is invokable by
  tests, puppets, and the remoting CLI; the handoff includes a copyable command
  and exact JSON/heatmap/framing artifact paths.
- [ ] Java semantic regions include punctuation/nested definitions, target
  projections, provenance, and reciprocal definition/reference evidence.
- [ ] Ctrl+click/right-click/Alt+Enter/F12/Alt+F7 share provider truth and use
  the command palette for genuine ambiguity.
- [ ] Every concrete symbol failure in UX-SYM-1 through UX-SYM-3 is fixed
  without regressing the working witnesses.
- [ ] Editor address, branching history, mouse navigation, explorer contextual
  actions/semantic children, exact easy-case/fallback target framing, toasts,
  and ItemStack reference presentation pass focused and live evidence.
- [ ] Capability selection is generalized safely and at least one bounded rich
  preview is proven; world simulation remains gated by source-backed research.
- [ ] Shared selection/comment adapters and later Rust support reuse existing
  ledgers rather than creating parallel models.
- [ ] Release evidence states source/index/layout fingerprints and never treats
  automation as human approval.

## Risk register

| Risk | Guardrail |
| --- | --- |
| Canvas-primary design discards useful text/source identities | Explicit versioned projections and round-trip/loss tests for every retained linear coordinate. |
| “100% coverage” becomes a misleading count of sampled points or silently omits files | Inventory every workspace file first; separate classification, navigation, gesture, branch, boundary, reciprocity, exhaustive-fixture, and sampled-corpus measures; publish denominators and terminal file states. |
| A generic action hides missing Ctrl-click navigation | Strict parsed-Java navigation policy and approved witnessed exceptions; action-only and unapproved non-whitespace no-navigation both fail. |
| A region claim skips a narrow contradictory area | Boundary/adversarial probes, subdivision on disagreement, and exhaustive truth comparison on small fixtures. |
| Region optimization degenerates into one remote query per pixel/glyph | Publish semantic-query/reuse/subdivision counters and enforce fixture-equivalent query budgets before corpus sampling. |
| Random tests are flaky or repeatedly explore nearby glyphs | Stored seed/policy, deterministic maximin ordering, strata, and replayable position list. |
| Semantic-distance metric hard-codes one bad theory | Versioned feature vector/weights and comparison against exhaustive fixtures; keep alternatives observable. |
| Java map is huge or stale | Compact nested regions, generation tags, bounded queries/transfer, cancellation, and stale rejection. |
| Punctuation behavior is nearest-token guessing | Syntax-kind provider rules with explicit provenance; strict negative fallback tests. |
| Immediate Ctrl+click arrives before hover lookup and creates an editor cursor | SD-18 captures/consumes link intent independently of lookup completion and proves pending/non-action/stale paths. |
| Definition/reference reciprocity overclaims dynamic Java | Typed ambiguity/generated/dynamic/unresolved exceptions with witness; never silently pass. |
| Spatial pane selector changes target during async work | Resolve once, capture stable pane/entry ids, revalidate generation before publication. |
| Branching history leaks or grows unbounded | Immutable compact events, explicit retention policy gate, and no source snapshots duplicated unnecessarily. |
| Address text grants filesystem authority | Resolver capability and source identity remain separate from visible/copyable canonical text. |
| Destination framing hides the line start or target at some scale/panel width | Per-axis fit/fallback contract, pure geometry matrix, and machine-readable framing observations at Auto/1..8 GUI scales. |
| Third-party provider order or failure destabilizes built-in navigation | Owner-qualified deterministic ordering, tie diagnostics, bounded failure isolation, and provenance in every candidate. |
| A delayed Dismiss action removes a newer toast that reused screen position or coalescing key | Stable non-reused toast ids; capture exact id in the action draft; stale/expired ids fail without index/position fallback; replacement tests. |
| NX-4 or NX-3a duplicates existing toast/icon infrastructure | Explicit P-2.6/C-4a ownership joins; migrate/adapter-only implementation; retained scale-toast and contributor-precedence regressions. |
| Generic capability system cannot recover if palette provider fails | Permanent built-in bootstrap chooser and tested explicit fallback. |
| Preview code corrupts render state or stalls input | Allocated clipped host, state restoration, budgets, cancellation, disposal, and contributor error isolation. |
| Virtual preview mutates the live world or leaks ticking entities | Isolated world/episode contract, deterministic seed/tick budget, cleanup assertions, and CP-3 evidence gate. |
| New plan duplicates existing selection/comment/timeline work | Ownership table, exact cross-plan join ids, and one completion ledger per task. |

## Source and reference inventory

- `docs/AGENTS.md`
- `docs/tasks/goal execution and testing readiness guidelines.md`
- `docs/tasks/contextual input actions and addressable explorer plan.md`
- `docs/tasks/typed selections relations and lazy explorers plan.md`
- `docs/tasks/draw editor document regions and commands plan.md`
- `docs/tasks/in-game code review workspace and window manager plan.md`
- `docs/tasks/global comment selection and review sessions plan.md`
- `docs/tasks/cli ast refactoring suite plan.md`
- `docs/tasks/snapshot episodes and deterministic action environments plan.md`
- `docs/tasks/release checkpoint and slim artifact plan.md`
- `platform/minecraft/src/main/java/ca/teamdman/sfm/client/context/SFMContextSpatialProjection.java`
- `platform/minecraft/src/main/java/ca/teamdman/sfm/client/context/SFMContextCanvasTextMap.java`
- `platform/minecraft/src/main/java/ca/teamdman/sfm/client/context/SFMContextSelectionProjection.java`
- `platform/minecraft/src/main/java/ca/teamdman/sfm/client/screen/SFMDrawCanvasDocumentIndex.java`
- `platform/minecraft/src/main/java/ca/teamdman/sfm/client/screen/SFMDrawCanvasScreen.java`
- `platform/minecraft/src/main/java/ca/teamdman/sfm/client/screen/text_editor/SFMTextEditorPanel.java`
- `platform/minecraft/src/main/java/ca/teamdman/sfm/client/symbol/SFMSymbolHoverStateMachine.java`
- `platform/minecraft/src/main/java/ca/teamdman/sfm/client/symbol/SFMDefinitionResult.java`
- `platform/minecraft/src/main/java/ca/teamdman/sfm/client/action/SFMClientAction.java`
- `platform/minecraft/src/main/java/ca/teamdman/sfm/client/screen/workspace/SFMScreenMultiplexer.java`
- `platform/minecraft/src/main/java/ca/teamdman/sfm/client/screen/explorer/SFMExplorerPresentationRegistry.java`
- `platform/minecraft/src/main/java/ca/teamdman/sfm/client/screen/explorer/SFMFileExtensionExplorerPresenter.java`
- `platform/minecraft/src/main/java/ca/teamdman/sfm/client/screen/explorer/SFMFilePathExplorerPresenter.java`
- `platform/minecraft/src/main/java/ca/teamdman/sfm/client/screen/workspace/timeline/`
- `platform/minecraft/src/test/java/ca/teamdman/sfm/client/context/SFMContextSpatialProjectionTests.java`
- `platform/minecraft/src/test/java/ca/teamdman/sfm/client/context/SFMTextEditorContextProjectionTests.java`
- `platform/minecraft/src/test/java/ca/teamdman/sfm/client/screen/SFMDrawCanvasDocumentIndexTests.java`
- `platform/minecraft/src/test/java/ca/teamdman/sfm/client/screen/explorer/SFMExplorerFilePresentationTests.java`
- `platform/minecraft/src/test/java/ca/teamdman/sfm/client/symbol/SFMSymbolReferenceExplorerResolverTests.java`
- `platform/minecraft/src/test/java/ca/teamdman/sfm/client/symbol/SFMSymbolHoverStateMachineTests.java`
- `platform/minecraft/src/gametest/java/ca/teamdman/sfm/gametest/puppet/definition/TitleScreenOutputStatementSourceNavigationGamePuppet.java`
- `G:\Programming\Repos\Create`
- `G:\Programming\Repos\Create\src\main\java\com\simibubi\create\CreateClient.java`
- `G:\Programming\Repos\Create\src\main\java\com\simibubi\create\foundation\ponder\CreatePonderPlugin.java`
- `G:\Programming\Repos\Create\src\main\java\com\simibubi\create\infrastructure\ponder\AllCreatePonderScenes.java`
- `G:\Programming\Repos\Create\src\main\java\com\simibubi\create\foundation\ponder\CreateSceneBuilder.java`
- `G:\Programming\Repos\Panopticon`
- `G:\Programming\Repos\Panopticon\26.1.2-neoforge\src\main\java\net\mokich\panopticon\seed\RegionScan.java`
- `G:\Programming\Repos\Panopticon\26.1.2-neoforge\src\main\java\net\mokich\panopticon\seed\BiomeScan.java`

## Source-message re-audit corrections — 2026-08-17/18

The original planning pass captured every broad subsystem, but this second
atom-by-atom pass found acceptance wording that could still permit a lossy
implementation. These are corrections, not optional embellishments:

| Re-audit finding | Corrective ids/evidence |
| --- | --- |
| “Actionable” could pass when a glyph only had Copy/Inspect and Ctrl-click still had nowhere to navigate. | SURF-7, COV-10, SD-2, SS-1..SS-4 now require a separate strict navigation-outlink dimension. |
| “Classify the corpus” did not force every supported workspace file to appear, so a failed or skipped file could vanish from a 100% result. | COV-9, SD-19, SS-2/SS-3 and overall completion now require a snapshotted per-file inventory and terminal states. |
| Coverage tests could remain trapped behind private fixtures with no way to ask for the current document/workspace report. | COV-12 and SD-23 require one Java-canvas-owned service/action invokable by tests, puppets, and the remoting CLI, with copyable invocation and artifact paths. |
| Returning an applicable region did not explicitly require a soundly certified extent or prove that query reduction matches exhaustive truth. | SURF-8, COV-11, SD-20 and SS-1/SS-2 now require witness + certified region, subdivision/bisection on disagreement, equivalence, and query-cost counters. |
| Destination projection covered percentages and semantic children but did not explicitly retain the user's first-line/100th-line generator example. | LINK-4 and `SFMNavigationProjection` now include bounds-checked nth source row/line as a parameterized projection. |
| Brace behavior named only the opening side even though both delimiters participate in the semantic relation. | LINK-3 and SS-0/SS-3 now require direction-aware behavior at both ends. |
| Provider extensibility was architectural prose without deterministic third-party conflict/failure acceptance. | LINK-12 and SD-22 add owner-qualified registration, ordering, isolation, authority, and provenance evidence. |
| Rich action metadata named aliases/icons but did not explicitly preserve the active hotkey/chord display mentioned in the source message. | CAP-4 now requires keybinding-presentation truth alongside localization, aliases, icons, and search terms. |
| “Keep the left edge visible” did not define when that is geometrically possible, what wins on a long line, or how a test observes the decision. | UX-FRAME-1..UX-FRAME-3, SD-21 and SS-4 now define the easy-case invariant, deterministic constrained fallback, and `SFMNavigationFramingObservation`. |
| The operational handoff could install a CLI and then modify its source again before completion. | The linked goal-readiness guide now requires the final freshness/install check after the last relevant mutation and reports final process/runtime state. |
| The initial actionable-toast wording made pinning/copying explicit but left Dismiss only implicit in completion prose. | UX-TOAST-2, SD-11, and NX-4 now define `sfm:toast/dismiss <toast-id>`, exact-instance/stale safety, mouse/keyboard/action parity, and the rule that dismiss does not silence future messages. |
| The expanded observable goal could accidentally duplicate completed toast/icon systems or quietly pull in unrelated pane/history work. | Ownership joins now name P-2.6 and C-4a as completed baselines, NX-3a is bounded to reference presentation, and the recommended-goal exclusions defer X-10/NX-1/NX-2/full NX-3. |

## Intent audit evidence — 2026-08-17/18

- **Pass 1 — extraction:** Split the request into canvas/linear domain
  coexistence; finite and sampled coverage; exploration/exploitation and
  semantic distance; whole-workspace file accounting; distinct classification
  versus Ctrl-click navigation coverage; certified-region query efficiency;
  explicit punctuation/delimiter direction; nested semantic regions; region
  target projections and exact fit/fallback framing; zero-to-many outlinks;
  general/third-party providers; reciprocal
  definition/reference checks; typed selection/domain theory; general
  capability selection; aliases/icons/previews; Create/Panopticon research;
  editor address; spatial panel selectors; explorer context/semantic children;
  concrete symbol/framing failures; branching history; actionable toasts with
  exact-instance dismiss; and reference icons.
- **Pass 2 — traceability:** Assigned every atom to SURF/COV/LINK/DOM/CAP/UX
  ids, then mapped those ids to SS/NX/CP work and evidence. Cross-linked X-9,
  X-10, contextual actions, CLI analysis, comments, episodes, and release rather
  than copying their ledgers. The 2026-08-18 expansion adds exact NX-3a/NX-4
  ownership links back to completed C-4a/P-2.6 baselines.
- **Pass 3 — adversarial omission:** Rechecked canvas versus string authority,
  exhaustive versus random coverage, geometric versus semantic distance,
  generic action versus actual navigation, explicit no-action versus
  unclassified, fixtures versus complete workspace inventory, certified region
  versus unchecked region expansion, destination region versus cursor offset,
  fit-capable versus impossible left-edge framing, definition versus reverse
  references, preference versus explicit provider choice, static versus
  animated/world preview, editor content address versus address-document
  identity, pane versus panel-entry identity, and working versus failing symbol
  witnesses. The plan does not claim that a generic nearest token, an
  action-only classification, an omitted file, a sampled percentage, or a
  passing analyzer-only test proves the requested canvas navigation behavior.
  Rechecked natural in-game behavior versus diagnostics-only evidence,
  exact-toast-id dismiss versus screen-position removal, migration versus a
  second toast/icon system, and ambitious scope versus unrelated pane/history
  expansion.
- **Fresh-agent resumption check:** The next implementation goal is exactly
  SS-0 through SS-4, NX-3a, and NX-4. Checkpoint the planning documents first;
  use SS-0 as the schema barrier; then parallelize SS-2, SS-3, NX-3a, and NX-4
  before coordinator-owned SS-4 integration. Treat the August 16 C11 artifact
  as historical, run actual workspace/screen coordinate and natural-use puppet
  tests rather than only injected range/state tests, preserve explicit
  incompleteness in references, keep navigation coverage distinct from generic
  actions, inventory every corpus file, verify certified-region query savings
  against exhaustive truth, retain P-2.6/C-4a behavior, and prove any installed
  worker test did not skip. Do not begin X-10/NX-1/NX-2/full NX-3, comments,
  previews, Rust-language support, propagation, publication, or release.
- **Known source limitation:** The phrase comparing the desired value model to
  “XCD” is retained verbatim as DOM-7 because its referent was not established.
  It must not be guessed during implementation. The exact Ponder 1.0.82 engine
  source is not present in the local Create checkout; current official Ponder
  behavior and historical Create engine code are reference evidence, and CP-4
  must fingerprint the exact artifact before version-specific preview
  implementation. This does not block the recommended navigation goal.
