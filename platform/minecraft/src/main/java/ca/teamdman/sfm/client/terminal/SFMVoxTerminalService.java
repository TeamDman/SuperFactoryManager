package ca.teamdman.sfm.client.terminal;

import ca.teamdman.sfm.SFM;
import org.facet.vox.ConnectionOptions;
import org.facet.vox.ConnectionState;
import org.facet.vox.LaneOptions;
import org.facet.vox.ServiceLane;
import org.facet.vox.VoxConnection;
import org.facet.vox.VoxResult;
import org.facet.vox.generated.TerminalCapabilities;
import org.facet.vox.generated.TerminalClient;
import org.facet.vox.generated.TerminalCancelRequest;
import org.facet.vox.generated.TerminalConnectRequest;
import org.facet.vox.generated.TerminalConnectResult;
import org.facet.vox.generated.TerminalContentRequest;
import org.facet.vox.generated.TerminalContentResult;
import org.facet.vox.generated.TerminalDisconnectRequest;
import org.facet.vox.generated.TerminalError;
import org.facet.vox.generated.TerminalFrameEncoding;
import org.facet.vox.generated.TerminalFrameKind;
import org.facet.vox.generated.TerminalInputResult;
import org.facet.vox.generated.TerminalKeyInput;
import org.facet.vox.generated.TerminalMouseInput;
import org.facet.vox.generated.TerminalResizeRequest;
import org.facet.vox.generated.TerminalResizeResult;
import org.facet.vox.generated.TerminalServiceDescriptor;
import org.facet.vox.generated.TerminalSnapshot;
import org.facet.vox.generated.TerminalSnapshotRequest;
import org.facet.vox.generated.TerminalSurfaceMetrics;
import org.facet.vox.generated.TerminalState;
import org.facet.vox.generated.TerminalTextInput;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Optional Java client for the generated Vox terminal service.
 *
 * <p>Rust owns the PTY, VT state, and rasterization. Java only sends terminal
 * input and retains the latest bounded full-frame PNG for presentation. The
 * Java-local service is a separate explicit REPL surface, never an implicit
 * fallback for this scene.
 */
public final class SFMVoxTerminalService implements SFMTerminalRemoteService {
    private static final int REQUEST_WIDTH = 120;
    private static final int REQUEST_HEIGHT = 40;
    private static final int MAX_FRAME_BYTES = 4 * 1024 * 1024;
    private static final int MAX_CONTENT_CHARS = 256 * 1024;
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration CONTENT_READINESS_TIMEOUT = Duration.ofSeconds(3);
    private static final long FRAME_POLL_MILLIS = 50;

    private final InetSocketAddress endpoint;
    private final ConnectionOptions connectionOptions;
    private final Duration callTimeout;
    private final ExecutorService driver;
    private final ExecutorService connectionDriver;
    private final ScheduledExecutorService poller;
    private final SFMVoxTerminalTelemetry telemetry = new SFMVoxTerminalTelemetry();
    private final Object lock = new Object();
    private VoxConnection connection;
    private ServiceLane lane;
    private TerminalClient client;
    private String sessionId;
    private String failure;
    private TerminalSnapshot latestSnapshot;
    private TerminalContentResult latestContent;
    private long clientSequence;
    private int requestedWidth = REQUEST_WIDTH;
    private int requestedHeight = REQUEST_HEIGHT;
    private int requestedPixelWidth;
    private int requestedPixelHeight;
    private boolean polling;
    private boolean snapshotInFlight;
    private boolean connectionInFlight;
    private long nextConnectionAttemptNanos;
    private boolean closed;

    public SFMVoxTerminalService(InetSocketAddress endpoint) {
        this(endpoint, ConnectionOptions.builder()
                .handshakeTimeout(Duration.ofMillis(500))
                .idleTimeout(DEFAULT_TIMEOUT)
                .closeTimeout(Duration.ofSeconds(1))
                .build(), DEFAULT_TIMEOUT);
    }

