package ca.teamdman.sfm.client.review.session;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Canonical v2 store with atomic writes, last-valid recovery, and strict v1 import. */
public final class SFMReviewSessionStore {
    public record LoadResult(
            Optional<SFMReviewSessionV2> session,
            boolean recoveredLastValid,
            boolean migratedV1,
            List<String> diagnostics
    ) {
        public LoadResult {
            diagnostics = List.copyOf(diagnostics);
        }
    }

    private record ReadResult(Optional<SFMReviewSessionV2> session, boolean migratedV1) {
    }

    private final Path path;
    private final Path lastValidPath;

    public SFMReviewSessionStore(Path path) {
        this.path = path;
        this.lastValidPath = path.resolveSibling(path.getFileName() + ".last-valid");
    }

    public static SFMReviewSessionStore forSessionId(String sessionId) {
        String localAppData = System.getenv("LOCALAPPDATA");
        Path root = localAppData == null || localAppData.isBlank()
                ? Path.of(System.getProperty("user.home"), ".local", "share")
                : Path.of(localAppData);
        String fileName = SFMReviewSessionV1Kernel.sha256(sessionId.getBytes(StandardCharsets.UTF_8)) + ".json";
        return new SFMReviewSessionStore(root.resolve("teamdman").resolve("SFM")
                .resolve("review-sessions").resolve(fileName));
    }

    public LoadResult load() {
        List<String> diagnostics = new ArrayList<>();
        ReadResult active = read(path, diagnostics, "active");
        if (active.session().isPresent()) {
            return new LoadResult(active.session(), false, active.migratedV1(), diagnostics);
        }
        ReadResult recovered = read(lastValidPath, diagnostics, "last-valid");
        if (recovered.session().isPresent()) diagnostics.add("Recovered the last valid review session");
        return new LoadResult(
                recovered.session(),
                recovered.session().isPresent(),
                recovered.migratedV1(),
                diagnostics
        );
    }

    public void save(SFMReviewSessionV2 session) throws IOException {
        String canonical = SFMReviewSessionV2Codec.write(session);
        SFMReviewSessionV2 parsed = SFMReviewSessionV2Codec.parse(canonical);
        SFMReviewSessionV2Kernel.evaluateAll(parsed);
        Files.createDirectories(path.getParent());
        replaceAtomically(path, canonical);
        replaceAtomically(lastValidPath, canonical);
    }

    public Path path() {
        return path;
    }

    public Path lastValidPath() {
        return lastValidPath;
    }

    private static ReadResult read(Path path, List<String> diagnostics, String label) {
        if (!Files.isRegularFile(path)) {
            diagnostics.add("No " + label + " session at " + path);
            return new ReadResult(Optional.empty(), false);
        }
        try {
            String json = Files.readString(path, StandardCharsets.UTF_8);
            boolean migrated = json.contains("\"schema\": \"" + SFMReviewSessionV1.SCHEMA + "\"");
            SFMReviewSessionV2 session = SFMReviewSessionV2Codec.parseOrMigrate(json);
            SFMReviewSessionV2Kernel.evaluateAll(session);
            if (migrated) diagnostics.add("Imported " + label + " v1 session into the v2 model");
            return new ReadResult(Optional.of(session), migrated);
        } catch (RuntimeException | IOException exception) {
            diagnostics.add("Invalid " + label + " session at " + path + ": " + exception.getMessage());
            return new ReadResult(Optional.empty(), false);
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
