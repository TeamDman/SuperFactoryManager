package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.screen.SFMActionChoice;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** Client-thread feedback whose lifetime is the operation, not its transient choice palette. */
final class SFMReleaseReviewOperationFeedback {
    private final SFMScreenMultiplexer workspace;
    private final Consumer<Component> console;
    private final String lane;
    private final long operation;

    SFMReleaseReviewOperationFeedback(SFMClientActionContext context, long operation, Consumer<Component> console) {
        this.workspace = context.originatingHost() instanceof SFMScreenMultiplexer value ? value : null;
        this.console = Objects.requireNonNull(console, "console");
        this.operation = operation;
        this.lane = "sfm:release-review-operation/" + operation;
    }

    void pending(Component message) {
        emit(message, true);
    }

    void complete(Component message) {
        emit(message, false);
    }

    void failed(String summary, String details, List<SFMActionChoice> recovery) {
        console.accept(Component.literal(summary));
        SFM.LOGGER.info("SFM_RELEASE_REVIEW_OPERATION_FEEDBACK operation={} pending=false message={} details={}",
                operation, summary, details);
        if (workspace == null || workspace.isClosing()) return;
        var toast = workspace.showWorkspaceToast(lane, Component.literal(summary), false, recovery);
        workspace.attachWorkspaceToastDetails(toast, details);
    }

    private void emit(Component message, boolean pending) {
        console.accept(message);
        SFM.LOGGER.info("SFM_RELEASE_REVIEW_OPERATION_FEEDBACK operation={} pending={} message={}",
                operation, pending, message.getString());
        // An old callback may report its outcome, but may never create a new workspace.
        if (workspace == null || workspace.isClosing()) return;
        List<SFMActionChoice> actions = pending && operation > 0
                ? List.of(SFMActionChoice.invoke(new ResourceLocation("sfm", "review/session/operation/cancel"),
                        Long.toString(operation), "Cancel pending review operation"))
                : List.of();
        var toast = workspace.showWorkspaceToast(lane, message, false, actions);
        if (pending) workspace.stopWorkspaceToastTimer(toast);
    }
}
