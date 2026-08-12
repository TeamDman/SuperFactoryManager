# SFM in-game control CLI plan

**Plan status:** Active; I-1/I-2/I-3/I-3a foundation complete, workspace mutation is next
**Primary implementation root:** `D:\Repos\Minecraft\SFM\repos2\1.19.2`
**New Rust CLI root:** `platform/cli/sfm`
**Linked workspace plan:** `docs/tasks/contextual input actions and addressable explorer plan.md`
**Reference template:** `G:\Programming\Repos\teamy-rust-windows-utils`
**Last updated:** 2026-08-11
**Foundation implementation commit:** `8e202d915` (`Add live game control CLI`)
**Intent audit:** Passed 2026-08-11 against the complete dedicated-CLI, multi-instance, focus-recency, workspace-add, and future-control proposal

## How to update this plan

- `[ ]` Not started
- `[~]` In progress
- `[x]` Complete
- `[!]` Blocked

Keep at most one integration slice in progress. Record decisions, commit ids,
exact commands, focused/full test output, generated-protocol evidence, and live
artifact ids under the work item they prove. Work on 1.19.2 first. Do not invoke
Gradle directly, propagate, publish, or push merely because a phase is complete.

## Purpose

Create a dedicated `sfm.exe` for controlling live SFM Minecraft clients from a
shell, including the Teamy Terminal running inside the game. The first useful
journey is:

```powershell
cd D:\Repos\Minecraft\SFM\repos2\1.19.2
sfm instance list
sfm workspace add .
```

`sfm workspace add .` resolves `.` in the CLI process, discovers live SFM game
instances, and sends a typed request to the most recently focused compatible
instance. `--instance-pid <pid>` targets one exact live process. The game—not
the CLI—owns validation, workspace persistence, UI-model notification, and the
response saying what changed.

The first completed control journey is intentionally more concrete than
listing instances:

```powershell
sfm instance list
sfm invoke sfm:panel/open sfm:size_display
```

The second command selects one exact live game, sends a typed invocation
request, enters the Minecraft client thread, executes the existing registered
client-action grammar as `sfm action invoke sfm:panel/open sfm:size_display`,
and visibly opens the size-display panel. This is the first vertical proof that
the control plane can safely cause an observable in-game effect.

This establishes a reusable local control plane for later commands such as
`sfm manager list` and `sfm inventory show`. It is not a wrapper that injects
text into Minecraft chat, clicks screen coordinates, edits the game's config
files behind its back, or guesses a process from a window title.

## Authoritative user guidance ledger

| ID | Active guidance | Required plan consequence | Superseded by |
| --- | --- | --- | --- |
| ICLI-1 | Create a new CLI at `platform/cli/sfm`. | I-1 scaffolds an independently installable `sfm.exe`; it does not rename or overload `sfm-propagate-changes.exe`. | — |
| ICLI-2 | Base it on `G:\Programming\Repos\teamy-rust-windows-utils`, as the existing SFM propagation CLI was. | I-1 reuses the template's Windows/process/console/logging conventions and the repository's current Facet/Figue CLI/output conventions rather than copying stale boilerplate blindly. | — |
| ICLI-3 | The CLI must work from the in-game terminal. | All commands are ordinary noninteractive shell commands over local Vox IPC; no dependency on an external GUI terminal, inherited HWND, or Minecraft chat exists. I-5 proves invocation through Teamy Terminal. | — |
| ICLI-4 | `sfm workspace add .` adds the caller's current directory to an open game's explorer workspace. | I-4 resolves and normalizes the CLI path, transmits it as a typed request, and lets the selected game validate/persist it through the same workspace add-root executor as UI picker/drop/actions. | — |
| ICLI-5 | Multiple Minecraft instances must be supported carefully. | I-2 gives every process a unique instance id/local endpoint and published descriptor; I-3 never assumes only one game exists. | — |
| ICLI-6 | `sfm instance list` enumerates open game instances. | I-3 emits a typed list containing identity, PID, compatibility, focus recency, lifecycle metadata, endpoint health, and which instance would be selected by default. | — |
| ICLI-7 | Each game tracks when it was focused; an unqualified command targets the most recently focused instance. | I-2 records focus-gained transitions from Minecraft's real window-active state; I-3 snapshots all live descriptors and chooses the uniquely newest compatible instance with explicit ambiguity/error behavior. | — |
| ICLI-8 | `sfm workspace add . --instance-pid 1234` targets a specific game. | I-3/I-4 support the exact trailing named option, verify the endpoint's live identity/PID before mutation, and fail rather than falling back to another process. | — |
| ICLI-9 | There is currently no CLI control surface for in-game SFM behavior. | The plan adds a game-hosted SFM control service instead of extending the terminal-rendering service or pretending the existing Java terminal client is a server. | — |
| ICLI-10 | Future commands may include `sfm manager list`, `sfm inventory show`, and other in-game inspection/control. | The protocol and dispatch use versioned typed operations/results, capability discovery, client-thread handoff, and bounded structured output; no workspace-specific wire shortcut becomes the whole architecture. | — |
| ICLI-11 | Users should not need to assign an internal `sfm_source` role when adding a workspace root. | Remove `sfm:explorer/workspace/root/role/set ... sfm_source` from the public plan. A root is added as itself; project/source discovery is derived metadata and `sfm_source` remains only a toolchain-contributed source id where the older panel scene still needs it. | — |
| ICLI-12 | Preserve the proposal in a resumable, verifiable plan before implementation. | This ledger, contracts, gates, phases I-1 through I-5, topology, risks, and exact next-goal statement are authoritative. | — |
| ICLI-13 | The first goal must end with a concrete in-game operation, specifically opening the panel that displays its allocated size, rather than only `sfm instance list`. | I-3a adds the exact `sfm invoke sfm:panel/open sfm:size_display` command, registered-action-only Java dispatch, structured feedback, and a visible witness. The active goal is not complete until the panel is visibly opened through the external CLI. | — |

