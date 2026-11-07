# Finish the projection migration

**Plan status:** Active
**Primary implementation root:** This checkout, branch `main`
**Review baseline:** `44f238318` (2026-11-07)
**Last updated:** 2026-11-07
**Intent audit:** Passed against both original user requests in this session

## Resume and update

Start at the first unfinished work item. `[ ]` means not started, `[~]` in progress,
`[x]` complete, and `[!]` blocked by a stated external condition. Update each heading
and its completion notes together. Keep evidence beside the behavior it qualifies;
name superseded evidence explicitly. Use small local commits. Do not publish,
upload, push or create release tags.

## Authoritative user guidance and traceability

| ID | User direction | Consequence |
| --- | --- | --- |
| U1 | Review work since 4.34.0 to improve confidence in unreleased 4.35.0; preserve the existing migration. | Retain the foundation/findings below; qualify a selected candidate rather than discard or repeat the migration. |
| U2 | Work locally with the user hands-on in VS Code. | Separate cleanup, selection, guards and provider integration into inspectable steps. |
| U3 | Get the working tree clean first and explain the dirty JSON/build files. | Classify every initial dirty group in phase 1 before restoring, retaining or committing it. |
| U4 | Save findings and a plan under docs so work survives compaction. | This file owns the next steps, decisions, evidence and limits. |
| U5 | Assess review/blog/localization material; useful work may remain until a later purge decision. | Preserve unique bytes before cleanup; do not blindly delete drafts or commit generated dossiers. |
| U6 | Finish migration away from CLI dependence on the flat source tree. | Inventory production callers and migrate their semantics before retiring the tree. |
| U7 | Publishing must ask which projection and reject inappropriate release selections. | Require exact selection, release environment, release-version feature and selected metadata agreement. |
| U8 | Do not actually publish or make accidental release changes. | No provider writes/uploads, release creation/amendment, push, tags, credential use or publication commands. |
| U9 | Apply TeamyStyle to Rust organization and state/recovery design. | Separate arguments, validated requests, operations and results; give invariants an owner. |
| U10 | Wait for the user on disk-space errors instead of resolving them ourselves. | Stop affected work on capacity errors; no cache deletion or archival offload to make room. |

The user's projection-first direction supersedes older branch-first migration
advice. Existing no-Gradle guidance still applies. The supplied TeamyStyle document
is design guidance, not authorization for unrelated rewrites or publication.

## Intent audit evidence

- Extraction: reread the initial review request and cleanup/migration follow-up;
  retained their ordering, preservation concerns, hands-on preference and no-publication
  constraint as U1-U10.
- Traceability: U3/U5 map to phase 1, U4 to this file, U6/U7/U8 to phases 2-3,
  U1/U2/U9 to the foundation/work boundaries/validation, and U10 to operational limits.
- Adversarial omission: checked that cleaning preserves useful drafts, authored
  build inputs are not treated as outputs, preparation does not authorize publication,
  and flat-tree removal waits for caller migration. Release/dev distinctions remain.
- Source limitation: none for the two user requests. Historical execution records
  were inspected; their original workloads were not rerun.

## Verified foundation

History anchor: `4.34.0-1.19.2`, commit
`31135b8e86801b862d5cb2283c7c5878b7cc5bb4` (2026-07-04). Review reached
`44f238318` after 1,450 commits. Product/UI work accumulated before September;
projection migration began in late September; shared Liquid authoring and the
original twenty-context native matrix completed by October 7. Later work expanded
projection-based run/test commands, shared caches, SDK pins, runtime ownership,
resumable validation and Linux support.

Authoring uses `platform/minecraft/core-liquid-template/`, with contexts in
`platform/minecraft/projections.json` and membership/features in the core metadata.
Current catalog: 74 contexts, ten Minecraft targets, 25 feature sets, 214 registered
features. Twenty contexts use release semantics; 54 use development semantics,
including extended/portable/visual release-looking names. Plain 4.35.0 enables only
`recipe_namespace`, `release_version_4_35_0` and
`quantity_sensitive_extraction_progress`; dev 1.19.2 enables 190 features.

The core's `build/` contains authored scripts/lockfiles, explicitly unignored in
`.gitignore`. It is not disposable output. The adjacent flat `platform/minecraft/src`
and root Gradle project were outside the October cleanup scope; active callers
remain. See [the retirement record](../tasks/sfm-projection-retirement-plan.md).

