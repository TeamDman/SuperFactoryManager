package ca.teamdman.sfm.client.context;

import java.util.Objects;

/** One immutable origin, its payload, and the revisions that witnessed it. */
public record SFMContextContribution(
        SFMContextOriginId originId,
        SFMContextGenerationEvidence generations,
        SFMContextProjection projection
) {
    public SFMContextContribution {
        Objects.requireNonNull(originId, "originId");
        Objects.requireNonNull(generations, "generations");
        Objects.requireNonNull(projection, "projection");
    }
}
