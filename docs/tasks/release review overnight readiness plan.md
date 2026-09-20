# Release review overnight readiness

**Plan status:** Complete — core and every ordered stretch verified 2026-09-06
**Primary implementation root:** `D:/Repos/Minecraft/SFM/repos2/1.19.2`
**Last updated:** 2026-09-06
**Intent audit:** Passed against the overnight/stretches authorization, current clipboard/rule discussion and preserved ER/RCOV/IPR ledgers
**Work window:** Approximately ten hours from goal activation; an estimate, never evidence of completion
**Goal activated:** 2026-09-06 06:36:32 UTC (02:36:32 America/Toronto); planning horizon 16:36:32 UTC (12:36:32 local)
**Current focus:** None; complete ladder checkpointed, no new work claimed

**Post-completion user-testing follow-up:**
[Explorer find, selection, compact hierarchy, and review freshness](explorer%20find%20selection%20compact%20hierarchy%20and%20review%20freshness%20plan.md)
records the September 6 exact-filename/input reports and amended design requirements
as EFR-01–49. It is an authorized successor, not a reopened or extended overnight
goal. EF-0 records read-only diagnosis; EF-1 begins the new iteration's contract
work. New working-tree review and matching/highlighting requirements do not
retroactively alter this completed goal's evidence.

## How to update this plan

`[ ]` not started; `[~]` active; `[x]` verified complete; `[!]` blocked with
exact evidence and an unblocking condition. Keep one implementation task active.
Record source decisions, observations and tests beneath the affected task.
The previous E-1–E-4 and RCOV-A–E goals remain complete; this is their successor,
not a retroactive extension. This plan owns tonight's execution order; linked
plans own the broader requirements. No commit or push is authorized here.

## Goal and observable core

Make the current in-game release-review loop practical to use on a real-size,
portable review: open it, browse/filter changes, compare before/after/diffs,
select an exact region, leave a freeform or preset comment, inspect that comment,
find what remains, close Minecraft, and resume without losing review progress.
This work improves the existing surface rather than inventing another review UI.

At the core checkpoint the user can:

- Open a review and submit either comment form while the UI remains responsive
  with honest pending/saved/failed state; failed saves keep the draft recoverable.
- Follow saved-comment value/target links in an appropriate existing preview
  area without accumulating accidental narrow splits or stale reopened panels.
- Read long comments through a bounded menu and a complete detail/value view;
  recent comment templates no longer consist of generated review-unit boilerplate.
- Discover remaining work by mouse, see understandable exact-coverage labels,
  change lenses without unknowingly hiding their contents with an old filter,
  and retain the existing canonical query/keyboard routes.
- Restart the game over the same portable file and see the same exact comments
  and remaining-work state. Automated fixture approvals are not human approval.

## Guidance and traceability

| ID | Origin and requirement | Coverage |
| --- | --- | --- |
| RNO-1 | User: set tonight's goal, including stretch goals, considering the whole direction. | Required core + ordered continuation below; current review usability precedes icon customization. |
| RNO-2 | Preserved user north-star: resumable code-surface comments and queryable unreviewed area, not an isolated annotation demo. | ER-F1–F4 and RNO-A exercise portable persistence, exact targets and remaining queries. RCOV semantics stay unchanged. |
| RNO-3 | User: agents should experience ordinary use, preserve evidence and find friction without waiting for the human to report it. | RNO-A adaptive mouse/keyboard charters; ER-S3 bounded follow-through, same public actions as humans. |
| RNO-4 | User/OPS: uninterrupted useful work, explicit installed-tool freshness, scoped recovery, frozen dependencies and no OS pointer hijacking. | RNO-0, ER-F6, RNO-A and operational contract; no blanket process or filesystem authority. |
| RNO-5 | Current user: flat contextual rules, user themes and details/prompt clipboard handoff with registry-informed instructions but no ItemStack catalogue. | ER-S4 includes all IPR-01–35 and IPR-T0–T5 including T3a; authorized stretch, not quietly mandatory core or already implemented. |
| RNO-6 | Agent proposal grounded in observations: repair open/save stalls, preview reuse/epochs, comment legibility and remaining-work discoverability. | ER-F1–F4 retain the existing backlog IDs and recorded ~1.24 s/~2.13 s stalls. |
| RNO-7 | Agent proposal: fix the known resource/bootstrap test problem early so tonight's full suite is meaningful. | ER-F6: named failures reproduced and removed without disabling assertions; installed worker explicitly exercised. |
| RNO-8 | OPS: estimates must not cause early stopping, scope inflation, or abandonment of claimed work. | Core checkpoint first, then one ordered stretch at a time; finish/verify a claimed slice before another. No unplanned filler. |

## Intent audit evidence

- **Final closure audit, 2026-09-06:** Rechecked the required review loop before
  the newest icon/prompt requests, mapped every RNO row to the thirteen completed
  core/stretch contracts, and checked the IPR-01–35 proof map against the
  preserved clipboard/predicate/theme constraints. Explicitly retained manual
  execution, no ItemStack catalogue, no automatic model calls, source/theme
  integrity, installed-tool ownership, independent restart and no OS injection.
  Seven changed plan/guide files have no missing local links or trailing
  whitespace; all thirteen overnight and seven IPR task headings are complete.
  The authoritative IPR ledger has 35 unique rows with no gaps. Scoped
  `git diff --check` passes; the whole tree still reports only the two generated
  datagen-cache CRLF warnings already observed earlier. See
  `build/rno-readiness-20260906/final-document-checks.json` under Minecraft.
  Older timestamped checkpoints below retain their then-pending statements;
  the final task/operational checkpoints supersede them, not their evidence.
- **Extraction:** Reread the current overnight authorization and available
  icon/theming/clipboard messages. Consulted the full exploratory plan and OPS
  guidance, exact-coverage foundation and the 35-atom IPR ledger. RNO-1–8 retain
  core review purpose, ordered stretches, practical self-use, portable state,
  manual clipboard handoff and bounded operational authority.
- **Traceability:** Each RNO row maps to named work below. Inspected actual
  synchronous review open/freeform-save versus async preset-save paths,
  existing query/work navigation actions, installed-worker test properties,
  CLI test/puppet arguments and prior runtime evidence. Gates distinguish new
  implementation decisions from verified APIs; stretch eligibility is explicit.
- **Adversarial:** Checked that the most recent icon idea does not displace
  review completion, a preset-save test does not stand in for freeform save,
  pending is not success, a same-process reopen is not restart, a marker is not
  exact coverage, and a model-generated rule is not automatically executed.
  Frozen dependencies, immutable human reviews, no OS mouse movement and final
  installer responsibility apply equally to all stretches. No 5% timing gate.
- **Source limitation:** Older assistant proposals are not all available as
  original replies. ER/RCOV/IPR and parent ledgers are authoritative durable
  context. This audit does not claim to reread missing replies or re-prove old
  runtime evidence. New runtime acceptance is pending.
- **Document checks:** Eight unique RNO guidance IDs and thirteen individually
  addressable core/stretch work contracts validated; every contract has Work,
  Validation and Completion sections. Local links in this and the IPR plan
  resolve, and the touched plan files pass `git diff --check`. Stale no-goal
  wording in linked current-status sections was reconciled; historical completed
  goals remain unchanged. These are planning checks, not implementation proof.

## Verified foundation and source entry points

- [Exploratory usability](release%20review%20exploratory%20usability%20plan.md)
  records four JVMs/165 steps, exact persisted note navigation, real SFM.java
  diffs, virtual-pointer captures, and filtered expansion dispatch 1.57 s ->
  32 ms in one measured case. That is not a broad comfort/performance guarantee.
- [Exact coverage](release%20review%20exact%20coverage%20acceptance%20plan.md)
  owns Java/Rust parity, pinned before/after interval coverage and portable state.
- `SFMReleaseReviewRuntime.open` loads synchronously under its monitor;
  `mutateAsync` already persists outside that monitor. In
  `SFMReleaseReviewCommentChoiceAction`, preset `apply` uses `applyAsync`, but
  `saveDraft` for the freeform editor calls synchronous `apply`. Do not assume
  the async method alone fixes all visible routes.
- Review sources: `client/review/release_review/` (runtime/store/drafts,
  Explorer/surface runtime, decorations/coverage), `SFMReleaseReviewAction`,
  `SFMReleaseReviewCommentChoiceAction`, `SFMReviewLensSetAction`,
  `SFMReleaseReviewCommentDetailsAction`, `SFMTextEditorPanel`,
  `SFMScreenMultiplexer` and `SFMWorkspacePanelIntentDispatcher` under
  `platform/minecraft/src/main/java/ca/teamdman/sfm/`.
- Existing review actions include `review/session/query/activate`,
  `review/session/work/next`, `previous`, `defer` and `resume`. Reuse them and
  existing portable cursor/query state; do not introduce a second progress store.
- `SFMSymbolServerInstalledIntegrationTests` currently skips without
  `sfm.symbol.workerExecutable` and `sfm.symbol.workerBranch`; these are JVM
  properties, not invented CLI flags. `RunTestArgs` supplies substring `--filter`,
  not regex. Inspect the test launch environment before choosing property wiring.
- Test runner sources are `platform/cli/sfm-propagate-changes/src/cli/test.rs`,
  `cli/run/run_test_cli.rs`, `jar_build/run_test_command.rs`,
  `jar_build/run_test_options.rs` and `jar_build/junit_event_runner.java`.
  Puppet options in `cli/puppet.rs` include `--variant` and `--keep-open`.
- `ExploreReviewInteractivelyPuppetAction`, `TitleScreenExploratoryReviewGamePuppet`,
  `SFMGamePuppetPointer` and completed-render captures provide adaptive virtual
  input. Reuse their bounded test-only bridge; do not invent a public agent API.

## Gates and invariants

| Gate | Decision before relevant edits | Required proof |
| --- | --- | --- |
| RNO-G1 | Define staged open/save ownership, cancellation boundary, failure retention and atomic-commit point. Reuse the existing lease/hash/epoch authority. A cancel before commit leaves bytes unchanged; a request after commit must report the committed outcome, not pretend it undid the write. | Inject blocked I/O, failure, duplicate submit, cancellation, close/reopen and stale completion; render-state reads must not wait on a worker-held monitor. |
| RNO-G2 | Select an appropriate preview destination and identity policy through the shared intent layer; existing explicit split commands must still split. | Repeated identical target, alternating value/target, changed workspace/epoch, closed and recreated same-ID panel, and dirty editor exclusion. |
| RNO-G3 | Choose discoverable queue/filter affordances over existing query grammar, preserving raw hashtag vs effective approval distinction. | Mouse and canonical command produce the same exact remaining witnesses; lens filters are visible, clearable and not silently destructive. |
| RNO-G4 | Before claiming ER-S4, close IPR-G1–G4 with its typed grammar/storage/specificity fixtures. Prefer additive versioned theme data and preserve prior mappings. | All IPR tests/round trips and a complete authoring journey including T3a; no fabricated grammar or new dependency. |

