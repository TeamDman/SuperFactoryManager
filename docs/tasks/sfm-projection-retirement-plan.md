# Retire the snapshot and overlay migration layout

Plan status: Complete. Last updated: 7 October 2026.

The user authorized cleanup after identifying obsolete directories beside the
completed Liquid core. The current branch is `main`. The committed checkpoint
`556e4f0994b88873189eff18e4eef4ebcd629279` preserves the old layout for recovery.

## Scope and intent

| ID | Requirement | Acceptance |
| --- | --- | --- |
| R1 | Remove development-baselines, development-overlays, release-baselines, mc-version and projection-resources. | No duplicate tracked trees remain at those roots. |
| R2 | Retire callers and configuration of the superseded workflow. | Current CLI uses the catalog; historical commands are explicit legacy operations. Tests use current roots or historical witnesses. |
| R3 | Preserve the completed migration. | All twenty oracle comparisons and generated output checks pass after cleanup. |
| R4 | Preserve recoverability and contributor changes. | Inspect exact deletion targets; preserve unrelated files and history. |
| R5 | Keep tooling usable. | Required Rust checks, final install and command smoke pass. |

Intent audit: extraction covers the five named directories and accepted cleanup
proposal. Traceability maps them to R1–R5. Adversarial review keeps source cleanup
separate from deleting release evidence, modifying templates or changing feature
sets. Earlier implementation details use the completed migration plan and receipts.

## Work

### [x] 1 Remove obsolete ownership and duplicate trees

Removed 22,153 tracked files from the five named roots. Moved the old manifest
into the CLI's test fixtures. No untracked contributor files existed under the
five roots. The remaining ignored `mc-version` contents were build caches,
Gradle state and GameTest runtime files; preserved them outside the checkout in
the SFM repository parent's `retired-generated-projects/mc-version-20261007`.
No game/build process was using that directory. No data was discarded from those
ignored files. Tracked content remains recoverable from the prior commit.

Retained historical command surfaces now live under `source legacy`. Snapshot
authoring is rejected in repositories with the current projection catalog.
Small test-only metadata lives in fixtures; legacy tests extract bounded paths
from the pinned checkpoint above. Production generation never uses that fixture
mechanism. Release parity checks inspect the named projections and original tag
blobs, applying the existing CRLF/final-newline comparison allowances and datagen
cache exclusion. Historical membership and Git blob identities remain exact.
The flat `src`, root Gradle project and ignored IDE/staging directories are outside
this bounded cleanup unless a direct dependency requires a specific adjustment.

### [x] 2 Validate and install

Run focused affected tests, the required `check-all.ps1`, all twenty full oracle
comparisons and named output checks. Rebuild/install the operational CLI and
verify its path, hash and current command help. No JAR rebuild is required when
the unchanged generated build inputs match the accepted content manifests.

The existing installed CLI's full oracle check passes all twenty required
contexts: complete coverage, current references, matching snapshot and zero
unresolved paths. The two new namespace/authoring-boundary tests pass. Core,
catalog, checked-in projections and dependency declarations have no diff from
the accepted checkpoint. No native JAR rebuild is planned.

The final full-gate attempt passed all regular library groups and all 70
integration tests. Its last dedicated historical promotion fixture correctly
hit the new catalog guard. That fixture now explicitly checks out the preserved
legacy checkpoint and removes the catalog in its isolated temporary clone.
Production behavior is unchanged by this test-only correction. The repaired
fixture passes, completing coverage of 2,428 passing library tests, 18 existing
ignores and 70 passing integration tests across the combined gate receipts.
Bin/doc checks and the final operational build pass. Formatting and strict
Clippy pass again after the fixture correction. Installation completed.
This is split validation, not a claim of a single successful `check-all.ps1`
invocation. The test profile used optimization level 1 with debug assertions.

The newly installed CLI passes all twenty `source project check` invocations
and a fresh full `source oracle status`: 20 required/compared contexts, complete
coverage, matching snapshot, current references, `done: true`, zero unresolved
paths. Current and legacy command help both pass. Staged and unstaged Git
whitespace checks pass. All five retired directories are absent.

### [x] 3 Update the handoff

Document current roots, historical command scope, removed file counts, validation
and any remaining local files. Do not push cleanup automatically. Stop on an
actual disk-space error and wait for the user; do not clean caches.

The contributor guide now identifies `main` as the published default branch,
documents the retired roots and distinguishes `source project` from explicit
legacy commands. No push or mod publication is part of this cleanup.

## Operational readiness

- Target: `main`, cleanup based on `556e4f099`.
- Tooling changed: CLI namespace separation and a catalog guard; no authored
  Minecraft inputs or checked-in generated output changed.
- Installation: `platform/cli/sfm-propagate-changes/install.ps1` completed after
  the final Rust edit. The normal PATH executable reports 0.1.1, base revision
  `556e4f099`, built 7 October 2026 at 15:48:17 -04:00 from the cleanup working tree.
- Installed SHA-256: `e1c224d40bb718a824a076a4f73eaec52155aefdb9e0d487f3af7081ce687112`.
- User install required: no. The installed executable passed the final checks.
- Dependency posture: frozen; dependency declarations and locks unchanged.
  No new repositories, dependency acquisition or arbitrary artifact substitution.
- Process state: validation, build, installer and projection-check processes
  completed. No game was launched or stopped. An attempted stop during an earlier
  test run found that the process had already exited; no process was terminated.
- No disk-space errors occurred. Ignored runtime/cache files were preserved
  outside the checkout; they were not discarded to recover space.
- Manual check from repository root:
  `sfm-propagate-changes source project check --repo-root . --projection sfm-4.34.0/mc-1.19.2`.
- Scope limit: no Gradle execution, fresh JAR rebuild, gameplay proof or release
  publication claimed. The flat source/root Gradle and ignored IDE/staging files
  remain outside this cleanup. No continuation item is claimed.
