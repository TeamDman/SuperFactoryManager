# Puppet control surface and rich command arguments plan

**Plan status:** Draft; bookkeeping and design reconciliation in progress; no implementation goal is active
**Primary implementation root:** `D:\Repos\Minecraft\SFM\repos2\1.19.2`
**Primary implementation target:** Minecraft 1.19.2 development/game-puppet runtime
**Last updated:** 2026-08-23
**Intent audit:** Passed 2026-08-23 against the complete 2026-08-23 user message and follow-up clarification

## How to update this plan

- `[ ]` Not started
- `[~]` In progress
- `[x]` Complete
- `[!]` Blocked

Update a work item's heading and completion notes together. Record decisions,
commits, exact validation, artifacts, operational readiness, and intentional
exceptions beside the item they affect. Keep at most one integration focus;
parallel lanes must have disjoint write ownership. Do not set an implementation
goal merely because this draft exists: the user explicitly requested a separate
bookkeeping/planning review first.

## Purpose

Make SFM game puppets ordinary addressable developer objects that can be
discovered, inspected, configured, and run through the same explorer, command
palette, action, and contextual-choice surfaces they test. The first end-to-end
proof must repair the rejected command:

```pwsh
sfm-propagate-changes.exe puppet run sfm:title_screen_ordinary_document_history --branch 1.19.2 --variant 2000x2000@4 --wait-for-build-lock
```

and then make the same puppet discoverable under a generic registry explorer,
editable through a reusable rich viewport-argument presenter, and runnable from
the title-screen development client without inventing a dedicated puppet
configuration screen.

This plan coordinates but does not absorb the linked plans. The puppet matrix
plan owns process/artifact collection; typed explorers own path/relation/session
semantics; contextual input owns palette/context action routing; spatial
capabilities own generic provider selection and previews; the window manager
owns persistent UI scene/element presentation; the control CLI owns live-game
instance selection; and the log plan owns observation transport/presentation.

## Authoritative user guidance ledger

