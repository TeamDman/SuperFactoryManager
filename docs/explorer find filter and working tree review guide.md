# Find code, review uncommitted changes, and resume later

This guide describes the 1.19.2 iteration tracked in
[the implementation plan](tasks/explorer%20find%20selection%20compact%20hierarchy%20and%20review%20freshness%20plan.md).
The core plus editor match-selection and compact paths are verified. The installed
CLI is current; you do **not** need to run `install.ps1`. Test clients were closed
after verification, so launch a fresh client using the command below.

## Start with an existing review

1. Launch from the 1.19.2 checkout:

   ```powershell
   sfm-propagate-changes.exe run client --branch 1.19.2 --wait-for-build-lock
   ```

2. Press Ctrl+Shift+E to open an Explorer. Right-click your `.sfm-review.json`
   file and choose **Open release review writable**. The keyboard equivalents
   are the context-menu key or Alt+Enter on the selected row.
3. Do not open a large review ledger as raw text. Its ordinary review view
   provides lazy file, diff, comment, query and status entries instead.

If opening directly through the palette, the existing open action takes the raw
path as its final argument (do not surround it with quotes):

```text
sfm action invoke sfm:review/session/open/view D:/path/to/my-review.sfm-review.json
```

This differs from the separately quoted arguments for creating a working-tree
review below. The mouse menu supplies the correct open command for you.

## Find and Filter are separate

| Control | What it does |
| --- | --- |
| Ctrl+F | Focus **Find**. Match and navigate without hiding the other rows. |
| Ctrl+Shift+F | Focus **Filter**. Restrict the visible Explorer domain. |
| Enter in Find | Replace the current selection with the next match, wrapping. |
| Shift+Enter in Find | Select the previous match, wrapping. |
| Escape in either input | Return focus to the Explorer body. |
| Alt+J | Add the next unselected Find match. |
| Ctrl+Shift+Alt+J | Select all Find matches in the current search scope. |

Both inputs support Ctrl+A, Ctrl+Backspace, Ctrl+Delete, Home/End, selection,
clipboard editing and Unicode-safe cursor movement. Ctrl+Arrow stops at colons,
slashes and hyphens; underscores stay inside identifiers.

New Find and Filter inputs default to case-insensitive **literal** matching.
Fuzzy matching is still available and retains its ranking. For example, enter
`ExploreReviewInteractivelyPuppetAction.java` literally to distinguish an absent
file from similar names. With Find focused, the first preview starts below the
topmost visible selected row, or at the top visible row if no selection is
visible. Typing does not itself replace selection; Enter does.

The inline buttons, or the **…** menu in a narrow panel, expose case sensitivity,
whole word, regular expressions, fuzzy matching, dot-all and highlight visibility.
Regex and fuzzy are mutually exclusive. Dot-all is available only in regex mode.
The bounded regex engine rejects unsupported syntax or exhausted work explicitly;
an incomplete result is never silently treated as a complete selection.

Alt+F toggles fuzzy and Alt+H toggles matched-glyph highlights in the focused
search input. Find and Filter have independent option states. Right-click either
input for **Clear input** and its other actions. Find also offers nonwrapping
next/previous and an explicit loaded-entries-only scope when a complete-domain
provider is unavailable. Find respects an active Filter; clearing one does not
clear the other.

Find glyphs are yellow by default, Filter glyphs cyan, and their intersection
purple. Themes may change these colours. A small descendant marker indicates a
match below a folder; a folder's own matching glyphs are highlighted separately.
Incomplete descendant knowledge is distinguished from a known match.

## Select several Explorer rows

- Click: replace selection and establish the range anchor.
- Shift+click: replace selection with the anchored range.
- Ctrl+click: toggle only that row.
- Ctrl+Shift+click: add the anchored range, or remove it if the whole range was
  already selected. Other selected rows are preserved.
- Enter in Find: return to one selected match. Alt+J remains additive.

