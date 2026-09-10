package ca.teamdman.sfm.client.explorer.lazy;

import ca.teamdman.sfm.client.explorer.SFMChildPage;
import ca.teamdman.sfm.client.explorer.SFMChildRelationRepository;
import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.search.SFMTextMatchOptions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

class SFMExplorerMatchDomainTests {
    @Test void findAndFilterCanShareAnExactPredicateWithoutMixingDifferentModes() {
        var fixture = new Fixture();
        var literal = SFMTextMatchOptions.defaults();
        var fuzzy = literal.toggleFuzzy();
        var filterLiteral = fixture.loader.ensureFilterDomain(fixture.root, "word", literal).orElseThrow();
        var findLiteral = fixture.loader.ensureFinderDomain(fixture.root, "word", literal).orElseThrow();
        assertSame(filterLiteral, findLiteral);
        assertEquals(1, fixture.requests.size());
        var filterFuzzy = fixture.loader.ensureFilterDomain(fixture.root, "word", fuzzy).orElseThrow();
        assertEquals(2, fixture.requests.size());
        assertFalse(fixture.requests.get(0).cancellation().isCancelled(), "finder still owns literal work");
        fixture.complete(1, fuzzy);
        fixture.complete(0, literal);
        assertEquals(SFMLazyExplorerLoader.LoadDisposition.PUBLISHED, filterFuzzy.join());
        assertEquals(SFMLazyExplorerLoader.LoadDisposition.PUBLISHED, findLiteral.join());
        assertTrue(fixture.loader.filterProjection(Set.of(fixture.root), "word", Set.of(), literal).isPresent());
        assertTrue(fixture.loader.filterProjection(Set.of(fixture.root), "word", Set.of(), fuzzy).isPresent());
        assertTrue(fixture.loader.filterProjection(Set.of(fixture.root), "word", Set.of(), literal.withCase(true)).isEmpty());
    }

    @Test void wrongModeResponseFailsClosedAndDoesNotRetryEveryTick() {
        var fixture = new Fixture();
        var options = SFMTextMatchOptions.defaults();
        var first = fixture.loader.ensureFilterDomain(fixture.root, "word", options).orElseThrow();
        fixture.complete(0, SFMTextMatchOptions.legacyFuzzy());
        assertEquals(SFMLazyExplorerLoader.LoadDisposition.FAILED, first.join());
        for (int i = 0; i < 100; i++) assertSame(first,
                fixture.loader.ensureFilterDomain(fixture.root, "word", options).orElseThrow());
        assertEquals(1, fixture.requests.size());
        assertTrue(fixture.loader.filterProjection(Set.of(fixture.root), "word", Set.of(), options).isEmpty());
        fixture.loader.invalidateQueryDomains(fixture.root);
        var retry = fixture.loader.ensureFilterDomain(fixture.root, "word", options).orElseThrow();
        assertNotSame(first, retry);
        fixture.complete(1, options);
        assertEquals(SFMLazyExplorerLoader.LoadDisposition.PUBLISHED, retry.join());
    }

    @Test void changingCaseOrWordOptionsCancelsOnlySupersededLaneWork() {
        var fixture = new Fixture();
        var options = SFMTextMatchOptions.defaults();
        var old = fixture.loader.ensureFilterDomain(fixture.root, "word", options).orElseThrow();
        var replacementOptions = options.withWholeWord(true).withCase(true);
        var next = fixture.loader.ensureFilterDomain(fixture.root, "word", replacementOptions).orElseThrow();
        assertEquals(SFMLazyExplorerLoader.LoadDisposition.CANCELLED, old.join());
        fixture.complete(0, options);
        assertTrue(fixture.loader.filterProjection(Set.of(fixture.root), "word", Set.of(), options).isEmpty());
        fixture.complete(1, replacementOptions);
        assertEquals(SFMLazyExplorerLoader.LoadDisposition.PUBLISHED, next.join());
    }