| ID | Active guidance | Required plan consequence | Superseded by |
| --- | --- | --- | --- |
| PCR-1 | `--variant 2000x2000@4` should be usable when a puppet is genuinely responsive; determine whether the current failure is global or puppet-specific. | PV-1 separates supported viewport constraints from recommended evidence variants, and PV-2 proves the exact reported command. | — |
| PCR-2 | Existing matrix support should continue running one puppet at several sizes/scales. | Recommended/declared matrix cells remain first-class and independent from the larger exact-override acceptance domain. | — |
| PCR-3 | Audit other puppets for the same profile problem rather than fixing only the reported definition. | PV-1 emits a complete profile/constraint inventory and classifies all definitions before widening claims. | — |
| PCR-4 | Puppets should prefer registered actions, command-palette invocation, stable element identities, and published geometry over brittle fixed coordinates. | PA-1 classifies every pointer/automation helper; PA-2 removes duplicated geometry and ordinal targeting where a semantic identity exists. Real pointer tests remain pointer tests. | — |
| PCR-5 | Explorer support should not stop at the Minecraft item registry; puppets should have an explorable registry location. | PE-1 introduces contributed registry-domain dispatch and `registry://sfm/puppet/`, with no second incompatible explorer implementation. | — |
| PCR-6 | A puppet entry should offer Run, Open Definition, and Reveal Definition in Explorer. | PE-2 contributes typed contextual offers whose availability reflects runtime/source capability. | — |
| PCR-7 | Filesystem explorer nodes need semantic “expand children,” “replace root with this,” and “replace root with parent” behavior; a `..` affordance may invoke the parent-root action rather than masquerade as a real child. | PE-3 adds explicit action-backed root/navigation operations and keeps display affordances separate from relation membership. | — |
| PCR-8 | Puppets should be runnable from the title-screen command palette, not only by choosing JVM startup properties through the Rust CLI. | PR-1 turns the one-shot static harness into a restartable, single-active-run session and registers a development-only run action. | — |
| PCR-9 | A command with all parameters present should still offer replacements for the parameter under the caret, preserving the other parameters. | CA-1 makes command/caret/selection an explicit completion context and asks Brigadier/providers at that cursor rather than always at end-of-input. | — |
| PCR-10 | Rich editing of `WIDTHxHEIGHT@SCALE` should use a reusable command-palette argument presenter, not a dedicated puppet settings screen or an infinite suggestion list. | CA-2 freezes typed argument descriptors/presenter providers; CA-3 proves width, height, GUI scale, and aspect preview in a bounded palette region. | — |
| PCR-11 | Rich argument presenters and candidate states must remain keyboard accessible and communicate disabled/enabled state with text as well as colour/style. | CA-2/CA-3 require normal focus/narration, action-backed mutation, textual state labels, responsive layout, and pure state artifacts. | — |
| PCR-12 | Alternative implementations should generalize beyond the existing preferred text editor: manager-open, text-edit, command-palette, and later third-party Vim-like providers should be selectable through intent/capability resolution. | Routed to spatial capability CP-1/CP-1a; this plan consumes that direction but does not implement the general provider system in the puppet core. | — |
| PCR-13 | Opening a manager should support policies such as resume the prior manager session versus open its main screen, without an alternative provider bypassing authority. | Routed to spatial capability CP-1a and the manager-screen permission gate; provider choice and state-resume recipe are distinct. | — |
| PCR-14 | A future cloud-like manager console should aggregate several managers, performance/lag evidence, and recent resource movement. | Routed to the in-game control CLI's future typed manager/observation phase; excluded from the puppet core. | — |
| PCR-15 | A future `sfm` CLI/editor save flow should be able to update a manager program, with an explicit permission model once operation is no longer limited to looking at or being near the manager. | Routed to the control CLI's server-authoritative manager-operation gate; no generic client action may bypass player/server permission checks. | — |
| PCR-16 | Shared Java/Rust logs should be consumable from an in-game terminal attached to the running launch/session; a bespoke log panel may be lower priority. Multiple Rust processes and game instances must not be conflated. | Routed to log-plan LOG-X6. The observation stream remains presentation-independent; terminal follow/attach is prioritized while a specialized panel remains an optional consumer. | — |
| PCR-17 | Useful features such as document history need a discoverable UI affordance, but the UI should remain minimal and user-customizable. | Routed to contextual/window UIE-1/UIE-2: one history action element is the proof for generic element visibility preferences. | — |
| PCR-18 | Live UI elements need stable identities so the palette can enumerate visible/hidden elements and invoke hide/show actions; candidate styling cannot rely on colour alone. | Routed to contextual/window UIE-1/UIE-2 and reused by CA-2 presenter accessibility. | — |
| PCR-19 | The next unattended batch should minimize regret, have observable in-game outcomes, and use an elastic continuation ladder rather than treating a ten-hour estimate as acceptance. | A whole-plan dependency audit compares this candidate with the release-review path. The global comment plan's RCS-0 through RCS-8 plus real-lane RCS-S2/RCS-S1 domain and resumability proof is now the recommended next batch; this puppet candidate retains its own core/ladder for later or explicit reprioritization. | — |
| PCR-20 | Do not set the goal yet; remain in bookkeeping and planning until the user reviews the proposal. | This plan remains Draft and no goal tool is called during this phase. | — |

## Guidance traceability

