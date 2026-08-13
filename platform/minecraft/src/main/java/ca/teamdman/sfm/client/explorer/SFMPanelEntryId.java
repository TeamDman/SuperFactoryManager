package ca.teamdman.sfm.client.explorer;

import java.util.Objects;

/** Stable identity of one stacked panel entry/content instance. */
public record SFMPanelEntryId(String value) implements SFMStableEntityId {
    public SFMPanelEntryId {
        Objects.requireNonNull(value, "value");
        SFMCanonicalText.requireValidUnicode(value, "selector.invalid-panel-entry-id");
        if (value.isEmpty()) throw new IllegalArgumentException("Panel-entry id must not be empty");
    }

    @Override
    public String toString() {
        return value;
    }
}
