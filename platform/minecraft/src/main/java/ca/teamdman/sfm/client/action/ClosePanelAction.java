package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.network.chat.Component;

/** Closes the visible entry in the originating workspace slot. */
public final class ClosePanelAction implements SFMClientAction<SFMScreenMultiplexer> {
    @Override
    public Component title() {
        return Component.literal("Close panel");
    }

    @Override
    public Component description() {
        return Component.literal("Close the visible panel entry and reveal the next stacked entry");
    }

    @Override
    public SFMClientActionRequirement<SFMScreenMultiplexer> requirement() {
        return PanelActionSupport::resolve;
    }

    @Override
    public int execute(SFMScreenMultiplexer workspace, CommandContext<SFMClientActionSource> context) {
        return workspace.closeFocused() == ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelIntentResult.APPLIED
                ? 1 : 0;
    }
}
