package ca.teamdman.sfm.common.template;

import org.jetbrains.annotations.NotNull;

public record SFMDrawTemplate(
        String key,
        String displayName,
        String resourcePath,
        String programString
) implements Comparable<SFMDrawTemplate> {
    @Override
    public int compareTo(@NotNull SFMDrawTemplate other) {
        int displayNameComparison = displayName.compareTo(other.displayName());
        if (displayNameComparison != 0) {
            return displayNameComparison;
        }
        return key.compareTo(other.key());
    }
}