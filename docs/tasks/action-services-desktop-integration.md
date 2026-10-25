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
- Locked native compile of main, GameTest, test and datagen sources succeeded
  for **both red and fixed projections on all ten supported targets**. Every
  final receipt says `compilation: completed` and `immutable_source_lock: true`.
  Game source targets are Java 17 through 1.20.4, Java 21 for 1.21.0/1.21.1,
  and Java 25 for 26.1.2. NeoForge 1.20.1–1.20.4 uses JBR 21 as its build JVM
  while retaining Java 17 source output. No dependency pin, source hash guard
  or game JDK was bypassed. These are compiles; JAR packaging was not performed.
- `git diff --check` and `git diff --cached --check` pass.

| Target | Red extended | Fixed extended |
| --- | --- | --- |
| 1.19.2 | Compiled | Compiled |
| 1.19.4 | Compiled | Compiled |
| 1.20 | Compiled | Compiled |
| 1.20.1 | Compiled | Compiled |
| 1.20.2 | Compiled | Compiled |
| 1.20.3 | Compiled | Compiled |
| 1.20.4 | Compiled | Compiled |
| 1.21.0 | Compiled | Compiled |
| 1.21.1 | Compiled | Compiled |
| 26.1.2 | Compiled | Compiled |

The first 1.20.1 red/fixed builds were unusually long because each projection
context had an `mcp_config_joined` cache miss. The joined Minecraft
decompile/patch stage took 555,781 and 543,220 ms respectively; their actual
SFM compile nodes took 22,884 and 21,975 ms. This was repeated first-use MCP
preparation, not a javac loop.

## Local JAR packaging

All twenty explicit extended projections were packaged with the native `source
project jar` route after compilation. A narrow CLI change permits the same
guarded `--cache-root` for development JARs, reusing the exact compile cache;
released-recipe JARs still reject this override. The CLI was rebuilt from the
frozen offline Rust lock, and both the positive development JAR route and the
negative released-recipe guard were exercised. No JAR was published or run.

The machine-local manifest is
`C:\Users\Teamy\Documents\Codex\2026-10-24\task\action-services-receipts\extended-jar-manifest.json`.
It records each absolute JAR path, byte size, SHA-256, loader metadata, SDK,
source/lock/cache identity and matching compile receipt. Verification opened
and CRC-checked all 20 archives, required the action runtime, arbiter, capture,
palette and portable FE provider classes, and found no GameTest class in the
production archive. The added tooling is absent from all plain-release source
trees. Every extended `OutputStatement.java` is byte-equal to its corresponding
plain-release source, while packaged red and fixed `OutputStatement.class`
hashes differ on all ten targets.

The JARs remain in the isolated task-local integration worktree named in the
manifest; they have not been copied into the local main checkout. The frozen
offline release CLI build succeeded. `cargo fmt --check` still reports the same
56 unrelated pre-existing diffs on both the previous local main and this
integration; neither edited CLI file appears in that diff list. `git diff
--check` passes.

For the selected 1.21.1 demo pair:

| Projection | JAR bytes | SHA-256 |
| --- | ---: | --- |
| `sfm-4.34.0-extended/mc-1.21.1` | 1,963,183 | `a43fb038dfc7b7d2a25e22dde795d3e015d1622223a7c872164fd0c57c711eb3` |
| `sfm-4.35.0-extended/mc-1.21.1` | 1,974,667 | `f8e6242097328a67b44d8e1e86e8f956829fce388b0a765181667bc579ac9dfd` |

The older `scripts/test_release_recipe_namespaces.py` suite reports 20 failures
here and reports the same 20 failures on unmodified local `main` at `ba81a01fd`.
Those pre-existing expectations include the older feature set and whole-tree
parity outside the action-service change; this suite is not counted as passing.

The action services have **not** been enabled in a running Minecraft client or
tested live. The two earlier 1.21.1 visual-demo clients were launched with
earlier JARs. After explicit user authorization, their exact slot 61040/61041
launchers and JVMs were matched by process tree, projection, executable, start
time and window title; both received a normal window-close request. Each
Minecraft slot log confirms `Saving players`, `Saving worlds`, and
`All dimensions are saved`. Both JVMs and their launchers exited; their saved worlds
remain in the original slot directories.

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
cannot retrofit the previously closed visual-demo clients.
