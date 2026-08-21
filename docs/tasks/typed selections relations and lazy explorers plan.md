# Typed selections, relations, and lazy explorers plan

**Plan status:** Active; X-1 through X-7 and X-8a through X-8c are complete;
X-3a, picker X-8, and X-9 through X-11 remain
**Primary implementation root:** `D:\Repos\Minecraft\SFM\repos2\1.19.2`
**Primary implementation target:** Minecraft 1.19.2
**Related control plan:** `docs/tasks/sfm in-game control cli plan.md`
**Related UI plan:** `docs/tasks/contextual input actions and addressable explorer plan.md`
**Related comment plan:** `docs/tasks/global comment selection and review sessions plan.md`
**Related editor plan:** `docs/tasks/draw editor document regions and commands plan.md`
**Last updated:** 2026-08-21
**Intent audit:** Passed and post-compaction re-audited 2026-08-21 against the complete 2026-08-12 through 2026-08-16
CLI/explorer/path/selection/relation/picker/layout design discussion, the latest
projection/icon/filter/focus/scroll observations, and the linked plans' existing
ledgers, plus the explicit non-destructive undo-tree follow-up; implementation
closure audit remains valid for completed X-1 through X-7/X-8a/X-8b code,
tests, protocol, and live artifacts while X-3a supersedes the linear redo-head
limitation without marking X-3 incomplete

## How to update this plan

- `[ ]` Not started
- `[~]` In progress
- `[x]` Complete
- `[!]` Blocked

Keep at most one integration slice in progress unless the parallel work map
explicitly assigns disjoint ownership. Update a work-item heading and its
completion notes together. Record exact commands, test outcomes, artifact ids,
public grammar changes, schema fingerprints, commits, and intentional follow-up
limits under the item they prove. Work on 1.19.2 first. Do not invoke Gradle
directly, propagate, publish, or push merely because a phase is complete.

## Purpose

Build one reusable, action-addressable substrate for:

- lazy heterogeneous explorers containing filesystem, registry, selection,
  comment, review, or future domain roots;
- explicit set-valued targeting of running games, panes, panel entries,
  explorer components, selections, and content;
- versioned named selections with set algebra and undo-capable history;
- lazy parent/child relations that refresh without blanking useful old data;
- picker destinations whose accepted values survive unrelated explorer
  navigation;
- future Text Editor v3 range selections and global comment selectors; and
- direct, typed `sfm.exe` game-control commands that do not hide ordinary
  operations beneath a generic `invoke` subcommand.

The first observable journey is intentionally concrete:

```powershell
cd D:\Repos\Minecraft\SFM\repos2\1.19.2
sfm explorer root add focused . --if-no-match open-new
```

The selected game opens a generic explorer if necessary and shows the
canonical repository path as a lazy root. Adding the item-registry root to that
same explorer changes it from a hoisted single-root presentation to a visible
two-root presentation without changing explorer type. Expanding a directory
loads only its immediate children off the render thread. A refresh retains the
published children until a complete replacement page is ready, then swaps the
relation atomically.

This plan supersedes the proposed global persisted
`SFMExplorerWorkspace`/`sfm workspace add .` abstraction. It does not remove the
existing use of “workspace” for the complete split/stack panel host.

## Spatial semantic region relationship — 2026-08-17

`docs/tasks/spatial semantic surfaces outlinks and capability presenters plan.md`
owns typed canvas/document/syntax regions, outlink relations, spatial coverage,
and navigation projections. This plan continues to own paths, path
expressions, entity selectors, selection revisions/set algebra, lazy explorer
membership, pane/entry/component identity, and picker destinations.

The join remains explicit: X-9 adapts semantic/spatial regions into the shared
selection ledger without enumerating pixels or replacing pinned comment rules;
X-10 freezes pane terminology before spatial pane selectors and branching
navigation history become public. Semantic Java children in spatial-plan NX-3
reuse this plan's lazy relation/revision/publication rules.

## Authoritative user guidance ledger

### CLI and action grammar

| ID | Active guidance | Required plan consequence | Superseded by |
| --- | --- | --- | --- |
| XCLI-1 | Canonical `sfm.exe` commands should be explicit, long-form, and hierarchically named. Short conveniences like `code -a .` are useful, but SFM should leave aliases to the user's shell profile. | Phase X-6 adds `sfm explorer ...` commands and asserts no built-in short aliases. Help and `ToArgs` expose canonical commands. | — |
| XCLI-2 | Aside from discovery such as `sfm instance list`, most `sfm.exe` commands act on running games; ordinary typed operations should not be guarded by `sfm invoke`. | `sfm invoke` remains a generic registered-action diagnostic/escape hatch. Explorer operations get direct typed commands and Vox methods. | — |
| XCLI-3 | Every explorer action must contain an explorer selector; it must not depend on ambient focus. `focused` is itself an explicit selector. | Every registered action and external request carries a typed selector expression. Widgets emit exact ids; keyboard/CLI calls may spell `focused`. | — |
| XCLI-4 | Selectors are set-valued in the Alloy sense, and actions should gracefully handle zero, one, or many matched entities. | Phase X-2 implements deterministic set-valued resolution; X-5 preflights/stages multi-target mutations and returns typed per-target outcomes. | — |
| XCLI-5 | If an exact `--explorer-id` was supplied and is missing/incompatible, fail without fallback. If no exact id was supplied and no compatible explorer exists, opening a new explorer is desirable. | The canonical first slice makes empty-target policy explicit as `--if-no-match fail|open-new`; `id(...)` plus `open-new` is rejected. No selector evaluation itself creates UI. | — |
| XCLI-6 | Game instances, explorer components, panes, stacked content, selections, and content all need independently addressable/queryable identities. | X-1/X-2 define distinct ids and selector domains; no `explorer://<id>` content-address shortcut is introduced. | — |
| XCLI-7 | Task ids are useful, but explanations to the user must include the short meaning of each id. | This plan keeps stable ids and descriptive headings; handoffs and goal proposals include both. | — |

### Paths, expressions, and identity

| ID | Active guidance | Required plan consequence | Superseded by |
| --- | --- | --- | --- |
| XPATH-1 | Minecraft `ResourceLocation` cannot represent every valid Windows path and must not be coerced into serving as a universal path type. | X-1 defines a separate typed `SFMPath` sum with resolver scheme and canonical UTF-8 spelling. Registry adapters may retain `ResourceLocation` internally. | — |
| XPATH-2 | For SFM's interoperable surfaces, a path must have a valid UTF-8 representation; attempts to introduce non-UTF-8 paths should fail gracefully. | Rust rejects non-Unicode `OsStr`; Java rejects malformed/unpaired input; typed diagnostics identify the offending boundary without lossy replacement. | — |
| XPATH-3 | Files, registries, selections, and later domain objects need resolver-specific paths such as `file:///C:/tmp`, `registry://minecraft/item/`, and `selection://a`. | X-1 freezes typed variants, canonical parse/print, escaping, equality, and resolver dispatch fixtures. | — |
| XPATH-4 | A selector that finds selection objects is not the same as an expression that produces their member paths. Commands consuming content should accept a path expression whose result is a set of paths. | X-1 defines `Selector<Selection>` separately from `PathExpression`; `members(selection(...))` is the explicit dereference operation. | — |
| XPATH-5 | Inline `a|b` multi-root spelling is undesirable because of shell pipes, OS differences, escaping, and nesting. | Multi-root locations use selections/path-expression algebra. No pipe-concatenated canonical encoding is accepted. | — |
| XPATH-6 | A persistent named collection should not leak an implementation type such as `object://List<String>/...`. | Named/versioned selections provide stable semantic identity. Their resolver exposes members without embedding Java/Rust type names. | — |
| XPATH-7 | Content identity, transient UI identity, and a query/selector that may match several entities must remain distinct. | X-1/X-2 use concrete path, path expression, entity id, and selector types with no first-match coercions. | — |

### Selections and selection destinations

| ID | Active guidance | Required plan consequence | Superseded by |
| --- | --- | --- | --- |
| XSEL-1 | A selection is a set of paths, identified by stable id and optionally by name. Multiple selections must coexist. | X-3 implements session-scoped selection entities with immutable member sets and id/name selectors. | — |
| XSEL-2 | Users need add, remove, union, intersection, and difference operations and can derive a named result from other selections. | X-3 adds typed action/CLI-ready operations and pure algebra tests. | — |
| XSEL-3 | Selection history should be versioned so Ctrl+Z/undo/pop can be implemented without destructive state loss. | X-3 stores immutable revisions, parent links, operation provenance, actor/request ids, and movable heads. Undo/redo append or move history explicitly rather than erasing revisions. | — |
| XSEL-4 | `selection://a` should be explorable; it identifies selection `a`, whose children are its current member paths. Reproducible consumers need a pinned revision form. | The selection resolver supports live `selection://a` and immutable `selection://a@<revision>`; comments/review artifacts use pinned meaning where reproducibility matters. | — |
| XSEL-5 | Explorer navigation focus/selection must not clobber items already being picked, especially when navigating folders. | X-4 separates navigation cursor, expansion, focus highlight, and semantic named selections. | — |
| XSEL-6 | “Pick three icons” is a destination/drop-zone with a selection, acceptance/cardinality rules, discard controls, and confirm/cancel—not a special tree implementation. | X-8 models selection destinations independently from explorers. Any compatible explorer can feed a destination. | — |
| XSEL-7 | Selection confirmation must remain valid while the user navigates hierarchies and may draw values from multiple explorers. | Destination state owns the selection and validation; explorer navigation never owns or resets it. | — |
| XSEL-8 | Text Editor v3's multiple cursors are trying to cover part of this selection problem, but cursor order, primary identity, anchor/head direction, and insertion behavior still matter. | X-9 projects cursor ranges into shared document-region selections while retaining the editor's ordered operational cursor model. | — |
| XSEL-9 | The global comment/review system already needs selectors spanning regions and documents; it should reuse the shared algebra rather than grow another incompatible selection engine. | X-9 adds an adapter between shared selection expressions and review-session rules while preserving pinned snapshot/hash semantics. | — |
| XSEL-10 | Different named selections should be independently visible/manipulable and able to contribute overlays. | X-4 reserves overlay/style contributions keyed by selection id; the first slice proves independent membership, while editor/comment styling remains later. | — |
| XSEL-11 | Undo-undo-do must not clobber access to the old redo descendant or any alternative/projected history. | X-3a replaces the head's single linear undo/redo stacks with parent/child adjacency plus movable/named heads. Undo follows a parent; redo selects a child; new mutation appends a sibling and preserves all existing revisions. | — |

### Lazy relations and refresh

| ID | Active guidance | Required plan consequence | Superseded by |
| --- | --- | --- | --- |
| XREL-1 | Opening a broad root such as `C:\` must not eagerly traverse descendants or freeze/choke the application. | X-4 replaces recursive snapshots with bounded, cancellable immediate-child pages and virtualized visible rows. | — |
| XREL-2 | Parent/child hierarchy is a relation of `(parent, child)` tuples, not recursively nested entry ownership. | X-3 defines a versioned child relation store; hierarchy grouping projects that relation. | — |
| XREL-3 | Expanding/collapsing a node is a visual action, while rebuilding/refreshing children is a data action. First expansion may request refresh automatically. | X-5 registers distinct expand/collapse/toggle and refresh actions. Expand changes view state immediately and schedules refresh only when no current materialization exists. | — |
| XREL-4 | Refresh must fetch before replacing so useful old children remain visible; never clear first and leave an avoidable blank state. | X-3 stages a candidate relation page and atomically replaces rows for the requested parent only after success. Failure retains the prior revision and adds a visible diagnostic. | — |
| XREL-5 | Refreshing several parents should remove only their old child rows and union the newly fetched rows in one publication. | X-3 defines `new = old - rows(parent in P) + fetched(P)` with one committed relation revision. | — |
| XREL-6 | Slow/obsolete child requests need cancellation and generation checks; a late response must not overwrite newer data. | X-3/X-4 add request ids, resolver generations/fingerprints, cancellation, and stale-publication rejection. | — |
| XREL-7 | Very large sibling sets require paging, continuation, bounded caches, and visible incomplete/error state rather than exhaustive materialization. | Resolver pages carry continuation, completeness, diagnostics, and generation. A refresh keeps the old generation until the replacement first page is ready, then atomically publishes the new known prefix and continuation. | — |
| XREL-8 | The proposed `rebuild_children file:///C:/tmp selection://a` example captures a useful operation, but a flat selection alone loses parent ownership. | The authoritative operation rebuilds the child relation; selections of children are derived from relation domain/range afterward. The example and rationale remain fixtures/documentation. | — |

