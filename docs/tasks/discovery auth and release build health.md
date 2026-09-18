# Discovery authentication and release build health

Plan status: Complete for the authorized authentication, build-repair and reproduction scope; limitations below remain explicit
Primary implementation root: 1.19.2; version adapters start at the oldest affected branch
Last updated: 2026-09-17
Intent audit: Passed against the authentication investigation and follow-up authorization

## How to update this plan

Use `[ ]` not started, `[~]` active, `[x]` complete and `[!]` blocked in task headings.
Keep evidence beneath its task. A successful merge is not build validation.

## Authoritative user guidance and traceability

| ID | Active guidance | Work and acceptance |
| --- | --- | --- |
| A1 | Find the previous temporary-secret cleanup work, including scheduled deletion, task self-removal and missed-run nuances; search recent repositories before inventing it again | Task 1: committed historical evidence and limits |
| A2 | Reduce repeated 1Password prompts for CurseForge discovery with explicit purpose-scoped login and a configurable TTL | Task 2: login once, search and list repeatedly without invoking 1Password |
| A3 | Missing authentication must fail with an actionable login command; remove the upload-token fallback | Task 2: negative tests; no automatic secret lookup |
| A4 | Publishing must not consume the discovery credential or silently inherit author credentials | Task 2: separate authorization, host and method boundaries |
| A5 | Local TTL does not revoke the underlying API key | Task 2: documentation and fail-closed local expiry independent of scheduler |
| A6 | Repair 1.21.1 compilation, add Just Dire Things for tests and continue the real reproduction | Task 4; preserve exact JDT and approved Vox pins from the energy investigation |
| A7 | Verify all supported versions, including reported 26.1.2 failure, not only 1.21.1; use parallel CLI builds and repair procedurally | Task 3: ten-target matrix, oldest-first fixes, rerun all after final edits |
| A8 | Sequence historical research, usable discovery authentication, then cross-version repair and reproduction | Tasks 1 to 4 in that order |
| A9 | Approved native Windows DPAPI and Task Scheduler APIs, enabling only required features on pinned windows 0.62.2; no upgrades or Defender exclusions | Task 2: native implementation and dummy-credential lifecycle proof |
| A10 | Approved exact Vox pin alignment on the eight remaining release branches, preserving unrelated dependencies and profiles | Task 3: scoped lockfile comparisons, full matrix and oldest-first Java repairs |
| A11 | Explain the remaining Rust/JDK mismatch, update issue 610 using gh outside the sandbox, and commit and push the completed changes | Publication follow-up: verified snapshot differences, reviewed commits and issue evidence; no production energy fix |

## Intent audit evidence

- Pass 1 — extraction: reread the two authentication messages and latest approval, retaining purpose, TTL, least privilege, historical reuse, all-version scope and ordering as A1 to A8.
- Pass 2 — traceability: each ID maps to a bounded task and observable validation. Earlier JDT work remains foundation, not a new production transfer fix.
- Pass 3 — adversarial omission: checked the distinctions between local expiry and key revocation, merging and compiling, discovery and publishing, real mod tests and reduced probes. No commit or push is implied.
- Known source limitation: earlier implementation tool output was compacted; the energy investigation plan retains its evidence. Latest user instructions are available in full.

## Boundaries and decisions

Use the SFM CLI, not Gradle. Follow the repository's goal execution and testing readiness guidelines, including final installer responsibility and bounded test-process cleanup.
Preserve existing dirty GameTests, fixture changes and changelog edits. Do not reset worktrees or overwrite later-version adapters.
Production energy-transfer changes are out of scope: the requested endpoint is a real reproduction.
Dependency posture: existing pinned graph, plus already authorized JDT test dependency and exact Vox alignment across the release matrix. Vox source revision and artifact identity must match the approved baseline; preserve branch scopes, features, profiles and unrelated dependencies. Authentication may enable required security, cryptography, COM, variant and Task Scheduler features on the existing windows 0.62.2 dependency. No unrelated version upgrades or Defender exclusions.
Persist repository-relative paths and generic user-data locations, not machine/user-specific paths or secret values.

