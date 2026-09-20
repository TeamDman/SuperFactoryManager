# CI and container puppet experiment

**Plan status:** Complete
**Primary implementation root:** branch `ci/1.19.2-container-puppet`, based on `707f53f4a`
**Last updated:** 2026-09-19
**Intent audit:** Passed against U1-U8, including the authorized Vox update

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
| U8 | Continue on GitHub Actions if useful; Podman may be started locally. Update SFM to the new Vox build and publish it to the appropriate Teamy branch. | Task 2: publish a narrow Java-only Facet fix, pin exact source/hash, retain independent hosted verification and preserve busy integration work. |

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

Project dependencies and lockfiles stay frozen except for the explicitly
authorized Vox Java fix. Deterministic restoration of pinned inputs is allowed.
Container base images and OS graphics/build
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

## [x] 2. Build and deliver a mod artifact from the feature branch

**Portability repair checkpoint:** The earlier native Linux and Windows runs compiled all
SFM Java source sets. Linux then exposed Windows-only paths in ten test classes;
Windows exposed CRLF conversion of canonical replay JSON. Test fixtures now use
native absolute paths/URIs, and the JSON fixtures explicitly use LF. The one
native Windows case-insensitive containment test is scoped to Windows. No
production path validation or assertion was weakened.

The Vox Java fix is published at
`4a079ac1c8a8bb8a914811ef55945bc1d9a9fef3` on
`TeamDman/facet` / `teamy/vox-java-late-credit` ([PR #2](https://github.com/TeamDman/facet/pull/2)).
The user-suggested `teamy-main` belongs to the older Roam repository and does not
contain this Java runtime. The new branch starts at SFM's exact previous pin and
leaves `teamy/terminal-selection-paste` unchanged. The canonical locked
`vox-xtask package-java` recipe passed with the historical Java compiler, full
Java suite, 19 deterministic regressions, repeat-JAR equality and dependency
checks. The resulting artifact hash is
`blake3:2be34a7d38bbd4a455d2a933c856c9462630f47a`.
Only the Vox artifact hash, derived expected hash, source commit, branch and
portable source-root reference changed in SFM's lock. Rust pins and all other
dependencies remain unchanged. The final hosted results below verify this pin.

**Hosted acceptance update:** [PR #617](https://github.com/TeamDman/SuperFactoryManager/pull/617)
tests source `d7e22e73e` as merge revision
`77e42edb0469ae9ca71b19b5b677c12cf245e79e`.
[Linux run 35481820090](https://github.com/TeamDman/SuperFactoryManager/actions/runs/35481820090)
passed all three jobs. Native JUnit reports 2,075 passed, zero failed, one
Windows-only skip and six existing opt-in tests aborted by missing helper/fixture
prerequisites. The mod JAR is 8,920,959 bytes, SHA-256
`cb1ec405414992f3f37731dd3b8ddb9fcb142dc8aa96007d991b436208d14e5d`.
Its embedded Vox JAR independently hashes to the approved new value above.
[Windows run 35481820091](https://github.com/TeamDman/SuperFactoryManager/actions/runs/35481820091)
also passed, reporting 2,076 passed, zero failed/skipped and the same six opt-in
aborts. Its mod JAR is 8,921,315 bytes, SHA-256
`7b576c99b416ba1f40e63a70de116bf15f07ccd6a5768e2628a9824bf4a21491`.
Both archives contain the required mod/mixin/refmap/jarjar entries and identical
Vox JAR bytes. The complete mod archives are not byte-identical across platforms;
this experiment does not claim cross-platform mod archive reproducibility.
Windows completed in 33m17s; Linux native build completed in 14m28s.

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

That second run finished: graphics and the Linux path tests passed. The UTF-8
container failure was resolved, but both the native and container source builds
failed in the same pinned Vox Java test. The historical compiler alone does not
resolve it; artifact hash reproducibility has not yet been reached. No mod JAR
was produced. An independent [Windows run](https://github.com/TeamDman/SuperFactoryManager/actions/runs/35461257226)
uses the same source and explicit separation between the historical dependency
compiler and current Java 17 mod runtime. Its dedicated branch avoids cancelling
other experiments when the diagnostic changes.

The Windows run finished with a distinct infrastructure failure. The CLI and all
five path tests passed. Vox's canonical recipe passed its Java suite, duplicate
JAR byte comparison and Java smoke test, then rejected the startup banner emitted
by global `JAVA_TOOL_OPTIONS` as an unresolved dependency. Its `jdeps` check
requires empty stdout and stderr. The fix scopes UTF-8 to `JDK_JAVAC_OPTIONS`
instead; the container's JVM tuning must likewise avoid affecting `jdeps` during
preparation. These environment fixes do not change the dependency graph. A fresh
build attempt will validate both fixes and the next actual layer.

The [focused Linux diagnostic](https://github.com/TeamDman/SuperFactoryManager/actions/runs/35461627486)
compiled the unchanged 181 pinned Java source files and ran the original test
once. JVM exception logging captured `message for unknown channel 1:1` in
`VoxConnection.processInboundChannel` before connection shutdown. The first
40-item transfer is affected. Late receiver credit is a hypothesis supported by
the transfer roles and deterministic driver probe; the original trace does not
record the message body. A review-only patch and probe are being tested in
disposable checkouts through a separate workflow. They are not SFM build inputs.

The [candidate comparison](https://github.com/TeamDman/SuperFactoryManager/actions/runs/35462115154)
at `e8318d2f0` passed on Linux: the candidate ran the unchanged original test once
and all 19 deterministic variants passed. The unchanged baseline also passed
once in this comparison, so the original source-test failure is timing-sensitive,
not guaranteed on every run. Local reduced sequences distinguish active sender
credit (passes both) from identical credit after sender Close (unknown-channel
failure in the baseline, passes with the candidate). The candidate follows the
pinned Rust driver's absent-credit behavior while preserving Java's accepted-lane,
message-direction and numeric validation. No source tests are retried to obtain
a green result, and no candidate JAR is supplied to SFM.

The [reduced Linux run](https://github.com/TeamDman/SuperFactoryManager/actions/runs/35462297128)
at `06e8fe9ce` compiled untouched pinned source and the probe in a fresh directory.
It ran only the closest passing and failing inputs: active credit exited 0;
identical credit after local Close exited 1 with the exact unknown-channel error
from `processInboundChannel`. Its receipt is `baseline-failure-reproduced`, with
zero full original-test runs. This positive diagnostic result records a confirmed
bug reproduction; it is not a passing dependency build.

The user subsequently authorized publishing the Vox Java fix and updating SFM to
that build. The mutable boundary is limited to Vox Java source/test changes in
`TeamDman/facet` and its source commit, branch, cache identity and exact artifact
hash/derived checks in `platform/minecraft/sfm-toolchain.lock.json`. Other project
dependencies and Cargo declarations/locks remain frozen. The user suggested
`teamy-main`; inspection found that branch in the older `TeamDman/roam` repository,
which has no Java runtime. SFM actually pins Facet's
`teamy/terminal-selection-paste` at `f2afdece6`. A separate
`teamy/vox-java-late-credit` worktree starts at that exact published commit so
the fix cannot absorb or overwrite unrelated newer work.

The next hosted runs at `859b6cbf4` passed dependency preparation. Windows compiled
SFM, then two canonical replay JSON tests failed because checkout converted their
LF fixture to CRLF. A narrow `.gitattributes` rule preserves the canonical bytes.
Docker compiled and packaged the mod JAR and executed both real graphical
puppets during preparation. Its final verifier looked in the wrapper console
instead of the authoritative child-process log. The raw completion marker must
be checked in that child log, with fresh copies per puppet. Offline restricted
execution is still a separate, pending check.

**Acceptance complete:** Both operating systems independently rebuilt the exact
published Vox pin, passed canonical JUnit and packaged the mod. No source-test
bypass, arbitrary cached JAR or hash relaxation was used.

**Work:** Add a push/PR workflow with least permissions, explicit 1.19.2 scope,
fresh-checkout tooling, bounded jobs, preserved failure diagnostics, and mod
artifacts. Confirm non-default branch behavior with a real run.

**Validation:** Push the experimental branch; inspect Actions job results,
canonical compile/JUnit/JAR output, and artifact contents. Use a local branch
at the exact event commit so CLI worktree selection also works for PR checkouts.

**Completion criteria:** A recorded remote commit/run verifies current source
and produces the mod JAR, or a reproducible upstream blocker is precisely
recorded without claiming a passing build.

## [x] 3. Run a graphical puppet inside a restricted Docker worker

**Accepted result:** Run `35481820090` built the prepared image in 19m12s, then
completed both fresh offline clients in 3m42s inside the container. Raw JVM logs
contain one successful completion per puppet and the passed
`move_1_stack_direct` GameTest. All 11 PNGs satisfy the strict manifest verifier
and were separately decoded during artifact inspection. The source receipt is
the exact tested merge revision above. Docker inspection confirms UID 10001,
network `none`, no bind mounts, only the anonymous `/workspace` volume, read-only
root, dropped `ALL` capabilities, no-new-privileges, 8 GiB memory/swap cap,
four CPUs and 512 PIDs. In-container assertions confirm seccomp filtering;
the process exited 0 without an OOM kill. Cleanup removes the worker and volume.

The first orbit image has incomplete geometry; later views show the full SFM
fixture. This is an observed capture-readiness limitation for the future bot,
not evidence of a production screenshot-quality guarantee. An external Vox
control smoke test and the Discord broker remain future integration work.

**Earlier checkpoint:** Run `35462847488` built the distributable mod and ran
both real puppets during Docker image preparation, generating three title and
eight world captures. The final check failed because it read the CLI progress
log instead of the raw JVM console. `run.sh` now preserves the fresh per-puppet
JVM log; the verifier requires exactly one successful completion and exactly
three title/eight world captures, and rejects failure markers. Eleven focused
verifier regressions pass, including misleading wrapper output, duplicate
completion, extra/duplicate captures, missing images and failing process exits.
The host wrapper refuses nonempty artifact destinations without deleting their
contents; a stub-Docker regression checks both rejection and fresh destinations.
These evidence tests run in the workflow alongside the graphics probe.
The accepted final run repeated both puppets after disabling networking and
applying all runtime restrictions; preparation screenshots alone were insufficient.

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
requires the initial loading overlay. The second run passed the graphics
restrictions again but stopped at Vox source preparation. The third run advanced
through both real game launches as recorded in the current checkpoint above.

Required `check-all.ps1` results: dependency policy, formatting, all-feature
Clippy with denied warnings, and build pass. Outside the sandbox, 739 unit tests
pass with four ignored, nine Java integration siblings pass, and the release
review integration suites pass 12 and 40 tests. The Java analysis snapshot suite
fails because the selected JDK source content/hash differs from its recorded JDK
fixtures (for example, `String.java` has 4660 lines instead of 4656). An installed
JBR 17.0.6 matches the expected complete source archive and all three checked
source files, but the suite explicitly selects branch `1.19.2` and reads that
busy checkout's saved JBR 17.0.14 plan. Its provider ignores `JAVA_HOME` and offers
no task-local override. The busy plan and snapshots were not changed. An initial
sandbox-only inability to launch `rg` was resolved
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

## [x] 4. Review isolation and provide reproducible handoff

**Completion notes:** The container guide records exact commands, measured
results, runtime restrictions and production limitations. Read-only reviews
found and fixed stale host artifact merging, extra/duplicate screenshot entries
and the diagnostic's accidental dependence on the newly updated SFM pin. The
recorded original diagnostic remains independently reproducible. [SFM PR #617](https://github.com/TeamDman/SuperFactoryManager/pull/617)
and [Facet PR #2](https://github.com/TeamDman/facet/pull/2) are drafts for review;
neither was merged into a busy integration branch. Discord and Kubernetes were
not deployed. Future worker work includes external Vox-control verification,
capture readiness, broker/input/output boundaries, storage quotas and stronger
sandboxing for arbitrary executable inputs.

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
- Installer: `platform/cli/sfm-propagate-changes/install.ps1` completed successfully with locked offline acquisition. Installed command reports `10967aefc`; SHA-256 `95095EB678494595B6B40C7E37A1B155F2AB17EA713931235A111035ED82D5EF`. Its source subtree `d3785ff480719c67f1574efa5bfede644e653d93` is identical at `d7e22e73e`. User install required: no. CI builds its own executable from each event revision.
- Dependency posture: mutable only for the explicitly authorized Vox Java update
  described above; all other project dependencies remain frozen.
- New developer/reference clones: none.
- Process preflight: no local game launch or process termination is planned; hosted workers own their test processes.
- Tool freshness was rechecked after diagnostic commit `e8318d2f0`: the installed
  version/hash and current Rust subtree still match the values above.
- Dependency declarations and lockfiles: only the five Vox source/hash fields
  described in Task 2 changed. The canonical source build generated the new JAR;
  the busy checkout's artifact cache was not overwritten.
- Original checkout: still clean on `1.19.2` at `707f53f4a` when rechecked after
  the candidate diagnostic was prepared.
- Cache rehydration: hosted runners acquired checked-in locked dependencies;
  diagnostics materialized only the exact pinned Facet commit in disposable
  source directories. Their candidate source is never a mod-build input.
- Process state: no local Minecraft instance was launched. The successful hosted
  jobs finished and own/clean up their workers and test processes. No task-owned
  local Minecraft, Cargo or helper process remains running.
- Exact manual graphics check: from the worktree root on a Linux Docker host,
  use the two commands under `containers/sfm/README.md` / "Run the independent
  graphics probe". Expect `GRAPHICS_PROBE_PASSED renderer=llvmpipe`.
- Exact full fixture commands: `docker build --build-arg
  SFM_SOURCE_REVISION="$(git rev-parse HEAD)" -f containers/sfm/Dockerfile
  -t sfm-ci:local .`, then `bash containers/sfm/smoke.sh sfm-ci:local
  build/container-smoke`. Use a fresh artifact directory on each run. Expect
  `passed: true`, three title captures, eight orbit captures, two successful
  raw JVM completion markers, and the stated restrictions in Docker inspection.
- Runtime scope: the experiment ran on GitHub-hosted Linux Docker; no local
  container engine was required or configured. Discord and Kubernetes
  remain design handoffs, not deployed services.
