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

**Cross-plan priority:** This extension is valuable observability and later
review evidence, but it is not the immediate predecessor of the missing human
release-review gate. Unless the user explicitly reprioritizes logging, the next
unattended implementation candidate remains RCS-0 through RCS-8 plus the real
1.19.2 RCS-S2 domain and RCS-S1 resumable proof in the global comment/review
plan: selection-to-comment adaptation, structural selector proposals,
conservative evaluation/migration, a portable queryable review document, and a
natural in-game proof over both fixtures and current release code.
Recent logging discussion must not displace that dependency path merely by
being newer.

### [ ] LOG-X0 Measure the actual Java/Forge sink topology before replacing projections

- Emit distinct bounded probe records through direct `SFM.LOGGER`, the approved
  SFM semantic facade, `TranslatableLogger`, a child/third-party-named Log4j
  logger, `System.out`, and `System.err`, including one throwable and one
  multiline message. Do not infer sink behavior from console appearance.
- For title-screen client, integrated-server, dedicated-server, GameTest, and
  puppet launch profiles, record exactly which probes appear in the inherited
  console stream, Forge `latest.log`, Forge `debug.log`, the Rust launch
  `console.log`, and the pre-bridge Rust `--log-file` NDJSON.
- In particular, settle empirically whether the current Forge launch redirects
  raw `System.out`/`System.err` into `latest.log`. Until that table exists,
  documentation must not claim that `latest.log` is a fallback for direct
  stdio; the Rust parent process remains the known owner of inherited child
  stdout/stderr.
- Record logger names, markers, levels, formatting/template survival,
  throwable behavior, duplicate projections, buffering, and shutdown flush.
  The probe is test/development-only and uses stable marker ids so an agent can
  evaluate the truth table without OCR.

**Observable proof:** One versioned artifact contains the emitted marker set
and a sink-by-marker presence matrix for every launch profile, with byte/sample
references to each retained output. Every later capture/fallback statement in
LOG-X1 through LOG-X6 cites that measured topology.

### [ ] LOG-X1 Define a versioned cross-runtime observation envelope

- Preserve source runtime/process/thread/task, logger or `tracing` target,
  level, per-source monotonic sequence, optional zoned wall time, span and
  correlation ids, structured fields, translatable content, rendered fallback,
  and bounded error/throwable details.
- Preserve source provenance as structured data rather than only decorating the
  terminal projection. A record may carry runtime class/module, method, source
  filename, line, column, byte/glyph span, canonical source URI, branch/source
  set, source hash, callsite id, capture mechanism, and confidence. Missing or
  ambiguous fields remain explicit instead of being guessed.
- Model human-readable messages as a sum type. Ordinary diagnostic text keeps
  its format/template, frozen parameters, and rendered fallback. A genuinely
  translatable SFM message additionally keeps a stable event/message id,
  translation key, typed/bounded arguments, required English fallback, and an
  optional localized rendering plus locale id. English and localized text are
  projections of the semantic record, not competing authoritative records.
- Permit optional episode, branch, event, action, request, terminal-session,
  and panel identities without requiring every producer to know all of them.
- Do not impose a false total order by wall clock across processes. Preserve
  source ordering and explicit causal/correlation links; expose unknown clock
  skew in inspection/export.
- Version/bound every record and count dropped or redacted records.

### [ ] LOG-X1A Capture source provenance without imposing release overhead

- Rust `tracing` records retain macro metadata (`file`, `line`, and, where
  published by the callsite, column/module path) and normalize it into the
  observation source-location field used by terminal hyperlinks and NDJSON.
- Java Log4j records use `LogEvent.getSource()` only under an explicit location
  policy. Log4j 2.17 computes absent source data with
  `StackLocatorUtil.calcLocation`, which creates a `Throwable` and scans its
  stack; this is measured rather than assumed cheap. The default is deliberately
  simple and inspectable: SFM-authored records include runtime source locations
  when `SFMEnvironmentUtils.isInIDE()` is true, while ordinary distributed runs
  default them off unless the user explicitly enables them.
