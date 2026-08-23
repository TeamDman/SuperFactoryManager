package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.screen.workspace.SFMReleaseReviewExplorerScreenType;
import org.junit.jupiter.api.Test;

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
}
