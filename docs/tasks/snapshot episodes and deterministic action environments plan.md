# Snapshot episodes, action traces, and deterministic environments plan

**Plan status:** Proposed  
**Primary implementation root:** `D:\Repos\Minecraft\SFM\repos2\1.19.2`  
**Last updated:** 2026-07-21

## How to update this plan

- `[ ]` Not started
- `[~]` In progress
- `[x]` Complete
- `[!]` Blocked

Record schema decisions, fixtures, commit ids, validation output, visible puppet
evidence, and intentional exclusions beneath the task they affect. Work from
1.19.2. Java-only work uses the installed canonical CLI epoch; changes to the
Rust CLI require `check-all.ps1` and reinstalling the accepted CLI before
parallel Java work consumes its new surface.

## Purpose

Create a deliberately simple, lossless representation of a system state and the
transition history of an episode. The first state model is:

```rust
BTreeMap<RepoPath, BString>
```

The constitutional interchange format is one potentially large JSON file. It
is allowed to duplicate unchanged files and be inefficient. Every future
content-addressed, delta, AST-normalized, or amalgamated representation must be
able to export this ordinary full form. A user or test must always be able to
select any timestep, including `t=0` and the final-frame alias `t=-1`, and
rehydrate a complete independent snapshot.

This foundation supports several related products:

- an in-game timeline/episode inspector;
- exact replay of input events through stateful action engines;
- a generic multiplexer observation/recording layer, proven against an ordinary
  calculator panel for deterministic agent experiments;
- source snapshots and structured comparisons for in-game code review;
- refactoring environments with before/after repositories and compile feedback;
- a capability-controlled virtual shell and virtual filesystem;
- future Vox transport between Java/Minecraft and Rust tools; and
- later semantic amalgamation, normalization, compression, or sea-of-nodes
  experiments without making them prerequisites for the dumb format.

## Related plans

- [In-game code review workspace and window manager](in-game%20code%20review%20workspace%20and%20window%20manager%20plan.md)
- [CLI AST refactoring suite](cli%20ast%20refactoring%20suite%20plan.md)
- [Draw editor layers, commands, and canvas workspace](draw%20editor%20document%20regions%20and%20commands%20plan.md)
- [SFM client log console](sfm%20client%20log%20console%20plan.md)

## Terms and invariants

### Snapshot

A snapshot is an immutable, complete logical filesystem. Paths are normalized
repository-relative values, never arbitrary ambient host paths. Contents are
bytes; JSON entries distinguish UTF-8 text from base64 bytes rather than
assuming every `BString` is valid JSON text.

The canonical JSON uses a deterministically sorted file-entry array. JSON object
member ordering is not treated as semantic ordering. A canonical snapshot hash
covers schema version, normalized paths, encoding tags, and exact bytes.

### Episode

An episode contains an initial state, ordered input events, action invocations,
state transitions, observations, terminal status, and enough full frames to
rehydrate any advertised timestep. The v1 dumb format may store every complete
snapshot. Later formats may use checkpoints and deltas internally, but their
ordinary exporter must reproduce the same full frames.

### Raw event and action invocation

Raw input is not collapsed into action counts. Events have monotonically
increasing sequence numbers plus optional tick/time metadata. Key press and
release, repeat state, focus loss/reset, typed characters, pointer/controller
events, and environment-specific inputs use typed variants.

An action invocation retains:

- stable action id and typed arguments;
- source binding and binding revision when applicable;
- exact source-event range or event ids;
- action sequence number and tick/time;
- availability/authorization decision;
- result, error, or cancellation; and
- state-transition identity.

Tests prefer exact traces such as “only events 21 and 41 invoked
`format_document`” over a weaker call-count assertion.

### Replay

A replayable component may be stateful. It receives events incrementally and
may retain pressed keys, partial key sequences, calculator memory, cursor
position, or other ordinary state. Replay means that a fresh instance with the
same configuration, initial state, timing policy, and ordered events produces
the same action and transition trace. It does not require every method to accept
the complete history or make internal state illegal.

### Amalgamation and deamalgamation

Keep three operations distinct:

1. lossless pack/unpack of snapshots and episodes;
2. structural normalization, such as formatting or alpha-renaming; and
3. semantic amalgamation into an equivalence-oriented intermediate form.

