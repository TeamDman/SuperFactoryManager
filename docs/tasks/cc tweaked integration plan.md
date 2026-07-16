# CC:Tweaked Integration Plan

**Plan status:** Complete

> The read-only table/item-detail contract recorded here was superseded by
> [`cc tweaked mutation plan.md`](cc%20tweaked%20mutation%20plan.md). Keep this
> document as the completed dependency, availability, and original-integration
> record.

**Primary implementation branch:** `1.19.2`

**Last updated:** 2026-07-13

**Primary implementation root:** `D:\Repos\Minecraft\SFM\repos2\1.19.2`

## How to update this plan

Each work item carries its status in its heading. Update the heading and the
completion notes together so progress is visible where the work is described.

- `[ ]` Not started
- `[~]` In progress
- `[x]` Complete
- `[!]` Blocked

A phase is complete only when every work item in that phase is marked `[x]`.
Record commit IDs, contract decisions, validation results, and follow-up notes
directly under the relevant work item. Do not add a detached work log at the
end of this document.

## Purpose

Deliver a supported CC:Tweaked integration for SFM, beginning on Minecraft
1.19.2. A CC:Tweaked computer must be able to discover and use the agreed SFM
peripheral surface, and SFM must be proven able to use a turtle inventory
through an SFM cable network. CC:Tweaked item-detail integration must expose
SFM's meaningful item state without leaking mutable NBT implementation details.

This plan starts after the completed dependency and source-management work. It
does **not** reopen dependency resolution, source acquisition, or the v3
lockfile design.

## Scope

In scope:

- An API-only CC:Tweaked integration: new production code may depend only on
  `dan200.computercraft.api`, never `dan200.computercraft.shared` or another
  CC:Tweaked internal package.
- A documented Lua-facing peripheral contract for SFM managers and the agreed
  cable-network projection.
- Read-only SFM item details for printing forms, program disks, and label guns.
- Explicit, validated label/program write operations if the contract adopts
  them; no write is implicit in an item-detail query.
- GameTests that prove peripheral discovery, the agreed Lua/API behavior,
  cable topology behavior, turtle inventory capability discovery, and an
  end-to-end SFM item transfer involving a turtle.
- User-facing documentation and changelog notes for the released behavior.
- Forward propagation only after the 1.19.2 implementation and tests pass.

Out of scope:

- Changing the CC:Tweaked version, Maven repositories, source providers, or
  lockfile schema, except for an independently justified dependency update.
- Calling CC:Tweaked internals, mixins, reflection, or private access
  transformers from production code to obtain functionality not offered by its
  public API. GameTests may use CC:Tweaked internals as fixture infrastructure
  when that is the clearest way to construct and drive a real CC environment.
- Redesigning SFM's DSL, cable-network cache, label-gun UX, disk editor, or
  manager GUI beyond the small server-side primitives needed by the integration.
- Turning a read-only item detail call into a general NBT editor.
- Inventing an unbounded remote-network protocol. Physical CC:Tweaked
  peripheral/wired-modem semantics remain CC:Tweaked's responsibility.

## Established foundation

The dependency-source plan already established the following. These are
prerequisites, not work to repeat in this plan.

- `cc-tweaked` is a 1.19.2 integration mod at
  `org.squiddev:cc-tweaked-1.19.2:1.101.3`, with compile, runtime, and
  GameTest scopes in `platform/minecraft/sfm-toolchain.lock.json`.
- The authoritative Git source is
  `https://github.com/cc-tweaked/CC-Tweaked.git` at
  `v1.19.2-1.101.3` / `f9bb1b497964cccab6cde34e8948333210275f93`.
  The lockfile also retains a Maven-sources alternative.
- `dependency source acquire` and `dependency source search` can materialize
  and inspect that exact source without depending on a developer's checkout.
- `ComputerCraftDependencySmokeGameTest` proves that the dependency loads and
  a turtle and disk drive can be placed. It is intentionally not a functional
  integration test.

## Confirmed design constraints

1. Work begins only on `1.19.2`. Use `sfm-propagate-changes.exe git merge` to
   propagate the finished feature forward; preserve newer-version code rather
   than overwriting it.
2. New production CC:Tweaked integration belongs in a clearly named,
   server-safe package under `ca.teamdman.sfm.common.compat.computercraft`.
   Keep CC:Tweaked references out of generic SFM core classes where a narrow
   adapter will do.
3. A manager operation must use the existing `ManagerBlockEntity` and
   `DiskItem` lifecycle. In particular, program changes must go through the
   normal disk update, parse, lint, warning, state, persistence, and client
   update behavior; they must not write `sfm:program` directly.
4. Labels must use `LabelPositionHolder` and existing label-gun semantics.
   SFM-owned NBT keys such as `sfm:program` and `reference` are storage
   details, not the Lua API.
