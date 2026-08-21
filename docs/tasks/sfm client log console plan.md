# SFM client log console plan

## Summary

Create a client-side SFM console that displays log records emitted by SFM's
normal logging paths. The console must observe logging as a consequence of the
existing `SFM.LOGGER`/manager logger activity; screens and commands should not
have to push duplicate presentation messages into a palette-specific list.

The immediate implementation is a reusable `SFMConsoleWidget` shown inside the
command palette. Its output source is the shared
`SFMConsoleWidgetLogger`, backed by `TranslatableLogger`; the command palette is
only one producer and other client systems may publish to the same logger.
The logger defaults to `TRACE` so the first implementation preserves all
diagnostic output. Filtering is intentionally deferred until the unfiltered
volume demonstrates that it is needed. A standalone console screen and a
Draw-canvas log layer can follow once the widget contract is proven.

The central sink and logger adapters remain future work. For this milestone,
the widget owns only presentation state and receives explicit lines from its
host. This keeps the first implementation small while leaving room for a
later `SFMClientLogSink` without coupling the widget to its eventual backend.

## Completed first-slice validation

- [x] Add the `echo` client action and a title-screen puppet that invokes it,
  providing a repeatable capture of command output in the console widget.
- [x] Publish each puppet preview into a fresh timestamped run directory under
  `artifacts/game-test-preview/runs/` while retaining the stable manifest at
  the artifact root. This prevents Codex and other image viewers from serving
  an older PNG after a rerun overwrites the same logical capture name.

## Scope reset

The current milestone deliberately ignores Log4j appenders, global
`SFM.LOGGER` capture, manager-network convergence, and persistent log history.
It does retain `SFMConsoleWidgetLogger` as the output source, with the command
palette as its first producer. Those broader topics remain documented as
future phases below, but they are not prerequisites for introducing or
validating `SFMConsoleWidget`.

This plan is deliberately separate from the existing manager-log transport:
manager logs use `TranslatableLogger` and `TranslatableLogEvent`, while
`SFM.LOGGER` uses the Minecraft Log4j context and ordinary Log4j `Message`
instances. They can converge on a shared client record/view model only after
their lifecycle, locality, and translation semantics are explicit.

## Integration consumers recorded 2026-07-21

The [in-game review workspace](in-game%20code%20review%20workspace%20and%20window%20manager%20plan.md)
and [snapshot/episode plan](snapshot%20episodes%20and%20deterministic%20action%20environments%20plan.md)
introduce additional consumers of reusable read-only diagnostic presentation:

- the action-details and dynamic-keymapping screens show binding conflicts,
  unavailable reasons, parse failures, and replay diagnostics;
- the source-comparison workspace shows comparison/parser/audit diagnostics;
- the Episode Inspector shows selected action results and errors; and
- the generic multiplexer recorder/ownership layer shows capture, ownership,
  and replay failures while its ordinary calculator proving panel remains free
  of dedicated trace UI.

These consumers may embed `SFMConsoleWidget` or reuse its styled scrolling
model, but their authoritative structured state remains in their own models.
Raw episode events/actions, review decisions, source code, calculator state, and
keybinding configuration are not converted into ordinary log lines merely to
reuse the widget. Conversely, the console plan does not become responsible for
snapshot rehydration, action replay, or review-ledger persistence.

## Cross-runtime observation extension recorded 2026-08-20

The [snapshot/episode plan](snapshot%20episodes%20and%20deterministic%20action%20environments%20plan.md)
now owns one temporal spine across Java actions, Rust tools, editor sessions,
terminal sessions, guided studies, and optional external agents. This log plan
owns the capture adapters and read-only presentation that project runtime
records into that spine as **observations**. Log records never become canonical
input actions or state transitions merely because they are correlated with an
episode.

### [ ] LOG-X1 Define a versioned cross-runtime observation envelope

- Preserve source runtime/process/thread/task, logger or `tracing` target,
  level, per-source monotonic sequence, optional zoned wall time, span and
  correlation ids, structured fields, translatable content, rendered fallback,
  and bounded error/throwable details.
- Permit optional episode, branch, event, action, request, terminal-session,
  and panel identities without requiring every producer to know all of them.
- Do not impose a false total order by wall clock across processes. Preserve
  source ordering and explicit causal/correlation links; expose unknown clock
  skew in inspection/export.
- Version/bound every record and count dropped or redacted records.

### [ ] LOG-X2 Adapt Java Log4j/translatable and Rust `tracing` sources

