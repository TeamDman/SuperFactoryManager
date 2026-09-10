package ca.teamdman.sfm.client.review.release_review;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SFMReviewMigrationSuccessorTests {
    @Test void multiDocumentUnicodeSuccessorPreservesPrimaryAndDirections() throws Exception {
        var observed = SFMReviewMigrationPlanTests.fixture();
        var plan = SFMReviewMigrationPlan.preview(observed, "human:unicode-multidoc-note", "1.19.2", SFMReviewMigrationPreview.Limits.DEFAULT);
        assertEquals(3, plan.ranges().size());
        assertTrue(plan.canAccept(), plan.ranges().toString());
        var result = SFMReviewMigrationSuccessor.accept(observed, plan, "human:multi-successor", "Checked all three regions");
        var binding = result.selectorBindings().stream().filter(value -> value.commentId().equals("human:multi-successor")).findFirst().orElseThrow();
        assertEquals(plan.originalBinding().capturedSelection().primaryRangeIndex(), binding.capturedSelection().primaryRangeIndex());
        assertEquals(plan.originalBinding().capturedSelection().ranges(), binding.capturedSelection().ranges());
        assertEquals(SFMReleaseReviewV1.SelectorKind.BOUNDED_MULTI_REGION, binding.selectedProposal().kind());
    }
    @Test void acceptanceAppendsLinkedCommentAndPreservesOriginalWitness() throws Exception {
        var observed = SFMReviewMigrationPlanTests.fixture();
        var plan = SFMReviewMigrationPlan.preview(observed, "human:approved-value", "1.19.2", SFMReviewMigrationPreview.Limits.DEFAULT);
        String before = SFMReleaseReviewV1Codec.write(observed.document());
        var result = SFMReviewMigrationSuccessor.accept(observed, plan, "human:successor", "I checked the proposed target");
        assertEquals(before, SFMReleaseReviewV1Codec.write(observed.document()));
        assertTrue(result.reviewSession().comments().containsAll(observed.document().reviewSession().comments()));
        assertTrue(result.selectorBindings().containsAll(observed.document().selectorBindings()));
        var successor = result.reviewSession().comments().stream().filter(c -> c.id().equals("human:successor")).findFirst().orElseThrow();
        assertEquals(plan.originalComment().text(), successor.text());
        assertEquals(List.of(plan.originalComment().id()), successor.provenance().parentCommentIds());
        var binding = result.selectorBindings().stream().filter(b -> b.commentId().equals(successor.id())).findFirst().orElseThrow();
        assertEquals(binding.capturedSelection(), binding.selectedProposal().literalWitness());
        assertTrue(binding.selectedProposal().semanticProvenance().stream().anyMatch(e -> e.key().equals("migration-decision-note")));
        assertEquals(result, SFMReleaseReviewV1Codec.parse(SFMReleaseReviewV1Codec.write(result)));
    }
    @Test void staleOrIncompletePreviewCannotBeAccepted() throws Exception {
        var observed = SFMReviewMigrationPlanTests.fixture();
        var plan = SFMReviewMigrationPlan.preview(observed, "human:approved-value", "1.19.2", SFMReviewMigrationPreview.Limits.DEFAULT);
        var forged = new SFMReviewMigrationPlan("0".repeat(64), plan.destinationLane(), plan.originalComment(), plan.originalBinding(), plan.ranges());
        assertThrows(IllegalArgumentException.class, () -> SFMReviewMigrationSuccessor.accept(observed, forged, "new", "check"));
        var incomplete = new SFMReleaseReviewLedgerResolver.Resolved(observed.document(), observed.sources(), List.of("incomplete"));
        assertThrows(IllegalArgumentException.class, () -> SFMReviewMigrationSuccessor.accept(incomplete, plan, "new", "check"));
        assertThrows(IllegalArgumentException.class, () -> SFMReviewMigrationSuccessor.accept(observed, plan, plan.originalComment().id(), "check"));
    }
}
