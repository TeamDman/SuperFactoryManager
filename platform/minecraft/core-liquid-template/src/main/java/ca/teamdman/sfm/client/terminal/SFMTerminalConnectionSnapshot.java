package ca.teamdman.sfm.client.terminal;

import java.util.Objects;
import java.util.Optional;

/** One lock-consistent remote lifecycle observation for a UI update. */
public record SFMTerminalConnectionSnapshot(
        boolean connected,
        boolean connecting,
        boolean presentationReady,
        long interactionEpoch,
        Optional<String> failure,
        Optional<SFMTerminalError> inputFailure
) {
    public SFMTerminalConnectionSnapshot(
            boolean connected, boolean connecting, boolean presentationReady,
            long interactionEpoch, Optional<String> failure) {
        this(connected, connecting, presentationReady, interactionEpoch, failure, Optional.empty());
    }

    public SFMTerminalConnectionSnapshot {
        failure = Objects.requireNonNull(failure);
        inputFailure = Objects.requireNonNull(inputFailure);
    }

    /** Input rejection is visible without making the connection itself retryable. */
    public Optional<String> displayFailure() {
        return failure.or(() -> inputFailure.map(error ->
                "Terminal input rejected: " + error.message()
                        + (error.retryable() ? " (retry manually)" : "")));
    }
}
