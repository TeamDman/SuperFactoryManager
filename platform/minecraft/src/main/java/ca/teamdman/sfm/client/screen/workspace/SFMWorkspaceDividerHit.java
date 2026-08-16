package ca.teamdman.sfm.client.screen.workspace;

import java.util.List;
import java.util.Objects;

/** Deterministic divider set selected at one pointer coordinate. */
public record SFMWorkspaceDividerHit(
        List<SFMWorkspaceDivider> dividers,
        SFMWorkspaceDividerCursor cursor
) {
    public SFMWorkspaceDividerHit {
        dividers = List.copyOf(dividers);
        Objects.requireNonNull(cursor, "cursor");
        if (dividers.isEmpty() && cursor != SFMWorkspaceDividerCursor.DEFAULT) {
            throw new IllegalArgumentException("An empty hit must use the default cursor");
        }
    }

    public static SFMWorkspaceDividerHit empty() {
        return new SFMWorkspaceDividerHit(List.of(), SFMWorkspaceDividerCursor.DEFAULT);
    }

    public boolean isEmpty() {
        return dividers.isEmpty();
    }
}
