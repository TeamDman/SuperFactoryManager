# Explorer navigation and review diff presentation

**Plan status:** Active implementation
**Primary implementation root:** `D:/Repos/Minecraft/SFM/repos2/1.19.2`
**Last updated:** 2026-09-07
**Intent audit:** Passed against the September 7 menu/refresh reports and latest implementation request
**Current focus:** ND-1

## Update protocol

`[ ]` scheduled, `[~]` active, `[x]` verified, `[!]` blocked with evidence.
Keep one implementation focus. Store decisions and actual test/runtime evidence
under the task they prove; a source-only test does not prove a palette menu.
This follows `resumable-implementation-plans` and `docs/AGENTS.md`.

## Intent and traceability

| ID | User atom / constraint | Task |
| --- | --- | --- |
| ND-U1 | Explorer right-click menu needs Refresh, including a usable root refresh when no root row is shown. | ND-1 |
| ND-U2 | Change location to parent of root. | ND-1 |
| ND-U3 | Add parent of root as another root, preserving existing roots. | ND-1 |
| ND-U4 | Directory row menu must expose Add directory as root; retain multi-root capability. | ND-1 |
| ND-U5 | `.gradle` review source needs syntax highlighting. | ND-3 |
| ND-U6 | Text and structured diffs need light red removed / light green added backgrounds, independently of syntax foreground. | ND-4 |
| ND-U7 | Add split leaves alongside existing inline text/structured diff leaves; left/right in one canvas editor, not unrelated panels. | ND-5 |
| ND-U8 | Name-based icon rules: Minecraft container should be grass block, not generic chest. "Rename rules" interpreted as icon naming predicates, not filesystem renames. | ND-2 |
| ND-U9 | `.gradle` and `.md` need distinct icons. | ND-2 |
| ND-U10 | Inventory basically all extensions under `file:///D:/Repos/Minecraft/SFM/repos2/1.19.2/platform/minecraft/run/` and supply useful defaults. Preserve specific `.sfm-review.json` precedence and user theme authority. | ND-2 |
| ND-U11 | Update notes, make a plan, complete requested changes. Preserve exact comment targets through visual layout. | All, ND-6 |
| ND-U12 | Earlier working-tree baseline menu option was absent; direct creation succeeded but cached Explorer listing hid its output. | ND-1/ND-6 |

### Intent audit evidence

- Extraction: reread the latest original request and preceding create-review /
  refresh exchange; ND-U1–12 retain all named actions, extensions, colours,
  one-editor split layout and the exact inventory location.
- Traceability: each atom has an implementation task and runtime acceptance.
  Name rules are a reversible interpretation stated to the user. No actual
  filesystem rename, new parser dependency or source write is inferred.
- Adversarial pass: parent replacement differs from additive roots; per-root
  choices avoid arbitrarily choosing one root of a multi-root Explorer. Existing
  inline diffs remain; split views must preserve source-side byte mappings.
  User overrides remain stronger than new defaults. Paper fallback is retained
  only for unknown types, not as silent "coverage" of known extensions.
- Source limitation: historical work is additionally represented by the linked
  living plans; this audit does not re-prove their completed tasks.

## Foundation, decisions and boundaries

- Existing `SFMExplorerAction` has root/node operations and canonical selectors;
  `SFMExplorerContextActionRegistry` currently exposes inspection/review actions
  but no generic directory navigation provider. F5 only refreshes selected row.
- `SFMReviewLensSetAction` constructs a baseline continuation, but
  `SFMActionChoiceCatalog` removes it because `SFMReleaseReviewAction` does not
  implement `SFMClientActionCompletion`. Exercise actual availability in a test.
- `SFMTextDocumentLanguage` and Rust `syntax_highlight/engine.rs` support remote
  Java only. Frozen Cargo contains Arborium Java, not Groovy/Markdown grammars.
  Add an explicit bounded local lexical provider for Gradle/Groovy and useful
  Markdown/JSON/config source colouring; do not mislabel it AST/semantic support
  or pretend Java grammar understands Groovy. Keep Java on its existing worker.
- `SFMReleaseReviewSurfacePresentation` already projects syntax and change
  decorations. Diagnose why ordinary opened diffs lose them before extending
  layout. No screenshot-only fix that breaks exact source mapping.
- Root mutation is presentation/read authority, not disk creation/deletion.
  Parent at volume/authority root is unavailable, not self-looping. For multiple
  roots, provide explicit parent choices for each eligible root. Navigation must
  use registered actions and originating Explorer identity, including location
  bar/body context menus. Do not hijack icon-specific aspect menus.
- Split layout is a view over an immutable generated surface. Keep separate
  before/after identities even when lines have identical text. Unicode, empty
  lines, source comments, copy and selections must retain their side.

## Source inventory (metadata only)

`rg --files --hidden --no-ignore platform/minecraft/run` grouped by extension:
toml 295, dat 204, mca 162, txt 156, gz 138, png 68, json 45, dat_old 24,
lua 20, log 18, lock 15, ini 7, cfg 2, properties/bak/v1/json5/marker/zip 1 each.
Root directories include config, crash-reports, defaultconfigs, logs, mods,
resourcepacks, saves, screenshots, SFM and terminal-content. No contents were
read to pick icons. Add gradle/md plus sensible adjacent source/archive suffixes;
test every inventoried extension and `.sfm-review.json` specificity. Runtime
contents will evolve; unknown extension fallback remains visible/inspectable.

