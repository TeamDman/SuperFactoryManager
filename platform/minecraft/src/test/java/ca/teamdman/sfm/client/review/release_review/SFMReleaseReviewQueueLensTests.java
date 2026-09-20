package ca.teamdman.sfm.client.review.release_review;

import ca.teamdman.sfm.client.explorer.*;
import ca.teamdman.sfm.client.explorer.lazy.*;
import ca.teamdman.sfm.client.screen.explorer.SFMExplorerPanel;
import ca.teamdman.sfm.client.screen.workspace.SFMReleaseReviewExplorerScreenType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class SFMReleaseReviewQueueLensTests {
    @TempDir Path temporaryDirectory;

    @Test
    void warmEquivalentExplicitQueryKeepsRowsRootPresentationAndPortableBytes() throws Exception {
        try (var fixture = fixture(Optional.of(" ( HEAD ) "), Runnable::run)) {
            fixture.loader.openRoot(fixture.root).get(5, TimeUnit.SECONDS);
            fixture.session.initializeRootChildren(fixture.root, fixture.loader, 128).orElseThrow()
                    .completion().get(5, TimeUnit.SECONDS);
            fixture.session.setFilterQuery("Main");
            fixture.session.setScrollOffset(3);
            var sessionBefore = fixture.session.snapshot();
            var relationBefore = fixture.loader.relationSnapshot();
            var reviewBefore = fixture.runtime.snapshot();
            String bytes = Files.readString(fixture.file);

            var next = fixture.resolver.ensureActiveWorkQueueLens(fixture.panel).toCompletableFuture();
            assertTrue(next.isDone(), "equivalent warm navigation is not a fresh asynchronous lens load");
            assertEquals(fixture.root, next.join().root());
            assertEquals(sessionBefore, fixture.session.snapshot());
            assertEquals(relationBefore, fixture.loader.relationSnapshot());
            assertEquals(reviewBefore, fixture.runtime.snapshot());
            assertEquals(bytes, Files.readString(fixture.file));
        }
    }

    @Test
    void equivalentPendingQueueIsNotCancelledOrReplaced() throws Exception {
        var publications = new ConcurrentLinkedQueue<Runnable>();
        var hold = new AtomicBoolean(true);
        Executor executor = action -> { if (hold.get()) publications.add(action); else action.run(); };
        try (var fixture = fixture(Optional.of("HEAD"), executor)) {
            var pending = fixture.session.requestChildren(fixture.root, fixture.loader, 128);
            assertFalse(pending.completion().isDone());
            var next = fixture.resolver.ensureActiveWorkQueueLens(fixture.panel).toCompletableFuture();
            assertTrue(next.isDone(), "read navigation can use the known lens while its children load");
            assertEquals(fixture.root, next.join().root());
            assertFalse(pending.completion().isDone(), "navigation must not cancel the original load");
            assertSame(pending, fixture.session.activeRequestHandle(fixture.root).orElseThrow());
            hold.set(false);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (!pending.completion().isDone() && System.nanoTime() < deadline) {
                Runnable action;
                while ((action = publications.poll()) != null) action.run();
                Thread.sleep(1);
            }
            assertEquals(SFMLazyExplorerLoader.LoadDisposition.PUBLISHED,
                    pending.completion().get(1, TimeUnit.SECONDS).disposition());
        }
    }

    @Test
    void differentQueryStillSwitchesAndReopenedEpochIsRejected() throws Exception {
        try (var fixture = fixture(Optional.of("#approved"), Runnable::run)) {
            var next = fixture.resolver.ensureActiveWorkQueueLens(fixture.panel)
                    .toCompletableFuture().get(5, TimeUnit.SECONDS);
            assertNotEquals(fixture.root, next.root());
            assertEquals(Optional.of("HEAD"), fixture.resolver.queryExpression(next));
            assertEquals(Set.of(next.root()), fixture.session.snapshot().roots());
            fixture.runtime.open(fixture.file, true);
            assertThrows(IllegalStateException.class,
                    () -> fixture.resolver.ensureActiveWorkQueueLens(fixture.panel));
        }
    }

    @Test
    void explicitMutationRefreshStillPublishesTheNewResolverGeneration() throws Exception {
        try (var fixture = fixture(Optional.empty(), Runnable::run)) {
            fixture.session.initializeRootChildren(fixture.root, fixture.loader, 128).orElseThrow()
                    .completion().get(5, TimeUnit.SECONDS);
            long previous = fixture.runtime.generation();
            fixture.runtime.activateQuery(Optional.empty(), "#approved");
            assertTrue(fixture.runtime.generation() > previous);
            fixture.resolver.switchLens(fixture.panel,
                    SFMReleaseReviewExplorerScreenType.Projection.QUERY, Optional.empty())
                    .toCompletableFuture().get(5, TimeUnit.SECONDS);
            assertEquals(fixture.runtime.generation(),
                    fixture.loader.relationSnapshot().pageStates().get(fixture.root).resolverGeneration());
        }
    }

    private Fixture fixture(Optional<String> explicitQuery, Executor publicationExecutor) throws Exception {
        Path file = temporaryDirectory.resolve("review.sfm-review.json");
        Files.copy(sourceFixture(), file);
        var runtime = new SFMReleaseReviewRuntime();
        runtime.open(file, true);
        runtime.activateQuery(Optional.empty(), "HEAD");
        var resolver = new SFMReleaseReviewExplorerRuntime(runtime);
        var root = resolver.prepareLens(file, SFMReleaseReviewExplorerScreenType.Projection.QUERY, explicitQuery);
        var registry = new SFMExplorerResolverRegistry();
        registry.register(resolver);
        var loader = new SFMLazyExplorerLoader(registry, new SFMChildRelationRepository(), publicationExecutor);
        var session = new SFMExplorerSession(new SFMExplorerId("queue-lens"),
                new SFMPathExpression.Literal(SFMPath.fromNative(file)), Set.of(root), new SFMSelectionRepository());
        var panel = new SFMExplorerPanel(session, loader, ignored -> { });
        return new Fixture(file, runtime, resolver, root, loader, session, panel);
    }

    private static Path sourceFixture() {
        Path cursor = Path.of("").toAbsolutePath();
        for (int depth = 0; depth < 8 && cursor != null; depth++, cursor = cursor.getParent()) {
            Path candidate = cursor.resolve("docs/architecture/fixtures/release-review-v1.json");
            if (Files.isRegularFile(candidate)) return candidate;
        }
        throw new IllegalStateException("Unable to locate canonical release-review fixture");
    }

    private record Fixture(Path file, SFMReleaseReviewRuntime runtime, SFMReleaseReviewExplorerRuntime resolver,
                           SFMPath root, SFMLazyExplorerLoader loader, SFMExplorerSession session,
                           SFMExplorerPanel panel) implements AutoCloseable {
        @Override public void close() { session.close(); runtime.close(); }
    }
}
