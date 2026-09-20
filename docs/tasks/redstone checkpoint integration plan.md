# Redstone checkpoint integration

Plan status: Complete
Last updated: 16 September 2026
Primary branch: `feat/1.19.2/contraption-as-code`; integration target: `1.19.2`

## Intent and update rules

Use `[ ]` for pending, `[~]` for active, `[x]` for complete, and `[!]` for blocked.
Update evidence under the task it supports. Keep one integration focus.

| Requirement | Coverage |
| --- | --- |
| Document introduced redstone functionality, including open GitHub issues and discussions. | D1; dated states, implementation/proposal distinction, examples and source/test links. |
| Prepare the document before merging the redstone checkpoint into main 1.19.2. | D1 precedes D2; preserve unrelated work and verify the integrated result. |
| Preserve this work before returning to bidirectional 3D exploration. | Commit code, tests, examples and documentation; defer new contraption features. |

Intent audit: extraction captured the latest document-and-merge request;
traceability mapped it to D1-D3; omission review checked sequencing, preservation,
GitHub context, and separation from future exploration. Earlier feature plans
provide compacted implementation history. This request supersedes their earlier
local-only, no-commit/no-merge scope. It does not request a push, release, issue
closure, or posting to GitHub Discussions.

## [x] D1 Prepare the redstone reference

Evidence: drafted the feature guide with working examples, all 6 directly
relevant open issues, related buffer/reload issues, 5 core Discussions, and the
R1-R8 gate map. Preserved proposal differences and the 61-test evidence.

Work: write `docs/redstone-support.md` from current source, runtime evidence,
GitHub issues and Discussion bodies/replies. Sanitize machine-specific paths
in the earlier notes before including them in a commit.

Research: checked the redstone/buffer issue searches, direct issue states, searched
all 67 Discussions, and read relevant bodies plus replies on #202, #400 and #613. GitHub sources
are linked in the document; no external messages or state changes are needed.

Validation: confirm local links, SFML examples, dates/states, code contracts,
and advertised limits. Keep historical proposal values distinct from current
integer capacity and comparator limit.

Complete when: the reference is reviewable and accurately describes what lands.

## [x] D2 Integrate with current main

Work: commit the feature checkpoint, merge current main into the feature branch,
preserve both changelog additions, and validate the integrated code. Then advance
main to the tested result while preserving its pending edits. Recheck main HEAD
and pending-file hashes before changing its checkout.

Initial main HEAD: `9e3d197a86fa6fca9439b9b5a9403e0da39ee244`.
Pending main edits: the AE2/Mekanism infusion-bank test and its changelog entry.
Feature base: `8794d96fd0f74306d70131c9b4a858db74984a89`.

Checkpoint commit: `6216fb85ec474ec5afbc0c654031b210fd4c788e`.
Integrated feature commit: `b08513e0187a6030b983c6c85b01ddec5289f0f8`.
The sole merge conflict was the changelog; both branches' entries are retained.

Dedicated-server validation on 16 September: 61 required tests passed, process
exit 0. Evidence: `redstone-integration-server-game.log` reports
`All 61 required tests passed :)`. Main's existing client-registration change
resolves the original feature base's dedicated-server startup failure.
Integrated-client validation on the same date passed all 61 required tests.
Evidence: `redstone-integration-client-game.log` reports
`SFM_CLIENT_PUPPET_TESTS_PASSED required=61 total=61`.
Both runners exited 0. The focused suite is 29 world-query cases, 29 buffer
cases, and 3 existing regressions. Only documentation changed after these runs.

Documentation evidence commit: `8ea56a9ef3ceabbe9fcd52547db34c5ee4846a34`.
Main merge: `57a9cfb5613214b4dd17bfad00e967ebc8a34fb6`, with original main as
its first parent and the documentation evidence commit as its second parent.
The committed tree matches the feature tree exactly. The gameplay source and
tests match the tested integration commit; subsequent changes only record this
documentation and handoff.

