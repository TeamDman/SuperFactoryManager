package ca.teamdman.sfm.client.explorer.lazy;

import ca.teamdman.sfm.client.explorer.SFMEntitySelector;
import ca.teamdman.sfm.client.explorer.SFMExplorerId;
import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.explorer.SFMPathExpression;
import ca.teamdman.sfm.client.explorer.SFMSelectionId;
import ca.teamdman.sfm.client.explorer.SFMSelectionRepository;
import ca.teamdman.sfm.client.explorer.SFMSelectionRevision;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/** Independent mutable navigation/projection state for one explorer component. */
public final class SFMExplorerSession implements AutoCloseable {
    private static final int MAXIMUM_RECENT_REQUESTS = 64;
    /** Shared with the bounded Rust control protocol. */
    public static final int MAXIMUM_ROOTS = 4096;

    public record RequestObservation(
            SFMLazyExplorerLoader.RequestEvidence evidence,
            long startedAtEpochMillis,
            Optional<Long> completedAtEpochMillis,
            Optional<SFMLazyExplorerLoader.LoadDisposition> disposition,
            Optional<String> diagnostic
    ) {
        public RequestObservation {
            Objects.requireNonNull(evidence, "evidence");
            if (startedAtEpochMillis < 0) throw new IllegalArgumentException("Request start must not be negative");
            Objects.requireNonNull(completedAtEpochMillis, "completedAtEpochMillis");
            Objects.requireNonNull(disposition, "disposition");
            Objects.requireNonNull(diagnostic, "diagnostic");
            if (completedAtEpochMillis.isPresent() != disposition.isPresent()) {
                throw new IllegalArgumentException("Completed request evidence requires a disposition");
            }
        }
    }

    /** Lifecycle of one independent find query; finding never changes projection filtering. */
    public enum FinderStatus {
        CLEARED,
        PENDING,
        READY,
        EMPTY,
        FAILED
    }

    /** Machine-readable finder feedback retained with the session snapshot. */
    public enum FinderDiagnosticCode {
        SEARCH_PENDING,
        NO_MATCHES,
        SEARCH_FAILED,
        RESULTS_TRUNCATED,
        REVEAL_FAILED
    }

    public record FinderDiagnostic(FinderDiagnosticCode code, String message) {
        public FinderDiagnostic {
            Objects.requireNonNull(code, "code");
            message = Objects.requireNonNull(message, "message").strip();
            if (message.isEmpty()) throw new IllegalArgumentException("Finder diagnostic must not be blank");
        }
    }

    /** Immutable, deterministic find state for one Explorer session. */
    public record FinderState(
            String query,
            FinderStatus status,
            List<SFMPath> matches,
            int cursorIndex,
            List<FinderDiagnostic> diagnostics,
            long generation,
            ca.teamdman.sfm.client.search.SFMTextMatchOptions options
    ) {
        public FinderState {
            query = Objects.requireNonNull(query, "query");
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(options, "options");
            matches = List.copyOf(matches);
            diagnostics = List.copyOf(diagnostics);
            if (generation < 0) throw new IllegalArgumentException("Finder generation must not be negative");
            if (cursorIndex < -1 || cursorIndex >= matches.size()) {
                throw new IllegalArgumentException("Finder cursor is outside the result list");
            }
            if (matches.size() != new java.util.HashSet<>(matches).size()) {
                throw new IllegalArgumentException("Finder matches must be unique canonical identities");
            }
            if (status == FinderStatus.CLEARED && (!query.isEmpty() || !matches.isEmpty())) {
                throw new IllegalArgumentException("Cleared finder state cannot retain a query or matches");
            }
            if (status == FinderStatus.PENDING && query.isEmpty()) {
                throw new IllegalArgumentException("Pending finder state requires a query");
            }
            if (status == FinderStatus.READY && matches.isEmpty()) {
                throw new IllegalArgumentException("Ready finder state requires matches");
            }
            if (status == FinderStatus.EMPTY && !matches.isEmpty()) {
                throw new IllegalArgumentException("Empty finder state cannot retain matches");
            }
        }

        public static FinderState cleared() {
            return cleared(0);
        }

        public FinderState(String query, FinderStatus status, List<SFMPath> matches, int cursorIndex,
                           List<FinderDiagnostic> diagnostics, long generation) {
            this(query.strip(), status, matches, cursorIndex, diagnostics, generation,
                    ca.teamdman.sfm.client.search.SFMTextMatchOptions.legacyFuzzy());
        }

        private static FinderState cleared(long generation) {
            return new FinderState("", FinderStatus.CLEARED, List.of(), -1, List.of(), generation,
                    ca.teamdman.sfm.client.search.SFMTextMatchOptions.defaults());
        }

        public Optional<SFMPath> currentMatch() {
            return cursorIndex < 0 ? Optional.empty() : Optional.of(matches.get(cursorIndex));
        }
    }

