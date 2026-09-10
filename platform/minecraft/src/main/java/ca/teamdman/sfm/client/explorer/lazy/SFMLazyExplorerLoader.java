package ca.teamdman.sfm.client.explorer.lazy;

import ca.teamdman.sfm.client.explorer.SFMChildEdge;
import ca.teamdman.sfm.client.explorer.SFMChildPage;
import ca.teamdman.sfm.client.explorer.SFMChildRelationRepository;
import ca.teamdman.sfm.client.explorer.SFMChildRelationRevision;
import ca.teamdman.sfm.client.explorer.SFMPath;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

/**
 * Coordinates asynchronous resolvers with the atomic child-relation store.
 * Resolver results become visible only after their captured request publishes.
 */
public final class SFMLazyExplorerLoader {
    private enum QueryLane {
        FILTER,
        FINDER
    }

    private record FilterDomainKey(SFMPath root, String query, long resolverGeneration,
                                   ca.teamdman.sfm.client.search.SFMTextMatchOptions options) {
        private FilterDomainKey {
            Objects.requireNonNull(root, "root");
            Objects.requireNonNull(options, "options");
            query = Objects.requireNonNull(query, "query");
            if (query.isEmpty()) throw new IllegalArgumentException("Filter-domain query must not be blank");
            if (resolverGeneration < 0) {
                throw new IllegalArgumentException("Resolver generation must not be negative");
            }
        }
    }

    public record ProjectionGeneration(
            long relationRevision,
            long relationStatusGeneration,
            long entryGeneration,
            long filterGeneration
    ) {
    }

    /** Immutable query-specific projection input, separate from the ordinary lazy relation. */
    public record FilterProjection(
            String query,
            SFMChildRelationRepository.Snapshot relations,
            Map<SFMPath, SFMExplorerEntry> entries,
            java.util.Set<SFMPath> matchedPaths,
            int totalMatchCount,
            boolean complete,
            List<String> diagnostics,
            long resolverGeneration,
            Map<SFMPath, SFMExplorerEntryMatch> matchEvidence
    ) {
        public FilterProjection {
            query = Objects.requireNonNull(query, "query");
            if (query.isEmpty()) throw new IllegalArgumentException("Filter query must not be blank");
            Objects.requireNonNull(relations, "relations");
            entries = Collections.unmodifiableMap(new TreeMap<>(entries));
            matchEvidence = Map.copyOf(matchEvidence);
            matchedPaths = Collections.unmodifiableSet(new TreeSet<>(matchedPaths));
            if (totalMatchCount < matchedPaths.size()) {
                throw new IllegalArgumentException("Total matches cannot be below published matches");
            }
            diagnostics = List.copyOf(diagnostics);
            if (resolverGeneration < 0) throw new IllegalArgumentException("Resolver generation must not be negative");
        }

        public FilterProjection(String query, SFMChildRelationRepository.Snapshot relations,
                                Map<SFMPath, SFMExplorerEntry> entries, java.util.Set<SFMPath> matchedPaths,
                                int totalMatchCount, boolean complete, List<String> diagnostics, long resolverGeneration) {
            this(query.strip(), relations, entries, matchedPaths, totalMatchCount, complete, diagnostics,
                    resolverGeneration, Map.of());
        }
    }

    public enum LoadDisposition {
        PUBLISHED,
        STALE,
        FAILED,
        CANCELLED
    }

    public record RequestEvidence(
            long relationRequestId,
            SFMChildRelationRepository.RequestMode mode,
            SFMPath parent,
            String resolverScheme,
            long resolverGeneration,
            Optional<String> continuation,
            int pageSize
    ) {
        public RequestEvidence {
            if (relationRequestId <= 0) throw new IllegalArgumentException("Request id must be positive");
            Objects.requireNonNull(mode, "mode");
            Objects.requireNonNull(parent, "parent");
            Objects.requireNonNull(resolverScheme, "resolverScheme");
            if (resolverGeneration < 0) {
                throw new IllegalArgumentException("Resolver generation must not be negative");
            }
            Objects.requireNonNull(continuation, "continuation");
            if (pageSize <= 0) throw new IllegalArgumentException("Page size must be positive");
        }
    }

    public record LoadResult(
            RequestEvidence evidence,
            LoadDisposition disposition,
            SFMChildRelationRepository.Snapshot relationSnapshot,
            Optional<SFMExplorerResolver.ChildPage> resolverPage,
            Optional<String> diagnostic
    ) {
        public LoadResult {
            Objects.requireNonNull(evidence, "evidence");
            Objects.requireNonNull(disposition, "disposition");
            Objects.requireNonNull(relationSnapshot, "relationSnapshot");
            Objects.requireNonNull(resolverPage, "resolverPage");
            Objects.requireNonNull(diagnostic, "diagnostic");
        }
    }

    public final class LoadHandle {
        private final SFMChildRelationRepository.RefreshTicket ticket;
        private final RequestEvidence evidence;
        private final SFMExplorerCancellationToken cancellation;
        private final CompletableFuture<LoadResult> completion;

