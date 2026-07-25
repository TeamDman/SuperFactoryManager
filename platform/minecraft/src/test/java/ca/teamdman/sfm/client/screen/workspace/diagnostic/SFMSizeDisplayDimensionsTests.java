package ca.teamdman.sfm.client.screen.workspace.diagnostic;

import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanelBounds;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SFMSizeDisplayDimensionsTests {
    @Test
    void logicalDimensionsRemainIndependentOfThePhysicalViewport() {
        var dimensions = new SFMSizeDisplayDimensions(427, 240);

        assertEquals(427, dimensions.width());
        assertEquals(240, dimensions.height());
    }

    @Test
    void allocatedPanelSourceUsesTheLeafBoundsRatherThanTheFullViewport() {
        var bounds = new SFMScreenPanelBounds(602, 362, 600, 360);

        assertEquals(
                new SFMSizeDisplayDimensions(600, 360),
                SFMSizeDisplayDimensionsSource.allocatedPanel().snapshot(bounds)
        );
    }

    @Test
    void negativeLogicalDimensionsFailAtTheInjectionBoundary() {
        assertThrows(IllegalArgumentException.class, () -> new SFMSizeDisplayDimensions(-1, 240));
        assertThrows(IllegalArgumentException.class, () -> new SFMSizeDisplayDimensions(427, -1));
    }
}
