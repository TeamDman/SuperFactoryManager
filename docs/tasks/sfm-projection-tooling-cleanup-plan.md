# Fast, checkout-directed source projection

Plan status: Active. Branch: `main`. Last updated: 2026-10-08.
Starting checkpoint: `bc71d79d0` (clean worktree).

## Updating this plan

Use `[ ]` pending, `[~]` active, `[x]` complete, `[!]` externally blocked.
Keep evidence beside each task. Do not mark the goal complete before every
requirement below has current-source evidence. This plan supersedes the tooling
cleanup direction at the end of `sfm-whitespace-normalization-plan.md`, not its
completed source changes or historical evidence.

## User requirements and acceptance

| ID | Confirmed requirement | Work and proof |
| --- | --- | --- |
| C1 | Remove historical migration evidence from production compile-time dependencies; operational configuration belongs to the selected checkout. | Task 1; external-include audit and alternate-root runtime checks. |
| C2 | Tests validate machinery, not current SFM source snapshots. Oracle comparisons are runtime CLI checks. Actively remove cruft. | Task 1; small positive/negative fixtures, obsolete-test removal inventory, runtime oracle evidence. |
| C3 | Explicit file-to-projection selection: one-to-one, one-to-all, all-to-all; share work across the batch. | Task 2; selector tests, runtime scope and timing checks. |
| C4 | Workers edit one template and verify in memory against pinned Git revisions without manifesting unrelated files. | Tasks 2 and 4; worker results and unchanged unrelated outputs. |
| C5 | Replace generation-history manifests with Git dirty protection and optional-selector `--allow-dirty`. | Task 3; staged/unstaged/untracked and scoped-override fixtures; old manifest consumers removed. |
| C6 | Generated-only contributions require a hydration PR before merge, not permanent provenance tracking. | Task 3; contributor instructions. |
| C7 | Two genuine candidates, two parallel workers, templates and selected projections complete within a measured 60-second pilot. | Task 4; elapsed wall time includes dispatch, coordination, verification and manifestation. |
| C8 | A missed deadline is reported honestly and drives bottleneck repair, not weaker checks. Broad validation once per batch outside workers' critical path. | Tasks 4 and 5; timings, diagnostic outcome and final gate. |
| C9 | Commit reviewable changes; no push or dependency changes; stop on disk-space errors. | Task 5 and all work. |

Intent audit: passed against the current five-part goal and preceding cleanup
clarifications. Extraction retained optional-selector overrides and the hydration
workflow. Traceability maps every requirement above to work and evidence.
Adversarial review retained runtime oracle checking, shared batch context and the
complete pilot timing boundary. Older conversation details are summarized in the
previous plan; they are not claimed as freshly verified implementation facts.

## [~] 1 Remove historical coupling

Production includes currently bind released-native recipes, NFRT contracts and
child identities, and a one-time seed validator to historical documents. Inspect
each consumer before changing it: dependency identities and execution checks must
survive; migration review prose and exact source snapshots must not be operational
inputs. No dependency declarations or lockfiles may change.

Inspect `source_projection/{released_native_inputs,frozen_recipe_project,
nfrt_child_identity_supplement,core_version_seed}.rs` and
`jar_build/nfrt_launch_contract.rs`. Separate obsolete import commands from the
ongoing generator. Remove repository-sized golden suites after identifying the
generic contract fixtures that replace their ongoing value.

Proof: production no longer compiles documentation from outside the crate;
runtime uses the selected checkout; generic negative tests reject semantic
changes, unsafe selection and invalid configuration. Preserve Git oracle pins.

First removal completed: retired `source legacy seed-shared`, `seed-versions`,
`seed-build` and `seed-auxiliary`, their argument adapters and the three one-time
seed modules, including their historical tests (4,135 lines removed). Reference
search found no consumers outside those command adapters and the seed modules.
The catalog already rejected these legacy authoring commands. The shared
`variant_consolidation` engine, current generator and runtime oracle routes are
unchanged. Historical documents remain as evidence, not deleted wholesale.
`cargo check --locked --offline --lib` passed in 40.69 seconds; only a cache
hard-link fallback warning occurred. Full validation and installation remain
pending until the cleanup batch is ready. No dependency or Java input changed.

