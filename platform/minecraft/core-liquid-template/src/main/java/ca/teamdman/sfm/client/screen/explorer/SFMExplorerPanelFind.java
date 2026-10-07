package ca.teamdman.sfm.client.screen.explorer;

import static ca.teamdman.sfm.client.search.SFMExplorerSearchText.*;

import ca.teamdman.sfm.client.explorer.*;
import ca.teamdman.sfm.client.explorer.lazy.*;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanelBounds;
import ca.teamdman.sfm.client.search.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Bounded background matching plus a render-thread preview/selection consumer. */
final class SFMExplorerPanelFind {
    private record Published(SFMExplorerFindNavigation.Token token, List<SFMPath> order,
                             Set<SFMPath> matches, Map<SFMPath, SFMExplorerEntryMatch> evidence,
                             boolean complete, List<String> diagnostics) { }
    private final SFMExplorerSession session;
    private final SFMLazyExplorerLoader loader;
    private final SFMExplorerPanelModel model;
    private final SFMExplorerFindNavigation navigation = new SFMExplorerFindNavigation();
    private volatile SFMExplorerFindNavigation.Token current;
    private final java.util.concurrent.atomic.AtomicReference<Published> materializedResult =
            new java.util.concurrent.atomic.AtomicReference<>();
    private volatile Optional<SFMPath> materializedPreview = Optional.empty();
    private volatile String revealFailure = "";
    private SFMExplorerSession.FinderState consumedFinder;
    private Map<SFMPath, SFMExplorerEntryMatch> evidence = Map.of();
    private Set<SFMPath> descendantMatches = Set.of();
    private boolean domainComplete;
    private Optional<SFMPath> requestedPreview = Optional.empty();
    private Optional<SFMPath> selectionAwaitingReveal = Optional.empty();
    private String query = "";
    private SFMTextMatchOptions options = SFMTextMatchOptions.defaults();
    private boolean materializedOnly;
    private boolean captured;
    private Boolean queuedMatchSelection;
    private boolean additiveSelection;
    private Set<SFMPath> queryRoots = Set.of();
    private String status = value(FIND_DEFAULT);
    private String matchSummary = status;

    SFMExplorerPanelFind(SFMExplorerSession session, SFMLazyExplorerLoader loader, SFMExplorerPanelModel model) {
        this.session = session;
        this.loader = loader;
        this.model = model;
    }

    String query() { return query; }
    SFMTextMatchOptions options() { return options; }
    String status() { return revealFailure.isEmpty() ? status : revealFailure; }
    boolean materializedOnly() { return materializedOnly; }
    Map<SFMPath, SFMExplorerEntryMatch> evidence() { return evidence; }
    Optional<SFMPath> preview() { return navigation.preview(); }
    boolean descendantMatch(SFMPath path) { return descendantMatches.contains(path); }
    boolean complete() { return domainComplete; }

    void capture(SFMScreenPanelBounds bounds) {
        var state = model.state(bounds);
        navigation.capture(state.viewport().cells().stream().filter(cell -> !cell.row().loading())
                .flatMap(cell -> cell.row().paths().stream()).toList(), state.selectedPaths());
        captured = true;
        current = navigation.token();
    }

    void setQuery(String query, SFMTextMatchOptions options, SFMScreenPanelBounds bounds) {
        if (!captured) capture(bounds);
        this.query = query;
        this.options = options;
        queuedMatchSelection = null;
        additiveSelection = false;
        queryRoots = session.snapshot().roots();
        evidence = Map.of();
        descendantMatches = Set.of();
        domainComplete = false;
        requestedPreview = Optional.empty();
        selectionAwaitingReveal = Optional.empty();
        materializedPreview = Optional.empty();
        materializedResult.set(null);
        revealFailure = "";
        consumedFinder = null;
        matchSummary = value(PENDING);
        if (query.isEmpty()) {
            navigation.clear();
            current = navigation.token();
            model.emitFindSet("", options);
            status = value(FIND_CLEARED);
            return;
        }
        current = navigation.beginQuery();
        status = value(SEARCHING, value(materializedOnly ? MATERIALIZED : COMPLETE));
        if (!materializedOnly) {
            model.emitFindSet(query, options);
            return;
        }
        // Explicit reduced scope. Copy state once, never scan the filesystem here.
        var token = current;
        var snapshot = session.snapshot();
        long generation = session.beginFinderQuery(query, options);
        var entries = loader.entrySnapshot();
        var relations = loader.relationSnapshot();
        CompletableFuture.runAsync(() -> {
            var fields = new HashMap<SFMPath, SFMExplorerEntryMatch>();
            var matches = new HashSet<SFMPath>();
            var diagnostics = new ArrayList<String>();
            boolean complete = true;
            try {
                var matcher = SFMTextMatcher.compile(query, options);
                var budget = new SFMMatchBudget(1_000_000, () -> !token.equals(current));
                for (var entry : entries.values()) {
                    if (SFMPathHierarchy.deepestContainingRoot(snapshot.roots(), entry.path()).isEmpty()) continue;
                    var match = SFMExplorerEntryMatch.evaluate(entry, matcher, budget);
                    fields.put(entry.path(), match);
                    if (match.matches()) matches.add(entry.path());
                    complete &= match.complete();
                    diagnostics.addAll(match.diagnostics());
                    if (budget.exhausted()) break;
                }
            } catch (java.util.concurrent.CancellationException ignored) { return; }
            catch (IllegalArgumentException failure) { complete = false; diagnostics.add(failure.getMessage()); }
            if (!token.equals(current)) return;
            var order = SFMExplorerMatchTraversal.order(fields.keySet(), snapshot, relations, entries);
            session.publishFinderResultsInDisplayOrder(generation, query,
                    order.stream().filter(matches::contains).toList(), complete, diagnostics);
            var publication = new Published(token, order, Set.copyOf(matches), Map.copyOf(fields), complete,
                    List.copyOf(diagnostics));
            // A superseded worker can finish traversal after the current worker. Reject
            // it at the actual atomic publication, not only before doing that traversal.
            materializedResult.updateAndGet(previous -> token.equals(current) ? publication : previous);
        });
    }

