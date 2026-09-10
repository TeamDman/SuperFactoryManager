# Explorer find, selection, compact hierarchy, and review freshness

**Plan status:** Verified handoff — core plus EF-7/EF-9 complete; EF-10 remains scheduled
**Primary implementation root:** `D:/Repos/Minecraft/SFM/repos2/1.19.2`
**Last updated:** 2026-09-06
**Intent audit:** Passed 2026-09-06; includes the subsequent fuzzy/ranking correction, shared options/highlights, pointer selection and commit-versus-disk request
**Current implementation focus:** None. EF-1–EF-9, EF-11/EF-12 and EF-A have verified checkpoints. EF-10 remains unclaimed for the next feedback iteration.

## How to update this plan

`[ ]` not started; `[~]` active; `[x]` verified complete; `[!]` blocked with
exact evidence and an unblocking condition. Keep one implementation task active.
Put decisions, affected contracts, actual commands/results and exceptions under
the task they prove. A completed test of an earlier goal does not prove this
follow-up. No implementation is claimed by writing this plan.

This is the successor to the completed
[overnight readiness goal](release%20review%20overnight%20readiness%20plan.md).
It refines, rather than replaces, the authoritative
[typed selection/explorer plan](typed%20selections%20relations%20and%20lazy%20explorers%20plan.md),
[contextual input plan](contextual%20input%20actions%20and%20addressable%20explorer%20plan.md),
[editor plan](draw%20editor%20document%20regions%20and%20commands%20plan.md), and
[comment/review plan](global%20comment%20selection%20and%20review%20sessions%20plan.md).
In particular, X-9 remains the shared selection adapter, not a second selection
engine invented here. Existing immutable review/approval meaning stays intact.

## Purpose and proposed checkpoints

The concrete journey is: find `ExploreReviewInteractivelyPuppetAction.java`,
understand which source universe is being searched, reach the intended file
without expanding every package segment, and attach review evidence to the
intended version of its contents. The user has confirmed fuzzy results and
reranking work appropriately: preserve them as a selectable mode, not a bug to
remove. Literal mode must distinguish absence; a stale review must not masquerade
as today's checkout.

Authorized goal core: EF-1–EF-6, EF-8, EF-11/EF-12 and EF-A. Deliver shared
single-line editing; Find/Filter with independent shared option suites and
highlight toggles; Explorer multi-selection; exact revision presentation and
freshness; and a usable commit-before versus captured-working-tree-after review
including untracked code. Preserve old Git/Git reviews and their evidence.
This deliberately includes the uncommitted collaboration loop, not only search
polish. No arbitrary time estimate constitutes completion.

Execution: EF-1 → EF-2 → EF-4/G2 → EF-8 → EF-5 → EF-6; then EF-11/G5 → EF-12
and EF-3 integrated with the agreed source identities; finally EF-A. Pure
revision-label correction may land during EF-2, but does not complete EF-3.
Core checkpoint first; ordered eligible stretches: EF-7 (editor match selection),
EF-9 (compact chains), EF-10 (segment actions). Each claimed stretch reruns its
EF-A evidence and tooling checkpoint before the next is claimed. No runtime
work is complete merely because its plan entry or core checkpoint exists.

## Authoritative guidance ledger and traceability

User direction is marked U; agent recommendations are marked R. Retain original
rows with explicit corrections below; EFR-37 supersedes any interpretation of
EFR-01 as a ranking defect, and EFR-40/41 confirm and refine EFR-29. Current proof
is recorded under each completed task; exact segment actions (EFR-06/EF-10)
remain scheduled. EFR-01–36 are the original audit; EFR-37–49 are the amendment.

| ID | Source and active requirement | Implementation / proof |
| --- | --- | --- |
| EFR-01 | U: exact filename search admits unrelated-looking files; determine fuzzy matching versus hierarchy ranking rather than guessing. | EF-0, EF-4; exact, similar, absent-name fixtures. |
| EFR-02 | U: Ctrl+Backspace, Ctrl+A and ordinary editing should work in filter and basically all single-line text inputs, sharing palette capabilities. | G1, EF-1/EF-2; field inventory and shared editing conformance. |
| EFR-03 | U: one motion should traverse a sole continuation such as `gametest/java/ca/teamdman/sfm`. | EF-9; complete single-child chain fixture. |
| EFR-04 | U: prefer merging by default per Explorer, not a global toggle changing every panel; retain path-specific overrides. | EF-9; independent panel preferences and persistence. |
| EFR-05 | U: right-click a merged chain to unmerge locally; avoid widespread layout/scroll disruption. | EF-9; anchor and override tests. |
| EFR-06 | U: also target an individual merged segment (e.g. `teamdman`) to act/create there without unmerging; never assume deepest child. | EF-10; exact segment identity and action capture. |
| EFR-07 | U: scroll anchoring becomes ambiguous with selected/topmost/disjoint items; specify it. | G3, EF-5/EF-6/EF-9; anchor matrix below. |
| EFR-08 | U: Ctrl+Shift+F filters complete domain; Ctrl+F finds/highlights without hiding. | EF-4/EF-5; independent state and remappable action wiring. |
| EFR-09 | U: preserve high-contrast inversion's readability while making find highlights more yellow. | G4, EF-5; theme/render evidence, not a fixed guessed colour. |
| EFR-10 | U: find menu exposes next, previous, next wrapping, previous wrapping. | EF-5; all four canonical actions and edge cases. |
| EFR-11 | U: Enter/Shift+Enter in find default to next/previous wrapping; Enter clears old selection and selects the match. | EF-5/EF-6; focus-scoped navigation, singleton replacement. |
| EFR-12 | U: both filter and find input menus offer Clear input. | EF-2/EF-5; mouse and action parity, isolated field reset. |
| EFR-13 | U: distinguish neither match, self-only, descendant-only, self-and-descendant. | EF-4/EF-5; full four-state matrix plus unknown descendant state. |
| EFR-14 | U: self matches highlight the matching name fragments; descendant match gets a pixel-art-style dot. | G4, EF-5; fragment bounds and descendant indicator not just full-row tint. |
| EFR-15 | U: no selected item means find starts at top visible element. | EF-5 anchor test, inclusive top row. |
| EFR-16 | U: any visible selections mean start below the topmost visible selected element. | EF-5/EF-6 anchor test, exclusive selected row. |
| EFR-17 | U: selected items exist but none are visible means start at top visible element. | EF-5/EF-6 anchor test; hidden selection must not pull viewport away. |
| EFR-18 | U: Explorer needs multiple selections; design navigation around it now. | EF-6, X-9 adapter; membership versus primary cursor/anchor. |
| EFR-19 | U: Alt+J adds next find match; Ctrl+Shift+Alt+J selects all highlights, in both editor and Explorer. | G1, EF-6/EF-7; scope-correct shared actions and 2D editor coverage. |
| EFR-20 | U: do not repurpose Shift+Enter for additive selection and then invent modifiers for backwards navigation. | EF-5/EF-6/EF-7; shortcut contract preserved. |
| EFR-21 | U: case sensitivity, whole word, regex options with inline toggle affordances like the supplied screenshots. | G2, EF-4/EF-8; visible mode flags, localized tooltip/state. |
| EFR-22 | U: explicit Dot-all option beside regex; paragraph/multiline matching should not need a workaround character class. | G2, EF-8; multiline/LF/CRLF, invalid and zero-length pattern tests. |
| EFR-23 | U: exact requested puppet might postdate the review's pinned release-to-HEAD capture. | EF-0/EF-3; distinguish corpus absence from search failure. |
| EFR-24 | U: visible warning that the review does not contain latest changes. | EF-3; async freshness and actionable banner, unknown is not current. |
| EFR-25 | U: filesystem collections and Git-revision collections are different addressing schemes. | G5, EF-3/EF-11; explicit domain identity in UI and details. |
| EFR-26 | U: comment on code that is uncommitted or untracked as well as Git-pinned code. | EF-11/EF-12; immutable working-tree capture, no automatic git add/commit. |
| EFR-27 | U: a mutable `src/main/hi.txt:L25C50-L50C0` can later exceed EOF; preserve this failure distinction. | EF-11/EF-12; stale/unresolved target, no clamping or silent retarget. |
| EFR-28 | U: a field selector such as an access-transformer-style DiskItem field reference differs between live filesystem and `rev:abc123`. | EF-11/EF-12; same selector against distinct source revisions, ambiguous/missing symbols explicit. |
| EFR-29 | R from observed code: literal case-insensitive matching should be default for Find and new filter UI; fuzzy is an explicit alternate mode, not a silent fallback when nothing exists. | G2, EF-4; compatibility for existing fuzzy CLI calls documented rather than silently rewritten. |
| EFR-30 | U/R: `Ctrl+F`, then typing `explore`, should find the next candidate from the stated anchor; R: freeze that anchor while typing and preview the candidate separately from committing selection with Enter. | EF-5; as-you-type candidate reveal, no async cursor chasing. |
| EFR-31 | R safety: finite/cancellable indexing and matching, no IO on render/key paths or unbounded regex work; incomplete cannot mean no matches. | G2, EF-4/EF-8/EF-A; bounded pages/work and stale completion rejection. |
| EFR-32 | R review safety: pinned commits plus dirty/untracked state are separate freshness facts; commit equality alone is insufficient. | EF-3; unchanged HEAD + new/untracked file fixture. |
| EFR-33 | Preserved U: comments/approvals remain portable and resumable; reevaluating a live selector does not transfer approval automatically. | G5, EF-11/EF-12/EF-A; restart and exact-coverage queries, no new appdata-only review state. |
| EFR-34 | Preserved U: controls are discoverable public actions, keyboard and mouse equivalent; avoid opaque widget IDs, preserve focus and localization. | G1, EF-2/EF-5/EF-6/EF-10/EF-A; registry and real input proof. |
| EFR-35 | Preserved U: virtual input must never move the OS pointer; use disposable evidence, frozen dependencies and current-tool handoff. | EF-A and operational boundary below. |
| EFR-36 | R integration: reuse the existing finder/session, typed selection and rendering infrastructure rather than build disconnected replacements. | Foundation, G1/G3/G4/G5, all task entry points. |
| EFR-37 | U correction: fuzzy filtering and hierarchical reranking are fine and working appropriately; do not fix them away. | EF-4/EF-8 preserve existing fuzzy relevance; supersedes the defect interpretation of EFR-01/EF-0. |
| EFR-38 | U: show actual Git revisions as review identities, not a primary label saying HEAD. | EF-3; old review with `after_label: HEAD` renders its actual candidate commit without rewriting old bytes. |
| EFR-39 | U: an ambient alias may decorate a revision as `abc123 (HEAD)` only while it is actually HEAD; a later commit must not make it misleading. | EF-3; async equality check and stale alias removal; label is not target identity. |
| EFR-40 | U: both Find and Filter get match-case, whole-word, regex and fuzzy controls; fuzzy/regex are mutually exclusive. | G2, EF-4/EF-5/EF-8; one shared option type, independent per-field state, legal combination tests. |
| EFR-41 | U: Alt+F toggles fuzzy in the filter input; literal is default. | EF-5; focused Find/Filter Alt+F uses the same control, no global palette hijack; prior recommended literal default now confirmed. |
| EFR-42 | U: Alt+H toggles highlight-matched-glyphs for both Find and Filter. | EF-5; each field owns its toggle; hiding highlights does not disable matching/filtering/navigation. |
| EFR-43 | U: use different Find/Filter colours, ideally with a third intersection colour; composition could produce it naturally. | G4, EF-5; independent glyph membership and visible overlap test, not two indistinguishable row tints. |
| EFR-44 | U: if intersection colour is chosen explicitly, render filter/find/both/none per glyph rather than composing both overlays over the custom colour. | G4, EF-5; explicit theme colour overrides composition with one classified highlight pass; preserve option of derived colour. |
| EFR-45 | U: Shift+click replaces selection with a range; Ctrl+click appends/toggles a member. | EF-6; anchored pointer selection, hidden members and plain-click replacement tests. |
| EFR-46 | U: Ctrl+Shift+click adds/toggles a range instead of clobbering existing selections. | G3, EF-6; record deterministic whole-range add/remove policy and overlap cases. |
| EFR-47 | U: reviews must support (Git commit, files on disk) before/after, not require agents to commit before review. The currently open review can remain a commit pair. | EF-11/EF-12; captured tracked/untracked working tree with explicit source kind; no automatic commit/stage or conversion of existing review. |
| EFR-48 | U: agents should read the portable review/comments for uncommitted changes, rather than forcing copy/paste communication. | EF-12/EF-A; ordinary in-game comment → portable file → existing structured CLI query/inspection → restart. |
| EFR-49 | U: update plans to retain these atoms, then set and pursue a goal for the next major usable iteration. | Authorized core/ordered stretches, amendment audit and EF-A testing guide. |

## Intent audit evidence

- **Amendment pass 1 — extraction:** Reread the subsequent original message;
  captured EFR-37–49 separately, including the initial conditional Git-only idea
  followed by its explicit rejection in favour of commit/disk review. Do not
  interpret the first paragraph as a Git-only constraint.
- **Amendment pass 2 — traceability:** Mapped all new atoms to EF-3/4/5/6/8/11/12
  and EF-A; the core now includes working-tree review. Shared control types do
  not mean Find and Filter share mutable options. Existing fuzzy ordering is
  retained; literal mode and highlight visibility are orthogonal to ranking.
- **Amendment pass 3 — adversarial:** Rechecked independent highlights and
  custom overlap versus natural composition, Alt+F/H focus ownership, additive
  range behaviour, ephemeral HEAD decoration, untracked bytes, and agent-readable
  portable comments. Explicitly exclude automatic commit, source overwrite,
  approval migration and treating working-tree snapshots as Git commits. Original
  extraction/traceability rechecked after the corrections; all EFR-01–49 retained.

