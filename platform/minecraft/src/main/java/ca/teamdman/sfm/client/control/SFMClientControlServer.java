package ca.teamdman.sfm.client.control;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.action.SFMClientActionContext;
import ca.teamdman.sfm.client.action.SFMClientActionExecutor;
import ca.teamdman.sfm.client.explorer.SFMEntitySelector;
import ca.teamdman.sfm.client.explorer.SFMExplorerId;
import ca.teamdman.sfm.client.explorer.SFMExplorerRuntime;
import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.explorer.action.SFMExplorerActionRequest;
import ca.teamdman.sfm.client.explorer.action.SFMExplorerActionResult;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerProjection;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.fml.ModList;
import org.facet.vox.CallContext;
import org.facet.vox.ConnectionOptions;
import org.facet.vox.ServiceRegistry;
import org.facet.vox.VoxConnection;
import org.facet.vox.VoxResult;
import ca.teamdman.sfm.client.control.generated.SfmControlDescribeRequest;
import ca.teamdman.sfm.client.control.generated.SfmControlDescribeResult;
import ca.teamdman.sfm.client.control.generated.SfmControlDispatcher;
import ca.teamdman.sfm.client.control.generated.SfmControlError;
import ca.teamdman.sfm.client.control.generated.SfmControlErrorCode;
import ca.teamdman.sfm.client.control.generated.SfmControlExplorerOperationRequest;
import ca.teamdman.sfm.client.control.generated.SfmControlExplorerOperationResult;
import ca.teamdman.sfm.client.control.generated.SfmControlExplorerOperationStatus;
import ca.teamdman.sfm.client.control.generated.SfmControlExplorerTargetOutcome;
import ca.teamdman.sfm.client.control.generated.SfmControlExplorerTargetResult;
import ca.teamdman.sfm.client.control.generated.SfmControlHandler;
import ca.teamdman.sfm.client.control.generated.SfmControlInvokeClientActionRequest;
import ca.teamdman.sfm.client.control.generated.SfmControlInvokeClientActionResult;
import ca.teamdman.sfm.client.control.generated.SfmControlLifecycle;
import ca.teamdman.sfm.client.control.generated.SfmControlPingRequest;
import ca.teamdman.sfm.client.control.generated.SfmControlPingResult;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Authenticated loopback Vox service used by the short-lived {@code sfm.exe} client. */
public final class SFMClientControlServer implements AutoCloseable, SfmControlHandler {
    static final int PROTOCOL_VERSION = 1;
    static final int MAX_ACTION_TOKENS = 128;
    static final int MAX_ACTION_TOKEN_BYTES = 4096;
    static final int MAX_FEEDBACK_ENTRIES = 64;
    static final int MAX_EXPLORER_SELECTOR_BYTES = 4096;
    static final int MAX_EXPLORER_PATH_BYTES = 16 * 1024;
    static final int MAX_EXPLORER_SETTING_BYTES = 256;
    static final int MAX_EXPLORER_TARGET_RESULTS = 256;
    static final int MAX_EXPLORER_ROOTS_PER_TARGET = 4096;
    static final int MAX_EXPLORER_VISIBLE_PATHS = 512;
    static final int MAX_EXPLORER_EVIDENCE_TEXT_BYTES = 16 * 1024;
    private static final String DESCRIPTOR_SCHEMA = "sfm.game-instance/1";
    private static final String HOST = "127.0.0.1";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final Minecraft minecraft;
    private final ServerSocket listener;
    private final Thread acceptThread;
    private final ThreadPoolExecutor connectionsExecutor;
    private final ThreadPoolExecutor controlExecutor;
    private final Set<VoxConnection> connections = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final SFMClientThreadGate clientThreadGate;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final String instanceId = UUID.randomUUID().toString();
    private final long processId = ProcessHandle.current().pid();
    private final String processStartNonce = randomHex(16);
    private final String authenticationToken = randomHex(32);
    private final long createdAtEpochMillis = System.currentTimeMillis();
    private final Path descriptorPath;
    private final String sfmVersion;
    private final String minecraftVersion;
    private final AtomicReference<ClientSnapshot> clientSnapshot = new AtomicReference<>(ClientSnapshot.initial());
    private volatile SfmControlLifecycle lifecycle = SfmControlLifecycle.STARTING;

