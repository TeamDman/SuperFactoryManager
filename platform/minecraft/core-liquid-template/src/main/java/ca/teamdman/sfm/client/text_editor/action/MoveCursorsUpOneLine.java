package ca.teamdman.sfm.client.text_editor.action;

import ca.teamdman.sfm.client.text_editor.Cursor;
import ca.teamdman.sfm.client.text_editor.TextEditContext;
import it.unimi.dsi.fastutil.ints.Int2IntFunction;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
import net.minecraft.client.gui.screens.Screen;
{% when '26.1.2' %}
{% endcase %}
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;

public class MoveCursorsUpOneLine implements ITextEditAction {
    @Override
    public boolean matches(
            TextEditContext context,
            KeyboardImpulse impulse
    ) {
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
        return impulse.keyCode() == GLFW.GLFW_KEY_UP && !Screen.hasShiftDown() && !Screen.hasControlDown() && !Screen.hasAltDown();
{% when '26.1.2' %}
        return impulse.event().key() == GLFW.GLFW_KEY_UP && !impulse.event().hasShiftDown() && !impulse.event().hasControlDown() && !impulse.event().hasAltDown();
{% endcase %}
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
