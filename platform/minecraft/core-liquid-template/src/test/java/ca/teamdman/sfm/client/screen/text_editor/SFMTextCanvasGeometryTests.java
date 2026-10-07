package ca.teamdman.sfm.client.screen.text_editor;

import ca.teamdman.sfm.client.context.SFMContextTextCoordinates;
import ca.teamdman.sfm.client.screen.SFMDrawCanvasDocumentIndex;
import ca.teamdman.sfm.client.screen.SFMDrawCanvasModel.CanvasGlyph;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SFMTextCanvasGeometryTests {
    @Test void realGlyphGeometrySurvivesFractionalRowOriginsAndUnequalWidths() {
        var glyphs = List.of(new CanvasGlyph("W", 1.5, 2.25, 9), new CanvasGlyph("i", 10.5, 2.25, 2),
                new CanvasGlyph("😀", 0.75, 16.75, 8));
        var index = SFMDrawCanvasDocumentIndex.build(glyphs, 4, 10);
        assertEquals("Wi\n😀", index.projection().text());
        var geometry = SFMTextCanvasGeometry.capture("Wi\r\n😀\r\n", index, 4, 10);
        var boxes = geometry.rectangles(SFMContextTextCoordinates.rangeAtUtf16Offsets("Wi\r\n😀\r\n", 0, 6));
        assertTrue(boxes.contains(new SFMTextCanvasGeometry.Rect(1.5, 2.25, 12.5, 12.25)));
        assertTrue(boxes.contains(new SFMTextCanvasGeometry.Rect(0.75, 16.75, 8.75, 26.75)));
        assertEquals(new SFMTextCanvasGeometry.Point(8.75, 16.75), geometry.pointAtUtf16(6));
        assertEquals(new SFMTextCanvasGeometry.Point(0, 26.75), geometry.pointAtUtf16(8));
        assertThrows(IllegalArgumentException.class, () -> geometry.pointAtUtf16(5));
        assertThrows(IllegalArgumentException.class, () -> geometry.pointAtUtf16(3));
        assertEquals(SFMContextTextCoordinates.atUtf16Offset("Wi\r\n😀\r\n", 4), geometry.positionAtCanvas(0.8, 17));
        assertEquals(SFMContextTextCoordinates.atUtf16Offset("Wi\r\n😀\r\n", 6), geometry.positionAtCanvas(8, 17));
    }

    @Test void inferredSpacesAreSelectableAndIncompatibleTextIsRejected() {
        var index = SFMDrawCanvasDocumentIndex.build(List.of(new CanvasGlyph("a", 0, 0, 4),
                new CanvasGlyph("b", 12, 0, 4)), 4, 10);
        var geometry = SFMTextCanvasGeometry.capture("a  b", index, 4, 10);
        assertEquals(new SFMTextCanvasGeometry.Point(8, 0), geometry.pointAtUtf16(2));
        assertFalse(geometry.rectangles(SFMContextTextCoordinates.rangeAtUtf16Offsets("a  b", 1, 3)).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> SFMTextCanvasGeometry.capture("different", index, 4, 10));
    }
}
