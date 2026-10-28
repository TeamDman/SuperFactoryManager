# Shared SFM toolchain storage and projection closure

**Plan status:** Reviewed; planning complete, implementation not started and no cutover/deletion approved
**Primary root:** `D:\Repos\Minecraft\SFM\worktrees\sfm-main-source-projection` (`main`)
**Last updated:** 2026-10-28
**Intent audit:** Passed 2026-10-28 after three rerun passes and focused independent review
**Independent review:** Passed against Library v3 SHA-256 `4a0fecaa6f9e0c656af96221758b8f9af5d4a901f115e18b09f7bf73c2a32d9c`; implementation gates remain open
**Dependency posture:** Frozen graph; no new dependency or toolchain admission here.

## How to update this plan

Use `[ ]` not started, `[~]` in progress, `[x]` complete, `[!]` blocked only with exact reason and unblock condition. Update the heading and completion evidence together. A phase closes only when all its tasks are `[x]`. Record commits, commands, results and exceptions beside their task, not in a detached log. Follow the first unfinished item, with one heavy desktop build/GameTest job at a time.

## Intent audit evidence

- **Pass 1 — extraction, rerun 2026-10-28:** Reread all 384 lines of the original Library conversation `libfile_429f31f5a1688191900b0be2fc29f4c8`, the latest delegated user quotes and the exact local `resumable-implementation-plans` skill. Split the former mixed ledger into 16 active U requirements plus superseded U04a, six evidence findings F01–F06 and six derived requirements R01–R06. Corrected cross-version reuse to optional, the local root name to proposed, and separated delegated scope restrictions from user quotes.
- **Pass 2 — traceability, rerun 2026-10-28:** Checked all 29 U/F/R IDs against the traceability table and all 19 milestone blocks for work/validation/exit; none were untracked or missing a contract. Inverse check tied stage keys, corruption rules and rollback to recorded findings/requirements; G1 numerical targets remain future evidence-derived decisions, not fabricated measurements. G6 now freezes per-key operations before broad testing.
- **Pass 3 — adversarial omission, rerun 2026-10-28:** Reread the original attachment after the repairs and challenged the contrasts: Gradle without Rust versus common root, Git context versus standalone projection, proposal versus selected path, timestamp-only hashes versus arbitrary sharing, cold bootstrap versus deletion, 4.34 baseline versus future pruning, and old-plus-new savings versus mere relocation. Also checked unchanged runs, worlds/evidence, explicit retirement approval and finite parallel milestones against the latest delegated user direction. Repairs split the M3 producer and integrity work and made required blockers incomplete rather than green.
- **Known source limit:** The Library attachment covers the cache discussion. Earlier GameTest/dependency turns are represented by trusted delegated quotes and local receipts, not a complete verbatim transcript; historical receipts do not prove current source. Independent focused review passed; this source limitation remains.
## Guidance, findings and design requirements

`U` records the user's instruction or preference. `F` records source or measured findings, which are evidence rather than user instructions. `R` records proposed engineering obligations derived from the requested outcome and findings. A later reviewer may challenge an F or R without rewriting what Teamy asked for.

### Authoritative user guidance