    private SFMClientControlServer(Minecraft minecraft) throws IOException {
        this.minecraft = minecraft;
        if (processId <= 0 || processId > 0xFFFF_FFFFL) {
            throw new IOException("process id is outside the Vox u32 contract: " + processId);
        }

        listener = new ServerSocket();
        listener.bind(new InetSocketAddress(InetAddress.getByName(HOST), 0));

        connectionsExecutor = new ThreadPoolExecutor(
                0,
                8,
                15,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(16),
                runnable -> daemonThread(runnable, "sfm-client-control-connection"),
                new ThreadPoolExecutor.AbortPolicy()
        );
        controlExecutor = new ThreadPoolExecutor(
                2,
                4,
                15,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(64),
                runnable -> daemonThread(runnable, "sfm-client-control-worker"),
                new ThreadPoolExecutor.AbortPolicy()
        );
        clientThreadGate = new SFMClientThreadGate(minecraft::execute, controlExecutor, 32);
        descriptorPath = descriptorDirectory().resolve(instanceId + ".json");
        sfmVersion = sfmVersion();
        minecraftVersion = SharedConstants.getCurrentVersion().getName();
        acceptThread = daemonThread(this::acceptLoop, "sfm-client-control-accept");
    }

    public static SFMClientControlServer start(Minecraft minecraft) throws IOException {
        SFMClientControlServer server = new SFMClientControlServer(minecraft);
        try {
            server.acceptThread.start();
            server.publishDescriptor();
            server.lifecycle = SfmControlLifecycle.READY;
            server.observeClientTick();
            SFM.LOGGER.info(
                    "SFM_CLIENT_CONTROL_READY instance={} pid={} endpoint={}:{} descriptor={}",
                    server.instanceId,
                    server.processId,
                    HOST,
                    server.listener.getLocalPort(),
                    server.descriptorPath
            );
            return server;
        } catch (IOException | RuntimeException failure) {
            server.close();
            throw failure;
        }
    }

    public void observeClientTick() {
        ClientSnapshot previous = clientSnapshot.get();
        boolean nowFocused = minecraft.isWindowActive();
        boolean focusSeen = previous.focusSeen();
        long lastFocusEpochMillis = previous.lastFocusEpochMillis();
        if (nowFocused && !previous.focused()) {
            focusSeen = true;
            lastFocusEpochMillis = System.currentTimeMillis();
        }
        Screen screen = minecraft.screen;
        boolean worldPresent = minecraft.level != null;
        clientSnapshot.set(new ClientSnapshot(
                nowFocused,
                focusSeen,
                lastFocusEpochMillis,
                screen != null,
                screen == null ? "" : screen.getClass().getName(),
                worldPresent,
                worldPresent ? minecraft.level.dimension().location().toString() : ""
        ));
    }

    private void acceptLoop() {
        while (!closed.get()) {
            Socket socket = null;
            try {
                socket = listener.accept();
                Socket accepted = socket;
                connectionsExecutor.execute(() -> driveConnection(accepted));
            } catch (RejectedExecutionException failure) {
                closeQuietly(socket);
                SFM.LOGGER.warn("SFM_CLIENT_CONTROL_CONNECTION_REJECTED reason=capacity");
            } catch (SocketException failure) {
                if (!closed.get()) {
                    SFM.LOGGER.warn("SFM_CLIENT_CONTROL_ACCEPT_FAILED", failure);
                }
            } catch (IOException failure) {
                if (!closed.get()) {
                    SFM.LOGGER.warn("SFM_CLIENT_CONTROL_ACCEPT_FAILED", failure);
                }
                closeQuietly(socket);
            }
        }
    }

    private void driveConnection(Socket socket) {
        VoxConnection connection = null;
        try {
            ConnectionOptions options = ConnectionOptions.builder()
                    .maxFrameBytes(1024 * 1024)
                    .maxQueuedOutboundBytes(1024 * 1024)
                    .maxQueuedOutboundMessages(32)
                    .maxPendingRequests(32)
                    .maxOpenLanes(4)
                    .maxSchemaBytes(1024 * 1024)
                    .maxSchemas(64)
                    .handshakeTimeout(Duration.ofSeconds(5))
                    .idleTimeout(Duration.ofSeconds(30))
                    .closeTimeout(Duration.ofSeconds(2))
                    .handlerExecutor(controlExecutor)
                    .build();
            connection = VoxConnection.accept(
                    socket,
                    new ServiceRegistry().register(new SfmControlDispatcher(this)),
                    options
            );
            connections.add(connection);
            connection.drive();
        } catch (IOException | RuntimeException | org.facet.vox.VoxException failure) {
            if (!closed.get()) {
                SFM.LOGGER.debug("SFM_CLIENT_CONTROL_CONNECTION_CLOSED failure={}", failure.toString());
            }
        } finally {
            if (connection != null) {
                connections.remove(connection);
                connection.close();
            } else {
                closeQuietly(socket);
            }
        }
    }

    @Override
    public CompletableFuture<VoxResult<SfmControlPingResult, SfmControlError>> ping(
            CallContext context,
            SfmControlPingRequest request
    ) {
        SfmControlError validation = validateRequest(
                request.authenticationToken(),
                request.protocolVersion(),
                request.requestId()
        );
        if (validation != null) {
            return completedError(validation);
        }
        return CompletableFuture.completedFuture(VoxResult.success(new SfmControlPingResult(
                instanceId,
                processId,
                processStartNonce,
                PROTOCOL_VERSION,
                request.requestId()
        )));
    }