## Guidance traceability

| Guidance | Plan coverage | Evidence when complete |
| --- | --- | --- |
| ICLI-1, ICLI-2 | I-1 | Crate/install/help/output tests and template/current-SFM dependency decisions |
| ICLI-3 | I-1, I-3, I-5 | Console-safe CLI plus a live command entered in Teamy Terminal |
| ICLI-4 | I-4, I-5 | Path-resolution tests, game-side add-root parity, restart persistence, and live explorer evidence |
| ICLI-5, ICLI-6, ICLI-7, ICLI-8 | I-2, I-3, I-5 | Two-game discovery fixtures/live proof, focus-recency ordering, exact PID targeting, stale/PID-reuse rejection, and default marker |
| ICLI-9, ICLI-10 | Protocol/control contracts; I-2; I-4 | Game-hosted Vox service, capabilities/typed dispatch, workspace operation, and future-operation fixtures |
| ICLI-11 | Linked workspace-plan remediation; I-4 | Public help/action absence proof plus root classification metadata without manual role assignment |
| ICLI-12 | Entire plan | Three-pass audit and fresh-agent resumption review |
| ICLI-13 | I-1, I-2, I-3, I-3a | Exact CLI grammar/round trip, Minecraft-thread assertion, registered action execution result, size-display panel state, and screenshot/puppet witness |

## Intent audit evidence

- **Pass 1 — extraction:** Preserved the exact new path/name, template source,
  in-game terminal scenario, `cd` example, `workspace add .` spelling,
  multi-instance requirement, `instance list`, most-recent-focus default,
  trailing `--instance-pid 1234`, absence of a current in-game CLI, and future
  manager/inventory examples as separate guidance ids.
- **Pass 2 — local feasibility:** Confirmed `teamy-rust-windows-utils` supplies
  Windows/console/window/process foundations; `sfm-propagate-changes` supplies
  current Facet/Figue/CliOutput conventions; `teamy-terminal-control` already
  proves Vox local endpoints, per-process descriptors, and app-thread dispatch;
  Minecraft 1.19.2 exposes `Minecraft.isWindowActive()` updated by GLFW's real
  window-focus callback. The SFM mod currently consumes a terminal Vox service
  but does not host a general SFM control service.
- **Pass 3 — adversarial omission:** Checked PID reuse, stale descriptors,
  multiple equally recent/unfocused games, process focus races, incompatible
  protocol versions, game shutdown/crash, CLI path versus game filesystem
  authority, UI-thread mutation, response-after-persistence semantics,
  terminal-server coupling, Gradle-without-Rust contributors, raw config edits,
  window-title guessing, broad arbitrary-action execution, and future command
  extensibility. Each has a contract, proof, risk, or explicit non-goal.
- **Known source limitation:** None. The complete proposal and referenced local
  repositories/runtime sources were available.

## Established foundation and source evidence

- Repository rules in `docs/AGENTS.md` require 1.19.2-first work, the SFM CLI
  instead of direct Gradle, and deliberate later propagation.
- `G:\Programming\Repos\teamy-rust-windows-utils` provides `teamy-windows`,
  console/logging/process/window utilities, Windows-rs feature conventions,
  and a CLI scaffold. Its current example CLI uses Clap; this new CLI should
  use the SFM repository's current Figue/Facet output conventions where they
  improve typed help, `ToArgs`, and machine-readable output.
- `platform/cli/sfm-propagate-changes` demonstrates the current pinned
  TeamDman Facet/Figue stack, `CliOutput`, output formats, install revision
  handling, tests, and offline installation. The new CLI is user/game control,
  while `sfm-propagate-changes` remains repository/build/release tooling.
- `G:\Programming\Repos\teamy-terminal\crates\teamy-terminal-control`
  demonstrates a local Vox control plane with per-process instance JSON,
  process-id lookup, generated service dispatch, bounded channels, and
  application-thread execution. Its MVP registry does not track focus or ping
  every descriptor, so SFM must not copy its stale/PID-only assumptions.
