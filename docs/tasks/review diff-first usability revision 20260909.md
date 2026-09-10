# Diff-first review usability — discussion draft

Status: completed and verified 2026-09-09; final acceptance below supersedes
chronological pending checkpoints. Approved 2026-09-09. Predecessor: single-file lazy review
overnight plan.md. Inherit goal execution and testing readiness guidelines.md:
frozen dependency declarations/lockfiles, existing-lock cache recovery only,
no new dependencies/clones, no Gradle, no commits/push/propagation, preserve user
reviews and unrelated edits, virtual input without OS cursor movement.

**Authorized dependency exception (2026-09-09):** User answered “Allow exact-pinned
language grammars” to adding narrowly scoped exact-version Arborium language
dependencies for Rust and other repository languages identified by the coverage
audit. This supersedes the frozen posture only for those grammar additions and
their required locked dependency closure in platform/cli/sfm-propagate-changes/
Cargo.toml and Cargo.lock. Prefer existing Arborium2.18.1 family compatibility;
verify availability and compatibility before selection. No unrelated upgrades,
reference clones, permissive local-artifact bypass or other declaration changes.
Record exact identities, lockfile diff, dependency-policy checks, installer and
syntax coverage evidence before completion. All other dependencies remain frozen.

## Evidence and uncertainty

- User reports roughly one minute opening
  working-tree-1dbe5982-7a9d-4d12-a125-7a021dad3ebd.sfm-review.json and about1545
  automatic comment entries. Do not call this a measured phase breakdown.
- Read-only inspection finds that file is1548bytes, schema sfm.release-review/3.
  Therefore this incident is not explained by a large serialized review file.
- SFMReleaseReviewLedgerProjection.project identifies derived observation
  comments, removes them from durable comments/bindings, and captures evidence
  only for durable targets. Removing generated markers still matters for UX and
  potentially materialization cost; do not claim it alone solves opening latency.
- Reported plain Rust surface: after, platform/cli/sfm/src/cli.rs,
  review2d14d8d7-cdf3-3c29-b5a1-74e18f76bcec, Explorer3, relation8. Icon correctly
  identifies Rust while syntax is missing. SFMReviewExplorerModel's pair-building
  path currently assigns java for .java and text otherwise (around line1588).
  This is a concrete suspect, not a complete runtime diagnosis.

## Requirement atoms

1. **Empty initial comments.** New review stores baseline/candidate target,
   policy/scope and minimal session state, with no automatic per-file comments.
   Diff/change discovery is derived data, not a comment authored by the system.
2. **Separate change coverage from comments.** Remove generated release-change
   markers from ordinary comments, counts, decorations and comment navigation.
   Retain a typed changed-surface model for diffing and remaining-work queries;
   do not lose the denominator of approval coverage when removing markers.
   Preserve legacy evidence and human text, even text containing #release-change;
   classification must use provenance, never just a hashtag. Plan compatibility
   explicitly rather than destructively rewriting old files.
3. **Live target, immutable comment.** Git before target is a resolved revision;
   after may be current files on disk, including untracked files in scope.
   Browsing/refresh resolves current state. A comment pins the exact displayed
   non-Git content on first capture in the same file, deduplicated. Later edits
   must expose stale/missing/relocated evidence, not silently move approval.
4. **Diff-first presentation.** Before/After remain source views; inline and
   split diff views explain changes. Automatic comments must not stand in for
   removed/added highlighting. Unchanged common syntax stays neutral.
5. **Shared comment identity across views.** Selecting a diff's left/right source
   maps to exact before/after revision and UTF-8 ranges. Same comment is visible
   in corresponding source and diff views, without duplicate comments. Cross-side
   selection may produce a composite selector; headers, gutters, padding and
   alignment-only rows cannot invent source bytes. Test Unicode, CRLF, missing
   sides, disjoint selections and changed source during draft capture. Comment
   persistence must use displayed source evidence, not rendered diff text.
6. **Wheel toggle beside # in text editor v3.** Mouse wheel zooms versus scrolls.
   Localized state/tooltips and action-backed keyboard/CLI access. Preserve zoom
   anchor and scroll semantics; preference scope/defaults need discussion.
7. **Pointer-role toggle beside #.** Middle pans/right opens actions versus
   middle opens actions/right pans. Preserve left-drag selection, gesture capture
   and panel-level controls. Existing middle-click panel management must not
   steal the content's configured gesture. Define click-versus-drag behavior.
8. **Language routing, not icon inference.** Resolve source language from actual
   source identity/path/metadata, including virtual review addresses; preserve it
   across Before/After and diff panels. Use installed/pinned Arborium providers.
   Surface unavailable/pending/failed highlighting instead of silent fallback.
9. **Repository syntax coverage audit.** Inventory repo-root file types (not run
   directory), rank by count and source bytes. For representative meaningful
   samples, verify detection, enabled grammar/query, worker response and rendered
   nontrivial spans in ordinary and review views. Rust is a required regression;
   Java, Markdown, JSON, Gradle/Groovy and other popular types follow inventory.
   A registered language or correctly colored icon is not proof of highlighting.
   List unsupported/binary/generated/excluded cases and reasons; do not game a
   percentage by counting empty/plain fixtures. Include GUI2/4 evidence.
10. **Opening performance.** Measure cold/warm actual-scale open by phase:
    source enumeration/Git reading, diffing, generated model, serialization/IPC,
    Java decoding, Explorer indexing and first usable rows. Separate total wait
    from render-thread stalls. Stage/cancel background work and show meaningful
    progress; avoid doing every diff before the user can browse. Record absolute
    times/allocations where available, not noisy percentage gates. Re-run against
    the reported target using a disposable session without modifying its file.

## Approved vertical slices

1. Diagnose actual-scale open + remove generated-comment coupling while preserving
   query/coverage parity, sparse persistence and legacy compatibility.
2. Fix shared language routing and add repo-driven highlighting coverage checks.
3. Establish exact diff/source comment round-trip and visible navigation at both
   GUI scales. Verify save/close/reopen/refresh and old-approval isolation.
4. Add the two action-backed pointer toggles, including multiplexer arbitration,
   persistence and localized tooltips. Verify every combination using virtual input.
5. Repeat a disposable natural review journey at repository scale, record latency
   and images, update guide/changelog, tests/datagen/tool installation evidence.

## Accepted defaults

- Proposed pointer preferences: per-editor overrides, with a persisted user
  default for new editors; no surprise change to every open editor.
- Proposed initial wheel default: preserve existing behavior until user changes
  it. Persist the chosen new-editor default explicitly rather than implicitly.
- Generated markers should disappear from ordinary review UX, not merely hide
  behind another filter. Historical human comments remain intact.
- Aim first for responsive progressive opening, then measured total-time gains.
  Do not promise a particular speedup before identifying expensive phases.

## Execution checkpoint — goal start

- Goal active; slice1 diagnosis/generated-comment separation in progress.
- Branch1.19.2 HEAD16328629fa60c45a4f525b6f20aaa77715f91077;439existing
  dirty/untracked entries. Preserve these, including preceding-goal changes.
- Existing user client: launcher11148, JVM18852, runClient on this branch.
  Inspected only; not stopped. Prefer read-only source/log diagnostics first.
- Protected working-tree review SHA256:
  82BAD424C705603E130C565B435E7560A6F6D259AA7E29DC04D1096197D12D5F.
  Protected review-2026-09-07 and manual-test hashes remain52E8EE6E...D306 and
  9ADDCCEC...D592 respectively (full hashes in predecessor final checkpoint).
- Additional syntax finding: SFMTextDocumentLanguage maps rs to rust but declared
  rust falls through to NEUTRAL. Thus fixing review-pair language alone is not
  sufficient; verify the full worker/render route.
- No dependency changes or new clones. Final tests/datagen/installation and
  GUI2/4 acceptance are still pending; preceding-goal evidence is not acceptance
  of this revision.

### Initial measured diagnosis

Read-only installed CLI `review session resolve --file <reported review>` exited0
in61774ms with33620835 output characters. Timer covers process/output collection,
not subsequent PowerShell JSON parsing. The observation envelope uses `resolution`;
the initial summary accidentally queried root.document, so no corpus/comment
counts were established by that summary. Do not infer counts from its blank fields.
Probe session94807 reaped; existing launcher11148/JVM18852 untouched. This is one
baseline run, not a cold/warm distribution or an in-game render-thread measurement.

Syntax worker engine.rs explicitly rejects non-java requests and constructs an
arborium_java grammar/query. Full remediation therefore needs three boundaries:
review metadata, Java highlighting route, and Rust grammar dispatch. Verify pinned
available grammar dependencies before extending; no dependency mutation authority.

### First implementation checkpoint (not yet verified)

release_review_ledger_resolve now excludes newly generated materializer comments
and their synthetic styles from live observations, retaining typed review units,
corpus and existing durable comments. Added initial-empty-comment assertions to
the exact capture/old-approval isolation regression. Frozen legacy materializer
behavior remains unchanged for compatibility; generated legacy UI handling and
avoiding generation cost at source are still pending.

