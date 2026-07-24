# Track 3 file explorer visual QA

This is Track 3-owned evidence. It is not the canonical in-game review workspace
plan. The captures use the repository's existing `@SFMGamePuppet` discovery,
client harness, native render-target capture, caption compositor, artifact
manifest, and CLI launch surface.

## Environment

- Date: 2026-07-20
- Branch: `feat/1.19.2/file-explorer`
- Baseline implementation commit: `360c246d055cc2f13493d24f745ea528c1ff99a0`
- Minecraft: 1.19.2
- Forge userdev: 43.4.0
- Host: Windows
- CLI epoch: E1
- CLI: `G:\Programming\Caches\CARGO_HOME\bin\sfm-propagate-changes.exe`
- CLI version: `0.1.1 (rev 706933b05, built 2026-07-20 18:54:36 -04:00)`
- CLI SHA-256: `391D762D09228F1B19C686C29CB25000CAC0F168CA6FE4C300A0B1DCA73CDD81`
- Puppet: `sfm:title_screen_file_explorer`
- Capture profile: native main render target, HUD hidden, transient overlays not
  globally cleared

The puppet waits for the loading overlay and title fade, opens the existing
command palette, and submits this real Brigadier command:

```text
sfm action invoke sfm:developer/open_file_explorer
```

It then captures the initial selection, navigates the explorer through its key
handling to expand both directories and select `ReviewWorkspace.java`, and
supplies deterministic loading, empty, and error snapshots through the same
immutable snapshot boundary intended for asynchronous source providers.

## Large viewport run

Exact invocation from the assigned worktree:

```powershell
sfm-propagate-changes.exe puppet run sfm:title_screen_file_explorer --branch feat/1.19.2/file-explorer --width 1280 --height 720 --wait-for-build-lock
```

The command exited successfully. Caption composition increases each PNG's
height beyond the 720-pixel source frame.

Artifact directory:

```text
D:\Repos\Minecraft\SFM\worktrees\1.19.2-file-explorer\platform\minecraft\build\sfm-toolchain\artifacts\game-test-preview\runs\sfm-title_screen_file_explorer-20260720-205351-471\title_screen_file_explorer
```

Captures:

- `figure_01_ready.png` — collapsed tree with visible keyboard selection.
- `figure_02_expanded-file-types.png` — expanded `.sfml`, `.sfmp`, `.java`,
  `.g4`, and `.json` examples plus no-extension and unknown fallbacks.
- `figure_03_loading.png` — loading message in the status region.
- `figure_04_empty.png` — successful-but-empty source message.
- `figure_05_error.png` — source failure rendered as red diagnostic text.

## Compact supported viewport run

Exact invocation from the assigned worktree:

```powershell
sfm-propagate-changes.exe puppet run sfm:title_screen_file_explorer --branch feat/1.19.2/file-explorer --width 640 --height 480 --wait-for-build-lock
```

The command exited successfully. At Minecraft's automatic GUI scale this
exercises the 320 logical-pixel compact breakpoint while remaining above the
explorer's documented 180x120 minimum.

Artifact directory:

```text
D:\Repos\Minecraft\SFM\worktrees\1.19.2-file-explorer\platform\minecraft\build\sfm-toolchain\artifacts\game-test-preview\runs\sfm-title_screen_file_explorer-20260720-205842-503\title_screen_file_explorer
```

Captures:

- `figure_01_ready.png`
- `figure_02_expanded-file-types.png`
- `figure_03_loading.png`
- `figure_04_empty.png`
- `figure_05_error.png`

The latest machine-readable manifest is:

```text
D:\Repos\Minecraft\SFM\worktrees\1.19.2-file-explorer\platform\minecraft\build\sfm-toolchain\artifacts\game-test-preview\preview-manifest.json
```

It records the five compact captures at 640x556 after caption composition,
their BLAKE3 hashes, `SFMFileExplorerScreen` as the active screen, and the
declared 640x480 viewport.

## Observations

- The large viewport preserves generous empty list space and displays the
  source name. The compact layout removes that secondary header line while
  retaining the title, rows, selection, and status.
- The blue selection background remains clear behind yellow directory text and
  orange Java text at both sizes.
- Every style includes a textual icon and kind: for example `[SFM] ... (SFM
  program)`, `[J] ... (Java source)`, and `[G4] ... (ANTLR grammar)`. The visual
  does not rely on colour alone.
- Directory hierarchy and disclosure state remain understandable using
  indentation and `>`/`v` markers.
- Loading, empty, and error messages remain legible at both sizes. Error text is
  additionally red, but its wording carries the meaning.
- No row or status text visibly escapes the bordered content region in either
  viewport.

## Isolated Minecraft instance source

The resolved puppet instance directory is:

```text
D:\Repos\Minecraft\SFM\worktrees\1.19.2-file-explorer\platform\minecraft\runGameTestPreview
```

The `SFMPathFileExplorerSource` adapter is rooted only at that explicit
directory. It reads metadata without reading file contents, does not follow
links, caps traversal to depth 3, 256 total entries, and 64 children per
directory, and excludes `screenshots`, `logs`, `saves`, crash/download
directories, and known account/server-history filenames. The captioned artifact
root is elsewhere under `build/sfm-toolchain/artifacts`, and the raw
`screenshots` child is excluded, preventing capture recursion.