        private LoadHandle(
                SFMChildRelationRepository.RefreshTicket ticket,
                RequestEvidence evidence,
                SFMExplorerCancellationToken cancellation,
                CompletableFuture<LoadResult> completion
        ) {
            this.ticket = ticket;
            this.evidence = evidence;
            this.cancellation = cancellation;
            this.completion = completion;
        }

        public RequestEvidence evidence() {
            return evidence;
        }

        public CompletableFuture<LoadResult> completion() {
            return completion;
        }

        public synchronized boolean cancel() {
            if (completion.isDone()) return false;
            boolean tokenChanged = cancellation.cancel();
            boolean relationChanged = relations.cancel(ticket);
            completion.complete(new LoadResult(
                    evidence,
                    LoadDisposition.CANCELLED,
                    relations.snapshot(),
                    Optional.empty(),
                    Optional.of("request cancelled")
            ));
            return tokenChanged || relationChanged;
        }
    }

    private final SFMExplorerResolverRegistry resolvers;
    private final SFMChildRelationRepository relations;
    private final Executor publicationExecutor;
    private final Map<SFMPath, SFMExplorerEntry> entries = new TreeMap<>();
    private final Map<FilterDomainKey, CompletableFuture<LoadDisposition>> filterDomains = new HashMap<>();
    private final java.util.Set<FilterDomainKey> activeFilterDomains = new HashSet<>();
    private final java.util.Set<FilterDomainKey> filterLaneDomains = new HashSet<>();
    private final java.util.Set<FilterDomainKey> finderLaneDomains = new HashSet<>();
    private final Map<FilterDomainKey, SFMExplorerCancellationToken> filterDomainCancellations = new HashMap<>();
    private final Map<FilterDomainKey, FilterProjection> publishedFilterDomains = new HashMap<>();
    private final Map<FilterDomainKey, String> filterDomainFailures = new HashMap<>();
    private long entryGeneration;
    private long filterGeneration;
    private final java.util.concurrent.atomic.AtomicLong filterProjectionRequests =
            new java.util.concurrent.atomic.AtomicLong();

    /** Work evidence: cache hits in a panel must not enter domain composition. */
    public long filterProjectionRequestCount() {
        return filterProjectionRequests.get();
    }

    /** Live complete-domain work matching this query and resolver revision. */
    public boolean hasPendingFilterDomain(java.util.Set<SFMPath> roots, String query) {
        return hasPendingFilterDomain(roots, query.strip(), ca.teamdman.sfm.client.search.SFMTextMatchOptions.legacyFuzzy());
    }

    public boolean hasPendingFilterDomain(java.util.Set<SFMPath> roots, String query,
            ca.teamdman.sfm.client.search.SFMTextMatchOptions options) {
        String normalizedQuery = Objects.requireNonNull(query, "query");
        synchronized (filterDomains) {
            return roots.stream().anyMatch(root -> activeFilterDomains.contains(
                    new FilterDomainKey(root, normalizedQuery, resolvers.require(root).generation(), options)));
        }
    }

    public SFMLazyExplorerLoader(
            SFMExplorerResolverRegistry resolvers,
            SFMChildRelationRepository relations
    ) {
        this(resolvers, relations, Runnable::run);
    }

    /**
     * @param publicationExecutor owns relation/cache publication; production
     *                            supplies Minecraft's client executor.
     */
    public SFMLazyExplorerLoader(
            SFMExplorerResolverRegistry resolvers,
            SFMChildRelationRepository relations,
            Executor publicationExecutor
    ) {
        this.resolvers = Objects.requireNonNull(resolvers, "resolvers");
        this.relations = Objects.requireNonNull(relations, "relations");
        this.publicationExecutor = Objects.requireNonNull(publicationExecutor, "publicationExecutor");
    }

    /** Describes one root only; this method never asks for its children. */
    public CompletableFuture<SFMExplorerEntry> openRoot(SFMPath root) {
        Objects.requireNonNull(root, "root");
        SFMExplorerResolver resolver = resolvers.require(root);
        SFMExplorerCancellationToken cancellation = new SFMExplorerCancellationToken();
        return resolver.describe(root, cancellation).thenApplyAsync(entry -> {
            if (!entry.path().equals(root)) {
                throw new IllegalStateException("Resolver described a different root path");
            }
            synchronized (entries) {
                entries.put(root, entry);
                entryGeneration++;
            }
            return entry;
        }, publicationExecutor);
    }

    public LoadHandle refresh(SFMPath parent, int pageSize) {
        Objects.requireNonNull(parent, "parent");
        invalidateQueryDomains(parent);
        SFMExplorerResolver resolver = resolvers.require(parent);
        long resolverGeneration = resolver.generation();
        SFMChildRelationRepository.RefreshTicket ticket = relations.beginRefresh(
                java.util.Set.of(parent),
                resolverGeneration
        );
        return start(ticket, resolver, Optional.empty(), pageSize);
    }

