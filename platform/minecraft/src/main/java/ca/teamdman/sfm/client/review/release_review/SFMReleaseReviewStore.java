package ca.teamdman.sfm.client.review.release_review;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Explicit-path, optimistic, atomically replaced release-review store. */
public final class SFMReleaseReviewStore implements AutoCloseable {
    public enum Access { READ_ONLY, WRITABLE }

    public record LoadResult(
            Optional<SFMReleaseReviewV1> document,
            Optional<String> openedContentHash,
            boolean recoveredMachineLocalCopy,
            List<String> diagnostics
    ) {
        public LoadResult {
            diagnostics = List.copyOf(diagnostics);
        }
    }

    public record SaveResult(String contentHash, long byteCount, List<String> diagnostics) {
        public SaveResult {
            contentHash = java.util.Objects.requireNonNull(contentHash, "contentHash");
            diagnostics = List.copyOf(diagnostics);
        }

        public SaveResult(String contentHash, long byteCount) {
            this(contentHash, byteCount, List.of());
        }
    }

    /** Exact rebuildable machine-local paths associated with one authoritative review file. */
    public record MachineLocalState(Path recovery, Path writerLease) {}

    public static final class ExternalEditConflict extends IOException {
        public ExternalEditConflict(Path path, String expected, String actual) {
            super("Release-review file changed outside this writer: " + path
                    + " (opened " + expected + ", current " + actual + ")");
        }
    }

    private static final ConcurrentHashMap<Path, UUID> WRITERS = new ConcurrentHashMap<>();

    private final Path path;
    private final Path recoveryPath;
    private final Path writerLockPath;
    private final Access access;
    private final UUID owner = UUID.randomUUID();
    private FileChannel writerLockChannel;
    private FileLock writerLock;
    private boolean closed;

    private SFMReleaseReviewStore(Path path, Access access) throws IOException {
        this.path = path.toAbsolutePath().normalize();
        this.access = access;
        this.recoveryPath = recoveryRoot().resolve(
                SFMReleaseReviewKernel.sha256(this.path.toString().getBytes(StandardCharsets.UTF_8)) + ".json");
        this.writerLockPath = recoveryRoot().resolve(
                SFMReleaseReviewKernel.sha256(this.path.toString().getBytes(StandardCharsets.UTF_8)) + ".writer.lock");
        if (access == Access.WRITABLE) {
            UUID previous = WRITERS.putIfAbsent(this.path, owner);
            if (previous != null) {
                throw new IOException("Release-review file already has a writable owner: " + this.path);
            }
            try {
                Files.createDirectories(writerLockPath.getParent());
                writerLockChannel = FileChannel.open(
                        writerLockPath,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.WRITE
                );
                try {
                    writerLock = writerLockChannel.tryLock();
                } catch (OverlappingFileLockException ignored) {
                    writerLock = null;
                }
                if (writerLock == null) {
                    throw new IOException("Release-review file already has a writable owner in another process: "
                            + this.path);
                }
            } catch (IOException | RuntimeException failure) {
                closeWriterLock();
                WRITERS.remove(this.path, owner);
                if (failure instanceof IOException io) throw io;
                throw failure;
            }
        }
    }

    public static SFMReleaseReviewStore open(Path path, Access access) throws IOException {
        return new SFMReleaseReviewStore(path, access);
    }

    public static MachineLocalState machineLocalState(Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        String key = SFMReleaseReviewKernel.sha256(normalized.toString().getBytes(StandardCharsets.UTF_8));
        Path root = recoveryRoot();
        return new MachineLocalState(root.resolve(key + ".json"), root.resolve(key + ".writer.lock"));
    }

    public LoadResult load() {
        requireOpen();
        List<String> diagnostics = new ArrayList<>();
        Optional<SFMReleaseReviewV1> active = read(path, diagnostics, "repository file");
        if (active.isPresent()) {
            return new LoadResult(active, contentHash(path), false, diagnostics);
        }
        Optional<SFMReleaseReviewV1> recovery = read(recoveryPath, diagnostics, "machine-local recovery");
        if (recovery.isPresent()) diagnostics.add("Recovered machine-local bytes; save explicitly to restore authority");
        return new LoadResult(recovery, Optional.empty(), recovery.isPresent(), diagnostics);
    }