Preservation: the AE2/Mekanism infusion-bank file retained its original byte
hash. Its pending changelog patch is identical, excluding Git's blob IDs. Only
the overlapping changelog was temporarily stashed, then restored cleanly.
A separate energy investigation added pending tracked and untracked files during
validation. Those files were left in place and excluded from the merge commit;
its concurrent edit to an untracked test was retained. Main's runtime had exited
before integration. No reset, force push, or unrelated commit was used.

Validation: focused 61-case redstone/buffer suite on the integrated source;
dedicated-server run if current main's client-registration fix permits startup.
Check the final main ancestry, expected file contents, and preserved pending edits.

Complete when: main contains the documented checkpoint and unrelated edits remain
uncommitted and intact. Do not propagate other versions or push.

## [x] D3 Record the checkpoint and handoff

Work: record commit IDs, validation, unchanged dependency posture, installed CLI
freshness, final process state, and the exact rerun command. Open the final document.

Complete when: the user can find the reference and the main-branch checkpoint.

Evidence: `docs/redstone-support.md` records the local merge, introduced behavior,
61-case server/client validation, dated GitHub issue and Discussion links, and
remaining work. The final handoff links that reference in main. Integration is
local; no remote branch, issue, Discussion, release, or other Minecraft version
was changed. Broader contraption exploration remains deferred.

## Operational readiness

- Follow the repository's goal execution/testing guidance; no Gradle.
- No new dependency declarations, lockfile edits, clones, or arbitrary local artifacts. Preserve main's already-committed toolchain lock update during integration.
- Rebuilt and installed main's CLI using `platform/cli/sfm-propagate-changes/install.ps1` (`cargo install --locked --offline`); installer exit 0. No new tooling source edits were needed.
- Installed command: `<CARGO_HOME>/bin/sfm-propagate-changes.exe`, version `0.1.1`, revision `9e3d197a8`, built 16 September 2026. CLI source tree `db966901f42f89da2657d926881e593bac1130e3` matches the integrated feature. SHA256: `BE3D494C4CFF699F236FC000DBB234DDF9D60C77029F70862377CFBF7F0F629B`.
- User must run install.ps1: no. The installed command ran the tests; final version, hash, and source-tree verification after both runs matched the evidence above.
- Process preflight: no relevant SFM CLI/Java/Cargo/Terminal processes found. Test only this checkpoint; do not interrupt other tasks.
- Both test processes exited normally. The client runner (PID 31932) and its JVM (PID 39148) are gone; no process from this checkpoint is left running. An unrelated main-branch energy test began separately and was left untouched. Its process exited before integration; any subsequent energy-test runs belong to that separate task.
- Both launch paths acquired their required caches and completed without lock recovery. No cache deletion, unpinned dependency acquisition, new clone, or dependency declaration change was needed. Client assets reported zero downloads.
- Test artifacts remain in the feature worktree: `redstone-integration-server-game.log` and `redstone-integration-client-game.log`; CLI build/launch logs share the corresponding prefixes. The run profiles were `runGameTestServer` and `runClientPuppet`, under `platform/minecraft/build/sfm-toolchain/run`.
- Source and Git writes require the host's normal workspace access for this worktree. Use scoped elevation and per-command Git ownership exceptions; do not change global Git trust settings.

Manual verification after integration:

```powershell
sfm-propagate-changes.exe game-test run-client --branch 1.19.2 --filter 'buffer_redstone_*,redstone_query_*,circle_redstone,side_resolve_direction,move_1_stack_direct' --keep-open
```

Expect 61 required passes. There is no stretch-work ladder for this checkpoint.
The client starts a dedicated test world and stays open after success with
`--keep-open`. Inspect the test chat and the `runClientPuppet/console.log` under
the target checkout's toolchain run directory. No installer or manual setup is
required before this command. Production buffer availability and the lifecycle,
modded-emitter, and tunnel gaps remain as documented in the feature reference.
