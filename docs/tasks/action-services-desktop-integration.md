# Action services: desktop integration qualification

2026-10-25. The reviewed `codex/action-services-all-targets` source checkpoint
`ad7e79b8fc58684210fce3d88abb8fbb155db0e5` was merged against local
`main` at `ba81a01fd93147b6595c90a841af2aebc71eb789` in an isolated
worktree. The conflict resolution retains the local portable FE fixture,
inspection workspace, deferred opening and yield behavior. The plain 4.34 and
4.35 projections do not acquire action services or the portable manual fixture.

The twenty explicit `-extended` projections include independent File and Vox
action services, the existing release regressions and
`issue_610_portable_fixture` (including `Issue610PortableControlsGameTest` and
Manual Mode). 1.19.2/1.19.4 extended projections also select the typed command
palette; all extended projections select the existing formatted-font and static
GameTest-name adapters required by their generated puppet code. Optional theme
settings and Explorer context classes are selected only with their UI features.
The command palette keyboard-context constructor omits its workspace panel
argument when that feature is absent. For 26.1.2 the control server uses
`ResourceKey.identifier()`.

## Evidence

- All twenty extended projections generated and passed native source checks.
- `scripts/test_action_services.py -v`: five tests pass, including 34 real Java
  policy/scheduling assertions, feature closure, generated service/fixture
  membership, complete historical 4.34 source equality and the reviewed plain
  4.35 fixture-only membership delta.
- Locked native compile of main, GameTest, test and datagen sources succeeded for
  red and fixed 1.19.2 with installed JBR 17, red and fixed 1.21.1 with installed
  JBR 21, and red 26.1.2 with installed JBR 25. Red 1.20.3 also compiled with
  JBR 21 as the NeoForge build JVM before the portable fixture was added to the
  extended projections. No dependency pin, source hash guard or game JDK was
  bypassed.
- `git diff --check` and `git diff --cached --check` pass.

The older `scripts/test_release_recipe_namespaces.py` suite reports 20 failures
here and reports the same 20 failures on unmodified local `main` at `ba81a01fd`.
Those pre-existing expectations include the older feature set and whole-tree
parity outside the action-service change; this suite is not counted as passing.

Native compilation of the final feature set is **not yet verified** for the other fifteen extended
targets. The action services have **not** been enabled in a running Minecraft
client or tested live. Existing demo clients were launched with earlier JARs;
they remain open, and this integration does not alter their runtime behavior.
The service ports, file directories, capture and cooperative pause need runtime
qualification in newly launched extended clients before claiming availability.
The older exploration puppet's raw mouse/key protocol has not been promoted to
the all-version service surface. No release JAR or remote `main` was published.

For a later approved runtime run, build the selected `-extended` projection JAR,
launch that exact projection in a **new** game process, and enable File and/or
Vox independently with `SFM_ACTION_FILES=1` and `SFM_ACTION_VOX=1`, or invoke
the corresponding `sfm:control/files/enable` and `sfm:control/vox/enable`
palette actions. Both default off. Verify endpoint identity and safe interaction
before using `sfm.exe` or file requests. This requires a separate launch; it
cannot retrofit the two currently open clients.
