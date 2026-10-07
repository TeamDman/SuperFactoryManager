package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.client.screen.SFMCommandPaletteScreen;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.network.chat.Component;

/** Opens the constrained diagnostics choice palette for the originating panel. */
public final class OpenPanelDiagnosticsAction implements SFMClientAction<SFMScreenMultiplexer> {
    @Override
    public Component title() {
        return Component.literal("Open panel diagnostics");
    }

    @Override
    public Component description() {
        return Component.literal("Choose diagnostics relevant to the current SFM panel");
    }

    @Override
    public SFMClientActionRequirement<SFMScreenMultiplexer> requirement() {
        return PanelActionSupport::resolve;
    }

    @Override
    public int execute(SFMScreenMultiplexer workspace, CommandContext<SFMClientActionSource> command) {
        var context = command.getSource().context();
        var panel = PanelActionSupport.capturedPanel(workspace, context).orElse(null);
        if (panel == null) return 0;
        SFMCommandPaletteScreen.openChoices(
                context,
                Component.literal("SFM diagnostics"),
                SFMScreenMultiplexer.diagnosticChoices(panel));
        return 1;
    }
}
