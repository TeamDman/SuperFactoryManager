# SFM live-game control CLI

`sfm.exe` discovers running Super Factory Manager Minecraft clients and invokes
their registered, context-aware client actions over authenticated loopback Vox.

```powershell
sfm instance list
sfm invoke sfm:panel/open sfm:size_display
sfm spatial coverage run document focused sfm:strict_java_navigation sfm:auto_1_through_8 0 100000 auto
```

Use `install.ps1` to install the CLI independently from
`sfm-propagate-changes.exe`.
