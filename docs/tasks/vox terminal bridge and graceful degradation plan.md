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

## User-testing follow-up — 2026-08-02

The earlier immediate SFM bridge slice was a presentation-state correction,
not a renderer optimization. The release-checkpoint evidence later in this
document records its completion. The terminal panel exposes two explicit states:

- disconnected: show status plus Start/Retry, with no stale Java-local REPL
  help text in the Rust panel; and
- connected: show only the Rust-authoritative frame and route input/resize to
  Vox.

Register the terminal and the puppet size-display surface as panel scenes so
`sfm:panel/open sfm:terminal` and the layout witnesses use one action surface.
The Java-local `sfm:repl/open` path remains independently available.

The GUI-scale-7 blur report establishes a separate frame-contract item: the
requested `columns × rows` is stable while Rust receives the panel's logical
dimensions and chooses larger font/cell pixel dimensions. Java presents the
resulting native-sized frame rather than stretching a smaller PNG. The current
CPU `fontdue` path is sufficient for this correctness proof. Teamy Terminal
owns the later slug/GPU renderer and dirty-upload optimization; those are not
part of the immediate panel/action goal.

Native-size/cell-metric negotiation, the correlated multi-second lag witness,
retained font/glyph/frame/texture resources, bounded Rust-to-Java Vox `Tx`/`Rx`
frame delivery, and V-4.2a's three named CPU presentation transports are now
complete. The next goal-sized vertical slice is Teamy Terminal 3.6.2c and
3.6.4a–3.6.4e together with SFM V-4.2c: add the true no-window
`rust-gpu-slug` backend, migrate the contract from a transport-only generation
to an atomic renderer/transport presentation generation, and select the two
axes independently from a focused SFM terminal panel. V-4.2b semantic cells
and V-4.3 Java font renderers remain independent later work. CPU/full-PNG stays
the initial default until V-4.5/V-4.6 compare visual and temporal evidence.
Teamy Terminal Phase 3.6 remains authoritative for Rust renderer work; the SFM
items below own Java capability consumption, panel controls, presentation
resources, and end-to-end proof.

### Batch 3 planning items

- **[x] V-3.1:** implement and test disconnected/connected terminal presentation,
  including Start/Retry and stale-frame rejection;
- **[x] V-3.2:** expose terminal and size-display scenes through the panel registry;
- **[x] V-3.3:** document and test the logical-size/cell-metric frame contract at
  normal scale and GUI scale 7, asserting stable columns/rows, larger Rust
  font/cell pixels, native frame dimensions, and no Java bitmap upscaling; and
- **[ ] V-3.4:** execute the evidence-driven renderer/transport comparison
  detailed below. Rust scrollback remains a separate functionality slice.

## Terminal performance and rendering comparison program — 2026-08-02, revised 2026-08-03

The motivating user-visible symptom was a multi-second delay in the
Rust-backed Minecraft terminal. The correlated V-4.1/V-4.4 witness identifies
repeated Rust font discovery/renderer construction as the dominant measured
current-path stage, while Java decode/texture/presentation was low-millisecond
in the sampled run. That evidence authorizes the V-4.1a/V-4.1b baseline
repairs; it does not excuse later candidates from measuring Rust frame
generation, GPU or CPU readback where applicable, PNG encoding, Vox
transport/backpressure, Java PNG decoding, `NativeImage` allocation,
Minecraft texture upload, render-thread scheduling, scaling, and final panel
presentation.

The comparison must vary renderer and transport independently:

| Renderer | Required font variants | Required transport comparisons |
| --- | --- | --- |
| Rust CPU `fontdue` | Caskaydia Cove Nerd Font Mono | full PNG baseline, full raw pixels, dirty pixels/tiles where supported |
| Rust Vulkan slug | the same named/versioned Caskaydia face | GPU readback plus the same portable pixel transports; no assumed native-handle sharing |
| Java/Minecraft text | vanilla Minecraft font and Caskaydia Cove Nerd Font Mono | bounded semantic terminal cells/damage, never lossy plain transcript text |

Here `renderer` always means the final rasterizer. A renderer capability has
non-selectable `rasterization_owner`: `server` means Teamy Terminal/Rust creates
pixels before Vox, while `client` means SFM/Java creates pixels after receiving
semantic cells. Ownership is not a third dropdown and must not be inferred from
an id prefix. The selected transport determines the bounded wire payload, and
capability tuples enforce compatibility: Rust CPU/GPU renderers use pixel
transports; Java font renderers use semantic cells. Java's texture upload/blit
path for Rust pixels is a presenter, not a Java renderer.

The Java Caskaydia comparator must use the same face version and verify its
font bytes/hash and license provenance. If Minecraft 1.19.2 and later versions
require different font-provider setup, isolate that through the normal
version-dependent adapter boundary. Java's vanilla-font result remains a
first-class comparison even though its glyph geometry and appearance will not
be pixel-identical to Caskaydia.

### [x] V-4.1 Reproduce and correlate the current lag

Add a correlation id and terminal/frame sequence that can be followed from a
Java input or expected-output marker through Rust input receipt, PTY/VT update,
snapshot/damage, rasterization, encode/send, Java receive/decode/upload, panel
render, and the first presented frame containing that sequence. Record local
monotonic stage durations and Java-observed end-to-end latency; do not subtract
unsynchronized process clocks. Include queue depth, poll wait, dropped,
coalesced, stale, and superseded frames.

The reproducer must cover cold/warm caches, idle and prompt typing,
`1..100`, `1..10000 | Out-Host`, cyan ANSI output, scroll flood,
alternate-screen restoration, resize, full-screen/split/narrow panels, normal
GUI scale, and GUI scale 7. Run repeated samples. If an unattended puppet does
not reproduce the multi-second delay, add typed capture start/mark/stop actions
and use the exact manual interaction to produce the same manifest. This item
is not complete until the perceived delay can be pointed to in milliseconds
and associated with one or more measured stages.

**Completion notes — 2026-08-02:** The Rust CPU/full-PNG path now emits bounded
`SFM_TERMINAL_TIMING_WITNESS` data with request/server sequences, correlation
IDs, logical/native dimensions, payload size, and local stage durations. The
focused Rust witness measured a 2.853889-second snapshot at 1200×760 / 120×40;
cold font loading accounted for 2.278390 seconds, with 136.599 ms rasterization
and 393.669 ms PNG encoding. The refreshed SFM bridge carries the same
correlation vocabulary through Vox polling and logs both
`SFM_VOX_TERMINAL_TIMING` and `SFM_TERMINAL_PRESENTATION_TIMING` records. A
clean headless `sfm:title_screen_rust_terminal` puppet passed at normal scale,
covering the prompt, control keys, paste, Ctrl+C interruption, `1..100`, cyan
output, alternate-screen restoration, reconnect, RPC cancellation, and
triple-Escape close. The live records show Rust totals around 0.75–1.1 seconds
with font loading around 0.7–1.0 seconds, while Java PNG decode, texture
allocation/registration, and render/present work are low-millisecond samples.
The high-scale run reached effective GUI scale 7 with native `547×303` panel
metrics and crisp screenshots. The later retained-resource acceptance run
corrected the inaccurately named one-minute whole-puppet watchdog and completed
all 13 captures at 4K. Rust and Java clocks are never subtracted as if
synchronized.

