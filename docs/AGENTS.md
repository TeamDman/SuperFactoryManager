# Super Factory Manager (SFM)

The other branches' AGENTS.md files point to this one. Do not read those copies.

## Current source workflow

The current checkout uses one authored
[`core-liquid-template`](../platform/minecraft/core-liquid-template/) tree and an
explicit [`projections.json`](../platform/minecraft/projections.json) catalog.
Generated Java and resources belong to a selected projection under
`platform/minecraft/projections/<projection-key>/`. Edit the core templates and
selection rules, then generate and review the affected projections. Do not create
or restore a flat `platform/minecraft/src` tree.

From the repository root:

```powershell
sfm-propagate-changes source list --repo-root .
sfm-propagate-changes source show --repo-root . --projection sfm-dev/mc-1.19.2
sfm-propagate-changes source project dry-run --repo-root . --projection sfm-dev/mc-1.19.2
sfm-propagate-changes source project sync --repo-root . --projection sfm-dev/mc-1.19.2
sfm-propagate-changes source project check --repo-root . --projection sfm-dev/mc-1.19.2
```

Choose the projection for the task; `sfm-dev/mc-1.19.2` is an example, not an
implicit default. A Minecraft version alone does not identify a projection's
features or release environment. Review contributor edits before synchronizing
tracked output. Use a scoped dirty override only when the intended replacement
has been reviewed. Follow the
[contributor guide](source-projection-contributor-guide.md) for authoring,
projection selection and contributor-change hydration.

Before creating a worktree or choosing toolchain-cache isolation or sharing, read
the [worktree cache decision guide](worktree-toolchain-cache.md). Creating a
worktree or cache link requires Teamy's approval.

## Native builds and runtime commands

Do not execute Gradle for automated repository work. Use the Rust CLI with an
explicit projection:

```powershell
sfm-propagate-changes run compile --repo-root . --projection sfm-dev/mc-1.19.2
sfm-propagate-changes jar build --repo-root . --projection sfm-dev/mc-1.19.2
sfm-propagate-changes test run --repo-root . --projection sfm-dev/mc-1.19.2
sfm-propagate-changes game-test run-server --repo-root . --projection sfm-dev/mc-1.19.2 --filter 'sfm:*'
sfm-propagate-changes run client --repo-root . --projection sfm-dev/mc-1.19.2
sfm-propagate-changes run data --repo-root . --projection sfm-dev/mc-1.19.2
```

Build, test and launch are separate operations. Run only the operations authorized
for the task. Successful rendering or compilation does not prove gameplay or
publication readiness. Never publish, upload, push or tag without the user's
authorization for that operation.

The `sfm-propagate-changes` source is
[here](../platform/cli/sfm-propagate-changes/src/cli/cli.rs).
[The installer](../platform/cli/sfm-propagate-changes/install.ps1) updates the EXE
on PATH; do not assume an installed binary includes current uncommitted source.
After Rust changes, run
[`check-all.ps1`](../platform/cli/sfm-propagate-changes/check-all.ps1).

The Codex harness buffers stdio by default. If a CLI command hangs, use
`--log-file` to write progress to a chosen file. The log obeys `RUST_LOG`;
`RUST_LOG=warn` can leave healthy INFO progress invisible. Use `--log-filter info`
with `--log-file` when progress evidence is required. Normal automatic logs belong
to the selected application home, not the invocation directory.

When the CLI reports `os_error=5` opening a user-cache lock, first check for a
sandbox access denial. Rerun with normal Windows cache access or outside the
sandbox before assuming the lock is stale. A concurrent process can cause the
same error; preserve lock diagnostics and ownership information.

## Goal execution and testing readiness

For a prepared Codex cloud image, use the repository-owned
[cloud setup and offline publication gate](codex-cloud-environment.md). Its
scope and lock hashes must pass inside a networkless candidate image before
publication.

Read the repository-wide
[goal execution and testing readiness guidelines](tasks/goal%20execution%20and%20testing%20readiness%20guidelines.md)
before starting or completing an autonomous implementation goal. It requires
tooling freshness, installer responsibility, bounded process cleanup, lock
diagnostics and a copyable manual-testing handoff. Goal authority permits
stopping and restarting only processes proven to belong to the SFM/Minecraft/
toolchain test scope. It does not permit broad process killing, repository reset,
recursive deletion or unrelated-application disruption.

## Changelog

The authored changelog is
[`core-liquid-template/src/main/resources/assets/sfm/template_programs/changelog.sfml`](../platform/minecraft/core-liquid-template/src/main/resources/assets/sfm/template_programs/changelog.sfml).
Update it for gameplay-observable changes. Put version- or feature-specific
entries under the appropriate Liquid conditions. Generate the affected
projections and inspect their rendered changelogs; do not edit only generated
copies or revive the flat source tree.

## Writing Java

Express Minecraft API differences with Liquid target/feature conditions in the
core. Keep version-specific behavior in small adapters when that improves the
code. Verify both enabled and disabled contexts affected by a condition, including
source membership and registrations. Use `source oracle` for pinned comparisons;
text parity does not replace compilation or runtime evidence.

Minecraft and Forge/NeoForge source discovery is available through the CLI's
`dependency source` commands. Inspect their help and select the intended project
inputs instead of relying on an arbitrary branch's local build directory.

## Historical branch checkouts

Version branches and pre-consolidation checkouts retain their historical source
layout. Only when explicitly working on those checkouts, start with the oldest
version that needs the change and use `sfm-propagate-changes git merge` for branch
propagation. Feature worktrees whose names are not Minecraft versions are skipped.
The default merge behavior is to keep existing changes and graft the intended
behavior carefully, especially around version-specific build configuration.

These branch-propagation rules do not select inputs for the current catalog
checkout. Historical snapshot/preset operations are under `source legacy`.
Do not recreate retired trees in the current checkout to make a legacy command
work.