| Guidance | Plan coverage | Evidence when complete |
| --- | --- | --- |
| PCR-1 through PCR-3 | PV-1, PV-2 | Constraint/matrix inventory, pure resolver tests, and successful exact reported command |
| PCR-4 | PA-1, PA-2 | Machine-readable helper audit plus identity/geometry-driven interaction tests |
| PCR-5 through PCR-7 | PE-1 through PE-3 | Generic registry-domain tests, live puppet explorer, contextual offers, and root/parent behavior |
| PCR-8 | PR-1, PR-2 | Restartable-session tests and a title-screen Run journey returning to a usable title screen |
| PCR-9 through PCR-11 | CA-1 through CA-3 | Mid-command replacement fixtures, presenter host tests, viewport preview artifact, and live keyboard/mouse proof |
| PCR-12, PCR-13 | Spatial capability CP-1/CP-1a | Generic provider/preference/ask/resume tests; not required for the puppet core |
| PCR-14, PCR-15 | Control CLI manager extension | Typed permission/capability and server-authority tests; deferred |
| PCR-16 | Log-plan LOG-X6 | Exact run/game/observation-session attach and terminal-follow proof; deferred from core |
| PCR-17, PCR-18 | UIE-1/UIE-2; stretch PS-2 | Addressable history affordance and visible/hidden candidate proof |
| PCR-19, PCR-20 | Cross-plan priority note, candidate-goal/operational-readiness sections, and global-comment RCS batch | User approval precedes goal creation; the recommended review core and later puppet candidate each have explicit observable checkpoints and stretch ladders |

## Intent audit evidence

- **Pass 1 — extraction:** Reread the complete 2026-08-23 message and its
  explicit no-goal-yet clarification. PCR-1 through PCR-20 retain the reported
  command, matrix/profile question, coordinate concern, registry/explorer and
  root actions, title-screen invocation, caret-local replacement, rich viewport
  widget example, general intent/provider direction, manager aggregate and
  permission concerns, terminal-log alternative, minimal/discoverable UI, and
  ten-hour planning constraint.
- **Pass 2 — traceability:** Mapped the puppet/palette/explorer requirements to
  PV/CA/PE/PR/PA tasks and routed adjacent ownership explicitly to CP, manager
  control, logging, and UI-element tasks. A later whole-plan audit routed the
  next unattended recommendation to global-comment RCS-0 through RCS-8 plus
  the real-lane RCS-S2/RCS-S1 domain and resumability proof because
  it is the direct release-review dependency; the puppet core remains fully
  specified without being mislabeled as highest priority.
- **Pass 3 — adversarial omission:** Rechecked the distinctions between a
  recommended matrix and accepted exact domain; semantic action invocation and
  legitimate derived pointer testing; selecting a command argument and
  inventing a dedicated screen; current visible elements and persistent
  preferences; provider choice and resume policy; client UI substitution and
  server authority; terminal presentation and observation ownership; and a
  ten-hour estimate versus completion. No goal was set.
- **Known source limitation:** None for this request. The complete source
  message remains available in the conversation. Broader historical intent is
  consumed through the linked plans' existing ledgers rather than reconstructed
  from memory.

## Verified foundation

- `TitleScreenOrdinaryDocumentHistoryGamePuppet` declares
  `FIXED_1280X720_AUTO`; `SFMGamePuppetViewportSelection.resolve` accepts an
  exact variant only when its dimensions occur in
  `SFMGamePuppetViewportProfile.acceptedExactSizes()`. This is the direct cause
  of the reported rejection.
- `SFMGamePuppetViewportController` already resizes the GLFW window and changes
  Minecraft GUI scale after a variant is authorized. The missing concept is a
  truthful support predicate, not basic resizing machinery.
- The 2026-08-23 audit found 43 definitions: 22 `CURRENT`, 2
  `COMMON_RESPONSIVE`, 11 `FIXED_1280X720_AUTO`, 7 `GUI_SCALE_MATRIX`, and 1
  `TERMINAL_PRESENTATION`. The suite contains no literal absolute
  `mouseClicked(123, 456)` calls; most interactions use actions, keys, widget
  bounds, glyph layout, or divider geometry. Remaining fragility includes
  reconstructed terminal-dropdown geometry and explorer/panel ordinal clicks.
- The production resolver registry currently dispatches only by URI scheme and
  permits one resolver per scheme. `registry` is occupied by the item resolver,
  so another registry domain requires composite/subdomain dispatch rather than
  registering a second `registry` resolver. Production currently exposes only
  `registry://minecraft/item/`; generic registry shapes exist in tests.
- The Java harness is selected by Rust-provided startup properties and uses
  static one-shot `initialized`/`completed` state. There is no registered
  puppet-run client action.