Authentication contract: `curseforge auth login --purpose discovery --ttl 20m`, `auth status`, `auth logout`.
Login alone may read the Core API key from 1Password. Discovery supports the explicit Core API key/environment source or a valid local lease, never an author token. Publishing requires an invocation-specific author credential.
Windows lease: user-scoped DPAPI, restricted user ACL, unique file/task per lease, strict bounded TTL (maximum 1 hour), no sliding extension, no secret in task arguments/logs. Scheduled deletion is backup; expiry is checked before authenticated requests. A task must not delete a renewed lease. Task registration failure must not leave a usable cached credential.
Cross-platform commands may use explicit credentials; persistent login fails clearly where secure storage is not implemented.

### [x] 1. Recover the historical cleanup knowledge

Found the `skills` repository commit `0b9bdec247631d2421695111c543dec564759e58`, 9 August 2026, `add temp secret task cleanup guidance`.
The committed `windows-temporary-secret-cleanup` skill records private file ACLs, exact-path deletion, self-unregistration, `StartWhenAvailable`, `DeleteExpiredTaskAfter=P30D`, and the missing `EndBoundary` issue.
Broad Rust/PowerShell/Markdown/C# source scans across the local repository roots and recent commit-message scans did not identify a separate application implementation. Existing task-name inspection found no surviving custom credential-cleanup task. Do not claim the original application was identified.
Reuse the recorded lifecycle, with DPAPI rather than plaintext storage. Microsoft Task Scheduler documentation confirms missed runs can be delayed and task expiry requires trigger end boundaries. Therefore deletion timing must not grant credential validity.

### [x] 2. Implement and verify explicit discovery authentication

Native implementation approved after Defender blocked the encoded PowerShell helper before credential creation. The blocked helper is removed, not allowlisted. The native implementation uses current-user DPAPI, an owner-only DACL, COM Task Scheduler registration and a narrow `auth cleanup --lease <generated-name>` command. Task actions contain no credential or arbitrary deletion path. Existing cleanup guidance is reused for missed runs, trigger end boundaries and self-removal.
Native validation: the main library suite passed 734 tests (4 ignored); the explicit Windows dummy lifecycle test passed in 26 seconds. It proves encryption, task settings, exact-file deletion, self-unregistration and preservation of a newer lease. Actual Core login followed by JDT search and compatible-file listing succeeded without repeated secret acquisition. The installed CLI reused that lease for both commands; logout removed the real test lease and no matching task remained.
`check-all.ps1` passed formatting, lint and build, then failed the existing Java-analysis scenario snapshot test. A September 17 structural comparison corrects the initial identity-only count: of 35 differing scenarios, 28 differ only in their context's JDK root identity/path. The remaining seven include JDK source hashes/spans or index fingerprints, and a missing dependency index instead of the expected partial index. Expected snapshots were not overwritten. `cargo test --locked --offline --all-features -- --skip java_analysis_scenarios` passed 795 tests total (734 + 9 + 12 + 40; 4 ignored). This is not a claim that the complete suite is green.
The `definition_at_position_jdk_string` expected source hash matches `String.java` in JBRSDK 17.0.6 b829.9; its actual hash matches JBRSDK 17.0.14 b1367.22. The latter is the selected, hotswap-fixed runtime. Its class end moves from line 4656 to 4660. This is a Java-analysis test-input change, not a Rust compiler or JVM ABI incompatibility. `usage_at_position_partial_index` additionally expects a valid partial dependency index, but the selected identity has no index directory. Do not bless that difference as cosmetic. Follow-up: make scenario JDK/index inputs deterministic, verify source-span changes, and construct the intended partial-index fixture before accepting reviewed snapshots. Do not revert the hotswap runtime to satisfy old snapshots.
Installed outside the sandbox with `install.ps1`: revision ea4dcc9aa, executable SHA-256 `a044ab89b35768e5981b8fff1c0685240386ce23d22b880b3cc43249104dceea`. No Cargo lockfile version changes. README now documents explicit login, limits and publishing separation.
Also repaired dependency inventory writes: edits retain schema-4 features/profiles and inactive components instead of serializing the runtime projection as schema 3. Add, refresh, remove and source-configuration share this writer; the new inactive-component regression passed.
Intent audit extension for A9: extracted the explicit native-only approval, mapped it to this task, and checked that it does not authorize dependency upgrades or security exclusions.

