package ca.teamdman.sfm.client.control;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.action.SFMClientActionContext;
import ca.teamdman.sfm.client.action.SFMClientActionExecutor;
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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Authenticated loopback Vox service used by the short-lived {@code sfm.exe} client. */
public final class SFMClientControlServer implements AutoCloseable, SfmControlHandler {
    static final int PROTOCOL_VERSION = 1;
    static final int MAX_ACTION_TOKENS = 128;
    static final int MAX_ACTION_TOKEN_BYTES = 4096;
    static final int MAX_FEEDBACK_ENTRIES = 64;
    private static final String DESCRIPTOR_SCHEMA = "sfm.game-instance/1";
    private static final String HOST = "127.0.0.1";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final Minecraft minecraft;
    private final ServerSocket listener;
    private final Thread acceptThread;
    private final ThreadPoolExecutor connectionsExecutor;
    private final Set<VoxConnection> connections = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final Executor clientThreadExecutor;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final String instanceId = UUID.randomUUID().toString();
    private final long processId = ProcessHandle.current().pid();
    private final String processStartNonce = randomHex(16);
    private final String authenticationToken = randomHex(32);
    private final long createdAtEpochMillis = System.currentTimeMillis();
    private final Path descriptorPath;
    private volatile SfmControlLifecycle lifecycle = SfmControlLifecycle.STARTING;
    private volatile boolean focused;
    private volatile boolean focusSeen;
    private volatile long lastFocusEpochMillis;

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
        clientThreadExecutor = new BoundedClientThreadExecutor(minecraft, 32);
        descriptorPath = descriptorDirectory().resolve(instanceId + ".json");
        acceptThread = daemonThread(this::acceptLoop, "sfm-client-control-accept");
    }

    public static SFMClientControlServer start(Minecraft minecraft) throws IOException {
        SFMClientControlServer server = new SFMClientControlServer(minecraft);
        try {
            server.acceptThread.start();
            server.publishDescriptor();
            server.lifecycle = SfmControlLifecycle.READY;
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
        boolean nowFocused = minecraft.isWindowActive();
        if (nowFocused && !focused) {
            focusSeen = true;
            lastFocusEpochMillis = System.currentTimeMillis();
        }
        focused = nowFocused;
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
                    .handlerExecutor(clientThreadExecutor)
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

        Screen screen = minecraft.screen;
        boolean worldPresent = minecraft.level != null;
        String worldLabel = worldPresent ? minecraft.level.dimension().location().toString() : "";
        return CompletableFuture.completedFuture(VoxResult.success(new SfmControlDescribeResult(
                instanceId,
                processId,
                processStartNonce,
                PROTOCOL_VERSION,
                sfmVersion(),
                SharedConstants.getCurrentVersion().getName(),
                lifecycle,
                focused,
                focusSeen,
                lastFocusEpochMillis,
                screen != null,
                screen == null ? "" : screen.getClass().getName(),
                worldPresent,
                worldLabel,
                List.of("instance.describe", "client-action.invoke"),
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
            return completedError(error(code, failure.getMessage(), false, request.requestId()));
        } catch (RuntimeException failure) {
            SFM.LOGGER.error("SFM_CLIENT_CONTROL_ACTION_FAILED action={}", canonicalAction, failure);
            return completedError(error(
                    SfmControlErrorCode.ACTION_FAILED,
                    failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage(),
                    false,
                    request.requestId()
            ));
        }

        Screen resultingScreen = minecraft.screen;
        SFMScreenMultiplexer workspace = resultingScreen instanceof SFMScreenMultiplexer multiplexer
                ? multiplexer
                : null;
        return CompletableFuture.completedFuture(VoxResult.success(new SfmControlInvokeClientActionResult(
                instanceId,
                processId,
                request.requestId(),
                canonicalAction,
                result,
                feedback,
                resultingScreen != null,
                resultingScreen == null ? "" : resultingScreen.getClass().getName(),
                workspace != null,
                workspace == null ? 0 : workspace.panels().size()
        )));
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
        connectionsExecutor.shutdownNow();
        try {
            Files.deleteIfExists(descriptorPath);
        } catch (IOException failure) {
            SFM.LOGGER.warn("SFM_CLIENT_CONTROL_DESCRIPTOR_DELETE_FAILED path={}", descriptorPath, failure);
        }
    }

    private static final class BoundedClientThreadExecutor implements Executor {
        private final Minecraft minecraft;
        private final Semaphore capacity;

        private BoundedClientThreadExecutor(Minecraft minecraft, int maximumPendingTasks) {
            this.minecraft = minecraft;
            this.capacity = new Semaphore(maximumPendingTasks);
        }

        @Override
        public void execute(Runnable command) {
            if (!capacity.tryAcquire()) {
                throw new RejectedExecutionException("Minecraft client-thread queue is full");
            }
            minecraft.execute(() -> {
                try {
                    command.run();
                } finally {
                    capacity.release();
                }
            });
        }
    }
}
