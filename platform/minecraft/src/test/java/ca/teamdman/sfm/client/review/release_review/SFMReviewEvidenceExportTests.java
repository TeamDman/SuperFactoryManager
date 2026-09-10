package ca.teamdman.sfm.client.review.release_review;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class SFMReviewEvidenceExportTests {
    private static final String TEXT = "// exact Unicode evidence\r\nclass Café {}\r\n";
    private static SFMReviewEvidenceTable.Document document(String id, boolean git) throws Exception {
        byte[] bytes = TEXT.getBytes(StandardCharsets.UTF_8);
        var digest = java.security.MessageDigest.getInstance("SHA-1");
        digest.update(("blob " + bytes.length + "\0").getBytes(StandardCharsets.US_ASCII));
        var reference = new SFMReviewEvidenceTable.GitReference("sfm", "1".repeat(40),
                java.util.HexFormat.of().formatHex(digest.digest(bytes)));
        return new SFMReviewEvidenceTable.Document(id, "src/Cafe.java", SFMReleaseReviewKernel.sha256(bytes),
                git ? Optional.of(reference) : Optional.empty());
    }
    private static String ledger(List<SFMReviewEvidenceTable.Document> documents, boolean embedded) {
        var base = SFMReleaseReviewLedgerV3.create("export-test", "Export test", List.of(
                new SFMReleaseReviewLedgerV3.TargetLane("1.19.2", "sfm", ".", "1".repeat(40),
                        Optional.empty(), List.of("."), List.of(), true)));
        var contents = embedded ? List.of(new SFMReviewEvidenceTable.Content(documents.get(0).sha256(), TEXT))
                : List.<SFMReviewEvidenceTable.Content>of();
        return SFMReleaseReviewLedgerV3Codec.write(new SFMReleaseReviewLedgerV3(base.targets(), base.state(),
                new SFMReviewEvidenceTable(contents, documents)));
    }
    @Test void portableCopyEmbedsExactGitBytesAndPreservesIdentities() throws Exception {
        var doc = document("git-source", true);
        String original = ledger(List.of(doc), false);
        var prepared = SFMReviewEvidenceExport.prepare(original, SFMReviewEvidenceExport.Policy.EMBED_COMMENT_EVIDENCE,
                Map.of(doc.revisionId(), new SFMReviewEvidenceTable.Observed(doc, TEXT)));
        assertTrue(prepared.commentEvidencePortable());
        assertEquals(TEXT, prepared.ledger().evidence().contents().get(0).text());
        assertEquals(List.of(doc), prepared.ledger().evidence().documents());
        assertEquals(SFMReleaseReviewLedgerV3Codec.parse(original).state(), prepared.ledger().state());
        assertEquals(SFMReleaseReviewLedgerV3Codec.parse(original).targets(), prepared.ledger().targets());
        assertEquals(original.getBytes(StandardCharsets.UTF_8).length, prepared.originalBytes());
        assertEquals(prepared.output().getBytes(StandardCharsets.UTF_8).length, prepared.outputBytes());
        assertEquals(TEXT.getBytes(StandardCharsets.UTF_8).length, prepared.embeddedUtf8Bytes());
        assertTrue(prepared.outputBytes() > prepared.originalBytes());
    }
    @Test void portableEmbeddedEvidenceDoesNotRequireAvailableGitObjects() throws Exception {
        String original = ledger(List.of(document("git-source", true)), true);
        var result = SFMReviewEvidenceExport.prepare(original, SFMReviewEvidenceExport.Policy.EMBED_COMMENT_EVIDENCE, Map.of());
        assertTrue(result.commentEvidencePortable());
        assertEquals(original, result.output());
    }
    @Test void missingOrWrongGitEvidenceCannotProducePortableCopy() throws Exception {
        var doc = document("git-source", true);
        String original = ledger(List.of(doc), false);
        assertThrows(IllegalArgumentException.class, () -> SFMReviewEvidenceExport.prepare(original,
                SFMReviewEvidenceExport.Policy.EMBED_COMMENT_EVIDENCE, Map.of()));
        var wrong = document("other-source", true);
        assertThrows(IllegalArgumentException.class, () -> SFMReviewEvidenceExport.prepare(original,
                SFMReviewEvidenceExport.Policy.EMBED_COMMENT_EVIDENCE,
                Map.of(doc.revisionId(), new SFMReviewEvidenceTable.Observed(wrong, TEXT))));
    }
    @Test void removingGitEmbeddingRequiresVerificationAndReportsDependency() throws Exception {
        var doc = document("git-source", true);
        String original = ledger(List.of(doc), true);
        assertThrows(IllegalArgumentException.class, () -> SFMReviewEvidenceExport.prepare(original,
                SFMReviewEvidenceExport.Policy.VERIFIED_GIT_REFERENCES, Map.of()));
        var compacted = SFMReviewEvidenceExport.prepare(original, SFMReviewEvidenceExport.Policy.VERIFIED_GIT_REFERENCES,
                Map.of(doc.revisionId(), new SFMReviewEvidenceTable.Observed(doc, TEXT)));
        assertFalse(compacted.commentEvidencePortable());
        assertEquals(1, compacted.gitDependentDocuments());
        assertEquals(0, compacted.embeddedBodies());
        assertTrue(compacted.outputBytes() < compacted.originalBytes());
    }
    @Test void sharedNonGitBodyCannotBeRemovedAndDuplicateGitBytesAreDeduplicated() throws Exception {
        var first = document("git-one", true);
        var second = document("git-two", true);
        var live = document("disk-source", false);
        var witnesses = Map.of(first.revisionId(), new SFMReviewEvidenceTable.Observed(first, TEXT),
                second.revisionId(), new SFMReviewEvidenceTable.Observed(second, TEXT));
        var portable = SFMReviewEvidenceExport.prepare(ledger(List.of(first, second), false),
                SFMReviewEvidenceExport.Policy.EMBED_COMMENT_EVIDENCE, witnesses);
        assertEquals(1, portable.embeddedBodies());
        var compacted = SFMReviewEvidenceExport.prepare(ledger(List.of(first, second, live), true),
                SFMReviewEvidenceExport.Policy.VERIFIED_GIT_REFERENCES, witnesses);
        assertEquals(1, compacted.embeddedBodies());
        assertTrue(compacted.commentEvidencePortable());
    }
    @Test void separatelyRecognizedSharedBytesPreserveBothOriginalTargets() throws Exception {
        var first = document("disk-one", false);
        var second = new SFMReviewEvidenceTable.Document("disk-two", "renamed/Cafe.java", first.sha256(), Optional.empty());
        var reference = document("storage", true).git().orElseThrow();
        var proof = new SFMReviewGitEvidenceVerifier.Recognized(new SFMReviewEvidenceTable.Observed(first, TEXT),
                reference, "committed/Cafe.java");
        String original = ledger(List.of(first, second), true);
        var compact = SFMReviewEvidenceExport.prepare(original, SFMReviewEvidenceExport.Policy.VERIFIED_GIT_REFERENCES,
                Map.of(), Map.of(first.sha256(), proof));
        assertEquals(List.of(first, second), compact.ledger().evidence().documents());
        assertEquals(2, compact.gitDependentDocuments());
        assertEquals(0, compact.embeddedBodies());
        assertEquals(1, compact.ledger().evidence().gitStorage().size());
        assertThrows(IllegalArgumentException.class, () -> SFMReviewEvidenceExport.prepare(compact.output(),
                SFMReviewEvidenceExport.Policy.EMBED_COMMENT_EVIDENCE, Map.of()));
        var portable = SFMReviewEvidenceExport.prepare(compact.output(), SFMReviewEvidenceExport.Policy.EMBED_COMMENT_EVIDENCE,
                Map.of(), Map.of(first.sha256(), proof));
        assertTrue(portable.commentEvidencePortable());
        assertEquals(1, portable.embeddedBodies());
        assertEquals(List.of(first, second), portable.ledger().evidence().documents());
        var bad = new SFMReviewGitEvidenceVerifier.Recognized(proof.original(),
                new SFMReviewEvidenceTable.GitReference("sfm", reference.commit(), "0".repeat(40)), proof.storagePath());
        assertThrows(IllegalArgumentException.class, () -> SFMReviewEvidenceExport.prepare(original,
                SFMReviewEvidenceExport.Policy.VERIFIED_GIT_REFERENCES, Map.of(), Map.of(first.sha256(), bad)));
    }
}
