# Release process

The following process is designed to catch the most obvious problems that may arise from creating a new release.

```pwsh // pwsh is used for syntax highlighting, this is not a script.
01. Manual: Update known_issues.sfml
02. Manual: Update GitHub milestone name
03. Manual: Bump `mod_version` in gradle.properties
04. Manual: Update heading in changelog.sfml
05. Manual: Update thank_you.sfml
06. Manual: Commit bump
07. Action: `sfm-propagate-changes.exe merge`
08. Action: `sfm-propagate-changes.exe gradle runData`
08. Action: `sfm-propagate-changes.exe status` # will observe any changes under src\generated and will prompt you to auto-commit
09. Action: `sfm-propagate-changes.exe merge` # we want each branch to keep its own src/generated files during the merge; reject incoming
10. Action: `sfm-propagate-changes.exe gradle runGameTestServer`
11. Action: `sfm-propagate-changes.exe build`
12. Action: `sfm-propagate-changes.exe jar clean`
13. Action: `sfm-propagate-changes.exe jar collect`
14. Action: `sfm-propagate-changes.exe jar update-clients`
15. Action: `sfm-propagate-changes.exe jar update-servers`
16. Action: Launch PrismMC
17. Action: Launch test server

18. For each version:
    19. Launch version from PrismMC
    20. Multiplayer -> join localhost
    21. Break previous setup
    22. Build new setup from scratch -- ensure core gameplay loop is always tested
    23. Validate changelog accuracy
    24. /stop
    25. Quit game

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