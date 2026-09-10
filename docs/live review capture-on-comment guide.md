# Live single-file reviews

Status: diff-first revision verified2026-09-09. This guide records tested routes
and their limitations, not a claim that every longer-term planned affordance exists.

## Diff-first revision walkthrough

- A new live review has no automatic release-change comments. Changes remain
  available through Before/After and inline/split diff rows.
- Rust source now shows language highlighting in After and diff views. Structural
  matching is still Java-specific; Rust structural-diff views explicitly report
  their fallback. Syntax coloring and structural matching are separate features.
- Beside `#`, `Z` means the wheel zooms and `S` means it scrolls. Click to switch.
  `M` means middle-button pan/right-button actions; `R` swaps those roles.
  These are per-editor settings. Explicitly saving defaults is a separate action,
  not an automatic change to other open editors.
- After saving a comment, its selected bytes receive a marker/background.
  Right-click those bytes to open comment text, reveal it in Explorer, or inspect
  its selector and matching targets. The marker is not the comment's text.
- The GUI4 disposable example saved one comment on Rust bytes `[0,3)` (`use`),
  with exactly one source document and one hash-verified content blob. Its file
  was 53,424 bytes after capture. This is not a guarantee of every review's size.

Fresh-JVM persistence and Alt+Enter access to an existing retained comment have
now been verified. A GUI2 repository-scale journey also verified one comment
created on inline Rust diff bytes, visible in After and accessible from split
diff without duplication or re-expanding the Explorer. Pointer combinations,
language routing and repository-scale opening checks are now complete; see the
settings and measured-performance sections below.

A working-tree observation has its own identity. Even when one file's bytes are
unchanged, other repository changes can produce a new observation. Its old
comments remain on their recorded snapshot; they do not silently approve the
new observation. The historical source still offers the comment's text, selector,
targets and reveal actions through Alt+Enter. The latest build labels this branch
`Historical comment evidence · not current approval`, containing a `Commented
snapshot` rather than unavailable diff entries; that presentation was checked
in a fresh GUI4 game with its retained Rust comment.

If right-clicking an already-commented range first shows existing-comment
details, use the selection-actions entry to reach the commands for adding a new
comment. Nested context menus can return to their parent after saving; Cancel
returns toward the document without discarding the already-saved comment.

## Launch

From `D:/Repos/Minecraft/SFM/repos2/1.19.2`:

```powershell
sfm-propagate-changes.exe run client --branch 1.19.2 --wait-for-build-lock
```

The current CLI is installed and smoke-checked; you do not need to run install.ps1.
Final process inspection found no Java/SFM/Cargo workers running, so launch with
the command above. Your original review files were not rewritten. Start at the
title screen, press Ctrl+Shift+E, and open the review file through Explorer's
writable-review context action. The final disposable repository-scale journey
completed and shut down normally; an earlier timed-out exploration is retained
as partial evidence only, not counted as a passing puppet.

## Mouse settings and highlighting: verified2026-09-09

In a v3 source or diff editor, the bottom-left controls beside `#` are:

| Button | Meaning | Click result |
| --- | --- | --- |
| `Z` / `S` | Wheel zooms / scrolls | Switch wheel mode |
| `M` / `R` | Middle drag pans / right drag pans | Swap pan and context-action buttons |

In scroll mode, Shift+wheel scrolls horizontally. Left drag remains text
selection. Right-click either settings button to find the separate action for
saving these settings as defaults for new editors; existing editors keep their
own settings. Alt+middle remains reserved for panel manipulation.

The corresponding actions operate on the originating editor:

```text
sfm action invoke sfm:document/pointer/wheel
sfm action invoke sfm:document/pointer/buttons
sfm action invoke sfm:document/pointer/save_defaults
```

The deferred source/diff toolbar dispatch regression is fixed and tested at GUI
scales2 and4. A successful button click changes its letter immediately; a toast
or command dispatch alone is not evidence that the mode changed.

Actual ordinary-file samples now visibly highlight Rust, JSON, Gradle/Groovy,
Markdown, PowerShell, TypeScript and TOML. Java source/diff highlighting is also
tested. Markdown currently styles block structure such as headings, not all
inline markup. Structural matching remains Java-specific: a language having
syntax colors does not mean its structural diff has language-aware matching.

For ordinary files, first open an Explorer rooted at the directory you intend
to inspect. A review's virtual tree does not grant direct filesystem access.
For example:

