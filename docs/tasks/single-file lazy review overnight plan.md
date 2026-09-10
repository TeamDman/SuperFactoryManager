# Single-file capture-on-comment review overnight iteration

**Status:** Verified complete, 2026-09-08. Core C1–4 and stretch items 1–6 passed. Root: `D:/Repos/Minecraft/SFM/repos2/1.19.2`.

Follow-up user testing and proposed next revision are tracked in
[diff-first review usability discussion](review%20diff-first%20usability%20revision%2020260909.md).
That draft is planning-only: it records real-scale opening latency, removal of
generated-comment presentation, cross-view source anchors, syntax coverage and
pointer preferences; prior test completion is not a claim those issues are fixed.
**Authority:** User approved the proposal and ordered stretch goals. This plan
inherits [goal execution and testing readiness guidelines](goal%20execution%20and%20testing%20readiness%20guidelines.md).
Ten-plus hours is available capacity, not a deadline or evidence of completion.
`[ ]` pending, `[~]` current, `[x]` verified. Claim one item at a time; finish and
checkpoint the core before entering the stretch ladder.

## Intent and invariants

- A review remains ONE committable file, not a manifest plus required sidecar.
- Begin with before/after target descriptions and policy. Git targets resolve
  immutable commit/blob identities; non-Git browsing is explicitly live.
- Persist non-Git source content only when a user-authored comment targets it.
  Generated change markers, browsing, diffing and indexes must not trigger this.
- Save exact displayed/selected evidence and comment atomically, deduplicated
  by content hash. Never reread changed disk bytes and call them the selection.
  Start with whole-document evidence, preserving line endings and context.
- Comments stay pinned after later edits. Live working-tree coverage is tied to
  an observed source state, not a mutable path/offset alias. Old approvals never
  silently approve new bytes. Missing Git objects fail explicitly, not to disk.
- Keep old formats readable and original reviews unchanged. Explicit conversion
  must preserve historical evidence; do not reinterpret an old frozen capture as
  today's live disk. A new live review is a distinct operation/identity.
- Review storage excludes itself from its source domain; no recursive capture.
- Structural declaration matching is not enough: unchanged `@Override` and
  unchanged fragments must stay neutral. Change backgrounds are separate from
  syntax, selection and comment styles. Ambiguity remains conservative and
  documented; visual refinement must not weaken approval/completion policy.
- Age text updates per second below 10 seconds, then floors to 10-second buckets.
  Exact timestamps stay available; status transitions are not delayed.

## Required core

### [x] C1 Contract, compatibility and quiet freshness checkpoint

Inspect Rust/Java serialization, materialization, resolver and persistence paths;
define a versioned sparse storage contract and compatibility boundaries. Record
size attribution and current-source preflight. Implement/test the bounded age
change as the initial independently verifiable UI checkpoint.
Acceptance: serialized examples/contracts distinguish live/Git/embedded evidence;
age boundary tests include 9/10/19/20 seconds, clock rollback and absent check.

### [x] C2 Single-file lazy storage and capture-on-comment vertical slice

Implement creation/opening, lazy source resolution, exact evidence capture,
deduplication and transactional save through existing CLI/action/UI mechanisms.
Retain compact target metadata and comment/resume state. Do not serialize derived
diff/index data merely to make an in-memory model easy to save. Preserve legacy
read paths and explicit safe conversion to a new file. Prove no-content initial
state, browsing without growth, first comment embeds exact evidence, second
comment on identical content reuses it, changed content creates a new identity.
Test interrupted/failed save leaves previous file valid and draft recoverable;
delete/rename/unavailable source is explicit. Atomic replacement uses existing
platform helpers; do not weaken collision/concurrent-save checks.

### [x] C3 Precise structural change backgrounds

Refine matched declarations into unchanged and changed source spans using existing
pinned parser/diff dependencies. Preserve exact UTF-8 source mapping and inline/
split alignment. Cover annotations, signature/body edits, moved declarations,
repeated tokens, comments/whitespace, Unicode and parse recovery. Display fallbacks
honestly. Use unchanged `@Override` as an explicit visual regression, with a real
changed method containing common prefix/suffix rather than addition-only proof.

### [x] C4 Core integration and usable handoff checkpoint

On disposable real-source reviews: create, browse, comment, close JVM, reopen,
query approved/remaining surfaces, mutate source and recheck conservatively.
Compare size/open/save costs with baseline without pretending noisy timings are
strict percentage gates. Capture virtual-input GUI2/GUI4 evidence; no OS cursor
injection. Run full Java tests, datagen, Rust check-all and final installer after
last relevant tool edit. Record final hashes/process state, changelog and guide.
Completion includes mouse/keyboard affordances, not merely an available CLI.

## Ordered elastic continuation (items 1–6 verified; no claimed unfinished item)

Each item starts only after C1–4 and its predecessors are verified; each has a
separate reversible patch/checkpoint. Unstarted items remain optional. A claimed
item is an obligation, not silently reclassified as deferred at handoff.

1. [x] **Evidence/storage inspector:** separate contextual actions explaining
   Git/live/embedded identity, capture time, storage reason and byte breakdown.
   Read-only, no content reads merely to draw an icon; test exact selected target.
2. [x] **Richer invalidation/remaining-work UI:** approve, externally edit, refresh
   and see new work while retaining old evidence. Test insertion before selection,
   deletion, unrelated edits and query parity. Core safety already belongs to C2/4.
3. [x] **Explicit migration:** preview exact/relocated/ambiguous/missing matches;
   record accepted relocation provenance. Never infer approval on ambiguous bytes.
   No automatic rewriting of historical targets or maintainer evidence.
4. [x] **Single-file portability/compaction:** dry-run size report and new-file
   portable export, embed needed Git evidence when requested; recognize captured
   content later committed as a Git blob without changing identity. Verified
   compaction may remove redundant embeddings only with explicit policy and
   availability guarantees. Test offline/missing objects and query equivalence.
5. [x] **Incremental performance and successor reuse:** avoid full Explorer/index
   reconstruction after one comment; avoid redundant snapshot serialization;
   reuse equal content in successor captures without transferring approvals.
   Keep canonical single-file durability; no required AppData or sidecar store.
6. [x] **Adversarial exploration/navigation polish:** natural disposable workflows
   involving edit-during-preview, close-during-save, rename/delete, interruption;
   add regressions for discoveries and improve existing remaining-work/comment
   navigation affordances. No unrelated graph/window-manager or large-file editor.

## Operational readiness / dependency pinning

- Dependency posture **FROZEN**: no edits to Cargo.toml, Cargo.lock, build.gradle,
  sfm-toolchain.lock.json or other dependency declarations. No new dependency,
  unpinned substitute, local-artifact bypass, or developer/reference clone.
- Deterministic rehydration from EXISTING pinned lockfile identities is allowed;
  record hashes/identity and outcome if needed. A cache miss is not permission to
  upgrade a dependency. No Gradle; use the canonical Rust toolchain commands.
- Work only on 1.19.2. No commits, pushes or propagation. Preserve unrelated dirty
  files; initial status contains 389 changed/untracked paths.
- HEAD `16328629fa60c45a4f525b6f20aaa77715f91077`; initial dependency diff empty.
- User review SHA `52E8EE6E700B0090C7D8612C93D84393ED21FC39801F9047D59C9F3CEB42D306`;
  manual-test SHA `9ADDCCEC595CAA916EFDB8019270F62D577B336508D2608D84BDA2FE7AADD592`.
- Installed CLI initial SHA `63583BCFD8EFA93CE916DB449E9507F16D26FE56F55C16DF1F0ED17A0F5D8C1E`.
  Rust edits require check-all.ps1 and install.ps1, then path/version/hash smoke
  after last relevant edit. User installer responsibility must be explicit.
- Bounded lifecycle authority applies only to proven in-scope game/tool processes;
  preflight before builds, log progress with --log-filter info --log-file, graceful
  shutdown first. No broad process kills or destructive filesystem operations.
- Final process state/cache ownership, current-source tests and exact manual
  launch command must be recorded before completion. Artifacts use a dedicated
  `platform/minecraft/build/lazy-review-20260908` directory.

## Initial evidence and audit

The September 7 review is 31,219,051 bytes. Compact JSON attribution: revision
lanes 23,447,370 bytes (Before 1,786,416; After 21,660,853), comments 1,910,125;
other metadata contributes the remainder. These are reserialized field sizes,
not exact offsets in the pretty-printed original. Java structural producer emits
whole declaration bodies as StructuralBefore/After when hashes differ, explaining
unchanged annotation colouring. Freshness currently derives seconds directly.

Intent audit: single-file user correction supersedes the earlier sidecar proposal.
Capture-on-comment differs from lazy capture of an allegedly immutable working
tree: unreviewed content is live, commented content is pinned. Portability,
compaction and richer migration are ordered stretches, not substitutes for core
snapshot integrity. No plan item grants dependency or publication authority.

### C1 checkpoint

Freshness age implementation and boundary regression passed 6/6 tests in
`build/lazy-review-20260908/freshness-tests-1.ndjson` (current-source compile).
Exact timestamp/details unchanged; missing check stays timeless, rollback clamps
to zero. Changelog updated; final GUI evidence still belongs to C4.
Preflight found no in-scope game/tool process before launching tests.
The [v3 implementation contract](../architecture/release-review-lazy-single-file-v3.md)
records target kinds, single-file evidence closure, historical/live coverage and
the actual Java store/runtime and Rust load/materializer integration seams.
Current codecs reject unknown fields; introduce explicit version dispatch rather
than permissively mixing sparse and fully resolved models. C2 is now the focus.

### C2 foundation in progress

`SFMReviewEvidenceTable` separates immutable observed bytes from retained content.
Capture accepts the existing selector proposal and exact literal witness, traverses
the selector closure, validates hashes/UTF-8 boundaries and deduplicates content
while retaining independent document identities. Git evidence holds commit/blob
references and does not embed text. No filesystem reread occurs during capture.
Tests cover absent/changed evidence, equal bytes across paths, Git reference
pinning, repeat captures, surrogate rejection and invalid byte boundaries.
`evidence-tests-1.ndjson` passed **4 tests, zero failures/aborts** with current-source
compilation (owned launcher 20389). This is not wired to
creation/store yet and does not establish smaller review files. Next: versioned
ledger codec and format-aware store/CLI resolution, retaining original review
format on save and retaining historical evidence alongside current live units.

The Java v3 ledger/strict codec now round-trips a target-only authority below
2,500 bytes in its focused test. `ledger-tests-1.ndjson`: **4 passed, 0 failed**.
The explicit observation-to-ledger projection rejects another observation's
bytes, retains only user comments/bindings and captures new/changed bindings;
unchanged saves do not require another source read. Derived marker identities
come from the original observation, not edited text/provenance. The second
current-source run `projection-tests-2.ndjson`: **3 passed, 0 failed**, launcher
70610 exited 0. First projection run also passed 3/3. Evidence reference checking
now uses a set rather than a content-by-document quadratic scan.

Rust `release_review_ledger` has the matching strict sparse wire contract, using
the existing Facet dependency and the existing tagged-selector validator.
`cargo test --locked --offline --lib release_review_ledger::tests -- --nocapture`
passed **2 tests**, exit 0 (launcher 6343). The repository-required
`check-all.ps1` completed successfully (launcher 2313, exit 0): format, Clippy,
build, **702 unit tests passed / 3 ignored**, and integration groups **10 + 12 +
40 passed**. The Java-analysis integration group took 129.64 seconds and exited
normally; no test/helper lifecycle intervention was needed. Rust source now differs from the installed
CLI; final install/hash evidence is required after integration, not claimed yet.

These are storage foundations, NOT an in-game v3 creation/open/save claim.
Next integration sequence: resolve ledger targets via the existing bounded Rust
Git/working-tree materializers; merge durable comments and historical evidence
without adding historical surfaces to current completion; expose resolved output
to Java; make the Java store format-aware and atomically save the projection;
wire creation/actions and explicit live-observation refresh. Preserve old frozen
reviews as frozen. Existing user files remain untouched, dependency-file diff
remains empty. C2 stays in progress; no stretch has been claimed.

### C2 resolver/store integration checkpoint (still in progress)

- Added explicit transient `sfm.release-review-observation/3`, separate from
  durable `sfm.release-review/3`. Java and Rust accept narrowly identified
  historical evidence lanes without pretending they are current Git/captured
  repository bindings. Current completion units cannot reference those lanes;
  legacy formats still reject them. Java `observation-tests-1.ndjson`: 1 passed,
  launcher 26842 exited 0.
- Rust `release_review_ledger_resolve` uses existing pinned Git/working-tree
  materializers, self-excludes the output, scopes producer IDs per lane, records
  Git blob identities from exact source bytes, hydrates embedded history and
  reports missing historical Git blobs. Its disposable live/edit/delete/reopen
  regression passed (launcher 79382, 1 test). Initial compile caught an optional
  version-label type mismatch, fixed before that passing run.
- Added read-only CLI `review session resolve --file ... [--request-file ...]`.
  The optional input is the exact opened ledger bytes; `--file` remains the root
  context/self-exclusion identity. The response carries its input SHA-256.
  General CLI review reads dispatch sparse ledgers through resolution.
