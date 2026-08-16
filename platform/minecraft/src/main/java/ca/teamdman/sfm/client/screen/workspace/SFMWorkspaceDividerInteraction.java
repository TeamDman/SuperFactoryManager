package ca.teamdman.sfm.client.screen.workspace;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Pure pointer-capture state machine; native cursor ownership is injected at the edge. */
public final class SFMWorkspaceDividerInteraction implements AutoCloseable {
    public static final int PRIMARY_BUTTON = 0;

    private final Host host;
    private final CursorSink cursorSink;
    private SFMWorkspaceDividerHit hover = SFMWorkspaceDividerHit.empty();
    private SFMWorkspaceDividerCursor selectedCursor = SFMWorkspaceDividerCursor.DEFAULT;
    private @Nullable Capture capture;
    private double lastMouseX;
    private double lastMouseY;
    private boolean closed;

    public SFMWorkspaceDividerInteraction(Host host, CursorSink cursorSink) {
        this.host = Objects.requireNonNull(host, "host");
        this.cursorSink = Objects.requireNonNull(cursorSink, "cursorSink");
    }

    public void pointerMoved(double mouseX, double mouseY) {
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        if (closed || capture != null) return;
        hover = hit(mouseX, mouseY);
        selectCursor(hover.cursor());
    }

    /** Captures exact divider identities before any child panel receives the click. */
    public boolean pointerPressed(double mouseX, double mouseY, int button) {
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        if (closed || capture != null || button != PRIMARY_BUTTON) return false;
        SFMWorkspaceDividerHit pressed = hit(mouseX, mouseY);
        if (pressed.isEmpty()) {
            hover = pressed;
            selectCursor(SFMWorkspaceDividerCursor.DEFAULT);
            return false;
        }
        SFMWorkspaceLayout layout = host.layout();
        var session = layout.captureDividerResize(
                pressed.dividers().stream().map(SFMWorkspaceDivider::id).toList(),
                host.viewport(),
                host.dividerPixels(),
                host.hitSlopPixels(),
                host.minimumPanelPixels()
        ).orElse(null);
        if (session == null) return false;
        hover = pressed;
        capture = new Capture(
                session,
                mouseX,
                mouseY,
                button,
                pressed.cursor(),
                session.currentBounds());
        selectCursor(pressed.cursor());
        return true;
    }

    /** Uses absolute displacement from pointer-down and remains captured outside the hit region. */
    public boolean pointerDragged(double mouseX, double mouseY, int button) {
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        Capture active = capture;
        if (closed || active == null || button != active.button) return false;
        int deltaX = roundedDelta(mouseX - active.startX);
        int deltaY = roundedDelta(mouseY - active.startY);
        SFMWorkspaceDividerResizeResult result = host.layout().updateDividerResize(
                active.session, deltaX, deltaY);
        if (result.status() == SFMWorkspaceDividerResizeResult.Status.STALE) {
            host.layout().abandonDividerResize(active.session);
            capture = null;
            hover = SFMWorkspaceDividerHit.empty();
            selectCursor(SFMWorkspaceDividerCursor.DEFAULT);
            return true;
        }
        if (!active.lastObservedBounds.equals(result.afterBounds())) {
            active.lastObservedBounds = result.afterBounds();
            host.dividerLayoutChanged();
        }
        return true;
    }

    public boolean pointerReleased(double mouseX, double mouseY, int button) {
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        Capture active = capture;
        if (closed || active == null || button != active.button) return false;
        host.layout().finishDividerResize(active.session);
        capture = null;
        hover = hit(mouseX, mouseY);
        selectCursor(hover.cursor());
        return true;
    }

    /** Escape and focus loss roll back a valid capture to its exact starting shares. */
    public boolean cancel() {
        Capture active = capture;
        if (active == null) {
            hover = SFMWorkspaceDividerHit.empty();
            selectCursor(SFMWorkspaceDividerCursor.DEFAULT);
            return false;
        }
        boolean restored = host.layout().cancelDividerResize(active.session);
        capture = null;
        hover = SFMWorkspaceDividerHit.empty();
        selectCursor(SFMWorkspaceDividerCursor.DEFAULT);
        if (restored) host.dividerLayoutChanged();
        return true;
    }

    /** A newer composition owns the tree; never restore the stale captured root over it. */
    public void layoutReplaced() {
        if (capture != null) host.layout().abandonDividerResize(capture.session);
        capture = null;
        hover = SFMWorkspaceDividerHit.empty();
        selectCursor(SFMWorkspaceDividerCursor.DEFAULT);
    }