5. Peripheral identity and equality must remain stable while the represented
   world object remains valid. Adapters must not retain a removed level, block
   entity, or stale cable-network view.
6. Peripheral calls that touch Minecraft world state run on the server thread.
   They must neither access client-only classes nor hold a world reference past
   its valid lifecycle.
7. The Lua contract is public compatibility surface. Every exported type name,
   method name, argument shape, result shape, and error behavior must be
   documented and covered by a GameTest before it is treated as stable.
8. Existing general CC:Tweaked support for Forge item handlers is not a
   substitute for this feature. The turtle test must prove SFM's cable-network
   discovery and transfer behavior, while item-detail tests prove the specific
   SFM metadata contract.
9. GameTests may use CC:Tweaked registry and runtime internals to place and
   drive real computers, turtles, and other test fixtures. New production
   integration code must use only the public API.

## Design questions that must be closed before implementation

These are intentional gates, not invitations to decide behavior incidentally
while coding.

| Question | Required decision | Acceptance consequence |
| --- | --- | --- |
| Peripheral targets | Which of a manager block, a cable block, and a cable-network endpoint expose a peripheral; whether a cable with zero or multiple reachable managers exposes nothing, a network peripheral, or another explicitly named object. | A topology GameTest covers direct adjacency, one manager, zero managers, multiple managers, network split, and removal. |
| Peripheral contract | Exact peripheral type(s), method names, argument/result tables, validation, state reporting, and write/error behavior. | API-level and real-computer GameTests assert each supported call and each declared failure. |
| Program mutation | Whether Lua can write a manager/disk program; if yes, whether it requires an inserted SFM disk and how compile failure is returned without losing the stored source. | Tests prove successful update, no-disk rejection, invalid-program result, state refresh, and persistence. |
| Label mutation | Whether labels are read-only in the first release or which explicit operations may mutate labels on a disk/label gun. | Tests prove stable read shape and, if enabled, validation, mutation, and removal without raw NBT edits. |
| Item details | The exact CC:Tweaked 1.19.2 public extension point for item details and its namespaced Lua data schema. | Tests invoke the same public CC:Tweaked detail path exposed to users, not a copied SFM-only serializer. |
| Lua execution proof | Whether an embedded computer runs Lua in GameTests or a public-API peripheral harness is used for each case; at least one end-to-end test must exercise real CC:Tweaked discovery and invocation. | The test suite states which layer each test proves and does not mistake direct Java calls for Lua wiring coverage. |

## Source and implementation references

### SFM

- `docs/AGENTS.md` — oldest-branch-first, propagation, Gradle prohibition, and
  version-surface rules.
- `platform/minecraft/src/main/java/ca/teamdman/sfm/common/blockentity/ManagerBlockEntity.java`
  — disk ownership, program lifecycle, state, persistence, and updates.
- `platform/minecraft/src/main/java/ca/teamdman/sfm/common/block_network/CableNetwork.java`
  — contiguous cable topology and capability cache.
- `platform/minecraft/src/main/java/ca/teamdman/sfm/common/label/LabelPositionHolder.java`
  — label storage and mutation boundary.
- `platform/minecraft/src/main/java/ca/teamdman/sfm/common/item/DiskItem.java`
  — program source, diagnostics, and name behavior.
- `platform/minecraft/src/main/java/ca/teamdman/sfm/common/item/FormItem.java`
  — stored reference-item behavior for printing forms.
- `platform/minecraft/src/main/java/ca/teamdman/sfm/common/item/LabelGunItem.java`
  — label-gun state and user behavior.
- `platform/minecraft/src/gametest/java/ca/teamdman/sfm/gametest/SFMGameTestHelper.java`
  — manager, capability, inventory, and asynchronous GameTest helpers.
- `platform/minecraft/src/gametest/java/ca/teamdman/sfm/gametest/tests/compat/computercraft/ComputerCraftDependencySmokeGameTest.java`
  — retained loading smoke test.

### CC:Tweaked 1.19.2 API

Acquire and inspect the locked tree rather than a local checkout:

```pwsh
cargo run -- dependency source acquire cc-tweaked --provider git --branch 1.19.2
cargo run -- dependency source search IPeripheralProvider --dependency cc-tweaked --provider git --branch 1.19.2 --require-complete
cargo run -- dependency source search GenericSource --dependency cc-tweaked --provider git --branch 1.19.2 --require-complete
cargo run -- dependency source search IComputerAccess --dependency cc-tweaked --provider git --branch 1.19.2 --require-complete
```

The primary starting points are:

- `dan200.computercraft.api.ForgeComputerCraftAPI`
- `dan200.computercraft.api.peripheral.IPeripheralProvider`
- `dan200.computercraft.api.peripheral.IPeripheral`
- `dan200.computercraft.api.peripheral.IDynamicPeripheral`
- `dan200.computercraft.api.peripheral.IComputerAccess`
- `dan200.computercraft.api.lua.GenericSource`
- the public item-detail extension API identified in Phase 1.

