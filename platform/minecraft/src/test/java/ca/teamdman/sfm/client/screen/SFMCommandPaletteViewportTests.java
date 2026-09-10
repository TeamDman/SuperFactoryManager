package ca.teamdman.sfm.client.screen;

import ca.teamdman.sfm.client.screen.widget.SFMVerticalListViewport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMCommandPaletteViewportTests {
    @Test
    void wideCandidateTooltipDoesNotFlipOutsideGuiFourViewport() {
        var anchor = SFMCommandPaletteScreen.tooltipAnchor(480, 270, 122, 149, 360, 18);
        assertEquals(104, anchor.x());
        assertEquals(94, anchor.y());
        // Previously vanilla's right-overflow branch placed this at -254.
        assertTrue(anchor.x() + 12 + 360 + 4 <= 480);
    }

    @Test
    void candidateTooltipBorderStaysInsideEveryMatrixViewportAtPointerEdges() {
        for (int[] viewport : new int[][]{
                {1920, 1080}, {960, 540}, {640, 360}, {480, 270}, {500, 500}, {320, 180}
        }) {
            int width = viewport[0];
            int height = viewport[1];
            int rows = SFMCommandPaletteScreen.tooltipRowBudget(height);
            for (int contentWidth : new int[]{1, 70, Math.min(360, width - 40)}) {
                for (int x : new int[]{0, width / 4, width / 2, width - 1}) {
                    for (int y : new int[]{0, height / 2, height - 1}) {
                        var anchor = SFMCommandPaletteScreen.tooltipAnchor(
                                width, height, x, y, contentWidth, rows);
                        // The caller has already wrapped/budgeted the content. Verify
                        // the real Screen offsets and border, not merely anchor bounds.
                        int left = anchor.x() + 12;
                        int top = anchor.y() - 12;
                        assertTrue(left - 4 >= 0);
                        assertTrue(left + contentWidth + 4 <= width);
                        assertTrue(top - 4 >= 0);
                        assertTrue(top + rows * 10 + 2 + 4 <= height);
                    }
                }
            }
        }
    }

    @Test
    void candidateTooltipHasABoundedViewportBudgetAtEveryGuiScale() {
        assertEquals(1, SFMCommandPaletteScreen.tooltipRowBudget(20));
        assertEquals(7, SFMCommandPaletteScreen.tooltipRowBudget(108));
        assertEquals(18, SFMCommandPaletteScreen.tooltipRowBudget(540));
        assertEquals(18, SFMCommandPaletteScreen.tooltipRowBudget(2000));
    }

    @Test
    void suggestionAndConsoleWheelRegionsAreIndependentAndHalfOpen() {
        SFMVerticalListViewport.Bounds suggestions =
                new SFMVerticalListViewport.Bounds(10, 20, 100, 90);
        SFMVerticalListViewport.Bounds console =
                new SFMVerticalListViewport.Bounds(10, 120, 100, 40);

        assertEquals(SFMCommandPaletteScreen.ScrollRegion.SUGGESTIONS,
                region(10, 20, suggestions, console));
        assertEquals(SFMCommandPaletteScreen.ScrollRegion.SUGGESTIONS,
                region(109.99, 109.99, suggestions, console));
        assertEquals(SFMCommandPaletteScreen.ScrollRegion.NONE,
                region(10, 110, suggestions, console));
        assertEquals(SFMCommandPaletteScreen.ScrollRegion.CONSOLE,
                region(10, 120, suggestions, console));
        assertEquals(SFMCommandPaletteScreen.ScrollRegion.CONSOLE,
                region(109.99, 159.99, suggestions, console));
        assertEquals(SFMCommandPaletteScreen.ScrollRegion.NONE,
                region(110, 120, suggestions, console));
        assertEquals(SFMCommandPaletteScreen.ScrollRegion.NONE,
                region(10, 160, suggestions, console));
    }

    @Test
    void consoleWinsIfDefensiveLayoutOverlapOccurs() {
        SFMVerticalListViewport.Bounds suggestions =
                new SFMVerticalListViewport.Bounds(10, 20, 100, 100);
        SFMVerticalListViewport.Bounds console =
                new SFMVerticalListViewport.Bounds(10, 100, 100, 40);

        assertEquals(SFMCommandPaletteScreen.ScrollRegion.CONSOLE,
                region(50, 110, suggestions, console));
    }

    private static SFMCommandPaletteScreen.ScrollRegion region(
            double x,
            double y,
            SFMVerticalListViewport.Bounds suggestions,
            SFMVerticalListViewport.Bounds console
    ) {
        return SFMCommandPaletteScreen.scrollRegionAt(x, y, suggestions, console);
    }
}
