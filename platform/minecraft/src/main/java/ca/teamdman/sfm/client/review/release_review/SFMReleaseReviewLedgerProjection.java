package ca.teamdman.sfm.client.review.release_review;

import ca.teamdman.sfm.client.review.session.SFMReviewSessionV2;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/** Projects an edit of one immutable observation back to its small durable authority. */
public final class SFMReleaseReviewLedgerProjection {
    private SFMReleaseReviewLedgerProjection() {}

    /**
     * This is the comment-save boundary, not a browsing/materialization callback. Derived
     * comments are identified from the original observation, never from the edited text.
     * All capture bytes must belong to the displayed observation; this method performs no I/O.
     */
    public static SFMReleaseReviewLedgerV3 project(
            SFMReleaseReviewLedgerV3 ledger,
            SFMReleaseReviewV1 observation,
            SFMReleaseReviewV1 edited,
            Map<String, SFMReviewEvidenceTable.Observed> displayedSources
    ) {
        String id = ledger.state().reviewSession().id();
        if (!id.equals(observation.reviewSession().id()) || !id.equals(edited.reviewSession().id()))
            throw new IllegalArgumentException("Review edit belongs to another ledger");
        if (!observation.repositoryBindings().equals(edited.repositoryBindings())
                || !observation.corpusDocuments().equals(edited.corpusDocuments())
                || !observation.reviewUnits().equals(edited.reviewUnits())
                || !observation.reviewSession().revisionLanes().equals(edited.reviewSession().revisionLanes()))
            throw new IllegalArgumentException("Review edit changed the displayed source observation");

        var durableIds = new HashSet<String>();
        ledger.state().reviewSession().comments().forEach(comment -> durableIds.add(comment.id()));
        var observedComments = new HashMap<String, SFMReviewSessionV2.Comment>();
        observation.reviewSession().comments().forEach(comment -> observedComments.put(comment.id(), comment));
        for (var comment : ledger.state().reviewSession().comments())
            if (!comment.equals(observedComments.get(comment.id())))
                throw new IllegalArgumentException("Observation does not retain the current durable comment state");
        var observedDocuments = new HashMap<String, ca.teamdman.sfm.client.review.session.SFMReviewSessionV1.DocumentRevision>();
        for (var lane : observation.reviewSession().revisionLanes()) {
            lane.before().documents().forEach(document -> observedDocuments.put(document.id(), document));
            lane.after().documents().forEach(document -> observedDocuments.put(document.id(), document));
        }
        for (var source : displayedSources.values()) {
            var document = observedDocuments.get(source.document().revisionId());
            if (document == null || !document.path().equals(source.document().path())
                    || !document.sha256().equals(source.document().sha256()) || !document.text().equals(source.text()))
                throw new IllegalArgumentException("Capture bytes do not belong to the displayed observation");
        }
        var derived = new HashMap<String, SFMReviewSessionV2.Comment>();
        for (var comment : observation.reviewSession().comments()) {
            if (!durableIds.contains(comment.id())) {
                if (!"generated".equals(comment.provenance().kind()))
                    throw new IllegalArgumentException("Observation contains an unpersisted user comment");
                derived.put(comment.id(), comment);
            }
        }
        for (var comment : edited.reviewSession().comments()) {
            if (derived.containsKey(comment.id()) && !derived.get(comment.id()).equals(comment))
                throw new IllegalArgumentException("Derived change markers cannot be edited into durable comments");
        }
        var comments = edited.reviewSession().comments().stream()
                .filter(comment -> !derived.containsKey(comment.id())).toList();
        var retainedIds = new HashSet<String>();
        comments.forEach(comment -> retainedIds.add(comment.id()));
        var bindings = edited.selectorBindings().stream()
                .filter(binding -> retainedIds.contains(binding.commentId())).toList();
        var byComment = new HashMap<String, SFMReleaseReviewV1.CommentSelectorBinding>();
        bindings.forEach(binding -> byComment.put(binding.commentId(), binding));
        var previousBindings = new HashMap<String, SFMReleaseReviewV1.CommentSelectorBinding>();
        ledger.state().selectorBindings().forEach(binding -> previousBindings.put(binding.commentId(), binding));
        var evidence = ledger.evidence();
        for (var comment : comments) {
            var binding = byComment.get(comment.id());
            if (!(comment.target() instanceof SFMReviewSessionV2.CommittedReviewTarget target)
                    || binding == null || !binding.selectedProposal().selectionRule().equals(target.selectionRule()))
                throw new IllegalArgumentException("Durable source comment requires its exact selector binding");
            if (!durableIds.contains(comment.id()) || !binding.equals(previousBindings.get(comment.id())))
                evidence = evidence.capture(binding.selectedProposal(), displayedSources);
        }
        var session = edited.reviewSession();
        var sparseSession = new SFMReviewSessionV2(session.schema(), session.id(), session.title(),
                session.coordinateSystem(), List.of(), comments, session.styleRules(), session.completionPolicy());
        var sparse = new SFMReleaseReviewV1(SFMReleaseReviewV1.SCHEMA, sparseSession, List.of(), List.of(),
                List.of(), bindings, edited.migrationReports(), edited.namedQueries(), edited.resumeState(),
                List.of(), edited.completionAttestations());
        return new SFMReleaseReviewLedgerV3(ledger.targets(), sparse, evidence);
    }
}
