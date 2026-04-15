package ca.teamdman.sfm.test;

import ca.teamdman.sfm.client.draw.SFMDrawSpatialQueries;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SFMDrawSpatialQueriesTests {
    private static final SFMDrawSpatialQueries.TextMetrics FIXED_WIDTH_METRICS = new SFMDrawSpatialQueries.TextMetrics() {
        @Override
        public int lineHeight() {
            return 1;
        }

        @Override
        public int width(String text) {
            return text.length();
        }
    };

    @Test
    public void concatenatesGlyphsWithinSelectedRectangleUnion() {
        String result = SFMDrawSpatialQueries.concatenateFromRectPoint(
                List.of(
                        new SFMDrawSpatialQueries.RectangleRegion(0, 0, 2, 1),
                        new SFMDrawSpatialQueries.RectangleRegion(1, 0, 4, 1)
                ),
                List.of(new SFMDrawSpatialQueries.TextSurface(0, 0, "abcd", 1.0D)),
                FIXED_WIDTH_METRICS,
                1.5D,
                0.5D
        );

        assertEquals("abcd", result);
    }

    @Test
    public void slicesMultilineTextAcrossSingleTextElement() {
        String result = SFMDrawSpatialQueries.concatenateFromRectPoint(
                List.of(new SFMDrawSpatialQueries.RectangleRegion(0, 0, 2, 2)),
                List.of(new SFMDrawSpatialQueries.TextSurface(0, 0, "abcd\nefgh", 1.0D)),
                FIXED_WIDTH_METRICS,
                1.0D,
                1.0D
        );

        assertEquals("abef", result);
    }

    @Test
    public void insertsRequestedDelimiterBetweenCapturedLines() {
        String result = SFMDrawSpatialQueries.concatenateFromRectPoint(
                List.of(new SFMDrawSpatialQueries.RectangleRegion(0, 0, 2, 2)),
                List.of(new SFMDrawSpatialQueries.TextSurface(0, 0, "abcd\nefgh", 1.0D)),
                FIXED_WIDTH_METRICS,
                1.0D,
                1.0D,
                " | "
        );

        assertEquals("ab | ef", result);
    }

    @Test
    public void concatenatesSpatiallyAcrossMultipleTextElements() {
        String result = SFMDrawSpatialQueries.concatenateFromRectPoint(
                List.of(new SFMDrawSpatialQueries.RectangleRegion(0, 0, 6, 1)),
                List.of(
                        new SFMDrawSpatialQueries.TextSurface(0, 0, "ab", 1.0D),
                        new SFMDrawSpatialQueries.TextSurface(3, 0, "cd", 1.0D)
                ),
                FIXED_WIDTH_METRICS,
                1.0D,
                0.5D
        );

        assertEquals("abcd", result);
    }
}