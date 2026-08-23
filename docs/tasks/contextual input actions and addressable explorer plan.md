# Contextual input, action ownership, and addressable explorer plan

**Plan status:** Active; C-4a through C-11 and linked CLI-AST Phases 0.11
through 0.12.4 are complete; the bounded REVEAL-3 subset of B-4 and CTXREF
subset of B-5 plus B-5a are complete; B-0 is complete; broader Phase B and
B-6 remain
**Primary implementation root:** `D:\Repos\Minecraft\SFM\repos2\1.19.2`
**Coordinating release plan:** `docs/tasks/release checkpoint and slim artifact plan.md`
**Selection/explorer foundation plan:** `docs/tasks/typed selections relations and lazy explorers plan.md`
**Last updated:** 2026-08-22
**Intent audit:** Passed and post-compaction re-audited 2026-08-22 against the user's command-boundary/history, argument-frontier, required-usage, ordinary undo/redo, and temporal-canvas report; the 2026-08-16 symbol-navigation audit remains retained below

## How to update this plan

- `[ ]` Not started
- `[~]` In progress
- `[x]` Complete
- `[!]` Blocked

Keep at most one integration slice in progress. Parallel work is permitted only
where the topology below gives owners disjoint files or pure models. Update a
work item's heading and its completion notes together. Record decisions,
commit ids, exact SFM CLI commands, focused test outcomes, and live artifact
run ids beside the item they prove. Do not use Gradle directly, propagate to
later Minecraft branches, publish, or push merely because an item is complete.

## Purpose

Make every SFM-owned interactive surface predictably keyboard-navigable and
action-addressable. A focused control must participate in Minecraft-like focus,
narration, activation, and Tab traversal; its active keyboard-usage situation
must be stable and inspectable; and user bindings must invoke semantic actions
rather than hidden coordinate mutations. Build on that foundation to evolve
the current SFM Shortcuts list into an address-driven explorer of actions,
bindings, live UI elements, registries, entries, and bounded path devices.

The completed first user-visible correction was the Rust terminal panel: its
disconnected Start/Retry control, Presentation selector, and terminal
properties controls now participate in the shared Minecraft-like widget/focus
tree rather than a terminal-only focus mechanism. K-1 through K-7 retain the
contract and evidence for that completed foundation.

K-8 is the next keyboard-discovery extension: the command palette will expose
an explicit button that captures a physical hotkey/sequence without executing
it, then reuses the palette's result viewport to show effective matches,
conflicts/shadowed matches, and clearly labelled related bindings in the
captured origin keyboard-usage context. Exact effective matches rank first; a
captured `Ctrl+Alt+L` may also show a `Ctrl+L` binding below them as a related
modifier-subset result, but must not falsely claim that `Ctrl+L` would fire for
the extra-Alt input. Query mode is inspection-only: selecting a result opens
action details and must not invoke the matched action.

The next user-visible extension is contextual search and action discovery.
Ctrl+Shift+N opens a command-palette-backed fuzzy search over bounded file
paths seeded from the user's current panels rather than a disk root;
Ctrl+Shift+E reveals an addressable focused object in the appropriate explorer;
and Alt+Enter opens a constrained palette of actions derived from the complete
focused context. Brigadier remains the parser and executor while the palette
gains an asynchronous, cancellable candidate layer for data that cannot be
represented as a small static literal tree.

The concrete developer destination remains opening SFM Java files in Text
Editor v3 and invoking jump-to-definition from the editor cursor. The route to
that outcome changed on 2026-08-12: it now begins with generic, panel-local,
selection-backed lazy explorers rather than a globally persisted
`sfm:explorer/workspace`. The authoritative foundation and immediate vertical
slice live in `typed selections relations and lazy explorers plan.md`. The
first symbol provider still reuses the existing SFM CLI Java-analysis/index
machinery asynchronously; it does not guess definitions from token text or
perform source analysis on Minecraft's render thread.

## Spatial semantic surface relationship — 2026-08-17

`docs/tasks/spatial semantic surfaces outlinks and capability presenters plan.md`
is authoritative for the next source-navigation layer: typed canvas/document/
syntax regions, zero-to-many outlink relations, exhaustive and sampled spatial
coverage, destination-region projections, reciprocal definition/reference
evidence, editor location/history/toast follow-ups, generalized capability
selection, and bounded rich previews.

This plan remains authoritative for gestures, keyboard situations, constrained
command-palette choices, action drafts, and address/explorer integration. Its
completed C-7 through C-11 evidence is the baseline, not proof that every
canvas glyph is semantically classified. Future hover/F12/Alt+F7 work joins
the spatial plan at SS-4 and must replace the current identifier-only hit gate
without introducing another gesture-specific parser. K-8 hotkey discovery is
independent and remains owned here.

## Selection-backed explorer supersession — 2026-08-12

The earlier C-1/C-2 proposal treated “workspace” as one persisted multi-root
catalog shared by explorer panels. Subsequent design work rejected that as too
strong and overloaded: this codebase already uses workspace for the complete
split/stack panel host, while each generic explorer should own a location,
projection, navigation state, and explicit set-valued identity.

`docs/tasks/typed selections relations and lazy explorers plan.md` is now
authoritative for typed UTF-8 content paths, path expressions, entity
selectors, versioned selections, parent/child relations, heterogeneous lazy
explorers, picker destinations, direct `sfm explorer ...` remoting, and later
pane terminology/drag behavior. Its X-1 through X-7 replace the active C-1/C-2
global-workspace implementation path. Historical C-1/C-2 text remains below
only to explain the superseded proposal and must not be implemented as a
provisional persistence format.

C-3 through C-6 retain their user outcomes—open source read-only and jump to
definition—but must consume the selection-backed explorer/path contracts after
X-1 through X-7. Native folder selection, folder drop, picker confirmation,
editor range selections, and comment selection integration are later consumers
of the shared selection substrate, not reasons to recreate a global explorer
workspace.

## Authoritative user guidance ledger — 2026-08-05

| ID | Active guidance | Required plan consequence | Superseded by |
| --- | --- | --- | --- |
| WNAV-1 | The disconnected Rust-terminal Start/Retry control and Presentation dropdown must behave like Vanilla Minecraft widgets and be keyboard navigable. | K-1 introduces a panel child-widget host; K-2 migrates both controls and proves click, Tab, Shift+Tab, Enter/Space, focus visibility, and narration. | — |
| WNAV-4 | A disconnected Rust terminal is a landing scene, not a connected terminal with hidden/disabled pieces. Its widget tree contains only Start/Retry; the connected scene exclusively owns the viewport, Presentation selector, and option rows. Background connection attempts must not flash transient text, and the latest bounded connection results remain visible until a meaningful later event replaces them. | K-2's 2026-08-06 state-model correction replaces visibility-flag composition with disjoint scene-owned child trees and a retained connection-event history. | WNAV-1's implication that Presentation appears while disconnected |
| WNAV-2 | Terminal-properties controls currently bypass normal Minecraft Tab navigation; panels/screens must either support the native mechanism or adapt/pump it seamlessly. | K-1 owns a version-adapted focus/event bridge; K-2 converts every tuning control rather than special-casing terminal properties keys. | — |
| WNAV-3 | Prefer an automated static and/or runtime way to detect SFM screen elements that lack keyboard-navigation consideration. | K-5 defines an enforceable SFM-owned action-element inventory, runtime assertions, and a bounded static audit; unsupported detection claims must be documented honestly. | — |
| KCTX-1 | Introduce a registry of keyboard-focus/usage situations with stable identifiers users can select when relating hotkeys to actions. | K-3 adds `Registry<SFMKeyboardUsageSituation>`, contextual binding storage, active-situation resolution, precedence, and conflict rules. | — |
| KCTX-2 | A mixed panel may expose a terminal input widget as `sfm:terminal` while ordinary controls use `sfm:default`; the screen, panel, and LWJGL/Forge hooks must agree on one hierarchy. | K-1 exposes focused-child identity; K-3 resolves an ordered active situation ancestry before matching and then forwards unconsumed input to the child. | — |
| KCTX-3 | Distinguish safe defaults active only in an SFM-owned context from arbitrarily user-added global mappings that may conflict with Minecraft or other mods. | K-3 has explicit contextual versus global situations, displays scope, and ships no new conflict-prone global default merely to imitate another application. | — |
| KBIND-1 | Add a default binding for `sfm action invoke sfm:panel/close`. | K-3/K-4 provide a stable overridable default; the working default is Ctrl+Shift+W in the SFM workspace context, matching Microsoft Terminal close-pane. | — |
| KBIND-2 | Add panel-resize and panel-duplicate actions using the Microsoft Terminal hotkey defaults. | K-4 adds hierarchical actions, pure layout behavior, and defaults from the pinned local Terminal `defaults.json`: Alt+Shift+arrows resize; Alt+Shift+minus duplicates below; Alt+Shift+plus duplicates right. | — |
| KBIND-3 | Preserve the existing generic `KeyOrChord -> SomeActionString` model; when the action string still has required arguments, open the command palette for completion rather than failing or inventing arguments. | K-3 migrates the existing dispatch behavior and tests contextual incomplete drafts. | — |
| KBIND-4 | The terminal F3 behavior must appear in SFM Shortcuts as a real binding/action. | K-3 registers the diagnostic-choice behavior as a semantic action, seeds F3 contextually, and removes the hard-coded multiplexer-only path; K-5 audits the remaining SFM-owned raw handlers. | — |
| KBIND-5 | Panel-scale increase is the physical Ctrl+Equal relationship, shown as separated key tokens (`Ctrl =`) rather than the ambiguous `Ctrl++`; increase/decrease/clear each ship one main-row default rather than a visually duplicated keypad pair. | K-3's 2026-08-06 correction updates immutable defaults and fingerprints, proves the exact event match, and adds shared pink read-only keycaps; K-6 reuses them as focusable/removable capture controls. | Earlier main/keypad parity and logical-plus display wording |
| KBIND-6 | `sfm:panel/scale/clear` restores auto/inherited. If its effective scale is N, increase must select N+1 and decrease must first select explicit N before later numeric decrements; the null/auto state must never be treated as zero. | Release P-2.5 owns the scale-state transition; K-7 proves the contextual Ctrl+0/Ctrl+=/Ctrl+- route uses the same action semantics. | — |
| KBIND-7 | Scale changes should communicate state without permanently consuming panel space: show fading `gui scale N` or `gui scale auto (N)` toasts, and repeat the toast with a slight shake when increase/decrease is already at a numeric boundary. | Release P-2.6 owns and proves the completed scale-feedback producer/single-toast baseline; spatial-semantic NX-4 owns the later generic addressable queue/input/lifecycle evolution and must migrate this producer without regressing fade, effective-auto text, boundary feedback, or shake. | — |
| KUI-1 | `sfm:keybindings/manage` needs sortable Name and Binding Count headers. | K-6 adds keyboard-focusable headers, ascending/descending state, stable tie breaks, and filter/scroll preservation. | — |
| KUI-2 | Binding entry needs a focusable capture mechanism that records the entered mapping. | K-6 introduces a dedicated capture widget integrated with normal focus and dispatch suspension. | — |
| KUI-3 | Triple Escape should back out of capture; each captured chord element is a keyboard-focusable button that removes that element when activated. | K-6 defines the time-bounded cancel sequence, removable stroke chips, Save/Cancel focus targets, and mouse/keyboard parity. | — |
| KUI-4 | The command palette needs an explicit, focusable button that grabs keyboard attention for hotkey lookup rather than interpreting the next key as palette text or an action. | K-8 adds a discoverable “Find actions by hotkey” capture control that shares the physical capture primitive but uses an inspection-only result mode. | — |
| KDISC-1 | While hotkey lookup is active, physical key events (including modifier/key combinations and supported multi-stroke sequences) are captured by the lookup surface; they do not type into the command draft, invoke actions, or leak to Minecraft/terminal input. Escape cancellation, completion, focus loss, and key-release cleanup are explicit. | K-8 adds a capture-session state machine around `SFMKeySequenceCapture`, freezes the keyboard-usage context at entry, and proves dispatch suspension/restoration on every exit path. | — |
| KDISC-2 | After capture, show actions whose bindings match the captured sequence, with exact matches before near/derivative matches. A captured `Ctrl+Alt+L` must be able to show a matching `Ctrl+L` binding below a direct `Ctrl+Alt+L` result rather than silently treating them as equal. | K-8 defines a pure deterministic matcher/ranker: exact physical sequence and modifier set first, then same-sequence eligible modifier-subset derivatives with an explicit relation label and distance; no arbitrary fuzzy key substitution is implied. | — |
| KDISC-3 | The result surface must explain why each action appears, including its binding, match relation/score, action identity, and keyboard-usage situation. Looking up a hotkey is not an invocation; result activation opens action details, and execution requires leaving lookup mode and using the ordinary explicit command path. | K-8 extends the shared palette suggestion model with typed lookup results, stable tie-breaking, frozen-origin-context eligibility, and an inspection-only details activation contract. | — |
| KDISC-4 | The feature must be testable without asking a human to press every key: pure capture/ranking/context tests cover exact, subset, no-match, chord, cancellation, focus-loss, and cleanup cases, while a live palette puppet proves the button, visible captured hotkey, ranked results, and non-invocation. | K-8 adds machine-readable lookup artifacts and a live witness alongside focused Java tests; the existing binding-management capture tests remain regression coverage for editing. | — |
| ACT-1 | Keyboard-drivable SFM behavior, including clickable/focusable buttons, should be backed by registered actions instead of mutating otherwise unreachable state directly. | K-2 converts terminal controls; K-5 inventories and migrates semantic SFM behaviors in bounded waves while retaining intrinsic text/pointer input as parameterized input actions. | — |
| ACT-2 | Public shortcut/action exploration must not be polluted by opaque `sfm:screen/mouse/click <x> <y>` bindings. Stable element identity such as `sfm:button/edit_manager_disk` may bridge a live control to its semantic action. | K-5 introduces action-element contributions with stable addresses and canonical action drafts; coordinate clicks remain automation input, not the user-facing semantic contract. | — |
| ACT-3 | A Manager Edit control should expose the semantic relationship between the live element, a contextual action such as `sfm:manager/disk/edit <manager-pos>`, and any bindings. | K-5 uses one Manager-screen action element as the non-panel proof that dynamic context and semantic action ownership work. Exact final id is a contract gate. | — |
| EXPL-1 | The current shortcuts surface is conceptually an Action Explorer: it should enumerate all known `KeyOrChord` responders and provide navigable links to owners, actions, and mappings. | A-4 projects actions, situations, bindings, and action elements into the existing explorer/panel language rather than maintaining a disconnected list forever. | — |
| EXPL-2 | Panels show properties of one object; explorers show many objects. A Registry Explorer should list known registries and expose type-specific outlinks, such as client action -> bindings or item -> corresponding blocks. | A-3/A-4 define generic explorer nodes plus resolver-provided properties/actions/outlinks. | — |
| ADDR-1 | Navigation requires addresses with resolver/protocol identity; introduce a registry of address resolvers and preserve the proposed fixtures `sfm:registry/minecraft/item/minecraft/stick` and `sfm:registry/sfm/client_actions/sfm/developer/open_text_editor` while finalizing an unambiguous grammar. | A-1 freezes typed `SFMAddress`/`SFMAddressQuery` values and `Registry<SFMAddressResolver<?, ?>>`; A-2b adds registry-entry resolution. | — |
| ADDR-2 | Preserve proposed forms such as `sfm:path/options.txt` and `sfm:path/c/tmp/a.txt`, but resolve them relative to an explicit device, which may be an SFM VFS, the current instance run directory, or another bounded contributed device. | A-1/A-2a separate logical addresses from device context, enforce containment, and never infer ambient filesystem authority from an address string. | — |
| ADDR-3 | SFM may assign addresses/adapters to existing Minecraft behavior it did not author; there is no orphan-rule restriction, and resolver context should bootstrap useful access to the environment in which it runs. | A-1 makes resolvers contributor-extensible and context-capability based; A-2b proves one Vanilla registry adapter without modifying Vanilla classes. | — |
| PANEL-OLD-1 | Earlier guidance chose move instead of duplicate and the release plan consequently prohibited duplicate-panel actions. | Preserve `sfm:panel/move/...`; mark the old no-duplicate prohibition superseded and add deliberate duplication only after D-1 closes its state semantics. | KBIND-2 |
| PLAN-1 | Atomize the message into achievable, uniquely addressable, durable work items using the resumable-implementation-plans protocol, and identify vertical slices. | This ledger, traceability table, contract gates, work items, validation, topology, and next-slice definition are authoritative. | — |

## Authoritative user guidance ledger — 2026-08-10 extension

| ID | Active guidance | Required plan consequence | Superseded by |
| --- | --- | --- | --- |
| FFILE-1 | Ctrl+Shift+N must provide fuzzy search across file paths through the existing command-palette experience. | B-3 registers one semantic open-file/search action, one contextual default binding, and a palette argument candidate source; no second search dialog is introduced. | — |
| FFILE-2 | File search must begin from useful context rather than a disk root. An open file explorer and an open text editor can both project Path-like context. | B-2 preserves independently addressable context projections from every relevant visible panel; B-3 derives bounded, labelled search roots and never enumerates an ambient drive/root. | — |
| FFILE-3 | The file explorer contributes its selected item path independently of any text-editor document path. | B-2 gives contributions stable origin identities instead of collapsing them to one current path; B-3 ranks focused context without discarding other visible-panel roots. | — |
| FFILE-4 | Path candidates should be able to stream into command-palette intellisense while Brigadier continues to interpret commands and supply ordinary suggestions. | B-1 adds a generation-tagged, cancellable candidate stream and deterministic merge/ranking layer around the existing Brigadier result. | — |
| FFILE-5 | The generic mental model is `gci -Recurse \| fzf`; determine whether that requires inventing an SFM pipe system. | D-7 and B-1 deliberately provide typed candidate producers/consumers in-process. A general command-pipe language is not required by, and is excluded from, the first slice. | — |
| REVEAL-1 | Ctrl+Shift+E may show an explorer and/or reveal the file represented by the focused panel. Explorer selection and editor documents must remain independently targetable. | D-10 freezes fallback behavior; B-4 adds explicit focus/open and reveal-address actions plus the approved contextual default. | — |
| REVEAL-2 | Reveal-in-explorer is a general address operation: paths go to a file explorer, item ids/queries go to an item explorer, and future domains can contribute their own explorer projection. | A-1/A-3 and B-4 route typed addresses/queries through resolver-provided explorer capabilities rather than a file-only `instanceof` ladder. | — |
| CTXA-1 | The old Ctrl+Space token action should evolve into a VS Code-like Alt+Enter lightbulb/pick-list experience using the command palette now that the palette is mature. | D-9 and B-5 add a semantic contextual-actions action and open the existing constrained palette/choice surface; migration and compatibility are explicit. | — |
| CTXA-2 | Replace one-to-one `TokenKind -> Runnable` behavior with a contributor registry that can map contextual inputs to zero or more action suggestions. | B-2/B-5 introduce typed context projections and a one-to-many `SFMContextActionProvider` registry that emits canonical action drafts, not callbacks. | — |
| CTXA-3 | A context such as `a.txt` may offer path-existence/content actions; a query such as `sfm:item:minecraft:*wood*` may offer a reveal-in-explorer draft. | A-1 distinguishes concrete addresses from address queries; B-5 preserves these examples as fixtures while D-11 freezes exact public action spelling. | — |
| CTXA-4 | Providers should supply action drafts and let the command palette request any missing arguments instead of building provider-specific argument UI. | B-1/B-5 reuse Brigadier completeness checks and the existing incomplete-draft path. | — |
| ECTX-1 | Program-editor context is the whole program string/document plus a position, not only the token under the cursor. | B-2 defines an immutable document snapshot/address and typed spatial context; B-5 adapts AST/token enrichment onto that context. | — |
| ECTX-2 | Editor position is intrinsically 2D, matching Text Editor v3/canvas semantics rather than a flat string plus positive integer. | D-8/B-2 preserve canvas coordinates and resolved text row/column where available; legacy offsets are derived adapters, never the authoritative context. | — |
| ECTX-3 | The same context architecture should later support querying arbitrary screen points/rays like HWYLA/Jade/WAILA, without assuming the ray always goes through screen centre. | B-2 defines typed coordinate-space/origin metadata; this future provider family is compatibility scope, not part of the first file-search implementation. | — |
| SCENE-1 | Older one-off developer opening actions should be consolidated into the typed `sfm:panel/open` scene grammar. | X-4/X-5 establish one generic explorer component; A-2c makes `sfm:panel/open sfm:explorer [path-expression]` the sole public opening surface and removes the old developer actions. | — |
| SCENE-2 | Historical intermediate proposal: separate `sfm:explorer/item`, `sfm:explorer/file instance`, and `sfm:explorer/file sfm_source` scene spellings. The later heterogeneous-explorer design makes content a path expression rather than an explorer type. | Preserve the three examples as provenance/negative migration fixtures. A-2c uses generic examples such as `sfm:panel/open sfm:explorer registry://minecraft/item/` and a resolver-authorized `file://...` path. It adds no compatibility aliases because the palette surface is unreleased. | XEXP-1, XEXP-2, and XEXP-9 in the typed selection/explorer plan |
| SCENE-3 | Cut over completely: the command-palette feature is unreleased, so do not preserve aliases, redirects, migration, or backwards compatibility for `sfm:developer/open_file_explorer`, `sfm:developer/open_instance_file_explorer`, or `sfm:developer/open_item_icon_picker`. | A-2c removes all three action registrations and exclusive support code, migrates tests/puppets, and asserts the old ids are absent from the registry/tree. | — |
| ELOC-1 | The generic explorer header currently wastes space on `SFM Explorer` and a textual `List`/`Small icons` toggle that restate visible facts. | Selection/explorer X-8a and A-2c replace both with one full-width semantic location control; projection modes remain action-discoverable without permanent prose. | — |
| ELOC-2 | Clicking the displayed explorer location should open it in the user's preferred text editor through the shared panel-opening behavior, not a bespoke inline input. | The location control is keyboard focusable/narrated and emits the same exact-explorer typed panel-open recipe for mouse and keyboard activation. The editor id is optional and defaults through `SFMClientTextEditorConfig`. | — |
| ELOC-3 | Viewing/editing a multi-root explorer location should expose its exact canonical expression, including internal backing-selection ids, so users can understand and manipulate the real system rather than a simplified facade. | Header tooltip/narration and preferred-editor content preserve `ExplorerSession.location().canonical()` byte-for-byte; only the visible glyph run may be truncated for geometry. | — |
| ELOC-4 | Location editing changes explorer session membership, not the host files named by the location, and must be atomic, authority checked, and stale-write safe. | Save carries expected explorer/location revision, resolves and preflights the complete document, and publishes one all-or-none action on the client thread. Invalid, unauthorized, or stale saves retain the prior location and report diagnostics in the editor. | — |
| ELOC-5 | Removing the header view toggle must not remove keyboard access or discoverability for view, sort, group, or hoist. | Existing semantic actions remain available through contextual actions, command palette, and keybinding discovery, with parity tests after the header cutover. | — |
| PLAN-2 | Preserve every atom, motivation, example, and open design question from the fuzzy-file/context-action proposal in the resumable plan before implementation. | This extension ledger and cutover addendum, three-pass audits, gates D-7 through D-14, contracts, A-2c/B-1 through B-6, topology, risks, and next-slice definition are authoritative. | — |

## Authoritative user guidance ledger — 2026-08-11 multi-root symbol-navigation extension

| ID | Active guidance | Required plan consequence | Superseded by |
| --- | --- | --- | --- |
| WSPACE-1 | Historical proposal: add one `sfm:explorer/workspace` panel holding several folders like a VS Code workspace. The desired multi-root experience remains, but a global persisted workspace is no longer the model. | The selection/explorer plan X-1 through X-7 implement panel-local generic explorer locations backed by selections; one root auto-hoists and multiple heterogeneous roots show their parents. | XEXP-1 through XEXP-14 in `typed selections relations and lazy explorers plan.md` |
| WSPACE-2 | A folder should be addable to an explorer through a native folder picker when available. | Retain D-15 as a later input adapter. It must invoke the same explicit selector/path action and may not introduce a workspace repository or separate tree. | XSEL-6/XEXP-12 define the shared destination/action substrate; native picker remains later |
| WSPACE-3 | Native picking must not be the only way to establish roots. | Direct typed path actions, external `sfm explorer root add`, and later folder drop all share the X-5/X-6 executor; tests never depend on OS-dialog automation. | XCLI-1/XEXP-12 |
| WSPACE-4 | Explorer roots are explicit bounded authority, not permission to traverse a drive, user profile, process working directory, or arbitrary parent. | X-1/X-4 normalize and validate explicitly supplied roots and enumerate only immediate requested children. No ambient-root fallback exists. | XPATH-1 through XPATH-7 and XREL-1 |
| WSPACE-5 | SFM source should be openable in the in-game text editor. | C-3 is complete: resolver-authorized file paths open read-only with address/hash/range metadata; no manual `sfm_source` role or guessed root is required. | — |
| SYMBOL-1 | From SFM Java code in the in-game editor, the user must be able to jump to the definition of the symbol at the cursor. | C-4/C-5 add a source-location symbol query and a registered `sfm:symbol/definition/open` action with an editor-context F12 default and Alt+Enter offer. | — |
| SYMBOL-2 | Definition lookup must use code context, not only a globally searched token string. | D-18/C-4 send the document address, immutable source snapshot/hash, source-set/resolver/index context, and exact row/column; the provider returns zero, one, or multiple typed source spans with diagnostics/confidence. | — |
| SYMBOL-3 | Definition results should compose with panels and the command palette. | C-5 opens one unambiguous result in a Text Editor v3 panel at its range, uses the constrained palette for multiple candidates, and preserves a visible diagnostic for no/incomplete results. | — |
| SYMBOL-4 | Reuse the Java symbol work already built in `sfm-propagate-changes`; do not create an unrelated Java resolver inside the Minecraft UI. | C-4 adds a reusable asynchronous provider/transport boundary over the existing live-source and dependency index; D-19 freezes the first transport without coupling the action contract to process or Vox details. | — |
| SYMBOL-5 | Interactive navigation must not stall Minecraft. | C-4 runs source/index work off the render thread, supports cancellation and request generations, rejects stale responses, and records latency/index-completeness telemetry. | — |
| PLAN-3 | Preserve the complete path from folder authorization through opening SFM code and jumping to a definition as executable vertical slices. | Selection/explorer X-1 through X-7 and C-3 through C-6 are complete with typed/live evidence; D-15 remains the later native-picker adapter and superseded C-1/C-2 remain provenance only. | — |

## Authoritative user guidance ledger — 2026-08-15 source-presentation extension

| ID | Active guidance | Required plan consequence | Superseded by |
| --- | --- | --- | --- |
| SRCPRES-1 | File-backed explorer directory rows should use a real `minecraft:chest` ItemStack icon instead of the textual `[D]` marker. | C-4a adds a file-path presentation contributor ahead of the generic fallback and proves that directory rows resolve to the theme-backed chest icon without changing registry/item rows. | — |
| SRCPRES-2 | File-backed explorer file rows may use a real `minecraft:paper` ItemStack icon instead of the textual `[F]` marker. | C-4a maps ordinary file leaves to the theme-backed paper icon, preserves richer future extension contributors, and retains the generic marker only for non-file domains with no richer presenter. | — |
| READONLY-1 | A file opened from the explorer is read-only and EditorV3 must say `Read-only` between its `#` and Done controls. | C-4b renders a localized, non-interactive read-only status in the bottom control lane only when the document is read-only. | — |
| READONLY-2 | The read-only words need contrast even when document content lies behind them. | C-4b draws the status over a solid bounded rectangle, keeps both neighbouring buttons usable/focusable, and proves responsive geometry at narrow panels and the declared GUI-scale matrix. | — |
| HILITE-1 | Investigate and use Arborium for syntax highlighting so Minecraft Java can send document text to Rust and receive formatting spans instead of implementing another Java-side ANTLR grammar for every language. | C-4c/CLI-AST Phase 0.11 freeze a versioned Rust highlighting request/result and a supervised asynchronous service using Arborium grammar/query data; Java remains a transport, validation, and rendering consumer. | — |
| HILITE-2 | Java support is the first required language. | C-4d implements `.java -> java` discovery and an Arborium Java highlighter before any additional grammar is enabled. | — |
| HILITE-3 | Returned ranges should be ChatFormatting spans that the Java document renderer can apply. | C-4c returns source-hash-bound, non-overlapping UTF-8 byte ranges with stable Arborium tags and validated canonical ChatFormatting names; C-4d converts boundaries safely and applies colour/style to EditorV3 glyphs. | — |
| HILITE-4 | Highlighting must use the exact current Java text and must not apply a response to a changed document. | C-4c/C-4d carry request id, origin generation, language id, exact UTF-8 text, and content hash; work is off the render thread, cancellable, bounded, and stale/hash-mismatched responses are discarded. | — |
| HILITE-5 | Analyze the SFM repository to identify which extensions should follow Java. | C-4c records the reproducible tracked-file audit and the deferred priority order: Rust, JSON, Gradle/Groovy, PowerShell, Markdown, TypeScript, then TOML. Existing SFML and G4 highlighters remain intact; enabling those additional Arborium languages is not part of this goal. | — |
| HILITE-6 | Reusable parser/query objects should survive requests rather than being rebuilt for every frame or draw. | CLI-AST Phase 0.11 compiles the Java highlight query once per worker, reuses bounded parser/query state, and returns cached immutable results only when language plus source hash match. Editor rendering consumes an immutable snapshot and never reparses per frame. | — |
| PLAN-4 | Update the resumable plan with current progress and every atom above, then set a goal that completes them together with C-5. | This ledger, traceability, three-pass audit, D-20 through D-24, completed C-4a through C-6, completed CLI-AST Phase 0.11, topology, completion criteria, risks, and retained evidence record that goal without losing its design constraints. | — |

## Authoritative user guidance ledger — 2026-08-16 symbol interaction and responsiveness extension

| ID | Active guidance | Required plan consequence | Superseded by |
| --- | --- | --- | --- |
| NAVHARD-1 | F12 on JDK types such as `String`, `Object`, and `StringBuilder` must navigate instead of reporting `No symbol present at captured editor position`. | C-7 and CLI-AST Phase 0.12 add implicit `java.lang` resolution plus branch-selected JDK-source indexing/identity and exact dependency/JDK source navigation fixtures. | — |
| NAVHARD-2 | F12 on a variable, field, method, annotation, or import statement must navigate to the corresponding declaration when Java semantics make it statically resolvable. | C-7 and CLI-AST Phase 0.12 cover local/parameter lexical declarations, members/overloads, annotation types, imported symbols, and truthful ambiguous/dynamic/unsupported outcomes. | — |
| NAVHARD-3 | The observed messages `Jump to definition unavailable: no worker source root contains the document within its resolver authorization` and `No symbol is present at the captured editor position (dependency/source index is incomplete)` are false negatives for known source documents and must not be papered over. | C-7 preserves both strings as regression fixtures, repairs resolver-root composition and index completeness at the responsible boundary, and requires diagnostics to distinguish an actual authorization/index problem from an unsupported Java construct. | — |
| NAVPLACE-1 | F12 sometimes opens a new split panel when navigation should open a new tab/entry in the current pane. | C-7 removes the unconditional `openRight` fallback from definition navigation: reuse an exact visible document first, otherwise push the target into the originating pane stack and focus it. Explicit open-to-side actions remain separate. | — |
| HOVERDEF-1 | Holding Ctrl over a resolvable symbol in EditorV3 should underline the exact symbol range and change the OS cursor to a link/pointer affordance. | C-9 adds generation-safe hover hit testing, cached asynchronous availability, decoration, and a version-adapted GLFW cursor-lifecycle seam; it performs no worker query or allocation per render frame. | — |
| HOVERDEF-2 | Ctrl+click on that decorated symbol should invoke the same go-to-definition action as F12 rather than hard-code another resolver. | C-9 emits the captured `sfm:symbol/definition/open` action/context and tests parity, stale hover rejection, focus changes, drag suppression, and the existing multi-cursor interaction recorded in D-25. | — |
| CTXREF-1 | Alt+Enter must offer `Find References`/`Find Usages`, and right-click in the editor should open the same contextual action surface rather than a second menu implementation. | B-5/C-9 use one `SFMContextActionProvider` registry and constrained command-palette surface for Alt+Enter and right-click; providers emit canonical action drafts and missing arguments remain palette-owned. | — |
| CTXREF-2 | Alt+F7 should directly invoke find references. | C-8/C-9 register `sfm:symbol/references/open`, add an `sfm:text_editor` Alt+F7 default, and prove parity with the Alt+Enter offer. | — |
| CTXREF-3 | Alt-click was proposed as another possible find-references gesture, but not selected as a firm default. | D-27 keeps Alt-click unbound until explicitly approved; C-9 must not steal EditorV3's existing Alt+click multi-cursor gesture accidentally. | — |
| REFS-1 | Find references must produce a persistent explorer panel or document that retains the complete result list and permits repeated navigation; it must not be a one-time choice list that disappears after opening one result. | C-8 projects one immutable, versioned reference-result entity through the generic explorer resolver/panel, with category/file/span rows, completeness/diagnostics, and an independently retained source-query identity. | — |
| REFS-2 | Opening one reference should preserve the result surface so the user can jump among several references. | C-8 gives the reference explorer ordinary explorer-owned preview semantics: result activation opens/focuses a source tab/entry without replacing the reference explorer, and back/repeated activation remains deterministic. | — |
| REVEAL-3 | A focused document needs a `Reveal in Explorer` action. | B-4 explicitly contributes the focused editor's concrete document path to `sfm:explorer/reveal`; it reuses or opens a compatible explorer through typed resolver capability and never guesses from the title. | — |
| EDITPERF-1 | EditorV3 is perceptibly laggy on large documents such as `platform/minecraft/src/main/java/ca/teamdman/sfml/ast/OutputStatement.java`. | C-10 first reproduces and measures open, frame, pointer, selection, scroll, context-capture, and syntax-style costs on that exact file before changing the implementation. | — |
| EDITPERF-2 | Large-document work should be data-driven and should reuse immutable/visible-state products rather than repeatedly scanning or rebuilding the complete glyph collection. | C-10 adds counters/traces and then addresses the measured dominant stages with viewport-aware glyph indexing, cached projections/styles/selection geometry, explicit invalidation, and allocation bounds; it does not assume syntax highlighting is the bottleneck. | — |
| PLAN-5 | Every atom, concrete failure string, gesture, tentative proposal, motivation, and expected navigation/persistence behavior in the 2026-08-16 report must remain addressable in the resumable plans. | This ledger, traceability rows, three-pass audit, D-25 through D-29, C-7 through C-11, linked CLI Phase 0.12, explorer X-8b, and window-manager Track 1b form the lossless cross-plan record. | — |