Next runtime boundary: `released_native_inputs` and `frozen_recipe_project` both
embed the same review document. Their operational fields describe dependency
roles and source-lock bindings. `nfrt_launch_contract` additionally embeds tool
function arguments and source-tool ordering. Extract the consumed configuration
without weakening exact artifact checks; do not just relocate review prose into
the executable package. `CatalogOwnedProject::repo_root()` provides the selected
checkout root for this integration.

Release-recipe extraction is now wired: production `frozen_recipe_project`
reads `platform/minecraft/build-configuration/released-native.json` from its
selected checkout and passes those bytes to the input validator. Configuration
contains the ten original consumed recipe rows; dependency pins/locks are
unchanged. The receipt hashes the supplied configuration and recheck rejects a
later change. Both recipe consumers no longer embed the historical review in
production; temporary test-only adapters remain pending suite cleanup. The first
library check passed; the later recheck/fixture edits still need validation.
The seed-removal test-target compile check also passed (74 seconds).

NFRT function configuration now reads `build-configuration/nfrt.json` relative
to the selected repository's Minecraft directory for both release and development
execution. Six consumed target rows were extracted without changing their tool
coordinates or arguments. Small isolated-root tests cover checkout selection,
missing target/configuration and unknown schema; execution is pending. The
remaining production embedding is the child-tool identity supplement and its
official identity evidence. The focused historical frozen-recipe suite is still
finished session 69617: compilation took 175 seconds, then all 17 tests failed
before exercising behavior because the sandbox denied temporary `.git/config`
writes. A direct elevated rerun of the same built test executable was started,
avoiding another compile. This binary predates the subsequent NFRT extraction;
its result cannot validate that newer code.
The elevated rerun is session 73991: 16 tests have passed, with the twenty-context
case still running. Current-source `cargo check --locked --offline --lib` passed
after the NFRT extraction (32.36 seconds). Small new configuration tests are not
yet executed. `git diff --check` passed; dependency declaration/lock diffs are empty.

The elevated recipe run completed: 17 passed, zero failures, 69.10 seconds.
Child-tool constructors now receive the selected repository root, read its two
existing supplement files, and require their unchanged approved SHA-256 values.
Publisher review documents are no longer production inputs: exact approved pin
bytes bind that historical evidence, while parent/child artifact bytes are still
checked during use. Production library check passed (18.99 seconds). Historical
proof readers and embedded fixtures remain test-only for the next cleanup pass.

Removed 116 registered source-golden/current-format-inverse modules (including
`release_source_parity_test`). Their module-reference audit found no external
callers outside the removed group. Retained generic renderer/scanner, selector,
consolidation, oracle and strict equivalence tests, plus the two shared fixture
helpers still consumed by native-build tests. No Java source, feature selection,
dependency pin or oracle reference was removed. These deleted tests are
recoverable from `bc71d79d0`; real repository comparisons are runtime commands.
The remaining test-target compile check passed (session 90792, 45.29 seconds).
It exposed unused wave-specific capture exports, which were then removed with
their now-unreferenced helper module. Do not claim the new configuration fixtures
or broad gate have passed yet. Some shared fixture cleanup remains.

## [~] 2 Implement selected manifestation with shared context

Entry: `cli/source/core_project_cli.rs`; input selection/rendering:
`source_projection/core_inputs.rs`. Load catalog/configuration once, select
contexts and files explicitly, reuse reads/parses, render only selected outputs.
Workers continue using `source simplify verify` against pinned Git oracles.

Proof: one-to-one, one-to-all and all-to-all operate with the same semantics;
unselected files remain unchanged; in-memory verification needs no disk refresh.

Implemented the initial `source project manifest` route with repeatable `--file`
and `--projection` exact selectors; empty or a solitary `*` selects all. One
invocation shares catalog, metadata, source inventory, bounded input snapshots and
compiled Liquid templates. Context validation still runs on every render; cached
templates do not cache context-dependent output. Added small selector/cache tests.
Current library check passed (session 25763, 34.14 seconds) after fixing a selector integration error.
Runtime, Figue optional-value parsing and performance proofs remain pending.

## [~] 3 Replace provenance bookkeeping

Entry: `source_projection/sync.rs` and its consumers. Remove last-generated-hash
ownership manifests and the reconcile workflow once their consumers are replaced.
Use Git dirty-output checks, not a renamed provenance database. Bare
`--allow-dirty` affects selected outputs only; a selector narrows the override.
Figue nested options are the intended representation, subject to parser tests.
Keep path safety and bounded writes. Explicitly test ignored development output
handling rather than mistaking ignored output for tracked clean content.

