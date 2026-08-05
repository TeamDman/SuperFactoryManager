package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.network.chat.Component;

/** Closes the exact originating workspace, including every retained stack entry. */
public final class CloseScreenAction implements SFMClientAction<SFMScreenMultiplexer> {
    @Override
    public Component title() {
        return Component.literal("Close screen");
    }

    @Override
    public Component description() {
        return Component.literal("Close the complete SFM panel workspace");
    }

    @Override
    public SFMClientActionRequirement<SFMScreenMultiplexer> requirement() {
        return PanelActionSupport::resolve;
    }

    @Override
    public int execute(SFMScreenMultiplexer workspace, CommandContext<SFMClientActionSource> context) {
        workspace.onClose();
        return 1;
    }
}
