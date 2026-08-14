package ca.teamdman.sfm.client.screen.workspace;

import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;

/** Stable, host-owned state that travels with a panel entry. */
public record SFMWorkspacePanelMetadata(
        @Nullable Integer guiScaleOverride,
        String provenance
) {
    private static final String EXPLORER_PREVIEW_PREFIX = "explorer-preview(owner=";
    private static final String EXPLORER_PREVIEW_SUFFIX = ")";

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
        if (explorerId.isBlank() || explorerId.indexOf(')') >= 0) {
            throw new IllegalArgumentException("Explorer preview owner must be a non-blank stable id");
        }
        return new SFMWorkspacePanelMetadata(
                null,
                EXPLORER_PREVIEW_PREFIX + explorerId + EXPLORER_PREVIEW_SUFFIX
        );
    }

    /** Typed ownership used to locate only the preview belonging to one explorer. */
    public Optional<String> explorerPreviewOwner() {
        if (!provenance.startsWith(EXPLORER_PREVIEW_PREFIX)
                || !provenance.endsWith(EXPLORER_PREVIEW_SUFFIX)) {
            return Optional.empty();
        }
        String owner = provenance.substring(
                EXPLORER_PREVIEW_PREFIX.length(),
                provenance.length() - EXPLORER_PREVIEW_SUFFIX.length()
        );
        return owner.isBlank() ? Optional.empty() : Optional.of(owner);
    }

    public boolean isExplorerPreviewOwnedBy(String explorerId) {
        return explorerPreviewOwner().equals(Optional.of(Objects.requireNonNull(explorerId)));
    }

    public SFMWorkspacePanelMetadata withGuiScaleOverride(int scale) {
        return new SFMWorkspacePanelMetadata(scale, provenance);
    }

    public SFMWorkspacePanelMetadata clearGuiScaleOverride() {
        return new SFMWorkspacePanelMetadata(null, provenance);
    }
}
