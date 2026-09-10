package ca.teamdman.sfm.client.text_editor;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SFMTextHighlightRasterTests {
    @Test void adjacentRowsNeverDoubleBlendAtFractionalZoomOrNegativePan() {
        for (double zoom : new double[] {0.125, 0.5, 0.75, 1, 1.1, 1.5, 2, 4}) {
            for (double pan : new double[] {-100.3, -0.9, 0, 0.2, 22.75}) {
                int previousEnd = SFMTextHighlightRaster.boundary(pan);
                for (int line = 0; line < 100; line++) {
                    int start = SFMTextHighlightRaster.boundary(pan + line * 9 * zoom);
                    int end = SFMTextHighlightRaster.boundary(pan + (line + 1) * 9 * zoom);
                    assertEquals(previousEnd, start);
                    assertTrue(end >= start);
                    previousEnd = end;
                }
            }
        }
    }
}