Other bounded implementation choices are delegated to agent judgment. An
irreversible data migration, dependency mutation or semantic expansion not
covered here requires direction. No need to wait for the user over local UI
wording, test fixture layout or a reversible implementation detail.

## Required core work

### [x] RNO-0 Establish current baseline and disposable scope

**Completion evidence:** The ignored
`platform/minecraft/build/rno-readiness-20260906/baseline.json` records HEAD
`16328629fa60c45a4f525b6f20aaa77715f91077`, branch `1.19.2`, 200 preexisting
changed/untracked files with hashes, 12 dependency declarations/locks, five
protected review copies and both existing run/preview theme files. Maintainer
and manual review hashes still match the September 5 evidence. CLI SHA256
`CAE8E332529A02241F6F70CDD1E3A75616BD81AE7968E9E27C99CB925C826E6C` and sfm.exe
SHA256 `CEACCF5CFEAA4F9E0A7C554EED219D0CF376DC1A69D2BCC7B191A02FEAD6C569`
resolve under `G:/Programming/Caches/CARGO_HOME/bin/`.
Elevated scoped CIM inspection found no matching game/helper/CLI process;
sandbox CIM access was denied, not evidence of an absent process. The first
test attempt failed at the cache lock with os_error=5 before running tests;
normal host-cache access succeeded without lock deletion or process killing.
New review target is `runGameTestPreview/sfm-puppet/rno-20260906.sfm-review.json`;
the build evidence path is verified ignored. No user review/theme was modified.

**Work:** Record dirty-source manifest, actual CLI paths/hashes, scoped process
ownership and protected-review/theme/dependency hashes. Select a new ignored
disposable review and separate per-session control/artifact directories. Preserve
the manual review, maintainer ledger and prior RCOV/exploratory copies. Read the
OPS guide before any process recovery; identify sandbox lock denial separately.

**Validation:** `git status --short`, `git rev-parse HEAD`, `Get-FileHash` on
protected inputs/tools, scoped process inspection and verified ignored output
paths. Record exact baseline in operational readiness below.

**Completion:** Current tools and disposable targets are identified; tests cannot
accidentally write to a human review or confuse previous dirty edits with new work.

### [x] ER-F6 Make baseline tests and installed-worker checks trustworthy

**Completion evidence:** Full fresh JVM: 1,712 found, 1,711 passed, zero
failures/skips, one optional worker abort separately executed and passed.
Fresh isolated affected families: OverlayActionGrammar 5/5,
ClientActionDispatcherCompiler 7/7, SpatialCoverageRunAction 5/5,
WorkspaceToast 20/20, TerminalPropertiesAction 5/5; zero failures/aborts.
`full-green*` and `green-<family>*` retain exact evidence. All 19 protected
reviews/themes/dependency declarations and locks still match baseline hashes.
Scoped changed-file `git diff --check` passes; the wider dirty tree retains
preexisting generated language-cache whitespace warnings, not changed here.

**Reproduction:** Current-source baseline full run (September 6) found 1,710
tests: 1,702 passed, seven failed, one installed-worker test aborted. Preserved
`baseline-full-host.ndjson` and `baseline-full-console.log` under the RNO build
evidence directory. The first failure loads Language with a null
`assets/minecraft/lang/en_us.json` stream. Direct archive inspection confirms
dev-compile.jar contains Minecraft classes but not that resource; the existing
locked client-extra.jar contains the resource and no Language class, but is
missing from testRuntimeClasspath.txt. Repair the JUnit runtime resource path,
not production translation or assertions. No green acceptance yet.

**Repair checkpoint:** Added `SFMMinecraftLanguageResourceTests`; both resource
and real-translation tests fail against the old installed runner (saved as
`language-red.ndjson`/`language-red-console.log`). The runtime classpath now
uses the existing locked resource-only client-extra.jar while preserving
project/test resource precedence and dependency order/deduplication. Focused
Rust regression `junit_runtime_includes_vanilla_resources_and_preserves_project_precedence`
passes with `cargo test --locked --offline --lib <test name>`. Required Rust
check-all/install and green Java/full/installed-worker validation remain pending.
Configure worker properties process-locally via JAVA_TOOL_OPTIONS for the
acceptance command, restoring the prior environment afterwards; do not add
an undocumented CLI flag or make fixture-only tests depend on ambient branch.

**Intermediate verification:** `check-all.ps1` completed with exit 0 (format,
Clippy, build, unit and integration suites). `install.ps1` completed; installed
`G:/Programming/Caches/CARGO_HOME/bin/sfm-propagate-changes.exe` SHA256 is
`BAF3C332C0C3247709481F28D27210327F0FFF541561ABC0BBD2E2125835358E`;
`help list --short` succeeds. Both language regression tests now pass. The
explicit installed-worker test passed (one found/executed, zero aborted),
including DiskItem definition reuse, the large OutputStatement interaction map
and graceful worker shutdown. Evidence: `language-green*` and
`installed-worker*` in the RNO directory. Full/affected-family checks pending.
The full suite uses its normal environment: JAVA_TOOL_OPTIONS is limited to the
focused worker test because child-process protocol tests inherit that variable
and Java emits its startup notice on stderr. Its optional integration abort is
accounted for by the separate executed test, never counted as a pass.

**Work:** Reproduce the recorded JUnit Language/en_us bootstrap/order issue.
Fix the narrow test-resource/bootstrap seam without masking assertions or
changing production localization semantics. Configure the existing installed
symbol-worker integration through explicit test-launch properties. If Rust
runner changes are necessary, they are in scope with frozen dependencies and
mandatory check-all/install. Do not substitute an untracked Minecraft jar.

**Validation:** Run the full suite, affected tests alone and their failing
combination in fresh processes; reproduce the original failure class first.
Run `test run --filter SFMSymbolServerInstalledIntegrationTests --branch 1.19.2`
with the verified worker properties supplied by the chosen launcher seam and
record that it executed rather than aborted. Preserve evidence of any genuinely
external failure, never label a skipped test a pass.

**Completion:** The named resource/order failures are resolved and integration
readiness is explicit; full-suite results become a useful regression baseline.

### [x] ER-F1 Keep review opening and both comment-save routes responsive

**Completion checkpoint:** 136/136 review tests pass after the final canonical
comment-create async wiring (`review-final-f1-1.ndjson`). Fresh JVM PID 9452,
session `a5ff5f0f-6bbb-4122-8189-31d3ffadf899`, completed 28 adaptive virtual-input
steps and exited successfully in 6m42s. It reopened both durable notes from JVM B,
displayed their source markers and the full freeform text in recent choices,
then cancelled a held preset through the notification's own context menu.
Dispatch took 10,707 microseconds for save and 1,479 for cancellation; completed
frames advanced throughout. On worker release the comment count stayed 2,919 and
the authoritative SHA256 stayed
`9DF323ECF20B771511CC606B4E39DABAB53409E72FFA66EA2B60D1A897A4947A`.
The retained draft reopened and retried successfully (2,879 microsecond dispatch;
3.01 seconds worker time), adding exactly `human:release-review:3` at the same
SFM.java `[0,25)` target. No cancellation was mislabeled saved. The logged error
in the otherwise successful run is the deliberately induced CancellationException.
All protected review/theme/dependency hashes match. Evidence: `live-er-f1-c*` and
gallery `sfm-title_screen-20260906-043400-188` (28 figures). B's freeform/newer-edit
proof plus C's restart/cancellation/retry proof complete this task; later RNO-A
still owns the integrated final query/persistence journey. The verbose cancellation
toast is a presentation follow-up under ER-F3, not lost diagnostic evidence.

**Second adaptive JVM checkpoint:** Session `1c631197-2f23-4e77-aaa4-9d129b52c5f3`
proved freeform Done dispatch at 8,089 microseconds while persistence was held,
continued typing/frame progress, acknowledgement of only the submitted version,
retention of newer edits, then an update of the same comment ID on the second Done.
The exact SFM.java `[0,25)` target and full text survive in the disposable file as
`human:release-review:1`. Preset dispatch took 3,793 microseconds while held and
displayed a pinned Saving notification with the canonical operation-cancel action.
The worker's safety timeout released the hold and saved `human:release-review:2`;
this is **not** evidence of a cancellation. The session itself hit the 900-second
runner limit before the cancellation gesture completed and is not a passing puppet.
`live-er-f1-b-console.log`, NDJSON and 39 screenshots preserve the evidence.
All protected hashes still match. A short fresh JVM must finish cancellation/retry
and verify these notes after restart before claiming ER-F1 complete.

Cold read-only/writable parsing took approximately 2.34/1.91 seconds on the worker;
freeform save/update took 3.32/2.97 seconds on the worker, excluding deliberate
queue holds. These are observed samples, not universal performance bounds.
The pending-notification and same-file Explorer-rebind repairs pass 136 review
tests and 21 toast tests. The direct canonical comment-create action now also
uses the async commit path; its final test run is pending.

**Adaptive first JVM (07:41–07:56 UTC; incomplete runner session):** PID 20576,
1920x1080@2, virtual input only, 31 observed steps retained in
`live-er-f1-a-screenshots` and `live-er-f1-a-console.log`. Cold 30 MB read-only
open dispatched in 12,820 µs; worker load/publication took 2,036,157 µs. SFM.java
filter input dispatched in 1,741 µs and its expanded children in 25,110 µs.
Holding the persistence executor before writable reopen left the old epoch,
comments and read-only view unchanged while frames advanced and the palette
accepted cancellation. Exact operation 2 changed to CANCELLATION_REQUESTED,
then disappeared on release without altering the ledger hash. Retrying the
same frozen draft successfully reopened writable and offered comment choices.

The exercise found two F1 defects despite unit-green plumbing: feedback was
sent into the already-dismissed contextual palette, and same-file writable
reopen left old Explorer lens roots invalid. Repairs now keep operation-owned
pending notifications pinned with canonical right-click cancellation, and
explicitly rebind captured same-file Explorer lenses after exact epoch/host
checks. Validation is pending. The runner reached its known 900-second timeout
before freeform save; this is **not a passing puppet or save/persistence proof**.
The new disposable ledger remains byte-for-byte unchanged. Pending request 32
was never dispatched. No OS cursor movement or user review writes occurred.

Other observed backlog: Explorer Ctrl+A did not select filter text (typing then
appended), filesystem filter completeness differs from review completeness,
and generated review-unit templates dominate recent choices (already ER-F3).
The first Other editor was V1, reflecting the existing preference; its severe
new right split is ER-F2 evidence, not grounds to change the user's preference.

