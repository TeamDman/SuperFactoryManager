# Release review exploratory usability

**Plan status:** Complete (bounded E-1–E-4; broader review UX remains open)
**Primary implementation root:** `D:/Repos/Minecraft/SFM/repos2/1.19.2`
**Last updated:** 2026-09-05
**Intent audit:** Passed against the user's phone-away exploration request,
the accepted five-step proposal, and the notification follow-up. Earlier
implementation evidence is referenced from RCOV, not claimed as new testing.

**Successor authorized September 6:** [Release review overnight readiness](release%20review%20overnight%20readiness%20plan.md)
owns ER-F1–F4, early ER-F6 test repair and an ordered stretch ladder. E-1–E-4
remain complete; their observations below are baseline evidence, not tests of
the successor's changes.

## How to update this plan

`[ ]` not started; `[~]` active; `[x]` verified complete; `[!]` blocked with
specific evidence and unblocking condition. Keep one active implementation
item. Put observations, decisions and proof under their task; preserve findings
even when deferred. The parent global comment plan owns the broader roadmap.

## Purpose and foundation

Complete a discoverable ordinary review loop, not only a preselected happy path:
find a real change, compare it, attach a precise concern, inspect the comment
and its target, find remaining work, close the game and resume. The user should
not need memorized opaque commands to discover the next step.

RCOV-A–E already proved exact partial coverage, Java/Rust parity, independent
diff/syntax/comment styling and portable persistence across two JVMs. Those
proofs do not establish general usability or latency. RCS-UX6b1 remains active.
The current live CLI has action invocation and typed Explorer inspection; its
protocol does not provide a general screenshot/input stepping API. The existing
gametest virtual-pointer and render-boundary capture seams provide that lower
layer without OS cursor manipulation. Reuse them in a bounded test-only bridge.

## Guidance and traceability

| ID | Active guidance | Work and proof |
| --- | --- | --- |
| ER-1 | Narrate adaptive self-use to identify the kinds of friction the human finds; do not equate scripted success with a pleasant experience. | E-1 observe/choose/act/inspect sessions, E-3 minimal reproductions. |
| ER-2 | Explore disposably while the user is away on a phone and the desktop may be locked; no OS pointer hijacking. | E-1 isolated review/control files, virtual callbacks and View Image evidence. |
| ER-3 | Improve coherent human/agent ergonomics and accumulate useful evidence, not a new abstract framework. | E-1 bounded bridge; E-3 same public actions and retained observations/regressions. |
| ER-4 | Exercise mouse-first and keyboard-first discovery, cancellation/backtracking, switching during loading, exact comments, queries and restart. | E-1 charters, E-4 rerun and restart. |
| ER-5 | Shorten routine path notifications without losing exact traceability; right-click offers notification-specific open-path-as-text and open-path-in-explorer. | E-2 immutable captured targets, full copy/detail evidence, no focus retargeting. |
| ER-6 | Minecraft Components support clickable phrases and phrase-specific context actions. | E-2 evaluate existing Component/render hit testing; implement bounded explicit path phrase and shared actions, or record an evidence-backed integration limit. Never execute arbitrary embedded commands from path contents. |
| ER-7 | Fix highest-impact obstacles, retain regression tests, provide current installed tooling and a usable walkthrough with limitations. | E-3 ranked fixes; E-4 current runtime/test/install evidence and guide. |
| ER-8 | Preserve real reviews, unrelated dirty changes and frozen dependencies; automated comments are not human approval. | Protected hashes and OPS guide; E-4 final comparison. |

### Intent audit evidence

1. Extraction: ER-1–8 retain the accepted proposal and both notification
   suggestions, including right-click specificity and traceability.
2. Traceability: every row maps to an executable task below. The test-only
   bridge and bounded ranking are agent implementation choices, not a newly
   requested public automation framework.
3. Adversarial: do not hide canonical IDs, substitute direct model mutation
   for UI discovery, claim human subjective comfort from timing, or turn test
   approvals into maintainer evidence. Keep mouse/keyboard and restart distinct.
   Prior long conversation intent remains in the parent ledger; this audit
   covers the current request, not a claim to reconstruct unavailable replies.

