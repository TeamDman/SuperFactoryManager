package ca.teamdman.sfm.client.review.session;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Atomic application-data persistence with an independently parseable last-valid recovery file. */
public final class SFMReviewSessionV1Store {
    public record LoadResult(Optional<SFMReviewSessionV1> session, boolean recoveredLastValid,
                             List<String> diagnostics) {
        public LoadResult { diagnostics = List.copyOf(diagnostics); }
    }

    private final Path path;
    private final Path lastValidPath;

    public SFMReviewSessionV1Store(Path path) {
        this.path = path;
        this.lastValidPath = path.resolveSibling(path.getFileName() + ".last-valid");
    }

    public static SFMReviewSessionV1Store forSessionId(String sessionId) {
        String localAppData = System.getenv("LOCALAPPDATA");
        Path root = localAppData == null || localAppData.isBlank()
                ? Path.of(System.getProperty("user.home"), ".local", "share") : Path.of(localAppData);
        String fileName = SFMReviewSessionV1Kernel.sha256(sessionId.getBytes(StandardCharsets.UTF_8)) + ".json";
        return new SFMReviewSessionV1Store(root.resolve("teamdman").resolve("SFM")
                .resolve("review-sessions").resolve(fileName));
    }

    public LoadResult load() {
        List<String> diagnostics = new ArrayList<>();
        Optional<SFMReviewSessionV1> active = read(path, diagnostics, "active");
        if (active.isPresent()) return new LoadResult(active, false, diagnostics);
        Optional<SFMReviewSessionV1> recovered = read(lastValidPath, diagnostics, "last-valid");
        if (recovered.isPresent()) diagnostics.add("Recovered the last valid review session");
        return new LoadResult(recovered, recovered.isPresent(), diagnostics);
    }

    public void save(SFMReviewSessionV1 session) throws IOException {
        String canonical = SFMReviewSessionV1Codec.write(session);
        SFMReviewSessionV1 parsed = SFMReviewSessionV1Codec.parse(canonical);
        SFMReviewSessionV1Kernel.evaluateAll(parsed);
        Files.createDirectories(path.getParent());
        replaceAtomically(path, canonical);
        replaceAtomically(lastValidPath, canonical);
    }

    public Path path() { return path; }
    public Path lastValidPath() { return lastValidPath; }

    private static Optional<SFMReviewSessionV1> read(Path path, List<String> diagnostics, String label) {
        if (!Files.isRegularFile(path)) {
            diagnostics.add("No " + label + " session at " + path);
            return Optional.empty();
        }
        try {
            SFMReviewSessionV1 session = SFMReviewSessionV1Codec.parse(Files.readString(path, StandardCharsets.UTF_8));
            SFMReviewSessionV1Kernel.evaluateAll(session);
            return Optional.of(session);
        } catch (RuntimeException | IOException exception) {
            diagnostics.add("Invalid " + label + " session at " + path + ": " + exception.getMessage());
            return Optional.empty();
        }
    }

    private static void replaceAtomically(Path destination, String text) throws IOException {
        Path temporary = destination.resolveSibling(destination.getFileName() + ".tmp");
        Files.writeString(temporary, text, StandardCharsets.UTF_8);
        try {
            Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicMoveUnavailable) {
            Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
