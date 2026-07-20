# CC:Tweaked integration (1.19.2)

SFM's optional CC:Tweaked integration exposes mutable, structured handles for
SFM program disks and label guns. It does not expose raw SFM NBT or return a
large snapshot table for a network's managers or labels.

This contract is tested against CC:Tweaked 1.101.3 on Minecraft 1.19.2.

## Availability on maintained SFM versions

The source remains in every maintained branch. A version-specific source
exclude controls whether a branch packages it, preserving the history needed
for forward merges.

| Minecraft version | CC:Tweaked version | Status |
| --- | --- | --- |
| 1.19.2 | 1.101.3 | Supported. |
| 1.19.4 | 1.108.0 | Supported with Forge 45.0.42. |
| 1.20 | 1.105.0 | Supported. |
| 1.20.1 | 1.111.0 | Supported. |
| 1.20.4 | 1.110.2 | Supported. |
| 1.21.1 | 1.113.1 | Supported. |
| 1.20.2, 1.20.3, 1.21.0, 26.1.2 | — | Source retained but not packaged because no compatible locked runtime is available. |

## Cable-network managers

Touch a computer to an SFM cable or manager. The side has a peripheral of type
`sfm_network`.

```lua
local network = assert(peripheral.wrap("front"))
assert(peripheral.getType("front") == "sfm_network")

local managers = network.getManagers()
for index = 1, managers.count() do
  local manager = assert(managers.get(index))
  local x, y, z = manager.position()
  print(index, x, y, z, manager.state())
end
```

`getManagers()` returns a live collection object, not an array table.
`count()` and `get(index)` are one-indexed and deterministic by block
position. A manager exposes:

- `position()` → `x, y, z`
- `state()` → `no_disk`, `no_program`, `invalid_program`, or `running`
- `disk()` → a disk handle. Its first read or write returns `nil, "no_disk"` or
  `false, "no_disk"` if the manager has no disk at that point.

The manager handle is tied to the cable the computer originally touched.
Every operation rechecks topology. After a split, a retained manager or disk
handle returns `nil, "manager_unreachable"` for reads or
`false, "manager_unreachable"` for writes and makes no change.

## Disk, gun, and label handles

Normal CC:Tweaked inventory peripherals gain these one-indexed methods:

```lua
local chest = assert(peripheral.wrap("top"))
local disk = assert(chest.getSfmDisk(1))
local gun = assert(chest.getSfmLabelGun(2))
```

The factory methods return a handle immediately so CC:Tweaked preserves it as
an object rather than serialising it through a main-thread result. Its first
read or write confirms that the selected slot contains the required item; a
wrong item reports `not_disk` or `not_label_gun`. The handle then stays
attached to that exact item stack. If its slot is replaced, a later read
returns `nil, "target_changed"` and a write returns `false, "target_changed"`.

Disk handles provide:

```lua
local source = disk.getProgram()
local ok, status = disk.setProgram('NAME "Example"')
-- `status` is "invalid_program" when the source was stored but did not compile.
local labels = assert(disk.labels())
```

`setProgram` always stores the requested source and refreshes normal SFM
diagnostics. Valid source returns `true`; invalid source returns
`true, "invalid_program"`. A manager disk additionally follows the manager's
normal state, lint, synchronization, and persistence lifecycle.

Label-gun handles provide `getActiveLabel()`, `setActiveLabel(label)`,
`clearActiveLabel()`, `getViewMode()`, `setViewMode(view_mode)`, and
`labels()`. View-mode values are `show_all`,
`show_only_active_label_and_targeted_block`, and
`show_only_targeted_block`.

`labels()` returns an owned `LabelPositionHolder` editor. It is deliberately
not a Lua table:

```lua
local labels = assert(disk.labels())
for i = 1, labels.labelCount() do
  local name = labels.labelName(i)
  for position = 1, labels.positionCount(name) do
    local x, y, z = labels.position(name, position)
    print(name, x, y, z)
  end
end

assert(labels.add("ore", 10, 64, 10))
assert(labels.remove("ore", 10, 64, 10))
assert(labels.removeLabel("old_name"))
assert(labels.clear())
assert(labels.save())
```

The editor also has `contains(label, x, y, z)`. Reads and edits operate on its
owned snapshot. Only `save()` writes it back, replacing the source's complete
label holder with last-writer-wins semantics. There is no CC-specific paging,
label count, or positions-per-label limit. Label names must be nonblank and no
longer than SFM's native 256-character limit.

Mutations return `true` on success or `false, <code>` when rejected. Common
codes are `invalid_label`, `invalid_view_mode`, `target_changed`,
`manager_unreachable`, `no_disk`, `not_label_gun`, `not_manager`, and
`invalid_direction`.

`getItemDetail(slot, true)` no longer includes `detail.sfm`. Printing forms
are intentionally outside the mutable CC surface.

## SFM turtle upgrade

An unmodified SFM label gun can be equipped as the `sfm:labeler` turtle
peripheral upgrade, which exposes the peripheral type `sfm`. It must be blank:
CC:Tweaked's normal upgrade suitability
check rejects a gun carrying SFM label-gun NBT, preventing that state from
being discarded during `turtle.equipLeft()` or `turtle.equipRight()`.

The upgrade is an editor/action tool; it does not store a label gun itself.
Put the actual label gun or disk in the turtle inventory and select it before
calling the peripheral.

The wrapped upgrade is one flat SFM controller. There are no nested `disk()`
or `labelGun()` methods. Program methods act on a selected disk, label-gun
state methods act on a selected label gun, and `labels()` edits either item.

```lua
local sfm = assert(peripheral.wrap("left"))
assert(peripheral.getType("left") == "sfm")

-- Discover the same contiguous, cable-adjacent targets as the player tool.
local discovery = sfm.discover("front", { contiguous = true })
local positions = discovery.positions()
local skipped = discovery.skippedPositions()
print("selected", positions.count(), "skipped", skipped.count())

-- Dynamic CC objects cannot retain Java identity when passed back as method
-- arguments, so explicitly transfer the immutable set as Lua position records.
local positionSet = positions.toTable()
local labels = sfm.labels()
assert(labels.addAll("furnaces", positionSet))
assert(labels.save())

assert(labels.removeAll("furnaces", positionSet))
assert(labels.save())

-- Select a disk to use the same controller and discovered positions on it.
turtle.select(2)
assert(sfm.setProgram('NAME "turtle disk"'))
local diskLabels = sfm.labels()
assert(diskLabels.addAll("furnaces", positionSet))
assert(diskLabels.save())
```

Position-set handles expose `count()`, one-indexed `position(index)`,
`contains(x, y, z)`, and `toTable()`. The transfer table contains one-indexed
`{ x = ..., y = ..., z = ... }` records. Label editors expose `addAll` and
`removeAll` in addition to their single-position operations. Edits remain an
owned snapshot until the explicit `save()` call.

The older action helpers remain available for selected label guns.
`toggle`, `clearActive`, `clearAll`, and `pick` accept `front`, `up`, or
`down`, plus an optional contiguous boolean. `push` and `pull` target a
manager in that direction. Actions execute through the turtle command queue,
so they remain ordered with turtle movement and use the selected inventory
slot when executed. Contiguous targeting uses exactly the same connected,
cable-adjacent block selection as the player label gun.

## GameTest coverage

The CC:Tweaked GameTests cover manager topology and stale handles, real Lua
network/inventory calls, no `detail.sfm` regression, native-size label reads,
invalid-program diagnostics, owned-session overwrite behavior, and a real
turtle's flat disk/gun API, inspectable contiguous discovery, bulk label
editing, command queue, and push/pull flow. Production
integration uses only `dan200.computercraft.api`; test fixtures may use
CC:Tweaked internals to boot a real computer or turtle.