    private static final class Fixture implements SFMExplorerFilterDomainResolver {
        final SFMPath root = SFMPath.parse("registry://match/root");
        final SFMExplorerEntry entry = SFMExplorerEntry.simple(root, "root", true, Optional.empty());
        final List<FilterDomainRequest> requests = new ArrayList<>();
        final List<CompletableFuture<FilterDomain>> responses = new ArrayList<>();
        final SFMLazyExplorerLoader loader;
        Fixture() {
            var registry = new SFMExplorerResolverRegistry();
            registry.register(this);
            loader = new SFMLazyExplorerLoader(registry, new SFMChildRelationRepository(), Runnable::run);
        }
        public String scheme() { return "registry"; }
        public long generation() { return 1; }
        public CompletableFuture<SFMExplorerEntry> describe(SFMPath path, SFMExplorerCancellationToken cancellation) {
            return CompletableFuture.completedFuture(entry);
        }
        public CompletableFuture<ChildPage> resolveChildren(ChildRequest request) {
            return CompletableFuture.completedFuture(new ChildPage(root, List.of(), Optional.empty(), 1, List.of(), 0));
        }
        public CompletableFuture<FilterDomain> resolveFilterDomain(FilterDomainRequest request) {
            requests.add(request);
            var response = new CompletableFuture<FilterDomain>();
            responses.add(response);
            return response;
        }
        void complete(int index, SFMTextMatchOptions options) {
            responses.get(index).complete(new FilterDomain(root, requests.get(index).query(), List.of(entry),
                    List.of(new SFMChildPage(root, List.of(), Optional.empty(), SFMChildPage.Completeness.COMPLETE, 1, List.of())),
                    Set.of(), 0, true, 1, List.of(), options));
        }
    }

    @Test void finderFailureDoesNotPoisonAnIndependentFilter() {
        var fixture = new Fixture();
        var options = SFMTextMatchOptions.defaults();
        var filter = fixture.loader.ensureFilterDomain(fixture.root, "filter", options).orElseThrow();
        var find = fixture.loader.ensureFinderDomain(fixture.root, "find", options.toggleFuzzy()).orElseThrow();
        fixture.responses.get(1).completeExceptionally(new IllegalStateException("finder test failure"));
        fixture.complete(0, options);
        assertEquals(SFMLazyExplorerLoader.LoadDisposition.FAILED, find.join());
        assertEquals(SFMLazyExplorerLoader.LoadDisposition.PUBLISHED, filter.join());
        assertTrue(fixture.loader.filterDomainFailure(fixture.root).isEmpty());
        assertTrue(fixture.loader.filterDomainFailure(fixture.root, "filter", options).isEmpty());
        assertTrue(fixture.loader.filterDomainFailure(fixture.root, "find", options.toggleFuzzy()).isPresent());
    }

    @Test void patternWhitespaceIsPartOfDomainIdentityAndSurvivesPublication() {
        var fixture = new Fixture();
        var regex = SFMTextMatchOptions.defaults().toggleRegex();
        var pending = fixture.loader.ensureFilterDomain(fixture.root, " word ", regex).orElseThrow();
        assertEquals(" word ", fixture.requests.get(0).query());
        fixture.complete(0, regex);
        assertEquals(SFMLazyExplorerLoader.LoadDisposition.PUBLISHED, pending.join());
        assertTrue(fixture.loader.filterProjection(Set.of(fixture.root), " word ", Set.of(), regex).isPresent());
        assertTrue(fixture.loader.filterProjection(Set.of(fixture.root), "word", Set.of(), regex).isEmpty());
        var session = new SFMExplorerSession(new ca.teamdman.sfm.client.explorer.SFMExplorerId("whitespace"), fixture.root,
                new ca.teamdman.sfm.client.explorer.SFMSelectionRepository());
        session.setFilterOptions(regex);
        session.setFilterQuery(" word ");
        long generation = session.beginFinderQuery(" word ", regex);
        assertTrue(session.publishFinderResults(generation, " word ", List.of(), true, List.of()));
        assertEquals(" word ", session.snapshot().settings().filterQuery());
        assertEquals(" word ", session.snapshot().finder().query());
    }
}