- `SFMCommandPaletteScreen.refreshSuggestions` parses the entire input and does
  not pass the edit-box caret to completion generation. `SFMPaletteCandidate`
  already preserves Brigadier replacement ranges, but the planned streaming
  candidate source, active argument descriptor, generic capability registry,
  and preview host do not yet exist in production source.
- The typed explorer, action-element, contextual-offer, selection, workspace,
  local `sfm.exe` control, and ordinary document-history foundations are already
  present and should be reused.

## Confirmed constraints and working decisions

- A viewport profile's **recommended evidence variants** and **accepted exact
  constraint** are separate values. `declared` expands recommendations;
  `preferred` chooses one recommendation; an exact selection is accepted by the
  constraint even when it is not a declared matrix cell.
- Do not label every existing puppet responsive. Existing behavior remains
  conservative until PA-1/PV-1 classifies it; unsupported exact requests return
  the failed constraint and recommended variants in an actionable diagnostic.
- Real pointer behavior remains necessary for hit-testing, dragging, text
  selection, and focus proofs. Such coordinates derive from the addressed live
  element/path/glyph/divider bounds. State-only automation seams cannot be used
  as proof of natural pointer routing.
- The working canonical puppet root is `registry://sfm/puppet/`; entries use
  `registry://sfm/puppet/<namespace>/<path>`. This is a domain contribution to
  one composite registry resolver, not a new scheme or special explorer.
- Puppet catalog entries are development-only capabilities. They do not become
  release-jar player functionality merely because the generic explorer and
  presenter contracts live in main client code.
- The working command is
  `sfm action invoke sfm:puppet/run <puppet-selector> <viewport-selection>`.
  The viewport defaults to the puppet's preferred variant when omitted. An
  unavailable development runtime returns a reason rather than exposing a dead
  action.
- In-process runs are single-active-run, restartable sessions. Interactive
  origin returns to a stable title screen and does not stop Minecraft;
  bootstrap CLI origin retains existing completion/exit/artifact semantics.
- Argument descriptors and presenters have stable owner-qualified ids.
  Presenters edit only the active Brigadier replacement range, preserve suffix
  arguments, never execute implicitly, and are optional: the command remains
  fully editable as canonical text.
- The viewport presenter exposes direct numeric width/height fields, bounded
  step/slider affordances, Auto or numeric GUI scale, and an aspect-ratio box.
  It never enumerates every integer dimension as suggestions.
- The dependency posture for the proposed goal is frozen. No propagation,
  release, publication, push, or cross-version merge is included.

## Proposed design decisions for the eventual goal

These are the default decisions attached to approval of the eventual goal; they
do not require another round of user input unless the user wishes to change
one. Implementation may revise one only when direct source/runtime evidence
falsifies it, and must then update this plan with the evidence before proceeding
through the affected integration point.

| Gate | Decision | Adopted default | Acceptance consequence |
| --- | --- | --- | --- |
| PG-1 Development runtime | How a title-screen client loads puppet definitions without including them in a release jar | Add `sfm-propagate-changes.exe puppet browse --branch 1.19.2`, a development-control launch mode using the existing game-puppet source set and generated static catalog but no startup selection | Ordinary player builds remain unchanged; live proof starts from that copyable CLI command |
| PG-2 Catalog authority | How runtime definitions gain source URI/line and viewport metadata | Merge reflection-discovered executable definitions with the Rust static source catalog by canonical puppet id; mismatch is diagnostic and Run requires an executable definition | Open/Reveal use truthful source paths; stale catalog cannot execute the wrong class |
| PG-3 Responsive bounds | What arbitrary dimensions/scales a responsive definition may accept | Versioned constraint with minimum/maximum physical width/height, GUI-scale policy, and optional logical-size predicate; recommendations remain separate | `2000x2000@4` passes only when the definition's declared predicate accepts it |
| PG-4 Context action host | How right-click on explorer rows presents offers | Reuse the existing captured contextual-action constrained palette, with selected path and explorer identity as immutable origin | No bespoke popup/menu implementation and no action retarget after selection changes |
| PG-5 Root parent semantics | Whether `..` is relation content | Keep `..` as an optional presentation/action affordance; canonical parent/root replacement is a typed action and the provider's actual child relation stays truthful | Paging/filter/selection never mistake `..` for a filesystem child |
| PG-6 Rich presenter placement | Where the argument UI appears at narrow/wide layouts | One bounded optional presenter region beside suggestions when wide and below them when narrow; text input and Cancel/Execute remain reachable | GUI-scale matrix and narration prove no clipped or keyboard-inaccessible controls |