- Minecraft 1.19.2's `Window` installs GLFW's focus callback and calls
  `Minecraft.setWindowActive`; `Minecraft.isWindowActive()` exposes the state.
  A client tick/event adapter can record only false-to-true focus transitions
  without replacing Mojang's GLFW callback.
- Java 17 exposes `ProcessHandle.current().pid()` for the game descriptor.
- `vox-java` is already part of the SFM terminal bridge/toolchain. The new
  SFM-specific service requires a reviewed shared spec and generated Java/Rust
  bindings; generated Java needed by Gradle contributors must be checked in or
  supplied through the ordinary locked dependency, not generated by Cargo at
  IntelliJ sync time.
- The pinned `vox-java-0.10.0-rc.5` artifact already exposes
  `VoxConnection.accept(Socket, ServiceRegistry, ConnectionOptions)`, and the
  Facet Java runtime tests prove a Java `ServerSocket` can accept a connection,
  register a generated dispatcher, and serve typed Vox calls. SFM does not yet
  host such a service. The initial bridge therefore needs application wiring,
  not a long-lived Rust broker or a new Java RPC runtime.

## Confirmed design constraints

1. `sfm.exe` and `sfm-propagate-changes.exe` remain distinct products and
   binaries. The former controls live games; the latter controls repositories,
   builds, tests, dependencies, and releases.
2. The game is the local Vox server and authority. `sfm.exe` discovers and
   invokes it. Teamy Terminal is merely one shell from which the CLI may run.
3. Every game instance has an opaque random/start-scoped `instance_id`, PID,
   process-start identity/nonce, protocol version, random per-launch
   authentication token, and unique loopback-TCP endpoint. The game binds an
   ephemeral `127.0.0.1` port before publishing its descriptor.
   PID is a convenient selector, not sufficient identity for trusting a stale
   descriptor after PID reuse.
4. Discovery is per-user and local-only. Descriptor publication is atomic;
   normal shutdown removes the owned record; listing pings records with bounded
   concurrency/timeouts and reports or safely cleans proven stale entries.
5. No-selector mutation targets the uniquely most-recently-focused live
   compatible game according to a freshly queried snapshot. If recency is
   absent/tied/ambiguous, the command fails and prints exact PID-qualified
   alternatives rather than choosing by PID, title, directory, or enumeration
   order.
6. `--instance-pid` must match one live endpoint whose self-description has
   that PID and instance identity. Failure never falls back to the default.
7. Focus recency updates only on real focus-gained transitions. Heartbeats,
   terminal/panel focus, mouse movement, requests, and rendering do not make an
   instance “most recently focused.”
8. Selection is a request-time snapshot. A later focus change does not retarget
   a command already dispatched; responses name the exact instance that
   executed it.
9. Game mutations enter a bounded queue and run on the Minecraft client thread.
   Vox threads never mutate screen/workspace/world state directly.
10. `workspace add` resolves the CLI argument to a normalized absolute
    directory before sending. The game revalidates directory/root policy and
    persists through the same semantic workspace operation as native picker,
    typed in-game action, and folder drop.
11. The CLI never edits the game's workspace file directly. Success is returned
    only after the game applies and durably persists the new root, or reports
    an idempotent already-present outcome.
12. Operations/results and CLI output are typed, versioned Facet values. Figue
    owns grammar and `ToArgs`; logs stay on stderr; text/JSON/CSV rendering is
    centralized where the shape supports it.
13. Protocol capabilities are negotiated. An incompatible most-recent game is
    reported explicitly; a mutating command does not silently target an older
    compatible game instead.
14. The first service exposes typed instance operations plus one bounded
   `invoke_client_action` operation. Invocation accepts only the existing
   registered SFM client-action Brigadier grammar, executes through
   `SFMClientActionExecutor`, and returns parse/availability/feedback/result
   data. It does not expose Java reflection, arbitrary file reads, OS or
   Minecraft commands, screen coordinates, chat injection, or an unregistered
   “execute any string as trusted” RPC.
15. Protocol generation must preserve the normal Gradle/IntelliJ contributor
    path without requiring Cargo or the new CLI. Rust regeneration and parity
    checks belong to the SFM toolchain/check scripts, not Java compilation.

## Reversible working assumptions

- Use `%LOCALAPPDATA%\teamdman\sfm\instances` as the per-user discovery
  directory. Each game binds an ephemeral `127.0.0.1` TCP port, starts its Java
  Vox accept loop, then atomically publishes a bounded versioned descriptor
  containing transport, host, port, instance identity, PID/start nonce,
  protocol version, and a random per-launch authentication token. Keep
  discovery/transport construction behind adapters so a future Java named-pipe
  transport can replace TCP without changing the service contract.
- Publish only static bootstrap metadata in the descriptor. `sfm instance
  list` connects and calls `describe` for current focus time, compatibility,
  game/world metadata, and capabilities, avoiding a disk write on every focus
  or heartbeat transition.
- Represent focus recency as UTC with sub-millisecond precision where the
  platform supplies it. Exact ties are ambiguity, not a deterministic
  tie-break to a mutating target.