### Cross-plan atom index for the complete 2026-08-16 report

| Report atom | Authoritative IDs/work item |
| --- | --- |
| Ctrl-held hover underlines the exact actionable symbol range | HOVERDEF-1; C-9 |
| Ctrl-held hover changes the OS cursor to a link/pointer and restores it on every exit path | HOVERDEF-1; C-9 |
| Ctrl+click uses the registered definition action when actionable; the report's alternative of opening the context surface is preserved as a considered choice, with Alt+Enter/right-click owning that surface | HOVERDEF-2; D-25; C-9 |
| Alt+Enter offers Find References/Usages | CTXREF-1; B-5; C-8/C-9 |
| Editor right-click opens the same contextual command-palette surface as Alt+Enter | CTXREF-1; B-5; C-9 |
| Alt+F7 directly invokes persistent Find References | CTXREF-2; C-8/C-9 |
| Alt-click remains a tentative proposal and is deliberately unbound so it does not steal existing multi-cursor input | CTXREF-3; D-27; C-9 |
| Find References opens a persistent explorer/document rather than a one-shot disappearing choice list | REFS-1; C-8; CLI Phase 0.12.2/0.12.3 |
| Opening one reference retains the result surface and permits repeated jumps | REFS-2; C-8; C-11 |
| Every resizable panel border/divider supports VS Code-like pointer dragging | Window-manager WRESIZE-1/WRESIZE-4/WRESIZE-5; Track 1b; explorer XLAY-6 |
| Horizontal/vertical dividers advertise the correct resize cursor | Window-manager WRESIZE-2; Track 1b |
| A real three-or-more-panel orthogonal intersection supports one two-axis drag with a resize-all/crosshair affordance | Window-manager WRESIZE-3; Track 1b |
| File icons vary through an extension/theme contribution; `.java` initially uses the proposed cocoa-beans ItemStack rather than a renderer hard-code | Explorer XEXP-20; XD-9; X-8b |
| The exact `sfm:explorer/view/set` frontier suggests list and small-icons values | Explorer XEXP-21; X-8b |
| Small-icons on `registry://minecraft/item/` render actual ItemStacks | Explorer XEXP-21; X-8b |
| Absolute-path presentation is available and composes independently with list/icons | Explorer XEXP-22; XD-10; X-8b |
| F12 resolves `String`, `Object`, and `StringBuilder` through branch-selected JDK sources and implicit `java.lang` | NAVHARD-1; D-28; C-7; CLI Phase 0.12.1 |
| F12 resolves statically knowable variables, fields, methods, annotations, and imports | NAVHARD-2; C-7; CLI Phase 0.12.1 |
| A known authorized source cannot produce `Jump to definition unavailable: no worker source root contains the document within its resolver authorization` | NAVHARD-3; C-7; CLI Phase 0.12.3 |
| A resolvable symbol cannot be suppressed by `No symbol is present at the captured editor position (dependency/source index is incomplete)` | NAVHARD-3; C-7; CLI Phase 0.12.1/0.12.3 |
| The real `OutputStatement.java` EditorV3 lag is measured, attributed, and fixed without speculative debounce/input loss | EDITPERF-1/EDITPERF-2; D-29; C-10 |
| Address-bar focus leaves no stale left/right explorer-body highlight | Explorer XEXP-23; X-8b |
| Body focus draws the missing top and bottom edges as well as the sides | Explorer XEXP-23; X-8b |
| The body viewport is inset/layered so selected rows cannot overwrite focus chrome | Explorer XEXP-23; X-8b |
| Multiple wheel callbacks are applied immediately, individually, and in receipt order rather than after input stops | Explorer XEXP-24; X-8b |
| The explorer has action-backed, focusable fuzzy filtering over its current lazy materialization | Explorer XEXP-25; XD-11; X-8b |
| F12 reuses an exact visible target or opens a tab/entry in the current pane stack, never an implicit side split | NAVPLACE-1; D-26; C-7 |
| A focused document contributes an exact resolver-backed `Reveal in Explorer` action | REVEAL-3; B-4; C-11 |

## Authoritative user guidance ledger — 2026-08-22 completion-frontier extension

| ID | Active guidance | Required plan consequence | Superseded by |
| --- | --- | --- | --- |
| PALUX-1 | With a recent complete command such as `sfm action invoke sfm:panel/open sfm:chamber/temporal-decimal-numbering`, typing `open` should rank the bare `sfm:panel/open` grammar boundary first. Accepting the likely action should take two deliberate completion steps rather than one Tab unexpectedly committing the historical leaf. | B-0 separates action boundaries from complete historical commands. A blank action slot retains complete-MRU-first behavior; a nonblank action-id query uses history to boost the matching bare boundary and keeps complete historical leaves below it. | — |
| PALUX-2 | Tab-completing `sfm:panel/open` should not append a space automatically because `/right`, `/left`, `/above`, and `/below` remain valid action-id continuations. Space is the user's explicit decision to enter the scene argument. A second Tab must advance to a strict continuation rather than no-op and move focus away. | B-0 introduces typed insertion intent and frontier-aware Tab cycling. Action-boundary acceptance and deliberate argument entry are distinct from Enter/execution preparation. | — |
| PALUX-3 | Scene history should be reusable across compatible members of the panel-open family, such as `open`, `open/right`, and `open/below`, when the parameter has the same semantic role. | B-0 preserves raw history and builds a bounded in-memory projection keyed by an explicit completion-history family plus compatible Brigadier slot/grammar; slash-prefix similarity alone never authorizes sharing. | — |
| PALUX-4 | `focused` for `sfm:episode/trajectory/plan`, exact overlay selectors such as `id(sfm%3Ahistory)`, and `visible|hidden` must be suggested at their argument frontiers instead of requiring hostile manual entry. | B-0 first adds command-tree and palette-level characterization tests. Existing registered providers remain authoritative; fixes land at the palette/frontier/runtime-freshness layer shown to fail rather than hard-coding duplicate values. | — |
| PALUX-5 | When a command still requires arguments and no concrete value suggestion is available, the palette must visibly explain the expected named argument(s); a grey Execute button is insufficient. | B-0 extracts a shared Brigadier frontier/usage analyzer using parse context and smart usage, with `SFMCommandDraftAnalysis` as the existing named-argument seam. Usage rows are explanatory and cannot execute. | — |
| PALUX-6 | Candidate acceptance, typing, deletion, paste, and command-history recall in the palette must eventually participate in the same ordinary document history as Text Editor V3, including Ctrl+Z/Ctrl+Shift+Z and exact before/after evidence. | Snapshot/episode TE-S2 owns the event journal and undo graph. B-0 exposes exact candidate kind, replacement range, and insertion policy so TE-S2 records one semantic completion transaction without inventing a second ranker or history engine. | — |
| PALUX-7 | Pathfinding and temporal chambers are useful proving grounds but must not be the only approachable route to palette completion or input history. | B-0 is proven through the normal title-screen command palette and joins the ordinary editor/history puppet in TE-S2; chamber-only evidence cannot close it. | — |
| PLAN-7 | Every command-palette atom in the 2026-08-22 report must remain addressable and coordinated with the ordinary-history plan rather than being compressed into “improve suggestions.” | This ledger, B-0, its tests, the cross-plan TE-S2C join, the three-pass audit, and the explicit first-goal boundary are authoritative. | — |

## Guidance traceability

| Guidance | Plan coverage | Evidence when complete |
| --- | --- | --- |
| WNAV-1, WNAV-2 | K-1, K-2, K-7 | Widget-host tests, terminal/property focus-path tests, and a live keyboard-only terminal puppet |
| WNAV-3 | K-5, K-7 | Static inventory output plus runtime actionable-element/focus audit with explicit exemptions |
| KCTX-1, KCTX-2, KCTX-3 | K-3, K-6, K-7 | Registry/bootstrap tests, schema migration, precedence/conflict tests, UI scope display, and terminal non-leak proof |
| KBIND-1, KBIND-2, KBIND-3, KBIND-4, KBIND-5, KBIND-6, KBIND-7 | K-3, K-4, K-6, K-7 | Default/tombstone/action/layout tests, exact auto/explicit scale transitions, transient-toast/fade/boundary evidence, `[?]` and management captures, exact physical-key matching, incomplete-draft palette proof, and live pane operations |
| KUI-1, KUI-2, KUI-3 | K-6, K-7 | Sort/filter/scroll tests and keyboard/mouse capture-chip puppet evidence |
| ACT-1, ACT-2, ACT-3 | K-2, K-5, K-7 | Action-element inventory, semantic invocation parity, Manager Edit fixture, and absence of public coordinate-click actions |
| EXPL-1, EXPL-2 | A-3, A-4, A-5 | Resolver-backed action/registry explorer, typed outlink tests, and live navigation captures |
| ADDR-1, ADDR-2, ADDR-3 | A-1, A-2a, A-2b, A-5 | Parse/print, context, contributor, containment, unavailable-context, registry, path-device, and Vanilla adapter tests |
| PANEL-OLD-1 | D-1, K-4, release-plan supersession note | Recorded duplication semantics plus independent-state and move-preservation tests |
| PLAN-1 | Entire plan | Three-pass intent audit and a fresh-agent resumption review |
| FFILE-1, FFILE-2, FFILE-3, FFILE-4, FFILE-5 | A-1, A-2a, B-1, B-2, B-3, B-6 | Address/path fixtures, streamed-candidate tests, bounded traversal telemetry, contextual-root ranking tests, Ctrl+Shift+N live proof, and explicit absence of ambient-root traversal |
| REVEAL-1, REVEAL-2 | A-1, A-3, B-2, B-4, B-6 | Focus/open/reveal action tests, stable origin targeting, file/item resolver fixtures, safe panel-placement proof, and Ctrl+Shift+E live proof |
| CTXA-1, CTXA-2, CTXA-3, CTXA-4 | A-1, B-1, B-2, B-5, B-6 | Provider registry tests, one-to-many ordering/deduplication, constrained-palette proof, incomplete-draft completion, and legacy Ctrl+Space migration evidence |
| ECTX-1, ECTX-2, ECTX-3 | B-2, B-5 | Whole-document/2D context fixtures, canvas/text coordinate adapters, AST enrichment parity, and a future non-centre screen-point contract fixture |
| SCENE-1, SCENE-2, SCENE-3 | Selection/explorer X-4/X-5; A-2c | Generic path-expression panel-open grammar, heterogeneous file/item execution, palette discovery, migrated puppets, and absence assertions for the three deleted developer ids plus superseded file/item scene ids |
| ELOC-1 through ELOC-5 | Selection/explorer X-8a; A-2c | Location-header geometry/narration, preferred-editor action parity, exact canonical multi-root/selection-id document, atomic/stale save fixtures, projection-action discovery, and split-view puppet |
| PLAN-2 | Entire 2026-08-10 extension | Three-pass audit and fresh-agent resumption review |
| WSPACE-1, WSPACE-2, WSPACE-3, WSPACE-4 | Selection/explorer X-1 through X-8; later D-15 adapter | Selection/path/relation tests, direct CLI/action parity, heterogeneous lazy-root puppet, and later fake/native picker evidence; no global workspace persistence claim |
| WSPACE-5 | Selection/explorer X-1 through X-7; C-3 | Resolver-authorized read-only source opening with concrete document address/hash/range and live editor evidence |
| SYMBOL-1, SYMBOL-2, SYMBOL-3, SYMBOL-4, SYMBOL-5 | B-2, B-5, C-3, C-4, C-5, C-6 | Location-query scenarios, current-snapshot/hash tests, async cancellation/stale-result tests, single/ambiguous/missing definition UI proofs, F12/Alt+Enter action parity, open-at-span proof, and measured live latency |
| PLAN-3 | Entire 2026-08-11 multi-root extension | Three-pass intent audit, cross-plan dependency review, and fresh-agent resumption review |
| SRCPRES-1, SRCPRES-2 | D-20; C-4a; C-6 | Presenter resolution tests, actual ItemStack ids, unchanged non-file presenter precedence, and a live explorer screenshot/artifact |
| READONLY-1, READONLY-2 | D-21; C-4b; C-6 | Read-only/writable visibility tests, bottom-lane geometry and hit/focus parity, narrow-panel proof, and GUI-scale visual evidence |
| HILITE-1, HILITE-2, HILITE-3, HILITE-4, HILITE-6 | D-22 through D-24; C-4c/C-4d; CLI-AST Phase 0.11; C-6 | Rust Arborium span fixtures, protocol/direct-worker parity, Unicode/CRLF/hash/cancellation tests, Java glyph-style tests, latency/cache telemetry, and live highlighted `SFM.java` evidence |
| HILITE-5 | C-4c; source references | Reproducible `git ls-files` extension audit, local Arborium support cross-check, and an explicitly deferred ordered language backlog |
| PLAN-4 | Entire 2026-08-15 source-presentation extension | Three distinct audit passes, prepared goal wording, and fresh-agent resumption review |
| NAVHARD-1, NAVHARD-2, NAVHARD-3 | D-28; C-7; CLI-AST Phase 0.12.1/0.12.3 | Exact location scenarios for JDK/project/dependency types, locals/parameters/fields/methods/annotations/imports, root-authority composition tests, the two quoted false-negative regressions, and truthful completeness/unsupported outcomes |
| NAVPLACE-1 | D-26; C-7; C-11 | Current-pane stack placement tests, exact-visible reuse, explicit-side-action separation, nested split/stack fixtures, and a live F12 topology artifact proving no surprise split |
| HOVERDEF-1, HOVERDEF-2 | D-25; C-9; C-11 | Symbol-range hit tests, Ctrl modifier transitions, pointer-cursor lifecycle, one cached request per immutable hover identity, F12/action parity, stale/drag/focus tests, and live hover/click evidence |
| CTXREF-1, CTXREF-2, CTXREF-3 | D-27; B-5; C-8; C-9; C-11 | Shared provider/choice-surface tests, right-click/Alt+Enter parity, Alt+F7 default/action proof, Alt-click non-stealing assertion, and live contextual/reference evidence |
| REFS-1, REFS-2 | D-26; C-8; CLI-AST Phase 0.12.2/0.12.3; C-11 | Versioned reference-result schema, complete/incomplete category fixtures, generic explorer projection, repeated result activation with retained result panel, preview ownership, and live source jumps |
| REVEAL-3 | B-4; C-11 | Focused-document contribution, exact path/resolver targeting, compatible explorer reuse/open, reveal selection/scroll, no-title-guess and live action proof |
| EDITPERF-1, EDITPERF-2 | D-29; C-10; C-11 | Reproducible `OutputStatement.java` benchmark, stage/frame/input counters, before/after traces, viewport/caching/invalidation tests, allocation bounds, and live responsive interaction evidence |
| PLAN-5 | Entire 2026-08-16 extension plus linked explorer/CLI/window-manager plans | Three-pass intent audit, exact-id cross-plan map, unresolved-decision register, and fresh-agent resumption review |
| KUI-4, KDISC-1, KDISC-2, KDISC-3, KDISC-4 | K-8 | Pure capture/ranking/context tests, dispatch-leak tests, and a live command-palette hotkey-lookup artifact showing exact-before-relaxed ordering and no action invocation |
| PALUX-1 through PALUX-5 | B-0 | Typed candidate/ranking tests, exact Tab/Space frontier tests, parsed compatible-slot history fixtures, trajectory/overlay provider journeys, smart-usage snapshots, and a normal command-palette puppet |
| PALUX-6, PALUX-7 | B-0 plus snapshot/episode TE-S2B through TE-S2D | Exact candidate replacement transactions, generic palette undo/redo and branch evidence, and a natural title-screen document/history-canvas puppet with no chamber dependency |
| PLAN-7 | B-0 and the snapshot/episode TE-S2 sequence | Three-pass intent audit, cross-plan ownership check, prepared goal wording, and fresh-agent resumption review |

## Intent audit evidence — 2026-08-05

- **Pass 1 — extraction:** Reread the complete message from the disconnected
  terminal controls through the resumable-plan request. WNAV-1 through PLAN-1
  separately retain each behavior, constraint, example, proposal, motivation,
  earlier-decision conflict, and sequencing concern. This pass caught and
  repaired the initially generalized address examples by preserving all four
  proposed registry/path spellings in ADDR-1/ADDR-2.
- **Pass 2 — traceability:** Located concrete work and proof for every active
  ledger id in K-1 through K-7 or A-1 through A-5, then checked the inverse.
  New material choices are either verified by current source/Microsoft Terminal
  evidence, confirmed constraints, reversible working assumptions, or explicit
  D-1 through D-6 gates. No work item is supported only by an unlabeled agent
  preference.
- **Pass 3 — adversarial omission:** Reread the message after the task graph was
  complete, specifically checking terminal-versus-browser context examples,
  `sfm:terminal` versus `sfm:default`, the prior move-only decision, static
  and/or runtime auditing, action versus coordinate/element activation,
  Manager-position context, action-owner jumplinks, individual-properties
  versus collection-explorer roles, registry outlinks, path devices, generic
  resolver context, Vanilla/no-orphan adapters, and incomplete action drafts.
  Each remains explicit; browser hotkeys and exact address spelling are not
  silently converted into commitments.
- **Known source limitation:** None. The complete message was available in this
  task.

## Intent audit evidence — 2026-08-10 fuzzy-file/context-action extension

- **Pass 1 — extraction:** Reread the complete message from Ctrl+Shift+N file
  search through the future arbitrary-ray idea. FFILE-1 through PLAN-2 retain
  each requested key gesture, current-panel seed source, independent explorer
  selection/editor identity, streaming question, `gci -Recurse | fzf`
  motivation, address-resolver generalization, Ctrl+Space history, Alt+Enter
  lightbulb model, one-to-many provider requirement, both concrete examples,
  incomplete-argument ownership, whole-program context, 2D position, and
  non-centre screen-point future.
- **Pass 2 — traceability:** Mapped every new ledger id to typed-address work,
  B-1 through B-6 implementation, a design gate where public behavior remains
  unresolved, and machine/live evidence. The inverse check removed an early
  proposal for a general shell-like pipe AST because no active requirement
  needs it; the typed candidate-source boundary preserves the useful producer
  -> filter -> ranked-consumer shape without expanding the command language.
- **Pass 3 — adversarial omission:** Checked specifically for lossy collapse of
  explorer and editor paths into one value, synchronous recursive traversal on
  the Minecraft thread, wildcard queries masquerading as durable addresses,
  Brigadier being replaced as executor, callback-only context actions,
  provider-owned argument prompts, 1D cursor offsets becoming canonical,
  Ctrl+Shift+E silently choosing one of its proposed meanings, and future rays
  being hard-coded to screen centre. Each is explicit in the ledger, contracts,
  risks, or D-7 through D-13.
- **Known source limitation:** None. The complete 2026-08-10 message was
  available in this task.

## Intent audit evidence — 2026-08-11 explorer-scene cutover addendum

- **Pass 1 — extraction:** Preserved all three replacement commands, the shared
  `sfm:panel/open` motivation, the three exact legacy action ids, and the user's
  explicit full-cutover/no-backwards-compatibility constraint.
- **Pass 2 — traceability:** SCENE-1 through SCENE-3 map to A-2c implementation,
  grammar/execution tests, migrated puppets, and negative registry/tree proof.
  No redirect, alias, history migration, or deprecated registration remains in
  the planned result.
- **Pass 3 — adversarial omission:** Checked that “supporting code removal” does
  not accidentally delete reusable file explorer/item picker domain code used
  by non-legacy callers, that the old fixture explorer is not mislabeled as
  `sfm_source`, and that palette discovery comes from the typed scene grammar
  rather than replacement one-off actions.
- **Known source limitation:** `sfm_source` has no current implementation or
  established root meaning; D-14 records the remaining semantic decision.

## Intent audit evidence — 2026-08-11 multi-root symbol-navigation addendum

- **Pass 1 — extraction:** Preserved the desired end state (open SFM code in
  the in-game editor and jump to a symbol definition), the native-picker
  question, the add-folder operation, and the VS Code-like multi-root workspace
  requirement as WSPACE-1 through SYMBOL-5 rather than collapsing them into a
  generic “file explorer improvements” task.
- **Pass 2 — local feasibility:** Confirmed from the generated 1.19.2 Minecraft
  library manifest and local class signature that Minecraft already supplies
  `org.lwjgl:lwjgl-tinyfd:3.3.1`, platform natives, and
  `TinyFileDialogs.tinyfd_selectFolderDialog(...)`. Confirmed that current
  file explorers already receive `Screen.onFilesDrop`, while Text Editor v3
  recipes currently support only literal/resource text and do not retain a
  file address or target range.
- **Pass 3 — architecture/traceability:** Mapped explicit root authority to
  A-1/A-2a/C-1, native/typed/drop acquisition to C-2, addressed file opening to
  C-3, location-aware reuse of the existing CLI symbol engine to C-4, and
  action/panel integration to C-5/C-6. Checked specifically that native dialog
  automation is not required, source analysis never runs on the render thread,
  ambiguous symbols do not select silently, unsaved snapshots are not confused
  with disk contents, and `sfm_source` no longer means a fixture or ambient
  working directory.
- **Known source limitation:** None. The complete multi-root/jump-definition
  request and the relevant local runtime/editor/CLI sources were available.

## Intent audit evidence — 2026-08-12 selection/lazy-explorer reconciliation

- **Pass 1 — extraction:** Rechecked every later correction: explicit
  set-valued explorer selectors, `focused` as syntax rather than ambient state,
  generic heterogeneous roots, path expressions versus selection selectors,
  versioned selection history, picker-as-destination, relation-owned hierarchy,
  fetch-before-replace refresh, paging, pane versus stacked-entry identity, and
  deferred drag semantics. The dedicated X ledger retains each atom and example.
- **Pass 2 — traceability:** Replaced active C-1/C-2 dependencies with X-1
  through X-7, made A-1/A-2a/A-2b additive consumers of the shared types,
  changed A-2c to one generic path-expression explorer scene, and updated the
  control, source-opening, fuzzy-search, and symbol-navigation paths to consume
  explicit explorer/resolver locations rather than a global catalog.
- **Pass 3 — adversarial omission:** Searched active contracts, topology,
  gates, command vocabulary, work items, completion criteria, and risks for
  accidental `sfm workspace add`, `SFMExplorerWorkspace`, implicit focused
  mutation, file/item explorer subtype, duplicate path algebra, eager recursive
  hierarchy, and clear-before-fetch behavior. Remaining occurrences are marked
  historical/superseded provenance or refer to the split/stack workspace host.
- **Known source limitation:** None. The complete convergence discussion and
  linked authoritative plan were available.

## Intent audit evidence — 2026-08-13 implementation/trajectory reconciliation

- **Pass 1 — extraction:** Rechecked the original generic-explorer cutover,
  open-real-SFM-source, Text Editor v3 emplacing, Space preview, explicit
  Ctrl+Enter new-panel correction, safe preview ownership, jump-to-definition,
  and GUI-scale-matrix requests. Kept native folder picking, fuzzy search,
  symbol workers, and writable documents as separately ordered work.
- **Pass 2 — traceability:** Reconciled source evidence with A-2c and C-3,
  then closed those items in implementation commit `6dc3d2d04`: the one-off
  developer ids and exclusive puppet path are gone, the generic scene accepts
  full path expressions, and resolver-authorized file activation now reaches a
  read-only Text Editor v3 document. F12/C-4 through C-6 remain behind that
  completed foundation.
- **Pass 3 — adversarial omission:** Checked that the revised slice does not
  reintroduce a global workspace, infer an ambient filesystem root, silently
  retain unreleased aliases, write source files, reuse a dirty/unrelated panel,
  conflate directory expansion with file opening, call fixture text
  `SFM.java`, or smuggle fuzzy search/symbol indexing into acceptance.
- **Known source limitation:** None. The relevant original messages, current
  sources, linked plans, clean commits, successful matrix log, and figures were
  available.

## Intent audit evidence — 2026-08-15 source-presentation extension

- **Pass 1 — extraction:** Reread the complete current request and separated
  directory chest icon, file paper icon, replacement of textual markers,
  Arborium investigation, Java-to-Rust text ownership, returned
  ChatFormatting spans, avoidance of per-language Java ANTLR implementations,
  Java-first delivery, repository-extension prioritization, exact read-only
  wording/placement/contrast, progress reconciliation, resumable-plan usage,
  goal creation, and inclusion of C-5 into SRCPRES-1 through PLAN-4.
- **Pass 2 — traceability:** Mapped every active id to D-20 through D-24,
  C-4a through C-6, linked CLI-AST Phase 0.11, focused tests, a tracked-file
  extension audit, and live artifacts. The inverse check ties the dedicated
  Rust syntax lane, UTF-8 span contract, cached grammar/query state, and
  Java-only boundary to local Arborium 2.18.1 APIs, the existing symbol-worker
  lifecycle, the current EditorV3 model, or an explicit reversible decision.
- **Pass 3 — adversarial omission:** Checked that “file” does not accidentally
  turn every non-expandable registry object into paper; “between Done and #”
  is the bottom control lane rather than a floating message above it; the solid
  rectangle does not become an invisible hit target; Java sends the exact
  current text rather than only a path; stale spans cannot colour changed
  glyphs; the highlighter is not rebuilt per draw/request; existing SFML/G4
  highlighting is retained; and the extension audit creates a later priority
  list rather than silently widening this goal beyond Java.
- **Known source limitation:** None. The complete current request, current
  plans/sources/tests, local Arborium checkout, pinned crate sources, and
  tracked SFM file list were available.

## Intent audit evidence — 2026-08-16 symbol interaction and responsiveness extension

- **Pass 1 — extraction:** Reread the complete report and split every compound
  sentence into NAVHARD-1 through PLAN-5. Preserved all concrete gestures
  (Ctrl-hover, Ctrl+click, Alt+Enter, tentative Alt-click, Alt+F7, right-click,
  F12), all three named JDK examples, both exact failure strings, every Java
  symbol category, persistent-reference motivation, current-pane-tab placement,
  focused-document reveal, the exact `OutputStatement.java` lag witness, and
  the requirement that no atom be forgotten. Explorer presentation/filter/
  scroll/focus-border and panel-divider atoms are retained in the linked
  selection/explorer and window-manager ledgers rather than compressed here.
- **Pass 2 — traceability:** Mapped definition/JDK/local-symbol correctness to
  C-7 and CLI Phase 0.12.1; location usages and persistent results to C-8 and
  CLI Phase 0.12.2/0.12.3; pointer/context gestures to B-5/C-9; focused reveal
  to B-4; measured large-document work to C-10; and one live integrated proof
  to C-11. Cross-checked the inverse against current source: navigation really
  calls `openRight`, Ctrl+left-click currently moves all cursors, right-click
  has no EditorV3 context route, and the renderer repeatedly traverses the
  complete glyph list. These are verified foundations, not silently assumed
  causes or accepted designs.
- **Pass 3 — adversarial omission:** Checked that `Find References` is not
  reduced to a disappearing definition-style choice list; right-click and
  Alt+Enter do not become two selection systems; the pointer does not imply a
  stale/unresolvable target; hover does not spawn work per frame; JDK support
  is not mislabeled as an ordinary mod dependency; incomplete-index and
  authorization diagnostics remain truthful; F12 does not keep creating side
  splits; Alt-click does not steal the established multi-cursor gesture without
  approval; reveal does not infer a path from title text; and performance work
  measures event, projection, style, selection, and render stages before
  choosing viewport/caching changes.
- **Fresh-agent resumption check:** A new agent can identify C-7 as the first
  source-navigation dependency, see that it requires CLI 0.12.1, proceed to
  C-8/0.12.2-0.12.3, then C-9/C-10/C-11, while treating explorer X-8b and
  window-manager Track 1b as parallel bounded plans. The agent can also see
  that no goal, propagation, publication, or implementation authorization was
  created by this planning update.
- **Known source limitation:** None. The complete user report, current plans,
  current 1.19.2 Java/Rust sources, and the local VS Code source reference were
  available. The reversible `.java` icon, Alt-click, absolute-path presentation,
  local-filter scope, Ctrl+click fallback, target-placement, JDK-source, and
  performance-budget choices are closed below for this implementation goal and
  remain explicit rather than becoming hidden assumptions.

## Intent re-audit evidence — 2026-08-16 verbatim repost after compaction

- **Pass 1 — extraction:** Reread the user's current message from beginning to
  end; it reproduces the complete earlier report verbatim despite conversation
  compaction. Expanded the cross-plan atom index above so underline, pointer,
  click choice, each context gesture, persistent-result lifetime, divider axes,
  each explorer presentation/focus/scroll/filter defect, both exact resolver
  failures, every named Java symbol category, pane placement, reveal, and the
  exact large-file witness are independently visible rather than hidden inside
  grouped prose.
- **Pass 2 — traceability:** Followed every atom from the index to an active
  guidance id, decision, executable work item, validation, and observable
  completion criterion across C-7 through C-11, the REVEAL-3 portion of B-4,
  the CTXREF portion of B-5, CLI-AST 0.12.1 through 0.12.3, explorer X-8b, and
  window-manager Track 1b. The inverse check found no material work in those
  slices unsupported by the report, verified source behavior, or a labeled
  reversible design decision.
- **Pass 3 — adversarial omission:** Rechecked the report's uncertainty words
  and motivations: Ctrl+click uses definition while Alt+Enter/right-click own
  context; tentative Alt-click remains unbound; cocoa beans is an initial
  contributed mapping rather than a hard-coded universal icon; absolute paths
  compose with view mode; scroll delay is measured before its cause is named;
  references persist after activation; two-axis resize requires a real hit
  intersection; and F12 geometry changes only through explicit side-opening
  actions. No atom is deferred merely because it crosses one of the four plans.
- **Fresh-agent resumption check:** The authoritative next set is now named by
  exact ids and dependency order: CLI 0.12.1 -> C-7; CLI 0.12.2/0.12.3 -> C-8;
  B-4/B-5 subsets -> C-9; with X-8b and Track 1b as bounded parallel tracks,
  C-10 after instrumentation, and C-11 as the integrated acceptance/bookkeeping
  gate. A fresh agent can tell what must not happen: no Gradle, Java-source
  mutation/refactoring capability, analyzed-source-tree mutation, propagation,
  publication, release tagging, Alt+drag relocation, picker X-8, or broader
  Phase B search work. Java consumer implementation/test edits remain allowed.
- **Known source limitation:** None. The verbatim requirement source is present
  in the current message, and the linked plans and 1.19.2 sources are available.

## Intent audit evidence — 2026-08-17 hotkey-discovery extension

- **Pass 1 — extraction:** Atomized the new request into the explicit palette
  capture control (KUI-4), keyboard ownership/non-interpretation and cleanup
  (KDISC-1), exact-versus-derivative result ordering including the concrete
  `Ctrl+Alt+L` / `Ctrl+L` example (KDISC-2), explainable inspection-only result
  presentation (KDISC-3), and pure/live proof without requiring manual key-by-
  key inspection (KDISC-4).
- **Pass 2 — traceability:** Mapped each atom to D-30 and K-8, reusing the
  verified `SFMKeySequenceCapture` primitive while keeping binding editing and
  palette lookup as separate state machines. The inverse check confirms that
  capture, ranking, context eligibility, result activation, cleanup, and live
  evidence each have an executable work or validation consequence.
- **Pass 3 — adversarial omission:** Checked that the feature is not reduced to
  editing a binding, ordinary palette text entry, a hidden global key listener,
  arbitrary fuzzy key substitution, or an action-invocation shortcut. The
  captured context, strict modifier-subset example, physical-key semantics,
  no-leak cleanup, and inspection-only result behavior remain explicit.
- **Known source limitation:** The broader pre-compaction conversation was not
  reread from raw history; this extension was audited against the current user
  message, the durable keybinding ledger, and the current capture/palette source
  and tests. No older requirement was changed or marked superseded.

### K-8 implementation-readiness re-audit — 2026-08-17

- **Pass 1 — extraction:** Split the former single K-8 task into origin-context
  capture, runtime-dispatch-equivalent result semantics, physical capture
  completion/cancellation, scoped suspension ownership, typed palette rows,
  exact/conflict/shadow/related ranking, lifecycle/accessibility, live evidence,
  and release ownership.
- **Pass 2 — traceability:** D-30 and K-8a through K-8e now name stable ids,
  context/binding revisions, relation meanings, per-stroke subset distance,
  action-details-only activation, constrained-palette exclusion, fault cleanup,
  focused tests, machine artifacts, and the P-5.5 join/defer decision.
- **Pass 3 — adversarial omission:** Rechecked that “related” does not falsely
  mean “would dispatch”; palette focus does not overwrite the origin situation;
  deepest-situation, partial-sequence, and equal-depth conflicts are not lost;
  Tab/Enter/arrows/Delete remain capturable; one global boolean cannot release
  another capture owner; and inspecting a result cannot execute, draft, or
  enter history.
- **Known source limitation:** Exact inter-stroke and triple-Escape durations
  remain values to inherit from the tested binding engine rather than duplicate
  as prose constants. K-8a freezes their referenced constants/fixtures before
  implementation.

## Intent audit evidence — 2026-08-22 completion-frontier extension

