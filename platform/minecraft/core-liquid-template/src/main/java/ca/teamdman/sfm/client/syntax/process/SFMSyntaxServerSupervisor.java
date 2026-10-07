package ca.teamdman.sfm.client.syntax.process;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.syntax.SFMSyntaxHighlightLimits;
import ca.teamdman.sfm.client.syntax.SFMSyntaxHighlightRequest;
import ca.teamdman.sfm.client.syntax.SFMSyntaxHighlightResult;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Owns one lazy, restartable {@code syntax serve} process. Process launch,
 * framed I/O, protocol validation, and future completion all happen on
 * dedicated daemon threads rather than Minecraft's render thread.
 */
public final class SFMSyntaxServerSupervisor implements AutoCloseable {
    public static final String EXECUTABLE_PROPERTY = "sfm.syntax.workerExecutable";
    public static final String DEFAULT_EXECUTABLE = "sfm-propagate-changes.exe";
    public static final String CLIENT_NAME = "sfm-minecraft";
    public static final String CLIENT_VERSION = "1";
    private static final int MAXIMUM_PENDING_READINESS = 16;
    private static final Duration DEFAULT_RESTART_BACKOFF_INITIAL = Duration.ofMillis(250);
    private static final Duration DEFAULT_RESTART_BACKOFF_MAXIMUM = Duration.ofSeconds(8);

    public enum Lifecycle {
        STOPPED,
        STARTING,
        READY,
        STOPPING,
        CLOSED
    }

    public record Configuration(
            String executable,
            Duration handshakeTimeout,
            Duration requestTimeout,
            Duration shutdownTimeout,
            int maximumFrameBytes,
            int maximumPendingRequests,
            Duration restartBackoffInitial,
            Duration restartBackoffMaximum
    ) {
        public Configuration {
            executable = requireNonBlank(executable, "executable");
            handshakeTimeout = positive(handshakeTimeout, "handshakeTimeout");
            requestTimeout = positive(requestTimeout, "requestTimeout");
            shutdownTimeout = positive(shutdownTimeout, "shutdownTimeout");
            if (maximumFrameBytes <= 0) {
                throw new IllegalArgumentException("maximumFrameBytes must be positive");
            }
            if (maximumPendingRequests <= 0) {
                throw new IllegalArgumentException("maximumPendingRequests must be positive");
            }
            restartBackoffInitial = positive(restartBackoffInitial, "restartBackoffInitial");
            restartBackoffMaximum = positive(restartBackoffMaximum, "restartBackoffMaximum");
            if (restartBackoffMaximum.compareTo(restartBackoffInitial) < 0) {
                throw new IllegalArgumentException(
                        "restartBackoffMaximum must not be less than restartBackoffInitial"
                );
            }
        }

        public Configuration(
                String executable,
                Duration handshakeTimeout,
                Duration requestTimeout,
                Duration shutdownTimeout,
                int maximumFrameBytes,
                int maximumPendingRequests
        ) {
            this(
                    executable,
                    handshakeTimeout,
                    requestTimeout,
                    shutdownTimeout,
                    maximumFrameBytes,
                    maximumPendingRequests,
                    DEFAULT_RESTART_BACKOFF_INITIAL,
                    DEFAULT_RESTART_BACKOFF_MAXIMUM
            );
        }

        public static Configuration defaults() {
            String configured = System.getProperty(EXECUTABLE_PROPERTY, "").trim();
            return new Configuration(
                    configured.isEmpty() ? DEFAULT_EXECUTABLE : configured,
                    Duration.ofSeconds(10),
                    Duration.ofSeconds(3),
                    Duration.ofSeconds(2),
                    SFMSyntaxServerFrameCodec.DEFAULT_MAXIMUM_FRAME_BYTES,
                    8
            );
        }

        public List<String> command() {
            return List.of(executable, "syntax", "serve");
        }
    }

    /** Injectable process/session seam for deterministic lifecycle tests. */
    @FunctionalInterface
    public interface SessionFactory {
        WorkerSession start(Configuration configuration) throws IOException;
    }

    /** One owned process whose stdout is protocol-only and whose stderr is inherited. */
    public interface WorkerSession {
        long pid();

        InputStream stdout();

        OutputStream stdin();

        boolean alive();

        /** Reap the complete process tree without involving the render thread. */
        void terminateTree(Duration grace) throws InterruptedException;
    }

    public record Submission(
            SFMSyntaxHighlightRequest request,
            CompletableFuture<SFMSyntaxHighlightResult> result,
            Runnable cancellation
    ) {
        public Submission {
            Objects.requireNonNull(request, "request");
            Objects.requireNonNull(result, "result");
            Objects.requireNonNull(cancellation, "cancellation");
        }
    }

