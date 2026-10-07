package ca.teamdman.sfm.client.review.release_review;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class SFMReviewMigrationPlanTests {
    @Test void multiRangeBudgetIsSharedRatherThanResetForEachWitness() throws Exception {
        var observed = fixture();
        var plan = SFMReviewMigrationPlan.preview(observed, "human:unicode-multidoc-note", "1.19.2",
                new SFMReviewMigrationPreview.Limits(5, 256));
        assertEquals(3, plan.ranges().size());
        assertFalse(plan.canAccept());
        assertTrue(plan.ranges().stream().mapToLong(SFMReviewMigrationPreview.Result::comparedBytes).sum() <= 5);
        assertTrue(plan.ranges().stream().allMatch(value -> value.status() == SFMReviewMigrationPreview.Status.INCOMPLETE));
    }
    static SFMReleaseReviewLedgerResolver.Resolved fixture() throws Exception {
        for (Path root = Path.of("").toAbsolutePath(); root != null; root = root.getParent()) {
            Path file = root.resolve("docs/architecture/fixtures/release-review-v1.json");
            if (!Files.isRegularFile(file)) continue;
            var review = SFMReleaseReviewV1Codec.parse(Files.readString(file));
            var sources = new HashMap<String, SFMReviewEvidenceTable.Observed>();
            for (var view : SFMReleaseReviewCorpus.from(review).documents()) {
                if (view.materializedDocument().isEmpty()) continue;
                var row = view.binding();
                sources.put(row.documentRevisionId(), new SFMReviewEvidenceTable.Observed(
                        new SFMReviewEvidenceTable.Document(row.documentRevisionId(), row.path(), row.sha256(), Optional.empty()),
                        view.materializedDocument().orElseThrow().text()));
            }
            return new SFMReleaseReviewLedgerResolver.Resolved(review, sources);
        }
        throw new IllegalStateException("Missing review fixture");
    }
    @Test void previewIsScopedAndDoesNotModifyTheReview() throws Exception {
        var observed = fixture();
        String before = SFMReleaseReviewV1Codec.write(observed.document());
        var plan = SFMReviewMigrationPlan.preview(observed, "human:approved-value", "1.19.2", SFMReviewMigrationPreview.Limits.DEFAULT);
        assertEquals(1, plan.ranges().size());
        assertEquals(SFMReviewMigrationPreview.Status.EXACT, plan.ranges().get(0).status());
        assertTrue(plan.canAccept());
        assertEquals(before, SFMReleaseReviewV1Codec.write(observed.document()));
        assertThrows(IllegalArgumentException.class, () -> SFMReviewMigrationPlan.preview(observed,
                "human:approved-value", "not-a-current-lane", SFMReviewMigrationPreview.Limits.DEFAULT));
    }
    @Test void missingDestinationBytesCannotEstablishUniqueness() throws Exception {
        var observed = fixture();
        var sources = new HashMap<>(observed.sources());
        sources.remove("1.19.2:after:src/Other.java");
        var incomplete = new SFMReleaseReviewLedgerResolver.Resolved(observed.document(), sources);
        var plan = SFMReviewMigrationPlan.preview(incomplete, "human:approved-value", "1.19.2", SFMReviewMigrationPreview.Limits.DEFAULT);
        assertFalse(plan.canAccept());
        assertEquals(SFMReviewMigrationPreview.Status.INCOMPLETE, plan.ranges().get(0).status());
    }
    @Test void resolverDiagnosticPreventsAcceptanceAndGeneratedMarkersAreRejected() throws Exception {
        var observed = fixture();
        var incomplete = new SFMReleaseReviewLedgerResolver.Resolved(observed.document(), observed.sources(), List.of("racing read"));
        assertFalse(SFMReviewMigrationPlan.preview(incomplete, "human:approved-value", "1.19.2",
                SFMReviewMigrationPreview.Limits.DEFAULT).canAccept());
        assertThrows(IllegalArgumentException.class, () -> SFMReviewMigrationPlan.preview(observed,
                "generated:unit-value", "1.19.2", SFMReviewMigrationPreview.Limits.DEFAULT));
    }
}