    public Optional<LoadHandle> nextPage(SFMPath parent, int pageSize) {
        Objects.requireNonNull(parent, "parent");
        SFMChildRelationRepository.PageState state = relations.snapshot().pageStates().get(parent);
        if (state == null || state.continuation().isEmpty()) return Optional.empty();
        String continuation = state.continuation().orElseThrow();
        Optional<SFMChildRelationRepository.RefreshTicket> ticket = relations.beginNextPage(
                parent,
                continuation
        );
        if (ticket.isEmpty()) return Optional.empty();
        SFMExplorerResolver resolver = resolvers.require(parent);
        return Optional.of(start(ticket.orElseThrow(), resolver, Optional.of(continuation), pageSize));
    }

    public Optional<SFMExplorerEntry> entry(SFMPath path) {
        synchronized (entries) {
            return Optional.ofNullable(entries.get(Objects.requireNonNull(path, "path")));
        }
    }

    public Map<SFMPath, SFMExplorerEntry> entrySnapshot() {
        synchronized (entries) {
            return Collections.unmodifiableMap(new TreeMap<>(entries));
        }
    }

    public SFMChildRelationRepository.Snapshot relationSnapshot() {
        return relations.snapshot();
    }

    /** Initial display may reuse current children; explicit refresh still replaces them. */
    public boolean hasCurrentMaterializedChildren(SFMPath parent) {
        Objects.requireNonNull(parent, "parent");
        SFMChildRelationRepository.PageState state = relations.snapshot().pageStates().get(parent);
        return state != null
                && state.materialization() == SFMChildRelationRepository.PageState.Materialization.MATERIALIZED
                && state.resolverGeneration() == resolvers.require(parent).generation();
    }

    /** Parents currently resolving away from the publication thread. */
    public java.util.Set<SFMPath> activeParents() {
        TreeSet<SFMPath> answer = new TreeSet<>(relations.activeParents());
        synchronized (filterDomains) {
            activeFilterDomains.stream().map(FilterDomainKey::root).forEach(answer::add);
        }
        return Collections.unmodifiableSet(answer);
    }

    /** Latest bounded complete-filter-domain failure for one root, if any. */
    public Optional<String> filterDomainFailure(SFMPath root) {
        synchronized (filterDomains) {
            Objects.requireNonNull(root, "root");
            return filterLaneDomains.stream().filter(key -> key.root().equals(root))
                    .map(filterDomainFailures::get).filter(Objects::nonNull).findFirst();
        }
    }

    public Optional<String> filterDomainFailure(SFMPath root, String query,
            ca.teamdman.sfm.client.search.SFMTextMatchOptions options) {
        synchronized (filterDomains) {
            return filterDomainFailures.entrySet().stream()
                    .filter(entry -> entry.getKey().root().equals(root)
                            && entry.getKey().query().equals(query)
                            && entry.getKey().options().equals(options))
                    .map(Map.Entry::getValue).findFirst();
        }
    }

    /**
     * Returns an exact published query projection for every supplied root.
     * Ordinary relation state is merged only for immediate children of direct
     * matches the user explicitly expanded, preserving contextual children
     * without contaminating the unfiltered lazy tree.
     */
    public Optional<FilterProjection> filterProjection(
            java.util.Set<SFMPath> roots,
            String query,
            java.util.Set<SFMPath> expanded
    ) {
        return filterProjection(roots, query.strip(), expanded, ca.teamdman.sfm.client.search.SFMTextMatchOptions.legacyFuzzy());
    }

