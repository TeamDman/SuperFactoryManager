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
graphics run established software OpenGL 4.5 under the restrictions; the complete
game run is a separate acceptance check.

Implementation progress and observed blockers are recorded in
[the experiment plan](../docs/tasks/ci%20and%20container%20puppet%20experiment%20plan.md).
Only 1.19.2 is in this workflow's acceptance scope. Add version-specific
validation before propagating the workflow to other Minecraft branches.
