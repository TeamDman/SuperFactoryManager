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
   Vox client and Rust is the service, using Teamy Studio's terminal engine or
   another Rust implementation. It may provide a real shell, richer VT
   behavior, semantic prompt/symbol information, compilation, audit, and other
   repository tooling.

The first two modes must remain useful when Rust is not installed, the game is
offline, the endpoint is stopped, authentication fails, or the protocol is
incompatible. A failed optional connection produces a visible status and a
fallback or launch suggestion; it must not disable mounted editing or ordinary
SFM gameplay.

## Boundary and ownership

Java owns all Minecraft-facing concerns:

- the terminal panel, multiplexer allocation, focus, keyboard/mouse routing,
  clipping, theme, accessibility text, and GUI-scale behavior;
- the local service implementation and virtual/local filesystem policy;
- validation of every inbound frame, bounded buffering, thread handoff through
  the Minecraft executor, and terminal lifecycle shown to the player; and
- graceful fallback when the optional endpoint is absent or unhealthy.

Rust owns optional development-environment concerns:

- a real process-backed shell or Teamy Studio terminal engine;
- VT parsing, terminal screen state, cursor/style state, scrollback, replay,
  and semantic prompt/symbol/handle metadata;
- repository/compiler/audit commands that are inappropriate for the Java-only
  gameplay runtime; and
- richer external interfaces such as eframe, when the user explicitly asks
  for them.

The wire contract carries portable data and intent only. Rust never sends a
Minecraft `Screen`, panel, widget tree, renderer callback, arbitrary layout
instruction, or Java object reference. Java renders the terminal locally,
whether the service is Java-local or remote.

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

## Optional Rust-rendered texture presentation

The preferred Rust-backed presentation experiment is a texture stream rather
than reimplementing Teamy Studio's renderer in Java. Teamy Studio can render
its terminal into an off-screen target using its existing DirectX/font
pipeline; Java then owns a Minecraft texture resource and blits the received
frame inside a terminal panel. Keyboard, mouse, resize, focus, and paste events
travel in the opposite direction through the same session.

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
- Integrate the mounted-disk adapter only after the virtual backend is stable;
  preserve the mount feature's Java-only operation.

### Phase 2 — Puppet-visible fallback proof

Create a puppet that proves, without Rust installed or reachable:

1. open the terminal from the command palette;
2. show Java-local status and a prompt;
3. run `pwd`, `ls`, and `cat` against a falsified in-memory tree;
4. edit a file and show the updated buffer/output;
5. resize or place the terminal in nested panels; and
6. simulate an unavailable Rust endpoint and show a useful fallback message.

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