Focused locked/offline Rust regression session6409 running. Full check-all and
installer must follow final Rust edits; installed tool remains baseline-only.
Cargo declarations/lock currently include arborium-java and shared highlighting,
but no Rust grammar. Do not disguise a lexical fallback as Arborium highlighting
or silently add dependencies. Resolve this capability boundary explicitly while
continuing independent work.

Focused regression6409 exited0:1passed/0failed,3.66s test execution (1m41s build).
That validates the initial generated-comment separation patch, not subsequent
phase logging. Added phase logging for capture/materialization/observation
validation/total resolution next; it still needs a rebuild before measurement.
No installed CLI update yet. Existing user client remains untouched.

Initial repo-root tracked+unignored-untracked extension counts: Java2221,
Rust433, JSON207, Gradle107, Markdown81, PNG78, PowerShell72, text46, SFML21,
TypeScript14, TOML7. This is an inventory, not highlighting coverage; byte weights,
representative content and actual rendered spans remain pending. User explicitly
authorized the grammar dependency exception above during execution.

### Instrumented materialization diagnosis and patch

Build14278 passed. First command's document read used the CLI workdir by mistake;
corrected to root and read the checkpoint separately. Probe38119 exited0 in77945ms
using the debug build (not directly comparable to installed release timing).
Evidence: platform/minecraft/build/diff-first-resolve-20260909.ndjson.
capture10561ms/3372entries; materialization63132ms; target73697ms;
2070documents/1917units/1917generatedcomments; final validation2517ms,
29643512serializedbytes; resolve_total76242ms. Original file unchanged.

Source shows review_session_v2::evaluate_comment clones all revision lanes for
each committed comment and invokes v1 validation. The materializer repeatedly
canonicalizes/validates a full generated-comment corpus before the live resolver
can filter it. Added materialize_observation mode to omit marker construction and
synthetic styles BEFORE those validations, retaining units/documents; original
materialize remains the legacy path. Resolver now uses the new mode. Resolver
regression suite17066 running after nightly cargo fmt. This patch's performance
improvement is not yet measured or claimed. All final integration gates pending.

Exact package metadata checks confirmed arborium-rust2.18.1 and arborium-json2.18.1
exist; latest2.18.2 was not selected. No declarations changed yet. docs.rs browsing
failed; canonical cargo info supplied the package identities.

Resolver suite17066 passed5/0 in3.80s, including live authority immutability,
old-approval isolation, Git membership and copied-review exclusions. Build51935
is currently running to produce the patched binary for the comparable debug
measurement. Next: reap that handle, rerun probe with a distinct phase log, inspect
times and counts, then continue remaining generated-marker/UI and language work.

Build51935 passed28.02s. Patched probe1004 phase evidence in
platform/minecraft/build/diff-first-resolve-no-markers-20260909.ndjson:
capture10622ms/3372entries; materialization3583ms (previous63132ms);
target14207ms; same2070documents/1917units, now0generatedcomments;
validation2254ms; resolve_total16487ms (previous76242ms).
This is a same-profile debug comparison, not a claim about end-to-end GUI latency
or statistical variance. Capture remains the next significant phase. The working
tree changed only via goal code/docs during the comparison, so byte-identical
corpus is not claimed. No approval transfers or source exclusions were added.

### Multi-language worker checkpoint

Added exact=2.18.1 direct grammars: rust,json,groovy,markdown(default-featuresfalse),
powershell,typescript,toml alongside existingjava. Cargo.lock diff inspected:
9new grammar packages only (includes javascript/jsdoc inherited by TypeScript),
108added lines, no removed/version-changed existing packages. Cargo fetch also
rehydrated already-locked security-framework3.6.0/rustls-native-certs0.8.3 cache
entries, not new graph additions. No reference clones or unrelated upgrades.

New syntax_highlight/languages.rs registry; lazily compiled per-language queries,
parser switching/reset, language-specific cache keys retained. Server advertises
registry; Java/Rust parser fingerprint updated together to source-grammars/2.18.1
and formatting/2. Java canonical language routing now includes rs/rust,
gradle/groovy,md/markdown,ps1/powershell,ts/typescript,toml,json. Review corpus pair
uses shared filename resolver instead of Java-versus-text special case. Java
metadata regression updated; Java tests still pending. Existing diff syntax
projection independently requests each source's language and can now use these
routes. In-game rendering and repository audit still required.

Check76747 passed. Rust syntax suite76216 passed28/0 in0.33s (1m08build), including
actual non-neutral bounded spans for all8languages and cancellation/cache/protocol
regressions. This is representative engine coverage, NOT full repository or GUI
coverage. Java plain input fallback/offline behavior and Markdown embedded-language
injections need explicit verification/limitations; no claim of injection support.
No installed CLI rebuild yet. Existing game remains unchanged.

Java test attempt failed fast on verified existing game build-cache lock (not a
test failure). Verified JVM18852 parent11148 and exact branch launch argument;
CloseMainWindow returnedtrue. Both processes subsequently absent; no force kill.
Retry68626 now compiles/tests SFMTextDocumentLanguageTests; log
platform/minecraft/build/diff-first-language-tests-retry-20260909.ndjson.
The user's game is now stopped normally and will need relaunch after final tool
installation. No full GUI or Java acceptance claimed yet; continue from this handle.

Java language suite68626 exited0:4passed/0failed in1m00s total. Existing game and
launcher were closed normally, not force-killed.

Pointer implementation checkpoint: immutable per-editor settings with independent
wheelZooms/middlePans toggles; config persists defaults only through explicit save.
Named actions document/pointer/wheel, buttons, save_defaults target originating
V3 panel. Two buttons beside # invoke those actions; canvas wheel scroll preserves
zoom, Shift scrolls horizontally; context action button follows the swap, pan
capture/release follows the opposite button. Left selection unchanged. Existing
workspace Alt+middle panel move arbitration remains separate. Added settings tests
for all4combinations, involution, independence and immutable defaults.
Compilation/focused tests currently starting; labels are provisional Z/S and M/R,
localized full-state tooltips and richer mouse default-save affordance still need
polish and GUI validation. No claim of complete pointer acceptance yet.

Pointer test launcher37859 is live; main Java compilation passed in21465ms,
gametest/test compilation still running. Re-poll this handle rather than restart.
Button tooltip implementation is in client/screen/widget/SFMButtonBuilder.java
and SFMExtendedButtonWithTooltip.java (not client/widget). Next polish should
show current and next modes in localized tooltips, ensure mouse default-save
access, verify chrome/gesture arbitration and tests before GUI2/4 evidence.

### Continuation checkpoint — legacy markers, repository audit, integration freshness

Previous turn classified progress: full Java evidence isolated the only failure to
SFMSyntaxServerInstalledWorkerIntegrationTests, Unsupported syntax parser
fingerprint (old installed worker versus new Java protocol). Do not weaken the
fingerprint check. Full Java run:2036found,2030passed,1failed,5aborted; log
platform/minecraft/build/diff-first-java-full-20260909.ndjson. No full pass claimed.
Pointer37859 passed2/0; dynamic current/next tooltip suppliers and right-click
control menus were added afterward and compiled in subsequent full/focused runs.

Rust check49566 caught resolver101/100line lint. Extracted unchanged validation
phase helper. Check24641 passed (712unit tests,3ignored;10+12+40integration tests).
After audit/Git observation changes, check12986 passed713unit,3ignored plus
10+12+40integration tests. Check26914 was an intermediate wildcard-import lint
failure, fixed with explicit imports. No lint suppression was introduced.

Known legacy generated marker producer provenance is now excluded from source
decorations, source comment children, Comments/Hashtags and comment migration
projections, WITHOUT deleting records or rewriting saved evidence. Classification
requires kind=generated AND one of the three known Git/working-tree/retired
materializer IDs; a human #release-change comment and other producer annotations
remain visible. Focused15377 passed1/0 (70s total), including adapter persistence
identity and marker/human/other producer separation. Coverage still uses typed
units and the unchanged kernel; further GUI/legacy-scale verification remains.
Live Git-target observations now skip generated marker construction at source,
as the working-tree mode already does. Frozen materialize APIs remain compatible.
Git resolver regression explicitly asserts units retained, zero synthetic comments
and styles, zero effective approval. Source evidence was not pruned.

New repeatable command: `sfm-propagate-changes.exe --output-format json syntax audit
--root D:/Repos/Minecraft/SFM/repos2/1.19.2`. It inventories git tracked plus
unignored untracked files, count/bytes per extension, and up to3 nonempty UTF-8
samples per extension through the real engine, preserving hashes and diagnostics.
Report states exact engine-only verification boundary and sample exclusions;
does NOT claim those samples prove rendered Java/worker/diff coverage. Root-only,
source byte limits, no source bodies exported, unknown/empty inputs do not count
as highlighted. Test includes untracked samples, ignored exclusion and subdirectory
rejection. Parse diagnostics now name actual source language rather than Java.

