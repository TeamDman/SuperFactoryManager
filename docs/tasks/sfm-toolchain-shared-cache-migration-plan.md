# Shared SFM toolchain storage and projection closure

**Plan status:** Draft; local checkpoint committed, migration not started
**Primary root:** `D:\Repos\Minecraft\SFM\worktrees\sfm-main-source-projection` (`main`)
**Last updated:** 2026-10-28
**Intent audit:** Three self-audit passes recorded 2026-10-28; independent draft review pending
**Dependency posture:** Frozen graph; no new dependency or toolchain admission here.

## How to update this plan

Use `[ ]` not started, `[~]` in progress, `[x]` complete, `[!]` blocked only with exact reason and unblock condition. Update the heading and completion evidence together. A phase closes only when all its tasks are `[x]`. Record commits, commands, results and exceptions beside their task, not in a detached log. Follow the first unfinished item, with one heavy desktop build/GameTest job at a time.

## Intent audit evidence

- **Pass 1 — extraction, 2026-10-28:** Reread all 384 lines of Library conversation `libfile_429f31f5a1688191900b0be2fc29f4c8`, the supplied delegation and the `resumable-implementation-plans` skill. U01–U20 capture outcomes, paths, ordering, corrections and non-goals; U20 was added after the first draft to preserve the user's anomaly-to-cause requirement.
- **Pass 2 — traceability, 2026-10-28:** Checked each U01–U20 against the traceability table and milestone/terminal criteria (20/20 mapped). Inverse check tied the proposed root to the user's Git-relative preference, stage keys to the timestamp/read-set evidence, and numerical savings/growth budgets to G1 as *future* decisions rather than claimed results. No implementation or migration decision was smuggled in as completed.
- **Pass 3 — adversarial omission, 2026-10-28:** Reread the attachment after drafting and checked the later corrections: Gradle without Rust, no standalone projection or dynamic Git search, 4.34 grounding, existing D: not moved, timestamp-only same-version JARs, unchanged runs, old-plus-new physical savings, fresh-machine recovery and finite/parallel closure. Corrected the draft to name all pending user work, fresh bootstrap and unchanged-run budgets. Independent review may still amend this draft.- **Known source limit:** The attachment covers the cache discussion and recent corrections. Earlier long-running GameTest/dependency history is available as local receipts and `SFM-final-guard-next.md`, not a complete verbatim transcript. Historic receipts are not current-source proof.

## Authoritative user guidance ledger

| ID | Active guidance | Required consequence | Superseded by |
| --- | --- | --- | --- |
| U01 | Make a comprehensive, finite, resumable plan; avoid endless polish of one slice. | Frozen milestone exits, terminal report, deferred backlog. | — |
| U02 | Commit existing SFM progress before migration; preserve user edits. No push/release. | Scoped local checkpoints; separate plan commit. | — |
| U03 | Substantially reduce physical space wasted under `projections/**/build/sfm-toolchain`, not merely move downloads. | Measured allocated baseline, approved target, retirement, unchanged-build/run growth. | — |
| U04 | Share compatible same-version preparations, and cross-version identical bytes where safe, with readable upstream/versioned paths; avoid opaque custom CAS. | Stage input keys and physical reuse proof. | — |
| U05 | One ignored Git-checkout-local `sfm-toolchain-data` outside projections; Git context required. | Fixed relative paths, no standalone-projection fallback or dynamic Git discovery. | Earlier standalone fallback proposal withdrawn. |
| U06 | Gradle works without Rust and may retain projection-local `build/`; share only compatible artifacts. | Independent Gradle config/tests. | Earlier all-Gradle-output relocation proposal withdrawn. |
| U07 | Keep truthful plain 4.34.0 baseline grounding 4.35.0; version-specific Gradle trimming and baseline retirement are later. | Plain parity gate; deferred backlog. | — |
| U08 | Current `D:\Repos\Minecraft\SFM\sfm-toolchain-data-dir` is not yet approved to move. | Separate cutover approval; retain old roots. | — |
| U09 | Do not delete or free space as workaround; stop if capacity insufficient. Preserve worlds, options, evidence and user work. | Capacity gate; exact separately approved retirement. | — |
| U10 | Keep frozen hashes; qualify dependency closure systematically across projections rather than one missing artifact at a time. | Full workload ledger; no unlocked fallback. | — |
| U11 | The 31 sampled dev-1.19.2 Tinkers remaps have timestamp-only hash variation; normalization is not prerequisite for same-key reuse. | Relevant transformation read-set, validated immutable result. | Earlier hash-count caveat and mandatory ZIP rewrite corrected. |
| U12 | Clean future successful task-owned scratch after readers finish; retain useful failures. | Lifetime/Windows-handle tests. | — |
| U13 | Unrelated project edits must not multiply dependency prep; mutable consumers must not write shared files. | Stage-specific keys and consumer audit. | — |
| U14 | Unchanged *runs* grow snapshots, sessions and launch derivatives; persistent data must survive. | Runtime retention and repeated-run plateau. | — |
| U15 | Cover asset-index/output integrity, partial-entry saturation, absolute receipts and native/Gradle/Prism/server/historical-JAR boundaries. | Explicit correctness gates. | — |
| U16 | Prove cold/warm/offline, two-projection, relevant/irrelevant edits, concurrency, corruption and cancellation. | End-to-end acceptance suite. | — |
| U17 | Preserve GameTest floor/fixture work and exhaustive dependency trajectory; distinguish old receipts from latest source, especially 26.1.2. | Guard task and separate source/build/run/test rows. | — |
| U18 | No new repo/worktree/workspace, Cargo-cache relocation, SDK install/upgrade, OS/security change or unrelated work without exact approval. | Existing checkout and non-goals. | — |
| U19 | Parallelize independent slices with named file ownership while serializing desktop heavy validation. | Ownership table and integration order. | — |
| U20 | Follow surprising storage/hash results to their actual cause and inspect all major artifact lifecycles before treating a design as complete. | Evidence limits, source-backed read-set gate and anomalous-result investigation. | — |

