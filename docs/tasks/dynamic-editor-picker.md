# Dynamic editor selection

The manager and editor settings open a searchable, paginated picker populated
from `SFMTextEditors.registry().keys()`. The chosen registry ID remains the
persisted `preferredEditor` value. This is enabled by `dynamic_editor_picker`
in the 1.19.2 and 1.19.4 development projections; historical release projections
retain their prior behavior. The primary target is the oldest supported source,
1.19.2. Forward-porting the screen's rendering/input adapters to newer targets
is outside this checkpoint.

## Behavior and compatibility

- Clicking a row stages a stable ID. Apply writes and saves the preference.
  Cancel/Escape writes nothing; neither path saves a program.
- The preference affects the next editor opened. An active editor, its undo
  state, unsaved text, open context and save callback remain untouched. A live
  editor transplant is intentionally not part of this API: third-party screens
  do not expose a common current-text/undo/caret transfer contract.
- The manager offers Choose editor independently of the selected implementation
  and even without a disk. A third-party editor need not implement settings to
  allow switching back to it later.
- IDs are sorted and deduplicated; search is case-insensitive and pages resize
  with the screen. Full IDs appear in row tooltips. No translated label is used
  as an identity, and no editor factory runs while browsing the picker.
- Registry keys are re-read on opening, once per second while open, on selection,
  and immediately before Apply. The picker does not unfreeze Forge registries;
  it reflects late registrations only if the loader permits them.
- An unavailable or malformed configured ID remains stored. The normal open
  path falls back to V1, and the picker identifies that fallback. Restoring an
  add-on restores the configured choice without rewriting the config file.
- A staged ID that disappears cannot be applied. A persistence exception keeps
  the picker open with a diagnostic and restores the prior in-memory value.

## Shared surfaces

Authored changes are in core Liquid templates, feature/source catalogs, manager
controls, editor settings, preferred-editor resolution and English localization.
There are no Explorer implementation or completion-ranking changes. The picker
uses ordinary buttons and EditBox; it does not alter V3, PickList, or V1 editing
widgets. Existing editor IDs and registration interfaces are unchanged.

## Regression and manual handoff

`SFMTextEditorSelectionTests` exercises staged third-party round trips,
cancellation, malformed/missing IDs, late registration/removal, apply-time
revalidation, 1,000 entries with duplicates, search, page clamping, empty lists,
and persistence failure/retry. This is a test of the production selection model,
not a rendered screen or a real Forge registration event.

After resolving the native build prerequisite, from the repository root:

```bash
source /workspace/.sfm-env/env.sh
sfm-propagate-changes test run --repo-root . --projection sfm-dev/mc-1.19.2 --java-home /workspace/.sfm-env/jdk17 --filter SFMTextEditorSelection
sfm-propagate-changes run client --repo-root . --projection sfm-dev/mc-1.19.2 --java-home /workspace/.sfm-env/jdk17
```

In a development client with a third-party editor registered:

1. Put a saved multiline program on a manager disk. Open V1 and add unsaved text.
   Use `#` → Choose editor, select the add-on, then Cancel/Escape. Verify the
   preference, text, selection, undo state and saved disk content are unchanged.
2. Repeat with Apply. Verify the current document remains open and unchanged;
   save/close normally. Reopen from the manager and verify the add-on receives
   the exact saved program, including blank lines and Unicode.
3. From the manager choose V2, reopen, save a change, choose the add-on again,
   and reopen. Repeat several times. Verify config persistence after restart.
4. Try malformed and missing stored IDs. Verify the unavailable notice and V1
   fallback without a config rewrite. Restore/register the missing editor and
   verify it is usable again. Do not force mutations into a frozen registry.
5. Add enough fixture registrations for several pages. Search for a middle ID,
   page to the final entry, resize, use keyboard focus/Enter, and check full-ID
   tooltips and cancellation. Same display names must remain distinct by ID.

## Operational readiness

- Base: `e2ba833b2b235835d69475b641e24b22bfd9551a`; branch `feat/dynamic-editor-picker`.
- Dependency graph, locks, repositories, security settings and CLI source are
  unchanged. No Gradle, desktop/GPU launch, push, merge or tool upgrade was run.
- Installed CLI: `/workspace/.sfm-env/libexec/sfm-propagate-changes`, revision
  `122e4b31`, SHA-256 `35374239ffa5562ee5452d884f09ea1fef07f2121fccdd0575c44d9e3472e405`.
  User must run install.ps1: no; this is Java/template-only work.
- Attempted native command: `sfm-propagate-changes --output-format json
  --log-filter info --log-file /workspace/editor-picker-evidence/baseline-build.ndjson
  source project jar --repo-root . --projection sfm-dev/mc-1.19.2
  --dependency-profile rust-toolchain --java-home /workspace/.sfm-env/jdk17`.
- Build blocked before Minecraft Java compilation: locked
  `org.facet:vox-java:0.10.0-rc.5` source build at
  `f2afdece6c79e64085d2f8c047e22fe16b2c8c54` produced
  `vox/java/target/vox-java-0.10.0-rc.5.jar`, but the Linux resolver sought literal
  `vox\java\target\vox-java-0.10.0-rc.5.jar`. No path, hash or lock was bypassed.
- That command rehydrated existing pinned dependencies and built the managed
  Facet input. Its process (PID 850) and Cargo child finished. No game was
  started; no process was killed. No retained process/lock wait remains.
- The pinned JUnit console 1.8.1 was downloaded from its existing lock URL;
  its BLAKE3-160 equals `1b806456ca8a7a09bd65ef672af0b4344611fa3d`.
  Isolated javac/JUnit tests are retained in `/workspace/editor-picker-evidence/`.
- Full client compilation, real registry integration, rendered UI and gameplay
  checks remain unrun. Successful source generation/model tests do not establish
  any of those outcomes. The client is not launch-ready in this environment.

## Checkpoint results

- Isolated production-model Java/JUnit: 5 tests passed, 0 failed.
- Source sync: development 1.19.2 and 1.19.4 passed. Development 1.19.2
  consistency check passed (2,959 generated inputs).
- 4.35.0/1.19.2 release consistency: 1,150 inputs unchanged.
- 4.34.0/1.19.2 full consistency failed on three files outside this change:
  missing ManagedSessionPuppetAction/ManagedSuperflatSessionGamePuppet and stale
  SFMLTests. All picker-touched existing release files were unchanged in its
  dry-run. No release outputs were rewritten.
- Native build failed before Java compilation as described above; full native
  JUnit, actual Forge registry events, rendered UI and gameplay remain unrun.