Selection membership uses stable path identities, not row numbers. A filter can
hide selected rows without deleting their membership. The primary navigation
cursor and range anchor are distinct from that membership. Existing contextual
row actions act on their explicitly captured row; they do not silently become
bulk writes merely because several rows are selected.

## Understand the review's source identity

A Git review is pinned to actual commit IDs. `HEAD` is resolved when the review
is created; an old review does not begin following later commits. A freshness
banner reports the last check and its age. Unavailable checks say **unknown**,
not current. The banner's context actions expose the exact source evidence.

If a file was introduced after the pinned candidate, it will not appear in that
review. Filter changes cannot add it. Create a new review for a newer source
state; do not reinterpret previous comments against different bytes.

## Review files on disk without committing them

In an open review's **Changes** lens menu, choose **Create working-tree review
from this baseline…**. This prepares an ordinary palette command with the
baseline and a new output path. Supply an explicit repository-relative scope,
then execute. Slash-containing scope/path arguments must be quoted.

Here is a small starting example from the 1.19.2 repository. Change the output
name if it already exists; creation deliberately refuses to overwrite it.

```text
sfm action invoke sfm:review/session/create/working_tree "docs/reviews/my-working-tree.sfm-review.json" 1.19.2 HEAD "platform/minecraft/src/main/java/ca/teamdman/sfm"
```

This uses today's committed HEAD as **before**, and captures the chosen source
directory's **files on disk** as after. To review since a release instead, use
that release's Git revision as before. An optional final quoted repository root
is available when the game is not running below the intended repository.

The new review opens writable when creation finishes. It includes tracked edits
and allowed nonignored untracked files; staged content does not replace newer
disk content. An untracked addition has a truthful missing-before entry. The
candidate is labelled **working tree <hash> · captured <time>**, not HEAD.

This initial producer is Windows-only. Creation needs the companion, but an
already saved capture is portable and can be reopened/commented on without live
source access. It reads only the declared repository scope, excludes Git internals
and disclosed untracked secret-file patterns, refuses concurrent changes and
unsafe filesystem traversal, and uses explicit size/deadline limits. Choose a
narrower scope if a limit is reached. Text above 4 MiB and unsupported sources
remain visible limitations, not falsely approved text. This is not a secret scanner.

## Leave a comment and recover it after closing the game

1. Expand a file and open **After** (or **Before**, text diff, structured diff).
2. Drag over the code you want to discuss. In a read-only preview, Ctrl+A selects
   the whole exact document, including its terminal newline, without creating
   one cursor per glyph.
3. Press Alt+Enter or right-click inside the selection. Choose the **exact
   selected bytes** comment target, then `#approved`, `#needs-change`, a recent
   comment, or **Other**. Check the target description before approving.
4. For **Other**, type your note and use **Done** / Shift+Enter. Wait for the
   pending save to finish. Comments and the resumable work queue are stored in
   the review file, not only in appdata.
5. Use the review lens menu's **Comments**, **Queries** or remaining-work queue.
   Close the game, reopen the same `.sfm-review.json`, and continue.

An agent can read the same saved evidence without clipboard transcription:

```powershell
sfm-propagate-changes.exe --output-format json review session query --file docs/reviews/my-working-tree.sfm-review.json "effective(#approved) intersect 1.19.2 candidate"
sfm-propagate-changes.exe --output-format json review session query --file docs/reviews/my-working-tree.sfm-review.json "(1.19.2 candidate) difference effective(#approved)"
sfm-propagate-changes.exe --output-format json review session freshness --file docs/reviews/my-working-tree.sfm-review.json
```

`candidate` means this review's saved candidate domain; an explicit capture ID or
commit ID is also queryable. Query results retain exact before/after coverage.
Approving a portion does not approve the whole file. Initial working-tree units
conservatively cover whole changed files; diffs and exact comments still permit
finer inspection. Generated change comments are not human approvals.