Actual audit (debug binary,2.4s,exit0): Java2227files/15140727bytes,
Rust436/4868923,JSON207/31044760,Markdown81/3278398,Gradle107/152138,
PowerShell72/99306,TypeScript14/298972,TOML7/14333. All24selected samples yielded
nonzero styled spans. Gradle build.gradle and one PowerShell sample reported two
recoverable parse gaps each; do not claim complete grammar fidelity. Markdown is
block grammar without embedded-language injection. Two tracked Java paths are
missing. Remaining languages/binaries/native SFML are explicitly outside Arborium
probe coverage; classification and stronger source/render samples still needed.

Ordinary and diff editors now expose pending/unavailable syntax status; failed
diff results are no longer silently ignored. Focused Java presentation/status run
21222 is currently live, log diff-first-syntax-status-tests-20260909.ndjson; includes
late completion after close. Reap this handle before launching another Java build.

Installer89246 is live (locked/offline, exact grammar builds); re-poll rather than
restart. This is an intermediate installation, not final OPS-7 evidence if any
later Rust/generated input changes. Full Java rerun must follow installation.
Protected original review hashes rechecked unchanged:82BAD424...D5F,
52E8EE6E...D306,9ADDCCEC...D592. Game remains stopped normally. GUI2/4,
repo-scale natural journey, final datagen/guide/install remain unfulfilled core
gates. Goal remains active; no completion claim.

Follow-up: syntax status/presentation21222 passed4/0; installer89246 succeeded
locked/offline in91s. Installed executable G:/Programming/Caches/CARGO_HOME/bin/
sfm-propagate-changes.exe, rev16328629f built2026-09-09 11:09:54-04:00,
SHA2565E784475A0F80153C2719132F3BDCA6A282A2EACFA341B5FE11717CF1BA4FBB4.
Full Java current-worker62035 passed2033/0failed,5explicit opt-in aborts,
2038found,77s total. The previously failing installed syntax worker now passes;
no protocol validation was weakened. Source changes after this checkpoint require
fresh relevant verification, not reuse of these counts.

Disposable GUI2/4 ledgers created at platform/minecraft/build/
diff-first-gui2.sfm-review.json and diff-first-gui4.sfm-review.json. Both retain
the reported target's actual repo-wide scope and baseline31135b8e..., with new
session IDs and zero comments/evidence. Their original-review exclusions match
the reported ledger; their own file is excluded by the resolver. These are the
only files authorized for demonstration comments in the forthcoming GUI journey.
Do not shrink the domain to a toy fixture for opening-performance claims.

Datagen8176 currently live, log platform/minecraft/build/diff-first-datagen-20260909.ndjson.
Reap before a Java/game launch. No new puppet/game has been launched this goal yet.
Next: verify datagen resources, run virtual-input GUI2/4 natural journeys with
source/diff syntax, mouse role combinations, exact comment identity/persistence,
and actual-scale open/cancel responsiveness; fix observed issues before handoff.

Datagen8176 exited0 in50.7s, two warnings, zero errors. All eight pointer
localization keys are present in generated/resources/assets/sfm/lang/en_us.json.
Changelog gained the legacy marker and visible syntax/audit entries during this
run; next runtime build must pick up the current template/resources (do not assume
earlier copied runtime inputs contain that last changelog edit). No live handles
remain from this checkpoint. No GUI evidence for this goal yet. Next action is
the planned virtual-input exploratory GUI2 launch, followed by GUI4, not another
restart of the already completed test/install/datagen handles.

### Live GUI2 exploratory checkpoint (2026-09-09)

The previous status-only goal turn was no progress, not a technical blocker.
Goal is active. Revalidated JVM8660 alive (started11:28:35), using current
installed worker and fresh compiled Java/resources. CLI session13622 owns
`puppet run sfm:title_screen_exploratory_review --branch 1.19.2 --variant
1920x1080@2 --wait-for-build-lock --log-filter info --log-file
platform/minecraft/build/diff-first-gui2-20260909.ndjson`.
Virtual control directory: platform/minecraft/runGameTestPreview/sfm-puppet/
exploration-control/session-bb73772f-61d9-4ef7-bc10-7bb88c98612d.
Requests through000023 completed; next000024. Revalidate process before next
input; never restart merely because a response is delayed. This puppet has a
bounded900second lifetime. No OS pointer injection.

Disposable actual-repository review diff-first-gui2.sfm-review.json opened
writable with zero comments. Exact filter platform/cli/sfm/src/cli.rs yields
one match, four context ancestors, six context children after expansion.
After opened through mouse double-click (dispatch198512us, observation406ms).
Step0010 visibly reports Rust highlighting loading;0011 shows real colored
keywords/types/comments after completion. Step0012 toolbar Z switches to S;
step0014 scroll-3 moves text216physical pixels without resizing glyphs.
Step0015 switches M to R;0017-0019 right-button drag moves canvas40x50logical
pixels without panel-management interception. Step0020 middle click opens
document contextual actions;Escape dismisses it. Step0022 opens structured
inline Rust diff with addressed-document loading;0023 shows Rust syntax plus
green added-source backgrounds, neutral diff header. Since before is missing,
this does NOT prove unchanged-span neutrality or removed-source highlighting.
Each new editor retains default Z/M rather than inheriting another panel's S/R.

Evidence images are screenshots/title_screen_exploratory_review__explore-step-
NNNN__viewport-1920x1080-gui-2-effective-2.png under runGameTestPreview.
Found defects to address: first Explorer frame briefly exposes raw changes UUID;
ordinary and inline document glyphs draw behind the bottom toolbar. Source
inspection confirms SFMDrawCanvasScreen.render renders full-height glyphs then
chrome without a content scissor. Fix must respect multiplexer coordinate/scissor
ownership and split renderer as well, not disable outer clipping globally.
GUI4, remaining pointer combinations/default persistence, source/diff exact
comment persistence and stale approval isolation, other-language rendered audit,
cancel responsiveness, final guide/readiness remain required. No completion claim.

GUI2 follow-up0024/0025 opened structured split Rust: actual source syntax and
green added backgrounds render. Its diagnostic explicitly states Java-only
structural matching with fallback for Rust; do not describe this as Rust AST
matching. Missing-before fixture still cannot establish unchanged-span neutrality.
Step0026 finish closed JVM8660 normally; CLI13622 reaped exit0. Durable gallery:
platform/minecraft/build/sfm-toolchain/artifacts/game-test-preview/runs/
sfm-title_screen-20260909-114121-872/index.html (26captures). Report's one ERROR
is an Industrial Foregoing missing texture during startup, not SFM action failure.

Patched SFMDrawCanvasScreen.render to nest pose-aware SFMScissorStack clips above
the bottom28logical pixels for ordinary/inline and split content, restoring parent
clip in finally before chrome/tooltips. Split renderer also receives content height.
Changelog updated. This invalidates prior Java/runtime rendering acceptance;
fresh GUI2/4 proof still required. Focused compile/scissor test session58925
running with log diff-first-toolbar-clip-tests-20260909.ndjson; re-poll this handle
before any new build. No Rust/dependency changes in this follow-up.
Session58925 reaped exit0: full Java compilation succeeded; two existing scissor
intersection tests passed, zero failures. These prove clip math, not the new
render call placement; visual toolbar acceptance remains pending. No live game
or test handle remains at this checkpoint.

### GUI4 live checkpoint

Previous turn made progress (toolbar patch, compile, clip tests, durable gallery).
Launched current-source1920x1080@4 exploratory review; CLI session8567 PID29848,
JVM4976, effective4 logical480x270, ready11:45:08. Initial sandbox CIM denied;
normal-access inspection confirmed scoped CLI ownership. No unrelated process
stopped. Control directory under runGameTestPreview/sfm-puppet/exploration-control/
session-1ed03dbf-7f7d-46b3-b78a-3a3907a9ec2a; requests000001..000018 done,
next000019. Keep this live session; revalidate before input,900second lifetime.

Opened disposable diff-first-gui4 review (zero comments; dispatch31108us, not
total opening latency), filtered actual platform/cli/sfm/src/cli.rs, opened After.
Step0010 confirms colored Rust at GUI4 and corrected bottom toolbar clipping.
Narrow240logical-pixel panel exposes Read-only badge overlap with pointer buttons.
Patched renderReadOnlyChrome to reserve entire left controls group through pointer
button instead of only #. This later edit is NOT in current JVM and requires build
plus fresh GUI verification; no runtime acceptance claimed for that follow-up.

Steps0011-0013 CtrlHome,CtrlShiftRight,AltEnter selected exact Rust token `use`
and showed comment range[0,3). Tab/Enter then Tab/Enter approved this ONLY in
disposable review. Step0017 showed pending save/toast;0018 generation3,dirtyfalse,
comment_count1,visible green checkmark and exact token background. Disk file
53424bytes after first capture. Need inspect durable comment/evidence identity,
open same comment from source and inline/split, close/reopen, stale-source isolation.
Do not infer all those from successful save. Current live view is Rust After with
first token selected and approval visible. No other human review mutated.

