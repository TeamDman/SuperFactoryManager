# CI and container puppet experiment

**Plan status:** Active
**Primary implementation root:** branch `ci/1.19.2-container-puppet`, based on `707f53f4a`
**Last updated:** 2026-09-19
**Intent audit:** Passed against the initial CI/container request

## How to update this plan

Use `[ ]` for not started, `[~]` for in progress, `[x]` for complete, and `[!]`
for an external blocker with evidence and an unblock condition. Update each
task's completion notes with its status. The independent tracks are workflow
implementation, container implementation, and integration/validation.

## Authoritative user guidance and traceability

| ID | Guidance | Coverage and required evidence |
| --- | --- | --- |
| U1 | Make CI/CD verify Super Factory Manager builds properly for 1.19.2. | Task 2: use the canonical Rust CLI to compile, test and package a mod JAR on GitHub. Artifact delivery is this experiment's CD boundary; release publishing is a later decision. |
| U2 | The 1.19.2 checkout is busy with other development; experiment in a worktree and establish Actions behavior on a non-default branch. | Tasks 1 and 2: separate branch/worktree; push-triggered workflow run tied to its commit. |
| U3 | Docker may be installed but not running; experiment with Docker. | Tasks 1 and 3: inspect actual runtime availability and execute Linux Docker on a hosted runner if local prerequisites are absent. |
| U4 | A future Discord help-channel bot should run isolated game instances rather than an unprotected host process. | Tasks 3 and 4: restricted disposable worker, bounded lifetime/resources and broker separation. Do not infer authorization to connect or message Discord. |
| U5 | Determine how graphics work in Docker or potentially Kubernetes. | Tasks 3 and 4: Xvfb/Mesa experiment with screenshot evidence; document Kubernetes translation and its untested status. |
| U6 | Existing game puppet manipulation and screenshot capture is likely the fixture. | Task 3: use the existing puppet; require its result and screenshot, not merely a successful process start. |
| U7 | The user identified the existing 1.19.2 source checkout. | Task 1: confirmed `TeamDman/SuperFactoryManager`, branch `1.19.2`. Refer to this machine-varying path as `<existing-1.19.2-checkout>` in public notes. |

## Intent audit evidence

- Extraction: reread the initial request and recorded build verification, branch/worktree constraints, uncertain local Docker installation, Discord isolation purpose, graphics/Kubernetes question, existing puppet, and source checkout as U1-U7.
- Traceability: every requirement maps to a task and evidence; Docker images and workflow plumbing are reversible implementation choices within the requested experiment.
- Adversarial omission: preserved the future nature of the help bot and possible Kubernetes deployment; neither is represented as already deployed. The busy checkout remains outside the implementation working directory.
- Source limitation: none.

## Foundation and constraints

The original checkout was clean at `707f53f4a`; its origin and remote default
branch are `TeamDman/SuperFactoryManager` and `1.19.2`. There is no checked-in
Actions workflow at the base. GitHub Actions is enabled. The isolated worktree
uses branch `ci/1.19.2-container-puppet`.

`docs/AGENTS.md` requires the Rust `sfm-propagate-changes` tool rather than
Gradle. Its commands own compilation, JUnit, packaging, game launches and
puppets. Minecraft targets Java 17. Follow
`docs/tasks/goal execution and testing readiness guidelines.md`.

Project dependencies and lockfiles stay frozen. Deterministic restoration of
their pinned inputs is allowed. Container base images and OS graphics/build
packages are new infrastructure inputs for this experiment; they do not change
the mod's dependency graph. No credentials, developer caches, Docker socket,
host display, or user home should be exposed to a game worker.

The concrete user checkout path reveals machine storage layout and is not
needed by CI. Public notes preserve its role with the placeholder above; no
publication confirmation is needed for that redacted form.

## [x] 1. Establish an isolated experiment and runtime availability

**Completion notes:** Created a worktree on `ci/1.19.2-container-puppet` from
`707f53f4a`. GitHub authentication works outside the sandbox. Docker was not
found via PATH, standard install directories, indexed file search, or installed
application records. WSL reports uninstalled. Podman CLI 6.0.2 is installed but
has no machine or connection; its server connection fails. Therefore use
GitHub-hosted Linux Docker for the experiment without requiring a host reboot
or changing OS virtualization configuration.

**Validation:** `git status --short --branch`, `git worktree list`, `gh auth
status`, `gh workflow list`, `wsl --status`, `podman version`, `podman machine
list`, and `podman system connection list`.

## [~] 2. Build and deliver a mod artifact from the feature branch

