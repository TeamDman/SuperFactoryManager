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
