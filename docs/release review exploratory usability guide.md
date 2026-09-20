# Trying the review loop

This guide follows the disposable September 6 overnight exploration. It is not a release
approval. The review pins an earlier candidate commit; its HEAD label means
the review's pinned candidate, not today's moving checkout.

## Start with a safe copy

From the repository, launch the current development client:

```powershell
sfm-propagate-changes.exe run client --branch 1.19.2 --wait-for-build-lock
```

**User install required: no** at the September 6 final overnight checkpoint. The Rust CLI
was checked and reinstalled after repairing the Java test-resource classpath
and held-puppet completion reporting (installed SHA256 starts `AB53A790`);
the final Java suite passed 1,819 tests, with its one opt-in worker test also
passing in the separately configured integration run. Final authoring/restart
puppets and a 31-step read-only review walkthrough passed after the last source
changes. All acceptance games/helpers exited normally; the scoped process
inventory is empty. Current Java/live verification and final process state are recorded in
[the overnight plan](tasks/release%20review%20overnight%20readiness%20plan.md).

For testing without adding notes to your real review, use this already-created
disposable copy in the command palette:

```text
sfm action invoke sfm:review/session/open/view D:/Repos/Minecraft/SFM/repos2/1.19.2/platform/minecraft/runGameTestPreview/sfm-puppet/rno-20260906.sfm-review.json
```

Do not paste this into game chat as a server command. It is a local palette
action. The open action takes the rest of the line as the path, without quotes.
Alternatively, Ctrl+Shift+E opens the Explorer; navigate to the review file,
right-click it (or use Alt+Enter/Menu on the selected row), choose **Open release
review writable**, then Execute. Double-clicking the JSON is raw text opening,
which correctly refuses an oversized review ledger. `open/view` opens writable;
`open/read_only/view` inspects without saving. Do not append a `writable` word:
the remaining command text is the path.

## Inspect and comment

1. Search for SFM.java in the Changes Explorer. This is fuzzy search, so it can
   match more than that exact filename. Context ancestors stay visible.
2. Expand the actual SFM.java row. Open before, after, text diff or structured
   diff. Children appear only when requested; wait for the loading row to finish.
3. In either diff, Java syntax colours and red/green change backgrounds carry
   separate information. A structured diff is still a source-mapped text
   presentation, not a side-by-side graphical diff.
4. Drag across a small changed source range and use right-click or Alt+Enter.
   Choose the exact-byte comment target when that is what you mean; a symbol
   target can legitimately describe a different semantic object.
5. Choose Other and write a clearly test-only note. Submit with the editor's
   Done/Save control (Shift+Enter in V1; Ctrl+S in V3). Preparing/saving is
   asynchronous: you can continue interacting while the pending message is
   visible. Wait for saved confirmation before closing the game. Right-click
   a pending notification to cancel while the operation is still cancellable;
   a cancellation or failure keeps the old saved review and your draft.
   Edits made after submitting Save/Done are not silently discarded.
6. The saved range gains a review decoration and gutter marker. Its context
   choices expose value, selector, matches and provenance. The Comments lens
   also lets you expand the comment as an object: value is its text, matches
   leads back to its exact source, and provenance is not part of the note text.

The overnight copy contains four test-only human-provenance notes on one exact
SFM.java range [0,25), including a long two-line note with `café` and `雪`.
They were saved and reopened across separate JVMs. They are not release verdicts.
Recent comment templates come from human-authored notes, not generated change
records. Long menu labels are summaries: hover for more, or right-click for the
full choice details. Open a comment's Value to read its complete text.

## Find what is still unreviewed

In the review Explorer, click the top-right lens button (Changes, Comments, or
Work queue). Choose **Remaining work · 1.19.2**, then Execute. This prepares and
saves the queue before displaying it in the same Explorer. The all-lanes choice
uses all configured branches. In a read-only review the view is explicitly
temporary and does not save a queue.

The same action is available from the palette while that review Explorer is
the originating panel:

```text
sfm action invoke sfm:review/work/remaining 1.19.2
```

Expand a unit and its **Unreviewed surface** group to navigate remaining exact
ranges labelled **Unreviewed before** or **Unreviewed after**. Coverage reports
the sides separately. A concern or arbitrary note does not count as approval.
Partial approval does not make an incompletely covered unit disappear. Use
**Exact coverage and blockers** from the same menu for the review-wide status.

