# Vox Terminal Bridge and Graceful Degradation Plan

This plan supersedes the colour-picker flow as the first user-facing Vox
experiment. The colour-input panel remains useful reusable UI infrastructure,
but the first bridge should solve a more practical problem: an in-game
terminal/console surface that works for every player and becomes richer when
the optional Rust development environment is available.

The motivating reference implementation is Teamy Studio at
`G:\Programming\Repos\Teamy-Studio`. Its terminal work is especially relevant:

- `docs/notes/terminal-control-plane.md` treats a terminal session as an
  addressable resource with lifecycle, input, snapshot, and transcript actions;
- `docs/notes/terminal-engine-replacement-plan.md` puts VT parsing, screen
  state, cursor/style state, semantic prompt tracking, and keyboard encoding
  behind a Rust-owned engine boundary; and
- the terminal crates already provide a useful reference for cell-grid
  rendering, replay fixtures, headless tests, and terminal-engine selection.

This is a design and implementation plan, not permission to make the Minecraft
mod depend on Teamy Studio or on a Rust process at runtime.

The Rust terminal implementation is now planned as a separate public
MPL-2.0 `TeamDman/teamy-terminal` repository rather than as a dependency on the
larger Teamy Studio application. Its core/Vulkan/font workspace, bootstrap,
path-override iteration, pinned-dependency transition, and subagent gates are
recorded in the authoritative [Teamy Terminal Repository and Vulkan Renderer Plan](https://github.com/TeamDman/teamy-terminal/blob/main/docs/tasks/teamy%20terminal%20repository%20and%20Vulkan%20renderer%20plan.md).

## Product outcome

The command palette can open a terminal panel inside the SFM multiplexer. The
same Java screen and typed terminal contract work in three modes:

1. **Java-local virtual terminal** — the default and always-available mode.
   Java owns the service and client in-process, backed by a bounded in-memory
   virtual file system and a safe command set.
2. **Java-local filesystem terminal** — an explicitly selected mode that can
   inspect or edit approved roots such as the mounted Minecraft instance. It
   uses the same service contract but applies path containment, size, encoding,
   and mutation policy before touching disk.
3. **Vox/Rust terminal** — an optional development-environment mode. Java is a
   thin Vox client and Rust is authoritative for the PTY, command execution, VT
   parsing, scrollback, colors, cursor state, and terminal rasterization. The
   first presentation mode is a bounded full PNG snapshot; Java uploads and
   displays that image in the panel and sends input/resize messages back. It
   may later provide richer VT behavior, semantic prompt/symbol information,
   compilation, audit, and other repository tooling.

### Explicit user-facing modes and lifecycle actions

The command palette must make backend selection explicit instead of silently
changing the meaning of one action based on a JVM property:

- `sfm:repl/open` always opens the Java-local virtual terminal. It remains
  useful even when a Rust server is running.
- `sfm:terminal/open` tries to open the Rust-backed terminal using the current
  Rust server endpoint. If the endpoint is absent or unavailable, it opens a
  visibly labelled retryable fallback rather than permanently disabling the
  panel.
- `sfm:terminal/connect-rust-server [address]` accepts an optional
  `HOST:PORT` (also `:PORT` for loopback), otherwise using the client-configured
  default endpoint. It updates the current Rust terminal connection and can be
  invoked after the Minecraft client has already started.
- `sfm:terminal/start-rust-server [address]` launches the configured
  `teamy-terminal.exe serve [address]` process hidden, reuses an already-live
  endpoint, and then connects the Rust terminal. SFM must track only processes
  it started so an explicitly launched server is never killed accidentally.

The client config should own the default loopback address/port (initially
`127.0.0.1:63946`) and an optional executable path. The JVM property remains a
test/puppet override during migration, not the normal manual-launch contract.

The first two modes must remain useful when Rust is not installed, the game is
offline, the endpoint is stopped, authentication fails, or the protocol is
incompatible. A failed optional connection produces a visible status and a
fallback or launch suggestion; it must not disable mounted editing or ordinary
SFM gameplay.

## Boundary and ownership

Java owns all Minecraft-facing concerns:

- the terminal panel, multiplexer allocation, focus, keyboard/mouse routing,
  clipping, theme, accessibility text, and GUI-scale behavior;
- the local service implementation and virtual/local filesystem policy when
  the Java-local backend is selected;
- Vox connection/input/resize plumbing, validation of every inbound frame,
  bounded buffering, PNG texture upload, thread handoff through the Minecraft
  executor, and terminal lifecycle shown to the player; and
- graceful fallback when the optional endpoint is absent or unhealthy.

Rust owns optional development-environment concerns:

- a real process-backed shell or Teamy Studio terminal engine;
- VT parsing, terminal screen state, cursor/style state, scrollback, replay,
  semantic prompt/symbol/handle metadata, and the visual contents of every
  Rust-backed frame;
- repository/compiler/audit commands that are inappropriate for the Java-only
  gameplay runtime; and
- richer external interfaces such as eframe, when the user explicitly asks
  for them.

The wire contract carries portable data and intent only. Rust never sends a
Minecraft `Screen`, panel, widget tree, renderer callback, arbitrary layout
instruction, or Java object reference. In Java-local mode the panel renders
the local transcript; in Vox mode it displays the Rust-owned full-frame PNG
and does not independently reinterpret terminal output or colors.

## Contract shape

The contract should be generated from the frozen Facet/Vox schema machinery,
not hand-copied into separate Java and Rust enums. The initial unary surface
should be deliberately small and extensible:

### Endpoint lifecycle

- `discover` / `status`: endpoint presence, protocol version, capabilities,
  authentication state, and a human-readable failure reason;
- `connect`: authenticate, negotiate schema and capability versions, and
  obtain a connection id;
- `disconnect` / `shutdown`: terminalize pending calls and release resources;
- bounded request ids and cancellation for every operation that may wait.

The Java-local implementation uses the exact same service interface over
in-memory channels. It is not a second command hierarchy; it is another
transport/ownership implementation of the same typed actions.

### Terminal session

- `create_session(name, backend, initial_size, scrollback_limit)`;
- `attach(session_id)` and `detach(session_id)`;
- `resize(session_id, columns, rows, cell_metrics)`;
- `send_text`, `send_key_event`, and `send_mouse_event`;
- `snapshot(session_id, kind)` for visible cells, cursor, selection, and
  bounded scrollback;
- `close_session(session_id)`.

The first version should support exact key-down/key-up ordering as well as
ordinary text input. This keeps the action stream replayable and lets the
Minecraft screen use the same input model as a future agent or puppet.

### Published terminal state

Output events should be structured rather than an unbounded stream of ad hoc
strings. A frame may contain:

- logical columns and rows plus the bounded cell matrix;
- glyph, foreground/background, style, and cell-occupancy information;
- cursor position/shape/visibility and selection ranges;
- scrollback sequence or snapshot witness;
- prompt and command-range markers; and
- optional semantic handles/symbols such as a command, argument, diagnostic,
  file path, or source location.

Semantic handles are metadata for navigation and tooling. They do not give the
remote side permission to draw or mutate an arbitrary Minecraft panel.

### Filesystem and command policy

The Java-local virtual backend should begin with deterministic commands such as
`pwd`, `ls`, `cat`, `echo`, and bounded file read/write operations over an
in-memory `BTreeMap<RepoPath, BString>`-style store. A local-disk backend must
be explicit, root-contained, size-limited, and auditable. An unrestricted OS
shell is not the default Java behavior.

The Rust backend may host Teamy Studio's real shell and terminal engine, but
its process execution remains behind the optional endpoint and its own command
approval/sandbox policy. Java must not silently turn a game command into an
arbitrary host-process launch.

## Screen and command-palette integration

Add a typed command-palette action such as **Open terminal** with parameters
for backend preference, initial working root, and optional session name. The
action opens a terminal leaf in the current SFM multiplexer; outside the
multiplexer it follows the existing open/push behavior.

The screen should expose, at minimum:

- connection/backend status (`Java local`, `Rust connected`, `fallback`, or
  `unavailable`) with an explanation;
- terminal text/cell rendering, cursor, selection, and scrollback;
- a compact session/control strip that does not steal the terminal's logical
  area; and
- actions for reconnect, switch backend, copy, clear, snapshot, and close.

The terminal panel is a normal composable leaf. It must work as a full-screen
panel and inside horizontal, vertical, tab, and nested split layouts. Rust
frames describe terminal content; the Java panel owns all layout and theme
decisions.

## Rust-owned PNG presentation

The initial Rust-backed presentation is deliberately a complete PNG frame
rather than a cell protocol or native GPU-handle interop. Rust owns the PTY,
VT state, font rasterization, and pixel contents; Java receives a bounded full
PNG, decodes it on the Minecraft render thread, uploads it to a dynamic
texture, and blits it inside the terminal panel. Keyboard, mouse, resize,
focus, and paste events travel in the opposite direction through the same
session. This gives us an end-to-end correctness proof before optimizing
unchanged glyphs, dirty regions, or GPU paths.

The eventual texture-stream experiment can replace the PNG payload without
changing the ownership boundary or panel contract. Teamy Studio can render
its terminal into an off-screen target using its existing DirectX/font
pipeline; Java then owns a Minecraft texture resource and blits the received
frame inside a terminal panel. Native GPU-handle interop remains out of scope
for the first implementation.

The process boundary needs to be treated honestly: a DirectX GPU texture
handle cannot normally be handed directly to Minecraft's separate LWJGL/OpenGL
context. The first transport must therefore be a bounded pixel-frame protocol
(for example RGBA/BGRA tiles or a compressed frame) with sequence number,
dimensions, stride/format, dirty rectangles, and an optional cursor/selection
overlay. PNG is appropriate for puppet screenshots, saved snapshots, and a
low-frequency proof because it is easy to validate and archive, but encoding a
full PNG for every keystroke is needlessly latency- and CPU-sensitive. The live
MVP should therefore permit raw or losslessly compressed dirty tiles, with a
PNG/keyframe fallback when a full refresh is needed. A later native
shared-resource experiment may use explicit
DirectX/OpenGL interop only if it can prove device/context ownership, lifetime,
security, and graceful fallback. It must not be assumed merely because both
sides call the result a texture.

The Java texture panel owns upload, resource lifetime, clipping, aspect-ratio
policy, GUI-scale/layout bounds, and dropped/stale-frame handling. Rust owns
the font rasterization and terminal appearance in texture mode. A resize of
the multiplexer leaf sends a bounded render-target request to Rust; Java never
accepts arbitrary remote layout instructions. The frame protocol should allow
the Java-local backend to use the same presentation seam with a locally
rendered fallback, or to select the structured-cell renderer when a texture
stream is unavailable.

The live frame envelope should include a monotonically increasing sequence,
session id, pixel width/height, logical panel bounds, pixel format, stride,
full-frame versus dirty-tile kind, compression kind, and an optional cursor or
selection layer. Java rejects oversized, out-of-order, malformed, or stale
frames and may drop intermediate frames while retaining the newest complete
frame. The protocol must distinguish a terminal snapshot PNG intended for
archive/replay from a presentation frame intended for immediate upload.

Texture mode is an optional capability negotiated at connect time. Its proof
must show: the unmistakable Teamy Studio terminal appearance, keyboard input
round-tripping to the Rust session, resize/re-render behavior, a dropped-frame
or disconnect state, and fallback to the Java-local terminal without losing
the session's useful status. Captures should include full-screen, nested-panel,
narrow-window, and supported GUI-scale layouts. This is a presentation mode
for terminal content, not a way to send Minecraft panels or executable UI over
the wire.

## Phased implementation

### Active delegation wave — 2026-07-25

The first concrete batch is deliberately split across independent repositories
and worktrees. Agents must commit their own branches and report tests/evidence;
the coordinator owns canonical-plan edits, review, merge order, and the final
Minecraft proof.

| Track | Branch / worktree | Acceptance target |
| --- | --- | --- |
| Java-local terminal | `feat/1.19.2/terminal-java-local` / `D:\Repos\Minecraft\SFM\worktrees\1.19.2-terminal-java-local` | In-process service/client, bounded virtual filesystem, safe commands, composable panel, command-palette action, focused tests, and a Java-local puppet proof. |
| Vox terminal contract | `teamy/vox-java-terminal-contract` / `G:\Programming\Repos\facet-worktrees\vox-java-terminal-contract` | Generated portable session/input/resize/frame/cancellation/error DTOs plus deterministic schema and round-trip fixtures on the reviewed `teamy/vox-java` base. |
| Teamy Studio frame probe | `teamy/terminal-frame-probe` / `G:\Programming\Repos\teamy-studio-terminal-frame-probe` | Headless off-screen terminal capture, PNG artifact, bounded raw/compressed frame seam, and readback/latency evidence without native GPU-handle interop. |

The contract and frame-probe tracks may proceed in parallel with the Java-local
slice. Integration is coordinator-owned: first review the generated contract,
then adapt the Java service/panel, and only afterward connect the optional Rust
texture presentation. No agent should edit the canonical `1.19.2` worktree or
silently broaden the scope into the colour-picker bridge.

### Delegation wave results — 2026-07-25

All three tracks reached a reviewable boundary without being merged into
canonical `1.19.2`:

- **Java-local terminal:** `bc97e8bc2` on
  `feat/1.19.2/terminal-java-local`. It contains the typed in-process
  service/client, bounded virtual filesystem, deterministic commands, terminal
  panel, command-palette action, focused filesystem/service tests, and
  `title_screen_java_local_terminal` puppet definition. Pure terminal classes
  compile with JDK 17. Feature-worktree CLI compile/test/puppet generation
  could not produce a screenshot because the CLI targets the canonical branch
  build lock; canonical verification is the next integration step.
- **Vox terminal contract:** the reviewed worktree now ends at `26fda8736`
  on `teamy/vox-java-terminal-contract`, based on the reviewed contract
  commits `144382fd5` and `35461380d`. It adds the Rust-authoritative Terminal
  service, generated Java 17 DTO/client/handler/dispatcher/descriptors,
  capability and frame bounds, cancellation/disconnect/error types, generator
  integration, design notes, and
  `vox/test-fixtures/terminal/terminal-contract-v1.json`. The elevated
  `test-java` and `package-java` gates pass, including the generated-source
  checks, reproducible assembly, fresh consumer, and `jdeps` checks. The
  packaged runtime JAR includes the generated `Terminal*` bindings while
  excluding testbed/application fixtures.
- **Teamy Studio frame probe:** `e03e9d5` on `teamy/terminal-frame-probe`.
  It adds bounded Raw RGBA8, full-PNG, dirty-PNG-tile, and input/resize frame
  seams. Five focused tests and clippy pass. Review artifacts include a
  256×128 terminal frame, raw bytes, and metrics showing raw 131072 B, full
  PNG 2515 B, and three dirty PNG tiles 1430 B with approximately 3 ms CPU
  encoding. The full replay suite still has one pre-existing missing-fixture
  failure.

The Teamy Studio DirectX follow-up also produced a real 1040×680 off-screen
render after temporarily setting `CUDARC_CUDA_VERSION=13020` with a target
directory on `D:`. The existing cudarc 0.19.7 lock only supports through
13.2; `teamy-llm-service` uses cudarc 0.19.8 and `13030`. The render then
failed only on a stale expected scene/color snapshot, not renderer startup.
No CUDA or Teamy Studio source changes were committed in that follow-up.

The packaged contract review is complete, and the coordinator-owned portable
Cargo/xtask acquisition route is now implemented. The reviewed contract was
merged into and pushed on `TeamDman/facet` `main` at
`aa75598dabb2138b18365cdf0d97ca94a34c5319`; SFM now pins that published
revision in its canonical 1.19.2 lockfile. The Java-local backend remains the
default while the optional Vox adapter is implemented and verified.

Coordinator status after the standalone terminal baseline: `teamy-terminal`
main contains the bounded core scrollback/reflow, dirty rendering, Tracy
profiling, idle-scheduler, and dirty-render-default commits locally; they are
not pushed from this worktree. The SFM 1.19.2 branch now contains the reviewed
Java-local terminal acceptance baseline at `c996521da`. The reviewed Vox contract is
frozen as the `vox-java-0.10.0-rc.5` artifact produced at `26fda8736`, but it
is now consumed by SFM through the pinned `TeamDman/facet` `main` revision and
the portable Cargo/xtask source-build recipe. A workspace-relative Maven URL
is not used, and generated runtime sources are not vendored into SFM.

The integration order is therefore:

1. Keep the generated contract boundary at `26fda8736` reviewed and the
   published Facet `main` revision pinned through the SFM-supported Cargo
   source-build recipe for `org.facet:vox-java:0.10.0-rc.5`.
2. Keep the canonical compile/test and Java-local command-palette puppet proof
   green from the canonical worktree.
3. Implement and prove the optional `SFMVoxBridge` against the pinned generated
   Java contract while preserving Java-local fallback.
4. Re-run the same terminal scenario against the optional Rust endpoint, then
   pursue the separate texture-presentation experiment.

### Canonical Java-local validation — 2026-07-26

The canonical `1.19.2` worktree now has the first Java-local acceptance proof
from the actual propagation CLI, rather than only the focused class tests:

- `sfm-propagate-changes.exe run compile --branch 1.19.2 --explain-rebuild`
  completed successfully after granting the CLI access to its user-level
  artifact-cache locks.
- `sfm-propagate-changes.exe test run --branch 1.19.2` completed with 419
  tests found, 417 passed, 0 failed, and 2 aborted because this Windows
  client lacks the privilege needed to create symbolic links. The focused
  `SFMJavaLocalTerminalServiceTests` run is also green.
- `sfm-propagate-changes.exe puppet run title_screen_java_local_terminal
  --branch 1.19.2 --width 1280 --height 720 --wait-for-build-lock` passed with
  `failed=0`, opened the terminal through the command palette, exercised safe
  commands and bounded scrollback, and wrote the
  `title_screen_java_local_terminal__java-local-terminal.png` capture under
  `platform/minecraft/runGameTestPreview/screenshots/`.

The proof caught and fixed two bookkeeping-level correctness issues: the
scrollback viewport now preserves the viewed row while new output arrives
and clamps only when bounded retention evicts that row, and the puppet uses
the canonical fully-qualified `sfm action invoke sfm:terminal/open` command.
The Java-local phase is committed as `c996521da` in the canonical worktree and
has been propagated baseline-first through every version worktree from
`1.19.4` through `26.1.2`; all version worktrees were clean after the merge.
The reviewed Vox contract is now pinned through the published Facet `main`
revision `aa75598dabb2138b18365cdf0d97ca94a34c5319`. The artifact is
`vox-java-0.10.0-rc.5`, produced from contract commit `26fda8736`; its
published-main SHA-256 is
`728884A046A0A754144E67D6EECC89002EA9138C50A20AC83A1FFFC9C33E59B8` and its
locked BLAKE3 content hash is
`3bd59c93fd5d602821c4460dc4e5255635c355f9`. The old locally cached artifact
was stale and was rejected by the content-hash check. No Cloud Terrastodon
changes are part of that work.

The focused `SFMJavaLocalTerminalServiceTests` run remains green after the
commit and propagation. A fresh elevated rerun of
`sfm-propagate-changes.exe run compile --branch 1.19.2 --no-refresh
--no-wait-for-build-lock` passed, as did the corresponding full JUnit gate
(`419` tests found, `417` passed, `0` failed, and `2` expected Windows
symlink-privilege aborts) and the focused terminal filter. The non-elevated
account could not open the existing user-level Forge artifact lock; this was
an ACL/environment issue, not a Java-terminal failure. The propagation command
was `sfm-propagate-changes.exe git merge --auto-abort`; no push was performed.
The profiler wrapper now starts `tracy-capture.exe` with
`Start-Process -NoNewWindow`, so capture output stays in the invoking
Codex/current console instead of opening an explicit `wt.exe` window.

The follow-up commits `727816f05` and `7af8f8efd` were propagated with
`sfm-propagate-changes.exe git merge --auto-abort`. All ten version worktrees
from `1.19.2` through `26.1.2` are clean and contain the canonical
`7af8f8efd` commit as an ancestor. A fresh canonical compile and full JUnit
rerun after propagation again completed with 419 tests found, 417 passed,
0 failed, and the same two expected symlink-privilege aborts.

### Portable Cargo source-build support — 2026-07-26

The canonical propagation CLI now recognizes the reviewed Vox runtime source
layout and records a portable `CargoCommand` recipe for
`org.facet:vox-java:0.10.0-rc.5`:

```text
cargo run --locked --package vox-xtask -- package-java
vox/java/target/vox-java-0.10.0-rc.5.jar
```

The recipe is materialized from the locked Git commit into SFM's managed
source-build cache, and its output is copied into the Maven cache with the
existing content-hash and provenance checks. The focused CLI suite passes 363
tests with one pre-existing ignored network test. This closes the earlier
"no Cargo/xtask acquisition path" implementation gap without adding a Gradle
requirement.

The source-build path also accounts for a Windows-specific portability limit:
the Vox packaging task invokes `javac`, which failed from the long per-user
temp checkout even though the generated sources and Cargo target were already
separated. SFM now uses `SFM_SOURCE_BUILD_ROOT` when set, otherwise an existing
`%SystemDrive%\tmp` directory on Windows, and finally the normal temp directory
as a fallback. Cargo checkouts and target outputs both use this root. On this
client, a fresh physical checkout under `C:\tmp` passed the full
`cargo run --locked --package vox-xtask -- package-java` task, and the
canonical SFM compile then passed. `LongPathsEnabled` is therefore not a
required prerequisite for this acquisition route; the short physical root is
the deterministic fix.

### Java Vox adapter and propagation — 2026-07-26

The canonical SFM branch now contains `SFMVoxTerminalService` at
`26965ca95`. It owns the optional Vox connection driver and terminal lane,
performs generated `connect`, `send_text`, `snapshot`, `disconnect`, and
reconnect operations, enforces the negotiated frame bound, and retains a
defensive snapshot-payload handoff for the future cell/frame renderer. An
unavailable endpoint fails closed with a visible response; the existing
Java-local service and panel remain the default. The focused adapter tests and
the full canonical JUnit gate pass with only the two known Windows
symlink-privilege assumptions aborted.

The required oldest-first propagation command completed after preserving each
Minecraft version's lock inventory and adding the same published Vox pin. The
ten supported worktrees from `1.19.2` through `26.1.2` are clean, contain the
canonical adapter commit as an ancestor, and each lockfile has exactly one
`vox-java` dependency and one
`org-facet-vox-java-0-10-0-rc-5-3bd59c93` artifact. The 1.19.4 compile was
also attempted; it remains blocked by three pre-existing Minecraft API calls
using the wrong `ItemRenderer` signatures in
`SFMItemIconRenderer` and `SFMFalsifiedInventoryReplayPanel`, which were not
changed by this terminal work.

The next implementation slice is launch ergonomics and lifecycle recovery
around the already-proven bounded full-PNG endpoint. It must preserve the
explicit Java-local `repl` path, must not modify Cloud Terrastodon, and must
make starting the server after Minecraft a supported flow.

### Rust-authoritative PNG endpoint — 2026-07-26

The implementation now follows the intended ownership boundary:

- `G:\Programming\Repos\teamy-terminal\tools\vox-terminal-server` is an
  isolated Cargo workspace because the main Teamy Terminal workspace still
  pins an older Facet revision through the `weavy` dependency. It uses the
  published Facet `main` revision and the generated Terminal contract.
- The endpoint owns a real `pwsh -NoProfile` PTY, answers the initial terminal
  device/cursor queries, feeds VT bytes into `TerminalSession`, and rasterizes
  the complete session into a bounded CPU PNG using the terminal font crate.
- The focused Rust puppet test runs `1..100; Write-Host -ForegroundColor Cyan
  "hello, world!"; exit`, verifies the terminal state and cyan cell, and
  verifies the returned payload is a bounded PNG. The test is green.
- `SFMVoxTerminalService` now sends carriage-return input, accepts only full
  PNG frames with a valid signature, and retains the defensive snapshot for
  the panel. `SFMTerminalPngRenderer` decodes/uploads one frame per server
  sequence and replaces the prior dynamic texture safely.
- `sfm.terminal.voxEndpoint=HOST:PORT` is currently the test/puppet override
  for the Rust endpoint. With the property absent, the Java-local backend
  remains the deterministic fallback until the explicit action/configuration
  split is implemented. The terminal puppet has a Rust path that captures the
  `1..100` and cyan `Write-Host` states.

The SFM-side source changes are committed locally at `211e5cb1b` and are not
propagated. The focused Java tests and live endpoint-backed Minecraft puppet
capture are green. The next verification gate is the full canonical SFM gate,
followed by oldest-first propagation; performance work such as dirty
glyph/tile updates is explicitly deferred until the full-PNG correctness proof
and lifecycle behavior are stable.

### Live TCP and Minecraft presentation proof — 2026-07-27

The isolated server was built and launched hidden on a loopback TCP port. The
canonical puppet was run with
`JAVA_TOOL_OPTIONS=-Dsfm.terminal.voxEndpoint=127.0.0.1:63946`; it completed
with exit code zero and produced:

- `title_screen_java_local_terminal__vox-terminal-powershell-range.png`,
  showing the Rust-rendered `1..100` tail (`62..100`) in the bounded 40-row
  viewport; and
- `title_screen_java_local_terminal__vox-terminal-powershell-cyan.png`,
  showing the Rust-rendered cyan `hello, world!` output.

The first attempt correctly fell back after the initial PTY drain exceeded the
old three-second Java timeout. Raising the correctness-first RPC timeout to
15 seconds fixed the live startup. A second presentation issue was also found
by visual inspection: the dynamic texture needed an explicit Minecraft shader
sampler binding in addition to the texture-manager bind. The corrected live
captures match the raw Rust PNG. The raw endpoint test and focused
`SFMVoxTerminalServiceTests`/`SFMJavaLocalTerminalServiceTests` gates are green.

The earlier intentional limitation—text input and full snapshots only—has now
been removed at the bridge boundary. Rust implements the already-generated
`send_key` and `send_mouse` operations, Java sends exact printable text and
physical key transitions, and Rust encodes mouse reports only after the
terminal has requested mouse tracking. No performance optimization or Cloud
Terrastodon change is part of this slice.

### Manual launch ergonomics and `--solo` correction — 2026-07-27

The first manual instructions exposed two integration gaps:

- `--solo` currently skips `resolve_run_plain_dependencies` and the
  deobfuscated project dependency path wholesale. The locked `vox-java`
  dependency is a required plain runtime library with
  `data_run_policy: include`, not a loader-managed Minecraft mod and not
  currently a Jar-in-Jar dependency. This can omit `org.facet.vox.*` even
  though optional mods such as Mekanism were the intended things to omit.
- The user currently has to start an isolated Cargo workspace and pass a JVM
  property before launching SFM. That is a development proof, not an
  acceptable manual workflow.

The implementation status for those gates is now:

1. **Complete.** Solo classpath construction retains required plain runtime
   libraries while omitting loader-managed mod jars and deobfuscated outputs.
   The lockfile projection regression and the rebuilt effective smoke
   classpath both prove `vox-java` is present and Mekanism is absent.
2. **Complete.** `repl/open` is Java-local, `terminal/open` is Rust-preferred
   with fallback, and endpoint/executable defaults live in `SFMClientConfig`.
   `terminal/connect-rust-server [address]` accepts `HOST:PORT`, `:PORT`, or a
   bare port; JVM properties remain explicit test/puppet overrides.
3. **Complete.** `SFMVoxTerminalService` clears transient connection failure
   state and retries on later resize/execute requests.
4. **Complete.** `terminal/start-rust-server [address]` launches the configured
   `teamy-terminal.exe serve HOST:PORT` with no console window, waits for TCP
   readiness, and then opens the Rust-backed panel. The Java-only action does
   not probe or depend on the Rust process.
5. **In progress.** The Rust-start puppet proves Java-started Rust, full PNG
   frames, `1..100`, and cyan `Write-Host` output. Remaining lifecycle work is
   a focused restart/stop matrix and explicit manual proof of every ordering
   permutation; it is not required to change the Rust-authoritative frame
   contract.

#### Evidence — 2026-07-27

- The rebuilt canonical command
  `sfm-propagate-changes.exe run client --solo --smoke --branch 1.19.2`
  reached the title screen. Its authoritative
  `runClientSmoke/minecraftClasspath.txt` contains 123 entries including
  `org.facet:vox-java:0.10.0-rc.5` and no Mekanism entry.
- The Rust CLI passes 16 focused library tests, including the bounded PNG Vox
  puppet and `serve :0` stop-after smoke. The SFM CLI passes the solo-classpath
  and lockfile-projection regressions.
- `sfm:title_screen_rust_terminal` completed with
  `SFM_GAME_PUPPET_COMPLETE failed=0`, producing the range and cyan captures
  under `build/sfm-toolchain/artifacts/game-test-preview`. The Java action
  launched `teamy-terminal.exe` and the captured panel shows Rust-authoritative
  PNG output.

The server process must remain owned by the Rust CLI, not reimplemented in
Java. Java owns only process launch, endpoint selection, transport state, and
the panel; Rust remains authoritative for terminal visual state.

### Interactive Rust bridge: direct input and periodic publication — 2026-07-27

This follow-up closes the most important correctness gap in the first PNG
proof. `send_text` now means exact bytes and no longer adds an implicit Enter;
Enter, Backspace, Ctrl sequences, modified navigation, Tab, Escape, and key
release events travel through `send_key`. The Rust endpoint maps the generic
GLFW key-code/modifier representation to terminal control bytes, including
Ctrl+A/C, Ctrl+Backspace, Ctrl+Arrow, and Ctrl+Shift navigation, without
hard-coding one special chord in Java.

The terminal core records xterm mouse modes 1000/1002/1003/1006. Rust emits
one-based SGR or legacy mouse reports only when those modes are enabled, and
SFM maps panel coordinates to logical terminal cells for click, release, drag,
move, and wheel events. SFM also owns a 50 ms snapshot poller using the
server's sequence witness; unchanged snapshots carry no PNG payload, avoiding
repainting/uploading an unchanged full frame.

The hosted panel uses a 1.5-second triple-key escape hatch: the first two Esc
or Tab presses are forwarded to the terminal, the third Esc submits the panel
close intent, and the third Tab returns `false` so Minecraft can perform its
normal focus traversal. Deterministic Java tests cover the sequence boundary.

The focused Rust gates now pass: the CLI Vox puppet, key mapping tests, mouse
mode/encoding tests, and the terminal-core mouse-mode tests. Main/test Java
source compilation also passes. Commit `df80cb38d` fixed the Java frame-poller
timing and changed the automation hook to obtain a synchronous command frame;
the live capture
`build/sfm-toolchain/artifacts/game-test-preview/runs/sfm-title_screen-20260728-000407-517/title_screen_rust_terminal/1280x720_auto/figure_02_rust-terminal-powershell-cyan.png`
now visibly contains the Rust-rendered range and cyan `Write-Host` output.
The remaining proof is a live puppet that captures control-key editing,
mouse-mode delivery, streaming `1..10000` output, triple-Esc/triple-Tab
behavior, and disconnect/retry handling.

### User-testing follow-up — 2026-08-01

Manual testing found three presentation/correctness gaps to close before this
bridge can be treated as release-ready:

- The Rust PNG path still leaves the Java-local `> _` input strip below the
  blit. `SFMTerminalPanel` must give the Rust frame the full terminal content
  area and must not render or buffer a Java prompt for `SFMVoxTerminalService`.
- Triple-Esc closes correctly, but the user cannot discover the escape hatch.
  `SFMTerminalFocusSequence` should expose progress/remaining-window state so
  the panel can render a short status such as “Press Esc 2 more times within
  1.5 seconds to close.” The triple-Tab Java-focus escape should receive the
  corresponding cue without obscuring the terminal frame.
- These behaviors need a real panel/puppet regression proof in addition to
  the existing pure sequence tests. The status overlay must remain Java-owned
  presentation; it must not be sent to or baked into the Rust terminal PNG.

### Batch 1 resolution — 2026-08-01

The three presentation gaps above are now closed in the canonical 1.19.2
worktree. The Rust PNG path no longer renders the Java-local input strip; the
panel renders localized, Java-owned Escape/Tab progress overlays while Rust
remains authoritative for the PNG; and deterministic focus tests cover the
count, timeout, and gesture-clearing boundaries. The real
`title_screen_rust_terminal` puppet passed with four captures, including both
guidance states plus `1..100` and cyan `Write-Host` output. The captures are
under `platform/minecraft/build/sfm-toolchain/artifacts/game-test-preview/`
and `platform/minecraft/runGameTestPreview/screenshots/`.

The command-palette fuzzy-search fix is tracked by the release checkpoint
plan because it is a shared SFM action-surface issue, not a Vox transport
issue. Its deterministic JUnit test passed 2/2, and the real
`title_screen_command_palette` puppet passed with the live
`sfm action invoke open` result list captured. The broader bridge work remains
active: control/mouse/long-output/restart matrices and Rust scrollback are not
being declared complete by this Batch 1 slice.

### Batch 2 progress — machine-readable terminal content witnesses — 2026-08-01

Screenshot captures are now paired with bounded text artifacts so puppet
assertions do not depend on computer-vision interpretation. Facet's Terminal
contract has a `get_content` method returning the Rust-owned visible grid,
including the rendered prompt line, with a character bound and sequence
witness. The generated Java contract and Rust/Facet Phon round-trip tests pass;
the local Facet content-witness commit is `8c3c23c31` and has not been pushed.

Teamy Terminal implements the endpoint by draining the PTY and returning the
terminal core's visible text and OSC 133 prompt/command metadata. Its real
PowerShell puppet test asserts both `1..100` and `hello, world!` through
`get_content`. The local Teamy workspace temporarily uses sibling Facet paths
until that commit can be replaced by the intended immutable pushed revision.

The Vox key path now uses the existing ConPTY Win32 physical-key encoding and
honors separate key-down/key-up transitions. The server-side `cancel` RPC sends
an explicit Ctrl+C transition while retaining the session. A focused Teamy
test and the real SFM puppet both prove that a `1..10000` stream returns to the
profile's prompt before `10000` appears. The Rust service now launches the
interactive PowerShell profile so this proof matches the manually tested
terminal behavior; the user profile should keep PSReadLine history saving
disabled for automation accounts.

SFM exposes `writeTerminalContent(artifact, required, forbidden)` to game
puppets. It writes UTF-8 files under
`platform/minecraft/runGameTestPreview/terminal-content/` and fails the puppet
when a required witness is absent or a forbidden witness is present. The live
`title_screen_rust_terminal` run passed with artifacts for Ctrl+Backspace,
control navigation, Ctrl+V paste, Ctrl+C interruption, protocol cancellation,
`1..100`, and cyan output. The paste artifact contains the current profile
prompt and `pasted-through-ctrl-v`, proving that the Minecraft clipboard
shortcut reaches the Rust-owned PTY. The Ctrl+C and protocol-cancellation
artifacts contain fresh profile prompts and output only through 72/71
respectively; the `10000` forbidden-text assertions passed. The cancellation
puppet waits for the prompt to settle before reading the content witness so
the assertion covers the terminal state rather than a transient post-cancel
frame. The installed PATH
`teamy-terminal.exe` was refreshed from the local source so live SFM uses the
same contract revision as the Java jar. The live cancellation artifact also
proves the prompt is preserved (`❯` for the current Starship profile), rather
than treating screenshot interpretation as a cancellation witness.

The same live puppet now proves the remaining key/TUI behavior in the real
Java-to-Vox path. Ctrl+L clears the earlier `ctrl-l-before` command before the
`ctrl-l-after` witness is entered. The `ratatui-key-debug` executable renders
its alternate-screen `Key Events` frame, and a direct automation-only triple
Escape sequence is sent to the PTY so SFM's own third-Escape close gesture does
not intercept it; the restored artifact contains the profile prompt and
forbids `Key Events`. The direct key hook is test-only and does not change the
user-facing Escape/Tab focus behavior.

The Batch 2 interaction proof is now complete through the real Java-to-Vox
path. Paste is covered by the live clipboard shortcut witness. The mouse
contract uses an explicit `motion` boolean so drag/move events are distinct
from button transitions; this replaces the temporary button-value sentinel
and is covered by Teamy SGR drag assertions plus a Facet Phon round-trip
fixture. Facet commit `973318f72` is committed locally (not published or
pushed), imported through the supported SFM `--artifact-source` path, and
the lock now records its exact local artifact hash and provenance. The local
package wrapper still reports the known javac resource-cleanup exit-3 issue,
so that gate is not claimed as fully green.

The refreshed SFM compile and installed `teamy-terminal.exe` puppet pass with
`SFM_GAME_PUPPET_COMPLETE failed=0`. The live artifacts prove resize delivery,
mouse click/drag, wheel delivery, and alternate-screen restoration in addition
to Ctrl+Backspace, Ctrl navigation, Ctrl+L, Ctrl+C, paste, streaming output,
and cancellation. The puppet driver now exercises held-pointer motion through
the same `mouseMoved` dispatch Minecraft uses during a real drag.

The lifecycle proof is now complete as well. `SFMVoxTerminalService` rejects
stale poll completions, does not enqueue a generated disconnect during an
automation reconnect, and clones the connection options for each fresh Vox
connection so the prior connection's owned scheduler cannot be reused after
close. SFM owns only the Rust process it launches and restarts that process
through the explicit puppet action. The live
`title_screen_rust_terminal__rust-server-reconnected.txt` artifact contains
the Rust PowerShell prompt after Java-local REPL use, Rust launch, owned-server
stop, and reconnect. `contentForAutomation()` now waits briefly for a
nonblank Rust content witness or prompt metadata, preventing a normal empty
ConPTY startup frame from becoming a false failure. Rust scrollback remains a
later extension; this content witness is intentionally a bounded visible-grid
artifact rather than a scrollback replacement.

Final focused verification for this slice is green: Teamy Terminal's six
`vox_server` tests pass; SFM's `SFMTerminalFocusSequenceTests` pass 3/3; and
`SFMClientActionPaletteSuggestionTests` pass 2/2. The focused real puppet
passed with `failed=0 total=1`. The Facet motion Phon test is present but its
offline run remains blocked only by the uncached `astral-tokio-tar v0.6.4`
dependency. No repository was pushed, and Cloud Terrastodon was not changed.

### Phase 0 — Contract fixtures and capability matrix

- Record the schema in the Facet/Vox integration worktree and generate Java
  and Rust DTOs.
- Define protocol/capability negotiation, request bounds, cancellation,
  terminal state, semantic handles, and error categories.
- Add deterministic JSON fixtures for handshake, Java-local echo, terminal
  resize, key replay, malformed frames, cancellation, and disconnect.
- Complete the capability matrix for mount, disk editing, formatting, parsing,
  compiling, testing, auditing, and source builds. For each row record Java
  only behavior, optional Vox enhancement, prerequisites, failure behavior,
  and observable in-game proof.

### Phase 1 — Java-local terminal

- Implement the service/client pair in Java with in-memory transport.
- Implement the bounded virtual filesystem and deterministic command set.
- Add the composable terminal panel and command-palette action.
- Add replayable input events and snapshots so this mode can be tested without
  a live Rust process.
- Make bounded scrollback a first-class Java-local behavior: expose a stable
  scrollback sequence/witness or viewport offset, handle wheel/page/home/end
  navigation, preserve the user's scroll position while output arrives, and
  return to the live bottom on explicit follow-output input.
- Add deterministic tests for scrollback bounds, output while scrolled,
  resize/reflow while scrolled, and stale snapshot rejection.
- Integrate the mounted-disk adapter only after the virtual backend is stable;
  preserve the mount feature's Java-only operation.

### Phase 2 — Puppet-visible fallback proof

Create a puppet that proves, without Rust installed or reachable:

1. open the terminal from the command palette;
2. show Java-local status and a prompt;
3. run `pwd`, `ls`, and `cat` against a falsified in-memory tree;
4. edit a file and show the updated buffer/output;
5. generate enough output to exercise bounded scrollback, scroll away from the
   live bottom, receive more output, resize, and return to the newest row;
6. resize or place the terminal in nested panels; and
7. simulate an unavailable Rust endpoint and show a useful fallback message.

Capture at the supported viewport/GUI-scale matrix, including a narrow layout.
The report should include the backend status in captions and preserve enough
state to distinguish Java-local proof from Rust-connected proof.

### Phase 3 — Vox/Rust adapter

- Implement `SFMVoxBridge` endpoint lifecycle and the generated terminal
  service adapter against the frozen `org.facet:vox-java` artifact.
- Add the Rust-side Teamy Studio adapter that maps its terminal control-plane
  operations and frames into the portable schema.
- Add connected, authentication failure, schema mismatch, timeout, malformed
  frame, cancellation, reconnect, and clean shutdown states.
- Re-run the same puppet scenario against both Java-local and Rust backends;
  the screen and action model should not fork.

### Phase 3a — Texture presentation experiment

- Add a negotiated `texture-frame` capability alongside the structured-cell
  capability.
- Implement a bounded RGBA/BGRA frame or dirty-tile message, Java-side texture
  upload, sequence/lifetime checks, and resize requests.
- Adapt Teamy Studio's DirectX off-screen terminal renderer to produce the
  portable frame in a headless proof first; do not make Minecraft depend on a
  shared native GPU handle.
- Capture connected texture mode, input round-trip, resize, stale/disconnected
  frame handling, and Java-local fallback before considering native graphics
  interop.

### Phase 4 — Development-environment capabilities

Only after the narrow terminal loop is reliable, add optional Rust-backed
operations such as source compilation, audit, semantic symbol navigation,
editor handoff, and eframe prompts. Each action declares its prerequisites and
has a Java-local or disabled/fallback behavior. This is where the Teamy Studio
terminal's symbol/handle metadata can become useful to the in-game interface.

## Validation and safety

- Unit-test both transports against the same contract fixtures.
- Assert bounded frame sizes, output queues, scrollback, prompt text, paths,
  command lengths, and request lifetimes.
- Test cancellation and disconnect while a call is pending; no stale callback
  may mutate a closed Minecraft screen.
- Test Java-local behavior with no network, no Rust executable, and no mounted
  disk. These are supported modes, not error-only test cases.
- Keep the Rust endpoint loopback/authenticated and opt-in. Never accept a
  remote Minecraft UI object or arbitrary code execution request.
- Use the existing puppet viewport sweep and size-display leaf to inspect full,
  half, third, nested, smallest-Auto, and numeric-scale layouts.
- Keep the mod's ordinary gameplay, mounted editing, and Java-local terminal
  usable when Vox is unavailable.

## Deferred choices

- whether the first Java-local terminal should expose only a virtual shell or
  also a carefully allowlisted local-disk shell;
- whether the terminal should initially render a cell grid or a simpler text
  view before the semantic frame surface is complete;
- whether symbols/handles are emitted in every frame or queried separately;
  and
- whether eframe is needed for the first Rust terminal proof or can remain a
  later external presentation surface.

The colour-picker bridge is therefore deferred as the first cross-language
demo. The reusable colour-input header-slot refactor may still land as local UI
infrastructure, but terminal lifecycle, Java-local fallback, and one useful
Rust-backed operation are the next user-visible bridge milestones.
