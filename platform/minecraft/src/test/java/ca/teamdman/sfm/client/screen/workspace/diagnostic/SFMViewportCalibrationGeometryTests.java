package ca.teamdman.sfm.client.screen.workspace.diagnostic;

import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanelBounds;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMViewportCalibrationGeometryTests {
    @Test
    void colourBarsTileTheEntireAvailableWidthWithoutGaps() {
        SFMScreenPanelBounds bounds = new SFMScreenPanelBounds(10, 20, 101, 80);
        var geometry = SFMViewportCalibrationGeometry.create(bounds, 40, 50);

        assertEquals(7, geometry.colourBars().size());
        assertEquals(bounds.x(), geometry.colourBars().get(0).bounds().x());
        assertEquals(bounds.x() + bounds.width(), geometry.colourBars().get(6).bounds().x()
                + geometry.colourBars().get(6).bounds().width());
        for (int index = 1; index < geometry.colourBars().size(); index++) {
            var previous = geometry.colourBars().get(index - 1).bounds();
            assertEquals(previous.x() + previous.width(), geometry.colourBars().get(index).bounds().x());
        }
    }

    @Test
    void pointerReportsScreenAndPanelLocalCoordinatesAtHalfOpenEdges() {
        SFMScreenPanelBounds bounds = new SFMScreenPanelBounds(100, 50, 40, 30);
        var inside = SFMViewportCalibrationGeometry.create(bounds, 139, 79).pointer();
        assertTrue(inside.inside());
        assertEquals(39, inside.localX());
        assertEquals(29, inside.localY());

        var outside = SFMViewportCalibrationGeometry.create(bounds, 140, 80).pointer();
        assertFalse(outside.inside());
        assertEquals(40, outside.localX());
        assertEquals(30, outside.localY());
    }

    @Test
    void rulersUseOneLogicalPixelAndMarkersRemainInsidePanel() {
        SFMScreenPanelBounds bounds = new SFMScreenPanelBounds(7, 11, 23, 19);
        var geometry = SFMViewportCalibrationGeometry.create(bounds, 0, 0);

        assertEquals(bounds.width() + bounds.height(), geometry.checkerboard().size());
        assertTrue(geometry.checkerboard().stream().allMatch(pixel ->
                pixel.x() >= bounds.x() && pixel.x() < bounds.x() + bounds.width()
                        && pixel.y() >= bounds.y() && pixel.y() < bounds.y() + bounds.height()));
        assertTrue(geometry.markers().stream().allMatch(line ->
                line.x0() >= bounds.x() && line.x1() <= bounds.x() + bounds.width()
                        && line.y0() >= bounds.y() && line.y1() <= bounds.y() + bounds.height()));
    }

    @Test
    void tinyAndEmptyBoundsStillProduceSafeGeometry() {
        var empty = SFMViewportCalibrationGeometry.create(new SFMScreenPanelBounds(3, 4, 0, 0), 3, 4);
        assertEquals(7, empty.colourBars().size());
        assertTrue(empty.checkerboard().isEmpty());
        assertTrue(empty.markers().isEmpty());
        assertFalse(empty.pointer().inside());
    }

    @Test
    void narrowDiagnosticsUseOneMeasurementPerLineWhileWideDiagnosticsStayDescriptive() {
        var diagnostics = new SFMViewportDiagnostics(1280, 720, 1280, 720, 427, 240, "Auto", 3.0, "narrow");
        var narrowBounds = new SFMScreenPanelBounds(0, 0, 211, 117);
        var pointer = SFMViewportCalibrationGeometry.create(narrowBounds, 4, 5).pointer();
        var narrow = SFMViewportCalibrationPanel.diagnosticLines("nested", narrowBounds, diagnostics, pointer);

        assertEquals("win 1280x720", narrow.get(1));
        assertEquals("fb 1280x720", narrow.get(2));
        assertEquals("logical 427x240", narrow.get(3));
        assertEquals("GUI Auto (effective 3)", narrow.get(4));
        assertEquals("pointer screen 4,5", narrow.get(6));
        assertEquals("local 4,5  inside", narrow.get(7));

        var wide = SFMViewportCalibrationPanel.diagnosticLines(
                "full",
                new SFMScreenPanelBounds(0, 0, 425, 238),
                diagnostics,
                pointer
        );
        assertTrue(wide.get(1).contains("window 1280x720  framebuffer 1280x720"));
        assertTrue(wide.get(2).contains("GUI Auto (effective 3)"));
    }
}