## Execution order

```text
PV-1 contract/audit -----> PV-2 exact CLI proof ------------------------+
       |                                                               |
       +--> PA-1 interaction inventory --> PA-2 semantic repairs        |
                                                                       +--> PR-2 live journey
CA-1 cursor context --> CA-2 presenter host --> CA-3 viewport editor ---+
                                                                       |
PE-1 registry domains --> PE-2 puppet entries/actions --> PE-3 roots ---+
                                                                       |
PR-1 restartable harness -----------------------------------------------+
```

## Phase PV — Separate viewport capability from evidence matrices

### [ ] PV-1 Freeze viewport constraints and audit every puppet

**Work:** Replace the enum's `acceptedExactSizes == requestedSizes` assumption
with a versioned constraint evaluated independently from declared/preferred
variants. Inventory all definitions and classify profile, recommended variants,
minimum logical/physical needs, GUI-scale policy, and coordinate/geometry
dependencies. Do not broaden a definition merely because it compiled.

**Validation:** Pure tests cover exact accepted/rejected boundaries, Auto and
numeric scales, malformed/overflow dimensions, current-size-only definitions,
matrix expansion, diagnostic suggestions, and unchanged declared/preferred
results. A deterministic inventory artifact accounts for every discovered
puppet exactly once.

**Completion criteria:** Every puppet has a truthful support constraint and a
separate finite evidence recommendation; no exact request is rejected solely
because it was absent from the evidence matrix.

### [ ] PV-2 Make the reported exact run pass without weakening other definitions

**Work:** Classify ordinary-document-history as responsive only after its
layout/assertion contracts pass at the requested variant. Preserve its normal
fast preferred run. Improve exact rejection text for constrained puppets.

**Validation:** Run the exact reported command and the existing preferred run;
inspect PNG and structured bounds/history artifacts from both. Add a rejected
fixed/current-profile control.

**Completion criteria:** `2000x2000@4` completes with zero puppet failures and
viewport restoration, while an honestly unsupported puppet still fails before
mutation with an actionable constraint diagnostic.

## Phase CA — Cursor-aware rich command arguments

### [ ] CA-1 Make the caret-selected argument the completion frontier

**Work:** Introduce immutable palette edit context containing canonical input,
caret, selection, active Brigadier node/argument descriptor, replacement range,
and suffix. Use Brigadier's cursor-aware completion path and adapt fuzzy/history
sources so replacing parameter two of four preserves parameters three/four.
Keep execution based on the complete command.

**Validation:** Fixtures cover start/middle/end caret, selected text, quoted and
canonical tokens, literal versus argument boundaries, Unicode, parse errors,
no-op completions, undo/redo, and stale asynchronous generations.

**Completion criteria:** Moving the caret into any describable argument changes
the suggestions to that argument, and accepting one replacement mutates only
its exact range.

### [ ] CA-2 Add an optional typed argument-presenter host

**Work:** Register stable argument descriptors and bounded presenter providers.
The palette allocates a responsive clipped region and owns focus, narration,
lifecycle, cancellation, and render-state isolation. Presenter edits dispatch
one typed draft-replacement intent through the same input-history path. Text
editing remains the universal fallback.

**Validation:** Provider precedence/failure/unload, missing presenter fallback,
focus traversal, Escape/Cancel, screen resize/GUI scale, clipping, stale range,
suffix preservation, undo/redo, and colour-independent state labels.

**Completion criteria:** One argument type can contribute an interactive editor
without adding a bespoke screen or making the canonical command uneditable.

### [ ] CA-3 Prove the host with a viewport-selection presenter

