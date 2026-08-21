package ca.teamdman.sfm.client.screen.review.explorer;

import ca.teamdman.sfm.client.history.SFMHistoryGraphContract;
import ca.teamdman.sfm.client.review.session.SFMReviewSessionV1;
import ca.teamdman.sfm.client.review.session.SFMReviewSessionV2;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
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

    @Test
    void productionCandidateCommentsRenderWithoutCommittedRangesAndCarryExactNavigation() {
        SFMReviewSessionV2.CandidateTrajectoryTarget oldPlan = candidateTarget(
                "plan-old", "route-a", SFMHistoryGraphContract.ProjectionStatus.EXTERNAL_BARRIER,
                SFMReviewSessionV2.CandidateTargetKind.ACTION);
        SFMReviewSessionV2.CandidateTrajectoryTarget replanned = candidateTarget(
                "plan-new", "route-b", SFMHistoryGraphContract.ProjectionStatus.MATERIALIZED,
                SFMReviewSessionV2.CandidateTargetKind.STATE);
        SFMReviewSessionV2 session = candidateSession(List.of(
                candidateComment("old-comment", "#review Keep the old route decision", oldPlan),
                candidateComment("new-comment", "#review Inspect the replacement state", replanned)
        ));

        SFMReviewExplorerModel model = SFMReviewExplorerModel.comments(session);

        assertEquals(2, model.root().children().size());
        SFMReviewExplorerModel.Node oldComment = model.root().children().get(0);
        assertTrue(oldComment.label().contains("[candidate · external_barrier]"));
        assertEquals(1, oldComment.children().size(),
                "a range-free candidate comment still needs an explorer target row");
        SFMReviewExplorerModel.Node targetNode = oldComment.children().get(0);
        assertEquals(SFMReviewExplorerModel.Kind.CANDIDATE_TARGET, targetNode.kind());
        assertTrue(targetNode.label().contains("plan=plan-old"));
        assertTrue(targetNode.label().contains("route=route-a"));
        assertTrue(targetNode.label().contains("frame=1"));
        assertTrue(targetNode.label().contains("step=step-1"));
        assertTrue(targetNode.label().contains("action=action-1"));
        assertTrue(targetNode.label().contains("state=state-1"));
        assertTrue(targetNode.label().contains("status=external_barrier"));
        SFMReviewExplorerModel.CandidateNavigation navigation = assertInstanceOf(
                SFMReviewExplorerModel.CandidateNavigation.class,
                targetNode.action().orElseThrow()
        );
        assertEquals("old-comment", navigation.commentId());
        assertSame(oldPlan, navigation.target(),
                "navigation must retain the immutable old-plan target rather than resolve the latest plan");
        assertSame(oldPlan, assertInstanceOf(
                SFMReviewExplorerModel.CandidateNavigation.class,
                oldComment.action().orElseThrow()).target());
        SFMReviewExplorerModel.CandidateNavigation newNavigation = assertInstanceOf(
                SFMReviewExplorerModel.CandidateNavigation.class,
                model.root().children().get(1).action().orElseThrow()
        );
        assertEquals("plan-new", newNavigation.target().trajectoryPlanRevisionId());
        assertEquals("plan-old", navigation.target().trajectoryPlanRevisionId(),
                "replanning must not retarget the retained old comment");
    }

    @Test
    void hashtagProjectionKeepsRangeFreeCandidateTargets() {
        SFMReviewSessionV2.CandidateTrajectoryTarget target = candidateTarget(
                "plan-tagged", "route-tagged", SFMHistoryGraphContract.ProjectionStatus.EXTERNAL_BARRIER,
                SFMReviewSessionV2.CandidateTargetKind.ACTION);

        SFMReviewExplorerModel model = SFMReviewExplorerModel.hashtags(candidateSession(List.of(
                candidateComment("candidate-tagged", "#review Explain the route barrier", target)
        )));

        SFMReviewExplorerModel.Node hashtag = model.root().children().stream()
                .filter(node -> node.label().equals("#review"))
                .findFirst().orElseThrow();
        SFMReviewExplorerModel.Node candidate = hashtag.children().get(0);
        assertEquals(SFMReviewExplorerModel.Kind.CANDIDATE_TARGET, candidate.kind());
        assertSame(target, assertInstanceOf(
                SFMReviewExplorerModel.CandidateNavigation.class,
                candidate.action().orElseThrow()).target());
    }

    @Test
    void candidateGlyphLabelAndPayloadRetainTheExactProjectedUtf8Witness() {
        SFMReviewSessionV2.ProjectedDocumentSelection selection = new SFMReviewSessionV2.ProjectedDocumentSelection(
                "document-a",
                "document-state-7",
                "0".repeat(64),
                4,
                9,
                "1".repeat(64)
        );
        SFMReviewSessionV2.CandidateTrajectoryTarget glyph = new SFMReviewSessionV2.CandidateTrajectoryTarget(
                "sfm:test/machine",
                4,
                "plan-glyph",
                "route-glyph",
                1,
                Optional.of("step-glyph"),
                "state-glyph",
                Optional.of("state-hash-glyph"),
                SFMHistoryGraphContract.ProjectionStatus.MATERIALIZED,
                SFMReviewSessionV2.CandidateTargetKind.DOCUMENT_REGION,
                Optional.of("action-glyph"),
                Optional.of(selection),
                Optional.of("evaluator-1"),
                List.of()
        );

        SFMReviewExplorerModel.Node targetNode = SFMReviewExplorerModel.comments(candidateSession(List.of(
                candidateComment("glyph-comment", "Inspect the projected glyphs", glyph)
        ))).root().children().get(0).children().get(0);

        assertTrue(targetNode.label().contains("document=document-a[4,9)"));
        SFMReviewExplorerModel.CandidateNavigation navigation = assertInstanceOf(
                SFMReviewExplorerModel.CandidateNavigation.class,
                targetNode.action().orElseThrow()
        );
        assertEquals(selection, navigation.target().projectedDocumentSelection().orElseThrow());
    }

    private static SFMReviewSessionV2 candidateSession(List<SFMReviewSessionV2.Comment> comments) {
        SFMReviewSessionV2 empty = SFMReviewSessionV2.empty("sfm:test/explorer-candidates", "Candidate review");
        return new SFMReviewSessionV2(
                empty.schema(), empty.id(), empty.title(), empty.coordinateSystem(),
                empty.revisionLanes(), comments, empty.styleRules(), empty.completionPolicy()
        );
    }

    private static SFMReviewSessionV2.Comment candidateComment(
            String id,
            String text,
            SFMReviewSessionV2.CandidateTrajectoryTarget target
    ) {
        return new SFMReviewSessionV2.Comment(
                id,
                text,
                new SFMReviewSessionV1.Provenance("human", "explorer-test", "1", List.of()),
                target
        );
    }

    private static SFMReviewSessionV2.CandidateTrajectoryTarget candidateTarget(
            String plan,
            String route,
            SFMHistoryGraphContract.ProjectionStatus status,
            SFMReviewSessionV2.CandidateTargetKind kind
    ) {
        return new SFMReviewSessionV2.CandidateTrajectoryTarget(
                "sfm:test/machine",
                4,
                plan,
                route,
                1,
                Optional.of("step-1"),
                "state-1",
                Optional.of("state-hash-1"),
                status,
                kind,
                kind == SFMReviewSessionV2.CandidateTargetKind.ACTION
                        ? Optional.of("action-1")
                        : Optional.empty(),
                Optional.empty(),
                Optional.of("evaluator-1"),
                List.of()
        );
    }
}
