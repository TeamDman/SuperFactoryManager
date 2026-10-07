package ca.teamdman.sfm.client.review.release_review;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** New-file publication only. This never replaces, retargets or saves the source review. */
public final class SFMReviewEvidenceExportFile {
    private SFMReviewEvidenceExportFile() { }
    public record Published(Path path, String sha256, long bytes, java.util.List<String> diagnostics) {
        public Published { diagnostics = java.util.List.copyOf(diagnostics); }
    }
    public static Published publish(Path source, Path destination, SFMReviewEvidenceExport.Prepared prepared) throws IOException {
        source = source.toAbsolutePath().normalize();
        destination = destination.toAbsolutePath().normalize();
        if (source.equals(destination)) throw new IOException("Export requires a new destination, not the source review");
        Path parent = destination.getParent();
        if (parent == null || !Files.isDirectory(parent)) throw new IOException("Export parent directory does not exist");
        if (Files.exists(destination, java.nio.file.LinkOption.NOFOLLOW_LINKS))
            throw new java.nio.file.FileAlreadyExistsException(destination.toString());
        byte[] output = prepared.output().getBytes(StandardCharsets.UTF_8);
        if (!SFMReleaseReviewLedgerV3Codec.parse(prepared.output()).equals(prepared.ledger())
                || output.length != prepared.outputBytes()) throw new IOException("Export preparation is inconsistent");
        checkAuthority(source, prepared.originalSha256());
        Path temporary = Files.createTempFile(parent, ".sfm-review-export-", ".tmp");
        boolean published = false;
        var diagnostics = new java.util.ArrayList<String>();
        try {
            try (var channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                ByteBuffer buffer = ByteBuffer.wrap(output);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            checkAuthority(source, prepared.originalSha256());
            // An atomic hard-link creation refuses existing targets, including a target
            // created after the check above. ATOMIC_MOVE can replace existing files on
            // some providers, so it is not a valid no-overwrite primitive here.
            try { Files.createLink(destination, temporary); }
            catch (UnsupportedOperationException unsupported) {
                throw new IOException("This filesystem cannot atomically publish a new review without overwrite", unsupported);
            }
            published = true;
        } finally {
            try { Files.deleteIfExists(temporary); }
            catch (IOException cleanup) {
                if (!published) throw cleanup;
                diagnostics.add("Export saved; temporary hard link could not be removed: " + temporary);
            }
        }
        return new Published(destination, SFMReleaseReviewKernel.sha256(output), output.length, diagnostics);
    }
    private static void checkAuthority(Path source, String expected) throws IOException {
        try (var input = Files.newInputStream(source)) {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            long bytes = 0;
            for (int count; (count = input.read(buffer)) >= 0;) {
                bytes += count;
                if (bytes > 256L * 1024 * 1024) throw new IOException("Review authority exceeds export read limit");
                digest.update(buffer, 0, count);
            }
            if (!java.util.HexFormat.of().formatHex(digest.digest()).equals(expected))
                throw new IOException("Source review changed after export preview; prepare it again");
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
