package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.screen.SFMCommandPaletteScreen;
import ca.teamdman.sfm.client.screen.SFMScreenChangeHelpers;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.client.terminal.SFMTerminalPanel;
import ca.teamdman.sfm.client.terminal.SFMTerminalServiceFactory;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Opens the Rust-authoritative terminal when a Vox endpoint is configured. */
public final class OpenTerminalAction implements SFMClientAction<SFMClientActionContext> {
    @Override
    public Component title() { return Component.literal("Open terminal"); }

    @Override
    public Component description() {
        return Component.literal("Open the terminal (Rust/Vox when configured, Java-local fallback otherwise)");
    }

    @Override
    public SFMClientActionRequirement<SFMClientActionContext> requirement() {
        return SFMClientActionAvailability::available;
    }

    @Override
    public int execute(SFMClientActionContext target, CommandContext<SFMClientActionSource> context) {
        Minecraft minecraft = Minecraft.getInstance();
        Screen origin = target.originatingHost() instanceof Screen screen ? screen : minecraft.screen;
        if (minecraft.screen instanceof SFMCommandPaletteScreen palette) palette.onClose();
        SFMScreenChangeHelpers.setScreen(SFMScreenMultiplexer.create(
                origin,
                new SFMTerminalPanel(SFMTerminalServiceFactory.create())
        ));
        return 1;
    }
}