| ID | Guidance and provenance | Plan consequence |
| --- | --- | --- |
| U01 | Comprehensive, finite, resumable plan with a real end; avoid polishing one chunk while many independent chunks wait. Delegated user quotes. | Frozen milestone exits, terminal report, optional backlog. |
| U02 | Commit our existing SFM changes before migration; preserve other review edits. Delegated user quotes. | Scoped local checkpoint commits, separate plan commit, no blanket staging. |
| U03 | Achieve a massive reduction of wasted `projections/**/build/sfm-toolchain` space. Delegated user quote. | Evidence-derived physical target, retirement gate, total old+new measurement. |
| U04a | Earlier Teamy wanted content-addressed deduplication. Delegated user quote; **superseded by U04** as a path-format requirement, not as a reuse goal. | No mandatory opaque CAS. |
| U04 | Same-MC-version projections must avoid repeated physical storage; readable upstream/versioned layout preferred. Delegated correction. Cross-version reuse is an opportunity, not a required gate. | Two-projection same-version proof; readable shared paths. |
| U05 | Consider one ignored repository-local toolchain root outside projections, analogous to `target/`; Git context is required; use baked-in relative paths rather than dynamic Git root discovery. Library conversation. Exact root name remains a proposal. | G3 root decision and relative-path contract; no standalone fallback. |
| U06 | Gradle remains useful without Rust and may keep projection-local `build/`; share only compatible inputs. Library conversation. | Independent Gradle proof and separate producer contracts. |
| U07 | Preserve plain 4.34.0 to ground 4.35.0; per-version Gradle file trimming and eventual baseline retirement are later. Library conversation. | Plain parity gate and deferred backlog. |
| U08 | Existing `D:\Repos\Minecraft\SFM\sfm-toolchain-data-dir` was selected for the earlier copy, but its proposed move is not approved. Delegated correction. | No cutover without separate approval. |
| U09 | Do not delete in this analysis; if disk space is insufficient, stop rather than move, clean or free it. Preserve user state. Delegated quotes. | Capacity gate and separately approved exact retirement list. |
| U10 | Qualify dependency pins across projections systematically, without repeated one-at-a-time surprises. Delegated quote. | Applicability-first frozen workload ledger and consolidated missing-artifact manifest. |
| U11 | Explain the surprising remapped-JAR hash divergence and investigate anomalies to causes, not just counts. Library conversation. | Source-backed read-set and anomaly investigation before reuse decisions. |
| U12 | Remove task-owned successful scratch after completion; `tempfile` may help. Library conversation. | Reader-lifetime and failure-retention slice; no blanket deletion. |
| U13 | A clean build should recover from pins and new machines must bootstrap; do not delete build directories before careful analysis. Library conversation. | Cold/empty-cache proof before any approved retirement. |
| U14 | Plan parallel independent work with clear ownership while one desktop heavy job runs at a time. Delegated user quotes and resource coordination. | Named tracks and serialized validation lane. |
| U15 | Preserve the ongoing floor-guard/fixture work and finish actual supported build/runtime/GameTest closure, distinguishing old from latest source. Delegated user direction. | M4 ledger and current-source receipts. |
| U16 | Do not create/clone a new repo, worktree or development workspace without approval; avoid ad hoc Cargo-cache environment relocation. Delegated user quotes. | Existing checkout only; default Cargo target/cache practices. |

**Delegated scope restrictions (P, not attributed to the Library user attachment):** no implementation, migration, deletion, download, SDK/OS change, remote push or release in this planning turn. Future destructive actions have explicit separate approval gates. File/Vox only for any later game control; no computer-use fallback.

### Verified findings, not user instructions

| ID | Evidence and limit | Consequence |
| --- | --- | --- |
| F01 | All 31 sampled Tinkers remaps are dev MC 1.19.2; 23 whole-archive hashes differ only by ZIP timestamps, with identical entry payloads. Local analysis receipt; narrow sample only. | Same-key reuse need not wait for ZIP normalization. |
| F02 | Some preparation identities include unrelated source/Cargo inputs; remap/Forge restores make local copies while NeoForm held references can share immutable bytes. Source in `jar_build`, sampled receipts. | Trace exact read-sets and mutating consumers per stage. |
| F03 | Unchanged runs retain snapshots, launch derivatives and sessions; worlds/options are durable. Runtime source review. | Measure and bound unchanged-run growth without discarding user state. |
| F04 | Asset-index download and some run-output reuse lack the desired digest check; path-bound receipts and partial Forge entries complicate relocation/retry. Source review, not an observed corruption. | Independent integrity and recovery slices. |
| F05 | Catalog has 74 keys; prior audit read 60 materialized locks; 50 plain/extended/dev targets are a distinct core. Catalog and audit receipts. | G6 freezes actual operation applicability before a sweep. |
| F06 | Historic 20-extended and focused dev/GameTest receipts predate later guard/fixture edits; dev26 AE2 selector matched zero. `SFM-final-guard-next.md`. | Fresh source/lock/JDK/tool evidence required. |

### Derived engineering requirements and rejected proposals

| ID | Derived requirement; basis | Gate or slice |
| --- | --- | --- |
| R01 | Stage reuse keys include every actually read transformation input; frozen whole-graph validation remains separate. F01/F02 and U04. | G2, M1.2, M3.1–M3.3 |
| R02 | Immutable publication, held readers, mutable-consumer isolation, atomic concurrency and corruption rejection. F02 and U04/U09. | G2/G5, M3 |
| R03 | Classify 74 keys and supported operations, then freeze required, N/A and external rows before broad execution. F05 and U10/U15. | G6, M1.3, M4 |
| R04 | Measure allocated old+new bytes, link identity, write peak and unchanged-build/run growth; set numerical budgets from evidence. U03/U09 and F03. | G1, M1.1, M3.8, M6 |
| R05 | Preserve compact provenance/failure diagnostics and reconstruct retired artifacts from validated pins. U09/U12/U13 and F04. | G5, M3.4, M5/M6 |
| R06 | Validate stage hits/misses, cold/warm/offline, interruption, corruption and two-projection reuse at the layer claimed. U04/U10/U13 and F01–F06. | M2–M6 |

