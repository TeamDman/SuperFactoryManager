package ca.teamdman.sfm.client.screen.workspace;

import java.util.Objects;
import java.util.Optional;

/** Narrow capability passed to embedded content for requesting host-level changes. */
public record SFMWorkspacePanelContext(SFMWorkspacePanelId panelId, SFMWorkspacePanelHost host) {
    public SFMWorkspacePanelContext {
        Objects.requireNonNull(panelId);
        Objects.requireNonNull(host);
    }

    public SFMWorkspacePanelIntentResult submit(SFMWorkspacePanelIntent intent) {
        return host.submit(panelId, Objects.requireNonNull(intent));
    }

    public Optional<SFMWorkspacePanelMetrics> measure(SFMScreenPanelBounds logicalBounds) {
        return host.measure(panelId, Objects.requireNonNull(logicalBounds));
    }

    public static SFMWorkspacePanelContext unhosted(SFMWorkspacePanelId panelId) {
        return new SFMWorkspacePanelContext(panelId, SFMWorkspacePanelHost.UNAVAILABLE);
    }
}
