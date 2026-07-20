# CC:Tweaked Mutable Handles and Turtle Labeler Plan

**Plan status:** Completed 2026-07-14; forward-propagated and validated on every maintained branch.

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
  manager disks and ordinary CC inventory peripherals. The turtle upgrade is a
  flat `sfm` controller whose disk, label-gun, and label methods act on its
  selected slot.
  Label editors are owned `LabelPositionHolder` sessions with explicit,
  last-writer-wins `save()`; they use count/index access rather than a full Lua
  table and preserve SFM's native 256-character label limit.
- Disk writes retain invalid source and diagnostics, reporting
  `true, "invalid_program"`; other rejected mutations report stable error
  codes. Manager disk writes use the normal manager rebuild lifecycle.
- `detail.sfm`, its serializer, and its item-detail provider are removed.
- A blank SFM label gun is the `sfm:labeler` turtle upgrade item and exposes
  peripheral type `sfm`. Runtime
  actions use selected inventory label guns and CC:Tweaked's command queue for
  player-equivalent toggle, clear-active, clear-all, pick, push, and pull
  actions at `front`, `up`, or `down`. Lua can also inspect contiguous target
  discovery and compose those immutable position sets with bulk label-editor
  additions and removals on selected disks or label guns.

## Acceptance work

- Compile 1.19.2 and run the focused CC:Tweaked tests, including real Lua
  computer and turtle tests, then the full 1.19.2 GameTest suite.
- Run the version-surface audit before and after propagation. Propagate
  oldest-first with `sfm-propagate-changes.exe git merge`, preserving newer
  branch behavior and adding `@MCVersionDependentBehaviour` adapters only for
  real loader/API differences.
- Compile every core branch and run focused CC GameTests only on branches that
  package a compatible locked CC:Tweaked runtime.

## Completion evidence

- Every core branch compiles: 1.19.2, 1.19.4, 1.20, 1.20.1, 1.20.2, 1.20.3,
  1.20.4, 1.21.0, 1.21.1, and 26.1.2.
- Focused real-mod CC:Tweaked GameTests pass on each supported runtime: 9 tests
  on 1.19.2, 1.19.4, 1.20, and 1.20.1; 8 tests on 1.20.4 and 1.21.1. The full
  1.19.2 suite also passed all 228 required tests.
- 1.20.2, 1.20.3, 1.21.0, and 26.1.2 retain CC source but exclude it from the
  toolchain because no compatible locked runtime is available.
- `SFM.registerComputerCraftTurtleUpgrades()` is the sole bootstrap variation.
  Its `@MCVersionDependentBehaviour` body registers the upgrade on supported
  runtimes and is deliberately empty on source-excluded versions.
- The final version-surface audit exits successfully with zero CLI warnings.