## Boundaries and decisions

- Primary validation: Minecraft 1.19.2. No cross-version or release clearance claim.
- Frozen dependency posture from `goal execution and testing readiness guidelines.md`:
  no declaration/lockfile changes, new dependencies or clones; locked cache repair allowed.
- No raw huge-file editor virtualization, general RL/search/agent platform,
  screen registry redesign, or broad icon engine in this goal.
- A new ignored disposable copy must not overwrite the previous RCOV acceptance
  copy. Test action files are bounded, opt-in and local to the selected puppet.
- Observations separate expectation, visible result, timings, interpretation,
  reproduction and disposition. A test may know setup identity, but must not
  preselect the user's entire route or silently inject model state to pass it.
- Core: E-1 through E-4. No speculative stretch claimed. If more friction is
  discovered than fits this bounded review loop, preserve it in the parent
  backlog with explicit priority and evidence rather than broadening this goal.

## Tasks

### [x] E-1 Drive and record an adaptive disposable review session

**Final checkpoint:** Four independent JVM sessions, 165 acknowledged steps
(one deliberately rejected invalid operation), actual rendered captures and
virtual Minecraft inputs. The charter/evidence table below is the completion
record; earlier checkpoints are retained as historical observations.

**Checkpoint, September 5:** Session `session-80a49115-2f68-45c1-8cff-f7b4f3015e2d`
in JVM 11984 exercised 22 adaptive steps from the title screen, recorded under
`runGameTestPreview/sfm-puppet/exploration-control`. No model mutations or OS
pointer injection. Stop request 22 checkpoints before repair, not acceptance.
Steps 5–8 found that a filtered ordinary folder stays visually collapsed while
`sfm.exe explorer describe all --instance-pid 11984 --output-format json` lists
its loaded children. Clearing/retyping the filter (10–13) exposes them. Step 16
opened the disposable review writable through its visible context menu and spent
1,247,222 microseconds in dispatch. Steps 19–21 searched SFM.java; the completed
1,557-row projection caused 4,017,974 microseconds in the next expansion click
and 11,701 ms to the recorded observation. Capture overhead is included only in
the latter, not the dispatch duration. E-3a is temporarily the implementation
focus because this blocks useful exploration; return here after the repair.

**Work:** Add the smallest gametest-only request/observation bridge needed to
step virtual keys, pointer moves/buttons/wheel, palette input and screenshot
capture. Keep file I/O off the render thread, bounded input, one in-flight step,
explicit completion/error, no shell execution, and a stop operation. Observations
include visible controls/choices, focus/viewport and feedback. Requests may be
written by the agent one at a time after inspecting the preceding state.

Explore: mouse-first opening/filtering/comparison/commenting; keyboard-first
context and remaining-query navigation; cancel/backtrack and change focus while
loading. Retain captures at meaningful transitions, not a video of every tick.

**Validation:** `sfm-propagate-changes.exe puppet run sfm:title_screen_exploratory_review --branch 1.19.2 --variant 1920x1080@2 --wait-for-build-lock --log-filter warn --log-file platform/minecraft/build/er-explore-fourth.ndjson`.
Finish within the CLI's 900-second limit. `--keep-open` removes that limit but
has the separate completion-reporting discrepancy recorded under E-2.
Register this exact puppet before using the command. Assert the bridge uses
virtual Minecraft callbacks and actual completed rendered frames. Invalid steps
must report errors rather than kill the game or silently repeat mutations.

**Completion:** A narrated session has evidence for each charter and identifies
concrete friction. Do not relabel an existing scripted puppet as exploration.

### [x] E-2 Make path notifications concise and actionable

**Final proof:** Fourth session steps 23–31 retain the compact filename,
canonical URI hover, clipboard readback verification, six-item context menu
and Escape cancellation. Clipboard access succeeded on this attempt (no GLFW
error); its request note anticipated a denial but is not the observed outcome.
Do not infer a fixed Windows clipboard policy from either session. The silent
denial/mismatched-readback and throwing-writer regressions pass; all 20 focused
toast tests pass. Third-session open-text/open-Explorer checks remain valid.

