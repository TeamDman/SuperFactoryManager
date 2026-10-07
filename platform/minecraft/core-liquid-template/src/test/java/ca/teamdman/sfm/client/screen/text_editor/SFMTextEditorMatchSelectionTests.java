package ca.teamdman.sfm.client.screen.text_editor;

import ca.teamdman.sfm.client.context.SFMContextTextCoordinates;
import ca.teamdman.sfm.client.search.SFMTextMatchOptions;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentSelection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SFMTextEditorMatchSelectionTests {
    @Test void noMatchesPreserveSelectionAndAddNextCannotOverflowTheSelectionBudget() {
        var current = List.of(selection("hello", "seed", 0, 2, true));
        var none = SFMTextEditorMatchSelection.select("hello", "absent", SFMTextMatchOptions.defaults(), current, true);
        assertEquals(current, none.selections());
        assertEquals(0, none.matchCount());
        String text = "a ".repeat(4096) + "b";
        var coordinates = new ca.teamdman.sfm.client.context.SFMTextCoordinateIndex(text);
        var full = java.util.stream.IntStream.range(0, 4096).mapToObj(i -> new SFMTextDocumentSelection(
                "existing-" + i, coordinates.atUtf16(i * 2), coordinates.atUtf16(i * 2 + 1), i == 0)).toList();
        assertThrows(IllegalArgumentException.class, () -> SFMTextEditorMatchSelection.select(
                text, "b", SFMTextMatchOptions.defaults(), full, false));
        assertEquals(4096, full.size());
    }
    @Test void primarySeedIsLiteralAndAddNextRetainsDisjointSelections() {
        String text = "a.b other a.b a.b";
        var primary = selection(text, "seed", 0, 3, true);
        var other = selection(text, "other", 4, 9, false);
        var result = SFMTextEditorMatchSelection.select(text, "", SFMTextMatchOptions.defaults().toggleRegex(),
                List.of(primary, other), false);
        assertEquals("a.b", result.query());
        assertEquals(SFMTextMatchOptions.Mode.LITERAL, result.options().mode());
        assertEquals(3, result.selections().size());
        assertEquals(13, result.selections().stream().filter(SFMTextDocumentSelection::primary).findFirst().orElseThrow().active().byteOffset());
        assertTrue(result.selections().stream().anyMatch(value -> value.id().equals("other")));
        var all = SFMTextEditorMatchSelection.select(text, result.query(), result.options(), result.selections(), true);
        assertEquals(3, all.selections().size());
        assertEquals(List.of(0, 10, 14), all.selections().stream().map(value -> value.orderedRange().start().byteOffset()).toList());
    }

    @Test void unicodeRegexZeroWidthAndOverlapsHaveExactNonduplicatedSelections() {
        String text = "😀x\r\n😀x";
        var all = SFMTextEditorMatchSelection.select(text, "😀", SFMTextMatchOptions.defaults(), List.of(), true);
        assertEquals(List.of(0, 7), all.selections().stream().map(value -> value.anchor().byteOffset()).toList());
        assertEquals(List.of(4, 11), all.selections().stream().map(value -> value.active().byteOffset()).toList());
        var end = SFMTextEditorMatchSelection.select(text, "$", SFMTextMatchOptions.defaults().toggleRegex(), List.of(), true);
        assertTrue(end.selections().stream().allMatch(SFMTextDocumentSelection::collapsed));
        assertFalse(end.selections().isEmpty());
        var overlap = SFMTextEditorMatchSelection.select("ababa", "aba", SFMTextMatchOptions.defaults(), List.of(), true);
        assertEquals(1, overlap.selections().size(), "Reuse the shared nonoverlapping matcher policy");
        var unchanged = SFMTextEditorMatchSelection.select("ababa", "aba", SFMTextMatchOptions.defaults(), overlap.selections(), false);
        assertEquals(overlap.selections(), unchanged.selections());
    }

    @Test void absentSeedInvalidCrlfBoundaryAndBudgetCannotBecomePartialSuccess() {
        assertThrows(IllegalArgumentException.class, () -> SFMTextEditorMatchSelection.select("hello", "",
                SFMTextMatchOptions.defaults(), List.of(), false));
        assertThrows(IllegalArgumentException.class, () -> SFMTextEditorMatchSelection.select("x\r\ny", "\\n",
                SFMTextMatchOptions.defaults().toggleRegex(), List.of(), true));
        assertThrows(IllegalArgumentException.class, () -> SFMTextEditorMatchSelection.select("x".repeat(40_000), "x",
                SFMTextMatchOptions.defaults(), List.of(), true));
    }

    private static SFMTextDocumentSelection selection(String text, String id, int start, int end, boolean primary) {
        return new SFMTextDocumentSelection(id, SFMContextTextCoordinates.atUtf16Offset(text, start),
                SFMContextTextCoordinates.atUtf16Offset(text, end), primary);
    }
}