- **Pass 1 — extraction:** Reread the latest original message, its copied row
  details and six images. EFR-01–28 preserve the observed input/search problems,
  compact-chain scope/segment examples, all find controls and anchor states,
  additive shortcuts, regex/dot-all and mutable-versus-pinned target examples.
  EFR-29–36 distinguish recommendations and inherited safety/integration rules.
- **Pass 2 — traceability:** Checked each of the 36 unique ledger rows against
  gates and EF-0–EF-12/EF-A. All fourteen task contracts have Work, Validation
  and Completion criteria. Inspected existing finder actions/session tests to
  avoid re-planning an already implemented backend; review refresh is explicitly
  distinguished from creating a newer candidate. Only EF-0 is complete.
- **Pass 3 — adversarial omission:** Rechecked the full latest request for
  per-path overrides AND per-segment menus, off-screen/disjoint selection,
  inclusive/exclusive search starts, four match states, Shift+Enter backwards,
  partial domains and live untracked targets. Repaired a draft recommendation
  that deferred all navigation until Enter: `Ctrl+F explore` now locates/reveals
  the anchored candidate as typed, while Enter commits selection. Re-ran
  extraction and traceability after that repair. Checked that Alt+J is not
  lost to earlier Alt-for-focus guidance and that screenshot Alt+C does not
  silently steal Cancel. No human file or pinned approval is to be rewritten.
- **Source limitation:** None for the latest request. Earlier discussion is
  preserved by linked authoritative ledgers; this audit does not claim to have
  reread every missing historical assistant reply or re-prove prior runtime tests.
- **Planning validation:** 36 unique requirement IDs, fourteen complete task
  contracts, six touched documents with no missing local Markdown targets, no
  trailing whitespace in the new plan, and scoped `git diff --check` passed.
  Validation commands below were inspected, not run as runtime tests this turn.

## Verified foundation and source entry points

All paths below are relative to the primary root; source inspection is not a
claim that new behaviour has been runtime-tested.

- `platform/minecraft/src/main/java/ca/teamdman/sfm/client/search/SFMFuzzyScorer.java`:
  normalized Damerau-Levenshtein distance and subsequence scoring, threshold
  `0.65`, plus prefix/substring bonuses. A result need not contain the literal
  query. This scorer is shared with palette relevance; changing its global
  threshold to fix Explorer would silently change another product surface.
- `.../client/explorer/lazy/SFMExplorerProjection.java`: hierarchical filtering
  retains context ancestors, sorts branches by best descendant score, and can
  expose nonmatching children of explicitly expanded matching rows. This differs
  from a flat global results list. Keep roles/counts honest.
- `.../client/review/release_review/SFMReleaseReviewExplorerRuntime.java`:
  review complete-domain search scores `reviewSearchTerms` with the same scorer,
  caps selected matches and materializes their ancestor paths. A complete
  review domain still excludes documents outside its pinned corpus.
- `.../client/screen/explorer/SFMExplorerPanel.java`: filter is a `String`
  `filterDraft` with hand-written key handling. Ctrl+C copies the whole draft;
  Backspace removes one final code point even with Ctrl; Delete clears all;
  typed characters append. There is no Ctrl+A/caret/selection editing parity.
  `SFMCommandPaletteScreen.java` uses `EditBox` and separate history support.
- `.../client/explorer/lazy/SFMExplorerSession.java` already has independent
  `FinderState` (pending/ready/empty/failed), generation-checked publication,
  canonical path ordering and wrapping navigation. Its operational row cursor
  is singular (`navigationCursor`); selection overlays are not interactive
  multi-selection. `.../client/explorer/action/SFMExplorerActionEngine.java`
  requests finder domains asynchronously and reuses query projections.
- Existing action grammar (keep compatible; new flags/actions need G1/G2):
  `sfm action invoke sfm:explorer/find/set focused java source`,
  `sfm:explorer/find/next focused`, `sfm:explorer/find/previous focused`,
  `sfm:explorer/find/clear focused`. The latter three are action tails for
  `sfm action invoke`. This is foundation, not the requested panel Find UI.
- `.../client/screen/SFMScreenRenderUtils.java#renderHighlight` implements the
  existing colour-logic highlight. It is version-adapted rendering code; do not
  globally recolour every text selection while experimenting with Find.
- `platform/cli/sfm-propagate-changes/src/release_review_git.rs`: ambient
  relationship currently compares pinned candidate against ambient Git HEAD,
  including review-evidence-only descendants. The exact-HEAD return does not
  establish absence of uncommitted/untracked changes.
- [release-review-v1 architecture](../architecture/release-review-v1.md):
  `after_label: HEAD` names a pinned candidate, not a moving target. Existing
  review refresh rematerializes the original pair, refuses source-affecting
  divergence, and preserves human evidence. It is **not** an update-to-latest
  operation. Keep that distinction in new UI copy and actions.

## Confirmed direction, recommendations, and gates

User-confirmed: split Find/Filter shortcuts; additive-selection shortcuts;
per-Explorer compact default with local unmerge and segment targeting; explicit
dot-all; comments can address live filesystem and immutable revisions.

Confirmed new direction: literal default, fuzzy preserved and explicitly
toggleable, same case/whole-word/regex/fuzzy suite on both Find/Filter, independent
Alt+H highlight flags, exact Git revision labels with truthful optional HEAD
decoration, and commit-before/working-tree-after review. The initial conditional
Git-only thought is superseded by the later explicit request in the same message.

Reversible recommendations: case-insensitive name matching default;
optional explicit path/metadata and fuzzy modes; per-panel preference persistence
through existing workspace/preset ownership; Find opens/focuses input and
locates/highlights the anchored candidate as typed, first Enter commits its
selection. The user's later
phrase "focus the filter" in the Ctrl+F example is read as Find, consistent with
their explicit Ctrl+Shift+F versus Ctrl+F distinction, not a second filter.

| Gate | Decision required before downstream work | Acceptance consequence |
| --- | --- | --- |
| G1 (EF-1) | Inventory fields; choose shared editing adapter and exact action/focus ownership. Preserve existing palette undo/completion. Alt+J is a user-requested scoped exception to earlier Alt-for-focus guidance. Screenshot Alt+C/W/R hints are references, not authority to steal palette Alt+C Cancel. | Field/shortcut conformance matrix and migration list, including excluded widgets with reason and tracked follow-up; no silent remainder for "all single-line inputs". |
| G2 (EF-4/EF-8) | Freeze matcher options, searched fields, literal/fuzzy compatibility, Unicode whole-word boundaries, regex engine/newline/zero-width/overlap semantics and executable work limits. Existing dependencies only. | Same predicate/fragments at resolver and projection layers; old command compatibility fixtures; adversarial regex proof before exposing regex. |
| G3 (EF-6/EF-9) | Adapt X-9's set membership plus separate primary/anchor/order to interactive row selection; define layout/scroll anchoring and persistence keys for per-panel/per-path compaction. | Hidden/disjoint selections survive projection changes; split labels are aliases of exact paths, not new resources. |
| G4 (EF-5) | Choose yellow-biased high-contrast composition after measuring/rendering existing inversion; specify theme, selection, diff and comment layer ordering. | Readable dark/light examples, marker not colour-only, no unrelated selection recolouring or clipping. |
| G5 (EF-11) | Freeze live selector versus immutable capture schema using the parent review/selection adapters; decide compatible portable working-tree snapshot storage and explicit update/migration workflow. | Golden cross-language serialization/query fixtures and failure cases before EF-12; no opportunistic new incompatible review format. |

### Find semantics to freeze in G2/G3

One shared matcher-options type serves Find and Filter, with independent values
and query generations. Match-case and whole-word are independent constraints;
regex/fuzzy are an exclusive mode pair (enabling one disables the other; disabling
the active special mode returns to literal). Dot-all is applicable only in regex
mode. Alt+F toggles fuzzy, Alt+H toggles highlight visibility only while the
respective Find/Filter input owns focus. Case/word/regex controls retain localized
mouse affordances and scope-correct action bindings. Preserve the established
fuzzy scorer/reranking when enabled; do not globally adjust palette relevance.

Find and Filter highlight state is independent of their predicates. Compute
per-glyph membership `none | find | filter | both` after applying visibility
toggles; an explicit intersection colour draws once for `both`. If the theme
omits an explicit intersection colour, a documented deterministic composition
may derive it. Recommended defaults: yellow Find, cyan Filter, readable distinct
intersection with contrast-adjusted glyph foreground. User themes can override
colours; syntax, selected text, review markers and descendant dots remain legible.

Find does not change filtering or reorder the visible hierarchy. Filter may
hide rows but keeps matching branches and navigable before/after/diff children;
context rows are not counted as direct matches. Literal mode must report zero
direct matches for an absent exact filename, not silently display fuzzy matches.
If path/metadata matching is enabled, explain the matched field; do not invent
highlighted name characters when the match exists only in metadata.

Find initially searches the current authorized Explorer domain independently of
materialization; a resolver without complete-domain capability must report that
limit and offer an explicit supported scope. When a filter is active, default
navigation stays inside the filtered domain (including revealed context children)
and says so. Expanding ancestors to reveal a match is permitted; silently clearing
the filter is not. A user may explicitly clear it or choose a broader scope.

Capture an initial row/path anchor when Find takes focus; preserve it while
query/options/results change. Empty query clears highlights/candidate evidence,
not the Explorer selection. As typed, locate and reveal the next candidate using
the following forward rule, without changing selection or the frozen anchor.
First Enter selects that preview candidate; later Enter/Shift+Enter advance
relative to the committed current match. Initial Shift+Enter chooses the reverse
candidate from the frozen anchor. Coalesce async preview updates; reject results
whose query, panel or explicit user-navigation generation has changed.
Pending Enter may queue one generation-bound intent; never replay it for another
query or a closed panel. Thus `Ctrl+F explore` does locate the next match before
Enter, while Enter is the explicit selection-replacement operation. Escape may
restore the captured viewport only if the user has not explicitly navigated or
committed a match in the meantime; record that reversible policy in G3.

| Selection at find capture | Forward starting point |
| --- | --- |
| None | Topmost visible logical row, inclusive. |
| At least one selected row is visible | Immediately after the topmost visible selected row. |
| Selected rows exist but none is visible | Topmost visible logical row, inclusive. |

Use stable display traversal order, not fuzzy relevance order, for next/previous.
G3 must adapt today's canonical-path finder list without breaking deterministic
command iteration. Reverse is the corresponding preceding search from the
anchor; nonwrapping boundary reports "no further match" without changing
selection. Wrapped search visits each eligible row at most once. Enter replaces
selection; Alt+J adds the next unselected match; Ctrl+Shift+Alt+J selects all
matches in the declared scope. No matches leave prior selection intact. Partial
results require explicit partial feedback and must not claim select-all completed.

Expose self/descendant flags separately. Unknown/unloaded descendants are a
separate state, not false. Highlight self name fragments; use a pixel-art dot
plus descriptive tooltip/accessibility text for descendant evidence; combine
both when both match. Async counts and flags carry the domain/query generation.

### Compact-path identity and scroll policy (recommendation)

Compact only certified complete one-child **container** continuations. A page
with one known child, or a filter that leaves one child, is not proof. Never
cross resolver/root/authority boundaries or fold a file's before/after/diff
facets into a fake directory. Root hoisting and chain compaction are separate.

Preference precedence: exact per-path override, then that Explorer's setting,
then default. A future global default seeds new panels; changing one panel does
not mutate siblings. Persist the panel setting and path exceptions via existing
workspace preference/preset ownership, not in the reviewed source or comments.

For local merge/unmerge, anchor the manipulated row/segment at its prior pixel
offset. For a panel-wide toggle, anchor the top visible logical item, not an
arbitrary selected member; keep all selections by identity without auto-scrolling
to them. If the anchor disappears, resolve its containing merged row/nearest
surviving ancestor, then nearest surviving neighbour deterministically. Clamp
only viewport limits. Tests include disjoint selections above/below the viewport.

### Review address and freshness policy

Git-backed targets are always identified by exact revisions; display the short
revision with full identity in details and only append `(HEAD)` after a current
ambient equality check. Never use persisted `after_label: HEAD` as the primary
display or retarget old reviews. Preserve historical aliases only as provenance.
An input commit-ish such as HEAD may still be resolved at capture time.

The new before/after source pairing is Git commit versus files on disk. Its after
side has an immutable captured-working-tree identity and content, not a made-up
Git commit. Source changes after capture are new candidate evidence, never silent
changes under an existing comment. Capture/refresh is explicit and preserves the
previous review/capture; use the existing portable comment/query substrate so
agents can read the review directly without copy/paste or a prerequisite commit.

Git review target: repository/lane + exact commit/tree + path + document
revision/hash + exact selection (or explicit semantic selector evaluated there).
Filesystem intent: authorized root + relative path + optional semantic query,
with an explicit live/re-evaluate policy. The same printable path or field name
does not establish identical content or authority.

Recommendation for saving a live-file comment: persist both the user's live
selector intent and its immutable evaluated evidence (source snapshot/hash,
range, query/selection revision). This permits untracked code review without
changing Git. A later evaluation is a new result; changed, deleted, beyond-EOF,
ambiguous or unresolvable targets retain their original comment and report their
state. Never clamp old ranges, choose a nearest symbol, or transfer `#approved`
automatically. An explicit migration can propose/confirm a new target.

Freshness is two axes: pinned-versus-ambient Git relationship, and workspace
changes (staged, unstaged, untracked) relative to ambient/pinned content. Show a
bounded banner such as "Review pinned to 58ed4e431; newer source exists" with
separate local-file coverage information. Pending/unavailable checks are not
"up to date". A missing file may be outside the review, unchanged since base,
excluded, untracked, or unavailable; name the evidenced reason, not a guess.