**UI wiring checkpoint (2026-09-06 07:39 UTC, live acceptance pending):**
Open/reopen actions and post-create opening now use the staged runtime. V1 Done
and v3 Save/Done opt into asynchronous persistence without offloading existing
Minecraft callbacks. The shared save session waits for client-thread durable
acknowledgement, advances only submitted text, refuses duplicate concurrent
writes, and revokes detached-editor callbacks. Later saves update the same
comment, preserving its exact target/provenance and freeform whitespace.
Preset drafts share a pending operation and retain cancelled/failed text for
retry. `sfm:review/session/operation/cancel <positive-operation-id>` exposes the
same before-commit boundary with current-ID suggestions. Existing palette
lifetime guards were not reused as long-lived host authority: continuations
capture the exact workspace/panel instance.

Full fresh JVM: **1,732 found, 1,731 passed, zero failures/skips, one optional
installed-worker abort** (separately executed under ER-F6). Evidence:
`full-async-ui-3.ndjson` and `full-async-ui-3-console.log`. Includes save-session,
freeform/preset cancellation/retry/update, panel acknowledgement, exact host
continuation and cancellation grammar tests. Earlier review-only checkpoints
passed 127/127 and 131/131. No live responsiveness claim yet.

The adaptive puppet now has explicit test-only `review_worker_pause` and
`review_worker_resume` controls: they hold the existing persistence executor,
never mutate the review, automatically release after 180 seconds, and release
on Finish. Observations include barrier/pending phase, review epoch/generation,
comment count and completed frames; runtime logs separate queue and worker time.
New ignored 30,307,364-byte copy `rno-20260906.sfm-review.json` starts with
SHA256 `998F256C668FF08F16008CADDDC39356111F057327CD9DE597BB6166D3AB0171`.
Datagen for save-status translations and live cold/warm/paused-worker checks
are next. The store now computes the optimistic hash from the same byte read
it parses, avoiding mixed content/hash witnesses across an external replacement.

**Core checkpoint (not UI completion):** Added staged `openAsync`, explicit
operation IDs/phase cancellation, worker-owned lease cleanup, and the pre-replace
store commit callback. Async mutation now schedules outside the runtime monitor;
closing pre-commit cancels the old operation without letting its completion clear
a newer request. The `SFMReleaseReview` family passes 127/127, including seven new
queued/blocked-open, same-writer reload, failed/cancelled save, stale-reopen and
store commit-boundary regressions (`review-async-core-1*`). UI actions and editor
Save/Done still need wiring; no claim of live responsiveness yet.

**G1 decision (implemented; live acceptance pending):** Preserve the previous
committed runtime snapshot while an owned worker stages a new open. Keep one
pending operation with a stable request identity; reject conflicting mutations
and duplicate saves. Reuse the current writer lease when reloading the same
writable path; stage a different lease before replacing the old one. Failed or
cancelled loads retain the old view. Publication checks the captured lease,
document, generation and open epoch, then advances identity atomically; lease
cleanup must occur outside the snapshot monitor.

Cancellation is an explicit operation request, not CompletableFuture.cancel
masquerading as an undone write. Validation/serialization/temporary-file work
is cancellable; immediately before the authority replacement, an atomic phase
transition grants commit authority. Cancellation that wins that transition
leaves authoritative bytes unchanged. Once commit authority is granted, report
committing/the actual durable outcome rather than false cancellation. Recovery
mirror failure remains a saved result with a diagnostic. Freeform editors use
an opt-in asynchronous save-handler seam; existing synchronous host callbacks
remain on their required caller thread. Pending never closes or marks the editor
saved; only the captured submitted text becomes the baseline after durable
success, and intervening edits prevent automatic close. Detached/replaced panel
callbacks cannot close a new panel. Cancellation/failure retains the draft for
retry. Tests will inject queued/blocked preparation and commit-boundary races.

**Work:** Close G1. Stage parsing/validation/serialization/I/O off the client
thread and outside monitors needed for UI snapshots. Wire review-file writable
open, read-only-to-writable reopen, preset comments and freeform Save/Done into
the same pending/commit/failure model. Preserve the old usable view while loading
and the draft until a durable save succeeds; avoid duplicate submissions.

**Validation:** Extend `SFMReleaseReviewCreateRuntimeTests`,
`SFMReleaseReviewCommentDraftServiceTests`, `SFMReleaseReviewCommentChoiceActionTests`
and portable-store tests with blocked-I/O/cancellation/epoch fixtures. Run the
`SFMReleaseReview` test family and adaptive real-size cold/warm runs for BOTH
comment routes. Separate dispatch, worker completion, frame and screenshot
latency; prove input/frame progress while a test worker is deliberately paused.

**Completion:** No synchronous ledger load/save stalls in these user paths;
pending is immediately visible and input-responsive. Failures/cancellation
cannot lose a note, corrupt a ledger, claim an unsaved comment was saved, or
apply a stale result to a different review. Actual latency evidence is recorded.

### [x] ER-F2 Reuse previews without stale navigation or split proliferation

**First-load repair verified (2026-09-06 09:35 UTC):** Root initialization had
called `loader.refresh` without registering ownership with the session, racing
the tracked reveal request. It now joins a tracked load or reuses current
materialized children, and rejects delayed initialization for removed roots or
closed sessions. Five reveal tests cover both orderings; all 283 Explorer tests
pass (`reveal-initialization-f2-1`, `explorer-initialization-f2-1`). Fresh JVM F,
PID 43360, completed the first Comments-tab/value reveal without retry (6–8),
then exited normally; gallery `sfm-title_screen-20260906-053436-941` and
`live-reveal-f*`. Its first command included an erroneous extra `writable` word
and was correctly rejected without a write; steps 3–4 corrected the test input.
The long E note survived this independent JVM unchanged. No review mutation was
performed in F. RNO-A still owns the final integrated proof.

**Reopened (2026-09-06 09:26 UTC):** Adaptive JVM E at 1920x1080@3 exposed an
initial-load race: the first `comment/details/open` into a newly created Comments
tab reported `Explorer reveal load was stale` (steps 4–6). Retrying after that
tab loaded succeeded (7–8). Existing exact-owner guards prevented a wrong target,
but first-use navigation must succeed without a manual retry. Repair the shared
load/reveal ordering with a deterministic test and repeat a fresh-tab live open;
do not weaken stale-owner, root, epoch, or generation rejection.

**Completion evidence (2026-09-06 08:55 UTC):** Fresh full suite: 1,739 found,
1,738 passed, zero failures/skips, one optional installed-worker abort already
explicitly passed under ER-F6 (`full-navigation-f2-1*`). Adaptive JVM D, PID
25324, completed 34 virtual-input observations and exited 0. Gallery
`sfm-title_screen-20260906-045512-680`, `live-er-f2-d.ndjson` and preserved console
record Comments as a tab in the existing left Explorer area; value, exact SFM.java
target and Changes After all share the right preview area. Revisiting Value
reused its exact tab (1,793 microsecond dispatch). The preferred V1 freeform
draft opened as another right-area tab and Cancel returned to the source with
no ledger write. No third split accumulated.

A held comment-navigation worker entered its test barrier, then the exact
destination Comments panel was closed via the public panel action. Releasing
the worker logged `SFM_REVIEW_COMMENT_DETAILS_STALE`; four panels remained,
without a resurrected destination or provenance preview. Unit guards additionally
cover review/epoch/root changes and recreated same-ID panels. All 19 protected
dependency/review/theme hashes match. Reported launch errors are preexisting
third-party Mixin/AE2/Industrial Foregoing resource messages, not SFM navigation
failures. No OS pointer injection occurred. RNO-A still owns final integrated
acceptance after the remaining core changes.

**G2 decision:** Read-only typed previews share an owner derived from the exact
normalized review path, while presentation identities continue to include pinned
source/content/target identity. Different review lenses may therefore reuse the
same preview area without treating ordinary or writable editors as disposable.
Explicit adjacent mode remains a new ordinary split. A new Comments Explorer
becomes a tab in an existing same-review Explorer area, not a split beside the
source preview; a freeform draft becomes a tab beside its originating source.
Every asynchronous comment-navigation stage must retain both initiating and
destination panel instances, active workspace, review path/open epoch and exact
destination roots. A focus change alone is not revocation; close/recreate, lens
replacement or review replacement is. Tests and live acceptance are pending.

**Implementation checkpoint:** Review family passes 138/138 and shared preview
placement passes 5/5 (including cross-lens reuse, repeated identity, explicit
adjacent and unrelated/writable exclusion). The guard fixture covers hidden
attached tabs, changed roots/path/generation/epoch, closed origin, and replacement
destination with the same numeric ID. The exploratory bridge adds a bounded
`review_navigation_pause/resume` fault-injection control around only the comment
navigation executor; it cannot mutate documents, releases after 180 seconds,
restores normal scheduling on resume/Finish, and reports when the barrier is entered.
Live reuse and delayed-close acceptance are next; no ER-F2 completion claim yet.

**Work:** Close G2. Route comment value/target navigation through the shared
preview intent policy, revalidating workspace, exact live panel and review epoch
on client-thread completion. Reuse an appropriate preview; do not overwrite a
dirty editor or defeat an explicit request to open a new split.

**Validation:** Extend surface/Explorer runtime, comment-details/action and
workspace-intent tests. Live-repeat the gutter -> value -> target route, then
close/reopen/change review while a delayed result is pending. Assert panel/split
counts and exact target identity, not merely that some document opened.

**Completion:** Repeated review navigation stays in the intended area, while
old async callbacks cannot resurrect a closed panel or retarget a new one.

### [x] ER-F3 Make long comments legible and recent templates meaningful

**Completion evidence (2026-09-06 09:26 UTC):** Full suite found 1,747 tests:
1,746 passed, zero failures/skips, one optional installed-worker abort already
explicitly passed under ER-F6 (`full-legibility-f3-1*`). Adaptive JVM E, PID
14640, completed 31 observations and exited 0. Gallery
`sfm-title_screen-20260906-052507-191`, `live-er-f3-e.ndjson` and its preserved
console show 1920x1080@3 narrow split panes. A long two-line café/雪 note saved as
`human:release-review:4` in the disposable ledger; its complete text and exact
SFM.java [0,25) witness were inspected from the persisted JSON. The value view
retains both lines (step 24); recent choices contain the two genuine freeform
notes and no generated review-unit boilerplate (29). The long-row hover remains
inside the viewport and exposes the localized right-click full-value/copy hint
(30). Save dispatch was 8,352 microseconds and displayed pending feedback before
durable completion. Initial Comments navigation revealed the separate ER-F2
race above; the overall core is not claimed complete. No OS pointer injection.

