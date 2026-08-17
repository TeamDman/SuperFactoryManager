# Goal execution and testing readiness guidelines

**Document status:** Active
**Scope:** Every autonomous implementation goal that builds, launches, or tests SFM, Minecraft, Rust tooling, Teamy Terminal, or related local helpers
**Last updated:** 2026-08-17
**Authority:** Repository-wide operational guidance; feature plans still own feature scope and acceptance criteria

This document records the operating promises that make a completed goal useful
to the human tester. A goal is not operationally complete merely because the
source changes compile or a test artifact exists. The completion handoff must
also say whether the user's installed tools are current, whether the game can
be launched immediately, and whether any process or cache state was reset.

## Authoritative guidance ledger

| ID | Active guidance | Required consequence | Superseded by |
| --- | --- | --- | --- |
| OPS-1 | The user should not have to discover after goal completion that a modified CLI or helper still needs `install.ps1`; the goal must establish tooling updatedness and make the testing path clear. | Every goal completion records source revision, modified-tool status, installed executable path/hash, the installer/build command actually run, and an explicit `User must run install.ps1: no` or the exact command if it could not be run. | — |
| OPS-2 | Build and test goals may be blocked by an already-running game, Rust helper, Cargo process, or occupied build cache. | Goal submission authorizes inspection and lifecycle control of in-scope SFM/Minecraft/toolchain processes without an additional approval prompt. The agent may gracefully stop and restart them, then force-stop their proven process tree after a bounded timeout. | — |
| OPS-3 | Process cleanup must not become a vague permission to kill unrelated or catastrophic targets. | A process is in scope only when its PID, command line, executable path, parent/child relation, or working directory ties it to the goal's branch, SFM toolchain cache, launched game, Vox/Teamy Terminal helper, or test runner. Never use broad image-name killing, delete/reset a repository, terminate unrelated user applications or services, or overwrite arbitrary data under this authority. | — |
| OPS-4 | Autonomous work must not wait invisibly for hours behind a lock or hung process. | Use bounded waits and progress evidence; inspect lock/process ownership, use `--log-file` when stdio is buffered, and either recover an in-scope owner or report the exact external blocker. No opaque wait may continue indefinitely. | — |
| OPS-5 | A stale installed executable or stale cache can make a successful test misleading. | Evidence names the target commit, branch, cache/profile, executable path, tool revision/hash where available, and the command's relevant output. A runtime test is not accepted as proof of current source until the source/tool freshness boundary is established. | — |
| OPS-6 | The user should receive an immediately actionable manual-testing handoff. | The final goal report gives the exact CLI launch/test command, required branch/profile/variant, expected initial state, artifacts to inspect, and any known manual-only step. | — |

## Intent audit evidence — 2026-08-17

- **Pass 1 — extraction:** Captured the user's requirements as OPS-1 through
  OPS-6: prove installer/tool freshness, remove game/Cargo/cache blockers
  autonomously, bound the process permission to ordinary testing scope, avoid
  indefinite waits, preserve a clear user testing path, and remember the policy
  in a discoverable repository file.
- **Pass 2 — traceability:** Mapped each ID to the installer contract, process
  preflight/recovery order, bounded-wait diagnostics, required goal metadata,
  and completion checklist below. Added the `docs/AGENTS.md` link so a fresh
  agent encounters the policy before working.
- **Pass 3 — adversarial omission:** Rechecked the distinctions between a
  modified CLI and Java-only work, a game-owned cache lock and an unrelated
  process, graceful shutdown and force termination, and ordinary testing
  authority versus catastrophic filesystem/system actions. None was collapsed
  into a broader permission.
- **Known source limitation:** None for this guidance request.

## Confirmed operational contract

### Tool freshness and installer responsibility

When a goal modifies or may modify `sfm-propagate-changes`, its generated
artifacts, `teamy-terminal`, Vox/Figuré/Facet inputs, or another executable used
by the testing path:

1. The agent must rebuild the affected tool before claiming completion.
2. For `sfm-propagate-changes`, run the repository installer from
   `platform/cli/sfm-propagate-changes`:

   ```pwsh
   .\install.ps1
   ```

   The script derives the current worktree's nine-character Git revision and
   runs `cargo install --path $PSScriptRoot --locked --offline`.
3. The agent must verify the installed command after installation using its
   resolved path, a smoke command, and a file hash or tool-reported revision.
   If the executable does not expose a source revision, record the installer
   revision, installed path, hash, and smoke-command result together; a package
   version alone is not proof of source parity.
4. The final report must state one of:

   - **User install required: no.** The agent rebuilt/installed and verified the
     executable used by the handoff.
   - **User install required: yes.** The agent gives the exact command and the
     reason it could not complete it.
   - **Not applicable.** No executable/tooling source or generated runtime input
     changed, with the unchanged-tool evidence named.

If the goal modifies only Java or documentation, do not make the user run the
installer merely by habit. State why the existing installed CLI remains valid.

### Process and cache preflight

At goal start, before a long build or live launch, record:

- target branch and source commit;
- relevant running processes and their command lines/working directories;
- known build/cache lock state;
- whether the game, server, Rust worker, Teamy Terminal, or test runner is
  already running;
- the intended launch profile and artifact/cache directory.

For in-scope processes, the autonomous recovery order is:

1. request normal shutdown and wait a bounded interval;
2. if still alive, terminate the identified process tree and wait/reap it;
3. verify that the lock, port, window, and child-process state are gone;
4. rebuild or relaunch from the current source and record the new PID(s).

This permission is intended for ordinary testing state such as a Minecraft
client holding the Rust/Cargo build cache. It does not authorize broad
`taskkill /IM`, guessed PID termination, repository reset, recursive deletion,
or disruption of unrelated applications. If ownership cannot be established,
stop and report that ambiguity instead of escalating the target.

### Bounded waiting and diagnostics

Commands must emit or preserve progress. Because the Codex harness buffers
stdio, a long-running `sfm-propagate-changes.exe` command should use
`--log-file <path>` so lock acquisition, process cleanup, and build progress
remain inspectable. Do not leave the user with an unexplained several-hour
wait. After a bounded retry window, inspect the owner and recover an in-scope
process or surface the exact blocker.

## Required goal metadata

Every future goal that can launch or build something should add this compact
section to its plan or completion record:

```markdown
## Operational readiness

- Target branch/commit:
- Tooling or generated runtime inputs changed: yes/no; details:
- Installer/build command run:
- Installed executable/path and revision/hash:
- User must run install script: no / yes — `<exact command>` / not applicable — `<reason>`
- Process preflight: `<processes stopped/restarted or none>`
- Cache/lock verification:
- Exact manual test command:
- Expected initial state and artifacts:
- Known manual-only or external limitation:
```

The plan's feature acceptance remains authoritative for whether the behavior is
correct. This section is the operational proof that the user can test that
behavior without rediscovering stale tooling or a blocked process.

## Completion checklist

Before marking a goal complete, verify:

- [ ] Tool freshness and installer responsibility are explicit.
- [ ] The current executable used by tests is proven current or explicitly
  unchanged.
- [ ] In-scope process cleanup was performed when needed, with PIDs/ownership
  and outcome recorded.
- [ ] No unbounded lock/build wait remains unexplained.
- [ ] The manual testing path is a copyable command with expected state and
  artifact locations.
- [ ] The final report states what was not changed and what remains for a later
  goal.

This policy grants autonomous lifecycle control only within the bounded
testing scope above. It never grants permission for destructive repository,
filesystem, system-service, or unrelated-application actions.