Editing/deleting the original file later does not erase the captured preview or
move its comments. Freshness checks compare the chosen scope; unrelated commits
outside it do not invalidate identical captured content. A new capture starts
without inherited approval. Migration is a separate explicit decision.

## Select and edit repeated text in an ordinary editor

The editor match-selection slice is verified under EF-7 in the plan. Open a
disposable **V3 canvas** buffer with this command (Alt+D honors
your saved editor preference and can select the legacy editor instead):

```text
sfm action invoke sfm:panel/open sfm:text_editor sfm:text_editor_v3
```

Type `hi hi hi`, and drag over the first `hi`.
Alt+J adds the next occurrence; Ctrl+Shift+Alt+J selects all. Typing
replaces the selected ranges together. Ctrl+Z / Ctrl+Shift+Z use the ordinary
editor history, including the selection changes. The first character replacing
a selection and the subsequent typing run are separate undo chunks; undo twice
to restore `hi hi hi` after typing `yes` over those ranges. Read-only previews remain
read-only, but their exact matches can be selected and copied/commented on.

An empty query takes the primary selected text literally. It does not guess a
nearby word. Right-click / Alt+Enter exposes **Add next occurrence**, **Select
all occurrences**, **Set editor Find query**, and **Toggle editor Find option**.
These are named actions, also available directly:

```text
sfm action invoke sfm:document/search/query "hi"
sfm action invoke sfm:document/search/toggle whole-word
sfm action invoke sfm:document/search/select all
```

Editor options use the shared case/whole-word/regex/fuzzy/dot-all matcher. Set
query `""` to return to selection seeding. Approximate fuzzy evidence is not
used as an editing range. Unsupported regex, stale results and exceeded limits
leave the current selection unchanged. Match selection is bounded to 32,768
Unicode scalars and 4,096 fragments; exact range replacement additionally caps
the source/result at 32,768 UTF-16 units and transformation work. Ambiguous
composite glyph cuts are refused. Recent non-grid edit layouts can be restored
by undo, but the general persistent history still stores logical text/ranges,
not an unlimited archive of canvas geometry.

There is not yet an editor Find bar; the new bar described earlier belongs to
the Explorer.

## Compact Explorer paths

Compact paths are verified under EF-9, including a separate-game preset reload.
In hierarchy mode,
already-known complete single-child container chains display as one row, such
as `ca/teamdman/sfm`. Expanding that row opens the last container's children.
Files and their Before/After/diff choices are never folded into a directory.
Unknown or partially loaded chains stay separate until their topology is known;
compaction does not recursively read directories merely to shorten a label.

Right-click the **row text** (not the icon) for **Unmerge this path chain only**.
This keeps the row's first segment anchored while exposing the intermediate
containers. **Reset compact-path overrides at and below this path** restores
that subtree's panel default. The same menu can enable/disable the default for
this Explorer alone. Root hoisting and flat/hierarchical grouping are separate
settings. Existing selection membership survives layout changes, including
intermediate paths found while they were merged. Copy entry details includes
every exact compact segment address.

Preferences are copied when a panel is duplicated/reopened by its existing
typed recipe; a different Explorer is independent. For reuse after a game
restart, choose **Copy compact-path preset as JSON**, save the clipboard text in
a file you own, and later use **Apply compact-path preset JSON to this Explorer**
and paste that JSON as its argument. This is an explicit portable preset, not an
automatic saved-workspace system. It does not modify your review file or create
ambient appdata state. Missing/renamed paths do not transfer their overrides.

```text
sfm action invoke sfm:explorer/compact/set false
sfm action invoke sfm:explorer/compact/preset/copy
sfm action invoke sfm:explorer/compact/preset/apply {"schema":"sfm.explorer-compaction/1","enabled":true,"overrides":{}}
```

These actions use the originating/focused Explorer. A normal compact-row action
targets the last container; individual segment hit targets are the next ordered
stretch. Until that is implemented, unmerge first to target an intermediate row.
