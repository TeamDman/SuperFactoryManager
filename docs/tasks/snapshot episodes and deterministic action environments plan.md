# Snapshot episodes, action traces, and deterministic environments plan

**Plan status:** TE-S1M core and the ordered S1M-X1 through S1M-X6 elastic continuation ladder are complete and locally checkpointed; TE-S2A through TE-S2D are complete; TE-S2E is the next unclaimed ordinary-document trajectory slice
**Primary implementation root:** `D:\Repos\Minecraft\SFM\repos2\1.19.2`  
**Last updated:** 2026-08-22
**Intent audit:** Passed and re-audited 2026-08-22 against the complete temporal-history follow-up, both supplied Excalidraw diagrams, and the earlier undo-tree/frontline-UI and trajectory-machine guidance

## How to update this plan

- `[ ]` Not started
- `[~]` In progress
- `[x]` Complete
- `[!]` Blocked

Record schema decisions, fixtures, commit ids, validation output, visible puppet
evidence, and intentional exclusions beneath the task they affect. Work from
1.19.2. Java-only work uses the installed canonical CLI epoch; changes to the
Rust CLI require `check-all.ps1` and reinstalling the accepted CLI before
parallel Java work consumes its new surface.

## Purpose

Create a deliberately simple, lossless representation of a system state and the
transition history of an episode. The first state model is:

```rust
BTreeMap<RepoPath, BString>
```

The constitutional interchange format is one potentially large JSON file. It
is allowed to duplicate unchanged files and be inefficient. Every future
content-addressed, delta, AST-normalized, or amalgamated representation must be
able to export this ordinary full form. A user or test must always be able to
select any timestep, including `t=0` and the final-frame alias `t=-1`, and
rehydrate a complete independent snapshot.

This foundation supports several related products:

- an in-game timeline/episode inspector;
- exact replay of input events through stateful action engines;
- a generic multiplexer observation/recording layer, proven against an ordinary
  calculator panel for deterministic agent experiments;
- source snapshots and structured comparisons for in-game code review;
- refactoring environments with before/after repositories and compile feedback;
- a capability-controlled virtual shell and virtual filesystem;
- future Vox transport between Java/Minecraft and Rust tools;
- sync.in-like playback and branching of document creation;
- reusable editing recipes learned from recorded semantic actions rather than
  requiring the user to write a program;
- safe writable source experiments in branch-backed false universes;
- guided editor test chambers and structured user-study sessions;
- one correlated Java/Rust observation stream viewable in-game;
- optional locally authenticated coding-agent participation through a bounded,
  provider-neutral capability contract; and
- later semantic amalgamation, normalization, compression, or sea-of-nodes
  experiments without making them prerequisites for the dumb format.

## Related plans

- [History Graph, temporal actions, and gameplay overlay user guide](<../history graph temporal actions and overlay guide.md>)

- [In-game code review workspace and window manager](in-game%20code%20review%20workspace%20and%20window%20manager%20plan.md)
- [Global comment selection and review sessions](global%20comment%20selection%20and%20review%20sessions%20plan.md)
- [CLI AST refactoring suite](cli%20ast%20refactoring%20suite%20plan.md)
- [Draw editor layers, commands, and canvas workspace](draw%20editor%20document%20regions%20and%20commands%20plan.md)
- [Spatial semantic surfaces, outlinks, and capability presenters](spatial%20semantic%20surfaces%20outlinks%20and%20capability%20presenters%20plan.md)
- [Contextual input, action ownership, and addressable explorer](contextual%20input%20actions%20and%20addressable%20explorer%20plan.md)
- [SFM client log console](sfm%20client%20log%20console%20plan.md)
- [Typed selections, relations, and lazy explorers](typed%20selections%20relations%20and%20lazy%20explorers%20plan.md)
- [SFM in-game control CLI](sfm%20in-game%20control%20cli%20plan.md)
- [Vox terminal bridge and graceful degradation](vox%20terminal%20bridge%20and%20graceful%20degradation%20plan.md)

This plan owns generic snapshots, episodes, timelines, and deterministic
replay. The spatial/capability plan owns command-palette preview contribution
and uses these episode contracts for animated/simulated previews; it must not
invent a second time/replay format.

This plan is the authoritative temporal spine. It owns immutable revisions,
branches, event/action histories, exact replay, semantic rebase, recipes,
episode inspection, and safe experimental environments. It does **not** absorb
the other plans' domain models: typed selections own path/region expressions;
spatial semantic surfaces own glyph geometry, outlinks, coverage masks, and
rich capability presenters; comments own review annotations and approval;
Draw/Text Editor V3 owns editing/rendering behavior; the log plan owns runtime
capture adapters and presentation; the terminal plan owns PTY lifecycle and
terminal protocol; and the in-game CLI owns discovery and remote invocation.

## Authoritative user guidance ledger

| ID | Active guidance | Required plan consequence and proof |
| --- | --- | --- |
| TE-1 | A document session must be replayable like sync.in, including a timeline scrubber that shows how the document was produced. | Episodes retain raw input, semantic actions, transitions, and complete seekable document frames; TE-S1 includes a visible in-game scrubber and replay artifact. |
| TE-2 | Physical key events alone are useful provenance but semantic operations such as “select all occurrences” and “insert decimal number sequence” must remain visible and reusable. | The trace is layered as raw input → binding decision → semantic action → transition → observation. Semantic actions, not inferred keystrokes, are the reusable unit. |
| TE-3 | Inserting actions earlier in history must allow a later numbering action to include newly introduced list items. | Historical insertion creates a child branch. Exact replay preserves old witnesses; semantic rebase re-evaluates stored queries and records new witnesses. Neither operation mutates an existing revision. |
| TE-4 | Wall-clock time, logical action position, and branch-relative history are distinct; a timestamp such as `2026-08-20T13:03-Toronto` must not be ambiguous. | Records retain wall-clock provenance, monotonic logical sequence, and immutable branch/revision identity. Public selectors spell whether they mean “recorded by wall time” or “state at logical position on branch.” |
| TE-5 | Relative/absolute addressing in a temporal medium should learn from R1C1 without blindly inheriting spreadsheet assumptions. | Phase 0 defines revision-qualified absolute anchors and relative anchors whose base is explicit. An action-list index is only meaningful inside one pinned immutable revision. |
| TE-6 | Selections are surfaces over typed domains, while documents and explorer locations are addressable state. Text may be line-like or freely positioned in 2D. | A document revision is not collapsed into a selection. A selection expression targets regions within a revision-qualified domain and stores evaluator/version plus a resolved witness. Text defaults to source order; freeform 2D ordering is explicit. |
| TE-7 | Provide the concrete “replace selected hyphens with 1, 2, 3…” operation, optionally targeting a selection query. | The provisional canonical action is `sfm:text/selection/replace/decimal_sequence`, with target document, selection expression, start, step, formatting, and ordering. A shorthand may use focus/current selection, but the recorded invocation is self-contained. |
| TE-8 | A user should be able to perform a transformation once and reuse it on another document without writing code. | A recorded concrete episode may be promoted to a typed recipe with explicit parameter holes and capability requirements. The first version is deterministic/manual promotion, not opaque LLM synthesis and not an extension of the SFM factory-program DSL. |
| TE-9 | Ctrl+Alt+J-style “select all occurrences” and multiple cursors must work in the 2D editor without assuming every glyph lies on an evenly spaced line grid. | The editor contributes semantic region identities and geometry; selection evaluation is independent of raster line height. Exact ordering is a named policy with stable tie-breakers. |
| TE-10 | Source exploration/editing must not clobber the real checkout; the current read-only source view needs a safe path to writable experiments. | Writable sessions materialize an immutable source snapshot into a bounded ignored overlay/false universe. Save mutates only the branch overlay; applying/exporting to ambient source is a separate explicit capability. |
| TE-11 | Build Portal-like test chambers/tutorials for multiple cursors and editing fluency, including speed trials and “transform document A into B.” | A chamber declares initial snapshot, goal predicate/expected snapshot, capabilities, prompts, and scoring. Correctness/safety remain hard gates; elapsed time, action count, and trace compression are separate metrics. |
| TE-12 | Automate the facilitator role for self-user-studies and retain structured narration markers. Audio is useful; full video is presently too much information. | Puppets can run scripted chambers and capture episode data, screenshots, text, and facilitator markers. Audio is a later explicit opt-in artifact with consent/retention controls; continuous video is deferred. |
| TE-13 | Java Log4j/translatable messages and Rust `tracing` events should become one correlated end-to-end stream that can be inspected in-game without opening a terminal. Java and Rust records should retain truthful clickable source provenance, and SFM-authored semantic logs should support English plus the user's locale. The same stream should also be attachable from an exact external or in-game Teamy Terminal session without making terminal transport authoritative. | The log plan owns `LOG-X0`/`LOG-X1A`/`LOG-X1B`/`LOG-X1C`: measured sink topology precedes claims about `latest.log`; Rust retains tracing callsite metadata; Java uses measured SFM-scoped caller capture plus an optional hash-checked build manifest for exact URI/column/span; wrapper loggers preserve the application caller; semantic records retain message id/key, English fallback, typed arguments, locale projection, and throwable; and effective capture/location/rendering policy has explicit precedence. `LOG-X2A` supplies Java scope ancestry across async handoffs, while `LOG-X2B` proves the exact combined `run client --log-file` path and honest stream termination. Adapters publish a versioned observation envelope with runtime, session, span/correlation ids, sequence, wall time, structured fields, rendered projections, provenance confidence, and bounded errors. `LOG-X6` adds exact launch/game/run/stream attachment for shell and terminal consumers while preserving LOG-X3 as an optional rich panel. Logs are observations, never replay authority. |
| TE-14 | A Teamy Terminal that launched Minecraft should be attachable from inside the game as another device manipulating the same terminal session. | Terminal sessions remain independent from presentation. External window and in-game panel attach to one addressable PTY/session with explicit observer/input leases and multi-writer arbitration; no duplicate PTY is implied. |
| TE-15 | Codex should eventually help users write SFM programs and review changes, using the user's existing local Codex setup where possible. | Phase 8 defines a provider-neutral external-agent contract and an optional Codex adapter around supported local SDK/app-server or CLI surfaces. It inherits local authentication/permissions, embeds no credentials, and receives only bounded revision/selection/capability context. |
| TE-16 | Release review comments target spatial surfaces across files and historical before/after states, while static analysis and AI review should reduce duplicated human inspection. | Review lanes pin immutable snapshot revisions; comment selectors and approvals remain in the comment plan. Static facts/outlinks and agent suggestions are provenance-bearing observations, never automatic approval. |
| TE-17 | Semantic navigation should strive for complete glyph-surface coverage, rich outlinks, reciprocal definition/reference evidence, and full-document raster/coverage artifacts. | The spatial semantic plan remains the owner. Episodes pin the source revision, semantic-map revision, click position, chosen outlink, and resulting navigation so coverage and behavior are replayable. |
| TE-18 | Actions and candidate presenters should be registry/capability based so command palettes, editors, previews, and external mods can supply alternatives. | Episode actions carry stable registry ids and presenter/provider revisions. Preference resolution is recorded; rich image/panel/world previews remain owned by the capability-presenter plan. |
| TE-19 | Review comments may be created through Excalidraw/ShareX-like text, rectangle, arrow, and freehand markup over one or several laid-out historical documents. | The comment plan owns durable markup/comment association; Draw/spatial plans own primitives, layout projections, and region resolution. Markup pins snapshot/selection/projection evidence rather than treating transient screen pixels as source identity. |
| TE-20 | Undo must form a tree: undoing and then performing a new action must not clobber prior redo descendants or projected alternatives. | A history head moves among immutable state revisions. New work after undo appends another child. Redo enumerates/selects children; automatic deletion requires a separate explicit retention action. |
| TE-21 | Dynamic intent and its concrete outcome are both first-class. “Open selected document” or “select all occurrences” must retain the query/intention and the exact document/regions it resolved at that time. | ActionIntent, ActionEvaluation, resolved witness/outcome, and StateRevision are separate linked records. The same intent may have several evaluations under different parents. |
| TE-22 | Rewriting an earlier action can make downstream outcomes expensive, different, invalid, or computationally irreducible; the UI must not pretend every future is instantly known. | Branch projection is lazy, bounded, cancellable, cached by pinned inputs, and visibly reports projected/running/materialized/conflict/unknown/external-barrier states. Re-evaluation never blocks the render thread. |
| TE-23 | The frontline UI should show actions live, expose non-linear branch points, and let the user inspect or choose how an action is replayed. | `sfm:panel/open sfm:episode/history` opens a reusable live History Graph panel beside ordinary workspace panels. It shows state/action/evaluation nodes, the current head, child branches, witnesses, outcomes, and exact-versus-recompute controls. |
| TE-24 | Ctrl+Z must not unexpectedly rewind an entire live Minecraft world merely because all actions are recorded. | Undo resolves an explicit/focused undo domain. In a writable editor it moves that document history head; the global episode records the movement. Global checkout is an explicit History Graph/action operation and respects transition replayability. |
| TE-25 | History, FPS, size diagnostics, and similar information should eventually be visible over active gameplay without replacing the whole screen. | The workspace plan owns a non-pausing SFM overlay layer with passive and interactive focus modes. TE-S1 uses the existing split-panel host first; overlay hosting is a separate bounded successor. |
| TE-26 | Layout behavior must remain action-addressable and puppetable; showing an overlay and positioning it are separate composable actions rather than one action per corner. | Typed overlay visibility, placement, focus, and layout-state actions target explicit selectors and are invokable from command palette, `sfm.exe`, puppets, or agents. F3 offers actions, not bespoke mutations. |
| TE-27 | Overlay/panel placement needs one general coordinate/constraint model, and restoring a preset must not depend on replaying every historical layout action. | Runtime UI is a versioned declarative scene/layout state mutated by actions. Presets persist that state plus optional provenance. Placement uses an explicit reference frame, normalized anchors, logical offsets, size constraints, and z-order. |
| TE-28 | A complete controlled journey—title screen, palette, explorer selection, document open, edit—must demonstrate how changing an earlier selection affects later dynamic actions without losing the original history. | A later whole-workspace fixture records focus/selection dependencies, then compares recorded-state checkout, frozen-witness replay, and intent re-evaluation after selecting another document. External/irreversible effects remain typed barriers. |
| TE-29 | The history UI should feel machine-like: a clear instruction pointer identifies the next planned action while the actual history head identifies the committed state. | A versioned `TrajectoryMachineState` keeps actual head, selected trajectory revision, instruction pointer, projection frontier, supervision contract, status, and remaining budget separate. Moving one never silently moves another. |
| TE-30 | The selected future should appear as a line through branching history, like a pathfinding debug view, while alternatives and search work remain inspectable. | The History Graph layers committed edges, projected candidate edges, the selected route, executed prefix, open/closed search sets, frontier, rejected barriers, and target. Styling and narration never present a projection as committed history. |
| TE-31 | Given a starting document and a supervised target, the system should be able to seek the least-cost known action sequence. | A deterministic A*/Dijkstra planner searches only a declared finite action generator under a versioned non-negative cost policy and goal predicate. “Shortest” is scoped to that action library/policy; optimality is never claimed over unregistered or irreducible actions. |
| TE-32 | A supervised document is more than byte equality. It may require invariants, safety limits, evidence, and eventual human approval. | A `SupervisionContract` separates target predicates, hard invariants/forbidden effects, required evidence, execution budget, and approval state. Automation may produce `SUPERVISION_READY`; it cannot manufacture human approval. |
| TE-33 | Planning must not execute stale witnesses or smuggle speculative effects into history. | Every planned step names expected parent hash, intent, evaluation policy, predicted witness/outcome, effect class, and cost. Step/run verifies preconditions; divergence pauses at the instruction pointer and offers frozen execution, re-evaluation, or bounded replanning as distinct operations. |
| TE-34 | Replanning or changing an earlier parameter must preserve old proposed routes just as undo-then-edit preserves old committed branches. | Trajectory plans are immutable revisions attached to their start/goal/policy inputs. Replan creates a sibling plan revision; selecting a new route changes a trajectory head, not historical plan contents. |
| TE-35 | The first proof should make semantic editing visibly preferable to simulated typing without pretending that a tiny chamber solves general program synthesis. | The temporal-numbering chamber supplies a finite action catalog containing semantic select-all/decimal-sequence actions and deliberately more expensive literal edits. A* must choose a minimum-cost route in that catalog, and exhaustive/Dijkstra oracle tests prove the claim. |
| TE-36 | Candidate histories should be scrub-able before execution just like committed histories, without scrubbing accidentally materializing them. | A candidate-frame address pins trajectory-plan revision, route, step position, predicted state hash/status, and evaluator evidence. Seeking is read-only and visibly handles not-yet-projected or invalidated frames. |
| TE-37 | The comment system should annotate candidate states, actions, transitions, routes, and document regions so projected alternatives can be reviewed before execution. | Comment targets gain a typed candidate-trajectory anchor while retaining ordinary pinned source/selection witnesses. Candidate comments are visually and semantically distinct from comments on committed history. |
| TE-38 | Replanning, executing, or invalidating a candidate must not silently move its comments or turn them into approval of committed content. | Candidate comments stay pinned to their plan revision. Exact state-hash correspondence may offer an explicit promotion/link to a committed target; changed or ambiguous correspondence requires witnessed migration and never transfers human approval implicitly. |
| TE-39 | Time estimates must not become a scope cliff when autonomous work proceeds faster than expected. | TE-S1M has an ordered elastic continuation ladder. The core is completed and checkpointed first; one stretch item is claimed at a time, has its own tests/commit, and cannot weaken or retroactively redefine core acceptance. |
| TE-40 | Temporal editing must be useful in an ordinary Text Editor V3 and in the command-palette input; it cannot remain a special capability of the decimal-numbering chamber. | TE-S2 extracts one reusable per-document history owner and makes the chamber an adapter/fixture over it. Every writable text surface can opt into the same immutable history and focused-domain actions. |
| TE-41 | High-fidelity physical events and intuitive undo units are both required. Rapidly typing `open the` must not become one destructive undo unit, but aggregation must not erase key press/release timing or character provenance. | Store an append-only raw ingress journal, immutable mutation/state transitions, and a separately versioned semantic-transaction projection with exact source-event/state witnesses. Initial grouping produces word runs, standalone whitespace/punctuation, and explicit paste/completion/action boundaries. |
| TE-42 | Ctrl+Z and Ctrl+Shift+Z must work in generic editors and command-palette input, preserve alternate descendants, and expose ambiguous redo instead of deleting or guessing. | Add explicit `sfm:document/history/undo` and `sfm:document/history/redo` actions over a document selector. One eligible child may be followed; several open a constrained choice. The recorded invocation resolves `focused` to an exact document/history identity. |
| TE-43 | The approachable history view should primarily show the linear append-only sequence the user experienced, even though undo and later edits form a non-linear state graph. | Keep the immutable state DAG as authority and derive an append-only narrative projection. Undo/redo/checkout append head-movement nodes and draw jump edges to the referenced state; they never splice the narrative or hide retained branches. |
| TE-44 | The frontline history UI should be a tangible 2D canvas with circles, arrows, and readable labels, including an action/state pairing such as `type hello` beside resulting document `hello`. | TE-S2 renders action/head-movement nodes and resulting state nodes in paired lanes, supports pan/zoom/selection/hover, and lets the selected state be inspected without requiring an RL/debug chamber. The supplied Excalidraw diagrams are acceptance references. |
| TE-45 | Top-down layout should be the readable default because it leaves horizontal room for labels, while users must be able to transpose to left-right and inspect dense candidate graphs without permanent labels on every node. | Layout orientation is a reversible projection preference. A deterministic layered baseline lands first; dense level-of-detail shows labels for selected/hovered/current-route nodes. Spectral embeddings remain a pluggable later strategy, not a prerequisite or authority source. |
| TE-46 | Document pathfinding is fundamentally a comparison between a current document and a desired document. A user should be able to open two editors and ask how the left document can become the right one. | TE-S2's pairwise planner accepts two explicit document selectors/revisions, derives a supervision target from the desired document, and opens ordinary candidate-history canvases. Test chambers become fixtures supplying those same arguments rather than owning another planner. |
| TE-47 | A planned route, a committed event history, and the raw input stream are related projections, not one graph with overloaded node meaning. | The schema and UI explicitly distinguish raw-event journal, semantic transactions, committed state DAG, append-only experienced narrative, and candidate search graph. Every derived node carries provenance back to canonical events/states. |
| TE-48 | Broader multi-document, selection, workspace, and external-process behavior should compose from proven document primitives instead of forcing one monolithic undo manager into the first slice. | After single-document history and pairwise document planning pass, higher-order state can be represented as typed documents whose actions reference other documents/selections. Irreversible effects remain barriers; this is a later adapter slice, not TE-S2's first goal. |
| TE-49 | Command-palette history should help the user advance through the grammar rather than forcing a complete historical command. Typing `open` should prefer bare `sfm:panel/open`, accepting it must leave `sfm:panel/open/right` discoverable until the user deliberately types a space, related open actions may share compatible argument history, and missing arguments must be named visibly. | The contextual-input plan's B-0 slice owns completion-frontier kinds, boundary-first nonblank history ranking, insertion policy, explicit parameter-history scopes, dynamic selector/value completions, and Vanilla/Brigadier-style smart-usage hints. TE-S2 consumes that repaired palette but does not duplicate it. |
| TE-50 | Current trajectory/pathfinding behavior is technically interesting but too tucked into an RL-oriented episode surface for ordinary use. | TE-S2's first user journey starts with a normal blank text editor and normal palette; its history canvas follows natural typing/undo/redo. Pairwise planning is introduced only after that ordinary flow is usable. |

## Guidance routing and non-duplication audit

