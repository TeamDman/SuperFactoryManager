package ca.teamdman.sfm.client.screen.explorer;

import ca.teamdman.sfm.client.explorer.SFMExplorerId;
import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.explorer.SFMChildRelationRepository;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerProjection;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerEntry;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerSession;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerRowSelection;
import ca.teamdman.sfm.client.explorer.lazy.SFMLazyExplorerLoader;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanelBounds;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.Map;
import java.util.TreeMap;

/**
 * Read-only explorer projection plus transient panel cursor/scroll state.
 * Semantic mutations are emitted through {@link SFMExplorerSemanticActionSink}.
 */
public final class SFMExplorerPanelModel {
    private static final int DEFAULT_PAGINATION_PAGE_SIZE = 128;
    private static final int MAXIMUM_SCROLL_TRACES = 128;

    public record ScrollTrace(
            long sequence,
            int requestedDelta,
            int beforeRow,
            int afterRow,
            long callbackNanoTime,
            long modelMutationNanoTime,
            Optional<Long> firstObservedFrame,
            Optional<Long> firstObservedNanoTime,
            Optional<Integer> observedScrollRow
    ) {
        public ScrollTrace {
            if (sequence <= 0) throw new IllegalArgumentException("Scroll sequence must be positive");
            Objects.requireNonNull(firstObservedFrame, "firstObservedFrame");
            Objects.requireNonNull(firstObservedNanoTime, "firstObservedNanoTime");
            Objects.requireNonNull(observedScrollRow, "observedScrollRow");
            if (firstObservedFrame.isPresent() != observedScrollRow.isPresent()
                    || firstObservedFrame.isPresent() != firstObservedNanoTime.isPresent()) {
                throw new IllegalArgumentException("Observed scroll evidence requires frame, time, and row");
            }
        }

        public long eventToModelNanos() {
            return Math.max(0L, modelMutationNanoTime - callbackNanoTime);
        }

        public Optional<Long> modelToFrameNanos() {
            return firstObservedNanoTime.map(observed -> Math.max(0L, observed - modelMutationNanoTime));
        }

        public Optional<Long> eventToFrameNanos() {
            return firstObservedNanoTime.map(observed -> Math.max(0L, observed - callbackNanoTime));
        }
    }

    public record State(
            SFMExplorerSession.Snapshot session,
            SFMExplorerProjection.Result projection,
            SFMExplorerPanelViewport.Snapshot viewport
    ) {
        public State {
            Objects.requireNonNull(session, "session");
            Objects.requireNonNull(projection, "projection");
            Objects.requireNonNull(viewport, "viewport");
        }

        public Optional<SFMPath> selectedPath() {
            return session.navigationCursor();
        }

        public Set<SFMPath> selectedPaths() { return session.selectedPaths(); }

        public Optional<SFMExplorerProjection.Row> selectedRow() {
            Optional<SFMPath> selectedPath = selectedPath();
            if (selectedPath.isEmpty()) return Optional.empty();
            return projection.rows().stream()
                    .filter(row -> row.contains(selectedPath.orElseThrow()))
                    .findFirst();
        }
    }

    /**
     * Deterministic evidence that high-frequency viewport updates reuse the
     * semantic projection and only materialize the bounded visible window.
     */
    public record ProjectionWorkTelemetry(
            long projectionBuilds,
            long projectionCacheHits,
            long inlineLoadingBuilds,
            long inlineLoadingCacheHits,
            int latestProjectionRows,
            int latestViewportCells
    ) {
        public ProjectionWorkTelemetry {
            if (projectionBuilds < 0 || projectionCacheHits < 0
                    || inlineLoadingBuilds < 0 || inlineLoadingCacheHits < 0
                    || latestProjectionRows < 0 || latestViewportCells < 0) {
                throw new IllegalArgumentException("Explorer projection telemetry must not be negative");
            }
        }
    }