    /** Content-free bounded evidence; no source text, origin, language, or hash is retained. */
    public record Telemetry(
            Lifecycle lifecycle,
            long launchAttempts,
            long sessionsReady,
            long launchFailures,
            long protocolFailures,
            long transportFailures,
            long submitted,
            long completed,
            long cancelled,
            long timedOut,
            long remoteFailures,
            long lateResponses,
            long restarts,
            int pendingRequests,
            int pendingReadiness,
            boolean processAlive
    ) {
    }

    public static class WorkerUnavailableException extends IOException {
        public WorkerUnavailableException(String message) {
            super(message);
        }

        public WorkerUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static final class ProtocolMismatchException extends WorkerUnavailableException {
        public ProtocolMismatchException(String message) {
            super(message);
        }

        public ProtocolMismatchException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static final class CapacityException extends WorkerUnavailableException {
        public CapacityException(String message) {
            super(message);
        }
    }

    public static final class RemoteSyntaxException extends WorkerUnavailableException {
        private final String code;

        public RemoteSyntaxException(String code) {
            super("Syntax worker rejected the request [" + boundedCode(code) + "]");
            this.code = boundedCode(code);
        }

        public String code() {
            return code;
        }
    }

    private final Configuration configuration;
    private final SessionFactory sessionFactory;
    private final ExecutorService stateExecutor;
    private final ExecutorService readerExecutor;
    private final ScheduledExecutorService scheduler;
    private final boolean ownsExecutors;
    private final AtomicBoolean closing = new AtomicBoolean();
    private final AtomicLong launchAttempts = new AtomicLong();
    private final AtomicLong sessionsReady = new AtomicLong();
    private final AtomicLong launchFailures = new AtomicLong();
    private final AtomicLong protocolFailures = new AtomicLong();
    private final AtomicLong transportFailures = new AtomicLong();
    private final AtomicLong submitted = new AtomicLong();
    private final AtomicLong completed = new AtomicLong();
    private final AtomicLong cancelled = new AtomicLong();
    private final AtomicLong timedOut = new AtomicLong();
    private final AtomicLong remoteFailures = new AtomicLong();
    private final AtomicLong lateResponses = new AtomicLong();
    private final AtomicLong restarts = new AtomicLong();
    private final AtomicInteger pendingCount = new AtomicInteger();
    private final AtomicInteger readinessCount = new AtomicInteger();
    private final Map<Long, PendingHighlight> pending = new LinkedHashMap<>();
    private final List<CompletableFuture<SFMSyntaxServerProtocol.ServerHello>> readiness = new ArrayList<>();
    private final CompletableFuture<Void> termination = new CompletableFuture<>();
    private volatile Lifecycle lifecycle = Lifecycle.STOPPED;
    private volatile Optional<SFMSyntaxServerProtocol.ServerHello> hello = Optional.empty();
    private volatile SessionState session;
    private long nextSessionEpoch;
    private int consecutiveFailures;
    private long retryNotBeforeNanos;
    private ScheduledFuture<?> deferredLaunch;

    public SFMSyntaxServerSupervisor(Configuration configuration) {
        this(
                configuration,
                new ProcessBuilderSessionFactory(),
                Executors.newSingleThreadExecutor(daemonThreads("sfm-syntax-state")),
                Executors.newSingleThreadExecutor(daemonThreads("sfm-syntax-reader")),
                Executors.newSingleThreadScheduledExecutor(daemonThreads("sfm-syntax-timer")),
                true
        );
    }

    SFMSyntaxServerSupervisor(
            Configuration configuration,
            SessionFactory sessionFactory,
            ExecutorService stateExecutor,
            ExecutorService readerExecutor,
            ScheduledExecutorService scheduler,
            boolean ownsExecutors
    ) {
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        this.sessionFactory = Objects.requireNonNull(sessionFactory, "sessionFactory");
        this.stateExecutor = Objects.requireNonNull(stateExecutor, "stateExecutor");
        this.readerExecutor = Objects.requireNonNull(readerExecutor, "readerExecutor");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.ownsExecutors = ownsExecutors;
    }

    public Configuration configuration() {
        return configuration;
    }

    public Lifecycle lifecycle() {
        return lifecycle;
    }

    /** Present only after a fully validated handshake for the current process. */
    public Optional<SFMSyntaxServerProtocol.ServerHello> hello() {
        return hello;
    }

    public CompletableFuture<Void> termination() {
        return termination;
    }

    public Telemetry telemetry() {
        SessionState current = session;
        return new Telemetry(
                lifecycle,
                launchAttempts.get(),
                sessionsReady.get(),
                launchFailures.get(),
                protocolFailures.get(),
                transportFailures.get(),
                submitted.get(),
                completed.get(),
                cancelled.get(),
                timedOut.get(),
                remoteFailures.get(),
                lateResponses.get(),
                restarts.get(),
                pendingCount.get(),
                readinessCount.get(),
                current != null && current.worker.alive()
        );
    }

    public Submission submit(SFMSyntaxHighlightRequest request) {
        return submit(request, configuration.requestTimeout());
    }

    public Submission submit(SFMSyntaxHighlightRequest request, Duration timeout) {
        Objects.requireNonNull(request, "request");
        positive(timeout, "timeout");
        CompletableFuture<SFMSyntaxHighlightResult> result = new CompletableFuture<>();
        AtomicBoolean cancellationRequested = new AtomicBoolean();
        Runnable cancellationAction = () -> {
            if (!cancellationRequested.compareAndSet(false, true)) return;
            executeState(() -> cancelPending(
                    request,
                    new CancellationException("Syntax request cancelled"),
                    CancellationKind.EXPLICIT,
                    result
            ), result);
        };
        executeState(() -> accept(request, result, timeout, cancellationRequested), result);
        return new Submission(request, result, cancellationAction);
    }

    /** Start lazily and complete asynchronously only after the validated server hello. */
    public CompletableFuture<SFMSyntaxServerProtocol.ServerHello> start(Duration timeout) {
        positive(timeout, "timeout");
        CompletableFuture<SFMSyntaxServerProtocol.ServerHello> result = new CompletableFuture<>();
        executeState(() -> beginReady(result, timeout), result);
        return result;
    }

    /**
     * Explicit retry seam for a user action or diagnostic surface. Unlike normal
     * submissions, this clears the current crash/launch backoff and attempts to
     * establish a session immediately.
     */
    public CompletableFuture<SFMSyntaxServerProtocol.ServerHello> retry(Duration timeout) {
        positive(timeout, "timeout");
        CompletableFuture<SFMSyntaxServerProtocol.ServerHello> result = new CompletableFuture<>();
        executeState(() -> {
            clearRestartBackoff();
            beginReady(result, timeout);
        }, result);
        return result;
    }

    private void accept(
            SFMSyntaxHighlightRequest request,
            CompletableFuture<SFMSyntaxHighlightResult> result,
            Duration timeout,
            AtomicBoolean cancellationRequested
    ) {
        if (closing.get()) {
            result.completeExceptionally(new WorkerUnavailableException("Syntax worker is closed"));
            return;
        }
        if (cancellationRequested.get() || result.isCancelled()) {
            result.completeExceptionally(new CancellationException("Syntax request cancelled before submission"));
            return;
        }
        try {
            request.validate(SFMSyntaxHighlightLimits.defaults());
        } catch (IllegalArgumentException failure) {
            result.completeExceptionally(failure);
            return;
        }
        if (pending.containsKey(request.requestId())) {
            result.completeExceptionally(new IllegalArgumentException("Duplicate pending syntax request id"));
            return;
        }
        long queued = pending.values().stream().filter(value -> value.sentEpoch == 0).count();
        if (queued >= configuration.maximumPendingRequests()) {
            result.completeExceptionally(new CapacityException("Local syntax request queue limit is reached"));
            return;
        }
        PendingHighlight value = new PendingHighlight(request, result);
        pending.put(request.requestId(), value);
        pendingCount.set(pending.size());
        submitted.incrementAndGet();
        value.timeout = schedule(() -> executeState(() -> cancelPending(
                request,
                new TimeoutException("Syntax request timed out"),
                CancellationKind.TIMEOUT,
                result
        ), result), timeout);
        ensureSession();
        flushPending();
    }

    private void beginReady(
            CompletableFuture<SFMSyntaxServerProtocol.ServerHello> result,
            Duration timeout
    ) {
        if (closing.get()) {
            result.completeExceptionally(new WorkerUnavailableException("Syntax worker is closed"));
            return;
        }
        SessionState current = session;
        if (hello.isPresent() && current != null && current.phase == Lifecycle.READY) {
            result.complete(hello.orElseThrow());
            return;
        }
        if (readiness.size() >= MAXIMUM_PENDING_READINESS) {
            result.completeExceptionally(new CapacityException("Syntax readiness pending limit is reached"));
            return;
        }
        readiness.add(result);
        readinessCount.set(readiness.size());
        ScheduledFuture<?> timeoutTask = schedule(() -> executeState(() -> {
            if (readiness.remove(result)) {
                readinessCount.set(readiness.size());
                result.completeExceptionally(new TimeoutException("Syntax-worker readiness timed out"));
            }
        }, result), timeout);
        result.whenComplete((ignored, failure) -> timeoutTask.cancel(false));
        ensureSession();
    }

    private void ensureSession() {
        if (session != null) return;
        long remainingBackoffNanos = retryNotBeforeNanos - System.nanoTime();
        if (remainingBackoffNanos > 0) {
            scheduleDeferredLaunch(Duration.ofNanos(remainingBackoffNanos));
            return;
        }
        cancelTimer(deferredLaunch);
        deferredLaunch = null;
        lifecycle = Lifecycle.STARTING;
        hello = Optional.empty();
        launchAttempts.incrementAndGet();
        long epoch = incrementEpoch();
        WorkerSession worker;
        try {
            worker = sessionFactory.start(configuration);
        } catch (IOException | RuntimeException failure) {
            launchFailures.incrementAndGet();
            lifecycle = Lifecycle.STOPPED;
            recordRestartFailure();
            WorkerUnavailableException unavailable =
                    new WorkerUnavailableException("Unable to start the syntax worker", failure);
            failAllPending(unavailable);
            failReadiness(unavailable);
            privacyLog("SFM_SYNTAX_WORKER_LAUNCH_FAILED", failure);
            return;
        }
        if (epoch > 1) restarts.incrementAndGet();
        SessionState started = new SessionState(epoch, worker, configuration.maximumFrameBytes());
        session = started;
        started.handshakeTimeout = schedule(
                () -> executeState(() -> handshakeTimedOut(started), null),
                configuration.handshakeTimeout()
        );
        readerExecutor.execute(() -> readLoop(started));
        try {
            write(started, SFMSyntaxServerProtocol.hello(
                    CLIENT_NAME,
                    CLIENT_VERSION,
                    configuration.maximumFrameBytes()
            ));
            SFM.LOGGER.info("SFM_SYNTAX_WORKER_START pid={} epoch={}", worker.pid(), epoch);
        } catch (IOException failure) {
            failSession(started, new WorkerUnavailableException(
                    "Unable to write syntax-worker hello", failure), FailureKind.TRANSPORT);
        }
    }

    private void scheduleDeferredLaunch(Duration delay) {
        if (deferredLaunch != null && !deferredLaunch.isDone()) return;
        deferredLaunch = schedule(() -> executeState(() -> {
            deferredLaunch = null;
            if (!pending.isEmpty() || !readiness.isEmpty()) ensureSession();
        }, null), delay);
    }

    private void readLoop(SessionState observed) {
        try {
            while (true) {
                Optional<String> encoded = SFMSyntaxServerFrameCodec.read(
                        observed.worker.stdout(),
                        observed.negotiatedMaximumFrameBytes
                );
                if (encoded.isEmpty()) {
                    executeState(() -> failSession(
                            observed,
                            new WorkerUnavailableException("Syntax worker reached EOF"),
                            FailureKind.TRANSPORT
                    ), null);
                    return;
                }
                SFMSyntaxServerProtocol.ServerFrame decoded =
                        SFMSyntaxServerProtocol.decodeServerFrame(encoded.orElseThrow());
                dispatchDecodedFrame(observed, decoded);
            }
        } catch (IOException | RuntimeException failure) {
            executeState(() -> failSession(
                    observed,
                    new WorkerUnavailableException("Syntax worker protocol reader failed", failure),
                    failure instanceof SFMSyntaxServerProtocol.ProtocolException
                            ? FailureKind.PROTOCOL
                            : FailureKind.TRANSPORT
            ), null);
        }
    }

    /**
     * The reader waits until the single state owner consumes each decoded frame.
     * This is deliberate transport backpressure: even a runaway worker can have
     * at most one decoded frame queued per owned reader instead of filling an
     * unbounded executor queue with frame payloads.
     */
    private void dispatchDecodedFrame(
            SessionState observed,
            SFMSyntaxServerProtocol.ServerFrame decoded
    ) throws IOException {
        CompletableFuture<Void> consumed = new CompletableFuture<>();
        try {
            stateExecutor.execute(() -> {
                try {
                    handleFrame(observed, decoded);
                    consumed.complete(null);
                } catch (RuntimeException failure) {
                    consumed.completeExceptionally(failure);
                }
            });
        } catch (RejectedExecutionException failure) {
            throw new IOException("Syntax supervisor rejected a decoded worker frame", failure);
        }
        try {
            consumed.get();
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IOException("Syntax worker frame delivery was interrupted", failure);
        } catch (ExecutionException failure) {
            throw new IOException("Syntax worker frame handling failed", failure.getCause());
        }
    }

    private void handleFrame(SessionState observed, SFMSyntaxServerProtocol.ServerFrame frame) {
        if (session != observed) {
            lateResponses.incrementAndGet();
            return;
        }
        if (observed.phase == Lifecycle.STARTING) {
            if (!(frame instanceof SFMSyntaxServerProtocol.HelloFrame helloFrame)) {
                failSession(observed, new ProtocolMismatchException(
                        "Syntax worker sent a non-hello frame before handshake"), FailureKind.PROTOCOL);
                return;
            }
            acceptHello(observed, helloFrame.hello());
            return;
        }
        if (observed.phase == Lifecycle.STOPPING) {
            if (frame instanceof SFMSyntaxServerProtocol.ShutdownFrame) {
                finishStoppedSession(observed, true);
            } else {
                lateResponses.incrementAndGet();
            }
            return;
        }
        if (observed.phase != Lifecycle.READY) {
            lateResponses.incrementAndGet();
            return;
        }
        if (frame instanceof SFMSyntaxServerProtocol.HelloFrame) {
            failSession(observed, new ProtocolMismatchException(
                    "Syntax worker sent a duplicate hello"), FailureKind.PROTOCOL);
        } else if (frame instanceof SFMSyntaxServerProtocol.HighlightResultFrame highlighted) {
            completeHighlight(observed, highlighted.result());
        } else if (frame instanceof SFMSyntaxServerProtocol.CancelledFrame cancellation) {
            observeCancellation(observed, cancellation);
        } else if (frame instanceof SFMSyntaxServerProtocol.PongFrame) {
            lateResponses.incrementAndGet();
        } else if (frame instanceof SFMSyntaxServerProtocol.ShutdownFrame) {
            failSession(observed, new WorkerUnavailableException(
                    "Syntax worker stopped unexpectedly"), FailureKind.TRANSPORT);
        } else if (frame instanceof SFMSyntaxServerProtocol.ErrorFrame error) {
            handleRemoteError(observed, error);
        }
    }

    private void acceptHello(
            SessionState observed,
            SFMSyntaxServerProtocol.ServerHello value
    ) {
        if (value.maximumFrameBytes() > configuration.maximumFrameBytes()) {
            failSession(observed, new ProtocolMismatchException(
                    "Syntax-worker hello exceeded the client frame limit"), FailureKind.PROTOCOL);
            return;
        }
        cancelTimer(observed.handshakeTimeout);
        observed.negotiatedMaximumFrameBytes = value.maximumFrameBytes();
        observed.maximumPendingRequests = Math.min(
                configuration.maximumPendingRequests(),
                value.maximumPendingRequests()
        );
        observed.phase = Lifecycle.READY;
        lifecycle = Lifecycle.READY;
        hello = Optional.of(value);
        clearRestartBackoff();
        sessionsReady.incrementAndGet();
        SFM.LOGGER.info(
                "SFM_SYNTAX_WORKER_READY pid={} epoch={} frame_limit={} pending_limit={}",
                observed.worker.pid(),
                observed.epoch,
                observed.negotiatedMaximumFrameBytes,
                observed.maximumPendingRequests
        );
        List<CompletableFuture<SFMSyntaxServerProtocol.ServerHello>> waiting = List.copyOf(readiness);
        readiness.clear();
        readinessCount.set(0);
        waiting.forEach(result -> result.complete(value));
        flushPending();
    }

    private void flushPending() {
        SessionState current = session;
        if (current == null || current.phase != Lifecycle.READY) return;
        int sent = (int) pending.values().stream()
                .filter(value -> value.sentEpoch == current.epoch)
                .count();
        for (PendingHighlight value : List.copyOf(pending.values())) {
            if (value.sentEpoch != 0) continue;
            if (sent >= current.maximumPendingRequests) return;
            String encoded = SFMSyntaxServerProtocol.highlight(value.request);
            if (SFMSyntaxServerFrameCodec.encodedPayloadBytes(encoded)
                    > current.negotiatedMaximumFrameBytes) {
                removePending(value);
                value.result.completeExceptionally(new CapacityException(
                        "Encoded syntax request exceeds the negotiated worker frame limit"
                ));
                continue;
            }
            try {
                write(current, encoded);
                value.sentEpoch = current.epoch;
                sent++;
            } catch (IOException failure) {
                failSession(current, new WorkerUnavailableException(
                        "Unable to write syntax request", failure), FailureKind.TRANSPORT);
                return;
            }
        }
    }

    private void completeHighlight(
            SessionState observed,
            SFMSyntaxHighlightResult result
    ) {
        PendingHighlight value = pending.get(result.requestId());
        if (value == null) {
            lateResponses.incrementAndGet();
            return;
        }
        try {
            if (!result.matchesIdentity(value.request)) {
                throw new IllegalArgumentException("Syntax response identity mismatch");
            }
            result.validateAgainst(value.request, SFMSyntaxHighlightLimits.defaults());
        } catch (IllegalArgumentException failure) {
            failSession(observed, new ProtocolMismatchException(
                    "Syntax worker returned an invalid highlight result", failure), FailureKind.PROTOCOL);
            return;
        }
        removePending(value);
        if (value.localTerminal) {
            lateResponses.incrementAndGet();
        } else {
            completed.incrementAndGet();
            value.result.complete(result);
        }
        flushPending();
    }

    private void observeCancellation(
            SessionState observed,
            SFMSyntaxServerProtocol.CancelledFrame frame
    ) {
        PendingHighlight value = pending.get(frame.requestId());
        if (value == null) {
            lateResponses.incrementAndGet();
            return;
        }
        if (!matches(value.request, frame)) {
            failSession(observed, new ProtocolMismatchException(
                    "Syntax cancellation acknowledgement identity mismatch"), FailureKind.PROTOCOL);
            return;
        }
        if (frame.status().equals("already-terminal")) {
            removePending(value);
            if (!value.localTerminal) {
                value.localTerminal = true;
                value.result.completeExceptionally(new CancellationException(
                        "Syntax request was already terminal remotely"
                ));
            }
            flushPending();
        }
    }

    private void handleRemoteError(
            SessionState observed,
            SFMSyntaxServerProtocol.ErrorFrame error
    ) {
        if (error.fatal()) {
            failSession(observed, new ProtocolMismatchException(
                    "Syntax worker reported a fatal protocol error [" + boundedCode(error.code()) + "]"
            ), FailureKind.PROTOCOL);
            return;
        }
        if (error.requestId().isPresent()) {
            PendingHighlight value = pending.get(error.requestId().orElseThrow());
            if (value != null
                    && error.requestGeneration().orElse(value.request.requestGeneration())
                    == value.request.requestGeneration()) {
                removePending(value);
                remoteFailures.incrementAndGet();
                if (!value.localTerminal) {
                    value.result.completeExceptionally(new RemoteSyntaxException(error.code()));
                }
                flushPending();
                return;
            }
        }
        lateResponses.incrementAndGet();
    }

    private void cancelPending(
            SFMSyntaxHighlightRequest request,
            Throwable reason,
            CancellationKind kind,
            CompletableFuture<SFMSyntaxHighlightResult> expectedResult
    ) {
        PendingHighlight value = pending.get(request.requestId());
        if (value == null || value.result != expectedResult || !sameIdentity(value.request, request)) {
            expectedResult.completeExceptionally(reason);
            return;
        }
        if (value.localTerminal) return;
        value.localTerminal = true;
        cancelTimer(value.timeout);
        if (kind == CancellationKind.TIMEOUT) timedOut.incrementAndGet();
        else cancelled.incrementAndGet();
        value.result.completeExceptionally(reason);
        SessionState current = session;
        if (value.sentEpoch != 0
                && current != null
                && current.epoch == value.sentEpoch
                && current.phase == Lifecycle.READY) {
            try {
                write(current, SFMSyntaxServerProtocol.cancel(request, kind.wireReason));
            } catch (IOException failure) {
                failSession(current, new WorkerUnavailableException(
                        "Unable to write syntax cancellation", failure), FailureKind.TRANSPORT);
            }
            return;
        }
        removePending(value);
        flushPending();
    }

    private void handshakeTimedOut(SessionState observed) {
        if (session == observed && observed.phase == Lifecycle.STARTING) {
            failSession(observed, new WorkerUnavailableException(
                    "Syntax-worker handshake timed out"), FailureKind.TRANSPORT);
        }
    }

    private void failSession(SessionState observed, Throwable failure, FailureKind kind) {
        if (observed == null || session != observed) return;
        if (kind == FailureKind.PROTOCOL) protocolFailures.incrementAndGet();
        else transportFailures.incrementAndGet();
        recordRestartFailure();
        privacyLog(kind == FailureKind.PROTOCOL
                ? "SFM_SYNTAX_WORKER_PROTOCOL_FAILED"
                : "SFM_SYNTAX_WORKER_TRANSPORT_FAILED", failure);
        session = null;
        hello = Optional.empty();
        lifecycle = closing.get() ? Lifecycle.STOPPING : Lifecycle.STOPPED;
        cancelTimer(observed.handshakeTimeout);
        failAllPending(failure);
        failReadiness(failure);
        terminate(observed, configuration.shutdownTimeout());
        if (closing.get()) finishClosed();
    }

    private void recordRestartFailure() {
        consecutiveFailures = Math.min(consecutiveFailures + 1, 31);
        long maximum = configuration.restartBackoffMaximum().toNanos();
        long delay = configuration.restartBackoffInitial().toNanos();
        for (int index = 1; index < consecutiveFailures && delay < maximum; index++) {
            delay = delay > maximum / 2 ? maximum : Math.min(maximum, delay * 2);
        }
        retryNotBeforeNanos = System.nanoTime() + Math.min(delay, maximum);
    }

    private void clearRestartBackoff() {
        consecutiveFailures = 0;
        retryNotBeforeNanos = 0;
        cancelTimer(deferredLaunch);
        deferredLaunch = null;
    }

    private void finishStoppedSession(SessionState observed, boolean acknowledged) {
        if (session != observed) return;
        session = null;
        hello = Optional.empty();
        cancelTimer(observed.handshakeTimeout);
        lifecycle = closing.get() ? Lifecycle.STOPPING : Lifecycle.STOPPED;
        terminate(observed, acknowledged ? Duration.ZERO : configuration.shutdownTimeout());
        if (closing.get()) finishClosed();
    }

    private void terminate(SessionState value, Duration grace) {
        try {
            value.worker.terminateTree(grace);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException failure) {
            privacyLog("SFM_SYNTAX_WORKER_REAP_FAILED", failure);
        }
    }

    private void failAllPending(Throwable failure) {
        List<PendingHighlight> values = List.copyOf(pending.values());
        pending.clear();
        pendingCount.set(0);
        for (PendingHighlight value : values) {
            cancelTimer(value.timeout);
            value.result.completeExceptionally(failure);
        }
    }

    private void failReadiness(Throwable failure) {
        List<CompletableFuture<SFMSyntaxServerProtocol.ServerHello>> waiting = List.copyOf(readiness);
        readiness.clear();
        readinessCount.set(0);
        waiting.forEach(result -> result.completeExceptionally(failure));
    }

    private void removePending(PendingHighlight value) {
        if (!pending.remove(value.request.requestId(), value)) return;
        cancelTimer(value.timeout);
        pendingCount.set(pending.size());
    }

    private void write(SessionState destination, String json) throws IOException {
        if (session != destination) throw new IOException("Syntax worker session was replaced");
        SFMSyntaxServerFrameCodec.write(
                destination.worker.stdin(),
                json,
                destination.negotiatedMaximumFrameBytes
        );
    }

    @Override
    public void close() {
        if (!closing.compareAndSet(false, true)) return;
        try {
            stateExecutor.execute(this::beginClose);
        } catch (RejectedExecutionException failure) {
            lifecycle = Lifecycle.CLOSED;
            termination.complete(null);
        }
    }

    private void beginClose() {
        lifecycle = Lifecycle.STOPPING;
        clearRestartBackoff();
        CancellationException stopped = new CancellationException("Syntax supervisor is closing");
        failAllPending(stopped);
        failReadiness(stopped);
        SessionState current = session;
        if (current == null) {
            finishClosed();
            return;
        }
        if (current.phase != Lifecycle.READY) {
            finishStoppedSession(current, false);
            return;
        }
        current.phase = Lifecycle.STOPPING;
        try {
            write(current, SFMSyntaxServerProtocol.shutdown("minecraft client shutdown"));
            schedule(() -> executeState(() -> {
                if (session == current) finishStoppedSession(current, false);
            }, null), configuration.shutdownTimeout());
        } catch (IOException failure) {
            finishStoppedSession(current, false);
        }
    }

    private void finishClosed() {
        if (lifecycle == Lifecycle.CLOSED) return;
        lifecycle = Lifecycle.CLOSED;
        termination.complete(null);
        SFM.LOGGER.info(
                "SFM_SYNTAX_WORKER_CLOSED launches={} ready={} completed={} failures={}",
                launchAttempts.get(),
                sessionsReady.get(),
                completed.get(),
                launchFailures.get() + protocolFailures.get() + transportFailures.get()
        );
        if (ownsExecutors) {
            scheduler.shutdownNow();
            readerExecutor.shutdownNow();
            stateExecutor.shutdown();
        }
    }

    private void executeState(Runnable action, CompletableFuture<?> affected) {
        try {
            stateExecutor.execute(action);
        } catch (RejectedExecutionException failure) {
            if (affected != null) {
                affected.completeExceptionally(new WorkerUnavailableException(
                        "Syntax supervisor executor is unavailable", failure));
            }
        }
    }

    private ScheduledFuture<?> schedule(Runnable action, Duration delay) {
        return scheduler.schedule(action, delay.toNanos(), TimeUnit.NANOSECONDS);
    }

    private long incrementEpoch() {
        if (nextSessionEpoch == Long.MAX_VALUE) {
            throw new IllegalStateException("Syntax session epoch exhausted");
        }
        return ++nextSessionEpoch;
    }

    private static boolean matches(
            SFMSyntaxHighlightRequest request,
            SFMSyntaxServerProtocol.CancelledFrame frame
    ) {
        return request.requestId() == frame.requestId()
                && request.requestGeneration() == frame.requestGeneration()
                && request.originId().equals(frame.originId())
                && request.originGeneration() == frame.originGeneration();
    }

    private static boolean sameIdentity(
            SFMSyntaxHighlightRequest first,
            SFMSyntaxHighlightRequest second
    ) {
        return first.requestId() == second.requestId()
                && first.requestGeneration() == second.requestGeneration()
                && first.originId().equals(second.originId())
                && first.originGeneration() == second.originGeneration()
                && first.language().equals(second.language())
                && first.sourceSha256().equals(second.sourceSha256());
    }

    private static void cancelTimer(ScheduledFuture<?> future) {
        if (future != null) future.cancel(false);
    }

    private static ThreadFactory daemonThreads(String baseName) {
        AtomicInteger ids = new AtomicInteger();
        return action -> {
            Thread thread = new Thread(action, baseName + "-" + ids.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    private static Duration positive(Duration value, String label) {
        Objects.requireNonNull(value, label);
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(label + " must be positive");
        }
        return value;
    }

    private static String requireNonBlank(String value, String label) {
        Objects.requireNonNull(value, label);
        if (value.isBlank()) throw new IllegalArgumentException(label + " must not be blank");
        return value;
    }

    private static String boundedCode(String value) {
        if (value == null) return "unknown";
        String cleaned = value.replaceAll("[^a-zA-Z0-9_.-]", "_");
        if (cleaned.length() > 64) cleaned = cleaned.substring(0, 64);
        return cleaned.isEmpty() ? "unknown" : cleaned;
    }

    private static void privacyLog(String event, Throwable failure) {
        SFM.LOGGER.warn("{} failure_type={}", event, failure.getClass().getSimpleName());
    }

    private enum FailureKind {
        PROTOCOL,
        TRANSPORT
    }

    private enum CancellationKind {
        EXPLICIT("client-cancelled"),
        TIMEOUT("client-timeout");

        private final String wireReason;

        CancellationKind(String wireReason) {
            this.wireReason = wireReason;
        }
    }

    private static final class PendingHighlight {
        private final SFMSyntaxHighlightRequest request;
        private final CompletableFuture<SFMSyntaxHighlightResult> result;
        private boolean localTerminal;
        private long sentEpoch;
        private ScheduledFuture<?> timeout;

        private PendingHighlight(
                SFMSyntaxHighlightRequest request,
                CompletableFuture<SFMSyntaxHighlightResult> result
        ) {
            this.request = request;
            this.result = result;
        }
    }

    private static final class SessionState {
        private final long epoch;
        private final WorkerSession worker;
        private volatile int negotiatedMaximumFrameBytes;
        private volatile Lifecycle phase = Lifecycle.STARTING;
        private int maximumPendingRequests;
        private ScheduledFuture<?> handshakeTimeout;

        private SessionState(
                long epoch,
                WorkerSession worker,
                int negotiatedMaximumFrameBytes
        ) {
            this.epoch = epoch;
            this.worker = worker;
            this.negotiatedMaximumFrameBytes = negotiatedMaximumFrameBytes;
        }
    }

    private static final class ProcessBuilderSessionFactory implements SessionFactory {
        @Override
        public WorkerSession start(Configuration configuration) throws IOException {
            ProcessBuilder builder = new ProcessBuilder(configuration.command());
            builder.redirectError(ProcessBuilder.Redirect.INHERIT);
            Process process = builder.start();
            return new ProcessWorkerSession(process);
        }
    }

    private static final class ProcessWorkerSession implements WorkerSession {
        private final Process process;
        private final ProcessHandle root;

        private ProcessWorkerSession(Process process) {
            this.process = process;
            this.root = process.toHandle();
        }

        @Override
        public long pid() {
            return process.pid();
        }

        @Override
        public InputStream stdout() {
            return process.getInputStream();
        }

        @Override
        public OutputStream stdin() {
            return process.getOutputStream();
        }

        @Override
        public boolean alive() {
            return process.isAlive();
        }

        @Override
        public void terminateTree(Duration grace) throws InterruptedException {
            List<ProcessHandle> descendants = root.descendants().toList();
            closeQuietly(process.getOutputStream());
            destroy(descendants, false);
            if (process.isAlive()) process.destroy();
            long graceMillis = Math.max(0, grace.toMillis());
            if (process.isAlive() && graceMillis > 0) {
                process.waitFor(graceMillis, TimeUnit.MILLISECONDS);
            }
            List<ProcessHandle> survivors = descendants.stream()
                    .filter(ProcessHandle::isAlive)
                    .toList();
            destroy(survivors, true);
            if (process.isAlive()) {
                process.destroyForcibly();
                process.waitFor(Math.max(1, graceMillis), TimeUnit.MILLISECONDS);
            }
            closeQuietly(process.getInputStream());
            closeQuietly(process.getErrorStream());
        }

        private static void destroy(List<ProcessHandle> handles, boolean forcibly) {
            for (int index = handles.size() - 1; index >= 0; index--) {
                ProcessHandle handle = handles.get(index);
                if (!handle.isAlive()) continue;
                if (forcibly) handle.destroyForcibly();
                else handle.destroy();
            }
        }

        private static void closeQuietly(AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception ignored) {
            }
        }
    }
}