- Reuse `SFMClientLogSink` for ordinary Java Log4j and
  `TranslatableLogEvent` sources while preserving their different localization
  semantics.
- Add a Rust-side adapter at the local Vox/tool boundary that forwards
  structured `tracing` events rather than parsing terminal-rendered text.
- Correlate request/span/session ids across Java and Rust where the protocol
  already has them. Adapter failure must not recurse through either logger or
  block the Minecraft render thread.
- Keep retention, filtering, and transport opt-in/bounded; do not turn this into
  an unrestricted Forge-wide remote log collector.

### [ ] LOG-X3 Build an addressable in-game observation panel

- Open through the ordinary panel/action surface without requiring a terminal.
- Subscribe incrementally and filter by runtime, episode/branch/action/request,
  terminal session, level, target/logger, and correlation id.
- Expose structured details and jump links to resolvable documents, selections,
  actions, requests, or terminal sessions; retain copyable rendered text as a
  projection.
- Make pause/follow-tail, clear-view, retention/drop indicators, and export
  keyboard navigable. Clearing a view does not erase authoritative episode
  state or another sink's retained records.

### [ ] LOG-X4 Prove end-to-end correlation without terminal dependence

A puppet invokes an action that crosses Java→Rust→Java, opens the observation
panel, and shows correlated Java and Rust records under one request/action id.
The episode artifact retains those records as observations while exact replay
remains driven by recorded events/actions. The proof runs with no terminal
panel or external Windows Terminal window open.

## Current architecture and constraints

- `SFM.LOGGER` is the normal Log4j logger used throughout client, common, and
  server code. Most calls use parameterized strings (`{}`) and some include a
  throwable. These messages are not automatically localization keys.
- `TranslatableLogger` creates a separate Log4j `LoggerContext` and attaches a
  `TranslatableAppender` to each manager logger. Its records retain
  `TranslatableContents`, timestamps, and levels so the existing manager Logs
  screen can localize them on the client.
- `LogsScreen`, `LogsScreenMultiLineEditBox`, and
  `LogsTextStylingHelper` are coupled to `ManagerContainerMenu` and its server
  synchronization lifecycle. The useful text styling and scrolling behavior
  should be extracted rather than making the global console depend on a
  manager block.
- `SFMCommandPaletteScreen` now hosts the initial `SFMConsoleWidget`; the
  widget has its own clipped viewport, wheel/scrollbar behavior, follow-tail
  state, and bounded line model. Completing the current milestone still
  requires the shared `SFMConsoleWidgetLogger` output contract and removal of
  any remaining palette-owned feedback path. This is initially a
  client-console channel, not a substitute for observing the global
  `SFM.LOGGER` stream.
- Log4j appender/configuration APIs and Minecraft GUI/rendering APIs vary across
  supported Minecraft branches. Version-sensitive installation and screen
  details must stay behind the existing helper seams and be audited.

`SFMConsoleWidgetLogger` is a client logging service, not a field owned by
`SFMCommandPaletteScreen`. Its backing `TranslatableLogger` should use a stable
console-widget logger name and be configured to `TRACE` by default. The first
slice deliberately shows every record it receives; level/logger filtering is a
later presentation feature, added only if the unfiltered console becomes too
noisy.

## Future central sink shape (deferred)

`SFMClientLogSink` is an opt-in, client-side service rather than another GUI
widget:

- Producers publish immutable records with level, source/channel, sequence,
  timestamp, logger name when available, translated/component content when
  available, rendered fallback text, and bounded throwable details.
- The sink owns the bounded retention policy, dropped-record count, clear
  operation, and subscriptions/snapshots. Producers never receive a screen or
  palette callback.
- The palette action source publishes command feedback through the sink. A
  global Log4j appender publishes selected `SFM.LOGGER` events. A manager-log
  adapter publishes records only when the client is authorized to see them.
- The initial sink backend may delegate storage to a dedicated
  `TranslatableLogger`; later it can use a unified ring buffer without changing
  producers or consumers.
- Console and Draw views consume snapshots/subscriptions and apply filtering,
  level styling, localization, scrolling, and copy/export. They must not mutate
  producer state or call back into actions to obtain output.

## Contract decisions to settle before implementation

