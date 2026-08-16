package ca.teamdman.sfm.client.screen.workspace;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.action.SFMClientActionContext;
import ca.teamdman.sfm.client.action.SFMClientActionExecutor;
import ca.teamdman.sfm.client.screen.SFMActionChoice;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.function.Consumer;

/** Executes a panel control's canonical action against its captured host/panel. */
public final class SFMPanelActionExecution {
    private SFMPanelActionExecution() {
    }

    /** Executes an action without making callers reconstruct the canonical command surface. */
    public static boolean executeAction(
            SFMWorkspacePanelContext context,
            Minecraft minecraft,
            ResourceLocation actionId,
            Consumer<Component> feedback
    ) {
        Objects.requireNonNull(actionId);
        return execute(context, minecraft, SFMActionChoice.invoke(actionId, "").command(), feedback);
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