### Explorer composition and presentation

| ID | Active guidance | Required plan consequence | Superseded by |
| --- | --- | --- | --- |
| XEXP-1 | “Explorer” is a generic hierarchical/tree presentation, already associated with ItemStack icons and chat-component formatting, not merely a filesystem browser. | X-4 introduces generic entry presentation/resolver contributions and removes file-only nested entry assumptions. | — |
| XEXP-2 | An explorer may contain heterogeneous roots; adding a filesystem path to an item-registry explorer is compatible. | X-4 proves registry and file roots in one explorer session. Destination policies, not explorer type, decide whether a selected subject can be submitted. | — |
| XEXP-3 | With one root, VS Code-style presentation should normally hoist it and show its children; with several roots, show each parent root. | X-4 adds idempotent `root/hoist/set <selector> auto|show-roots`; `auto` hoists exactly one root and exposes two or more roots. | — |
| XEXP-4 | View, Sort By, and Group By are projection choices separate from underlying membership. Windows Explorer is useful inspiration; desired sort candidates include name, extension, and icon. | X-4 stores projection independently: view, sort, group, hoist, filters, and manual root order, and proves contributed name/extension/icon sort ids without baking sort into membership. | — |
| XEXP-5 | The current tree is approximately View=List plus Group By=Path Hierarchy; the item picker resembles View=Small Icons. Hierarchy may be a grouping projection over paths/relations. | X-4 supplies those initial projection ids without making either a separate explorer implementation. | — |
| XEXP-6 | UI elements should map to registered actions. Clicking a chevron must emit a self-contained exact explorer/path action. | X-5 routes widget, keyboard, palette, drop, and remote behavior through semantic actions with explicit selectors. | — |
| XEXP-7 | `add_children_from` is implementation language; expand and refresh are separate semantic intents. | Canonical action ids use `node/expand|collapse|toggle|refresh`. | — |
| XEXP-8 | Each explorer has its own location, projection, navigation/scroll/expansion state, and stable session id. Duplicate explorers initially copy an immutable recipe/snapshot and then diverge. | X-4 defines `ExplorerSession`; mutable component state is not globally shared merely because roots are equal. | — |
| XEXP-9 | An explorer location describes content, not the explorer's transient identity. `explorer://1/` is therefore not the location of an item explorer. | Locations are path expressions such as registry/file/selection expressions; explorer ids remain selector targets. | — |
| XEXP-10 | Adding a second root to a direct single-root location should transition to a selection-backed multi-root location without a pipe encoding or an unnecessary global workspace. | X-4 creates an ephemeral versioned location selection containing old and new roots, then points the explorer at that selection. | — |
| XEXP-11 | Root membership and manual root order are distinct from sort/group presentation. | Semantic location membership is a set; optional manual-order metadata is a projection relation with deterministic fallback. | — |
| XEXP-12 | Root add/remove/move should be shared semantic actions usable by UI, drop, command palette, and external CLI. | X-5 and X-6 converge on one game-thread executor and typed outcome model. | — |
| XEXP-13 | Exact explorer targeting, focused targeting, and multi-explorer targeting must all be possible. | X-2 supports exact/focused/all/composed selectors; X-7 live proof exercises focused, exact-missing, and all. | — |
| XEXP-14 | If no compatible explorer matches a non-exact request, opening a generic explorer is acceptable; explicit missing ids must never create a replacement. | X-5 implements explicit empty-target policy and safe panel-open composition. | — |
| XEXP-15 | The persistent `SFM Explorer` title and textual `List` view toggle consume scarce panel-header space while restating identity and a view mode that is already visually apparent. | X-8a replaces both with one full-width location control. View/sort/group remain registered actions discoverable from the palette/context surface; no persistent prose label is required. | — |
| XEXP-16 | The explorer header should display the explorer's semantic location, and clicking it should open that location in the configured preferred text editor through the ordinary typed `sfm:panel/open[/direction]` seam. | X-8a adds a focusable/narrated location control and an explorer-location document recipe; mouse activation and keyboard activation submit the same exact-id panel-open action. Omitting an editor id uses `SFMClientTextEditorConfig`. | — |
| XEXP-17 | A selection-backed multi-root location should expose its truthful canonical expression, including internal selection ids such as `members(id(explorer-1-location))`; the UI must not hide system complexity that a user can learn and manipulate. | X-8a renders `ExplorerSession.location().canonical()` directly in the header and preferred-editor document. Visual truncation may save space, but tooltip, narration, copy, and editor content preserve the exact expression. | — |
| XEXP-18 | Editing the location must not partially mutate roots, grant new filesystem authority merely by typing text, or silently overwrite a newer location revision. | X-8a parses and resolves the complete document, preflights all resolver/authority requirements, then applies one action-backed all-or-none location replacement with an expected explorer/location revision. Invalid, unauthorized, or stale saves leave the explorer unchanged and return in-editor diagnostics/conflict evidence. | — |
| XEXP-19 | Removing the textual view toggle must not make view/sort/group inaccessible, mouse-only, or undiscoverable. | Existing projection actions remain canonical and appear in contextual actions/command palette/keybinding discovery; focused tests prove keyboard and action parity after the header cutover. | — |
| XEXP-20 | File icons should vary by extension; `.java` should have a recognizable non-paper icon, with cocoa/cocoa beans proposed as an example. | X-8b adds an ordered extension/theme presentation contribution ahead of the ordinary paper fallback. The exact `.java` ItemStack remains XD-9; the 1.19.2 working proposal is `minecraft:cocoa_beans`. | — |
| XEXP-21 | `sfm action invoke sfm:explorer/view/set ` must suggest registered view values such as list and icons so `registry://minecraft/item/` can be switched to an ItemStack icon view. | X-8b repairs deep Brigadier/palette continuation discovery, exposes `sfm:list` and `sfm:small_icons`, and proves the item-registry small-icon projection renders actual ItemStacks. | — |
| XEXP-22 | “Absolute paths” is another desired explorer presentation option, but it should be composable with list versus icons rather than accidentally making those mutually exclusive. | XD-10/X-8b preserve the request and add a separate path/label presentation axis with `name`, `relative_path`, and `absolute_path` contributions unless the user explicitly chooses one closed view enum. | — |
| XEXP-23 | Explorer body focus chrome is internally inconsistent: left/right edges remain when the address bar is focused, top/bottom are missing, and a selected row can paint over the border because content is not inset/clipped. | X-8b derives body chrome solely from `KeyboardFocus.BODY`, renders all four edges above or outside row content, and allocates an inset body viewport so cells cannot overwrite it. | — |
| XEXP-24 | Multiple mouse-wheel events must apply immediately and in order; one physical motion must not pause and then become one large jump. Holding Down, which currently feels responsive, is the comparison control. | X-8b instruments callback -> model mutation -> visible frame, preserves every received delta in order, forbids trailing-edge debounce/coalescing in SFM, and fixes whichever measured event/render stage causes the delay. | — |
| XEXP-25 | The explorer needs fuzzy filtering. | X-8b adds action-backed per-explorer filter query/state and a focusable filter surface using the shared fuzzy scorer. XD-11 keeps local materialized-row filtering distinct from B-3's bounded recursive file search. | — |

### Layout and deferred interaction

| ID | Active guidance | Required plan consequence | Superseded by |
| --- | --- | --- | --- |
| XLAY-1 | “Panel” currently overloads visible split region and stacked content; the model needs distinct pane, panel-entry/tab, and component terms/ids. | X-10 audits and introduces public identities without silently changing current `SFMWorkspacePanelId` meaning. | — |
| XLAY-2 | Ctrl+number should target visible split panes, while Ctrl+Tab rotates stacked entries within a pane. | X-10 specifies selector/index semantics and fixtures such as `[[1,2],3]`. | — |
| XLAY-3 | Arbitrary layouts are recursive split trees, including `[1,2;3,4]` and `[1,[2;3],4]`; matrix notation is descriptive, not storage. | Preserve the existing split/stack tree foundation and add pane identity rather than replacing it with a fixed matrix. | — |
| XLAY-4 | Alt+drag panel repositioning with a VS Code-like highlighted destination is desirable but lower priority than explorer content/remoting. | X-11 records it as a later action-backed slice; it is excluded from the first goal. | — |
| XLAY-5 | Dragging roots/nodes between explorers should later support Ctrl=copy, Shift=move, and right-drag=ask, changing explorer membership rather than moving underlying filesystem/registry objects. | X-11 preserves explicit deferred semantics and tests them separately from OS file mutation. | — |
| XLAY-6 | Panel borders and multi-divider intersections need VS Code-like pointer resizing and cursor affordances. | Window-manager Track 1b owns divider identities, hit geometry, continuous shares, two-axis intersection capture, cursor lifecycle, registered-action parity, and live proof; X-10's pane terminology must remain compatible. | — |

## Guidance traceability

| Guidance | Plan coverage | Evidence when complete |
| --- | --- | --- |
| XCLI-1, XCLI-2 | Confirmed constraints; X-6 | Figue help/`ToArgs` snapshots, command-tree absence assertions, direct Vox round trip |
| XCLI-3 through XCLI-6 | Selector/action contracts; X-2, X-5, X-6, X-7 | Parser/resolution/transaction tests and live exact/focused/all/empty-target artifacts |
| XCLI-7 | Work-item headings; handoff protocol | Goal/final summaries always pair ids with descriptive titles |
| XPATH-1 through XPATH-7 | Identity/path-expression contracts; X-1 | Cross-language parse/print/equality/UTF-8/ambiguity fixtures |
| XSEL-1 through XSEL-4 | Selection contract; X-3 | Revision/algebra/live/pinned/undo tests |
| XSEL-5 through XSEL-7 | Explorer navigation and destination contracts; X-4, X-8 | Navigation-retention and pick-cardinality puppets/tests |
| XSEL-8 through XSEL-10 | X-9 | Editor cursor-projection and review-rule adapter evidence |
| XSEL-11 | X-3a and snapshot/episode 0.5/1.5/2.8 | Undo-undo-do branch fixture, retained child enumeration/checkout, ambiguous-redo chooser, export/import, and no orphaned pinned revisions |
| XREL-1 through XREL-8 | Child-relation contract; X-3, X-4, X-5, X-7 | IO-count/paging/cancel/generation/atomic-refresh tests and before/during/after artifacts |
| XEXP-1 through XEXP-5 | Explorer session/projection contract; X-4 | Heterogeneous root, hoist, view/sort/group tests and visual proof |
| XEXP-6 through XEXP-14 | Action/target/remoting contract; X-5, X-6, X-7 | Registry/action parity tests plus direct CLI puppet |
| XEXP-15 through XEXP-19 | Editable location-header contract; X-8a and contextual-plan A-2c | Geometry/narration/action tests, preferred-editor recipe, atomic/stale save fixtures, and live location-editor puppet |
| XEXP-20 through XEXP-25 | XD-9 through XD-11; X-8b | Extension/theme presenter tests, deep action-completion tests, list/small-icon ItemStack proof, independent path-label modes, exact focus-border geometry/render ordering, wheel event-to-frame traces, fuzzy filter ranking/action/keyboard tests, and a live explorer interaction puppet |
| XLAY-1 through XLAY-3 | X-10 | Pane/entry/component selector and nested-layout tests |
| XLAY-4, XLAY-5 | X-11 explicit deferral | Later goal must retain highlighted placement and copy/move/ask semantics |
| XLAY-6 | Window-manager Track 1b; X-10 compatibility | Divider-id/geometry/share tests, horizontal/vertical/intersection cursors and drag proofs, action parity, nested three/four-pane fixtures, and live screenshot/machine topology evidence |

