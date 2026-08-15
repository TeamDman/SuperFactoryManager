package ca.teamdman.sfm.client.symbol;

import ca.teamdman.sfm.SFM;

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
 * Owns one restartable long-lived {@code symbol serve} process and all of its
 * framed requests. Process launch, I/O, JSON decoding, and future completion
 * happen on dedicated daemon threads, never on Minecraft's render thread.
 */
public final class SFMSymbolServerSupervisor implements AutoCloseable {
    public static final String EXECUTABLE_PROPERTY = "sfm.symbol.workerExecutable";
    public static final String DEFAULT_EXECUTABLE = "sfm-propagate-changes.exe";
    public static final String CLIENT_NAME = "sfm-minecraft";
    public static final String CLIENT_VERSION = "1";

    public enum Lifecycle {
        STOPPED,
        STARTING,
        READY,
        STOPPING,
        CLOSED
    }

    public record Configuration(
            String executable,
            String branch,
            Duration handshakeTimeout,
            Duration requestTimeout,
            Duration shutdownTimeout,
            int maximumFrameBytes,
            int maximumPendingDefinitions,
            int maximumPendingControls
    ) {
        public Configuration {
            executable = requireNonBlank(executable, "executable");
            branch = requireNonBlank(branch, "branch");
            handshakeTimeout = positive(handshakeTimeout, "handshakeTimeout");
            requestTimeout = positive(requestTimeout, "requestTimeout");
            shutdownTimeout = positive(shutdownTimeout, "shutdownTimeout");
            if (maximumFrameBytes <= 0) throw new IllegalArgumentException("maximumFrameBytes must be positive");
            if (maximumPendingDefinitions <= 0) {
                throw new IllegalArgumentException("maximumPendingDefinitions must be positive");
            }
            if (maximumPendingControls <= 0) {
                throw new IllegalArgumentException("maximumPendingControls must be positive");
            }
        }

        public static Configuration defaults(String branch) {
            String configured = System.getProperty(EXECUTABLE_PROPERTY, "").trim();
            return new Configuration(
                    configured.isEmpty() ? DEFAULT_EXECUTABLE : configured,
                    branch,
                    Duration.ofSeconds(10),
                    Duration.ofSeconds(3),
                    Duration.ofSeconds(2),
                    SFMDefinitionWorkerFrameCodec.DEFAULT_MAXIMUM_FRAME_BYTES,
                    8,
                    16
            );
        }

        public List<String> command() {
            return List.of(executable, "symbol", "serve", "--branch", branch);
        }
    }

    /** Injectable process/session seam used by deterministic protocol tests. */
    @FunctionalInterface
    public interface SessionFactory {
        WorkerSession start(Configuration configuration) throws IOException;
    }

    /** One owned worker process with protocol-only stdout and inherited/log stderr. */
    public interface WorkerSession {
        long pid();

        InputStream stdout();

        OutputStream stdin();

        boolean alive();

        /** Destroy and wait for the process tree without touching the calling/render thread. */
        void terminateTree(Duration grace) throws InterruptedException;
    }

    public record Submission(
            SFMDefinitionRequest request,
            CompletableFuture<SFMDefinitionResult> result,
            Runnable cancellation
    ) {
        public Submission {
            Objects.requireNonNull(request, "request");
            Objects.requireNonNull(result, "result");
            Objects.requireNonNull(cancellation, "cancellation");
        }
    }