- **Pass 1 — extraction:** Reread the complete report and both supplied
  Excalidraw concepts. PALUX-1 through PALUX-7 separately retain nonblank bare-
  boundary ranking, preservation of blank complete-MRU behavior, deliberate
  Tab-versus-Space progression, strict-continuation Tab behavior, history
  sharing across compatible panel-open arguments, the exact missing
  `focused`/overlay/visibility examples, named required-argument guidance,
  palette input undo/redo, and the requirement that ordinary UI—not only a
  chamber—prove the result.
- **Pass 2 — source/traceability:** Traced the current ranking tie to
  `SFMClientActionCommandTree`, unconditional separator insertion to
  `SFMClientCommandInsertion`, and the application path to
  `SFMCommandPaletteScreen`. Confirmed that trajectory and overlay grammars
  already register `focused`, `all`, exact ids, and visibility literals;
  therefore B-0 begins with tree-level and palette-level characterization and
  does not duplicate those providers. Confirmed that Vanilla's
  `CommandSuggestions` uses Brigadier suggestion context and smart usage and
  that `SFMCommandDraftAnalysis` already extracts named missing arguments.
- **Pass 3 — adversarial omission/conflict:** Checked that typed search does not
  regress the completed blank-palette MRU contract, common slash ancestry does
  not accidentally share unrelated argument histories, quoted/greedy values
  are never split as whitespace, provider work stays bounded and in memory,
  executable partial tokens do not suppress useful completions, a second Tab
  cannot become a no-op focus escape, and the future streamed-candidate layer
  does not become a competing ranker. Cross-checked ownership with snapshot/
  episode TE-S2: this plan owns candidates/frontiers/usage; TE-S2 owns raw
  input, semantic transactions, revisions, undo, redo, and the history canvas.
- **Known source limitation:** The linked Stephen's Sausage Roll video was
  available only through the user's description during this planning pass.
  Spectral embedding is preserved as later view-provider research, not as a
  claimed implementation fact or a dependency in B-0/TE-S2A through TE-S2D.

## Scope

In scope:

- SFM-owned full screens, workspace panels, panel children, and their adapters to
  Minecraft 1.19.2 focus, narration, keyboard, mouse, and controller-compatible
  activation conventions.
- Contextual dynamic bindings, defaults, persistence/migration, command-draft
  completion, conflict reporting, capture UX, and action discovery.
- Focused-panel close, scale, resize, duplicate, navigation, diagnostics, and
  other semantic workspace actions.
- Stable action-element identity and an inventory/audit boundary for SFM-owned
  interactive controls.
- Typed addresses, resolver/device registries, registry/path adapters, and
  explorer projections over actions, bindings, situations, registries, and
  selected Vanilla entries.
- Asynchronous palette candidate sources, deterministic fuzzy ranking,
  cancellation/stale-result rejection, bounded path enumeration, contextual
  search-root derivation, and visible truncation/error state.
- Typed context projection from focused screens, panels, child widgets,
  explorer selections, editor documents/cursors, and future spatial targets.
- Semantic open-file, explorer focus/reveal, and contextual-actions commands
  plus approved SFM-owned default bindings.
- Canonical typed file/item explorer panel scenes and complete removal of the
  unreleased one-off developer opening actions they replace.
- Selection-backed session-scoped multi-root explorer locations, explicit root
  add/remove/reorder actions, later native TinyFD folder selection, typed-path
  and drag/drop alternatives, and resolver-root availability diagnostics.
  Persisting named selections/locations remains a separately approved schema.
- File-backed Text Editor v3 documents with durable addresses, safe read-only
  opening, open/focus-at-range, and source-hash-aware snapshots.
- Location-aware Java definition lookup over workspace and dependency sources,
  exposed as asynchronous registered actions and contextual offers.
- Correct location-aware definition and reference lookup for JDK, dependency,
  project, member, local, annotation, and import symbols; persistent generic
  reference-result explorers; Ctrl-hover/click, Alt+F7, right-click, and
  Alt+Enter action parity; deterministic current-pane navigation; and focused
  document reveal.
- Measured EditorV3 large-document responsiveness with viewport/caching work
  selected from reproducible stage and frame evidence rather than conjecture.
- File-domain explorer presentation with theme-backed chest/paper ItemStack
  icons, plus an explicit contrast-backed EditorV3 read-only status.
- A bounded, versioned, Rust-owned Arborium syntax-highlighting service and
  Java client/renderer integration for `.java` documents first.
- Baseline implementation and proof on 1.19.2, with explicit version-adapter
  seams for later propagation.

Out of scope unless a later goal explicitly expands it:

- Modifying or merging Microsoft Terminal; its pinned defaults are reference
  evidence only.
- Copying browser tab semantics or assigning Ctrl+W/Ctrl+Shift+T merely because
  browsers use them. Those examples motivate contextual situations, not SFM
  defaults without matching SFM actions.
- A global input grab that shadows Minecraft or other mods outside SFM-owned
  contexts. User-created global bindings remain possible and visibly scoped.
- Registering one global action for every Unicode character, pointer coordinate,
  or low-level editor impulse. Parameterized input action kinds remain valid.
- Granting arbitrary host-filesystem access through `sfm:path`.
- Enumerating a drive root, home directory, or other ambient filesystem root
  because no contextual path seed was available.
- A general shell/pipeline language, process piping, PowerShell execution, or
  arbitrary `Get-ChildItem` invocation. `gci -Recurse | fzf` is the product
  behavior analogy, not an execution implementation requirement.
- Implementing HWYLA/Jade/WAILA replacement rendering or arbitrary world-ray
  providers in the first contextual-action slice; only the extensible context
  shape is in scope.
- Converting every Minecraft/Forge registry and every Vanilla screen in the
  first resolver/explorer slice.
- Propagation, publication, release metadata changes, Teamy Studio, or Cloud
  Terrastodon work.
- Enabling Arborium grammars beyond Java in this goal. Rust, JSON,
  Gradle/Groovy, PowerShell, Markdown, TypeScript, and TOML are prioritized
  follow-ups; existing SFML and G4 paths remain unchanged.
- Writable host-file editing/save/conflict semantics, semantic diagnostics,
  code completion, or replacing the existing Java symbol engine with
  Arborium highlighting captures.

## Established foundation and source evidence

- Repository rules are in `docs/AGENTS.md`: implement on 1.19.2, use
  `sfm-propagate-changes.exe`, and do not invoke Gradle directly.
- SFM baseline inspected at `33030f2503e7ed4af9fe6b53dbfb06920e00c2a7`.
  `SFMScreenPanel` is a narrow manually routed interface with no child-widget,
  focus-path, or narration-entry collection. `SFMScreenMultiplexer` manually
  forwards key/mouse events and hard-codes F3, Ctrl+M, Ctrl+digits, Ctrl+Tab,
  and Escape behavior.
- `SFMTerminalPanel` hand-renders and hit-tests Start/Retry and Presentation.
  `SFMTerminalPropertiesPanel` rebuilds hand-drawn `Control` rectangles during
  rendering and supports scrolling keys, but not child focus traversal or
  keyboard activation of its tuning controls.
- `SFMKeyBinding` schema 1 stores binding id, action id, command draft, key
  sequence, and enabled state; it has no usage-situation field. The engine
  matches every enabled binding globally. `SFMKeyBindingService` already opens
  `SFMCommandDraftScreen` when a bound command is incomplete, which is a
  verified behavior to preserve rather than redesign.
- `SFMClientActions` and `SFMClientScreenTypes` already use contributor-friendly
  Forge registries. `SFMWorkspaceLayout` already stores split-track shares and
  minimum pixels through `configurePanel`, but there are no user-facing resize
  intents/actions. Deliberate duplication needs a new panel re-instantiation
  contract; reusing a mutable panel instance would alias lifecycle and state.
- The file/review explorers already use bounded logical sources and panel-hosted
  views. `SFMFileExplorerSource` explicitly states that a visible logical path
  does not grant ambient filesystem authority. The address/device design must
  preserve that invariant.
- `SFMClientActionCommandTree.getPaletteSuggestions(...)` currently layers
  fuzzy action-id/title/description ranking and bounded literal-descendant
  discovery over Brigadier. Argument suggestion providers still return one
  final `CompletableFuture<Suggestions>`; the implementation has no contract
  for incremental batches or candidate provenance.
- `SFMCommandPaletteScreen.refreshSuggestions(...)` already captures a
  monotonically increasing `suggestionRevision`, receives completion off-path,
  returns to the Minecraft executor, and drops stale final results. B-1 extends
  that proven lifecycle to multiple batches instead of introducing a second
  palette or permitting background UI mutation.
- `ProgramTokenContextActions` reparses the full program but collapses the
  result to the first `Optional<Runnable>` around one flat cursor offset.
  `SFMTextEditScreenV1` invokes it directly from Ctrl+Space. This is the exact
  one-to-one callback seam B-5 replaces with registered action drafts.
- `SFMFileExplorerModel.selection()` already exposes a source-owned logical
  path and `SFMTextEditorPanel` already knows its editor identity, but
  `SFMTextEditorPanelOpenContext` has no document address/path. Context
  projection therefore requires an explicit optional document identity; a
  title string must never be guessed to be a path.
- Text Editor v3's current `SFMDrawCanvasModel` has true canvas-space cursors
  and may contain multiple cursors. Its document text is a projection of canvas
  glyphs. B-2 must retain coordinate-space identity and the focused cursor,
  with row/column/legacy offset derived only when a text projection can do so
  unambiguously.
- `SFMDeveloperActions` currently registers the three one-off ids targeted by
  SCENE-3. `OpenTitleScreenDevScreenAction` restricts them to `TitleScreen` and
  bypasses panel composition. The old file-explorer action opens the
  deterministic `SFMFileExplorerFixtureSource`; the instance action opens
  `Minecraft.gameDirectory`; the item-icon action opens `SFMItemPickerScreen`
  with a no-op selection callback.
- `OpenPanelAction` already enumerates `SFMClientScreenTypes` and delegates each
  scene's typed Brigadier node to a reopen recipe. Existing text-editor and
  review-explorer screen types prove that scene-specific optional/required
  arguments belong in this grammar, so no new action family is needed.
- The generated Minecraft 1.19.2 library manifest includes
  `org.lwjgl:lwjgl-tinyfd:3.3.1` plus Windows, Windows x86/ARM64, Linux, macOS,
  and macOS ARM64 natives. The bundled class exposes
  `TinyFileDialogs.tinyfd_selectFolderDialog(CharSequence, CharSequence)`, so
  the client can present a native folder chooser without shipping another
  native library. GLFW itself does not define file/folder dialogs; this is the
  optional LWJGL TinyFD module already selected by Minecraft.
- `SFMFileExplorerScreen`/`SFMFileExplorerPanel` already accept
  `Screen.onFilesDrop`, and focused tests/puppets already exercise a dropped
  directory. C-2 can route picker, explicit path, and drop results through one
  root-authorization operation instead of maintaining three workspace models.
- `SFMTextDocumentSource` currently supports only immutable literal text and a
  Minecraft `ResourceLocation`; `SFMTextEditorPanelOpenContext` retains only
  editor id, initial value, read-only state, and title. It cannot yet reopen a
  host file, identify its workspace root, detect stale disk content, or focus a
  source span. C-3 owns those missing contracts.
- The completed CLI Java-analysis work provides canonical
  `symbol show-definition`, live all-source-set analysis, immutable dependency
  indexes, source hashes/spans, typed JSON output, and measured warm
  `DiskItem` definition queries around 2.1 seconds. It does not yet accept a
  document-address plus cursor location or a caller-supplied unsaved snapshot;
  C-4 and the linked CLI plan add that contextual request without duplicating
  the resolver in Java UI code.
- `SFMExplorerPresentationRegistry` already supports ordered contributors and
  render-ready `SFMExplorerPresentation.ItemIcon`; only its generic fallback
  still emits `[D]`/`[F]`. `SFMClientTheme.defaults()` already names
  `minecraft:chest` for `directory` and `minecraft:paper` for `unknown`, so
  C-4a is a file-domain contributor/default-registration correction rather
  than a second icon system.
- `SFMTextDocumentSnapshot` and `SFMTextEditorPanelOpenContext` already carry
  authoritative read-only state. `SFMDrawCanvasScreen` already localizes
  `gui.sfm.text_editor_v3.read_only` and contains an unused contrast-backed
  message renderer, while its bottom `#` and Done controls occupy the intended
  status lane. C-4b makes that state visible with responsive, non-interactive
  geometry instead of introducing another document capability.
- `sfm-propagate-changes` already pins `arborium-java = 2.18.1` and
  `tree-sitter-patched-arborium = 0.25.10`. The local Arborium checkout and
  cached 2.18.1 crates prove that `arborium_java::HIGHLIGHTS_QUERY` is
  available and that `arborium-highlight` can flatten overlapping captures
  into non-overlapping UTF-8 `FlatToken` ranges. The new dependency must use
  `arborium-highlight` without its `tree-sitter` feature so Cargo keeps the
  already-pinned `links = "tree-sitter"` provider and avoids a duplicate
  native tree-sitter link.
- A 2026-08-15 `git ls-files` audit counted the leading tracked extensions as
  `.java` 1,524, `.rs` 400, `.json` 178, `.gradle` 107, `.ps1` 55, `.md` 51,
  `.txt` 45, `.sfml` 21, `.sfm` 15, `.ts` 14, `.toml` 7, and `.g4` 6. The local
  Arborium checkout supplies Java, Rust, JSON, Groovy, PowerShell, Markdown,
  TypeScript, and TOML grammars; Java is this goal, while the remaining
  supported languages are ordered by prevalence/usefulness for later goals.
- `docs/tasks/snapshot episodes and deterministic action environments plan.md`
  already distinguishes parameterized input actions from thousands of global
  registrations and plans deterministic keybinding traces. This plan defines
  the interactive product contract that the episode plan may later record.
- Microsoft Terminal inspected at
  `c334f91f80dfe4c882e60be466622a7a51ca5bb9`, file
  `G:\Programming\Repos\microsoft-terminal\src\cascadia\TerminalSettingsModel\defaults.json`.
  Its defaults bind Ctrl+Shift+W to `Terminal.ClosePane`, Alt+Shift+minus to
  `Terminal.DuplicatePaneDown`, Alt+Shift+plus to
  `Terminal.DuplicatePaneRight`, and Alt+Shift+arrow keys to the four
  `Terminal.ResizePane*` commands. Ctrl+Shift+T opens a new terminal tab; it is
  reference context, not an SFM commitment.

## Confirmed design constraints

1. Action ids remain hierarchical (`sfm:panel/resize/left`, not
   `sfm:panel_resize_left`). Existing move and rotate actions remain.
2. `sfm:terminal` and `sfm:default` are stable keyboard-usage situation ids.
   The implementation may add an SFM workspace ancestor, but may not replace
   these user-visible ids with class names or ephemeral widget identities.
3. Contextual defaults fire only while an applicable SFM-owned focus situation
   is active. Unmatched terminal keys continue to the PTY; unmatched widget keys
   continue through the normal Minecraft child path.
4. Bindings continue to target canonical action command drafts. Incomplete
   drafts open the command palette with their current arguments intact.
5. Click, Enter/Space activation, a default binding, a palette invocation, and
   an explorer link for one semantic control must converge on the same action
   executor/context capture. UI code must not implement a second mutation path.
6. Screen coordinates are valid automation input but not stable public action
   identity. A semantic element address or canonical action is the public link.
7. Address resolution is contextual and may return unavailable with a reason.
   An address never implies filesystem, world, screen, or process capability.
8. Registries/resolvers are contributor-extensible, including adapters for
   Vanilla objects not authored by SFM.
9. Existing user binding files migrate without losing entries. Built-in
   defaults are stable and user disable/remove decisions survive restart via
   explicit override/tombstone state.
10. Version-sensitive Minecraft focus APIs live behind
    `@MCVersionDependentBehaviour` adapters before propagation.
11. Brigadier remains authoritative for command grammar, parse ranges,
    contextual availability, completeness, and execution. Streamed candidates
    may propose replacement text and presentation metadata but cannot execute
    an alternate command representation.
12. Candidate producers never mutate palette widgets from worker threads. A
    query/context generation owns cancellation; the Minecraft executor accepts
    only batches for the still-active generation and originating context.
13. Concrete `SFMAddress` values and wildcard/filter `SFMAddressQuery` values
    are different types. Durable history/outlinks may store concrete addresses;
    unresolved query patterns are not silently promoted to object identity.
14. Path-like context is a labelled collection of contributions with stable
    origin identity, capability, device, and priority. Focusing one panel may
    raise its rank but cannot erase independently addressable explorer/editor
    contributions.
15. Recursive path enumeration uses only resolver-granted bounded devices,
    preserves existing containment/link/exclusion policy, runs off the render
    thread, publishes bounded batches, and reports truncation/errors visibly.
16. Context-action providers emit canonical action drafts plus metadata. They
    do not return `Runnable`, open bespoke dialogs, or bypass the existing
    incomplete-command palette path.
17. Editor context contains an immutable document snapshot/address and a typed
    2D point/selection in a named coordinate space. A UTF-16/byte/character
    offset may be derived for legacy parsers but is not the durable API.
18. Context acquisition is side-effect free. Providers may asynchronously
    enrich a captured snapshot, but they may not read a later focused panel and
    accidentally retarget a choice after the palette opens.
19. `sfm:panel/open` is the sole public opening executor for the new file/item
    explorer scenes. Directional variants automatically receive the same scene
    grammar through `SFMClientScreenType`; no direction-specific explorer
    actions are registered.
20. The three legacy developer action ids are deleted, not hidden or forwarded.
    Persisted command history may display an invalid old string only as generic
    historical text if it already exists locally; no product migration or
    execution compatibility is added for this unreleased surface.
21. Removal is ownership-aware: delete enum cases, action registrations,
    localization, and callbacks used only by the old entry points, but retain
    reusable explorer, source, presentation, and item-picker components still
    referenced by other product behavior.
22. A workspace is a persisted ordered collection of explicit bounded roots.
    It is not synonymous with the process working directory, Minecraft
    instance directory, SFM repository, or an unbounded host filesystem.
23. Root acquisition through TinyFD, command arguments, and file drop converges
    on one normalizer/validator and one semantic add-root action. A native
    chooser result does not bypass containment, duplicate, capability, or
    persistence checks.
24. The native chooser is isolated behind an injectable adapter. Automated
    tests and puppets use fake results, explicit paths, or drag/drop and never
    attempt to automate an operating-system dialog.
25. A file-backed editor retains a concrete workspace/path address and source
    identity independently from its title. Reopening, reveal, search, and
    definition navigation use that address; no visible label is parsed as a
    path.
26. Definition lookup is location-aware. The authoritative query includes the
    originating document, immutable text/hash, source-set/workspace context,
    and exact cursor position; a bare token is display/debug context only.
27. Symbol analysis and process/IPC waits never execute on the Minecraft render
    thread. Requests are generation-tagged and cancellable, and late results
    cannot retarget a changed cursor/document/workspace.
28. One definition opens/focuses its exact source range. Multiple viable
    definitions use the constrained palette; zero or incomplete results remain
    visible with diagnostics and index-refresh/retry actions. The client never
    silently chooses the first match.
29. The in-game contract depends on a registered symbol-navigation provider,
    not on a hard-coded executable path or Vox endpoint. The first provider may
    use the installed SFM CLI/service, while packaged installations report its
    absence truthfully.
30. Workspace paths and source text are local/private by default. Telemetry may
    record counts, durations, hashes, provider ids, and outcome classes, but
    not raw absolute paths or document contents unless an explicit artifact
    asks for them.
31. File icons are selected by an ordered file-path presenter. A file-domain
    directory is chest and a file-domain leaf is paper; expandable/leaf status
    alone must not override richer registry/item presenters in other schemes.
32. Read-only status is derived from the immutable document/open context. It is
    visual and narrated but not clickable, cannot steal focus, and cannot
    overlap or change the hit bounds of the `#` and Done controls.
33. Syntax highlighting is advisory presentation. Unsupported language,
    missing worker, timeout, cancellation, malformed span, or stale hash leaves
    a readable plain/existing-highlight document and never blocks opening,
    editing, closing, or jump-to-definition.
34. Minecraft never parses Java for the new highlighting path and never waits
    for Rust on the render thread. It sends exact text/hash/language/generation,
    validates the versioned response, converts UTF-8 boundaries without
    splitting scalars or CRLF, and publishes only the still-current snapshot.
35. Rust owns Arborium parser/query execution and capture normalization. The
    Java renderer owns applying the returned canonical ChatFormatting names to
    Minecraft components/glyphs; raw HTML or ANSI output never crosses the
    protocol.
36. Compiled grammar/query data is process-lifetime state and parse contexts
    are reused on their bounded worker lane. Rendering consumes immutable
    highlight snapshots; neither side reparses/recompiles on every frame.

## Reversible working assumptions

- The first built-in situation ancestry is `sfm:terminal -> sfm:default`, with
  an SFM-workspace scope supplied by the host. The exact representation is
  finalized in D-2; ids and observable precedence are stable.
- Binding Count sorts by total stored relationships, including disabled
  entries; the row may display enabled/total so the count is not misleading.
- Expose duplicate actions for all four directions for vocabulary symmetry,
  but seed only below and right because those are the pinned Microsoft Terminal
  defaults. Expose all four resize actions and seed all four arrow defaults.
- Do not seed Ctrl+Shift+T until SFM has a separately approved semantic action
  for a new/reopened panel or terminal. Duplicate-pane actions are not called
  “open tab.”
- Begin keyboard-navigation enforcement at runtime over registered SFM action
  elements, then add a static source audit for obvious raw controls. A complete
  proof over arbitrary third-party rendering is not claimed.
- Keep `sfm:keybindings/manage` as the compatibility action id while its visible
  title evolves; the eventual Registry Explorer is a panel scene rather than a
  forced replacement for every full-screen caller in its first slice.
- Name the first file-search action `sfm:path/open` and seed Ctrl+Shift+N with
  the incomplete canonical draft `sfm action invoke sfm:path/open `. The final
  id remains subject to D-11, but the implementation must preserve the
  incomplete-draft flow rather than add a hard-coded key handler.
- Search all distinct contextual roots concurrently and rank roots from the
  focused child/panel ahead of visible explorer selections and other visible
  document parents. Show device/root labels and deduplicate only identical
  canonical concrete addresses; do not discard a second origin merely because
  its display path matches.
- Treat Alt+Enter as the primary default for contextual actions. Ctrl+Space may
  remain a compatibility binding during migration, but both gestures resolve
  the same semantic action and constrained palette rather than two provider
  systems.
- An editor contributes the focused 2D cursor first, optional selection/range,
  the complete immutable document snapshot, and all additional cursors as
  supplemental context. Providers decide which shapes they support and explain
  unavailability instead of forcing the host to flatten them.
- A generic explorer owns a session-local path-expression location. One root is
  normally hoisted; adding a second root creates an ephemeral versioned
  selection containing both. Persistent named selections/collections require
  a later explicit schema and are not called a global workspace.
- Keep `sfm_source` only as historical provenance for the rejected typed
  file-scene proposal. The development launcher may contribute the exact
  `<branch>/platform/minecraft/src` root, but it translates that root to an
  ordinary resolver-authorized path expression before opening the generic
  explorer; users never assign a role or invoke an `sfm_source` scene/device.
  Generic explorers derive project/source-set metadata. A packaged install
  without an explicit source root reports source navigation unavailable and
  offers the ordinary add-root action rather than guessing.
- Open host source files read-only in the first navigation slice. The address,
  disk hash, editor snapshot, dirty-state seam, and save-policy capability are
  still modeled so later editing cannot accidentally overwrite a stale file.
- Seed F12 only in the `sfm:text_editor` usage situation for
  `sfm:symbol/definition/open`; also expose the same action through Alt+Enter.
  Do not claim a global Minecraft F12 mapping.

## Design gates

| Gate | Decision required | Working recommendation | Acceptance consequence |
| --- | --- | --- | --- |
| D-1 Duplicate state semantics | Does duplicate create an independent panel from a re-open recipe, alias the same live object, or vary by panel type? | **Closed by K-4 (2026-08-06):** independent panel from an immutable typed scene/address recipe. Terminal duplicates create distinct sessions; explorers/editors share only immutable selector/document/path inputs and own independent focus/scroll/dirty/model state. A directory drop replaces the explorer's normalized-root recipe. Panels without an exact recipe report unavailable; a recipe that aliases an attached mutable panel is rejected. | K-4 tests lifecycle independence, terminal session identity, source sharing, unsupported panels, stacks, close behavior, and live action/default dispatch. |
| D-2 Situation ancestry and precedence | How do focused element, panel, workspace, and optional global situations compose when bindings overlap or a sequence is partial? | **Closed by K-3 (2026-08-06):** `sfm:terminal -> sfm:default -> sfm:workspace -> sfm:global`; resolve deepest to broadest, let a deeper viable partial reserve before a broader completion, let a same-depth completion win over a longer same-depth prefix, diagnose equal-depth completions without invoking either, and forward a failed/mismatched partial's current stroke exactly once. | K-3 registry/matcher tests plus the real Forge pre-screen live witness prove precedence, reset, conflict, and fallback. |
| D-3 Semantic action boundary | Must every editor/navigation impulse be a registry entry, or only semantic operations with parameterized low-level input beneath them? | Register semantic operations and stable parameterized action kinds; do not register every character or coordinate. All visible buttons and non-text shortcuts still expose a semantic action contribution. | K-5 audit has explicit categories and exemptions instead of either thousands of actions or silent hard-coded behavior. |
| D-4 Action Explorer naming | Rename the current surface immediately, or introduce the addressable explorer first and migrate the old title later? | Use “SFM Actions & Shortcuts” during K phases; introduce `sfm:registry_explorer`/action projection in A-4, then retire or redirect redundant presentation only with live parity. | K-6 and A-4 user-facing names, compatibility, and migration tests differ. |
| D-5 Address text grammar | Freeze the exact serialized spelling for resolver id, registry id, entry id, device id, path segments, escaping, and fragments. | First define typed components and fixture round trips. Preserve `sfm:registry/minecraft/item/minecraft/stick`, `sfm:registry/sfm/client_actions/sfm/developer/open_text_editor`, `sfm:path/options.txt`, and `sfm:path/c/tmp/a.txt` as proposed-intent fixtures, but do not rely on ambiguous unescaped slash concatenation. | A-1 cannot expose persistent links/history until canonical parse/print fixtures are approved. This does not block K phases. |
| D-6 Release boundary | Which K/A items are required before the candidate release? | K-1 through K-7 are release-correctness work. A-1 freezes addresses before public owner ids become durable; A-2a/A-2b through A-5 may follow as feature graduation unless the Action Explorer is declared release-blocking. | The coordinating release plan must record the approved cutoff before final release acceptance. |
| D-7 Candidate composition versus pipes | Does `gci -Recurse \| fzf` require a general user-visible pipe grammar? | **Working recommendation:** no. Add typed, in-process candidate sources with batch publication, cancellation, ranking, and a palette sink. Keep the internal producer/consumer seam composable so a later pipeline design is possible without exposing one now. | B-1 can proceed without parser/process-pipe scope; adding public pipes remains a separately approved plan. |
| D-8 Editor coordinate contract | Is the authoritative editor position canvas x/y, text row/column, flat offset, or all of them? | **Approved for B-2 on 2026-08-14:** a typed coordinate-space value: Editor v3 supplies canvas x/y plus an optional resolved text row/column and selection; legacy editors adapt row/column and derive parser offsets with explicit encoding. | B-2 fixtures must prove conversions, multi-cursor retention, out-of-glyph points, Unicode, line endings, and no ambiguous silent coercion. |
| D-9 Ctrl+Space migration | Is Ctrl+Space removed immediately when Alt+Enter ships, retained as an alias, or left on the legacy callback? | **Working recommendation:** retain it temporarily as a contextual binding to the same registered `context/actions/open` action, remove the direct callback, document Alt+Enter as primary, and decide removal only after live parity. | B-5 tests one provider registry/palette path for both gestures and no direct `Runnable` dispatch. |
| D-10 Ctrl+Shift+E fallback | When the focused object has no revealable address, should the gesture focus an existing explorer, open a generic explorer, open an incomplete reveal draft, or report unavailable? | **Working recommendation:** capture the focused address and compatible explorer target set into an explicit action draft. Prefer an exact visible compatible explorer according to deterministic recency; otherwise use explicit non-exact `open-new`. Multiple equally ranked addresses open the constrained palette rather than guessing. | B-4 action vocabulary/default and live behavior remain provisional until accepted; the final action contains an explorer selector rather than consulting focus during execution. |
| D-11 Public action/address-query vocabulary | Freeze names and quoting for file open, explorer focus/reveal, contextual actions, path-exists/contents examples, and wildcard address queries. | **Partially closed by A-2c/C-3:** `sfm:path/open` is frozen. Remaining hierarchical provisional ids are `sfm:explorer/focus <explorer-selector>`, `sfm:explorer/reveal <explorer-selector> <path-expression>`, and `sfm:context/actions/open`. Context providers materialize exact/self-contained drafts; interactive callers may explicitly spell `focused`. Preserve the user's `a.txt`, path-exists/contents, and `sfm:item:minecraft:*wood*` examples as parse/offer fixtures. | B-3 through B-5 later freeze the search/reveal/context ids and their public history/default fingerprints. |
| D-12 File-result activation | What does accepting a path result do, especially for directories, binary files, dirty editors, and non-workspace callers? | **Closed by C-3:** Space updates an explorer-owned read-only Text Editor v3 preview and retains explorer focus; Enter opens/focuses that safe target; Ctrl+Enter creates/focuses a new adjacent visible panel. Directories remain explorer navigation, unsupported files stay visible with a reason, and no path replaces a dirty editor, terminal, or unrelated panel. A non-workspace caller opens the ordinary workspace host first. | B-3 later reuses the proven action/placement contract for fuzzy results instead of inventing another preview policy. |
| D-13 No-context search root | What should Ctrl+Shift+N search when no panel contributes a path? | **Working recommendation:** use explicitly contributed resolver roots, including the current Minecraft instance run directory as the bounded packaged/dev contribution; show “no bounded roots available” if none exists. Visible explorer locations and named selections are contextual roots, not a global workspace catalog. Never fall back to a drive root, user profile, or process working directory. | X-1/X-4 and A-2a/B-3 default-device fixtures and the empty-context live state depend on the accepted fallback. |
| D-14 `sfm_source` meaning | Which bounded root/device does the historical `sfm:explorer/file sfm_source` literal identify? | **Closed by the generic heterogeneous-explorer cutover:** that public literal/device is not implemented. `sfm_source` remains provenance for the rejected scene proposal only. A development launcher or deterministic puppet may supply the exact branch `platform/minecraft/src` root, but it becomes an ordinary resolver-authorized path expression. Direct `sfm explorer root add` and UI/drop/picker adapters add typed roots to explicitly selected explorers; project/source-set classification is derived. Missing explicit source authority is visibly unavailable. Never use a fixture, process directory, or upward directory search as a substitute. | A-2c proves the old scene/literal is absent; C-3 proves explicit source-root available/unavailable behavior. Selection/explorer X-1 through X-7 and control I-4 prove generic roots with no public role-assignment action. |
| D-15 Native folder chooser lifecycle | Which API owns native folder selection and how is its blocking/modal lifecycle isolated from Minecraft? | **Working recommendation:** use Minecraft's bundled `TinyFileDialogs.tinyfd_selectFolderDialog` behind `SFMFolderPicker`; execute through a bounded platform-aware async/modal coordinator, suppress duplicate opens, restore game focus after completion, and submit the result to the same exact-selector root/add action on the Minecraft executor. A fake adapter proves cancel/success/failure. | A later post-X adapter may not call TinyFD directly from panel rendering, hide the explorer selector, create a separate catalog, or make OS-dialog automation part of completion. |
| D-16 Explorer location ownership and persistence | Does root management mutate a focused explorer, a named collection, or a global singleton, and where is it persisted? | **Closed by the 2026-08-12 selection/explorer supersession:** each explorer session owns an explicit path-expression location. Adding a second root creates/uses a versioned selection-backed location. The first slice is game-session-only; no global default workspace or provisional disk format is introduced. Every action carries an explorer selector, with explicit non-exact open-if-none policy. Persisted named selections/collections require a later schema/migration goal. | Selection/explorer X-1 through X-7 prove exact/focused/all targeting, heterogeneous roots, session lifetime, and no accidental persistence. C-3 consumes the resulting paths. |
| D-17 File-editor mutation boundary | Is opening an explorer-addressed file editable in the first slice? | **Closed by C-3:** open read-only initially, retain address/hash and the capability seam for later writable documents, and never overwrite host source as a side effect of navigation. | C-3 delivers safe source browsing/jump navigation without inventing save/conflict semantics; later write support is a separate approved goal. |
| D-18 Definition-at-cursor request | What exact data identifies the symbol to resolve? | **Closed by C-4 on 2026-08-15:** document concrete address/root/source-set, immutable source text plus content hash, UTF-aware row/column and derived byte offset, branch/classpath/index identity, provider-origin request generation, and worker-global workspace generation. The provider resolves the symbol at that location and returns typed spans; the Java client does not first reduce it to a bare token. | C-4 scenarios and tests cover imports, same simple name in multiple packages, fields, overloaded methods, constructors, dependency symbols, whitespace/no-symbol, stale disk text, CRLF, Unicode, malformed positions, and duplicate root-relative paths. |
| D-19 First symbol-provider transport | Should Minecraft spawn the installed CLI per request, hold a long-lived worker, or call a Vox service? | **Closed by C-4 on 2026-08-15:** the provider-neutral Java contract uses one supervised long-lived `sfm-propagate-changes` symbol worker with framed typed requests/responses. It reuses the existing Java-analysis engine and bounded immutable resolution surfaces, remains replaceable by a Vox adapter, fails visibly when unavailable, and does not spawn one process per cursor query. | C-4's direct/worker parity, lifecycle/cancellation/process tests, install discovery, and 72.001 ms installed warm median are the implementation evidence. |
| D-20 File icon domain | Should chest/paper replace the generic marker for every expandable/leaf object or only filesystem-backed rows? | **Closed for C-4a:** add an ordered file-path presenter. `file://` directories resolve to the theme's `directory` ItemStack (`minecraft:chest`) and `file://` leaves resolve to the theme's ordinary/unknown file ItemStack (`minecraft:paper`). Item-registry and future domain presenters retain precedence; only a truly unclaimed non-file object reaches `[D]`/`[F]`. | Presenter tests must assert path kind, actual resolved item id, contributor precedence, fallback behavior, narration/label stability, and list/small-icon geometry. |
| D-21 Read-only status geometry | Is read-only communicated as a floating toast, a button, title suffix, or stable editor chrome? | **Closed for C-4b:** render localized `Read-only` as stable, non-interactive bottom chrome centred in the free lane between `#` and Done, on a solid high-contrast rectangle. Keep the narration suffix. At narrow widths, clamp/trim the status within the free lane without overlapping either button; writable documents omit it. | Geometry/render tests and the GUI-scale puppet must prove visibility, contrast, no hit/focus target, neighbour-button parity, and no document-content dependence. |
| D-22 Syntax worker/process ownership | Should highlighting run in Java, spawn per document, extend the heavy symbol worker, or use a dedicated long-lived Rust lane? | **Closed as a reversible first implementation:** add `sfm-propagate-changes syntax highlight` plus supervised `syntax serve`. It reuses the existing bounded frame-codec/process-lifecycle patterns but does not require symbol-workspace/index startup and does not spawn per document/request. One client service shares the worker across editors; a later generic code-intelligence daemon may unify processes without changing the provider contract. | CLI direct/worker byte-equivalence, one-process reuse, cached-query evidence, crash/restart/cancel/timeout tests, and a Java missing-worker fallback are required. |
| D-23 Highlight wire and style contract | What offsets and presentation data cross Rust/Java? | **Closed for schema 1:** request id/generation, language id, exact UTF-8 source, SHA-256, and bounded options go to Rust. Rust returns the same identity plus Arborium/parser fingerprint and sorted, non-overlapping `[start_byte,end_byte)` ranges containing a stable Arborium theme tag and ordered canonical lower-case ChatFormatting names. Java rejects unknown/invalid/split/stale spans and converts valid UTF-8 boundaries to the current glyph projection. No HTML/ANSI crosses the wire. | Facet JSON snapshots and Java codec tests cover empty/overlap, astral Unicode, combining marks, CRLF, trailing newline, malformed ranges/styles, hash mismatch, deterministic order, and direct/worker parity. |
| D-24 Language and fallback policy | How is language selected, what ships first, and what happens without support/service? | **Closed for this goal:** an explicit language registry derives `.java -> java` from the concrete document path and sends that id; Rust remains authoritative for whether the language is available. Java is the sole new Arborium grammar enabled. Unsupported/no-path documents retain the existing SFML/G4/plain presentation; unavailable/failed/stale Rust results leave readable text and a bounded diagnostic/telemetry state, never a modal or blocked editor. | C-4c records the extension audit; C-4d tests Java selection, unsupported extension, missing executable, cancellation/staleness, and unchanged existing highlighters. |
| D-25 Ctrl+click versus existing cursor behavior | EditorV3 currently uses Alt+click to add a cursor and Ctrl+click to move all cursors. Does definition navigation replace Ctrl+click everywhere or only when a symbol is actionable? | **Closed for this goal:** an exact current-hash hover target intercepts Ctrl+click and submits definition navigation; with no actionable target, retain the existing cursor behavior. Alt+click remains multi-cursor. Never begin a text drag and a navigation action from the same press. | C-9 tests actionable/unavailable/stale targets, modifier transitions, drag thresholds, and current multi-cursor parity. A different global replacement requires explicit approval. |
| D-26 Definition/reference target placement | Should an unseen source target create a split, a tab in the current pane, a preview owned by a result explorer, or something else? | **Closed for this goal:** direct F12/Ctrl+click/Alt+F7 preserve the originating pane and push a new panel entry/tab there after exact-visible reuse. A reference explorer retains its own pane and opens results through its owned preview target. Only explicit directional actions create new geometry. | C-7/C-8 use self-contained captured pane/result-owner ids and nested layout tests; no fallback calls `openRight` implicitly. |
| D-27 Alt-click reference binding | Should Alt-click also find references? | **Closed for this goal:** do not bind it. Alt+click already adds EditorV3 cursors and the user described Alt-click as “maybe”; Alt+F7 plus Alt+Enter/right-click provide complete reference access without a conflict. | C-9 asserts no Alt-click reference default. If later approved, the plan must state the replacement/modifier precedence and migrate the multi-cursor gesture deliberately. |
| D-28 Java platform/source completeness | How do JDK declarations and source-root authorization compose with the existing live-workspace plus dependency-source index? | **Closed for this goal:** add a branch-JDK source domain keyed by selected JDK release/home plus `src.zip` content identity, model implicit `java.lang`, and compose resolver-authorized editor roots with negotiated worker source mappings by concrete containment/source-set identity. Never downgrade a missing platform source to authoritative `NoSymbol`. | CLI Phase 0.12/C-7 scenarios must resolve `String`, `Object`, and `StringBuilder`, preserve partial-index diagnostics, and reproduce both quoted false negatives before the fix. |
| D-29 Large-document responsiveness budget | What evidence is sufficient to call EditorV3 responsive? | **Closed for this goal:** on the declared baseline and exact `OutputStatement.java`, record cold open separately; during warm idle, pointer movement, scroll, selection, and F12-context capture, target median editor-attributed frame work <=16.7 ms, p95 <=33.3 ms, no editor-attributed pause >=100 ms, and input-to-visible p95 <=50 ms. If hardware/host load invalidates a bound, preserve raw traces and seek user approval rather than silently weakening it. | C-10 instruments glyph visits, projection/style/selection cache rebuilds, allocations, worker calls, and event-to-frame latency; C-11 retains before/after evidence and visual approval. |
| D-30 Hotkey lookup capture and ranking | Is lookup a binding-editor operation, a normal palette query, or a separate keyboard-capture mode, and which “derivatives” are eligible? | **Closed for K-8:** use a palette-owned inspection session entered by a focusable action element; freeze the originating situation/action/panel/element and binding revision before the palette replaces that context; simulate the runtime deepest-situation/partial-sequence/conflict matcher for effective exact results; label shadowed/conflicting exact rows separately; then rank same-stroke strict modifier-subset bindings as related results that would not fire for the captured input. No arbitrary key substitution or execution is permitted. | K-8a freezes stable ids/capture timing/release classification; K-8b through K-8e implement exact lookup, related sequences, lifecycle/accessibility, and live evidence. |

