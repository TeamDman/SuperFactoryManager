package ca.teamdman.sfm.client.text_editor.action;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.text_editor.Cursor;
import ca.teamdman.sfm.client.text_editor.TextEditContext;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
import net.minecraft.client.gui.screens.Screen;
{% when '26.1.2' %}
{% endcase %}
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;

public class SwapCursorHeadsAndTailsAction implements ITextEditAction {
    @Override
    public boolean matches(
            TextEditContext context,
            KeyboardImpulse impulse
    ) {
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
        SFM.LOGGER.info("got {} ({}) with {} {} {}", impulse.keyCode(),  GLFW.glfwGetKeyName(impulse.keyCode(), impulse.scanCode()),
                Screen.hasControlDown() ? "control" : "no control",
                Screen.hasAltDown() ? "alt" : "no alt",
                Screen.hasShiftDown() ? "shift" : "no shift");
        return impulse.keyCode() == GLFW.GLFW_KEY_O
               && Screen.hasControlDown()
               && !Screen.hasAltDown()
               && !Screen.hasShiftDown();
{% when '26.1.2' %}
        SFM.LOGGER.info("got {} ({}) with {} {} {}", impulse.event().key(),  GLFW.glfwGetKeyName(impulse.event().key(), impulse.event().scancode()),
                impulse.event().hasControlDown() ? "control" : "no control",
                impulse.event().hasAltDown() ? "alt" : "no alt",
                impulse.event().hasShiftDown() ? "shift" : "no shift");
        return impulse.event().key() == GLFW.GLFW_KEY_O
               && impulse.event().hasControlDown()
               && !impulse.event().hasAltDown()
               && !impulse.event().hasShiftDown();
{% endcase %}
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