    public Optional<FilterProjection> filterProjection(java.util.Set<SFMPath> roots, String query,
            java.util.Set<SFMPath> expanded, ca.teamdman.sfm.client.search.SFMTextMatchOptions options) {
        filterProjectionRequests.incrementAndGet();
        roots = java.util.Set.copyOf(Objects.requireNonNull(roots, "roots"));
        query = Objects.requireNonNull(query, "query");
        expanded = java.util.Set.copyOf(Objects.requireNonNull(expanded, "expanded"));
        if (roots.isEmpty() || query.isEmpty()) return Optional.empty();

        ArrayList<FilterProjection> projections = new ArrayList<>();
        long observedFilterGeneration;
        synchronized (filterDomains) {
            for (SFMPath root : roots.stream().sorted().toList()) {
                long resolverGeneration = resolvers.require(root).generation();
                FilterProjection projection = publishedFilterDomains.get(
                        new FilterDomainKey(root, query, resolverGeneration, options)
                );
                if (projection == null) {
                    return Optional.empty();
                }
                projections.add(projection);
            }
            observedFilterGeneration = filterGeneration;
        }

        TreeMap<SFMPath, SFMExplorerEntry> mergedEntries = new TreeMap<>();
        HashMap<SFMPath, SFMExplorerEntryMatch> mergedEvidence = new HashMap<>();
        TreeSet<SFMChildEdge> mergedEdges = new TreeSet<>();
        TreeMap<SFMPath, SFMChildRelationRepository.PageState> mergedStates = new TreeMap<>();
        TreeSet<SFMPath> matches = new TreeSet<>();
        ArrayList<String> diagnostics = new ArrayList<>();
        int totalMatches = 0;
        boolean complete = true;
        long resolverGeneration = 0;
        for (FilterProjection projection : projections) {
            mergedEntries.putAll(projection.entries());
            mergedEvidence.putAll(projection.matchEvidence());
            mergedEdges.addAll(projection.relations().relation().edges());
            mergedStates.putAll(projection.relations().pageStates());
            matches.addAll(projection.matchedPaths());
            diagnostics.addAll(projection.diagnostics());
            totalMatches += projection.totalMatchCount();
            complete &= projection.complete();
            resolverGeneration = Math.max(resolverGeneration, projection.resolverGeneration());
        }

        SFMChildRelationRepository.Snapshot ordinary = relationSnapshot();
        Map<SFMPath, SFMExplorerEntry> ordinaryEntries = entrySnapshot();
        // A matching file may contain contextual groups (before/after, remaining
        // regions) whose own labels do not match. Follow only explicitly expanded
        // descendants; never eagerly materialize an entire matching subtree.
        java.util.ArrayDeque<SFMPath> pending = new java.util.ArrayDeque<>(
                expanded.stream().filter(matches::contains).sorted().toList());
        java.util.HashSet<SFMPath> visited = new java.util.HashSet<>();
        while (!pending.isEmpty()) {
            SFMPath parent = pending.removeFirst();
            if (!visited.add(parent)) continue;
            for (SFMPath child : ordinary.relation().childrenOf(parent)) {
                mergedEdges.add(new SFMChildEdge(parent, child));
                SFMExplorerEntry entry = ordinaryEntries.get(child);
                if (entry != null) {
                    if (!entry.equals(mergedEntries.get(child))) mergedEvidence.remove(child);
                    mergedEntries.put(child, entry);
                }
                if (expanded.contains(child)) pending.addLast(child);
            }
            SFMChildRelationRepository.PageState state = ordinary.pageStates().get(parent);
            if (state != null) mergedStates.put(parent, state);
        }

        SFMChildRelationRepository.Snapshot mergedRelations = new SFMChildRelationRepository.Snapshot(
                new SFMChildRelationRevision(observedFilterGeneration, mergedEdges),
                observedFilterGeneration,
                mergedStates
        );
        return Optional.of(new FilterProjection(
                query,
                mergedRelations,
                mergedEntries,
                matches,
                totalMatches,
                complete,
                diagnostics,
                resolverGeneration,
                mergedEvidence
        ));
    }

    /**
     * Resolves one immutable query-specific filter domain. A newer query
     * cancels older work for the same root, while the last published result
     * remains available until the replacement publishes atomically.
     */
    public Optional<CompletableFuture<LoadDisposition>> ensureFilterDomain(
            SFMPath root,
            String query
    ) {
        return ensureFilterDomain(root, query.strip(), ca.teamdman.sfm.client.search.SFMTextMatchOptions.legacyFuzzy());
    }

    public Optional<CompletableFuture<LoadDisposition>> ensureFilterDomain(SFMPath root, String query,
            ca.teamdman.sfm.client.search.SFMTextMatchOptions options) {
        return ensureQueryDomain(root, query, QueryLane.FILTER, options);
    }

    /** Finder queries use an independent lane and cannot evict the visible filter projection. */
    public Optional<CompletableFuture<LoadDisposition>> ensureFinderDomain(
            SFMPath root,
            String query
    ) {
        return ensureFinderDomain(root, query.strip(), ca.teamdman.sfm.client.search.SFMTextMatchOptions.legacyFuzzy());
    }

    public Optional<CompletableFuture<LoadDisposition>> ensureFinderDomain(SFMPath root, String query,
            ca.teamdman.sfm.client.search.SFMTextMatchOptions options) {
        return ensureQueryDomain(root, query, QueryLane.FINDER, options);
    }