- Support a global/trailing `--instance-pid <u32>` on commands that target a
  game. Also model opaque `--instance-id <id>` internally and consider exposing
  it in the same phase because it survives PID-reuse ambiguity more safely.
- `sfm workspace add` defaults its path argument to `.` only if Figue help and
  `ToArgs` make that behavior obvious. The exact user example with explicit
  `.` remains a required fixture regardless.
- Adding a repository root stores that root as itself. Project/source-set
  classification is derived asynchronously and displayed as metadata; users do
  not assign an `sfm_source` role.

## Design gates

| Gate | Decision required | Working recommendation | Acceptance consequence |
| --- | --- | --- | --- |
| IC-D1 Protocol ownership/codegen | Where does the SFM-specific Vox service spec live and how are Rust/Java bindings kept equal? | Keep the authoritative spec in the SFM repository beside `platform/cli/sfm`, generate Rust client/server descriptors and Java service bindings with the pinned Vox generator, check Java output in or package it through the locked Vox Java artifact, and add a schema/hash parity check. Do not require Cargo during Gradle sync. | I-1/I-2 cannot integrate until a tiny ping/describe fixture compiles on both sides through the normal Rust and Java checks. |
| IC-D2 Discovery and initial transport | Where are records published, and how does Java accept the short-lived CLI? | Use per-user app data for predictable ownership/inspection and direct loopback TCP because the pinned Vox Java runtime already accepts `java.net.Socket`. Treat records as ephemeral leases; bind before atomic publication; authenticate every connection with the descriptor's random launch token; and prove stale cleanup, PID/port reuse rejection, and loopback-only binding. A long-lived Rust broker and named-pipe dependency are explicitly excluded from the first slice. | I-2 path/security/cleanup/transport tests and user documentation depend on this decision. |
| IC-D3 Default target ambiguity | What if no game has focus history or two report the same latest time? | If exactly one live instance exists, it may be selected even before a focus transition; with multiple instances, require a unique latest focus time or explicit selector. Ties/unknown fail with `sfm instance list` and PID-qualified examples. | I-3 selection fixtures and live two-instance proof depend on this safety rule. |
| IC-D4 Public stable selector | Is `--instance-pid` enough? | Preserve the requested PID option and add `--instance-id` as the exact identity selector; list prints both. PID remains ergonomic, opaque id handles PID reuse and diagnostics. | I-3 grammar/help/output either includes both or records explicit deferral of `--instance-id`. |
| IC-D5 First vertical operation | Should instance discovery land separately from workspace mutation? | Yes. First goal ends with scaffold + game ping/describe + `instance list` + safe selection. The next goal joins the workspace model and `workspace add`. | This keeps protocol/discovery uncertainty out of workspace persistence and produces an independently testable foundation. |

## Target command and output contract

Required first command surface:

```text
sfm instance list
sfm invoke <registered-sfm-client-action> [--instance-pid <pid>] [--instance-id <id>]
sfm workspace add <path> [--instance-pid <pid>] [--instance-id <id>]
```

The exact first-goal user fixtures are:

```powershell
sfm instance list
sfm invoke sfm:panel/open sfm:size_display
```

The exact later workspace fixtures are:

```powershell
sfm workspace add .
sfm workspace add . --instance-pid 1234
```

`sfm instance list` is read-only. Text output marks the unique default target,
if any, and includes PID, instance id, SFM/Minecraft version, game directory or
privacy-safe label, focus recency, compatibility, endpoint health, and
capabilities. JSON output returns the same typed fields plus selection reason.
Stale/unreachable descriptors are distinguishable from live incompatible games.

`sfm invoke` captures the remaining OS arguments as action tokens, serializes
them canonically without losing spaces/quotes, and sends a versioned typed
request. Java reconstructs only the `sfm action invoke ...` client-action
surface and executes it through the existing registry/Brigadier seam on the
Minecraft client thread using the screen current at execution time as the
action context. The response names the exact instance and canonical action,
and reports parse position/errors, unavailable reason, action result code,
bounded textual feedback, and resulting screen/workspace identity where
available. Unknown, incomplete, unavailable, timed-out, cancelled, or
overloaded invocations are typed failures and never fall through to chat,
Minecraft commands, reflection, or shell execution.

`sfm workspace add` returns a typed outcome containing the exact executing
instance identity, requested/canonical path, root id, added/already-present
disposition, workspace generation, persistence state, project classification
if currently known, and a user-facing diagnostic/recovery command on failure.
The CLI working directory matters only when resolving the path before the call.

Future namespaces such as `manager`, `inventory`, `panel`, `action`, and
`screen` reuse instance selection, capability negotiation, output, and local
transport. Their operation-specific contracts remain separate future plan
items; no placeholder command pretends they already work.

## Instance discovery and focus contract

On client startup the mod creates a random start-scoped instance id, launch
nonce, and authentication token; binds an ephemeral loopback TCP listener;
starts the Java SFM Vox control service; then atomically publishes a versioned
descriptor containing endpoint bootstrap data. The endpoint's
`describe` method returns its own identity, PID/start nonce, protocol and
capabilities, SFM/Minecraft version, lifecycle state, focus state, last
focus-gained UTC time, and bounded display metadata. The client tick adapter
compares `Minecraft.isWindowActive()` with the prior value and updates recency
only on false-to-true.

