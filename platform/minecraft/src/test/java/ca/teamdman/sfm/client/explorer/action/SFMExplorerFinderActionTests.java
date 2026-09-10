package ca.teamdman.sfm.client.explorer.action;

import ca.teamdman.sfm.client.explorer.SFMChildEdge;
import ca.teamdman.sfm.client.explorer.SFMChildPage;
import ca.teamdman.sfm.client.explorer.SFMChildRelationRepository;
import ca.teamdman.sfm.client.explorer.SFMEntitySelector;
import ca.teamdman.sfm.client.explorer.SFMExplorerId;
import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.explorer.SFMSelectionRepository;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerCancellationToken;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerEntry;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerFilterDomainResolver;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerResolver;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerResolverRegistry;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerSession;
import ca.teamdman.sfm.client.explorer.lazy.SFMLazyExplorerLoader;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SFMExplorerFinderActionTests {
    private static final SFMPath ROOT = SFMPath.parse("registry://test/root");
    private static final SFMPath ALPHA = SFMPath.parse("registry://test/root/alpha");
    private static final SFMPath BETA = SFMPath.parse("registry://test/root/beta");

    @Test
    public void asynchronousFinderDoesNotPruneTheTreeAndNavigationWrapsBothWays() {
        PendingFinderResolver resolver = new PendingFinderResolver();
        SFMExplorerResolverRegistry resolvers = new SFMExplorerResolverRegistry();
        resolvers.register(resolver);
        SFMLazyExplorerLoader loader = new SFMLazyExplorerLoader(
                resolvers,
                new SFMChildRelationRepository()
        );
        loader.openRoot(ROOT).join();
        loader.refresh(ROOT, 16).completion().join();
        var relationBefore = loader.relationSnapshot();

        SFMExplorerSession session = new SFMExplorerSession(
                new SFMExplorerId("finder-action"),
                ROOT,
                new SFMSelectionRepository()
        );
        session.setFilterQuery("existing-filter");
        SFMExplorerRepository.Explorer explorer = new SFMExplorerRepository.Explorer(
                session,
                loader,
                SFMExplorerPathPolicy.schemes(Set.of("registry")),
                16
        );
        SFMExplorerRepository repository = new SFMExplorerRepository();
        repository.register(explorer, true);
        SFMExplorerActionEngine engine = new SFMExplorerActionEngine(repository, ignored -> {
            throw new AssertionError("finder must not create an Explorer");
        });
        SFMEntitySelector selector = SFMEntitySelector.exact(
                SFMEntitySelector.Domain.EXPLORER,
                explorer.id().value()
        );

        SFMExplorerActionResult started = engine.execute(request(
                selector,
                new SFMExplorerActionRequest.FindSet("entry",
                        ca.teamdman.sfm.client.search.SFMTextMatchOptions.defaults().withCase(true))
        ));
        assertEquals(SFMExplorerActionResult.Status.SUCCEEDED, started.status());
        assertEquals(SFMExplorerSession.FinderStatus.PENDING, started.targets().get(0).snapshot().finder().status());
        assertTrue(session.snapshot().finder().options().matchCase());
        assertFalse(session.snapshot().settings().filterOptions().matchCase());
        assertEquals("existing-filter", session.snapshot().settings().filterQuery());
        assertEquals(relationBefore.relation().edges(), loader.relationSnapshot().relation().edges());
        assertEquals(relationBefore.pageStates(), loader.relationSnapshot().pageStates());
        assertTrue(session.snapshot().expanded().isEmpty(), "finding must not force projection expansion");

        resolver.publish(Set.of(BETA, ALPHA));
        assertEquals(SFMExplorerSession.FinderStatus.READY, session.snapshot().finder().status());
        assertEquals(List.of(ALPHA, BETA), session.snapshot().finder().matches());
        assertEquals("existing-filter", session.snapshot().settings().filterQuery());
        assertEquals(relationBefore.relation().edges(), loader.relationSnapshot().relation().edges(),
                "query-specific finder publication must not mutate the ordinary relation");

        engine.execute(request(selector, new SFMExplorerActionRequest.FindNext()));
        assertEquals(Optional.of(ALPHA), session.snapshot().navigationCursor());
        engine.execute(request(selector, new SFMExplorerActionRequest.FindNext()));
        assertEquals(Optional.of(BETA), session.snapshot().navigationCursor());
        engine.execute(request(selector, new SFMExplorerActionRequest.FindNext()));
        assertEquals(Optional.of(ALPHA), session.snapshot().navigationCursor(), "next must wrap");
        engine.execute(request(selector, new SFMExplorerActionRequest.FindPrevious()));
        assertEquals(Optional.of(BETA), session.snapshot().navigationCursor(), "previous must wrap");
        assertEquals("existing-filter", session.snapshot().settings().filterQuery(),
                "race-safe reveal must restore the unrelated projection filter");

        SFMExplorerActionResult cleared = engine.execute(request(
                selector,
                new SFMExplorerActionRequest.FindClear()
        ));
        assertEquals(SFMExplorerSession.FinderStatus.CLEARED, cleared.targets().get(0).snapshot().finder().status());
        assertEquals("existing-filter", session.snapshot().settings().filterQuery());
    }

    @Test
    public void unsupportedFinderDomainRetainsTypedFailureWithoutChangingTheFilter() {
        SFMExplorerResolver resolver = new PlainResolver();
        SFMExplorerResolverRegistry resolvers = new SFMExplorerResolverRegistry();
        resolvers.register(resolver);
        SFMLazyExplorerLoader loader = new SFMLazyExplorerLoader(
                resolvers,
                new SFMChildRelationRepository()
        );
        SFMExplorerSession session = new SFMExplorerSession(
                new SFMExplorerId("unsupported-finder"),
                ROOT,
                new SFMSelectionRepository()
        );
        session.setFilterQuery("unchanged");
        SFMExplorerRepository.Explorer explorer = new SFMExplorerRepository.Explorer(
                session,
                loader,
                SFMExplorerPathPolicy.schemes(Set.of("registry")),
                16
        );
        SFMExplorerRepository repository = new SFMExplorerRepository();
        repository.register(explorer, true);
        SFMExplorerActionEngine engine = new SFMExplorerActionEngine(repository, ignored -> explorer);

        engine.execute(request(
                SFMEntitySelector.exact(SFMEntitySelector.Domain.EXPLORER, explorer.id().value()),
                new SFMExplorerActionRequest.FindSet("entry")
        ));

        assertEquals(SFMExplorerSession.FinderStatus.FAILED, session.snapshot().finder().status());
        assertEquals(
                List.of(SFMExplorerSession.FinderDiagnosticCode.SEARCH_FAILED),
                session.snapshot().finder().diagnostics().stream()
                        .map(SFMExplorerSession.FinderDiagnostic::code)
                        .toList()
        );
        assertEquals("unchanged", session.snapshot().settings().filterQuery());
        assertFalse(session.snapshot().finder().diagnostics().get(0).message().isBlank());
    }

    @Test
    public void failedFinderIncludesTheUnderlyingDiagnostic() {
        var resolver = new PendingFinderResolver();
        var registry = new SFMExplorerResolverRegistry();
        registry.register(resolver);
        var loader = new SFMLazyExplorerLoader(registry, new SFMChildRelationRepository());
        var session = new SFMExplorerSession(new SFMExplorerId("failed-finder"), ROOT, new SFMSelectionRepository());
        var explorer = new SFMExplorerRepository.Explorer(session, loader, SFMExplorerPathPolicy.schemes(Set.of("registry")), 16);
        var repository = new SFMExplorerRepository();
        repository.register(explorer, true);
        var engine = new SFMExplorerActionEngine(repository, ignored -> explorer);
        engine.execute(request(SFMEntitySelector.exact(SFMEntitySelector.Domain.EXPLORER, explorer.id().value()),
                new SFMExplorerActionRequest.FindSet("entry")));
        resolver.completions.get("entry").completeExceptionally(new IllegalArgumentException("fixture-specific source limit"));
        assertEquals(SFMExplorerSession.FinderStatus.FAILED, session.snapshot().finder().status());
        assertTrue(session.snapshot().finder().diagnostics().get(0).message().contains("fixture-specific source limit"));
    }

    @Test
    public void finderSelectionPublishesOnlyAfterTheRevealChainMaterializes() {
        PendingFinderResolver resolver = new PendingFinderResolver();
        SFMExplorerResolverRegistry resolvers = new SFMExplorerResolverRegistry();
        resolvers.register(resolver);
        SFMLazyExplorerLoader loader = new SFMLazyExplorerLoader(
                resolvers,
                new SFMChildRelationRepository()
        );
        loader.openRoot(ROOT).join();
        SFMExplorerSession session = new SFMExplorerSession(
                new SFMExplorerId("finder-reveal"),
                ROOT,
                new SFMSelectionRepository()
        );
        SFMExplorerRepository.Explorer explorer = new SFMExplorerRepository.Explorer(
                session,
                loader,
                SFMExplorerPathPolicy.schemes(Set.of("registry")),
                16
        );
        SFMExplorerRepository repository = new SFMExplorerRepository();
        repository.register(explorer, true);
        SFMExplorerActionEngine engine = new SFMExplorerActionEngine(repository, ignored -> explorer);
        SFMEntitySelector selector = SFMEntitySelector.exact(
                SFMEntitySelector.Domain.EXPLORER,
                explorer.id().value()
        );

        engine.execute(request(selector, new SFMExplorerActionRequest.FindSet("alpha")));
        resolver.publish(Set.of(ALPHA));
        resolver.gateNextChildren();
        engine.execute(request(selector, new SFMExplorerActionRequest.FindNext()));

        assertEquals(Optional.of(ALPHA), session.snapshot().finder().currentMatch());
        assertTrue(session.snapshot().navigationCursor().isEmpty(),
                "the selected row must wait for its ordinary ancestor chain");
        assertEquals(1, session.activeRequestCount());

        resolver.publishChildren();
        assertEquals(Optional.of(ALPHA), session.snapshot().navigationCursor());
        assertEquals(0, session.activeRequestCount());
    }

    @Test
    public void finderAndVisibleFilterKeepIndependentPublishedQueryLanes() {
        PendingFinderResolver resolver = new PendingFinderResolver();
        SFMExplorerResolverRegistry resolvers = new SFMExplorerResolverRegistry();
        resolvers.register(resolver);
        SFMLazyExplorerLoader loader = new SFMLazyExplorerLoader(
                resolvers, new SFMChildRelationRepository()
        );
        loader.openRoot(ROOT).join();
        SFMExplorerSession session = new SFMExplorerSession(
                new SFMExplorerId("finder-filter-independence"), ROOT, new SFMSelectionRepository()
        );
        session.setFilterQuery("alpha-filter");
        CompletableFuture<SFMLazyExplorerLoader.LoadDisposition> visibleFilter =
                loader.ensureFilterDomain(ROOT, "alpha-filter").orElseThrow();
        resolver.publish("alpha-filter", Set.of(ALPHA));
        assertEquals(SFMLazyExplorerLoader.LoadDisposition.PUBLISHED, visibleFilter.join());

        SFMExplorerRepository.Explorer explorer = new SFMExplorerRepository.Explorer(
                session, loader, SFMExplorerPathPolicy.schemes(Set.of("registry")), 16
        );
        SFMExplorerRepository repository = new SFMExplorerRepository();
        repository.register(explorer, true);
        SFMExplorerActionEngine engine = new SFMExplorerActionEngine(repository, ignored -> explorer);
        SFMEntitySelector selector = SFMEntitySelector.exact(
                SFMEntitySelector.Domain.EXPLORER, explorer.id().value()
        );

        engine.execute(request(selector, new SFMExplorerActionRequest.FindSet("beta-find")));
        assertEquals(Set.of(ALPHA), loader.filterProjection(Set.of(ROOT), "alpha-filter", Set.of())
                .orElseThrow().matchedPaths());
        resolver.publish("beta-find", Set.of(BETA));

        assertEquals(SFMExplorerSession.FinderStatus.READY, session.snapshot().finder().status());
        assertEquals(List.of(BETA), session.snapshot().finder().matches());
        assertEquals("alpha-filter", session.snapshot().settings().filterQuery());
        assertEquals(Set.of(ALPHA), loader.filterProjection(Set.of(ROOT), "alpha-filter", Set.of())
                .orElseThrow().matchedPaths());
        assertEquals(Set.of(BETA), loader.filterProjection(Set.of(ROOT), "beta-find", Set.of())
                .orElseThrow().matchedPaths());
    }

    @Test
    public void currentFilterRowsBeyondOrdinaryPaginationCanExpandButOldQueriesDoNotAuthorizeNodes() {
        PendingFinderResolver resolver = new PendingFinderResolver();
        SFMExplorerResolverRegistry resolvers = new SFMExplorerResolverRegistry();
        resolvers.register(resolver);
        SFMLazyExplorerLoader loader = new SFMLazyExplorerLoader(resolvers, new SFMChildRelationRepository());
        loader.openRoot(ROOT).join(); // Deliberately do not materialize ROOT's ordinary children.
        SFMExplorerSession session = new SFMExplorerSession(new SFMExplorerId("filter-node"), ROOT, new SFMSelectionRepository());
        session.setFilterQuery("alpha");
        var published = loader.ensureFilterDomain(ROOT, "alpha", session.snapshot().settings().filterOptions()).orElseThrow();
        resolver.publish("alpha", Set.of(ALPHA));
        published.join();
        assertTrue(loader.relationSnapshot().relation().childrenOf(ROOT).isEmpty());
        var explorer = new SFMExplorerRepository.Explorer(session, loader, SFMExplorerPathPolicy.schemes(Set.of("registry")), 16);
        var repository = new SFMExplorerRepository();
        repository.register(explorer, true);
        var engine = new SFMExplorerActionEngine(repository, ignored -> explorer);
        var selector = SFMEntitySelector.exact(SFMEntitySelector.Domain.EXPLORER, explorer.id().value());
        var expanded = engine.execute(request(selector, new SFMExplorerActionRequest.NodeExpand(ALPHA, 16)));
        assertEquals(SFMExplorerActionResult.Status.SUCCEEDED, expanded.status());
        assertTrue(session.snapshot().expanded().contains(ALPHA));
        assertTrue(loader.relationSnapshot().relation().childrenOf(ROOT).isEmpty(), "Do not pollute ordinary pagination");
        session.setFilterOptions(ca.teamdman.sfm.client.search.SFMTextMatchOptions.legacyFuzzy());
        assertFalse(engine.execute(request(selector, new SFMExplorerActionRequest.NodeExpand(BETA, 16))).status()
                == SFMExplorerActionResult.Status.SUCCEEDED,
                "same text with different options must not borrow the literal domain's authority");
        session.setFilterQuery("unpublished-new-query");
        var rejected = engine.execute(request(selector, new SFMExplorerActionRequest.NodeExpand(BETA, 16)));
        assertFalse(rejected.status() == SFMExplorerActionResult.Status.SUCCEEDED,
                "The previous query's index must not authorize an unknown node in a new query");
    }

    @Test
    public void explicitlyExpandedContextGroupsRetainTheirChildrenUnderAFilter() {
        SFMPath group = SFMPath.parse("registry://test/root/alpha/remaining");
        SFMPath gap = SFMPath.parse("registry://test/root/alpha/remaining/gap");
        PendingFinderResolver resolver = new PendingFinderResolver() {
            @Override public CompletableFuture<ChildPage> resolveChildren(ChildRequest request) {
                List<SFMExplorerEntry> children = request.parent().equals(ALPHA)
                        ? List.of(SFMExplorerEntry.simple(group, "Unreviewed surface", true, Optional.empty()))
                        : request.parent().equals(group)
                        ? List.of(SFMExplorerEntry.simple(gap, "Unreviewed bytes", false, Optional.empty())) : List.of();
                return CompletableFuture.completedFuture(new ChildPage(request.parent(), children,
                        Optional.empty(), generation(), List.of(), children.size()));
            }
        };
        var registry = new SFMExplorerResolverRegistry();
        registry.register(resolver);
        var loader = new SFMLazyExplorerLoader(registry, new SFMChildRelationRepository());
        loader.openRoot(ROOT).join();
        var filter = loader.ensureFilterDomain(ROOT, "alpha").orElseThrow();
        resolver.publish("alpha", Set.of(ALPHA));
        filter.join();
        loader.refresh(ALPHA, 16).completion().join();
        loader.refresh(group, 16).completion().join();
        var collapsed = loader.filterProjection(Set.of(ROOT), "alpha", Set.of(ALPHA)).orElseThrow();
        assertTrue(collapsed.relations().relation().childrenOf(group).isEmpty());
        var expanded = loader.filterProjection(Set.of(ROOT), "alpha", Set.of(ALPHA, group)).orElseThrow();
        assertEquals(Set.of(gap), expanded.relations().relation().childrenOf(group));
        var session = new SFMExplorerSession(new SFMExplorerId("nested-context"), ROOT, new SFMSelectionRepository());
        session.setFilterOptions(ca.teamdman.sfm.client.search.SFMTextMatchOptions.legacyFuzzy());
        session.setFilterQuery("alpha");
        session.expand(ALPHA);
        var explorer = new SFMExplorerRepository.Explorer(session, loader, SFMExplorerPathPolicy.schemes(Set.of("registry")), 16);
        var repository = new SFMExplorerRepository();
        repository.register(explorer, true);
        var engine = new SFMExplorerActionEngine(repository, ignored -> explorer);
        var selector = SFMEntitySelector.exact(SFMEntitySelector.Domain.EXPLORER, explorer.id().value());
        assertEquals(SFMExplorerActionResult.Status.SUCCEEDED,
                engine.execute(request(selector, new SFMExplorerActionRequest.NodeExpand(group, 16))).status(),
                "current filtered contextual rows must authorize their own expand action");
        var projection = ca.teamdman.sfm.client.explorer.lazy.SFMExplorerProjection.project(
                session.snapshot(), expanded.relations(), expanded.entries());
        assertTrue(projection.rows().stream().anyMatch(row -> row.path().equals(gap)),
                "A deliberately expanded context group must reveal its ordinary child under the filter");
        assertTrue(projection.rows().stream().anyMatch(row -> row.path().equals(group) && row.expanded()));
        session.collapse(group);
        var hidden = ca.teamdman.sfm.client.explorer.lazy.SFMExplorerProjection.project(
                session.snapshot(), collapsed.relations(), collapsed.entries());
        assertFalse(hidden.rows().stream().anyMatch(row -> row.path().equals(gap)));
        assertTrue(loader.relationSnapshot().relation().childrenOf(ROOT).isEmpty());
        assertTrue(loader.filterProjection(Set.of(ROOT), "other", Set.of(ALPHA, group)).isEmpty());
    }

    private static SFMExplorerActionRequest request(
            SFMEntitySelector selector,
            SFMExplorerActionRequest.Operation operation
    ) {
        return new SFMExplorerActionRequest(
                selector,
                operation,
                SFMExplorerActionRequest.IfNoMatch.FAIL
        );
    }

    private static class PlainResolver implements SFMExplorerResolver {
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
            return CompletableFuture.completedFuture(entry(path));
        }

        @Override
        public CompletableFuture<ChildPage> resolveChildren(ChildRequest request) {
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

        protected static SFMExplorerEntry entry(SFMPath path) {
            return SFMExplorerEntry.simple(
                    path,
                    path.equals(ROOT) ? "Root" : path.segments().get(path.segments().size() - 1),
                    path.equals(ROOT),
                    Optional.empty()
            );
        }
    }

    private static class PendingFinderResolver extends PlainResolver
            implements SFMExplorerFilterDomainResolver {
        private final java.util.Map<String, FilterDomainRequest> requests = new java.util.HashMap<>();
        private final java.util.Map<String, CompletableFuture<FilterDomain>> completions =
                new java.util.HashMap<>();
        private String latestQuery;
        private ChildRequest gatedChildRequest;
        private CompletableFuture<ChildPage> gatedChildCompletion;

        @Override
        public CompletableFuture<ChildPage> resolveChildren(ChildRequest request) {
            if (gatedChildCompletion == null) return super.resolveChildren(request);
            gatedChildRequest = request;
            return gatedChildCompletion;
        }

        @Override
        public CompletableFuture<FilterDomain> resolveFilterDomain(FilterDomainRequest request) {
            latestQuery = request.query();
            requests.put(request.query(), request);
            CompletableFuture<FilterDomain> completion = new CompletableFuture<>();
            completions.put(request.query(), completion);
            return completion;
        }

        private void publish(Set<SFMPath> matches) {
            publish(latestQuery, matches);
        }

        private void publish(String query, Set<SFMPath> matches) {
            FilterDomainRequest request = requests.get(query);
            List<SFMExplorerEntry> entries = List.of(entry(ROOT), entry(ALPHA), entry(BETA));
            completions.get(query).complete(new FilterDomain(
                    ROOT,
                    request.query(),
                    entries,
                    List.of(new SFMChildPage(
                            ROOT,
                            List.of(new SFMChildEdge(ROOT, ALPHA), new SFMChildEdge(ROOT, BETA)),
                            Optional.empty(),
                            SFMChildPage.Completeness.COMPLETE,
                            generation(),
                            List.of()
                    )),
                    matches,
                    matches.size(),
                    true,
                    generation(),
                    List.of(),
                    request.options()
            ));
        }

        private void gateNextChildren() {
            gatedChildCompletion = new CompletableFuture<>();
        }

        private void publishChildren() {
            CompletableFuture<ChildPage> completion = gatedChildCompletion;
            ChildRequest request = gatedChildRequest;
            gatedChildCompletion = null;
            gatedChildRequest = null;
            completion.complete(super.resolveChildren(request).join());
        }
    }
}
