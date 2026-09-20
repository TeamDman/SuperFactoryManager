package ca.teamdman.sfm.client.terminal;

import java.util.Objects;
import java.util.Optional;

/** Immutable requested-versus-active state consumed by panels, actions, and tests. */
public record SFMTerminalPresentationTransitionState(
        SFMTerminalPresentationSelection requested,
        Optional<SFMTerminalPresentationSelection> active,
        Optional<String> failure
) {
    public SFMTerminalPresentationTransitionState {
        Objects.requireNonNull(requested, "requested");
        active = Objects.requireNonNull(active, "active");
        failure = Objects.requireNonNull(failure, "failure");
    }

    public boolean pending() {
        return active.isEmpty() || !active.get().equals(requested);
    }
}
