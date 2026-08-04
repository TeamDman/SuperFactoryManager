package ca.teamdman.sfm.client.terminal;

import java.util.Optional;
import java.util.List;

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

    /** Cached server/Java intersection shown by this panel's renderer selector. */
    default List<SFMTerminalRendererOption> rendererOptions() {
        return List.of();
    }

    /** Cached server/Java intersection shown by this panel's transport selector. */
    default List<SFMTerminalTransportOption> transportOptions() {
        return List.of();
    }

    /** Latest user renderer request, including while a full resync is pending. */
    default String requestedRendererId() {
        return "";
    }

    /** Latest user request, including while its replacement stream awaits a full resync. */
    default String requestedTransportId() {
        return "";
    }

    /** Renderer of the last accepted frame; stable during an in-flight switch. */
    default Optional<String> activeRendererId() {
        return Optional.empty();
    }

    /** Transport of the last accepted frame; remains stable during an in-flight switch. */
    default Optional<String> activeTransportId() {
        return Optional.empty();
    }

    default SFMTerminalPresentationTransitionState presentationState() {
        return new SFMTerminalPresentationTransitionState(
                SFMTerminalPresentationSelection.DEFAULT, Optional.empty(), Optional.empty());
    }

    /** Requests a non-blocking atomic presentation replacement without replacing the PTY/session. */
    default SFMTerminalPresentationChangeResult requestRenderer(String rendererId) {
        return SFMTerminalPresentationChangeResult.rejected(
                "Terminal renderer selection is unavailable");
    }

    /** Requests a non-blocking atomic presentation replacement without replacing the PTY/session. */
    default SFMTerminalPresentationChangeResult requestTransport(String transportId) {
        return SFMTerminalPresentationChangeResult.rejected(
                "Terminal transport selection is unavailable");
    }

    /** True while an empty latest-frame handoff may reuse the renderer's current texture. */
    default boolean canPresentRetainedFrame() {
        return isConnected();
    }

    int logicalWidth();

    int logicalHeight();

    String contentForAutomation();

    /** Optional machine-readable proof supplied by push-capable transports. */
    default String assertPushEvidenceForAutomation(boolean reconnectExpected) {
        throw new IllegalStateException("Terminal service does not expose push evidence");
    }

    boolean cancel();

    void reconnect();

    @Override
    void close();
}
