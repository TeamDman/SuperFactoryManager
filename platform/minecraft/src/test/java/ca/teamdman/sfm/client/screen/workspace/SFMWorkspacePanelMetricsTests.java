package ca.teamdman.sfm.client.screen.workspace;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SFMWorkspacePanelMetricsTests {
    @Test
    void mapsLogicalViewportToPhysicalFramebufferPixels() {
        SFMWorkspacePanelMetrics metrics = SFMWorkspacePanelMetrics.map(
                new SFMScreenPanelBounds(0, 0, 766, 409),
                0,
                0,
                1.0D,
                3840,
                2045,
                768,
                409
        );

        assertEquals(new SFMScreenPanelBounds(0, 0, 3830, 2045), metrics.physicalPixelBounds());
        assertEquals(5.0D, metrics.localToPhysicalScaleX());
        assertEquals(5.0D, metrics.localToPhysicalScaleY());
    }

    @Test
    void includesPanelScaleAndCoversFractionalPixelEdges() {
        SFMWorkspacePanelMetrics metrics = SFMWorkspacePanelMetrics.map(
                new SFMScreenPanelBounds(1, 2, 3, 4),
                3,
                5,
                1.25D,
                150,
                150,
                100,
                100
        );

        assertEquals(new SFMScreenPanelBounds(6, 11, 6, 8), metrics.physicalPixelBounds());
        assertEquals(1.875D, metrics.localToPhysicalScaleX());
        assertEquals(1.875D, metrics.localToPhysicalScaleY());
    }
}
