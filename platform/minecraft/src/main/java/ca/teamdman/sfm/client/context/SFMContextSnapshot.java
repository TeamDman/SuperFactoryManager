package ca.teamdman.sfm.client.context;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Complete immutable multi-origin context captured at one workspace generation. */
public record SFMContextSnapshot(
        long captureGeneration,
        long workspaceGeneration,
        long focusGeneration,
        Optional<SFMContextOriginId> focusedOriginId,
        List<SFMContextContribution> contributions
) {
    public SFMContextSnapshot {
        if (captureGeneration < 0 || workspaceGeneration < 0 || focusGeneration < 0) {
            throw new IllegalArgumentException("Snapshot generations must not be negative");
        }
        focusedOriginId = Objects.requireNonNull(focusedOriginId, "focusedOriginId");
        contributions = List.copyOf(contributions);
        HashSet<SFMContextOriginId> origins = new HashSet<>();
        for (SFMContextContribution contribution : contributions) {
            if (!origins.add(contribution.originId())) {
                throw new IllegalArgumentException("Duplicate context origin " + contribution.originId());
            }
        }
    }

    /** 0: exact focused origin, 1: another origin in its container, 2: other visible origin. */
    public int focusRank(SFMContextOriginId originId) {
        Objects.requireNonNull(originId, "originId");
        if (focusedOriginId.isEmpty()) return 2;
        SFMContextOriginId focused = focusedOriginId.orElseThrow();
        if (focused.equals(originId)) return 0;
        return focused.sharesContainerWith(originId) ? 1 : 2;
    }
}
