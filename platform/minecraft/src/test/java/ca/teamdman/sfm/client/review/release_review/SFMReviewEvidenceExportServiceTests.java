package ca.teamdman.sfm.client.review.release_review;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class SFMReviewEvidenceExportServiceTests {
    @TempDir Path directory;
    private String git(String... args) throws Exception {
        var command = new java.util.ArrayList<>(java.util.List.of("git", "-C", directory.toString()));
        command.addAll(java.util.List.of(args));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        try {
            assertTrue(process.waitFor(15, java.util.concurrent.TimeUnit.SECONDS));
            String result = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertEquals(0, process.exitValue(), result);
            return result.trim();
        } finally { if (process.isAlive()) process.destroyForcibly(); }
    }
    @Test void laterCommittedCaptureCompactsAndRestoresWithoutChangingComments() throws Exception {
        var ledger = SFMReviewOfflineEvidenceTests.fixture();
        git("init", "--quiet");
        git("config", "user.name", "Disposable export test");
        git("config", "user.email", "review@example.invalid");
        git("config", "core.autocrlf", "false");
        for (var document : ledger.evidence().documents()) {
            Path file = directory.resolve(document.path());
            Files.createDirectories(file.getParent());
            String body = ledger.evidence().contents().stream().filter(c -> c.sha256().equals(document.sha256()))
                    .findFirst().orElseThrow().text();
            Files.writeString(file, body);
        }
        git("add", ".");
        git("commit", "--quiet", "-m", "Captured bytes now committed");
        String commit = git("rev-parse", "HEAD");
        var target = ledger.targets().get(0);
        ledger = new SFMReleaseReviewLedgerV3(java.util.List.of(new SFMReleaseReviewLedgerV3.TargetLane(
                target.id(), target.repositoryId(), target.rootHint(), commit, java.util.Optional.empty(),
                target.scopePaths(), java.util.List.of("original.sfm-review.json", "compact.sfm-review.json",
                "portable.sfm-review.json", "missing.sfm-review.json"), true)), ledger.state(), ledger.evidence());
        String original = SFMReleaseReviewLedgerV3Codec.write(ledger);
        Path source = directory.resolve("original.sfm-review.json"), compact = directory.resolve("compact.sfm-review.json");
        Files.writeString(source, original);
        var preview = SFMReviewEvidenceExportService.preview(source, compact, hash(original),
                SFMReviewEvidenceExport.Policy.VERIFIED_GIT_REFERENCES);
        var output = preview.prepared().ledger();
        assertEquals(ledger.state(), output.state());
        assertEquals(ledger.evidence().documents(), output.evidence().documents());
        assertFalse(output.evidence().gitStorage().isEmpty());
        assertTrue(output.evidence().gitStorage().stream().allMatch(entry -> entry.git().commit().equals(commit)));
        assertEquals(0, preview.prepared().embeddedBodies());
        assertFalse(preview.prepared().commentEvidencePortable());
        SFMReviewEvidenceExportFile.publish(source, compact, preview.prepared());
        assertEquals(output, SFMReleaseReviewLedgerV3Codec.parse(Files.readString(compact)));
        assertFalse(SFMReviewOfflineEvidence.inspect(output).missingDocumentIds().isEmpty());
        // A new live edit must not be mistaken for the committed storage bytes.
        for (var document : ledger.evidence().documents()) Files.writeString(directory.resolve(document.path()), "changed live text\n");
        String status = git("status", "--porcelain");
        var portable = SFMReviewEvidenceExportService.preview(compact, directory.resolve("portable.sfm-review.json"),
                hash(Files.readString(compact)), SFMReviewEvidenceExport.Policy.EMBED_COMMENT_EVIDENCE);
        assertTrue(portable.prepared().commentEvidencePortable());
        assertEquals(SFMReviewOfflineEvidence.inspect(ledger), SFMReviewOfflineEvidence.inspect(portable.prepared().ledger()));
        assertEquals(original, Files.readString(source));
        assertEquals(status, git("status", "--porcelain"));
        if ("true".equals(System.getenv("SFM_TEST_LEDGER_COMPANION"))) {
            var resolved = SFMReleaseReviewLedgerResolver.companion(compact, Files.readString(compact));
            for (var captured : ledger.evidence().documents()) {
                assertEquals(captured, resolved.sources().get(captured.revisionId()).document());
                assertTrue(resolved.sources().get(captured.revisionId()).document().git().isEmpty());
            }
            for (var comment : ledger.state().reviewSession().comments())
                assertTrue(resolved.document().reviewSession().comments().contains(comment));
        }
        String inspection = SFMReviewStorageInspection.describe(Files.readString(compact), ledger.state(),
                java.util.Optional.of(output.evidence().documents().get(0).revisionId()));
        assertTrue(inspection.contains("original non-Git target identity unchanged"));
        // Structurally valid provenance with a nonexistent object must not permit
        // a portable publication. The compact file is a disposable test artifact.
        var missingStorage = output.evidence().gitStorage().stream().map(entry -> new SFMReviewEvidenceTable.GitStorage(
                entry.sha256(), entry.path(), new SFMReviewEvidenceTable.GitReference(entry.git().repositoryId(),
                "0".repeat(40), entry.git().blob()))).toList();
        String unavailable = SFMReleaseReviewLedgerV3Codec.write(new SFMReleaseReviewLedgerV3(output.targets(), output.state(),
                new SFMReviewEvidenceTable(java.util.List.of(), output.evidence().documents(), missingStorage)));
        Path missing = directory.resolve("missing.sfm-review.json");
        Files.writeString(missing, unavailable);
        Path refused = directory.resolve("must-not-exist.sfm-review.json");
        assertThrows(Exception.class, () -> SFMReviewEvidenceExportService.preview(missing, refused, hash(unavailable),
                SFMReviewEvidenceExport.Policy.EMBED_COMMENT_EVIDENCE));
        assertFalse(Files.exists(refused));
    }
    @Test void portableCopyRebasesHintsButPreservesEvidenceAndQueriesWithoutGitReads() throws Exception {
        var ledger = SFMReviewOfflineEvidenceTests.fixture();
        String original = SFMReleaseReviewLedgerV3Codec.write(ledger);
        Path source = directory.resolve("original.sfm-review.json");
        Files.writeString(source, original);
        Path other = Files.createDirectory(directory.resolve("elsewhere"));
        Path output = other.resolve("portable.sfm-review.json");
        var preview = SFMReviewEvidenceExportService.preview(source, output, hash(original),
                SFMReviewEvidenceExport.Policy.EMBED_COMMENT_EVIDENCE);
        assertFalse(Files.exists(output));
        assertTrue(preview.repositoryHintsRebased());
        assertTrue(preview.prepared().commentEvidencePortable());
        assertEquals(source.toRealPath().getParent(), Path.of(preview.prepared().ledger().targets().get(0).rootHint()));
        assertEquals(ledger.state(), preview.prepared().ledger().state());
        assertEquals(ledger.evidence(), preview.prepared().ledger().evidence());
        SFMReviewEvidenceExportFile.publish(preview.source(), preview.destination(), preview.prepared());
        var reopened = SFMReleaseReviewLedgerV3Codec.parse(Files.readString(output));
        assertEquals(SFMReviewOfflineEvidence.inspect(ledger), SFMReviewOfflineEvidence.inspect(reopened));
        assertEquals(original, Files.readString(source));
    }
    @Test void sameDirectoryPreservesHintsAndStaleAuthorityOrExistingDestinationFails() throws Exception {
        String original = SFMReleaseReviewLedgerV3Codec.write(SFMReviewOfflineEvidenceTests.fixture());
        Path source = directory.resolve("original.sfm-review.json");
        Path output = directory.resolve("copy.sfm-review.json");
        Files.writeString(source, original);
        var preview = SFMReviewEvidenceExportService.preview(source, output, hash(original),
                SFMReviewEvidenceExport.Policy.EMBED_COMMENT_EVIDENCE);
        assertFalse(preview.repositoryHintsRebased());
        Files.writeString(source, original + "\n");
        assertThrows(java.io.IOException.class, () -> SFMReviewEvidenceExportService.preview(source, output, hash(original),
                SFMReviewEvidenceExport.Policy.EMBED_COMMENT_EVIDENCE));
        assertThrows(java.io.IOException.class, () -> SFMReviewEvidenceExportFile.publish(source, output, preview.prepared()));
        assertFalse(Files.exists(output));
        Files.writeString(output, "belongs to user");
        assertThrows(java.io.IOException.class, () -> SFMReviewEvidenceExportService.preview(source, output, hash(original + "\n"),
                SFMReviewEvidenceExport.Policy.EMBED_COMMENT_EVIDENCE));
        assertEquals("belongs to user", Files.readString(output));
    }
    private static String hash(String text) { return SFMReleaseReviewKernel.sha256(text.getBytes(StandardCharsets.UTF_8)); }
}