Proof: protect dirty selected tracked outputs by default; overrides cannot widen
the selected write set; no projection manifest is needed on a second invocation.
Document hydration PRs and distinguish projection configuration from removed
generation-history files.

The new manifestation path does not read or write provenance manifests. It checks
Git dirty paths once for the selected roots, protects changed outputs by default,
and supports bare or repository-relative prefix-scoped `--allow-dirty`. It reads
only regular non-reparse outputs, rechecks bytes before replacement, and stages
each write in the destination directory. Small regression fixtures cover dirty
untracked outputs, scoped overrides, rename status parsing, repeated generation
and preservation of unrelated files. Tests are added but not yet run.

Still required: route existing sync/build consumers onto the replacement,
handle removal of outputs excluded by a changed feature selection, remove old
manifests and reconcile consumers, and prove staged/unstaged/ignored cases.
The old writer remains temporarily available; this is not completion of C5.

The initial selected-manifestation regression run passed five tests in 0.27s
(compilation 103s), including Figue's absent/bare/scoped optional flag parsing.
Subsequent changes remove live provenance reads from catalog-owned build receipts
and their rechecks; source/configuration/output verification remains. Catalog sync
now delegates writes to Git protection and rejects reconcile with hydration
guidance. Excluded source rules can mark selected outputs for removal. These later
changes passed test-target compilation (52.99s), not runtime validation yet.

Current focus: remove remaining live readers before deleting manifest files.
`CoreCatalog::for_project` now resolves an exact project root using the containing
checkout's catalog and feature registry. Static test/puppet discovery uses this
lookup instead of generated provenance. Named-root ignore policy no longer probes
for a history manifest. Library compilation passed (18.57s); focused fixture tests
are being rebuilt. The fixture deliberately gives the directory a name that does
not encode its version. Java workspace analysis and source tracing still need
conversion. Pinned-oracle verification must retain historical Git compatibility
while deriving new snapshot contexts from committed catalog inputs, not live files.
Legacy sync branches and manifest-dependent test fixtures also remain. No pilot
timer has started, and no generated manifest has been deleted yet.

Focused lookup/discovery validation completed: the current test binary passed
`project_lookup_uses_catalog_values_without_generation_history` and
`generated_project_catalog_uses_checkout_version_and_project_sources`, each one
test, zero failures, 0.02s. Test compilation took 80s. The initial invocation used
an invalid libtest `--exact=false` option; rerunning the compiled executable with
the two plain filters required no rebuild. This proves only these two boundaries,
not the remaining manifest-consumer migration or the full gate.

Java workspace analysis now derives version and index identity from the exact
checkout catalog context, including feature booleans and environment. Its
workspace and symbol CLI fixtures no longer write generation manifests. Replaced
the real-branch inventory test (which assumed a populated ambient JDK cache) with
four small source-set files and isolated caches. The first sandbox run had 12
passes and two JDK/cache-related failures; the exact cached-JDK fixture passed
outside the sandbox without code changes. After replacing the machine-dependent
inventory test, normal-access workspace tests passed 14/14 in 0.25s (54.39s
compilation). The same current binary passed symbol-list tests 5/5 in 3.60s and
symbol-project-list tests 2/2 in 0.07s. Source tracing remains the next live reader
to convert. No broad gate or pilot result is implied by these focused tests.

Source tracing now renders its selected output from current core membership and
compares it with optional disk bytes; schema `sfm:source_projection_trace@3`
reports `matches_current_template` rather than historical ownership. It does not
write files. Its initial two tests passed in 0.08s. Removed the named-project
`reconcile` command. Pinned simplification verification now reads catalog and
feature definitions from the pinned Git commit rather than a generation manifest;
the fixture has no history file and proves independence from changed HEAD/disk,
rejects a changed catalog context and rejects changed Java. Three verifier tests
passed in 0.72s after 52.24s compilation. Historical branch-binding mode remains.
Found and repaired a real-input readiness bug: selected manifestation and tracing
used the catalog's 1 MiB bound for the 1,241,070-byte core membership metadata.
They now use its existing 8 MiB bound with the same checked/bounded reader; the
trace fixture includes metadata exceeding 1 MiB. No dependency limit was loosened.