    private static final class ActiveRequest {
        private final SFMLazyExplorerLoader.LoadHandle handle;
        private final long startedAtEpochMillis;
        private final AtomicBoolean recorded = new AtomicBoolean();

        private ActiveRequest(SFMLazyExplorerLoader.LoadHandle handle, long startedAtEpochMillis) {
            this.handle = handle;
            this.startedAtEpochMillis = startedAtEpochMillis;
        }
    }

    /** Complete reversible state for an action transaction; resolver work is deferred. */
    public static final class TransactionSnapshot {
        private final Set<SFMPath> roots;
        private final List<SFMPath> manualRootOrder;
        private final Set<SFMPath> expanded;
        private final Set<SFMSelectionId> overlaySelections;
        private final SFMPathExpression location;
        private final Optional<SFMPath> navigationCursor;
        private final Optional<SFMSelectionId> rowSelectionId;
        private final Optional<SFMPath> rangeAnchor;
        private final FinderState finder;
        private final Optional<SFMSelectionId> ephemeralLocationSelection;
        private final int scrollOffset;
        private final long locationRequestOrdinal;
        private final long revision;
        private final SFMExplorerProjection.Settings settings;
        private final boolean closed;

        private TransactionSnapshot(SFMExplorerSession session) {
            roots = Set.copyOf(session.roots);
            manualRootOrder = List.copyOf(session.manualRootOrder);
            expanded = Set.copyOf(session.expanded);
            overlaySelections = Set.copyOf(session.overlaySelections);
            location = session.location;
            navigationCursor = session.navigationCursor;
            rowSelectionId = session.rowSelectionId;
            rangeAnchor = session.rangeAnchor;
            finder = session.finder;
            ephemeralLocationSelection = session.ephemeralLocationSelection;
            scrollOffset = session.scrollOffset;
            locationRequestOrdinal = session.locationRequestOrdinal;
            revision = session.revision;
            settings = session.settings;
            closed = session.closed;
        }
    }

    public record Snapshot(
            SFMExplorerId id,
            long revision,
            SFMPathExpression location,
            Set<SFMPath> roots,
            List<SFMPath> manualRootOrder,
            Set<SFMPath> expanded,
            Optional<SFMPath> navigationCursor,
            int scrollOffset,
            SFMExplorerProjection.Settings settings,
            Set<SFMSelectionId> overlaySelections,
            Optional<SFMSelectionId> ephemeralLocationSelection,
            boolean closed,
            FinderState finder,
            Optional<SFMSelectionRevision> rowSelection,
            Optional<SFMPath> rangeAnchor
    ) {
        /** Compatibility for older projections without an interactive membership ledger. */
        public Snapshot(SFMExplorerId id, long revision, SFMPathExpression location, Set<SFMPath> roots,
                        List<SFMPath> manualRootOrder, Set<SFMPath> expanded, Optional<SFMPath> navigationCursor,
                        int scrollOffset, SFMExplorerProjection.Settings settings, Set<SFMSelectionId> overlaySelections,
                        Optional<SFMSelectionId> ephemeralLocationSelection, boolean closed, FinderState finder) {
            this(id, revision, location, roots, manualRootOrder, expanded, navigationCursor, scrollOffset, settings,
                    overlaySelections, ephemeralLocationSelection, closed, finder, Optional.empty(), Optional.empty());
        }

        public Set<SFMPath> selectedPaths() {
            return rowSelection.map(SFMSelectionRevision::members)
                    .orElseGet(() -> navigationCursor.map(Set::of).orElse(Set.of()));
        }
        /** Compatibility constructor for callers that predate independent finder state. */
        public Snapshot(
                SFMExplorerId id,
                long revision,
                SFMPathExpression location,
                Set<SFMPath> roots,
                List<SFMPath> manualRootOrder,
                Set<SFMPath> expanded,
                Optional<SFMPath> navigationCursor,
                int scrollOffset,
                SFMExplorerProjection.Settings settings,
                Set<SFMSelectionId> overlaySelections,
                Optional<SFMSelectionId> ephemeralLocationSelection,
                boolean closed
        ) {
            this(
                    id,
                    revision,
                    location,
                    roots,
                    manualRootOrder,
                    expanded,
                    navigationCursor,
                    scrollOffset,
                    settings,
                    overlaySelections,
                    ephemeralLocationSelection,
                    closed,
                    FinderState.cleared()
            );
        }

        public Snapshot {
            Objects.requireNonNull(id, "id");
            if (revision < 0) throw new IllegalArgumentException("Session revision must not be negative");
            Objects.requireNonNull(location, "location");
            roots = Collections.unmodifiableSet(new TreeSet<>(roots));
            manualRootOrder = List.copyOf(manualRootOrder);
            expanded = Collections.unmodifiableSet(new TreeSet<>(expanded));
            Objects.requireNonNull(navigationCursor, "navigationCursor");
            if (scrollOffset < 0) throw new IllegalArgumentException("Scroll offset must not be negative");
            Objects.requireNonNull(settings, "settings");
            TreeSet<SFMSelectionId> overlayCopy = new TreeSet<>(
                    java.util.Comparator.comparing(SFMSelectionId::value)
            );
            overlayCopy.addAll(overlaySelections);
            overlaySelections = Collections.unmodifiableSet(overlayCopy);
            Objects.requireNonNull(ephemeralLocationSelection, "ephemeralLocationSelection");
            Objects.requireNonNull(finder, "finder");
            Objects.requireNonNull(rowSelection, "rowSelection");
            Objects.requireNonNull(rangeAnchor, "rangeAnchor");
        }
    }