```text
sfm action invoke sfm:panel/open sfm:explorer file:///D:/Repos/Minecraft/SFM/repos2/1.19.2/
sfm action invoke sfm:path/open file:///D:/Repos/Minecraft/SFM/repos2/1.19.2/docs/AGENTS.md focus
```

Run each command separately. If the palette remains after opening the file,
Escape returns to the editor. These paths are examples for this checkout.

## Opening time versus file size

A small target-only review may still take time to open: its source observation
is derived from the repository. The reported target-only file was1548bytes,
not a large embedded-source document. Current disposable full-repository opens
measured9.42seconds in a fresh JVM and7.78seconds warm (not cold-disk benchmarks).
The transient response contained about31million Java characters for2082sources;
it is not automatically saved into the review JSON.

Most measured wait was companion processing/output transfer. Java decoding was
0.83/0.59seconds, validation0.12/0.06seconds, and evidence indexing0.07/0.06seconds.
These are observations on this machine, not promised latency limits. Look for
`SFM_RELEASE_REVIEW_RESOLUTION_PHASE` in logs to distinguish those phases.
The first non-root contents rendered0.62/0.42seconds after panel construction.
That timer has a different origin from opening: do not add it to the totals.
Opening is still not instant. Companion processing plus IPC is a combined timer;
Rust logs separately expose capture, materialization and validation/serialization.

## What the file means

A new v3 review starts with pinned Git baseline and candidate/live target
descriptions, source scope and review policy. Opening it resolves an observation;
browsing does not save the source corpus into the review file. A live observation
does not automatically change an editor that is already displaying it.

Adding a human comment to non-Git content saves the exact displayed whole-document
bytes with that comment in the same file. Equal content is stored once. Git-backed
evidence retains immutable commit/blob references instead of embedding text.
Keep the repository available for those references. Old frozen reviews are still
frozen; opening one does not reinterpret its historical capture as current disk.

## Open and comment

1. In the command palette, run `sfm action invoke sfm:review/session/open <path>`
   with the path of the review. Wait for the opened-writable feedback.
2. Run `sfm action invoke sfm:panel/open sfm:explorer/release_review/changes`.
3. Use Ctrl+Shift+F for a filename filter. Expand the matching file, then
   double-click its After row to open the source. Before can legitimately be
   missing for a new untracked file.
4. Select text in the source, then Alt+Enter. Choose the **exact selected bytes**
   comment target unless you deliberately want a different offered target.
5. Choose #approved, #needs-change or Other. Tab accepts a choice; Enter invokes
   it. Only approve code you have actually reviewed. The automated demonstration
   approvals belong solely to disposable test files.
6. Wait for save completion. The comment and required non-Git evidence are now in
   the review file and can survive closing the game.

## Refresh versus recheck

Click the freshness banner in the review Explorer. **Recheck review freshness**
compares the displayed observation with current source. **Refresh live review
from current source** resolves a new observation of a v3 ledger, retaining saved
comments and historical evidence. Changed bytes do not inherit old approval.

Currently these actions require a review Explorer context. If the source editor
is focused, use the Explorer banner rather than typing the refresh command into
the global palette. This limitation is tracked for improvement.

## Inspect from the CLI

`sfm-propagate-changes.exe --output-format json review session status --file <path>`
resolves the current observation and reports exact approved/remaining coverage.
It does not save a source snapshot or grant maintainer attestation.

GUI2 evidence so far: target-only test file 1,511 bytes; after a comment targeting
6,633 source bytes, 12,718 bytes with one content entry and one human comment.
These are fixture measurements, not a universal size or performance guarantee.
Fresh-JVM GUI4 reopening retained the comment, exact match and text value without
changing the file. Current CLI status reports one exact approved unit and zero
remaining units for this disposable example, not an approval of the repository.

## Read diffs

Expand a changed file and choose **structured diff (inline)** or **structured
diff (split)**. Matched Java declarations retain neutral unchanged fragments,
including unchanged `@Override`, while removed/added fragments have red/green
backgrounds independent of syntax colors. Unsupported/ambiguous/over-limit cases
use an explicit text fallback; text diffs remain line-based.

Ctrl+Shift+9 fits the canvas width. For long split columns, a narrow panel makes
that text very small; enlarge the area or pan/zoom to read it. GUI4 narrow
Explorer headers are also cramped. These are known layout limitations, not
storage or source-mapping failures.

## Create a new small review

From the repository root, use a new output filename (creation refuses overwrite):

