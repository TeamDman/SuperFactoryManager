package ca.teamdman.sfm.test.draw;

import ca.teamdman.sfm.client.screen.SFMDrawCanvasModel;
import ca.teamdman.sfm.client.screen.SFMDrawCanvasSyntaxHighlightingHelper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SFMDrawCanvasModelTests {
    @Test
    public void startsWithOnlyPrimaryCursor() {
        SFMDrawCanvasModel canvas = new SFMDrawCanvasModel();

        assertEquals(1, canvas.cursors().size());
        assertEquals(0, canvas.focusedCursorIndex());
        assertTrue(canvas.cursors().get(0).active());
    }

    @Test
    public void shiftFocusIncludesCursorInActiveSet() {
        SFMDrawCanvasModel canvas = new SFMDrawCanvasModel();
        canvas.addCursor(64, 0);
        canvas.focusPreviousCursor(false);

        canvas.focusNextCursor(true);

        assertEquals(1, canvas.focusedCursorIndex());
        assertTrue(canvas.cursors().get(0).active());
        assertTrue(canvas.cursors().get(1).active());
    }

    @Test
    public void soloFocusActivatesOnlyFocusedCursor() {
        SFMDrawCanvasModel canvas = new SFMDrawCanvasModel();
        canvas.addCursor(64, 0);
        canvas.focusPreviousCursor(false);
        canvas.focusNextCursor(true);

        canvas.focusPreviousCursor(false);

        assertEquals(0, canvas.focusedCursorIndex());
        assertTrue(canvas.cursors().get(0).active());
        assertFalse(canvas.cursors().get(1).active());
    }

    @Test
    public void collapseToFocusedCursorKeepsOnlyFocusedCursor() {
        SFMDrawCanvasModel canvas = new SFMDrawCanvasModel();
        canvas.addCursor(64, 0);
        canvas.focusPreviousCursor(false);

        canvas.collapseToFocusedCursor();

        assertEquals(1, canvas.cursors().size());
        assertEquals(0, canvas.focusedCursorIndex());
        assertEquals(0, canvas.cursorCanvasX());
        assertTrue(canvas.focusedCursor().active());
    }

    @Test
    public void typingWritesAtAllActiveCursors() {
        SFMDrawCanvasModel canvas = new SFMDrawCanvasModel();
        canvas.addCursor(64, 0);

        canvas.typeGlyph("x", 1);

        assertEquals(2, canvas.glyphs().size());
        assertEquals(0, canvas.glyphs().get(0).x());
        assertEquals(65, canvas.glyphs().get(1).x());
        assertEquals(1, canvas.cursors().get(0).x());
        assertEquals(66, canvas.cursors().get(1).x());
    }

    @Test
    public void typingSpaceCreatesAnAddressableGlyph() {
        SFMDrawCanvasModel canvas = new SFMDrawCanvasModel();

        canvas.typeText("a b", ignored -> 1, 9);

        assertEquals(3, canvas.glyphs().size());
        assertEquals("a", canvas.glyphs().get(0).text());
        assertEquals(" ", canvas.glyphs().get(1).text());
        assertEquals("b", canvas.glyphs().get(2).text());
        assertEquals(2, canvas.glyphs().get(2).x());
        assertEquals("a b", SFMDrawCanvasSyntaxHighlightingHelper.projectCanvasDocument(canvas.glyphs(), 1, 9).text());
    }

    @Test
    public void copyableTextCopiesWholeDocumentWhenNothingIsSelected() {
        SFMDrawCanvasModel canvas = new SFMDrawCanvasModel();

        canvas.typeText("a b\nc", ignored -> 1, 9);

        assertEquals("a b\nc", canvas.copyableText(1, 9));
    }

    @Test
    public void copyableTextCopiesSelectedGlyphsWithNormalizedCoordinates() {
        SFMDrawCanvasModel canvas = new SFMDrawCanvasModel();
        typeTextAt(canvas, "abcd", 0, 0);
        canvas.setCursor(1, 0);
        canvas.addCursor(3, 0);

        assertEquals("b d", canvas.copyableText(1, 9));
    }

    @Test
    public void pasteTextTypesMultilineClipboardText() {
        SFMDrawCanvasModel canvas = new SFMDrawCanvasModel();

        canvas.pasteText("a b\nc", ignored -> 1, 9);

        assertEquals("a b\nc", canvas.projectedText(1, 9));
    }

    @Test
    public void pasteTextInsertsIntoExistingLine() {
        SFMDrawCanvasModel canvas = fromFixture("""
                a|bc
                """);

        canvas.pasteText("X", ignored -> 1, 1);

        assertEquals("""
                aX|bc
                """, toFixture(canvas));
    }

    @Test
    public void typingGlyphShiftsSameLineContentRight() {
        SFMDrawCanvasModel canvas = fromFixture("""
                |asd
                """);

        canvas.typeGlyph("X", 1);

        assertEquals("""
                X|asd
                """, toFixture(canvas));
    }

    @Test
    public void typingGlyphInsideGlyphBoundsInsertsOnGlyphLine() {
        SFMDrawCanvasModel canvas = new SFMDrawCanvasModel();
        typeTextAt(canvas, "WWW", 0, 0, 3);
        canvas.setCursor(4.5D, 4.0D);

        canvas.typeGlyph("X", 1, 9);

        assertEquals(4, canvas.glyphs().size());
        assertEquals(0, glyphAt(canvas, 0, 0).x());
        assertEquals("X", glyphAt(canvas, 3, 0).text());
        assertEquals(4, glyphAt(canvas, 4, 0).x());
        assertEquals(0, canvas.cursorCanvasY());
    }

    @Test
    public void movingActiveCursorsToSamePositionCollapsesDuplicates() {
        SFMDrawCanvasModel canvas = new SFMDrawCanvasModel();
        canvas.addCursor(64, 0);

        canvas.setActiveCursors(10, 20);

        assertEquals(1, canvas.cursors().size());
        assertEquals(10, canvas.cursorCanvasX());
        assertEquals(20, canvas.cursorCanvasY());
        assertTrue(canvas.focusedCursor().active());
    }

    @Test
    public void movingAllCursorsToSamePositionCollapsesDuplicates() {
        SFMDrawCanvasModel canvas = new SFMDrawCanvasModel();
        canvas.addCursor(64, 0);
        canvas.addCursor(128, 0);

        canvas.setAllCursors(10, 20);

        assertEquals(1, canvas.cursors().size());
        assertEquals(10, canvas.cursorCanvasX());
        assertEquals(20, canvas.cursorCanvasY());
        assertTrue(canvas.focusedCursor().active());
    }

    @Test
    public void addCursorAvoidingCrowdingDoesNotAddSecondCursorForCoveredGlyph() {
        SFMDrawCanvasModel canvas = new SFMDrawCanvasModel();
        typeTextAt(canvas, "A", 0, 0);
        canvas.setCursor(0.25D, 4);

        canvas.addCursorAvoidingCrowding(0.75D, 4, 9, 9);

        assertEquals(1, canvas.cursors().size());
        assertTrue(hasCursorNearestToGlyph(canvas, glyphAt(canvas, 0, 0)));
    }

    @Test
    public void addCursorAvoidingCrowdingAddsCursorForUncoveredGlyph() {
        SFMDrawCanvasModel canvas = new SFMDrawCanvasModel();
        typeTextAt(canvas, "AB", 0, 0);
        canvas.setCursor(0.25D, 4);

        canvas.addCursorAvoidingCrowding(1.25D, 4, 9, 9);

        assertEquals(2, canvas.cursors().size());
        assertTrue(hasCursorNearestToGlyph(canvas, glyphAt(canvas, 0, 0)));
        assertTrue(hasCursorNearestToGlyph(canvas, glyphAt(canvas, 1, 0)));
    }

    @Test
    public void addCursorAvoidingCrowdingUsesMinimumSpacingAwayFromGlyphs() {
        SFMDrawCanvasModel canvas = new SFMDrawCanvasModel();
        canvas.setCursor(20, 20);

        canvas.addCursorAvoidingCrowding(24, 24, 9, 9);
        canvas.addCursorAvoidingCrowding(40, 20, 9, 9);

        assertEquals(2, canvas.cursors().size());
        assertTrue(hasCursorAt(canvas, 20, 20));
        assertTrue(hasCursorAt(canvas, 40, 20));
    }

    @Test
    public void backspaceDeletesAtAllActiveCursors() {
        SFMDrawCanvasModel canvas = new SFMDrawCanvasModel();
        canvas.addCursor(64, 0);
        canvas.typeGlyph("a", 1);

        canvas.deleteLeft();

        assertEquals(0, canvas.glyphs().size());
        assertEquals(0, canvas.cursors().get(0).x());
        assertEquals(64, canvas.cursors().get(1).x());
    }

    @Test
    public void ensureCursorClosestToEachGlyphCreatesMissingCursors() {
        SFMDrawCanvasModel canvas = new SFMDrawCanvasModel();
        typeTextAt(canvas, "abc", 0, 0);
        canvas.setCursor(0, 0);

        canvas.ensureCursorClosestToEachGlyph();

        assertEquals(3, canvas.cursors().size());
        assertEquals(0, canvas.cursors().get(0).x());
        assertEquals(1, canvas.cursors().get(1).x());
        assertEquals(2, canvas.cursors().get(2).x());
        assertTrue(canvas.cursors().get(0).active());
        assertTrue(canvas.cursors().get(1).active());
        assertTrue(canvas.cursors().get(2).active());
    }

    @Test
    public void ensureCursorClosestToEachGlyphDoesNotDuplicateCoveredGlyphs() {
        SFMDrawCanvasModel canvas = new SFMDrawCanvasModel();
        typeTextAt(canvas, "abc", 0, 0);
        canvas.setCursor(0, 0);
        canvas.ensureCursorClosestToEachGlyph();

        canvas.ensureCursorClosestToEachGlyph();

        assertEquals(3, canvas.cursors().size());
    }

    @Test
    public void ctrlATargetsThenBackspaceDeletesTransactionally() {
        SFMDrawCanvasModel canvas = new SFMDrawCanvasModel();
        typeTextAt(canvas, "asd", 0, 0);
        canvas.setCursor(0, 0);
        canvas.ensureCursorClosestToEachGlyph();

        canvas.deleteLeft();

        assertEquals(0, canvas.glyphs().size());
    }

    @Test
    public void ctrlBackspaceDeletesNearestWordRunAndClosesGap() {
        SFMDrawCanvasModel canvas = fromFixture("""
                abc,def|
                """);

        canvas.deleteLeftWord(1);

        assertEquals("""
                abc,|
                """, toFixture(canvas));
    }

    @Test
    public void ctrlDeleteDeletesNearestWordRunAndClosesGap() {
        SFMDrawCanvasModel canvas = fromFixture("""
                |abc,def
                """);

        canvas.deleteRightWord(1);

        assertEquals("""
                |,def
                """, toFixture(canvas));
    }

    @Test
    public void ctrlDeleteCanDeleteContiguousNonWordRun() {
        SFMDrawCanvasModel canvas = fromFixture("""
                abc|,.;def
                """);

        canvas.deleteRightWord(1);

        assertEquals("""
                abc|def
                """, toFixture(canvas));
    }

    @Test
    public void ctrlBackspaceConsumesTrailingExplicitWhitespaceAndThePreviousWord() {
        SFMDrawCanvasModel canvas = new SFMDrawCanvasModel();
        canvas.typeText("hello  ", ignored -> 1, 1);

        canvas.deleteLeftWord(1);

        assertEquals("", canvas.projectedText(1, 1));
        assertEquals(0.0D, canvas.cursorCanvasX());
    }

    @Test
    public void ctrlDeleteConsumesLeadingExplicitWhitespaceAndTheNextWord() {
        SFMDrawCanvasModel canvas = new SFMDrawCanvasModel();
        canvas.typeText("  hello", ignored -> 1, 1);
        canvas.setCursor(0, 0);

        canvas.deleteRightWord(1);

        assertEquals("", canvas.projectedText(1, 1));
        assertEquals(0.0D, canvas.cursorCanvasX());
    }

    @Test
    public void ctrlDeleteAcrossInterstitialExplicitWhitespaceConsumesTheFollowingWord() {
        SFMDrawCanvasModel canvas = new SFMDrawCanvasModel();
        canvas.typeText("hello  world", ignored -> 1, 1);
        canvas.setCursor(5, 0);

        canvas.deleteRightWord(1);

        assertEquals("hello", canvas.projectedText(1, 1));
        assertEquals(5.0D, canvas.cursorCanvasX());
    }

    @Test
    public void ctrlATargetsThenDeleteDeletesTransactionally() {
        SFMDrawCanvasModel canvas = new SFMDrawCanvasModel();
        typeTextAt(canvas, "asd", 0, 0);
        canvas.setCursor(0, 0);
        canvas.ensureCursorClosestToEachGlyph();

        canvas.deleteNearestAndMoveRight();

        assertEquals(0, canvas.glyphs().size());
    }

    @Test
    public void ctrlCommaDiscardsCursorsThatAreNotClosestToAnyGlyph() {
        SFMDrawCanvasModel canvas = new SFMDrawCanvasModel();
        typeTextAt(canvas, "ab", 0, 0);
        canvas.setCursor(0, 0);
        canvas.addCursor(1, 0);
        canvas.addCursor(100, 100);

        canvas.discardCursorsNotClosestToAnyGlyph();

        assertEquals(2, canvas.cursors().size());
        assertTrue(hasCursorNearestToGlyph(canvas, glyphAt(canvas, 0, 0)));
        assertTrue(hasCursorNearestToGlyph(canvas, glyphAt(canvas, 1, 0)));
        assertFalse(hasCursorAt(canvas, 100, 100));
    }

    @Test
    public void ctrlCommaKeepsOnlyOneCursorForGlyphWithDuplicateCoverage() {
        SFMDrawCanvasModel canvas = new SFMDrawCanvasModel();
        typeTextAt(canvas, "a", 0, 0);
        canvas.setCursor(0, 0);
        canvas.addCursor(0.4D, 0);

        canvas.discardCursorsNotClosestToAnyGlyph();

        assertEquals(1, canvas.cursors().size());
        assertTrue(hasCursorNearestToGlyph(canvas, glyphAt(canvas, 0, 0)));
    }

    @Test
    public void ctrlCommaOnEmptyCanvasCollapsesToFocusedCursor() {
        SFMDrawCanvasModel canvas = new SFMDrawCanvasModel();
        canvas.addCursor(10, 20);

        canvas.discardCursorsNotClosestToAnyGlyph();

        assertEquals(1, canvas.cursors().size());
        assertEquals(10, canvas.cursorCanvasX());
        assertEquals(20, canvas.cursorCanvasY());
    }

    @Test
    public void ctrlLTargetsGlyphsOnActiveCursorLine() {
        SFMDrawCanvasModel canvas = new SFMDrawCanvasModel();
        typeTextAt(canvas, "abc", 0, 0);
        typeTextAt(canvas, "def", 0, 9);
        canvas.setCursor(1.5D, 4);

        canvas.ensureCursorClosestToEachGlyphOnActiveCursorLines(9);

        assertEquals(3, canvas.cursors().size());
        assertTrue(hasCursorNearestToGlyph(canvas, glyphAt(canvas, 0, 0)));
        assertTrue(hasCursorNearestToGlyph(canvas, glyphAt(canvas, 1, 0)));
        assertTrue(hasCursorNearestToGlyph(canvas, glyphAt(canvas, 2, 0)));
        assertFalse(hasCursorAt(canvas, 0, 9));
    }

    @Test
    public void ctrlLTargetsGlyphsOnEachActiveCursorLine() {
        SFMDrawCanvasModel canvas = new SFMDrawCanvasModel();
        typeTextAt(canvas, "ab", 0, 0);
        typeTextAt(canvas, "cd", 0, 9);
        typeTextAt(canvas, "ef", 0, 18);
        canvas.setCursor(0, 0);
        canvas.addCursor(1, 18);

        canvas.ensureCursorClosestToEachGlyphOnActiveCursorLines(9);

        assertEquals(4, canvas.cursors().size());
        assertTrue(hasCursorAt(canvas, 0, 0));
        assertTrue(hasCursorAt(canvas, 1, 0));
        assertFalse(hasCursorAt(canvas, 0, 9));
        assertFalse(hasCursorAt(canvas, 1, 9));
        assertTrue(hasCursorAt(canvas, 0, 18));
        assertTrue(hasCursorAt(canvas, 1, 18));
    }

    @Test
    public void ctrlLAdvancesToNextLineWhenCurrentLineIsAlreadyCovered() {
        SFMDrawCanvasModel canvas = new SFMDrawCanvasModel();
        typeTextAt(canvas, "ab", 0, 0);
        typeTextAt(canvas, "cd", 0, 9);
        canvas.setCursor(0, 0);

        canvas.ensureCursorClosestToEachGlyphOnActiveCursorLines(9);
        canvas.ensureCursorClosestToEachGlyphOnActiveCursorLines(9);

        assertTrue(hasCursorNearestToGlyph(canvas, glyphAt(canvas, 0, 0)));
        assertTrue(hasCursorNearestToGlyph(canvas, glyphAt(canvas, 1, 0)));
        assertTrue(hasCursorNearestToGlyph(canvas, glyphAt(canvas, 0, 9)));
        assertTrue(hasCursorNearestToGlyph(canvas, glyphAt(canvas, 1, 9)));
    }

    @Test
    public void ctrlLDoesNotAdvanceWhenCurrentLineIsNotFullyCovered() {
        SFMDrawCanvasModel canvas = new SFMDrawCanvasModel();
        typeTextAt(canvas, "abc", 0, 0);
        typeTextAt(canvas, "de", 0, 9);
        canvas.setCursor(0, 0);
        canvas.addCursor(1, 0);

        canvas.ensureCursorClosestToEachGlyphOnActiveCursorLines(9);

        assertTrue(hasCursorNearestToGlyph(canvas, glyphAt(canvas, 0, 0)));
        assertTrue(hasCursorNearestToGlyph(canvas, glyphAt(canvas, 1, 0)));
        assertTrue(hasCursorNearestToGlyph(canvas, glyphAt(canvas, 2, 0)));
        assertFalse(hasCursorNearestToGlyph(canvas, glyphAt(canvas, 0, 9)));
    }

    @Test
    public void enterRepeatedlyAdvancesBlankLines() {
        SFMDrawCanvasModel canvas = new SFMDrawCanvasModel();
        int lineHeight = 9;
        for (char c : "Hello, world!".toCharArray()) {
            canvas.typeGlyph(Character.toString(c), 1);
        }

        canvas.moveCursorToNextLine(lineHeight);
        assertEquals(lineHeight, canvas.cursorCanvasY());

        canvas.moveCursorToNextLine(lineHeight);
        assertEquals(lineHeight * 2, canvas.cursorCanvasY());

        canvas.moveCursorToNextLine(lineHeight);
        assertEquals(lineHeight * 3, canvas.cursorCanvasY());
    }

    @Test
    public void insertLineBreakMovesRowsBelowCursorDown() {
        SFMDrawCanvasModel canvas = new SFMDrawCanvasModel();
        typeTextAt(canvas, "abc", 0, 0);
        typeTextAt(canvas, "def", 0, 9);
        canvas.setCursor(3, 0);

        canvas.insertLineBreak(9);

        assertEquals(0, glyphAt(canvas, 0, 0).y());
        assertEquals(18, glyphAt(canvas, 0, 18).y());
        assertEquals(0, canvas.cursorCanvasX());
        assertEquals(9, canvas.cursorCanvasY());
    }

    @Test
    public void insertLineBreakOnGlyphShiftsCurrentAndLowerRowsDown() {
        SFMDrawCanvasModel canvas = fromFixture("""
                |asd
                dsa
                """);

        canvas.insertLineBreak(1);

        assertEquals("""
                |
                asd
                dsa
                """, toFixture(canvas));
    }

    @Test
    public void insertLineBreakWithMultipleActiveRowsMovesLowerRowsOncePerDistinctCursorRow() {
        SFMDrawCanvasModel canvas = new SFMDrawCanvasModel();
        typeTextAt(canvas, "abc", 0, 0);
        typeTextAt(canvas, "def", 0, 9);
        typeTextAt(canvas, "ghi", 0, 18);
        canvas.setCursor(3, 0);
        canvas.addCursor(3, 9);

        canvas.insertLineBreak(9);

        assertEquals(0, glyphAt(canvas, 0, 0).y());
        assertEquals(18, glyphAt(canvas, 0, 18).y());
        assertEquals(36, glyphAt(canvas, 0, 36).y());
        assertEquals(2, canvas.cursors().size());
        assertEquals(9, canvas.cursors().get(0).y());
        assertEquals(27, canvas.cursors().get(1).y());
    }

    @Test
    public void leftWrapsFromBlankLineToPreviousLineEnd() {
        SFMDrawCanvasModel canvas = fromFixture("""
                abc
                def
                |
                """);

        canvas.moveCursorLeft();

        assertEquals("""
                abc
                def|
                """, toFixture(canvas));
    }

    @Test
    public void leftMovesWithinLineByCaretPosition() {
        SFMDrawCanvasModel canvas = fromFixture("""
                abc
                def|
                """);

        canvas.moveCursorLeft();

        assertEquals("""
                abc
                de|f
                """, toFixture(canvas));
    }

    @Test
    public void leftFromTopLeftGlyphMovesLeftBySpaceWidth() {
        SFMDrawCanvasModel canvas = fromFixture("""
                |abc
                """);

        canvas.moveCursorLeft(1, 1);

        assertEquals(-1, canvas.cursorCanvasX());
        assertEquals(0, canvas.cursorCanvasY());
    }

    @Test
    public void leftFromInsideGlyphMovesToPreviousGlyphOnVisualLine() {
        SFMDrawCanvasModel canvas = new SFMDrawCanvasModel();
        typeTextAt(canvas, "ABC", 0, 0);
        canvas.setCursor(1.5D, 4.0D);

        canvas.moveCursorLeft(9);

        assertEquals(0, canvas.cursorCanvasX());
        assertEquals(0, canvas.cursorCanvasY());
    }

    @Test
    public void backspaceFromBlankLineDeletesPreviousLineLastGlyph() {
        SFMDrawCanvasModel canvas = fromFixture("""
                abc
                def
                |
                """);

        canvas.deleteLeft();

        assertEquals("""
                abc
                de|
                """, toFixture(canvas));
    }

    @Test
    public void backspaceFromImpliedBlankLineUnshiftsFollowingRows() {
        SFMDrawCanvasModel canvas = new SFMDrawCanvasModel();
        int lineHeight = 9;
        canvas.typeText("""
                every 20 ticks do
                  input from a
                  output to b

                end""", ignored -> 1, lineHeight);
        canvas.setCursor(2, lineHeight * 3);

        canvas.deleteLeft(lineHeight);

        assertEquals("""
                every 20 ticks do
                  input from a
                  output to b
                end""", SFMDrawCanvasSyntaxHighlightingHelper.projectCanvasDocument(canvas.glyphs(), 1, lineHeight).text());
        assertEquals(13, canvas.cursorCanvasX());
        assertEquals(lineHeight * 2, canvas.cursorCanvasY());
    }

    @Test
    public void backspaceFromMultipleImpliedBlankLinesKeepsCursorOnRemainingBlankLine() {
        SFMDrawCanvasModel canvas = fromFixture("""
                a


                |
                b
                """);

        canvas.deleteLeft(1);

        assertEquals("""
                a

                |
                b
                """, toFixture(canvas));
    }

    @Test
    public void backspaceWithinLineDeletesGlyphToLeft() {
        SFMDrawCanvasModel canvas = fromFixture("""
                abc
                de|f
                """);

        canvas.deleteLeft();

        assertEquals("""
                abc
                de|
                """, toFixture(canvas));
    }

    @Test
    public void backspaceInsideGlyphBoundsDeletesNearestGlyphAndClosesGap() {
        SFMDrawCanvasModel canvas = new SFMDrawCanvasModel();
        typeTextAt(canvas, "WWW", 0, 0, 3);
        canvas.setCursor(4.5D, 4.0D);

        canvas.deleteLeft(9);

        assertEquals(2, canvas.glyphs().size());
        assertEquals(0, canvas.glyphs().get(0).x());
        assertEquals(3, canvas.glyphs().get(1).x());
        assertEquals(3, canvas.cursorCanvasX());
        assertEquals(0, canvas.cursorCanvasY());
    }

    @Test
    public void repeatedBackspaceDeletesRepeatedlyToTheLeft() {
        SFMDrawCanvasModel canvas = fromFixture("""
                abc|
                """);

        canvas.deleteLeft();
        canvas.deleteLeft();
        canvas.deleteLeft();

        assertEquals("""
                |
                """, toFixture(canvas));
    }

    @Test
    public void deleteDeletesNearestGlyphAndMovesRight() {
        SFMDrawCanvasModel canvas = fromFixture("""
                a|bc
                """);

        canvas.deleteNearestAndMoveRight();

        assertEquals("""
                a|c
                """, toFixture(canvas));
    }

    @Test
    public void deleteInsideGlyphBoundsDeletesNearestGlyphAndClosesGap() {
        SFMDrawCanvasModel canvas = new SFMDrawCanvasModel();
        typeTextAt(canvas, "WWW", 0, 0, 3);
        canvas.setCursor(4.5D, 4.0D);

        canvas.deleteNearestAndMoveRight(9);

        assertEquals(2, canvas.glyphs().size());
        assertEquals(0, canvas.glyphs().get(0).x());
        assertEquals(3, canvas.glyphs().get(1).x());
        assertEquals(3, canvas.cursorCanvasX());
        assertEquals(0, canvas.cursorCanvasY());
    }

    @Test
    public void deleteAfterLineEndRemovesImpliedBlankLineBeforeNextLine() {
        SFMDrawCanvasModel canvas = fromFixture("""
                a|

                b
                """);

        canvas.deleteNearestAndMoveRight(1);

        assertEquals("""
                a|
                b
                """, toFixture(canvas));
    }

    @Test
    public void repeatedDeleteDeletesRepeatedlyToTheRight() {
        SFMDrawCanvasModel canvas = fromFixture("""
                |abc
                """);

        canvas.deleteNearestAndMoveRight();
        canvas.deleteNearestAndMoveRight();
        canvas.deleteNearestAndMoveRight();

        assertEquals("""
                |
                """, toFixture(canvas));
    }

    @Test
    public void deleteMovesToNextLineWhenDeletedLineIsExtinguished() {
        SFMDrawCanvasModel canvas = fromFixture("""
                abc
                  |d
                ef
                """);

        canvas.deleteNearestAndMoveRight();

        assertEquals("""
                abc

                |ef
                """, toFixture(canvas));
    }

    @Test
    public void rightFromBottomRightGlyphMovesRightBySpaceWidth() {
        SFMDrawCanvasModel canvas = fromFixture("""
                abc|
                """);

        canvas.moveCursorRight(1);

        assertEquals(4, canvas.cursorCanvasX());
        assertEquals(0, canvas.cursorCanvasY());
    }

    @Test
    public void controlLeftMovesToStartOfWordRun() {
        SFMDrawCanvasModel canvas = fromFixture("""
                abc,def|
                """);

        canvas.moveCursorLeftWord(1, 1);

        assertEquals("""
                abc,|def
                """, toFixture(canvas));
    }

    @Test
    public void controlRightMovesToEndOfWordRun() {
        SFMDrawCanvasModel canvas = fromFixture("""
                |abc,def
                """);

        canvas.moveCursorRightWord(1, 1);

        assertEquals("""
                abc|,def
                """, toFixture(canvas));
    }

    @Test
    public void controlRightMovesAcrossNonWordRun() {
        SFMDrawCanvasModel canvas = fromFixture("""
                abc|,.;def
                """);

        canvas.moveCursorRightWord(1, 1);

        assertEquals("""
                abc,.;|def
                """, toFixture(canvas));
    }

    @Test
    public void controlLeftSkipsTrailingExplicitWhitespaceToThePreviousWordStart() {
        SFMDrawCanvasModel canvas = new SFMDrawCanvasModel();
        canvas.typeText("hello  ", ignored -> 1, 1);

        canvas.moveCursorLeftWord(1, 1);

        assertEquals(0.0D, canvas.cursorCanvasX());
    }

    @Test
    public void controlLeftFromTheNextWordStartSkipsInterstitialWhitespace() {
        SFMDrawCanvasModel canvas = new SFMDrawCanvasModel();
        canvas.typeText("hello  world", ignored -> 1, 1);
        canvas.setCursor(7, 0);

        canvas.moveCursorLeftWord(1, 1);

        assertEquals(0.0D, canvas.cursorCanvasX());
    }

    @Test
    public void controlRightSkipsLeadingExplicitWhitespaceToTheNextWordEnd() {
        SFMDrawCanvasModel canvas = new SFMDrawCanvasModel();
        canvas.typeText("  hello", ignored -> 1, 1);
        canvas.setCursor(0, 0);

        canvas.moveCursorRightWord(1, 1);

        assertEquals(7.0D, canvas.cursorCanvasX());
    }

    @Test
    public void addCursorLeftWordTargetsStartOfWordRun() {
        SFMDrawCanvasModel canvas = fromFixture("""
                abc,def|
                """);

        canvas.addCursorLeftWord(1, 1);

        assertEquals(2, canvas.cursors().size());
        assertTrue(hasCursorAt(canvas, 7, 0));
        assertTrue(hasCursorAt(canvas, 4, 0));
    }

    @Test
    public void addCursorRightWordTargetsEndOfWordRun() {
        SFMDrawCanvasModel canvas = fromFixture("""
                |abc,def
                """);

        canvas.addCursorRightWord(1, 1);

        assertEquals(2, canvas.cursors().size());
        assertTrue(hasCursorAt(canvas, 0, 0));
        assertTrue(hasCursorAt(canvas, 3, 0));
    }

    @Test
    public void addCursorUpToGlyphUsesVerticalGlyphJumpTarget() {
        SFMDrawCanvasModel canvas = new SFMDrawCanvasModel();
        typeTextAt(canvas, "every 20 ticks do", 0, 0);
        typeTextAt(canvas, "end", 0, 18);
        canvas.setCursor(3, 18);

        canvas.addCursorUpToGlyph(9);

        assertEquals(2, canvas.cursors().size());
        assertTrue(hasCursorAt(canvas, 3, 18));
        assertTrue(hasCursorAt(canvas, 3, 0));
    }

    @Test
    public void addCursorDownToGlyphUsesVerticalGlyphJumpTarget() {
        SFMDrawCanvasModel canvas = new SFMDrawCanvasModel();
        typeTextAt(canvas, "every 20 ticks do", 0, 0);
        typeTextAt(canvas, "end", 0, 18);
        canvas.setCursor(3, 0);

        canvas.addCursorDownToGlyph(9);

        assertEquals(2, canvas.cursors().size());
        assertTrue(hasCursorAt(canvas, 3, 0));
        assertTrue(hasCursorAt(canvas, 2, 18));
    }

    @Test
    public void homeMovesToBeginningOfLine() {
        SFMDrawCanvasModel canvas = fromFixture("""
                abc
                 d|ef
                """);

        canvas.moveCursorToLineStart();

        assertEquals("""
                abc
                 |def
                """, toFixture(canvas));
    }

    @Test
    public void homeOnEmptyCanvasMovesToXZero() {
        SFMDrawCanvasModel canvas = new SFMDrawCanvasModel();
        canvas.setCursor(12, 34);

        canvas.moveCursorToLineStart();

        assertEquals(0, canvas.cursorCanvasX());
        assertEquals(34, canvas.cursorCanvasY());
    }

    @Test
    public void endMovesToEndOfLine() {
        SFMDrawCanvasModel canvas = fromFixture("""
                abc
                 d|ef
                """);

        canvas.moveCursorToLineEnd();

        assertEquals("""
                abc
                 def|
                """, toFixture(canvas));
    }

    @Test
    public void controlHomeMovesToTopmostThenLeftmostGlyph() {
        SFMDrawCanvasModel canvas = fromFixture("""
                  bc
                a
                   |def
                """);

        canvas.moveCursorToDocumentStart();

        assertEquals("""
                  |bc
                a
                   def
                """, toFixture(canvas));
    }

    @Test
    public void controlHomeOnEmptyCanvasMovesToOrigin() {
        SFMDrawCanvasModel canvas = new SFMDrawCanvasModel();
        canvas.setCursor(12, 34);

        canvas.moveCursorToDocumentStart();

        assertEquals(0, canvas.cursorCanvasX());
        assertEquals(0, canvas.cursorCanvasY());
    }

    @Test
    public void controlEndMovesToBottommostThenRightmostGlyphEnd() {
        SFMDrawCanvasModel canvas = fromFixture("""
                  bc
                a
                d|ef
                 gh
                """);

        canvas.moveCursorToDocumentEnd();

        assertEquals("""
                  bc
                a
                def
                 gh|
                """, toFixture(canvas));
    }

    @Test
    public void upMovesIntoBlankGapWhenPreviousGlyphLineIsFarEnoughAway() {
        SFMDrawCanvasModel canvas = new SFMDrawCanvasModel();
        int lineHeight = 9;
        typeTextAt(canvas, "every 20 ticks do", 0, 0);
        typeTextAt(canvas, "end", 0, lineHeight * 2);
        canvas.setCursor(3, lineHeight * 2);

        canvas.moveCursorUp(lineHeight);

        assertEquals(3, canvas.cursorCanvasX());
        assertEquals(lineHeight, canvas.cursorCanvasY());
    }

    @Test
    public void upFromTopGlyphLineMovesUpByLineHeight() {
        SFMDrawCanvasModel canvas = fromFixture("""
                a|
                """);

        canvas.moveCursorUp(1);

        assertEquals(1, canvas.cursorCanvasX());
        assertEquals(-1, canvas.cursorCanvasY());
    }

    @Test
    public void downFromBottomGlyphLineMovesDownByLineHeight() {
        SFMDrawCanvasModel canvas = fromFixture("""
                a|
                """);

        canvas.moveCursorDown(1);

        assertEquals(1, canvas.cursorCanvasX());
        assertEquals(1, canvas.cursorCanvasY());
    }

    @Test
    public void upToGlyphSkipsBlankGap() {
        SFMDrawCanvasModel canvas = new SFMDrawCanvasModel();
        int lineHeight = 9;
        typeTextAt(canvas, "every 20 ticks do", 0, 0);
        typeTextAt(canvas, "end", 0, lineHeight * 2);
        canvas.setCursor(3, lineHeight * 2);

        canvas.moveCursorUpToGlyph(lineHeight);

        assertEquals(3, canvas.cursorCanvasX());
        assertEquals(0, canvas.cursorCanvasY());
    }

    @Test
    public void downToGlyphSkipsBlankGap() {
        SFMDrawCanvasModel canvas = new SFMDrawCanvasModel();
        int lineHeight = 9;
        typeTextAt(canvas, "every 20 ticks do", 0, 0);
        typeTextAt(canvas, "end", 0, lineHeight * 2);
        canvas.setCursor(3, 0);

        canvas.moveCursorDownToGlyph(lineHeight);

        assertEquals(2, canvas.cursorCanvasX());
        assertEquals(lineHeight * 2, canvas.cursorCanvasY());
    }

    @Test
    public void upStillSnapsToAdjacentLineWhenNoBlankGapExists() {
        SFMDrawCanvasModel canvas = fromFixture("""
                every 20 ticks do
                end|
                """);

        canvas.moveCursorUp(1);

        assertEquals("""
                eve|ry 20 ticks do
                end
                """, toFixture(canvas));
    }

    private static SFMDrawCanvasModel fromFixture(String fixture) {
        SFMDrawCanvasModel canvas = new SFMDrawCanvasModel();
        String[] lines = fixture.stripTrailing().split("\n", -1);
        double cursorX = 0.0D;
        double cursorY = 0.0D;
        boolean cursorFound = false;
        for (int y = 0; y < lines.length; y++) {
            String line = lines[y].stripTrailing();
            int glyphX = 0;
            for (int index = 0; index < line.length(); index++) {
                char c = line.charAt(index);
                if (c == '|') {
                    cursorX = glyphX;
                    cursorY = y;
                    cursorFound = true;
                    continue;
                }
                if (c != ' ') {
                    canvas.setCursor(glyphX, y);
                    canvas.typeGlyph(Character.toString(c), 1);
                }
                glyphX++;
            }
        }
        if (!cursorFound) {
            throw new IllegalArgumentException("Fixture must contain a | cursor marker.");
        }
        canvas.setCursor(cursorX, cursorY);
        return canvas;
    }

    private static void typeTextAt(
            SFMDrawCanvasModel canvas,
            String text,
            double x,
            double y
    ) {
        typeTextAt(canvas, text, x, y, 1);
    }

    private static void typeTextAt(
            SFMDrawCanvasModel canvas,
            String text,
            double x,
            double y,
            int width
    ) {
        canvas.setCursor(x, y);
        for (char c : text.toCharArray()) {
            canvas.typeGlyph(Character.toString(c), width);
        }
    }

    private static String toFixture(SFMDrawCanvasModel canvas) {
        int maxY = (int) canvas.cursorCanvasY();
        int maxX = (int) canvas.cursorCanvasX();
        for (SFMDrawCanvasModel.CanvasGlyph glyph : canvas.glyphs()) {
            maxY = Math.max(maxY, (int) glyph.y());
            maxX = Math.max(maxX, (int) glyph.x() + glyph.width());
        }

        StringBuilder out = new StringBuilder();
        for (int y = 0; y <= maxY; y++) {
            StringBuilder line = new StringBuilder();
            for (int x = 0; x <= maxX; x++) {
                if (Double.compare(canvas.cursorCanvasX(), x) == 0
                    && Double.compare(canvas.cursorCanvasY(), y) == 0) {
                    line.append('|');
                }
                SFMDrawCanvasModel.CanvasGlyph glyph = glyphAt(canvas, x, y);
                if (glyph != null) {
                    line.append(glyph.text());
                } else if (x < maxX) {
                    line.append(' ');
                }
            }
            out.append(line.toString().stripTrailing()).append('\n');
        }
        return out.toString();
    }

    private static SFMDrawCanvasModel.CanvasGlyph glyphAt(
            SFMDrawCanvasModel canvas,
            int x,
            int y
    ) {
        for (SFMDrawCanvasModel.CanvasGlyph glyph : canvas.glyphs()) {
            if (Double.compare(glyph.x(), x) == 0 && Double.compare(glyph.y(), y) == 0) {
                return glyph;
            }
        }
        return null;
    }

    private static boolean hasCursorAt(
            SFMDrawCanvasModel canvas,
            double x,
            double y
    ) {
        for (SFMDrawCanvasModel.CanvasCursor cursor : canvas.cursors()) {
            if (Double.compare(cursor.x(), x) == 0 && Double.compare(cursor.y(), y) == 0) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasCursorNearestToGlyph(
            SFMDrawCanvasModel canvas,
            SFMDrawCanvasModel.CanvasGlyph glyph
    ) {
        for (SFMDrawCanvasModel.CanvasCursor cursor : canvas.cursors()) {
            if (nearestGlyph(canvas, cursor) == glyph) {
                return true;
            }
        }
        return false;
    }

    private static SFMDrawCanvasModel.CanvasGlyph nearestGlyph(
            SFMDrawCanvasModel canvas,
            SFMDrawCanvasModel.CanvasCursor cursor
    ) {
        SFMDrawCanvasModel.CanvasGlyph nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (SFMDrawCanvasModel.CanvasGlyph glyph : canvas.glyphs()) {
            double dx = cursor.x() - glyph.x();
            double dy = cursor.y() - glyph.y();
            double distance = dx * dx + dy * dy;
            if (distance < nearestDistance) {
                nearest = glyph;
                nearestDistance = distance;
            }
        }
        return nearest;
    }
}