**Live proof (third session, JVM 17288):** Steps 31–44 show the short
underlined options.txt notification, its six-item context menu, successful
open-as-text and new-Explorer reveal, full canonical URI hover, click-to-copy
and acknowledgement toast. Pointer events are virtual. The notification's
captured path remains separate from the newly focused panel; stale/expired
identity and Unicode/long-path bounds are covered by the 19 passing tests.
Screenshots 34, 41, 43 and 44 are the primary notification evidence.

**Lockscreen correction:** Console inspection showed GLFW clipboard access
denied at step 44 despite the acknowledgement. Therefore actual OS copying is
NOT proven by that screenshot. Verify the setter by readback; a denied or
unverifiable write must retain the source toast and report clipboard unavailable,
not success. Regression added for a silent platform failure and failed writer.
The final repeated gesture is recorded above; denied writes are deterministically
covered by tests, not falsely claimed as the fourth session's observed result.

**Harness reporting discrepancy:** The third JVM's console contains
SFM_GAME_PUPPET_SUCCEEDED and KEEP_FINAL_WORLD_OPEN before normal exit 0, but
the CLI reports no successful completion. Preserve the 51 screenshots/console
under the control session's evidence directory and track the runner's
keep-open/reporting mismatch separately; do not call this a crashed game or a
clean CLI acceptance run.

**Implementation checkpoint:** Standard Minecraft Components now retain
explicit copy-path link styles in a bounded, detached toast payload. Short
leaf labels have full-path hover text; context actions address toast ID plus
path index. Copy, open referenced content as text, and open/reveal in Explorer
reuse existing resolver authority. Plain unlinked text is not path-detected;
embedded RUN_COMMAND/URL events are not executed. Live-pointer validation and
live checks remain pending. All 19 toast tests pass, including stale path
identity, Unicode canonical paths, detached Components, bounded links,
clipboard acknowledgement and canonical grammar. One fixture initially used
an unescaped non-ASCII URI; correcting it to SFM's canonical encoding resolved
that test failure without relaxing the parser.

**Work:** Preserve full captured path and resolver/owner evidence separately from
brief display text. Add contextual open-path-as-text and open-path-in-explorer
choices, full copy/details, and bounded clickable phrase support using Components
where the actual render/input seam supports it. Reuse preferred editor, existing
Explorer actions and toast lifetime leases. Plain-text toast copy, pin/resume,
dismiss and pause-on-hover must continue working. Distinct notifications retain
distinct targets even after focus changes; stale targets fail clearly.

**Validation:** Focused queue/action/layout tests plus real pointer/context
actions. Check long paths, spaces/Unicode, stale toast, replacement, focus
switch, narrow pane and keyboard invocation. Inspect image showing brief text
and contextual exact-path access. Verify no command injection from path text.

**Completion:** The long-path reveal example is readable, and its exact target
can still be copied, opened as text or explored without guessing.

### [x] E-3 Repair the highest-impact observed review-loop obstacles

**Work:** Rank findings by blocking review, misleading evidence, then repeated
interaction cost. Claim the highest-impact reproducible fixes (initial budget:
up to three beyond E-2); add/update named subitems here before coding. Preserve
other observations in the parent backlog. Repair shared UI/action paths, not
exploration-only shortcuts. Add regression proof for each repair.

**Validation:** Focused tests adjacent to changed behavior, then rerun the actual
discoverable route. Latency evidence distinguishes command dispatch, pending
work and visible presentation; no invented percentage performance threshold.

**Completion:** The chosen fixes have before/after observations and tests, and
remaining limitations are named rather than concealed by harness knowledge.

#### [x] E-3a Repair filtered projection cache correctness and hot-path cost