- Do not make the root Log4j appender location-aware merely to identify SFM
  callsites: that would impose the stack-walk cost on every Forge/Minecraft/mod
  event, including noisy initialization. Scope runtime location capture to the
  SFM namespace or to an explicit diagnostic profile. Third-party records may
  truthfully have unavailable source provenance.
- Direct `SFM.LOGGER` calls should report their real caller. Any SFM logging
  wrapper, including an evolved `TranslatableLogger`, must delegate through
  Log4j's caller-aware `ExtendedLogger`/FQCN or explicit-location seam so the
  wrapper method is not misreported as the emitter.
- A JVM `StackTraceElement` supplies class, method, filename, and line but no
  source column. The Rust-owned build generates a versioned, hashed Java
  logging-callsite manifest from the source AST and enriches captured
  class/method/line/message-id tuples to canonical file URIs, exact call
  expression spans, and columns. This enrichment does not rewrite Java source
  or bytecode and therefore does not become mandatory for Gradle-only
  contributors. Same-line collisions produce candidate/ambiguity diagnostics;
  an audit may later require one relevant callsite per source line if useful.
- Source provenance identifies both the compiled source snapshot and the live
  source used for hyperlinking. Hash mismatch is visible and prevents an old
  classfile location from silently claiming exactness against edited source.
- Compare disabled capture, SFM-only runtime capture, and manifest enrichment
  using repeated representative low- and high-volume runs. The paired workload
  set includes title-screen/mod initialization, one normal editor/terminal
  interaction run, and a deterministic high-volume SFM logger fixture. Report
  wall time, CPU, allocations/event, throughput, p50/p95/p99 event cost, queue
  depth/drops, and manifest hit/ambiguity rate, but treat noisy whole-game timing
  as descriptive evidence rather than a percentage acceptance gate. The fixed
  default remains `SFMEnvironmentUtils.isInIDE()`: true means SFM source
  locations on, false means off. Correctness, bounded allocation/queue behavior,
  absence of new drops, and absence of render/logging-thread blocking are hard
  gates; timing comparison exists to reveal an obvious regression and inform a
  later policy change, not to pretend natural GameTest variance is precise.

**Observable proof:** A Java `SFM.LOGGER` call and a call through the
translatable wrapper both produce clickable terminal labels targeting the real
Java callsite. Their NDJSON records contain the same canonical URI, line,
column/span, source hash, capture mechanism, and confidence. Disabling Java
location capture removes its runtime stack-walk cost and reports provenance as
unavailable/unenriched rather than fabricating a location.

### [ ] LOG-X1B Make SFM-authored logs semantically translatable and bilingual

- Introduce a normal SFM logging facade/message type for global logs rather
  than repurposing arbitrary English Log4j format strings as localization keys.
  A translatable message carries a stable id, `LocalizationEntry` key, English
  default/fallback, bounded arguments, structured fields, and throwable; its
  Log4j formatted fallback remains readable by ordinary Console/File appenders.
- Preserve the existing manager `TranslatableLogger` transport while extracting
  or adapting its semantic-message representation into the common observation
  model. Its custom logger context and multiplayer authority do not silently
  become global merely because rendering is shared.
- Define `english`, `localized`, `bilingual`, and `auto` rendering policies for
  terminal, file/export, and in-game views. When a usable locale differs from
  English, bilingual copy/export includes localized text and English fallback;
  when they are equivalent it emits one rendering. Records always retain the
  key, arguments, English fallback, locale id, and localization-success status.
- Server-side or early-bootstrap records may not have a client language table.
  They retain semantic key/arguments and English fallback, and never pretend a
  client-native rendering was available. In-game views may re-render later
  from the retained semantics and current resource packs.
- Keep values such as paths, ids, item data, and exception text as structured
  arguments rather than translation text. Apply the existing truncation and
  sensitive-data policy before transport/export.
- Extend source audit with separately scoped rules for production/gametest
  `System.out`, `System.err`, `printStackTrace`, direct `LogManager`/raw Log4j
  acquisition, and direct `SFM.LOGGER` use outside the approved facade. Tests,
  protocol stdout, bootstrap emergency diagnostics, and staged migration use
  narrow explicit permits rather than disabling the rule globally.