Offer flat actions to inspect the pin/freshness details, open the corresponding
working-tree location, or explicitly create/capture a new review candidate.
Do not imply existing `review refresh` follows moving HEAD. Any new candidate
or working-tree capture must preserve the old portable review and its comments;
new/deleted content changes the review denominator. Ask before overwriting an
existing human review or adopting a materially different persistence schema.

## Work contracts

### [x] EF-0 Diagnose the supplied filename and input reports read-only

**Completion notes:** On 2026-09-06 inspected the scorer, review search,
projection, filter key handler, existing finder, ambient Git comparison and
release-review specification. `git rev-parse HEAD` returned
`16328629fa60c45a4f525b6f20aaa77715f91077`. The user's portable file has base
`31135b8e86801b862d5cb2283c7c5878b7cc5bb4` (4.34.0-1.19.2) and candidate
`58ed4e43122adfd3d6939f0401101fdf6d501108`, tree
`6b43af199bb246e1d8e31b999255c9d220dd11e7`, despite `after_label: HEAD`.
Its 1,766 corpus entries contain zero entries for the requested filename.
`git status --porcelain -- <puppet path>` reports `??`: the current local file
is untracked. A review built from today's Git HEAD alone still omits it.
Manual review SHA-256 read during inspection:
`9addccec595caa916efdb8019270f62d577b336508d2608d84bda2fe7aadd592`.
This is a point-in-time observation, not a prohibition on later human edits.

**Work:** Preserve separate causes: fuzzy matching admits similar
matches as intended (user confirmed ranking works, EFR-37); corpus pinning/untracked status explains why this particular file is
absent; primitive input handling explains missing selection/word deletion.

**Validation:** Read-only commands from the primary root:

```pwsh
git rev-parse HEAD
git status --porcelain -- platform/minecraft/src/gametest/java/ca/teamdman/sfm/gametest/puppet/action/ExploreReviewInteractivelyPuppetAction.java
$reviewData = Get-Content -Raw -LiteralPath 'platform/minecraft/run/manual-test.sfm-review.json' | ConvertFrom-Json -Depth 100
$reviewData.repository_bindings | ConvertTo-Json -Depth 8
$reviewData.corpus_documents.Count
@($reviewData.corpus_documents | Where-Object { $_.path -like '*ExploreReviewInteractivelyPuppetAction.java' }).Count
```

**Completion criteria:** Evidence distinguishes all three issues without editing
the review, starting a game/test, or claiming new runtime validation. Met.

### [x] EF-1 Freeze the shared single-line editing/focus contract

**Completion notes:** G1 closed by source inspection on 2026-09-06. Shared
single-line buffer owns text, directional UTF-16 caret/anchor (always code-point
boundaries), edit operations, word breaks and optional existing document-history
session. A vanilla EditBox adapter uses the same core; palette opts out of local
history because its host already records it. Custom canvas inputs use the same
buffer and a bounded glyph-aware renderer. No duplicate undo-stack engine.

Migration inventory: Explorer filter and new Find; ItemStack picker search;
colour hex entry; vanilla EditBox fields in SFMCommandPaletteScreen,
SFMCommandDraftScreen, SFMKeyBindingScreen and LabelGunScreen. Explorer location
is an action-backed read-only button which opens the ordinary text editor, not
an inline draft. Theme/argument panels that delegate to these controls inherit
the adapter. Terminal input is a PTY document, not a single-line widget; full
editors keep their existing multi-line/2D controller. No vanilla/third-party
widgets are globally patched. These are explicit scope exceptions, not silent
unmigrated SFM inputs. Audit again for newly added custom fields before EF-2
completion. Pure tests cover edits without Minecraft; runtime tests prove adapters.

Baseline: `platform/minecraft/build/ef-next-iteration-20260906/baseline.json`
records HEAD, the dirty/untracked worktree, protected review/dependency hashes
and installed CLI hash. Initial CIM inspection was sandbox-denied; elevated
read-only process inspection found no matching game/CLI/Cargo/terminal processes.
No processes stopped. Runtime code is not yet changed at this checkpoint.

**Work:** Inventory SFM single-line fields (Explorer filter/location, palette,
keybinding manager search, argument widgets and other in-scope fields). Trace
`SFMCommandPaletteScreen`, `EditBoxAccessor`, input/history actions and editor
word-navigation behaviour. Choose one shared adapter/model seam; do not copy
palette business logic into Explorer. Close G1; list every migration and any
intentional exception. Retain colon/slash/hyphen word breaks, not underscores.

**Validation:** Inventory via `rg -n 'new EditBox|filterDraft|charTyped' platform/minecraft/src/main/java/ca/teamdman/sfm/client`;
write conformance cases for caret, Shift-selection, Ctrl+A/C/X/V, word move/delete,
Home/End, Unicode, undo/redo, and focus lifecycle before changing handlers.

**Completion criteria:** Named shared owner, exact action/scope contract and
field-by-field migration matrix; no runtime capability claimed yet.

### [x] EF-2 Adopt shared editing in Explorer and other single-line fields

**Progress (2026-09-06):** Added `SFMSingleLineInput` backed by the existing
document-history kernel, `SFMSingleLineEditBox` for vanilla chrome, and
`SFMSingleLineInputView` for bounded canvas caret/selection rendering. Migrated
Explorer Filter, ItemStack search, colour hex, palette input (host-owned history),
command draft, keybinding search and Label Gun fields. Host filter/maximum-length
reconfiguration starts a fresh field history domain; ordinary undo/new-edit keeps
alternative histories. Clipboard paste is one edit/query update. Filter's Clear
context choice invokes the existing exact-Explorer public action.

**Validation so far:** Shared core initially 8/8; after validation and panel
integration tests, `test run --branch 1.19.2 --filter client` passed **1570**, failed
**0**, aborted **1** (existing installed-symbol-worker prerequisite), 1571 found.
Evidence: `platform/minecraft/build/ef-next-iteration-20260906/client-tests.ndjson`.
The preceding attempted `--filter 'A|B|...'` selected zero tests: CLI filter is a
substring, not regex; that failed invocation is not acceptance. Initial sandbox
cache permission failure was recovered using authorized host execution; no cache
or dependency changes. Current virtual-input exploration is in progress, so EF-2
is not yet complete. A later Clear-menu and test-observation change postdates this
JUnit pass and was included in the concluding compile/runtime/test checkpoint.

**Concluding checkpoint:** `input-checkpoint-tests.ndjson` passed **1571**, failed
**0**, aborted **1** (same installed-worker prerequisite), 1572 found. Added
Explorer regression proving caret-only operations emit no domain query and each
text edit emits exactly one public Filter action. No `new EditBox(...)` remains
in SFM-owned source. Palette retains its own history owner; no global vanilla or
third-party input patch was introduced.

Real game: `sfm:title_screen_exploratory_review`, `1920x1080@2`, PID 28000,
45 virtual-input observations in
`platform/minecraft/runGameTestPreview/sfm-puppet/exploration-control/session-9c1ea752-372a-40c1-997f-8ba1d72f69f8`.
Steps 4–10 prove Explorer middle-word deletion/undo/Ctrl+A replacement; 12–17
prove palette separator selection/replacement/host undo; 20–25 prove colour
selection replacement; 30–36 prove picker Ctrl+Backspace/Home/undo; 39–44 prove
keybinding search selection/deletion/undo. Screenshots 9 and 32 inspected.
Preview: `platform/minecraft/build/sfm-toolchain/artifacts/game-test-preview/runs/sfm-title_screen-20260906-140632-225/index.html`.
Client exited 0 and viewport restoration was recorded. No OS pointer injection,
review mutation, theme save or keybinding change. Command-draft and Label Gun
screen-specific business flows were not individually exercised; their editing
uses the same live-tested EditBox adapter and passed the client regression suite.
Final test-only/headless null-client guard postdates the live run and is covered
by the concluding suite; ordinary live behavior is unchanged. Remaining Find
chrome, localized labels, option actions, and broad final matrix belong to EF-5/A.

**Work:** Replace filter append-only handling; migrate EF-1's inventory in
bounded subitems recorded here. Preserve palette completion and ordinary history.
Clear/context actions use the same public routes as keyboard input, copy selected
text when selected, and keep paste/selection/caret state correct under async
filter updates. A batch paste creates one query update, not one update per glyph.

**Validation:** Run V1/V2 below. Extend `SFMExplorerPanelInteractionTests`,
`SFMExplorerFilterTests`, palette input/history tests and the shared conformance
suite; include Ctrl+Backspace with a middle caret and selected span, not just end
of input. EF-A must type/edit through virtual key callbacks, not `setFilterQuery`.

**Completion criteria:** All EF-1 fields pass the declared editing matrix;
filter typing no longer loses ordinary editing shortcuts or blocks the UI.

### [x] EF-3 Expose pinned review identity and honest freshness

**Progress:** Existing Git ambient classification deliberately describes commit
ancestry only and is reused by safe rematerialization. Preserve that contract.
Add working-tree evidence as a separate axis (staged/unstaged/untracked, unknown
on failure, declared review-evidence exclusions) so `HEAD == candidate` cannot
masquerade as a clean checkout. Refresh must still mean the original pinned pair.

2026-09-06 implementation checkpoint (not acceptance yet):

- Review Explorer root/lane labels use the actual 12-character commit pins;
  old stored `HEAD` labels are retained only as legacy data, not displayed as
  identity. Optional ambient `(HEAD)` decoration is intentionally omitted.
- Rust adds a separate `WorkingTreeEvidence` probe and
  `review session freshness --file <review>` structured read-only endpoint.
  Status now fails closed for local source changes or an unavailable check while
  retaining document-only completion separately. Existing refresh still means
  the original pinned pair and its ancestry classification is unchanged.
- Probe handles staged/unstaged/untracked paths, both rename endpoints, unusual
  path bytes and exact declared review-evidence exclusions. Git runs with
  `GIT_OPTIONAL_LOCKS=0`; a fixture verifies that status leaves index bytes intact.
- Java caches one check per open-review lease/binding identity, not per panel,
  render or keystroke; explicit recheck and Ctrl+R invalidate it. Worker timeout
  is 30s, stdout 8MiB, stderr 64KiB. Stale completions are rejected; missing or
  older companion output is unknown, never clean. No periodic probe is implied.
- A shared Explorer banner shows assessment/check age and opens flat registered
  `sfm:review/freshness/check`, `/details`, `/copy` actions plus current-files
  Explorer choices per resolved repository. In-game details are capped at 64KiB
  and include the full machine-readable replay command. Old review leases must
  not display the next opened review's evidence.
- Shared JSON fixture: `docs/architecture/fixtures/release-review-freshness-v1.json`.
  Rust Git tests 12/12; CLI review tests 8/8 (including shared schema and unchanged
  refresh tests); revision-label model tests 19/19. Full Java client pass and
  current-installed-runtime visual proof are being completed. No human review
  files have been changed. Localization/guide integration remains in EF-A.

Acceptance checkpoint 2026-09-06:

- Full client suite: **1,575 passed, 0 failed, 1 prerequisite-aborted optional
  installed-symbol-worker test** (1,576 found). Includes the three new freshness
  tests for unknown/race/dirty states, immutable review content and late workers.
- Installed CLI SHA256
  `63736e11d6b37112af19aaeb0d6659e4538e3854c093e315f0e297296f573ec6`;
  installer used existing locked/offline dependencies. Real repository freshness
  observed exact HEAD/candidate `16328629fa60c45a4f525b6f20aaa77715f91077`
  alongside **304 dirty/untracked paths**, correctly reporting excluded source.
- Disposable `platform/minecraft/build/ef-next-iteration-20260906/freshness-runtime.sfm-review.json`
  captures HEAD^→HEAD (2 changed paths, 10 units). The exploratory puppet used
  virtual inputs only, PID 8156, 1920×1080@2; control session
  `session-756572f7-37e2-4e05-a5b5-5980c78a12c8`, steps 1–16.
  Step 3 shows the completed banner; steps 4–6 mouse-open its details; steps
  7–11 open the current-files Explorer beside the pinned review; steps 12–15
  explicitly recheck and show a reset check age. Full/split/narrow layouts and
  cross-panel tooltip rendering were inspected. Review generation/comment count
  stayed 2/10, dirty=false, with no review mutations invoked.
- Correction to the first two exploration captions: `/open/view` opens writable;
  `/open/read_only/view` is the read-only variant. This was only the disposable
  file; no write actions ran. Step 3 explicitly records the correction.
- Exploratory observation for EF-A ergonomics: selecting a fully specified
  palette option may shrink its suggestion list and move Execute. Step 9 missed
  the old button location; step 11 used newly observed bounds and succeeded.
  No hardcoded stale coordinates were treated as a successful action.
- Human `manual-test.sfm-review.json` SHA256 remains
  `9addccec595caa916efdb8019270f62d577b336508d2608d84bda2fe7aadd592`.
  Goal remains active; working-tree capture and Find/Filter are not yet delivered.
- Exploration exited normally (no timeout/cancellation); packaged evidence:
  `platform/minecraft/build/sfm-toolchain/artifacts/game-test-preview/runs/sfm-title_screen-20260906-143108-704/index.html`.

**Work:** Extend typed ambient relationship evidence without weakening existing
release checks. Async read-only work detects current HEAD divergence, staged/
unstaged/untracked changes in authorized scope, evidence-only commits and unknown
states. Cache by explicit generation, invalidate on explicit refresh/reopen,
show check age, and reject stale completions. No Git calls in render/keystrokes.
Add concise banner, pinned candidate label and flat detail/open-working-location
actions to the existing review Explorer. Explain corpus absence when evidence
supports it. Do not modify the user's file to include new source automatically.
EFR-38/39 require actual commit labels, optional truthful `(HEAD)` decoration,
and removal/invalidation of that decoration after ambient HEAD changes.

