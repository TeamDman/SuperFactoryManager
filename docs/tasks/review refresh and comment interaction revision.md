# Review refresh and comment interaction revision

**Plan status:** Complete — verified 2026-09-08
**Primary implementation root:** `D:/Repos/Minecraft/SFM/repos2/1.19.2`
**Last updated:** 2026-09-08
**Intent audit:** Passed against the latest freshness/comment screenshots and request.

## Update protocol

`[ ]` pending, `[~]` current focus, `[x]` verified, `[!]` externally blocked.
Keep one focus and put evidence beneath its owning task. Source changes alone
are not proof of in-game availability. This plan continues ND-4 through ND-6
in [the navigation/diff plan](explorer%20navigation%20and%20review%20diff%20presentation%20plan.md);
it does not claim those unfinished tasks were completed.

## Requirements and traceability

| ID | User requirement / integrity constraint | Task |
| --- | --- | --- |
| RC1 | Freshness warning needs an accessible way to include latest changes. | 1 |
| RC2 | Explain working-tree capture mismatch separately from an exact ambient HEAD; empty change lists must not mislead. | 1 |
| RC3 | Preserve old comments and approvals against their original snapshots; no implicit approval of new bytes. | 1, 4 |
| RC4 | Investigate and eliminate one-Minecraft-pixel highlight line artifacts. | 2 |
| RC5 | Explain the R marker: decoration is not the comment value. | 2 |
| RC6 | Right-click highlighted regions offers open comment text and reveal comment in Explorer separately. | 2 |
| RC7 | Before/after document rows expose their comments as expandable children. | 2 |
| RC8 | Preserve click-and-drag text selection, including overlapping comments. | 2, 3 |
| RC9 | Explicit inline labels and left/right split text and structured diff leaves in one canvas. | 3 |
| RC10 | Update plans, set goal, finish a major revision with a concrete in-game testing flow. | 4 |

## Intent audit evidence

- Extraction: latest original request, JSON, row details and two screenshots
  identify RC1–10. The user review is `platform/minecraft/run/review-2026-09-07.sfm-review.json`;
  the illustrated source is `docs/architecture/evidence/clean-loader-jarjar-probe-1.19.2.full.evidence.json`.
- Traceability: all atoms map to tasks below; preservation is an acceptance gate,
  not an assumption that retargeting old approval is safe.
- Adversarial pass: separate background seams from intentional underlines;
  separate marker, comment text and selector; separate opening from revealing;
  preserve drag selection and current inline views. Split means one canvas, not
  two unrelated editors or renamed inline output.
- Source limitation: prior implementation status comes from the existing ND plan
  and repository inspection; its pending runtime checks remain pending.

## Verified foundation and decision gates

- `SFMReviewLensSetAction` has a new-working-tree capture continuation, but the
  warning needs its own discoverable route. Decide continuation versus explicit
  successor migration using existing capture/migration APIs before implementation.
- `SFMReleaseReviewCommentDecorations` attaches interactive comment IDs, values
  and source ranges. Persisted style rules can override gutter and underline.
- Rust `release_review_materialize.rs` and
  `release_review_working_tree_materialize.rs` generate an `R` gutter style.
- Canvas hit testing exists; investigate why current marker/context behavior is
  not discoverable or functional before adding parallel UI machinery.
- Split layout must close the existing origin-based `projectPinned` geometry
  assumption with exact side/source mapping before allowing comment capture.

## Work contracts

### [x] 1 Make newer-change capture actionable and truthful

**Investigation/source checkpoint:** HEAD is `16328629fa60c45a4f525b6f20aaa77715f91077`.
User review SHA-256 remains `52E8EE6E700B0090C7D8612C93D84393ED21FC39801F9047D59C9F3CEB42D306`.
Freshness choices now share a new-capture continuation with the lens menu, using
a collision-resistant sibling filename. This is not yet a complete retarget or
migration workflow and is not live-GUI verified. Focused tests are recorded at
`platform/minecraft/build/rc-20260907/action-tests-1.ndjson`.
Rust `review_cli.rs` sets scope mismatch/source_dirty while leaving changes empty;
diagnostic clarification now explicitly says the changes array is not a per-path
capture comparison. A fixture mutates scoped disk content while retaining HEAD
and verifies both the mismatch and the clarified HEAD diagnostic. Required Rust
check-all is running (`rust-check-all-1.log`); final installation is pending.