**Implementation checkpoint:** Recent templates now select `provenance.kind ==
human`, deduplicate newest-first and preserve exact stored whitespace; generated
records remain in the ledger and ordinary comment views. Presentation summaries
collapse whitespace and truncate on Unicode code-point boundaries, while command
arguments retain the complete original text. Palette headings/rows obey pixel
width, full-row truncated-candidate hover obeys a screen-height row budget, and
the overflow notice points to existing complete detail/copy actions. The single
gutter-comment menu uses a concise identity rather than the entire note as title.

Preset failure/cancellation now produces a concise draft-retained notification
with canonical retry and `sfm:toast/details/copy <toast-id>` actions. Diagnostic
payloads are tied to the exact expiring notification, not its replacement lane;
the complete payload remains in the log. The clipboard capture is bounded at
32,768 code points with an explicit truncation notice for exceptional payloads.
No parsing of arbitrary message text into executable commands is introduced.

Review tests pass 141/141 (`review-legibility-f3-1.ndjson`); the new tooltip
translation was generated by `run data` and is present in generated en_us.
Full palette/toast/Unicode and live narrow-view verification remain pending.

**Work:** Bound the direct gutter menu's heading and row text to available space;
retain complete note/target through accessible detail/value actions and tooltip
hit regions. Filter generated review-unit boilerplate out of suggested recent
human templates by provenance, not a destructive text rewrite. Keep stored
comments, arbitrary user text and canonical IDs intact.

**Validation:** Comment-choice, decoration, palette viewport and draft-service
tests cover long/multiline/Unicode notes, genuine repeated templates and generated
unit records. Live inspect narrow panes with mouse and keyboard selection.

**Completion:** Menus stay within bounds, the complete comment is discoverable,
and template suggestions help write a note rather than repeat generated IDs.

### [x] ER-F4 Expose remaining work and lens/filter state by mouse

**G3 implementation decision:** Add explicit Remaining-work choices to the
existing review-lens control (all configured lanes and each available lane),
plus an exact-coverage Status choice. They use the existing query kernel, not a
second definition of approval. Activating a writable queue stages its query and
current unit into the existing portable `resume_state` off-thread before showing
the Query lens in the same Explorer. A read-only view must not imply persistence.
The bound Explorer, roots, review path and open epoch are revalidated before UI
publication. Query activation failure/cancellation keeps the prior queue.

Lens changes preserve the local filter deliberately, display that fact, and
offer a canonical exact-Explorer Clear filter action; the filter bar remains the
mouse/keyboard entry point for editing it. Compact lens labels retain detailed
query/filter information in tooltips. Coverage labels distinguish before/after
required bytes and exact gaps from raw approval tags and blocking notes. Tests
compare the produced lane query with `remaining intersect 1.19.2 HEAD` and retain
the existing one-byte/both-side/stale-target fail-closed semantics.

**Work:** Close G3. Add discoverable action-backed controls/context choices to
the existing review Explorer for remaining work and exact coverage inspection.
Use concise labels with full identities/details available. Make retained filter
state explicit on lens change and offer an obvious clear/change operation.
Keep existing canonical queries and exact before/after semantics.

**Validation:** Review Explorer/action/filter tests compare mouse-produced
queries with `remaining intersect 1.19.2 HEAD`; one-byte approval, blocking note,
both sides and stale targets retain RCOV truth. Live navigate Changes -> Comments
-> Remaining with an active `.java` filter and no memorized opaque command.

**Completion:** A user can discover what remains and open its exact source
surface, and can explain an empty filtered lens from visible state. Closing and
reopening the portable review preserves the authoritative comments/query state.

**Implementation checkpoint (2026-09-06 09:54 UTC):** The existing lens menu now
offers `sfm:review/work/remaining all|<configured lane>`, Exact coverage and
blockers, and an exact-Explorer Clear retained filter action. Writable query
activation uses the asynchronous store path, preserving the old snapshot on
cancellation/failure and persisting the query/current-unit/deferred state before
same-Explorer publication; read-only views are explicitly temporary. Query rows
use concise names while retaining full-path search and exact source witnesses.
Coverage labels distinguish total and before/after approved/required bytes.
222/222 focused tests pass, followed by 1,754/1,755 full-suite tests passing with
only the explicitly opt-in installed-worker integration abort (already exercised
successfully with its settings under ER-F6). The new menu regression also checks
the actual lane/status/clear-filter choices. `full-core-f4-1` had one test-source
compile error (wrong shortTitle argument type), corrected before the passing
`full-core-f4-2` run. Live G/H persistence and interaction proof remains required.

**Live G (2026-09-06 09:54–09:59 UTC):** JVM 34096, adaptive session
`2402e7c9-daea-4fd3-8fd2-1b2bf6125ed0`, 32 steps, CLI exit 0 in 6m28s,
gallery `sfm-title_screen-20260906-055929-361`. Mouse-only Changes → Comments →
Remaining 1.19.2 retained `.java` visibly. Queue dispatch was 7,259 µs while
the save ran 3.57 s off-thread; the normalized expression, first stable unit and
resume generation 2 are in the portable JSON, not just local UI state. Same
Explorer panel was retained; exact clear-filter action worked, then SFM.java
filter, uncovered after [199,345) and exact-coverage status were opened. All
2,921 comments including four prior test notes remain unchanged. Snapshot and
hash are in `g-portable-checkpoint.json`; H must prove a distinct JVM restores it.

This adaptive run caught that equal-named hunks were hard to distinguish before
expanding. Their row headings now put labelled before/after UTF-8 ranges before
semantic/limitation detail; exact-gap regression asserts this. Final rerun/H is
pending. G steps 17–19 used obsolete button bounds after the palette re-centred
when suggestions shrank; no action was submitted, and step 20 corrected from
the observed widget bounds. This is not a product crash or persistence failure.
Retain palette position stability as a later usability observation, not hidden
acceptance or an unbounded additional repair. Existing third-party startup
Mixin/AE2/Industrial Foregoing diagnostics remain separate.

**Completion (2026-09-06 10:07 UTC):** `full-core-f4-3` passes 1,754 tests,
0 failed, 1 opt-in abort after the final unit-range label change. Independent
JVM H (5472; session `1a9f282d-c6a4-4465-8efa-3f1b9646fa14`, 15 steps, CLI exit 0
in 4m05s) restores G's exact normalized query/current-unit/resume generation 2
and all 2,921 comments. Runtime observation now includes those cheap immutable
resume fields rather than inferring them solely from a disk read. H opens Query
without reactivating/saving it, shows the new range labels, then opens the full
long Unicode note and its exact SFM.java [0,25) match. The portable SHA-256 stays
`7AC370EADB50A66CDAC59A9969EDF53CE0E987208ED88A161DACA571458FB9FF` through H.
Evidence: `h-restart-proof.json`, `live-resume-h*`, gallery
`sfm-title_screen-20260906-060719-754` (figures 8, 11, 14). View Image inspection
performed. G/H have distinct PIDs and normal shutdowns; no new approvals,
dependency changes, OS input injection or real-review writes.

### [x] RNO-A Prove the core, checkpoint, and hand off ready-to-use tooling

**Work:** Run an adaptive disposable review journey using virtual inputs, first
mouse-first and then keyboard-first. Include real SFM.java, long notes, preset
and freeform saves, cancel/backtrack, delayed navigation, comments/value/target
and remaining work. Restart into a second JVM with the same portable file and
verify exact saved text/targets/query state. Show meaningful screenshots with
View Image and retain observations/reproducers rather than only a success flag.

**Validation:** From the repository root, using the current installed CLI:

```powershell
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMReleaseReview --log-filter info --log-file platform/minecraft/build/rno-review-tests.ndjson
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMCommandPalette --log-filter info --log-file platform/minecraft/build/rno-palette-tests.ndjson
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMWorkspace --log-filter info --log-file platform/minecraft/build/rno-workspace-tests.ndjson
sfm-propagate-changes.exe test run --branch 1.19.2 --log-filter info --log-file platform/minecraft/build/rno-full-tests.ndjson
sfm-propagate-changes.exe run compile --branch 1.19.2
sfm-propagate-changes.exe puppet run sfm:title_screen_exploratory_review --branch 1.19.2 --variant 1920x1080@2 --wait-for-build-lock --log-filter info --log-file platform/minecraft/build/rno-exploration.ndjson
```

Use distinct logs/session artifacts per rerun instead of overwriting evidence.
Until ER-F5 is complete, use bounded sessions within the known 900-second CLI
limit; do not treat `--keep-open` reporting as clean CLI acceptance. This bridge
requires adaptive requests; issuing the launch command alone is not the test.
Run `run data --branch 1.19.2` when localization/generated inputs change, then
the conclusive compile/runtime checkpoint. If Rust inputs change, run that
crate's `check-all.ps1` and `install.ps1` after its last edit and verify PATH/hash.

**Completion:** Focused/full/runtime results and two-JVM persistence evidence
are recorded, protected hashes match, no new regressions are unexplained, and
the guide states the launch command, tested scope, limitations, tool freshness
and final process state. No installer work is silently delegated to the user.
This is a tested local source/artifact checkpoint, not an automatic git commit.
Repeat the final affected checks after any stretch modifies these inputs.

**Core acceptance checkpoint (2026-09-06 10:10 UTC):** All required core tasks
are verified. Final focused checks: ReleaseReview 143/143, CommandPalette 14/14,
Workspace 107/107; full production-change suite 1,754 passing, 0 failures and
1 explicitly configured-separately worker integration abort. Final compile
after the observation-only queue fields passes all four Java source sets. Logs
are `core-final-*` and `full-core-f4-3*` beneath the ignored readiness directory.
ER-F6 records the green configured worker integration and required Rust checks/
installer. G/H prove exact portable query/comment/target restart, C proves
cancel/retry, D proves preview reuse and stale closed-panel rejection, E/F prove
long Unicode comments and the repaired first-load/reveal race. A/B timeout and
the intentionally recovered adaptive mis-clicks are not erased or counted as
clean end-to-end passes. View Image screenshots were inspected and shared.

User install required: **no** at this checkpoint. PATH resolves to
`G:/Programming/Caches/CARGO_HOME/bin/sfm-propagate-changes.exe`, SHA-256
`BAF3C332C0C3247709481F28D27210327F0FFF541561ABC0BBD2E2125835358E`;
the unchanged `sfm.exe` hash is
`CEACCF5CFEAA4F9E0A7C554EED219D0CF376DC1A69D2BCC7B191A02FEAD6C569`.
Language datagen ran after F3 keys; no later generated-resource input changed.
All 19 protected lock/dependency/review/theme hashes still match baseline. Both
G/H games exited normally and the scoped process check found no preview JVM.
No commit/push, new dependency, release approval or OS pointer control occurred.
The updated exploratory/exact-coverage guides give writable versus read-only
commands, mouse/keyboard navigation, pending/cancel semantics, retained filters,
the test-only `rno-20260906.sfm-review.json` path and current artifact links.
Stretches must re-establish affected readiness before final handoff.

