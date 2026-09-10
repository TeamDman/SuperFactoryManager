# Release review exact coverage acceptance

**Plan status:** Complete — bounded RCOV-A through RCOV-E slice
**Primary implementation root:** `D:/Repos/Minecraft/SFM/repos2/1.19.2`
**Last updated:** 2026-09-05
**Intent audit:** Passed 2026-09-05 against the current north-star request,
the proposed bounded goal, the user's explicit approval, and the parent plan's
preserved requirements. Earlier conversation implementation claims are not new
runtime evidence.

## How to update this plan

`[ ]` not started; `[~]` active; `[x]` verified complete; `[!]` blocked with
specific evidence. Update headings and adjacent completion notes together.
Only one implementation item is active. This supplement owns the current
bounded goal; `global comment selection and review sessions plan.md` retains
the full design ledger and future scope.

## Confirmed direction and traceability

| ID | Requirement | Work / proof |
| --- | --- | --- |
| RCOV-1 | Partial approval must not count as approval of an entire changed surface. | RCOV-A interval regression and Java/Rust query/completion parity. |
| RCOV-2 | In game, select a real change, comment, see the annotation and navigate what remains. | RCOV-B source-mapped remaining regions and independent syntax/diff/comment styles. |
| RCOV-3 | Stop the game halfway and resume from a portable review file. | RCOV-D two separate JVM launches over disposable real-source evidence. |
| RCOV-4 | Validate pending bounded icon edits and reconcile contradictory plan statuses. | RCOV-C compile/tests/render evidence; do not grow a new presentation engine. |
| RCOV-5 | User is on a phone with a locked desktop; virtual puppet inputs must not move the OS cursor. | RCOV-D virtual-input source guard, runtime attempt and View Image evidence. Rendering limitations remain explicit. |
| RCOV-6 | Preserve maintainer reviews, dirty user work and frozen dependencies. | Hashes below, scoped patches, no attestation, no push, no new dependency or clone. |
| RCOV-7 | Ready-to-test handoff, not an installer chore. | RCOV-E final executable/source freshness, exact launch, guide and screenshots. |

### Intent audit evidence

- Extraction: the seven requirements preserve the approved four-part goal,
  locked-desktop caveat, unchanged maintainer evidence and bounded non-goals.
- Traceability: each requirement has a task and an observable proof; previous
  foundations are reused rather than relabeled as new implementation.
- Adversarial pass: raw hashtag intersections are not full coverage; before
  approval cannot silently approve after; a fixture reopen is not a JVM restart;
  automated approvals never edit the maintainer's real review. Broader icon
  rules/AI enrichment, large-file editing and cross-version approval remain out.

## Established foundation and decisions

- At initial inspection, Java `SFMReleaseReviewKernel.hashtagUnits` used
  `unitsIntersecting` for both raw tags and effective approval; completion
  subtracted whole unit IDs. RCOV-A repaired this violation of the parent's
  every-changed-before/after-surface contract.
- Keep raw `#approved` queries useful as unit intersections; distinguish full
  effective unit coverage and exact remaining regions. A unit requires all
  nonempty declared before/after ranges, unioned by pinned document revision.
  An absent side is not a second required copy. Unsupported/unmaterialized
  surfaces cannot become approved from arbitrary same-file overlap.
- Union exact current approvals before subtraction. Normalize overlapping and
  adjacent half-open intervals. Unicode boundaries remain validated by the
  existing document/session layer. Empty selections do not approve bytes.
- Blocking and unresolved evidence remains conservative; do not weaken existing
  release safety. Version changed completion semantics so old attestations
  cannot remain valid merely because the serialized comments did not change.
- Java and Rust own equivalent evaluation contracts; inspect both before
  changing outputs, and keep CLI/game parity. Persist existing comments without
  rewriting targets to fit the stronger calculation.
- Recorded September 3 puppet evidence proves a small fixture comment/query
  loop and same-process reopen. It is not fresh locked-desktop evidence.
- Virtual pointer calls Minecraft mouse handlers through `MouseHandlerInvoker`;
  it does not call `glfwSetCursorPos` or inject OS mouse movement.

## Scope and execution

RCOV-A -> RCOV-B -> RCOV-C -> RCOV-D -> RCOV-E. No optional stretch is claimed;
finish this goal at a verified checkpoint. Multi-session expansion, generic
Explorer home/help, semantic icon rules/AI caches and huge JSON virtualization
remain with their parent tasks.

### [x] RCOV-A Reproduce and repair whole-unit approval from partial overlap