Only lossless packing is inherently reversible. If normalization unifies
different identifiers or semantic lowering unifies a loop with repeated
statements, exact source spelling has been discarded. Exact deamalgamation then
requires a witness containing names, source ranges, formatting, and
transformation lineage.

Required laws for the dumb representation are:

```text
unpack(pack(snapshot)) == snapshot
export(import(big_json)) == canonicalize(big_json)
rehydrate(episode, t) == episode.full_frame(t)
```

Optimized representations must either satisfy equivalent round trips or declare
which distinctions they intentionally quotient and retain witnesses sufficient
for every advertised reconstruction.

### Equivalence levels

Never expose a single ambiguous `equivalent` boolean. Comparison reports name
the proven level:

- byte equality;
- syntax equality;
- formatting-insensitive equality;
- alpha-equivalence under declared symbol correspondence;
- symbolic identity under a bounded rewrite set;
- observational equivalence under an explicit observation policy;
- task/terminal-state equivalence; or
- unknown.

A ten-iteration loop and ten `println` statements may have the same bounded
output while differing under timing, interruption, instrumentation, stack
traces, and side effects. The observation policy is part of the claim.
Conservative `unknown` is correct when proof is unavailable.

The saved calculator article at
`D:\OneDrive\Documents\articles\calculator-app - Chad Nauseam Home.html`
is a design reference: Android's calculator combines exact rational,
recognizable symbolic, and constructive-real representations instead of
requiring one representation to solve every case. Apply the analogous policy
here—retain exact/simple forms where possible, use bounded symbolic reasoning
where justified, and fall back to approximation or `unknown` without a false
claim. The conference-talk reference `NxiKlnUtyio` and the maintainer's
starred sea-of-nodes/compiler repositories remain research inputs to inventory
before Phase 7 chooses an IR.

## Proposed v1 interchange shape

```json
{
  "schema": "sfm.snapshot-episode/1",
  "environment": {
    "id": "sfm:repository",
    "version": 1,
    "configuration": {}
  },
  "frames": [
    {
      "index": 0,
      "files": [
        {
          "path": "src/A.java",
          "content": {
            "encoding": "utf8",
            "text": "class A {}\n"
          }
        }
      ],
      "state": {}
    }
  ],
  "events": [],
  "actions": [],
  "transitions": [],
  "terminal": null
}
```

The schema above is illustrative until Phase 0 closes naming and validation.
The first committed schema requires explicit size limits, duplicate-path
rejection, path normalization, unknown-field/version policy, and canonical
serialization tests.

## Phase 0 — Close the contracts

### [ ] 0.1 Define snapshot and episode vocabulary

- Decide canonical schema ids, versions, and hashes.
- Specify `RepoPath` normalization and reject absolute paths, parent
  traversal, duplicate normalized paths, and platform-dependent separators.
- Specify UTF-8/base64 content variants and bounded decoding.
- Define frame indexing, the final-frame alias, empty episodes, and failure
  behavior for missing or corrupt frames.
- Define canonical serialization independently of pretty JSON presentation.

**Completion criteria:** Rust and Java fixtures agree byte-for-byte on paths,
contents, frame indices, and hashes.

### [ ] 0.2 Define event, action, transition, and observation records

- Define typed raw events with exact sequence ordering and optional clock data.
- Define action invocation provenance, availability, authorization, arguments,
  results, and source-event ranges.
- Define deterministic transition ids and environment observations.
- Record binding/configuration revisions so replay does not accidentally use a
  newer keymap.
- Separate hard method/capability constraints from reward or efficiency scores.

**Completion criteria:** Two traces with the same action count but different
input histories remain observably different.

## Phase 1 — Implement the dumb adapters

### [ ] 1.1 Rust snapshot and episode models

- Add Facet-serializable models around deterministic maps and byte strings.
- Read/write one complete JSON file without requiring Git, Gradle, Minecraft,
  an AST parser, or Vox.
- Provide canonicalize, validate, hash, inspect, and extract-frame operations.
- Keep optimized blob/delta storage out of the first implementation.

### [ ] 1.2 Java snapshot and episode models

- Implement the same versioned interchange contract on the Minecraft side.
- Keep host filesystem access behind an explicit source/provider boundary.
- Bound file count, content bytes, event count, and decoded allocation.
- Add shared fixture conformance tests between Rust-produced and Java-produced
  documents.

### [ ] 1.3 Full-frame round-trip proof

