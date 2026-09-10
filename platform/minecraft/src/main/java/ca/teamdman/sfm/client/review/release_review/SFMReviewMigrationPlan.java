package ca.teamdman.sfm.client.review.release_review;

import ca.teamdman.sfm.client.review.session.SFMReviewSessionV2;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/** One immutable, explicitly scoped preview over the current observed after-documents. */
public record SFMReviewMigrationPlan(String semanticStateHash, String destinationLane,
                                     SFMReviewSessionV2.Comment originalComment,
                                     SFMReleaseReviewV1.CommentSelectorBinding originalBinding,
                                     List<SFMReviewMigrationPreview.Result> ranges) {
    public SFMReviewMigrationPlan { ranges = List.copyOf(ranges); }

    public boolean canAccept() {
        if (ranges.isEmpty() || ranges.size() != originalBinding.capturedSelection().ranges().size()
                || ranges.stream().anyMatch(result -> !result.uniquelyApplicable())) return false;
        // Distinct original regions must not collapse into the same candidate without explanation.
        var destinations = new HashSet<SFMReviewMigrationPreview.Candidate>();
        return ranges.stream().allMatch(result -> destinations.add(result.candidates().get(0)));
    }

    public static SFMReviewMigrationPlan preview(SFMReleaseReviewLedgerResolver.Resolved observation,
                                                String commentId, String destinationLane,
                                                SFMReviewMigrationPreview.Limits limits) {
        var review = observation.document();
        var corpus = SFMReleaseReviewCorpus.from(review);
        if (review.repositoryBindings().stream().noneMatch(binding -> binding.laneId().equals(destinationLane)))
            throw new IllegalArgumentException("Select an explicit current repository lane, not a historical evidence lane");
        var comment = review.reviewSession().comments().stream().filter(value -> value.id().equals(commentId))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Original comment is unavailable"));
        if ("generated".equals(comment.provenance().kind()))
            throw new IllegalArgumentException("Generated changes are not migration comments");
        var binding = review.selectorBindings().stream().filter(value -> value.commentId().equals(commentId))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Original selector witness is unavailable"));
        var destination = new ArrayList<SFMReviewEvidenceTable.Observed>();
        boolean complete = observation.diagnostics().isEmpty();
        for (var view : corpus.documents()) {
            var row = view.binding();
            if (!row.laneId().equals(destinationLane) || row.snapshotSide() != SFMReleaseReviewV1.SnapshotSide.AFTER) continue;
            var source = observation.sources().get(row.documentRevisionId());
            if (row.materialization() != SFMReleaseReviewV1.Materialization.COMPLETE || source == null
                    || view.materializedDocument().isEmpty()) { complete = false; continue; }
            validateObserved(source, view);
            destination.add(source);
        }
        var results = new ArrayList<SFMReviewMigrationPreview.Result>();
        long remaining = limits.comparedBytes();
        for (var range : binding.capturedSelection().ranges()) {
            var source = observation.sources().get(range.documentRevisionId());
            var view = corpus.documentRevision(range.documentRevisionId());
            if (remaining == 0 || source == null || view.isEmpty() || view.get().materializedDocument().isEmpty()
                    || !source.document().sha256().equals(range.documentSha256())) {
                results.add(new SFMReviewMigrationPreview.Result(SFMReviewMigrationPreview.Status.INCOMPLETE,
                        List.of(), 0, "Original evidence unavailable or shared comparison budget exhausted"));
                continue;
            }
            validateObserved(source, view.get());
            try {
                var result = SFMReviewMigrationPreview.match(source, range.startByte(), range.endByte(), destination,
                        complete, new SFMReviewMigrationPreview.Limits(remaining, limits.candidates()));
                remaining -= result.comparedBytes();
                results.add(result);
            } catch (IllegalArgumentException invalidWitness) {
                results.add(new SFMReviewMigrationPreview.Result(SFMReviewMigrationPreview.Status.INCOMPLETE,
                        List.of(), 0, "Invalid original witness: " + invalidWitness.getMessage()));
            }
        }
        return new SFMReviewMigrationPlan(SFMReleaseReviewKernel.semanticStateHash(review), destinationLane,
                comment, binding, results);
    }

    private static void validateObserved(SFMReviewEvidenceTable.Observed source, SFMReleaseReviewCorpus.DocumentView view) {
        var row = view.binding();
        if (!source.document().revisionId().equals(row.documentRevisionId()) || !source.document().path().equals(row.path())
                || !source.document().sha256().equals(row.sha256())
                || !source.text().equals(view.materializedDocument().orElseThrow().text()))
            throw new IllegalArgumentException("Migration bytes do not match the displayed observation");
    }
}