    public SaveResult save(SFMReleaseReviewV1 document, Optional<String> expectedContentHash) throws IOException {
        requireOpen();
        if (access != Access.WRITABLE) throw new IOException("Release-review store is read-only: " + path);
        SFMReleaseReviewKernel.validate(document);
        String canonical = SFMReleaseReviewV1Codec.write(document);
        SFMReleaseReviewV1 reparsed = SFMReleaseReviewV1Codec.parse(canonical);
        SFMReleaseReviewKernel.validate(reparsed);

        Optional<String> current = contentHash(path);
        if (!current.equals(expectedContentHash)) {
            throw new ExternalEditConflict(path, expectedContentHash.orElse("<absent>"), current.orElse("<absent>"));
        }
        byte[] bytes = canonical.getBytes(StandardCharsets.UTF_8);
        replaceAtomically(path, bytes);

        List<String> diagnostics = new ArrayList<>();
        try {
            replaceAtomically(recoveryPath, bytes);
        } catch (IOException | RuntimeException failure) {
            diagnostics.add("Release-review authority was saved, but its machine-local recovery mirror was not"
                    + " code=review.recovery-mirror-write-failed"
                    + " path=" + recoveryPath
                    + " failure_type=" + failure.getClass().getSimpleName()
                    + " message=" + failureMessage(failure));
        }
        return new SaveResult(SFMReleaseReviewKernel.sha256(bytes), bytes.length, diagnostics);
    }

    public Path path() {
        return path;
    }

    public Path recoveryPath() {
        return recoveryPath;
    }

    public Path writerLockPath() {
        return writerLockPath;
    }

    public Access access() {
        return access;
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        if (access == Access.WRITABLE) {
            closeWriterLock();
            WRITERS.remove(path, owner);
        }
    }

    private void requireOpen() {
        if (closed) throw new IllegalStateException("Release-review store is closed");
    }

    private static Optional<SFMReleaseReviewV1> read(Path path, List<String> diagnostics, String label) {
        if (!Files.isRegularFile(path)) {
            diagnostics.add("No " + label + " at " + path);
            return Optional.empty();
        }
        try {
            SFMReleaseReviewV1 value = SFMReleaseReviewV1Codec.parse(Files.readString(path, StandardCharsets.UTF_8));
            SFMReleaseReviewKernel.validate(value);
            return Optional.of(value);
        } catch (IOException | RuntimeException exception) {
            diagnostics.add("Invalid " + label + " at " + path + ": " + exception.getMessage());
            return Optional.empty();
        }
    }

    private static Optional<String> contentHash(Path path) {
        if (!Files.isRegularFile(path)) return Optional.empty();
        try {
            return Optional.of(SFMReleaseReviewKernel.sha256(Files.readAllBytes(path)));
        } catch (IOException exception) {
            throw new IllegalStateException("Could not hash release-review file " + path, exception);
        }
    }

    private static void replaceAtomically(Path destination, byte[] bytes) throws IOException {
        Path parent = destination.getParent();
        if (parent != null) Files.createDirectories(parent);
        Path temporary = destination.resolveSibling(destination.getFileName() + ".tmp-" + UUID.randomUUID());
        Files.write(temporary, bytes);
        try {
            Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicMoveUnavailable) {
            Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static String failureMessage(Throwable failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? "<no message>" : message;
    }

    private static Path recoveryRoot() {
        String localAppData = System.getenv("LOCALAPPDATA");
        Path root = localAppData == null || localAppData.isBlank()
                ? Path.of(System.getProperty("user.home"), ".local", "share")
                : Path.of(localAppData);
        return root.resolve("teamdman").resolve("SFM").resolve("release-review-recovery");
    }

    private void closeWriterLock() {
        if (writerLock != null) {
            try {
                writerLock.release();
            } catch (IOException ignored) {
                // Closing the channel below is the final best-effort release.
            }
            writerLock = null;
        }
        if (writerLockChannel != null) {
            try {
                writerLockChannel.close();
            } catch (IOException ignored) {
                // close() cannot report recovery work to the caller.
            }
            writerLockChannel = null;
        }
    }
}