    @Override
    public CompletableFuture<VoxResult<SfmControlDescribeResult, SfmControlError>> describe(
            CallContext context,
            SfmControlDescribeRequest request
    ) {
        SfmControlError validation = validateRequest(
                request.authenticationToken(),
                request.protocolVersion(),
                request.requestId()
        );
        if (validation != null) {
            return completedError(validation);
        }

        ClientSnapshot snapshot = clientSnapshot.get();
        return CompletableFuture.completedFuture(VoxResult.success(new SfmControlDescribeResult(
                instanceId,
                processId,
                processStartNonce,
                PROTOCOL_VERSION,
                sfmVersion,
                minecraftVersion,
                lifecycle,
                snapshot.focused(),
                snapshot.focusSeen(),
                snapshot.lastFocusEpochMillis(),
                snapshot.screenPresent(),
                snapshot.screenClass(),
                snapshot.worldPresent(),
                snapshot.worldLabel(),
                List.of("instance.describe", "client-action.invoke", "explorer.control.v1"),
                request.requestId()
        )));
    }

    @Override
    public CompletableFuture<VoxResult<SfmControlInvokeClientActionResult, SfmControlError>> invokeClientAction(
            CallContext context,
            SfmControlInvokeClientActionRequest request
    ) {
        SfmControlError validation = validateRequest(
                request.authenticationToken(),
                request.protocolVersion(),
                request.requestId()
        );
        if (validation != null) {
            return completedError(validation);
        }
        if (request.actionTokens().isEmpty() || request.actionTokens().size() > MAX_ACTION_TOKENS) {
            return completedError(error(
                    SfmControlErrorCode.INVALID_REQUEST,
                    "action token count is invalid",
                    false,
                    request.requestId()
            ));
        }
        for (String token : request.actionTokens()) {
            if (token.isEmpty()
                    || token.getBytes(StandardCharsets.UTF_8).length > MAX_ACTION_TOKEN_BYTES
                    || token.indexOf('\0') >= 0) {
                return completedError(error(
                        SfmControlErrorCode.INVALID_REQUEST,
                        "action token is invalid",
                        false,
                        request.requestId()
                ));
            }
        }

        String canonicalAction = request.actionTokens().stream()
                .map(SFMClientControlServer::escapeCommandToken)
                .collect(java.util.stream.Collectors.joining(" ", "sfm action invoke ", ""));
        CompletableFuture<VoxResult<SfmControlInvokeClientActionResult, SfmControlError>> action =
                clientThreadGate.submit(() -> executeClientAction(canonicalAction, request.requestId()));
        context.cancellation().thenRun(() -> action.cancel(false));
        return action.handle((result, failure) -> failure == null
                ? result
                : VoxResult.applicationError(mapClientThreadFailure(failure, request.requestId())));
    }

    @Override
    public CompletableFuture<VoxResult<SfmControlExplorerOperationResult, SfmControlError>> explorerOperation(
            CallContext context,
            SfmControlExplorerOperationRequest request
    ) {
        SfmControlError validation = validateRequest(
                request.authenticationToken(),
                request.protocolVersion(),
                request.requestId()
        );
        if (validation != null) return completedError(validation);
        validation = validateExplorerOperationRequest(request);
        if (validation != null) return completedError(validation);
        CompletableFuture<VoxResult<SfmControlExplorerOperationResult, SfmControlError>> action =
                clientThreadGate.submit(() -> executeExplorerOperation(request));
        context.cancellation().thenRun(() -> action.cancel(false));
        return action.handle((result, failure) -> failure == null
                ? result
                : VoxResult.applicationError(mapClientThreadFailure(failure, request.requestId())));
    }

