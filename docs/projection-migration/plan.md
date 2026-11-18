# Finish the projection migration

**Plan status:** Tooling migration complete; candidate artifact/runtime qualification pending
**Primary implementation root:** This checkout, branch `main`
**Review baseline:** `44f238318` (2026-11-07)
**Last updated:** 2026-11-17
**Intent audit:** Passed against the available user instructions and retained U1-U14 ledger, including the raw-map correction (U15), validation-delay follow-up (U16), first-pass dictionary correction (U17) composite-field proxy request and confirmed typed mapping forms (U18), domain contracts (U19), concrete SFM witnesses (U20) and the production assertion policy (U21) and explicit invocation homes (U22) and checked SFM witness conversions (U23)

## Resume and update

The authorized migration goal is complete. All changes remain uncommitted for
review. The [candidate ledger](release-readiness.md) owns the remaining native
artifact, mod-test and gameplay qualification; publication needs later authorization.
Older phase checkpoints below are retained history.

Start at the first unfinished work item. `[ ]` means not started, `[~]` in progress,
`[x]` complete, and `[!]` blocked by a stated external condition. Update each heading
and its completion notes together. Keep evidence beside the behavior it qualifies;
name superseded evidence explicitly. Leave all changes uncommitted for user review. Do not publish,
upload, push or create release tags.

## Validation workflow for subsequent changes

Apply this workflow within the existing required checks and review boundaries.

1. Choose the smallest useful proof before editing. Parser changes need parsing,
   schema and byte-preserving edit coverage. Repeat all-projection CLI checks when
   the changed behavior warrants them; a parser refactor does not imply that scope.
2. Finish the patch, tests and review before expensive compilation. If a required
   source edit appears during a run, stop downstream stages; the earlier artifacts
   do not qualify the revised source.
3. Format the touched files and run the required repository gate before expensive
   test builds. Protect dirty work from its global formatter. Any continuation past
   known failures must compare diagnostic messages and source paths, not just counts.
   New or unexplained failures stop the pipeline before build/runtime stages.
4. Reuse tests and builds already completed by a successful gate. If verified
   baseline failures prevent the gate reaching tests, run one focused test command
   for the remaining evidence. Keep target directory, features and build options
   stable. Build the operational CLI once after the final source edit if the gate
   has not already done so; distinguish it from the all-feature test product.
5. Record results once beside the task. Documentation-only follow-ups need document
   and diff checks, not another Rust build. Repeat a check only for changed relevant
   inputs, a failure or an unresolved concern, and state what the repeat will prove.

Work item 4.1 repaired the red gate and runner partition. A
possible later build improvement is a small crate for pure parsing/domain code:
libtest filters reduce execution scope but still compile the current CLI crate.
That extraction is a proposal, not started work; assess its dependency/cache cost
before expanding this migration.

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
| U11 | Proceed with phase 2.1 and leave changes uncommitted for review. | Implement/test that slice without staging or committing it; phase 2.2 remains separate. |
| U12 | Read-only commands still create normal logs; automatic logs belong under the Rust CLI application directory, not invocation CWD. | Preserve default and explicit logging; qualify read-only claims as source/build/provider effects and test application-root log routing. |
| U13 | Create `GradleDotPropertiesFileContent` to own the scattered Gradle-properties parser, then identify similar newtype opportunities. | Implement the content owner and both named callers in 2.1a; record evidence-backed candidates without silently implementing a broader migration. |
| U14 | Parse our properties into a named-field structure using Facet renames and typed version ranges, rather than retaining a one-field text wrapper. | 2.1b supersedes the raw-wrapper design with a complete SFM schema, scalar/range parsing and a separate source-span document for byte-preserving edits. |
| U15 | Use a keyed raw properties document with key/value newtypes before parsing the stronger model; retain Facet renames for dotted keys. | 2.1c introduces `RawGradlePropertiesDocument` as a unique-key map, moves key/value parsing into their newtypes and keeps source-preserving edits separate. |
| U16 | Explain and avoid excessive delays on small changes. | Use the validation workflow above: finish the patch before compilation, check cheap failures first, stop downstream work on new errors and repeat only justified checks. Existing required checks remain in force. |
| U17 | The first `.properties` pass is only dictionary parsing with string values; let Facet convert to stronger fields, including a `SemverVersionRange` newtype. | 2.1d removes span-bearing raw intermediates, gives the raw line parser only string key/value results, invokes range parsing through Facet/`FromStr` and isolates source editing in `GradlePropertiesVersionEdit`. |
| U18 | Keep a date/Minecraft-version pair as a tuple in the domain model; use a field proxy such as `MinecraftDateVersionPairProxy` for its string parsing instead of a pervasive pair newtype. Preserve all authored mapping forms as typed variants. | 2.1e wires `Option<MinecraftMappingVersion>` through `MinecraftMappingVersionProxy`, delegates date-pair parsing to `MinecraftDateVersionPairProxy` and preserves source mappings separately from the cross-version target. |
| U19 | Ground the eight mod metadata fields in their actual domain types and investigate platform constraints, including mod IDs and GitHub usernames. | [Metadata type contracts](mod-metadata-types.md) separates loader/platform rules from SFM policies; 2.1f owns implementation and constructor/codec acceptance. |
| U20 | Implement the agreed types with source-linked rustdoc and zero-sized concrete SFM values; convert infallibly into generic values and fallibly back, while keeping generic helpers usable. | 2.1f adds seven fixed identity witnesses, an exact const-generic version witness and a checked SFM document with immutable access to its generic snapshot. Its forward-conversion choice is superseded by U23. |
| U21 | Reject production `expect`/`unwrap` in SFM, following Cloud Terrastodon, and change conversion APIs where needed. | 2.1g adopts Clippy deny rules with test-only allowances and explicit const checks; production callers propagate errors instead of asserting prior validation. |
| U22 | Resolve application and cache homes once at the top-level CLI invocation; pass selected homes into operations. | 2.1h removes global/lazy resolution, injects homes into logging/settings/build helpers, preserves cache-root repair after selection failures and retains guarded native-cache precedence. |
| U23 | SFM fixed-value conversions must validate their values with `TryFrom`; do not bypass generic domain parsing. | 2.1i replaces all five remaining unchecked witness conversions and enforces a fallible conversion contract for all eight witnesses. |
| U24 | Set a goal and complete the identified work, including the larger release-preparation slice, remaining flat-tree consumers and qualified retirement. | Execute 2.2, 3.1, 3.2 and 4 in coherent batches; preserve the index and uncommitted work, frozen dependencies, explicit homes and no-publication boundary. |

The user's projection-first direction supersedes older branch-first migration
advice. Existing no-Gradle guidance still applies. The supplied TeamyStyle document
is design guidance, not authorization for unrelated rewrites or publication.

## Intent audit evidence

- Extraction: reread the initial review, cleanup/migration follow-up, phase 2.1
  authorization, logging clarifications, parser-ownership and structured-schema
  corrections, the raw-map request, validation-delay follow-up and explicit first-pass
  dictionary correction, composite-field proxy request and confirmation to preserve
  all mapping forms as typed variants. U1-U18 preserve ordering, review without
  commits, normal application-root logs, hands-on work and no publication.
- Traceability: U3/U5 map to phase 1, U4 to this file, U6/U7/U8 to phases 2-3,
  U1/U2/U9 to the foundation/work boundaries/validation, U10 to operational limits,
  U11 to the uncommitted phase 2.1 boundary, U12 to operation scope/logging tests,
  U13 to 2.1a and the newtype review, U14 to 2.1b and the structured schema/corpus
  tests, U15 to 2.1c and raw-map/duplicate-policy coverage, U16 to the validation
  workflow above, U17 to 2.1d and the raw-string/Facet conversion boundary, and
  U18 to 2.1e and the resolved typed-variant/tuple/proxy representation, and
  U19/U20 to 2.1f and source-linked generic/concrete metadata types.
