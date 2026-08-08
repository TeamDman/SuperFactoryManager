package ca.teamdman.sfm.client.screen;

import ca.teamdman.sfm.client.screen.widget.SFMVerticalListViewport;
import org.junit.jupiter.api.Test;

import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SFMKeyBindingScreenViewportTests {
    @Test
    void searchAndBlankSpaceNeverResolveToTheFirstAction() {
        SFMVerticalListViewport viewport = viewport(20, 240);
        int centerX = 160;

        assertEquals(OptionalInt.empty(), rowAt(viewport, centerX, 87, 320, 240, true));
        assertEquals(OptionalInt.empty(), rowAt(viewport, centerX, 110, 320, 240, true));
        assertEquals(OptionalInt.of(0), rowAt(viewport, centerX, 88, 320, 240, true));
    }

    @Test
    void rowGapsAndScrollbarSpaceAreNotActionRows() {
        SFMVerticalListViewport viewport = viewport(20, 240);
        SFMVerticalListViewport.Bounds rows = SFMKeyBindingScreen.rowBounds(320, 240, true);

        assertEquals(OptionalInt.empty(), rowAt(viewport, rows.x() + 10, 110, 320, 240, true));
        assertEquals(OptionalInt.of(1), rowAt(viewport, rows.x() + 10, 112, 320, 240, true));
        assertEquals(OptionalInt.empty(), rowAt(viewport, rows.x() + rows.width(), 88, 320, 240, true));
    }

    @Test
    void rowHitUsesTheFirstVisibleActionAfterScrolling() {
        SFMVerticalListViewport viewport = viewport(20, 240);
        viewport.scrollRows(4);
        SFMVerticalListViewport.Bounds rows = SFMKeyBindingScreen.rowBounds(320, 240, true);

        assertEquals(OptionalInt.of(4), rowAt(viewport, rows.x(), 88, 320, 240, true));
    }

    @Test
    void visibleRowsClampAtZeroForTinyScreens() {
        assertEquals(0, SFMKeyBindingScreen.visibleRowCount(80));
        assertEquals(5, SFMKeyBindingScreen.visibleRowCount(240));
    }

    private static SFMVerticalListViewport viewport(int items, int screenHeight) {
        SFMVerticalListViewport viewport = new SFMVerticalListViewport();
        viewport.configure(items, SFMKeyBindingScreen.visibleRowCount(screenHeight));
        viewport.selectFirst();
        return viewport;
    }

    private static OptionalInt rowAt(
            SFMVerticalListViewport viewport,
            double mouseX,
            double mouseY,
            int screenWidth,
            int screenHeight,
            boolean scrollbarVisible
    ) {
        return SFMKeyBindingScreen.actionRowAt(
                viewport,
                mouseX,
                mouseY,
                screenWidth,
                screenHeight,
                scrollbarVisible
        );
    }
}
