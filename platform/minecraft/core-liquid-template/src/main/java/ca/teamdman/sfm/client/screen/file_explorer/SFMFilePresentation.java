package ca.teamdman.sfm.client.screen.file_explorer;

import ca.teamdman.sfm.client.presentation.SFMItemIcon;

import java.util.Objects;

/** Rendering metadata. It does not affect filesystem behavior. */
public record SFMFilePresentation(
        SFMItemIcon itemIcon,
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
        Objects.requireNonNull(itemIcon, "itemIcon");
        Objects.requireNonNull(kindLabel, "kindLabel");
        Objects.requireNonNull(emphasis, "emphasis");
    }
}