    private record ProjectionCacheKey(
            Set<SFMPath> roots,
            List<SFMPath> manualRootOrder,
            Set<SFMPath> expanded,
            SFMExplorerProjection.Settings settings,
            SFMLazyExplorerLoader.ProjectionGeneration loaderGeneration
    ) {
        private ProjectionCacheKey {
            roots = Set.copyOf(roots);
            manualRootOrder = List.copyOf(manualRootOrder);
            expanded = Set.copyOf(expanded);
            Objects.requireNonNull(settings, "settings");
            Objects.requireNonNull(loaderGeneration, "loaderGeneration");
        }
    }

    private final SFMExplorerSession session;
    private final SFMLazyExplorerLoader loader;
    private final SFMExplorerSemanticActionSink actionSink;
    private final int paginationPageSize;
    private final ArrayList<ScrollTrace> scrollTraces = new ArrayList<>();
    private long nextScrollSequence = 1;
    private long nextFrameSequence = 1;
    private ProjectionCacheKey cachedProjectionKey;
    private SFMExplorerProjection.Result cachedProjection;
    private SFMChildRelationRepository.Snapshot cachedRelations;
    private SFMExplorerProjection.Result cachedInlineLoadingBase;
    private List<SFMExplorerSession.RequestObservation> cachedInlineLoadingRequests = List.of();
    private SFMExplorerProjection.Result cachedInlineLoadingProjection;
    private long projectionBuilds;
    private long projectionCacheHits;
    private long inlineLoadingBuilds;
    private long inlineLoadingCacheHits;
    private int latestProjectionRows;
    private int latestViewportCells;
    private java.util.function.IntSupplier toolbarHeight = () -> 0;
    private boolean findVisible;

    public void setFindVisible(boolean visible) { findVisible = visible; }

    public void setToolbarHeight(java.util.function.IntSupplier height) {
        toolbarHeight = Objects.requireNonNull(height, "height");
    }

    public SFMExplorerPanelModel(
            SFMExplorerSession session,
            SFMLazyExplorerLoader loader,
            SFMExplorerSemanticActionSink actionSink
    ) {
        this(session, loader, actionSink, DEFAULT_PAGINATION_PAGE_SIZE);
    }

    SFMExplorerPanelModel(
            SFMExplorerSession session,
            SFMLazyExplorerLoader loader,
            SFMExplorerSemanticActionSink actionSink,
            int paginationPageSize
    ) {
        this.session = Objects.requireNonNull(session, "session");
        this.loader = Objects.requireNonNull(loader, "loader");
        this.actionSink = Objects.requireNonNull(actionSink, "actionSink");
        if (paginationPageSize <= 0) throw new IllegalArgumentException("Pagination page size must be positive");
        this.paginationPageSize = paginationPageSize;
    }

