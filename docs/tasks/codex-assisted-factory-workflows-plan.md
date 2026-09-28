# Codex-assisted factory workflows: living implementation plan

**Plan status:** Active — Rust app-server create/resume and desktop visibility proven; SFM world tools and the logistics challenge remain pending.
**Primary implementation root:** SFM `1.19.2`; companion implementation in `teamy-codex`.
**Last updated:** 2026-09-23.
**Intent audit:** Passed 2026-09-23 against the available recent user messages and linked predecessor plans; evidence below.

## How to update this plan

- `[ ]` Not started; `[~]` in progress; `[x]` complete; `[!]` blocked with evidence and unblock condition.
- Update a work item's heading and its completion notes together. Record decisions, commits, exact validation commands, and results under that item, not in a detached log.
- Add each new user requirement to the stable guidance ledger and trace it to a task, gate, or explicit non-goal before changing scope.
- A phase is complete only when all its items are `[x]`. Keep one current focus unless the plan names independent parallel tracks.
- Do not turn examples marked *possible* or *later* into committed public contracts without a recorded decision.

## Purpose and current focus

Enable a Codex agent to inspect a bounded, textual SFM cable-network snapshot, propose labels and a manager program, apply reviewed changes through `sfm.exe`, and prove that an initially unsolved logistics fixture now works. Use a Rust Codex app-server integration in `teamy-codex`, with threads discoverable and resumable in the official Codex app if the real host accepts that path. Grow the same typed observations into operator tooling and a unified in-game explorer without making the test fixture or terminal renderer the authority for world changes.

**Current focus:** Extend the now-proven operator-authorized `sfm manager show` query into bounded world observations, then design a revision-safe, reviewed manager edit protocol. The Rust CLI can create and resume a real Codex task visible in the desktop app; its first sandbox-local attempt exposed a separate-user-profile trap, now documented. Continue the original Touch Display and Client Manager release gates through their own plan; this track does not replace them.

## Scope, ownership, and safe local references

- SFM is the product and this document's owner. `sfm.exe` is the live-game tool surface; `sfm-propagate-changes.exe` remains build, worktree, and development-puppet tooling.
- `teamy-codex` owns the Rust Codex client. Its local checkout is `G:\Programming\Repos\teamy-codex`. This path varies by machine; the user explicitly approved recording it in this personal plan. The official `openai/codex` repository and community `codex-sdk-rs` are protocol references, not dependencies.
- `teamy-terminal` may host an interactive Codex CLI and later an in-world terminal bridge. Its local checkout is `G:\Programming\Repos\teamy-terminal`. This path varies by machine; the user explicitly approved recording it here. The Rust app-server path remains the focus; do not implement it in TypeScript, Node or Python.
- Cloud-Terrastodon is a reference for Rust command-module structure and awaitable `IntoFuture` request objects, not a product dependency.
- Other checkout paths, usernames, drive layouts, credentials, discovery output, and backup locations are not persisted here. SFM paths below are repository-relative. The user explicitly approved the two companion-repository paths above for this personal working plan.
- The existing `docs/architecture/sfm-touch-display-and-client-manager-plan.md`, `docs/tasks/sfm in-game control cli plan.md`, `docs/tasks/puppet control surface and rich command arguments plan.md`, and `docs/tasks/spatial semantic surfaces outlinks and capability presenters plan.md` remain their feature-domain contracts. This plan coordinates, but does not silently mark their pending work complete or supersede their decisions.

## Relationship to the original feature goal

The Touch Display and Client Manager plan remains the authority for its packet schema, image resource, client runtime, consent, action, signing, multiplayer and in-world terminal contracts. It marks P1 through P13 complete with historical evidence. Its release tasks R2 and R3 remain open: later-version propagation and aggregate operational acceptance are not done. Canonical 1.19.2 integration and the 1.19.4 checkpoint passed, but changes made since those checkpoints need fresh tests.

This plan adds a user-facing agent workflow on top of those features. Rust app-server integration, server-authorized `sfm.exe` observations and edits, the unsolved logistics challenge, top-level Explorer and operator views are new work, not hidden P14 tasks or replacements for P1 through P13. The old goal still names the packet-computation feature worktree; current SFM integration work is on the canonical 1.19.2 branch. Preserve the feature worktree as history, but make the canonical branch the implementation base for new SFM changes.

The later-version hold is explicit. The old plan's G41 postpones 1.20 and later until the user directs that work. V1 below may prepare version adapters and acceptance criteria, but it must not start those merges merely because the agent workflow reaches a 1.19.2 checkpoint. An umbrella goal may name both tracks, provided it keeps the old R2/R3 gates open and preserves this hold.

Proposed umbrella goal objective, not yet applied to the app's paused goal. The agent's available goal tool can change status but cannot edit an unfinished objective; the user can apply this wording through Codex's `/goal <objective>` command when ready, then resume the paused goal separately:

> Complete the Codex-assisted factory workflow on canonical SFM 1.19.2, following this plan for the Rust app-server client, server-authorized `sfm.exe` tools, logistics challenge, Explorer, puppets, operator views and tests. Preserve the Touch Display and Client Manager plan as an independent contract: its P1–P13 work and consent, signing, multiplayer and in-world terminal behavior must not regress, while its R2/R3 release gates remain open. Validate and checkpoint 1.19.2 first. Do not propagate beyond 1.19.4 without explicit user direction. Keep unrelated dependencies frozen; the separately authorized exact-JDK pin track is governed by `docs/tasks/parallel-objectives-orchestration-plan.md`. Make reversible decisions within scope, and record tests, commits and operational readiness. Do not call the combined goal complete until both plans' authorized acceptance gates are met.

## Authoritative user guidance ledger

| ID | Guidance, preserving confidence and timing | Disposition |
| --- | --- | --- |
| U01 | Investigate the official Codex implementation and the unofficial Rust SDK; prefer selectively vendoring/adapting useful behaviour to trusting the community SDK as a dependency. | R1–R2, D1 |
| U02 | Focus on a Rust Codex app-server SDK/client; do not build this through TypeScript/Node or Python. | R1–R3; explicit non-goal |
| U03 | Extend `teamy-codex` beyond disk backups/inspection so it can create and resume threads that appear in the official Codex app, if verified. | R2–R3, D2 |
| U04 | Draw on Cloud-Terrastodon CLI layout and `IntoFuture` request-object ideas where they help the Rust client. | R2; design reference only |
| U05 | Use subagents for parallel progress on the independent ideas; preserve findings in a resumable SFM plan that can absorb future user messages. | This ledger, intent audit, independent research tracks |
| U06 | Keep the plan in the SFM repo, while planning companion `teamy-codex` and `teamy-terminal` changes; their local checkout paths may be recorded, but scrutinize other local paths. The plan is for the user's own work. | Scope and safe references; R2, T1 |
| U07 | Start an agent logistics challenge from the `Move1StackDirectGameTest` shape, with manager program and label assignments withheld from the solver. | C1–C2 |
| U08 | Let Codex solve by inspecting SFM world state and using `sfm.exe` tools to label and program the manager. | S2–S3, C2–C3 |
| U09 | For ordinary gameplay, serialize the blocks and capabilities touching a manager's cable network into bounded text that the agent can reason over. | S2, D4 |
| U10 | Keep `sfm.exe` as the agent's tool-use interface; `sfm-propagate-changes.exe` is mainly mod-development tooling. | Scope; S2–S4 |
| U11 | Add `sfm manager list/show`, `manager labels set/add/remove/list` (with `label`/`labels` aliases), `manager program show/set`, `manager disk show/add/remove/reset`, and possibly `manager add/remove` for world placement/removal. | S2–S4, D3 |
| U12 | Add `sfm network show --cable-pos ...` to report network cables and adjacent blocks. | S2 |
| U13 | Gate CLI edits in server packet handling by existing Minecraft game-master/operator privilege mechanisms first; later explore fair personal ownership and party/group grants. | S1, S3–S4, D3; later authorization track |
| U14 | Build a console-like operator explorer for loaded factory managers, with per-manager performance such as average execution duration; consider unloaded managers only if observations are persisted honestly. | O1, D5 |
| U15 | Record manager-opening events and the opener's position and angle; an operator action such as `sfm:teleport/me_to_manager` can target that viewpoint rather than invent a safe adjacent position. | O2, D5 |
| U16 | Flesh out puppets so scenarios can be browsed and run from the title screen, not only launched by `sfm-propagate-changes.exe`. | X2; existing puppet-control plan PE/PR |
| U17 | A broad top-level Explorer should discover Files, Saves, Multiplayer Servers, Player, Puppets, and Game test definitions. World-dependent nodes should degrade at the title screen. | X1–X2, D6 |
| U18 | Under Files, expose Instance and SFM Source; allow context action to make an area the Explorer root. | X1–X3 |
| U19 | Provide breadcrumbs/history and mouse back/forward for Explorer location changes, like a browser. | X3 |
| U20 | Account for registry availability changing across Minecraft versions, using existing version seams such as `SFMWellKnownRegistries`; avoid propagation drift. | V1, D6 |
| U21 | Teamy Terminal can run Codex CLI with SFM guidance for interactive experiments; investigate SDK opportunities separately, with Rust app-server as current focus. | T1, R1–R3 |
| U22 | Preserve existing 1.19.2 feature work and postpone later-version migration until the base work is integrated and validated. | V1; scope |
| U23 | Avoid day-based effort forecasts; progress is measured by dependencies and acceptance evidence. | Whole plan |
| U24 | An isolated 1.19.2 worktree for the agent challenge is an option, not a requirement; avoid disturbing the current dirty feature checkout while experimenting. | C1, scope |
| U25 | Existing in-game terminal interaction and `sfm.exe packet send` are usable adjacent tools, but should not become the only agent protocol or a shortcut around game authority. | T1, S1 |
| U26 | The challenge should permit normal game interaction; a puppet may set up/capture a scene, but must not monopolize the window while Codex reasons. | C1–C2, X2 |
| U27 | The current goal should reflect the newer Codex-assisted trajectory without losing the earlier Touch Display and Client Manager feature or treating its remaining release work as complete. | Relationship to the original feature goal; old plan R2/R3; this plan R1–V1; goal proposal |

## Guidance traceability

