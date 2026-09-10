# Reviewing precise surfaces in game

This guide accompanies `docs/tasks/release review exact coverage acceptance plan.md`.
The portable `.sfm-review.json` file owns the comments, selectors, query and work
cursor. Cache files are not the review authority. The generated before/after
documents are immutable; a comment does not edit the code.

The tools are installed and current; **you do not need to run `install.ps1`**.
The test games have exited. From the 1.19.2 checkout, start a normal game with:

```powershell
sfm-propagate-changes.exe run client --branch 1.19.2
```

## Start with the disposable acceptance review

The acceptance run leaves its test-only copy at:

```text
D:\Repos\Minecraft\SFM\repos2\1.19.2\platform\minecraft\runGameTestPreview\sfm-puppet\real-release-review-journey.sfm-review.json
```

This copy contains automated test comments, **not maintainer approvals**. It is
based on the existing real review's pinned `58ed4e431` candidate, not the latest
working-tree contents. `HEAD` in a review query means that review's pinned
candidate. A new source edit does not silently rewrite the reviewed snapshot.

1. Open the command palette and run:

   ```text
   sfm action invoke sfm:review/session/open D:\Repos\Minecraft\SFM\repos2\1.19.2\platform\minecraft\runGameTestPreview\sfm-puppet\real-release-review-journey.sfm-review.json
   ```

   This opens the disposable review writable so the commenting steps below work.
   Use `sfm:review/session/open/read_only/view` instead to inspect without writing. Keep
   your actual review in a different file; rerunning the stage replaces this copy.

2. In Changes, use Ctrl+F to find `SFM.java`. Press **Enter** to return focus to
   the rows without clearing the filter. Escape clears a nonempty filter.
   Expand a matching path and open Before, After, Text diff or Structured diff.
3. Select a short stretch of source text; Alt+Enter or right-click opens its
   actions. Choose the exact-byte Comment option, then `#approved`,
   `#needs-change`, or Other. Other uses your preferred editor, which may differ
   from the source preview. Click its text area, type the note, then use its
   Save/Done control (Shift+Enter in V1; Ctrl+S in V3). Wait for the saved
   confirmation before closing.
4. Look for the comment underline/gutter marker. Inspect it to see the comment
   value, selector and matched source ranges. In generated diffs, added/removed
   backgrounds and comment underlines are separate. Java foreground syntax is
   highlighted from the original sources, then mapped into the diff. Other
   languages can remain neutral when no syntax provider is enabled.
5. In the review Explorer, click the lens button at top right and choose
   **Remaining work · 1.19.2**, then Execute. This saves the queue asynchronously
   when writable and displays it in the same Explorer. Alternatively, the
   explicit view-only command remains:

   ```text
   sfm action invoke sfm:panel/open sfm:explorer/release_review/query remaining intersect 1.19.2 HEAD
   ```

   Expand a unit and its **Unreviewed surface** group. Its children open exact
   uncovered ranges labelled **Unreviewed before** and **Unreviewed after**.
   The coverage group reports approved/required source bytes separately for
   each side; the Before/After entries still provide full context. A
   small approval must leave the rest—including the other changed side—here.
   Wait for the refreshed index/children to finish loading after saving a comment;
   old visible rows are retained during refresh but are not current authority.
   To narrow the view to partially approved changes, use
   `#approved intersect remaining intersect 1.19.2 HEAD` as the query instead.
   The lens menu also offers **Exact coverage and blockers**. Retained text
   filters are stated explicitly and can be cleared in that menu; switching
   lenses does not silently clear or reinterpret them.
6. Close the game and reopen the same review file. Your comments and persisted
   work cursor should remain. To persist the queue explicitly:

   ```text
   sfm action invoke sfm:review/session/query/activate remaining intersect 1.19.2 HEAD
   ```

## Understand the counts

- `#approved intersect 1.19.2 HEAD`: units touched by an approval comment.
- `effective(#approved) intersect 1.19.2 HEAD`: units whose entire required
  before/after surface is covered by eligible current approvals without blockers.
- `remaining intersect 1.19.2 HEAD`: units still needing work. Inspect their
  exact range children to see what is missing.

For example, approving 2 bytes out of a 100-byte required surface leaves 98
bytes unreviewed. Multiple approvals can jointly cover a unit; a gap is never
filled by proximity. Byte counts are labelled as bytes, not glyph percentages.
Unavailable/operation-only surfaces remain explicitly incomplete. Automated
tests and successful builds do not grant human release approval.

Existing comments are not rewritten by this update. A review can show more
remaining work because a partial overlap no longer approves a whole change;
old completion attestations also need reevaluation under the stronger semantics.

## Repeat the process-restart proof

These two commands must run in order as separate game processes:

```powershell
sfm-propagate-changes.exe puppet run sfm:title_screen_exact_release_review_stage --branch 1.19.2 --variant 1920x1080@2 --log-filter info --log-file platform/minecraft/build/rcov-real-stage.ndjson
sfm-propagate-changes.exe puppet run sfm:title_screen_exact_release_review_resume --branch 1.19.2 --variant 1920x1080@2 --log-filter info --log-file platform/minecraft/build/rcov-real-resume.ndjson
```

The stage overwrites only its disposable test copy. Do not store personal review
work at that test path. The resume removes only that copy's rebuildable local
recovery/lease state, verifies a distinct JVM and checks the same portable bytes,
comments, work cursor and exact coverage. Both use virtual Minecraft input.

## Verified examples and limits

- [Real SFM.java remaining range after restarting Minecraft](../platform/minecraft/build/sfm-toolchain/artifacts/game-test-preview/runs/sfm-title_screen-20260905-151611-643/title_screen_exact_release_review_resume/1920x1080_2/figure_01_exact-review-resumed.png)
- [Syntax-coloured diff with independent review-comment markers](../platform/minecraft/build/sfm-toolchain/artifacts/game-test-preview/runs/sfm-title_screen-20260905-151759-429/title_screen_release_review_explorer_ux/3840x2130_4/figure_02_release-review-approved-comment.png)

The real test hunk has one approved byte out of 285, leaving three exact ranges
totalling 284 bytes. Java and the installed Rust CLI agree. The images were
captured through virtual game inputs without moving the OS cursor. These
screenshots/data live in exported build artifacts, while your portable review
file owns the durable review work.

The September 5 evidence above predates the overnight responsiveness work. The
language-resource initialization failures were repaired September 6 and the
installed-worker integration was exercised explicitly; its opt-in test remains
an abort in an unconfigured full-suite invocation. Current test/live evidence
and tool freshness are recorded in the
[overnight plan](tasks/release%20review%20overnight%20readiness%20plan.md).
These checks are not approval to publish a release. Broader Explorer/icon work
and performance outside the covered review loop remain separately tracked.