### [x] V-4.1a Consume a Vox Tx/Rx frame subscription and remove live polling

Replace `SFMVoxTerminalService`'s `FRAME_POLL_MILLIS` scheduler with the typed
Facet/Vox terminal frame subscription defined by Teamy Terminal 3.6.2b. The
expected direction is a `Tx<TerminalFrameEvent>` method argument: Java creates
the channel pair, passes the sending endpoint to the Rust handler, and consumes
the paired `Rx`. Facet's Java runtime currently rejects request channels as
outside its unary slice, so the reviewed Java channel runtime,
credit/backpressure tests, generated terminal binding, packaged JAR, and
immutable SFM toolchain pin are prerequisites to this integration.

Consume frames on a dedicated receiver executor. Validate connection/session
epoch, monotonically increasing terminal/frame sequences, negotiated bounds,
payload kind, and correlation metadata before retaining only the newest frame
awaiting render-thread presentation. A new subscription must begin with a full
latest-state frame; disconnect/reconnect closes the old request-scoped channel,
rejects late old-epoch frames, opens a fresh subscription, and receives a full
resynchronization frame. Do not model raw Vox channels as durable streams.
Keep unary `snapshot` available for explicit screenshot, diagnostic, and
resynchronization calls, but steady-state telemetry and tests must show zero
periodic snapshot requests.

Rust begins publication immediately when its PTY/session actor mutates the VT
sequence. There is no debounce/minimum frame interval to tune to zero. The
producer permits one render/send in flight and one newest pending sequence;
credit pressure or a slow Java/render thread coalesces obsolete intermediate
states instead of queueing full PNGs. Preserve counters for mutation-to-send,
credit wait, pre-render coalescing, Java receive/supersede, render scheduling,
and first presentation so this result remains comparable to V-4.1/V-4.4.

**Validation:** The real endpoint must push output with no further Java input
or unary frame call, show intermediate and final states during a long command,
remain idle without a polling loop, and stay bounded under a deliberately slow
receiver. Cover resize, Ctrl+C, alternate screen, cancellation, channel drop,
server restart, full reconnect/resync, old-epoch rejection, and two sessions.
The normal and high-scale puppets retain machine-readable content/screenshots
and demonstrate lower change-to-present latency than the measured polling
baseline without unexplained drops.

**Completion evidence — 2026-08-03:** Facet commit
`4d62d2cfb211f48f89ca4a06c466ca8fe452ca03` supplies the packaged Java channel
binding and complete Rust-authoritative nested schema closure; the lockfile pins
that exact source revision and BLAKE3 package hash
`14d192241617c123a37b37434b7f0bfbf681eb3e`. Teamy Terminal commit `7117b00`
supplies immediate actor-owned publication, one render/send in flight plus one
newest pending sequence, full resynchronization, channel/epoch ownership, and
bounded telemetry. SFM consumes `Rx` on a dedicated receiver, validates
connection/session identity and monotonic sequences, retains only the newest
render handoff, performs input/resize/cancel calls without blocking the Vox
driver, and tears down replaced subscriptions outside the connection lock.

Focused SFM terminal service, frame-identity, inbox, and telemetry tests pass.
The normal and 3840×2130 GUI-scale-7 real puppets both pass output-without-input,
`1..10000`, Ctrl+C, resize, alternate-screen restoration, mouse/drag/wheel,
cancellation, channel drop, server restart/reconnect, and triple-Escape gates.
Normal records zero snapshots/polls, two subscriptions, one closed old channel,
397 accepted events, 393 delivered Java frames, four supersedes, 34 completed
pushes, 119 pre-render coalesces, and maximum producer pending depth one.
GUI-scale 7 records zero snapshots/polls, two subscriptions, one closed old
channel, 399 accepted events, 397 delivered Java frames, two supersedes, 32
completed pushes, 125 pre-render coalesces, maximum pending depth one, and a
4,321-microsecond mutation-to-send sample. Both report zero rejected events.
The reproducible commands, counters, checks, revisions, and artifact locations
are preserved in
`docs/architecture/evidence/vox-terminal-push-delivery-1.19.2.json`.

### [x] V-4.1b Retain compatible Java presentation resources

Apply Teamy Terminal 3.6.2a's lifetime rules at the Minecraft boundary. Keep a
single registered `DynamicTexture` and compatible upload storage across frame
sequences; update/upload it in place when dimensions and format are unchanged.
Replace and close it exactly once only when resize, format, backend, or context
loss requires a new allocation. If PNG decoding still requires a temporary
`NativeImage`, bound and close that object explicitly and retain separate
decode/allocation versus texture-registration telemetry. An A→B→A resize must
not leak registrations and should reuse bounded compatible storage where the
Minecraft API safely permits it.

The Rust side concurrently retains process-wide font discovery/face data,
size-specific `TerminalFont`/glyph caches, frame buffers, and PNG scratch
capacity as specified by Teamy Terminal 3.6.2a. SFM acceptance consumes a
pinned artifact containing that implementation and records cold/warm cache
identity and counters; it must not infer reuse merely from lower elapsed time.

**Validation:** Stable-size received frames do not increase dynamic-texture
registration/allocation once per sequence; replacement and close counts match
dimension/context changes; visual output, stale-frame rejection, and panel
clipping remain unchanged. The correlated warm run reports no repeated Rust
font discovery/renderer construction and no Java texture churn, including
normal scale, GUI scale 7, split panels, reconnect, and A→B→A resize.

**Completion evidence — 2026-08-03:** Commit `ce8c53f44` retains one unique
registered `DynamicTexture` per panel renderer, updates/uploads compatible
dimensions in place, grows and reuses the off-heap encoded-PNG buffer, closes
decoded `NativeImage` temporaries explicitly, and replaces/closes resources
exactly once when dimensions or the then-current backend identity change.
V-4.2c supersedes that conservative CPU-only identity rule: a renderer switch
may reuse the texture when dimensions and pixel format remain compatible.
Focused lifecycle
tests cover stable size, A→B→A, buffer growth/reuse, backend changes, exact
closes, telemetry, and split-panel texture identities. Commit `343f10f72`
correlates Java presentation counters with Rust font/raster/encode stages.