    private Optional<CompletableFuture<LoadDisposition>> ensureQueryDomain(
            SFMPath root,
            String query,
            QueryLane lane,
            ca.teamdman.sfm.client.search.SFMTextMatchOptions options
    ) {
        Objects.requireNonNull(root, "root");
        query = Objects.requireNonNull(query, "query");
        if (query.isEmpty()) return Optional.empty();
        SFMExplorerResolver resolver = resolvers.require(root);
        if (!(resolver instanceof SFMExplorerFilterDomainResolver filterResolver)) return Optional.empty();
        long resolverGeneration = resolver.generation();
        FilterDomainKey key = new FilterDomainKey(root, query, resolverGeneration, Objects.requireNonNull(options));
        CompletableFuture<LoadDisposition> completion;
        boolean created = false;
        boolean alreadyPublished = false;
        ArrayList<CompletableFuture<LoadDisposition>> retiredCompletions = new ArrayList<>();
        synchronized (filterDomains) {
            java.util.Set<FilterDomainKey> ownLane = lane == QueryLane.FILTER
                    ? filterLaneDomains
                    : finderLaneDomains;
            java.util.Set<FilterDomainKey> otherLane = lane == QueryLane.FILTER
                    ? finderLaneDomains
                    : filterLaneDomains;
            List<FilterDomainKey> retired = ownLane.stream()
                    .filter(candidate -> candidate.root().equals(root) && !candidate.equals(key))
                    .toList();
            ownLane.removeAll(retired);
            ownLane.add(key);
            retired.forEach(candidate -> {
                if (otherLane.contains(candidate)) return;
                SFMExplorerCancellationToken cancellation = filterDomainCancellations.remove(candidate);
                if (cancellation != null) cancellation.cancel();
                activeFilterDomains.remove(candidate);
                CompletableFuture<LoadDisposition> retiredCompletion = filterDomains.remove(candidate);
                if (retiredCompletion != null) retiredCompletions.add(retiredCompletion);
                publishedFilterDomains.remove(candidate);
                filterDomainFailures.remove(candidate);
            });
            alreadyPublished = publishedFilterDomains.containsKey(key);
            completion = alreadyPublished ? null : filterDomains.get(key);
            // A terminal failed query is evidence, not permission to retry every render tick.
            // A new query/options/resolver generation or explicit refresh can retry it.
            if (!alreadyPublished && completion == null) {
                completion = new CompletableFuture<>();
                filterDomains.put(key, completion);
                activeFilterDomains.add(key);
                filterDomainFailures.remove(key);
                created = true;
            }
        }
        retiredCompletions.forEach(retired -> retired.complete(LoadDisposition.CANCELLED));
        if (alreadyPublished) {
            return Optional.of(CompletableFuture.completedFuture(LoadDisposition.PUBLISHED));
        }
        if (!created) return Optional.of(completion);
        SFMExplorerCancellationToken cancellation = new SFMExplorerCancellationToken();
        synchronized (filterDomains) {
            filterDomainCancellations.put(key, cancellation);
        }
        SFMExplorerFilterDomainResolver.FilterDomainRequest request =
                new SFMExplorerFilterDomainResolver.FilterDomainRequest(
                        root,
                        query,
                        SFMExplorerFilterDomainResolver.DEFAULT_MAXIMUM_FILTER_MATCHES,
                        resolverGeneration,
                        cancellation,
                        options
                );
        CompletableFuture<SFMExplorerFilterDomainResolver.FilterDomain> resolution;
        try {
            resolution = Objects.requireNonNull(
                    filterResolver.resolveFilterDomain(request),
                    "resolver filter-domain completion"
            );
        } catch (RuntimeException failure) {
            resolution = new CompletableFuture<>();
            resolution.completeExceptionally(failure);
        }
        CompletableFuture<LoadDisposition> capturedCompletion = completion;
        resolution.whenComplete((domain, failure) -> {
            Runnable publication = () -> publishFilterDomain(
                    key,
                    request,
                    domain,
                    failure,
                    capturedCompletion,
                    filterResolver
            );
            try {
                publicationExecutor.execute(publication);
            } catch (RuntimeException rejected) {
                completeFilterDomainFailure(
                        key,
                        capturedCompletion,
                        LoadDisposition.FAILED,
                        "publication executor rejected filter-domain work: " + rejected.getMessage()
                );
            }
        });
        return Optional.of(completion);
    }

    /** Explicit retry boundary; retires both query lanes for this exact root outside render paths. */
    public void invalidateQueryDomains(SFMPath root) {
        var completions = new ArrayList<CompletableFuture<LoadDisposition>>();
        synchronized (filterDomains) {
            var keys = filterDomains.keySet().stream().filter(key -> key.root().equals(root)).toList();
            for (var key : keys) {
                var cancellation = filterDomainCancellations.remove(key);
                if (cancellation != null) cancellation.cancel();
                var completion = filterDomains.remove(key);
                if (completion != null) completions.add(completion);
                publishedFilterDomains.remove(key);
                activeFilterDomains.remove(key);
                filterLaneDomains.remove(key);
                finderLaneDomains.remove(key);
            }
            filterDomainFailures.keySet().removeIf(key -> key.root().equals(root));
            if (!keys.isEmpty()) filterGeneration++;
        }
        completions.forEach(completion -> completion.complete(LoadDisposition.CANCELLED));
    }

    /** Cheap projection cache key; unlike the snapshots, this copies no trees. */
    public ProjectionGeneration projectionGeneration() {
        SFMChildRelationRepository.Generation relationGeneration = relations.generation();
        long observedFilterGeneration;
        synchronized (filterDomains) {
            observedFilterGeneration = filterGeneration;
        }
        synchronized (entries) {
            return new ProjectionGeneration(
                    relationGeneration.relationRevision(),
                    relationGeneration.statusGeneration(),
                    entryGeneration,
                    observedFilterGeneration
            );
        }
    }