Rejected **assistant** proposals, not superseded user instructions: mandatory ZIP timestamp rewriting, opaque custom CAS, moving all Gradle output, standalone-projection fallback, dynamic Git root discovery, retiring 4.34 now, and treating path relocation alone as savings. Optional cross-version byte deduplication remains a deferred opportunity.

## Traceability

| Guidance/finding/requirement | Executable coverage |
| --- | --- |
| U01,U14 | Milestone exits, ownership table, change control, M7 |
| U02,U16 | M0, existing checkout and explicit scope |
| U03,U09,R04 | G1, M1.1, M3.8, M5–M7 |
| U04a,U04,U11,F01,F02,R01,R02 | G2, M1.2, M3.1–M3.3 |
| U05,U08 | G3, M2.1, M5 |
| U06,U07 | G4, M2.2, plain parity and deferred backlog |
| U10,U15,F05,F06,R03 | G6, M1.3, M4.1–M4.2 |
| U12,U13,F03,F04,R05,R06 | G5, M3.4–M3.8, M5–M6 |
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
| G6 applicability | Freeze per-key/profile/operation required, N/A, external and blocked rows before broad execution. | 74 keys classified with source-backed reasons; no zero-selector or failure counted N/A. |

## Support populations and the G6 applicability freeze

The catalog has **74 keys**, the prior lock audit covered **60 materialized locks**, and the core release/extended/dev population is **50 targets**. These denominators are not interchangeable. MC 1.21.0 catalog keys use `minecraft_version: "1.21"`; never infer a missing version from that alias.

| Key family | Count | Required before G6 can close | Operation proposal to confirm per key at G6 |
| --- | ---: | --- | --- |
| Plain 4.34.0 and 4.35.0 | 20 | Historical source/resource equality, frozen lock and declared recipe availability for each key. | Native compile/package where the declared historical recipe supports it; ordinary launch only where that route is supported. Added FE fixture/guard GameTests are N/A because plain releases intentionally exclude them. A failing supported recipe is a blocker, not N/A. |
| Extended red/green | 20 | Frozen lock/source and exact paired baseline/fixed behavior for every MC target. | Native compile, JAR, server launch and one selected portable FE GameTest per key, with red second consumer 0 FE and green 100,000 FE after drain. Graphical inspection is a separately identified representative 1.21.1 pair, not a proxy for all-version runtime. |
| `sfm-dev` | 10 | Frozen lock/source, infrastructure-guard coverage and declared dependency profiles. | Native compile/JAR and applicable selected headless guard tests per MC target; client-only tests need a client receipt. The dev26 AE2 zero-match requires an explicit applicability decision, not an inferred pass. |
| Other issue-610/compatibility variants | 24 | Classify every key's declared features, source lock, intended operation and dependency profile. | Source render/lock check for all; compile/JAR/launch/GameTest only for variants whose declared profile and route support them. Record the exact reason and evidence for each N/A. Do not silently promote these into the 50-target core. |

G6 produces a frozen ledger with one row per **key × declared profile × operation**, status `required`, `N/A`, `external`, or `blocked`, reason, source declaration and expected proof. It freezes the set **before** M4's broad sweep. A zero-selector, missing dependency, code error or untested supported route cannot become N/A merely to make the ledger green. Adding a required route after the freeze is a change-control decision with owner, rationale, capacity estimate and new acceptance row.

Gradle-without-Rust has its own Forge/NeoForge smoke rows (M2.2); its local compilation store is not a Rust-stage reuse claim. The named historical-JAR route has four exact 4.34 Forge recipe rows and its own cache restriction; classify whether a relevant command exercises it. Prism and tracked servers are separately owned external routes: record their boundary and evidence needed to avoid an SFM-owned cache claim, but do not redesign their internals. Mutable worlds, options and evidence are never stage-cache rows.
## Source map and executable validation entry points

