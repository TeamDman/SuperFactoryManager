# CC:Tweaked integration (1.19.2)

SFM 1.19.2 supports a read-only CC:Tweaked view of an SFM cable network and
adds SFM data to CC:Tweaked detailed item queries. The integration is optional:
SFM continues to load normally when CC:Tweaked is not installed.

This document describes the 1.19.2 contract tested with CC:Tweaked 1.101.3.

## Cable-network peripheral

Place a normal computer directly against an SFM cable or manager block. A
manager is also a member of its cable network, so either is a valid entrypoint.
The adjacent side exposes a peripheral of type `sfm_network`.

```lua
local network = assert(peripheral.wrap("front"), "No SFM cable network on the front")
assert(peripheral.getType("front") == "sfm_network")

for index, manager in ipairs(network.getManagers()) do
  print(index, manager.position.x, manager.position.y, manager.position.z, manager.state)
end
```

`getManagers()` takes no arguments and returns a one-indexed Lua array of up
to the first sixteen currently loaded managers in that cable network. Its order
is deterministic by block position. `getManagerCount()` returns the total
loaded-manager count, so scripts can detect when `getManagers()` was capped.
An otherwise valid cable network with no managers returns an empty array and a
count of zero.

Each manager is represented as:

```lua
{
  position = { x = integer, y = integer, z = integer },
  state = "no_disk" | "no_program" | "invalid_program" | "running",
  disk = nil | {
    name = string,
    nameTruncated = boolean,
    program = string,
    programTruncated = boolean,
    labels = {
      [label_name] = {
        { x = integer, y = integer, z = integer },
        -- more positions in deterministic order
      }
    },
    labelsTruncated = boolean
  }
}
```

The peripheral resolves the cable network again on every call. It therefore
reflects cable splits and joins. If its touched cable is removed, a retained
peripheral object returns an empty manager list; a newly discovered peripheral
will normally be absent from that side.

This first release is deliberately read-only. It provides no Lua method to
write a program, add/remove labels, or edit SFM item NBT.

### Payload limits

To keep a peripheral call safe for a computer's Lua memory, `getManagers()`
returns at most sixteen managers. Each returned text field is at most 8,192
characters; its adjacent `*Truncated` field is `true` when a longer value was
cut. A label table contains at most sixteen labels with at most 64 positions
per label. Label names longer than 8,192 characters are omitted. In either
label case, `labelsTruncated` is `true`. The returned label and position order
remains deterministic.

## SFM item details

CC:Tweaked inventories and turtles expose the SFM table when callers request
detailed item data:

```lua
local chest = assert(peripheral.wrap("top"))
local detail = assert(chest.getItemDetail(1, true))
local sfm = detail.sfm
```

`detail.sfm` is present only for an SFM program disk, label gun, or printing
form. It never contains raw SFM NBT.

| Item | `detail.sfm` fields |
| --- | --- |
| Program disk | `kind = "program_disk"`, `name`, `nameTruncated`, `program`, `programTruncated`, `labels`, `labelsTruncated` |
| Label gun | `kind = "label_gun"`, `activeLabel`, `activeLabelTruncated`, `viewMode`, `labels`, `labelsTruncated` |
| Printing form | `kind = "printing_form"`, `reference = { name, count }` |

`labels` has the same label-to-position-table shape as the network peripheral.
The payload limits and truncation fields above apply to detailed items too.
`viewMode` is the lowercase enum name, such as
`"show_only_targeted_block"`. A form without a valid stored reference returns
an empty `reference` table. Blank disks, forms, and label guns return their
default data without creating NBT or changing the item.

## Turtle inventories

CC:Tweaked turtles expose their ordinary Forge item-handler capability. No
special SFM adapter is necessary: place a turtle directly next to an SFM cable,
label it on the manager disk as usual, and use it as an SFM input or output.
Disconnected turtles are not reachable through the cable network.

## Tested examples

The GameTest suite proves all of the following against a live CC:Tweaked
installation:

- a real computer runs `peripheral.wrap("front")`, calls `getManagers()`, and
  reads program/label data;
- the same computer queries disk, label-gun, and printing-form data through a
  real chest peripheral's `getItemDetail(slot, true)` call;
- manager and cable entrypoints, empty networks, cable splits/rejoins, and
  entry-cable removal follow the contract above; and
- oversized program/label data is bounded and marked, malformed stored label
  data is ignored safely, and an over-limit cable network reports its full
  manager count while bounding the returned manager table; and
- SFM moves items through a cable network into a live turtle while leaving a
  disconnected turtle untouched.
