package ca.teamdman.sfm.client.text_editor.action;

import ca.teamdman.sfm.client.text_editor.Cursor;
import ca.teamdman.sfm.client.text_editor.TextEditContext;
import it.unimi.dsi.fastutil.ints.Int2IntFunction;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.Iterator;

public class DeleteSelectionOrCharacterToTheLeftForEachCursorAction implements ITextEditAction {
    @Override
    public boolean matches(
            TextEditContext context,
            KeyboardImpulse impulse
    ) {
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
        return impulse.keyCode() == GLFW.GLFW_KEY_BACKSPACE;
{% when '26.1.2' %}
        return impulse.event().key() == GLFW.GLFW_KEY_BACKSPACE;
{% endcase %}
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
                newCursors.push(cursor.growSelectionLeft(lineLengths));
            }
        }
        context.multiCursor().cursors().addAll(newCursors);
        context.deleteSelectedText();
    }
}
