package ca.teamdman.sfm.client.terminal;

import ca.teamdman.sfm.SFM;
import org.facet.vox.ConnectionOptions;
import org.facet.vox.ConnectionState;
import org.facet.vox.CallOptions;
import org.facet.vox.LaneOptions;
import org.facet.vox.ServiceLane;
import org.facet.vox.VoxConnection;
import org.facet.vox.VoxChannels;
import org.facet.vox.VoxRx;
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
import org.facet.vox.generated.TerminalFrameEvent;
import org.facet.vox.generated.TerminalFrameKind;
import org.facet.vox.generated.TerminalInputResult;
import org.facet.vox.generated.TerminalKeyInput;
import org.facet.vox.generated.TerminalMouseInput;
import org.facet.vox.generated.TerminalOperationResult;
import org.facet.vox.generated.TerminalPublicationTelemetry;
import org.facet.vox.generated.TerminalResizeRequest;
import org.facet.vox.generated.TerminalResizeResult;
import org.facet.vox.generated.TerminalServiceDescriptor;
import org.facet.vox.generated.TerminalSnapshot;
import org.facet.vox.generated.TerminalSnapshotRequest;
import org.facet.vox.generated.TerminalSubscribeRequest;
import org.facet.vox.generated.TerminalSurfaceMetrics;
import org.facet.vox.generated.TerminalState;
import org.facet.vox.generated.TerminalTextInput;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

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
    private static final Duration SUBSCRIPTION_IDLE_TIMEOUT = Duration.ofDays(3650);

    private final InetSocketAddress endpoint;
    private final ConnectionOptions connectionOptions;
    private final Duration callTimeout;
    private final ExecutorService driver;
    private final ExecutorService connectionDriver;
    private final ExecutorService subscriptionReceiver;
    private final ExecutorService transportCleanup;
    private final SFMVoxTerminalTelemetry telemetry = new SFMVoxTerminalTelemetry();
    private final SFMVoxTerminalFrameInbox frameInbox =
            new SFMVoxTerminalFrameInbox(MAX_FRAME_BYTES);
    private final Object lock = new Object();
    private VoxConnection connection;
    private ServiceLane lane;
    private TerminalClient client;
    private String sessionId;
    private String failure;
    private TerminalSnapshot latestSnapshot;
    private TerminalContentResult latestContent;
    private VoxRx<TerminalFrameEvent> frameReceiver;
    private CompletableFuture<VoxResult<TerminalOperationResult, TerminalError>> subscriptionCall;
    private SFMVoxTerminalFrameInbox.Subscription subscription;
    private final AtomicLong clientSequence = new AtomicLong();
    private int requestedWidth = REQUEST_WIDTH;
    private int requestedHeight = REQUEST_HEIGHT;
    private int requestedPixelWidth;
    private int requestedPixelHeight;
    private long resizeVersion;
    private boolean resizeTaskQueued;
    private MouseOperation pendingMouseMotion;
    private boolean mouseMotionTaskQueued;
    private boolean connectionInFlight;
    private long transportGeneration;
    private volatile Thread driverThread;
    private long nextConnectionAttemptNanos;
    private boolean closed;

    private record Transport(TerminalClient client, String sessionId, long generation) {}

    private record DetachedSubscription(
            VoxRx<TerminalFrameEvent> receiver,
            CompletableFuture<VoxResult<TerminalOperationResult, TerminalError>> call) {}

    private record DetachedTransport(
            TerminalClient client,
            String sessionId,
            ServiceLane lane,
            VoxConnection connection,
            boolean requestDisconnect) {}

    private record ResizeOperation(
            int width,
            int height,
            int panelWidth,
            int panelHeight,
            long version) {}

    private record MouseOperation(
            int x,
            int y,
            int buttons,
            int button,
            boolean pressed,
            boolean motion,
            int wheelX,
            int wheelY) {}

    @FunctionalInterface
    private interface RemoteOperation {
        void run(Transport transport) throws Exception;
    }

    public SFMVoxTerminalService(InetSocketAddress endpoint) {
        this(endpoint, ConnectionOptions.builder()
                .handshakeTimeout(Duration.ofMillis(500))
                .idleTimeout(DEFAULT_TIMEOUT)
                .closeTimeout(Duration.ofSeconds(1))
                .initialChannelCredit(1)
                .build(), DEFAULT_TIMEOUT);
    }

    /** Retained as a source-compatible constructor; the fallback is deliberately ignored. */
    public SFMVoxTerminalService(InetSocketAddress endpoint, SFMTerminalService fallbackService) {
        this(endpoint, ConnectionOptions.builder()
                .handshakeTimeout(Duration.ofMillis(500))
                .idleTimeout(DEFAULT_TIMEOUT)
                .closeTimeout(Duration.ofSeconds(1))
                .initialChannelCredit(1)
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
            Thread thread = new Thread(() -> {
                driverThread = Thread.currentThread();
                runnable.run();
            }, "sfm-vox-terminal-request");
            thread.setDaemon(true);
            return thread;
        });
        this.connectionDriver = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "sfm-vox-terminal-connection");
            thread.setDaemon(true);
            return thread;
        });
        this.subscriptionReceiver = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "sfm-vox-terminal-frame-receiver");
            thread.setDaemon(true);
            return thread;
        });
        this.transportCleanup = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "sfm-vox-terminal-cleanup");
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
        return new Session();
    }

    @Override
    public void requestConnect() {
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

    /** Returns bounded machine-readable subscription and explicit-snapshot evidence. */
    public SFMVoxTerminalTelemetry.Snapshot telemetry() {
        return telemetry.snapshot();
    }

    /** Validates and serializes the live bounded-push invariants for puppet evidence. */
    public String assertPushEvidenceForAutomation(boolean reconnectExpected) {
        SFMVoxTerminalTelemetry.Snapshot evidence = telemetry.snapshot();
        SFMVoxTerminalFrameInbox.Snapshot inbox = frameInbox.snapshot();
        SFMVoxTerminalTelemetry.ProducerMetadata producer = evidence.latestProducer()
                .orElseThrow(() -> new IllegalStateException("No Rust push producer telemetry was received"));
        SFMVoxTerminalTelemetry.SubscriptionEventMetadata event = evidence.latestSubscriptionEvent()
                .orElseThrow(() -> new IllegalStateException("No accepted Rust push event was received"));
        if (evidence.pollsStarted() != 0
                || evidence.pollsCompleted() != 0
                || evidence.pollsFailed() != 0
                || evidence.pollsSkippedInFlight() != 0
                || evidence.pollsSkippedUnavailable() != 0) {
            throw new IllegalStateException("Steady terminal presentation used the retired polling path");
        }
        if (evidence.snapshotCalls() != 0) {
            throw new IllegalStateException(
                    "Steady terminal presentation made " + evidence.snapshotCalls() + " snapshot calls");
        }
        long minimumSubscriptions = reconnectExpected ? 2 : 1;
        if (evidence.subscriptionsStarted() < minimumSubscriptions) {
            throw new IllegalStateException("Expected at least " + minimumSubscriptions
                    + " terminal subscriptions, observed " + evidence.subscriptionsStarted());
        }
        if (reconnectExpected && evidence.subscriptionChannelsClosed() < 1) {
            throw new IllegalStateException("The server restart did not close the original subscription");
        }
        if (evidence.subscriptionEventsReceived() < 1
                || evidence.subscriptionEventsAccepted() < 1
                || evidence.acceptedFrames() < 1
                || producer.rendersCompleted() < 1) {
            throw new IllegalStateException("The pushed frame path did not deliver accepted live evidence");
        }
        if (producer.pendingDepth() < 0 || producer.pendingDepth() > 1
                || producer.pendingDepthMax() < 0 || producer.pendingDepthMax() > 1
                || producer.rendersStarted() < producer.rendersCompleted()
                || producer.rendersStarted() - producer.rendersCompleted() > 1) {
            throw new IllegalStateException("Rust producer violated the one-in-flight plus one-pending bound");
        }
        if (inbox.state() != SFMVoxTerminalFrameInbox.State.LIVE
                || inbox.lastTerminalSequence() < 0
                || inbox.lastFrameSequence() < 1
                || inbox.framesDelivered() < 1) {
            throw new IllegalStateException("Java pushed-frame inbox is not live and ordered");
        }
        String result = String.join("\n",
                "transport=vox.txrx",
                "steady_snapshot_calls=" + evidence.snapshotCalls(),
                "steady_polls_started=" + evidence.pollsStarted(),
                "subscriptions_started=" + evidence.subscriptionsStarted(),
                "subscription_channels_closed=" + evidence.subscriptionChannelsClosed(),
                "subscription_events_received=" + evidence.subscriptionEventsReceived(),
                "subscription_events_accepted=" + evidence.subscriptionEventsAccepted(),
                "subscription_events_rejected=" + evidence.subscriptionEventsRejected(),
                "java_frames_delivered=" + inbox.framesDelivered(),
                "java_frames_superseded=" + inbox.eventsSuperseded(),
                "producer_mutations=" + producer.mutations(),
                "producer_renders_started=" + producer.rendersStarted(),
                "producer_renders_completed=" + producer.rendersCompleted(),
                "producer_pre_render_coalesced=" + producer.preRenderCoalesced(),
                "producer_credit_blocked_sends=" + producer.creditBlockedSends(),
                "producer_frames_pushed=" + producer.framesPushed(),
                "producer_pending_depth=" + producer.pendingDepth(),
                "producer_pending_depth_max=" + producer.pendingDepthMax(),
                "producer_mutation_to_send_us=" + producer.mutationToSendUs(),
                "producer_credit_wait_us=" + producer.creditWaitUs(),
                "latest_connection_epoch=" + event.connectionEpoch(),
                "latest_session_epoch=" + event.sessionEpoch(),
                "latest_terminal_sequence=" + event.terminalSequence(),
                "latest_frame_sequence=" + event.frameSequence());
        SFM.LOGGER.info("SFM_VOX_TERMINAL_PUSH_EVIDENCE {}", result.replace('\n', ' '));
        return result + "\n";
    }

    @Override
    public Optional<SFMTerminalFrame> latestFrame() {
        return frameInbox.takeLatest();
    }

    @Override
    public boolean canPresentRetainedFrame() {
        return isConnected() && frameInbox.isLive();
    }

    private SFMTerminalFrameMetadata frameMetadata(TerminalSnapshot snapshot) {
        return frameMetadata(snapshot, "rust.cpu.fontdue", "vox", snapshot.correlationId());
    }

    private SFMTerminalFrameMetadata frameMetadata(
            TerminalSnapshot snapshot,
            String backendId,
            String transportId,
            String correlationId) {
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
                backendId,
                transportId,
                timing.ptyDrainUs(),
                0L,
                timing.terminalSnapshotUs(),
                timing.fontLoadUs(),
                timing.rasterUs(),
                0L,
                timing.pngEncodeUs(),
                timing.totalUs(),
                correlationId);
    }

    private static SFMVoxTerminalTelemetry.NativeFrameMetadata nativeFrameMetadata(
            TerminalSnapshot snapshot) {
        return nativeFrameMetadata(snapshot, "rust.cpu.fontdue", "vox", snapshot.correlationId());
    }

    private static SFMVoxTerminalTelemetry.NativeFrameMetadata nativeFrameMetadata(
            TerminalSnapshot snapshot,
            String backendId,
            String transportId,
            String correlationId) {
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
                backendId,
                transportId,
                correlationId,
                timing.totalUs(),
                snapshot.payload().length);
    }

    private static SFMVoxTerminalTelemetry.ProducerMetadata producerMetadata(
            TerminalPublicationTelemetry producer) {
        return new SFMVoxTerminalTelemetry.ProducerMetadata(
                producer.mutations(),
                producer.rendersStarted(),
                producer.rendersCompleted(),
                producer.preRenderCoalesced(),
                producer.creditBlockedSends(),
                producer.framesPushed(),
                producer.pendingDepth(),
                producer.pendingDepthMax(),
                producer.mutationToSendUs(),
                producer.creditWaitUs());
    }

    /** Returns a defensive copy of the latest frame payload for a renderer. */
    public Optional<byte[]> latestSnapshotPayload() {
        synchronized (lock) {
            return latestSnapshot == null
                    ? Optional.empty()
                    : Optional.of(Arrays.copyOf(latestSnapshot.payload(), latestSnapshot.payload().length));
        }
    }

    /** Returns the latest Rust-owned visible text witness from an explicit automation read. */
    public Optional<String> latestContentText() {
        synchronized (lock) {
            return latestContent == null ? Optional.empty() : Optional.of(latestContent.text());
        }
    }

    /** Reads a bounded Rust-owned visible text witness for deterministic automation assertions. */
    public String contentForAutomation() {
        try {
            return runOnDriverBlocking(() -> {
                ensureConnected();
                Transport transport = requireCurrentTransport();
                try {
                    TerminalContentResult content = readReadyContent(
                            transport.client(), transport.sessionId());
                    synchronized (lock) {
                        if (isCurrentTransportLocked(transport)) latestContent = content;
                    }
                    return content.text();
                } catch (Exception error) {
                    failTransportIfCurrent(transport, "Vox terminal content unavailable: ", error);
                    throw error;
                }
            });
        } catch (Exception error) {
            throw new IllegalStateException(
                    "Vox terminal content unavailable: " + describe(error), error);
        }
    }

    /**
     * Performs one explicit unary capture for diagnostics or recovery tools.
     * Live presentation never calls this method; it consumes the subscription.
     */
    public Optional<TerminalSnapshot> snapshotForDiagnostics() {
        try {
            return Optional.of(runOnDriverBlocking(() -> {
                ensureConnected();
                Transport transport = requireCurrentTransport();
                try {
                    return requestSnapshot(
                            transport.client(), transport.sessionId(), 0, "diagnostic-snapshot");
                } catch (Exception error) {
                    failTransportIfCurrent(
                            transport, "Vox terminal diagnostic snapshot unavailable: ", error);
                    throw error;
                }
            }));
        } catch (Exception error) {
            throw new IllegalStateException("Vox terminal diagnostic snapshot unavailable: "
                    + describe(error), error);
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
        subscriptionReceiver.shutdownNow();
        transportCleanup.shutdown();
    }

    private void startFrameSubscription(TerminalClient currentClient, String currentSession) {
        VoxChannels.Pair<TerminalFrameEvent> channel = VoxChannels.channel(TerminalFrameEvent.ADAPTER);
        SFMVoxTerminalFrameInbox.Subscription currentSubscription;
        long requestSequence = nextSequence();
        synchronized (lock) {
            if (closed || client != currentClient || !Objects.equals(sessionId, currentSession)) {
                channel.rx().close();
                return;
            }
            closeFrameSubscriptionLocked();
            currentSubscription = frameInbox.begin(currentSession);
            subscription = currentSubscription;
            frameReceiver = channel.rx();
            telemetry.recordSubscriptionStarted();
        }

        // Facet currently requires a positive per-call idle timeout and has no
        // explicit never-time-out value. Keep normal terminal idleness from
        // cycling a healthy request-scoped subscription; transport close and
        // Rx reset remain the actual lifetime controls.
        CompletableFuture<VoxResult<TerminalOperationResult, TerminalError>> currentCall =
                currentClient.subscribeFrames(
                        new TerminalSubscribeRequest(
                                currentSession,
                                0,
                                MAX_FRAME_BYTES,
                                requestSequence,
                                correlationId("subscribe", requestSequence)),
                        channel.tx(),
                        CallOptions.withIdleTimeout(SUBSCRIPTION_IDLE_TIMEOUT));
        synchronized (lock) {
            if (!frameInbox.isCurrent(currentSubscription)) {
                channel.rx().close();
                currentCall.cancel(true);
                return;
            }
            subscriptionCall = currentCall;
        }
        currentCall.whenComplete((result, error) ->
                subscriptionCompleted(currentSubscription, currentClient, currentSession, result, error));
        try {
            subscriptionReceiver.execute(() ->
                    receiveFrames(currentSubscription, currentClient, currentSession, channel.rx()));
        } catch (RejectedExecutionException error) {
            channel.rx().close();
            throw error;
        }
    }

    private void receiveFrames(
            SFMVoxTerminalFrameInbox.Subscription currentSubscription,
            TerminalClient currentClient,
            String currentSession,
            VoxRx<TerminalFrameEvent> receiver) {
        Throwable failureCause = null;
        try {
            while (!Thread.currentThread().isInterrupted()) {
                TerminalFrameEvent event = receiver.receive();
                if (event == null) break;
                acceptSubscriptionEvent(currentSubscription, event);
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            failureCause = error;
        } catch (Exception error) {
            failureCause = error;
        } finally {
            telemetry.recordSubscriptionChannelClosed();
            subscriptionReceiverStopped(
                    currentSubscription, currentClient, currentSession, failureCause);
        }
    }

    private void acceptSubscriptionEvent(
            SFMVoxTerminalFrameInbox.Subscription currentSubscription,
            TerminalFrameEvent event) {
        telemetry.recordSubscriptionEventReceived();
        TerminalSnapshot snapshot = event.frame();
        SFMTerminalFrame frame = new SFMTerminalFrame(
                event.frameSequence(),
                snapshot.kind() == TerminalFrameKind.FULL,
                snapshot.encoding() == TerminalFrameEncoding.PNG,
                snapshot.payload(),
                frameMetadata(snapshot, event.backendId(), event.transportId(), event.correlationId()),
                streamIdentity(event.connectionEpoch(), event.sessionEpoch()));
        SFMVoxTerminalFrameInbox.OfferResult result = frameInbox.offer(
                currentSubscription,
                new SFMVoxTerminalFrameInbox.Event(
                        event.sessionId(),
                        event.connectionEpoch(),
                        event.sessionEpoch(),
                        event.terminalSequence(),
                        event.frameSequence(),
                        event.fullResync(),
                        event.maxFrameBytes(),
                        event.backendId(),
                        event.transportId(),
                        event.correlationId(),
                        snapshot.sessionId(),
                        snapshot.sequence(),
                        snapshot.complete(),
                        frame));
        if (!result.accepted()) {
            telemetry.recordSubscriptionEventRejected();
            telemetry.recordDropped();
            if (result == SFMVoxTerminalFrameInbox.OfferResult.REJECTED_EPOCH
                    || result == SFMVoxTerminalFrameInbox.OfferResult.REJECTED_SEQUENCE
                    || result == SFMVoxTerminalFrameInbox.OfferResult.REJECTED_STALE_SUBSCRIPTION) {
                telemetry.recordStale();
            }
            return;
        }

        synchronized (lock) {
            if (!frameInbox.isCurrent(currentSubscription)) return;
            latestSnapshot = snapshot;
            failure = null;
            telemetry.recordSubscriptionEventAccepted(result.superseded());
            telemetry.recordAccepted(result.superseded());
            telemetry.recordObserved(nativeFrameMetadata(
                    snapshot, event.backendId(), event.transportId(), event.correlationId()));
            telemetry.recordProducer(producerMetadata(event.publication()));
            telemetry.recordSubscriptionEvent(new SFMVoxTerminalTelemetry.SubscriptionEventMetadata(
                    event.sessionId(),
                    event.connectionEpoch(),
                    event.sessionEpoch(),
                    event.terminalSequence(),
                    event.frameSequence(),
                    event.fullResync(),
                    event.maxFrameBytes(),
                    event.correlationId()));
        }
    }

    private void subscriptionCompleted(
            SFMVoxTerminalFrameInbox.Subscription currentSubscription,
            TerminalClient currentClient,
            String currentSession,
            VoxResult<TerminalOperationResult, TerminalError> result,
            Throwable error) {
        synchronized (lock) {
            if (closed
                    || client != currentClient
                    || !Objects.equals(sessionId, currentSession)
                    || !frameInbox.isCurrent(currentSubscription)) {
                return;
            }
            if (error == null && result != null && result.isSuccess()) {
                telemetry.recordSubscriptionCompleted();
                SFM.LOGGER.info(
                        "SFM_VOX_TERMINAL_SUBSCRIPTION_COMPLETED session={} result={}",
                        currentSession,
                        result.success().state());
                return;
            }
            telemetry.recordSubscriptionFailed();
            failure = "Vox terminal frame subscription failed: "
                    + (error == null ? describeSubscriptionResult(result) : describe(error));
            SFM.LOGGER.warn(
                    "SFM_VOX_TERMINAL_SUBSCRIPTION_FAILED session={} failure={}",
                    currentSession,
                    failure,
                    error);
            nextConnectionAttemptNanos = System.nanoTime() + Duration.ofSeconds(2).toNanos();
            closeTransportLocked();
        }
    }

    private void subscriptionReceiverStopped(
            SFMVoxTerminalFrameInbox.Subscription currentSubscription,
            TerminalClient currentClient,
            String currentSession,
            Throwable error) {
        synchronized (lock) {
            if (closed
                    || client != currentClient
                    || !Objects.equals(sessionId, currentSession)
                    || !frameInbox.isCurrent(currentSubscription)) {
                return;
            }
            failure = error == null
                    ? "Vox terminal frame subscription closed"
                    : "Vox terminal frame subscription unavailable: " + describe(error);
            if (error == null) {
                SFM.LOGGER.warn(
                        "SFM_VOX_TERMINAL_SUBSCRIPTION_RECEIVER_CLOSED session={} reason=graceful_eof",
                        currentSession);
            } else {
                SFM.LOGGER.warn(
                        "SFM_VOX_TERMINAL_SUBSCRIPTION_RECEIVER_FAILED session={} failure={}",
                        currentSession,
                        failure,
                        error);
            }
            nextConnectionAttemptNanos = System.nanoTime() + Duration.ofSeconds(2).toNanos();
            closeTransportLocked();
        }
    }

    private void ensureConnected() throws Exception {
        long attemptGeneration;
        synchronized (lock) {
            if (closed) throw new IllegalStateException("Vox terminal service is closed");
            if (sessionId != null) return;
            failure = null;
            attemptGeneration = transportGeneration;
        }

        VoxConnection newConnection = null;
        ServiceLane newLane = null;
        TerminalClient newClient = null;
        String newSessionId = null;
        try {
            newConnection = VoxConnection.connect(endpoint, freshConnectionOptions());
            // The request executor may be synchronously waiting for this
            // connection to open. Drive the transport on its own executor so
            // requestConnect() cannot deadlock before the first lane opens.
            CompletableFuture<Void> closedFuture = newConnection.start(connectionDriver);
            awaitConnectionOpen(newConnection, closedFuture);
            newLane = newConnection.openLane(TerminalServiceDescriptor.INSTANCE, LaneOptions.defaults());
            await(newLane.opened(), "opening terminal lane");
            newClient = new TerminalClient(newLane);
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
            newSessionId = connected.sessionId();
            synchronized (lock) {
                if (closed || transportGeneration != attemptGeneration || sessionId != null) {
                    throw new IllegalStateException("Vox terminal connection attempt was superseded");
                }
                connection = newConnection;
                lane = newLane;
                client = newClient;
                sessionId = newSessionId;
            }
            startFrameSubscription(newClient, newSessionId);
        } catch (Exception error) {
            if (newLane != null) newLane.close();
            if (newConnection != null) newConnection.close();
            synchronized (lock) {
                if (transportGeneration == attemptGeneration
                        && (client == null || client == newClient)
                        && (sessionId == null || Objects.equals(sessionId, newSessionId))) {
                    failure = "Vox terminal unavailable: " + describe(error);
                    nextConnectionAttemptNanos = System.nanoTime() + Duration.ofSeconds(2).toNanos();
                    if (client == newClient) closeTransportLocked();
                }
            }
            throw new IllegalStateException("Vox terminal unavailable: " + describe(error), error);
        }
    }

    /** Sends a bounded logical terminal resize; the next frame remains Rust-owned. */
    @Override
    public boolean resize(int width, int height) {
        int panelWidth;
        int panelHeight;
        synchronized (lock) {
            panelWidth = requestedPixelWidth;
            panelHeight = requestedPixelHeight;
        }
        return resize(width, height, panelWidth, panelHeight);
    }

    @Override
    public boolean resize(int width, int height, int panelWidth, int panelHeight) {
        int boundedWidth = Math.max(1, Math.min(240, width));
        int boundedHeight = Math.max(1, Math.min(120, height));
        int boundedPanelWidth = Math.max(0, Math.min(4096, panelWidth));
        int boundedPanelHeight = Math.max(0, Math.min(4096, panelHeight));
        boolean schedule;
        synchronized (lock) {
            if (closed) return false;
            requestedWidth = boundedWidth;
            requestedHeight = boundedHeight;
            requestedPixelWidth = boundedPanelWidth;
            requestedPixelHeight = boundedPanelHeight;
            resizeVersion = incrementGeneration(resizeVersion, "terminal resize version");
            schedule = !resizeTaskQueued;
            if (schedule) resizeTaskQueued = true;
        }
        if (!schedule) return true;
        if (submitDriver(this::runLatestResize)) return true;
        synchronized (lock) {
            resizeTaskQueued = false;
        }
        return false;
    }

    /** Send exact printable bytes; no implicit Enter is added. */
    public boolean sendText(String text) {
        if (text == null || text.isEmpty()) return true;
        return enqueueRemoteOperation("Vox terminal input unavailable: ", transport -> {
            long requestSequence = nextSequence();
            requireSuccess(
                    await(transport.client().sendText(new TerminalTextInput(
                            transport.sessionId(), text, requestSequence,
                            correlationId("text", requestSequence))), "sending terminal text"),
                    "sending terminal text");
        });
    }

    /** Send a physical key transition to Rust; printable text arrives separately. */
    public boolean sendKey(int keyCode, int modifiers, boolean pressed, boolean repeat) {
        return enqueueRemoteOperation("Vox terminal key unavailable: ", transport -> {
            long requestSequence = nextSequence();
            requireSuccess(
                    await(transport.client().sendKey(new TerminalKeyInput(
                            transport.sessionId(), keyCode, modifiers, pressed, repeat, requestSequence,
                            correlationId("key", requestSequence))),
                            "sending terminal key"),
                    "sending terminal key");
        });
    }

    /** Request a server-side interrupt while retaining the Rust terminal session. */
    public boolean cancel() {
        return enqueueRemoteOperation("Vox terminal cancellation unavailable: ", transport -> {
            long requestSequence = nextSequence();
            TerminalOperationResult result = requireSuccess(
                    await(transport.client().cancel(new TerminalCancelRequest(
                            transport.sessionId(),
                            0,
                            "SFM terminal cancellation",
                            requestSequence, correlationId("cancel", requestSequence))),
                            "cancelling terminal operation"),
                    "cancelling terminal operation");
            if (result.state() != TerminalState.READY) {
                throw new IllegalStateException("terminal cancellation did not leave the session ready");
            }
        });
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
        MouseOperation operation = new MouseOperation(
                Math.max(0, Math.min(239, x)),
                Math.max(0, Math.min(119, y)),
                Math.max(0, Math.min(255, buttons)),
                Math.max(0, Math.min(255, button)),
                pressed,
                motion,
                wheelX,
                wheelY);
        if (motion) return enqueueLatestMouseMotion(operation);
        return enqueueRemoteOperation("Vox terminal mouse unavailable: ",
                transport -> performMouse(transport, operation));
    }

    private void performMouse(Transport transport, MouseOperation operation) throws Exception {
        long requestSequence = nextSequence();
        requireSuccess(
                await(transport.client().sendMouse(new TerminalMouseInput(
                        transport.sessionId(),
                        operation.x(),
                        operation.y(),
                        operation.buttons(),
                        operation.button(),
                        operation.pressed(),
                        operation.motion(),
                        operation.wheelX(),
                        operation.wheelY(),
                        requestSequence,
                        correlationId("mouse", requestSequence))), "sending terminal mouse"),
                "sending terminal mouse");
    }

    private boolean enqueueLatestMouseMotion(MouseOperation operation) {
        boolean schedule;
        synchronized (lock) {
            if (closed) return false;
            pendingMouseMotion = operation;
            schedule = !mouseMotionTaskQueued;
            if (schedule) mouseMotionTaskQueued = true;
        }
        if (!schedule) return true;
        if (submitDriver(this::runLatestMouseMotion)) return true;
        synchronized (lock) {
            mouseMotionTaskQueued = false;
            pendingMouseMotion = null;
        }
        return false;
    }

    private void runLatestMouseMotion() {
        MouseOperation operation;
        synchronized (lock) {
            operation = pendingMouseMotion;
            pendingMouseMotion = null;
        }
        if (operation != null) {
            try {
                ensureConnected();
                Transport transport = requireCurrentTransport();
                try {
                    performMouse(transport, operation);
                } catch (Exception error) {
                    failTransportIfCurrent(transport, "Vox terminal mouse unavailable: ", error);
                }
            } catch (Exception ignored) {
                // ensureConnected records the current connection failure.
            }
        }
        boolean repeat;
        synchronized (lock) {
            repeat = !closed && pendingMouseMotion != null;
            if (!repeat) mouseMotionTaskQueued = false;
        }
        if (repeat && !submitDriver(this::runLatestMouseMotion)) {
            synchronized (lock) {
                mouseMotionTaskQueued = false;
                pendingMouseMotion = null;
            }
        }
    }

    private void runLatestResize() {
        ResizeOperation operation;
        synchronized (lock) {
            operation = new ResizeOperation(
                    requestedWidth,
                    requestedHeight,
                    requestedPixelWidth,
                    requestedPixelHeight,
                    resizeVersion);
        }
        try {
            ensureConnected();
            Transport transport = requireCurrentTransport();
            try {
                long requestSequence = nextSequence();
                TerminalResizeResult resized = requireSuccess(
                        await(transport.client().resize(new TerminalResizeRequest(
                                transport.sessionId(),
                                operation.width(),
                                operation.height(),
                                new TerminalSurfaceMetrics(
                                        operation.width(),
                                        operation.height(),
                                        operation.panelWidth(),
                                        operation.panelHeight(),
                                        0,
                                        0,
                                        0),
                                requestSequence,
                                correlationId("resize", requestSequence))),
                                "resizing terminal"),
                        "resizing terminal");
                if (resized.width() != operation.width() || resized.height() != operation.height()) {
                    throw new IllegalStateException("resize response dimensions were "
                            + resized.width() + "x" + resized.height() + " instead of "
                            + operation.width() + "x" + operation.height());
                }
            } catch (Exception error) {
                failTransportIfCurrent(transport, "Vox terminal resize unavailable: ", error);
            }
        } catch (Exception ignored) {
            // ensureConnected records the current connection failure.
        }
        boolean repeat;
        synchronized (lock) {
            repeat = !closed && resizeVersion != operation.version();
            if (!repeat) resizeTaskQueued = false;
        }
        if (repeat && !submitDriver(this::runLatestResize)) {
            synchronized (lock) {
                resizeTaskQueued = false;
            }
        }
    }

    private boolean enqueueRemoteOperation(String failurePrefix, RemoteOperation operation) {
        synchronized (lock) {
            if (closed) return false;
        }
        return submitDriver(() -> {
            try {
                ensureConnected();
                Transport transport = requireCurrentTransport();
                try {
                    operation.run(transport);
                } catch (Exception error) {
                    failTransportIfCurrent(transport, failurePrefix, error);
                }
            } catch (Exception ignored) {
                // ensureConnected records the current connection failure.
            }
        });
    }

    private boolean submitDriver(Runnable operation) {
        try {
            driver.execute(operation);
            return true;
        } catch (RejectedExecutionException error) {
            return false;
        }
    }

    private SFMTerminalResponse execute(String command) {
        String workingDirectory = workingDirectory();
        try {
            return runOnDriverBlocking(() -> {
                ensureConnected();
                Transport transport = requireCurrentTransport();
                try {
                    long requestSequence = nextSequence();
                    TerminalInputResult input = requireSuccess(
                            await(transport.client().sendText(new TerminalTextInput(
                                    transport.sessionId(), command + "\r", requestSequence,
                                    correlationId("execute", requestSequence))), "sending terminal text"),
                            "sending terminal text");
                    return SFMTerminalResponse.ok(List.of(
                            "Vox terminal accepted command",
                            "frame sequence: " + input.frameSequence()), workingDirectory);
                } catch (Exception error) {
                    failTransportIfCurrent(transport, "Vox terminal input unavailable: ", error);
                    throw error;
                }
            });
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

    private long nextSequence() {
        long sequence = clientSequence.incrementAndGet();
        if (sequence <= 0) throw new IllegalStateException("terminal client sequence exhausted");
        return sequence;
    }

    private TerminalSnapshot requestSnapshot(
            TerminalClient currentClient,
            String currentSession,
            long afterSequence,
            String operation) throws Exception {
        telemetry.recordSnapshotCall();
        long requestSequence = nextSequence();
        long waitStarted = System.nanoTime();
        try {
            TerminalSnapshot snapshot = requireSuccess(
                    await(currentClient.snapshot(new TerminalSnapshotRequest(
                            currentSession,
                            Math.max(0, afterSequence),
                            MAX_FRAME_BYTES,
                            requestSequence,
                            correlationId(operation, requestSequence))), operation),
                    operation);
            telemetry.recordVoxWait(System.nanoTime() - waitStarted, false, false);
            validateSnapshot(snapshot, operation);
            telemetry.recordObserved(nativeFrameMetadata(snapshot));
            logSnapshotTiming(snapshot, elapsedMicros(waitStarted), 0, false);
            synchronized (lock) {
                if (!closed && client == currentClient && Objects.equals(sessionId, currentSession)) {
                    latestSnapshot = snapshot;
                }
            }
            return snapshot;
        } catch (Exception error) {
            telemetry.recordVoxWait(
                    System.nanoTime() - waitStarted,
                    true,
                    error instanceof TimeoutException);
            throw error;
        }
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

    private Transport requireCurrentTransport() {
        synchronized (lock) {
            if (client == null || sessionId == null) {
                throw new IllegalStateException("Vox terminal transport is not connected");
            }
            return new Transport(client, sessionId, transportGeneration);
        }
    }

    private boolean isCurrentTransportLocked(Transport transport) {
        return transport != null
                && transport.generation() == transportGeneration
                && transport.client() == client
                && Objects.equals(transport.sessionId(), sessionId);
    }

    private void failTransportIfCurrent(Transport transport, String prefix, Throwable error) {
        synchronized (lock) {
            if (!isCurrentTransportLocked(transport)) return;
            failure = prefix + describe(error);
            SFM.LOGGER.warn(
                    "SFM_VOX_TERMINAL_OPERATION_FAILED session={} failure={}",
                    transport.sessionId(),
                    failure,
                    error);
            nextConnectionAttemptNanos = System.nanoTime() + Duration.ofSeconds(2).toNanos();
            closeTransportLocked();
        }
    }

    private <T> T runOnDriverBlocking(Callable<T> operation) throws Exception {
        if (Thread.currentThread() == driverThread) return operation.call();
        CompletableFuture<T> result = new CompletableFuture<>();
        if (!submitDriver(() -> {
            try {
                result.complete(operation.call());
            } catch (Throwable error) {
                result.completeExceptionally(error);
            }
        })) {
            throw new IllegalStateException("Vox terminal request executor is unavailable");
        }
        try {
            return result.get(Math.max(1L, callTimeout.toMillis() * 2L), TimeUnit.MILLISECONDS);
        } catch (ExecutionException error) {
            Throwable cause = unwrap(error.getCause());
            if (cause instanceof Exception exception) throw exception;
            throw new IllegalStateException("Vox terminal request failed", cause);
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
                .initialChannelCredit(1)
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
        transportGeneration = incrementGeneration(transportGeneration, "terminal transport generation");
        closeFrameSubscriptionLocked();
        DetachedTransport detached = new DetachedTransport(
                client, sessionId, lane, connection, requestDisconnect);
        client = null;
        lane = null;
        connection = null;
        sessionId = null;
        latestSnapshot = null;
        latestContent = null;
        if (detached.client() != null || detached.lane() != null || detached.connection() != null) {
            scheduleTransportCleanup(() -> closeDetachedTransport(detached));
        }
    }

    private static long incrementGeneration(long value, String name) {
        if (value == Long.MAX_VALUE) throw new IllegalStateException(name + " exhausted");
        return value + 1;
    }

    private void closeFrameSubscriptionLocked() {
        SFMVoxTerminalFrameInbox.Subscription closingSubscription = subscription;
        DetachedSubscription detached = new DetachedSubscription(frameReceiver, subscriptionCall);
        subscription = null;
        frameReceiver = null;
        subscriptionCall = null;
        if (closingSubscription != null) frameInbox.disconnect(closingSubscription);
        if (detached.receiver() != null || detached.call() != null) {
            scheduleTransportCleanup(() -> closeDetachedSubscription(detached));
        }
    }

    private void closeDetachedSubscription(DetachedSubscription detached) {
        if (detached.receiver() != null) detached.receiver().close();
        if (detached.call() != null) detached.call().cancel(true);
    }

    private void closeDetachedTransport(DetachedTransport detached) {
        if (detached.requestDisconnect() && detached.client() != null && detached.sessionId() != null) {
            try {
                detached.client().disconnect(new TerminalDisconnectRequest(
                        detached.sessionId(), "SFM terminal closed", nextSequence()));
            } catch (Exception ignored) {
                // Transport shutdown remains best effort after a failed optional endpoint.
            }
        }
        if (detached.lane() != null) detached.lane().close();
        if (detached.connection() != null) detached.connection().close();
    }

    private void scheduleTransportCleanup(Runnable cleanup) {
        try {
            transportCleanup.execute(cleanup);
        } catch (RejectedExecutionException error) {
            Thread fallback = new Thread(cleanup, "sfm-vox-terminal-cleanup-fallback");
            fallback.setDaemon(true);
            fallback.start();
        }
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

    private static String describeSubscriptionResult(
            VoxResult<TerminalOperationResult, TerminalError> result) {
        if (result == null) return "subscription completed without a result";
        if (result.isApplicationError()) return result.applicationError().message();
        String detail = result.detail();
        return detail == null || detail.isBlank() ? result.kind().name() : detail;
    }

    static String streamIdentity(String connectionEpoch, String sessionEpoch) {
        return connectionEpoch + "\u0000" + sessionEpoch;
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