    public State state(SFMScreenPanelBounds bounds) {
        SFMExplorerSession.Snapshot sessionSnapshot = session.snapshot();
        ProjectionCacheKey projectionKey = new ProjectionCacheKey(
                sessionSnapshot.roots(),
                sessionSnapshot.manualRootOrder(),
                sessionSnapshot.expanded(),
                sessionSnapshot.settings(),
                loader.projectionGeneration()
        );
        SFMChildRelationRepository.Snapshot relationSnapshot;
        SFMExplorerProjection.Result projection;
        // Check identity before copying/merging a complete filter domain.
        if (projectionKey.equals(cachedProjectionKey)
                && cachedProjection != null
                && cachedRelations != null) {
            relationSnapshot = cachedRelations;
            projection = cachedProjection;
            projectionCacheHits++;
        } else {
            Optional<SFMLazyExplorerLoader.FilterProjection> filterProjection =
                    sessionSnapshot.settings().filterActive()
                            ? loader.filterProjection(sessionSnapshot.roots(),
                                    sessionSnapshot.settings().filterQuery(), sessionSnapshot.expanded(),
                                    sessionSnapshot.settings().filterOptions())
                            : Optional.empty();
            boolean retainPreviousFilterWhilePending = sessionSnapshot.settings().filterActive()
                    && filterProjection.isEmpty()
                    && cachedProjection != null
                    && cachedRelations != null
                    && cachedProjection.filter().active()
                    && loader.hasPendingFilterDomain(
                            sessionSnapshot.roots(), sessionSnapshot.settings().filterQuery(),
                            sessionSnapshot.settings().filterOptions());
            if (retainPreviousFilterWhilePending) {
                // Ordinary resolvers have no complete domain; absence is not pending.
                relationSnapshot = cachedRelations;
                projection = cachedProjection;
                projectionCacheHits++;
            } else {
                relationSnapshot = filterProjection
                        .map(SFMLazyExplorerLoader.FilterProjection::relations)
                        .orElseGet(loader::relationSnapshot);
                Map<SFMPath, SFMExplorerEntry> projectionEntries = filterProjection
                        .map(SFMLazyExplorerLoader.FilterProjection::entries)
                        .orElseGet(loader::entrySnapshot);
                projection = SFMExplorerProjection.project(
                        sessionSnapshot,
                        relationSnapshot,
                        projectionEntries,
                        filterProjection.map(SFMLazyExplorerLoader.FilterProjection::matchEvidence).orElse(Map.of())
                );
                if (filterProjection.isPresent()) {
                    var domain = filterProjection.orElseThrow();
                    projection = projection.withDomainEvidence(domain.complete(), domain.diagnostics());
                }
                cachedProjectionKey = projectionKey;
                cachedProjection = projection;
                cachedRelations = relationSnapshot;
                projectionBuilds++;
            }
        }
        projection = withCachedInlineLoadingRows(
                projection,
                sessionSnapshot,
                session.activeRequestEvidence()
        );
        retainOrChooseSelection(sessionSnapshot, projection);
        sessionSnapshot = session.snapshot();
        SFMExplorerPanelViewport.Snapshot viewport = SFMExplorerPanelViewport.calculate(
                bounds,
                projection.settings().view(),
                projection.rows(),
                sessionSnapshot.scrollOffset(),
                toolbarHeight.getAsInt(), findVisible
        );
        if (!projection.filter().active()
                && viewport.scrollRow() != sessionSnapshot.scrollOffset()) {
            session.setScrollOffset(viewport.scrollRow());
            sessionSnapshot = session.snapshot();
        }
        State state = new State(sessionSnapshot, projection, viewport);
        latestProjectionRows = projection.rows().size();
        latestViewportCells = viewport.cells().size();
        if (!projection.filter().active()) requestNextPageNearViewport(state, relationSnapshot);
        return state;
    }

    public void select(SFMPath path, SFMScreenPanelBounds bounds) {
        select(path, bounds, SFMExplorerRowSelection.Gesture.REPLACE);
    }

    public void select(SFMPath path, SFMScreenPanelBounds bounds, SFMExplorerRowSelection.Gesture gesture) {
        Objects.requireNonNull(path, "path");
        State state = state(bounds);
        int index = indexOf(state.projection().rows(), path);
        if (index < 0 || state.projection().rows().get(index).loading()) return;
        session.selectRow(path, selectableOrder(state), gesture);
        revealIndex(index, state.viewport());
    }

    /** Scroll a preview into view without changing the document/row selection. */
    public boolean revealPreview(SFMPath path, SFMScreenPanelBounds bounds) {
        State state = state(bounds);
        int index = indexOf(state.projection().rows(), path);
        if (index < 0) return false;
        revealIndex(index, state.viewport());
        return true;
    }

    public void moveSelection(int delta, SFMScreenPanelBounds bounds) {
        moveSelection(delta, bounds, SFMExplorerRowSelection.Gesture.REPLACE);
    }