## Intent audit evidence

- **Pass 1 — extraction:** Reread the complete available 2026-08-12 user
  discussion from the initial canonical-CLI/workspace concern through the
  selector, picker, selection-ledger, address, pane terminology, lazy loading,
  and fetch-before-replace corrections. Split compound requests into XCLI,
  XPATH, XSEL, XREL, XEXP, and XLAY ledger entries. Preserved examples carrying
  design force, including `focused`, item-registry plus filesystem roots,
  `selection://a`, non-UTF-8 rejection, `[[1,2],3]`, and the two `kubectl`
  refresh orders.
- **Pass 2 — traceability:** Mapped every active ledger id to a concrete
  contract, work item, validation layer, and/or explicit deferred item. Checked
  the inverse: all material choices are either confirmed user direction,
  verified current-code evidence, or one reversible working assumption named
  below. Removed the earlier ungrounded global persisted-workspace requirement.
- **Pass 3 — adversarial omission:** Rechecked later corrections against the
  completed plan. Restored nuances that are easy to lose: selectors are always
  explicit and may match many; picker state is a destination rather than row
  highlight; explorer expansion does not equal refresh; a flat selection does
  not preserve parent/child ownership; exact-id failure differs from
  non-exact empty targeting; old rows remain visible until successful
  replacement data exists; and the requested name/extension/icon sort choices
  remain concrete contributions rather than a lossy generic “sort” summary.
  The final cross-plan scan also replaced active global-workspace/file-explorer-
  subtype dependencies while retaining those spellings only as explicitly
  superseded provenance.
- **Pass 4 — implementation closure:** Reconciled every X-1 through X-7
  completion criterion against the final Java/Rust implementation and live
  artifacts. The closure audit specifically checked target-root reachability
  with a shared relation store, all-or-none rollback and resolver preflight,
  client-thread publication, filesystem-authority timing, frozen Java/Rust
  projection ids, bounded individual and aggregate Vox response evidence,
  exact-miss behavior, source/build provenance, and the absence of persistence,
  propagation, or X-8+ implementation. Focused regressions were added for each
  correctness issue found during that audit before the full suite and live
  journey were repeated.
- **Known source limitation:** None for this design convergence. The original
  messages summarized above were available in the active conversation. Older
  related intent is retained by the linked plans' existing ledgers rather than
  silently re-summarized here.

### Intent-audit extension — 2026-08-13 checkpoint and matrix reconciliation

- **Pass 1 — extraction:** Rechecked the recent request to expose the truthful
  explorer location, the explicit preference not to hide internal selection
  ids, the request to inspect recent work at every GUI scale, and the prior
  requirement that Ctrl+Enter open a new panel. No new selection/picker/layout
  requirement was inferred from the screenshot sweep.
- **Pass 2 — traceability:** Attached clean commit and nine-variant evidence to
  X-8a, retained picker composition under X-8, pane identity under X-10, and
  routed real-file/Text-Editor-v3 activation to contextual-plan C-3 rather than
  broadening the completed location-document item.
- **Pass 3 — adversarial omission:** Checked that bookkeeping did not claim X-8
  complete, confuse Auto with a ninth numeric scale, hide the low-scale v1
  editor geometry, or reinterpret visual truncation as loss of canonical
  content. The exact internal expression remains available through the editor,
  copy, narration, and tooltip contracts.
- **Known source limitation:** None. The relevant original messages, plans,
  commits, console completion record, manifest, and all nine figures were
  available.

### Intent-audit extension — 2026-08-16 explorer interaction repair

- **Pass 1 — extraction:** Split the latest explorer report into extension-icon,
  projection-completion, list/icons/absolute-path presentation, three separate
  focus-border defects, wheel sequencing/latency, fuzzy filtering, and linked
  panel-divider requirements. Preserved `registry://minecraft/item/`, the
  proposed cocoa Java icon, the comparison with responsive Down-key repeat,
  and the distinction between a missing option and an option that exists but
  cannot be discovered through the palette.
- **Pass 2 — traceability:** Mapped XEXP-20 through XEXP-25 to one bounded X-8b
  item and XLAY-6 to window-manager Track 1b. Verified the present foundation:
  `SFMExplorerProjection.View` already has LIST/SMALL_ICONS and
  `SFMExplorerAction.settingSuggestions()` already supplies `sfm:list` and
  `sfm:small_icons`; `SFMExplorerPanel` draws a focused content border without
  consulting LOCATION versus BODY and cells render into that same rectangle;
  `mouseScrolled` mutates the model immediately in source. The plan therefore
  tests deeper suggestion traversal and event-to-frame behavior instead of
  inventing missing enums or a debounce without evidence.
- **Pass 3 — adversarial omission:** Checked that extension icons retain chest
  directories and paper fallback; item icons remain real ItemStacks; absolute
  path labels can coexist with small icons; address-bar focus does not leave
  body side edges; fixing top/bottom cannot let rows overwrite the new border;
  fuzzy filtering does not silently recurse `C:\`; every wheel callback remains
  observable; and pointer-divider work is not conflated with later Alt+drag
  relocation or filesystem node moves.
- **Fresh-agent resumption check:** A new agent can begin at X-8b, resolve
  XD-9 through XD-11 before public-id/history changes, and validate icons,
  deep completions, path display, focus chrome, filtering, and wheel latency
  without beginning picker X-8 or divider Track 1b. The plan exposes exact
  commands, boundaries, tests, and the current-source observations that must be
  verified rather than assumed.
- **Known source limitation:** None. Current Java source and the complete user
  report were available. The exact Java icon, path-display axis spelling, and
  local-filter recursion policy remain XD-9 through XD-11 rather than silently
  frozen.

### Intent-audit extension — 2026-08-21 non-destructive undo trees

- **Pass 1 — extraction:** Preserved the explicit requirement that undoing more
  than once and then performing new work must not clobber prior materialized or
  projected descendants. Added XSEL-11 rather than weakening XSEL-3's immutable
  revision promise.
- **Pass 2 — traceability:** Mapped XSEL-11 to new X-3a, the snapshot/episode
  ActionIntent/ActionEvaluation/StateRevision graph, History Graph UI, pure
  repository tests, constrained command-palette child choice, and export/import
  evidence.
- **Pass 3 — adversarial omission:** Inspected current source and tests. The
  repository retains immutable revision objects, but `SFMSelection` stores
  linear `undoRevisionIds`/`redoRevisionIds`, a mutation replaces the redo list
  with empty, and the current branch test expects redo to become unavailable.
  Therefore the old descendant is not adequately discoverable as a redo branch;
  X-3a is real work rather than a documentation-only rename.
- **Known source limitation:** None. The current Java model/tests and complete
  user follow-up were available.

## Established foundation

- `platform/cli/sfm` already provides an independently installable `sfm.exe`,
  per-game discovery, authenticated loopback Vox, typed Facet/Figue output,
  exact PID/id targeting, and generic registered-action invocation. Commits
  `8e202d915`, `d23a23d3f`, and `412c5e382` establish that foundation.
- The current working tree refines the game control server so protocol work
  stays on bounded control workers and only Minecraft-sensitive mutation
  crosses a bounded cancellable `SFMClientThreadGate`. Focused tests, the full
  Java suite, canonical compile, and the external size-display puppet passed;
  `docs/tasks/sfm in-game control cli plan.md` contains exact evidence. This
  refinement is not yet committed at the time this plan is written.
- `SFMPathFileExplorerSource.snapshot()` is verified eager/recursive: depth 3,
  at most 256 entries, and at most 64 children per directory. It returns nested
  `SFMFileExplorerEntry.children`. This is a bounded prototype, not the target
  lazy contract.
- `SFMFileExplorerModel` owns one source, one navigation index, expansion paths,
  and a flattened projection over that nested snapshot. Its selection is
  navigation state, not a durable semantic selection.
- `SFMItemPickerModel` separately owns a filtered item list, one selected index,
  and detailed/dense-icon modes. It proves useful item presentation but is not
  yet a generic explorer or multi-selection destination.
- `SFMWorkspaceLayout` already stores an n-ary horizontal/vertical split tree
  plus nested stacks. `SFMWorkspacePanelId` identifies each stacked content
  instance; `visiblePanels()` projects active stack entries. There is not yet a
  distinct public pane id.
- `SFMReviewSessionV1.SelectionRule` already implements literal UTF-8 ranges,
  union, intersection, and difference for pinned review snapshots. It is a
  review-specific semantic model, not a general live selection repository.
- There is intentionally no persisted explorer-workspace format. X-1 through
  X-7 now provide session-lifetime named selections, a generic set-valued
  selector engine, immutable child-relation revisions, and lazy explorer
  sessions; persistence remains an explicit later schema/migration decision.

## Confirmed constraints and ownership boundaries

1. Implement on 1.19.2 first; later propagation is deliberate and separate.
2. Use `sfm-propagate-changes.exe`; do not invoke Gradle directly.
3. Ordinary `sfm.exe` explorer commands are direct typed game operations.
   Generic `sfm invoke` remains available but is not their canonical parent.
4. Every action is self-contained: target selectors and content expressions are
   explicit values. Widget actions use exact identities; `focused` is syntax,
   not hidden ambient capture.
5. Selectors return sets. Actions never silently choose the first result.
6. Multi-target mutation resolves one immutable target snapshot, preflights all
   targets, stages required asynchronous data, and applies one game-thread
   logical transaction only if every target is still valid. Failure produces no
   partial mutation and reports per-target reasons.
7. Selector evaluation has no mutation side effect. Empty-target UI creation is
   a separate explicit action policy.
8. `SFMPath` is a canonical UTF-8 resolver path, not `java.nio.file.Path`, Rust
   `PathBuf`, or Minecraft `ResourceLocation`. Boundary adapters retain native
   types internally.
9. Explorer location membership, child relations, projection order/grouping,
   expansion, navigation cursor, and semantic selections are separate state.
10. Resolver enumeration and refresh never run on Minecraft's render thread.
11. No operation may infer filesystem authority from a title, logical display
    string, process working directory, user profile, drive root, or parent walk.
12. Explorer sessions and ephemeral selections are game-session state in the
    first slice. Disk persistence/named collection sharing requires a later
    explicit format/migration goal; no provisional global “workspace” file is
    introduced now.
13. Adding/removing explorer roots only changes explorer membership. It never
    moves or deletes underlying files, registry entries, items, or comments.
14. Existing editor cursor and review selector semantics are preserved until
    their explicit adapters land in X-9.
15. Alt+drag layout placement and inter-explorer root dragging are recorded but
    excluded from the first implementation goal.

## Reversible working assumptions

- Canonical first-slice external grammar uses positional selector/content
  arguments plus an explicit empty-target policy:

  ```text
  sfm explorer list --instance-id <id>|--instance-pid <pid>|--instance latest-focused
  sfm explorer root list <explorer-selector> [instance selector]
  sfm explorer root add <explorer-selector> <concrete-path> --if-no-match fail|open-new [instance selector]
  sfm explorer root remove <explorer-selector> <concrete-path> [instance selector]
  sfm explorer view set <explorer-selector> <view-id> [instance selector]
  sfm explorer sort set <explorer-selector> <sort-id> [instance selector]
  sfm explorer group set <explorer-selector> <group-id> [instance selector]
  sfm explorer root hoist set <explorer-selector> auto|show-roots [instance selector]
  ```

  The corresponding in-game registered action ids use the same argument order,
  e.g. `sfm:explorer/view/set <explorer-selector> <view-id>`. Exact Figue and
  Brigadier spellings are frozen in X-1/X-2 before history or documentation
  makes them durable.
- X-1 through X-7 expose the `Literal(SFMPath)` subset at explorer mutation
  boundaries. The shared parser/resolver already models set-valued path
  expressions, but resolving `members(...)`, `children(...)`, and set algebra
  directly from mutation commands is retained for X-8 with the selection-action
  surface. This prevents the completion record from claiming a command surface
  that was not part of the first live slice.
- Initial explorer selectors are `id(<id>)`, `focused`, `all`, `union(...)`,
  `intersection(...)`, and `difference(...)`. Predicate selectors such as
  `where(kind = "file")` may land when one real use needs them; the typed AST
  reserves them so commands do not later require a wire break.
- `--if-no-match open-new` is accepted only for selectors that are not exact
  identity claims. `focused ... --if-no-match open-new` is the explicit version
  of “use the focused explorer, otherwise create one.”
- Initial explorer views are `sfm:list` and `sfm:small_icons`; initial sorts are
  `sfm:name`, `sfm:extension`, and `sfm:icon`; initial groups are
  `sfm:hierarchy` and `sfm:none`. These are
  registry contributions, not closed enums in the durable model.
- X-8b's working path-label axis is `sfm:name`, `sfm:relative_path`, and
  `sfm:absolute_path`, independently combinable with view/sort/group/hoist.
  The canonical action is provisionally
  `sfm:explorer/path-display/set <explorer-selector> <path-display-id>`; XD-10
  freezes the exact name before command history or documentation claims it.
- A live selection address (`selection://a`) resolves the current head; a pinned
  address (`selection://a@<revision-id>`) resolves exactly one immutable
  revision. Names need not be globally unique beyond their repository/lifetime;
  ambiguous selectors return all matches or require exact id.