## Ordered stretch ladder

Only claim these after RNO-A's core checkpoint. At the ten-hour planning horizon
or a user return, stop claiming new slices; finish and validate the current
bounded slice, or report it honestly incomplete. If no capacity constraint is
reached, continue in order. Unstarted stretches remain future work. Do not mark
the goal complete with unfinished claimed work or merely because ten hours ran
out. Exhausting the ladder is a stop condition, not a reason to invent more work.

### [x] ER-S1 Add next/previous controls and resumable queue navigation

**Completion checkpoint 2026-09-06 10:54 UTC:** `stretch-s1-full-2` passes
1,765 tests, zero failures/skips, one previously configured-separately opt-in
worker abort (1,766 found). All source sets compiled after final fixes.
Independent JVM J (22976; 19 virtual-input steps, exit 0 in 5m10s; gallery
`sfm-title_screen-20260906-065347-017`) restores I's exact saved unit, active
query, resume generation 6 and one deferred unit. Show current opens the exact
SFMNavigationRequestWitness source without saving. Resume restores the deferred
TextEditContext region and removes only that deferred identity. Previous at the
first unit leaves the full file SHA-256 unchanged; Right/Space Next returns to
the already-open TextEditContext tab (same panel ID 2, same preview area), makes
it visible, and returns focus to Explorer. `.java` remains in the filter;
pending/outcome notifications are singular. Full notes and exact targets match
G; total comments remain 2,921. `j-queue-restart-proof.json` retains IDs/hashes;
View Image inspected figures 7, 11, 13, 18. Final portable cursor generation 9
points at TextEditContext; no approval/format changes. I/J both stopped normally.
Installed CLI hash remains BAF3C332...5358E; no Rust/generated-resource changes
since core installation/datagen. User install required: no. The guide now covers
the five controls, read-only limits, preserved filters and durable versus blue
selection. The known non-Java warning is explicitly owned by next ER-F7.

**Claimed 2026-09-06 10:10 UTC after the core checkpoint.** Reuse the canonical
`review/session/work/{select,next,previous,defer,resume}` actions, moving their
query/save work off the render thread. Visible queue controls must distinguish
the persisted work cursor from incidental Explorer highlight/filtering, and
reveal/open the selected exact unit only in the captured current review view.
An explicit Show current work choice resumes without advancing or resaving.
First/last/empty queues, deferred/missing units and stale closed/reopened targets
must fail or report boundaries honestly, never silently wrap or retarget.
No new durable format, review meaning or approval mechanism is introduced.

**Implementation checkpoint 10:32 UTC (not completion):** Added a pure
`SFMReleaseReviewWorkQueue` transition model and captured-snapshot async queue
mutations. Next/Previous skip deferred units and never wrap. Deferring the last
unit leaves no current cursor; Resume takes the first deferred unit only if it
still belongs to the active query. Selecting an explicitly deferred unit resumes
it. Missing/current-query-stale/empty/boundary cases retain prior file bytes.
The previous wraparound test is intentionally superseded by this explicit
non-wrapping contract. `review/session/work/show` is registered separately from
Resume and does no save. A five-control toolbar on Query uses the canonical
actions, reserves real viewport space, exposes keyboard focus and full tooltips,
and distinguishes `[Saved cursor]`/`[Deferred]` from Explorer highlight. Exact
query/epoch/panel guards protect background reveal and preview placement.
The first focused invocation incorrectly used a glob (`*SFMReleaseReview*`)
instead of a substring and found zero tests; this is not passing evidence.
The second caught a test type-name typo, now corrected. Current focused,
full-suite, mouse/keyboard and two-JVM persistence proof remain pending.

**Adaptive I checkpoint (10:45 UTC, not completion):** JVM 41864 completed 21
virtual-input steps and exited normally (10m11s, gallery
`sfm-title_screen-20260906-064433-738`). Mouse Next and Defer advanced to two
different exact Java source units; Ctrl+F, Tab/Enter Previous, Right/Space Next
proved keyboard dispatch and non-wrapping deferred skipping. Portable resume
generation is 6, with one deferred TextEditContext unit and current
SFMNavigationRequestWitness unit; `i-queue-checkpoint.json` records full IDs.
All 2,921 comments and four exact human-provenance test notes remain unchanged.
The first read-only Show current left the entire file hash unchanged. Focused
tests 149/149 and full suite 1,762 passed/0 failed/1 opt-in abort were green
before I, but I exposed three gaps which invalidate stretch completion:
duplicate toolbar toasts, reused PREVIEW tabs not becoming visible, and reveal's
default filter-clear policy overriding the queue's retained-filter contract.
The repairs use one operation-owned feedback lane, activate the existing tab
then restore Explorer focus, and opt this queue into explicit RETAIN reveal
policy (ordinary reveal retains its existing CLEAR policy). New regressions
cover inactive-tab activation and filter edits while loading. Final full tests
and independent JVM J restoration remain pending. Cargo.lock also reproduced
the known non-Java `authorized_root_not_mapped` warning at 10:37:18 UTC; ER-F7
remains next, not silently folded into this task.

**Work:** Reuse existing next/previous/defer/resume/query actions in visible,
keyboard-accessible controls on the remaining-work view. Persist the selected
stable work unit/query using the portable file, not AppData-only progress.
Keep empty/end-of-queue state clear and avoid synchronous saves from new controls.

**Validation:** Existing review runtime/store/action tests plus first/last/empty,
filter change, missing/stale unit, deferred unit and two-JVM resume fixtures.
Live move through two real targets, close Minecraft, reopen and resume that queue.

**Completion:** The user advances through remaining work without repeatedly
reconstructing the query or losing their place. Requires the core; no new review
format or approval semantics. Checkpoint and rerun affected RNO-A proof.

### [x] ER-F7 Explain or repair the non-Java interaction-map warning

**Completion evidence 11:13 UTC:** Live K (PID 2832, independent current-source
JVM, 27 virtual steps) opened options.txt and the same pinned Cargo.lock after
preview that warned in I/J, without a Java-map request/failure for either.
SFM.java still published 1,649 regions/3,783 outlinks and the acquired Forge
FMLJavaModLoadingContext.java published 189 regions/449 outlinks through
`dependency-source-2`. Real per-map unresolved diagnostics remain visible;
the focused failure test proves WARN evidence is not globally suppressed.
K completed normally (`failed=0 total=1`, CLI exit 0, 8m43s), gallery
`sfm-title_screen-20260906-071140-247`; figures 21 and 26 were inspected with
View Image. This was a language/authorization check, not a layout-comfort test:
the intentionally opened independent Explorers accumulated narrow areas.
`live-language-k.ndjson` and `live-language-k-java.log` retain the result.
The saved queue was moved once to Cargo.lock (resume generation 10); all 2,921
comments and four full human-provenance test notes/targets are unchanged.
Disposable SHA256 `48EBC5DD13FCCDC8A0A6B3D8F985C0C014CEE48BD517851D3C88C099FA5A9B58`.
All 19 protected hashes match. Scoped diff whitespace checks pass. Java-only
edits required no reinstall; installed CLI SHA256 remains `BAF3C332C0C3247709481F28D27210327F0FFF541561ABC0BBD2E2125835358E`.

**Claimed 10:54 UTC after ER-S1 checkpoint.** I/J reproducibly emitted
`authorized_root_not_mapped` when previewing Cargo.lock (I 10:37:18 UTC;
J 10:52:19 UTC). `SFMJavaInteractionMapSession.structurallyUnavailable` checks
document readiness/path/authority shape but not Java language eligibility;
trace the editor's language/provenance seam before selecting the repair. Keep
real Java/index failures observable and add safe captured document metadata.

**Decision/implementation (10:57 UTC, validation pending):** The editor already
preserves explicit `SFMTextDocumentLanguage` through pinned review materialization
and `semanticAnalysisSnapshot`; that metadata is the eligibility authority, not
the generated file suffix or title. The Java-map session now cancels/rejects old
work normally and skips non-Java language IDs before requesting handshake/root
adaptation. Real Java failures retain WARN severity and gain language, scheme,
SHA-256 document-address identity, current-content identity and known root/source
set fields, without putting source text or private absolute paths into the new
failure evidence. Four regressions cover ordinary non-Java names, misleading
materialization names/declared Java, cancellation/late publication, and genuine
Java failures. This changes no resolver permission or language/index capability.

**Test checkpoint 11:02 UTC:** Focused interaction-map family 18/18 and full
suite 1,769 passed/0 failed/1 opt-in abort (1,770 found). The first focused run
had three newly added test failures because the pinned-snapshot factory expects
raw SHA-256 while `SFMDefinitionRequest.sha256` includes its prefix; this also
caught a duplicate prefix in new document-address diagnostic identity. Both were
corrected, with the failed run retained as `stretch-f7-focused-1*`, green rerun
`stretch-f7-focused-2*`, and `stretch-f7-full-1*`. Runtime K was pending at this
intermediate checkpoint; its completed proof is recorded above.

**Work:** Reproduce the recorded `authorized_root_not_mapped` event around
options.txt, identify actual language/source/authorization context, and repair
unnecessary Java requests or incorrect metadata at the owning seam. Retain
useful warnings for genuine Java/index problems; do not suppress them globally.

**Validation:** Symbol/semantic/language focused tests plus ordinary text and
Java/dependency-source live opens. Logs must identify the captured document and
reason without changing permissions or fabricating a symbol mapping.

**Completion:** The event has an evidence-backed classification and any defect
has a regression; expected non-Java behavior is quiet or explicitly not-applicable.
No broad symbol-index redesign. Requires the preceding checkpoint.

### [x] ER-F5 Reconcile successful keep-open puppet reporting

**Claimed 11:13 UTC after ER-F7 checkpoint.** Inspect the current CLI/Java
completion markers, timeout ownership and viewport artifact projection before
changing behavior. K has stopped normally; no live game needs to be terminated.

**Decision/implementation 11:20 UTC, validation pending:** Java's Forever route
sets `completed` without calling `finishRun`, so no terminal completion marker
exists; Rust independently requires restored viewport even when the user asked
to retain it. Report all-execution assertion completion once before finite or
indefinite holds, separately publish the retained viewport and exact hold
seconds, and keep ordinary restoration after countdown. Rust accepts a retained
viewport only when it matches the CLI-requested hold and has full nonzero
geometry; single-puppet success, missing/duplicate/failed/zero-total completion
and an unrequested hold do not pass. Normal process exit is still required.
Capture-level dimensions remain authoritative, separate from restored launch
dimensions. The existing Forever timeout exemption is unchanged; live validation
will impose an external bounded deadline and use ownership-proven normal window
close (not OS pointer input). Test finite hold, Forever plus normal close, and
an actual selected puppet's rejected viewport. Source wiring assertions are
explicitly not runtime proof; Rust parser tests and three live launches supply it.