Named sync no longer has a dead catalog-history writer arm. Replaced its obsolete
owner/reconcile tests with repeatable manifest-free generation, dirty-output
refusal, exact hydration/no-op and unselected-output preservation. Build preflight
fixtures now compare real output bytes rather than an absent history file.
The named-filter run initially found nine fixtures missing checkout-owned build
configuration/supplements; supplying those unchanged inputs yielded 77 passed,
zero failed, one pre-existing opt-in ignored (49.82s tests, 40.31s compilation).
Core-project fixtures passed 7/7 (1.23s), output observation 1/1 (0.12s).
Catalog-owned fixtures passed 18/19 (20.66s). The remaining test expected ignored
development outputs to receive old provenance protection; it now asserts the
agreed disposable-output refresh while a retained checked handle still rejects
edits before refresh. That updated fixture has not yet been rerun. Tracked dirty
outputs remain protected. Native-target fixture changes still need their direct
test run. No manifest files have been deleted and no pilot has started.

The next reader audit found `source simplify scan` still loading provenance.
Removed that dependency: scan schema v2 explicitly reports `all_inputs_parsed`
and `parsed_pair_count`, compares disk Java and does not claim core freshness or
oracle certification. Strict certification remains `source simplify verify`.
Four scan tests passed in 0.05s (58.37s compilation). The updated ignored-dev
refresh test passed in 1.23s; all 14 native-project preflight tests passed in
12.16s. Removed the ten tracked release projection history manifests (68,908
lines); no generated Java/resource/build file changed. These files remain
recoverable from Git. Ignored local projection history files may still exist and
need a bounded catalog-root cleanup. Current catalog commands do not read them.
The pre-consolidation `source legacy` compatibility path still has old provenance
types/writer logic and is blocked in catalog checkouts; audit/retire obsolete
routes without accidentally removing independent release tooling.

Updated the contributor guide with one-to-one and one-to-all manifestation,
explicit all-to-all dry-run, Git dirty-output protection, optional-selector
overrides, disposable ignored dev output and hydration PRs before merge. Removed
current reconciliation guidance and documented commit-catalog oracle identity.
`git diff --check` passed. Full batch performance/correctness validation, current
CLI install, cleanup commits and the timed pilot remain pending.

## [x] 4 Run the timed parallel pilot

After the report-size repair, a fresh pair completed in **57.640 seconds**:
`CableBlock.java` and `PrintingPressBlock.java`. The same two agents received new
file assignments in parallel. Timing started before first assignment at
06:44:41.065 UTC and ended after the combined disk manifestation returned.
No candidate edits were prepared before the timer. Both agents verified all 20
pinned historical oracles and zero remaining whitespace-candidate pairs. The
single manifestation changed 4 outputs and left 36 unchanged. Independent
post-pilot verification again passed all 40 contexts; the disk dry-run found
40 unchanged outputs. The successful measurement used already-running agents,
not fresh agent startup. It proves this measured workflow, not a universal SLA.

Installed compact reporting was checked before dispatch: 308ms for all 20
CableBlock contexts, returning 2,628 UTF-8 bytes. It retained complete checks and
bounded candidate examples. Both the initial miss and this fresh pass remain
recorded; no completed edit was replayed to manufacture a passing time.

The two-agent pilot completed correctly in **81.219 seconds**, missing the
60-second deadline by 21.219 seconds. Timing began before first dispatch at
06:34:06.414 UTC and ended after the combined manifestation returned. Each agent
edited only its assigned core template and verified all 20 historical oracles.
Both files now have zero whitespace-candidate pairs. The coordinator manifested
both files in one call: 8 outputs changed, 32 unchanged, across 20 projects.
Independent post-checks again verified all 40 file/context combinations; the
targeted dry-run reports 40 unchanged and no writes. Only these two Java output
paths changed; dependency, catalog and oracle inputs are unchanged.

Measured bottleneck: workers printed full pairwise JSON before selecting fields,
producing 171,791 and 75,824 output tokens before truncation. Water's five tool
calls totalled 7.1 seconds of wrapper time; Fancy's three totalled 4.0 seconds.
Startup, model processing and reasoning were not separately instrumented; do not
attribute the entire remaining duration to one of them. The combined manifestation
shell took 1.408 seconds. The repair is a compact `verify --summary` worker report
with unchanged full checks, failure details and bounded/deduplicated whitespace
examples. Its tests and installed-output proof are pending. Do not rerun the
already completed edits and claim that as a fresh successful timed pilot.