## Target action and binding vocabulary

The planned public action family is:

```text
sfm:panel/close
sfm:panel/resize/left|right|above|below
sfm:panel/duplicate/left|right|above|below
sfm:panel/diagnostics/open
sfm:panel/focus/left|right|above|below|next|previous
sfm:panel/focus/index <1..9>
sfm:panel/maximize/toggle
sfm:panel/scale/increase|decrease|clear
```

Explorer opening is scene grammar below that action family, not another action
family. After the X-4/X-5 heterogeneous-explorer foundation, the cutover target
is the generic scene:

```text
sfm action invoke sfm:panel/open sfm:explorer [path-expression]
sfm action invoke sfm:panel/open sfm:explorer registry://minecraft/item/
sfm action invoke sfm:panel/open sfm:explorer file:///D:/Repos/Minecraft/SFM/repos2/1.19.2
```

The same generic scene appears under `sfm:panel/open/left|right|above|below`
automatically. Resolver-contributed roots such as the bounded current instance
directory or an exact development-toolchain source root are translated into
typed path expressions before execution; they do not become explorer subtypes
or public role names.
Deterministic fixture sources remain test-only and do not regain a public
`developer/...` action.

Explorer and source navigation use hierarchical semantic actions:

```text
sfm:explorer/root/add <explorer-selector> <path-expression> --if-no-match fail|open-new
sfm:explorer/root/pick <explorer-selector>
sfm:explorer/root/remove <explorer-selector> <path-expression>
sfm:explorer/root/move/up <explorer-selector> <path-expression>
sfm:explorer/root/move/down <explorer-selector> <path-expression>
sfm:explorer/location/edit <explorer-selector> [focused|left|right|above|below] [editor-id]
sfm:explorer/location/set <explorer-selector> <path-expression> --expected-revision <revision>
sfm:explorer/reveal <explorer-selector> <path-expression>
sfm:path/open <concrete-path-address>
sfm:symbol/definition/open
sfm:symbol/references/open
sfm:context/actions/open
```

Every root action contains an explorer selector. Widgets emit exact ids;
interactive keyboard actions may explicitly spell `focused`; none falls back
to an implicit default. `root/pick` is the later native-dialog adapter,
`root/add` is deterministic, and a folder drop submits the same exact-selector
intent. Contextual definition providers capture the focused editor
document/cursor into an immutable context value before producing the final
action; execution does not reread an unrelated later focus state.

`sfm:symbol/references/open` is the UI-facing “Find References” operation and
uses the CLI engine's `symbol list-usages` vocabulary internally. It captures
the editor document/hash/position exactly once and opens a versioned generic
reference-result explorer; it is not a transient candidate chooser. F12,
Ctrl+click, Alt+F7, Alt+Enter, and right-click are bindings/gestures over these
registered semantic actions, never alternate parsers or hidden callbacks.

The explorer header gesture emits exact-id `location/edit`, whose default
placement is `right`; that action delegates to the ordinary typed
`OpenPanelAction` text-editor recipe and uses the configured preferred editor
when no editor id is supplied. The editor displays the exact canonical location
expression—including internal selection ids—and emits `location/set` with the
captured expected revision after parsing the edited expression. These action ids describe semantic intent while
reusing panel placement and the existing action history/keybinding surface.

The following ids must not exist after A-2c:

```text
sfm:developer/open_file_explorer
sfm:developer/open_instance_file_explorer
sfm:developer/open_item_icon_picker
```

The exact focus/maximize ids are provisional until K-5 inventories the current
hard-coded surface. Existing `sfm:panel/move/...`, open, rotate, and tuning
actions remain. Default relationships are declared data with stable ids:

| Situation | Key/chord | Canonical draft | Source |
| --- | --- | --- | --- |
| SFM workspace/default | Ctrl+Shift+W | `sfm action invoke sfm:panel/close` | User requirement + Microsoft Terminal ClosePane |
| SFM workspace/default | Alt+Shift+Down | `sfm action invoke sfm:panel/resize/below` | Microsoft Terminal ResizePaneDown |
| SFM workspace/default | Alt+Shift+Left | `sfm action invoke sfm:panel/resize/left` | Microsoft Terminal ResizePaneLeft |
| SFM workspace/default | Alt+Shift+Right | `sfm action invoke sfm:panel/resize/right` | Microsoft Terminal ResizePaneRight |
| SFM workspace/default | Alt+Shift+Up | `sfm action invoke sfm:panel/resize/above` | Microsoft Terminal ResizePaneUp |
| SFM workspace/default | Alt+Shift+- | `sfm action invoke sfm:panel/duplicate/below` | Microsoft Terminal DuplicatePaneDown |
| SFM workspace/default | Alt+Shift++ | `sfm action invoke sfm:panel/duplicate/right` | Microsoft Terminal DuplicatePaneRight |
| SFM workspace/default | Ctrl+= (displayed `Ctrl =`) | `sfm action invoke sfm:panel/scale/increase` | I-KEY-1 / KBIND-5 correction |
| SFM workspace/default | Ctrl+- (displayed `Ctrl -`) | `sfm action invoke sfm:panel/scale/decrease` | I-KEY-1 / KBIND-5 correction |
| SFM workspace/default | Ctrl+0 (displayed `Ctrl 0`) | `sfm action invoke sfm:panel/scale/clear` | I-KEY-1 / KBIND-5 correction |
| SFM workspace/default | F3 | `sfm action invoke sfm:panel/diagnostics/open` | Existing workspace behavior made discoverable |
| SFM text editor | F12 | `sfm action invoke sfm:symbol/definition/open` | WSPACE/SYMBOL definition-navigation requirement |

Binding storage records physical keys plus exact modifier sets. The increase
default therefore matches GLFW Equal+Control (the Ctrl+= gesture), not a
logical plus synthesized by requiring Shift and not a duplicate keypad-add
relationship. One token model drives spaced text fallbacks and distinct pink
keycaps, and keeps main/keypad names unambiguous when a user explicitly records
either physical key. K-6 adds capture-time focus/removal behavior to those
keycaps.

## Widget, focus, and action-element contract

K-1 introduces a panel-local child host rather than making every panel a full
Minecraft `Screen`. It owns ordered focusable/renderable/narratable children,
one focused child, local-to-workspace coordinate transforms, lifecycle, and
version-adapted Tab/Shift+Tab/controller navigation. The multiplexer remains the
global Screen owner and routes to the focused visible panel's child host.

Each actionable child contribution provides:

- a stable element id/address, user-visible title, narration, and current
  enabled/visible state;
- a keyboard-usage situation id;
- a canonical action draft or a bounded contextual action-draft supplier;
- one activation path shared by pointer, Enter/Space, palette, and binding; and
- optional property/outlink contributions for the later explorer.

The terminal viewport is one focusable child in `sfm:terminal`. Start/Retry,
Presentation, dropdown rows, and properties controls are ordinary
`sfm:default` children. Ordinary Tab traverses controls when a control owns
focus. Tab sent to the terminal remains PTY input until the established
triple-Tab escape transfers focus back to the host traversal; triple Escape
retains its established constrained-palette behavior. Clicking the viewport
focuses the terminal child; clicking controls focuses the exact control.

## Contextual binding contract

`SFMKeyboardUsageSituation` is a contributor registry entry with stable id,
title/description, and declared ancestry/compatibility metadata. The active
input context is a snapshot captured at dispatch: current screen, workspace,
focused panel id, focused action-element id, deepest usage situation, ancestry,
and an originating-host validity check. Bindings store a situation id in schema
2. Global user bindings use an explicit global situation; omission does not
silently mean global.

The matcher remains deterministic and event ordered. It resets partial matches
when the binding snapshot, focus situation, or window focus changes. Contextual
availability is checked before consuming a completed binding. A complete
binding invokes once; an incomplete canonical command opens the palette; an
unmatched event falls through to the focused widget/panel/terminal. Conflict
queries compare only situations whose active domains can overlap.

Built-in defaults are immutable definitions plus persisted user overrides or
tombstones. They are not blindly copied into the user's list on every launch.
Schema-1 entries migrate to an explicit global/user situation so existing
behavior is preserved; the UI invites narrowing but does not silently change
scope.

## Semantic action ownership and audit contract

K-5 inventories SFM-owned `Screen`/panel key handlers, raw hand-drawn controls,
and widget callbacks. Every item is classified as:

1. registered semantic action;
2. parameterized low-level input action owned by a widget/editor/terminal;
3. focus/navigation behavior supplied by the host contract; or
4. documented non-action observation with no user mutation.

Visible mutation controls require an action-element contribution. The first
non-panel proof is Manager Edit: the button and its keyboard activation resolve
the same contextual manager/disk edit action and expose that relationship to
the inventory. Do not introduce a public coordinate click action. If a generic
live-element activation command is needed, it takes a stable element address
and validates the captured host/context before invoking that element's semantic
action.

The runtime audit walks registered SFM child hosts and reports visible/enabled
action elements that are unreachable by focus, missing narration, missing a
situation, or missing a semantic action contribution. A static audit searches
SFM source for raw control/event patterns and requires either a registered
contribution or an explicit bounded exemption. Static output is a guardrail,
not a claim that arbitrary drawing code is decidable.

## Address and resolver contract

`SFMAddress` is immutable data containing a resolver `ResourceLocation` and a
resolver-owned typed payload. `SFMAddressResolver<C, A>` parses/prints that
payload, declares the context capabilities it requires, and resolves to a
bounded descriptor: title, kind, properties, semantic actions, and typed
outlinks. Resolution may be unavailable or diagnostic; it does not mutate by
default. Contributor registries permit SFM adapters for Vanilla registries and
screen behaviors without modifying their classes.

The registry resolver addresses a registry and optionally an entry. Its first
vertical fixture includes the SFM client-action registry and one Vanilla item
entry, with action -> binding/situation links and item -> corresponding block
links only where a real relationship exists. The path resolver includes an
explicit logical device id. Device adapters enforce normalized relative paths,
root containment, read/write policy, and lifecycle. Initial devices are a
read-only fixture/VFS and the bounded Minecraft instance run directory; no
drive-letter or `..` spelling escapes the declared root.

The existing explorer model is adapted to generic address nodes instead of
assuming everything is a host file. An object-properties panel resolves one
address. A Registry Explorer enumerates resolver/registry roots and children.
The Action Explorer is the client-action projection, showing action metadata,
usage situations, default/user bindings, and live action-element owners as
typed links.

Concrete and query forms are separate. `SFMAddress` identifies one durable
resolver-owned object when the resolver can do so. `SFMAddressQuery` carries a
resolver id plus resolver-owned pattern/filter payload and may resolve to zero,
one, or many candidates. A query can be used by search/reveal actions and
history only when explicitly serialized as a query; it is never emitted as the
canonical address/outlink of a resolved object. Resolver results may declare
which explorer projection can reveal them.

## Palette streaming and fuzzy-candidate contract

B-1 adds a palette layer around, not inside, Brigadier's execution semantics.
An `SFMPaletteCandidate` has a stable candidate key, Brigadier replacement
range/text, display title/detail, provider id, optional concrete address or
canonical action draft, ranking signals, and executable/diagnostic state.
`SFMPaletteCandidateSource` receives a captured action/context snapshot, parsed
Brigadier frontier, active argument descriptor, query text, generation, and
cancellation signal. It may publish zero or more immutable bounded batches and
one terminal completed/truncated/failed state.

The palette starts ordinary Brigadier completion and applicable streamed
sources together. One accumulator merges candidates by stable key, preserves
Brigadier replacement ranges, deduplicates identical canonical completions,
and orders deterministically by availability, exact/prefix/fuzzy match,
context-root proximity, provider priority, stable title, and stable key. Recent
command history remains an explicit ranking signal; streamed arrival order is
never a tie breaker. When a batch changes ordering, selection follows the same
stable candidate key if it still exists instead of jumping back to row zero.

Each input/context change cancels the previous generation and increments the
existing palette revision. Worker threads may enumerate/score immutable data,
but only the Minecraft executor applies a batch after verifying palette
identity, generation, originating-host validity, and active argument frontier.
Closing the palette cancels all producers. Loading, truncation, provider error,
and “no bounded roots available” are visible non-executable rows/status, not
empty-list ambiguity. Slow-provider telemetry records provider id, first-result
latency, completion latency, candidates visited/emitted, cancellation, and
truncation without logging private path contents by default.

This contract is the bounded equivalent of the producer/filter/ranker shape in
`gci -Recurse | fzf`. It is intentionally not a shell AST, process pipeline,
PowerShell bridge, or public general-purpose pipe grammar.

## Context projection and contextual-action contract

`SFMContextSnapshot` is immutable and origin-captured. It retains the current
screen/workspace, focused panel and focused child, plus an ordered collection
of typed `SFMContextProjection` contributions from relevant visible panels.
Every contribution has a stable origin id, kind, address/query when available,
capabilities, display label, and focus/proximity rank. Hosts do not reduce the
collection to one “current path.” A file explorer contributes its selected
entry and source/device identity; a text editor contributes its document
identity independently. A title or visible path string alone never grants
filesystem authority.

Editor projection carries the complete immutable document snapshot, optional
document address, read-only state, editor id, focused cursor, selection/range,
and supplemental cursors. A position identifies its coordinate space. Text
editors can expose row/column; Text Editor v3 exposes canvas x/y and may attach
the glyph/text row/column hit derived at capture time. Legacy flat offsets are
explicit derived views with documented UTF-16/Unicode/line-ending semantics.
Out-of-glyph canvas points remain valid spatial context instead of being
rounded to unrelated text.

`SFMContextActionProvider` is a contributor registry. A provider declares the
projection shapes/capabilities it accepts and returns zero or more
`SFMContextActionOffer` values containing canonical action drafts, title,
reason/diagnostic, source projection ids, and stable ranking metadata. Multiple
providers and multiple offers may match one context. Offers are data, never
`Runnable`; Brigadier validates availability and asks for missing arguments
through the normal palette flow.

Alt+Enter invokes one semantic contextual-actions action and opens the existing
constrained palette over those offers. The legacy program provider adapts the
whole document plus 2D point into AST/token facts and emits all applicable
offers, replacing the current first-match callback. The `a.txt` path
existence/content and `sfm:item:minecraft:*wood*` reveal examples are mandatory
fixtures, not a mandate to special-case strings in the host.

The context shape reserves typed spatial projections with coordinate space,
screen point, ray origin/direction, and hit/provider metadata. This permits a
later arbitrary-point HWYLA/Jade/WAILA-like provider without making screen
centre, current mouse position, or world lookup part of B-1 through B-6.

## Contextual file-root and reveal contract

Path search consumes only path-capable projections whose resolver grants a
bounded device. The default root set is the union of distinct canonical roots
derived from the focused document/selection, visible explorer selections, and
other visible addressed documents, followed by explicitly registered workspace
defaults. File contributions normally seed their parent; directory
contributions seed themselves. The UI labels device/root provenance, boosts the
focused contribution, searches distinct roots concurrently, and deduplicates
only equal canonical concrete addresses. It does not start at a drive root when
the set is empty.

Enumeration reuses the path resolver's normalization, containment, symlink,
exclusion, read-capability, and lifecycle rules. It is incremental and bounded
by configurable/testable depth, visited-entry, emitted-candidate, elapsed-time,
and batch-size budgets. Hitting a bound produces an explicit partial-result
state with an action to narrow/change roots; it does not silently claim the
search was exhaustive.

Reveal is capability-based. A resolved address/query contributes a compatible
explorer scene plus a reveal intent. Existing compatible explorers are
preferred according to D-10; otherwise panel opening uses the established
placement/preview rules and must not replace a terminal or unrelated workflow
panel. File, item, registry, and future domain adapters register their behavior
through resolver/explorer capabilities rather than a central type switch.

## Generic multi-root explorer and native-folder adapter contract

The authoritative model is `ExplorerSession` from
`typed selections relations and lazy explorers plan.md`, not
`SFMExplorerWorkspace`. Each explorer has a stable session id, a typed
path-expression location, independent projection/navigation/expansion/request
state, and a versioned child relation. A one-root location may be shown hoisted;
adding another heterogeneous root creates an ephemeral selection containing
both roots and reveals their parents. Root membership is a set, optional manual
order is projection metadata, and view/sort/group never mutate membership.

Every mutation takes an explicit set-valued explorer selector. The executor
captures matching ids once, stages and preflights all targets, revalidates on
the Minecraft thread, and publishes all-or-none with per-target outcomes. A
missing exact id fails. An explicit non-exact `open-new` policy may create one
generic explorer through the normal panel-open route. Explorer state is
session-local in the first slice; no default global catalog or persistence
schema is introduced.

`SFMFolderPicker` is a later input adapter returning
selected/cancelled/failed. The production 1.19.2 adapter may use bundled LWJGL
TinyFD; tests inject a fake. Its suggested directory comes only from an
authorized selected explorer root or contributed instance device. The modal
coordinator permits one active picker, prevents key leakage, performs no
traversal itself, and submits the canonical result to the same exact-selector
root/add action after returning to the Minecraft executor. Explicit action,
external CLI, picker, and folder drop therefore share one authorization and
transaction boundary.

The development toolchain may contribute an exact selected-branch
`platform/minecraft/src` path through explicit launch configuration. It is
launcher data translated to an ordinary path expression, not upward directory
discovery, a public `sfm_source` device, or a root role. Packaged clients without
explicit source authority report unavailable. A user may instead add a
resolver-authorized repository/source path through `sfm explorer root add`;
derived project/source-set metadata then supports search and jump-to-definition.

## File-backed editor and symbol-navigation contract

A file opened from an explorer becomes a concrete path-addressed
`SFMTextDocumentSource`. Its reopen recipe stores the address/root identity,
not an unrestricted absolute path capability; loading re-resolves through the
current path device, records source bytes/text hash and encoding/line endings,
and returns a visible unavailable/stale result when authority or content has
changed. The first slice opens read-only. The editor open intent optionally
contains a target source range and places the primary cursor/selection there
after the exact snapshot is loaded. Existing literal/resource documents remain
supported but cannot claim host-file navigation capabilities.

`SFMSymbolNavigationProvider` accepts an immutable `DefinitionAtPositionRequest`
and returns an asynchronous, cancellable `DefinitionResult`. The request owns
resolver, selection/relation, and request generations; branch/source-set/
classpath/index identities; document
address; exact source text/hash; UTF-aware row/column and derived byte offset;
and request generation. Results contain zero or more canonical symbol ids and
concrete target document addresses/ranges, plus confidence, diagnostics,
completeness, source/index fingerprints, and recommended recovery actions.
Raw process paths/protocol frames do not escape this provider boundary.

The first provider reuses `sfm-propagate-changes` Java analysis through the
D-19 worker transport. The CLI-side engine adds definition-at-source-location
as a typed API and public/manual request form, composes explicitly supplied
resolver-authorized roots with the immutable dependency index, and may overlay
the supplied current document snapshot without writing it to the source tree.
One supervised worker may cache immutable dependency/index structures and
source facts keyed by resolver/selection/content generations; it must invalidate precisely, bound
memory/workers, cancel and reap children, and never turn stale/incomplete index
state into a false no-match.

`sfm:symbol/definition/open` captures the focused editor context once. One
unambiguous result opens/focuses the target Text Editor v3 document at the
returned range according to safe preview/panel placement. Multiple results
open the shared constrained command palette with labelled source/root/symbol
candidates. No symbol, unavailable provider, stale request, incomplete index,
or analysis failure remains a visible actionable diagnostic. F12 and the
Alt+Enter context provider invoke this same action path; neither implements a
second token resolver.

## Rust-owned syntax-highlighting contract

`SFMSyntaxHighlightProvider` is presentation-only and independent from symbol
resolution. It accepts an immutable document origin/generation, explicit
language id, exact UTF-8 source text, source SHA-256, and bounds. Its future is
cancellable. It returns a versioned immutable result with the same identity,
an Arborium/parser fingerprint, elapsed/cache evidence, diagnostics, and
sorted non-overlapping UTF-8 spans. Each span carries both Arborium's stable
flat theme tag and canonical lower-case Minecraft ChatFormatting names; Java
does not parse HTML or ANSI and Rust does not import Minecraft classes.

The first Rust implementation is a lightweight, supervised
`sfm-propagate-changes syntax serve` process with a manually invokable
`syntax highlight` parity command. It uses the already pinned
`arborium-java::HIGHLIGHTS_QUERY`, the existing patched tree-sitter parser,
and Arborium's flat-token normalization. `arborium-highlight` is added without
its `tree-sitter` feature to avoid a second native `links = "tree-sitter"`
provider. The compiled Java query is process-lifetime shared state; bounded
parse contexts and immutable `(language, source hash)` results are reusable.
Protocol framing, executable discovery, cancellation, timeout, crash/restart,
and child cleanup follow the proven symbol-worker patterns but do not require
symbol workspace/dependency-index startup.

The Java client derives a language id only from typed document metadata (Java
initially means a concrete `.java` path), never from title prose. It submits
off the render thread, supersedes by editor-origin generation, validates hash,
span ordering/bounds/style names, converts UTF-8 byte boundaries without
splitting Unicode scalars or CRLF, and publishes through the Minecraft
executor only if the document is unchanged. EditorV3 renders immutable styles;
it never calls the provider or reparses during `render`. Missing/unsupported/
failed highlighting preserves readable existing/plain text and does not affect
opening, keyboard input, closing, or C-5 symbol navigation.

The next-language audit is recorded, not implemented: after Java, prioritize
Rust, JSON, Gradle/Groovy, PowerShell, Markdown, TypeScript, then TOML. Plain
text remains plain. Existing SFML lexer and G4 grammar highlighting remain
available until a separately validated replacement exists.

## Execution order and parallel topology

```text
K-1 panel widget host ---------------------> K-2 terminal/properties migration --+
                                                                                 |
K-3 situation registry/storage/defaults --> K-4 panel topology actions ----------+--> K-7 live join
             |                                  ^                                |
             +------------------------------> K-6 binding-management UX ---------+

K-5 inventory/action ownership starts read-only after K-1 contract,
then integrates semantic controls after K-2/K-3.

K-3 + K-6 + shared palette viewport -> K-8a contract/origin/release freeze
  -> K-8b exact walking skeleton -> K-8c sequences/conflicts/related ranking
  -> K-8d lifecycle/accessibility -> K-8e live/release join.
K-8b extracts only the typed palette-row seam that future B-1 will reuse; it
does not wait for or implement B-1's complete asynchronous streaming layer.

Selection/explorer X-1..X-7 first freeze typed content paths, selections,
relations, generic lazy explorer sessions/actions, and direct control.

X-1 -> A-1 enriches content paths into broader addresses/queries.
X-4 -> A-2a adds bounded recursive candidate walking beyond immediate-child pages.
X-4 -> A-2b enriches registry subjects with properties/actions/outlinks.
X-4/X-5 -> A-2c completes generic panel-scene cutover and removes legacy ids.
A-1 + A-2a + A-2b + A-2c -> A-3 richer addressed projection
                                  -> A-4 Action/Registry Explorer -> A-5 live join.

B-1 streamed palette candidates can proceed beside A-1/A-2a/A-2b.
A-1 + A-2a -> B-2 captured context projections -> B-3 Ctrl+Shift+N file search.
A-3 + B-2 -> B-4 generic reveal/Ctrl+Shift+E.
B-1 + B-2 + B-4 -> B-5 Alt+Enter contextual actions -> B-6 live join.

X-1..X-7 -> C-3 addressed file opening.
Native picker/drop is a later adapter to X-5 root actions, not a C-1/C-2 model.
C-3 + B-2 + existing CLI symbol engine -> C-4 definition-at-location provider.
C-3 -> C-4a file-domain icons + C-4b read-only chrome.
C-3 + CLI-AST Phase 0.11 -> C-4c syntax contract/service -> C-4d Java editor integration.
C-3 + C-4 -> C-5 F12/Alt+Enter/panel navigation.
C-4a + C-4b + C-4d + C-5 -> C-6 live source-presentation/navigation join.

CLI-AST Phase 0.12.1 + C-7 fixes location coverage/root mapping/pane-stack placement.
CLI-AST Phase 0.12.2/0.12.3 + C-7 -> C-8 persistent reference explorer.
B-5 + C-7 + C-8 -> C-9 hover/click/Alt+F7/right-click gesture parity.
C-3/C-4d -> C-10 measured large-document optimization.
C-7 + C-8 + C-9 + C-10 -> C-11 live corrected source-navigation join.

Selection/explorer X-8b independently repairs extension icons, deep projection
completion, path labels, focus chrome, local fuzzy filtering, and scroll latency.
Window-manager Track 1b independently adds pointer divider resize/cursors and
joins pane terminology through selection/explorer X-10.

In-game control CLI I-1/I-2/I-3 (scaffold + instance discovery/selection)
is complete. X-1..X-5 + control I-3 -> control I-4/X-6 direct typed explorer
commands -> X-7 one-game proof -> control I-5 multi-game hardening.

X-1/X-4 provide D-13's explicit no-context explorer/resolver roots;
the development launcher may contribute D-14's exact ordinary source-root path.
B-3 consumes those locations/selections rather than introducing a second root
catalog. C-4's CLI worker/API changes are maintained in the CLI AST refactoring
plan but join through the provider contract here.
```

Safe parallel work after contract review:

- one owner may implement the pure situation/storage model while another owns
  the panel child-widget host;
- after K-8a freezes ids/context/capture semantics, one owner may implement the
  pure runtime-matcher parity and related-result ranker while another owns the
  scoped capture lease; palette controls/typed rows and the final lifecycle/
  puppet join remain coordinator-owned;
- pure layout resize/duplicate recipe tests can proceed independently after
  D-1, without touching the widget host or binding UI;
- address parse/print fixtures can proceed independently after D-5;
- the pure candidate accumulator/cancellation model in B-1 can proceed while
  A-1/A-2a own address/query/path-device types, provided one coordinator freezes
  the candidate/address seam before integration;
- editor 2D snapshot fixtures and explorer-selection projection fixtures in
  B-2 are disjoint until they join in the context collector;
- bounded path enumeration and fuzzy ranking can be developed against an
  in-memory device while the command action/default binding is owned by the
  integration coordinator;
- A-2c may own generic explorer scene cutover, legacy developer-action removal,
  and its focused grammar tests while A-1/B-1 remain in disjoint address/palette
  files; puppet definitions and central registration remain coordinator-owned;
- after X-1/X-5 freeze roots and the semantic add action, one native-dialog
  owner may prepare the later TinyFD/fake input adapter without inventing
  storage or changing the central explorer transaction executor;
- one editor owner may implement C-3 file document/address/open-range support
  after the address seam freezes, without touching CLI Java analysis;
- one CLI owner may implement C-4 definition-at-location requests, worker
  protocol, and scenario fixtures in `platform/cli/sfm-propagate-changes` while
  a Minecraft owner builds the provider/result adapter against captured
  protocol fixtures; only the integration owner wires process discovery and
  live lifecycle;
- the completed `platform/cli/sfm` instance-discovery foundation (I-1 through
  I-3 in its linked plan) is disjoint until direct typed explorer requests join
  at I-4/X-6; protocol generation and central Java lifecycle remain
  coordinator-owned;
- C-5 palette/action/panel integration and C-6 puppets remain coordinator-owned
  because they touch central action registration, defaults, UI placement, and
  both sides' versioned schemas;
- C-4a owns only explorer presentation contributor/default tests, while C-4b
  owns only EditorV3 bottom-chrome geometry/render tests; they may proceed in
  parallel because their Java write sets do not overlap;
- CLI-AST Phase 0.11 owns Rust syntax request/result, Arborium execution,
  direct/worker protocol, and Rust tests while C-5 initially owns Java action/
  palette wiring; one coordinator freezes protocol fixtures before C-4d joins
  the Java syntax client and editor renderer;
