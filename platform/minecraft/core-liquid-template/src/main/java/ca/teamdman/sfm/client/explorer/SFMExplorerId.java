package ca.teamdman.sfm.client.explorer;

import java.util.Objects;

/** Stable identity of one explorer component/session. */
public record SFMExplorerId(String value) implements SFMStableEntityId {
    public SFMExplorerId {
        Objects.requireNonNull(value, "value");
        SFMCanonicalText.requireValidUnicode(value, "selector.invalid-explorer-id");
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Explorer id must not be empty");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