    private final SFMExplorerId id;
    private final SFMSelectionRepository selections;
    private final TreeSet<SFMPath> roots = new TreeSet<>();
    private final ArrayList<SFMPath> manualRootOrder = new ArrayList<>();
    private final TreeSet<SFMPath> expanded = new TreeSet<>();
    private final TreeSet<SFMSelectionId> overlaySelections = new TreeSet<>(
            java.util.Comparator.comparing(SFMSelectionId::value)
    );
    private final Map<SFMPath, ActiveRequest> activeRequests = new ConcurrentHashMap<>();
    private final ArrayDeque<RequestObservation> recentRequests = new ArrayDeque<>();
    private SFMPathExpression location;
    private Optional<SFMPath> navigationCursor = Optional.empty();
    private Optional<SFMSelectionId> rowSelectionId = Optional.empty();
    private Optional<SFMPath> rangeAnchor = Optional.empty();
    private FinderState finder = FinderState.cleared();
    private Optional<SFMSelectionId> ephemeralLocationSelection = Optional.empty();
    private int scrollOffset;
    private long locationRequestOrdinal = 1;
    private long revision;
    private SFMExplorerProjection.Settings settings = SFMExplorerProjection.Settings.defaults();
    private boolean closed;

    public SFMExplorerSession(
            SFMExplorerId id,
            SFMPath initialRoot,
            SFMSelectionRepository selections
    ) {
        this(
                id,
                new SFMPathExpression.Literal(Objects.requireNonNull(initialRoot, "initialRoot")),
                Set.of(initialRoot),
                selections
        );
    }

    /** Creates one independent session from an already-resolved immutable location capture. */
    public SFMExplorerSession(
            SFMExplorerId id,
            SFMPathExpression initialLocation,
            Set<SFMPath> initialRoots,
            SFMSelectionRepository selections
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.selections = Objects.requireNonNull(selections, "selections");
        location = Objects.requireNonNull(initialLocation, "initialLocation");
        TreeSet<SFMPath> normalizedRoots = new TreeSet<>(Objects.requireNonNull(initialRoots, "initialRoots"));
        if (normalizedRoots.isEmpty()) {
            throw new IllegalArgumentException("An explorer location must resolve to at least one root");
        }
        if (normalizedRoots.size() > MAXIMUM_ROOTS) {
            throw new IllegalArgumentException(
                    "An explorer location cannot resolve to more than " + MAXIMUM_ROOTS + " roots"
            );
        }
        roots.addAll(normalizedRoots);
        manualRootOrder.addAll(normalizedRoots);
    }

    public synchronized Snapshot snapshot() {
        return new Snapshot(
                id,
                revision,
                location,
                roots,
                manualRootOrder,
                expanded,
                navigationCursor,
                scrollOffset,
                settings,
                overlaySelections,
                ephemeralLocationSelection,
                closed,
                finder,
                rowSelectionId.flatMap(selections::selection).flatMap(value -> selections.revision(value.headRevisionId())),
                rangeAnchor
        );
    }

    public synchronized TransactionSnapshot transactionSnapshot() {
        return new TransactionSnapshot(this);
    }

    public synchronized void restoreTransactionSnapshot(TransactionSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        roots.clear();
        roots.addAll(snapshot.roots);
        manualRootOrder.clear();
        manualRootOrder.addAll(snapshot.manualRootOrder);
        expanded.clear();
        expanded.addAll(snapshot.expanded);
        overlaySelections.clear();
        overlaySelections.addAll(snapshot.overlaySelections);
        location = snapshot.location;
        navigationCursor = snapshot.navigationCursor;
        rowSelectionId = snapshot.rowSelectionId;
        rangeAnchor = snapshot.rangeAnchor;
        finder = snapshot.finder;
        ephemeralLocationSelection = snapshot.ephemeralLocationSelection;
        scrollOffset = snapshot.scrollOffset;
        locationRequestOrdinal = snapshot.locationRequestOrdinal;
        revision = snapshot.revision;
        settings = snapshot.settings;
        closed = snapshot.closed;
    }