- Child pages are bounded to a provider-configured count. Atomic refresh means
  the old generation remains visible until a successful replacement first page
  exists; later pages append to that new generation atomically. It does not mean
  exhaustively reading an unbounded directory before any update appears.

## Core typed model

### Concrete content paths and path expressions

```text
SFMPath = FilePath | RegistryPath | SelectionPath | DocumentRegionPath | contributed variants

PathExpression =
    Literal(SFMPath)
  | Members(Selector<Selection>)
  | Children(PathExpression)
  | Union(List<PathExpression>)
  | Intersection(List<PathExpression>)
  | Difference(PathExpression, List<PathExpression>)
```

Concrete paths identify one subject. Path expressions resolve to zero or more
concrete paths and retain completeness/diagnostics. A query is never persisted
as though it identified one concrete object.

`selection://a` identifies the selection object as an explorable subject. Its
children are the selection's members. `members(selection(name("a")))` produces
those member paths directly for an operation that consumes a set.

### Entity selectors

```text
Selector<T> =
    Id(TId)
  | Focused
  | All
  | Predicate(...)
  | Union(List<Selector<T>>)
  | Intersection(List<Selector<T>>)
  | Difference(Selector<T>, List<Selector<T>>)
```

Each selector is typed: a `Selector<Explorer>` cannot target a pane or game.
Resolution returns a deterministic set of stable ids plus diagnostics and a
repository generation. The action captures this result once.

### Versioned selections

```text
Selection {
    id
    optional_name
    lifetime
    head_revision
}

SelectionRevision {
    id
    parent_revision_ids
    members: Set<SFMPath>
    operation
    actor
    request_id
    created_at
}
```

Membership is a set. Manual display order, if requested, is a separate
projection relation. Revisions are immutable; repeated requests are
idempotent by request id where the protocol supplies one.

### Versioned child relations

```text
ChildEdge(parent: SFMPath, child: SFMPath)

ChildPage {
    parent
    edges
    continuation
    completeness
    resolver_generation
    diagnostics
}
```

A refresh for parent set `P` stages a candidate page off-thread, then publishes:

```text
new_edges = old_edges excluding edges whose parent is in P
          union fetched_edges_for_P
```

The commit is one relation revision. A failed or stale candidate is not
published; the old rows remain visible with a refresh diagnostic.

### Explorer sessions and projection

```text
ExplorerSession {
    explorer_id
    location: PathExpression
    projection
    navigation_cursor
    expanded_paths
    child_relation_revision
    active_requests
    selection_overlays
}

ExplorerProjection {
    view
    sort
    group
    single_root_hoist
    filters
    optional_manual_root_order
}
```

One literal root may remain a literal location. Adding a second root creates an
ephemeral selection containing both paths and changes the location to that
selection. Removing back to one root need not destroy history; `auto` hoisting
simply presents the remaining root's children.

### Selection destinations

```text
SelectionDestination {
    destination_selection_id
    acceptance_rule
    cardinality_rule
    submit_action
    cancel_action
    result_consumer
}
```

An explorer may browse subjects the destination cannot accept. Submit is
disabled with a reason until the destination selection satisfies its rules.

## Action and transaction contract

Canonical registered explorer actions implemented in X-1 through X-7 include:

```text
sfm:explorer/node/expand <explorer-selector> <concrete-path>
sfm:explorer/node/collapse <explorer-selector> <concrete-path>
sfm:explorer/node/toggle <explorer-selector> <concrete-path>
sfm:explorer/node/refresh <explorer-selector> <concrete-path>
sfm:explorer/root/add <explorer-selector> <concrete-path> --if-no-match fail|open-new
sfm:explorer/root/remove <explorer-selector> <concrete-path>
sfm:explorer/view/set <explorer-selector> <view-id>
sfm:explorer/sort/set <explorer-selector> <sort-id>
sfm:explorer/group/set <explorer-selector> <group-id>
sfm:explorer/root/hoist/set <explorer-selector> auto|show-roots
```

The following canonical selection actions and expression-valued mutation
adapters remain required by X-8/X-9; their ids are reserved here rather than
being reported as implemented by the first slice:

```text
sfm:selection/create <name>
sfm:selection/member/add <selection-selector> <path-expression>
sfm:selection/member/remove <selection-selector> <path-expression>
sfm:selection/derive/union <selection-selector> <result-name>
sfm:selection/derive/intersection <selection-selector> <result-name>
sfm:selection/derive/difference <include-selector> <exclude-selector> <result-name>
sfm:selection/history/undo <selection-selector>
sfm:selection/history/redo <selection-selector>
```

A widget action uses `id(<exact-explorer-id>)` and a literal exact node path.
A keyboard action may use `focused`. No widget calls the model directly for a
semantic operation that has an action.

For every set-valued mutation:

1. Parse and canonicalize the action.
2. Resolve the game and typed target selector once.
3. Report zero/many matches rather than narrowing silently.
4. Preflight availability/capabilities for every target.
5. Resolve/stage the concrete path (and, in X-8+, path expressions) or async
   provider data without mutation.
6. Revalidate target and repository generations on the Minecraft client thread.
7. Apply all target mutations in one logical publication, or none.
8. Return bounded per-target changed/no-op/error data plus aggregate outcome.

## Scope

### In the first vertical slice

- Typed UTF-8 content paths and the minimal path-expression algebra.
- Typed exact/focused/all and set-composed explorer selectors.
- Session-lifetime versioned selections with add/remove/set algebra and
  undo/redo-capable revisions.
- Versioned immediate-child relations with one-level file and item-registry
  resolvers, paging, cancellation, and atomic refresh.
- A generic explorer session that supports heterogeneous roots, list/small-icon
  views, name/extension/icon sorts, hierarchy/none grouping, and idempotent
  single-root hoist.
- Exact action-backed expansion, refresh, root membership, and projection.
- Direct `sfm explorer ...` CLI/Vox commands using the existing control service
  and client-thread gate.
- Focused unit/integration tests, full canonical validation, and one
  self-orchestrating live puppet with machine-readable relation/selection
  artifacts and screenshots.

### Explicitly outside the first vertical slice

- Persistent selections or named explorer collections across game restarts.
- Selection destinations and migration of the item picker.
- Text Editor v3 selection overlays/cursor adapters.
- Comment/review selector migration.
- Native folder picker and folder-drop UX beyond any existing fixture path.
- Root/node drag between explorers and copy/move/ask modifier behavior.
- Alt+drag pane rearrangement and highlighted drop targets.
- Fuzzy recursive search, jump-to-definition, or source editing.
- Propagation to later Minecraft branches, publication, or release tagging.

## Design gates

| Gate | Required decision | Working resolution | Acceptance consequence |
| --- | --- | --- | --- |
| XD-1 Canonical text grammar | Exact selector/path-expression spelling in Brigadier and Figue, including quoting and native paths | Freeze fixture-first canonical printers/parsers in X-1/X-2. Keep CLI native-path convenience at the boundary but transmit typed canonical paths. | Cross-language round trips and help snapshots must agree before action history or docs are updated. |
| XD-2 Empty target | How a self-contained command requests explorer creation | Require explicit `--if-no-match fail|open-new`; reject `open-new` with an exact-id selector. | Tests prove no implicit creation and no replacement for missing ids. |
| XD-3 Multi-target failure | Partial success or transaction | Preflight/stage all, then all-or-none logical publication. Return per-target evidence. | Fault injection proves one incompatible/stale target prevents every mutation. |
| XD-4 Selection lifetime | Persist now or later | First slice is game-session only with versioned in-memory revisions; persistence is a separate schema/migration goal. | Restart tests assert absence of accidental persistence rather than claiming it. |
| XD-5 Relation paging refresh | How atomic replacement works for unbounded children | Keep old generation until replacement first page succeeds; publish fresh prefix+continuation atomically, then append later pages. | Delayed/failing provider tests prove no blank interval and bounded memory. |
| XD-6 Root ordering | Is location a list or set? | Membership is a set; manual order is independent projection metadata. | Algebra tests ignore order; projection tests prove deterministic manual/name order. |
| XD-7 Resolver authority | Can a displayed path be opened directly? | Only resolver-issued typed paths/capabilities authorize reads; display text never grants authority. | Spoofed title/path and traversal/symlink fixtures fail closed. |
| XD-8 Existing review algebra | Replace or adapt? | Preserve `SFMReviewSessionV1.SelectionRule`; later adapt shared expressions to pinned review rules. | Existing review fixtures remain byte/semantic compatible until X-9. |
| XD-9 Java extension icon | Which ItemStack represents `.java`, and is the map fixed or contributed? | **Closed for X-8b:** use an ordered contributed extension/theme registry and `minecraft:cocoa_beans` for `.java` on 1.19.2 (the valid item matching the proposed cocoa concept), with paper fallback and no filename-only hard-coded renderer branch. The mapping remains replaceable through the contribution surface. | X-8b tests precedence, case handling, compound extensions, missing item fallback, narration, list/small-icons, and actual item ids. |
| XD-10 Absolute-path presentation | Is “absolute paths” a mutually exclusive view beside list/icons or an independent label/path-display axis? | **Closed for X-8b:** independent path display so `small_icons + absolute_path` is representable. `view/set` still discovers list/icons; a hierarchical `path-display/set` action discovers name/relative/absolute. | X-8b freezes action/id spelling and tests every combination before exposing it in palette history. |
| XD-11 Explorer fuzzy-filter scope | Does filtering search only known rows or recursively enumerate descendants? | **Closed for X-8b:** filter/rank the current lazy materialization (and newly arriving pages) immediately without I/O. B-3 owns bounded cancellable recursive file search through Ctrl+Shift+N. The filter visibly says when a subtree is unmaterialized rather than implying exhaustive search. | Filter tests assert zero resolver reads caused solely by query changes, stable selection/expansion, streamed-page incorporation, and a separate recursive-search action. |

All gates have a reversible working resolution sufficient for the first slice.
X-1/X-2 must record the exact final grammar before downstream integration.

### X-1/X-2 contract freeze — 2026-08-12

The first implementation uses these canonical, round-trippable spellings:

```text
concrete-path   = file:///D:/dir/name.txt
                | file://server/share/dir
                | registry://minecraft/item/
                | registry://minecraft/item/minecraft/stick
                | selection://name
                | selection://name@revision
                | <contributed-lowercase-scheme>://<encoded-authority/path>

path-expression = <concrete-path>
                | members(<selection-selector>)
                | children(<path-expression>)
                | union(<path-expression>,...)
                | intersection(<path-expression>,...)
                | difference(<include>,<exclude>,...)

entity-selector = id(<percent-encoded-id>)
                | name(<percent-encoded-name>)       # Selection domain only
                | focused                            # Focus-capable domains only
                | all
                | union(<entity-selector>,...)
                | intersection(<entity-selector>,...)
                | difference(<include>,<exclude>,...)
```

