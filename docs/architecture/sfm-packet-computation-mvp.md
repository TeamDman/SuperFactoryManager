# SFM packet computation: single-player MVP

**Status:** Slices A through D complete
**Implementation branch:** `feat/1.19.2/packet-computation`  
**Implementation baseline:** `1be4b3cff9387f0b2870bc281017b3589f0119ef`  
**Design-inspection baseline:** `53b9b302117289c945def6ed73297f2997901559`

This is the maintained implementation contract for the packet-computation
vertical slice. It preserves the confirmed product decisions from the
12 September 2026 design handoff and records the concrete defaults needed to
implement each slice without letting incidental code define public behavior.

The implementation baseline is one commit newer than the inspection baseline.
That drift is limited to release-review, Explorer, generated localization, and
`sfm-propagate-changes` review surfaces. The packet carrier, resource movement,
program execution, network registry, and control-protocol foundations named by
this design did not change between those commits.

## Product circuit

The MVP proves one best-effort loop in a private integrated single-player
world:

1. A manager copies text from a disk or book.
2. It constructs a generic request value with a fresh user-visible job ID.
3. A lazy `sfm:packet` input materializes the request as an item.
4. `broadcast` observes that value without consuming the item.
5. A desktop worker lists received values through the existing `sfm` control
   bridge and may send a response to an explicitly addressed inventory.
6. The server attempts ordinary item-handler insertion on its game thread.
7. A normal timed manager trigger can match and route the response item.

Inventories remain the persistent mailbox. The transport does not promise
delivery, acceptance, completion, retry, replay, or exactly-once execution.

## Confirmed semantic contract

| ID | Requirement | Observable consequence |
| --- | --- | --- |
| C01 | Packet effects are restricted to private integrated single-player worlds for the MVP. | Dedicated, remote, and LAN-published worlds cannot use the effect boundary. |
| C02 | Delivery is best effort. | There is no built-in ACK, retry, durable job table, offline queue, or replay. |
| C03 | Managers in unloaded chunks miss events; inventories are the mailbox. | No manager-specific inbox or chunk ticket is introduced. |
| C04 | Downstream work uses ordinary timed triggers and resource movement. | No `await`, suspended continuation, or packet scheduler is introduced. |
| C05 | The client log exposes generic received values without application-shape filtering. | Workers may ignore values; SFM does not admit only Request/Ack/Response objects. |
| C06 | `sfm:packet` is one generic item carrying structural data. | Request, ACK, and Response are values, not registered item types. |
| C07 | Named types are structural aliases. | Equivalent patterns do not gain nominal identities. |
| C08 | `like` pattern-matches and open object patterns retain extra fields. | Matching filters/binds complete values rather than returning booleans. |
| C09 | Relations preserve occurrences, including equal values. | Equal source texts still produce independent rows and job IDs. |
| C10 | Values read from items have copy semantics. | Later source-item mutation cannot mutate a captured value. |
| C11 | Resource creation is lazy and memoized per occurrence. | First demand constructs once; later peeks/output use the same logical resource and ID. |
| C12 | `broadcast` peeks and never consumes. | Repeated observation is allowed and output remains possible. |
| C13 | Created items live in context-owned ephemeral storage until moved. | Unmoved leftovers are discarded at context teardown. |
| C14 | `forget` removes active input entries but not variables. | Captured values and bindings survive input forgetting. |
| C15 | Input statements accumulate. | An unfiltered output may move both the source text item and generated packet. |
| C16 | Serverbound sending targets an ordinary inventory. | It may create one legal packet item there without an open menu, but no arbitrary item. |

## Frozen Slice A defaults

These defaults close the former design choices for the packet circuit. A later
change must update this section and its boundary tests deliberately.

