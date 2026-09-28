package ca.teamdman.sfm.client.text_editor.action;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.text_editor.Cursor;
import ca.teamdman.sfm.client.text_editor.TextEditContext;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;

public class SwapCursorHeadsAndTailsAction implements ITextEditAction {
    @Override
    public boolean matches(
            TextEditContext context,
            KeyboardImpulse impulse
    ) {
        SFM.LOGGER.info("got {} ({}) with {} {} {}", impulse.event().key(),  GLFW.glfwGetKeyName(impulse.event().key(), impulse.event().scancode()),
                impulse.event().hasControlDown() ? "control" : "no control",
                impulse.event().hasAltDown() ? "alt" : "no alt",
                impulse.event().hasShiftDown() ? "shift" : "no shift");
        return impulse.event().key() == GLFW.GLFW_KEY_O
               && impulse.event().hasControlDown()
               && !impulse.event().hasAltDown()
               && !impulse.event().hasShiftDown();
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