    public void moveSelection(int delta, SFMScreenPanelBounds bounds, SFMExplorerRowSelection.Gesture gesture) {
        State state = state(bounds);
        List<SFMExplorerProjection.Row> rows = state.projection().rows();
        if (rows.isEmpty()) return;
        int current = state.selectedPath().map(path -> indexOf(rows, path)).orElse(-1);
        int next = nextSelectableIndex(rows, current, delta);
        if (next < 0) return;
        session.selectRow(rows.get(next).path(), selectableOrder(state), gesture);
        revealIndex(next, state.viewport());
    }

    public void selectFirst(SFMScreenPanelBounds bounds) {
        selectBoundary(bounds, false);
    }

    public void selectLast(SFMScreenPanelBounds bounds) {
        selectBoundary(bounds, true);
    }

    public void selectBoundary(SFMScreenPanelBounds bounds, boolean last, SFMExplorerRowSelection.Gesture gesture) {
        State state = state(bounds);
        var rows = state.projection().rows();
        int index = boundarySelectableIndex(rows, last);
        if (index < 0) return;
        session.selectRow(rows.get(index).path(), selectableOrder(state), gesture);
        revealIndex(index, state.viewport());
    }

    private static List<SFMPath> selectableOrder(State state) {
        return state.projection().rows().stream().filter(row -> !row.loading())
                .flatMap(row -> row.paths().stream()).distinct().toList();
    }

    public void changeCompaction(ca.teamdman.sfm.client.explorer.lazy.SFMExplorerCompaction.Options options,
                                 Optional<SFMPath> local, SFMScreenPanelBounds bounds) {
        var before = state(bounds);
        var anchor = SFMExplorerScrollAnchor.capture(before.projection().rows(), before.viewport().scrollRow(),
                before.viewport().columns(), local);
        // Make previously implicit links explicit before a local/panel-wide split. No resolver IO.
        for (var row : before.projection().rows()) for (int i = 0; i + 1 < row.segments().size(); i++)
            if (!options.allows(row.segments().get(i).path())) session.expand(row.segments().get(i).path());
        session.setSettings(session.snapshot().settings().withCompaction(options));
        var after = state(bounds);
        session.setScrollOffset(Math.min(after.viewport().maximumScrollRow(),
                anchor.restore(after.projection().rows(), after.viewport().columns())));
    }

    public synchronized ScrollTrace scrollRows(int delta, SFMScreenPanelBounds bounds) {
        long callbackNanoTime = System.nanoTime();
        State state = state(bounds);
        int before = state.viewport().scrollRow();
        int after = Math.max(0, Math.min(
                state.viewport().maximumScrollRow(),
                before + delta
        ));
        session.setScrollOffset(after);
        long modelMutationNanoTime = System.nanoTime();
        ScrollTrace trace = new ScrollTrace(
                nextScrollSequence++,
                delta,
                before,
                after,
                callbackNanoTime,
                modelMutationNanoTime,
                Optional.empty(),
                Optional.empty(),
                Optional.empty()
        );
        if (scrollTraces.size() == MAXIMUM_SCROLL_TRACES) scrollTraces.remove(0);
        scrollTraces.add(trace);
        return trace;
    }

    public synchronized void observeVisibleFrame(int visibleScrollRow) {
        long frame = nextFrameSequence++;
        long observedNanoTime = System.nanoTime();
        for (int index = 0; index < scrollTraces.size(); index++) {
            ScrollTrace trace = scrollTraces.get(index);
            if (trace.firstObservedFrame().isPresent()) continue;
            scrollTraces.set(index, new ScrollTrace(
                    trace.sequence(),
                    trace.requestedDelta(),
                    trace.beforeRow(),
                    trace.afterRow(),
                    trace.callbackNanoTime(),
                    trace.modelMutationNanoTime(),
                    Optional.of(frame),
                    Optional.of(observedNanoTime),
                    Optional.of(visibleScrollRow)
            ));
        }
    }

