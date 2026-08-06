package ca.teamdman.sfm.client.screen.workspace;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.action.SFMClientActionContext;
import ca.teamdman.sfm.client.action.SFMClientActionExecutor;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.Objects;
import java.util.function.Consumer;

/** Executes a panel control's canonical action against its captured host/panel. */
public final class SFMPanelActionExecution {
    private SFMPanelActionExecution() {
    }

    public static boolean execute(
            SFMWorkspacePanelContext context,
            Minecraft minecraft,
            String command,
            Consumer<Component> feedback
    ) {
        Objects.requireNonNull(context);
        Objects.requireNonNull(command);
        Objects.requireNonNull(feedback);
        SFMClientActionContext actionContext = new SFMClientActionContext(
                context.host(),
                () -> minecraft != null && minecraft.screen == context.host(),
                context.panelId()
        );
        try {
            return SFMClientActionExecutor.execute(command, actionContext, feedback) > 0;
        } catch (CommandSyntaxException | RuntimeException error) {
            SFM.LOGGER.warn("Panel action failed: {}", command, error);
            feedback.accept(Component.literal(error.getMessage() == null
                    ? "Panel action failed"
                    : error.getMessage()));
            return false;
        }
    }
}
