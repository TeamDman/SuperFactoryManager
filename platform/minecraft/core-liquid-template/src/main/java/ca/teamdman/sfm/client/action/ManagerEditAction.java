package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.screen.ManagerScreen;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.network.chat.Component;

/** Opens the program editor for the exact Manager screen that owns the action. */
public final class ManagerEditAction implements SFMClientAction<ManagerScreen> {
    @Override public Component title() { return Component.literal("Edit manager program"); }

    @Override public Component description() {
        return Component.literal("Open the focused manager's program in the text editor");
    }

    @Override public SFMClientActionRequirement<ManagerScreen> requirement() {
        return context -> context.requireOriginatingHost(
                ManagerScreen.class,
                Component.literal("Manager edit requires an active manager screen"));
    }

    @Override public int execute(
            ManagerScreen target,
            CommandContext<SFMClientActionSource> context
    ) {
        target.openProgramEditorFromAction();
        return 1;
    }
}
