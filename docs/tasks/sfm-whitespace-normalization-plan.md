# Normalize reviewed projection whitespace

Plan status: Active. Branch: `main`. Updated: 7 October 2026.
Use `[ ]`, `[~]`, `[x]` and `[!]` for pending, active, complete and blocked work.

## Intent and boundaries

U1: preserve the exact migration baseline with a Git tag before normalization.
U2: begin merging redundant Liquid branches on main, reducing whitespace
differences in actual projections. These are Liquid branches, not Git branches.
U3: validate complete rendered Java, preserving tokens, comments and literals.
U4: fix the remaining SFM.java candidates reported after the constructor change.
Keep historical oracle pins and exact comparison policy unchanged. Differences
introduced intentionally must remain visible, not silently waived.

Intent audit: extraction covered the user's tagging request and approval to
begin normalization; traceability maps U1 to task 1 and U2/U3 to task 2; the
adversarial pass checked that tagging is not publishing and that candidates are
not automatic rewrite permission without before/after evidence. No limitation
applies to these available recent messages.

## [x] 1 Preserve the baseline

Annotated local tag `source-projection-exact-baseline-2026-10-07` points to
`7c086257c7d1df614f75d1786069392fda7cd857`. It has not been pushed. The full
`source oracle status` run completed with 20 compared contexts, complete
coverage, snapshot_matches, references_current and done all true. The tag is
a source checkpoint, not a mod release or a byte-identical JAR claim.

## [~] 2 Normalize SFM.java candidates

Merge the 26.1.2 constructor into the 1.20.4/1.21/1.21.1 Liquid case. Only the
release and development 26.1.2 SFM.java outputs should gain one blank line after
the opening brace. Compare all 20 full renders before and after using the
existing parser/token engine, and assert the exact expected byte insertion.

Historical client-entrypoint tests retain their original template hashes and
Git witnesses: a test-only reverse transform restores this one exact reviewed
template fragments before historical checks. A separate current-source test
must validate the actual template and all 20 new renders. This is not a
production fallback and does not change oracle results.

Synchronize affected manifested outputs through the existing guarded CLI.
Re-run simplify to verify removal of the candidate. Existing exact oracle
checks are expected to report seven deliberate file differences; do not
rewrite pins or introduce blanket whitespace normalization to hide them.

Run focused tests before the required full Rust gate if test code changes.
No runtime Rust changes, dependency updates, Java semantic edits, Gradle runs,
publication or pushes are in scope. Stop on a disk-space error. Further files
are a subsequent batch, not implied by completing this first adjustment.

Evidence so far: all 20 installed-CLI single-file renders match the old output
exactly except the two predicted one-newline insertions. All 20 guarded project
syncs succeeded. The post-sync simplify scan verifies all inputs: exact pairs
increased from 6 to 9, whitespace-only pairs fell from 3 to 0, and pairs with
local whitespace candidates fell from 54 to 44. Remaining candidates can repeat
one difference; they are not a count of independent edits.

After explicitly refreshing the derived index for the installed renderer,
`source oracle next` reports exactly 2 changed cells and one unresolved path,
SFM.java. This is the intended strict historical difference, not a failed
normalization proof. Oracle pins and policies are unchanged.

All 8 focused `core_client_registration_slice_tests` passed in 5.87 seconds
after compilation, including the complete-render and mutation-rejection tests.
The first full Rust gate was stopped during compilation when U4 expanded this
batch. It is not completion evidence. Restart the gate after the expanded
batch's focused checks; final gate completion and commit remain pending.
Runtime CLI code has not changed; the installed `cdbbd89c8` scanner is current
for this operation. No Java build is needed to establish the narrowly proven
single-newline change, and no gameplay validation is claimed.

### Remaining-candidate batch (U4)

The 44 candidate-bearing pairs represent three recurring spacing patterns,
not 44 independent source edits. Remove the feature/version conditional whose
only output is an extra blank line after config registration. Add consistent
spacing before ComputerCraft registration. This removes the extra gaps before
packet listeners and automatic event registration without changing Java tokens.

