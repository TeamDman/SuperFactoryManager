package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.screen.review.explorer.SFMReviewExplorerModel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SFMReviewChangesLayoutSetActionTests {
    @Test
    void constrainedChoiceOffersOnlyTheAlternativeLayout() {
        var fromHierarchy = SFMReviewChangesLayoutSetAction.alternativeChoice(
                SFMReviewExplorerModel.PathLayout.HIERARCHY
        );
        var fromFlat = SFMReviewChangesLayoutSetAction.alternativeChoice(
                SFMReviewExplorerModel.PathLayout.FLAT_PATHS
        );

        assertEquals(1, fromHierarchy.size());
        assertEquals(
                "sfm action invoke sfm:review/changes/layout/set flat-paths",
                fromHierarchy.get(0).command()
        );
        assertEquals(
                "sfm action invoke sfm:review/changes/layout/set hierarchy",
                fromFlat.get(0).command()
        );
    }

    @Test
    void flatAliasAndCanonicalTokensResolveDeterministically() {
        assertEquals(
                SFMReviewExplorerModel.PathLayout.HIERARCHY,
                SFMReviewChangesLayoutSetAction.parse("hierarchy")
        );
        assertEquals(
                SFMReviewExplorerModel.PathLayout.FLAT_PATHS,
                SFMReviewChangesLayoutSetAction.parse("flat")
        );
        assertEquals(
                SFMReviewExplorerModel.PathLayout.FLAT_PATHS,
                SFMReviewChangesLayoutSetAction.parse("flat-paths")
        );
    }
}
