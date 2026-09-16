# Redstone support in SFM

Reviewed on 16 September 2026. Implementation target: Minecraft 1.19.2.
This is an implementation checkpoint, not a published-release announcement.
Local integration checkpoint: `57a9cfb56` on branch `1.19.2`.

SFM can now read live redstone signals from labelled blocks and use experimental
resource buffers as persistent counters. These features reuse existing SFML
conditions and transfers. They do not add function calls or new trigger syntax.

## What is available

| Surface | Behavior in this checkpoint | Status |
| --- | --- | --- |
| `IF REDSTONE GE 2 THEN` | Reads incoming neighbour power at the manager. | Existing behavior, preserved. |
| `EVERY REDSTONE PULSE DO` | Runs through the existing manager-local pulse mechanism. | Existing behavior, preserved. |
| `IF abc HAS GT 0 redstone:: THEN` | Reads the labelled block's emitted signal. | Live reads, side selection, and label handling implemented and tested. |
| `INPUT 1 redstone:: FROM donor` followed by `OUTPUT redstone:: TO counter` | Transfers stored units between resource buffers. | Implemented and tested; buffers remain experimental. |
| Comparator beside a resource buffer | Reads the stored count, capped at 15. | Implemented and tested. |

`redstone::` is a resource type. It is distinct from the redstone dust item,
which can be selected as `item:minecraft:redstone`. Its meaning depends on the
endpoint: a world block exposes a read-only signal, while a buffer exposes stored
units. Reading a signal does not consume it or create transferable units.

## Read a labelled signal

Connect the signal source to the manager's cable network, label it `abc`, and
push the labels to the manager. Label the source and destination inventories
`a` and `b`.

```sfml
EVERY 20 TICKS DO
    IF abc HAS GT 0 redstone:: THEN
        INPUT FROM a
        OUTPUT TO b
    END
END
```

This polls the labelled source each time the timer runs. It does not subscribe
to edges or guarantee that a short pulse between samples will be observed.
The manager itself can remain unpowered.

### Signal meaning and sides

World queries read the block's own emitted weak-signal value through Minecraft's
`getSignal` API, bounded to 0 through 15. They do not measure incoming neighbour
power or a separate strong/direct-signal channel. An ordinary powered stone
block does not become a signal source under this contract.

| Query | Meaning |
| --- | --- |
| `abc HAS GE 8 redstone::` | Strongest emitted signal across the block's 6 faces. |
| `abc NULL SIDE HAS GE 8 redstone::` | Same strongest-face behavior. |
| `abc EAST SIDE HAS GE 8 redstone::` | Signal emitted through the labelled block's east face. |
| `abc FRONT SIDE HAS GE 8 redstone::` | Uses SFML's existing block-facing resolution, then reads that face. |

Explicit sides name the source's outward face. The provider converts that face
to Minecraft's opposite query direction. Relative sides retain existing SFML
rules: if a relative side cannot resolve, it becomes `NULL`, so this query uses
the strongest face.

Cached handlers read current block state and block-entity output. A comparator's
output can change without replacing its block state, and the next query observes
that change. Lever toggling and replacing or removing a world signal source are
also covered by tests.

A comparator is a world signal source. To measure an inventory's comparator
output through this interface, label the actual comparator. Labelled reads do
not add generic inventory-fullness queries to arbitrary blocks.

### Multiple labels and faces

Existing `HAS` aggregation still applies:

```sfml
IF SOME signals HAS GT 0 redstone:: THEN
    -- at least one labelled source meets the condition
END

IF EVERY signals HAS EQ 15 redstone:: THEN
    -- each labelled source meets the condition
END

IF OVERALL signals HAS EQ 30 redstone:: THEN
    -- for example, two sources each emitting 15
END
```

`OVERALL` sums resource quantities; it does not describe one physical signal
above 15. `EACH SIDE` can count several readings from the same block, including
the `NULL` strongest-face reading. Omit sides when you want one reading per block.

Signal sources remain valid labels and fancy-cable endpoints while switched
off. Ordinary stone and air do not become inventories merely because the world
provider can report zero there. Filled redstone buffers also remain valid
endpoints when their other resource capabilities are unavailable.

Disconnected sources cannot be reached through the manager's cable network.
The world handler returns zero when its chunk is unavailable. Actual chunk
unload/reload behavior remains a test gap; this implementation does not promise
to keep chunks loaded.

## Use a buffer as a counter

The existing resource buffer can store redstone units. `HAS` reads its exact
count, and transfers increase or decrease it. The current registered buffer's
redstone capacity is 2,147,483,647 units. The underlying handler honours the
buffer tier's capacity within that integer limit.

