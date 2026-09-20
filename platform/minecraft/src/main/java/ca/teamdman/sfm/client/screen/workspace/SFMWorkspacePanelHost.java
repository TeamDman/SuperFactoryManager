package ca.teamdman.sfm.client.screen.workspace;

import java.util.Optional;

@FunctionalInterface
public interface SFMWorkspacePanelHost {
    SFMWorkspacePanelHost UNAVAILABLE = (source, intent) -> SFMWorkspacePanelIntentResult.UNAVAILABLE;

    SFMWorkspacePanelIntentResult submit(SFMWorkspacePanelId source, SFMWorkspacePanelIntent intent);

    /** Maps panel-local logical coordinates to their exact framebuffer allocation when hosted. */
    default Optional<SFMWorkspacePanelMetrics> measure(
            SFMWorkspacePanelId source,
            SFMScreenPanelBounds logicalBounds
    ) {
        return Optional.empty();
    }

    /** Resolves the exact stable panel identity without retargeting to focus. */
    default Optional<SFMScreenPanel> panel(SFMWorkspacePanelId panelId) {
        return Optional.empty();
    }
}
