package ca.teamdman.sfm.client.history.comparison;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.Optional;
import java.util.ArrayList;
import java.util.List;

/** Atomic persistence seam with last-valid recovery for one route-comparison session. */
public final class SFMRouteComparisonStore {
    public record LoadResult(
            Optional<SFMRouteComparisonSession> session,
            boolean recoveredLastValid,
            List<String> diagnostics
    ) {
        public LoadResult {
            Objects.requireNonNull(session, "session");
            diagnostics = List.copyOf(diagnostics);
        }
    }

    private final Path path;
    private final Path lastValidPath;

    public SFMRouteComparisonStore(Path path) {
        this.path = Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
        this.lastValidPath = this.path.resolveSibling(this.path.getFileName() + ".last-valid");
    }

    public Optional<SFMRouteComparisonSession> load() throws IOException {
        return loadResult().session();
    }

    public LoadResult loadResult() throws IOException {
        ArrayList<String> diagnostics = new ArrayList<>();
        Optional<SFMRouteComparisonSession> active = read(path, "active", diagnostics);
        if (active.isPresent()) return new LoadResult(active, false, diagnostics);
        Optional<SFMRouteComparisonSession> recovered = read(lastValidPath, "last-valid", diagnostics);
        if (recovered.isPresent()) diagnostics.add("Recovered the last valid route comparison");
        return new LoadResult(recovered, recovered.isPresent(), diagnostics);
    }

    public void save(SFMRouteComparisonSession session) throws IOException {
        Objects.requireNonNull(session, "session");
        writeAtomically(path, session);
        writeAtomically(lastValidPath, session);
    }

    private static void writeAtomically(Path destination, SFMRouteComparisonSession session) throws IOException {
        Path parent = destination.getParent();
        if (parent == null) throw new IOException("Route-comparison store path has no parent: " + destination);
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, destination.getFileName() + ".", ".tmp");
        try {
            Files.writeString(temporary, SFMRouteComparisonCodec.encode(session), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, destination,
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public Path path() {
        return path;
    }

    public Path lastValidPath() {
        return lastValidPath;
    }

    private static Optional<SFMRouteComparisonSession> read(
            Path candidate,
            String label,
            List<String> diagnostics
    ) {
        if (!Files.isRegularFile(candidate)) {
            diagnostics.add("No " + label + " route comparison at " + candidate);
            return Optional.empty();
        }
        try {
            return Optional.of(SFMRouteComparisonCodec.decode(
                    Files.readString(candidate, StandardCharsets.UTF_8)));
        } catch (IOException | RuntimeException failure) {
            diagnostics.add("Invalid " + label + " route comparison at " + candidate
                    + ": " + (failure.getMessage() == null
                    ? failure.getClass().getSimpleName()
                    : failure.getMessage()));
            return Optional.empty();
        }
    }
}