- Adversarial omission: checked that cleaning preserves useful drafts, authored
  build inputs are not treated as outputs, preparation does not authorize publication,
  and flat-tree removal waits for caller migration. Release/dev distinctions remain.
  File syntax stays separate from SFM policy. U14 replaces the wrapper with parsed
  fields; optional presence, escaped description text and original edit bytes remain distinct.
  U15 requires a map before typed conversion; duplicate keys must fail before
  insertion can overwrite a value. Original edit bytes and unrelated repeated lines
  remain separate from the unique-key raw view. Candidate identification does not
  authorize implementing every proposed type. Read-only does not prohibit normal
  diagnostics; automatic logs must not follow CWD. U16 reduces repeated work while
  retaining required checks, source/binary freshness and honest baseline failures;
  it does not authorize a crate extraction or skipping the repository gate.
  U17 keeps every first-pass value a string, including invalid domain syntax; only
  Facet conversion may invoke `SemverVersionRange::from_str`. The previous
  `Range<usize>` fields were source-byte spans, not version intervals. Their removal
  makes the raw dictionary boundary explicit while preserving separate edit behavior.
  U18 retains the tuple inside typed mapping variants: official mappings need no
  fabricated date, and cross-version Parchment keeps its source mapping version
  distinct from the target Minecraft version. The mapping-form decision is closed;
  that wiring is qualified by the 2.1e evidence below. U19/U20 extend the domain
  model with source-linked metadata contracts and checked concrete SFM identities
  while preserving generic parsing and helpers.
- Source limitation: the raw-map and first-pass dictionary corrections and available user instructions were
  reread on 2026-11-12, including the tuple/proxy request and typed-form confirmation. Earlier execution details rely on the retained ledger and
  task-level evidence; unavailable compacted exchanges were not reread or rerun.

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
and root Gradle project were outside the October cleanup scope. Phase 3.2 retired
them on 17 November after migrating their active consumers. The
[October retirement record](../tasks/sfm-projection-retirement-plan.md) retains the
earlier scope; current completion evidence is below.

## Findings to preserve

Rust paths below are relative to `platform/cli/sfm-propagate-changes/`. Findings are
source/history inspection, not new runtime certification.

| ID | Finding and consequence | Source / next proof |
| --- | --- | --- |
| F1 | Matrix compile reuse accepts any nonempty native-project directory, skipping lower-level compiler inventory checks even when actual outputs are missing/damaged. | `src/cli/check_everything.rs::output_available`, `execute_one`, test `missing_compiler_output_invalidates_reuse`; test full resume with an unrelated entry plus missing/corrupt compiler output. |
| F2 | Matrix cancellation kills/waits only its immediate CLI child; descendant Java quiescence is not established. | `check_everything.rs::operation`, `jar_build/launch_argument_file.rs`, `nfrt_child_process.rs`; qualify an owned child/grandchild cancellation fixture. |
| F3 | Forge/MCP/remap keys hash entire producer files and Cargo.lock; formatting or an unrelated dependency edge can rekey preparations. Ordinary NeoForm local-dependency identity hashes selected host sources instead; Linux immutable-profile lookup separately hashes the CLI executable. Same MC version does not imply equal producer inputs. | `jar_build/forge_preparation_inputs.rs::recipe_basis`, `jar_build/local_dependency_cache.rs::implementation_identity`, `jar_build/derived_toolchain_cache.rs`; compare same-version keys and expose typed reuse/decline reasons before narrowing key policy. This corrects the earlier unqualified NeoForm whole-lock claim. |
| F4 | Catalog manifestation writes files sequentially and may leave a partial tree after later failure. Ordinary project check has weaker resource-membership rules than native ownership admission. | `source_projection/manifestation.rs::manifest_selected`, `sync.rs::sync_catalog_projection`, `catalog_owned_project.rs`; specify recovery and test stale omitted resources. |
| F5 | Provider publishing and helpers still use flat metadata, configured global JARs and branch selection. Catalog CWD does not select a projection. | Caller inventory and phases 2-3 below. |
| F6 | Plans contain superseded status statements; warm reuse, cold reconstruction, expected-red tests, graphical acceptance and physical retirement are separate results. | Shared-cache plan and [one-row offline qualification](../tasks/sfm-cloud-offline-one-row-20261105.md); that retained result covers one projection's check/compile/JAR only. |
| F7 | Private catalog ownership types are useful TeamyStyle foundations; important states elsewhere use strings/options and large textual engine includes. | `CollectedCatalogProject` to `CatalogOwnedProject`, `ForgePreparationInputs`, `LocalDependencyCache`, `check_everything.rs`, `jar_build/engine.rs`; refactor by owner/state in bounded changes. |
| F8 | The required gate formats unrelated source before linting, and existing strict-Clippy failures prevent later checks. The bounded test runner also rejects its existing partition: four substring overlaps and seven omitted tests. These mechanisms can change source identities or prevent qualification. | `check-all.ps1`, `scripts/{test-bounded,bounded-library-plan}.ps1`; phase 2.1 restored 41 verified formatter/temporary-annotation changes and ran checks separately. Repair lint debt and partition ownership as a separate slice. |
| F9 | Java-analysis integration fixtures do not fully control cache and JDK-source selection. The partial-index consumer selects a branch-local cache instead of the fixture home; the semantic matrix leaves JDK sources ambient and misses three expected definition links. | `cli/symbol/symbol_index_cli.rs::build_query_index`, `tests/java_analysis/java_analysis_scenario_test.rs`; routing changes `6a1cd91ed4`/`14c0702431` and matrix setup `2354726360` predate this slice. Reproduce with explicit cache/SDK identities; do not accept new snapshots to conceal either failure. Exact missing JDK input remains unqualified. |

Current dispositions: F5 is resolved for catalog CLI admission and offline
preparation; F8 is resolved by the passing gate and corrected runner; F9 is
resolved by explicit cache/SDK fixtures and their passing scenarios. F6 now has
current status and historical labels. F1-F4 and the broader architecture work in
F7 remain separate follow-ups; this goal did not claim those cache-policy changes.

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

## Authorized completion goal (2026-11-16)

U24 authorizes completion of the migration previously summarized in chat. The
required core is the complete no-publication release-preparation workflow and
retirement of the flat working source tree; remaining tooling and candidate
qualification must be reported at their proven scope. Leave all changes
uncommitted. Do not stage, reset, publish, upload, push or create tags. Dependencies
and their lockfiles remain frozen; no game, server or Gradle execution is needed
for this source/tooling migration. Existing historical oracle paths remain valid.

The preservation checkpoint is under ignored
`.local-review/finish-projection-migration-2026-11-16/`: tracked input hashes,
exact relevant originals, HEAD and staged-entry listing. The starting worktree
already has 352 changed paths. Restore only proven formatter changes and preserve
all unrelated user work. No new worktree or cache layout is authorized. Stop on a
disk-space error and wait for the user.

Execution ownership is intentionally parallel: the release guard owns CLI dispatch;
Java tools own projection-aware analysis/audits/probes; editor tooling owns the
extension, native tasks/wrappers and contributor guidance. The primary agent owns
catalog candidate/artifact/provider preparation, integration, remaining provenance
and Git classifications, validation and the physical retirement admission.

1. Complete 2.2 together with 3.1 and release-specific 3.2 work. Add a checked
   candidate/artifact contract before claiming artifact readiness. Source metadata
   alone, a matching filename or a user-authored report cannot establish that a JAR
   was built from the selected inputs. Reuse the existing native build authority.
2. Complete other 3.2 consumers with explicit projection inputs. Keep historical
   branch/commit tools explicit; never treat a raw Liquid template as ordinary Java.
   Editor and executable-example consumers use rendered bytes.
3. Qualify coherent patches with the existing required gate before expensive tests.
   Repair new failures immediately; record known gate debt without calling it a
   pass. Complete 4.1 tooling qualification and final installation after the final
   source edit. Candidate 4.2 observations cover the ten plain `sfm-4.35.0` release
   keys; they do not certify gameplay or authorize publication.
4. Before retiring the 2,797 tracked flat source files, prove preserved originals,
   classify dirty/untracked inputs and all active callers, check replacement
   workflows, and preserve historical comparisons. Do not recursively delete an
   unchecked computed path or include ignored runtime/cache data in retirement.

**Intent audit for this extension:** extraction reread the available original
requests and the immediately preceding file/batch summary, adding U24 without
weakening U1-U23. Traceability maps guards to 2.2, checked provider preparation to
3.1, runtime/editor retirement to 3.2, and tooling/candidate evidence to 4.1/4.2.
Adversarial review preserves uncommitted review, logs under the supplied home,
explicit cache selection, domain construction checks, no production assertions,
frozen dependencies and the no-publication boundary. Older execution details rely
on the retained task evidence, not a claim of fresh artifact certification.

## Phase 2: define catalog-native release selection

### [x] 2.1 Close the no-network preparation contract