- Migrate existing global callsites incrementally by category and retain a
  report of plain, translatable, intentionally exempt, and unresolved calls.
  The release gate requires every SFM-owned production runtime callsite to use
  the approved facade or a narrow named bootstrap/protocol exemption. GameTest
  and test callsites remain separately counted and must either migrate or name
  a test-output reason; unresolved counts cannot disappear behind a warning.
  Third-party Forge/Minecraft messages are never required to become
  translatable.

**Observable proof:** One parameterized INFO record and one throwable-bearing
WARN record flow through ordinary Log4j files, the Rust terminal formatter, and
NDJSON. A non-English client can display/copy native plus English text while an
English client sees one copy. Both preserve the same event id, key, arguments,
source location, and throwable. Audit fixtures distinguish forbidden direct
output/logger access from explicit protocol/test permits.

### [ ] LOG-X1C Freeze capture, provenance, localization, and presentation policy precedence

- Define one serializable effective policy with independent axes for Log4j
  capture scope, Java source-location capture, semantic-message rendering,
  terminal/NDJSON projection, retention, and query profile. “Capture ProjectE
  initialization so it can later be filtered” is distinct from “pay a Java
  stack walk for ProjectE” and from “translate ProjectE.”
- A Rust-owned developer `run client` defaults to console-equivalent structured
  Log4j capture, including Minecraft/Forge/third-party logger records that
  would normally reach its console, while source stack walking remains
  SFM-only and third-party records retain ordinary template/rendered evidence.
  Distributed player runs default to the bounded release policy rather than a
  broad remote collector.
- Use deterministic precedence: explicit command-line option, then named SFM
  environment/JVM launch setting, then persisted client presentation setting
  where that process can truthfully own it, then launch-profile default. The
  Rust launcher computes and passes the effective capture/location policy;
  changing an in-game rendering preference cannot retroactively alter what the
  parent NDJSON sink captured. When no override exists, the Java source-location
  launch default is `sfm` iff `SFMEnvironmentUtils.isInIDE()` and `off`
  otherwise.
- Reserve documented settings for `off|sfm|console-equivalent` capture,
  `off|sfm` Java location, and `english|localized|bilingual|auto` rendering.
  Invalid values fail with the accepted values and effective source rather than
  silently falling back. Exports retain semantic/native fields regardless of
  the selected human projection.

**Observable proof:** A precedence matrix launches the same marker fixture
through default, environment/JVM, and explicit CLI policies. Each artifact
records the effective policy and provenance; ProjectE-like records are
captured/filterable without translation or stack walking, while SFM records
retain their richer source and localization data.

### [ ] LOG-X2 Adapt Java Log4j/translatable and Rust `tracing` sources

- Reuse `SFMClientLogSink` for ordinary Java Log4j and
  `TranslatableLogEvent` sources while preserving their different localization
  semantics.
- Add a Rust-side adapter at the local Vox/tool boundary that forwards
  structured `tracing` events rather than parsing terminal-rendered text.
- Correlate request/span/session ids across Java and Rust where the protocol
  already has them. Adapter failure must not recurse through either logger or
  block the Minecraft render thread.
- For Rust-owned `run client`, server, data, GameTest, and puppet launches, the
  parent creates an authenticated loopback endpoint before spawning the JVM and
  passes endpoint, run id, policy, and random capability token as launch
  properties. The Java appender snapshots immutable records into a bounded
  queue; one daemon transport worker performs framed I/O. Logging threads never
  connect, block, or retry network operations.
- Adapt native `tracing` events/spans and incoming Java records into one
  `ObservationRecord` router. Do not synthesize unbounded dynamic tracing
  callsites for Java. Terminal and NDJSON are sibling sinks over the normalized
  record, so message/source formatting is not separately reimplemented at the
  subprocess boundary.
- Treat raw child stdout/stderr as separately typed `process-stdio`
  observations. They remain useful for JVM/native writes and bridge fallback,
  but are never parsed or relabelled as structured Log4j events. Forge's
  `latest.log`/`debug.log` appenders remain intact; direct stdio is guaranteed
  only in the Rust-owned launch `console.log`/observation path.
