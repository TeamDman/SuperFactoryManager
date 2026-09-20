# Test Touch Displays and Client Managers

The implementation branch is `feat/1.19.2/packet-computation`. Run the commands
below from its repository root. Use `sfm-propagate-changes.exe`, not Gradle.
The [living plan](architecture/sfm-touch-display-and-client-manager-plan.md)
records which acceptance runs have passed against the latest source.

## Launch the 1.19.4 checkpoint

The packet feature is integrated into canonical 1.19.2 and the 1.19.4 version
worktree. Propagation to 1.20 and later is postponed. Use the current launcher
installed from canonical 1.19.2; the 1.19.4 checkout retains its older tooling
and dependency files and does not contain the control-worker crate.

From the 1.19.4 repository root, launch an ordinary client:

```pwsh
sfm-propagate-changes.exe run client --branch 1.19.4 --control-cli-source-root '<canonical-1.19.2-checkout>' --log-file platform/minecraft/build/packet-1.19.4-manual-client.log --log-filter info
```

Replace the placeholder with the absolute canonical checkout path. The client
opens at the title screen; no puppet takes control. For the automated journeys
below, replace the feature branch with `1.19.4` and add the same
`--control-cli-source-root` argument. This builds the worker from that checkout's
existing lockfile; it does not use an arbitrary `sfm.exe` from PATH.

Do not reinstall the older launcher from 1.19.4. The canonical installed
launcher supplies this explicit cross-checkout worker option.

The Client Manager uses the ordinary Manager crafting layout with a comparator
in place of the repeater. A Touch Display uses four iron bars in the corners,
glass at the top and side centres, cable in the centre and redstone below.
Data packets are created by programs, touch events and authorised delivery;
they do not have a blank-item crafting recipe.

## Screen-free integration tests

Run the packet-to-display circuit in one integrated client:

```pwsh
sfm-propagate-changes.exe game-test run-client --branch feat/1.19.2/packet-computation --filter sfm:client_program_read,sfm:touch_display_client_manager_circuit,sfm:client_manager_signing --wait-for-build-lock --log-file platform/minecraft/build/client-manager-circuit.log --log-filter info
```

These tests construct their own fixtures and must not open panels, move the
player or take control of the camera. Client consent in a fixture is scoped
to its own program. It does not approve unrelated managers.

Run the dedicated-server invariants separately:

```pwsh
sfm-propagate-changes.exe game-test run-server --branch feat/1.19.2/packet-computation --filter sfm:touch_display_server_invariant,sfm:client_manager_signing --wait-for-build-lock --log-file platform/minecraft/build/client-manager-server.log --log-filter info
```

Client-only tests are excluded before their classes load on a dedicated server.
The CLI reports failures even when Minecraft itself exits normally.

## Packet item inspection

The opt-in packet puppet proves inventory hover and Alt+D without operating-system
input. Run it separately from ordinary gameplay:

```pwsh
sfm-propagate-changes.exe puppet run sfm:in_world_packet_inspection --branch feat/1.19.2/packet-computation --variant 1280x720@auto --wait-for-build-lock --log-file platform/minecraft/build/packet-inspection-puppet.log --log-filter info
```

Wait for its fresh control directory and `ready.json`, then run:

```pwsh
./platform/minecraft/Run-PacketInspectionPuppet.ps1 -ControlDirectory '<fresh control directory>'
```

Its 11 requests cover the rendered packet, expanded and compact tooltips,
immutable read-only packet and ordinary-item documents, and the blank writable
no-hover fallback. The tooltip steps use the real command palette and restore
the previous mode during cleanup, including aborts.
Screenshots remain in the canonical preview artifacts. The container is a
disposable client-only fixture, not a server inventory test.

Tooltip mode is separate from key polling. These no-argument palette actions
can also be invoked through `sfm.exe`:

```text
sfm action invoke sfm:tooltip/more_info/expand
sfm action invoke sfm:tooltip/more_info/compact
sfm action invoke sfm:tooltip/more_info/reset
```

Expand always shows more information. Compact always hides it, even while the
configured key is held. Reset follows the configured key again. Overrides are
not saved and reset when leaving a real player session; null-player connection
transitions preserve the override. Packet, disk, label-gun and form presentation
share this mode. They do not simulate or change any physical key state.

