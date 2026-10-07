package ca.teamdman.sfm.client.screen.workspace;

import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import org.lwjgl.glfw.GLFW;

import java.util.LinkedHashSet;
import java.util.Set;

/** Owns one reusable set of standard GLFW cursor handles for a workspace lifecycle. */
final class SFMWorkspaceGlfwCursorHost implements SFMWorkspaceDividerInteraction.CursorSink {
    private static final NativeApi GLFW_API = new NativeApi() {
        @Override
        public long createStandardCursor(int shape) {
            return GLFW.glfwCreateStandardCursor(shape);
        }

        @Override
        public void setCursor(long window, long cursor) {
            GLFW.glfwSetCursor(window, cursor);
        }

        @Override
        public void destroyCursor(long cursor) {
            GLFW.glfwDestroyCursor(cursor);
        }
    };

    private final long window;
    private final NativeApi api;
    private final long horizontal;
    private final long vertical;
    private final long both;
    private SFMWorkspaceDividerCursor selected = SFMWorkspaceDividerCursor.DEFAULT;
    private boolean closed;

    static SFMWorkspaceGlfwCursorHost live(long window) {
        return new SFMWorkspaceGlfwCursorHost(window, GLFW_API);
    }

    SFMWorkspaceGlfwCursorHost(long window, NativeApi api) {
        if (window == 0L) throw new IllegalArgumentException("GLFW window handle must be non-zero");
        this.window = window;
        this.api = api;
        this.horizontal = api.createStandardCursor(standardShape(SFMWorkspaceDividerCursor.HORIZONTAL_RESIZE));
        this.vertical = api.createStandardCursor(standardShape(SFMWorkspaceDividerCursor.VERTICAL_RESIZE));
        this.both = api.createStandardCursor(standardShape(SFMWorkspaceDividerCursor.RESIZE_BOTH));
    }

    @Override
    public void set(SFMWorkspaceDividerCursor cursor) {
        if (closed || selected == cursor) return;
        selected = cursor;
        api.setCursor(window, handle(cursor));
    }

    @Override
    public void reassert(SFMWorkspaceDividerCursor cursor) {
        if (closed || cursor == SFMWorkspaceDividerCursor.DEFAULT) return;
        api.setCursor(window, handle(cursor));
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        api.setCursor(window, 0L);
        Set<Long> owned = new LinkedHashSet<>();
        owned.add(horizontal);
        owned.add(vertical);
        owned.add(both);
        owned.stream().filter(handle -> handle != 0L).forEach(api::destroyCursor);
    }

    private long handle(SFMWorkspaceDividerCursor cursor) {
        return switch (cursor) {
            case DEFAULT -> 0L;
            case HORIZONTAL_RESIZE -> horizontal;
            case VERTICAL_RESIZE -> vertical;
            case RESIZE_BOTH -> both;
        };
    }

    /** Later branches may select richer resize-all shapes while retaining this owned-handle seam. */
    @MCVersionDependentBehaviour
    private static int standardShape(SFMWorkspaceDividerCursor cursor) {
        return switch (cursor) {
            case HORIZONTAL_RESIZE -> GLFW.GLFW_HRESIZE_CURSOR;
            case VERTICAL_RESIZE -> GLFW.GLFW_VRESIZE_CURSOR;
            case RESIZE_BOTH -> GLFW.GLFW_CROSSHAIR_CURSOR;
            case DEFAULT -> GLFW.GLFW_ARROW_CURSOR;
        };
    }

    interface NativeApi {
        long createStandardCursor(int shape);

        void setCursor(long window, long cursor);

        void destroyCursor(long cursor);
    }
}
