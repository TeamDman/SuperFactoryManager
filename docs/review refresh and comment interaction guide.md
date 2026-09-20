# Review refresh and comment interaction

**Status: September 8 review revision — validated and ready for feedback.**
The owning [plan](tasks/review%20refresh%20and%20comment%20interaction%20revision.md)
records the live GUI2/GUI4 evidence and final operational checks.

Validation: 1,953 Java tests passed, zero failures, three opt-in aborts; Rust
check-all/install and final datagen passed. Disposable virtual-input walkthroughs
covered GUI scales 2 and 4 without moving the OS mouse. User reviews were not
modified. No installer step is required; test clients have been closed.

## Include changes made since a capture

1. Open your `.sfm-review.json` in the review Explorer.
2. Open the freshness banner's choices. If necessary choose **Recheck review
   freshness** and wait for the repository check.
3. Choose **Include latest changes · 1.19.2 · new review, retain capture scope
   and exclusions**, then Execute.
4. Wait for the capture notification and opening of the new Changes explorer tab. The new file is a
   `working-tree-<unique id>.sfm-review.json` sibling of the original review.

Large captures can take a minute or more. The progress notification stays visible
while the background operation runs; you can continue using the Explorer. A
failure is reported rather than silently retargeting the original review.

The action captures current disk content, including committed changes since the
baseline and uncommitted content allowed by the saved policy. Existing capture
scopes, exclusions and the untracked-file policy are retained. The old review is
also excluded so it does not become source content in the successor capture.
For a commit-only review, the action starts a repository-wide disk capture.

This does **not** overwrite your old review or transfer its approvals to new
bytes. Reopen the original review to access its original evidence. The alternative
**Choose a different capture scope…** action solicits a new scope explicitly.

An unchanged HEAD is not proof that disk content is unchanged. Freshness details
report those two checks separately. The working-tree `changes` array currently
is not a per-path capture diff; consult `capture_scope_matches` and its diagnostics.

## Read an existing comment

- Expand a **before** or **after** document row to see its applicable comments.
  Use its chevron to expand; Enter or double-click on the row opens the source,
  even when it has comment children.
  Open a comment child to read its complete value in a read-only text editor.
- Right-click a highlighted source region and choose **Open comment as text**.
  This opens a text-editor tab in the current area; the source tab remains available.
- Choose **Reveal comment in Explorer** separately to navigate to the comment's
  value in the Comments explorer, without opening another text editor.
- Overlapping comments offer a choice of which comment to inspect.
- **Actions for current text selection…** retains access to ordinary selection
  actions. Left-click and dragging in the source still selects text.

The gutter **R** is a generated `#release-change` marker: it identifies changed
surface requiring review, not a one-letter comment or an approval. Hover details
explain the marker; the comment value contains the actual generated explanation.
An explicit saved style may still specify an underline; the ordinary generated
release-change style no longer adds one automatically on every line.

## Compare before and after

Expand a changed file. It has **before**, **after**, **text diff (inline)**,
**structured diff (inline)**, **text diff (split)** and **structured diff (split)**.
An absent source side is explicitly marked missing.

Split views put Before and After on one canvas. Drag within either column to
select that side's source text; crossing the gap does not select the other file.
They initially show the first present source at normal reading size. Long lines
can extend outside the viewport; pan across or use Fit Width to see both columns.
Ctrl+C copies source text rather than diff markers or alignment padding. Use
Ctrl+A to select all displayed source slices on the active side, including their
original line endings. Arrows and Home/End move within that side, Shift extends
selection, and Ctrl+Left/Right moves by words (underscores remain in a word).
Use
middle-mouse drag to pan, the wheel to zoom, and Ctrl+Shift+9 to fit the split width.
Added/removed backgrounds are independent of syntax foreground colours. A
structured fallback is labelled rather than represented as successful Java analysis.

## Launch and suggested first test

From `D:/Repos/Minecraft/SFM/repos2/1.19.2`:

```powershell
sfm-propagate-changes.exe run client --branch 1.19.2 --wait-for-build-lock
```

Open your review writable using the Explorer file context menu. First test an
existing highlighted comment, then expand the Before/After comment children.
For a two-sided Java example in the saved September 7 review, find
`SFMTextEditScreenV2Registration.java` and open **structured diff (split)**.
Focus the editor and press Ctrl+Shift+9. Before contains the old method; After
includes `context.preferPush()`. Select one side and Alt+Enter to attach a comment
to exact selected bytes. The two sides remain separate pinned targets.

Structured views show changed declarations, not necessarily whole files. An
addition-only structured result can have an empty Before column even when the
file itself existed previously. Long lines become small when fitting both columns;
wheel zoom and middle-button pan restore a readable local view.