The ambient `sfm:packet_item_tooltip` GameTest invokes the same registered
actions without opening a screen. It reads production item tooltips and
restores the previous mode within one client task. Native Shift polling is
not claimed by this test or puppet, and is not a required manual boundary.

For optional hands-on use in 1.19.2, launch an ordinary client:

```pwsh
sfm-propagate-changes.exe run client --branch feat/1.19.2/packet-computation --wait-for-build-lock
```

Open a disposable world with cheats enabled and create a known packet:

```text
/give @s sfm:packet{"sfm:packet_codec":2,"sfm:packet_value":'{"status":"hello"}'} 1
```

Hover it in your inventory. In automatic mode, the compact tooltip shows the
more-info key reminder; holding that key shows the formatted `status` value.
The actions above select the same presentation without holding a key.
This command uses pre-component item NBT and
must be adapted before documenting the same check for newer targets.

## Consent and signing review

UI acceptance deliberately uses an opt-in file-driven puppet. It controls
Minecraft's own widgets, not the operating-system pointer. Do not run it while
trying to use that test window interactively.

```pwsh
sfm-propagate-changes.exe puppet run sfm:client_program_consent_and_signing --branch feat/1.19.2/packet-computation --variant 1280x720@auto --wait-for-build-lock --log-file platform/minecraft/build/client-signing-puppet.log --log-filter info
```

Wait for its fresh control directory and `ready.json`, then run the driver
from a second shell:

```pwsh
./platform/minecraft/Run-ClientProgramSigningPuppet.ps1 -ControlDirectory '<fresh control directory>'
```

Use the exact directory reported by this run. The driver refuses to overwrite
previous request or response files. It exercises editor save acknowledgement,
source review, protected fixture keys, separate signing, keyboard confirmation,
stale revisions and signer trust. Saving a program must never sign it.

Screenshots and response files remain under the worktree's build and puppet
run directories. Fixture keys are disposable encrypted test keys. The driver
does not read or sign with personal keys.

For manual review in an ordinary client, open the consent panel:

```text
sfm action invoke sfm:panel/open sfm:client_script_consents
```

Review the exact program, location and requested capabilities before approving.
Execution and drawing are separate permissions. Explicit denial, local policy
and stop-all take precedence over signer trust.

## In-world terminal circuit

```pwsh
sfm-propagate-changes.exe game-test run-client --branch feat/1.19.2/packet-computation --filter sfm:touch_display_terminal_integration --wait-for-build-lock --log-file platform/minecraft/build/touch-display-terminal.log --log-filter info
```

This ambient test requires the terminal helper and a source-built `sfm`
CLI worker. Client automation defaults to the checkout-local worker through
`sfm.controlCliExecutable`; an installed `sfm.exe` on PATH is not proof that
the current worker source is being tested. The terminal helper remains selected
by `sfm.terminal.rustServerExecutable` or the existing terminal configuration.

For a target checkout without `platform/cli/sfm/Cargo.toml`, explicitly select
the baseline checkout that contains the worker and its existing lockfile:

```pwsh
sfm-propagate-changes.exe game-test run-client --branch <target-branch> --control-cli-source-root '<baseline-checkout>' --filter sfm:touch_display_terminal_integration --wait-for-build-lock
```

The launcher builds that worker with locked, offline Cargo inputs, then passes
its absolute executable and source root to Minecraft. It does not copy
dependency declarations into the target or fall back to PATH. The same option
works with `puppet run`, `puppet matrix` and `run client`. An ordinary client
only provisions the worker when this option is supplied. A dry run validates
the source root, manifest and lockfile without building the worker; it is not
runtime acceptance. Check the living plan before treating a target as tested.

The test owns an isolated loopback helper. It checks one server-generated touch
packet, an ordinary manager broadcast, consented inbox input, a structured
worker acknowledgement, changed raster content and teardown. It must not adopt
or terminate an unrelated terminal session. See the living plan for the latest
runtime result; a compiled fixture alone does not prove this circuit.

Manual terminal session selection is available through:

```text
sfm action invoke sfm:panel/open sfm:terminal_mounts
```

Creating or connecting a declared mount is explicit. Reading the raster does
not grant touch-input permission. The panel connects only compatible
SFM-owned sessions; it does not attach to an arbitrary external process.