| Question | Initial direction | Required proof |
|---|---|---|
| Capture scope | Capture the SFM logger namespace in the client process, beginning with the exact `SFM.LOGGER` logger and explicitly deciding whether child names are included. | A probe proves which logger names are emitted on title screen, in-world client, and integrated-server paths without capturing unrelated Forge/Minecraft noise. |
| Manager-log convergence | Keep manager `TranslatableLogEvent` transport working. Add an adapter into the shared client record model only after deciding whether a client console may show server logs in multiplayer or only locally available manager logs. | Single-player and multiplayer behavior is named; no server-only data silently appears as if it were local. |
| Message representation | Preserve raw Log4j format, frozen parameter strings, rendered fallback text, logger name, level, timestamp, and throwable summary. Preserve `TranslatableContents` when a source provides it. | Tests show translation is resolved at render time for translatable records and ordinary parameterized logs remain readable without pretending their English format is a translation key. |
| Retention | Use a thread-safe bounded ring/deque with a configurable maximum and an explicit dropped-record counter. Appender failures must never recurse into SFM logging. | A stress test emits from multiple threads, verifies bounded memory, ordering, and continued game operation. |
| Lifecycle | Install the collector once on the client logger context, avoid duplicate appenders after reload/hotswap, and detach or disable it during client shutdown. Dedicated-server loading must not require client classes. | Startup/reload/shutdown tests and an audit prove the collector is client-only and does not alter normal file/console logging. |
| Console logger | Introduce `SFMConsoleWidgetLogger` as the client-console output contract for the first slice. It wraps the existing `TranslatableLogger`, defaults to `TRACE`, and exposes bounded snapshots/clear behavior without filtering. | The command palette and a second non-palette producer can publish independently; the widget has no producer-specific callbacks and no duplicate feedback list. |
| Palette output | Adapt `SFMClientActionSource.sendFeedback` through `SFMConsoleWidgetLogger` rather than retaining palette-owned output state. Keep this logger separate from the future global `SFM.LOGGER` collector because it has its own Log4j context. | `list`, `help`, `dump_registries`, and action failures appear consistently without each action knowing about the palette or console screen; component localization/style is not flattened prematurely. |
| Presentation scope | Ship a standalone console first; add a command-palette action to open/focus it. Treat Draw embedding as a later read-only log layer with panning/zooming or a bounded viewport. | The standalone view remains usable with thousands of records before any canvas integration begins. |
| Filtering | Keep the first `SFMConsoleWidgetLogger` view unfiltered at `TRACE`; retain pause/follow-tail, clear, and copy/export only. Add logger/level filters after real output volume shows that they are necessary, and defer arbitrary regex queries and persistent history. | Initial UI tests cover auto-scroll, clear, and copy without changing the logger's data. A later filter milestone must prove that filtering is a presentation concern rather than producer loss. |
| Sensitive data | Treat logs as diagnostic data. Do not add program source, item NBT, labels, or Lua arguments to normal levels merely to make the console interesting. | Review existing log call sites and document any redaction/truncation policy. |

## Phases

### [ ] Current milestone — Introduce `SFMConsoleWidget` over palette logs

- [x] Establish the initial standalone `SFMConsoleWidget` with its own
  scissor-clipped viewport, wheel handling, track-click/thumb-drag scrollbar,
  follow-tail state, and bounded line buffer. It intentionally does not extend
  a vanilla `AbstractScrollWidget`; the version-sensitive scissor calculation
  is kept inside the widget seam.
- Extract the reusable read-only text/scrolling behavior from
  `LogsScreenMultiLineEditBox` and `SFMMultiLineTextRenderWidget` into a
  `SFMConsoleWidget` under the client screen/widget seam. Do not make it depend
  on `LogsScreen`, `ManagerContainerMenu`, global Log4j, or command execution.
- Define a small host-facing API: replace/append lines, clear, bounded line
  retention, `scrollToBottom`, follow-tail toggle, and a snapshot/copyable-text
  view. Accept styled `Component` lines so localization and level coloring can
  be supplied by the caller without the widget flattening them.
- Add `SFMConsoleWidgetLogger`, backed by `TranslatableLogger`, with a default
  `TRACE` level and a bounded snapshot/clear path. Route
  `SFMClientActionSource` output into this shared logger through a
  component-aware adapter, then resolve its `TranslatableLogEvent` records into
  styled lines for the widget. Keep the logger usable by non-command-palette
  client producers from the start.
- Keep the widget read-only. Support mouse-wheel scrolling, scrollbar dragging,
  keyboard focus, selection/copy only if the extracted text renderer already
  provides it cleanly; do not add command input or log filtering yet.
