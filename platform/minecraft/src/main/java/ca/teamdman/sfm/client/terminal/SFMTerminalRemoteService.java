package ca.teamdman.sfm.client.terminal;

import java.util.Optional;

/** Optional remote-terminal capability kept free of the Vox generated API. */
public interface SFMTerminalRemoteService extends SFMTerminalService, AutoCloseable {
    /** Request a non-blocking connection attempt for a panel that is already open. */
    void requestConnect();

    boolean isConnected();

    boolean isConnecting();

    Optional<String> failureMessage();

    boolean resize(int columns, int rows);

    /** Resize logical cells and declare the physical panel target separately. */
    default boolean resize(int columns, int rows, int panelWidth, int panelHeight) {
        return resize(columns, rows);
    }

    boolean sendKey(int keyCode, int modifiers, boolean pressed, boolean repeat);

    boolean sendText(String text);

    boolean sendMouse(int x, int y, int buttons, int button, boolean pressed, boolean motion,
                      int wheelX, int wheelY);

    Optional<SFMTerminalFrame> latestFrame();

    /** True while an empty latest-frame handoff may reuse the renderer's current texture. */
    default boolean canPresentRetainedFrame() {
        return isConnected();
    }

    int logicalWidth();

    int logicalHeight();

    String contentForAutomation();

    boolean cancel();

    void reconnect();

    @Override
    void close();
}