    private SfmControlError validateExplorerOperationRequest(SfmControlExplorerOperationRequest request) {
        try {
            requireBoundedText(
                    request.explorerSelector(),
                    1,
                    MAX_EXPLORER_SELECTOR_BYTES,
                    "explorer selector"
            );
            SFMEntitySelector selector = SFMEntitySelector.parseCanonical(
                    SFMEntitySelector.Domain.EXPLORER,
                    request.explorerSelector()
            );

            boolean usesPath = switch (request.operation()) {
                case ROOT_ADD, ROOT_REMOVE, NODE_EXPAND, NODE_COLLAPSE, NODE_TOGGLE, NODE_REFRESH -> true;
                default -> false;
            };
            if (usesPath) {
                requireBoundedText(request.canonicalPath(), 1, MAX_EXPLORER_PATH_BYTES, "explorer path");
                SFMPath path = SFMPath.parse(request.canonicalPath());
                if (!path.canonical().equals(request.canonicalPath())) {
                    throw new IllegalArgumentException("explorer path is not canonical");
                }
            } else if (!request.canonicalPath().isEmpty()) {
                throw new IllegalArgumentException("operation does not accept a path");
            }

            boolean usesSetting = switch (request.operation()) {
                case VIEW_SET, SORT_SET, GROUP_SET, ROOT_HOIST_SET -> true;
                default -> false;
            };
            if (usesSetting) {
                requireBoundedText(request.settingValue(), 1, MAX_EXPLORER_SETTING_BYTES, "setting value");
                if (request.settingValue().codePoints().anyMatch(Character::isWhitespace)) {
                    throw new IllegalArgumentException("setting value contains whitespace");
                }
            } else if (!request.settingValue().isEmpty()) {
                throw new IllegalArgumentException("operation does not accept a setting value");
            }
            if (request.operation()
                    == ca.teamdman.sfm.client.control.generated.SfmControlExplorerOperation.ROOT_HOIST_SET
                    && !request.settingValue().equals("auto")
                    && !request.settingValue().equals("show-roots")) {
                throw new IllegalArgumentException("root hoist setting must be auto or show-roots");
            }
            if (request.operation()
                    == ca.teamdman.sfm.client.control.generated.SfmControlExplorerOperation.VIEW_SET
                    && !request.settingValue().equals("sfm:list")
                    && !request.settingValue().equals("sfm:small_icons")) {
                throw new IllegalArgumentException("view setting must be sfm:list or sfm:small_icons");
            }
            if (request.operation()
                    == ca.teamdman.sfm.client.control.generated.SfmControlExplorerOperation.SORT_SET
                    && !request.settingValue().equals("sfm:name")
                    && !request.settingValue().equals("sfm:extension")
                    && !request.settingValue().equals("sfm:icon")) {
                throw new IllegalArgumentException("sort setting is not a registered explorer sort id");
            }
            if (request.operation()
                    == ca.teamdman.sfm.client.control.generated.SfmControlExplorerOperation.GROUP_SET
                    && !request.settingValue().equals("sfm:hierarchy")
                    && !request.settingValue().equals("sfm:none")) {
                throw new IllegalArgumentException("group setting must be sfm:hierarchy or sfm:none");
            }
            if (request.ifNoMatch()
                    == ca.teamdman.sfm.client.control.generated.SfmControlExplorerIfNoMatch.OPEN_NEW) {
                if (request.operation()
                        != ca.teamdman.sfm.client.control.generated.SfmControlExplorerOperation.ROOT_ADD) {
                    throw new IllegalArgumentException("open-new is supported only by root add");
                }
                if (selector.isExactIdentity()) {
                    throw new IllegalArgumentException("open-new cannot be combined with an exact explorer id");
                }
            }
            return null;
        } catch (RuntimeException failure) {
            return error(
                    SfmControlErrorCode.INVALID_REQUEST,
                    failure.getMessage() == null ? "invalid explorer request" : failure.getMessage(),
                    false,
                    request.requestId()
            );
        }
    }

    private static void requireBoundedText(String value, int minimumBytes, int maximumBytes, String label) {
        int bytes = value.getBytes(StandardCharsets.UTF_8).length;
        if (bytes < minimumBytes || bytes > maximumBytes) {
            throw new IllegalArgumentException(label + " length is outside the accepted bounds");
        }
        if (value.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(label + " contains a control character");
        }
    }