**Progress evidence (2026-09-05):** Canonical JUnit run first reproduced
`A one-byte approval must not approve the rest of the changed method` (expected
false, actual true) in `rcov-red.ndjson` / the runTest console. Java now unions
exact revision-qualified approval intervals, subtracts blockers and publishes
required/approved/remaining witnesses. The focused release-review suite passed
117/117 (including presentation tests) after correcting legacy after-only
completion expectations. The source fixture and maintainer files were not
rewritten. Rust's 18 focused release-review tests pass. Final `check-all.ps1`
passed policy, formatting, all-feature Clippy, build, 679 library tests
(3 ignored), and 52 integration tests including the Java scenario suite.
One older CLI readiness fixture now explicitly approves both changed sides.
Final installation is recorded under RCOV-E.
Hash domain becomes `sfm.release-review/1:semantic-state:exact-coverage/2`;
CLI derived query/status outputs become version 2, not the durable ledger.

**Work:** Add failing small-surface tests to the release review evaluator;
implement normalized pinned range union/difference and full-unit coverage;
expose exact remaining witnesses without losing stable work-unit queries.
Cover disjoint/multiple approvals, one-byte gaps, boundaries, both sides,
stale/migrated targets and independent blockers. Audit Rust parity and version
the completion semantic hash domain if needed.

**Validation:** From the primary root:
`sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMReleaseReview --log-filter info --log-file platform/minecraft/build/rcov-tests.ndjson`.
Record red-before-green evidence. Rust changes use locked/offline focused tests
and `platform/cli/sfm-propagate-changes/check-all.ps1`.

**Completion:** A small approval leaves the rest navigable and prevents
completion; a union covering the required domain succeeds only without blockers.

### [x] RCOV-B Make exact remaining work visible and usable

**Completion evidence:** The final `sfm:title_screen_release_review_explorer_ux`
run passed at `3840x2130@4`, including the CLI artifact export (exit 0), in
`sfm-title_screen-20260905-151759-429`. Its
`artifact_release-review-diff-presentation.json` verifies 53 styled glyphs in
the text diff, 30 in the structured diff, and two independent change
backgrounds. `figure_01_release-review-text-diff.png` and
`figure_02_release-review-approved-comment.png` were inspected with View Image:
source colours, red/green backgrounds, persistent comments, usable pane widths
and title-screen chest containers are visible. Comment selection/save, Comments
lens refresh and same-file reopen passed. The exporter caught an earlier
test-only duplicate artifact emission during capture polling; the driver now
writes that artifact once, after capture completes. The final run contains no
duplicate report. RCOV-D supplies the real-source integrity proof.

**Integration repair evidence:** The first live independent-syntax
assertion correctly failed although the pure source-map tests passed. Logs
showed both original Java requests succeeded, but the canvas rejected their
result because its projection drops unpainted terminal line endings. A pinned
glyph mapper now verifies every scalar, glyph position and row against the
immutable source, retaining CRLF/Unicode byte coordinates without treating
different content as equivalent. A regression exercises leading/trailing
blank rows and rejects changed content. Final visual validation above passed;
stale-layout publication is diagnostic-only rather than a render-thread crash.

**Additional real-path regressions (2026-09-05):** A visible filtered review
unit beyond ordinary pagination could not expand: action authorization searched
only the ordinary relation. It now uses the current root/query/generation's
filter projection, with a stale-query rejection test and no contamination of
ordinary pagination. Explicitly expanded contextual groups beneath a matched
unit also retain their lazily loaded children. A focused nested-group test
passes. The live driver uses row selection plus Enter to avoid relying on a
double-click deadline during cold projection work; no OS pointer is moved.

The nested context-group assertion now covers both loader relations and actual
visible rows; the focused action family passes 6/6. Live real-source evidence
already proves a 1-byte approval leaves 284 of 285 required bytes unreviewed:
79 Before bytes plus 205 remaining After bytes, pinned to distinct revisions.
The artifact is `title_screen_exact_release_review_stage__exact-review-partial__1920x1080_2.json`.
This initial partial evidence is superseded by the separate-process
stage/resume proof recorded under RCOV-D.

