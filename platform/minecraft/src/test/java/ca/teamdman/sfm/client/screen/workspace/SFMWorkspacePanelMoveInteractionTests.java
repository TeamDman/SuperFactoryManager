package ca.teamdman.sfm.client.screen.workspace;

import ca.teamdman.sfm.client.screen.SFMDrawCanvasScreen;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMWorkspacePanelMoveInteractionTests {
    @Test
    void ordinaryMiddleDragRemainsOwnedByTextEditorCanvasPanning() throws Exception {
        Fixture fixture = new Fixture();
        SFMWorkspacePanelMoveInteraction interaction = fixture.interaction();
        SFMDrawCanvasScreen canvas = createCanvasWithMinecraftBootstrap();

        assertFalse(interaction.pointerPressedFromContent(
                20, 20, SFMWorkspacePanelMoveInteraction.BUTTON, false));
        assertFalse(interaction.isCaptured());
        assertTrue(canvas.mouseClicked(20, 20, SFMWorkspacePanelMoveInteraction.BUTTON));
        assertTrue(canvas.mouseDragged(32, 35, SFMWorkspacePanelMoveInteraction.BUTTON, 12, 15));
        assertEquals(-12.0D, doubleField(canvas, "cameraX"));
        assertEquals(-15.0D, doubleField(canvas, "cameraY"));
        assertTrue(canvas.mouseReleased(32, 35, SFMWorkspacePanelMoveInteraction.BUTTON));
        assertTrue(fixture.events.isEmpty(), "ordinary content panning must not dispatch workspace actions");
    }

    @Test
    void modifiedMiddleDragMovesPanelWithoutDispatchingToContentOrOpeningActions() {
        Fixture fixture = new Fixture();
        SFMWorkspacePanelMoveInteraction interaction = fixture.interaction();

        assertTrue(interaction.pointerPressedFromContent(
                20, 20, SFMWorkspacePanelMoveInteraction.BUTTON, true));
        assertTrue(interaction.pointerDragged(120, 20, SFMWorkspacePanelMoveInteraction.BUTTON));
        assertTrue(interaction.pointerReleased(120, 20, SFMWorkspacePanelMoveInteraction.BUTTON));
        assertFalse(interaction.pointerReleased(120, 20, SFMWorkspacePanelMoveInteraction.BUTTON),
                "a completed capture must not dispatch twice");

        assertEquals(List.of("move:left->right"), fixture.events);
        assertFalse(interaction.isCaptured());
    }

    @Test
    void middleClickOnEntryAffordanceOpensExactEntryActionsOnce() {
        Fixture fixture = new Fixture();
        SFMWorkspacePanelMoveInteraction interaction = fixture.interaction();

        assertTrue(interaction.pointerPressedFromAffordance(
                fixture.left, 20, 20, SFMWorkspacePanelMoveInteraction.BUTTON));
        assertTrue(interaction.pointerReleased(20, 20, SFMWorkspacePanelMoveInteraction.BUTTON));
        assertFalse(interaction.pointerReleased(20, 20, SFMWorkspacePanelMoveInteraction.BUTTON));

        assertEquals(List.of("actions:left"), fixture.events);
        assertFalse(interaction.isCaptured());
    }

    @Test
    void crossingThresholdHighlightsDestinationAndReleaseUsesTheMoveSeam() {
        Fixture fixture = new Fixture();
        SFMWorkspacePanelMoveInteraction interaction = fixture.interaction();

        assertTrue(interaction.pointerPressedFromAffordance(
                fixture.left, 20, 20, SFMWorkspacePanelMoveInteraction.BUTTON));
        assertTrue(interaction.pointerDragged(120, 20, SFMWorkspacePanelMoveInteraction.BUTTON));
        assertTrue(interaction.snapshot().dragging());
        assertEquals(fixture.right.entryId(), interaction.snapshot().destination().orElseThrow().entryId());
        assertTrue(interaction.pointerReleased(120, 20, SFMWorkspacePanelMoveInteraction.BUTTON));

        assertEquals(List.of("move:left->right"), fixture.events);
        assertFalse(interaction.isCaptured());
    }

    @Test
    void subThresholdDragRemainsAClickAndDoesNotAdvertiseADestination() {
        Fixture fixture = new Fixture();
        SFMWorkspacePanelMoveInteraction interaction = fixture.interaction();

        assertTrue(interaction.pointerPressedFromAffordance(
                fixture.left, 20, 20, SFMWorkspacePanelMoveInteraction.BUTTON));
        assertTrue(interaction.pointerDragged(23, 22, SFMWorkspacePanelMoveInteraction.BUTTON));
        assertFalse(interaction.snapshot().dragging());
        assertTrue(interaction.snapshot().destination().isEmpty());
        assertTrue(interaction.pointerReleased(23, 22, SFMWorkspacePanelMoveInteraction.BUTTON));

        assertEquals(List.of("actions:left"), fixture.events);
    }

    @Test
    void cancelSuppressesBothClickAndMoveAndMakesLaterEventsUnowned() {
        Fixture fixture = new Fixture();
        SFMWorkspacePanelMoveInteraction interaction = fixture.interaction();

        assertTrue(interaction.pointerPressedFromContent(
                20, 20, SFMWorkspacePanelMoveInteraction.BUTTON, true));
        assertTrue(interaction.pointerDragged(120, 20, SFMWorkspacePanelMoveInteraction.BUTTON));
        interaction.cancel();

        assertFalse(interaction.isCaptured());
        assertFalse(interaction.pointerDragged(130, 20, SFMWorkspacePanelMoveInteraction.BUTTON));
        assertFalse(interaction.pointerReleased(130, 20, SFMWorkspacePanelMoveInteraction.BUTTON));
        assertTrue(fixture.events.isEmpty());
    }

    @Test
    void capturedGestureKeepsOwnershipAcrossUnrelatedSurfaceCoordinates() {
        Fixture fixture = new Fixture();
        SFMWorkspacePanelMoveInteraction interaction = fixture.interaction();

        assertTrue(interaction.pointerPressedFromContent(
                20, 20, SFMWorkspacePanelMoveInteraction.BUTTON, true));
        assertTrue(interaction.pointerDragged(250, 250, SFMWorkspacePanelMoveInteraction.BUTTON),
                "a captured drag remains consumed outside all panel bounds");
        assertTrue(interaction.pointerReleased(250, 250, SFMWorkspacePanelMoveInteraction.BUTTON));

        assertTrue(fixture.events.isEmpty());
        assertFalse(interaction.isCaptured());
    }

    @Test
    void anInvalidDragDropDoesNotAccidentallyBecomeAClick() {
        Fixture fixture = new Fixture();
        SFMWorkspacePanelMoveInteraction interaction = fixture.interaction();

        interaction.pointerPressedFromContent(
                20, 20, SFMWorkspacePanelMoveInteraction.BUTTON, true);
        interaction.pointerDragged(250, 20, SFMWorkspacePanelMoveInteraction.BUTTON);
        interaction.pointerReleased(250, 20, SFMWorkspacePanelMoveInteraction.BUTTON);

        assertTrue(fixture.events.isEmpty());
    }

    private static double doubleField(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.getDouble(target);
    }

    private static SFMDrawCanvasScreen createCanvasWithMinecraftBootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        return new SFMDrawCanvasScreen((Screen) null);
    }

    private static final class Fixture implements SFMWorkspacePanelMoveInteraction.Host {
        private final ArrayList<String> events = new ArrayList<>();
        private final SFMWorkspacePanelMoveInteraction.Target left = target(1, "left", 0);
        private final SFMWorkspacePanelMoveInteraction.Target right = target(2, "right", 100);

        private SFMWorkspacePanelMoveInteraction interaction() {
            return new SFMWorkspacePanelMoveInteraction(this);
        }

        @Override
        public SFMWorkspacePanelMoveInteraction.Target targetAt(double mouseX, double mouseY) {
            if (left.bounds().contains(mouseX, mouseY)) return left;
            if (right.bounds().contains(mouseX, mouseY)) return right;
            return null;
        }

        @Override
        public void openActions(SFMWorkspacePanelMoveInteraction.Target source) {
            events.add("actions:" + name(source));
        }

        @Override
        public boolean moveToStack(
                SFMWorkspacePanelMoveInteraction.Target source,
                SFMWorkspacePanelMoveInteraction.Target destination
        ) {
            events.add("move:" + name(source) + "->" + name(destination));
            return true;
        }

        private String name(SFMWorkspacePanelMoveInteraction.Target target) {
            if (target.entryId().equals(left.entryId())) return "left";
            if (target.entryId().equals(right.entryId())) return "right";
            return "unknown";
        }

        private static SFMWorkspacePanelMoveInteraction.Target target(long id, String title, int x) {
            return new SFMWorkspacePanelMoveInteraction.Target(
                    new SFMWorkspacePanelId(id),
                    new SFMTestScreenPanel(title),
                    new SFMScreenPanelBounds(x, 0, 100, 100)
            );
        }
    }
}