**Subsequent capture-policy checkpoint:** the user's actual capture scopes `.`
and excludes its original review JSON. `sfm:review/freshness/capture <lane>` now
uses the checked repository root, carries all saved scopes/exclusions and
tracked-only policy, additionally excludes the original review path, and creates
a unique sibling file. The existing continuation is explicitly the alternative
custom-scope route. Generic create requests retain their previous constructor
and command grammar. New tests inspect the emitted scope/exclusion arguments.
Full Java run 3 passed **1,945 tests, zero failed, three opt-in aborts (1,948 found)**
in `full-tests-3.ndjson` after these edits. The remaining gate is live GUI evidence.

Rust check-all completed successfully: 700 unit tests, integration suites 10,
12 and 40 passed, zero failures, three explicit ignores. `install.ps1` completed;
installed CLI `G:/Programming/Caches/CARGO_HOME/bin/sfm-propagate-changes.exe`
reports revision `16328629f`, built `2026-09-07 23:22:17 -04:00`, SHA-256
`63583BCFD8EFA93CE916DB449E9507F16D26FE56F55C16DF1F0ED17A0F5D8C1E`.
Evidence: `rust-check-all-1.log`, `rust-install-1.log` in the RC build directory.
No dependency declarations/lockfiles changed. No user game was running at
preflight; only owned tests were running. No client launched yet.

Inspect freshness and capture APIs, expose a warning/context action with a clear
destination and snapshot semantics, preserve existing evidence and show loading,
success, failure and resulting file actions. Test actual choice availability,
working-tree-only edits, new commit, unchanged capture and failed capture.
Completion: a mouse-driven warning-to-newer-review journey succeeds on a disposable
review and original file hashes remain unchanged.

### [x] 2 Make comments visible, readable and reachable

**Investigation:** R is the generated `#release-change` style marker, not comment
text. Its null underline override inherits ordinary blue underline per line.
Current left gutter click already opens comment section choices; right-click
ignores decoration objects and goes directly to generic context projection.
Preserve the existing gutter path while adding the requested flat region actions.

**Source/test checkpoint:** highlighted-region right-click now opens the comment
choices; `text` opens its immutable literal value directly, and `reveal` navigates
to its value in the Comments lens without opening that value. Generic current
selection actions remain a separate choice. Source rows have collapsed comment
children populated from evaluated revision ranges, with complete text values.
Generated comment prose is searched by role in the Changes lens so incidental
language names do not auto-expand every filename match. Generated release-change
styles no longer inherit a default underline (explicit saved overrides remain).
Vertical background boundaries share floor rounding, tested over negative pan
and fractional zoom. Full Java run 1 caught three regressions: old leaf/menu-count
expectations and the real generated-prose filter regression; run 2 after fixes
passes **1,943 tests, zero failures, three opt-in aborts (1,946 found)**. Includes
exact-revision comment child tests and raster adjacency tests. No GUI proof yet.

Inspect exact R style and seams; fix geometry/style based on evidence. Add region
context actions, document comment children and marker explanation with useful
comment previews. Reuse canonical actions. Validate Unicode/endpoints, overlapping
comments, selection dragging, exact document identities and stale publications.
Completion: open a highlighted comment value and reveal it via independent actions;
expand document comments and inspect readable text; no highlight stripe artifact.

### [x] 3 Finish inline and split diff presentation

**Current focus:** `SFMReleaseReviewSplitLayout` is the pure, source-addressed
two-column row model. It validates the generated surface first, separates each
mapped source slice into exact lines (including CRLF), pairs deletion/addition
blocks, and preserves independent before/after identities even for equal text.
Selections stay on their starting side and return exact source ranges plus copied
bytes, never synthetic separator/padding text. Row growth is bounded.
The initial foundation is now wired to a single split canvas and explicit inline/
split Explorer leaves. `Recipe.split` separates generated-document identities
without changing the worker's source-mapping protocol. Pointer drags remain on
their starting side; copy and context projection use the original source bytes.
Source syntax and interactive comment decorations use each cell's inline source
map range. This integration is not yet GUI-verified.
The Unicode/multiline test passed on run 1; the missing-side fixture incorrectly
declared MODIFIED instead of ADDED/DELETED and was corrected. Run 2 is in
`split-layout-tests-2.ndjson`: **two passed, zero failures**, current-source
compile succeeded. This proves the pure row/selection contract, not UI behavior.

