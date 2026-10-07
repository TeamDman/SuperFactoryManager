package ca.teamdman.sfm.client.screen.workspace;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMWorkspacePaneLifecycleTests {
    @Test
    void captureReportsExactRiskCountsAndCloseRemovesOnlyThatPane() {
        LifecyclePanel dirty = new LifecyclePanel("dirty", new SFMPanelCloseState(true, false));
        LifecyclePanel readOnly = new LifecyclePanel("read-only", SFMPanelCloseState.cleanReadOnly());
        LifecyclePanel neighbor = new LifecyclePanel("neighbor", SFMPanelCloseState.cleanEditable());
        SFMWorkspaceLayout layout = SFMWorkspaceLayout.group(SFMWorkspaceLayout.horizontal(
                SFMWorkspaceLayout.stack(
                        1,
                        SFMWorkspaceLayout.panel(dirty),
                        SFMWorkspaceLayout.panel(readOnly)
                ),
                SFMWorkspaceLayout.panel(neighbor)
        ));
        SFMScreenMultiplexer workspace = SFMHeadlessWorkspaceTestSupport.create(layout);
        SFMWorkspacePanelId dirtyId = idFor(layout, dirty);
        workspace.setPanelReopenRecipe(dirtyId, recipeFor(dirty));

        SFMWorkspacePaneCloseCapture capture = workspace.capturePaneClose(dirtyId).orElseThrow();

        assertEquals(
                new SFMWorkspacePaneCloseSummary(2, 1, 1, 1, 1),
                capture.summary()
        );
        assertTrue(capture.summary().requiresConfirmation());
        assertEquals(
                "total=2, dirty=1, read-only=1, recoverable=1, non-recoverable=1",
                capture.summary().exactCounts()
        );
        assertEquals(SFMWorkspacePanelIntentResult.APPLIED, workspace.closePane(capture));
        assertEquals(1, workspace.panels().size());
        assertSame(neighbor, workspace.panels().get(0));
        assertEquals(1, dirty.closeCount());
        assertEquals(1, readOnly.closeCount());
        assertEquals(0, neighbor.closeCount());
    }

    @Test
    void payloadReplacementMakesCapturedPaneIdentityStale() {
        LifecyclePanel left = new LifecyclePanel("left", SFMPanelCloseState.cleanEditable());
        LifecyclePanel right = new LifecyclePanel("right", SFMPanelCloseState.cleanEditable());
        SFMWorkspaceLayout layout = SFMWorkspaceLayout.sideBySide(left, right);
        SFMScreenMultiplexer workspace = SFMHeadlessWorkspaceTestSupport.create(layout);
        SFMWorkspacePanelId leftId = idFor(layout, left);
        SFMWorkspacePaneCloseCapture capture = workspace.capturePaneClose(leftId).orElseThrow();

        assertTrue(workspace.rotateVisibleContent(1));

        assertFalse(workspace.matchesPaneCloseCapture(capture));
        assertEquals(SFMWorkspacePanelIntentResult.UNAVAILABLE, workspace.closePane(capture));
        assertEquals(2, workspace.panels().size());
        assertEquals(0, left.closeCount());
        assertEquals(0, right.closeCount());
    }

    @Test
    void mostRecentCompatiblePanelUsesIdentityCheckedBoundedFocusHistory() {
        LifecyclePanel first = new LifecyclePanel("first", SFMPanelCloseState.cleanEditable());
        LifecyclePanel second = new LifecyclePanel("second", SFMPanelCloseState.cleanEditable());
        LifecyclePanel third = new LifecyclePanel("third", SFMPanelCloseState.cleanEditable());
        SFMWorkspaceLayout layout = SFMWorkspaceLayout.group(SFMWorkspaceLayout.horizontal(
                SFMWorkspaceLayout.panel(first),
                SFMWorkspaceLayout.panel(second),
                SFMWorkspaceLayout.panel(third)
        ));
        SFMScreenMultiplexer workspace = SFMHeadlessWorkspaceTestSupport.create(layout);
        SFMWorkspacePanelId firstId = idFor(layout, first);
        SFMWorkspacePanelId secondId = idFor(layout, second);
        SFMWorkspacePanelId thirdId = idFor(layout, third);

        assertTrue(workspace.focusPanel(firstId));
        assertTrue(workspace.focusPanel(secondId));
        assertTrue(workspace.focusPanel(thirdId));

        SFMWorkspaceLayout.PanelEntry recent = workspace.mostRecentlyFocusedPanel(
                panel -> panel instanceof LifecyclePanel,
                thirdId
        ).orElseThrow();
        assertEquals(secondId, recent.id());
        assertSame(second, recent.panel());

        assertTrue(workspace.rotateVisibleContent(1));
        assertTrue(workspace.mostRecentlyFocusedPanel(panel -> panel == second, thirdId).isEmpty());
    }

    @Test
    void mouseFocusReplacementPublishesThePreviousCompatiblePanelBeforeDispatch() {
        LifecyclePanel document = new LifecyclePanel("document", SFMPanelCloseState.cleanEditable());
        FocusProbePanel explorer = new FocusProbePanel(document);
        SFMWorkspaceLayout layout = SFMWorkspaceLayout.sideBySide(document, explorer);
        SFMScreenMultiplexer workspace = SFMHeadlessWorkspaceTestSupport.create(layout);
        SFMWorkspacePanelId documentId = idFor(layout, document);
        SFMWorkspacePanelId explorerId = idFor(layout, explorer);
        explorer.attach(workspace, explorerId);
        assertTrue(workspace.focusPanel(documentId));

        assertTrue(workspace.mouseClicked(250, 40, 0));

        assertEquals(explorerId, workspace.focusedPanelId());
        assertSame(document, explorer.observedPrevious().get());
    }

    private static SFMWorkspacePanelId idFor(SFMWorkspaceLayout layout, SFMScreenPanel panel) {
        return layout.panels().stream()
                .filter(entry -> entry.panel() == panel)
                .findFirst()
                .orElseThrow()
                .id();
    }

    private static SFMPanelReopenRecipe recipeFor(LifecyclePanel panel) {
        return new SFMPanelReopenRecipe() {
            @Override
            public ResourceLocation sceneTypeId() {
                return new ResourceLocation("sfm", "test/lifecycle");
            }

            @Override
            public SFMScreenPanel reopen() {
                return new LifecyclePanel(panel.name(), panel.closeState());
            }
        };
    }

    static final class LifecyclePanel implements SFMScreenPanel {
        private final String name;
        private final SFMPanelCloseState closeState;
        private final AtomicInteger closeCount = new AtomicInteger();

        LifecyclePanel(String name, SFMPanelCloseState closeState) {
            this.name = name;
            this.closeState = closeState;
        }

        String name() {
            return name;
        }

        int closeCount() {
            return closeCount.get();
        }

        @Override
        public Component title() {
            return Component.literal(name);
        }

        @Override
        public SFMPanelCloseState closeState() {
            return closeState;
        }

        @Override
        public void closed() {
            closeCount.incrementAndGet();
        }

        @Override
        public void render(
                PoseStack poseStack,
                Minecraft minecraft,
                SFMScreenPanelBounds bounds,
                int mouseX,
                int mouseY,
                float partialTick,
                boolean focused
        ) {
        }
    }

    private static final class FocusProbePanel implements SFMScreenPanel {
        private final SFMScreenPanel expected;
        private final AtomicReference<SFMScreenPanel> observedPrevious = new AtomicReference<>();
        private SFMScreenMultiplexer workspace;
        private SFMWorkspacePanelId ownId;

        private FocusProbePanel(SFMScreenPanel expected) {
            this.expected = expected;
        }

        private void attach(SFMScreenMultiplexer workspace, SFMWorkspacePanelId ownId) {
            this.workspace = workspace;
            this.ownId = ownId;
        }

        private AtomicReference<SFMScreenPanel> observedPrevious() {
            return observedPrevious;
        }

        @Override
        public Component title() {
            return Component.literal("focus probe");
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            workspace.mostRecentlyFocusedPanel(panel -> panel == expected, ownId)
                    .map(SFMWorkspaceLayout.PanelEntry::panel)
                    .ifPresent(observedPrevious::set);
            return true;
        }

        @Override
        public void render(
                PoseStack poseStack,
                Minecraft minecraft,
                SFMScreenPanelBounds bounds,
                int mouseX,
                int mouseY,
                float partialTick,
                boolean focused
        ) {
        }
    }
}
