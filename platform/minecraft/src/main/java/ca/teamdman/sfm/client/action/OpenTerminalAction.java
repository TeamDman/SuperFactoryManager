package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.screen.SFMCommandPaletteScreen;
import ca.teamdman.sfm.client.screen.SFMScreenChangeHelpers;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.client.terminal.SFMJavaLocalTerminalService;
import ca.teamdman.sfm.client.terminal.SFMTerminalPanel;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Opens the Java-only terminal; Vox is an optional future backend. */
public final class OpenTerminalAction implements SFMClientAction<SFMClientActionContext> {
    @Override
    public Component title() { return Component.literal("Open terminal"); }

    @Override
    public Component description() {
        return Component.literal("Open the Java-local terminal (works without Rust or Vox)");
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
                new SFMTerminalPanel(new SFMJavaLocalTerminalService())
        ));
        return 1;
    }
}
