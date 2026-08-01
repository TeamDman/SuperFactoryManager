package ca.teamdman.sfm.client.terminal;

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
import org.facet.vox.generated.TerminalState;
import org.facet.vox.generated.TerminalTextInput;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Optional Java client for the generated Vox terminal service.
 *
 * <p>Rust owns the PTY, VT state, and rasterization. Java only sends terminal
 * input and retains the latest bounded full-frame PNG for presentation. The
 * Java-local service is still available as an explicit degradation path when
 * the endpoint cannot be reached.
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
    private final SFMTerminalService fallbackService;
    private final ConnectionOptions connectionOptions;
    private final Duration callTimeout;
    private final ExecutorService driver;
    private final ScheduledExecutorService poller;
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
    private boolean polling;
    private boolean snapshotInFlight;
    private boolean closed;

    public SFMVoxTerminalService(InetSocketAddress endpoint) {
        this(endpoint, new SFMJavaLocalTerminalService());
    }

    public SFMVoxTerminalService(InetSocketAddress endpoint, SFMTerminalService fallbackService) {
        this(endpoint, fallbackService, ConnectionOptions.builder()
                .handshakeTimeout(Duration.ofMillis(500))
                .idleTimeout(DEFAULT_TIMEOUT)
                .closeTimeout(Duration.ofSeconds(1))
                .build(), DEFAULT_TIMEOUT);
    }

    public SFMVoxTerminalService(
            InetSocketAddress endpoint,
            SFMTerminalService fallbackService,
            ConnectionOptions connectionOptions,
            Duration callTimeout) {
        this.endpoint = Objects.requireNonNull(endpoint, "endpoint");
        this.fallbackService = Objects.requireNonNull(fallbackService, "fallbackService");
        this.connectionOptions = Objects.requireNonNull(connectionOptions, "connectionOptions");
        this.callTimeout = requirePositive(callTimeout, "callTimeout");
        this.driver = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "sfm-vox-terminal-driver");
            thread.setDaemon(true);
            return thread;
        });
        this.poller = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "sfm-vox-terminal-frame-poller");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Override
    public SFMTerminalSession openSession() {
        startPolling();
        return new Session(fallbackService.openSession());
    }

    /** Returns the latest bounded frame received from Vox, if any. */
    public Optional<TerminalSnapshot> latestSnapshot() {
        synchronized (lock) {
            return Optional.ofNullable(latestSnapshot);
        }
    }

    @Override
    public Optional<SFMTerminalFrame> latestFrame() {
        synchronized (lock) {
            if (latestSnapshot == null) return Optional.empty();
            return Optional.of(new SFMTerminalFrame(
                    latestSnapshot.sequence(),
                    latestSnapshot.kind() == TerminalFrameKind.FULL,
                    latestSnapshot.encoding() == TerminalFrameEncoding.PNG,
                    latestSnapshot.payload()));
        }
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

    /** Clears a failed transport so the next command attempts a fresh connection. */
    public void reconnect() {
        synchronized (lock) {
            if (closed) return;
            closeTransportLocked(false);
            failure = null;
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
            if (closed || client == null || sessionId == null || snapshotInFlight) return;
            snapshotInFlight = true;
            currentClient = client;
            currentSession = sessionId;
            afterSequence = latestSnapshot == null ? 0 : latestSnapshot.sequence();
        }
        CompletableFuture<VoxResult<TerminalSnapshot, TerminalError>> future;
        try {
            future = currentClient.snapshot(new TerminalSnapshotRequest(
                    currentSession, afterSequence, MAX_FRAME_BYTES, nextSequence()));
            TerminalSnapshot snapshot = requireSuccess(
                    await(future, "polling terminal snapshot"), "polling terminal snapshot");
            synchronized (lock) {
                snapshotInFlight = false;
                if (closed || (snapshot.payload().length == 0 && !snapshot.complete())) return;
                validateSnapshot(snapshot, "polled terminal snapshot");
                latestSnapshot = snapshot;
            }
            pollContent();
        } catch (Exception error) {
            synchronized (lock) {
                snapshotInFlight = false;
                // A poll may belong to a transport that reconnect() already
                // replaced. Never let that stale completion tear down the
                // replacement client.
                if (!closed && client == currentClient && Objects.equals(sessionId, currentSession)) {
                    failure = "Vox terminal snapshot unavailable: " + describe(error);
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
            // A failed optional endpoint is retryable. The server may be started
            // after Minecraft, so a prior fallback must not poison this service.
            failure = null;

            try {
                connection = VoxConnection.connect(endpoint, freshConnectionOptions());
                CompletableFuture<Void> closedFuture = connection.start(driver);
                awaitConnectionOpen(closedFuture);
                lane = connection.openLane(TerminalServiceDescriptor.INSTANCE, LaneOptions.defaults());
                await(lane.opened(), "opening terminal lane");
                client = new TerminalClient(lane);
                TerminalCapabilities capabilities = new TerminalCapabilities(
                        true, true, true, false, true, false, false, false,
                        requestedWidth, requestedHeight, MAX_FRAME_BYTES);
                TerminalConnectRequest request = new TerminalConnectRequest(
                        "sfm-terminal", requestedWidth, requestedHeight, capabilities, nextSequence());
                TerminalConnectResult connected = requireSuccess(
                        await(client.connect(request), "connecting terminal"), "connecting terminal");
                sessionId = connected.sessionId();
            } catch (Exception error) {
                failure = "Vox terminal unavailable: " + describe(error);
                closeTransportLocked();
                throw new IllegalStateException(failure, error);
            }
        }
    }

    /** Sends a bounded logical terminal resize; the next frame remains Rust-owned. */
    public boolean resize(int width, int height) {
        int boundedWidth = Math.max(1, Math.min(240, width));
        int boundedHeight = Math.max(1, Math.min(120, height));
        synchronized (lock) {
            if (closed) return false;
            requestedWidth = boundedWidth;
            requestedHeight = boundedHeight;
            if (sessionId == null) return true;
            try {
                TerminalResizeResult resized = requireSuccess(
                        await(client.resize(new TerminalResizeRequest(
                                sessionId, boundedWidth, boundedHeight, nextSequence())), "resizing terminal"),
                        "resizing terminal");
                if (resized.width() != boundedWidth || resized.height() != boundedHeight) {
                    throw new IllegalStateException("resize response dimensions were "
                            + resized.width() + "x" + resized.height() + " instead of "
                            + boundedWidth + "x" + boundedHeight);
                }
                TerminalSnapshot snapshot = requireSuccess(
                        await(client.snapshot(new TerminalSnapshotRequest(
                                sessionId, 0, MAX_FRAME_BYTES, nextSequence())), "reading resized terminal snapshot"),
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
            requireSuccess(
                    await(currentClient.sendText(new TerminalTextInput(
                            currentSession, text, nextSequence())), "sending terminal text"),
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
            requireSuccess(
                    await(currentClient.sendKey(new TerminalKeyInput(
                            currentSession, keyCode, modifiers, pressed, repeat, nextSequence())),
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
            return requireSuccess(
                    await(currentClient.cancel(new TerminalCancelRequest(
                            currentSession,
                            0,
                            "SFM terminal cancellation",
                            nextSequence())), "cancelling terminal operation"),
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
                            nextSequence())), "sending terminal mouse"),
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

    private SFMTerminalResponse execute(String command, SFMTerminalSession fallbackSession) {
        String workingDirectory = workingDirectory();
        try {
            ensureConnected();
            TerminalInputResult input = requireSuccess(
                    await(client.sendText(new TerminalTextInput(
                            sessionId, command + "\r", nextSequence())), "sending terminal text"),
                    "sending terminal text");
            TerminalSnapshot snapshot = requireSuccess(
                    await(client.snapshot(new TerminalSnapshotRequest(
                            sessionId, 0, MAX_FRAME_BYTES, nextSequence())), "reading terminal snapshot"),
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
            SFMTerminalResponse local = fallbackSession.execute(command);
            List<String> lines = new ArrayList<>();
            lines.add("Vox unavailable; Java-local fallback active");
            lines.add(describe(error));
            lines.addAll(local.lines());
            return new SFMTerminalResponse(local.success(), lines, local.workingDirectory());
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

    private void awaitConnectionOpen(CompletableFuture<Void> closedFuture) throws Exception {
        long deadline = System.nanoTime() + callTimeout.toNanos();
        while (connection.state() != ConnectionState.OPEN && System.nanoTime() < deadline) {
            if (closedFuture.isDone()) await(closedFuture, "opening Vox connection");
            Thread.sleep(5);
        }
        if (connection.state() != ConnectionState.OPEN) {
            throw new IllegalStateException("connection did not open: " + connection.state());
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
        private final SFMTerminalSession fallbackSession;

        private Session(SFMTerminalSession fallbackSession) {
            this.fallbackSession = Objects.requireNonNull(fallbackSession, "fallbackSession");
        }

        @Override
        public SFMTerminalResponse execute(String command) {
            if (command == null || command.isBlank()) {
                return SFMTerminalResponse.ok(List.of(), workingDirectory());
            }
            return SFMVoxTerminalService.this.execute(command, fallbackSession);
        }

        @Override
        public String workingDirectory() {
            return fallbackSession.workingDirectory();
        }
    }
}
