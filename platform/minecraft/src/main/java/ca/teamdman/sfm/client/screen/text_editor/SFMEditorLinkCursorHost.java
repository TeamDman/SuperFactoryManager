package ca.teamdman.sfm.client.screen.text_editor;

import org.lwjgl.glfw.GLFW;

/** Owns one reusable GLFW hand cursor for a hosted editor-panel lifecycle. */
final class SFMEditorLinkCursorHost implements AutoCloseable {
    private static final NativeApi GLFW_API = new NativeApi() {
        @Override public long createHandCursor() {
            return GLFW.glfwCreateStandardCursor(GLFW.GLFW_HAND_CURSOR);
        }

        @Override public void setCursor(long window, long cursor) {
            GLFW.glfwSetCursor(window, cursor);
        }

        @Override public void destroyCursor(long cursor) {
            GLFW.glfwDestroyCursor(cursor);
        }
    };

    private final long window;
    private final NativeApi api;
    private final long hand;
    private boolean selected;
    private boolean closed;

    static SFMEditorLinkCursorHost live(long window) {
        return new SFMEditorLinkCursorHost(window, GLFW_API);
    }

    SFMEditorLinkCursorHost(long window, NativeApi api) {
        if (window == 0L) throw new IllegalArgumentException("GLFW window handle must be non-zero");
        this.window = window;
        this.api = java.util.Objects.requireNonNull(api, "api");
        this.hand = api.createHandCursor();
    }

    void setLink(boolean link) {
        if (closed || selected == link) return;
        selected = link;
        api.setCursor(window, link ? hand : 0L);
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        api.setCursor(window, 0L);
        if (hand != 0L) api.destroyCursor(hand);
    }

    interface NativeApi {
        long createHandCursor();

        void setCursor(long window, long cursor);

        void destroyCursor(long cursor);
    }
}