The installed Teamy Terminal executable is the clean pinned implementation at
`1c5fd0d`. Normal puppet run `sfm-title_screen-20260803-112610-670` passes with
139 uploads, two allocations/registrations for the deliberate resize, and 137
in-place texture reuses; 137 warm samples report `rust_font_load_us=0` and the
two cold samples are the initial and intentionally restarted Rust processes.
GUI-scale-7 run `sfm-title_screen-20260803-113254-252` passes at 3840×2130,
effective scale 7, native 547×303 panel pixels, 13 inspected captures, and all
machine-readable terminal-content assertions. It records 134 uploads, two
registrations, 132 reuses, and 132 zero-font-load samples. Commit `4c2846618`
waits for Windows listener teardown before starting the replacement process,
proves distinct ready PIDs across reconnect, and gives screenshot-heavy 4K
puppets an honest two-minute whole-run watchdog without relaxing their
individual action bounds. The preserved evidence logs are
`retained-resources-{normal,scale7}-console.log` under the terminal-guide
artifact directory. Those runs are the historical polling baseline; V-4.1a's
later completion evidence above proves that the 50 ms poller has been removed.

### [x] V-4.2a Introduce CPU presentation transports and the panel selector

**Completion notes — 2026-08-03:** SFM commit `3a4255860` adds a typed,
bounded Java raster validator/compositor plus retained full-raw and dirty-raw
presentation alongside the existing full-PNG presenter. The focused-panel
`sfm:terminal/transport/set <transport-id>` action and visible keyboard
dropdown share one panel-local transition path. Requested and active transport
remain distinct until a fresh full-resync frame from a new generation is
accepted; the PTY, input stream, and last accepted image remain live while the
replacement subscription starts. Closed/replaced targets and unsupported
choices fail explicitly, retired-generation and base-mismatched frames are
rejected, and two panels retain independent subscriptions.

Facet/Vox commits `fa54d1ec`, `ae3bd147`, `a02161cc`, and `71fc22e9d` provide
the typed raster schema, lane-scoped bindings, and isolated sibling raster-lane
lifecycle. Teamy Terminal commits `0d9d10d`, `8722dda`, `a4be3d6`, and
`008cc64` provide the three push transports, retained renderer resources,
canonical dirty composition, initial full resync, PNG stride correction, and
headless evidence. SFM pins the resulting `org.facet:vox-java:0.10.0-rc.5`
artifact at BLAKE3 prefix `7e30d059e0b53ec54b02517b504d5fadbcd60d32`
in canonical lock schema 4. Default Gradle remains Rust-optional because the
panel's automation proof is exposed through the dependency-free remote-service
interface rather than directly naming the Vox implementation.

The real-bridge puppet passes at normal scale in run
`sfm-title_screen-20260803-182559-878` and at GUI scale 7 in run
`sfm-title_screen-20260803-182901-119`. Each durable run manifest records four
captioned screenshots and separate hashed terminal-content/push-evidence files
for full PNG, full raw RGBA, dirty raw RGBA, and the independent right panel.
All checkpoints report zero rejected frames, every replacement observes a full
resync, the one stale frame is the expected retired-generation race, and
producer pending depth is at most one. The GUI-7 witness uses a 3840×2130
framebuffer, effective scale 7, and native logical 549×305 panel dimensions;
visual inspection found crisp glyphs without Java bitmap stretching. Terminal
content proves session preservation, complete `1..100` dirty output, and an
independent right-panel command.

Machine-readable evidence is checked in at
`docs/architecture/evidence/vox-terminal-raster-transports-1.19.2.json`; the
matching Teamy headless proof is
`docs/evidence/rust-cpu-raster-transports-3.6.3.json`. Validation passed with
`sfm-propagate-changes.exe test run --branch 1.19.2`,
`sfm-propagate-changes.exe dependency migrate --check --branch 1.19.2`, the
SFM CLI `check-all.ps1` gate (364 passed, one ignored), contributor-default
`compileJava compileGameTestJava`, Teamy core (15), CLI (36 plus integrations),
font (11), and frame (6) tests. Two unrelated symlink JUnit cases abort on
Windows when the process lacks symbolic-link privilege; the canonical SFM test
command still exits successfully.

Keep the existing Rust-authoritative terminal session independent from the
presentation backend. Add an explicit Java panel presentation interface whose
implementations consume `full-png`, `full-raw-rgba`, or `dirty-raw-rgba` from
Teamy Terminal 3.6.3. Renderer, raster/damage mode, and transport names must
appear independently in configuration, capability negotiation, screenshots,
content artifacts, traces, and result manifests; unsupported combinations fail
clearly instead of silently falling back and spoiling a comparison. Full and
dirty raw payloads validate the versioned RGBA8 layout, dimensions, row stride,
alpha interpretation, frame/base sequence, region bounds, and ordered patch
application before touching presentation resources.

Every Rust-backed SFM terminal panel has a visible **Transport** dropdown. Each
panel owns its remote service/subscription, so this is a panel-local selection:
two terminal panels may deliberately use different transports for comparison.
The dropdown is populated from the intersection of cached server-advertised
capabilities and Java presentation implementations, uses stable transport ids,
and labels unsupported choices with a reason rather than silently selecting a
different path. Full PNG remains the initial compatibility default unless the
client configuration names another supported default.

The UI distinguishes **requested** from **active** transport. While disconnected,
the dropdown chooses the desired transport for the next connection. While
connected, changing it preserves the Rust session/PTY and visible last accepted
frame, performs a bounded request-scoped resubscription, waits for a complete
resynchronization frame carrying the requested transport id and a fresh
presentation generation (renamed from the historical transport-only field by
V-4.2c), and only then reports the choice as active. Late frames
from the retired generation are rejected. Selection must not block Minecraft's
render thread, restart the shell, lose input, or affect another terminal panel.
Failure leaves an explicit error and requested/active state instead of claiming
the switch succeeded.

Back the dropdown with the hierarchical command-palette/automation action
`sfm:terminal/transport/set <transport-id>`. Resolve its target from the
workspace's focused slot and the top/active panel in that slot at invocation
time. The action is contextually available only when that exact panel exposes a
Rust-terminal transport action target; a Java-local REPL, a non-terminal panel,
an obscured panel lower in the focused stack, or no focused workspace panel is
not eligible. Direct invocation in an ineligible context fails with an explicit
"focused panel is not a Rust terminal" result rather than choosing another
terminal or doing nothing. Capture the target panel identity before beginning
the asynchronous switch; if that panel is closed or replaced, cancel/ignore the
completion instead of applying it to the panel that later gains focus.

The `<transport-id>` suggestions come from that captured panel's cached
capability intersection. Dropdown and action use the same target model and
transition path; clicking the dropdown focuses its panel, while command
invocation does not otherwise move focus. Puppets first focus the intended
panel and then invoke the action, without automating mouse coordinates.
Candidate enumeration and tooltip rendering must not perform synchronous Vox,
filesystem, font, or renderer construction work. Tests cover contextual action
availability, direct-invocation failure, focused stacked-panel resolution,
target capture across asynchronous focus/close changes, keyboard operation,
panel-local independence, disconnected selection, successful switch and full
resync, failed/unsupported selection, stale-generation rejection, and
requested-versus-active artifact/telemetry fields.

