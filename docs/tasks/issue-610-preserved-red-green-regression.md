# Issue 610: retained red and green regression projections

Status: preserved paired host GameTest acceptance completed: corrected identical tests fail on 4.34.0 production and all eight pass on 4.35.0. Linux/container and broader release checks remain pending.

## Scope

`quantity_sensitive_extraction_progress` gates the production fix across all ten supported targets. A zero extraction for one destination's requested quantity no longer marks the entire source done. The outer destination iterator bounds attempts; existing source exhaustion, quantity and retention checks remain. No extraction/insertion quantities or resource accounting are changed. This fixes downstream starvation, not the adapter's unrepresentable one-FE top-up or issue 608 insertion loss.

`issue_610_progress_tests` independently adds 1.21.1 tests. The original six real Mekanism/JDT characterization cases are retained and reused in a separate progress generator, with later consumers expected to receive power on all five passes. A seventh real-block case uses the current report's `INPUT fe:: FROM PowerSource EACH SIDE` and `OUTPUT fe:: TO EACH Dropper, Healthy` syntax. All cases retain raw FE/joule assertions and exact conservation checks. The characterization generator remains unchanged.

An additional reduced server GameTest exercises the real `OutputStatement.moveTo` with energy, item and fluid handlers; failed small requests followed by larger requests, exhausted sources, nonempty energy, item and fluid sources each refusing every extraction across 256 destinations, preserving source contents and quantity budgets, and input quantity/retention accounting. This reduced test is distinct from real mod/world capability acceptance.

The retained catalog entries are:

- `sfm-4.34.0-issue610/mc-1.21.1`: unchanged baseline production features plus additive progress tests and the captured dependency profile.
- `sfm-4.35.0-issue610/mc-1.21.1`: identical tests plus the energy fix and the existing 4.35.0 recipe namespace/version features.

Use these descriptive projection keys for both source preparation and host execution. Their names describe the regression workflow; the selected production versions remain 4.34.0 and 4.35.0. The initial longer `-issue610-regression` host attempt encountered a 264-character relative patcher JAR path. A former shorter alias recovered that step, but has been replaced with the clear `-issue610` names. Prefer bounded shorter internal work/cache paths for remaining Windows path limits; do not encode ambiguous version-like projection names. Historical generated directories and attempt logs are retained; they are not active catalog entries.

Both entries use the development environment to select the existing captured native dependency profile instead of mutating frozen 4.34.0 release recipes. Exact generated-root ignore rules are required by that environment. The catalog/core definitions retain the runnable projections; generated trees can be recreated with `source project sync`.

Existing 1.21.1 schema-4 inputs pin NeoForge 21.1.206, Mekanism 10.7.4 and test-only Just Dire Things 1.5.7 (file 7463040). No dependency declarations, locks, Rust resolver or container code were changed by this task.

## Source evidence

At preparation, 7,633 existing files under all ten 4.34.0 projections' production, generated-resource and datagen trees retained their SHA-256 hashes. The retained red projection's 765 files in those trees exactly matched the existing 1.21.1 4.34.0 projection, including identical path inventories. The two newly added test files render byte-identically in red and green.

The ten drafted 4.35.0 release projections now select the production fix. Their existing recipe namespace/version features remain. Only the affected production source, changelog and English trace localization outputs were refreshed; other authored and generated dirty work was preserved.

Source comparisons and projection checks are not GameTest acceptance. Runtime counts, logs and return codes must be recorded below after execution against the final source/image snapshot.

## Recreate and run

From the repository root, using the current source-matching CLI:

```powershell
sfm-propagate-changes source project sync --repo-root . --projection sfm-4.34.0-issue610/mc-1.21.1
sfm-propagate-changes source project sync --repo-root . --projection sfm-4.35.0-issue610/mc-1.21.1
sfm-propagate-changes game-test run-server --repo-root . --projection sfm-4.34.0-issue610/mc-1.21.1 --filter "issue_610_*progress*"
sfm-propagate-changes game-test run-server --repo-root . --projection sfm-4.35.0-issue610/mc-1.21.1 --filter "issue_610_*progress*"
```

