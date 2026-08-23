package ca.teamdman.sfm.client.screen;

import ca.teamdman.sfm.client.context.SFMContextTextCoordinates;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentRange;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SFMDrawCanvasScreenTests {
    @Test
    public void projectedLfHoverCoordinatesMapToExactCrlfBaselineWithoutAddressingItsInterior() {
        String projected = "first\nsecond\nemoji \uD83D\uDE80 target";
        String baseline = "first\r\nsecond\r\nemoji \uD83D\uDE80 target";

        assertEquals(
                SFMContextTextCoordinates.atLineColumn(baseline, 1, 0),
                SFMDrawCanvasScreen.contextPositionAtProjectedUtf16Offset(
                        projected,
                        baseline,
                        projected.indexOf("second")
                ).orElseThrow()
        );
        assertEquals(
                SFMContextTextCoordinates.atLineColumn(baseline, 2, 8),
                SFMDrawCanvasScreen.contextPositionAtProjectedUtf16Offset(
                        projected,
                        baseline,
                        projected.indexOf("target")
                ).orElseThrow()
        );
    }

    @Test
    public void incompatibleProjectedCoordinateIsReportedAsAbsentInsteadOfCrashingInputHandling() {
        assertTrue(SFMDrawCanvasScreen.contextPositionAtProjectedUtf16Offset(
                "first\nsecond",
                "first",
                "first\nsecond".indexOf("second")
        ).isEmpty());
    }

    @Test
    public void exactHighlightPreservesValidHalfOpenEofAfterTrailingNewline() {
        String source = "first\nsecond\n";
        SFMTextDocumentRange range = new SFMTextDocumentRange(
                SFMContextTextCoordinates.atLineColumn(source, 1, 0),
                SFMContextTextCoordinates.atLineColumn(source, 2, 0)
        );

        assertEquals(
                List.of(new SFMDrawCanvasScreen.TextHighlightRow(1, "second", 0, "second".length())),
                SFMDrawCanvasScreen.textHighlightRows(source, range)
        );
    }

    @Test
    public void exactHighlightPreservesFullFileRangeForCrLfUnicodeAndLeadingEmptyLine() {
        String source = "\r\nα💡\r\n";
        SFMTextDocumentRange range = new SFMTextDocumentRange(
                SFMContextTextCoordinates.atLineColumn(source, 0, 0),
                SFMContextTextCoordinates.atLineColumn(source, 2, 0)
        );

        assertEquals(
                List.of(new SFMDrawCanvasScreen.TextHighlightRow(1, "α💡", 0, "α💡".length())),
                SFMDrawCanvasScreen.textHighlightRows(source, range)
        );
    }

    @Test
    public void exactHighlightDoesNotPaintMultipleEmptyLinesAtEof() {
        String source = "body\n\n";
        SFMTextDocumentRange range = new SFMTextDocumentRange(
                SFMContextTextCoordinates.atLineColumn(source, 0, 0),
                SFMContextTextCoordinates.atLineColumn(source, 2, 0)
        );

        assertEquals(
                List.of(new SFMDrawCanvasScreen.TextHighlightRow(0, "body", 0, "body".length())),
                SFMDrawCanvasScreen.textHighlightRows(source, range)
        );
    }

    @Test
    public void exactHighlightPreservesFullFileRangeEndingOnLineOneHundredNinety() {
        String source = "line\n".repeat(190);
        SFMTextDocumentRange range = new SFMTextDocumentRange(
                SFMContextTextCoordinates.atLineColumn(source, 0, 0),
                SFMContextTextCoordinates.atLineColumn(source, 190, 0)
        );

        List<SFMDrawCanvasScreen.TextHighlightRow> rows = SFMDrawCanvasScreen.textHighlightRows(source, range);
        assertEquals(190, rows.size());
        assertEquals(189, rows.get(rows.size() - 1).line());
    }

    @Test
    public void exactHighlightRejectsRangeAfterDocumentWasShortened() {
        String original = "first\nsecond\n";
        SFMTextDocumentRange range = new SFMTextDocumentRange(
                SFMContextTextCoordinates.atLineColumn(original, 1, 0),
                SFMContextTextCoordinates.atLineColumn(original, 2, 0)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> SFMDrawCanvasScreen.textHighlightRows("first", range)
        );
    }

    @Test
    public void resizingViewportKeepsCanvasOriginAtSameScreenOffset() {
        double initialCamera = 427.0D / 2.0D - 32.0D;
        double resizedCamera = SFMDrawCanvasScreen.resizeCameraAxis(
                initialCamera,
                1.0D,
                427,
                103
        );

        assertEquals(32.0D, -initialCamera + 427.0D / 2.0D);
        assertEquals(32.0D, -resizedCamera + 103.0D / 2.0D);
    }

    @Test
    public void unionRectsMergesOverlappingRectangles() {
        assertEquals(
                Set.of(new SFMDrawCanvasScreen.CanvasRect(0, 0, 15, 10)),
                new HashSet<>(SFMDrawCanvasScreen.unionRects(List.of(
                        new SFMDrawCanvasScreen.CanvasRect(0, 0, 10, 10),
                        new SFMDrawCanvasScreen.CanvasRect(5, 0, 15, 10)
                )))
        );
    }

    @Test
    public void unionRectsKeepsSeparateRectanglesSeparate() {
        assertEquals(
                Set.of(
                        new SFMDrawCanvasScreen.CanvasRect(0, 0, 10, 10),
                        new SFMDrawCanvasScreen.CanvasRect(20, 0, 30, 10)
                ),
                new HashSet<>(SFMDrawCanvasScreen.unionRects(List.of(
                        new SFMDrawCanvasScreen.CanvasRect(0, 0, 10, 10),
                        new SFMDrawCanvasScreen.CanvasRect(20, 0, 30, 10)
                )))
        );
    }

    @Test
    public void unionRectsPreservesLShapedSelectionMask() {
        assertEquals(
                Set.of(
                        new SFMDrawCanvasScreen.CanvasRect(0, 0, 10, 10),
                        new SFMDrawCanvasScreen.CanvasRect(0, 10, 20, 20)
                ),
                new HashSet<>(SFMDrawCanvasScreen.unionRects(List.of(
                        new SFMDrawCanvasScreen.CanvasRect(0, 0, 10, 10),
                        new SFMDrawCanvasScreen.CanvasRect(0, 10, 10, 20),
                        new SFMDrawCanvasScreen.CanvasRect(10, 10, 20, 20)
                )))
        );
    }

    @Test
    public void unionRectsDoesNotOverlapAroundPartialIntersection() {
        assertEquals(
                Set.of(
                        new SFMDrawCanvasScreen.CanvasRect(0, 0, 10, 5),
                        new SFMDrawCanvasScreen.CanvasRect(0, 5, 15, 10),
                        new SFMDrawCanvasScreen.CanvasRect(5, 10, 15, 15)
                ),
                new HashSet<>(SFMDrawCanvasScreen.unionRects(List.of(
                        new SFMDrawCanvasScreen.CanvasRect(0, 0, 10, 10),
                        new SFMDrawCanvasScreen.CanvasRect(5, 5, 15, 15)
                )))
        );
    }
}
