package ca.teamdman.sfm.client.context;

import java.util.Objects;

/** Immutable primary or supplemental editor cursor. */
public record SFMContextCursorProjection(
        String id,
        SFMContextPosition position,
        boolean primary,
        boolean active
) {
    public SFMContextCursorProjection {
        Objects.requireNonNull(id, "id");
        if (id.isBlank()) throw new IllegalArgumentException("Cursor id must not be blank");
        Objects.requireNonNull(position, "position");
    }
}