    static SFMExplorerActionRequest parseExplorerActionRequest(SfmControlExplorerOperationRequest request) {
        SFMEntitySelector selector = SFMEntitySelector.parseCanonical(
                SFMEntitySelector.Domain.EXPLORER,
                request.explorerSelector()
        );
        SFMExplorerActionRequest.Operation operation = switch (request.operation()) {
            case LIST -> new SFMExplorerActionRequest.ListExplorers();
            case DESCRIBE -> new SFMExplorerActionRequest.Describe();
            case ROOT_LIST -> new SFMExplorerActionRequest.RootList();
            case ROOT_ADD -> new SFMExplorerActionRequest.RootAdd(SFMPath.parse(request.canonicalPath()));
            case ROOT_REMOVE -> new SFMExplorerActionRequest.RootRemove(SFMPath.parse(request.canonicalPath()));
            case VIEW_SET -> new SFMExplorerActionRequest.ViewSet(switch (request.settingValue()) {
                case "sfm:list" -> SFMExplorerProjection.View.LIST;
                case "sfm:small_icons" -> SFMExplorerProjection.View.SMALL_ICONS;
                default -> throw new IllegalArgumentException("Unknown explorer view id");
            });
            case SORT_SET -> new SFMExplorerActionRequest.SortSet(switch (request.settingValue()) {
                case "sfm:name" -> SFMExplorerProjection.Sort.NAME;
                case "sfm:extension" -> SFMExplorerProjection.Sort.EXTENSION;
                case "sfm:icon" -> SFMExplorerProjection.Sort.ICON;
                default -> throw new IllegalArgumentException("Unknown explorer sort id");
            });
            case GROUP_SET -> new SFMExplorerActionRequest.GroupSet(switch (request.settingValue()) {
                case "sfm:hierarchy" -> SFMExplorerProjection.Group.HIERARCHY;
                case "sfm:none" -> SFMExplorerProjection.Group.NONE;
                default -> throw new IllegalArgumentException("Unknown explorer group id");
            });
            case ROOT_HOIST_SET -> new SFMExplorerActionRequest.HoistSet(switch (request.settingValue()) {
                case "auto" -> SFMExplorerProjection.Hoist.AUTO;
                case "show-roots" -> SFMExplorerProjection.Hoist.SHOW_ROOTS;
                default -> throw new IllegalArgumentException("Unknown explorer root-hoist mode");
            });
            case NODE_EXPAND -> new SFMExplorerActionRequest.NodeExpand(
                    SFMPath.parse(request.canonicalPath()),
                    SFMExplorerRuntime.DEFAULT_PAGE_SIZE
            );
            case NODE_COLLAPSE -> new SFMExplorerActionRequest.NodeCollapse(
                    SFMPath.parse(request.canonicalPath())
            );
            case NODE_TOGGLE -> new SFMExplorerActionRequest.NodeToggle(
                    SFMPath.parse(request.canonicalPath()),
                    SFMExplorerRuntime.DEFAULT_PAGE_SIZE
            );
            case NODE_REFRESH -> new SFMExplorerActionRequest.NodeRefresh(
                    SFMPath.parse(request.canonicalPath()),
                    SFMExplorerRuntime.DEFAULT_PAGE_SIZE
            );
        };
        return new SFMExplorerActionRequest(
                selector,
                operation,
                request.ifNoMatch()
                        == ca.teamdman.sfm.client.control.generated.SfmControlExplorerIfNoMatch.OPEN_NEW
                        ? SFMExplorerActionRequest.IfNoMatch.OPEN_NEW
                        : SFMExplorerActionRequest.IfNoMatch.FAIL
        );
    }

