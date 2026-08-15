# Contextual input, action ownership, and addressable explorer plan

**Plan status:** Active; B-2/C-4 and CLI-AST Phase 0.10 are complete; C-4a through C-6 are prepared as the next coherent goal
**Primary implementation root:** `D:\Repos\Minecraft\SFM\repos2\1.19.2`
**Coordinating release plan:** `docs/tasks/release checkpoint and slim artifact plan.md`
**Selection/explorer foundation plan:** `docs/tasks/typed selections relations and lazy explorers plan.md`
**Last updated:** 2026-08-15
**Intent audit:** Passed 2026-08-15 including the explorer-icon, read-only-status, Arborium-highlighting, and C-5 goal extension recorded below

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
| KBIND-7 | Scale changes should communicate state without permanently consuming panel space: show fading `gui scale N` or `gui scale auto (N)` toasts, and repeat the toast with a slight shake when increase/decrease is already at a numeric boundary. | Release P-2.6 owns the workspace toast lifecycle; K-7 proves action, fade, effective-auto text, and boundary feedback. | — |
| KUI-1 | `sfm:keybindings/manage` needs sortable Name and Binding Count headers. | K-6 adds keyboard-focusable headers, ascending/descending state, stable tie breaks, and filter/scroll preservation. | — |
| KUI-2 | Binding entry needs a focusable capture mechanism that records the entered mapping. | K-6 introduces a dedicated capture widget integrated with normal focus and dispatch suspension. | — |
| KUI-3 | Triple Escape should back out of capture; each captured chord element is a keyboard-focusable button that removes that element when activated. | K-6 defines the time-bounded cancel sequence, removable stroke chips, Save/Cancel focus targets, and mouse/keyboard parity. | — |
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
| PLAN-3 | Preserve the complete path from folder authorization through opening SFM code and jumping to a definition as executable vertical slices. | Selection/explorer X-1 through X-7, C-3, and C-4 are complete; gates D-15 through D-19, remaining C-5/C-6, topology, proofs, risks, and revised next-goal definition are authoritative. Superseded C-1/C-2 remain provenance only. | — |

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
| PLAN-4 | Update the resumable plan with current progress and every atom above, then set a goal that completes them together with C-5. | This ledger, traceability, three-pass audit, D-20 through D-24, C-4a through C-6, CLI-AST Phase 0.11, topology, completion criteria, and risks define that goal. | — |

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
- Persisted multi-root SFM workspaces, explicit root add/remove/reorder actions,
  native TinyFD folder selection, typed-path and drag/drop alternatives, and
  workspace-root availability diagnostics.
- File-backed Text Editor v3 documents with durable addresses, safe read-only
  opening, open/focus-at-range, and source-hash-aware snapshots.