```powershell
sfm-propagate-changes.exe review session create-ledger --file platform/minecraft/run/my-live.sfm-review.json --branch 1.19.2 --before HEAD --working-tree --scope platform/minecraft/src
```

Here `HEAD` is resolved once to an immutable baseline commit. Use your previous
release tag instead when reviewing everything since that release. The after target
is explicitly live disk within the supplied scope, including untracked files
unless `--tracked-only` is supplied. This command does not commit source changes.
For a committed candidate instead, omit `--working-tree`/`--scope` and supply
`--candidate HEAD`; both ends are then pinned. Existing reviews are not retargeted
or overwritten by either operation.

## Inspect why the review occupies space

Click the review's freshness banner and choose **Open review storage details**,
then Execute. The read-only report opens as another tab in the same area. It
shows the actual review-file size/hash, durable comment count, retained document
count, and deduplicated source bytes. **Copy review storage details** is a
separate action in that menu.

For one source, select text in its review preview and use its context menu's
storage/evidence actions. The report distinguishes a Git reference (repository
objects required), embedded non-Git evidence, and transient browsing data. It
does not read source files or change the review. Original capture timestamps
not recorded by this schema are explicitly unavailable; current observation
timestamps are not presented as replacements.

Advanced palette form: `sfm action invoke sfm:review/storage/open "<review-file>"
"<document-revision-id>"`. Quote both arguments; omit both for the current
review summary. The source ID is an exact identity, not a filename.

GUI2 verified the banner-menu summary and quoted exact-source report on the
disposable gui-live review. GUI4 verified selecting source text, Alt+Enter,
filtering choices with `storage`, and copying the exact source report. The
clipboard contained the expected revision and byte count. Reports include
explicit before/after identity; existing ledgers without original capture time
remain honestly labelled as such.

## Understand approvals after source changes

In the review Explorer's top-right menu, choose **Switch to Exact coverage and
blockers**. Expand **Approval evidence**. Each approval offers its comment text,
target source, and **Why this approval does or does not count**. Open those rows
like ordinary files. The source link keeps the exact original range and revision;
an invalid original range opens a diagnostic instead of guessing new coordinates.

After editing a live source outside the game, click the freshness banner and
choose **Refresh live review from current source**, then Execute. This updates
the observation, not your saved comments. The same behavior applies when you
close the game and reopen the review. A retained historical approval remains
readable but does not approve a new source revision. **Remaining** shows the
current work; **Approval evidence** counts comments, not approved files.

Snapshot identity includes the captured source domain. Even if one file's bytes
are unchanged, editing another file in that domain can create a new identity.
The explanation calls out identical bytes when applicable; it does not silently
transfer approval. Explicit migration is a separate acceptance step described
below, not a promise made by the refresh button.

Verified disposable walkthrough: approve Example.java containing `return 2`,
change disk to `return 3`, reopen, see one historical approval / zero current
approved surfaces / one remaining change, and open the original `return 2`.
Changing disk again and explicitly refreshing retained the same saved ledger
hash. The GUI2 evidence is in game-test-preview run
`sfm-title_screen-20260908-052108-020` (figures5-9 and15-16).
GUI4 verified the final compact target label and retained-source navigation in
`sfm-title_screen-20260908-052757-045` (figures5,7,8). Narrow split-panel headers
remain cramped at this scale; the source action itself stays navigable.

## Explicitly carry a comment onto newer observed code

This flow requires a saved v3 live review. Legacy frozen reviews keep their old
behavior; they do not offer these successor choices.

1. Focus the review Explorer. Click the freshness banner and choose **Refresh
   live review from current source**, then Execute. This does not move comments.
2. Switch to **Comments**. Right-click the original comment row and choose
   **Preview linked successor · current observed sources · 1.19.2** (or the
   intended lane), then Execute. The same preview is available through the
   comment details choices on a highlighted source region.
3. Choose **Inspect original and proposed targets** and Execute. The read-only
   report shows the comment text, original/proposed byte ranges, paths, revision
   identities, hashes, and matching status. Pan/scroll to inspect long identities.
4. Return to the Explorer using its numbered tab. Preview the comment again.
   Only a unique, complete match in a writable review offers **Accept linked
   successor · copies entire comment INCLUDING any #approved**. Choose it and
   Execute only after checking that this is the intended target.
5. The save adds a new comment linked to its original, retaining both versions of
   the source evidence in the same review file. Close/reopen the game or review:
   both comments remain in Comments, and their provenance records the link.