**Completion criteria:** The focused-panel dropdown/action can switch among all
three CPU transports through the real bridge without restarting the PTY, losing
input, stretching pixels, affecting another panel, or accepting a stale/base-
mismatched frame. Headless composition and normal/GUI-scale-7 puppets prove
full-versus-dirty pixel equivalence and record comparable renderer, damage,
transport, byte, queue, cache, upload-region, and stage-timing manifests.

### [ ] V-4.2b Define the semantic-cell transport contract

Semantic transport is not a string transcript. It must preserve bounded cell
content, foreground/background, bold, underline, inverse, width/continuation,
cursor shape/location, selection, full-refresh/damage regions, logical grid,
cell metrics, session, and sequence. Rust remains authoritative for PTY, VT,
scrollback, terminal modes, and damage semantics. Java owns only the chosen
Minecraft presentation and input/layout integration.

The server advertises the semantic-cell source contract, not fake Java
renderer implementations. SFM intersects that source capability with its
locally registered client-owned renderers to form valid presentation tuples
such as `java-vanilla + semantic-cells`. Plain transcript text is never an
acceptable substitute.

### [ ] V-4.2c Add panel-local renderer selection and prove the GPU bridge slice

This item depends on completed V-4.2a and Teamy Terminal 3.6.2c plus
3.6.4a–3.6.4e; it does **not** depend on V-4.2b. It is complete only when the
new GPU renderer is usable through the actual Minecraft panel, not merely
advertised by a server fixture.

#### Contract and Java model

Consume the migrated Facet/Vox raster contract with
`default_renderer_id`, `default_transport_id`, and one
`presentation_generation` guarding the complete `renderer_id` +
`damage_mode_id` + `transport_id` + `transport_version` tuple. Repin the
canonical toolchain lock to the reviewed immutable Facet revision and exact
Vox Java artifact hash, regenerate/import through the supported dependency
flow, and keep contributor-default Gradle/IDE synchronization Rust-optional.
Ordinary Java compilation must neither require Cargo/Teamy Terminal/Vulkan nor
connect to a Rust server; Vox-specific generated types stay behind the
existing optional/reflection boundary.

Consume and preserve each capability's closed `rasterization_owner` value
(`server` or `client`) in typed state, telemetry, captions, and artifacts.
Reject unknown ownership and invalid owner/transport combinations without
falling back or deriving behavior from renderer names. This V-4.2c slice
advertises and selects only the two server-owned Rust renderers. V-4.2b/V-4.3
later add semantic-cell source capabilities and combine them on the client with
locally implemented `java-vanilla` and `java-caskaydia` renderers; the Rust
server must not pretend to implement or advertise those Java rasterizers.

Replace the current transport-grouped/first-mode selection logic with typed
renderer and presentation models (for example `SFMTerminalRendererId` and
`SFMTerminalPresentationModeOption`). Remove the hard filter that accepts only
`rust-cpu-fontdue`; otherwise duplicate transport ids advertised by CPU and
GPU would collapse to an arbitrary first mode. Cache all valid advertised
tuples and the reason a known renderer or tuple is unavailable. `renderer_id`
is the canonical new-raster identity in service state, logs, captions, and
artifacts. Keep legacy `backend_id` handling only where an old snapshot API
still requires it.

Consume Facet's separate `unavailable_presentations` collection alongside the
valid `modes`. Match each diagnostic by the exact renderer/owner/damage/
transport/version tuple, retain its typed error code/message/retryability in
the in-memory catalog, and use that cached reason for disabled selector labels,
request rejection, command-palette suggestion tooltips, telemetry, and puppet
artifacts. The valid-mode list must not contain disabled/fake tuples, and a GPU
probe failure must not fail the complete capabilities request or hide the
three usable CPU tuples. The generic “not advertised by the server” reason is
reserved for combinations for which the server supplied neither a valid mode
nor an explicit negative diagnostic. No UI/candidate path repeats the probe or
performs Vox I/O.

Renderer and transport are independent user choices but one atomic service
transition. Changing either axis combines the new value with the panel's
currently requested value on the other axis, validates the resulting tuple,
and requests one fresh presentation generation. If rapid choices overlap,
serialize/cancel obsolete transitions so only the newest requested tuple may
become active. Unsupported combinations, closed targets, connection changes,
GPU initialization/render errors, and timeouts preserve the old active tuple
and last accepted image and expose an explicit requested-versus-active error;
they never silently select CPU, another transport, or another panel.

The first accepted event for a new tuple must be a complete resynchronization
frame carrying that exact renderer, transport, and generation. Reject retired
generation, wrong-renderer, wrong-transport, non-monotonic, malformed, and
dirty-without-valid-base frames before touching presentation resources. A
successful CPU→GPU→CPU cycle preserves the Rust session/PTY, input,
scrollback, prompt, terminal sequence, and alternate-screen state. Compatible
RGBA dimensions/format may reuse the panel's Java texture/upload resources;
do not allocate or register a new texture solely because `renderer_id`
changed. Replacement and closure remain exact when dimensions/format truly
change.

#### Panel and action surface

Evolve the existing compact Transport control into one keyboard-accessible
**Presentation** menu/popover containing two explicit, non-flattened selectors:

- **Renderer:** `rust-cpu-fontdue` or `rust-gpu-slug`, showing requested and
  active values while a transition is pending; and
- **Transport:** `full-png`, `full-raw-rgba`, or `dirty-raw-rgba`, likewise
  showing requested and active values.

Do not present the six cross-product tuples as a flat list and do not add
`rust-gpu-slug` to the Transport list. Populate both selectors from the
captured panel's cached capability tuples intersected with Java pixel
presenters. Disable unavailable values with an actionable reason. While
disconnected, retain the desired tuple for the next connection and show
availability as cached/unknown honestly. Clicking the control focuses that
panel; keyboard use must not leak terminal input or require desktop UI
automation.

Add the hierarchical action
`sfm:terminal/renderer/set <renderer-id>` alongside the existing
`sfm:terminal/transport/set <transport-id>`. Both actions resolve the focused
workspace slot and its top/active Rust-terminal panel, capture that exact panel
identity before asynchronous work, use the same transition method as the UI,
and leave focus otherwise unchanged. A Java-local REPL, non-terminal panel,
obscured panel lower in a stack, closed/replaced target, or absent focused
panel fails explicitly rather than finding a different terminal. Suggestions
and tooltips use cached bounded capabilities only: no synchronous Vox call,
filesystem/font scan, Vulkan initialization, or renderer construction may run
on command-palette candidate enumeration or Minecraft's render thread.

#### Expected compatibility matrix

On a server that advertises a usable Vulkan device, the vertical slice must
exercise this matrix through the same Java pixel-presentation interfaces:

| Renderer | `full-png` | `full-raw-rgba` | `dirty-raw-rgba` |
| --- | --- | --- | --- |
| `rust-cpu-fontdue` | required/current reference | required/current reference | required/current reference |
| `rust-gpu-slug` | required | required | required |