    public synchronized boolean addRoot(SFMPath root) {
        ensureOpen();
        Objects.requireNonNull(root, "root");
        if (roots.contains(root)) return false;
        if (roots.size() >= MAXIMUM_ROOTS) {
            throw new IllegalStateException("An explorer session cannot contain more than " + MAXIMUM_ROOTS + " roots");
        }
        if (ephemeralLocationSelection.isEmpty()) {
            SFMSelectionId selectionId = allocateLocationSelectionId();
            TreeSet<SFMPath> prospectiveRoots = new TreeSet<>(roots);
            prospectiveRoots.add(root);
            selections.create(
                    selectionId,
                    Optional.empty(),
                    prospectiveRoots,
                    actor(),
                    nextRequestId("create-location")
            );
            ephemeralLocationSelection = Optional.of(selectionId);
            location = members(selectionId);
        } else {
            SFMSelectionId selectionId = ephemeralLocationSelection.orElseThrow();
            selections.add(
                    selectionId,
                    Set.of(root),
                    actor(),
                    nextRequestId("add-root")
            );
        }
        roots.add(root);
        manualRootOrder.add(root);
        invalidateFinderForDomainChange();
        revision++;
        return true;
    }

    public synchronized boolean removeRoot(SFMPath root) {
        boolean changed = removeRootWithoutCancelling(root);
        if (changed) cancelRequest(root);
        return changed;
    }

    /**
     * Replaces the semantic location and its resolved roots as one reversible
     * state mutation. Resolver and authority checks belong to the action
     * engine and must finish before this method is called.
     */
    public synchronized boolean replaceLocation(
            SFMPathExpression nextLocation,
            Set<SFMPath> nextRoots
    ) {
        ensureOpen();
        Objects.requireNonNull(nextLocation, "nextLocation");
        TreeSet<SFMPath> normalizedRoots = new TreeSet<>(Objects.requireNonNull(nextRoots, "nextRoots"));
        if (normalizedRoots.isEmpty()) {
            throw new IllegalArgumentException("An explorer location must resolve to at least one root");
        }
        if (normalizedRoots.size() > MAXIMUM_ROOTS) {
            throw new IllegalArgumentException(
                    "An explorer location cannot resolve to more than " + MAXIMUM_ROOTS + " roots"
            );
        }
        if (location.equals(nextLocation) && roots.equals(normalizedRoots)) return false;

        ArrayList<SFMPath> nextOrder = new ArrayList<>();
        manualRootOrder.stream().filter(normalizedRoots::contains).forEach(nextOrder::add);
        normalizedRoots.stream().filter(path -> !nextOrder.contains(path)).forEach(nextOrder::add);

        roots.clear();
        roots.addAll(normalizedRoots);
        manualRootOrder.clear();
        manualRootOrder.addAll(nextOrder);
        expanded.retainAll(normalizedRoots);
        navigationCursor = navigationCursor.filter(normalizedRoots::contains);
        if (rowSelectionId.isPresent()) replaceRowMembers(Set.of());
        rangeAnchor = Optional.empty();
        scrollOffset = 0;
        location = nextLocation;
        invalidateFinderForDomainChange();
        ephemeralLocationSelection = ephemeralLocationSelection.filter(selectionId ->
                members(selectionId).equals(nextLocation)
        );
        revision++;
        return true;
    }

    /** State-only variant used while a multi-target transaction is reversible. */
    public synchronized boolean removeRootWithoutCancelling(SFMPath root) {
        ensureOpen();
        Objects.requireNonNull(root, "root");
        if (roots.size() == 1 && roots.contains(root)) {
            throw new IllegalStateException("An explorer session must retain at least one root");
        }
        if (!roots.contains(root)) return false;
        ephemeralLocationSelection.ifPresent(selectionId -> selections.remove(
                selectionId,
                Set.of(root),
                actor(),
                nextRequestId("remove-root")
        ));
        roots.remove(root);
        manualRootOrder.remove(root);
        invalidateFinderForDomainChange();
        expanded.remove(root);
        navigationCursor = navigationCursor.filter(cursor -> !cursor.equals(root));
        revision++;
        return true;
    }

    public synchronized void setManualRootOrder(List<SFMPath> order) {
        ensureOpen();
        Objects.requireNonNull(order, "order");
        if (order.size() != roots.size() || !new TreeSet<>(order).equals(roots)) {
            throw new IllegalArgumentException("Manual root order must contain every root exactly once");
        }
        if (manualRootOrder.equals(order)) return;
        manualRootOrder.clear();
        manualRootOrder.addAll(order);
        revision++;
    }

    public synchronized void setSettings(SFMExplorerProjection.Settings settings) {
        ensureOpen();
        settings = Objects.requireNonNull(settings, "settings");
        if (this.settings.equals(settings)) return;
        this.settings = settings;
        revision++;
    }

    public synchronized void setView(SFMExplorerProjection.View view) {
        setSettings(new SFMExplorerProjection.Settings(
                view,
                settings.sort(),
                settings.group(),
                settings.hoist(),
                settings.pathDisplay(),
                settings.filterQuery(),
                settings.filterOptions(), settings.compaction()
        ));
    }

