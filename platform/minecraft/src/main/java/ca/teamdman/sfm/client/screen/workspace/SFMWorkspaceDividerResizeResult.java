package ca.teamdman.sfm.client.screen.workspace;

import java.util.Map;

/** Machine-readable before/after evidence for one constrained divider transaction. */
public record SFMWorkspaceDividerResizeResult(
        Status status,
        Map<SFMWorkspaceDividerId, Integer> appliedDeltas,
        Map<SFMWorkspacePanelId, SFMScreenPanelBounds> beforeBounds,
        Map<SFMWorkspacePanelId, SFMScreenPanelBounds> afterBounds
) {
    public SFMWorkspaceDividerResizeResult {
        appliedDeltas = Map.copyOf(appliedDeltas);
        beforeBounds = Map.copyOf(beforeBounds);
        afterBounds = Map.copyOf(afterBounds);
    }

    public boolean changed() {
        return !beforeBounds.equals(afterBounds);
    }

    public enum Status {
        APPLIED,
        CLAMPED,
        UNCHANGED,
        STALE,
        UNAVAILABLE
    }
}
