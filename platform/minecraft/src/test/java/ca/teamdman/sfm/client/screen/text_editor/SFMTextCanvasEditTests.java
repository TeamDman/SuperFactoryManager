package ca.teamdman.sfm.client.screen.text_editor;

import ca.teamdman.sfm.client.context.SFMContextTextCoordinates;
import ca.teamdman.sfm.client.screen.SFMDrawCanvasModel;
import ca.teamdman.sfm.client.screen.SFMDrawCanvasModel.CanvasGlyph;
import ca.teamdman.sfm.client.search.SFMTextMatchOptions;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentSelection;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SFMTextCanvasEditTests {
    private SFMTextDocumentSelection selection(String text, int start, int end, String id, boolean primary) {
        return new SFMTextDocumentSelection(id, SFMContextTextCoordinates.atUtf16Offset(text, start),
                SFMContextTextCoordinates.atUtf16Offset(text, end), primary);
    }
    private SFMDrawCanvasModel model(String text) {
        var model = new SFMDrawCanvasModel(); model.replaceText(text, ignored -> 4, 10); return model;
    }
    @Test void selectedOccurrencesReplaceTogetherAndProduceDirectionalPrimaryCarets() {
        String text = "hi hi hi";
        var model = model(text);
        var matches = SFMTextEditorMatchSelection.select(text, "hi", SFMTextMatchOptions.defaults(), List.of(), true);
        var edit = SFMTextCanvasEdit.replace(text, model.documentIndex(4, 10), matches.selections(), "yes", ignored -> 4, 4, 10);
        assertEquals("yes yes yes", edit.text());
        assertEquals(List.of(3, 7, 11), edit.selections().stream().map(s -> s.active().byteOffset()).toList());
        assertTrue(edit.selections().get(0).primary());
        assertTrue(edit.selections().stream().allMatch(SFMTextDocumentSelection::collapsed));
        assertEquals(text, model.projectedText(4, 10), "draft creation must not mutate its input");
        model.replaceGlyphs(edit.glyphs());
        assertEquals(edit.text(), model.projectedText(4, 10));
        var second = SFMTextCanvasEdit.replace(edit.text(), model.documentIndex(4, 10), edit.selections(), "!", ignored -> 4, 4, 10);
        assertEquals("yes! yes! yes!", second.text());
    }
    @Test void supplementaryUnicodeCrLfAndTerminalEmptyLinesRemainExact() {
        String text = "😀x\r\n😀x\r\n";
        var model = model(text);
        var matches = SFMTextEditorMatchSelection.select(text, "😀", SFMTextMatchOptions.defaults(), List.of(), true);
        var edit = SFMTextCanvasEdit.replace(text, model.documentIndex(4, 10), matches.selections(), "W", ignored -> 9, 4, 10);
        assertEquals("Wx\r\nWx\r\n", edit.text());
        assertEquals(List.of(1, 5), edit.selections().stream().map(s -> s.active().byteOffset()).toList());
        var deleted = SFMTextCanvasEdit.replace(text, model.documentIndex(4, 10), matches.selections(), "", ignored -> 4, 4, 10);
        assertEquals("x\r\nx\r\n", deleted.text());
    }
    @Test void crossRowReplacementPreservesThePhysicalAnchorAndUnrelatedFractionalGeometry() {
        String text = "ab\ncd";
        var model = new SFMDrawCanvasModel();
        model.replaceGlyphs(List.of(new CanvasGlyph("a", 1.5, 2.25, 4), new CanvasGlyph("b", 5.5, 2.25, 4),
                new CanvasGlyph("c", 0.75, 16.75, 4), new CanvasGlyph("d", 4.75, 16.75, 4)));
        var edit = SFMTextCanvasEdit.replace(text, model.documentIndex(4, 10),
                List.of(selection(text, 1, 4, "range", true)), "X", ignored -> 7, 4, 10);
        assertEquals("aXd", edit.text());
        assertTrue(edit.glyphs().contains(new CanvasGlyph("a", 1.5, 2.25, 4)));
        assertTrue(edit.glyphs().contains(new CanvasGlyph("X", 5.5, 2.25, 7)));
        assertTrue(edit.glyphs().contains(new CanvasGlyph("d", 12.5, 2.25, 4)));
    }
    @Test void insertedLineBreaksAtCaretAndInferredWhitespaceHaveExactOutcomes() {
        var model = model("ab");
        var edit = SFMTextCanvasEdit.replace("ab", model.documentIndex(4, 10),
                List.of(selection("ab", 0, 0, "caret", true)), "\n", ignored -> 4, 4, 10);
        assertEquals("\nab", edit.text());
        assertTrue(edit.glyphs().stream().allMatch(g -> g.y() == 10));
        model.replaceGlyphs(List.of(new CanvasGlyph("a", 0, 0, 4), new CanvasGlyph("b", 12, 0, 4)));
        edit = SFMTextCanvasEdit.replace("a  b", model.documentIndex(4, 10),
                List.of(selection("a  b", 1, 3, "spaces", true)), "_", ignored -> 4, 4, 10);
        assertEquals("a_b", edit.text());
    }
    @Test void overlapCompositeGlyphAndExcessiveResultAreRefusedBeforeAnyMutation() {
        var model = model("abcdef");
        var index = model.documentIndex(4, 10);
        var overlap = List.of(selection("abcdef", 0, 3, "a", true), selection("abcdef", 2, 4, "b", false));
        assertThrows(IllegalArgumentException.class, () -> SFMTextCanvasEdit.replace("abcdef", index, overlap, "x", ignored -> 4, 4, 10));
        assertThrows(IllegalArgumentException.class, () -> SFMTextCanvasEdit.replace("abcdef", index,
                List.of(selection("abcdef", 0, 0, "a", true)), "x".repeat(32768), ignored -> 4, 4, 10));
        model.replaceGlyphs(List.of(new CanvasGlyph("word", 0, 0, 16)));
        assertThrows(IllegalArgumentException.class, () -> SFMTextCanvasEdit.replace("word", model.documentIndex(4, 10),
                List.of(selection("word", 1, 2, "part", true)), "x", ignored -> 4, 4, 10));
        assertEquals("word", model.projectedText(4, 10));
    }
}
