package ca.teamdman.sfm.client.screen.review.explorer;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMReviewExplorerModelTests {
    @Test
    void changesKeepBothRevisionLeavesForEveryFileAndLane() {
        var model = SFMReviewExplorerModel.changes("mod 4.34.0", "HEAD");

        var example = model.root().children().stream()
                .filter(node -> node.label().equals("src/Example.java"))
                .findFirst().orElseThrow();

        assertEquals(2, example.children().size());
        assertTrue(example.children().stream().allMatch(lane -> lane.children().size() == 2));
        assertEquals(List.of("before", "after"), example.children().get(0).children().stream()
                .map(node -> node.leaf().title().split(" · ")[0]).toList());
    }

    @Test
    void missingRevisionIsAnExplicitTombstoneLeaf() {
        var model = SFMReviewExplorerModel.changes("before", "after");
        var added = model.root().children().stream()
                .filter(node -> node.label().equals("src/Added.java"))
                .findFirst().orElseThrow();

        assertTrue(added.children().stream().allMatch(lane -> lane.children().get(0).leaf().missing()));
        assertFalse(added.children().stream().allMatch(lane -> lane.children().get(1).leaf().missing()));
    }

    @Test
    void commentProjectionIsCommentFileRegion() {
        var model = SFMReviewExplorerModel.comments();
        assertEquals(SFMReviewExplorerModel.Kind.COMMENT, model.root().children().get(0).kind());
        assertTrue(model.root().children().get(0).children().stream()
                .allMatch(node -> node.kind() == SFMReviewExplorerModel.Kind.REGION));
        assertTrue(model.root().children().get(0).children().stream()
                .allMatch(node -> node.leaf() != null && !node.leaf().missing()));
    }

    @Test
    void hashtagProjectionIsHashtagFileRegion() {
        var model = SFMReviewExplorerModel.hashtags();
        assertTrue(model.root().children().stream()
                .allMatch(node -> node.kind() == SFMReviewExplorerModel.Kind.HASHTAG));
        assertTrue(model.root().children().stream().flatMap(tag -> tag.children().stream())
                .allMatch(node -> node.kind() == SFMReviewExplorerModel.Kind.FILE));
    }

    @Test
    void navigationCollapsesBeforeSelectingParent() {
        var model = SFMReviewExplorerModel.changes("before", "after");
        model.select(0);
        model.expandSelection();
        model.selectNext();
        model.collapseSelectionOrSelectParent();
        model.collapseSelectionOrSelectParent();
        assertEquals(SFMReviewExplorerModel.Kind.ROOT, model.selected().kind());
    }
}
