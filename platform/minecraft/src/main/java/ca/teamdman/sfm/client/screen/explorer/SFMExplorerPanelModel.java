package ca.teamdman.sfm.client.screen.explorer;

import ca.teamdman.sfm.client.explorer.SFMExplorerId;
import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.explorer.SFMChildRelationRepository;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerProjection;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerSession;
import ca.teamdman.sfm.client.explorer.lazy.SFMLazyExplorerLoader;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanelBounds;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

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

        public Optional<SFMExplorerProjection.Row> selectedRow() {
            Optional<SFMPath> selectedPath = selectedPath();
            if (selectedPath.isEmpty()) return Optional.empty();
            return projection.rows().stream()
                    .filter(row -> row.path().equals(selectedPath.orElseThrow()))
                    .findFirst();
        }
    }

    private final SFMExplorerSession session;
    private final SFMLazyExplorerLoader loader;
    private final SFMExplorerSemanticActionSink actionSink;
    private final int paginationPageSize;
    private final ArrayList<ScrollTrace> scrollTraces = new ArrayList<>();
    private long nextScrollSequence = 1;
    private long nextFrameSequence = 1;

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
        SFMChildRelationRepository.Snapshot relationSnapshot = loader.relationSnapshot();
        SFMExplorerProjection.Result projection = SFMExplorerProjection.project(
                sessionSnapshot,
                relationSnapshot,
                loader.entrySnapshot()
        );
        retainOrChooseSelection(sessionSnapshot, projection);
        sessionSnapshot = session.snapshot();
        SFMExplorerPanelViewport.Snapshot viewport = SFMExplorerPanelViewport.calculate(
                bounds,
                projection.settings().view(),
                projection.rows(),
                sessionSnapshot.scrollOffset()
        );
        if (viewport.scrollRow() != sessionSnapshot.scrollOffset()) {
            session.setScrollOffset(viewport.scrollRow());
            sessionSnapshot = session.snapshot();
        }
        State state = new State(sessionSnapshot, projection, viewport);
        if (!projection.filter().active()) requestNextPageNearViewport(state, relationSnapshot);
        return state;
    }

    public void select(SFMPath path, SFMScreenPanelBounds bounds) {
        Objects.requireNonNull(path, "path");
        State state = state(bounds);
        int index = indexOf(state.projection().rows(), path);
        if (index < 0) return;
        session.navigateTo(path);
        revealIndex(index, state.viewport());
    }

    public void moveSelection(int delta, SFMScreenPanelBounds bounds) {
        State state = state(bounds);
        List<SFMExplorerProjection.Row> rows = state.projection().rows();
        if (rows.isEmpty()) return;
        int current = state.selectedPath().map(path -> indexOf(rows, path)).orElse(-1);
        int next = Math.max(0, Math.min(rows.size() - 1, current + delta));
        session.navigateTo(rows.get(next).path());
        revealIndex(next, state.viewport());
    }

    public void selectFirst(SFMScreenPanelBounds bounds) {
        selectBoundary(bounds, false);
    }

    public void selectLast(SFMScreenPanelBounds bounds) {
        selectBoundary(bounds, true);
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

    public boolean emitExpandSelected(SFMScreenPanelBounds bounds) {
        return emitSelected(bounds, NodeOperation.EXPAND);
    }

    public boolean emitCollapseSelected(SFMScreenPanelBounds bounds) {
        return emitSelected(bounds, NodeOperation.COLLAPSE);
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
        if (selected.isEmpty() || selected.orElseThrow().entry().expandable()) return false;
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
        actionSink.submit(SFMExplorerPanelActions.filterSet(explorerId(), query));
    }

    public void emitFilterClear() {
        actionSink.submit(SFMExplorerPanelActions.filterClear(explorerId()));
    }

    private boolean emitSelected(SFMScreenPanelBounds bounds, NodeOperation operation) {
        State state = state(bounds);
        Optional<SFMExplorerProjection.Row> selected = state.selectedRow();
        if (selected.isEmpty() || !selected.orElseThrow().entry().expandable()) return false;
        SFMPath path = selected.orElseThrow().path();
        String action = switch (operation) {
            case EXPAND -> SFMExplorerPanelActions.nodeExpand(state.session().id(), path);
            case COLLAPSE -> SFMExplorerPanelActions.nodeCollapse(state.session().id(), path);
            case TOGGLE -> SFMExplorerPanelActions.nodeToggle(state.session().id(), path);
        };
        actionSink.submit(action);
        return true;
    }

    private void selectBoundary(SFMScreenPanelBounds bounds, boolean last) {
        State state = state(bounds);
        List<SFMExplorerProjection.Row> rows = state.projection().rows();
        if (rows.isEmpty()) return;
        int index = last ? rows.size() - 1 : 0;
        session.navigateTo(rows.get(index).path());
        revealIndex(index, state.viewport());
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
        if (sessionSnapshot.navigationCursor().isPresent()
                && indexOf(projection.rows(), sessionSnapshot.navigationCursor().orElseThrow()) >= 0) return;
        if (projection.filter().active() && sessionSnapshot.navigationCursor().isPresent()) return;
        Optional<SFMPath> navigation = sessionSnapshot.navigationCursor()
                .filter(path -> indexOf(projection.rows(), path) >= 0);
        Optional<SFMPath> next = navigation.or(() -> projection.rows().stream()
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
            if (rows.get(index).path().equals(path)) return index;
        }
        return -1;
    }

    private enum NodeOperation {
        EXPAND,
        COLLAPSE,
        TOGGLE
    }
}