**Current validation:** focused run 3 passed both layout tests and compiled the
renderer. Full run 4 found six stale four-leaf/old-label expectations, updated to
assert six leaves plus distinct split recipes. Full run 5 passes **1,947 tests,
zero failures, three opt-in aborts (1,950 found)**. Subsequent minor edits retain
structured fallback diagnostics on canvas and disable incorrect inline-geometry
symbol hits in split mode; the GUI launch recompiles these edits, with a final
full suite still required. `runtime-1.ndjson` launches the disposable GUI2 test;
the copied review is `build/rc-20260907/disposable-review.sfm-review.json`. The
original review hash remains 52E8EE6E700B0090C7D8612C93D84393ED21FC39801F9047D59C9F3CEB42D306.
Check keyboard selection, structured fallback visibility, source-side context
projection, and empty-column interactions before live GUI evidence.

**Remaining integration checks:** retain this cell/source model in one pan/zoom canvas;
render columns with measured font widths, feed source selection to existing
context actions, and source syntax/comment styles to cell spans. Do not pad an
ordinary linear document then allow a left-column drag to capture right-column
bytes. Add explicit inline/split leaves and exact layout cache identity only
when the renderer and selection integration exist. Preserve diagnostic/fallback
information from structured output, and test unchanged-context alignment.

Continue ND-4/5: labelled inline/split text and structured leaves, one canvas with
before/after identity, aligned rows, syntax foreground and independent change
backgrounds. Test empty sides, Unicode, multiline, copy/selection and source-mapped
comments. Completion requires both sides to target the correct immutable bytes.

### [x] 4 Verify and hand off

**Live GUI2 findings (session dcf35da4-a621-4add-acfd-2b81d2f972a1):**
Opened the disposable 1,846-comment review through palette+Enter. Banner context
menu exposes the new capture action. Discovered Java freshness parsing treated
the new informational `capture.scope-changed` diagnostic as UNKNOWN; fixed in
source with matching-scope informational allowlist and fail-closed regression
checks, not yet rebuilt into this running game. The capture action successfully
created/opened `build/rc-20260907/working-tree-7226cafd-ace6-44b9-8a11-6ac82f8b43e9.sfm-review.json`
(31,451,710 bytes). Its long capture needs persistent progress feedback: the
context palette closes and its feedback otherwise disappears. This is required
before handoff. Source changes after full run 5 still need final compilation/tests.

Steps 19–22 verified six source/diff leaves and loaded a syntax-coloured,
green-background split diff for the added JSON evidence file. Steps 28–30 verified
highlight right-click offers separate open-text/reveal actions and opens the
actual generated `#release-change` value. Additional required usability fixes:
initial split framing must not show only an empty Before column in a narrow panel;
clicking that empty side must still focus the panel; repeated open-to-right for
review/comment previews must not produce progressively unusable narrow columns.
Step 31 requests normal puppet finish. GUI4, exact source selection/comment
creation, reveal action execution, seam checks and final tests remain pending.
The test JVM exited normally (14m49s launcher duration). Durable evidence:
`platform/minecraft/build/sfm-toolchain/artifacts/game-test-preview/runs/sfm-title_screen-20260908-000535-411/index.html`.
All 31 request/response pairs remain in the session directory; screenshot copies
are under its `evidence` directory. User review checksum is unchanged after exit.
No build or game process remains owned by this test run. Goal remains active.

**Follow-up implementation:** capture now reuses operation-lifetime persistent
toast feedback (negative capture IDs avoid falsely exposing persistence-cancel).
New review views and comment values use existing-area tabs. Split initial/fit
framing uses only present source sides; empty-side clicks consume input so focus
does not remain in another panel. Full run 6 is validating these changes plus the
freshness diagnostic classification regression. Changelog and draft guide now
describe split leaves and pan/zoom/fit/copy semantics, pending live validation.
Full run 6 completed: **1,947 passed, zero failed, three opt-in aborts**. No Java
process remained at the next preflight. `runtime-2.ndjson` is the GUI4 launch
using the updated Java source and changelog resources.

Run current-source focused and full Java tests, datagen, and disposable virtual
GUI exploration at GUI scales 2 and 4. Rust changes require check-all.ps1 plus
final install.ps1 and executable hash verification. Update changelog and guide
with the actual mouse/keyboard flow, evidence and limitations. Never operate the
OS pointer or alter user review data during tests.

Root commands (add unique log paths under `platform/minecraft/build/rc-20260907`):