Native filesystem arguments such as `.` or `D:\repo with spaces` are accepted
only at a native-path boundary and immediately converted to a canonical `file`
URI. Canonical grammar uses uppercase Windows drive letters, forward slashes,
strict UTF-8 percent encoding, no query/fragment/user-info/port, no unescaped
expression delimiters, and no `.`/`..` segments after normalization. UNC hosts
are canonicalized case-insensitively. Resolver-specific identity may apply
additional filesystem case/symlink rules; generic path equality never lowercases
arbitrary components. Malformed percent escapes, decoded NUL, unpaired Java
surrogates, non-Unicode Rust paths, unsupported selector nodes for a domain,
empty set operations, and pipe aggregates fail closed with typed diagnostics.

Java and Rust own equivalent typed ASTs and canonical printers/parsers. Vox
DTOs carry a dedicated typed canonical path/expression/selector value rather
than action-token strings; decoding always reparses and validates before use.
This flat canonical wire boundary avoids requiring recursive generated Java
union support while preserving typed domain values on both sides.

## Execution order and parallel topology

```text
X-1 paths/expressions + X-2 selectors
             |                 |
             +-------> X-3 selections/relations
                               |
                 +-------------+--------------+
                 |                            |
          X-4 generic explorer          X-6 CLI/protocol DTOs
                 |                            |
                 +------------> X-5 actions <-+
                                      |
                                      v
                               X-7 live proof

Immediate repair: X-8b explorer projection/icon/filter/focus/scroll fidelity
Later: X-8 picker destinations -> X-9 editor/comments
       X-10 pane terminology -> X-11 drag interactions
Linked: window-manager Track 1b pointer divider resize -> X-10 terminology
```

Safe parallel lanes after X-1/X-2 freeze shared Facet/Java contracts:

- **Selection/relation lane:** X-3 pure repositories, immutable revisions,
  algebra, paging, generation, and atomic publication tests.
- **Resolver lane:** X-4 file and item-registry immediate-child adapters plus
  bounded fake/delayed/failing providers. It does not own central registration.
- **Explorer UI lane:** X-4 session/projection model and X-5 widgets/actions
  against in-memory repositories. It does not edit generated Vox files.
- **Rust CLI/protocol lane:** X-6 Figue grammar, typed output, protocol spec, and
  fake-service tests. One integration owner regenerates/checks Java bindings.
- **Integration lane:** one owner alone changes central registries, control
  capabilities, checked-in generated Java, puppets, plans, and changelog.

## Phase X — First selection-backed lazy explorer vertical slice

### [x] X-1 Freeze typed UTF-8 paths and path expressions

**Work:**

- Add cross-language Facet/Java models for concrete resolver paths and the
  minimal path-expression algebra.
- Implement canonical parse/print/equality and native file-path boundary
  conversion without `ResourceLocation` coercion.
- Preserve literal `file`, `registry`, and `selection` examples; reject pipe
  aggregates, malformed schemes, invalid escaping, and non-UTF-8 input.
- Define exact live/pinned selection-path spelling and completeness diagnostics.

**Validation:**

```pwsh
cd platform\cli\sfm
.\check-all.ps1
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMPathExpressionTests --wait-for-build-lock
```

**Completion criteria:** Rust and Java produce byte-identical canonical strings
and equivalent typed ASTs for every fixture; native Windows paths round-trip
without becoming resource locations; invalid/non-UTF-8 inputs fail with typed
diagnostics; no `|` aggregate is accepted.

**Completion evidence (2026-08-12):** Added strict Java `SFMPath`/
`SFMPathExpression` and Rust `explorer::{SfmPath, PathExpression}` sum types,
including canonical file/UNC/registry/selection/contributed paths, percent
encoding, native Windows conversion, set-expression parsing, pipe rejection,
and malformed-Unicode/encoding failures. Shared expected spellings are asserted
independently on both sides. `SFMPathExpressionTests` passed through the
canonical test harness and `cargo test explorer` passed 9/9 Rust tests.

### [x] X-2 Implement explicit set-valued selectors

**Work:**

- Define typed selectors and repositories for game, pane, panel entry, explorer,
  and selection domains without allowing cross-domain coercion.
- Implement exact/focused/all plus union/intersection/difference; reserve typed
  predicate nodes without exposing unsupported syntax as successful.
- Resolve to stable ids, deterministic ordering for reports, repository
  generation, and diagnostics; never mutate or silently first-match.
- Freeze canonical action/Figue selector spelling and exact empty-target policy.

**Validation:**

```pwsh
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMEntitySelectorTests --wait-for-build-lock
cd platform\cli\sfm
.\check-all.ps1
```

**Completion criteria:** The same selector fixtures resolve identically in
action and control paths; `focused` is visible syntax; `all` may target several
explorers; exact-id misses remain misses; selector evaluation has no UI side
effect; malformed/unsupported expressions fail closed.

**Completion evidence (2026-08-12):** Added domain-typed Java and Rust selector
ASTs with canonical `id`, `name`, `focused`, `all`, union, intersection, and
difference spellings. Java resolves immutable repository snapshots to stable,
deterministically ordered ids plus diagnostics and generation evidence; action,
Brigadier, and Vox boundaries require canonical explorer-domain syntax. Tests
cover cross-domain rejection, exact misses, focus recency, multi-target `all`,
set algebra, malformed expressions, and selector/output round trips. The final
`platform/cli/sfm/check-all.ps1` run passed all 41 Rust tests and generated-Java
parity; the canonical Java suite passed all selector tests as part of 794/794.

### [x] X-3 Build versioned selection and child-relation repositories

**Work:**

- Implement session-scoped named/id selections with immutable revisions,
  add/remove/union/intersection/difference, idempotent request provenance, and
  undo/redo-capable heads.
- Implement immutable child-relation revisions and per-parent page state,
  continuation, completeness, diagnostics, and resolver generations.
- Stage refresh candidates before publication; atomically replace only requested
  parent rows; retain old rows on failure/staleness; cancel obsolete work.
- Expose relation ranges as path expressions without pretending a flat
  selection preserves parent ownership.

**Validation:**

```pwsh
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMSelectionRepositoryTests --wait-for-build-lock
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMChildRelationRepositoryTests --wait-for-build-lock
```

**Completion criteria:** Pure tests prove set algebra, independent names,
immutable history, undo/redo, live/pinned resolution, multi-parent replacement,
failure retention, cancellation, stale-generation rejection, paging, and no
intermediate empty relation revision.

**Completion evidence (2026-08-12):** Added typed selection ids, immutable
membership revisions and head events, actor/request-idempotent mutation,
unique id/name aliases, union/intersection/difference, live and pinned
`selection://` resolution, undo/redo with retained branches, plus immutable
parent/child relation revisions with staged multi-parent replacement,
continuation pages, cancellation, stale-request rejection, and failure
diagnostics that retain published rows. `SFMSelectionRepositoryTests` and
`SFMChildRelationRepositoryTests` passed through the canonical test harness.

### [x] X-3a Upgrade selection-head navigation from linear stacks to an undo tree

**Work:**

- Preserve the completed immutable `SFMSelectionRevision` model and existing
  pinned `selection://...@<revision>` identities.
- Replace `undoRevisionIds`/`redoRevisionIds` as the authoritative topology with
  parent/child adjacency and one current/named head per selection history.
- Undo follows an explicitly chosen/default parent. Redo enumerates children;
  one child may be selected directly, while several children require an
  explicit revision or the shared constrained command-palette chooser.
- Creating a revision from an ancestor appends a child and never clears/removes
  siblings. Keep the last-traversed child only as a convenience preference, not
  as the sole surviving redo path.
- Record head movements and branch naming/pinning with actor/request provenance.
  Keep request-idempotence and deterministic ordering.
- Add enumerate-history, checkout-revision, undo, and redo-child operations to
  the typed action surface consumed by Text Editor v3/episode history. Do not
  invent an independent editor-only tree format.
- Since selections are session-scoped and this behavior is unreleased, perform
  a full internal cutover rather than preserving the misleading linear DTO as a
  public compatibility layer.

**Validation:**

```pwsh
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMSelectionRepositoryTests --wait-for-build-lock
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMPathExpressionResolverTests --wait-for-build-lock
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMTextEditorPanelTests --wait-for-build-lock
```

Add a deterministic graph fixture: create A→B→C, undo twice to A, create D,
then prove B/C/D remain enumerable and pinned-resolvable; redo at A reports B
and D, explicit checkout reaches C, branch naming survives export/import, and
deleting/closing a view does not delete revisions.

**Completion criteria:** Undo-undo-do yields a visible branch rather than a
destroyed redo stack, all previously reachable revisions remain addressable,
ambiguous redo is explicit, and the shared snapshot/episode History Graph can
consume the same topology without translating from a second history model.

**Completion evidence (2026-08-21):** `SFMSelection` no longer contains linear
undo/redo stacks. The repository derives deterministic parent/child adjacency
from immutable revisions, exposes current and named heads, explicit checkout,
default/explicit undo, ambiguous/single/explicit-child redo, history
enumeration, and last-traversed-child preference without using that preference
to resolve ambiguity. Head movement and naming retain actor/request provenance.

The A→B→C, undo twice, then A→D fixture proves both B→C and D remain
enumerable and pinned-resolvable; redo at A reports B and D; explicit traversal
reaches C; `old-route` survives a deterministic
`sfm.selection-history/1` JSON encode/decode/repository restore; and the encoded
archive is byte-stable after round trip. `SFMSelectionHistoryActions` supplies
hierarchical typed operation ids for enumerate, checkout, undo, redo,
redo-child, and name-head. `SFMSelectionHistoryGraphProjection` consumes the
repository archive directly and publishes the same branch topology through
`sfm.history-graph/1`, with named heads represented as retention pins rather
than a second redo model. Repository, path-expression, Text Editor v3,
trajectory-contract, and projection focused tests pass through the canonical
SFM test harness. No dependency or lockfile changed.

### [x] X-4 Replace recursive snapshots with a heterogeneous lazy explorer session

**Work:**

- Introduce generic resolver/presentation registries and immediate-child
  adapters for bounded filesystem paths and the item registry.
- Replace nested `SFMFileExplorerEntry.children` ownership in the production
  path with relation-backed visible projection and lazy expansion.
- Add `ExplorerSession` with path-expression location, heterogeneous roots,
  independent projection/navigation/expansion/scroll/request state, and stable
  session id.
- Implement list/small-icons, contributed name/extension/icon sorts,
  hierarchy/none group, and auto/show-roots hoist. Keep membership and manual
  root order separate.
- Convert a literal single root to an ephemeral selection-backed location when
  a second heterogeneous root is added.

**Validation:**

```pwsh
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMLazyExplorerTests --wait-for-build-lock
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMExplorerProjectionTests --wait-for-build-lock
```

**Completion criteria:** A broad/deep fixture proves opening reads root metadata
only, expansion reads one level only, paging is bounded, and collapse/close
cancels obsolete work. One explorer shows an item-registry and filesystem root
together; one root auto-hoists, two roots show parents; navigation remains
independent from semantic selections; switching name/extension/icon sort changes
only projection order and reports unavailable deterministically where a subject
has no applicable extension or icon key.

**Completion evidence (2026-08-12):** Added resolver and presentation
registries, bounded filesystem and Minecraft item-registry resolvers,
owner-executor publication, a relation-backed lazy loader, independent explorer
sessions, pagination, navigation/scroll/request state, and list/small-icon plus
name/extension/icon/hierarchy/hoist projections. A second heterogeneous root
creates an ephemeral versioned location selection; presentation resolution
renders the item root with a real ItemStack icon without hard-coding registry
parsing into the panel. Focused tests prove metadata-only open, immediate-child
bounded pages, continuation loading beyond 128 entries, cancellation,
staleness, failure retention, symlink containment, owner-thread publication,
independent navigation, deterministic unavailable sort keys, and mixed roots.
The final live I/O artifact records 8 metadata reads, 3 exact parent
enumerations, 6 immediate entries, no dropped evidence, and 0 render-thread I/O
violations.

