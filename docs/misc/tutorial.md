# Tutorial System (WIP)

This document describes the current implementation of the SFM tutorial feature in `1.19.2`.

## Overview

The tutorial feature provides isolated, generated tutorial lobbies inside a dedicated tutorial dimension.

- Players enter through `/sfm tutorial`.
- A lobby object is created with a unique numeric `LobbyId`.
- A starting tutorial chamber is rendered into the lobby.
- Players can be listed by lobby and chambers can be advanced by success command.

The system is intentionally modular so additional chambers can be added through a registry.

## Commands

The tutorial command tree is registered under `/sfm tutorial`.

### `/sfm tutorial`

Alias for `/sfm tutorial lobby create`.

### `/sfm tutorial leave`

Alias for `/sfm tutorial lobby leave`.

### `/sfm tutorial lobby create`

Creates a new lobby for the invoking player.

Behavior:

1. Requires a real player sender.
2. Fails if player inventory is not empty.
3. Resolves the tutorial dimension.
4. Chooses a room center that is separated from other players in the tutorial dimension:
	 - Collect other players in tutorial dimension.
	 - Build an XZ bounding box over their positions.
	 - Expand by `20_000` blocks in X and Z.
	 - Choose a random point on the perimeter.
5. Creates a `LobbyId` and in-memory lobby object.
6. Clears and regenerates base lobby room geometry.
7. Renders starting chamber into that lobby.
8. Teleports the player into the lobby.

### `/sfm tutorial lobby list`

Gamemaster-only command.

- Prints all active lobbies.
- Shows lobby id and online player names in that lobby.
- Uses colored chat components.

### `/sfm tutorial lobby leave`

- Requires a real player sender.
- Removes the player from their current tutorial lobby.
- Fails with a message if the player is not currently assigned to a tutorial lobby.

### `/sfm tutorial lobby chamber succeed <player>`

Marks the current chamber as completed for the specified player's current lobby.

- Looks up lobby by the target player assignment.
- Reads the current chamber definition.
- If chamber has a `nextChamberId`, renders it and updates lobby state.
- If no next chamber is defined, reports completion of final chamber.

This command is currently triggered by chamber-placed command blocks (pressure plate room).

### `/sfm tutorial lobby chamber restart`

- Can be run by a player or by a chamber command block.
- Player invocation resolves the sender's current tutorial lobby.
- Command-block invocation resolves lobby by command source position within tutorial lobby bounds.
- Before restart render, iterates tracked lobby players:
	- If player is not in tutorial dimension, removes them from lobby and does not modify inventory.
	- If player is in tutorial dimension, clears inventory.
- Re-renders the current chamber for the lobby.

## Lobby Model

### `LobbyId`

`LobbyId` is a newtype record wrapping a positive `int`.

- Validation: must be > 0.
- Created through an auto-incrementing counter in the lobby manager.

### `SFMTutorialLobby`

Each lobby stores:

- `LobbyId id`
- `BlockPos roomCenter`
- `BlockPos chamberOrigin`
- `Set<UUID> playerIds`
- `ResourceLocation currentChamberId`

### `SFMTutorialLobbyManager`

Static in-memory manager:

- `NEXT_ID` counter starts at 1.
- `LOBBIES_BY_ID` map.
- `PLAYER_TO_LOBBY` map.

Current lifecycle behavior:

- Creating a lobby removes player from any prior lobby.
- Lobby is deleted when its last tracked player is removed.
- When a player is removed/left, remaining lobby players are notified.
- Data is memory-only (not persisted across restart).

## Tutorial Dimension and World Rules

Tutorial dimension resources:

- `data/sfm/dimension/tutorial.json`
- `data/sfm/dimension_type/tutorial.json`

Key properties:

- Flat generator with no layers (void-like world).
- Biome set to plains.
- Fixed time at day.
- Bright ambient settings.

Runtime enforcement is done every tick in `TutorialWorldHandler`:

- Always day (`setDayTime(6000)`).
- Always clear weather (`setWeatherParameters(0, 0, false, false)`).
- Night vision effect for players in tutorial dimension:
	- No particles
	- No icon
	- Reapplied continuously

During chamber rendering, the chamber/lobby build volume is reset before placement:

- The full target volume is set to air first.
- All non-player entities in that volume are discarded (for example item frames, dropped items, mobs).

## Chamber Registry Architecture

Tutorial chambers use a custom Forge registry:

- Registry id: `sfm:tutorial_test_chamber`
- Registration class: `SFMTutorialTestChambers`
- Starting chamber id constant: `sfm:move_1_stack_direct`

### Base Definition

`SFMTutorialTestChamberDefinition` exposes:

- `run(SFMTutorialTestChamberHelper helper)`
- `@Nullable nextChamberId()` (default `null`)

This supports optional chamber progression.

### Chamber Helper

`SFMTutorialTestChamberHelper` provides a minimal subset of building utilities:

- Relative-to-absolute coordinate transform by origin.
- Block placement by relative positions.
- Block entity lookup.
- Item frame placement on walls.
- Command block placement + command string programming.
- Access to owning `LobbyId`.

When a chamber is rendered (initial start, restart, or progression), each player in the lobby receives:

- `You have entered chamber {chamber_id}`

## Starting Chamber: `move_1_stack_direct`

The current starting chamber models the move-one-stack scenario:

- Places a manager and two test barrels against the wall.
- Creates a disk item preloaded with:

	- `EVERY 20 TICKS DO`
	- `INPUT FROM a`
	- `OUTPUT TO b`

- Applies labels:
	- `a` -> left barrel
	- `b` -> right barrel
- Places the disk in an item frame on the wall above manager.

It also builds an exit/success room:

- Iron door in right-side wall (closed by default).
- Passage beyond door into small room.
- Command block under pressure plate.
- Command block executes:

	- `sfm tutorial lobby chamber succeed @p`

It also builds a reset control on the wall opposite the disk item frame:

- Stone wall button on the interior wall.
- Wall sign beside it using tutorial i18n text `"reset"`.
- Hidden command block just outside that wall.
- Button powers hidden command block to execute:

	- `sfm tutorial lobby chamber restart`

## Localization

Tutorial-localized strings are defined in `SFMTutorialLocalizationKeys`.

This includes messages for:

- Player/inventory prechecks
- Lobby created/list/empty
- Player-left-lobby notifications
- Lobby not found
- Chamber not found
- Chamber advanced/final completion
- Chamber reset sign text

`LocalizationKeys.getEntries()` now appends entries from `SFMTutorialLocalizationKeys`, so existing language datagen automatically includes tutorial keys without broader localization refactors.

## Current Limitations and Notes

- Lobby state is not persisted to disk.
- Only one chamber is currently registered (`move_1_stack_direct`).
- Chamber success criteria are currently external/manual via command block trigger.
- No automatic player re-assignment into existing nearby lobbies yet.
- No cleanup scheduler for stale lobbies beyond player-removal logic.

## Primary Source Files

- Command orchestration: `platform/minecraft/src/main/java/ca/teamdman/sfm/common/command/SFMTutorialCommand.java`
- Lobby model:
	- `platform/minecraft/src/main/java/ca/teamdman/sfm/common/tutorial/lobby/LobbyId.java`
	- `platform/minecraft/src/main/java/ca/teamdman/sfm/common/tutorial/lobby/SFMTutorialLobby.java`
	- `platform/minecraft/src/main/java/ca/teamdman/sfm/common/tutorial/lobby/SFMTutorialLobbyManager.java`
- Chambers:
	- `platform/minecraft/src/main/java/ca/teamdman/sfm/common/tutorial/chamber/SFMTutorialTestChamberDefinition.java`
	- `platform/minecraft/src/main/java/ca/teamdman/sfm/common/tutorial/chamber/SFMTutorialTestChamberHelper.java`
	- `platform/minecraft/src/main/java/ca/teamdman/sfm/common/tutorial/chamber/Move1StackDirectTutorialTestChamberDefinition.java`
- Registry:
	- `platform/minecraft/src/main/java/ca/teamdman/sfm/common/registry/registration/SFMTutorialTestChambers.java`
- World behavior:
	- `platform/minecraft/src/main/java/ca/teamdman/sfm/common/tutorial/SFMTutorialWorld.java`
	- `platform/minecraft/src/main/java/ca/teamdman/sfm/common/handler/TutorialWorldHandler.java`
- Dimension data:
	- `platform/minecraft/src/main/resources/data/sfm/dimension/tutorial.json`
	- `platform/minecraft/src/main/resources/data/sfm/dimension_type/tutorial.json`
- Localization:
	- `platform/minecraft/src/main/java/ca/teamdman/sfm/common/localization/SFMTutorialLocalizationKeys.java`
	- `platform/minecraft/src/main/java/ca/teamdman/sfm/common/localization/LocalizationKeys.java`
