package ca.teamdman.sfm.client.action;

{% if features.workspace_panels and features.workspace_panel_reopening and features.workspace_notifications %}
import ca.teamdman.sfm.client.screen.workspace.*;
{% endif %}
{% if features.workspace_panels and features.workspace_panel_reopening and features.workspace_notifications %}
import com.mojang.blaze3d.vertex.PoseStack;
{% endif %}
{% if features.workspace_panels and features.workspace_panel_reopening and features.workspace_notifications %}
import net.minecraft.client.Minecraft;
{% endif %}
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

class SFMReleaseReviewOperationFeedbackTests {
{% if features.workspace_panels and features.workspace_panel_reopening and features.workspace_notifications %}
    @Test
    void pendingOutlivesTransientPaletteAndTerminalOutcomeReplacesOnlyItsOwnLane() {
        var workspace = SFMHeadlessWorkspaceTestSupport.create(SFMWorkspaceLayout.single(new Panel()));
        var messages = new ArrayList<Component>();
        // A closed choice palette's availability guard is irrelevant to a live workspace's status.
        var context = new SFMClientActionContext(workspace, () -> false, workspace.focusedPanelId());
        var first = new SFMReleaseReviewOperationFeedback(context, 10, messages::add);
        var second = new SFMReleaseReviewOperationFeedback(context, 11, messages::add);
        first.pending(Component.literal("Opening review…"));
        var pending = workspace.latestWorkspaceToast().orElseThrow();
        assertTrue(pending.pinned());
        second.pending(Component.literal("Other operation"));
        first.complete(Component.literal("Cancelled before commit; previous review retained"));
        assertTrue(workspace.workspaceToastSnapshot(pending.id()).isEmpty());
        assertEquals(2, workspace.activeWorkspaceToastIds().size());
        var finished = workspace.latestWorkspaceToast().orElseThrow();
        assertFalse(finished.pinned());
        assertEquals("sfm:release-review-operation/10", finished.replacementKey());
        assertEquals(3, messages.size());
    }

{% endif %}
    @Test
    void nonWorkspaceInvocationStillReportsWithoutCreatingAPanel() {
        var messages = new ArrayList<Component>();
        var feedback = new SFMReleaseReviewOperationFeedback(
{% if features.workspace_panels %}
                new SFMClientActionContext(new Object(), () -> true, null), 2, messages::add);
{% else %}
                new SFMClientActionContext(new Object(), () -> true), 2, messages::add);
{% endif %}
        feedback.pending(Component.literal("Saving…"));
        feedback.complete(Component.literal("Saved"));
        assertEquals(java.util.List.of("Saving…", "Saved"), messages.stream().map(Component::getString).toList());
    }

{% if features.workspace_panels and features.workspace_panel_reopening and features.workspace_notifications %}
    private static final class Panel implements SFMScreenPanel {
        @Override public Component title() { return Component.literal("Test panel"); }
        @Override public void render(PoseStack stack, Minecraft minecraft, SFMScreenPanelBounds bounds,
                                     int x, int y, float partialTick, boolean focused) { }
    }
{% endif %}
}
