package ca.teamdman.sfm.client.text_editor.action;

import ca.teamdman.sfm.client.text_editor.TextEditContext;

public interface ITextEditAction {
    boolean matches(
            TextEditContext context,
            KeyboardImpulse impulse
    );

    void apply(
            TextEditContext context,
            KeyboardImpulse impulse
    );

    default float priority() {
        return 0.0f;
    }
}
