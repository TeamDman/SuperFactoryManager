package ca.teamdman.sfm.client.review.session;

import ca.teamdman.sfm.client.screen.review.SFMReviewLedger;
import ca.teamdman.sfm.client.screen.review.SFMSourceComparison;

import java.util.ArrayList;
import java.util.List;

/** One-way compatibility projection; legacy booleans never become authoritative v1 fields. */
public final class SFMLegacyReviewLedgerProjection {
    private SFMLegacyReviewLedgerProjection() {}

    public static List<SFMReviewSessionV1.Comment> project(
            SFMSourceComparison.SourceOperation operation,
            SFMReviewLedger.Decision decision,
            SFMReviewSessionV1.SelectionRule selectionRule
    ) {
        List<SFMReviewSessionV1.Comment> comments = new ArrayList<>();
        List<String> decisionTags = new ArrayList<>();
        if (decision.reviewState() == SFMReviewLedger.ReviewState.REVIEWED) decisionTags.add("#reviewed");
        if (decision.humanDecision() == SFMReviewLedger.HumanDecision.APPROVED) decisionTags.add("#approved");
        if (decision.humanDecision() == SFMReviewLedger.HumanDecision.REJECTED) decisionTags.add("#needs-change");
        if (decision.isStale(operation)) decisionTags.add("#stale");
        if (!decisionTags.isEmpty()) comments.add(comment("legacy-decision:" + operation.id(),
                String.join(" ", decisionTags) + " Imported legacy review decision.", operation.id(), selectionRule));

        if (operation.auditStatus() == SFMSourceComparison.AuditStatus.FORBIDDEN) {
            comments.add(comment("legacy-audit:" + operation.id(),
                    "#problem #audit-forbidden " + diagnostic(operation), operation.id(), selectionRule));
        } else if (operation.auditStatus() == SFMSourceComparison.AuditStatus.WARNED) {
            comments.add(comment("legacy-audit:" + operation.id(),
                    "#audit-warning " + diagnostic(operation), operation.id(), selectionRule));
        }
        return List.copyOf(comments);
    }

    private static SFMReviewSessionV1.Comment comment(String id, String text, String operationId,
                                                       SFMReviewSessionV1.SelectionRule selectionRule) {
        return new SFMReviewSessionV1.Comment(id, text,
                new SFMReviewSessionV1.Provenance("legacy_ledger", "sfm-review-ledger:" + operationId,
                        "1", List.of()), selectionRule);
    }

    private static String diagnostic(SFMSourceComparison.SourceOperation operation) {
        return operation.diagnostic().isBlank() ? "Imported legacy audit status." : operation.diagnostic();
    }
}