- Location-aware Java definition lookup over workspace and dependency sources,
  exposed as asynchronous registered actions and contextual offers.
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
sfm:path/open <concrete-path-address>
sfm:symbol/definition/open
```

Every root action contains an explorer selector. Widgets emit exact ids;
interactive keyboard actions may explicitly spell `focused`; none falls back
to an implicit default. `root/pick` is the later native-dialog adapter,
`root/add` is deterministic, and a folder drop submits the same exact-selector
intent. Contextual definition providers capture the focused editor
document/cursor into an immutable context value before producing the final
action; execution does not reread an unrelated later focus state.

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

### [ ] B-1 Add cancellable streamed palette candidates without replacing Brigadier

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

### [ ] B-4 Generalize reveal-in-explorer and bind Ctrl+Shift+E

**Work:** Close D-10 and the explorer vocabulary in D-11. Register semantic
explorer focus/open and reveal actions. Resolve the captured focused address or
explicit address/query to a resolver-contributed compatible explorer/reveal
intent. Reuse/focus an appropriate existing explorer or open one through the
established panel placement rules; preserve source origin and selected item.
Add one approved contextual Ctrl+Shift+E default and ambiguity choices through
the constrained palette. Prove both a path/file-explorer adapter and an
item/item-explorer (or deterministic item fixture) adapter; do not centralize a
file/item type switch.

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

### [ ] B-5 Replace token callbacks with Alt+Enter contextual action offers

**Work:** Close D-9 and the contextual-action portion of D-11. Register the
semantic contextual-actions action and Alt+Enter default. Add the
`SFMContextActionProvider` registry and open the existing constrained palette
with every applicable offer. Adapt `ProgramTokenContextActions` behavior to
whole-document/2D-context providers that emit canonical drafts for resource
identifier expansion, label/input/output/bool/if inspection, preserving all
applicable offers rather than first-match only. Add the mandatory path
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

**Completion criteria:** Alt+Enter presents the familiar searchable palette
constrained to all valid contextual action drafts; context includes the whole
document and true 2D point; missing arguments remain Brigadier/palette-owned;
and Ctrl+Space no longer owns a separate one-to-one callback system.

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
font-render-rule warnings. No F12/Alt+Enter result navigation or live UI proof
was introduced; those remain C-5/C-6.

### [ ] C-4a Replace file explorer text markers with ItemStack icons

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

### [ ] C-4b Add explicit contrast-backed read-only EditorV3 chrome

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

### [ ] C-4c Add the Rust Arborium Java highlighting contract and service

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

### [ ] C-4d Apply current Rust Java spans in EditorV3 asynchronously

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

### [ ] C-5 Register jump-to-definition and integrate result navigation

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

### [ ] C-6 Prove the SFM-source jump-to-definition journey live

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

## Most recently completed vertical slice

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
implementation evidence. It stopped one boundary before any F12/keybinding/
palette result-navigation behavior, so C-5 can consume a measured,
deterministic provider rather than mixing UI design with analysis and process-
lifecycle work. The prepared next goal combines the newly added C-4a through
C-4d source-presentation/highlighting work with C-5 navigation and C-6 live
proof so the user can inspect one coherent explorer-to-highlighted-source-to-
definition journey. The independent fuzzy-file chain remains available after
B-2 and is not part of that goal.

## Prepared next goal — source presentation through definition navigation

Set the next goal as:

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

The observable end state is one explicitly seeded generic explorer showing
chest directories and paper files; opening `SFM.java` beside it shows a
contrast-backed `Read-only` status and visibly distinct Rust-supplied Java
formatting without a UI stall; F12 and Alt+Enter resolve the exact symbol,
opening one result at its source range or presenting stable choices for many;
machine artifacts prove hashes/spans/cache/generations/latency and screenshots
cover the declared GUI-scale matrix. Focused Rust/Java tests, `check-all.ps1`,
canonical compile/full Java tests, and both declared puppets pass.

## Overall completion criteria

- [ ] Every active guidance id has task-level and validation coverage.
- [ ] Every SFM-owned visible mutation control is keyboard reachable, narrated,
  situation-tagged, and semantically action-backed or explicitly classified.
- [ ] Contextual defaults do not leak into ordinary Minecraft/other-mod input,
  while user-created global mappings remain possible and visibly scoped.
- [ ] Panel close, scale, resize, duplicate, focus, and diagnostics actions have
  stable ids, captured-target safety, and documented defaults.
- [ ] Binding management is fully keyboard-operable, sortable, scrollable, and
  capable of editing composable chords without focus traps.
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
- [ ] File-domain explorer rows use chest/paper ItemStack icons without
  overriding non-file presenters, and read-only EditorV3 panels expose the
  contrast-backed bottom-lane status without changing focus/hit behavior.
- [ ] Concrete Java documents receive exact-current-text, Rust Arborium-derived
  ChatFormatting spans asynchronously with validated Unicode/hash/generation
  identity, cached grammar/query state, plain/existing fallback, and no
  render-thread or per-frame parsing.
- [ ] F12 and the contextual-action provider resolve the symbol at the captured
  document location through the existing Java-analysis/index engine, never
  block the render thread, and correctly handle one/many/none/incomplete/stale
  outcomes.
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
| Dependency index is stale but no-match is presented as authoritative | Existing index identity/completeness contract is preserved in `DefinitionResult`; incomplete outcomes include typed refresh/retry actions |
| Source paths or text leak through telemetry | Default telemetry records provider/root ids, hashes, counts, durations, and outcomes only; raw paths/text require explicit user-visible artifact capture |
| Chest/paper fallback makes registry leaves look like files | D-20 file-scheme presenter and contributor-precedence tests; expandable/leaf alone is never treated as file identity |
| Read-only chrome covers or intercepts `#`/Done at small panel sizes | D-21 bounded free-lane geometry, non-widget rendering, overlap/hit/focus assertions, and GUI-scale visual proof |
| Syntax service freezes the game or reparses every frame | Dedicated supervised Rust worker, off-render-thread Java coordinator, immutable published snapshots, query counters, and explicit no-per-frame tests |
| Late highlighting colours a newer document or splits Unicode | Request origin/generation plus source hash, strict sorted UTF-8 boundary validation, scalar/CRLF conversion fixtures, and Minecraft-executor stale rejection |
| Adding Arborium highlight introduces a second native tree-sitter library | Pin `arborium-highlight = 2.18.1` without its `tree-sitter` feature and test Cargo resolution/check-all against the existing patched tree-sitter provider |
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
