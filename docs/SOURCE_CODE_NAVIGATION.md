# Source Code Navigation

This note captures how we want agents to locate source code (for base behaviours from Minecraft/Forge/NeoForge and others) while working on SFM, and how to reason about @MCVersionDependentBehaviour while minimizing code differences between branches.

## Goals

- Prefer searching already-unpacked source trees over re-extracting jars.
- Prefer locating the oldest supported branch's answer first, then compare forward.
- Prefer containing version drift inside small `@MCVersionDependentBehaviour` helpers instead of spreading `if version then ...` logic through tests or gameplay code.

## Golden Rule

When a behavior differs across Minecraft versions, start from the oldest SFM branch that still supports the feature.

For current SFM work, that usually means:

- Start in `D:\Repos\Minecraft\SFM\repos2\1.19.2`
- Compare with newer branches only to understand drift
- Put version-specific adaptation behind a helper method where possible

## Preferred Search Workflow

### 1. Try `teamy-mft` first

`teamy-mft` is the preferred full-disk search tool when it is available and correctly configured.
You MUST use the `--profile sfm` arguments when using `teamy-mft query`.

Example:

```pwsh
teamy-mft query --profile sfm "SummonCommand.java"
teamy-mft query --profile sfm "Summon .java$ 1.19.2" --limit 5
```

Why:

- It can find already-generated source trees across all worktrees very quickly.
- It avoids wasting time spelunking jars manually.
- It helps answer "where did NeoForge/Forge unpack this version's source?" without guessing directory layouts.

### 2. If `teamy-mft` fails, fall back to `rg`

Historically, `teamy-mft` has failed because the MFT cache for `C:` was missing:

```text
MFT file for drive C not found at expected path: G:\Programming\Caches\MFT_FILES\C.mft
```

That means agents should treat `teamy-mft` as preferred, not guaranteed.

However, there is an important nuance:

- the cache artifact may exist
- but ACL protection may still prevent the current sandboxed user from reading it

If `teamy-mft` reports a missing `.mft` file and the local machine is using protected machine caches, try:

```pwsh
teamy-mft protection disable
```

After disabling protection during this investigation, `teamy-mft query --profile sfm "GameTestHelper.java"` and `teamy-mft query --profile sfm "SummonCommand.java"` both started returning results successfully.

Notes:

- `protection disable` will solicit the user for UAC elevation
- `teamy-mft status` can help diagnose the health of the teamy-mft utility
- this is still preferable to manually extracting jars if the goal is just to locate already-generated source trees

Fallback pattern:

```pwsh
rg --files "D:\Repos\Minecraft\SFM\repos2\1.21.1\platform\minecraft\build" | rg "GameTestHelper\.java$|SummonCommand\.java$"
rg --files "D:\Repos\Minecraft\SFM\repos2\26.1.2\platform\minecraft\build" | rg "GameTestHelper\.java$|SummonCommand\.java$"
```

If an older branch does not currently have unpacked sources in its own `build` directory, search nearby historical worktrees or already-expanded source caches before extracting jars again.