**Work:** Add width, height, and GUI-scale controls plus aspect-ratio preview.
Values are constrained by the selected puppet when known. Direct text edits and
presenter edits round-trip the same canonical `WIDTHxHEIGHT@auto|N` token.

**Validation:** Pure geometry/value tests and a live puppet capture wide,
narrow, keyboard-only, pointer, invalid value, Auto, numeric scale, and a
four-argument command where the viewport is not final.

**Completion criteria:** The user can configure `2000x2000@4` visually, see its
shape and labels, and change it later without disturbing sibling arguments.

## Phase PE — Expose puppets through the generic explorer

### [ ] PE-1 Generalize registry resolver dispatch and add the puppet root

**Work:** Replace one-resolver-per-`registry` assumptions with deterministic
domain contributions keyed by canonical authority/kind prefix. Adapt the item
resolver without changing its paths. Add a lazy bounded puppet catalog at
`registry://sfm/puppet/` with ids, labels, runtime availability, viewport
recommendations/constraints, source location, and status diagnostics.

**Validation:** Item and puppet domains coexist; duplicate/overlapping domain,
unknown domain, paging, invalidation, stale generation, mixed roots, and
third-party contribution tests pass.

**Completion criteria:** One explorer can contain item and puppet roots, and
adding another registry domain no longer requires changing a scheme singleton.

### [ ] PE-2 Add Run, Open Definition, and Reveal Definition offers

**Work:** Contribute actions from captured puppet-entry context. Run opens the
ordinary palette with the exact puppet id and viewport argument; Open Definition
uses the configured editor; Reveal uses the existing explorer reveal/preview
rules. Source-less or runtime-less entries explain unavailability.

**Validation:** Context capture/staleness, availability, exact source URI/hash,
palette draft, no implicit execution, preferred-editor selection, and reveal
target tests. Live right-click uses the constrained palette and survives a
selection change behind it without retargeting.

**Completion criteria:** Every puppet entry truthfully offers the operations its
current capabilities support, through ordinary actions rather than row-specific
callbacks.

### [ ] PE-3 Add action-backed root/parent navigation

**Work:** Register replace-root-with-selected, replace-root-with-parent,
open-selected-as-root-in-new-explorer, and explicit expand/refresh actions.
Optionally render `..` as a presentation affordance that invokes the parent
action; do not insert it into provider child membership.

**Validation:** Filesystem root/drive/UNC, registry root, selection-backed
multi-root, authorization, exact/multi explorer selectors, paging/filtering,
focus, and no underlying filesystem mutation.

**Completion criteria:** A definition file or directory can become the current
root or reveal its parent using self-contained semantic actions.

## Phase PR — Run a puppet from the title-screen development client

### [ ] PR-1 Refactor the harness into restartable run sessions

**Work:** Separate process/bootstrap configuration from `PuppetRunSession`.
Queue at most one active run on the client thread; reject overlap; restore
viewport/options/world/title state on success, failure, cancellation, and
screen closure. Interactive sessions do not auto-exit Minecraft. Preserve
existing bootstrap CLI completion markers and artifact ownership.

**Validation:** Repeated runs, concurrent rejection, cancel/failure, title/world
origin, viewport restore, option restore, bootstrap auto-exit, interactive
no-exit, stale tick/callback, and source-set/release isolation tests.

**Completion criteria:** The same client can run, finish, and run another puppet
without stale static state, and existing CLI automation remains compatible.

### [ ] PR-2 Register the development run action and complete the natural journey

**Work:** Add the development-only action and source-catalog suggestions. Launch
the explicit browse/control mode, open the puppet explorer, right-click ordinary
history, configure `2000x2000@4`, run it, inspect completion, and return to the
title-screen workspace. Capture action, session, viewport, artifact, and source
identities.

**Validation:** One self-orchestrating puppet or equivalent harness drives the
real palette/explorer/action paths with observation pauses and writes PNG plus
structured catalog/context/presenter/run-session artifacts.

**Completion criteria:** A user can naturally discover and run the reported
puppet at the reported exact variant without restarting the client or invoking
the Rust CLI for that individual run.