| Concern | Authoritative owner | Temporal integration recorded here |
| --- | --- | --- |
| Revision-qualified comments and release approval | Global comment/review plan | Comments pin snapshot/branch identities and may consume episode/static-analysis observations. |
| 2D glyph regions, outlinks, full-document rasterization, coverage masks, and command previews | Spatial semantic surfaces plan | Event captures and semantic actions reference exact region/map revisions; episode artifacts may embed derived masks or screenshots. |
| Path expressions, set-valued selections, explorer locations, and resolvers | Typed selections/explorers plan | Selection expressions are stored with evaluator revision and exact resolved witness. |
| Freeform text geometry, multiple cursors, selection drawing, and editing | Draw/Text Editor V3 plan | Editor operations publish typed actions/transitions and can run against an episode-owned overlay. |
| Runtime log capture and in-game console | SFM client log console plan | Structured records attach as observations using episode correlation ids. |
| PTY ownership, rendering, and input transport | Vox terminal plan | One terminal session can have multiple presentation devices and an explicit input lease. |
| Shell-to-game discovery and invocation | SFM in-game control CLI plan | Future `episode`, `logs`, and `agent` commands use its existing typed multi-instance control plane. |
| Graphical review markup | Global comments + Draw/Text Editor V3 + spatial semantic surfaces | Markup references a pinned comment selection and document-layout projection; geometry supplies interaction/presentation but does not replace source identity. |
| Panel/overlay scene graph, placement, focus, and persistence | In-game workspace/window-manager plan | Layout transitions are episode actions/observations; the workspace model remains authoritative for current UI state. |
| Trajectory planning, supervision contracts, instruction-pointer execution, and route projection | This plan | Domain plans contribute typed action generators and predicates; they do not independently own a second planner, history graph, or execution cursor. |
| Command-palette completion, history ranking, insertion, argument hints, and streamed candidate UX | Contextual-input plan | Temporal document history records palette input and invocations; it does not fork Brigadier or maintain a second completion ranker. |

## Guidance traceability

| Guidance | Plan coverage | Evidence when complete |
| --- | --- | --- |
| TE-1 through TE-5 | 0.1–0.5, 1.1–1.5, 2.7/2.8, TE-S1 | Cross-language schema fixtures, immutable fork/hash proof, clock/address fixtures, live branch scrubber, and action-history artifacts |
| TE-6 through TE-9 | 0.4/0.5, 4.4–4.7, selection X-9/X-3a | Query/witness/order round trips, multiple-cursor and decimal-sequence tests, exact/recompute branch comparison |
| TE-10 through TE-12 | 5.0–5.4, TE-S1 | Checkout-unchanged proof, writable overlay puppet, chamber score/trace/screenshots, and opt-in media policy before audio work |
| TE-13 | 9.1/9.2 and LOG-X0, LOG-X1, LOG-X1A, LOG-X1B, LOG-X1C, LOG-X2, LOG-X2A, LOG-X2B, LOG-X3–LOG-X6 | Correlated Java/Rust structured records retain measured sink provenance, truthful source locations, semantic bilingual projections, async span ancestry, and honest stream completeness; they remain queryable and visible through either a rich in-game panel or exact terminal/shell attachment, and are retained only as episode observations rather than replay authority. |
| TE-14 | 9.3 and terminal T-SHARED-1 | One PTY/session with independent native/in-game attachments, observer/input/resize leases, and lifecycle trace |
| TE-15 | 8.1–8.3 | Optional local-provider tests, bounded context/capabilities, provenance, cancellation, and ordinary no-Codex operation |
| TE-16 through TE-19 | Comment Phase 3–7/6a, spatial SS-7/SS-8, Draw tools, CLI-AST | Pinned multi-revision comments, graphical markup witness, static rule/morphism evidence, full-document coverage masks, and human approval artifact |
| TE-20 through TE-24 | 0.5, 1.5, 2.8/2.9, selection X-3a, TE-S1 | Undo-undo-do branch retention, child chooser, intent/evaluation graph, focused-domain Ctrl+Z, lazy projection statuses, and whole-workspace fixture |
| TE-25 through TE-27 | Workspace Track 1c | Passive/interactive gameplay overlay puppet, independent visibility/placement actions, declarative preset round trip, and CLI/action parity |
| TE-28 | 2.9 and Track 7 | Two-document full-journey episode proving checkout/frozen-witness/recompute alternatives and typed external barriers |
| TE-29 through TE-35 | 0.6, 1.6, 2.10/2.11, TE-S1M | Machine-state fixture, A*/oracle minimality tests, route/debug projection, stale-precondition pause/replan, immutable plan branches, and an in-game supervised numbering journey |
| TE-36 through TE-39 | 2.12/2.13, comment Phase 6b, TE-S1M elastic ladder | Candidate-frame random seek, candidate-target comment round trip, explicit promotion/migration proof, and one-at-a-time reversible stretch checkpoints |
| TE-40 through TE-45 | TE-S2A through TE-S2D | Generic editor and palette undo/redo, raw-to-semantic witness artifacts, retained redo descendants, and a natural 2D action/state canvas with linear and transposed projections |
| TE-46 through TE-48 | TE-S2E through TE-S2G | Two ordinary document panels produce a bounded candidate transformation graph; higher-order adapters follow only after single/pairwise proofs |
| TE-49 | Contextual-input B-0 joined by TE-S2C | Boundary-first `open` completion, deliberate-space insertion, shared compatible argument history, dynamic selector suggestions, and visible smart-usage placeholders |
| TE-50 | TE-S2 natural puppet | A normal editor/palette journey, not a chamber-only diagnostic, supplies the acceptance screenshots and content/history artifacts |

## Intent-audit evidence — 2026-08-21 re-audit

- **Pass 1 — extraction:** Reread the complete attached messages at
  `C:\Users\Teamy\.codex\attachments\88125277-36c0-444b-a1fe-7ae7586cdf10\pasted-text.txt`
  and
  `C:\Users\Teamy\.codex\attachments\5c845238-c930-4255-84d2-2e747d24faad\pasted-text.txt`,
  plus the complete undo-tree/frontline-UI follow-up. Preserved replayable document creation, raw key
  provenance, semantic numbering, historical insertion, branching, wall versus
  logical time, R1C1-like addressing, 2D selections and order, reusable
  no-code recipes, multiple-cursor teaching, safe source experiments,
  structured studies, audio versus video scope, Java/Rust observations,
  shared terminal attachment, Codex participation, and the release-review
  motivation as separate guidance ids rather than one summary sentence. Added
  TE-19 through TE-28 for graphical markup, undo trees, intent/outcome duality,
  bounded projection, live history UI, undo domains, gameplay overlays,
  composable placement, declarative presets, and the full selected-document
  counterfactual journey. The trajectory-machine follow-up is retained as
  TE-29 through TE-35: independent machine cursors, selected-route rendering,
  bounded A*/Dijkstra planning, supervision contracts, stale-step safety,
  immutable replans, and a deliberately scoped semantic-editing proof. The
  candidate-review/stretch follow-up is preserved as TE-36 through TE-39:
  read-only candidate scrubbing, candidate comments, explicit promotion and
  migration, and an ordered continuation ladder that is not bounded by an
  unreliable duration estimate.
- **Pass 2 — feasibility and reuse:** Routed existing selection, semantic
  surface, comment, editor, log, terminal, and live-game CLI contracts to their
  current owner. Confirmed that Rust already uses `tracing`, Java already has
  Log4j/translatable records, terminal sessions are already addressable, and
  the editor already exposes document snapshots/save seams even though source
  documents currently open read-only. No second selection, terminal, log, or
  review model is proposed.
- **Pass 3 — adversarial ambiguity:** Split exact replay from semantic rebase;
  branch identity from timestamps; document identity from selection; source
  order from canvas order; concrete traces from reusable recipes; observations
  from authoritative actions; AI suggestions from approval; and attached
  terminal presentation from PTY ownership. Also split current layout state
  from action provenance, focused editor undo from global episode checkout,
  dynamic intent from recorded witness/outcome, passive overlay rendering from
  interactive focus, and materialized history from speculative projection.
  The trajectory re-audit additionally split actual history head from
  instruction pointer and search frontier, planned candidates from committed
  transitions, supervision-ready from human-approved, and shortest-under-one
  finite action graph from impossible global editing optimality claims. The
  stretch re-audit split candidate seeking from execution, candidate discussion
  from committed approval, immutable plan targets from moving plan heads, and
  core acceptance from optional sequential continuation.
  Historical insertion never edits an immutable episode in place, ambient
  source is never the first writable target, and Codex never receives ambient
  authority merely by being present.
- **Known source limitation:** None for the supplied requirements: both source
  messages and the follow-up were available in full. The sync.in service itself
  was not inspected because its required behavior was supplied directly; R1C1
  and Create/Ponder remain design references, not dependencies or normative
  contracts.

## Intent audit evidence — 2026-08-22 ordinary-history usability extension

- **Pass 1 — extraction:** Re-read the complete current user message and both
  supplied Excalidraw clipboard documents at
  `C:\Users\Teamy\.codex\attachments\edeacac5-dbfd-4f00-8558-aaa3fcb77ec1\pasted-text.txt`
  and
  `C:\Users\Teamy\.codex\attachments\7a12164a-a137-4e3b-a1e6-8fad9d4748ec\pasted-text.txt`.
  TE-40 through TE-50 separately preserve: ordinary-editor and palette undo;
  missing redo; non-destructive undo trees; raw key timing; semantic grouping
  of `open`, space, and `the`; before/after document states; a primary linear
  experienced history with explicit undo jump arrows; paired action/state
  lanes; circles/arrows/labels; top-down and transposed layouts; dense
  labels-on-hover and later spectral layout; two-open-document planning; the
  chamber-as-parameterized-fixture correction; higher-order document
  composition; completion-boundary ranking; shared compatible argument
  history; dynamic `focused`/overlay selector completions; and visible required
  argument names. Neither diagram was reduced to “add a graph UI.”
- **Pass 2 — feasibility and reuse:** Source inspection confirmed that
  `SFMHistoryGraphContract` already retains immutable states, branch edges, and
  head movements; `SFMHistoryGraphPanel` currently projects them as a nested
  text row list; `SFMDrawCanvasScreen` already receives key press/release and
  character events but keeps only a bounded diagnostic string list; and
  `SFMDecimalNumberingChamberPanel` is the only production
  `SFMDocumentHistoryTarget`. TE-S2 therefore extracts a general document
  history owner, retains the chamber as an adapter, and adds projections over
  the existing graph rather than inventing a second episode or planner model.
  The current Brigadier providers already offer `focused`, `all`, exact overlay
  ids, and visibility literals; contextual-input B-0 repairs the palette
  frontier/insertion/ranking path that prevents those values from being
  approachable.
- **Pass 3 — adversarial consistency:** The ordinary append-only view is not a
  destructive linear history: it is a chronological projection over the same
  retained DAG, and head movements append narrative events that point backward
  or forward. Semantic grouping is a versioned projection with raw-event and
  state witnesses, never irreversible event coalescing. Pairwise planning does
  not infer “left” and “right” from mutable focus after invocation; interactive
  shorthand resolves to exact document revisions before recording. Candidate
  search nodes never masquerade as committed history. Spectral embedding is a
  future layout provider and conveys no semantic authority. Multi-document and
  external-process undo are deferred behind typed higher-order documents and
  effect barriers rather than promised by the first generic editor slice.
- **Known source limitation:** The referenced YouTube page was not fetchable by
  the available web reader. Its normative requirement is nevertheless complete
  in the user's message: support high-volume state-space projections whose
  labels appear on hover, with spectral embedding as inspiration rather than a
  mandated dependency or first implementation. No repository clone, package,
  or video download is required by this plan.

## Terms and invariants

### Snapshot

A snapshot is an immutable, complete logical filesystem. Paths are normalized
repository-relative values, never arbitrary ambient host paths. Contents are
bytes; JSON entries distinguish UTF-8 text from base64 bytes rather than
assuming every `BString` is valid JSON text.

The canonical JSON uses a deterministically sorted file-entry array. JSON object
member ordering is not treated as semantic ordering. A canonical snapshot hash
covers schema version, normalized paths, encoding tags, and exact bytes.

Review sessions reference these immutable identities but own their comments,
selection rules, provenance, style rules, evaluation revisions, and migration
lineage separately. A release-review session may contain one before/after
snapshot lane per Minecraft version. It must not invent a second filesystem
snapshot representation, and advancing a lane never mutates the historical
snapshot or comment that targeted it.

Comment rules may select glyphs across several snapshot files and lanes. Their
evaluation results can be stored as derived observations or indexes, but the
canonical source bytes, rule, evaluator version, and witnesses remain sufficient
to recompute the result. Optimized interval/symbol indexes are disposable.

### Document revision, branch, and clock domains

A document is revision-qualified state in an addressable domain. It is not a
selection, although it can be lifted into a singleton path expression. A
selection is a predicate/expression over regions inside one or more explicitly
qualified document revisions.

Every committed episode revision is immutable. V1 history is a single-parent
branch tree: inserting an event or action before an existing suffix creates a
new child revision and never rewrites its parent. Merge commits and concurrent
collaborative conflict resolution are deliberately deferred until branch and
rebase semantics are proven.

Keep these coordinates distinct:

- `recorded_at`: optional zoned wall-clock provenance describing when a record
  was observed;
- `logical_sequence`: monotonic position inside one immutable episode revision;
- `event_id`/`action_id`: stable identities that survive presentation changes;
- `branch_id` and `revision_id`: the immutable history being addressed; and
- environment tick or animation time, when the environment has one.

A bare timestamp is not a canonical state identity. Queries must spell either
“what revision/records existed by this wall-clock instant” or “state after this
logical position on this branch.” An ordinal such as action 12 is only valid
inside its pinned immutable revision. Relative anchors inspired by R1C1 always
name their base revision/region/action; no ambient “current cell” is serialized.

### Episode

An episode revision contains an initial state, ordered input events, action
invocations, state transitions, observations, terminal status, and enough full
frames to rehydrate any advertised timestep. The v1 dumb format may store every
complete snapshot. Later formats may use checkpoints and deltas internally,
but their ordinary exporter must reproduce the same full frames. A branch is a
lineage of immutable episode revisions, not a mutable list disguised by a
moving timestamp.

### Raw event and action invocation

Raw input is not collapsed into action counts. Events have monotonically
increasing sequence numbers plus optional tick/time metadata. Key press and
release, repeat state, focus loss/reset, typed characters, pointer/controller
events, and environment-specific inputs use typed variants.

An action invocation retains:

- stable action id and typed arguments;
- source binding and binding revision when applicable;
- exact source-event range or event ids;
- action sequence number and tick/time;
- availability/authorization decision;
- result, error, or cancellation; and
- state-transition identity.

Tests prefer exact traces such as “only events 21 and 41 invoked
`format_document`” over a weaker call-count assertion.

Semantic actions that consume a query retain both intent and evidence:

- the revision-qualified target document/path expression;
- the selection expression and evaluator/version;
- the named traversal-order policy and version;
- the exact ordered region witness resolved for that invocation;
- precondition and pre-state hashes;
- typed parameters and capability authorization; and
- post-state/transition identity plus diagnostics.

For ordinary Java/text documents, `source_order` is the default: UTF-8/source
positions order selections independently from proportional glyph geometry. A
freeform canvas must request a policy such as `canvas_reading_order_yx`, which
groups top-to-bottom and then left-to-right with a stable region-id tie-break.
Minecraft/document-local canvas coordinates use x-right/y-down; Cartesian
adapters may map y explicitly. Coordinate sign never silently defines semantic
ordering.

### Canonical event journal and semantic transaction projections

The canonical ingress journal is append-only. It stores physical key
press/release/repeat/reset events, `charTyped` delivery, paste/completion
acceptance, pointer/focus events, direct registered-action invocations, and
their timing/order exactly enough to replay the declared input boundary. It
does not infer that a programmatic action was a keystroke and it does not throw
away events after producing a friendlier label.

Every successful document mutation links a before state, after state, cursor/
selection snapshots, responsible raw-event range, and semantic action when one
was invoked. A separately versioned `SemanticTransactionProjection` may group
adjacent mutations for undo and presentation. Its initial deterministic policy
is deliberately legible:

- consecutive printable non-whitespace word glyphs may form one typing run
  only while document, history head, cursor/selection topology, insertion
  direction, and focus remain unchanged and the configured idle bound is not
  crossed;
- whitespace and punctuation terminate the prior word run and form their own
  transactions, so rapidly typing `open the` projects as `type "open"`,
  `click "<space>"`, and `type "the"` rather than one giant undo unit;
- paste, completion acceptance, command submission, semantic editing actions,
  caret/selection changes, focus changes, undo/redo/checkout, and any
  non-contiguous edit close the current transaction;
- deletion and key-repeat runs use explicit transaction kinds and cannot be
  merged across a direction, selection, or intervening action change; and
- every grouped transaction retains ordered source event ids and the complete
  chain of before/after state ids, so another grouping policy can be computed
  without mutating history.

Undo targets semantic transaction boundaries. Exact low-level replay targets
raw events. A UI may expand a semantic node to its raw key actions; neither
representation claims the other never existed.

### Action intent, evaluation, outcome, and state graph

Do not collapse “what the user asked for” into “what happened this time.” The
temporal graph alternates immutable state revisions with action evaluations:

```text
StateRevision(parent-state)
  -> ActionIntent(stable action id, typed arguments, target/query, provenance)
  -> ActionEvaluation(parent-state, evaluator/config revision, policy,
                      resolved witnesses, diagnostics, cost/status)
  -> ActionOutcome(result/error/cancel plus authorized transition)
  -> StateRevision(child-state)
```

One intent may have several evaluations under different parent states. For
example, `open selected document` retains the explorer-selection expression as
intent and `A.java` as its original witness. On another branch it may resolve to
`B.java`; both evaluations remain attached to the same or derived intent with
their own state/outcome nodes.

The UI and API distinguish four operations:

1. **Checkout recorded state** moves an inspection/head cursor to an already
   materialized state without re-executing the action.
2. **Reuse recorded transition** is allowed only for a typed portable/pure
   transition whose preconditions match; arbitrary pixels, logs, or external
   side effects are never applied as patches.
3. **Re-execute with frozen witness** invokes the action against its recorded
   concrete documents/regions and verifies the expected outcome.
4. **Re-evaluate intent** resolves its query against the new parent state and
   records a new witness/outcome.

Every branch edge names the operation/policy used. A suffix rebase may choose
per action; there is no global boolean that silently converts all recorded
outcomes into dynamic queries or vice versa.

### Undo tree, heads, and redo choices

A history owns immutable state/action/evaluation nodes plus one or more named
heads. Undo is a head movement to a parent state and is itself recorded as a
navigation event; it does not delete the departed child. Performing an action
from an ancestor appends another child. Redo means choosing an existing child,
not popping from a destructive stack. When exactly one child is eligible, the
ordinary binding may choose it; when several are eligible, the command palette
or History Graph presents a constrained child choice. Explicit checkout can
select any retained revision allowed by policy.

The canonical actions are provisionally:

```text
sfm:history/undo <history-selector>
sfm:history/redo <history-selector> [child-revision]
sfm:history/checkout <history-selector> <revision>
sfm:history/branch/name <history-selector> <revision> <name>
```

`focused` is a convenience selector resolved before the action record is
committed. Ctrl+Z targets the focused undo domain: a writable editor document,
selection, or workspace may each contribute one. It never implicitly restores
an entire live world. The containing episode still records local head movement
so a whole user session remains inspectable.

Unreferenced branches are retained by default. Comments, recipes, named heads,
exports, and open inspectors pin revisions. Any future garbage collection is an
explicit bounded retention operation with preview and pin diagnostics; closing
a panel or taking another action never silently destroys history.

### Experienced narrative and state-graph projections

One canonical event/state graph supplies several typed projections:

1. the raw ingress journal in receipt order;
2. the semantic transaction narrative in committed observation order;
3. the immutable document state DAG with branch edges and movable heads;
4. the append-only experienced history, including head-movement events; and
5. separately typed candidate search/trajectory graphs.

The ordinary History Canvas opens in projection 4. Its spine is chronological:
new observations append below (or to the right after transposition). An undo
appends an Undo action and the newly current state while a curved jump edge
points to the earlier state revision. Editing after undo continues the
chronological spine, but the departed child remains reachable in projection 3.
Redo similarly appends a head movement; it never rewrites the narrative.

The paired action/state layout uses one lane for action or head-movement nodes
and one lane for the resulting document state. Selecting either side reveals
the exact before/after state, raw-event witness, semantic transaction, branch
identity, and any candidate/committed status. Top-down is the default; a
transpose operation changes only layout. Dense mode may elide labels except
for hovered, selected, current-head, instruction-pointer, target, and selected-
route nodes. Layout coordinates, clustering, and spectral embeddings are
derived observations and never become state identity.

### Projection, reducibility, and effect classes

Changing an early action can invalidate every dependent suffix action, and in
the general case the new outcome is known only by executing it. A projected
branch therefore has explicit per-node status:

```text
unrequested | queued | running | materialized | conflict | cancelled |
budget_exhausted | unknown | external_barrier
```

Projection is lazy, bounded, cancellable, and off the Minecraft render thread.
Cache keys include parent state hash, intent hash, evaluator/configuration
revision, evaluation policy, capabilities, and relevant dependency witnesses.
Changing an upstream result invalidates only known dependents; absent a sound
dependency witness, conservatively invalidate the remaining suffix.

Every action/transition declares the strongest honest effect class:

- `PURE` — deterministic over declared input state;
- `SNAPSHOT_RESTORABLE` — exact state can be cloned/restored by the environment;
- `REEXECUTABLE_WITH_CAPABILITY` — may be run again only with explicit bounded
  authority and may produce a different external observation;
- `EXTERNAL_IRREVERSIBLE` — inspection can cross the historical record, but a
  speculative branch cannot undo/replay the effect; or
- `UNKNOWN` — stop projection until a user/provider supplies a policy.

TE-S1 contains only pure/snapshot-restorable document actions. A later
title-screen→palette→explorer→document journey may replay UI state, but opening
a network connection, launching a process, mutating a live world, or writing an
ambient file remains a visible barrier unless its environment supplies a
restorable adapter.

### Trajectory machine, supervision, and bounded shortest paths

Committed history and proposed futures are different graphs sharing state
identities. The History Graph may project them together, but its model and
legend must preserve the distinction:

```text
TrajectoryMachineState {
  actual_history_head,
  selected_trajectory_revision,
  instruction_pointer,          // next trajectory step, not a source line
  projection_frontier,
  supervision_contract_revision,
  status,
  remaining_budget
}

TrajectoryPlanRevision {
  parent_plan_revision?,
  start_state,
  goal_contract,
  action_generator_revision,
  cost_policy_revision,
  heuristic_revision,
  selected_route,
  explored_candidates,
  result: FOUND | EXHAUSTED | BUDGET_EXHAUSTED | BLOCKED | CANCELLED
}

TrajectoryStep {
  expected_parent_state_hash,
  action_intent,
  evaluation_policy,
  predicted_witness_and_outcome,
  effect_class,
  step_cost,
  accumulated_cost,
  estimated_remaining_cost
}
```

