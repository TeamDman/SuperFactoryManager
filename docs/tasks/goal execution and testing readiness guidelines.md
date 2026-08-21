# Goal execution and testing readiness guidelines

**Document status:** Active
**Scope:** Every autonomous implementation goal that builds, launches, or tests SFM, Minecraft, Rust tooling, Teamy Terminal, or related local helpers
**Last updated:** 2026-08-21
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
| OPS-7 | An install or freshness check performed before the final tooling/generated-input edit can become stale again within the same goal. | Perform the conclusive rebuild/install/path/hash/smoke verification after the last relevant source or generated-runtime-input mutation. Any later relevant mutation invalidates that evidence and requires the final checkpoint again. | — |
| OPS-8 | Autonomous process recovery should not leave the human guessing whether a game/helper remains running or whether another restart is required. | The completion handoff records the final state and identity of every in-scope game/server/worker/helper changed by the goal, whether it was intentionally left running or stopped, and the exact next command when manual testing requires a launch. | — |
| OPS-9 | Dependency authority must be as explicit and bounded as process-lifecycle authority. | Every implementation goal declares either the frozen dependency posture below or a goal-specific mutable posture naming the dependency declarations, lockfiles, repositories, and changes it authorizes. If dependency mutation is not explicit, the frozen posture applies. | — |
| OPS-10 | A frozen dependency graph must not turn a recoverable cache miss into an artificial blocker. | Deterministic rehydration from existing checked-in lockfiles is permitted without further goal approval, but dependency declarations and lockfiles remain unchanged and unpinned substitutes, arbitrary local artifacts, and new developer/reference clones remain prohibited. | — |
| OPS-11 | Duration estimates for long autonomous goals are uncertain and must not become either a premature stopping point or permission to improvise unrelated work. | A long/unattended goal defines a required core plus an ordered elastic continuation ladder. Complete and checkpoint the core first, then claim one testable/reversible stretch item at a time while capacity remains. Never weaken core acceptance, skip ahead, or invent unplanned work merely to consume time. | — |

## Intent audit evidence — 2026-08-17

- **Pass 1 — extraction:** Captured the user's requirements as OPS-1 through
  OPS-10: prove final installer/tool freshness, remove game/Cargo/cache blockers
  autonomously, bound the process permission to ordinary testing scope, avoid
  indefinite waits, preserve a clear user testing path, and remember the policy
  in a discoverable repository file. The re-audit made explicit that an early
  install is invalidated by a later edit and that the final process/runtime
  state must be handed off. The dependency re-audit made explicit that a goal
  must distinguish dependency mutation from deterministic restoration of bytes
  already identified by its checked-in lockfiles.
- **Pass 2 — traceability:** Mapped each ID to the installer contract, process
  preflight/recovery order, bounded-wait diagnostics, frozen/mutable dependency
  postures, required goal metadata, and completion checklist below. Added the
  `docs/AGENTS.md` link so a fresh agent encounters the policy before working.
- **Pass 3 — adversarial omission:** Rechecked the distinctions between a
  modified CLI and Java-only work, a game-owned cache lock and an unrelated
  process, graceful shutdown and force termination, and ordinary testing
  authority versus catastrophic filesystem/system actions. Also distinguished
  a lockfile-pinned managed Git/source-build materialization from cloning a new
  developer/reference repository, and cache repair from lockfile mutation or
  arbitrary local-artifact substitution. None was collapsed into a broader
  permission. The long-goal follow-up also distinguishes estimated duration
  from acceptance, required core from ordered continuation, an unstarted
  stretch item from a claimed obligation, and useful persistence from
  improvised scope growth.
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
4. This conclusive install/verification occurs after the last relevant source
   or generated-runtime-input mutation in the goal. If any such input changes
   afterwards, rerun the affected build/install and verification; do not reuse
   the intermediate hash as final evidence.
5. The final report must state one of:

   - **User install required: no.** The agent rebuilt/installed and verified the
     executable used by the handoff.
   - **User install required: yes.** The agent gives the exact command and the
     reason it could not complete it.
   - **Not applicable.** No executable/tooling source or generated runtime input
     changed, with the unchanged-tool evidence named.

If the goal modifies only Java or documentation, do not make the user run the
installer merely by habit. State why the existing installed CLI remains valid.

### Process and cache preflight

When the user approves or sets an implementation goal whose plan incorporates
this guide, that goal approval activates OPS-2 for the goal's bounded testing
scope. The agent does not pause for a second permission merely because a proven
in-scope game, Cargo worker, or helper must be stopped/restarted.

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

At completion, record the final state of each in-scope process whose lifecycle
the goal changed: executable/path, PID when still running, stopped/running
state, why that state is intentional, and the next launch command if manual
testing starts from a stopped state. Process cleanup is not complete evidence
if the user must infer whether the game or helper is still alive.

This permission is intended for ordinary testing state such as a Minecraft
client holding the Rust/Cargo build cache. It does not authorize broad
`taskkill /IM`, guessed PID termination, repository reset, recursive deletion,
or disruption of unrelated applications. If ownership cannot be established,
stop and report that ambiguity instead of escalating the target.

### Dependency and acquisition posture

Every implementation goal must state whether dependency mutation is in scope.
Unless the goal explicitly names a mutable dependency boundary, the following
**frozen dependency posture** applies:

> The dependency graph and checked-in dependency declarations and lockfiles
> must remain unchanged. Introduce no new Cargo, SFM toolchain, Gradle, or other
> project dependency. Do not clone or acquire any new unpinned developer or
> reference repository. Deterministic cache rehydration from existing checked-in
> lockfiles is permitted, including hash-verified locked downloads,
> platform-source generation, symbol-index refresh, and materialization of
> already-pinned Git/source-build inputs. Do not run lockfile-mutating dependency
> commands or substitute arbitrary machine-local artifacts.

Under this posture, restoring a missing or corrupt cache entry is not a
dependency-graph change. The agent may use the repository's canonical tooling
to:

- download an artifact already identified by a checked-in lockfile and verify
  its locked content hash;
- repopulate Cargo or SFM-managed caches from already-pinned package or Git
  identities;
- run an existing lockfile's platform source pipeline or preferred source
  provider;
- rebuild an artifact from an already-pinned source revision and locked build
  recipe; and
- rebuild derived immutable state such as the Java dependency-symbol index.

A tool-managed bare repository, object store, or checkout created solely to
materialize an already-pinned lockfile input counts as cache rehydration, not as
cloning a new developer/reference repository. It must remain managed cache
state and must not introduce a new repository path as project or plan input.

The frozen posture does not authorize dependency `add`, `remove`, `refresh`,
`artifact accept`, version/repository/provenance changes, edits to
`Cargo.toml`, `Cargo.lock`, or `sfm-toolchain.lock.json`, or permissive local
artifact fallback such as `--allow-local-artifact-cache`. If a locked remote or
source revision is unavailable, report the exact identity and failed recovery
path rather than changing the lock, choosing a newer version, or copying an
untracked substitute.

A dependency-upgrade, lockfile-migration, publication, or dependency-tooling
goal may instead declare a **mutable dependency posture**. That declaration
must name the files and dependency identities allowed to change, whether new
repositories or network discovery are permitted, the expected lockfile diff,
and the portability/reproducibility checks required at completion. Authority to
change one named dependency does not imply authority to change unrelated ones.

### Bounded waiting and diagnostics

Commands must emit or preserve progress. Because the Codex harness buffers
stdio, a long-running `sfm-propagate-changes.exe` command should use
`--log-file <path>` so lock acquisition, process cleanup, and build progress
remain inspectable. Do not leave the user with an unexplained several-hour
wait. After a bounded retry window, inspect the owner and recover an in-scope
process or surface the exact blocker.

### Elastic continuation for long autonomous goals

For an overnight, multi-hour, or otherwise unattended goal, predicted duration
is scheduling evidence only. The plan must identify:

1. a required core with ordinary observable acceptance and operational gates;
2. a complete tested local checkpoint after that core;
3. an ordered list of independent stretch items, each with prerequisites,
   observable completion, focused validation, exclusions, and a reversible
   commit boundary; and
4. the currently claimed item, if any.

After the core passes, continue with the first eligible stretch item instead of
stopping because an estimate was pessimistic. Mark only one item in progress.
Do not begin the next until the current item passes and is checkpointed. If the
work window ends or the user returns, unstarted stretch items remain future
work and do not retroactively invalidate the core; a claimed incomplete item is
reported as active rather than relabelled complete. If the ladder is exhausted,
stop at a clean verified checkpoint and report that fact instead of inventing
new scope.

Stretch items inherit the goal's dependency, repository, process, push,
publication, and destructive-action authority. Entering a stretch item does not
silently broaden any of those boundaries. A newly discovered need outside that
authority is a reason to stop at the last passing checkpoint and request a plan
amendment, not a reason to improvise.

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
- Dependency posture: frozen / mutable — `<exact authorized boundary>`
- Dependency declarations/lockfiles changed: no / yes — `<files and intentional diff>`
- Cache rehydration performed: none / `<locked identities, commands, and result>`
- New developer/reference repositories cloned: none / `<explicitly authorized paths>`
- Process lifecycle authority: active via this guide / not applicable
- Process preflight: `<processes stopped/restarted or none>`
- Final in-scope process/runtime state:
- Cache/lock verification:
- Exact manual test command:
- Expected initial state and artifacts:
- Known manual-only or external limitation:
- Autonomous continuation ladder and currently claimed item:
```

The plan's feature acceptance remains authoritative for whether the behavior is
correct. This section is the operational proof that the user can test that
behavior without rediscovering stale tooling or a blocked process.

## Completion checklist

Before marking a goal complete, verify:

- [ ] Tool freshness and installer responsibility are explicit.
- [ ] The current executable used by tests is proven current or explicitly
  unchanged.
- [ ] The conclusive tooling freshness/install proof was captured after the
  last relevant source or generated-input mutation.
- [ ] The goal's frozen or mutable dependency posture is explicit.
- [ ] Under a frozen posture, dependency declarations and lockfiles have no
  diff, no new dependency was introduced, and any cache acquisition is traced
  to an existing locked identity.
- [ ] No new developer/reference repository or arbitrary local artifact was
  introduced outside the goal's explicit authority.
- [ ] In-scope process cleanup was performed when needed, with PIDs/ownership
  and outcome recorded.
- [ ] The final running/stopped state of every changed in-scope process and any
  needed next launch command are explicit.
- [ ] No unbounded lock/build wait remains unexplained.
- [ ] Long/unattended work completed and checkpointed its core before claiming
      at most one next stretch item; no estimated duration was used as evidence
      of completion or as authority for improvised scope.
- [ ] The manual testing path is a copyable command with expected state and
  artifact locations.
- [ ] The final report states what was not changed and what remains for a later
  goal.

This policy grants autonomous lifecycle control only within the bounded
testing scope above. It never grants permission for destructive repository,
filesystem, system-service, or unrelated-application actions.
