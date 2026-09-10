package ca.teamdman.sfm.client.review.release_review;

import ca.teamdman.sfm.client.review.session.SFMReviewSessionV2;
import ca.teamdman.sfm.client.review.session.SFMReviewSessionV2Kernel;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class SFMReviewOfflineEvidenceTests {
    static SFMReleaseReviewLedgerV3 fixture() throws Exception {
        var observed = SFMReviewMigrationPlanTests.fixture();
        var original = observed.document();
        var ids = original.selectorBindings().stream().map(value -> value.commentId()).toList();
        var s = original.reviewSession();
        var session = new SFMReviewSessionV2(s.schema(), s.id(), s.title(), s.coordinateSystem(), List.of(),
                s.comments().stream().filter(value -> ids.contains(value.id())).toList(), s.styleRules(), s.completionPolicy());
        var state = new SFMReleaseReviewV1(SFMReleaseReviewV1.SCHEMA, session, List.of(), List.of(), List.of(),
                original.selectorBindings(), List.of(), original.namedQueries(), original.resumeState(), List.of(), List.of());
        var evidence = SFMReviewEvidenceTable.EMPTY;
        for (var binding : state.selectorBindings()) evidence = evidence.capture(binding.selectedProposal(), observed.sources());
        return new SFMReleaseReviewLedgerV3(List.of(new SFMReleaseReviewLedgerV3.TargetLane("1.19.2", "sfm", ".",
                "1".repeat(40), Optional.empty(), List.of("."), List.of(), true)), state, evidence);
    }
    @Test void retainedCommentEvaluationMatchesOnlineWithoutAnyRepositoryReads() throws Exception {
        var ledger = fixture();
        var offline = SFMReviewOfflineEvidence.inspect(ledger);
        var original = SFMReviewMigrationPlanTests.fixture().document();
        assertTrue(offline.missingDocumentIds().isEmpty());
        assertTrue(offline.currentDomainStatus().startsWith("unavailable:"));
        assertEquals(ledger.state().reviewSession().comments(), offline.session().comments());
        for (var evaluation : offline.comments()) {
            var online = SFMReviewSessionV2Kernel.evaluateAll(original.reviewSession()).stream()
                    .filter(value -> value.commentId().equals(evaluation.commentId())).findFirst().orElseThrow();
            assertEquals(online, evaluation);
        }
        assertEquals(offline, SFMReviewOfflineEvidence.inspect(SFMReleaseReviewLedgerV3Codec.parse(SFMReleaseReviewLedgerV3Codec.write(ledger))));
    }
    @Test void absentGitBodiesRemainExplicitlyMissingAndCommentsRemainReadable() throws Exception {
        var ledger = fixture();
        var documents = ledger.evidence().documents().stream().map(document -> new SFMReviewEvidenceTable.Document(
                document.revisionId(), document.path(), document.sha256(), Optional.of(
                        new SFMReviewEvidenceTable.GitReference("sfm", "1".repeat(40), "2".repeat(40))))).toList();
        var missing = new SFMReleaseReviewLedgerV3(ledger.targets(), ledger.state(), new SFMReviewEvidenceTable(List.of(), documents));
        var offline = SFMReviewOfflineEvidence.inspect(missing);
        assertEquals(documents.size(), offline.missingDocumentIds().size());
        assertEquals(ledger.state().reviewSession().comments(), offline.session().comments());
        assertTrue(offline.comments().stream().noneMatch(value -> value.status() == SFMReviewSessionV2Kernel.Status.RESOLVED_EXACTLY));
    }
}