- Root/download plan: `platform/cli/sfm-propagate-changes/src/jar_build/engine_plan.rs`, `src/paths/cache.rs`, `src/cli/cache/cache_root_cli.rs`. Current environment > persisted **absolute** root > platform default is a checkpoint, not the proposed repository-local contract.
- Generated Gradle: `platform/minecraft/core-liquid-template/build/{standard,features/locked_dependency_projection}/gradle/repositories.gradle` and `project-inputs.json`; `src/cli/gradle/gradle_cli.rs` invokes the wrapper from the project. It does not pass the persisted Rust root. Sharing a location does not imply compatible transforms.
- Stage sources/tests: `jar_build/{forge_preparation_cache,forge_preparation_cache_tests,dependency_remap_cache,dependency_remap_cache_tests,native_cache_root,native_cache_root_tests,held_forge_preparation_outputs}.rs`, plus NeoForm producer/cache call sites identified at G2. Project snapshot: `jar_build/{build_snapshot_receipt,build_snapshot_source_capture}.rs`.
- Runtime/integrity: `container_build/minecraft_asset_index.rs`; `jar_build/{engine_run,runtime_session,runtime_session_receipt}.rs`. Named historical JAR: `jar_build/engine_named_jar.rs` (four exact 4.34 Forge recipes, separate cache restriction).
- Frozen catalog/locks: `source_projection/{core_catalog,candidate_lock,projection_catalog}.rs`, `platform/minecraft/projections.json`, template `feature-definitions.json`/`project-inputs.json`, and generated projection `sfm-toolchain.lock.json` files. Guard: `SFMGameTestInfrastructure.java`, `SFMGameTestIntegrityMixin.java`, `docs/tasks/issue-610-cross-version-fixtures.md` and local `SFM-final-guard-next.md` receipt.
- `docs/AGENTS.md` directs agent validation to the native CLI; Gradle remains a user fallback. `platform/cli/sfm-propagate-changes/check-all.ps1` and `install.ps1` are end gates after the last relevant Rust edit. Never treat the installed executable or an old JSON receipt as proof of current source.

**First safe slice, M1.1 (read-only reconnaissance):** from PowerShell in the existing checkout, without starting a build or creating a workspace:

```powershell
$repo = 'D:\Repos\Minecraft\SFM\worktrees\sfm-main-source-projection'
$evidence = 'C:\Users\Teamy\Documents\Codex\2026-10-24\task\action-services-evidence'
Set-Location -LiteralPath $repo
git status --short
Get-PSDrive -Name C,D | Select-Object Name,Free,Used
Get-Item -LiteralPath 'D:\Repos\Minecraft\SFM\sfm-toolchain-data-dir' -ErrorAction Stop | Select-Object FullName,LastWriteTimeUtc
Get-Item -LiteralPath (Join-Path $evidence 'sfm-projection-sizes-20261027.json') -ErrorAction Stop | Select-Object FullName,Length
```

Those commands are an inventory starting point, **not** an allocated-byte measurement. M1.1 creates a bounded accounting helper in the existing `scripts/` or CLI test area (exact file chosen at that task) that records logical size, Windows allocated size, file IDs/link counts, disjoint categories and free-space deltas. It must have a small fixture/sampling check before scanning major roots; it must not hash all 300 GB. The helper does not exist yet, so no test name is claimed.

**Native qualification after M1/G6 and adequate disk headroom:** use the current source-built executable. This command uses the existing crate's default Cargo target and does not relocate Cargo caches. `--locked --offline` failure is a blocker, never permission for an unlocked retry.

```powershell
$crate = Join-Path $repo 'platform\cli\sfm-propagate-changes'
Set-Location -LiteralPath $crate
cargo build --release --locked --offline -j 1
if ($LASTEXITCODE -ne 0) { throw 'Frozen CLI build failed; retain stdout/stderr' }
$cli = Join-Path $crate 'target\release\sfm-propagate-changes.exe'
if (!(Test-Path -LiteralPath $cli)) { throw 'Fresh CLI missing' }
$cliSha = (Get-FileHash -LiteralPath $cli -Algorithm SHA256).Hash.ToLowerInvariant()
$sourceRevision = (& git -C $repo rev-parse HEAD).Trim()
```

For each projection key, read `platform/minecraft/projections.json` for `minecraft_version` and the generated projection `sfm-toolchain.lock.json`. The current final-guard receipt maps MC 1.19.2–1.20.1 to JBR17, 1.20.2–1.21.1 to JBR21, and 26.1.2 to JBR25. On this desktop the existing homes are `C:\Users\Teamy\.jdks\jbrsdk-17.0.14-windows-x64-b1367.22`, `C:\Users\Teamy\.jdks\jbr-21.0.11`, and `C:\Users\Teamy\.jdks\jbr-25.0.3`. Compare each selected home’s `release` file/major and **any** JDK pin declared in that projection lock before use. The checked dev1.19.2 lock declares exact JBRSDK17; the checked dev1.21.1/26.1.2 locks have no `jdk_pins`, so record those explicit SDK identities without claiming a lock pin. Do not substitute `java` on PATH.