```powershell
sfm-propagate-changes.exe test run --branch 1.19.2 --wait-for-build-lock --log-filter info --log-file platform/minecraft/build/rc-20260907/full-tests.ndjson
sfm-propagate-changes.exe run data --branch 1.19.2 --wait-for-build-lock --log-filter info --log-file platform/minecraft/build/rc-20260907/datagen.ndjson
sfm-propagate-changes.exe puppet run sfm:title_screen_exploratory_review --branch 1.19.2 --variant 1920x1080@2 --wait-for-build-lock --log-filter info --log-file platform/minecraft/build/rc-20260907/runtime.ndjson
```

## Operational readiness

### GUI4 checkpoint — 2026-09-08

Virtual-input session `5c36a28a-0ad7-4a83-a0d4-ecacb5a4c85f` finished normally.
Evidence: `platform/minecraft/build/sfm-toolchain/artifacts/game-test-preview/runs/sfm-title_screen-20260908-002312-893/index.html`.
Confirmed old capture is OUTDATED, capture progress persists while filtering remains
usable, and the newly captured review opens as a same-area tab with a current-scope
freshness result. Original review checksum remains unchanged. Expanding an after
row exposes its comment child, but discovered activation regression: double-click
expanded children rather than opening the source. Added explicit primary-open
entry metadata separate from expandability; review leaves attest it and body
activation respects it, while chevrons still expand. Two regression tests added;
full run 7 is in progress. Must repeat runtime activation after rebuild.

Remaining correctness audit: split Ctrl+A/arrows currently reach hidden inline
canvas selection logic. Route selection/navigation to the split source model
before claiming keyboard fidelity. Exact source comment creation, reveal execution,
fractional seam screenshots, final GUI2/GUI4 and datagen remain outstanding.

Follow-up: full run 7 passed **1,949 tests, zero failures, three opt-in aborts**.
Split keyboard selection now routes to its source-side layout, with Unicode-scalar
arrows, Shift extension, word motion, Home/End, Ctrl+A preserving original endings,
and a visible caret. Hidden inline-model shortcuts are no longer dispatched in
split mode. Added pure layout navigation/exact-side tests. Full run 8 is validating
this follow-up; live proof is still required, not implied by this implementation.
Full run 8 passed **1,950 tests, zero failures, three opt-in aborts**. Datagen
`datagen-1.ndjson` exited successfully in 50.7s (no generated files written).
User review SHA remains `52E8EE6E700B0090C7D8612C93D84393ED21FC39801F9047D59C9F3CEB42D306`;
installed CLI SHA remains `63583BCFD8EFA93CE916DB449E9507F16D26FE56F55C16DF1F0ED17A0F5D8C1E`.
No dependency declaration/lockfile diff. `runtime-3.ndjson` now launches GUI2
current source for activation, exact selection, comment reveal and seam checks.

### GUI2 interaction evidence — runtime 3

Session `f4bac85a-1ea2-47d6-8518-42032d8aae8d` exited normally after 26 requests.
Durable gallery: `platform/minecraft/build/sfm-toolchain/artifacts/game-test-preview/runs/sfm-title_screen-20260908-004036-669/index.html`.
Steps 14–17 executed Reveal comment in Explorer: correct value row selected in
Comments and split editor retained. Steps 18–25 selected all in the split After
column, invoked exact byte comment through Alt+Enter, and saved a needs-change
comment. Disposable review advanced 1846→1847 comments, generation 2→3, dirty=false.
Persisted `human:release-review:1` contains 18 literal ranges [0..944) on
`review-document:sha256:d9b2bb8155dd72fc2e61844d20aa5b3d771f7badbd3cd607003edb5b5153f5fa`,
the JSON After source, not generated diff text. Original review checksum unchanged.
Only launcher ERROR was third-party Industrial Foregoing missing texture.

Live discovery: `emitOpenSelected` independently rejected expandable entries even
after the panel fix. Updated this guard to `opensOnActivate()` and added a
panel+model action-emission regression using an expandable document fixture.
`activation-tests-1.ndjson` is validating it. Source opening/seam live proof still
requires the next rebuild. Also initial fit of very long JSON lines makes text
tiny; readable initial framing should be distinct from explicit Fit Width, while
retaining access to both split sides. Do not claim this usability issue resolved.