**Completion notes:** implemented and left uncommitted for review. The command is
`source release plan`, requiring an exact projection and expected mod version.
Arguments become a validated request; preparation checks current catalog ownership
and reads retained selected properties, lock and changelog inputs. The report keeps
target ID, upstream version, package loader, metadata verification, artifact readiness
and provider readiness separate. It records the exact selected changelog section and
input hashes. The build-time base revision is a hint; exact tool-source identity
remains unknown. No artifact or publication readiness is inferred.

Normal application-root and explicit CLI logging remains enabled per U12.
Read-only describes source, generated output, build and provider state. Tests resolve
the real platform-default destination from two invocation directories without writing
into the user's application home; isolated child processes create real logs under an
application-home override. Explicit `--log-file` configuration retains its normal
destination. Production logging policy is unchanged.

| Final validation | Result and scope |
| --- | --- |
| Scoped non-mutating Rust format check | Passed for every new Rust file. |
| Focused all-features tests | 32 passed, zero failed, one ignored child helper exercised by parent tests; 21.91 seconds. |
| Complete all-features library, one test thread | 2,478 passed, zero failed, 30 ignored; 674.13 seconds. Ignored retained/environmental workloads are not qualified. |
| Five integration targets, direct execution | 111 passed, two failed in Java analysis. The complete integration suite has not passed. |
| All-features and default-feature builds | Both passed. Explicit bin/doc test commands passed with zero tests defined there. |
| Fresh default CLI help/version and real metadata plans | Root/source/release/plan help passed. All ten 4.35.0 release projections passed using explicit-root and nested-checkout discovery; `1.21.0` reports upstream `1.21`. Invocation directory entries stayed unchanged. Normal logging remained enabled. |
| Required full gate / final strict Clippy | Gate stopped at Clippy. Final scoped source has 72 errors outside the new release implementation and none in it. No broad suppressions or unrelated lint fixes were added. |
| Bounded runner | Artifact compilation passed; partition admission failed on four overlaps and seven omissions, all present at baseline. Direct execution above supplies separate evidence. |

The two failing integration tests are `java_interaction_map_semantic_matrix_scenario`
(missing expected JDK definition coverage) and `usage_at_position_partial_index`
(isolated-cache path check). Static triage found pre-existing branch-cache routing
bypassing the fixture home and ambient JDK-source dependence in the semantic matrix;
the exact unavailable JDK input remains unqualified. See F8/F9 for follow-up.
No snapshots were accepted. The full gate remains red, so the installed CLI was not
refreshed; phase 4.1 retains that qualification/install obligation. The gate's 41
unrelated formatter/temporary-annotation changes were verified, preserved locally
and restored before final checks.

The tested default executable is version `0.1.1`, base-revision hint `d16af8c38`,
built 2026-11-07 18:33:35 -05:00. SHA-256:
`515c66709577f834e08177dc2efceb9fd703b9973f1333c476ca6cc9750be189`.
This identifies the executed local file; it does not establish a reproducible build
or exact tool-source provenance. From the checkout root, use the local review
launcher to avoid the older PATH installation:

```powershell
./.local-review/phase-2.1/run-cli.ps1 --output-format json source release plan --projection sfm-4.35.0/mc-1.21.0 --expected-mod-version 4.35.0
```

Compilation used an isolated temporary Cargo target, jobs/test threads 1, incremental
disabled, dev/test debug symbols disabled and dependencies offline. Existing
projection/toolchain roots were unchanged. Local commands, logs, the changed-Rust
input manifest, CLI observations and validation summary are in ignored
`.local-review/phase-2.1/`; they are local review evidence. No stage, commit,
publication or legacy guard change was made. Implementation baseline is `d16af8c38`.
Phase 2.2 remains the next implementation slice; the flat source tree still serves
the callers listed below.

**Work:** use an exact `--projection` and explicit/discovered repository root.
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
Normal diagnostic log creation remains permitted and must use the CLI application
directory by default, independently of the invocation directory.
**Completion:** CLI, typed invariants, report and side-effect boundary agree and have
no-network fixture coverage. Existing historical source-legacy commands are not relabeled.

### [x] 2.1a Give Gradle properties parsing one owner

**Completion notes:** `GradleDotPropertiesFileContent` owns UTF-8/physical-line
validation, escaped-key recognition, selected-key uniqueness, literal access and
byte-preserving edits. `CatalogReleaseProperties::from_gradle_properties` retains
SFM policy; `release_version` delegates parsing/replacement to the shared owner.
Bare CR and CR/LF replacement injection are rejected. The
[newtype review](newtype-review.md) records other consumers' compatibility rules and
ranked candidates; those proposals are not implemented by this slice. No dependencies,
logging policy or publishing behavior changed. Existing staged work and the user's
CLI invocation edit were preserved; the refactor was not staged or committed.

| Validation | Result and scope |
| --- | --- |
| All-features focused parser/caller tests | 41 passed, zero failed/ignored, 2,473 filtered; 18.42 seconds. Includes five new content tests, artifact preservation on rejection, catalog planning and frozen authoring/release rewrites. |
| Scoped non-mutating Rust format check | Passed for the new type and three changed parser callers. |
| Bounded-runner ownership | New root module registered; all five new tests have exactly one owner. The existing eleven omitted/overlapping tests still prevent full partition admission. |
| Default-feature CLI build | Passed. Normal PATH installation remains older because full qualification is still red; use the local launcher below. |
| Fresh CLI metadata checks | 1.19.2 with explicit root and 1.21.0 with nested-checkout discovery passed. The latter retains upstream `1.21`. Normal logging stayed enabled; properties and invocation-directory entries were unchanged. |
| Required full gate | Stops at 74 existing Clippy diagnostics outside the changed parsers, with none in those parsers. Each attempt's 41 formatting-only changes were verified and restored to exact pre-check bytes. Full qualification has not passed. |

The first gate attempt stopped earlier on a missing generated tree-sitter resource.
Its build script copies that resource unconditionally. Reconstructing only that
package's derived outputs in the isolated Cargo target resolved the missing file;
no dependency source or Minecraft cache changed. This was an incomplete-build repair,
not disk-capacity cleanup. There was no disk-space error.

The tested default binary's SHA-256 is
`b6c7de2d8ea6e22618f1ea3adad6b7aceb7ef904fc5e74c9cc3856b82c35039c`. The recorded Rust input bytes
still match the validation manifest. Local commands/logs, initial index identity,
formatting proofs and validation summary are in ignored
`.local-review/newtypes-2026-11-08/`. From the checkout root:

```powershell
./.local-review/phase-2.1/run-cli.ps1 --output-format json source release plan --projection sfm-4.35.0/mc-1.21.0 --expected-mod-version 4.35.0
```

**Superseded freshness:** the executable hash and tests in 2.1 describe the
2026-11-07 implementation before this extraction. They do not qualify the refined
parser; final validation belongs here. Existing gate/integration failures remain
recorded until newer evidence replaces them.

**Work:** keep one discoverable type file with private contents and associated
parsing/access/edit methods. Move the two named consumers to that API, retaining
release-version/SFM policy in domain types. Identify other types by their invariant
and callers rather than wrapping every string.
**Validation:** byte-preserving LF/CRLF/EOF edits, Unicode offsets, aliases/duplicates,
malformed escapes/continuations, literal replacement refusal and unchanged artifacts
on failure; existing catalog selection and release/frozen rewrite tests. Run the
required gate and record its limits. Preserve the index and use a fresh local CLI
for the manual review route.
**Completion:** both named callers share one syntax owner; behavior guards and
remaining migration policies have test/source evidence, with changes uncommitted.

### [x] 2.1b Parse complete SFM project properties

**Completion notes:** `GradleDotPropertiesFileContent` is a 23-field parsed model.
Fifteen common fields are required; eight version-dependent fields preserve absence
through `Option`. Dotted keys use Facet renames, Gradle switches are booleans, pack
format is `u32`, and Minecraft/Neo/loader ranges have included, excluded or unbounded
endpoints. The range is opaque to reflective construction; Facet invokes its
`FromStr` parser, and Rust accessors expose its structured bounds.

`GradlePropertiesDocument` holds parsed entries and source spans for version-only
edits, including historical fragments. Values decode properties escapes, including
the description's `\n`. ASCII delimiter whitespace after `=` is skipped; escaped
leading space and trailing space remain data. SFM policy reads named fields in the
parsed model. There is no string-key lookup in the release metadata consumer.
See the [model and compatibility review](newtype-review.md).

