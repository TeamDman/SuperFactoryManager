package ca.teamdman.sfm.client.explorer.lazy;

import ca.teamdman.sfm.client.explorer.SFMChildRelationRepository;
import ca.teamdman.sfm.client.explorer.SFMExplorerId;
import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.explorer.SFMSelectionRepository;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMExplorerPathRevealTests {
    private static final SFMPath ROOT = SFMPath.parse("registry://minecraft/item/");
    private static final SFMPath DECOY_A = SFMPath.parse("registry://minecraft/item/a");
    private static final SFMPath DECOY_B = SFMPath.parse("registry://minecraft/item/b");
    private static final SFMPath PACKAGE = SFMPath.parse("registry://minecraft/item/zpackage");
    private static final SFMPath TARGET = SFMPath.parse("registry://minecraft/item/zpackage/target");

    @Test
    void revealPagesUntilEveryAncestorExistsThenPublishesExactSelection() {
        Fixture fixture = fixture();
        fixture.session.setFilterQuery("nothing-matches");
        AtomicInteger selected = new AtomicInteger();

        SFMExplorerPathReveal.Result result = SFMExplorerPathReveal.reveal(
                fixture.session,
                fixture.loader,
                ROOT,
                TARGET,
                1,
                selected::incrementAndGet
        ).toCompletableFuture().join();

        assertEquals(4, result.loadedPages(), "three root pages plus one package page");
        assertEquals(List.of(ROOT, PACKAGE), result.expanded());
        assertTrue(fixture.session.snapshot().expanded().containsAll(List.of(ROOT, PACKAGE)));
        assertEquals(Optional.of(TARGET), fixture.session.snapshot().navigationCursor());
        assertEquals("", fixture.session.snapshot().settings().filterQuery());
        assertEquals(1, selected.get());
        assertTrue(fixture.loader.relationSnapshot().relation().childrenOf(ROOT).contains(PACKAGE));
        assertTrue(fixture.loader.relationSnapshot().relation().childrenOf(PACKAGE).contains(TARGET));

        AtomicInteger repeatedSelection = new AtomicInteger();
        SFMExplorerPathReveal.Result repeated = SFMExplorerPathReveal.reveal(
                fixture.session,
                fixture.loader,
                ROOT,
                TARGET,
                1,
                repeatedSelection::incrementAndGet
        ).toCompletableFuture().join();
        assertEquals(0, repeated.loadedPages(), "already-materialized reveals perform no resolver work");
        assertEquals(1, repeatedSelection.get());
    }

    @Test
    void revealJoinsAnAlreadyInFlightPageInsteadOfFailingContinuationAcquisition() {
        ArrayDeque<Runnable> resolverTasks = new ArrayDeque<>();
        ArrayList<SFMInMemoryRegistryExplorerResolver.Node> nodes = new ArrayList<>();
        nodes.add(node(ROOT, true, List.of(DECOY_A, DECOY_B, PACKAGE)));
        nodes.add(node(DECOY_A, false, List.of()));
        nodes.add(node(DECOY_B, false, List.of()));
        nodes.add(node(PACKAGE, true, List.of(TARGET)));
        nodes.add(node(TARGET, false, List.of()));
        SFMExplorerResolverRegistry resolvers = new SFMExplorerResolverRegistry();
        resolvers.register(new SFMInMemoryRegistryExplorerResolver(nodes, resolverTasks::addLast, 1));
        SFMLazyExplorerLoader loader = new SFMLazyExplorerLoader(
                resolvers,
                new SFMChildRelationRepository(),
                Runnable::run
        );
        SFMExplorerSession session = new SFMExplorerSession(
                new SFMExplorerId("reveal-in-flight-test"),
                ROOT,
                new SFMSelectionRepository()
        );
        CompletableFuture<SFMExplorerEntry> opened = loader.openRoot(ROOT);
        resolverTasks.removeFirst().run();
        opened.join();

        session.requestChildren(ROOT, loader, 1);
        assertTrue(session.activeRequestHandle(ROOT).isPresent());
        CompletableFuture<SFMExplorerPathReveal.Result> reveal = SFMExplorerPathReveal.reveal(
                session,
                loader,
                ROOT,
                TARGET,
                1,
                () -> { }
        ).toCompletableFuture();
        while (!reveal.isDone() && !resolverTasks.isEmpty()) resolverTasks.removeFirst().run();

        SFMExplorerPathReveal.Result result = reveal.join();
        assertEquals(4, result.loadedPages(), "the existing root page plus its remaining pages are joined once");
        assertEquals(Optional.of(TARGET), session.snapshot().navigationCursor());
        assertTrue(session.activeRequestHandle(ROOT).isEmpty());
    }

    private static Fixture fixture() {
        return fixture(Runnable::run);
    }

    @Test
    void queryPreviewRetainsSelectionAndFilterAndRejectsStalePageContinuations() {
        var fixture = fixture();
        fixture.session.navigateTo(DECOY_A);
        fixture.session.setFilterQuery(".java");
        var result = SFMExplorerPathReveal.preview(fixture.session, fixture.loader, ROOT, TARGET, 1,
                () -> { }, () -> true).toCompletableFuture().join();
        assertEquals(4, result.loadedPages());
        assertEquals(Optional.of(DECOY_A), fixture.session.snapshot().navigationCursor());
        assertEquals(".java", fixture.session.snapshot().settings().filterQuery());

        var tasks = new ArrayDeque<Runnable>();
        var delayed = fixture(tasks::addLast);
        var current = new java.util.concurrent.atomic.AtomicBoolean(true);
        var callback = new AtomicInteger();
        var pending = SFMExplorerPathReveal.preview(delayed.session, delayed.loader, ROOT, TARGET, 1,
                callback::incrementAndGet, current::get).toCompletableFuture();
        current.set(false);
        tasks.removeFirst().run();
        assertTrue(pending.isCompletedExceptionally());
        assertTrue(tasks.isEmpty(), "obsolete find must not request a second page");
        assertEquals(0, callback.get());
        assertTrue(delayed.session.snapshot().navigationCursor().isEmpty());
        assertTrue(!delayed.session.snapshot().expanded().contains(PACKAGE));
    }

    @Test
    void retainingFilterNeverClearsItOrRestoresAnOlderFilterAfterBackgroundLoading() {
        ArrayDeque<Runnable> tasks = new ArrayDeque<>();
        Fixture fixture = fixture(tasks::addLast);
        fixture.session.setFilterQuery(".java");
        var reveal = SFMExplorerPathReveal.reveal(fixture.session, fixture.loader, ROOT, TARGET, 1,
                () -> { }, SFMExplorerPathReveal.FilterPolicy.RETAIN).toCompletableFuture();
        assertEquals(".java", fixture.session.snapshot().settings().filterQuery());
        fixture.session.setFilterQuery("newer filter while loading");
        while (!tasks.isEmpty()) tasks.removeFirst().run();
        assertEquals(4, reveal.join().loadedPages());
        assertEquals("newer filter while loading", fixture.session.snapshot().settings().filterQuery());
        assertEquals(Optional.of(TARGET), fixture.session.snapshot().navigationCursor());
    }

    @Test
    void delayedRootInitializationJoinsRevealInsteadOfMakingItsFirstPageStale() {
        ArrayDeque<Runnable> tasks = new ArrayDeque<>();
        Fixture fixture = fixture(tasks::addLast);
        CompletableFuture<SFMExplorerEntry> described = fixture.loader.openRoot(ROOT);
        described.thenRun(() -> fixture.session.initializeRootChildren(ROOT, fixture.loader, 1));
        CompletableFuture<SFMExplorerPathReveal.Result> reveal = SFMExplorerPathReveal.reveal(
                fixture.session, fixture.loader, ROOT, TARGET, 1, () -> { }
        ).toCompletableFuture();
        var original = fixture.session.activeRequestHandle(ROOT).orElseThrow();
        tasks.removeFirst().run(); // describe finishes after reveal already owns root loading
        assertEquals(original, fixture.session.activeRequestHandle(ROOT).orElseThrow());
        while (!tasks.isEmpty()) tasks.removeFirst().run();
        assertEquals(4, reveal.join().loadedPages());
        assertEquals(Optional.of(TARGET), fixture.session.snapshot().navigationCursor());
        assertTrue(fixture.session.initializeRootChildren(ROOT, fixture.loader, 1).isEmpty(),
                "initialization must not throw away already fetched continuation pages");
        assertTrue(tasks.isEmpty());
    }

    @Test
    void initialRootLoadIsTrackedSoRevealCanJoinIt() {
        ArrayDeque<Runnable> tasks = new ArrayDeque<>();
        Fixture fixture = fixture(tasks::addLast);
        var initial = fixture.session.initializeRootChildren(ROOT, fixture.loader, 1).orElseThrow();
        assertEquals(initial, fixture.session.activeRequestHandle(ROOT).orElseThrow());
        var reveal = SFMExplorerPathReveal.reveal(
                fixture.session, fixture.loader, ROOT, TARGET, 1, () -> { }
        ).toCompletableFuture();
        while (!tasks.isEmpty()) tasks.removeFirst().run();
        assertEquals(4, reveal.join().loadedPages());
    }

    @Test
    void delayedInitializationCannotMaterializeRemovedRootsOrClosedSessions() {
        Fixture fixture = fixture();
        fixture.session.replaceLocation(new ca.teamdman.sfm.client.explorer.SFMPathExpression.Literal(PACKAGE),
                java.util.Set.of(PACKAGE));
        assertTrue(fixture.session.initializeRootChildren(ROOT, fixture.loader, 1).isEmpty());
        assertTrue(fixture.loader.relationSnapshot().pageStates().isEmpty());
        fixture.session.close();
        assertTrue(fixture.session.initializeRootChildren(PACKAGE, fixture.loader, 1).isEmpty());
        assertTrue(fixture.loader.relationSnapshot().pageStates().isEmpty());
    }

    private static Fixture fixture(java.util.concurrent.Executor executor) {
        ArrayList<SFMInMemoryRegistryExplorerResolver.Node> nodes = new ArrayList<>();
        nodes.add(node(ROOT, true, List.of(DECOY_A, DECOY_B, PACKAGE)));
        nodes.add(node(DECOY_A, false, List.of()));
        nodes.add(node(DECOY_B, false, List.of()));
        nodes.add(node(PACKAGE, true, List.of(TARGET)));
        nodes.add(node(TARGET, false, List.of()));
        SFMExplorerResolverRegistry resolvers = new SFMExplorerResolverRegistry();
        resolvers.register(new SFMInMemoryRegistryExplorerResolver(nodes, executor, 1));
        SFMChildRelationRepository relations = new SFMChildRelationRepository();
        SFMLazyExplorerLoader loader = new SFMLazyExplorerLoader(resolvers, relations, Runnable::run);
        SFMExplorerSession session = new SFMExplorerSession(
                new SFMExplorerId("reveal-test"),
                ROOT,
                new SFMSelectionRepository()
        );
        return new Fixture(session, loader);
    }

    private static SFMInMemoryRegistryExplorerResolver.Node node(
            SFMPath path,
            boolean expandable,
            List<SFMPath> children
    ) {
        return new SFMInMemoryRegistryExplorerResolver.Node(
                SFMExplorerEntry.simple(path, path.canonical(), expandable, Optional.empty()),
                children
        );
    }

    private record Fixture(SFMExplorerSession session, SFMLazyExplorerLoader loader) {
    }
}