Exact invocation:

```powershell
sfm-propagate-changes.exe puppet run sfm:title_screen_instance_file_explorer --branch feat/1.19.2/file-explorer --width 1280 --height 720 --wait-for-build-lock
```

The command exited successfully. Captures:

```text
D:\Repos\Minecraft\SFM\worktrees\1.19.2-file-explorer\platform\minecraft\build\sfm-toolchain\artifacts\game-test-preview\runs\sfm-title_screen_instance_file_explorer-20260720-210927-345\title_screen_instance_file_explorer\figure_01_instance-root.png
D:\Repos\Minecraft\SFM\worktrees\1.19.2-file-explorer\platform\minecraft\build\sfm-toolchain\artifacts\game-test-preview\runs\sfm-title_screen_instance_file_explorer-20260720-210927-345\title_screen_instance_file_explorer\figure_02_instance-expanded.png
```

Visual inspection confirmed that the screen labels the source as `Minecraft
instance / runGameTestPreview`, shows the selected root, and expands to bounded
real entries such as `config`, `defaultconfigs`, `mods`, `options.txt`, and
`resourcepacks`. Excluded screenshot/log/save locations do not appear.

## Deterministic 1,002-file hierarchy

The `sfm:title_screen_large_file_explorer` puppet supplies the actual explorer
with exactly `0000.txt` through `1001.txt`, grouped as:

```text
0000-0999/
  0000.txt ... 0999.txt
1000-1999/
  1000.txt
  1001.txt
```

Exact successful invocation:

```powershell
sfm-propagate-changes.exe puppet run sfm:title_screen_large_file_explorer --branch feat/1.19.2/file-explorer --width 1280 --height 720 --wait-for-build-lock
```

Final visually inspected captures:

```text
D:\Repos\Minecraft\SFM\worktrees\1.19.2-file-explorer\platform\minecraft\build\sfm-toolchain\artifacts\game-test-preview\runs\sfm-title_screen_large_file_explorer-20260720-211326-162\title_screen_large_file_explorer\figure_01_thousand-groups.png
D:\Repos\Minecraft\SFM\worktrees\1.19.2-file-explorer\platform\minecraft\build\sfm-toolchain\artifacts\game-test-preview\runs\sfm-title_screen_large_file_explorer-20260720-211326-162\title_screen_large_file_explorer\figure_02_first-entry.png
D:\Repos\Minecraft\SFM\worktrees\1.19.2-file-explorer\platform\minecraft\build\sfm-toolchain\artifacts\game-test-preview\runs\sfm-title_screen_large_file_explorer-20260720-211326-162\title_screen_large_file_explorer\figure_03_thousand-boundary.png
```

Figure 1 visibly contains both collapsed group nodes. Figure 2 expands the first
group with `0000.txt` selected. Figure 3 demonstrates virtual scrolling across
the directory boundary: `0999.txt`, the expanded `1000-1999` node, `1000.txt`,
and selected `1001.txt` are visible together. Rendering remains bounded to the
visible rows even though the model's flattened projection contains 1,004 nodes.

An initial attempt to run both new puppet IDs in one comma-separated invocation
was rejected during CLI artifact assembly with `Preview screenshots reported
duplicate figure number 2`. Each puppet numbers its own figures from one, while
this CLI epoch incorrectly treats those numbers as globally unique. Separate
invocations are the reliable existing workflow; no CLI code was changed in this
Java exploration track.

## ItemStack icon and scheme follow-up

The current registry deliberately proved deterministic extension matching with
ASCII markers such as `[J]`, `[CFG]`, and `[DIR]`. Supersede those markers with
Minecraft-native `ItemStack` icons as specified by the
[in-game theming, item icons, and color inputs plan](in-game%20theming%20item%20icons%20and%20color%20inputs%20plan.md).

The file explorer remains responsible for path/type classification; the theme
resolves that semantic presentation to an item registry id, text style, and
colour. Preserve longest-suffix matching and cover directories, `.sfml`,
`.java`, `.json`, `.toml`, `.properties`, `.md`, `.txt`, `.tar.gz`, `.gz`,
extensionless files, and unknown files. A missing modded item must fall back to
a shipped vanilla item and accessible text rather than leaving an empty row.

Add a puppet using the falsified hierarchy that shows enough file types at once
to compare the icons, then switch to a user-defined icon scheme without
restarting the client.

## Limitations and follow-up

- The source is the bounded in-memory fixture. These captures do not validate a
  host filesystem, mounted disk, drag-and-drop, symlink policy, or mutation.
- Loading, empty, and error states are deterministic injected snapshots; they
  validate rendering and state transitions, not asynchronous timing.
- The puppet proves keyboard focus/selection. It does not capture a mouse
  pointer, double-click open intent, scrolling with more than one viewport of
  entries, or an editor opening from the emitted intent.
- Narration text is covered by unit assertions but is not audible in a PNG.
- Vanilla automatic GUI scaling prevents a normal full-screen surface from
  becoming much narrower than 320 logical pixels. The pure layout tests cover
  the documented 180x120 minimum and below-minimum safe geometry; a future
  multiplexer capture should supply a smaller panel rectangle directly.
- Generated screenshots live under the ignored build artifact root and are not
  committed. Cleaning the toolchain build directory removes them; rerunning the
  exact commands creates a new timestamped run directory.
