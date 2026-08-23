package ca.teamdman.sfm.client.screen.workspace;

import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** Stable, host-owned state that travels with a panel entry. */
public record SFMWorkspacePanelMetadata(
        @Nullable Integer guiScaleOverride,
        String provenance
) {
    private static final String EXPLORER_PREVIEW_PREFIX = "explorer-preview(owner=";
    private static final String EXPLORER_PREVIEW_SUFFIX = ")";
    private static final String EXPLORER_PREVIEW_V2_PREFIX = "explorer-preview-v2(owner=";
    private static final String EXPLORER_PREVIEW_V2_SEPARATOR = ";presentation=";

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

    public static SFMWorkspacePanelMetadata explorerPreview(
            String explorerId,
            String presentationIdentity
    ) {
        Objects.requireNonNull(explorerId, "explorerId");
        Objects.requireNonNull(presentationIdentity, "presentationIdentity");
        if (explorerId.isBlank() || explorerId.indexOf(')') >= 0) {
            throw new IllegalArgumentException("Explorer preview owner must be a non-blank stable id");
        }
        if (presentationIdentity.isBlank()) {
            throw new IllegalArgumentException("Explorer preview presentation identity must not be blank");
        }
        return new SFMWorkspacePanelMetadata(
                null,
                EXPLORER_PREVIEW_V2_PREFIX + explorerId
                        + EXPLORER_PREVIEW_V2_SEPARATOR + encode(presentationIdentity)
                        + EXPLORER_PREVIEW_SUFFIX
        );
    }

    /** Typed ownership used to locate only the preview belonging to one explorer. */
    public Optional<String> explorerPreviewOwner() {
        if (provenance.startsWith(EXPLORER_PREVIEW_V2_PREFIX)
                && provenance.endsWith(EXPLORER_PREVIEW_SUFFIX)) {
            int separator = provenance.indexOf(EXPLORER_PREVIEW_V2_SEPARATOR);
            if (separator <= EXPLORER_PREVIEW_V2_PREFIX.length()) return Optional.empty();
            return Optional.of(provenance.substring(EXPLORER_PREVIEW_V2_PREFIX.length(), separator));
        }
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

    public Optional<String> explorerPreviewPresentationIdentity() {
        if (!provenance.startsWith(EXPLORER_PREVIEW_V2_PREFIX)
                || !provenance.endsWith(EXPLORER_PREVIEW_SUFFIX)) return Optional.empty();
        int separator = provenance.indexOf(EXPLORER_PREVIEW_V2_SEPARATOR);
        if (separator < 0) return Optional.empty();
        int start = separator + EXPLORER_PREVIEW_V2_SEPARATOR.length();
        int end = provenance.length() - EXPLORER_PREVIEW_SUFFIX.length();
        if (start >= end) return Optional.empty();
        try {
            return Optional.of(new String(
                    Base64.getUrlDecoder().decode(provenance.substring(start, end)),
                    StandardCharsets.UTF_8
            ));
        } catch (IllegalArgumentException malformed) {
            return Optional.empty();
        }
    }

    public boolean isExplorerPreviewOwnedBy(String explorerId) {
        return explorerPreviewOwner().equals(Optional.of(Objects.requireNonNull(explorerId)));
    }

    public boolean isExplorerPreview(String explorerId, String presentationIdentity) {
        return isExplorerPreviewOwnedBy(explorerId)
                && explorerPreviewPresentationIdentity().equals(Optional.of(
                Objects.requireNonNull(presentationIdentity, "presentationIdentity")));
    }

    public SFMWorkspacePanelMetadata withGuiScaleOverride(int scale) {
        return new SFMWorkspacePanelMetadata(scale, provenance);
    }

    public SFMWorkspacePanelMetadata clearGuiScaleOverride() {
        return new SFMWorkspacePanelMetadata(null, provenance);
    }

    private static String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
