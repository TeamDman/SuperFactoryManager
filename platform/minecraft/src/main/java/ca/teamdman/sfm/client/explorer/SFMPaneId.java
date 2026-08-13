package ca.teamdman.sfm.client.explorer;

import java.util.Objects;

/** Reserved typed pane identity; live resolution waits for the X-10 layout cutover. */
public record SFMPaneId(String value) implements SFMStableEntityId {
    public SFMPaneId {
        Objects.requireNonNull(value, "value");
        SFMCanonicalText.requireValidUnicode(value, "selector.invalid-pane-id");
        if (value.isEmpty()) throw new IllegalArgumentException("Pane id must not be empty");
    }

    @Override
    public String toString() {
        return value;
    }
}
