package ca.teamdman.sfm.client.keybinding;

import java.util.List;

public record SFMKeySequence(List<SFMKeyStroke> strokes) {
    public SFMKeySequence {
        strokes = List.copyOf(strokes);
        if (strokes.isEmpty()) throw new IllegalArgumentException("A key sequence needs at least one stroke");
    }

    public static SFMKeySequence of(SFMKeyStroke... strokes) {
        return new SFMKeySequence(List.of(strokes));
    }
}
