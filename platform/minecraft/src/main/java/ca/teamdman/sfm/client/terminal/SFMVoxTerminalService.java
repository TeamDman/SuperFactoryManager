package ca.teamdman.sfm.client.terminal;

import org.facet.vox.ConnectionOptions;
import org.facet.vox.ConnectionState;
import org.facet.vox.LaneOptions;
import org.facet.vox.ServiceLane;
import org.facet.vox.VoxConnection;
import org.facet.vox.VoxResult;
import org.facet.vox.generated.TerminalCapabilities;
import org.facet.vox.generated.TerminalClient;
import org.facet.vox.generated.TerminalConnectRequest;
import org.facet.vox.generated.TerminalConnectResult;
import org.facet.vox.generated.TerminalDisconnectRequest;
import org.facet.vox.generated.TerminalError;
import org.facet.vox.generated.TerminalInputResult;
import org.facet.vox.generated.TerminalServiceDescriptor;
import org.facet.vox.generated.TerminalSnapshot;
import org.facet.vox.generated.TerminalSnapshotRequest;
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
import java.util.concurrent.TimeUnit;

/**
 * Optional Java client for the generated Vox terminal service.
 *
 * <p>The Java-local service remains the default. This class is deliberately
 * usable as a probe/adapter without changing the panel: Vox snapshots are
 * retained as bounded typed frames, while decoding structured-cell payloads
 * and rendering them remains a separate presentation slice.
 */
public final class SFMVoxTerminalService implements SFMTerminalService, AutoCloseable {
    private static final int REQUEST_WIDTH = 120;
    private static final int REQUEST_HEIGHT = 40;
    private static final int MAX_FRAME_BYTES = 4 * 1024 * 1024;
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(3);

    private final InetSocketAddress endpoint;
    private final ConnectionOptions connectionOptions;
    private final Duration callTimeout;
    private final ExecutorService driver;
    private final Object lock = new Object();
    private VoxConnection connection;
    private ServiceLane lane;
    private TerminalClient client;
    private String sessionId;
    private String failure;
    private TerminalSnapshot latestSnapshot;
    private long clientSequence;
    private boolean closed;

    public SFMVoxTerminalService(InetSocketAddress endpoint) {
        this(endpoint, ConnectionOptions.builder()
                .handshakeTimeout(Duration.ofMillis(500))
                .idleTimeout(DEFAULT_TIMEOUT)
                .closeTimeout(Duration.ofSeconds(1))
                .build(), DEFAULT_TIMEOUT);
    }

    public SFMVoxTerminalService(
            InetSocketAddress endpoint, ConnectionOptions connectionOptions, Duration callTimeout) {
        this.endpoint = Objects.requireNonNull(endpoint, "endpoint");
        this.connectionOptions = Objects.requireNonNull(connectionOptions, "connectionOptions");
        this.callTimeout = requirePositive(callTimeout, "callTimeout");
        this.driver = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "sfm-vox-terminal-driver");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Override
    public SFMTerminalSession openSession() {
        return new Session();
    }

    /** Returns the latest bounded frame received from Vox, if any. */
    public Optional<TerminalSnapshot> latestSnapshot() {
        synchronized (lock) {
            return Optional.ofNullable(latestSnapshot);
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

    /** Clears a failed transport so the next command attempts a fresh connection. */
    public void reconnect() {
        synchronized (lock) {
            if (closed) return;
            closeTransportLocked();
            failure = null;
            latestSnapshot = null;
            clientSequence = 0;
        }
    }

    @Override
    public void close() {
        synchronized (lock) {
            if (closed) return;
            closed = true;
            closeTransportLocked();
        }
        driver.shutdownNow();
    }

    private void ensureConnected() throws Exception {
        synchronized (lock) {
            if (closed) throw new IllegalStateException("Vox terminal service is closed");
            if (sessionId != null) return;
            if (failure != null) throw new IllegalStateException(failure);

            try {
                connection = VoxConnection.connect(endpoint, connectionOptions);
                CompletableFuture<Void> closedFuture = connection.start(driver);
                awaitConnectionOpen(closedFuture);
                lane = connection.openLane(TerminalServiceDescriptor.INSTANCE, LaneOptions.defaults());
                await(lane.opened(), "opening terminal lane");
                client = new TerminalClient(lane);
                TerminalCapabilities capabilities = new TerminalCapabilities(
                        true, true, false, true, true, false, true, true,
                        REQUEST_WIDTH, REQUEST_HEIGHT, MAX_FRAME_BYTES);
                TerminalConnectRequest request = new TerminalConnectRequest(
                        "sfm-terminal", REQUEST_WIDTH, REQUEST_HEIGHT, capabilities, nextSequence());
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

    private SFMTerminalResponse execute(String command) {
        String workingDirectory = workingDirectory();
        try {
            ensureConnected();
            TerminalInputResult input = requireSuccess(
                    await(client.sendText(new TerminalTextInput(
                            sessionId, command + "\n", nextSequence())), "sending terminal text"),
                    "sending terminal text");
            TerminalSnapshot snapshot = requireSuccess(
                    await(client.snapshot(new TerminalSnapshotRequest(
                            sessionId, 0, MAX_FRAME_BYTES, nextSequence())), "reading terminal snapshot"),
                    "reading terminal snapshot");
            if (snapshot.payload().length > MAX_FRAME_BYTES) {
                throw new IllegalStateException("Vox terminal snapshot exceeds the frame bound");
            }
            synchronized (lock) {
                latestSnapshot = snapshot;
            }
            return SFMTerminalResponse.ok(List.of(
                    "Vox terminal accepted command",
                    "frame sequence: " + input.frameSequence()), workingDirectory);
        } catch (Exception error) {
            return SFMTerminalResponse.error(describe(error), workingDirectory);
        }
    }

    private String workingDirectory() {
        return "vox://" + endpoint.getHostString() + ":" + endpoint.getPort();
    }

    private long nextSequence() {
        return ++clientSequence;
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

    private static <T> T requireSuccess(VoxResult<T, TerminalError> result, String operation) {
        if (result.isSuccess()) return result.success();
        if (result.isApplicationError()) {
            throw new IllegalStateException(operation + ": " + result.applicationError().message());
        }
        throw new IllegalStateException(operation + ": " + result.detail());
    }

    private void closeTransportLocked() {
        if (client != null && sessionId != null) {
            try {
                await(client.disconnect(new TerminalDisconnectRequest(
                        sessionId, "SFM terminal closed", nextSequence())), "disconnecting terminal");
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