**Validation:** V3/V4; disposable Git fixtures for exact clean HEAD, same HEAD
dirty, same HEAD untracked, staged changes, evidence-only descendant, newer source,
non-descendant, renamed/deleted file, missing Git and moved root. Schema fixtures
must match Java/Rust. EF-A reproduces the reported absent-file story, banner
present on open and no write to the original review. Existing refresh tests stay
green and continue to mean rematerialize the original pair.

**Completion criteria:** The UI cannot call a review "latest" merely because
HEAD matches; the user can see the pin and understand how to inspect excluded
local source without losing old review evidence.

### [x] EF-4 Define literal matching and extend existing finder evidence

G2 non-regex contract (2026-09-06): `SFMTextMatchOptions` is the shared predicate
value (literal/fuzzy/regex exclusive mode, case, whole-word, dot-all). Highlight
visibility is presentation state, not part of the matching cache key. Defaults
are literal/case-insensitive/not-whole-word; old fuzzy callers are represented
explicitly as legacy-fuzzy rather than changing the palette scorer globally.
Literal matching uses Unicode code points and returns original UTF-16 fragment
boundaries for Java glyph layout. Case folding is locale-independent simple
Unicode folding (not linguistic collation); underscores/letters/digits/combining
marks are word members. Punctuation including colon/slash/hyphen is a boundary.
Whole-word fuzzy matching scores complete lexical tokens for a single-word query;
multi-word/path queries score the full field. An exact substring/subsequence can
provide precise fuzzy glyph fragments; edit-distance-only matches mark the full
field as approximate evidence instead of claiming exact character alignment.
For a precise fuzzy subsequence, whole-word also requires its outer fragment
boundaries to meet word boundaries (so `paper` is not a whole-word match for
`papermachinery`). Approximate edit-only matches describe the whole token/field.
Searchable entry fields retain their identity so metadata/path-only matches do
not fabricate glyph highlights in the displayed name. Query/candidate/fragment
and fuzzy-product bounds fail explicitly, never silently mean no match. Regex
mode remains unavailable until EF-8's engine/work-limit proof; no inert toggle
is exposed by this checkpoint.

Foundation files now present: `SFMTextMatchOptions`, `SFMTextMatcher`, and
`SFMExplorerEntryMatch`. The existing fuzzy scorer's two-argument behavior is
unchanged; an explicit case-sensitive overload was added. Entry evidence keeps
search-field identity, original UTF-16 label fragments and partial diagnostics.
It translates only verbatim complete field occurrences into decorated labels.
Integration checkpoint (2026-09-06): typed options now travel through session
settings, finder state, request/response acknowledgement, cache keys, action
validation and projection. Old `filter/set <selector> <query>` and
`find/set <selector> <query>` explicitly retain fuzzy semantics. New
`filter/match` and `find/match` take `<selector> <literal|fuzzy> <match_case>
<whole_word> <dot_all> <query...>`; query text stays the final greedy argument.
The filter widget emits the explicit form, defaulting to literal. Registry,
mouse and keyboard option controls remain EF-5 (regex mode remains EF-8).
Changing view/sort/group/hoist/path display preserves the current options.

Find and Filter have separate ownership lanes; exact predicates may share work,
but switching a lane's options cannot cancel the other lane or reuse its result.
Failures are query/options-specific and do not retry every tick; explicit refresh
invalidates that root's query cache. Wrong-option provider responses fail closed.
Late results after close/location change cannot publish. An incomplete empty
finder result is failed/unknown, not `NO_MATCHES`. Complete-domain bounds survive
local projection. Field fragments and independent self/descendant/unknown evidence
are retained in `Result.matchEvidence`; `SFMExplorerMatchTraversal` follows
hierarchy/name-or-other-sort order independently of fuzzy relevance. It does not
expand or reorder the live tree. Copy-row details now includes match options.

Verification checkpoint: `sfm-propagate-changes.exe test run --branch 1.19.2
--filter client --log-filter warn --log-file
platform/minecraft/build/ef-next-iteration-20260906/options-client-suite-2.ndjson`
passed **1599**, failed **0**, prerequisite-aborted **1**, found **1600**. The
abort is the existing optional installed-symbol-worker prerequisite. Earlier
failures exposed implicit fuzzy assumptions in historical fixtures; these now
select fuzzy explicitly and retain their ranking assertions. New tests prove
literal absence for the requested puppet filename, Unicode original fragments,
all four self/descendant combinations plus unknown, independent query failures,
mode acknowledgements, incomplete absence, location/close guards, display order,
and real review-resolver predicate agreement across literal/fuzzy/case/word modes.
Final focused `--filter SFMExplorer` rerun passed **137/137**
(`ef4-final-explorer-tests.ndjson`); the last authority regression rerun
`--filter SFMExplorerFinderActionTests` passed **6/6**
(`ef4-authority-tests.ndjson`). `git diff --check` passed for changed matcher,
Explorer and action paths. No final Find UI/runtime claim is made here.

**Work:** Close the non-regex portion of G2. Introduce typed match mode/options,
searched fields and exact fragment evidence shared by resolver and projection.
Literal/case/whole-word modes are separate from fuzzy rank. Preserve legacy
palette fuzzy scoring and explicitly migrate/version old Explorer fuzzy command
behaviour. Finder and filter state/options are independent. Preserve canonical
identity while adding deterministic display-order traversal and descendant flags.
Preserve user-confirmed fuzzy ordering when enabled; share case/whole-word/
regex/fuzzy option semantics across both inputs, with regex/fuzzy exclusion.

**Validation:** V1/V3; extend `SFMExplorerFinderSessionTests`,
`SFMExplorerFinderActionTests`, `SFMExplorerFinderActionGrammarTests`,
`SFMExplorerProjectionTests` and `SFMReleaseReviewExplorerRuntimeTests`.
Fixtures include exact requested name, many similar `PuppetAction.java` names,
no exact name, metadata-only match, duplicate labels, lazy/partial domains,
query cancellation, moved/closed panel and old command compatibility.

**Completion criteria:** Literal absence is honest, direct/context matches are
distinct, and Find does not filter/reorder the tree. G2's regex clause remains
open for EF-8, not an excuse to present an unsafe or inert regex toggle.

### [x] EF-5 Expose Find/Filter inputs and deterministic navigation

**Completion notes:** Started after the EF-8 engine gate passed. Visible Regex/
Dot-all controls and their in-game accuracy are owned here; final runtime proof
remains EF-A. Editor multiline match-selection remains EF-7, not claimed by the
engine checkpoint. The following final checkpoint supersedes the interim pending
notes below, without claiming EF-A's final all-source acceptance.

GUI-4 virtual-input session `e28f7820-c6a2-4fae-a323-423870e860d0`, 27 steps,
finished normally (Java PID 12268, CLI exit 0). Durable report:
`platform/minecraft/build/sfm-toolchain/artifacts/game-test-preview/runs/sfm-title_screen-20260906-155609-064/index.html`.
Steps 6/12/15 show exact committed-file selection and cyan/yellow/purple glyph
membership, including light-theme rendering. Step 17 reports invalid `[` regex
without losing the selection. Steps 22–24 select the next two of 98 eligible
filtered matches and return to the first with Shift+Enter; step 26 shows split
GUI-4 controls. The temporary test-profile theme was restored byte-for-byte
(SHA256 `04FBF31D2181BBF70E71AD3C270EFC081D2BDE0662D47E0D36062C3873790A9C`).
Light-theme inspection found dark label shadows; the small follow-up removes
Explorer label shadows and selects contrasting foreground on hovered/selected
rows. That final visual retest, localization, and the 40-second freshness issue
remain explicit EF-A obligations. No human review or OS cursor was modified.

UI command contract (before registration): `sfm:explorer/search/focus
<find|filter|body>`, `search/toggle <find|filter|focused>
<case|whole-word|regex|fuzzy|dot-all|highlight>`, `search/clear <find|filter|focused>`,
`search/move <next|previous|next-wrapping|previous-wrapping>`, and
`search/scope <complete|materialized>` (all under `sfm:explorer/`). Commands act
on the captured current Explorer panel, reject a replaced/closed panel, and
preserve the older selector-based `find/*` and `filter/*` commands. Mouse controls
submit these commands; bindings use Explorer/Find/Filter focus scopes. Materialized
Find is an explicit reduced-scope choice, never a silent complete-domain fallback.

G4 implementation decision: the existing `SFMScreenRenderUtils.renderHighlight`
uses OpenGL `OR_REVERSE` with blue; applying it twice would not preserve a chosen
intersection colour. Explorer matching instead classifies disjoint UTF-16/code-
point-safe runs once, paints the resolved `search.find` (yellow), `search.filter`
(cyan), or `search.intersection` (purple) theme colour, and picks black/white text
by relative-luminance contrast after alpha composition. This preserves the
high-contrast intent without changing selection/diff/comment rendering. Unit
tests cover all four memberships, supplementary code points, context-prefix/
metadata provenance, explicit intersection override, and >=4.5:1 contrast for
the tested dark/light/selection backgrounds. Real theme/clipping proof is pending.

Checkpoints: `find-navigation-tests.ndjson` **5/5**, then initial UI suite **73/76**
(three tests still assumed hard-coded Ctrl+F or the pre-Find focus cycle). After
updating those contracts, `find-ui-client-suite.ndjson` passed **1616**, failed
**0**, prerequisite-aborted **1**, found **1617**, exit 0. Subsequent root/pending-
filter guards, descendant tooltips and default-binding regression tests require
another final run. `SFMExplorerPathReveal.preview` now leaves selection/filter
untouched and checks a generation guard before each additional page and callback.
The in-game exploration is underway, not yet accepted. Review/worktree capture,
multi-selection, localization and final readiness remain unfinished.

GUI-2 adaptive evidence: virtual-input session `20139756-45d7-497f-b33c-1dd5a366811c`,
34 steps, durable report `platform/minecraft/build/sfm-toolchain/artifacts/game-test-preview/runs/sfm-title_screen-20260906-154100-716/index.html`.
Steps 12/13 show Find `plan` revealing without changing the selected folder, then
Enter selecting the candidate; 15/16/18 show intersection, Find-only, and no
glyph highlight while matching/selection stay intact. Steps 20/21/24 exercise
regex-to-fuzzy exclusivity and independently focused Filter fuzzy. Full/split
GUI-2 controls were inspected through View Image; GUI-4/light proof remains.
The explicitly selected materialized scope is honest about filesystem limits.

Scale failure found, not waived: a new disposable review created from release
commit `31135b8e86801b862d5cb2283c7c5878b7cc5bb4` to current pin
`16328629fa60c45a4f525b6f20aaa77715f91077` reconciles 1,659 changes / 1,813 corpus
sides / 2,963 units, but its Explorer domain failed (step 29). Capture:
`platform/minecraft/build/ef-next-iteration-20260906/find-runtime.sfm-review.json`
(62,952,016 bytes), create command exit 0, about four minutes. The small pinned
review success is not release-scale acceptance. `SFMReleaseReviewScaleTests`
loads the opt-in `SFM_TEST_REVIEW_SCALE_FIXTURE` read-only, exercises root children
and exact filename search, and checks the file hash remains unchanged. Without
that explicit fixture the test is prerequisite-aborted, not claimed passing.
Finder failures now retain the loader's exact diagnostic instead of only
"domain was failed". Game PID 20644 exited normally through the exploration
`finish` operation; no OS pointer injection was used.

Scale diagnosis: `release-scale-tests.ndjson` failed 1/1 at
`SFMReleaseReviewSurfaceV1.Recipe`: constructing a diff for any source over the
existing 4 MiB per-side transport limit threw during whole-tree projection.
Fix keeps the limit and uses a typed `SourceLimitException`; only that file's
diff leaf becomes an openable `review.surface.source-oversized` explanation.
Other exceptions still fail honestly. Before/after identities and other files
remain present. A permanent model regression covers oversized and small sibling
recipes for both diff kinds; the opt-in scale retest is pending.

Retest 2 builds the tree and completes the search, but its initial expected-one
assertion was wrong: `git status --short` proves
`ExploreReviewInteractivelyPuppetAction.java` is **untracked**, absent from both
the current index and this pinned review. Corrected acceptance explicitly tests
one committed `SFMExplorerPanel.java` match and zero untracked-puppet matches.
This is a concrete EF-12 acceptance input: captured working-tree reviews must
include that untracked file, not relabel a commit-only domain as current files.

Scale freshness follow-up for EF-A/EF-12: the existing installed `review session
freshness --file .../find-runtime.sfm-review.json` returned correct exact-HEAD /
dirty-working-tree evidence, exit 0, but took roughly 40 seconds (NDJSON start
19:45:00Z, observation timestamp 19:45:40Z). It fully parses/validates the 63 MB
review via `read_review_document_at`, exceeding the Java probe's 30-second bound;
the visible UNKNOWN banner was honest but is not a satisfactory scale checkpoint.
Reduce unnecessary full-corpus work for the read-only freshness boundary while
preserving binding validation and source-race checks; do not weaken the deadline
or fake currentness. No persistent review was changed by the probe.

Current-source full Java checkpoint (not just client-filtered):
`SFM_TEST_REVIEW_SCALE_FIXTURE=.../find-runtime.sfm-review.json`
`sfm-propagate-changes.exe test run --branch 1.19.2 --wait-for-build-lock --log-filter info --log-file platform/minecraft/build/ef-next-iteration-20260906/find-ui-client-suite-2.ndjson`
passed **1,880**, failed **0**, prerequisite-aborted **1**, found **1,881**, exit 0.
This includes the corrected full release-scale search, oversized-diff sibling
regression, underlying Finder failure diagnostic and scoped binding assertions.
The one abort is the unconfigured installed-symbol-worker prerequisite. The
protected human review still hashes to `9ADDCCEC595CAA916EFDB8019270F62D577B336508D2608D84BDA2FE7AADD592`.
Current-source GUI-4 exploration is now launching; it is not yet proof.