| Surface | Frozen behavior |
| --- | --- |
| Single-player gate | Effects are allowed only while the local player owns a private integrated world. Opening to LAN disables them. The gate is rechecked at execution time on both effect boundaries. |
| Receiver | The request carries an explicit dimension identifier and block position plus an optional side. No side means one unsided item-handler lookup. A missing requested/unsided handler is a drop; faces are never probed as fallback. |
| Value kinds | JSON-compatible null, Boolean, UTF-8 string, signed 64-bit integral number, array, and string-keyed object. Floating point, exponent notation, and out-of-range integers are rejected. |
| Canonical JSON | Object keys are written in Unicode code-unit order. Duplicate keys, malformed Unicode, comments, trailing values, and lenient JSON forms are rejected. |
| Value limits | At most 3,072 UTF-8 bytes of canonical value JSON, nesting depth 16, and 256 members/elements in any one container. Oversize values are rejected whole, never truncated. |
| Codec version | Item and network envelopes begin at version `1`. An unsupported version is rejected rather than guessed. |
| Forge channel version | The two new append-only message IDs raise the exact-match channel protocol from `1.0.0` to `1.1.0`, so mixed old/new endpoints fail compatibility checks instead of decoding the wrong contract. |
| Client log | Per-world-session, in-memory, non-destructive cursor log. A UUID distinguishes sessions; sequences restart at 1. Retain at most 256 entries and 1 MiB of canonical value payloads, evicting oldest first. Default page size is 50; maximum is 100. Oldest/newest sequences and contiguous, evicted-gap, session-changed, and cursor-ahead states are explicit. |
| Structured action result | Existing invoke results gain one optional schema ID plus one machine JSON object, bounded to a 128-byte schema ID and 512-KiB JSON payload. Packet schemas begin with `sfm.packet.list/1` and `sfm.packet.send/1`; human feedback remains separate. Publishing more than one machine result fails the action. |
| Control compatibility | The generated Vox invoke-result schema and descriptor protocol advance together from version 1 to 2. Existing actions keep their source/executor call sites and return an absent structured result; old CLI/game pairs fail the existing protocol-version check rather than misdecode the changed record. |
| List action | `sfm:packet/list` accepts optional `after`, `limit`, and `session` terms in that order. Its JSON reports requested/current sessions, oldest/newest sequences, retained count/bytes, continuity, entries, the last returned sequence as the next cursor, and `has_more`. A stale session is explicit and returns no current-session entries. |
| Send action | `sfm:packet/send` accepts an exact dimension and integer block position, optional `side` then `session` terms, and one strict bounded value. Status is one of `send_attempted`, `effects_disabled`, `session_changed`, or `no_session`; only `send_attempted` sets local transport acceptance, and no status claims inventory delivery. |
| Packet stacking | `sfm:packet` has a maximum stack size of 64. Packets stack only when their complete item data, including codec version and canonical payload, is equal. Every item count is one packet occurrence; stacking never deduplicates application events or job IDs. |
| Observation identity | One observation statement emits at most the largest eligible quantity for a physical resource type/handler/slot, even when overlapping input matches produce duplicate handles. Equal values in distinct slots or generated handlers remain distinct occurrences. Independent input declarations do not make duplicate views of the same physical slot into new occurrences. |
| GameTest distribution | `@SFMGameTest` reuses the existing `SFMDist[]` physical-side vocabulary and defaults to client plus dedicated server. Integrated-client packet tests declare `SFMDist.CLIENT` and are excluded from dedicated-server discovery before their classes are loaded. Client-action contributor classes are likewise not initialized on a dedicated server. |
| Item availability | The carrier has no recipe and is not listed as a blank creative-tab item. Production creation is limited to packet construction/sending paths; operator commands remain ordinary Minecraft authority. |
| Initial model | The generated model uses the vanilla paper texture as a neutral carrier icon. Bespoke packet art is outside A1. |

The 3,072-byte value cap fits beneath the raw 4,096-byte control-token limit,
but quote and backslash escaping can make its reconstructed Brigadier token
larger than 4,096 bytes. A3 measures the exact quoted/escaped UTF-8 form on
both Rust and Java. Content that fits the value codec but exceeds that framing
boundary is rejected whole before dispatch; the value cap is not advertised as
an unconditional CLI capacity.

## Integrated slices

Only one integrated slice is active at a time. Internal checkpoints do not
turn partial mechanics into a completed public feature.

| Slice | Scope | Exit evidence |
| --- | --- | --- |
| A — Packet circuit | Immutable value/codec, packet item, handlers, single-player gates, world-session log, list/send actions, structured results, and codegen. | A real private integrated world sends a known value to the client; the CLI lists it and sends a response into a loaded inventory without a menu. |
| B — Execution ownership | Input-source abstraction, generated handler, context variable/owner separation, exception-safe cleanup, simulation hooks, and observation path. | One generated packet is memoized, repeatedly peeked, moved normally, and cleaned on normal/exceptional teardown. |
| C — Language and text | Aliases, object patterns, occurrence relations, copied values, text read, lazy create, broadcast, source mapping, and completion. | Disks/books produce distinct per-occurrence requests; empty input creates nothing; the same packet value is broadcast and output. |
| D — Player circuit | ACK/Response routing, deterministic worker fixture, examples, diagnostics, changelog, and installed-tool handoff. | The complete documented journey passes, including duplicate and loss cases. |

Slice A is implemented through these bounded checkpoints:

- **A0 — contract:** this document, frozen defaults, and requirement ledger.
- **A1 — value/carrier:** immutable values, strict bounded canonical codec,
  stackable packet item, item storage adapter, localization/model, and focused
  tests.
- **A2 — network/log:** packet directions, effect gates, world session, bounded
  log, and addressed insertion.
- **A3 — control/CLI:** structured invoke result, generated protocol, packet
  list/send actions, and machine-readable CLI output.
- **A4.0 — test topology:** physical-side GameTest declarations, scan-time
  filtering before class loading, and dedicated-server-safe client-action
  contributor initialization.
- **A4.1 — terminal echo:** a puppet-owned server fixture emits a request,
  the real in-game terminal runs the checkout-local `sfm.exe`, and the response
  reaches the fixture's exact loaded chest.
