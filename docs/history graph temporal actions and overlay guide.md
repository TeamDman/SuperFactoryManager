# History Graph, temporal actions, and gameplay overlay guide

This guide covers the user-visible work completed by the supervised-trajectory
milestone and its six continuation slices. It applies to the 1.19.2 development
branch and the currently installed SFM tools.

The short version is: the overlay is a non-pausing host for the same **History
Graph** that can be opened as a workspace panel. It lets you keep an episode's
actual history, selected future, instruction pointer, and alternatives visible
while ordinary gameplay continues. It does not independently record every game
event, rewind the Minecraft world, or invent an episode when none exists.

The implementation plan and acceptance evidence live in the
[snapshot episodes and deterministic action environments plan](<tasks/snapshot episodes and deterministic action environments plan.md>).

## Before starting

- Open the SFM command palette with `Ctrl+K`. It is also listed as **Open Command
  Palette** in Minecraft's Key Binds screen if you have changed the default.
- Commands in this guide beginning with `sfm action invoke` are entered in that
  in-game palette.
- `Ctrl+1`, `Ctrl+2`, and so on focus visible workspace slots from left to right.
- The examples deliberately use the bounded **Temporal Numbering Chamber**. It
  is an in-memory test environment; it does not edit the SFM checkout.
- The **Workspace Counterfactual** is also an in-memory fixture. Its `A.java`
  and `B.java` are virtual files, not files in your checkout.

## What the overlay is showing

The History Graph keeps several related concepts separate:

| Concept | Meaning |
| --- | --- |
| Actual history head | The immutable state revision that is authoritative now. |
| Selected route | A retained candidate future chosen for inspection or execution. |
| Instruction pointer (`IP`) | The next step on the selected route. It is not the actual head. |
| Projection frontier | Candidate work the planner has discovered or is still evaluating. |
| Retained alternative | A sibling history or plan that remains available after undo, replan, or divergence. |
| `SUPERVISION_READY` | Automated predicates and hard checks passed. It does **not** mean a human approved anything. |

The graph's visual language is:

- an emphasized solid path is the committed prefix;
- an emphasized dashed path is the selected projected suffix;
- a thin neutral path is a retained alternative;
- amber identifies open/frontier work;
- muted nodes are closed or already explored;
- red identifies barriers, conflicts, stale preconditions, or unknown futures;
- `IP` marks the next selected step; and
- the target marker identifies the supervised goal.

If the overlay says that `focused` matched nothing and it is waiting for a push,
the overlay itself is working: there is simply no live trajectory machine to
display. Lab 1 demonstrates a machine inside the workspace; the in-world puppet
demonstrates a machine whose lifetime is long enough to populate the overlay.

There is one lifecycle distinction to keep in mind:

| Surface | Current availability |
| --- | --- |
| Chamber, History Graph, Candidate History, comments, comparison, replay, and counterfactual labs | Manually usable in the ordinary SFM workspace. |
| Overlay visibility, mode, placement, z-order, and input lifecycle | Manually controllable in an ordinary loaded world. |
| A populated trajectory machine that remains alive after its workspace closes | Not yet supplied by the ordinary chamber; the accepted in-world demonstration is seeded by `in_world_history_graph_overlay`. |

The Temporal Numbering Chamber registers its machine while its panel is alive
and unregisters it when that panel closes. Consequently, Lab 1 proves the graph
inside the workspace, while the in-world overlay puppet proves the overlay host
with an independently retained bounded machine. The two are intentionally not
described here as if persistent background episode ownership already existed.

## Lab 1: create an episode and watch it execute

This is the best first tour because every later feature builds on the episode it
creates.

1. Open the command palette and run:

   ```text
   sfm action invoke sfm:panel/open sfm:chamber/temporal-decimal-numbering
   ```

   A writable editor opens with:

   ```text
   - apples
   - bananas
   ```

   Each chamber opening creates a fresh episode instead of reusing an old one.

2. Open the History Graph to the right:

   ```text
   sfm action invoke sfm:panel/open/right sfm:episode/history
   ```

   You should now have the chamber in slot 1 and History Graph in slot 2.

