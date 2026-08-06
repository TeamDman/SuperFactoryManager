package ca.teamdman.sfm.client.screen.workspace;

import java.util.Objects;

/** Exact workspace and source identity used to revalidate a typed panel recipe. */
public record SFMPanelReopenContext(
        SFMScreenMultiplexer workspace,
        SFMWorkspacePanelId sourcePanelId
) {
    public SFMPanelReopenContext {
        Objects.requireNonNull(workspace);
        Objects.requireNonNull(sourcePanelId);
    }
}
