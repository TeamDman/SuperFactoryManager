package ca.teamdman.sfm.client.terminal;

import ca.teamdman.sfm.client.raster.TouchDisplayRasterFrame;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Owns one local PTY and a fixed foreground structured worker. All blocking
 * service/content calls run on a bounded daemon executor, never the client.
 * This does not start/stop a shared terminal server, reconnect, or open a panel.
 */
public final class TouchDisplayTerminalServiceTransport implements TouchDisplayTerminalBroker.Transport {
    public enum Mode { INTERACTIVE, STRUCTURED_WORKER }
    public static final int DEFAULT_WIDTH = 512;
    public static final int DEFAULT_HEIGHT = 384;
    private static final long POLL_NANOS = Duration.ofMillis(250).toNanos();
    private static final long START_NANOS = Duration.ofSeconds(30).toNanos();
    private static final long ACK_NANOS = Duration.ofSeconds(20).toNanos();

    private final Supplier<SFMTerminalRemoteService> factory;
    private final String bootstrap;
    private final Mode mode;
    private final int width;
    private final int height;
    private final ExecutorService io;
    private final LongSupplier clock;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicBoolean polling = new AtomicBoolean();
    private final AtomicReference<OwnedService> service = new AtomicReference<>();
    private final AtomicReference<TouchDisplayTerminalWorkerProtocol.Observation> observation = new AtomicReference<>();
    private final AtomicReference<Pending> pending = new AtomicReference<>();
    private volatile boolean bootstrapped;
    private volatile @Nullable String failure;
    private boolean started;
    private boolean ready;
    private long startedAt;
    private long nextPoll;
    private long epoch = Long.MIN_VALUE;
    private long lastSequence = -1;
    private long acknowledged;
    private @Nullable String streamIdentity;
    private int frameWidth;
    private int frameHeight;

    private record Pending(long id, double u, double v, long sentAt) {}

    private static final class OwnedService {
        private final SFMTerminalRemoteService remote;
        private final AtomicBoolean closed = new AtomicBoolean();
        private OwnedService(SFMTerminalRemoteService remote) { this.remote = remote; }
        private void close() { if (closed.compareAndSet(false, true)) remote.close(); }
    }

    /** Only locally selected, already resolved loopback endpoints and real absolute executables. */
    public static TouchDisplayTerminalServiceTransport local(InetSocketAddress endpoint, Path workerExecutable) throws IOException {
        return local(endpoint, Mode.STRUCTURED_WORKER, workerExecutable);
    }

    /** Interactive sessions keep the terminal server's normal configured shell. */
    public static TouchDisplayTerminalServiceTransport local(InetSocketAddress endpoint, Mode mode, @Nullable Path workerExecutable) throws IOException {
        if (endpoint == null || endpoint.isUnresolved() || !endpoint.getAddress().isLoopbackAddress()
                || endpoint.getPort() < 1) throw new IllegalArgumentException("Terminal mounting requires a resolved loopback endpoint");
        String command = mode == Mode.STRUCTURED_WORKER ? bootstrapCommand(workerExecutable) : "";
        return new TouchDisplayTerminalServiceTransport(() -> {
            SFMTerminalService created = SFMTerminalServiceFactory.createRust(endpoint);
            if (!(created instanceof SFMTerminalRemoteService remote)) {
                throw new IllegalStateException("Local terminal service has no raster transport");
            }
            return remote;
        }, command, mode, DEFAULT_WIDTH, DEFAULT_HEIGHT, boundedExecutor(), System::nanoTime);
    }

    /** The path is configuration, never packet data; arguments are fixed by this implementation. */
    public static String bootstrapCommand(Path executable) throws IOException {
        if (executable == null || !executable.isAbsolute()) throw new IllegalArgumentException("Worker executable must be absolute");
        Path real = executable.toRealPath();
        if (!Files.isRegularFile(real)) throw new IllegalArgumentException("Worker executable must be a regular file");
        String path = real.toString();
        if (path.length() > 2048 || path.chars().anyMatch(c -> c < 32 || c == 127)) {
            throw new IllegalArgumentException("Worker executable contains unsupported path characters");
        }
        return "& '" + path.replace("'", "''") + "' terminal worker";
    }