The arbitrary-note step exposed two real V1 focus defects:
`mouseClicked` returned false before acquiring focus when no rendered content
existed. The bounded fix gives that empty area focus and handles the click.
Its render method also called a legacy tooltip helper which cleared all widget
focus whenever the editor was not the top-level Minecraft screen. That is always
true in the multiplexer. A diagnostic capture proved the first character arrived
and the next frame left the current focused child with `isFocused=false`.
The editor no longer mutates input focus while drawing tooltips; this is not a
speculative resize fix. The live note step types across separate rendered frames.
The next save check exposed V1 polling physical Shift state rather than the key
event modifiers; Shift+Enter now uses the event flags, with an ordinary/shifted
Enter/keypad-Enter regression test. No OS key injection is necessary.
The test retains the profile's preferred V1 editor and uses its centered input
area and Shift+Enter save, rather than silently switching preference to V3.
The draft title no longer advertises a universal Ctrl+S binding; the guide
distinguishes V1 from V3. RCOV-D now validates this typing/save path without
changing the preferred editor to V3.

The actual save diagnostic then exposed `Duplicate selected-proposal id` when
an approval and a note reused an exact capture. The new small regression first
failed with that error (three other draft tests passed). New comment bindings
now qualify their durable proposal identity by comment, preserving the original
rule, witness, semantic provenance and projection fingerprint. Existing saved
bindings are untouched; migration can still address each binding independently.

The final full Java run (`rcov-all-tests-completion-v2.ndjson`) found 1,696
tests: 1,689 passed, six failed, one property-gated integration aborted.
Legacy Before/After-child and partial-approval expectations were corrected;
the nested filtered-context test now executes the actual NodeExpand action,
and the pinned-glyph/source-frame guards pass. Six failures reproduce the already documented
`net.minecraft.locale.Language` missing default-resource initialization cascade
from RCS-UX6b1 (August 24), not a new approval regression. They are
`SFMOverlayActionGrammarTests` (one), `SFMClientActionDispatcherCompilerTests`
(three), `SFMSpatialCoverageRunActionTests` (one), and
`SFMTerminalPropertiesActionTests` (one). The opt-in installed symbol-server
test aborts because its executable/branch properties were not supplied.
The complete suite is not green; no resource shim or relaxed assertion hides
these failures. Full output remains in the `runTest/console.log` artifact.

**Progress 2026-09-05:** Query/status unit rows now contain an Unreviewed surface
group with exact revision-qualified source links and honest per-unit byte
counts. A 49/50-byte test navigates the precise missing byte. The locked-desktop
baseline puppet ran successfully at 3840x2130@4, but its screenshot revealed
neutral diff text and impractical pane widths. Generated diff backgrounds,
source-mapped comment underlines/gutters, and independent original-Java syntax
requests are now implemented and tested. Syntax requests fan out per source;
each result can publish without waiting for the other side. Snapshot identity
and panel-close cancellation remain enforced. Non-Java unsupported foregrounds
stay neutral; no grammar or dependency was added. Final runtime proof is above.

**Work:** Connect coverage witnesses to existing query/status Explorer rows and
source navigation. Verify existing comment decorations and value/selector/matches
inspection, repair gaps, and retain independent syntax/diff/comment layers.
No color-only status and no new opaque active-review authority.

**Validation:** Pure model and action tests plus RCOV-D natural input journey:
select/comment, see text and decoration, query remaining, open an uncovered
range, prove no unselected source was approved. Use practical pane widths.

**Completion:** The user sees what their comment covers and what still needs
attention without relying on diagnostic logs or memorized command strings.

### [x] RCOV-C Close bounded icon validation and plan-status discrepancies

**Prerequisite repair:** The first canonical build exposed a root-entry String
versus SFMItemIcon mismatch and two duplicate Java pattern variable names in
SFMClientThemeLoader. These three pending-icon compile errors are repaired;
main/gametest/datagen/test compilation passed during RCOV-A. The full JUnit run
passes the theme, icon policy, resolver and presentation families; the successful
`sfm-title_screen-20260905-122301-336` title-screen Explorer artifact shows
preferred chest containers at `3840x2130@4` on the locked desktop. View Image
inspection completed. X-8d is closed with this evidence; X-8e contextual Help,
typed subjects and optional asynchronous enrichment remain deliberately open.
RCS-UX6b/UX6b1 umbrella status is not promoted to complete from this bounded proof.

**Work:** Compile the preexisting icon/theme edits, repair only necessary
regressions, and verify title-screen container icons preserve the requested
fallback. Reconcile parent headings with their adjacent evidence; preserve
unfinished breadth as unfinished rather than marking whole umbrella tasks done.

**Validation:** Item icon/theme/explorer focused tests, canonical compile,
virtual-pointer source tests, title-screen screenshot and full Java suite.

**Completion:** Pending icon edits have current build/runtime evidence and the
plans accurately distinguish complete bounded slices from future work.

### [x] RCOV-D Prove real-source review across two game processes

