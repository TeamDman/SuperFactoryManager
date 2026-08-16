package ca.teamdman.sfm.client.screen.workspace;

import java.util.Objects;

/** Explicit semantic linkage for dividers that must move together. */
public record SFMWorkspaceDividerLinkId(String value) {
    public SFMWorkspaceDividerLinkId {
        Objects.requireNonNull(value, "value");
        if (!value.matches("[a-zA-Z0-9_.-]+")) {
            throw new IllegalArgumentException("Divider link id must be action-token safe: " + value);
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
