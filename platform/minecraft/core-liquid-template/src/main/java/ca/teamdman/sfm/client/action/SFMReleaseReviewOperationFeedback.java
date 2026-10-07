package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.screen.SFMActionChoice;
{% if features.workspace_panels and features.workspace_notifications %}
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
{% endif %}
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** Client-thread feedback whose lifetime is the operation, not its transient choice palette. */
final class SFMReleaseReviewOperationFeedback {
{% if features.workspace_panels and features.workspace_notifications %}
    private final SFMScreenMultiplexer workspace;
{% endif %}
    private final Consumer<Component> console;
    private final String lane;
    private final long operation;

    SFMReleaseReviewOperationFeedback(SFMClientActionContext context, long operation, Consumer<Component> console) {
{% if features.workspace_panels and features.workspace_notifications %}
        this.workspace = context.originatingHost() instanceof SFMScreenMultiplexer value ? value : null;
{% endif %}
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
{% if features.workspace_panels and features.workspace_notifications %}
        if (workspace == null || workspace.isClosing()) return;
        var toast = workspace.showWorkspaceToast(lane, Component.literal(summary), false, recovery);
        workspace.attachWorkspaceToastDetails(toast, details);
{% endif %}
    }

    private void emit(Component message, boolean pending) {
        console.accept(message);
        SFM.LOGGER.info("SFM_RELEASE_REVIEW_OPERATION_FEEDBACK operation={} pending={} message={}",
                operation, pending, message.getString());
{% if features.workspace_panels and features.workspace_notifications %}
        // An old callback may report its outcome, but may never create a new workspace.
        if (workspace == null || workspace.isClosing()) return;
        List<SFMActionChoice> actions = pending && operation > 0
                ? List.of(SFMActionChoice.invoke(new ResourceLocation("sfm", "review/session/operation/cancel"),
                        Long.toString(operation), "Cancel pending review operation"))
                : List.of();
        var toast = workspace.showWorkspaceToast(lane, message, false, actions);
        if (pending) workspace.stopWorkspaceToastTimer(toast);
{% endif %}
    }
}