**Intermediate tests:** 19 focused Rust puppet/preview tests and both Java
capture/lifecycle source assertions passed. Full Java: 1,771 found, 1,770 passed,
zero failures/skips, one installed-worker opt-in abort to rerun explicitly after
the final CLI install. `stretch-f5-java-full*` retains evidence. Initial Rust
check-all stopped with one subprocess `rg` access-denied failure inside the
sandbox (681 passed/1 failed/3 ignored); the host-access rerun passes all 682
enabled unit tests and is still running integration scenarios. A subsequent
additive `viewportRole: initial-launch-window` field labels the existing top-level
launch request without changing capture-specific viewport metadata or old
manifest readability; this is included in the host rerun, not the initial run.

**Tool checkpoint 11:29 UTC:** Host `check-all.ps1` completed with exit 0:
682 enabled unit tests, 10 Java-analysis scenario tests, 9 Git-review tests and
33 materialization tests pass (three preexisting ignored Rust tests remain
explicit). `install.ps1` completed after the last Rust edit, revision 16328629f,
installed PATH SHA256 `AB53A7900E6E7CBE39B4FE0A9CFE8F52CC99E2F3D64D57B83B366BD2E2D2FFA0`;
help smoke succeeds. The explicit installed-worker test passed (1/1, no abort),
retained as `stretch-f5-installed-worker*`. All 19 protected hashes match and
scoped whitespace checks pass. Live L finite hold is launching; runtime closure
and final artifact proof remain pending.

**Live L finite hold:** `puppet run sfm:title_screen_size_display --branch 1.19.2
--variant 1920x1080@4 --keep-open 5s --wait-for-build-lock` exited 0 in 1m54s.
The JVM (14992) logged exactly one `COMPLETE failed=0 total=1` at 07:31:17 local,
then retained 1920x1080 / GUI 4 / logical 480x270, completed its five-second hold,
and restored the 1280x720 startup viewport at 07:31:22 before normal exit.
`live-hold-countdown-l.ndjson` and `live-hold-countdown-l-java.log` preserve it.
Gallery `sfm-title_screen-20260906-073124-641` has four captures with the measured
1920x1080 metadata and `viewportRole: initial-launch-window` on the separate
startup request. View Image inspection of the nested layout confirms three
panels and the caption outside the captured viewport. This is lifecycle/geometry
proof, not review UX matrix proof. M indefinite hold is the next live case.

**Live M indefinite hold:** The same puppet at `1920x1080@3 --keep-open`
reported `COMPLETE failed=0 total=1` once, then explicit `keep_open_seconds=-1`
and `KEEP_FINAL_WORLD_OPEN`. Owned JVM 17472 (parent 26144, exact branch preview
argfile and pinned JBR path verified with CIM) received `CloseMainWindow()` only
after those markers. This normal window-close request returned true and the
JVM exited 0 without timeout/cancellation; the CLI also exited 0 in 2m03s,
accepting the intentionally retained viewport without demanding restoration.
No OS mouse was moved. Logs are `live-hold-forever-m*`; gallery
`sfm-title_screen-20260906-073748-727` has all four GUI-3 captures. N now tests
a real selected puppet with impossible effective GUI scale 20, not a missing
puppet/discovery typo, to ensure requested keep-open cannot turn failure into
success.

**Completed checkpoint 11:41 UTC:** N rejected explicit GUI scale 20 clamped
to 4 on the actual selected size-display puppet, logged `FAILED` and exactly
one `COMPLETE failed=1 total=1`, restored and stopped normally. JVM exit 0 did
not mask the failed assertions: CLI exit 1, no successful gallery published.
`live-hold-failure-n*` preserves the negative evidence. L/M/N are stopped;
all 19 protected hashes still match. The full Rust/Java and installed-worker
checkpoint above is after the final source edits; installed AB53A790 remains
current. No localization keys changed, so no additional datagen is needed.
User install required: no. ER-F5 is complete; the next claimed slice is ER-S2.

**Work:** Trace the existing success/keep-open/normal-shutdown and viewport
metadata path through Java and Rust. Fix completion/export bookkeeping; preserve
the ability to hold the final scene without claiming success before assertions.

**Validation:** Runner/parser tests and one bounded keep-open successful run,
one failing puppet, and clean user-requested shutdown. Confirm CLI status and
gallery viewport metadata match Java completion. Rust checks/install mandatory
if touched; do not require OS pointer/focus control to prove the result.

**Completion:** A passing held puppet is not falsely reported failed, failures
remain failures, and final artifacts reflect the requested effective viewport.
No general puppet/window-manager redesign. Requires the preceding checkpoint.

### [x] ER-S2 Expand review evidence across GUI scales and split sizes

**Claimed 11:41 UTC after ER-F5 checkpoint.** The adaptive review puppet currently
uses COMMON_RESPONSIVE, whose exact-size allowlist excludes 2000x2000. Add a
bounded REVIEW_READINESS profile retaining the common declared sizes and adding
only the square exact witness; do not change other puppets' support claims.
Use independent adaptive virtual-input runs at 1920x1080@1, @2, @3, @4, @auto,
and 2000x2000@4. Exercise the portable disposable review, comment target/value
access and remaining queue through actual GUI controls, including a narrow split.
No model mutation shortcuts or existing acceptance-file replacement. Retain
per-variant observations, capture metadata and View Image inspection. Pure
profile contract checks and fresh Java compile/tests accompany the wiring change.

**Profile checkpoint:** `SFMGamePuppetReviewViewportContractTests` compiles the
three pure gametest profile/selection/variant sources with the configured JDK
and checks every exact matrix witness, unchanged four-size declared expansion,
rejection of square by COMMON_RESPONSIVE, rejection of an unlisted square, and
the adaptive puppet's profile wiring. 1/1 passed with no skips/aborts. The live
scale-1 run uses a new ignored `rno-matrix-1.sfm-review.json` copy, leaving the
core portable checkpoint unchanged. Evidence prefix: `matrix-gui-1`.

**GUI 1 live checkpoint:** JVM 42452, adaptive session
`8329db75-56dd-4a88-8b1b-bc088da9c5e8`, 32 virtual steps, normal CLI exit 0 in
7m07s; gallery `sfm-title_screen-20260906-075037-489`. Actual window/framebuffer
1920x1080, requested/effective GUI 1, logical 1920x1080, verified on all captures.
Opened the 30MB disposable review, filtered SFM.java, expanded its exact row,
opened After, dragged an actual [0,23) source range and used contextual Other
to save `#needs-change Matrix GUI 1 test-only note: café 雪; exact selected bytes.`
The new comment is human-provenance test record 5, not an approval, with exact
text and target verified in the file; count 2,922. The visible lens menu saved
Remaining work (1.19.2), retained SFM.java filtering, and Show current opened
the saved Cargo.lock range in the existing right-hand tab area without advancing
resume generation 11. Target/queue tooltips render across the split, not clipped.
View Image inspections include source, context picker, freeform draft, queue,
cross-split tooltip and final source. Matrix-copy SHA256
`38743375FD34D959CACCEFAE1ADB5E8F26C16E8439B791EF70A4DFDBE9F1DB03`;
original RNO checkpoint remains `48EBC5DD...`. Logs: `matrix-gui-1*`.
GUI 2 now launches from a separate `rno-matrix-2.sfm-review.json` copy.

**GUI 2 live checkpoint:** JVM 29292, adaptive session
`00aa30b9-8df1-4a2d-9e0f-d59ab7a27cfb`, 31 virtual steps, normal CLI exit 0 in
6m28s; gallery `sfm-title_screen-20260906-075750-582`. All 31 captures have
actual/framebuffer 1920x1080, GUI 2 and logical 960x540. Repeated the exact
[0,23) source selection, contextual Other, Unicode freeform note (this time
saved by Shift+Enter), Remaining work through the lens menu and Show current.
The long recent-note tooltip stays inside the viewport, clearly points to
right-click full details, and does not get clipped to the underlying split.
Five queue buttons fit the half-width Explorer. The new GUI-2 test concern is
exact in the file, count 2,922, resume generation 11, no approval created.
Matrix-copy SHA256 `D8B8896AA4467DCB5AD5E49A81819B4E6AEC1A95337F7254FD9F982A05982160`.
View Image: filtered hierarchy, source, long tooltip, draft, saved queue and
queued source. `matrix-gui-2*` preserves the logs. GUI 3 now launches with its
own copy; scales 3, 4, Auto, square and narrow-split evidence remain pending.

**GUI 3 + narrow split live checkpoint:** JVM 44412, session
`c5821c4b-1138-43ee-a82c-157b7e2c28c4`, 37 virtual steps, normal CLI exit 0 in
9m59s; gallery `sfm-title_screen-20260906-080830-042`. All captures validate
1920x1080 window/framebuffer, requested/effective GUI 3, logical 640x360.
Saved the exact GUI-3 Unicode note on SFM.java [0,23), count 2,922; Remaining
work and Show current retain resume generation 11 and the SFM.java filter.
Virtual border drag changed the split from halves to Explorer width 159 and
source width 479 (2-pixel divider). All five queue controls remain reachable;
the complete Show current tooltip crosses the split without clipping, and
the lens menu uses the full viewport. Source remains in its existing tab stack.
View Image inspections: exact target, draft, queue, narrow source, tooltip and
lens menu. Copy SHA256 `35ED9C738810B6BD00E18A7673280F9740E0272DF72C596C8D792D3308962BD4`.
Evidence: `matrix-gui-3*`; GUI 4 is starting independently.

**GUI 4 red-layout checkpoint:** JVM 11620, session
`e8a8780c-9f4c-4875-83c9-ff8323e6a5a8`, 32 steps, normal CLI exit 0 in 5m31s;
gallery `sfm-title_screen-20260906-081438-359`, logs `matrix-gui-4*`.
The exact [0,23) note and saved queue route worked at logical 480x270, but
figure 18 shows the long recent-note tooltip overflowing the viewport's left
edge. The puppet lifecycle pass is not a GUI acceptance pass. The prewrapped
tooltip is bounded to 360 pixels, but the vanilla tooltip placement can flip
it left of a mouse near the left half, producing a negative x. ER-S2 now owns
this bounded viewport-placement repair: retain full detail actions and bounded
text, test placement over all matrix widths/pointer edges, and repeat the
GUI-4 hover in a fresh JVM. Auto and square remain pending. No broader tooltip
framework change; no live shared runtime rebuild while this JVM is running.