The pinned Facet dependency enables `reflect`; a safe `Partial` builder uses
rename-aware field lookup and scalar parsers without JSON conversion. The lockfile
adds one dependency edge to already-locked `teamy-facet-reflect`; package versions
and checksums are unchanged. Forge/MCP/remap keys hash the whole lockfile (F3), so
this rebuilt CLI uses new preparation keys on those paths. Later native builds can
repeat preparation and retain additional outputs. No native preparation was run;
ordinary NeoForm local-dependency identity does not directly share that whole-lock
rule.

| Final validation | Result and scope |
| --- | --- |
| Focused all-features parser/caller tests | 57 passed, zero failed/ignored, 2,473 filtered; 21.14 seconds. Covers all 31 authored property files, typed fields and errors, escaped values, source-preserving edits, catalog/frozen callers and reflection's range-construction boundary. |
| Non-mutating format check | Passed for the five format/model/range files and changed callers. |
| Bounded-runner ownership | All 21 parser/range tests have exactly one configured owner. The eleven existing omitted/overlapping tests still prevent full partition admission. |
| Default-feature CLI build | Passed using the isolated offline Cargo target. |
| Fresh CLI help/version and metadata checks | Root/source/release/plan help and all ten 4.35.0 release contexts passed, using explicit root and nested discovery. `1.21.0` retains upstream `1.21`; invocation-directory entries stayed unchanged. Normal application-root logging remained enabled. |
| Required full gate | Stops at 74 existing Clippy diagnostics outside the new parser/range code, with none in it. The gate and repository-wide qualification have not passed. Its 41 formatting-only changes were byte-verified and restored. |

A disk-space error interrupted the first gate's review-evidence write. Work stopped
until the user freed space. Recovery proved and restored 38 remaining formatting-only
files, with three already restored; staged contents stayed intact. Retry source
snapshots are persisted before the formatter runs, so restoration is recoverable.
No files or caches were deleted to resolve the capacity error.

The tested default binary's SHA-256 is
`60a69d9644d4d2879b25427a53b0fcc6cd37a3865693c4275f2ccb1d59b321d0`. Parser/caller Rust input hashes
still match the final manifest. Earlier 2.1/2.1a binary hashes and test counts are
historical and do not qualify this model. Local commands, source/formatting proofs,
CLI reports and the validation summary are in ignored
`.local-review/structured-properties-2026-11-08/`.

The installed CLI was not refreshed while full qualification remains red. From the
checkout root, use the tested local binary through this launcher:

```powershell
./.local-review/phase-2.1/run-cli.ps1 --output-format json source release plan --projection sfm-4.35.0/mc-1.21.0 --expected-mod-version 4.35.0
```

Expected: metadata is verified, artifact/provider readiness remains unassessed or
unprepared, and publication is not authorized. Existing staging is preserved; this
refinement remains uncommitted. This evidence qualifies the 2.1b implementation;
2.1c subsequently refined raw storage; 2.1d now revises the first-pass boundary
and needs fresh validation before phase 2.2.

**Work:** parse the actual authored schema, reject missing/unknown/duplicate fields,
and retain explicit optional settings. Preserve version identifiers and qualifiers;
range structure does not assert Maven ordering or artifact compatibility. Keep
byte-preserving authoring separate from structured values. Update metadata fixtures
to complete projects instead of weakening required fields for synthetic fragments.
**Validation:** all 31 authored property files, typed booleans/numbers/ranges,
renamed keys, decoded descriptions, missing/unknown/duplicate properties, source
span/edit preservation, catalog/frozen callers, bounded-test ownership, required
gate and a fresh local CLI across ten release projections. Preserve staging and
leave changes uncommitted; no publication, Gradle or Minecraft launch.
**Completion:** production consumers read named parsed fields; all authored file
variants and edit contracts have current evidence, with remaining qualification
failures stated.

### [x] 2.1c Parse raw properties into a unique-key map

The following evidence qualifies the completed 2.1c implementation. Its span-based
editing representation, source hashes and binary are historical for 2.1d below.

**Completion notes:** `RawGradlePropertiesDocument` owns a
`BTreeMap<GradlePropertiesKey, GradlePropertiesValue>` and the Facet conversion.
`GradlePropertiesKey` owns the literal ASCII key grammar;
`GradlePropertiesValue` owns decoded value escapes. The stronger
`GradleDotPropertiesFileContent` parses this raw map, retaining Facet renames for
external dotted names, required fields and scalar/range parsing.

`GradlePropertiesDocument` is the editing view. It retains source bytes and a
`BTreeMap<GradlePropertiesKey, Vec<Range<usize>>>` of value occurrences, preserving
unrelated duplicate lines. Its version edit still requires exactly one
`mod_version`. Both views use `GradleProperty::parse_document` to iterate physical
lines with shared syntax handling. Raw-map construction rejects every duplicate
key before insertion; it cannot silently retain the first or last value. The
editing view remains usable for historical fragments without requiring the full
SFM schema.

**Work:** separate unique-key raw values from source occurrence data, with each
newtype owning its parser. Move Facet deserialization to the raw map and keep
version editing byte-preserving. Add no dependency changes, staging, commits,
installation or publication. Preserve existing staged contents.

**Validation:** 2.1c source at `d16af8c38` plus its then-uncommitted edits;
these results do not qualify the subsequent 2.1d refinement.

| Check | Result |
| --- | --- |
| Focused parser and release-caller tests | 63 passed, zero failed; includes all 31 authored property files, Facet dotted renames/aliases, identical and conflicting duplicates, scalar/range failures and byte-preserving edits. |
| Parser/range runner ownership | All 27 tests have exactly one configured owner. Added an explicit raw-module skip to the editing-module job because libtest filters match substrings. The full runner retains its 11 baseline omitted/overlapping tests. |
| Required `check-all.ps1 -TestWorkers 1 -TestThreads 1` | Stops at the same 74 Clippy diagnostics as 2.1b; messages and source paths were compared, with no added diagnostics. All 41 formatter-only changes were proven and restored to the saved pre-gate bytes; no unproven mutation remains. |
| Fresh default-feature CLI | Built offline and locked after the final Rust edit; root/nested help and version pass. All ten 4.35.0 release metadata plans pass, including nested discovery and the 1.21.0-to-1.21 upstream distinction. Normal logging remains enabled and invocation directories are unchanged. |
| Change preservation | HEAD and the complete staged-entry listing match the start of 2.1c. Cargo.toml/Cargo.lock and the recorded final source/script inputs retain their expected hashes. |

The consuming `parse(String)` constructor retains its original zero-copy
`into_bytes` delegation. An initial map gate found an ownership lint after it was
changed to borrow the string; that warning was corrected before rerunning the
checks above. The isolated Rust target also needed the existing narrow tree-sitter
build-output repair for a missing generated file. This was not a disk-capacity
cleanup; dependency declarations and native/Minecraft caches were unchanged.

The default-feature binary SHA-256 is
`dea04b1d90bbaa012338e5755b320f3e2b48df3b9e12a3fee3a580d4a858b839`.
Logs, the Clippy comparison, source hashes and formatter-recovery proofs are under
Git-ignored `.local-review/raw-properties-map-2026-11-09/`; superseded evidence is
retained in its `before-constructor-fix/` folder. The local review command in 2.1b
uses this rebuilt binary. The installed PATH CLI remains unchanged pending review
and resolution of the full gate in 4.1; this is not full release qualification.

The focused command, from the CLI crate with the existing isolated build profile:

```pwsh
cargo test --locked --offline --all-features --lib -- `
  gradle_dot_properties_file_content gradle_properties_document `
  gradle_properties_key gradle_properties_value gradle_property `
  raw_gradle_properties_document maven_version_range `
  source_projection::catalog_release source_projection::release_version `
  source_projection::frozen_release source_projection::frozen_authoring `
  cli::source::source_cli::tests::release --test-threads=1
```

**Completion:** the raw representation is a unique-key map with parsed key/value
owners, typed construction uses that map, editing preserves its existing byte and
duplicate policies, and current validation evidence records all remaining limits.
Changes remain uncommitted for review.

### [x] 2.1d Keep the first properties pass a string dictionary

**Completion notes:** first-pass `.properties` parsing produces only
`BTreeMap<GradlePropertiesKey, GradlePropertiesValue>`. Both newtypes contain
strings. The raw line parser returns an optional key/value pair and handles only
file syntax and string escapes; it has no source-span fields or domain-range
parsing. A raw value such as `minecraft_version_range=not-a-range` remains a string
at this stage.