## Phase PA — Remove remaining brittle automation seams

### [ ] PA-1 Publish a machine-readable puppet interaction audit

**Work:** Classify every helper as semantic action, real keyboard/character,
identity-derived pointer, geometry-derived pointer, state-only automation, or
legacy ordinal/duplicated geometry. Record which evidence claims each category
may support.

**Validation:** Static/audit tests account for all helpers and fail when a new
unclassified helper appears.

**Completion criteria:** “Responsive puppet” is an evidence-backed property,
not an inference from the absence of obvious numeric literals.

### [ ] PA-2 Replace the bounded known fragile helpers

**Work:** Publish terminal dropdown/action-element bounds and address explorer
rows/panels by stable path/selector. Keep ordinal APIs only in tests explicitly
about ordering, and keep pointer coordinates derived from the resolved live
bounds.

**Validation:** Existing terminal/explorer/panel puppets pass at preferred plus
at least one non-default accepted variant, with natural input-consumption
artifacts.

**Completion criteria:** The known duplicated dropdown geometry and incidental
visible-row/panel ordinal targeting are gone from ordinary interaction proofs.

## Candidate puppet-control goal — ready for later review; do not set yet

**Cross-plan priority reconciliation — 2026-08-23:** This is a coherent and
observable puppet/developer-UX batch, but it is not currently the recommended
next overnight goal for the release objective. A whole-plan dependency audit
found that RCS-0 through RCS-8 plus RCS-S2/RCS-S1 in the global comment/review plan more directly
unlocks durable semantic comments, migration, AST-aware review surfaces, and
eventual release approval. The work below remains ready as a later goal or an
explicit user-prioritized detour; none of its tasks is falsely marked complete
or discarded.

### Required core

Complete PV-1, PV-2, CA-1, CA-2, CA-3, PE-1, PE-2, PR-1, and PR-2. The core
ends only when both of these observable journeys work:

1. the user's exact `sfm-propagate-changes.exe puppet run ... --variant
   2000x2000@4` command succeeds and restores the viewport; and
2. a development-control client opens `registry://sfm/puppet/`, presents the
   selected puppet's Run/Open/Reveal contextual offers, edits the viewport in
   the generic palette presenter, runs it in-process, and returns to a usable
   title screen with inspectable artifacts.

### Ordered elastic continuation ladder

1. **PS-1 — PE-3 root/parent navigation:** add and prove the explorer root
   operations, then checkpoint.
2. **PS-2 — addressable history affordance:** implement the linked UIE-1/UIE-2
   slice with a hideable EditorV3 history button and visible/hidden palette
   candidates, then checkpoint.
3. **PS-3 — PA-1/PA-2 puppet interaction hardening:** publish the complete
   audit and remove the known fragile helpers, then checkpoint.
4. **PS-4 — generic capability-provider proof:** complete the smallest linked
   CP-1 foundation using text-edit plus one bounded second intent; do not begin
   manager mutation or remote permissions, then checkpoint.

Only one stretch item may be claimed at a time after the core is complete and
committed. Unstarted stretch items do not invalidate the core.

### Explicitly preserved outside the required core

- PE-3's root/parent actions are first in the continuation ladder rather than
  silently omitted from the explorer request.
- UIE-1/UIE-2 preserve the optional history affordance and generic shown/hidden
  element controls; PA-1/PA-2 preserve the complete interaction audit and
  brittle-helper repairs.
- CP-1a preserves generic text-editor/manager/palette provider selection and
  manager resume-versus-main policy. I-6/I-7 preserve the multi-manager console,
  external editor Save, and server-authoritative permissions. LOG-X6 preserves
  exact terminal/shell attachment to the combined Java/Rust observation stream.
- Those larger systems are deliberately not smuggled into the puppet core.
  Their owner-plan task contracts are now detailed enough to resume later, and
  PS-4 proves only the smallest generic provider foundation if time remains.

## Parallel topology for an approved goal

- **Track A — viewport and harness:** PV-1/PV-2 then PR-1; owns puppet profile,
  selection, harness/session, and associated tests.