## Guidance traceability

| IDs | Coverage | Evidence when complete |
| --- | --- | --- |
| U01,U19 | Milestones M0–M7, ownership and change control | Closed exits and terminal report |
| U02,U18 | M0 and scope | Local commits, unchanged unrelated status |
| U03,U09 | G1, M1, M5–M7 | Old+new allocated bytes, approved retirement |
| U04,U11,U13 | G2, M2–M3 | Two-projection physical reuse and invalidation |
| U05,U08 | G3, M2, M5 | Relative path tests, approved cutover |
| U06,U07 | G4, M2, M4 | Gradle without Rust and plain-source equality |
| U10,U16,U17 | M4 and M6 | Frozen actual build/package/launch/GameTest ledger |
| U12,U14,U15,U20 | G2,G5, M1–M3 | Source-backed lifecycle, anomaly explanation, interruption, integrity, unchanged-run growth |

## Purpose and scope

**Observable end state:** This Git checkout can bootstrap from frozen inputs, then build and run multiple projections without repeatedly storing separate physical copies of compatible Minecraft/dependency preparations or accumulating successful scratch and unchanged-run derivatives. Gradle remains independent. Allocated bytes across *old and new SFM-owned roots together* fall by a reviewed, evidence-derived substantial target after separately approved retirement. A final ledger shows exactly which projections resolved, compiled, packaged, launched and ran applicable tests.

**Later implementation scope:** Rust shared-input/stage ownership, Gradle relative access only where compatible, retained-output integrity, scratch/run lifetime, frozen closure, safe cutover and measured reclamation. **Not authorized now:** code redesign, move/copy, deletion, install, download, new workspace or release. Exclude a custom opaque CAS unless a gate proves necessary; ZIP normalization is optional. Do not move Gradle `build/`, support copied standalone projections, require cross-checkout sharing, alter Prism/tracked-server internals, or retire plain 4.34. Version-specific Gradle file pruning is separate later work.

## Established foundation and evidence limits