    TouchDisplayTerminalServiceTransport(Supplier<SFMTerminalRemoteService> factory, String bootstrap,
                                        int width, int height, ExecutorService io, LongSupplier clock) {
        this(factory, bootstrap, Mode.STRUCTURED_WORKER, width, height, io, clock);
    }

    TouchDisplayTerminalServiceTransport(Supplier<SFMTerminalRemoteService> factory, String bootstrap, Mode mode,
                                        int width, int height, ExecutorService io, LongSupplier clock) {
        if (width < 1 || height < 1 || width > 512 || height > 512) throw new IllegalArgumentException("Terminal dimensions exceed raster bounds");
        this.factory = Objects.requireNonNull(factory);
        this.bootstrap = Objects.requireNonNull(bootstrap);
        this.mode = Objects.requireNonNull(mode);
        this.width = width;
        this.height = height;
        this.io = Objects.requireNonNull(io);
        this.clock = Objects.requireNonNull(clock);
    }

    static ExecutorService boundedExecutor() {
        return new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(1), task -> {
            Thread thread = new Thread(task, "sfm-touch-terminal-worker");
            thread.setDaemon(true);
            return thread;
        }, new ThreadPoolExecutor.AbortPolicy());
    }

    @Override public void start() {
        if (started || closed.get()) throw new IllegalStateException("Terminal transport cannot restart");
        started = true;
        startedAt = clock.getAsLong();
        submit(() -> {
            OwnedService owned = new OwnedService(Objects.requireNonNull(factory.get()));
            service.set(owned);
            if (closed.get()) {
                owned.close();
                return;
            }
            SFMTerminalRemoteService remote = owned.remote;
            if (!remote.requestTransport("full-raw-rgba").accepted()) throw new IllegalStateException("Raw RGBA terminal transport unavailable");
            var tuning = new SFMTerminalTuningSettings(width, height, 8, 80, 32);
            if (!remote.resize(tuning, tuning.resolve(width, height, 80, 32))) throw new IllegalStateException("Terminal dimensions rejected");
            if (closed.get()) return;
            if (mode == Mode.STRUCTURED_WORKER) {
                if (!remote.openSession().execute(bootstrap).success()) throw new IllegalStateException("Worker bootstrap was rejected");
            } else remote.requestConnect();
            if (!closed.get()) bootstrapped = true;
        });
    }

    @Override public void pump() {
        if (!active()) return;
        long now = clock.getAsLong();
        if (!ready && now - startedAt >= START_NANOS) { fail("worker_start_timeout"); return; }
        OwnedService owned = service.get();
        if (owned == null) return;
        var snapshot = owned.remote.connectionSnapshot();
        if (snapshot.failure().isPresent()) { fail("terminal_connection_failed"); return; }
        if (snapshot.connected()) {
            if (epoch == Long.MIN_VALUE) epoch = snapshot.interactionEpoch();
            else if (epoch != snapshot.interactionEpoch()) { fail("terminal_session_changed"); return; }
        } else if (epoch != Long.MIN_VALUE) { fail("terminal_disconnected"); return; }
        if (mode == Mode.INTERACTIVE) {
            ready = bootstrapped && snapshot.connected();
            return;
        }
        var observed = observation.getAndSet(null);
        if (observed != null) {
            if (observed.ended() || observed.error() != null || observed.rejectedRecords() > 0) {
                fail("worker_protocol_failed"); return;
            }
            ready |= observed.ready();
            var ack = observed.ack();
            if (ack != null && ack.count() > acknowledged) {
                Pending expected = pending.get();
                if (expected == null || ack.id() != expected.id() || ack.count() != acknowledged + 1
                        || Double.compare(ack.u(), expected.u()) != 0 || Double.compare(ack.v(), expected.v()) != 0) {
                    fail("worker_ack_mismatch"); return;
                }
                acknowledged = ack.count();
                pending.compareAndSet(expected, null);
            }
        }
        Pending expected = pending.get();
        if (expected != null && now - expected.sentAt() >= ACK_NANOS) { fail("worker_ack_timeout"); return; }
        if (bootstrapped && snapshot.connected() && now >= nextPoll && polling.compareAndSet(false, true)) {
            nextPoll = now + POLL_NANOS;
            submit(() -> {
                try {
                    var parsed = TouchDisplayTerminalWorkerProtocol.inspect(owned.remote.contentForAutomation());
                    if (!closed.get() && service.get() == owned) observation.set(parsed);
                } finally {
                    polling.set(false);
                }
            });
        }
    }

    @Override public boolean active() { return started && !closed.get(); }
    @Override public boolean ready() { return active() && ready; }
    public Optional<String> failure() { return Optional.ofNullable(failure); }
    public long acknowledgedTouches() { return acknowledged; }
    public Mode mode() { return mode; }

    @Override public Optional<TouchDisplayRasterFrame> latestFrame() {
        OwnedService owned = service.get();
        if (!ready() || owned == null) return Optional.empty();
        var snapshot = owned.remote.connectionSnapshot();
        if (!snapshot.connected() || snapshot.interactionEpoch() != epoch) { fail("terminal_session_changed"); return Optional.empty(); }
        Optional<SFMTerminalFrame> latest = owned.remote.latestFrame();
        if (latest.isEmpty()) return Optional.empty();
        SFMTerminalFrame frame = latest.get();
        if (!frame.full() || frame.png() || frame.metadata().panelWidth() > width || frame.metadata().panelHeight() > height) {
            fail("terminal_frame_not_bounded_raw_rgba"); return Optional.empty();
        }
        if (streamIdentity == null) streamIdentity = frame.streamIdentity();
        if (!streamIdentity.equals(frame.streamIdentity())) { fail("terminal_stream_changed"); return Optional.empty(); }
        if (frameWidth != 0 && (frameWidth != frame.metadata().panelWidth() || frameHeight != frame.metadata().panelHeight())) {
            fail("terminal_dimensions_changed"); return Optional.empty();
        }
        if (frame.sequence() <= lastSequence) return Optional.empty();
        try {
            var result = TouchDisplayRasterFrame.full(frame.sequence(), frame.metadata().panelWidth(), frame.metadata().panelHeight(), frame.payload());
            frameWidth = result.width();
            frameHeight = result.height();
            lastSequence = frame.sequence();
            return Optional.of(result);
        } catch (IllegalArgumentException invalid) {
            fail("terminal_frame_invalid"); return Optional.empty();
        }
    }

    @Override public boolean touch(long id, double u, double v) {
        if (!ready()) return false;
        String line = TouchDisplayTerminalWorkerProtocol.touch(id, u, v);
        if (mode == Mode.INTERACTIVE) {
            OwnedService owned = service.get();
            if (owned == null) return false;
            int columns = owned.remote.logicalWidth(), rows = owned.remote.logicalHeight();
            if (columns < 1 || rows < 1 || columns > 512 || rows > 512) { fail("terminal_cell_bounds_invalid"); return false; }
            int x = Math.min(columns - 1, (int) (u * columns));
            int y = Math.min(rows - 1, (int) (v * rows));
            // A press packet represents a click, not a persistent held button.
            if (!owned.remote.sendMouse(x, y, 1, 0, true, false, 0, 0)
                    || !owned.remote.sendMouse(x, y, 0, 0, false, false, 0, 0)) {
                fail("terminal_input_rejected"); return false;
            }
            return true;
        }
        Pending request = new Pending(id, u == 0 ? 0.0 : u, v == 0 ? 0.0 : v, clock.getAsLong());
        if (!pending.compareAndSet(null, request)) return false;
        OwnedService owned = service.get();
        if (owned == null || !owned.remote.sendText(line + "\r")) {
            pending.compareAndSet(request, null);
            fail("terminal_input_rejected");
            return false;
        }
        return true;
    }

    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        observation.set(null);
        pending.set(null);
        try {
            OwnedService owned = service.get();
            if (owned != null) owned.close();
        } finally {
            io.shutdownNow();
        }
    }

    private void fail(String reason) {
        failure = reason;
        close();
    }

    private void submit(Runnable task) {
        if (closed.get()) return;
        try {
            io.execute(() -> {
                if (closed.get()) return;
                try { task.run(); }
                catch (RuntimeException failure) { fail("terminal_worker_io_failed"); }
            });
        } catch (RejectedExecutionException rejected) {
            fail("terminal_worker_queue_full");
        }
    }
}