Work: add login/status/logout; remove discovery's automatic `op` reads and author fallback; separate publishing authorization; restrict authenticated HTTP requests to their intended origin and method. Preserve ordinary dependency discovery. Review error/debug output for secret leakage.
Validation: focused Rust tests for TTL boundaries, corruption, source separation, redaction, renewal, host/method rejection; Windows dummy-secret integration test for encryption, task settings, deletion and self-removal; then actual Core login followed by repeated project/file discovery with no further prompts. Run `check-all.ps1`, install outside sandbox, verify installed help and executable hash.
Completion: no secret appears in artifacts/output; no unexplained cleanup task remains; installed commands match tested source. The user does not need to run the installer.

### [x] 3. Verify and repair the entire release build matrix

The initial compile-only matrix ran with two workers but ended without a final summary; its partial diagnostics are not a complete matrix result. All ten target HEADs still match the table. Only baseline and 1.21.1 had dirty work at preflight. No SFM/Java/Cargo build process remained at resumption.
Initial infrastructure finding: eight branches pinned Vox at aa75598dabb2138b18365cdf0d97ca94a34c5319 / hash 3bd59c93..., whereas baseline and approved 1.21.1 pinned f2afdece6c79e64085d2f8c047e22fe16b2c8c54 / hash 4d1e8835.... Both versions declare the same Maven cache filename. Parallel materialization quarantined the other valid pin as a hash mismatch and rebuilt it. Keep this distinct from a corrupt download or a Java compile failure.
Under A10, aligned the eight older Vox records in `platform/minecraft/sfm-toolchain.lock.json` to the baseline source-build acquisition, source revision and artifact hash. Structural JSON comparisons proved all other dependencies, profiles, features and each branch's existing Vox scopes unchanged. Exact approved pin: `f2afdece6c79e64085d2f8c047e22fe16b2c8c54`, `blake3:4d1e88353f941be926fdf84f1dd8da9bd594b60f`.
The first complete post-alignment matrix passed 1.19.2 and failed nine targets. The shared Vox cache rebuild completed and its lock released after about 51 seconds; no security exclusion or process termination was needed.
Oldest-first repair: 1.19.4 item icons and inventory replay now receive the caller's PoseStack; all four source sets compile. On 1.20, migrated the newly propagated workspace/picker/review/terminal rendering paths and the literal-glob diagnostic puppet to GuiGraphics, preserving existing older-screen adapters. Explicit version annotations mark changed rendering signatures. Main, GameTest, datagen and test compilation passed before carrying context-checked patches through 1.21.1.
Second full matrix: 1.19.2, 1.19.4, 1.20 and 1.20.1 pass all source-set compilation. 1.20.2 through 1.21.1 have eight initial event/import errors; 26.1.2 has 339 initial diagnostics, including renamed identifiers and newer GUI APIs. These are compilation counts, not runtime validation. Current focus: repair 1.20.2 NeoForge events, then remaining GUI/input boundaries, before proceeding forward.
Subsequent evidence: 1.20.2's NeoForge imports, four-argument scrolling and background adapters passed all source sets before propagation. The third complete matrix passed seven releases through 1.20.4. The 1.20 JUnit suite passed all 430 tests. ClientTickEvent.Post, the existing SFMResourceLocation factory adapter and a missing puppet ArrayList import then brought 1.21.0 and 1.21.1 to individual compile passes. Preserved 1.21.1's already-migrated ComputerCraft adapters.
26.1.2 now individually compiles main, GameTest, datagen and test: migrated only stale GUI/input/identifier paths, preserving existing extraction-based screens; adapted registry holder lookups, deferred clipping, texture lifecycle and puppet event records. Compiler warnings remain (187 in the last run), chiefly deprecated NeoForge capability APIs; no dependency version was upgraded.
Runtime follow-up: the first real JDT server attempt stopped before tests because common startup initialized client action classes and resolved Screen on DEDICATED_SERVER. This is not a pass despite process exit 0. Baseline already isolates client registrations; carry that intent to the older release adapters, validate dedicated startup, and rerun the complete matrix after those edits. 1.21.1's existing pinned Mekanism main artifact also needs GameTest-runtime scope for the real-block fixture; do not enable it in ordinary runtime or bundling.
Completion: isolated client registration behind the physical-client guard, preserving each branch's existing setup ownership. The oldest affected branch, 1.19.4, passed the real `water_tank_capacity_scaling` dedicated-server test before the adapter was carried forward. Added only GameTest-runtime scope to 1.21.1's existing pinned Mekanism main component, with no normal-runtime or bundle scope.
Final complete matrix: **10/10 targets passed**, all configured main, GameTest, datagen and test source sets. JUnit: **430/430 each on 1.20, 1.21.1 and 26.1.2**, no failed/skipped/aborted tests. No graphical client was launched, so this is not a claim of exhaustive interactive rendering validation. All branch `git diff --check` checks passed. Final structural lockfile comparison retained every unrelated dependency/profile/feature field, allowing only approved Vox, JDT and the required Mekanism test-runtime scope.
Audit caveat: version-surface audit still reports broad existing divergence from baseline (9 CLI and 17978 Java warnings); its committed-version comparison is unchanged by uncommitted repairs. The default source audit additionally reports unresolved font-rule calls. Do not present a compiling matrix as full feature parity or a warning-free audit. Preserve this separate propagation debt instead of merging unrelated baseline features into the repair.
Intent audit extension for A10: extracted the eight-branch approval, mapped it to scoped lockfile validation and the full matrix, then checked that it does not authorize unrelated upgrades, bundling changes or profile changes.