    public synchronized void setSort(SFMExplorerProjection.Sort sort) {
        setSettings(new SFMExplorerProjection.Settings(
                settings.view(),
                sort,
                settings.group(),
                settings.hoist(),
                settings.pathDisplay(),
                settings.filterQuery(),
                settings.filterOptions(), settings.compaction()
        ));
    }

    public synchronized void setGroup(SFMExplorerProjection.Group group) {
        setSettings(new SFMExplorerProjection.Settings(
                settings.view(),
                settings.sort(),
                group,
                settings.hoist(),
                settings.pathDisplay(),
                settings.filterQuery(),
                settings.filterOptions(), settings.compaction()
        ));
    }

    public synchronized void setHoist(SFMExplorerProjection.Hoist hoist) {
        setSettings(new SFMExplorerProjection.Settings(
                settings.view(),
                settings.sort(),
                settings.group(),
                hoist,
                settings.pathDisplay(),
                settings.filterQuery(),
                settings.filterOptions(), settings.compaction()
        ));
    }

    public synchronized void setPathDisplay(SFMExplorerProjection.PathDisplay pathDisplay) {
        setSettings(new SFMExplorerProjection.Settings(
                settings.view(),
                settings.sort(),
                settings.group(),
                settings.hoist(),
                pathDisplay,
                settings.filterQuery(),
                settings.filterOptions(), settings.compaction()
        ));
    }

    public synchronized void setFilterQuery(String filterQuery) {
        setSettings(new SFMExplorerProjection.Settings(
                settings.view(),
                settings.sort(),
                settings.group(),
                settings.hoist(),
                settings.pathDisplay(),
                filterQuery,
                settings.filterOptions(), settings.compaction()
        ));
    }

    public synchronized void setFilterOptions(ca.teamdman.sfm.client.search.SFMTextMatchOptions options) {
        setSettings(new SFMExplorerProjection.Settings(settings.view(), settings.sort(), settings.group(),
                settings.hoist(), settings.pathDisplay(), settings.filterQuery(), options, settings.compaction()));
    }

    public synchronized boolean expand(SFMPath path) {
        ensureOpen();
        boolean changed = expanded.add(Objects.requireNonNull(path, "path"));
        if (changed) revision++;
        return changed;
    }

    public synchronized boolean collapse(SFMPath path) {
        boolean changed = collapseWithoutCancelling(path);
        cancelRequest(path);
        return changed;
    }

    /** State-only variant used while a multi-target transaction is reversible. */
    public synchronized boolean collapseWithoutCancelling(SFMPath path) {
        ensureOpen();
        boolean changed = expanded.remove(Objects.requireNonNull(path, "path"));
        if (changed) revision++;
        return changed;
    }

    public synchronized void navigateTo(SFMPath path) {
        selectRow(path, List.of(path), SFMExplorerRowSelection.Gesture.REPLACE);
    }

    public synchronized void selectRow(SFMPath path, List<SFMPath> displayedOrder,
                                       SFMExplorerRowSelection.Gesture gesture) {
        ensureOpen();
        var next = SFMExplorerRowSelection.apply(selectedPaths(), rangeAnchor, path, displayedOrder, gesture);
        if (navigationCursor.equals(Optional.of(path)) && rangeAnchor.equals(next.anchor())
                && selectedPaths().equals(next.members())) return;
        replaceRowMembers(next.members());
        navigationCursor = Optional.of(path);
        rangeAnchor = next.anchor();
        revision++;
    }

    public synchronized Set<SFMPath> selectedPaths() {
        return rowSelectionId.flatMap(selections::selection).flatMap(value -> selections.revision(value.headRevisionId()))
                .map(SFMSelectionRevision::members).orElse(Set.of());
    }

    /** Complete-domain match selection may include exact paths not yet materialized as rows. */
    public synchronized void selectMembers(Set<SFMPath> members, Optional<SFMPath> primary) {
        ensureOpen();
        primary.ifPresent(path -> { if (!members.contains(path)) throw new IllegalArgumentException("Primary must be selected"); });
        replaceRowMembers(members);
        navigationCursor = primary;
        rangeAnchor = primary;
        revision++;
    }

    private void replaceRowMembers(Set<SFMPath> members) {
        if (rowSelectionId.isEmpty()) {
            var created = selections.create(Optional.empty(), members, actor(), nextRequestId("row-selection-create"));
            rowSelectionId = Optional.of(created.selection().id());
        } else if (!selectedPaths().equals(members)) {
            selections.replace(rowSelectionId.orElseThrow(), members, actor(), nextRequestId("row-selection-replace"));
        }
    }

    public synchronized void clearNavigationCursor() {
        ensureOpen();
        if (navigationCursor.isEmpty()) return;
        navigationCursor = Optional.empty();
        revision++;
    }