    public synchronized List<ScrollTrace> scrollTraceSnapshot() {
        return List.copyOf(scrollTraces);
    }

    public ProjectionWorkTelemetry projectionWorkTelemetry() {
        return new ProjectionWorkTelemetry(
                projectionBuilds,
                projectionCacheHits,
                inlineLoadingBuilds,
                inlineLoadingCacheHits,
                latestProjectionRows,
                latestViewportCells
        );
    }

    public boolean emitExpandSelected(SFMScreenPanelBounds bounds) {
        return emitSelected(bounds, NodeOperation.EXPAND);
    }

    public boolean emitCollapseSelected(SFMScreenPanelBounds bounds) {
        return emitSelected(bounds, NodeOperation.COLLAPSE);
    }

    /**
     * Handles one tree-navigation Left Arrow operation.
     *
     * <p>An expanded selection collapses in place. Otherwise, when the
     * selected row is visible because its projected parent is expanded, the
     * same operation moves the cursor to that parent and emits its collapse
     * action. Selection remains panel-local state while the collapse continues
     * through the registered semantic action surface.</p>
     */
    public boolean emitCollapseSelectedOrParent(SFMScreenPanelBounds bounds) {
        State state = state(bounds);
        Optional<SFMExplorerProjection.Row> selected = state.selectedRow();
        if (selected.isEmpty()) return false;
        SFMExplorerProjection.Row selectedRow = selected.orElseThrow();
        if (state.session().expanded().contains(selectedRow.path())) {
            return emitNode(state, selectedRow, NodeOperation.COLLAPSE);
        }

        List<SFMExplorerProjection.Row> rows = state.projection().rows();
        int selectedIndex = indexOf(rows, selectedRow.path());
        int parentIndex = projectedParentIndex(rows, selectedIndex);
        if (parentIndex >= 0) {
            SFMExplorerProjection.Row parent = rows.get(parentIndex);
            if (state.session().expanded().contains(parent.path())) {
                session.navigateTo(parent.path());
                revealIndex(parentIndex, state.viewport());
                return emitNode(state, parent, NodeOperation.COLLAPSE);
            }
        }

        return emitNode(state, selectedRow, NodeOperation.COLLAPSE);
    }

    public boolean emitToggleSelected(SFMScreenPanelBounds bounds) {
        return emitSelected(bounds, NodeOperation.TOGGLE);
    }

    public boolean emitRefreshSelected(SFMScreenPanelBounds bounds) {
        State state = state(bounds);
        Optional<SFMExplorerProjection.Row> selected = state.selectedRow();
        if (selected.isEmpty()) return false;
        actionSink.submit(SFMExplorerPanelActions.nodeRefresh(
                state.session().id(),
                selected.orElseThrow().path()
        ));
        return true;
    }

    public void emitRootAdd(SFMPath path) {
        actionSink.submit(SFMExplorerPanelActions.rootAdd(explorerId(), path));
    }

    public void emitRootRemove(SFMPath path) {
        actionSink.submit(SFMExplorerPanelActions.rootRemove(explorerId(), path));
    }

    public void emitLocationEdit() {
        actionSink.submit(SFMExplorerPanelActions.locationEdit(explorerId()));
    }

    public boolean emitOpenSelected(
            SFMScreenPanelBounds bounds,
            SFMExplorerPreviewPlacement.Mode mode
    ) {
        State state = state(bounds);
        Optional<SFMExplorerProjection.Row> selected = state.selectedRow();
        if (selected.isEmpty() || !selected.orElseThrow().entry().opensOnActivate()) return false;
        actionSink.submit(SFMExplorerPanelActions.pathOpen(selected.orElseThrow().path(), mode));
        return true;
    }

    public void emitDroppedRoots(List<Path> paths) {
        Objects.requireNonNull(paths, "paths");
        paths.stream()
                .map(path -> SFMPath.fromNative(Objects.requireNonNull(path, "path")))
                .forEach(this::emitRootAdd);
    }