3. Ask the bounded planner for a route:

   ```text
   sfm action invoke sfm:episode/trajectory/plan focused
   ```

   The selected future should contain two semantic operations: select the two
   hyphen markers, then replace them with a decimal sequence. Nothing has been
   committed merely because the route is visible.

4. Execute exactly one planned operation:

   ```text
   sfm action invoke sfm:episode/trajectory/step focused
   ```

   Only the selection operation commits. The actual head advances once and the
   `IP` moves to the numbering operation.

5. Finish the bounded route:

   ```text
   sfm action invoke sfm:episode/trajectory/run focused 32
   ```

   The editor should become:

   ```text
   1. apples
   2. bananas
   ```

   The route reaches `SUPERVISION_READY`; this is machine readiness, not review
   approval.

6. Focus the chamber with `Ctrl+1`, then press `Ctrl+Z` once.

   The document head moves back, but the numbered child remains in the graph.
   Undo is therefore a branch operation, not destructive deletion of redo
   history.

7. Create a sibling history through ordinary editor input:

   - Press `Ctrl+Home`.
   - Press `End`.
   - Press `Enter`.
   - Type `- apricots`.

   The source should now be:

   ```text
   - apples
   - apricots
   - bananas
   ```

8. Try the old plan without replanning:

   ```text
   sfm action invoke sfm:episode/trajectory/step focused
   ```

   Its parent precondition is stale, so the machine pauses and does not partially
   mutate the new document.

9. Replan and run:

   ```text
   sfm action invoke sfm:episode/trajectory/replan focused
   sfm action invoke sfm:episode/trajectory/run focused 32
   ```

   The old two-item route remains inspectable, while the new route produces:

   ```text
   1. apples
   2. apricots
   3. bananas
   ```

### History Graph controls

When the History Graph panel or interactive overlay owns focus:

| Input | Result |
| --- | --- |
| `Up` / `Down` | Select the previous or next graph row. |
| `Home` / `End` | Select the first or last row. |
| `Page Up` / `Page Down` | Move by one visible page. |
| Mouse wheel | Move the selected row incrementally. |
| Left click a row | Select that row. |

The compact buttons invoke the same registered actions available from the
palette:

| Button | Action |
| --- | --- |
| Plan | Search for a supervised route. |
| Step | Commit one selected step. |
| Run | Execute the selected route within its bound. |
| Pause | Stop bounded execution without discarding the route. |
| Replan | Retain the old plan and produce a new plan from the current head. |
| Route | Choose among retained routes. |
| Cost | Inspect the selected route's cost evidence. |
| Select `-` | Select all matching hyphen markers semantically. |
| Number | Replace the selected markers with a decimal sequence. |
| Exact | Replay a recorded witness only against its exact parent. |
| Rebase | Re-evaluate recorded semantic intent against another parent. |
| Causal | Inspect raw input, binding, semantic action, and transition provenance. |

The corresponding direct commands are:

```text
sfm action invoke sfm:episode/trajectory/plan focused
sfm action invoke sfm:episode/trajectory/step focused
sfm action invoke sfm:episode/trajectory/run focused 32
sfm action invoke sfm:episode/trajectory/pause focused
sfm action invoke sfm:episode/trajectory/replan focused
sfm action invoke sfm:episode/trajectory/route/select focused
sfm action invoke sfm:episode/trajectory/cost/inspect focused
sfm action invoke sfm:text/selection/select/all_matching_hyphen_markers focused
sfm action invoke sfm:text/selection/replace/decimal_sequence focused
sfm action invoke sfm:episode/replay/exact focused
sfm action invoke sfm:episode/replay/semantic_rebase focused
sfm action invoke sfm:episode/replay/causal/inspect focused
```

The selector-only `route/select`, `exact`, and `semantic_rebase` forms open a
constrained choice palette when additional IDs are required. Command completion
also offers exact `id(...)` episode selectors when `focused` would be ambiguous.

## Lab 2: use the History Graph as a gameplay overlay

The overlay has the stable ID `sfm:history`. Its exact selector is
`id(sfm%3Ahistory)`; the colon is percent-encoded because the selector itself is
a single canonical command token.

