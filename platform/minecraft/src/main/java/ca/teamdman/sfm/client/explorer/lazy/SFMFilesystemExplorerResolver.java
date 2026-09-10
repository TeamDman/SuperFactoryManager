package ca.teamdman.sfm.client.explorer.lazy;

import ca.teamdman.sfm.client.explorer.SFMPath;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
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

    @Override
    public boolean supportsTextRead() {
        return true;
    }

    @Override
    public CompletableFuture<SFMResolverTextResult> readText(SFMResolverTextRequest request) {
        Objects.requireNonNull(request, "request");
        return CompletableFuture.supplyAsync(() -> readTextOnResolverExecutor(request), executor);
    }

    private SFMResolverTextResult readTextOnResolverExecutor(SFMResolverTextRequest request) {
        SFMResolverTextResult race = textReadRace(request);
        if (race != null) return race;
        long observedGeneration = generation();
        if (!request.path().scheme().equals(scheme())
                || !request.authorizedRoot().scheme().equals(scheme())) {
            return SFMResolverTextResult.failure(
                    request,
                    SFMResolverTextResult.Status.UNSUPPORTED_RESOLVER,
                    observedGeneration,
                    "Filesystem resolver only accepts file paths"
            );
        }

        Path rootLexical = request.authorizedRoot().toNativePath().toAbsolutePath().normalize();
        ExplicitRoot exactRoot = explicitRoots.stream()
                .filter(candidate -> candidate.lexical().equals(rootLexical))
                .findFirst()
                .orElse(null);
        if (exactRoot == null) {
            ioCounter.containmentRejected(request.path());
            return SFMResolverTextResult.failure(
                    request,
                    SFMResolverTextResult.Status.REMOVED_ROOT,
                    generation(),
                    "The supplied authorized root is not currently granted: "
                            + request.authorizedRoot().canonical()
            );
        }

        Path rootReal;
        try {
            rootReal = exactRoot.lexical().toRealPath();
        } catch (NoSuchFileException missing) {
            return SFMResolverTextResult.failure(
                    request,
                    SFMResolverTextResult.Status.REMOVED_ROOT,
                    generation(),
                    "The supplied authorized root no longer exists: "
                            + request.authorizedRoot().canonical()
            );
        } catch (IOException failure) {
            return ioError(request, "Could not resolve the authorized root", failure);
        }

        Path lexical = request.path().toNativePath().toAbsolutePath().normalize();
        if (!lexical.startsWith(exactRoot.lexical())) {
            ioCounter.containmentRejected(request.path());
            return SFMResolverTextResult.failure(
                    request,
                    SFMResolverTextResult.Status.UNAVAILABLE,
                    generation(),
                    "The requested path is outside the supplied authorized root"
            );
        }

        Path real;
        try {
            real = lexical.toRealPath();
        } catch (NoSuchFileException missing) {
            return SFMResolverTextResult.failure(
                    request,
                    SFMResolverTextResult.Status.UNAVAILABLE,
                    generation(),
                    "The requested path is unavailable: " + request.path().canonical()
            );
        } catch (IOException failure) {
            return ioError(request, "Could not resolve the requested path", failure);
        }
        if (!real.startsWith(rootReal)) {
            ioCounter.containmentRejected(request.path());
            return SFMResolverTextResult.failure(
                    request,
                    SFMResolverTextResult.Status.UNAVAILABLE,
                    generation(),
                    "The requested path escapes the supplied authorized root through a symbolic link"
            );
        }

        race = textReadRace(request);
        if (race != null) return race;

        BasicFileAttributes before;
        try {
            ioCounter.metadataRead(request.path());
            before = Files.readAttributes(real, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        } catch (NoSuchFileException missing) {
            return SFMResolverTextResult.failure(
                    request,
                    SFMResolverTextResult.Status.UNAVAILABLE,
                    generation(),
                    "The requested path disappeared before it could be read"
            );
        } catch (IOException failure) {
            return ioError(request, "Could not inspect the requested path", failure);
        }

        Optional<Instant> lastModified = Optional.of(before.lastModifiedTime().toInstant());
        if (before.isDirectory()) {
            return SFMResolverTextResult.failure(
                    request,
                    SFMResolverTextResult.Status.DIRECTORY,
                    generation(),
                    Optional.empty(),
                    OptionalLong.of(before.size()),
                    lastModified,
                    "Directories cannot be opened as text"
            );
        }
        if (!before.isRegularFile()) {
            return SFMResolverTextResult.failure(
                    request,
                    SFMResolverTextResult.Status.UNAVAILABLE,
                    generation(),
                    Optional.empty(),
                    OptionalLong.of(before.size()),
                    lastModified,
                    "The requested path is not a regular file"
            );
        }
        if (before.size() > request.maximumBytes()) {
            return SFMResolverTextResult.failure(
                    request,
                    SFMResolverTextResult.Status.OVERSIZED,
                    generation(),
                    Optional.empty(),
                    OptionalLong.of(before.size()),
                    lastModified,
                    "The file exceeds the " + request.maximumBytes() + " byte text-read limit"
            );
        }

        byte[] bytes;
        try {
            bytes = readAtMostMaximumPlusOne(real, request);
        } catch (RacedTextRead racedRead) {
            return racedRead.result();
        } catch (NoSuchFileException missing) {
            return SFMResolverTextResult.failure(
                    request,
                    SFMResolverTextResult.Status.UNAVAILABLE,
                    generation(),
                    "The requested path disappeared while it was being read"
            );
        } catch (IOException failure) {
            return ioError(request, "Could not read the requested path", failure);
        }
        race = textReadRace(request);
        if (race != null) return race;
        if (bytes.length > request.maximumBytes()) {
            return SFMResolverTextResult.failure(
                    request,
                    SFMResolverTextResult.Status.OVERSIZED,
                    generation(),
                    Optional.empty(),
                    OptionalLong.of(bytes.length),
                    lastModified,
                    "The file grew beyond the " + request.maximumBytes() + " byte text-read limit"
            );
        }

        String sha256 = sha256(bytes);
        BasicFileAttributes after;
        try {
            after = Files.readAttributes(real, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        } catch (NoSuchFileException missing) {
            return SFMResolverTextResult.failure(
                    request,
                    SFMResolverTextResult.Status.STALE_CONTENT,
                    generation(),
                    Optional.of(sha256),
                    OptionalLong.of(bytes.length),
                    lastModified,
                    "The requested path disappeared after its content was read"
            );
        } catch (IOException failure) {
            return ioError(request, "Could not verify the requested path after reading", failure);
        }
        if (!sameFileSnapshot(before, after)) {
            return SFMResolverTextResult.failure(
                    request,
                    SFMResolverTextResult.Status.STALE_CONTENT,
                    generation(),
                    Optional.of(sha256),
                    OptionalLong.of(bytes.length),
                    Optional.of(after.lastModifiedTime().toInstant()),
                    "The file changed while its content was being read"
            );
        }
        if (request.expectedSha256().isPresent()
                && !request.expectedSha256().orElseThrow().equals(sha256)) {
            return SFMResolverTextResult.failure(
                    request,
                    SFMResolverTextResult.Status.STALE_CONTENT,
                    generation(),
                    Optional.of(sha256),
                    OptionalLong.of(bytes.length),
                    lastModified,
                    "The file content no longer matches the expected SHA-256"
            );
        }

        if (containsNul(bytes)) {
            return SFMResolverTextResult.failure(
                    request,
                    SFMResolverTextResult.Status.BINARY,
                    generation(),
                    Optional.of(sha256),
                    OptionalLong.of(bytes.length),
                    lastModified,
                    "The file contains a NUL byte and is treated as binary"
            );
        }

        String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException failure) {
            return SFMResolverTextResult.failure(
                    request,
                    SFMResolverTextResult.Status.UNSUPPORTED_ENCODING,
                    generation(),
                    Optional.of(sha256),
                    OptionalLong.of(bytes.length),
                    lastModified,
                    "The file is not valid UTF-8"
            );
        }

        long publicationGeneration = generation();
        if (request.cancellation().isCancelled()) {
            return SFMResolverTextResult.failure(
                    request,
                    SFMResolverTextResult.Status.CANCELLED,
                    publicationGeneration,
                    "Resolver text request was cancelled before publication"
            );
        }
        if (request.expectedResolverGeneration() != publicationGeneration) {
            return SFMResolverTextResult.failure(
                    request,
                    SFMResolverTextResult.Status.STALE_GENERATION,
                    publicationGeneration,
                    "Resolver generation changed from "
                            + request.expectedResolverGeneration() + " to " + publicationGeneration
                            + " before text publication"
            );
        }
        return SFMResolverTextResult.ready(
                request,
                publicationGeneration,
                text,
                sha256,
                bytes.length,
                lastModified,
                detectLineEndings(text)
        );
    }

    private byte[] readAtMostMaximumPlusOne(
            Path real,
            SFMResolverTextRequest request
    ) throws IOException {
        int limit = request.maximumBytes() + 1;
        ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(limit, 8192));
        byte[] buffer = new byte[Math.min(Math.max(limit, 1), 8192)];
        try (SeekableByteChannel channel = Files.newByteChannel(
                real,
                StandardOpenOption.READ,
                LinkOption.NOFOLLOW_LINKS
        ); InputStream input = Channels.newInputStream(channel)) {
            while (output.size() < limit) {
                SFMResolverTextResult race = textReadRace(request);
                if (race != null) throw new RacedTextRead(race);
                int count = input.read(buffer, 0, Math.min(buffer.length, limit - output.size()));
                if (count < 0) break;
                output.write(buffer, 0, count);
            }
        }
        return output.toByteArray();
    }

    private SFMResolverTextResult textReadRace(SFMResolverTextRequest request) {
        long actualGeneration = generation();
        if (request.cancellation().isCancelled()) {
            return SFMResolverTextResult.failure(
                    request,
                    SFMResolverTextResult.Status.CANCELLED,
                    actualGeneration,
                    "Resolver text request was cancelled"
            );
        }
        if (request.expectedResolverGeneration() != actualGeneration) {
            return SFMResolverTextResult.failure(
                    request,
                    SFMResolverTextResult.Status.STALE_GENERATION,
                    actualGeneration,
                    "Resolver generation changed from "
                            + request.expectedResolverGeneration() + " to " + actualGeneration
            );
        }
        return null;
    }

    private SFMResolverTextResult ioError(
            SFMResolverTextRequest request,
            String operation,
            IOException failure
    ) {
        String detail = failure.getMessage() == null
                ? failure.getClass().getSimpleName()
                : failure.getMessage();
        return SFMResolverTextResult.failure(
                request,
                SFMResolverTextResult.Status.IO_ERROR,
                generation(),
                operation + ": " + detail
        );
    }

    private static boolean sameFileSnapshot(BasicFileAttributes before, BasicFileAttributes after) {
        return before.size() == after.size()
                && before.lastModifiedTime().equals(after.lastModifiedTime())
                && Objects.equals(before.fileKey(), after.fileKey());
    }

    private static boolean containsNul(byte[] bytes) {
        for (byte value : bytes) {
            if (value == 0) return true;
        }
        return false;
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("The runtime does not provide SHA-256", impossible);
        }
    }

    private static SFMResolverTextResult.LineEndingKind detectLineEndings(String text) {
        boolean lf = false;
        boolean crlf = false;
        boolean cr = false;
        for (int index = 0; index < text.length(); index++) {
            char value = text.charAt(index);
            if (value == '\r') {
                if (index + 1 < text.length() && text.charAt(index + 1) == '\n') {
                    crlf = true;
                    index++;
                } else {
                    cr = true;
                }
            } else if (value == '\n') {
                lf = true;
            }
        }
        int kinds = (lf ? 1 : 0) + (crlf ? 1 : 0) + (cr ? 1 : 0);
        if (kinds == 0) return SFMResolverTextResult.LineEndingKind.NONE;
        if (kinds > 1) return SFMResolverTextResult.LineEndingKind.MIXED;
        if (lf) return SFMResolverTextResult.LineEndingKind.LF;
        if (crlf) return SFMResolverTextResult.LineEndingKind.CRLF;
        return SFMResolverTextResult.LineEndingKind.CR;
    }

    /** Carries a typed race result out of the bounded InputStream loop. */
    private static final class RacedTextRead extends RuntimeException {
        private final SFMResolverTextResult result;

        private RacedTextRead(SFMResolverTextResult result) {
            super(result.diagnostic().orElse("Resolver text read became obsolete"));
            this.result = result;
        }

        private SFMResolverTextResult result() {
            return result;
        }
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
        keys.put(SFMExplorerEntry.SUBJECT_KIND, SFMExplorerEntry.SortKey.available(directory ? "container" : "file"));
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
