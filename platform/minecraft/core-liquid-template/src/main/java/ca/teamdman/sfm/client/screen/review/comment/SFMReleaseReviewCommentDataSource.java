package ca.teamdman.sfm.client.screen.review.comment;

import ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewRuntime;
import ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewV1;
import ca.teamdman.sfm.client.review.session.SFMReviewSessionV2;

import java.util.List;
import java.util.Objects;

/**
 * Mutable comment UI adapter whose only durable authority is the currently open
 * {@code sfm.release-review/1} file.
 */
public final class SFMReleaseReviewCommentDataSource implements SFMReviewCommentDataSource {
    private final SFMReleaseReviewRuntime runtime;

    public SFMReleaseReviewCommentDataSource(SFMReleaseReviewRuntime runtime) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
    }

    @Override
    public SessionView refresh() {
        return delegate().refresh();
    }

    @Override
    public String createLiteralComment(String text, List<RangeView> ranges) {
        SFMReviewCommentKernelDataSource delegate = delegate();
        String id = delegate.createLiteralComment(text, ranges);
        persist(delegate.session());
        return id;
    }

    @Override
    public void editComment(String id, String text) {
        SFMReviewCommentKernelDataSource delegate = delegate();
        delegate.editComment(id, text);
        persist(delegate.session());
    }

    @Override
    public void archiveComment(String id) {
        SFMReviewCommentKernelDataSource delegate = delegate();
        delegate.archiveComment(id);
        persist(delegate.session());
    }

    @Override
    public void updateStyleColour(String ruleId, StyleChannel channel, int argb) {
        SFMReviewCommentKernelDataSource delegate = delegate();
        delegate.updateStyleColour(ruleId, channel, argb);
        persist(delegate.session());
    }

    private SFMReviewCommentKernelDataSource delegate() {
        SFMReleaseReviewV1 review = runtime.document().orElseThrow(() ->
                new IllegalStateException("No release-review document is open"));
        return new SFMReviewCommentKernelDataSource(review.reviewSession());
    }

    private void persist(SFMReviewSessionV2 session) {
        SFMReleaseReviewRuntime.MutationResult result = runtime.mutate(review -> new SFMReleaseReviewV1(
                review.schema(), session, review.repositoryBindings(), review.corpusDocuments(), review.reviewUnits(),
                review.selectorBindings(), review.migrationReports(), review.namedQueries(), review.resumeState(),
                review.producerGenerations(), review.completionAttestations()
        ));
        if (!result.saved()) {
            throw new IllegalStateException(result.failure().orElse("Unable to autosave release-review comment"));
        }
    }
}
