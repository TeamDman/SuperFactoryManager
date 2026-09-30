# Ten-version source-projection migration acceptance

Status: original SP-01 through SP-09 migration accepted technically, 30
September 2026. Full regression, installed-tool checks and independent review
passed. This document does not approve publication or a branch change.

## Decision and boundary

Accept the measured `released-4.34.0` projection as the migration's technical
baseline under the original SP-01 through SP-09 requirements and decision D2.
The author accepted behaviour and compatibility parity with disclosed compiler
or packaging differences, rather than byte-identical JARs. Acceptance uses the
requested representative comparisons; it is not a promise that every method,
mod combination, packet or existing world has been exhaustively tested.

The single-branch workflow now covers 1.19.2, 1.19.4, 1.20, 1.20.1, 1.20.2,
1.20.3, 1.20.4, 1.21.0, 1.21.1 and 26.1.2. It remains on the isolated
`feat/sfm-main-source-projection` branch. Do not rename it to the default
branch, merge it, retire version branches, create public tags or publish JARs
under this engineering disposition.

This later disposition supersedes the narrower *exploratory-only* acceptance
in the [29 September record](sfm-main-release-parity-disposition-20260929.md).
That historical record and its limits remain intact. It does not approve the
RegexCache opt-in candidate, a fictional rehearsal or any future preset.

## Requirement-to-evidence map

| Requirement | Accepted evidence and remaining boundary |
| --- | --- |
| SP-01: source and feature selection | Typed Facet/Liquid projection, version-selected Java and feature effects; real enabled/disabled production JAR checks establish absent classes, registrations and resources. Runtime booleans are not substituted for compilation exclusion. |
| SP-02: ordinary contributor builds | All ten checked-in release projects built with their own wrapper and documented Java line in an isolated checkout without the Rust CLI. Nine Java compilations executed; 26.1.2 reused a cache result. This is the documented warm-cache proof, not a cold-download claim. |
| SP-03: deterministic, guarded projection | Repeat sync/check, pinned inputs, unknown or unsupported flags/directives, collisions, missing inputs and changed-output refusal are covered by passing negative and golden tests. No arbitrary template includes or contributor-overwrite route is enabled. |
| SP-04: provenance and reconciliation | Per-file source/overlay/output hashes, `source trace`, manual backpropagation and explicit byte-identical reconciliation preserve contributor edits. Arbitrary inverse templating is not claimed. |
| SP-05: released baseline | All ten verified production JAR pairs have entry/resource/API comparisons, registry and selected save round trips, one vanilla transfer and packaged-client startup/world rendering. All ten pass one packet-body codec plus a real config request/response. M6.22 directly checks all 9,980 tagged source members; M6.23 adds endpoint permissions and one seeded cache-clear effect. Differences are disposed of below. |
| SP-06: existing work retained | Eight reviewed 1.19.2 transfer commits, separate release and development presets, version-owned overlays and existing living plans preserve unreleased feature/toolchain/incident work. This migration does not complete those feature plans or open release gates for them. |
| SP-07: implemented tooling | Source catalog, generated-root builds, read-only Java declaration/member indexing and queries pass the matrix, including Java 17/21/25 and the reduced ANTLR stack-overflow regression. Pre-existing rename/move stubs remain unsupported; manual reconciliation is the supported mutation path. |
| SP-08: ten testable, independently releasable targets | Full development unit/focused GameTest and release production-build matrices pass. Guarded candidate/package/promotion predicates and exact-target preflights are tested. The reviewed standard-provider [publication handoff](sfm-main-projection-publication-handoff.md) supplies an independent release route without legacy branch scanning. No new version choice, real upload or retirement of the old merge route is required to accept this migration. |
| SP-09: separate development projections | External development outputs and simultaneous immutable preset identities leave all ten checked-in release projects unchanged. One primary edit propagated through every eligible target while version-owned/absent paths were preserved; all ten edited Java compilations executed. |

The detailed [living plan](sfm-main-source-projection-plan.md),
[production comparison matrix](sfm-main-release-candidate-matrix-20260928.md),
[fresh-wrapper matrix](sfm-main-fresh-wrapper-matrix-20260928.md) and
[contributor guide](../source-projection-contributor-guide.md) retain commands,
counts, hashes, cached/skipped work and the contributor workflow.

## Disposition of measured differences

1. Accept the one generated Java provenance banner. The new frozen local-Git
   audit resolves every release tag to its pinned commit, checks complete tagged
   source membership/modes/blob IDs and freshly reads all 9,980 blob bodies.
   Generated bytes match after removing only that banner; BOMs and line endings
   are preserved. This does not assert absence of every generated extra file;
   source-membership and JAR-absence checks cover their separate contracts.
2. Accept archive timestamps, ZIP framing/directory records and the generated
   implementation timestamp under D2. Retain each official/projected production
   JAR digest, rather than describing different archives as byte-identical.
3. Accept the disclosed compiler/debug/synthetic-helper differences for this
   measured 4.34.0 baseline. All compared non-synthetic declarations match and
   the selected production-runtime witnesses show no candidate-only difference.
   The controlled JBRSDK 17.0.6 experiment explains only the separate 82-class
   checked-in-versus-external 1.20.2 drift. It does not explain every difference
   from the official JAR or prove all annotations/method bodies equivalent.
