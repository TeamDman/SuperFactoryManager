package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.screen.SFMCommandPaletteScreen;
import ca.teamdman.sfm.client.screen.SFMScreenChangeHelpers;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.client.terminal.SFMTerminalPanel;
import ca.teamdman.sfm.client.terminal.SFMTerminalService;
import ca.teamdman.sfm.client.terminal.SFMTerminalServiceFactory;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Opens the Rust-authoritative terminal, with Java-local fallback if it is unavailable. */
public final class OpenTerminalAction implements SFMClientAction<SFMClientActionContext> {
    @Override
    public Component title() { return Component.literal("Open terminal"); }

    @Override
    public Component description() { return Component.literal("Open the Rust/Vox terminal with Java-local fallback"); }

    @Override
    public SFMClientActionRequirement<SFMClientActionContext> requirement() {
        return SFMClientActionAvailability::available;
    }

    @Override
    public int execute(SFMClientActionContext target, CommandContext<SFMClientActionSource> context) {
        return open(target, SFMTerminalServiceFactory.createRust());
    }

    static int open(SFMClientActionContext target, SFMTerminalService service) {
        Minecraft minecraft = Minecraft.getInstance();
        Screen origin = target.originatingHost() instanceof Screen screen ? screen : minecraft.screen;
        if (minecraft.screen instanceof SFMCommandPaletteScreen palette) palette.onClose();
        SFMScreenChangeHelpers.setScreen(SFMScreenMultiplexer.create(
                origin,
                new SFMTerminalPanel(service)
        ));
        return 1;
    }
}
