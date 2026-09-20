package ca.teamdman.sfm.client.explorer;

import java.util.Objects;

/** Stable start-scoped identity of one running game instance. */
public record SFMGameInstanceId(String value) implements SFMStableEntityId {
    public SFMGameInstanceId {
        Objects.requireNonNull(value, "value");
        SFMCanonicalText.requireValidUnicode(value, "selector.invalid-game-id");
        if (value.isEmpty()) throw new IllegalArgumentException("Game id must not be empty");
    }

    @Override
    public String toString() {
        return value;
    }
}