- Java `SFMReleaseReviewLedgerResolver` invokes that endpoint with an owned
  temporary request, bounded streams/deadline and input-hash verification. Store
  loading is format-aware; ledger saves atomically persist the sparse projection,
  advance accepted ledger state only after replacement, and preserve old formats.
  `ledger-store-tests-1.ndjson`: 1 passed (launcher 44472, exit 0), including
  injected before-replacement failure, unchanged previous authority, exact
  content capture, repeat-save deduplication, conflict detection and reopen.
  This test injects the resolver; real companion/UI round-trip is still required.
- Rust check-all caught two lint issues (nested item placement and dropped error
  context); both fixed without suppressions. Retry launcher 20259 is running at
  this checkpoint; format/Clippy/build passed, tests pending. The installed CLI
  is still the prior build. No final installer or GUI evidence claimed.

Next: add target-only CLI creation and switch in-game creation to that explicit
route while retaining legacy capture creation for compatibility; install the CLI
and exercise the real resolver boundary. Add live-observation refresh/status UI,
verify historical comment navigation, and test current-source full workflow. Git
targets currently explicitly reject custom scope/exclusion policies rather than
silently ignoring them. Multi-repository targets currently require the review
path inside each authorized root; revisit or expose that limit explicitly before
claiming broader multi-repository support. Historical Git reference path/commit
membership validation and bounded/deduplicated evidence validation remain audit
items before C2 completion.

Creation is now wired: `review session create-ledger` resolves Git commit targets
without materializing a source corpus, writes a unique small authority, and
refuses replacement. Existing `create` retains frozen legacy captures. In-game
creation now chooses `create-ledger`. Rust creation regression passed in check-all
launcher 71334 (704 unit tests passed, 3 ignored; 10+12+40 integration tests
passed, exit 0). It proved <2,500-byte initial storage despite ~96KB of new source,
no review-file growth on resolve, no Git index mutation, and self-exclusion.
Java creation-command tests passed 3/3 (launcher 66141).

Further current-source changes after that checkpoint: `freshness-of` accepts
the exact opened observation's binding metadata instead of rereading the ledger
as if it were a frozen capture; the Java freshness worker supplies that bounded
request. Added `review/freshness/refresh_observation` via the existing queued-open
flow, guarded to transient ledger observations. Store loads now have explicit
stage/accept phases, so cancelling a same-store refresh cannot advance the save
projection before runtime publication. The store regression now covers abandoned
staging as well. Changelog updated, but final compilation/datagen still required.

Current verification: full Java launcher 84860,
`build/lazy-review-20260908/java-full-ledger-1.ndjson`; Rust check-all launcher
25044. Both running at this note. Earlier 20259 check-all passed 703 unit tests
(3 ignored) plus 10+12+40 integration tests. An attempted UUID use during creation
failed compilation because it is not a dependency; replaced with existing
SHA-256 plus creation identity inputs, with NO declaration/lockfile changes.
One function-length lint was resolved by extracting target-policy validation.
Do not treat earlier passing checkpoints as final proof of the later freshness
and staged-load edits. Next install + actual Java-to-companion round trip, then
natural disposable GUI2/GUI4 evidence and remaining C2 audit before C3.

### Current-source integration continuation

Full Java launcher 84860 exited 0: **1,967 passed, zero failed, three expected
opt-in aborts**. Rust launcher 25044 exited 0 after format/Clippy/build, 704 unit
tests (three ignored) and the 10+12+40 integration groups. Later Java changes
carry resolver diagnostics and clarify live-observation freshness wording; those
still require current-source verification, not the earlier full-run claim.

Intermediate installer 43888 exited 0 using locked/offline dependencies. Installed
`G:/Programming/Caches/CARGO_HOME/bin/sfm-propagate-changes.exe` SHA-256:
`FFD84FA28527461C150B701FD7BB9EC6316719B0AFE4653200B90F1871EAC1BA`,
revision 16328629f, build 2026-09-08 02:51:39 -04:00. Version and resolve-help
smokes passed. This is an integration checkpoint, not the final install gate.

Added an opt-in actual companion/store test (`SFM_TEST_LEDGER_COMPANION=true`):
disposable Git baseline, target-only ledger, browse without growth, edit disk
after opening, save exact displayed evidence, reopen historical and current
source together. Launcher 16527 is running with
`build/lazy-review-20260908/ledger-companion-tests-1.ndjson`. No GUI evidence or
completion claim yet. Dependencies remain frozen per the operational section.

Launcher 16527 exited 0: **2 passed, zero failed/skipped/aborted**, including the
enabled real installed-companion boundary test. Current Java source compiled.
The test confirms save retains the displayed `value = 2` after disk changes to
`value = 3`, and reopening resolves both immutable historical and current bodies.
Dependency declaration/lockfile diff check remained empty. C2 remains active:
remaining Git-evidence audit and virtual-input end-to-end review UI checks are
still required, followed by C3 precise structural spans and C4 final handoff.

Historical Git audit continuation: resolution now verifies `commit:path` names
the recorded blob before reading it, not merely that some object with that hash
exists. Missing/mismatched references produce explicit unavailable evidence;
current identity collisions also compare Git provenance. Regression covers valid
membership, missing path and wrong blob. Rust check-all launcher 48782: format,
Clippy/build and **705 unit tests passed / three ignored**; integration groups
still running. This later Rust edit invalidates the intermediate installed hash
as final-source proof; reinstall remains required after checks.

Java ledger construction now requires the same sparse v1 state envelope as Rust,
rejecting transient observation/working-tree envelopes even when empty. Focused
regression launcher 95703 is running with `ledger-schema-tests-1.ndjson`.
No game has been launched in this continuation. Next re-poll these exact handles,
then install current Rust and proceed with C2 virtual-input exploration.

Java schema regression reported **5 passed, zero failed/skipped/aborted** on
launcher 95703. Rust 48782 remains live in the Java-analysis integration group
(normal >60-second diagnostic); do not restart it based on that duration alone.

Rust 48782 exited 0: integration groups 10/12/40 all passed (Java-analysis group
148.41 seconds). Java 95703 exited 0. Installer 65833 exited 0; current CLI SHA
`EAF267CC63DED7A013F90091295BAB8218FBC39FD60BA1D09DC59F2207671601`, revision
16328629f, build 2026-09-08 03:00:41 -04:00. Version smoke passed.

CLI created disposable `build/lazy-review-20260908/gui-live.sfm-review.json` at
**1,511 bytes**, before pinned HEAD, live scope restricted to
`platform/minecraft/src/main/java/ca/teamdman/sfm/client/review/release_review/SFMReleaseReviewFreshness.java`.
GUI2 exploration launcher **17053** is building/running, log `gui2-1.ndjson`.
Use its new `run/sfm-puppet/exploration-control/session-*/ready.json` identified
by live JVM PID before sending request 000001. Never reuse an old session.
The puppet's requests invoke virtual GUI inputs only; original user reviews are
not test targets. GUI2/GUI4, C2 remaining scope limits and C3/C4 remain active work.

GUI2 is live: JVM **30916**, launcher **17053**, session
`platform/minecraft/runGameTestPreview/sfm-puppet/exploration-control/session-54945dab-709a-41dd-84d3-4077c46461b1`.
Ready reports logical 960x540 and OS pointer injection false. Step 1 opened the
palette with the disposable ledger open command; Execute is enabled. Step 2
submits Enter and is pending observation. Read that response before the next
request; sequence increments monotonically, no batching ahead of observations.
Correct control root is `runGameTestPreview`, not the earlier shorthand `run`.

GUI2 steps 3–27: open published in 714ms, target-only file stayed 1,511 bytes
through browsing. Changes panel, exact filename filter and six before/after/diff
leaves worked; After rendered Java syntax. Ctrl+A / Alt+Enter offered literal
[0..6633), selected explicitly over symbol alternatives. Disposable approval
saved in 14.7ms total worker-operation time: file now **12,718 bytes**, one durable
human comment, one content body, one evidence document; derived marker stayed
transient (two in-memory comments). CLI `review session status --file` returned
changed_domain=1, approved_effective=1, remaining=0, exact [0..6633) coverage.
This is disposable test approval, not a maintainer approval of repository code.

Banner contextual `Refresh live review from current source` completed, epoch
2→3 and generation 3→4, two in-memory comments retained, dirty=false. Ledger SHA
`7B2E2C73381FE6BE3962606C5B59A4654465BC36EB170CD831B7C36BB7519E0C`.
Step 28 requests normal puppet finish; poll launcher 17053 for terminal before
GUI4 launch/reopen. Screenshots currently in `runGameTestPreview/screenshots`
with `title_screen_exploratory_review__explore-step-NNNN__viewport-1920x1080-gui-2-effective-2.png`.
Viewed steps 5/6/7/10/12/14. Copy/retain evidence before another same-variant run.

Usability findings to retain: compact lazy hierarchy initially expands one level
per click until complete-domain materialization; refresh action is scoped to a
review Explorer and unavailable when source editor is focused (banner route works).
Typing an unavailable action then Enter accepted a fuzzy alternate suggestion;
no unintended action was executed, but availability explanation needs attention.
C2 still needs restart/reopen and additional coverage proof; C3 not started.

GUI2 launcher 17053 exited 0 normally (11m45s), preview completion validated.
Durable gallery: `build/sfm-toolchain/artifacts/game-test-preview/runs/sfm-title_screen-20260908-031409-727/index.html`.
All 28 captures retained there. Test runtime reported two warnings/one error;
inspect that error before treating the run as clean. No restart required for a
hung process: this JVM exited normally on the finish request.

GUI2 console error audit found startup messages from other mods (missing Mixin
minVersion, AE2 guide startup page, Industrial Foregoing missing texture), not an
SFM review exception. Keep them in raw evidence; do not call the whole console
error-free. Fresh GUI4 launcher **77832**, JVM **2248**, log `gui4-1.ndjson`, is
loading. No new ready session as of the first check; previous session PID30916
is stale and must not receive more requests.

Draft [capture-on-comment guide](../live%20review%20capture-on-comment%20guide.md)
records the verified command/keyboard route and explicit limitations. Additional
label audit: create-ledger stores a title with the original `--before` spelling,
so `HEAD` remains in that title despite immutable target fields. Replace this
with resolved revision labeling before final handoff; preserve existing user
titles/files rather than rewriting them automatically.

Fresh GUI4 session `3f23957f-2d53-4ce3-88a7-cf6a3ec9eff8`, JVM2248, logical
480x270, OS pointer injection false: steps 1–3 reopened GUI2's saved ledger;
two in-memory comments, writable=true, dirty=false. Steps 4–9 opened Comments,
expanded the human approval (matches=1 resolved exactly), and opened its actual
text value. Viewed screenshots 6/8/9. Split panels are cramped at this scale,
but the persisted comment/selector/value remain accessible. Step10 requested
normal finish; poll launcher77832 terminal and retain its gallery.

Reopen did not change the disposable file SHA
`7B2E2C73381FE6BE3962606C5B59A4654465BC36EB170CD831B7C36BB7519E0C`.
Both original user review SHA checks still match the goal-start values. No
sidecar required for the tested human comment. Remaining C2 work includes title
label correction, parity/closure audit and explicit scope-limit documentation;
then C3 changed-span refinement and final C4 full tests/datagen/install/guide.

GUI4 launcher77832 exited0 normally (5m02s), completion validated. Durable gallery:
`build/sfm-toolchain/artifacts/game-test-preview/runs/sfm-title_screen-20260908-032014-767/index.html`.
No live test handle remains from GUI2/GUI4; both JVMs ended via puppet finish.

C2 final-integrity continuation: create-ledger title now uses resolved before
and after revisions (or explicit `live working tree`) instead of input `HEAD`.
Creation regression checks that title. Rust check-all launcher **2409** has passed
format/Clippy/build and 705 unit tests (three ignored); integrations still live.
Installed CLI EAF267... remains prior to this label edit; reinstall after checks.

Java sparse-ledger validation now requires every durable source comment to have
one matching selector binding and retained evidence for its rule/witness closure.
Embedded ranges validate UTF-8 bounds and selected hashes without disk access;
Git references remain repository-dependent and are checked during resolution.
Duplicate/orphan bindings rejected. Missing-evidence regression added to store
tests. Launcher **17456**, `ledger-closure-tests-1.ndjson`, runs all Ledger-matching
Java tests with real companion opt-in enabled. A subsequent captured-selection
equals proposal-witness guard was added after that compile began, so a rerun may
be needed. Rust sparse-validation parity for this closure guard is still pending;
do not mark C2 complete until parity and tests are done. Then proceed to C3.

Java launcher17456 reported **10 passed, zero failed/skipped/aborted**, including
the real companion test. Last tiny witness-equality edit still requires final
current-source verification. Continue polling Rust2409's same live handle.

Rust2409 exited0, all 10/12/40 integration tests passed. Subsequent Rust closure
validation now matches Java's required committed-source selector binding,
captured witness equality, retained document identities and embedded UTF-8/slice
hash checks. Resolver test now supplies its real binding and verifies missing
evidence is rejected. First check-all69324 caught two style errors (import inside
loop and missing semicolon), fixed without suppressions. Retry **15997** running;
no install yet for these later edits. Need final Java/Rust consistency tests and
reinstall before treating earlier UI evidence as final-current-source evidence.