4. Accept only the repaired refmap state: five targets now include the exact
   published refmap bytes. The 26.1.2 pair both omit it and share the warning.
   Broader mixin/mod combinations are not inferred from these results.
5. Accept sampled runtime/config evidence at its stated scope. All ten pairs
   have equal `sfm-server.toml`; six older pairs inherited the official seed
   config and four later pairs generated it independently. Endpoint command
   probes use real server-console permission replacements, not logged-in player
   identities. They compare fourteen node and ancestor-path decisions and each
   execute `sfm bust_cable_network_cache` against a cache measured at one entry,
   returning one and leaving zero entries. Other command effects, all settings,
   full framed wire traffic and all packet codecs remain unproved.

For the original migration baseline these limits are disclosed engineering
risks, not an instruction to keep expanding testing without a stopping rule.
Any future release must review its own changed sources, immutable inputs and
representative risks. This acceptance cannot be inherited by a new preset.

## Final regression and operational checkpoint

The required `check-all.ps1` completed dependency policy, nightly formatting,
strict all-features Clippy, build and all 48 bounded test shards. It listed
1,153 library tests: 1,143 passed and ten were intentionally ignored. All
68 integration tests passed; bin and doc phases passed with zero runnable
tests. The complete transcript has SHA-256
`94b441e599bf689240d6fda8e61adaa22aec1b913f79e3b74ee30fc08abee414`.
This replaces earlier partial or composed-suite checkpoints as the final
required regression, without converting ignored cases into passes.

The release-source module passed 10/10 focused cases and its full-suite shard.
The permission/effect validator passed 30/30 offline cases. Both exact-loader
official/projected endpoint pairs passed, saved and exited with code zero;
input JAR hashes remained unchanged. The
[command evidence](sfm-main-command-permission-parity-20260930.md) records the
actual identities, snapshot hashes and limitations. The test-only probes are
not added to production JARs or generated source projects.

The local code checkpoints are `31fc5136d` (direct release-tag audit) and
`dd7600160` (test-only command witness and independently checked evidence).
`install.ps1` completed offline after these commits. PATH resolves to the
installed `sfm-propagate-changes` 0.1.1, revision `dd7600160`, built at
2026-09-30 00:29:42 -04:00. Its SHA-256 is
`84170f57ea693e21e9e11aea082f6975fa326471350ed4f23583334c07d3c945`;
the companion `source-jar-absence` digest is
`81249cf6e566c523b201a34ea3106fdb6793fabeea703459b9e73221044fd2ad`.
The installation transcript digest is
`0f569a426f63b065945be01e6118db46299323862cf4fc1e8afb822e5dc90147`.

That installed tool passed `source release-modrinth --help` and read-only
`source check` on all ten `released-4.34.0` roots: 11,495 unchanged files,
zero creates/updates and no changed manifest. The matrix transcript digest is
`9d51cf2684f757fc513be0c7f7bab8fb4c41a6de60793b3950cd7cbff270877e`.
Later documentation-only commits do not alter the installed executable.

For a manual smoke from the trusted repository root, run:

```powershell
sfm-propagate-changes --version
sfm-propagate-changes source release-modrinth --help
sfm-propagate-changes source check --repo-root . --target 1.19.2 --preset released-4.34.0 --output-root platform/minecraft/mc-version/1.19.2
```

Expected: the revision above, read-only help, and 1,146 unchanged files with
zero writes and `manifest_changed=false`. No game is needed. An isolated
agent account may require its exact-root process-local Git ownership exception;
do not change global trust settings. See the contributor guide for a temporary
development build or ordinary Gradle-only build.

Independent review recommends this original SP-01 through SP-09 technical
acceptance and finds no waived core requirement. The corrected manual handoff
has thirteen PowerShell blocks with zero parse errors; it binds canonical
Modrinth IDs and exact metadata, rejects inherited Git overrides and pins the
public GitHub host. It remains unexecuted provider guidance, not upload proof.
The preservation audit found unchanged primary/version/import trees, dependency
manifests/locks, projection definitions and other tracked plans against
`819b3f56aaa071a07719ce38d93c8932bf2c278e`; the frozen fictional candidate
remains clean at `90b0adfa4d5ea744990bc37be0b9ba68efbaa33e`. All owned
test servers exited; no user game was stopped. No disk-space error occurred
in this continuation. No caches or user saves were removed.

## Work intentionally still open

- Choose and approve the next public version and feature set, then validate
  and promote its own real candidate. The `9.99.99-rehearsal` production-shaped
  package proves packaging/control contracts, not a real release's behaviour.
- Implement a projection-native publisher if desired. Current Rust provider
  preparation is read-only; the documented standard-provider/manual route is
  the supported effect handoff. Provider credentials, ownership, duplicates,
  exact numeric CurseForge IDs, tag/push and upload each need their stated
  review and authority. No provider write was tested or performed here.
- Decide whether to adopt `sfm-main` as the default branch and retire the old
  merge flow. Until separate acceptance, preserve both the version branches
  and release-tag emergency-fix route.
- Continue the Touch Display/Client Manager, Codex-assisted workflow, JDK,
  containerisation and tunnel incident plans on their own terms. None has
  been deleted or silently marked complete by this migration.

These remain recorded obligations or authority gates, not unfinished original
SP-01 through SP-09 migration implementation. Changing that distinction later
requires an explicit plan/goal update, not retroactive evidence relabelling.