### [x] X-5 Route explorer UI and mutations through explicit actions

**Work:**

- Register exact-selector node expand/collapse/toggle/refresh, root
  add/remove, projection, and hoist actions.
- Make chevrons, keyboard operations, panel controls, and existing directory
  drop adapters invoke these actions instead of mutating models directly.
- Add explorer registry/focus recency and explicit empty-target handling.
- For set-valued targets, resolve once, preflight/stage all, revalidate on the
  client thread, and publish all-or-none typed outcomes.
- Open a generic explorer through the ordinary panel action when an explicitly
  non-exact request uses `--if-no-match open-new` and matches none.

**Validation:**

```pwsh
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMExplorerActionTests --wait-for-build-lock
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMMultiTargetActionTests --wait-for-build-lock
```

**Completion criteria:** Every visible semantic explorer control has a
registered exact action; widget and palette paths produce the same result;
focused/all/exact selectors work; one stale/incompatible target prevents every
multi-target mutation; exact misses never create; explicit non-exact empty
policy creates exactly one explorer.

**Completion evidence (2026-08-12):** Registered hierarchical node
expand/collapse/toggle/refresh, root add/remove, view/sort/group, and root-hoist
actions. The generic panel emits exact-id actions for chevrons, keyboard
semantic controls, view changes, and file drops. The authoritative transaction
engine captures one selector snapshot, checks target-local path reachability,
revalidates repository/session/selection revisions under deterministic locks,
preflights every resolver before any work starts, snapshots reversible session
and selection state, rolls back injected apply failures, and starts resolver
side effects only after the logical commit. Filesystem authority is granted by
the committed hook only after successful target/preflight/state validation.
Focused regressions prove one incompatible/stale/missing-resolver target changes
none, a second-target failure restores both ledgers, rejected/no-target actions
grant no authority, synchronous resolver failures retain typed request
evidence, and individually valid fields cannot compose an oversized post-commit
Vox response.

### [x] X-6 Add direct typed `sfm explorer` control commands

**Work:**

- Extend the authoritative Vox spec with explorer describe/list/root/projection
  request/result DTOs and capabilities; regenerate/check Java deterministically.
- Add direct Figue `sfm explorer ...` grammar and versioned Facet outputs. Do not
  place these commands under `sfm invoke` and do not add built-in short aliases.
- Reuse existing authenticated game selection and the bounded client-thread
  gate. Resolve/canonicalize native paths in Rust and independently revalidate
  them in the game.
- Return selected game identity, captured explorer targets, selection/relation
  revisions, per-target outcomes, and resulting UI evidence.

**Validation:**

```pwsh
cd platform\cli\sfm
.\check-all.ps1
sfm help list --short
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMControlExplorerTests --wait-for-build-lock
```

**Completion criteria:** Canonical help exposes direct long-form explorer
commands and no aliases; fake/live round trips preserve selector and path ASTs;
ordinary Gradle/IntelliJ Java work still consumes checked-in generated Java
without invoking Cargo; all mutations cross the narrow client-thread gate.

**Completion evidence (2026-08-12):** Extended the authoritative Facet/Vox
service with bounded typed explorer operations/results and checked-in generated
Java; both sides independently parse canonical selector/path values and accept
the same frozen first-slice projection ids. Added direct Figue commands for
list/describe, root list/add/remove/hoist, node expand/collapse/toggle/refresh,
and view/sort/group settings. `sfm help list --short` lists only these long-form
commands plus the pre-existing generic `invoke`; tests assert no explorer
aliases. Requests use authenticated instance selection and the bounded
`SFMClientThreadGate`; responses include game/request/target ids, revision and
relation-request provenance, per-target outcomes, and bounded state evidence.
The final `check-all.ps1` passed formatting, strict Clippy, 41/41 tests, and
generated-Java parity. Gradle-independent canonical Java compilation consumed
the checked-in bindings without running Cargo.

### [x] X-7 Prove the slice through one self-orchestrating live journey

**Work:**

- Add a deterministic puppet that starts with no explorer, runs the real
  external `sfm.exe` child process, and adds a seeded filesystem root using
  `focused --if-no-match open-new`.
- Prove single-root hoisting, then add the item-registry root to the exact same
  explorer and show both parent roots.
- Expand one directory, capture resolver query counts proving no descendant
  prefetch, and run a delayed refresh that retains old rows until atomic swap.
- Open a second explorer and use an `all` selector to change both views; prove an
  explicit missing explorer id fails without creating a third.
- Capture screenshots plus machine-readable explorer, selection, child-relation,
  CLI, and timing artifacts.

**Validation:**

```pwsh
sfm-propagate-changes.exe test run --branch 1.19.2 --wait-for-build-lock
sfm-propagate-changes.exe run compile --branch 1.19.2 --wait-for-build-lock
sfm-propagate-changes.exe puppet run sfm:title_screen_external_cli_lazy_explorer --branch 1.19.2 --wait-for-build-lock
```

**Completion criteria:** A human can see the externally created explorer,
single-root hoist, heterogeneous two-root presentation, lazy expansion, stable
old rows during refresh, atomic replacement, and multi-explorer view change.
Artifacts correlate game/explorer/selection/relation/request ids and prove no
recursive opening, no exact-id fallback, no render-thread IO, and no generic
`sfm invoke` dependency. Full tests and canonical compile pass.

**Completion evidence (2026-08-12):** The self-orchestrating
`sfm:title_screen_external_cli_lazy_explorer` puppet launches the real external
CLI itself and completed at:

```text
platform/minecraft/build/sfm-toolchain/artifacts/game-test-preview/runs/sfm-title_screen-20260812-182322-010
```

Its six inspected screenshots show the hoisted single filesystem root,
heterogeneous filesystem/item roots, one-level directory expansion, old rows
while a completed resolver page is publication-gated, atomic replacement, and
two panels changed to small icons by one `all` command. JSON artifacts preserve
the full CLI journey, explorer states, selection revisions, relation
before/during/after revisions (2/2/3), request correlations, timing, and
sequence-ordered resolver I/O. The exact miss created no third explorer; the
same external request id correlates both `all` targets. The manifest pins Git
revision `412c5e382fccd79d4bee5a49dd2db17fcf0ae957`, dirty working-tree status,
and source fingerprint
`blake3:99dcfb1af0a9aef2d8ea9ca9589535e6ff247282` so the proof cannot be mistaken
for a stale clean-HEAD build.

Final canonical validation on those sources:

```text
platform/cli/sfm/check-all.ps1: 41 passed; format, Clippy, codegen check passed
platform/cli/sfm-propagate-changes cargo test: 484 passed, 3 ignored; scenarios 7 passed
platform/cli/sfm-propagate-changes cargo clippy --all-targets -- -D warnings: passed
sfm-propagate-changes.exe test run --branch 1.19.2 --wait-for-build-lock: 794/794 passed
sfm-propagate-changes.exe run compile --branch 1.19.2 --wait-for-build-lock: passed
sfm-propagate-changes.exe puppet run sfm:title_screen_external_cli_lazy_explorer --branch 1.19.2 --wait-for-build-lock: passed
```

## Later phases retained by this plan

### [x] X-8a Make the explorer location a preferred-editor document

**Work:** Replace the generic explorer panel's static `SFM Explorer` title and
textual `List`/`Small icons` header toggle with one full-width, focusable
location control. A literal single-root location displays its canonical path;
a selection-backed or expression-valued location displays its exact canonical
expression, including backing selection ids such as
`members(id(explorer-1-location))`. Truncate only the visual glyph run; retain
the exact value in tooltip, narration, copy behavior, and editor content so the
implementation remains inspectable and learnable. Preserve usable geometry in
narrow panels.

Click or keyboard activation must submit an exact-explorer action that opens a
typed explorer-location document through the ordinary directional
`sfm:panel/open ... sfm:text_editor` recipe. Opening to the side preserves the
explorer by default; omitted editor id uses the configured preferred editor,
while explicit placement/editor variants remain invokable. Do not create a
private inline editor or a second panel-placement mechanism.

The canonical semantic entry points are:

```text
sfm:explorer/location/edit <explorer-selector> [focused|left|right|above|below] [editor-id]
sfm:explorer/location/set <explorer-selector> <path-expression> --expected-revision <revision>
```

`location/edit` defaults to `right` for the header gesture and delegates to the
same `OpenPanelAction`/text-editor recipe used by `sfm:panel/open/right`.
`location/set` receives the one canonical path expression represented by the
editor document.

The editable virtual document contains exactly the canonical path expression
stored by the explorer. Do not materialize or rewrite a selection-backed
expression into a friendlier root list. Its reopen/save recipe captures explorer
id, expected location/session revision, and resolver-authority context. Saving
parses and preflights the whole expression before one all-or-none client-thread
location replacement. Syntax, unsupported resolver, containment, authority,
and stale-revision failures leave the old location visible and place actionable
diagnostics in the editor. This edits explorer session state only; it never
writes any referenced host file.

Retain view/sort/group/hoist as registered semantic actions and expose them
through the command palette, contextual actions, and keybinding discovery after
the prose toggle is removed.

**Validation:** Add pure header-geometry/ellipsis/narration tests; mouse and
keyboard action-parity tests; preferred/explicit editor and directional recipe
tests; literal, heterogeneous, selection-backed-id, Unicode, and composed-expression
document round trips with byte-identical canonical content; successful atomic replacement; invalid/unauthorized/stale
save retention; multi-target rollback; and projection-action discoverability.
Capture a live split view with the explorer on one side and its editable
location document in the configured preferred editor on the other. The fixture
must leave the preference untouched so the omitted-editor path proves it
honours the actual configured registration; explicit Text Editor v3 and other
editor ids remain covered by the command-tree contract.

**Completion criteria:** The header spends its space on a truthful location
control rather than redundant identity/view prose; activating it opens the
preferred editor through the shared panel action; a user can inspect and safely
replace the exact single- or multi-root location expression, including internal
selection ids; stale or invalid edits never
partially mutate or broaden authority; and all former view behavior remains
keyboard/action accessible.

**Completion evidence (2026-08-12):** The static explorer/view prose was
replaced by one full-width canonical location control. Its visible run may
ellipsis, while title, hover tooltip, narration, Ctrl+C copy, and the virtual
editor document all retain the exact `ExplorerSession.location().canonical()`
value, including the live internal id
`members(id(explorer-explorer-1-location))`. The control is reachable by Tab,
has a focused border/narration, and mouse, Enter, and Ctrl+L converge on the
same exact-id `sfm:explorer/location/edit ... right` action. Ctrl+G and the
registered view/sort/group/hoist actions remain independent of the header.

`location/edit` accepts focused/exact/set-valued selectors plus every
focused/left/right/above/below placement and optional editor id. Omitting the
editor id resolves `SFMClientTextEditorConfig`; the live fixture intentionally
used its configured v1 editor. The shared editor panel seam now provides
responsive narrow-panel geometry, pose-aware nested clipping, resize-stable
Text Editor v3 camera anchoring, panel-local close semantics, and visible typed
save rejection diagnostics. Successful saves publish one expected-revision
`LocationSet`; invalid, unauthorized, stale, or one-target-rejected requests
leave every prior location intact and never grant filesystem authority merely
because text named a path.

**User-testing boundary correction (2026-08-16):** X-8a proved that projection
actions remained registered and directly invokable after removing the header
toggle; it did not prove that the production command palette explores the deep
continuation far enough to display `sfm:list`/`sfm:small_icons` after the exact
`sfm:explorer/view/set` prefix. It also did not cover body-versus-location
border rendering, wheel event-to-frame latency, fuzzy filtering, extension
icons, or an absolute-path label axis. X-8b owns those observed gaps; X-8a must
not be cited as proof they already work.