GUI4 steps0019..0027: AltEnter at selected approved bytes opens generic selection
menu, not existing-comment actions (keyboard discoverability gap). Right-click
at approved `use` opens human:release-review:1 details, exact target, open-text and
reveal actions;Tab/Enter opens its actual body. Durable ledger inspected: exactly
one comment, one content blob, one document; content SHA verified independently,
selected bytes0..3 equal `use`, not diff text. Comment identity
human:release-review:1, document SHA5494e5312a400469be068ff348707dcd167b1458415d19eb5cfb8a47651f24b9.

Opening retained inline diff after save fails with selected row missing/stale,
both double-click and Enter. Root cause: new generation has lazy leaf maps empty
while Explorer still holds valid rows; reusable source index does not hydrate leaf
map. Patched ProjectionSnapshot.leaf to traverse only requested exact node path
against current model. It validates full scheme/authority/revision/segments and
allows trailing-slash expandability hint to change after first comment. No global
materialization or cross-epoch fallback. Added regression opening all prior source/
diff paths BEFORE descendants repopulates new generation; initial15tests passed
(session29327 exit0,62s). Then added expandability-hint regression and corresponding
strict component equality refinement; these latest edits require fresh test result.
ReadOnly badge group patch compiled successfully in that initial run as well.

GUI4 finished normally via000027;CLI8567 exit0,JVM4976 stopped. Gallery runs/
sfm-title_screen-20260909-115505-085/index.html has27captures. Actual SFM stale-row
errors are present in console despite launcher report only counting one startup
ERROR: do not treat report counter as comprehensive action failure evidence.
Next rebuild test log diff-first-post-save-row-tests-final-20260909.ndjson; inspect
live handle before new build. Need fresh GUI verify row fix, comment diff/reopen,
badge layout, keyboard comment access and remaining original core requirements.
Live regression handle79407 (initial compile in progress); no live game. Reap it,
do not restart based only on observation timeout.

Regression79407 reaped exit0:15passed/0failed including retained-row resolution
and trailing-slash expandability change. Keyboard context provider now appends
existing comment text/reveal/matches/selector actions for evaluated ranges
intersecting exact captured source selections, including source ranges projected
from diff captures. Existing new-comment offers remain; named action commands
carry the current open epoch. Skip known generated markers and unresolved targets.
Caret uses start-inclusive/end-exclusive containment; nonempty selections use
half-open overlap and exact revision identity; zero-length target ranges excluded.
Focused85145 passed2/0 after full Java compilation (57.4s), testing matching bounds,
caret, wrong revision, adjacent ranges and zero-length target. This does not prove
in-game menu placement or all comment evaluation statuses; fresh GUI still needed.
Modified-file whitespace check passed. Changelog and live review capture-on-comment
guide updated, explicitly separating observed additions from pending acceptance.
No live game/build handles at this checkpoint. Next: fresh GUI reopen saved GUI4
ledger to verify persistence, post-save row opening and keyboard comment actions,
then remaining pointer/split/other-language/cancel/full-suite requirements.

Fresh GUI2 continuation: confirmed live Java PID6000 (started12:02:52), control
session-337e178f-0b62-4e46-a0ae-40044bc37178. Requests000001..000014 dispatched;
next000015. Do not relaunch without checking this process. Step000003 reopened
diff-first-gui4.sfm-review.json in this new JVM: writable=true,dirty=false,
comment_count=1,epoch2,generation2. Cross-JVM persistence proven, not yet full
cross-view navigation. Step000008 exact-path filter resolved one match. Step0012
exposed separate current working-tree and retained review-evidence lanes for
cli.rs. Retained lane presents unavailable diff leaves and long raw evidence IDs;
current After has no visible approval marker in step0014, while Rust highlighting
and separated bottom controls render correctly at GUI2. Investigate source identity
versus retained evidence before claiming comment navigation acceptance; do not
silently transfer approvals to a newer observation. Current source opened successfully.
The earlier turn answering status was informational; this continuation produced
new runtime persistence/navigation evidence. Original user reviews untouched.

Reopen follow-up000015..000019 completed. Current cli.rs SHA is exactly the saved
5494e5312a400469be068ff348707dcd167b1458415d19eb5cfb8a47651f24b9; document_id in
release_review_working_tree.rs includes repository,lane,source snapshot,side,path,
hash. Other working-tree changes therefore produce a new identity even for these
unchanged bytes. This is not lost persistence: historical approval intentionally
does not confer current-snapshot approval. Opened retained After (logical170,347),
CtrlHome,CtrlShiftRight,AltEnter. Step0018 offers existing comment text/reveal/
matches/selector actions for human:release-review:1 at epoch2 alongside exact new
comment [0,3). This verifies the keyboard context patch against saved evidence in
a fresh JVM. Next UX issue: buildReleaseChanges currently treats unpaired retained
evidence as a normal pair, producing raw evidence-lane labels and four unavailable
diff leaves. Give historical evidence an explicit presentation without implying
current coverage; preserve IDs and safe explicit migration rather than aliasing
revisions merely because hashes match. Current-source related-history affordance
still needs design/implementation acceptance.

Normal finish000019 dispatched; PID6000 absent after shutdown and launcher log
reports successful 9m48s run at16:12:20Z. No live game from this session remains.
Do not reuse its ready file. Final full suite and remaining source/diff, gesture,
GUI4 and lifecycle acceptance are still outstanding.

Historical evidence presentation follow-up: buildReleaseChanges recognizes an
unpaired After owned by sfm:review-evidence, keeps its original lane/source IDs,
and presents one Commented snapshot source beneath Historical comment evidence
· not current approval. No invented missing Before or unavailable diff siblings.
Its exact comment children and immutable SourceLeaf are preserved. Added focused
source-node identity/comment retention regression. Test launched with filter
SFMReviewExplorerModelTests and log diff-first-historical-label-tests-20260909.ndjson;
result pending at this checkpoint. No approval migration or identity changes.
Focused runner40277 reports22passed/0failed/0skipped/0aborted after full Java
compilation. This verifies source-node retention and existing model regressions;
fresh rendered historical-lane check and a full projection fixture for retained
evidence remain necessary. No fresh GUI acceptance claimed for this edit.
Added complete validated observation fixture with an embedded historical Rust
snapshot: projection has one retained source child, exact text/revision identity,
and unchanged current review units. Initial full-suite61424 failed compilation
because test versionLabel expected String, not Optional; corrected that fixture.
Full Java rerun25932 terminal exit0:2036passed,0failed,0skipped,5opt-in aborted,
2041found,86s. Log diff-first-java-final-pass2-20260909.ndjson. The five opt-in
integration/performance tests remain exclusions, not passes. All three protected
original review SHA256s rechecked unchanged. No game left running; test reaped.

GUI4 historical/pointer run44627,Java28552,control session-835e2866-fdfb-4805-8f66-
7cd1bef930de completed requests1..26. Early command sequence ran before review
open finished: panel/open was not executed, subsequent filter keys remained in
palette. Recovered by reissuing panel/open after execute-active verification;
do not count early dispatch as successful navigation. Steps13/14 visually prove
Historical comment evidence label with single Commented snapshot child, no
unavailable diffs. Step16 opens retained Rust with exact `use` approval marker,
syntax colors and GUI4 toolbar/read-only badge separated without glyph clipping.
Steps17/18 toolbar changed Z/M to S/R. Step20 invoked named save_defaults; disk
runGameTestPreview/config/sfm-client-program-editor.toml changed both canvas
booleans from true to false. Step22 developer/open_text_editor selected legacy
editor (not a valid v3 assertion). Step24 opened a NEW current After v3 source;
step25 visibly S/R, proving saved defaults apply to new editor in same JVM.
Need fresh-JVM S/R check then restore disposable profile true/true through actions.
Normal finish26 publishes gallery runs/sfm-title_screen-20260909-122836-803.
Original reviews remain untouched; only disposable profile preferences changed.
Datagen57170 completed exit0,47.2s,2warnings/0errors; log
diff-first-datagen-final-20260909.ndjson. HashCache reports132 files,0written,
0stale removed. All8 pointer localization keys verified in generated en_us.json.
Read-through of full-suite tests confirms split Unicode/CRLF/missing-side mapping
and syntax-prefix/header clipping are exercised, while refined mappings in the
split test are synthetic (not proof of real structural matching). Runtime round
trip across actual inline/split surfaces remains required. GUI4 run44627 was
reaped exit0. Disposable profile still S/R for upcoming cross-JVM test; restore
true/true afterward. No manual testing or approval is needed to continue.
Fresh GUI2 run95586 live,Java15076(start12:31:25),control
session-1ef0b4e6-f31f-4c7f-ad22-9ac4cd946137. Step2 explicit command
sfm action invoke sfm:panel/open sfm:text_editor sfm:text_editor_v3
visibly starts S/R in this fresh JVM, proving persisted defaults. Steps3/4 toolbar
restore Z/M; step6 save_defaults; file rechecked true/true for both canvas booleans.
No user profile affected (runGameTestPreview only). Step8 dispatched opening
disposable diff-first-gui2.sfm-review.json for within-observation source/diff
roundtrip. Next request9; first observe until open pending clears before trying
panel/open. Keep live handle95586; do not launch another client merely because a
ready file or observation is old. Earlier session ended; this one is intentionally
still active for remaining acceptance.
Same GUI2 session9..35 completed source/diff roundtrip. Step17 actual Rust inline
diff: neutral headers and green added code with Rust syntax spans. CtrlHome,
Down3,Right,CtrlShiftRight selected `use` after the rendered '+'; AltEnter step24
proposed exact source[0,3), not generated bytes. Approval saved step28/29 into ONLY
diff-first-gui2 ledger: generation3,dirtyfalse,onecomment. Persisted literal target
review-document:sha256:a860e9bdff8f437f33cb303f6dd8415e297f498b45e686be7820eddec058fa46,
documentSHA5494e5312a400469be068ff348707dcd167b1458415d19eb5cfb8a47651f24b9,
selectedSHAa3b142af6e97cfc3bb23e409ab83467af7d16ded7dc0632be6a6a9023e49ce8b.
Step30 opened retained After row immediately after save;31 shows exact marker on
`use`. Step32 opened retained text split row;33 shows syntax plus addition spans;
right-click510,52 at34 offers same human:release-review:1 text/reveal/selector,
matches(1) resolved exactly. No duplicate comment and no row refresh/reexpand
needed, verifying post-save leaf fix in actual UI. Whole-file-added Rust case
does not establish removed/unchanged structural backgrounds or two-sided layout.
Normal finish35,run95586 reaped exit0; gallery
runs/sfm-title_screen-20260909-124154-734/index.html. No live game from this run.