`MISSING`, `AMBIGUOUS`, or `INCOMPLETE` offers inspection only. This is bounded
literal matching within the current observed AFTER corpus of the selected lane,
not semantic equivalence, a fresh disk query, or a search of every repository file.
Refresh explicitly before previewing if disk changed. A refreshed/replaced review
invalidates older previews; an external edit to the review file prevents overwrite.

The command equivalent is:

```text
sfm action invoke sfm:review/comment/successor/preview "human:release-review:1" 1.19.2
```

The quotes matter for IDs containing colons. Context choices and suggestions
generate the quoted arguments for you. Reports do not save any source content.
Acceptance saves the exact observed evidence, not a later reread of the disk.

**A relocated approval is not whole-file approval.** In the disposable walkthrough,
inserting 63 bytes before an approved `[0..138)` region moved its successor to
`[63..201)`. The newly inserted prefix remained unreviewed: the status correctly
showed one raw approved match but zero completely approved surfaces and one
remaining unit. The original historical approval stayed intact.

Evidence: GUI2 run `sfm-title_screen-20260908-060805-255`, figures9 (MISSING, no
acceptance),22 (RELOCATED and warning),24–25 (saved successor). GUI4 run
`sfm-title_screen-20260908-061550-197`, figures5–6 (reopened comments and mouse
context action),11 (labelled ranges),14 (conservative coverage).

Known navigation rough edges: refresh currently requires Explorer focus and can
reset its lens to Changes; selecting a palette choice can move Execute as the
palette resizes. These do not change the saved evidence, but remain tracked polish.

## Read retained evidence without resolving the repository

In the normal file Explorer, right-click a v3 `.sfm-review.json` file and choose
**Open retained review evidence (offline, v3)**, then Execute. This opens another
ordinary Explorer; it does not replace your active writable review.

- Expand **Retained comments · offline evidence**, then a comment.
- Open **comment value** to read its exact text.
- Expand **matches**, then open a match to read its retained source version.
  Java evidence retains Java syntax highlighting; this is not a read of today's
  file on disk.
- Open **Status · retained evidence only** for counts and limitations.
- **Missing embedded evidence** identifies source bodies that still require Git.
  A missing body is not treated as an empty document or a successful review.

This view reads only the review file. It can show your retained comments and
evidence even when the original repository is unavailable, provided those bodies
are embedded. It deliberately makes **no current release completion claim**.
Use the normal review workflow to evaluate current source coverage.

The command equivalent uses a quoted path:

```text
sfm action invoke sfm:review/evidence/open "D:/path/to/review.sfm-review.json"
```

GUI4 evidence: `sfm-title_screen-20260908-073712-569`, figures15–20 show an
exported copy's original comment and Java evidence;22 confirms return to the title
screen;24–28 reopen via the normal file context menu and read offline status.
The original writable review path, epoch, generation and file hash were unchanged.

## Export a single-file evidence copy

1. Open your saved v3 review normally. Right-click its freshness banner.
2. Choose **Preview self-contained comment evidence copy…**, then Execute.
   The generated command proposes a new filename beside the original; you can
   change that destination before executing. Existing files are never overwritten.
3. Choose **Inspect paths, size and portability before saving**. The report shows
   original/output bytes, embedded body count and any Git dependencies. No file
   has been created by previewing.
4. Right-click the report (or Alt+Enter) and choose **Create the inspected evidence
   copy · original unchanged**, then Execute. Alternatively choose **Discard this
   export preview · create nothing**.
5. After saving, choose **Open retained evidence in the new copy**. The original
   review remains active; this opens a separate evidence-only Explorer.

The portable choice embeds missing Git bodies needed by retained comments. It
does **not** capture every uncommitted file you have browsed. Non-Git evidence
already captured when commenting remains in the single file, deduplicated by hash.

**Preview Git-dependent evidence copy…** is an explicit alternative, not
an offline copy. It verifies exact Git references before removing redundant Git
bodies. It can also recognize captured non-Git bytes that now exist at the same
path in a later commit, when the repository is unambiguous. Original comment
identities and approvals stay unchanged; only the storage location changes in the
new copy. Unmatched captures remain embedded. The report shows separate Git storage
references and actual output size (small files can grow due to reference metadata).
The preview can refuse missing Git objects; it never drops evidence merely because
it could not read the repository. Export the Git-dependent copy with **portable**
to restore its exact embedded evidence, even if the working files have since changed.
This recognition path passed both GUI2 and GUI4 walkthroughs, including opening
both historical source versions from the self-contained copy.
In the offline evidence view, missing bodies mean the view did not consult Git—not
that Git has been checked and found broken. Open the missing-evidence row for the
filename, pinned storage location and recovery instructions. A fully embedded copy
instead shows **All retained evidence is embedded**.

