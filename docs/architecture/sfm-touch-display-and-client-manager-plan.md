# Touch Display and Client Manager: living implementation plan

Plan status: active. Baseline feature evidence covers static Touch Display interaction, the consented Client Manager frame runtime, typed invocation, bounded raster rendering, consent review, explicit author signing, exact-grant remote multiplayer and in-world terminal integration. Aggregate release checks and target propagation remain.

Last updated: 20 September 2026.

Intent audit: passed 17 September 2026 against the available user discussion and the pasted historical messages; some earlier assistant replies are unavailable except as pasted excerpts.

Current focus: R2 canonical 1.19.2 integration is validated and ready to checkpoint. The combined tree passed 2,530 unit tests, 339 dedicated and 354 integrated GameTests, and the 11-step file puppet. Preserve the recorded intermittent anvil test and inherited tooling snapshot failures below. The merge checkpoint and launcher installation precede later-version hops. Native physical Shift is not a required human acceptance boundary. P0 through P10 have baseline feature evidence; cross-target/release acceptance remains required. Phase checkpoints do not end the full goal.

Implementation branch: `feat/1.19.2/packet-computation`.
Starting baseline inspected: `4a99b69465e36b9f619f3380fe3f978166398afe`. Validated packet checkpoint: `43cfe001f`; finite-value/touch-schema checkpoint: `1a8cd9b84`.
Companion contract: `docs/architecture/sfm-packet-computation-mvp.md`. That document remains authoritative for the existing packet-computation slices A–D. This plan builds on them and does not redefine their completed behaviour by implication.

This is an evidence-led plan, not a calendar estimate. Work advances when its dependency and acceptance gates pass. The packet value, item, editor and terminal baseline was reconciled and committed in P0. Future implementation must inspect current worktree state and rerun validation against its final source.

Task state uses `[ ]` pending, `[~]` active, `[x]` complete and `[!]` blocked. Keep only one current focus unless tasks are explicitly independent. When updating this plan:

1. Change a task state only with its acceptance evidence or named blocker.
2. Record the exact validation command and result below that task; do not replace evidence with a calendar estimate.
3. Add changed user intent to the guidance ledger before changing a public contract.
4. Record a decision in the relevant gate and update dependent tasks before implementation resumes.
5. Preserve repository-relative references and never add workstation-specific paths or credentials.

## Intent audit and authority

This plan distils the available user discussion and the historical messages pasted on 17 September 2026. Known source limitation: some earlier assistant replies are available only through the pasted excerpts. Those proposals are context, not user decisions. Add later transcript corrections to the ledger before implementation.

| Audit pass | Check performed | Result |
| --- | --- | --- |
| 1. Extraction | Re-read the available user messages and the pasted historical user/assistant exchanges from first in-world screen idea through the later click-flow correction, then the 17 September full-goal clarification. | Added G32–G38 and separated the user's choices from superseded assistant proposals below. |
| 2. Traceability | Matched every active guidance entry to a contract, task, test, non-goal or open gate. | Corrected the static-fixture-first order, labelled-target proof, terminal follow-up and P0 test filter. The new full-goal instruction covers P1–P10, including P8G and P10; no calendar estimate or checkpoint is a completion condition. |
| 3. Adversarial review | Rechecked later user corrections against earlier assistant recommendations after those repairs. | Fixed-point UV, server-side `EVERY FRAME`, hard-coded sharing modes, exact visual/frame correlation, solid-block power derivation and a signing-stroke threshold remain superseded. The new authority to choose reversible details does not waive feature tests, security checks or unrelated repository permissions. |

20 September amendment audit: extraction records the new tooltip decoupling
request as G40. Traceability maps it to R1, the companion P03 contract, shared
tooltip consumers, registered actions, ambient GameTest and file-puppet proof.
The adversarial pass preserves configured-key behaviour as the default and
replaces the required human check, rather than claiming simulated native input.
The original guidance ledger remains in force; no operating-system automation
or dependency change is authorised by this amendment.

Evidence labels in this document mean:

- Confirmed: the user chose the product behaviour or repository/source inspection established the technical fact.
- Proposed: a concrete default that still needs implementation review.
- Open gate: a decision or proof required before an irreversible public contract.

## Guidance ledger

| ID | Status | User intent or constraint | Traced to |
| --- | --- | --- | --- |
| G01 | Confirmed | An in-world Touch Display shows raster content on a block face and receives direct surface presses without opening a GUI. | Display contract; P2–P4 |
| G02 | Confirmed | A press emits an `sfm:packet` item into an inventory behind the display. Item-first input is acceptable even though image output needs frame-rate rendering. | Click contract; P3 |
| G03 | Confirmed | Use `sfm:touch@1`, flattened dimension/position/face/action/UV fields, named `x`, `y`, `z`, and floating `u`, `v` in `[0, 1]`. | Schema; P1, P3 |
| G04 | Confirmed | Commit server-owned image, interaction state and revision atomically; disclose that a client-rendered red/blue frame may lag server state in the first slice. | Content revision; P2–P4, O8 |
| G05 | Confirmed | Add finite floating-point SFM values while retaining whole-number semantics for quantities and counts. | Value contract; P1 |
| G06 | Confirmed | Make Client Manager a distinct block and logical runtime, not merely the server manager on another physical distribution. | Execution contract; P5 |
| G07 | Confirmed | Allow optional `CLIENT BTW` or `SERVER BTW` at the top of a program as a host-side assertion; an omitted header remains compatible. | Grammar; P5 |
| G08 | Confirmed | Support `EVERY FRAME FOR displays AS display DO` for eligible labelled displays without chunk tickets or assumed sole control of the game window. | Scheduler and label binding; P6, P9 |
| G09 | Confirmed | A client program may change a frame through arbitrary logic every frame. Cached world/inbox reads and unchanged output suppress needless work; they do not restrict what the program may compute. | Scheduler and renderer; P6–P7 |
| G10 | Confirmed | Keep server semantic state lower cadence and client visual output high cadence; reuse `sfm:packet` value semantics while adding a deliberate bounded server-to-client delivery path. | Data flow; P4, P7 |
| G11 | Confirmed | Throughput is occurrence data. Polling a chest slot cannot prove how many equal stacks passed through it; the authoritative mover must report those occurrences if a dashboard needs exact counts. | Data flow; P7, P9 |
| G12 | Confirmed | Read directly available replicated block states, such as redstone dust `POWER`; do not prioritise inferred power inside opaque blocks. | Client read; P7 |
| G13 | Confirmed | Do not bake shared/private/published display modes into the block. Programs choose their data paths. | Display contract; P4, P7 |
| G14 | Confirmed | Client Manager actions should be available through a typed, permissioned registry; action use can include server-bound packet sending. | Actions; P8A–P8D, P8G |
| G15 | Confirmed | Design security, addressing, rate limits and bandwidth for multiplayer even while current packet effects remain private-integrated-world-only. | Security boundary; P8B, P8G |
| G16 | Confirmed | Client programs require explicit local consent. The initial grant may be long-lived but exact to program, world, dimension, position and capabilities. | Consent; P6A, P8D |
| G17 | Confirmed | Consent has absent, pending, approved and denied states. Denial must not permit repeated modal prompting; the player can inspect and manage grants. | Consent; P6A, P8D |
| G18 | Confirmed | Capability-level controls allow graceful degradation: lack of permission to draw or invoke an action need not erase every approved program behaviour. | Consent; P6A, P8D |
| G19 | Confirmed | Public-key author attribution and trusted signers are client-side trust aids. Private keys never go to the server or into a program. Preserve exact previous source for consent comparison. | Signatures; P8E |
| G20 | Confirmed | Signing is explicit after confirming the server-stored revision. A drawing pad is aesthetic; first pen contact may unlock Sign and its strokes do not prove identity. | Signatures; P8E, P8F |
| G21 | Confirmed | Ordinary client-only GameTests should construct and check the world without opening screens or monopolising keyboard/window focus. Opt-in file-driven puppets may prove actual in-world client interactions and GUI flows; do not use OS-level computer control for these game checks. | Tests; P2A, P3, P8F, P9 |
| G22 | Confirmed | `SFMDist.CLIENT` already marks integrated-client GameTests. Physical distribution is distinct from a program's logical execution side. | Tests; P5, P9 |
| G23 | Confirmed | Support image resources and static image movement through the generic resource system; live frame transfer is a different, latest-wins stream. | Images; P2, P7 |
| G24 | Proposed | Add readable interval syntax `EVERY ... GLOBAL TICKS` and `OFFSET BY`, preserving old programs through a migration window. | Grammar; P5 |
| G25 | Confirmed direction, deferred | An in-world rasterised terminal with touch-to-desktop input is a desired later experience. A structured test shell is a proposed way to avoid parsing PowerShell prompts. Neither is a prerequisite for the first press/display circuit. | Later integration; P10 |
| G26 | Confirmed | Focus on evidence and dependency gates, not day-based effort predictions. | Whole plan |
| G27 | Confirmed | Investigate whether vanilla use already carries UV before deciding to intercept the client click or reuse arbitrary client-requested packet insertion. | Click evidence; P3 |
| G28 | Confirmed | The client action input syntax uses Brigadier, but result typing and shared value schemas need investigation rather than assuming Brigadier covers them. | Action evidence; P8A, P8C |
| G29 | Confirmed | Account for the `ResourceLocation` to `Identifier` API rename on the 26.1.2 surface without treating legal hyphens as incompatible. | Identifier note; P5 |
| G30 | Confirmed | Keypad Enter parity, spiral-notebook packet art, progressive packet tooltip, Alt+D item inspection and the `sfm.controlCliExecutable` test override remain packet-foundation work governed by the companion contract. | P0; companion contract |
| G31 | Confirmed | RADIUS and general datagram exploration must not divert the Touch Display and Client Manager trajectory. | Non-goals |
| G32 | Proposed staging from the earlier vertical-slice recommendation | Prove the first one-face display with a deterministic test image and one press before making generic `IMAGE::` transfer a prerequisite. Later support both one-big-button and multi-button layouts through UV-aware programs rather than built-in widgets. | First circuit; P2A, P3, P2B |
| G33 | Confirmed | Use the existing label gun to name Touch Displays for `FOR displays AS display`; missing or competing targets need diagnostics. | P6B, O12 |
| G34 | Confirmed | A manager-facing content update must pair an image reference with arbitrary interaction state before advancing the server revision. | P2A, P2B, P3 |
| G35 | Confirmed | Touch packets are the first input mechanism; dedicated `EVERY TOUCH` grammar is a later convenience only after queue, ordering, overflow and unloaded-manager semantics are defined. | Non-goals; P10 |
| G36 | Proposed | Rename the whole-number grammar rule to `integer` for control quantities if doing so improves clarity; do not broaden the existing `NUMBER` control token to floating values. | P1, P5 |
| G37 | Confirmed | Pursue one goal to complete the entire remaining plan after P0, including later P8G multiplayer and P10 in-world terminal integration. Passing a phase checkpoint is progress, not a stopping point. | Full-goal scope; P1–P10; overall acceptance |
| G38 | Confirmed | Resolve in-scope design questions with best judgment during implementation, favouring testable reversible choices; refine with the user after observing working behaviour instead of waiting on hypotheses. | O1–O13; completion notes; validation |
| G39 | Confirmed on 19 September 2026 | Activate the separately negotiated multiplayer transport with default-deny, operator-owned grants for exact players, inventory targets, inbox channels and server-manager publishers. Preserve the legacy private-world gates. | P8G; operator policy persistence; remote transport acceptance |
| G40 | Confirmed on 20 September 2026 | Decouple the tooltip's alternate presentation decision from configured-key polling. Add command-palette actions that tests can actuate, so native physical Shift verification is not a required human acceptance boundary. | R1; companion P03; semantic mode, action and file-puppet tests |

Earlier proposals that later user choices superseded must not become implementation requirements: `sfm:touch/1` and scaled-integer UV, unique click sequence/frame generation, nesting the position/UV fields, server-manager frame triggers, permanent shared/private/published block modes, solid-block redstone inference, a minimum scribble-length requirement, and the claim that 26.1.2 `Identifier` implies Yarn mappings. Existing structured action-result schema IDs using `/1` also remain unchanged; `sfm:touch@1` does not trigger a global schema migration.

## The smallest product circuit

1. A server manager or authorised operator sets the Touch Display's server-owned `semanticContent`: an image reference, an arbitrary SFM state value and a content revision.
2. A nearby client renders a `renderedFrame`. A Client Manager may produce newer frames at render cadence without changing server semantic state or uploading unchanged content.
3. The player presses the display face. Minecraft delivers the block hit to the server through normal use handling.
4. The server verifies the face and local coordinates, snapshots its own content state, and inserts one touch packet into the inventory behind the block.
5. A server manager can route that packet and broadcast a new semantic state. Client Managers can read that broadcast and render the next image.

The first static press proof needs only a deterministic fixture image and server-owned content. Generic image movement, client programs, and terminal mounting follow without redefining that press contract. The later in-world terminal experience remains a product direction, not a prerequisite for the first proof.

## Non-goals for this trajectory

- Do not redirect the implementation into RADIUS or general datagram protocol work. Those ideas may inform a separate transport exploration later.
- Do not make exact client-rendered-frame identity part of touch schema version 1.
- Do not enable remote multiplayer packet effects by relaxing the current private-world gate; P8G requires a separately negotiated boundary.
- Do not make terminal mounting or screen sharing a dependency of the first Touch Display and Client Manager acceptance. Plan terminal mounting as a later integrated milestone.
- Do not add `EVERY TOUCH` before its queue capacity, ordering, unloaded-manager, simulation and overflow rules exist. First consume ordinary packet items with timed programs.

## Touch packet and display contract

Proposed first packet shape:

```json
{
  "schema": "sfm:touch@1",
  "dimension": "minecraft:overworld",
  "x": 12,
  "y": 64,
  "z": -7,
  "face": "north",
  "u": 0.731,
  "v": 0.284,
  "action": "press",
  "contentRevision": 42,
  "state": {"color": "red"}
}
```

`semanticContent` means the server-owned image reference, state and revision. `renderedFrame` means the client-local image actually presented by the renderer. `state` in the packet is a copy of the server-owned semantic state, not an assertion supplied by the clicking client. The server updates its semantic image reference, state and revision as one content transaction. `sfm:touch@1` is an SFM schema string, not a Minecraft `ResourceLocation`; the schema validator must explicitly allow its version marker rather than passing it to the resource-location parser.