    void setMaterializedOnly(boolean value, SFMScreenPanelBounds bounds) {
        materializedOnly = value;
        setQuery(query, options, bounds);
    }

    void tick(SFMScreenPanelBounds bounds) {
        if (query.isEmpty() || current == null || navigation.outcome() == SFMExplorerFindNavigation.Outcome.CANCELLED) return;
        var snapshot = session.snapshot();
        if (snapshot.closed() || !snapshot.roots().equals(queryRoots)) {
            userNavigated();
            evidence = Map.of();
            descendantMatches = Set.of();
            status = value(SOURCE_CHANGED);
            return;
        }
        var finder = snapshot.finder();
        if (finder.query().equals(query) && finder.options().equals(options) && finder != consumedFinder
                && finder.status() != SFMExplorerSession.FinderStatus.PENDING) {
            Published result;
            if (materializedOnly) {
                result = materializedResult.get();
                if (result == null || !result.token().equals(current)) return;
            } else {
                var domain = loader.filterProjection(snapshot.roots(), query, snapshot.expanded(), options);
                if (domain.isEmpty()) {
                    consumedFinder = finder;
                    status = finder.diagnostics().stream().map(SFMExplorerSession.FinderDiagnostic::message)
                            .findFirst().orElse(value(DOMAIN_UNAVAILABLE))
                            + value(TRY_MATERIALIZED);
                    navigation.publish(current, List.of(), Set.of(), false);
                    return;
                }
                var published = domain.orElseThrow();
                result = new Published(current,
                        SFMExplorerMatchTraversal.order(published.entries().keySet(), snapshot,
                                published.relations(), published.entries()), published.matchedPaths(),
                        published.matchEvidence(), published.complete(), published.diagnostics());
            }
            var eligible = new HashSet<>(result.matches());
            boolean complete = result.complete();
            if (snapshot.settings().filterActive()) {
                if (loader.hasPendingFilterDomain(snapshot.roots(), snapshot.settings().filterQuery(),
                        snapshot.settings().filterOptions())) return;
                var filtered = model.state(bounds).projection();
                if (!filtered.settings().equals(snapshot.settings())) return; // old filter still displayed while loading
                complete &= !filtered.filter().incompleteMaterialization();
                eligible.retainAll(filtered.rows().stream().filter(row -> !row.loading())
                        .flatMap(row -> row.paths().stream()).collect(java.util.stream.Collectors.toSet()));
            }
            if (!navigation.publish(result.token(), result.order(), eligible, complete)) return;
            consumedFinder = finder;
            evidence = result.evidence();
            domainComplete = complete;
            var ancestors = new HashSet<SFMPath>();
            for (var match : eligible) {
                SFMPathHierarchy.deepestContainingRoot(snapshot.roots(), match).ifPresent(root -> {
                    var chain = SFMPathHierarchy.chain(root, match);
                    ancestors.addAll(chain.subList(0, Math.max(0, chain.size() - 1)));
                });
            }
            descendantMatches = Set.copyOf(ancestors);
            status = value(MATCH_COUNT, eligible.size(), value(materializedOnly ? MATERIALIZED : COMPLETE))
                    + (snapshot.settings().filterActive() ? value(WITHIN_FILTER) : "")
                    + (complete ? "" : value(INCOMPLETE))
                    + (result.diagnostics().isEmpty() ? "" : " · " + result.diagnostics().get(0));
            matchSummary = status;
        }
        if (queuedMatchSelection != null && consumedFinder != null) {
            boolean all = queuedMatchSelection;
            queuedMatchSelection = null;
            applyMatchSelection(all, bounds);
        }
        consumeNavigation(bounds);
    }