Every active ID maps to the work items or decision gate in the ledger. To close the plan, verify U01–U27 individually against observed behavior, tests, or an explicit deferred/non-goal disposition. The task-specific completion criteria below are the evidence column; no row is complete merely because its design appears in this document.

## Intent audit evidence

- **Pass 1 — extraction:** Re-read the user's SDK/SFM/explorer message and the three corrections on Rust-only SDK use, operator-first editing, and SFM plan/path ownership. Added U24–U26 after checking the worktree example, existing packet/terminal tool, and nonexclusive game-window requirement against the first draft.
- **Pass 2 — traceability:** Checked U01–U26 against named work items or gates. Preserved the pre-existing CLI I-6/I-7 manager tasks, puppet PE/PR tasks, and explorer UX-HIST/NX tasks as dependencies instead of falsely treating this document as their completion. Added the server-auth prerequisite and read-only network index issue to S1–S2.
- **Pass 3 — adversarial omission:** Re-read the later corrections after the ledger update. Kept unloaded manager history, manager block placement/removal, terminal CLI hosting, and new ownership/team/party policy as possible or future work; the initial edit grant remains game-master/operator only. Separated app-server `threadSource` analytics from the actual session source and did not claim desktop visibility before a real test.
- **Known source limitation:** Earlier SFM packet/client-manager discussions are represented by the existing touch-display/client-manager and packet plans, not recreated here. This audit covers the recent user messages available in this task and links those older contracts; future corrections must amend this ledger.
- **23 September trajectory amendment:** Extraction added U27 from the user's goal-continuity request. Traceability links the old plan's completed P1–P13 and open R2/R3 to this plan's additive R1–V1 work. Adversarial review checked that the packet feature worktree is no longer described as the canonical implementation base and that G41 still bars automatic 1.20-and-later propagation. The agent has not rewritten the paused goal objective; the user can apply the proposed wording through `/goal`.

## Verified foundation and constraints