`contentRevision` correlates a press with lower-cadence server semantic state. It does not claim to identify the exact frame a Client Manager happened to render at monitor cadence. The first circuit accepts that distinction. If exact local-frame correlation becomes necessary, version the click contract and investigate a constrained custom client report without trusting it as server state.

Repeated presses on unchanged content should produce equal packet data and may stack as ordinary packet items; do not add a unique click sequence solely for deduplication. If a future consumer needs occurrence identity, it can add an application-level ID or a versioned schema extension without silently changing this first contract. A press that cannot insert is handled through an explicit failure policy in P3, not hidden client-side creation.

`action` starts with `press` and reserves room for later release, move or drag semantics. Those richer gestures may require a custom client message and a new schema version; they are not silently inferred by version 1.

Store front orientation and derive the rear inventory position and access side on the server. Convert the server-received hit to UV using the block's orientation, not a hard-coded world-axis mapping. Define pixel-edge convention and reject off-face or out-of-range hits. Whether a held item's own use takes precedence is an open behaviour gate; the first test uses empty hands.

Before freezing schema version 1, define UV origin and handedness for every block orientation, inclusive/exclusive edge behaviour and tolerance for the packet's float hit coordinates. Also define the initial content revision, persistence across save/reload and replacement, monotonic increment/wrap behaviour, and reset semantics when a block is broken or copied. These are O9 and O10 rather than implementation accidents.

## Verified repository evidence and boundaries

This section records the initial reconnaissance. Completed task evidence below
supersedes its descriptions of missing implementations; the security and
execution-side boundaries remain requirements.

### Click route

Minecraft 1.19.2 `ServerboundUseItemOnPacket` includes a `BlockHitResult` with block position, face and three block-relative floating hit coordinates, plus hand and the vanilla prediction sequence. `MultiPlayerGameMode.useItemOn` sends this packet even if client `Block.use` returns a consuming result; that result instead stops the main/offhand loop. The server checks its ordinary reach and hit plausibility and passes the hit to `Block.use`. It does not independently reconstruct the clicked pixel, so UV remains client-reported within those limits. Validate the active face, UV bounds and display identity server-side.

In 1.19.2, `ServerGamePacketListenerImpl.handleUseItemOn` performs the eye-to-block-centre six-block and player-position squared-distance-below-64 checks before `ServerPlayerGameMode.useItemOn` reaches block use, alongside world/build checks. Forge lets held items intercept use before the block. The first interaction GameTest should use empty hands and assert one server packet item per main-hand press. A consuming main-hand block result should prevent offhand fallback even if insertion fails.

The existing `SFMClientPacketTransport.sendInsertion` and `SFMServerPacketTransport` accept an arbitrary client-supplied exact inventory target and value, but the effect boundary in `platform/minecraft/src/main/java/ca/teamdman/sfm/common/net/SFMPacketEffectGate.java` limits that route to a private integrated world. It has no touched-block or player-reach authorisation. Do not widen or call it for Touch Display clicks. It is reasonable to reuse the server-side insertion helper after the normal block-use checks and display-specific validation. A custom client packet may be needed later for drag, move, release or client-local revision information, but is not needed for the first press.

### Value, resource and raster foundations

`platform/minecraft/src/main/java/ca/teamdman/sfm/common/value/SFMValue.java` originally had only a long-number variant. P1 added a finite binary64 variant; `SFMValueJsonCodec` now writes version 2 and reads strict integral version 1 as well as version 2. Inspect the current source before any later edit rather than relying on this historical boundary note.

`ResourceType` already models quantified slotted resources and simulated/actual transfer. `SFMResourceTypes` registers items, fluids, energy and redstone. The existing wildcard resource grammar can already parse an `IMAGE::`-shaped resource expression; the missing work is registration, capability semantics and storage, not merely a grammar token. `BufferBlockEntityContents` can create handlers for registered types and enforces one nonempty type at a time. Registering `sfm:image` alone will not create durable storage: `BufferBlockEntity` has no save/load implementation for its contents. Decide image payload ownership, slot quantity and persistence before promising image buffers.

`SFMTerminalRasterFrame` and `SFMVoxTerminalFrameInbox` demonstrate bounded immutable frames and newest-pending-frame behaviour. They are terminal/session-specific today, so reuse their rules only after separating transport-neutral pieces. Their generous single-terminal limits are not an automatic safe budget for many in-world displays.

### GameTest and actions

`@SFMGameTest` already defaults to both `SFMDist.CLIENT` and `SFMDist.DEDICATED_SERVER`; client-only annotated tests are excluded from dedicated-server discovery before class loading. Generated tests use a separate discovery path, so use annotated definitions for client-only scenarios unless that path gains equivalent metadata. `SFMDist` describes the physical Minecraft distribution, not the new logical program execution side.

Brigadier currently validates command syntax and some primitive argument ranges, not an action's full input/output contract. `SFMClientAction` exposes command configuration and `int execute` without a machine input schema, result schema, permission or cost class. `SFMClientActionStructuredResult` validates a schema ID and bounded object JSON, not typed fields. `SFMValuePattern` has useful structural matching but is not yet a full numeric/array/union/optional object schema. The resource-type registry describes movable capabilities, not action value types. Do not infer a programmatic action API from a command line string alone. The programmatic input/result contract must live on the common side and describe SFM values; O4 leaves its representation open between a new schema algebra, an extended pattern system and explicit codecs. Brigadier remains one human command adapter over that contract.

The 1.19.2 source uses Parchment layered on Mojang names and imports `net.minecraft.resources.ResourceLocation`. The 1.21.1 source still uses that class name. The 26.1.2 NeoForge surface instead imports Mojang's newer `net.minecraft.resources.Identifier`; this is a real API rename, not an SFM switch to Yarn mappings. Both implementations accept `-` in the path and namespace checks, so `sfm:touch-display/render` is legal across those surfaces. SFM may still prefer underscores as a naming convention.

## Implementation tasks and gates

Each task leaves focused automated evidence. Public-contract and security boundaries require their own tested checkpoints within the active full-feature goal, not a new goal or an automatic pause. Only an authority or external-state blocker outside this goal calls for user direction. Test names below are the required stable filters; create them as part of the task if they do not yet exist. Add `Evidence: <command, result, commit>` only after the task passes.

### [x] R0. Review and accept this contract

Completion notes: the user supplied historical messages and asked for corrections, a goal and continuation on 17 September 2026. The review added G32–G36, recorded superseded proposals, staged a fixture display before generic image transfer, added atomic manager-facing content commits, label binding, terminal follow-up and a full client-action-to-display test. The second adversarial pass removed unnecessary image/read prerequisites for client-local frame animation and narrowed the security claim for client-reported hit coordinates. Open gates O1–O13 remain explicit phase gates, not hidden approval assumptions.

Dependencies: none.

Work: review the guidance ledger, first circuit, verified evidence, reversible defaults and O1–O13. Incorporate any pasted transcript corrections without silently replacing a confirmed decision.

Validate: `rg -n "[[:blank:]]+$" docs/architecture/sfm-touch-display-and-client-manager-plan.md` returns no matches. After the file is tracked, also run `git diff --check -- docs/architecture/sfm-touch-display-and-client-manager-plan.md`.

Done when: the user accepts the corrected plan as the implementation contract or names changes; P0 becomes the sole active implementation task.

Evidence: historical-message extraction, ledger traceability and adversarial omission passes recorded above; untracked-file whitespace and path-sensitivity scans passed on 17 September 2026. User's review-and-proceed request supplies the bounded implementation go-ahead without deciding later gates prematurely.

### [x] P0. Freeze the packet baseline and test seam — G21, G22, G26, G30

Completion notes: completed 17 September 2026 on `feat/1.19.2/packet-computation`. Reconciled the pre-existing packet-polish work without dropping it and committed the tested source as `43cfe001f` (starting from `4a99b69465e36b9f619f3380fe3f978166398afe`). Keypad Enter press/release parity, the spiral-notebook item texture, compact/expanded packet tooltip, generic read-only Alt+D hovered-item inspection and `sfm.controlCliExecutable` override are recorded in the companion packet plan. The installed `sfm-propagate-changes.exe` was usable and unchanged; no competing game/build process was present at preflight. Dependencies and lockfiles stayed frozen. At that checkpoint, physical Shift-hover, art and exact Alt+D hover acceptance remained open. The 20 September file puppet proved art and Alt+D; G40 replaces the physical Shift gate with registered semantic actions and actual rendered hover.

Dependencies: R0 approval.

Work: reconcile the dirty packet worktree and companion contract without discarding changes. Disposition keypad Enter, notebook art, progressive tooltip, Alt+D inspection and `sfm.controlCliExecutable`. Confirm annotated `SFMDist.CLIENT` discovery with one ordinary client window and dedicated-server exclusion. Do not merge or propagate while the worktree is dirty.

Validate: `sfm-propagate-changes.exe game-test run-client --branch feat/1.19.2/packet-computation --filter sfm:packet_item_tooltip`, `sfm-propagate-changes.exe game-test run-server --branch feat/1.19.2/packet-computation --filter packet_item`, and `sfm-propagate-changes.exe test run --branch feat/1.19.2/packet-computation --filter SFMGameTestDiscoveryTests` after adding that named regression test.

Done when: the client-only test runs without a full-screen UI or exclusive input, the server run excludes it safely, and the packet baseline has commit and test evidence.

Evidence: `SFMGameTestDiscoveryTests` passed and guards the side filter before client-only class loading. `game-test run-client --filter sfm:packet_item_tooltip` passed 1/1 without opening an SFM screen; its first exact run exposed an invalid simulated-key assertion, which was corrected through a deterministic renderer seam without changing physical key polling. `game-test run-server --filter packet_item` passed 1/1; its discovery log lists `packet_item` but not `packet_item_tooltip`. `game-test run-client --filter packet_item` also passed 1/1 for the older carrier test only—selection is exact, not prefix matching. `run data` and `run compile` passed. The full Java suite passed 2,136 with zero failures and five expected assumption aborts (2,141 found). Commit: `43cfe001f`. Generated cache whitespace remains generator-owned; the rest of the staged diff passed `git diff --check`.

### [x] P1. Finite floating values and touch schema — G03, G05

Completion notes: completed 18 September 2026 in `1a8cd9b84`. `SFMValue.DoubleValue` stores only finite binary64 values and normalises negative zero. Integer-shaped JSON remains an exact `LongValue`; decimal/exponent JSON is a distinct `DoubleValue`, with integral doubles retaining their decimal marker when written. Codec v2 is written for new items/envelopes; v1 integer data remains readable without mutating old item NBT; unknown future versions cannot dispatch. `SFMTouchValue.press` constructs the flat `sfm:touch@1` value with named position fields, bounded floating UVs and a complete-packet byte check. Numeric JSON parsing uses binary64 rounding, so an extremely tiny nonzero decimal may round to zero; this follows the chosen finite-binary64 model and can be revisited if a use case needs decimal underflow rejection. The outer Forge channel was unchanged. The companion contract and player-visible changelog now reflect v2. The installed CLI remained unchanged (revision `ea4dcc9aa`); preflight found no competing SFM/Minecraft/toolchain process, and dependency declarations/lockfiles were not changed.

Dependencies: P0.

Reversible default: add finite binary64 `DoubleValue` beside `LongValue`; reject NaN and infinities, normalise negative zero, parse decimal/exponent JSON as double and integer-shaped JSON as long. Canonical double output uses a deterministic shortest round-trip spelling while retaining a decimal marker for integral doubles such as `1.0`, so decoding does not change the value kind. Write codec version 2 and retain the version-1 decoder. Audit item, network and value boundaries before changing versions; do not bump an unchanged outer Forge envelope merely for an inner codec version. Keep counts and resource quantities whole-number typed.

Validate: `sfm-propagate-changes.exe test run --branch feat/1.19.2/packet-computation --filter SFMValueDoubleTests`.

Done when: tests cover old-item reads, canonical round trips, equality and hashing, malformed/non-finite values, negative zero, float-to-double wire precision and `[0, 1]` UV validation.

Evidence: `test run --branch feat/1.19.2/packet-computation --filter SFMValueDoubleTests` passed 6/6; `--filter SFMTouchValueTests` passed 4/4. The final unfiltered Java suite passed 2,148, failed 0, with five expected assumption aborts (2,153 found). `game-test run-server --branch feat/1.19.2/packet-computation --filter packet_item` passed 1/1, including exact v1 item reads and v2 double writes. `SFMPacketContractTests` in the full suite prove v1 envelope dispatch, v2 double payloads, unknown-future fail-closed behaviour and bit-exact widened-float wire roundtrip. Commit: `1a8cd9b84`; staged diff whitespace check passed.

### [x] P2A. Static interactive surface with fixture — G01, G04, G32, G34

Completion notes: static six-face block, atomic persisted and client-synchronized content tuple, bundled red/blue fixtures, renderer, registration and generated assets implemented in `2f057a24c`; full-bright image-quad fix in `90758015b`. Identical content is a no-op; malformed stored image IDs fail closed without reusing a revision. The ambient client probe retries from server GameTest ticks, never recursively on the client thread, and does not police unrelated screens. The opt-in file-driven puppet in `54f37c508` supplied direct visible-pixel proof while preserving a normal free-moving client window.

Dependencies: P1.

Work: add a distinct Touch Display block and one outward raster face. Render a deterministic bundled test image without requiring `sfm:image` registration or a terminal. Add server-owned orientation and an atomic `semanticContent` record containing fixture image reference, arbitrary interaction state and revision. Provide an internal content commit that copies image reference and state together, then advances the revision once. Keep the first fixture path private to tests or operator setup; P2B adds the manager-facing resource path.

Validate: `sfm-propagate-changes.exe game-test run-client --branch feat/1.19.2/packet-computation --filter sfm:touch_display_render` checks the integrated client projection, fixture selection and renderer registration without opening or policing the player's current screen; a visual capture or direct in-world inspection separately confirms the actual pixels and no display-owned GUI. `sfm-propagate-changes.exe game-test run-server --branch feat/1.19.2/packet-computation --filter sfm:touch_display_content_commit` checks the authoritative atomic tuple and persistence. A GameTest body runs server-side even when its discovery requires a client, so its ordinary assertions alone cannot establish rendered pixel output.