- Once a healthy structured bridge owns terminal presentation, avoid duplicate
  Log4j console projection while retaining file/debug appenders. Bridge loss
  fails open to ordinary Forge console/file behavior and emits a bounded
  non-recursive bridge diagnostic/drop count.
- Keep retention, filtering, and transport opt-in/bounded; do not turn this into
  an unrestricted Forge-wide remote log collector.

### [ ] LOG-X2A Add explicit Java observation scopes and safe async propagation

- Add a small caller-aware `AutoCloseable` Java observation-scope contract that
  emits versioned open/close/error/cancel lifecycle records with stable span
  id, parent id, operation/category, correlation ids, and source provenance.
  Ordinary Log4j calls made in the scope inherit its context without pretending
  they are native Rust `tracing` callsites.
- Make context capture/restore explicit across Minecraft client-thread
  scheduling, bounded worker executors, Vox/network callbacks, and completion
  continuations. A raw `ThreadLocal` that is lost or leaks when work changes
  threads is not sufficient. Context restoration uses a scoped lease and every
  fault/cancel path closes it.
- Translate scope lifecycle into normalized observation span records that the
  Rust router can parent beneath or correlate with native `tracing` spans.
  Unknown/missing parents, late events, duplicate close, and process loss remain
  explicit diagnostics; arrival order does not manufacture ancestry.
- Instrument one real editor open/edit/save-or-close journey and one
  Java→Rust→Java request so LOG-X5 can query “inside this operation” and check
  the dirty-document lifecycle without line-number slicing.

**Observable proof:** A nested asynchronous fixture changes threads twice yet
all Java events remain under the intended scope; a fault and cancellation close
exactly once; and an end-to-end action produces a source-linked Java/Rust span
tree whose parent/correlation claims are machine-verifiable.

### [ ] LOG-X2B Prove the exact Rust-owned Java-to-NDJSON and console path

- Make this exact user workflow an acceptance command:

  ```pwsh
  sfm-propagate-changes.exe run client --branch 1.19.2 --log-file abc.ndjson
  ```

- The resulting parseable file contains native Rust events/spans, structured
  Java Log4j events, semantic/translatable SFM events, and separately typed
  `process-stdio` records when the probe writes raw stdout/stderr. Records share
  the versioned observation envelope while retaining source-native fields and
  capture provenance; Java records are not forged as static Rust callsites.
- Feed the same normalized router into the existing Rust terminal formatter
  and NDJSON layer. Prefixes, level/source labels, multiline/throwable layout,
  hyperlinks, filtering, and duplicate suppression are implemented as shared
  projections rather than one formatter at the JVM pipe boundary and another
  in the JSON sink.
- Prove bounded queue/backpressure/drop counts, flush on normal exit, abrupt
  JVM loss, malformed/oversized frame isolation, bridge disconnect/reconnect,
  and fail-open fallback to ordinary console/file behavior. A bridge failure
  never recursively logs through itself or blocks the render/logging thread.
  A transition fixture drives healthy bridge, failed-open fallback, and
  reconnected bridge states. Each stable marker has exactly one terminal
  presentation and one normalized-record identity across the transition;
  fallback raw-console evidence is explicitly attributed and is not mistaken
  for a second structured event.
  Normal shutdown performs a bounded drain and emits a terminal stream record
  containing final Java/Rust source sequences plus queued/sent/dropped counts.
  Timeout, crash, or transport loss marks the artifact tail as truncated with
  the last observed sequence; absence of a terminal record is never presented
  as a complete stream.

**Observable proof:** The exact command runs a marker fixture, and one query of
`abc.ndjson` finds Rust, Java SFM, generic third-party Log4j, and process-stdio
records with the expected identities. The live terminal projection and NDJSON
record ids agree, Forge files remain available according to LOG-X0, and no
structured Log4j message is duplicated merely because it also had a console
appender.

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

### [ ] LOG-X5 Query profiles and validate semantic lifecycle machines

- Add structured queries over runtime, logger/target, level, event/message id,
  source URI/span, ancestor span, correlation/request/action/document id, and
  per-source sequence. “Between/inside spans” follows recorded span ancestry
  and lifecycle, not naive NDJSON line-number ranges or cross-process wall-time
  sorting.