If implementation discovers a genuine technical reason one GPU tuple cannot
be supported, stop and update this plan with evidence before reducing the
matrix; omitting it at runtime without a reviewed plan change does not satisfy
the goal. No native GPU-handle sharing is part of this slice: GPU results use
bounded portable PNG/RGBA readback so transport remains independently
measurable.

#### Tests, puppet, and evidence

Add deterministic Java tests for capability parsing/defaults, tuple
intersection without duplicate collapse, explicit rasterization ownership,
unknown-owner and owner/transport rejection, renderer and transport suggestions,
focused/stacked-panel targeting, target capture across focus/close races,
disconnected selection, overlapping transitions, requested-versus-active
state, unsupported/unavailable choices, CPU→GPU→CPU full resync, stale/wrong
tuple rejection, compatible texture reuse, exact resource closure, and two
panels with independent renderer/transport choices. Injected capability/device
failures must prove explicit unavailable/error UI and no silent fallback.

Extend the real Rust-terminal puppet at normal GUI scale and supported GUI
scale 7. It must:

1. start `teamy-terminal serve` without a visible native Teamy window or
   taskbar entry and connect through the existing lifecycle action;
2. render one deterministic styled fixture and PowerShell output through all
   six matrix combinations, preserving machine-readable terminal text/content
   witnesses independently of screenshot interpretation;
3. perform CPU→GPU→CPU in one panel, proving one retained prompt/session and
   accepted full resync for each new presentation generation;
4. keep a CPU terminal in one panel and a GPU terminal in another, then send
   independent commands and prove no cross-panel selection or frame leakage;
5. cover `1..100`, cyan `Write-Host`, resize, split panels, alternate screen,
   wide/Unicode/fallback and the difficult slug glyph sheet, with native panel
   dimensions and no Java bitmap stretch; and
6. capture the unavailable-GPU presentation through a deterministic injected
   test seam when the live machine has Vulkan, while the live GPU path itself
   must pass on a supported Windows/Vulkan machine.

Each run-local manifest records SFM, Teamy Terminal, Facet, generated artifact,
font, GPU/driver, and shader revisions; requested/active renderer and transport;
presentation generation; terminal/frame sequences; native/grid/cell metrics;
full/dirty bytes and regions; Rust geometry/band/cache, GPU submit/completion,
readback, encode/pack, Vox credit/send timings; and Java receive/decode,
allocation/reuse, upload, scheduling, present, stale/drop/coalescing counters.
Keep screenshots, content artifacts, push telemetry, and cold/warm resource
evidence together. CPU-versus-GPU exact pixels are not required, but each
backend must be deterministic against itself and direct-full versus composed-
dirty must match exactly for that backend; cross-renderer assertions cover
semantic content, dimensions, style/color, glyph occupancy, known slug
artifacts, and bounded perceptual difference.

Run the canonical SFM compile/test/dependency checks through
`sfm-propagate-changes.exe`, the CLI `check-all.ps1` gate when CLI code or lock
projection changes, and the focused/live puppet. Update the gameplay changelog
for the new Presentation selector and GPU renderer. Do not run Gradle directly.
Do not propagate to newer Minecraft branches, publish a release, push
repositories, alter Teamy Studio, implement semantic cells/Java renderers, or
choose a new default in this goal; those are explicit later integration steps.

**Completion criteria:** From a focused SFM terminal panel, the user can
independently select CPU or true no-window GPU rasterization and any of the
three pixel transports through the Presentation UI or typed actions. Switching
preserves the Rust-authoritative session, panels remain independent, stale and
failed transitions are safe and visible, all six supported tuples have durable
normal/GUI-scale-7 evidence, contributor-default Java development remains
Rust-optional, and the resulting telemetry is sufficient for V-4.5/V-4.6 to
compare rather than guess. Rasterization ownership is explicit metadata rather
than a third selector, and CPU/full-PNG remains the default.

### [ ] V-4.3 Implement the two Java text/font comparators

Implement one semantic-cell renderer using Minecraft's vanilla font and one
using Caskaydia Cove Nerd Font Mono. Both consume the same terminal-cell
snapshot and must support colors/styles, cursor/selection, wide and combining
cells, fallback/missing glyphs, clipping, native panel dimensions, GUI-scale
changes, and damage/full-refresh behavior. Preserve the Caskaydia font license
and record the exact bytes/version used by Rust and Java.

Do not optimize by dropping terminal information or by turning styled cells
back into lines of plain text. The Java renderers are performance and visual
comparators and may become a production choice only after the matrix; they do
not change Rust's authority over terminal state.

### [x] V-4.4 Instrument Minecraft decode, upload, and presentation

Add bounded Java-side timing and counters for snapshot polling and wait,
payload bytes, PNG or raw decode, `NativeImage` and other allocations, texture
creation versus reuse, texture registration/update/upload, dirty upload area,
render-thread queue delay, panel draw CPU time, Minecraft frame interval,
garbage collection where observable, dropped/stale/coalesced frames, and the
last terminal sequence actually presented. Distinguish CPU submission from GPU
completion where an OpenGL timing/query seam is practical; otherwise label the
measurement honestly.

The current `SFMTerminalPngRenderer` decodes a new PNG and registers a dynamic
texture when a sequence changes, so decode/allocation/registration/upload and
bitmap scaling are explicit hypotheses alongside `fontdue`. Measure them
before introducing texture reuse, raw/dirty uploads, or scheduling changes,
then retain matched evidence for each accepted change.

**Completion notes — 2026-08-02:** `SFMTerminalPngTelemetry` bounds render, PNG
decode, dynamic-texture allocation/registration, upload, stale/drop,
coalesced, and presented-sequence counters with total/max durations.
`SFMVoxTerminalTelemetry` separately bounds poll, Vox wait, failure/timeout,
stale/drop/coalesced, and latest native-frame metadata. The Java logger now
emits `SFM_TERMINAL_PRESENTATION_TIMING` when a new correlated frame is
presented, including those cumulative/max Java stage timings and the last
presented sequence. The live normal-scale puppet produced zero upload failures
and zero dropped/coalesced frames in the sampled presentation records; for
example, a frame with `rust_total_us=853658` had cumulative Java PNG decode of
803 µs, texture allocation of 485 µs, registration of 66 µs, and bounded
render timing. The implementation compiles, focused tests pass, and the live
runtime evidence is recorded without making a GPU-completion claim that the
current OpenGL seam cannot support.

### [ ] V-4.5 Generate matched visual and temporal artifacts

For one deterministic terminal snapshot/workload set, retain machine-readable
content/cell witnesses, each native-resolution screenshot, difference or heat
maps, and an HTML/JSON report containing renderer, transport, font identity,
font/cell metrics, grid, panel/window dimensions, GUI scale, cache state,
environment, source revisions, timing distributions, bytes, allocations,
uploads, and frame outcomes. The report must compare:

1. Rust CPU/fontdue with Caskaydia;
2. Rust GPU/slug with Caskaydia;
3. Java semantic cells with the vanilla Minecraft font; and
4. Java semantic cells with Caskaydia.