Listing snapshots descriptor files, validates paths/schemas/bounds, probes
endpoints concurrently under a small limit and timeout, authenticates with the
launch token, and sorts live results by focus recency then display-only stable
identity. Default selection does not
use that display tie-break: it requires the IC-D3 unique winner. A successful
connection rechecks descriptor identity so a stale record cannot redirect a
request to a reused PID or TCP port. Normal shutdown removes only the record
whose instance id/endpoint it owns. Crash leftovers are expected and are only
removed after bounded probes prove them stale.

The game service hands each typed operation to a bounded client-thread queue and
awaits a typed response. Overload, shutdown, timeout, unsupported capability,
and screen/world unavailability are ordinary outcomes. IPC workers do not hold
workspace/world locks while waiting for the client thread and cancellation does
not leave a later mutation detached from its original response semantics.

## Execution order and parallel topology

```text
I-1 CLI/protocol scaffold ----------------------+--> I-3 instance list/selection --> I-5 live join
                                                |
I-2 game discovery/focus/service ---------------+
                                                |
Registered client-action adapter ---------------+--> I-3a visible invoke witness

Contextual explorer A-1/A-2a/C-1 workspace model
                              + I-1/I-2/I-3 --> I-4 workspace add --> I-5 live join
```

Safe parallel lanes after IC-D1 freezes the smallest ping/describe schema:

- **Rust CLI lane:** crate/install, Figue grammar, CliOutput, descriptor reader,
  Vox client, selection model, and fake-endpoint tests.
- **Java service lane:** process/start identity, focus transition tracker,
  descriptor publisher, bounded client-thread dispatcher, describe/ping, and
  lifecycle tests.
- **Protocol/codegen lane:** authoritative spec, generated Rust/Java bindings,
  schema fingerprint, deterministic regeneration, and Gradle-without-Rust proof;
  one owner integrates generated files.
- **Workspace lane:** I-4 adapter begins only after C-1 exposes the semantic
  add-root repository operation; it must not invent temporary persistence.
- **Integration lane:** one coordinator owns dependency pins, central Java
  registration/lifecycle, install scripts, plans/changelog, and live puppets.

## Phase I — Dedicated control CLI and live-game bridge

### [x] I-1 Scaffold `platform/cli/sfm` and freeze the control protocol seam

**Work:** Close IC-D1. Create the independent Rust crate, install/check scripts,
embedded Windows metadata/icon where appropriate, current Facet/Figue global
args and `CliOutput`, typed error/exit conventions, and `instance`/`workspace`
command grammar fixtures. Add the smallest versioned SFM control spec with
`ping`, `describe`, capability values, and a future-extensible typed operation
envelope without exposing arbitrary execution. Generate and parity-check Rust
and Java bindings while keeping the ordinary Java contributor path Cargo-free.

**Validation:** Figue parse/help/`ToArgs` and output-format tests cover all exact
required command strings, selectors before/after subcommands as supported,
Unicode/Windows paths, malformed PID/id, and no accidental
`sfm-propagate-changes` command bleed. Protocol regeneration is deterministic;
schema fingerprints and a memory-link ping/describe round trip agree.

```pwsh
cargo test --all-features
.\check-all.ps1
.\install.ps1
sfm help list --short
```

**Completion criteria:** `sfm.exe` installs independently, exact help/grammar is
typed and stable, both languages compile the same minimal service contract, and
Gradle/IntelliJ Java work does not run Cargo or code generation.

**Completion evidence (2026-08-11):** Added the independent crate, Figue
grammar, Facet text/JSON/CSV output, exact protocol DTOs/service, deterministic
Java generation, `install.ps1`, and `check-all.ps1`. The generated Java package
is rewritten deterministically to
`ca.teamdman.sfm.client.control.generated`, including its package-private
primitive adapter, because the generator's upstream default package would
create a Java module split-package with the jar-in-jar Vox runtime. The normal
Minecraft compile consumes checked-in Java and never invokes Cargo. The full
Rust check script passed (format, clippy with warnings denied, ten tests, and
generated-Java check), and `install.ps1` installed `sfm.exe` independently.
The complete 1.19.2 Java suite passed through
`sfm-propagate-changes.exe test run --branch 1.19.2 --wait-for-build-lock`.

### [x] I-2 Host discoverable per-game control services and track real focus recency

**Work:** Close IC-D2. Add client-only instance lifecycle, random start id/PID
identity/launch nonce/token, an ephemeral loopback-only `ServerSocket`, a Java
Vox accept loop with the generated SFM control dispatcher, atomic descriptor
lease, focus-gained tracker based on `Minecraft.isWindowActive()`, authenticated
`ping`/`describe`, bounded client-thread dispatch, and exact-owned cleanup. Add
version adapters where later Minecraft focus/lifecycle APIs differ. Do not
attach this to terminal connection state, require a terminal server, or add a
long-lived Rust relay.

