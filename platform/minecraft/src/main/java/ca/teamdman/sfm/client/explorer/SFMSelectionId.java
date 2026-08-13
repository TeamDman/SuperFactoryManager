package ca.teamdman.sfm.client.explorer;

import java.util.Objects;

/** Stable identity of one versioned selection entity. */
public record SFMSelectionId(String value) implements SFMStableEntityId {
    public SFMSelectionId {
        Objects.requireNonNull(value, "value");
        SFMCanonicalText.requireValidUnicode(value, "selector.invalid-selection-id");
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Selection id must not be empty");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
