package ca.teamdman.sfm.client.ide.session;

import java.util.Optional;

public record IdeShellContextSnapshot(
        Optional<String> playerPosition,
        Optional<String> lookVector,
        Optional<String> hitSummary,
        Optional<String> dimensionId,
        Optional<String> focusedPanelDisplay,
        Optional<String> focusedTargetSummary,
        int selectedTargetCount
) {
    public static IdeShellContextSnapshot empty() {
        return new IdeShellContextSnapshot(
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                0
        );
    }
}