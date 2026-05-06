package ca.teamdman.sfm.client.text_editor.action;

import ca.teamdman.sfm.client.text_editor.Cursor;
import ca.teamdman.sfm.client.text_editor.TextEditContext;
import it.unimi.dsi.fastutil.ints.Int2IntFunction;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;

public class MoveCursorsRightOneCharacter implements ITextEditAction {
    @Override
    public boolean matches(
            TextEditContext context,
            KeyboardImpulse impulse
    ) {
        return impulse.event().key() == GLFW.GLFW_KEY_RIGHT && !impulse.event().hasShiftDown() && !impulse.event().hasControlDown() && !impulse.event().hasAltDown();
    }

    @Override
    public void apply(
            TextEditContext context,
            KeyboardImpulse impulse
    ) {
        ArrayDeque<Cursor> cursors = context.multiCursor().cursors();
        ArrayDeque<Cursor> newCursors = new ArrayDeque<>();
        Int2IntFunction lineLengths = context.lineLengths();
        int numLines = context.lines().size();
        for (Cursor cursor : cursors) {
            var head = cursor.head().moveRightOneCharacter(lineLengths, numLines);
            var tail = cursor.tail().moveRightOneCharacter(lineLengths, numLines);
            newCursors.add(new Cursor(tail, head));
        }
        cursors.clear();
        cursors.addAll(newCursors);
    }
}
