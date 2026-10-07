package ca.teamdman.sfm.client.screen.workspace;
{% if features.workspace_panel_measurement or features.workspace_panel_lookup %}

import java.util.Optional;
{% endif %}

@FunctionalInterface
public interface SFMWorkspacePanelHost {
    SFMWorkspacePanelHost UNAVAILABLE = (source, intent) -> SFMWorkspacePanelIntentResult.UNAVAILABLE;

    SFMWorkspacePanelIntentResult submit(SFMWorkspacePanelId source, SFMWorkspacePanelIntent intent);
{% if features.workspace_panel_measurement or features.workspace_panel_lookup %}

{% endif %}
{% if features.workspace_panel_measurement %}
    /** Maps panel-local logical coordinates to their exact framebuffer allocation when hosted. */
    default Optional<SFMWorkspacePanelMetrics> measure(
            SFMWorkspacePanelId source,
            SFMScreenPanelBounds logicalBounds
    ) {
        return Optional.empty();
    }

{% endif %}
{% if features.workspace_panel_lookup %}
    /** Resolves the exact stable panel identity without retargeting to focus. */
    default Optional<SFMScreenPanel> panel(SFMWorkspacePanelId panelId) {
        return Optional.empty();
    }
{% endif %}
}