- C-4d Java syntax integration and C-5 definition navigation both consume
  editor context/lifecycle, so one coordinator must serialize edits to
  `SFMTextEditorPanel`, `SFMDrawCanvasScreen`, shared context contracts, and
  installed-worker discovery even if their pure tests proceed independently;
- after Phase 0.12 schemas freeze, JDK/location-definition analysis, usage
  collection, worker protocol, explorer X-8b presentation/interaction, and
  window-manager Track 1b pure divider geometry can proceed in disjoint lanes;
- C-7/C-8 Java provider/navigation integration, C-9 EditorV3 gestures, and C-10
  EditorV3 instrumentation/optimization overlap in screen/context files and
  must be integrated serially even when their Rust/pure-model tests run in
  parallel;
- X-8b owns explorer panel/action/presentation files and must coordinate any
  shared command-palette continuation edit with B-1/B-5; Track 1b owns layout/
  multiplexer cursor/drag files and must coordinate central input routing with
  C-9;
- one integration owner must serialize changes to `SFMScreenMultiplexer`,
  `SFMKeyBindingService`, action registration, canonical plans, changelog, and
  live puppet definitions.

## Phase K — Keyboard, widgets, actions, and panel operations

### [x] K-1 Introduce the panel child-widget and focus host

**Work:** Add a reusable child host for `SFMScreenPanel` that adapts real or
behaviorally equivalent Minecraft widgets into panel-local coordinates while
preserving rendering, focus order, narration, active/visible state, click,
drag, release, wheel, key, char, Tab/Shift+Tab, and activation. Add the minimum
`@MCVersionDependentBehaviour` seam for focus APIs that differ across supported
Minecraft versions. Integrate it into the multiplexer without changing panel
topology or PTY behavior.

**Validation:** Add `SFMPanelWidgetHostTests` for traversal order, reverse
traversal, hidden/disabled children, click-to-focus, Enter/Space activation,
coordinate transforms at panel scale, child removal/resizing, narration, and
focus restoration. Then run:

```pwsh
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMPanelWidgetHostTests --wait-for-build-lock
sfm-propagate-changes.exe run compile --branch 1.19.2 --wait-for-build-lock
```

**Completion criteria:** A panel can host multiple ordinary keyboard-focusable
controls through one multiplexer path, and no terminal-specific code is needed
to traverse a generic button/select control.

**Completion evidence — 2026-08-05:** Added `SFMPanelWidget`,
`SFMPanelWidgetHost`, and the Vanilla-button action adapter behind
`SFMScreenPanel`, with one multiplexer path for transformed pointer input,
panel-scale drag deltas, Tab/Shift+Tab, key/char input, active-panel focus,
lifecycle, and full child narration. `SFMPanelWidgetHostTests` passed via the
canonical SFM CLI, including forward/reverse traversal, hidden/disabled
children, click/activation, scaling, restoration, inactive focus, and wheel
fallback. Canonical 1.19.2 compilation passed.

### [x] K-2 Migrate terminal and terminal-properties controls to action-backed widgets

**Work:** Convert Start/Retry, Presentation, renderer/transport choices, and
every terminal-properties plus/minus/auto control into K-1 children. Assign
stable element ids, `sfm:default` situations, narration, and canonical existing
server/presentation/tuning actions. Make the connected terminal viewport a
focusable `sfm:terminal` child. Preserve stale-button invalidation, selector
state/error display, terminal click focus, triple-Tab transfer, triple-Escape,
selection, clipboard, and per-panel target capture.

**Validation:** Add focused widget/action parity tests and extend the Rust
terminal presentation puppet to operate disconnected Start/Retry,
Presentation, and properties exclusively by Tab/Shift+Tab/arrow/Enter/Space,
then by pointer, asserting the same action result and focus owner.

```pwsh
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMTerminalPanelInteractionTests --wait-for-build-lock
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMTerminalPropertiesPanelTests --wait-for-build-lock
sfm-propagate-changes.exe puppet run title_screen_rust_terminal_presentation --branch 1.19.2 --variant 1280x720@auto --wait-for-build-lock
```

**Completion criteria:** The entire terminal control surface is discoverable and
operable without a mouse, uses visible/narrated Minecraft-like focus, and has no
second direct mutation path outside registered actions.

**Completion evidence — 2026-08-05:** Start/Retry, Presentation and every
renderer/transport choice, the terminal viewport, and all thirteen
terminal-properties plus/minus/auto controls are stable panel children with
usage-situation metadata, narration, and canonical action drafts. Registered
actions retain the originating panel after focus changes. Focused
`SFMTerminalPanelInteractionTests`, `SFMTerminalPropertiesPanelTests`,
`SFMTerminalPresentationActionTests`, and `SFMTerminalPropertiesActionTests`
all passed. The canonical `title_screen_rust_terminal_presentation` puppet
passed at `1280x720@auto` in 348.5 seconds and exercised disconnected Space
activation, the live Rust connection, former-button routing through the
multiplexer, keyboard and pointer properties controls, presentation changes,
selection/clipboard, and retained PTY focus gestures. Browsable evidence is in
`platform/minecraft/build/sfm-toolchain/artifacts/game-test-preview/runs/title_screen_rus-20260805-224150-156/`.
The complete canonical 1.19.2 test suite also passed; two Windows symlink
fixtures were explicitly aborted by their existing privilege assumptions.

**Follow-up correction — 2026-08-06:** The Presentation selector and option
rows now retain Vanilla's 20-pixel minimum button height. `SFMPanelWidgetHost`
publishes one authoritative active/focused-child state and clears child focus
on empty-area clicks; the terminal treats its selector and visible options as
one focus scope, dismissing the menu when a click, focus traversal, or panel
deactivation leaves that scope. Focused host/terminal tests prove click-away
dismissal and that Start/Retry, the selector, and hidden options can never
remain focused simultaneously. The canonical full 1.19.2 Java suite passed;
only the two existing Windows symbolic-link assumptions aborted.

**State-model correction — 2026-08-06:** A remote terminal panel is now an
explicit sum of local REPL, disconnected Rust landing, or connected Rust
terminal scenes. The landing child tree contains exactly Start/Retry; the
connected child tree contains exactly the viewport, Presentation selector,
and current renderer/transport option rows. Scene transitions replace the
host's children instead of leaving invisible controls with stale geometry or
focus. The landing headline is stable, automatic retry no longer selects a
one-frame `isConnecting()` message, and the three latest meaningful
connection/start/failure events remain visible. A lock-consistent
`SFMTerminalConnectionSnapshot` is captured once per tick/resize/render; the
connected child tree is not installed until that snapshot reports an accepted
presentation frame, rather than merely a provisional Vox session id. Focused
interaction tests prove exact disconnected -> provisional transport -> failed
discovery -> presentation-ready membership, focus transfer, retained timeout
history, and rejection of stale Start activation after async connection.
Connected transport/presentation failure is a distinct landing product state:
it shows an accurate unavailable headline and an enabled `Retry terminal
connection` control that resets the existing remote transport without
launching another server; an immediate repeated activation observes the
in-flight connection request and is idempotent. The button uses Vanilla's
exact 20-pixel atlas-row height; the previous 22-pixel height sampled the next
texture row and produced
the focus-dependent detached sliver reported in manual testing. Focused tests
pass 35/35, the canonical `Tests` selector passes 645/647 with no failures and
the two expected Windows symlink assumptions aborted, and live
puppet run `title_screen_rus-20260806-190626-836` captures the clean landing
control before proving connection, former-button routing, input, reconnect,
and constrained close.

### [x] K-3 Register keyboard-usage situations and migrate binding storage/defaults

**Work:** Close D-2. Add the contributor registry, active-context snapshot,
context-aware engine, schema-1 to schema-2 migration, explicit global scope,
built-in default definitions, overrides/tombstones, situation-aware conflict
detection, and display metadata. Preserve incomplete command-draft palette
opening. Seed the existing panel scale defaults, panel close, and F3 diagnostic
action only in the approved SFM-owned context. Register
`sfm:panel/diagnostics/open` and remove the multiplexer-only F3 mutation path.

**Validation:** Add situation registry, ancestry, precedence, overlap conflict,
focus-change reset, terminal pass-through, storage migration, corrupt-file,
default/override/tombstone, canonical physical-key display/matching, F3 discovery, close action, and
incomplete-draft tests.

```pwsh
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMKeyboardUsageSituationTests --wait-for-build-lock
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMKeyBindingStorageTests --wait-for-build-lock
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMKeyBindingEngineTests --wait-for-build-lock
```

**Completion criteria:** Users can inspect/select stable usage situations;
defaults do not leak outside SFM-owned focus; existing global bindings migrate;
F3 and panel close appear as ordinary action bindings; and unmatched terminal
input still reaches Rust exactly once.

**Completion evidence — 2026-08-06:** The contributor registry now exposes the
stable ancestry `sfm:terminal -> sfm:default -> sfm:workspace -> sfm:global`,
and one immutable host/panel/element/focus-revision snapshot drives matching at
Forge's cancellable pre-screen boundary. Multi-parent situations resolve by
breadth/depth so all direct parents have equal precedence. The matcher implements
D-2, including self-overlapping chords without restarting a successfully
continued chord as an unrelated one-stroke action; unavailable and unmatched
terminal relationships remain unconsumed. The input bridge tracks consumed and
held keys through canceled releases, suppresses associated AltGr/surrogate
character events, and intercepts Vanilla's pre-Forge screenshot/fullscreen keys
through a narrow 1.19.2 mixin so contextual bindings can reserve them.

Schema 2 persists standalone user relationships, field-level built-in overrides,
built-in tombstones, and observed-default fingerprints. Schema 1 migrates as
explicit-global state through a backup plus atomic replacement; corrupt, future,
or structurally incomplete files remain untouched and visibly read-only, and a
failed save switches the session to visible read-only persistence rather than
claiming success. Immutable contextual defaults cover panel close, F3 diagnostics,
and one canonical main-row relationship per scale action; the management UI exposes situation, origin,
conflict, restore, and a visible storage-recovery warning. Focused
`SFMKeyboardUsageSituationTests`, `SFMKeyBindingStorageTests`, and
`SFMKeyBindingEngineTests` passed. The canonical
`title_screen_dynamic_key_bindings`, `title_screen_workspace`, and
`title_screen_rust_terminal_presentation` puppets passed, including real
pre-screen complete/incomplete dispatch, Ctrl++/Ctrl+0, F3 choices,
Ctrl+Shift+W, and terminal presentation/input pass-through. Evidence is under
`platform/minecraft/build/sfm-toolchain/artifacts/game-test-preview/runs/title_screen_dyn-20260806-003236-037/`,
`title_screen_wor-20260806-003416-703/`, and
`title_screen_rus-20260806-004017-716/`. The complete canonical 1.19.2 test
suite passed, with only the two Windows symlink fixtures explicitly aborted by
their existing privilege assumptions.

**Default correction — 2026-08-06:** Manual testing showed that the original
increase default was both duplicated (main-row and keypad rows rendered the
same) and mismatched the desired physical gesture by requiring Shift. The
immutable catalog now keeps only Ctrl+Equal, Ctrl+Minus, and Ctrl+0 for panel
scale. Display fallback uses separated physical tokens (`Ctrl =`) and the
palette/details surfaces draw the same tokens as pink keycaps; K-6 owns their
focus/removal behavior during capture. Focused storage and engine tests
prove one increase relationship, no shipped scale keypad duplicates, exact
Equal+Control consumption, and fingerprint migration through the existing
built-in lifecycle.

### [x] K-4 Add resize and independent duplicate panel actions

**Work:** Close D-1. Extend the pure layout/intents with bounded resize and
duplicate operations. Resize adjusts the nearest split track in the requested
axis by a named deterministic step, respects minimum panel pixels, normalizes
shares, and reports unavailable at an edge/no-neighbor boundary. Duplicate
uses a typed re-open recipe to create a new independent panel identity in the
requested direction; never insert the same mutable panel object twice. Preserve
existing move behavior and hidden stack entries. Register the hierarchical
actions and K-3 defaults from the pinned Microsoft Terminal table.

**Validation:** Test nested horizontal/vertical splits, nearest neighbor,
minimums, repeated resize, inverse operations within rounding tolerance,
edge no-op, stacks, move coexistence, independent lifecycle/focus/scale, every
supported duplicate recipe, unsupported panels, and terminal session identity.

```pwsh
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMWorkspaceLayoutTests --wait-for-build-lock
sfm-propagate-changes.exe test run --branch 1.19.2 --filter PanelActionTests --wait-for-build-lock
sfm-propagate-changes.exe puppet run title_screen_workspace --branch 1.19.2 --wait-for-build-lock
```

**Completion criteria:** Palette and contextual shortcuts can resize in four
directions and duplicate in supported directions without aliasing state;
existing move actions retain their distinct transfer semantics.

**Completion notes (2026-08-06):** Added
`sfm:panel/resize/left|right|above|below` and
`sfm:panel/duplicate/left|right|above|below`. Resize changes the nearest
matching-axis split by a named 5% step, searches outward when an inner split
has no neighbor, stops at a 48-pixel recursive minimum, normalizes shares, and
preserves stacks/focus. The exact pinned defaults are scoped to `sfm:default`:
Alt+Shift+arrows resize, physical Alt+Shift+Equal displays as the separated
tokens `Alt Shift =` and duplicates right, and Alt+Shift+Minus duplicates below;
left/above duplication intentionally remain palette/user-bindable without
built-in defaults. KBIND-5 later corrects panel-scale increase to `Ctrl =`.

Duplication is host-catalogued by panel identity but recipe data is immutable
and typed rather than an opaque supplier. Test, size-display, terminal,
terminal-properties, text-editor/grammar, review-explorer, and default fixture
or normalized-root file-explorer panels reconstruct fresh mutable state. Rust
terminal recipes preserve the resolved endpoint while opening a distinct
session; terminal-properties duplication is available only while its owner
terminal remains live. Explorer directory drops replace the source recipe so
future duplicates cannot reopen a stale root. Previous-screen wrappers,
callback-bound read-only previews, custom-presentation explorers, color/item
pickers, theme drafts, timeline panels, and any other uncatalogued panel report
duplication unavailable. Responsive panel groups also report unavailable until
their dynamic-entry contract is designed. Existing move actions still transfer
one identity and do not use this reconstruction path.

Focused `SFMWorkspaceLayoutTests`, `PanelActionTests`,
`OpenPanelActionTests`, `SFMFileExplorerWorkspaceTests`, and
`SFMKeyBindingStorageTests` passed. The canonical full 1.19.2 Java suite
passed, with only the two existing Windows symbolic-link fixtures aborted by
their privilege assumptions. The live `title_screen_workspace` puppet passed
at `1280x720@auto` (`failed=0`), exercising all four resize defaults, the two
pinned duplicate defaults, the unbound left/above duplicate actions, one-pixel
inverse rounding tolerance, distinct panel instances, and the final six-panel
layout. Evidence is under
`platform/minecraft/build/sfm-toolchain/artifacts/game-test-preview/runs/title_screen_wor-20260806-170614-790/`.

### [x] K-5 Actionize SFM-owned controls and enforce keyboard reachability

**Work:** Close D-3. Produce a machine-readable inventory of SFM-owned raw key
handlers, control callbacks, and hand-rendered hit regions. Convert semantic
workspace shortcuts (Ctrl+Tab, Shift variant, direct panel focus, maximize,
F3/Escape entry surfaces where applicable) and one Manager Edit slice to
registered actions/action elements. Keep intrinsic editor/terminal input in the
documented parameterized category. Add stable element addresses and eliminate
public coordinate-click action drafts. Add runtime and static audits with
bounded explicit exemptions.

**Validation:** Fixture tests prove pointer/keyboard/palette/default-binding
parity for each migrated semantic control, captured-context safety for Manager
position/owner panel, no coordinate action registrations, and audit failures for
missing focus, narration, situation, or semantic action. The inventory is saved
as a deterministic test artifact.

```pwsh
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMActionElementTests --wait-for-build-lock
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMKeyboardNavigationAuditTests --wait-for-build-lock
```

**Completion criteria:** Every inventoried SFM mutation control has a declared
semantic/input disposition, visible action elements are keyboard reachable,
and the action explorer will not need to explain raw pixel-coordinate commands.

**Incremental progress — 2026-08-07:** Registered the first workspace semantic
navigation family: `sfm:panel/focus/next`, `sfm:panel/focus/previous`,
`sfm:panel/focus/index <1..9>`, and `sfm:panel/maximize/toggle`. The
multiplexer now routes Ctrl+Tab, Ctrl+Shift+Tab, Ctrl+1..9, and Ctrl+M through
the action executor, while the defaults expose those relationships in the
workspace situation. The pure action-grammar test passes `2 found, 2 passed`,
the affected keybinding slice passes `38 found, 38 passed`, and the current
main/test source sets compile. An explicit compile refresh separately hit a
cached Forge renaming-tool replacement failure, but it did not block the
current focused test run. The subsequent completion note below records the
bounded inventory, action-element audit, and Manager Edit proof that closed
K-5; the live witness is intentionally deferred to K-7.

**Completed — 2026-08-07 (action-element proof and bounded inventory):** Added the
`SFMActionElement` contract and deterministic line-oriented inventory/audit.
`SFMPanelWidgetHost.actionElements()` exposes live panel children to the audit
without creating a second mutable registry, and `SFMPanelActionButton` now
rejects missing situation, narration, keyboard reachability, semantic action,
or public coordinate-click metadata at construction time. Migrated the
Manager Edit button and its legacy key path through registered
`sfm:manager/edit`; its requirement captures the exact active `ManagerScreen`,
which is the bounded proof that dynamic manager position/owner context stays
attached to the semantic action. Focused pure tests cover deterministic
inventory, audit failures, and wrong-host unavailability. The explicit
`SFMKeyboardNavigationInventory` records every currently reviewed raw handler
with a semantic-action, parameterized-input, vanilla-widget, or bounded legacy
exemption disposition; `SFMKeyboardNavigationAuditTests` emits deterministic
machine-readable lines. `SFMActionElementAudit` rejects public coordinate-click
drafts and missing focus/situation/narration/action metadata. K-5 is complete;
the live cross-screen witness remains owned by K-7.

### [x] K-6 Upgrade binding management with sorting and composable capture

**Work:** Close D-4's K-phase naming. Add focusable Name and Binding Count
headers, stable ascending/descending sorting, enabled/total counts, situation
scope display/filtering, and filter/viewport preservation. Replace the ad hoc
recording boolean with a focusable capture widget. Each captured physical key
and modifier is a pink, whitespace-separated button/keycap removable by click
or keyboard activation; text-only fallbacks preserve the same `Ctrl =` token
order without `+` as a delimiter. Triple Escape within a
documented window cancels capture and removes its pending Escape presses;
single/double Escape may remain recordable. Save, Cancel, add/edit/remove,
enable/disable, conflicts, and scrolling all participate in one focus tree.

**Validation:** Test sort direction/ties/count changes, filtering, scrolling,
capture focus, modifiers, main/keypad keys, multiple strokes, chip deletion,
triple-Escape timing, save/cancel, conflict scope, dispatch suspension and
restoration, resize, and narration.

```pwsh
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMKeyBindingScreenTests --wait-for-build-lock
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMKeyBindingCaptureWidgetTests --wait-for-build-lock
```

**Completion criteria:** The complete binding collection is sortable,
searchable, scoped, and editable without a mouse; users can understand and
modify every recorded chord element without trapping keyboard focus.

**Completed — 2026-08-07:** `SFMKeyBindingListModel` supplies stable name and
binding-count ordering, situation filtering, enabled/total row counts, and
selection/viewport preservation. `SFMKeySequenceCapture` and
`SFMKeySequenceCaptureWidget` replace the old recording-only path with a
focusable capture surface, separated physical tokens such as `Ctrl =`, pink
removable keycaps, keyboard and mouse token removal, explicit Save/Cancel
targets, dispatch suspension restoration, and bounded triple-Escape cancel
behavior. The focused tests pass for list sorting/filtering, timeout and
triple-Escape behavior, modifier/key removal, and post-capture keyboard-token
focus semantics. K-7 supplies the live keyboard-management witness.

### [x] K-7 Prove the contextual input vertical slice live

**Work:** Extend `title_screen_dynamic_key_bindings`, `title_screen_workspace`,
and the Rust terminal presentation puppet with machine assertions and text
artifacts. Cover keyboard-only disconnected terminal controls, properties,
F3 discoverability, close, scale, resize, duplicate, situation display,
incomplete command completion, sorting, capture chips, triple-Escape cancel,
Manager semantic action ownership, and terminal non-leak. Every puppet command
palette submission leaves the entered command visible for ten 20-Hz client
ticks (500 ms) before submitting through the real Enter path, so a human can
observe the action and the evidence capture is not an instantaneous state jump.
Update `changelog.sfml` and both coordinating plans.

**Validation:** Run focused tests, canonical compile/full tests, and the named
puppets only through the SFM CLI. Record manifests and inspect representative
screenshots plus machine-readable focus/action inventories.

```pwsh
sfm-propagate-changes.exe run compile --branch 1.19.2 --wait-for-build-lock
sfm-propagate-changes.exe test run --branch 1.19.2 --no-capture --wait-for-build-lock
sfm-propagate-changes.exe puppet run title_screen_dynamic_key_bindings --branch 1.19.2 --wait-for-build-lock
sfm-propagate-changes.exe puppet run title_screen_workspace --branch 1.19.2 --wait-for-build-lock
sfm-propagate-changes.exe puppet run title_screen_rust_terminal_presentation --branch 1.19.2 --variant 1280x720@auto --wait-for-build-lock
```

**Completion criteria:** Every K guidance id has pure/integration evidence and a
live user-visible witness; no result depends solely on OCR or screenshot
interpretation; generated-resource edits unrelated to the slice remain
untouched.

**Completed — 2026-08-08:** The final toolchain evidence is complete. The
canonical compile passed, and the full suite reports `669 found, 667 passed,
0 failed, 0 skipped, 2 aborted`; both aborts are the established Windows
symlink-privilege assumptions. The live manifests are
`title_screen_dyn-20260808-103044-061` (12 captures, including the pink
Ctrl+H capture and three-Escape cancellation),
`title_screen_wor-20260808-103446-639` (8 workspace focus/scale/resize/move/
duplicate/diagnostic captures), and
`title_screen_rus-20260808-104057-247` (33 renderer/transport/focus captures
plus terminal text, selection, paste, properties, and push-telemetry artifacts).
The presentation selector is now itself backed by the semantic
`sfm:terminal/presentation/toggle` action, and the viewport tests use the
post-sort-header geometry. The shared puppet palette actions set the real
input, hold it for the documented 500 ms observation window, and then submit
through the real command path. The two unrelated generated-resource edits
remain unmodified by this slice.

### [ ] K-8 Discover actions from a captured hotkey in the command palette

K-8 is split so a smaller implementation agent does not conflate physical
capture, runtime dispatch semantics, palette row presentation, and release
bookkeeping. It reuses the capture value representation, but not the binding-
editor widget's Enter/arrow/Delete behavior.

#### [ ] K-8a Freeze lookup identity, origin context, capture timing, and release boundary

**Work:**

- Freeze stable identities before public/history use:
  `sfm:palette/hotkey_lookup/open`, a focus/audit element id, lookup session and
  generation ids, serialized origin-context fingerprint, binding-repository
  revision, typed relation ids, stable row key
  `(binding-revision, binding-id, relation)`, action-details destination, puppet
  phase/test-counter ids, and artifact schema.
- Capture the origin **before** pushing or focusing the palette. The immutable
  lookup context contains the action context, ordered situation ids/depths,
  originating pane/panel-entry/component/action-element identities, binding
  revision, and a stale-origin policy. The palette's own global situation must
  not replace the terminal/editor/workspace context being inspected.
- Freeze result meanings. `effective-exact` means the runtime matcher would
  dispatch that binding. `exact-shadowed` and `exact-conflict` explain exact
  physical matches that runtime precedence suppresses. A
  `related-modifier-subset` row has the same stroke keys and fewer modifiers
  but would **not** fire for the captured extra-modifier input. Result prose
  must preserve these distinctions.
- Freeze capture behavior independently from binding editing. After activating
  the lookup control, wait until the activation key and pre-held modifiers are
  released. Every subsequent physical non-modifier key—including Tab, Enter,
  arrows, Backspace/Delete, and supported mouse buttons—is capturable. Key
  repeat is suppressed. A completed stroke starts the existing bounded
  inter-stroke timeout; timeout submits the sequence. A pointer-activated Done
  control may submit early. Triple Escape within its documented window cancels;
  one/two Escape strokes remain capturable if the window expires. Focus/screen
  loss cancels and releases all state. Modifier-only input does not form a
  complete stroke.
- Lookup is available only in an ordinary palette. Hide/disable it in ephemeral
  constrained `sfm choose` sessions so a curated choice surface cannot leak
  unrelated global actions.
- Activation has one outcome: leave lookup and open the existing action-details
  surface for that result. It never fills a draft, executes an action, or writes
  command history. Draft preparation, if later desired, needs a separate
  explicit action.
- Close the release cutoff without reopening completed P-5.2. If K-8 lands
  before release P-5.5, extend P-5.5/changelog/live evidence; otherwise record
  it explicitly as post-release work.

**Validation:** contract/parser/identity golden tests and a release-plan
traceability audit; no production behavior is claimed from this task alone.

```pwsh
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMKeyHotkeyLookupContractTests --wait-for-build-lock
```

**Completion criteria:** Every persisted/public id, origin field, result
relation, input completion/cancellation rule, activation behavior, constrained-
palette rule, and release owner is explicit enough that K-8b through K-8e do
not invent semantics.

#### [ ] K-8b Implement an exact effective-lookup walking skeleton

**Work:**

- Add the action-backed, focusable/narrated `Find actions by hotkey` control and
  explicit Tab/Shift+Tab traversal. Ordinary Tab suggestion acceptance remains
  deliberate; focus traversal cannot be swallowed by the query widget.
- Acquire a scoped dispatch-suspension lease owned by the lookup session.
  Replace/wrap process-global boolean suspension so one owner cannot unsuspend
  another. Closing the lease resets matcher, input-handler pressed/consumed
  state, held modifiers, and character-event suppression on every exit path.
- Capture one physical single-stroke sequence, run the same pure runtime
  deepest-situation/partial-sequence/conflict precedence against the frozen
  snapshot, and publish one typed `effective-exact` row.
- Extract the minimum typed palette-row/stable-selection seam compatible with
  future B-1 streamed candidates. Do not encode relation metadata in Brigadier
  display strings or block K-8 on the complete asynchronous B-1 phase.
- Activating the row opens action details; an action/test counter and history
  assertions prove no invocation or command-history write occurred.

**Validation:** focused action-element/focus, scoped-lease, frozen-origin,
runtime-matcher parity, typed-row, details-activation, zero-execution, and
zero-history tests.

```pwsh
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMKeyHotkeyLookupExactTests --wait-for-build-lock
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMCommandPaletteFocusTests --wait-for-build-lock
```

**Completion criteria:** From a terminal/editor/workspace origin, a keyboard or
pointer user can enter lookup, capture one exact hotkey, see the runtime-
effective binding, and inspect its details with no input leak or mutation.

#### [ ] K-8c Add sequences, conflicts, shadowing, and related modifier subsets

**Work:**

- Extend capture through the frozen inter-stroke timeout and maximum sequence
  bounds. Preserve physical keys/modifiers per stroke; do not compare rendered
  glyph labels.
- Simulate runtime partial-sequence reservation, deepest situation precedence,
  equal-depth conflict suppression, disabled/tombstoned mappings, and duplicate
  action ids. Show one stable row per binding with its effective, shadowed, or
  conflict relation.
- After all exact-physical rows, add related rows only when every candidate
  stroke has the same key and a modifier set that is a subset of the captured
  stroke. Distance is the total removed-modifier count, followed by situation
  depth, stable binding id, action id, and relation tie-breaks. Do not substitute
  keys, reorder strokes, or compare unrelated sequence lengths.
- The required fixture has direct `Ctrl+Alt+L` results first and a clearly
  labelled `Ctrl+L` related result below them; prose states that the latter
  would not dispatch for the captured input.

**Validation:** exact/partial/conflict/shadow/disabled/duplicate/multi-stroke,
per-stroke subset, no-substitution, deterministic-order, stable-selection, and
runtime-matcher equivalence tests.

```pwsh
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMKeyHotkeyLookupRankingTests --wait-for-build-lock
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMKeybindingMatchResolverTests --wait-for-build-lock
```

**Completion criteria:** Results accurately explain runtime behavior and nearby
modifier-subset discoveries without calling related bindings effective.

#### [ ] K-8d Harden lifecycle, capturable controls, focus, and accessibility

**Work:**

- Cover Tab, Enter, arrows, Backspace/Delete, Escape, modifier-only events,
  repeats, character callbacks, activation-key release, mouse buttons, timeout,
  empty/no-match, maximum sequence, focus loss, origin invalidation, binding-
  revision changes, screen replacement, exceptions, and close.
- Define stale origin/revision presentation: keep the captured diagnostic
  artifact, reject dispatch claims, and require a new lookup rather than
  silently re-evaluating against changed state.
- Reuse the palette viewport/scrollbar/mouse selection and add full narration
  of captured sequence, relation, action, situation, binding, reason/distance,
  stale state, and inspection-only activation.
- Prove every exit releases the scoped lease and restores prior palette focus
  or safely closes when the origin no longer exists.

**Validation:** pure capture/lifecycle/focus-loss/cleanup and accessibility
tests, including fault injection after each acquired resource.

```pwsh
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMKeyHotkeyLookupLifecycleTests --wait-for-build-lock
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMKeySequenceCaptureTests --wait-for-build-lock
```

**Completion criteria:** No physical key is accidentally unavailable merely
because the binding editor used it for editing, and no cancellation/error path
leaves dispatch suspended, a modifier held, a character leaked, or stale
results described as current.

#### [ ] K-8e Prove the lookup live and join the release ledger

**Work:**

- Add machine-readable origin-context, binding-revision, capture events,
  matcher decisions, ranked rows, selected details row, cleanup state,
  history-before/after, and test-action counter artifacts.
- Extend the dynamic-keybinding puppet with keyboard and pointer entry, visible
  capture, the required exact/conflict/related ordering, one multi-stroke
  example, no-match/cancel, details activation, and the existing 500 ms visible
  observation convention.
- Run canonical Java validation and the release decision from K-8a. Record the
  current installed-tool state according to the operational-readiness guide.

**Validation:**

```pwsh
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMKeyHotkeyLookupTests --wait-for-build-lock
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMKeySequenceCaptureTests --wait-for-build-lock
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMKeybindingMatchResolverTests --wait-for-build-lock
sfm-propagate-changes.exe run compile --branch 1.19.2 --wait-for-build-lock
sfm-propagate-changes.exe puppet run title_screen_dynamic_key_bindings --branch 1.19.2 --wait-for-build-lock
```

**Completion criteria:** A user can focus the command-palette lookup control,
capture physical single/multi-stroke input without typing or invocation, and
inspect stable origin-eligible results whose effective, suppressed, conflict,
and related meanings agree with runtime dispatch. Every cleanup path is clean,
artifacts prove inspection-only behavior, and release inclusion/deferment is
explicit.

## Phase A — Typed addresses and explorer projections

### [ ] A-1 Freeze typed addresses, address queries, resolver context, and canonical spelling

**Work:** Begin after X-1 freezes `SFMPath` and `PathExpression`. Close D-5 and
the address/query portion of D-11 by enriching—not duplicating—those content
types. Define `SFMAddress` for subjects that are not content paths (live UI
elements, registries, actions, and other contributed objects) and
`SFMAddressQuery` for wildcard/filter lookup. A concrete content address embeds
or references the canonical X-1 path; it does not invent a second filesystem or
selection grammar. Add typed resolver payloads, parse/print, versioning,
escaping, equality, unavailable/diagnostic resolution, contextual capabilities,
contributor registry, and bounded property/action/outlink/reveal results.
Specify persistent/public versus query versus ephemeral forms. Bootstrap
context from explicit client/screen/pane/panel-entry/component ids without
granting authority that a resolver did not declare.

**Validation:** Fixture round trips cover proposed registry/path examples,
`sfm:item:minecraft:*wood*` as a query rather than a concrete address,
namespaced ids containing nested paths, Unicode, escaping, malformed/unknown
resolver ids, missing context, stale panels/elements, contributor entries, and
rejection of query-only syntax from durable concrete-address fields.

```pwsh
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMAddressTests --wait-for-build-lock
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMAddressQueryTests --wait-for-build-lock
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMAddressResolverRegistryTests --wait-for-build-lock
```

**Completion criteria:** Durable links and explicit query values have distinct,
unambiguous canonical forms; resolvers can describe requirements, outlinks, and
reveal capability without ambient mutation access; no wildcard is persisted as
if it identified one resolved object.

### [ ] A-2a Add bounded path-device resolvers and incremental enumeration

**Work:** Reuse X-4's immediate-child filesystem resolver and authority model.
Add the incremental, bounded recursive candidate-walk surface needed by B-3,
with read-only fixture/VFS and contained instance-run-directory contributions.
Do not create a second path-device registry when the X resolver registry can
expose the capability. Normalize and reject traversal, drive/UNC injection,
unsupported mutation, stale devices, oversized output, symlink escape, and
ambient-root fallback. Keep logical display paths separate from resolver-issued
root authority.

**Validation:** Test device selection, root containment, symlink policy,
unavailable/stale contexts, read bounds, read-only enforcement, deterministic
batched enumeration, cancellation, limits, and explicit partial/error state.

```pwsh
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMPathAddressResolverTests --wait-for-build-lock
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMPathDeviceWalkTests --wait-for-build-lock
```

**Completion criteria:** Path families resolve and enumerate real bounded
objects through explicit device authority, never infer an ambient root, and
fail or truncate visibly and safely.

### [ ] A-2b Add registry-entry resolvers and a Vanilla adapter

**Work:** Enrich X-4's minimal item-registry path/presentation adapter with the
SFM client-action registry, one Vanilla item fixture, explicit registry-entry
addresses, properties, actions, and outlinks. Demonstrate the no-orphan adapter
by addressing Vanilla data without modifying its classes. Contribute only
truthful capabilities and do not create a second explorer implementation.

