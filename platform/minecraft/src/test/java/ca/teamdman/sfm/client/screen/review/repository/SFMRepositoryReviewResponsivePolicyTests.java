package ca.teamdman.sfm.client.screen.review.repository;

import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanelBounds;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SFMRepositoryReviewResponsivePolicyTests {
    @Test
    void choosesModesFromLogicalWidthAtDocumentedBoundaries() {
        assertEquals(SFMRepositoryReviewResponsivePolicy.Mode.NARROW, mode(699));
        assertEquals(SFMRepositoryReviewResponsivePolicy.Mode.MEDIUM, mode(700));
        assertEquals(SFMRepositoryReviewResponsivePolicy.Mode.MEDIUM, mode(999));
        assertEquals(SFMRepositoryReviewResponsivePolicy.Mode.WIDE, mode(1000));
    }

    private static SFMRepositoryReviewResponsivePolicy.Mode mode(int width) {
        return SFMRepositoryReviewResponsivePolicy.mode(new SFMScreenPanelBounds(0, 0, width, 480));
    }
}