Switching lenses deliberately retains the Explorer's text filter. The menu
heading and lens tooltip say so; **Clear retained filter** clears that exact
Explorer. Ctrl+F or the filter bar edits it. An empty Comments lens with `.java`
still active can simply be filtered, not missing your saved note.

For an arbitrary expression, the canonical queue command remains available:

```text
sfm action invoke sfm:review/session/query/activate remaining intersect 1.19.2 HEAD
```

The review JSON is the portable session file. Reopen that same file after
closing/restarting, then choose **Switch to Query** in the lens menu to display
the saved expression without activating a new queue. Use **Show current** in
the toolbar below the filter to reveal the saved unit and its source again.
This does not advance or rewrite the review. The work cursor belongs
to the review, while each Explorer's current text filter is local presentation.
Do not use attestation actions for a usability test.
See the [exact coverage guide](release%20review%20exact%20coverage%20guide.md)
for approval semantics and fail-closed completion.

### Move through the saved queue

Verified in independent games I/J on September 6: the saved cursor/deferred list
survived restart, and returning to an already-open source selected its existing
tab without taking keyboard focus away from Explorer. Screenshots are in the
[queue restart gallery](../platform/minecraft/build/sfm-toolchain/artifacts/game-test-preview/runs/sfm-title_screen-20260906-065347-017/index.html)
(figures 7, 11, 18). This is disposable test progress, not release approval.

The Work queue lens has **Previous**, **Next**, **Defer**, **Resume**, and
**Show current** controls beneath its filter. Hover for the full meaning and
canonical command. Tab reaches these controls; Left/Right moves between them
and Enter or Space invokes the focused one.

In a read-only review, Show current still works. Controls that would save a new
cursor are muted and report that the review must be opened writable.

- **Previous/Next** save the adjacent non-deferred unit and open its first exact
  after range, or before for a deletion. They do not wrap at either end.
- **Defer** saves the current unit in the deferred list and advances. At the
  final unit there is no next cursor: use Resume when ready to return to it.
- **Resume** removes the first deferred unit from that list and makes it current.
  A deferred unit outside the active query is reported rather than silently lost.
- **Show current** opens the saved position without saving or advancing. The
  `[Saved cursor]` label identifies that unit; the blue Explorer highlight only
  identifies the row you are browsing. Browsing, filtering and opening other
  rows do not change saved progress.

Queue changes use the same pending/saved/cancelled feedback as comments. Wait
for **Work cursor saved** before quitting. Navigation is not an approval;
comments and exact coverage remain separate. A retained text filter may hide
the saved row even though its source preview opens correctly—clear the filter
to see it. Expand the unit's children to inspect its other sides/ranges.

These same commands work from the originating review Explorer:

```text
sfm action invoke sfm:review/session/work/next
sfm action invoke sfm:review/session/work/previous
sfm action invoke sfm:review/session/work/defer
sfm action invoke sfm:review/session/work/resume
sfm action invoke sfm:review/session/work/show
```

## Short notifications, full traceability

Open an ordinary small file such as options.txt in an Explorer, then click its
target-block button while that file is the most recently focused document.
The toast says **Revealed options.txt in this Explorer**, not an entire absolute
path.

- Hover the underlined filename to see the canonical URI and pause expiry.
- Click that filename to copy its full path. Actual clipboard access is
  verified; a locked desktop can deny it and should report unavailability.
- Right-click the notification for **Copy full path**, **Open path as text**,
  **Open path in Explorer**, and the existing copy/pin/dismiss actions.
- These actions retain the notification's path, not whichever file gets focus
  afterwards. Expired notifications and unavailable resolver authority fail
  instead of guessing a replacement.

Open-as-text opens the referenced file's contents; it does not open a text
buffer containing just the address. Copy full path supplies the address.

## Honest limits

The tested large-result SFM.java expansion now takes about 32 ms dispatch,
versus 1.57 seconds in the preceding run. That is one measured interaction,
not a promise about every file, frame rate or layout.

The roughly 30 MB review still takes time to parse, evaluate and save; those
operations now run asynchronously, with pending versus saved states kept distinct.
This is not a claim that every Explorer refresh or every frame is instantaneous.
Value/target previews reuse their review-owned tabs; unrelated or edited tabs
must not be overwritten. Closing their originating workspace while work is
pending must not resurrect it.