**Work:** Ctrl+F focuses a non-hiding Find input; Ctrl+Shift+F focuses complete-
domain Filter. Add mouse controls, four movement actions, Clear menus and typed
scope/mode feedback. Apply the three-case initial anchor rule and generation-
bound navigation. Keep next/previous wrapping defaults; nonwrapping has explicit
boundary feedback. Extend existing `find/*` public grammar compatibly; new exact
action IDs and tooltips are recorded before registration. Close G4 and implement
fragment highlights plus descendant dot/unknown state as independent overlays.
Add focused Alt+F fuzzy and Alt+H highlight actions to each input and independent
theme-aware find/filter/intersection membership colouring. A manually configured
intersection is rendered once, not coloured again by two overlapping passes.

**Validation:** V1/V2 plus EF-A at GUI scales 2/4, split/full panel, dark/light
theme, multiple name matches and an active filter. Assert every anchor case,
forward/reverse/wrap/no-match, mode options, no accidental source selection from
typing, no filter reset, and no async viewport chase. Inspect screenshots through
View Image; isolated model tests do not prove colour or clipping.

**Completion criteria:** A user can find without hiding rows and can distinguish
self/descendant/both/neither evidence. Single-selection navigation is complete;
EFR-18/19 remain explicitly pending until EF-6/EF-7.

### [x] EF-6 Add interactive Explorer multi-selection through X-9

**Completion notes / G3 decision:** One session-owned `SFMSelectionId` in the
existing immutable selection repository stores row membership. Primary cursor
and range anchor remain separate session state. Add atomic replacement to the
repository (one revision, not remove-then-add). Projection/filter/collapse changes
never discard hidden membership. Plain navigation replaces; Ctrl navigation can
move only the primary cursor; Shift extends the anchored display-order range;
Ctrl+Shift adds or removes the entire range based on endpoint membership before
the action. Missing/hidden anchors use the endpoint, not a guessed range through
unloaded rows. Existing single-target open/node actions apply to the explicit
primary/clicked row and their labels/details must say so; no implicit bulk write.
Find capture uses all visible members; Alt+J adds one unselected match and
Ctrl+Shift+Alt+J replaces membership with all complete in-scope matches. Partial
or pending results refuse select-all. Membership revisions stay inspectable and
transaction snapshots restore the selection head alongside cursor/anchor.

Implementation checkpoint: added `SFMSelectionRepository.replace` and additive
`REPLACE` operation provenance (old selection archives remain readable; new
archives with this operation require this reader). `SFMExplorerRowSelection`
defines the pure gesture policy; the session stores only its selection ID,
primary and anchor, and snapshots read membership from the repository head.
`sfm:explorer/search/select <add-next|all>` supplies Find selection, and
`sfm:explorer/selection/row <replace|toggle|range|toggle-range|cursor> <path>`
exposes the row policy to the palette/CLI. Row details include membership ID,
revision, primary/anchor, count and bounded exact members with truncation flag.
Right-clicking an already-selected row preserves the set, explicitly targets
that row, and labels multi-selected menus as non-bulk. Primary outline and
multi-selection/off-screen count are visible UI affordances.

Raw mouse modifiers are captured at `MouseHandler.onPress` so virtual Ctrl/
Shift clicks do not consult OS key state; the unit-only direct-callback route
uses the same scoped modifier carrier. No OS cursor API was added. Initial
client checkpoint `multi-selection-client-tests.ndjson` passed **1,623**, failed
**0**, aborted **2**, found **1,625**, exit 0; aborts are the unconfigured installed
symbol worker and opt-in scale fixture. Subsequent row-action, primary rendering,
and production-panel pointer/Find regressions are under current retest. In-game
modifier/Alt+J/select-all proof is still pending; EF-6 is not complete.

The second client run passed **1,625**, failed **1**, aborted **2**: the new
production-panel Find test exposed URI-prefix reveal on a directly published
registry child (`.../item/minecraft/test_item_1` has no intermediate `minecraft`
row). The fix reveals an already-projected row directly, without resolver work,
and retains the guarded asynchronous path only for missing rows. This is a
production correction, not a weakened fixture. Third retest adds default Alt+J
bindings, JSON selection-archive roundtrip and nested/failed modifier scopes.

Third client run `multi-selection-client-tests-3.ndjson`: **1,627 passed, 0
failed, 2 prerequisite-aborted, 1,629 found**, exit 0. Includes direct production
panel pointer/range/right-click, hidden membership/primary details, additive and
all Find plus Enter replacement/invalid-query refusal, scoped keybindings and
JSON history roundtrip. Current-source GUI-4 runtime proof is now underway.

GUI-4 run `multi-selection-ui-exploration.ndjson`, report
`platform/minecraft/build/sfm-toolchain/artifacts/game-test-preview/runs/sfm-title_screen-20260906-161445-857/index.html`,
used disposable `find-runtime.sfm-review.json` (writable `/open/view`, no edits,
dirty remained false), process 21612, normal `finish` exit 0. Virtual Enter,
Alt+J twice, Ctrl+Shift+Alt+J produced 1→2→3→98 members (97 off-screen);
Shift+Enter replaced with one. Raw modifier pointer sequence produced
1→2→4→1 members; right-click preserved two and explicitly labelled the target
as row-only. Applying an exact filter preserved two hidden members. Screenshots
10/17 inspected. No OS cursor movement or human review mutation.

That run exposed stale "Selected all" status after replacement and Find consuming
an unrequested Filter projection. Clear action feedback on navigation and start
the Filter lane before Find consumption. New timing regression initially had
a missing test-only filter action application (`multi-selection-client-tests-4`:
1,627 passed, 1 failed, 2 aborted), then exposed a second rapid-query race:
an older materialized worker could overwrite a newer publication after its
early token check. Publication now uses a generation-checked atomic update.
`find-filter-timing-tests-2.ndjson` passed 1/1 with both fixes; the test deliberately
finishes Find before the first Filter request, then requires complete intersection
and exact select-all membership. Final current-source runtime retest and full
suite remain required; these are not yet a completion claim.

Verified runtime retest `multi-selection-ui-exploration-2.ndjson`, process 43148,
28 virtual steps, explicit `/open/read_only/view`, report
`platform/minecraft/build/sfm-toolchain/artifacts/game-test-preview/runs/sfm-title_screen-20260906-163119-294/index.html`,
normal finish exit 0. Steps 7–10 prove 1→2→98→1 membership with current feedback;
12/13 prove Find `Panel` intersected with literal Filter `SFMExplorerPanel.java`
is one **complete** match (not stale zero/incomplete). Step 22 selects that exact
file. Dark screenshot 13 and light screenshot 27 inspected: glyph intersection
is distinct, primary outline visible and the previous doubled shadow removed.
Light context-ancestor contrast is still weak and is retained as an EF-A polish
requirement; the matched text is readable. Restored the disposable theme file
byte-for-byte (`04FBF31D…3790A9C`); human review remains `9ADDCCEC…DD592`.
Selection slice is checkpointed; final client/full-suite run and localization
remain EF-A, not silently waived. The known release-scale freshness timeout
still requires the bounded metadata-only probe work under EF-12/EF-A.

Current-source client suite `multi-selection-client-tests-5.ndjson` completed
exit 0: **1,628 passed, 0 failed, 2 aborted, 1,630 found**. The explicit aborts
are the installed symbol-worker property prerequisite and opt-in release-scale
fixture; neither is counted as passing. This includes the generation-safe
Find/Filter timing regression. Final all-source acceptance remains EF-A.

**Work:** Close selection part of G3; separate set membership, primary row,
range anchor and deterministic traversal. Support ordinary pointer/keyboard
additive/range selection, Alt+J add-next and Ctrl+Shift+Alt+J select-all highlights.
Plain click replaces; Shift+click replaces with anchor-to-click range; Ctrl+click
toggles one member without dropping others. Recommended Ctrl+Shift+click policy:
if the clicked endpoint was unselected, add the whole anchored range; if selected,
remove the whole range. Do not invert each member independently. Keep the anchor
stable during modified clicks and record this reversible resolution of EFR-46.
Keep Enter replacement and Shift+Enter previous semantics. Hidden selected paths
survive filter/layout changes; actions declare one-versus-many target support
and never mutate a silently chosen member. Add inspectable selection details.

**Validation:** V1; membership/anchor tests, duplicate/overlap handling,
visible/hidden/disjoint matrix, partial select-all/refusal, multi-panel isolation,
and safe single-target action behaviour. EF-A tests actual shortcut dispatch.

**Completion criteria:** Multi-selection is interactive, survives projection
changes and has the user's exact find shortcuts without a second selection store.

### [x] EF-7 Apply match-selection actions to the 2D editor

**Completion notes:** Claimed only after the core runtime checkpoint below.
Reconnaissance found that exact selection checkout places carets and paints rows
using fixed line heights, while the model already has an immutable glyph index
with actual x/y/width. Also, ordinary typing currently inserts at exact-range
endpoints rather than replacing ranges. This stretch must cover geometry and
selection-aware edits, not merely wire Alt+J to a list of byte offsets. Reuse
`SFMTextMatcher`, `SFMExactDocumentSelectionPublication`, the existing context/X-9
projection and generic document history. Add pure geometry/match/edit tests
before runtime wiring. No new selection store or dependency is authorized.

Implemented bounded policy: an explicit per-editor query wins; otherwise seed
from the primary nonempty exact selection, retaining its literal text (not
interpreting metacharacters as regex). Empty selection asks for a selection or
query rather than guessing a nearby word. Add-next wraps, skips existing or
overlapping selections and preserves disjoint members; select-all replaces with
the complete bounded match set. Regex zero-width matches are carets; fuzzy
approximate evidence must remain disclosed. Limits or stale geometry produce
an explicit unchanged result, never a partial all-selected success. Public
query/options/select actions and localized context affordances accompany the
remappable TEXT_EDITOR hotkeys; Explorer bindings remain scoped independently.

First geometry/edit checkpoint: `editor-match-edit-tests-3.ndjson` passed **72/72**
Java tests (exit 0). New pure tests cover query seeding/add-next/all, Unicode/CRLF,
fractional glyph rows/unequal widths, inferred spaces, multi-range replacement,
cross-row joins and unchanged refusal of overlapping/composite-glyph edits.
The first wiring compile caught two String-vs-Component errors; both were fixed
before this passing run. `SFMTextCoordinateIndex` now shares one exact source
pass across endpoints; selection rectangles coalesce adjacent actual glyphs.
Datagen passed (`editor-match-datagen.ndjson`, exit 0). The first full suite
found 1,917 tests: 1,915 passed, one failed, one expected worker opt-in aborted.
The failure was the established malformed-range diagnostic losing the word
"outside", not accepting the invalid range; `SFMTextCoordinateIndex` now keeps
that precise outside-0..N message. The corrected complete suite passed as
`editor-match-full-tests-2.ndjson`: **1,917 passed, zero failed, one configured-
prerequisite abort, 1,918 found**, exit 0, with the 62,952,016-byte disposable
review fixture. This run includes the subsequent integration fix below.

Real virtual-input exploration (`editor-match-runtime-2.ndjson`, CLI exit 0,
JVM 22980 normally stopped) opened an explicit V3 scratch editor, selected `hi`
in `hi hi hi`, used Alt+J and Ctrl+Shift+Alt+J, replaced all three with `yes`,
and restored the original three ranges with two ordinary undo chunks. Redo
restored `yes yes yes`. The initial replacement character is a separate history
chunk from following letters; that existing policy is now explained in the guide.
Evidence: `platform/minecraft/build/sfm-toolchain/artifacts/game-test-preview/runs/sfm-title_screen-20260906-201149-272/index.html`,
figures 11, 13, 15–18 and 21. The saved default chose V1 on Alt+D, so the guide
now uses the existing explicit V3 argument without changing user preferences.
The generic editor panel title misleadingly says V3 for V1 too; track that
presentation cleanup separately, not as proof of editor implementation identity.

The same exploration exposed a real context integration omission: only two
executable selection choices were visible; query/option continuations lacked
`SFMClientActionCompletion` opt-in. The action now opts in only to its exact
query/toggle prefixes; Brigadier still owns parameter construction. A new
prefix-contract test covers that boundary. `editor-match-runtime-3.ndjson`
(normal exit 0) verified all four entries, query construction's named argument
hint, all five option suggestions, actual query submission and all-match
selection without a pointer seed. Invalid `[` regex remained in the palette
with a visible error and unchanged canvas. Evidence is in
`platform/minecraft/build/sfm-toolchain/artifacts/game-test-preview/runs/sfm-title_screen-20260906-202146-527/index.html`,
figures 4, 7, 11, 16 and 20.

Final negative-case pass preserves the prior selection on zero matches and
refuses add-next overflow of the 4,096-selection budget. Shared dynamic
keybinding dispatch drops generic feedback, so this action additionally uses a
per-panel replacement-key workspace toast for pending/result/failure messages.
`editor-match-full-tests-3.ndjson` passed **1,918 tests, zero failed, one expected
worker opt-in abort, 1,919 found**, exit 0, full review-scale fixture enabled.
The final current-Java feedback runtime proof passed as
`editor-match-runtime-4.ndjson`, normal exit 0: Alt+J without a seed showed
"Find selection unchanged: Select text first, or set this editor's Find query";
explicit `hi` query and Ctrl+Shift+Alt+J showed "3 ranges selected; 3 matches";
typing `yes` replaced all three; two undos restored the original text/ranges.
Evidence: `platform/minecraft/build/sfm-toolchain/artifacts/game-test-preview/runs/sfm-title_screen-20260906-203205-200/index.html`,
figures 4, 8, 9 and 11. All injection was virtual, with OS pointer injection
false. JVM 40652 shut down normally; no Java process remained at this checkpoint.
The preview profile still emits unrelated existing third-party startup warnings
and errors; no new SFM crash/failure is claimed away by the CLI's exit status.