Follow-up: activation action-emission suite passed 14/14, covering panel Enter
and direct model preview emission for an expandable source. Initial split view
now uses normal reading scale at the first present source origin; explicit Fit
Width retains its whole-layout framing. Full suite 9 is running before GUI4.
Full suite 9 passed **1,951 tests, zero failed, three opt-in aborts**. GUI4 current
source launch is `runtime-4.ndjson`; launcher session 8725. The prior GUI2 test
process has exited. The disposable review intentionally retains the new test
comment for checking overlapping comments and resume in this run.

### GUI4 runtime 4 — activation verified, source hit-test discrepancy found

Session `c5a356db-9502-4a81-a578-082c711ded3d` completed normally after ten requests.
Gallery: `platform/minecraft/build/sfm-toolchain/artifacts/game-test-preview/runs/sfm-title_screen-20260908-005102-854/index.html`.
Reopened disposable review retains 1847 comments. Double-click After now opens
its source despite comment children. Source right-click still produced ordinary
selection actions instead of overlapping comment choices. Inspection found hit
testing used `currentDocumentText()` while rendering used `pointerCoordinateText()`:
reconstructed glyph text can omit terminal newlines and invalidate exact ranges.
All three decoration consumers now share `documentDecorationRows`, backed by exact
source text. Added a whole-source CRLF/emoji/terminal-newline cache regression.
Focused `decoration-tests-1.ndjson` is running. Source menu and fractional seam
proof must be repeated; red underlines in this run belong to explicit needs-change
styling and are not evidence of the former generated blue underline artifact.

### GUI4 runtime 5 — exact-source comment interaction verified

Session `b7fc3995-974e-48b3-ae3b-66dcd6f6dca4` finished normally after 53 virtual
requests; gallery `platform/minecraft/build/sfm-toolchain/artifacts/game-test-preview/runs/sfm-title_screen-20260908-010502-611/index.html`.
Steps 10–18 resolve two overlapping comments on the JSON After source, open the
human comment value in the existing tab area, and expand both comment children.
The saved comment's 18 slice hashes were checked against the exact 944 source
bytes: zero mismatches. Step 30 explains R on a generated-only Java Before;
step 35 preserves drag-selected `package` as exact source bytes [0..7).
Structured Java split uses Arborium with syntax foreground and added backgrounds;
this selected example only adds declarations, so an empty Before is expected.
Focused decoration suite passed 19/19 after the exact-source hit-test fix.

Live follow-up: Ctrl+Shift+9 was intercepted as tab 9 by the workspace. Restrict
tab-digit interception to plain Control; add regression checks for modified
digits. Full suite 10 is running. Final GUI2/GUI4 Fit Width, fractional source
highlight and two-sided structured selection proof remain pending.

Full suite 10 passed **1,953 tests, zero failures, three opt-in aborts (1,956
found)** after the source hit-test and shortcut fixes. GUI2 `runtime-6.ndjson`
launches the final interaction pass; owned launcher session 83154. Runtime 5
is stopped normally, with no remaining test client from that session.

### Final-source GUI2 runtime 6

Session `bb51719a-ac8e-4a35-8c07-539fef17b4f6` (JVM 38320) exited normally after
47 requests. Gallery: `platform/minecraft/build/sfm-toolchain/artifacts/game-test-preview/runs/sfm-title_screen-20260908-012025-311/index.html`.
Step 23 verifies Ctrl+Shift+9 fitting both structured Java sides, red Before and
green After with Arborium syntax foreground. Steps 24–38 create two disposable
comments on the changed method in `SFMTextEditScreenV2Registration.java`:
`human:release-review:2` has seven exact Before ranges [342..581), revision
`review-document:sha256:00e446e1bbc369299ec289ed68bac0067e0dca930a66324d662239955492f648`;
`:3` has eight After ranges [342..615), revision
`review-document:sha256:34b40b29eb7701731d12dd0c99a3ca76675d8a149c1ae2c6609ec4bec9c2abc8`.
All 15 persisted slice hashes match the corresponding original source bytes.
Step 41 verifies text split on the same two-sided change. Step 45 checks source
highlight at fractional zoom: generated blue backgrounds have no repeated blue
underlines; explicit needs-change red underlines remain intentional. Step 46
opens independent comment text/reveal choices from that source highlight.
Disposable review now has 1849 comments. The user review and installed CLI hashes
remain unchanged. No dependency declaration/lockfile diff.

Tasks 1–3 are verified by the cumulative evidence above; earlier pending language
is chronological checkpoint history, superseded by these later results. Task 4
is closing with final-source GUI4 runtime 7, final datagen and handoff audit.