- `platform/cli/sfm/src/cli.rs` has direct `instance`, `explorer`, `packet`, `spatial`, `action`, `logs`, `terminal`, and now `manager show` commands. The `network` family and other manager verbs remain pending. `platform/cli/sfm/README.md` documents the opt-in live-game control bridge and the bounded manager read.
- `platform/minecraft/src/gametest/java/ca/teamdman/sfm/gametest/tests/general/Move1StackDirectGameTest.java` builds the exact two-barrel transfer, but currently supplies both labels and program. `.../puppet/definition/Move1StackDirectWalkthroughGamePuppet.java` already demonstrates its visual journey.
- `platform/minecraft/src/main/java/ca/teamdman/sfm/common/block_network/CableNetwork.java` exposes cable and cached adjacent capability-provider positions plus currently loaded managers within one network; it is not a complete global manager index. `ServerboundNetworkToolUsePacket.java` already assembles human-readable network/capability inspection. Read-only `CableNetworkManager.getNetworkFromCablePosition` avoids materializing a cache on a query.
- At the initial audit, `ServerboundManagerProgramPacket.java` used a menu-bound server packet while `SFMPacketHandlingContext.handleServerboundContainerPacket` lacked exact menu-target and `stillValid` checks. S1 has since added both checks and a regression. A new CLI write still requires independent server-side game-master checks and revision safety. The loopback Vox token authenticates a local process, not Minecraft privileges.
- `DiskItem.getProgramStringReadOnly`, `LabelPositionHolder.fromReadOnly`, and `ManagerBlockEntity.getStateReadOnly` are non-mutating inspection seams. `LabelPositionHolder.save` alone mutates disk NBT/cache but does not provide the full manager rebuild/sync contract required for a production CLI edit.
- `platform/minecraft/src/main/java/ca/teamdman/sfm/client/explorer/SFMExplorerRuntime.java` registers one item-registry resolver alongside filesystem and other providers; a heterogeneous root needs dispatch/composition, not a second resolver for the same scheme. The draft puppet-control plan already specifies pending PE/PR title-screen puppet work. The spatial-semantics plan has pending UX-HIST/NX branching navigation-history work; do not replace it with a linear stack by accident.
- `ManagerBlockEntity` retains only a bounded sample of executions that did work, not an all-tick average. `OpenContainerTracker` tracks currently open menus by position, not dimension-qualified historical opener viewpoints. Both need new contracts before the proposed operator display/teleport is truthful.
- At the initial audit, `teamy-codex` only read local thread SQLite/session state and supported backup. It now has a typed Rust app-server adapter and create/resume commands; R3 records a desktop-visible live task. The official CLI's `codex app-server` uses stdio JSONL and an interactive VSCode session source; `thread/start.threadSource` is separate analytics metadata. Official `codex-app-server-client` is a deep in-process facade, while community `codex-sdk-rs` remains a protocol reference rather than a dependency.
- Relevant companion sources: `G:\Programming\Repos\teamy-codex\src\cli\thread\thread_cli.rs`, `G:\Programming\Repos\teamy-codex\src\codex_state.rs`, and `G:\Programming\Repos\teamy-terminal\crates\teamy-terminal-pty\src\lib.rs`. The official Rust protocol lives in `openai/codex` at `codex-rs/app-server-protocol/src/protocol/`; the community wrapper is `codex-sdk-rs` at its `src/` and `Cargo.toml`. Cloud-Terrastodon's request-object pattern is a reference, not a path or dependency recorded here.
- Official OpenAI references: [Codex App Server](https://learn.chatgpt.com/docs/app-server) and [Codex SDK](https://learn.chatgpt.com/docs/codex-sdk). Only the app-server protocol, not the TypeScript or Python package, is in the implementation scope.
- Work only on 1.19.2 for SFM feature edits. Preserve existing uncommitted changes. Use `sfm-propagate-changes.exe`, not direct Gradle. Later-version propagation is a separate acceptance gate.

## Material design gates

| Gate | Decision to close | Initial direction and required evidence |
| --- | --- | --- |
| D1 Rust Codex dependency | Official in-process Rust client, a small typed stdio/JSON-RPC adapter, or selected vendored behavior? | **Closed for first slice:** own a small Rust stdio JSON-RPC adapter to the official `codex app-server` executable. Official stdio is supported/default; WebSocket is experimental, the in-process facade pulls deep upstream crates, and community SDK pins a broad historical Git graph. Revisit only with measured compatibility evidence. |
| D2 App visibility | Which host, Codex home, thread source, cwd/project association, and persistence yield a resumable desktop-visible task? | **Closed for local host:** run the Rust CLI in the same user profile as the desktop app; a different sandbox user has a different default Codex home. A real task was created, listed, read and resumed without source spoofing or database edits. |
| D3 Mutation authority | Exact target identity, game-master privilege predicate, unloaded-target behavior, atomic revision/CAS semantics, audit record, and CLI grammar? | Server owns checks. Initially operator/game-master only; owner/team/party policy is deferred, not implicitly granted. Harden existing menu path separately. |
| D4 World snapshot | Which capabilities, directions, inventory contents/counts, redstone and labels may be disclosed, with what bounds and freshness? | **Working assumption:** operator-only first for both reads and writes; future policy may widen reads separately. Server-owned, deterministic, paged/bounded snapshot with loaded/unloaded and truncation markers; no hidden unlimited world dump. A global manager list needs an explicit lifecycle index, not only network cache enumeration. Add per-sender request admission or a rate quota before expanding the read surface. |
| D5 Operator observation | Sampling window, retained history, dropped samples, last-opener privacy and teleport-time safety? | Loaded data first; persistent unloaded entries only after a retention and accuracy contract. Revalidate destination at invocation. |
| D6 Explorer lifecycle | Which providers are available at title screen versus world attach, and how do registries adapt on later versions? | Provider availability is explicit; no fake Player/dimension data while disconnected. Version-specific registry access goes behind annotated adapter seams. |

## Execution order

```text
R1 app-server contract → R2 Rust adapter → R3 desktop-visible thread proof
S1 server authority hardening → S2 read-only world tools → S3 reviewed edits
R3 + S2 + S3 → C1 unsolved fixture → C2 agent/evaluator loop → C3 gameplay proposal
S2 → O1 operator observation → O2 opener audit/teleport
existing explorer/puppet plan + S2 → X1–X3
R3 + C2 → T1 optional terminal journey
validated 1.19.2 slices → V1 version propagation
```

## Phase R — Rust Codex app-server path (current focus)

### [x] R1 Freeze the Rust app-server lifecycle and version boundary

**Completion notes:** Source reconnaissance chose the owned stdio adapter for D1. Official `codex app-server` uses stdio JSONL; WebSocket is experimental. The Rust in-process client serves Codex exec/TUI, while community `codex-sdk-rs` pins multiple Codex crates to `rust-v0.144.4`. The initial `teamy-codex` adapter performed `initialize`/`initialized` and read-only `thread/list` through typed `facet_json` models. An ignored live test then established handshake and list without creating a thread. R2 added controlled turns and R3 subsequently established desktop visibility and resume; this paragraph records the earlier protocol decision rather than their current status.

**Version-skew policy for this slice:** Ignore unknown notifications while awaiting a response; reject all unexpected server-originated requests with JSON-RPC `-32601` (including approvals), never auto-approve. Fail on malformed UTF-8/JSON, missing result, server error, unexpected response ID, premature EOF, or a message above 2 MiB. Continue only after a successful handshake. This fail-closed policy is deliberately stricter than future event subscriptions; once turns are supported, known event schemas and approval decisions need explicit typed handling. The caller must own process termination and reaping—`AppServerStdio` does not spawn or clean up a child.

| App-server lifecycle surface | Current contract | Implementation/evidence |
| --- | --- | --- |
| Handshake | `initialize` request, then `initialized` notification | Typed adapter; fake-server and installed-binary tests passed |
| Read-only history | `thread/list` with numeric request ID | Typed, bounded page in `teamy-codex app-server thread list`; cursor and limit supported |
| Create/resume | `thread/start`, `thread/resume`, then explicit turn start | Typed commands implemented and proven against the local host |
| Turn completion | Drain events until `turn/completed`, including server requests | Implemented; live completed turns returned `READY` and `RESUMED` |
| Approvals and unknown requests | No implicit grant; explicit decline/error | Controlled tests pass for command/file/MCP denial and empty permission grants; live proof requested no approvals |
| Cancellation/shutdown | Abort turn where supported, terminate and reap owned child | Graceful `turn/interrupt` with two-second fallback passed controlled tests; live cancellation not exercised |

**Work:** Compare official app-server docs and Rust protocol/client source with the community wrapper. Record `initialize`, `thread/start`, `turn/start`, event/approval handling, resume/list, cancellation, shutdown, source metadata, and Codex binary selection. Decide D1 with a small protocol compatibility matrix. No TypeScript/Node/Python implementation.

**Validation:** Initial fake-server tests passed for interleaved response/notification/server request, fail-closed request response, oversized input, EOF, unexpected response ID, typed page/cursor and blocked-child cancellation. `cargo test installed_app_server_handshake_and_read_only_list -- --ignored --nocapture` passed against the installed executable without creating a thread. The later full companion check is recorded in R2, and the live host task in R3.

**Completion criteria:** A Rust implementation path, protocol version policy, and explicit failure behavior for unknown messages/version skew are recorded. This does not claim desktop visibility.

### [x] R2 Implement a small Rust adapter and typed `teamy-codex` commands

**Evidence:** `teamy-codex/src/codex_app_server_stdio.rs` is the bounded transport seam. `teamy-codex app-server thread list` owns a short-lived `codex app-server --stdio` child, returns a typed cursor page and terminates/reaps the child. It uses `useStateDbOnly: true` and persisted source kinds, and passes the shared `--codex-home` to the child. The transport/process layer has typed persisted `thread/start`/`thread/resume`, read-only `turn/start`, bounded drain through `turn/completed`, typed completed/interrupted/failed outcomes, explicit denial of command/file/MCP approval requests, empty permission grants and mismatched-scope rejection. CLI start/resume commands use that same owner. Graceful cancellation sends `turn/interrupt` once a turn ID is known and falls back to kill/reap after two seconds. Controlled fake-server tests and full companion-repo `./check-all.ps1` pass (30 tests, one ignored live-host test). The successful host-context live proof returned completed `READY` and `RESUMED` turns without tools or file changes. An earlier sandbox-context attempt persisted an unmaterialized record under a different user profile; it did not prove desktop visibility and remains untouched.

**Work:** Add one focused adapter and hierarchical CLI modules following the companion repo's `AGENTS.md`; use request objects/`IntoFuture` only where they improve cancellable lifecycle clarity. Begin with transport/handshake/read-only protocol list before enabling create/turn. Correlate JSON-RPC IDs while continuously draining stdout, answer or explicitly decline server requests, drain stderr separately, and terminate/reap the child on cancellation. Keep current read-only/backup commands intact. Bound events and prompts; keep auth in Codex's own runtime, never copy credentials into the repo.

**Validation:** From the `teamy-codex` root, run `./check-all.ps1`; add fake app-server protocol tests for handshake, start, events, approvals, resume, cancellation, and malformed/late responses.

**Completion criteria:** Rust commands can create, observe, and resume one persisted Codex thread against a controlled app-server, with typed errors and no direct state-database writes.

### [x] R3 Prove official-app discovery and resume

**Work:** Run one deliberately named disposable thread in the actual local Codex host/project context. Compare its ID/session source/cwd in app-server `thread/list`, `teamy-codex thread list`, and the desktop app; resume it through both supported paths if possible. A default `codex app-server` session being classified as VSCode is promising but not proof of app project/sidebar visibility. Do not spoof `threadSource` to force display. If it is not visible, record the exact mismatch and revise D2 rather than claiming success.

**Evidence:** On 23 September 2026, the Rust CLI created disposable task `01a0cfa8-0e87-7181-9038-78abb3406ff1` in the host profile. The turn completed with `READY`. A separate `teamy-codex` process resumed that same ID after the task was restored from an unexpected archived state; the second turn completed with `RESUMED`. The host-context Rust `thread list`, desktop `list_threads`, and desktop `read_thread` all resolved that ID. The desktop app accepted a further no-tool follow-up and the rollout recorded `APP`; the app's summary projection showed an empty newest-turn item list immediately afterward, so that particular projection is not used as the acceptance evidence. No Codex SQLite or rollout file was edited directly. The first sandbox-profile attempt created only a sandbox-local, unmaterialized record and was not visible to the desktop app. Source/host selection, not source-label spoofing, was the decisive difference.

**Completion criteria:** The same thread is visible and resumable in the official app. If the experiment fails, mark R3 `[!]` with the observed source/host/project mismatch and a proposed supported alternative; do not call the requirement complete.

## Phase S — SFM world tools and server authority

### [~] S1 Harden manager-target validation and freeze operator-only write policy

**Progress:** The existing shared serverbound-container helper now rejects stale menus and manager packets whose requested target position differs from the open `ManagerContainerMenu` position. `ManagerProgramTargetAuthorizationGameTest` proves wrong-target denial, an allowed same-target edit, and stale-menu denial using an isolated fake player. It opens no GUI or live-player menu; the retained test enters the packet handler after channel dispatch. `SFMManagerOperatorAuthorization` now separately checks the actual connected PlayerList sender object, server thread, operator permission, non-spectator status, exact dimension and loaded manager. Its client-only GameTest passed 1/1, including a same-UUID fake-player denial. Canonical 1.19.2 compile passed on 2026-09-23. Revision-safe edit protocol and denial auditing remain required; this is not yet an authorization-complete write boundary.

**Work:** Add a regression for a forged manager-menu packet aimed at another loaded position; require menu-target binding and normal `stillValid` checks for all callers of the shared helper. For new CLI operations, authenticate the actual `ServerPlayer`, check `hasPermissions(Commands.LEVEL_GAMEMASTERS)` (or a version adapter) in the server packet handler before reading or writing, bind exact dimension/position, reject spectators, unloaded and stale targets, and audit denials. Existing server-config updates demonstrate packet-handler privilege checks; do not reuse the unrelated private-integrated-world packet-effect gate as an admin grant. Record future owner/team/party policy as deferred.

**Validation:** `sfm-propagate-changes.exe run compile --branch 1.19.2 --wait-for-build-lock` passed with zero errors, and `sfm-propagate-changes.exe game-test run-server --branch 1.19.2 --filter sfm:manager_program_target_authorization --wait-for-build-lock` passed 1/1. The retained test proves wrong position and stale menu, not non-operator, spectator, stale revision, disconnected or SimpleChannel registration. Add those cases before closing S1.

**Completion criteria:** No CLI or menu packet can mutate a different or unauthorized manager merely because a client connection or some manager menu is open.

### [~] S2 Expose bounded read-only `manager` and `network` observations

**Progress:** `sfm manager show <dimension> <x> <y> <z>` now sends a bounded exact-target query to the server. The reply carries only a projected program and label map or an explicit data-free denial; it reuses the connected-operator predicate. The packet-insertion private-world gate was removed from this read-only query after review because it would wrongly block the requested multiplayer operator workflow; operator authority remains server-checked. Client correlation checks request ID, target, player object, connection and world. Appending two Forge packet IDs bumped the strict channel version from `1.4.0` to `1.5.0` so old peers are rejected rather than misdecode the packet table. Canonical compile, the focused Client-only manager-query GameTest, JUnit correlation tests and offline `sfm` Rust tests passed. The fixture was restored after an oversized-case regression; assertion logs now include the exact manager coordinates. An independent security review found no bypass or leak in the bounded read path. A BlockPos wire-wrap guard passed its red/green regression (2/2). The live file-driven `manager_show_query` puppet passed: CLI `show` returned the exact manager's `NAME "show proof"` program and `input` label; an adjacent block and wrong-dimension target returned data-free denials. The puppet exited cleanly, and pre-existing preview worlds and screenshots were restored unchanged. Per-sender request admission or a rate quota remains a follow-up. This is one read-only verb, not `manager list`, disk/revision views, network topology or writes.

**Work:** Add `sfm manager list`, remaining label/program/disk reads, and `sfm network show --cable-pos ...` using server-owned snapshots. For `manager list`, add a bounded loaded-manager lifecycle index or document a narrower query; a cable cache is not global. For network show, sort cables and deduplicated six-sided adjacent block summaries, inspect only loaded positions, and distinguish missing, unloaded, untracked, and empty. Include dimension, positions, side/capability kinds, current program/labels/disk presence, revision, result truncation, authorization and per-sender request admission. Avoid claiming arbitrary unopened inventory contents are client-known.

**Validation:** Focused Java snapshot and CLI serialization tests; `platform/cli/sfm/check-all.ps1`; canonical 1.19.2 compile. Live CLI proof against a small multi-manager cable fixture, including invalid cable, unloaded chunk, paging/bounds, and unauthorized query.

**Completion criteria:** Codex can receive sufficient deterministic, bounded topology to identify source, destination, manager, and available inventory sides without opening a GUI.

### [ ] S3 Add reviewed, revision-safe label and program edits

**Work:** Add canonical `manager label[s] set/add/remove/list` and `manager program show/set`; preserve the single contained disk as the authority. Freeze exact `--manager-pos`, `--label`, `--label-pos`, dimension, file/stdin and alias spelling before publishing. Accept program text from stdin/file rather than lossy command-token quoting; parse/diagnose before apply; require exact expected revision/content hash and operator privilege; return a diff/preview before mutation. Rebuild/mark dirty/sync the manager after label mutation. Coordinate with the existing manager editor so concurrent saves cannot silently overwrite each other.

**Validation:** Focused server authority, label codec, parser, CLI stdin/Unicode and stale-CAS tests; live operator and denied-nonoperator journeys. Failed preflight or denied operations produce no partial disk mutation.

**Completion criteria:** An authorized operator can apply a reviewed program/label change to exactly one manager, and the same command fails safely for an ordinary player or stale editor.

### [ ] S4 Decide and implement destructive manager/disk operations separately

**Work:** Specify `manager disk show/add/remove/reset` and optional `manager add/remove` placement semantics, drops, backups, confirmation, and idempotency. Reuse D3 server authority but require an additional explicit destructive-operation contract; do not infer that program-edit permission authorizes block removal.

**Validation:** GameTest item/block conservation, denial, rollback and repeated-request cases; live confirmation for destructive verbs.

**Completion criteria:** Each verb has a documented reversible or explicitly confirmed result; no loss of a disk/program is concealed as a successful edit.

## Phase C — Agent-solvable logistics challenge and player workflow

### [ ] C1 Create an unsolved persistent challenge fixture and verifier

**Work:** Reuse the two-barrel geometry and expected 64-dirt transfer of `Move1StackDirectGameTest`, but leave the manager program and labels unset in a separate opt-in fixture. Do not weaken the existing 100-tick regression or require an ordinary `/test run` to wait for an LLM. A fixture/session persists long enough for agent interaction and leaves the game window free; a separate verifier checks the transfer and records the submitted program, labels, revisions, and tool calls. An isolated 1.19.2 worktree is an optional experiment boundary if current uncommitted feature changes would interfere.

**Validation:** Existing Move1StackDirect GameTest still passes; new fixture initially fails only the logistics objective, accepts a known-good CLI solution, rejects an incorrect label or program, and runs without taking sole control of the player's window.

**Completion criteria:** A reproducible challenge can be set up, inspected, solved and verified without putting the answer in the solver's prompt or the fixture's mutable program/disk fields.

### [ ] C2 Run a Rust Codex thread using `sfm.exe` as its tool surface

**Work:** Give the agent the CLI contract and the exact fixture objective; let it call read-only observations and submit reviewed edits through `sfm.exe`. Record thread ID, tool sequence, proposal, authorization, and verifier output. Separate agent reasoning from the server's authority checks.

**Validation:** One actual app-server thread solves the challenge end-to-end; a no-write and a denied-write run fail honestly without a false positive. Repeat from a fresh fixture to catch answer leakage.

**Completion criteria:** The agent's observable tool use, not prefilled test code, creates the labels/program that move the stack.

### [ ] C3 Generalize to a player-initiated proposal flow

**Work:** On a player request, serialize only their authorized network view, ask Codex for a proposed labels/program diff, and require an explicit authorized apply. Under the first policy, an ordinary player may inspect the proposal but only an operator can apply it. Define failure/unknown capability reports and a path for the user to edit the proposal. Owner/group permission design remains future work after the operator-only proof.

**Validation:** Two distinct user-built network fixtures, a misleading/unloaded topology, and a denied non-operator apply; no ambient or unsolicited world mutation.

**Completion criteria:** A user can understand and review what the agent intends to change; execution remains server-authoritative. Ordinary-player self-service is not claimed until a future ownership/group authorization phase passes.

## Phase X — Discovery explorer and puppet access

### [ ] X1 Compose an everything-explorer root from typed providers

**Work:** Add a top-level discovery root with Files → Instance/SFM Source, Saves, Multiplayer Servers, Player, Puppets, and Game test definitions. Reuse current lazy resolver/selection/action architecture; unavailable providers present an explicit reason and do not pretend a world is loaded. File roots remain bounded by existing filesystem authority.

**Validation:** Title-screen and in-world resolver tests; inspect every root under no world, integrated world, and server connection. No synchronous filesystem/world scan on render.

**Completion criteria:** A user can discover existing explorer domains from one root, while world-only nodes degrade gracefully at the title screen.

### [ ] X2 Browse and run puppets from the title screen

**Work:** Resume the existing puppet-control plan's pending PE-1–PE-3 and PR-1–PR-2 rather than forking them: expose its `registry://sfm/puppet/` development-only catalog, Run/Open Definition/Reveal Definition offers, and a restartable single-active run session. `SFMGamePuppetDiscovery.gatherPuppets()` can enumerate all definitions; current selected discovery/harness require a startup selector. Provide a title-screen entry point for the unsolved challenge. Avoid shipping dev-only definitions in an ordinary release jar.

**Validation:** Follow the existing puppet plan's `sfm-propagate-changes.exe puppet browse --branch 1.19.2` proposed journey after that command exists; live title-screen browse/run and return-to-title evidence.

**Completion criteria:** A user can start the challenge from the title screen without relaunching Minecraft with a different puppet selection.

### [ ] X3 Add root focus and navigation history

**Work:** Context action can replace Explorer root with Files/Instance/SFM Source or another eligible node. Reconcile with the spatial-semantics plan's pending UX-HIST-1/2 and NX-1: navigation history is branching and append-only, not a linear stack that discards forward branches. Add breadcrumbs and mouse-button 4/5 mapping; preserve stable path identity and selection across root changes. Reuse the existing action engine rather than inserting fake `..` child records. The current panel accepts only left/right buttons.

**Validation:** Resolver/action tests for navigation cycles, stale/unavailable roots, context action capture, and mouse/keyboard parity; one live title-screen journey.

**Completion criteria:** The user can focus, backtrack, and return without losing the semantic path or leaving a broken Explorer session.

## Phase O — Operator observability (independent after S2)

### [ ] O1 List manager performance with honest observation windows

**Work:** Sample loaded manager execution time and recent resource movements with a bounded ring/window and drop counters. Name the metric: the existing last-20 `didSomething` durations are not an average of all ticks or idle cost. Expose typed CLI results and an operator Explorer domain. Treat unloaded managers as absent unless a deliberate persisted-summary contract is added; network tracking is purged on unload. Distinguish stale historical summaries from live performance.

**Validation:** Multi-manager slow/fast fixture, unload/reload, capped history, dropped samples, authorization, and live `manager list/show` output.

**Completion criteria:** An operator can find slow loaded managers with units, window and freshness, without fabricated all-time averages.

### [ ] O2 Add manager-open audit and safe last-opener teleport

**Work:** Record authorized manager-open events with actor, dimension, position, viewpoint and time under a bounded privacy/retention policy. Existing `OpenContainerTracker` tracks only currently open menus keyed by position, so do not reuse it as dimension-qualified history. Offer an operator-only `sfm:teleport/me_to_manager` action that uses the recorded opener viewpoint, but checks current dimension/chunk/collision and refuses unsafe or missing history.

**Validation:** No-history, stale/obstructed viewpoint, cross-dimension, non-operator, multiple-opener, and successful safe teleport tests; prove no spectator/private data leaks through ordinary-player views.

**Completion criteria:** Operator navigation uses an observed player viewpoint when safe and otherwise fails clearly without teleporting inside blocks.

## Phase T and V — Interactive use and compatibility

### [ ] T1 Connect the agent workflow to Teamy Terminal without duplicating authority

**Work:** Preserve the option to run Codex CLI interactively inside Teamy Terminal with SFM guidance, while the Rust app-server client remains the primary automation path. Reuse `sfm.exe` commands and existing terminal transport; do not make a PTY shell transcript the structured agent protocol or bypass server checks.

**Validation:** One interactive terminal journey can inspect the challenge and execute a permitted command; the same server denial is observed through terminal and Rust-client paths.

**Completion criteria:** Terminal is a convenient UI, not a second manager-edit implementation.

### [ ] V1 Reconcile documentation and propagate only after 1.19.2 acceptance

**Work:** Update the existing companion plans to cross-reference completed work without overwriting their separate scope. Update CLI help/examples and the gameplay changelog for visible features. Prepare version-adapter audits, but do not merge 1.20 or later until the user directs that hop under the original plan's G41. When authorized, use `sfm-propagate-changes.exe git merge` and preserve newer-version behavior and registry lifecycle differences.

**Validation:** Run `platform/cli/sfm/check-all.ps1` from SFM, `./check-all.ps1` from the approved `teamy-codex` checkout, and `sfm-propagate-changes.exe run compile --branch 1.19.2 --wait-for-build-lock` from SFM; add focused and full SFM suites when implementation reaches release scope, then target-specific compile/GameTest and `sfm-propagate-changes.exe` audits for each supported later version.

**Completion criteria:** SFM docs, CLI behavior, real app thread proof, end-to-end challenge, authorization tests, and supported-version matrix agree; no later branch is overwritten to make a merge green.

## Acceptance matrix

| Surface | Status now | Required release proof |
| --- | --- | --- |
| SFM 1.19.2 integrated client | Primary implementation | CLI observation/edit, challenge, title-screen explorer/puppet and operator journeys |
| SFM dedicated or multiplayer server | Design target for operator authority | Exact server-side privilege tests; transport availability and policy documented; no private-world gate bypass |
| Later Minecraft branches | Deferred until 1.19.2 integrated | Version-adapter audit, compile and focused GameTests per target after propagation |
| `teamy-codex` Rust client | Companion implementation | Fake-server protocol suite and real official-app thread visibility/resume |
| `teamy-terminal` | Optional interactive companion | CLI parity journey without extra authority |

## Risk register

| Risk | Guardrail |
| --- | --- |
| App-server thread is persisted but hidden from the desktop sidebar | R3 verifies source/host/cwd/project in the real app; no database spoofing or false visibility claim. |
| Community SDK pins a large or stale Codex dependency graph | D1 selects a small versioned adapter by evidence; unknown protocol fields fail or degrade explicitly. |
| CLI caller escapes Minecraft menu or operator authority | S1 hardens legacy target binding; S3 checks exact sender, dimension, privilege and revision on the server. |
| Repeated bounded reads still exhaust server work | S2 adds per-sender admission or rate quota before wider read APIs; retain payload caps and explicit denial. |
| Agent sees too much or too little world data | D4 bounds and authorizes snapshots; explicit unloaded/truncated/unknown markers; C2 repeats from fresh fixture. |
| An ordinary GameTest times out while the agent is thinking | C1 separates persistent setup/session from deterministic verification. |
| Operator metrics or opener history imply false accuracy or expose player location | O1 records sampling windows/drop counts; O2 adds retention, access and teleport-time safety checks. |
| Everything Explorer duplicates existing puppet plan or breaks at title screen | X1–X2 compose existing resolvers and reference PE/PR; D6 makes provider availability explicit. |
| Later-version propagation clobbers registry/toolchain differences | V1 follows oldest-branch-first workflow and adapter audit; do not merge while base acceptance is missing. |

## Overall completion criteria

- [ ] U01–U27 have a final traced disposition and the three-pass intent audit is recorded as passed.
- [ ] A Rust-only `teamy-codex` client creates and resumes a thread genuinely visible in the official Codex app; a documented failed gate is not completion.
- [ ] `sfm.exe` exposes bounded typed world observations and revision-safe operator-authorized label/program updates; unauthorized/stale operations never mutate a manager.
- [ ] An unsolved logistics fixture is solved by actual agent tool use and independently verified from a fresh run.
- [ ] The top-level Explorer, title-screen puppet journey, operator observation and optional terminal path have the scope-specific proof above or remain explicitly unfinished; no deferred item is silently described as shipped.
- [ ] SFM 1.19.2 is validated before any later-version propagation; the existing dirty worktree and companion plans are preserved.
