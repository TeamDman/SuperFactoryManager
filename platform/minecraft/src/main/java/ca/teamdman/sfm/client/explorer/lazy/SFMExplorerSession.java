package ca.teamdman.sfm.client.explorer.lazy;

import ca.teamdman.sfm.client.explorer.SFMEntitySelector;
import ca.teamdman.sfm.client.explorer.SFMExplorerId;
import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.explorer.SFMPathExpression;
import ca.teamdman.sfm.client.explorer.SFMSelectionId;
import ca.teamdman.sfm.client.explorer.SFMSelectionRepository;

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
            boolean closed
    ) {
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
                closed
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
        scrollOffset = 0;
        location = nextLocation;
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
        setSettings(new SFMExplorerProjection.Settings(view, settings.sort(), settings.group(), settings.hoist()));
    }

    public synchronized void setSort(SFMExplorerProjection.Sort sort) {
        setSettings(new SFMExplorerProjection.Settings(settings.view(), sort, settings.group(), settings.hoist()));
    }

    public synchronized void setGroup(SFMExplorerProjection.Group group) {
        setSettings(new SFMExplorerProjection.Settings(settings.view(), settings.sort(), group, settings.hoist()));
    }

    public synchronized void setHoist(SFMExplorerProjection.Hoist hoist) {
        setSettings(new SFMExplorerProjection.Settings(settings.view(), settings.sort(), settings.group(), hoist));
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
        ensureOpen();
        Optional<SFMPath> next = Optional.of(Objects.requireNonNull(path, "path"));
        if (navigationCursor.equals(next)) return;
        navigationCursor = next;
        revision++;
    }

    public synchronized void clearNavigationCursor() {
        ensureOpen();
        if (navigationCursor.isEmpty()) return;
        navigationCursor = Optional.empty();
        revision++;
    }

    public synchronized void setScrollOffset(int scrollOffset) {
        ensureOpen();
        if (scrollOffset < 0) throw new IllegalArgumentException("Scroll offset must not be negative");
        if (this.scrollOffset == scrollOffset) return;
        this.scrollOffset = scrollOffset;
        revision++;
    }

    public synchronized void showSelectionOverlay(SFMSelectionId selectionId) {
        ensureOpen();
        if (overlaySelections.add(Objects.requireNonNull(selectionId, "selectionId"))) revision++;
    }

    public synchronized void hideSelectionOverlay(SFMSelectionId selectionId) {
        ensureOpen();
        if (overlaySelections.remove(Objects.requireNonNull(selectionId, "selectionId"))) revision++;
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