Exact pixels are required only for deterministic repeat runs or genuinely
equivalent paths. Cross-font/rasterizer checks use semantic cell assertions,
geometry/color/glyph-occupancy checks, perceptual/image-distance metrics, and
side-by-side review so antialiasing differences are tolerated without hiding
missing glyphs, wrong colors, stale cursors, or stretched frames.

Temporal results must include p50/p95/p99/max input-to-present and
output-complete-to-present latency, Rust and Java CPU stages, GPU/readback time
when available, rasterized cells/pixels, encode and transfer bytes, upload
bytes/regions, frames rendered/presented/dropped/coalesced/stale, and resize or
reflow time. Separate cold startup from warm steady state.

### [ ] V-4.6 Select the default from end-to-end evidence

Re-run the original multi-second witness against every valid renderer and
transport combination. A faster Rust slug span is not sufficient if GPU
readback, encoding, transfer, or Java upload erases the gain. A low-bandwidth
Java semantic path is not sufficient if Minecraft font rendering blocks the
render thread or fails visual/terminal correctness. Select the default and
fallback policy from complete latency distributions, visual evidence,
resource use, correctness, packaging, and graceful degradation. Retain the
Rust CPU path as a correctness reference and at least one Java semantic
comparator for diagnosis even when neither is the shipping default.

No implementation is considered successful while the matched workload still
contains unexplained multi-second presentation delays. Record rejected and
deferred combinations, exact reasons, commands, manifests, and source
revisions so the decision can be repeated rather than remembered.

### Parallel work allocation

After V-4.1 establishes the correlation vocabulary and the V-4.2a
contract revision is reviewed, the following tracks may run concurrently as
separate subagents or user-visible Codex tasks backed by isolated
branches/worktrees. One integration owner
controls contract and generated-code changes; agents must not concurrently
edit canonical plans or generated Vox outputs.

| Track | Repository/ownership | Parallel output |
| --- | --- | --- |
| S0 — lag witness and Java telemetry | canonical-oldest SFM terminal/panel and puppet surfaces | V-4.1/V-4.4 current-path manifest |
| S1a — retained Rust CPU resources | Teamy Terminal CPU/font/frame/session paths | bounded face/size/glyph/frame/encode caches and cold/warm witness |
| S1b — Rust push producer | Teamy Terminal PTY/session/publication path after reviewed channel schema | immediate latest-state publication with bounded coalescing/backpressure |
| S2a — slug correctness oracle | Teamy Terminal only; Teamy Studio read-only | provenance, directional band contract, difficult-glyph fixtures |
| S2b — no-window Vulkan runtime | Teamy Terminal renderer crate | retained device/targets, trivial readback, no-HWND and failure proofs |
| S2c — Rust GPU/slug integration | Teamy Terminal after S2a/S2b interfaces freeze | true GPU glyph backend, three portable transports, stage timings |
| S3 — Java vanilla font | isolated 1.19.2 SFM worktree | semantic-cell vanilla renderer and captures |
| S4 — Java Caskaydia font | isolated 1.19.2 SFM worktree/resources | same-font Java renderer, licensing, captures |
| S5 — Vox channel and semantic capability | isolated Facet/Vox runtime/codegen/contract worktree | credit-controlled Java `Tx`/`Rx`, frame subscription, then bounded cells/damage schema and generated round trips |
| S5c — raster presentation migration | one isolated Facet/Vox integration worktree and one lock owner | default renderer, presentation generation, tuple round trips, immutable generated artifact |
| S5b — SFM push/texture consumer | canonical-oldest SFM terminal service/presenter | latest-only channel receive, no frame poller, texture reuse, reconnect/full-resync evidence |
| S5d — SFM renderer selector | isolated 1.19.2 SFM worktree against fake capabilities, then canonical integration | typed tuple model, Presentation UI/actions, targeting/lifecycle tests |
| S6 — comparison reports | renderer-independent artifact tooling | HTML/JSON visual and temporal matrix |

For the next goal, S2a, S2b, S5c, and S5d's fake-capability Java work may begin
in parallel. S2c follows the frozen S2a/S2b interfaces; Teamy service
integration and canonical SFM import follow the reviewed S5c generated
contract. One integration owner controls generated Vox output, the SFM lock,
and canonical plan updates. The V-4.2c live normal/GUI-scale-7 matrix is the
join gate and must run from canonical 1.19.2 after immutable imports. S3/S4
remain later semantic work, follow the oldest-branch-first rule, and are not
propagated before acceptance. S5 must not change Cloud Terrastodon. The V-4.5
matrix and V-4.6 default decision are later integration gates after all
candidate artifacts are available. The corresponding Rust implementation
details and source-of-truth statuses live in Teamy Terminal Phase 3.6; this SFM
plan owns Minecraft presentation, end-to-end bridge evidence, and propagation.