Supported new-creation scope is explicit: one repository/lane via the UI/CLI,
Git-to-Git whole-repository comparison or Git-to-live scoped comparison. Manually
authored multi-repository ledgers and custom Git-only scope are rejected where
unsupported, not silently broadened. They are not a claimed new capability.
Legacy frozen multi-lane reads remain on their existing path.

Rust15997 exited0: 705 unit tests passed (three ignored), and all 10/12/40
integration tests passed. C2 closure parity checkpoint passed; final Java
witness guard and installed-tool freshness remain C4 obligations.

C3 claimed: structured declaration producer now partitions exact copied source
mappings into neutral and changed UTF-8 spans using the already pinned gix Myers
implementation. Algorithm fingerprint advanced to correspondence/2. Comparison
work and mapping count are bounded; excess uses the existing explicit text
fallback. No dependency declaration/lockfile changes. Tests cover unchanged
`@Override`, non-BMP context, CRLF common islands and the comparison bound.
First compile identified that pinned slider API requires byte-like tokens;
replaced char tokens with exact single-codepoint string slices. Canonical
check-all34710 is running; Clippy passed. Do not claim final validation or install
yet. Changelog updated; C4 datagen/install must follow this last runtime edit.

C3 downstream correction: split layout formerly made one cell per mapping
fragment. It now coalesces contiguous fragments on their original source line,
including CRLF boundaries, and split canvas uses exact decoration spans instead
of painting the whole cell by mapping kind. New Java regression exercises
single-codepoint fragments, Unicode selection and original line count. Java
launcher94475 (`refined-split-tests-1.ndjson`) is running, main compilation passed.
Rust34710 passed 707 unit tests (three ignored); integrations still running.
Dependency declaration/lockfile diff remains empty. C3 is not complete until
these checks and current-runtime visual verification pass; no stretch claimed.

C3 Rust34710 exited0 (707 unit tests, three ignored, all 10/12/40 integrations).
Java94475 exited0, all four split-layout tests passed. Datagen85119 exited0,
132 generated entries, zero writes; existing generated resources already match.
Install29068 built successfully but replacement failed because the concurrently
owned datagen CLI was running; after datagen ended, installer retry exited0.
No locks were deleted and no process was killed.

Current installed CLI SHA256:
`A1145E14FF8668A1652991B91A3FF842794E478625DD03B18B469B29212A0FEF`,
`G:/Programming/Caches/CARGO_HOME/bin/sfm-propagate-changes.exe`, rev16328629f,
build2026-09-08 03:42:08 -04:00. Full Java launcher43487 with real companion
opt-in is running (`core-full-java-2.ndjson`). New disposable visual review
`build/lazy-review-20260908/gui-refined.sfm-review.json` is 1557 bytes, targeting
the modified SFMRevealInExplorerAction.java against pinned current HEAD. C3
visual regression and C4 full-suite/in-game/guide completion still required.

Full Java43487 exited0: 1970 passed, zero failed, three opt-in aborted (installed
symbol worker settings, scale fixture, repository icon audit). Ledger companion
opt-in was enabled. Current virtual GUI2 launcher74927 has JVM26776, parent38744,
runGameTestPreview args in this branch; loading normally. No OS input injection.

Current GUI2 session `b57f571b-37c0-4876-9a2d-bbec138d4d18` steps1–13 opened the
new 1557-byte ledger, filtered the exact filename, opened structured inline and
split diffs. Viewed4/6/7/9/12/13. Real modified execute method shows unchanged
`@Override` and common body neutral, exact red/green changed fragments, retained
Java syntax; split lines remain assembled. Fingerprint `/2` is visible in split
header. Fit-width works but two long columns in half-width panel become tiny;
tracked UX limitation, not hidden evidence of readability. Browsing did not grow
the ledger (still1557bytes). Step14 requests normal finish; poll launcher74927.
Current GUI4 refined-diff verification and C4 final audit remain. No stretch claimed.

GUI2 launcher74927 exited0 with validated completion, 6m54s. Durable gallery:
`build/sfm-toolchain/artifacts/game-test-preview/runs/sfm-title_screen-20260908-035236-545/index.html`.
Original review hashes still match both goal-start values. JVM26776 ended via
normal puppet finish. Launcher summary has one error count; audit console
classification before describing the run as error-free.

## Core checkpoint — 04:03, 2026-09-08

Current GUI4 launcher84919 exited0, JVM12372 stopped normally. Session
`0b721c02-1e83-43c2-92fc-dfae51f9bd01` reopened saved human comment, exposed
exact match and text value (steps4/5/6 viewed), switched to refined review,
filtered and opened split (15/16/18/19 viewed). The unchanged annotation remains
neutral; exact changed spans colored, syntax intact. Both scales show the
documented cramped half-width layout limitation, no source-line fragmentation.
Durable gallery `build/sfm-toolchain/artifacts/game-test-preview/runs/sfm-title_screen-20260908-040237-191/index.html`.
Current CLI status of gui-live reports changed_domain1, approved_effective1,
remaining0; file SHA remains7B2E2C... and gui-refined remains1557bytes. Original
user review hashes remain unchanged. All owned core test processes stopped.
Console error classification is third-party Mixin/AE2/Industrial Foregoing,
not an SFM review exception; entire modpack is not claimed error-free.

Core evidence audited in `lazy-review-core-acceptance-20260908.md`, with scoped
test evidence rather than implying every optional integration ran. Installed
A1145E14... is current after runtime edits/datagen; user install required: no.
No dependency declaration changes, no new repositories, no source commits/pushes.
Manual command and limitations are in `../live review capture-on-comment guide.md`.

Now claim stretch1 only: read-only evidence/storage inspection with separate
context actions, selected-document identity and exact size attribution. No
automatic disk content read for icons, no portability mutation or migration.

Stretch1 foundation: added pure `SFMReviewStorageInspection` over acquired
authority plus an immutable observation. Reports original authority byte/hash,
deduplicated raw/JSON-string source sizes, target identities, and selected exact
revision's Git/embedded/transient status. Does not read source files/Git objects.
Observation capture timestamp is labelled separately; v3 does not retain the
original embedding timestamp, so the report explicitly says unavailable rather
than deriving it from a later observation or file mtime. Legacy frozen storage
is explained without pretending it is a lazy ledger. Tests57824 running; after
compile began, legacy unknown-target rejection was tightened and needs a rerun.
UI/CLI actions, bound asynchronous authority read/identity validation, selected
document context wiring and final tests remain. Stretch1 is NOT complete.

Stretch1 progress, 2026-09-08 04:18: registered explicit
`sfm:review/storage/open` and `sfm:review/storage/copy` actions, with optional
review-file and exact document-revision arguments. Review freshness/lens choices
offer the whole-review report; captured-source context choices offer selected
evidence reports. Authority is read on a dedicated worker, checked against the
opened file hash, and discarded when the originating context/review generation
changes. No automatic source resolution or mutation is performed. Storage test
run 2 passed 27/27; run 3 adds deduplicated Unicode/JSON evidence, Git-reference,
and same-path/different-identity checks. UI exploration and final datagen/full
test checkpoint are still pending; stretch1 remains in progress.

Dependency reminder: OPS-9/OPS-10 frozen posture remains in force throughout
stretches: preserve dependency declarations and locks, no new dependencies or
reference clones, existing-lock deterministic cache rehydration only. Continue
using the canonical Rust build/test CLI; no Gradle and no commits/propagation.

Storage run 3 completed: 28 passed, zero failed/skipped/aborted, including
deduplicated Unicode bodies and exact-identity rejection. This is model/build
evidence only; the new report actions still require the in-game checkpoint.

Inspector GUI2 checkpoint: datagen24936 exited0 (2 warnings, no errors).
Launcher21225 owns JVM15372 (parent31324), virtual session
`1d905867-2c24-4d90-91d7-ce39192206aa`. Opened disposable gui-live; clicked
freshness banner, selected storage report by mouse, submitted Enter (steps5–7).
Viewed7:12718 authority bytes,1 human comment,1 content body,6633 raw bytes,
6842 JSON-string bytes. Viewed11: quoted exact source command renders identity,
SHA, embedded retention and honest timestamp distinction. Step12 requests
normal finish; verify launcher exit and durable gallery next.

Exploration finding for stretch6: unquoted path/identity command at step8 was
not executable at capture, then Enter produced a shorter summary via palette
history completion (step9), rather than the requested selected-source report.
Quoted arguments at step10/11 worked. Context-generated commands already quote
arguments. Do not treat this as source-specific acceptance for unquoted input.
Inspector source-context/copy, full-suite and final evidence remain pending.

GUI2 launcher21225 exited0 after normal finish, 6m39s; durable gallery:
`platform/minecraft/build/sfm-toolchain/artifacts/game-test-preview/runs/sfm-title_screen-20260908-042731-531/index.html`.
The review SHA remains `7B2E2C73381FE6BE3962606C5B59A4654465BC36EB170CD831B7C36BB7519E0C`.
Full Java test session57843 now running with `SFM_TEST_LEDGER_COMPANION=true`,
log `platform/minecraft/build/lazy-review-20260908/storage-full-tests.ndjson`.
No game intentionally left running; inspector stretch remains active.

Inspector acceptance progress: full Java57843 exited0,1974passed/0failed/3opt-in
aborted (symbol-worker configuration, scale fixture, repository icon audit).
Added command-grammar regression for quoted spaces/Unicode paths and exact IDs,
both Open/Copy; session75216 passed1/1. GUI4 launcher94159/JVM34556 parent20108
completed normally,6m13s,session`4886b0ae-88f8-4beb-b0ca-4ed45142faba`.
Viewed6/7/8/11. Selected source with Ctrl+A then Alt+Enter; offers15/16 use exact
local revision `ab05037...`, not semantic dependency destinations. Filtered menu
with `storage`, clicked Copy and submitted Enter. Clipboard report length1756,
schema/revision/6633-byte assertions true. Gallery:
`platform/minecraft/build/sfm-toolchain/artifacts/game-test-preview/runs/sfm-title_screen-20260908-043731-010/index.html`.
Final report-only clarification adds observed side/repository/pinned commit;
focused Storage test session37508 running. This minor text-field edit postdates
the GUI captures; command lifecycle/rendering unchanged, field assertions added.
Checkpoint stretch1 only after this test and unchanged-review/hash verification.

Stretch1 checkpoint: final Storage session37508 exited0,29passed/0failed.
The disposable review hash is unchanged; original review hashes were rechecked
after full tests and match the goal-start values. GUI4 console errors are the
existing third-party Mixin/AE2/Industrial Foregoing startup issues, not storage
exceptions. Both GUI launchers completed normally. Installed CLI A1145E14...
remains unchanged/current (no Rust mutation in stretch1); no user install needed.
Datagen was run after the action/changelog addition; final report-only Java
fields do not change generated resources. No dependency declarations/locks or
user reviews changed. Guide documents the mouse/keyboard flow and timestamp
limitation. Stretch1 complete; no migration or portability behavior implied.

Now claim stretch2 only: explain retained approval evidence versus current
remaining work after source changes. Acceptance covers insertion before the
selection, deletion, unrelated edits, and parity with the canonical query.
No automatic migration or transfer of approval is authorized by this stretch.

Stretch2 initial inspection: `SFMReviewExplorerModel.releaseStatus` currently
exposes current work-unit counts and exact remaining ranges, but no explicit
category explaining retained approvals whose document identities are no longer
in the current completion domain. `SFMReleaseReviewCoverage.evaluate` correctly
intersects by revision-qualified ranges; preserve this authority. Add explanation
and links to historical evidence without path-based approval transfer. A same-path
relationship may explain why evidence is historical, never count as coverage.

Stretch2 implementation checkpoint (not acceptance): added read-only
`SFMReleaseReviewKernel.approvalEvidence` reusing canonical hashtag eligibility,
blockers, revision-qualified required ranges, and historical-lane validation.
Status tree adds an Approval evidence category with comment text, explanation,
target witnesses and exact current coverage links. No saved state changes.
Attribution/archival tests32089 passed2/2. Added historical approval non-transfer
test and extended status-tree assertions; broader Review test running next.
Still required: live insertion/deletion/unrelated-edit scenarios, query parity,
in-game status discovery, datagen and final checkpoint. Also keep original target
witnesses versus evaluated/relocated ranges explicitly distinguished in labels;
do not let the explanation imply migration acceptance.

Stretch2 evidence: Review suite12636 passed328,zero failed,3 opt-in aborted
(scale and installed-companion cases not enabled for that run). Live matrix56521
ran the companion and failed the new assumption that changing unrelated B.java
preserves A.java's revision. Inspection of `release_review_working_tree.rs`
shows document_id hashes repository/lane/source snapshot ID/side/path/content;
manifest.id includes the capture. This matches the architecture's source-revision
identity contract. Do not silently change addressing or migrate approvals here.
The corrected acceptance explicitly tests restoring the identical full snapshot
(original coverage returns), then adding B.java (same bytes, new identity,
historical approval retained with an explanation). Insertions, field removal,
same-file unrelated edits and file deletion remain historical, with query parity
and unchanged ledger bytes checked. Added indexed same-path/content comparison
for explanation only, requiring COMPLETE materialization on both sides; it does
not enter the coverage calculation. Matrix82629 running with companion opt-in.
Target links now distinguish evaluator targets from unresolved literal witnesses.
Still need passing matrix, UI/datagen/current tests and guide before checkpoint.