    private VoxResult<SfmControlExplorerOperationResult, SfmControlError> executeExplorerOperation(
            SfmControlExplorerOperationRequest request
    ) {
        try {
            SFMExplorerRuntime runtime = SFMExplorerRuntime.get();
            Screen origin = minecraft.screen;
            var prepared = runtime.prepare(parseExplorerActionRequest(request));
            SFMExplorerControlBounds.validatePrepared(prepared);
            SFMExplorerActionResult action = runtime.publishAndOpen(
                    prepared,
                    SFMClientActionContext.create(origin, () -> minecraft.screen == origin)
            );
            SFMExplorerRuntime.Evidence evidence = runtime.evidence();
            Map<SFMExplorerId, SFMExplorerRuntime.ExplorerEvidence> evidenceById = new java.util.TreeMap<>(
                    java.util.Comparator.comparing(SFMExplorerId::value)
            );
            evidence.explorers().forEach(item -> evidenceById.put(item.session().id(), item));

            ArrayList<SfmControlExplorerTargetResult> targets = new ArrayList<>();
            SFMExplorerControlBounds.VisiblePathBudget visiblePathBudget =
                    SFMExplorerControlBounds.visiblePathBudget();
            long changed = 0;
            long opened = 0;
            for (SFMExplorerActionResult.TargetResult target : action.targets()) {
                SFMExplorerRuntime.ExplorerEvidence current = evidenceById.get(target.explorerId());
                var snapshot = current == null ? target.snapshot() : current.session();
                List<String> roots = snapshot.roots().stream().map(SFMPath::canonical).toList();
                SFMExplorerProjection.Result projection = current == null ? null : current.projection();
                List<String> visiblePaths = projection == null
                        ? List.of()
                        : projection.rows().stream()
                                .map(row -> row.path().canonical())
                                .filter(visiblePathBudget::tryInclude)
                                .limit(MAX_EXPLORER_VISIBLE_PATHS)
                                .toList();
                long visibleRows = visiblePaths.size();
                long pending = current == null ? 0 : current.activeRequests().size();
                long diagnosticCount = target.diagnostics().size()
                        + (projection == null ? 0 : projection.diagnostics().size());
                SfmControlExplorerTargetOutcome outcome = switch (target.outcome()) {
                    case DESCRIBED -> SfmControlExplorerTargetOutcome.OBSERVED;
                    case CREATED -> SfmControlExplorerTargetOutcome.CREATED;
                    case APPLIED, REFRESH_REQUESTED -> SfmControlExplorerTargetOutcome.CHANGED;
                    case UNCHANGED -> SfmControlExplorerTargetOutcome.UNCHANGED;
                    case REJECTED, STALE -> SfmControlExplorerTargetOutcome.REJECTED;
                };
                if (outcome == SfmControlExplorerTargetOutcome.CREATED) opened++;
                if (outcome == SfmControlExplorerTargetOutcome.CREATED
                        || outcome == SfmControlExplorerTargetOutcome.CHANGED) changed++;
                Optional<Long> selectionRevision = target.after().locationSelectionRevision();
                Optional<Long> relationRequestId = target.loadRequest()
                        .map(ca.teamdman.sfm.client.explorer.lazy.SFMLazyExplorerLoader.RequestEvidence::relationRequestId);
                targets.add(new SfmControlExplorerTargetResult(
                        target.explorerId().value(),
                        outcome,
                        SFMExplorerControlBounds.boundedEvidenceText(
                                target.diagnostics().isEmpty()
                                        ? target.outcome().name().toLowerCase(java.util.Locale.ROOT)
                                        : target.diagnostics().get(0),
                                SFMExplorerControlBounds.MAX_TARGET_MESSAGE_BYTES
                        ),
                        target.after().sessionRevision(),
                        selectionRevision.isPresent(),
                        selectionRevision.orElse(0L),
                        true,
                        target.after().relationRevision(),
                        relationRequestId.isPresent(),
                        relationRequestId.orElse(0L),
                        current != null && current.focused(),
                        snapshot.location().canonical(),
                        roots,
                        viewId(snapshot.settings().view()),
                        sortId(snapshot.settings().sort()),
                        groupId(snapshot.settings().group()),
                        hoistId(snapshot.settings().hoist()),
                        visiblePaths,
                        visibleRows,
                        pending,
                        diagnosticCount
                ));
            }

            Optional<Long> selectionRevision = action.targets().stream()
                    .map(target -> target.after().locationSelectionRevision())
                    .flatMap(Optional::stream)
                    .max(Long::compareTo);
            Optional<Long> relationRevision = action.targets().stream()
                    .map(target -> target.after().relationRevision())
                    .max(Long::compareTo);
            ArrayList<String> feedback = new ArrayList<>(action.diagnostics());
            action.targets().forEach(target -> feedback.addAll(target.diagnostics()));
            if (feedback.size() > MAX_FEEDBACK_ENTRIES) {
                feedback.subList(MAX_FEEDBACK_ENTRIES, feedback.size()).clear();
            }
            feedback.replaceAll(value -> SFMExplorerControlBounds.boundedEvidenceText(
                    value,
                    SFMExplorerControlBounds.MAX_FEEDBACK_MESSAGE_BYTES
            ));
            Screen resultingScreen = minecraft.screen;
            SFMScreenMultiplexer workspace = resultingScreen instanceof SFMScreenMultiplexer multiplexer
                    ? multiplexer
                    : null;
            return VoxResult.success(new SfmControlExplorerOperationResult(
                    instanceId,
                    processId,
                    request.requestId(),
                    request.operation(),
                    request.explorerSelector(),
                    request.canonicalPath(),
                    request.settingValue(),
                    request.ifNoMatch(),
                    operationStatus(action, request.operation()),
                    targets.size(),
                    action.selectorResolution().identities().size(),
                    changed,
                    opened,
                    selectionRevision.isPresent(),
                    selectionRevision.orElse(0L),
                    relationRevision.isPresent(),
                    relationRevision.orElse(0L),
                    targets,
                    feedback,
                    resultingScreen != null,
                    resultingScreen == null
                            ? ""
                            : SFMExplorerControlBounds.boundedEvidenceText(resultingScreen.getClass().getName()),
                    workspace != null,
                    workspace == null ? 0 : workspace.panels().size()
            ));
        } catch (RuntimeException failure) {
            SFM.LOGGER.error("SFM_CLIENT_CONTROL_EXPLORER_FAILED operation={}", request.operation(), failure);
            return VoxResult.applicationError(error(
                    SfmControlErrorCode.ACTION_FAILED,
                    failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage(),
                    false,
                    request.requestId()
            ));
        }
    }