**Completion evidence (2026-09-05):** The corrected render-boundary stage
passed in JVM 42016 (`sfm-title_screen-20260905-150342-182`), followed by resume
in JVM 12656 (`sfm-title_screen-20260905-150704-080`). Both commands exited 0.
The latter has `artifact_exact-review-resumed.json` and
`artifact_exact-review-navigation.json`: the Before SFM.java link selects
exactly bytes `[2099,2178)` in revision
`git-document:sha256:3b3f47ef94660a8bbd79c28269737ec5a8109e03a24f606cad531253be5bf135`.
The two independent comments, work cursor, semantic hash and portable file hash
are identical after removing only the local recovery/lease cache. Screenshots
now show the expanded range children and the actual target editor, rather
than the prior framebuffer. A later wider-layout resume is an additional
handoff capture, not a replacement for the two-process integrity proof. It also
passed (JVM 43116), exported as `sfm-title_screen-20260905-151611-643`.
`figure_01_exact-review-resumed.png` was inspected via View Image and shows
the source in a majority-width pane with the exact remaining region selected.

The installed Rust `review session query --file <disposable file>
'remaining intersect 1.19.2 HEAD' --output-format json` also passed an explicit
PowerShell equality comparison against Java's `exact_coverage_json` witness,
normalizing only field spelling and range ordering. All required, approved and
remaining revision-qualified ranges, and boundedness, match. Querying leaves
the portable file unchanged. The derived output schema is
`sfm.release-review-query-result/2`; the target has 285 required bytes,
one approved byte and 284 remaining bytes in three ranges:

| Pinned side of SFM.java | Remaining UTF-8 bytes |
| --- | --- |
| Before | `[2099,2178)` |
| After | `[4518,4527)` |
| After | `[4528,4724)` |

These are automated comments on a disposable copy, not human approval.

**Intermediate evidence:** Stage and resume both passed their model/persistence
checks in JVMs 33924 and 42868, exported as
`sfm-title_screen-20260905-144201-161` and
`sfm-title_screen-20260905-144558-307`. Their identical portable hash was
`97c4e1e38271674386c69ddc181fb2267ca1c1e3ff6dced9eeb8f1413ca3451f`;
approval and plain note persisted independently. Inspection then caught stale
framebuffer screenshots: the runtime waited one client tick, which does not
guarantee a rendered frame on a background/locked desktop. Capture now waits
for a completed RenderTick END after HUD preparation. This has a source guard
(JUnit excludes gametest classes) plus real runtime validation. Reruns prove
the visual state, not reuse these old images as
proof of final navigation. The navigation assertion also verifies the pinned
document revision, not just matching numeric offsets, and writes an exact-range
artifact. The early raw stage artifacts may be cleared by a later puppet;
retain evidence via the exported preview run directories.

**Work:** Create isolated acceptance evidence from pinned real 1.19.2 changes;
never use the maintainer review as a writable test target. A self-orchestrating
virtual-input puppet creates partial approval and another note, captures
remaining/source/comment views, and stops. A second JVM opens the saved file
without reinitializing it and verifies comments, queries, hashes and resume.

**Validation:** The registered acceptance IDs are
`sfm:title_screen_exact_release_review_stage` and
`sfm:title_screen_exact_release_review_resume`. Run stage and then resume as
separate CLI invocations/JVMs with `--branch 1.19.2 --variant 1920x1080@2
--log-filter info --log-file platform/minecraft/build/rcov-real-stage.ndjson`
(use `rcov-real-resume.ndjson` for resume). They reuse the existing real-review
staging/authority-hash helpers, not the maintainer ledger as a writable target.
The stage targets a real pinned SFM.java hunk; the resume removes only its
rebuildable machine-local recovery/lease files before reopening the staged copy.
Additional presentation proof uses the registered review Explorer puppet at
`3840x2130@4`. Record actual ID/commands before
execution; no unregistered command is presented as runnable. Capture PNGs and
structured reports, view the PNGs. Verify distinct JVM identities and unchanged
protected hashes. Locked-desktop rendering failure is diagnosed, not hidden.

**Completion:** Persisted partial approval survives process death/restart with
identical exact uncovered regions and no OS cursor movement.

### [x] RCOV-E Hand off current tools and a short phone-readable walkthrough