**Final proof:** Fourth session step 5 expands SFM.java in the same 1,557-row
filtered result in 31,898 us (396 ms to captured observation), compared with
1,574,025 us dispatch in the third session. This is a specific before/after
observation, not a general FPS or percentage guarantee. Ordinary filtered folder
expansion also works (second step 5; fourth steps 14–17). Tests: 12 interaction,
13 lazy-loader, 11 projection, 35 SFMPath-filtered and 17 canvas tests pass.
Immutable paths retain canonical URI/hash values; decoration caches are bounded
to 8,192 ranges, 32,768 rows and 1,048,576 retained characters per cache and
invalidate on changed text. Exact-baseline and canvas caches are separate.
The final 30-second JFR profile's leading SFM render samples are font/glyph
rendering, not the earlier repeated URI encoding/range rescans. Sampling and
different interaction sequences do not support a precise global speedup claim.

**Work:** `SFMExplorerPanelModel.state` calls the expensive
`loader.filterProjection` before checking its cheap cache key. It also equates
an absent complete-filter projection with pending work for ordinary resolvers,
permanently retaining old rows. Check cache identity first; retain old filter
results only for a genuine matching active query. Add deterministic counters
and query-change regressions, then repeat observed steps 5–8 and 19–21.

**Validation:** `sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMExplorerPanelInteractionTests`;
real adaptive rerun; existing complete-filter stale/publication tests.
Do not change fuzzy matching semantics or drop context children for speed.

**Red proof (2026-09-05):** New ordinary-resolver query-change regression failed
with the expected stale row assertion; 10 existing interaction tests passed.
Fix and deterministic pre-cache composition counter regression are in progress.

**Partial green proof:** 12 interaction tests and 13 lazy-loader tests pass.
Session `session-df0cc828-1c9d-414d-874d-f435e9bccad2`, JVM 5788, step 5
now expands the filtered folder correctly. Stable search observations are
responsive; step 12 still spends 1,632,030 us expanding SFM.java and 5,563 ms
to observation. Continue investigating cache-miss composition, not just hits.

**Further repair, pending live check:** Build one parent/child adjacency index
per projection instead of scanning every edge for every node. Avoid eager
fallback-entry allocation on known entries. All 11 projection tests pass,
including deterministic one-edge-visit evidence over 2,048 children. Final
latency acceptance remains based on the real review rerun, not this unit proof.

**Third-session profiling:** Step 12 remains 1,574,025 us in dispatch:
the adjacency change is not sufficient. A 90-second JFR profile found repeated
canonical URI encoding and whole-document range/line rescans during diff
decoration rendering. Keep immutable canonical/hash values per SFMPath (same
constructor/accessors/value equality; no global cache), and bounded per-document
highlight-row caches with exact baseline and canvas coordinates separated.
Regression checks cover unchanged-text reuse and changed-text revalidation.
Live timing acceptance is still pending. A JSON-export diagnostic exhausted the
diagnostic PowerShell process's memory; the bounded text event export succeeded.
This was not a game failure or evidence of a new application memory regression.

#### [x] E-3b Restore real SFM.java text-diff navigation

**Live proof:** Third session step 13 opens the real SFM.java text diff with
Java syntax and independent added/removed backgrounds; dispatch 24,695 us.
Step 17 opens its structured counterpart. The 300-line regression and exact
92-mapping/97-region decoder proof above remain green.

**Evidence:** Second session step 13 opens a text diff but receives
`review.surface.invalid-output`; step 15's structured diff works over the
same pair. Reproduce the installed process response and preserve the underlying
validation diagnostic. Fix the producer/consumer mismatch without weakening
exact-byte source-map validation; add a regression on the actual failure class.
Validate the normal before/text-diff/structured-diff route in game.

**Reproduced and fixed, pending live check:** The installed Rust response has
valid source bytes, but Java decoded whole-hunk regions with a 16-source-range
cap (and correspondences with 256). The actual SFM.java response failed at
readRegion. Align these collection limits with the bounded mapping contract;
retain strict byte/hash/identity checks and include the underlying diagnostic.
The new 300-line regression failed before the fix and passes now. Direct Java
decoding validates the real response's 92 exact mappings and 97 regions.