    public void emitViewSet(SFMExplorerProjection.View view) {
        actionSink.submit(SFMExplorerPanelActions.viewSet(explorerId(), view));
    }

    public void emitSortSet(SFMExplorerProjection.Sort sort) {
        actionSink.submit(SFMExplorerPanelActions.sortSet(explorerId(), sort));
    }

    public void emitGroupSet(SFMExplorerProjection.Group group) {
        actionSink.submit(SFMExplorerPanelActions.groupSet(explorerId(), group));
    }

    public void emitHoistSet(SFMExplorerProjection.Hoist hoist) {
        actionSink.submit(SFMExplorerPanelActions.hoistSet(explorerId(), hoist));
    }

    public void emitPathDisplaySet(SFMExplorerProjection.PathDisplay pathDisplay) {
        actionSink.submit(SFMExplorerPanelActions.pathDisplaySet(explorerId(), pathDisplay));
    }

    public void emitFilterSet(String query) {
        actionSink.submit(SFMExplorerPanelActions.matchQuery(explorerId(), false, query,
                session.snapshot().settings().filterOptions()));
    }

    public void emitFilterClear() {
        actionSink.submit(SFMExplorerPanelActions.filterClear(explorerId()));
    }

    public void submitSearchCommand(String command) { actionSink.submit(command); }

    public void emitFindSet(String query, ca.teamdman.sfm.client.search.SFMTextMatchOptions options) {
        actionSink.submit(query.isEmpty()
                ? "sfm action invoke sfm:explorer/find/clear " + ca.teamdman.sfm.client.explorer.SFMEntitySelector.exact(
                        ca.teamdman.sfm.client.explorer.SFMEntitySelector.Domain.EXPLORER, explorerId().value()).canonical()
                : SFMExplorerPanelActions.matchQuery(explorerId(), true, query, options));
    }

    private boolean emitSelected(SFMScreenPanelBounds bounds, NodeOperation operation) {
        State state = state(bounds);
        Optional<SFMExplorerProjection.Row> selected = state.selectedRow();
        if (selected.isEmpty()) return false;
        return emitNode(state, selected.orElseThrow(), operation);
    }

    private boolean emitNode(
            State state,
            SFMExplorerProjection.Row row,
            NodeOperation operation
    ) {
        if (!row.entry().expandable()) return false;
        SFMPath path = row.path();
        String action = switch (operation) {
            case EXPAND -> SFMExplorerPanelActions.nodeExpand(state.session().id(), path);
            case COLLAPSE -> SFMExplorerPanelActions.nodeCollapse(state.session().id(), path);
            case TOGGLE -> SFMExplorerPanelActions.nodeToggle(state.session().id(), path);
        };
        actionSink.submit(action);
        return true;
    }

    private static int projectedParentIndex(List<SFMExplorerProjection.Row> rows, int selectedIndex) {
        if (selectedIndex <= 0) return -1;
        int parentDepth = rows.get(selectedIndex).depth() - 1;
        if (parentDepth < 0) return -1;
        for (int index = selectedIndex - 1; index >= 0; index--) {
            int candidateDepth = rows.get(index).depth();
            if (candidateDepth == parentDepth) return index;
            if (candidateDepth < parentDepth) return -1;
        }
        return -1;
    }

    private void selectBoundary(SFMScreenPanelBounds bounds, boolean last) {
        selectBoundary(bounds, last, SFMExplorerRowSelection.Gesture.REPLACE);
    }

    private void revealIndex(int index, SFMExplorerPanelViewport.Snapshot viewport) {
        int row = index / viewport.columns();
        if (row < viewport.scrollRow()) {
            session.setScrollOffset(row);
        } else if (row >= viewport.scrollRow() + viewport.visibleGridRows()) {
            session.setScrollOffset(Math.max(0, row - viewport.visibleGridRows() + 1));
        }
    }

