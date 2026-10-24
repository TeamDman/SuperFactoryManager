package ca.teamdman.sfm.client.text_editor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SFMVimModalStateTests {
    private static void commands(SFMVimModalState editor, String commands) {
        for (char command : commands.toCharArray()) assertTrue(editor.command(command, false));
    }

    @Test
    void normalCommandsDoNotInsertTheirCharacters() {
        var editor = new SFMVimModalState("alpha\nbeta");
        commands(editor, "llhjk0$");
        assertEquals("alpha\nbeta", editor.snapshot().text());
        assertEquals(4, editor.snapshot().cursor());
        assertEquals(SFMVimModalState.Mode.NORMAL, editor.mode());
    }

    @Test
    void insertEscapeAndUndoPreserveExactWhitespaceAndUnicode() {
        var editor = new SFMVimModalState("  α😀\n\n");
        commands(editor, "A");
        assertEquals(5, editor.snapshot().cursor());
        editor.acceptWidgetValue("  α😀 text\n\n", 10);
        editor.acceptWidgetValue("  α😀 text!\n\n", 11);
        editor.escape();
        assertEquals("  α😀 text!\n\n", editor.snapshot().text());
        assertEquals(SFMVimModalState.Mode.NORMAL, editor.mode());
        editor.undo();
        assertEquals("  α😀\n\n", editor.snapshot().text());
        editor.redo();
        assertEquals("  α😀 text!\n\n", editor.snapshot().text());
        editor.undo();
        editor.undo();
        assertEquals("  α😀\n\n", editor.snapshot().text(), "Insert session is one undo block");
    }

    @Test
    void insertionEntryPointsUseGapPositions() {
        var editor = new SFMVimModalState("  abc");
        commands(editor, "I");
        assertEquals(2, editor.snapshot().cursor());
        editor.escape();
        commands(editor, "0a");
        assertEquals(1, editor.snapshot().cursor());
        editor.escape();
        commands(editor, "$a");
        assertEquals(5, editor.snapshot().cursor());
        editor.escape();
        commands(editor, "i");
        assertEquals(4, editor.snapshot().cursor());
    }

    @Test
    void escapeAtLineStartDoesNotCrossToPreviousLine() {
        var editor = new SFMVimModalState("abc\ndef");
        commands(editor, "ji");
        editor.escape();
        assertEquals(4, editor.snapshot().cursor());
        assertEquals("abc\ndef", editor.snapshot().text());
    }

    @Test
    void horizontalMotionsAndDeleteRespectCodePointsAndLineBounds() {
        var editor = new SFMVimModalState("a😀b\nc");
        commands(editor, "lx");
        assertEquals("ab\nc", editor.snapshot().text());
        editor.undo();
        assertEquals("a😀b\nc", editor.snapshot().text());
        assertEquals(1, editor.snapshot().cursor());
        commands(editor, "ll");
        assertEquals(3, editor.snapshot().cursor());
        commands(editor, "0hh");
        assertEquals(0, editor.snapshot().cursor());
    }

    @Test
    void verticalMotionsRememberDesiredColumnAcrossShortLines() {
        var editor = new SFMVimModalState("abcdef\nx\nabcdef");
        commands(editor, "lllljj");
        assertEquals(13, editor.snapshot().cursor());
        commands(editor, "kk");
        assertEquals(4, editor.snapshot().cursor());
    }

    @Test
    void wordMotionsDistinguishWordsPunctuationAndWhitespace() {
        var editor = new SFMVimModalState("foo.bar  baz\nqux");
        commands(editor, "w"); assertEquals(3, editor.snapshot().cursor());
        commands(editor, "w"); assertEquals(4, editor.snapshot().cursor());
        commands(editor, "w"); assertEquals(9, editor.snapshot().cursor());
        commands(editor, "w"); assertEquals(13, editor.snapshot().cursor());
        commands(editor, "bb"); assertEquals(4, editor.snapshot().cursor());
    }

    @Test
    void lineDeletionHandlesFirstMiddleLastAndOnlyLines() {
        var editor = new SFMVimModalState("first\nsecond\nthird");
        commands(editor, "jdd"); assertEquals("first\nthird", editor.snapshot().text());
        editor.undo(); assertEquals("first\nsecond\nthird", editor.snapshot().text());
        commands(editor, "jdd"); assertEquals("first\nsecond", editor.snapshot().text());
        commands(editor, "kdd"); assertEquals("second", editor.snapshot().text());
        commands(editor, "dd"); assertEquals("", editor.snapshot().text());
        commands(editor, "ddxhjkl0$wb"); assertEquals("", editor.snapshot().text());
    }

    @Test
    void pendingDeleteIsCancelledByEscapeAndUnsupportedMotions() {
        var editor = new SFMVimModalState("safe");
        commands(editor, "d"); assertTrue(editor.pendingDelete());
        editor.escape(); assertFalse(editor.pendingDelete());
        commands(editor, "dx");
        assertEquals("safe", editor.snapshot().text());
        assertFalse(editor.command('.', false));
        assertFalse(editor.command(':', false));
        assertEquals("safe", editor.snapshot().text());
    }

    @Test
    void readOnlyCommandsCannotChangeTextOrEnterInsert() {
        var editor = new SFMVimModalState("safe\ntext");
        for (char command : "iaIAxdddu".toCharArray()) assertTrue(editor.command(command, true));
        assertEquals("safe\ntext", editor.snapshot().text());
        assertEquals(SFMVimModalState.Mode.NORMAL, editor.mode());
        assertFalse(editor.pendingDelete());
        assertTrue(editor.command('j', true));
        assertEquals(5, editor.snapshot().cursor());
    }

    @Test
    void newEditAfterUndoInvalidatesRedo() {
        var editor = new SFMVimModalState("abc");
        commands(editor, "x"); editor.undo(); commands(editor, "$x"); editor.redo();
        assertEquals("ab", editor.snapshot().text());
    }

    @Test
    void nativeWidgetChangesAreUndoableAndCursorClampsAtUnicodeBoundaries() {
        var editor = new SFMVimModalState("😀hello");
        editor.acceptWidgetValue("😀hello", 1);
        assertEquals(0, editor.snapshot().cursor());
        editor.acceptWidgetValue("changed\n\n", Integer.MAX_VALUE);
        editor.undo(); assertEquals("😀hello", editor.snapshot().text());
        editor.redo(); assertEquals("changed\n\n", editor.snapshot().text());
    }

    @Test
    void emptyInsertIsNotAnUndoStepAndHistoryIsBounded() {
        var editor = new SFMVimModalState("a".repeat(120));
        commands(editor, "i"); editor.escape();
        for (int i = 0; i < 120; i++) commands(editor, "x");
        for (int i = 0; i < 120; i++) editor.undo();
        assertEquals(100, editor.snapshot().text().length());
    }

    @Test
    void undoWhileInInsertCommitsTheSessionThenRevertsIt() {
        var editor = new SFMVimModalState("");
        commands(editor, "i"); editor.acceptWidgetValue("hello", 5);
        editor.undo(); assertEquals("", editor.snapshot().text());
        assertEquals(SFMVimModalState.Mode.NORMAL, editor.mode());
        editor.redo(); assertEquals("hello", editor.snapshot().text());
    }
}