    /** Repository authority used for one atomic path-expression resolution. */
    public SFMChildRelationRepository relationRepository() {
        return relations;
    }

    /** Pure request validation; no relation ticket or resolver work is started. */
    public void preflightRefresh(SFMPath parent, int pageSize) {
        Objects.requireNonNull(parent, "parent");
        if (pageSize <= 0) throw new IllegalArgumentException("Page size must be positive");
        SFMExplorerResolver resolver = resolvers.require(parent);
        relations.validateRefresh(java.util.Set.of(parent), resolver.generation());
    }

    private LoadHandle start(
            SFMChildRelationRepository.RefreshTicket ticket,
            SFMExplorerResolver resolver,
            Optional<String> continuation,
            int pageSize
    ) {
        if (pageSize <= 0) throw new IllegalArgumentException("Page size must be positive");
        SFMPath parent = ticket.parents().iterator().next();
        RequestEvidence evidence = new RequestEvidence(
                ticket.requestId(),
                ticket.mode(),
                parent,
                resolver.scheme(),
                ticket.resolverGeneration(),
                continuation,
                pageSize
        );
        SFMExplorerCancellationToken cancellation = new SFMExplorerCancellationToken();
        CompletableFuture<LoadResult> completion = new CompletableFuture<>();
        LoadHandle handle = new LoadHandle(ticket, evidence, cancellation, completion);
        SFMExplorerResolver.ChildRequest request = new SFMExplorerResolver.ChildRequest(
                parent,
                continuation,
                pageSize,
                ticket.resolverGeneration(),
                cancellation
        );
        CompletableFuture<SFMExplorerResolver.ChildPage> resolverCompletion;
        try {
            resolverCompletion = Objects.requireNonNull(
                    resolver.resolveChildren(request),
                    "resolver child completion"
            );
        } catch (RuntimeException failure) {
            resolverCompletion = new CompletableFuture<>();
            resolverCompletion.completeExceptionally(failure);
        }
        resolverCompletion.whenComplete((page, failure) -> schedulePublication(handle, () -> {
            synchronized (handle) {
                if (completion.isDone()) return;
                Throwable cause = unwrap(failure);
                if (cause != null) {
                    completeFailure(handle, cause);
                    return;
                }
                try {
                    handle.cancellation.throwIfCancelled();
                    validatePage(request, page);
                    ArrayList<SFMChildEdge> edges = new ArrayList<>();
                    page.entries().forEach(entry -> edges.add(new SFMChildEdge(parent, entry.path())));
                    SFMChildPage relationPage = new SFMChildPage(
                            parent,
                            edges,
                            page.continuation(),
                            page.complete() ? SFMChildPage.Completeness.COMPLETE : SFMChildPage.Completeness.PARTIAL,
                            page.resolverGeneration(),
                            page.diagnostics()
                    );
                    SFMChildRelationRepository.PublishResult published = relations.publish(
                            ticket,
                            java.util.List.of(relationPage)
                    );
                    LoadDisposition disposition = switch (published.disposition()) {
                        case PUBLISHED -> LoadDisposition.PUBLISHED;
                        case STALE -> LoadDisposition.STALE;
                        case FAILED -> LoadDisposition.FAILED;
                        case CANCELLED -> LoadDisposition.CANCELLED;
                    };
                    if (disposition == LoadDisposition.PUBLISHED) {
                        synchronized (entries) {
                            page.entries().forEach(entry -> entries.put(entry.path(), entry));
                            entryGeneration++;
                        }
                    }
                    completion.complete(new LoadResult(
                            evidence,
                            disposition,
                            published.snapshot(),
                            Optional.of(page),
                            Optional.empty()
                    ));
                } catch (Throwable validationFailure) {
                    completeFailure(handle, validationFailure);
                }
            }
        }));
        return handle;
    }

    private void schedulePublication(LoadHandle handle, Runnable publication) {
        try {
            publicationExecutor.execute(publication);
        } catch (RuntimeException failure) {
            completeFailure(handle, new IllegalStateException(
                    "publication executor rejected work: " + failure.getMessage(),
                    failure
            ));
        }
    }