- Define named include/exclude profiles such as ordinary SFM interaction,
  dependency initialization, and maximal diagnostics. Prefer producer-declared
  categories/event ids; third-party fallback classification may use logger,
  marker, message template hash, and explicit reviewed rules, never only the
  localized/rendered sentence.
- Permit agents and puppets to sample/query the completed NDJSON artifact and
  summarize repeated warnings, drops, latency outliers, and invalid sequences
  without requiring the human to notice them in a live console.
- Build entity-scoped lifecycle validators from stable semantic observations,
  correlation ids, and authoritative action/episode records. For example,
  `editor.opened`, `editor.dirty.changed`, `editor.close.requested`,
  `editor.save.completed`/`editor.discard.confirmed`, and `editor.closed` can
  prove that a dirty document was not silently discarded.
- Logs remain observations rather than replay or product-state authority. A
  validator may report a missing/forbidden transition and fail a test, but a
  rendered log sentence never manufactures an action, save, approval, or
  causal ordering.

**Observable proof:** A query selects Java and Rust records under one request
and source span while excluding a named dependency-initialization profile. A
fixture with a valid editor close passes; a dirty-close trace lacking save or
discard confirmation produces a versioned, source-linked invariant violation.

### [ ] LOG-X6 Attach terminal and shell consumers to an exact launch observation stream

- Keep the normalized observation router independent from presentation. Give
  each Rust launcher process, game instance, run, and observation stream a
  distinct stable identity and authenticated local attachment capability.
  Selection must resolve an exact compatible stream or fail visibly; PID,
  newest-focus, and newest-run conveniences report the identity they chose.
- Add direct long-form shell operations such as `sfm logs list` and
  `sfm logs follow --instance <selector> --run <selector>` using the existing
  in-game control discovery rather than another descriptor directory. A Teamy
  Terminal inside Minecraft is one caller and one presentation device; it is
  not the observation transport or authority.
- Permit an in-game terminal panel to attach to the Rust launch/session that
  owns the current game and render the same terminal/NDJSON projections as an
  external console. Attach and detach do not create another Minecraft process,
  PTY, or log stream. Multiple Rust launchers, games, and terminal devices must
  not cross-wire records or input leases.
- Preserve LOG-X3 as an optional rich structured consumer for filtering,
  source jumps, and lifecycle inspection. The terminal-follow slice may ship
  first because it reuses the already planned terminal; neither consumer owns
  capture, retention, or correlation semantics.
- Record bounded backlog/follow-tail behavior, pause, cancellation, reconnect,
  retention gaps, drop counts, source hyperlinks, bilingual projections, and
  bridge fail-open state. Attaching after launch reports the retained interval
  honestly rather than implying it observed process start.

**Observable proof:** Start Minecraft through `sfm-propagate-changes.exe run
client`, list the exact run/game/stream identities, follow the stream from the
external shell, attach an in-game Teamy Terminal to the same stream, and see one
correlated Java/Rust action in both projections with identical record ids.
Launch a second game and prove explicit/default selection cannot mix the two;
detach the in-game consumer without affecting capture or the external follower.

## Current architecture and constraints

- `SFM.LOGGER` is the normal Log4j logger used throughout client, common, and
  server code. Most calls use parameterized strings (`{}`) and some include a
  throwable. These messages are not automatically localization keys.
- `TranslatableLogger` creates a separate Log4j `LoggerContext` and attaches a
  `TranslatableAppender` to each manager logger. Its records retain
  `TranslatableContents`, timestamps, and levels so the existing manager Logs
  screen can localize them on the client. Its current ordinary
  `logger.info(...)`/`warn(...)` forwarding obscures the application callsite;
  a caller-aware `ExtendedLogger`/FQCN path is required before its events can
  truthfully identify the source line that invoked the wrapper.
- The pinned Log4j 2.17 runtime can expose `LogEvent.getSource()` as a
  `StackTraceElement` with class, method, source filename, and line. When the
  event did not already carry source data, its location calculation constructs
  a `Throwable`, materializes the stack trace, and scans beyond the logger
  FQCN. It has no JVM source-column metadata. Consequently, caller capture is
  an opt-in diagnostic cost for the SFM namespace, never an accidental
  root-logger tax on every Minecraft/Forge/mod event.