    /** Retained as a source-compatible constructor; the fallback is deliberately ignored. */
    public SFMVoxTerminalService(InetSocketAddress endpoint, SFMTerminalService fallbackService) {
        this(endpoint, ConnectionOptions.builder()
                .handshakeTimeout(Duration.ofMillis(500))
                .idleTimeout(DEFAULT_TIMEOUT)
                .closeTimeout(Duration.ofSeconds(1))
                .build(), DEFAULT_TIMEOUT);
    }

    public SFMVoxTerminalService(
            InetSocketAddress endpoint,
            ConnectionOptions connectionOptions,
            Duration callTimeout) {
        this.endpoint = Objects.requireNonNull(endpoint, "endpoint");
        this.connectionOptions = Objects.requireNonNull(connectionOptions, "connectionOptions");
        this.callTimeout = requirePositive(callTimeout, "callTimeout");
        this.driver = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "sfm-vox-terminal-request");
            thread.setDaemon(true);
            return thread;
        });
        this.connectionDriver = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "sfm-vox-terminal-connection");
            thread.setDaemon(true);
            return thread;
        });
        this.poller = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "sfm-vox-terminal-frame-poller");
            thread.setDaemon(true);
            return thread;
        });
    }

    /** Retained as a source-compatible constructor; the fallback is deliberately ignored. */
    public SFMVoxTerminalService(
            InetSocketAddress endpoint,
            SFMTerminalService ignoredFallback,
            ConnectionOptions connectionOptions,
            Duration callTimeout) {
        this(endpoint, connectionOptions, callTimeout);
    }

    @Override
    public SFMTerminalSession openSession() {
        startPolling();
        return new Session();
    }

    @Override
    public void requestConnect() {
        startPolling();
        synchronized (lock) {
            if (closed || sessionId != null || connectionInFlight
                    || failure != null && System.nanoTime() < nextConnectionAttemptNanos) return;
            connectionInFlight = true;
        }
        try {
            driver.execute(() -> {
                try {
                    ensureConnected();
                } catch (Exception ignored) {
                    // The panel exposes the failure and keeps the retry button available.
                } finally {
                    synchronized (lock) {
                        connectionInFlight = false;
                    }
                }
            });
        } catch (RejectedExecutionException ignored) {
            synchronized (lock) {
                connectionInFlight = false;
            }
        }
    }

    @Override
    public boolean isConnected() {
        synchronized (lock) {
            return sessionId != null;
        }
    }

    @Override
    public boolean isConnecting() {
        synchronized (lock) {
            return connectionInFlight;
        }
    }

    @Override
    public Optional<String> failureMessage() {
        synchronized (lock) {
            return Optional.ofNullable(failure);
        }
    }

    /** Returns the latest bounded frame received from Vox, if any. */
    public Optional<TerminalSnapshot> latestSnapshot() {
        synchronized (lock) {
            return Optional.ofNullable(latestSnapshot);
        }
    }

    /** Returns bounded machine-readable polling and frame evidence. */
    public SFMVoxTerminalTelemetry.Snapshot telemetry() {
        return telemetry.snapshot();
    }

    @Override
    public Optional<SFMTerminalFrame> latestFrame() {
        synchronized (lock) {
            if (latestSnapshot == null) return Optional.empty();
            return Optional.of(new SFMTerminalFrame(
                    latestSnapshot.sequence(),
                    latestSnapshot.kind() == TerminalFrameKind.FULL,
                    latestSnapshot.encoding() == TerminalFrameEncoding.PNG,
                    latestSnapshot.payload(),
                    frameMetadata(latestSnapshot)));
        }
    }

    private SFMTerminalFrameMetadata frameMetadata(TerminalSnapshot snapshot) {
        var timing = snapshot.timing();
        return new SFMTerminalFrameMetadata(
                snapshot.requestSequence(),
                snapshot.logicalColumns(),
                snapshot.logicalRows(),
                snapshot.panelWidth(),
                snapshot.panelHeight(),
                snapshot.cellWidth(),
                snapshot.cellHeight(),
                snapshot.fontPixelSize(),
                "rust.cpu.fontdue",
                "vox",
                timing.ptyDrainUs(),
                0L,
                timing.terminalSnapshotUs(),
                timing.fontLoadUs(),
                timing.rasterUs(),
                0L,
                timing.pngEncodeUs(),
                timing.totalUs(),
                snapshot.correlationId());
    }

    private static SFMVoxTerminalTelemetry.NativeFrameMetadata nativeFrameMetadata(
            TerminalSnapshot snapshot) {
        var timing = snapshot.timing();
        return new SFMVoxTerminalTelemetry.NativeFrameMetadata(
                snapshot.sequence(),
                snapshot.requestSequence(),
                snapshot.logicalColumns(),
                snapshot.logicalRows(),
                snapshot.panelWidth(),
                snapshot.panelHeight(),
                snapshot.cellWidth(),
                snapshot.cellHeight(),
                snapshot.fontPixelSize(),
                "rust.cpu.fontdue",
                "vox",
                snapshot.correlationId(),
                timing.totalUs(),
                snapshot.payload().length);
    }

    /** Returns a defensive copy of the latest frame payload for a renderer. */
    public Optional<byte[]> latestSnapshotPayload() {
        synchronized (lock) {
            return latestSnapshot == null
                    ? Optional.empty()
                    : Optional.of(Arrays.copyOf(latestSnapshot.payload(), latestSnapshot.payload().length));
        }
    }

    /** Returns the latest Rust-owned visible text witness, if polling has produced one. */
    public Optional<String> latestContentText() {
        synchronized (lock) {
            return latestContent == null ? Optional.empty() : Optional.of(latestContent.text());
        }
    }

    /** Reads a bounded Rust-owned visible text witness for deterministic automation assertions. */
    public String contentForAutomation() {
        try {
            ensureConnected();
            TerminalClient currentClient;
            String currentSession;
            synchronized (lock) {
                currentClient = client;
                currentSession = sessionId;
            }
            TerminalContentResult content = readReadyContent(currentClient, currentSession);
            synchronized (lock) {
                latestContent = content;
            }
            return content.text();
        } catch (Exception error) {
            synchronized (lock) {
                failure = "Vox terminal content unavailable: " + describe(error);
                closeTransportLocked();
            }
            throw new IllegalStateException(failure, error);
        }
    }

    /**
     * A newly created ConPTY can expose one or more empty frames before the
     * shell has written its prompt.  Automation reads are a synchronization
     * surface, so wait briefly for the Rust-owned prompt instead of turning
     * that normal startup window into a false reconnect failure.
     */
    private TerminalContentResult readReadyContent(TerminalClient currentClient, String currentSession)
            throws Exception {
        long deadline = System.nanoTime() + Math.min(
                callTimeout.toNanos(), CONTENT_READINESS_TIMEOUT.toNanos());
        TerminalContentResult content;
        do {
            content = requireSuccess(
                    await(currentClient.getContent(new TerminalContentRequest(
                            currentSession, 0, MAX_CONTENT_CHARS, nextSequence())),
                            "reading terminal content"),
                    "reading terminal content");
            if (!content.text().isBlank() || content.prompt().promptPresent()) {
                return content;
            }
            if (System.nanoTime() >= deadline) {
                return content;
            }
            Thread.sleep(25);
        } while (true);
    }

    public int logicalWidth() {
        synchronized (lock) {
            return requestedWidth;
        }
    }

    public int logicalHeight() {
        synchronized (lock) {
            return requestedHeight;
        }
    }

    private TerminalSurfaceMetrics requestedSurface() {
        return new TerminalSurfaceMetrics(
                requestedWidth, requestedHeight, requestedPixelWidth, requestedPixelHeight,
                0, 0, 0);
    }

    /** Clears a failed transport so the next command attempts a fresh connection. */
    public void reconnect() {
        synchronized (lock) {
            if (closed) return;
            closeTransportLocked(false);
            failure = null;
            nextConnectionAttemptNanos = 0;
            latestSnapshot = null;
            latestContent = null;
            clientSequence = 0;
        }
    }

    @Override
    public void close() {
        synchronized (lock) {
            if (closed) return;
            closed = true;
            closeTransportLocked(true);
        }
        driver.shutdownNow();
        connectionDriver.shutdownNow();
        poller.shutdownNow();
    }

    private void startPolling() {
        synchronized (lock) {
            if (closed || polling) return;
            polling = true;
            poller.scheduleWithFixedDelay(this::pollSnapshot, 0, FRAME_POLL_MILLIS, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Polls the Rust-owned frame independently of Java input events. An empty
     * snapshot means the server sequence has not changed and is deliberately
     * ignored, avoiding needless PNG uploads and repaints.
     */
    private void pollSnapshot() {
        TerminalClient currentClient;
        String currentSession;
        long afterSequence;
        synchronized (lock) {
            if (closed) return;
            if (client == null || sessionId == null) {
                telemetry.recordPollSkippedUnavailable();
                return;
            }
            if (snapshotInFlight) {
                telemetry.recordPollSkippedInFlight();
                return;
            }
            snapshotInFlight = true;
            telemetry.recordPollStarted();
            currentClient = client;
            currentSession = sessionId;
            afterSequence = latestSnapshot == null ? 0 : latestSnapshot.sequence();
        }
        long pollStarted = System.nanoTime();
        long requestSequence = nextSequence();
        String correlationId = correlationId("snapshot", requestSequence);
        long voxWaitStarted = 0L;
        boolean voxWaitStartedFlag = false;
        CompletableFuture<VoxResult<TerminalSnapshot, TerminalError>> future;
        try {
            voxWaitStarted = System.nanoTime();
            voxWaitStartedFlag = true;
            future = currentClient.snapshot(new TerminalSnapshotRequest(
                    currentSession, afterSequence, MAX_FRAME_BYTES, requestSequence, correlationId));
            TerminalSnapshot snapshot = requireSuccess(
                    await(future, "polling terminal snapshot"), "polling terminal snapshot");
            long voxWaitUs = elapsedMicros(voxWaitStarted);
            telemetry.recordVoxWait(System.nanoTime() - voxWaitStarted, false, false);
            synchronized (lock) {
                snapshotInFlight = false;
                if (closed || (snapshot.payload().length == 0 && !snapshot.complete())) {
                    telemetry.recordDropped();
                    telemetry.recordPollCompleted(System.nanoTime() - pollStarted);
                    return;
                }
                validateSnapshot(snapshot, "polled terminal snapshot");
                latestSnapshot = snapshot;
            }
            telemetry.recordObserved(nativeFrameMetadata(snapshot));
            telemetry.recordAccepted(false);
            logSnapshotTiming(snapshot, voxWaitUs, elapsedMicros(pollStarted), false);
            telemetry.recordPollCompleted(System.nanoTime() - pollStarted);
            pollContent();
        } catch (Exception error) {
            synchronized (lock) {
                snapshotInFlight = false;
                telemetry.recordPollFailed(System.nanoTime() - pollStarted);
                if (voxWaitStartedFlag) {
                    telemetry.recordVoxWait(
                            System.nanoTime() - voxWaitStarted,
                            true,
                            error instanceof TimeoutException);
                }
                // A poll may belong to a transport that reconnect() already
                // replaced. Never let that stale completion tear down the
                // replacement client.
                if (!closed && client == currentClient && Objects.equals(sessionId, currentSession)) {
                    failure = "Vox terminal snapshot unavailable: " + describe(error);
                    nextConnectionAttemptNanos = System.nanoTime() + Duration.ofSeconds(2).toNanos();
                    latestSnapshot = null;
                    latestContent = null;
                    closeTransportLocked();
                }
            }
        }
    }

    private void pollContent() {
        TerminalClient currentClient;
        String currentSession;
        long afterSequence;
        synchronized (lock) {
            if (closed || client == null || sessionId == null) return;
            currentClient = client;
            currentSession = sessionId;
            afterSequence = latestContent == null ? 0 : latestContent.sequence();
        }
        try {
            TerminalContentResult content = requireSuccess(
                    await(currentClient.getContent(new TerminalContentRequest(
                            currentSession, afterSequence, MAX_CONTENT_CHARS, nextSequence())),
                            "polling terminal content"),
                    "polling terminal content");
            synchronized (lock) {
                if (!closed && content.complete()) latestContent = content;
            }
        } catch (Exception ignored) {
            // The PNG stream remains the user-facing path; content is a test/debug witness.
        }
    }

    private void ensureConnected() throws Exception {
        synchronized (lock) {
            if (closed) throw new IllegalStateException("Vox terminal service is closed");
            if (sessionId != null) return;
            failure = null;
        }

        VoxConnection newConnection = null;
        ServiceLane newLane = null;
        try {
            newConnection = VoxConnection.connect(endpoint, freshConnectionOptions());
            // The request executor may be synchronously waiting for this
            // connection to open. Drive the transport on its own executor so
            // requestConnect() cannot deadlock before the first lane opens.
            CompletableFuture<Void> closedFuture = newConnection.start(connectionDriver);
            awaitConnectionOpen(newConnection, closedFuture);
            newLane = newConnection.openLane(TerminalServiceDescriptor.INSTANCE, LaneOptions.defaults());
            await(newLane.opened(), "opening terminal lane");
            TerminalClient newClient = new TerminalClient(newLane);
            TerminalCapabilities capabilities = new TerminalCapabilities(
                    true, true, true, false, true, false, false, false,
                    requestedWidth, requestedHeight, MAX_FRAME_BYTES,
                    "rust.cpu.fontdue", "vox");
            long requestSequence = nextSequence();
            TerminalConnectRequest request = new TerminalConnectRequest(
                    "sfm-terminal", requestedWidth, requestedHeight, requestedSurface(), capabilities,
                    requestSequence, correlationId("connect", requestSequence));
            TerminalConnectResult connected = requireSuccess(
                    await(newClient.connect(request), "connecting terminal"), "connecting terminal");
            synchronized (lock) {
                if (closed) {
                    newLane.close();
                    newConnection.close();
                    return;
                }
                connection = newConnection;
                lane = newLane;
                client = newClient;
                sessionId = connected.sessionId();
            }
        } catch (Exception error) {
            if (newLane != null) newLane.close();
            if (newConnection != null) newConnection.close();
            synchronized (lock) {
                failure = "Vox terminal unavailable: " + describe(error);
                nextConnectionAttemptNanos = System.nanoTime() + Duration.ofSeconds(2).toNanos();
                closeTransportLocked();
            }
            throw new IllegalStateException(failure, error);
        }
    }

    /** Sends a bounded logical terminal resize; the next frame remains Rust-owned. */
    @Override
    public boolean resize(int width, int height) {
        synchronized (lock) {
            return resize(width, height, requestedPixelWidth, requestedPixelHeight);
        }
    }

    @Override
    public boolean resize(int width, int height, int panelWidth, int panelHeight) {
        int boundedWidth = Math.max(1, Math.min(240, width));
        int boundedHeight = Math.max(1, Math.min(120, height));
        int boundedPanelWidth = Math.max(0, Math.min(4096, panelWidth));
        int boundedPanelHeight = Math.max(0, Math.min(4096, panelHeight));
        synchronized (lock) {
            if (closed) return false;
            requestedWidth = boundedWidth;
            requestedHeight = boundedHeight;
            requestedPixelWidth = boundedPanelWidth;
            requestedPixelHeight = boundedPanelHeight;
            if (sessionId == null) return true;
            try {
                long requestSequence = nextSequence();
                TerminalResizeResult resized = requireSuccess(
                        await(client.resize(new TerminalResizeRequest(
                                sessionId, boundedWidth, boundedHeight, requestedSurface(),
                                requestSequence, correlationId("resize", requestSequence))),
                                "resizing terminal"),
                        "resizing terminal");
                if (resized.width() != boundedWidth || resized.height() != boundedHeight) {
                    throw new IllegalStateException("resize response dimensions were "
                            + resized.width() + "x" + resized.height() + " instead of "
                            + boundedWidth + "x" + boundedHeight);
                }
                TerminalSnapshot snapshot = requireSuccess(
                        await(client.snapshot(new TerminalSnapshotRequest(
                                sessionId, 0, MAX_FRAME_BYTES,
                                nextSequence(), correlationId("resize-snapshot", clientSequence))),
                                "reading resized terminal snapshot"),
                        "reading resized terminal snapshot");
                if (snapshot.payload().length != 0) {
                    validateSnapshot(snapshot, "resized terminal snapshot");
                    latestSnapshot = snapshot;
                }
                return true;
            } catch (Exception error) {
                failure = "Vox terminal resize unavailable: " + describe(error);
                latestSnapshot = null;
                latestContent = null;
                closeTransportLocked();
                return false;
            }
        }
    }

    /** Send exact printable bytes; no implicit Enter is added. */
    public boolean sendText(String text) {
        if (text == null || text.isEmpty()) return true;
        try {
            ensureConnected();
            String currentSession;
            TerminalClient currentClient;
            synchronized (lock) {
                currentSession = sessionId;
                currentClient = client;
            }
            long requestSequence = nextSequence();
            requireSuccess(
                    await(currentClient.sendText(new TerminalTextInput(
                            currentSession, text, requestSequence,
                            correlationId("text", requestSequence))), "sending terminal text"),
                    "sending terminal text");
            return true;
        } catch (Exception error) {
            synchronized (lock) {
                failure = "Vox terminal input unavailable: " + describe(error);
                closeTransportLocked();
            }
            return false;
        }
    }

    /** Send a physical key transition to Rust; printable text arrives separately. */
    public boolean sendKey(int keyCode, int modifiers, boolean pressed, boolean repeat) {
        try {
            ensureConnected();
            String currentSession;
            TerminalClient currentClient;
            synchronized (lock) {
                currentSession = sessionId;
                currentClient = client;
            }
            long requestSequence = nextSequence();
            requireSuccess(
                    await(currentClient.sendKey(new TerminalKeyInput(
                            currentSession, keyCode, modifiers, pressed, repeat, requestSequence,
                            correlationId("key", requestSequence))),
                            "sending terminal key"),
                    "sending terminal key");
            return true;
        } catch (Exception error) {
            synchronized (lock) {
                failure = "Vox terminal key unavailable: " + describe(error);
                closeTransportLocked();
            }
            return false;
        }
    }

    /** Request a server-side interrupt while retaining the Rust terminal session. */
    public boolean cancel() {
        try {
            ensureConnected();
            String currentSession;
            TerminalClient currentClient;
            synchronized (lock) {
                currentSession = sessionId;
                currentClient = client;
            }
            long requestSequence = nextSequence();
            return requireSuccess(
                    await(currentClient.cancel(new TerminalCancelRequest(
                            currentSession,
                            0,
                            "SFM terminal cancellation",
                            requestSequence, correlationId("cancel", requestSequence))),
                            "cancelling terminal operation"),
                    "cancelling terminal operation").state() == TerminalState.READY;
        } catch (Exception error) {
            synchronized (lock) {
                failure = "Vox terminal cancellation unavailable: " + describe(error);
                closeTransportLocked();
            }
            return false;
        }
    }

    /** Send a terminal mouse transition in logical terminal-cell coordinates. */
    public boolean sendMouse(
            int x,
            int y,
            int buttons,
            int button,
            boolean pressed,
            boolean motion,
            int wheelX,
            int wheelY) {
        try {
            ensureConnected();
            String currentSession;
            TerminalClient currentClient;
            synchronized (lock) {
                currentSession = sessionId;
                currentClient = client;
            }
            long requestSequence = nextSequence();
            requireSuccess(
                    await(currentClient.sendMouse(new TerminalMouseInput(
                            currentSession,
                            Math.max(0, Math.min(239, x)),
                            Math.max(0, Math.min(119, y)),
                            Math.max(0, Math.min(255, buttons)),
                            Math.max(0, Math.min(255, button)),
                            pressed,
                            motion,
                            wheelX,
                            wheelY,
                            requestSequence,
                            correlationId("mouse", requestSequence))), "sending terminal mouse"),
                    "sending terminal mouse");
            return true;
        } catch (Exception error) {
            synchronized (lock) {
                failure = "Vox terminal mouse unavailable: " + describe(error);
                closeTransportLocked();
            }
            return false;
        }
    }

    private SFMTerminalResponse execute(String command) {
        String workingDirectory = workingDirectory();
        try {
            ensureConnected();
            long requestSequence = nextSequence();
            TerminalInputResult input = requireSuccess(
                    await(client.sendText(new TerminalTextInput(
                            sessionId, command + "\r", requestSequence,
                            correlationId("execute", requestSequence))), "sending terminal text"),
                    "sending terminal text");
            long snapshotSequence = nextSequence();
            TerminalSnapshot snapshot = requireSuccess(
                    await(client.snapshot(new TerminalSnapshotRequest(
                            sessionId, 0, MAX_FRAME_BYTES, snapshotSequence,
                            correlationId("execute-snapshot", snapshotSequence))),
                            "reading terminal snapshot"),
                    "reading terminal snapshot");
            if (snapshot.payload().length != 0) {
                validateSnapshot(snapshot, "terminal snapshot");
                synchronized (lock) {
                    latestSnapshot = snapshot;
                }
            }
            return SFMTerminalResponse.ok(List.of(
                    "Vox terminal accepted command",
                    "frame sequence: " + input.frameSequence()), workingDirectory);
        } catch (Exception error) {
            synchronized (lock) {
                latestSnapshot = null;
                latestContent = null;
            }
            return SFMTerminalResponse.error(
                    "Rust terminal unavailable: " + describe(error), workingDirectory);
        }
    }

    private String workingDirectory() {
        return "vox://" + endpoint.getHostString() + ":" + endpoint.getPort();
    }

    private synchronized long nextSequence() {
        return ++clientSequence;
    }

    private static void validateSnapshot(TerminalSnapshot snapshot, String operation) {
        if (snapshot.payload().length > MAX_FRAME_BYTES) {
            throw new IllegalStateException(operation + " exceeds the frame bound");
        }
        if (snapshot.kind() != TerminalFrameKind.FULL) {
            throw new IllegalStateException(operation + " returned a dirty tile before SFM enables tiles");
        }
        if (snapshot.encoding() != TerminalFrameEncoding.PNG || !isPng(snapshot.payload())) {
            throw new IllegalStateException(operation + " did not return a full PNG frame");
        }
    }

    private void logSnapshotTiming(
            TerminalSnapshot snapshot, long voxWaitUs, long javaPollUs, boolean stale) {
        int logicalColumns = snapshot.logicalColumns();
        int logicalRows = snapshot.logicalRows();
        int panelWidth = snapshot.panelWidth();
        int panelHeight = snapshot.panelHeight();
        int cellWidth = snapshot.cellWidth();
        int cellHeight = snapshot.cellHeight();
        String message = "SFM_VOX_TERMINAL_TIMING correlation_id={} request_sequence={} "
                + "server_sequence={} rust_total_us={} pty_drain_us={} vt_update_us={} "
                + "snapshot_us={} font_load_us={} raster_us={} frame_build_us={} encode_us={} "
                + "java_vox_wait_us={} java_poll_us={} logical_columns={} logical_rows={} "
                + "panel_width={} panel_height={} cell_width={} cell_height={} font_pixel_size={} "
                + "payload_bytes={} backend_id={} transport_id={} rust_timing_source={} stale={}";
        Object[] fields = {
                snapshot.correlationId(), snapshot.requestSequence(), snapshot.sequence(),
                snapshot.timing().totalUs(), snapshot.timing().ptyDrainUs(), 0L,
                snapshot.timing().terminalSnapshotUs(), snapshot.timing().fontLoadUs(),
                snapshot.timing().rasterUs(), 0L, snapshot.timing().pngEncodeUs(),
                voxWaitUs, javaPollUs, logicalColumns, logicalRows, panelWidth, panelHeight,
                cellWidth, cellHeight, snapshot.fontPixelSize(), snapshot.payload().length,
                "rust.cpu.fontdue", "vox", "snapshot-metadata", stale
        };
        if (javaPollUs >= 100_000) {
            SFM.LOGGER.info(message, fields);
        } else {
            SFM.LOGGER.debug(message, fields);
        }
    }

    private static long elapsedMicros(long startedNanos) {
        return Math.max(0L, (System.nanoTime() - startedNanos) / 1_000L);
    }

    private static String correlationId(String operation, long sequence) {
        return "sfm-terminal/" + operation + "/" + sequence;
    }

    private void awaitConnectionOpen(VoxConnection currentConnection, CompletableFuture<Void> closedFuture) throws Exception {
        long deadline = System.nanoTime() + callTimeout.toNanos();
        while (currentConnection.state() != ConnectionState.OPEN && System.nanoTime() < deadline) {
            if (closedFuture.isDone()) await(closedFuture, "opening Vox connection");
            Thread.sleep(5);
        }
        if (currentConnection.state() != ConnectionState.OPEN) {
            throw new IllegalStateException("connection did not open: " + currentConnection.state());
        }
    }

    private <T> T await(CompletableFuture<T> future, String operation) throws Exception {
        try {
            return future.get(callTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (ExecutionException error) {
            throw new IllegalStateException(operation + " failed", unwrap(error.getCause()));
        }
    }

    /**
     * Vox closes the scheduler owned by a connection when that connection is
     * closed. Reconnects therefore need a fresh options instance rather than
     * reusing the previous connection's scheduler.
     */
    private ConnectionOptions freshConnectionOptions() {
        return ConnectionOptions.builder()
                .maxFrameBytes(connectionOptions.maxFrameBytes())
                .maxQueuedOutboundBytes(connectionOptions.maxQueuedOutboundBytes())
                .maxQueuedOutboundMessages(connectionOptions.maxQueuedOutboundMessages())
                .maxPendingRequests(connectionOptions.maxPendingRequests())
                .maxOpenLanes(connectionOptions.maxOpenLanes())
                .maxSchemaBytes(connectionOptions.maxSchemaBytes())
                .maxSchemas(connectionOptions.maxSchemas())
                .handshakeTimeout(connectionOptions.handshakeTimeout())
                .idleTimeout(connectionOptions.idleTimeout())
                .closeTimeout(connectionOptions.closeTimeout())
                .handlerExecutor(connectionOptions.handlerExecutor())
                .build();
    }

    private static <T> T requireSuccess(VoxResult<T, TerminalError> result, String operation) {
        if (result.isSuccess()) return result.success();
        if (result.isApplicationError()) {
            throw new IllegalStateException(operation + ": " + result.applicationError().message());
        }
        throw new IllegalStateException(operation + ": " + result.detail());
    }

    private void closeTransportLocked() {
        closeTransportLocked(false);
    }

    private void closeTransportLocked(boolean requestDisconnect) {
        if (requestDisconnect && client != null && sessionId != null) {
            try {
                client.disconnect(new TerminalDisconnectRequest(
                        sessionId, "SFM terminal closed", nextSequence()));
            } catch (Exception ignored) {
                // Transport shutdown remains best effort after a failed optional endpoint.
            }
        }
        if (lane != null) lane.close();
        if (connection != null) connection.close();
        client = null;
        lane = null;
        connection = null;
        sessionId = null;
        latestContent = null;
    }

    private static Duration requirePositive(Duration value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isZero() || value.isNegative()) throw new IllegalArgumentException(name + " must be positive");
        return value;
    }

    private static Throwable unwrap(Throwable error) {
        if (error instanceof CompletionException completion && completion.getCause() != null) {
            return unwrap(completion.getCause());
        }
        return error;
    }

    private static String describe(Throwable error) {
        Throwable cause = unwrap(error);
        String message = cause.getMessage();
        return message == null || message.isBlank() ? cause.getClass().getSimpleName() : message;
    }

    static boolean isPng(byte[] payload) {
        return payload != null
                && payload.length >= 8
                && payload[0] == (byte) 0x89
                && payload[1] == 0x50
                && payload[2] == 0x4E
                && payload[3] == 0x47
                && payload[4] == 0x0D
                && payload[5] == 0x0A
                && payload[6] == 0x1A
                && payload[7] == 0x0A;
    }

    private final class Session implements SFMTerminalSession {
        @Override
        public SFMTerminalResponse execute(String command) {
            if (command == null || command.isBlank()) {
                return SFMTerminalResponse.ok(List.of(), workingDirectory());
            }
            return SFMVoxTerminalService.this.execute(command);
        }

        @Override
        public String workingDirectory() {
            return SFMVoxTerminalService.this.workingDirectory();
        }
    }
}