Done when: one client sees the fixture on the active face without opening a GUI; the server persists orientation, image reference, state and revision as specified; a red/blue commit never exposes one revision's image with another revision's interaction state; unchanged static content reuses the same resource-managed texture without a dynamic upload.

Evidence: `run compile` and `run data` passed. The focused server GameTest passed 1/1 after the malformed-image regression; the focused integrated-client GameTest passed 1/1 after correcting its asynchronous retry and ambient-screen assumptions. The unfiltered Java suite passed 2,148, failed 0, with five expected assumption aborts (2,153 found). Static fixtures reuse Minecraft's red and blue concrete textures through a resource-managed renderer; there is no dynamic texture upload path in P2A. The JSON-controlled `sfm:in_world_touch_display_exploratory` puppet exited 0 with no GUI or OS pointer injection. Its durable aim capture visibly shows the red face, with interior RGB `(104,25,25)` after full-bright rendering versus `(10,2,2)` before, while the client observed the red image reference at revision 1.

### [x] P2B. Generic image resource and display sink — G23, G34

Dependencies: P2A. This is not a prerequisite for the first press proof in P3.

Work: choose immutable image descriptor/payload ownership, content identity and deduplication, quantified slot semantics, byte/dimension/format limits, save/load behaviour and missing-payload recovery. Content-addressed snapshots are a reversible default. Register `sfm:image` with a buffer handler and consuming Touch Display sink. Give the manager an atomic way to supply `IMAGE::` plus arbitrary interaction state to the same content commit from P2A. Keep high-frequency frames outside item movement. Cache GPU resources by identity and release them on lifecycle changes.

Validate: `sfm-propagate-changes.exe test run --branch feat/1.19.2/packet-computation --filter SFMImageResourceTests`, `sfm-propagate-changes.exe game-test run-server --branch feat/1.19.2/packet-computation --filter sfm:touch_display_image_transfer`, and `sfm-propagate-changes.exe game-test run-client --branch feat/1.19.2/packet-computation --filter sfm:touch_display_dynamic_image`.

Done when: the chosen payload and descriptor survive the stated save/reload cycle; oversize or malformed images fail whole; simulated transfer is side-effect-free; a server-safe real-resource GameTest moves an image through a buffer into the display; a paired image/state update never exposes a mismatched revision.

Evidence: `90758015b` adds immutable exact-byte-SHA-256 PNG snapshots capped at 64 KiB encoded, 512×512 and 262,144 decoded pixels; strict chunk/order/CRC and decode checks reject malformed content, while versioned NBT reloads fail closed. A one-unit `sfm:image` resource stack carries both image and arbitrary bounded interaction state. Image buffers persist their snapshot/state without changing other resource types; the consuming display sink commits image/state/revision together. The client texture cache uploads a digest once while resident, with bounded admission and unload/reload cleanup. Focused snapshot tests passed 6/6, resource tests 1/1, texture-cache tests 3/3, and the client-only dynamic image GameTest passed 1/1 for synced PNG/state and dynamic texture registration/reuse. `run compile` and `run data` passed. The dedicated-server real-manager `sfm:touch_display_image_transfer` GameTest passed 1/1, proving real `IMAGE::` movement, buffer save/load and atomic sink commit. The file-driven puppet in `54f37c508` separately proves visible output on the face after the renderer lighting fix.

### [x] P3. Server-authoritative press to rear inventory — G02, G03, G04, G27

Dependencies: P1, P2A, O1, O2, O9 and O10 resolved. Generic `sfm:image` movement in P2B is not needed for the first touch proof.

Work: use normal block interaction. The client consumes the successful main-hand interaction without material mutation. The server validates face and UV, takes its own semantic snapshot, derives the rear handler from block orientation and inserts one packet through the server-side helper. Do not call or widen arbitrary client-requested insertion.

Validate: `sfm-propagate-changes.exe game-test run-client --branch feat/1.19.2/packet-computation --filter sfm:touch_display_press` and `sfm-propagate-changes.exe game-test run-server --branch feat/1.19.2/packet-computation --filter sfm:touch_display_press_server`.

Done when: known UV points and all rotations produce exactly one packet with server-owned state/revision; wrong-face and out-of-bounds hits fail; no client hit can nominate an arbitrary inventory because the server derives the rear target from the reached display; full/missing rear inventory follows O1 without duplicate/spill; offhand fallback is absent. Vanilla does not independently reconstruct the ray, so a plausible claimed hit on another reachable display remains a separate permissions concern.

Evidence: the server uses the vanilla `BlockHitResult`, validates the active face, inset, reach and unobstructed line of sight, and derives its rear target from server block orientation. The six-face UV unit tests passed 5/5. The dedicated-server GameTest passed 1/1 for all six faces, packet state/revision snapshots, stacking, malformed hits, offhand/spectator rejection, occlusion, oversized-state preflight and full/missing rear inventories. The integrated-client GameTest passed 1/1 without opening a screen or moving the player. The unfiltered Java suite passed 2,153/2,158, failed 0, with five expected assumption aborts. `run compile` and `run data` passed. The opt-in file-driven in-world puppet `54f37c508` completed exit 0: a normal `Minecraft.gameMode.useItemOn` click on the aimed north face produced exactly one rear `sfm:packet` with `sfm:touch@1`, server-owned red state/revision and floating UVs matching `(0.25, 0.75)`, without a screen or OS pointer control.

### [x] P4A. Bounded server-to-client value delivery — G10, G13

Dependencies: P0. This task builds a new delivery path; the existing packet feature contributes value semantics, not this transport.

Work: define a private-integrated-world first transport with recipient and inbox identity, subscribe/unsubscribe lifecycle, cursor/order/overflow, logout/world-change/unload handling, size/entry limits and privacy policy. Resolve O13 before freezing addresses: a stable channel/recipient address may need to differ from the exact program identity used for consent. Preserve best-effort, no-ACK and no-retry semantics. Do not expose it to Client Manager code until P6A and P7A apply consent.

Validate: `sfm-propagate-changes.exe test run --branch feat/1.19.2/packet-computation --filter SFMClientInboxTests` for limits, cursor and lifecycle rules, and `sfm-propagate-changes.exe game-test run-client --branch feat/1.19.2/packet-computation --filter sfm:client_inbox_delivery` for actual integrated-client dispatch. Create this named GameTest in P4A; a unit test alone does not prove runtime packet registration.

Done when: one integrated client receives bounded addressed values; source edits have defined subscription behaviour; multi-viewer fan-out has a documented later path; stale session/cursor and overflow are explicit; unload/logout clears or suspends according to contract; unopened inventory contents never enter the path implicitly.

Evidence: addressed values use a stable recipient UUID, dimension and channel ID, separate from program source/consent identity. Private integrated-world effect gating, explicit session-scoped subscriptions, closeable client handles, client page cursors/eviction reporting and bounded server send budgets are implemented in `f2163736e`. The Forge packet IDs were appended and strict channel version advanced to 1.2.0. Focused client unit tests passed 4/4, the three-case `SFMClientInboxServerTests` rerun exited 0, and integrated-client `sfm:client_inbox_delivery` passed 1/1 without opening a screen.

### [x] P4B. Semantic broadcast and dashboard accounting — G04, G10, G11, G13

Dependencies: P3, P4A.

Work: freeze the server-manager grammar or action name that publishes a bounded addressed value through P4A, then implement it. A current-state snapshot reports only current state. An authoritative mover emits occurrence values when exact throughput matters. Red/blue server state updates must advance one coherent semantic revision without requiring client frame reports.

Validate: `sfm-propagate-changes.exe game-test run-client --branch feat/1.19.2/packet-computation --filter sfm:touch_display_broadcast`.

Done when: one integrated client receives coherent red/blue values; repeated or dropped snapshots cannot be counted as transfers; an occurrence broadcast from the mover yields the correct throughput total; no client sends duplicate frame-state reports.

Evidence: optional `BROADCAST TO playerAlias CHANNEL sfm:channel` syntax routes selected packet values through P4A's addressed private-world transport; legacy `BROADCAST TO playerAlias` retains the observation path. Both Minecraft and VS Code grammars/highlighting were updated, and parser regression coverage was added in `09217d141`. `sfm:touch_display_broadcast` passed 1/1 in the integrated client: two red/blue server-owned state revisions arrived as two values, while three actual iron-transfer occurrences arrived as three distinct values through a separate channel. Neither program used a GUI or moved the player. The addressed broadcast stops on a rejected send rather than looping through an exhausted per-tick budget.

### [x] P5. Logical execution side and compatible grammar — G06, G07, G22, G24, G29

Dependencies: P0.

Work: add a logical execution-side enum independent of `SFMDist`, with no `BOTH` value initially. Parse optional `CLIENT BTW` or `SERVER BTW` as the first non-comment statement and reject host mismatch; absent header uses its host. Retain those words as identifiers elsewhere. Preserve source maps, both editors and VS Code grammar. Consider renaming the grammar's whole-number rule to `integer` without broadening quantity, tick, slot or range syntax to floats. Adapt global/offset syntax from commit `8a2e70247be045657183deb6a21f9779228b37f1`; do not merge the divergent branch wholesale. Parse legacy forms during migration, print the new form canonically and warn on ambiguity.

Validate: `sfm-propagate-changes.exe test run --branch feat/1.19.2/packet-computation --filter ProgramExecutionSideTests` and `sfm-propagate-changes.exe test run --branch feat/1.19.2/packet-computation --filter IntervalGrammarCompatibilityTests`.

Done when: old programs parse; explicit mismatch fails before effects; editors agree; client/server logical-side tests pass; target adapters use `ResourceLocation` or `Identifier` without changing public identifier text.

Evidence: optional first `CLIENT BTW` / `SERVER BTW` assertions reject host mismatch before server Manager effects, including the inspection compile path; absent headers remain compatible. Both parsers accept legacy integer offsets and worded `GLOBAL` / `OFFSET BY`; canonical printing uses the latter and the linter warns on legacy offset spelling. Code and generated parser are in `09217d141`. `run compile` passed; `ProgramExecutionSideTests` and `IntervalGrammarCompatibilityTests` passed 3/3 each; existing timer tests and `SFMLTests` passed (52/52). The VS Code parser was regenerated with the matching local antlr4ts version and TypeScript type-check passed without a dependency or lockfile change. `run data` emitted the new linter localization key.

### [x] P6A. Program identity and minimum consent gate — G16–G18

Dependencies: P5. This precedes all Client Manager ticking.

Work: derive exact program identity from source, host side, world, dimension, manager position and runtime revision. Implement absent, pending, approved and denied states for the base `sfm:client_program/execute` and `sfm:touch_display/render` capabilities. Requests are idempotent; denial suppresses automatic re-prompt. The gate itself opens no modal; P6B supplies the inert placeholder and coalesced proximity notice. P8D later adds the full management/history model.

Validate: `sfm-propagate-changes.exe test run --branch feat/1.19.2/packet-computation --filter ClientProgramConsentGateTests`.

Done when: the execution gate refuses an unapproved tick in unit tests; approval applies only to its exact scope; source/capability/location changes return to absent; pending and denied cannot spam prompts; approved-but-policy-blocked is distinguishable from denied. P6B proves the gate on a real Client Manager.

Evidence: `abb6cb6a8` adds an exact-scope identity bound to stored UTF-8 source hash, logical host, normalized endpoint/world UUID, dimension, manager position, runtime revision and capability manifest. The default-deny, UI-free gate models absent/pending/approved/denied, idempotent requests, explicit reopening of denial and separate policy blockers. `ClientProgramConsentGateTests` passed all six cases. P6B must connect this gate to actual client ticks and placeholder/notice presentation; P8D must persist consent history.

### [x] P6B. Client Manager block and render scheduler — G06, G08, G09, G16, G18

Dependencies: P2A, P5, P6A. Generic image transfer in P2B is not needed for client-local frame computation.

Work: add a visibly distinct block and a client visual runtime rather than reusing the server `ProgramContext`. Implement `EVERY FRAME FOR displays AS display DO` once per eligible Touch Display found through existing label-gun bindings. Resolve O12 for missing, duplicate and competing labelled targets. Reversible defaults: one writer per display and loaded, render-selected, front-facing and viewport-size eligibility; no chunk tickets and no exact-occlusion promise. An unapproved program shows an inert placeholder and coalesced proximity notice, never a passive modal. Program logic may vary each frame without any world/inbox read. Compare output identity before repaint/upload.

Validate: `sfm-propagate-changes.exe game-test run-client --branch feat/1.19.2/packet-computation --filter sfm:client_manager_frame`.

Done when: a free-moving client window starts/stops execution with eligibility and consent; label-gun binding finds the intended display and reports missing or competing targets; pure logic changes frames; equal output performs no repaint/upload; cached reads do not constrain execution; unloading manager/display stops work.

Evidence: the final `sfm:client_manager_frame` integrated test passed 1/1 on 19 September (720 ms; CLI exit 0). It observes server-synchronized source and label changes, consent/revocation, pure frame logic, unchanged-output suppression, competing writers and manager removal without changing the camera or screen. It calls an explicit scheduler eligibility seam and therefore does not claim pixel/visibility proof. Final `ClientFrameProgramTests` passed 8/8, including the pre-parser nesting/token guard and shared 128-display per-frame work budget. Source/label projection tests passed 3/3. Datagen and the regenerated VS Code parser type-check passed without dependency changes. Source, label, manager count and per-trigger AST budgets apply before execution. Labels and positions enter exact consent identity, so retargeting cannot borrow an earlier approval.

Actual render proof: `sfm-propagate-changes.exe puppet run sfm:in_world_touch_display_exploratory --branch feat/1.19.2/packet-computation --variant 1280x720@auto --wait-for-build-lock` exited 0 on 19 September. Numbered request files installed the fixture manager, approved its exact identity, pressed the display, revoked execution and finished. Capture 4 visibly shows blue client output while server semantic content remains red at revision 1. Capture 5 recorded 9,334 evaluations but only one changed frame. The real vanilla-use press at UV `(0.25, 0.75)` passed the rear-inventory packet assertion. Capture 7 visibly restores red after revocation and reports no active client frame. Every observation has `screen: none` and `os_pointer_injection: false`. Evidence is retained under `platform/minecraft/build/sfm-toolchain/artifacts/game-test-preview/runs/sfm-in_world_tou-20260919-140058-082/`; the puppet closed its client normally. Test-only fixture approval does not prove the user consent panel, which remains P8D/P8F work.