    /** Starts or restarts an asynchronous find without changing the Explorer filter settings. */
    public synchronized long beginFinderQuery(String query) {
        return beginFinderQuery(query.strip(), ca.teamdman.sfm.client.search.SFMTextMatchOptions.legacyFuzzy());
    }

    public synchronized long beginFinderQuery(String query, ca.teamdman.sfm.client.search.SFMTextMatchOptions options) {
        ensureOpen();
        query = validateFinderQuery(query);
        long generation = finder.generation() + 1;
        finder = new FinderState(
                query,
                FinderStatus.PENDING,
                List.of(),
                -1,
                List.of(new FinderDiagnostic(
                        FinderDiagnosticCode.SEARCH_PENDING,
                        "Searching Explorer entries for '" + query + "'"
                )),
                generation,
                options
        );
        revision++;
        return generation;
    }

    /** Publishes a generation-matched finder result atomically in canonical path order. */
    public synchronized boolean publishFinderResults(
            long expectedGeneration,
            String query,
            List<SFMPath> matches,
            boolean complete,
            List<String> resolverDiagnostics
    ) {
        return publishFinderResultsInDisplayOrder(expectedGeneration, query,
                matches.stream().distinct().sorted().toList(), complete, resolverDiagnostics);
    }

    /** The query provider computes a deterministic presentation order without reordering the live tree. */
    public synchronized boolean publishFinderResultsInDisplayOrder(long expectedGeneration, String query,
            List<SFMPath> matches, boolean complete, List<String> resolverDiagnostics) {
        if (closed) return false;
        query = validateFinderQuery(query);
        Objects.requireNonNull(matches, "matches");
        Objects.requireNonNull(resolverDiagnostics, "resolverDiagnostics");
        if (finder.generation() != expectedGeneration || !finder.query().equals(query)) return false;

        List<SFMPath> ordered = matches.stream().distinct().toList();
        ArrayList<FinderDiagnostic> diagnostics = new ArrayList<>();
        resolverDiagnostics.stream()
                .filter(message -> message != null && !message.isBlank())
                .map(message -> new FinderDiagnostic(FinderDiagnosticCode.SEARCH_FAILED, message))
                .forEach(diagnostics::add);
        if (!complete) {
            diagnostics.add(new FinderDiagnostic(
                    FinderDiagnosticCode.RESULTS_TRUNCATED,
                    "Finder results are bounded; additional matches may exist"
            ));
        }
        FinderStatus status;
        if (ordered.isEmpty() && complete) {
            status = FinderStatus.EMPTY;
            diagnostics.add(new FinderDiagnostic(
                    FinderDiagnosticCode.NO_MATCHES,
                    "No Explorer entries match '" + query + "'"
            ));
        } else if (ordered.isEmpty()) {
            status = FinderStatus.FAILED;
        } else {
            status = FinderStatus.READY;
        }
        finder = new FinderState(query, status, ordered, -1, diagnostics, expectedGeneration, finder.options());
        revision++;
        return true;
    }

    /** Retains a typed terminal failure only if the request is still current. */
    public synchronized boolean failFinderQuery(
            long expectedGeneration,
            String query,
            String diagnostic
    ) {
        if (closed) return false;
        query = validateFinderQuery(query);
        diagnostic = Objects.requireNonNull(diagnostic, "diagnostic").strip();
        if (diagnostic.isEmpty()) throw new IllegalArgumentException("Finder failure must not be blank");
        if (finder.generation() != expectedGeneration || !finder.query().equals(query)) return false;
        finder = new FinderState(
                query,
                FinderStatus.FAILED,
                List.of(),
                -1,
                List.of(new FinderDiagnostic(FinderDiagnosticCode.SEARCH_FAILED, diagnostic)),
                expectedGeneration,
                finder.options()
        );
        revision++;
        return true;
    }

    /** Advances with deterministic wraparound; pending and empty searches remain unchanged. */
    public synchronized boolean advanceFinder(int delta) {
        ensureOpen();
        if (delta != -1 && delta != 1) throw new IllegalArgumentException("Finder delta must be -1 or 1");
        if (finder.status() != FinderStatus.READY || finder.matches().isEmpty()) return false;
        int nextIndex = finder.cursorIndex() < 0
                ? (delta > 0 ? 0 : finder.matches().size() - 1)
                : Math.floorMod(finder.cursorIndex() + delta, finder.matches().size());
        finder = new FinderState(
                finder.query(),
                finder.status(),
                finder.matches(),
                nextIndex,
                finder.diagnostics().stream()
                        .filter(value -> value.code() != FinderDiagnosticCode.REVEAL_FAILED)
                        .toList(),
                finder.generation(),
                finder.options()
        );
        revision++;
        return true;
    }