Facet then converts the dictionary to `GradleDotPropertiesFileContent`, retaining
field renames for dotted keys. Its Minecraft, Neo and loader range fields use
`SemverVersionRange`; Facet calls that type's `FromStr` implementation. The renamed
`SemverVersionRange`/`SemverVersionBound` preserve the project's bracketed interval
syntax, including partial and four-component version tokens. They do not impose a
new strict three-component SemVer requirement or add version ordering.

`GradlePropertiesVersionEdit { source: String }` owns the separate byte-preserving
version edit. It computes temporary source offsets only when applying the edit.
Remove `GradleProperty`, `GradlePropertiesDocument` and the persistent map of span
lists. The old `Range<usize>` fields described byte offsets, not version intervals;
U17 removes that bookkeeping from the first-pass path. Unrelated duplicate lines
remain editable; raw dictionary construction still rejects duplicate keys, and a
version edit still requires one `mod_version`.

**Work:** implement these boundaries and names without changing dependency
versions, authored properties or publication behavior. Preserve the existing
staged contents and leave changes uncommitted. Keep edits atomic on failure.

**Validation:** source at `d16af8c38` plus the uncommitted 2.1d edits.

- The required gate ran before test compilation. Its 74 Clippy diagnostics match
  the prior baseline by message and source path, with no added diagnostics. All 41
  formatter-only changes were proven and restored; all 933 pre-gate Rust sources
  retain their recorded bytes. The full gate remains red.
- One focused all-feature library test command passed 63 tests, including all 31
  authored property files. `domain_values_stay_strings_until_facet_conversion`
  proves raw range/boolean/number values remain strings even when invalid for the
  stronger fields; only Facet conversion rejects them. Existing range, dotted-key,
  alias, duplicate, escaping and byte-preserving edit tests pass.
- All 27 parser/range tests have exactly one configured runner owner. The obsolete
  span-helper test was replaced by the boundary regression. The new module names
  remove the previous substring collision and its special skip. The full runner
  still has its 11 baseline omitted/overlapping tests.
- The default-feature CLI was rebuilt once after the final source edit. Version,
  root help and nested plan help pass. One selected metadata plan,
  `sfm-4.35.0/mc-1.21.0`, passes with upstream `1.21`, no source writes or network
  requests, and publication unauthorized. No all-projection CLI rerun was needed.
- HEAD and all staged entries remain unchanged. Dependency declarations/lockfile
  retain their initial hashes; authored properties were not edited. Changes remain
  uncommitted and the installed PATH CLI was not replaced pending review/full gate.

The default-feature binary SHA-256 is
`ba5b561543b1e64b2d3f560889521389a4805c381ca8e6b011bceceb837f1c8a`.
Current logs, source hashes, gate comparison and recovery proofs are in
Git-ignored `.local-review/properties-first-pass-2026-11-09/`. The local review
launcher from 2.1b points to this fresh binary. The prior 2.1c binary and test
results are historical; this evidence qualifies the current boundary refinement.

From the CLI crate, with the existing isolated build profile:

```pwsh
cargo test --locked --offline --all-features --lib -- `
  gradle_dot_properties_file_content gradle_properties_version_edit `
  gradle_properties_key gradle_properties_value raw_gradle_properties_document `
  semver_version_range source_projection::catalog_release `
  source_projection::release_version source_projection::frozen_release `
  source_projection::frozen_authoring `
  cli::source::source_cli::tests::release --test-threads=1
```

**Completion:** raw parsing is dictionary parsing only; typed range construction
belongs to Facet/`SemverVersionRange`; source-edit offsets remain confined to the
explicit edit operation; current evidence records the result and remaining limits.

### [x] 2.1e Keep composite mapping values concrete with a field proxy

**Completion notes:** represent the dated mapping value as a tuple of a date and a
Minecraft version. Keep its string codec in `MinecraftDateVersionPairProxy`,
without introducing a domain-wide named date/version-pair wrapper. The user
confirmed that all authored mapping forms should remain as typed variants.

**Verified foundation:** pinned Facet supports `#[facet(opaque, proxy = ...)]` on
an optional field. Opaque field metadata avoids requiring Facet integration on the
core enum or a new chrono dependency feature. The properties adapter must enter
the field proxy through `begin_custom_deserialization` before attempting ordinary
optional/scalar parsing. Raw dictionary parsing remains string-only.

**Resolved representation:** the model field is
`Option<MinecraftMappingVersion>`, annotated with
`#[facet(default, opaque, proxy = MinecraftMappingVersionProxy)]`. Its plain Rust
enum has these forms:

- `Official(MinecraftVersion)` for `1.20.1`.
- `Parchment((NaiveDate, MinecraftVersion))` for `2022.11.27-1.19.2`.
- `CrossVersionParchment { mappings: (NaiveDate, MinecraftVersion), target: MinecraftVersion }`
  for `1.19.3-2023.03.12-1.19.4`.

For the cross-version example, the mapping tuple contains date `2023.03.12` and
source version `1.19.3`; target version `1.19.4` remains separate. Existing
`jar_build/engine_plan.rs::parchment_coordinate` selects source mappings for the
artifact coordinate, confirming this distinction. Do not reverse the versions or
discard either one.

`MinecraftMappingVersionProxy` owns the three wire forms and delegates date-pair
calendar parsing/formatting to `MinecraftDateVersionPairProxy`. The existing
version-first two-part alias, such as `1.19.2-2022.11.27`, is accepted as a
Parchment pair and formats date-first. The date/version pair remains a concrete
tuple in both Parchment variants. No pervasive pair domain type, Facet derive on
the core enum, new dependency or new dependency feature is required. Reusing the
existing numeric `MinecraftVersion` type remains an implementation choice; this
task does not globally replace it with a different SemVer library.

**Work:** finish the pair codec, broader mapping proxy, generic field-proxy
conversion and actual model wiring. Cover the three authored forms and legacy
alias without altering authored properties or legacy engine behavior. Preserve
original mappings, dependency versions, staging and the uncommitted review boundary.

**Validation:** source at `d16af8c38` plus these uncommitted edits.

- The required gate ran before test compilation. The final 74 Clippy diagnostics
  match the previous baseline by message and source path, with none added. All 41
  formatter-only changes were proven and restored; all 936 pre-gate Rust sources
  retain their recorded bytes. The full gate remains red.
- One focused all-feature library test command passed 73 tests, including all 31
  authored property files. Proxy tests cover calendar validity, malformed versions,
  supported-year formatting, tuple and variant roundtrips, official and cross-version
  data, raw-string preservation, optional/default/null handling and JSON conversion.
  The model test distinguishes source `1.19.3` from target `1.19.4` explicitly.
- All 37 parser/range/proxy tests have exactly one runner owner. The full runner
  still has its 11 baseline omitted/overlapping tests. Cargo's compiler-artifact
  output identified the current unit-test executable; no stale binary path was assumed.
- The operational CLI was rebuilt once after the final source edit. Version, root
  help and nested plan help pass. One selected `sfm-4.35.0/mc-1.19.2` metadata plan
  passes with no source writes or network requests and publication unauthorized.
  The all-projection CLI matrix was not repeated for this parser/proxy refinement.
- HEAD and all staged entries match the start of 2.1e. Cargo.toml and Cargo.lock
  retain their initial hashes: the opaque proxy avoided enabling Facet's chrono
  feature or adding a dependency edge. Authored properties were not changed.

The first gate stopped on a missing generated tree-sitter file in the existing
isolated Rust target. A narrow Cargo package-output rebuild repaired that derived
input; no source, dependency or native/Minecraft cache repair was needed. The next
cheap gate caught a discarded alternate-format error; both parse errors are now
retained. Neither failed gate advanced to expensive test/CLI builds. Tests and
source review were complete before compilation; no tests were added mid-build.

The fresh default-feature binary SHA-256 is
`77b82c6fc1d3eff5e89ad881c852b72f22187f9a88bb2a10c4bcdcc9e6dc5d6c`.
Current logs, source/test-binary hashes and gate/recovery proofs are under
Git-ignored `.local-review/mapping-pair-proxy-2026-11-12/`. Earlier 2.1d hashes are
historical. The local launcher originally targeted this binary; 2.1f supersedes its identity.
The installed PATH CLI
remains unchanged pending review and the full-gate qualification in 4.1. Changes
remain uncommitted; this does not qualify artifacts or publication.