Historical product context is recorded in SFM issues #437 (CC:Tweaked
integration) and #261 (turtle/label-gun work). Treat their text as input to
the contract review, not as an implementation specification that overrides
this plan's explicit decisions.

## Execution order

```text
Phase 0 established dependency/source fixture
  -> Phase 1 contract and API reconnaissance
  -> Phase 2 peripheral architecture and lifecycle
  -> Phase 3 item details and explicit mutations
  -> Phase 4 turtle/cable-network support
  -> Phase 5 GameTest acceptance matrix
  -> Phase 6 documentation, propagation, and release validation
```

Phases 2, 3, and 4 may use separate commits after Phase 1 closes the API
contract. Phase 5 starts with each implementation phase and is complete only
when its full matrix passes; it is shown later to make the release gate easy to
audit.

## Phase 0: Established dependency and source fixture

### [x] 0.1 Preserve the CC:Tweaked binary smoke baseline

**Completion notes:** The dependency-source plan completed this baseline on
2026-07-13. `ComputerCraftDependencySmokeGameTest` verifies that CC:Tweaked is
available, reports an installed API version, and can place a turtle and disk
drive. This test remains a fast dependency regression check and is not renamed
or replaced by the functional tests in this plan.

**Validation:**

```pwsh
cargo run -- game-test run-server --branch 1.19.2 --filter computer_craft_dependency_smoke
```

### [x] 0.2 Establish authoritative source access

**Completion notes:** The v3 lockfile locks Git and Maven-source providers for
CC:Tweaked. Complete source search resolves `IPeripheralProvider`,
`IPeripheral`, `IComputerAccess`, and `GenericSource` from the exact 1.19.2
Git tree. The integration must continue to rely on those sources, not a
developer-specific checkout.

## Phase 1: Close the public contract and API choices

### [x] 1.1 Inventory the public CC:Tweaked extension APIs

**Completion notes:** Completed on 2026-07-13 against the locked CC:Tweaked
`v1.19.2-1.101.3` Git tree. `ForgeComputerCraftAPI.registerPeripheralProvider`
and `IPeripheralProvider` are the supported block-entrypoint API;
`IPeripheral` public final methods annotated with `@LuaFunction(mainThread =
true)` provide the Lua method surface. `VanillaDetailRegistries.ITEM_STACK`
and `BasicItemDetailProvider` are the supported item-detail extension point
for `turtle.getItemDetail(..., true)` and inventory `getItemDetail` calls.
New production code will use these API packages only. A direct Java
provider/peripheral GameTest can isolate adapter behavior, but Phase 5 still
requires a live CC:Tweaked computer to perform discovery and invocation.

**Work:**

- Read the locked API source and identify the supported 1.19.2 registration
  point and lifecycle for block peripherals.
- Compare `IPeripheral` and `IDynamicPeripheral`; select the implementation
  that produces a deterministic, documented method surface without depending
  on CC:Tweaked internals.
- Identify the actual public item-detail registration API for this CC:Tweaked
  version, including the accepted target type and Lua serialization rules.
- Identify a supported GameTest-compatible path to create/discover a computer
  and invoke a peripheral. Record whether the test needs a minimal Lua program
  or can invoke public API objects directly for a given assertion.
- Record API file paths and relevant signatures in the completion notes. Do
  not copy source into SFM or cite an unversioned online API page instead.

**Completion criteria:** The chosen production APIs are public, exist in the
locked 1.19.2 source, have no `shared`/internal imports, and are sufficient
for every accepted contract item. GameTest fixture code may use locked
CC:Tweaked internals only to construct or drive a real test environment.

### [x] 1.2 Decide and write the manager/cable topology contract

**Completion notes:** Confirmed on 2026-07-13. Every SFM `ICableBlock` is a
potential `sfm_network` peripheral entrypoint, including a manager block
because `ManagerBlock` is an `ICableBlock`. The peripheral represents the
currently connected cable network at its touched position, not one arbitrarily
selected manager. A network exposes all *loaded* `ManagerBlockEntity` members
in deterministic packed-position order; zero managers yields an empty list and
multiple managers yield multiple entries. `CableNetwork` must provide this
explicit manager enumeration from its existing member representation. A
peripheral is identity-stable for its level and touched cable position, and
re-resolves the network for each call so removal, split, merge, and chunk purge
cannot retain a stale network or manager.

**Work:**

- Specify which positions can supply an SFM peripheral and from which queried
  sides. Define the exact relation between manager, cables, and the underlying
  cable network.
- Decide the behavior for a cable network containing zero, one, or more than
  one manager. Never choose a manager by incidental iteration order.
- Specify what happens after a cable is added/removed, a network splits or
  merges, a manager is removed, or a chunk is unavailable.
- Specify peripheral type names and equality/identity behavior for each valid
  target.
