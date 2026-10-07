package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.screen.SFMTransientActionScreen;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Cancels the currently visible transient action surface. */
public final class ClosePaletteAction implements SFMClientAction<SFMTransientActionScreen> {
    @Override
    public Component title() {
        return Component.literal("Cancel");
    }

    @Override
    public Component description() {
        return Component.literal("Close this constrained command palette without changing the workspace");
    }

    @Override
    public SFMClientActionRequirement<SFMTransientActionScreen> requirement() {
        return ignored -> Minecraft.getInstance().screen instanceof SFMTransientActionScreen transientScreen
                ? SFMClientActionAvailability.available(transientScreen)
                : SFMClientActionAvailability.unavailable(
                        Component.literal("No transient SFM action surface is open"));
    }

    @Override
    public int execute(
            SFMTransientActionScreen target,
            CommandContext<SFMClientActionSource> context
    ) {
        target.dismissActionSurface();
        return 1;
    }
}