- **A4.2 — negative journeys:** prove the private-world/LAN boundary and
  representative best-effort loss cases without implying acknowledgement or
  retry. Complete: puppet-owned live journeys cover both boundaries while
  remaining outside ordinary GameTest discovery.

A2 is split at effect boundaries so the transport can be reviewed before it
owns retained state or inventory mutation:

- **A2.1 — transport contract:** explicit Forge play directions, receive-context
  direction checks, private-integrated-world gates on both sending and receipt,
  bounded versioned value envelopes, clientbound observation messages, and
  serverbound exact-address insertion messages.
- **A2.2 — client session log:** append accepted observations to the bounded
  per-world-session cursor log and reset it across world/session transitions.
- **A2.3 — addressed insertion:** resolve the requested loaded dimension,
  position, and optional face and attempt ordinary item-handler insertion.

A2.1 deliberately left its two validated handoff methods empty. A2.2 fills the
client handoff with the synchronized per-session log. A2.3 fills the server
handoff with one-shot, exact-address item-handler insertion; it does not retain
failed requests or report delivery to the client.

Slice B is split so its execution-lifetime changes remain independently
reviewable before language syntax depends on them:

- **B1 — input-source abstraction:** `ProgramContext`, ordinary output, and
  forgetting operate on `ProgramInputSource`; the existing labelled
  `InputStatement` owns its current gather/cache/forget behavior behind that
  interface. No grammar or gameplay behavior changes.
- **B2 — context ownership:** completed by separating trigger-local variable
  state, active input views, and the owner of ephemeral generated resources.
  World-input slot caches now live on execution-local views rather than cached
  `InputStatement` nodes; no new mutable values or generated stacks live in the
  cached AST/program.
- **B3 — lazy generated source:** completed with an ephemeral
  item-handler-backed source whose constructor, immutable value, and carrier
  are evaluated and memoized once per occurrence on first value or resource
  demand. Drained handlers release themselves from the execution owner, while
  forgotten materialized handlers remain owner-held until trigger teardown.
- **B4 — lifetime and simulation:** completed with exception-safe trigger,
  execution-scope, and ephemeral-owner teardown. Simulation/lint behavior
  rejects lazy runtime materialization before constructors, GUIDs, items, or
  effects can be created.
- **B5 — observation path:** completed with detached stack snapshots and a
  transactional tracker view that applies current shared/expanded quantity and
  retention state without extraction or live bookkeeping. Stack counts remain
  occurrence counts, duplicate physical handles collapse, and normal output
  quantity remains available.
- **C — language and text:** completed with structural aliases and open object
  patterns, occurrence-preserving relations, copied disk/book text adapters,
  lazy object/GUID construction, filtered/bound inputs, lazy packet creation,
  non-consuming player broadcast, source mapping, syntax highlighting, parser
  round trips, and completion candidates. The VS Code grammar source mirrors
  the game grammar; its generated TypeScript remains build-generated.
- **D — player circuit and handoff:** completed with ordinary ACK/Response
  routing, a manager-absent mailbox-persistence journey, a deterministic
  terminal worker that lists a language-created request and sends an ACK plus
  two equal responses, localized broadcast diagnostics, a complete template,
  changelog coverage, and checkout-local CLI verification.

The production byte cap is intentionally retained even though it is currently
secondary: 256 values at the 3,072-byte value maximum total 786,432 bytes,
below 1 MiB. Tests use smaller injected limits to exercise byte-first eviction
so a future value/count change cannot silently invalidate that invariant.

### A3 command surface

The dedicated CLI hides Brigadier quoting and emits the action's machine JSON
object directly for `--output-format json`; text output keeps the same payload
under a stable action/schema/instance/result summary. Generic `sfm invoke`
continues to work and now reports whether a structured result is present plus
its schema and JSON string.

```text
sfm packet list [--after-sequence N] [--limit 1..100] [--session-id UUID] [instance selector]
sfm packet send DIMENSION X Y Z VALUE_JSON [--side FACE] [--session-id UUID] [instance selector]
```

For a negative positional coordinate, place named options before the standard
`--` terminator. For example:

```text
sfm packet send --side north -- minecraft:overworld 12 64 -7 '{"value":1}'
```

## Requirement and test ledger

