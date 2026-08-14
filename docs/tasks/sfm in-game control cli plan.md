# SFM in-game control CLI plan

**Plan status:** Active; I-1 through I-4 complete, I-5 retained as an independent later multi-game/Teamy-Terminal hardening slice
**Primary implementation root:** `D:\Repos\Minecraft\SFM\repos2\1.19.2`
**New Rust CLI root:** `platform/cli/sfm`
**Linked selection/explorer plan:** `docs/tasks/typed selections relations and lazy explorers plan.md`
**Linked contextual UI plan:** `docs/tasks/contextual input actions and addressable explorer plan.md`
**Reference template:** `G:\Programming\Repos\teamy-rust-windows-utils`
**Last updated:** 2026-08-13
**Foundation implementation commit:** `8e202d915` (`Add live game control CLI`)
**Intent audit:** Passed 2026-08-13 including the cross-plan trajectory reconciliation recorded below

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
shell, including the Teamy Terminal running inside the game. The first proposed
post-discovery journey was:

```powershell
cd D:\Repos\Minecraft\SFM\repos2\1.19.2
sfm instance list
sfm workspace add .
```

That spelling and global persisted-workspace model were superseded on
2026-08-12. The active journey is a direct typed game operation with an
explicit explorer selector and empty-target policy:

```powershell
sfm explorer root add focused . --if-no-match open-new
```

The CLI resolves `.` to a strict UTF-8 canonical file path, selects one exact
game through the existing safe instance logic, and sends a typed explorer
request. The game revalidates the path, resolves the explicit set-valued
explorer selector, and mutates the selected explorer session(s) through the
shared action/client-thread path. It does not edit a workspace file or invent a
global catalog. Exact missing explorer ids fail; an explicitly non-exact
request may open one generic explorer only when `open-new` was requested.

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
| ICLI-4 | Historical proposal: `sfm workspace add .` adds the caller's current directory to a global explorer workspace. | Preserve as provenance only. I-4 now targets generic explorer sessions through direct typed commands and introduces no global persistence. | ICLI-14 through ICLI-17 |
| ICLI-5 | Multiple Minecraft instances must be supported carefully. | I-2 gives every process a unique instance id/local endpoint and published descriptor; I-3 never assumes only one game exists. | — |
| ICLI-6 | `sfm instance list` enumerates open game instances. | I-3 emits a typed list containing identity, PID, compatibility, focus recency, lifecycle metadata, endpoint health, and which instance would be selected by default. | — |
| ICLI-7 | Each game tracks when it was focused; an unqualified command targets the most recently focused instance. | I-2 records focus-gained transitions from Minecraft's real window-active state; I-3 snapshots all live descriptors and chooses the uniquely newest compatible instance with explicit ambiguity/error behavior. | — |
| ICLI-8 | Historical exact-game example: `sfm workspace add . --instance-pid 1234`. | Preserve exact-game targeting semantics, but use the active `sfm explorer root add ... --instance-pid 1234` grammar. | ICLI-14 through ICLI-17 |
| ICLI-9 | There is currently no CLI control surface for in-game SFM behavior. | The plan adds a game-hosted SFM control service instead of extending the terminal-rendering service or pretending the existing Java terminal client is a server. | — |
| ICLI-10 | Future commands may include `sfm manager list`, `sfm inventory show`, and other in-game inspection/control. | The protocol and dispatch use versioned typed operations/results, capability discovery, client-thread handoff, and bounded structured output; no workspace-specific wire shortcut becomes the whole architecture. | — |
| ICLI-11 | Users should not need to assign an internal `sfm_source` role when adding an explorer root. | No public role-setting action or `sfm_source` scene/device exists. A typed path is added as itself; project/source discovery is derived metadata. A development launcher may contribute its exact source root only as an ordinary resolver-authorized path expression. | — |
| ICLI-12 | Preserve the proposal in a resumable, verifiable plan before implementation. | This ledger, contracts, gates, phases I-1 through I-5, topology, risks, and exact next-goal statement are authoritative. | — |
| ICLI-13 | The first goal must end with a concrete in-game operation, specifically opening the panel that displays its allocated size, rather than only `sfm instance list`. | I-3a adds the exact `sfm invoke sfm:panel/open sfm:size_display` command, registered-action-only Java dispatch, structured feedback, and a visible witness. The active goal is not complete until the panel is visibly opened through the external CLI. | — |
| ICLI-14 | Canonical SFM commands are explicit, hierarchical, long-form commands; short aliases belong in the user's shell profile. Ordinary running-game operations should not sit beneath `sfm invoke`. | I-4 adds direct `sfm explorer ...` Figue commands, keeps generic `invoke` as a diagnostic escape hatch, and asserts no built-in explorer aliases. | — |
| ICLI-15 | Every explorer operation carries an explicit set-valued explorer selector such as `focused`, exact id, or `all`; no action silently depends on ambient focus or first-matches. | I-4 transports the typed selector AST and returns captured target ids/per-target outcomes. | — |
| ICLI-16 | A non-exact request may explicitly open a generic explorer when none matches, while a missing exact explorer id must fail without replacement. Explorer locations are panel-local/session-local rather than one persisted workspace. | I-4 uses `--if-no-match fail|open-new`, rejects exact-id plus open-new, and introduces no workspace persistence schema. | — |
| ICLI-17 | Native filesystem paths are strict UTF-8 typed content paths, not Minecraft resource locations; explorer roots may be heterogeneous and lazily resolved. | The shared selection/explorer X-1 through X-7 foundation owns path/expression/relation semantics; I-4 reuses its generated DTOs and game-side authority checks. | — |