### Final-source GUI4 runtime 7 and acceptance audit

Session `2e04ff1a-f2d1-4098-bd45-a95df6a7e1f1` (JVM 22468) completed 20 virtual
requests and exited normally. Gallery: `platform/minecraft/build/sfm-toolchain/artifacts/game-test-preview/runs/sfm-title_screen-20260908-012537-555/index.html`.
Reopened 1849 comments; steps 18–19 verify corrected Fit Width with red/green
two-sided structured Java, syntax foreground and saved comment decorations.
No OS pointer injection. Runtime 6 and 7 launcher sessions are both terminal.

| Requirement | Conclusive evidence |
| --- | --- |
| RC1 | Runtime 1/2 banner choices, actual sibling capture, persistent progress and successful opening. |
| RC2 | Rust fixture scope-only mutation plus Java informational-diagnostic tests; runtime 2 old/new freshness classifications. |
| RC3 | Original review SHA unchanged throughout; captures use new sibling files and retain old comments, no automatic approval transfer. |
| RC4 | Shared floor raster boundary tests; runtime 6 step 45 fractional source highlight, runtime 5 generated-only marker surface. |
| RC5 | Runtime 5 step 30 explicit R explanation. |
| RC6 | Runtime 5 steps 10–18 open actual human value; runtime 3 steps 14–17 independent reveal; runtime 6 step 46 flat actions. |
| RC7 | Runtime 5 After expands two overlapping comment children; source Enter/double-click still opens. |
| RC8 | Runtime 5 step 35 drag yields exact [0..7); pure split side-lock/Unicode tests and runtime 6 persisted side hashes. |
| RC9 | Six distinct leaves, runtime 6 text/structured two-sided views, runtime 7 GUI4 Fit Width; 15 persisted source slice hashes match both revisions. |
| RC10 | Plan, changelog, updated guide; full Java 1953/0/3, Rust check-all/install, current-source GUI2/4; final datagen pending. |

No third-party warning is being presented as an SFM pass: each launcher retained
the existing Industrial Foregoing missing-texture error, while puppet completion
and artifact publication succeeded. No SFM interaction crash occurred in the
final runs. Fit of long source lines in narrow columns is an overview, not a
promise of readable text at that zoom; normal initial scale and pan/zoom remain
available. Current goal excludes semantic go-to-definition from split padding,
automatic comment migration/approval transfer, and unrelated Explorer redesign.

### Final operational checkpoint

Final `datagen-2.ndjson` passed in 49.6s, exit 0, zero generated files written,
zero stale removals. No runtime-input mutation invalidates the final tests.
All four tasks and RC1–10 are complete against the acceptance audit above.
Current source is dirty atop `16328629fa60c45a4f525b6f20aaa77715f91077`; no commit,
push or branch propagation was performed. Unrelated changes are retained.

Installed CLI version/hash remains the proven post-Rust-install value above;
`--version` smoke passed after the last Java edit. **User must run install.ps1:
no.** No further Rust changes followed installation. Both user reviews remain
unchanged: September 7 SHA `52E8EE6E700B0090C7D8612C93D84393ED21FC39801F9047D59C9F3CEB42D306`,
manual test SHA `9ADDCCEC595CAA916EFDB8019270F62D577B336508D2608D84BDA2FE7AADD592`.
Only the disposable copy gained three human test comments. Final clients 38320
and 22468 and datagen launcher 23851 exited normally; no test game is left open.
Dependency declaration/lockfile diff remains empty; no new clone or dependency.

Manual launch from the repository root:
`sfm-propagate-changes.exe run client --branch 1.19.2 --wait-for-build-lock`.
This recompiles Java and opens the title screen. Follow
`docs/review refresh and comment interaction guide.md`; no installer step needed.
No unrelated stretch item was claimed. Automatic approval migration remains out
of scope; captures deliberately preserve originals and create a new review.

Frozen dependency declarations/lockfiles; no new dependencies/repositories,
Gradle, commits, pushes or propagation. Follow the goal execution/readiness guide
for bounded proven process ownership. Preserve user games/unsaved work where
possible by using a separate preview. Preflight commit, processes, review hashes
and installed CLI hash before testing. Record final runtime state and whether
installation is necessary after the last relevant edit. All four tasks are core;
no unrelated stretch work until these acceptance gates pass.