Focused tests cover narrow/wide geometry, exact title/tooltip-adjacent
narration/copy content, mouse/keyboard action parity, all placement/editor
command forms, literal/heterogeneous/selection-id/Unicode/composed byte-exact
documents, atomic replacement, stale/unauthorized retention, multi-target
rollback, projection action access, nested scissor intersection, camera
re-anchoring, and panel save/close behavior. Canonical validation passed:

```text
sfm-propagate-changes.exe run compile --branch 1.19.2 --wait-for-build-lock: passed
sfm-propagate-changes.exe test run --branch 1.19.2 --wait-for-build-lock: passed
sfm-propagate-changes.exe puppet run sfm:title_screen_external_cli_lazy_explorer --branch 1.19.2 --wait-for-build-lock: passed
```

The final inspected split-view proof and its machine-readable companion
artifacts are rooted at:

```text
platform/minecraft/build/sfm-toolchain/artifacts/game-test-preview/runs/sfm-title_screen-20260812-200141-912
```

Its manifest records dirty-source fingerprint
`blake3:29bcdb590792d0e4843c46820be09de9acb80e62`; figure 7 visibly contains
both explorer panels and the exact internal selection-backed location in the
preferred editor without drawing or widget leakage across panel boundaries.

**Clean checkpoint and scale-matrix evidence (2026-08-13):** Commit
`7cfd4b138` (`Add typed lazy explorers and external control CLI`) checkpoints
X-1 through X-8a after canonical compile, the full Java suite, Rust checks, and
the focused live journey had passed. Commit `dbf6bf344` (`Add explorer GUI
scale matrix coverage`) gives the explorer journey a reusable 3840x2130
declared viewport profile and makes each repeated variant dispose its live
explorer sessions while retaining process-wide resolver authority. Its I/O
assertions are relative to the variant baseline, so retained counters cannot
produce false failures.

```text
sfm-propagate-changes.exe puppet run title_screen_external_cli_lazy_explorer --branch 1.19.2 --variant declared --wait-for-build-lock
SFM_GAME_PUPPET_COMPLETE failed=0 total=9
```

The inspected run is rooted at
`platform/minecraft/build/sfm-toolchain/artifacts/game-test-preview/runs/`
`title_screen_ext-20260813-162233-680`. It contains figure 7 at numeric GUI
scales 1 through 8 plus Auto (effective scale 8), always showing the primary
explorer, secondary explorer, and exact location document in the configured
preferred editor. Panel boundaries remain stable. The matrix also makes a
then-pending C-3 acceptance concern explicit: the preferred-v1 editor kept a
centered bounded form at low GUI scales and therefore left substantial panel
space unused. C-3 closed that concern in `6dc3d2d04`; its inspected
`title_screen_explorer_open_sfm_java` Auto-plus-scales-1-through-8 matrix uses
the panel allocation for the real read-only `SFM.java` Text Editor v3 view.

### [x] X-8b Repair explorer projection discovery and interaction fidelity

**Work:** Deliver XEXP-20 through XEXP-25 without changing semantic explorer
membership or beginning picker X-8. Add an ordered extension/theme presenter
between the file presenter and paper fallback; preserve chest directories,
item-registry presentation, custom contributor precedence, chat-component
labels, and list/small-icon geometry. Apply XD-9's goal-scoped cocoa-beans
mapping through the contribution surface rather than a renderer special case.

Repair the command-palette/Brigadier continuation path rather than adding more
hard-coded top-level fuzzy aliases. At
`sfm action invoke sfm:explorer/view/set <selector> `, expose every registered
view (`sfm:list`, `sfm:small_icons`) with stable labels/descriptions and prove
that small-icons on `registry://minecraft/item/` renders actual ItemStacks.
Apply XD-10's independent path-display axis so name, relative, and absolute
labels compose with either view.

Refactor explorer geometry into header, inset body viewport, status/filter, and
border layers. Render the body focus rectangle only for `KeyboardFocus.BODY`,
include all four edges, and prevent selected/hovered/icon cells from covering
it at any supported width/GUI scale. Address-bar focus owns only address-bar
chrome.

Add self-contained `sfm:explorer/filter/set <explorer-selector> <query>` and
`sfm:explorer/filter/clear <explorer-selector>` actions plus a focusable,
narrated filter control. Use the shared fuzzy scorer, deterministic stable keys,
and XD-11's lazy-materialization boundary; preserve expansion and selection by
path when ranks change. Instrument every wheel callback, requested delta,
model-row transition, and first frame displaying it. Apply events in receipt
order and fix the measured delay without trailing debounce or input loss.

**Validation:** Presenter tests cover `.java`, ordinary files, directories,
case/compound/unknown extensions, missing theme items, custom precedence, and
list/small-icons. Palette tests start from the exact deep command prefix and
prove registered view/path-display candidates plus Brigadier execution. Render
geometry tests cover LOCATION/BODY/unfocused state, four edges, row inset,
narrow panels, selected first/last rows, and the GUI-scale matrix. Fuzzy tests
cover typo/subsequence scoring, empty/clear, selection retention, newly loaded
pages, zero filter-caused resolver reads, and multi-explorer selectors. Scroll
tests inject one and several callbacks in one frame and require ordered visible
steps with event-to-frame telemetry; Down-key repeat is retained as a control.

```pwsh
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMExplorerFilePresentationTests --wait-for-build-lock
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMClientActionPaletteSuggestionTests --wait-for-build-lock
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMExplorerPanelInteractionTests --wait-for-build-lock
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMExplorerFilterTests --wait-for-build-lock
```

**Completion criteria:** A Java file has the approved extension icon; deep
palette completion exposes view and path-display values; the item registry can
visibly use small ItemStack icons; focus chrome belongs to exactly one child
and cannot be overpainted; fuzzy filtering is responsive and truthful about
lazy scope; and each wheel event changes the next observable projection without
a delayed aggregate jump.

**Completion evidence (2026-08-16):** Java commit `2c013aa66` plus acceptance
revision `776c2c4f8` add the contributed `.java` cocoa-beans icon with safe
paper fallback, deep view/path-display completions, ItemStack small-icons
projection, four-edge inset focus chrome, immediate ordered wheel application/
telemetry, and fuzzy filtering over current lazy materialization. Focused tests,
canonical compile, and the full Java suite passed. The clean declared Auto plus
GUI scales 1 through 8 `title_screen_explorer_interaction_fidelity` matrix
passed at
`platform/minecraft/build/sfm-toolchain/artifacts/game-test-preview/runs/`
`title_screen_exp-20260816-185130-819`. Every one of its nine machine reports
proves the exact deep completion set, `LIST` plus `ABSOLUTE_PATH`, incomplete-
materialization disclosure, zero filter-triggered relation revision/resolver
I/O, ordered visible wheel steps `1,2,3`, final row `3`, and exclusive focus
ownership. Representative GUI-scale-1 and Auto frames plus Auto deep-view,
deep-path-display, small-icons/body-focus, location-focus, and ordered-wheel
frames were visually inspected. Picker X-8 remains unstarted.

**2026-08-17 goal reconciliation:** XEXP-20 through XEXP-25 are also marked
complete in the contextual plan's 35-requirement acceptance ledger. Their
independent EXP matrix remains the authoritative live proof; the completed
source-navigation SRC run does not broaden X-8b or begin picker X-8.

### [x] X-8c Preserve root-to-match ancestry during lazy explorer filtering

**Completion notes (2026-08-18):** Filtering now projects the minimal
already-materialized root-to-match hierarchy instead of flattening matches.
Shared ancestors are merged without cycles, context rows remain distinct from
actual fuzzy matches, original depth and deterministic descendant ordering are
retained, and status evidence separates match, visible-row, and context counts.
The query performs no resolver I/O or eager recursion and clearing it restores
the prior expansion, selection, and scroll state.

Projection/filter/panel interaction tests pass in the 1,187-test Java
acceptance run. The Auto plus GUI-scales 1 through 8 explorer matrix passed at
`platform/minecraft/build/sfm-toolchain/artifacts/game-test-preview/runs/`
`title_screen_exp-20260818-175027-577`, proving the complete materialized chain
to `SFM.java`, truthful counts, visual match/context distinction, and zero
filter-triggered relation work. The shared closure is implementation commit
`d6947fa84` plus the installed revision/hash recorded under CLI-AST 0.12.4.

**Manual evidence and changed requirement (2026-08-18):** Filtering the SFM
source explorer for `SFM` currently shows a flat fuzzy-ranked list containing
rows such as `sfml`, `sfm`, and `SFM.java`, while hiding the materialized parent
chain that explains where each result lives. This matches X-8b's explicit flat
ranking implementation, but natural testing established that preserving
hierarchical context is more useful. X-8b remains complete for its original
contract; X-8c owns this intentional projection change.

**Work:** Continue matching only the current lazy materialization: changing a
query must cause zero resolver reads, must not recurse an arbitrary filesystem,
and must retain the existing incomplete-materialization disclosure. Project a
minimal hierarchy containing every fuzzy match plus every already-published
ancestor connecting it to an explorer root. Ancestors are force-revealed only
in the filtered projection and do not mutate persisted expansion state. Merge
shared ancestry without duplicates or cycles; distinguish actual matches from
context rows visually and in narration; retain original depths; and use each
subtree's best descendant score plus canonical path as deterministic ordering.

Track `matchCount`, `visibleRowCount`, and `contextAncestorCount` separately so
status text does not call ancestors matches. Preserve selected path even when
temporarily hidden, keep hoist/group/path-display/view axes composable, and
restore the exact pre-filter hierarchy/scroll/selection when the filter clears.

**Validation:** Extend `SFMExplorerFilterTests` with the screenshot topology:
the `SFM.java` match must include its complete materialized root-to-file chain,
while `sfml` and `sfm` matches retain their own ancestry. Cover shared parents,
multiple roots, ancestor also matching, cycles, collapsed parents, hoisted
single root, flat group mode, deterministic ranking, selection/scroll restore,
newly published pages, truthful counts, and zero filter-caused resolver I/O.
Extend the explorer puppet and visually inspect Auto plus GUI scales 1..8.

```pwsh
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMExplorerFilterTests --wait-for-build-lock
sfm-propagate-changes.exe puppet run title_screen_explorer_interaction_fidelity --branch 1.19.2 --variant declared --wait-for-build-lock
```

**Completion criteria:** A filtered hierarchy always explains each visible
match's location using materialized ancestors; actual-match and context counts
are honest; filtering performs no resolver I/O; and clearing restores the
unfiltered explorer state exactly.

### [ ] X-8 Compose pickers as selection destinations

**Work:** Promote set-consuming explorer/selection action boundaries from the
first slice's literal concrete paths to resolved set-valued path expressions,
including the reserved selection create/member/derive/history actions above.
Then adapt item/icon picking to a destination selection with acceptance,
cardinality, remove, confirm, and cancel actions. Allow any explorer to feed it;
navigation and expansion must never clear picked members. Preserve exact
literal widget actions while proving expression-valued all-or-none mutation,
exactly-three, mixed incompatible roots, multi-explorer drag/click input, and
cancel/submit.

**Validation:** focused selection-destination tests plus a live item/icon picker
puppet.

**Completion criteria:** The item picker no longer owns an incompatible
single-row selection mechanism; picked items survive hierarchy navigation and
confirmation is enabled strictly by destination policy.

### [ ] X-9 Adapt Text Editor v3 and review comments to the shared algebra

**Work:** Project ordered editor cursor ranges into document-region paths and
named selections without losing cursor direction/primary identity. Add a
review adapter from shared pinned expressions to
`SFMReviewSessionV1.SelectionRule`, retaining snapshot/hash/migration evidence.

**Validation:** editor multi-cursor/range fixtures, shared set operations across
documents, existing review-session compatibility tests, and comment migration
proof.

**Completion criteria:** Editor, explorer, and comments share one path/set
algebra while each domain retains required operational/pinning semantics.

### [ ] X-10 Separate pane, panel-entry, and component identities