Live matrix82629 exited0 (2 tests, companion enabled). It exercises changed value,
leading insertion, field removal, another same-file edit, exact snapshot restore,
unrelated B.java addition and deletion, checking unchanged ledger bytes and
canonical remaining/coverage parity. This validates the snapshot-qualified
contract, not automatic content-based approval transfer. New disposable GUI
fixture `docs/architecture/fixtures/review-live-invalidation/Example.java` starts
with return2; new gui-invalidation.sfm-review.json is1520bytes and scopes only
that fixture directory. Datagen79246 running. Next: approve in game, change only
this goal-owned fixture to return3, refresh and inspect new status category.

Continuation checkpoint: datagen79246 passed. GUI launcher45868 remains active,
JVM43836 (parent15316), virtual session d6d57c3b-2a90-4859-b43c-c4f5b5b239b5.
Requests1-8 open the disposable invalidation ledger, open Changes, filter
Example.java, expand and open its after source. No human approval submitted yet.
Screenshots6/7 inspected: exact fixture, before missing, after and all four
explicit inline/split diff leaves present; freshness shows live source matches.
Continue with Ctrl+A, Alt+Enter, exact-byte approval, then edit ONLY disposable
fixture return2 to return3 and inspect refresh/status evidence. Do not restart
the running client or reuse older session directories. Dependency posture stays
frozen as declared by the goal and operational readiness guide.
Additional safety check pending: unresolved original approval witness ranges may
be outside the retained text; ensure status tree renders diagnostic evidence
rather than constructing an invalid SourceLeaf or silently clamping its range.

GUI continuation: requests9-14 selected all138 bytes and submitted exact-byte
#approved through Tab/Enter. Save completed in15609us total per operation log;
ledger grew1520->5895 bytes, SHA37E72C9F3EB5C4FC074458343425B80E96838CCC4D0D5A23E97980F957EA88B9.
Only disposable fixture changed return2->return3 afterward. Launcher45868 then
exited1 at its configured900-second deadline; console timed_out=true, not an
application crash. Requests15/16 were not dispatched (no responses). JVM43836
confirmed absent. New launcher20447 starts current-source GUI2 to reopen the
same saved ledger and inspect historical approval in Status; no requests sent
to its new session yet. Do not send more requests to d6d57c3b session.
Added guarded original-target presentation (diagnostic leaf, no clamping) and
test with end_byte99999. Focused suite75473 passed3/3,0aborted. Broader final
tests and live status walkthrough still pending. Protected user review hashes
rechecked unchanged; dependency-declaration diff remains empty.

Reopen GUI2 launcher20447 completed normally (7m24s); session11fbb439-cadf-4149-b9fa-31d7f1af1c03,
JVM43132. Gallery sfm-title_screen-20260908-052108-020. Figures5-9 visually verified
historical approval1/currentapproved0/remaining1, readable explanation, and exact
original return2 evidence after disk became return3. Then fixture changed to4;
freshness-menu refresh completed611317us, generation3/openEpoch3, saved ledger
hash unchanged37E72C...EA88B9. Figure16 preserves original source panel after
refresh. Note requests11/12 tried a top-level command inside a constrained choice
palette (not executable); selecting its offered choice in14/15 worked normally.
No SFM runtime failure; console errors are third-party Mixin minVersion, AE2
guide page, IndustrialForegoing texture and TheOneProbe config-change message.
The source target row was too verbose with its synthetic lane hash. Shortened
it to Target source / filename / range, retaining exact source identity in the
leaf; added a regression test. This label edit is after GUI evidence, requiring
current-source verification. Final Review suite27402 running with companion
opt-in; next current GUI4 check, final guide/checkpoint before marking stretch2.

Review suite27402 passed331,0failed,2opt-inaborted (scale fixture and repository
icon coverage not enabled; live ledger companion matrix enabled and passed).
InstalledCLI SHA A1145E14FF8668A1652991B91A3FF842794E478625DD03B18B469B29212A0FEF
reconfirmed; no new Rust edits. Completed GUI2 JVM43132 confirmed absent.
Final-label GUI4 launcher42686 started, log approval-gui4-final.ndjson; no requests
yet. Complete this current-source visual check before stretch2 checkpoint.

Stretch2 checkpoint: final-label GUI4 launcher42686 exited0 normally4m20s;
session78b7318c-db7e-440a-81c3-45217d1c07e7, JVM11720. Gallery
sfm-title_screen-20260908-052757-045, figures5/7/8 visually inspected. Compact
target filename/range shown and original return2 opens with highlighted approval.
Ledger hash remains37E72C...EA88B9. Third-party console messages only (Mixin,
AE2guide, IndustrialForegoing missing texture); no SFM failure. Core GUI4 narrow
split headers still cramped, tracked under stretch6 rather than claimed fixed.
Guide updated. Current Review suite331passed plus live matrix and GUI2/4 evidence
establish this item; datagen79246 already included its changelog entry. Subsequent
edits were Java presentation/tests, not generator inputs or Rust. No install
needed for this stretch; installed CLI A1145E...A0FEF unchanged. No source mutation
after final GUI4 build. Item3 is next, not yet implemented by this checkpoint.

Item3 claimed after checkpoint2. Initial source inspection: existing
SFMReleaseReviewMigrationPipeline is a pure legacy evaluator bridge over
selectorBindings, requires distinct BEFORE/AFTER sides, and scopes candidate
lanes from the original witness. V3 historical retained AFTER evidence resides
in synthetic historical lanes, while current live source is also AFTER. Do not
pretend beforeToAfter covers this transition or broaden historical lanes into
approval eligibility. Inspect ledger projection/store and existing migration
decision persistence next, then implement an explicit version-qualified preview
and acceptance lifecycle with source evidence preserved and stale decisions
rejected. No migration source implementation changed yet. No test JVM remains
running (11720 confirmed absent); no pending tool sessions at this checkpoint.

Stretch3 progress: inspected ledger/projection/runtime. Existing decideMigration
rewrites original target and lacks the v3 successor-binding contract; use a separate
explicit path rather than changing legacy semantics. New pure
SFMReviewMigrationPreview provides bounded exhaustive literal matches with exact,
relocated, ambiguous, missing and incomplete states; UTF-8/CRLF boundaries, overlapping
matches and no implicit preference for same-offset duplicates. Seven focused tests
added; suite94671 currently running (migration-preview-tests.ndjson). No migration
UI/acceptance wired yet. Architecture direction now records scope/lease and append-only
successor/provenance requirements. Continue with explicit observation adapter,
multi-range aggregation and atomic successor creation through existing capture/store.

Focused matcher suite94671 exited0:7passed,0failed,0aborted (54.2s compile+test).
This proves only matcher behavior, not end-to-end migration. No running test/game
session at this checkpoint. Migration remains the claimed incomplete stretch3.

Stretch3 adapter/acceptance: SFMReviewMigrationPlan scopes only explicit current
repository lane AFTER documents, verifies source bytes against corpus, shares a
comparison budget across captured ranges, and rejects missing-domain uniqueness.
Plan tests plus existing migration tests61824 passed12/12. New pure
SFMReviewMigrationSuccessor recomputes shown evidence, rejects stale/nonunique plans,
appends linked human successor with decision/projection provenance and exact new
literal binding, preserves original comments/bindings. Suite28905 passed14/14.
Runtime captureMigrationContext + acceptMigrationSuccessorAsync now route through
existing generation/openEpoch and atomic store save; store preserves resolver
diagnostics for preview completeness. No UI wiring yet. Extended real-companion
ledger test approves unique relocation, checks no preview growth, captures second
body at save, rejects stale repeat, and reopens linked successor with current coverage.
First compile9310 failed on checked exception in pending guard; corrected to an
IllegalStateException (capture does no I/O). Rerun73495 active in
migration-runtime-tests.ndjson. Need outcome before claiming runtime integration.
Next UI integration reference: SFMReviewStorageAction worker/continuation pattern,
SFMReviewActions registration. Need preview display and inspectable old/new targets,
explicit acceptance choice warning about copied approval text, context affordance,
changelog/datagen, durable failure tests, GUI2/4 and user guide.

Runtime/ledger suite73495 passed2/2,companion opt-in enabled,0aborted (1m00s).
Extended integration proves preview leaves bytes unchanged, acceptance saves a
second evidence body and linked successor, original comment/binding retained,
stale same-preview acceptance rejected without writes, reopened successor earns
current coverage while original remains historical. This is not UI acceptance:
no actions/context menu yet. No test session remains running. Relevant context
provider is client/context/SFMReleaseReviewContextActionProvider.java (not the
release_review directory). Full Review suite/datagen/GUI remain after UI edits.

Stretch3 UI implementation checkpoint: SFMReviewMigrationAction now registers
review/comment/successor/{preview,report,accept}. Explicit comment and destination
lane arguments have suggestions; acceptance requires a decision note. One background
preview worker and an eight-entry transient registry bound results to the runtime
lease; stale publication is rejected. Preview offers an evidence text document and,
only for unique complete results in writable reviews, explicit acceptance warning
that the whole comment INCLUDING approval text is copied. Context choices are on
comment details/highlight menus and exact comment objects in the Comments Explorer.
Legacy reviews do not offer the new v3-only choices. Source matching scope is labelled
current observed AFTER corpus, not all repository files or a fresh disk read.

UI source compile + prior matcher tests27925 passed14/14. Added multidocument Unicode
primary/direction preservation and shared comparison-budget tests. Review suite14910
passed344,failed1,aborted2: action-surface test caught legacy choices unexpectedly
growing from6to8. Added the saved-v3-observation availability guard (also avoids
offering acceptance in read-only previews), plus required-lane/decision-note parsing
tests. Rerun65349 running with real companion enabled in migration-review-tests-final.ndjson.
Changelog now includes the successor feature; datagen has NOT run since this edit.
No Rust/dependency declaration/lockfile edits in this stretch. Frozen dependency
posture and operational guidelines continue to apply. Need passing rerun, failure
integrity checks, GUI2/4 preview/accept/reopen evidence, and guide before checkpoint3.

Rerun65349 passed346,failed0,aborted2 (opt-in scale/icon analysis),1m16s.
This includes explicit lane/note command grammar, multidocument Unicode successor,
shared budget and real-companion save/reopen/stale acceptance checks. Afterwards,
successor acceptance was connected to existing operation feedback and shared
review Explorer refresh; refresh failure cannot relabel a durable save as failed.
Resource generation49597 is running (migration-datagen.ndjson), also compiling
that final feedback adjustment. Next: await it, then disposable virtual GUI2/4.
Do not claim the new migration UI validated based on these unit results alone.

Datagen49597 exited0 in1m10s; final feedback/refresh source compiled. No test or
datagen tool session remains active. Both protected review SHA256 hashes rechecked
unchanged; git diff for Cargo.toml/Cargo.lock/sfm-toolchain.lock.json/build.gradle
is empty. Next continuation starts disposable virtual migration UI acceptance at
GUI2 then GUI4 (no game launched yet in this checkpoint). Existing disposable
gui-invalidation ledger retains original return2 evidence; fixture currentlyreturn4
can exercise MISSING first, then explicitly change only that goal-owned fixture
to original return2 with a leading insertion for unique relocation. Preserve
user reviews. Stretch3 remains active, not complete.

GUI2 migration walkthrough43864 finished normally0,8m26s,JVM15512. Session
aa6b45bc-35fb-41a5-a749-e62f5cc448c0, gallery sfm-title_screen-20260908-060805-255.
Steps1–4open saved disposable gui-invalidation and Comments. Steps5–6unquoted
human ID rejected;7–8quoted command works. Figure9shows MISSING with inspection
only;10–11open report. Ledger still5895bytes/hash37E72...unchanged. Goal-owned
fixture changedreturn4 toreturn2 with leading new comment. Steps12–20discover
refresh requires Explorer focus: Ctrl1 did not switch this tab, mouse tab1 did;
then explicit refresh. Refresh also reset Comments lens to Changes (polish issue).
Steps21–22preview RELOCATED with explicit approval-copy warning. Mouse23selects
acceptance,24Execute persists,25observes comment_count3/dirtyfalse. Ledgernow10929
bytes/hashB364F0AFDDE09FC383649ABEF11BBE272D45E97624F7F959B097FA8B67C7A00D,
two retained bodies; human:release-review:2 has parent human:release-review:1.
Step26finish. Only third-party Mixin/AE2/texture/TheOneProbe log errors; noSFMerror.
Missingreport screenshot15 exposed long raw range record offscreen. Report now
prints byte range, path, revision, hash on separate labelled lines. This last
presentation change still needs compile/GUI4. Need reopened saved successor,
mouse context affordance, final GUI2/4 report readability and guide before3done.