The Rust terminal implementation is now planned as a separate public
MPL-2.0 `TeamDman/teamy-terminal` repository rather than as a dependency on the
larger Teamy Studio application. Its core/Vulkan/font workspace, bootstrap,
path-override iteration, pinned-dependency transition, and subagent gates are
recorded in the authoritative [Teamy Terminal Repository and Vulkan Renderer Plan](https://github.com/TeamDman/teamy-terminal/blob/main/docs/tasks/teamy%20terminal%20repository%20and%20Vulkan%20renderer%20plan.md).

## Product outcome

The command palette can open composable terminal scenes inside the SFM
multiplexer. Java-local REPL scenes and the Rust terminal share typed service
contracts where useful, but they are distinct user-facing surfaces:

1. **Java-local virtual REPL** — the default and always-available Java mode.
   Java owns the service and client in-process, backed by a bounded in-memory
   virtual file system and a safe command set.
2. **Java-local filesystem REPL** — an explicitly selected Java mode that can
   inspect or edit approved roots such as the mounted Minecraft instance. It
   uses the same service contract but applies path containment, size, encoding,
   and mutation policy before touching disk.
3. **Vox/Rust terminal scene** — an optional development-environment surface.
   Java is a thin Vox client and Rust is authoritative for the PTY, command
   execution, VT parsing, scrollback, colors, cursor state, and terminal visual
   semantics. The historical first presentation was bounded full PNG; the
   current raster contract independently negotiates a Rust renderer and one of
   full PNG, full raw RGBA, or dirty raw RGBA. Java uploads/displays the result
   and sends input/resize messages back. Later semantic-cell Java renderers may
   be negotiated without moving terminal-state authority into Java. The scene
   may later expose richer prompt/symbol information, compilation, audit, and
   other repository tooling.

### Explicit user-facing modes and lifecycle actions

Opening a scene and managing the optional Rust server are separate operations:

- `sfm:repl/open` always opens the Java-local virtual terminal. It remains
  useful even when a Rust server is running.
- `sfm:panel/open sfm:terminal` is the sole action that opens the Rust terminal
  scene. The scene is disconnected or connected; it never silently becomes a
  Java-local REPL.
- `sfm:terminal/server/connect [address]` accepts an optional
  `HOST:PORT` (also `:PORT` for loopback), otherwise using the client-configured
  default endpoint. It updates the Rust terminal connection without opening or
  replacing a panel and can be invoked after Minecraft has started.
- `sfm:terminal/server/start [address]` launches the configured
  `teamy-terminal.exe serve [address]` process hidden, reuses an already-live
  endpoint, and then connects existing/future terminal scenes without opening
  one. SFM tracks only processes it started so an explicitly launched server is
  never killed accidentally.

The unpublished `sfm:terminal/open`,
`sfm:terminal/connect-rust-server`, and
`sfm:terminal/start-rust-server` ids are retired migration inputs, not retained
aliases. `sfm:panel/open sfm:terminal` is the sole terminal-opening action;
`sfm:terminal/server/start` and `sfm:terminal/server/connect` are lifecycle-only.

The client config should own the default loopback address/port (initially
`127.0.0.1:63946`) and an optional executable path. The JVM property remains a
test/puppet override during migration, not the normal manual-launch contract.

The Java-local modes remain useful when Rust is not installed, the game is
offline, the endpoint is stopped, authentication fails, or the protocol is
incompatible. A failed optional connection leaves the Rust scene disconnected
with status and Start/Retry controls. It does not inject Java REPL content into
that scene or disable mounted editing or ordinary SFM gameplay.

## Boundary and ownership

Java owns all Minecraft-facing concerns:

- the terminal panel, multiplexer allocation, focus, keyboard/mouse routing,
  clipping, theme, accessibility text, and GUI-scale behavior;
- the local service implementation and virtual/local filesystem policy when
  the Java-local backend is selected;
- Vox connection/input/resize plumbing, validation of every inbound frame,
  bounded buffering, negotiated pixel or semantic-cell presentation, PNG/raw
  texture upload, Minecraft-font rendering, thread handoff through the
  Minecraft executor, and terminal lifecycle shown to the player; and
- graceful fallback when the optional endpoint is absent or unhealthy.

Rust owns optional development-environment concerns:

- a real process-backed shell or Teamy Studio terminal engine;
- VT parsing, terminal screen state, cursor/style state, scrollback, replay,
  semantic prompt/symbol/handle metadata, terminal-cell/damage semantics, and
  the visual contents of Rust-rendered pixel frames;
- repository/compiler/audit commands that are inappropriate for the Java-only
  gameplay runtime; and
- richer external interfaces such as eframe, when the user explicitly asks
  for them.

The wire contract carries portable data and intent only. Rust never sends a
Minecraft `Screen`, panel, widget tree, renderer callback, arbitrary layout
instruction, or Java object reference. In Java-local mode the panel renders
the local transcript. In Vox pixel modes it displays Rust-owned frame pixels;
in Vox semantic-cell modes it renders the Rust-owned cells and damage using
the explicitly selected Java font backend. Java does not reparse PTY bytes or
invent terminal colors, styles, cursor state, width, or damage semantics.

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

Register `sfm:terminal` as a typed panel scene. The generic
`sfm:panel/open[/direction]` family opens it in the current multiplexer or
creates a multiplexer when needed. Backend preference is not an opening
argument: this scene always represents the Rust terminal connection.

The scene exposes exactly two presentation states:

- **Disconnected:** connection status plus Start/Retry; no terminal PNG and no
  Java-local REPL instructions.
- **Connected:** the Rust-owned terminal frame, cursor, selection, and
  scrollback plus non-obscuring lifecycle/status affordances.

The terminal panel is a normal composable leaf. It must work as a full-screen
panel and inside horizontal, vertical, tab, and nested split layouts. Rust
frames describe terminal content; the Java panel owns all layout and theme
decisions.

## Rust-owned portable pixel presentation

The historical first Rust-backed presentation deliberately used a complete PNG
frame rather than a cell protocol or native GPU-handle interop. That proof is
complete. V-4.2a now also carries bounded full and dirty raw RGBA through the
same ownership boundary, and V-4.2c adds Teamy Terminal's no-window Vulkan slug
renderer without changing the Java panel contract. Rust owns the PTY, VT
state, chosen font rasterization, and pixel contents; Java validates, uploads,
and blits the selected portable frame. Keyboard, mouse, resize, focus, and
paste travel in the opposite direction through the same session.

The process boundary is treated honestly: a Rust Vulkan image cannot normally
be handed directly to Minecraft's separate LWJGL/OpenGL context. The accepted
contract therefore uses bounded portable PNG/RGBA frames with sequence,
dimensions, stride/format, full/dirty regions, cursor/selection already
composited by Rust, and one presentation generation. PNG remains useful for
artifacts/keyframes; raw and dirty raw expose the latency/bandwidth trade-off.
A later native shared-resource experiment may proceed only with explicit
cross-API ownership, synchronization, lifetime, security, and failure proof.

The Java texture panel owns upload, resource lifetime, clipping, aspect-ratio
policy, GUI-scale/layout bounds, and dropped/stale-frame handling. Rust owns
the font rasterization and terminal appearance in texture mode. A resize of
the multiplexer leaf sends a bounded render-target request to Rust; Java never
accepts arbitrary remote layout instructions. The frame protocol may be reused
by other renderers, but failure of the Rust texture stream transitions this
scene to disconnected state rather than switching it to a Java-local backend.

The live frame envelope should include a monotonically increasing sequence,
session id, pixel width/height, logical panel bounds, pixel format, stride,
full-frame versus dirty-tile kind, compression kind, and an optional cursor or
selection layer. Java rejects oversized, out-of-order, malformed, or stale
frames and may drop intermediate frames while retaining the newest complete
frame. The protocol must distinguish a terminal snapshot PNG intended for
archive/replay from a presentation frame intended for immediate upload.

Pixel presentation is an optional capability negotiated at connect time. Its
proof must identify the selected Teamy Terminal renderer and transport, show
keyboard input round-tripping to the Rust session, resize/re-render behavior,
stale/drop/disconnect handling, and a useful disconnected status while the
separately openable Java-local REPL remains available. Captures include full-
screen, nested/split-panel, narrow-window, and supported GUI-scale layouts.
This is terminal content, not a way to send Minecraft panels or executable UI
over the wire.

## Phased implementation

### Historical delegation wave — 2026-07-25 (completed)

The first concrete batch was split across independent repositories and
worktrees. Agents committed their own branches and reported tests/evidence;
the coordinator owned canonical-plan edits, review, merge order, and the final
Minecraft proof. This section records that completed coordination history.

| Track | Branch / worktree | Acceptance target |
| --- | --- | --- |
| Java-local terminal | `feat/1.19.2/terminal-java-local` / `D:\Repos\Minecraft\SFM\worktrees\1.19.2-terminal-java-local` | In-process service/client, bounded virtual filesystem, safe commands, composable panel, command-palette action, focused tests, and a Java-local puppet proof. |
| Vox terminal contract | `teamy/vox-java-terminal-contract` / `G:\Programming\Repos\facet-worktrees\vox-java-terminal-contract` | Generated portable session/input/resize/frame/cancellation/error DTOs plus deterministic schema and round-trip fixtures on the reviewed `teamy/vox-java` base. |
| Teamy Studio frame probe | `teamy/terminal-frame-probe` / `G:\Programming\Repos\teamy-studio-terminal-frame-probe` | Headless off-screen terminal capture, PNG artifact, bounded raw/compressed frame seam, and readback/latency evidence without native GPU-handle interop. |

The contract and frame-probe tracks proceeded in parallel with the Java-local
slice. Integration was coordinator-owned: first review the generated contract,
then adapt the Java service/panel, and only afterward connect the optional Rust
texture presentation. Agents did not edit the canonical `1.19.2` worktree or
broaden the scope into the colour-picker bridge.

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
revision in its canonical 1.19.2 lockfile. At that checkpoint, the Java-local
backend remained the default while the optional Vox adapter was being
implemented and verified.

Coordinator status after the standalone terminal baseline: `teamy-terminal`
main contains the bounded core scrollback/reflow, dirty rendering, Tracy
profiling, idle-scheduler, and dirty-render-default commits locally; they are
not pushed from this worktree. The SFM 1.19.2 branch now contains the reviewed
Java-local terminal acceptance baseline at `c996521da`. The reviewed Vox contract is
frozen as the `vox-java-0.10.0-rc.5` artifact produced at `26fda8736`, but it
is now consumed by SFM through the pinned `TeamDman/facet` `main` revision and
the portable Cargo/xtask source-build recipe. A workspace-relative Maven URL
is not used, and generated runtime sources are not vendored into SFM.

The integration order at that checkpoint was:

1. Keep the generated contract boundary at `26fda8736` reviewed and the
   published Facet `main` revision pinned through the SFM-supported Cargo
   source-build recipe for `org.facet:vox-java:0.10.0-rc.5`.
2. Keep the canonical compile/test and Java-local command-palette puppet proof
   green from the canonical worktree.
3. Implement and prove the optional `SFMVoxBridge` against the pinned generated
   Java contract while preserving the independent Java-local REPL.
4. Re-run the terminal scenario against the optional Rust endpoint, then pursue
   the separate texture-presentation experiment.

Later sections record completion of that adapter work. This list is not the
current execution order. The release-plan P-1 panel-scene and lifecycle
migration is complete on canonical 1.19.2; the remaining work in this plan is
the later Rust scrollback, packaging, and clean-install acceptance tracks.

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
and clamps only when bounded retention evicts that row. At that historical
checkpoint the puppet used `sfm action invoke sfm:terminal/open`; release-plan
P-1 migrates it to `sfm:panel/open sfm:terminal`.
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

At that 2026-07-26 checkpoint, the next implementation slice was launch ergonomics and lifecycle recovery
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
2. **Historical baseline complete; action migration pending P-1.** `repl/open`
   is Java-local, the old `terminal/open` action opened the Rust-preferred
   surface, and endpoint/executable defaults live in `SFMClientConfig`. The old
   `terminal/connect-rust-server [address]` accepted `HOST:PORT`, `:PORT`, or a
   bare port. P-1 replaces those Rust ids with the panel scene and
   `terminal/server/connect`; JVM properties remain test/puppet overrides.
3. **Complete.** `SFMVoxTerminalService` clears transient connection failure
   state and retries on later resize/execute requests.
4. **Historical baseline complete; action migration pending P-1.** The old
   `terminal/start-rust-server [address]` launches the configured
   `teamy-terminal.exe serve HOST:PORT` with no console window and waits for TCP
   readiness. P-1 renames it to `terminal/server/start` and removes the implicit
   panel open. The Java-local REPL does not probe or depend on the Rust process.
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

### Interactive Rust bridge: direct input and polling baseline — 2026-07-27

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

This paragraph records the historical correctness/evidence implementation.
V-4.1a supersedes it for the target bridge: live frames use a bounded Vox
`Tx`/`Rx` subscription, while unary snapshot remains an explicit capture and
resynchronization operation rather than a periodic publication mechanism.

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
That historical remaining-proof list was closed by the live P-1/P-2
verification recorded below; it is retained as the original acceptance
boundary, not as an open blocker.

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

### Release checkpoint P-1 closure — 2026-08-02

The canonical release-checkpoint goal completed its terminal boundary. The
focused `SFMVoxTerminalServiceTests` and
`SFMUnavailableTerminalServiceTests` passed, and the live
`title_screen_rust_terminal` puppet passed with `failed=0`. Its run is
`platform/minecraft/build/sfm-toolchain/artifacts/game-test-preview/runs/title_screen_rus-20260802-141350-220/`;
it contains disconnected/lifecycle/guidance/input/alternate-screen/reconnect
captures. The puppet also wrote 16 machine-readable terminal-content artifacts
under `platform/minecraft/runGameTestPreview/terminal-content/`, including
required/forbidden assertions for Ctrl+C interruption, `1..10000`, paste,
alternate-screen restoration, cancellation, and cyan PowerShell output.

The old terminal-opening and lifecycle ids are not compatibility aliases. Use
`sfm:panel/open sfm:terminal` to open the scene and
`sfm:terminal/server/start` or `sfm:terminal/server/connect` only to manage
the Rust endpoint. Rust scrollback, crisp font-size negotiation without Java
bitmap upscaling, clean-install companion-server packaging, and the later
Rust CPU/GPU/Java renderer comparison remain open plan items. The detailed
performance and visual evidence gates are V-4.1 through V-4.6 above.

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

### Historical Phase 3 outline — Vox/Rust adapter (superseded by V-4 items)

- Implement `SFMVoxBridge` endpoint lifecycle and the generated terminal
  service adapter against the frozen `org.facet:vox-java` artifact.
- Add the Rust-side Teamy Terminal adapter that maps its terminal control-plane
  operations and frames into the portable schema.
- Add connected, authentication failure, schema mismatch, timeout, malformed
  frame, cancellation, reconnect, and clean shutdown states.
- Re-run the same puppet scenario against both Java-local and Rust backends;
  the screen and action model should not fork.

### Historical Phase 3a outline — Texture presentation experiment (superseded)

- Add a negotiated `texture-frame` capability alongside the structured-cell
  capability.
- Implement a bounded RGBA/BGRA frame or dirty-tile message, Java-side texture
  upload, sequence/lifetime checks, and resize requests.
- Use Teamy Terminal's portable CPU path and the V-4.2c true no-window Vulkan
  renderer; Teamy Studio's historical DirectX probe is evidence only and is
  not an implementation dependency. Do not make Minecraft depend on a shared
  native GPU handle.
- Capture connected texture mode, input round-trip, resize, stale/disconnected
  frame handling, and independent Java-local REPL availability before
  considering native graphics interop.

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
infrastructure, but terminal lifecycle, independent Java-local REPL
availability, and one useful Rust-backed operation are the next user-visible
bridge milestones.
