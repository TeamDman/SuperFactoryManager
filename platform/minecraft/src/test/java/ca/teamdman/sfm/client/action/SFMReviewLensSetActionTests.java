package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewExplorerRuntime;
import ca.teamdman.sfm.client.screen.review.explorer.SFMReviewExplorerModel;
import ca.teamdman.sfm.client.screen.workspace.SFMReleaseReviewExplorerScreenType;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMReviewLensSetActionTests {
    @Test
    void constrainedChoiceSurfaceContainsEveryTypedLensAndMarksTheActiveOne() {
        var choices = SFMReviewLensSetAction.choices(
                SFMReleaseReviewExplorerScreenType.Projection.COMMENTS
        );

        assertEquals(6, choices.size());
        assertEquals(Set.of("changes", "comments", "hashtags", "query", "status", "migrations"),
                choices.stream()
                        .map(choice -> choice.command().substring(choice.command().lastIndexOf(' ') + 1))
                        .collect(java.util.stream.Collectors.toSet()));
        assertTrue(choices.stream().allMatch(choice -> choice.actionId().equals(SFMReviewLensSetAction.ID)));
        assertEquals(1, choices.stream().filter(choice -> choice.displayText().startsWith("Current · ")).count());
        assertTrue(choices.stream().anyMatch(choice -> choice.displayText().equals("Current · Comments")));
    }

    @Test
    void changesControlAlsoOffersTheAlternativePathLayout() {
        SFMPath root = new SFMPath(
                SFMPath.Kind.CONTRIBUTED,
                SFMReleaseReviewExplorerRuntime.PATH_SCHEME,
                "review-test",
                List.of("changes-test"),
                Optional.empty(),
                true
        );
        var lens = new SFMReleaseReviewExplorerRuntime.LensDescriptor(
                root,
                Path.of("review.sfm-review.json"),
                1L,
                SFMReleaseReviewExplorerScreenType.Projection.CHANGES,
                SFMReviewExplorerModel.PathLayout.HIERARCHY,
                Optional.empty(),
                "Release review changes · Hierarchy"
        );

        assertTrue(SFMReviewLensSetAction.controlChoices(lens).stream().anyMatch(choice -> choice.command().equals(
                "sfm action invoke sfm:review/changes/layout/set flat-paths"
        )));
    }
}
