# Super Factory Manager (SFM)

The other branches' AGENTS.md files all point to this one, do not read those other agent files to save us some tokens.

We use worktrees to support multiple MC versions at the same time, see `git worktree list`

## The Golden Rule

> Work only on the oldest version/branch that needs attention; `sfm-propagate-changes.exe git merge` will handle updating the other branches.

Most work should occur in the 1.19.2 branch.
The `sfm-propagate-changes.exe git merge` command will handle updating the other branches.
Feature worktrees that are not named like Minecraft versions are intentionally skipped by propagation.

When merging, the default behaviour should be "keep existing" and subsequently meticulously graft the essence of the change over, we NEVER want to accidentally clobber changes that were present in the code for supporting the newer MC version. Absolute caution must be exercised when modifying build.gradle especially.

## !!!🚫🐘🚫 NO GRADLE 🚫🐘🚫!!!

Instead of `./gradlew build`, use `sfm-propagate-changes run compile`
Instead of `./gradlew runClient_teamy`, use `sfm-propagate-changes run client`
Instead of `./gradlew runDatagen`, use `sfm-propagate-changes run data`
Instead of `./gradlew runGameTestServer`, use `sfm-propagate-changes game-test run-server`
Instead of `./gradlew test`, use `sfm-propagate-changes test run`

The `sfm-propagate-changes` source code is [here](../platform/cli/sfm-propagate-changes/src/cli/cli.rs)

[There is a script to update the EXE in PATH after changes.](../platform/cli/sfm-propagate-changes/install.ps1).

After making changes to rust code, run [`check-all.ps1`](../platform/cli/sfm-propagate-changes/check-all.ps1).

The codex harness buffers stdio by default, so if a `sfm-propagate-changes.exe` command is hanging without exiting you can use the `--log-file` argument to write to a file that can be inspected while the program is running to diagnose problems.

When the CLI reports `os_error=5` while opening a lock under the user-level artifact cache, first treat it as a Codex sandbox access-denial case. The sandbox may be unable to open the lock even when no other process owns it; rerun the command with normal Windows cache access or outside the sandbox before investigating stale locks. A real concurrent process can produce the same Windows error, so the CLI still retries briefly and reports both possibilities.

## Changelog

The SFM changelog is [here](../platform/minecraft/src/main/resources/assets/sfm/template_programs/changelog.sfml) and MUST be updated after performing changes that are observable during gameplay.
Always update the changelog on 1.19.2 so we may `sfm-propagate-changes.exe git merge` to update all versions.

We can use `sfm-propagate-changes.exe git status` to view a summary of all of the worktrees instead of manually running `git status` in each one.

## Writing Java

When we encounter a part of code that requires a difference across the branches, we should introduce adapter methods annotated with `@MCVersionDependentBehaviour` to minimize and make obvious the surface area where the code is forced to change to accommodate the differences between Minecraft versions.

Run `cargo run -- audit --branch core --version-surfaces` before/after propagating version work. It warns when a later branch has a non-1.19.2 CLI commit or differing CLI source tree, and reports Java diff hunks that are not bounded by `@MCVersionDependentBehaviour`; use the report to move version-specific code behind explicit adapters.

We have source code for Minecraft and for Forge/NeoForge available to us:

- `sfm-propagate-changes.exe dependency source acquire minecraft --provider platform-pipeline --branch 1.19.2`
- `teamy-mft query --profile sfm "1.19.2 GameTestHelper.java"`
- `rg --files "D:\Repos\Minecraft\SFM\repos2\1.21.1\platform\minecraft\build" | rg "GameTestHelper\.java$|SummonCommand\.java$"`
- `ls G:\Programming\Repos\Minecraft` contains many toolchain-relevant cloned repos such as MinecraftForge, NeoForged, and supporting elements.