**Tooltip repair checkpoint (12:17 UTC, validation pending):** Inspected the
existing locked Minecraft Screen source: prewrapped tooltip x is mouse+12 and
right overflow subtracts width+28, with no left clamp. Candidate tooltips now
measure their already-wrapped lines and constrain the vanilla anchor, retaining
four-pixel borders, row budget and first-line gap. Two focused tests cover the
exact 480x270 / mouse (122,149) failure and pointer edges across six logical
viewports, including 320x180. This does not truncate the complete detail/value
payload or alter selection/command execution. The GUI-4 red-run copy retains
its exact note, SHA256 `B951CD4851D0C1875CC53E7ECE9CF7D79711961069E17752AA993B70A7031859`.
Focused palette checks and a fresh GUI-4 replay are next.

**Test checkpoint:** All 16 focused palette tests pass; fresh full Java suite
has 1,774 found, 1,773 passed, no failures/skips and the one separately exercised
installed-worker opt-in abort. `stretch-s2-tooltip-focused*` and
`stretch-s2-full*` preserve results after the source edit. The new ignored
`verify-matrix.ps1` checks every capture's window/framebuffer/logical/scale/crop
metadata plus every one of the 2,921 prior comments unchanged, the new exact
test note and saved query/cursor. It passes on GUI 1/2/3 and the GUI-4 red run;
data/geometry success does not override the latter's observed tooltip defect.
Fresh GUI-4 repaired runtime is now launching with `rno-matrix-4-green`.

**GUI 4 green checkpoint (12:30 UTC):** Fresh JVM 11704, session
`fec8ee2f-ed37-4775-b6bc-ef4726f8e831`, 33 steps, normal CLI exit 0 in 5m00s;
gallery `sfm-title_screen-20260906-082602-406`, logs `matrix-gui-4-green*`.
Figure 18 repeats the same long Unicode tooltip hover and now shows every
bounded line plus the full-details instruction inside the viewport. Figure 32
shows queued Cargo content and immediate three-row virtual wheel movement
(2,743 microseconds dispatch). All 33 actual 1920x1080 / GUI 4 / 480x270 captures
pass the matrix validator. The independently copied review preserves all 2,921
prior comments, adds only the exact [0,23) test note, and persists resume 11 with
the expected query/Cargo cursor. Its SHA256 is
`B951CD4851D0C1875CC53E7ECE9CF7D79711961069E17752AA993B70A7031859`, identical to
the red run because the saved semantic state is identical; the layout evidence
is deliberately distinct. Auto and square runs are next.

**Auto live checkpoint:** JVM 11700, session
`3a539d35-5bd7-4ccd-8da7-7227c11f441a`, 32 steps, normal CLI exit 0 in 4m57s;
gallery `sfm-title_screen-20260906-083540-285`, logs `matrix-gui-auto*`.
All captures report requested auto / effective 4 / logical 480x270 with actual
1920x1080 window and framebuffer. Inspected source, exact-byte context, repaired
long tooltip (18), pending queue and completed queued source (31). Validator
passes every capture and all prior comments; the one exact [0,23) Auto note
and resume-11 query/cursor persist. Review SHA256
`D8A6FE89DA5CE87DED03A5A8521935C5120507DF457A4464D6043E5A0A40139B`.
The independent square run is the last matrix case.

**Square / ER-S2 completion checkpoint (12:42 UTC):** JVM 25492, session
`2db1eefa-f22e-48dd-b7af-5531c2320295`, 32 steps, normal CLI exit 0 in 5m10s;
gallery `sfm-title_screen-20260906-084119-270`, logs `matrix-square*`.
All captures are actual 2000x2000 / GUI 4 / logical 500x500. Source text, exact
comment choice, bounded tooltip (18), draft/save and completed source/queue (31)
were inspected. Validator passes all geometry and all 2,921 preserved comments;
the square note is the sole added comment, resume 11 and query/Cargo cursor
persist, SHA256 `A88CBCB6A7277169D4F81E3CE5AFA4E1A33B212226C3992BC989BD615C3376B1`.
The full declared matrix and narrow split now have evidence; the one observed
viewport defect has focused/full regression plus fresh live proof. All 19
protected dependency/review/theme hashes were unchanged at 12:37 UTC. No Rust
change since the installed AB53 build; user install required: no.

Square also exposed a non-viewport race: clicking Show current before queue
publication superseded its pending refresh, so the first operation reported
`The Explorer changed before its review lens loaded` (08:40:37 local). The
replacement operation subsequently opened the correct saved source. This is
not called an error-free journey: ER-S3 must reproduce/rank it with the related
queue-blanking observation below. It did not corrupt persisted state.

**Observation for ER-S3, not yet a repair claim:** Show current temporarily
empties the queue while a branch reload is pending (step 30), then restores
filtered rows and opens the requested source (31). Frames continue and pending
feedback is honest, but losing the prior rows for several seconds is distracting.
Reproduce and rank during the fresh-user charter before deciding its one repair.

**Work:** Reuse the current puppet viewport contract for 1920x1080 at GUI scales
1, 2, 3, 4 and auto, plus a square 2000x2000@4 and narrow split. Verify declared
support first; if a review-specific profile unjustifiably rejects these, repair
that bounded profile/input-layout coupling rather than silently swapping sizes.

**Validation:** Repeat the review/comment/remaining route with semantic targets
or geometry-derived virtual inputs and inspect screenshots. Assert actual window,
GUI scale, panel bounds, menu/tooltip clipping, icon rendering and text access.
Record any platform/lockscreen limitation instead of inferring a pass.

**Completion:** This slice's matrix has accurate runtime evidence or an explicit
external blocker; fixtures do not hard-code OS coordinates. No claim of every
Minecraft version or every puppet. Requires the preceding checkpoint.

### [x] ER-S3 Perform a fresh-user walkthrough and fix one remaining obstacle

**Completion checkpoint (13:10 UTC):** Fresh JVM 39156, session
`0c4b3ab2-49d9-43d0-a302-4bf26012f805`, completed 24 virtual-input steps and
normal CLI exit 0 in 8m08s. Gallery `sfm-title_screen-20260906-090813-499`, logs
`fresh-user-green*`. Only visible menus were used: writable open, Remaining work,
then Show current during initial publication (16) and warm/repeated (19/21/22).
Figures 17/18/20 retain the populated queue and exact Cargo.lock [23529..23692)
preview; warm actions preserve relation r4 and the same panel 2 rather than
blanking/reloading or splitting. Warm dispatch measured 1,013/975/935 microseconds
in this run, not a general latency guarantee. No queue failure event occurred;
the log still contains unrelated mod-startup errors, not an error-free-log claim.
All 2,921 comments compare exactly with the core copy; only explicit Remaining
activation advances resume to 11. Green review SHA256
`0994BB3D8C1B9A07DADB08E974B94FB91DF48014FC608050BEAC071E03FC7AA3` matches the
baseline semantic result. All 19 protected hashes and core `48EBC5DD...FA5A9B58`
remain unchanged. Focused red/green and full-suite evidence below are current.
Java-only source changes require no Rust reinstall; installed AB53 build remains
current. The game exited normally. The guide links this repair's before/after.

**Claim / charter (12:42 UTC):** Fresh title screen, 1920x1080@2, independent
`runGameTestPreview/rno-fresh-user.sfm-review.json` copied from the verified core.
Use Ctrl+Shift+E, visible Explorer location/filter/row menus, visible review
lenses and queue buttons; do not type known action IDs to skip discovery.
Open writable through the file's menu, browse SFM.java, inspect a saved comment,
find remaining work, and exercise Show current both during loading and after
the queue is warm. Compare blanking/cancellation against other observed friction
before selecting one repair. Protect every existing comment and review authority.
Record baseline evidence and exact acceptance before any source edit.

**Baseline / selected obstacle (12:50 UTC):** JVM 9616, control session
`ae20dc80-d95f-41dc-8bac-72d77ee9fea9`. Steps 1–19 use Ctrl+Shift+E, a filename
filter, the file's writable-review menu, hierarchy and After. Steps 21–39 use
the visible Comments menu, clear the retained filter with Escape, find the
long E note and open its value as a tab in the existing preview area. No action
ID was typed. Steps 40–46 choose Remaining work, clear the note filter, and
show a warm 128-entry queue. Step 47 clicks Show current; 48 replaces those
rows with a raw query-root identifier while source lookup is pending. This is
the selected highest-impact bounded repair, together with its pending-load
race already captured by the square matrix. Data remains intact.

**Cause and acceptance before editing:** Remaining-work activation supplies an
explicit normalized query; Show current unconditionally switches to the implicit
saved-query lens, giving equivalent expressions different roots. Even identical
roots take an explicit refresh path. Add an idempotent navigation-only route
that reuses a current-epoch Query lens when its normalized expression equals
the saved active query; otherwise retain the legitimate lens switch. Do not
make Next/Defer/save skip their required mutation refresh. Warm Show current
must retain root, rows, filter, scroll and saved-file bytes during lens preparation
(the subsequent explicit reveal may scroll to the saved row); an in-flight
equivalent lens must not be canceled/replaced. Different queries, changed
epochs and real mutations must still resolve/reject correctly. Add focused
fixture tests with controlled async publication, then repeat visible warm and
pending clicks in a fresh JVM and verify the exact saved target and unchanged
comments. This is not a general cache or Explorer rewrite.

**Unclaimed observations:** Keeping the file Explorer open makes the first
source preview a third narrow area (19); future layout/discoverability work can
help without overwriting that Explorer. The filter is not a full text field:
Ctrl+A does not select its text (34), whereas Escape clears it (35–37); retain
this as a future keyboard-consistency task. Menu candidates beyond the eight
visible rows require scrolling (26), which worked; clicking beyond the viewport
in steps 22–23 was an automation targeting error, not an application defect.

**Red test / implementation checkpoint:** Baseline walkthrough completed all
50 steps normally (9m14s), gallery `sfm-title_screen-20260906-085156-360`, logs
`fresh-user-red*`. Every one of the 2,921 comments is byte-for-byte equivalent
in parsed form to the core copy; only queue activation advanced resume to 11,
file SHA256 `0994BB3D8C1B9A07DADB08E974B94FB91DF48014FC608050BEAC071E03FC7AA3`.
Four new focused tests ran against the old forwarding behavior: two passed,
and warm/pending equivalence failed their immediate-reuse assertions as intended
(`stretch-s3-red*`). The navigation-only method now checks exact review epoch/path
and normalized effective-query equality before retaining the descriptor.
Show current uses it; cursor mutations continue through explicit switch/refresh.
Focused green run is pending; no performance claim from compilation alone.