From the CLI crate with the existing isolated build profile:

```pwsh
cargo test --locked --offline --all-features --lib -- `
  gradle_dot_properties_file_content gradle_properties_version_edit `
  gradle_properties_key gradle_properties_value raw_gradle_properties_document `
  semver_version_range minecraft_date_version_pair_proxy `
  minecraft_mapping_version_proxy source_projection::catalog_release `
  source_projection::release_version source_projection::frozen_release `
  source_projection::frozen_authoring `
  cli::source::source_cli::tests::release --test-threads=1
```

**Completion:** the model contains all mapping forms as concrete typed variants;
string codecs stay in proxies, dated pairs stay tuples, source/target identities
remain distinct and current evidence qualifies the resulting adapter/model changes.

### [x] 2.1f Replace mod metadata strings with domain values

**Implementation:** all eight fields now carry domain values. Constructors own
invariants, scalar Facet String proxies reenter those constructors, and authors use
a typed list with a CSV proxy. The raw dictionary still contains only string keys
and values. Each new public type links its source contracts in rustdoc.

Seven zero-sized SFM identity types convert into their generic counterparts and
reject other valid identities through `TryFrom`. `SfmModVersion<4, 35, 0>` proves
an exact unqualified release; projection versions remain dynamic until requested.
`SfmGradleDotPropertiesFileContent` owns a private generic snapshot and seven public
identity witnesses. Helpers borrow the generic view immutably; obtaining editable
ownership consumes the SFM proof. Catalog release preparation now requires this
checked SFM document before its existing release-version admission.

**Resolved policies:** [metadata type contracts](mod-metadata-types.md) distinguishes
loader rules from SFM choices. `ModId` follows the older portable Forge rule without
hardcoding `sfm`. General versions use strict SemVer; authors/issues use public
GitHub identities. Full Java identifier/package types accept Unicode, while the
SFM group contract uses lower ASCII segments. Software licenses recognize MPL-2.0
and its authored human-name aliases, or retain another nonempty declaration; this
is not a full SPDX-expression parser. Provider payload limits remain provider work.

**Dependencies and cache effects:** direct pinned `regex` and `semver` edges reuse
already locked versions. Compared with this task's saved lockfile, every package,
version, source and checksum is unchanged; only the CLI's two dependency edges
changed. Existing Forge/MCP/remap whole-lock keys will therefore change on future
preparation. No Minecraft build or cache preparation was run for this slice.

**Validation:** source review and scoped formatting completed before compilation.
The required gate completed with the same 74 recorded Clippy diagnostics by
message and source path. Its 41 unrelated formatter changes were proven and
restored to exact prior bytes. An initial run found three new lint issues; those
were fixed before any test build. The full gate remains red.

| Check | Result |
| --- | --- |
| All-feature focused library tests | 124 passed, zero failed/ignored; 2,473 filtered; 20.64 seconds. Includes constructor/codec boundaries, Unicode/escaping, all 32 authored/flat files, concrete witnesses and catalog/release callers. |
| Current-binary runner ownership | All 88 parser/domain tests have exactly one owner. The full runner still has its recorded 11 omitted/overlapping tests. |
| Default-feature operational CLI | Built once after final source edits. Help/version and nested plan help passed. |
| Selected projection | `sfm-4.35.0/mc-1.19.2`, expected version `4.35.0`: metadata verified, source writes false, network requests zero, publication authorization false. |
| Source and review state | All 964 frozen Rust/build inputs, HEAD and the initial staged listing match their saved identities. Changes remain uncommitted and prior staging is preserved. |

The current default binary's SHA-256 is
`92d2003d77453a44b7ddf80ac8167594f30ea461085a13756649037c1e34cd81`.
Commands, manifests, logs and formatter-restoration proofs are in ignored
`.local-review/mod-domain-types-2026-11-12/`. The local launcher shown in 2.1a
now targets this binary. Earlier 2.1e binary evidence is historical. The installed
PATH CLI remains unchanged while full repository qualification is red.

The direct dependency provenance comparison confirms unchanged package identities,
sources, versions and checksums; only the two declared CLI edges changed. No
provider, publication, Gradle, Minecraft or native-cache preparation was run.
Resume at 2.2 after the user's review; this completion does not authorize it.

**Completion:** all eight fields carry source-linked domain values, SFM witnesses
admit only their concrete identities, generic helpers remain usable, and current
constructor/codec/corpus/release evidence qualifies the final source.

### [x] 2.1g Reject production unwrap and expect

**Implementation:** all four first-party Rust packages declare `expect_used` and
`unwrap_used` as `deny`: the propagation CLI, its Styx compatibility crate, the
live-control CLI and translation helper. The shared `platform/cli/clippy.toml`
and propagation CLI's local config allow test assertions and explicitly check
constants. Production source has no panic-lint exceptions. The lexical audit finds
no production assertions; three fallible query-parser methods named `expect` are
separate from the standard-library panic methods.

Fixed SFM metadata conversions now follow the checked `TryFrom` construction
policy in 2.1i. That correction supersedes the earlier direct-storage and typed
constructor bypass used while removing panic assertions. The first properties
pass stays a string dictionary. Inventory lookups, source-provider paths, report
serialization, lock retries, cached regex/cache selection and producer helpers
now return failures through their callers.

**Test boundaries:** Clippy does not infer every helper's test role from a Cargo
test target, `include!` invocation or `cfg(all(test, windows))`. Six integration-test
crate roots now have `#![cfg(test)]`; 13 included helpers have explicit test
markers; two Windows test modules use separate test and platform conditions. The
conditions are equivalent, assertions are preserved, and no production allowance
was added. Isolated compiler/Clippy probes and a production-macro audit record the
rule's scope and tool limitations.

| Check | Result and scope |
| --- | --- |
| Focused Clippy policy | Passed all targets and declared features in all four packages. Other warnings were excluded only in these focused commands; source lint levels remain enabled. Styx has no features and uses parent `-p sfm-styx-state` selection with the existing lockfile. |
| Propagation library | 2,576 unique tests passed; 30 ignored. The full run passed 2,575 and found one stale source-mutation needle after a private binding rename. Only that test string changed; all 12 tests in the affected Forge input-identity group then passed. Other full-run results remain applicable. |
| Additional tests | Styx compatibility: 4 passed. Live-control CLI: 71 passed. No failed tests remain in this slice. |
| Current runner ownership | All 88 parser/domain tests have exactly one configured owner. The full runner retains its existing 11 omitted/overlapping tests; it is not qualified. |
| Fresh operational CLIs | Default builds passed. Propagation help/version/nested plan help and both CLIs' error handling passed. A synthetic invalid startup argument remained redacted. |
| Read-only projection | `sfm-4.35.0/mc-1.19.2`, expected `4.35.0`: metadata verified, source writes false, network requests zero, publication authorization false. |
| Required repository gate | Still red: 1,985 existing import-policy diagnostics plus 69 previously recorded diagnostics. No new non-import diagnostics remain; five older diagnostics were resolved. Every remaining import path was checked against task-start source; none increased. |
| Preservation | All 1,003 frozen Rust/build/config inputs match. Lockfiles are byte-identical to task start, and HEAD is preserved. Current staging was observed and left alone; this task performed no staging or commits. |

**Baseline correction:** `absolute_paths = "warn"` already existed in the task-start
Cargo manifest but not the November 12 baseline. The old 74-error count therefore
no longer describes this checkout. The current comparison records messages and
source paths for the other diagnostics, and production-token provenance for all
1,985 import diagnostics. The full gate's 31 formatter changes were proven and
restored to exact prior bytes. Later adapter/test-data fixes changed only test code;
the all-target checks and targeted rerun qualify those changes.

The staged index changed concurrently during implementation. Initial,
pre-validation and post-check listings remain in the ignored review directory;
none was used as a Git index or restored over the current index. The translation
helper needed a locked Windows dependency fetch before its offline check. No
package versions or lockfile contents changed, and no standalone Styx lockfile was
created. Source-hashed producer regions can receive new identities after these
Rust changes; no native preparation, cache cleanup or Minecraft/Gradle workload
was run.

