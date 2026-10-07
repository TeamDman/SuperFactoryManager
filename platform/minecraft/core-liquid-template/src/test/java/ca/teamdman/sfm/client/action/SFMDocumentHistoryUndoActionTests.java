package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.history.SFMDocumentHistoryTarget;
import ca.teamdman.sfm.client.history.SFMDocumentHistoryHost;
import ca.teamdman.sfm.client.history.SFMDocumentHistoryHostController;
import ca.teamdman.sfm.client.history.SFMHistoryGraphRuntime;
import ca.teamdman.sfm.client.history.document.SFMDocumentHistoryContract;
import ca.teamdman.sfm.client.history.document.SFMDocumentHistorySession;
import ca.teamdman.sfm.client.history.document.runtime.SFMDocumentHistoryRuntime;
import ca.teamdman.sfm.client.history.document.runtime.SFMDocumentHistorySelector;
{% if features.workspace_panels %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanel;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspaceLayout;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
{% endif %}
{% endif %}
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
{% if features.workspace_panels %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}
import sun.misc.Unsafe;

import java.lang.reflect.Field;
{% endif %}
{% endif %}
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMDocumentHistoryUndoActionTests {
{% if features.workspace_panels %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}
{% if features.workspace_panel_actions or features.client_screen_actions or features.workspace_lifecycle %}
    @Test
    void canonicalActionTargetsTheCapturedTemporalDocumentPanel() throws Exception {
        RecordingPanel panel = new RecordingPanel();
        SFMScreenMultiplexer workspace = headlessWorkspace(SFMWorkspaceLayout.single(panel));
        SFMClientActionSource source = new SFMClientActionSource(
                SFMClientActionContext.create(workspace, () -> true));
        SFMClientActionCommandTree tree = SFMClientActionDispatcherCompiler.compileCommandTree(List.of(
                Map.entry(new ResourceLocation("sfm", "document/history/undo"),
                        new SFMDocumentHistoryUndoAction())
        ));

        assertEquals(
                PanelActionSupport.closePaletteAfter(1),
                tree.execute("sfm action invoke sfm:document/history/undo focused", source)
        );
        assertEquals(1, panel.undoCalls);
    }

{% endif %}
{% endif %}
{% endif %}
    @Test
    void focusedReadOnlyHostRefusesInsteadOfMutatingAnOlderRuntimeSession() {
        SFMDocumentHistoryRuntime runtime = new SFMDocumentHistoryRuntime();
        HistoryFixture fallback = new HistoryFixture("sfm:test/fallback", true);
        HistoryFixture readOnly = new HistoryFixture("sfm:test/read-only", false);
        try (SFMDocumentHistoryRuntime.Registration ignored = runtime.registerFocused(fallback.controller)) {
            SFMClientActionContext context = SFMClientActionContext.create(readOnly, () -> true);

            assertTrue(SFMDocumentHistoryActionSupport.resolve(
                    context, runtime, SFMDocumentHistorySelector.focused()).isEmpty());
            assertEquals("sfm:test/fallback", SFMDocumentHistoryActionSupport.resolve(
                    context, runtime, SFMDocumentHistorySelector.exact("sfm:test/fallback"))
                    .orElseThrow().sessionId());
        } finally {
            fallback.close();
            readOnly.close();
        }
    }

{% if features.workspace_panels %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}
    @Test
    void focusedReadOnlyWorkspacePanelAlsoRefusesRuntimeFallback() throws Exception {
        SFMDocumentHistoryRuntime runtime = new SFMDocumentHistoryRuntime();
        HistoryFixture fallback = new HistoryFixture("sfm:test/fallback", true);
        HistoryPanel readOnly = new HistoryPanel("sfm:test/read-only", false);
        SFMScreenMultiplexer workspace = headlessWorkspace(SFMWorkspaceLayout.single(readOnly));
        try (SFMDocumentHistoryRuntime.Registration ignored = runtime.registerFocused(fallback.controller)) {
            SFMClientActionContext context = SFMClientActionContext.create(workspace, () -> true);

            assertTrue(SFMDocumentHistoryActionSupport.resolve(
                    context, runtime, SFMDocumentHistorySelector.focused()).isEmpty());
        } finally {
            fallback.close();
            readOnly.close();
        }
    }

{% endif %}
{% endif %}
{% if features.workspace_panels %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}
    private static SFMScreenMultiplexer headlessWorkspace(SFMWorkspaceLayout layout) throws Exception {
        SFMScreenMultiplexer workspace = allocateWithoutConstructor(SFMScreenMultiplexer.class);
        Field layoutField = SFMScreenMultiplexer.class.getDeclaredField("layout");
        layoutField.setAccessible(true);
        layoutField.set(workspace, layout);
        return workspace;
    }

    private static <T> T allocateWithoutConstructor(Class<T> type) throws Exception {
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        return type.cast(((Unsafe) unsafeField.get(null)).allocateInstance(type));
    }

{% endif %}
{% endif %}
{% if features.workspace_panels %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}
{% if features.workspace_panel_actions or features.client_screen_actions or features.workspace_lifecycle %}
    private static final class RecordingPanel implements SFMScreenPanel, SFMDocumentHistoryTarget {
        private int undoCalls;

        @Override
        public Component title() {
            return Component.literal("Temporal document fixture");
        }

        @Override
        public void render(
                PoseStack poseStack,
                Minecraft minecraft,
                ca.teamdman.sfm.client.screen.workspace.SFMScreenPanelBounds bounds,
                int mouseX,
                int mouseY,
                float partialTick,
                boolean focused
        ) {
        }

        @Override
        public SFMHistoryGraphRuntime.OperationResult undoDocumentHistory() {
            undoCalls++;
            return SFMHistoryGraphRuntime.OperationResult.applied("undone");
        }
    }

{% endif %}
{% endif %}
{% endif %}
    private static class HistoryFixture implements SFMDocumentHistoryHost, AutoCloseable {
        private final String sessionId;
        private final boolean available;
        private final SFMDocumentHistorySession session;
        private final SFMDocumentHistoryHostController controller;

        private HistoryFixture(String sessionId, boolean available) {
            this.sessionId = sessionId;
            this.available = available;
            this.session = SFMDocumentHistorySession.create(
                    new SFMDocumentHistoryContract.SessionIdentity(
                            sessionId,
                            sessionId + "/document",
                            Optional.empty()
                    ),
                    SFMDocumentHistoryContract.DocumentState.withCaret("", 0)
            );
            this.controller = new SFMDocumentHistoryHostController(
                    session,
                    "test",
                    "fixture",
                    ignored -> { }
            );
        }

        @Override
        public boolean documentHistoryAvailable() {
            return available;
        }

        @Override
        public String documentHistorySessionId() {
            return sessionId;
        }

        @Override
        public SFMDocumentHistorySession documentHistorySession() {
            return session;
        }

        @Override
        public SFMDocumentHistoryHostController documentHistoryController() {
            return controller;
        }

        @Override
        public void close() {
            controller.close();
        }
    }

{% if features.workspace_panels %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}
    private static final class HistoryPanel extends HistoryFixture implements SFMScreenPanel {
        private HistoryPanel(String sessionId, boolean available) {
            super(sessionId, available);
        }

        @Override
        public Component title() {
            return Component.literal("History fixture");
        }

        @Override
        public void render(
                PoseStack poseStack,
                Minecraft minecraft,
                ca.teamdman.sfm.client.screen.workspace.SFMScreenPanelBounds bounds,
                int mouseX,
                int mouseY,
                float partialTick,
                boolean focused
        ) {
        }
    }
{% endif %}
{% endif %}
}