### Show, hide, and toggle

Enter a world, then run:

```text
sfm action invoke sfm:overlay/visibility/set id(sfm%3Ahistory) visible
```

The graph host appears at the top-right. Its content follows the runtime's
`focused` episode selector. If no machine survives independently of a workspace
panel, it honestly displays the waiting state rather than stale or invented
history. Run the `in_world_history_graph_overlay` puppet later in this guide to
watch the same host with a populated bounded machine.

To hide it without discarding its in-session placement and selected graph row:

```text
sfm action invoke sfm:overlay/visibility/set id(sfm%3Ahistory) hidden
```

To toggle it:

```text
sfm action invoke sfm:overlay/visibility/toggle id(sfm%3Ahistory)
```

### Passive mode

Passive is the default and the mode intended for ordinary play:

```text
sfm action invoke sfm:overlay/input-mode/set id(sfm%3Ahistory) passive
```

The graph remains live, but keyboard, character, mouse, drag, and wheel input
continue to the game. You can walk, look around, and let the world tick while
the episode changes elsewhere.

### Interactive mode and explicit focus

Interactive mode makes the overlay eligible to consume input, but changing the
mode does not steal focus:

```text
sfm action invoke sfm:overlay/input-mode/set id(sfm%3Ahistory) interactive
```

Focus acquisition must happen while no Minecraft screen is open **and** the
Minecraft window is active. Opening the command palette is itself a screen and
therefore intentionally releases overlay focus. Merely switching to a terminal
to type a command also makes Minecraft inactive.

For a manual test, schedule the remote action with a short delay and return focus
to Minecraft before it runs:

```powershell
Start-Sleep -Seconds 3
sfm invoke sfm:overlay/focus/acquire 'id(sfm%3Ahistory)'
```

After pressing Enter in PowerShell, immediately return to the game during the
three-second delay. The mouse cursor is released and the overlay now receives
graph navigation and button input. While it owns focus, movement keys are
consumed by the overlay rather than moving the player. Press `Escape` to release
focus and return to normal gameplay without hiding the graph.

The corresponding explicit action is:

```powershell
sfm invoke sfm:overlay/focus/release 'id(sfm%3Ahistory)'
```

In normal manual use, `Escape` is simpler. Moving focus to PowerShell already
causes the game-window-focus lifecycle guard to release the overlay.

Focus is automatically released if a screen or modal opens, the game window
loses focus, the overlay becomes unavailable, or the world unloads.

### Move and resize through declarative placement

The default placement is a clamped 320 by 190 logical-GUI-pixel box in the
top-right. This moves the same overlay to the lower-left:

```text
sfm action invoke sfm:overlay/placement/set id(sfm%3Ahistory) gui-safe(0,1,0,1,8,-8,96,72,320,190,4096,4096,clamp)
```

This restores the tested top-right placement:

```text
sfm action invoke sfm:overlay/placement/set id(sfm%3Ahistory) gui-safe(1,0,1,0,-8,8,96,72,320,190,4096,4096,clamp)
```

The placement grammar is:

```text
gui-safe(reference-u,reference-v,content-u,content-v,offset-x,offset-y,min-width,min-height,preferred-width,preferred-height,max-width,max-height,clamp|clip)
```

- Anchor values are normalized from `0` to `1`.
- Reference anchors identify a point in the GUI-safe viewport.
- Content anchors identify which point of the overlay is attached there.
- Offsets and sizes use logical GUI coordinates, not framebuffer pixels.
- All six size fields may be `auto`, but they must be either all numeric or all
  `auto`.
- `clamp` keeps the full overlay inside the safe viewport; `clip` permits it to
  extend beyond that viewport.

Set overlap order with:

```text
sfm action invoke sfm:overlay/z-order/set id(sfm%3Ahistory) 321
```

Higher z-order overlays render above lower ones.

### Remote control and multiple game instances

Every overlay action uses the same registered action surface from `sfm.exe`:

```powershell
sfm invoke sfm:overlay/visibility/set 'id(sfm%3Ahistory)' visible
sfm invoke sfm:overlay/placement/set 'id(sfm%3Ahistory)' 'gui-safe(0,1,0,1,8,-8,96,72,320,190,4096,4096,clamp)'
sfm invoke sfm:overlay/z-order/set 'id(sfm%3Ahistory)' 321
```

