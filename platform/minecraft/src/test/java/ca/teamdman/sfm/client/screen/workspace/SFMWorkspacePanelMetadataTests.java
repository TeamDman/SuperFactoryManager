package ca.teamdman.sfm.client.screen.workspace;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMWorkspacePanelMetadataTests {
    @Test
    void explorerPreviewOwnershipIsTypedAndSurvivesScaleChanges() {
        SFMWorkspacePanelMetadata metadata = SFMWorkspacePanelMetadata.explorerPreview("explorer-17");

        assertEquals(Optional.of("explorer-17"), metadata.explorerPreviewOwner());
        assertTrue(metadata.isExplorerPreviewOwnedBy("explorer-17"));
        assertFalse(metadata.isExplorerPreviewOwnedBy("explorer-18"));
        assertEquals(Optional.of("explorer-17"), metadata.withGuiScaleOverride(4).explorerPreviewOwner());
        assertEquals(Optional.of("explorer-17"), metadata.withGuiScaleOverride(4)
                .clearGuiScaleOverride().explorerPreviewOwner());
    }

    @Test
    void ordinaryAndMalformedProvenanceNeverClaimPreviewOwnership() {
        assertEquals(Optional.empty(), SFMWorkspacePanelMetadata.ordinary().explorerPreviewOwner());
        assertEquals(Optional.empty(), new SFMWorkspacePanelMetadata(
                null,
                "explorer-preview(owner=unterminated"
        ).explorerPreviewOwner());
    }

    @Test
    void previewOwnerCannotForgeTheProvenanceBoundary() {
        assertThrows(IllegalArgumentException.class,
                () -> SFMWorkspacePanelMetadata.explorerPreview(""));
        assertThrows(IllegalArgumentException.class,
                () -> SFMWorkspacePanelMetadata.explorerPreview("explorer-1)ordinary"));
    }

    @Test
    void typedPresentationIdentityRoundTripsWithoutLeakingIntoOwnerParsing() {
        String identity = "review|D:\\repo with spaces|before|sha256:abc|[0,12)";
        SFMWorkspacePanelMetadata metadata = SFMWorkspacePanelMetadata.explorerPreview(
                "explorer-7",
                identity
        );

        assertEquals(Optional.of("explorer-7"), metadata.explorerPreviewOwner());
        assertEquals(Optional.of(identity), metadata.explorerPreviewPresentationIdentity());
        assertTrue(metadata.isExplorerPreview("explorer-7", identity));
        assertFalse(metadata.isExplorerPreview("explorer-7", identity + "-other"));
        assertEquals(Optional.of(identity), metadata.withGuiScaleOverride(3)
                .explorerPreviewPresentationIdentity());
    }
}
