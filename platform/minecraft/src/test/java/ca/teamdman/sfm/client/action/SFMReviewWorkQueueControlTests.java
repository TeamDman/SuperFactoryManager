package ca.teamdman.sfm.client.action;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SFMReviewWorkQueueControlTests {
    @Test
    void unavailableDirectControlReportsOneVisibleOutcomeWithALogOnlySink() {
        var workspace = ca.teamdman.sfm.client.screen.workspace.SFMHeadlessWorkspaceTestSupport.create(
                ca.teamdman.sfm.client.screen.workspace.SFMWorkspaceLayout.single(
                        new ca.teamdman.sfm.client.screen.workspace.SFMTestScreenPanel("not an explorer")));
        var messages = new java.util.ArrayList<net.minecraft.network.chat.Component>();
        assertFalse(SFMReviewWorkQueueControls.invoke(
                new SFMClientActionContext(workspace, () -> true, workspace.focusedPanelId()),
                SFMReleaseReviewAction.Kind.NEXT, java.util.Optional.empty(), messages::add));
        assertEquals(1, messages.size());
        assertEquals(1, workspace.activeWorkspaceToastIds().size());
        assertFalse(workspace.latestWorkspaceToast().orElseThrow().pinned());
    }

    @Test
    void everyVisibleControlUsesOneRegisteredCanonicalCommandWithExplicitMeaning() throws Exception {
        var controls = SFMReviewWorkQueueControls.CONTROLS;
        assertEquals(List.of("Previous", "Next", "Defer", "Resume", "Show current"),
                controls.stream().map(SFMReviewWorkQueueControls.Control::label).toList());
        assertEquals(5, new HashSet<>(controls.stream().map(value -> value.kind().path()).toList()).size());
        for (var control : controls) {
            var dispatcher = new CommandDispatcher<SFMClientActionSource>();
            LiteralArgumentBuilder<SFMClientActionSource> node = LiteralArgumentBuilder.literal(control.kind().path());
            new SFMReleaseReviewAction(control.kind()).configureCommandNode(node);
            dispatcher.register(node);
            String command = control.choice().command().substring("sfm action invoke sfm:".length());
            assertTrue(SFMClientActionExecutor.isExecutable(dispatcher.parse(command,
                    new SFMClientActionSource(new SFMClientActionContext(null, () -> true, null)))), command);
            String field = control.kind() == SFMReleaseReviewAction.Kind.SHOW_CURRENT ? "RELEASE_SHOW_CURRENT"
                    : "RELEASE_" + control.kind().name();
            assertNotNull(SFMReviewActions.class.getDeclaredField(field));
        }
        assertTrue(controls.get(2).description().contains("not approval"));
        assertTrue(controls.get(4).description().contains("without advancing or saving"));
    }
}
