package ca.teamdman.sfm.client.ide.session;

import java.util.Optional;

public record IdeSessionTarget(IdeTargetKind kind, Optional<String> display, Optional<String> location, Optional<String> dimensionId) {
    public static IdeSessionTarget none() {
        return new IdeSessionTarget(IdeTargetKind.NONE, Optional.empty(), Optional.empty(), Optional.empty());
    }

    public boolean isBound() {
        return kind != IdeTargetKind.NONE;
    }

    public Optional<String> summary() {
        return display.map(value -> location
                .filter(it -> !it.isBlank())
                .map(it -> value + " @ " + it)
                .orElse(value)
        );
    }
}