GUI4 launcher4551 normalexit0,6m16s,JVM27936,session0876b164-f532-477c-bba1-558552760ae6.
Gallery sfm-title_screen-20260908-061550-197. Figures5–6show reopened original plus
linked successor and right-click preview action;7select/8missed old Executeposition
because menu resized/9actualExecute;10–11report showsoriginal[0..138),proposed[63..201).
12–14status:2approvalcomments,1rawapproved,0complete,1remaining. Correct: inserted
63-byteprefix remains unreviewed.15finish. LedgerhashB364...unchanged across reopen
and preview. Only same third-party log errors, noSFMerror. Report field-layout
change compiled in this GUI4 launch. User guide now describes complete workflow,
scope/quoting/acceptance semantics and remaining-prefix explanation with evidence.
Extended real-companion test injects external review edit before acceptance,
asserts no write/runtime publication, restores exact disposablebytes, then retries.
Full Java suite37905 running (migration-full-tests.ndjson); awaitbefore3checkpoint.
No game remains running through launched handles. Check JVMabsence at finalgate.

### Stretch3 verified checkpoint / stretch4 claimed

Full Java37905 exited0 in1m34s:1994passed,0failed,3opt-inaborted (installedsymbol,
reviewscale,iconcoverage). Both ledger store tests SUCCESSFUL, including new
external-edit conflict/no runtime publication/retry checks. JVM15512and27936
confirmedabsent byCIM. InstalledCLIhashA1145E14FF8668A1652991B91A3FF842794E478625DD03B18B469B29212A0FEF
unchanged; Java-only migration work does not require reinstall. Protectedreviews
hashes52E8... and9ADD... unchanged. Changelog/datagen49597 current (later change only
report Java formatting); finalGUI4 and fullJava compiled it. No running handles.
Core and stretches1–3 verified; stretch4 now claimed for next implementation.
Start with current v3codec/evidence closure and available pinnedGit resolver.
Preserve the single-file contract, exact identities and original reviews. Export
must be a NEW file with explicit Git embedding policy, dry-run byte accounting,
offline missing-object tests and query equivalence. Do not silently discard
nonGit comment evidence or treat Gitblob discovery as authority to rewrite targets.
Incrementalperformance and navigation issues above remain unclaimed5/6.

Stretch4 foundation: inspected v3ledger/evidence and Rust current-first resolver.
Architecture now records evidence portability versus current-domain availability.
New pure SFMReviewEvidenceExport prepares EMBED_COMMENT_EVIDENCE or explicitly
repository-dependent VERIFIED_GIT_REFERENCES. Preserves targets/state/document
identities; checks supplied exact Git witness and blobSHA1, UTF8/SHA256, deduplicates
hashes, retains shared nonGit bodies, reports exact input/output/embedded bytes and
dependency count. Five tests60847passed5/5 in53.9s. This is NOT a user-facing export.
New SFMReviewEvidenceExportFile writes/forces temporary content then atomically
creates a hard link at a NEW destination (no-overwrite, including races); rechecks
sourcehash beforepublication, refuses source-as-output and existing paths. Unsupported
filesystems failclosed; postpublication tempcleanup cannot falsely report unsaved.
Two disposable publisher tests added; suite7390running export-file-tests.ndjson.

Remaining integration findings: existing resolver hydrates current targets before
embedded history, so missing baselineGit can block offline evidence access. Need
truthful evidence-only/offline UI rather than inventing empty completed domain.
Ordinary observation is NOT necessarily a fresh Git verification: embedded history
skips Git reads. Compaction must use explicit verified commit/path/blob acquisition.
Git target resolver currently rejects excluded_paths entries other than the current
review's path; preserving original exclusion in a new export can therefore break
reopening. Address actual self-exclusion semantics and query equivalence, not just
JSON roundtrip. Recognized later-committed blobs must not rewrite target identity.
Need CLI/action/context flow, realGit/offline tests, new-file reopen, dry-run evidence,
changelog/datagen/installedCLI ifRustchanges and GUI before4complete. No dependencies,
lockfiles orRustsource changed yet; no game running. Coreand1–3 remainverified.

Publisher suite7390 exited0:7passed,0failed,0aborted,54.8s. Includes new-file output,
nooverwrite, unchangedsource, staleauthority refusal, portablemissingGit and shared
hash retention. No live tool sessions remain. Next implementation should inspect
release_review_ledger_resolve.rs resolve_target and CLI review_cli.rs resolve_ledger_at:
the latter derives roots viaGit beside the review, preventing outside-repository
offline access. GitReviewDomain has changes,units,reconciliation, so filtering an
excluded review path must preserve all three rather than droppingonlychanges.
The preparation class is not wired to user actions and cannot yet verifyGit itself;
do not advertise portable export as finished from this checkpoint.

### Stretch4 offline evidence and exact Git verification checkpoint

SFMReviewOfflineEvidence projects embedded immutable bodies into the canonical V2
comment evaluator, with one retained lane per document revision. It deliberately
does not construct a current release-domain observation. Missing bodies remain
explicit; comments stay readable, and current completion is unavailable rather
than vacuously successful. Suite89152 passed2/2 in54.6s, including online evaluation
parity and absent Git bodies.

SFMReviewGitEvidenceVerifier reads exact commit:path -> blob -> strict UTF-8 bytes
from an explicitly supplied local repository. No replacement objects, network
protocols, lazy fetch or interactive prompts; bounded time/output and owned process
cleanup. Independently verifies Git blob SHA1 and evidence SHA256. Disposable Git
suite77638 exited0:1passed/0failed in55.6s. It changes working-tree text after a
private fixture commit, verifies the committed bytes, rejects wrong path/blob/hash,
and checks Git status remains unchanged. This does not commit any project source.

SFMReviewOfflineExplorerTree adapts canonical Comments/Hashtags models to the
existing SFMInMemoryTextExplorerResolver, preserving source extensions for ordinary
text preview highlighting. Its root/status explicitly say retained evidence only,
with no current-source completion claim; missing embedded revisions have a separate
readable leaf. No custom review panel or filesystem/Git calls in tree preparation.
Suite42744 (offline-tree-tests.ndjson) exited0:4passed/0failed in55.3s. Checks mounted
tree traversal/text reads, exact comment values, language extensions, deterministic
preparation, mount disposal and unchanged original ledger.

Remaining: action/context integration and mount lifecycle, GUI verification,
portable export dry-run/confirmation, actual new-file reopening/self-exclusion,
recognized Git blob reuse without identity rewriting. Stretch4 is still ACTIVE,
not complete. No running test/game handles at this checkpoint. Rust/installed CLI
unchanged since the prior A1145E14... checkpoint; dependency declarations/lockfiles
remain frozen, no new dependency or reference clone. Next integration should use
SFMExplorerRuntime.openProjectedScene and an explicitly disposed in-memory mount,
not the old bespoke review explorer. Do not announce an unavailable menu option.

### Stretch4 offline action integration (GUI gate pending)

Added sfm:review/evidence/open with one quoted review_file argument, exposed in the
ordinary .sfm-review.json context menu as Open retained review evidence (offline,
v3). It reads at most64MiB on a dedicated worker, uses strict UTF-8/v3 parsing and
publishes only if the originating host/panel still exists. It never calls the
active review runtime or resolves targets. Ordinary projected Explorer displays
the backing file location and a retained-evidence root; comments/hashtags and
missing-body/status leaves come from the tested in-memory tree. Explorer owns the
mount through ownProjection/disposeProjection, released on close/discard/close-all.
Failed workspace attachment discards the new Explorer/mount. No wrapper screen.

Initial compile+offline suite17755 passed4/4 in54.5s. Added two action reader tests
for a review outside any repository, unchanged authority, bad UTF-8/schema and
missing files; updated context registry expectation for the new fifth choice.
Changelog entry added. Full Java suite99110 is running with
SFM_TEST_LEDGER_COMPANION=true (offline-full-tests.ndjson); poll this exact handle.
Datagen and GUI2/GUI4 still required after this observable change. Do not label
the new action GUI-verified yet. Portable export/compaction remains incomplete.
Protected review hashes rechecked52E8.../9ADD... unchanged; dependency manifests and
lockfiles git diff empty. No Rust edits/new dependencies or unpinned acquisitions.

Full suite99110 exited0:2008passed,0failed,3opt-inaborted in1m35s. Datagen67044
exited0 in50.0s. Subsequent read-path inspection caught a real integration gap:
SFMPathOpenAction rejected all contributed paths despite resolver text capability.
Replacing that with explicit supportsTextRead and requiring an originating Explorer
for contributed roots. Also inspect deepestContainingRoot: it still calls native
Path and must receive a contributed-path containment branch plus tests before GUI.
Suite84075 currently running offline-path-tests.ndjson covers the first guard edit,
not that forthcoming containment fix. Re-poll; do not treat this narrow suite as
proof of the full user flow. No gameplay process launched yet.

Suite84075 exited0:6passed/0failed in55.0s. Containment fix now implemented:
SFMPathOpenAction.deepestContainingRoot delegates to a testable collection overload;
contributed paths compare exact scheme/kind/mount/revision and segment prefixes,
while filesystem paths retain native Windows containment. Test checks deepest root,
cross-mount rejection, comments vs comments-other, and contributed/file separation.
Suite20769 currently running offline-root-tests.ndjson for SFMPathOpenAction.
This is the only live execution handle at checkpoint. GUI2/GUI4 and a post-final
full suite still pending; datagen67044 covers current changelog (later changes Java
only). Next: finish20769 then virtual exploratory game for offline evidence action,
read comment/source/status, close/reopen and prove active review unchanged.

Suite20769 exited0:4passed/0failed in56.4s, including new contributed-root test.
No live test or game handles remain. Next gate is actual virtual GUI2/GUI4 offline
Explorer usage; full suite2008pass predates this last small path-opening fix, so
rerun full Java after final integration edits. Goal remains active on stretch4.

### Stretch4 first offline GUI2 walkthrough

Launcher63763 exited0 after7m10s; JVM7180 confirmed absent. Virtual-input session
15660b44-186e-440b-85e6-a788bb09e74d, gallery
sfm-title_screen-20260908-070025-424/index.html. Steps1–2 demonstrate an unquoted
Windows path is not a valid StringArgumentType.string;3–4 quoted command opens
offline Explorer directly from title screen. Active review remains path=none,
epoch1/generation1 throughout: the offline view does not load writable authority.
5–6 expand comments/properties;7 reads exact original comment value;8–9 reads
original retained Java source with syntax colors (no newly inserted prefix from
current disk).10 reads status with explicit unavailable-current-domain statement.
11 Escape closes only the preview;14 Escape on sole Explorer opens close-choice
palette.16–17 explicitly choose screen/close and confirm TitleScreen;18–19 reopen
new evidence Explorer;20 finish. This is actual close/reopen, not inferred from Esc.
Review fixture SHA B364F0AFDDE09FC383649ABEF11BBE272D45E97624F7F959B097FA8B67C7A00D
unchanged. NoSFMerrors; startup errors are pre-existing okzoomer/epp mixin metadata,
AE2 guide invalidpage, TheOneProbe config notification and missing IF texture.

Rendered shortcomings corrected after run: auto-hoisted root hid the offline
warning while inherited Comments title said live working tree; now Comments and
Hashtags headings explicitly say retained/offline. Status prose split into shorter
lines. review-evidence scheme now uses existing review ItemStack presenter instead
of [D]/[F] markers. Generic path previews use already-published Explorer labels for
contributed paths instead of 000000.java/txt titles. No resolver reads for labels.
These presentation changes are NOT yet GUI-verified. Suite pending functions
cell1341 (offline-label-tests.ndjson); resume cell then its returned exec handle.
Next: finish test, GUI4 with file context menu and updated visuals, then user guide
and final full Java. Export/compaction/recognizedGit work remains active afterward.

Presentation suite49361 (from cell1341) exited0:6passed/0failed in56.3s. Current
sources compile; no test/game handles remain. GUI4 must validate the final headings,
ItemStack icons, published-label preview titles and file-context entry point.

### Stretch4 export acquisition and publication UI checkpoint

Rust check-all19492 exited0:709 unit tests passed,3ignored; integration groups
10/12/40 all passed. Copied Git reviews now exclude both original and new review
authority paths, retaining reconciliation/unit consistency and rename boundaries.
Scope restriction for Git targets remains explicit. Installer32459 is running;
verify its exit, installed hash/path and smoke command before runtime acceptance.

Export service focused suite (export-service-tests.ndjson) completed successfully:
9passed/0failed in1m00s. New service bounds acquisition, verifies exact saved hash,
rejects existing destinations, preserves embedded non-Git content and rebases
repository hints when copying to a different parent. Outside-repository copies
support offline evidence; this does not promise live source portability.

Added preview/report/confirm actions under sfm:review/evidence/export and choices
in the freshness menu. One transient preview bounds retained memory; worker handles
all source/Git reads and new-file publication. Confirmation preserves active review
and offers offline opening of the result. This action integration is NOT yet
compiled or GUI-verified at this checkpoint. Next: focused action tests, full suite,
datagen/changelog, GUI4 export/offline walkthrough and guide. Stretch4 remains active.

Operational constraints remain docs/tasks/goal execution and testing readiness
guidelines.md: frozen dependency declarations/lockfiles, no new dependencies or
reference clones, deterministic pinned-cache rehydration only, canonical CLI/no
Gradle, no OS cursor injection, no commits/push/propagation, protected user reviews
unchanged. Installer freshness must be checked after the final Rust mutation.