- **Track B — palette argument surface:** CA-1 through CA-3; owns palette edit
  context, descriptor/presenter host, viewport presenter, and focused tests.
- **Track C — explorer catalog:** PE-1/PE-2; owns registry-domain composition,
  puppet resolver/presenter/context offers, and focused tests.
- **Integration owner:** owns shared action/screen registration, generated
  static catalog schema, PR-2 live journey, plans/changelog, CLI installation,
  and final verification. Shared schemas freeze before Tracks A/C integrate.

Tracks may implement pure contracts in parallel, but PR-2 begins only after
CA-3, PE-2, and PR-1 pass. No two agents edit central registration or this plan.

## Operational readiness for the proposed goal

- **Target branch/commit:** 1.19.2; exact starting commit recorded at goal start.
- **Tooling or generated runtime inputs changed:** expected yes if the Rust
  static catalog/CLI browse mode changes; conclusive `check-all.ps1` and
  `install.ps1` run after the final relevant edit.
- **User must run install script:** no at successful completion; the goal owner
  installs and verifies PATH freshness/hash.
- **Dependency posture:** frozen. Dependency declarations and checked-in
  lockfiles remain unchanged; no new Cargo/SFM/Gradle dependency and no new
  repository clone. Deterministic lockfile-pinned cache rehydration is allowed.
- **Process lifecycle authority:** active through the repository goal-readiness
  guide for proven SFM/Minecraft/toolchain processes only.
- **Propagation/push/release:** excluded.
- **Exact manual test commands:** the reported exact command plus the final
  development-control browse command chosen at PG-1; both recorded copyably in
  the completion handoff.
- **Expected artifacts:** preview index/PNGs plus versioned viewport inventory,
  palette edit/presenter, puppet catalog/context, and run-session JSON.
- **Autonomous continuation:** required core, then PS-1 through PS-4 in order.

## Risks and guardrails

| Risk | Guardrail |
| --- | --- |
| Arbitrary exact sizes turn every puppet into an untested responsive claim | Separate constraints/recommendations, complete inventory, conservative defaults, rejected control |
| Matrix behavior regresses while exact override is broadened | Preserve declared/preferred semantics and existing contact-sheet keys; dedicated regression fixtures |
| Rich presenter becomes a puppet-only settings screen | Stable argument-descriptor/provider registry, text fallback, second generic fixture, no direct puppet dependency in host |
| Mid-command completion deletes later arguments | Exact caret-local replacement range plus suffix/undo fixtures |
| Another registry requires special-casing the explorer | Composite registry-domain resolver with item-registry regression and third-party fixture |
| In-process puppet corrupts static state or exits the user's game | Restartable session object, single-active-run gate, origin-specific exit policy, finally-style restoration |
| Development-only classes leak into the release jar | Explicit source-set capability, ordinary release artifact audit, unavailable action outside dev mode |
| Open Definition points at stale source | Rust catalog/runtime id join plus canonical URI/hash and visible mismatch diagnostics |
| Context menu retargets after selection changes | Immutable captured origin/path/explorer generation and constrained-palette stale checks |
| Generic provider or UI-visibility work expands the core indefinitely | Route PCR-12 through PCR-18 to owner plans; only the named PS item may enter after core checkpoint |

## Overall completion criteria

- [ ] Every active PCR requirement has a concrete owner, task, validation, or
  explicit deferred disposition.
- [ ] Viewport support and evidence recommendations are distinct and truthful
  for every puppet.
- [ ] The exact reported CLI run succeeds without weakening fixed controls.
- [ ] Caret-local completion and a generic rich argument host work without
  losing canonical text or suffix arguments.
- [ ] Puppets appear in the generic explorer with truthful Run/Open/Reveal
  offers.
- [ ] A title-screen development client runs the selected puppet in-process and
  returns to a usable state.
- [ ] Relevant Java/Rust tests, compile, live artifacts, changelog, tool
  freshness, dependency posture, process state, and manual handoff agree.
- [ ] No goal is created until the user approves the reviewed boundary.