The current-source regression checks all 20 contexts against exact, explicitly
listed whitespace replacements. Historical template hashes remain frozen:
the test-only reverse transform must reconstruct their exact original bytes.
Expected changes relative to the tag: release 26.1.2 and development 1.20,
1.20.1, 1.20.2, 1.20.3, 1.20.4 and 26.1.2. All other contexts stay byte-exact.
Acceptance: all inputs verified and zero remaining SFM.java whitespace
candidates. Other Java files have not been scanned by this batch.

Expanded-batch evidence: all 20 guarded syncs succeeded. The installed scanner
verified all inputs and all 190 pairs, with zero candidate-bearing pairs
(previously 44). The strict oracle index was explicitly rebuilt after sync;
it reports exactly seven changed cells and one unresolved path, as expected.
All eight focused regression tests passed (6.78 seconds after compilation),
including current-source full-render certification and mutation rejection.
The replacement full gate failed in unit:cli (245 passed, 90 failed). The
reported temporary log is no longer available; do not infer a root cause.
Focused tests passed, but full validation and the normalization commit remain pending.

## [~] 3 Build and pilot independent per-file workers

U5: coordinator discovers work; workers edit only their assigned template.
U6: workers render in memory and compare complete Java against immutable Git
commit/path oracles, without synchronizing shared on-disk projections.
U7: original MC sources use the existing pinned oracle bindings; committed
feature projections may use a pinned commit and projection path. Later commits
or output writes must not move those identities. Uncommitted outputs are not
Git oracles. Missing or unsupported inputs must fail explicitly.
U8: build tooling, identify two candidates, run two subagents concurrently,
and prove this small pilot before attempting tree-wide edits.

Intent audit: extracted U5–U8 from the available worker/oracle discussion and
pilot request; mapped each to the tooling and pilot below; checked the worker
versus coordinator distinction and the immutable-baseline qualifier. Earlier
normalization evidence is retained above, not treated as proof of this tooling.

Tooling owner: simplify_worker_tooling (Rust only). Coordinator owns candidate
selection, this plan, builds, final synchronization and acceptance. Two Java
workers will receive disjoint single-file assignments after tooling validation.

Acceptance: focused tests reject semantic changes and missing oracles, accept
whitespace-only edits, and leave projections untouched. Two concurrent workers
must produce passing in-memory evidence with no shared output writes; coordinator
then syncs and confirms those outputs. Measure wall time rather than assume
concurrency is fast. No broader fan-out, dependencies, Gradle runs or publication.
Required final Rust gate and installer remain part of the tooling handoff.

Pilot assignments: simplify_item_resource owns ItemResourceType.java (36
candidate-bearing pairs); simplify_manager_block owns ManagerBlock.java (140).
Both are verified in all 20 manifested contexts. Workers may not write other
files or synchronize outputs. Coordinator captured hashes of both output files
and manifests in all contexts before their edits for the no-write proof.

Prior-gate diagnostic: one failing CLI sync fixture was reproduced with access
denied replacing staged build.gradle in the sandbox. The same existing test
binary passed outside it. Final gate must use ordinary cache/filesystem access;
this single proof does not classify all 90 earlier failures.

Pilot decision: certificate success and candidate exhaustion are separate.
ManagerBlock includes token-alignment candidates between genuinely different
methods at different nesting depths. Do not damage indentation to force a zero
count; report those residuals explicitly and retain the strict same-context
oracle certificate. Workers remain responsible only for their assigned file,
not global discovery. Tooling caches each authored input within the invocation
so the 20 renders do not reread a changing template between contexts.

Completion evidence so far: both workers ran the newly built verifier at the
same time. ItemResourceType verified all 20 historical pins (18 exact, two
whitespace-only), reduced 36 candidate pairs to zero, and took 3.325 seconds.
ManagerBlock verified all 20 pins, reduced 140 pairs to 36 nesting-alignment
residuals, and took 3.095/3.111 seconds. It removed two whitespace-only Liquid
cases without forcing unrelated method indentation to match. Both changed only
their assigned template. All 60 captured projection-file/manifest hashes were
unchanged after worker completion.