**Validation:** Java/pure integration tests cover startup, descriptor atomicity,
false/true focus transitions, repeated focused ticks, blur, headless/hidden
client state, identity/PID/start nonce, endpoint uniqueness for two fixtures,
bounded queue/timeout/cancel/shutdown, normal cleanup, simulated crash residue,
and no server-side/classloading registration. Rust memory/local transport tests
prove ping/describe and schema mismatch behavior.

```pwsh
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMControlInstanceTests --wait-for-build-lock
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMControlServiceTests --wait-for-build-lock
```

**Completion criteria:** Every live client independently advertises and serves
truthful identity/focus/capability state over direct loopback TCP, mutations can
be handed safely to the Minecraft thread, stale records cannot impersonate PID
or port reuse, and no Teamy Terminal process or long-lived Rust broker is
required.

**Completion evidence (2026-08-11):** Added a client-only loopback
`ServerSocket`, generated Java Vox dispatcher, random instance id/start nonce/
authentication token, bounded connection and client-thread executors, atomic
per-user descriptor publication, authenticated ping/describe, real
false-to-true window-focus tracking, capability/lifecycle metadata, and
owned-descriptor shutdown cleanup. The live witness advertised PID `36324`,
Minecraft `1.19.2`, SFM `4.34.0`, title-screen state, and both capabilities;
after the puppet exited, `sfm instance list --output-format json` returned zero
instances, proving normal cleanup. No terminal server or Rust relay was
started. Canonical `run compile` and focused Java tests passed.

### [x] I-3 Implement `sfm instance list` and safe explicit/default selection

**Work:** Close IC-D3/IC-D4. Enumerate bounded descriptor records, probe them
concurrently, validate self-identity, classify live compatible/incompatible/
stale/malformed outcomes, and render typed list output. Implement one shared
target selector for future commands: exact instance id, exact PID, or unique
most-recent focus snapshot. Emit exact Figue-rendered retry examples on
ambiguity/no instance/incompatibility. Never fall back from an explicit selector
or from the most-recent incompatible game to an older game.

**Validation:** Scenario tests cover zero/one/many games; no focus history;
unique/tied recency; currently blurred latest game; stale descriptor; PID reuse;
duplicate/malformed records; endpoint identity mismatch; timeout; protocol and
capability mismatch; explicit PID/id success/failure; focus race after
selection; deterministic display ordering versus non-deterministic mutation
ambiguity; text/JSON/CSV output; and exact requested command spellings.

**Completion criteria:** `sfm instance list` makes the selection decision
inspectable, and every future mutation can identify one exact live game or fail
safely with actionable commands.

**Completion evidence (2026-08-11):** Descriptor reading is size/count bounded;
each file is read and probed in a bounded concurrent task; live responses must
match instance id, PID, and start nonce before use. Output classifies live,
incompatible, stale, unreachable, and malformed records, explains selection,
and marks the selected default. A dead-PID record is removed only after a
second liveness check and an unchanged descriptor reread. Shared selection
supports exact PID, exact opaque id, the sole live
instance before any focus transition, or a unique latest focus timestamp;
explicit misses and focus ties fail without fallback. Unit tests cover exact
selector non-fallback, latest-focus ties, stale cleanup, and refusal to skip a
newer incompatible game. The live JSON list marked exactly
one instance as live/default, and the mutating call repeated the identity ping
on a fresh connection before dispatch.

### [x] I-3a Prove registered action invocation by visibly opening the size-display panel

**Work:** Add the Figue grammar and typed Vox request/result for
`sfm invoke <action...>` with the shared I-3 selectors. Canonicalize the
remaining CLI tokens, authenticate and select the target, enqueue the request
onto the Minecraft client thread, capture the current screen into a valid
`SFMClientActionContext`, and invoke only `SFMClientActionExecutor` with the
`sfm action invoke ` prefix. Return bounded structured parse, availability,
feedback, result-code, and resulting-screen evidence. Use the existing
registered scene id `sfm:size_display`; do not invent `sfm:size_test`.

**Validation:** Rust grammar/`ToArgs`/output tests cover the exact command,
quoted action arguments, selector placement, malformed/incomplete actions, and
token bounds. Java tests prove execution occurs on the Minecraft client thread,
opens `sfm:size_display` from a title/non-workspace screen, opens into an
existing workspace when appropriate, executes exactly once, returns feedback,
and rejects unavailable/unknown actions without mutation. A puppet or live
witness launches a game, invokes the command from an external shell, captures
the visible size-display panel and terminal/CLI artifact, and correlates the
instance id, PID, request id, action, result, and resulting workspace.

```powershell
sfm instance list
sfm invoke sfm:panel/open sfm:size_display
```

**Completion criteria:** The exact external command selects one verified game,
returns a successful typed result, and visibly opens the registered size-display
panel. No terminal server, Rust broker, chat injection, coordinate click, or
direct screen mutation outside the existing action executor participates.