## Guidance traceability

| Guidance | Plan coverage | Evidence when complete |
| --- | --- | --- |
| ICLI-1, ICLI-2 | I-1 | Crate/install/help/output tests and template/current-SFM dependency decisions |
| ICLI-3 | I-1, I-3, I-5 | Console-safe CLI plus a live command entered in Teamy Terminal |
| ICLI-4 | Superseded by ICLI-14 through ICLI-17 | Historical command remains documented; no implementation evidence is required for the rejected global workspace model |
| ICLI-5, ICLI-6, ICLI-7, ICLI-8 | I-2, I-3, I-5 | Two-game discovery fixtures/live proof, focus-recency ordering, exact PID targeting using active explorer grammar, stale/PID-reuse rejection, and default marker |
| ICLI-9, ICLI-10 | Protocol/control contracts; I-2; I-4 | Game-hosted Vox service, capabilities/typed dispatch, explorer operation, and future-operation fixtures |
| ICLI-11 | Selection/explorer foundation; I-4 | Public help/action absence proof plus derived classification with no manual role assignment |
| ICLI-12 | Entire plan | Three-pass audit and fresh-agent resumption review |
| ICLI-13 | I-1, I-2, I-3, I-3a | Exact CLI grammar/round trip, Minecraft-thread assertion, registered action execution result, size-display panel state, and screenshot/puppet witness |
| ICLI-14, ICLI-15, ICLI-16, ICLI-17 | Selection/explorer X-1 through X-7; I-4/I-5 | Direct help/`ToArgs`, selector/path round trips, exact/focused/all/empty-target tests, heterogeneous lazy explorer artifacts, and absence of built-in aliases/global persistence |

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

### Intent-audit extension — 2026-08-12 explorer-control supersession

- **Pass 1 — extraction:** Reread the complete canonical-command,
  explorer-target, heterogeneous-root, UTF-8 path, selection, relation, and
  open-if-none discussion. Added ICLI-14 through ICLI-17 and marked the exact
  older `workspace add` requirements as superseded rather than deleting them.
- **Pass 2 — traceability:** Mapped every new control requirement to the shared
  X-1 through X-7 foundation and I-4/I-5 direct CLI integration. Verified that
  game discovery/authentication/thread handoff remain established rather than
  being replanned.
- **Pass 3 — adversarial omission:** Checked that the revised plan does not hide
  ordinary operations under `invoke`, add short aliases, implicitly target a
  focused explorer, first-match a set selector, create a replacement for a
  missing exact id, coerce Windows paths to resource locations, or reintroduce
  a global persisted workspace.
- **Known source limitation:** None for the supersession; the original messages
  were available in the active conversation.

### Intent-audit extension — 2026-08-13 checkpoint and trajectory reconciliation