    private void retainOrChooseSelection(
            SFMExplorerSession.Snapshot sessionSnapshot,
            SFMExplorerProjection.Result projection
    ) {
        // Projection changes are not selection edits. Preserve hidden membership and cursor.
        if (sessionSnapshot.navigationCursor().isPresent() || sessionSnapshot.rowSelection().isPresent()) return;
        Optional<SFMPath> navigation = sessionSnapshot.navigationCursor()
                .filter(path -> indexOf(projection.rows(), path) >= 0);
        Optional<SFMPath> next = navigation.or(() -> projection.rows().stream()
                .filter(row -> !row.loading())
                .findFirst()
                .map(SFMExplorerProjection.Row::path));
        if (next.isPresent()) {
            session.navigateTo(next.orElseThrow());
        } else {
            session.clearNavigationCursor();
        }
    }

    /**
     * Requests at most one append page per panel update. A parent qualifies only
     * when its published direct-child boundary is visible or one viewport row
     * ahead. Collapsed parents and unmaterialized descendants therefore cannot
     * trigger recursive prefetch.
     */
    private void requestNextPageNearViewport(
            State state,
            SFMChildRelationRepository.Snapshot relations
    ) {
        if (state.viewport().capacity() == 0 || state.projection().rows().isEmpty()) return;
        int visibleEnd = state.viewport().lastVisibleIndexExclusive();
        int oneViewportRow = Math.max(1, state.viewport().columns());
        for (var entry : relations.pageStates().entrySet().stream()
                .filter(entry -> entry.getValue().materialization()
                        == SFMChildRelationRepository.PageState.Materialization.MATERIALIZED)
                .filter(entry -> entry.getValue().continuation().isPresent())
                .sorted(Comparator.comparing(entry -> entry.getKey().canonical()))
                .toList()) {
            if (!continuationBoundaryIsNear(
                        relations.relation().childrenOf(entry.getKey()),
                        state.projection().rows(),
                        visibleEnd,
                        oneViewportRow
                )) continue;
            if (session.requestNextPage(entry.getKey(), loader, paginationPageSize).isPresent()) return;
        }
    }

    private static boolean continuationBoundaryIsNear(
            Set<SFMPath> publishedChildren,
            List<SFMExplorerProjection.Row> rows,
            int visibleEnd,
            int lookahead
    ) {
        int finalChildIndex = -1;
        for (int index = 0; index < rows.size(); index++) {
            if (publishedChildren.contains(rows.get(index).path())) finalChildIndex = index;
        }
        if (finalChildIndex < 0) return false;
        int boundary = finalChildIndex + 1;
        return visibleEnd <= boundary && boundary - visibleEnd <= lookahead;
    }

    private SFMExplorerId explorerId() {
        return session.snapshot().id();
    }

    private static int indexOf(List<SFMExplorerProjection.Row> rows, SFMPath path) {
        for (int index = 0; index < rows.size(); index++) {
            if (rows.get(index).contains(path)) return index;
        }
        return -1;
    }

    private static int nextSelectableIndex(
            List<SFMExplorerProjection.Row> rows,
            int current,
            int delta
    ) {
        if (rows.isEmpty()) return -1;
        int direction = Integer.compare(delta, 0);
        if (direction == 0) return current >= 0 && !rows.get(current).loading() ? current : -1;
        int remaining = Math.abs(delta);
        int index = current;
        while (remaining > 0) {
            index += direction;
            while (index >= 0 && index < rows.size() && rows.get(index).loading()) index += direction;
            if (index < 0 || index >= rows.size()) return boundarySelectableIndex(rows, direction > 0);
            remaining--;
        }
        return index;
    }

    private static int boundarySelectableIndex(List<SFMExplorerProjection.Row> rows, boolean last) {
        if (last) {
            for (int index = rows.size() - 1; index >= 0; index--) {
                if (!rows.get(index).loading()) return index;
            }
        } else {
            for (int index = 0; index < rows.size(); index++) {
                if (!rows.get(index).loading()) return index;
            }
        }
        return -1;
    }