### Bounded acceptance closeout

The subsequent GUI4 fixture run did NOT execute tests: discovery rejected
1920x1080@4 for GUI_SCALE_MATRIX. Console records failed=1,total=0 despite the
JVM exiting0. This is a launch-selection error, not a product acceptance pass.
The process handle20001 is terminal/missing. The profile explicitly accepts
3840x2130; corrected run uses3840x2130@4, handle53249, log
diff-first-review-ux-supported-gui4-20260909.ndjson. Re-poll that exact handle
before launching another game. It compiled current Java and spawned Minecraft.

Close remaining evidence gaps in this order; do not add unrelated polish while
these remain open:

1. Two-sided Java diff runtime: inspect real syntax plus removed/added background
   evidence, unchanged-token neutrality, comment save/reopen. Existing fixture
   puppet is complementary to, not a replacement for, repository-scale evidence.
2. Actual-scale opening: cold/warm phase timings and responsive cancellation;
   report first-usable rows separately from command dispatch and total resolution.
3. Remaining runtime pointer combinations and representative language routes
   at ordinary/review/diff boundaries, using existing virtual input only.
4. Audit exact source/diff mapping edge-case tests and legacy coverage parity;
   fill genuine missing assertions rather than repeating already-proven suites.
5. Consolidate acceptance matrix, guide, protected-file hashes, final tool and
   process readiness. Any source changes invalidate affected earlier evidence.

Repository engine audit saved to build/diff-first-syntax-audit-20260909.json
(under platform/minecraft). It samples three meaningful files per enabled
extension; it does not by itself prove Java worker delivery or rendered glyphs.
Unsupported inventory must remain visible: VSIX/JAR and PNG/ICO are binary
containers/images; ANTLR interp/tokens are generated text; .sfml uses the native
language path rather than Arborium, while .sfm currently resolves neutral and
must not be counted as supported merely because its content is SFML. G4, XML,
BAT, RC, YAML, MJS, extensionless
files and backup .orig need explicit route classification. Cargo.lock, .mcmeta
and app.manifest have recognizable content families but extension aliases must
not be counted as supported without verifying actual dispatch. No additional
dependency authority is implied by this inventory.

Supported viewport run53249 compiled/launched JVM23800 and reaped exit1 after
one real puppet failure: WAIT_BEFORE expected a preview owned by explorer-2.
Production openDocument intentionally uses previewOwner(reviewPath), a stable
review-file owner shared across lenses. The puppet's previews()/previewHits()
still used the old individual Explorer ID, so could not see opened previews.
Updated both lookup sites to the production review-file owner; retained all
counts, focus, dedup and content assertions. No production behavior changed.
Rerun handle61769, log diff-first-review-ux-owner-gui4-20260909.ndjson, at the
supported3840x2130@4. Must inspect its result; test patch alone is not acceptance.

Run61769 reaped exit1 after progressing through Before/After dedup, reveal, pane
controls and both inline diff types. Actual screenshot release-review-text-diff
shows Java syntax, red removed value=1 line, green added value=2 line, neutral
package/class/header lines. Artifact release-review-diff-presentation reports
53 text-diff styled glyphs,30 structured-diff styled glyphs,2 text-diff backgrounds,
os_pointer_injection=false. This does not yet visually establish token-level
structured neutrality. It then failed at new-comment selection because the
fixture already contains comments on that range: the current menu offers their
details plus sfm:context/actions/open. Updated puppet to follow that explicit
mouse-only affordance once before demanding the new-comment choice; no retries
that hide absent commands and no production change. Run41059 now rebuilding,
log diff-first-review-ux-context-gui4-20260909.ndjson, same supported viewport.

Run41059 reaped exit1: pointer comment creation, exact source binding, atomic
save and pinned #approved query assertions passed. It then waited for a workspace
while the nested selection menu had returned to its parent palette. Added bounded
mouse Cancel navigation (at most3 parent menus, only after a known saved comment)
before lens traversal. Also added real structured-surface acceptance/capture:
Cafe fixture must map only removed "1" and added "2", not shared method syntax.
Run52012 compiled and launched with log
diff-first-review-ux-structural-gui4-20260909.ndjson. Its outcome is pending.
Guide now reflects verified historical GUI4 rendering and GUI2 source/diff
roundtrip rather than retaining obsolete pending claims. No product code,
dependency declarations or user reviews changed in these test follow-ups.

GUI4 run52012 reaped exit0, puppet failed=0,total=1 (1m50s including build/startup).
Durable gallery: runs/sfm-title_screen-20260909-130014-964/index.html under
platform/minecraft/build/sfm-toolchain/artifacts/game-test-preview. Structured
artifact reports algorithm arborium-java/2.18.1+sfm-declaration-correspondence/2,
removed=["1"],added=["2"],30 remote-styled glyphs. Rendered structured screenshot
inspected: shared method syntax neutral, changed digits colored, existing comment
underlines independent. Panel is narrow before later divider resize; this is not
a claim of ideal default layout. Mouse comment loop, all six lens transitions,
atomic save/pinned approval query and reopen assertions passed. No OS input.
The one launcher error count is not a test failure; inspect actual console for
mod startup versus SFM errors rather than treating the report count as complete.
Run55812 now compiling same source at3840x2130@2; log
diff-first-review-ux-structural-gui2-20260909.ndjson. No need to repeat GUI4 unless
affected production/test behavior changes. Remaining original acceptance gaps
are still tracked above; this is not whole-goal completion.

GUI2 run55812 reaped exit0 (1m37s); failed=0,total=1. Same structural1->2
assertion, syntax, mouse comment save/query/reopen and six lenses passed.
Gallery runs/sfm-title_screen-20260909-130229-679/index.html. Console inspected:
no SFM ERROR; startup errors are okzoomer/epp minVersion metadata, AE2 guide
startupPage and Industrial Foregoing missing texture. These are reported, not
silently counted as goal failures or fixed outside scope. Both games exited
normally; no live test handle remains. Whitespace check passed for this turn's
puppet/plan/guide edits. Next acceptance work is actual-scale responsive opening/
cancellation, remaining pointer and language runtime cases, then final audit.

### Actual-scale opening/cancellation and language continuation

Exploratory run6807 remains LIVE, Java17788(start13:04:12),1920x1080@2.
Control session-4dc49fc9-4f84-4fab-bb41-31043f4c1a71 under
runGameTestPreview/sfm-puppet/exploration-control. Requests1..24 complete;
next25. Revalidate process and reuse this session rather than rebuilding.
Log diff-first-opening-acceptance-20260909.ndjson. No user review modified.

Steps1..6 held persistence worker, submitted real repo-scale open, used the
named cancel action and released worker. GUI frames advanced1290->4321;
open dispatch32.184ms,cancel6.121ms,each rendered response~0.5s including capture.
Pending changed PREPARING->CANCELLATION_REQUESTED; release cleared pending,
path remained none,epoch/generation1 unchanged. Worker logged not_published,
CancellationException,426us actual work (30.9s deliberate queue hold excluded
from performance claims). This proves queued cancellation and UI responsiveness,
not interruption midway through a live companion subprocess.