- **Pass 1 — extraction:** Rechecked that ordinary game commands remain direct
  top-level typed commands, explorer targeting remains explicit and set-valued,
  Teamy Terminal is a caller rather than an IPC dependency, and multi-instance
  proof is desired but was not ordered ahead of opening real SFM source.
- **Pass 2 — traceability:** Recorded the clean I-4 checkpoint and repeated
  real-CLI matrix evidence. Kept I-5 intact as this plan's next internal item.
  The former cross-plan trajectory to contextual A-2c/C-3 completed in
  `6dc3d2d04`; the active cross-plan goal is now contextual B-2/C-4 plus
  CLI-AST Phase 0.10.
- **Pass 3 — adversarial omission:** Checked that the priority note does not
  mark I-5 complete, weaken wrong-instance/ambiguity safeguards, couple game
  control to terminal rendering, or make two-game proof a hidden prerequisite
  for addressed file opening.
- **Known source limitation:** None. The relevant original messages, linked
  plans, commits, and live artifact were available.

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
   Vox threads never mutate screen/explorer/world state directly.
10. Direct explorer commands carry an explicit game target plus a typed,
    set-valued explorer selector. Native path expressions are normalized by the
    CLI and revalidated by the game; registry/selection schemes remain typed
    expressions rather than being coerced into filesystem paths.
11. The CLI never edits game or explorer state files directly. The game captures
    matching explorers once, stages and preflights the entire operation, and
    publishes an all-or-none logical mutation with explicit per-target results.
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
- Canonical explorer mutations are direct, hierarchical commands such as
  `sfm explorer root add focused . --if-no-match open-new`. The path is
  explicit, the explorer selector is explicit, and Figue `ToArgs` round trips
  the long form. `sfm invoke` remains a diagnostic escape hatch rather than a
  parent for ordinary operations; the executable ships no short aliases.
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
| IC-D5 First vertical operation | Should instance discovery land separately from explorer mutation? | Yes. The completed first goal ended with scaffold + game ping/describe + `instance list` + safe selection. The next goal joins the typed path/selection/relation/lazy-explorer foundation to direct `sfm explorer ...` operations. | This keeps protocol/discovery uncertainty out of explorer semantics and reuses one action model across UI, palette, and CLI. |

## Target command and output contract

Completed foundation command surface:

```text
sfm instance list
sfm invoke <registered-sfm-client-action> [--instance-pid <pid>] [--instance-id <id>]
```

The exact first-goal user fixtures are:

```powershell
sfm instance list
sfm invoke sfm:panel/open sfm:size_display
```

The canonical next explorer fixtures are:

```powershell
sfm explorer list
sfm explorer root add focused . --if-no-match open-new
sfm explorer root add id:explorer-7 registry://minecraft/item/ --if-no-match fail --instance-pid 1234
sfm explorer view set all list
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

Direct `sfm explorer ...` commands carry a typed explorer selector and typed
path expression. The game captures the complete target set once, preflights
and stages every target, then either publishes the logical mutation to all
captured explorers or none. Results include the exact game identity, captured
explorer ids, requested/canonical path expression, per-target disposition,
selection/relation/explorer revisions, and an actionable diagnostic. A missing
exact id fails and never creates a replacement. `--if-no-match open-new` is
valid only for a non-exact selector and creates a generic explorer through the
ordinary panel-open action. The CLI working directory matters only while
canonicalizing a native path; the game revalidates it before mutation.

The direct surface includes `explorer list`, `explorer root list/add/remove`,
`explorer view set`, `explorer sort set`, `explorer group set`, and
`explorer root hoist set`. It introduces neither a global workspace nor an
explorer-specific persistence file. UI controls, palette actions, drag/drop,
and the CLI all converge on the same registered semantic actions.

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
explorer/world locks while waiting for the client thread and cancellation does
not leave a later mutation detached from its original response semantics.

## Execution order and parallel topology

```text
I-1 CLI/protocol scaffold ----------------------+--> I-3 instance list/selection
                                                |
I-2 game discovery/focus/service ---------------+
                                                |
Registered client-action adapter ---------------+--> I-3a visible invoke witness

Selection/explorer X-1..X-5 + I-1/I-2/I-3
                         --> I-4 / X-6 direct explorer CLI --> X-7 one-game live proof

