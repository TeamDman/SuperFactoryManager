package ca.teamdman.sfm.client.context;

import java.util.List;
import java.util.Optional;

/** Narrow adapter seam for a visible panel or another independently scoped source. */
public interface SFMContextContributor {
    /** Stable contributor kind/namespace; multiple visible instances may share it. */
    String id();

    /**
     * Origin corresponding to this contributor's focused child, when it has
     * enough local focus information to identify one without capturing content.
     */
    default Optional<SFMContextOriginId> focusedOriginId() {
        return Optional.empty();
    }

    List<SFMContextContribution> capture(SFMContextCaptureRequest request);
}
