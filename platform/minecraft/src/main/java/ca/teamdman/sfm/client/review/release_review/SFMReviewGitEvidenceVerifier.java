package ca.teamdman.sfm.client.review.release_review;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** Bounded exact local Git reads. No fetch, checkout, index mutation or shell command. */
public final class SFMReviewGitEvidenceVerifier {
    private SFMReviewGitEvidenceVerifier() { }
    /** A storage witness, not a replacement for the captured document's identity. */
    public record Recognized(SFMReviewEvidenceTable.Observed original,
                             SFMReviewEvidenceTable.GitReference storage, String storagePath) { }

    /**
     * Prove that captured bytes exist at an explicitly pinned commit/path. Callers
     * must resolve moving aliases once before invoking this method for a batch.
     * A different path is allowed (e.g. a later rename), but never different bytes.
     * This does not mutate the ledger or authorize dropping embedded content.
     */
    public static Recognized recognize(SFMReviewEvidenceTable.Document original,
                                       String repositoryId, String commit, String storagePath,
                                       Path authorizedRepositoryRoot) throws Exception {
        return recognize(original, repositoryId, commit, storagePath, authorizedRepositoryRoot, Duration.ofSeconds(10));
    }
    static String pinHead(Path root, Duration timeout) throws Exception {
        validateTimeout(timeout);
        String commit = new String(read(root.toRealPath(), List.of("rev-parse", "--verify", "HEAD^{commit}"),
                1024, System.nanoTime() + timeout.toNanos()), StandardCharsets.US_ASCII).trim();
        new SFMReviewEvidenceTable.GitReference("pin", commit, "0".repeat(40));
        return commit;
    }
    static Recognized recognize(SFMReviewEvidenceTable.Document original,
                                 String repositoryId, String commit, String storagePath,
                                 Path authorizedRepositoryRoot, Duration timeout) throws Exception {
        validateTimeout(timeout);
        // Validate the pinned address before starting any process. GitReference
        // deliberately rejects HEAD, branch names and abbreviated object IDs.
        new SFMReviewEvidenceTable.GitReference(repositoryId, commit, "0".repeat(40));
        if (storagePath == null || storagePath.isBlank() || storagePath.startsWith("/")
                || storagePath.indexOf('\\') >= 0 || storagePath.indexOf(':') >= 0
                || java.util.Arrays.stream(storagePath.split("/", -1))
                    .anyMatch(part -> part.isEmpty() || part.equals(".") || part.equals("..")))
            throw new IllegalArgumentException("Git storage path must be repository-relative");
        Path root = authorizedRepositoryRoot.toRealPath();
        long deadline = System.nanoTime() + timeout.toNanos();
        String blob = new String(read(root, List.of("rev-parse", "--verify", "--end-of-options",
                commit + ":" + storagePath), 1024, deadline), StandardCharsets.US_ASCII).trim();
        var storage = new SFMReviewEvidenceTable.GitReference(repositoryId, commit, blob);
        var storageDocument = new SFMReviewEvidenceTable.Document(original.revisionId(), storagePath,
                original.sha256(), java.util.Optional.of(storage));
        var verified = verifyAtDeadline(storageDocument, root, deadline);
        return new Recognized(new SFMReviewEvidenceTable.Observed(original, verified.text()), storage, storagePath);
    }
    public static SFMReviewEvidenceTable.Observed verify(SFMReviewEvidenceTable.Document document,
                                                        Path authorizedRepositoryRoot) throws Exception {
        return verify(document, authorizedRepositoryRoot, Duration.ofSeconds(10));
    }
    static SFMReviewEvidenceTable.Observed verify(SFMReviewEvidenceTable.Document document,
                                                 Path authorizedRepositoryRoot, Duration timeout) throws Exception {
        validateTimeout(timeout);
        Path root = authorizedRepositoryRoot.toRealPath();
        long deadline = System.nanoTime() + timeout.toNanos();
        return verifyAtDeadline(document, root, deadline);
    }
    private static void validateTimeout(Duration timeout) {
        if (timeout.isZero() || timeout.isNegative() || timeout.compareTo(Duration.ofSeconds(60)) > 0)
            throw new IllegalArgumentException("Git evidence timeout must be positive and at most 60 seconds");
    }
    private static SFMReviewEvidenceTable.Observed verifyAtDeadline(
            SFMReviewEvidenceTable.Document document, Path root, long deadline) throws Exception {
        var reference = document.git().orElseThrow(() -> new IllegalArgumentException("Document has no Git reference"));
        String address = reference.commit() + ":" + document.path();
        String blob = new String(read(root, List.of("rev-parse", "--verify", "--end-of-options", address), 1024, deadline),
                StandardCharsets.US_ASCII).trim();
        if (!reference.blob().equals(blob)) throw new IOException("Git commit/path does not identify the recorded evidence blob");
        byte[] bytes = read(root, List.of("cat-file", "blob", blob), 16 * 1024 * 1024, deadline);
        String text = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
        var observed = new SFMReviewEvidenceTable.Observed(document, text);
        var digest = java.security.MessageDigest.getInstance("SHA-1");
        digest.update(("blob " + bytes.length + "\0").getBytes(StandardCharsets.US_ASCII));
        if (!java.util.HexFormat.of().formatHex(digest.digest(bytes)).equals(blob))
            throw new IOException("Git object bytes do not match their blob identity");
        return observed;
    }
    private static byte[] read(Path root, List<String> args, int limit, long deadline) throws Exception {
        long remaining = deadline - System.nanoTime();
        if (remaining <= 0) throw new IOException("Git evidence verification exceeded its time limit");
        var command = new ArrayList<>(List.of("git", "--no-replace-objects", "-c", "protocol.allow=never", "-C", root.toString()));
        command.addAll(args);
        var builder = new ProcessBuilder(command);
        builder.environment().put("GIT_NO_LAZY_FETCH", "1");
        builder.environment().put("GIT_TERMINAL_PROMPT", "0");
        builder.environment().put("GIT_OPTIONAL_LOCKS", "0");
        // Environment overrides must not redirect these explicit-root reads to another
        // object database or index. The caller supplies the authorized repository root.
        for (String key : List.of("GIT_DIR", "GIT_WORK_TREE", "GIT_INDEX_FILE", "GIT_OBJECT_DIRECTORY", "GIT_ALTERNATE_OBJECT_DIRECTORIES"))
            builder.environment().remove(key);
        Process process = builder.start();
        process.getOutputStream().close();
        var stdout = CompletableFuture.supplyAsync(() -> collect(process.getInputStream(), limit));
        var stderr = CompletableFuture.supplyAsync(() -> collect(process.getErrorStream(), 64 * 1024));
        try {
            if (!process.waitFor(remaining, TimeUnit.NANOSECONDS)) throw new IOException("Git evidence verification timed out");
            byte[] output = stdout.get(1, TimeUnit.SECONDS);
            byte[] errors = stderr.get(1, TimeUnit.SECONDS);
            if (process.exitValue() != 0) throw new IOException("Git evidence unavailable: " +
                    new String(errors, 0, Math.min(errors.length, 2048), StandardCharsets.UTF_8));
            return output;
        } finally {
            if (process.isAlive()) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
                process.waitFor(2, TimeUnit.SECONDS);
            }
            process.getInputStream().close(); process.getErrorStream().close();
            stdout.cancel(true); stderr.cancel(true);
        }
    }
    private static byte[] collect(InputStream stream, int limit) {
        try (stream; var bytes = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            for (int count; (count = stream.read(buffer)) >= 0;) {
                if (bytes.size() > limit - count) throw new IOException("Git evidence output exceeds its byte limit");
                bytes.write(buffer, 0, count);
            }
            return bytes.toByteArray();
        } catch (IOException failure) { throw new java.io.UncheckedIOException(failure); }
    }
}
