package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.network.chat.Component;

/** Toggles maximization of the currently focused workspace panel. */
public final class ToggleMaximizePanelAction implements SFMClientAction<SFMScreenMultiplexer> {
    @Override
    public Component title() {
        return Component.literal("Toggle panel maximize");
    }

    @Override
    public Component description() {
        return Component.literal("Show only the focused panel or restore the workspace layout");
    }

    @Override
    public SFMClientActionRequirement<SFMScreenMultiplexer> requirement() {
        return context -> {
            SFMClientActionAvailability<SFMScreenMultiplexer> workspace = PanelActionSupport.resolve(context);
            if (!workspace.isAvailable()) return workspace;
            if (!workspace.target().canToggleMaximize()) {
                return SFMClientActionAvailability.unavailable(
                        Component.literal("The current workspace has no maximizable panel group"));
            }
            return workspace;
        };
    }

    @Override
    public int execute(
            SFMScreenMultiplexer workspace,
            CommandContext<SFMClientActionSource> context
    ) {
        return PanelActionSupport.closePaletteAfter(
                workspace.toggleMaximizeFocusedPanel() ? 1 : 0);
    }
}
