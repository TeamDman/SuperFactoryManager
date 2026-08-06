# Contextual input, action ownership, and addressable explorer plan

**Plan status:** Active
**Primary implementation root:** `D:\Repos\Minecraft\SFM\repos2\1.19.2`
**Coordinating release plan:** `docs/tasks/release checkpoint and slim artifact plan.md`
**Last updated:** 2026-08-05
**Intent audit:** Passed 2026-08-05 against the complete 2026-08-05 user message

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

The immediate user-visible correction is the Rust terminal panel: its
disconnected Start/Retry control, Presentation selector, and terminal
properties controls are hand-rendered hit regions today and are not one
coherent Minecraft widget/focus tree. The architecture must fix that specific
problem without adding another terminal-only focus mechanism.

## Authoritative user guidance ledger — 2026-08-05

| ID | Active guidance | Required plan consequence | Superseded by |
| --- | --- | --- | --- |
| WNAV-1 | The disconnected Rust-terminal Start/Retry control and Presentation dropdown must behave like Vanilla Minecraft widgets and be keyboard navigable. | K-1 introduces a panel child-widget host; K-2 migrates both controls and proves click, Tab, Shift+Tab, Enter/Space, focus visibility, and narration. | — |
| WNAV-2 | Terminal-properties controls currently bypass normal Minecraft Tab navigation; panels/screens must either support the native mechanism or adapt/pump it seamlessly. | K-1 owns a version-adapted focus/event bridge; K-2 converts every tuning control rather than special-casing terminal properties keys. | — |
| WNAV-3 | Prefer an automated static and/or runtime way to detect SFM screen elements that lack keyboard-navigation consideration. | K-5 defines an enforceable SFM-owned action-element inventory, runtime assertions, and a bounded static audit; unsupported detection claims must be documented honestly. | — |
| KCTX-1 | Introduce a registry of keyboard-focus/usage situations with stable identifiers users can select when relating hotkeys to actions. | K-3 adds `Registry<SFMKeyboardUsageSituation>`, contextual binding storage, active-situation resolution, precedence, and conflict rules. | — |
| KCTX-2 | A mixed panel may expose a terminal input widget as `sfm:terminal` while ordinary controls use `sfm:default`; the screen, panel, and LWJGL/Forge hooks must agree on one hierarchy. | K-1 exposes focused-child identity; K-3 resolves an ordered active situation ancestry before matching and then forwards unconsumed input to the child. | — |
| KCTX-3 | Distinguish safe defaults active only in an SFM-owned context from arbitrarily user-added global mappings that may conflict with Minecraft or other mods. | K-3 has explicit contextual versus global situations, displays scope, and ships no new conflict-prone global default merely to imitate another application. | — |
| KBIND-1 | Add a default binding for `sfm action invoke sfm:panel/close`. | K-3/K-4 provide a stable overridable default; the working default is Ctrl+Shift+W in the SFM workspace context, matching Microsoft Terminal close-pane. | — |
| KBIND-2 | Add panel-resize and panel-duplicate actions using the Microsoft Terminal hotkey defaults. | K-4 adds hierarchical actions, pure layout behavior, and defaults from the pinned local Terminal `defaults.json`: Alt+Shift+arrows resize; Alt+Shift+minus duplicates below; Alt+Shift+plus duplicates right. | — |
| KBIND-3 | Preserve the existing generic `KeyOrChord -> SomeActionString` model; when the action string still has required arguments, open the command palette for completion rather than failing or inventing arguments. | K-3 migrates the existing dispatch behavior and tests contextual incomplete drafts. | — |
| KBIND-4 | The terminal F3 behavior must appear in SFM Shortcuts as a real binding/action. | K-3 registers the diagnostic-choice behavior as a semantic action and seeds F3 contextually; K-5 removes the hard-coded multiplexer-only path. | — |
| KUI-1 | `sfm:keybindings/manage` needs sortable Name and Binding Count headers. | K-6 adds keyboard-focusable headers, ascending/descending state, stable tie breaks, and filter/scroll preservation. | — |
| KUI-2 | Binding entry needs a focusable capture mechanism that records the entered mapping. | K-6 introduces a dedicated capture widget integrated with normal focus and dispatch suspension. | — |
| KUI-3 | Triple Escape should back out of capture; each captured chord element is a keyboard-focusable button that removes that element when activated. | K-6 defines the time-bounded cancel sequence, removable stroke chips, Save/Cancel focus targets, and mouse/keyboard parity. | — |
| ACT-1 | Keyboard-drivable SFM behavior, including clickable/focusable buttons, should be backed by registered actions instead of mutating otherwise unreachable state directly. | K-2 converts terminal controls; K-5 inventories and migrates semantic SFM behaviors in bounded waves while retaining intrinsic text/pointer input as parameterized input actions. | — |
| ACT-2 | Public shortcut/action exploration must not be polluted by opaque `sfm:screen/mouse/click <x> <y>` bindings. Stable element identity such as `sfm:button/edit_manager_disk` may bridge a live control to its semantic action. | K-5 introduces action-element contributions with stable addresses and canonical action drafts; coordinate clicks remain automation input, not the user-facing semantic contract. | — |
| ACT-3 | A Manager Edit control should expose the semantic relationship between the live element, a contextual action such as `sfm:manager/disk/edit <manager-pos>`, and any bindings. | K-5 uses one Manager-screen action element as the non-panel proof that dynamic context and semantic action ownership work. Exact final id is a contract gate. | — |
| EXPL-1 | The current shortcuts surface is conceptually an Action Explorer: it should enumerate all known `KeyOrChord` responders and provide navigable links to owners, actions, and mappings. | A-4 projects actions, situations, bindings, and action elements into the existing explorer/panel language rather than maintaining a disconnected list forever. | — |
| EXPL-2 | Panels show properties of one object; explorers show many objects. A Registry Explorer should list known registries and expose type-specific outlinks, such as client action -> bindings or item -> corresponding blocks. | A-3/A-4 define generic explorer nodes plus resolver-provided properties/actions/outlinks. | — |
| ADDR-1 | Navigation requires addresses with resolver/protocol identity; introduce a registry of address resolvers and preserve the proposed fixtures `sfm:registry/minecraft/item/minecraft/stick` and `sfm:registry/sfm/client_actions/sfm/developer/open_text_editor` while finalizing an unambiguous grammar. | A-1 freezes a typed `SFMAddress` and `Registry<SFMAddressResolver<?, ?>>`; A-2 adds registry-entry resolution. | — |
| ADDR-2 | Preserve proposed forms such as `sfm:path/options.txt` and `sfm:path/c/tmp/a.txt`, but resolve them relative to an explicit device, which may be an SFM VFS, the current instance run directory, or another bounded contributed device. | A-1/A-2 separate logical addresses from device context, enforce containment, and never infer ambient filesystem authority from an address string. | — |
| ADDR-3 | SFM may assign addresses/adapters to existing Minecraft behavior it did not author; there is no orphan-rule restriction, and resolver context should bootstrap useful access to the environment in which it runs. | A-1 makes resolvers contributor-extensible and context-capability based; A-2 proves one Vanilla registry adapter without modifying Vanilla classes. | — |
| PANEL-OLD-1 | Earlier guidance chose move instead of duplicate and the release plan consequently prohibited duplicate-panel actions. | Preserve `sfm:panel/move/...`; mark the old no-duplicate prohibition superseded and add deliberate duplication only after D-1 closes its state semantics. | KBIND-2 |
| PLAN-1 | Atomize the message into achievable, uniquely addressable, durable work items using the resumable-implementation-plans protocol, and identify vertical slices. | This ledger, traceability table, contract gates, work items, validation, topology, and next-slice definition are authoritative. | — |