Installer32459 exited0 (locked/offline) in1m19s. Installed executable
G:/Programming/Caches/CARGO_HOME/bin/sfm-propagate-changes.exe SHA256
0142A805365C90A3F1FBE47AA6693CCC680CD3C097B6531DFD06DC9791E3B87F;
--version succeeds:0.1.1 rev16328629f built2026-09-08 07:14:17 -04:00.
Dependency declaration/lockfile diff empty; protected reviews remain52E8.../9ADD....
Action suite65523 passed9/0 before final path-binding edit; final focused72080
passed11/0 in55.4s including source/destination/policy grammar and truthful export
report tests. Added changelog entry (datagen still pending). Full Java suite launched
as export-full-tests.ndjson with real ledger companion enabled; inspect exact active
handle before launching another test. GUI4, user guide, recognizedGit and full
stretch4 acceptance are still outstanding. No game launched in this checkpoint.

Full suite5795 exited0:2013passed,0failed,3expected opt-in aborts in1m20s with
SFM_TEST_LEDGER_COMPANION=true. Datagen63752 is running export-datagen.ndjson;
poll exact handle. Next launch virtual GUI4 exploratory review only after datagen
completes. Current action grammar is preview <review_file> <new_file> <policy>,
with quoted paths and portable/git-references alternatives; context choices bind
the original path and confirmation retains the active review.

### Stretch4 GUI4 export/offline acceptance and follow-up

Datagen63752 exited0 in49.6s. GUI4 launcher9459 exited0 in12m20s, virtual session
953f568b-444b-4f0e-938c-af5924541ed5, JVM40276 confirmed absent afterward. Durable
gallery: sfm-title_screen-20260908-073712-569/index.html. No SFM failure in logs;
TheOneProbe config FATAL notifications remain third-party startup noise.

Steps1–3 found legacy review-open's greedy path rejects surrounding quotes;
4–6 use the accepted unquoted greedy path and open the disposable review.7 opens
freshness context menu by mouse;8–9 use its generated portable-export choice.
Preview says10929bytes and Test-Path destination=false.10–11 inspect report:
two bodies339UTF8bytes,zeroGitdependencies.12–13 explicitly confirm; newfile10929
bytes.14–15 open its offline evidence.16–20 expand/read original comment and
original Java bytes (return2,without later inserted prefix), correct syntax/icons/
published preview labels.21–22 close to TitleScreen;23–27 normal file Explorer,
right-click and mouse Execute reopen the copied evidence;28 reads explicit current
domain unavailable/no release completion claim.29 finishes.

Created only goal-owned file:
platform/minecraft/build/lazy-review-20260908/review-export-88404d56-a4db-4886-b971-ccbe6931330a.sfm-review.json.
Original fixture remains B364F0AFDDE09FC383649ABEF11BBE272D45E97624F7F959B097FA8B67C7A00D;
active review stayed original epoch2/generation2/dirtyfalse throughout export and
offline browsing. This proves exported evidence reopening, not fresh source coverage.

Found report-to-confirm navigation dead end: after opening a report, user had to
return to palette manually. Added exact-report-bound context provider offering
confirm/discard, a cancel action, and report instructions for right-click/Alt+Enter.
No text-selection behavior changed; existing generic context action registry used.
Current focused suite46226 (export-report-context-tests.ndjson) is running; final
GUI2/report-context test and post-final full suite remain pending. Guide now records
verified offline-reading flow. Legacy quoted-open inconsistency remains recorded
for polish. RecognizedGit work still outstanding; stretch4 not complete.

Report-context suite46226 exited0:11passed/0failed in55.9s. GUI2 launcher7089 exited0
in7m48s; session6775db2a-af84-40c0-8684-1a2fa990caa0, JVM44760 confirmed absent.
Gallery sfm-title_screen-20260908-074909-180/index.html.7 report includes context
instructions;8 right-click yields confirm/discard ahead of ordinary text actions;
9–10 mouse selection+Execute publishes10929byte export-context-2.sfm-review.json.
12 Alt+Enter on consumed report has no export actions.14–17 a second preview is
discarded: export-discard-2.sfm-review.json does not exist. Original fixture hash
B364...unchanged; activepath/epoch2/generation2/dirtyfalse unchanged. NoSFMerrors;
pre-existing thirdparty TheOneProbe notifications remain in startup log.

Guide now documents the verified mouse-driven export/report/offline workflow.
Post-context full Java suite launched export-context-full-tests.ndjson; poll exact
returned exec handle. No game remains running. Current CLI hash0142... still valid
(no Rust edits since install). Recognizing later-committed nonGit captured content
without changing approval identity remains the next part of stretch4; do not mark
the stretch complete merely because export and offline viewing now work.

### Stretch4 recognition checkpoint — immutable identity first

Post-context full Java suite41224 exited0:2013passed,0failed,3expected opt-in
aborts in1m22s. Added bounded local Git recognition primitive to the existing
verifier: explicit full commit plus repository-relative storage path, exact SHA256
and Git blob verification, original document identity returned unchanged. A later
rename may change the storage path, never the original comment target. HEAD aliases
are rejected by this primitive; a batch coordinator must resolve any alias once.
Focused git-recognition-tests.ndjson (exec8243) passed2/0 with no aborts. The new
private-repository test covers capture-before-commit, later renamed storage,
changed working bytes, exact original identity preservation, hash mismatch,
missing path, moving alias and unsafe path rejection. Dependency declaration diff
remains empty. No game was launched during this recognition checkpoint.

Next integration must keep storage provenance separate from Document.git. Proposed
optional evidence storage references are keyed by content hash, contain pinned
repository/commit/path/blob, and are independently verified before any explicit
Git-dependent export may omit a body. Java and Rust readers must both support them
before publication; old v3 files remain readable. Portable export must recover exact
bodies or fail, never silently claim portability. Default capture still embeds
non-Git evidence on first human comment. Recognition alone is not permission to
discard evidence and does not complete the compaction/reuse acceptance.

Dependency posture remains frozen per goal execution and testing readiness guidelines:
no new dependency, lockfile edit, upgrade, unpinned substitute or reference clone;
only deterministic existing-lock cache rehydration is permitted. No source commits,
push or propagation. Protected user reviews are not targets of this work.

### Stretch4 separate Git storage integration — validation in progress

Implemented optional evidence.git_storage, keyed by content SHA256 with pinned
repository/commit/path/blob, independently of document identity. Java codec/table
preserve it through capture; Rust codec validates it and the resolver reads exact
historical bytes under original IDs/path/Git identity. Explicit Git-dependent export
recognizes same-path later commits only for an unambiguous repository, pins HEAD
once per repository, retains unmatched bodies, and reverifies saved references.
Portable export restores exact bodies; missing objects refuse publication. Storage
inspection and export reports explain this separately from original targets.

Java focused13198 found a report test using null ledger placeholder (not a product
null preview). Replaced it with valid fixture. Focused11674 passed12/0 in56.9s;
includes private-repository capture/commit/compact/changed-working-bytes/portable
round trip and missing-object refusal. Added installed-companion assertions after
that run; final suite must run SFM_TEST_LEDGER_COMPANION=true after installation.
Rust check-all38557 exited0:711unitpassed/0failed/3ignored plus10,12,40 integration
tests. Test coverage includes optional-field roundtrip, unsafe/duplicate storage
rejection, and original non-Git identity restored from a different Git storage path.

Installer50507 is running locked/offline; poll exact handle, verify installed hash
and --version before companion tests. No game running. Final GUI2/4 recognition
workflow, datagen, full Java suite and goal acceptance remain pending; stretch4
stays active. Dependency declaration diff empty; protected reviews still52E8... and
9ADD... . Changelog and guide describe the new behavior with verification caveat.

Installer50507 exited0 in1m17s. InstalledCLI --version0.1.1 rev16328629f built
2026-09-08 08:12:38 -04:00, SHA256
C9087CBDB2D4FFC5BBC1AEAB341337B02ABB893C9845ED72940B400A0C80A168.
Full Java98016 exited0:2016passed/0failed/3expected opt-in aborts in1m39s with
SFM_TEST_LEDGER_COMPANION=true. This includes Java-created compacted ledger read
through the installed Rust companion, original non-Git identity and comments
preserved, portable recovery, unavailable-object refusal and shared-hash identities.
Current tests also cover storage inspection for nonembedded non-Git captures.
Datagen launched git-storage-datagen.ndjson; inspect returned handle next.

Prepared goal-owned private GUI repository (not an SFM source commit):
platform/minecraft/build/lazy-review-20260908/gui-git-storage,
commit53dc5ca8f26cead5ffcddff8fe1a47a8c81bda0f. Original.sfm-review.json is the
disposable fixture with baseline replaced by that private commit and review copies
self-excluded; SHA9A1262E9738351378051E201BE1B2F42474BBB1916EAED01A0B5B689CCB9DC1E.
It contains both historical source bodies; only the newer CE2E... body exists at
the committed path, so Git-dependent export must retain the unmatched old138byte
body and use one storage reference for the newer201byte body. No game launched yet.
Next: GUI2 andGUI4 inspect/confirm Git-dependent copy, reopen historical comments,
portable re-export and explicit missing-evidence behavior; retain actual size
measurements (metadata can make very small examples larger, so UI no longer promises
"smaller"). Stretch4 remains active until these final gates and audit pass.

Datagen53455 exited0 in51.4s, zero errors, two reported warnings. All command
handles from this checkpoint are terminal; no game was launched. Installed hash
C908... remains current for Rust sources. Next continuation should launch virtual
GUI2 against the prepared private fixture, then GUI4; do not reuse old exploration
session directories. No new dependency or protected-review mutation occurred.

### Stretch4 GUI2 verified, offline explanation improved

GUI2 launcher86750 exited0 in11m38s; JVM36112 confirmed absent. Virtual session
566883b3-27a6-47c3-9131-e66d5b0f21de; gallery
sfm-title_screen-20260908-083025-078/index.html. Initial attempts failed because
the private Git fixture was created as CodexSandboxOffline but the game runs as
Teamy. Repaired OWNER only on the exact private root and its .git directory to
Teamy's SID; no global safe.directory or other security relaxation. Retry7–9
opened normally. These failures did not mutate the fixture review.

10–14 Git-dependent preview/report: original10996bytes, output10983bytes, one
138byte embedded body, one Git-dependent document and one separate storage ref.
Destination absent before confirmation.15–17 right-click report, mouse choose and
Execute published compact.sfm-review.json. Original active epoch2/gen2/dirtyfalse
unchanged.18–21 offline reopening correctly shows one missing embedded body.
22–24 reopening compact through companion keeps both comments, epoch3/gen3.
25–31 portable re-export/report/Alt+Enter confirmation restores two bodies339bytes,
zeroGitdependent, output11313bytes.32–34 offline reopening reports zero missing
bodies.35finish. Original fixtureSHA remains9A1262...; exported original documents
retain their non-Git identities. Startup ERRORs are existing thirdparty mixin,
AE2 guide and IndustrialForegoing texture messages, not SFM failures.

Found offline missing-details page listed only an opaque revision ID. Changed it
to show filename, original path, pinned storage provenance, explicit "not checked"
Git availability and recovery instructions. Zero-missing label now positively says
all retained evidence is embedded. Added regression for separate-storage details.
Full suite git-storage-offline-explanation-tests.ndjson now running; inspect exact
handle. GUI4 and final checkpoint remain. Repeated report opens can accumulate
narrow splits; record for stretch6 navigation polish, not a silent behavior change.

Post-explanation full Java72112 exited0:2017passed,0failed,3expected opt-in aborts
in1m52s with installed companion enabled. No active exec/game handles remain.
GUI4 should use fresh output names (compact-4.sfm-review.json/portable-4.sfm-review.json)
inside the same private fixture, because GUI2 outputs intentionally remain. Verify
the improved missing report and open retained source text, not just root counts.
Only report prose/labels changed after datagen53455; no new generated localization
keys or Rust runtime inputs. Final installedCLI remainsC908... . Goal not complete.

### Stretch4 verified checkpoint; stretch5 claimed

GUI4 launcher51468 exited0 normally in14m09s; JVM42508 absent. Session
9a5b58a4-710e-4b78-9618-24109b5b4db5 used virtual input only; gallery
sfm-title_screen-20260908-084930-558/index.html. Steps4–11 preview/report and
mouse confirmation made compact-4;15–16 verified the improved missing-body
explanation;19–21 reopened compact with both comments;22–28 portable export and
offline reopen showed all retained evidence embedded. Steps29–38 expanded both
comments and opened their distinct historical Java source bodies, with syntax
highlighting.39finish. No SFM failure in console; third-party mixin/AE2/texture
errors and TheOneProbe's config-change FATAL message did not prevent normal exit.

Post-run semantic comparison proves targets, entire state (including comments),
and document identities equal in original/compact-4/portable-4. Original fixture
SHA9A1262... unchanged. Correction to earlier size shorthand: original physical
file is10946bytes; preview's10996 is normalized serialization, not physical size.
Compact-4 is10983bytes SHA6EB01CCFDFA7C68CC3E3D7D6010F072F77E1AACA12C530D7A428CA90589162CB;
portable-4 is11313bytes SHACD0FABE5AB49B4F32807CAEA85BB6A151D6EEC4E7EFFC1DEB76A1FF5753BA54C.
Thus this tiny fixture's Git-dependent copy is larger than its physical original;
the UI deliberately does not promise size reduction. Original has no storage refs,
compact has one retained body/one storage ref, portable two bodies/one ref.
Protected user reviews remain52E8... and9ADD...; dependency declaration diff empty.
Full Java2017/0, Rust711+10/12/40, datagen and installed C908... evidence above
remain current. No test game or active command handle remains.