### [x] P7A. Consent-aware read surfaces — G09–G13

Completion notes: the typed action/runtime binding and eight read-service tests passed in the broad `Client` suite. `game-test run-client --branch feat/1.19.2/packet-computation --filter sfm:client_program_read,sfm:touch_display_client_manager_circuit,sfm:client_manager_signing --wait-for-build-lock` passed all three required tests. The read fixture proves real replicated dust `POWER` changes, cache reuse and one-entry invalidation, no implicit chest contents, an explicitly broadcast inventory summary, and hidden-display subscription cleanup after revocation. Source changes retire the old read identity. No screen or real-player camera control is used.

Dependencies: P4A, P6A, P6B.

Work: expose the per-program inbox and bounded reads of already-replicated loaded block states, including direct redstone dust `POWER`. Cache reads until the specific source revision changes, then invalidate them without requiring the program to stop running. Do not imply exact occlusion, derive opaque-block redstone power, or expose unopened inventories. Enforce distance, loaded-chunk and capability scope. Programs, not block modes, choose data topology.

Validate: `sfm-propagate-changes.exe game-test run-client --branch feat/1.19.2/packet-computation --filter sfm:client_program_read`.

Done when: approved programs read permitted loaded state and inbox values; repeated unchanged reads hit the cache while a block-state or inbox revision invalidates only the affected entry; out-of-scope/unloaded reads return typed unavailable results; inventory summaries arrive only through explicit broadcast; denied reads preserve safe last-frame behaviour.

Foundation evidence: `cc3159201` adds a consent-aware, bounded client block-state read surface and P4A inbox wrapper. Exact bound or radius-limited loaded-block reads project immutable block-state properties, including integer redstone dust `POWER`, with 128 unique reads per frame and a 256-position cache keyed by observed state revision; unload/world change fails closed. Inbox pages require an exact allowed channel, reuse P4A cursors/subscriptions and release them on revocation/close. Typed outcomes distinguish awaiting consent, user denial and policy block. `ClientProgramBlockReadSurfaceTests` passed 5/5 and `ClientProgramInboxReadSurfaceTests` passed 2/2. The later production and integrated-client evidence above closes the runtime gap left by these pure tests.

### [x] P7B. Live raster path and budgets — G09, G23

Dependencies: P2A, P6B. World and inbox reads in P7A are optional inputs, not a condition for client-local frame changes.

Work: carry image references or frame descriptions to the renderer through a bounded latest-wins path. Reuse transport-neutral terminal ideas only after extracting them. Use content hashes, optional dirty regions and per-display/player/world memory, network and GPU-upload budgets. Keep these budgets distinct from static image storage.

Validate: `sfm-propagate-changes.exe game-test run-client --branch feat/1.19.2/packet-computation --filter sfm:touch_display_raster_inbox`.

Done when: stale frames cannot replace newer ones; a client animation can change independently of server semantic revision; unchanged frames do not upload; a many-display load test stays within selected budgets and releases resources.

Foundation evidence: the independent bounded latest-wins frame inbox passed final `TouchDisplayRasterInboxTests` 12/12 on 19 September, exit 0. Opaque per-display writer leases reject late writes after replacement/unload; dirty regions name the latest accepted base sequence; image dimensions and retained memory are bounded. Separate ingress, reconstruction and upload-delivery budgets account for the full-image work caused by small patches. Defaults are 512 by 512 pixels per image, 8 MiB retained pixels, 64 MiB/s local ingress/upload delivery, 128 MiB/s reconstruction and 4,096 processed frames per window. Lease churn does not reset budgets. These local decoded-byte limits do not define future network limits.

Final renderer evidence: `test run --branch feat/1.19.2/packet-computation --filter TouchDisplayRaster --wait-for-build-lock` passed 20/20 on 19 September, exit 0. The eight GPU-cache tests add a separate 8 MiB and 128-texture allocation cap, including retired textures awaiting deletion. A rejected resize retains the previous image; failed uploads have bounded retry metadata. Production rendering uses dynamic textures, fresh presentation authority, newest-frame selection and at most one upload per selected epoch. Visibility-only suspension retains program state and writer leases but performs no evaluation or upload. Revocation, stale source or label scope, removed managers, chunk/world unload and resource reload invalidate the appropriate state.

`game-test run-client --branch feat/1.19.2/packet-computation --filter sfm:client_program_invocation,sfm:client_manager_frame,sfm:touch_display_raster_inbox --wait-for-build-lock` passed 3/3, exit 0. The raster test verifies actual native RGBA pixel order, dirty updates, unchanged-content reuse, newest-frame presentation, hidden/resumed operation, revoked hidden streams, GPU admission and client-tick cleanup. It leaves the current screen and camera untouched. Cleanup probes produce missing-resource warnings after released textures; these do not indicate a failed upload. The client exited normally. These local decoded-byte limits do not grant future network bandwidth.

### [x] P8A. Common action value-schema algebra — G14, G28

Dependencies: P1.

Work: decide O4 between a new common `SFMValue` schema algebra, extending `SFMValuePattern`, or explicit codecs. Add optional action descriptors for typed input/result, logical side, base control permission, dynamic data scope, cost class and error/ack semantics. Brigadier remains a human command adapter. Undescribed actions are unavailable to programs by default. Make the existing private-integrated-world `sfm:packet/send` a first typed high-risk descriptor without weakening its effect gate.

Validate: `sfm-propagate-changes.exe test run --branch feat/1.19.2/packet-computation --filter SFMClientActionDescriptorTests`.

Done when: descriptors express numeric, array, union, optional and closed/open object requirements; typed failures are stable; existing palette/CLI actions remain compatible; filesystem, clipboard and process actions stay program-inaccessible without explicit descriptors.

Evidence: `764062120` adds a bounded `SFMValueSchema` separate from packet-matching patterns, plus opt-in action descriptors and a typed `sfm:packet/send` descriptor. Inputs validate integer and finite floating ranges, arrays, unions, nullable or omitted fields, and open/closed objects with stable failure paths. A separate 16 KiB action envelope does not narrow the established 3,072-byte packet payload limit. The descriptor declares client execution, exact target scope, server-effect cost and local-transport-attempt-only acknowledgement; registration alone grants no program access. Undescribed clipboard, filesystem and process actions remain unavailable to programs. `SFMClientActionDescriptorTests` passed 6/6 and existing `SFMPacketActionsTests` passed 7/7. P8B adds real principal/rate authorization; P8C adds invocation from Client Manager programs.

### [x] P8B. Principal-aware shared effect service — G14, G15, G18

Dependencies: P6A, P8A.

Work: place target authorisation and rate enforcement below the CLI, palette and program adapters. Preserve distinct caller identity and consent: a human palette/CLI action does not inherit Client Manager consent, while a program cannot borrow human authority. Static manifests extract literal action IDs/scopes. For computed action IDs, resolve O11 by forbidding them or requiring an explicit broad grant; dynamic arguments always receive invocation-time checks. Record program/revision/consent provenance in traces.

Validate: `sfm-propagate-changes.exe test run --branch feat/1.19.2/packet-computation --filter SFMClientActionAuthorizationTests`.

Done when: every adapter reaches the same target/rate enforcement, principal policy remains distinct, parameter scope is checked after evaluation, and bypass tests fail.

Evidence: `SFMClientActionAuthorizationService` distinguishes human and program principals. It checks the registry descriptor, typed input, declared and approved capabilities, evaluated target scope, current loaded manager identity and shared quotas before dispatch. A stale saved approval cannot survive source, binding, location or world changes. CLI/palette packet send uses the human adapter; program callers cannot fall back to it. The local server-effect allowance is 32 operations and 64 KiB per second across both principals. The server independently enforces its private-world owner/spectator/thread checks and per-sender 32-operation/64-KiB tick budget. Capacity pressure cannot evict an active quota to reset it. Traces retain bounded identity/scope hashes and consent provenance, not source or packet bodies. The `SFMClientAction` filtered suite exited 0; `SFMBoundedEffectBudgetTests` passed 3/3 and final `SFMPacketActionsTests` passed 8/8 on 19 September. Static invocation manifests and production program dispatch remain P8C work; local reads will need their own cost-class budget rather than inheriting server-effect cadence.

### [x] P8C. Client Manager action invocation — G14, G28

Dependencies: P5, P6B, P8A, P8B.

Work: freeze an SFML grammar and AST node for invoking a literal action ID with typed SFM arguments and binding a typed result. Preserve source mapping and useful diagnostics. Add the literal action and scope to the program's capability manifest, then dispatch through P8B with program identity and revision. Forbid computed action IDs initially under O11. Use typed `sfm:packet/send` as the first end-to-end effect.

Validate: `sfm-propagate-changes.exe test run --branch feat/1.19.2/packet-computation --filter ClientProgramInvocationTests`.

Done when: parse/type/source-map tests pass; a result is bound without reparsing human console text; dispatch carries program/revision provenance into P8B; malformed, unauthorised and rate-limited calls return typed failures. P8D proves real user consent and private-world packet send.

Implementation decision: typed local bindings use `LET request BE JSON "..."`, `LET response BE INVOKE sfm:packet/send WITH request` and `LET status BE FIELD "status" OF response`. Action IDs are literal. Initial inputs and target scopes must resolve statically; the dispatcher rechecks evaluated input and scope. Typed results use a bounded `status`/`result` envelope, not parsed console text. The common AST never loads client registry classes. Execution, drawing and action permissions are separate; an execute-only program can inspect a typed denial without drawing. Local reads use their own bounded cost class rather than the server-effect allowance.

Validation: the final `test run --branch feat/1.19.2/packet-computation --filter ClientProgram --wait-for-build-lock` passed 47/47 on 19 September, exit 0, including ten invocation tests. They cover exact source locations and cursor mapping, grammar round-tripping, typed input/result failures, declared capabilities and exact subjects, stale callers, per-operation consent and separate local-read quotas. The first test attempt used a one-tick legacy timer below the existing configured minimum; correcting the fixture to 20 ticks also prevents that negative test from passing for the wrong reason. Editor parser generation and `tsc --noEmit` passed with existing pinned dependencies. The `sfm:client_program_invocation` ambient test passed in the 3/3 combined client run above. It compiles synchronized source, observes absent and denied send permission, approves the exact capability, sends one packet through the real client/server transport, binds the typed result and rejects a changed source revision without borrowing approval. No human command adapter, screen or camera control is involved.

### [x] P8D. Full consent store and management model — G16–G18

Final review evidence: the 97-request P8F journey passed against the combined runtime. It inspected remembered/current source, granted scoped signer authority, proved exact denial still overrides it, and removed its exact fixture grants. Earlier consent persistence, policy, expiry and retry-after tests remain the model evidence; the 33-request consent puppet remains the detailed management-controls proof.

Dependencies: P6A, P8A–P8C.

Work: expand the minimum store to exact source copy/hash, runtime plus SFM/Minecraft/loader/build context, declared/resolved capabilities, location, decision and history. Seed `sfm:client_program/execute`, `sfm:touch_display/render`, `sfm:world/block_state/read_loaded`, `sfm:client_inbox/read`, `sfm:packet/send` and `sfm:terminal/input`. Add typed idempotent request/status actions and the existing panel-action route. Effective policy is registered capability intersected with server policy and client/parental policy. Within it, user authority is either an exact-program grant or a matching trusted-signer-and-capability grant, further restricted by its world/dimension/position/time scope. New actions never enter an old wildcard grant silently.

Validate: `sfm-propagate-changes.exe test run --branch feat/1.19.2/packet-computation --filter ClientProgramConsent` and `sfm-propagate-changes.exe game-test run-client --branch feat/1.19.2/packet-computation --filter sfm:client_program_invocation`.

Done when: previous/current source and capability diffs are reviewable; denial and retry-after policies survive restart as specified; fine permission loss degrades predictably; changed resolved capability sets re-consent; policy-blocked stays distinct from denial; signer grants cannot exceed their capability and location scope; an approved Client Manager sends only to an authorised target in a private integrated world while LAN and remote remain rejected.

Foundation evidence: `test run --branch feat/1.19.2/packet-computation --filter ClientProgramConsent --wait-for-build-lock` passed 13/13 on 19 September. The local store retains exact source, label bindings, observed versions and bounded per-capability decision history. Approval expiry and denial retry-after are checked at evaluation. Absent expiry means persistent approval; absent retry-after means no automatic reopening. Explicit forgetting frees a bounded record. Source and binding hashes are recomputed on load, and saves require atomic replacement. Corrupt storage fails closed without a last-valid backup, which could resurrect revoked approval.

Production evidence: the final 47/47 `ClientProgram` suite includes 23 consent tests. The lazy local service persists explicit requests and user decisions, not incidental test approvals or passive observation. Store v2 persists stop-all and reads v1. The panel exposes source, previous source, diff, scope, history, capability, expiry, denial cooldown, revoke, forget and stop/resume controls. Typed self-only request/status actions never open a modal. Save failure stops execution locally and warns that restart may restore older disk decisions. The integrated invocation test proves a consented private-world send. The 33-step real consent puppet below proves the review controls. Loader/build context and runtime signer authority now have regression evidence in P8E. The combined previous-source and signing UI journey passed in P8F.

### [x] P8E. Author signatures and trust — G19, G20

Final runtime evidence: P8F now proves actual protected-key creation and unlock, server-acknowledged editor save, independent Alice/Bob signatures over the real packet route, stale revision refusal and live trust/denial precedence. The earlier unit and dedicated GameTest evidence covers persistence, post-sign edit/reversion, key backup/restore/rotation and closeable signing lifetime. These layers are complementary; the puppet does not claim to exercise every key-management UI operation.