Changing/reopening the active review invalidates its old preview. A changed review
file on disk also prevents publication. Once confirmed or discarded, its report
no longer offers another confirmation. Cross-directory copies disclose rebased
repository hints: the retained evidence can travel, but the original live sources
do not travel with it.

GUI2 evidence: `sfm-title_screen-20260908-074909-180`, figures7–10 show report,
right-click actions and mouse confirmation;12 shows consumed-preview actions gone;
14–17 discard another preview without creating its destination. The test copy was
10,929 bytes, with two embedded bodies totalling339UTF-8 bytes. Original hash stayed
unchanged. These are fixture measurements, not promised sizes for every review.

Git-reuse GUI2 evidence: `sfm-title_screen-20260908-083025-078`. Figure14 reports
one embedded body/138bytes and one Git-dependent document;15–17 show mouse-driven
confirmation.20–21 show offline missing evidence;22–24 reopen the copy with the
companion and preserve both comments.28 shows portable rehydration restoring two
bodies/339bytes with zero Git dependencies;34 reopens that evidence offline.
The 10946byte disposable original was unchanged (10996bytes when normalized by
the preview serializer); the Git-dependent copy was10983
bytes and its portable re-export11313bytes. The latter retains storage provenance
as well as bodies. These small-fixture sizes are not a compression guarantee.

## Checking comment-refresh responsiveness

The Changes Explorer now reuses unchanged source pairing, diff recipes and reveal
indexes after a comment update. This adds no new command or required cache file.
To try it, keep Changes open, open an After document, select a range and add a
comment through Alt+Enter. Return to Changes and expand that document's comments:
the new comment should appear without losing existing expansion state. Open the
comment to read its text. Right-click a Changes comment row for **Open comment as
text**, **Reveal comment in Explorer**, **Show comment targets in Explorer**, or
**Inspect comment selector**. Targets opens the Comments lens at the matching
branch; expand it and double-click a match to open the exact source range. This
does not silently pick one target when there are several. The Explorer target
button reveals that source and names the file in its notification, retaining the
exact row address for copying/context actions. A second Explorer must retain its
own expansion state. Reopening the review invalidates old comment-row menus.

In a source preview, Shift+Arrow and Shift+Home/End extend an exact selection.
Alt+Enter then offers a comment on that byte range. Plain Left/Right collapses a
selection to its respective edge. Closing the source while its comment saves
does not reopen it; surviving review Explorers still refresh after the save.

This does not waive saving or validation. Changing source bytes invalidates source
reuse; old comments remain pinned rather than approving those new bytes. Closing
and reopening still reconstructs the view from the saved single-file review.

Developer measurement on the 31 MB legacy review (1999 documents/1846 comments)
observed warm projection costs of124–153ms versus2330–2640ms uncached, with17154
nodes compared for equality. These are projection-only measurements, not promises
about total comment-save latency. GUI2 and GUI4 verification passed: a saved comment
survived JVM restart, a second comment reused the identical evidence table, both
appeared under After, and source reveal worked. Evidence galleries are
sfm-title_screen-20260908-094710-712 and sfm-title_screen-20260908-095702-639.

GUI4 navigation evidence: sfm-title_screen-20260908-105446-887. Figure13 confirms
Shift+Left selected[191..192);18–21 hold a save, close the source, release the save,
and expand the new comment.22 shows named comment actions;24–26 navigate matches
back to the exact selected semicolon.27 shows `Revealed Example.java in this
Explorer`;33 reads the comment text. Fit Width can make long single-line comments
small in narrow panels; enlarge the area or zoom to read comfortably.

## CLI status for sparse reviews

Use `sfm-propagate-changes.exe --output-format json review session status --file
"<review-path>"` to resolve current source and inspect remaining/approved surfaces.
Exit5 means the review is still in progress; read its structured status rather
than treating this as an execution failure. This command does not rewrite the
review. The older `freshness --file` route accepts v1/v2 pinned observations, not
a sparse v3 ledger. In-game **Recheck review freshness** compares the captured
observation; **Refresh from current source** obtains a new one. CLI consumers
needing that distinction can use `resolve` and `freshness-of --request-file` with
the exact observed source bindings, rather than treating live disk as a HEAD alias.