## Guidance traceability

| Guidance | Plan coverage | Evidence when complete |
| --- | --- | --- |
| WNAV-1, WNAV-2 | K-1, K-2, K-7 | Widget-host tests, terminal/property focus-path tests, and a live keyboard-only terminal puppet |
| WNAV-3 | K-5, K-7 | Static inventory output plus runtime actionable-element/focus audit with explicit exemptions |
| KCTX-1, KCTX-2, KCTX-3 | K-3, K-6, K-7 | Registry/bootstrap tests, schema migration, precedence/conflict tests, UI scope display, and terminal non-leak proof |
| KBIND-1, KBIND-2, KBIND-3, KBIND-4 | K-3, K-4, K-7 | Default/tombstone/action/layout tests, `[?]` and management captures, incomplete-draft palette proof, and live pane operations |
| KUI-1, KUI-2, KUI-3 | K-6, K-7 | Sort/filter/scroll tests and keyboard/mouse capture-chip puppet evidence |
| ACT-1, ACT-2, ACT-3 | K-2, K-5, K-7 | Action-element inventory, semantic invocation parity, Manager Edit fixture, and absence of public coordinate-click actions |
| EXPL-1, EXPL-2 | A-3, A-4, A-5 | Resolver-backed action/registry explorer, typed outlink tests, and live navigation captures |
| ADDR-1, ADDR-2, ADDR-3 | A-1, A-2, A-5 | Parse/print, context, contributor, containment, unavailable-context, registry, path-device, and Vanilla adapter tests |
| PANEL-OLD-1 | D-1, K-4, release-plan supersession note | Recorded duplication semantics plus independent-state and move-preservation tests |
| PLAN-1 | Entire plan | Three-pass intent audit and a fresh-agent resumption review |