## Work contracts

### [~] ND-1 Expose Explorer navigation and working-tree continuation

**Work:** Add canonical refresh-current-roots, parent-replace and parent-add
operations using existing action engine; row directory Add as root and Refresh
plus location/body root choices. Respect multi-root and rootless/volume cases.
Fix the baseline continuation registration contract. Invalidate/update affected
directory listing after successful review creation without render-thread IO.
**Validation:** Tests for exact commands, stale host, multiple roots, add versus
replace, volume root and actual choice-catalog availability. Live right-click
and resulting tree; created file appears after menu refresh.
**Done:** All requested navigation operations work from mouse menus and CLI.

### [ ] ND-2 Expand inspectable default icon rules

**Work:** Add typed name predicates for Minecraft and useful directory names,
and distinct suffix defaults from inventory. Preserve user layers, compound
suffix specificity and item-rendering fallback explanations. No floating-point
priority or IO in predicates.
**Validation:** Pure rule table covers each inventoried extension, Minecraft
container, `.gradle`, `.md`, renamed subjects, user overrides and review
virtual-directory metadata; GUI title-screen/split-panel screenshot.
**Done:** Known types and name-based exceptions select their intended items.

### [ ] ND-3 Colour Gradle and supporting review text languages

**Work:** Add dependency-free bounded lexical highlighting with explicit language
metadata and source-offset spans. Share foreground projection in inline/split
diffs. Strings/comments/keywords/numbers and Unicode offsets must be correct;
unknown syntax stays neutral. Document lexical versus AST distinction.
**Validation:** Gradle fixtures including strings/comment delimiters/multiline,
Markdown, Unicode and size bounds; before/after review runtime coloured text.
**Done:** `.gradle` is visibly highlighted without a new dependency or wrong
SFML/Java parsing route.

### [ ] ND-4 Restore independent inline diff backgrounds

**Work:** Reproduce source-map/decorations lifecycle through normal open flow;
repair publication/invalidation or draw ordering as indicated. Use configurable
light red/green defaults and retain foreground, selection and comment layers.
**Validation:** Open both diff types through Explorer, assert nonempty expected
decorations and inspect pixels; deletion/addition/context, empty/trailing-newline
and Unicode cases. No background on metadata pretending it is source evidence.
**Done:** Both inline types visibly show removed/added areas with readable text.

### [ ] ND-5 Add split text/structured diff canvas leaves

**Work:** Separate named leaves/identities for split and inline; implement
left-before/right-after canvas layout with labels/aligned rows, pan and selection.
Source-side mappings remain immutable, exact, and independently commentable.
Reuse generated diff data rather than invoking unrelated external tools.
**Validation:** Text and structural fallback cases; both sides, additions-only,
deletions-only, Unicode, variable-width glyphs, copied selected bytes, comments
and existing inline regression. Live single-panel split at GUI 2/4.
**Done:** Both new leaves open one usable two-column editor and exact targets.

### [ ] ND-6 Integrate, exercise and hand off

**Work:** Full current Java tests/datagen; Rust full check/install if changed;
adaptive virtual-input disposable review journey, docs/changelog, installed-tool
hash and process state. Record limitations and evidence under owning tasks.
**Validation commands (root above):**

```powershell
sfm-propagate-changes.exe test run --branch 1.19.2 --wait-for-build-lock --log-filter info --log-file platform/minecraft/build/nd-20260907/full-tests.ndjson
sfm-propagate-changes.exe run data --branch 1.19.2 --wait-for-build-lock --log-filter info --log-file platform/minecraft/build/nd-20260907/datagen.ndjson
sfm-propagate-changes.exe puppet run sfm:title_screen_exploratory_review --branch 1.19.2 --variant 1920x1080@2 --wait-for-build-lock --log-filter info --log-file platform/minecraft/build/nd-20260907/runtime.ndjson
```

Repeat runtime at GUI4; use only virtual bridge requests and disposable files.
**Done:** All tasks verified, guide matches actual menu/canvas, no user reinstall
surprise. This is required work, not an unclaimed stretch ladder.

## Operational readiness and risks

Frozen graph/declarations/lockfiles; no Gradle, new repositories/dependencies,
commits, pushes, propagation, human approvals or overwrites of user review files.
Follow `goal execution and testing readiness guidelines.md` for proven in-scope
process lifecycle only. Record running client before tests; prefer separate
preview instance and do not discard unsaved user data. Current installed CLI
from preceding checkpoint is C7259E…C0; reverify before final handoff.

Risks: phantom menu entries (availability integration test), ambiguous multi-root
parent (explicit root target), filtered/review roots escaping authority (typed
resolver boundary), async publication race (snapshot identity checks), colour
hidden by rendering state (live pixel evidence), split selection retargeting
(exact side/byte tests), user rules overridden (layer tests), false syntax claim
(bounded lexical provider documented), lifecycle/lock issues (owned processes).

