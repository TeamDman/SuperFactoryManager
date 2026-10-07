package ca.teamdman.sfm.client.review.release_review;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class SFMReviewGitEvidenceVerifierTests {
    @TempDir Path repository;
    private String git(String... args) throws Exception {
        var command = new ArrayList<>(List.of("git", "-C", repository.toString()));
        command.addAll(List.of(args));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        try {
            assertTrue(process.waitFor(15, TimeUnit.SECONDS));
            String result = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertEquals(0, process.exitValue(), result);
            return result.trim();
        } finally { if (process.isAlive()) process.destroyForcibly(); }
    }
    @Test void recognizesLaterCommitWithoutRewritingOriginalIdentityOrReadingWorkingBytes() throws Exception {
        git("init", "--quiet");
        git("config", "user.name", "Disposable recognition test");
        git("config", "user.email", "review@example.invalid");
        git("config", "core.autocrlf", "false");
        String body = "class Café { int x = 1; }\r\n";
        var original = new SFMReviewEvidenceTable.Document("working-tree-capture", "Before.java",
                SFMReleaseReviewKernel.sha256(body.getBytes(StandardCharsets.UTF_8)), Optional.empty());
        // Capture predates committing and renaming the source. Storage provenance
        // may differ; the human's original selector identity may not.
        Files.writeString(repository.resolve("After.java"), body);
        git("add", "--", "After.java");
        git("commit", "--quiet", "-m", "Later committed evidence");
        String commit = git("rev-parse", "HEAD");
        Files.writeString(repository.resolve("After.java"), "different live bytes\n");
        String status = git("status", "--porcelain");
        var recognized = SFMReviewGitEvidenceVerifier.recognize(original, "sfm", commit, "After.java", repository);
        assertEquals(original, recognized.original().document());
        assertTrue(recognized.original().document().git().isEmpty());
        assertEquals(body, recognized.original().text());
        assertEquals("After.java", recognized.storagePath());
        assertEquals(commit, recognized.storage().commit());
        assertEquals(git("rev-parse", commit + ":After.java"), recognized.storage().blob());
        var mismatch = new SFMReviewEvidenceTable.Document(original.revisionId(), original.path(),
                "0".repeat(64), Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> SFMReviewGitEvidenceVerifier.recognize(
                mismatch, "sfm", commit, "After.java", repository));
        assertThrows(Exception.class, () -> SFMReviewGitEvidenceVerifier.recognize(
                original, "sfm", commit, "missing.java", repository));
        assertThrows(IllegalArgumentException.class, () -> SFMReviewGitEvidenceVerifier.recognize(
                original, "sfm", "HEAD", "After.java", repository));
        for (String unsafe : List.of("../After.java", "/After.java", "D:/After.java", "a//b", "a/./b", "a\\b"))
            assertThrows(IllegalArgumentException.class, () -> SFMReviewGitEvidenceVerifier.recognize(
                    original, "sfm", commit, unsafe, repository));
        assertEquals(status, git("status", "--porcelain"));
    }
    @Test void verifiesCommittedBytesNotChangedWorkingTreeAndRejectsFalseLinks() throws Exception {
        git("init", "--quiet");
        git("config", "user.name", "Disposable evidence test");
        git("config", "user.email", "review@example.invalid");
        git("config", "core.autocrlf", "false");
        String path = "Café evidence.java";
        String body = "class Café { int x = 1; }\r\n";
        Files.writeString(repository.resolve(path), body);
        git("add", "--", path);
        git("commit", "--quiet", "-m", "Disposable baseline");
        String commit = git("rev-parse", "HEAD"), blob = git("rev-parse", "HEAD:" + path);
        var reference = new SFMReviewEvidenceTable.GitReference("sfm", commit, blob);
        var document = new SFMReviewEvidenceTable.Document("captured-identity", path,
                SFMReleaseReviewKernel.sha256(body.getBytes(StandardCharsets.UTF_8)), Optional.of(reference));
        Files.writeString(repository.resolve(path), "uncommitted changes must not be read\n");
        String status = git("status", "--porcelain");
        var verified = SFMReviewGitEvidenceVerifier.verify(document, repository);
        assertEquals(body, verified.text());
        assertEquals(document, verified.document());
        assertEquals(status, git("status", "--porcelain"));
        var wrongPath = new SFMReviewEvidenceTable.Document(document.revisionId(), "missing.java", document.sha256(), document.git());
        assertThrows(Exception.class, () -> SFMReviewGitEvidenceVerifier.verify(wrongPath, repository));
        var wrongBlob = new SFMReviewEvidenceTable.Document(document.revisionId(), path, document.sha256(),
                Optional.of(new SFMReviewEvidenceTable.GitReference("sfm", commit, "0".repeat(40))));
        assertThrows(Exception.class, () -> SFMReviewGitEvidenceVerifier.verify(wrongBlob, repository));
        var wrongHash = new SFMReviewEvidenceTable.Document(document.revisionId(), path, "0".repeat(64), document.git());
        assertThrows(IllegalArgumentException.class, () -> SFMReviewGitEvidenceVerifier.verify(wrongHash, repository));
        assertEquals(status, git("status", "--porcelain"));
    }
}