Validated foundation checkpoint: protected client keys use platform Ed25519, AES-GCM and bounded PBKDF2 without new dependencies. Creation, rotation, authenticated encrypted backup and restore never overwrite existing keys. A closeable signer exposes no private-key getter. Server signing metadata now persists incarnation, monotonic revision and bounded independent history. Review/save acknowledgement binds source, all labels, player, address and expiring one-use challenge; clients must recompile acknowledged source and capabilities before signing. The server accepts declared capabilities as signed authorship data, never as permissions. New packet types require strict Forge channel version `1.3.0`; older peers reject the incompatible channel. Exact consent decisions and policy precede signer authority. Stop-all and forgetting remove relevant signer rules; a failed revocation leaves programs stopped. The panel reviews an exact location, runtime, binding and capability ceiling, with a warning that signer trust can cover future signed revisions.

Combined evidence: `sfm-propagate-changes.exe test run --branch feat/1.19.2/packet-computation --filter Client --wait-for-build-lock --log-file platform/minecraft/build/client-signing-recovery-tests.log --log-filter info` passed 2,041 tests with zero failures and five expected assumption aborts (2,046 found), exit 0. This broad filter includes 13 protected-key tests, 18 signing-controller tests, 14 server signing protocol/admission tests, seven signer-service tests, eight read-service tests and 33 terminal tests. Earlier attempts found two incompatible `FriendlyByteBuf` chains and one ambiguous JUnit method reference; both were corrected before passing validation. The controller admits matching connection/world/request UUIDs before decoding, recompiles the acknowledged exact source and manifest, waits for matching block projection, and signs only through a separate off-thread closeable operation. Pure tests do not prove the production UI or network authoring journey.

Audit evidence: the passing run includes regressions for valid persisted signer rules alongside corrupted exact consent. Corrupt or unreadable consent storage stops execution; explicit resume clears signer rules before unblocking. Clean first-use absence remains distinct from corruption. The runtime caches validated public evidence against immutable signing snapshots but rechecks live identity on every authority query. Terminal pumping also isolates cleanup exceptions, so a failed session does not prevent a healthy session from progressing.

Dependencies: P8D. The feature is advanced and off by default, but its implementation is part of the full feature contract.

Work: use platform Ed25519 as the proposed default, subject to the repository's frozen-dependency check. Define client-side key creation, protected storage, backup/rotation and fingerprint identity. Define trust scope as signer plus capability subset, not signer alone. Sign a canonical descriptor of LF-normalised exact source hash, runtime and sorted capabilities. Store independent signatures beside source; an untrusted extra signature cannot veto a trusted one. Save must acknowledge exact server source/hash/revision before a separate compare-and-swap Sign action. Body edits invalidate active signatures but retain history; exact reversion may restore a matching signature. The pad is cosmetic, first contact may unlock Sign, and strokes remain ephemeral by default with an accessible alternative.

Validate: `sfm-propagate-changes.exe test run --branch feat/1.19.2/packet-computation --filter ClientProgramSignatureTests`.

Done when: a trusted Alice signature within the granted capability subset authorises an otherwise unchanged program; Bob cannot; Bob's edit invalidates Alice's active authority without deleting history; exact reversion can restore it; Bob's extra signature cannot veto trusted Alice; stale revisions cannot be signed; private keys and strokes never reach the server or program.

Evidence: seven `ClientProgramSignatureTests` passed within the final 47/47 `ClientProgram` suite on 19 September, exit 0. The independent model uses JCA Ed25519 without new dependencies, a canonical `sfm:program_descriptor@1` JSON representation, and strict UTF-8 with LF-only source normalization. Signatures cover source hash, runtime and sorted resolved capability IDs, not other signatures. Bounded history rejects overflow without evicting earlier attestations. A local signer rule initially fixes world, dimension, manager position and label-binding digest, plus an exact allowed capability set and optional expiry. Source revisions may change under that rule only if the trusted author signs the new descriptor and its capabilities remain a subset. Tests prove independent Alice/Bob attestations, tamper rejection, edit/reversion behaviour, portable line endings and exact local scope. This was the original pure-model checkpoint. Runtime authority, protected keys, revision acknowledgement, compare-and-swap signing and durable trust have the foundation evidence above. The actual signing UI journey passed in P8F.

### [x] P8F. Consent and signing UI acceptance — G17, G20, G21

Checkpoint commit: `db09e33e1` contains the signing UI, protected-key controls, explicit ceremony and file-driven 97-request proof. Terminal and multiplayer changes remain separate work.

Final evidence: `sfm-propagate-changes.exe puppet run sfm:client_program_consent_and_signing --branch feat/1.19.2/packet-computation --variant 1280x720@auto --wait-for-build-lock --log-file platform/minecraft/build/client-signing-editor-puppet-retry.log --log-filter info`, driven by `platform/minecraft/Run-ClientProgramSigningPuppet.ps1`, passed all 97 numbered requests with zero failures. Both runner and CLI exited 0. The client saved and shut down normally, and exact fixture consent/trust records were removed. Durable artifacts: `platform/minecraft/build/sfm-toolchain/artifacts/game-test-preview/runs/sfm-client_progr-20260919-225414-180/`.

The final journey proves actual editor typing and Done/save, matching server SAVED hash and projected revision, zero signatures after save, exact rendered previous/current diff content, and a disabled Sign while both the passphrase and ceremony are ready but the delay has not elapsed. Real Tab/Space input proves the accessible alternative. It also proves network signing, cancel/secret reset, stale-target refusal, trusted Alice authority, independent Bob no-veto and exact denial precedence. Figures 19 and 49 were visually inspected: key controls and source diff are readable without underlying-panel bleed-through. No OS-level pointer injection was used.

Signing journey evidence: the file-driven `sfm:client_program_consent_and_signing` puppet passed 93 requests with zero failures, exit 0. It proved the source diff, delayed approval, cancellation, first-contact pen enablement, accessible alternative, separate network signatures, stale revision refusal, trusted signer authority, exact denial precedence and an extra untrusted signer not vetoing trusted authority. The fixture removed its exact consent/trust records and the client exited normally. Evidence: `platform/minecraft/build/sfm-toolchain/artifacts/game-test-preview/runs/sfm-client_progr-20260919-220941-986/`. Disposable encrypted keys remained only in the puppet run directory.

Earlier follow-up, now resolved by the final evidence above: the original journey omitted the editor's actual Done/save path and screenshots exposed background text bleeding through stacked key/review screens. The combined client unit snapshot passed 2,055 tests with zero failures and five expected assumption aborts (2,060 found), exit 0, before those final UI corrections.

Dependencies: P8D, P8E. This is the deliberate puppet-only UI proof; it is not an ambient GameTest.

Work: exercise the consent panel's current/previous source diff, capability and location scope, denial/retry management and approved-but-policy-blocked state. Exercise separate save acknowledgement and Sign, first-contact pad enablement, ephemeral stroke clearing and an accessible non-drawing alternative. Confirm no passive proximity modal appears.

Validate: `sfm-propagate-changes.exe puppet run sfm:client_program_consent_and_signing --branch feat/1.19.2/packet-computation --variant 1280x720@auto --wait-for-build-lock`.

Done when: the bounded puppet captures the review and signing journey with assertions, restores the viewport and exits; the ambient GameTests remain screen-free.

Consent evidence: `sfm-propagate-changes.exe puppet run sfm:client_program_consent_review --branch feat/1.19.2/packet-computation --variant 1280x720@auto --wait-for-build-lock` passed on 19 September, exit 0. All 33 numbered request files reported `dispatched` with zero failed requests. Actual panel clicks proved initial absence, source review, disabled Confirm with enabled/focused Cancel, cancellation without approval, separate execute/render approval, denial and explicit reopening. Capture 19 shows blue local output over red semantic content; a real vanilla-use press produced exactly one rear packet. Capture 25 shows red after revocation. Stop-all revoked both capabilities; explicit delayed resume restored neither. The puppet closed the workspace, forgot its exact durable fixture and exited normally, without OS pointer injection. Evidence: `platform/minecraft/build/sfm-toolchain/artifacts/game-test-preview/runs/sfm-client_progr-20260919-145431-472/`.

Two prior attempts exposed harness assumptions: command submission requires an open palette, and Escape opens the workspace close chooser. The puppet now opens the palette explicitly and invokes `sfm:screen/close`; any failed request makes review mode fail overall. The failed attempts are not acceptance evidence. Ordinary timeout/exception invokes bounded fixture cleanup; forced process termination cannot guarantee it. Review mode rejects direct approval shortcuts. The subsequent 97-request journey above completes the combined previous-version/signing proof.

### [x] P8G. Remote multiplayer bidirectional packet boundary — G14, G15, G39

Completion checkpoint: the isolated remote journey below passed all 36 file requests. The subsequent unfiltered unit run passed all 66 `SFMMultiplayer` tests, including the new connection/owner lease regressions. That aggregate run had one separate source-location assertion failure caused by canonical INVOKE quoting; its correction and final aggregate rerun belong to cross-cutting validation. Private-world gates remain unchanged.

Authority checkpoint: activation was paused after auto-review rejected the network expansion. The user explicitly approved the exact-grant boundary on 19 September. The approved registration and broadcast-routing patch was then applied. This does not relax the old private-world gate or authorise wildcard grants. New packet registration advances the strict Forge channel to `1.4.0`; the inner multiplayer protocol separately negotiates version 1. The combined wire, runtime, operator policy and client adaptation evidence is recorded below.

First bounded work item: implement the independent common-side negotiation/admission model and its regression tests. No existing private-world gate changes in this checkpoint. Default-deny operator grants bind connection-derived player, allowed action, exact inventory address/side or own inbox channel, and expiry. Program claims must match a server-observed manager incarnation/revision/source and declared scope; a claimed hash does not prove that code executed and can never increase the player's grant. Session rotation rejects stale messages and subscriptions without resetting player budgets. Stable insertion results report the insertion attempt only, never downstream completion.

Harness decision: use the opt-in `sfm:multiplayer_packet_boundary` file-driven puppet plus an isolated loopback dedicated fixture. The implemented runner prepares launch files through `sfm-propagate-changes.exe run server --branch feat/1.19.2/packet-computation --dry-run --wait-for-build-lock` and `sfm-propagate-changes.exe puppet run sfm:multiplayer_packet_boundary --branch feat/1.19.2/packet-computation --variant 1280x720@auto --dry-run --wait-for-build-lock`. It replays the generated Java argfiles with child-only Forge environment and isolated working directories. Both normal CLI launches retain the same build lock throughout their lifetime, so concurrent ordinary launch commands are unsuitable. The runner holds that lock across both JVMs, verifies launch hashes and selectors after acquiring it, owns both process handles, binds only loopback and enforces bounded cleanup. Local offline userdev authentication proves the remote transport boundary, not Mojang account authentication. The acceptance command is `pwsh -File scripts/test-multiplayer-packet-boundary.ps1`; compilation passed, but the runtime journey remains unproven.

Dependencies: P4A, P4B, P8B–P8D. Later than first feature acceptance, but explicitly included in the active full-feature goal by G37.

Work: add a separately versioned, negotiated, server-authorised protocol for both client-to-server action/packet send and server-to-client addressed inbox/broadcast delivery. Define recipient and target policy, player/program/action/channel byte and operation budgets, session/subscription identity, acknowledgement meaning, privacy and abuse telemetry. Preserve the old private-world gates until all peers negotiate the new protocol.

Validate: `sfm-propagate-changes.exe test run --branch feat/1.19.2/packet-computation --filter SFMMultiplayer` and `pwsh -File scripts/test-multiplayer-packet-boundary.ps1`.

Done when: remote clients cannot address arbitrary inventories, spoof program/recipient identity or subscribe to another inbox; limits cover both directions and reconnect behaviour; acknowledgements never claim downstream completion; old/new peers fail safely.

Foundation evidence: `sfm-propagate-changes.exe test run --branch feat/1.19.2/packet-computation --filter SFMMultiplayer --wait-for-build-lock --log-file platform/minecraft/build/multiplayer-boundary-unit-tests.log --log-filter info` passed 64/64 tests with zero failures or aborts, exit 0. The snapshot compiled 1,551 main, 466 GameTest and 465 test sources. Tests cover admission, bounded wire parsing, operator policy persistence, static program-scope extraction and client connection/session handling. Review also corrected expired negotiation entries, quota-rejected initial offers and stale subscription-epoch retention. Pure tests prove the model boundaries; the isolated remote-client runtime journey remains required.

Remote runtime evidence: `pwsh -NoProfile -File scripts/test-multiplayer-packet-boundary.ps1` passed on 19 September, exit 0. The file-driven proof made 20 client and 16 server requests. It proved default denial, exact grants, a registered human action round trip and a consented Client Manager round trip through actual server SFML, wrong target/side, forged/stale program claims, recipient spoofing, replay rejection, bounded pressure, reconnect, stale nonce, revocation and expiry. Final inventory counts were exactly 2 archived packets, an empty mailbox and an unchanged denied target. Server diagnostics recorded 2 insertions, 2 deliveries, 2 negotiations, 133 authority denials, 2 program rejections, one recipient rejection, one replay, one stale session and 65 rate-limit refusals. The runner also verified `failed=0`, one terminal puppet completion and viewport restoration. Artifacts: `platform/minecraft/build/sfm-toolchain/artifacts/multiplayer-packet-boundary/run-d0d16f43-96f2-4704-b889-4cc877953635/result.json`. Client PID 32000 and dedicated-server PID 17888 both exited normally with code 0. This proves the loopback remote transport, not Mojang authentication or owner-command UI. New lease/cleanup unit regressions still need a fresh aggregate run.

### [x] P9. Full integrated GameTest circuit — G02, G08, G10, G11, G21, G22

Client evidence: the combined P7A/P9/signing run above passed 3/3. The shared fixture places real server managers and cable. Timed SFML broadcasts and archives a touch; approved client invocation sends a response to a command mailbox; another server SFML path moves a real image resource into the display with its semantic state. The client fixture uses explicit consent and render-eligibility seams, without moving the real player or opening a screen. The common server invariant fixture loads no client classes.

Dependencies: P1–P7B and P8A–P8D. P8E signing, P8F UI proof and P8G multiplayer are not prerequisites for this screen-free circuit; P8E/F retain separate first-release acceptance.

Work: create ambient client-only annotated GameTests for placement, press, inbox delivery, consent, semantic broadcast and logic-driven repaint. Include one circuit where an approved Client Manager invokes `sfm:packet/send`, a server mailbox and manager route it, and the server commits new display semantic content. They must not open panels, terminals or full-screen menus or capture exclusive window control. Keep dedicated-server-safe value/block/resource invariants. Use puppets only for UI-specific acceptance.

