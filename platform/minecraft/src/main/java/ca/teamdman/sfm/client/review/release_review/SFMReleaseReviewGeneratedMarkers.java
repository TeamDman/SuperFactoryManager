package ca.teamdman.sfm.client.review.release_review;

import ca.teamdman.sfm.client.review.session.SFMReviewSessionV2;

import java.util.Set;

/** Legacy producer records remain evidence, but are not user-facing annotations. */
public final class SFMReleaseReviewGeneratedMarkers {
    private static final Set<String> PRODUCERS = Set.of(
            "sfm.release-review.git-materializer/1",
            "sfm.release-review.git-materializer-retired/1",
            "sfm.release-review.working-tree-materializer/1");

    private SFMReleaseReviewGeneratedMarkers() {}

    public static boolean isChangeMarker(SFMReviewSessionV2.Comment comment) {
        return "generated".equals(comment.provenance().kind())
                && PRODUCERS.contains(comment.provenance().producer());
    }
}