## Findings to preserve

Rust paths below are relative to `platform/cli/sfm-propagate-changes/`. Findings are
source/history inspection, not new runtime certification.

| ID | Finding and consequence | Source / next proof |
| --- | --- | --- |
| F1 | Matrix compile reuse accepts any nonempty native-project directory, skipping lower-level compiler inventory checks even when actual outputs are missing/damaged. | `src/cli/check_everything.rs::output_available`, `execute_one`, test `missing_compiler_output_invalidates_reuse`; test full resume with an unrelated entry plus missing/corrupt compiler output. |
| F2 | Matrix cancellation kills/waits only its immediate CLI child; descendant Java quiescence is not established. | `check_everything.rs::operation`, `jar_build/launch_argument_file.rs`, `nfrt_child_process.rs`; qualify an owned child/grandchild cancellation fixture. |
| F3 | Forge keys hash entire Rust files and Cargo.lock; formatting can rekey large preparations. NeoForm conservatively retains whole-lock digests. Same MC version does not imply equal producer inputs. | `jar_build/forge_preparation_inputs.rs::recipe_basis`, `local_dependency_key.rs`; compare two normal same-version keys and expose typed reuse/decline reasons before changing key policy. |
| F4 | Catalog manifestation writes files sequentially and may leave a partial tree after later failure. Ordinary project check has weaker resource-membership rules than native ownership admission. | `source_projection/manifestation.rs::manifest_selected`, `sync.rs::sync_catalog_projection`, `catalog_owned_project.rs`; specify recovery and test stale omitted resources. |
| F5 | Provider publishing and helpers still use flat metadata, configured global JARs and branch selection. Catalog CWD does not select a projection. | Caller inventory and phases 2-3 below. |
| F6 | Plans contain superseded status statements; warm reuse, cold reconstruction, expected-red tests, graphical acceptance and physical retirement are separate results. | Shared-cache plan and [one-row offline qualification](../tasks/sfm-cloud-offline-one-row-20261105.md); that retained result covers one projection's check/compile/JAR only. |
| F7 | Private catalog ownership types are useful TeamyStyle foundations; important states elsewhere use strings/options and large textual engine includes. | `CollectedCatalogProject` to `CatalogOwnedProject`, `ForgePreparationInputs`, `LocalDependencyCache`, `check_everything.rs`, `jar_build/engine.rs`; refactor by owner/state in bounded changes. |

Preserve per-key locks, staged cache publication, held inputs, compiler output
inventories and private catalog ownership. Do not replace them with directory
conventions or weaken key validation to increase hit counts.

## Phase 1: establish a clean local checkpoint

### [x] 1.1 Classify and preserve initial dirty work

**Completion notes:** all 18 initial tracked modifications and the exporter test
were copied byte-for-byte into `.local-review/projection-migration-2026-11-07/`.
The 49-file localization dossier was moved into that archive and reverified.
All 68 original file hashes match. Exactly ten tracked cache-formatting/report
files were restored to the review baseline after verifying source and saved bytes;
eight authored locks remain unchanged for their scoped commit. The archive has a
relative-path inventory, restoration notes and a binary-capable patch. Manifest
SHA-256: `A005961B295DB16094BA613D13D0A96E5EB5A95198F2FF94600E740950143778`.
Patch SHA-256: `23B85D9CDC0D7F243D2AD360D6FD635AF90E421E192590C4ED30DC1D2C68E28B`.
No unique work was deleted. Node/exporter/browser checks remain unperformed, so
report changes are preserved drafts rather than committed release behavior.

| Initial group | Verified meaning | Disposition |
| --- | --- | --- |
| Five jar_build cache/input/test files | Formatting/import changes only; two whole-file-hashed production inputs would rekey preparations. | Save exact bytes and a diff locally, verify them, then restore only those five files to the review baseline. |
| Eight authored schema-4 lockfiles | Only jdk_pins added: 170 lines. Exact JBR17/JBR21 identities are already tracked elsewhere; all 35 affected emitted locks contain them. | Retain/commit as a scoped Windows SDK-selection checkpoint. |
| Five release-report templates plus untracked exporter test | Coherent navigation/export/article draft. Node is unavailable even outside the sandbox; connected Node execution times out. Browser/render checks have not run. | Preserve all six files together locally before restoring tracked templates. Do not certify or discard the draft. |
| docs/localization-4.35.0-review | 49 files/4,710,991 bytes; 42 JSON documents parse and 34/34 report hashes match. Three source bindings are stale. Unique review decisions and pinned PR inputs remain useful; six files contain machine-specific paths. | Preserve the complete dossier locally. Later extract a sanitized compact ledger if wanted; do not commit the generated dossier. |

