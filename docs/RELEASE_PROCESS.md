# Release process

The following is a formalization of the steps involved in publishing a new release of Super Factory Manager.

This process is designed to catch the most obvious problems that may arise, ensuring no step is forgotten.

Some steps may reveal complications leading to additional modifications that must be included in the release and therefore will necessitate jumping back to before already-executed steps, this is normal (albeit undesired).

## Phase 1 - Confirm Release Scope

To make a release, something should have changed about the mod.

1. Confirm that at least one gameplay-visible, user-facing, documentation, localization, or compatibility change is ready to release
2. Confirm that each release-worthy change is represented in [changelog.sfml](../platform/minecraft/src/main/resources/assets/sfm/template_programs/changelog.sfml)
3. Confirm that any fixed GitHub issues are either closed or labelled `implemented awaiting release`
4. Confirm that any relevant GitHub issues are associated with the milestone for this release

## Phase 2 - Update Known Issues

This file requires human judgement and must be checked deliberately before release automation begins.

1. Bring [known_issues.sfml](../platform/minecraft/src/main/resources/assets/sfm/template_programs/known_issues.sfml) up to date
    - Remove issues that were fixed by this release
    - Add unresolved release-relevant caveats that users should see in-game

## Phase 3 - Update Thank You

This file requires checking donor sources directly so release credits do not depend on stale local memory.

1. Open [thank_you.sfml](../platform/minecraft/src/main/resources/assets/sfm/template_programs/thank_you.sfml)
2. Visit each donor URL listed in the file
3. Bring [thank_you.sfml](../platform/minecraft/src/main/resources/assets/sfm/template_programs/thank_you.sfml) up to date with the names of any new donors

## Phase 4 - Verify Release Metadata

These checks must be completed before committing release preparation changes.

1. Bump `mod_version` in [gradle.properties](../platform/minecraft/gradle.properties)
    - Compare against existing release tags to confirm the version is the next intended release
2. Ensure heading correctness in [changelog.sfml](../platform/minecraft/src/main/resources/assets/sfm/template_programs/changelog.sfml)
    - The heading should match `mod_version`
    - Remove any indications of this being a pre-release
    - Assert that no TODO items remain

## Phase 5 - Commit And Propagate Release Preparation

1. Commit release preparation changes to git
2. Run `sfm-propagate-changes.exe git merge` to ensure all MC versions have all the latest SFM code
3. Run `sfm-propagate-changes.exe git status` to ensure every worktree is clean before continuing

## Phase 6 - Running Datagen

This phase handles ensuring generated sources are up-to-date.

1. Run `sfm-propagate-changes.exe run data --parallel --branch core` to ensure all generated resources are up to date for each MC version.
    - This was previously `sfm-propagate-changes.exe gradle run runData`.
2. Run `sfm-propagate-changes.exe git status` to inspect generated changes
    - If only generated resources changed under [src/generated](../platform/minecraft/src/generated/), the command will offer to auto-commit them
    - Review the proposed generated-only changes and accept the auto-commit prompt when they are expected
    - Do not manually stage generated files unless the auto-commit workflow is insufficient
3. Run `sfm-propagate-changes.exe git merge` to ensure merge stability after committing generated files; each branch must keep its own src/generated files during the merge; reject incoming

## Phase 7 - Running Gametests

This phase ensures that there is no unexpected behaviour in the mod.

1. Run `sfm-propagate-changes.exe run game-test-server --parallel --branch core` to ensure all game tests are passing
    - This was previously `sfm-propagate-changes.exe gradle run runGameTestServer`

## Phase 8 - Building Jarfiles

This phase produces the `.jar` files that users will add to their instance's `mods` directory

1. Run `sfm-propagate-changes.exe run test --parallel --branch core` to ensure all unit tests are passing for each MC version
2. Run `sfm-propagate-changes.exe jar build --parallel --branch core` to build the jar for each MC version
3. Run `sfm-propagate-changes.exe jar dir clean` to prepare the destination directory
4. Run `sfm-propagate-changes.exe jar collect` to collect all the built jar files in one location

## Phase 9 - Verification Preparation

This phase ensures that the built jar files behave as expected.

Historical anecdotes include builds being successful but with missing textures, missing translation entries, and other behaviour that is not obvious until testing outside of the IDE.

1. Run `sfm-propagate-changes.exe client set-instances-dir <path>` if the Prism Launcher instances directory has not been configured on this machine
2. Run `sfm-propagate-changes.exe client sync --branch core --loader pinned` to ensure each Prism Launcher verification instance exists, is tracked, uses the Gradle-inferred loader/JDK from the last clean-slate build plan, and has the latest jar file
    1. Optional: run `sfm-propagate-changes.exe loader list --branch core` to compare the pinned loader against Prism's recommended/latest metadata before intentionally testing with `--loader recommended` or `--loader latest`
3. Run `sfm-propagate-changes.exe jar update-servers` to ensure each tracked dedicated server has the latest jar file
4. Optional: run `sfm-propagate-changes.exe client open` to open Prism Launcher without launching a verification instance