    /** Content-free bounded evidence suitable for logs and diagnostics. */
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
            int pendingDefinitions,
            int pendingControls,
            boolean processAlive
    ) {
    }

    public static class WorkerUnavailableException extends IOException {
        public WorkerUnavailableException(String message) { super(message); }
        public WorkerUnavailableException(String message, Throwable cause) { super(message, cause); }
    }

    public static final class ProtocolMismatchException extends WorkerUnavailableException {
        public ProtocolMismatchException(String message) { super(message); }
        public ProtocolMismatchException(String message, Throwable cause) { super(message, cause); }
    }

    public static final class CapacityException extends WorkerUnavailableException {
        public CapacityException(String message) { super(message); }
    }

    public static final class RemoteDefinitionException extends WorkerUnavailableException {
        private final String code;
        private final boolean retryable;

        public RemoteDefinitionException(String code, String message, boolean retryable) {
            super("Symbol worker rejected the definition request [" + boundedCode(code) + "]: "
                    + boundedMessage(message));
            this.code = boundedCode(code);
            this.retryable = retryable;
        }

        public String code() { return code; }
        public boolean retryable() { return retryable; }
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
    private final AtomicInteger controlCount = new AtomicInteger();
    private final Map<Long, PendingDefinition> pending = new LinkedHashMap<>();
    private final Map<Long, PendingPing> pings = new LinkedHashMap<>();
    private final Map<Long, CompletableFuture<Long>> workspaceUpdates = new LinkedHashMap<>();
    private final List<CompletableFuture<SFMSymbolServerProtocol.ServerHello>> readiness = new ArrayList<>();
    private final CompletableFuture<Void> termination = new CompletableFuture<>();
    private volatile Lifecycle lifecycle = Lifecycle.STOPPED;
    private volatile Optional<SFMSymbolServerProtocol.ServerHello> hello = Optional.empty();
    private volatile SessionState session;
    private long nextSessionEpoch;
    private long nextPingNonce;

    public SFMSymbolServerSupervisor(Configuration configuration) {
        this(
                configuration,
                new ProcessBuilderSessionFactory(),
                Executors.newSingleThreadExecutor(daemonThreads("sfm-symbol-state")),
                Executors.newCachedThreadPool(daemonThreads("sfm-symbol-reader")),
                Executors.newSingleThreadScheduledExecutor(daemonThreads("sfm-symbol-timer")),
                true
        );
    }

    SFMSymbolServerSupervisor(
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
    public Optional<SFMSymbolServerProtocol.ServerHello> hello() {
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
                controlCount.get(),
                current != null && current.worker.alive()
        );
    }

    public Submission submit(SFMDefinitionRequest request) {
        return submit(request, configuration.requestTimeout());
    }

    public Submission submit(SFMDefinitionRequest request, Duration timeout) {
        Objects.requireNonNull(request, "request");
        positive(timeout, "timeout");
        CompletableFuture<SFMDefinitionResult> result = new CompletableFuture<>();
        AtomicBoolean cancellationRequested = new AtomicBoolean();
        Runnable cancellationAction = () -> {
            if (cancellationRequested.compareAndSet(false, true)) {
                executeState(() -> cancelPending(
                        request.requestId(),
                        request.requestGeneration(),
                        new CancellationException("Definition request cancelled"),
                        CancellationKind.EXPLICIT
                ), result);
            }
        };
        executeState(() -> accept(request, result, timeout, cancellationRequested), result);
        return new Submission(request, result, cancellationAction);
    }

    /** Start lazily and complete asynchronously after the validated hello. */
    public CompletableFuture<SFMSymbolServerProtocol.ServerHello> start(Duration timeout) {
        positive(timeout, "timeout");
        CompletableFuture<SFMSymbolServerProtocol.ServerHello> result = new CompletableFuture<>();
        executeState(() -> beginReady(result, timeout), result);
        return result;
    }

    public CompletableFuture<Long> ping(Duration timeout) {
        positive(timeout, "timeout");
        CompletableFuture<Long> result = new CompletableFuture<>();
        executeState(() -> beginPing(result, timeout), result);
        return result;
    }

    public CompletableFuture<Long> updateWorkspaceGeneration(long generation, Duration timeout) {
        if (generation < 0) throw new IllegalArgumentException("generation must not be negative");
        positive(timeout, "timeout");
        CompletableFuture<Long> result = new CompletableFuture<>();
        executeState(() -> beginWorkspaceUpdate(generation, result, timeout), result);
        return result;
    }

    private void accept(
            SFMDefinitionRequest request,
            CompletableFuture<SFMDefinitionResult> result,
            Duration timeout,
            AtomicBoolean cancellationRequested
    ) {
        if (closing.get()) {
            result.completeExceptionally(new WorkerUnavailableException("Symbol worker is closed"));
            return;
        }
        if (cancellationRequested.get() || result.isCancelled()) return;
        if (request.requestId() <= 0) {
            result.completeExceptionally(new IllegalArgumentException("Worker request id must be positive"));
            return;
        }
        if (!request.workspace().branch().equals(configuration.branch())) {
            result.completeExceptionally(new IllegalArgumentException(
                    "Definition request branch does not match the supervised worker"
            ));
            return;
        }
        if (pending.containsKey(request.requestId())) {
            result.completeExceptionally(new IllegalArgumentException("Duplicate pending definition request id"));
            return;
        }
        if (pending.size() >= configuration.maximumPendingDefinitions()) {
            result.completeExceptionally(new CapacityException("Local definition pending limit is reached"));
            return;
        }
        PendingDefinition value = new PendingDefinition(request, result);
        pending.put(request.requestId(), value);
        pendingCount.set(pending.size());
        submitted.incrementAndGet();
        value.timeout = schedule(() -> executeState(
                () -> cancelPending(
                        request.requestId(),
                        request.requestGeneration(),
                        new TimeoutException("Definition request timed out"),
                        CancellationKind.TIMEOUT
                ),
                result
        ), timeout);
        ensureSession();
        flushPending();
    }

    private void ensureSession() {
        if (session != null) return;
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
            WorkerUnavailableException unavailable =
                    new WorkerUnavailableException("Unable to start the symbol worker", failure);
            failAllPending(unavailable);
            failControls(unavailable);
            privacyLog("SFM_SYMBOL_WORKER_LAUNCH_FAILED", failure);
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
            write(started, SFMSymbolServerProtocol.hello(
                    CLIENT_NAME,
                    CLIENT_VERSION,
                    configuration.maximumFrameBytes()
            ));
            SFM.LOGGER.info("SFM_SYMBOL_WORKER_START pid={} epoch={}", worker.pid(), epoch);
        } catch (IOException failure) {
            failSession(started, new WorkerUnavailableException("Unable to write symbol-worker hello", failure),
                    FailureKind.TRANSPORT);
        }
    }

    private void readLoop(SessionState observed) {
        try {
            while (!closing.get() || observed == session) {
                Optional<String> frame = SFMDefinitionWorkerFrameCodec.read(
                        observed.worker.stdout(),
                        observed.negotiatedMaximumFrameBytes
                );
                if (frame.isEmpty()) {
                    executeState(() -> failSession(
                            observed,
                            new WorkerUnavailableException("Symbol worker reached EOF"),
                            FailureKind.TRANSPORT
                    ), null);
                    return;
                }
                SFMSymbolServerProtocol.ServerFrame decoded =
                        SFMSymbolServerProtocol.decodeServerFrame(frame.orElseThrow());
                executeState(() -> handleFrame(observed, decoded), null);
            }
        } catch (IOException | RuntimeException failure) {
            executeState(() -> failSession(
                    observed,
                    new WorkerUnavailableException("Symbol worker protocol reader failed", failure),
                    failure instanceof SFMSymbolServerProtocol.ProtocolException
                            ? FailureKind.PROTOCOL
                            : FailureKind.TRANSPORT
            ), null);
        }
    }

    private void handleFrame(SessionState observed, SFMSymbolServerProtocol.ServerFrame frame) {
        if (session != observed) {
            lateResponses.incrementAndGet();
            return;
        }
        if (observed.phase == Lifecycle.STARTING) {
            if (!(frame instanceof SFMSymbolServerProtocol.HelloFrame helloFrame)) {
                failSession(observed, new ProtocolMismatchException(
                        "Symbol worker sent a non-hello frame before handshake"), FailureKind.PROTOCOL);
                return;
            }
            acceptHello(observed, helloFrame.hello());
            return;
        }
        if (observed.phase != Lifecycle.READY && !(frame instanceof SFMSymbolServerProtocol.ShutdownFrame)) {
            lateResponses.incrementAndGet();
            return;
        }
        if (frame instanceof SFMSymbolServerProtocol.HelloFrame) {
            failSession(observed, new ProtocolMismatchException("Symbol worker sent a duplicate hello"),
                    FailureKind.PROTOCOL);
        } else if (frame instanceof SFMSymbolServerProtocol.DefinitionResultFrame definition) {
            completeDefinition(definition.result());
        } else if (frame instanceof SFMSymbolServerProtocol.DefinitionCancelledFrame definition) {
            completeCancelled(definition);
        } else if (frame instanceof SFMSymbolServerProtocol.DefinitionFailedFrame definition) {
            completeFailed(definition);
        } else if (frame instanceof SFMSymbolServerProtocol.CancelledFrame) {
            // Cancellation acknowledgements are intentionally not retained.
        } else if (frame instanceof SFMSymbolServerProtocol.WorkspaceGenerationFrame update) {
            completeWorkspaceUpdate(observed, update);
        } else if (frame instanceof SFMSymbolServerProtocol.PongFrame pong) {
            completePong(pong);
        } else if (frame instanceof SFMSymbolServerProtocol.ShutdownFrame) {
            finishStoppedSession(observed, true);
        } else if (frame instanceof SFMSymbolServerProtocol.ErrorFrame error) {
            handleRemoteError(observed, error);
        }
    }

    private void acceptHello(SessionState observed, SFMSymbolServerProtocol.ServerHello value) {
        if (value.maximumFrameBytes() > configuration.maximumFrameBytes()
                || !value.workspace().workspace().branch().equals(configuration.branch())) {
            failSession(observed, new ProtocolMismatchException("Symbol-worker hello negotiation disagrees"),
                    FailureKind.PROTOCOL);
            return;
        }
        cancelTimer(observed.handshakeTimeout);
        observed.negotiatedMaximumFrameBytes = value.maximumFrameBytes();
        observed.maximumPendingDefinitions = Math.min(
                configuration.maximumPendingDefinitions(),
                value.maximumPendingDefinitions()
        );
        observed.workspaceGeneration = value.workspaceGeneration();
        observed.phase = Lifecycle.READY;
        lifecycle = Lifecycle.READY;
        hello = Optional.of(value);
        sessionsReady.incrementAndGet();
        SFM.LOGGER.info(
                "SFM_SYMBOL_WORKER_READY pid={} epoch={} frame_limit={} pending_limit={} workspace_generation={}",
                observed.worker.pid(),
                observed.epoch,
                observed.negotiatedMaximumFrameBytes,
                observed.maximumPendingDefinitions,
                observed.workspaceGeneration
        );
        List<CompletableFuture<SFMSymbolServerProtocol.ServerHello>> waiting = List.copyOf(readiness);
        readiness.clear();
        updateControlCount();
        waiting.forEach(result -> result.complete(value));
        flushPending();
    }

    private void flushPending() {
        SessionState current = session;
        if (current == null || current.phase != Lifecycle.READY) return;
        int sent = (int) pending.values().stream().filter(value -> value.sentEpoch == current.epoch).count();
        List<PendingDefinition> snapshot = new ArrayList<>(pending.values());
        for (PendingDefinition value : snapshot) {
            if (value.sentEpoch != 0) continue;
            if (current.requestedWorkspaceGeneration >= 0) return;
            if (sent >= current.maximumPendingDefinitions) {
                removeAndFail(value, new CapacityException("Worker definition pending limit is reached"));
                continue;
            }
            long requestedGeneration = value.request.workspace().workspaceGeneration();
            if (requestedGeneration < current.workspaceGeneration) {
                removeAndFail(value, new WorkerUnavailableException(
                        "Definition request has a stale workspace generation"));
                continue;
            }
            if (requestedGeneration > current.workspaceGeneration) {
                advanceWorkspace(current, requestedGeneration);
                return;
            }
            SFMSymbolServerProtocol.ServerHello currentHello = hello.orElse(null);
            if (currentHello == null
                    || !value.request.workspace().equals(currentHello.workspace().workspace())) {
                removeAndFail(value, new ProtocolMismatchException(
                        "Definition request workspace does not match the worker handshake"));
                continue;
            }
            try {
                write(current, SFMSymbolServerProtocol.definition(value.request));
                value.sentEpoch = current.epoch;
                sent++;
            } catch (IOException failure) {
                failSession(current, new WorkerUnavailableException(
                        "Unable to write a definition request", failure), FailureKind.TRANSPORT);
                return;
            }
        }
    }

    private boolean advanceWorkspace(SessionState current, long generation) {
        if (generation <= current.workspaceGeneration) return true;
        if (current.requestedWorkspaceGeneration >= 0) {
            return current.requestedWorkspaceGeneration == generation;
        }
        try {
            write(current, SFMSymbolServerProtocol.workspaceGeneration(generation));
        } catch (IOException failure) {
            failSession(current, new WorkerUnavailableException(
                    "Unable to update symbol-worker workspace generation", failure), FailureKind.TRANSPORT);
            return false;
        }
        current.requestedWorkspaceGeneration = generation;
        List<PendingDefinition> stale = pending.values().stream()
                .filter(value -> value.sentEpoch == current.epoch)
                .filter(value -> value.request.workspace().workspaceGeneration() < generation)
                .toList();
        for (PendingDefinition value : stale) {
            removeAndFail(value, new CancellationException(
                    "Definition request superseded by workspace generation"));
        }
        return true;
    }

    private void completeDefinition(SFMDefinitionResult result) {
        PendingDefinition value = pending.get(result.requestId());
        if (value == null) {
            lateResponses.incrementAndGet();
            return;
        }
        if (!result.matches(value.request)) {
            failSession(session, new ProtocolMismatchException("Definition response identity mismatch"),
                    FailureKind.PROTOCOL);
            return;
        }
        removePending(value);
        completed.incrementAndGet();
        value.result.complete(result);
        flushPending();
    }

    private void completeCancelled(SFMSymbolServerProtocol.DefinitionCancelledFrame frame) {
        PendingDefinition value = pending.get(frame.requestId());
        if (value == null) {
            lateResponses.incrementAndGet();
            return;
        }
        if (!matches(value.request, frame.requestId(), frame.requestGeneration(), frame.workspaceGeneration())) {
            failSession(session, new ProtocolMismatchException("Cancellation response identity mismatch"),
                    FailureKind.PROTOCOL);
            return;
        }
        removePending(value);
        cancelled.incrementAndGet();
        value.result.completeExceptionally(new CancellationException("Definition cancelled by worker"));
        flushPending();
    }

    private void completeFailed(SFMSymbolServerProtocol.DefinitionFailedFrame frame) {
        PendingDefinition value = pending.get(frame.requestId());
        if (value == null) {
            lateResponses.incrementAndGet();
            return;
        }
        if (!matches(value.request, frame.requestId(), frame.requestGeneration(), frame.workspaceGeneration())) {
            failSession(session, new ProtocolMismatchException("Failure response identity mismatch"),
                    FailureKind.PROTOCOL);
            return;
        }
        removePending(value);
        remoteFailures.incrementAndGet();
        value.result.completeExceptionally(new RemoteDefinitionException(
                frame.code(), frame.message(), frame.retryable()
        ));
        flushPending();
    }

    private void beginReady(
            CompletableFuture<SFMSymbolServerProtocol.ServerHello> result,
            Duration timeout
    ) {
        if (closing.get()) {
            result.completeExceptionally(new WorkerUnavailableException("Symbol worker is closed"));
            return;
        }
        if (hello.isPresent() && session != null && session.phase == Lifecycle.READY) {
            result.complete(hello.orElseThrow());
            return;
        }
        if (controlCount.get() >= configuration.maximumPendingControls()) {
            result.completeExceptionally(new CapacityException("Symbol-worker control pending limit is reached"));
            return;
        }
        readiness.add(result);
        updateControlCount();
        ScheduledFuture<?> timeoutTask = schedule(() -> executeState(() -> {
            if (readiness.remove(result)) {
                updateControlCount();
                result.completeExceptionally(new TimeoutException("Symbol-worker readiness timed out"));
            }
        }, result), timeout);
        result.whenComplete((ignored, failure) -> timeoutTask.cancel(false));
        ensureSession();
    }

    private void beginPing(CompletableFuture<Long> result, Duration timeout) {
        SessionState current = requireReady(result);
        if (current == null) return;
        if (controlCount.get() >= configuration.maximumPendingControls()) {
            result.completeExceptionally(new CapacityException("Symbol-worker control pending limit is reached"));
            return;
        }
        long nonce = incrementPingNonce();
        PendingPing ping = new PendingPing(nonce, result);
        pings.put(nonce, ping);
        updateControlCount();
        ping.timeout = schedule(() -> executeState(() -> {
            PendingPing removed = pings.remove(nonce);
            updateControlCount();
            if (removed != null) removed.result.completeExceptionally(new TimeoutException("Symbol ping timed out"));
        }, result), timeout);
        try {
            write(current, SFMSymbolServerProtocol.ping(nonce));
        } catch (IOException failure) {
            failSession(current, new WorkerUnavailableException("Unable to write symbol ping", failure),
                    FailureKind.TRANSPORT);
        }
    }

    private void completePong(SFMSymbolServerProtocol.PongFrame frame) {
        PendingPing ping = pings.remove(frame.nonce());
        updateControlCount();
        if (ping == null) {
            lateResponses.incrementAndGet();
            return;
        }
        cancelTimer(ping.timeout);
        ping.result.complete(frame.nonce());
    }

    private void beginWorkspaceUpdate(long generation, CompletableFuture<Long> result, Duration timeout) {
        SessionState current = requireReady(result);
        if (current == null) return;
        if (generation < current.workspaceGeneration) {
            result.completeExceptionally(new IllegalArgumentException("Workspace generation cannot move backwards"));
            return;
        }
        if (controlCount.get() >= configuration.maximumPendingControls()) {
            result.completeExceptionally(new CapacityException("Symbol-worker control pending limit is reached"));
            return;
        }
        if (workspaceUpdates.putIfAbsent(generation, result) != null) {
            result.completeExceptionally(new IllegalArgumentException("Workspace generation update is already pending"));
            return;
        }
        updateControlCount();
        ScheduledFuture<?> timeoutTask = schedule(() -> executeState(() -> {
            CompletableFuture<Long> removed = workspaceUpdates.remove(generation);
            updateControlCount();
            if (removed != null) removed.completeExceptionally(new TimeoutException(
                    "Workspace generation update timed out"));
        }, result), timeout);
        result.whenComplete((ignored, failure) -> timeoutTask.cancel(false));
        long previousGeneration = current.workspaceGeneration;
        if (!advanceWorkspace(current, generation)) return;
        try {
            // advanceWorkspace already wrote the update when it advanced. An
            // equal-generation request still needs an acknowledgement.
            if (generation == previousGeneration) {
                write(current, SFMSymbolServerProtocol.workspaceGeneration(generation));
            }
        } catch (IOException failure) {
            failSession(current, new WorkerUnavailableException(
                    "Unable to write workspace generation update", failure), FailureKind.TRANSPORT);
        }
    }

    private void completeWorkspaceUpdate(
            SessionState observed,
            SFMSymbolServerProtocol.WorkspaceGenerationFrame frame
    ) {
        if (frame.workspaceGeneration() < observed.workspaceGeneration) {
            failSession(observed, new ProtocolMismatchException("Workspace acknowledgement moved backwards"),
                    FailureKind.PROTOCOL);
            return;
        }
        if (observed.requestedWorkspaceGeneration >= 0
                && frame.workspaceGeneration() != observed.requestedWorkspaceGeneration) {
            failSession(observed, new ProtocolMismatchException("Workspace acknowledgement generation mismatch"),
                    FailureKind.PROTOCOL);
            return;
        }
        if (!frame.workspace().workspace().branch().equals(configuration.branch())) {
            failSession(observed, new ProtocolMismatchException("Workspace acknowledgement branch mismatch"),
                    FailureKind.PROTOCOL);
            return;
        }
        observed.workspaceGeneration = frame.workspaceGeneration();
        observed.requestedWorkspaceGeneration = -1;
        hello = hello.map(previous -> new SFMSymbolServerProtocol.ServerHello(
                previous.protocolSchema(),
                previous.serverName(),
                previous.serverVersion(),
                previous.capabilities(),
                previous.maximumFrameBytes(),
                previous.maximumPendingDefinitions(),
                frame.workspace(),
                previous.rawHelloJson()
        ));
        CompletableFuture<Long> result = workspaceUpdates.remove(frame.workspaceGeneration());
        List<Map.Entry<Long, CompletableFuture<Long>>> satisfied = workspaceUpdates.entrySet().stream()
                .filter(entry -> entry.getKey() < frame.workspaceGeneration())
                .map(Map.Entry::copyOf)
                .toList();
        satisfied.forEach(entry -> workspaceUpdates.remove(entry.getKey(), entry.getValue()));
        updateControlCount();
        if (result != null) result.complete(frame.workspaceGeneration());
        satisfied.forEach(entry -> entry.getValue().complete(frame.workspaceGeneration()));
        if (!flushWorkspaceUpdates(observed)) flushPending();
    }

    /** Serialize distinct explicit generation updates instead of leaving later controls unsent. */
    private boolean flushWorkspaceUpdates(SessionState current) {
        if (current.requestedWorkspaceGeneration >= 0) return true;
        Optional<Long> next = workspaceUpdates.keySet().stream()
                .filter(generation -> generation > current.workspaceGeneration)
                .min(Long::compareTo);
        if (next.isEmpty()) return false;
        return advanceWorkspace(current, next.orElseThrow());
    }

    private void handleRemoteError(
            SessionState observed,
            SFMSymbolServerProtocol.ErrorFrame error
    ) {
        if (error.fatal()) {
            failSession(observed, new ProtocolMismatchException(
                    "Symbol worker reported fatal protocol error [" + boundedCode(error.code()) + "]"
            ), FailureKind.PROTOCOL);
            return;
        }
        if (error.requestId().isPresent()) {
            PendingDefinition value = pending.get(error.requestId().orElseThrow());
            if (value != null && error.requestGeneration().orElse(value.request.requestGeneration())
                    == value.request.requestGeneration()) {
                removePending(value);
                remoteFailures.incrementAndGet();
                value.result.completeExceptionally(new RemoteDefinitionException(
                        error.code(), error.message(), false
                ));
                return;
            }
        }
        lateResponses.incrementAndGet();
    }

    private void cancelPending(
            long requestId,
            long requestGeneration,
            Throwable reason,
            CancellationKind kind
    ) {
        PendingDefinition value = pending.get(requestId);
        if (value == null || value.request.requestGeneration() != requestGeneration) return;
        removePending(value);
        if (kind == CancellationKind.TIMEOUT) timedOut.incrementAndGet();
        else cancelled.incrementAndGet();
        SessionState current = session;
        if (value.sentEpoch != 0 && current != null && current.epoch == value.sentEpoch
                && current.phase == Lifecycle.READY) {
            try {
                write(current, SFMSymbolServerProtocol.cancel(value.request, kind.wireReason));
            } catch (IOException failure) {
                failSession(current, new WorkerUnavailableException(
                        "Unable to write definition cancellation", failure), FailureKind.TRANSPORT);
            }
        }
        value.result.completeExceptionally(reason);
        flushPending();
    }

    private SessionState requireReady(CompletableFuture<?> result) {
        if (closing.get() || session == null || session.phase != Lifecycle.READY) {
            result.completeExceptionally(new WorkerUnavailableException("Symbol worker is not ready"));
            return null;
        }
        return session;
    }

    private void handshakeTimedOut(SessionState observed) {
        if (session == observed && observed.phase == Lifecycle.STARTING) {
            failSession(observed, new WorkerUnavailableException("Symbol-worker handshake timed out"),
                    FailureKind.TRANSPORT);
        }
    }

    private void failSession(SessionState observed, Throwable failure, FailureKind kind) {
        if (observed == null || session != observed) return;
        if (kind == FailureKind.PROTOCOL) protocolFailures.incrementAndGet();
        else transportFailures.incrementAndGet();
        privacyLog(kind == FailureKind.PROTOCOL
                ? "SFM_SYMBOL_WORKER_PROTOCOL_FAILED"
                : "SFM_SYMBOL_WORKER_TRANSPORT_FAILED", failure);
        session = null;
        hello = Optional.empty();
        lifecycle = closing.get() ? Lifecycle.STOPPING : Lifecycle.STOPPED;
        cancelTimer(observed.handshakeTimeout);
        failAllPending(failure);
        failControls(failure);
        terminate(observed, configuration.shutdownTimeout());
        if (closing.get()) finishClosed();
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
            privacyLog("SFM_SYMBOL_WORKER_REAP_FAILED", failure);
        }
    }

    private void failAllPending(Throwable failure) {
        List<PendingDefinition> values = List.copyOf(pending.values());
        pending.clear();
        pendingCount.set(0);
        for (PendingDefinition value : values) {
            cancelTimer(value.timeout);
            value.result.completeExceptionally(failure);
        }
    }

    private void failControls(Throwable failure) {
        List<PendingPing> pingValues = List.copyOf(pings.values());
        pings.clear();
        for (PendingPing ping : pingValues) {
            cancelTimer(ping.timeout);
            ping.result.completeExceptionally(failure);
        }
        List<CompletableFuture<Long>> updates = List.copyOf(workspaceUpdates.values());
        workspaceUpdates.clear();
        updates.forEach(value -> value.completeExceptionally(failure));
        List<CompletableFuture<SFMSymbolServerProtocol.ServerHello>> ready = List.copyOf(readiness);
        readiness.clear();
        ready.forEach(value -> value.completeExceptionally(failure));
        controlCount.set(0);
    }

    private void updateControlCount() {
        controlCount.set(pings.size() + workspaceUpdates.size() + readiness.size());
    }

    private void removeAndFail(PendingDefinition value, Throwable failure) {
        removePending(value);
        value.result.completeExceptionally(failure);
    }

    private void removePending(PendingDefinition value) {
        if (!pending.remove(value.request.requestId(), value)) return;
        cancelTimer(value.timeout);
        pendingCount.set(pending.size());
    }

    private void write(SessionState destination, String json) throws IOException {
        if (session != destination) throw new IOException("Symbol worker session was replaced");
        SFMDefinitionWorkerFrameCodec.write(
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
        CancellationException stopped = new CancellationException("Symbol supervisor is closing");
        failAllPending(stopped);
        failControls(stopped);
        SessionState current = session;
        if (current == null) {
            finishClosed();
            return;
        }
        current.phase = Lifecycle.STOPPING;
        try {
            write(current, SFMSymbolServerProtocol.shutdown("minecraft client shutdown"));
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
        SFM.LOGGER.info("SFM_SYMBOL_WORKER_CLOSED launches={} ready={} completed={} failures={}",
                launchAttempts.get(), sessionsReady.get(), completed.get(),
                launchFailures.get() + protocolFailures.get() + transportFailures.get());
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
            if (affected != null) affected.completeExceptionally(
                    new WorkerUnavailableException("Symbol supervisor executor is unavailable", failure));
        }
    }

    private ScheduledFuture<?> schedule(Runnable action, Duration delay) {
        return scheduler.schedule(action, delay.toNanos(), TimeUnit.NANOSECONDS);
    }

    private long incrementEpoch() {
        if (nextSessionEpoch == Long.MAX_VALUE) throw new IllegalStateException("Session epoch exhausted");
        return ++nextSessionEpoch;
    }

    private long incrementPingNonce() {
        if (nextPingNonce == Long.MAX_VALUE) nextPingNonce = 0;
        return ++nextPingNonce;
    }

    private static boolean matches(
            SFMDefinitionRequest request,
            long requestId,
            long requestGeneration,
            long workspaceGeneration
    ) {
        return request.requestId() == requestId
                && request.requestGeneration() == requestGeneration
                && request.workspace().workspaceGeneration() == workspaceGeneration;
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

    private static String boundedMessage(String value) {
        if (value == null) return "remote failure";
        String cleaned = value.replaceAll("[\\r\\n\\p{Cntrl}]", " ");
        return cleaned.length() <= 256 ? cleaned : cleaned.substring(0, 256);
    }

    private static void privacyLog(String event, Throwable failure) {
        SFM.LOGGER.warn("{} failure_type={}", event, failure.getClass().getSimpleName());
    }

    private enum FailureKind { PROTOCOL, TRANSPORT }

    private enum CancellationKind {
        EXPLICIT("client-cancelled"),
        TIMEOUT("client-timeout");

        private final String wireReason;
        CancellationKind(String wireReason) { this.wireReason = wireReason; }
    }

    private static final class PendingDefinition {
        private final SFMDefinitionRequest request;
        private final CompletableFuture<SFMDefinitionResult> result;
        private long sentEpoch;
        private ScheduledFuture<?> timeout;

        private PendingDefinition(
                SFMDefinitionRequest request,
                CompletableFuture<SFMDefinitionResult> result
        ) {
            this.request = request;
            this.result = result;
        }
    }

    private static final class PendingPing {
        private final long nonce;
        private final CompletableFuture<Long> result;
        private ScheduledFuture<?> timeout;

        private PendingPing(long nonce, CompletableFuture<Long> result) {
            this.nonce = nonce;
            this.result = result;
        }
    }

    private static final class SessionState {
        private final long epoch;
        private final WorkerSession worker;
        private volatile int negotiatedMaximumFrameBytes;
        private volatile Lifecycle phase = Lifecycle.STARTING;
        private int maximumPendingDefinitions;
        private long workspaceGeneration = -1;
        private long requestedWorkspaceGeneration = -1;
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

        @Override public long pid() { return process.pid(); }
        @Override public InputStream stdout() { return process.getInputStream(); }
        @Override public OutputStream stdin() { return process.getOutputStream(); }
        @Override public boolean alive() { return process.isAlive(); }

        @Override
        public void terminateTree(Duration grace) throws InterruptedException {
            List<ProcessHandle> descendants = root.descendants().toList();
            closeQuietly(process.getOutputStream());
            destroy(descendants, false);
            if (process.isAlive()) process.destroy();
            long graceMillis = Math.max(0, grace.toMillis());
            if (process.isAlive() && graceMillis > 0) process.waitFor(graceMillis, TimeUnit.MILLISECONDS);
            List<ProcessHandle> survivors = descendants.stream().filter(ProcessHandle::isAlive).toList();
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