- Add the decision to this section's completion notes, including a diagram or
  position examples if they clarify the selected topology.

**Completion criteria:** A computer next to any valid/invalid topology has one
predictable discovery result, and the result can be implemented without
reaching into `CableNetwork` internals from an unsafe lifetime.

### [x] 1.3 Freeze the first Lua-facing method and data contract

**Current decisions:** The first network surface is read-only. CC:Tweaked
accesses it using normal peripheral discovery, for example
`local network = peripheral.wrap("north")`; there is no static
`CableNetwork:TryAcquire` Lua API. The first method is
`network.getManagers()`, returning a one-indexed Lua array. Each manager table
contains `position = { x, y, z }`, `state`, and `disk` (or `nil`). A disk table
contains `name`, `program`, and `labels`, where labels map each label name to a
deterministically ordered array of `{ x, y, z }` positions. This preserves the
user's network → managers → disk → program/labels model without pretending
that arbitrary returned Java objects are Lua peripherals.

`VanillaDetailRegistries.ITEM_STACK` will expose structured, read-only `sfm`
data for disks, label guns, and printing forms in ordinary detailed item
queries. Mutations are deliberately deferred from the network peripheral.
Phase 3 will add separately named, explicit item operations that model the
`DiskItem`/`LabelPositionHolder` surface; they must not be smuggled into a
detail lookup.

**Completion notes:** Implemented and exercised with a real CC:Tweaked
computer on 2026-07-13. `sfm_network.getManagers()` takes no arguments and is
the only network method in this first release; it runs on the server thread and
re-resolves the network for every call. `state` is one of `no_disk`,
`no_program`, `invalid_program`, or `running`. The namespaced item-data table
is `detail.sfm`: program disks expose `kind`, `name`, `program`, and `labels`;
label guns expose `kind`, `activeLabel`, `viewMode`, and `labels`; printing
forms expose `kind` and bounded `reference = { name, count }`. Unsupported
stacks have no `sfm` key. There are intentionally no mutation methods or raw
NBT values in this release.

**Work:**

- Define a compact first-release method set for program/state/labels. For each
  method, state its Lua name, arguments, result table, nil/false/error result,
  mutation authority, and thread/world preconditions.
- Define namespaced keys for SFM data returned to Lua. Use plain Lua values and
  tables that callers can serialize; do not expose Java objects, `ItemStack`s,
  NBT tags, or translation objects.
- Decide whether program and label writes ship in the first release. If they
  do, define a no-disk result, invalid-source result, label validation rules,
  and whether each mutation causes a state/client update.
- State all deliberately excluded methods so a later addition is an intentional
  compatibility change rather than an accidental omission.

**Completion criteria:** The complete contract is recorded in this plan and
is sufficiently precise to write call-level tests before implementation.

### [x] 1.4 Review contract decisions against gameplay and compatibility

**Completion notes:** User approved a read-only initial surface on
2026-07-13, while retaining explicit mutations as a future contract phase.
Reads use `ManagerBlockEntity`, `DiskItem`, `LabelPositionHolder`,
`LabelGunItem`, and `FormItem` read-only helpers; they neither bypass the
manager program lifecycle nor create NBT. The production version boundary is
the narrow `common.compat.computercraft` package, which depends only on the
public CC:Tweaked API. GameTest fixture code is explicitly allowed to use
CC:Tweaked internals to operate real computers and turtles. Any genuine
Minecraft/Forge divergence is deferred to the annotated adapter work during
forward propagation.

**Work:**

- Review the proposed behavior against the current manager/disk/label-gun
  interactions and the historical issue context.
- Confirm that an integration cannot bypass program parsing, labels, manager
  persistence, or existing SFM permissions/configuration assumptions.
- Check that the proposed method/data surface is viable on every supported
  Minecraft branch, or identify the smallest adapter boundary that will be
  annotated with `@MCVersionDependentBehaviour` after propagation.

**Completion criteria:** The plan has a user-approved contract with no open
choice that would change existing world data, permissions, or public Lua
behavior during Phase 2 or 3.

## Phase 2: Implement the peripheral architecture

### [x] 2.1 Add a narrow, server-only registration and provider adapter

**Completion notes:** `ComputerCraftIntegration` registers once from SFM's
common-setup enqueue step, only when the optional `computercraft` mod is
loaded. It registers `SFMNetworkPeripheralProvider` through
`ForgeComputerCraftAPI` and the item-detail provider through
`VanillaDetailRegistries.ITEM_STACK`. The provider rejects client and
non-`ICableBlock` positions, constructs the relevant server cable network, and
returns a peripheral anchored to an immutable queried position. Production
imports remain in `dan200.computercraft.api` only.

**Work:**

- Create the `common.compat.computercraft` package and register the chosen
  public CC:Tweaked provider at the correct Forge lifecycle point.