EF-A renewal: generated localization and changelog are current; the guide names
the explicit V3 opening, boundaries and history chunks. Rust was unchanged in
this stretch, installed CLI SHA256 remains
`C7259EB80429EBE5A4CDB335660165BE60752A7FF88D500E589349FD5C6ECCB0`,
**user install required: no**. The human review hash remains
`9ADDCCEC595CAA916EFDB8019270F62D577B336508D2608D84BDA2FE7AADD592`.
No dependencies, lockfiles, user preferences, commits or pushes changed.

Bounded edit adapter decision: ranges replace atomically in a draft glyph list,
retaining physical seam offsets; ambiguous composite-glyph cuts or spatial
collisions refuse unchanged. It caps source/replacement/result at 32,768 UTF-16
units, 4,096 ranges and 1,000,000 transformation steps. This does not reduce
read-only preview or selection limits. Recent edit geometry is a disposable
16-state/131,072-glyph presentation cache used by existing history checkout;
logical text/range history remains authoritative, with ordinary linear layout
as the fallback after cache eviction. Persistent arbitrary-canvas geometry
history is not newly claimed. No new selection or revision store was added.

**Work:** Reuse matching options and X-9 adapter for Alt+J/select-all highlights
in text editor scope, including seeding a query from selected text where no find
query exists (record exact empty/multiple-selection seed policy). Map text matches
to existing document/glyph surfaces, not fixed line-height cells. Preserve
multi-cursor edits, selection undo/history and exact byte/document revision.

**Validation:** V5; text and pointer-selection tests plus unequal glyph widths,
Unicode/supplementary characters, moved/irregularly spaced glyphs, overlapping
matches, disjoint selections and edits after a find. EF-A shows both Explorer
and editor receive the same intended action in their own scopes.

**Completion criteria:** Match selection works in both surfaces with correct
geometry and history; it is not only an Explorer convenience.

### [x] EF-8 Implement the bounded regex and dot-all matching engine

G2 engine decision (2026-09-06): implement a small operation-counted Java NFA
interpreter, using prioritized alternatives to retain greedy/lazy semantics.
This requires no dependency and remains available without the Rust companion.
The design follows the instruction/state model described by
[Russ Cox](https://swtch.com/~rsc/regexp/regexp2.html); do not copy its C code.
Every compile/execute transition consumes a budget; epsilon cycles are visited
once per position. A Future timeout is not the stop mechanism. Bound pattern
length, group depth, repetition expansion, states, result count and aggregate
query work. Cancellation is checked within matching, not only between files.

Initial supported subset: Unicode literals, escaped punctuation, dot, character
sets/ranges/negation, `\d/\D`, `\s/\S`, `\w/\W`, `\b/\B`, `\n/\r/\t/\f`,
`\xHH`/`\uHHHH`, grouping/`(?:...)`, alternation, greedy/lazy `* + ? {m,n}`,
line anchors `^/$`, and absolute `\A/\z`. No backreferences, lookaround,
capture substitution, named groups, class intersections, inline mode flags or
Unicode property escapes; unsupported syntax is an error, not a literal guess.
Groups affect matching but do not yet publish capture groups. Comparison tests
use [Java Pattern](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/regex/Pattern.html)
only as an oracle for the shared subset, not as the production matching engine.
`^/$` use line boundaries with CRLF treated as one break; dot-all includes line
breaks. Whole-word uses the existing shared Unicode/underscore predicate.
Zero-width matches advance by one Unicode code point (including a terminal EOF
match), have explicit zero-width evidence and never fabricate a glyph selection.
Find/Filter input affordances remain EF-5; regex must pass these engine tests
before its mode is advertised there.

Engine checkpoint: `SFMRegexPattern` implements the subset above with immutable
compiled instructions, iterative per-position epsilon closures, 1024 states,
24 group levels, repetition counts up to 256, and a 10,000-operation compile
budget. `SFMMatchBudget` is consumed synchronously and checks cancellation inside
matching; there is no detached timeout worker. Each direct match is capped at
1,000,000 operations, release-review queries share 20,000,000 across fields,
and uncached projection work shares 1,000,000. Prepared field/fragment evidence
travels from the background review resolver through the query-specific cache
to projection, avoiding repeated regex work during rendering/scrolling. A mode,
query, or resolver-generation change invalidates this prepared evidence. Bounded
queries publish explicit incompleteness; cancellation stops rather than consuming
the rest of the field list. Query options/whitespace survive settings, command
transport and cache identity; legacy wrapper calls still strip as before.

One deliberate anchor policy differs from Java's multiline `^`: SFM treats the
empty document and final empty line as real line positions, so `^$` matches at
EOF after a final newline. The shared-subset oracle tests exclude that difference
and dedicated assertions prove it. Zero-width occurrences are separate offsets,
not zero-area `Fragment`s, so callers cannot confuse them with selected glyphs.
First matcher run caught this explicit policy mismatch; after separating the
oracle domain, `--filter client.search` passed **13/13**. Full client integration
(`regex-client-suite.ndjson`) passed **1604**, failed **0**, prerequisite-aborted
**1**, found **1605**. Final prepared-evidence/whitespace regressions passed in
`regex-client-suite-2.ndjson`: **1606 passed, 0 failed, 1 prerequisite-aborted,
1607 found**, exit 0. The skipped installed-symbol-worker integration requires
explicit executable/branch properties and is unrelated to matching. Controls
still belong to EF-5; this checkpoint does not claim their in-game UI has been
delivered.

**Work:** Close remaining G2 with a real enforceable matching budget; existing
dependencies only. A timeout on a Future alone does not prove a matcher stopped.
EF-5 must expose Regex and Dot-all next to case/word controls only after this
gate passes; dot-all is disabled with explanation outside regex. Preserve pattern text;
surface invalid/unsupported patterns, work limits and partial results. User's
paragraph intent needs multiline matches. `[.\n]` means literal dot or newline;
the intended dot-all example is `.*?\n\n`, with explicit LF/CRLF handling.

**Validation:** V1/V5 plus matcher-level adversarial tests: long nonmatches,
catastrophic-backtracking candidates, cancellation/work-limit proof, zero-length
progress, Unicode boundaries, multiline paragraphs and option combinations.
Do not introduce a new regex dependency without separate authority if existing
facilities cannot meet the contract; record supported pattern restrictions.

**Completion criteria:** Matching options and multiline paragraph ranges are
accurate in bounded, cancellable engine tests; unsupported syntax fails explicitly.
No pattern can create unbounded render-thread or worker matching work. The original
discoverability and multiline-selection acceptance is retained under EF-5/EF-A
and EF-7 respectively, rather than treating an engine test as UI delivery.

### [x] EF-9 Compact complete single-child chains with local overrides

**Completion notes:** Claimed after the verified EF-7 checkpoint. First close
G3's persistence and projection boundary against the actual source, then add
pure complete/partial/filtered topology and anchor fixtures before UI wiring.
`SFMExplorerProjection.Row` currently names one path; compaction must retain
structured intermediate path aliases rather than encode them in a display string.
`SFMChildRelationRepository.PageState` supplies materialization/completeness and
continuation evidence. Existing file Explorer reopen recipes currently retain
the source address but not presentation preferences, so that persistence seam
must be extended explicitly; no preference is stored in a review/comment file.
G3 compaction decision (2026-09-06): retain typed entry segments on a projected
row; ordinary row activation uses its terminal container and all intermediate
paths remain aliases for selection/find/anchoring. Only a materialized COMPLETE
page without continuation and exactly one actual child, both explicitly typed
containers in the same address authority/revision, certifies a fold. Filtered
visible-child counts never certify topology; unknown metadata declines folding.
Shown roots stay separate. Flat grouping does not compact. Sort follows the
first segment; the terminal container supplies the icon and expansion state.
The immutable settings value owns panel-default enabled plus exact parent-edge
overrides. Reopening captures this value; other panels receive independent copies.
Source inspection corrected the earlier "existing workspace/preset" assumption:
there is no general disk workspace preset store. Add an explicit bounded,
versioned compaction preset export/import through existing clipboard/action
surfaces, so the user can save the text as a file and reapply it after restart.
Do not invent an ambient appdata preference file or mutate review JSON. Presets
contain only the default and canonical-path overrides, not review/source bytes;
missing/renamed paths remain inert exact overrides until reset. The guide must
make the explicit save/apply workflow and non-automatic restart behavior clear.
This reversible, narrow preset boundary meets persistence without claiming a
general saved-workspace system. No EF-9 acceptance is claimed yet; EF-10 remains
unstarted.

Implementation checkpoint: `SFMExplorerCompaction` and immutable Settings now
retain exact segments/overrides; `SFMExplorerPanelModel` restores a logical scroll
anchor without selection edits, and finder/highlight/inspection adapters retain
intermediate identities. New named `sfm:explorer/compact/{set,unmerge,reset}` and
`preset/{copy,apply}` actions are panel-scoped. Typed reopen recipes decorate
source reconstruction rather than replace it. The preset parser is streaming,
128-Ki-character/4096-override bounded, rejects unknown/duplicate/nested fields,
and performs no IO or evaluation. Review search pages with request ID 0 describe
complete *matches*, not full child lists; added a resolver-attested full child
count (`sfm:subject/complete-child-count`) and refuse query-page compaction without
that attestation. Full topology, partial/unknown, filter subset, repeated-name
glyph, anchor, immutable preset and panel/reopen fixtures are present.
`compact-tests-1.ndjson` exposed three new-fixture mistakes (canonical path also
matched `sfm`, and self-edge construction was invalid); fixtures now use explicit
name search terms and a real two-node cycle. Run 2 caught a missing ArrayList
import. Run 3: 1927 passed, two UI-text/golden assertions failed, one existing
opt-in abort; topology/preset/highlight tests passed. Run 4 is pending after
updating truthful compact counts/diagnostic fields, with two further alias-reveal
and root-boundary edits requiring the final acceptance rerun. Run 4's only two
failures were a shared fixture's uppercase registry-path segment (registry IDs
must be lowercase); corrected without changing production semantics. Full
current-source `compact-tests-5.ndjson` exited 0: **1931 passed, 0 failed, one
existing opt-in abort (1932 found)** with the full review scale fixture enabled.
This includes disjoint selection/local reset and portable preset/reopen isolation.
Datagen and real runtime exploration remain required; no completed EF-9 claim yet.

First runtime `compact-runtime-1.ndjson` (JVM 23780, bridge
`9920e380-16a8-4747-a2db-77963d014d88`) rendered `java/ca/teamdman` and truthful
7 compact / 9 logical rows for literal `SFM.java`, then failed on row right-click:
the inspection adapter rejected query-page request ID 0. Added that exact
regression (negative IDs still rejected), and exploration failures now retain
their cause chain and emit the full exception through SFM.LOGGER. This test-only
bridge caught the exception; normal process exit 0 is NOT menu acceptance.
Artifact `sfm-title_screen-20260906-212132-193`, figure 12 records the failure.
Also observed existing `review/session/open/view` accepts a raw greedy path,
not quoted syntax; this differs from working-tree creation's separate quoted
arguments. Keep the manual guide explicit; parser unification is a follow-up.
`compact-datagen.ndjson` succeeded (51.4s); the new menu labels exist in generated
en_us.json. Renew full tests and live menu checks after the inspection fix.

`compact-tests-6.ndjson`: 1932 passed / 1933 found, 0 failures, one opt-in abort.
Second live attempt reached the next guard: `Explorer request must agree with
its row inspection` because the compact display label differs from the terminal
entry's name. Context identity now checks the exact Explorer/path/entry addresses,
not presentation-label equality; the integrated compact-row context/choice test
also rejects a mismatched target path. Runtime 2 (JVM 30044) stopped normally
after recording the failure, not counted as acceptance. Full suite/runtime must
be renewed again.

**Verified final checkpoint (2026-09-06):** Full current-source
`compact-tests-7.ndjson` exited 0: **1,933 passed, 0 failed, 0 skipped, one
existing opt-in abort (1,934 found)**, 347 containers, 1m46s. This includes both
runtime-discovered context failures above. The abort remains the unconfigured
installed-symbol-worker integration, not a claimed navigation pass. Datagen
`compact-datagen.ndjson` remains current: subsequent fixes add no language keys.

Final GUI-2 live session `411323b3-f1f1-45cc-8889-be2152839d59` (JVM 3004,
48 virtual steps, `compact-runtime-3.ndjson`) exercised the real row menu,
mouse unmerge/reset, stable first-segment screen anchoring, Find of intermediate
`teamdman`, and preserved exact selection. Copy-preset used the game's clipboard
action, then saved that actual JSON to the ignored
`platform/minecraft/build/ef-next-iteration-20260906/compact-review-preset.json`.
Duplicating/reopening retained the local overrides; applying defaults to the
right Explorer left the left Explorer's nine unmerged rows unchanged while the
right showed seven compact rows. A third, disposable filesystem Explorer loaded
`ca/teamdman/sfm`, collapsed the terminal children and reopened the known complete
chain/leaf with one mouse click. No source or review write was required.
Artifact `sfm-title_screen-20260906-214814-661/index.html`: figures 6–12 mouse
menus/anchoring; 16 intermediate matching; 30 independent split preferences;
47 real-filesystem one-motion expansion. Handled invalid diagnostic command
spellings (trailing file-URI slash and exact-selector/open-new mismatch) are not
counted as successful actions; the corrected commands were executed afterward.