A buffer's comparator output is `min(stored count, 15)`. For example, 2 units
produce strength 2, and both 15 and 37 units produce strength 15. SFML can still
distinguish those exact quantities. The comparator provides the emitted signal;
the buffer does not gain a direct redstone emitter on every face.

### Run the counter example

Buffers are still experimental. Their item registration remains development-only,
and this checkpoint does not add a production recipe or resolve their loot-table
reports. In a developer world, commands can place and initialise them:

```text
/setblock <x> <y> <z> sfm:buffer{redstone:2}
/setblock <x> <y> <z> sfm:buffer
```

Use the first command for a donor with 2 units. Use the second at two other
positions for an empty counter and sink. Connect all three to the manager and
apply the labels `donor`, `counter`, and `sink`. Put a comparator against the
counter, pointing away from it.

```sfml
EVERY 20 TICKS DO
    IF donor HAS GT 0 redstone:: THEN
        INPUT 1 redstone:: FROM donor
        OUTPUT redstone:: TO counter
    ELSE
        INPUT 1 redstone:: FROM counter
        OUTPUT redstone:: TO sink
    END
END
```

The example produces this sequence:

| Completed executions | Donor | Counter | Sink | Comparator |
| --- | ---: | ---: | ---: | ---: |
| 0 | 2 | 0 | 0 | 0 |
| 1 | 1 | 1 | 0 | 1 |
| 2 | 0 | 2 | 0 | 2 |
| 3 | 0 | 1 | 1 | 1 |
| 4 | 0 | 0 | 2 | 0 |

After that, the counter stays empty. Units are conserved between the buffers.
A full destination accepts only the units it can hold; the rest stay at the
source. Simulated operations do not change the count, resource icon, or notify
the owner as though a transfer happened. Cached handlers cannot insert redstone
while another resource occupies the buffer.

Write `redstone::` on both the input and output. An unqualified `OUTPUT TO`
defaults to items and will not move these units.

The [shipped counter example](../platform/minecraft/src/main/resources/assets/sfm/template_programs/buffer_redstone_counter.sfml)
is executed directly by the sequence GameTest. The
[signal examples](../platform/minecraft/src/main/resources/assets/sfm/template_programs/redstone_signals.sfml)
also retain the existing manager-local examples.

### Persistence and notifications

Buffer block entities save the count in the integer NBT field `redstone` and
restore it on load. A missing or invalid field means zero. Negative values are
clamped to zero, and oversized numeric values are bounded by storage capacity.
Loading into a live buffer preserves the identity of its existing storage handle.

Real insertions and extractions mark the block entity changed. Minecraft then
updates neighbouring comparators. Emptying, loading a different quantity, and
removing the buffer update the comparator too.

The tests cover NBT serialisation, construction through the registered block-entity
loader, replacement in the world, and live NBT reload. They do not yet cover a
complete process restart or chunk-unload cycle. Persistence for other experimental
buffer resource types is outside this checkpoint.

## Open GitHub issues

The following states were checked through GitHub on 16 September 2026. Coverage
here describes local implementation and tests; it does not imply a released fix
or an issue closure.

### Direct redstone requests

