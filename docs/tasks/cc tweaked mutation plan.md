# CC:Tweaked Mutable Handles and Turtle Labeler Plan

**Plan status:** Implemented on `1.19.2`; validation and forward propagation pending.

## Goal

Replace the original read-only CC:Tweaked table projection with mutable,
structured disk and label-gun handles, and add an SFM label-gun turtle upgrade
that uses the turtle's selected inventory item.

## Implemented contract

- `sfm_network.getManagers()` returns a live manager collection with
  one-indexed `count()` and `get(index)` methods. Manager handles expose
  position, state, and disk acquisition and reject disconnected retained
  handles with `manager_unreachable`.
- Disks and label guns are acquired through reusable item-backed handles from
  manager disks, ordinary CC inventory peripherals, and turtle selected slots.
  Label editors are owned `LabelPositionHolder` sessions with explicit,
  last-writer-wins `save()`; they use count/index access rather than a full Lua
  table and preserve SFM's native 256-character label limit.
- Disk writes retain invalid source and diagnostics, reporting
  `true, "invalid_program"`; other rejected mutations report stable error
  codes. Manager disk writes use the normal manager rebuild lifecycle.
- `detail.sfm`, its serializer, and its item-detail provider are removed.
- A blank SFM label gun is the `sfm_labeler` turtle upgrade item. Runtime
  actions use selected inventory label guns and CC:Tweaked's command queue for
  player-equivalent toggle, clear-active, clear-all, pick, push, and pull
  actions at `front`, `up`, or `down`.

## Acceptance work

- Compile 1.19.2 and run the focused CC:Tweaked tests, including real Lua
  computer and turtle tests, then the full 1.19.2 GameTest suite.
- Run the version-surface audit before and after propagation. Propagate
  oldest-first with `sfm-propagate-changes.exe git merge`, preserving newer
  branch behavior and adding `@MCVersionDependentBehaviour` adapters only for
  real loader/API differences.
- Compile every core branch and run focused CC GameTests only on branches that
  package a compatible locked CC:Tweaked runtime.
