package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMPathOpenActionTests {
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
