# Reporting an SFM problem

Report what you expected, what happened, and the smallest setup that still shows the problem. You do not need to find the cause first. A test that passes is useful if you explain what changed.

Open a [Super Factory Manager GitHub issue](https://github.com/TeamDman/SuperFactoryManager/issues/new) or ask in the [SFM Discord](https://discord.gg/5mbUY3mu6m). If you start in Discord, link the conversation in the issue so we keep the observations together.

## Capture the environment

Record:

- Minecraft, Forge or NeoForge, SFM, modpack, and relevant mod versions
- whether you play in singleplayer, over LAN, or on a dedicated server
- whether the problem began after an update, world migration, server restart, or configuration change

If you do not know individual mod versions, include a mod list or launcher export.

## Describe the setup and failure

Show us:

- which SFM blocks and faces connect, and what moves through them
- the manager program, labels, input and output sides, and relevant machine settings
- the exact trigger: client relog, world reopen, server restart, chunk unload, neighboring block placement, or a machine state change
- what was present when you placed the tunnel, and which side or neighboring block changed afterwards
- what still works, what stops, and whether an SFM rebuild alone restores it
- a screenshot or short recording before and after, if available

For a loop with separate outbound and return paths, capture each path separately. Record actual per-tick input/output rates and source-tank changes as well as stored tank amounts: a destination receiving some material does not prove that the return path can keep up. For a Mekanism reactor and turbine, the useful comparison is reactor heating rate and coolant change versus turbine flow and maximum water output, with the reactor failsafe left enabled.

These triggers follow different game paths, so keep them separate. Do not dismantle a valuable or dangerous contraption just to test a workaround.

The usual log is `logs/latest.log` in the affected client or server instance. A crash may also create a file in `crash-reports`. Client and server log excerpts are especially useful for multiplayer.

Remove server addresses, access tokens, personal paths, and other private data before sharing files. World exports, launcher exports, and manager programs may also contain coordinates or secrets. Review them before sharing, and share only a small disposable world when possible.

## Reduce the problem without losing the important mod

Change one variable at a time and record both passes and failures. For a tunnel connected to a modded machine, a useful sequence is:

1. Record what you already observed in the original world, including the placement order and whether a neighboring block changed state. Only repeat the failure in a safe disposable copy.
2. Create a new superflat world in the same running modpack. Rebuild the smallest connection in the same placement order and repeat the same trigger. This keeps the same mod versions while testing whether the original world matters.
3. In that same disposable superflat world, replace only the modded endpoint with a hopper or another simple inventory, then repeat the same trigger. A hopper pass does not prove the modded endpoint is at fault; it identifies a useful difference.
4. If practical, try the affected mod with SFM in a smaller mod set. Report the exact set you used. A pass in an SFM-only world does not rule out an interaction that requires the other mod.

For a multiblock, note whether it is formed before leaving and after returning. Show the whole structure with chunk borders if a load-order problem is suspected; ports sharing a chunk do not establish that the rest of the structure shares it. If the failing setup is expensive or hazardous, stop at observation and share the layout rather than rebuilding it repeatedly.

## Copyable report template

```text
Expected:
Observed:
Minecraft / loader / SFM versions:
Modpack and relevant mod versions:
Singleplayer, LAN, or dedicated server:
SFM program, labels, tunnel faces, and resource type:
Minimal steps to reproduce:
Trigger (relog, world reopen, server restart, chunk unload, neighbor placement or update):
Placement order and block state changes:
What restores it, if anything:
If transfer is partial, per-tick rates and tank changes on each path:
Same-pack superflat result:
Simple-inventory control result:
Logs, screenshots, or safe world fixture:
```

For an example under investigation, see [the tunnel capability report involving a Mekanism reactor and block updates](https://github.com/TeamDman/SuperFactoryManager/issues/620).
