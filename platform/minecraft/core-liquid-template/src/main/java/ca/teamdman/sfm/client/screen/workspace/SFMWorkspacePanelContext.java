package ca.teamdman.sfm.client.screen.workspace;

import java.util.Objects;
{% if features.workspace_panel_measurement or features.workspace_panel_lookup %}
import java.util.Optional;
{% endif %}

/** Narrow capability passed to embedded content for requesting host-level changes. */
public record SFMWorkspacePanelContext(SFMWorkspacePanelId panelId, SFMWorkspacePanelHost host) {
    public SFMWorkspacePanelContext {
        Objects.requireNonNull(panelId);
        Objects.requireNonNull(host);
    }

    public SFMWorkspacePanelIntentResult submit(SFMWorkspacePanelIntent intent) {
        return host.submit(panelId, Objects.requireNonNull(intent));
    }

{% if features.workspace_panel_measurement %}
    public Optional<SFMWorkspacePanelMetrics> measure(SFMScreenPanelBounds logicalBounds) {
        return host.measure(panelId, Objects.requireNonNull(logicalBounds));
    }

{% endif %}
{% if features.workspace_panel_lookup %}
    public Optional<SFMScreenPanel> panel(SFMWorkspacePanelId requestedPanelId) {
        return host.panel(Objects.requireNonNull(requestedPanelId));
    }

{% endif %}
    public static SFMWorkspacePanelContext unhosted(SFMWorkspacePanelId panelId) {
        return new SFMWorkspacePanelContext(panelId, SFMWorkspacePanelHost.UNAVAILABLE);
    }
}