- Keep registration idempotent under the normal mod lifecycle; do not register
  on the client or from a block-entity constructor.
- Resolve queried positions and sides through the public provider input and
  return no peripheral for every invalid/unloaded/unsupported case.
- Add focused Java tests where pure topology or validation logic can be tested
  without a server.

**Completion criteria:** The provider is registered once, has no CC:Tweaked
internal import, and returns a valid peripheral only for the Phase 1 topology.

### [x] 2.2 Implement the manager peripheral against SFM lifecycle methods

**Completion notes:** `SFMNetworkPeripheral` implements the declared
read-only method and equality semantics. Manager values are read via
`ManagerBlockEntity.getStateReadOnly`, `DiskItem` read-only accessors, and
`LabelPositionHolder.fromReadOnly`; no tag layout is copied into the Lua
contract. This initial contract has no program mutation, so there is no
alternative program-write path that could bypass `ManagerBlockEntity`.

**Work:**

- Implement the frozen peripheral type, identity/equality, attach/detach
  behavior, and each declared Lua method.
- Route reads through `ManagerBlockEntity`, `DiskItem`, and
  `LabelPositionHolder` public behavior rather than duplicating their tag
  formats.
- Route program changes through the manager's program-update path so parsing,
  diagnostics, state, persistence, warning rebuilds, and UI notifications
  remain consistent with in-game editing.
- Return declared, stable failures for no disk, invalid input, unloaded state,
  and any unsupported operation. Do not silently report a successful write.

**Completion criteria:** Every public method has one implementation path and
its mutation behavior is observable through existing SFM state.

### [x] 2.3 Implement the agreed cable-network projection

**Completion notes:** `CableNetwork.getManagers()` enumerates loaded
`ManagerBlockEntity` members in deterministic packed-position order. The
peripheral performs a non-mutating network lookup for each call, so split,
merge, and entry-cable removal do not retain a stale manager/network reference.
`ComputerCraftNetworkPeripheralGameTest` covers a cable entrypoint, direct
manager entrypoint, a managerless network, split/rejoin, and entry removal.

**Work:**

- Implement the exact zero/one/multiple-manager behavior selected in Phase
  1.2, using a topology query that is deterministic and lifecycle-safe.
- Invalidate or re-resolve projections after cable-network changes instead of
  caching a manager forever inside a peripheral.
- Ensure a CC computer adjacent to a cable cannot gain access to a different
  manager merely because a network changed after discovery.

**Completion criteria:** Direct-manager and cable-mediated discovery obey the
same documented identity, isolation, and invalidation rules.

### [x] 2.4 Add operational diagnostics without leaking program contents

**Completion notes:** Expected discovery misses are represented as an empty
CC:Tweaked provider result, rather than normal-log noise. The only public
method has no caller-controlled arguments and returns a safe empty list when
its entry cable no longer resolves, so malformed Lua data cannot crash a
manager tick or enter logs. No path logs program text, labels, or item NBT.

**Work:**

- Add useful server log context for registration or unexpected provider
  failures, subject to SFM's existing logging conventions.
- Do not log full program source, labels, item NBT, or a Lua caller's arguments
  at normal log levels.
- Confirm malformed peripheral calls return their documented Lua error/result
  and do not crash a manager tick.

**Completion criteria:** A bad peripheral call is diagnosable and contained;
no diagnostic path exposes player program or item data unexpectedly.

## Phase 3: Expose SFM item details and explicit mutations

### [x] 3.1 Define canonical read-only item details

**Completion notes:** `SFMItemDetailProvider` adds only `sfm` to CC:Tweaked's
existing detailed item map and leaves all standard fields intact. It supplies
semantic disk, label-gun, and printing-form data with empty/default values for
blank items. `ComputerCraftItemDetailsGameTest` tests the registry path and
the absence of NBT creation on a blank disk; the real-computer Lua GameTest
uses a chest's `getItemDetail(slot, true)` call for all three SFM item types.

**Work:**

- Implement the Phase 1 public CC:Tweaked item-detail extension point for
  `FormItem`, `DiskItem`, and `LabelGunItem`.
- Define a namespaced SFM detail table. Its values must reflect SFM concepts,
  such as a form's referenced item, a disk's program/name/diagnostic summary,
  and label-holder data, rather than raw tag layout.
- Preserve standard CC:Tweaked item detail fields and avoid key collisions.
- Establish empty/default values for blank disks, forms without a valid
  reference, and label holders with no labels.

**Completion criteria:** A normal CC:Tweaked item-detail request exposes
stable, documented read-only SFM data for all three item types and no SFM keys
for unrelated stacks.

### [x] 3.2 Implement only approved program and label writes

**Completion notes:** No program or label write operation is approved for the
initial release. The public Lua surface has no mutation method, and the player
documentation records that deliberate exclusion. Read-only helpers and the
blank-disk GameTest prove that detailed queries do not create SFM NBT. A future
write proposal remains a compatibility expansion and must add separately named
operations plus the success/failure coverage required by this work item.

