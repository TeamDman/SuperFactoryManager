# Redstone checkpoint integration

Plan status: Active
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

## [~] D2 Integrate with current main

Work: commit the feature checkpoint, merge current main into the feature branch,
preserve both changelog additions, and validate the integrated code. Then advance
main to the tested result while preserving its pending edits. Recheck main HEAD
and pending-file hashes before changing its checkout.

Initial main HEAD: `9e3d197a86fa6fca9439b9b5a9403e0da39ee244`.
Pending main edits: the AE2/Mekanism infusion-bank test and its changelog entry.
Feature base: `8794d96fd0f74306d70131c9b4a858db74984a89`.

Validation: focused 61-case redstone/buffer suite on the integrated source;
dedicated-server run if current main's client-registration fix permits startup.
Check the final main ancestry, expected file contents, and preserved pending edits.

Complete when: main contains the documented checkpoint and unrelated edits remain
uncommitted and intact. Do not propagate other versions or push.

## [ ] D3 Record the checkpoint and handoff

Work: record commit IDs, validation, unchanged dependency posture, installed CLI
freshness, final process state, and the exact rerun command. Open the final document.

Complete when: the user can find the reference and the main-branch checkpoint.

## Operational readiness

- Follow the repository's goal execution/testing guidance; no Gradle.
- No new dependency declarations, lockfile edits, clones, or arbitrary local artifacts. Preserve main's already-committed toolchain lock update during integration.
- Main has newer CLI code than the initially installed revision `8794d96fd`. Rebuild using main's `platform/cli/sfm-propagate-changes/install.ps1`; verify its CLI source tree matches the integration tree.
- Initial CLI SHA256: `5FAB81DC99EF2BEFE8E636EE229C8CF8A51DCA15B49F95C28547686C065ADDAD`. Final revision/hash and installer result pending.
- Process preflight: no relevant SFM CLI/Java/Cargo/Terminal processes found. Test only this checkpoint; do not interrupt other tasks.
- Build/test state and process/lock completion evidence: pending D2-D3.
- Source and Git writes require the host's normal workspace access for this worktree. Use scoped elevation and per-command Git ownership exceptions; do not change global Git trust settings.

Manual verification after integration:

```powershell
sfm-propagate-changes.exe game-test run-client --branch 1.19.2 --filter 'buffer_redstone_*,redstone_query_*,circle_redstone,side_resolve_direction,move_1_stack_direct' --keep-open
```

Expect 61 required passes. There is no stretch-work ladder for this checkpoint.
