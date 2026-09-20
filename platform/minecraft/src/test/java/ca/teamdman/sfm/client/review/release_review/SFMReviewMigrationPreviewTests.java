package ca.teamdman.sfm.client.review.release_review;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class SFMReviewMigrationPreviewTests {
    private static SFMReviewEvidenceTable.Observed source(String id, String path, String text) {
        return new SFMReviewEvidenceTable.Observed(new SFMReviewEvidenceTable.Document(id, path,
                SFMReleaseReviewKernel.sha256(text.getBytes(StandardCharsets.UTF_8)), Optional.empty()), text);
    }
    private static SFMReviewMigrationPreview.Result match(String text, boolean complete) {
        return SFMReviewMigrationPreview.match(source("old", "A.java", "value"), 0, 5,
                List.of(source("new", "A.java", text)), complete, SFMReviewMigrationPreview.Limits.DEFAULT);
    }
    @Test void uniqueSameOffsetIsOnlyAPreviewNotImplicitAcceptance() {
        var result = match("value changed elsewhere", true);
        assertEquals(SFMReviewMigrationPreview.Status.EXACT, result.status());
        assertEquals("new", result.candidates().get(0).documentRevisionId());
        assertTrue(result.diagnostic().contains("explicit acceptance"));
    }
    @Test void insertionRelocatesAndDeletionIsMissing() {
        var moved = match("prefix value", true);
        assertEquals(SFMReviewMigrationPreview.Status.RELOCATED, moved.status());
        assertEquals(7, moved.candidates().get(0).startByte());
        assertEquals(SFMReviewMigrationPreview.Status.MISSING, match("gone", true).status());
    }
    @Test void sameOffsetDoesNotHideAmbiguousRepeatedOccurrence() {
        var result = match("value value", true);
        assertEquals(SFMReviewMigrationPreview.Status.AMBIGUOUS, result.status());
        assertEquals(2, result.candidates().size());
        assertFalse(result.uniquelyApplicable());
    }
    @Test void unavailableDomainAndWorkLimitCannotClaimUniqueMatch() {
        assertEquals(SFMReviewMigrationPreview.Status.INCOMPLETE, match("value", false).status());
        var limited = SFMReviewMigrationPreview.match(source("old", "A", "value"), 0, 5,
                List.of(source("new", "A", "value")), true, new SFMReviewMigrationPreview.Limits(1, 1));
        assertEquals(SFMReviewMigrationPreview.Status.INCOMPLETE, limited.status());
        assertEquals(1, limited.comparedBytes());
        assertFalse(limited.uniquelyApplicable());
    }
    @Test void unicodeOffsetsAndRenameAreExplicit() {
        var result = SFMReviewMigrationPreview.match(source("old", "A", "é"), 0, 2,
                List.of(source("new", "B", "🙂é")), true, SFMReviewMigrationPreview.Limits.DEFAULT);
        assertEquals(SFMReviewMigrationPreview.Status.RELOCATED, result.status());
        assertEquals(4, result.candidates().get(0).startByte());
        assertEquals(6, result.candidates().get(0).endByte());
        assertThrows(IllegalArgumentException.class, () -> SFMReviewMigrationPreview.match(source("old", "A", "é"), 1, 2,
                List.of(), true, SFMReviewMigrationPreview.Limits.DEFAULT));
    }
    @Test void overlappingCandidatesAreNotDiscardedAndCapIsConservative() {
        var result = SFMReviewMigrationPreview.match(source("old", "A", "aa"), 0, 2,
                List.of(source("new", "A", "aaa")), true, SFMReviewMigrationPreview.Limits.DEFAULT);
        assertEquals(2, result.candidates().size());
        var capped = SFMReviewMigrationPreview.match(source("old", "A", "aa"), 0, 2,
                List.of(source("new", "A", "aaa")), true, new SFMReviewMigrationPreview.Limits(100, 1));
        assertEquals(SFMReviewMigrationPreview.Status.INCOMPLETE, capped.status());
    }
    @Test void emptyWitnessAndCrLfInteriorAreNotRelocated() {
        var empty = SFMReviewMigrationPreview.match(source("old", "A", ""), 0, 0,
                List.of(), true, SFMReviewMigrationPreview.Limits.DEFAULT);
        assertEquals(SFMReviewMigrationPreview.Status.INCOMPLETE, empty.status());
        var interior = SFMReviewMigrationPreview.match(source("old", "A", "\n"), 0, 1,
                List.of(source("new", "A", "\r\n")), true, SFMReviewMigrationPreview.Limits.DEFAULT);
        assertEquals(SFMReviewMigrationPreview.Status.MISSING, interior.status());
    }
}