| Requirement | Owning slice | Required evidence | Current state |
| --- | --- | --- | --- |
| C01 | A2/A4 | Unit/negative network tests plus private integrated-world proof | Covered: A4.1 proves the complete private integrated-world terminal circuit; A4.2 publishes that same integrated server to LAN, observes `effects_disabled` with `local_transport_accepted=false`, verifies the server-side gate is closed, and finds no packet in the target chest |
| C02 | A2/A4 | Missing/full/unloaded destinations produce no retry or delivery claim | Covered: A2.3 drops each condition without loading chunks, retaining requests, or acknowledging delivery; A4.2 attempts missing-dimension, unloaded, no-handler, and full targets through the real terminal/CLI, observes only local `send_attempted` with no delivery acknowledgement, and proves no delayed replay after capacity becomes available |
| C03 | A2/D | Mailbox-loaded/manager-unloaded journey | Covered: the server-safe mailbox-persistence GameTest inserts into an already-loaded ordinary chest while no manager runtime exists, retains the packet through a 20-tick inactive interval, and then routes it with a newly started ordinary timed manager; no replay or manager-specific storage participates |
| C04 | C/D | Ordinary timed-trigger response routing | Covered: D routes ACK and Response values from an ordinary inventory with a normal 20-tick trigger, both in a dedicated-server GameTest and after external terminal/CLI delivery in the complete player circuit |
| C05 | A2/A3 | Arbitrary shapes survive log/list unchanged | A2.2 packet-to-log GameTest covers arbitrary nested value retention; A3 returns canonical nested values directly in bounded list JSON and its registered-action GameTest observes the real client log |
| C06 | A1 | One `sfm:packet` item round-trips every supported value kind | Covered by the all-kind codec round trip and registered-item GameTest |
| C07 | C | Equivalent alias-pattern tests | Covered: aliases resolve to structural patterns; equivalent aliases match the same values and GUID remains an unbranded canonical string constraint |
| C08 | C/D | Open-object matching retains extra fields | Covered: C unit coverage proves successful matches retain the complete object and missing/wrong fields contribute no match; D routes complete ACK/Response values containing the undeclared `worker` field, while wrong-type and invalid-GUID near-matches remain in the mailbox |
| C09 | B/C | Equal inputs retain separate occurrence rows and IDs | Covered: B establishes identity-owned sources and observation; C relation mapping preserves reference identities, and the integrated language circuit turns two equal physical prompts into two requests with distinct GUIDs |
| C10 | A1/C | Immutable construction and copied item-read tests | Covered: C snapshots selected item resources and reads disk, writable-book, and written-book content from copies; later source mutation cannot alter the captured value |
| C11 | B/C | Peek/peek/output uses one memoized generated item | Covered: B proves demand-order memoization and repeated observation; C registers one lazy packet source per relation row and the integrated language circuit broadcasts the exact values later moved by ordinary output |
| C12 | B/C | Repeated broadcast does not consume output quantity | Covered: B proves repeated read-only observation leaves live transfer budgets unchanged; C's language-level broadcast observes the generated values before ordinary output moves the same carriers |
| C13 | B | Normal/exceptional context teardown frees leftovers | Covered: B2 provides context-owned, identity-based, exactly-once cleanup; B3 proves unmoved generated handlers are cleared at normal teardown and drained handlers release early without affecting moved items; B4 frees every trigger fork and root context through `finally`, continues after individual cleanup failures, and preserves the trigger exception as primary when cleanup also fails |
| C14 | B/C | Bare/selective forget clears inputs but retains variables | Covered: B separates variables from views and proves bare/selective source behavior; C bindings live only in the trigger-local variable environment and filtered-source forgetting cannot delete them |
| C15 | B/C | Accumulated source/generated inputs move independently | Covered: the integrated C circuit accumulates the selected world input plus three generated sources, then ordinary unfiltered output moves all three original text items and all three independently generated packets |
| C16 | A2/A4 | GUI-closed loaded-inventory insertion and negative destinations | Covered: A2.3 exercises unsided/exact-face success and negative destinations without an open menu; A4.1 runs the external CLI through the in-game terminal and delivers its response to the exact loaded chest after the terminal closes |

Cross-cutting A1 checks also cover malformed/oversize JSON, nesting/container
limits, integral-number bounds, deterministic object ordering, unsupported item
codec versions, and identical-versus-distinct stack payload behavior.

A1 exit evidence on 12 September 2026:

- `test run --filter SFMValueJsonCodecTests`: 4/4 passed.
- `game-test run-client --filter packet_item`: 1/1 required GameTest passed.
- `run data`: completed and generated the packet localization/model.
- `run compile`: all main, gametest, datagen, and test source sets compiled.

The dedicated GameTest server is not A1 evidence because the implementation
baseline has a pre-existing dedicated-server classload failure in
`SFMCommandPaletteActions`; it exits before discovering any GameTest. The
integrated client result above exercises the same registered item and NBT
behavior in the supported runtime.

A2.1 focused evidence on 12 September 2026:

- `test run --branch feat/1.19.2/packet-computation --filter SFMPacketContractTests`:
  5/5 passed, covering canonical round trips at the byte cap, malformed,
  oversized and truncated rejection, unsupported versions, pre-dispatch gate
  rechecks, both message directions, and sided/unsided exact addresses.
- `test run --branch feat/1.19.2/packet-computation --filter SFMPacketEffectGateTests`:
  1/1 passed; only an owned, unpublished integrated world is allowed.
- `game-test run-client --branch feat/1.19.2/packet-computation --filter packet_item`:
  1/1 required GameTest passed after Forge registered the two new explicitly
  directed messages.