For the opt-in world-rendering check, run:

```pwsh
sfm-propagate-changes.exe puppet run sfm:in_world_touch_display_terminal --branch feat/1.19.2/packet-computation --variant 1280x720@auto --wait-for-build-lock --log-file platform/minecraft/build/terminal-world-puppet.log --log-filter info
```

Wait for the fresh terminal control directory and its `ready.json`, then run:

```pwsh
./platform/minecraft/Run-TouchDisplayTerminalPuppet.ps1 -ControlDirectory '<fresh control directory>'
```

This separate puppet aims the camera, captures the rendered terminal, presses
the face once, captures the changed raster and waits for owned-process cleanup.
It uses Minecraft gameplay input, not operating-system automation. The ambient
test above never performs those camera actions.

To author a manual mount, place a Touch Display at your chosen position and
label it `displays` on a Client Manager's disk. Replace the coordinates below
with that display's exact block position:

```sfml
CLIENT BTW
EVERY FRAME FOR displays AS display DO
    LET terminalBinding BE JSON "{\"x\":12,\"y\":64,\"z\":8,\"channel\":\"sfm:terminal_demo\",\"input\":true}"
    LET terminalStatus BE INVOKE "sfm:terminal/display" WITH terminalBinding
    LET inputStatus BE INVOKE "sfm:terminal/input/status" WITH terminalBinding
    RENDER IMAGE "minecraft:textures/block/red_concrete.png" TO display
END
```

The red texture is a fallback. Review the exact manager in the consent panel,
including the separate session, raster, input and inbox permissions. In the
terminal-mount panel, choose that declaration, start the local server if needed,
and create an interactive session
or connect a compatible SFM-owned session. Creating a mount does not enable input.

For input, place a mailbox behind the display. Connect it and an archive to a
server Manager and label them `touches` and `touched`. Replace `YourPlayerName`
with the recipient's game name:

```sfml
SERVER BTW
LET owner BE PLAYER OF YourPlayerName
EVERY 20 TICKS DO
    INPUT 1 sfm:packet FROM touches
    BROADCAST TO owner CHANNEL sfm:terminal_demo
    OUTPUT 1 sfm:packet TO touched
END
```

Drain any stale touch packets from the rear mailbox before enabling input.
Choose Enable input in the terminal-mount panel before pressing the display.
This skips entries already retained in the client inbox. A packet still in the
physical mailbox can arrive as a new inbox entry after input is enabled.
On a remote server, the owner must also grant the exact subscription and
publisher scopes described below, using `sfm:terminal_demo` for both channel
arguments and the actual server Manager position. This touch-to-broadcast
circuit does not need an inventory insertion grant.

## Remote multiplayer boundary

Run the isolated, loopback-only dedicated server and remote-client proof:

```pwsh
pwsh -File scripts/test-multiplayer-packet-boundary.ps1
```

This runner prepares canonical launch files, holds the shared build lock,
owns both test processes and retains their logs. Its offline userdev server
tests the remote transport boundary, not Mojang account authentication.
Do not treat its test grants as a public-server policy template.

Real servers deny remote packet operations until an owner grants exact scopes.
The following examples use placeholders for a real player's UUID and the grant
ID returned by the server. Run them as an owner with permission level 4:

```text
/sfm packet_policy grant inventory <player UUID> minecraft:overworld 12 64 8 north expires_in 600
/sfm packet_policy grant inbox <player UUID> minecraft:overworld sfm:dashboard expires_in 600
/sfm packet_policy grant delivery <player UUID> minecraft:overworld 10 64 8 sfm:dashboard expires_in 600
/sfm packet_policy list
/sfm packet_policy revoke <grant UUID>
```

The inventory grant covers only that exact address and side. `unsided` is an
exact capability choice, not a wildcard. The delivery position is the actual
server manager that may publish. A subscription alone does not authorise
publication. Grants default to one hour if `expires_in` is omitted.

Inspect bounded client receipts with:

```text
sfm action invoke sfm:packet/remote_status
```

A receipt describes admission or an insertion attempt, not downstream manager
completion. Requests are not retried automatically. Reconnect creates a fresh
session without replenishing the server's player budget. The old private-world
transport remains separate and does not become remotely accessible.