- Use `SFMFontUtils` for text rendering and preserve the existing version seams
  around scroll geometry. Add pure tests for bounded append/clear/follow-tail
  behavior and a focused client rendering test for multiline styled lines.
- Replace the command palette's small feedback label area with the widget
  backed by `SFMConsoleWidgetLogger`. This proves layout, scrolling,
  localized output, and palette stack behavior without introducing global
  logger capture.

**Completion criteria:** The palette can display more than its current two
feedback lines in a scrollable console widget sourced from
`TranslatableLogger`, command execution behavior is unchanged, and no global
Log4j or manager-network code is required by the widget.

### [ ] Future milestone gate — Choose the output source

After the widget is proven, decide whether `SFMConsoleWidgetLogger` should be
wrapped by an `SFMClientLogSink` shared with future global/manager adapters. Do
not begin global Log4j capture until the widget has a stable host-facing API.

### [ ] 0. Establish a logging baseline and source catalog

- Inventory `SFM.LOGGER` call sites by level, parameter style, throwable use,
  thread, and client/server side.
- Inventory manager `TranslatableLogger` use, its custom context, packet
  synchronization, and the existing styling/scrolling components.
- Write a small diagnostic mode or test appender that records logger names and
  message classes without changing user-visible behavior.
- Decide whether the first console is local-client-only or may include
  manager/server records that already arrive through the manager screen.

**Completion criteria:** The record sources, locality rules, translation cases,
and first-release non-goals are documented with representative examples.

### [ ] 1. Capture global SFM Log4j events

- Define the `SFMClientLogSink` API before wiring any producer: publish a
  structured record, take a bounded snapshot, subscribe for incremental
  updates, clear, and report dropped records. Include source/channel and a
  stable sequence number so global, manager, and palette records can be
  distinguished and de-duplicated.
- Implement a client-only bounded appender/collector attached to the actual
  SFM Log4j logger configuration, preserving existing appenders and additivity.
- Snapshot mutable Log4j events immediately: level, logger name, timestamp,
  message format, parameter strings, formatted fallback, and throwable summary.
- Provide a thread-safe snapshot/clear API and a monotonically increasing event
  sequence so a view can update incrementally without comparing text.
- Guard appender installation against duplicate registration and avoid logging
  from inside appender error handling.
- Isolate any Log4j/Minecraft-version differences in one helper seam and add a
  source audit permit/deny rule if direct logger-context manipulation becomes
  version-sensitive.

**Validation:** A client test emits INFO/WARN/ERROR messages and an exception
from different threads, verifies ordering/bounds, and confirms the ordinary
Log4j output still reaches its existing sink.

### [ ] 2. Define structured records and translation/rendering adapters

- Introduce a record model shared by the global collector and future manager
  adapter. Keep raw diagnostic data separate from the rendered `Component`.
- Add a `TranslatableContents` path for records that genuinely carry a
  translation key; resolve it with the existing client translation helpers at
  render time.
- Render ordinary SFM logger messages as frozen diagnostic text with parameter
  values and bounded throwable summaries. Do not reinterpret arbitrary Log4j
  format strings as localization keys.
- Extract level coloring, timestamp formatting, multiline/code-block handling,
  and copy-text generation from `LogsTextStylingHelper` into a reusable log
  presentation service. Keep manager-specific transport out of that service.
- Define whether command feedback is converted to structured log records through
  a central bridge. If it is, preserve `Component`/translation information
  rather than flattening it through `getString()` prematurely.

**Completion criteria:** The same record can be rendered from an ordinary
`SFM.LOGGER` event and a manager `TranslatableLogEvent` without either source
losing its translation or diagnostic metadata.

### [ ] 3. Build the standalone SFM console

- Start with `SFMConsoleWidgetLogger` backed by `TranslatableLogger` as a
  proven source of localized command output. Keep its default level at `TRACE`,
  expose bounded snapshot/clear/subscription behavior, and reuse the manager
  log styling before adding global Log4j capture. Do not add filtering until
  the unfiltered console has been exercised with real output.
- Add a client action such as `sfm:logs/open` that opens the console without
  making the collector depend on the command palette.
- Implement a read-only, virtualized or bounded multiline view with level
  colors, timestamps, logger names, multiline continuation, exception details,
  follow-tail/pause, clear, and copy/export. Add level/logger filters only in a
  later milestone if the default `TRACE` volume proves too noisy.
- Make empty-state and dropped-record indicators localized. Keep the console
  usable on the title screen and in-world, and ensure it does not pause or
  steal the OS cursor unexpectedly in puppet mode.