- Generate text, binary, empty, Unicode, malformed, duplicate, and hostile-path
  fixtures.
- Prove every valid timestep rehydrates a complete independent snapshot.
- Prove export from any future internal representation returns canonical v1.

## Phase 2 — Build the reusable timeline panel and Episode Inspector

### [ ] 2.0 Define the timeline position and seekable-panel contract

The timeline is a composable host that accepts one inner panel and instructs it
which timestep to present. It does not know how to render repository files,
inventory slots, calculators, or editor state.

The initial time domain is a bounded inclusive integer range. A conceptual
contract is:

```text
TimelineModel {
    first_timestep,
    last_timestep,
    current_timestep,
    playing,
    ticks_per_step
}

SeekableTimelinePanel {
    timelineBounds()
    setTimelinePosition(timestep)
}
```

Resolve aliases such as `t=-1` to an ordinary bounded position before calling
the child. Seeking is random-access and idempotent: the result at `t=4` cannot
depend on first visiting `t=0..3`. Invalid bounds and positions produce visible
diagnostics rather than partially mutating the child.

The first movement proof uses several integer timesteps for animation. A future
fractional/substep position may interpolate between keyframes, but is not
required to establish the v1 API.

### [ ] 2.1 Implement the MPV-like timeline host

Place the inner panel in the content area and a compact horizontal transport at
the bottom. The first controls are:

- play/pause;
- click or drag on the horizontal track to seek;
- fixed thumb/current-position indicator;
- current and final timestep text;
- previous/next single-step;
- Home/End first/final navigation; and
- keyboard focus, narration, and deterministic tick-driven playback.

The transport owns input in its own bounds and does not leak slider clicks into
the inner panel. Seeking instructs the child immediately. Playback advances by
the declared deterministic tick policy rather than wall-clock sampling.
Resizing changes geometry but not the selected timestep. The timeline wrapper
can itself be hosted full-screen or as a normal multiplexer panel.

### [ ] 2.2 Add the falsified chest/inventory replay panel

Create a read-only, non-menu-backed inventory replay panel that visually
resembles a chest plus player inventory. It renders copied `ItemStack` values,
slot backgrounds, a virtual cursor, and any cursor-held stack, but it does not
open a live container, send clicks, mutate a player inventory, or require a
server.

Use a deterministic fixture:

1. `t=0`: one cobblestone is in a chest slot; player inventory and cursor are
   empty;
2. pickup: the chest slot becomes empty and the cobblestone becomes the
   cursor-held stack;
3. transit timesteps: the virtual cursor and held stack move through recorded
   panel-local positions toward a selected player-inventory slot;
4. pre-place: the held stack is over the destination slot; and
5. final: the player slot contains one cobblestone and the cursor is empty.

The replay state explicitly stores chest slots, player slots, cursor stack, and
cursor position at each advertised timestep or derives them from deterministic
keyframes. It must support seeking final-to-first-to-middle in arbitrary order
with identical results. This is groundwork for visually replaying item
movements, not a claim that live Minecraft menu synchronization has been
captured.

### [ ] 2.3 Build responsive Episode Inspector content

Create a normal full-screen host plus composable panels showing:

- the reusable timeline host and its selected inner visualization;
- logical file tree;
- selected file contents or binary metadata;
- raw events;
- resulting action invocations;
- transition/result/error details; and
- environment-specific state/observations.

Scrubbing never mutates the source episode. Selecting an action can jump to its
source events; selecting an event can reveal resulting actions. Selecting an
inventory transition may make the falsified inventory replay the timeline's
inner panel.

### [ ] 2.4 Add command-palette and multiplexer integration

- Add a typed action such as `sfm:developer/open_episode`.
- Accept only bounded, explicitly selected episode sources.
- Open full-screen normally and as a panel when the current host is the SFM
  multiplexer.
- Reuse file-presentation styles and console widgets where appropriate without
  coupling the episode model to those screens.

### [ ] 2.5 Add timeline and Episode Inspector puppets

The inventory timeline puppet captures:

1. chest-owned cobblestone at the first timestep;
2. cursor-held cobblestone after pickup;
3. at least one visible transit position;
4. the pre-place destination hover;
5. player-inventory ownership at the final timestep;
6. dragging the horizontal timeline backwards; and
7. random seek order proving the view does not depend on playback history.

