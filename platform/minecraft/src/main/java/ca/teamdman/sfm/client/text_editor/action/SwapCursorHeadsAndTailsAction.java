package ca.teamdman.sfm.client.text_editor.action;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.text_editor.Cursor;
import ca.teamdman.sfm.client.text_editor.TextEditContext;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;

public class SwapCursorHeadsAndTailsAction implements ITextEditAction {
    @Override
    public boolean matches(
            TextEditContext context,
            KeyboardImpulse impulse
    ) {
        SFM.LOGGER.info("got {} ({}) with {} {} {}", impulse.keyCode(),  GLFW.glfwGetKeyName(impulse.keyCode(), impulse.scanCode()),
                Screen.hasControlDown() ? "control" : "no control",
                Screen.hasAltDown() ? "alt" : "no alt",
                Screen.hasShiftDown() ? "shift" : "no shift");
        return impulse.keyCode() == GLFW.GLFW_KEY_O
               && Screen.hasControlDown()
               && !Screen.hasAltDown()
               && !Screen.hasShiftDown();
    }

    @Override
    public void apply(
            TextEditContext context,
            KeyboardImpulse impulse
    ) {
        ArrayDeque<Cursor> cursors = context.multiCursor().cursors();
        ArrayDeque<Cursor> newCursors = new ArrayDeque<>();
        for (Cursor cursor : cursors) {
            newCursors.add(new Cursor(cursor.head(), cursor.tail()));
        }
        cursors.clear();
        cursors.addAll(newCursors);
    }
}