**Focused green:** Four found/started/passed, zero failures/skips/aborts;
`stretch-s3-green-java.log` preserves the JUnit summary and all four successful
events (5,514 ms test runtime). The default-level NDJSON/console files are empty
because this successful run emitted no default-level messages; the archived
JUnit event log is the evidence, not an inferred pass from those empty files.
The full suite now runs with explicit info logging before the live replay.

**Full green:** 1,778 found; 1,777 passed, zero failures/skips, one installed-worker
opt-in abort (already exercised explicitly with the installed worker earlier).
Full run completed normally in 1m17s; `stretch-s3-full*` retains current info-level
output. Source edits and new tests pass scoped whitespace checks. Independent
green walkthrough is starting at GUI 2 with another disposable core copy.

**Work:** From the title screen and a disposable review, discover the loop without
using known internal IDs as shortcuts. Record observations, select at most one
reproducible highest-impact remaining obstacle within review navigation,
commenting, filtering or persistence, and define its acceptance here before
editing. Reuse shared actions; preserve all other findings as backlog.

**Validation:** Before/after virtual-input reproduction, focused regression and
RNO-A affected-path rerun. If no defect is found, record the charter/evidence;
do not invent a repair or use an arbitrary refactor to consume time.

**Completion:** The walkthrough is retained and the selected repair is proven,
or the absence of a new reproducible obstacle is honestly recorded. No framework,
semantic expansion or additional unbounded fixes. Requires prior checkpoints.

### [x] ER-S4 Implement contextual ItemStack preview rules and clipboard handoff

**Completion, 2026-09-06 15:44 UTC:** IPR-T0–T5 including T3a and all 35 ledger
atoms are verified in the linked plan's task evidence/proof map. The delivered
slice has typed structured predicates and conservative user/mod/default rule
resolution, explicit revision-pinned TOML authority, flat icon inspection,
context/generic command continuation, reusable picker/draft/Save/Cancel/reset,
and independent details/prompt exports. Prompt generation walks actual command
and operator descriptors without ItemStack catalogue enumeration or model calls.

Authoring passes at GUI 2/4 with new populated-theme fixtures; a distinct JVM
restores the exact saved theme/rule/source hashes. Live clipboard readback,
earlier-parameter editing, narrowing/reset and two-Explorer rendering all pass.
During acceptance, canonical suffix generation was aligned with explicit legacy
theme mappings, and read-only inspection closure was repaired for a terminal
newline projection mismatch; neither fix weakens editable unsaved-work guards.
Final pure/integration proof: rule family 38/38, read-only panel family 13/13,
full Java 1,819 passed/0 failed/1 opt-in abort, then configured installed-worker
1/1 with five successful real requests and normal shutdown. All 1,820 tests
therefore have a pass, without miscounting the opt-in abort.

Post-integration adaptive review proof: 31 virtual-input steps in JVM 11048,
normal exit 0 in 5m41s, gallery `sfm-title_screen-20260906-114145-150`.
The 2,921-comment disposable review, long Unicode value, exact SFM.java match,
reused source/value tabs and saved queue (generation 10, 131 materialized rows)
survive the shared integration. Repeated Show current dispatch is 1.540/1.513 ms
in this run, without a review write or extra preview split. View Image figures
16/20/27/30 were inspected; long canvas lines still require panning.
`final-review-smoke*`, `final-full*`, `final-installed-worker*` and
`final-readiness.json` retain the final evidence under the ignored readiness
directory. Three icon galleries and the IPR-01–35 proof map are under IPR-T5.
The [rule guide](../contextual%20itemstack%20preview%20rules%20guide.md) documents
scope, theme ownership, ambiguity/reset and manual clipboard sharing.
No broader async/cache/archive provider or cross-version completion is claimed.

**Claimed 13:10 UTC after ER-S3 checkpoint:** Complete the bounded IPR slice,
starting with T0 and closing G1–G4 before public/storage integration. Theme tests
must use a new disposable authority, never either protected user theme. Preserve
all earlier checkpoints; no external model invocation or automatic execution.

**Work:** Claim the complete bounded authoring slice in
[Contextual ItemStack preview rule authoring](contextual%20itemstack%20preview%20rule%20authoring%20plan.md):
IPR-T0, T1, T2, T3, **T3a**, T4 and T5, satisfying IPR-01–35. Close G4/IPR gates
before storage/public grammar changes. Include flat aspect actions; exact name,
basename, suffix and prefix predicates; typed string/Boolean completion; explicit
theme authority; preview/reset/save/restart; and the details/prompt copy actions.
Prompt documentation comes from registered command/operator schemas, never an
ItemStack catalogue or arbitrary suggestion-provider enumeration. Use current
`sfm action invoke` spelling and normal validation of user-run returned commands.

**Validation:** All IPR task tests and its virtual-input authoring puppet,
including zero catalogue-enumeration counters, no automatic AI/network calls,
cancel/no-write, explicit theme identity, unknown item rejection, save/reload,
and mouse/keyboard access. Rerun core review regression paths after integration.

**Completion:** The user can create and persist a reusable icon rule directly,
or copy a self-contained prompt to solicit one externally and run the validated
result. No companion/AI service is required. Broader X-8e async inference/cache
providers and X-8f archives remain deferred. This is a substantial final stretch,
not a promise that it will fit tonight. Requires all preceding checkpoints.

## Operational readiness and boundaries

- Target: 1.19.2, starting HEAD recorded under RNO-0; dirty user changes preserved.
- Frozen dependency declarations/lockfiles; no new Cargo/SFM/Java dependency,
  no new developer/reference repository, no Gradle. Deterministic hash-verified
  rehydration from existing locks (including sources/index) is allowed.
- Read [goal execution and testing readiness guidelines](goal%20execution%20and%20testing%20readiness%20guidelines.md).
  Inspect then gracefully stop/restart only proven SFM/game/toolchain test
  processes; force-stop only their identified tree after a bounded timeout.
  A locked desktop is a possible rendering/clipboard limitation, not a reason
  to hijack or move the OS pointer. Copy failure must not be reported as success.
- Use ignored disposable reviews/themes. Do not alter
  `docs/reviews/4.34.0-1.19.2-to-58ed4e431.sfm-review.json`,
  `platform/minecraft/run/manual-test.sfm-review.json`, previous acceptance
  copies, existing user preferences or human approval evidence.
- No commit/push/propagation/release attestation, unrelated process cleanup,
  raw huge-file virtualization, broad AI/static-analysis/logging platform,
  history/overlay redesign, new archive resolver or automatic model spend.
- Tooling/generated-input changes, installer commands, initial process/cache
  state and intermediate hashes are recorded under RNO-0/RNO-A/ER-F5. The
  conclusive post-stretch checkpoint below supersedes intermediate freshness.
- Final guide must say `User install required: no` with verified reasoning, or
  state the exact genuine blocker. Java-only work does not require reinstalling
  an unchanged Rust CLI. Changed Rust does require final checks/install.
- Risk controls: G1/G2 bound races and data loss; ER-F6 prevents false confidence;
  RNO-A separates adaptation from hidden model mutation; G3 preserves exact
  review meaning; ER-S4/IPR bound predicate/schema expansion and prompt disclosure.

### Final operational checkpoint — 2026-09-06 15:44 UTC

- Branch/HEAD: `1.19.2` / `16328629fa60c45a4f525b6f20aaa77715f91077`, with
  preexisting dirty changes preserved; no commit/push/propagation performed.
- **User install required: no.** ER-F5 ran the required Rust checks/installer
  after its final Rust edit; ER-S2/S3/S4 changed no Rust inputs. Final PATH,
  `--version`, hashes, configured installed-worker requests and live launch
  confirm the executable used for testing is current, not merely same-version.
- `G:/Programming/Caches/CARGO_HOME/bin/sfm-propagate-changes.exe`:
  version `0.1.1`, revision `16328629f`, built `2026-09-06 07:26:00 -04:00`,
  SHA-256 `AB53A7900E6E7CBE39B4FE0A9CFE8F52CC99E2F3D64D57B83B366BD2E2D2FFA0`.
  `G:/Programming/Caches/CARGO_HOME/bin/sfm.exe` remains unchanged at
  `CEACCF5CFEAA4F9E0A7C554EED219D0CF376DC1A69D2BCC7B191A02FEAD6C569`.
- Final full test run and final adaptive puppet rebuilt Java after the last
  production edit. Datagen ran after the last localization edit; current
  generated translations and in-game changelog are included. Final full suite:
  1,820 found, 1,819 passed, no failures/skips, one opt-in abort; separately
  configured integration 1/1 passed, no failures/skips/aborts.
- All **19** protected dependency/declaration/review/theme file hashes match
  the RNO-0 baseline. The read-only final walkthrough also leaves the disposable
  core review SHA-256 `48EBC5DD13FCCDC8A0A6B3D8F985C0C014CEE48BD517851D3C88C099FA5A9B58`
  unchanged. No new dependencies/clones or lockfile-mutating commands.
- Final game PID **11048** exited normally, as did icon authoring/resume games
  and the configured worker. Elevated ownership-scoped CIM inventory found no
  remaining SFM Java/game/worker/terminal/Cargo helper. No game is intentionally
  left running, and no OS pointer injection was used.
- Manual launch from the primary repository:
  `sfm-propagate-changes.exe run client --branch 1.19.2 --wait-for-build-lock`.
  The [review guide](../release%20review%20exploratory%20usability%20guide.md)
  supplies the exact disposable-review palette command, mouse/keyboard flow,
  pending-save behavior, remaining-work controls and evidence galleries.
- Limits: 1.19.2 only; existing third-party startup diagnostics remain visible;
  long text lines need canvas panning; Explorer filter Ctrl+A consistency is
  retained under ER-S3 as later work. Broader X-8e async/AI/cache, X-8f archives
  and release attestation are not part of this completion. All listed stretches
  were claimed and completed; none are silently abandoned or newly invented.

## Overall completion

- [x] RNO-0, ER-F6, ER-F1–F4 and RNO-A have durable completion evidence.
- [x] The same portable review survives a second JVM with exact notes/targets
  and useful remaining-work navigation; human review/approval files unchanged.
- [x] No new unexplained test failures, silent skipped acceptance, blocking
  I/O in the covered interaction paths, or stale callback retargeting remains.
- [x] Each claimed stretch is complete and checkpointed; unstarted items are
  explicitly handed off. Estimated duration has not substituted for acceptance.
- [x] Current builds/tools, datagen when needed, artifacts/View Image evidence,
  final process state and a mouse/keyboard walkthrough are delivered.
- [x] Broader review, icon-provider and cross-version work remains accurately
  scoped; completing this goal is not approval to publish a release.
