package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMPathOpenActionTests {
    @Test void contributedRootsUseExactMountAndSegmentContainment() {
        var root = contributed("mount-a", java.util.List.of());
        var nested = contributed("mount-a", java.util.List.of("comments"));
        var leaf = contributed("mount-a", java.util.List.of("comments", "value.txt"));
        assertEquals(nested, SFMPathOpenAction.deepestContainingRoot(java.util.List.of(root, nested), leaf).orElseThrow());
        assertTrue(SFMPathOpenAction.deepestContainingRoot(java.util.List.of(contributed("mount-b", java.util.List.of())), leaf).isEmpty());
        assertTrue(SFMPathOpenAction.deepestContainingRoot(java.util.List.of(nested),
                contributed("mount-a", java.util.List.of("comments-other", "value.txt"))).isEmpty());
        assertTrue(SFMPathOpenAction.deepestContainingRoot(java.util.List.of(root),
                ca.teamdman.sfm.client.explorer.SFMPath.fromNative(java.nio.file.Path.of("value.txt"))).isEmpty());
    }

    private static ca.teamdman.sfm.client.explorer.SFMPath contributed(String mount, java.util.List<String> segments) {
        return new ca.teamdman.sfm.client.explorer.SFMPath(ca.teamdman.sfm.client.explorer.SFMPath.Kind.CONTRIBUTED,
                "review-evidence", mount, segments, java.util.Optional.empty(), false);
    }
    private static final SFMWorkspacePanelId SOURCE = new SFMWorkspacePanelId(1);
    private static final SFMWorkspacePanelId RESULT_EXPLORER = new SFMWorkspacePanelId(2);

    @Test
    void referenceNavigationPrefersTheOriginalEditorWhileItExists() {
        assertEquals(
                SOURCE,
                SFMPathOpenAction.chooseReferenceNavigationPanel(
                        true,
                        SOURCE,
                        true,
                        RESULT_EXPLORER
                ).orElseThrow()
        );
    }

    @Test
    void retainedReferenceExplorerBecomesTheNavigationStackAfterSourceClosure() {
        assertEquals(
                RESULT_EXPLORER,
                SFMPathOpenAction.chooseReferenceNavigationPanel(
                        false,
                        SOURCE,
                        true,
                        RESULT_EXPLORER
                ).orElseThrow()
        );
    }

    @Test
    void unrelatedPanelsCannotClaimAReferenceResultAfterSourceClosure() {
        assertTrue(SFMPathOpenAction.chooseReferenceNavigationPanel(
                false,
                SOURCE,
                false,
                RESULT_EXPLORER
        ).isEmpty());
    }
}