```powershell
Set-Location -LiteralPath $repo
$projection = 'sfm-dev/mc-1.19.2'
$catalog = Get-Content -LiteralPath 'platform/minecraft/projections.json' -Raw | ConvertFrom-Json
$mc = $catalog.PSObject.Properties[$projection].Value.minecraft_version
if ($mc -ne '1.19.2') { throw "Unexpected catalog version: $mc" }
$jdk = 'C:\Users\Teamy\.jdks\jbrsdk-17.0.14-windows-x64-b1367.22'
$jdkRelease = Get-Content -LiteralPath (Join-Path $jdk 'release') -Raw
if ($jdkRelease -notmatch 'JAVA_RUNTIME_VERSION="17\.') { throw 'Wrong JDK major/identity' }
$lock = Get-Content -LiteralPath 'platform/minecraft/projections/sfm-dev/mc-1.19.2/sfm-toolchain.lock.json' -Raw | ConvertFrom-Json
if ($lock.jdk_pins.Count -ne 1 -or $lock.jdk_pins[0].version -ne '17.0.14' -or $lock.jdk_pins[0].build -ne 'b1367.22') { throw 'JBRSDK17 lock mismatch' }
& $cli source project compile --repo-root $repo --projection $projection --dependency-profile rust-toolchain --java-home $jdk --output-format json 1> (Join-Path $evidence 'final-dev1192-compile.json') 2> (Join-Path $evidence 'final-dev1192-compile.err')
if ($LASTEXITCODE -ne 0) { throw 'Compile failed; retain both receipts' }
& $cli source project jar --repo-root $repo --projection $projection --dependency-profile rust-toolchain --java-home $jdk --output-format json 1> (Join-Path $evidence 'final-dev1192-jar.json') 2> (Join-Path $evidence 'final-dev1192-jar.err')
if ($LASTEXITCODE -ne 0) { throw 'JAR packaging failed; retain both receipts' }
& $cli game-test run-server --repo-root $repo --projection $projection --java-home $jdk --filter 'buffer_redstone_*' --output-format json 1> (Join-Path $evidence 'final-dev1192-guard.json') 2> (Join-Path $evidence 'final-dev1192-guard.err')
if ($LASTEXITCODE -ne 0) { throw 'GameTest failed; inspect console and selected count' }
```

Use unique receipt filenames per run, not overwrite the sample names above when retaining earlier evidence. A zero selected-test count is **not** a pass; inspect the CLI JSON, linked console, source revision, lock/JDK identity, JAR hash and actual FE/guard assertions before marking a ledger row complete. For the extended FE pair use each exact extended key and filter `issue_610_portable_controls` (the registered test name); confirm numeric red/green outcomes, not just process exit. For dev26, `water_tank_plus_split` has a prior one-test pass; `ae2_inscribers` had zero selection and needs G6 resolution.

Future test surfaces are created with their task: M2.1 relative-root tests, M3.1–M3.3 stage hit/miss/integrity tests, M3.4 Windows scratch/partial tests, M3.5–M3.8 asset/run/snapshot/session tests. Existing `forge_preparation_cache_tests.rs`, `dependency_remap_cache_tests.rs` and `native_cache_root_tests.rs` provide starting fixtures; no new test name is asserted to exist. Each task records the exact `cargo test --locked --offline <actual-filter>` command after adding its tests, then runs the relevant native CLI acceptance command only when the serialized heavy lane is available.
## Execution order, parallelism and change control

M0 checkpoint -> M1 physical/read-set/applicability gates -> M2 root and Gradle contract -> independently closable M3 producer/integrity/lifetime slices -> M4 frozen final-source workload closure -> separately approved M5 cutover -> separately approved M6 retirement/savings -> M7 closure. M3.5 asset-index and M3.6 run-output integrity can implement after G5 without waiting for every shared producer; M3.7 snapshots follows G3; M3.8 session retention follows the run/snapshot ownership decisions. The heavy desktop build/GameTest lane remains **single-job and serialized**. No new worktree is implied.

| Track / owner boundary | Exclusive files while active | Prerequisite and integration order |
| --- | --- | --- |
| A1 Forge/MCP | `jar_build/forge_preparation_{cache,inputs,producers,capture}.rs` and related tests | G2/M2.1; integrate M3.1 before M3.4 touches the same cache. |
| A2 remap | `jar_build/{dependency_remap_cache,dependency_deobf_producer,local_dependency_cache}.rs` and related tests | G2/M2.1; independent of A1 implementation until shared interface integration. |
| A3 NeoForm/native | `jar_build/native_cache_root.rs` and G2-identified NeoForm producer/consumer files | G2/M2.1; preserve current held-reference behavior. |
| B Gradle fallback | Generated Gradle templates and generator mappings | M2 relative contract; no A files; integrate with plain source equality. |
| C1 asset/run integrity | `container_build/minecraft_asset_index.rs` for M3.5; `jar_build/engine_run.rs` for M3.6 | G5 contracts; independently implementable before all producer slices. |
| C2 snapshot/session | `jar_build/{build_snapshot_receipt,build_snapshot_source_capture}.rs` for M3.7; `{runtime_session,runtime_session_receipt}.rs` for M3.8 | G3/G5, then run-output interface; never edit an A file concurrently. |
| D qualification | Applicability ledger/receipts; GameTest fixture source only on a separately justified bug | G6 freeze and integrated source; owns the serialized heavy lane. |