    private SFMExplorerProjection.Result withCachedInlineLoadingRows(
            SFMExplorerProjection.Result projection,
            SFMExplorerSession.Snapshot session,
            List<SFMExplorerSession.RequestObservation> activeRequests
    ) {
        if (activeRequests.isEmpty()) return projection;
        if (projection == cachedInlineLoadingBase
                && activeRequests.equals(cachedInlineLoadingRequests)
                && cachedInlineLoadingProjection != null) {
            inlineLoadingCacheHits++;
            return cachedInlineLoadingProjection;
        }
        SFMExplorerProjection.Result decorated = withInlineLoadingRows(
                projection,
                session,
                activeRequests
        );
        cachedInlineLoadingBase = projection;
        cachedInlineLoadingRequests = List.copyOf(activeRequests);
        cachedInlineLoadingProjection = decorated;
        inlineLoadingBuilds++;
        return decorated;
    }

    private static SFMExplorerProjection.Result withInlineLoadingRows(
            SFMExplorerProjection.Result projection,
            SFMExplorerSession.Snapshot session,
            List<SFMExplorerSession.RequestObservation> activeRequests
    ) {
        if (activeRequests.isEmpty()) return projection;
        ArrayList<SFMExplorerProjection.Row> rows = new ArrayList<>(projection.rows());
        Map<SFMPath, SFMExplorerSession.RequestObservation> byParent = new TreeMap<>();
        activeRequests.forEach(observation -> byParent.put(observation.evidence().parent(), observation));
        for (var request : byParent.entrySet()) {
            SFMPath parent = request.getKey();
            int parentIndex = indexOf(rows, parent);
            int parentDepth;
            int insertion;
            if (parentIndex >= 0) {
                SFMExplorerProjection.Row parentRow = rows.get(parentIndex);
                if (!parentRow.expanded()) continue;
                parentDepth = parentRow.depth();
                insertion = request.getValue().evidence().mode()
                        == SFMChildRelationRepository.RequestMode.REPLACE
                        ? parentIndex + 1
                        : afterSubtree(rows, parentIndex);
            } else if (session.roots().contains(parent)) {
                parentDepth = -1;
                insertion = 0;
            } else {
                continue;
            }
            SFMPath loadingPath = loadingPath(parent, request.getValue().evidence().relationRequestId());
            SFMExplorerEntry entry = SFMExplorerEntry.simple(
                    loadingPath,
                    "Loading children...",
                    false,
                    Optional.of("minecraft:clock")
            );
            rows.add(insertion, new SFMExplorerProjection.Row(
                    loadingPath,
                    entry,
                    parentDepth + 1,
                    false,
                    false,
                    entry.sortKey(SFMExplorerEntry.SORT_NAME),
                    SFMExplorerProjection.FilterRole.NONE,
                    SFMExplorerProjection.RowKind.LOADING
            ));
        }
        return new SFMExplorerProjection.Result(
                projection.settings(),
                projection.relationRevision(),
                rows,
                projection.diagnostics(),
                projection.filter(),
                projection.matchEvidence()
        );
    }

    private static int afterSubtree(List<SFMExplorerProjection.Row> rows, int parentIndex) {
        int depth = rows.get(parentIndex).depth();
        int index = parentIndex + 1;
        while (index < rows.size() && rows.get(index).depth() > depth) index++;
        return index;
    }

    private static SFMPath loadingPath(SFMPath parent, long requestId) {
        return new SFMPath(
                SFMPath.Kind.CONTRIBUTED,
                "explorer-ui",
                "loading",
                List.of(parent.canonical(), Long.toString(requestId)),
                Optional.empty(),
                false
        );
    }

    private enum NodeOperation {
        EXPAND,
        COLLAPSE,
        TOGGLE
    }
}