    private static SfmControlExplorerOperationStatus operationStatus(
            SFMExplorerActionResult action,
            ca.teamdman.sfm.client.control.generated.SfmControlExplorerOperation operation
    ) {
        return switch (action.status()) {
            case NO_TARGETS -> SfmControlExplorerOperationStatus.NO_MATCH;
            case REJECTED, STALE -> SfmControlExplorerOperationStatus.REJECTED;
            case SUCCEEDED -> {
                boolean observed = operation == ca.teamdman.sfm.client.control.generated.SfmControlExplorerOperation.LIST
                        || operation == ca.teamdman.sfm.client.control.generated.SfmControlExplorerOperation.DESCRIBE
                        || operation == ca.teamdman.sfm.client.control.generated.SfmControlExplorerOperation.ROOT_LIST;
                if (observed) yield SfmControlExplorerOperationStatus.OBSERVED;
                boolean unchanged = action.targets().stream().allMatch(target ->
                        target.outcome() == SFMExplorerActionResult.TargetOutcome.UNCHANGED);
                yield unchanged
                        ? SfmControlExplorerOperationStatus.UNCHANGED
                        : SfmControlExplorerOperationStatus.APPLIED;
            }
        };
    }

    private static String viewId(SFMExplorerProjection.View view) {
        return view == SFMExplorerProjection.View.LIST ? "sfm:list" : "sfm:small_icons";
    }

    private static String sortId(SFMExplorerProjection.Sort sort) {
        return "sfm:" + sort.name().toLowerCase(java.util.Locale.ROOT);
    }

    private static String groupId(SFMExplorerProjection.Group group) {
        return "sfm:" + group.name().toLowerCase(java.util.Locale.ROOT);
    }

    private static String hoistId(SFMExplorerProjection.Hoist hoist) {
        return hoist.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
    }

    private VoxResult<SfmControlInvokeClientActionResult, SfmControlError> executeClientAction(
            String canonicalAction,
            String requestId
    ) {
        Screen origin = minecraft.screen;
        List<String> feedback = new ArrayList<>();
        int result;
        try {
            result = SFMClientActionExecutor.execute(
                    canonicalAction,
                    SFMClientActionContext.create(origin, () -> minecraft.screen == origin),
                    component -> {
                        if (feedback.size() < MAX_FEEDBACK_ENTRIES) {
                            feedback.add(component.getString());
                        }
                    }
            );
        } catch (CommandSyntaxException failure) {
            SfmControlErrorCode code = failure.getCursor() >= canonicalAction.length()
                    ? SfmControlErrorCode.ACTION_INCOMPLETE
                    : SfmControlErrorCode.ACTION_UNKNOWN;
            return VoxResult.applicationError(error(code, failure.getMessage(), false, requestId));
        } catch (RuntimeException failure) {
            SFM.LOGGER.error("SFM_CLIENT_CONTROL_ACTION_FAILED action={}", canonicalAction, failure);
            return VoxResult.applicationError(error(
                    SfmControlErrorCode.ACTION_FAILED,
                    failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage(),
                    false,
                    requestId
            ));
        }

        Screen resultingScreen = minecraft.screen;
        SFMScreenMultiplexer workspace = resultingScreen instanceof SFMScreenMultiplexer multiplexer
                ? multiplexer
                : null;
        return VoxResult.success(new SfmControlInvokeClientActionResult(
                instanceId,
                processId,
                requestId,
                canonicalAction,
                result,
                feedback,
                resultingScreen != null,
                resultingScreen == null ? "" : resultingScreen.getClass().getName(),
                workspace != null,
                workspace == null ? 0 : workspace.panels().size()
        ));
    }

    private SfmControlError mapClientThreadFailure(Throwable failure, String requestId) {
        Throwable cause = unwrapCompletionFailure(failure);
        if (cause instanceof SFMClientThreadGate.CapacityExceededException) {
            return error(SfmControlErrorCode.CAPACITY_EXCEEDED, cause.getMessage(), true, requestId);
        }
        if (cause instanceof SFMClientThreadGate.ClientUnavailableException) {
            return error(SfmControlErrorCode.CLIENT_UNAVAILABLE, cause.getMessage(), true, requestId);
        }
        if (cause instanceof SFMClientThreadGate.ShuttingDownException) {
            return error(SfmControlErrorCode.SHUTTING_DOWN, cause.getMessage(), true, requestId);
        }
        if (cause instanceof CancellationException) {
            return error(SfmControlErrorCode.CANCELLED, "client action was cancelled", true, requestId);
        }
        SFM.LOGGER.error("SFM_CLIENT_CONTROL_ACTION_HANDOFF_FAILED", cause);
        return error(
                SfmControlErrorCode.INTERNAL,
                cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage(),
                false,
                requestId
        );
    }