Stretch5 is now the sole claimed item: inspect comment-save publication/index
reconstruction and evidence serialization, introduce bounded reuse with content/
generation identity guards, and test that equal bytes do not transfer approvals.
Retain single-file durability and frozen dependency posture; no broad cache or
windowing redesign. Stretch6 remains unstarted. Goal remains active.

### Stretch5 initial reuse checkpoint (not acceptance)

SFMReviewEvidenceTable.capture now returns the same immutable table when every
requested identity/body is already retained, after validating all ranges and
identity consistency. Retain compares exact text before reusing an existing
Content rather than constructing/hashing it again. Equal bytes under a distinct
successor revision still append a distinct Document; old authority is unchanged.
Tests assert object reuse, separate successor identity, warm-path invalid Unicode
range rejection, and independent comment state despite a shared evidence table.
Review-filter suite30308 exited0 in1m21s:369passed/0failed/2expected opt-in aborts,
installed-companion enabled. No running command/game remains. Java-only edit;
installed Rust C908... remains current. Full final suite/GUI/datagen checkpoint
will be repeated after the rest of stretch5; this is not its completion.

Investigation for the next implementation: ExplorerRuntime.projection keys every
projection by review generation, rebuilds releaseChanges and recursively indexes
reveal paths. Changes contains comment children, so blindly reusing the old tree
would hide new comments; source topology and comment attachment need distinct
reuse boundaries. Model.releaseChanges constructs corpus/pairs/diff leaves and
documentCommentChildren on every generation. Preserve source/generation guards,
comment navigation, stale-continuation rejection and source-range mappings.
Store.save still serializes/round-trips the whole sparse ledger for validation;
do not simply delete safety validation to claim a speedup. V3Codec.write also
serializes V1 state only to parse it back into a JSON tree, and V1 does the same
for V2 session; a shared typed-tree writer is a potential redundant-work reduction
with exact serialized-output tests. No such codec/tree refactor implemented yet.

### Stretch5 direct-tree serialization checkpoint

V2 session codec now exposes fresh owned writeTree; V1 uses it and exposes a
package-local writeTree; V3 nests that directly. Public string writers preserve
pretty-printing/trailing newline and still build fresh trees. This removes two
intermediate serialize/parse boundaries, not the final Store.save round-trip or
external-edit checks. Regression reconstructs the previous nesting boundary,
checks canonical output, and mutates a returned tree to prove later writes are
independent. Review suite62122 exited0 in1m21s:370passed/0failed/2expected opt-in
aborts with installed companion enabled. No active test/game handle remains.
Dependency declaration diff empty; protected reviews52E8.../9ADD... unchanged.
No Rust edits or dependency changes. Full final validation remains due after the
remaining stretch5 changes; stretch5 stays claimed.

Additional Explorer constraint: SFMReviewExplorerModel.Node includes mutable
expanded state, so do not share whole model instances between panels. Source
topology/diff recipes can be reused independently, with fresh comment children
and panel state. Changes currently evaluates documentCommentChildren once per
comment then rebuilds all corpus pairs; reveal indexing traverses the whole tree.
Next continuation should implement this guarded incremental boundary and tests
for newly added comments, changed source, two panels, stale generations and reveal
paths. This checkpoint does not claim Explorer rebuild reduction yet.

### Stretch5 Changes source cache (verification in progress)

Implemented resolver-owned SFMReviewExplorerModel.ChangesCache, bounded to one
source observation per layout. It guards schema/session ID/repository bindings/
corpus documents/review units/revision lanes, reuses immutable source leaves and
diff recipes, and builds fresh mutable nodes with newly evaluated comment children.
Neither comment removal/addition nor panel expansion may leak through shared nodes.
Warm cache hits still run Kernel.validate on the new full state; source equality
does not validate comment bindings or resume state. Public uncached releaseChanges
remains available for parity tests. No global cache, required sidecar or new deps.

Suite92820 failed two new fixture setups (dangling selector after comment removal,
dangling resume unit after unit removal), before testing cache behavior. Fixed test
setup to retain only matching bindings and clear resume/attestations. Suite83554
then passed372/0/2expected opt-in aborts in1m04s, real companion enabled. Tests
compare cached/uncached tree descriptions, source-leaf reference reuse, fresh node
identity/expansion, comment addition/removal and changed source-unit/layout guards.
Post-pass audit added warm-hit validation and a dangling-selector regression.
Current suite36318 is RUNNING: stretch5-changes-cache-tests-3.ndjson. Resume that
exact handle; no game is running. No Rust change, installedCLI remainsC908... .
Changelog now describes the reuse work; final datagen must run after this edit.

Still pending for claimed stretch5: source/reveal-index and comment-evaluation
incrementality (current implementation still clones/traverses all nodes), stronger
changed-byte/source-invalidation and runtime-generation integration evidence,
representative operation counts/latency, GUI2/GUI4 comment navigation and final
suite/datagen/guide checkpoints. Do not label the entire performance item complete.

### Stretch5 reveal indexing no longer acquires analysis identity

Warm-validation suite36318 exited0 in1m18s:373passed/0failed/2expected opt-in
aborts. Follow-up code inspection found ProjectionSnapshot.indexRevealPaths used
documentSource, whose analysis resolver scans corpus/repository lists, walks parent
directories and Files.readAllBytes/hash-checks candidate checkout files. That work
was being performed merely to collect pinned path/root/hash/range identities.

Extracted pure pinnedIdentity from documentSource. Reveal indexing now uses it
directly and skips literals/generated surfaces as before. Opening a document still
uses the full analysis resolver so Java highlighting/navigation keep their existing
capabilities. Index construction no longer performs those working-file reads or
hash checks. Existing tests cover exact reveal targets/ranges and reject changed
hashes. Suite87248 RUNNING: stretch5-reveal-pure-tests.ndjson; poll this exact handle.
No game launched, no Rust edits. This is substantive filesystem-work removal, not
proof of full index incrementality or measured latency improvement. Remaining
stretch5 items and final datagen/GUI gates above still apply.

Reveal-pure suite87248 exited0 in1m19s:373passed/0failed/2expected opt-in aborts.
Existing before/after reveal-path round trips and changed-hash rejection passed
with the pure identity projection. No active command/game remains. Next work
continues claimed stretch5, not stretch6; source cache and filesystem-work removal
are verified substeps but full incremental index/performance acceptance is pending.

### Stretch5 source-only reveal index reuse (tests pending)

Added ChangesIndex with immutable node-path/reveal-path maps, retained in a bounded
16-lens-root resolver cache. Same-source guard is shared with ChangesCache; root
identity includes open epoch/layout. Warm comment-only projections borrow the maps
without traversing/rebuilding source reveal entries. Changes revision children
are comment value leaves, so cold source indexing deliberately excludes them;
current childSlice materializes their IDs lazily. Comments/Query lenses retain
full current-generation indexing. Mutable per-projection maps remain separate.

Runtime regression adds a human comment, checks visible comment rows and unchanged
source-index build count, then reopens and checks a new root/build. Suite26130
failed fixture validation because the added comment reused an existing proposal
ID; corrected it to proposal:incremental-test. Current rerun8331 is RUNNING:
stretch5-index-reuse-tests-2.ndjson. Poll exact handle before further builds. No game
running; no Rust/dependency edit. Source diff whitespace check passed.
Remaining: finish this regression, comment-evaluation reuse/measurement, stronger
source-change invalidation, final GUI2/4, suite/datagen/guide. Stretch5 remains active.

### Stretch5 index verification and comment presentation reuse

Index suite8331 exited0 in1m05s:374passed/0failed/2expected opt-in aborts.
The runtime test proves a newly saved human comment appears in Changes while
source-index builds remain1; reopening yields a distinct root and build2.

ChangesCache now retains presentation evaluations keyed by the full immutable
Comment, only for current session comments. Missing comments are evicted. Session
schema/ID/coordinate system/revision lanes/style rules/completion policy changes
clear evaluations. Full Kernel.validate still runs; this cache only avoids the
additional per-comment evaluateComment layer used to build Changes children.
Current literal/union/intersection/difference selectors have no cross-comment
dependency. Mutable view nodes remain fresh. New test compares cached/uncached
trees and asserts adding one comment performs one additional presentation evaluation.
Suite12401 is RUNNING: stretch5-comment-cache-tests.ndjson; poll exact handle.
No game, no Rust/dependency changes. Remaining stretch5 validation includes changed
source bytes, operation counts/latency, GUI2/4 and final suite/datagen/guide.

Comment-cache suite12401 exited0 in1m19s:375passed/0failed/2expected opt-in
aborts. New-comment presentation evaluation count and cached/uncached tree parity
passed. No active command/game remains. This completes the initial incremental
implementation substeps, not stretch5 acceptance; next should strengthen actual
changed-byte invalidation and collect representative latency/GUI evidence before
claiming this item complete. Do not start stretch6 yet.

### Stretch5 changed-byte regression checkpoint

Added a regression changing actual UTF-8 document bytes and the corresponding
corpus witness in a disposable, comment-free projection fixture. The warm cache
rebuilds source recipes, matches the uncached tree, and leaves the old projection
unchanged. This checks source invalidation, not approval migration or permission
to rewrite an immutable production revision. Suite93074 reported 376 passed,
0 failed, 2 expected opt-in aborts (378 found), with successful JUnit completion.
Log: build/lazy-review-20260908/stretch5-changed-bytes-tests.ndjson under
platform/minecraft. Representative performance measurements and final GUI/full
suite/datagen/guide gates remain; stretch5 is still claimed, stretch6 unstarted.

Dependency pinning was rechecked: Cargo.toml/Cargo.lock/build.gradle and
sfm-toolchain.lock.json have no diff. Frozen posture remains in force; no new
dependencies, reference clones, Gradle, commits, or propagation. Both protected
user review hashes still match the earlier recorded values.

### Stretch5 release-scale projection measurements

Suite35069 exited0 in1m01s, all6 ChangesCache tests passed. The optional read-only
measurement used review-2026-09-07.sfm-review.json:31,219,051bytes,1999 corpus
documents,1846comments,17154 projected nodes. Each sample compared every node's
identity/label/kind/leaf against uncached output; exact input bytes remained equal.
After warmup, alternating cached/uncached order produced these microseconds:

| Iteration | Cached | Uncached |
| --- | ---: | ---: |
| 0 | 152881 | 2638276 |
| 1 | 126185 | 2349546 |
| 2 | 123997 | 2329626 |
| 3 | 140146 | 2327434 |

Source builds stayed1; presentation evaluations stayed1846. This is projection
cost only, not save/end-to-end latency, and not a strict percentage acceptance
threshold. Current full validation still runs on each warm projection. Logger
measurements appeared in platform/minecraft/logs/latest.log at09:28:41–09:28:51;
the table preserves them because later runs replace that log. Build/test log:
platform/minecraft/build/lazy-review-20260908/stretch5-projection-measurements.ndjson.
Repeat with SFM_TEST_REVIEW_PROJECTION_FIXTURE pointing to a legacy materialized
review and test filter SFMReviewChangesCacheTests. No fixture mutation is permitted.
Next: full Java suite/datagen and final GUI2/GUI4 before stretch5 completion.

Full Java suite73401 exited0 in1m23s:2025passed/0failed/4expected opt-in aborts
(2029found). The optional projection benchmark was disabled here and passed
separately above. Datagen66104 exited0 in50.2s,2warnings/0errors. Logs:
stretch5-full-java.ndjson and stretch5-datagen.ndjson in the same evidence directory.
Guide now includes a comment-refresh test flow and explicitly labels GUI validation
pending. No test/game handle remains active from these commands. Installed CLI
SHA remains C9087CBDB2D4FFC5BBC1AEAB341337B02ABB893C9845ED72940B400A0C80A168;
no Rust edit occurred in this slice, so no new installer is necessary.
Next is disposable virtual-input GUI2/GUI4 comment refresh/reveal, then checkpoint
stretch5. Do not start stretch6 until that acceptance is verified.

### Stretch5 GUI2 comment-refresh checkpoint

Launcher50392 exited0 in13m20s, JVM28796 confirmed absent. Virtual session
4dae1cc4-17ec-4279-b2d3-f675a873b248 finished at request35. Gallery:
sfm-title_screen-20260908-094710-712/index.html under the game-test-preview runs.
No OS pointer injection. The copied disposable ledger is
build/lazy-review-20260908/gui-git-storage/incremental-2.sfm-review.json;
private Example.java changed return2 to return3 (not SFM source). Original evidence
copies and protected user reviews are untouched; protected hashes rechecked equal.

Figures11/28 show Java source;23 captures dragged bytes[183..192);27 records
generation3,4comments,dirty=false;29 shows the new needs-change child under After
with prior hierarchy still expanded;30 opens its readable value;34 successfully
reveals After via the target button. Saved human:release-review:3 pins document
efd0588399b19eca8c482573c4466a45edaa976c0f9241445d62821be2ec6f7b and
SHA9a5f37f654d15980435d41b258792191c627cc5e4b52a54352024b4f5279a53d.
No claimed direct comment-row-to-target affordance: that row currently only
offers generic Explorer actions. The source target button is the verified path.