- `run compile --branch feat/1.19.2/packet-computation`: all source sets compiled
  after A2.1 implementation.

A2.2 focused evidence on 12 September 2026:

- `test run --branch feat/1.19.2/packet-computation --filter SFMPacketObservation`:
  7/7 passed, covering non-destructive paging, duplicate preservation,
  count/byte eviction, explicit cursor discontinuities, immutable pages,
  512 concurrent appends, session rotation/teardown, and disabled-gate refusal.
- `game-test run-client --branch feat/1.19.2/packet-computation --filter packet_observation_log`:
  1/1 required GameTest passed; a nested generic value crossed the real
  server-to-client channel and appeared exactly once in the active client log.
- `run compile --branch feat/1.19.2/packet-computation`: all source sets compiled
  after A2.2 implementation.

A2.3 focused evidence on 12 September 2026:

- `test run --branch feat/1.19.2/packet-computation --filter SFMPacketInventoryInserterTests`:
  2/2 passed, proving a sided or unsided address causes exactly one capability
  lookup and never face fallback.
- `game-test run-client --branch feat/1.19.2/packet-computation --filter packet_insertion`:
  1/1 required GameTest passed. Real client-to-server messages inserted exactly
  one packet through both unsided and explicit-face handlers while the owner had
  no container menu open.
- `game-test run-client --branch feat/1.19.2/packet-computation --filter packet_insertion_negative`:
  1/1 required GameTest passed. Missing dimension, unloaded position,
  absent/full handler, wrong face, and absent unsided handler requests were
  dropped; an ordered successful request proved the negative messages had been
  processed.
- `run compile --branch feat/1.19.2/packet-computation`: all source sets compiled
  after A2.3 implementation.

A3 focused evidence on 13 September 2026:

- `platform/cli/sfm: .\check-all.ps1`: formatting and clippy passed with
  warnings denied; 56/56 tests and doctests passed; generated Java matched the
  Rust-authoritative Vox schema.
- `platform/cli/sfm: cargo test --locked --offline`: 56/56 passed, including
  protocol compatibility, optional/invalid structured results, exact escaped
  4,096-byte boundaries, quote-heavy value rejection, dedicated packet CLI
  parsing/action tokens, typed result-envelope validation, and direct JSON
  versus stable text rendering.
- `platform/cli/sfm: cargo run --locked --offline --features codegen --bin
  sfm-codegen -- --check`: generated Java matched the Rust-authoritative Vox
  schema after the protocol version/result extension.
- `test run --branch feat/1.19.2/packet-computation`: 2,093 tests found; 2,088
  passed, 0 failed, and five opt-in fixture tests were assumption-aborted.
- The same suite included 7/7 `SFMPacketActionsTests`, covering bounded cursor
  output, no-session and disabled-effect results, stale
  session refusal, exact address/side/value forwarding, local-only send status,
  and strict value rejection.
- `game-test run-client --branch feat/1.19.2/packet-computation --filter
  packet_control_actions`: 1/1 required GameTest passed. A real server
  observation was returned by the registered list action and the registered
  send action crossed the existing client/server transport into its exact
  loaded chest.
- `run compile --branch feat/1.19.2/packet-computation`: the final exact branch
  compiled through the source-matched propagator with no propagation.
- `platform/cli/sfm: .\install.ps1`: the locked/offline build installed to
  `G:\Programming\Caches\CARGO_HOME\bin\sfm.exe`; installed and worktree
  release binaries had matching SHA-256
  `0C693D5E9EA5BB627258FF08AD169CB85F2376709CF17B464FD9D5C6DA949E3A`,
  and version/packet help smokes passed.

A4.0 test-topology evidence on 13 September 2026:

- `game-test run-server --branch feat/1.19.2/packet-computation --filter
  packet_*`: the dedicated server skipped the four integrated-client packet
  tests from scan metadata before loading their classes, selected only the
  dual-compatible `packet_item` test, and passed 1/1 required tests.
- `game-test run-client --branch feat/1.19.2/packet-computation --filter
  packet_*`: the integrated client selected and passed all five packet tests.
- The dedicated-server launch also proves client-action contributors are no
  longer initialized on the wrong physical distribution; common packet
  registration and the private-world effect gates remain unchanged.

A4.1 terminal-echo evidence on 13 September 2026:

- `puppet run in_world_packet_terminal_echo --branch
  feat/1.19.2/packet-computation --variant 1280x720@auto`: the command palette
  opened `sfm:terminal`; its real Teamy Terminal session invoked the
  checkout-local debug `sfm.exe`, listed exactly one fixture request under
  `sfm.packet.list/1`, and returned a response with local status
  `send_attempted` under `sfm.packet.send/1`.
- The puppet closed the pausing terminal before waiting on the integrated
  server, then the GameTest verified one exact response packet in its loaded
  destination chest. Durable terminal transcripts and screenshots cover both
  the CLI witnesses and final inventory state.