Steps7..10 first unpaused open of existing disposable full-repository GUI2
ledger: operation2 published in8.065812s,queue64us;one saved comment retained.
This is fresh-JVM with existing OS/tool caches, not a cold-cache benchmark.
Step9 Home dispatch2.920ms but its captured observation was already completed;
do not claim this establishes mid-resolution keystroke latency. Step12 Changes
panel dispatch201ms,first capture shows one placeholder root,NOT usable data.
Step14 warm open/view operation3 published7.679505s,queue51us. Step15 sees five
real roots in both existing/new Explorers; request14 to response15 bounded by
11s wall time including agent delay/capture,not exact first-usable-row latency.
Step16 rendered populated hierarchy and match-current-source banner inspected.
Java companion reader currently lacks separate process-output/decode timing;
add narrow instrumentation before claiming full phase breakdown.

Steps17..24 complete-domain exact Gradle filter:one match,two ancestors,six
children. After build.gradle opened and step22 visibly shows Groovy keyword,
string,call and comment styles. Text-inline diff opens at23;24 inspected with
Groovy styles plus an independent green added-line background and neutral context
and headers (this displayed hunk has no removed line, so is not red-side evidence).
Current view is Gradle inline diff in rightmost panel (x720,width240 logical),
two Explorer panels remain. This is repo-scale review/inline evidence,not yet
ordinary-file or all-language coverage. Keep current client for remaining checks.

### Deferred diff pointer-action regression

Exploration steps25..30 exposed a genuine failure: the wheel button submitted
`sfm action invoke sfm:document/pointer/wheel`, but Brigadier rejected the action.
The editor remained in zoom mode. The hosted entry is SFMDeferredTextEditorPanel;
the action requirement previously accepted only directly hosted SFMTextEditorPanel.
It now resolves the loaded delegate using the same resolver as spatial coverage,
while unresolved/closed delegates remain unavailable and host currency is checked.
The review UX puppet now clicks both toolbar buttons in text and structured diffs,
asserts each setting changed, then restores settings through those same buttons.
This exercises command dispatch from the deferred host rather than only pure settings.

Exploration finished normally via request31; Java17788 exited and run6807 reaped
exit0. Gallery runs/sfm-title_screen-20260909-131836-734 retains all31 observations.
The first regression launch66143 failed on sandbox cache access (os_error5), not
Java compilation; it exited before the normal-cache retry58376. Retry is compiling
current production and puppet changes for3840x2130@2. Do not count this as passed
until its completion is inspected. Original review files were not changed.

Retry58376 completed the GUI2 review journey with the new toolbar assertions;
JVM exit0, timed_out=false, cancelled=false. Durable gallery:
runs/sfm-title_screen-20260909-132145-632/index.html. Both inline text and
structured deferred previews now pass wheel and button-mapping dispatch and
restoration before continuing through structural precision and comment persistence.
All three protected original-review hashes still match the recorded baselines.
Scoped diff whitespace checks pass; the existing generated language-cache file
has trailing whitespace and was not edited to hide that unrelated check output.

### Java resolution phase evidence

The companion resolver now emits monotonic microsecond timings correlated by its
owned subprocess PID: companion_and_ipc (process start through drained output),
json_decode, validation, evidence_index. Output is measured in Java characters,
explicitly not bytes; no second giant UTF-8 allocation is made just for telemetry.
These measurements run on the persistence worker and do not claim that IPC and
Rust computation are independently timed. Rust's own phase events remain needed
for that breakdown. Full Java suite62055 is running against this change and the
deferred-pointer fix. Runtime actual-scale measurements are still pending.

Full Java62055 reaped exit0:2036passed,0failed,0skipped,5opt-in-aborted,
2041found (2m06s build+test). GUI4 regression98340 is now rebuilding the same
production state for3840x2130@4; log diff-first-deferred-pointer-gui4-20260909.ndjson.
Keep/poll that live handle; no reason to start a duplicate run. Actual-scale
phase measurements and remaining language runtime coverage are still outstanding.

GUI4 regression98340 reaped exit0 (1m52s), completing the deferred-pointer
toolbar assertions and full review/comment journey. Gallery:
runs/sfm-title_screen-20260909-132717-400/index.html. Together with GUI2 this
closes the discovered deferred-host dispatch regression at both GUI scales.

### Actual-scale Java phase capture, current source

