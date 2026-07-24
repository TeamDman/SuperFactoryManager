package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.screen.SFMKeyBindingScreen;
import ca.teamdman.sfm.client.screen.SFMScreenChangeHelpers;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.network.chat.Component;

public final class OpenKeyBindingScreenAction implements SFMClientAction<SFMClientActionContext> {
    @Override
    public Component title() {
        return Component.literal("Manage SFM shortcuts");
    }

    @Override
    public Component description() {
        return Component.literal("Add, disable, edit, or remove dynamic shortcuts for client actions");
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