**Validation:** Test registry/entry existence, action metadata/outlinks,
item-to-block relationship truth, contributor namespaces, unavailable context,
wildcard item queries, and absence of fabricated relationships.

```pwsh
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMRegistryAddressResolverTests --wait-for-build-lock
```

**Completion criteria:** SFM and Vanilla registry objects resolve through
contributed adapters with accurate properties/outlinks/query behavior and no
modification of the adapted classes.

### [x] A-2c Complete the generic explorer panel-scene cutover

**Established progress (2026-08-13):** X-4/X-5 and X-8a now provide the
selection-backed lazy explorer, the registered generic `sfm:explorer` scene,
the canonical location header/document, shared directional panel placement,
and direct external `sfm explorer ...` control. These sources are checkpointed
by `7cfd4b138`; the successful Auto plus GUI-scale-1-through-8 visual matrix is
checkpointed by `dbf6bf344` and rooted at
`platform/minecraft/build/sfm-toolchain/artifacts/game-test-preview/runs/`
`title_screen_ext-20260813-162233-680`.

A-2c is complete in implementation commit `6dc3d2d04`.
`SFMDeveloperActions`, its title-screen support branches, action metadata, and
puppet command paths no longer expose `developer/open_file_explorer`,
`developer/open_instance_file_explorer`, or `developer/open_item_icon_picker`.
The obsolete bespoke large-file-explorer puppet/runtime hook was removed as
well; reusable item-icon-picker presentation fixtures remain test-only and are
not public opening aliases. `SFMExplorerScreenType` now accepts the complete
X-1 path-expression token while retaining the item-registry root as its explicit
omitted-argument default. File activation is owned by C-3; completed X-8a
location editing remains unchanged.

**Work:** Begin after X-4/X-5 provide the generic heterogeneous explorer and
registered actions. Register one `sfm:explorer` `SFMClientScreenType` consumed
by every `sfm:panel/open[/direction]` action, with an optional X-1
path-expression location. `registry://minecraft/item/`, the bounded current
instance directory, an explicitly contributed SFM source-root path, and ordinary
filesystem roots are content inputs—not separate explorer types. Reopen recipes
retain the location expression and immutable creation recipe while each panel
owns independent projection/navigation/expansion/request state. Missing
contributions report unavailable rather than substituting fixture/process
paths. Preserve reusable item-picker destination behavior for X-8; do not make
it the generic explorer's navigation state.

Complete selection/explorer X-8a's header cutover as part of the scene
integration. Replace the static `SFM Explorer` title and textual view toggle
with one full-width, focusable location display. It shows the exact canonical
`ExplorerSession.location()` expression, including internal selection ids;
visual truncation is permitted only when full tooltip, narration, copy, and
editor content remain exact. Mouse click and keyboard activation
must open a versioned explorer-location document in a side panel through the
ordinary typed panel-open seam, selecting the configured preferred text editor
when no editor id is supplied. Do not add a private inline text box or another
panel-placement implementation.

The location document contains exactly the canonical path expression, without
materializing or prettifying selection-backed state. Saving is a registered
exact-explorer mutation carrying the expected location/session revision. Parse,
resolve, and preflight the entire replacement—including filesystem authority—
before one client-thread publication. Syntax, resolver, containment, authority,
or stale-revision failures retain the old location and appear in the editor;
editing this virtual document never writes a referenced host file. Keep
view/sort/group/hoist discoverable through their existing actions, contextual
offers, palette, and keybinding surfaces after removing the prose toggle.

Remove `sfm:developer/open_file_explorer`,
`sfm:developer/open_instance_file_explorer`, and
`sfm:developer/open_item_icon_picker` from `SFMDeveloperActions`. Delete their
exclusive `OpenTitleScreenDevScreenAction` metadata/switch branches and
`SFMTitleScreenDevScreen` cases. Do not register aliases, redirects, hidden
compatibility actions, storage migrations, or replacement developer commands.
Also remove the now-superseded unreleased `sfm:explorer/file`,
`sfm:explorer/item`, and `sfm:explorer/workspace` scene ids if present. Do not
register aliases or redirects for either generation. Migrate every unit,
gametest, puppet, and action fixture to the generic path-expression panel-open
form; keep deterministic sources behind test-only construction.

**Validation:** Test generic command parsing/execution for omitted, file,
registry, and selection locations plus directional variants; path-expression
completion/fuzzy discovery; heterogeneous roots; instance containment;
explicit SFM source-root available/unavailable behavior; fresh reopen state; safe panel
placement; exact canonical ellipsis/tooltip/narration; mouse/keyboard preferred-editor
opening; literal/multi-root-selection-id/expression document round trips; atomic successful
replacement; invalid/unauthorized/stale save retention; projection-action
discoverability; and reusable picker compatibility. Assert the three developer ids
and three superseded explorer subtype ids are absent from the action/screen
registries, compiled Brigadier tree, palette suggestions, defaults, and puppet
strings. Run migrated file/item explorer puppets through the generic scene.

```pwsh
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMExplorerScreenTypeTests --wait-for-build-lock
sfm-propagate-changes.exe test run --branch 1.19.2 --filter OpenPanelActionTests --wait-for-build-lock
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMDeveloperActionRemovalTests --wait-for-build-lock
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMExplorerLocationDocumentTests --wait-for-build-lock
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMExplorerPanelActionEmissionTests --wait-for-build-lock
sfm-propagate-changes.exe puppet run title_screen_item_icon_command_palette --branch 1.19.2 --variant 1280x720@auto --wait-for-build-lock
sfm-propagate-changes.exe puppet run title_screen_explorer_open_sfm_java --branch 1.19.2 --variant declared --wait-for-build-lock
sfm-propagate-changes.exe puppet run title_screen_explorer_location_editor --branch 1.19.2 --variant 1280x720@auto --wait-for-build-lock
```

**Completion criteria:** One generic path-expression panel-open grammar is the
only public command-palette entry point for explorer surfaces; file and item
roots can coexist; every old developer and explorer-subtype id plus exclusive
support code is gone; tests/puppets use the generic grammar; and reusable picker
behavior remains intact. The header is a truthful, keyboard-operable location
control rather than redundant identity/view prose; it opens the preferred editor
through shared panel placement, and valid/stale/invalid edits exhibit atomic,
authority-safe behavior.

**Completion evidence (2026-08-13):** Commit `6dc3d2d04` removes the three
unreleased developer actions, their title-screen cases, four superseded legacy
file-explorer puppets, and the final bespoke large-explorer runtime hook. The
only source occurrences of the deleted ids are negative assertions in
`SFMDeveloperActionRemovalTests`. Generic omitted/file/registry/expression and
directional command tests pass, as do the complete 838-test suite and canonical
compile. The migrated item-registry puppet passed at `1280x720@auto`; its
browsable run is
`platform/minecraft/build/sfm-toolchain/artifacts/game-test-preview/runs/`
`title_screen_ite-20260813-214345-182`. The real-file generic-scene proof is
recorded with C-3 below. No aliases, redirects, propagation, publication, or
release operation was performed.

### [ ] A-3 Generalize explorer nodes and object-properties panels around addresses

**Work:** Enrich the X-4 relation-backed explorer node presentation so a subject
can carry an address, title, kind, properties, actions, and typed outlinks
without pretending every object is a file. Children remain X-3 relation edges,
not nested node ownership. Preserve file/review behavior through adapters. Add
a single-object properties panel and richer registry-root contributions; do
not add another generic explorer scene.

**Validation:** Test lazy/bounded expansion, cycles, unavailable/stale nodes,
focus/selection, keyboard open, preview-slot ownership, typed outlinks, file
adapter parity, and resolver errors that remain visible rather than dropping
nodes.

**Completion criteria:** One panel can inspect an addressed object and one
explorer can navigate heterogeneous addressed collections without coupling the
core to a specific registry.

### [ ] A-4 Project Action Explorer and Registry Explorer

**Work:** Close D-4. Register panel scenes/actions for the registry root and
client-action projection. Show actions with metadata, availability, situations,
default/user binding collections, and live element owners. Show registries and
entry-specific outlinks such as a real item/block relationship. Jumplinks open
addressed properties or explorer nodes through the existing panel open/preview
rules; they do not clobber terminal or unrelated workflow panels.

**Validation:** Test all links in both directions where declared, no fabricated
relationships, binding-count consistency with K-6, live-owner disappearance,
panel placement/preview behavior, keyboard navigation, and palette discovery.

**Completion criteria:** Users can start at known registries, reach actions and
their mappings/owners, and follow supported entry relationships through stable
addresses rather than bespoke screen transitions.

### [ ] A-5 Prove addressable navigation live and record the release boundary

**Work:** Add a live explorer puppet covering registry -> client action ->
binding/situation/owner, Vanilla item -> block where applicable, path-device ->
file properties, unavailable context, and panel-safe jumplinks. Update gameplay
changelog, release-plan cutoff, source references, and propagation notes.

**Validation:** Run canonical compile/full tests and the new live puppet through
the SFM CLI. Capture machine-readable address/outlink trees beside screenshots.

**Completion criteria:** Every active ADDR/EXPL guidance id has typed tests and
live proof; the release plan explicitly says which A items are required for the
candidate release; no propagation or publication occurs without a later goal.

## Phase B — Streaming fuzzy search and contextual action discovery

### [x] B-0 Repair progressive completion boundaries, parameter history, and usage guidance

**User outcome:** The ordinary command palette behaves as a progressive grammar
browser. With a recently executed full panel-open command in history, typing
`open` first offers the reusable action boundary `sfm:panel/open`; Tab accepts
that boundary without prematurely entering its scene argument, a later Tab can
continue to `/right`, and an explicit Space enters the scene slot. Dynamic
selector/value suggestions and named required-argument guidance remain visible
at every real Brigadier frontier.

**Verified starting state — 2026-08-22:**

- `SFMClientActionCommandTree.getPaletteSuggestions()` puts complete history
  commands and bare action ids in one ranked list. For a nonblank action-id
  query they receive the same metadata fuzzy score; the newest history row has
  no recency penalty and lexical tie-breaking makes the complete
  `sfm action invoke ...` string beat `sfm:panel/open`. The existing test proves
  only presence, not first position.
- `SFMCommandPaletteScreen.applySelectedSuggestion()` always calls
  `SFMClientCommandInsertion.prepare()`. That helper appends a separator after
  every non-executable resource-location literal with children, so accepting
  `sfm:panel/open` jumps past still-valid sibling action ids such as
  `sfm:panel/open/right`. Merely removing the space is insufficient: if the
  exact no-change boundary remains selected, the next Tab currently yields
  focus instead of selecting a strict continuation.
- Executed-command history is consulted only while replacing the action-id
  range. There is no parameter-history projection after the action separator.
- `SFMTrajectoryMachineAction` already registers `focused`, `all`, and exact
  episode ids. `SFMOverlayAction` already registers `focused`, `all`, exact
  overlay ids, and `visible|hidden` continuations. Missing live suggestions
  therefore require tree/palette characterization before any provider change.
- `SFMCommandDraftAnalysis` can name a missing Brigadier argument. Vanilla
  `CommandSuggestions` additionally derives contextual usage with
  `findSuggestionContext()` and `getSmartUsage()`; the SFM palette currently
  reduces this information to a generic “provide the required argument” line.

**Work contract:**

1. Introduce a bounded typed palette-candidate seam that is deliberately
   compatible with B-1's later streaming accumulator. At minimum distinguish
   `ACTION_BOUNDARY`, `COMPLETE_HISTORY_COMMAND`, `LITERAL_CONTINUATION`,
   `ARGUMENT_VALUE`, and non-activatable `USAGE_HINT`; preserve canonical
   replacement range/text, origin, action identity, completion frontier,
   history recency, and insertion intent. Do not create a second public
   suggestion model that B-1 would immediately replace.
2. Keep the completed blank-palette rule: immediately after
   `sfm action invoke `, executable complete MRU commands rank first. Once the
   user enters a nonblank action-id query, history boosts its bare action
   boundary and that boundary outranks argument-bearing historical leaves.
   The leaves stay visible below it.
3. Separate Tab completion from Enter/execution preparation. Tab applies the
   candidate's exact replacement and no separator unless that candidate's
   explicit insertion policy says otherwise. At an exact action boundary with
   strict action-id descendants, another Tab selects/cycles to the first
   deterministic strict continuation rather than reapplying a no-op or moving
   focus. Space explicitly enters the argument frontier. Enter may still use
   preparation when advancing an incomplete command, but it must not fabricate
   an argument value.
4. Preserve raw persisted command-history entries. Build a bounded in-memory
   parsed projection for argument values, keyed by an explicit semantic
   history-family id plus compatible Brigadier slot/grammar. Prove the first
   family for the scene argument shared by `sfm:panel/open`, `/left`, `/right`,
   `/above`, and `/below`. Do not infer compatibility from slash prefixes or
   split quoted/greedy arguments on whitespace.
5. Extract one frontier analyzer shared by the palette and
   `SFMCommandDraftAnalysis`. It reports the current Brigadier parent/range,
   concrete suggestions, smart usages, named missing arguments/types, parse
   failures, and command completeness. Render usage/help rows even when no
   concrete value exists; they are keyboard/mouse-readable but cannot execute.
   Do not hide suggestions merely because a permissive token argument already
   makes Execute technically available.
6. Add exact command-tree and palette-screen characterizations for trajectory
   and overlay selectors before modifying their action classes. If the tree
   sees the values and the palette does not, fix palette frontier/application
   logic. If both unit routes pass but the live puppet fails, diagnose runtime
   freshness/origin availability rather than copying the values into another
   table.
7. Publish exact completion-application metadata for snapshot/episode TE-S2:
   before value, replacement range/text, optional deliberate separator,
   candidate kind/origin, and after value. TE-S2 records this as one semantic
   document transaction while preserving raw Tab/Space events; B-0 does not
   own undo or revisions.

**Validation:** Add or extend focused tests for all of the following:

- blank palette with recent commands still ranks complete MRU first;
- typed `open` with the same history ranks `sfm:panel/open` first and retains
  the complete command below it;
- first Tab produces exactly `sfm:panel/open` with no trailing space; second
  Tab advances to a strict descendant such as `/right`; Space produces exactly
  `sfm:panel/open ` and opens the scene argument frontier;
- explicit compatible scene history boosts a recent `sfm:text_editor` value
  across open directions, while unrelated slots and incompatible grammars do
  not receive it;
- `sfm:episode/trajectory/plan ` suggests `focused`, `all`, and exact live ids;
- `sfm:overlay/visibility/set ` suggests `focused`, `all`, and canonical exact
  ids, and its next frontier suggests `visible|hidden`;
- a command with an unbounded/unsuggested argument visibly reports its named
  Brigadier usage and cannot activate that explanatory row;
- quoted and greedy historical arguments round-trip through Brigadier ranges;
- Tab cycling never turns an exact no-op candidate into an unintended focus
  change; mouse acceptance, Up/Down selection, cancellation, and ordinary
  execution retain existing behavior.

```pwsh
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMClientActionPaletteSuggestionTests --wait-for-build-lock
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMClientCommandInsertionTests --wait-for-build-lock
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMCommandPaletteScreenTests --wait-for-build-lock
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMTrajectoryMachineActionTests --wait-for-build-lock
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMOverlayActionGrammarTests --wait-for-build-lock
```

Join the ordinary TE-S2 puppet after its document-history adapter lands. It
must pause visibly at each frontier and emit a structured candidate artifact
containing order, candidate kind, replacement range, usage text, history scope,
and final input after each Tab/Space operation.

**Completion criteria:** PALUX-1 through PALUX-7 have machine evidence and a
normal title-screen palette witness. The user can progressively discover the
panel-open family, reuse compatible parameter history, see registered dynamic
selectors and named missing arguments, and accept a candidate without an
implicit separator or focus escape. Blank complete-MRU behavior and Brigadier
execution semantics are unchanged. The candidate seam is directly consumable
by B-1 and TE-S2 rather than becoming transitional debt.

**Completion evidence — 2026-08-22:**

- `SFMPaletteCandidate` is the bounded typed candidate seam. Blank input keeps
  complete MRU commands first; nonblank `open` ranks the bare
  `sfm:panel/open` boundary first; Tab performs exact replacement without an
  implicit separator; a repeated Tab advances to the deterministic strict
  descendant (`sfm:panel/open/left` in the current registration order); and
  Space deliberately enters the scene argument frontier.
- Compatible panel-open scene arguments share parsed bounded history without
  changing persisted command history. Dynamic trajectory/overlay selectors,
  visibility values, and named non-activatable usage rows are represented at
  their actual Brigadier frontiers. Async candidate revisions clear stale
  rows immediately, so automation and users do not observe suggestions from a
  previous query.
- Candidate application publishes exact replacement metadata and participates
  in the command palette's ordinary document history. Focused Java tests cover
  ranking, insertion, history compatibility, frontier freshness, named usage,
  mouse/keyboard acceptance, and undo/redo around completion.
- The natural title-screen puppet
  `sfm:title_screen_ordinary_document_history` passed at `1280x720@auto` in
  run `sfm-title_screen-20260822-215127-171`. Figures 1 through 6 and the
  `artifact_palette-*.json` files prove blank MRU, nonblank boundary ranking,
  exact first Tab, strict second Tab, deliberate Space, selector/value
  suggestions, and required-argument usage without opening a chamber.
- The canonical full test run reported `1444 found, 1443 passed, 0 failed,
  1 aborted`; the sole abort is the intentionally opt-in installed symbol
  worker integration test. Canonical `run compile` passed. Implementation
  checkpoints include `4bf109948`; final hardening is recorded by the goal's
  completion commit below.

### [ ] B-1 Add cancellable streamed palette candidates without replacing Brigadier

**Dependency:** B-0 freezes the one-shot typed candidate kind, replacement,
frontier, usage, and history-scope fields. B-1 extends that same model with
source generations and batches; it must not reintroduce a parallel ranker or
erase B-0's boundary/usage semantics.

**Work:** Close D-7. Introduce the immutable candidate/batch/source contracts,
provider registry, generation cancellation, accumulator, deterministic ranking,
stable-selection retention, visible provider states, and privacy-safe timing/
count telemetry described above. Adapt ordinary Brigadier suggestions and the
existing action/history fuzzy results into the same view model while keeping
their existing one-final-future implementation valid. No provider may recurse
files, block on I/O, or mutate widgets on the Minecraft thread.

**Validation:** Pure tests drive interleaved providers and prove first batch,
later insertion/reordering, stable selected candidate, duplicate canonical
completion removal, identical display text from different addresses, stale
generation rejection, cancellation on input/context/close, provider failure,
truncation, deterministic ordering independent of batch timing, Brigadier range
application, incomplete-command insertion, and unchanged execution through
`SFMClientActionCommandTree`.

```pwsh
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMPaletteCandidateAccumulatorTests --wait-for-build-lock
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMCommandPaletteStreamingTests --wait-for-build-lock
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMClientActionPaletteSuggestionTests --wait-for-build-lock
```

**Completion criteria:** The existing palette can visibly accept multiple
asynchronous candidate batches for one Brigadier argument frontier, remains
responsive/cancellable, never applies stale results, and executes only a
command Brigadier parses as available and complete.

### [x] B-2 Capture typed multi-origin context and 2D editor projections

**Work:** Close D-8. Add immutable context snapshots, stable projection-origin
ids, provider/contributor hooks, and path-root derivation. Extend panel/editor
open contracts with optional explicit document addresses. Project the focused
panel/child, every relevant visible explorer selection, and every relevant
visible editor document independently. Add Text Editor v3 canvas x/y plus
optional text hit, legacy row/column/offset adapters, selection, read-only
state, complete document snapshot, and supplemental cursors. Reserve the typed
screen-point/ray shape without implementing world inspection.

**Validation:** Fixture workspaces cover one explorer plus one editor, multiple
explorers/editors with identical display paths on different devices, focus rank
changes without contribution loss, empty selection, directory/file parent-root
derivation, stale/closed panels, an editor without a document address, dirty and
read-only documents, Unicode/CRLF offset conversion, canvas whitespace,
multiple cursors, and captured-context immutability after focus/content changes.

```pwsh
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMContextSnapshotTests --wait-for-build-lock
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMPathContextProjectionTests --wait-for-build-lock
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMTextEditorContextProjectionTests --wait-for-build-lock
```

**Completion criteria:** A captured context retains independently addressable
explorer/editor origins and a true 2D editor point/document snapshot; no path is
guessed from title text and no later focus change can retarget the snapshot.

**Completion evidence (2026-08-14):** Commit `643385fb2` adds immutable
multi-origin snapshots, stable contributor/container/local origin ids,
independent visible explorer-root/selection and editor-document projections,
focused-origin ranking without contribution loss, exact current-text hashes,
read-only/dirty state, canvas x/y with optional Unicode-aware text hits,
supplemental cursors/selections, path-root derivation, and reserved screen-point/
ray values. `SFMContextSnapshotTests`, all `*ContextProjectionTests`,
`SFMScreenMultiplexerContextTests`, and `SFMExplorerPanelActionEmissionTests`
pass through `sfm-propagate-changes.exe test run --branch 1.19.2`; the
multiplexer uses the same pure assembly seam in production and headless tests.

### [ ] B-3 Deliver Ctrl+Shift+N bounded fuzzy file search

**Work:** Close the file-search portion of D-11 and D-12. Register the semantic
path-open/search action and its incomplete command form, add one contextual
Ctrl+Shift+N default through `SFMKeyBindingDefaults`, and attach an async path
candidate source to the address/query argument. Search all distinct B-2 roots
concurrently through A-2a devices, fuzzy-rank normalized path segments and file
names, stream early results, retain source labels, and show no-root/loading/
partial/error states. Accepting a result follows D-12 through existing
panel/reopen/preview ownership rather than a new direct screen mutation path.

**Validation:** In-memory and bounded-directory tests cover focused-root boost,
simultaneous explorer/editor roots, same display path on different devices,
files and directories, typo/subsequence ranking, early batches before walk
completion, cancellation while typing, symlink/traversal/exclusion rejection,
depth/entry/time/candidate bounds, explicit truncation, no ambient root, no
render-thread walking, text/directory/unsupported activation, dirty/terminal
panel preservation, incomplete-draft opening, default fingerprint/tombstone,
and execution via Brigadier.

```pwsh
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMFuzzyPathCandidateSourceTests --wait-for-build-lock
sfm-propagate-changes.exe test run --branch 1.19.2 --filter OpenPathActionTests --wait-for-build-lock
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMKeyBindingStorageTests --wait-for-build-lock
```

**Completion criteria:** Ctrl+Shift+N opens the familiar palette at a path
argument, useful contextual results arrive before a bounded recursive walk is
finished, accepting a result performs the approved safe open/reveal behavior,
and an empty context never scans a drive root.

### [~] B-4 Generalize reveal-in-explorer and bind Ctrl+Shift+E

**Work:** Close D-10 and the explorer vocabulary in D-11. Register semantic
explorer focus/open and reveal actions. Resolve the captured focused address or
explicit address/query to a resolver-contributed compatible explorer/reveal
intent. Reuse/focus an appropriate existing explorer or open one through the
established panel placement rules; preserve source origin and selected item.
Add one approved contextual Ctrl+Shift+E default and ambiguity choices through
the constrained palette. Prove both a path/file-explorer adapter and an
item/item-explorer (or deterministic item fixture) adapter; do not centralize a
file/item type switch. A focused Text Editor v3 document contributes its exact
resolver-issued concrete path and authority independently from any explorer
selection, so `Reveal in Explorer` is available from the document context and
never derives a path from title text.

**Validation:** Tests cover focused editor file, focused explorer selection,
independently targeting the non-focused contribution, directory, item id,
item wildcard query, absent/unavailable resolver, ambiguous equal-priority
origins, existing-compatible-explorer reuse, safe new-panel placement,
selection expansion/scroll, stale context, and no terminal/dirty-editor
replacement.

```pwsh
sfm-propagate-changes.exe test run --branch 1.19.2 --filter RevealInExplorerActionTests --wait-for-build-lock
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMExplorerRevealResolverTests --wait-for-build-lock
```

**Completion criteria:** Ctrl+Shift+E has one documented deterministic fallback;
explicit reveal can target any compatible concrete address/query; file and item
proofs use contributed explorer capabilities and preserve unrelated panels.

**Partial checkpoint (2026-08-16):** REVEAL-3 completes only the focused-
document exact-path contribution and compatible-explorer reuse/open behavior.
Ctrl+Shift+E fallback, item/query adapters, ambiguity handling, and every other
B-4 requirement remain incomplete.

### [~] B-5 Replace token callbacks with Alt+Enter contextual action offers

**Work:** Close D-9 and the contextual-action portion of D-11. Register the
semantic contextual-actions action and Alt+Enter default. Right-click over an
EditorV3 document opens this same constrained command-palette choice surface;
it does not own a second context-menu model. Add the
`SFMContextActionProvider` registry and open the existing constrained palette
with every applicable offer. Adapt `ProgramTokenContextActions` behavior to
whole-document/2D-context providers that emit canonical drafts for resource
identifier expansion, label/input/output/bool/if inspection, preserving all
applicable offers rather than first-match only. Java document providers add
both jump-to-definition and `sfm:symbol/references/open` where the captured
position is analyzable; Alt+F7 remains the direct references binding owned by
C-8/C-9. Add the mandatory path
existence/content and item-query reveal fixtures. Remove direct `Runnable`
dispatch; route Ctrl+Space according to D-9. Missing arguments stay in the
palette and use Brigadier suggestions/completeness.

**Validation:** Test zero/one/many providers, deterministic ranking and
deduplication, provider diagnostics, full document plus 2D point, nested AST
nodes, invalid/partial programs, canvas whitespace, Unicode/line endings,
multiple cursors, stale origin, unavailable server-backed inspection, both
example offers, incomplete action drafts, Alt+Enter/Ctrl+Space parity, and no
legacy direct callback invocation.

```pwsh
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMContextActionProviderTests --wait-for-build-lock
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMProgramContextActionTests --wait-for-build-lock
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMChoiceSessionTests --wait-for-build-lock
```

**Completion criteria:** Alt+Enter and editor right-click present the same
familiar searchable palette
constrained to all valid contextual action drafts; context includes the whole
document and true 2D point; missing arguments remain Brigadier/palette-owned;
definition and persistent-reference offers can coexist; and Ctrl+Space no
longer owns a separate one-to-one callback system.

**Partial checkpoint (2026-08-16):** CTXREF completes only the Java-symbol
provider, the shared Alt+Enter/right-click constrained palette, and the direct
reference gestures. General token-callback migration, path/item providers,
Ctrl+Space migration, and every other B-5 requirement remain incomplete.

### [x] B-5a Add explicit mouse-accessible cancellation to every palette surface

**Completion notes (2026-08-18):** The shared command-palette screen
now owns one visible Vanilla-like, narrated, Tab-focusable `Cancel` widget for
full and constrained surfaces. It dispatches canonical
`sfm:palette/close`, supports mouse and keyboard activation, and participates
in the same exactly-once choice-session cleanup as Escape, removal, external
dismissal, and successful execution. Cancellation neither runs the selected
draft nor mutates command history. Focused palette/context tests pass in the
full 1,187-test Java acceptance run. The contextual live matrix at
`platform/minecraft/build/sfm-toolchain/artifacts/game-test-preview/runs/`
`title_screen_con-20260818-172947-550` and standard-palette evidence at
`title_screen-20260818-173837-438` exercise the shared surface, including
right-click open, Cancel dismissal, reopen, and real action execution across
the supported scale/layout coverage. The shared closure is implementation
commit `d6947fa84` plus the installed revision/hash recorded under CLI-AST
0.12.4.

**Manual evidence (2026-08-18):** The full command palette and constrained
Alt+Enter/right-click choice surface close with Escape but expose no visible
mouse target for dismissal.

**Work:** Add a Vanilla-like, narrated, Tab-focusable `Cancel` button to the
shared `SFMCommandPaletteScreen` layout. The button must invoke the already
registered canonical `sfm:palette/close` action against the active action
surface rather than directly mutating screen state. Use the same widget and
lifecycle path for full command entry, bounded choices, toast choices, and
definition ambiguity. Preserve the input, Execute button, list scrollbar,
suggestion selection, and narrow/low-scale layouts. Escape, Cancel activation,
screen removal, external dismissal, and successful command execution must each
close a choice session and its interaction lease exactly once.

**Validation:** Extend palette/choice tests for mouse click, Tab/Shift+Tab,
Enter/Space activation, narration, all opening modes, narrow panels and Auto
plus GUI scales 1..8, lease/listener exactly-once semantics, no command-history
entry for cancellation, and no accidental execution of the selected action.
The contextual-actions puppet must open by right-click, dismiss by clicking
Cancel, reopen, and execute a real action.

```pwsh
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMCommandPaletteScreenTests --wait-for-build-lock
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMChoiceSessionTests --wait-for-build-lock
sfm-propagate-changes.exe puppet run title_screen_contextual_actions --branch 1.19.2 --variant declared --wait-for-build-lock
```

**Completion criteria:** Every palette-derived surface has an obvious mouse and
keyboard Cancel route backed by `sfm:palette/close`; all close paths clean up
exactly once; cancellation never executes or records another action; and the
button remains fully visible and reachable at every supported GUI scale.

### [ ] B-6 Prove contextual search/actions live and record the release boundary

**Work:** Add deterministic puppets for Ctrl+Shift+N streamed file search,
Ctrl+Shift+E reveal, and Alt+Enter constrained context actions. Each puppet
creates at least one explorer and one Text Editor v3 panel with independently
asserted addresses, types a fuzzy query slowly enough to observe early/later
batches, and writes machine artifacts for context projections, candidate
batches/ranking, selected concrete address, action draft, cancellation, bounds,
and final panel topology. Update changelog, coordinating release plan, source
references, default fingerprints, and propagation notes only after behavior is
approved.

**Validation:** Run the focused suites above, canonical compile/full tests, and
new puppets through the SFM CLI. Inspect representative screenshots, but make
pass/fail depend on text/JSON artifacts and semantic assertions rather than OCR.

```pwsh
sfm-propagate-changes.exe run compile --branch 1.19.2 --wait-for-build-lock
sfm-propagate-changes.exe test run --branch 1.19.2 --no-capture --wait-for-build-lock
sfm-propagate-changes.exe puppet run title_screen_contextual_file_search --branch 1.19.2 --variant 1280x720@auto --wait-for-build-lock
sfm-propagate-changes.exe puppet run title_screen_contextual_actions --branch 1.19.2 --variant 1280x720@auto --wait-for-build-lock
```

**Completion criteria:** Every FFILE/REVEAL/CTXA/ECTX guidance id has pure,
integration, and live evidence; streamed latency and bounds are computationally
observable; the release plan records whether B is release-blocking; no
propagation/publication occurs without a later goal.

## Phase C — Source opening and jump-to-definition after the lazy explorer foundation

### C-1 Superseded: persisted default multi-root explorer workspace

**Supersession:** Do not implement this item. The 2026-08-12 design replaces
it with X-1 through X-7 in
`docs/tasks/typed selections relations and lazy explorers plan.md`. The text
below records the rejected global-workspace proposal for provenance only.

**Work:** Close the model/storage portion of D-16. Add versioned
`SFMExplorerWorkspace`, root entries/repository, generation/change events,
stable ids/order/labels/capabilities/derived project metadata, normalized path-device references,
and client-local persistence. Seed the current instance root through an
explicit built-in contribution; keep the optional toolchain-provided
`sfm_source` single-scene device independent from workspace metadata. Never
infer source roots by walking parent directories. Keep panel view state outside
the shared catalog.

**Validation:** Cover empty/default workspaces, two and many roots, duplicate
and Windows case-equivalent paths, symlinks/containment, derived project/source-set classification,
add/remove/reorder, restart round-trip, concurrent panel observers, missing
drives/directories, corrupt/unknown schema, stable generation, no source-byte
storage, and absence of ambient drive/profile/process-root authority.

```pwsh
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMExplorerWorkspaceTests --wait-for-build-lock
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMExplorerWorkspaceStorageTests --wait-for-build-lock
```

**Completion criteria:** One persisted default workspace can truthfully retain
multiple bounded roots plus derived project/source-set metadata across restart;
all traversal authority remains in A-2a devices, and two panels share catalog
changes without sharing focus/selection/scroll state.

### C-2 Superseded: dedicated workspace panel and global root management

**Supersession:** Do not implement this item. Generic explorer sessions,
explicit set-valued selectors, direct typed root actions, and selection-backed
locations are owned by selection/explorer X-1 through X-7. Native picker/drop
adapters remain later inputs into that same action path; they may not recreate
the global catalog described below.

**Work:** Close D-15 and the UI/action portion of D-16. Register
`sfm:explorer/workspace` as an `SFMClientScreenType` under every
`sfm:panel/open[/direction]` action. Present ordered top-level roots with
keyboard/mouse expansion, selection, status, labels, derived project metadata,
and action-backed Add Folder/Remove/Move controls. Add the exact hierarchical root
actions. Implement `SFMFolderPicker` plus the bundled TinyFD 1.19.2 adapter and
modal coordinator; send picker, explicit path, and `Screen.onFilesDrop`
outcomes through the same validated add-root executor.

**Validation:** Pure/fake-picker tests cover success, cancel, failure, duplicate
open suppression, unavailable native backend, suggested starting root, focus
restoration, no game-key leakage, and Minecraft-executor application. Action
and screen tests cover focused/default targeting, complete/incomplete drafts,
directional panel scene grammar, add/drop parity, removal confirmation with an
open document, ordering, project classification, narration/Tab/Enter/Space, and two live panels.
Automated puppets use typed/drop routes; one manual development witness uses
the actual native chooser.

```pwsh
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMFolderPickerTests --wait-for-build-lock
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMExplorerWorkspacePanelTests --wait-for-build-lock
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMExplorerWorkspaceActionTests --wait-for-build-lock
sfm-propagate-changes.exe puppet run title_screen_multi_root_workspace --branch 1.19.2 --variant 1280x720@auto --wait-for-build-lock
```