Unless a different path is shown, named local receipts in this section are in `C:\Users\Teamy\Documents\Codex\2026-10-24\task\action-services-evidence\`.

- Starting HEAD `172aa84f4d4cfe382d992a592b1011336387a95d` was ahead of `origin/main`. Local scoped commits `17932200e` (existing cache-root CLI) and `74c58ac2a` (GameTest infrastructure/fixture WIP) preserve reviewed work, without push. Neither proves final-source matrix or new cache architecture.
- Teamy-selected current root `D:\Repos\Minecraft\SFM\sfm-toolchain-data-dir` has a verified C:-to-D: copy: 195,722 files, 16,434,756,688 **logical** bytes, zero copy mismatches (`action-services-evidence/sfm-cache-verification-20261027.json`). Old C: cache and projection builds remain. This was a copy-identity check, not fresh upstream authenticity or reclaimability.
- Projection inventory (`sfm-projection-sizes-20261027.json`) is 137,686,713,407 path-summed **logical** bytes, not physical savings. Nested categories include NFRT/invocation ~68.74 GB, dependencies/Forge/MCP ~45.90 GB, project/snapshots ~8.06 GB and runtime ~7.22 GB. Re-measure; do not add nested totals to the parent.
- All 31 sampled Tinkers remaps were in dev MC 1.19.2; 23 whole-JAR hashes reflect ZIP timestamps with identical entry payloads. Sampled MCP/Forge archives show analogous metadata effects. This narrow result does not equate other artifact inputs.
- Existing NeoForm held-path cache validates immutable reads. Forge/remap restore copies to each project. Current derived namespace deliberately includes projection identity, while some preparation keys include unrelated source/Cargo inputs. Fresh invocation scratch calls `keep()`.
- Unchanged runs still publish snapshots and retain launch sessions/derived files. Snapshot receipts/native markers bind absolute paths. Individual asset objects are verified, but the asset-index download lacks expected-digest validation at its call site; some retained run outputs lack an output-digest check. Partial Forge `entry-*` dirs count toward the 32-entry budget and can cause safe misses/rework.
- Catalog: **74** keys = 20 plain release + 20 extended red/green + 10 dev + 24 other issue-610 variants. The earlier **60** materialized-lock audit found 415 present unique portable paths; it did not run the 74 workloads. The **50-target** plain/extended/dev matrix is distinct. Historic 20-extended FE receipts passed red/green before later guard/fixture edits.
- `C:\Users\Teamy\Documents\Codex\2026-10-24\task\action-services-evidence\SFM-final-guard-next.md`: dev 1.19.2 and 26.1.2 compile/package and selected GameTests passed, then later fixture edits had **no current receipt**. Plain 4.34 dry-runs on 1.19.2 and 26.1.2 also precede later edits. Dev26 AE2 Inscribers selected zero tests; it is an applicability gap, not a pass.
- At drafting, D: had ~52.8 GiB free and C: ~99.1 GiB. The older 8.84 GiB D: measurement is historical. Allocated-byte baseline and safe peak-write estimate remain unmeasured.

## Confirmed geometry and design gates

The Git checkout root is `D:\Repos\Minecraft\SFM\worktrees\sfm-main-source-projection`, whose `.git` is a **file**. `D:\Repos\Minecraft\SFM` contains multiple repositories, so it is not the checkout root. Existing catalog projection path `platform/minecraft/projections/<name>/mc-<version>` reaches the checkout root with **five** `..` components (`../../../../..`), verified with an existing extended 1.19.2 projection; `platform/minecraft` reaches it via `../..`. Proposed checkout-local `sfm-toolchain-data/` is not created or selected and needs a root `.gitignore` rule. Keep it separate from Cargo `target/` and Gradle `build/`.

| Gate | Decide before downstream work | Acceptance |
| --- | --- | --- |
| G1 physical target | Measure allocated bytes, old+new roots, file IDs/links and peak writes; choose numerical **substantial** reduction and unchanged-build/run growth budgets from verified candidates, not invented percentages. | No success claim without measured before/after and reviewed target. |
| G2 stage read-sets | Record ordered inputs, patches, mapping, loader/tool/JDK/platform and mutable readers for MCP, Forge, remap and NeoForm. | Same-key hit; relevant-input miss; unrelated-source hit; shared file never mutated. |
| G3 root/cutover | Confirm baked-in relative literals and one effective override rule in Rust/Gradle; classify path-bound receipts. | Both tools agree; existing D: untouched until separate approval. |
| G4 Gradle/parity | Select only compatible shared artifacts; retain Gradle output and plain 4.34/4.35 historical bytes. | Gradle works without Rust; release source comparison unchanged. |
| G5 lifetime/integrity | Define reader holds, atomic publish, failure evidence, asset-index/output digests, partial-entry recovery. | Corruption, crash, concurrent readers and 32-entry saturation fail closed and recover. |
| G6 applicability | Enumerate 74 keys, profiles and test availability; mark plain/no-tooling cases N/A. | No source-parity-only or count-conflation claim. |

## Support and acceptance populations

| MC target | Plain 4.34/4.35 | Extended red/green | Dev | Other catalog entries | Final-source obligation |
| --- | ---: | ---: | ---: | ---: | --- |
| 1.19.2, 1.19.4, 1.20, 1.20.1, 1.20.2, 1.20.3, 1.20.4, 1.21.0, 26.1.2 | 2 each | 2 each | 1 each | 2 each | Locked resolution, applicable build/package/launch/GameTest, plain fidelity |
| 1.21.1 | 2 | 2 | 1 | 6 | Same, plus explicit visual/legacy variant applicability |

This is **74** catalog keys. Determine actual tasks/tests for each row before calling it supported or N/A. Record source resolution, compilation, package, launch and GameTest as separate columns. A zero-test selector is not success. The 60 audited locks and 50-target core are not substitutes.

## Source map and command shapes

- Root/download plan: `platform/cli/sfm-propagate-changes/src/jar_build/engine_plan.rs:5,128-145,381-449`; cache CLI: `src/paths/cache.rs`, `src/cli/cache/cache_root_cli.rs`. Current env > persisted **absolute** root > platform default is a checkpoint, not the proposed repository-local contract.
- Generated Gradle: `platform/minecraft/core-liquid-template/build/{standard,features/locked_dependency_projection}/gradle/repositories.gradle` and `project-inputs.json`. Gradle CLI `src/cli/gradle/gradle_cli.rs:1288` invokes wrapper from project dir; it does not pass persisted Rust root. Shared location does not imply compatible transforms.
- Reuse: `jar_build/{forge_preparation_cache,dependency_remap_cache,local_dependency_cache,native_cache_root}.rs`; snapshots/runtime: `{build_snapshot_receipt,runtime_session,runtime_slot,engine_run}.rs`; named historical JAR: `engine_named_jar.rs` (four exact 4.34 Forge recipes, separate cache restriction).
- Frozen catalog/locks: `source_projection/{core_catalog,candidate_lock,projection_catalog}.rs`, `platform/minecraft/projections.json`, `core-liquid-template/{feature-definitions,project-inputs}.json`, projection `sfm-toolchain.lock.json` files. GameTest guard/fixtures: `SFMGameTestInfrastructure.java`, `SFMGameTestIntegrityMixin.java`, `docs/tasks/issue-610-cross-version-fixtures.md` and `SFM-final-guard-next.md` evidence.
- `docs/AGENTS.md` directs agents to native CLI rather than Gradle, while Gradle remains a user fallback. Final CLI handoff follows `platform/cli/sfm-propagate-changes/check-all.ps1`, `install.ps1` **after last relevant edit**, and installed path/hash confirmation when safe.

```powershell
$repo = 'D:\Repos\Minecraft\SFM\worktrees\sfm-main-source-projection'
# Resolve $cli to the freshly built executable and $jdk to the exact installed lock-selected JBR first.
& $cli source project compile --repo-root $repo --projection 'sfm-dev/mc-1.19.2' --dependency-profile rust-toolchain --java-home $jdk --output-format json
& $cli source project jar --repo-root $repo --projection 'sfm-dev/mc-1.19.2' --dependency-profile rust-toolchain --java-home $jdk --output-format json
& $cli game-test run-server --repo-root $repo --projection 'sfm-4.34.0-extended/mc-1.19.2' --java-home $jdk --filter 'sfm:issue_610_portable_controls' --output-format json
```

Old JSON receipts or an old installed binary do not prove future edited source. Focused tests and `git -c core.whitespace=cr-at-eol diff --check` are preliminary, not runtime evidence.

## Execution order, parallelism and change control

M0 checkpoint -> M1 measurement/contracts -> M2 root/Gradle contract -> M3 stage and lifetime slices -> M4 final-source/dependency qualification -> M5 separately approved cutover -> M6 separately approved retirement and physical measurement -> M7 closure. M3 and source-only M4 review may overlap after prerequisites. Heavy validation is a **single serialized desktop lane**. No new worktree is implied.

| Track | Exclusive file ownership | Prerequisite / integration order |
| --- | --- | --- |
| A Rust shared stages | `jar_build` preparation/cache modules and `paths/cache.rs` | M2 root; integrate before lifecycle changes in shared files. |
| B Gradle fallback | Generated Gradle templates and generator mappings | M2 fixed relative literal; no A edits; integrate with plain source equality. |
| C integrity/lifetime | Asset/run/snapshot/session modules | G5; source review parallel, edits integrate after A ownership model. |
| D qualification | Ledger/receipts and GameTest fixtures only after explicit issue | Exact integrated source; owns single heavy build/GameTest lane. |

One writer per file. New correctness/safety failures block only affected milestone with evidence and bounded repair; optional refinements go to the deferred backlog rather than extending a satisfied milestone. No correctness blocker may be silently deferred. Review each milestone exit before starting the next expensive sweep.

## Milestones and work contracts

### [x] M0.1 Checkpoint existing SFM source locally

**Completion notes:** `17932200e` commits the existing cache-root CLI, its tests/docs and custom-root cleanup guard. `74c58ac2a` commits opt-in GameTest infrastructure/fixture work as **WIP**. No push. Latest 30-target compile/package/runtime gate remains pending. The five modified `release_report/templates/{article.js,article_layout.css,article_rebuilt_body.liquid,class_tree.js,review.js}` files and untracked `release_report/scripts/test_review_export.mjs` predate this checkpoint (19–20 October); their active owner and validation are not established, so they remain uncommitted. `docs/localization-4.35.0-review/` contains earlier audit receipts and is likewise preserved, not blindly staged. No cache data/evidence blobs were staged.

**Validation:** Before each commit, `git diff --cached --stat` named only intended files and `git -c core.whitespace=cr-at-eol diff --cached --check` passed. `git status --short` records remaining user work.

**Completion criteria:** Separate local commits exist, with unrelated work preserved; met.

### [ ] M1.1 Measure physical ownership and safe write budget

**Work:** Inventory old C: cache, current D: root, proposed root, projection `build/sfm-toolchain`, runtime worlds/evidence and Gradle-owned storage. Report disjoint logical *and allocated* bytes, links/file IDs, free space and peak copy/build footprint. Use bounded category scans rather than hashing 300 GB. Set G1's numerical substantial-reduction target and incremental unchanged-build/run byte budgets only from verified candidates, with review of scope and method.

**Validation:** Reproducible receipt in the existing evidence location; independent sample method and free-space-delta reconciliation. Confirm no active/uncertain session counted as scratch.

**Completion criteria:** G1 has measured physical baseline, reduction target, unchanged-build/run growth budgets, peak budget and stop threshold. If capacity is short, mark `[!]` with needed bytes; never clean or relocate around it.

### [ ] M1.2 Freeze stage inputs and ownership

**Work:** Inventory actual ordered read-sets and consumers for Forge/MCP, NeoForm, remap, native extraction, Gradle, named historical JAR and launch. Classify immutable publication, mutable project output, successful scratch, failure diagnostic, snapshot and world. Decide G2/G5, including absolute receipt paths and Windows reader lifetime.

**Validation:** Source-backed producer/consumer table; focused negative witnesses for changed mapping, patch, tool, JDK/platform, loader and unrelated source edit. Inspect every consumer mutation site before direct sharing.

**Completion criteria:** Each candidate has a complete key, reader/mutation contract and corruption policy; nonshared paths have an explicit owner.

### [ ] M2.1 Establish checkout-local root and relative path contract

**Work:** Add an explicit root `/sfm-toolchain-data/` ignore and ownership marker. Rust uses its known catalog repository identity; generated Gradle resolves baked-in `../../../../..` relative to a catalog project and `../..` relative to `platform/minecraft`, never cwd or dynamic Git probing. Validate expected config/project layout and `.git` file/directory. Define a coherent explicit override, if retained, for both tools; do not silently honor the old machine-global persistent setting or cut over current D: data.

**Validation:** Focused Rust root tests using the existing `.git`-file worktree and another existing ordinary checkout, without creating a repository or worktree; generated Gradle path tests without Rust; wrong depth/link/override negatives and identical effective-path reporting.

**Completion criteria:** G3 contract passes and old data/compiled historical roots are unchanged.

### [ ] M2.2 Preserve Gradle fallback and release parity

**Work:** Expose only compatible verified Maven/inputs through generated Gradle. Keep Gradle wrapper, project-local build outputs and independently resolvable standard dependencies. Gate new source emission or use repo-side configuration to keep plain 4.34/4.35 historical source byte-faithful. Do not prune other-version Gradle files now.

**Validation:** Gradle without Rust on one Forge and one NeoForge projection; compare all 20 plain generated source/resource manifests before/after; check lock projection and artifact bytes separately.

**Completion criteria:** G4 passes, with no plain-release drift or false claim that Gradle transforms equal Rust outputs.

### [ ] M3.1 Share immutable Forge/MCP/remap/NeoForm stages

**Work:** Extend held validated references with readable stage directories and complete relevant-input keys. Exclude unrelated source snapshot churn from dependency-stage keys but retain full frozen graph validation outside the stage. A mutable consumer receives its own copy; all shared publications are atomic, checksum-verified and concurrency-safe. Same-key reuse does not require ZIP timestamp rewriting.

**Validation:** Pilot on two distinct 1.19.2 projections: file IDs/allocated bytes, output hashes and class/resource contents; unchanged and unrelated-source edit hit; mapping/tool/JDK/dependency edit miss; corrupt, concurrent and interrupted publisher negatives.

**Completion criteria:** G2 safe reuse proven; compatible pilot stages have no separate physical output copies.

### [ ] M3.2 Bound successful scratch and partial-entry saturation

**Work:** Stop retaining future successful scratch only after descendants/readers release and immutable outputs publish. Keep compact failure diagnostics; leave old trees pending reviewed retirement. Diagnose/recover incomplete Forge entries and the 32-entry limit without accepting partial bytes or silently forcing repeated local preparation. No broad automatic deletion.

**Validation:** Windows handle/cancellation/crash fixtures; success scratch disappears after release, failure evidence remains; partial, unknown-sibling and exactly-32-entry cases recover through documented verified operation.

**Completion criteria:** G5 passes; retained bytes plateau during repeated successful preparations.

### [ ] M3.3 Stop unchanged-run growth and close integrity gaps

**Work:** Separate reusable launch derivatives from unique session evidence and mutable slot data. Validate asset-index bytes against authenticated parent metadata and retained run output hashes before reuse. Reuse/retire snapshots and launch copies only if inactive and reproducible. Never call saves, options or uncertain live sessions scratch. Keep project-local snapshot ownership and absolute-path receipt checks until an explicit schema change is proven.

**Validation:** Two unchanged builds and three unchanged runs, then a changed input, with allocated bytes by category; corrupt asset index/run JAR fail or regenerate; live/uncertain world and session files stay byte-identical.

**Completion criteria:** G5 integrity and G1 unchanged-run growth bound pass without losing evidence or user state.

### [ ] M4.1 Requalify final GameTest/fixture source and plain fidelity

**Work:** Resume `SFM-final-guard-next.md` from `74c58ac2a`, never old pass counts. Verify one canonical floor, run-block preconditions, helper pre-write rejection, postconditions, destructive opt-in and migrated fixtures. Compile/package final 20 extended and 10 dev targets; test red/green FE behavior and applicable guard witnesses. Plain historical releases must not acquire test/control tooling. Client-only tests require client evidence. Decide the dev26 AE2 zero-selector applicability explicitly.

**Validation:** Per-target `source project compile`, `source project jar`, `game-test run-server` with current commit, source lock, JDK, JAR hash, selected-test count/result and console; plain `source check`/source comparison. One bounded job at a time.

**Completion criteria:** Every applicable final-source core row is proven or `[!]` with a precise blocker; no old receipt fills a fresh row.

### [ ] M4.2 Close frozen dependency resolution for the catalog

**Work:** Enumerate all 74 keys/profiles and reconcile the prior 60 lock audit and 50-target core without merging denominators. Record source resolution, compile, package, launch and GameTest applicability separately for every supported workload. Use isolated/cold locked resolution and offline repeat, including lazy runtime dependencies. Emulate fresh-machine bootstrap from pinned inputs within the existing checkout and an approved empty cache location; do not create a new repository/worktree. Consolidate missing requirements in one manifest; any lock addition is a separate reviewed admission, never an unlocked fallback.

**Validation:** Machine-readable ledger keyed by projection/profile/commit/lock/JDK/tool hash; actual bounded CLI receipts; missing/corrupt pin negatives; code/platform errors separate from missing locks and N/A lanes.

**Completion criteria:** No unexamined agreed supported workload closure; unresolved required rows remain explicit blockers, not counted green.

### [ ] M5.1 Seek separate approval, then cut over selectively

**Work:** Present exact source/destination, peak write estimate, byte-verified immutable reuse list, path-bound receipts to regenerate, rollback config/pointer sequence and no-delete scope. Obtain approval before moving/copying the current D: root. Cut over one project, then a same-version second project, before expanding. Retain old C:/D: data through validation.

**Validation:** Full digest/provenance checks, Rust/Gradle effective root, cold/offline build, unchanged run, two-projection reuse, path/receipt rechecks and rollback rehearsal. Measure *combined* allocated bytes while old and new roots coexist.

**Completion criteria:** Approved cutover works and rollback is demonstrated. Relocation alone is **not** storage reclamation.

### [ ] M6.1 Seek separate exact-list retirement approval and prove savings

**Work:** Inventory only now-proven redundant old roots and projection-local preparation/scratch trees. Present an exact item list with owner, active-reader check, recovery source, digest/receipt and expected allocated bytes. Obtain separate deletion approval. Do not delete worlds, options, evidence, unqualified historical data or uncertain files; stop on low space or identity ambiguity.

**Validation:** Recheck builds/runs/Gradle after authorized retirement. Measure allocated bytes across *old and new roots together* and free-space delta; compare with G1 substantial target and repeated build/run growth bound. Retain rollback until integrity rechecks pass.

**Completion criteria:** Real physical reduction meets G1 target. Otherwise keep milestone open and report measured shortfall rather than claiming success from a move.

### [ ] M7.1 Close the plan and hand off manual testing

**Work:** Rebuild/install final CLI after last source edit under `docs/AGENTS.md` and goal-readiness guide; record source revision, installed path/hash, commands and process/session state. Produce closure report and optional backlog. No push or release under this plan.

**Validation:** `platform/cli/sfm-propagate-changes/check-all.ps1`, `install.ps1`, installed CLI smoke, final frozen/projection ledger and storage results when capacity permits; exact skips/blockers disclosed.

**Completion criteria:** M0–M6 `[x]`, copyable user test commands, measured physical savings and stable growth, no hidden correctness blocker. Mark plan Complete and report bytes actually reclaimed.

## Overall terminal definition of done

- [ ] Checkout-local root approved and ignored; Rust and Gradle independently resolve verified relative paths; plain 4.34/4.35 release source remains truthful.
- [ ] 74-key ledger and applicable 50-target core distinguish source/build/package/launch/GameTest; every required row has current-source proof. Unresolved required correctness rows prevent completion.
- [ ] Frozen pins/runtime inputs remain validated; compatible projections physically reuse stages; relevant changes invalidate; corrupt/partial entries cannot be consumed.
- [ ] Future successful scratch and repeated unchanged **build and run** growth meet measured bounds; worlds/options/evidence remain intact.
- [ ] Separately approved selective retirement achieves measured **substantial allocated-byte reduction across old plus new SFM roots**, meeting reviewed G1 numerical target. Cutover without this is incomplete.
- [ ] Final tool freshness, process state, manual commands, rollback state, commits and deferred optional work are reported; no unauthorized release/push.

## Risk register and deferred backlog

| Risk | Control |
| --- | --- |
| Moved absolute receipts falsely pass | G3/M5 path/schema checks; regenerate incompatible receipts. |
| Shared bytes mutated by a loader/Gradle/consumer | G2 consumer audit, held read-only input or proven necessary local copy. |
| Disk peak or wrong cleanup harms world/evidence | G1 capacity stop, M6 exact approval and owner checks. |
| Cache hit hides wrong bytes/missing source | Frozen outer check, asset/output digest negatives, cold/offline repeat. |
| Gradle silently requires Rust | G4 independent invocation and bounded shared-artifact scope. |
| Unrelated edit invalidates all preparation | G2 stage keys and unrelated-source witness. |
| Sessions grow despite preparation reuse | M3.3 unchanged-run allocated-byte proof. |
| Endless optional refinements | Freeze exits; defer nonblocking item with owner and separate approval. |

**Deferred, not terminal blockers unless a gate exposes a correctness failure:** per-producer deterministic ZIP normalization; cross-checkout sharing; opaque cross-version byte deduplication; version-specific Gradle file pruning; eventual 4.34 baseline retirement; Prism/tracked-server internal cache redesign; unrelated release-report and localization changes. Each needs a separate scoped decision after this plan closes.