The broader Episode Inspector puppet also captures a loaded repository episode
at `t=0`, an intermediate changed file/state, final `t=-1`, an action with
its source events, an exported/reopened snapshot, and malformed/bounded-error
presentation.

## Phase 3 — Prove generic panel observation with a normal calculator

The calculator is a proving application, not the owner of recording. Generic
input observation, replay, visual/structured capture, agent routing, and
interaction ownership wrap the multiplexer/panel host as Track 7 of the
[in-game workspace plan](in-game%20code%20review%20workspace%20and%20window%20manager%20plan.md).

### [ ] 3.1 Define the generic panel-recording projection

- Project multiplexer panel lifecycle, focus, normalized panel-local pointer,
  keyboard/character/controller events, origin, routing/consumption results,
  ownership changes, optional frame images, and optional structured panel
  observations into the episode interchange.
- Record workspace/panel and configuration revisions needed to reject an
  incompatible replay.
- Keep visual frame capture optional: exact input provenance must not depend on
  taking a screenshot after every event.
- Permit ordinary full-screen `Screen` hosts to use the same envelope through
  an adapter while treating composable panels as the primary workspace unit.
- Keep the recorder outside child panels; panels may only contribute optional
  structured observations through a narrow interface.

### [ ] 3.2 Build an ordinary composable calculator panel

Implement calculator display, memory/expression/input state, and
controller-like buttons as a normal responsive panel with a full-screen
compatibility host. It accepts ordinary pointer, keyboard, and controller input
through the shared panel routing surface and contains no dedicated event log,
timeline scrubber, replay engine, or agent session.

An optional structured observation reports calculator state for tests and
agents. The visible panel remains an ordinary calculator even when no recorder
or agent exists.

Keep “display equals 4” distinct from “compute the supplied addition using
calculator controls.” Hard method sanctity belongs in environment capabilities
and terminal predicates. Reward may prefer fewer steps but cannot make a
forbidden direct-state mutation acceptable. Randomized/hidden operands can
distinguish general addition from memorizing an answer.

Retain fixtures where `2+2` reaches display `4` by pressing 4, 2+2,
1+1+1+1, 4+0, or 8/2, and where 99+44 uses inefficient repeated increment.
Which satisfy the task depends on the declared goal and capabilities, not logic
inside the calculator panel.

### [ ] 3.3 Generic recording, ownership, and replay puppet

Open the normal calculator beside another panel, record human input through the
generic multiplexer envelope, replay it into a fresh calculator, acquire the
calculator for an agent with a visible ownership indication, manipulate its
virtual cursor/keyboard, reject human mutation input only on the owned panel,
release ownership, and inspect the resulting generic episode. Rehydrating each
recorded frame/observation must reproduce the advertised calculator state.

## Phase 4 — General action spaces and keybinding traces

### [ ] 4.1 Parameterized input actions

Keep `charTyped(char)` or `type_char { scalar }` as a parameterized action
kind. A bounded RL environment may instantiate an allowed glyph alphabet as
discrete choices or use an action-kind head plus parameter head. Do not require
thousands of global action registrations merely to represent characters.

Model glyph-picker, cursor movement, calculator controls, and editor operations
as composable actions so characters unavailable on a physical keyboard remain
reachable through the environment.

### [ ] 4.2 Integrate the SFM dynamic keybinding engine

Record normalized press/release/focus/tick events, the exact binding snapshot,
partial-sequence behavior, and emitted action invocations. Replay through a
fresh engine must reproduce the same invocations, including absence of an
invocation after cancellation or focus loss.

### [ ] 4.3 Define binding JSON, rule IR, and optional program projection

Treat declarative JSON and a small program-like form as projections of one
typed rule IR. A rule such as “on each tick, if Ctrl+Alt+L became pressed,
invoke format_document” may be easier to analyze or compose as a program/state
machine than as ad hoc configuration fields. Do not make the surface syntax
the executor.

Define parse/print and From/Into-style round trips, explicit timing/edge
semantics, and diagnostics for rules that cannot be represented by the bounded
keybinding UI. The ordinary settings screen continues to edit the typed
key-sequence subset even if a later advanced editor exposes the program form.

## Phase 5 — Refactoring playground environments

### [ ] 5.1 Before/after repository fixtures