| Issue | State | Relationship to this checkpoint |
| --- | --- | --- |
| [#108 — Feature Request: Redstone querying](https://github.com/TeamDman/SuperFactoryManager/issues/108) | Open | Implements labelled emitted-signal queries using `HAS ... redstone::`. Vanilla signal fixtures pass. The requested AE2 level-emitter use case still needs a dedicated compatibility test. |
| [#591 — Add game tests for redstone resource type](https://github.com/TeamDman/SuperFactoryManager/issues/591) | Open | Adds runtime coverage for all strengths 0 through 15, followed by sides, freshness, discovery, and scheduled use. This directly covers the requested test work on 1.19.2. |
| [#229 — Support Redstone querying and Restone output](https://github.com/TeamDman/SuperFactoryManager/issues/229) | Open | Reads are covered. Buffer transfers and comparator readout provide one output path. Arbitrary world-signal writes remain absent. |
| [#170 — Add two functions](https://github.com/TeamDman/SuperFactoryManager/issues/170) | Open | Partially covers its redstone request. Its separate request to select members of a label group by state is unchanged. |
| [#465 — Tunnelled manager should also pass through redstone queries](https://github.com/TeamDman/SuperFactoryManager/issues/465) | Open | Tunnel forwarding remains unverified and has no new contract here. Comparator forwarding and strong/weak behavior raised in its comments remain follow-up work. |
| [#129 — Controlling factory manager with redstone signal](https://github.com/TeamDman/SuperFactoryManager/issues/129) | Open | Conditions can guard actions, but this does not introduce a disabled manager state or guarantee reduced evaluation cost. |

The [resource-syntax comment on #108](https://github.com/TeamDman/SuperFactoryManager/issues/108#issuecomment-2623019805)
proposed reusing `HAS` with `redstone::`. A
[later naming comment](https://github.com/TeamDman/SuperFactoryManager/issues/108#issuecomment-3023680061)
raised confusion with dust items. This checkpoint retains the registered name
and documents the distinction explicitly.

### Related limits and defects

| Issue | State | Remaining work |
| --- | --- | --- |
| [#583 — Buffer loot table references an unregistered item](https://github.com/TeamDman/SuperFactoryManager/issues/583) | Open | Resolve production item/drop policy. Counter support does not fix the startup loot-table error. |
| [#530 — Buffer block loot table](https://github.com/TeamDman/SuperFactoryManager/issues/530), [#568 — Buffer loot-table parse failure](https://github.com/TeamDman/SuperFactoryManager/issues/568) | Both open | Related reports of the same unavailable item reference; coordinate with #583. |
| [#522 — Manager loses labelled block locations after rejoining](https://github.com/TeamDman/SuperFactoryManager/issues/522) | Open | Separate network/reload report. The new NBT tests do not establish a fix for it. |
| [#597 — Test a manager removing its own program disk](https://github.com/TeamDman/SuperFactoryManager/issues/597) | Open | Separate lifecycle/control-flow work, relevant to future disable and event semantics. |

The historical [#430 — Fancy cables connect to air](https://github.com/TeamDman/SuperFactoryManager/issues/430)
is closed. New label tests preserve the distinction between actual signal sources
and arbitrary positions; this is regression context, not another open issue.

## GitHub discussions and design decisions

These discussions were open when reviewed on 16 September 2026. The search covered
the repository's 67 discussions; the directly relevant redstone bodies and replies
were read. Linked Discord conversations are background references; their contents
were not independently reviewed for this document.

| Discussion | Relationship to the implementation |
| --- | --- |
| [#613 — Redstone support summary](https://github.com/TeamDman/SuperFactoryManager/discussions/613) | Maintained design index with 8 decision gates. Its 14 September source-only review predates this tested checkpoint. Use this document for the newer implementation evidence. |
| [#202 — Redstone as a resource](https://github.com/TeamDman/SuperFactoryManager/discussions/202) | Proposes typed units, counters, world sources/sinks, change triggers, and re-entry limits. Stored counter transfers are implemented; source/sink creation and reactive triggers remain proposals. |
| [#400 — Add redstone sidedness](https://github.com/TeamDman/SuperFactoryManager/discussions/400) | Labelled face reads now use existing label-access syntax. Proposed manager-side conditions, labelled pulse triggers, and cable event subscriptions are not introduced. |
| [#407 — Add crossover buffer](https://github.com/TeamDman/SuperFactoryManager/discussions/407) | Proposes 3 independent internal buffers for opposite face pairs. The current counter uses one shared store across sides. |
| [#614 — Terraform for Minecraft](https://github.com/TeamDman/SuperFactoryManager/discussions/614) | Broader module, graph, builder, and code/world translation design. Redstone reads and stored counters are foundations for that later work. |

Discussion #202 proposed a separate redstone-buffer block, 64-bit capacity, and
output capped at 14. This checkpoint instead uses the existing generic buffer,
32-bit bounded storage, and a comparator capped at 15. It has no percentage-full
output mode. Those are explicit implementation choices, not claims that the
historical proposal was implemented in full.

Discussion #400's [cable-observation reply](https://github.com/TeamDman/SuperFactoryManager/discussions/400#discussioncomment-14680246)
suggests reacting to redstone without making cables block entities. Current
timer-based reads do not implement that event mechanism.

### Status of the gates in discussion 613

| Gate | Decision or remaining gap |
| --- | --- |
| R1 — Observation | Emitted weak signals chosen for world `redstone::` reads; incoming power and strong/direct queries remain distinct future surfaces. |
| R2 — Address and side | Labels, physical faces, strongest-face defaults, and existing relative-side resolution are specified and tested. Actual chunk unload/reload remains unverified. |
| R3 — Groups | Existing `SOME`, `EVERY`, and `OVERALL` semantics retained. Explicit sums may exceed 15; a single physical signal cannot. |
| R4 — Events | No labelled edges, change subscriptions, or new re-entry policy. |
| R5 — Output | Buffer comparator output is implemented. General emitters, set/reset lifetime, and competing-writer policy remain open. |
| R6 — Stored counters | Exact stored counts, bounded transfers, comparator readout, and redstone NBT persistence implemented in experimental buffers. |
| R7 — Tunnels | No forwarding guarantee added; define and test endpoint and side behavior. |
| R8 — Manager control | Existing manager-local conditions remain; no new disabled state or scheduling-cost guarantee. |

The broader design also references [functions #146](https://github.com/TeamDman/SuperFactoryManager/issues/146),
[arithmetic and count functions #363](https://github.com/TeamDman/SuperFactoryManager/issues/363),
and [resource transactions #436](https://github.com/TeamDman/SuperFactoryManager/issues/436),
all open when checked. [Sequencing and state machines #492](https://github.com/TeamDman/SuperFactoryManager/discussions/492)
and [stopwatch observations #491](https://github.com/TeamDman/SuperFactoryManager/discussions/491)
are related timing proposals. None is delivered by the counter implementation.

## Validation and source map

The checkpoint passed all 61 required GameTests in both a dedicated server and
an integrated client on 16 September 2026, after integrating current main.
The original feature also passed the client suite on 15 September.

| Group | Cases | Evidence covered |
| --- | ---: | --- |
| `redstone_query_*` | 29 | Strengths 0 through 15, all 6 physical faces, relative faces, cached changes, source replacement/removal, groups, label cleanup, read-only world IO, template compilation, and scheduled item routing. |
| `buffer_redstone_*` | 29 | Counter sequence, quantities above 15, capacity/remainders, overflow and simulation, resource exclusion/switching, comparator strengths and removal, NBT restoration, labels, and rejecting world transfers. |
| Existing regressions | 3 | `circle_redstone`, `side_resolve_direction`, and `move_1_stack_direct`. |

The [checkpoint integration plan](tasks/redstone%20checkpoint%20integration%20plan.md)
records validation against the current main branch and the merge result. The
earlier [labelled-query plan](tasks/labeled%20redstone%20queries%20plan.md) and
[buffer-counter plan](tasks/buffer%20redstone%20counter%20plan.md) retain the original
implementation evidence. Main's existing client-registration fix resolved the
original feature base's dedicated-server startup failure. Both final runs exited
successfully against integration commit `b08513e01`; later checkpoint changes
only update documentation.

Run the focused suite from a checkout containing this checkpoint:

```powershell
sfm-propagate-changes.exe game-test run-client --branch 1.19.2 --filter 'buffer_redstone_*,redstone_query_*,circle_redstone,side_resolve_direction,move_1_stack_direct' --keep-open
```

Omit `--keep-open` for an automated run. Replace `run-client` with `run-server`
and omit `--keep-open` to run the dedicated-server suite. Use the repository CLI;
older discussions contain historical Gradle commands that no longer match the
repository workflow.

| Source | Responsibility |
| --- | --- |
| [RedstoneSignalCapabilityProvider](../platform/minecraft/src/main/java/ca/teamdman/sfm/common/capability/RedstoneSignalCapabilityProvider.java) | Live, read-only world reads; storage takes precedence over the world fallback. |
| [RedstoneResourceType](../platform/minecraft/src/main/java/ca/teamdman/sfm/common/resourcetype/RedstoneResourceType.java) and [RedstoneSignalStorage](../platform/minecraft/src/main/java/ca/teamdman/sfm/common/capability/RedstoneSignalStorage.java) | Resource IO, capacity, simulation, and mutable stored quantities. |
| [BufferBlock](../platform/minecraft/src/main/java/ca/teamdman/sfm/common/block/BufferBlock.java), [BufferBlockEntity](../platform/minecraft/src/main/java/ca/teamdman/sfm/common/blockentity/BufferBlockEntity.java), and [contents](../platform/minecraft/src/main/java/ca/teamdman/sfm/common/blockentity/BufferBlockEntityContents.java) | Comparator output, persistence, notifications, and resource ownership. |
| [SFMBlockCapabilityDiscovery](../platform/minecraft/src/main/java/ca/teamdman/sfm/common/capability/SFMBlockCapabilityDiscovery.java) | Label and cable eligibility for signal sources and buffers. |
| [World-query tests](../platform/minecraft/src/gametest/java/ca/teamdman/sfm/gametest/tests/general/RedstoneQueryGameTestGenerator.java) and [buffer tests](../platform/minecraft/src/gametest/java/ca/teamdman/sfm/gametest/tests/general/BufferRedstoneGameTestGenerator.java) | Executable acceptance cases. |

## Remaining work

The next bounded steps are:

1. Define in-game sources and sinks for stored units, including whether reading
   a redstone block may create units and whether output may discard them.
2. Decide production buffer availability, recipes, drops, and persistence for its
   other accepted resource types.
3. Test actual chunk/world lifecycle, modded emitters, and tunnel forwarding.
4. Specify labelled events and any general signal-output or manager-disable
   feature before adding their syntax.
5. Propagate and validate supported Minecraft versions before publishing a release.

The bidirectional 3D representation remains separate exploration. This checkpoint
preserves observable signals and persistent counters without committing that
future design to a particular function, module, or world-layout model.