**Completion notes:** Initial experiment commit `f7dc28338` pushed successfully.
GitHub started [run 35460447959](https://github.com/TeamDman/SuperFactoryManager/actions/runs/35460447959)
from the feature branch without a default-branch merge. The workflow passed
actionlint 1.7.12. This proves the branch trigger, not yet the build. Its Linux CLI
compiled successfully; Java acquisition then failed inside pinned Vox's
`VoxRuntimeTest.generatedChannelRoundTripHonorsCreditAndCancellation` with
`lane is not open: CLOSED`. The initial Docker build independently stopped when
`javac` treated UTF-8 Phon test sources as US-ASCII. Neither failure was bypassed.
Commit `bd6aff529` adds UTF-8 locale after `10967aefc` introduced a checksum-pinned
JBR 17.0.6 build compiler and independent puppet JVMs. Current Java 17 remains
the explicit mod compiler/game runtime. [Run 35461041230](https://github.com/TeamDman/SuperFactoryManager/actions/runs/35461041230)
tests these fixes; the intervening superseded run was cancelled by concurrency.

**Work:** Add a push/PR workflow with least permissions, explicit 1.19.2 scope,
fresh-checkout tooling, bounded jobs, preserved failure diagnostics, and mod
artifacts. Confirm non-default branch behavior with a real run.

**Validation:** Push the experimental branch; inspect Actions job results,
canonical compile/JUnit/JAR output, and artifact contents. Use a local branch
at the exact event commit so CLI worktree selection also works for PR checkouts.

**Completion criteria:** A recorded remote commit/run verifies current source
and produces the mod JAR, or a reproducible upstream blocker is precisely
recorded without claiming a passing build.

## [~] 3. Run a graphical puppet inside a restricted Docker worker

**Completion notes:** `containers/sfm/` contains a two-stage image, independent
graphics probe, offline runtime wrapper and screenshot verifier. Static Bash
syntax checks pass. The independent graphics job passed in run `35460447959`:
Mesa 22.3.6 reports llvmpipe (LLVM 15.0.6), OpenGL core 4.5, and no hardware
acceleration. Container inspection confirms exit 0, UID 10001, network `none`,
read-only root, dropped `ALL` capabilities, no-new-privileges, no mounts, 1 GiB
memory and 128 PIDs. The in-container assertions also verified seccomp filtering.
The canonical path decoder needed a portability fix:
`JsonPath` now accepts Windows separators on Unix and writes forward slashes,
without changing the lockfile. Five regression tests cover cached artifacts,
source-build outputs and optional paths; all five passed via `cargo test --locked
json_path::tests --lib`. The title and orbit fixtures now run in separate fresh
clients because discovery sorts puppets alphabetically and the title capture
requires the initial loading overlay. Full Minecraft execution is pending.

Required `check-all.ps1` results: dependency policy, formatting, all-feature
Clippy with denied warnings, and build pass. Outside the sandbox, 739 unit tests
pass with four ignored, nine Java integration siblings pass, and the release
review integration suites pass 12 and 40 tests. The Java analysis snapshot suite
fails because installed JDK source content/hash differs from its recorded JDK
fixtures (for example, `String.java` has 4660 lines instead of 4656). Snapshots
were not changed. An initial sandbox-only inability to launch `rg` was resolved
by the normal-user rerun. Doc tests report zero cases.

**Work:** Build the canonical Linux tool and prewarm pinned game inputs.
Run the client using Xvfb and software OpenGL. Use a non-root worker with
capabilities removed, no host bind mounts, no network at execution time,
bounded memory/CPU/PIDs/time, and disposable writable state. Copy only selected
evidence out after execution.

**Validation:** Require renderer diagnostics, puppet success evidence and PNG
output from the actual game. Verify runtime settings with container inspection.

**Completion criteria:** A real container run proves game/puppet/screenshot
operation under the stated restrictions, or records the first actual failing
layer without substituting a desktop-only test.

## [ ] 4. Review isolation and provide reproducible handoff

**Work:** Document exact tested commands, evidence and limitations. Describe a
Discord broker/job boundary and Kubernetes translation, with ephemeral jobs,
resource limits, private loopback puppet control, separate bot credentials,
restricted security context and enforced network policy. Distinguish ordinary
container isolation from a hostile-code sandbox.

**Validation:** Review workflow/container diffs against the observed results;
check no project dependencies or busy-checkout files changed; verify tools and
process state. Do not publish release artifacts or deploy Discord/Kubernetes.

**Completion criteria:** A fresh operator can repeat the verified experiment
and identify the remaining production decisions.

## Risks and acceptance boundaries

| Risk | Guardrail |
| --- | --- |
| Machine-local dependency hides a CI failure | Fresh Linux checkout and pinned acquisition; no local artifact fallback. |
| Launch exit code hides puppet failure | Assert result JSON and nonempty screenshot evidence. |
| Game can execute terminal commands | No broker credentials or host access; finite disposable worker; stronger VM boundary for hostile workloads. |
| Cold build exhausts runner or time | Stage caches and record per-layer diagnostics with bounded jobs. |
| Branch workflow or credential permissions prevent remote execution | Record exact GitHub error; complete concrete local files before requesting any required account action. |

## Operational readiness

- Target: `ci/1.19.2-container-puppet`, base `707f53f4a`.
- Tooling source changes: portable serialized-path conversion in `jar_build/json_path.rs`.
- Installer: `platform/cli/sfm-propagate-changes/install.ps1` completed successfully with locked offline acquisition. Installed command reports `10967aefc`; SHA-256 `95095EB678494595B6B40C7E37A1B155F2AB17EA713931235A111035ED82D5EF`. Its source subtree `d3785ff480719c67f1574efa5bfede644e653d93` is identical at `bd6aff529`. No user installer step is required. CI builds its own executable from each event revision.
- Dependency posture: frozen project dependencies; new container infrastructure as scoped above.
- New developer/reference clones: none.
- Process preflight: no local game launch or process termination is planned; hosted workers own their test processes.
- Final process state, exact test commands, artifact evidence and remote run URL: pending validation.
