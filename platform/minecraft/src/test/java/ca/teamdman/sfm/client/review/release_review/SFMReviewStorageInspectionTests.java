package ca.teamdman.sfm.client.review.release_review;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class SFMReviewStorageInspectionTests {
    @org.junit.jupiter.api.io.TempDir Path temporary;

    @Test void authorityReadRejectsExternalChangesAndDoesNotResolveSourcePaths() throws Exception {
        var observation = fixture();
        String authority = SFMReleaseReviewV1Codec.write(observation);
        Path file = temporary.resolve("review.json");
        Files.writeString(file, authority);
        String hash = SFMReleaseReviewKernel.sha256(authority.getBytes(StandardCharsets.UTF_8));
        assertTrue(SFMReviewStorageInspection.read(file, hash, observation, Optional.empty()).contains("legacy frozen review"));
        assertEquals(authority, Files.readString(file));
        Files.writeString(file, authority + "\n");
        assertThrows(java.io.IOException.class, () -> SFMReviewStorageInspection.read(file, hash, observation, Optional.empty()));
        assertEquals(authority + "\n", Files.readString(file));
    }
    @Test void smallLedgerReportsTransientSourceWithoutEmbeddingOrResolvingIt() throws Exception {
        var observation = fixture();
        var binding = observation.repositoryBindings().get(0);
        var ledger = SFMReleaseReviewLedgerV3.create("inspection", "Inspection", List.of(
                new SFMReleaseReviewLedgerV3.TargetLane(binding.laneId(), binding.repositoryId(), ".", binding.beforeCommit(),
                        Optional.empty(), List.of("."), List.of("review.json"), true)));
        String authority = SFMReleaseReviewLedgerV3Codec.write(ledger);
        String document = observation.corpusDocuments().get(0).documentRevisionId();
        String report = SFMReviewStorageInspection.describe(authority, observation, Optional.of(document));
        assertTrue(report.contains("authority-utf8-bytes: " + authority.getBytes(StandardCharsets.UTF_8).length));
        assertTrue(report.contains("deduplicated-content-bodies: 0"));
        assertTrue(report.contains("storage: not retained as comment evidence"));
        assertTrue(report.contains("selected-document-revision: " + document));
        var selected = observation.corpusDocuments().get(0);
        var selectedBinding = observation.repositoryBindings().stream().filter(value -> value.laneId().equals(selected.laneId())).findFirst().orElseThrow();
        assertTrue(report.contains("observed-side: " + selected.snapshotSide()));
        assertTrue(report.contains("observation-repository: " + selectedBinding.repositoryId()));
        assertTrue(report.contains("observation-commit: " + (selected.snapshotSide() == SFMReleaseReviewV1.SnapshotSide.BEFORE
                ? selectedBinding.beforeCommit() : selectedBinding.candidateCommit())));
        assertEquals(authority, SFMReleaseReviewLedgerV3Codec.write(ledger));
        assertThrows(IllegalArgumentException.class, () -> SFMReviewStorageInspection.describe(authority, observation, Optional.of("wrong-document")));
    }

    @Test void legacyStorageIsExplainedWithoutPretendingItIsLazy() throws Exception {
        var review = fixture();
        String report = SFMReviewStorageInspection.describe(SFMReleaseReviewV1Codec.write(review), review, Optional.empty());
        assertTrue(report.contains("legacy frozen review"));
        assertFalse(report.contains("storage-model: single-file target ledger"));
    }

    @Test void retainedEvidenceDistinguishesGitReferencesFromDeduplicatedUnicodeBodies() throws Exception {
        var observation = fixture();
        var binding = observation.repositoryBindings().get(0);
        var empty = SFMReleaseReviewLedgerV3.create("inspection", "Inspection", List.of(
                new SFMReleaseReviewLedgerV3.TargetLane(binding.laneId(), binding.repositoryId(), ".", binding.beforeCommit(),
                        Optional.empty(), List.of("."), List.of(), true)));
        String body = "\"hello\"\r\n\uD83D\uDE00";
        String hash = SFMReleaseReviewKernel.sha256(body.getBytes(StandardCharsets.UTF_8));
        var evidence = new SFMReviewEvidenceTable(List.of(new SFMReviewEvidenceTable.Content(hash, body)), List.of(
                new SFMReviewEvidenceTable.Document("old-a", "same.java", hash, Optional.empty()),
                new SFMReviewEvidenceTable.Document("old-b", "same.java", hash, Optional.empty()),
                new SFMReviewEvidenceTable.Document("git-c", "same.java", hash, Optional.of(
                        new SFMReviewEvidenceTable.GitReference(binding.repositoryId(), binding.beforeCommit(), "a".repeat(40))))));
        String authority = SFMReleaseReviewLedgerV3Codec.write(new SFMReleaseReviewLedgerV3(empty.targets(), empty.state(), evidence));
        String embedded = SFMReviewStorageInspection.describe(authority, observation, Optional.of("old-b"));
        assertTrue(embedded.contains("deduplicated-content-bodies: 1"));
        assertTrue(embedded.contains("retained-document-identities: 3"));
        assertTrue(embedded.contains("embedded-source-utf8-bytes: " + body.getBytes(StandardCharsets.UTF_8).length));
        assertTrue(embedded.contains("storage: embedded exact non-Git source"));
        assertTrue(embedded.contains("original-evidence-capture-time: not recorded"));
        assertFalse(embedded.contains("observation-source:"));
        String git = SFMReviewStorageInspection.describe(authority, observation, Optional.of("git-c"));
        assertTrue(git.contains("storage: Git reference; repository objects required"));
        assertTrue(git.contains("blob: " + "a".repeat(40)));
        assertFalse(git.contains("storage: embedded exact non-Git source"));
        assertThrows(IllegalArgumentException.class, () -> SFMReviewStorageInspection.describe(authority, observation, Optional.of("same.java")));
    }

    private static SFMReleaseReviewV1 fixture() throws Exception {
        for (Path root = Path.of("").toAbsolutePath(); root != null; root = root.getParent()) {
            Path file = root.resolve("docs/architecture/fixtures/release-review-v1.json");
            if (Files.isRegularFile(file)) return SFMReleaseReviewV1Codec.parse(Files.readString(file));
        }
        throw new IllegalStateException("Review fixture not found");
    }
}
