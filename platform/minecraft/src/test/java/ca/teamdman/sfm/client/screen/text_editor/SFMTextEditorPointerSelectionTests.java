package ca.teamdman.sfm.client.screen.text_editor;

import ca.teamdman.sfm.client.screen.SFMDrawCanvasModel;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentSelection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMTextEditorPointerSelectionTests {
    private static final int SPACE_WIDTH = 5;
    private static final int LINE_HEIGHT = 10;

    @Test
    void forwardAndReverseDragsRetainDirectionalAnchorAndOrderedRange() {
        var layout = layout("abcdef", ignored -> 10);
        var start = layout.positionAtCanvas(4.0D, 4.0D);
        var end = layout.positionAtCanvas(55.0D, 4.0D);

        var forward = new SFMTextDocumentSelection("pointer", start, end, true);
        var reverse = new SFMTextDocumentSelection("pointer", end, start, true);

        assertEquals(0, forward.anchor().byteOffset());
        assertEquals(6, forward.active().byteOffset());
        assertEquals(6, reverse.anchor().byteOffset());
        assertEquals(0, reverse.active().byteOffset());
        assertEquals(forward.orderedRange(), reverse.orderedRange());
    }

    @Test
    void glyphMidpointChoosesTheNearestInsertionBoundaryWithoutSplittingUnicode() {
        var layout = layout("😀x", glyph -> glyph.equals("😀") ? 8 : 10);

        var beforeEmoji = layout.positionAtCanvas(3.99D, 2.0D);
        var afterEmoji = layout.positionAtCanvas(4.0D, 2.0D);
        var beforeX = layout.positionAtCanvas(8.0D, 2.0D);

        assertEquals(0, beforeEmoji.column());
        assertEquals(0, beforeEmoji.byteOffset());
        assertEquals(1, afterEmoji.column());
        assertEquals(4, afterEmoji.byteOffset());
        assertEquals(afterEmoji, beforeX);
    }

    @Test
    void explicitWhitespaceAndInferredCanvasGapsHaveAddressableMidpoints() {
        var explicit = layout("a b", glyph -> glyph.equals(" ") ? 6 : 10);
        assertEquals(1, explicit.positionAtCanvas(12.99D, 2.0D).column());
        assertEquals(2, explicit.positionAtCanvas(13.0D, 2.0D).column());

        SFMDrawCanvasModel model = new SFMDrawCanvasModel();
        model.glyphs().add(new SFMDrawCanvasModel.CanvasGlyph("a", 0.0D, 0.0D, 10));
        model.glyphs().add(new SFMDrawCanvasModel.CanvasGlyph("b", 30.0D, 0.0D, 10));
        String inferredText = model.projectedText(SPACE_WIDTH, LINE_HEIGHT);
        var inferred = SFMTextEditorPointerSelection.capture(
                inferredText,
                model.documentIndex(SPACE_WIDTH, LINE_HEIGHT),
                SPACE_WIDTH,
                LINE_HEIGHT
        );

        assertEquals("a    b", inferredText);
        assertEquals(3, inferred.positionAtCanvas(17.6D, 2.0D).column());
        assertEquals(5, inferred.positionAtCanvas(29.0D, 2.0D).column());
    }

    @Test
    void blankLinesLineEndsAndTrailingCrLfEofRemainExact() {
        String source = "ab\r\n\r\n😀\r\n";
        var layout = layout(source, glyph -> glyph.equals("😀") ? 8 : 10);

        var firstLineEnd = layout.positionAtCanvas(500.0D, 2.0D);
        var blankLine = layout.positionAtCanvas(500.0D, 12.0D);
        var afterEmoji = layout.positionAtCanvas(4.0D, 22.0D);
        var eof = layout.positionAtCanvas(0.0D, 32.0D);

        assertEquals(List.of(0, 2, 2), List.of(
                firstLineEnd.line(), firstLineEnd.column(), firstLineEnd.byteOffset()));
        assertEquals(List.of(1, 0, 4), List.of(
                blankLine.line(), blankLine.column(), blankLine.byteOffset()));
        assertEquals(List.of(2, 1, 10), List.of(
                afterEmoji.line(), afterEmoji.column(), afterEmoji.byteOffset()));
        assertEquals(List.of(3, 0, 12), List.of(eof.line(), eof.column(), eof.byteOffset()));
    }

    @Test
    void pointerOutsideTheCanvasClampsToDocumentStartOrEof() {
        var layout = layout("first\nlast", ignored -> 10);

        assertEquals(0, layout.positionAtCanvas(-100.0D, -100.0D).byteOffset());
        assertEquals("first\nlast".length(), layout.positionAtCanvas(500.0D, 500.0D).byteOffset());
    }

    @Test
    void rightClickPreservesOnlyNonEmptyRangesContainingThePointer() {
        var layout = layout("abcdef", ignored -> 10);
        var one = layout.positionAtCanvas(5.0D, 2.0D);
        var four = layout.positionAtCanvas(35.0D, 2.0D);
        var inside = layout.positionAtCanvas(25.0D, 2.0D);
        var outside = layout.positionAtCanvas(55.0D, 2.0D);
        var reverse = new SFMTextDocumentSelection("reverse", four, one, true);

        assertTrue(SFMTextEditorPointerSelection.containsNonEmptySelection(List.of(reverse), inside));
        assertTrue(SFMTextEditorPointerSelection.containsNonEmptySelection(List.of(reverse), one));
        assertTrue(SFMTextEditorPointerSelection.containsNonEmptySelection(List.of(reverse), four));
        assertFalse(SFMTextEditorPointerSelection.containsNonEmptySelection(List.of(reverse), outside));
        assertFalse(SFMTextEditorPointerSelection.containsNonEmptySelection(
                List.of(new SFMTextDocumentSelection("caret", inside, inside, true)),
                inside
        ));
    }

    private static SFMTextEditorPointerSelection.Layout layout(
            String source,
            SFMDrawCanvasModel.GlyphWidthReader widths
    ) {
        SFMDrawCanvasModel model = new SFMDrawCanvasModel();
        model.replaceText(source, widths, LINE_HEIGHT);
        return SFMTextEditorPointerSelection.capture(
                source,
                model.documentIndex(SPACE_WIDTH, LINE_HEIGHT),
                SPACE_WIDTH,
                LINE_HEIGHT
        );
    }
}
