package ca.teamdman.sfm.client.semantic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMNavigationFramingPolicyTests {
    @Test
    void wideViewportKeepsDocumentAndLineLeftVisibleWithPositiveInset() {
        var result = choose(rect(0, 0, 500, 400), rect(0, 120, 300, 130), rect(120, 120, 160, 130), 400, 220, 2);

        assertTrue(result.observation().documentLeftVisible());
        assertTrue(result.observation().lineLeftVisible());
        assertTrue(result.observation().landmarkVisible());
        assertEquals(-4.0D, result.observation().viewportBounds().left(), 0.0001D);
        assertNull(result.observation().clippingReason());
    }

    @Test
    void narrowViewportKeepsDestinationAndMaximizesLeadingContext() {
        var result = choose(rect(0, 0, 900, 400), rect(0, 120, 700, 130), rect(620, 120, 650, 130), 240, 180, 1);

        assertFalse(result.observation().lineLeftVisible());
        assertTrue(result.observation().landmarkVisible());
        assertEquals("line-left-does-not-fit", result.observation().clippingReason());
        assertEquals(418.0D, result.observation().viewportBounds().left(), 0.0001D);
    }

    @Test
    void firstLinePreservesDocumentTopButMidFileDoesNotPretendLineOneMustFit() {
        var first = choose(rect(0, 0, 400, 1000), rect(0, 0, 200, 10), rect(20, 0, 40, 10), 300, 160, 1);
        var middle = choose(rect(0, 0, 400, 1000), rect(0, 600, 200, 610), rect(20, 600, 40, 610), 300, 160, 1);

        assertTrue(first.observation().documentTopVisible());
        assertFalse(middle.observation().documentTopVisible());
        assertTrue(middle.observation().landmarkVisible());
    }

    @Test
    void oversizedDestinationReportsTypedClippingWhileKeepingStartLandmark() {
        var result = choose(rect(0, 0, 1000, 1000), rect(0, 300, 1000, 310), rect(100, 300, 700, 310), 200, 120, 1);

        assertEquals("destination-wider-than-viewport", result.observation().clippingReason());
        assertTrue(result.observation().landmarkVisible());
    }

    private static SFMNavigationFramingPolicy.Result choose(
            SFMSpatialSemanticContract.Rectangle document,
            SFMSpatialSemanticContract.Rectangle line,
            SFMSpatialSemanticContract.Rectangle destination,
            double viewportWidth,
            double viewportHeight,
            double zoom
    ) {
        return SFMNavigationFramingPolicy.choose(new SFMNavigationFramingPolicy.Request(
                "pane-1",
                "file:///workspace/A.java",
                "sha256:test",
                "region-1",
                "start",
                document,
                line,
                destination,
                viewportWidth,
                viewportHeight,
                8,
                new SFMSpatialSemanticContract.Camera(17, 23, zoom)
        ));
    }

    private static SFMSpatialSemanticContract.Rectangle rect(double left, double top, double right, double bottom) {
        return new SFMSpatialSemanticContract.Rectangle(left, top, right, bottom);
    }
}