**Completion evidence (2026-08-11):** The installed external CLI executed
`sfm invoke sfm:panel/open sfm:size_display --output-format json`. The typed
response correlated instance `5525e708-9aee-404c-86ab-a018252ce756`, PID
`36324`, request id, canonical action
`sfm action invoke sfm:panel/open sfm:size_display`, result code `1`, resulting
`SFMScreenMultiplexer`, and one workspace panel. The dedicated
`sfm:title_screen_external_cli_size_display` puppet launches that independent
process itself with Java `ProcessBuilder`, polls it without blocking the
Minecraft client thread, validates exit code and resulting panel, and requires
no operator or Teamy Terminal companion action. It asserted one
`SFMSizeDisplayPanel` and captured
`runs/sfm-title_screen-20260812-003711-522/title_screen_external_cli_size_display/1280x720_auto/figure_01_external-cli-size-display.png`.
The corresponding launcher console records child PID `58124`, the exact six
arguments, successful `sfm.invoke/1` JSON, canonical registered action, and
`workspace_panel_count: 1` before capture.
The first live attempt also caught and fixed generic Brigadier token escaping:
literal action ids containing `:` and `/` stay raw, while whitespace/quotes are
escaped, with focused Java regression tests.

### [ ] I-4 Deliver `sfm workspace add` through the authoritative workspace model

**Work:** Begin only after contextual-plan C-1 exposes the shared workspace
repository/add-root operation. Add the typed Vox request/result and capability,
Figue path argument/default, canonical CLI path resolution, explicit/default
instance targeting, Java client-thread adapter, idempotency, persistence, and
workspace panel/model notification. Remove the proposed public
`root/role/set ... sfm_source` action and model project/source classification as
derived metadata. UI native picker, in-game typed action, folder drop, and CLI
all invoke one semantic add-root executor.

**Validation:** Cover relative/absolute/dot/Unicode/space/UNC/drive paths,
nonexistent/file/not-directory, canonical duplicate, symlink policy, game-side
rejection after CLI resolution, selected instance mismatch, add/already-present,
persistence failure rollback, restart, two open workspace panels, project
classification, no manual source role, output schemas, and no direct CLI write
to game config. Exact fixtures include both required command strings.

```pwsh
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMControlWorkspaceTests --wait-for-build-lock
sfm workspace add .
sfm workspace add . --instance-pid 1234
```

**Completion criteria:** A shell in any directory can add that directory to one
exact game through its authoritative persisted workspace operation, with safe
multi-instance selection and immediate explorer-model visibility.

### [ ] I-5 Prove two-game targeting from Teamy Terminal and close the foundation

**Work:** Add deterministic harness support for two independently registered
game-client service fixtures and, where practical, two live clients. Prove
`instance list`, focus A then B, unqualified add targets B, PID-qualified add
targets A, ambiguity/incompatibility fails safely, roots persist, and a command
typed inside the in-game Teamy Terminal reaches the game without going through
the terminal rendering server. Capture typed instance/workspace artifacts and
screenshots; update docs/changelog and linked-plan completion notes.

**Validation:** Run new CLI checks, focused Java tests, canonical compile/full
suite, deterministic multi-instance integration, and a live/manual focus
witness through the SFM CLI. Do not propagate or publish in this phase.

**Completion criteria:** Machine evidence identifies every process/instance/
focus/selection/request/root generation in the journey; the visual witness
shows the resulting workspace; stale/wrong-instance cases are proven; all
ICLI guidance has evidence; and the foundation is ready for typed manager and
inventory operations without redesigning discovery or dispatch.

## Next recommended vertical slices

The first goal completed **I-1, I-2, I-3, and I-3a** on 2026-08-11. It ended with an
installed `sfm.exe`, a game-hosted local control service, truthful focus-aware
discovery, safe `sfm instance list`/target selection, and the visible
`sfm invoke sfm:panel/open sfm:size_display` journey. It deliberately stops
before workspace-root mutation so the control plane can be verified
independently of the still-unimplemented C-1 workspace repository.

After IC-D1 through IC-D4 are accepted or amended, the exact goal is:

> Complete I-1, I-2, I-3, and I-3a in
> `docs/tasks/sfm in-game control cli plan.md`,
> including protocol/codegen parity, independent CLI install/help, game
> loopback-TCP listener and authenticated ping/describe lifecycle, atomic
> per-user instance descriptors, focus-recency tracking, stale/PID/port-reuse
> handling, exact PID/id/default selection, bounded registered client-action
> invocation, and a visible successful
> `sfm invoke sfm:panel/open sfm:size_display` witness, plus focused tests,
> canonical compile/full-suite evidence, and completion notes; do not implement
> workspace-root mutation, add a long-lived Rust broker, propagate, publish, or
> begin manager/inventory commands.

The next goal joins **contextual-plan A-1/A-2a/C-1 plus I-4/I-5** and ends with
the exact `sfm workspace add .` journey against one and two game instances.
Native folder selection and addressed file opening remain C-2/C-3 work but use
the same workspace repository.

## Overall completion criteria