Separate GUI-4 JVM 38316, session `d93a7f1b-0a3f-4758-a342-be600fdbb8e4`,
`compact-runtime-4.ndjson`: figure 8 shows seven default compact rows; figure 11
shows nine unmerged rows after applying the same preset read back from disk;
figures 12–14 use the mouse context menu and Execute button to reset that subtree
to seven compact rows, preserving exact primary selection. Figure 16 is the
clean final view. The first four requests used an incorrect exploration request
field (`command` instead of `text`); they are harness-driver mistakes, not review
acceptance. The corrected public-input journey begins at request 5. Both final
JVMs used normal `finish`, both CLI processes exited 0, and no Java process remains.
Final GUI-4 artifact:
`platform/minecraft/build/sfm-toolchain/artifacts/game-test-preview/runs/sfm-title_screen-20260906-215547-758/index.html`.
Every request reports `os_pointer_injection=false`; the disposable review remains
dirty=false with 2,963 existing fixture comments. No SFM exploration failure or
client crash appears in the final console; unrelated startup mixin metadata,
AE2 guide and missing-texture errors remain visible and are not hidden as passes.

EF-9 is complete at this bounded checkpoint. Individual segment hit targets and
create-child authority remain EF-10, deliberately unclaimed; unmerge first to
act on an intermediate folder. Preset persistence is explicit save/apply, not
automatic workspace restoration. The guide states both limitations.

**Work:** Close compaction part of G3. Add per-Explorer default merge preference,
exact per-path unmerge/reset override, stable path segments and workspace/preset
persistence. Implement the anchoring policy above and one-motion expansion.
Existing root hoisting remains independent. Reuse lazy loader completeness;
never fetch arbitrary descendants merely to decide a row's label.

**Validation:** V1; chain/branch/paged/unknown/filter-limited topology matrix,
two independent Explorers, override persistence, renamed/missing paths,
disjoint selections and resize. EF-A tests local unmerge by mouse without a
large scroll jump and no cross-panel preference mutation.

**Completion criteria:** `ca/teamdman/sfm` can collapse visually without losing
identity, and the user can unmerge just that relationship and restore default.

### [ ] EF-10 Target exact compact-path segments by mouse and action

**Handoff:** Next eligible stretch, not implemented or claimed in the completed
iteration. Generic filesystem Explorer access is currently read-only; inspect
the existing capability boundary before offering create-child. Do not turn the
source browsing grant into an implicit write grant just to satisfy a menu.

**Work:** Add segment hit bounds and localized menus using real path/metadata
capture. Right-clicking `teamdman` supplies that path to existing copy/reveal/
open/create-capable actions, not `sfm`. Make keyboard access to intermediate
segments possible through the same choice/action surface. Resolver capabilities
control creation; read-only review containers must not acquire write authority.
If create-child is missing, record/add a bounded authorized filesystem action
with its own no-overwrite contract, not hidden icon/row side effects.

**Validation:** V1 and EF-A; variable width glyphs, padding/separators, clipped
and merged labels at GUI 2/4, stale context, intermediate child creation only in
a disposable writable filesystem root, refused creation on review/registry paths.

**Completion criteria:** A segment action and an unmerged-row action have the
same exact target; creating a child cannot accidentally occur at the deepest node.

### [x] EF-11 Specify live selector and immutable working-tree capture adapter

**Completion notes:** Reconnaissance confirms the embedded `SnapshotV1.id` is a
general immutable string identity and comment evaluation already pins document
IDs/hashes; only the release envelope's repository binding requires Git SHA-1
candidate fields. Preserve the old Git/Git wire form and semantic hash. A new
explicitly versioned working-tree binding must not forge Git IDs or reinterpret
old fields as content hashes. The adapter design and golden fixtures are the
current gate; no working-tree runtime is claimed yet.

Adapter contract is now in `docs/architecture/release-review-working-tree-v2.md`:
additive release-envelope v2, unchanged v1 canonical form/hash, exclusive real-Git
or captured-source binding, length-framed cross-language SHA-256 identity,
explicit source scope/exclusions, verified two-pass observation, and bounded
portable bodies. `release_review_capture.rs` and `SFMWorkingTreeCaptureV1` plus
their codec/standalone golden tests implement the manifest boundary only.
Validation is pending; EF-12 producer/envelope/runtime remains unstarted. Prefix
validation uses segment lookup rather than quadratic scans at the 100k bound.

G5 contract gate passed: `cargo test --locked --offline release_review_capture
--lib -- --nocapture` **5 passed**, and installed CLI `test run --branch 1.19.2
--filter SFMWorkingTreeCaptureV1Tests --wait-for-build-lock` **4 passed**
(`working-tree-capture-client-tests.ndjson`, exit 0). Shared canonical fixture
hash is `b4744d1e…c6ba6fc`; Unicode UTF-8 ordering/byte framing, source-policy
refusals, null/absent option canonicalization, and time/diagnostic-independent
identity are frozen. Initial Rust test caught Facet scalar coercion (`"true"`
accepted as Boolean); raw token validation now matches Java's strict types.
Parent evaluator owns exact/changed/missing/ambiguous semantic outcomes; concrete
capture-body/envelope, mutable-source, beyond-EOF and runtime persistence proofs
remain EF-12, not claimed by this manifest-only gate.

**Work:** Close G5 using parent X-9/comment/revision contracts. Specify portable
storage, canonical identity, source authority, capture consistency, limits and
explicit migration. Preserve Git v1 reader/approval compatibility. Distinguish
staged/unstaged/untracked scope, modified/deleted paths, ignored/secret exclusions
and symlink boundaries. Decide whether existing snapshot infrastructure can
safely store captured bytes in the selected review file; no enormous-file UI
virtualization or appdata-only hidden review authority is implied.

**Validation:** Cross-language golden design fixtures and source-owner matrix;
same path at two commits, path-live selector before/after mutation, changed field
type/name/overloads, beyond-EOF range, untracked file and removed file. Review
the proposed format/migration against `release-review-v1.md` and parent plans
before implementing; a material incompatible schema decision requires direction.

**Completion criteria:** A fresh implementer knows exactly what moves versus
what remains pinned, how to resume the file elsewhere, and why approval cannot
follow mutable paths automatically. Schema proposal is not runtime completion.

### [x] EF-12 Enable explicit working-tree review capture and resume

**Completion notes:** Additive source-binding/codec integration, bounded
Windows producer/CLI and in-game creation/comment/save/restart are verified.
Independent JVM 23912 reopened the copied portable review at GUI scale 4:
two comments, writable, `dirty=false`, exact saved capture query/current-unit
identity retained. Evidence is `session-59001e8d-29ad-4a1d-bd98-a932e08764c3`
response 2 under the exploratory bridge. This closes the restart requirement;
the earlier pending notes below describe intermediate checkpoints, not current
obligations. Rust disposable-repository tests separately prove later edit/delete,
staged-versus-disk/untracked capture and no inherited approval. The explicit
field fixture proves the prepared-evidence boundary, not a new live-field parser.

Latest runtime checkpoint (supersedes the earlier stale-tool/pending-creation
notes below): installed CLI SHA256 is
`783875191233BA9D526892734BBFC7EE944F2C540FCD8A59AC4DA66285B946C9`,
version `0.1.1 (rev 16328629f, built 2026-09-06 18:02:43 -04:00)`.
Java/datagen sources are current. A separate preview JVM 25920 opened the
in-game-created capture, found the exact untracked filename, opened After and
selected all 21,364 UTF-8 bytes through real Ctrl+A in **17,710 µs** dispatch.
Alt+Enter → exact selected bytes → #approved saved in **13,911 µs** dispatch;
these are individual observations, not stable benchmark guarantees. The
portable review now has two comments and `dirty=false`; the exact comment
range is `[0,21364)` with document/selection hash `d8369b13…967bc`.
Normal bridge `finish` closed that JVM, puppet `failed=0 total=1`.
Responses and 19 preserved images are under
`platform/minecraft/runGameTestPreview/sfm-puppet/exploration-control/session-05e8b8e3-fad1-4e01-80f8-be398f60ba74/`.
An independent CLI process read `working-tree-portable-copy.sfm-review.json`,
returned that exact approved range for the explicit capture ID, and returned
zero units for `(1.19.2 candidate) difference effective(#approved)`.
This approval is synthetic test evidence in ignored disposable files only;
no human release approval is implied. Separate-JVM reopen of the commented
portable copy and final all-source suite remain pending.

New invalid-EOF regression preserves invalid comments for inspection but
requires evaluation `INVALID`, no ranges and a diagnostic; rejecting the
entire review would contradict the existing inspectable-invalid-target model.
Added a prepared-symbol boundary fixture with actual `DiskItem.capacity` field
declarations before/after: same selector is exact before, changed after,
ambiguous without revision scope and missing if the after candidate is absent.
These additions are in the current full-suite run, not yet claimed passing.

Final core-source Java suite `search-capture-full-client-tests-3.ndjson` passed
**1,903 tests, zero failures, one existing opt-in integration abort (1,904
found)**, exit 0. The abort requests `sfm.symbol.workerExecutable` and
`sfm.symbol.workerBranch`; it is not a test failure or silently excluded case.
The real 62.95 MB review scale fixture was enabled. Both invalid-EOF and
field-at-two-revisions regressions above passed, as did the localization audit
and readonly exact Ctrl+A regression. No Rust source changed after its full
check-all/install checkpoint.

Envelope checkpoint: existing Rust release-review unit group passed **44/44**;
new capture/working-tree group passed **7/7**. Java `SFMWorkingTree` group passed
**6/6** (`working-tree-envelope-client-tests-2.ndjson`, exit 0), including the
same canonical mixed Git/capture fixture and semantic hash
`c3f1c531…6333bd`. Initial fixture lacked a review unit for its new Unicode file;
both kernels correctly refused it, and the fixture was corrected. Candidate
source choice, corpus/body/manifest binding, exact source-ID query aliases and
collision guards are implemented; old v1 golden bytes remain unchanged.
Producer checkpoint: `cargo test --locked --offline --lib
release_review_working_tree` passed **4/4**, including immutable materialization
after a later disk edit, staged-versus-disk content, untracked/deleted/binary
paths, ignored/secret exclusions, cancellation and two-pass race refusal.
CLI group passed **10/10**, including repeated scopes, actual disposable
Git/working-tree creation, unchanged index/HEAD and no-clobber output.
Review group subsequently passed **51/51**, including metadata-only source
bindings, shared v2 semantic hash and unchanged v1 canonical fixtures.
New `review/session/create/working_tree` is registered in Java with explicit
output/lane/before/scope/root parameters and a bounded background producer.
The lens menu continuation and scoped live comparison are now being validated;
Java focused run `working-tree-ui-action-tests.ndjson` compiled but reported
**156 passed, 1 failed, 1 aborted**. Investigate the exact test failure before
accepting this UI checkpoint. No game runtime proof is claimed yet. Rust CLI
installation remains stale after these changes; final check-all/install required.

Second Java creation/action run passed **158**, failed **0**, aborted **1**
(opt-in scale fixture not configured; `working-tree-ui-action-tests-2.ndjson`).
The failure was the new grammar fixture supplying an unquoted slash-containing
scope to Brigadier's string argument; corrected quoted fixture and added quoted
scope suggestions. Additional Windows capture tests passed **6/6**, proving
mode-only changes, staged deletion with retained disk bytes, oversized refusal,
and an outside-scope commit not making captured source stale. Rust clippy passed.
Full `check-all.ps1` then found the old standalone materializer integration
test's missing capture module and obsolete nonoptional candidate fields; fixed
those test adapters, full rerun still pending.

Measured current debug executable metadata-only freshness on the existing
62,952,016-byte `find-runtime.sfm-review.json`: **2,491 ms**, exit 0, real pinned
HEAD and `source_dirty=true` (the previous full-corpus route took about 40s).
This is one observed check, not a timing benchmark guarantee. Captured-source
freshness separately compares the declared manifest and ignores out-of-scope
commits; Java tests for current/changed/unknown scope evidence are added but not
yet rerun. No human review file was modified.

Complete Rust `check-all.ps1` rerun passed: clippy/build, **700 unit tests**
(3 existing environment/network opt-ins ignored), **10 Java-analysis scenario**
tests, **12 Git review** integration tests and **40 materializer/codec** tests.
The unit group includes a real disposable Windows junction escape test; it did
not read through the junction, removed only that test junction and preserved its
outside sentinel. Reserved device paths are refused before opening. No unsafe
OS mouse input was involved. Java capture freshness group passed **4/4**
(`working-tree-freshness-tests.ndjson`).

Real-source CLI proof: created only
`platform/minecraft/build/ef-next-iteration-20260906/working-tree-real-source.sfm-review.json`.
Capture `working-tree:sha256:5a7316aa39af4aad51b90b784a187abc5e754369067c3bcc07d696e189df0f94`
contains the actual untracked `ExploreReviewInteractivelyPuppetAction.java`
(21,364 UTF-8 bytes), one complete after surface, no unsupported surfaces.
`candidate intersect 1.19.2` returns that unit; live freshness reports
`capture_scope_matches=true`. The protected manual review hash remains
`9ADDCCEC…D592`; dependency/lockfile diff is empty. Installer is running for
the in-game acceptance checkpoint; no runtime creation/comment/restart proof yet.

Interactive checkpoint exposed a real Ctrl+A stall on the captured 21 KB Java
preview. JVM 7912 render stack was inside `ensureCursorClosestToEachGlyph` →
`cursorClosestToGlyph` → `nearestGlyph`, repeatedly scanning the full document
as cursors grew. Stopped only that proven preview JVM after capturing the stack;
the review was already saved and no comment was pending. Read-only Ctrl+A now
uses the existing exact source-range/pointer-selection publication with one
cursor, including Unicode, CRLF and EOF, and clipboard copying honors exact
published selections. Mutable/standalone canvas cursor-expansion optimization
remains under EF-7 rather than changing its editing semantics in this fix.
Regression and restarted runtime proof pending. The new creation action itself
worked in game, and literal Find returned the one real untracked source.

