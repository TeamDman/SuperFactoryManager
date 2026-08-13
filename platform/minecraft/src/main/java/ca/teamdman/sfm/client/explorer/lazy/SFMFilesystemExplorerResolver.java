package ca.teamdman.sfm.client.explorer.lazy;

import ca.teamdman.sfm.client.explorer.SFMPath;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Read-only filesystem resolver restricted to explicitly granted roots.
 *
 * <p>Containment is checked both lexically and after resolving symlinks. A
 * symlink may be displayed, but it is never traversable when its real target
 * escapes the granted root.</p>
 */
public final class SFMFilesystemExplorerResolver implements SFMExplorerResolver {
    private record ExplicitRoot(Path lexical) {
    }

    private record SafePath(Path lexical, Path real, ExplicitRoot root) {
    }

    private final Executor executor;
    private final SFMExplorerIoCounter ioCounter;
    private final int maximumPageSize;
    private volatile List<ExplicitRoot> explicitRoots;
    private final AtomicLong generation = new AtomicLong(1);

    public SFMFilesystemExplorerResolver(
            Collection<Path> explicitRoots,
            Executor executor,
            SFMExplorerIoCounter ioCounter,
            int maximumPageSize
    ) {
        Objects.requireNonNull(explicitRoots, "explicitRoots");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.ioCounter = Objects.requireNonNull(ioCounter, "ioCounter");
        if (maximumPageSize <= 0) {
            throw new IllegalArgumentException("Maximum page size must be positive");
        }
        this.maximumPageSize = maximumPageSize;
        ArrayList<ExplicitRoot> normalized = new ArrayList<>();
        for (Path root : explicitRoots) {
            Objects.requireNonNull(root, "explicitRoot");
            Path lexical = root.toAbsolutePath().normalize();
            normalized.add(new ExplicitRoot(lexical));
        }
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("At least one explicit filesystem root is required");
        }
        normalized.sort(Comparator.comparing(root -> root.lexical().toString()));
        this.explicitRoots = List.copyOf(normalized);
    }

    @Override
    public String scheme() {
        return "file";
    }

    @Override
    public long generation() {
        return generation.get();
    }

    /** Invalidates already captured resolver requests without changing authority. */
    public long invalidate() {
        return generation.incrementAndGet();
    }

    public List<SFMPath> explicitRoots() {
        return explicitRoots.stream().map(root -> SFMPath.fromNative(root.lexical())).toList();
    }

    /**
     * Grants one lexical root without touching the filesystem.
     *
     * <p>This method is safe to call from an action on the client thread. The
     * first asynchronous describe/children request resolves real paths and
     * enforces symlink containment before any data is published.</p>
     */
    public synchronized boolean authorizeRoot(Path root) {
        Objects.requireNonNull(root, "root");
        Path lexical = root.toAbsolutePath().normalize();
        if (explicitRoots.stream().anyMatch(candidate -> candidate.lexical().equals(lexical))) {
            return false;
        }
        ArrayList<ExplicitRoot> next = new ArrayList<>(explicitRoots);
        next.add(new ExplicitRoot(lexical));
        next.sort(Comparator.comparing(candidate -> candidate.lexical().toString()));
        explicitRoots = List.copyOf(next);
        generation.incrementAndGet();
        return true;
    }

    /**
     * Validates an editor-supplied location without granting any new authority.
     * This is intentionally stricter than {@link #authorizeRoot(Path)}: typing
     * a path may consume an existing grant, but cannot create one.
     */
    public Optional<String> locationIncompatibility(SFMPath path) {
        try {
            safe(Objects.requireNonNull(path, "path"));
            return Optional.empty();
        } catch (IOException | SecurityException | IllegalArgumentException failure) {
            String detail = failure.getMessage() == null
                    ? failure.getClass().getSimpleName()
                    : failure.getMessage();
            return Optional.of(detail);
        }
    }

    @Override
    public CompletableFuture<SFMExplorerEntry> describe(
            SFMPath path,
            SFMExplorerCancellationToken cancellation
    ) {
        requireScheme(path);
        Objects.requireNonNull(cancellation, "cancellation");
        return CompletableFuture.supplyAsync(() -> {
            cancellation.throwIfCancelled();
            try {
                SFMExplorerEntry entry = describeNative(safe(path), path, List.of());
                cancellation.throwIfCancelled();
                return entry;
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<ChildPage> resolveChildren(ChildRequest request) {
        requireScheme(request.parent());
        int offset = parseContinuation(request.continuation());
        int pageSize = Math.min(request.pageSize(), maximumPageSize);
        return CompletableFuture.supplyAsync(() -> {
            request.cancellation().throwIfCancelled();
            assertGeneration(request.expectedResolverGeneration());
            try {
                SafePath parent = safe(request.parent());
                if (!Files.isDirectory(parent.real())) {
                    throw new IllegalArgumentException("Explorer path is not a directory: " + request.parent());
                }
                ioCounter.directoryEnumerated(request.parent());
                ArrayList<SFMExplorerEntry> entries = new ArrayList<>();
                boolean more = false;
                int observed = 0;
                int index = 0;
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(parent.lexical())) {
                    for (Path child : stream) {
                        request.cancellation().throwIfCancelled();
                        SFMPath childPath = SFMPath.fromNative(child);
                        ioCounter.entryObserved(childPath);
                        observed++;
                        if (index++ < offset) continue;
                        if (entries.size() >= pageSize) {
                            more = true;
                            break;
                        }
                        try {
                            entries.add(describeNative(safe(childPath), childPath, List.of()));
                        } catch (SecurityException escaped) {
                            entries.add(escapedSymlinkEntry(child, childPath, escaped.getMessage()));
                        }
                    }
                }
                entries.sort(Comparator.comparing(entry -> entry.path().canonical()));
                request.cancellation().throwIfCancelled();
                assertGeneration(request.expectedResolverGeneration());
                Optional<String> continuation = more
                        ? Optional.of("offset-" + (offset + entries.size()))
                        : Optional.empty();
                return new ChildPage(
                        request.parent(),
                        entries,
                        continuation,
                        request.expectedResolverGeneration(),
                        List.of(),
                        observed
                );
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }, executor);
    }

    private SFMExplorerEntry describeNative(
            SafePath safe,
            SFMPath logicalPath,
            List<String> diagnostics
    ) throws IOException {
        ioCounter.metadataRead(logicalPath);
        BasicFileAttributes noFollow = Files.readAttributes(
                safe.lexical(),
                BasicFileAttributes.class,
                LinkOption.NOFOLLOW_LINKS
        );
        boolean directory = noFollow.isDirectory()
                || noFollow.isSymbolicLink() && Files.isDirectory(safe.real());
        String label = Optional.ofNullable(safe.lexical().getFileName())
                .map(Path::toString)
                .orElse(logicalPath.canonical());
        TreeMap<String, SFMExplorerEntry.SortKey> keys = new TreeMap<>();
        keys.put(SFMExplorerEntry.SORT_NAME, SFMExplorerEntry.SortKey.available(label));
        String extension = logicalPath.extension();
        keys.put(
                SFMExplorerEntry.SORT_EXTENSION,
                extension.isEmpty()
                        ? SFMExplorerEntry.SortKey.unavailable("path has no extension")
                        : SFMExplorerEntry.SortKey.available(extension)
        );
        keys.put(
                SFMExplorerEntry.SORT_ICON,
                SFMExplorerEntry.SortKey.available(directory ? "folder" : iconFor(extension))
        );
        return new SFMExplorerEntry(logicalPath, label, directory, keys, diagnostics);
    }

    private SFMExplorerEntry escapedSymlinkEntry(Path child, SFMPath path, String diagnostic) {
        ioCounter.metadataRead(path);
        String label = Optional.ofNullable(child.getFileName()).map(Path::toString).orElse(path.canonical());
        TreeMap<String, SFMExplorerEntry.SortKey> keys = new TreeMap<>();
        keys.put(SFMExplorerEntry.SORT_NAME, SFMExplorerEntry.SortKey.available(label));
        String extension = path.extension();
        keys.put(
                SFMExplorerEntry.SORT_EXTENSION,
                extension.isEmpty()
                        ? SFMExplorerEntry.SortKey.unavailable("path has no extension")
                        : SFMExplorerEntry.SortKey.available(extension)
        );
        keys.put(SFMExplorerEntry.SORT_ICON, SFMExplorerEntry.SortKey.available("blocked-link"));
        return new SFMExplorerEntry(path, label, false, keys, List.of(diagnostic));
    }

    private SafePath safe(SFMPath path) throws IOException {
        requireScheme(path);
        Path lexical = path.toNativePath().toAbsolutePath().normalize();
        List<ExplicitRoot> roots = explicitRoots;
        ExplicitRoot root = roots.stream()
                .filter(candidate -> lexical.startsWith(candidate.lexical()))
                .max(Comparator.comparingInt(candidate -> candidate.lexical().getNameCount()))
                .orElseThrow(() -> rejected(path, "path is outside every explicit explorer root"));
        Path real = lexical.toRealPath();
        Path rootReal = root.lexical().toRealPath();
        if (!real.startsWith(rootReal)) {
            throw rejected(path, "path escapes its explicit explorer root through a symbolic link");
        }
        return new SafePath(lexical, real, root);
    }

    private SecurityException rejected(SFMPath path, String reason) {
        ioCounter.containmentRejected(path);
        return new SecurityException(reason + ": " + path.canonical());
    }

    private void assertGeneration(long expected) {
        long actual = generation();
        if (expected != actual) throw new StaleGenerationException(expected, actual);
    }

    private static String iconFor(String extension) {
        return extension.isEmpty() ? "file" : "file-" + extension.toLowerCase(java.util.Locale.ROOT);
    }

    private static int parseContinuation(Optional<String> continuation) {
        if (continuation.isEmpty()) return 0;
        String value = continuation.orElseThrow();
        if (!value.startsWith("offset-") || value.length() == "offset-".length()) {
            throw new IllegalArgumentException("Unsupported filesystem continuation token: " + value);
        }
        try {
            int offset = Integer.parseInt(value.substring("offset-".length()));
            if (offset <= 0) throw new NumberFormatException();
            return offset;
        } catch (NumberFormatException ignored) {
            throw new IllegalArgumentException("Invalid filesystem continuation token: " + value);
        }
    }

    private static void requireScheme(SFMPath path) {
        Objects.requireNonNull(path, "path");
        if (!path.scheme().equals("file")) {
            throw new IllegalArgumentException("Filesystem resolver only accepts file paths");
        }
    }
}
