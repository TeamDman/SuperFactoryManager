# SFM live-game control CLI

`sfm.exe` discovers running Super Factory Manager Minecraft clients and invokes
their registered, context-aware client actions over authenticated loopback Vox.

```powershell
sfm instance list
sfm action list
sfm action help sfm:echo
sfm action invoke sfm:echo hi
sfm action invoke sfm:help sfm:echo
sfm logs --tail 100 --log-filter error
sfm manager show minecraft:overworld 0 64 0
sfm spatial coverage run document focused sfm:strict_java_navigation sfm:auto_1_through_8 0 100000 auto
```

`sfm invoke ...` remains a compatibility spelling for `sfm action invoke ...`.
The canonical action family uses the same authenticated, loopback connection as
the compatibility command and never starts a second game process.

`sfm manager show` reads one exact loaded manager's program and labels through
the connected server. The server requires the actual player to have Minecraft
game-master/operator privilege and be in the target dimension; an ordinary
player, spectator, unloaded target, or different block receives a denial. It
does not edit the disk or enumerate managers. Both game and CLI need a build
with SFM's `1.5.0` network channel; older peers are rejected.
When a coordinate is negative, separate options from positional arguments, for
example `sfm manager show -- minecraft:overworld 1 -58 1`.

To opt an already-running client into the file-driven puppet/control surface,
open the in-game command palette and run:

```text
sfm action invoke sfm:control/files/enable
```

The action returns a directory containing `ready.json`. Write a bounded request
to a temporary name, then rename it to `000001.request.json` so the client never
reads a partly written file:

```json
{"command":"sfm action invoke sfm:echo hello"}
```

The client consumes it on its normal client tick and atomically publishes
`000001.response.json`. Use increasing six-digit sequence names for later
requests. Requests are retained as evidence, and the bridge is opt-in; it is
not enabled merely by launching Minecraft.

Use `install.ps1` to install the CLI independently from
`sfm-propagate-changes.exe`.
