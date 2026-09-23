# SFM 1.19.2 verification

`workflows/ci.yml` verifies the checked-out 1.19.2 source with the repository's
Rust toolchain. Its three jobs run independently:

| Job | Passing evidence |
| --- | --- |
| Container software graphics and isolation | Xvfb/Mesa llvmpipe with the asserted container restrictions |
| Compile, test and package | Current-source CLI, all Java source sets, JUnit success and a distributable mod JAR |
| Isolated Minecraft client | A fresh offline puppet run with three title captures and eight world captures |

The delivery output is a downloadable Actions artifact named
`sfm-1.19.2-<commit>`. Build, graphics and game evidence have separate artifacts,
including diagnostics from failed jobs. Retention is 14 days. The workflow uses
read-only repository permissions and needs no mod publishing or Discord secrets.

`workflows/windows-ci.yml` runs the same canonical compile, JUnit and JAR checks
on Windows. Its mod artifact is named `sfm-1.19.2-windows-<commit>`.
`workflows/vox-diagnostic.yml` is a separate dependency investigation, triggered
only by its diagnostic branch or a manual run. It does not supply artifacts to
the SFM build.

## Observed experiment results

The [final Linux/Docker PR run](https://github.com/TeamDman/SuperFactoryManager/actions/runs/35481820090)
passed all three jobs at source `d7e22e73e`, tested as PR merge revision
`77e42edb0469ae9ca71b19b5b677c12cf245e79e`. Linux passed 2,075 Java tests with
zero failures, one Windows-only skip and six existing opt-in tests aborted by
their prerequisites. Its packaged mod contains the new Vox JAR with the exact
expected hash. The fresh offline container completed both puppets and all 11
PNG files decoded successfully. The [Windows PR run](https://github.com/TeamDman/SuperFactoryManager/actions/runs/35481820091)
also passed compilation, 2,076 Java tests and packaging, with zero failures and
the same six opt-in tests aborted by their prerequisites. Both platforms embed
identical Vox JAR bytes; the complete mod archives are not byte-identical.

| Evidence | Result |
| --- | --- |
| [First feature-branch run](https://github.com/TeamDman/SuperFactoryManager/actions/runs/35460447959) | Push trigger, Linux CLI and restricted software graphics passed; dependency preparation failed |
| [Second Linux/Docker run](https://github.com/TeamDman/SuperFactoryManager/actions/runs/35461041230) | UTF-8 container compilation fixed; both builds stopped in the pinned Vox Java test; graphics passed again |
| [Focused Vox diagnostic](https://github.com/TeamDman/SuperFactoryManager/actions/runs/35461627486) | Captured `message for unknown channel 1:1` before the connection closes |
| [Vox candidate comparison](https://github.com/TeamDman/SuperFactoryManager/actions/runs/35462115154) | Candidate passed the original test and 19 reduced cases; unchanged baseline also passed on this run, confirming the full-test failure is timing-sensitive |
| [Reduced Linux reproduction](https://github.com/TeamDman/SuperFactoryManager/actions/runs/35462297128) | Identical credit passes before sender Close and reproduces the exact unknown-channel failure after Close in unchanged pinned code |
| [First Windows build](https://github.com/TeamDman/SuperFactoryManager/actions/runs/35461257226) | Vox suite and deterministic JAR passed; a global Java-options banner incorrectly failed the dependency check |
| [Third Linux/Docker run](https://github.com/TeamDman/SuperFactoryManager/actions/runs/35462847488) | Linux compiled SFM then found Windows-specific JUnit fixtures; Docker built the mod and completed both puppets, but the verifier read CLI progress instead of the raw game log |
| [Second Windows run](https://github.com/TeamDman/SuperFactoryManager/actions/runs/35462847520) | Mod compilation passed; two canonical replay tests found Git's CRLF conversion of their byte-exact JSON fixture |
| [Final Linux/Docker PR run](https://github.com/TeamDman/SuperFactoryManager/actions/runs/35481820090) | All jobs passed: native compilation/JUnit/package, software graphics and the fresh restricted offline game |
| [Final Windows PR run](https://github.com/TeamDman/SuperFactoryManager/actions/runs/35481820091) | Compilation, all enabled Java tests, packaging and dependency checks passed; verified mod uploaded |

With the user's authorization, SFM now pins `org.facet:vox-java:0.10.0-rc.5`
to Facet revision `4a079ac1c8a8bb8a914811ef55945bc1d9a9fef3`, published on
`teamy/vox-java-late-credit` in [Facet PR #2](https://github.com/TeamDman/facet/pull/2).
Its full canonical package recipe passed locally, including 19 regression cases,
with content hash `blake3:2be34a7d38bbd4a455d2a933c856c9462630f47a`.
All other project dependencies remain unchanged. Source tests and artifact hashes
are enforced. The [diagnostic](../containers/sfm/vox-diagnostic/README.md)
retains the original pinned reproduction and candidate comparison.

## Worktrees and feature branches

A worktree is a local checkout. GitHub receives its branch and commits through
an ordinary push; the local directory does not affect Actions.

This experiment triggers on pushes to `1.19.2` and
`ci/1.19.2-container-puppet`, and on pull requests targeting `1.19.2`. The
initial experiment triggered successfully at `f7dc28338` before any merge into
the default branch. GitHub's PR checkout is a merge revision; the workflow
creates a local `sfm-ci-checkout` branch at that same revision so the SFM CLI's
worktree selector can find it.

Use a push for the first experiment. The manual workflow button depends on
workflow discovery on the default branch; a feature-only workflow does not need
that button to receive push events.
[GitHub workflow events](https://docs.github.com/en/actions/reference/workflows-and-actions/events-that-trigger-workflows),
[manual runs](https://docs.github.com/en/actions/how-tos/manage-workflow-runs/manually-run-a-workflow).

```bash
git push origin ci/1.19.2-container-puppet
gh run list --repo TeamDman/SuperFactoryManager --branch ci/1.19.2-container-puppet
gh run view <run-id> --repo TeamDman/SuperFactoryManager
gh run download <run-id> --repo TeamDman/SuperFactoryManager --dir build/ci-download
```

## Reproduce the container experiment

See [the container guide](../containers/sfm/README.md) for exact Docker commands,
artifact checks and the Discord/Kubernetes deployment boundaries. The first
graphics run established software OpenGL 4.5 under the restrictions; the final
PR run also verified the complete game with networking disabled.

Implementation progress and observed blockers are recorded in
[the experiment plan](../docs/tasks/ci%20and%20container%20puppet%20experiment%20plan.md).
Only 1.19.2 is in this workflow's acceptance scope. Add version-specific
validation before propagating the workflow to other Minecraft branches.