#### [x] E-3c Make a saved comment marker open useful actions

**Final proof:** Fourth session steps 7–10 click the persisted source marker,
offer four useful sections, Tab/Enter the value action, and open the exact note
as a text panel. The generated quoted-ID regression and 120 review tests pass.
The initial reveal is marshalled onto the client thread and checks captured
panel identity. Async errors now have a toast/log instead of disappearing after
the palette closes. Further end-to-end close/reopen epoch-race hardening is
tracked with ER-F2; this acceptance is not proof of every cancellation race.

**Evidence:** Steps 24–35 select the removed SFMBlocks registration in the
structured diff, target before SFM.java [1603,1626), and save a test-only note.
Step 37 clicks its visible gutter marker: the title contains the note, but the
palette has no available actions. Fix the captured comment/host action seam;
provide value/target access with real scope/identity checks and a regression.
The Comments lens itself works: steps 45–49 find the note, expand value,
selector, matches and provenance, read value, then reopen the exact source.

**Reproduced and fixed, pending live check:** Generated choices quote opaque
comment IDs, but the action used Brigadier word() instead of string(). Both
the parser and suggestions now agree on quoted IDs. Canonical generated-choice,
colon, whitespace and Unicode regressions passed after failing on the old
parser. Combined release-review regression run: 120/120 passed. The first
attempt used an unsupported regex filter and found zero tests; it is not
counted as test evidence.

**Third-session follow-through:** Steps 18–20 show all four choices and valid
value invocation, but asynchronous navigation stops at the comment parent.
Steps 22–24 manually expand and read its persisted value. Keep E-3c open:
marshal reveal initiation back to the client thread, retain exact live-panel
ownership, surface asynchronous failure in a toast and log it, and replace the
overflowing full-note menu title with a concise title (the note remains in the
value choice). The command-based openChoices route now has that concise title;
the direct gutter route still uses the full note and remains ER-F3 below.
The complete marker-to-value route was rechecked successfully after rebuild.

**Restart/query proof:** The third JVM reads the second JVM's test-only note on
before SFM.java [1603,1626), with its visible source marker. Steps 45–50 execute
the existing guide's remaining intersect 1.19.2 HEAD query and expose unreviewed
surface rows. The query still needs a much more discoverable mouse entry point;
narrow panes truncate its long unit labels. These are retained UX findings,
not a claim that the existing query presentation is already comfortable.
Third session finishes at step 51; its owned JVM is closed through the normal
window-close request, with no OS pointer input.

**Second session boundary:** The CLI's independent 900-second timeout ended
the JVM after step 56; request 57 has no response and must not be replayed as
an acknowledged action. This was not an application crash. Preserved 56 PNGs
and console.log under that control session's `evidence/` directory.
An intermediate launch used `--keep-open` to remove the unrelated CLI timeout,
then explicitly finished/captured and stopped only the owned test JVM. The final
acceptance omitted that flag to obtain a clean completion/export. An attempted
`--hold` spelling was rejected before launch; `--keep-open` is the actual
PuppetRunArgs flag (verified in source).

**Deferred findings:** Review open ~1.24 s; freeform note save ~2.13 s in
dispatch; recently-used comments are polluted by generated review-unit text;
Ctrl+M does not maximize this workspace layout; single-panel middle-click has
no close menu (tab context and palette close work); lens switching retains a
filter that can hide the new lens's rows; long palette titles escape the box.
Do not confuse slow tool dispatch/approval with game latency: recorded
Comments search steps 42–49 were responsive.

**Deferred observation:** Opening the 30 MB portable review has synchronous
1.25-second dispatch (step 16). Rank after E-3a and comment-path exploration;
do not claim that source inspection alone proves all latency causes.

### [x] E-4 Prove restart, readiness and a human testing path