## Overall acceptance

### Implementation checkpoint — 2026-09-07 (not completion)

- ND-1 source: registered `sfm:explorer/refresh` and revision-checked
  `sfm:explorer/root/parent/set`; row/location/empty-body context choices expose
  refresh and additive roots. Parent replacement acquires the parent first,
  then removes the original. This is deliberately fail-safe but **not atomic**:
  if removal fails both roots remain. The maximum-root-count edge still needs
  an explicit test. Non-file authorities do not guess parents.
- ND-1 source also restores `SFMClientActionCompletion` on working-tree review
  creation and requests refresh of an already-known output parent after success.
  A real choice-catalog regression test was added, rather than only testing the
  helper that constructs the choice.
- ND-2 source covers every inventoried extension plus Gradle/Markdown/Rust/PS1.
  `minecraft` container naming is an inspectable DEFAULT-layer conjunction;
  USER rules and explicit directory icon preferences remain authoritative.
- ND-3 source uses `SFMLocalLexicalStyles`, explicitly lexical, bounded at
  262,144 UTF-16 units, with exact UTF-8 spans. It is synchronous at document
  load/change, not a new asynchronous provider. Above the bound it returns
  neutral styling. Tests include astral Unicode, comments and strings. Java
  remains on the existing worker. Generated surface source fragments reuse
  this lexical path through the existing mapping projection.
- ND-4 source brightens green/red backgrounds and changes decoration geometry
  to read exact pointer/source text instead of the canvas-reconstructed text
  that can omit CRLF/trailing newline bytes. This is not yet live pixel proof
  of the reported absent-colour issue.
- ND-5 is **not implemented**. `projectPinned` currently validates top-down
  glyph order and exact origin-based positions. Split layout must replace that
  assumption with a source-to-glyph identity map before side-by-side selection
  can safely target comments. Do not just add rows that open inline text.
- Test evidence: `full-tests-1.ndjson` failed on sandbox cache access; rerun with
  normal Windows cache access, `full-tests-2.ndjson`, passed 1,932 tests with
  zero failures and two opt-in aborts. This only covers the earlier ND-1 source.
  `full-tests-3.ndjson` found a test label type error (Component instead of
  String), corrected before `full-tests-4.ndjson`. Record that run's final
  result below. No current-source game validation has been claimed.
- No Rust source/dependency/lockfile edit, installation, commit or propagation
  was performed for this checkpoint. No user review was rewritten. Changelog
  records source changes, not a claim that ND-5 or runtime validation is done.
- Follow-up evidence: run 4 had three failures (two old `.txt` presenter-ID
  expectations and incomplete metadata in the new directory fixture). Run 5
  had one remaining fixture validation failure (missing search term); corrected.
  The new continuation, icon precedence, and lexical Unicode tests passed in
  those runs. Run 6 is the current-source full-suite verification.
- Run 6 result: **1,938 passed, zero failed, two opt-in aborted, 1,940 found**.
  This includes all current Java source changes and the corrected fixtures.
  The opt-in omissions are installed symbol-worker integration and captured
  release-review scale coverage; no runtime/GUI proof is inferred from this.
- Datagen `datagen-1.ndjson` completed successfully (exit 0), producing all
  eight `gui.sfm.explorer.navigation.*` translations. No stale files removed.
  The later language-routing metadata edit does not alter localization.
- Operational readback: installed CLI SHA-256 is
  `C7259EB80429EBE5A4CDB335660165BE60752A7FF88D500E589349FD5C6ECCB0`;
  no installer is needed because Rust was not edited. Manual review remains
  `9ADDCCEC595CAA916EFDB8019270F62D577B336508D2608D84BDA2FE7AADD592`;
  new user review `review-2026-09-07.sfm-review.json` reads
  `52E8EE6E700B0090C7D8612C93D84393ED21FC39801F9047D59C9F3CEB42D306`.
  No user Minecraft process was present at the process preflight. No client
  was launched or closed during this checkpoint; only owned test/datagen JVMs.
- Draft user guide: `docs/explorer navigation and diff presentation guide.md`.
  It explicitly states refresh depth, lexical bounds and unfinished split views.

### September 8 continuation

The later [review refresh/comment revision](review%20refresh%20and%20comment%20interaction%20revision.md)
implements and validates ND-4/5's inline labels, independent change backgrounds
and actual single-canvas split text/structured views. Its exact source-side
selection/comment mapping supersedes the earlier `projectPinned` blocker above.
GUI2/GUI4 galleries and current-source full-suite evidence are in that plan;
the [current guide](../review%20refresh%20and%20comment%20interaction%20guide.md)
supersedes the old unfinished-split wording. This does not retroactively claim
the unrelated root-count edge or every earlier ND item has live proof.

- [ ] ND-U1–12 have current-source proof; every requested task is complete.
- [ ] Exact review/comment bytes and old inline views remain intact.
- [ ] Full tests, datagen and real menus/rendering verified at GUI 2/4.
- [ ] Notes, changelog, manual guide and tool/process readiness are truthful.