## Intent audit evidence

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
- Converting every Minecraft/Forge registry and every Vanilla screen in the
  first resolver/explorer slice.
- Propagation, publication, release metadata changes, Teamy Studio, or Cloud
  Terrastodon work.

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

## Design gates

| Gate | Decision required | Working recommendation | Acceptance consequence |
| --- | --- | --- | --- |
| D-1 Duplicate state semantics | Does duplicate create an independent panel from a re-open recipe, alias the same live object, or vary by panel type? | Independent panel from a typed scene/address recipe. Terminal duplicates create a distinct session; explorers/editors share immutable source/address inputs but own independent focus/scroll/dirty state. Panels without a recipe report unavailable. | K-4 tests lifecycle independence, terminal session identity, source sharing, unsupported panels, stacks, and close behavior. This gate blocks duplicate implementation, not K-1/K-3. |
| D-2 Situation ancestry and precedence | How do focused element, panel, workspace, and optional global situations compose when bindings overlap or a sequence is partial? | Ordered deepest-to-broadest ancestry; the most-specific complete binding wins, equal-specificity conflicts are diagnosed and do not double-fire, and a reserved partial sequence consumes only while it remains viable. | K-3 tests terminal pass-through, local/global conflict separation, sequence timeout/focus loss, and one invocation per event. |
| D-3 Semantic action boundary | Must every editor/navigation impulse be a registry entry, or only semantic operations with parameterized low-level input beneath them? | Register semantic operations and stable parameterized action kinds; do not register every character or coordinate. All visible buttons and non-text shortcuts still expose a semantic action contribution. | K-5 audit has explicit categories and exemptions instead of either thousands of actions or silent hard-coded behavior. |
| D-4 Action Explorer naming | Rename the current surface immediately, or introduce the addressable explorer first and migrate the old title later? | Use “SFM Actions & Shortcuts” during K phases; introduce `sfm:registry_explorer`/action projection in A-4, then retire or redirect redundant presentation only with live parity. | K-6 and A-4 user-facing names, compatibility, and migration tests differ. |
| D-5 Address text grammar | Freeze the exact serialized spelling for resolver id, registry id, entry id, device id, path segments, escaping, and fragments. | First define typed components and fixture round trips. Preserve `sfm:registry/minecraft/item/minecraft/stick`, `sfm:registry/sfm/client_actions/sfm/developer/open_text_editor`, `sfm:path/options.txt`, and `sfm:path/c/tmp/a.txt` as proposed-intent fixtures, but do not rely on ambiguous unescaped slash concatenation. | A-1 cannot expose persistent links/history until canonical parse/print fixtures are approved. This does not block K phases. |
| D-6 Release boundary | Which K/A items are required before the candidate release? | K-1 through K-7 are release-correctness work. A-1 freezes addresses before public owner ids become durable; A-2 through A-5 may follow as feature graduation unless the Action Explorer is declared release-blocking. | The coordinating release plan must record the approved cutoff before final release acceptance. |

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
| SFM workspace/default | Ctrl++ / keypad add | `sfm action invoke sfm:panel/scale/increase` | Existing release P-5.2 |
| SFM workspace/default | Ctrl+- / keypad subtract | `sfm action invoke sfm:panel/scale/decrease` | Existing release P-5.2 |
| SFM workspace/default | Ctrl+0 / keypad zero | `sfm action invoke sfm:panel/scale/clear` | Existing release P-5.2 |
| SFM workspace/default | F3 | `sfm action invoke sfm:panel/diagnostics/open` | Existing workspace behavior made discoverable |