Validate: `sfm-propagate-changes.exe game-test run-client --branch feat/1.19.2/packet-computation --filter sfm:touch_display_client_manager_circuit` and `sfm-propagate-changes.exe game-test run-server --branch feat/1.19.2/packet-computation --filter sfm:touch_display_server_invariant`.

Done when: the action-to-mailbox-to-display circuit passes in one normal window while the player may fly around; no test parses PowerShell prompts or needs focus; client-only classes never load on dedicated server; the handoff records command output and final process state.

Dedicated evidence: `sfm-propagate-changes.exe game-test run-server --branch feat/1.19.2/packet-computation --filter sfm:touch_display_server_invariant,sfm:client_manager_signing --wait-for-build-lock --log-file platform/minecraft/build/touch-display-signing-server-gametests.log --log-filter info` passed both required tests, exit 0. Discovery explicitly skipped the client-only read and circuit classes. Both this server and the preceding integrated client saved and exited normally; neither remains running. Their console logs are under `platform/minecraft/build/sfm-toolchain/run/`.

### [x] P10. In-world terminal integration — G23, G25, G35

Completion checkpoint: the ambient real-process circuit and the file-driven actual block-renderer journey below both passed. The baseline feature is testable; aggregate release checks, final metadata-path regression and target propagation remain cross-cutting obligations.

Worker readiness checkpoint: at `db09e33e1`, the clean `sfm` CLI source tree was `e68c3f3aa7b71032d299d6eb2e1a257cefa4b72a`. Its full check passed formatting, strict all-target/all-feature Clippy, 68/68 tests and generated-Java verification under locked/offline Cargo. The release installer passed; installed `sfm.exe` matched the worktree release artifact at SHA-256 `21AF6650A09D2D8C198149B6E2274DA1C93AFE8CE345AA5BDB9B3E414720EB99`, version `0.1.0`. The installed worker emitted ready, red frame, one acknowledged `(0.25, 0.75)` touch, blue frame and bye; PID 29068 exited 0 with empty stderr. No user installation is needed for this checkpoint. Dependency declarations and generated Java stayed unchanged.

Foundation checkpoint: checkout-local `sfm terminal worker` reads bounded newline-delimited JSON and emits ready/ack/render/bye records. It performs no arbitrary command execution. A repeated CLI library run passed 68/68 with `cargo test --locked --offline --lib`; the installed CLI is not yet the final feature build. The Java broker limits sessions, mounts, image size and input ownership; it fans one session's latest raster out to approved mounts and isolates cleanup failures. Its service adapter performs bounded background I/O and validates loopback endpoint and absolute executable selection. All 33 `TouchDisplayTerminal` tests passed in the broad `Client` run above. Interactive mode preserves the configured normal shell, never polls JSON worker records, and pairs mouse press/release; the structured test worker remains an explicit mode. Production mounting, structured-worker GameTest and final installer proof remain required.

Production checkpoint: explicit create/connect-existing mounting controls, separate raster/input permissions and bounded teardown are now present. The later combined client unit run passed 43 terminal tests. Six additional parser/lifecycle regressions await the next run. The first ambient `sfm:touch_display_terminal_integration` run failed before terminal setup because its generated UUID channel contained hyphens, which unquoted SFML channel syntax rejects. The fixture now uses a hexadecimal UUID; regression tests retain both the rejected input and accepted form. This failed run is not terminal acceptance evidence. Its client exited normally; no helper was started by that failed setup.

Launch integration correction: the second ambient run parsed its SFML but failed with `terminal_fixture_worker_configuration_failed`. Its generated launch lacked `sfm.controlCliExecutable`: the launcher provisioned the local worker for file-driven previews, not ambient client GameTests. The shared predicate now covers both automation kinds while excluding ordinary clients and servers. The focused `game_puppet_preview_tests` library suite passed 15/15 offline. Full launcher checks, installation and a fresh terminal runtime proof remain required. The failed client saved and exited normally; no terminal helper was created.

Launcher checkpoint: formatting, strict Clippy, build and 717 library tests passed, with 3 ignored. The full check reached 9/10 passing Java-analysis integration tests; the remaining snapshot scenario differs in existing JDK source/cache identity, source offsets and a missing partial-index cache. No snapshots or dependency files were changed. `install.ps1` then passed locked/offline. The installed launcher reports `0.1.1`, revision `bd7ddf050`, SHA-256 `B8EE8415D3993DAD46D604D8B8A20FFDEFDD59233B586AF433B71FE1EDC3829E`; version and client-test help smoke checks passed. This is an intermediate installation, not final release freshness evidence.

Fresh combined client regression run: 2,075 passed, one failed and 5 explicitly optional tests aborted (2,081 found). The failure is `TouchDisplayTerminalActionTests::ambientFixtureClientDeclarationParsesAndLinksItsExactCapabilities`: `input` in the action ID is a reserved SFML token. The narrow correction adds quoted static action IDs to INVOKE; computed action IDs remain prohibited. This run is not a passing aggregate acceptance result.

Ambient runtime evidence: `sfm-propagate-changes.exe game-test run-client --branch feat/1.19.2/packet-computation --filter sfm:touch_display_terminal_integration --wait-for-build-lock --log-file platform/minecraft/build/touch-display-terminal-integration-provisioned.log --log-filter info` passed its one required test, exit 0. The generated launch included the absolute checkout-local `sfm.controlCliExecutable`. The real circuit passed in 9,165 ms: touch item, server-manager broadcast, consented future-only inbox, fixed structured worker, one ACK, changed bounded raster, revocation, unsubscribe and owned-process cleanup. It verified no screen/player/camera changes. JVM PID 32336 saved and exited normally; no test-owned helper or worker remained.

World-rendering evidence: `sfm-propagate-changes.exe puppet run sfm:in_world_touch_display_terminal --branch feat/1.19.2/packet-computation --variant 1280x720@auto --wait-for-build-lock --log-file platform/minecraft/build/touch-display-terminal-world-puppet.log --log-filter info` exited 0. Its five file-driven requests proved real BER rendering, one normal gameplay press, worker ACK count 1, a changed 400-by-320 terminal raster, server content revision still 1 and owned cleanup. No synthetic frame selection or OS pointer injection was used. Screenshots 2 and 4 visibly show terminal text on the block face before and after the touch acknowledgement. Artifacts: `platform/minecraft/build/sfm-toolchain/artifacts/game-test-preview/runs/sfm-in_world_tou-20260919-233028-975/`. JVM PID 26308 saved and exited normally. The first driver invocation rejected a relative readiness path before sending any requests; running it from the game directory completed all five steps. The action now emits absolute normalized control/screenshot paths, with a regression guard awaiting the final unit rerun.

Dependencies: P9. A later product milestone, not part of first Touch Display and Client Manager acceptance, but included in the active full-feature goal by G37.

Final-source world proof on 20 September: the same terminal puppet passed all 5 requests after the READY-digest race fix and absolute metadata-path correction. The driver ran from the repository root without a path workaround. One real gameplay press produced one ACK, zero rejected inputs and a changed 400-by-320 raster; server content remained revision 1. Figures 2 and 4 visibly show the terminal text before and after acknowledgement. Artifacts: `platform/minecraft/build/sfm-toolchain/artifacts/game-test-preview/runs/sfm-in_world_tou-20260920-001744-583/`; launcher log: `touch-display-terminal-final-world-puppet.log`. Both driver and canonical launcher exited 0; client PID 2700 exited normally after fixture-owned cleanup.

Work: mount terminal raster on a Touch Display and route touch events to an authorised desktop terminal session without opening a full-screen Minecraft UI. Define input focus/lease, session selection, logical resolution and multi-viewer behaviour. Investigate a newline-delimited JSON test shell so tests need not parse PowerShell prompts. Decide whether `EVERY TOUCH` adds value after packet queue semantics are defined. Screen sharing requires explicit bandwidth/security policy and must not inherit a generous single-terminal limit accidentally.

Validate: `sfm-propagate-changes.exe game-test run-client --branch feat/1.19.2/packet-computation --filter sfm:touch_display_terminal_integration`.

Done when: this full-feature goal proves terminal raster on the block face and one authorised touch-to-desktop event, with bounded process ownership, structured I/O, session selection and teardown; the earlier touch packet circuit remains independently testable.

Evidence: baseline model, ambient and world-rendering results above. Session creation/connect-existing controls preserve separate raster/input grants; model tests prove input ownership and multi-mount cleanup. This journey does not claim remote screen sharing or arbitrary external PTY attachment.

## Cross-cutting validation and propagation

Release work remains explicit even though P0–P10 have baseline feature proofs:

- [x] R1. Close baseline release regressions and packet-polish visual acceptance. G40 replaces the physical Shift gate with a shared semantic mode and registered expand/compact/reset actions. Final unit, ambient client, dedicated-server and file-puppet validation passed, including actual rendered expanded/compact hover and cleanup.
- [~] R2. Integrate canonical 1.19.2 and propagate the adjacent version chain. Preserve destination dependency declarations, lockfiles and target adapters. Review nondependency feature metadata separately, then compile and exercise the supported renderer/network surfaces.
- [~] R3. Finish operational readiness and manual handoff. The baseline installation, hashes, compile smoke and process cleanup passed below. Repeat the relevant freshness and target checks after propagation changes; this baseline checkpoint is not final multi-version acceptance.

R2 integration work on 20 September: the fresh preflight found all 10 canonical
worktrees clean, no active merge, and unchanged destination HEADs. Canonical
1.19.2 has 14 commits absent from the feature branch; the feature has 31 absent
from canonical. `git merge --no-commit --no-ff 241431274` in canonical 1.19.2
produced the expected 6 conflicts, all subsequently resolved. Validation below
precedes the integration commit and any later-target hop. Keep canonical dependency declarations and lockfiles at
`707f53f4a`; the editor manifest's 18 keyword additions are nondependency
metadata. The clean feature checkpoint remains available until the combined
canonical result is validated, committed and brought back to the feature branch.

Parallel ownership is deliberate within this one integration: buffer
persistence and cross-resource regressions; client registrations and puppet
state/bounds; and the launcher worker-source override are separate edits.
The main integration owns generated resources, changelog, documentation,
serial builds, installation and aggregate acceptance. Buffer reload must
preserve both canonical redstone/comparator behavior and image persistence,
including cached handler identity. Client contributors belong in canonical
`SFMClientRegistrations`, not duplicated common-side registration.

The later targets do not have a control-worker Cargo manifest. Source audit
confirmed that the existing launcher requires one before Java starts, so a
Java executable override alone cannot run their client automation. The worker
itself speaks versioned control/terminal protocols without a Minecraft-version
or checkout runtime dependency. Add an explicit launcher control-worker source
root, build against that root's existing locked inputs, and publish its exact
executable/provenance. Keep checkout-local provisioning as the default and
preserve source-navigation freshness checks. This source-only adapter does not
authorise importing missing manifests or changing destination dependencies;
each target still needs runtime proof.

Combined canonical validation: `run data --branch 1.19.2 --log-file
platform/minecraft/build/packet-canonical-integration-datagen.log --log-filter
info` passed, regenerating the merged language cache. `test run --branch
1.19.2 --wait-for-build-lock --log-file
platform/minecraft/build/packet-canonical-integration-unit-tests.log
--log-filter info` passed 2,530 tests with zero failures or skips and 5 optional
aborts (2,535 found). Both used the unchanged installed launcher `bc4689326`;
these runs prove the combined Java source, not the new launcher adapter.
The unfiltered dedicated-server suite (`game-test run-server --branch 1.19.2
--wait-for-build-lock --log-file
platform/minecraft/build/packet-canonical-integration-server-tests.log
--log-filter info`) passed all 339 required tests on attempt 2, exit 0.
Attempt 1 failed only `falling_anvil_xp_shard`, reporting Thermal phytogro
instead of shards; preserve that intermittent failure in the evidence.
No dependency declaration or lockfile differs from canonical `707f53f4a`.

The new source-built launcher passed all 354 integrated-client GameTests with
`game-test run-client --branch 1.19.2 --control-cli-source-root
'<feature-checkout>' --wait-for-build-lock --log-file
platform/minecraft/build/packet-canonical-integration-client-tests.log
--log-filter info`, exit 0. The launch properties name the selected worker
and its source root. The tooltip action test and real terminal circuit passed;
the latter completed in 12,599 ms. The client saved and exited normally.
This proves the cross-checkout worker path on 1.19.2, not later-version support.

Combined tooling checks passed frozen dependency policy, nightly formatting,
all-feature Clippy with warnings denied, build, 738 library tests (4 ignored),
12 release-review Git tests and 40 materialization tests. Documentation tests
discovered no cases. Java-analysis integration again passed 9 and failed 1;
all 36 actual scenario outputs match the prior feature run structurally, with
the same 35 snapshot mismatches. The cause remains the selected JDK source
corpus and missing versus partial dependency index, not an integration change.
No snapshot was accepted. Logs: `packet-canonical-tooling-tests.log` and
`packet-canonical-tooling-release-tests.log` under `platform/minecraft/build`.
The current editor parser test also passed 9/9. Installation after the merge
checkpoint remains a separate acceptance step.

The canonical `puppet run sfm:in_world_packet_inspection --branch 1.19.2
--control-cli-source-root '<feature-checkout>' --variant 1280x720@auto
--wait-for-build-lock --log-file
platform/minecraft/build/packet-canonical-inspection-puppet.log --log-filter
info` passed all 11 file requests, exit 0. Figures 2 and 3 were visually
inspected for expanded formatted data and compact data hiding. The final
request restored owned state, and client PID 10396 exited normally. Artifacts:
`platform/minecraft/build/sfm-toolchain/artifacts/game-test-preview/runs/sfm-in_world_pac-20260920-034425-606/`.

First-hop preflight found another frozen boundary: 1.19.4's launcher manifest
lacks dependencies used by the current baseline launcher source. Do not copy
that source over the older manifest and leave an unbuildable crate. Preserve
the destination launcher tree and the absent worker crate together; use the
canonical installed launcher and its explicit control-worker source root for
feature acceptance. This is a documented tooling-source exception, not a new
Minecraft source of truth. The existing version-surface audit already reports
later launcher divergence. Source-navigation C-11 is part of the unrelated
pre-feature backlog and requires separate symbol-worker freshness evidence;
packet/display runtime proof does not imply that acceptance.

