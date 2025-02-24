# Release process

The following process is designed to catch the most obvious problems that may arise from creating a new release.

```pwsh

.\act.ps1
Manual: Bump `mod_version` in gradle.properties
Manual: Remove "-pre" heading from changelog.sfml
Manual: Update thank_you.sfml
Manual: Commit bump
Action: Propagate changes
Action: Run gameTestServer for all versions
Action: runData for all versions
Action: Build
Action: Wipe jars summary dir
Action: Collect jars
Action: Update PrismMC test instances to use latest build output
Action: Update test servers to latest build output
Action: Launch PrismMC
Action: Launch test server

for each version:
    Launch version from PrismMC
    Multiplayer -> join localhost
    Break previous setup
    Build new setup from scratch -- ensure core gameplay loop is always tested
    Validate changelog accuracy
    /stop
    Quit game

Action: Tag
Action: Push all

For each version:
    CurseForge -> Upload file
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

For each version:
    Modrinth -> Versions -> Drag n drop
"https://modrinth.com/mod/super-factory-manager/versions"
    Adjust populated version numbers
    Changelog=same as above


GitHub -> Draft a new release
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
```