package ca.teamdman.sfm.client.context;

import java.util.Objects;
import java.util.Optional;

/** Immutable workspace/focus witness passed to every contributor in one capture. */
public record SFMContextCaptureRequest(
        long captureGeneration,
        long workspaceGeneration,
        long focusGeneration,
        Optional<SFMContextOriginId> focusedOriginId
) {
    public SFMContextCaptureRequest {
        if (captureGeneration < 0 || workspaceGeneration < 0 || focusGeneration < 0) {
            throw new IllegalArgumentException("Capture generations must not be negative");
        }
        focusedOriginId = Objects.requireNonNull(focusedOriginId, "focusedOriginId");
    }
}
