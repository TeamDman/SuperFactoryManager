package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.screen.SFMScreenChangeHelpers;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.controls.ControlsScreen;
import net.minecraft.network.chat.Component;

/** Opens Minecraft's built-in Controls screen; SFM Key Binds has a separate action. */
public final class OpenMinecraftControlsAction implements SFMClientAction<SFMClientActionContext> {
    @Override
    public Component title() {
        return Component.literal("Open Minecraft Controls");
    }

    @Override
    public Component description() {
        return Component.literal("Open Minecraft's built-in Controls screen");
    }

    @Override
    public SFMClientActionRequirement<SFMClientActionContext> requirement() {
        return SFMClientActionAvailability::available;
    }

    @Override
    public int execute(SFMClientActionContext target, CommandContext<SFMClientActionSource> context) {
        Minecraft minecraft = Minecraft.getInstance();
        SFMScreenChangeHelpers.setOrPushScreen(new ControlsScreen(minecraft.screen, minecraft.options));
        return 1;
    }
}