Current default propagation CLI SHA-256:
`b2a0e9624c0825b378e36b855cfeb6855c3a801a660645d1febf6cd00bbae921`.
Current default live-control CLI SHA-256:
`d57e6777948bb85113e07156755ee45b18d8c3c44a83d97ca76b76bedf8d73b9`.
The installed PATH tools remain unchanged while full qualification is red. The
existing local launcher targets the current propagation binary. Checks, snapshots,
source manifests and diagnostic/provenance reports are under ignored
`.local-review/no-unwrap-2026-11-15/`. Review the source before proceeding to 2.2.
Nothing was published, pushed, tagged or installed.

**Completion:** ordinary production `Option`/`Result` assertions are rejected by
Clippy, constructors and helpers expose failures without those assertions, test
assertions retain explicit test boundaries, and current compilation/runtime
evidence qualifies this patch. Full release qualification remains phase 4.1 work.

### [x] 2.2 Guard legacy release entry points

**Work:** reject branch/global-JAR publication, provider check/validate, collection
and tagging when either invocation checkout or configured legacy input root is a catalog checkout. Reject
before secrets, notes-file writes, popularity-cache reads or transport. Global roots
must not override projection intent. Specify deliberate legacy-checkout behavior.
**Validation:** catalog CWD with unrelated configured root; catalog configured root
with outside CWD; rejection without configuration effects; retained legacy fixtures.
Old `--dry-run` is not a universal no-network proof.
**Evidence:** all nine legacy-admission tests passed in the sixth library run.
The final required gate also passes with the retired tree absent. No legacy
transport was invoked.
**Completion:** catalog use cannot silently dispatch to a legacy publisher; help
points to the explicit catalog preparation route.

## Phase 3: migrate consumers before retiring flat inputs

### [x] 3.1 Prepare provider adapters and release helpers

**Implementation contract (2026-11-16):** `source release prepare` accepts the
existing exact release selection, one projection-relative native JAR and an
explicit offline CurseForge game-version catalog. Create and amend are distinct;
amend requires both existing provider object IDs. Native successful packaging
publishes a local provenance receipt bound to current ownership, lock/recipe,
producer identity and exact JAR bytes. Preparation rechecks those bindings and
emits provider payloads, an unperformed collection plan and an uncreated tag plan.
On Windows, the create-only receipt writer denies all sharing until write, sync
and close, preserving the retained ancestry leases. Failure may leave partial
or complete bytes; independent verification determines admission. Unix retains
create-only atomic rename. Existing records are preserved.
Remote state is unverified and publication remains unauthorized. Git status and existing tag objects are reported, never silently worked around.
The proposed tag record is not a source-to-commit or publication permit.

Fixtures must exercise all ten supported target/upstream pairs, transitional
1.20.1 dual-provider loader policy, actual CLI dispatch, missing/drifted inputs,
explicit amend IDs, ambiguous/missing offline provider mappings and no source or
provider effects. Synthetic native fixtures prove contracts; they are not real
Minecraft build or release-qualification evidence.

**Work:** consume one reviewed candidate's metadata and verified assets through pure
GitHub/CurseForge/Modrinth payload adapters. Preserve loader/version policy, artifact
ownership/hash, changelog identity and create/update distinctions. Replace branch-era
collection/tag planning. Actual provider writes and tag creation remain out of scope.
**Validation:** pure metadata/payload fixtures, artifact drift refusal, no flat-file
fallback, no-network integration through the preparation CLI.
**Completion:** projection-specific, inspectable handoffs exist without publication.
**Evidence:** 52 related tests pass, with one isolated logging worker intentionally
ignored by the parent harness. They include all ten target/upstream contexts,
create/amend payloads, current ownership and recipe checks, retained-file drift,
Windows pending/partial/complete receipt states and no operation effects. The
full gate passes as recorded in 4.1; SDK/JVM release qualification is not implied.

### [x] 3.2 Migrate remaining active flat-tree consumers

**Retirement checkpoint (2026-11-17):** removed exactly 2,797 tracked flat source
files and 167 root Gradle files. Every file matched its initial hash and saved
original immediately before removal; no dirty, untracked, ignored or reparse
members were admitted. Only empty directories were removed afterward. HEAD,
raw index bytes and staged entries remained unchanged. Exact originals and the
per-file journal remain in the ignored goal archive. Core templates, projections,
root properties/toolchain witnesses and authored native build inputs were retained.

The replacement library run passed 2,623 tests with 30 ignored; all 52 Java
scenarios pass. The final required gate passes with the retired tree absent. The installed CLI
also verifies current source ownership and release metadata for all ten candidates.

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
- Java analysis/server and ordinary source audit now select checked generated
  project roots. Explicit branch analysis rejects a selected catalog checkout;
  unsupported mutation commands still perform no source access. The full library
  suite and all 52 Java scenarios pass. Historical version-surface audits read
  Git blobs and retain old paths.
- Puppet preview provenance now uses checked ownership for release and both
  development identities. Only historical Worktree plans fingerprint flat sources.
- The VS Code examples browser and executable examples/grammar links use rendered
  release inputs. Native editor tasks and the GameTest wrapper require a projection.
- `source_projection/{development_baseline,candidate_lock,release_apply,
  release_baseline}.rs` and `cli/source/source_cli.rs` retain explicit historical
  formats, pinned Git witnesses and catalog authoring guards. Propagation and
  generated-resource Git status patterns apply to historical version worktrees;
  their strings are not current-generation source reads. Preserve these contracts
  rather than rewriting historical Git paths to current template paths.

Reuse `CoreCatalog::load/context`, checked catalog ownership and pure provider
payload types. Historical `source legacy release-*` preflights target retired
mc-version ownership; they cannot simply be renamed into a catalog publisher.

## Phase 4: qualify tooling and the selected candidate

### [x] 4.1 Validate and install after final CLI implementation

**Current qualification checkpoint (2026-11-17):** strict production Clippy and
the all-feature build pass. The library suite passes 2,623 tests, with 30 ignored;
all 52 Java scenarios pass. The affected contract run passes 52 tests, with one
isolated logging worker ignored by its parent harness. The final required gate
passes after physical retirement: 2,623 library and 114 integration tests pass,
with 30 library tests ignored. Bin/doc checks and the default-feature build pass.
All four Rust packages pass the production panic-method policy for every Cargo
target and declared feature. No production panic-method calls or overrides remain.

The repaired test failures preserved production guards. Loader metadata now uses
the fixture's source rules. Partial-index recovery checks the selected cache and
branch, preserves the `minecraft-toolchain` namespace, and requires recovery and
index refresh commands to match. The changed index digest was reconstructed from
the three historical `source-owner` inputs; removing only those inputs reproduces
the old digest. Fresh invocations produce identical current output.

Windows receipt creation retains all JAR and ancestry leases. Exclusive creation
blocks readers until the writer closes; interrupted partial and complete records
remain subject to independent verification. Three regressions exercise these
states. Synthetic artifact fixtures establish tooling contracts, with native
Minecraft packaging and gameplay qualification still pending.

The initial lint debt was repaired without suppressions. All six formatter-only
changes in unrelated files were restored byte for byte.
The frozen dependency graph and initial staged entries remain intact.

**Work:** repair the existing strict-Clippy failures and bounded-runner partition
ownership before claiming a passing full gate. Resolve the two Java-analysis
integration failures with controlled cache/SDK inputs. Freeze dependency versions/recipes.
Cleanup retains eight existing pin
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

### [x] 4.2 Record candidate-specific release evidence

**Work:** choose the intended 4.35 projection family and ten-target scope, then bind
source/catalog/features/lock/SDK/CLI/artifact/test-selector identities. Separate
historical expected failures from green results and graphical from headless checks.
**Validation:** explicit per-target required-check ledger with applicable receipts;
no substitution of stale binaries or older plan prose.
**Completion:** readiness and missing evidence are reviewable; publication is not implied.
**Evidence:** the [candidate ledger](release-readiness.md) records 10/10 metadata
and ownership observations from the fresh installed binary, with source/report
digests. Native JAR/SDK, selected mod-test and gameplay evidence remains pending.
This completes the observation task without certifying a 4.35.0 release.

## Operational readiness and limits

The 17 November 2026 checkpoint qualifies the uncommitted working copy based on
HEAD `d16af8c38c85199bb41017b29ee3962d85620d1e`. The source-input manifest SHA-256
is `5c4f39d754aa4ea52487fa578f36192721cf0197abb867452569529f3d160f6b`. HEAD and staged entries remain at their
initial values. No changes were staged or committed, and no release tags,
provider writes, uploads or pushes were performed.