- The terminal fixture is passed directly to the puppet harness and is not
  annotated for ordinary GameTest discovery. A normal integrated-client
  `packet_*` run therefore selected and passed 5/5 tests rather than waiting
  for an absent terminal worker.
- A normal dedicated-server `packet_*` run selected only the dual-compatible
  `packet_item` test and passed 1/1; client-only tests and the puppet-owned
  fixture were not loaded.
- `run compile --branch feat/1.19.2/packet-computation`: all main, gametest,
  datagen, and test source sets compiled after the final fixture isolation.

A4.2 negative-journey evidence on 13 September 2026:

- `puppet run in_world_packet_lan_disabled --branch
  feat/1.19.2/packet-computation --variant 1280x720@auto`: the fixture first
  established that packet effects were available in the private integrated
  world, then the puppet published the server to LAN and invoked the real
  checkout-local `sfm.exe` through `sfm:terminal`. The send result reported
  `effects_disabled` and `local_transport_accepted=false`; the server-side
  execution gate was also false and the destination chest remained empty.
- `puppet run in_world_packet_terminal_loss --branch
  feat/1.19.2/packet-computation --variant 1280x720@auto`: the terminal invoked
  four invalid sends targeting a missing dimension, an unloaded position, a
  block without an item handler, and a full inventory. Each returned the
  deliberately local status `send_attempted`, while the CLI exposed that
  delivery was not acknowledged. A final valid barrier packet established
  ordering behind all four attempts.
- After the barrier arrived, the fixture verified that the invalid targets had
  not changed and that the unloaded target had not been loaded. It then freed
  one slot in the formerly full chest, waited 40 server ticks, and verified the
  slot remained empty, proving the failed attempt was neither retained nor
  replayed.
- Both A4.2 fixtures are passed directly to their puppet harnesses and remain
  unannotated. The final normal `packet_*` regressions therefore passed 5/5 on
  the integrated client and 1/1 on the dedicated server, unchanged from A4.0.
- The refactored A4.1 terminal echo was rerun after extracting shared fixture
  observation and source-matched CLI helpers; it again completed successfully.

B1 input-source-abstraction evidence on 13 September 2026:

- `ProgramInputSource` now defines resource gathering, source-owned forget
  transformation, and generic cleanup. `ProgramContext` stores that abstraction
  and `OutputStatement` consumes it without assuming a labelled AST node.
- Existing `InputStatement` behavior implements the abstraction directly. Its
  selective cache release, retained-label reconstruction, slot transfer, source
  mapping, and simulation-warning callbacks remain in the same order.
- `test run --branch feat/1.19.2/packet-computation --filter
  ProgramInputSourceTests`: 2/2 passed, covering abstract-source storage and
  cleanup plus source-controlled retain/drop replacement during forgetting.
- `game-test run-server --branch feat/1.19.2/packet-computation --filter
  forget*`: 4/4 required tests passed, covering bare forget, selective forget,
  slot filtering, and retained quantity state.
- `game-test run-server --branch feat/1.19.2/packet-computation --filter
  regression_input_retain*`: 4/4 required shared/expanded input-retention tests
  passed through ordinary output.
- The complete unit suite found 2,095 tests: 2,090 passed, none failed, and five
  opt-in integration fixtures were assumption-aborted as designed.
- `run compile --branch feat/1.19.2/packet-computation`: all main, gametest,
  datagen, and test source sets compiled after the final B1 changes.

B2 context-ownership evidence on 13 September 2026:

- Each trigger execution or simulated path now receives an isolated
  `ProgramExecutionScope` containing three separate stores: a variable
  environment, active input views, and an identity-owned ephemeral-resource
  owner. Normal teardown is idempotent and frees each owned resource once.
- `InputStatement` no longer implements `ProgramInputSource` or stores a
  limited-slot cache. Its execution creates a fresh `WorldProgramInputSource`,
  which preserves the established gather/cache/forget and diagnostic-source
  behavior without putting per-trigger state on the cached AST node.
- `test run --branch feat/1.19.2/packet-computation --filter
  ProgramExecutionScopeTests`: 5/5 passed, covering fork isolation, immutable
  variable snapshots, identity-based ownership, release/teardown behavior, and
  fresh world-input views from a reused AST statement.
- `test run --branch feat/1.19.2/packet-computation --filter
  ProgramInputSourceTests`: 2/2 passed after extending the forget test to prove
  its active-input replacement leaves trigger-local variables intact.
- Dedicated-server regressions passed 4/4 for `forget*`, 4/4 for
  `regression_input_retain*`, and 4/4 for `round_robin*`.
- The complete unit suite found 2,100 tests: 2,095 passed, none failed, and five
  opt-in integration fixtures were assumption-aborted as designed. That run
  also compiled the main, gametest, datagen, and test source sets with zero
  errors; test compilation retained 75 pre-existing `Unsafe` warnings.

B3 lazy-generated-source evidence on 13 September 2026:

- `GeneratedItemProgramInputSource` captures one occurrence constructor and
  materializes its immutable value, packet carrier, and one-slot item handler
  together on first value or resource demand. Both success and failure are
  memoized, and a source cannot cross execution owners.
- The handler is registered with the identity-based ephemeral owner. Complete
  extraction through ordinary movement releases an empty handler immediately;
  an unmoved or forgotten materialization remains owner-held and is cleared at
  scope teardown. The active source owns only its pooled limited-slot view.
- Generated limited slots carry an explicit source description and no fake
  block position, label, or side. Resource-loss diagnostics report that source
  without querying an unrelated world block.
- Bare `FORGET` is now represented as an explicit all-input operation instead
  of being expanded to the labels known while parsing. Selective labelled
  forget retains unlabelled generated sources, while bare forget detaches them;
  existing world-source cache transformation remains unchanged.
- `test run --branch feat/1.19.2/packet-computation --filter
  GeneratedItemProgramInputSourceTests`: 6/6 passed, covering both demand
  orders, single materialization, failure memoization, equal-occurrence
  independence, owner/view forgetting, normal teardown, and ordinary movement.
- `test run --branch feat/1.19.2/packet-computation --filter
  ProgramInputSourceTests`: 9/9 passed, including the generated-source suite
  selected by the shared name plus parser-level bare/selective forget evidence.
- `game-test run-server --branch feat/1.19.2/packet-computation --filter
  generated_packet_input_source`: 1/1 required test passed with the registered
  `sfm:packet` item and `sfm:item` resource type, proving repeated demands use
  one value/carrier and ordinary movement transfers it into world storage.
- `game-test run-server --branch feat/1.19.2/packet-computation --filter
  forget*`: all four established world-input forget regressions passed.
- The complete unit suite found 2,107 tests: 2,102 passed, none failed, and five
  opt-in integration fixtures were assumption-aborted as designed.

B4 lifetime-and-simulation evidence on 13 September 2026:

- Runtime resource materialization is an explicit `ProgramBehaviour`
  capability. Normal execution permits it, while simulation and lint behavior
  reject both value and resource demand before invoking the lazy constructor or
  registering an ephemeral resource.
- Every trigger fork and root execution context is freed from `finally`.
  Execution-scope and ephemeral-owner teardown attempt every remaining cleanup,
  are idempotent after failure, and aggregate later cleanup failures as
  suppressed exceptions; a trigger failure remains the primary exception.
- `test run --branch feat/1.19.2/packet-computation --filter
  GeneratedItemProgramInputSourceTests`: 8/8 passed, including denied
  simulation materialization and exceptional-trigger cleanup of a generated
  leftover.
- `test run --branch feat/1.19.2/packet-computation --filter
  ProgramExecutionScopeTests`: 7/7 passed, including cleanup continuation and
  trigger-versus-cleanup exception ordering.
- `game-test run-client --branch feat/1.19.2/packet-computation --filter
  generated_packet_input_source`: 1/1 required test passed after the runtime
  behavior boundary was made explicit.
- `game-test run-client --branch feat/1.19.2/packet-computation --filter
  forget*`: all four ordinary scheduled-program regressions passed through the
  new root/fork teardown paths.
- The complete unit suite found 2,111 tests: 2,106 passed, none failed, and five
  opt-in integration fixtures were assumption-aborted as designed.

B5 observation-path evidence on 13 September 2026:

- `ProgramResourceObserver` gathers active inputs lazily, filters before
  applying selection budgets, and returns detached stack snapshots. It neither
  calls extraction nor mutates a live input tracker.
- Each live input tracker supplies one per-observation transactional view.
  Hypothetical retention and transfer progress uses the same shared versus
  per-item expansion rules while incorporating any progress already present on
  the live tracker.
- Observation identity is resource type plus handler identity plus slot. The
  largest eligible overlap is retained once for that physical slot; equal
  stacks in distinct handlers remain separate. A stack amount is preserved as
  that many packet occurrences for the later broadcast adapter.
- `test run --branch feat/1.19.2/packet-computation --filter
  ProgramResourceObserverTests`: 5/5 passed, covering non-consuming
  quantity/retention, later output and depletion, ignored-resource filtering,
  physical overlap, distinct equal stacks, and all shared/expanded tracker
  groupings.
- `test run --branch feat/1.19.2/packet-computation --filter
  GeneratedItemProgramInputSourceTests`: 9/9 passed. Two observations reused
  one lazy construction and left all four generated units available for normal
  movement; mutating an observed snapshot did not mutate owned storage.
- `game-test run-server --branch feat/1.19.2/packet-computation --filter
  generated_packet_input_source`: 1/1 required test passed without a connected
  client. Three equal registered `sfm:packet` items stacked, were observed as
  three occurrences twice, and then moved together through ordinary output.
- The complete unit suite found 2,117 tests: 2,112 passed, none failed, and five
  opt-in integration fixtures were assumption-aborted as designed.

Slice C language-and-text evidence on 13 September 2026:

- The game and VS Code grammar sources accept player and structural-pattern
  declarations, `WITH CAPABILITY sfm:text` and `LIKE` input selections, `AS`
  occurrence bindings, text-read and object-construction expressions, lazy
  `CREATE INPUT sfm:packet`, and `BROADCAST TO`. Existing forward/reverse IO
  forms remain accepted, new keyword tokens remain usable as legacy labels,
  and Program rendering reparses successfully.
- AST construction rejects unknown aliases, unsupported capabilities and
  carriers, duplicate fields, and constructors that combine unrelated
  relation variables. Every new executable node and declaration has source
  mapping; editor highlighting and grammar-derived completion cover the new
  declaration and statement starters.
- `ProgramRelationTests`, `SFMValuePatternTests`, and
  `ProgramValueExecutionTests` cover reference-identity occurrences, lazy
  success/failure memoization, case-insensitive variable lookup, structural
  aliases, open-object matches retaining extra fields, missing/wrong-field
  rejection, copied book text, empty relation propagation, independent GUIDs,
  unrelated-relation rejection, and lazy source registration.
- `test run --branch feat/1.19.2/packet-computation --filter SFMLTests`: 51/51
  passed, including the complete proposed language surface, legacy keyword
  labels, declaration/executable source mapping, and parse/render/parse.
- `test run --branch feat/1.19.2/packet-computation --filter
  SFMLIntellisenseTests`: all completion regressions passed, including `LET` at
  program scope and `LET`/`CREATE`/`BROADCAST` inside a trigger.
- `game-test run-client --branch feat/1.19.2/packet-computation --filter
  packet_language_circuit`: 1/1 required client test passed. A real manager
  read one disk plus writable and written books, preserved two equal prompts as
  separate occurrences, allocated three distinct GUIDs, broadcast the three
  values to the actual client log, and moved those exact three packet carriers
  together with the three source items through ordinary output.
- The complete unit suite found 2,131 tests: 2,126 passed, none failed, and five
  opt-in integration fixtures were assumption-aborted as designed. The final
  compile also passed through `sfm-propagate-changes.exe` without propagation.

Slice D player-circuit evidence on 13 September 2026:

- The server-compatible packet routing test moves one open-pattern ACK and two
  equal Response occurrences while preserving their extra `worker` fields;
  wrong-type and invalid-GUID near-matches remain in the ordinary inbox.
- The mailbox-persistence test inserts a response into an already-loaded chest
  while no manager exists, observes it still present after 20 ticks, and then
  creates a normal manager whose timed trigger routes it. The grouped dedicated
  server run matched and passed all three compatible `packet_*` tests while
  excluding five client-only tests before class loading.
- `in_world_packet_language_worker` passed through the actual in-game Rust PTY.
  Its manager read disk text, constructed and broadcast a Request with a fresh
  `JobId`, and archived the same carrier. The terminal ran the absolute
  checkout-local `sfm.exe`, listed schema `sfm.packet.list/1`, sent one locally
  accepted ACK and two independently accepted equal Responses, and the second
  ordinary timed trigger routed all three while retaining the extra `worker`
  fields. The retained terminal artifact records
  `SFM_D_PACKET_RESPONSE_ATTEMPTS=2` and the three successful schema/status
  witnesses.
- `in_world_packet_terminal_loss` passed again through that source-matched CLI,
  covering missing dimension, unloaded position, absent handler, and full
  inventory with no delayed replay after capacity becomes available.
- The integrated client `packet_*` run matched and passed all eight packet
  GameTests: carrier, structured control actions, positive/negative insertion,
  observation log, language circuit, mailbox persistence, and response routing.
- The new `packet_computation.sfml` template compiles under `SFMLTests` and the
  generated English localization contains the offline-player and disabled-
  effect broadcast diagnostics. Datagen and the final source-set compile both
  completed through `sfm-propagate-changes.exe`.
- The checkout-local Rust CLI passed formatting, Clippy, generated-Java checks,
  and all 56 tests. Its `packet` help exposes the bounded `list` and `send`
  commands. The Java puppet harness injects its absolute path through
  `sfm.controlCliExecutable`; packet terminal actions require that property and
  never fall back to a PATH-installed executable.
- The complete Java suite found 2,131 tests: 2,126 passed, none failed, and five
  opt-in integration fixtures were assumption-aborted as designed.

## Exclusions

The MVP does not include a scheduler, durable job state, automatic ACK/retry,
client-authoritative inventory replacement, permissions/PKI, multiplayer,
Ollama integration, a shell runner, a VM rewrite, sign writing, general
relation joins, multicast/groups, or a separate companion daemon.

## Operational posture

- Dependency graph, declarations, and lockfiles remain frozen.
- Work happens only in the 1.19.2 feature worktree until propagation is
  separately authorized.
- Direct Gradle invocation is prohibited; use `sfm-propagate-changes`.
- Gameplay-visible changes update the SFM changelog.
- Generated localization and item models come from `run data`, not hand edits.
- A slice is not complete until its focused tests and a full compile pass.