## Phase 10 - Verification Actualization

The following steps must run for each MC version.

1. Run `sfm-propagate-changes.exe client launch --branch core` to issue Prism Launcher launch requests for the verification instances sequentially
2. In another terminal, run `sfm-propagate-changes.exe server launch --branch core` to run the dedicated servers sequentially
3. Launch version from PrismMC
4. Multiplayer -> join localhost
5. Build new setup from scratch to ensure core gameplay loop is functional
    1. Place a `sfm:manager` block
    2. Place a `minecraft:chest` block on the left and right of the manager
    3. Right click the manager block to open the GUI
    4. Place a `sfm:disk` item in the manager
    5. Click the `Reset` button to ensure the disk is empty
    6. Click `Examples > A Simple Program` to view the following example program:
        ```sfml
        EVERY 20 TICKS DO
            INPUT FROM a
            OUTPUT TO b
        END
        ```
    7. Save the program using the `Done` button
    8. Close the manager GUI
    9. Ensure a `sfm:labelgun` is in main hand
    10. Sneak-right-click the manager to update the label gun with the following [LabelPositionHolder](../platform/minecraft/src/main/java/ca/teamdman/sfm/common/label/LabelPositionHolder.java) data:
        ```item-data
        a: 0 positions
        b: 0 positions
        ```
    11. Sneak-scroll to select the `a` label
    12. Right-click the chest to the left of the manager to apply the `a` label to it in the label gun
    13. Sneak-scroll to select the `b` label
    14. Right-click the chest to the right of the manager to apply the `b` label to it in the label gun
    15. Right-click the manager block without sneaking to copy the LabelPositionHolder information from the label gun to the disk inside the manager
    16. Open the left chest GUI
    17. Place items in the left chest GUI
    18. Assert that the items disappear
    19. Close the GUI
    20. Open the right chest GUI
    21. Assert that the items that have arrived from the left chest
6. Validate changelog accuracy
    1. Run the `/sfm changelog` in the chat
    2. Assert that the headings are correct in the in-game view of [changelog.sfml](../platform/minecraft/src/main/resources/assets/sfm/template_programs/changelog.sfml)
    3. Assert that no TODO items remain
7. Run the `/stop` command in the chat, this will disconnect you from the server
8. Quit the game
9. GOTO Phase 8 step 1 for the next version to be tested, if any

## Phase 11 - Tagging

1. Run `sfm-propagate-changes.exe git merge`
2. Run `sfm-propagate-changes.exe git tag`
3. Run `sfm-propagate-changes.exe git push --tags`
4. Run `sfm-propagate-changes.exe git push`

## Phase 12 - Publishing to GitHub

This phase creates a GitHub release with the jar files uploaded as attachments.

1. Run GitHub release script from repo root
    ```pwsh
    pwsh -File ./platform/pwsh/github-release.ps1
    ```

## Phase 13 - Publishing to CurseForge

This phase makes the new builds available for download on CurseForge.

1. Run CurseForge metadata check command from repo root:
    ```pwsh
    sfm-propagate-changes.exe curseforge release check
    ```
2. Run CurseForge upload command from repo root:
    ```pwsh
    sfm-propagate-changes.exe curseforge release now
    ```

## Phase 14 - Publishing to Modrinth

1. Run Modrinth metadata check command from repo root:
    ```pwsh
    sfm-propagate-changes.exe modrinth release check
    ```
2. Run Modrinth upload command from repo root:
    ```pwsh
    sfm-propagate-changes.exe modrinth release now
    ```


## Phase 15 - Milestone Cleanup

This phase manages identifying the GitHub milestone for this release and ensuring that the related issues are closed.

Some issues remain open after being fixed; they are tagged with `implemented awaiting release` so that people who are still having the issue can easily find the issue.

We want to close these issues once the newest release is available.

1. Run milestone cleanup script from repo root
    ```pwsh
    pwsh -File ./platform/pwsh/milestone-cleanup.ps1
    ```

## Appendix

### Command Help

All commands have a `--help` behaviour that can be used to learn more.

```
❯ sfm-propagate-changes.exe gradle run --help
sfm-propagate-changes.exe gradle

Run arbitrary gradle task(s) for each worktree in strict sequence

USAGE:
    sfm-propagate-changes.exe gradle run [OPTIONS] <TASKS>

ARGUMENTS:
        <TASKS>
            Gradle tasks to run (for example: `runData`, `runGameTestServer`, `test`).

OPTIONS:
        --branch <STRING>
            Branch selector for worktrees. Defaults to all worktrees for legacy Gradle orchestration.
        --show-logs
            If set, stream gradle stdout/stderr to the console while tasks run.
        --continue-on-error
            If set, continue with later branches after a task failure.
```

```
sfm-propagate-changes.exe server launch

Launch tracked servers by running each `run.bat` and waiting for successful exit

USAGE:
    sfm-propagate-changes.exe server launch [OPTIONS]

OPTIONS:
        --branch <STRING>
            Branch selector used to choose tracked server Minecraft versions. Defaults to `core`.
```