    private static Throwable unwrapCompletionFailure(Throwable failure) {
        Throwable current = failure;
        while ((current instanceof CompletionException || current instanceof java.util.concurrent.ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private SfmControlError validateRequest(String token, int protocolVersion, String requestId) {
        if (closed.get() || lifecycle == SfmControlLifecycle.STOPPING) {
            return error(SfmControlErrorCode.SHUTTING_DOWN, "Minecraft client is stopping", true, requestId);
        }
        if (!MessageDigest.isEqual(
                authenticationToken.getBytes(StandardCharsets.UTF_8),
                token.getBytes(StandardCharsets.UTF_8)
        )) {
            return error(SfmControlErrorCode.AUTHENTICATION_FAILED, "authentication failed", false, requestId);
        }
        if (protocolVersion != PROTOCOL_VERSION) {
            return error(
                    SfmControlErrorCode.PROTOCOL_MISMATCH,
                    "unsupported protocol version " + protocolVersion,
                    false,
                    requestId
            );
        }
        return null;
    }

    private void publishDescriptor() throws IOException {
        Files.createDirectories(descriptorPath.getParent());
        JsonObject descriptor = new JsonObject();
        descriptor.addProperty("schema", DESCRIPTOR_SCHEMA);
        descriptor.addProperty("protocol_version", PROTOCOL_VERSION);
        descriptor.addProperty("instance_id", instanceId);
        descriptor.addProperty("process_id", processId);
        descriptor.addProperty("process_start_nonce", processStartNonce);
        descriptor.addProperty("transport", "tcp");
        descriptor.addProperty("host", HOST);
        descriptor.addProperty("port", listener.getLocalPort());
        descriptor.addProperty("authentication_token", authenticationToken);
        descriptor.addProperty("created_at_epoch_millis", createdAtEpochMillis);

        Path temporary = descriptorPath.resolveSibling(descriptorPath.getFileName() + ".tmp-" + randomHex(8));
        try {
            Files.writeString(
                    temporary,
                    new GsonBuilder().setPrettyPrinting().create().toJson(descriptor),
                    StandardCharsets.UTF_8
            );
            try {
                Files.move(
                        temporary,
                        descriptorPath,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING
                );
            } catch (AtomicMoveNotSupportedException failure) {
                throw new IOException("instance descriptor directory does not support atomic publication", failure);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static Path descriptorDirectory() {
        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData != null && !localAppData.isBlank()) {
            return Path.of(localAppData, "teamdman", "sfm", "instances");
        }
        String xdgDataHome = System.getenv("XDG_DATA_HOME");
        if (xdgDataHome != null && !xdgDataHome.isBlank()) {
            return Path.of(xdgDataHome, "teamdman", "sfm", "instances");
        }
        String home = System.getProperty("user.home");
        if (System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("mac")) {
            return Path.of(home, "Library", "Application Support", "teamdman", "sfm", "instances");
        }
        return Path.of(home, ".local", "share", "teamdman", "sfm", "instances");
    }

    private static String sfmVersion() {
        return ModList.get()
                .getModContainerById(SFM.MOD_ID)
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse("unknown");
    }

    private static SfmControlError error(
            SfmControlErrorCode code,
            String message,
            boolean retryable,
            String requestId
    ) {
        return new SfmControlError(code, message, retryable, requestId == null ? "" : requestId);
    }

    private static <T> CompletableFuture<VoxResult<T, SfmControlError>> completedError(SfmControlError error) {
        return CompletableFuture.completedFuture(VoxResult.applicationError(error));
    }

    private static String randomHex(int bytes) {
        byte[] random = new byte[bytes];
        SECURE_RANDOM.nextBytes(random);
        return java.util.HexFormat.of().formatHex(random);
    }

    /**
     * Preserves literal action ids such as {@code sfm:panel/open} while still
     * making one OS argument containing whitespace safe for Brigadier.
     */
    static String escapeCommandToken(String token) {
        boolean requiresQuoting = token.chars().anyMatch(Character::isWhitespace)
                || token.indexOf('"') >= 0
                || token.indexOf('\\') >= 0;
        return requiresQuoting ? StringArgumentType.escapeIfRequired(token) : token;
    }

    private static Thread daemonThread(Runnable runnable, String name) {
        Thread thread = new Thread(runnable, name);
        thread.setDaemon(true);
        return thread;
    }

    private static void closeQuietly(Socket socket) {
        if (socket == null) {
            return;
        }
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        lifecycle = SfmControlLifecycle.STOPPING;
        try {
            listener.close();
        } catch (IOException ignored) {
        }
        connections.forEach(VoxConnection::close);
        connections.clear();
        clientThreadGate.close();
        connectionsExecutor.shutdownNow();
        controlExecutor.shutdown();
        try {
            Files.deleteIfExists(descriptorPath);
        } catch (IOException failure) {
            SFM.LOGGER.warn("SFM_CLIENT_CONTROL_DESCRIPTOR_DELETE_FAILED path={}", descriptorPath, failure);
        }
    }

    private record ClientSnapshot(
            boolean focused,
            boolean focusSeen,
            long lastFocusEpochMillis,
            boolean screenPresent,
            String screenClass,
            boolean worldPresent,
            String worldLabel
    ) {
        private static ClientSnapshot initial() {
            return new ClientSnapshot(false, false, 0, false, "", false, "");
        }
    }
}