**Completion criteria:** The workspace panel manages more than one root through
ordinary Minecraft navigation; native pick, explicit command, and drop have
identical authority semantics; cancellation is harmless; roots persist; and no
test depends on operating-system UI automation.

### [x] C-3 Open addressed explorer files in Text Editor v3 at source ranges

**Work:** Close D-17. Extend `SFMTextDocumentSource`, panel open context, and
reopen recipe with the X-1 concrete path/root identity, source metadata/hash,
read-only capability, and optional target range. Add the file-open action and
generic explorer activation/preview behavior. Load only through the lazy
resolver; make unavailable, removed-root, stale-content, unsupported encoding,
binary, oversized, and I/O states visible. Keep literal/resource sources
compatible and do not add host-file saving in this slice.

Freeze the first file activation contract as part of this item: Space opens or
updates an explorer-owned preview slot while retaining explorer focus; Enter
opens/focuses that safe preview target; Ctrl+Enter creates and focuses a new
panel entry rather than reusing the preview. Directories continue to use
expansion/navigation actions instead of being misclassified as text documents.
Every gesture emits the same self-contained registered action as palette or
binding invocation; execution captures the explorer id, concrete path, source
revision/hash, and placement mode rather than consulting whichever panel is
focused later. The completed A-2c/C-3 goal freezes the canonical public spelling
as `sfm:path/open <concrete-path-address>`; the command-palette surface remains
unreleased.

Text Editor v3 must be emplaced as a panel-native document view. Its viewport,
text surface, cursor, scroll state, diagnostics, and controls consume the
allocated panel bounds at every supported GUI scale; it must not reproduce the
centered bounded-form dead space exposed by the 2026-08-13 preferred-v1 matrix.

**Validation:** Cover Java/text opening, exact UTF/CRLF line-column/byte-span
placement, focus versus preview placement, Space/Enter/Ctrl+Enter semantics,
duplicate editor recipes with independent cursor/scroll state, source changed
between recipe/open, root removed while editor remains open, reopen after
restart, binary/oversized/unreadable files, dirty-panel non-clobbering, and no
title-to-path guessing. Add responsive layout assertions and a declared
3840x2130 Auto/numeric GUI-scale matrix whose final figure shows an explorer
opening the real `SFM.java` read-only in an adjacent Text Editor v3 panel.

```pwsh
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMPathTextDocumentSourceTests --wait-for-build-lock
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMTextEditorAddressedOpenTests --wait-for-build-lock
sfm-propagate-changes.exe puppet run title_screen_explorer_open_sfm_java --branch 1.19.2 --variant declared --wait-for-build-lock
```

**Completion criteria:** A user can add/seed the SFM source tree, browse any
source set, and open a Java file read-only in Text Editor v3 with a concrete
document address and exact open-at-range behavior suitable for navigation.
Space, Enter, and Ctrl+Enter have distinct proven preview/focus/new-panel
semantics; unrelated or dirty panels are never replaced; and the editor uses
its panel allocation coherently from GUI scale 1 through the viewport maximum.

**Completion evidence (2026-08-13):** Commit `6dc3d2d04` adds typed
resolver-text requests/results, strict bounded UTF-8 filesystem reads,
address/root/hash/metadata/range document snapshots, non-blocking deferred
Text Editor v3 panels, `sfm:path/open`, and typed explorer-preview ownership.
Space, Enter, Ctrl+Enter, directory expansion, reopen identity, stale/removed/
binary/oversized/encoding/I/O diagnostics, range mapping, no-write behavior,
and safe non-clobber placement are covered by focused tests and the final
838/838 suite. The declared `title_screen_explorer_open_sfm_java` run passed
all nine variants—Auto plus GUI scales 1 through 8—at 3840x2130. Every variant
wrote a screenshot and `sfm.addressed-source-puppet/1` artifact proving the
real `SFM.java` selection, read-only `sfm:text_editor_v3`, canonical root/path,
SHA-256/byte metadata, focus/panel bounds, and no-write invariant. All nine
figures were visually inspected; the run is
`platform/minecraft/build/sfm-toolchain/artifacts/game-test-preview/runs/`
`title_screen_exp-20260813-212137-953`.

### [x] C-4 Add location-aware definition analysis and the async provider

**Work:** Close D-18 and D-19 with the linked CLI-plan slice. Extract/reuse the
existing Java-analysis engine behind a typed definition-at-document-location
request. Add its manually invokable CLI form and a supervised long-lived framed
worker mode, both returning the same versioned Facet output. Compose all
explicit resolver-authorized roots with branch source-set visibility and the
immutable dependency index; support a caller-supplied current-document overlay
without writing it.
On the Minecraft side, add provider registry/discovery, process lifecycle,
generation cancellation/stale rejection, typed recovery diagnostics, bounded
telemetry, and clean shutdown/restart. Do not parse Java independently in the
screen or spawn a fresh CLI process for every steady-state query.

**Validation:** CLI scenarios cover type/import, field, method/overload,
nested/local names where supported, same simple name in two packages,
cross-source-set visibility, dependency source, whitespace/no symbol,
unresolved/incomplete index, supplied overlay versus disk, stale hash, CRLF,
Unicode, invalid position, zero/one/many definitions, deterministic ordering,
and text/JSON parity. Worker/provider tests cover handshake/schema mismatch,
executable missing, startup/query timeout, cancellation, late responses,
crash/restart, bounded retained state, child reaping, no render-thread work,
privacy-safe telemetry, and byte-identical direct/worker results.

```pwsh
cargo test --all-features java_analysis
cargo test --all-features --test java_analysis_scenarios
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMSymbolServerNavigationProviderTests --wait-for-build-lock
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMSymbolServerInstalledIntegrationTests --wait-for-build-lock
```

**Completion criteria:** The existing SFM symbol engine can resolve the symbol
at a concrete editor location using the exact current snapshot and complete
workspace/dependency context; Minecraft receives typed cancellable results
without blocking its render thread; steady-state queries reuse one supervised
worker; and no-match is authoritative only when index completeness permits it.

**Completion evidence (2026-08-15):** Commits `21a9b06e7` and `c32607d2f`
complete the location engine, supervised worker, retained resolution surface,
and real-process Java integration. Minecraft now projects B-2 snapshots into a
provider-neutral, immutable definition request; owns explicit origin-scoped
supersession and worker-global workspace generation; and supervises one CLI
process through daemon state/I/O/timer threads with no render-thread wait. The
production adapter supports typed unavailable/stale/protocol/timeout/restart
recovery and emits only ids, hashes, counts, durations, and outcomes by default.
The installed release probe rotated through a main source, gametest source, and
dependency definition for 24 warm samples: 72.001 ms median, 103.068 ms p95,
107.290 ms maximum, 501,850,112-byte peak working set, one observed descendant,
and zero leaks. Cancellation and acknowledged shutdown were exercised. Raw
evidence is retained in
`docs/architecture/evidence/symbol-server-installed-probe-1.19.2.json`.
Focused protocol/provider/context tests, the opt-in installed-process Java test,
Rust Java-analysis/scenario suites, strict Rust checks, and canonical compile
pass. The complete Java suite found 891 tests: 890 passed, zero failed, and the
installed-process test was the sole expected opt-in abort when its properties
were absent. The source audit exited 0 with 30 pre-existing grouped unresolved
font-render-rule warnings. That provider slice introduced no F12/Alt+Enter
result navigation or live UI proof; the later completed C-5/C-6 slice consumed
the provider without retroactively broadening its scope.

### [x] C-4a Replace file explorer text markers with ItemStack icons

**Parallel owner:** explorer-presentation agent; write scope is the explorer
presentation contributor/default registry and focused presentation tests.

**Work:** Add an ordered file-path presenter to
`SFMExplorerPresentationRegistry.minecraftDefaults()`. Resolve a `file://`
directory row to the active theme's `directory` icon (`minecraft:chest`) and a
`file://` file row to the active theme's ordinary/unknown file icon
(`minecraft:paper`). Do not key only on `expandable`; preserve the item-registry
presenter, future richer file-extension presenters, labels, disclosure
chevrons, narration, list/small-icons layout, and the generic fallback for
unclaimed non-file schemes.

**Validation:** Cover file directory/file, root/child, expanded/collapsed,
list/small-icons, unavailable theme item fallback, item-registry precedence,
custom earlier/later contributors, and an unclaimed non-file row. Assert the
resolved item ids rather than only icon class.

```pwsh
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMExplorerFilePresentationTests --wait-for-build-lock
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMExplorerPresentationRegistryTests --wait-for-build-lock
```

**Completion criteria:** A real file explorer visibly uses chest ItemStacks
for directories and paper ItemStacks for files with no `[D]`/`[F]` markers,
while registry/item and unknown-domain presentation remains correct.

**Completion evidence (2026-08-15):** The ordered file-domain presenter uses
the active theme's `minecraft:chest` directory item and `minecraft:paper` file
item without overriding item-registry/custom presenters or generic fallback.
Focused presentation tests and the complete Java suite passed. The final live
journey and all nine declared GUI-scale variants visibly show chest directories
and paper files with no textual `[D]`/`[F]` replacement.

**Follow-up ownership (2026-08-18):** Spatial-semantic plan NX-3a owns the
bounded adapter that projects persistent reference-result category/file/span
rows through this completed ItemStack presentation pipeline. C-4a remains the
one icon registry/theme/fallback authority; NX-3a must not reinterpret every
expandable non-file object as a filesystem directory or create parallel icon
configuration.

### [x] C-4b Add explicit contrast-backed read-only EditorV3 chrome

**Parallel owner:** EditorV3-chrome agent; write scope is read-only status
geometry/rendering/localization and focused chrome tests.

**Work:** Change the localized status text to `Read-only` and render it only
for read-only documents in EditorV3's bottom control lane between `#` and Done.
Use a solid high-contrast rectangle (and border if needed), derive geometry
from the neighbouring controls, clamp/trim at narrow widths, and keep the
status non-interactive so it cannot receive focus or intercept either button.
Retain read-only narration and all save/close behavior; writable documents
show no badge.

**Validation:** Add pure geometry/state tests for read-only/writable, ordinary
and narrow panel bounds, overlap exclusion, text/rectangle containment, and
stable neighbour hit bounds. Exercise render ordering and keyboard/mouse access
to `#` and Done. Extend the declared source-editor GUI-scale puppet assertions.

```pwsh
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMTextEditorReadOnlyChromeTests --wait-for-build-lock
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMTextEditorAddressedOpenTests --wait-for-build-lock
```

**Completion criteria:** Every read-only EditorV3 panel has a legible
contrast-backed `Read-only` status physically between the existing controls at
supported sizes/scales; writable panels omit it; no new focus/hit target or
save behavior exists.

**Completion evidence (2026-08-15):** EditorV3 computes one non-interactive
bounded status rectangle between `#` and Done, renders the exact localized
`Read-only` text over a contrasting fill/border only for read-only documents,
and preserves both neighbour hit/focus bounds. Geometry/state tests passed and
the final Auto plus scales 1 through 8 screenshots were individually inspected
with no overlap or clipped control.

### [x] C-4c Add the Rust Arborium Java highlighting contract and service

**Parallel owner:** coordinator/main lane; Phase 0.11 schema, engine, service,
and Rust validation remain serialized here.

**Work:** Complete linked CLI-AST Phase 0.11. Add typed versioned direct and
framed-worker request/result contracts carrying request/origin generation,
explicit language, exact source text/SHA-256, parser fingerprint, diagnostics,
timing/cache evidence, and non-overlapping UTF-8 spans with Arborium flat tags
plus canonical ChatFormatting names. Add `syntax highlight` and supervised
`syntax serve`; compile the Java query once, reuse bounded parse context/cache,
cancel/reap cleanly, keep stdout protocol-only, and never require the symbol
workspace/index. Add `arborium-highlight = 2.18.1` without its tree-sitter
feature and reuse the pinned Arborium Java grammar/patched parser.

Record the reproducible tracked-extension audit in developer documentation:
Java 1,524; Rust 400; JSON 178; Gradle/Groovy 107; PowerShell 55; Markdown 51;
TypeScript 14; TOML 7. Keep Java as the only enabled new grammar and list the
remaining supported languages in that order. Preserve existing SFML/G4 paths.

**Validation:** Rust tests cover Java declarations/imports/comments/strings/
numbers/annotations/generics/text blocks, empty/malformed Java, overlap
flattening, deterministic order/coalescing, Unicode/combining/astral/CRLF/
trailing-newline boundaries, format mapping, unsupported language, malformed
hash/range/oversize, direct/worker byte parity, one-process/query reuse,
cache hit/miss, cancellation, timeout, crash/restart, frame bounds, stdout/
stderr separation, and clean shutdown with no child leak.

```pwsh
cargo test --all-features syntax_highlight
& .\platform\cli\sfm-propagate-changes\check-all.ps1
```

**Completion criteria:** The installed/current-source Rust CLI can highlight
exact Java text into deterministic, source-hash-bound ChatFormatting spans by
direct command and reusable supervised worker; compilation/query setup is not
repeated per request; no additional language or Java ANTLR parser ships.

**Completion evidence (2026-08-15):** Versioned Facet direct/worker schemas,
`syntax highlight`, and supervised `syntax serve` are implemented with the
pinned Arborium Java grammar/highlighter and existing patched tree-sitter.
Direct and worker results agree; one worker/query is reused; a repeated real
`SFM.java` request was a cache hit and returned the same 356 spans, 9 tags, and
10 formatting values in 75 microseconds of Rust work after a 1,776-microsecond
cold parse. `check-all.ps1` passed 569 tests with 3 ignored plus all 8 scenario
tests. Java remains the only newly enabled grammar.

### [x] C-4d Apply current Rust Java spans in EditorV3 asynchronously

**Work:** Add a provider-neutral Java highlight contract, codec, coordinator,
and supervised-process adapter. Derive `.java -> java` from concrete typed
document metadata, submit the exact current EditorV3 projection off the render
thread, cancel/supersede by editor origin/generation, validate protocol/hash/
language/span/style identity, convert UTF-8 byte boundaries safely to Java's
UTF-16/glyph projection, and publish immutable styles on the Minecraft
executor. Render Components/glyphs with the returned ChatFormatting while
preserving cursor/selection/open-target overlays. Never call Rust or parse in
`render`; unsupported/missing/failed/stale service leaves readable existing or
plain text and a bounded diagnostic state.

**Validation:** Fake-provider and real installed-worker tests cover request
text/path/language/hash, one editor and several editors, cancellation and late
results, content mutation, reopened/closed panels, Unicode/CRLF conversion,
malformed/unknown formatting, unsupported/no-path documents, worker missing/
crash/restart, cache evidence, render-thread prohibition, no per-frame query,
existing SFML/G4 parity, and styled Java glyph output.

```pwsh
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMSyntaxHighlightProviderTests --wait-for-build-lock
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMTextEditorSyntaxHighlightTests --wait-for-build-lock
```

**Completion criteria:** Opening a concrete `.java` document in EditorV3
asynchronously transitions from readable fallback text to Arborium-derived
ChatFormatting without a frame stall; stale spans never apply; other document
types retain their established rendering.

**Completion evidence (2026-08-15):** The Java provider/coordinator supervises
the installed Rust worker off-thread, validates every schema/hash/language/
origin/generation/range/style witness, converts UTF-8 ranges safely, and
publishes immutable styles through the Minecraft executor. Fake-provider,
protocol, stale/cancel/reopen, Unicode/CRLF, fallback, and real installed-worker
tests passed. The final live artifact records 135,044 microseconds from cold
worker launch to visible styles and 80,420 microseconds for a warm cache-hit
query-to-visible update, with one launch attempt and no retained raw source.

### [x] C-5 Register jump-to-definition and integrate result navigation

**Work:** Register `sfm:symbol/definition/open`, its `sfm:text_editor` F12
default, and an Alt+Enter context offer. Capture B-2 editor context once and
invoke C-4. For one result, open/focus the addressed target at its range using
C-3. For multiple results, open the shared constrained palette with stable
labelled candidates. Surface no-symbol, missing worker, stale request,
incomplete index, and failure with semantic retry/index/provider actions.
Preserve safe preview-slot ownership and do not replace dirty editors,
terminals, or unrelated panels.

**Validation:** Test palette invocation and direct F12 parity, contextual
availability, one/many/none/error outcomes, target in current/other root/
dependency source, same-file navigation, repeated/stale requests, result
selection stability, back/focus behavior, open target already visible, safe
panel placement, and no hard-coded token-only fallback.

```pwsh
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMJumpToDefinitionActionTests --wait-for-build-lock
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMSymbolDefinitionPaletteTests --wait-for-build-lock
```

**Completion criteria:** From a path-addressed Java editor, F12 and Alt+Enter
reach the same registered action; an unambiguous definition opens at the exact
source range; ambiguity is user-selected; and failures are visible and
recoverable without UI stalls or unsafe panel replacement.

**Completion evidence (2026-08-15):** `sfm:symbol/definition/open` is the
shared palette/F12/Alt+Enter route in the `sfm:text_editor` situation. Focused
tests and the live journey prove exact SFM-source navigation, reuse of an
already-visible target panel, a deterministic two-candidate `StringDistances`
choice, and exact acquired Forge dependency-source navigation to
`FMLClientSetupEvent.java`. Rust-emitted Windows extended paths are normalized
at the Java boundary without weakening canonical path identity.

**User-testing boundary correction (2026-08-16):** This completion evidence is
valid for the named fixtures only. It does not prove implicit JDK `java.lang`,
locals/parameters, every member/annotation/import position, all resolver-root
compositions, or desired current-pane-stack placement. The observed failures
and surprise `openRight` behavior are authoritative regressions assigned to
C-7; C-5/C-6 must not be cited as satisfying NAVHARD/NAVPLACE.

### [x] C-6 Prove the SFM-source jump-to-definition journey live

**Work:** Add one deterministic live journey: open a generic explorer, add or
use an explicitly seeded SFM source root, browse a Java file, open it in Text
Editor v3, place the cursor on an imported SFM type and a dependency type, use
F12, choose an ambiguous candidate fixture, return/focus between panels, and
capture explorer locations, editor text/address/cursor, definition result, worker
telemetry, and screenshots. The same evidence must prove chest/paper file
icons, the contrast-backed bottom-lane `Read-only` status, and visibly distinct
Java keyword/comment/string/type formatting sourced from the Rust result.
Capture syntax request/result hash, parser fingerprint, span/tag/format counts,
cache status, and request-to-visible-style latency without writing raw source
to default telemetry. Measure cold startup and warm query-to-visible-target
latency for both highlighting and definition navigation; if either user-visible
pause remains multi-second, profile the observed dominant stage before calling
the phase complete.

**Validation:** Run focused CLI/Java tests, canonical compile/full suite, and
the live puppet through the SFM CLI. Native picker use remains a separate
manual witness; the deterministic puppet uses an explicit seeded root.

```pwsh
sfm-propagate-changes.exe run compile --branch 1.19.2 --wait-for-build-lock
sfm-propagate-changes.exe test run --branch 1.19.2 --no-capture --wait-for-build-lock
sfm-propagate-changes.exe puppet run title_screen_sfm_java_jump_to_definition --branch 1.19.2 --variant 1280x720@auto --wait-for-build-lock
sfm-propagate-changes.exe puppet run title_screen_explorer_open_sfm_java --branch 1.19.2 --variant declared --wait-for-build-lock
```

**Completion criteria:** Machine-readable artifacts prove the complete
folder/explorer/file/cursor/definition/target chain, visual artifacts prove
the chest/paper/read-only/Java-style experience across the declared source-
editor scale matrix, warm highlighting and definition latency are recorded and
acceptable, every WSPACE/SYMBOL/SRCPRES/READONLY/HILITE guidance id has
evidence, and no propagation/publication occurs without a later goal.

**Completion evidence (2026-08-15):** The deterministic end-to-end run is
`platform/minecraft/build/sfm-toolchain/artifacts/game-test-preview/runs/`
`sfm-title_screen-20260815-151735-957`. Its five inspected figures and typed
artifacts prove explorer -> highlighted `SFM.java` -> exact SFM definition ->
stable ambiguous choice -> exact acquired Forge source, including addresses,
cursor/ranges, hashes, worker generations, cache state, and no source mutation.
The declared Auto plus GUI scales 1 through 8 run is
`platform/minecraft/build/sfm-toolchain/artifacts/game-test-preview/runs/`
`sfm-title_screen-20260815-152016-576`; all nine final screenshots were
individually inspected after the exact `Read-only` localization correction.

Warm definition navigation took 182,577 microseconds and dependency navigation
took 279,692 microseconds; ambiguity became visible in 1,363,569 microseconds
and the selected target followed in 298,418 microseconds. The 6,766,505-
microsecond cold SFM definition remains honestly recorded. Its durable stage
probe in `docs/architecture/evidence/symbol-server-installed-probe-1.19.2.json`
attributes the dominant Rust cold cost to parsing 1,505 Java files
(2,177,915 of 3,209,138 microseconds), while the warm worker reuses that state.
Focused Rust/Java tests, `check-all.ps1`, canonical compile, the full Java suite,
and both declared puppets exited successfully. No propagation, publication, or
release operation was performed.

## Previously completed vertical slice

The **generic explorer to addressed source editor slice** is complete in
implementation commit `6dc3d2d04`. It completed A-2c and C-3 without beginning
symbol-worker/F12 work, fuzzy search, picker destinations, propagation,
publication, or release work.

The final evidence is:

1. The canonical compile passed, and the complete Java suite reports `838`
   found, `838` passed, `0` failed, skipped, or aborted.
2. Focused explorer grammar/removal, resolver-read, addressed-document,
   Text Editor v3, range, preview-placement, metadata, and action tests passed.
3. `title_screen_item_icon_command_palette` passed at `1280x720@auto`; its
   browsable run is `platform/minecraft/build/sfm-toolchain/artifacts/`
   `game-test-preview/runs/title_screen_ite-20260813-214345-182`.
4. `title_screen_explorer_open_sfm_java` passed its declared 3840x2130 matrix:
   Auto plus GUI scales 1 through 8. Every variant contains a screenshot and
   `sfm.addressed-source-puppet/1` artifact, and all nine screenshots were
   visually inspected. The run is `platform/minecraft/build/sfm-toolchain/`
   `artifacts/game-test-preview/runs/title_screen_exp-20260813-212137-953`.
5. The structured evidence identifies real `SFM.java`, the canonical root/path,
   immutable source metadata/hash/range, read-only `sfm:text_editor_v3`, focus
   and panel bounds, effective GUI scale, and the no-write invariant.
6. A final legacy scan finds the removed developer action ids only in negative
   assertions. The obsolete large-explorer runtime hook and legacy puppets are
   gone. No propagation, publication, or release operation was performed.

## Previously completed definition-provider vertical slice

The accepted goal is the complete **captured editor context to warm definition
provider** boundary:

> Complete B-2 and C-4 in this plan together with Phase 0.10.1 through 0.10.4
> in `docs/tasks/cli ast refactoring suite plan.md`. Deliver immutable editor
> context plus a manually invokable and supervised warm definition-at-location
> provider. Stop before C-5/F12/Alt+Enter result navigation, C-6 live UI proof,
> fuzzy file search, writable documents, picker destinations, propagation,
> publication, or release work.

**Goal completion bookkeeping (2026-08-15):** B-2, C-4, and CLI-AST Phase
0.10.1 through 0.10.4 are complete. The implementation freezes D-8's typed
coordinate model, closes D-18's exact source-location request, and closes
D-19's supervised long-lived worker as the first transport. Focused/direct/
worker/provider tests, installed latency and process-liveness evidence,
canonical compile/full-suite validation, documentation, and completion notes
are recorded. No F12 or result-navigation behavior is part of this result.

Its observable completion state is:

1. Text Editor v3 and other contributors can capture one immutable,
   independently addressable context containing the concrete document/root/
   source-set identity, exact current text and content hash, read-only/dirty
   state, true canvas x/y, optional UTF-aware text row/column and byte offset,
   primary selection, supplemental cursors, origin identity, and request/
   workspace generations. Later focus or edits cannot retarget that snapshot.
2. The installed CLI has an unambiguous direct location form for
   `symbol show-definition` and returns a versioned typed zero/one/many result
   with canonical symbols, source spans, confidence, completeness,
   fingerprints, diagnostics, and recovery actions. Selector mode remains
   unchanged and mutually exclusive with location mode.
3. Location resolution reuses the existing source/dependency symbol engine for
   imports, types, fields, methods/overloads, constructors, nested names,
   cross-source-set visibility, and dependency sources. A supplied unsaved
   document snapshot is overlaid in memory for that request and is never
   written to disk; ambiguity and incomplete indexes remain explicit.
4. `symbol serve` provides a versioned framed protocol on stdout with logs on
   stderr, handshake/capability negotiation, many requests per process,
   cancellation and generation replacement, precise cache invalidation,
   bounded retained state, clean shutdown/restart, and no orphan child workers.
   Direct and worker results are canonically equivalent.
5. Minecraft owns a provider-neutral asynchronous registry/adapter around that
   worker. It performs no Java parsing or process waits on the render thread,
   rejects stale/late responses, visibly handles unavailable/crashed workers,
   can restart cleanly, and emits privacy-safe hash/count/duration telemetry.
6. Installed cold/warm benchmarks cover same-file, another SFM source set, and
   a dependency definition. The acceptance target remains warm median at or
   below 250 ms, warm p95 at or below 750 ms, no warm query above one second,
   and zero leaked processes; a miss requires profiling and fixing the measured
   dominant stage rather than weakening the target silently.

The completed work proceeded in parallel after the request/result DTO was frozen:

- a CLI analysis lane owns direct definition-at-location and scenarios;
- a worker lane owns framing, reuse, invalidation, cancellation, and process
  cleanup;
- a Minecraft lane owns immutable context projections and the provider-neutral
  async adapter against captured protocol fixtures;
- one integration owner serializes shared schema, executable discovery,
  lifecycle wiring, direct/worker parity, benchmark evidence, plans, and docs.

The completed goal freezes D-8's coordinate contract and closes D-18/D-19 with
implementation evidence. It stopped one boundary before F12/keybinding/palette
result navigation, allowing the later C-4a-through-C-6 slice to consume a
measured deterministic provider without mixing initial provider design with UI
navigation. That later slice is recorded immediately below. The independent
fuzzy-file chain remains available after B-2 and was not part of either goal.

## Phase C follow-up — symbol correctness, persistent references, and responsive interaction

### [x] C-7 Harden definition coverage, resolver authorization, and pane-stack placement

**Work:** Complete linked CLI-AST Phase 0.12.1 and consume its revised typed
results without adding Java-side parsing. Make definition-at-position resolve
branch-selected JDK sources and implicit `java.lang` (`String`, `Object`, and
`StringBuilder`), local variables, parameters, fields, methods/overloads,
annotation names/usages, and imported symbols. Repair the Java context adapter
so a resolver-authorized document under any negotiated branch source set maps
to the deepest unique worker root; retain fail-closed containment and explicit
ambiguity. Reproduce both quoted false-negative messages before correction and
keep genuine unavailable/incomplete cases typed and actionable.

Replace `SFMDefinitionNavigation`'s unconditional unseen-target `openRight`
path. First focus/navigate an exact already-open document; otherwise push one
read-only, hash/range-pinned editor entry into the originating pane's stack and
focus it. Never replace a dirty editor or terminal, and never create new split
geometry unless an explicit directional panel action requested it.

**Validation:** Rust scenario and Java integration fixtures cover every named
JDK/symbol category, declaration/reference positions, imports/static imports,
same names/overloads, source sets, dependency/JDK/workspace origins, missing or
stale indexes, exact authority mapping, ambiguous roots, changed snapshots,
and both verbatim regression messages. Workspace tests cover exact-visible
reuse and nested `[[1,2],3]`/stack placement with no extra visible pane.

```pwsh
cargo test --all-features java_analysis
cargo test --all-features --test java_analysis_scenarios
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMDefinitionContextAdapterTests --wait-for-build-lock
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMJumpToDefinitionActionTests --wait-for-build-lock
```

**Completion criteria:** F12 navigates every statically resolvable named
fixture—including the three JDK types—without either false-negative message;
real incompleteness remains truthful; and unseen definitions become focused
tabs/entries in the current pane rather than surprise side panels.

**Completion evidence (2026-08-17):** CLI commit `bd38aae8b`, Java commit
`2c013aa66`, and final interaction-stability commit `727bcef2c` implement the
named JDK/local/member/annotation/import coverage, exact authorization and
completeness regressions, and current-pane stack placement. Strict Rust checks,
the full Java suite, focused provider/navigation tests, and installed-worker
integration passed. SRC navigated all nine declared definition fixtures,
including `String`, `Object`, and `StringBuilder`, while reporting
`no_unexpected_panes=true`; genuine partial reference coverage remained
truthfully visible rather than suppressing known definitions.

### [x] C-8 Add location-aware usages and a persistent reference explorer

**Work:** Complete linked CLI-AST Phase 0.12.2/0.12.3. Add a provider-neutral
references request at the same immutable document/hash/UTF-aware location as
definitions, with direct `symbol list-usages` location form and supervised
worker capability. Return the resolved target symbol plus deterministic,
categorized source spans, completeness, diagnostics, index identities, and
recovery actions; do not require the UI to guess an exact selector first.

Register `sfm:symbol/references/open`. Materialize each accepted response as a
versioned session-scoped reference-result entity and a resolver-contributed
generic explorer location (provisional spelling
`symbol-references://<result-id>/`, frozen with parse/print fixtures before it
enters history). Project stable category -> file -> span rows with source-set/
origin labels. The result panel survives opening a row. Row activation uses an
explorer-owned source preview/current-pane stack target so repeated reference
jumps preserve the result list and unrelated panels. A missing/partial index
remains visible in the explorer instead of becoming an empty authoritative set.

**Validation:** Cover types, locals/parameters, fields, methods, annotations,
imports, method references, overrides where represented, zero/one/many,
workspace/dependency/JDK origins, ambiguous/unsupported targets, incomplete
indexes, stale document/result generations, direct/worker parity, stable row
ordering, retained panel identity, repeated activation, exact source ranges,
and no one-time choice-session substitution.

**Completion criteria:** Alt+F7/palette invocation opens one persistent generic
reference explorer whose complete identity and diagnostics remain visible while
the user repeatedly jumps among source ranges; no result is discarded merely
because one row was opened.

**Completion evidence (2026-08-17):** Commits `bd38aae8b` and `2c013aa66`
implement location-aware usage lookup, immutable reference-result entities,
generic explorer projection, retained result/source identities, and repeated
row activation. Strict Rust, pure/integration, full Java, and installed-worker
validation passed. SRC returned 76 ordered usages with the deliberately
incomplete dependency-index state visible, retained one reference-result
identity, and opened three source rows without replacing the explorer.

### [x] C-9 Add symbol hover/click and one contextual-action gesture surface

**Work:** Close D-25/D-27 while consuming B-5. Add immutable hover identity
`(editor origin, document hash/generation, exact glyph/text range, modifiers)`.
Holding Ctrl over a lexically valid Java symbol submits at most one cancellable
availability/definition lookup per identity, caches the result, rejects stale
responses, underlines only the exact actionable range, and owns a scoped
link/pointer cursor until modifier, mouse, focus, screen, document, or result
state changes. It never queries or creates cursor objects from `render`.

Ctrl+left-click on a current actionable hover emits the same captured
`sfm:symbol/definition/open` action as F12. Resolve D-25's fallback explicitly
and separate click from drag. Add the `sfm:text_editor` Alt+F7 default for
`sfm:symbol/references/open`. Right-click captures the clicked document point
and opens the exact same B-5 constrained command-palette surface as Alt+Enter,
including definition, references, and other registered providers. Per closed
D-27, Alt-click remains unbound for references and retains EditorV3's existing
Alt+click multi-cursor behavior.

**Validation:** Test modifier press/release without mouse movement, movement
between symbols, unresolved/ambiguous/slow/stale results, cache reuse, document
mutation, focus/screen close, cursor restoration, click/drag threshold,
multiple cursors, F12/Ctrl+click parity, Alt+F7 direct invocation, right-click/
Alt+Enter identical offers/order, and absence of per-frame provider work.

**Completion criteria:** A Ctrl-held actionable symbol visibly behaves like a
link and Ctrl+click navigates through the registered definition action;
Alt+F7 opens persistent references; right-click and Alt+Enter are one familiar
contextual palette; no stale decoration, stuck cursor, duplicate parser, or
stolen Alt+click behavior remains.

**Completion evidence (2026-08-17):** Java commit `2c013aa66` adds cached
Ctrl-hover decoration/cursor ownership, Ctrl+click action parity, Alt+F7, and
one provider registry/constrained palette shared by Alt+Enter and right-click
while retaining Alt-click multi-cursor behavior. Commit `727bcef2c` corrected
hover identity to the planned editor/document/exact-symbol-range/modifier tuple,
so native and logical pointer movement within one identifier no longer cancels
and restarts the same cold lookup. Focused gesture/cursor/context tests passed;
SRC passed the real foreground native-pointer gate, visibly captured the exact
underline/hand affordance, and reported `contextual_offer_parity=true`.

### [x] C-10 Measure and fix EditorV3 large-document responsiveness

**Work:** Establish a deterministic benchmark/puppet opening the real
`platform/minecraft/src/main/java/ca/teamdman/sfml/ast/OutputStatement.java` at
declared viewport/GUI-scale variants. Instrument cold document projection,
glyph creation, remote/local style projection, per-frame glyph visits/draws,
selection/open-target geometry, cursor hit testing, context capture, allocations,
worker submissions, mouse/scroll/key event receipt, state application, and
next-visible-frame time. Keep raw before traces and identify the dominant
stages before changing behavior.

Then fix measured costs. The expected candidates—subject to evidence—are a
line/spatial index and visible-glyph range, immutable document projection keyed
by content/layout identity, range-based syntax style lookup rather than repeated
full glyph maps, cached selection/open-target geometry, and invalidation only
on text/font/layout/zoom/selection changes. Preserve exact rendering,
multi-cursor semantics, Arborium stale safety, open-at-range, zoom/pan, and
small-document behavior. Do not hide latency by dropping input or debouncing
away intermediate state.

