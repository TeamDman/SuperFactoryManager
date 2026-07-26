package ca.teamdman.sfm.client.screen.workspace.diagnostic;

import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanelBounds;

import java.util.Objects;

/** Runtime boundary for obtaining the allocated logical size shown by a display panel. */
@FunctionalInterface
public interface SFMSizeDisplayDimensionsSource {
    SFMSizeDisplayDimensions snapshot(SFMScreenPanelBounds bounds);

    static SFMSizeDisplayDimensionsSource allocatedPanel() {
        return bounds -> {
            Objects.requireNonNull(bounds);
            return new SFMSizeDisplayDimensions(bounds.width(), bounds.height());
        };
    }
}
