package ca.teamdman.sfm.client.text_editor.action;

import ca.teamdman.sfm.client.text_editor.Cursor;
import ca.teamdman.sfm.client.text_editor.TextEditContext;
import it.unimi.dsi.fastutil.ints.Int2IntFunction;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;

public class MoveCursorsUpOneLine implements ITextEditAction {
    @Override
    public boolean matches(
            TextEditContext context,
            KeyboardImpulse impulse
    ) {
        return impulse.keyCode() == GLFW.GLFW_KEY_UP && !Screen.hasShiftDown() && !Screen.hasControlDown() && !Screen.hasAltDown();
    }

    @Override
    public void apply(
            TextEditContext context,
            KeyboardImpulse impulse
    ) {
        ArrayDeque<Cursor> cursors = context.multiCursor().cursors();
        ArrayDeque<Cursor> newCursors = new ArrayDeque<>();
        Int2IntFunction lineLengths = context.lineLengths();
        for (Cursor cursor : cursors) {
            var head = cursor.head().moveUpOneLine(lineLengths);
            var tail = cursor.tail().moveUpOneLine(lineLengths);
            newCursors.add(new Cursor(tail, head));
        }
        cursors.clear();
        cursors.addAll(newCursors);
    }
}