Work: record worktree status/HEAD and preflight processes, run `run compile --branch core --parallel=2 --error-action continue` with progress logs. Confirm `core` selects precisely the release matrix below before execution. Use bounded concurrency to avoid cache/resource contention.
Repair oldest affected target first, then graft common intent into later adapters without replacing newer behavior. Use `@MCVersionDependentBehaviour` for genuine API differences. Do not commit or invoke commit-producing merges without authority; carry targeted source patches and report remaining propagation explicitly.
Validation: `audit --branch core --version-surfaces` before/after; every target compiles all configured Java source sets after final fixes. Run focused CLI/JUnit/GameTests where changed behavior needs more than compilation. Report any excluded source sets and unsupported dependencies honestly.

| Branch | Initial HEAD | Initial compile | Final compile |
| --- | --- | --- | --- |
| 1.19.2 | ea4dcc9aa | pass | pass |
| 1.19.4 | b3a7c8404 | 3 item-rendering errors | pass |
| 1.20 | cdaf21f74 | 16 removed GuiComponent imports | pass |
| 1.20.1 | 1db963188 | 16 removed GuiComponent imports | pass |
| 1.20.2 | 621ae3195 | 24 GUI/loader errors | pass |
| 1.20.3 | 76220351f | 24 GUI/loader errors | pass |
| 1.20.4 | adaf2fd4e | 24 GUI/loader errors | pass |
| 1.21.0 | 497880ea9 | 24 GUI/loader errors | pass |
| 1.21.1 | 0f89d40be | 24 GUI/loader errors | pass |
| 26.1.2 | 70a6a9530 | 339 initial production errors | pass |

### [x] 4. Run the real JDT reproduction and hand off

Continue task 6 in `mekanism linter and energy investigation.md` after current-source compilation succeeds.
Completed on current 1.21.1 source: all six required real-mod cases passed and the dedicated server exited normally. The single-label/dropper-first/quantity-1000 cases reach 99999 FE, then starve the later consumer for four passes while the source remains full at 256000000 J. Healthy-first, full-dropper and retention-1000 controls continue supplying the later consumer. Exact results and limitations are in the energy investigation plan. This verifies the defect without changing production transfer behavior.
Validation: `sfm-propagate-changes.exe game-test run-server --branch 1.21.1 --filter "issue_610_jdt_*"`; record real droppers, pinned Mekanism adapter, five passes and order/quantity/retention controls. Repeat baseline focused energy/linter tests if shared changes affect them.
Completion: actual runtime results or precise external blocker; do not confuse characterization passing with a production fix. Give copyable manual commands, tooling freshness, dirty files and final process state.

## Risks and acceptance

- Same-user DPAPI is not isolation against another process running as the same user. A copied key remains valid until revoked at CurseForge.
- Scheduler delay, sleep or logout cannot extend local validity; failed cleanup remains detectable and retryable.
- Authenticated clients must not leak custom headers to download URLs or redirects.
- All-branch repairs may expose additional version-specific errors. Keep the complete matrix rather than declaring success after fixing first diagnostics.
- Cache rehydration is allowed only for locked identities. Ask before changing additional dependency pins.
- Completion requires all four tasks, test evidence, documentation and final CLI installation. The implementation phase did not authorize publication; A11 subsequently authorizes reviewed commits, branch pushes and an evidence update to issue 610. It does not authorize a release or production energy-transfer change.

