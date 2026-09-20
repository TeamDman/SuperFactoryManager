package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.screen.workspace.*;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SFMClientActionContinuationTests {
    @Test
    void focusMayMoveButExactPanelAndWorkspaceMustStillExist() {
        var first = new Panel();
        var second = new Panel();
        var layout = SFMWorkspaceLayout.sideBySide(first, second);
        var workspace = SFMHeadlessWorkspaceTestSupport.create(layout);
        var firstId = layout.visiblePanels().get(0).id();
        var continuation = SFMClientActionContinuation.capture(
                new SFMClientActionContext(workspace, () -> false, firstId));
        assertTrue(continuation.matches(workspace), "palette lifetime is not panel lifetime");
        layout.focus(layout.visiblePanels().get(1).id());
        assertTrue(continuation.matches(workspace));
        var otherWorkspace = SFMHeadlessWorkspaceTestSupport.create(SFMWorkspaceLayout.single(new Panel()));
        assertFalse(continuation.matches(otherWorkspace), "a coincidentally reused numeric id is not authority");
        layout.remove(firstId);
        assertFalse(continuation.matches(workspace));
    }

    @Test
    void missingPanelIsNeverTreatedAsAnUnscopedHostContinuation() {
        var workspace = SFMHeadlessWorkspaceTestSupport.create(SFMWorkspaceLayout.single(new Panel()));
        var continuation = SFMClientActionContinuation.capture(
                new SFMClientActionContext(workspace, () -> true, new SFMWorkspacePanelId(900)));
        assertFalse(continuation.matches(workspace));
        Object host = new Object();
        assertTrue(SFMClientActionContinuation.capture(new SFMClientActionContext(host, () -> true, null)).matches(host));
        assertFalse(SFMClientActionContinuation.capture(new SFMClientActionContext(host, () -> true, null)).matches(new Object()));
    }

    private static final class Panel implements SFMScreenPanel {
        @Override public Component title() { return Component.literal("Test panel"); }
        @Override public void render(PoseStack stack, Minecraft minecraft, SFMScreenPanelBounds bounds,
                                     int x, int y, float partialTick, boolean focused) { }
    }
}