Final-source unit evidence on 20 September: `test run --branch feat/1.19.2/packet-computation --wait-for-build-lock --log-file platform/minecraft/build/touch-display-final-packet-inspection-unit-tests.log --log-filter info` passed 2,494 tests, zero failures or skips and 5 optional aborts (2,499 found), exit 0. This includes all 4 packet-inspection source guards and all 5 terminal visual-control regressions.

Final-source runtime evidence: the unfiltered dedicated-server rerun passed 237/237, exit 0 (`touch-display-final-source-server-tests.log`). The integrated-client rerun reported `required=252 total=252` with all passing (`touch-display-final-source-client-tests.log`). Its ambient terminal circuit passed in 8,386 ms. These runs include the image reload correction and new packet-inspection fixture; the final client shutdown is checked separately below. The editor's `node --test tests/quoted-invoke.test.cjs` also passed 9/9 again using the already-verified locked package versions, without fetching dependencies.

Final tooling-check evidence: the safe staged equivalent of `check-all.ps1` passed dependency-policy metadata, nonmutating nightly `fmt --check`, locked/offline all-feature Clippy and build. The script itself omits frozen Cargo flags and runs mutating formatting, so it was not executed unmodified. Tests passed 717 library cases (3 ignored), 12 release-review Git cases and 40 release materialization cases. Java-analysis integration passed 9 and failed 1 with the existing JDK/cache-identity snapshot mismatch; the overall test exit was 101. No snapshots were accepted. An initial elevated test attempt stopped on exact-worktree Git ownership; the rerun used process-local trust, not global configuration. Artifacts: `platform/minecraft/build/final-tooling-check-20260920/tests.log` and `stage-summary.json`. Dependency declarations and lockfiles stayed unchanged. Installation after the source checkpoint remains required.

The installed `sfm.exe` was rechecked on 20 September: version 0.1.0, SHA-256 `21AF6650A09D2D8C198149B6E2274DA1C93AFE8CE345AA5BDB9B3E414720EB99`, matching the release artifact. Its source tree remains `e68c3f3aa7b71032d299d6eb2e1a257cefa4b72a`, with no worker or generated-protocol changes since the passing worker installation above. No worker reinstall is needed for this checkpoint.

Baseline source checkpoint: `bc4689326` records terminal mounting, quoted INVOKE parser parity, recipes, buffer reload correction, file puppets and their evidence. The full integrated-client runner exited 0 after normal shutdown. The 53-file checkpoint excludes dependency declarations and lockfiles. Generated-resource cache whitespace remains generator-owned; the rest of the staged diff passed `git diff --check`. No remote push or canonical merge was performed.

### Operational readiness at the baseline checkpoint

Target: `feat/1.19.2/packet-computation` at `bc4689326`. The launcher source tree
is `2a1322813b5ea097a79aacc3b7f442b6dfa81613`. Its changed automation-worker
provisioning required rebuilding the installed tool. Running
`platform/cli/sfm-propagate-changes/install.ps1` passed with locked/offline Cargo.
The resolved `sfm-propagate-changes.exe` on PATH reports version 0.1.1, revision
`bc4689326`, and SHA-256
`9CAA419E4B0F73D70D25DB74B59FD4A278EAC55C410AB7B2435194FBC26C4613`, identical
to the worktree release artifact. User must run install script: no.

The newly installed command passed `run compile --branch
feat/1.19.2/packet-computation --log-file
platform/minecraft/build/touch-display-installed-final-compile.log --log-filter
info`, compiling main, GameTest, datagen and test sources, exit 0. It acquired
the branch lock without `--wait-for-build-lock`, proving the earlier tests had
released it. This installation follows the final tooling mutation; later
documentation-only commits do not change that source tree.

Dependency posture remains frozen. Declarations and lockfiles are unchanged;
no new developer/reference repository was acquired. The final builds reused
locked caches. Final process inspection found no running branch-owned game,
terminal worker/helper, Cargo or launcher. Packet-preview PID 16332,
terminal-preview PID 2700 and final aggregate-client PID 36056 all exited
normally; runtime fixtures verified owned helper cleanup. No test window is
left open.

The copyable handoff is in `docs/touch display and client manager testing.md`,
including an ordinary client launch and a known-packet command. G40 supersedes
the physical-key gate with semantic action acceptance. Remaining limits are
the recorded tooling JDK/cache snapshot failure and unstarted canonical/version
propagation. R1's action acceptance passed; R2 is the next implementation work.
The full goal remains incomplete.

Full baseline unit evidence: `sfm-propagate-changes.exe test run --branch feat/1.19.2/packet-computation --wait-for-build-lock --log-file platform/minecraft/build/touch-display-final-unit-tests.log --log-filter info` passed 2,489 tests with zero failures, zero skips and 5 optional aborts (2,494 found), exit 0. This includes 66 multiplayer, 49 terminal, 4 quoted-action grammar and 4 visual-control tests, including the absolute metadata-path guard. The previous aggregate run exposed a source-span test that equated original text with canonical formatting; the corrected regression now proves source mapping for both quoted and unquoted action IDs. Final `run compile` passed all four Java source sets. `run data` also passed with no tracked generated changes. Unfiltered runtime suites, remaining visual acceptance and target propagation are separate checks.

Editor parity checkpoint: the consumed VS Code parser was still stale after the quoted-action grammar edit. The new `tests/quoted-invoke.test.cjs` reproduced 5 failures and 4 passes before regeneration, then passed all 9 checks after regeneration. Both INVOKE forms, the complete manual terminal snippet, punctuation, retained unquoted IDs and computed-ID rejection are covered. TypeScript `--noEmit` passed. Existing ANTLR `0.5.0-alpha.4` and TypeScript `5.9.3` packages matched the feature lockfile's versions and integrity records; no package fetch or manifest/lockfile edit was needed. Generated outputs use the repository's existing whitespace convention.

Release review follow-ups: read-only terminal review found a visual-test timing race between the initial raster and the later READY raster. The fixture now waits against the READY digest. The repaired unfiltered unit run (`touch-display-final-unit-tests-repaired.log`) passed 2,490 tests, zero failures or skips, with 5 optional aborts (2,495 found), including all 5 visual-control regressions. Production lifecycle and consent checks had no additional concrete finding. The manual guide now distinguishes retained inbox entries from old physical mailbox packets, and uses the terminal's exact channel and publisher scope when explaining remote grants.

The first unfiltered dedicated-server run failed recipe coverage: the new Client Manager and Touch Display were not craftable, and the computation-generated packet had no documented recipe exemption. All three launcher attempts failed that same coverage check; the final attempt reported only `recipes` failing. The correction gives Client Manager the manager layout with a comparator instead of a repeater, and Touch Display a glass/iron-bar/cable/redstone recipe. Packet items remain generated by effects and are explicitly exempt, like other non-crafting outputs. Data generation and a complete rerun are required before accepting these changes.

Repaired aggregate runtime evidence: `game-test run-server --branch feat/1.19.2/packet-computation --wait-for-build-lock --log-file platform/minecraft/build/touch-display-final-server-tests-repaired.log --log-filter info` passed all 237 required tests on its first attempt, exit 0. `game-test run-client --branch feat/1.19.2/packet-computation --wait-for-build-lock --log-file platform/minecraft/build/touch-display-final-client-tests.log --log-filter info` passed all 252 required tests, exit 0. The ambient terminal passed alongside the other tests in 6,836 ms. Client PID 12736 exited normally after its 25-second grace period. These runs follow successful `touch-display-recipes-datagen.log` generation and precede the new in-place image reload regression.

The image reload regression reproduced the concrete failure on 20 September: `game-test run-server --filter sfm:touch_display_image_transfer` exited 1 with `Image reload retained the old image or interaction state` (`image-buffer-reload-red.log`). The test keeps the same capability handle through replacement, omitted and malformed image data, and an occupied ordinary-item buffer. The production fix clears only the current image through its existing handler before restoring persisted data; it never replaces the contents object or erases an occupied nonpersisted resource. The same focused GameTest then passed 1/1, exit 0 (`image-buffer-reload-green.log`). This also compiled the new packet-inspection puppet; its runtime acceptance remains separate.

Packet polish acceptance remains separate from the display circuit. `puppet run sfm:in_world_packet_inspection --branch feat/1.19.2/packet-computation --variant 1280x720@auto --wait-for-build-lock --log-file platform/minecraft/build/packet-inspection-puppet.log --log-filter info` passed all 8 file-driven requests, exit 0. Actual vanilla rendered hover and keyboard-handler Alt+D ingress opened immutable read-only packet and ordinary-item documents; the no-hover background opened a blank writable editor. Figures 1 and 2 were visually inspected for the item, compact tooltip and readable document. Artifacts: `platform/minecraft/build/sfm-toolchain/artifacts/game-test-preview/runs/sfm-in_world_pac-20260920-001217-374/`. Client PID 16332 exited normally after owned-screen, pointer and preference cleanup. This client-only disposable container does not claim server-inventory evidence. Physical Shift is polled through native GLFW and cannot be proven by an injected game key callback. G40 superseded the proposed physical-key gate with registered tooltip-mode actions and automated acceptance. Native Shift polling is not claimed by the puppet and is not a release gate. No OS input automation is authorised or used.

Propagation preflight on 19 September: all 10 canonical version worktrees were clean, with no active merge. Canonical `1.19.2` at `707f53f4a` and feature checkpoint `bd7ddf050` had 14 canonical-only and 25 feature-only commits. Their merge is not a fast-forward. A read-only committed-snapshot preview found conflicts in the puppet Minecraft runtime, one generated-resource cache file, `SFM.java`, both buffer entity classes and the changelog. Preserve canonical redstone/buffer work while adding the feature.

The canonical pre-propagation `cargo run --locked --offline -- audit --branch core --version-surfaces` completed with exit 0 at `707f53f4a`. Its warnings are advisory, not clean acceptance: all 9 later branches have differing CLI source trees, and 19,312 Java hunks fall outside version-dependent annotations. Counts by target are 1.19.4: 1,667; 1.20: 1,951; 1.20.1: 1,954; 1.20.2: 2,022; 1.20.3: 2,060; 1.20.4: 2,083; 1.21.0: 2,349; 1.21.1: 2,371; 26.1.2: 2,855. The separate source audit reports 1,139 existing warnings. Canonical source and dependency hashes stayed unchanged; all audit-owned processes exited. The full canonical artifact is `platform/minecraft/build/pre-propagation-version-surfaces-console.log`, SHA-256 `8CD7B748757A634672380F6A3CBB76637CC308F93454A653F4223B295EAB16D3`. Post-propagation review must distinguish inherited backlog from feature adapter regressions.

The later targets also have an existing propagation backlog: `1.19.4`, `1.21.1` and `26.1.2` each lacked 186 canonical commits, including 172 before the feature fork. Their dependency declarations already differ. Frozen dependency posture is destination-relative: preserve each destination's dependency declarations and lockfiles. Nondependency feature metadata in a mixed manifest needs a separate, field-level review. The existing `git merge` CLI commits clean hops automatically, and `--auto-abort` is not a read-only mode. It cannot guarantee that invariant before committing. Use a documented adjacent-version `git merge --no-commit --no-ff` review point if necessary, record each destination HEAD and explicit frozen paths, retain its dependency bytes, graft version adapters, then validate before committing. If the feature cannot compile against a destination's frozen dependency graph, request a narrow dependency amendment rather than accepting inherited dependency changes. No propagation has begun.

The 20 September read-only recheck found all 10 canonical worktrees clean, with
no `MERGE_HEAD`. Their HEADs had not moved since 17 September. Canonical 1.19.2
remains `707f53f4a`; feature checkpoint `0eeac903a` has 29 feature-only commits
against 14 canonical-only commits. The fresh `git merge-tree --write-tree
--name-only 1.19.2 0eeac903a` preview produced tree
`b71e3c9d242025f5f0db5220db7a31530585b4b2` and the same 6 conflicts listed above.
It wrote only unreferenced Git objects; no branch, index or worktree was merged.
The preview retains captured GameTest bounds in the shared `startGameTest`
overload; the name-based overload delegates there. This checks one automatic
merge, not the unresolved runtime conflict or post-integration behaviour.

The preview changes the editor's `package.json` only by adding 18 language
keywords. Its dependency fields do not change. Restoring that entire file
would discard approved editor metadata, so the earlier whole-manifest byte
rule was too broad. Preserve dependency fields and the complete package
lockfile while retaining the keyword additions. This correction does not
authorise a dependency version, repository, feature or graph change.

The frozen-input inventory for each hop includes:

- all existing `Cargo.toml`, `Cargo.lock`, npm dependency fields and package
  lockfiles, plus `platform/minecraft/sfm-toolchain.lock.json`
- Minecraft `build.gradle`, `settings.gradle`, `gradle.properties`, wrapper
  properties, versioned dependency, plugin and Java-toolchain declarations;
  include `gradle/plugins/<version>/plugin-classpath.txt`, which also contains
  external coordinates
- shared repository, lockfile-feature and version-selection scripts; each
  target tracks the whole versioned matrix, including directory `1.21` for
  branch `1.21.0`
- dependency ranges in `META-INF/mods.toml` or `neoforge.mods.toml`, separately
  from ordinary mod metadata
- configuration, source-set/exclusion, jar-in-jar and access-transformer
  inputs where a change affects dependency activation, packaging or mappings

New files also need review. Only canonical 1.19.2 currently tracks
`platform/cli/sfm/Cargo.toml` and `Cargo.lock`; the 9 later targets do not.
Copying them would add dependency declarations even if every existing file
stayed unchanged. Do not import those manifests automatically under the
frozen posture. Before that hop, establish whether the existing baseline-built
worker can provide the required target support without adding declarations,
or request a narrow dependency amendment. A clean Git merge is not dependency
approval. The inventory and preview do not replace R1's acceptance evidence.