The physical plus binding must accept the platform's Shift+= representation
without conflating it with an extra logical modifier. Storage/display tests
must cover main-row and keypad forms.

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

## Execution order and parallel topology

```text
K-1 panel widget host ---------------------> K-2 terminal/properties migration --+
                                                                                 |
K-3 situation registry/storage/defaults --> K-4 panel topology actions ----------+--> K-7 live join
             |                                  ^                                |
             +------------------------------> K-6 binding-management UX ---------+

K-5 inventory/action ownership starts read-only after K-1 contract,
then integrates semantic controls after K-2/K-3.

A-1 typed addresses can proceed beside late K work once D-3/D-5 close.
A-2 resolvers -> A-3 generic explorer projection -> A-4 Action/Registry Explorer -> A-5 live join.
```

Safe parallel work after contract review:

- one owner may implement the pure situation/storage model while another owns
  the panel child-widget host;
- pure layout resize/duplicate recipe tests can proceed independently after
  D-1, without touching the widget host or binding UI;
- address parse/print fixtures can proceed independently after D-5;
- one integration owner must serialize changes to `SFMScreenMultiplexer`,
  `SFMKeyBindingService`, action registration, canonical plans, changelog, and
  live puppet definitions.

## Phase K — Keyboard, widgets, actions, and panel operations

### [ ] K-1 Introduce the panel child-widget and focus host

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

### [ ] K-2 Migrate terminal and terminal-properties controls to action-backed widgets

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

### [ ] K-3 Register keyboard-usage situations and migrate binding storage/defaults

**Work:** Close D-2. Add the contributor registry, active-context snapshot,
context-aware engine, schema-1 to schema-2 migration, explicit global scope,
built-in default definitions, overrides/tombstones, situation-aware conflict
detection, and display metadata. Preserve incomplete command-draft palette
opening. Seed the existing panel scale defaults, panel close, and F3 diagnostic
action only in the approved SFM-owned context. Register
`sfm:panel/diagnostics/open` and remove the multiplexer-only F3 mutation path.

**Validation:** Add situation registry, ancestry, precedence, overlap conflict,
focus-change reset, terminal pass-through, storage migration, corrupt-file,
default/override/tombstone, main/keypad parity, F3 discovery, close action, and
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

### [ ] K-4 Add resize and independent duplicate panel actions

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

### [ ] K-5 Actionize SFM-owned controls and enforce keyboard reachability

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

### [ ] K-6 Upgrade binding management with sorting and composable capture

**Work:** Close D-4's K-phase naming. Add focusable Name and Binding Count
headers, stable ascending/descending sorting, enabled/total counts, situation
scope display/filtering, and filter/viewport preservation. Replace the ad hoc
recording boolean with a focusable capture widget. Each captured stroke is a
button/chip removable by click or keyboard activation. Triple Escape within a
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

### [ ] K-7 Prove the contextual input vertical slice live

**Work:** Extend `title_screen_dynamic_key_bindings`, `title_screen_workspace`,
and the Rust terminal presentation puppet with machine assertions and text
artifacts. Cover keyboard-only disconnected terminal controls, properties,
F3 discoverability, close, scale, resize, duplicate, situation display,
incomplete command completion, sorting, capture chips, triple-Escape cancel,
Manager semantic action ownership, and terminal non-leak. Update
`changelog.sfml` and both coordinating plans.

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

## Phase A — Typed addresses and explorer projections

### [ ] A-1 Freeze typed addresses, resolver context, and canonical spelling

**Work:** Close D-5. Define `SFMAddress`, typed resolver payloads, parse/print,
versioning, escaping, equality, unavailable/diagnostic resolution, contextual
capabilities, contributor registry, and bounded property/action/outlink results.
Specify which forms are persistent/public and which are ephemeral live-element
addresses. Bootstrap context from current client/screen/workspace/panel without
granting authority that the resolver did not declare.