    /** Records asynchronous reveal failure without allowing stale callbacks to poison a new search. */
    public synchronized boolean recordFinderRevealFailure(
            long expectedGeneration,
            SFMPath target,
            String diagnostic
    ) {
        if (closed) return false;
        Objects.requireNonNull(target, "target");
        diagnostic = Objects.requireNonNull(diagnostic, "diagnostic").strip();
        if (diagnostic.isEmpty()) throw new IllegalArgumentException("Reveal failure must not be blank");
        if (finder.generation() != expectedGeneration || !finder.matches().contains(target)) return false;
        ArrayList<FinderDiagnostic> diagnostics = new ArrayList<>(finder.diagnostics().stream()
                .filter(value -> value.code() != FinderDiagnosticCode.REVEAL_FAILED)
                .toList());
        diagnostics.add(new FinderDiagnostic(FinderDiagnosticCode.REVEAL_FAILED, diagnostic));
        finder = new FinderState(
                finder.query(),
                finder.status(),
                finder.matches(),
                finder.cursorIndex(),
                diagnostics,
                finder.generation(),
                finder.options()
        );
        revision++;
        return true;
    }

    public synchronized boolean clearFinder() {
        ensureOpen();
        if (finder.status() == FinderStatus.CLEARED) return false;
        finder = new FinderState("", FinderStatus.CLEARED, List.of(), -1, List.of(),
                finder.generation() + 1, finder.options());
        revision++;
        return true;
    }

    public synchronized void setScrollOffset(int scrollOffset) {
        ensureOpen();
        if (scrollOffset < 0) throw new IllegalArgumentException("Scroll offset must not be negative");
        if (this.scrollOffset == scrollOffset) return;
        this.scrollOffset = scrollOffset;
        revision++;
    }

    private void invalidateFinderForDomainChange() {
        if (finder.status() == FinderStatus.CLEARED) return;
        finder = new FinderState(finder.query(), FinderStatus.FAILED, List.of(), -1,
                List.of(new FinderDiagnostic(FinderDiagnosticCode.SEARCH_FAILED,
                        "Explorer location changed; run Find again for the new domain")),
                finder.generation() + 1, finder.options());
    }

    public synchronized void showSelectionOverlay(SFMSelectionId selectionId) {
        ensureOpen();
        if (overlaySelections.add(Objects.requireNonNull(selectionId, "selectionId"))) revision++;
    }

    public synchronized void hideSelectionOverlay(SFMSelectionId selectionId) {
        ensureOpen();
        if (overlaySelections.remove(Objects.requireNonNull(selectionId, "selectionId"))) revision++;
    }

    /**
     * Completes delayed root initialization without replacing a reveal/prefetch
     * already in flight. A late describe result cannot expand a removed root,
     * reopen a closed session, or discard pages that reveal already materialized.
     */
    public synchronized Optional<SFMLazyExplorerLoader.LoadHandle> initializeRootChildren(
            SFMPath root,
            SFMLazyExplorerLoader loader,
            int pageSize
    ) {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(loader, "loader");
        if (pageSize <= 0) throw new IllegalArgumentException("Page size must be positive");
        if (closed || !roots.contains(root)) return Optional.empty();
        expand(root);
        Optional<SFMLazyExplorerLoader.LoadHandle> active = activeRequestHandle(root);
        if (active.isPresent()) return active;
        if (loader.hasCurrentMaterializedChildren(root)) return Optional.empty();
        return Optional.of(requestChildren(root, loader, pageSize));
    }

    public synchronized SFMLazyExplorerLoader.LoadHandle requestChildren(
            SFMPath parent,
            SFMLazyExplorerLoader loader,
            int pageSize
    ) {
        ensureOpen();
        Objects.requireNonNull(parent, "parent");
        Objects.requireNonNull(loader, "loader");
        cancelRequest(parent);
        SFMLazyExplorerLoader.LoadHandle handle = loader.refresh(parent, pageSize);
        track(parent, handle);
        return handle;
    }

    public synchronized Optional<SFMLazyExplorerLoader.LoadHandle> requestNextPage(
            SFMPath parent,
            SFMLazyExplorerLoader loader,
            int pageSize
    ) {
        ensureOpen();
        Objects.requireNonNull(parent, "parent");
        Objects.requireNonNull(loader, "loader");
        // Rendering may observe the continuation on several consecutive frames.
        // Keep the first append request authoritative instead of cancelling and
        // restarting it every time the viewport asks for the same next page.
        if (activeRequests.containsKey(parent)) return Optional.empty();
        Optional<SFMLazyExplorerLoader.LoadHandle> handle = loader.nextPage(parent, pageSize);
        handle.ifPresent(value -> track(parent, value));
        return handle;
    }

    public synchronized void cancelChildrenRequest(SFMPath parent) {
        ensureOpen();
        cancelRequest(Objects.requireNonNull(parent, "parent"));
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        revision++;
        List<ActiveRequest> requests = List.copyOf(activeRequests.values());
        activeRequests.clear();
        requests.forEach(request -> request.handle.cancel());
    }

