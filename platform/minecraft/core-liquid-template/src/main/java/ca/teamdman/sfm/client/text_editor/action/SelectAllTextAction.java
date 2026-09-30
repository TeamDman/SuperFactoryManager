package ca.teamdman.sfm.client.text_editor.action;

import ca.teamdman.sfm.client.text_editor.Caret;
import ca.teamdman.sfm.client.text_editor.Cursor;
import ca.teamdman.sfm.client.text_editor.TextEditContext;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
import net.minecraft.client.gui.screens.Screen;
{% when '26.1.2' %}
{% endcase %}

public class SelectAllTextAction implements ITextEditAction {
    @Override
    public boolean matches(
            TextEditContext context,
            KeyboardImpulse impulse
    ) {
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
        return Screen.isSelectAll(impulse.keyCode());
{% when '26.1.2' %}
        return impulse.event().isSelectAll();
{% endcase %}
    }

    @Override
    public void apply(
            TextEditContext context,
            KeyboardImpulse impulse
    ) {
        context.multiCursor().cursors().clear();
        context
                .multiCursor()
                .cursors()
                .add(new Cursor(
                        new Caret(0, 0),
                        new Caret(context.lines().size() - 1, context.lines().getLast().length())
                ));
    }
}
