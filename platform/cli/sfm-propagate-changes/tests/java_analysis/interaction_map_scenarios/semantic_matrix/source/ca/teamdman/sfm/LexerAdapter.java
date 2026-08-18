package ca.teamdman.sfm;

import java.io.Serial;
import java.io.Serializable;

@Mod
public final class LexerAdapter implements java.io.Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    private final LocalizationEntry location = new LocalizationEntry();
    private final String text = "lexer";

    String scan(String input) {
        if (input.isEmpty()) {
            return text;
        }
        return input;
    }
}