I-4 + proven multi-instance discovery ----------> I-5 two-game/multi-explorer proof
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
- **Explorer-model lane:** X-1 through X-5 own typed paths, selectors,
  selections, relations, lazy resolution, and registered actions. I-4 consumes
  those contracts and must not invent a second DTO or persistence model.
- **Integration lane:** one coordinator owns dependency pins, central Java
  registration/lifecycle, install scripts, plans/changelog, and live puppets.

## Phase I — Dedicated control CLI and live-game bridge

### [x] I-1 Scaffold `platform/cli/sfm` and freeze the control protocol seam

**Work:** Close IC-D1. Create the independent Rust crate, install/check scripts,
embedded Windows metadata/icon where appropriate, current Facet/Figue global
args and `CliOutput`, typed error/exit conventions, and `instance`/`invoke`
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

**Thread-handoff refinement evidence (2026-08-12):** Replaced the original
whole-handler client-thread executor with a bounded control-worker pool and a
narrow `SFMClientThreadGate`. Socket framing, authentication, protocol
validation, `ping`, and `describe` now remain off the Minecraft render thread;
`describe` reads one immutable snapshot captured at client-tick boundaries.
Only the registered action mutation enters `Minecraft.execute`, with a bounded
32-request capacity and typed capacity, client-unavailable, shutdown, and
cancellation failures. Cancelling a Vox request while its action is still
queued prevents the later mutation, each accepted mutation executes at most
once, and result completion normally returns to the control worker instead of
continuing protocol work on the render thread. Closing the service rejects new
work and resolves queued work without leaving detached mutations.

Focused `SFMClientThreadGateTests` cover thread affinity, exactly-once
execution, capacity release, cancellation-before-execution, executor
rejection, and shutdown. The full Java test suite and canonical 1.19.2 compile
passed. The self-orchestrating external-CLI puppet also passed after the
refinement, proving that the worker-to-client-thread gate still opens the real
size-display panel through the registered action path.

```pwsh
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMClientThreadGateTests --wait-for-build-lock --no-capture
sfm-propagate-changes.exe test run --branch 1.19.2 --wait-for-build-lock
sfm-propagate-changes.exe run compile --branch 1.19.2 --wait-for-build-lock
sfm-propagate-changes.exe puppet run sfm:title_screen_external_cli_size_display --branch 1.19.2 --wait-for-build-lock
```

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

**Latest regression witness (2026-08-12):** After moving Vox handlers off the
render thread and narrowing Minecraft access to `SFMClientThreadGate`,
`sfm:title_screen_external_cli_size_display` passed again. Its latest report is
`platform/minecraft/build/sfm-toolchain/artifacts/game-test-preview/runs/`
`sfm-title_screen-20260812-115021-792/index.html`; the captured figure shows the
externally selected game displaying the `sfm:size_display` panel. This closes
the implementation follow-up without changing I-3a's public command contract.

### [x] I-4 Deliver direct typed explorer control through the shared lazy-explorer model

**Work:** Begin after X-1 through X-5 in the typed-selection/lazy-explorer plan
freeze the shared DTOs and registered semantic actions. Implement direct Figue
commands for `sfm explorer list`, `root list/add/remove`, `view set`,
`sort set`, `group set`, and `root hoist set`; do not put them beneath
`invoke` and do not add built-in aliases. Carry explicit set-valued explorer
selectors (`focused`, exact id, `all`) and typed canonical concrete paths—the
`Literal(SFMPath)` subset of the shared path-expression model—across Vox.
Resolve targets once, preflight/stage the full set, and publish all-or-none with
per-target results. A missing exact id fails; an explicitly requested
`--if-no-match open-new` on a non-exact selector opens a normal generic explorer.
Resolve relative native paths in the CLI, reject non-UTF-8 paths cleanly, and
revalidate authority on the Minecraft client thread. Introduce no global
workspace or explorer persistence file. This item is the control-plane portion
of selection/explorer item X-6.

**Validation:** Cover help and Figue `ToArgs`; no alias and no nested-invoke
surface; exact/focused/all/empty selectors; 0/1/many explorers; exact-missing
and explicit-open-new behavior; relative/absolute/dot/Unicode/space/UNC/drive
paths; `file`, `registry`, and `selection` schemes; canonical duplicate;
game-side rejection after CLI resolution; selected-instance mismatch;
all-or-none rollback; per-target output; capability negotiation; and no direct
CLI write to game state. Run focused `SFMControlExplorerTests`, CLI tests, the
canonical Java suite, and compile through `sfm-propagate-changes.exe`.