**Final evidence (September 5):** Fourth JVM 19596 ran current Java sources,
finished all 36 steps, emitted `SFM_GAME_PUPPET_SUCCEEDED` and
`SFM_GAME_PUPPET_COMPLETE failed=0 total=1`, and exited normally. CLI exit 0.
Gallery: `platform/minecraft/build/sfm-toolchain/artifacts/game-test-preview/runs/sfm-title_screen-20260905-203053-825/index.html`.
Its manifest records dirty source fingerprint
`blake3:75ba6dfeec2e16935f720936f6743e2a883c4978` and `1920x1080@2`.
The final explicit locked-toolchain `run compile` also exited 0. No Java source
edits followed that live run; only bookkeeping did. No additional datagen keys
were introduced by this goal. No Rust source changes were made by this goal, so
Rust install/check-all was not required. No test JVM remains running.

Full Java suite: 1,709 found, 1,701 passed, 7 failed, 1 aborted, saved in
`platform/minecraft/build/er-full-tests-20260905.log`. Six failures repeat the
existing JUnit Minecraft Language/en_us resource/bootstrap problem; the new
toast grammar test is the seventh because that earlier failure poisons the
shared Language class. All 20 toast tests pass in their fresh focused process.
The one abort is the installed-symbol-worker integration test without its
required workerExecutable/workerBranch properties. The final additional canvas
cache character-bound test passed in the 17-test focused rerun after the full
suite. Therefore this is NOT an all-green full-suite or release-clearance claim.

Protected maintainer review, manual review and prior RCOV disposable-copy hashes
all match the start. Dependency/lockfile diff remains empty. The installed CLI
SHA256 remains `CAE8E332529A02241F6F70CDD1E3A75616BD81AE7968E9E27C99CB925C826E6C`.
Only the new ignored exploratory review received a test-only note. The dirty
tree was preserved and not committed. Existing generated cache whitespace
warnings remain untouched. The walkthrough is
[Trying the review loop](../release%20review%20exploratory%20usability%20guide.md).

**Work:** Repeat the loop after fixes, stop and restart the JVM over the same
disposable portable review, verify exact comment targets and remaining ranges.
Run focused and full Java checks, current compile/datagen as required, Rust
check-all/install only if Rust inputs change. Recheck protected hashes and
dependency status. Publish a short walkthrough and phone-readable screenshots.

**Validation:** `sfm-propagate-changes.exe run compile --branch 1.19.2`;
`sfm-propagate-changes.exe test run --branch 1.19.2`; the E-1 puppet in a second
JVM; targeted existing review acceptance puppets. Name known full-suite/audit
failures separately from new regressions. If Rust changes, run the relevant
crate's `check-all.ps1` and `install.ps1` after its last edit.

**Completion:** No installer chore; explicit executable freshness, final process
state, exact launch and discovered limitations. Review persistence is proven,
but no automated session grants human release approval.

## Operational readiness and risks

- Start HEAD: `16328629fa60c45a4f525b6f20aaa77715f91077`, dirty preexisting tree preserved.
- Protected `docs/reviews/4.34.0-1.19.2-to-58ed4e431.sfm-review.json` SHA256:
  `998F256C668FF08F16008CADDDC39356111F057327CD9DE597BB6166D3AB0171`.
- Protected `platform/minecraft/run/manual-test.sfm-review.json` SHA256:
  `9ADDCCEC595CAA916EFDB8019270F62D577B336508D2608D84BDA2FE7AADD592`.
- Process preflight: elevated SFM-scoped CIM inspection found no matching game/helpers.
- CLI resolved: `G:/Programming/Caches/CARGO_HOME/bin/sfm-propagate-changes.exe`;
  final hash/freshness is verified under E-4; no user installer step remains.
- All launch/capture writes use ignored game/build artifacts. User is away;
  lockscreen rendering must be measured, not assumed from the previous goal.
- Risks: harness bypass hides UI bugs (record actual route and forbid model
  mutations); snapshots are stale (completed-render boundary); approvals leak
  (protected hashes); async work retargets (captured identities/generation checks);
  arbitrary bridge file authority (test-only bounded directory and operations).