One writer per file; explicit handoff at overlap. Freeze milestone exit criteria before coding. A new correctness/safety blocker requires source evidence, a bounded repair and new proof for the affected slice; optional refinements go to the deferred backlog with owner. Do not silently defer correctness or let an optional improvement extend an already satisfied milestone.
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

### [ ] M1.3 Freeze the applicability ledger before the broad sweep

**Work:** Parse all 74 `projections.json` keys and declared features/profiles. Populate one row per key × operation with `required`, `N/A`, `external`, or `blocked`, citing the source declaration and expected receipt. Reconcile the 20 plain, 20 extended, 10 dev and 24 additional variants with the prior 60-lock audit without promoting an untested route to supported. Classify the four named historical-JAR recipes, Gradle fallback rows and Prism/tracked-server boundary separately. Freeze the required set and owner before M4.

**Validation:** Source-only catalog count and key/profile review; spot-check one key from each family and the 1.21.0/`1.21` alias. A zero-test selector, missing lock or failed supported recipe is a blocker, never N/A. The ledger format/checker is created in this task; no existing all-74 applicability test is claimed.

**Completion criteria:** G6 contains every catalog key and operation with evidence-backed status and a recorded scope-freeze revision. Later route additions require explicit change control and capacity estimate.
### [ ] M2.1 Establish checkout-local root and relative path contract

**Work:** Decide the checkout-local directory name at G3 (current candidate `sfm-toolchain-data/`), then add a root ignore and ownership marker. Rust uses its known catalog repository identity; generated Gradle resolves baked-in `../../../../..` relative to a catalog project and `../..` relative to `platform/minecraft`, never cwd or dynamic Git probing. Validate expected config/project layout and `.git` file/directory. Define a coherent explicit override, if retained, for both tools; do not silently honor the old machine-global persistent setting or cut over current D: data.

**Validation:** Focused Rust root tests using the existing `.git`-file worktree and another existing ordinary checkout, without creating a repository or worktree; generated Gradle path tests without Rust; wrong depth/link/override negatives and identical effective-path reporting.

**Completion criteria:** G3 contract passes and old data/compiled historical roots are unchanged.

### [ ] M2.2 Preserve Gradle fallback and release parity

**Work:** Expose only compatible verified Maven/inputs through generated Gradle. Keep Gradle wrapper, project-local build outputs and independently resolvable standard dependencies. Gate new source emission or use repo-side configuration to keep plain 4.34/4.35 historical source byte-faithful. Do not prune other-version Gradle files now.

**Validation:** From the generated `platform/minecraft/projections/sfm-dev/mc-1.19.2` and `.../mc-26.1.2` directories, run `& .\gradlew.bat --offline jar -PsfmProfile=rust-toolchain` directly, with no Rust executable invocation; capture stdout/stderr, exact task graph and exit. These checked directories have `gradlew.bat` and `build.gradle`; task/dependency failure remains a blocker, not proof of independence. Compare all 20 plain generated source/resource manifests before/after; check lock projection and artifact bytes separately.

**Completion criteria:** G4 passes, with no plain-release drift or false claim that Gradle transforms equal Rust outputs.

### [ ] M3.1 Publish one reusable Forge/MCP preparation

**Work:** In `forge_preparation_cache.rs`, its producers/inputs and `held_forge_preparation_outputs.rs`, define a relevant-input recipe for Forge/MCP with ordered artifacts, patches, mapping, loader/tool/JDK/platform identity. Keep full frozen-graph checks outside that narrower stage key. Publish one immutable checksum-verified result; copy only for a demonstrated mutating consumer. Do not normalize ZIP timestamps merely to make keys work.

**Validation:** Extend existing `forge_preparation_cache_tests.rs` with same-key, relevant-change, unrelated-source-change, corrupt-publication and two-projection readers; record actual test filter/command after adding it. Native pilot on two same-MC projections records file IDs and allocated bytes, not only hashes.

**Completion criteria:** One compatible Forge/MCP recipe produces one safely shared physical result; differing relevant inputs stay distinct and all readers are validated. This slice can close before remap/NeoForm work.

### [ ] M3.2 Share a validated dependency remap

