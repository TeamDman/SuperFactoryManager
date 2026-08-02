package ca.teamdman.sfm.client.screen.workspace;

import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** Stable, host-owned state that travels with a panel entry. */
public record SFMWorkspacePanelMetadata(
        @Nullable Integer guiScaleOverride,
        String provenance
) {
    public SFMWorkspacePanelMetadata {
        if (guiScaleOverride != null && guiScaleOverride < 0) {
            throw new IllegalArgumentException("GUI scale override must be non-negative");
        }
        provenance = Objects.requireNonNull(provenance);
    }

    public static SFMWorkspacePanelMetadata ordinary() {
        return new SFMWorkspacePanelMetadata(null, "ordinary");
    }

    public static SFMWorkspacePanelMetadata explorerPreview(String explorerId) {
        Objects.requireNonNull(explorerId);
        return new SFMWorkspacePanelMetadata(null, "explorer-preview(owner=" + explorerId + ")");
    }

    public SFMWorkspacePanelMetadata withGuiScaleOverride(int scale) {
        return new SFMWorkspacePanelMetadata(scale, provenance);
    }

    public SFMWorkspacePanelMetadata clearGuiScaleOverride() {
        return new SFMWorkspacePanelMetadata(null, provenance);
    }
}