- Exact canonical source URI, column, byte/glyph span, source-set/branch, and
  source hash can be enriched from a versioned callsite manifest generated by
  the Rust-owned build from the Java syntax tree. Runtime location and manifest
  evidence remain separately attributed; a missing, stale, or same-line
  ambiguous manifest entry degrades honestly instead of claiming an exact
  hyperlink.
- `LocalizationEntry` already gives SFM stable translation keys plus English
  defaults, and `TranslatableLogger` proves client-side localized rendering.
  Most other `SFM.LOGGER` calls are untyped English templates, however, so
  bilingual/global logging requires a semantic message facade rather than
  treating arbitrary English strings as localization keys.
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
- The exact `run client --branch 1.19.2 --log-file abc.ndjson` workflow emits a
  parseable combined artifact containing Rust, Java SFM, generic captured
  Log4j, and separately typed process-stdio records through one normalized
  router and shared formatter policy.
- Java observation scopes survive supported asynchronous handoffs and expose
  truthful parent/lifecycle evidence for span queries and state-machine checks;
  missing ancestry remains explicit rather than inferred from timestamps.
- Rust records preserve tracing callsite metadata. SFM-authored Java records
  preserve the true application caller through direct and wrapper-based logging
  and expose class/method/file/line in NDJSON plus a clickable source target.
  When a matching generated manifest exists, that target includes canonical
  URI, column, exact span, and source hash; stale or ambiguous enrichment is
  visibly diagnosed.
- Java source-location capture is measured in disabled, SFM-only, and manifest-
  enriched profiles. The default release profile does not force stack walking
  for unrelated Minecraft, Forge, or third-party-mod events.
- Capture scope, location cost, localization projection, and retention are
  independent effective policies with recorded CLI/environment/config/default
  precedence. Developer console-equivalent capture may retain third-party
  records for filtering without translating them or requesting their source
  location.
- New and migrated SFM-authored semantic records retain stable message id,
  localization key, English fallback, typed/bounded arguments, structured
  fields, and throwable separately. English, localized, bilingual, and auto
  projections are deterministic, and a non-English bilingual export remains
  intelligible to both the user and an English-speaking support channel.
- Audits report direct `System.out`/`System.err`, `printStackTrace`, raw Log4j
  acquisition/calls, and direct `SFM.LOGGER` use outside the approved facade,
  with narrow named allowances for tests, protocol/bootstrap boundaries, and
  migration. The report is staged so legacy callsites can be retired without
  hiding the remaining count; release-ready SFM production coverage has zero
  unresolved direct callsites.
- Normal combined-stream completion is proven by a terminal sequence/count
  record. Missing/abrupt tails are marked truncated and cannot masquerade as a
  complete artifact.
- The combined observation stream is inspectable in-game without opening a
  terminal and can jump to correlated episode/action/request context.
- The standalone console works from the title screen and in-world; Draw
  embedding is optional until its ephemeral-layer semantics are proven.
- Supported-version audits, tests, and live captures pass before release scope
  is expanded.

## Explicit non-goals for the first slice

- Replacing Minecraft/Forge's global log viewer or changing Log4j configuration
  for unrelated mods.
- Requiring source-location stack walking for the global/root logger, rewriting
  Java source or bytecode merely to inject callsite literals, or claiming that
  a JVM stack frame contains a source column it does not provide.
- Translating third-party Minecraft/Forge/mod messages, or making SFM infer a
  semantic localization key from arbitrary rendered English text.
- Treating every existing English `SFM.LOGGER` format string as a translatable
  localization key.
- Treating `SFMConsoleWidgetLogger`'s initial `TranslatableLogger` backend as if it captured the
  global Minecraft/Forge Log4j stream; global capture remains a separate
  producer adapter.
- Persisting an unbounded history, uploading logs, or exposing server logs to
  clients without an explicit network contract.
- Making the Draw document's saved program content include the live console by
  default.