- [x] `platform/cli/sfm` builds, tests, installs, and has stable typed help and
  machine-readable output independently of `sfm-propagate-changes`.
- [x] Every live client publishes a unique, authenticated-by-handshake local
  endpoint and truthful focus/lifecycle/capability state.
- [x] Instance listing distinguishes live compatible, live incompatible,
  stale, malformed, and unreachable descriptors and explains default selection.
- [x] Explicit PID/id selection never falls back; default selection is uniquely
  most-recent-focus or a visible ambiguity.
- [x] `sfm invoke sfm:panel/open sfm:size_display` executes only through the
  registered Java client-action dispatcher on the Minecraft thread and visibly
  opens the size-display panel in the selected game.
- [ ] `workspace add` changes only the exact selected game's authoritative
  persisted workspace and converges with UI/drop/in-game action semantics.
- [ ] The public surface contains no manual `sfm_source` role-assignment step.
- [ ] Teamy Terminal can invoke the CLI but is not an IPC dependency or target
  selector.
- [ ] The protocol can add typed manager/inventory operations without breaking
  instance discovery, output, client-thread dispatch, or capability negotiation.
- [ ] Focused/full tests, protocol parity, live artifacts, docs/changelog, and
  plan completion notes agree before propagation or release.

## Risk register

| Risk | Guardrail |
| --- | --- |
| A stale descriptor targets a new process that reused a PID | Opaque start-scoped instance id/nonce, unique endpoint, live describe handshake, explicit identity match, and PID-reuse fixtures |
| `sfm workspace add .` mutates the wrong of several games | Fresh concurrent describe snapshot, unique latest-focus rule, visible default marker, ambiguity failure, exact PID/id options, and response identity |
| Clock ties/skew make recency unsafe | Multiple-game ties/unknown fail; no PID/title/enumeration tie-break for mutation; explicit selector recommendation |
| Polling focus changes recency repeatedly while a window stays focused | Record only false-to-true `Minecraft.isWindowActive()` transitions and test repeated ticks |
| CLI writes config while game owns stale in-memory state | Game-hosted semantic operation and persistence; CLI never edits workspace files |
| Vox worker mutates Minecraft from the wrong thread | Bounded request queue/client-executor handoff, timeout/overload/shutdown outcomes, and thread assertions |
| Terminal bridge availability becomes required for all CLI control | Separate per-game local service; live proof works from external shell and Teamy Terminal with terminal server identity absent from selection |
| Protocol generation breaks Gradle-only contributors | Checked/generated Java or locked Java artifact, deterministic parity check in Rust tooling, and no Cargo invocation during Gradle/IDE sync |
| Registered action invocation accidentally becomes arbitrary remote execution | Accept only the existing SFM client-action Brigadier tree through `SFMClientActionExecutor`; canonical token and size bounds; typed availability/parse failures; no chat, Minecraft command, reflection, filesystem, shell, or coordinate fallback |
| Absolute workspace paths leak in normal logs/list output | Human output may show user-requested paths only for the operation; default discovery telemetry uses ids/hashes/labels and structured output documents sensitive fields |
| Incompatible focused game silently redirects command to older game | Select newest live instance first and fail capability/protocol negotiation; never fallback to another instance |
| Future manager/inventory commands force discovery redesign | Shared typed describe/capabilities/selection/dispatch/output foundation plus operation-specific request/result types |

## Source and implementation references

- `docs/AGENTS.md`
- `docs/tasks/contextual input actions and addressable explorer plan.md`
- `docs/tasks/cli ast refactoring suite plan.md`
- `platform/cli/sfm-propagate-changes/Cargo.toml`
- `platform/cli/sfm-propagate-changes/src/cli/`
- `platform/minecraft/src/main/java/ca/teamdman/sfm/client/terminal/SFMVoxTerminalService.java`
- `platform/minecraft/src/main/java/ca/teamdman/sfm/client/screen/workspace/`
- `platform/minecraft/build/sfm-toolchain/forge/1.19.2/sources/combined-deobfuscated.filetree/com/mojang/blaze3d/platform/Window.java`
- `platform/minecraft/build/sfm-toolchain/forge/1.19.2/sources/combined-deobfuscated.filetree/net/minecraft/client/Minecraft.java`
- `G:\Programming\Repos\teamy-rust-windows-utils\Cargo.toml`
- `G:\Programming\Repos\teamy-rust-windows-utils\src\cli\`
- `G:\Programming\Repos\teamy-rust-windows-utils\src\window\`
- `G:\Programming\Repos\teamy-terminal\crates\teamy-terminal-control\src\lib.rs`
- `G:\Programming\Repos\facet\vox\spec\spec-proto\src\terminal.rs`
- `G:\Programming\Repos\facet\vox\java\runtime\src\main\java\org\facet\vox\VoxConnection.java`
- `G:\Programming\Repos\facet\vox\java\runtime\src\test\java\org\facet\vox\VoxRuntimeTest.java`
- `G:\Programming\Repos\facet\vox\java\generated\src\main\java\org\facet\vox\generated\`
