package ca.teamdman.sfm.client.terminal;

import java.util.Objects;
import java.util.Optional;

/** One lock-consistent remote lifecycle observation for a UI update. */
public record SFMTerminalConnectionSnapshot(
        boolean connected,
        boolean connecting,
        boolean presentationReady,
        long interactionEpoch,
        Optional<String> failure
) {
    public SFMTerminalConnectionSnapshot {
        failure = Objects.requireNonNull(failure);
    }
}
