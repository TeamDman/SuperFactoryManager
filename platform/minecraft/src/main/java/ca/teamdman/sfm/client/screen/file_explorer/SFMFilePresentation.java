package ca.teamdman.sfm.client.screen.file_explorer;

import java.util.Objects;

/** Rendering metadata. It does not affect filesystem behavior. */
public record SFMFilePresentation(
        String icon,
        String kindLabel,
        int textColour,
        Emphasis emphasis
) {
    public enum Emphasis {
        NORMAL,
        BOLD,
        ITALIC
    }

    public SFMFilePresentation {
        Objects.requireNonNull(icon, "icon");
        Objects.requireNonNull(kindLabel, "kindLabel");
        Objects.requireNonNull(emphasis, "emphasis");
    }
}