**Completion criteria:** From a shell in the repository root,
`sfm explorer root add focused . --if-no-match open-new` opens a generic
explorer if needed and visibly adds the canonical repository path to the exact
selected game. A second heterogeneous root can be added to that explorer;
`sfm explorer view set all list` changes every captured explorer; and a missing
exact explorer id fails without creating or mutating anything. Structured
output identifies the game, target set, operation, and resulting revisions.

**Completion evidence (2026-08-12):** X-6/X-7 in the linked typed-selection
plan delivered the direct long-form Figue grammar, bounded Facet/Vox DTOs,
checked-in generated Java, authenticated game selection, client-thread action
dispatch, exact/focused/all targeting, all-or-none preflight and rollback,
explicit open-if-none, per-target revision evidence, and no global persistence
or built-in aliases. `platform/cli/sfm/check-all.ps1` passed format, strict
Clippy, 41/41 tests, and generated-Java parity; the canonical Java suite passed
794/794 and canonical compile passed. The self-orchestrating live run
`sfm-title_screen-20260812-182322-010` used the real external CLI to open one
explorer, add filesystem and item-registry roots, exercise lazy expansion and
atomic refresh, update two explorers with `all`, and prove an exact miss creates
no replacement.

**Clean checkpoint follow-up (2026-08-13):** Commit `7cfd4b138` contains the
completed I-4 control surface together with its shared typed explorer model.
Commit `dbf6bf344` makes the same real external-CLI journey repeat safely in one
client at 3840x2130 Auto and numeric GUI scales 1 through 8. The run
`title_screen_ext-20260813-162233-680` completed `failed=0 total=9`; per-variant
explorer disposal and relative I/O baselines prevent retained process authority
from being mistaken for cross-variant mutation. The 1.19.2 working tree was
clean after both commits.

### [ ] I-5 Prove two-game and multi-explorer targeting from Teamy Terminal

**Work:** Add deterministic harness support for two independently registered
game-client service fixtures and, where practical, two live clients, each with
several explorers. Prove `instance list`, focus A then B, an unqualified direct
explorer operation targets B, PID/id-qualified operation targets A, set-valued
explorer selectors update exactly their captured targets, and ambiguity or
incompatibility fails safely. Type a canonical `sfm explorer ...` command in
the in-game Teamy Terminal and prove that it reaches Java through the separate
game-control service rather than through terminal rendering. Capture typed
instance/explorer/selection/relation artifacts and screenshots; update linked
plans and changelog only for behavior actually delivered.

**Validation:** Run CLI checks, focused Java tests, canonical compile/full
suite, deterministic multi-instance integration, and a live/manual focus
witness. Include stale descriptor, wrong-instance, empty selector, and partial
preflight-failure cases. Do not propagate or publish in this phase.

**Completion criteria:** Machine evidence identifies every process, instance,
focus event, captured explorer target, request, and resulting revision. Visual
evidence shows only the intended game and explorer(s) changing; stale, wrong,
ambiguous, and partially invalid target sets produce no mutation. The shared
control foundation can accept future typed manager/inventory operations without
redesigning discovery or dispatch.

## Next recommended vertical slices

The first goal completed **I-1, I-2, I-3, and I-3a** on 2026-08-11. It ended
with an installed `sfm.exe`, a game-hosted local control service, truthful
focus-aware discovery, safe `sfm instance list`/target selection, and the
visible `sfm invoke sfm:panel/open sfm:size_display` journey. The generic
`invoke` operation remains a diagnostic escape hatch, but the completed slice
deliberately stopped before adding ordinary typed explorer commands.

The second goal completed **X-1 through X-7** in
`typed selections relations and lazy explorers plan.md`, including I-4 as its
control-plane portion, on 2026-08-12. It ended with a self-orchestrating one-game
journey for `sfm explorer root add focused . --if-no-match open-new`, a lazy
heterogeneous explorer, and machine-checkable selection/relation revisions.