**Work:** In `dependency_remap_cache.rs`, `dependency_deobf_producer.rs` and `local_dependency_cache.rs`, separate remap inputs from project source identity. Implement the shared publication/hold interface decided at G2 without waiting for the Forge slice; do not assume remap and Forge payload formats match. Audit every consumer for mutation before direct shared references; record unavoidable local copies.

**Validation:** Extend existing `dependency_remap_cache_tests.rs` with two 1.19.2 projections, changed mapping/tool/classpath and unrelated source edit, plus corrupt/concurrent publisher cases. Compare archive entry payloads and allocated bytes; timestamp-only divergence is not a correctness miss.

**Completion criteria:** Same remap recipe hits and physically reuses one verified result; relevant changes miss; mutable downstream operations cannot alter the shared file.

### [ ] M3.3 Preserve and extend NeoForm/native held reuse

**Work:** Inspect G2's exact NeoForm producer/consumer files plus `native_cache_root.rs`; reuse its existing held immutable-reference pattern only where loader-native extraction and recipe inputs match. Keep MC/version/platform/native layout boundaries explicit. Do not infer that all NeoForm invocation directories are reusable outputs.

**Validation:** Extend `native_cache_root_tests.rs` and producer-specific tests chosen at G2: shared same-recipe reader, changed version/loader/JDK miss, corrupt output rejection and two-projection allocated-byte sample. Record the actual tests and command when implemented.

**Completion criteria:** At least one compatible NeoForm/native stage is shared without a second physical copy; every excluded invocation/intermediate has an owner and reason.

### [ ] M3.4 End successful scratch retention and recover partial entries

**Work:** After M3.1 has exclusive `forge_preparation_cache.rs` ownership, stop `tempfile` `keep()` on successful task-owned scratch only when all processes/readers exit and immutable outputs publish. Keep compact failure diagnostics. Recognize incomplete `entry-*` candidates before the 32-entry limit and recover safely without reading partial bytes. Existing old directories remain until M6 approval.

**Validation:** Add Windows handle, cancellation, crash, unknown-sibling and exactly-32-entry regression cases in the Forge preparation test area. Prove successful future scratch disappears after release while failed-run evidence remains; run one bounded real preparation twice and measure growth.

**Completion criteria:** G5 scratch/partial behavior is proven and repeated successful preparation has bounded allocated growth.

### [ ] M3.5 Validate the Minecraft asset index independently

**Work:** In `container_build/minecraft_asset_index.rs` and the authenticated parent-metadata call site, require expected digest/size for the index before reusing or publishing it. Continue per-object verification. This integrity repair may begin after G5 without waiting for M3.1–M3.3.

**Validation:** New focused index tests (created in this task) cover valid, absent, corrupt and interrupted index; a bounded client/server asset-resolution smoke records the parent digest and source revision. No existing index-corruption test is claimed.

**Completion criteria:** A corrupt or partial index cannot be a cache hit, and valid unchanged resolution remains stable.

### [ ] M3.6 Validate reusable run outputs independently

**Work:** In `jar_build/engine_run.rs` and its actual output-receipt caller, check the stored output digest as well as input state/existence. Separate immutable launch derivatives from mutable slot output; keep version/loader/JDK/platform in the derivative contract. Do not alter live session files.

**Validation:** New focused run-output tests (created in this task) corrupt a retained JAR and change an input; a bounded unchanged run proves a validated hit and records output hash. Inspect actual console rather than process exit alone.

**Completion criteria:** Existing bad bytes fail closed or rebuild; correct same-key output is reusable without another large physical derivative.

### [ ] M3.7 Reuse snapshots only behind valid path-bound receipts

**Work:** In `jar_build/{build_snapshot_receipt,build_snapshot_source_capture}.rs`, keep project-owned snapshot identity and absolute-path checks until a versioned relocation schema is proven. Deduplicate only immutable, inactive, reproducible snapshot components; preserve source and diagnostic provenance. This slice follows G3 but can progress apart from Forge/remap implementation.

**Validation:** New snapshot reuse/relocation tests (created here) exercise unchanged build, changed source, moved root, stale absolute receipt and concurrent reader. Two unchanged builds record physical bytes by snapshot category.

**Completion criteria:** Unchanged snapshots stop multiplying immutable bytes; path relocation cannot silently admit stale outputs.

### [ ] M3.8 Bound session growth without discarding user state

**Work:** After M3.6/M3.7 ownership decisions, classify `runtime_session.rs`/`runtime_session_receipt.rs` files as world/options, evidence, unique session state or reproducible launch derivative. Reuse/retire only inactive derivatives after reader checks. Keep worlds, options, selected evidence and uncertain sessions intact. Set a G1 numerical unchanged-run growth budget from M1 measurements.