    /** Repository used by this session's selection-backed multi-root location. */
    public SFMSelectionRepository selectionRepository() {
        return selections;
    }

    private static String validateFinderQuery(String query) {
        query = Objects.requireNonNull(query, "query");
        if (query.isEmpty()) throw new IllegalArgumentException("Explorer finder query must not be blank");
        if (query.length() > 2048 || query.codePointCount(0, query.length()) > 1024)
            throw new IllegalArgumentException("Explorer finder query is too long");
        if (query.indexOf('\n') >= 0 || query.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("Explorer finder query must be one line");
        }
        return query;
    }

    /** Safe revalidation hook for prepared all-or-none action transactions. */
    public synchronized boolean hasRevision(long expectedRevision) {
        return revision == expectedRevision;
    }

    public synchronized List<RequestObservation> activeRequestEvidence() {
        return activeRequests.values().stream()
                .map(active -> new RequestObservation(
                        active.handle.evidence(),
                        active.startedAtEpochMillis,
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty()
                ))
                .sorted(java.util.Comparator.comparingLong(observation ->
                        observation.evidence().relationRequestId()))
                .toList();
    }

    public synchronized List<RequestObservation> recentRequestEvidence() {
        return List.copyOf(recentRequests);
    }

    public synchronized int activeRequestCount() {
        return activeRequests.size();
    }

    /**
     * Returns the authoritative in-flight request for one parent, if any.
     * Reveal and other coordinators join this handle instead of treating a
     * deduplicated request as an unavailable continuation.
     */
    public synchronized Optional<SFMLazyExplorerLoader.LoadHandle> activeRequestHandle(SFMPath parent) {
        ActiveRequest active = activeRequests.get(Objects.requireNonNull(parent, "parent"));
        if (active == null) return Optional.empty();
        if (active.handle.completion().isDone()) {
            // CompletableFuture dependents are not ordered. A reveal continuation
            // can run before track()'s bookkeeping callback and would otherwise
            // repeatedly rejoin the same already-completed handle. Retire it
            // eagerly from this authoritative accessor.
            SFMLazyExplorerLoader.LoadResult result = active.handle.completion().getNow(null);
            finishRequest(parent, active, result, null);
            return Optional.empty();
        }
        return Optional.of(active.handle);
    }

    private void track(SFMPath parent, SFMLazyExplorerLoader.LoadHandle handle) {
        ActiveRequest active = new ActiveRequest(handle, System.currentTimeMillis());
        activeRequests.put(parent, active);
        handle.completion().whenComplete((result, failure) -> finishRequest(parent, active, result, failure));
    }

    private void cancelRequest(SFMPath parent) {
        ActiveRequest active = activeRequests.get(parent);
        if (active != null) active.handle.cancel();
    }

    private synchronized void finishRequest(
            SFMPath parent,
            ActiveRequest active,
            SFMLazyExplorerLoader.LoadResult result,
            Throwable failure
    ) {
        activeRequests.remove(parent, active);
        if (!active.recorded.compareAndSet(false, true)) return;
        SFMLazyExplorerLoader.LoadDisposition disposition = result == null
                ? SFMLazyExplorerLoader.LoadDisposition.FAILED
                : result.disposition();
        Optional<String> diagnostic = result == null
                ? Optional.of(failure == null
                        ? "request failed without a result"
                        : failure.getClass().getSimpleName() + ": " + String.valueOf(failure.getMessage()))
                : result.diagnostic();
        recentRequests.addLast(new RequestObservation(
                active.handle.evidence(),
                active.startedAtEpochMillis,
                Optional.of(System.currentTimeMillis()),
                Optional.of(disposition),
                diagnostic
        ));
        while (recentRequests.size() > MAXIMUM_RECENT_REQUESTS) recentRequests.removeFirst();
    }

    private SFMSelectionId allocateLocationSelectionId() {
        String base = "explorer-" + id.value() + "-location";
        SFMSelectionId candidate = new SFMSelectionId(base);
        int suffix = 2;
        while (selectionIdentityIsOccupied(candidate)) {
            candidate = new SFMSelectionId(base + "-" + suffix++);
        }
        return candidate;
    }

    private boolean selectionIdentityIsOccupied(SFMSelectionId candidate) {
        return selections.selection(candidate).isPresent()
                || selections.stateSnapshot().selections().values().stream()
                .anyMatch(selection -> selection.name().equals(Optional.of(candidate.value())));
    }

    private static SFMPathExpression members(SFMSelectionId id) {
        return new SFMPathExpression.Members(SFMEntitySelector.exact(
                SFMEntitySelector.Domain.SELECTION,
                id.value()
        ));
    }

    private String actor() {
        return "explorer-session:" + id.value();
    }

    private String nextRequestId(String operation) {
        return operation + "-" + locationRequestOrdinal++;
    }

    private void ensureOpen() {
        if (closed) throw new IllegalStateException("Explorer session is closed");
    }
}
