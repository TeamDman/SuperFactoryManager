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
- IDs are sorted and deduplicated; search matches names and IDs case-insensitively, and pages resize
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

## Relationship to Saereth's PR #581

Read-only inspection of [PR #581](https://github.com/TeamDman/SuperFactoryManager/pull/581)
found Saereth's existing work at head
`f185a15f4aba48efe920f512bc6853d011b574a2`, based on 26.1.2 commit
`431066a30420ba135b6eaad493e780f1e12a7a14`. At inspection the PR was open,
unmerged and GitHub reported `mergeable: false`. The existing contribution is
preserved; this branch does not merge it, rewrite its commits or modify the PR.

The PR already proposes registry enumeration, a manager selector, an
in-editor selector, explicit preference persistence, display-name localization,
a changed event, immediate content-carrying switches, and using the preference
for examples/changelog. The picker checkpoint was written before the parent
identified that PR. The follow-up explicitly reuses its add-on display-name
convention: `gui.sfm.preferred_editor.<namespace>.<path>`, falling back to the
registry ID. Saereth is credited for this design. Translated labels retain the
ID alongside them; duplicate names remain independently selectable, and search
matches both names and IDs. No hardcoded registration list is introduced.

The chosen integration retains the PR's registry-driven manager access and
explicit persistence intent. Its small anchored popup is replaced with staged
selection, search, bounded pages, cancellation and stale-ID diagnostics for the
requirements of this task. The 26.1.2 implementation is not copied wholesale
into the 1.19.2 canonical source.

The PR's immediate-switch context forwards content, close/save and labels, but
this branch also has read-only metadata, asynchronous save lifecycle, document
snapshots and panel disposal state. An immediate transplant must preserve those
contracts and provide a transfer API for third-party screens. This change keeps
the current document alive and explicitly applies the preference on next open.
It does not adopt the changed event or change examples/changelog routing; those
are independent integration decisions. This should be reconciled with #581,
not landed as a competing replacement without reviewing the contributor's work.

PR metadata and full patch evidence are retained in
`/workspace/editor-picker-evidence/pr581-{metadata,patch}.json`. No tracker write
or communication to the contributor was performed.

The final name-search regression increases the isolated picker suite to six
passing tests (20 including the modal controller suite). A native test attempt
against the implementation, using `test run --filter SFMTextEditorSelection`,
failed during dependency preparation at the same locked Vox output path; it did
not reach Java compilation or run native JUnit. That process (PID 12609) and its
managed Cargo build completed and exited. This was not an approval denial and
there is no pending operation.

## Final source validation and review branches

- Picker-only branch: `feat/dynamic-editor-picker-only`,
  `46d14c9afba3fdbd03baf4517fa26b7bd4b85f0d` (includes PR #581 naming attribution).
- Combined source: `ef4569c2a10e3de7ac7d4225a514a1597d647265`,
  with modal checkpoint `48832f7455fb685c36036a4f211e4d278a2e2adf`.
- Final source consistency: 2,963 files on development 1.19.2 and 2,926 on
  development 1.19.4 passed. The 4.35.0/1.19.2 release check passed with 1,150
  unchanged files. An intermediate check caught a changed historical changelog
  line ending; it was restored in `ef4569c2`, without rewriting release output.
- All 20 isolated controller tests passed. Full runtime checks remain blocked,
  not passed. Logs, hashes, commands and scope are preserved in
  [verification.json](../task-evidence/editor-selection/verification.json),
  [the test log](../task-evidence/editor-selection/isolated-tests.log) and
  [the native failure](../task-evidence/editor-selection/native-test-error.txt).
- Both in-scope native commands exited; there is no game or helper running.
  No automatic approval rejection occurred. The follow-up below refines the
  blocker from path resolution to the produced artifact bytes.


## Linux portability follow-up

The editor baseline remains `e2ba833b2b235835d69475b641e24b22bfd9551a`;
no switch to the newer Vox migration branch was made. The picker follow-up
`3cde0795fe0012ec092acd4c6cbe3cb549ac18c1` removes its accidental dependency
on the canvas-only dynamic-tooltip builder method. Each supported Minecraft
version now uses the existing tooltip constructor and refreshes the ID tooltip.
No shared editor widget or Explorer implementation was changed. Development
source checks passed again: 2,963 inputs for 1.19.2 and 2,926 for 1.19.4.
These checks do not compile Java or render the UI.

The separately reviewable CLI branch `fix/portable-source-build-output`, commit
`f9831eb1aa4ba440845a1002aa055de2acbda081`, resolves the locked output using
portable separators. It preserves the literal recipe and source provenance,
and calls the same checked copy with the same expected hash. Both focused
Rust tests passed against the actual CLI library: real-file lookup with three
separator forms and rejection of wrong bytes before cache publication followed
by acceptance of exact bytes. No dependency/lock/security file was changed,
and no CLI executable was installed or substituted. The required full Rust
gate could not run: PowerShell, rustfmt and Clippy are absent. They were not
installed. See [the test/gate results](../task-evidence/editor-selection/portable-path-results.txt).

The path was not the only blocker. The existing pinned Facet recipe at
`f2afdece6c79e64085d2f8c047e22fe16b2c8c54` produced a 1,326,016-byte JAR:

- Expected BLAKE3-160: `4d1e88353f941be926fdf84f1dd8da9bd594b60f`.
- Actual BLAKE3-160: `cc19e740cf1c08970c23b2446781829106d9ab8c`.
- Manifest: `Created-By: 17.0.20.1 (Eclipse Adoptium)`.

The producer runs the host `jar --create` with a fixed timestamp but leaves
`Created-By` host-dependent, and its locked environment is empty. The lock
lists only a Windows JetBrains JBRSDK 17.0.14 artifact; it does not provide a
Linux producer SDK override. This establishes a reproducibility gap, but the
original locked JAR is unavailable, so it does not prove that the manifest is
the only difference or identify the original producer JDK.

Compilation and actual registry/UI qualification require the exact existing
locked artifact from the same source revision, or a separately approved
producer toolchain/artifact decision. The incompatible output was not copied
into the cache, rewritten to guess a hash, or hidden behind a filename with
backslashes. Full native compilation, third-party in-client round trips,
document persistence, modal input ordering and rendered checks remain unrun.
The failed native attempts still refer to the original path failure; the
follow-up establishes the byte mismatch by hashing the actual output, not by
claiming a successful rerun of the native build.

No action was denied or left pending. The changed-source Rust test process
completed normally. An accidental pre-edit filtered Cargo build was stopped
by its exact owned PIDs before the real run; no active task helper or game
remains. Source and detailed evidence are preserved in both worktrees and
`/workspace/editor-picker-evidence/`.
