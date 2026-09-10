package ca.teamdman.sfm.client.review.release_review;

import ca.teamdman.sfm.client.review.session.SFMReviewSessionV1;
import ca.teamdman.sfm.client.review.session.SFMReviewSessionV2;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/** Pure acceptance mutation; runtime must additionally enforce its opened-file writer lease. */
public final class SFMReviewMigrationSuccessor {
    private SFMReviewMigrationSuccessor() {}

    public static SFMReleaseReviewV1 accept(SFMReleaseReviewLedgerResolver.Resolved observation,
                                          SFMReviewMigrationPlan shown, String successorId, String note) {
        if (successorId == null || successorId.isBlank() || note == null || note.isBlank())
            throw new IllegalArgumentException("Successor identity and explicit decision note are required");
        var current = SFMReviewMigrationPlan.preview(observation, shown.originalComment().id(),
                shown.destinationLane(), SFMReviewMigrationPreview.Limits.DEFAULT);
        if (!current.equals(shown) || !current.canAccept())
            throw new IllegalArgumentException("Migration preview is stale, ambiguous, missing or incomplete");
        var review = observation.document();
        if (review.reviewSession().comments().stream().anyMatch(comment -> comment.id().equals(successorId)))
            throw new IllegalArgumentException("Successor comment identity already exists");
        String fingerprint = SFMReleaseReviewKernel.sha256((current.semanticStateHash() + "\n"
                + current.destinationLane() + "\n" + current.originalComment().id()).getBytes(StandardCharsets.UTF_8));
        var ranges = new ArrayList<SFMReleaseReviewV1.PinnedSelectionRange>();
        var rules = new ArrayList<SFMReviewSessionV1.SelectionRule>();
        for (int index = 0; index < current.ranges().size(); index++) {
            var target = current.ranges().get(index).candidates().get(0);
            var source = observation.sources().get(target.documentRevisionId());
            byte[] bytes = source.text().getBytes(StandardCharsets.UTF_8);
            ranges.add(new SFMReleaseReviewV1.PinnedSelectionRange(
                    current.originalBinding().capturedSelection().ranges().get(index).direction(),
                    target.documentRevisionId(), target.documentSha256(), target.startByte(), target.endByte()));
            rules.add(new SFMReviewSessionV1.LiteralUtf8Range(target.documentRevisionId(), target.startByte(), target.endByte(),
                    target.documentSha256(), SFMReleaseReviewKernel.sha256(Arrays.copyOfRange(bytes, target.startByte(), target.endByte()))));
        }
        var witness = new SFMReleaseReviewV1.PinnedSelection("migration-selection:" + fingerprint,
                "Explicit literal successor of " + current.originalComment().id(),
                current.originalBinding().capturedSelection().primaryRangeIndex(), ranges);
        SFMReviewSessionV1.SelectionRule rule = rules.size() == 1 ? rules.get(0) : new SFMReviewSessionV1.Union(rules);
        var proposal = new SFMReleaseReviewV1.SelectorProposal("migration-successor:" + successorId,
                rules.size() == 1 ? SFMReleaseReviewV1.SelectorKind.LITERAL : SFMReleaseReviewV1.SelectorKind.BOUNDED_MULTI_REGION,
                rule, witness, Optional.of("sfm:explicit-literal-successor"), Optional.empty(), List.of(
                new SFMReleaseReviewV1.Evidence("migration-original-comment", current.originalComment().id()),
                new SFMReleaseReviewV1.Evidence("migration-original-selector", current.originalBinding().selectedProposal().id()),
                new SFMReleaseReviewV1.Evidence("migration-observation", current.semanticStateHash()),
                new SFMReleaseReviewV1.Evidence("migration-destination-lane", current.destinationLane()),
                new SFMReleaseReviewV1.Evidence("migration-policy", "unique-literal-witness/1"),
                new SFMReleaseReviewV1.Evidence("migration-decision-note", note)),
                SFMReleaseReviewV1.ProposalConfidence.EXACT, fingerprint, "migration:" + fingerprint,
                List.of("Explicitly accepted literal successor; original comment and evidence unchanged"));
        var comments = new ArrayList<>(review.reviewSession().comments());
        comments.add(new SFMReviewSessionV2.Comment(successorId, current.originalComment().text(),
                new SFMReviewSessionV1.Provenance("human", "in-game-release-reviewer", "1", List.of(current.originalComment().id())),
                new SFMReviewSessionV2.CommittedReviewTarget(rule)));
        var previous = review.reviewSession();
        var session = new SFMReviewSessionV2(previous.schema(), previous.id(), previous.title(), previous.coordinateSystem(),
                previous.revisionLanes(), comments, previous.styleRules(), previous.completionPolicy());
        var bindings = new ArrayList<>(review.selectorBindings());
        bindings.add(new SFMReleaseReviewV1.CommentSelectorBinding(successorId, witness, proposal));
        var result = new SFMReleaseReviewV1(review.schema(), session, review.repositoryBindings(), review.corpusDocuments(),
                review.reviewUnits(), bindings, review.migrationReports(), review.namedQueries(), review.resumeState(),
                review.producerGenerations(), review.completionAttestations());
        SFMReleaseReviewKernel.validate(result);
        return result;
    }
}