Both repository installers ran with locked, offline dependencies and the existing
shared target. Normal PATH resolves the resulting default-feature release
products; their hashes match the corresponding freshly built release files.

| Tool | Installed binary SHA-256 |
|---|---|
| `sfm-propagate-changes` | `c2fce89fae1d94cb37be39e5d35480b4939a29346fe9ca2fa1ead862f1b002c7` |
| `sfm` | `277d93b56f14b8096105ffafc3c82b9dc827e3323dcbc5e1a34530a0899996ca` |

The exact resolved paths, installer commands and nine help/error/version checks
are in the ignored goal archive's `install-summary.json` and
`installed-cli-checks/summary.json`. **User install required: no.**

From this repository root, the immediate manual check is:

```powershell
sfm-propagate-changes --output-format json source release plan --repo-root . --projection sfm-4.35.0/mc-1.19.2 --expected-mod-version 4.35.0
```

Expect current release metadata and ownership, no source writes or network
requests, and no publication authority. All ten plain release projections pass
that check. The [candidate ledger](release-readiness.md) keeps native artifact,
SDK, mod-test and gameplay evidence pending. No Minecraft client/server or Gradle
workload was launched during this goal. No game or helper process was restarted
or deliberately left running; CLI/test processes exited normally.

The extension's source URL, editor JSON and wrapper dispatch were checked. The
VS Code extension was not packaged or installed: Node/npm and installed frontend
dependencies were unavailable. Its source change is separate from the installed
Rust CLI handoff.

The 2,964 retired files have exact saved originals and a per-file journal. The
non-CLI audit accounts for all 30,183 starting inputs: 27,208 unchanged, the 2,964
intended absences and 11 intended changes. Core templates, rendered projections,
root metadata and retained dependency inputs are unchanged. All nine dependency
declarations/lockfiles, including three Cargo locks, match the goal's starting
snapshot. Source validation used the existing target without cache cleanup,
offloading or new worktrees. No disk-space error occurred.

Private evidence is under `.local-review/finish-projection-migration-2026-11-16/`.
It preserves source snapshots, gate logs, the exact formatting restoration, file
retirement/preservation proofs, installer/path/hash checks and candidate reports.
Automatic logs remain enabled under the selected application home. The source
changes and this plan remain uncommitted for review.

### [x] 2.1h Pass invocation homes explicitly

**Scope:** resolve `AppHome` and the default `CacheHome` once after CLI parsing,
then pass the selected values into command routing. Logging uses that same
application home. Worker entrypoints retain their existing explicit input paths.
No global home, lazy home accessor, working-directory fallback or fixture-based
home resolver remains.

Routing carries an `InvocationHomes` snapshot; operations accept the specific
home or concrete cache path they need. Cache-selection failure is stored at the
boundary and returned only when an operation requests that default, so settings
commands and `cache root set/unset` remain usable for recovery. Native builds
retain their selected checkout cache and its ownership guards. Canonical frozen
witnesses and JUnit authority inputs retain their separate startup-selected
shared cache. Cache origin travels with its selected path; deleting or changing
a saved setting cannot authorize cleaning a custom cache selected earlier.

**Acceptance:** complete caller migration; verify independent injected homes,
normal/deferred logging, scenario fixture isolation, cache-root repair and native
cache precedence. Run required checks before focused tests, compare existing
lint debt, and retain the user's index. Leave all changes uncommitted. No
publication or Gradle/game execution is authorized.

**Implementation state:** source-complete and reviewed. `InvocationHomes` owns
startup selections without discovery; `SelectedCacheHome` retains path and
origin together. `CacheHomeRequired` carries a missing shared-cache input through
worker reports so command routing can restore the original startup diagnosis.
No production `unwrap`/`expect` calls or panic-lint suppressions were introduced.

| Startup cache selection | Operation behavior |
| --- | --- |
| Platform default | Use its captured path; cache cleaning retains default-only authority. |
| Environment/persisted/custom caller path | Use its captured path; refuse whole-cache cleaning even if settings later change. |
| Selection failure | Keep settings and set/unset recovery available; shared-cache operations return the captured cause when they need it. |
| Guarded native root | Retain its existing ownership guards and output layout; canonical witness/JUnit inputs remain separate shared-cache reads. |

**Historical validation checkpoint (15 November):** the implementation was
complete and uncommitted. The table and open findings below describe that earlier
run; phase 4.1 supersedes its red gate, partial-index mismatch, directory-membership
flakiness and installation status.

| Check | Result |
| --- | --- |
| All declared features and targets | Type/policy checks pass for all four Rust packages. |
| Library tests | Full run: 2,582 passed, 1 failed, 30 ignored. The unchanged directory-metadata test passed on isolated retry; all seven added tests passed. |
| Java scenarios | Full run: 49 passed, 2 failed. The deterministic JDK-fixture correction passed its focused rerun; 50 unique cases now pass, with the existing partial-index fixture mismatch open. |
| Default CLI products | Fresh default propagation/control builds, help/error/redaction checks and projection metadata plan pass. No installation. |
| Invocation-home behavior | Six CLI probes pass: normal app-home logs, empty invocation directory, invalid-cache settings/metadata commands and set/unset recovery. |
| Required repository gate | Still blocked by 2,047 existing lint diagnostics; no new compiler errors or newly triggered lints. |
| Source/index preservation | 1,007 frozen source/config inputs and all three lockfiles verified; HEAD and the user's index unchanged. No staging, commits or publication. |

All 1,978 qualified-path diagnostic spans matched exact compiled snapshots and
task-start bytes; none grew. Two existing long-function warnings changed only
their line counts. The gate's 22 unrelated formatter changes were proven and
restored byte for byte. Post-gate fixture edits leave normal production code
identical; the additional changed file is an explicitly declared integration-test
target. Final production audit finds no standard-library `unwrap`/`expect` calls
or panic-lint suppressions.

Private evidence is under `.local-review/explicit-homes-2026-11-15/`, including
`validation-summary.json`, the gate comparison, unchanged-production proof and
CLI probe results. The ignored directory is not a release artifact.

**Historical open findings outside the home-selection refactor, resolved in 4.1:**

- `scenario_cases::usage_at_position_partial_index` publishes its fixture index
  under a temporary application cache, but the query selects the guarded native
  branch cache. The saved task-start implementation already made this selection.
  The scenario needs an explicit source-cache input at its query seam. Keep its
  assertions and production cache guards; do not restore a thread-local cache
  lookup to make the test pass.
- `jdk_artifact_cache::tests::held_verified_install_blocks_selected_writes_and_audit_finds_new_member`
  failed once because a new child did not immediately change observed directory
  metadata. The unchanged test passed in an isolated retry using the same binary.
  Review directory-member detection separately; the passing retry does not make
  the full library run a clean pass.

The semantic-matrix scenario now builds a private temporary JDK corpus, including
its required serialization declarations. It no longer depends on host SDK
contents. Its expected output and the shared scenario corpus remain unchanged.


### [x] 2.1i Validate every SFM witness conversion

**User requirement:** fixed SFM values must enter generic domain types through
checked `TryFrom` conversions. Removing panic assertions does not justify
bypassing a domain parser.

ID, name and description conversions now live with their SFM witnesses and call
the generic parser. License parses its authored `VALUE`; the exact version parses
its formatted numeric components. Authors, group and issue tracker already use
checked construction. All eight round-trip tests require `eyre::Result<Generic>`
explicitly, which rejects a return to the blanket infallible conversion API.
Generic metadata types no longer import their SFM-specific scalar witnesses.

The consuming SFM-document conversion still returns its privately stored,
already validated generic document; it constructs no new unchecked value.
Only five test callers needed `.try_into()?` handling. No production callers,
CLI behavior, dependencies, publication paths or cache choices changed.

**Validation:** all 18 witness tests and 12 related parser/document tests pass.
The required gate reports the same 2,047 existing diagnostics as the preceding
home-selection snapshot, with no added diagnostics or qualified-path occurrences.
Its 22 unrelated formatter changes were proven and restored byte for byte. Static
review found no remaining unchecked fixed-witness conversions or production
caller changes; the production panic-call audit remains empty. All 1,007 frozen
source/config inputs, three lockfiles, HEAD and the user's index are preserved.
No staging, commits, installation or publication. Changes remain uncommitted.
Private evidence: `.local-review/checked-sfm-conversions-2026-11-15/validation-summary.json`.