**Validation:** Fixture round trips cover proposed registry/path examples,
namespaced ids containing nested paths, Unicode, escaping, malformed/unknown
resolver ids, missing context, stale panels/elements, and contributor entries.

```pwsh
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMAddressTests --wait-for-build-lock
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMAddressResolverRegistryTests --wait-for-build-lock
```

**Completion criteria:** Durable links have one unambiguous canonical form and
resolvers can describe requirements/outlinks without ambient mutation access.

### [ ] A-2 Add registry-entry and bounded path-device resolvers

**Work:** Resolve the SFM client-action registry, one Vanilla item fixture, and
explicit registry entries. Add a path-device registry with read-only fixture/VFS
and contained instance-run-directory adapters. Normalize and reject traversal,
drive/UNC injection, unsupported mutation, stale devices, and oversized output.
Demonstrate the no-orphan adapter by addressing Vanilla data without modifying
its classes.

**Validation:** Test registry/entry existence, action metadata/outlinks,
item-to-block relationship truth, contributor namespaces, device selection,
root containment, symlink policy, unavailable contexts, read bounds, and
read-only enforcement.

```pwsh
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMRegistryAddressResolverTests --wait-for-build-lock
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMPathAddressResolverTests --wait-for-build-lock
```

**Completion criteria:** The example registry and path families resolve real
bounded objects through explicit context/device authority and fail safely.

### [ ] A-3 Generalize explorer nodes and object-properties panels around addresses

**Work:** Adapt the existing explorer/panel abstractions so a node can carry an
address, title, kind, children, properties, actions, and typed outlinks without
pretending every object is a file. Preserve existing file/review explorer
behavior through adapters. Add a single-object properties panel that resolves
one address and a generic registry-root explorer scene.

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

## Next recommended vertical slice

The first implementation goal should complete **K-1 and K-2**. Its observable
endpoint is narrow but end-to-end: Start/Retry, Presentation, terminal viewport,
and all terminal-properties controls share one Minecraft-like focus tree; Tab
and Shift+Tab work; click selects the same focus owner; Enter/Space invokes the
same registered terminal/server/presentation/tuning actions; and triple-Tab and
triple-Escape retain their terminal semantics. K-3 is the next complete slice;
it introduces contextual matching/default persistence and makes F3 a
discoverable `sfm:panel/diagnostics/open` action.

In parallel, an independent worker may implement the pure situation/storage
model from K-3 without integrating global event routing, while another may
write K-4 layout fixtures after D-1 closes. The integration owner retains
`SFMScreenMultiplexer`, terminal panels, action registration, plans, changelog,
and puppets.

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

## Source and implementation references

- `docs/AGENTS.md`
- `docs/tasks/release checkpoint and slim artifact plan.md`
- `docs/tasks/vox terminal bridge and graceful degradation plan.md`
- `docs/tasks/snapshot episodes and deterministic action environments plan.md`
- `platform/minecraft/src/main/java/ca/teamdman/sfm/client/screen/workspace/SFMScreenPanel.java`
- `platform/minecraft/src/main/java/ca/teamdman/sfm/client/screen/workspace/SFMScreenMultiplexer.java`
- `platform/minecraft/src/main/java/ca/teamdman/sfm/client/screen/workspace/SFMWorkspaceLayout.java`
- `platform/minecraft/src/main/java/ca/teamdman/sfm/client/terminal/SFMTerminalPanel.java`
- `platform/minecraft/src/main/java/ca/teamdman/sfm/client/terminal/SFMTerminalPropertiesPanel.java`
- `platform/minecraft/src/main/java/ca/teamdman/sfm/client/keybinding/`
- `platform/minecraft/src/main/java/ca/teamdman/sfm/client/action/SFMCommandPaletteActions.java`
- `platform/minecraft/src/main/java/ca/teamdman/sfm/client/registry/SFMClientActions.java`
- `platform/minecraft/src/main/java/ca/teamdman/sfm/client/screen/file_explorer/`
- `G:\Programming\Repos\microsoft-terminal\src\cascadia\TerminalSettingsModel\defaults.json`