The adaptive test records virtual key/pointer callbacks, completed-render
screenshots, dispatch timings and observations. It never moves the OS cursor.
Screenshots prove presentation, not subjective comfort or platform clipboard
success. Automated notes and green tests do not replace your release review.

The former Minecraft language/resource bootstrap failures were repaired and
the installed-worker integration was run explicitly with its required settings.
The ordinary full suite still reports that opt-in integration as an abort when
those settings are absent; it is not counted as a passing test. Current counts
and affected-path reruns are kept in the overnight plan, rather than copying
September 5 results as proof of today's source.

## Evidence you can open

The final post-integration [read-only walkthrough](../platform/minecraft/build/sfm-toolchain/artifacts/game-test-preview/runs/sfm-title_screen-20260906-114145-150/index.html)
preserves the same 2,921 comments and review file hash. Figures 16/20 open the
long Unicode note and its exact SFM.java match; 27/30 retain the saved queue and
reuse previews on repeated Show current. The editor remains a canvas: long
unwrapped lines may need horizontal panning rather than automatic line wrapping.

The September 6 review matrix exercised 1920x1080 at GUI 1, 2, 3, 4 and Auto,
2000x2000 at GUI 4, and a narrow split. Each used an independent disposable
review, selected exact source bytes, saved a Unicode test note, and restored the
saved work queue. Long candidate tooltips now stay inside the viewport at large
scales. These are actual rendered/input checks, not only accepted CLI arguments.

- [GUI 4 repaired tooltip and review flow](../platform/minecraft/build/sfm-toolchain/artifacts/game-test-preview/runs/sfm-title_screen-20260906-082602-406/index.html): figure 18 shows the bounded tooltip; 32 shows the saved source and scroll response.
- [Auto scale](../platform/minecraft/build/sfm-toolchain/artifacts/game-test-preview/runs/sfm-title_screen-20260906-083540-285/index.html): actual effective scale 4, with exact-region comment and queue navigation.
- [Square window](../platform/minecraft/build/sfm-toolchain/artifacts/game-test-preview/runs/sfm-title_screen-20260906-084119-270/index.html): actual 2000x2000@4. The redundant Show current queue-refresh race observed here was subsequently repaired and replayed below; this is not a claim of zero remaining UX defects.
- [Show current after the repair](../platform/minecraft/build/sfm-toolchain/artifacts/game-test-preview/runs/sfm-title_screen-20260906-090813-499/index.html): figures 17/18/20 show initial and repeated navigation retaining the queue and reusing the exact source preview, without rewriting the review. The [baseline walkthrough](../platform/minecraft/build/sfm-toolchain/artifacts/game-test-preview/runs/sfm-title_screen-20260906-085156-360/index.html) figure 48 preserves the previous blanking behavior for comparison.

- [Mouse-first remaining-work walkthrough](../platform/minecraft/build/sfm-toolchain/artifacts/game-test-preview/runs/sfm-title_screen-20260906-055929-361/index.html):
  figure 6 shows the new menu, 12 confirms queue persistence with a retained
  filter, 25 opens the exact uncovered SFM.java range, and 31 shows coverage.
- [Independent-JVM restart](../platform/minecraft/build/sfm-toolchain/artifacts/game-test-preview/runs/sfm-title_screen-20260906-060719-754/index.html):
  figure 8 shows restored work with byte-range labels; 11 reopens the complete
  long Unicode note; 14 navigates its original source target. The portable
  file hash remained unchanged throughout this read/navigation session.
- [Earlier notification-path walkthrough](../platform/minecraft/build/sfm-toolchain/artifacts/game-test-preview/runs/sfm-title_screen-20260905-203053-825/index.html):
  figures 24 and 29 show path hover and contextual notification actions.

These are local ignored test artifacts, not the durable review authority.
The [overnight plan](tasks/release%20review%20overnight%20readiness%20plan.md)
retains cancellation/retry, preview-reuse, delayed-navigation and long-note
evidence alongside the current checks and completed stretch checkpoints.

The final stretch also adds contextual icon rules, including a copyable prompt
for asking an external chat agent to propose a rule without exporting the item
catalogue or automatically calling a model. See
[Choosing Explorer icons with reusable rules](contextual%20itemstack%20preview%20rules%20guide.md).
