package ca.teamdman.sfm.client.screen.review;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SFMSourceComparisonTests {
    @Test
    void fixtureCoversRequestedFileAndOperationFamilies() {
        var comparison = SFMSourceComparisonFixtures.reviewWalkthrough();
        assertEquals(List.of(
                SFMSourceComparison.FileChangeKind.ADDED,
                SFMSourceComparison.FileChangeKind.DELETED,
                SFMSourceComparison.FileChangeKind.RENAMED,
                SFMSourceComparison.FileChangeKind.MODIFIED
        ), comparison.files().stream().map(SFMSourceComparison.FileChange::kind).toList());
        assertEquals(List.of(
                SFMSourceComparison.OperationKind.INSERT,
                SFMSourceComparison.OperationKind.DELETE,
                SFMSourceComparison.OperationKind.RENAME,
                SFMSourceComparison.OperationKind.CHANGED_BODY
        ), comparison.operations().stream().map(SFMSourceComparison.SourceOperation::kind).toList());
    }

    @Test
    void reviewApprovalAndAuditPolicyAreIndependent() {
        var comparison = SFMSourceComparisonFixtures.reviewWalkthrough();
        var operation = comparison.operations().get(3);
        var ledger = new SFMReviewLedger();
        ledger.put(comparison, operation, SFMReviewLedger.ReviewState.REVIEWED,
                SFMReviewLedger.HumanDecision.APPROVED);
        var decision = ledger.get(comparison, operation);
        assertEquals(SFMReviewLedger.ReviewState.REVIEWED, decision.reviewState());
        assertEquals(SFMReviewLedger.HumanDecision.APPROVED, decision.humanDecision());
        assertEquals(SFMSourceComparison.AuditStatus.FORBIDDEN, operation.auditStatus());
        assertFalse(decision.isStale(operation));
    }

    @Test
    void decisionsRoundTripThroughAtomicJson(@TempDir Path directory) {
        Path path = directory.resolve("nested").resolve("ledger.json");
        var comparison = SFMSourceComparisonFixtures.reviewWalkthrough();
        var operation = comparison.operations().get(2);
        var ledger = SFMReviewLedger.open(path);
        ledger.put(comparison, operation, SFMReviewLedger.ReviewState.REVIEWED,
                SFMReviewLedger.HumanDecision.APPROVED);

        var restored = SFMReviewLedger.open(path);
        assertEquals(1, restored.size());
        assertEquals(SFMReviewLedger.HumanDecision.APPROVED,
                restored.get(comparison, operation).humanDecision());
        assertTrue(restored.persistenceStatus().startsWith("Loaded 1"));
    }

    @Test
    void sourceHashChangeMakesRestoredApprovalStale(@TempDir Path directory) {
        Path path = directory.resolve("ledger.json");
        var comparison = SFMSourceComparisonFixtures.reviewWalkthrough();
        var body = comparison.operations().get(3);
        var ledger = SFMReviewLedger.open(path);
        ledger.put(comparison, body, SFMReviewLedger.ReviewState.REVIEWED,
                SFMReviewLedger.HumanDecision.APPROVED);
        var changed = SFMSourceComparisonFixtures.changeBodySource(comparison);
        var changedBody = changed.operations().get(3);

        var restored = SFMReviewLedger.open(path);
        assertEquals(body.id(), changedBody.id());
        assertNotEquals(body.afterHash(), changedBody.afterHash());
        assertTrue(restored.get(changed, changedBody).isStale(changedBody));
        assertEquals(SFMReviewLedger.HumanDecision.APPROVED,
                restored.get(changed, changedBody).humanDecision());
    }

    @Test
    void corruptLedgerFailsClosedWithoutDiscardingDiagnostics(@TempDir Path directory) throws Exception {
        Path path = directory.resolve("ledger.json");
        java.nio.file.Files.writeString(path, "{not-json");
        var ledger = SFMReviewLedger.open(path);
        assertEquals(0, ledger.size());
        assertTrue(ledger.persistenceStatus().startsWith("Ledger load failed:"));
    }

    @Test
    void fallbackComparisonIsDeterministicAndRetainsChangedRanges() {
        var first = SFMLineRangeComparator.compare("before", "after", "Example.java",
                "one\ntwo\nthree", "one\nchanged\nthree");
        var second = SFMLineRangeComparator.compare("before", "after", "Example.java",
                "one\ntwo\nthree", "one\nchanged\nthree");
        assertEquals(first, second);
        var operation = first.operations().get(0);
        assertEquals(SFMSourceComparison.OperationKind.REPLACE, operation.kind());
        assertEquals(List.of("two"), operation.beforeLines());
        assertEquals(List.of("changed"), operation.afterLines());
        assertEquals(new SFMSourceComparison.SourceSpan(2, 2), operation.beforeSpan());
    }
}