**Work:**

- If Phase 1 leaves writes out of scope, document that result and add negative
  tests proving detail lookups cannot mutate item state.
- If writes are approved, implement the smallest explicit peripheral method(s)
  that operate on a selected SFM disk or label holder through existing SFM
  helpers. Validate target type, label form, bounds, and duplicate/removal
  behavior.
- Reuse the manager-aware program path for an inserted disk. For a standalone
  disk, define and test how the program is compiled/diagnosed without a
  manager; do not create a second incompatible representation.
- Preserve stack identity, count, and non-SFM data unless the documented action
  changes it.

**Completion criteria:** Every supported write is explicit, validated,
persistent, observable in the normal SFM UI, and covered by success and
failure tests.

### [x] 3.3 Verify serialization and item-data safety

**Completion notes:** Blank-disk reads are proven not to create NBT; forms
expose only `{ name, count }` for their referenced stack rather than
recursively serializing it. The CC contract now limits a network call to sixteen
manager tables, with `getManagerCount()` exposing the uncapped count. Each text
field is limited to 8,192 characters, labels to sixteen entries and 64
positions each; companion `*Truncated` fields make every omission visible.
`ComputerCraftItemDetailsGameTest` covers oversized program/labels and malformed
stored label data, while `ComputerCraftPayloadLimitsGameTest` proves a live
seventeen-manager network is bounded and observable.

**Work:**

- Test unusual but valid program text, invalid program text, empty strings,
  large label sets near existing SFM limits, and malformed stored data.
- Bound returned detail size and avoid recursive serialization of a printing
  form's reference item. State the truncation/error rule if a limit is needed.
- Verify an item-detail call is read-only and a rejected write leaves the stack
  byte-for-byte equivalent in its SFM-relevant state.

**Completion criteria:** The integration does not create unbounded Lua data,
mutate through reads, corrupt item NBT, or bypass SFM's existing error state.

## Phase 4: Prove turtle inventory support through SFM cables

### [x] 4.1 Verify turtle inventory capability discovery

**Completion notes:** `ComputerCraftTurtleCapabilityGameTest` places a live
normal CC:Tweaked turtle and retrieves its standard Forge item handler through
SFM's normal capability discovery. The test verifies all sixteen slots and a
real insertion. No production CC:Tweaked capability adapter was needed.

**Work:**

- Place a normal CC:Tweaked turtle in a GameTest and obtain its item handler
  through SFM's normal capability discovery path.
- Verify the expected sides and slots, including the behavior for an absent or
  invalid capability. Do not use CC:Tweaked internal turtle inventory classes
  in production code or tests when a public Forge capability suffices.
- Record whether this works through existing capability discovery without a
  new SFM capability provider. Add code only if a genuine integration gap is
  demonstrated.

**Completion criteria:** The test proves the exact capability SFM consumes
from a live turtle and distinguishes direct adjacent discovery from
cable-network discovery.

### [x] 4.2 Add an end-to-end manager-to-turtle cable-network GameTest

**Completion notes:** `ComputerCraftTurtleCableNetworkGameTest` moves sixteen
dirt from an SFM source barrel to a connected live turtle using a manager disk
and SFM cables. Its program attempts the disconnected turtle first; that
turtle remains empty and the connected turtle receives every item. The manager
remains running.

**Work:**

- Build a test fixture containing an SFM manager with a valid disk/program,
  SFM cables, a turtle inventory, and one ordinary source or destination
  inventory.
- Label the real endpoints through the supported SFM test helper/API and run
  the manager until an item transfer completes.
- Assert both inventories' final counts and the manager's running state. Add
  a negative topology case where a disconnected turtle is not selected.
- Include a cable split/reconnection case if the Phase 1 topology contract
  exposes peripherals through cables, so cache invalidation is covered at the
  same time.

**Completion criteria:** A GameTest demonstrates SFM moving an item to or from
a turtle over its actual cable network, and a disconnected topology cannot
produce the same transfer.

## Phase 5: Functional GameTest acceptance matrix

### [x] 5.1 Add focused CC:Tweaked integration GameTests

**Completion notes:** Focused tests now live under
`gametest/tests/compat/computercraft/`: dependency smoke, network topology and
payload limits, item details, real Lua invocation, direct turtle capability
discovery, and cable-network turtle transfer. The topology and turtle tests
include removal and disconnected negative cases. Mutations are intentionally
absent from the first-release contract.

**Work:**

- Keep `ComputerCraftDependencySmokeGameTest` as the loading smoke test.
- Add new tests under
  `gametest/tests/compat/computercraft/` with names that express the behavior,
  rather than one broad test that hides multiple failures.