The **actual history head** is the state that has happened. The **instruction
pointer** identifies the next step on one immutable selected trajectory. The
**projection frontier** is how far search/evaluation has materialized. These
may coincide, but no command updates all three implicitly.

The initial planner is deterministic A* over a finite registered successor
generator. Costs are finite, non-negative, versioned, and explainable. Stable
tie-breaking uses canonical action id, arguments, and resulting state hash. An
admissible heuristic permits a shortest-path claim within that exact generated
graph; `h = 0` supplies the Dijkstra correctness baseline. Tests compare A*
against exhaustive/Dijkstra results on bounded graphs. If an action library is
incomplete, a heuristic is not admissible, an effect is not projectable, or a
budget ends, the result says so. “Shortest known route under generator G and
policy C” is valid; “globally shortest way to edit this document” is not.

A supervision contract is also versioned:

```text
SupervisionContract {
  start_domain_and_revision,
  goal_predicates,
  hard_invariants,
  forbidden_effect_classes,
  required_evidence,
  search_and_execution_budgets,
  approval_requirement,
  status: DRAFT | PLANNED | RUNNING | SUPERVISION_READY |
          APPROVED | REJECTED | BLOCKED
}
```

For a document, predicates may include expected text/hash, semantic structure,
selection state, diagnostics, checkout non-mutation, and required test
artifacts. Reaching every machine-checkable predicate yields
`SUPERVISION_READY`; only the declared human/authority may produce `APPROVED`.

Planning is side-effect free. Candidate states live in the bounded overlay and
candidate edges do not enter committed history. `step` validates the expected
parent and executes one authorized action. `run` is just repeated bounded
`step` and is permitted automatically only for `PURE` or
`SNAPSHOT_RESTORABLE` transitions inside the contract. A mismatch pauses before
execution and records diagnostics. Frozen-witness execution, intent
re-evaluation, and replanning are explicit choices. Replanning creates an
immutable sibling trajectory revision, retaining the old route and its search
evidence.

The History Graph's initial pathfinding-debug projection uses a stable legend:

- solid emphasized edge — committed executed prefix;
- dashed emphasized edge — selected projected suffix;
- thin neutral edge — retained committed or projected alternative;
- amber node/edge — open set or currently evaluating frontier;
- muted node — closed/explored candidate;
- red barrier — conflict, stale precondition, forbidden effect, or unknown;
- instruction-pointer marker — next selected step; and
- target marker — the supervision contract's goal state/predicate.

The panel exposes Plan, Step, bounded Run, Pause, Replan, Select Route, and
Inspect Cost through registered actions and ordinary focus/navigation. It may
show `g`, `h`, and `f` values in a details/debug mode without requiring those
numbers in the default approachable view.

### Candidate-history scrubbing and review comments

A selected trajectory is a seekable candidate history even before any step is
committed. Its timeline domain is route-step position, with optional projected
transition time when an action supplies animation. A seek address is pinned:

```text
CandidateFrameAddress {
  trajectory_plan_revision,
  route_id,
  step_position,
  expected_or_predicted_state_hash,
  evaluator_revision,
  projection_status
}
```

Seeking never executes an action, advances the instruction pointer, or moves
the actual history head. A materialized predicted frame may be rendered in the
ordinary editor/inspector. A frame that is queued, invalidated, blocked, or
unknown renders its last trustworthy predecessor plus an explicit status; the
timeline never fabricates document bytes merely to keep the scrubber smooth.

Comments may target:

- a whole trajectory or route;
- one candidate action/evaluation/transition;
- one candidate state/frame;
- source/glyph regions within a materialized candidate document; or
- a relation between committed and candidate targets.

The comment plan remains authoritative for comment identity, body, tags,
author, approval, selection rules, and migration. This plan supplies the pinned
candidate-frame/action identities and projected source witnesses. A comment on
candidate content is labelled **candidate** and does not constitute approval of
committed source. Replan preserves the old plan and its comments. If executing
a candidate produces the exact predicted state hash and witnesses, the UI may
offer an explicit link/promote operation that creates a committed target while
retaining candidate provenance. Any changed, ambiguous, or missing
correspondence stays separate or enters the ordinary witnessed migration flow;
approval is never transferred implicitly.

### Replay

A replayable component may be stateful. It receives events incrementally and
may retain pressed keys, partial key sequences, calculator memory, cursor
position, or other ordinary state. Replay means that a fresh instance with the
same configuration, initial state, timing policy, and ordered events produces
the same action and transition trace. It does not require every method to accept
the complete history or make internal state illegal.

Two replay modes are intentionally different:

1. **Exact replay** uses recorded ordered witnesses and must reproduce the same
   transitions or report a precondition mismatch. It proves historical
   fidelity.
2. **Semantic rebase** starts from a chosen branch prefix, re-evaluates declared
   selection expressions for each rebased action, and records new witnesses and
   transitions in a child branch. This is how a newly inserted hyphen can be
   consumed by a later decimal-numbering action. It is deterministic for a
   pinned evaluator/order/configuration, but it is not called exact replay.

### Recorded recipe

A recipe is a typed, reusable projection of selected semantic actions from an
episode. It declares parameter holes (for example target document, selection
expression, start value, or order), capability requirements, preconditions,
and failure policy. Promoting a trace to a recipe does not erase the concrete
source episode or its witnesses. V1 promotion is explicit and deterministic;
automatic generalization or LLM-assisted synthesis may propose a recipe later
but cannot silently authorize or apply one.

Recipes form an action-oriented IR separate from the Super Factory Manager
factory-program DSL. The first version needs no general loops, variables, or
arithmetic language merely to replay a bounded action sequence.

### Observation and authority

Screenshots, raster masks, compiler output, logs, profiler spans, agent text,
audio markers, and structured panel state are observations. They can be pinned
to revision/event/action/session identities and inspected together, but they do
not become authoritative state transitions merely because they appear in the
same timeline. A transition names the authorized action that caused it.

### Amalgamation and deamalgamation

Keep three operations distinct:

1. lossless pack/unpack of snapshots and episodes;
2. structural normalization, such as formatting or alpha-renaming; and
3. semantic amalgamation into an equivalence-oriented intermediate form.

Only lossless packing is inherently reversible. If normalization unifies
different identifiers or semantic lowering unifies a loop with repeated
statements, exact source spelling has been discarded. Exact deamalgamation then
requires a witness containing names, source ranges, formatting, and
transformation lineage.

Required laws for the dumb representation are:

```text
unpack(pack(snapshot)) == snapshot
export(import(big_json)) == canonicalize(big_json)
rehydrate(episode, t) == episode.full_frame(t)
```

Optimized representations must either satisfy equivalent round trips or declare
which distinctions they intentionally quotient and retain witnesses sufficient
for every advertised reconstruction.

### Equivalence levels

Never expose a single ambiguous `equivalent` boolean. Comparison reports name
the proven level:

- byte equality;
- syntax equality;
- formatting-insensitive equality;
- alpha-equivalence under declared symbol correspondence;
- symbolic identity under a bounded rewrite set;
- observational equivalence under an explicit observation policy;
- task/terminal-state equivalence; or
- unknown.

A ten-iteration loop and ten `println` statements may have the same bounded
output while differing under timing, interruption, instrumentation, stack
traces, and side effects. The observation policy is part of the claim.
Conservative `unknown` is correct when proof is unavailable.

The saved calculator article at
`D:\OneDrive\Documents\articles\calculator-app - Chad Nauseam Home.html`
is a design reference: Android's calculator combines exact rational,
recognizable symbolic, and constructive-real representations instead of
requiring one representation to solve every case. Apply the analogous policy
here—retain exact/simple forms where possible, use bounded symbolic reasoning
where justified, and fall back to approximation or `unknown` without a false
claim. The conference-talk reference `NxiKlnUtyio` and the maintainer's
starred sea-of-nodes/compiler repositories remain research inputs to inventory
before Phase 7 chooses an IR.

## Proposed v1 interchange shape

```json
{
  "schema": "sfm.snapshot-episode/1",
  "environment": {
    "id": "sfm:repository",
    "version": 1,
    "configuration": {}
  },
  "frames": [
    {
      "index": 0,
      "files": [
        {
          "path": "src/A.java",
          "content": {
            "encoding": "utf8",
            "text": "class A {}\n"
          }
        }
      ],
      "state": {}
    }
  ],
  "events": [],
  "actions": [],
  "transitions": [],
  "terminal": null
}
```

The schema above is illustrative until Phase 0 closes naming and validation.
The first committed schema requires explicit size limits, duplicate-path
rejection, path normalization, unknown-field/version policy, and canonical
serialization tests.

## Phase 0 — Close the contracts

### Historical review-snapshot subset — 2026-07-22 (superseded transport)

The first real-repository review wave froze a snapshot/comparison subset in the
`repository-review-bundle-v1` contract and shared JSON fixture. It fixed
normalized repository paths, strict UTF-8/base64 file bytes, deterministic
semantic hashing, explicit resource bounds, byte-range comparison selections,
and a managed-inbox handoff.

The byte/path/hash lessons remain useful evidence, but the bundle and managed
inbox are not the future snapshot transport. Release-plan P-1.2 removes the
Rust producer, Java importer, schema document, and fixture. Phase 0.1 must
define and test the general snapshot vocabulary directly; it no longer depends
on passing the deleted bundle fixture.

#### Review-snapshot implementation result — 2026-07-22

The frozen review subset was implemented across Rust and Java. Rust accepted
Git revision pairs or bounded directories, emitted full immutable snapshots
plus a deterministic textual comparison, validated the shared fixture and
adversarial path/content cases, and atomically published to the managed inbox.
Java loaded the same contract and projected generated comments into a
deterministic persistent review-session identity. The real SFM proof pair `d07bef66c` to
`8e9946d9f` produced 1,983-file snapshots and bundle id
`sha256:704c12c4894ecb227c0b604934fc66ab8ba62182467b69d8517f36ac5fd0e36d`.

This was historical evidence only. Phase 0 remains open for the
episode/event/action/transition vocabulary, and Phase 1 remains open for
general snapshot/episode adapters and full-frame round trips. Future storage
may reuse the validated normalization and bounded-decoding laws without
retaining the bundle schema, command, or inbox.

### [ ] 0.1 Define snapshot and episode vocabulary

- Decide canonical schema ids, versions, and hashes.
- Specify `RepoPath` normalization and reject absolute paths, parent
  traversal, duplicate normalized paths, and platform-dependent separators.
- Specify UTF-8/base64 content variants and bounded decoding.
- Define frame indexing, the final-frame alias, empty episodes, and failure
  behavior for missing or corrupt frames.
- Define canonical serialization independently of pretty JSON presentation.

**Completion criteria:** Rust and Java fixtures agree byte-for-byte on paths,
contents, frame indices, and hashes.

### [ ] 0.2 Define event, action, transition, and observation records

- Define typed raw events with exact sequence ordering and optional clock data.
- Define action invocation provenance, availability, authorization, arguments,
  results, and source-event ranges.
- Define deterministic transition ids and environment observations.
- Record binding/configuration revisions so replay does not accidentally use a
  newer keymap.
- Separate hard method/capability constraints from reward or efficiency scores.

**Completion criteria:** Two traces with the same action count but different
input histories remain observably different.

### [ ] 0.3 Define immutable branch, clock, and temporal-address contracts

- Define branch/revision/event/action identities and single-parent fork rules.
- Specify `recorded_at`, deterministic tick, logical sequence, and animation
  time without using any one of them as an alias for another.
- Define explicit wall-time lookup and branch/logical-position lookup; reject
  ambiguous bare timestamp state selectors.
- Define absolute and relative revision-qualified region anchors, informed by
  R1C1 but independent from spreadsheet rows/cells.
- Specify historical insertion as creating a child branch with an unchanged
  parent and retained provenance for the insertion point.
- Defer merge commits and concurrent-edit conflict resolution explicitly.

**Completion criteria:** Fixtures distinguish two branches with the same
logical ordinal, two revisions recorded at the same wall-clock instant, and a
historical insertion without changing any parent hash.

### [ ] 0.4 Define selection witnesses, replay modes, and recipe contracts

- Freeze the action layer sequence: raw input, binding decision, semantic
  action, authorized transition, and observations.
- Define a versioned selection expression plus exact ordered resolved witness.
- Define `source_order` and `canvas_reading_order_yx` with stable tie-breaks.
- Define exact replay versus semantic rebase and require the caller/report to
  name which was performed.
- Define concrete-trace-to-recipe promotion, parameter holes, capability
  requirements, and deterministic failure/conflict reports.
- Confirm canonical action ids and typed arguments for select-all-occurrences
  and decimal-sequence replacement before shipping a binding.

**Completion criteria:** One fixture exact-replays a recorded witness while a
second fixture rebases the same semantic query over a changed prefix and
produces a different, explicitly witnessed target set.

### [~] 0.5 Freeze action-graph, undo-domain, and effect-class contracts

**Work:**

- Define separate versioned records for ActionIntent, ActionEvaluation,
  ActionOutcome, StateRevision, HistoryHead, head movement, and branch edge.
- Define checkout-recorded-state, reuse-recorded-transition,
  frozen-witness re-execution, and intent re-evaluation as distinct operations.
- Define explicit undo-domain selection (`focused`, document, selection,
  workspace, episode) and ambiguous redo-child behavior.
- Define effect classes, projection statuses, dependency witnesses,
  cancellation/budget results, and external barriers.
- Define pin/retention rules so comments, recipes, named branches, exports, and
  open inspectors prevent accidental history reclamation.

**Validation:** Shared fixtures contain one intent with two evaluations, an
undo-undo-new-action fork with two retained redo children, an ambiguous redo
choice, a pure projected suffix, and an irreversible barrier.

**Completion criteria:** Rust and Java agree on graph identity/order and no API
can represent “redo after a fork” only as one destructively replaced stack.

### [~] 0.6 Freeze trajectory-machine and supervision contracts

**Work:**

- Define versioned `TrajectoryMachineState`, `TrajectoryPlanRevision`,
  `TrajectoryStep`, `SupervisionContract`, action-generator identity, cost
  policy, heuristic, search budget/result, and plan-head records.
- Keep actual history head, instruction pointer, projection frontier, and
  selected trajectory as independent identities with explicit transitions.
- Define canonical planner ordering, non-negative cost validation, admissible
  heuristic declaration, Dijkstra fallback, and honest optimality wording.
- Define immutable replanning, stale-parent behavior, effect authorization,
  `SUPERVISION_READY` versus human `APPROVED`, and candidate-versus-committed
  graph boundaries.

**Validation:** One language-neutral fixture contains a committed fork, two
trajectory revisions, one selected route/instruction pointer, open and closed
search candidates, a stale precondition, a supervision-ready goal, and an
unapproved human gate.

**Completion criteria:** The interchange cannot mistake a candidate step for a
committed transition, advance the instruction pointer by moving the history
head, destroy an old plan during replan, or claim global shortest-path status
without a declared finite graph and valid proof policy.

**TE-S1M Java-local contract checkpoint (2026-08-21):** Added validated
`sfm.history-graph/1` and `sfm.trajectory-machine/1` records for intents,
evaluations, outcomes, committed/projected states and edges, undo domains,
heads/movements, retention pins, effect/projection classes, finite generator,
cost/heuristic identity, supervision predicates/evidence/approval, immutable
plan revisions/routes/search candidates, instruction pointer, projection
frontier, and remaining budget. Constructors reject projected irreversible
effects without barriers, committed non-materialized states, negative costs,
discontinuous routes, invalid selected routes, unbounded/inadmissible
minimum-cost claims, and `APPROVED` without authority evidence.

The language-neutral `trajectory-contract-v1.json` fixture contains one
committed fork with ambiguous redo, two retained plan revisions, independent
actual head/instruction pointer/frontier, open/closed candidates, one pure
projected suffix, one external barrier, a stale-parent machine state, and a
`SUPERVISION_READY` human gate with no fabricated approval. Java-local tests
pass. These items remain `[~]` because the later full TE-S1 interchange still
owes Rust/shared-schema conformance; TE-S1M intentionally freezes and consumes
the Java seam first rather than falsely claiming cross-runtime completion.

## Phase 1 — Implement the dumb adapters

### [ ] 1.1 Rust snapshot and episode models

- Add Facet-serializable models around deterministic maps and byte strings.
- Read/write one complete JSON file without requiring Git, Gradle, Minecraft,
  an AST parser, or Vox.
- Provide canonicalize, validate, hash, inspect, and extract-frame operations.
- Keep optimized blob/delta storage out of the first implementation.
- Any later Git-tree/directory adapter populates this general snapshot model;
  it must not restore the deleted repository-review bundle producer.

### [ ] 1.2 Java snapshot and episode models

- Implement the same versioned interchange contract on the Minecraft side.
- Keep host filesystem access behind an explicit source/provider boundary.
- Bound file count, content bytes, event count, and decoded allocation.
- Add shared fixture conformance tests between Rust-produced and Java-produced
  documents.
- Review consumers resolve explicit revision selectors into lanes/documents and
  feed the global comment/session model directly; they do not import named
  bundles from a managed inbox.

### [ ] 1.3 Full-frame round-trip proof

- Generate text, binary, empty, Unicode, malformed, duplicate, and hostile-path
  fixtures.
- Prove every valid timestep rehydrates a complete independent snapshot.
- Prove export from any future internal representation returns canonical v1.

### [ ] 1.4 Immutable branch store and replay engine

- Store parent revision, fork insertion point, immutable prefix, appended
  actions, evaluator/order revisions, and resulting complete frames.
- Support exact replay and semantic rebase as different typed operations.
- Use content/state hashes and preconditions to reject stale or incompatible
  action application instead of silently drifting.
- Keep V1 deliberately simple: duplicate full frames and suffix actions before
  introducing structural sharing or merge logic.
- Emit a machine-readable branch tree and transition/witness report suitable
  for the Episode Inspector and puppet artifacts.

**Completion criteria:** The hyphen-numbering scenario has immutable original
and forked branches, both export/import losslessly, and replaying either branch
in random seek order yields its recorded frames.

### [ ] 1.5 Implement persistent undo trees and lazy branch projection

**Work:**

- Store parent/child adjacency independently from named/current heads; adding a
  child never removes siblings.
- Implement focused-domain undo, single-child redo, explicit child selection,
  arbitrary retained checkout, branch naming/pinning, and inspectable head
  movement events.
- Evaluate projected suffixes in a bounded cancellable worker with generation
  checks, conservative invalidation, cache keys from 0.5, and no render-thread
  waits.
- Refuse unsupported external effects with a typed barrier while keeping the
  historical branch inspectable.
- Add explicit previewable retention/GC later; V1 retains all bounded chamber
  branches by default.

**Validation:** Pure model tests perform undo→undo→new action, prove both old
and new descendants remain selectable after export/import, cancel a stale
projection, and stop at an irreversible effect without partial publication.

**Completion criteria:** A branch cannot be lost merely by undoing and doing
something else, and every materialized/projected child has a visible status and
reproducible evaluation policy.

### [x] 1.6 Implement the deterministic bounded trajectory planner

**Work:**

- Implement a host-independent planner over immutable state ids, registered
  successor generation, goal predicates, non-negative costs, effect filters,
  cancellation, expansion/time budgets, and stable tie-breaking.
- Provide A* and `h = 0` Dijkstra modes over the same interface. Preserve
  explored candidates and reasons for pruning/barriers as bounded evidence.
- Validate every selected step against its expected parent before execution.
  A mismatch pauses; it never applies a stale predicted witness.
- Replan by appending a sibling `TrajectoryPlanRevision`; retain and inspect
  previous selected routes.

**Validation:** Property/bounded exhaustive tests compare A* cost with Dijkstra
and exhaustive enumeration across generated finite graphs, including ties,
zero-cost edges, no-route, budget exhaustion, cancellation, stale parents,
forbidden effects, and two replans from the same start.

**Completion criteria:** For every bounded fixture, the planner either returns
a reproducible minimum-cost route under its declared graph/policy or an exact
non-success result; it never partially commits search candidates.

**Completion evidence (2026-08-21):**
`SFMBoundedTrajectoryPlanner` is a host-independent pure projection kernel with
lazy canonical successor iterators, non-negative costs, independently checked
goal/invariant predicates, typed effect authorization and terminal reasons,
cooperative cancellation, all three search budgets, deterministic queue/path
ordering, immutable plan-book append, and exact plan/route/instruction-pointer
stale-parent validation. Inadmissible or non-zero-at-goal heuristics,
unbounded/non-canonical/failing generators, colliding immutable state ids, cost
overflow, forbidden/unknown/irreversible effects, and stale parents all fail
closed without committing a candidate transition. The focused suite compares
A* and Dijkstra against exhaustive enumeration for all 64 subsets of a bounded
DAG and against an independent Bellman–Ford oracle for 32 cyclic graphs with
zero-cost edges and admissible inconsistent heuristics. It also covers exact,
one-short, and zero budgets; deadline expiry inside generation and immediately
before goal acceptance; mid-iterator cancellation; deterministic ties;
hard-invariant and capability barriers; two retained replans; and cross-plan
stale-validation rejection. The Java-only change does not alter or require
reinstallation of `sfm-propagate-changes.exe`.

## Phase 2 — Build the reusable timeline panel and Episode Inspector

### [ ] 2.0 Define the timeline position and seekable-panel contract

The timeline is a composable host that accepts one inner panel and instructs it
which timestep to present. It does not know how to render repository files,
inventory slots, calculators, or editor state.

The initial time domain is a bounded inclusive integer range. A conceptual
contract is:

```text
TimelineModel {
    first_timestep,
    last_timestep,
    current_timestep,
    playing,
    ticks_per_step
}

SeekableTimelinePanel {
    timelineBounds()
    setTimelinePosition(timestep)
}
```

Resolve aliases such as `t=-1` to an ordinary bounded position before calling
the child. Seeking is random-access and idempotent: the result at `t=4` cannot
depend on first visiting `t=0..3`. Invalid bounds and positions produce visible
diagnostics rather than partially mutating the child.

The first movement proof uses several integer timesteps for animation. A future
fractional/substep position may interpolate between keyframes, but is not
required to establish the v1 API.

### [ ] 2.1 Implement the MPV-like timeline host

Place the inner panel in the content area and a compact horizontal transport at
the bottom. The first controls are:

- play/pause;
- click or drag on the horizontal track to seek;
- fixed thumb/current-position indicator;
- current and final timestep text;
- previous/next single-step;
- Home/End first/final navigation; and
- keyboard focus, narration, and deterministic tick-driven playback.

The transport owns input in its own bounds and does not leak slider clicks into
the inner panel. Seeking instructs the child immediately. Playback advances by
the declared deterministic tick policy rather than wall-clock sampling.
Resizing changes geometry but not the selected timestep. The timeline wrapper
can itself be hosted full-screen or as a normal multiplexer panel.

### [ ] 2.2 Add the falsified chest/inventory replay panel

Create a read-only, non-menu-backed inventory replay panel that visually
resembles a chest plus player inventory. It renders copied `ItemStack` values,
slot backgrounds, a virtual cursor, and any cursor-held stack, but it does not
open a live container, send clicks, mutate a player inventory, or require a
server.

Use a deterministic fixture:

1. `t=0`: one cobblestone is in a chest slot; player inventory and cursor are
   empty;
2. pickup: the chest slot becomes empty and the cobblestone becomes the
   cursor-held stack;
3. transit timesteps: the virtual cursor and held stack move through recorded
   panel-local positions toward a selected player-inventory slot;
4. pre-place: the held stack is over the destination slot; and
5. final: the player slot contains one cobblestone and the cursor is empty.

The replay state explicitly stores chest slots, player slots, cursor stack, and
cursor position at each advertised timestep or derives them from deterministic
keyframes. It must support seeking final-to-first-to-middle in arbitrary order
with identical results. This is groundwork for visually replaying item
movements, not a claim that live Minecraft menu synchronization has been
captured.

### [ ] 2.3 Build responsive Episode Inspector content

Create a normal full-screen host plus composable panels showing:

- the reusable timeline host and its selected inner visualization;
- logical file tree;
- selected file contents or binary metadata;
- raw events;
- resulting action invocations;
- transition/result/error details; and
- environment-specific state/observations.

Scrubbing never mutates the source episode. Selecting an action can jump to its
source events; selecting an event can reveal resulting actions. Selecting an
inventory transition may make the falsified inventory replay the timeline's
inner panel.

### [ ] 2.4 Add command-palette and multiplexer integration

- Add a typed action such as `sfm:developer/open_episode`.
- Accept only bounded, explicitly selected episode sources.
- Open full-screen normally and as a panel when the current host is the SFM
  multiplexer.
- Reuse file-presentation styles and console widgets where appropriate without
  coupling the episode model to those screens.

### [ ] 2.5 Add timeline and Episode Inspector puppets

The inventory timeline puppet captures:

1. chest-owned cobblestone at the first timestep;
2. cursor-held cobblestone after pickup;
3. at least one visible transit position;
4. the pre-place destination hover;
5. player-inventory ownership at the final timestep;
6. dragging the horizontal timeline backwards; and
7. random seek order proving the view does not depend on playback history.

The broader Episode Inspector puppet also captures a loaded repository episode
at `t=0`, an intermediate changed file/state, final `t=-1`, an action with
its source events, an exported/reopened snapshot, and malformed/bounded-error
presentation.

### [ ] 2.6 Refine inventory geometry, keyframes, and animation time

Use the first seven-frame puppet as the baseline, then bring the falsified
inventory presentation into alignment with Minecraft's real container layout:

- derive chest, main player-inventory, and hotbar slot origins from the
  corresponding 1.19.2 `ChestScreen`/`AbstractContainerScreen` menu geometry;
- preserve the visible vertical gap between the player's 3x9 inventory and
  1x9 hotbar instead of rendering four uniformly spaced rows;
- center the orange timeline heading from `font.width(text)` and the panel's
  actual content bounds, including narrow responsive layouts;
- extract or reuse the Draw/text-editor crosshair renderer for the virtual
  cursor so cursor shape, hotspot, scale, and theme roles are consistent; and
- keep the cursor/held stack anchored to exact source and destination slot
  centers at ownership keyframes.

Replace the integer-frame-only mental model with two related coordinates:

```text
keyframe position:  0, 1, 2, ... N
animation time:     0.0 .. total_duration
transition i:       keyframe i -> i+1 with its own duration and easing
```

An animation time inside a transition resolves to a fractional keyframe
position. For example, keyframe position `1.1` is ten percent through the
transition from keyframe 1 to keyframe 2, independent of whether that
transition lasts one second or ten. Discrete ownership changes occur at named
keyframes; presentation properties such as cursor position interpolate between
them. The first implementation uses linear interpolation and deterministic
client ticks, while leaving easing as typed transition metadata.

Distinguish transport operations:

- previous/next **keyframe** jumps to a semantic ownership boundary;
- optional previous/next **frame/substep** advances the sampled animation;
- play advances animation time according to per-transition durations; and
- seeking by either coordinate deterministically derives the other.

Expose keyframe position and wall-clock/deterministic animation time as two
stackable horizontal tracks when both are useful. One track is linear in
keyframe space (`0..N`); the other is linear in total duration (for example,
`0..40s`). Scrubbing either updates the same immutable replay state and never
depends on prior playback history.

Add focused interpolation, zero-duration, unequal-duration, boundary,
reverse-seek, and resize tests. The superseding puppet must show the hotbar gap,
properly centered heading, shared crosshair, exact ownership keyframes, at least
one visibly interpolated position, a keyframe jump, and a time-based seek across
two transitions with different durations.

### [ ] 2.7 Make the Episode Inspector branch- and intent-aware

- Add a branch tree/selector beside the timeline; changing branch preserves a
  shared logical anchor when possible and visibly reports when it cannot.
- Show wall-clock provenance separately from logical sequence and deterministic
  environment time.
- For every semantic action, show its typed invocation, selection expression,
  ordered witness, evaluator/order revision, pre/post hashes, and whether it was
  exact-replayed or semantically rebased.
- Permit scrubbing the document-creation sequence like sync.in and comparing
  the original and historically edited branches without flattening them into
  one mutable timeline.
- Add jump links from raw events to binding decisions/actions/transitions and
  from actions back to their input provenance.

**Completion criteria:** A user can scrub both numbering branches, identify the
historically inserted edit, and see why the rebased numbering action selected
three regions while the original selected two.

### [ ] 2.8 Build the live History Graph panel

**Work:**

- Register reusable scene `sfm:episode/history`, opened initially through
  `sfm:panel/open sfm:episode/history` beside the editor or explorer.
- Subscribe incrementally to the active/selected episode without polling full
  snapshots or requiring the panel to have existed when recording began.
- Render branch lanes with alternating state, intent/evaluation, and outcome
  nodes; visibly distinguish current head, named/pinned branches, projected
  dashed nodes, running/cancelled/conflict/unknown/barrier states, and exact
  versus recomputed evaluations.
- Selecting a node shows action args/query, frozen witness, outcome, pre/post
  hashes, effect class, cost/status, raw-event provenance, and available typed
  actions: checkout, undo, choose child/redo, fork, exact replay, recompute, pin,
  name, and cancel projection.
- Follow-head is independently toggleable. Scrubbing/inspection does not move
  the mutation head until an explicit checkout action is invoked.
- Use the ordinary command-palette constrained-choice surface whenever redo or
  rebase has several valid children/policies; do not create a second picker.

**Validation:** Pure layout/model tests cover deep/wide trees, virtualization,
branch labels, live append, lost focus, ambiguous child choice, projected
status changes, and narrow panels. The TE-S1 puppet keeps this panel visible
while actions arrive and captures the branch point before and after Ctrl+Z.

**Completion criteria:** A frontline user can watch actions arrive, inspect why
an outcome occurred, move through retained branches, and create a fork without
reading JSON or closing the editing workspace.

### [ ] 2.9 Prove a whole-workspace counterfactual journey

Record a controlled title-screen/workspace journey that opens the palette,
opens an explorer, selects `A.java`, invokes `open selected document`, and edits
the document. Fork before the selection and choose `B.java`. Compare:

1. checkout of the recorded `A.java` result;
2. frozen-witness replay that still targets `A.java`; and
3. intent re-evaluation that opens `B.java` and projects only compatible suffix
   actions.

The fixture records focus, explorer selection, panel/layout state, action
query/witness, and every conflict/barrier. It uses only restorable UI/document
state; any live-world, process, network, or ambient-file effect appears as a
typed barrier rather than being faked.

**Completion criteria:** The History Graph exposes all three narratives, the
original A branch remains accessible, the B branch explains which suffix was
recomputed or rejected, and a machine-readable artifact agrees with the visible
branch lanes.

### [ ] 2.10 Add instruction-pointer and route/search layers to History Graph

- Render actual head, selected trajectory, instruction pointer, executed
  prefix, projected suffix, target, frontier, open/closed candidates, retained
  alternatives, and barriers using the trajectory-machine legend.
- Keep the default view approachable; cost details expose `g`, `h`, `f`, action
  generator/policy revisions, and prune/barrier reasons on focus/inspection.
- Virtualize bounded search evidence and coalesce live updates without dropping
  the final state or blocking the render thread.
- Register selector-explicit Plan, Step, bounded Run, Pause, Replan, Select
  Route, and Inspect Cost actions. Mouse/keyboard controls invoke those actions
  rather than mutating panel-private state.

**Validation:** Pure presentation tests cover route forks/crossings, instruction
pointer at start/middle/end, replans, stale/error/barrier states, narrow panels,
keyboard narration, and large bounded explored sets.

**Completion criteria:** A user can tell what happened, what is merely proposed,
what will run next, why one route was selected, and where planning stopped.

### [ ] 2.11 Prove shortest supervised document transformation in TE-S1

- Give the temporal-numbering chamber a finite versioned action catalog with
  semantic select-all-occurrences and decimal-sequence replacement plus more
  expensive literal edit actions.
- Define a supervision contract for exact expected text, retained old branch,
  checkout non-mutation, allowed effect classes, required trace/screenshots,
  and human approval remaining pending.
- Plan from the two-item source to the numbered target, step/run the selected
  route through ordinary registered actions, then fork the document, add the
  third item, and replan without deleting the first route.
- Prove with Dijkstra/exhaustive oracle evidence that the chosen semantic route
  is minimum-cost in the declared catalog. Do not generalize the claim to all
  possible editing behavior.

**Completion criteria:** Natural in-game use shows the route line and moving
instruction pointer, reaches `SUPERVISION_READY`, preserves the old route and
history branch after replan, and emits machine-readable evidence agreeing with
the visible graph and final document.

### [x] 2.12 Make candidate trajectories independently seekable

- Extend the reusable timeline source with a read-only candidate-route domain
  addressed by `CandidateFrameAddress`; do not overload committed branch ids.
- Materialize bounded predicted document frames off the render thread and make
  queued/running/invalidated/barrier/unknown positions explicit.
- Scrubbing, stepping the timeline, or opening a candidate frame must not move
  actual history head, trajectory instruction pointer, or plan selection.
- Preserve a candidate frame after replan through its immutable old plan
  revision while allowing the new plan to project independently.

**Validation:** Random-seek every materialized frame in forward/reverse order,
seek unavailable and invalidated frames, replan while an old frame is visible,
and prove no action execution/head movement occurred. Visible screenshots and
JSON identify candidate versus committed state consistently.

**Completion criteria:** A user can scrub the proposed future, inspect exactly
what each step would show, and return to the actual state without changing it.

### [x] 2.13 Integrate candidate histories with the comment system

- Implement the candidate-target adapter owned jointly with comment Phase 6b:
  plan revision, route, step/action/state, predicted hash/status, projected
  document identity, pinned selector/witness, and provenance.
- Let ordinary comment creation/edit/navigation work while a candidate frame is
  displayed. Clearly label candidate comments in timeline, editor, explorer,
  and comment views.
- Preserve comments on old trajectory revisions after replan or invalidation.
  Offer explicit exact-hash promotion/link after execution; otherwise use a
  witnessed migration decision and keep human approval suspended/separate.
- Permit route/action-level comments even when no document frame exists, so a
  reviewer can explain a rejected cost, heuristic, barrier, or action choice.

**Validation:** Comment on a candidate glyph region and one route step, replan,
prove both remain on the old plan, execute an exact route and explicitly promote
one target, then introduce a divergent result and prove no implicit migration
or approval transfer. Round-trip the session and reopen every target.

**Completion criteria:** Candidate futures are reviewable without becoming
committed facts, and execution/replanning never silently retargets comments.

## Phase 3 — Prove generic panel observation with a normal calculator

The calculator is a proving application, not the owner of recording. Generic
input observation, replay, visual/structured capture, agent routing, and
interaction ownership wrap the multiplexer/panel host as Track 7 of the
[in-game workspace plan](in-game%20code%20review%20workspace%20and%20window%20manager%20plan.md).

### [ ] 3.1 Define the generic panel-recording projection

- Project multiplexer panel lifecycle, focus, normalized panel-local pointer,
  keyboard/character/controller events, origin, routing/consumption results,
  ownership changes, optional frame images, and optional structured panel
  observations into the episode interchange.
- Record workspace/panel and configuration revisions needed to reject an
  incompatible replay.
- Keep visual frame capture optional: exact input provenance must not depend on
  taking a screenshot after every event.
- Permit ordinary full-screen `Screen` hosts to use the same envelope through
  an adapter while treating composable panels as the primary workspace unit.
- Keep the recorder outside child panels; panels may only contribute optional
  structured observations through a narrow interface.

### [ ] 3.2 Build an ordinary composable calculator panel

Implement calculator display, memory/expression/input state, and
controller-like buttons as a normal responsive panel with a full-screen
compatibility host. It accepts ordinary pointer, keyboard, and controller input
through the shared panel routing surface and contains no dedicated event log,
timeline scrubber, replay engine, or agent session.

An optional structured observation reports calculator state for tests and
agents. The visible panel remains an ordinary calculator even when no recorder
or agent exists.

Keep “display equals 4” distinct from “compute the supplied addition using
calculator controls.” Hard method sanctity belongs in environment capabilities
and terminal predicates. Reward may prefer fewer steps but cannot make a
forbidden direct-state mutation acceptable. Randomized/hidden operands can
distinguish general addition from memorizing an answer.

Retain fixtures where `2+2` reaches display `4` by pressing 4, 2+2,
1+1+1+1, 4+0, or 8/2, and where 99+44 uses inefficient repeated increment.
Which satisfy the task depends on the declared goal and capabilities, not logic
inside the calculator panel.

### [ ] 3.3 Generic recording, ownership, and replay puppet

Open the normal calculator beside another panel, record human input through the
generic multiplexer envelope, replay it into a fresh calculator, acquire the
calculator for an agent with a visible ownership indication, manipulate its
virtual cursor/keyboard, reject human mutation input only on the owned panel,
release ownership, and inspect the resulting generic episode. Rehydrating each
recorded frame/observation must reproduce the advertised calculator state.

## Phase 4 — General action spaces and keybinding traces

### [ ] 4.1 Parameterized input actions

Keep `charTyped(char)` or `type_char { scalar }` as a parameterized action
kind. A bounded RL environment may instantiate an allowed glyph alphabet as
discrete choices or use an action-kind head plus parameter head. Do not require
thousands of global action registrations merely to represent characters.

Model glyph-picker, cursor movement, calculator controls, and editor operations
as composable actions so characters unavailable on a physical keyboard remain
reachable through the environment.

### [ ] 4.2 Integrate the SFM dynamic keybinding engine

Record normalized press/release/focus/tick events, the exact binding snapshot,
partial-sequence behavior, and emitted action invocations. Replay through a
fresh engine must reproduce the same invocations, including absence of an
invocation after cancellation or focus loss.

### [ ] 4.3 Define binding JSON, rule IR, and optional program projection

Treat declarative JSON and a small program-like form as projections of one
typed rule IR. A rule such as “on each tick, if Ctrl+Alt+L became pressed,
invoke format_document” may be easier to analyze or compose as a program/state
machine than as ad hoc configuration fields. Do not make the surface syntax
the executor.

Define parse/print and From/Into-style round trips, explicit timing/edge
semantics, and diagnostics for rules that cannot be represented by the bounded
keybinding UI. The ordinary settings screen continues to edit the typed
key-sequence subset even if a later advanced editor exposes the program form.

### [ ] 4.4 Add revision-qualified 2D selection expressions and ordering

- Reuse the typed-selection and spatial-region registries rather than storing
  editor-private cursor indexes in the episode contract.
- Add a text occurrence expression that resolves all exact occurrences of the
  current/explicit seed selection, including Ctrl+Alt+J-style invocation.
- Resolve text in source order even when glyph baselines/spacing are irregular;
  require an explicit order for freeform canvas regions.
- Materialize every shorthand (`focused`, `current selection`) into a canonical
  self-contained action record before transition authorization.
- Retain exact region ids, source ranges, geometry/map revision, and ordering
  witness so tests do not have to rediscover targets pixel by pixel.

### [ ] 4.5 Implement decimal-sequence replacement

Implement provisional action
`sfm:text/selection/replace/decimal_sequence` with typed target document,
selection expression, start (default 1), step (default 1), formatting policy,
and ordering policy. It replaces each resolved region atomically in a branch
overlay. Simultaneous replacements use the pre-action witness, avoiding offset
drift after earlier replacements. Empty, overlapping, stale, unordered, and
non-text selections return typed diagnostics without partial mutation.

Focused-editor/current-selection conveniences may supply omitted UI arguments,
but the command-palette preview and stored invocation show the resolved target
and query. The default Ctrl+Alt+J binding invokes the separate select-all-
occurrences action; it is not hard-coded inside decimal numbering.

**Completion criteria:** A two-item hyphen list becomes `1`/`2`, Unicode and
irregular 2D glyph placement preserve source semantics, and exact witness tests
prove atomic replacement order.

### [ ] 4.6 Promote concrete traces into reusable recipes

- Let a user select a contiguous semantic-action subsequence and promote it to
  a recipe while retaining links to its source episode/revision.
- Parameterize target document and selection expression first; expose start,
  step, formatting, and order only when the source actions declare them.
- Provide inspect/export/import/apply and dry-run/preview operations with typed
  capability requirements and no ambient source-write authority.
- Keep automatic inference optional. A Codex/external agent may propose a
  recipe draft later, but the user approves a visible typed recipe.

### [ ] 4.7 Historical insertion and semantic suffix rebase

- Fork at an explicit event/action/frame boundary.
- Append or insert new semantic actions into the child prefix.
- Rebase a selected suffix only through actions that declare deterministic
  query re-evaluation; preserve conflicts instead of guessing.
- Retain original and new witnesses side-by-side and support abandoning the
  child branch without affecting its parent.

**Completion criteria:** Inserting a new hyphen list item before the recorded
numbering action and semantically rebasing the suffix yields `1`, `2`, `3` on a
child branch while exact replay of the parent remains `1`, `2`.

## Phase 5 — Refactoring playground environments

### [ ] 5.0 Add a branch-backed writable document overlay

- Opening tracked/dependency source remains read-only by default.
- An explicit “edit in episode branch” action copies the authorized document
  snapshot into an ignored, bounded overlay and opens the same editor contract
  in writable mode.
- Save, undo, semantic actions, and recipe application mutate only the overlay
  revision. Export/apply-to-checkout is a separate previewable capability with
  explicit target and stale-base checks.
- Reset/discard is recoverable through immutable parent revisions; no ambient
  filesystem traversal is implied by editing one authorized document.

**Completion criteria:** A user edits and saves a source-shaped document,
scrubs/replays it, and discards the experiment while the real checkout remains
byte-identical.

### [ ] 5.1 Before/after repository fixtures

Store tracked immutable `state_before` and `state_after` snapshots for small
Java tasks. Materialize mutable attempts only beneath an ignored, bounded SFM
playground root. The first fixture renames `B` to `C`, including Java file
rename, constructor/type references, and compile outcome.

### [ ] 5.2 Environment actions and observations

Expose typed source operations, text-editor actions, compile, test, inspect
diagnostics, reset, and compare-to-goal. Preserve the exact action trace and
every resulting snapshot. Distance to the final code is an observation/metric,
not proof that the method used was safe or semantically valid.

### [ ] 5.3 Controlled Java compilation

Run compilation in an isolated worker with bounded time/memory/output,
controlled source/class paths, annotation processing disabled unless
allowlisted, no compiler plugins, and no ambient build-tool execution. Treat
compiler diagnostics as observations. Do not assume arbitrary `javac` input
is harmless merely because Gradle is absent.

### [ ] 5.4 Build editing test chambers and facilitator automation

- Define a chamber with immutable starting snapshot, goal predicate or expected
  snapshot, allowed capabilities, instructions, optional hints, and scoring.
- Keep correctness, non-escape, and method constraints as hard predicates;
  report elapsed time, action count, pointer travel, and trace/recipe compression
  as separate learning metrics.
- Add a multiple-cursor chamber and the exact temporal decimal-numbering
  chamber from TE-S1.
- Let a puppet act as facilitator: open the chamber, present instructions,
  insert structured markers, run the interaction, and export the episode,
  screenshots, final text, and result summary.
- Add opt-in microphone/narration capture only after defining consent, visible
  recording state, destination, retention, and deletion. Defer continuous video
  capture/parsing; screenshots plus structured events are the first contract.

**Completion criteria:** A user can transform A into B naturally in-game, and
the automated facilitator can replay and score the same chamber without
touching the real source tree.

## Phase 6 — Capability-controlled virtual shell

### [ ] 6.1 Define a small parsed shell language

Do not begin by claiming full PowerShell compatibility. Define a typed AST for
commands, arguments, pipelines, redirections, sequencing, and environment
operations over an SFM-owned virtual filesystem and PATH.

Approval operates on the parsed and resolved command/capability graph, never a
regex over the source string. Default deny. A compiler permission identifies
the resolved executable/capability and argument policy, optionally including
content hash, rather than trusting a basename or path-shaped string.

### [ ] 6.2 Deterministic interpreter and trace

Represent commands as typed functions over virtual state. Record pipeline
stages, capability checks, outputs, errors, and transitions. The same shell AST
and initial VFS must replay deterministically where its capabilities promise
determinism.

## Phase 7 — Structural and semantic amalgamation

### [ ] 7.1 Structural normalization with witnesses

Prototype formatting-insensitive and alpha-renamed Java forms while retaining
source maps and witnesses. Prove which original forms can be exactly restored.
Never replace the full snapshot interchange with normalized source.

### [ ] 7.2 Conservative semantic comparison

Add bounded symbolic rewrites, control-flow/IR experiments, or sea-of-nodes
lowering only after the dumb adapters and exact snapshot comparisons work.
Reports name their assumptions and return `unknown` rather than false
equivalence.