Pilot Java checkpoint: `d22d1bd4f`. Compact-report fixtures passed 4/4 in 0.87s:
bounded examples keep complete outcome counts, oracle failures retain exit 1,
and comment/literal changes remain rejected. Installed compact-report proof is
still pending. Worker guidance now explicitly includes `--summary`.

Batch machinery tests passed 7/7 in 0.65s (50.09s compilation). A fresh default
CLI build passed. Real read-only manifestation measured 1,802ms for one Java
template into one projection and 1,777ms for the same template into all 20.
The full-tree dry run took 148,091ms and matched all 29,544 existing outputs:
zero changes, zero removals, no writes. This is a batch check, not a worker-loop
operation. These timings precede the batch root-policy integration below.

Review found selected manifestation missing the established release/dev root
policy. It now calls a shared batch validator: one Git worktree check, one
tracked-development query and one ignore query for the selected batch, with
the existing path-alias and reparse checks retained. Focused tests are underway;
no pilot has started.

The seven named-root safety fixtures passed (1.32s test execution). The first
consolidated gate stopped at 11 lint diagnostics, before running tests; corrected
the naming/import issues and documented the requested three-state CLI override.
The restarted gate passed Clippy and is building. Removed the ten ignored dev
history manifests from exact catalog roots as well; these disposable metadata
copies had no Git backup. Generated project contents were otherwise untouched.

After tooling validation, discover two genuine remaining candidates and pin all
oracle contexts. Prepare bounded assignments without performing the edits ahead
of the timer. Start timer at dispatch of the first of two parallel workers.
Stop after both edits, oracle/whitespace checks and targeted disk manifestation
are verified. Agent startup and coordinator overhead count. Initial tooling
build, candidate discovery, final broad validation and commits do not.

Record actual elapsed time and each stage. If over 60 seconds, report the miss,
diagnose the bottleneck and improve it. Do not rerun already completed edits as
though that proved a fresh two-file pilot.

Candidate discovery (no edits): `WaterTankBlock.java` and `FancyCableBlock.java`
under `src/main/java/ca/teamdman/sfm/common/block/`. In-memory verification passed
all 20 pinned historical Git contexts for each. Their respective reports contain
92 and 36 projection pairs with whitespace candidates. The binding file already
stores full commit IDs; worker assignments will retain those bindings unchanged.
Discovery used the current all-features debug build for correctness only, not
performance evidence. Operational timing awaits the default/release build.

## [ ] 5 Validate, install and checkpoint

Cleanup checkpoint: `c08914986` (local only). Its installer completed in 2m15s;
the PATH executable reported that revision and SHA-256
`02736beabc0ac190f86dabc6ccb7b4df46b79323faf5f9b9110e76932c6f63b4`.
Installed one-file/all-20 dry-run took 515ms; an all-20 oracle check took 282ms.
This executable ran the pilot; the subsequent compact-report change requires
another final install. The consolidated gate later failed three native fixtures:
two mutated retired manifests, and one omitted checkout-owned supplements.
Fixtures now mutate the catalog and provide the unchanged pinned supplements.
The engine rerun passed 232 tests, zero failures, 3 ignored in 91.56s. Remaining
broad validation and compact-report verification are still required.

The consolidated gate passed linting and its all-features build. Its CLI group
passed 338 tests with zero failures in 444.15 seconds; native-build and remaining
groups are still running. This checkpoint is not final validation. Dependency
declarations and lockfiles have no diff. Publication review found no machine
paths in the added runtime configuration or current cleanup documentation.

Run focused machinery tests during implementation and the repository-required
`check-all.ps1 -TestWorkers 2` once per completed batch, not in the pilot path.
Run runtime oracle checks on the affected real contexts. Commit reviewable
changes without pushing. Run the CLI installer after the final executable-input
change and record installed identity plus a copyable smoke command.

Completion requires C1-C9 evidence, not just a green unit suite. Dependency
posture is frozen. No running game or process has been stopped for this goal.
No stretch work is authorized beyond the specified cleanup and pilot.