Expected red: nonzero exit from progress assertions on the baseline, with the actual failure names/counts retained in logs. Expected green: all eight selected required GameTests pass. Confirm actual discovery counts; an empty selector or a preparation-only receipt is failure of this acceptance workflow. Retain the baseline red catalog entry after green succeeds.

For separate real-block evidence use `--filter "issue_610_jdt_progress_*"` (seven tests); for the reduced controls use `--filter "issue_610_resource_progress"` (one test). Broader item/fluid/energy regression suites and release validation remain necessary before declaring release readiness.

## Limits and readiness

- Real-block fixtures establish the pinned Mekanism/JDT configuration; Productive Bees, Applied Flux/ExtendedAE and unknown modpack versions remain untested.
- Refusing nonempty sources may now incur one failed attempt per destination rather than one total failure. The algorithm remains bounded by inputs times destinations, with no retry loop. Actual performance measurements are pending.
- Heavy compute was explicitly deferred while Android owned it. No GameTest server, native compilation or image build was run by this task during source preparation.
- Source revision at start: `342a73160d95cf8377bba955eeaf592676e53278`, with existing dirty migration/container/release work preserved. No commit or push.
- This task changed no CLI code and ran no installer. Existing executable used for source rendering: `G:\Programming\Caches\CARGO_HOME\bin\sfm-propagate-changes.exe`, SHA-256 `C73535DF5FD395BAFB06544C9588ADABD6B9D607347204CD646DF782655AC59A`. This identifies source-rendering evidence; it does not establish freshness for the concurrently modified container CLI. The container owner must capture final tool/image identity before runtime acceptance.
- Frozen dependency posture: no locks/declarations changed, no cache substitutes, no reference clones, no mounts/privileged runs, no infrastructure changes, no disk cleanup. During initial source preparation no game/server processes were started or stopped; final host acceptance is recorded below. One task-owned Python hash scan was stopped and replaced by a production-only scan.

## Historical preparation record

Before the final accepted runs, two host red preparation attempts exited 1 with zero discovered/executed tests:

1. Long retained key: `applyNeoforgePatches` could not open its 264-character relative patcher JAR path. The identical shorter catalog key recovered this step.
2. Former short key `sfm-4.34.0-610/mc-1.21.1` (historical attempt; no longer active): exact pinned Minecraft/NeoForge preparation completed, then project ANTLR generation requested `src/main/antlr/antlr4/ANTLRv4Lexer.g4` and `ANTLRv4Parser.g4`, which are absent from baseline feature selection. These inputs require `canvas_text_editor`. Enabling that production feature would violate the baseline constraint, so it was not enabled. Shared builder locations include `engine_sources.rs:237` and `engine_named_compile.rs:1424`; their owner must coordinate the repair.

The green run was deferred at this shared-build blocker. All task-owned native hosts, children and CLI workers stopped; heavy compute was released. No server started. Host executable: revision `342a73160`, SHA-256 `C73535DF5FD395BAFB06544C9588ADABD6B9D607347204CD646DF782655AC59A`. Catalog SHA-256 at the failed historical short-key attempt `C26C4D17157F425D531AFB42BAC38FC8CF5BC34D701699BC3CEE37700705F09A`; pinned 1.21.1 schema-4 lock SHA-256 `3E9AD80F6A8FFDA904A732529C0BABEAACE6C6D946A97E5845144B9275AF9163`.

Raw attempt logs are in `C:\Users\Teamy\Documents\Codex\2026-10-10\task-8\issue610-red-console.log` and `issue610-red-host-console.log`. Reduced-test source SHA-256 at those historical attempts was `097AC16DB7E66071B13F7C3E91E622EAFE1CEE748814CC454B02ABC8E58DD098`; it includes separate 256-destination zero-return conservation/budget checks for items, fluids and energy.

After shared-builder repair, record exact final source/image identities, commands, exit codes, actual selected/required test counts, failing red progress assertions and successful green count. Preparation failures above are not red regression acceptance.


## Actual host attempt 38 and fixture correction

The container owner repaired the shared ANTLR selection and ran both retained projections on the actual host. Each of three red attempts selected and completed eight tests: five required failures, three passes, server exit 5 / CLI exit 1. Failures included the item progress control and four downstream Mekanism/JDT progress assertions. Each green attempt selected and completed eight tests: seven real Mekanism/JDT scenarios passed, while the reduced resource test failed on fluid progress (server/CLI exit 1). This is not green acceptance.