**Work:** Introduce stable pane ids for visible split leaves, retain distinct
stacked panel-entry ids and explorer/component ids, and migrate public selector
terminology. Prove nested split/stack examples and Ctrl+number versus Ctrl+Tab.

**Validation:** workspace-layout selector tests for `[[1,2],3]`,
`[1,2;3,4]`, and `[1,[2;3],4]` plus live focus navigation.

**Completion criteria:** “Second pane” cannot be mistaken for the second hidden
or visible stacked content entry, and every selector/action reports its domain.

### [ ] X-11 Add deferred visual drag interactions

**Work:** Add action-backed Alt+drag pane relocation with highlighted candidate
geometry, then explorer root/node drag with Ctrl=copy, Shift=move, and
right-drag=ask. These operations change explorer membership/layout only, never
underlying filesystem or registry objects.

**Validation:** drag geometry, cancel, modifier, multi-pane, stacked-entry, and
no-underlying-mutation puppets.

**Completion criteria:** Visual previews match resulting layout/membership and
every drag gesture has an equivalent registered semantic action.

## First-goal acceptance matrix

| Surface | Support in first goal | Required proof | Evidence |
| --- | --- | --- | --- |
| Minecraft 1.19.2 Java | Supported baseline | Focused tests, full suite, canonical compile, live puppet | 794/794, compile pass, live run `sfm-title_screen-20260812-182322-010` |
| `platform/cli/sfm` Rust | Supported | format/clippy/tests/codegen parity/help snapshots | `check-all.ps1` passed; 41/41 plus generated-Java parity |
| Gradle-only contributor path | Supported without Rust execution | canonical Java compile consumes checked-in generated bindings | Passed through `sfm-propagate-changes.exe run compile` |
| Filesystem resolver | Supported read-only for explicitly supplied roots | UTF-8, containment, symlink, one-level paging, cancellation, read bounds | Focused tests plus `artifact_resolver-io.json`; 0 render-thread violations |
| Item-registry resolver | Supported read-only | heterogeneous-root presentation and item icon/chat component evidence | Figure 2 and Figure 6 show contributed ItemStack presentation |
| Persisted selections/workspaces | Explicitly unsupported in first goal | no persistence file/schema introduced; restart does not claim restoration | Confirmed absent; state remains process/session scoped |
| Later Minecraft branches | Deferred | no propagation during goal; later merge/audit goal required | No propagation performed |

## Overall completion criteria

- [x] X-8b proves extension-specific file presentation, deep projection/path-
  display completion, truthful fuzzy filtering, coherent four-edge child focus
  chrome, and ordered low-latency wheel response without changing lazy
  membership semantics.
- [x] Every active guidance id has task and validation evidence or remains
  explicitly assigned to X-8b or X-8 through X-11.
- [x] Canonical long-form direct explorer commands and registered actions agree;
  no implicit explorer target or built-in short alias exists.
- [x] Concrete paths, path expressions, entity selectors, selections, child
  relations, explorer sessions, panes, and panel entries remain distinct types.
- [x] File and registry roots coexist in one generic explorer.
- [x] Broad roots load lazily with bounded pages, cancellation, virtualization,
  and no render-thread filesystem enumeration.
- [x] Refresh never blanks old children before successful replacement data and
  cannot apply stale generations.
- [x] Versioned selection set algebra and undo-capable history are proven.
- [ ] X-3a makes that immutable history fully navigable as a non-destructive
  undo tree: new work after undo retains all old children, ambiguous redo is
  explicit, and pinned descendants remain reachable.
- [x] Multi-target mutations are all-or-none and return per-target evidence.
- [x] The live puppet proves open-if-none, exact-id failure, single-root hoist,
  heterogeneous roots, lazy expansion, atomic refresh, and `all` targeting.
- [x] Full Java/Rust validation and canonical compile pass on current sources.
- [x] Plans/changelog accurately describe observable behavior; no propagation,
  publication, or release occurs without a later explicit goal.

## Risk register

| Risk | Guardrail |
| --- | --- |
| A selector silently changes meaning with focus or collection mutation | Capture stable ids and repository generation once; revalidate before publication; explicit `focused` syntax |
| One failing target leaves a partial multi-target mutation | Preflight/stage all and publish all-or-none on the game thread; fault-injection tests |
| Empty targeting unexpectedly opens UI or exact ids target replacements | Selector evaluation is pure; explicit `--if-no-match`; reject exact-id plus open-new |
| File path is corrupted through `ResourceLocation` or lossy Unicode conversion | Separate `SFMPath`; strict UTF-8 boundary; cross-language round trips and invalid-input tests |
| A displayed logical path grants host filesystem authority | Resolver-issued capabilities and root containment; no ambient/path-title inference |
| Opening `C:\` recursively enumerates the drive | Immediate-child pages only; IO counters assert zero descendant reads before expansion |
| Refresh clears the tree or stale results overwrite newer rows | Fetch-before-publish, immutable relation revisions, generation/cancellation checks, failure retention |
| Paging undermines atomic refresh | Retain old generation until fresh first page; atomically publish prefix+continuation; append pages by revision |
| Explorer navigation destroys picked values | Destination selection is separate from navigation cursor/expansion; retention tests |
| Selection undo destroys provenance needed by comments/collaboration | Immutable revision ledger and explicit heads; pinned addresses for reproducible consumers |
| A linear redo stack hides an old branch after undo-then-mutate even though revision objects still exist | X-3a parent/child adjacency, child enumeration, named/pinned heads, explicit checkout, undo-undo-do fixture, and no implicit GC |
| Selection set loses hierarchy parent ownership | Store parent/child relation separately; derive child path sets from relation range |
| View/sort/group choices mutate semantic membership | Separate projection object and membership/relation repositories |
| Extension icons replace domain presentation or make every file a special case | Ordered contributed extension/theme presenter after domain-specific handlers and before paper fallback; assert actual ItemStack ids and precedence |
| Existing registered view ids remain unreachable from a deep palette prefix | Exercise the exact `sfm action invoke sfm:explorer/view/set ...` frontier through the production continuation/ranking path; do not add top-level aliases |
| Absolute-path labels are encoded as a mutually exclusive view | XD-10 independent path-display axis and cross-product tests with list/small-icons |
| Focus-border repair leaves stale side edges or lets rows cover another edge | Child-specific focus state, inset body bounds, explicit render layer order, and first/last selected-row pixel assertions |
| Fuzzy filtering accidentally walks a drive or lies about exhaustive results | XD-11 zero-I/O local filter with visible materialization completeness; recursive traversal remains B-3 |
| Wheel smoothing/debounce hides intermediate events or a render stall is misdiagnosed as input coalescing | Sequence-numbered callback/model/frame telemetry, ordered event assertions, and evidence-selected repair rather than speculative timers |
| Generic explorer becomes least-common-denominator UI | Contributed resolver/presentation/view/group capabilities with explicit unavailable reasons |
| Existing file/item puppets regress during cutover | Keep adapters until parity; focused tests and migrated live artifacts before deleting old paths |
| Generated Vox changes break Gradle-only contributors | Deterministic checked-in Java generation and canonical compile without Cargo |
| Plan expands into editor/comments/layout before foundation is stable | X-8 through X-11 are explicit later phases and excluded from first-goal scope |

## Source and implementation references

- `docs/AGENTS.md`
- `docs/tasks/sfm in-game control cli plan.md`
- `docs/tasks/contextual input actions and addressable explorer plan.md`
- `docs/tasks/global comment selection and review sessions plan.md`
- `docs/tasks/draw editor document regions and commands plan.md`
- `platform/cli/sfm/src/cli.rs`
- `platform/cli/sfm/src/protocol.rs`
- `platform/cli/sfm/src/discovery.rs`
- `platform/minecraft/src/main/java/ca/teamdman/sfm/client/control/SFMClientControlServer.java`
- `platform/minecraft/src/main/java/ca/teamdman/sfm/client/control/SFMClientThreadGate.java`
- `platform/minecraft/src/main/java/ca/teamdman/sfm/client/action/SFMClientActionContext.java`
- `platform/minecraft/src/main/java/ca/teamdman/sfm/client/screen/file_explorer/SFMFileExplorerSource.java`
- `platform/minecraft/src/main/java/ca/teamdman/sfm/client/screen/file_explorer/SFMPathFileExplorerSource.java`
- `platform/minecraft/src/main/java/ca/teamdman/sfm/client/screen/file_explorer/SFMFileExplorerEntry.java`
- `platform/minecraft/src/main/java/ca/teamdman/sfm/client/screen/file_explorer/SFMFileExplorerModel.java`
- `platform/minecraft/src/main/java/ca/teamdman/sfm/client/screen/item_picker/SFMItemPickerModel.java`
- `platform/minecraft/src/main/java/ca/teamdman/sfm/client/screen/explorer/SFMExplorerPanel.java`
- `platform/minecraft/src/main/java/ca/teamdman/sfm/client/screen/explorer/SFMExplorerPanelModel.java`
- `platform/minecraft/src/main/java/ca/teamdman/sfm/client/screen/explorer/SFMExplorerPresentationRegistry.java`
- `platform/minecraft/src/main/java/ca/teamdman/sfm/client/action/SFMExplorerAction.java`
- `platform/minecraft/src/main/java/ca/teamdman/sfm/client/screen/workspace/SFMWorkspaceLayout.java`
- `platform/minecraft/src/main/java/ca/teamdman/sfm/client/screen/workspace/SFMWorkspacePanelId.java`
- `platform/minecraft/src/main/java/ca/teamdman/sfm/client/review/session/SFMReviewSessionV1.java`

## Completed X-1 through X-7 goal record

> Complete X-1 through X-7 in
> `docs/tasks/typed selections relations and lazy explorers plan.md` as one
> selection-backed lazy heterogeneous explorer vertical slice: freeze the
> cross-language typed path/path-expression and set-valued selector contracts;
> implement versioned selections and child relations; replace recursive nested
> snapshots with a generic lazy explorer; route explorer semantics through
> explicit registered actions; add direct canonical `sfm explorer ...` Vox/CLI
> commands; and prove the complete behavior in a self-orchestrating live
> puppet. Record focused/full validation, compile, artifacts, public grammar,
> completion notes, and any reversible gate resolution. Do not begin X-8
> through X-11, fuzzy search, source-file editing/navigation, propagation,
> publication, or release work during this goal.

All required outcomes below are now observable in the completion evidence above:

1. With one compatible game and no explorer open, running
   `sfm explorer root add focused . --if-no-match open-new` from the repository
   opens exactly one generic explorer and shows the canonical repository root.
   Text/JSON output identifies the game, explorer, path, request, and resulting
   selection/relation revisions.
2. Adding `registry://minecraft/item/` to that exact explorer produces one
   heterogeneous explorer. It changes from hoisted single-root presentation to
   two visible parent roots and retains ItemStack/chat-component presentation.
3. Opening a broad/deep filesystem fixture reads no descendants. Expanding one
   node fetches only one bounded immediate-child page off the render thread;
   continuation, cancellation, and query-count artifacts make that fact
   computationally visible.
4. During a delayed refresh, the previously published rows remain visible.
   Success swaps the fresh prefix/continuation atomically; failure or a stale
   generation retains old rows and shows a diagnostic.
5. Two explorer sessions can diverge independently. A direct `all` selector can
   change both projections (including name/extension/icon sort choices), while
   a missing exact id creates nothing and one invalid member of a captured
   multi-target set leaves every target unchanged.
6. Focused tests prove independent named selections, add/remove/union/
   intersection/difference, immutable live and pinned revisions, undo/redo,
   relation parent ownership, and navigation that does not clobber semantic
   selection.
7. `sfm help` exposes direct long-form explorer commands, no built-in short
   aliases, and no requirement to pass ordinary explorer operations through
   `sfm invoke`. Rust/Java fixtures agree on strict UTF-8 path parsing,
   canonical printing, selectors, and typed outcomes.
8. Focused Rust/Java tests, the canonical full Java suite, canonical compile,
   the live puppet, machine artifacts, screenshots, and plan/changelog notes
   all agree. No explorer persistence/global-workspace schema is introduced.
