package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;

import java.util.Objects;

/** Naturally types a bounded BMP string into the currently displayed screen. */
public final class TypeScreenTextPuppetAction implements SFMPuppetAction {
    private final String text;
    private int index;

    public TypeScreenTextPuppetAction(String text) {
        this.text = Objects.requireNonNull(text, "text");
        if (text.isEmpty()) throw new IllegalArgumentException("Screen text must not be empty");
        for (int offset = 0; offset < text.length(); offset++) {
            if (Character.isSurrogate(text.charAt(offset))) {
                throw new IllegalArgumentException(
                        "Screen text automation accepts BMP characters only; surrogate at UTF-16 offset " + offset
                );
            }
        }
    }

    @Override
    public String description() {
        return "type screen text at UTF-16 offset " + index + " of " + text.length();
    }

    @Override
    public boolean tick(ISFMGamePuppetRuntime runtime) {
        runtime.typeScreenCharacter(text.charAt(index), 0);
        index++;
        return index == text.length();
    }
}
