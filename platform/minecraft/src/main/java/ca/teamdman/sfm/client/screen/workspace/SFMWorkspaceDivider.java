package ca.teamdman.sfm.client.screen.workspace;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/** One action-addressable divider and its current logical geometry/constraints. */
public record SFMWorkspaceDivider(
        SFMWorkspaceDividerId id,
        SFMWorkspaceAxis axis,
        SFMScreenPanelBounds lineBounds,
        SFMScreenPanelBounds hitBounds,
        int position,
        int minimumPosition,
        int maximumPosition,
        int beforePixels,
        int afterPixels,
        int beforeMinimumPixels,
        int afterMinimumPixels,
        double beforeShare,
        double afterShare,
        List<SFMWorkspacePanelId> beforeTrackPanels,
        List<SFMWorkspacePanelId> afterTrackPanels,
        @Nullable SFMWorkspaceDividerLinkId linkId
) {
    public SFMWorkspaceDivider {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(axis, "axis");
        Objects.requireNonNull(lineBounds, "lineBounds");
        Objects.requireNonNull(hitBounds, "hitBounds");
        beforeTrackPanels = List.copyOf(beforeTrackPanels);
        afterTrackPanels = List.copyOf(afterTrackPanels);
        if (minimumPosition > position || position > maximumPosition) {
            throw new IllegalArgumentException("Divider position must be inside its movement interval");
        }
    }

    public int minimumDelta() {
        return minimumPosition - position;
    }

    public int maximumDelta() {
        return maximumPosition - position;
    }
}