    void selectMatches(boolean all, SFMScreenPanelBounds bounds) {
        if (query.isEmpty()) { status = value(ENTER_QUERY); return; }
        if (!captured || navigation.outcome() == SFMExplorerFindNavigation.Outcome.CANCELLED) {
            capture(bounds);
            setQuery(query, options, bounds);
        }
        if (navigation.outcome() == SFMExplorerFindNavigation.Outcome.SEARCHING) {
            queuedMatchSelection = all;
            status = value(WAIT_COMPLETE);
            return;
        }
        applyMatchSelection(all, bounds);
        consumeNavigation(bounds);
    }

    private void applyMatchSelection(boolean all, SFMScreenPanelBounds bounds) {
        if (!navigation.complete()) { status = value(SELECTION_INCOMPLETE); return; }
        var matches = navigation.orderedMatches();
        if (matches.isEmpty()) { status = value(NO_MATCHES); return; }
        if (all) {
            navigation.takeSelection(); // replace also cancels an earlier additive move awaiting reveal
            var primary = session.snapshot().navigationCursor().filter(matches::contains).or(() -> matches.stream().findFirst());
            session.selectMembers(Set.copyOf(matches), primary);
            selectionAwaitingReveal = Optional.empty();
            additiveSelection = false;
            status = value(SELECTED_ALL, matches.size(), value(materializedOnly ? MATERIALIZED : COMPLETE))
                    + (session.snapshot().settings().filterActive() ? value(WITHIN_FILTER) : "");
        } else {
            additiveSelection = true;
            if (navigation.addNext(session.selectedPaths()) != SFMExplorerFindNavigation.Outcome.SELECTED) {
                additiveSelection = false;
                status = value(ALREADY_SELECTED);
            }
        }
    }

    void move(int direction, boolean wrap, SFMScreenPanelBounds bounds) {
        queuedMatchSelection = null;
        additiveSelection = false;
        status = matchSummary;
        var outcome = navigation.move(direction, wrap);
        if (outcome == SFMExplorerFindNavigation.Outcome.BOUNDARY) status = value(NO_FURTHER);
        if (outcome == SFMExplorerFindNavigation.Outcome.NO_MATCHES) status = value(NO_MATCHES);
        if (outcome == SFMExplorerFindNavigation.Outcome.INCOMPLETE) status = value(NO_FURTHER_KNOWN);
        consumeNavigation(bounds);
    }

    private void consumeNavigation(SFMScreenPanelBounds bounds) {
        navigation.takeSelection().ifPresent(path -> selectionAwaitingReveal = Optional.of(path));
        var preview = navigation.preview();
        if (preview.isPresent() && !preview.equals(requestedPreview)) {
            requestedPreview = preview;
            var target = preview.orElseThrow();
            var token = current;
            var roots = session.snapshot().roots();
            var root = SFMPathHierarchy.deepestContainingRoot(roots, target);
            if (root.isEmpty()) { revealFailure = value(TARGET_OUTSIDE); return; }
            // Already-projected relation children need no URI-segment walk. Registries may
            // publish e.g. namespace/item directly beneath their root, without a namespace row.
            if (model.revealPreview(target, bounds)) {
                materializedPreview = Optional.of(target);
                revealFailure = "";
            } else try {
                SFMExplorerPathReveal.preview(session, loader, root.orElseThrow(), target, 128,
                        () -> { if (token.equals(current)) materializedPreview = Optional.of(target); },
                        () -> token.equals(current) && !session.snapshot().closed()
                                && session.snapshot().roots().equals(roots)).whenComplete((ignored, failure) -> {
                    if (failure != null && token.equals(current)) revealFailure = value(REVEAL_UNAVAILABLE, failure.getMessage());
                });
            } catch (RuntimeException failure) { revealFailure = value(REVEAL_UNAVAILABLE, failure.getMessage()); }
        }
        if (materializedPreview.isPresent()) {
            var target = materializedPreview.orElseThrow();
            if (model.revealPreview(target, bounds)) {
                if (selectionAwaitingReveal.equals(Optional.of(target))) {
                    commitSelection(target, bounds);
                    selectionAwaitingReveal = Optional.empty();
                }
                materializedPreview = Optional.empty();
            }
        } else if (selectionAwaitingReveal.isPresent()
                && model.revealPreview(selectionAwaitingReveal.orElseThrow(), bounds)) {
            commitSelection(selectionAwaitingReveal.orElseThrow(), bounds);
            selectionAwaitingReveal = Optional.empty();
        }
    }

    private void commitSelection(SFMPath target, SFMScreenPanelBounds bounds) {
        if (additiveSelection) {
            var members = new HashSet<>(session.selectedPaths());
            members.add(target);
            session.selectMembers(members, Optional.of(target));
            status = value(ADDED, members.size());
            additiveSelection = false;
        } else model.select(target, bounds);
    }

    void userNavigated() {
        navigation.userNavigated();
        current = navigation.token();
        captured = false;
        queuedMatchSelection = null;
        additiveSelection = false;
        if (!query.isEmpty()) status = matchSummary + value(PAUSED);
        materializedPreview = Optional.empty();
        selectionAwaitingReveal = Optional.empty();
    }
}
