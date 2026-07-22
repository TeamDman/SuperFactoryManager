package ca.teamdman.sfm.client.screen.theme_settings;

import java.util.Objects;

public record SFMThemeProperty(Kind kind, String id, String label) {
    public enum Kind { COLOUR, SYNTAX, FILE_ICON, ACTION_ICON }

    public SFMThemeProperty {
        Objects.requireNonNull(kind);
        id = Objects.requireNonNull(id).strip();
        label = Objects.requireNonNull(label).strip();
        if (id.isEmpty() || label.isEmpty()) throw new IllegalArgumentException("Theme property text must not be blank");
    }
}
