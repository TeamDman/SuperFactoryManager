# Release process

The following is a formalization of the steps involved in publishing a new release of Super Factory Manager.

This process is designed to catch the most obvious problems that may arise, ensuring no step is forgotten.

## Phase 0 - Make a Change

To make a release, something should have changed about the mod.

## Phase 1 - Release Preparation

These steps must be performed at the start of the release process.

1. Bring [known_issues.sfml](../platform/minecraft/src/main/resources/assets/sfm/template_programs/known_issues.sfml) up to date
3. Bump `mod_version` in [gradle.properties](../platform/minecraft/gradle.properties)
4. Ensure heading correctness in [changelog.sfml](../platform/minecraft/src/main/resources/assets/sfm/template_programs/changelog.sfml) (remove any indications of this being a pre-release)
5. Bring [thank_you.sfml](../platform/minecraft/src/main/resources/assets/sfm/template_programs/thank_you.sfml) up to date with the names of any new patrons
6. Commit changes to git

## Phase 2 - Produce Build

This phase often partially restarts due to complications and discoveries leading to additional modifications that must be included in the release.

1. Run `sfm-propagate-changes.exe merge` to ensure all MC versions have all the latest SFM code
2. Run `sfm-propagate-changes.exe gradle runData` to ensure all generated resources are up to date
3. Run `sfm-propagate-changes.exe status` to ensure all changes under [src/generated](../platform/minecraft/src/generated/) are committed
4. Run `sfm-propagate-changes.exe merge` to ensure merge stability after committing generated files; each branch must keep its own src/generated files during the merge; reject incoming
5. Run `sfm-propagate-changes.exe gradle runGameTestServer` to ensure all game tests are passing
6. Run `sfm-propagate-changes.exe gradle build` to build the jar and ensure all unit tests are passing
7. Run `sfm-propagate-changes.exe jar clean` to prepare the destination directory
8. Run `sfm-propagate-changes.exe jar collect` to collect all the built jar files in one location

## Phase 3 - Verification Preparation

This phase ensures that the built jar files behave as expected.

Historical anecdotes include builds being successful but with missing textures, missing translation entries, and other behaviour that is not obvious until testing outside of the IDE.

1. Run `sfm-propagate-changes.exe jar update-clients` to ensure each PrismMC instance has the latest jar file
2. Run `sfm-propagate-changes.exe jar update-servers` to ensure each dedicated server has the latest jar file
3. Run `sfm-propagate-changes.exe client launch` to open PrismMC
4. Run `sfm-propagate-changes.exe server launch` to run the dedicated servers

## Phase 4 - Verification Actualization

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

## Phase 5 - Publishing (WIP)

These steps finalize the release to make the built jar file available to people for download.

(WIP - The rest of this phase is from an older version of this document and needs to be revamped with new `sfm-propagate-changes.exe` commands)

```pwsh
26. Action: Tag
27. Action: Push all

28. For each version:
    29. CurseForge -> Upload file
"https://authors.curseforge.com/#/projects/306935/files/create"
    Environment=Server+Client
    Modloader=match mc version {
        ..1.20   -> Forge
        1.20.1   -> Forge+NeoForge
        1.20.2.. -> NeoForge
    }
    Java=match mc version {
        ..1.20.4 -> Java 17
        1.21.. -> Java 21
    }
    Minecraft=$version
    Changelog= <<
        ```
        $section from changelog.sfml
        ```
    >>

30. For each version:
    31. Modrinth -> Versions -> Drag n drop
"https://modrinth.com/mod/super-factory-manager/versions"
    Adjust populated version numbers
    Changelog=same as above


32. GitHub -> Draft a new release
"https://github.com/TeamDman/SuperFactoryManager/releases/new"
Choose a tag=latest
Target=latest
Release title=mod version
Description= <<
    ```
        $section from changelog.sfml
    ```
>>
Attach=latest jar for each mc version

33. Close GitHub milestone
34. Create new vNext milestone
35. Remove "Fixed awaiting release" label from issues
```

## Appendix

### Command Help

All commands have a `--help` behaviour that can be used to learn more.

```
❯ sfm-propagate-changes.exe gradle --help
sfm-propagate-changes.exe gradle

Run arbitrary gradle task(s) for each worktree in strict sequence

USAGE:
    sfm-propagate-changes.exe gradle [OPTIONS] <TASKS>

ARGUMENTS:
        <TASKS>
            Gradle tasks to run (for example: `runData`, `runGameTestServer`, `test`).

OPTIONS:
        --mc <STRING>
            Minecraft version filter expression for branch names (examples: `>=1.21.0`, `<1.20`, `=1.20.4`).
        --hide-logs
            If set, hide stdout of each gradle process while it runs.
        --continue-on-error
            If set, continue with later branches after a task failure.
```

```
sfm-propagate-changes.exe server launch

Launch tracked servers by running each `run.bat` and waiting for successful exit

USAGE:
    sfm-propagate-changes.exe server launch [OPTIONS]

OPTIONS:
        --mc <STRING>
            Minecraft version filter expression for tracked servers (examples: `>=1.21.0`, `<1.20`, `=1.20.4`).
```
