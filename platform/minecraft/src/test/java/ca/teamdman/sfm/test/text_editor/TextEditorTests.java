package ca.teamdman.sfm.test.text_editor;

import ca.teamdman.sfm.client.text_editor.Cursor;
import ca.teamdman.sfm.client.text_editor.TextEditContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TextEditorTests {
    @Test
    public void repeatedSingleCharacterInsertionAppendsAtCursor() {
        TextEditContext context = new TextEditContext();

        context.insertTextAtCursors("a");
        context.insertTextAtCursors("b");
        context.insertTextAtCursors("c");

        assertEquals("abc", context.getContent());
        Cursor cursor = context.multiCursor().cursors().getFirst();
        assertEquals(0, cursor.head().lineIndex());
        assertEquals(3, cursor.head().gapIndex());
        assertEquals(cursor.head(), cursor.tail());
    }

    @Test
    public void insertionMovesCursorToEndOfInsertedText() {
        TextEditContext context = new TextEditContext();

        context.insertTextAtCursors("Ahoy");

        assertEquals("Ahoy", context.getContent());
        Cursor cursor = context.multiCursor().cursors().getFirst();
        assertEquals(0, cursor.head().lineIndex());
        assertEquals(4, cursor.head().gapIndex());
        assertEquals(cursor.head(), cursor.tail());
    }
}
