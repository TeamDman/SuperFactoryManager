package ca.teamdman.sfm.client.text_editor.action;

import ca.teamdman.sfm.client.text_editor.Caret;
import ca.teamdman.sfm.client.text_editor.Cursor;
import ca.teamdman.sfm.client.text_editor.TextEditContext;
import net.minecraft.client.gui.screens.Screen;

public class SelectAllTextAction implements ITextEditAction {
    @Override
    public boolean matches(
            TextEditContext context,
            KeyboardImpulse impulse
    ) {
        return Screen.isSelectAll(impulse.keyCode());
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