The explicit committed-projection baseline also passed for the 26.1.2 release
ItemResourceType at commit 7c086257c7d1df614f75d1786069392fda7cd857. This tests
the second oracle mode against real repository data, not only fixtures.

The real pilot caught a metadata-reader limit bug (1 MiB versus the existing
8 MiB metadata contract). Corrected with a >1 MiB fixture regression; strict
all-features clippy passed. Focused tests and installation completed outside
the sandbox; the full gate remains active. Coordinator captured 40 render
hashes before the one post-batch synchronization. No broad fan-out has started.

Coordinator sync completed successfully for all 20 contexts. All 40 generated
Java bodies (removing only the exact generated banner) match the captured
in-memory render hashes. Thus worker validation and final generation agree.
The two-worker flow is proven; full-gate completion remains pending.

Focused validation passed outside the sandbox: three simplify_verify_cli tests
(0.74 seconds) and two direct_file_read tests (0.35 seconds), after compilation.
They cover both oracle modes, HEAD/disk mutation independence, semantic-change
failure, missing selected files, exact banner handling and bounded direct Git
reads. Installation completed; the full gate remains pending.

Operational checkpoint: install.ps1 completed from main HEAD 7c086257c plus
these uncommitted changes; installed CLI reports build 2026-10-07 22:33:10 -04:00.
User install required: no. Concurrent installed-binary verification measured
382 ms for ItemResourceType and 451 ms for ManagerBlock, each exit 0, 20 verified
contexts and writes_performed=false. Dependencies and lockfiles are unchanged.
Installed executable SHA-256:
`3b4f59117f5c47a351bc19e18a2c1e385d44235be299a256c2576947e5aa7790`.

The first post-install full-gate attempt stopped in build.rs because an empty
process-local installer revision variable survived installer restoration. A new
elevated check-all.ps1 -TestWorkers 2 run explicitly removes that variable and
uses CARGO_PROFILE_TEST_OPT_LEVEL=1. That gate reached unit:cli with 337 passed
and one failure: the historical ItemResourceType witness assertion required
the intentionally changed signature to remain byte-identical. The test now
allows only the exact reviewed 26.1.2 signature layout replacement, retaining
immutable witnesses and exact equality everywhere else. Targeted rerun passed
(one test, zero failures, 1.24 seconds);
full validation is not green. No further fan-out has started.

### AST-context candidate filtering

User requested implementation of the proposed nesting-aware discovery filter.
Compare the ancestor-node kind chains at both ends of each differing whitespace
gap. Suppress candidates when those contexts differ, including equal-depth
nodes with different parent kinds. This is a conservative discovery heuristic,
not proof that two subtrees mean the same thing. Keep the complete token-stream
and full-tree oracle certificate unchanged.

Implementation adds a suppressed-gap count and advances the discovery algorithm
identifier to v2. Regression coverage includes the ManagerBlock removal-method
example, deeper nesting, equal-depth different parents, and positive detection
of whitespace drift with matching nesting. All eight simplifier tests passed
(zero failures, 0.01 seconds after compilation). Installation completed.
Installed CLI verification passed for all 20 ManagerBlock historical oracles
in 378 ms: zero remaining candidate pairs (previously 36), no writes. The
manifested scan checked 190 pairs and suppressed 60 structurally unmatched
gaps under algorithm v2. No Java templates, generated projections or oracle
pins changed. Installed executable SHA-256:
`f4543461402c5291a7368860d01cac1c84842861d8bce5d7d38d15f234c61382`.

The required full gate passed dependency policy, formatting and strict
all-feature Clippy, and remains running at the build/test stages. Full-suite
success is not yet established. User install required: no.

## [x] 4 Repeat the two-worker pilot with nesting-aware discovery

U9: user authorized another run with two subagents, one file each. Preserve
U5-U7: workers validate only their owned file in memory against pinned Git
oracles; coordinator selects work and performs the single post-batch sync.
Intent audit: extracted the explicit two-worker scope, traced it to the owners
below, and checked that neither tree-wide edits nor weakened oracle checks are
implied. No additional feature or runtime changes are authorized by this batch.