Without an instance argument, `sfm.exe` targets the most recently focused game.
When more than one game is running, add `--instance-pid <pid>` to make the target
explicit.

## Lab 3: scrub a candidate future without executing it

1. Complete Lab 1 through the first `plan focused`, but do not run the plan.
2. Open a Candidate History panel:

   ```text
   sfm action invoke sfm:panel/open sfm:episode/candidate-history focused
   ```

3. Use `Left` and `Right` to move through frames, `Home` and `End` to jump to
   the ends, or `Space` to play and pause.

The frame controls `|<`, `>`/`||`, and `>|` provide the same behavior by mouse.
The document shown in this panel is a read-only projection. Scrubbing changes
neither the authoritative history head nor the instruction pointer.

To prove old futures survive replanning:

1. Leave the old Candidate History panel open.
2. Run the two-item route, undo, add `- apricots`, and run `replan focused` as in
   Lab 1.
3. Return to the old Candidate History panel. It still shows the pinned two-item
   route.
4. Open another Candidate History panel with the same command. The new panel
   pins the newly selected three-item route.

Materialized frames display exact document bytes. Unavailable, invalidated,
external-barrier, and unknown frames are explicit states; they do not fabricate
document contents. The full status catalogue is currently easiest to inspect
with the candidate-history puppet because some statuses require its bounded
failure fixture.

## Lab 4: attach review comments to candidate history

These commands require a focused Candidate History panel. They target different
levels of the immutable candidate:

```text
sfm action invoke sfm:review/comment/create/candidate/route focused route-note
sfm action invoke sfm:review/comment/create/candidate/step focused step-note
sfm action invoke sfm:review/comment/create/candidate/action focused action-note
sfm action invoke sfm:review/comment/create/candidate/state focused state-note
sfm action invoke sfm:review/comment/create/candidate/glyph focused 3 9 apples-note
```

Glyph bounds are UTF-8 byte offsets in the displayed candidate document and are
end-exclusive. For `1. apples\n2. bananas\n`, `[3,9)` targets `apples`.

Creation feedback and command completion expose the generated comment ID. Use
that ID rather than assuming a particular counter value:

```text
sfm action invoke sfm:review/comment/edit focused <comment-id> revised-note
sfm action invoke sfm:review/comment/navigate focused <comment-id>
sfm action invoke sfm:review/comment/archive focused <comment-id>
```

Route, step, action, state, and glyph comments pin the plan, route, frame, and
content identity that existed when they were created. Replanning does not
silently retarget them.

After exact execution, promotion is explicit:

```text
sfm action invoke sfm:review/comment/promote/exact focused <comment-id> <decision-id>
```

If execution diverged, nothing transfers automatically. An explicit witnessed
migration records the new byte bounds and correspondence evidence:

```text
sfm action invoke sfm:review/comment/migrate/witnessed focused <comment-id> <start-byte> <end-byte> <decision-id> <evidence>
```

Neither promotion nor migration manufactures release approval.

## Lab 5: compare two retained routes

1. Open the temporal chamber and History Graph.
2. Run `plan focused`.
3. Open Candidate History and optionally add a route comment.
4. Run `replan focused` without changing the source. This deliberately retains
   another route from the same start.
5. Open Candidate History again so both routes have visible pinned panels.
6. Open the comparison:

   ```text
   sfm action invoke sfm:panel/open sfm:episode/route-comparison focused
   ```

Useful controls are:

```text
sfm action invoke sfm:episode/route-comparison/mode/set focused lockstep
sfm action invoke sfm:episode/route-comparison/seek focused both 1
sfm action invoke sfm:episode/route-comparison/mode/set focused independent
sfm action invoke sfm:episode/route-comparison/seek focused left 0
sfm action invoke sfm:episode/route-comparison/seek focused right 2
```

Lockstep compares corresponding progress. Independent mode lets each side sit
on a different frame. Both lanes retain their route costs, outcomes, comments,
and immutable identities. Comparison mode, cursors, and dispositions survive
closing and reopening the persisted comparison session.

