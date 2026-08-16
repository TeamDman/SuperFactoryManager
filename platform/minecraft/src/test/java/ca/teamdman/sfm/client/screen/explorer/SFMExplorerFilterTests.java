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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SFMExplorerFilterTests {
    private static final SFMPath ROOT = SFMPath.parse("registry://test/entries");
    private static final SFMPath ALPHA = SFMPath.parse("registry://test/entries/alpha");
    private static final SFMPath BETA = SFMPath.parse("registry://test/entries/beta");
    private static final SFMScreenPanelBounds BOUNDS = new SFMScreenPanelBounds(0, 0, 320, 180);

    @Test
    public void changingAFilterPerformsNoResolverIoAndRetainsHiddenSelectionByPath() {
        CountingResolver resolver = new CountingResolver();
        SFMExplorerResolverRegistry resolvers = new SFMExplorerResolverRegistry();
        resolvers.register(resolver);
        SFMLazyExplorerLoader loader = new SFMLazyExplorerLoader(
                resolvers, new SFMChildRelationRepository()
        );
        loader.openRoot(ROOT).join();
        loader.refresh(ROOT, 16).completion().join();
        int readsBeforeFilter = resolver.reads();
        SFMExplorerSession session = new SFMExplorerSession(
                new SFMExplorerId("filter-io"), ROOT, new SFMSelectionRepository()
        );
        SFMExplorerPanelModel model = new SFMExplorerPanelModel(session, loader, ignored -> {});
        model.select(BETA, BOUNDS);

        session.setFilterQuery("alpha");
        SFMExplorerPanelModel.State filtered = model.state(BOUNDS);
        assertEquals(List.of(ALPHA), filtered.projection().rows().stream()
                .map(ca.teamdman.sfm.client.explorer.lazy.SFMExplorerProjection.Row::path).toList());
        assertEquals(Optional.of(BETA), session.snapshot().navigationCursor(),
                "a hidden cursor must survive ranking/filter changes");
        assertEquals(readsBeforeFilter, resolver.reads(), "filtering must not enumerate or describe more paths");

        session.setFilterQuery("");
        SFMExplorerPanelModel.State cleared = model.state(BOUNDS);
        assertEquals(Optional.of(BETA), cleared.selectedPath());
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
            List<SFMExplorerEntry> children = request.parent().equals(ROOT)
                    ? List.of(entry(ALPHA), entry(BETA))
                    : List.of();
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
            String label = path.equals(ROOT)
                    ? "Entries"
                    : path.segments().get(path.segments().size() - 1);
            return SFMExplorerEntry.simple(path, label, path.equals(ROOT), Optional.empty());
        }
    }
}
