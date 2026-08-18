package ca.teamdman.sfm.client.screen.explorer;

import ca.teamdman.sfm.client.explorer.SFMChildRelationRepository;
import ca.teamdman.sfm.client.explorer.SFMEntitySelector;
import ca.teamdman.sfm.client.explorer.SFMExplorerId;
import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.explorer.SFMSelectionRepository;
import ca.teamdman.sfm.client.explorer.action.SFMExplorerActionEngine;
import ca.teamdman.sfm.client.explorer.action.SFMExplorerActionRequest;
import ca.teamdman.sfm.client.explorer.action.SFMExplorerActionResult;
import ca.teamdman.sfm.client.explorer.action.SFMExplorerPathPolicy;
import ca.teamdman.sfm.client.explorer.action.SFMExplorerRepository;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerCancellationToken;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerEntry;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerResolver;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerResolverRegistry;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerSession;
import ca.teamdman.sfm.client.explorer.lazy.SFMLazyExplorerLoader;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanelBounds;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SFMExplorerFilterTests {
    private static final SFMPath ROOT = SFMPath.parse("registry://test/entries");
    private static final SFMPath ALPHA = SFMPath.parse("registry://test/entries/alpha");
    private static final SFMPath BETA = SFMPath.parse("registry://test/entries/beta");
    private static final SFMPath SOURCE_ROOT = SFMPath.parse("registry://test/source");
    private static final SFMPath SFML_DIRECTORY = SFMPath.parse("registry://test/source/sfml");
    private static final SFMPath SFM_DIRECTORY = SFMPath.parse("registry://test/source/sfm");
    private static final SFMPath SFM_JAVA = SFMPath.parse("registry://test/source/sfm/sfm.java");
    private static final SFMScreenPanelBounds BOUNDS = new SFMScreenPanelBounds(0, 0, 320, 180);

    @Test
    public void sfmScreenshotFilterPreservesTheMaterializedParentOfSfmJava() {
        ScreenshotHierarchyResolver resolver = new ScreenshotHierarchyResolver();
        SFMExplorerResolverRegistry resolvers = new SFMExplorerResolverRegistry();
        resolvers.register(resolver);
        SFMLazyExplorerLoader loader = new SFMLazyExplorerLoader(
                resolvers, new SFMChildRelationRepository()
        );
        loader.openRoot(SOURCE_ROOT).join();
        loader.refresh(SOURCE_ROOT, 16).completion().join();
        loader.refresh(SFML_DIRECTORY, 16).completion().join();
        loader.refresh(SFM_DIRECTORY, 16).completion().join();
        int readsBeforeFilter = resolver.reads();
        SFMExplorerSession session = new SFMExplorerSession(
                new SFMExplorerId("sfm-screenshot-filter"), SOURCE_ROOT, new SFMSelectionRepository()
        );
        SFMExplorerPanelModel model = new SFMExplorerPanelModel(session, loader, ignored -> {});

        session.setFilterQuery("SFM");
        SFMExplorerPanelModel.State filtered = model.state(BOUNDS);

        assertEquals(List.of(SFML_DIRECTORY, SFM_DIRECTORY, SFM_JAVA), filtered.projection().rows().stream()
                .map(ca.teamdman.sfm.client.explorer.lazy.SFMExplorerProjection.Row::path).toList());
        assertEquals(List.of(0, 0, 1), filtered.projection().rows().stream()
                .map(ca.teamdman.sfm.client.explorer.lazy.SFMExplorerProjection.Row::depth).toList());
        assertTrue(filtered.projection().rows().stream()
                .allMatch(ca.teamdman.sfm.client.explorer.lazy.SFMExplorerProjection.Row::filterMatch));
        assertEquals(3, filtered.projection().filter().matchCount());
        assertEquals(3, filtered.projection().filter().visibleRowCount());
        assertEquals(0, filtered.projection().filter().contextAncestorCount());
        assertEquals(readsBeforeFilter, resolver.reads(), "projecting the screenshot topology must perform no I/O");
    }

    @Test
    public void changingAFilterPerformsNoResolverIoAndRetainsHiddenSelectionByPath() {
        List<SFMPath> children = java.util.stream.Stream.concat(
                java.util.stream.Stream.of(ALPHA, BETA),
                IntStream.range(0, 30)
                        .mapToObj(index -> SFMPath.parse("registry://test/entries/row-" + index))
        ).toList();
        CountingResolver resolver = new CountingResolver(children);
        SFMExplorerResolverRegistry resolvers = new SFMExplorerResolverRegistry();
        resolvers.register(resolver);
        SFMLazyExplorerLoader loader = new SFMLazyExplorerLoader(
                resolvers, new SFMChildRelationRepository()
        );
        loader.openRoot(ROOT).join();
        loader.refresh(ROOT, 64).completion().join();
        int readsBeforeFilter = resolver.reads();
        SFMExplorerSession session = new SFMExplorerSession(
                new SFMExplorerId("filter-io"), ROOT, new SFMSelectionRepository()
        );
        SFMExplorerPanelModel model = new SFMExplorerPanelModel(session, loader, ignored -> {});
        model.select(BETA, BOUNDS);
        session.expand(ROOT);
        session.setScrollOffset(12);
        SFMExplorerSession.Snapshot beforeFilter = session.snapshot();

        session.setFilterQuery("alpha");
        SFMExplorerPanelModel.State filtered = model.state(BOUNDS);
        assertEquals(List.of(ALPHA), filtered.projection().rows().stream()
                .map(ca.teamdman.sfm.client.explorer.lazy.SFMExplorerProjection.Row::path).toList());
        assertEquals(Optional.of(BETA), session.snapshot().navigationCursor(),
                "a hidden cursor must survive ranking/filter changes");
        assertEquals(beforeFilter.expanded(), session.snapshot().expanded(),
                "force-revealed filter ancestry must not mutate persisted expansion");
        assertEquals(12, session.snapshot().scrollOffset(),
                "a filter-clamped viewport must not overwrite the pre-filter scroll position");
        assertEquals(0, filtered.viewport().scrollRow(), "the filtered viewport may clamp transiently");
        assertEquals(readsBeforeFilter, resolver.reads(), "filtering must not enumerate or describe more paths");

        session.setFilterQuery("");
        SFMExplorerPanelModel.State cleared = model.state(BOUNDS);
        assertEquals(Optional.of(BETA), cleared.selectedPath());
        assertEquals(beforeFilter.expanded(), cleared.session().expanded());
        assertEquals(12, cleared.session().scrollOffset());
        assertEquals(12, cleared.viewport().scrollRow());
        assertEquals(readsBeforeFilter, resolver.reads());
    }

    @Test
    public void setValuedFilterActionsApplyAndClearAcrossEveryMatchedExplorer() {
        SFMExplorerRepository repository = new SFMExplorerRepository();
        SFMExplorerRepository.Explorer one = explorer("one");
        SFMExplorerRepository.Explorer two = explorer("two");
        repository.register(one, true);
        repository.register(two, false);
        SFMExplorerActionEngine engine = new SFMExplorerActionEngine(repository, ignored -> {
            throw new AssertionError("factory must not be used");
        });
        SFMEntitySelector all = SFMEntitySelector.parse(SFMEntitySelector.Domain.EXPLORER, "all");

        SFMExplorerActionResult set = engine.execute(new SFMExplorerActionRequest(
                all,
                new SFMExplorerActionRequest.FilterSet("bt"),
                SFMExplorerActionRequest.IfNoMatch.FAIL
        ));
        assertEquals(SFMExplorerActionResult.Status.SUCCEEDED, set.status());
        assertTrue(set.targets().stream().allMatch(target -> target.snapshot()
                .settings().filterQuery().equals("bt")));
        assertEquals("bt", one.session().snapshot().settings().filterQuery());
        assertEquals("bt", two.session().snapshot().settings().filterQuery());

        SFMExplorerActionResult clear = engine.execute(new SFMExplorerActionRequest(
                all,
                new SFMExplorerActionRequest.FilterClear(),
                SFMExplorerActionRequest.IfNoMatch.FAIL
        ));
        assertEquals(SFMExplorerActionResult.Status.SUCCEEDED, clear.status());
        assertTrue(clear.targets().stream().allMatch(target -> target.snapshot()
                .settings().filterQuery().isEmpty()));
    }

    private static SFMExplorerRepository.Explorer explorer(String id) {
        CountingResolver resolver = new CountingResolver();
        SFMExplorerResolverRegistry resolvers = new SFMExplorerResolverRegistry();
        resolvers.register(resolver);
        return new SFMExplorerRepository.Explorer(
                new SFMExplorerSession(new SFMExplorerId(id), ROOT, new SFMSelectionRepository()),
                new SFMLazyExplorerLoader(resolvers, new SFMChildRelationRepository()),
                SFMExplorerPathPolicy.schemes(Set.of("registry")),
                16
        );
    }

    private static final class CountingResolver implements SFMExplorerResolver {
        private final AtomicInteger reads = new AtomicInteger();
        private final List<SFMPath> children;

        private CountingResolver() {
            this(List.of(ALPHA, BETA));
        }

        private CountingResolver(List<SFMPath> children) {
            this.children = List.copyOf(children);
        }

        @Override
        public String scheme() {
            return "registry";
        }

        @Override
        public long generation() {
            return 1;
        }

        @Override
        public CompletableFuture<SFMExplorerEntry> describe(
                SFMPath path,
                SFMExplorerCancellationToken cancellation
        ) {
            reads.incrementAndGet();
            return CompletableFuture.completedFuture(entry(path));
        }

        @Override
        public CompletableFuture<ChildPage> resolveChildren(ChildRequest request) {
            reads.incrementAndGet();
            List<SFMExplorerEntry> resolvedChildren = request.parent().equals(ROOT)
                    ? children.stream().map(CountingResolver::entry).toList()
                    : List.of();
            return CompletableFuture.completedFuture(new ChildPage(
                    request.parent(),
                    resolvedChildren,
                    Optional.empty(),
                    generation(),
                    List.of(),
                    resolvedChildren.size()
            ));
        }

        int reads() {
            return reads.get();
        }

        private static SFMExplorerEntry entry(SFMPath path) {
            String label = path.equals(ROOT)
                    ? "Entries"
                    : path.segments().get(path.segments().size() - 1);
            return SFMExplorerEntry.simple(path, label, path.equals(ROOT), Optional.empty());
        }
    }

    private static final class ScreenshotHierarchyResolver implements SFMExplorerResolver {
        private final AtomicInteger reads = new AtomicInteger();

        @Override
        public String scheme() {
            return "registry";
        }

        @Override
        public long generation() {
            return 1;
        }

        @Override
        public CompletableFuture<SFMExplorerEntry> describe(
                SFMPath path,
                SFMExplorerCancellationToken cancellation
        ) {
            reads.incrementAndGet();
            return CompletableFuture.completedFuture(entry(path));
        }

        @Override
        public CompletableFuture<ChildPage> resolveChildren(ChildRequest request) {
            reads.incrementAndGet();
            List<SFMPath> childPaths;
            if (request.parent().equals(SOURCE_ROOT)) childPaths = List.of(SFML_DIRECTORY, SFM_DIRECTORY);
            else if (request.parent().equals(SFM_DIRECTORY)) childPaths = List.of(SFM_JAVA);
            else childPaths = List.of();
            List<SFMExplorerEntry> children = childPaths.stream()
                    .map(ScreenshotHierarchyResolver::entry)
                    .toList();
            return CompletableFuture.completedFuture(new ChildPage(
                    request.parent(),
                    children,
                    Optional.empty(),
                    generation(),
                    List.of(),
                    children.size()
            ));
        }

        int reads() {
            return reads.get();
        }

        private static SFMExplorerEntry entry(SFMPath path) {
            String label = path.equals(SFM_JAVA)
                    ? "SFM.java"
                    : path.segments().isEmpty()
                    ? path.authority()
                    : path.segments().get(path.segments().size() - 1);
            boolean expandable = !path.equals(SFM_JAVA);
            return SFMExplorerEntry.simple(path, label, expandable, Optional.empty());
        }
    }
}
