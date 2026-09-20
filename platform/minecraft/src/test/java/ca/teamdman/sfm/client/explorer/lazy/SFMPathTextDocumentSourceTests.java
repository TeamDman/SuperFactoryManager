package ca.teamdman.sfm.client.explorer.lazy;

import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentSnapshot;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentSource;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SFMPathTextDocumentSourceTests {
    @TempDir
    Path temporaryDirectory;

    @Test
    public void readyTextIsStrictUtf8HashedAndClassifiedAcrossLineEndingKinds() throws Exception {
        Path root = Files.createDirectory(temporaryDirectory.resolve("root"));
        List<TextCase> cases = List.of(
                new TextCase("none.txt", "snowman ☃", SFMResolverTextResult.LineEndingKind.NONE),
                new TextCase("lf.txt", "alpha\nbeta\n", SFMResolverTextResult.LineEndingKind.LF),
                new TextCase("crlf.txt", "alpha\r\nbeta\r\n", SFMResolverTextResult.LineEndingKind.CRLF),
                new TextCase("cr.txt", "alpha\rbeta\r", SFMResolverTextResult.LineEndingKind.CR),
                new TextCase("mixed.txt", "alpha\r\nbeta\ngamma\r", SFMResolverTextResult.LineEndingKind.MIXED)
        );
        for (TextCase testCase : cases) {
            Files.writeString(root.resolve(testCase.name()), testCase.text(), StandardCharsets.UTF_8);
        }

        SFMExplorerIoCounter io = new SFMExplorerIoCounter();
        ExecutorService worker = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "resolver-text-test-worker");
            thread.setDaemon(true);
            return thread;
        });
        try {
            SFMFilesystemExplorerResolver resolver = resolver(List.of(root), worker, io);
            for (TextCase testCase : cases) {
                Path file = root.resolve(testCase.name());
                SFMResolverTextResult result = read(resolver, file, root, Optional.empty(), 4096);

                assertEquals(SFMResolverTextResult.Status.READY, result.status());
                assertEquals(testCase.text(), result.text().orElseThrow());
                assertEquals(testCase.lineEndings(), result.lineEndingKind().orElseThrow());
                assertEquals(sha256(testCase.text().getBytes(StandardCharsets.UTF_8)), result.sha256().orElseThrow());
                assertEquals(Files.size(file), result.byteLength().orElseThrow());
                assertTrue(result.lastModified().isPresent());
                assertTrue(result.diagnostic().isEmpty());
            }
            assertEquals(
                    List.of("resolver-text-test-worker"),
                    io.snapshot().observedThreads(),
                    "filesystem inspection must run through the resolver worker"
            );
        } finally {
            worker.shutdownNow();
            assertTrue(worker.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    public void expectedHashAcceptsAMatchAndReturnsTypedStaleContentAfterChangeOrMismatch() throws IOException {
        Path root = Files.createDirectory(temporaryDirectory.resolve("hash-root"));
        Path file = Files.writeString(root.resolve("A.java"), "class A {}\n", StandardCharsets.UTF_8);
        SFMFilesystemExplorerResolver resolver = resolver(root);

        SFMResolverTextResult initial = read(resolver, file, root, Optional.empty(), 4096);
        String initialHash = initial.sha256().orElseThrow();
        SFMResolverTextResult matching = read(
                resolver,
                file,
                root,
                Optional.of(initialHash.toUpperCase(java.util.Locale.ROOT)),
                4096
        );
        assertEquals(SFMResolverTextResult.Status.READY, matching.status());

        SFMResolverTextResult explicitMismatch = read(
                resolver,
                file,
                root,
                Optional.of("0".repeat(64)),
                4096
        );
        assertEquals(SFMResolverTextResult.Status.STALE_CONTENT, explicitMismatch.status());
        assertEquals(initialHash, explicitMismatch.sha256().orElseThrow());

        Files.writeString(file, "class B {}\n", StandardCharsets.UTF_8);
        SFMResolverTextResult changed = read(resolver, file, root, Optional.of(initialHash), 4096);
        assertEquals(SFMResolverTextResult.Status.STALE_CONTENT, changed.status());
        assertNotEquals(initialHash, changed.sha256().orElseThrow());
        assertEquals(Files.size(file), changed.byteLength().orElseThrow());
    }

    @Test
    public void eachRequestUsesItsExactAuthorizedRootAndNeverFallsBackToAnotherGrant() throws IOException {
        Path broad = Files.createDirectory(temporaryDirectory.resolve("broad"));
        Path nested = Files.createDirectory(broad.resolve("nested"));
        Path nestedFile = Files.writeString(nested.resolve("inside.txt"), "inside", StandardCharsets.UTF_8);
        Path wrong = Files.createDirectory(temporaryDirectory.resolve("wrong"));
        Path ungrantedNested = Files.createDirectory(broad.resolve("not-granted"));
        Path ungrantedFile = Files.writeString(
                ungrantedNested.resolve("inside.txt"),
                "inside",
                StandardCharsets.UTF_8
        );
        Path removed = Files.createDirectory(temporaryDirectory.resolve("removed"));
        Path removedFile = removed.resolve("gone.txt");
        SFMFilesystemExplorerResolver resolver = resolver(List.of(broad, nested, wrong, removed));

        assertEquals(
                SFMResolverTextResult.Status.READY,
                read(resolver, nestedFile, nested, Optional.empty(), 64).status(),
                "a specifically authorized nested root must work even when a broader root also exists"
        );
        assertEquals(
                SFMResolverTextResult.Status.UNAVAILABLE,
                read(resolver, nestedFile, wrong, Optional.empty(), 64).status(),
                "a different granted root must not authorize the target"
        );
        assertEquals(
                SFMResolverTextResult.Status.REMOVED_ROOT,
                read(resolver, ungrantedFile, ungrantedNested, Optional.empty(), 64).status(),
                "the resolver must not silently substitute the broader process-wide grant"
        );

        Files.delete(removed);
        assertEquals(
                SFMResolverTextResult.Status.REMOVED_ROOT,
                read(resolver, removedFile, removed, Optional.empty(), 64).status()
        );
        assertEquals(
                SFMResolverTextResult.Status.UNAVAILABLE,
                read(resolver, broad.resolve("missing.txt"), broad, Optional.empty(), 64).status()
        );
    }

    @Test
    public void directoriesBinaryInvalidUtf8AndOversizedFilesHaveDistinctTypedResults() throws IOException {
        Path root = Files.createDirectory(temporaryDirectory.resolve("failure-root"));
        Path directory = Files.createDirectory(root.resolve("directory"));
        Path binary = Files.write(root.resolve("binary.bin"), new byte[]{'a', 0, 'b'});
        Path invalidUtf8 = Files.write(root.resolve("invalid.txt"), new byte[]{(byte) 0xC3, 0x28});
        Path oversized = Files.write(root.resolve("oversized.txt"), new byte[]{1, 2, 3, 4, 5});
        SFMFilesystemExplorerResolver resolver = resolver(root);

        assertEquals(
                SFMResolverTextResult.Status.DIRECTORY,
                read(resolver, directory, root, Optional.empty(), 64).status()
        );
        assertEquals(
                SFMResolverTextResult.Status.BINARY,
                read(resolver, binary, root, Optional.empty(), 64).status()
        );
        assertEquals(
                SFMResolverTextResult.Status.UNSUPPORTED_ENCODING,
                read(resolver, invalidUtf8, root, Optional.empty(), 64).status()
        );
        SFMResolverTextResult tooLarge = read(resolver, oversized, root, Optional.empty(), 4);
        assertEquals(SFMResolverTextResult.Status.OVERSIZED, tooLarge.status());
        assertEquals(5, tooLarge.byteLength().orElseThrow());
    }

    @Test
    public void cancellationAndGenerationChangesAreTypedBeforeQueuedReadsTouchTheFilesystem() throws IOException {
        Path root = Files.createDirectory(temporaryDirectory.resolve("race-root"));
        Path file = Files.writeString(root.resolve("queued.txt"), "queued", StandardCharsets.UTF_8);
        ManualExecutor executor = new ManualExecutor();
        SFMExplorerIoCounter io = new SFMExplorerIoCounter();
        SFMFilesystemExplorerResolver resolver = resolver(List.of(root), executor, io);

        SFMExplorerCancellationToken cancellation = new SFMExplorerCancellationToken();
        CompletableFuture<SFMResolverTextResult> cancelled = resolver.readText(request(
                resolver,
                file,
                root,
                Optional.empty(),
                64,
                cancellation
        ));
        assertFalse(cancelled.isDone(), "the caller must not perform the filesystem read inline");
        cancellation.cancel();
        executor.runNext();
        assertEquals(SFMResolverTextResult.Status.CANCELLED, cancelled.join().status());
        assertEquals(0, io.snapshot().metadataReads());

        CompletableFuture<SFMResolverTextResult> staleGeneration = resolver.readText(request(
                resolver,
                file,
                root,
                Optional.empty(),
                64,
                new SFMExplorerCancellationToken()
        ));
        resolver.invalidate();
        executor.runNext();
        assertEquals(SFMResolverTextResult.Status.STALE_GENERATION, staleGeneration.join().status());
        assertEquals(0, io.snapshot().metadataReads());
    }

    @Test
    public void nonFilesystemResolversTruthfullyReportThatTextReadsAreUnsupported() {
        SFMPath root = SFMPath.parse("registry://minecraft/item/");
        SFMPath item = SFMPath.parse("registry://minecraft/item/stone");
        SFMExplorerResolver resolver = new SFMExplorerResolver() {
            @Override
            public String scheme() {
                return "registry";
            }

            @Override
            public long generation() {
                return 3;
            }

            @Override
            public CompletableFuture<SFMExplorerEntry> describe(
                    SFMPath path,
                    SFMExplorerCancellationToken cancellation
            ) {
                return CompletableFuture.completedFuture(
                        SFMExplorerEntry.simple(path, "item", false, Optional.empty())
                );
            }

            @Override
            public CompletableFuture<ChildPage> resolveChildren(ChildRequest request) {
                return CompletableFuture.completedFuture(new ChildPage(
                        request.parent(), List.of(), Optional.empty(), generation(), List.of(), 0
                ));
            }
        };
        SFMResolverTextRequest request = new SFMResolverTextRequest(
                item,
                root,
                Optional.empty(),
                64,
                resolver.generation(),
                new SFMExplorerCancellationToken()
        );

        assertFalse(resolver.supportsTextRead());
        assertEquals(SFMResolverTextResult.Status.UNSUPPORTED_RESOLVER, resolver.readText(request).join().status());
    }

    @Test
    public void pinnedSnapshotRetainsAddressAndFailsClosedOnHashMismatch() {
        String text = "class Café {}\n";
        String hash = sha256(text.getBytes(StandardCharsets.UTF_8));
        SFMPath root = SFMPath.parse("review://document/revision-1/");
        SFMPath path = SFMPath.parse("review://document/revision-1/src/Cafe.java");
        SFMTextDocumentSource.PinnedSnapshot source = new SFMTextDocumentSource.PinnedSnapshot(
                path, root, text, hash, Optional.empty(), Optional.empty());

        SFMTextDocumentSnapshot ready = source.load(new SFMExplorerCancellationToken()).join();
        assertTrue(ready.ready());
        assertTrue(ready.readOnly());
        assertEquals(path, ready.path().orElseThrow());
        assertEquals(root, ready.authorizedRoot().orElseThrow());
        assertEquals(hash, ready.sha256().orElseThrow());

        SFMTextDocumentSnapshot stale = new SFMTextDocumentSource.PinnedSnapshot(
                path, root, text, "0".repeat(64), Optional.empty(), Optional.empty())
                .load(new SFMExplorerCancellationToken()).join();
        assertEquals(SFMTextDocumentSnapshot.State.STALE_CONTENT, stale.state());
        assertFalse(stale.ready());
    }

    @Test
    public void symbolicLinksCannotEscapeTheSuppliedAuthorizedRoot() throws IOException {
        Path root = Files.createDirectory(temporaryDirectory.resolve("symlink-root"));
        Path outside = Files.writeString(
                temporaryDirectory.resolve("outside.txt"),
                "secret",
                StandardCharsets.UTF_8
        );
        Path link = root.resolve("escape.txt");
        try {
            Files.createSymbolicLink(link, outside);
        } catch (IOException | UnsupportedOperationException | SecurityException unavailable) {
            Assumptions.assumeTrue(false, "Symbolic links are unavailable: " + unavailable.getMessage());
        }

        assertEquals(
                SFMResolverTextResult.Status.UNAVAILABLE,
                read(resolver(root), link, root, Optional.empty(), 64).status()
        );
    }

    private static SFMResolverTextResult read(
            SFMFilesystemExplorerResolver resolver,
            Path path,
            Path authorizedRoot,
            Optional<String> expectedSha256,
            int maximumBytes
    ) {
        return resolver.readText(request(
                resolver,
                path,
                authorizedRoot,
                expectedSha256,
                maximumBytes,
                new SFMExplorerCancellationToken()
        )).join();
    }

    private static SFMResolverTextRequest request(
            SFMFilesystemExplorerResolver resolver,
            Path path,
            Path authorizedRoot,
            Optional<String> expectedSha256,
            int maximumBytes,
            SFMExplorerCancellationToken cancellation
    ) {
        return new SFMResolverTextRequest(
                SFMPath.fromNative(path),
                SFMPath.fromNative(authorizedRoot),
                expectedSha256,
                maximumBytes,
                resolver.generation(),
                cancellation
        );
    }

    private static SFMFilesystemExplorerResolver resolver(Path root) {
        return resolver(List.of(root));
    }

    private static SFMFilesystemExplorerResolver resolver(List<Path> roots) {
        return resolver(roots, Runnable::run, new SFMExplorerIoCounter());
    }

    private static SFMFilesystemExplorerResolver resolver(
            List<Path> roots,
            Executor executor,
            SFMExplorerIoCounter io
    ) {
        return new SFMFilesystemExplorerResolver(roots, executor, io, 32);
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private record TextCase(
            String name,
            String text,
            SFMResolverTextResult.LineEndingKind lineEndings
    ) {
    }

    private static final class ManualExecutor implements Executor {
        private final ArrayDeque<Runnable> work = new ArrayDeque<>();

        @Override
        public void execute(Runnable command) {
            work.addLast(Objects.requireNonNull(command, "command"));
        }

        private void runNext() {
            work.removeFirst().run();
        }
    }
}