Use Git-ignored `.local-review/` for recoverable copies and relative-path SHA-256
inventories. Recheck both saved and current bytes before restoring/moving. No broad
reset, clean or recursive deletion. Preserve local archives before a later purge
or checkout removal; they are not public or long-term evidence storage.

Pin additions are Windows-only. Targets 1.20.2-1.20.4 need compiler Java 17 plus
native-tool Java 21. No Linux support is established. Removing only the two Vox
runtime declaration keys introduced by `44f238318` from an in-memory comparison of
authored data makes all 35 current emitted locks semantically equal to their selected dirty authored inputs. That later Vox
drift is separate work: do not synchronize it incidentally during cleanup.

**Validation:** compare non-pin fields to HEAD and pins to tracked authorities;
verify every preserved hash and scoped diff. No Minecraft build/provider access.
**Completion:** every initial dirty path has a disposition and unique bytes remain
recoverable without deleting unrelated output, caches or evidence.

### [x] 1.2 Commit reviewed inputs and verify clean status

**Completion notes:** SDK pins are committed as `886371d2c`; the plan and scoped
archive ignore rule form a separate documentation checkpoint. After those two
checkpoints, `git status --porcelain` returned no entries. All eight lockfiles
passed the non-pin-field and exact Windows SDK identity comparison. The reference
authorities additionally contain Linux artifacts; these eight additions do not
copy them or establish Linux support. All 68 original file hashes and all 42
archived JSON documents passed verification. Plan links and portable-path checks
passed; staged whitespace checks passed after trimming a new EOF blank line.
Nothing was pushed, tagged or published. No new Rust implementation, Minecraft
build, application launch or installer was run. Report/exporter/render/browser
qualification remains deferred with the preserved draft. Resume at phase 2.1.

**Work:** commit retained SDK pins separately from plan/ignore policy. Keep archives
ignored. Record commits and an empty final `git status --porcelain`. Do not push.
**Validation:** inspect staged diffs; scoped whitespace/JSON checks; verify archive
hashes after cleanup. Review drafts remain unqualified pending their own tests.
**Completion:** clean tree, recoverable drafts, and committed actual cleanup evidence.

## Phase 2: define catalog-native release selection

### [ ] 2.1 Close the no-network preparation contract

**Work:** propose an exact `--projection` and explicit/discovered repository root.
Separate `CatalogReleasePlanArgs` to validated request to preparation operation to
typed report; give domain types discoverable files. Names are provisional. Require
release environment, an explicit release-version feature matching expected version,
selected rendered metadata agreement and current generated ownership. Agreement
includes expected mod version, release feature, rendered `mod_version`, target ID,
advertised Minecraft version and loader policy. Target `1.21.0` and Minecraft
version `1.21` remain distinct and need a fixture. Key spelling alone proves none
of these. Historical 4.34 reproductions remain buildable; they do
not implicitly become new publication candidates without the selected policy.
Read properties/changelog via `CatalogOwnedProject::selected_input`. Credentials and
transport stay outside preparation. Report metadata and artifact readiness separately.
**Validation:** synthetic exact/unknown/traversal selectors, dev/missing-flag rejection,
version mismatch and stale outputs; prove no network, credential, tag or upload effect.
**Completion:** CLI, typed invariants, report and side-effect boundary agree and have
no-network fixture coverage. Existing historical source-legacy commands are not relabeled.

### [ ] 2.2 Guard legacy release entry points

**Work:** reject branch/global-JAR publication, provider check/validate, collection
and tagging when either invocation checkout or configured legacy input root is a catalog checkout. Reject
before secrets, notes-file writes, popularity-cache reads or transport. Global roots
must not override projection intent. Specify deliberate legacy-checkout behavior.
**Validation:** catalog CWD with unrelated configured root; catalog configured root
with outside CWD; rejection without configuration effects; retained legacy fixtures.
Old `--dry-run` is not a universal no-network proof.
**Completion:** catalog use cannot silently dispatch to a legacy publisher; help
points to the explicit catalog preparation route.

