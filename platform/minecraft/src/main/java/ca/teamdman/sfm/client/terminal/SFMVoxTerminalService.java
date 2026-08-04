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
import org.facet.vox.generated.TerminalFrameOrigin;
import org.facet.vox.generated.TerminalFrameKind;
import org.facet.vox.generated.TerminalAlphaMode;
import org.facet.vox.generated.TerminalColorSpace;
import org.facet.vox.generated.TerminalInputResult;
import org.facet.vox.generated.TerminalKeyInput;
import org.facet.vox.generated.TerminalMouseInput;
import org.facet.vox.generated.TerminalOperationResult;
import org.facet.vox.generated.TerminalPublicationTelemetry;
import org.facet.vox.generated.TerminalPresentationCapabilitiesRequest;
import org.facet.vox.generated.TerminalPresentationCapabilitiesResult;
import org.facet.vox.generated.TerminalPresentationMode;
import org.facet.vox.generated.TerminalRasterFrameEvent;
import org.facet.vox.generated.TerminalRasterFrameKind;
import org.facet.vox.generated.TerminalRasterRegion;
import org.facet.vox.generated.TerminalRasterSubscribeRequest;
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
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
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
    private final ExecutorService rasterSubscriptionReceiver;
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
    private final SFMVoxTerminalRasterHandoff<RasterStream> rasterHandoff;
    private List<TerminalPresentationMode> presentationModes = List.of();
    private SFMTerminalPresentationCatalog presentationCatalog =
            SFMTerminalPresentationCatalog.undiscovered();
    private SFMTerminalPresentationSelection requestedPresentation =
            SFMTerminalPresentationSelection.DEFAULT;
    private SFMTerminalPresentationSelection activePresentation;
    private String presentationTransitionFailure;
    private SFMTerminalFrame pendingRasterFrame;
    private long presentationRequestGeneration;
    private boolean presentationExplicitlyRequested;
    private String acceptedRasterPresentationGeneration = "";
    private String rasterConnectionEpoch = "";
    private String rasterSessionEpoch = "";
    private long rasterLastTerminalSequence;
    private long rasterLastFrameSequence;
    private long rasterSubscriptionsStarted;
    private long rasterFramesReceived;
    private long rasterFramesAccepted;
    private long rasterFramesRejected;
    private long rasterStaleFrames;
    private long rasterFullFrames;
    private long rasterDirtyFrames;
    private long rasterFullResyncFrames;
    private TerminalPublicationTelemetry latestRasterPublication;
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

    private record RasterSubscription(
            long generation,
            String sessionId,
            String presentationGeneration,
            SFMTerminalPresentationSelection selection,
            TerminalPresentationMode mode) {}

    private static final class RasterStream {
        private final RasterSubscription subscription;
        private final ServiceLane lane;
        private final VoxRx<TerminalRasterFrameEvent> receiver;
        private final SFMTerminalRgbaCompositor compositor =
                new SFMTerminalRgbaCompositor(SFMTerminalRasterLimits.RGBA8_V1_DEFAULTS);
        private CompletableFuture<VoxResult<TerminalOperationResult, TerminalError>> call;
        private String acceptedPresentationGeneration = "";
        private String connectionEpoch = "";
        private String sessionEpoch = "";
        private long lastTerminalSequence;
        private long lastFrameSequence;
        private boolean cleanupScheduled;

        private RasterStream(
                RasterSubscription subscription,
                ServiceLane lane,
                VoxRx<TerminalRasterFrameEvent> receiver) {
            this.subscription = subscription;
            this.lane = lane;
            this.receiver = receiver;
        }
    }

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
        AtomicLong rasterReceiverSequence = new AtomicLong();
        this.rasterSubscriptionReceiver = new ThreadPoolExecutor(
                2,
                2,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(2),
                runnable -> {
                    Thread thread = new Thread(runnable,
                            "sfm-vox-terminal-raster-receiver-"
                                    + rasterReceiverSequence.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy());
        this.transportCleanup = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "sfm-vox-terminal-cleanup");
            thread.setDaemon(true);
            return thread;
        });
        this.rasterHandoff = new SFMVoxTerminalRasterHandoff<>(this::retireRasterStreamLocked);
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
    @Override
    public String assertPushEvidenceForAutomation(boolean reconnectExpected) {
        synchronized (lock) {
            if (rasterSubscriptionsStarted > 0) {
                if (activePresentation == null || rasterFramesAccepted < 1
                        || rasterLastFrameSequence < 1 || latestRasterPublication == null) {
                    throw new IllegalStateException(
                            "Java raster subscription has no accepted live evidence"
                                    + " requested=" + requestedPresentation.label()
                                    + " active=" + (activePresentation == null
                                    ? "none" : activePresentation.label())
                                    + " subscriptions=" + rasterSubscriptionsStarted
                                    + " received=" + rasterFramesReceived
                                    + " accepted=" + rasterFramesAccepted
                                    + " rejected=" + rasterFramesRejected
                                    + " stale=" + rasterStaleFrames
                                    + " lane=" + (rasterHandoff.active() == null
                                    ? "none" : rasterHandoff.active().lane.state())
                                    + " failure=" + failure);
                }
                TerminalPublicationTelemetry producer = latestRasterPublication;
                if (producer.pendingDepth() < 0 || producer.pendingDepth() > 1
                        || producer.pendingDepthMax() < 0 || producer.pendingDepthMax() > 1) {
                    throw new IllegalStateException("Rust raster producer violated its bounded pending depth");
                }
                String result = String.join("\n",
                        "transport=vox.txrx.raster",
                        "requested_renderer=" + requestedPresentation.rendererId().wireId(),
                        "requested_transport=" + requestedPresentation.transportId().wireId(),
                        "active_renderer=" + activePresentation.rendererId().wireId(),
                        "active_transport=" + activePresentation.transportId().wireId(),
                        "presentation_generation=" + acceptedRasterPresentationGeneration,
                        "raster_subscriptions_started=" + rasterSubscriptionsStarted,
                        "raster_frames_received=" + rasterFramesReceived,
                        "raster_frames_accepted=" + rasterFramesAccepted,
                        "raster_frames_rejected=" + rasterFramesRejected,
                        "raster_stale_frames=" + rasterStaleFrames,
                        "raster_full_frames=" + rasterFullFrames,
                        "raster_dirty_frames=" + rasterDirtyFrames,
                        "raster_full_resync_frames=" + rasterFullResyncFrames,
                        "latest_terminal_sequence=" + rasterLastTerminalSequence,
                        "latest_frame_sequence=" + rasterLastFrameSequence,
                        "producer_renders_started=" + producer.rendersStarted(),
                        "producer_renders_completed=" + producer.rendersCompleted(),
                        "producer_frames_pushed=" + producer.framesPushed(),
                        "producer_pending_depth=" + producer.pendingDepth(),
                        "producer_pending_depth_max=" + producer.pendingDepthMax(),
                        "producer_mutation_to_send_us=" + producer.mutationToSendUs(),
                        "producer_credit_wait_us=" + producer.creditWaitUs());
                SFM.LOGGER.info("SFM_VOX_TERMINAL_RASTER_EVIDENCE {}", result.replace('\n', ' '));
                return result + "\n";
            }
        }
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
        synchronized (lock) {
            if (pendingRasterFrame != null) {
                SFMTerminalFrame frame = pendingRasterFrame;
                pendingRasterFrame = null;
                return Optional.of(frame);
            }
        }
        return frameInbox.takeLatest();
    }

    @Override
    public boolean canPresentRetainedFrame() {
        synchronized (lock) {
            return sessionId != null && (activePresentation != null || frameInbox.isLive());
        }
    }

    @Override
    public List<SFMTerminalRendererOption> rendererOptions() {
        synchronized (lock) {
            return presentationCatalog.rendererOptions(requestedPresentation.transportId());
        }
    }

    @Override
    public List<SFMTerminalTransportOption> transportOptions() {
        synchronized (lock) {
            return presentationCatalog.transportOptions(requestedPresentation.rendererId());
        }
    }

    @Override
    public String requestedRendererId() {
        synchronized (lock) {
            return requestedPresentation.rendererId().wireId();
        }
    }

    @Override
    public String requestedTransportId() {
        synchronized (lock) {
            return requestedPresentation.transportId().wireId();
        }
    }

    @Override
    public Optional<String> activeRendererId() {
        synchronized (lock) {
            return Optional.ofNullable(activePresentation)
                    .map(selection -> selection.rendererId().wireId());
        }
    }

    @Override
    public Optional<String> activeTransportId() {
        synchronized (lock) {
            return Optional.ofNullable(activePresentation)
                    .map(selection -> selection.transportId().wireId());
        }
    }

    @Override
    public SFMTerminalPresentationTransitionState presentationState() {
        synchronized (lock) {
            return new SFMTerminalPresentationTransitionState(
                    requestedPresentation,
                    Optional.ofNullable(activePresentation),
                    Optional.ofNullable(presentationTransitionFailure));
        }
    }

    @Override
    public SFMTerminalPresentationChangeResult requestRenderer(String rendererId) {
        final SFMTerminalRendererId renderer;
        try {
            renderer = SFMTerminalRendererId.fromWireId(rendererId);
        } catch (IllegalArgumentException error) {
            return SFMTerminalPresentationChangeResult.rejected(error.getMessage());
        }
        final SFMTerminalPresentationSelection requested;
        synchronized (lock) {
            requested = requestedPresentation.withRenderer(renderer);
        }
        return requestPresentation(requested);
    }

    @Override
    public SFMTerminalPresentationChangeResult requestTransport(String transportId) {
        final SFMTerminalTransportId transport;
        try {
            transport = SFMTerminalTransportId.fromWireId(transportId);
        } catch (IllegalArgumentException error) {
            return SFMTerminalPresentationChangeResult.rejected(error.getMessage());
        }
        final SFMTerminalPresentationSelection requested;
        synchronized (lock) {
            requested = requestedPresentation.withTransport(transport);
        }
        return requestPresentation(requested);
    }

    private SFMTerminalPresentationChangeResult requestPresentation(
            SFMTerminalPresentationSelection requested
    ) {
        final String currentSession;
        final long generation;
        final boolean alreadyActive;
        synchronized (lock) {
            if (closed) return SFMTerminalPresentationChangeResult.rejected("Rust terminal is closed");
            if (presentationCatalog.discovered()
                    && presentationCatalog.supportedMode(requested).isEmpty()) {
                return SFMTerminalPresentationChangeResult.rejected(
                        "Terminal presentation '" + requested.label() + "' is unavailable: "
                                + presentationCatalog.unavailableReason(requested));
            }
            if (requested.equals(requestedPresentation)
                    && (requested.equals(activePresentation)
                    || rasterHandoff.pending() != null
                    && requested.equals(rasterHandoff.pending().subscription.selection()))) {
                return SFMTerminalPresentationChangeResult.accepted(
                        "Terminal presentation is already " + requested.label());
            }
            requestedPresentation = requested;
            presentationExplicitlyRequested = true;
            presentationTransitionFailure = null;
            presentationRequestGeneration = incrementGeneration(
                    presentationRequestGeneration, "terminal presentation request generation");
            generation = presentationRequestGeneration;
            RasterStream supersededPending = rasterHandoff.pending();
            if (supersededPending != null) rasterHandoff.failPending(supersededPending);
            alreadyActive = requested.equals(activePresentation);
            currentSession = sessionId;
        }
        if (currentSession == null) {
            return SFMTerminalPresentationChangeResult.accepted(
                    "Terminal presentation " + requested.label() + " will activate after connection");
        }
        if (alreadyActive) {
            return SFMTerminalPresentationChangeResult.accepted(
                    "Terminal presentation is already active as " + requested.label());
        }
        if (!submitDriver(() -> switchRasterSubscription(currentSession, requested, generation))) {
            synchronized (lock) {
                if (generation == presentationRequestGeneration) {
                    presentationTransitionFailure =
                            "Rust terminal presentation switch could not be queued";
                }
            }
            return SFMTerminalPresentationChangeResult.rejected(
                    "Rust terminal presentation switch could not be queued");
        }
        return SFMTerminalPresentationChangeResult.accepted(
                "Requested terminal presentation " + requested.label()
                        + "; awaiting full resynchronization");
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
        rasterSubscriptionReceiver.shutdownNow();
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

    static SFMTerminalPresentationCatalog intersectPresentationModes(
            List<TerminalPresentationMode> modes) {
        return SFMTerminalPresentationCatalog.intersect(
                SFMTerminalRendererId.RUST_CPU_FONTDUE.wireId(),
                SFMTerminalTransportId.FULL_PNG.wireId(),
                modes.stream()
                        .map(SFMVoxTerminalPresentationAdapter::advertisedMode)
                        .toList());
    }

    private void discoverPresentationModes(TerminalClient currentClient, String currentSession)
            throws Exception {
        TerminalPresentationCapabilitiesResult result = requireSuccess(
                await(currentClient.presentationCapabilities(
                        new TerminalPresentationCapabilitiesRequest(currentSession, nextSequence())),
                        "reading terminal presentation capabilities"),
                "reading terminal presentation capabilities");
        if (!currentSession.equals(result.sessionId())) {
            throw new IllegalStateException("terminal presentation capabilities returned another session");
        }
        List<TerminalPresentationMode> modes = List.copyOf(result.modes());
        SFMTerminalPresentationCatalog catalog =
                SFMVoxTerminalPresentationAdapter.catalog(result);
        SFMTerminalPresentationSelection selected;
        long generation;
        synchronized (lock) {
            if (closed || client != currentClient || !Objects.equals(sessionId, currentSession)) {
                throw new IllegalStateException("terminal capability discovery was superseded");
            }
            presentationModes = modes;
            presentationCatalog = catalog;
            boolean requestedSupported = catalog.supportedMode(requestedPresentation).isPresent();
            if (!requestedSupported) {
                if (presentationExplicitlyRequested) {
                    throw new IllegalStateException(
                            "requested terminal presentation is not supported: "
                                    + requestedPresentation.label() + ": "
                                    + catalog.unavailableReason(requestedPresentation));
                }
                if (catalog.supportedMode(catalog.defaultSelection()).isEmpty()) {
                    throw new IllegalStateException(
                            "server default terminal presentation has no Java presenter: "
                                    + catalog.defaultSelection().label());
                }
                requestedPresentation = catalog.defaultSelection();
            }
            selected = requestedPresentation;
            presentationTransitionFailure = null;
            presentationRequestGeneration = incrementGeneration(
                    presentationRequestGeneration, "terminal presentation request generation");
            generation = presentationRequestGeneration;
        }
        switchRasterSubscription(currentSession, selected, generation);
    }

    private TerminalPresentationMode presentationMode(
            SFMTerminalPresentationSelection selection
    ) {
        return presentationModes.stream()
                .filter(mode -> mode.rendererId().equals(selection.rendererId().wireId()))
                .filter(mode -> mode.transportId().equals(selection.transportId().wireId()))
                .filter(mode -> SFMTerminalPresentationCatalog.intersect(
                                selection.rendererId().wireId(),
                                selection.transportId().wireId(),
                                List.of(SFMVoxTerminalPresentationAdapter.advertisedMode(mode)))
                        .supportedMode(selection).isPresent())
                .findFirst()
                .orElse(null);
    }

    private void switchRasterSubscription(
            String currentSession,
            SFMTerminalPresentationSelection selection,
            long generation) {
        try {
            VoxConnection currentConnection;
            TerminalPresentationMode mode;
            synchronized (lock) {
                if (closed || !Objects.equals(sessionId, currentSession)
                        || generation != presentationRequestGeneration
                        || !requestedPresentation.equals(selection)) return;
                currentConnection = connection;
                mode = presentationMode(selection);
                if (currentConnection == null || mode == null) {
                    presentationTransitionFailure =
                            "Terminal presentation '" + selection.label() + "' is unavailable";
                    return;
                }
                RasterStream activeStream = rasterHandoff.active();
                if (activeStream != null
                        && selection.equals(activeStream.subscription.selection())) {
                    RasterStream pendingStream = rasterHandoff.pending();
                    if (pendingStream != null) rasterHandoff.failPending(pendingStream);
                    presentationTransitionFailure = null;
                    return;
                }
            }
            startRasterSubscription(currentConnection, currentSession, selection, mode, generation);
        } catch (Exception error) {
            synchronized (lock) {
                if (generation == presentationRequestGeneration
                        && Objects.equals(sessionId, currentSession)) {
                    presentationTransitionFailure =
                            "Vox terminal presentation switch failed: " + describe(error);
                }
            }
        }
    }

    private void startRasterSubscription(
            VoxConnection currentConnection,
            String currentSession,
            SFMTerminalPresentationSelection selection,
            TerminalPresentationMode mode,
            long generation) throws Exception {
        ServiceLane currentRasterLane = currentConnection.openLane(
                TerminalServiceDescriptor.INSTANCE, LaneOptions.defaults());
        RasterStream openedStream = null;
        try {
            await(currentRasterLane.opened(), "opening terminal raster lane");
            TerminalClient currentClient = new TerminalClient(currentRasterLane);
            VoxChannels.Pair<TerminalRasterFrameEvent> channel = VoxChannels.channel(
                    TerminalRasterFrameEvent.ADAPTER);
            long requestSequence = nextSequence();
            String wireGeneration = "sfm-presentation-" + generation + "-" + requestSequence;
            long maxFrameBytes = Math.min(MAX_FRAME_BYTES, mode.maxFrameBytes());
            int maxRegions = Math.min(SFMTerminalRasterLimits.RGBA8_V1_MAX_REGIONS, mode.maxRegions());
            RasterSubscription currentSubscription = new RasterSubscription(
                    generation, currentSession, wireGeneration, selection, mode);
            RasterStream currentStream = new RasterStream(
                    currentSubscription, currentRasterLane, channel.rx());
            openedStream = currentStream;
            if (mode.encoding() == TerminalFrameEncoding.RGBA8) {
                currentStream.compositor.expectGeneration(wireGeneration);
            }
            synchronized (lock) {
                if (closed || connection != currentConnection
                        || !Objects.equals(sessionId, currentSession)
                        || generation != presentationRequestGeneration
                        || !requestedPresentation.equals(selection)) {
                    retireRasterStreamLocked(currentStream);
                    return;
                }
                rasterHandoff.beginPending(currentStream);
                rasterSubscriptionsStarted = incrementGeneration(
                        rasterSubscriptionsStarted, "raster subscriptions started");
                SFM.LOGGER.info(
                        "SFM_VOX_TERMINAL_RASTER_SUBSCRIPTION_STARTED session={} renderer={} transport={} "
                                + "presentation_generation={} lane={}",
                        currentSession,
                        mode.rendererId(),
                        mode.transportId(),
                        wireGeneration,
                        currentRasterLane.state());
            }
            CompletableFuture<VoxResult<TerminalOperationResult, TerminalError>> currentCall =
                    currentClient.subscribeRasterFrames(
                            SFMVoxTerminalPresentationAdapter.subscribeRequest(
                                    currentSession,
                                    mode,
                                    wireGeneration,
                                    maxFrameBytes,
                                    maxRegions,
                                    requestSequence,
                                    correlationId("raster-subscribe", requestSequence)),
                            channel.tx(),
                            CallOptions.withIdleTimeout(SUBSCRIPTION_IDLE_TIMEOUT));
            synchronized (lock) {
                if (rasterHandoff.role(currentStream)
                        != SFMVoxTerminalRasterHandoff.Role.PENDING) {
                    retireRasterStreamLocked(currentStream);
                    return;
                }
                currentStream.call = currentCall;
            }
            currentCall.whenComplete((result, error) ->
                    rasterSubscriptionCompleted(currentStream, result, error));
            synchronized (lock) {
                if (rasterHandoff.role(currentStream)
                        != SFMVoxTerminalRasterHandoff.Role.PENDING) return;
            }
            try {
                rasterSubscriptionReceiver.execute(() -> receiveRasterFrames(currentStream));
            } catch (RejectedExecutionException error) {
                synchronized (lock) {
                    failPendingRasterStreamLocked(
                            currentStream,
                            "Vox terminal raster receiver capacity is exhausted");
                }
                throw error;
            }
        } catch (Exception error) {
            synchronized (lock) {
                if (openedStream == null) {
                    currentRasterLane.close();
                } else if (rasterHandoff.role(openedStream)
                        == SFMVoxTerminalRasterHandoff.Role.PENDING) {
                    rasterHandoff.failPending(openedStream);
                } else {
                    retireRasterStreamLocked(openedStream);
                }
            }
            throw error;
        }
    }

    private void receiveRasterFrames(RasterStream currentStream) {
        Throwable receiverFailure = null;
        try {
            while (!Thread.currentThread().isInterrupted()) {
                TerminalRasterFrameEvent event = currentStream.receiver.receive();
                if (event == null) break;
                acceptRasterFrame(currentStream, event);
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            receiverFailure = error;
        } catch (Exception error) {
            receiverFailure = error;
        } finally {
            rasterReceiverStopped(currentStream, receiverFailure);
        }
    }

    private void acceptRasterFrame(
            RasterStream currentStream,
            TerminalRasterFrameEvent event) {
        String eventPresentationGeneration =
                SFMVoxTerminalPresentationAdapter.presentationGeneration(event);
        try {
            synchronized (lock) {
                rasterFramesReceived = incrementGeneration(rasterFramesReceived, "raster frames received");
                SFMVoxTerminalRasterHandoff.Role role = rasterHandoff.role(currentStream);
                if (role == SFMVoxTerminalRasterHandoff.Role.STALE) {
                    rasterStaleFrames = incrementGeneration(rasterStaleFrames, "raster stale frames");
                    return;
                }
                RasterSubscription currentSubscription = currentStream.subscription;
                if (role == SFMVoxTerminalRasterHandoff.Role.PENDING
                        && (currentSubscription.generation() != presentationRequestGeneration
                        || !currentSubscription.selection().equals(requestedPresentation)
                        || !Objects.equals(currentSubscription.sessionId(), sessionId))) {
                    rasterStaleFrames = incrementGeneration(rasterStaleFrames, "raster stale frames");
                    rasterHandoff.failPending(currentStream);
                    return;
                }
                validateRasterEnvelopeLocked(currentStream, event);
                org.facet.vox.generated.TerminalRasterFrame nativeFrame = event.frame();
                byte[] presentedPayload;
                boolean png;
                if (nativeFrame.encoding() == TerminalFrameEncoding.PNG) {
                    validatePngRasterFrame(currentSubscription, event);
                    presentedPayload = nativeFrame.payload();
                    png = true;
                } else {
                    SFMTerminalRasterFrame rasterFrame = toRasterFrame(event);
                    SFMTerminalRasterLimits negotiated = new SFMTerminalRasterLimits(
                            currentSubscription.mode().maxPixelWidth(),
                            currentSubscription.mode().maxPixelHeight(),
                            Math.min(currentSubscription.mode().maxFrameBytes(),
                                    SFMTerminalRasterLimits.RGBA8_V1_MAX_PAYLOAD_BYTES),
                            Math.min(currentSubscription.mode().maxRegions(),
                                    SFMTerminalRasterLimits.RGBA8_V1_MAX_REGIONS));
                    SFMTerminalRasterFrameValidator.validateRgba8(rasterFrame, negotiated);
                    if (currentStream.compositor.apply(rasterFrame)
                            == SFMTerminalRgbaCompositor.ApplyResult.STALE_GENERATION) return;
                    presentedPayload = currentStream.compositor.pixels();
                    png = false;
                }
                SFMTerminalFrame frame = new SFMTerminalFrame(
                        event.frameSequence(), true, png, presentedPayload,
                        rasterFrameMetadata(event),
                        streamIdentity(event.connectionEpoch(), event.sessionEpoch())
                                + ":" + eventPresentationGeneration);
                boolean promoted = false;
                if (role == SFMVoxTerminalRasterHandoff.Role.PENDING) {
                    promoted = rasterHandoff.promotePending(
                            currentStream,
                            event.fullResync()
                                    && event.frame().kind() == TerminalRasterFrameKind.FULL
                                    && event.baseFrameSequence() == 0);
                    if (!promoted) {
                        rasterStaleFrames = incrementGeneration(
                                rasterStaleFrames, "raster stale frames");
                        return;
                    }
                }
                pendingRasterFrame = frame;
                activePresentation = currentSubscription.selection();
                currentStream.acceptedPresentationGeneration = eventPresentationGeneration;
                currentStream.connectionEpoch = event.connectionEpoch();
                currentStream.sessionEpoch = event.sessionEpoch();
                currentStream.lastTerminalSequence = event.terminalSequence();
                currentStream.lastFrameSequence = event.frameSequence();
                acceptedRasterPresentationGeneration = eventPresentationGeneration;
                rasterConnectionEpoch = event.connectionEpoch();
                rasterSessionEpoch = event.sessionEpoch();
                rasterLastTerminalSequence = event.terminalSequence();
                rasterLastFrameSequence = event.frameSequence();
                rasterFramesAccepted = incrementGeneration(rasterFramesAccepted, "raster frames accepted");
                if (event.frame().kind() == TerminalRasterFrameKind.FULL) {
                    rasterFullFrames = incrementGeneration(rasterFullFrames, "raster full frames");
                } else {
                    rasterDirtyFrames = incrementGeneration(rasterDirtyFrames, "raster dirty frames");
                }
                if (event.fullResync()) {
                    rasterFullResyncFrames = incrementGeneration(
                            rasterFullResyncFrames, "raster full resynchronization frames");
                }
                latestRasterPublication = event.publication();
                failure = null;
                if (promoted || rasterHandoff.pending() == null
                        && requestedPresentation.equals(activePresentation)) {
                    presentationTransitionFailure = null;
                }
                if (event.fullResync()) {
                    SFM.LOGGER.info(
                            "SFM_VOX_TERMINAL_RASTER_ACTIVE session={} renderer={} transport={} "
                                    + "presentation_generation={} frame_sequence={}",
                            event.sessionId(),
                            event.rendererId(),
                            event.transportId(),
                            eventPresentationGeneration,
                            event.frameSequence());
                }
            }
        } catch (RuntimeException error) {
            synchronized (lock) {
                SFMVoxTerminalRasterHandoff.Role role = rasterHandoff.role(currentStream);
                if (role != SFMVoxTerminalRasterHandoff.Role.STALE) {
                    rasterFramesRejected = incrementGeneration(
                            rasterFramesRejected, "raster frames rejected");
                    String rejection = "Rejected malformed terminal raster frame: " + describe(error);
                    if (role == SFMVoxTerminalRasterHandoff.Role.PENDING) {
                        presentationTransitionFailure = rejection;
                        if (rasterHandoff.active() == null) failure = rejection;
                        rasterHandoff.failPending(currentStream);
                    } else {
                        failure = rejection;
                        if (rasterHandoff.pending() == null) {
                            presentationTransitionFailure = rejection;
                        }
                    }
                    SFM.LOGGER.warn(
                            "SFM_VOX_TERMINAL_RASTER_FRAME_REJECTED session={} renderer={} transport={} "
                                    + "presentation_generation={} "
                                    + "terminal_sequence={} frame_sequence={} base_frame_sequence={} "
                                    + "full_resync={} frame_kind={} frame_encoding={} failure={}",
                            event.sessionId(),
                            event.rendererId(),
                            event.transportId(),
                            eventPresentationGeneration,
                            event.terminalSequence(),
                            event.frameSequence(),
                            event.baseFrameSequence(),
                            event.fullResync(),
                            event.frame().kind(),
                            event.frame().encoding(),
                            rejection,
                            error);
                }
            }
        }
    }

    private void validateRasterEnvelopeLocked(
            RasterStream currentStream,
            TerminalRasterFrameEvent event) {
        RasterSubscription currentSubscription = currentStream.subscription;
        TerminalPresentationMode mode = currentSubscription.mode();
        String eventPresentationGeneration =
                SFMVoxTerminalPresentationAdapter.presentationGeneration(event);
        long negotiatedBytes = Math.min(MAX_FRAME_BYTES, mode.maxFrameBytes());
        int negotiatedRegions = Math.min(SFMTerminalRasterLimits.RGBA8_V1_MAX_REGIONS, mode.maxRegions());
        if (!currentSubscription.sessionId().equals(event.sessionId())
                || !currentSubscription.presentationGeneration().equals(eventPresentationGeneration)
                || !mode.rendererId().equals(event.rendererId())
                || !mode.damageModeId().equals(event.damageModeId())
                || !mode.transportId().equals(event.transportId())
                || mode.transportVersion() != event.transportVersion()
                || mode.frameContractVersion() != event.frameContractVersion()
                || negotiatedBytes != event.maxFrameBytes()
                || negotiatedRegions != event.maxRegions()
                || event.terminalSequence() < 0 || event.frameSequence() <= 0
                || event.frame().payload().length > negotiatedBytes) {
            throw new IllegalArgumentException("raster event does not match its negotiated subscription");
        }
        boolean first = currentStream.acceptedPresentationGeneration.isEmpty();
        if (first) {
            if (!event.fullResync()
                    || event.frame().kind() != TerminalRasterFrameKind.FULL
                    || event.baseFrameSequence() != 0) {
                throw new IllegalArgumentException(
                        "replacement presentation must begin with a full resynchronization"
                                + " [full_resync=" + event.fullResync()
                                + ", frame_kind=" + event.frame().kind()
                                + ", base_frame_sequence=" + event.baseFrameSequence() + "]");
            }
        } else if (!currentStream.acceptedPresentationGeneration.equals(eventPresentationGeneration)
                || !currentStream.connectionEpoch.equals(event.connectionEpoch())
                || !currentStream.sessionEpoch.equals(event.sessionEpoch())
                || event.terminalSequence() < currentStream.lastTerminalSequence
                || event.frameSequence() <= currentStream.lastFrameSequence) {
            throw new IllegalArgumentException("stale or out-of-order raster event");
        }
    }

    private static void validatePngRasterFrame(
            RasterSubscription subscription,
            TerminalRasterFrameEvent event) {
        org.facet.vox.generated.TerminalRasterFrame frame = event.frame();
        if (!"full-png".equals(subscription.mode().transportId())
                || frame.encoding() != TerminalFrameEncoding.PNG
                || frame.kind() != TerminalRasterFrameKind.FULL
                || frame.origin() != TerminalFrameOrigin.TOP_LEFT
                || frame.alphaMode() != TerminalAlphaMode.STRAIGHT
                || frame.colorSpace() != TerminalColorSpace.SRGB
                || frame.width() <= 0 || frame.width() > subscription.mode().maxPixelWidth()
                || frame.height() <= 0 || frame.height() > subscription.mode().maxPixelHeight()
                || frame.stride() != 0 || !frame.regions().isEmpty()
                || !frame.complete()
                || !isPng(frame.payload())) {
            throw new IllegalArgumentException("malformed full-png raster frame");
        }
    }

    private static SFMTerminalRasterFrame toRasterFrame(TerminalRasterFrameEvent event) {
        org.facet.vox.generated.TerminalRasterFrame frame = event.frame();
        if (frame.encoding() != TerminalFrameEncoding.RGBA8
                || frame.origin() != TerminalFrameOrigin.TOP_LEFT
                || frame.alphaMode() != TerminalAlphaMode.STRAIGHT
                || frame.colorSpace() != TerminalColorSpace.SRGB
                || !frame.complete()) {
            throw new IllegalArgumentException("raw raster metadata does not match RGBA8 contract v1");
        }
        List<SFMTerminalRasterRegion> regions = frame.regions().stream()
                .map(region -> new SFMTerminalRasterRegion(
                        region.x(), region.y(), region.width(), region.height(), region.stride(),
                        region.payloadOffset(), region.payloadLength()))
                .toList();
        return new SFMTerminalRasterFrame(
                SFMTerminalTransportId.fromWireId(event.transportId()),
                event.transportVersion(), event.frameContractVersion(),
                SFMVoxTerminalPresentationAdapter.presentationGeneration(event),
                event.frameSequence(), event.baseFrameSequence(), event.fullResync(),
                SFMTerminalRasterEncoding.RGBA8,
                frame.kind() == TerminalRasterFrameKind.FULL
                        ? SFMTerminalRasterFrameKind.FULL
                        : SFMTerminalRasterFrameKind.DIRTY_REGIONS,
                SFMTerminalRasterOrigin.TOP_LEFT,
                SFMTerminalRasterAlphaMode.STRAIGHT,
                SFMTerminalRasterColorSpace.SRGB,
                frame.width(), frame.height(), frame.stride(), frame.payload(), regions);
    }

    private static SFMTerminalFrameMetadata rasterFrameMetadata(TerminalRasterFrameEvent event) {
        org.facet.vox.generated.TerminalRasterFrame frame = event.frame();
        var timing = frame.timing();
        var surface = frame.surface();
        return new SFMTerminalFrameMetadata(
                event.frameSequence(), frame.logicalColumns(), frame.logicalRows(),
                frame.width(), frame.height(), surface.cellWidth(), surface.cellHeight(),
                surface.fontPixelSize(), event.rendererId(), event.transportId(),
                timing.ptyDrainUs(), 0L, timing.terminalSnapshotUs(), timing.fontLoadUs(),
                timing.rasterUs(), timing.payloadPackUs(), timing.pngEncodeUs(), timing.totalUs(),
                event.correlationId());
    }

    private void rasterSubscriptionCompleted(
            RasterStream currentStream,
            VoxResult<TerminalOperationResult, TerminalError> result,
            Throwable error) {
        synchronized (lock) {
            SFMVoxTerminalRasterHandoff.Role role = rasterHandoff.role(currentStream);
            if (role == SFMVoxTerminalRasterHandoff.Role.STALE) return;
            if (error == null && result != null && result.isSuccess()) return;
            String streamFailure = "Vox terminal raster subscription failed: "
                    + (error == null ? describeSubscriptionResult(result) : describe(error));
            recordRasterStreamFailureLocked(currentStream, role, streamFailure);
            RasterSubscription currentSubscription = currentStream.subscription;
            SFM.LOGGER.warn(
                    "SFM_VOX_TERMINAL_RASTER_SUBSCRIPTION_FAILED session={} renderer={} transport={} "
                            + "presentation_generation={} failure={}",
                    currentSubscription.sessionId(),
                    currentSubscription.selection().rendererId().wireId(),
                    currentSubscription.mode().transportId(),
                    currentSubscription.presentationGeneration(),
                    streamFailure,
                    error);
        }
    }

    private void rasterReceiverStopped(RasterStream currentStream, Throwable error) {
        synchronized (lock) {
            SFMVoxTerminalRasterHandoff.Role role = rasterHandoff.role(currentStream);
            if (role == SFMVoxTerminalRasterHandoff.Role.STALE) return;
            String streamFailure = error == null
                    ? "Vox terminal raster subscription closed"
                    : "Vox terminal raster subscription unavailable: " + describe(error);
            recordRasterStreamFailureLocked(currentStream, role, streamFailure);
            RasterSubscription currentSubscription = currentStream.subscription;
            SFM.LOGGER.warn(
                    "SFM_VOX_TERMINAL_RASTER_RECEIVER_STOPPED session={} renderer={} transport={} "
                            + "presentation_generation={} failure={}",
                    currentSubscription.sessionId(),
                    currentSubscription.selection().rendererId().wireId(),
                    currentSubscription.mode().transportId(),
                    currentSubscription.presentationGeneration(),
                    streamFailure,
                    error);
        }
    }

    private void recordRasterStreamFailureLocked(
            RasterStream currentStream,
            SFMVoxTerminalRasterHandoff.Role role,
            String streamFailure) {
        if (role == SFMVoxTerminalRasterHandoff.Role.PENDING) {
            failPendingRasterStreamLocked(currentStream, streamFailure);
            return;
        }
        failure = streamFailure;
        if (rasterHandoff.pending() == null) presentationTransitionFailure = streamFailure;
        if (rasterHandoff.failActive(currentStream)) {
            activePresentation = null;
            clearAcceptedRasterStateLocked();
        }
    }

    private void failPendingRasterStreamLocked(RasterStream currentStream, String streamFailure) {
        if (rasterHandoff.role(currentStream) != SFMVoxTerminalRasterHandoff.Role.PENDING) return;
        presentationTransitionFailure = streamFailure;
        if (rasterHandoff.active() == null) failure = streamFailure;
        rasterHandoff.failPending(currentStream);
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
            discoverPresentationModes(newClient, newSessionId);
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
        closeRasterSubscriptionLocked();
        DetachedTransport detached = new DetachedTransport(
                client, sessionId, lane, connection, requestDisconnect);
        client = null;
        lane = null;
        connection = null;
        sessionId = null;
        latestSnapshot = null;
        latestContent = null;
        pendingRasterFrame = null;
        activePresentation = null;
        presentationTransitionFailure = null;
        presentationModes = List.of();
        presentationCatalog = SFMTerminalPresentationCatalog.undiscovered();
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
        // Retire the request with one lifecycle signal. Cancelling the call
        // ends the remote producer and releases its request slot; also closing
        // the Rx would enqueue a competing channel reset for the same work.
        if (detached.call() != null) {
            detached.call().cancel(true);
        } else if (detached.receiver() != null) {
            detached.receiver().close();
        }
    }

    private void closeRasterSubscriptionLocked() {
        rasterHandoff.closeAll();
        clearAcceptedRasterStateLocked();
    }

    private void clearAcceptedRasterStateLocked() {
        acceptedRasterPresentationGeneration = "";
        rasterConnectionEpoch = "";
        rasterSessionEpoch = "";
        rasterLastTerminalSequence = 0;
        rasterLastFrameSequence = 0;
    }

    private void retireRasterStreamLocked(RasterStream stream) {
        if (stream == null || stream.cleanupScheduled) return;
        stream.cleanupScheduled = true;
        // A raster stream owns an independent lane. One lane close retires its
        // request, Tx/Rx channel, and receiver without disturbing the control
        // lane, PTY session, active sibling, or a replacement pending stream.
        scheduleTransportCleanup(stream.lane::close);
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