- Source references: `SFMGamePuppetPointer`, `SFMGamePuppetMinecraftRuntime`,
  `SFMCommandPaletteScreen`, `SFMExplorerPanel`, `SFMWorkspaceToastQueue`,
  `SFMToastAction`, `SFMScreenMultiplexer`, and the RCOV acceptance supplement.

## Charter evidence and next work

Session directories under `runGameTestPreview/sfm-puppet/exploration-control/`:
A = `session-80a49115-2f68-45c1-8cff-f7b4f3015e2d`;
B = `session-df0cc828-1c9d-414d-874d-f435e9bccad2`;
C = `session-606d2c46-61dc-4e9d-9cc5-5fc06ab55f17`;
D = `session-ce7f23e9-f4db-4bc3-ad9c-511036a1b3d5`.
B/C retain their screenshot/console copies in `evidence/`; A/D have exported
galleries. Numbered requests/responses are the reproducible chronological trace.

| Charter | Observed evidence |
| --- | --- |
| Mouse-first discovery, filtering and actual diffs | A5–21, B5/12–15, C13/17, D5–6. No direct model mutation. |
| Exact concern and inspection | B24–35 saves before SFM.java [1603,1626); B45–49 inspects value/matches; D7–10 follows its marker to the saved value. |
| Independent restart | The B note survives both C and D JVMs, without touching the real reviews. |
| Keyboard-first query and backtracking | C45–50 executes remaining intersect 1.19.2 HEAD and exposes unreviewed ranges; D11–17 returns to title screen, Ctrl+Shift+E and file navigation. |
| Cancel/loading transitions | D29–31 Escape cancels toast choices; D33–35 opens the review and invokes Escape while its initial projection is still incomplete. The ordinary close-choice palette appears, not a crash; this does not claim all async cancellation interleavings. |
| Traceable notification | C31–43 opens referenced text/Explorer; D24 canonical URI hover, D25 verified copy, D29 specific context choices. |
| Bounded bridge failure | D26 rejects an unsupported operation explicitly; D27 observes the unchanged responsive game. All observations state os_pointer_injection=false. |

The next bounded goal should favour these observed obstacles over new generic
frameworks. They are backlog, NOT silently added to this completed goal:

| ID | Next work and observable acceptance | Existing owner |
| --- | --- | --- |
| ER-F1 | Remove synchronous real-ledger open/save stalls (observed ~1.24 s/~2.13 s). Pending state responds to input immediately; representative cold/warm dispatch and frame evidence, cancellation preserves the file. | RCS-UX6b1 / RUX-23 |
| ER-F2 | Reuse an appropriate preview area for comment value/target navigation instead of multiplying narrow splits. Validate captured workspace, exact panel instance and review epoch again at async completion; closing/reopening cannot resurrect or retarget a preview. | RCS-UX6 remainder / RUX-43 |
| ER-F3 | Bound the direct gutter palette title; show complete note/target via accessible detail, and exclude generated unit boilerplate from recent human templates. | RUX-39 / RUX-43 |
| ER-F4 | A mouse-discoverable remaining-work queue, understandable exact coverage labels, and an explicit way to clear/change retained filters when switching lenses. Preserve the canonical query commands. | RCS-UX6 remainder / RUX-16 |
| ER-F5 | Reconcile keep-open successful-puppet and CLI completion/viewport reporting. Reproduce C without falsely reporting either a crash or clean acceptance. | Puppet control plan |
| ER-F6 | Repair JUnit language/resource isolation and configure installed-worker integration explicitly so a full suite is meaningful without order-dependent failures. | Test/readiness infrastructure |
| ER-F7 | Classify the final run's Java interaction-map `authorized_root_not_mapped` warning at 20:27:31, around opening ordinary options.txt. Determine whether Java analysis was unnecessarily requested for non-Java content or whether authorization metadata is wrong; retain useful diagnostics and do not blindly suppress failures. No UI crash accompanied it. | Symbol-worker diagnostics / structured logging plan |

Confidence: good evidence for the tested single-review path and its persistence;
not yet evidence for broad comfort, every layout, multiple simultaneous reviews,
all race schedules, or cross-version correctness. Automation has produced no
human approval or release attestation.