**Validation:** Two unchanged builds and three unchanged runs plus one changed input, with old+new allocated bytes by category; byte-compare a saved world/options and retained evidence before/after. Use File/Vox for later game control, never foreground automation.

**Completion criteria:** Repeated unchanged runs meet the measured G1 budget, while persistent user state and exact session provenance survive.
### [ ] M4.1 Requalify final GameTest/fixture source and plain fidelity

**Work:** Resume `SFM-final-guard-next.md` from `74c58ac2a`, never old pass counts. Verify one canonical floor, run-block preconditions, helper pre-write rejection, postconditions, destructive opt-in and migrated fixtures. Compile/package final 20 extended and 10 dev targets; test red/green FE behavior and applicable guard witnesses. Plain historical releases must not acquire test/control tooling. Client-only tests require client evidence. Decide the dev26 AE2 zero-selector applicability explicitly.

**Validation:** Per-target `source project compile`, `source project jar`, `game-test run-server` with current commit, source lock, JDK, JAR hash, selected-test count/result and console; plain `source project check`/source comparison. One bounded job at a time.

**Completion criteria:** Every G6-required final-source core row has current-source proof. A required blocked row makes M4.1 `[!]` and **incomplete** until repaired or a separately approved support-scope change; N/A has its own source-backed reason. Old receipts never fill a fresh row.

### [ ] M4.2 Close frozen dependency resolution for the catalog

**Work:** Enumerate all 74 keys/profiles and reconcile the prior 60 lock audit and 50-target core without merging denominators. Record source resolution, compile, package, launch and GameTest applicability separately for every supported workload. Use isolated/cold locked resolution and offline repeat, including lazy runtime dependencies. Emulate fresh-machine bootstrap from pinned inputs within the existing checkout and an approved empty cache location; do not create a new repository/worktree. Consolidate missing requirements in one manifest; any lock addition is a separate reviewed admission, never an unlocked fallback.

**Validation:** Machine-readable ledger keyed by projection/profile/commit/lock/JDK/tool hash; actual bounded CLI receipts; missing/corrupt pin negatives; code/platform errors separate from missing locks and N/A lanes.

**Completion criteria:** Every G6-required catalog operation has a current-source locked-resolution/build/runtime receipt as applicable. Any unresolved required row keeps M4.2 incomplete; an external or N/A row has explicit owner/reason and cannot mask a missing dependency.

### [ ] M5.1 Seek separate approval, then cut over selectively

**Work:** Present exact source/destination, peak write estimate, byte-verified immutable reuse list, path-bound receipts to regenerate, pre-retirement rollback config/pointer sequence and no-delete scope. Obtain approval before moving/copying the current D: root. Cut over one project, then a same-version second project, before expanding. Retain old C:/D: data through validation.

**Validation:** Full digest/provenance checks, Rust/Gradle effective root, cold/offline build, unchanged run, two-projection reuse, path/receipt rechecks and rollback rehearsal. Measure *combined* allocated bytes while old and new roots coexist.

**Completion criteria:** Approved cutover works; while old bytes remain, reverting configuration/pointers and validating old receipts is demonstrated. Relocation alone is **not** storage reclamation.

### [ ] M6.1 Seek separate exact-list retirement approval and prove savings

**Work:** Inventory only now-proven redundant old roots and projection-local preparation/scratch trees. Present an exact item list with owner, active-reader check, recovery source, digest/receipt and expected allocated bytes. Obtain separate deletion approval. Before retirement prove the exact pinned reconstruction path and any offline-cache prerequisite for each candidate. Do not delete worlds, options, evidence, unqualified historical data or uncertain files; stop on low space or identity ambiguity.

**Validation:** Recheck builds/runs/Gradle after authorized retirement. Measure allocated bytes across *old and new roots together* and free-space delta; compare with G1 substantial target and repeated build/run growth bound. Once old files are deleted, direct pointer rollback is unavailable: recovery means restoring known configuration/receipts and rebuilding from verified frozen pins and retained or explicitly authorized downloads. Demonstrate that path before deletion and keep its instructions/receipts afterward.

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
| Sessions grow despite preparation reuse | M3.8 unchanged-run allocated-byte proof. |
| Endless optional refinements | Freeze exits; defer nonblocking item with owner and separate approval. |

**Deferred, not terminal blockers unless a gate exposes a correctness failure:** per-producer deterministic ZIP normalization; cross-checkout sharing; opaque cross-version byte deduplication; version-specific Gradle file pruning; eventual 4.34 baseline retirement; Prism/tracked-server internal cache redesign; unrelated release-report and localization changes. Each needs a separate scoped decision after this plan closes.