**Work:** Implement the approved EF-11 adapter and mouse/action routes. User
chooses bounded source scope/output; capture tracked plus allowed untracked
content into immutable evidence with retained live intent where requested.
Detect concurrent source changes during capture; refuse or retry boundedly.
Provide explicit commit-before/working-tree-after selection including untracked
files. Persist exact captured bytes and source-kind identity; do not fabricate
Git commits/trees or write the real index to make the existing model accept it.
Preserve old reviews, comments and proposed migrations; never auto-stage/commit.
Display pinned vs live versus newly captured state and exact review denominator.
Prove agents can inspect/query saved user comments through the same portable
review and existing structured CLI surfaces, without manual copy/paste.

**Validation:** V3/V4/V5; disposable repo and review, capture untracked Java,
comment exact selected bytes, close JVM, reopen same portable file, query
approval intersect the explicit captured revision and remaining area, then
change/delete source and prove original evidence survives without new approval.
Exercise `hi.txt` beyond-EOF and DiskItem-style field selectors at two revisions.

**Completion criteria:** Untracked code is genuinely reviewable and resumable;
changed content cannot inherit approval by sharing a path or field name.

### [x] EF-A Integrate, explore adaptively, and hand off the claimed checkpoint

**Completion notes:** Core claims EF-1–EF-6, EF-8, EF-11/EF-12 only. Final Java
suite and Rust full check/install are green as recorded above. Datagen
`search-capture-datagen.ndjson` exited 0 and generated the shared search/source
language keys; `SFMExplorerSearchText` supplies a headless English fallback and
passes the localization argument audit. The changelog and
`docs/explorer find filter and working tree review guide.md` describe the actual
core flows and explicitly leave editor match-selection/compact chains as
stretches. Final GUI-4 light-theme check is in progress; only the ignored preview
theme was temporarily changed (three colours), and it must be restored to SHA
`04FBF31D2181BBF70E71AD3C270EFC081D2BDE0662D47E0D36062C3873790A9C`.
All inputs remain virtual. Human manual-review hash and frozen lockfile checks
remain unchanged. Expected third-party missing-texture/Realms startup noise was
inspected; no SFM runtime exception occurred in the completed save journey.

Final GUI-4 light check (session `59001e8d…64c3`, image 11) verified readable
context ancestors, cyan Filter-only glyphs and purple Find/Filter intersections
on the selected row. Preview theme was restored byte-identically to the hash
above; normal `finish` closed JVM 23912. That exploration found a copied-review
freshness bug: a renamed review no longer matched the original evidence path,
and a `.` root hint resolved beside the review rather than to the Git top-level.
The CLI now performs bounded read-only top-level discovery only within that
hinted repository; failure still yields unavailable. A real copied/nested
working-tree fixture checks scope equality, no review rewrite and unchanged
Git HEAD/index. Full Rust check-all/install and runtime freshness recheck are
required again. No stretch is claimed until this core acceptance repair passes.

Nested-root repair passed a complete offline `check-all.ps1` rerun: clippy/build,
700 unit tests (3 existing opt-ins ignored), 10 Java-analysis scenarios, 12 Git
review integration tests and 40 materializer/codec tests. The slow Java-analysis
scenario took 129.78 s and completed normally. Installer is now rebuilding the
release executable; the previous executable hash is no longer final readiness
evidence because this repair changed Rust source.

The final runtime probe `3803e62b…b43b` (JVM 23076, normally closed) caught
Java rejecting Rust's canonical `\\\\?\\D:\\…` spelling. Its freshness details
reported `InvalidPathException` rather than falsely claiming current. Reuse the
existing symbol-boundary path adapter through `SFMNativePaths` for freshness;
the new parser test covers extended drive/UNC, ordinary paths, URI conversion
and preservation of raw diagnostic evidence. Final Java tests/runtime remain
required. Rust was installed successfully after its last edit: SHA256
`C7259EB80429EBE5A4CDB335660165BE60752A7FF88D500E589349FD5C6ECCB0`,
version `0.1.1 (rev 16328629f, built 2026-09-06 18:57:50 -04:00)`.

**Verified core checkpoint:** Final Java run
`search-capture-full-client-tests-4.ndjson` exited 0: **1,904 passed, 0 failed,
0 skipped, 1 aborted (1,905 found)** with the 62.95 MB scale fixture enabled.
The abort is the existing unconfigured installed-symbol-worker integration.
An initial sandbox cache-lock denial was retried with normal cache access;
no lock/cache deletion was used. Datagen from the prior run remains current
(the final path adapter added no language entries).

Final fresh JVM **16604**, virtual bridge `7dc0184b…373a`, reopened the renamed
copy writable with 2 comments, dirty=false and the saved remaining-work query.
Image 3 shows CURRENT_AT_CHECK; image 4 exposes the exact repo-root command;
images 6/7 prove it opens `file:///D:/Repos/Minecraft/SFM/repos2/1.19.2` in the
right Explorer, not the review's nested parent. Input remained virtual.
`core-final-freshness-2.ndjson` and its preview manifest preserve this evidence.
Normal `finish` closed JVM 16604 and its CLI exited 0. The completed artifact is
`platform/minecraft/build/sfm-toolchain/artifacts/game-test-preview/runs/sfm-title_screen-20260906-191727-403/index.html`.

Core operational readiness: branch 1.19.2, HEAD `16328629fa60c45a4f525b6f20aaa77715f91077`
plus this goal's working changes. Installed CLI is the C7259E…C0 executable above;
**user must run install.ps1: no**. Full offline Rust check-all and installer ran
after the last Rust edit. Frozen declarations/lockfiles have no diff; no new
dependencies, clones, commits or propagation. The manual review remains SHA256
`9ADDCCEC595CAA916EFDB8019270F62D577B336508D2608D84BDA2FE7AADD592`;
preview theme remains byte-identical to the recorded hash. Ordinary manual launch:
`sfm-propagate-changes.exe run client --branch 1.19.2 --wait-for-build-lock`.
Use `docs/explorer find filter and working tree review guide.md`; the user's
existing review is unchanged. Working-tree creation is Windows-only; existing
portable captures do not need source access to reopen. EF-7's renewed
Java/runtime/datagen readiness is recorded under its completed task. EF-9's
renewed full suite, datagen, two-scale mouse exploration and separate-JVM preset
restart are now recorded under its completed task. No further stretch is claimed.

**Final handoff (2026-09-06):** EF-1–EF-9 and EF-11/EF-12 are complete; EF-10 is
the next scheduled slice. Rechecked the installed C7259E…C0 executable/version,
manual-review 9ADDCC…D592 and preview-theme 04FBF3…A9C hashes after the final
GUI-4 run; all match the recorded values. Dependency declarations/lockfiles have
no diff. The last runtime compiled current Java source; the last Rust edit
predates the installed executable. **No user install script is required.**
All test clients are closed. No commits, pushes or propagation were performed.
Final documentation whitespace check passed. The whole-worktree `git diff
--check` reports only two lines in the generated language-cache manifest
(`src/generated/resources/.cache/c622617f6fabf890a00b9275cd5f643584a8a2c8`);
its generator-owned CRLF/timestamp format was retained rather than hand-edited.

Observed nonblocking follow-ups for the next feedback pass (not silently counted
as implemented): combined Find/Filter explanation tooltips can be overlong and
show newline-control glyphs at GUI 2; use explicit wrapped tooltip lines across
scales. Selecting a palette candidate can relayout the Execute button, so a mouse
user must reacquire its position; preserve stable controls where practical.
Unify or clearly expose the existing raw-greedy open-path argument versus the
separately quoted working-tree creation arguments. EF-10 retains exact segment
targeting; a general saved-workspace store and an editor Find bar remain outside
the claimed compact-preset/editor-match slices.

**Work:** Apply to each claimed slice, listing its exact task IDs. Use ignored
disposable review/repo/preference copies and virtual input. First add regression
assertions; then perform a fresh adaptive run with the real filename absence
story, editing query, choosing scope, navigating, selecting/commenting as the
claimed features permit. Include an intentionally absent file and pathological
input, not only known good results. Capture source/runtime identity, requests,
screenshots, frame/dispatch observations and remaining limits. Do not impose an
unreliable 5% game-timing gate; prove no IO/work loops on UI paths and record
representative responsiveness, cancellation and bounds.

**Validation:** V1–V6 as applicable; full Java suite after final runtime changes,
Rust check-all/install after Rust changes, datagen for localized strings,
separate-JVM persistence where claimed. Re-run after final generated input changes.
Use the existing exploratory puppet/virtual file bridge; diagnostics or direct
model mutation are not substitutes for typing Ctrl+F or hitting a merged segment.

**Completion criteria:** Claimed tasks have current-source, model and runtime
evidence; manual guide/changelog match actual controls; installed tool and game
state are explicit. Unclaimed work remains visibly unchecked. No human review,
approval, OS cursor or unrelated process was modified by testing.

## Validation commands and supported target

Run from the primary root unless noted. These commands exist today; they are
planned validation here, **not executed during this documentation turn**. Extend
named existing suites, or record new suite/puppet discovery before using new
filters. Require nonzero discovered tests; a broad green suite without the new
assertions is not evidence of the behaviours above.

```pwsh
# V1 — Explorer logic, projection, input, action grammar
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMExplorer
# V2 — palette/input regression
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMCommandPalette
# V3 — review Java boundary/UI models
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMReleaseReview
# V4 — Rust review core/CLI, still from the primary root
cargo test --manifest-path platform/cli/sfm-propagate-changes/Cargo.toml --locked --offline --lib release_review
# V5 — editor geometry/history/selection regression
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMTextEditor
# V6 — current Java, localized data, existing exploratory runtime (disposable)
sfm-propagate-changes.exe run data --branch 1.19.2
sfm-propagate-changes.exe run compile --branch 1.19.2
sfm-propagate-changes.exe puppet run sfm:title_screen_exploratory_review --branch 1.19.2 --variant 1920x1080@2 --wait-for-build-lock --log-filter info --log-file platform/minecraft/build/ef-exploration-2.ndjson
sfm-propagate-changes.exe puppet run sfm:title_screen_exploratory_review --branch 1.19.2 --variant 1920x1080@4 --wait-for-build-lock --log-filter info --log-file platform/minecraft/build/ef-exploration-4.ndjson
sfm-propagate-changes.exe test run --branch 1.19.2
# If Rust changed, run each script from its crate directory
Push-Location platform/cli/sfm-propagate-changes
try {
    .\check-all.ps1
    if ($LASTEXITCODE -ne 0) { throw 'check-all failed; do not install' }
    .\install.ps1
    if ($LASTEXITCODE -ne 0) { throw 'install failed; tooling is not current' }
} finally {
    Pop-Location
}
```

| Target | Support for this plan | Required evidence |
| --- | --- | --- |
| Minecraft 1.19.2 | Primary implementation; title-screen and multiplexer panels, GUI 2/4, full/split viewport. | Focused + full suite; virtual runtime exploration; independent restart where persistence claimed. |
| Java-only client, companion unavailable | Shared input, compaction and supported local matching remain usable. | No-companion fixture; freshness/complete-domain/semantic facilities say unavailable if their provider is missing. |
| Rust companion on Windows | Review freshness/capture adapters within frozen graph. | Locked offline review tests, check-all, installed-tool provenance and Java/Rust schema parity. |
| Other Minecraft branches | Not claimed in this bounded follow-up. | Future explicit propagation with version adapters and per-target acceptance; do not overwrite newer-target behaviour. |

## Operational boundaries and risks

Read [goal execution guidelines](goal%20execution%20and%20testing%20readiness%20guidelines.md)
before implementation. No new dependencies/lockfile changes/new unpinned repos;
locked deterministic cache rehydration is allowed. No Gradle, broad file scans,
model spending, commits, pushes or propagation implied. Never replace
`platform/minecraft/run/manual-test.sfm-review.json` or the maintained
`docs/reviews/4.34.0-1.19.2-to-58ed4e431.sfm-review.json` to make a test pass.
The user's September 6 goal authorization activates only the guidelines' proven
in-scope process lifecycle authority. Protect user reviews and preferences during
all builds and virtual runtime tests; no OS pointer injection.

| Risk | Guardrail / proof |
| --- | --- |
| Scorer adjustment regresses palette or other callers. | EF-4 adds explicit modes at owning boundaries, preserves shared fuzzy behaviour and old-command fixtures. |
| Matching/filter rebuild stalls on every key or regex never terminates. | Generation-bound async work, immutable results, cache/bounds, adversarial EF-8 tests; no synchronous IO/render work. |
| Complete-domain badge overstates partial resolver traversal. | Capability/completeness evidence, unknown descendant state, no false all-selected success. |
| Compaction loses parent identity or scroll/selection. | Complete topology only, exact segment paths, deterministic anchor, independent panel/path preferences. |
| Reused Alt keys steal palette focus/cancel or editor input. | Scope and canonical action identity tests; actual virtual keyboard integration, not only action invocation. |
| Yellow highlighting obscures syntax, review comments or diff state. | Separate rendering layers, accessible contrast/marker text, dark/light and scaled screenshot proof. |
| Stale review looks current or live selector launders approval. | Two-axis freshness; immutable evaluated evidence; explicit migration; EF-12 restart/coverage tests. |
| Capturing working tree leaks unrelated/ignored data or races edits. | Explicit authorized scope, exclusions, bounded reads, capture consistency proof, portable output ownership. |
| Expanding ambition hides unfinished requested work. | Checkpoint task IDs, full ledger, no regex/multi-select/working-tree claims before their tasks complete. |

## Overall completion criteria

- [x] Every claimed checkpoint identifies its tasks and current-source tests.
- [x] All EFR-01–49 have proof or remain explicitly scheduled, not silently dropped.
- [x] Find/filter, compact paths, selection, review domains and live/pinned targets
      agree with public actions, tooltips, guides and portable storage contracts.
- [x] Current-source runtime exploration demonstrates the user's story without
      OS cursor movement, hidden fixture rewrites or human approval mutation.
- [x] Future runtime handoff explicitly states installed tooling freshness,
      final game/process state and exact manual testing flow; planning alone
      requires no installer and does not alter running behaviour.
