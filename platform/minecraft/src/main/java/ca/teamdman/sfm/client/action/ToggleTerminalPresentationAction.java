package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.client.terminal.SFMTerminalPanel;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.network.chat.Component;

/** Opens or closes the focused terminal's renderer/transport selector. */
public final class ToggleTerminalPresentationAction implements SFMClientAction<SFMScreenMultiplexer> {
    @Override
    public Component title() {
        return Component.literal("Toggle terminal presentation menu");
    }

    @Override
    public Component description() {
        return Component.literal("Open or close renderer and transport choices for the focused terminal");
    }

    @Override
    public SFMClientActionRequirement<SFMScreenMultiplexer> requirement() {
        return PanelActionSupport::resolve;
    }

    @Override
    public int execute(SFMScreenMultiplexer workspace, CommandContext<SFMClientActionSource> context)
            throws CommandSyntaxException {
        if (!(PanelActionSupport.capturedPanel(workspace, context.getSource().context())
                .orElse(null) instanceof SFMTerminalPanel terminal)
                || !terminal.togglePresentationMenuForAction()) {
            throw new SimpleCommandExceptionType(Component.literal(
                    "Focused panel is not a connected Rust terminal")).create();
        }
        return PanelActionSupport.closePaletteAfter(1);
    }
}