Record review disposition without deleting or selecting either route:

```text
sfm action invoke sfm:episode/route-comparison/disposition/set focused left preferred
sfm action invoke sfm:episode/route-comparison/disposition/set focused right rejected
```

Disposition is review metadata, not approval and not trajectory selection. Only
this explicit action changes the machine's selected route:

```text
sfm action invoke sfm:episode/route-comparison/trajectory/select focused left
```

## Lab 6: inspect causal history, exact replay, and semantic rebase

This lab demonstrates why the archive keeps physical input, binding resolution,
semantic intent, concrete witness, and state transition as separate layers.

1. Open a fresh temporal chamber and History Graph.
2. Focus the chamber with `Ctrl+1`.
3. Press `Ctrl+Alt+J`. This records the raw key event and its binding while
   semantically selecting all matching hyphen markers.
4. Run:

   ```text
   sfm action invoke sfm:text/selection/replace/decimal_sequence focused
   ```

5. Focus the graph with `Ctrl+2` and inspect the causal chain:

   ```text
   sfm action invoke sfm:episode/replay/causal/inspect focused
   ```

6. Invoke exact replay with only the machine selector:

   ```text
   sfm action invoke sfm:episode/replay/exact focused
   ```

   A constrained choice palette lists valid source boundaries and target
   parents. Choosing the recorded source against its exact source parent
   succeeds without fabricating physical key presses.

7. Focus the chamber, undo twice, and add `- apricots` between the original
   lines as in Lab 1.
8. Invoke exact replay again and choose the changed current head as target. The
   retained concrete witness rejects the changed parent without partial
   mutation.
9. Invoke semantic rebase:

   ```text
   sfm action invoke sfm:episode/replay/semantic_rebase focused
   ```

   Choose the same recorded source intent and the changed current head. Rebase
   re-evaluates “select all hyphen markers” and produces `1.`, `2.`, `3.` while
   retaining the old two-item sibling.

The distinction is intentional:

- **Exact replay** asks whether the old concrete witness still applies to the
  exact parent against which it was recorded.
- **Semantic rebase** asks the recorded intent to resolve again against a new
  parent and records the new witness and outcome.

## Lab 7: run the whole-workspace counterfactual

Open the bounded fixture:

```text
sfm action invoke sfm:panel/open sfm:chamber/workspace-counterfactual
```

It opens an ordinary Explorer beside a History Graph and creates virtual
`A.java` and `B.java`.

1. Press `Ctrl+1` to focus Explorer.
2. Press `Right` to expand its root.
3. Select `A.java` and press `Ctrl+Enter` to open it in an adjacent writable
   Text Editor V3 panel.
4. Press `Ctrl+End`, `Enter`, and type a harmless suffix such as:

   ```java
   // reviewed through a compatible restorable suffix
   ```

5. Record the branch before the selection-dependent operation:

   ```text
   sfm action invoke sfm:episode/workspace-counterfactual/fork/before-selection focused
   ```

6. Focus Explorer and select `B.java` with `Down`.
7. Compare three narratives:

   ```text
   sfm action invoke sfm:episode/workspace-counterfactual/checkout/recorded-a focused
   sfm action invoke sfm:episode/workspace-counterfactual/replay/frozen-a focused
   sfm action invoke sfm:episode/workspace-counterfactual/replay/reevaluate-selected focused
   ```

What each one means:

- recorded checkout moves the history head to the recorded `A.java` state
  without evaluating or executing the suffix action;
- frozen-witness replay still targets `A.java`, even though the live Explorer
  selection is now `B.java`; and
- intent re-evaluation resolves the current selection as `B.java`, applies only
  its compatible in-memory work, and stops at the fixture's explicit external
  barrier.

This lab never writes the ambient checkout. It demonstrates the data and UI
contracts needed for a future safe false-universe source workspace.

For automation, the Explorer click is backed by a selector-explicit action. Use
completion to supply one of the fixture's logical paths:

```text
sfm action invoke sfm:episode/workspace-counterfactual/selection/set focused <logical-path>
```

