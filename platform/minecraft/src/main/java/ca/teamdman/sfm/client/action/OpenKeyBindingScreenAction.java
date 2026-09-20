package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.screen.SFMKeyBindingScreen;
import ca.teamdman.sfm.client.screen.SFMScreenChangeHelpers;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.network.chat.Component;

public final class OpenKeyBindingScreenAction implements SFMClientAction<SFMClientActionContext> {
    @Override
    public Component title() {
        return Component.literal("Open SFM Key Binds");
    }

    @Override
    public Component description() {
        return Component.literal("Open SFM's key-bind editor for client actions");
    }

    @Override
    public SFMClientActionRequirement<SFMClientActionContext> requirement() {
        return SFMClientActionAvailability::available;
    }

    @Override
    public int execute(SFMClientActionContext target, CommandContext<SFMClientActionSource> context) {
        SFMScreenChangeHelpers.setOrPushScreen(new SFMKeyBindingScreen());
        return 1;
    }
}