Evidence: `C:\Users\Teamy\Documents\Codex\2026-10-09\task-11\issue610-red-acceptance-38.json` and `issue610-green-acceptance-38.json`, with corresponding `issue610-*-host-38-server-attempts/attempt-{1,2,3}.log`. Runtime executable SHA-256: `CA73449718B5945CE1A6741967204B387347BB32E2E94F0815A1A32A23C98BCB`.

Source diagnosis established a fixture error: `ResourceIdSet.MATCH_ALL` uses the two-argument `ResourceIdentifier` constructor, which defaults to `sfm:item`. Thus the fluid control was finished by the unmatched-resource check before extraction. The corrected input/output helpers construct resource identifiers from the registered resource-type key. Preconditions now verify the nonempty source is selected, and the refused first request conserves source, destination and quantity budget. Existing later-transfer conservation and exact 256-destination work bounds remain intact. Production code was not changed for this fixture repair.

Corrected tests are identical in both retained projections; source hashes reverified 7,633 baseline files unchanged and 765 red production files byte-identical to 4.34.0. Historical generated directories/logs were preserved. At this source-only checkpoint, fresh host runs were required and compute remained with the broker. Final accepted runs of this corrected revision follow below.

Corrected reduced-test SHA-256: `C60A60EC9C885DA5498CBF9A1A9750159D500ECBA540045C94FF04B213FF4943`.


## Final paired host acceptance (green 39 / red 40)

Exact commands from `C:\Users\Teamy\Documents\Codex\2026-10-10\task-8`:

```powershell
& 'C:\Users\Teamy\Documents\Codex\2026-10-09\task-11\run_issue610_host_28.ps1' -Phase green -Attempt 39
& 'C:\Users\Teamy\Documents\Codex\2026-10-09\task-11\run_issue610_host_28.ps1' -Phase red -Attempt 40
```

The wrapper invokes final-gate-37 CLI, explicit JBR 21.0.11, portable pinned artifacts, canonical projection keys, and `--filter 'issue_610_*progress*'`. Its input JSON records exact arguments and executable/catalog hashes. Green completed in 356.12 seconds: matched eight of 198 tests, completed eight, all eight required passed, first server launch, CLI/server exit zero. The reduced test therefore reached and passed item, fluid and energy progress, exact 256-destination refusal bounds, conservation, empty-source and quantity/retention checks. All seven real Mekanism/JDT scenarios passed.

Red completed in 417.71 seconds: each of three automatic CLI server attempts matched/completed eight, five required progress failures and three passes; server exit five / CLI exit one. Failure names: `issue_610_resource_progress` (item source incorrectly finished), `issue_610_jdt_progress_single_label`, `issue_610_jdt_progress_dropper_first`, `issue_610_jdt_progress_quantity_1000`, and `issue_610_jdt_progress_each_side` (later destination expected FE, got zero on pass two). Setup prerequisites succeeded. Automatic retries are recorded, not interpreted as independent acceptance. Red attempt 39 was interrupted by executor connection reset before server launch; its preparation evidence is retained and is not acceptance.

Current evidence: `issue610-green-acceptance-39.json`, `issue610-red-acceptance-40.json`, `issue610-green-39-minecraft-logs/`, `issue610-red-40-minecraft-logs/`, and both wrapper input/receipt/event/disk records under task-8. The bounded collector preserved originals and decoded archived logs with hashes. `verify_issue610_runtime_logs.py` passed on the final completed logs and exact exits. Neither completed wrapper hit its 900-second or 8-GiB disk bound. Final source hash audit confirmed 7,633 baseline files unchanged, 765 red production files baseline-identical, and both additive test files byte-identical between red and green. Executable/catalog/pinned lock identities remain those recorded above. No commit, push, lock edits, unrelated changes, cleanup or infrastructure operations.

Compute released at 21:46 UTC for the parent-directed Android relink before container work. Linux image/JUnit/orbit, broader resource suites, Productive Bees and other mod configurations remain outside this host acceptance; no release-wide readiness claim.