- Cover at least the following acceptance cases:

  | Area | Required proof |
  | --- | --- |
  | Registration | A live manager exposes the documented peripheral through the public CC:Tweaked path. |
  | Direct manager | Every read method returns the documented value for blank, valid, and invalid manager states. |
  | Mutations | Each approved program/label write updates normal SFM state; rejected writes preserve it. |
  | Cable topology | The exact zero/one/multiple-manager and split/removal rules selected in Phase 1.2. |
  | Item details | Real CC:Tweaked item-detail calls return the documented SFM fields for form, disk, and label gun. |
  | Turtle network | SFM discovers the live turtle handler and completes an item transfer over cables. |
  | Regression | A malformed Lua/API call, unloaded/removed target, and a disconnected topology fail safely. |

- Give asynchronous tests enough ticks for CC:Tweaked and the manager to
  update, but use explicit success conditions rather than fixed long delays.

**Completion criteria:** Each accepted contract clause maps to a focused,
repeatable GameTest with a diagnostic failure message.

### [x] 5.2 Add a real invocation path and retain narrow direct tests

**Completion notes:** `ComputerCraftLuaNetworkPeripheralGameTest` boots a
real normal CC:Tweaked computer, runs `startup.lua`, discovers
`peripheral.wrap("front")`, calls `getManagers()`, reads detailed item data
from a real chest peripheral, and signals success with real computer redstone.
Narrow direct registry/provider GameTests remain for precise topology and
read-only-NBT assertions.

**Work:**

- Add at least one GameTest that uses an actual CC:Tweaked computer/peripheral
  discovery path and invokes the Lua-visible contract end to end.
- Retain direct Java/public-API tests where they isolate error tables,
  serialization, or topology more clearly, but label them as such in code.
- Ensure the real invocation test observes a result in-world or through a
  supported public API; it must not assert success merely because the provider
  was registered.

**Completion criteria:** The suite proves both that SFM implements the
contract and that CC:Tweaked exposes it to a real computer.

### [x] 5.3 Run the focused and full 1.19.2 gates

**Completion notes:** On 2026-07-13, `cargo run -- run compile --branch
1.19.2` passed, the `computer_craft_*` focused selector passed all seven CC
integration tests, and the unfiltered 1.19.2 GameTest server passed all 226
required tests.

**Validation:**

```pwsh
cargo run -- run compile --branch 1.19.2
cargo run -- game-test run-server --branch 1.19.2 --filter computer_craft_dependency_smoke
cargo run -- game-test run-server --branch 1.19.2 --filter 'computer_craft_*'
cargo run -- game-test run-server --branch 1.19.2
```

**Completion criteria:** The compile, dependency smoke, focused integration,
and full GameTest runs pass from the current CLI source tree. Any test filter
renames are recorded here with the command that replaces them.

## Phase 6: Document, propagate, and release

### [x] 6.1 Document the player and Lua contract

**Completion notes:** Added `docs/cc tweaked integration.md` with cable setup,
the `sfm_network` contract, item-detail schema, intentional read-only scope,
turtle behavior, and tested Lua examples. Added the player-visible feature to
`changelog.sfml` on 1.19.2.

**Work:**

- Add user-facing documentation with setup topology, peripheral type(s), Lua
  examples, returned tables, mutation/error behavior, and the exact supported
  CC:Tweaked/SFM versions.
- Document any intentional first-release exclusions, especially read-only
  label/program data or ambiguous cable networks.
- Update the in-game changelog once gameplay-visible behavior exists.

**Completion criteria:** A player can build a supported topology and write a
Lua program without reading Java source or guessing at item NBT.

### [x] 6.2 Propagate the completed 1.19.2 feature forward

**Completion notes:** Completed on 2026-07-13. The 1.19.2 implementation,
documentation, and CLI cache-correctness changes were committed deliberately,
then propagated oldest-first with `sfm-propagate-changes.exe git merge`. The
shared commits are `6ff7db197` (source excludes participate in the Java
compiler cache fingerprint), `025dfeab3` (availability and turtle-adapter
documentation), and `7c75a93fb` (refuse an interactive merge prompt when
stdin/stdout is not a terminal). The last change prevents an automated merge
from hanging on `stdin.read_line` as happened during this propagation.

Version-specific commits retain the current branch's tested API behavior:
`16f88cc16` updates 1.19.4 to Forge 45.0.42 for CC:Tweaked 1.108.0;
`10ed5b25a`, `7d76dcb0d`, `771d2d98e`, `d0232c420`, `b25fd5248`, and
`6af32cbc9` carry the required version adaptations through 26.1.2. Conflicts
were resolved by retaining each newer branch's loader/version behavior and
grafting the shared intent. The later NeoForge adapters are explicitly
`@MCVersionDependentBehaviour`; they expose a turtle's public Minecraft
`Container` through the loader item-handler wrapper without importing a
CC:Tweaked internal class.

