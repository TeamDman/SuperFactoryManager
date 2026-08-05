package ca.teamdman.sfm.client.terminal;

import java.util.Optional;
import java.util.List;
import java.util.function.Consumer;

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

    /** Resize with zero meaning automatic font fitting and a positive value meaning exact pixels. */
    default boolean resize(int columns, int rows, int panelWidth, int panelHeight, int fontPixelSize) {
        return resize(columns, rows, panelWidth, panelHeight);
    }

    /**
     * Resize while preserving which tuning axes are automatic versus explicit.
     * The effective dimensions remain suitable for legacy backends; typed Vox
     * backends override this method to transmit both the modes and values.
     */
    default boolean resize(
            SFMTerminalTuningSettings requested,
            SFMTerminalTuningSettings.Effective effective
    ) {
        return resize(
                effective.columns(),
                effective.rows(),
                effective.surfaceWidth(),
                effective.surfaceHeight(),
                effective.fontPixelSize()
        );
    }

    /** Latest typed resize/tuning rejection; independent from connection health. */
    default Optional<SFMTerminalTuningRejection> tuningFailure() {
        return Optional.empty();
    }

    /** True until the latest typed resize/tuning request is accepted or rejected. */
    default boolean tuningPending() {
        return false;
    }

    default Optional<String> tuningFailureMessage() {
        return tuningFailure().map(rejection -> rejection.error().message());
    }

    /** Latest accepted Rust-raster stream diagnostics, when this backend exposes them. */
    default Optional<SFMTerminalPresentationDiagnostics> presentationDiagnostics() {
        return Optional.empty();
    }

    boolean sendKey(int keyCode, int modifiers, boolean pressed, boolean repeat);

    boolean sendText(String text);

    boolean sendMouse(int x, int y, int buttons, int button, boolean pressed, boolean motion,
                      int wheelX, int wheelY);

    /**
     * Send mouse input and report whether a child TUI consumed it. Legacy test
     * doubles behave like an ordinary shell unless they override this seam.
     */
    default boolean sendMouse(
            int x,
            int y,
            int buttons,
            int button,
            boolean pressed,
            boolean motion,
            int wheelX,
            int wheelY,
            Consumer<SFMTerminalInputDisposition> completion
    ) {
        boolean accepted = sendMouse(x, y, buttons, button, pressed, motion, wheelX, wheelY);
        if (accepted) completion.accept(SFMTerminalInputDisposition.NO_CHANGE);
        return accepted;
    }

    /** Latest Rust-authoritative selection, independent from raster publication. */
    default Optional<SFMTerminalSelection> selection() {
        return Optional.empty();
    }

    /**
     * Monotonic client-side identity for the currently connected remote
     * terminal. Pending confirmations must be discarded when this changes.
     */
    default long interactionEpoch() {
        return 0L;
    }

    /** Atomically copy and clear the Rust-owned selection without rerasterizing unchanged glyphs. */
    default boolean copySelection(Consumer<SFMTerminalCopyResult> completion) {
        return false;
    }

    /** Paste immediately when safe, or return an opaque confirmation identity for multiline text. */
    default boolean pasteWithGuard(String text, Consumer<SFMTerminalPasteResult> completion) {
        return false;
    }

    /** Release exactly one retained multiline paste after explicit Java-side confirmation. */
    default boolean pasteWithoutGuard(
            String text,
            String approvedContentId,
            Consumer<SFMTerminalPasteResult> completion
    ) {
        return false;
    }

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
