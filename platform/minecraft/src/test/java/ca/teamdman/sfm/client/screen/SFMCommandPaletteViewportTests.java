package ca.teamdman.sfm.client.screen;

import ca.teamdman.sfm.client.screen.widget.SFMVerticalListViewport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SFMCommandPaletteViewportTests {
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