- Add a command-palette capture puppet for the console with representative
  INFO/WARN/ERROR records and a long multiline exception.

**Validation:** A live client run emits naturally from existing SFM code, opens
the console, shows those records without a command-specific append call, and
closes back to the originating screen with the normal screen-stack policy.

### [ ] 4. Integrate command results without coupling actions to the palette

- Replace the palette's direct feedback list with `SFMConsoleWidgetLogger`.
  Extend its initial `TranslatableLogger` backend or add a narrow component-aware adapter
  so composed/translatable `Component` values retain their translation keys and
  style metadata instead of being flattened with `getString()`.
- Ensure action list/help/dump-registry output, command syntax errors, and
  runtime failures have consistent levels, localization, and throwable display.
- Add an explicit `sfm:logs/clear`/pause/follow action only if the action model
  remains useful outside the palette; otherwise keep those as console controls.
- Verify that opening help or another editor from the palette does not cause
  log output to be lost when screens are pushed or reinitialized.

### [ ] 5. Converge manager logs where the product contract allows

- Build an adapter from `TranslatableLogEvent` to the shared client record model
  without changing existing manager packet framing or log-level semantics.
- Decide whether a global console may subscribe to the active manager menu,
  query multiple managers, or only display already-synchronized records.
- Preserve server authority and multiplayer privacy; never make a client-side
  global appender imply access to another player's manager logs.
- Keep the existing manager Logs screen working while it migrates to the shared
  styling/view service.

**Completion criteria:** Manager and global records have visibly consistent
level/timestamp/multiline treatment, with clear provenance showing their source.

### [ ] 6. Add a Draw-canvas log layer

- Represent the console as a read-only canvas layer or embedded log document,
  not as thousands of persistent glyphs in the user's saved program region.
- Reuse camera panning/zooming for navigation, but provide a bounded viewport,
  follow-tail behavior, and a way to recover the view after panning away.
- Colorize log lines by level and preserve translated components; do not run the
  SFML grammar highlighter over logs.
- Keep log state ephemeral by default. Saving a Draw document must not write the
  entire diagnostic console into the disk program unless the user explicitly
  exports a snapshot.

### [ ] 7. Cross-version proof, audit, and documentation

- Propagate the collector and console through supported Minecraft branches using
  SFM's propagation CLI, keeping Log4j/Minecraft-specific calls inside narrow
  annotated seams.
- Add audit rules for direct logger-context/appender manipulation outside the
  logging service and for GUI text rendering outside the shared log view seam.
- Run compile, unit tests, live console puppet captures, and the existing
  manager-log tests across the supported matrix.
- Document log scope, retention, localization behavior, multiplayer limits,
  copy/export semantics, and how to interpret dropped records.
- Decide whether this is release-worthy user functionality or developer-only
  tooling before changing the changelog.

## Acceptance criteria

- Existing `SFM.LOGGER` calls appear in the console automatically, without
  actions or screens manually appending to a palette list.
- Palette command responses publish through the central `SFMClientLogSink` and
  are visible through the same log-record view without a screen-specific
  feedback buffer.
- Existing file/console Log4j output and manager Logs behavior remain intact.
- Global parameterized messages, translated manager messages, multiline output,
  and exceptions render predictably and copy correctly.
- The collector is bounded, thread-safe, client-safe, and does not recurse or
  crash when logging itself encounters an error.
- Java Log4j/translatable and Rust `tracing` records can share one versioned,
  correlated observation view without claiming wall-clock total ordering or
  turning logs into replay authority.
- The combined observation stream is inspectable in-game without opening a
  terminal and can jump to correlated episode/action/request context.
- The standalone console works from the title screen and in-world; Draw
  embedding is optional until its ephemeral-layer semantics are proven.
- Supported-version audits, tests, and live captures pass before release scope
  is expanded.

## Explicit non-goals for the first slice

- Replacing Minecraft/Forge's global log viewer or changing Log4j configuration
  for unrelated mods.
- Treating every existing English `SFM.LOGGER` format string as a translatable
  localization key.
- Treating `SFMConsoleWidgetLogger`'s initial `TranslatableLogger` backend as if it captured the
  global Minecraft/Forge Log4j stream; global capture remains a separate
  producer adapter.
- Persisting an unbounded history, uploading logs, or exposing server logs to
  clients without an explicit network contract.
- Making the Draw document's saved program content include the live console by
  default.