    private void completeFailure(LoadHandle handle, Throwable failure) {
        synchronized (handle) {
            if (handle.completion.isDone()) return;
            Throwable cause = unwrap(failure);
            if (cause instanceof CancellationException || handle.cancellation.isCancelled()) {
                relations.cancel(handle.ticket);
                handle.completion.complete(new LoadResult(
                        handle.evidence,
                        LoadDisposition.CANCELLED,
                        relations.snapshot(),
                        Optional.empty(),
                        Optional.of("request cancelled")
                ));
                return;
            }
            if (cause instanceof SFMExplorerResolver.StaleGenerationException) {
                relations.cancel(handle.ticket);
                handle.completion.complete(new LoadResult(
                        handle.evidence,
                        LoadDisposition.STALE,
                        relations.snapshot(),
                        Optional.empty(),
                        Optional.of(cause.getMessage())
                ));
                return;
            }
            String diagnostic = cause == null
                    ? "resolver failed without an exception"
                    : cause.getClass().getSimpleName() + ": " + String.valueOf(cause.getMessage());
            SFMChildRelationRepository.PublishResult failed = relations.fail(handle.ticket, diagnostic);
            LoadDisposition disposition = failed.disposition() == SFMChildRelationRepository.PublishDisposition.STALE
                    ? LoadDisposition.STALE
                    : LoadDisposition.FAILED;
            handle.completion.complete(new LoadResult(
                    handle.evidence,
                    disposition,
                    failed.snapshot(),
                    Optional.empty(),
                    Optional.of(diagnostic)
            ));
        }
    }

    private void publishFilterDomain(
            FilterDomainKey key,
            SFMExplorerFilterDomainResolver.FilterDomainRequest request,
            SFMExplorerFilterDomainResolver.FilterDomain domain,
            Throwable failure,
            CompletableFuture<LoadDisposition> completion,
            SFMExplorerFilterDomainResolver resolver
    ) {
        if (completion.isDone()) return;
        Throwable cause = unwrap(failure);
        if (cause != null) {
            LoadDisposition disposition = cause instanceof CancellationException
                    ? LoadDisposition.CANCELLED
                    : cause instanceof SFMExplorerResolver.StaleGenerationException
                            ? LoadDisposition.STALE
                            : LoadDisposition.FAILED;
            completeFilterDomainFailure(key, completion, disposition, diagnostic(cause));
            return;
        }
        try {
            request.cancellation().throwIfCancelled();
            validateFilterDomain(request, domain);
            if (resolver.generation() != request.expectedResolverGeneration()) {
                throw new SFMExplorerResolver.StaleGenerationException(
                        request.expectedResolverGeneration(),
                        resolver.generation()
                );
            }
            TreeSet<SFMChildEdge> edges = domain.childPages().stream()
                    .flatMap(page -> page.edges().stream())
                    .collect(java.util.stream.Collectors.toCollection(TreeSet::new));
            TreeMap<SFMPath, SFMChildRelationRepository.PageState> states = new TreeMap<>();
            domain.childPages().forEach(page -> states.put(page.parent(),
                    new SFMChildRelationRepository.PageState(
                            page.resolverGeneration(),
                            page.continuation(),
                            page.completeness(),
                            SFMChildRelationRepository.PageState.Materialization.MATERIALIZED,
                            page.diagnostics(),
                            0
                    )));
            TreeMap<SFMPath, SFMExplorerEntry> indexedEntries = new TreeMap<>();
            domain.entries().forEach(entry -> indexedEntries.put(entry.path(), entry));
            synchronized (filterDomains) {
                if (filterDomains.get(key) != completion || request.cancellation().isCancelled()) {
                    completeFilterDomain(key, completion, LoadDisposition.CANCELLED,
                            Optional.of("filter query was superseded"));
                    return;
                }
                long publishedGeneration = ++filterGeneration;
                publishedFilterDomains.put(key, new FilterProjection(
                        domain.query(),
                        new SFMChildRelationRepository.Snapshot(
                                new SFMChildRelationRevision(publishedGeneration, edges),
                                publishedGeneration,
                                states
                        ),
                        indexedEntries,
                        domain.matchedPaths(),
                        domain.totalMatchCount(),
                        domain.complete(),
                        domain.diagnostics(),
                        domain.resolverGeneration(),
                        domain.matchEvidence()
                ));
            }
            completeFilterDomain(key, completion, LoadDisposition.PUBLISHED, Optional.empty());
        } catch (Throwable validationFailure) {
            Throwable unwrapped = unwrap(validationFailure);
            LoadDisposition disposition = unwrapped instanceof CancellationException
                    ? LoadDisposition.CANCELLED
                    : unwrapped instanceof SFMExplorerResolver.StaleGenerationException
                            ? LoadDisposition.STALE
                            : LoadDisposition.FAILED;
            completeFilterDomainFailure(key, completion, disposition, diagnostic(unwrapped));
        }
    }

    private void completeFilterDomainFailure(
            FilterDomainKey key,
            CompletableFuture<LoadDisposition> completion,
            LoadDisposition disposition,
            String diagnostic
    ) {
        completeFilterDomain(key, completion, disposition, Optional.of(diagnostic));
    }

    private void completeFilterDomain(
            FilterDomainKey key,
            CompletableFuture<LoadDisposition> completion,
            LoadDisposition disposition,
        Optional<String> diagnostic
    ) {
        synchronized (filterDomains) {
            activeFilterDomains.remove(key);
            filterDomainCancellations.remove(key);
            if (filterDomains.get(key) == completion) {
                if (diagnostic.isPresent()) filterDomainFailures.put(key, diagnostic.orElseThrow());
                else filterDomainFailures.remove(key);
            }
        }
        completion.complete(disposition);
    }