**Completion notes:** `docs/release review exact coverage guide.md` contains
the actual start command, disposable review path, mouse/keyboard commenting
steps, raw/effective/remaining distinction, exact-gap navigation, preferred
V1/V3 save controls, restart commands and final screenshot links. The gameplay
changelog records coverage, note input, distinct selector IDs, independent diff
styles and filtered-context actions. Rust checks/install passed after the last
Rust edit; later changes are Java/tests/docs. The installed executable's hash
was rechecked after final live validation. `User must run install.ps1: no`.
All in-scope game/symbol/syntax processes have exited (CIM check: zero);
no game is intentionally left running. Start testing with
`sfm-propagate-changes.exe run client --branch 1.19.2`.

The final full Java result is 1,689/1,696 passing, six previously documented
resource-initialization failures and one opt-in integration abort, as detailed
under RCOV-B. This is not release clearance. Audit exits 0 but reports 348
unresolved rule-call warnings for `GuiGraphicsExtractor text`; those warnings
remain visible, not converted into a clean audit claim. Both maintainer review
hashes and the staged portable hash are unchanged after all puppets. Dependency
manifest/lockfile status is clean. `git diff --check` passes when excluding the
preexisting August 24 generated language-cache metadata, whose two trailing-
whitespace lines were deliberately preserved. No commit, push or maintainer
attestation was made. Validation is on 1.19.2, not an implied cross-version
propagation. The parent review-workbench and X-8e breadth remains open.

**Work:** Update gameplay changelog, concrete manual guide and plan evidence.
Run final relevant full tests; install any changed CLI after its final mutation.
Report installed path/hash, cache restoration, process states and exact manual
launch. Share fresh images and explain the partial-approved/remaining story.

**Completion:** No unmentioned installation step, stale binary, or claim of
human release approval. All core tasks verified, or real remaining obligations
explicitly retained as active rather than claimed complete.

## Operational readiness and preservation

- Guidance: `goal execution and testing readiness guidelines.md` applies;
  bounded lifecycle control only for proven SFM test processes.
- Baseline branch/commit: `1.19.2`, `16328629fa60c45a4f525b6f20aaa77715f91077`.
- Large dirty worktree predates this goal; preserve all unrelated edits.
- Protected SHA256 `docs/reviews/4.34.0-1.19.2-to-58ed4e431.sfm-review.json`:
  `998F256C668FF08F16008CADDDC39356111F057327CD9DE597BB6166D3AB0171`.
- Protected SHA256 `platform/minecraft/run/manual-test.sfm-review.json`:
  `9ADDCCEC595CAA916EFDB8019270F62D577B336508D2608D84BDA2FE7AADD592`.
- Initial CLI: `G:/Programming/Caches/CARGO_HOME/bin/sfm-propagate-changes.exe`,
  SHA256 `5445A354999C66CD5D6BAEC18986F8EDC90229AE41BDF18A14BAADF2FA52350F`.
- Initial Java/SFM CLI process check: none found. Locked-desktop virtual-input
  rendering succeeded: `sfm-title_screen-20260905-122301-336`, baseline review
  Explorer puppet, 3840x2130@4. Startup errors in latest.log were third-party
  mixin metadata, AE2 guide configuration and a missing Industrial Foregoing
  texture; no SFM puppet failure was emitted. Fresh real acceptance and the
  final presentation run are recorded under RCOV-B/RCOV-D.
- Dependencies frozen; no new dependency/declaration/lockfile edits or repos.
  Deterministic cache rehydration under existing locks remains allowed.
- Final Rust `check-all.ps1` passed and `install.ps1` completed offline on
  2026-09-05 after the final Rust edit. Installed executable remains at the
  normal Cargo bin path; SHA256 is
  `CAE8E332529A02241F6F70CDD1E3A75616BD81AE7968E9E27C99CB925C826E6C`.
  `User must run install.ps1: no`. Subsequent edits are Java/test/docs only.
  Final game processes are stopped; completed runtime evidence is above.
- Final `audit --branch 1.19.2` returned exit 0 with 348 unresolved audit-rule
  call warnings (`DENY CALL net.minecraft.client.gui.GuiGraphicsExtractor text *`).
  This is not a zero-warning claim. Both protected hashes were rechecked unchanged;
  `git status --short -- '*lock*'` remains empty.

## Risks

- Unit intersections masquerading as coverage: adversarial partial-range tests.
- Existing fixture assertions encode old semantics: update expectations with
  explicit before/after witnesses, never broaden saved human selectors.
- Derived byte counts double-count overlapping units: normalize by revision;
  label work-unit and byte-surface counts distinctly.
- UI regressions hidden by screenshots: assert semantic artifacts and natural
  actions as well as inspect practical-layout images.
- Read-only sandbox outside G: request normal scoped elevation for D repository
  patches/build/cache access; never use filesystem workarounds.