I-5 remains the next slice within this control-specific plan: prove two-game
selection and run the canonical explorer CLI from Teamy Terminal without
coupling terminal rendering to game control. It is not the next recommended
cross-plan product goal. Contextual A-2c/C-3 completed in `6dc3d2d04`; the
active cross-plan goal is contextual B-2/C-4 plus CLI-AST Phase 0.10,
producing immutable editor context and a warm supervised
definition-at-location provider before F12 navigation. I-5 can follow
independently when multi-game hardening is more valuable; it is not a
prerequisite for addressed file opening or jump-to-definition work.

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
- [x] Direct `sfm explorer ...` commands change only their captured game and
  explorer target sets and converge with UI/drop/palette action semantics.
- [x] Missing exact explorer ids fail; explicit non-exact `open-new` creates a
  generic explorer through the normal panel-open path.
- [x] Explorer control introduces no global workspace or hidden persistence.
- [x] The public surface contains no manual `sfm_source` role-assignment step.
- [ ] Teamy Terminal can invoke the CLI but is not an IPC dependency or target
  selector.
- [x] The protocol can add typed manager/inventory operations without breaking
  instance discovery, output, client-thread dispatch, or capability negotiation.
- [x] Focused/full tests, protocol parity, live artifacts, docs/changelog, and
  plan completion notes agree before propagation or release.

## Risk register

| Risk | Guardrail |
| --- | --- |
| A stale descriptor targets a new process that reused a PID | Opaque start-scoped instance id/nonce, unique endpoint, live describe handshake, explicit identity match, and PID-reuse fixtures |
| A direct explorer mutation targets the wrong game or silently chooses one explorer | Fresh concurrent describe snapshot, explicit game selector, explicit set-valued explorer selector, captured target ids, visible default marker, ambiguity failure, and response identity |
| Clock ties/skew make recency unsafe | Multiple-game ties/unknown fail; no PID/title/enumeration tie-break for mutation; explicit selector recommendation |
| Polling focus changes recency repeatedly while a window stays focused | Record only false-to-true `Minecraft.isWindowActive()` transitions and test repeated ticks |
| CLI writes config while game owns stale in-memory state | Game-hosted semantic action; CLI never edits explorer or game files directly, and this slice introduces no explorer persistence |
| Vox worker mutates Minecraft from the wrong thread | Bounded request queue/client-executor handoff, timeout/overload/shutdown outcomes, and thread assertions |
| Terminal bridge availability becomes required for all CLI control | Separate per-game local service; live proof works from external shell and Teamy Terminal with terminal server identity absent from selection |
| Protocol generation breaks Gradle-only contributors | Checked/generated Java or locked Java artifact, deterministic parity check in Rust tooling, and no Cargo invocation during Gradle/IDE sync |
| Registered action invocation accidentally becomes arbitrary remote execution | Accept only the existing SFM client-action Brigadier tree through `SFMClientActionExecutor`; canonical token and size bounds; typed availability/parse failures; no chat, Minecraft command, reflection, filesystem, shell, or coordinate fallback |
| Absolute explorer paths leak in normal logs/list output | Human output may show user-requested paths only for the operation; default discovery telemetry uses ids/hashes/labels and structured output documents sensitive fields |
| Incompatible focused game silently redirects command to older game | Select newest live instance first and fail capability/protocol negotiation; never fallback to another instance |
| Future manager/inventory commands force discovery redesign | Shared typed describe/capabilities/selection/dispatch/output foundation plus operation-specific request/result types |

## Source and implementation references

- `docs/AGENTS.md`
- `docs/tasks/typed selections relations and lazy explorers plan.md`
- `docs/tasks/contextual input actions and addressable explorer plan.md`
- `docs/tasks/cli ast refactoring suite plan.md`
- `platform/cli/sfm-propagate-changes/Cargo.toml`
- `platform/cli/sfm-propagate-changes/src/cli/`
- `platform/minecraft/src/main/java/ca/teamdman/sfm/client/control/SFMClientControlServer.java`
- `platform/minecraft/src/main/java/ca/teamdman/sfm/client/control/SFMClientThreadGate.java`
- `platform/minecraft/src/test/java/ca/teamdman/sfm/client/control/SFMClientThreadGateTests.java`
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
