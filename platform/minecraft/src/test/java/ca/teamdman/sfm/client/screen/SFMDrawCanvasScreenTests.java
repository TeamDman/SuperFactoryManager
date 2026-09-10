package ca.teamdman.sfm.client.screen;

import ca.teamdman.sfm.client.context.SFMContextTextCoordinates;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentRange;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentSelection;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SFMDrawCanvasScreenTests {
    @Test
    public void keyboardExtensionKeepsExactAnchorAcrossDirectionChangesAndCollapsesWithoutShift() {
        String text = "α💡\r\nsecond\r\n";
        var start = SFMContextTextCoordinates.atLineColumn(text, 0, 1);
        var end = SFMContextTextCoordinates.atLineColumn(text, 1, 3);
        var before = SFMContextTextCoordinates.atLineColumn(text, 0, 0);
        var initial = new SFMTextDocumentSelection("pointer-primary", start, start, true);
        var forward = SFMDrawCanvasScreen.selectionAfterNavigation(initial, end, true);
        forward.validateAgainst(text);
        assertEquals(start, forward.anchor());
        assertEquals(end, forward.active());
        var backward = SFMDrawCanvasScreen.selectionAfterNavigation(forward, before, true);
        backward.validateAgainst(text);
        assertEquals(start, backward.anchor());
        assertEquals(before, backward.orderedRange().start());
        var collapsed = SFMDrawCanvasScreen.selectionAfterNavigation(backward, end, false);
        collapsed.validateAgainst(text);
        assertTrue(collapsed.collapsed());
        assertEquals(end, collapsed.anchor());
    }

    @Test
    public void readOnlySelectAllIsOneExactRangeIncludingCrLfUnicodeAndTerminalNewline() {
        for (String text : List.of("", "α💡\r\nsecond\r\n", "source line\n".repeat(3000))) {
            var selection = SFMDrawCanvasScreen.entireDocumentSelection(text);
            selection.validateAgainst(text);
            assertEquals(0, selection.anchor().byteOffset());
            assertEquals(text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length,
                    selection.active().byteOffset());
            assertTrue(selection.primary());
        }
    }

    @Test
    public void highlightCacheReusesRowsButRevalidatesEveryChangedDocument() {
        var cache = new SFMDrawCanvasScreen.TextHighlightCache();
        String text = "α💡\r\nsecond\r\n";
        var range = new SFMTextDocumentRange(
                SFMContextTextCoordinates.atLineColumn(text, 0, 0),
                SFMContextTextCoordinates.atLineColumn(text, 1, 6));
        var expected = SFMDrawCanvasScreen.textHighlightRows(text, range);
        for (int frame = 0; frame < 100; frame++) {
            assertEquals(expected, cache.rows(new String(text), range));
        }
        assertEquals(1, cache.projectionCount());
        assertThrows(IllegalArgumentException.class, () -> cache.rows("short", range));
        assertEquals(0, cache.cachedRangeCount());
        assertEquals(expected, cache.rows(text, range));
        assertEquals(3, cache.projectionCount());
    }

    @Test
    public void highlightCacheDoesNotRetainUnboundedCopiesOfLongLines() {
        var cache = new SFMDrawCanvasScreen.TextHighlightCache();
        String text = "x".repeat(1_048_577);
        var range = new SFMTextDocumentRange(
                SFMContextTextCoordinates.atLineColumn(text, 0, 0),
                SFMContextTextCoordinates.atLineColumn(text, 0, 1));
        assertEquals(1, cache.rows(text, range).size());
        assertEquals(0, cache.cachedRangeCount());
    }

    @Test
    public void wholeSourceCommentRequiresExactTextIncludingTerminalNewline() {
        String exact = "{\r\n  \"name\": \"😀\"\r\n}\r\n";
        var selection = SFMDrawCanvasScreen.entireDocumentSelection(exact);
        var range = new SFMTextDocumentRange(selection.anchor(), selection.active());
        var cache = new SFMDrawCanvasScreen.TextHighlightCache();
        assertEquals(3, cache.rows(exact, range).size());
        assertThrows(IllegalArgumentException.class,
                () -> cache.rows(exact.replace("\r\n", "\n").stripTrailing(), range));
        assertEquals(3, cache.rows(exact, range).size());
    }

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
    public void canvasTerminalRowSelectionRebasesToImmutableBaselineEof() {
        String canvas = "structured diff\n";
        String baseline = "structured diff";
        var canvasEof = SFMContextTextCoordinates.atUtf16Offset(canvas, canvas.length());
        SFMTextDocumentSelection selection = new SFMTextDocumentSelection(
                "selection-0", canvasEof, canvasEof, true);

        SFMTextDocumentSelection rebased = SFMDrawCanvasScreen.rebaseSelection(
                canvas, baseline, selection).orElseThrow();

        var baselineEof = SFMContextTextCoordinates.atUtf16Offset(baseline, baseline.length());
        assertEquals(baselineEof, rebased.anchor());
        assertEquals(baselineEof, rebased.active());
    }

    @Test
    public void canvasSelectionRebasesCrLfByLogicalLineAndUnicodeColumn() {
        String canvas = "first\nemoji 💡 value";
        String baseline = "first\r\nemoji 💡 value";
        var canvasStart = SFMContextTextCoordinates.atLineColumn(canvas, 1, 6);
        var canvasEnd = SFMContextTextCoordinates.atLineColumn(canvas, 1, 7);
        SFMTextDocumentSelection selection = new SFMTextDocumentSelection(
                "selection-0", canvasStart, canvasEnd, true);

        SFMTextDocumentSelection rebased = SFMDrawCanvasScreen.rebaseSelection(
                canvas, baseline, selection).orElseThrow();

        assertEquals(SFMContextTextCoordinates.atLineColumn(baseline, 1, 6), rebased.anchor());
        assertEquals(SFMContextTextCoordinates.atLineColumn(baseline, 1, 7), rebased.active());
    }

    @Test
    public void incompatibleNonEofSelectionIsOmittedInsteadOfClipped() {
        String canvas = "first\nsecond";
        String baseline = "first";
        var second = SFMContextTextCoordinates.atLineColumn(canvas, 1, 0);
        SFMTextDocumentSelection selection = new SFMTextDocumentSelection(
                "selection-0", second, second, true);

        assertTrue(SFMDrawCanvasScreen.rebaseSelection(canvas, baseline, selection).isEmpty());
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