### [x] 5. Publish the reviewed investigation and repairs

September 17 follow-up: A11 authorizes publication. Pass 1 captures the Rust/JDK explanation, outside-sandbox gh requirement and commit/push request. Pass 2 maps them to verified snapshot comparisons, issue 610 and scoped commits across the ten repaired branches. Pass 3 keeps the existing runtime fix, unmodified production transfers and separate skills-repository edits outside this publication work.

The source review found no real credentials or new concrete local paths in the pending changes. Matches were an explicitly dummy credential, regular-expression escapes and portable `$sfm-cache` paths. Existing unpushed branch history contains earlier propagation commits; ordinary fast-forward pushes include that history without rewriting it. Keep CLI tooling, the AE2 fixture adjustment, energy characterization and version-specific repairs in separate commits. Publish the real-mod results as an issue comment, retaining the original report and leaving the issue open because production behavior is not fixed.

Validation: inspect staged diffs and `git diff --check`; verify pushed branch tips against origin; read back the issue comment. No code changes or snapshot regeneration are part of this publication follow-up.

Completion evidence: the atomic, non-force push succeeded for all ten matching release branches. Local tips and origin matched afterward. Baseline commits are `ca373df99` (CLI authentication and inventory), `0148a986b` (AE2 pattern) and `d8133b8c4` (energy characterization). Release repair tips are `b98b06f80` (1.19.4), `6bf484576` (1.20), `faa040ce1` (1.20.1), `a829fb4db` (1.20.2), `704aa69ed` (1.20.3), `11d3ed07d` (1.20.4), `43068d610` (1.21.0) and `6bd27f038` (26.1.2). The 1.21.1 repair is `fa4069b1d`, followed by the separate real-JDT test commit `7524ab551`.

Posted [the issue 610 findings](https://github.com/TeamDman/SuperFactoryManager/issues/610#issuecomment-5724171813) with `gh issue comment` outside the sandbox. The comment links committed source, explains the one-FE-gap sequence, gives all six measured controls and the rerun command, and leaves the issue open. No production fix is claimed. This final documentation/changelog commit follows the code push; the Rust snapshot follow-up remains open, with no expected outputs changed.

## Operational readiness at the implementation checkpoint

- Targets: all ten branches at the recorded HEADs plus local repairs; no commits or merges were made.
- Tooling: native-auth CLI installed earlier in this task; no subsequent Rust change. Final executable check still reports revision ea4dcc9aa and SHA-256 `a044ab89b35768e5981b8fff1c0685240386ce23d22b880b3cc43249104dceea`. User installation required: no.
- Commands: `run compile --branch core --parallel=2 --error-action continue --wait-for-build-lock`; `test run --branch 1.21.1`; `test run --branch 26.1.2`; `game-test run-server --branch 1.21.1 --filter "issue_610_jdt_*"`, all through `sfm-propagate-changes.exe`.
- Dependency posture: exact approved Vox pin alignment, exact approved JDT test dependency, existing exact Mekanism jar enabled for GameTest runtime only. Other dependencies/profiles/features and Vox scopes preserved. Cache rehydration used locked source/artifact identities; no arbitrary reference clone or version upgrade.
- Process lifecycle: only bounded compilers, JUnit JVMs and isolated dedicated GameTest servers; final process inspection found no Java or SFM CLI process remaining. No user world/client was opened and no process kill was needed.
- Evidence: compile diagnostics remain in each branch's `platform/minecraft/build/sfm-toolchain/project`; real test console remains in 1.21.1's `platform/minecraft/build/sfm-toolchain/run/runGameTestServer/console.log`. Successful runtime pass counts, not JVM exit codes alone, establish acceptance.
- Expected manual result: rerunning the JDT command reports six passes while logs intentionally show starvation in the characterization cases. These tests must become forward-progress regressions when a production fix is authorized.
- Known limitations: no full graphical client exercise, no claim of baseline feature parity, existing audit/deprecation warnings, and the earlier Rust Java-analysis/JDK snapshot mismatch remains documented in task 2. No further stretch scope claimed.
