package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelIntentResult;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspaceSide;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.network.chat.Component;

/** Moves the visible entry intact into an adjacent workspace slot. */
public final class MovePanelAction implements SFMClientAction<SFMScreenMultiplexer> {
    private final SFMWorkspaceSide side;

    public MovePanelAction(SFMWorkspaceSide side) {
        this.side = side;
    }

    @Override
    public Component title() {
        return Component.literal("Move panel " + side.name().toLowerCase());
    }

    @Override
    public Component description() {
        return Component.literal("Move the visible panel entry into the adjacent slot without cloning it");
    }

    @Override
    public SFMClientActionRequirement<SFMScreenMultiplexer> requirement() {
        return PanelActionSupport::resolve;
    }

    @Override
    public int execute(SFMScreenMultiplexer workspace, CommandContext<SFMClientActionSource> context) {
        return PanelActionSupport.closePaletteAfter(
                workspace.moveFocused(side) == SFMWorkspacePanelIntentResult.APPLIED ? 1 : 0
        );
    }
}
