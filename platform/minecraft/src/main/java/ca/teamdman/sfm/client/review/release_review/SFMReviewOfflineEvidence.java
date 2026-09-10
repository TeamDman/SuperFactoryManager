package ca.teamdman.sfm.client.review.release_review;

import ca.teamdman.sfm.client.review.session.SFMReviewSessionV1;
import ca.teamdman.sfm.client.review.session.SFMReviewSessionV2;
import ca.teamdman.sfm.client.review.session.SFMReviewSessionV2Kernel;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/** Evidence-only projection. Deliberately not a current release-review observation. */
public final class SFMReviewOfflineEvidence {
    private SFMReviewOfflineEvidence() { }
    public record Inspection(SFMReviewSessionV2 session, List<String> missingDocumentIds,
                             List<SFMReviewSessionV2Kernel.Evaluation> comments) {
        public Inspection { missingDocumentIds = List.copyOf(missingDocumentIds); comments = List.copyOf(comments); }
        public String currentDomainStatus() { return "unavailable: evidence-only view; no current release completion claim"; }
    }
    public static Inspection inspect(SFMReleaseReviewLedgerV3 ledger) {
        var bodies = new TreeMap<String, String>();
        ledger.evidence().contents().forEach(value -> bodies.put(value.sha256(), value.text()));
        var lanes = new ArrayList<SFMReviewSessionV1.RevisionLane>();
        var missing = new ArrayList<String>();
        for (var document : ledger.evidence().documents()) {
            String text = bodies.get(document.sha256());
            if (text == null) { missing.add(document.revisionId()); continue; }
            // One lane per immutable revision permits two versions of the same path.
            String id = "retained:" + document.revisionId();
            lanes.add(new SFMReviewSessionV1.RevisionLane(id,
                    new SFMReviewSessionV1.Repository("retained-comment-evidence", "."),
                    "Retained evidence (not current source)", new SFMReviewSessionV1.Snapshot(id + ":empty", List.of()),
                    new SFMReviewSessionV1.Snapshot(id + ":body", List.of(new SFMReviewSessionV1.DocumentRevision(
                            document.revisionId(), document.path(), "utf-8", document.sha256(), text)))));
        }
        var state = ledger.state().reviewSession();
        var session = new SFMReviewSessionV2(state.schema(), state.id(), state.title(), state.coordinateSystem(),
                lanes, state.comments(), state.styleRules(), state.completionPolicy());
        return new Inspection(session, missing, SFMReviewSessionV2Kernel.evaluateAll(session));
    }
}