**Validation:** Unit tests prove visibility boundaries, invalidation causes,
offscreen exclusion, exact style/selection parity, Unicode/CRLF, zoom/pan, and
bounded retained allocations. JFR/JMH or equivalent stage probes plus a live
input script compare before/after on the exact file against D-29, retaining raw
machine/profile/viewport identity. Full-source screenshots detect visual drift.

**Completion criteria:** The lag is computationally reproduced and attributed;
the measured dominant stage is fixed; warm interaction meets D-29 without
rendering/scanning all offscreen glyphs or rebuilding unchanged projections;
and visual/semantic parity plus raw before/after evidence are inspectable.

**Completion evidence (2026-08-17):** Java commit `2c013aa66` plus corrected
measurement commit `776c2c4f8` add indexed visible-glyph/document projections,
reusable style/selection state, invalidation tests, stage telemetry, and
dispatch across distinct render-post events. Retained `before.json` evidence
reproduces 4.277 s cold projection, 23.45 GB allocation, 22,294 glyph scans,
and 12,353 pointer scans; `after.json` records 14.559 ms cold, 3.59 MB, 2,860
visible glyph scans, and one pointer candidate. The first strict live trace then
exposed a separate 386 ms input-to-frame cost: repeatedly focusing an already
focused panel invalidated the layout and resized every visible EditorV3 each
tick. Commit `727bcef2c` makes same-panel focus idempotent. The final 30-sample
SRC trace records frame median 4.2492 ms/p95 5.4607 ms/max 5.5621 ms,
input-to-frame median 24.1469 ms/p95 33.8814 ms/max 34.9333 ms, and input apply
p95 40.5 microseconds with every budget met.

### [x] C-11 Prove the corrected source-navigation journey and reconcile plans

**Work:** Extend the source-editor live journey to open `OutputStatement.java`,
exercise Ctrl-hover/Ctrl+click and F12 on project/JDK/local/member/annotation/
import fixtures, run Alt+F7, retain the reference explorer while opening at
least three rows, use right-click and Alt+Enter, reveal the focused document in
an explorer, and prove no unexpected pane appears. Capture pointer/underline,
pane/stack/result identities, worker/index outcomes, event/frame performance,
and exact document/source spans as machine artifacts plus screenshots.

Run the separate explorer X-8b and window-manager Track 1b puppets when those
items complete; do not claim their icon/filter/scroll/border/divider outcomes
from this source-navigation puppet. Update changelog and all four coordinating
plans with exact commits/tests/artifacts. Propagation, publication, and release
tagging remain separately authorized.

**Completion criteria:** Every NAVHARD/HOVERDEF/CTXREF/REFS/REVEAL-3/EDITPERF
guidance id has pure, integration, and live evidence; the user can inspect one
stable reference list while navigating repeatedly; large-document interaction
has measured acceptable latency; and the plan accurately retains all deferred
decisions.

**Completion evidence (2026-08-17):** Java commit `2c013aa66`, acceptance
harness commits through `9a606c54a`, and stability commit `727bcef2c` complete
the integrated `title_screen_output_statement_source_navigation` journey. The
fresh declared run `sfm-title_screen-20260816-235458-728` passed with foreground
and actual Minecraft HWND both `2166102`, all nine definitions, 76 usages,
three retained-result row opens, contextual-offer parity, compatible-explorer
reuse, zero unexpected panes, and all 30 input/frame samples within budget.
Its final report is
`platform/minecraft/build/sfm-toolchain/artifacts/game-test-preview/runs/`
`sfm-title_screen-20260816-235458-728/`
`title_screen_output_statement_source_navigation/1280x720_auto/`
`artifact_output-statement-source-navigation.json`; the sibling
`artifact_output-statement-source-navigation-performance-checkpoint.json`
contains the corrected timing checkpoint. All five journey figures were
visually inspected. The separate X-8b and Track 1b Auto-plus-scales-1-through-8
matrices also passed and were visually inspected at
`title_screen_exp-20260816-185130-819` and
`sfm-title_screen-20260816-185618-484`. Earlier LockApp and external-pointer
failures remain useful negative guard evidence; neither was substituted for
the successful foreground-native run.

### Goal acceptance ledger — 35 exact requirements

This table is the non-lossy completion surface for the 2026-08-16 goal. The
evidence aliases are:

- **RUST:** strict `check-all.ps1`: 598 tests passed, 3 ignored, plus all 9
  adjacent Java-analysis scenarios.
- **JAVA:** canonical compile and full Java suite, focused provider/adapter/
  workspace/explorer/gesture/performance tests, and installed-worker
  integration.
- **WORKER:**
  `docs/task-evidence/source-navigation/symbol-server-installed-probe.json`
  schema 5: installed revision `4604d6b89`, 1,605 analyzed Java files, cold
  4032.028 ms, warm mixed median 82.760 ms/p95 147.506 ms/max 148.340 ms,
  accepted bounded memory, clean exit, zero descendants, and identical pre/post
  source digest.
- **SRC:** corrected declared
  `sfm:title_screen_output_statement_source_navigation` run
  `sfm-title_screen-20260816-235458-728`. The final and performance reports are
  under `title_screen_output_statement_source_navigation/1280x720_auto/` as
  `artifact_output-statement-source-navigation.json` and
  `artifact_output-statement-source-navigation-performance-checkpoint.json`.
  Actual and foreground Minecraft HWND both equal `2166102`; the run proves
  nine definitions, 76 usages, three retained-result opens, contextual parity,
  explorer reuse, no surprise panes, and 30/30 samples within frame/input
  budgets. All five figures were visually inspected.
- **EXP:** clean Auto plus GUI scales 1–8 explorer matrix
  `title_screen_exp-20260816-185130-819`, machine-complete and visually
  inspected.
- **DIV:** clean Auto plus GUI scales 1–8 divider matrix
  `sfm-title_screen-20260816-185618-484`, machine-complete and visually
  inspected.
- **AUDIT:** `sfm-propagate-changes.exe audit --branch 1.19.2` exited zero on
  2026-08-17 with the existing 37 unresolved-rule warnings in 2 tracked groups.

| Requirement | State | Exact evidence |
| --- | --- | --- |
| NAVHARD-1 | Complete | RUST and JAVA resolve implicit-`java.lang` platform types; SRC navigated `String`, `Object`, and `StringBuilder` through the registered definition path. |
| NAVHARD-2 | Complete | RUST/JAVA cover declarations and references; SRC navigated the declared local, field, method, annotation, and import fixtures. |
| NAVHARD-3 | Complete | Root-composition and partial-index regressions pass; every known SRC fixture navigated while unrelated incomplete usage coverage remained visible. |
| NAVPLACE-1 | Complete | `SFMJumpToDefinitionActionTests` prove exact-visible/current-pane-stack placement; SRC reports `no_unexpected_panes=true` and visually shows same-pane reuse. |
| HOVERDEF-1 | Complete | Hover state/cache tests plus `727bcef2c` prove stable exact-symbol identity and cursor lifecycle; SRC passed native/cached-pointer agreement and visibly captured underline/hand affordance. |
| HOVERDEF-2 | Complete | Panel tests and SRC prove Ctrl+click and F12 dispatch the same registered definition action with stale-target rejection. |
| CTXREF-1 | Complete | `SFMSymbolContextActionTests` and SRC `contextual_offer_parity=true` prove right-click and Alt+Enter share one ordered constrained palette. |
| CTXREF-2 | Complete | Controller/keybinding tests and SRC prove the text-editor Alt+F7 default directly opens persistent references. |
| CTXREF-3 | Complete | Multi-cursor fallback tests retain unbound Alt-click behavior; the complete SRC gesture journey reports no stolen interaction. |
| REFS-1 | Complete | Resolver/controller tests and SRC prove one immutable persistent reference identity, 76 deterministic usages, and visible incomplete-index diagnostics. |
| REFS-2 | Complete | SRC opened three source rows while retaining the same reference-result explorer identity. |
| REVEAL-3 | Complete | Coordinator/action tests and SRC prove exact-provenance, most-recent compatible explorer reuse without title guessing. |
| EDITPERF-1 | Complete | Retained traces reproduce 4.277 s/23.45 GB before and 14.559 ms/3.59 MB projection after; SRC additionally identified and fixed a 386 ms redundant same-focus resize path. |
| EDITPERF-2 | Complete | Visibility/invalidation tests prove bounded indexed work; final 30-sample SRC records frame p95 5.4607 ms, input-to-frame p95 33.8814 ms, and input-apply p95 40.5 microseconds with all budgets met. |
| JAVA-45 | Complete | RUST/JAVA plus SRC resolve and navigate all three branch-selected JDK/implicit-import fixtures. |
| JAVA-46 | Complete | RUST/JAVA plus SRC resolve and navigate local, field, method, annotation, and import fixtures. |
| JAVA-47 | Complete | Deepest-unique authorized-root tests preserve the exact failure regression; SRC maps the real document without guessing. |
| JAVA-48 | Complete | Target-domain completeness tests and SRC return known matches while truthfully exposing unrelated partial coverage instead of false `NoSymbol`. |
| JAVA-49 | Complete | RUST usage-at-position scenarios, JAVA provider consumption, and SRC Alt+F7 return 76 location-derived usages. |
| JAVA-50 | Complete | Typed deterministic target/span tests and SRC prove persistent ordering/diagnostics and three repeated source navigations. |
| JAVA-51 | Complete | WORKER proves warm mixed reuse/lifecycle and installed Java integration; SRC consumes the installed worker lane live. |
| JAVA-52 | Complete | WORKER pre/post digest is byte-identical at `sha256:57d489ab5b7be89e20f5e17b3e35a0b77ce95604bd696cb112062ed16c92d37a`; no mutation/refactoring command or analyzed-source mutation was introduced. |
| XEXP-20 | Complete | `SFMExplorerFilePresentationTests`, `SFMItemIconRendererTests`, and EXP prove cocoa-beans Java presentation, paper fallback, chest directories, and real registry ItemStacks. |
| XEXP-21 | Complete | `SFMClientActionPaletteSuggestionTests` and EXP prove the exact deep `view/set` completion frontier and visible list/small-icons behavior. |
| XEXP-22 | Complete | Palette tests and EXP prove independent name/relative/absolute path display composing with view mode. |
| XEXP-23 | Complete | `SFMExplorerPanelInteractionTests` and EXP prove exclusive location/filter/body focus, four-edge body chrome, and row-content inset at every declared scale. |
| XEXP-24 | Complete | Interaction tests and EXP prove each callback mutates immediately and ordered visible rows `1,2,3` end at row `3`, with event/model/frame traces. |
| XEXP-25 | Complete | `SFMExplorerFilterTests` and EXP prove fuzzy filtering over current lazy materialization, retained selection, incompleteness disclosure, unchanged relation revision, and zero resolver I/O. |
| XLAY-6 | Complete | `SFMWorkspaceDividerTests`, `SFMScreenMultiplexerDividerInteractionTests`, and DIV prove stable divider geometry, orthogonal intersection capture, constrained share mutation, and identity preservation. |
| WRESIZE-1 | Complete | DIV before/during/after geometry plus pure/host tests prove pointer dragging on every represented resizable border. |
| WRESIZE-2 | Complete | Host cursor-lifecycle tests and DIV prove horizontal/vertical affordances with cached owned cursor handles and deterministic reset. |
| WRESIZE-3 | Complete | T-junction/four-pane tests and DIV prove one intersection gesture captures both axes with the resize-all/crosshair affordance. |
| WRESIZE-4 | Complete | Pure layout tests and DIV prove minima, normalized shares, stable panel/stack/focus/content identities, and distinct committed bounds. |
| WRESIZE-5 | Complete | Registered resize-intent/action parity tests and DIV machine artifacts prove automation-addressable divider identities/deltas without screen-coordinate automation. |
| WRESIZE-6 | Complete | Local VS Code `sash.ts`, `splitview.ts`, and `gridview.ts` were used only as behavioral references; SFM's implementation is original and adds no VS Code code or dependency. |

Every row is `Complete`. Earlier secure-desktop and externally moved-pointer
failures remain negative guard evidence; the final unlocked foreground SRC run
passed the native-input gate rather than substituting cached or synthetic state.

## Most recently completed vertical slice — source presentation through definition navigation

The completed goal was:

> Complete C-4a, C-4b, C-4c, C-4d, C-5, and C-6 in
> `docs/tasks/contextual input actions and addressable explorer plan.md`
> together with Phase 0.11 in
> `docs/tasks/cli ast refactoring suite plan.md`. Deliver file-domain chest/
> paper ItemStack icons, contrast-backed EditorV3 read-only chrome, a cached
> Rust Arborium Java syntax service and stale-safe Java renderer integration,
> registered F12/Alt+Enter/palette jump-to-definition, and deterministic live
> explorer-to-highlighted-source-to-definition evidence. Stop before enabling
> non-Java Arborium grammars, writable host files, fuzzy-file Phase B,
> propagation, publication, or release tagging.

The observed end state is one explicitly seeded generic explorer showing
chest directories and paper files; opening `SFM.java` beside it shows a
contrast-backed `Read-only` status and visibly distinct Rust-supplied Java
formatting without a UI stall; F12 and Alt+Enter resolve the exact symbol,
opening one result at its source range or presenting stable choices for many;
machine artifacts prove hashes/spans/cache/generations/latency and screenshots
cover the declared GUI-scale matrix. Focused Rust/Java tests, `check-all.ps1`,
canonical compile/full Java tests, and both declared puppets passed. The exact
artifact ids, measured cold/warm behavior, and remaining cold parse bottleneck
are recorded in C-4a through C-6 rather than being deferred to a future goal.

## Overall completion criteria

- [ ] Every active guidance id has task-level and validation coverage.
- [ ] Every SFM-owned visible mutation control is keyboard reachable, narrated,
  situation-tagged, and semantically action-backed or explicitly classified.
- [ ] Contextual defaults do not leak into ordinary Minecraft/other-mod input,
  while user-created global mappings remain possible and visibly scoped.
- [ ] Panel close, scale, resize, duplicate, focus, and diagnostics actions have
  stable ids, captured-target safety, and documented defaults.
- [ ] Binding management is fully keyboard-operable, sortable, scrollable, and
  capable of editing composable chords without focus traps; the command palette
  can also inspect eligible actions from a captured hotkey without invoking it.
- [ ] Typed addresses round-trip, resolve only with declared context/device
  authority, and support contributed adapters for SFM and Vanilla objects.
- [ ] Action/Registry Explorer links agree with the underlying registries,
  bindings, situations, elements, and object relationships.
- [ ] File and item explorer surfaces open through the exact typed
  `sfm:panel/open` scene grammar; the three superseded developer action ids and
  their exclusive code are absent without aliases or migration.
- [ ] Generic explorers use the typed selection/relation foundation: explicit
  set-valued targeting, heterogeneous lazy roots, action-backed projection and
  root operations, direct `sfm explorer ...` control, and no provisional global
  workspace persistence.
- [x] The generic explorer header is a focusable semantic location control,
  opens a versioned location document in the preferred editor through shared
  panel placement, applies edits atomically with authority/revision checks, and
  leaves view/sort/group/hoist discoverable without redundant header prose.
- [x] Explorer Java files open read-only in Text Editor v3 with concrete
  document addresses, source hashes, independent editor state, safe preview
  ownership, and exact open-at-range behavior.
- [x] File-domain explorer rows use chest/paper ItemStack icons without
  overriding non-file presenters, and read-only EditorV3 panels expose the
  contrast-backed bottom-lane status without changing focus/hit behavior.
- [x] Concrete Java documents receive exact-current-text, Rust Arborium-derived
  ChatFormatting spans asynchronously with validated Unicode/hash/generation
  identity, cached grammar/query state, plain/existing fallback, and no
  render-thread or per-frame parsing.
- [x] The original C-5 fixture set routes F12 through the captured asynchronous
  Java-analysis/index action and distinguishes one/many/none/incomplete/stale
  outcomes without render-thread blocking; C-7 owns the subsequently observed
  symbol-kind/root-mapping/placement gaps.
- [ ] F12 resolves the declared JDK/project/local/member/annotation/import
  fixtures without the recorded false-negative diagnostics and opens unseen
  targets in the current pane stack rather than creating an implicit split.
- [ ] Find References is available from Alt+F7 and the shared Alt+Enter/right-
  click contextual palette, and produces a persistent generic result explorer
  that remains while several source ranges are opened.
- [ ] Ctrl-hover/Ctrl+click expose one stale-safe link affordance/action path;
  OS cursor state always restores and no per-frame worker work or unapproved
  Alt-click conflict exists.
- [ ] Focused documents can reveal their exact resolver path in a compatible
  explorer without title guessing or unrelated-panel replacement.
- [ ] The exact `OutputStatement.java` workload has reproducible before/after
  stage and frame evidence, meets the approved warm interaction budget, and
  retains rendering/navigation/multi-cursor/syntax semantics.
- [ ] Palette candidates can arrive incrementally with deterministic ranking,
  stable selection, cancellation, stale-generation rejection, visible
  loading/truncation/errors, and Brigadier-authoritative execution.
- [ ] Ctrl+Shift+N searches only bounded context/device roots, preserves
  independent explorer/editor origins, and safely opens/reveals the selected
  result without replacing unrelated or dirty panels.
- [ ] Ctrl+Shift+E resolves through contributed explorer capabilities with a
  documented ambiguity/fallback rule rather than a file-only type switch.
- [ ] Alt+Enter and any retained Ctrl+Space relationship use one registered
  contextual-action provider/palette path over whole-document, 2D context and
  canonical action drafts.
- [ ] Focused tests, canonical compile/full suite, machine assertions, live
  puppets, changelog, and plan completion notes agree.
- [ ] Later-version propagation is deliberate and version-adapted; publication,
  pushing, and unrelated repositories remain separately authorized.

## Risk register

| Risk | Guardrail |
| --- | --- |
| Contextual bindings double-fire or steal PTY/Minecraft input | D-2 precedence contract, one captured situation snapshot, focus-change reset, exact terminal pass-through/non-leak tests |
| Hotkey lookup accidentally invokes a matched action or leaves a modifier/focus capture active | K-8 uses a separate inspection-only mode, frozen context, explicit dispatch-suspension ownership, release/focus/screen cleanup tests, and a puppet action counter proving lookup caused no execution |
| Panel widgets imitate Vanilla visually but remain outside focus/narration | K-1 child-host contract plus keyboard-only and narration assertions; visual similarity alone is insufficient |
| Duplicate aliases a mutable terminal/editor lifecycle | D-1 typed re-open recipe, reject unsupported panels, independent identity/session/dirty-state tests |
| Default reseeding overrides user choices | Immutable built-ins plus schema-2 override/tombstone migration tests |
| Actionization explodes into one action per character/coordinate | D-3 semantic versus parameterized input boundary and inventory categories |
| Stable element ids become stale or retarget a different live control | Address includes host/context generation; resolver returns unavailable; captured-target tests |
| Address grammar becomes ambiguous once ids/paths contain slashes | D-5 fixture-first canonical parse/print and escaping before persistence |
| `sfm:path` escapes a logical device or grants ambient disk access | Explicit device registry, normalization/containment/symlink policy, read/write capability checks |
| Registry Explorer fabricates semantic relationships | Resolver-provided typed outlinks only, relationship truth tests, visible unavailable nodes |
| Static audit creates false confidence | Pair source guardrail with runtime registered-element inventory and explicit exemptions |
| Cross-version focus APIs drift | `@MCVersionDependentBehaviour` adapter seam and per-target propagation validation |
| Recursive fuzzy search freezes or visibly lags Minecraft | No filesystem walk on the render thread; bounded incremental batches, cancellation, first-result/completion telemetry, and a live responsiveness witness |
| Late results from an old query replace current suggestions | Existing revision plus provider generation/context identity checks on every Minecraft-thread batch application |
| Streaming arrival makes selection jump unpredictably | Stable candidate keys, deterministic timing-independent ranking, and selection retention by key |
| A path-looking title or logical explorer path grants host access | Explicit document/source address plus resolver-granted device capability; never infer authority from display text |
| Multiple panel paths collapse to whichever panel was inspected last | Immutable labelled origin collection; focus affects ranking only; same-display/different-device tests |
| Search silently starts at a disk/profile/process root | D-13 explicit contributed defaults, no-root status, containment tests, and telemetry that records device ids rather than private paths |
| Wildcard query is stored as one object's durable identity | Separate `SFMAddressQuery` type and concrete-only outlink/persistence validation |
| Context provider bypasses Brigadier with a callback | Offers contain canonical drafts only; completeness/availability/execution parity tests; remove direct `Runnable` route |
| Editor context loses spatial meaning or mishandles Unicode | Typed coordinate spaces, full immutable document snapshot, explicit conversion semantics, canvas whitespace/multi-cursor/Unicode fixtures |
| New defaults steal terminal or ordinary Minecraft input | Scope Ctrl+Shift+N/E and Alt+Enter to approved SFM situations, use the contextual matcher, and prove terminal/global non-leak |
| Candidate telemetry leaks sensitive host paths | Log provider/device ids and aggregate counts/timings by default; path text appears only in the user-visible palette or explicitly requested artifacts |
| Full cutover accidentally removes reusable picker/explorer behavior | Delete by reference/ownership audit: remove legacy registrations, enum cases, metadata, and no-op callback entry only; retain components with non-legacy callers and cover them in focused tests |
| Historical `sfm_source` handling silently opens a fixture or unsafe working directory | D-14 removes the public literal/device; explicit resolver-authorized source-root contribution, visible unavailable state, and tests reject fixture/process-directory substitution |
| New panel scenes accidentally regain one-off directional actions | One `SFMClientScreenType` grammar consumed automatically by all `panel/open[/direction]` actions; registry absence tests for bespoke explorer actions |
| Native folder dialog freezes the game, opens twice, or leaves focus/input stuck | D-15 injectable TinyFD adapter plus one modal coordinator, duplicate suppression, worker/platform lifecycle tests, Minecraft-executor result application, and manual focus witness |
| Native-dialog automation makes tests flaky or platform-specific | Fake picker, explicit-path action, and `Screen.onFilesDrop` are authoritative automated routes; OS dialog is a bounded manual witness only |
| Explorer roots grant more authority than the user selected | Resolver-issued typed paths/capabilities, canonical containment/symlink checks, no parent walking, no implicit drive/profile roots, and explicit remove/revoke behavior |
| Two explorers accidentally alias navigation/projection state | Independent explorer sessions over immutable/versioned selections and relations; duplicate/divergence tests |
| Location header prettifies away internal selection ids or truncation is mistaken for the canonical value | Render the exact canonical expression, permit visual ellipsis only, preserve byte-identical tooltip/narration/copy/editor content, and add narrow-panel fixtures |
| Saving an old or partially valid location document corrupts roots or broadens filesystem authority | Expected revision, whole-document parse/resolve/preflight, resolver-issued authority, one all-or-none publication, and conflict/retention tests |
| Removing the textual view toggle hides projection controls | Preserve registered view/sort/group/hoist actions in contextual, palette, and keybinding discovery with keyboard-parity tests |
| File navigation overwrites or reopens stale source unexpectedly | First slice is read-only; recipes retain address/hash/range, re-resolve capability, and show stale/unavailable diagnostics rather than writing or guessing |
| Bare token lookup jumps to a same-named symbol in the wrong package | D-18 source-location query includes document snapshot/import/source-set context; ambiguity is typed and user-selected, never first-match |
| Symbol lookup blocks Minecraft for the former multi-second CLI startup/query | D-19 long-lived supervised worker, render-thread prohibition, generation cancellation, cold/warm telemetry, and the installed 72.001 ms warm-median acceptance witness |
| Worker crash/cancel leaks subprocesses or applies a late definition | Owned process/job lifecycle, framed request ids/generations, kill/wait on shutdown/failure, stale-response rejection, and liveness tests |
| A definition target changes after analysis but before its panel loads | Preserve and verify the worker's algorithm-tagged exact content identity through the asynchronous resolver; never discard a production `blake3:` witness, and never apply a retained target range to failure/diagnostic text |
| An immediate Alt+Enter result is lost during the constrained-palette-to-workspace transition | Treat that exact captured transition as valid (or defer one client turn), then revalidate the captured editor origin, document hash, generation, and cursor before any navigation or feedback mutation |
| Dependency index is stale but no-match is presented as authoritative | Existing index identity/completeness contract is preserved in `DefinitionResult`; incomplete outcomes include typed refresh/retry actions |
| JDK `java.lang` symbols are treated as bare unresolved tokens | Branch-selected JDK source identity plus implicit-import semantics, exact `String`/`Object`/`StringBuilder` scenarios, and no authoritative no-match without platform-source completeness |
| A valid editor file is rejected because resolver authorization and worker roots use different but containing boundaries | Compose the exact resolver grant with negotiated canonical root mappings, choose the deepest unique contained source root, retain strict containment, and preserve both quoted failures as regression fixtures |
| F12/reference navigation silently grows a forest of split panes | Exact-visible reuse followed by captured originating-pane stack insertion; directional geometry only from explicit actions; topology artifacts assert visible pane count |
| Reference results disappear after the first jump | Versioned result entity plus ordinary generic explorer panel/owned preview semantics; repeated-activation tests retain result id, rows, completeness, and scroll state |
| Ctrl-hover spams the worker or leaves a stale underline/OS cursor | Immutable hover identity, one cancellable cached request per identity, generation/hash/focus checks, no render-time submission, and lifecycle cursor restoration |
| Right-click and Alt+Enter drift into separate action menus | Both capture context then open the same `SFMContextActionProvider` constrained palette; parity tests compare offers, order, execution, and incomplete drafts |
| Ctrl/Alt mouse gestures regress existing multi-cursor editing | D-25/D-27 explicitly own precedence; tests cover actionable/unavailable symbols, drag thresholds, Ctrl fallback, and unmodified Alt+click behavior |
| Large-document optimization guesses the wrong bottleneck or drops input | Preserve raw event-to-frame/stage/allocation traces first; optimize only measured costs; never debounce away intermediate input; retain visual and semantic parity fixtures |
| Source paths or text leak through telemetry | Default telemetry records provider/root ids, hashes, counts, durations, and outcomes only; raw paths/text require explicit user-visible artifact capture |
| Chest/paper fallback makes registry leaves look like files | D-20 file-scheme presenter and contributor-precedence tests; expandable/leaf alone is never treated as file identity |
| Read-only chrome covers or intercepts `#`/Done at small panel sizes | D-21 bounded free-lane geometry, non-widget rendering, overlap/hit/focus assertions, and GUI-scale visual proof |
| Syntax service freezes the game or reparses every frame | Dedicated supervised Rust worker, off-render-thread Java coordinator, immutable published snapshots, query counters, and explicit no-per-frame tests |
| Late highlighting colours a newer document or splits Unicode | Request origin/generation plus source hash, strict sorted UTF-8 boundary validation, scalar/CRLF conversion fixtures, and Minecraft-executor stale rejection |
| Adding Arborium highlight introduces a second native tree-sitter library | Pin `arborium-highlight = 2.18.1` without its `tree-sitter` feature and test Cargo resolution/check-all against the existing patched tree-sitter provider |
| A valid highlight result or JSON-expanded request exceeds the negotiated frame | Preflight encoded request/result sizes, fail only the affected request with a compact typed outcome, and regression-test the boundary without terminating a healthy worker |
| Rapid editor supersession frees Java capacity before Rust releases the cancelled slot | Account cancelled-but-remotely-in-flight requests until a terminal acknowledgement/result, queue the newest local generation within negotiated bounds, and prove repeated supersession cannot produce `syntax.server-busy` |
| Model-state assertions are mistaken for proof that icons/chrome/styles rendered | Machine artifacts label model-backed expectations honestly; mandatory screenshots/render evidence are inspected across the declared GUI-scale matrix before C-6 completes |
| Missing Rust tooling makes source unreadable | Highlighting is advisory; unsupported/missing/crashed workers retain plain/existing text and visible bounded diagnostics while editor/navigation stay usable |
| Language audit silently expands the release binary | C-4c enables Java only; later-language order is documentation/backlog, with dependency/features and artifact-size review required by a later goal |

## Source and implementation references

- `docs/AGENTS.md`
- `docs/tasks/typed selections relations and lazy explorers plan.md`
- `docs/tasks/release checkpoint and slim artifact plan.md`
- `docs/tasks/vox terminal bridge and graceful degradation plan.md`
- `docs/tasks/snapshot episodes and deterministic action environments plan.md`
- `docs/tasks/cli ast refactoring suite plan.md`
- `docs/tasks/sfm in-game control cli plan.md`
- `platform/minecraft/src/main/java/ca/teamdman/sfm/client/screen/workspace/SFMScreenPanel.java`
- `platform/minecraft/src/main/java/ca/teamdman/sfm/client/screen/workspace/SFMScreenMultiplexer.java`
- `platform/minecraft/src/main/java/ca/teamdman/sfm/client/screen/workspace/SFMWorkspaceLayout.java`
- `platform/minecraft/src/main/java/ca/teamdman/sfm/client/terminal/SFMTerminalPanel.java`
- `platform/minecraft/src/main/java/ca/teamdman/sfm/client/terminal/SFMTerminalPropertiesPanel.java`
- `platform/minecraft/src/main/java/ca/teamdman/sfm/client/symbol/SFMDefinitionContextAdapter.java`
- `platform/minecraft/src/main/java/ca/teamdman/sfm/client/symbol/SFMDefinitionNavigation.java`
- `platform/minecraft/src/main/java/ca/teamdman/sfm/client/symbol/SFMJumpToDefinitionController.java`
- `platform/minecraft/src/main/java/ca/teamdman/sfm/client/screen/SFMDrawCanvasScreen.java`
- `platform/minecraft/src/main/java/ca/teamdman/sfm/client/screen/SFMDrawCanvasRemoteSyntaxStyles.java`
- `platform/minecraft/src/main/java/ca/teamdman/sfml/ast/OutputStatement.java`
- `platform/minecraft/src/main/java/ca/teamdman/sfm/client/keybinding/`
- `platform/minecraft/src/main/java/ca/teamdman/sfm/client/action/SFMCommandPaletteActions.java`
- `platform/minecraft/src/main/java/ca/teamdman/sfm/client/action/SFMDeveloperActions.java`
- `platform/minecraft/src/main/java/ca/teamdman/sfm/client/action/OpenTitleScreenDevScreenAction.java`
- `platform/minecraft/src/main/java/ca/teamdman/sfm/client/action/SFMClientActionCommandTree.java`
- `platform/minecraft/src/main/java/ca/teamdman/sfm/client/action/SFMClientActionContext.java`
- `platform/minecraft/src/main/java/ca/teamdman/sfm/client/ProgramTokenContextActions.java`
- `platform/minecraft/src/main/java/ca/teamdman/sfm/client/screen/SFMCommandPaletteScreen.java`
- `platform/minecraft/src/main/java/ca/teamdman/sfm/client/screen/SFMTitleScreenDevScreen.java`
- `platform/minecraft/src/main/java/ca/teamdman/sfm/client/screen/SFMDrawCanvasModel.java`
- `platform/minecraft/src/main/java/ca/teamdman/sfm/client/screen/SFMDrawCanvasScreen.java`
- `platform/minecraft/src/main/java/ca/teamdman/sfm/client/screen/text_editor/SFMTextEditorPanel.java`
- `platform/minecraft/src/main/java/ca/teamdman/sfm/client/text_editor/SFMTextDocumentSource.java`
- `platform/minecraft/src/main/java/ca/teamdman/sfm/client/text_editor/SFMTextEditorPanelRecipe.java`
- `platform/minecraft/src/main/java/ca/teamdman/sfm/client/text_editor/SFMTextEditorPanelOpenContext.java`
- `platform/minecraft/src/main/java/ca/teamdman/sfm/client/keybinding/SFMKeyBindingDefaults.java`
- `platform/minecraft/src/main/java/ca/teamdman/sfm/client/registry/SFMClientActions.java`
- `platform/minecraft/src/main/java/ca/teamdman/sfm/client/registry/SFMClientScreenTypes.java`
- `platform/minecraft/src/main/java/ca/teamdman/sfm/client/screen/workspace/SFMClientScreenType.java`
- `platform/minecraft/src/main/java/ca/teamdman/sfm/client/screen/workspace/SFMWorkspaceScreenTypes.java`
- `platform/minecraft/src/main/java/ca/teamdman/sfm/client/screen/file_explorer/`
- `platform/minecraft/src/main/java/ca/teamdman/sfm/client/screen/explorer/SFMExplorerPresentation.java`
- `platform/minecraft/src/main/java/ca/teamdman/sfm/client/screen/explorer/SFMExplorerPresentationRegistry.java`
- `platform/minecraft/src/main/java/ca/teamdman/sfm/client/screen/explorer/SFMExplorerPanel.java`
- `platform/minecraft/src/main/java/ca/teamdman/sfm/client/theme/SFMClientTheme.java`
- `platform/minecraft/src/main/java/ca/teamdman/sfm/client/context/SFMContextTextCoordinates.java`
- `platform/minecraft/src/main/java/ca/teamdman/sfm/client/symbol/SFMSymbolServerSupervisor.java`
- `platform/cli/sfm-propagate-changes/src/java_analysis/symbol_server_protocol.rs`
- `platform/cli/sfm-propagate-changes/src/java_analysis/symbol_server_runtime.rs`
- `G:\Programming\Repos\arborium\AGENTS.md`
- `G:\Programming\Repos\arborium\crates\arborium-highlight\src\render.rs`
- `G:\Programming\Repos\arborium\langs\group-bark\java\def\arborium.yaml`
- cached authoritative crate sources for `arborium-java` and
  `arborium-highlight` 2.18.1 under `G:\Programming\Caches\CARGO_HOME`
- `platform/minecraft/build/downloadMCMeta/version.json`
- `platform/cli/sfm-propagate-changes/src/java_analysis/`
- `platform/cli/sfm-propagate-changes/src/cli/symbol/`
- `G:\Programming\Repos\microsoft-terminal\src\cascadia\TerminalSettingsModel\defaults.json`