### [ ] 7.3 Optimized episode storage

Explore content-addressed blobs, Merkle trees, snapshot deltas, shared AST
nodes, and periodic checkpoints. Measure space/time against full-frame JSON.
Every accepted representation exports the canonical big JSON and supports
random-access rehydration of advertised frames.

## Phase 8 — Vox and external-agent integration

Transport the same versioned snapshot, episode, intent, and action models only
after the local Java and Rust adapters conform. Vox is an optional transport,
not the owner of the data model. External agents receive explicit capabilities
and bounded environments; they do not gain ambient filesystem, shell, compiler,
Minecraft input, or action authority merely by connecting.

### [ ] 8.1 Define a provider-neutral external-agent capability

- Define typed start/resume/cancel/status/request contracts independent from a
  particular model vendor or chat UI.
- Send a bounded context package: pinned episode revision, selected documents
  and regions, diagnostics/observations, requested operation, and granted
  capabilities. Never infer ambient repository or game authority from process
  locality.
- Treat agent prose, comments, proposed recipes, patches, and action plans as
  provenance-bearing drafts. Mutation still goes through explicit branch-backed
  SFM actions and user/goal authority.
- Record provider id/version, request/response ids, context hashes, approvals,
  cancellation, and resulting accepted/rejected actions without requiring raw
  hidden reasoning.

### [ ] 8.2 Add an optional local Codex provider

