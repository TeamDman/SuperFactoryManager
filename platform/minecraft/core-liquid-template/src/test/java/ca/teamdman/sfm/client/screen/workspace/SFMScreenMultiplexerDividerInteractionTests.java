package ca.teamdman.sfm.client.screen.workspace;

import org.junit.jupiter.api.Test;
import net.minecraft.client.gui.screens.Screen;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMScreenMultiplexerDividerInteractionTests {
    @Test
    void hoverCaptureContinuousDragAndReleaseOwnCursorAndSuppressChildren() {
        FakeHost host = FakeHost.twoPane();
        RecordingCursor cursor = new RecordingCursor();
        SFMWorkspaceDividerInteraction interaction = new SFMWorkspaceDividerInteraction(host, cursor);

        interaction.pointerMoved(100, 20);
        interaction.pointerMoved(100, 21);
        assertEquals(List.of(SFMWorkspaceDividerCursor.HORIZONTAL_RESIZE), cursor.selections);
        assertTrue(interaction.isHoveringDivider());
        assertTrue(interaction.pointerPressed(100, 20, 0));
        assertTrue(interaction.pointerDragged(350, 20, 0),
                "captured drag must remain consumed beyond the original hit bounds");
        assertEquals(1, host.layoutChanges);
        assertEquals(152, host.layout.bounds(host.viewport, host.dividerPixels()).get(host.leftId()).width());
        assertTrue(interaction.pointerReleased(350, 20, 0));
        assertFalse(interaction.isCaptured());
        assertEquals(SFMWorkspaceDividerCursor.DEFAULT, interaction.snapshot().cursor());
    }

    @Test
    void hoverExitAndVerticalDividerSelectTheExpectedCursorShapes() {
        FakeHost horizontal = FakeHost.twoPane();
        RecordingCursor horizontalCursor = new RecordingCursor();
        SFMWorkspaceDividerInteraction horizontalInteraction =
                new SFMWorkspaceDividerInteraction(horizontal, horizontalCursor);
        horizontalInteraction.pointerMoved(100, 20);
        horizontalInteraction.pointerMoved(20, 20);
        assertEquals(List.of(
                SFMWorkspaceDividerCursor.HORIZONTAL_RESIZE,
                SFMWorkspaceDividerCursor.DEFAULT), horizontalCursor.selections);

        FakeHost vertical = FakeHost.topAndBottom();
        RecordingCursor verticalCursor = new RecordingCursor();
        SFMWorkspaceDividerInteraction verticalInteraction =
                new SFMWorkspaceDividerInteraction(vertical, verticalCursor);
        verticalInteraction.pointerMoved(20, 39);
        assertEquals(List.of(SFMWorkspaceDividerCursor.VERTICAL_RESIZE), verticalCursor.selections);
        assertTrue(verticalInteraction.pointerPressed(20, 39, 0),
                "a divider press must be consumed before child dispatch");
        assertFalse(verticalInteraction.pointerPressed(20, 39, 0),
                "an existing capture owns subsequent presses");
    }

    @Test
    void escapeOrFocusLossRollsBackAndResetsCursor() {
        FakeHost host = FakeHost.twoPane();
        RecordingCursor cursor = new RecordingCursor();
        SFMWorkspaceDividerInteraction interaction = new SFMWorkspaceDividerInteraction(host, cursor);
        interaction.pointerMoved(100, 20);
        assertTrue(interaction.pointerPressed(100, 20, 0));
        assertTrue(interaction.pointerDragged(125, 20, 0));
        assertEquals(125, host.layout.bounds(host.viewport, host.dividerPixels()).get(host.leftId()).width());

        interaction.focusLost();

        assertEquals(100, host.layout.bounds(host.viewport, host.dividerPixels()).get(host.leftId()).width());
        assertFalse(interaction.isCaptured());
        assertEquals(SFMWorkspaceDividerCursor.DEFAULT, cursor.selections.get(cursor.selections.size() - 1));
        assertEquals(2, host.layoutChanges, "one live update and one rollback are observable");
    }

    @Test
    void orthogonalIntersectionSelectsResizeAllAndOneGestureChangesBothAxes() {
        FakeHost host = FakeHost.tJunction();
        RecordingCursor cursor = new RecordingCursor();
        SFMWorkspaceDividerInteraction interaction = new SFMWorkspaceDividerInteraction(host, cursor);
        List<SFMWorkspaceDivider> dividers = host.layout.dividers(
                host.viewport, host.dividerPixels(), host.hitSlopPixels(), host.minimumPanelPixels());
        int x = dividers.stream().filter(divider -> divider.axis() == SFMWorkspaceAxis.HORIZONTAL)
                .findFirst().orElseThrow().position();
        int y = dividers.stream().filter(divider -> divider.axis() == SFMWorkspaceAxis.VERTICAL)
                .findFirst().orElseThrow().position();

        interaction.pointerMoved(x, y);
        assertEquals(SFMWorkspaceDividerCursor.RESIZE_BOTH, interaction.snapshot().cursor());
        assertTrue(interaction.pointerPressed(x, y, 0));
        assertTrue(interaction.pointerDragged(x + 20, y + 10, 0));
        assertEquals(2, interaction.snapshot().capturedDividerIds().size());
        assertTrue(interaction.pointerReleased(x + 20, y + 10, 0));
    }

    @Test
    void structuralReplacementAbandonsCaptureWithoutRestoringOldTree() {
        FakeHost host = FakeHost.twoPane();
        RecordingCursor cursor = new RecordingCursor();
        SFMWorkspaceDividerInteraction interaction = new SFMWorkspaceDividerInteraction(host, cursor);
        interaction.pointerMoved(100, 20);
        interaction.pointerPressed(100, 20, 0);
        interaction.pointerDragged(120, 20, 0);
        SFMScreenPanel inserted = new SFMTestScreenPanel("inserted");
        SFMWorkspacePanelId insertedId = host.layout.insert(
                host.layout.panels().get(1).id(), SFMWorkspaceSide.RIGHT, inserted);

        interaction.synchronizeLayoutRevision();

        assertFalse(interaction.isCaptured());
        assertEquals(inserted, host.layout.panel(insertedId));
        assertEquals(3, host.layout.visiblePanels().size());
        assertEquals(SFMWorkspaceDividerCursor.DEFAULT, interaction.snapshot().cursor());
    }

    @Test
    void glfwCursorHostCreatesHandlesOnceReusesThemAndDestroysOwnedHandles() {
        FakeNativeCursorApi api = new FakeNativeCursorApi();
        SFMWorkspaceGlfwCursorHost cursor = new SFMWorkspaceGlfwCursorHost(77L, api);
        assertEquals(3, api.createdShapes.size());

        cursor.set(SFMWorkspaceDividerCursor.HORIZONTAL_RESIZE);
        cursor.set(SFMWorkspaceDividerCursor.HORIZONTAL_RESIZE);
        cursor.set(SFMWorkspaceDividerCursor.RESIZE_BOTH);
        cursor.close();
        cursor.close();

        assertEquals(3, api.createdShapes.size(), "hover frames must not allocate more cursor handles");
        assertEquals(List.of(101L, 103L, 0L), api.selectedHandles);
        assertEquals(List.of(101L, 102L, 103L), api.destroyedHandles);
    }

    @Test
    void dividerCursorReassertsAfterAChildCursorOwnerRestoresDefault() {
        FakeHost host = FakeHost.twoPane();
        FakeNativeCursorApi api = new FakeNativeCursorApi();
        SFMWorkspaceGlfwCursorHost cursor = new SFMWorkspaceGlfwCursorHost(77L, api);
        SFMWorkspaceDividerInteraction interaction = new SFMWorkspaceDividerInteraction(host, cursor);

        interaction.pointerMoved(100, 20);
        api.setCursor(77L, 0L); // Mirrors an editor render restoring GLFW's default cursor.
        interaction.reassertCursor();

        assertEquals(List.of(101L, 0L, 101L), api.selectedHandles);
        assertEquals(3, api.createdShapes.size(), "cursor arbitration must not allocate per render frame");
        interaction.close();
    }

    @Test
    void multiplexerConsumesCapturedDividerEventsAndRemovalRollsBackAndClosesCursor() throws Exception {
        FakeHost host = FakeHost.twoPane();
        RecordingCursor cursor = new RecordingCursor();
        SFMWorkspaceDividerInteraction interaction = new SFMWorkspaceDividerInteraction(host, cursor);
        SFMScreenMultiplexer workspace = unsafeWorkspace(host.layout);
        setScreenDimensions(workspace, host.viewport.width(), host.viewport.height());
        setField(workspace, "dividerInteraction", interaction);

        assertTrue(workspace.mouseClicked(100, 20, SFMWorkspaceDividerInteraction.PRIMARY_BUTTON));
        assertTrue(workspace.mouseDragged(
                125, 20, SFMWorkspaceDividerInteraction.PRIMARY_BUTTON, 25, 0));
        assertEquals(125, host.layout.bounds(host.viewport, host.dividerPixels()).get(host.leftId()).width());

        workspace.removed();

        assertEquals(100, host.layout.bounds(host.viewport, host.dividerPixels()).get(host.leftId()).width());
        assertFalse(interaction.isCaptured());
        assertTrue(cursor.closed, "screen removal must release the workspace cursor owner");
    }

    private static void setScreenDimensions(Screen screen, int width, int height) throws Exception {
        Field widthField = Screen.class.getDeclaredField("width");
        widthField.setAccessible(true);
        widthField.setInt(screen, width);
        Field heightField = Screen.class.getDeclaredField("height");
        heightField.setAccessible(true);
        heightField.setInt(screen, height);
    }

    private static SFMScreenMultiplexer unsafeWorkspace(SFMWorkspaceLayout layout) throws Exception {
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Unsafe unsafe = (Unsafe) unsafeField.get(null);
        SFMScreenMultiplexer workspace =
                (SFMScreenMultiplexer) unsafe.allocateInstance(SFMScreenMultiplexer.class);
        setField(workspace, "layout", layout);
        return workspace;
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static final class RecordingCursor implements SFMWorkspaceDividerInteraction.CursorSink {
        private final List<SFMWorkspaceDividerCursor> selections = new ArrayList<>();
        private boolean closed;

        @Override
        public void set(SFMWorkspaceDividerCursor cursor) {
            selections.add(cursor);
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    private static final class FakeNativeCursorApi implements SFMWorkspaceGlfwCursorHost.NativeApi {
        private final List<Integer> createdShapes = new ArrayList<>();
        private final List<Long> selectedHandles = new ArrayList<>();
        private final List<Long> destroyedHandles = new ArrayList<>();

        @Override
        public long createStandardCursor(int shape) {
            createdShapes.add(shape);
            return 100L + createdShapes.size();
        }

        @Override
        public void setCursor(long window, long cursor) {
            assertEquals(77L, window);
            selectedHandles.add(cursor);
        }

        @Override
        public void destroyCursor(long cursor) {
            destroyedHandles.add(cursor);
        }
    }

    private static final class FakeHost implements SFMWorkspaceDividerInteraction.Host {
        private final SFMWorkspaceLayout layout;
        private final SFMScreenPanelBounds viewport;
        private int layoutChanges;

        private FakeHost(SFMWorkspaceLayout layout, SFMScreenPanelBounds viewport) {
            this.layout = layout;
            this.viewport = viewport;
        }

        private static FakeHost twoPane() {
            return new FakeHost(SFMWorkspaceLayout.sideBySide(
                    new SFMTestScreenPanel("left"),
                    new SFMTestScreenPanel("right")),
                    new SFMScreenPanelBounds(0, 0, 202, 80));
        }

        private static FakeHost tJunction() {
            return new FakeHost(SFMWorkspaceLayout.group(SFMWorkspaceLayout.horizontal(
                    SFMWorkspaceLayout.panel(new SFMTestScreenPanel("left")),
                    SFMWorkspaceLayout.vertical(
                            SFMWorkspaceLayout.panel(new SFMTestScreenPanel("top")),
                            SFMWorkspaceLayout.panel(new SFMTestScreenPanel("bottom"))))),
                    new SFMScreenPanelBounds(0, 0, 302, 202));
        }

        private static FakeHost topAndBottom() {
            return new FakeHost(SFMWorkspaceLayout.group(SFMWorkspaceLayout.vertical(
                    SFMWorkspaceLayout.panel(new SFMTestScreenPanel("top")),
                    SFMWorkspaceLayout.panel(new SFMTestScreenPanel("bottom")))),
                    new SFMScreenPanelBounds(0, 0, 80, 80));
        }

        private SFMWorkspacePanelId leftId() {
            return layout.visiblePanels().get(0).id();
        }

        @Override
        public SFMWorkspaceLayout layout() {
            return layout;
        }

        @Override
        public SFMScreenPanelBounds viewport() {
            return viewport;
        }

        @Override
        public int dividerPixels() {
            return 2;
        }

        @Override
        public int hitSlopPixels() {
            return 3;
        }

        @Override
        public int minimumPanelPixels() {
            return 48;
        }

        @Override
        public void dividerLayoutChanged() {
            layoutChanges++;
        }
    }
}