    public void focusLost() {
        cancel();
    }

    public boolean isCaptured() {
        return capture != null;
    }

    public boolean isHoveringDivider() {
        return !hover.isEmpty();
    }

    public void synchronizeLayoutRevision() {
        if (capture != null
                && host.layout().mutationRevision() != capture.session.mutationRevision()) {
            layoutReplaced();
        }
    }

    public Snapshot snapshot() {
        Capture active = capture;
        return new Snapshot(
                selectedCursor,
                hover.dividers().stream().map(SFMWorkspaceDivider::id).toList(),
                active == null ? List.of() : active.session.dividerIds(),
                active == null ? null : active.startX,
                active == null ? null : active.startY,
                lastMouseX,
                lastMouseY,
                active == null ? Map.of() : active.session.beforeBounds(),
                active == null ? Map.of() : active.session.currentBounds(),
                active == null ? Map.of() : active.session.lastAppliedDeltas()
        );
    }

    /**
     * Reasserts a non-default divider cursor after child-panel rendering.
     * Panel-local cursor owners may legitimately restore GLFW's default while
     * rendering; an active divider hover/capture has workspace-level priority.
     */
    public void reassertCursor() {
        if (closed || selectedCursor == SFMWorkspaceDividerCursor.DEFAULT) return;
        cursorSink.reassert(selectedCursor);
    }

    @Override
    public void close() {
        if (closed) return;
        cancel();
        closed = true;
        cursorSink.close();
    }

    private SFMWorkspaceDividerHit hit(double mouseX, double mouseY) {
        return host.layout().hitTestDividers(
                mouseX,
                mouseY,
                host.viewport(),
                host.dividerPixels(),
                host.hitSlopPixels(),
                host.minimumPanelPixels());
    }

    private void selectCursor(SFMWorkspaceDividerCursor cursor) {
        if (selectedCursor == cursor) return;
        selectedCursor = cursor;
        cursorSink.set(cursor);
    }

    private static int roundedDelta(double value) {
        if (value >= Integer.MAX_VALUE) return Integer.MAX_VALUE;
        if (value <= Integer.MIN_VALUE) return Integer.MIN_VALUE;
        return (int) Math.round(value);
    }

    public interface Host {
        SFMWorkspaceLayout layout();

        SFMScreenPanelBounds viewport();

        int dividerPixels();

        int hitSlopPixels();

        int minimumPanelPixels();

        void dividerLayoutChanged();
    }

    public interface CursorSink extends AutoCloseable {
        void set(SFMWorkspaceDividerCursor cursor);

        default void reassert(SFMWorkspaceDividerCursor cursor) {
            set(cursor);
        }

        @Override
        default void close() {
        }
    }

    public record Snapshot(
            SFMWorkspaceDividerCursor cursor,
            List<SFMWorkspaceDividerId> hoveredDividerIds,
            List<SFMWorkspaceDividerId> capturedDividerIds,
            @Nullable Double startMouseX,
            @Nullable Double startMouseY,
            double currentMouseX,
            double currentMouseY,
            Map<SFMWorkspacePanelId, SFMScreenPanelBounds> beforeBounds,
            Map<SFMWorkspacePanelId, SFMScreenPanelBounds> currentBounds,
            Map<SFMWorkspaceDividerId, Integer> appliedDeltas
    ) {
        public Snapshot {
            hoveredDividerIds = List.copyOf(hoveredDividerIds);
            capturedDividerIds = List.copyOf(capturedDividerIds);
            beforeBounds = Map.copyOf(beforeBounds);
            currentBounds = Map.copyOf(currentBounds);
            appliedDeltas = Map.copyOf(appliedDeltas);
        }
    }

    private static final class Capture {
        private final SFMWorkspaceLayout.DividerResizeSession session;
        private final double startX;
        private final double startY;
        private final int button;
        @SuppressWarnings("unused")
        private final SFMWorkspaceDividerCursor cursor;
        private Map<SFMWorkspacePanelId, SFMScreenPanelBounds> lastObservedBounds;

        private Capture(
                SFMWorkspaceLayout.DividerResizeSession session,
                double startX,
                double startY,
                int button,
                SFMWorkspaceDividerCursor cursor,
                Map<SFMWorkspacePanelId, SFMScreenPanelBounds> lastObservedBounds
        ) {
            this.session = session;
            this.startX = startX;
            this.startY = startY;
            this.button = button;
            this.cursor = cursor;
            this.lastObservedBounds = lastObservedBounds;
        }
    }
}