Live exploratory run9437, Java16048(start13:28:09),1920x1080@2; control
session-30f7745e-5534-41c2-b573-59ad67eb702b. Steps1..6 complete, next7.
Do not relaunch while this process is alive. Log diff-first-phase-exploration-20260909.ndjson.
Opened disposable full-repository diff-first-gui2.sfm-review.json twice; the
open/view action is writable (request2's informal read-only note was inaccurate),
but neither open changed its single saved comment. No mutation commands issued.

First open total11.477551s: companion_and_ipc9.996860s,json_decode0.792807s,
validation0.135813s,evidence_index0.084766s. Warm open total7.937312s:
companion_and_ipc6.914781s,json_decode0.591907s,validation0.057205s,
evidence_index0.053632s. Both returned30,990,020Java characters and2082sources.
Fresh-JVM is not cold-disk evidence. Entry dispatch28.638ms on first open;
responses3/6 show five usable root entries, generations2/3,one saved comment.
Most measured wait is before Java decode, not in Java validation/index creation.
Root availability observations include agent/capture delay, so they do not supply
an exact first-row timestamp. Preserve this distinction in the final handoff.

Same live run steps7..13: exact filter docs/AGENTS.md returned one match with
one ancestor; expanding exposed all six source/diff children. Opened After and
text-inline diff. Screenshot11 visibly shows yellow Markdown heading spans with
neutral body text (block grammar only; no claim of inline injection). Screenshot13
captures the mapped inline-diff rendering for inspection. Current live state is
Markdown inline diff, two Explorers plus stacked After/diff editors; next request14.

Steps14..22 checked ordinary-file routing. Initial sfm:path/open was rejected
because a virtual review root is not filesystem authority (expected boundary).
Opened sfm:explorer explicitly at the repo root, then retried the same Markdown
path successfully. Palette remained open after the action; Escape dismissed it.
Screenshot22 and narration confirm ordinary AGENTS.md(read-only), not the Review
preview, with real yellow heading styles and neutral body. Screenshot13 also
visibly confirms both red removals/green additions and yellow Markdown headings
in the inline diff. No comment/source write was performed. Current session9437,
Java16048 is still live, next23; three Explorers, ordinary Markdown editor focused.

Steps23..40 completed ordinary-file GUI2 language samples through sfm:path/open
with the explicit repo root, inspecting each rendered screenshot:
-25 JSON docs/architecture/evidence/clean-loader-jarjar-probe-1.19.2.full.evidence.json:
 strings green, number orange, punctuation neutral.
-28 Rust platform/cli/sfm-propagate-changes/build.rs: keywords purple, types cyan,
 functions yellow, strings green, comments gray.
-31 TypeScript platform/visual-studio-code/super-factory-manager-language/src/activitybar/ActivityBar.ts:
 imports/keywords/types/functions/comments distinctly styled.
-34 Groovy platform/minecraft/build.gradle: keywords, calls, strings and comments styled.
-37 PowerShell platform/cli/act/act.ps1: loop/conditional keywords, commands,
 strings, type and comments styled.
-40 TOML platform/cli/sfm-propagate-changes/Cargo.toml: table/key cyan, strings
 green, booleans orange; this inspection does not authorize dependency edits.
These are nontrivial actual-source samples, not empty grammar-registration checks.
This closes their ordinary GUI2 rendering checks only; do not generalize to all
languages/views/scales. Live9437/Java16048 remains, next41, ordinary Cargo.toml
focused, three Explorers and seven ordinary-file previews stacked with existing
review previews. No source/review write or OS pointer injection performed.

## Current acceptance ledger (2026-09-09, after exploration40)

This summarizes—not replaces—the ten requirement atoms above. Passed unit tests
are from full Java62055; runtime evidence refers to the recorded galleries.

| Atom | Evidence established | Remaining acceptance work |
| --- | --- | --- |
|1 Empty initial comments |1548-byte v3 target; ledger projection tests; generated-marker removal before materialization |Final source/fixture cross-check |
|2 Separate coverage/comments |GeneratedMarkersTests checks provenance, human hashtag preservation and unchanged historical session; Rust materialization preserves corpus/units |Inspect explicit remaining-work denominator parity assertions |
|3 Live target/immutable comments |GUI2/4 one-comment capture and historical reopened evidence; original review hashes unchanged |Final persistence/stale-source evidence cross-check |
|4 Diff-first |Actual Java structural1->2 changed-token assertion GUI2/4; Rust/Gradle/Markdown review diff captures |Consolidated guide and limitations |
|5 Shared identity |GUI2 inline->source->split same-comment roundtrip; SplitLayoutTests explicitly asserts Unicode/CRLF, separate sides and no invented missing-side ranges |Complete audit of disjoint and changed-draft assertions |
|6 Wheel toggle |GUI2/4 live settings/default persistence; deferred toolbar failure fixed and regression passes both scales |Check original runtime evidence for zoom anchoring and both scroll axes |
|7 Pointer roles |Both role settings independently toggle; initial live pointer gestures and deferred GUI2/4 dispatch pass |Check all four combinations and left-selection/panel arbitration evidence |
|8 Language routing |All eight enabled grammars; ordinary GUI2 real samples; source/diff Java/Rust/Gradle/Markdown runtime; pending/failure tests |Review remaining representative boundaries; document unsupported cases |
|9 Coverage audit |Repo-root ranked inventory, meaningful engine samples and actual rendered ordinary files recorded |Consolidate scope without claiming all files or an untested cross-product |
|10 Opening |Same-scale before/after phase evidence; current full-repo11.48s/7.94s; Java breakdown; queued cancel/UI frames; actual usable roots |Exact first-row/index timing and deeper companion phase attribution remain incomplete |

Operational gates: Java62055 passed; Rust713+integration suites and datagen have
earlier source-valid checkpoints recorded above (no later Rust/datagen-input
change). Latest Java compile and GUI4 passed. Final installed-tool hash/readiness,
guide and owned-process cleanup are still required. Live9437/Java16048 remains
available; next virtual request41. Do not mark the goal complete from this table.

### Coverage invariant checkpoint and exploration termination

Added removingNeutralGeneratedMarkerPreservesChangedAndRemainingCoverage to
SFMReleaseReviewGeneratedMarkersTests. A nonempty canonical fixture with/without
a neutral generated marker must have identical changed-domain count, remaining
count, effective approvals, exact #approved query surface coverage, corpus and
review units. Focused run11568 reaped exit0:2passed,0failed,0aborted (50s).
This verifies annotation removal cannot erase the denominator; it does not
replace the Rust materializer tests. No production code changed in this step.

SelectionAdapter's canonical fixture test was inspected: ordered multi-document
Unicode/backward/primary ranges are compared with frozen witnesses; stale
selection revisions, unavailable original identities and changed hashes throw;
neutral adaptation leaves the existing approval query unchanged. LedgerProjection
tests explicitly reject changed-source capture without mutating the empty ledger,
and two comments on one source retain one content/document evidence record.

Exploration9437/Java16048 is now TERMINAL: launcher900-second timeout, exit1,
not a successful puppet run. Last completed request40 and its screenshots
were captured before timeout. Request41 finish was submitted after termination
and did not execute. Reaped9437; no reuse of its control directory. Preserved
40screenshots,40responses/requests,ready state and console in
platform/minecraft/build/diff-first-phase-evidence-20260909/ (124files including
README and unexecuted41 request). This prevents later capture names overwriting
the inspected evidence. The earlier1-hour assumption concerned the puppet, not
the external launcher's900-second cap; future exploratory batches must finish
before that cap. Runtime source unchanged during the completed observations.

### Rust materializer coverage parity checkpoint

Extended `materializes_complete_real_git_domain_canonically_and_without_mutation`
to compare the legacy materializer with `materialize_observation` on the same
nonempty Git domain. It asserts no observation comments/styles, identical corpus,
revision lanes and review units, and identical query results (including surface
coverage) for `1.19.2 HEAD`, remaining work, and effective approvals.
Focused locked/offline Cargo test session12368 passed:1 passed,0 failed,
39 filtered out; build1m47s,test3.56s. The existing test creates only disposable
temporary Git fixtures; it does not commit in the project worktree. A subsequent
edit only wrapped one assertion for formatting. No runtime/dependency changes.
This closes the explicit Git-materializer parity assertion gap, not the separate
working-tree-materializer coverage gate or remaining pointer/performance checks.

### Working-tree coverage and numerical pointer checks

Working-tree capture regression now compares legacy/observation materialization
of identical captured bytes after the disk file has changed again. Corpus,
revision lanes, units, changed-domain queries, remaining work and effective
approval results remain identical; observation comments/styles are empty.
Focused session83743:6 passed. Required offline check-all.ps1 session83839:
format,clippy,build passed;713 unit tests passed,3 ignored; integration suites
10/12/40 passed;exit0. Only test code changed in Rust, not runtime behavior.

Extended the existing Explorer UX puppet with numerical canvas checks for all
four wheel/button settings: anchored wheel zoom in X/Y, vertical scroll without
zoom or horizontal displacement, and mapped pan-button translation in both axes.
Gestures restore the camera and settings; no OS pointer injection. GUI2 session52908
passed and exited normally,2m12s; gallery
runs/sfm-title_screen-20260909-140007-473/index.html. These assertions exercise
canvas gestures in the hosted diff editor; they do not independently establish
Shift-wheel horizontal dispatch or multiplexer Alt+middle arbitration.

GUI4 session74653 also passed the same numerical pointer assertions and full
review journey,exit0,1m50s,normal game shutdown. Gallery:
runs/sfm-title_screen-20260909-140230-202/index.html. Both new test games are
terminal; Rust validation session83839 is terminal. No production Java edits
were made in this checkpoint and no dependency changes were introduced.

### First-render instrumentation (runtime measurement pending)

SFMExplorerPanel now emits SFM_EXPLORER_FIRST_ENTRIES_RENDERED once, after
rendering at least one non-loading viewport entry. Fields identify explorer,
location, relation revision, visible entry count and panel_to_frame_micros.
This measures construction-to-first-render, not command-to-first-render; the
preceding review-operation timestamps must be correlated separately. It does
not claim an empty result is populated or that all descendants are indexed.
No IO or repeated logging is added to ordinary frames after the first event.
All Java source sets compiled successfully in session61428,exit0,53s,
75 existing test Unsafe warnings,0 errors. New production Java tracing means
runtime and full-test checkpoints must be refreshed; previous GUI passes do not
prove this new event. Next capture should include a disposable repository-scale
open and the new event, then finish the game within the900-second launcher cap.

### First-render live diagnostic and correction

Full Java session30156 passed2037 tests,0 failed,5 opt-in aborted,exit0.
Exploratory session32386/Java25824 then opened disposable diff-first-gui2
at1920x1080@2. Four requests completed,including explicit finish; normal exit0,
gallery runs/sfm-title_screen-20260909-141250-977/index.html. The saved comment
count remained1 and dirty=false; five entries were present in observation3.
Open total11.400703s,companion+IPC10.039483s,decode0.778612s,
validation0.104376s,evidence index0.069347s,2082sources,31,013,014chars.

The new first-render event exposed a measurement flaw: relation_revision=0,
visible_entries=1,panel_to_frame_micros=142340 was only the initial root shell,
not the populated review. This value is NOT first-usable-results evidence.
Corrected the one-shot guard to require relationRevision>0 as well as real
non-loading entries. This final two-line runtime correction is not yet compiled
or rerun; it supersedes the preceding Java/runtime checkpoint for that guard.
Warm rerun was intentionally deferred until the timing guard is corrected.

### Published-relation cold/warm diagnostic

Session17782/Java32964,seven virtual requests,normal finish and exit0; gallery
runs/sfm-title_screen-20260909-141851-087/index.html. First/fresh-JVM open
8.076381s (companion6.599399,decode0.836356,validation0.111296,index0.068412).
Warm open7.807018s (companion6.714793,decode0.651885,validation0.058757,
index0.054090). Both2082sources,31,014,347chars. Neither is a cold-disk benchmark.
Observations3/6 show five entries and unchanged saved review state.

First render had revision1,five entries,538787microseconds from panel creation.
Warm render had revision1,one entry,17755microseconds: publication revision alone
still permits a root-only shell. Exclude that warm value from usable-content
evidence. Tightened the event's count to non-loading AND non-root rows, matching
the actual content criterion. A standalone file root intentionally cannot satisfy
this contents measurement. This final guard change needs compile/runtime checking;
do not generalize the prior revision-only runtime pass to it. No game remains
from this diagnostic; original review files were not opened or changed.

### First-content predicate regression and GUI checkpoint

Extracted the exact render predicate into SFMExplorerPanelViewport.publishedContentCount.
New viewport test exhausts root/non-root and ENTRY/LOADING combinations at
unpublished/published revisions, plus empty and offscreen contents. All9viewport
tests passed in session39462,exit0,59.4s. Runtime uses this tested predicate.
GUI2 session16751 passed full Explorer UX/comment/pointer journey,exit0,1m46s;
gallery runs/sfm-title_screen-20260909-142327-786/index.html. Actual events:
file Explorer14contents at243066microseconds; review Explorer revision2,one
content at165086microseconds; reopened review revision16,one at37446microseconds.
These are the small fixture's content frames, not repository-scale timings.
No root-only event qualifies. Game exited normally. Repository-scale corrected
first-content timing still needs recapture; do not substitute fixture latency.

### Virtual Shift-wheel routing fix

Found canvas scroll mode polling physical Shift state despite explicit virtual
pointer modifiers. It now prefers SFMPointerInputModifiers.current(), including
an explicit zero mask, and only falls back to physical polling when absent.
Extended the four-mode GUI matrix to assert Shift-wheel changes X by36/zoom,
leaves Y/zoom unchanged, and reverses cleanly for both pan-button assignments.
GUI2 session22569 passed,exit0,2m03s,gallery142727-152; GUI4 session18913 passed,
gallery143003-713. Both galleries use runs/sfm-title_screen-20260909- prefix.
The virtual wheel helper now has an explicit modifier overload (default0), and
exploratory scroll requests accept modifiers. These helper changes compiled in
the GUI4 run; the numerical assertion exercises the same scoped modifier carrier
directly at the canvas. Full callback-path exploration is not claimed by that
assertion. Changelog updated. No OS input injection or dependency edits.

### Panel Alt arbitration and shared modifier precedence

Multiplexer content panel-move dispatch also polled physical Alt directly.
Both it and canvas Shift-wheel now use SFMPointerInputModifiers.isDown, whose
explicit mask takes precedence even when zero. Physical fallback executes only
outside a scoped callback. New test covers Shift/Alt/combined/zero masks and
throws if explicit input polls physical keys; existing nested/failure restoration
test retained. Session73202:2passed,exit0. Panel move interaction suite55469:
8passed,exit0,including ordinary canvas pan ownership, modified panel movement,
entry click actions, drag thresholds and one-shot release dispatch. All source
sets compile. Changelog records the observable virtual Alt behavior. This is
unit/component evidence; final raw-callback GUI verification remains required.

### Raw wheel callback GUI2 proof

Replaced the GUI matrix's direct canvas Shift-wheel call with virtual pointer
positioning at the focused panel and SFMGamePuppetPointer.scrollVirtual with
explicit Shift. Input now passes through Minecraft onScroll, workspace routing,
the deferred editor and canvas. Both pan assignments retain the exact horizontal
delta,unchanged Y/zoom,and inverse-scroll assertions. Session97347 passed full
review journey,exit0,2m06s,normal shutdown; gallery
runs/sfm-title_screen-20260909-143643-336/index.html. No production edit in this
checkpoint. This closes the raw wheel callback gap at GUI2; panel Alt raw-callback
verification and corrected repository-scale content-frame capture remain distinct.

### Raw Alt+middle and wheel GUI4 proof

Extended the existing GUI journey with raw Minecraft clickVirtual Alt+middle
under both middle-pans and middle-actions settings. It requires the exact panel
action palette (pane-close command present),unchanged canvas camera,and dismissal
back to the identical workspace. Original editor settings are restored afterward.
Session45147 passed GUI4 including these checks,raw Shift-wheel,all four pointer
settings,and review/comment persistence. Gallery:
runs/sfm-title_screen-20260909-143945-310/index.html. This closes raw panel-action
arbitration at GUI4 and raw wheel evidence across GUI2/4. Only gametest code
changed since the prior production modifier fix. No OS pointer injection.

### Repository-scale first-content evidence, corrected predicate

Session91286/Java21392 completed7requests and explicit finish,normal exit0,5m10s.
Gallery runs/sfm-title_screen-20260909-144554-558/index.html. Same disposable
full-repo review,2082sources,31,026,314Java chars,one saved comment,dirty=false.
First/fresh-JVM open:total9.421589s,companion+IPC8.005104s,decode0.827972s,
validation0.122150s,evidence-index0.068390s. First non-root content frame:
revision1,five entries,0.622518s after panel construction.
Warm open:total7.778338s,companion+IPC6.760004s,decode0.585283s,
validation0.061269s,evidence-index0.056648s. First non-root content frame:
revision2,five entries,0.417505s after panel construction. Both observations
confirm five browsable contents; no synthetic root was counted. No cold-disk or
exact end-to-end sum is claimed: panel creation and operation timing have separate
origins. No source/runtime edits occurred during this run.

Remaining acceptance is now consolidated: final full-Java result after modifier
changes; final installed-tool/protected-file/process readiness; guide/ledger
reconciliation; and inspection of phase coverage against atom10 (companion
capture/materialization and serialization attribution versus measured combined IPC).
Prior pointer/raw-callback and generated-comment denominator gaps are closed by
the tests above; do not reopen them without contradictory evidence.

## Final acceptance and manual handoff — 2026-09-09

The last continuation completed a real gate: session41166 returned exit0 with
2039 Java tests passed, zero failed, five opt-in aborted (2044 found),1m27s.
The NDJSON is platform/minecraft/build/diff-first-final-acceptance-java-20260909.ndjson.
No implementation changes followed this suite; only guide/acceptance documentation.

| Requirement | Completion evidence |
| --- | --- |
|1 Empty initial comments | Reported v3 ledger remains1548bytes; observation materializers emit zero generated comments; first-comment capture tests and disposable GUI ledgers verify sparse persistence. |
|2 Coverage separate from comments | Java provenance tests and both real-Git and working-tree Rust parity tests compare corpus, units and exact changed/remaining/approval queries. Legacy evidence and human hashtag text are preserved. |
|3 Live target, immutable comments | Resolver and ledger tests cover untracked/disk authority, exact captured bytes, deduplication and rejected stale capture; fresh-JVM GUI journeys preserve historical evidence without transferring approval. |
|4 Diff-first presentation | Source/inline/split journeys and Java structural token-change checks preserve unchanged syntax while coloring changed spans. Non-Java structural fallback is explicit. |
|5 Shared comment identity | GUI2 Rust inline-to-source-to-split journey retains one comment; GUI4 historical reopening and selection adapter/split tests cover Unicode, CRLF, missing sides, disjoint ranges and stale evidence. |
|6 Wheel control | Localized action-backed per-editor toolbar/defaults; numerical zoom-anchor and scroll assertions across all four settings; raw Shift-wheel callbacks pass GUI2/4. |
|7 Pointer roles | Both role assignments and left selection retained; raw Alt+middle panel actions pass GUI4 without camera movement, plus panel gesture ownership tests. |
|8 Language routing | Actual source identity survives virtual review paths and deferred editors; eight pinned language routes have meaningful spans and ordinary runtime samples, with Java/Rust review/diff evidence and explicit unavailable/fallback states. |
|9 Coverage audit | Repository-root inventory and diff-first-syntax-audit-20260909.json rank count/bytes and meaningful samples; unsupported cases remain disclosed rather than counted as highlighted. GUI2/4 evidence recorded above. |
|10 Opening | Same-profile capture/materialization/validation measurements isolate removed marker cost; final repository-scale fresh-JVM/warm run91286 measures companion+IPC, Java decode/validation/index and actual non-root frames. Lazy diff/worker cancellation tests and GUI loading evidence establish responsive work. |

Phase boundaries are intentionally reported honestly: working_tree_capture groups
enumeration/Git/disk acquisition; materialization groups the generated model and
changed-surface computation; observation_validation includes canonical serialization
and parse. Full diff previews remain lazy rather than being generated at open.
Java companion_and_ipc includes Rust work and output transfer, not an independently
measured transport-only cost. These measured groups establish the bottleneck and
before/after result; no per-system-call or allocation profiler precision is claimed.
Latest full-repo total9.42s/7.78s is still noticeable, not an instant-open promise.

Operational gates: Rust check-all83839 passed format/clippy/build,713 unit tests
(3ignored),10+12+40 integration tests. Datagen57170 passed,132files checked,
zero writes/stale removals; no localization input changes since. GUI2/4 final
journeys97347/45147 passed; repository journey91286 finished normally. Earlier
timeout9437 is not counted as passing. Changelog and walkthrough are current.

Source HEAD remains16328629fa60c45a4f525b6f20aaa77715f91077 with preserved dirty
work. Installed CLI smoke reports0.1.1 rev16328629f built2026-09-09 11:09:54-04.
Path G:/Programming/Caches/CARGO_HOME/bin/sfm-propagate-changes.exe; SHA256
5E784475A0F80153C2719132F3BDCA6A282A2EACFA341B5FE11717CF1BA4FBB4.
The earlier locked/offline installation follows the last production Rust edit;
later Rust changes are test-only. User must run install.ps1: no.
Final Win32_Process inspection found no java/javaw/SFM CLI/cargo/rustc processes.
No process was killed during closeout; latest launched games exited normally.
All three protected review hashes match their initial recorded values, including
the1548byte reported review. No Gradle, project commits, push or propagation.
Dependency exception remains only the explicitly approved exact-pinned Arborium
grammars and required locked closure; other dependency authority remains frozen.

Manual start from the repo root:
`sfm-propagate-changes.exe run client --branch 1.19.2 --wait-for-build-lock`.
Follow docs/live review capture-on-comment guide.md: Explorer writable review,
source and inline/split diffs, select/comment/read/reopen, then Z/S and M/R controls.
User testing is now usability feedback, not an outstanding completion gate.
