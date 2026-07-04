# Release process

The following is a formalization of the steps involved in publishing a new release of Super Factory Manager.

This process is designed to catch the most obvious problems that may arise, ensuring no step is forgotten.

Some steps may reveal complications leading to additional modifications that must be included in the release and therefore will necessitate jumping back to before already-executed steps, this is normal (albeit undesired).

## Phase 0 - Make a Change

To make a release, something should have changed about the mod.

## Phase 1 - Release Preparation

These steps must be performed at the start of the release process.

1. Bring [known_issues.sfml](../platform/minecraft/src/main/resources/assets/sfm/template_programs/known_issues.sfml) up to date
2. Bump `mod_version` in [gradle.properties](../platform/minecraft/gradle.properties)
3. Ensure heading correctness in [changelog.sfml](../platform/minecraft/src/main/resources/assets/sfm/template_programs/changelog.sfml) (remove any indications of this being a pre-release)
4. Bring [thank_you.sfml](../platform/minecraft/src/main/resources/assets/sfm/template_programs/thank_you.sfml) up to date with the names of any new patrons
5. Commit changes to git
6. Run `sfm-propagate-changes.exe git merge` to ensure all MC versions have all the latest SFM code

## Phase 2 - Running Datagen

This phase handles ensuring generated sources are up-to-date.

1. Run `sfm-propagate-changes.exe run data --parallel` to ensure all generated resources are up to date for each MC version.
    - This was previously `sfm-propagate-changes.exe gradle run runData`.
2. Run `sfm-propagate-changes.exe git status` to ensure all changes under [src/generated](../platform/minecraft/src/generated/) are committed
3. Run `sfm-propagate-changes.exe git merge` to ensure merge stability after committing generated files; each branch must keep its own src/generated files during the merge; reject incoming

## Phase 3 - Running Gametests

This phase ensures that there is no unexpected behaviour in the mod.

1. Run `sfm-propagate-changes.exe run game-test-server --parallel` to ensure all game tests are passing
    - This was previously `sfm-propagate-changes.exe gradle run runGameTestServer`

## Phase 4 - Building Jarfiles

This phase produces the `.jar` files that users will add to their instance's `mods` directory

1. Run `sfm-propagate-changes.exe gradle run build` to build the jar and ensure all unit tests are passing for each MC version
2. Run `sfm-propagate-changes.exe jar dir clean` to prepare the destination directory
3. Run `sfm-propagate-changes.exe jar collect` to collect all the built jar files in one location

## Phase 5 - Verification Preparation

This phase ensures that the built jar files behave as expected.

Historical anecdotes include builds being successful but with missing textures, missing translation entries, and other behaviour that is not obvious until testing outside of the IDE.

1. Run `sfm-propagate-changes.exe jar update-clients` to ensure each PrismMC instance has the latest jar file
2. Run `sfm-propagate-changes.exe jar update-servers` to ensure each dedicated server has the latest jar file
3. Run `sfm-propagate-changes.exe client launch` to open PrismMC
4. Run `sfm-propagate-changes.exe server launch` to run the dedicated servers

## Phase 6 - Verification Actualization

The following steps must run for each MC version.

1. Launch version from PrismMC
2. Multiplayer -> join localhost
4. Build new setup from scratch to ensure core gameplay loop is functional
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
5. Validate changelog accuracy
    1. Run the `/sfm changelog` in the chat
    2. Assert that the headings are correct in the in-game view of [changelog.sfml](../platform/minecraft/src/main/resources/assets/sfm/template_programs/changelog.sfml)
    3. Assert that no TODO items remain
6. Run the `/stop` command in the chat, this will disconnect you from the server
7. Quit the game
8. GOTO Phase 4 step 1 for the next version to be tested, if any

## Phase 7 - Tagging

1. Run `sfm-propagate-changes.exe git merge`
2. Run `sfm-propagate-changes.exe git tag`
3. Run `sfm-propagate-changes.exe git push --tags`
4. Run `sfm-propagate-changes.exe git push`

## Phase 8 - Publishing to GitHub

This phase creates a GitHub release with the jar files uploaded as attachments.

1. Run GitHub release script from repo root
    ```pwsh
    pwsh -File ./platform/pwsh/github-release.ps1
    ```

## Phase 9 - Publishing to CurseForge

This phase makes the new builds available for download on CurseForge.

1. Run CurseForge metadata check command from repo root:
    ```pwsh
    sfm-propagate-changes.exe curseforge release check
    ```
2. Run CurseForge upload command from repo root:
    ```pwsh
    sfm-propagate-changes.exe curseforge release now
    ```

## Phase 10 - Publishing to Modrinth

1. Run Modrinth metadata check command from repo root:
    ```pwsh
    sfm-propagate-changes.exe modrinth release check
    ```
2. Run Modrinth upload command from repo root:
    ```pwsh
    sfm-propagate-changes.exe modrinth release now
    ```


## Phase 11 - Milestone Cleanup

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
