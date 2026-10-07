package ca.teamdman.sfm.client.screen.workspace;

import java.util.Objects;

/** Stable identity for one panel slot/stack, independent from its active content. */
public record SFMWorkspaceStackId(String value) {
    public SFMWorkspaceStackId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) throw new IllegalArgumentException("Stack id must not be blank");
    }

    @Override
    public String toString() {
        return value;
    }
}
