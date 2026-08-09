package ca.teamdman.sfm.client.text_editor.action;

import ca.teamdman.sfm.client.text_editor.Cursor;
import ca.teamdman.sfm.client.text_editor.TextEditContext;
import it.unimi.dsi.fastutil.ints.Int2IntFunction;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;

public class MoveCursorsDownOneLine implements ITextEditAction {
    @Override
    public boolean matches(
            TextEditContext context,
            KeyboardImpulse impulse
    ) {
        return impulse.keyCode() == GLFW.GLFW_KEY_DOWN && !Screen.hasShiftDown() && !Screen.hasControlDown() && !Screen.hasAltDown();
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
            var head = cursor.head().moveDownOneLine(lineLengths, numLines);
            var tail = cursor.tail().moveDownOneLine(lineLengths, numLines);
            newCursors.add(new Cursor(tail, head));
        }
        cursors.clear();
        cursors.addAll(newCursors);
    }
}