    private static void validateFilterDomain(
            SFMExplorerFilterDomainResolver.FilterDomainRequest request,
            SFMExplorerFilterDomainResolver.FilterDomain domain
    ) {
        Objects.requireNonNull(domain, "domain");
        if (!domain.root().equals(request.root())) {
            throw new IllegalStateException("Resolver filter domain root does not match its request");
        }
        if (!domain.query().equals(request.query())) {
            throw new IllegalStateException("Resolver filter domain query does not match its request");
        }
        if (!domain.options().equals(request.options())) {
            throw new IllegalStateException("Resolver filter domain match options do not match its request");
        }
        if (domain.resolverGeneration() != request.expectedResolverGeneration()) {
            throw new SFMExplorerResolver.StaleGenerationException(
                    request.expectedResolverGeneration(),
                    domain.resolverGeneration()
            );
        }
        TreeMap<SFMPath, SFMExplorerEntry> indexedEntries = new TreeMap<>();
        for (SFMExplorerEntry entry : domain.entries()) {
            if (indexedEntries.put(entry.path(), entry) != null) {
                throw new IllegalStateException("Resolver filter domain contains a duplicate entry path");
            }
            requireContained(request.root(), entry.path());
        }
        if (!indexedEntries.containsKey(request.root())) {
            throw new IllegalStateException("Resolver filter domain omitted its root entry");
        }
        if (domain.matchedPaths().size() > request.maximumMatches()) {
            throw new IllegalStateException("Resolver filter domain exceeded the requested match bound");
        }
        for (SFMPath match : domain.matchedPaths()) {
            requireContained(request.root(), match);
            if (!indexedEntries.containsKey(match)) {
                throw new IllegalStateException("Resolver filter domain match references an absent entry");
            }
        }
        HashSet<SFMPath> parents = new HashSet<>();
        for (SFMChildPage page : domain.childPages()) {
            if (!parents.add(page.parent())) {
                throw new IllegalStateException("Resolver filter domain contains duplicate parent pages");
            }
            requireContained(request.root(), page.parent());
            if (page.resolverGeneration() != request.expectedResolverGeneration()) {
                throw new SFMExplorerResolver.StaleGenerationException(
                        request.expectedResolverGeneration(),
                        page.resolverGeneration()
                );
            }
            if (page.completeness() != SFMChildPage.Completeness.COMPLETE
                    || page.continuation().isPresent()) {
                throw new IllegalStateException("Resolver filter-domain pages must be complete");
            }
            page.edges().forEach(edge -> {
                requireContained(request.root(), edge.child());
                if (!indexedEntries.containsKey(edge.child())) {
                    throw new IllegalStateException("Resolver filter-domain edge references an absent entry");
                }
            });
        }
        if (!parents.contains(request.root())) {
            throw new IllegalStateException("Resolver filter domain omitted the root child page");
        }
    }

    private static void requireContained(SFMPath root, SFMPath path) {
        if (!root.scheme().equals(path.scheme())
                || !root.authority().equals(path.authority())
                || root.segments().size() > path.segments().size()
                || !path.segments().subList(0, root.segments().size()).equals(root.segments())) {
            throw new IllegalStateException("Resolver filter domain escaped its requested root");
        }
    }

    private static String diagnostic(Throwable failure) {
        if (failure == null) return "resolver failed without an exception";
        String message = failure.getMessage();
        String answer = failure.getClass().getSimpleName() + ": " + String.valueOf(message);
        return answer.length() <= 512 ? answer : answer.substring(0, 512);
    }

    private static void validatePage(
            SFMExplorerResolver.ChildRequest request,
            SFMExplorerResolver.ChildPage page
    ) {
        Objects.requireNonNull(page, "page");
        if (!page.parent().equals(request.parent())) {
            throw new IllegalStateException("Resolver page parent does not match its request");
        }
        if (page.resolverGeneration() != request.expectedResolverGeneration()) {
            throw new SFMExplorerResolver.StaleGenerationException(
                    request.expectedResolverGeneration(),
                    page.resolverGeneration()
            );
        }
        if (page.entries().size() > request.pageSize()) {
            throw new IllegalStateException("Resolver returned more entries than the requested page bound");
        }
        for (SFMExplorerEntry entry : page.entries()) {
            if (!entry.path().scheme().equals(request.parent().scheme())) {
                throw new IllegalStateException("A resolver returned a child owned by another scheme");
            }
        }
    }

    private static Throwable unwrap(Throwable failure) {
        if (failure == null) return null;
        Throwable answer = failure;
        while ((answer instanceof CompletionException || answer instanceof java.util.concurrent.ExecutionException)
                && answer.getCause() != null) {
            answer = answer.getCause();
        }
        return answer;
    }
}