CC:Tweaked source is retained in every version branch. The source-exclude
lists select compilation/runtime availability for 1.20.2, 1.20.3, 1.21.0, and
26.1.2 rather than deleting the integration from those histories. The
post-propagation version-surface audit exited successfully with zero
later-branch CLI warnings. Its remaining Java warnings pre-date this feature
and are recorded baseline version-surface debt.

**Work:**

- Commit the 1.19.2 implementation and its tests deliberately.
- Propagate oldest-first with `sfm-propagate-changes.exe git merge` according
  to `docs/AGENTS.md`.
- Resolve each conflict by retaining newer-branch behavior and grafting the
  integration's intent. Add a narrow
  `@MCVersionDependentBehaviour` adapter only where the Minecraft/Forge/NeoForge
  API genuinely differs.
- Run the version-surface audit before and after propagation:

```pwsh
cargo run -- audit --branch core --version-surfaces
```

**Completion criteria:** Every supported branch has the documented
integration, its version-specific code is bounded by explicit adapters, and
the CLI has no later-branch-only change.

### [x] 6.3 Run cross-version acceptance

**Completion notes:** Completed on 2026-07-13 after propagation. The project
CLI successfully ran `run compile --branch core --parallel=3 --error-action
continue`, compiling every core branch. The focused `computer_craft_*`
GameTest selector passed on the active integration lines: 1.19.2 (CC:Tweaked
1.101.3), 1.19.4 (1.108.0 with Forge 45.0.42), 1.20 (1.105.0), 1.20.1
(1.111.0), 1.20.4 (1.110.2), and 1.21.1 (1.113.1). This includes real Lua
invocation, item details, topology, and turtle transfer coverage.

No CC GameTest was run on the deliberately unavailable lines. 1.20.2,
1.20.3, and 26.1.2 have no compatible locked runtime; their retained source is
excluded. 1.21.0's sole published CC:Tweaked 1.111.0 runtime is incompatible
with NeoForge 21.0.143, so its retained source and GameTests are excluded and
the dependency is compile-only. The successful compile gate proves those
exclusions leave the SFM branch buildable. The exact availability table is in
`docs/cc tweaked integration.md`.

**Work:**

- Run compile on every supported branch.
- Run the CC:Tweaked dependency smoke and focused functional GameTests on each
  branch whose locked CC:Tweaked version supports the selected public API.
- For a branch requiring an adapter, add the smallest additional test needed
  to prove the adapter rather than weakening the common assertion.
- Record branch heads, test commands, and any intentionally unsupported
  branch/version combination in the relevant completion notes.

**Completion criteria:** The advertised CC:Tweaked behavior is validated on
every supported SFM branch or is explicitly and accurately scoped in docs.

## Overall completion criteria

This plan is complete only when:

- Every work item above is marked `[x]` with completion notes.
- The manager/cable topology and full Lua contract are written before their
  implementation starts.
- Production integration imports only the locked CC:Tweaked public API.
- Manager program and label behavior uses existing SFM lifecycle boundaries;
  no raw-NBT bypass exists.
- Item details are stable, read-only by default, bounded, and documented.
- Any write operation is explicit, validated, persistent, and covered by a
  success and failure GameTest.
- A live CC:Tweaked computer can discover and invoke an SFM peripheral through
  the supported topology.
- SFM can discover and transfer items with a live turtle inventory over an SFM
  cable network, with a disconnected negative case.
- The dependency smoke, focused integration tests, full GameTest suite, and
  required cross-version validation pass.
- Player/Lua documentation and the gameplay changelog describe exactly what
  shipped.

## Risk register

### Ambiguous cable ownership

An SFM cable network can contain or touch more than one manager. Returning an
arbitrary manager would make peripheral identity depend on traversal order and
could grant access to the wrong factory. The topology decision and
zero/one/multiple-manager GameTests are mandatory before exposing cable
peripherals.

### API versus implementation coupling

The current CC:Tweaked source tree contains tempting `shared` implementation
classes, including computer, turtle, and disk-drive internals. They are not a
stable dependency contract. The API-only import rule and source review prevent
an integration that breaks on a routine CC:Tweaked update.

### Program and item-data corruption

Disk program text, label storage, printing-form references, and diagnostics
are NBT-backed in this Minecraft version. Direct mutation can skip compilation,
warnings, state refresh, or persistence. The implementation must route through
SFM domain methods and prove rejected writes leave state unchanged.

### GameTest false confidence

Placing a turtle proves only that CC:Tweaked loaded. Directly instantiating an
adapter proves only SFM logic. At least one test must use real peripheral
discovery/invocation, and the turtle test must execute an actual manager
transfer over cables.

### Version propagation drift

Minecraft, Forge/NeoForge, and CC:Tweaked APIs evolve across SFM branches.
Implement once on 1.19.2, propagate deliberately, bound genuine differences
behind `@MCVersionDependentBehaviour`, and run the version-surface audit rather
than allowing independent later-branch integration changes.
