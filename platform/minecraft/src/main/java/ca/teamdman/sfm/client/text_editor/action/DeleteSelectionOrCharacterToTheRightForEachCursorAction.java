package ca.teamdman.sfm.client.text_editor.action;

import ca.teamdman.sfm.client.text_editor.Cursor;
import ca.teamdman.sfm.client.text_editor.TextEditContext;
import it.unimi.dsi.fastutil.ints.Int2IntFunction;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.Iterator;

public class DeleteSelectionOrCharacterToTheRightForEachCursorAction implements ITextEditAction {
    @Override
    public boolean matches(
            TextEditContext context,
            KeyboardImpulse impulse
    ) {
        return impulse.keyCode() == GLFW.GLFW_KEY_DELETE;
    }

    @Override
    public void apply(
            TextEditContext context,
            KeyboardImpulse impulse
    ) {
        ArrayDeque<Cursor> newCursors = new ArrayDeque<>();
        Iterator<Cursor> cursorIterator = context.multiCursor().cursors().iterator();
        Int2IntFunction lineLengths = context.lineLengths();
        while (cursorIterator.hasNext()) {
            Cursor cursor = cursorIterator.next();
            if (!cursor.hasSelection()) {
                cursorIterator.remove();
                newCursors.push(cursor.growSelectionRight(lineLengths, context.lines().size()));
            }
        }
        context.multiCursor().cursors().addAll(newCursors);
        context.deleteSelectedText();
    }
}