## Phase 3: migrate consumers before retiring flat inputs

### [ ] 3.1 Prepare provider adapters and release helpers

**Work:** consume one reviewed candidate's metadata and verified assets through pure
GitHub/CurseForge/Modrinth payload adapters. Preserve loader/version policy, artifact
ownership/hash, changelog identity and create/update distinctions. Replace branch-era
collection/tag planning. Actual provider writes and tag creation remain out of scope.
**Validation:** pure metadata/payload fixtures, artifact drift refusal, no flat-file
fallback, no-network integration through the preparation CLI.
**Completion:** projection-specific, inspectable handoffs exist without publication.

### [ ] 3.2 Migrate remaining active flat-tree consumers

**Work:** distinguish runtime/audit/promotion consumers from tests, historical oracles
and report links. Migrate production semantics; retain pinned comparisons. Remove the
flat tree only after active callers and contributor documentation no longer depend on it.
**Validation:** scoped source search and meaningful caller tests; zero matches alone
are not proof of a working workflow.
**Completion:** every production reference has a replacement or explicit retirement.

## Caller inventory

All paths are relative to `platform/cli/sfm-propagate-changes/src/`.

- GitHub: `cli/github/github_cli.rs::release_now` and amend read flat properties,
  changelog and global JARs; existing-release uploads may clobber.
- CurseForge: `cli/curseforge/release/curseforge_release_{now,amend,check,validate}_cli.rs`
  reads flat version inputs; now/amend also use flat changelog.
- Modrinth: now/amend/check/validate read flat properties; changelog reading is in
  `cli/modrinth/modrinth_cli.rs`.
- Global selection/helpers: `cli/repo_root/repo_root_cli.rs`, `cli/jar/jar_dir_cli.rs`,
  `cli/git/git_tag_cli.rs`, `cli/jar/jar_collect_cli.rs`.
- Further owners to classify before retirement: `source_projection/{development_baseline,
  candidate_lock,release_apply,release_baseline}.rs`, `cli/source/source_cli.rs`,
  `source_audit/{source_audit_command,version_surface_audit}.rs`, `propagate.rs`,
  generated-resource handling in `cli/git/status/git_status_cli.rs`, and the runtime
  mapping/hotswap search in `jar_build/engine_run.rs`.

Reuse `CoreCatalog::load/context`, checked catalog ownership and pure provider
payload types. Historical `source legacy release-*` preflights target retired
mc-version ownership; they cannot simply be renamed into a catalog publisher.

## Phase 4: qualify tooling and the selected candidate

### [ ] 4.1 Validate and install after final CLI implementation

**Work:** freeze dependency versions/recipes. Cleanup retains eight existing pin
additions only; it authorizes no new dependency/platform expansion. After new Rust
implementation run focused tests, required full gate and installer. No Gradle or
graphical workloads during cleanup/preparation.

From `platform/cli/sfm-propagate-changes`:

```powershell
./check-all.ps1 -TestWorkers 1 -TestThreads 1
./install.ps1
sfm-propagate-changes --version
```

The gate mutates formatting and can affect producer source identities; inspect its
diff and record final source/binary identity. Check capacity before compilation.
On disk-space errors wait for the user; do not clean/offload caches. Verify root
and nested help plus the no-network preparation handoff.
**Completion:** focused/full gates, install and exact binary checks qualify the
final implementation; manual commands and remaining limits are explicit.

### [ ] 4.2 Record candidate-specific release evidence

**Work:** choose the intended 4.35 projection family and ten-target scope, then bind
source/catalog/features/lock/SDK/CLI/artifact/test-selector identities. Separate
historical expected failures from green results and graphical from headless checks.
**Validation:** explicit per-target required-check ledger with applicable receipts;
no substitution of stale binaries or older plan prose.
**Completion:** readiness and missing evidence are reviewable; publication is not implied.

## Operational readiness and limits

At review start PATH CLI reported `c9271aab9`, checkout debug `717b8d64d`, and HEAD
was `44f238318`. Neither executable qualifies current HEAD. No game/build process
was started or stopped. Cleanup uses metadata/preservation checks and does not
qualify application behavior or refresh the installed tool. New implementation
requires phase 4. Initial disk preflight left little room for large builds; check
again instead of assuming historical capacity. Raw machine paths and reports stay
in the ignored archive, not this public-capable document.