Owners: simplify_manager_entity owns ManagerBlockEntity.java (92 candidate
pairs); simplify_printing_entity owns PrintingPressBlockEntity.java (132).
Both start with all 20 historical oracle contexts passing. Workers are initially
read-only while the preceding full gate reads the source tree. Coordinator has
captured the two outputs and provenance manifest in each context (60 hashes).

Acceptance: both workers report exact edits, remaining candidates and passing
20-context `source simplify verify` results; the 60 output hashes remain
unchanged until coordinator sync. After sync, generated bodies must match the
certified in-memory render hashes. Do not force questionable candidates to zero.
No production Rust changes, dependencies, oracle updates, automatic commits or
pushes. Coordinator may update historical test expectations for the exact
reviewed whitespace changes while retaining their original witnesses.

Workers finished: ManagerBlockEntity 92 -> 0 candidate pairs (14 exact and six
whitespace-only oracle comparisons); PrintingPressBlockEntity 132 -> 0. All 20
contexts pass for each. Manager changes remove two blank-line-only Liquid cases
and join a split guard around saveAdditional; PrintingPress changes only spacing,
parameter layout and chain line breaks. Coordinator verified all 60 output and
manifest hashes unchanged after workers finished, independently reran both
certificates and captured all 40 in-memory render hashes before synchronization.

The pre-batch full gate passed CLI, core slice and oracle groups but stopped at
release_source_parity_test: its exact-byte expectation rejects the already
reviewed ManagerBlock whitespace from the first pilot. Nine other tests in that
group passed. This is not full-gate success. The test-only adjustment retains
exact Git blob hashes and membership; only the five reviewed Java files may
differ by the strict complete-token/full-AST whitespace certificate. All other
paths retain exact normalized byte comparison. A negative test covers unknown
paths and token/comment changes. The production exact oracle policy is unchanged.

Manager's historical fixtures reconstruct exactly the three reviewed template
edits, then check the original full source hash. Every existing Buffer/Manager
render context also compares current rendering to reconstructed historical
rendering with the strict certificate; partial-feature coverage is preserved.
All 20 guarded syncs succeeded. All 40 generated Java bodies match the captured
in-memory render hashes after removal of only the exact generated banner.
Focused historical tests are compiling. An additional exploratory Clippy run
with `--tests` failed on repository-wide test lint errors (1,700 lib-test
diagnostics, including pre-existing dead code and assertion style); it is not
the repository's standard production Clippy command and is not a passing gate.
No unrelated lint repairs were attempted. Full-gate rerun remains required.

First focused rerun: six Buffer/Manager tests passed; the common-edit no-write
assertion incorrectly compared current source to reconstructed historical source.
It now snapshots actual current bytes before the test and checks those same
bytes afterward. The reviewed reverse transform and strict source hash remain
unchanged. The three focused groups are rebuilding after this correction.
The required `check-all.ps1 -TestWorkers 2` rerun is started with the same test
optimization setting; it is waiting for Cargo's build lock while the focused
compilation finishes. Neither run is yet passing completion evidence. Runtime
CLI installation remains current because this batch changed only test Rust.

Final checkpoint: the three focused groups passed (26 tests total). The required
`check-all.ps1 -TestWorkers 2` completed successfully: all 213 test shards,
binary/doc checks, dependency policy, formatting, standard strict all-feature
Clippy, and the final operational CLI build passed. This supersedes the pending
and failed validation states recorded above. The extra exploratory test-target
Clippy invocation remains outside that successful standard gate.

### Next cleanup direction (not implemented in this checkpoint)

User requested a commit before further changes. Replace per-projection
last-generated-hash bookkeeping, including `.sfm-source-projection-manifest.json`,
with Git dirty-output protection. Support `--allow-dirty` without a selector
for all selected outputs, or with a path selector for a scoped override; the
proposed Figue representation is `Option<Option<T>>`. Preserve projection
configuration and dependency locks; these are not generation-history manifests.

Provide file-to-projection selection with shared context for batch manifestation.
Keep oracle comparison as a runtime CLI check and small machinery fixtures as
tests. Actively remove historical migration coupling from production inputs and
tests. Contributor changes to generated sources require a hydration PR updating
the core template before merge, rather than permanent provenance bookkeeping.