Store tracked immutable `state_before` and `state_after` snapshots for small
Java tasks. Materialize mutable attempts only beneath an ignored, bounded SFM
playground root. The first fixture renames `B` to `C`, including Java file
rename, constructor/type references, and compile outcome.

### [ ] 5.2 Environment actions and observations

Expose typed source operations, text-editor actions, compile, test, inspect
diagnostics, reset, and compare-to-goal. Preserve the exact action trace and
every resulting snapshot. Distance to the final code is an observation/metric,
not proof that the method used was safe or semantically valid.

### [ ] 5.3 Controlled Java compilation

Run compilation in an isolated worker with bounded time/memory/output,
controlled source/class paths, annotation processing disabled unless
allowlisted, no compiler plugins, and no ambient build-tool execution. Treat
compiler diagnostics as observations. Do not assume arbitrary `javac` input
is harmless merely because Gradle is absent.

## Phase 6 — Capability-controlled virtual shell

### [ ] 6.1 Define a small parsed shell language

Do not begin by claiming full PowerShell compatibility. Define a typed AST for
commands, arguments, pipelines, redirections, sequencing, and environment
operations over an SFM-owned virtual filesystem and PATH.

Approval operates on the parsed and resolved command/capability graph, never a
regex over the source string. Default deny. A compiler permission identifies
the resolved executable/capability and argument policy, optionally including
content hash, rather than trusting a basename or path-shaped string.

### [ ] 6.2 Deterministic interpreter and trace

Represent commands as typed functions over virtual state. Record pipeline
stages, capability checks, outputs, errors, and transitions. The same shell AST
and initial VFS must replay deterministically where its capabilities promise
determinism.

## Phase 7 — Structural and semantic amalgamation

### [ ] 7.1 Structural normalization with witnesses

Prototype formatting-insensitive and alpha-renamed Java forms while retaining
source maps and witnesses. Prove which original forms can be exactly restored.
Never replace the full snapshot interchange with normalized source.

### [ ] 7.2 Conservative semantic comparison

Add bounded symbolic rewrites, control-flow/IR experiments, or sea-of-nodes
lowering only after the dumb adapters and exact snapshot comparisons work.
Reports name their assumptions and return `unknown` rather than false
equivalence.

### [ ] 7.3 Optimized episode storage

Explore content-addressed blobs, Merkle trees, snapshot deltas, shared AST
nodes, and periodic checkpoints. Measure space/time against full-frame JSON.
Every accepted representation exports the canonical big JSON and supports
random-access rehydration of advertised frames.

## Phase 8 — Vox and external-agent integration

Transport the same versioned snapshot, episode, intent, and action models only
after the local Java and Rust adapters conform. Vox is an optional transport,
not the owner of the data model. External agents receive explicit capabilities
and bounded environments; they do not gain ambient filesystem, shell, compiler,
Minecraft input, or action authority merely by connecting.

## Overall acceptance criteria

- [ ] A single canonical JSON file losslessly represents a complete snapshot.
- [ ] A single canonical JSON file represents an episode and rehydrates every
  frame, including `t=0` and the final-frame alias.
- [ ] Rust and Java round-trip the same fixture corpus.
- [ ] Raw event provenance distinguishes traces with equal action counts.
- [ ] A stateful keybinding engine replays deterministically from recorded
  events and binding revision.
- [ ] The in-game Episode Inspector visibly scrubs files, events, actions, and
  environment state.
- [ ] The reusable timeline hosts an arbitrary seekable inner panel, provides
  deterministic MPV-like horizontal transport, and supports random-access
  seeking without playback-history dependence.
- [ ] The falsified chest replay visibly moves one cobblestone from chest to
  cursor to player inventory while never opening or mutating a live menu.
- [ ] The generic multiplexer layer records, exports, imports, and replays an
  ordinary calculator panel; recording and agent ownership are not calculator
  responsibilities, and hard method constraints remain separate from
  efficiency.
- [ ] Agent ownership gates mutation input only for the leased panel, uses
  virtual panel-local input, visibly identifies the owner, and always permits
  explicit human emergency revoke/recovery.
- [ ] Refactoring playgrounds never mutate tracked fixtures or escape their
  ignored bounded roots.
- [ ] Virtual-shell approvals operate on resolved AST/capabilities, not regex.
- [ ] Every optimized or amalgamated representation exports the canonical dumb
  form and declares any non-reversible quotient plus required witnesses.