## Run the automated visual tours

The puppets reproduce the milestone's accepted journeys, capture screenshots,
and write structured evidence. From the 1.19.2 repository root:

```powershell
sfm-propagate-changes.exe puppet run title_screen_temporal_trajectory_machine --branch 1.19.2 --variant 1280x720@auto --wait-for-build-lock
sfm-propagate-changes.exe puppet run title_screen_candidate_history_scrubbing --branch 1.19.2 --variant 1280x720@auto --wait-for-build-lock
sfm-propagate-changes.exe puppet run title_screen_candidate_comment_review --branch 1.19.2 --variant 1280x720@auto --wait-for-build-lock
sfm-propagate-changes.exe puppet run title_screen_route_comparison --branch 1.19.2 --variant 1280x720@auto --wait-for-build-lock
sfm-propagate-changes.exe puppet run title_screen_temporal_replay_rebase --branch 1.19.2 --variant 1280x720@auto --wait-for-build-lock
sfm-propagate-changes.exe puppet run title_screen_workspace_counterfactual --branch 1.19.2 --variant 1280x720@auto --wait-for-build-lock
sfm-propagate-changes.exe puppet run in_world_history_graph_overlay --branch 1.19.2 --variant 1280x720@auto --wait-for-build-lock
```

Each run prints its artifact directory. The in-world overlay run captures and
proves passive movement, interactive input ownership, explicit release,
placement, hide/show retention, external CLI parity, direct scene restoration,
and world-unload cleanup.

The candidate-history and candidate-comment puppets additionally install
bounded test-only status fixtures. Those fixtures are why their tours can show
unavailable, invalidated, external-barrier, and unknown candidates on demand.

## Troubleshooting

### The overlay is visible but empty

The overlay hosts History Graph content; it does not automatically turn
arbitrary gameplay into an episode. The current Temporal Numbering Chamber's
machine is panel-scoped, so closing its workspace unregisters it. Use the
`in_world_history_graph_overlay` puppet to exercise a populated in-world host;
an ordinary persistent background episode producer is future work.

### The overlay does not react to clicks or keys

It is probably passive, or interactive without focus. Set it to `interactive`,
then use the delayed external invocation above so the game is active and no
screen is open when focus is acquired. Merely clicking a passive overlay does
not steal input. Pressing `Escape` releases focus.

### Focus disappears as soon as I use the command palette

That is deliberate. Any full Minecraft screen or modal releases overlay focus
so gameplay cannot remain trapped behind an invisible input owner. Use the
external `sfm invoke ... focus/acquire` command after closing the palette.

### A command using `focused` reports zero or multiple matches

Focus the chamber, Candidate History, comparison, or History Graph panel that
owns the intended episode and try again. Where command completion offers an
exact `id(...)` selector, use it to remove ambiguity.

### `Step` says the plan is stale

The document changed after the plan was produced. This is a safety result, not a
partial failure. Run `sfm:episode/trajectory/replan focused`, inspect the new
route, then step or run it.

### Candidate glyph comments are rejected

Glyph comments require a materialized frame and valid end-exclusive UTF-8 byte
bounds. Route and action comments remain valid for unavailable frames because
they do not pretend unavailable document bytes exist.

### What persists?

Within the running client, hide/show retains overlay placement and the hosted
History Graph's local row selection. Candidate comments and route-comparison
session data use their production stores. The current overlay scene does not yet
promise automatic persistence across a full client restart.

## Deliberate current boundaries

The completed milestone provides the contracts and bounded journeys above. It
does not yet provide:

- automatic capture or undo of arbitrary live-world gameplay;
- automatic human approval from `SUPERVISION_READY`, route disposition, comment
  promotion, or comment migration;
- drag-to-move or mouse-border resizing of gameplay overlays;
- arbitrary user-selected content recipes in the overlay host;
- a public save/load command for complete overlay scene presets;
- ambient-checkout mutation from the Workspace Counterfactual fixture; or
- a guarantee that projected futures can always be computed. Unknown and
  external-barrier states are first-class outcomes.

Those exclusions are what make the current behavior safe to exercise while the
general episode, review, and false-universe systems continue to grow.