Exploration discoveries retained for stretch6 (not fixed/claimed yet): compact
single-child chain advances one directory per click; double-click/Shift+Left left
a caret rather than a range in this virtual read-only preview (drag worked;
investigate modifier transport vs editor behavior); reveal toast exposes opaque
row UUID; comment child context menu lacks direct target navigation. Initial
open/edit command was invalid; corrected to registered open/view before execution.
Startup ERROR logs are missing third-party mixin minVersion, invalid AE2 guide
startup page and an Industrial Foregoing missing texture, not a review failure.
GUI4 remains before stretch5 completion. No live process/test handle remains.

### Stretch5 GUI4 and verified checkpoint

Launcher52459 exited0 in8m25s; JVM24580 confirmed absent. Session
8164bbd1-c15b-44c2-8c42-872f830c2e6c finished request23. Gallery
sfm-title_screen-20260908-095702-639/index.html. Reopened the GUI2 disposable
incremental-2 ledger in a new JVM, preserving its earlier comment. Ctrl+A then
Alt+Enter captured[0..201), saved a second needs-change comment, and retained
Changes expansion. Figure18 shows both comments under After;20 shows readable
comment text using Fit Width;22 verifies source reveal. Persisted human comments
increased3→4, runtime comments4→5 including its synthetic change marker. The
entire evidence object is identical before/after:3bodies,3document records.
Protected reviews retain their recorded hashes. Startup errors match the same
third-party mixin/AE2/missing-texture issues; no review failure was observed.

Stretch5 acceptance combines source/comment/index count regressions,
changed-byte invalidation and projection independence, evidence object reuse and
serialization parity, release-scale node parity/timings, full Java2025pass,
datagen success and GUI2/GUI4 persistence/comment/reveal. No dependency changes;
installed CLI remains current because no Rust source changed in stretch5.
Guide/changelog updated. This is a checkpoint, not overall goal completion.

### Stretch6 claimed scope

Proceed with bounded review-navigation polish and adversarial flows from the
existing ladder. First address direct target navigation from Changes comment rows
and readable reveal feedback, then Shift+Arrow source-range selection with tests.
Use disposable edit-during-preview/close-during-save/rename-delete flows and the
existing worker/navigation barriers; never mutate protected user reviews. Preserve
single-file evidence and approval invariants. Investigate compact-chain UX
without replacing the general Explorer. Every discovered fix requires focused
regression evidence; final full tests/datagen/GUI and handoff remain gates.
Current process state: no game or test handle running. Frozen dependencies and
no-Gradle/no-commit/no-propagation/no-OS-pointer constraints remain unchanged.

### Stretch6 readable reveal feedback checkpoint

SFMWorkspaceToastContent.pathMessage now accepts an explicit bounded display label
separate from its exact path. SFMRevealHereAction uses the source filename as the
label while retaining the review-tree row address for clipboard/context actions.
Regression covers Unicode labels, opaque path retention, context availability and
64-code-point truncation. Suite75561 exited0 in58.9s:24passed/0failed/0aborted.
Log: stretch6-reveal-label-tests.ndjson. Changelog updated; final datagen/full/GUI
must rerun after this slice's final edits. No game or test handle running.

Next comment-row wiring seam: SFMExplorerContextActionRegistry's existing
ReleaseReviewProjectionProvider calls SFMReleaseReviewExplorerRuntime.contextChoices
(around900). Changes comment nodes currently have document-comment/<revision>/<id>
IDs and plain text SourceLeafs; preserve typed identities rather than splitting
opaque paths or guessing by labels. Existing SFMReleaseReviewCommentDetailsAction
offers text/reveal/selector/matches/provenance sections and guarded async navigation.
Reuse these actions and add explicit target navigation as needed, with stale-session
tests. Shift+Arrow issue confirmed in SFMDrawCanvasScreen: arrow handlers move model
cursors without applying Shift, whereas Ctrl+A correctly uses exact source ranges.

### Stretch6 comment menus and keyboard checkpoint (in progress)

Changes comment nodes now carry typed CommentNavigation identities. Their existing
Explorer context provider offers named text/reveal/matches/selector actions with
the captured review open epoch; execution rejects a menu from a reopened review.
The source-index regression passes15/0 after correcting its expected Brigadier
quoting (colon-containing IDs are quoted). Log: stretch6-comment-row-tests-retry-elevated.ndjson.
Sandbox cache access failed first; ordinary cache access succeeded without changing
any lockfiles or deleting locks. A new real-dispatch stale-menu test is pending the
full suite (an initial test constructor arity error was corrected).

Source-preview navigation now retains exact pointer-selection anchors for Shift
navigation and collapses horizontal selections to the appropriate edge without
Shift. It reuses existing canvas movement/geometry, not OS input. Unicode/CRLF
anchor-direction regression added. The attempted combined test filter containing
`|` selected zero tests: the runner is literal, not regex. This is NOT acceptance.
Full Java suite12480 is running with log stretch6-full-java.ndjson. Changelog
updated; datagen and virtual GUI2/GUI4 remain required. No game is running.
Named matches navigation reveals the matches branch rather than silently choosing
one of several targets; GUI verification of that flow remains pending.

Full Java12480 exited0 in1m32s:2027passed/0failed/5opt-inaborted(2032found).
The five absent opt-ins are installed symbol worker, release scale, repository icon
audit, installed ledger companion and projection benchmark. The stale-menu actual
dispatch regression and exact keyboard anchor regression passed in this suite.
Datagen19575 exited0 in51.4s with2warnings/0errors. Dependency declaration/lockfile
diff remains empty; installed CLI SHA remains C9087CBDB2D4FFC5BBC1AEAB341337B02ABB893C9845ED72940B400A0C80A168.
Both protected review hashes remain unchanged. GUI2 launcher61145 is starting the
virtual-input exploratory puppet; log stretch6-gui2.ndjson. Stretch6 is still active.

### Stretch6 adversarial GUI2 finding and fix in progress

GUI2 launcher61145 exited0 in12m50s. Sessiondd8c268b-4c26-4ea0-ad43-c365769b6bc2
finished request24; gallery sfm-title_screen-20260908-103542-595. Figure12 selects
the return line with Shift+Home;13 offers exact[175..192). Changed ONLY disposable
gui-git-storage/.../Example.java from return3 to return4 while its captured draft
remained open. Held persistence worker, approved old selection, closed source via
Done while save PREPARING, then released worker. Figure20 shows savedcomment5,
runtime6comments, generation3, dirtyfalse; source editor stayed closed. Persisted
human:release-review:5 is pinned to efd058.../9a5f37... bytes175..192 and retained
return3. Evidence remains3bodies/3documents; return4 is not embedded or approved.

This surfaced a real stale-Explorer bug: the save callback required the initiating
editor continuation, so closing it prevented surviving views from refreshing.
Figures21–23 show old expanded rows with no children. Production fix uses a
workspace-only continuation for refresh (NOT navigation), retaining review epoch
and matching lens path/epoch. A regression proves refresh survives source removal
but cannot transfer to another workspace; strict navigation still fails closed.
Focused suite11907 is running: stretch6-close-save-refresh-tests.ndjson.
Full tests/datagen/GUI evidence must be renewed after this fix. Compact directory
chains also required one click per not-yet-loaded segment; investigated/recorded,
not yet fixed. No protected review edits. Private sample currently contains return4.

Refresh regression11907 passed6/0 in56.5s. Final current-source full suite44004
with SFM_TEST_LEDGER_COMPANION=true exited0 in1m24s:2029passed/0failed/4opt-in
aborted(2033found), including real companion changed/deleted source and conflicting
save checks. GUI2 JVM31312 and helpers2268/32080 confirmed absent after finish;
the structured Rust GUI log has no ERROR events (game startup log classifications
remain separate). Datagen46753 currently running; then GUI4 and renewed GUI2
acceptance remain before claiming stretch6 complete.

Final datagen46753 exited0 in50.3s (2warnings/0errors). GUI4 launcher94029 is
starting, log stretch6-gui4.ndjson; no additional production edits since the full
2029pass suite. Pending: verify save refresh after closing source, named row
actions/targets, readable reveal toast, and final GUI2 smoke. Preserve original
review hashes and existing installed CLI; dependency posture unchanged.

### Stretch6 GUI4 verified checkpoint

Launcher94029 exited0 in13m04s. JVM39120/children confirmed absent. Session
2a84326e-4a41-4b19-8998-b68782670cc6 finished34. Gallery
sfm-title_screen-20260908-105446-887. Figure13 verifies Shift+Left exact[191..192).
18–21 repeat paused save/source close/release: humancomment6 persists and the
surviving Explorer refreshes, expands new needs-change and generated rows (14rows,
relationr15), without reopening source.22 exposes all4named row actions;24–26
reveal matches and open selected semicolon in its Java source;27 readable reveal
toast;32–33 read comment in the same area. Evidence now4bodies/4documents for6human
comments; new content is return4 (def374...); old approval remains pinned to return3.

Disposable rename check: validated exact sample paths, temporarily moved
Example.java to Example.renamed.java, restored in finally. v3 status returns
in_progress/exit5, changed2/approved0/remaining2; restored source status has
changed1/approved0/remaining1/blocking1. Initial legacy freshness command refused
v3, and an initial status wrapper misclassified exit5 before diagnostics were
inspected. Neither counts as validation; final structured status does. Guide now
explains this command distinction. Future polish: legacy freshness's unsupported
schema error should direct sparse-ledger callers to observation-aware commands.
Final GUI2 restart smoke launcher42046 starting (stretch6-final-gui2.ndjson).
No production edits since full2029pass/finaldatagen. Stretch6 not yet marked done.

### Final overnight acceptance — 2026-09-08 11:09 Toronto

The preceding entries are chronological checkpoints, not outstanding work.
Final GUI2 launcher42046 exited0 in8m12s after finish9. Session
d60e49f3-80ee-43d5-b11e-d4e566181868 reopened the saved review: epoch2,
generation2, writabletrue, dirtyfalse, runtime7comments. Figures5–7 navigate the
persisted humancomment6 matches branch and open exact bytes[191..192), the
semicolon in return4. Figure8 shows the selected target and readable linked
toast “Revealed Example.java in this Explorer”. Gallery:
`platform/minecraft/build/sfm-toolchain/artifacts/game-test-preview/runs/sfm-title_screen-20260908-110656-679/index.html`.
All observations report os_pointer_injection=false. GUI4 save/close/refresh and
named context-menu checks remain verified by the preceding checkpoint.

Final current-source gates: Java2029passed/0failed/4explicit opt-in aborts;
datagen exit0; final Rust check-all711unit/0failed/3ignored plus10/12/40integration
tests passed before the installed CLI checkpoint. No Rust changes followed that
install. Final GUI launcher reports ok with startup warning/error counts; these
are not asserted to be zero. Latest.log contains third-party missing mixin
minVersion (okzoomer/epp), AE2 guide startup-page and Industrial Foregoing texture
errors. The structured Rust run log has no ERROR events; the puppet completed.

Operational readiness:
- Branch1.19.2, HEAD16328629fa60c45a4f525b6f20aaa77715f91077, local goal edits.
- Installer was run using install.ps1's locked/offline recipe (session50507).
  Installed executable: G:/Programming/Caches/CARGO_HOME/bin/sfm-propagate-changes.exe,
  version0.1.1/rev16328629f/build2026-09-08 08:12:38-04:00.
  Final SHA256 C9087CBDB2D4FFC5BBC1AEAB341337B02ABB893C9845ED72940B400A0C80A168.
  Final --version smoke passed. User must run install script: **no**.
- Dependency posture remains **frozen**. Final dependency declaration/lockfile
  diff is empty. No new dependencies, developer/reference clones, commits,
  pushes or branch propagation. Existing-lock cache recovery only, as recorded
  in earlier checkpoints. No Gradle invocation or OS pointer injection.
- Final JVM36504 and its children are absent; CIM inspection also found no
  Java/SFM/Teamy process tied to this branch. Launcher42046 reaped normally.
  Earlier test processes were reaped at their checkpoints. No running test or
  unexplained lock wait remains; no unrelated process was stopped.
- Protected review SHA256 values rechecked unchanged:
  review-2026-09-07.sfm-review.json =
  52E8EE6E700B0090C7D8612C93D84393ED21FC39801F9047D59C9F3CEB42D306;
  manual-test.sfm-review.json =
  9ADDCCEC595CAA916EFDB8019270F62D577B336508D2608D84BDA2FE7AADD592.
- Disposable source restored to return4 after rename checks. No user approval
  was created or transferred by the demonstrations.
- Manual start from this root:
  `sfm-propagate-changes.exe run client --branch 1.19.2 --wait-for-build-lock`.
  Expected initial state: title screen; no installer/recovery step. Follow
  `docs/live review capture-on-comment guide.md` for creation, comments, refresh,
  storage, migration and export. Old review files remain frozen until an explicit
  new live-review operation; they are not silently converted on open.

Remaining later-goal polish, not hidden acceptance gaps: compact directories can
require another expansion as unloaded segments arrive; long comment text fitted
inside a narrow panel becomes small; legacy CLI freshness should suggest the v3
observation-aware API instead of only reporting unsupported schema. These do not
invalidate the tested current status/resolve route. The ordered ladder is now
exhausted; do not invent unrelated overnight scope.