R1 semantic tooltip amendment (20 September): `AUTO` follows the existing
configured key; `EXPANDED` and `COMPACT` bypass physical polling. The registered
human/palette/CLI actions are `sfm:tooltip/more_info/expand`,
`sfm:tooltip/more_info/compact` and `sfm:tooltip/more_info/reset` (to `AUTO`).
The mode is transient and resets after leaving a real player session;
null-player connection transitions preserve it. No client-program permission is
added. Packet, disk, label-gun and form presentation share the same decision.
The ambient tooltip GameTest must invoke registered actions and read actual
item tooltips in one client task, restoring the prior mode in `finally` without
opening or replacing a screen. The opt-in file puppet must capture actual
expanded and compact hover, exercise reset and restore its prior mode even on
abort. Pure tests separately prove configured-key adaptation and lazy polling.
No result is labelled as physical-key injection evidence.

Regression evidence: before implementation, `test run --branch
feat/1.19.2/packet-computation --filter PacketInspectionPuppetSourceTests
--log-file platform/minecraft/build/tooltip-mode-puppet-red.log --log-filter
info` passed the existing 4 guards and failed the new action-path guard, exit 1.
The final unfiltered unit run, `tooltip-mode-final-unit.log`, passed 2,511
tests with zero failures or skips and 5 optional aborts (2,516 found), exit 0.
This includes 16 tooltip service/action/compatibility tests and 5 packet-puppet
guards. The real null-player Forge logout event preserves each override;
leaving a player session resets without polling. The deprecated raw-key
predicate retains its physical semantics. A failed puppet request restores
owned state before publishing its error and terminating the journey.

The unfiltered integrated run, `tooltip-mode-final-client-tests.log`, passed
all 252 required GameTests, exit 0. `packet_item_tooltip` passed through the
registered actions and production packet/disk tooltip paths without owning a
screen. The existing ambient terminal circuit also passed. The client saved
and exited normally. The unfiltered dedicated-server run,
`tooltip-mode-final-server-tests.log`, passed all 237 required tests on its
first attempt, exit 0. Discovery explicitly skipped `PacketItemTooltipGameTest`
before class loading. Server PID 9012 saved and exited normally.

Final visual evidence: `puppet run sfm:in_world_packet_inspection --branch
feat/1.19.2/packet-computation --variant 1280x720@auto --log-file
platform/minecraft/build/tooltip-mode-final-packet-puppet.log --log-filter
info` passed all 11 numbered requests, driven by
`platform/minecraft/Run-PacketInspectionPuppet.ps1`. Both driver and canonical
launcher exited 0. Figures 2 and 3 were visually inspected: full pretty packet
JSON in expanded mode, no payload and an accurate palette hint in compact
mode. Reset returned `AUTO`; final observation reports no screen, zero failed
requests and `tooltip_mode_restored: true`. Actual Alt+D inspection and immutable
documents still passed. Artifacts:
`platform/minecraft/build/sfm-toolchain/artifacts/game-test-preview/runs/sfm-in_world_pac-20260920-030346-853/`.
Client PID 19884 saved and exited normally; no OS input automation was used.

G40 operational checkpoint: this is Java, GameTest, generated localization and
documentation work only. `run data` passed (`tooltip-mode-datagen.log`); all
four Java source sets compiled in the validation runs. No CLI or generated
protocol source, dependency declaration or lockfile changed. The installed
launcher remains 0.1.1 at `bc4689326` and its SHA-256 is unchanged from the
baseline above; the worker hash is also unchanged. User must run install
script: no. All launched game/server sessions exited; sequential final runs
acquired the branch lock without a waiting override. The testing guide includes
the three actions, ordinary client launch and repeatable puppet command.

Non-blocking harness diagnostic follow-up: if writing an already-failed
request's error response also fails, the write exception can mask the original
operation error. Owned-state cleanup has already run and the journey still
fails; this does not weaken tooltip acceptance or require manual input.

Read-only adapter map for R2, not target acceptance:

- Canonical 1.19.2 already separates client registration into `SFMClientRegistrations`. Add the feature contributors there once. Preserve its redstone buffer persistence, comparator notifications and cached handlers while adding image persistence. Preserve its puppet preparation/reset and captured-bounds handling alongside the feature's gameplay-use and split start/wait helpers.
- Both 1.21.1 and 26.1.2 already wrap packets as `CustomPacketPayload` with `StreamCodec<RegistryFriendlyByteBuf>`. Add the feature registrations and exact context checks to those wrappers; do not replace them with baseline `SimpleChannel`. Preserve strict channel version 1.4.0. The multiplayer frame is bounded to 8,192 bytes.
- Use target disk components and `DiskItem`/`LabelPositionHolder` accessors for source and labels. Keep projection bounded instead of serializing the whole disk. Adapt block-entity storage to provider-aware tags in 1.21.1 and `ValueInput`/`ValueOutput` in 26.1.2. Their SavedData APIs also differ: factory-based versus codec-backed `SavedDataType`.
- Preserve server authority when adapting 26.1.2: owner checks use `player.nameAndId()`, command level 4 uses the target `Commands.hasPermission(Commands.LEVEL_OWNERS)`, and server lookup follows `SFMEntityUtils.getLevel(player).getServer()`. Malformed policy data still quarantines and denies.
- Keep pure consent/signing models. Adapt screens to `GuiGraphics` in 1.21.1 and extracted rendering plus typed input events in 26.1.2. Preserve Cancel focus, delay, accessible confirmation and secret clearing. Use target identifier factories without changing public IDs.

Repository workflow prohibits direct Gradle invocation. Each task above has a stable focused filter. At release-review boundaries, run the unfiltered suites and generation/compile checks:

```pwsh
sfm-propagate-changes.exe test run --branch feat/1.19.2/packet-computation
sfm-propagate-changes.exe game-test run-client --branch feat/1.19.2/packet-computation
sfm-propagate-changes.exe game-test run-server --branch feat/1.19.2/packet-computation
sfm-propagate-changes.exe run data --branch feat/1.19.2/packet-computation
sfm-propagate-changes.exe run compile --branch feat/1.19.2/packet-computation
sfm-propagate-changes.exe run client --branch feat/1.19.2/packet-computation --wait-for-build-lock
```

Use the repository's frozen dependency posture unless a goal explicitly authorises a dependency change. Establish process/cache ownership before long launches; use bounded waits and a log file/filter if build progress is buffered. If the propagation CLI itself changes, run `platform/cli/sfm-propagate-changes/check-all.ps1` and perform its final installer/freshness proof after the last tooling edit. Java/docs-only changes do not require reinstalling the CLI.

Implement gameplay changes on the oldest supported target, normally 1.19.2. Feature worktrees are intentionally skipped by ordinary propagation. After work reaches the version branch, preserve newer-version adaptations and run this before and after propagation from the CLI project:

```pwsh
Set-Location platform/cli/sfm-propagate-changes
cargo run -- audit --branch core --version-surfaces
```

Update `platform/minecraft/src/main/resources/assets/sfm/template_programs/changelog.sfml` for player-visible behaviour; generate models and localisation with `run data` instead of hand-editing generated output.

## Support matrix

| Surface | Baseline feature evidence | Remaining acceptance |
| --- | --- | --- |
| Minecraft 1.19.2 | 2,511 unit tests, 237 dedicated and 252 integrated GameTests passed after G40; final 11-step file-driven tooltip/inspection proof passed. | Canonical integration and post-integration operational checkpoint. |
| Minecraft 1.19.4, 1.20, 1.20.1 | Not yet propagated; next adjacent targets. | Preserve target dependency files, compile and relevant runtime regression tests at each hop. |
| Minecraft 1.20.2, 1.20.3, 1.20.4 | Not yet propagated; loader/API adapters require review. | Preserve target adapters and dependencies, compile and focused renderer/network tests. |
| Minecraft 1.21.0 | Not yet propagated; component and loader APIs require review. | Compile and focused component, renderer and network tests. |
| Minecraft 1.21.1 | Not yet propagated; requires `ResourceLocation` adapters. | Compile and target-specific renderer/network tests. |
| Minecraft 26.1.2 | Not yet propagated; requires `Identifier` adapters with the same public IDs. | Compile and target-specific renderer/network tests. |
| Private integrated client | Full Touch Display, Client Manager, consent, inbox, off-by-default signing and in-world terminal circuit; final aggregate passed. | Canonical integration and propagated-target regressions. |
| Dedicated server | Server-safe value, schema, block and resource invariants; client-only classes excluded; final aggregate passed. | Canonical integration and propagated-target regressions. |
| Remote multiplayer | Separately negotiated P8G transport passed the isolated client/server proof with exact operator grants; legacy private gates remain closed remotely. | Target propagation; Mojang authentication is not claimed by the offline loopback fixture. |

## Risk register

| Risk | Consequence | Control and evidence |
| --- | --- | --- |
| Client-reported plausible UV is forged | A player claims another pixel on the touched face. | Vanilla reach plus server face/bounds checks; server chooses block, rear target and semantic state. Treat UV as player input, not trusted state. |
| Loaded-world reads become an x-ray convenience | Programs reveal more world state than intended. | Restrict to already-replicated loaded state, distance/capability scope and server/client policy; audit new read actions individually. |
| Inventory summaries disclose private contents | A server program broadcasts data to unintended clients. | Explicit recipient/address policy and authorisation; no implicit unopened-inventory replication. |
| Images or frames exhaust heap, GPU or network | Client instability or multiplayer denial of service. | P2B and P7B byte/dimension/count/upload budgets, newest-wins queues and lifecycle cleanup tests. |
| Consent prompts spam or trap the player | Coercive or unusable client experience. | No proximity modal, idempotent requests, coalesced notice and persisted denial/retry policy. |
| An action adapter bypasses policy | Program gains clipboard, filesystem, process or server effects. | Default-deny descriptors and P8B's principal-aware service below every adapter. |
| Signer trust is broader than intended | A trusted author silently gains new capabilities. | Trust author plus capability subset; capability resolution changes require consent; keys remain client-owned. |
| Version propagation changes public behaviour | Old/new targets disagree on schema, IDs or rendering. | 1.19.2-first work, version-surface audit, compatibility tests and target adapters. |
| Held item or offhand handles the same use | Duplicate or missing touch packets. | Empty-hand base test, consuming main-hand result and explicit O2 policy tests. |
| Dirty-worktree drift invalidates evidence | The plan cites tests from a different source state. | P0 reconciliation; record commit and commands after final edits, never infer validation from the current HEAD alone. |

## Open gates for review

| Gate | Why it remains open | Default until resolved |
| --- | --- | --- |
| O1. Rear inventory failure — resolved for P3 | A full/missing handler must not be mistaken for delivery. | Consume once, show an actionbar failure to the player, and neither insert nor spill a packet; dedicated-server tests cover both cases. |
| O2. Held-item priority — scoped for P3 | Forge can let an item handle use before the block. | Promise the empty-hand vanilla use path and consume main-hand block use to prevent offhand fallback. A held item that intercepts use before the block retains its vanilla priority; the file-driven physical-click check will record actual dispatch. |
| O3. Image storage — resolved in P2B | Image persistence and payload ownership needed a contract. | Immutable bounded content-addressed snapshots; buffer save/load and atomic image/state transfer are proven in P2B. |
| O4. Program capability scopes — resolved in P8A–P8C | Action schemas and evaluated scope needed a contract. | Common bounded value schemas, literal action manifests, exact statically resolved subjects and invocation-time checks. Undescribed actions remain unavailable. |
| O5. Multiplayer packet transport — resolved in P8G | Remote effects needed authority independent of the legacy private gate. | Separately negotiated default-deny exact operator grants, per-player limits and session checks; normal server block interaction still handles touches. |
| O6. Decorative signature strokes | Persisting a scribble offers novelty but creates privacy/data obligations. | Ephemeral drawing; signature cryptography ignores it. |
| O7. Exact visibility test | Render-selected/front-facing is cheaper than exact occlusion. | Use conservative eligibility and measure false positives before expanding. |
| O8. Exact rendered-frame click correlation | Server semantic revision can lag or differ from a client-only animation. | `sfm:touch@1` reports server state only; version and constrain a client report if a later use case needs exact local-frame identity. |
| O9. UV convention — resolved for P3 | Face rotation and hit rounding affect what a press reports. | U increases viewer-left to viewer-right and V viewer-top to viewer-bottom. `TouchDisplaySurface` defines a basis for all six faces; the image inset spans local ±7/16, both edges are inclusive, and at most 1e-5 face/edge float error is clamped. Other faces, inside hits and farther coordinates fail. |
| O10. Content revision lifecycle — resolved for P3 | Click correlation needs a stable server-owned content generation. | Start at 0; increment on each changed atomic image/state commit, persist and synchronize it, leave it unchanged for identical content, and reject a change at `Long.MAX_VALUE`. Breaking/replacing the block starts a new entity at 0; copying saved entity NBT retains its revision. Malformed content loads a safe fallback without reusing a lower revision. |
| O11. Computed action IDs — resolved in P8C | Static manifests cannot infer the capability of an arbitrary runtime action ID. | Only literal quoted or unquoted action IDs are accepted; computed IDs remain prohibited. |
| O12. Label binding — resolved in P6B | Missing, duplicate or competing Touch Display labels could create nondeterministic writers. | Existing label-gun bindings enter exact consent identity; competing writers are rejected and diagnosed. |
| O13. Inbox addressing — resolved in P4A | Binding delivery to a source hash could orphan messages after edits and complicate multi-viewer fan-out. | Stable channel/recipient addressing is separate from exact program consent and session-scoped subscription identity. |

## Acceptance for the overall feature

The first Touch Display and Client Manager release review is ready when a player can place a Touch Display, supply a static or client-produced image, press a known point, receive one correctly addressed packet carrying the corresponding server semantic state behind it, and use server-manager broadcast plus Client Manager inbox/render logic to update it. The player can run the client-only GameTests with one normal game window and without surrendering control to test UI. Unauthorised client programs do not tick, denied prompts do not spam, and programmatic actions cannot bypass shared permission or rate checks. Author signing remains off by default but passes source/revision/trust tests when enabled. Save/reload, old packet values, dedicated-server class loading and forward version propagation have explicit passing evidence. Exact local-frame click correlation, remote multiplayer packet send, screen sharing and decorative signature retention are outside this first acceptance. P10 retains the in-world terminal and touch-to-desktop experience as a later milestone.

The active full-feature goal is complete only after P1–P10 are all `[x]`, including the later remote multiplayer boundary and in-world terminal integration, and the support-matrix propagation, unfiltered validation, operational readiness and manual acceptance have evidence. First-release acceptance is an intermediate checkpoint, not permission to close the goal.