Use a supported local Codex surface rather than embedding credentials. The
official [Codex SDK documentation](https://learn.chatgpt.com/docs/codex-sdk)
describes starting and resuming local Codex threads, while the
[Codex CLI documentation](https://learn.chatgpt.com/docs/codex-cli) documents
interactive and non-interactive local execution. The first adapter may use the
SDK/app-server or a bounded CLI child process according to the stable supported
surface available at implementation time.

- Inherit the user's local Codex authentication and configured sandbox/approval
  policy; do not scrape subscription tokens or require an API key in SFM.
- Keep Codex optional so Minecraft/SFM, Gradle-only contributors, puppets, and
  deterministic replay work without it.
- Version and hash SFM-programming/review skills supplied to the provider.
- Stream status and structured results into the observation timeline; do not
  parse terminal pixels as the protocol.
- Permit a user to ask for SFM-program help, review suggestions, explanation,
  or a recipe draft while preserving explicit review/acceptance boundaries.

### [ ] 8.3 Expose bounded agent operations through the live-game control plane

Add future typed `sfm agent ...`/`sfm episode ...` operations through the
existing multi-instance SFM CLI service instead of adding shell aliases or a
second discovery registry. Commands state the selected game, episode/branch,
documents/selections, provider, and capability set. Teamy Terminal is one
possible shell host, not the agent protocol or authority.

## Phase 9 — Cross-runtime observations and shared terminal devices

### [ ] 9.1 Define one correlated structured-observation envelope

- Reuse the SFM client log plan for Java Log4j/translatable capture and add a
  Rust `tracing` adapter at the bridge boundary.
- Preserve runtime/process/thread/task, logger/target, level, span/correlation
  ids, monotonic source sequence, optional wall time, structured fields,
  translatable content, English fallback and optional localized rendering,
  rendered fallback, and bounded error chains.
- Preserve source provenance as structured evidence: Rust tracing callsite
  fields; Java class/method/file/line from opt-in SFM-scoped caller capture;
  and, when a versioned source-hash-matching callsite manifest resolves it,
  canonical source URI, column, and exact span. Record capture mechanism and
  confidence, and never synthesize an exact source link from stale evidence.
- Keep the potentially stack-walking Java location path disabled for unrelated
  root-logger traffic, measure its disabled/SFM-only/enriched costs, and make
  caller-aware wrappers identify the application callsite rather than the
  facade implementation.
- Retain semantic message identity and arguments independently from English,
  localized, bilingual, or auto rendering. Early bootstrap/server records may
  lack a client locale, but must retain the English support fallback without
  fabricating a localized value.
- Map records into episode observations without treating log arrival order as
  the canonical action order. Correlate to episode/event/action/terminal/request
  ids when known.
- Bound retention and payload size, count drops, avoid recursive logger failure,
  and make cross-runtime clock skew visible rather than silently sorting by
  timestamps alone.

### [ ] 9.2 Inspect Java and Rust observations in-game

Add a normal addressable log/observation panel that subscribes to the shared
sink and works without opening a terminal. It can filter by runtime, episode,
branch, action, request, severity, target/logger, and correlation id; jump links
open the associated source span, document/selection/action, or correlated
runtime context where resolvable. Export remains structured, with provenance
and semantic message fields intact and copyable English/localized/bilingual
text as selectable projections.

### [ ] 9.3 Attach several presentation devices to one terminal session

- Preserve one PTY/session id while an external Teamy Terminal window and one
  or more in-game panels attach/detach independently.
- Separate observer subscriptions from the input lease. Define focus transfer,
  explicit collaborative/multi-writer mode, ordering, resize ownership, and
  emergency human revoke rather than accepting races implicitly.
- Let a terminal-launched game discover/receive the originating session id and
  attach back to it through typed local control. Do not infer ownership from a
  process tree alone.
- Record terminal input/output and attachment lifecycle as episode events and
  observations according to the terminal privacy/redaction policy.

## Next bounded vertical slice — TE-S1 temporal decimal-numbering chamber

TE-S1 is the chosen next implementation slice. It deliberately carries a thin
path through Phase 0.3–0.6, 1.4–1.6, 2.7–2.11, 4.4, 4.5, 4.7, 5.0, 5.4, and
selection X-3a instead of finishing every generic phase horizontally first.

### TE-S1M — prepared 16-hour supervised-trajectory milestone

TE-S1M is the recommended next autonomous goal and the first independently
valuable milestone of TE-S1. It adds Phase 0.6, 1.6, 2.10, and 2.11 plus only
the minimum 0.5/1.5/2.8/editor/X-3a seams needed to make one natural in-game
journey work. It does not claim all of TE-S1 complete.

**Copyable goal objective:**

> Complete the TE-S1M core in the snapshot episodes plan, then continue through
> its ordered elastic stretch ladder while the autonomous work window remains.
> First reconcile, validate, and locally checkpoint the current
> uncommitted 1.19.2 source-navigation/symbol-index work without discarding user
> changes. Then implement the non-destructive undo-tree seam, deterministic
> bounded trajectory planner, machine instruction pointer, planned-route layer
> in the live History Graph, and the supervised temporal-numbering chamber.
> After those observable and operational-readiness gates pass, checkpoint the
> core and claim stretch items S1M-X1 onward one at a time. If the work window
> ends, finish or truthfully leave active the currently claimed item, preserve a
> tested local checkpoint, and never weaken completion criteria.

The sixteen-hour outline is a minimum planning horizon rather than a scope
cliff, deadline, or permission to declare partial work complete. Approximate
stage budgets are guides; machine consistency is used to advance through the
ordered ladder when estimates prove pessimistic. Blocked time moves to the next
safe verification/checkpoint opportunity rather than forcing a rushed API.

#### Execution stages and local checkpoints

| Stage | Approximate horizon | Work | Required reversible checkpoint |
| --- | ---: | --- | --- |
| M0 — reconcile the runway | 0–2 h | Inventory the already-dirty Java navigation, Rust symbol-index, test, changelog, and plan files; run their focused tests; repair only current-scope failures; separate user/unrelated changes. | A local commit (or clearly documented still-failing current-scope checkpoint) precedes trajectory implementation. If Rust CLI inputs changed, run `check-all.ps1`, install after the final Rust edit, and verify PATH freshness. |
| M1 — freeze the seam | 2–4 h | Complete the bounded Java-facing contracts from 0.5/0.6 and X-3a; use fixtures before panel code. | Contract/fixture commit; undo-undo-do retains all children and serialization is deterministic. |
| M2 — prove planning | 4–7 h | Implement 1.6 A*/Dijkstra planner, supervision predicates, immutable replans, stale-parent pause, and oracle/property tests. | Pure-kernel commit; no Minecraft launch is required and no candidate transition is committed. |
| M3 — expose the machine | 7–11 h | Implement the minimal 2.8/2.10 History Graph panel, instruction-pointer/route legend, keyboard narration, and registered Plan/Step/Run/Pause/Replan/Select Route actions. | UI-model/panel commit with focused tests and compile proof. |
| M4 — close the vertical slice | 11–14 h | Add writable ignored chamber overlay, finite semantic/literal action catalog, target supervision contract, A* plan, step/run, undo fork, third item, replan, and retained old route. | Chamber/action commit; expected document and graph artifacts agree. |
| M5 — prove and checkpoint the core | 14–16 h | Run focused tests, compile, self-orchestrating puppet, visual inspection, changelog/bookkeeping, and CLI freshness if applicable; record a complete core handoff before entering stretch work. | Core evidence/plan commit. Do not push, propagate, tag, or release. If capacity remains, claim S1M-X1 rather than stopping because the estimate ended. |

**M0 completion evidence (2026-08-21):** Reconciled the complete inherited
navigation/index diff without discarding user work and checkpointed it as
`728694ad7` (`Repair dependency source navigation`). `check-all.ps1` passed
dependency policy, format, Clippy, build, 634 active Rust library tests (three
intentional ignores), all ten Java-analysis scenarios, and doc tests. The full
1.19.2 Java suite passed 1,199 tests with only the documented opt-in worker test
initially aborted; after installation that exact
`SFMSymbolServerInstalledIntegrationTests` class passed with the executable and
branch properties supplied. The canonical installed CLI reports revision
`728694ad7` and SHA-256
`931766D833441446EEC7BFE1D1BF52C71BDD459D40270269FEF1BF77A9EED857`.
No dependency declaration or lockfile changed, no repository was cloned, and
the user does not need to run `install.ps1`. M1 began from this checkpoint.

**M1 completion evidence (2026-08-21):** Completed selection X-3a and froze the
TE-S1M Java-local 0.5/0.6 seam. Selection history now retains undo-undo-do
siblings, exposes explicit ambiguous redo candidates, round-trips named heads
through deterministic JSON, and projects the same topology into the shared
history graph through hierarchical typed operations. The trajectory fixture
separates actual head, selected plan, instruction pointer, frontier, candidate
edges, and approval state and rejects dishonest effect/optimality claims.
Focused selection repository, selection projection/action, trajectory
contract, path-expression, and Text Editor v3 tests pass. Checkpoint M2 may now
implement the pure bounded planner without changing these contracts.

M1's contract is the synchronization point. After it is frozen, the pure
planner, History Graph presentation skeleton, and puppet fixture definitions
are parallelizable with disjoint write ownership. One integration owner alone
edits shared action registration, panel scene registration, generated/shared
schema files, changelog, and this plan. Parallel work is merged only after its
focused tests pass; no worker installs a shared PATH executable or edits the
same contract independently.

**M2 completion evidence (2026-08-21):** The pure planner described by 1.6 is
implemented and passes its adversarial oracle/boundary suite. A first review
identified optimistic heuristic handling, weak generator/time boundaries,
untyped blocking, rejected-state heuristic evaluation, state-id conflation,
and cross-plan stale-validation risks; each was repaired before checkpointing
and received explicit regression coverage. A second adversarial pass found the
step-bounded `(state, depth)` dominance counterexample; depth-labelled search,
a bounded exhaustive regression, start-heuristic gate ordering, and a freshly
supplied authoritative-state check repaired it. A final independent audit found
no remaining path-optimality flaw under the declared finite/canonical generator,
collision-free immutable identity, non-negative cost, and admissible-heuristic
contracts. Execution integration must freshly read the authoritative state when
consuming a stale-parent validation. Search remains projection-only: it
does not mutate chamber state, history heads, plan selection, or instruction
pointers. The independently developed History Graph projection and chamber
domain can now integrate against this frozen M2 API.

**M3 completion evidence (2026-08-21):** The bounded 2.8/2.10 machine-exposure
seam is implemented. `sfm:episode/history` is a registered panel scene;
episode selectors and selector-explicit Plan, Step, bounded Run, Pause,
Replan, Select Route, and Inspect Cost actions address trajectory machines
without panel-private mutation. The push-fed runtime replays a baseline to a
late subscriber, preserves per-listener FIFO order, isolates blocked/failing
listeners on daemon delivery workers, and never invokes listener code while
holding the runtime monitor. Immutable snapshots lazily prepare exactly one
memoized presentation on a bounded daemon executor; panel ticks observe
pending/ready/failure states without waiting or recomputing the graph.

The presentation contains the semantic
state → intent → evaluation → outcome → state narrative, typed details and
provenance, actual head, instruction pointer, selected executed prefix,
selected projected suffix, target, plan-scoped search frontier/open/closed
evidence, retained plans, and barriers. It never relabels an executed
candidate as committed history. Default panel opening pins the originating
episode when one exists, broad route choices narrow to an exact machine, live
pushes coalesce without a lost-update race, keyboard narration follows stable
row identities, and narrow panels wrap all seven action controls into reachable
positive-sized rows.

The first and final adversarial reviews found and closed applied-result
reclassification after publication failure, baseline/live ordering, callback
lock/reentrancy, cross-plan search identity, omitted semantic evidence,
executed-prefix styling, origin leakage, broad route choice, render-thread
projection, blocking subscriber delivery, and narrow-control reachability.
The following passed from the final tree with the installed locked CLI:

```pwsh
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMHistoryGraph --wait-for-build-lock --no-capture
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMTrajectoryMachineActionTests --wait-for-build-lock --no-capture
sfm-propagate-changes.exe test run --branch 1.19.2 --filter OpenPanelActionTests --wait-for-build-lock --no-capture
sfm-propagate-changes.exe run compile --branch 1.19.2 --wait-for-build-lock
```

M3 intentionally completes only TE-S1M's minimal History Graph seam, not every
generic 2.8 action or validation item. Chamber-owned mutation, checkout/undo
journey controls, supervision readiness, and natural in-game proof remain M4
and M5 work. No dependency declaration, checked-in lockfile, or CLI source
changed; the installed CLI remains current and the user need not run
`install.ps1` for this checkpoint.

**M4 chamber integration guardrails from the pre-integration audit
(2026-08-21):** The current chamber-domain files are exploratory input, not a
safe checkpoint. M4 must close every item below before treating chamber tests
or supervision evidence as authoritative:

- Bind exact-target supervision to the canonical start, target derivation,
  replacement set, executed transition chain, and resulting document. A caller
  supplying only matching `expectedText` must not be able to forge readiness.
- Measure ambient-checkout unchanged and retained-parent immutability from
  before/after evidence; do not assert either fact as a constant supplied by
  the chamber itself.
- Keep stable semantic intent identity independent from parent revision,
  frozen witness, and evaluation result. Those belong to evaluation/execution
  evidence so the same intent can be re-evaluated on a later branch.
- Prove the exact fork `initial -> selected -> numbered`, then Ctrl+Z from
  `numbered` back to `selected`, followed by third-item insertion from that
  selected parent. The old numbered child and the new insertion child must
  remain siblings and both remain reachable.
- Feed chamber candidates to the planner using the planner's canonical
  `Transition.orderingKey`; do not substitute chamber-local string sorting.
  Reject malformed UTF-16 rather than hashing replacement characters, and
  namespace every externally visible revision/action/evidence identity by
  episode and document.
- Execute through a typed, plan-scoped manifest that retains the exact action
  payload for each trajectory step. Never reconstruct executable chamber
  actions from labels or display arguments.
- Start the chamber adapter with admissible `h = 0`, allow only `PURE` effects,
  keep all candidates outside committed history, validate a freshly read
  authoritative parent before each step, and update document/head/instruction
  pointer atomically. Replan creates a retained child plan revision rather than
  mutating or deleting the old route.
- Add A*, Dijkstra, and bounded exhaustive-oracle evidence for the declared
  finite catalog, including malformed input, stale parent, cancellation,
  budget exhaustion, and the exact two-branch journey. No shortest-path or
  supervision-ready claim is accepted from the existing happy-path tests.

**M4 completion evidence (2026-08-21):** The temporal decimal-numbering
chamber is now a registered ordinary panel scene backed by writable Text Editor
V3 state and the frozen trajectory contracts. Its typed finite catalog keeps
stable semantic intent separate from plan-scoped witnesses and payloads, uses
the planner's canonical ordering, permits only pure effects, validates a fresh
authoritative parent before each step, and atomically advances document head
and instruction pointer. Exact supervision derives its target and evidence
from the canonical start plus executed chain; it reaches
`SUPERVISION_READY` with a human approval requirement and never manufactures
approval. The A*, Dijkstra, and bounded exhaustive oracle tests agree on cost
2 for both the two-item and three-item routes and cover malformed input,
cancellation, exhausted bounds, stale parents, and retained replans.

Natural Text Editor V3 input participates in the same immutable history. The
proved journey is initial → selected → numbered, Ctrl+Z to selected, then
ordinary character input inserts `- apricots` as a sibling while retaining the
old numbered child. A stale step reports `STALE_PRECONDITION` without adding a
state, and Replan retains plan 1 while selecting plan 2 from the new
authoritative branch. `sfm:document/history/undo` is registered and defaults to
Ctrl+Z only in the temporal-document keyboard situation.

**M5 core checkpoint evidence (2026-08-21):** The self-orchestrating
`title_screen_temporal_trajectory_machine` puppet passes at
`1280x720@auto`. It opens the chamber and History Graph through command-palette
actions, pauses at nine meaningful checkpoints, types the third item through
the real editor input path, and emits exact document, episode, trajectory,
search, supervision, transcript, stage, and checkout artifacts under
`platform/minecraft/runGameTestPreview/puppet-artifacts`. The corresponding
nine screenshots under `platform/minecraft/runGameTestPreview/screenshots`
were visually inspected: the two- and three-item documents are readable, the
old branch survives undo, committed/executed/projected/head/frontier/IP states
remain distinguishable, and the final document is exact 1/2/3 numbering.
Machine evidence records two cost-2 A* plans and explicit replan lineage;
supervision remains `SUPERVISION_READY`; before/after ambient-checkout SHA-256
values are byte-identical.

The focused chamber/history/action/editor/keybinding/source tests pass, the
complete 1.19.2 JUnit invocation exits successfully (with only the documented
opt-in installed-worker integration assumption-aborted when its two `-D`
properties are omitted), and `sfm-propagate-changes.exe run compile --branch
1.19.2 --wait-for-build-lock` passes. No dependency declaration or checked-in
lockfile changed and no cache acquisition or repository clone was needed. This
stage changes only Java, tests, docs, and game resources, so the installed CLI
remains the verified `728694ad7` executable at
`G:\Programming\Caches\CARGO_HOME\bin\sfm-propagate-changes.exe`; user install
required: no. The puppet and its game/helper processes exited, and process
inspection found no in-scope runtime intentionally left running. The core is
ready for its local checkpoint before S1M-X1 is claimed.

#### Observable completion state

1. From the title screen, the command palette opens a writable temporal
   numbering chamber and `sfm:episode/history` beside it through normal panel
   actions.
2. The History Graph visibly distinguishes committed history from projected
   candidates. It marks actual head, target, selected route, search frontier,
   and a clear instruction pointer at the next action.
3. Planning from `- apples` / `- bananas` to the supervised numbered target
   selects the minimum-cost route in the declared finite catalog. The details
   show its cost/policy, and a Dijkstra/exhaustive oracle artifact confirms it.
4. Step advances exactly one instruction after validating the parent. Bounded
   Run reaches `SUPERVISION_READY`, never silently `APPROVED`, and the visible
   document becomes `1. apples` / `2. bananas`.
5. Ctrl+Z moves the focused document head while retaining the old numbered
   child. Adding `- apricots` creates a sibling; replanning preserves the first
   plan and selects a route to `1`, `2`, `3`.
6. A deliberately stale planned step pauses at the instruction pointer with a
   readable choice among frozen witness, re-evaluate, and replan. It does not
   mutate the document.
7. Keyboard and mouse can inspect routes and use Plan/Step/Pause/Replan; the
   default graph remains readable without enabling cost-debug details.
8. The puppet pauses at meaningful states and captures screenshots, terminal
   text, starting/final documents, supervision contract, action/history graph,
   search evidence, selected route, instruction-pointer transitions, retained
   sibling plans, and checkout-unchanged proof.

#### Validation and manual-test commands

```pwsh
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMSelectionRepositoryTests --wait-for-build-lock
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMTrajectory --wait-for-build-lock
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMHistoryGraph --wait-for-build-lock
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMTextEditorPanelTests --wait-for-build-lock
sfm-propagate-changes.exe run compile --branch 1.19.2 --wait-for-build-lock
sfm-propagate-changes.exe puppet run title_screen_temporal_trajectory_machine --branch 1.19.2 --variant 1280x720@auto --wait-for-build-lock
```

The final handoff repeats the puppet's ordinary command-palette sequence for
manual use, names the artifact directory, states the exact final game/helper
process state, and reports `User must run install script: no` or the precise
external blocker. The agent visually inspects the route/instruction-pointer
screenshots rather than accepting artifact existence alone.

#### Regret-minimizing authority and exclusions

- Work only on `1.19.2`; do not run cross-version propagation.
- Make local, reviewed checkpoint commits by stage. Do not push, tag, publish,
  release, rewrite history, reset/stash user work, or delete broad state.
- The dependency posture is frozen under the repository goal-readiness guide:
  no dependency/lockfile/declaration changes, no new unpinned repositories, and
  only deterministic existing-lockfile cache rehydration.
- The writable chamber uses ignored bounded overlay state. It cannot apply to
  ambient repository files, run shell/process/network actions, mutate a live
  world, or receive external-agent authority.
- No gameplay overlay host, general CSS/layout DSL, general program synthesis,
  cross-runtime Rust interchange, release review, audio/video, Teamy-Terminal
  sharing, or Codex provider belongs to the TE-S1M core. Candidate-history
  comments enter only through S1M-X2 after the core and S1M-X1 pass.
- “Shortest” means minimum cost within the chamber's versioned finite action
  catalog and cost policy. An unbounded generator, invalid heuristic, unknown
  effect, exhausted budget, or stale parent produces an inspectable non-success
  result instead of an optimistic claim.
- If current dirty navigation/index work cannot reach its existing focused
  gates, remain on that repair/checkpoint path and do not mix trajectory code
  into it. If a new dependency or destructive migration appears necessary,
  stop at the last passing local checkpoint and request human direction.
- Reaching an estimated time horizon without every core observable criterion
  leaves the goal active. Once the core passes, do not idle merely because the
  estimate was pessimistic: claim the next stretch item. The handoff names the
  last passing stage/item, commits, tests, installation state, and exact next
  action; elapsed time is never evidence of completion.

#### Elastic continuation ladder after the TE-S1M core

This queue answers “what next?” before unattended work begins. It is strictly
ordered by architectural proximity and reversibility, not speculative duration.
The core gets a complete tested commit first. Then the coordinator marks one
stretch item `[~]`, records its start commit, and finishes/tests/commits it
before claiming another. Unstarted items do not contaminate the current diff.
If work is interrupted, a claimed incomplete item keeps the goal active and is
reported precisely; no partial item is relabelled complete to reach the next.

| ID | Status | Stretch item | Observable completion | Gate/exclusion |
| --- | --- | --- | --- | --- |
| S1M-X1 | `[x]` | Complete 2.12 candidate-history scrubbing. | The user scrubs every projected route frame, including unavailable/barrier states, while actual head and instruction pointer remain unchanged; old-plan frames survive replan. | Completed and checkpointed as `a99ca3db7`; reuses its trajectory/state projections; no comments yet. |
| S1M-X2 | `[x]` | Complete 2.13 and comment Phase 6b candidate targeting. | A comment can target a candidate glyph, action, state, or route; replan preserves it; exact execution permits explicit promotion; divergence transfers neither target nor approval. | Completed against passing X1 checkpoint `a99ca3db7`; uses the existing comment persistence/selection adapter seam; no release-approval workflow. |
| S1M-X3 | `[x]` | Add side-by-side route comparison and review disposition. | Two retained trajectories can be scrubbed in lockstep or independently, their costs/outcomes/comments compared, and one selected/rejected without deleting either. | Completed and checkpointed as `a03800dff` from passing X2 checkpoint `2583b573f`; uses existing panels and comments; no generalized graphical markup requirement. |
| S1M-X4 | `[x]` | Finish the remaining TE-S1 exact-replay/semantic-rebase artifacts around the chamber. | Raw event → binding → semantic action → transition layers round-trip; exact replay and rebase produce the documented two- versus three-item histories. | Completed in core checkpoint `d8b55251e` and natural-proof checkpoint `8a98063e5`; Java-local only in this checkpoint, with Rust/shared interchange left explicitly open. |
| S1M-X5 | `[x]` | Complete 2.9's whole-workspace `A.java`/`B.java` counterfactual journey. | The visible graph compares checkout, frozen witness, and re-evaluated intent after changing the earlier explorer selection, with typed barriers and retained original history. | Completed and checkpointed as `f0ccc3c81` from clean passing checkpoint `4922fc713`; uses only restorable UI/document state and never writes the ambient checkout. |
| S1M-X6 | `[x]` | Adapt the proven History Graph to workspace Track 1c's passive overlay seam. | The same history content remains visible while a test world ticks; passive mode consumes no gameplay input and interactive mode has explicit focus/release. | Completed and checkpointed as `a8d7d3fd9` from clean passing checkpoint `7995a0761`; it uses a reusable scene/host/action substrate rather than a one-off History Graph HUD. |

#### S1M-X3 claim boundary — retained-route comparison and disposition

X3 introduces one bounded, versioned comparison model and one ordinary
workspace panel that owns two immutable route addresses. It reuses candidate
projection and comment-query seams rather than copying route states or comments.
The panel presents left and right frame views side by side, with route identity,
accumulated/total cost, terminal outcome/status/hash, and comment summaries.

- Comparison starts in lockstep mode. Seeking either side maps the requested
  progress deterministically onto both routes, including unequal route lengths;
  independent mode lets each cursor move without moving the other. Neither mode
  changes actual history head, instruction pointer, selected plan, or selected
  route.
- Review disposition is typed metadata keyed by immutable plan-revision/route
  identity: `UNDECIDED`, `PREFERRED`, or `REJECTED`. At most one compared route
  is preferred in a comparison session, but zero, one, or both may be rejected.
  Disposition does not masquerade as a comment, approval, or trajectory-machine
  selection and does not alter either retained route or its comments.
- Registered actions open a comparison, switch lockstep/independent mode, seek
  either/both cursors, set/clear disposition, and explicitly select a retained
  route in the trajectory machine. Only that last explicit selection action may
  mutate the machine's selected route; it must fail visibly if the immutable
  route address is no longer selectable.
- The comparison session and dispositions round-trip deterministically. Reopen
  resolves the same immutable routes and comment summaries; missing/unavailable
  frames remain inspectable statuses rather than causing route substitution.
- Focused tests cover unequal lengths, lockstep mapping, independent seeking,
  cost/outcome/comment comparison, disposition invariants, persistence, stale
  route failure, and non-mutation. A natural title-screen puppet creates and
  comments on two retained alternatives, compares/scrubs them in both modes,
  rejects one and prefers the other, proves both routes/comments remain, proves
  comparison alone leaves actual head/instruction pointer/selection unchanged,
  then invokes explicit route selection and records visible PNG plus structured
  JSON evidence.

The slice does not add generalized graphical review markup, a general-purpose
comparison framework, route deletion, approval derivation, new dependencies, or
cross-runtime interchange.

#### S1M-X4 claim boundary — causal archive, exact replay, and semantic rebase

X4 was claimed on 2026-08-21 from clean passing checkpoint `2929561ee`. It
extends the existing temporal decimal-numbering chamber and History Graph; it
does not create a parallel demo or relabel trajectory `Replan` as semantic
rebase. The implementation is Java-local and bounded to pure/snapshot-restorable
chamber state. Full Rust/shared-schema conformance for Phase 0/1 remains open.

##### [x] S1M-X4-A Freeze the Java-local causal archive contract

**Work:**

- Add one versioned, validated archive containing complete chamber frames and
  immutable branch edges plus ordered raw/source events, binding decisions,
  semantic invocations, evaluations, ordered selection witnesses, outcomes,
  transitions, observations, and replay/rebase reports.
- Preserve honest event origin. Physical keyboard/character/focus events retain
  their real input data; command-palette, registered-action, controller/test API,
  and replay-generated operations use explicit typed origins and must never be
  fabricated as keystrokes.
- Persist a canonical effective-binding snapshot digest and the binding id,
  revision, active context, match/conflict/consumption result, and exact source
  event range whenever a binding participated. A non-binding origin records an
  explicit not-applicable decision rather than omitting provenance ambiguously.
- Give records monotonic logical sequence identities and bounded validation.
  Retain typed target document, selection expression, evaluator/order/geometry
  revisions, `source_order` or `canvas_reading_order_yx`, and every exact ordered
  witness region rather than only a witness hash.

**Completion criteria:** Two traces with the same semantic action count but
different raw provenance remain distinguishable, every transition is reachable
through its causal links, and no archive consumer must infer that a controller
operation was a physical input event.

##### [x] S1M-X4-B Add deterministic codec and restoration proof

**Work:**

- Add a strict canonical JSON codec following the selection-archive precedent,
  with stable ordering, schema/version validation, bounded counts/bytes, and
  explicit malformed/reference/hash diagnostics.
- Round-trip a checked-in canonical fixture and a captured chamber archive as
  encode → decode → encode with byte-identical canonical output.
- Restore the complete immutable state/edge/head graph into a fresh Java-local
  replay runtime; random-seek every restored frame and prove the parent frame
  bytes and hashes did not change.

**Completion criteria:** A fresh runtime can import the archive, address every
recorded frame and causal record, and export the same canonical bytes; corrupt,
unsupported, oversized, dangling, and hash-invalid input fails closed.

##### [x] S1M-X4-C Implement explicit replay operations

**Work:**

- Add typed `ExactReplay` and `SemanticRebase` requests/reports. Exact replay
  starts from an explicitly identified recorded parent, reuses the recorded
  action/evaluation/witness, and either creates/verifies the same two-item result
  or returns a structured precondition mismatch without partial mutation.
- Semantic rebase starts at an explicit historical boundary and re-evaluates
  only suffix actions whose policy permits deterministic intent re-evaluation.
  It records old/new evaluation and witness lineage per action, creates a new
  child branch with the three-item result, and preserves the original two-item
  state, edge, witness, and parent hash.
- Conflicts, stale parents, ineligible frozen actions, and cancelled/budgeted
  work remain typed inspectable results. Neither operation silently chooses a
  branch, mutates an existing revision, or promotes human approval.

**Completion criteria:** Focused tests prove exact two-region replay, changed-
prefix mismatch, three-region semantic rebase, action-level lineage, sibling
retention, deterministic repeated execution, and zero partial mutation on every
failure path.

##### [x] S1M-X4-D Join runtime input, registered actions, and presentation

**Work:**

- Record the narrow real-input seam where the dynamic keybinding service knows
  the input event, effective binding, decision, and invocation; correlate the
  resulting registered chamber action with its evaluation/outcome/transition.
- Register the chamber's select-all-matching-hyphen action and default
  Ctrl+Alt+J binding, decimal-sequence replacement, exact replay, semantic
  rebase, and causal-detail inspection through ordinary selector-explicit SFM
  actions. Programmatic chamber/test operations record their actual origin.
- Extend History Graph detail/projection so a user can follow raw event →
  binding decision/revision → semantic invocation → evaluation/witness →
  transition/outcome, and can visibly distinguish exact replay, frozen-witness
  mismatch, and semantic rebase. Candidate History remains a read-only
  projection of the retained two- and three-item endpoints, never replay
  authority.
- Keep recording, codec, replay, and projection work off the render thread;
  publish generation-checked immutable snapshots and preserve ordinary editor
  focus/navigation behavior.

**Completion criteria:** Natural in-game use exposes the complete causal chain
and both replay modes through registered actions, with distinct visible two-
item exact and three-item rebased outcomes and no render-thread wait.

##### [x] S1M-X4-E Prove the natural journey and checkpoint X4

**Work:**

- Add a focused title-screen puppet using the real dynamic-keybinding path for
  Ctrl+Alt+J and ordinary registered actions for numbering, exact replay, undo,
  historical insertion, and semantic rebase.
- Capture readable PNG checkpoints plus canonical JSON/text artifacts for raw
  events/modifiers, effective binding digest/id/revision/source range, semantic
  action links, complete old/new witnesses, replay reports, retained states and
  edges, exact two-item and rebased three-item documents, and ambient-checkout
  before/after hashes. Pause long enough for a watching human to follow it.
- Parse the generated artifact in an automated test and cross-check its visible
  labels/state against the structured evidence. Run focused tests, full Java
  suite, 1.19.2 compile, natural puppet, and visual artifact inspection.

**Validation:**

```pwsh
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMTemporalReplay --wait-for-build-lock --no-capture
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMHistoryGraph --wait-for-build-lock --no-capture
sfm-propagate-changes.exe test run --branch 1.19.2 --wait-for-build-lock
sfm-propagate-changes.exe run compile --branch 1.19.2 --wait-for-build-lock
sfm-propagate-changes.exe puppet run title_screen_temporal_replay_rebase --branch 1.19.2 --variant 1280x720@auto --wait-for-build-lock
```

**Completion criteria:** The canonical artifact and inspected screenshots agree
that one real input history led through its recorded binding and semantic action
to the retained two-item exact history, while an explicit historical insertion
and semantic rebase produced a distinct retained three-item child; the checkout
is byte-identical, dependencies/lockfiles are unchanged, tooling freshness and
final process state are recorded, and X4 is committed before X5 is claimed.

**X4 exclusions:** This checkpoint does not add pointer/controller episode
coverage, a general snapshot repository format, Rust/shared-schema interchange,
arbitrary suffix merges, CRDT/concurrent editing, recipe promotion, checkout
writes, graphical markup, or release approval. These omissions prevent X4 from
marking Phase 0.1–1.4 or all TE-S1 complete.

#### [x] S1M-X5 claim boundary — whole-workspace counterfactual journey

X5 widens the proven temporal semantics from one document chamber to one
bounded, isolated workspace fixture. Its `A.java` and `B.java` documents are
in-memory resolver content, not files in the checkout. The authoritative state
frame includes panel/layout identity, focused panel, explorer location and
selection, open document identity, complete document bytes, and the revisions
needed to reject stale evaluation. Ordinary explorer, Text Editor v3, command-
palette, and History Graph components project or dispatch this state; none owns
an independent counterfactual truth.

##### [x] S1M-X5-A Define the workspace state and deterministic artifact

- Add a versioned bounded model for complete restorable workspace frames,
  dynamic action intent, concrete dependency witnesses, compatible/rejected
  suffix decisions, typed barriers, retained branches, and recorded head
  movements. Canonical ordering and hashes must not depend on map iteration,
  wall-clock time, framebuffer size, or ambient paths.
- Add strict canonical JSON plus a readable transcript. A fresh runtime must
  parse, validate, restore, and byte-identically re-encode the artifact. Unknown
  schemas, duplicate ids, dangling references, over-budget content, or an
  impossible successful barrier fail closed without partial publication.
- The fixture captures an ambient-checkout hash before and after but neither
  checkout nor replay writes a host file. `A.java` and `B.java` remain editable
  in-memory document values with explicit logical addresses.

##### [x] S1M-X5-B Record and compare the three narratives

- Record the natural title-screen → palette → explorer-open → `A.java` selected
  → open-selected-document → edit journey, including focus, layout, explorer
  selection expression, exact `A.java` witness, editor/document identity,
  complete pre/post text, and action/evaluation/outcome links.
- Fork at the state immediately before explorer selection and naturally select
  `B.java`; retain the original A branch and every intermediate state.
- Expose three separately named operations: checkout the already-recorded A
  result without execution; re-execute the open/edit suffix using the frozen
  `A.java` witness; and re-evaluate `open selected document` against the B
  parent, obtaining a new `B.java` witness before applying only compatible
  restorable suffix actions.
- Include one declared external/irreversible suffix operation. Projection stops
  at a typed `EXTERNAL_BARRIER`, reports the unapplied action and reason, and
  publishes no fabricated result state or partial external effect.

##### [x] S1M-X5-C Use ordinary frontline surfaces

- Open the fixture through a registered action and the normal command palette.
  Host a real generic explorer over a fixture-owned in-memory resolver/session;
  selecting/opening rows uses canonical semantic action drafts. Open editable
  in-memory documents through the ordinary Text Editor v3 panel/save seam.
- Display the same authoritative controller in the existing
  `sfm:episode/history` panel. Labels/details must make selected A versus B,
  frozen versus re-evaluated witness, compatible edit, recorded checkout,
  retained original branch, and external barrier understandable without JSON.
- Counterfactual operations are registered selector-explicit actions. UI
  controls invoke them rather than mutating controller-private state, and
  projection/publication never waits on the render thread.

##### [x] S1M-X5-D Prove the natural journey and checkpoint X5

- Add pure tests for state/hash determinism, branch retention, stale-parent
  rejection, exact checkout non-execution, frozen-witness targeting,
  intent re-evaluation, compatible suffix filtering, barrier atomicity,
  canonical round-trip, bounds, and ambient non-mutation.
- Add a watched title-screen puppet that uses the command palette and ordinary
  panel input to select/open/edit A, forks before selection, selects B, then
  invokes and captures all three narratives. Pause at readable stages.
- Emit inspected PNG stages plus canonical JSON, UTF-8 transcript, per-stage
  state evidence, visible History Graph labels, and ambient before/after hashes.
  An automated test parses the artifact and cross-checks the visible lanes with
  the machine-readable branch/witness/barrier facts.
- Run focused X5 tests, the full 1.19.2 Java suite, canonical 1.19.2 compile,
  natural puppet, visual inspection, and `git diff --check`; record dependency,
  lockfile, tool-installation, process, and manual-test readiness explicitly.

**Completion criteria:** A watching user sees the natural A journey and retained
A branch, changes the earlier selection to B, and can distinguish recorded A
checkout, frozen A replay, and re-evaluated B execution in the ordinary History
Graph. The B suffix visibly identifies what was recomputed, what was compatible,
and where an external action was stopped. Canonical evidence round-trips and
agrees with the screenshots, no host file changes, X5 is locally checkpointed,
and only then may X6 be claimed.

**X5 exclusions:** No ambient-file mutation, live-world rewind, process/network
replay, arbitrary suffix merge, cross-runtime interchange, generalized snapshot
repository, graphical markup, release approval, or new explorer/editor
replacement is introduced by this proving fixture.

**S1M-X5 completion evidence (2026-08-21):** Implementation checkpoint
`f0ccc3c81` adds the bounded in-memory `A.java`/`B.java` workspace contract,
strict canonical JSON and transcript codecs, fresh-runtime restore, an ordinary
lazy Explorer resolver, writable Text Editor V3 integration, selector-explicit
counterfactual actions, and History Graph projection of recorded checkout,
frozen-witness replay, intent re-evaluation, head movements, retained branches,
and a childless typed external barrier. Every immutable frame retains complete
document bytes and hashes plus layout, focus, explorer location/selection, and
open-document identity; no fixture operation writes the ambient checkout.

Focused `SFMWorkspaceCounterfactual` and `WorkspaceCounterfactual` tests pass.
The full 1.19.2 Java suite exits `0`, with only the installed-worker integration
test intentionally aborted without its opt-in JVM properties, and the canonical
1.19.2 compile exits `0`. Natural puppet run
`title_screen_wor-20260821-131846-300` produced seven visually inspected PNG
stages plus per-stage JSON, canonical episode JSON, UTF-8 transcript, and
aggregate evidence. The evidence reports `canonical_round_trip: true`, proves
identical ambient before/after hashes, retains the original A branch, shows
recorded checkout without execution, frozen replay still targeting A after B
selection, re-evaluation targeting B, and the irreversible operation as an
`EXTERNAL_BARRIER` action attempt with no fabricated result state. `git diff
--check` passes. No dependency declaration or checked-in lockfile changed, no
new dependency or repository was introduced, and no CLI source changed, so the
installed `728694ad7` CLI remains current and no user install is required. The
puppet-owned game and helper processes exited cleanly.

#### [x] S1M-X6 claim boundary — reusable non-pausing gameplay overlay

X6 was claimed on 2026-08-21 from clean passing X5 bookkeeping checkpoint
`7995a0761`. It implements the first complete workspace Track 1c vertical slice:
one reusable overlay substrate and the existing History Graph as its first
content consumer. It does not create a History Graph-specific Forge HUD, a
second history model, or an overlay-shaped full-screen `Screen`.

##### [x] S1M-X6-A Freeze scene, placement, and direct-restore contracts

- Add versioned immutable DTOs for client scene state, stable overlay instance
  id, content recipe/state, visibility, `PASSIVE`/`INTERACTIVE` input mode,
  focus, z-order, and placement. DTO identity cannot depend on widget, GLFW,
  Minecraft object, framebuffer, or Java object identity.
- Placement names a finite reference frame, normalized finite reference/content
  anchors, logical offsets, optional bounded size constraints, and explicit
  safe-area/clipping policy. Viewport/GUI-scale changes recompute bounds
  deterministically; corner values are ordinary placement values, not distinct
  action kinds.
- Add strict canonical serialization, validation, bounded recovery, and an
  unknown-content placeholder. A fresh runtime loads current scene state
  directly before its first render and byte-identically re-encodes it; restore
  never replays historical layout actions.

##### [x] S1M-X6-B Implement a reusable host and honest input lifecycle

- Define one narrow host-independent overlay-content/render boundary so the
  same History Graph projection/state can render in a multiplexer leaf or HUD
  overlay without duplicated application state.
- Render through a version-adapted SFM HUD seam distinct from Minecraft's
  loading overlay and modal GUI-layer stack, with client-only class isolation.
  Rendering and projection publication must not wait on the render thread.
- A passive overlay is visible and may update/narrate, but is absent from
  pointer, keyboard, scroll, and gameplay consumption. Interactive mode begins
  only through explicit focus, routes input by z-order/hit bounds, owns the
  cursor while focused, and has explicit release through Escape/action, focus
  loss, screen/modal transition, stale instance, world unload, and disconnect.

##### [x] S1M-X6-C Register selector-explicit actions and remote parity

- Add stable set-valued overlay selectors/runtime operations and registered
  actions for visibility set/toggle, placement set, input-mode set, focus,
  release, and z-order. Visibility and placement remain independent; hide/show
  retains placement and content state.
- Every mutation passes through the client-action dispatcher and returns a
  typed per-target result. There is no focus-implicit mutation and no direct UI
  field edit hidden behind an action-shaped button.
- Invoke at least one complete overlay operation through the existing local
  `sfm.exe` multi-instance control path. It must reach the same registered action
  and scene runtime as command-palette and puppet invocation; no special test
  socket or helper-side state mutation is accepted.

##### [x] S1M-X6-D Adapt the existing History Graph as the first consumer

- Register one stable History Graph overlay recipe/instance that resolves the
  same authoritative `SFMHistoryGraphRuntime` snapshot and presentation
  projection as `sfm:panel/open sfm:episode/history`.
- The passive graph follows live history while gameplay continues. Switching
  host or mode does not fork/copy history state, move the actual history head,
  or turn projections into committed transitions.
- Visible content identifies graph/session state, current head, retained branch
  or barrier information, overlay id/mode/focus, and placement sufficiently for
  screenshots and structured evidence to agree without relying on transient
  object identity.

##### [x] S1M-X6-E Prove the in-world journey and checkpoint X6

- Add pure tests for canonical scene round-trip/direct restore, malformed and
  bounded recovery, anchors/offsets/constraints, viewport changes, selector set
  semantics, visibility-placement independence, unknown content, stale ids,
  z-order/hit routing, and lifecycle cleanup.
- Add host/input tests proving passive transparency and explicit interactive
  focus/release. The proof records consumed and forwarded input rather than
  inferring transparency only from a screenshot.
- Add a self-orchestrating gameplay puppet that enters a test world, shows a
  passive History Graph while world ticks and movement input continues, enters
  interactive mode, demonstrates routed overlay interaction, releases focus,
  changes placement through a registered action, hides and re-shows at the
  retained placement, performs one equivalent external `sfm.exe` invocation,
  restores canonical scene state directly, and proves world-unload cleanup.
- Emit readable inspected PNG stages plus canonical scene JSON, history/session
  identity, tick/movement/input-consumption evidence, focus/mode/bounds/
  placement/z-order transitions, action ids/results, CLI invocation evidence,
  direct-restore evidence, and final cleanup state.
- Run focused overlay/workspace/history tests, the full 1.19.2 Java suite,
  canonical 1.19.2 compile, natural gameplay puppet, visual inspection, and
  `git diff --check`; then record dependency/lockfile/tool-installation/process/
  manual-test readiness and create a local checkpoint before finishing X6.

**Completion criteria:** A watching user sees the ordinary live History Graph
over a ticking world, movement continues while it is passive, overlay input is
accepted only after explicit focus, release restores gameplay control, and
hide/show plus direct restore preserve placement. The same state changes are
selector-explicit registered actions, including one invocation through
`sfm.exe`; artifacts prove input routing and cleanup. Dependencies and lockfiles
remain unchanged and X6 is locally checkpointed.

**X6 exclusions:** This bounded slice does not add pointer drag/resize gestures,
FPS or size-display consumers, a general textual/CSS layout DSL, world/entity
reference frames, arbitrary third-party widget hosting, live-world rewind,
cross-runtime history interchange, graphical review markup, or release
approval. Those omissions do not weaken the reusable host, placement, input,
action, persistence, and History Graph seams proved here.

**S1M-X6 completion evidence (2026-08-21):** Implementation checkpoint
`a8d7d3fd9` adds the strict versioned `sfm.client-scene/1` scene contract,
stable overlay/content identities, normalized anchor/offset/size/clipping
placement, canonical direct restore, selector-set controller, generic Forge HUD
host, and registered visibility, placement, input-mode, focus/release, and
z-order actions. Passive content is absent from every input-consumption path;
interactive content requires explicit single-target focus, raw keyboard,
character, pointer, and scroll routing honors topmost hit bounds, held pointer
presses retain capture through release, and Escape, screen/modal transitions,
focus loss, stale content, world unload, and disconnect release ownership. The
first recipe instantiates the ordinary `SFMHistoryGraphPanel` against the same
singleton `SFMHistoryGraphRuntime`; it does not copy history state or add a
History Graph-specific HUD.

Eighteen focused overlay action/contract/controller/codec tests pass via
`sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMOverlay
--no-capture --wait-for-build-lock`. The full parallel 1.19.2 Java suite exits
zero; its existing installed-symbol-worker integration test is explicitly
assumption-aborted because that opt-in run did not supply
`sfm.symbol.workerExecutable`/`sfm.symbol.workerBranch`. The canonical
`sfm-propagate-changes.exe run compile --branch 1.19.2
--wait-for-build-lock` passes. The final natural puppet
`sfm:in_world_history_graph_overlay` passes at `1280x720@auto`, exits cleanly,
and emits seven inspected PNGs plus six JSON reports under
`platform/minecraft/build/sfm-toolchain/artifacts/game-test-preview/runs/
sfm-in_world_his-20260821-145713-301/in_world_history_graph_overlay/
1280x720_auto`.

The puppet records 30 passive world ticks with 6.216 blocks of movement and two
forwarded W events, then 30 interactive ticks with zero movement, four consumed
keys, and a changed History Graph selection, followed by explicit release and
another 6.216-block movement interval. Registered placement moves the retained
graph from upper-right to lower-left; hide/show retains placement and host-local
selection. A short-lived external `sfm.exe` process invokes canonical action
`sfm action invoke sfm:overlay/z-order/set id(sfm%3Ahistory) 321`, exits zero,
reports one applied target, and advances the same runtime action counter. Direct
restore is byte-identical before render, replays no layout actions, and retains
bounds `(8,42,320,190)` after render. Natural world unload leaves no focus or
hosted overlay and suppresses the stale held-W release. Final screenshots make
passive, focused, released, moved, hidden/re-shown, CLI-mutated, and restored
states visually distinguishable while leaving substantial live-world context
visible.

`git diff --check` passes. The staged path audit contains no Cargo/SFM lockfile,
dependency declaration, Gradle manifest, or toolchain-lock change; no cache
mutation, new dependency, or repository clone was required. No Rust CLI source
changed, so installed CLI epoch `728694ad7` remains current and the user does
not need to run `install.ps1`. All puppet-owned game/helper/CLI processes exited
normally. Manual testing is ready by launching the 1.19.2 client with the
installed CLI, entering a world, and invoking the documented
`sfm:overlay/*` actions against `id(sfm%3Ahistory)`; no preparatory install or
cleanup step remains.

**S1M-X1 completion evidence (2026-08-21):** Added the versioned
`sfm.candidate-history/1` frame/address contract, a bounded off-render-thread
projection seam, dynamic timeline bounds, and the registered
`sfm:episode/candidate-history` scene. Materialized frames carry hash-matched
immutable document bytes; cancelled, conflicted, external-barrier, and unknown
frames carry no document bytes and identify their last trustworthy predecessor.
The natural title-screen puppet plans and scrubs the two-item future, executes,
undoes, edits a third item, replans, proves the still-open old panel remains
pinned byte-for-byte, and independently scrubs the new route. Its second
deterministic status route seeks in both directions across materialized,
unavailable, invalidated, external-barrier, and unknown frames while JSON proves
controller revision, actual head, and instruction pointer remain unchanged.
Focused candidate-history/controller/dynamic-timeline/source-journey tests and
the 1.19.2 compile pass; the final puppet screenshots and JSON were visually and
structurally cross-checked in artifact run
`title_screen_can-20260821-072754-848`. No dependency declaration or checked-in lockfile
changed, no cache acquisition or repository clone was needed, and no CLI source
changed, so the installed `728694ad7` CLI remains current and no user install is
required.

**S1M-X2 completion evidence (2026-08-21):** Added the versioned
`sfm.review-comment-session/2` model, deterministic codec/store, v1 migration,
candidate-target adapter, review runtime, registered comment/promotion/
migration actions, candidate-history labels, and review-explorer projection.
Candidate route, action, state, and pinned materialized-glyph targets retain
their immutable plan/route/frame identity through replan and persistence;
unavailable frames accept route/action observations while rejecting fabricated
glyph witnesses. Exact execution creates a distinct committed revision and an
explicit provenance-preserving promoted comment only after the user invokes
promotion. Divergence creates no committed target, while witnessed migration
requires explicit evidence and remains separate from human approval.

The candidate navigation seam now defers pinned seeks until asynchronously
projected timeline bounds include the requested frame, while a subsequent
ordinary user seek cancels that pending request. Focused model, codec, adapter,
grammar, explorer, runtime, promotion-identity, dynamic-timeline, and puppet
source tests pass. The full 1.19.2 Java suite exits 0 after that repair (the
installed-worker integration test is intentionally aborted without its opt-in
JVM properties), and the natural title-screen puppet passes as artifact run
`title_screen_can-20260821-085216-878`. Its screenshots and structured artifacts
prove route/action/glyph comments, exact reopen navigation, replan retention,
explicit exact promotion, no implicit divergent migration, explicit witnessed
migration with evidence, unavailable-frame behavior, persistence reload, and
zero effective approvals. No dependency declaration or checked-in lockfile
changed, no cache acquisition or repository clone was needed, and no CLI source
changed, so the installed `728694ad7` CLI remains current and no user install is
required.

**S1M-X3 completion evidence (2026-08-21):** Added the immutable
`sfm.route-comparison/1` session, pure lockstep/independent cursor kernel,
deterministic line codec, atomic persistence with last-valid recovery, and a
runtime that joins two retained route addresses to authoritative trajectory and
comment data without copying either. The ordinary `sfm:episode/route-comparison`
panel composes two candidate-history panels and reports route identity, frame,
cost, outcome, final predicted hash, comments, and typed `UNDECIDED`,
`PREFERRED`, or `REJECTED` review disposition. Registered actions open explicit
or latest-route pairs, switch mode, seek either/both sides, set disposition, and
perform the separately named explicit trajectory selection; comparison-only
actions never move actual head, document state, instruction pointer, selected
plan, or selected route.

Focused route-comparison tests pass, and the full 1.19.2 Java suite exits 0 with
`1327` found, `1326` passed, `0` failed, and only the installed-worker integration
test intentionally aborted without its opt-in properties. Natural title-screen
puppet run `title_screen_rou-20260821-094513-349` produced six inspected PNG/JSON
checkpoints. They prove initial and scrubbed lockstep, independent positions
`0`/`2`, persisted preferred/rejected disposition, both immutable routes and
both candidate comments retained, zero effective approvals, machine revision
unchanged at `2` throughout comparison/reload, and revision advancing to `3`
only after explicit left-route selection. No dependency declaration or
checked-in lockfile changed, no cache acquisition or repository clone was
needed, and no CLI source changed, so the installed `728694ad7` CLI remains
current and no user install is required.

**S1M-X4 completion evidence (2026-08-21):** Added the versioned complete
Java-local causal archive, strict canonical JSON codec, fresh-runtime restore,
pure exact-replay/semantic-rebase engine, atomic controller publication, and
History Graph causal presentation in core checkpoint `d8b55251e`. Exact replay
records its own typed non-keystroke operation even when it only verifies an
already retained transition chain. Ordinary and constrained command-palette
execution share the canonical registered-action provenance seam, while the
real Ctrl+Alt+J dynamic binding retains its physical key event, modifier set,
effective binding snapshot digest/id/revision, decision, source range, semantic
invocation, two-region witness, outcome, and transition.

Focused temporal-replay, trajectory-controller, choice-session, puppet-source,
and History Graph tests pass. The full 1.19.2 Java suite exits 0 with `1348`
found, `1347` passed, `0` failed, and only the installed-worker integration test
intentionally aborted without its opt-in properties; the canonical 1.19.2
compile also passes. Natural puppet run
`title_screen_tem-20260821-115250-943`, checkpointed in `8a98063e5`, produced
seven inspected PNG stages plus canonical archive, UTF-8 transcript, per-stage
JSON, and aggregate evidence. The artifact parses and restores byte-identically
into a fresh runtime and proves `EXACT_REPLAY:SUCCEEDED` for the retained 1/2
route, `EXACT_REPLAY:PRECONDITION_MISMATCH` with no partial result on the changed
prefix, and `SEMANTIC_REBASE:SUCCEEDED` for the retained 1/2/3 sibling. Ambient
checkout before/after hashes are identical. No dependency declaration or
checked-in lockfile changed, no cache acquisition or repository clone was
needed, and no CLI source changed, so the installed `728694ad7` CLI remains
current and no user install is required. The puppet-owned game and helper
processes exited cleanly.

S1M-X4 through X6 may receive plan-detail amendments at their claim boundary if
implementation evidence reveals a missing seam, but those amendments cannot
rewrite completed core/X1/X2/X3 contracts or broaden dependency/process/repository
authority. If every item finishes before human attention returns, stop at a
clean verified checkpoint and report the exhausted queue rather than inventing
an unplanned X7.

### User journey

1. Open a temporal-numbering chamber from the command palette into an ordinary
   writable Text Editor V3 panel backed by an ignored episode overlay, then open
   `sfm:episode/history` beside it through the normal panel action. The History
   Graph follows new actions live without taking editor focus.
2. Begin with:

   ```text
   - apples
   - bananas
   ```

3. Create/select the chamber's supervised numbered-document target and invoke
   Plan. The History Graph draws the selected projected route from the actual
   head to the target, marks its search frontier and instruction pointer, and
   keeps alternatives visually distinct from committed history. The chosen
   minimum-cost route includes the registered select-all-occurrences action
   (whose default Ctrl+Alt+J binding remains visible in action/keybinding help)
   and `sfm:text/selection/replace/decimal_sequence`, rather than expensive
   per-character literal edits.
4. Invoke Step once, inspect the changed selection and moved instruction
   pointer, then invoke bounded Run to produce `1` and `2` and reach
   `SUPERVISION_READY`. Human approval remains pending.
5. Scrub the timeline from typing/initialization through selection and
   numbering. The History Graph shows raw events, intent, evaluation, two-region
   witness, outcome, state frames, actual head, selected route, and costs.
6. Press Ctrl+Z in the editor. It moves the document head to the state before
   numbering; the numbered `1`/`2` child remains visible and selectable.
7. Insert `- apricots` between the items. This automatically creates another
   child from the earlier state. Inspecting/reusing the old materialized outcome
   still shows the original `1`/`2` branch; frozen-witness re-execution against
   the changed prefix either validates or returns the required stale-precondition
   diagnostic—it never silently targets the wrong glyphs.
8. Replan from the changed head. The old trajectory remains inspectable while
   the new route re-evaluates the semantic intent, resolves three hyphens, and
   produces `1`, `2`, `3` when stepped/run.
9. Invoke redo without a child while the relevant head has multiple children;
   the constrained command-palette chooser exposes both descendants rather than
   selecting or deleting one silently.
10. Switch branches and random-seek without changing any branch. Discarding the
   chamber leaves the real checkout byte-identical.

### Required artifacts and tests

- Shared Rust/Java fixtures for branch ids, clock coordinates, action
  intent/evaluation/outcome, effect class, selection expression/witness, head
  movement, child enumeration, exact replay, semantic rebase, and canonical
  export.
- Pure tests for simultaneous replacement, Unicode/source order, irregular 2D
  geometry, overlap/stale/empty conflicts, parent immutability, undo-undo-do
  branch retention, ambiguous redo, projection cancellation, random seek,
  A*/Dijkstra equivalence, deterministic ties, stale planned steps, budget
  exhaustion, and immutable replanning.
- A puppet that performs the natural registered actions and captures initial,
  planned route/instruction pointer, first stepped action, parent-numbered,
  post-undo retained child, new fork, stale/frozen-witness result, replanned
  child-numbered, ambiguous-redo chooser, branch-switch, and timeline-scrub
  screenshots plus terminal text, episode JSON, supervision contract, search
  evidence, action graph, branch/trajectory trees, final documents, and a
  checkout-unchanged proof.
- The puppet pauses long enough at meaningful states for a human watching the
  run to understand the operation; it does not teleport directly to a fixture
  result while claiming to test user input.
- Completion reports whether the installed `sfm.exe`/
  `sfm-propagate-changes.exe` changed and proves the canonical installed tool is
  current when applicable, so the user never has to rerun an install script to
  begin manual testing.

### Parallel work seams

- **Track A — history/schema:** immutable branch/plan core, exact/rebase
  operations, canonical fixture, and artifact inspection. TE-S1M implements the
  Java-local seam; full TE-S1 later proves cross-runtime interchange.
- **Track B — planner:** deterministic A*/Dijkstra kernel, supervision
  predicates, immutable replan, stale-step validation, and oracle tests against
  the frozen 0.6 interface.
- **Track C — Java/editor:** writable overlay adapter, selection witness/order,
  registered actions, and pure editor tests against the frozen contract.
- **Track D — presentation/puppet:** chamber host, branch/trajectory rendering,
  instruction pointer, facilitator steps, and captures after A/B/C freeze their
  shared DTO seam.
- Integration owns shared schema changes, generated bindings, tool install
  proof, final live run, and bookkeeping. Parallel workers must not independently
  edit the same protocol/schema file.

### Explicit exclusions from TE-S1

- No multi-user network collaboration, branch merges, CRDT, or conflict-free
  concurrent editing.
- No recipe inference/promotion UI, arbitrary source-checkout apply, Java
  compilation, release review, audio/video capture, terminal sharing, or Codex
  integration.
- No active-gameplay overlay host or general layout-preset system. TE-S1 proves
  the frontline History Graph as an ordinary split panel; workspace Track 1c
  later hosts the same view non-modally over gameplay.
- No new dependency is justified merely for this slice; use existing locked
  toolchain inputs unless a separately approved plan amendment proves otherwise.

## Next bounded implementation sequence — TE-S2 ordinary document history and transition canvas

TE-S1M proved the immutable graph, chamber actions, candidate routes, comments,
counterfactual replay, and overlay host. It did **not** make those capabilities
ordinary editor behavior. TE-S2 closes that product gap in small vertical
slices. The decimal-numbering chamber remains a regression fixture and a
specialized action-generator consumer; it ceases to own the only usable
document history.

### Verified starting state — 2026-08-22

- `SFMHistoryGraphContract` already models immutable states, branch edges,
  action intent/evaluation/outcome, heads, and head movements.
- `SFMHistoryGraphPresentationModel` and its projection already provide stable
  renderer-independent node/edge/marker identities, including explicit head-
  movement nodes. `SFMHistoryGraphRuntime` already publishes updates and
  supports late subscribers, but its current `MachineSnapshot` input requires
  trajectory/plan-book state and must be generalized for ordinary histories.
- `SFMDecimalNumberingChamberPanel` is the only production implementation of
  `SFMDocumentHistoryTarget`; its `synchronizeEditorAndController()` copies
  ordinary Text Editor V3 changes into the chamber controller.
- Generic `SFMTextEditorPanel` exposes exact text checkout/snapshot seams but
  does not own an undo history. `SFMDrawCanvasScreen` observes key press,
  release, and character input but retains only a bounded list of diagnostic
  strings; it does not publish typed immutable event/mutation records.
- `SFMDocumentHistoryActions` registers undo only. There is no redo action, and
  the Ctrl+Z default is scoped to the temporal-chamber situation.
- `SFMCommandPaletteScreen` uses a Vanilla `EditBox` without document-history
  ownership. Its Tab insertion calls `SFMClientCommandInsertion.prepare()`,
  which appends a space after a resource-location literal with required
  children and thereby skips the still-valid `sfm:panel/open/right` frontier.
- `SFMClientActionCommandTree` currently injects complete historical commands
  into the action-id replacement range. This is useful for a blank MRU palette
  but makes a nonblank `open` query rank an argument-bearing historical leaf
  against the bare grammar boundary. Brigadier providers already supply
  `focused`, `all`, exact episode/overlay ids, and operation literals; the
  palette does not yet expose Vanilla's `findSuggestionContext()` plus
  `getSmartUsage()` argument-hint behavior.
- `SFMHistoryGraphPanel` is push-fed and non-blocking but renders a nested text
  row list; the same panel can already be hosted by workspace and overlay
  runtimes. `SFMDrawCanvasScreen` proves pan, cursor-anchored zoom, clipping,
  glyph rendering, and canvas coordinate transforms that a shared graph
  viewport may reuse without making text-editor glyph state the graph model.

### [x] TE-S2A Extract the reusable per-document history kernel

**Work:** Introduce one Java-local, UI-independent document-history owner over
the existing history contract. A document revision contains exact content plus
edit-relevant 2D cursor/selection state; camera, panel placement, and transient
hover remain separate observations unless an explicit action targets them.
The owner appends typed raw events, mutation transitions, semantic transactions,
head movements, and immutable child revisions. It exposes snapshot, append,
undo, eligible-redo-children, redo/checkout, and deterministic projection APIs.

Give every session an exact identity independent of its path: two editors that
open the same file do not silently share one undo head unless an explicit
shared-document identity says they do. Store caret/selection as logical Unicode
offsets/ranges and adapt them exactly to the spatial canvas; framebuffer pixels
and current camera transforms are observations, not revision identity. Define
one pre-dispatch ingress authority so consumed Ctrl+Z events are captured once
before binding cancellation, while committed character/IME text remains
authoritative for inserted Unicode. Stable event ids prevent Forge, screen,
widget, and automation hooks from double-recording the same input.

Implement the initial versioned semantic grouping policy from
“Canonical event journal and semantic transaction projections.” Preserve every
raw event and every intermediate state witness even when several character
mutations project as one word transaction. Extract/adapt the chamber controller
to consume this owner; do not maintain a chamber graph and a generic graph in
parallel.

**Validation:** Add pure tests for Unicode/surrogate input, key down/up/repeat,
`open` + space + `the` grouping, timeout and cursor/focus/action boundaries,
paste/completion boundaries, deletion runs, exact raw expansion, before/after
state hashes, undo-undo-do retained siblings, one-child redo, ambiguous redo,
checkout, duplicate-ingress rejection, consumed-shortcut capture, same-path
independent sessions, and projection recomputation under a second grouping
revision. Run the existing temporal archive/replay/trajectory tests to prove
the chamber adapter retained its behavior.

**Completion criteria:** A headless test can feed raw events and mutations into
one ordinary document, obtain the exact three semantic typing transactions for
`open the`, undo and branch without losing the departed child, export every raw
event/state witness, and drive the existing chamber through the same kernel.

**Completion evidence — 2026-08-22:** The UI-independent immutable document
history session records raw input, mutation transitions, grouped semantic
transactions, exact Unicode document state, immutable child revisions, and
head movements. Undo-undo-do retains departed siblings; redo is deterministic
for one child and explicit for several. Archive/projection tests cover raw
witness expansion, grouping boundaries, duplicate ingress, Unicode, checkout,
and retained branches. Foundation checkpoints are `b66e2c597` and
`5b5155cdd`.

### [x] TE-S2B Make Text Editor V3 and command-palette input real history hosts

**Work:** Give every writable Text Editor V3 document session a stable runtime
history identity and the TE-S2A owner. Route editor mutations through a typed
event/mutation sink instead of inferring semantic intent from an occasional
whole-text poll. Programmatic checkout suppresses feedback and restores the
exact revision's edit-relevant cursor/selection state. Read-only editors remain
non-mutating and do not fabricate empty history transitions.

Treat the command palette input as an ephemeral document history host with its
own exact session identity. Typing, deleting, paste, and accepted completions
produce the same raw/mutation/semantic layers. The first slice retains this
history for the palette session; closing the palette may archive it into the
containing episode but reopening a new palette does not silently reuse a live
undo head. Its root state is the actual initial draft (normally
`sfm action invoke `), not an artificial empty string. Wrap every programmatic
`EditBox.setValue` route with an explicit cause—initialization/checkout,
ordinary edit, history recall, suggestion acceptance, required-separator
insertion, automation, or post-execution reset—so checkout suppression cannot
hide a user edit or turn a refresh into another revision.

Replace the chamber-only action seam with selector-explicit commands:

```text
sfm:document/history/undo <document-selector>
sfm:document/history/redo <document-selector> [child-revision]
```

Interactive defaults and keybindings spell `focused`; before recording, focus
is resolved to an exact document/history identity. Ctrl+Z invokes undo in the
generic writable-editor and command-palette situations. Ctrl+Shift+Z invokes
redo. Exactly one eligible child may be followed directly; multiple children
open the existing constrained choice palette, and no eligible child reports a
stable non-mutating result. Target resolution accepts either the current screen
when it is a document-history host or the focused panel inside a workspace;
the command palette must not need to masquerade as a workspace panel. Add its
editable-document keyboard situation so the defaults are contextual rather
than leaked globally. Update action/binding help so both defaults are
discoverable. Saving updates the saved baseline and dirty calculation; it does
not erase revisions or alternate branches.

**Validation:** Focused action, keybinding, editor, draw-canvas, and palette
tests prove ordinary `hi there` undo/redo; `open the` transaction-by-transaction
undo/redo in the palette; sibling retention after undo then typing; constrained
ambiguous-redo choices; selection/caret restoration; read-only refusal; close/
reopen lifetime; exact initial palette-prefix restoration; save-baseline
preservation; screen-versus-panel target resolution; and no recursion during
checkout. Existing chamber Ctrl+Z and archive tests remain green through the
generic path.

**Completion criteria:** Without opening a chamber, a user can type in a blank
Text Editor V3 or the command palette, use Ctrl+Z/Ctrl+Shift+Z naturally, and
inspect retained alternative descendants. The action registry contains both
undo and redo and no screen hard-codes a parallel destructive stack.

**Completion evidence — 2026-08-22:** Writable Text Editor V3 panels and each
command-palette session own exact independent histories. Canonical selector-
explicit undo/redo actions and contextual Ctrl+Z/Ctrl+Shift+Z defaults resolve
the focused document, refuse read-only hosts, preserve alternate descendants,
and present ambiguous redo through human-readable constrained choices. Exact
checkout restores directional Unicode ranges, multiple selections, and the
primary selection. Editor focus gain/loss and physical scan codes are retained
as raw evidence; save baselines remain separate from revision retention.
Focused editor, action-resolution, key-input, choice, draw-canvas, and palette
tests pass.

**Post-completion pathless-buffer hardening — 2026-08-22:** Scratch Text
Editor V3 documents intentionally have no resolver path or source-root
authority. `SFMJavaInteractionMapSession` now recognizes structurally
ineligible document projections before submission, clears any prior
publication, and records only a debug-level `SKIPPED` reason instead of
launching the symbol worker and warning once per edit revision. Superseded
asynchronous failures are checked against the current epoch and retained only
as debug-level stale evidence. `SFMJavaInteractionMapSessionTests` proves
repeated pathless revisions submit zero lookups and create no cancellation
churn. The exact natural-history puppet passed again in run
`sfm-title_screen-20260822-232951-899`; its captured console contains no
`document_path_absent`/interaction-map failure and closes the symbol worker
with `launches=0`. The full suite reported `1445 found, 1444 passed, 0 failed,
1 aborted`, with only the existing opt-in installed-worker test aborted.
Implementation is checkpointed in `05819991d`.

### [x] TE-S2C Join contextual-input B-0's completion-frontier repair

**Work:** Complete the authoritative contextual-input B-0 slice. The temporal
join adds no second ranker. It only proves that palette input history from
TE-S2B survives candidate application and that completion acceptance is a
semantic transaction with its exact Brigadier range and before/after state.

**Validation:** The joined tests and puppet must cover all of these exact
frontiers:

```text
open
sfm:panel/open
sfm:panel/open␠
sfm:panel/open/right
sfm:episode/trajectory/plan␠
sfm:overlay/visibility/set␠
sfm:overlay/visibility/set id(sfm%3Ahistory)␠
```

Here `␠` denotes one deliberate trailing U+0020 space at the argument
frontier; it is notation for the test vector, not a character entered into the
command.

They prove bare-boundary ranking for a nonblank query, deliberate-space versus
Tab continuation, compatible scene-argument history across the open family,
`focused`/exact overlay selectors and `visible|hidden`, required argument names
when no value completion exists, and undo/redo around suggestion acceptance.

**Completion criteria:** The user can reach the intended `panel/open` family by
Tab/Tab or choose Space to enter its scene argument; complete historical leaves
remain available but do not steal the first nonblank grammar boundary.

**Completion evidence — 2026-08-22:** Contextual-input B-0 is complete and the
palette records candidate acceptance as one semantic transaction while
retaining raw Tab/Space evidence. The natural puppet proves blank MRU,
nonblank `open`, exact `sfm:panel/open`, the deterministic strict descendant
`sfm:panel/open/left`, deliberate `sfm:panel/open `, trajectory and overlay
selectors, overlay visibility, named usage, and undo/redo around completion.
Its exact candidate order, kinds, replacement ranges, history scopes, and
input values are exported in `artifact_palette-*.json`.

### [x] TE-S2D Add the approachable 2D experienced-history canvas

**Work:** Add a reusable node/edge canvas presentation over the TE-S2A
projections and register an ordinary scene such as:

```text
sfm action invoke sfm:panel/open/right sfm:document/history focused
```

The exact scene spelling is frozen with its screen-type registration before
implementation; do not add a top-level opening alias. The default view is the
append-only experienced narrative, not the trajectory search debug tree. Draw
blue action/head-movement circles, amber resulting-state circles, directed
edges, and readable labels in paired lanes. Undo appends its own node and uses a
visually distinct curved jump edge to the state that became current. Selected
nodes expose before/after content, branch/revision identity, semantic label, and
expandable raw-event witnesses.

Derive canvas nodes from the existing renderer-independent
`SFMHistoryGraphPresentationModel`/projection identities rather than creating a
second graph authority. Generalize the runtime publication input as an explicit
ordinary-document versus trajectory-machine sum/variant; never satisfy the
current trajectory-first `MachineSnapshot` shape with a fake empty plan book or
irrelevant Plan/Replan controls. Keep the existing row-list projection as an
accessible chronological transcript/fallback while the canvas becomes the
frontline visual presentation. Stable node ids preserve hover, selection, and
details across push-fed graph revisions.

Reuse existing panel/canvas pan, zoom, clipping, focus, and cursor affordances
through a small shared viewport primitive; do not make graph nodes into fake
text glyphs or put graph topology in `SFMDrawCanvasModel`. Top-down is default.
A registered transpose action switches to left-right without changing
selection or graph identity. A selected-document content preview may be
textual in the first slice; no screenshot-only interpretation is acceptable.
Chronological narrative/state-parent edges determine layout rank. Curved
undo/redo/checkout jumps are non-ranking overlay edges, so their graph cycles
cannot destabilize or recursively expand the layout. The same canvas panel must
remain hostable in both workspace and overlay runtimes without changing the
overlay's passive/interactive focus rules.

**Validation:** Pure layout/hit-test tests cover deterministic coordinates,
paired action/state lanes, long labels, clipping, pan/zoom, transpose,
selection retained across pushed revisions, curved undo jumps, non-ranking
jump cycles, sibling retention, and an empty/single-node history. A normal
title-screen puppet opens a blank editor and history panel,
types `hello`, space, `world!`, undoes, types `new content`, and captures both
PNG and structured graph/document artifacts. It proves the timeline remains
chronological, the jump points to the earlier state, `hello world!` remains a
reachable descendant, and the canvas updates live without taking editor focus.
Artifacts include `document-history.json`, `history-canvas-layout.json`, exact
current/retained document text, screenshots for each milestone/orientation,
and an accessible chronological transcript. Graph ids and canvas rectangles/
edge paths must agree across the machine-readable and raster evidence.

**Completion criteria:** A user unfamiliar with the chamber can watch ordinary
editing appear as the two supplied Excalidraw concepts: an action/state paired
sequence and an append-only narrative with an undo jump. Keyboard/mouse can
select, pan, zoom, inspect, and transpose the graph.

**Completion evidence — 2026-08-22:** The push-fed
`SFMDocumentHistoryPanel` renders blue action/head-movement nodes, amber state
nodes, chronological edges, and curved checkout/undo jumps from the ordinary
document projection rather than a fake trajectory machine. It is hostable in
workspace and overlay runtimes. Users can select by keyboard or pointer,
expand raw evidence, wheel-zoom, middle-drag pan, frame the current subject
with `R`, fit all with Home, transpose with `T`, and open the chronological
transcript with `V`. Geometry, framing, transpose, hit-testing, pushed revision
selection, jump cycles, and resize behavior have pure tests.

The natural title-screen puppet
`sfm:title_screen_ordinary_document_history` passed at `1280x720@auto` in
run `sfm-title_screen-20260822-215127-171`. It visibly types
`hello world!`, undoes to the root, creates retained sibling `new content!`,
shows two labelled redo choices, restores the departed `hello world!` branch,
proves an immediate live push, then captures readable top-down, transposed, and
chronological-transcript views. The run contains 15 PNG figures plus exact
`artifact_document-history.json`, `artifact_history-canvas-layout.json`,
interaction/projection JSON, transcript, current text, and both retained child
texts. No temporal-numbering chamber was opened.

### [ ] TE-S2E Plan between two ordinary document revisions

**Work:** Register a selector-explicit pairwise operation, provisionally:

```text
sfm:document/trajectory/plan <source-document-selector> <desired-document-selector>
```

Both selectors resolve to exact immutable revisions before planning. A normal
workspace with two editors can offer a contextual draft that fills those
selectors; the planner itself never consults later focus or assumes screen-left
means source. The desired document supplies the target predicate. The existing
bounded planner and finite action-generator contract produce immutable route
revisions and an address that the ordinary candidate-history canvas can open.
The temporal-numbering chamber calls the same operation with fixture documents.

Begin with a deliberately finite edit catalog (typed insertion/deletion/
replacement plus already registered semantic edits) and report its cost policy
and incompleteness. Planning is cancellable/off-render-thread and never writes
either source document. Dijkstra/exhaustive-oracle checks bound any shortest-
route claim.

**Validation:** A puppet opens source and desired Text Editor V3 panels, edits
the desired side, invokes the pairwise action through the command palette,
opens the returned candidate graph beside them, scrubs at least two routes,
and proves both input heads are unchanged. Machine artifacts contain exact
input revisions, action-generator/cost revisions, candidate states, route
costs, and cancellation/budget outcomes.

**Completion criteria:** “Plan how the left document can become the right
document” is an ordinary in-game workflow using explicit captured documents,
not a chamber-only button or an implicit mutable-focus guess.

### [ ] TE-S2F Scale graph presentation without changing graph semantics

**Work:** Add a layout-provider registry over immutable graph input. Ship the
deterministic layered top-down and left-right providers first. Add level-of-
detail rules that retain labels for hovered, selected, actual-head,
instruction-pointer, target, and selected-route nodes while allowing dense
unselected nodes to become compact marks. Preserve stable selection through a
layout change and expose layout choice through registered actions/intellisense.
Cache layout by graph/projection/layout revision, cull drawing to the viewport,
and use a bounded spatial hit index so pointer interaction does not scan every
node on every frame. Record layout, culling, hit-test, and render costs
separately before choosing tighter data structures.

Research a spectral-embedding provider against the user's Stephen's Sausage
Roll state-space reference only after the deterministic providers and density
tests pass. If it needs a new dependency, that is a separately approved goal/
lockfile change; TE-S2A through TE-S2E may not acquire one. A spectral layout
is a view, never a planning heuristic or proof of semantic proximity.

**Validation:** Deterministic snapshot tests cover sparse, branching, cyclic-
presentation (through head-movement jump edges), disconnected, and 25+/1000+
node fixtures; labels-on-hover and selected-route visibility remain usable at
high density. Performance evidence records layout and render time separately.

**Completion criteria:** Users can transpose ordinary graphs and inspect dense
candidate spaces without a wall of overlapping labels; adding another layout
does not alter canonical event/state bytes.

### [ ] TE-S2G Compose higher-order histories from document primitives

**Work:** After TE-S2A through TE-S2E are proven, define typed documents for
selection sets, multi-document action manifests, workspace-layout state, and
external observation streams. Their actions may reference exact child
document/selection revisions. Pure/snapshot-restorable compositions can
participate in undo/planning; irreversible process/network/world effects remain
typed barriers. Do not introduce one ambient global Ctrl+Z domain.

**Validation:** A bounded two-document selection action records one higher-
order transition with links to both child revisions, undoes without losing
either branch, and exports/imports deterministically. A barrier fixture proves
that an external action remains inspectable but is never optimistically undone.

**Completion criteria:** Multi-document behavior composes from the same single
and pairwise contracts and no second global history engine appears.

### Completed first TE-S2 goal boundary — 2026-08-22

The completed implementation goal was:

> Complete contextual-input B-0 and TE-S2A through TE-S2D in
> `docs/tasks/contextual input actions and addressable explorer plan.md` and
> `docs/tasks/snapshot episodes and deterministic action environments plan.md`.

This boundary fixed the user's immediate palette and undo friction **and** ended
with a natural visible feature rather than only a kernel. It deliberately
stopped before pairwise search; TE-S2E remains the next unclaimed slice.

Observable acceptance for that goal:

1. Typing `open` ranks bare `sfm:panel/open` first despite a recent complete
   chamber command. Tab accepts the boundary without a space; another Tab
   continues to the deterministic strict descendant
   `sfm:panel/open/left` in the current registration order, while Space
   deliberately enters the scene argument and scene history is shared across
   compatible open actions.
2. `focused`, exact overlay selectors such as `id(sfm%3Ahistory)`, and
   `visible|hidden` are suggested at their actual argument frontiers. When no
   value completion exists, the palette visibly names the expected Brigadier
   argument(s) rather than only disabling Execute.
3. Ctrl+Z and Ctrl+Shift+Z work in a generic writable Text Editor V3 and in the
   palette input. `open the` undoes/redoes as `the`, space, and `open`; every
   raw key event and intermediate state remains inspectable.
4. Undo then typing creates a retained sibling. Ambiguous redo opens the normal
   constrained palette; neither path is deleted.
5. An ordinary split panel displays live circles/arrows with paired action and
   state labels. Undo is a chronological node plus a jump edge; transpose,
   pan, zoom, hover/select, and structured before/after details work.
6. A title-screen puppet demonstrates this exact flow with human-readable
   pauses and emits screenshots, current document text, raw events, semantic
   transactions, immutable state/edge/head-movement data, and layout/hit-test
   evidence. No decimal-numbering chamber is opened.

### First-goal parallel topology, gates, and exclusions

- **Track A — palette frontier (contextual B-0):** candidate kinds, history
  scopes/ranking, insertion policy, dynamic completions, and smart usage. It
  owns `SFMClientActionCommandTree`, `SFMClientCommandInsertion`, and palette
  candidate/usage tests.
- **Track B — history kernel (TE-S2A):** immutable document states, raw journal,
  semantic grouper, branch/head operations, and chamber adapter. It freezes the
  DTO/event-sink contract before Tracks C/D integrate.
- **Track C — editor/palette hosts (TE-S2B):** Text Editor V3 and `EditBox`
  adapters, focused document repository/selectors, actions/default bindings,
  and natural undo/redo tests. It begins against Track B's frozen interfaces.
- **Track D — canvas (TE-S2D):** pure projection/layout/hit-test model can begin
  against checked-in fixtures while Track B runs; runtime wiring waits for the
  frozen projection DTO.
- **Integration:** joins candidate acceptance to semantic history, adapts the
  chamber, runs the natural puppet, inspects PNG/structured artifacts, updates
  user documentation/changelog for gameplay-visible changes, and records tool
  installation freshness under the goal-readiness guide.

No new Cargo/SFM/Gradle dependency or lockfile change, repository clone,
cross-version propagation, release/publish action, generalized spectral
embedding, pairwise planner, multi-document undo, persistence-across-client-
restart promise, or ambient-source mutation belongs to this first goal. Use
only existing locked/cached sources and the current Java implementation. If a
new dependency appears desirable, finish at the last passing checkpoint and
amend the later TE-S2F goal instead.

Suggested focused validation commands (exact filters may be split as classes
land):

```pwsh
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMClientActionPaletteSuggestionTests --wait-for-build-lock
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMClientCommandInsertionTests --wait-for-build-lock
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMDocumentHistory --wait-for-build-lock
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMTextEditorPanelTests --wait-for-build-lock
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMCommandPaletteScreenTests --wait-for-build-lock
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMHistoryCanvas --wait-for-build-lock
sfm-propagate-changes.exe run compile --branch 1.19.2 --wait-for-build-lock
sfm-propagate-changes.exe puppet run sfm:title_screen_ordinary_document_history --branch 1.19.2 --variant 1280x720@auto --wait-for-build-lock
```

### TE-S2 first-goal operational readiness

- Target branch/commit: `1.19.2`, from planning checkpoint `ba212d857` through
  implementation checkpoints `b66e2c597`, `5b5155cdd`, and `4bf109948`, then
  final hardening and completion evidence in `bfd744c1c`.
- Tooling or generated runtime inputs changed: no. Goal changes are confined
  to Java, tests, gameplay resources, and docs.
- Installer/build command run: canonical `run compile`, full `test run`, and
  the natural puppet all passed. `install.ps1` was neither needed nor run.
- Installed executable/path and revision/hash:
  `G:\Programming\Caches\CARGO_HOME\bin\sfm-propagate-changes.exe`, revision
  `728694ad7`, SHA-256
  `931766D833441446EEC7BFE1D1BF52C71BDD459D40270269FEF1BF77A9EED857`;
  this matches the latest commit touching the CLI source tree.
- User must run install script: **no**. No CLI source or generated CLI input
  changed, and the installed executable remains the current accepted build.
- Dependency posture: frozen under the repository goal-readiness guide.
- Dependency declarations/lockfiles changed: no; this is an invariant of the
  goal.
- Cache rehydration performed: canonical build/test/puppet commands reused
  existing lockfile-pinned toolchain caches; no dependency acquisition or
  lockfile mutation was requested.
- New developer/reference repositories cloned: none; prohibited by the goal.
- Process lifecycle authority: active via the goal-readiness guide.
- Process preflight: no Java/Minecraft, Cargo/Rust, SFM CLI, `sfm.exe`, or
  Teamy Terminal process was running. A VS Code process had this plan's user
  guide open and is explicitly unrelated/not in scope.
- Final in-scope process/runtime state: stopped; the puppet, test, and compile
  processes exited normally and no game was left running.
- Cache/lock verification: no initial in-scope process held a known build
  cache; every long CLI invocation must still use bounded waits/log evidence.
- Exact manual test command:
  `sfm-propagate-changes.exe puppet run sfm:title_screen_ordinary_document_history --branch 1.19.2 --variant 1280x720@auto --wait-for-build-lock`.
- Final artifact run:
  `sfm-title_screen-20260822-215127-171/title_screen_ordinary_document_history/1280x720_auto`.
  It contains 15 screenshots and exact palette, raw-input, semantic-
  transaction, revision/edge/head-movement, canvas-layout/hit-test,
  transcript, current-text, and retained-branch artifacts.
- Known manual-only or external limitation: none for the claimed acceptance.
  The one full-suite abort is the existing opt-in installed symbol-worker
  integration test, which requires explicit executable/branch properties and
  is outside this goal.
- Autonomous continuation ladder and currently claimed item: the required core
  B-0 plus TE-S2A through TE-S2D is complete. TE-S2E through TE-S2G remain
  unclaimed future goals and are not implicit stretch authority.

## Overall acceptance criteria

- [ ] A single canonical JSON file losslessly represents a complete snapshot.
- [ ] A single canonical JSON file represents an episode and rehydrates every
  frame, including `t=0` and the final-frame alias.
- [ ] Rust and Java round-trip the same fixture corpus.
- [ ] Raw event provenance distinguishes traces with equal action counts.
- [ ] A stateful keybinding engine replays deterministically from recorded
  events and binding revision.
- [ ] Immutable branch history distinguishes exact replay from semantic rebase;
  historical insertion never changes an existing revision.
- [x] Undo followed by new work retains every prior child; redo enumerates an
  explicit child and ambiguous redo opens a constrained choice.
- [ ] Intent, evaluation/witness, outcome, and resulting state remain separately
  inspectable; checkout, frozen-witness execution, and intent re-evaluation are
  never presented as the same operation.
- [ ] Actual history head, selected trajectory, projection frontier, and
  instruction pointer are independently represented and visibly distinguish
  committed transitions from proposed futures.
- [ ] A bounded deterministic planner reports a reproducible minimum-cost route
  only within its declared finite generator/cost policy, agrees with a Dijkstra
  or exhaustive oracle, and preserves prior route revisions during replan.
- [ ] A supervision contract separates goal predicates, hard invariants,
  evidence, effect authority, `SUPERVISION_READY`, and human approval.
- [ ] Candidate trajectories are seekable without executing actions or moving
  actual head/instruction pointer, and unavailable frames remain explicit.
- [ ] Candidate route/action/state/region comments pin immutable plan revisions;
  replan preserves them and exact execution requires explicit promotion while
  divergent results transfer neither targets nor approval.
- [ ] Two retained candidate routes can be compared in lockstep or independently
  with typed review disposition while their comments, actual head, instruction
  pointer, and selection remain unchanged until a separately explicit route
  selection action.
- [ ] A long autonomous run completes/checkpoints its core before claiming one
  ordered stretch item at a time and never uses elapsed estimates as acceptance
  evidence or permission for invented scope.
- [ ] A revision-qualified selection action retains its expression, evaluator,
  ordering policy, and exact ordered witness.
- [ ] The temporal decimal-numbering chamber visibly proves two-item exact
  history and three-item semantically rebased history in-game while preserving
  the real checkout.
- [ ] The in-game Episode Inspector visibly scrubs files, events, actions, and
  environment state.
- [ ] The live History Graph panel receives actions as they occur, shows the
  current head and non-linear descendants, and never blocks the render thread
  while projecting a branch.
- [ ] The reusable timeline hosts an arbitrary seekable inner panel, provides
  deterministic MPV-like horizontal transport, and supports random-access
  seeking without playback-history dependence.
- [ ] The falsified chest replay visibly moves one cobblestone from chest to
  cursor to player inventory while never opening or mutating a live menu.
- [ ] The generic multiplexer layer records, exports, imports, and replays an
  ordinary calculator panel; recording and agent ownership are not calculator
  responsibilities, and hard method constraints remain separate from
  efficiency.
- [ ] Agent ownership gates mutation input only for the leased panel, uses
  virtual panel-local input, visibly identifies the owner, and always permits
  explicit human emergency revoke/recovery.
- [ ] Refactoring playgrounds never mutate tracked fixtures or escape their
  ignored bounded roots.
- [ ] Runtime logs, screenshots, agent drafts, and other observations remain
  correlated but non-authoritative; transitions always identify their action.
- [ ] Optional Codex integration inherits supported local authentication and
  bounded capabilities and is not required for ordinary SFM use or replay.
- [ ] Virtual-shell approvals operate on resolved AST/capabilities, not regex.
- [ ] Every optimized or amalgamated representation exports the canonical dumb
  form and declares any non-reversible quotient plus required witnesses.
