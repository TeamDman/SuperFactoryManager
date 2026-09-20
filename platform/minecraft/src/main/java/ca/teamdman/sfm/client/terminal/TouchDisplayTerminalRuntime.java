package ca.teamdman.sfm.client.terminal;

import ca.teamdman.sfm.client.action.*;
import ca.teamdman.sfm.client.program.*;
import ca.teamdman.sfm.client.raster.TouchDisplayRasterTextureCache;
import ca.teamdman.sfm.client.registry.SFMClientActions;
import ca.teamdman.sfm.common.block.TouchDisplayBlock;
import ca.teamdman.sfm.common.blockentity.ClientManagerBlockEntity;
import ca.teamdman.sfm.common.blockentity.ClientManagerLoadedRegistry;
import ca.teamdman.sfm.common.blockentity.TouchDisplayBlockEntity;
import ca.teamdman.sfm.common.event_bus.SFMSubscribeEvent;
import ca.teamdman.sfm.common.util.SFMDist;
import ca.teamdman.sfm.common.value.SFMValue;
import ca.teamdman.sfml.ast.ProgramExecutionSide;
import ca.teamdman.sfml.program_builder.ProgramBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;

import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Local session selection and lifetime. Programs can observe bindings, never create sessions or grant input. */
public final class TouchDisplayTerminalRuntime {
    public static final String WORKER_EXECUTABLE_PROPERTY = "sfm.terminal.workerExecutable";
    private static final Map<UUID, OwnedSession> SESSIONS = new LinkedHashMap<>();
    private static final Map<TouchDisplayTerminalBinding, Mounted> MOUNTS = new LinkedHashMap<>();
    private static final Map<ClientProgramIdentity, ClientProgramActionManifest> MANIFESTS = new HashMap<>();
    private static final ExecutorService PREPARATION = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(1), task -> { Thread thread = new Thread(task, "sfm-terminal-mount-prepare"); thread.setDaemon(true); return thread; },
            new ThreadPoolExecutor.AbortPolicy());
    private static TouchDisplayTerminalBroker broker;
    private static Level world;
    private static boolean preparing;
    private TouchDisplayTerminalRuntime() {}

    public record SessionView(UUID id, ClientProgramIdentity owner, TouchDisplayTerminalServiceTransport.Mode mode, boolean ready) {}
    private record OwnedSession(UUID id, TouchDisplayTerminalBroker.Session session, TouchDisplayTerminalServiceTransport transport) {}
    private static final class Mounted {
        final TouchDisplayTerminalBinding binding;
        final OwnedSession session;
        final TouchDisplayTerminalBroker.Mount mount;
        final TouchDisplayTerminalRasterTarget target;
        ClientProgramInboxReadSurface inbox;
        TouchDisplayTerminalInputQueue queue;
        TouchDisplayTerminalBroker.InputLease input;
        Mounted(TouchDisplayTerminalBinding binding, OwnedSession session, TouchDisplayTerminalBroker.Mount mount, TouchDisplayTerminalRasterTarget target) {
            this.binding = binding; this.session = session; this.mount = mount; this.target = target;
        }
        void closeInbox() {
            var owned = inbox;
            inbox = null; input = null;
            if (owned != null) owned.close();
        }
    }

    private static TouchDisplayTerminalBroker broker() {
        requireThread(); observeWorld();
        if (broker == null) broker = new TouchDisplayTerminalBroker(TouchDisplayTerminalRasterTarget.livePermissions());
        return broker;
    }

    /** Bounded discovery of static declarations, including programs awaiting consent. */
    public static List<TouchDisplayTerminalBinding> declarations() {
        requireThread(); observeWorld();
        if (world == null) return List.of();
        List<TouchDisplayTerminalBinding> result = new ArrayList<>();
        int inspected = 0;
        for (ClientManagerBlockEntity manager : ClientManagerLoadedRegistry.snapshot(world)) {
            if (++inspected > ClientManagerFrameRuntime.MAX_MANAGERS_PER_FRAME) break;
            var identity = ClientManagerFrameRuntime.identityFor(manager);
            if (identity.isEmpty()) continue;
            manifest(identity.orElseThrow()).ifPresent(manifest -> {
                for (var scope : manifest.scopes().getOrDefault(SFMTerminalDisplayAction.DISPLAY, Set.of())) {
                    if (scope.permission().equals(TouchDisplayTerminalBroker.READ) && result.size() < 64) {
                        result.add(TouchDisplayTerminalBinding.parse(identity.orElseThrow(), scope.subject()));
                    }
                }
            });
        }
        return result.stream().distinct().sorted(Comparator.comparing(binding -> binding.owner().managerPosition().toShortString()
                + ":" + binding.display().toShortString() + ":" + binding.channel())).toList();
    }

    public static List<SessionView> sessions() {
        requireThread();
        return SESSIONS.values().stream().filter(owned -> !owned.session.closed())
                .map(owned -> new SessionView(owned.id, owned.session.owner(), owned.transport.mode(), owned.transport.ready())).toList();
    }

    /** Explicit human/test creation; a prepared transport has not yet created a PTY or executed the worker. */
    public static Optional<UUID> create(TouchDisplayTerminalBinding binding, TouchDisplayTerminalServiceTransport transport) {
        requireThread();
        TouchDisplayTerminalBroker current = broker();
        if (!declared(binding, false) || !allowed(binding, TouchDisplayTerminalBroker.SESSION)
                || !allowed(binding, TouchDisplayTerminalBroker.READ) || MOUNTS.containsKey(binding)) {
            transport.close(); return Optional.empty();
        }
        var display = display(binding);
        if (display.isEmpty()) { transport.close(); return Optional.empty(); }
        var target = TouchDisplayTerminalRasterTarget.open(display.orElseThrow(), binding.owner());
        if (target.isEmpty()) { transport.close(); return Optional.empty(); }
        TouchDisplayTerminalBroker.Session created = null;
        boolean transferred = false;
        RuntimeException failure = null;
        try {
            created = current.create(binding.owner(), () -> transport).orElse(null);
            if (created == null) return Optional.empty();
            var session = new OwnedSession(UUID.randomUUID(), created, transport);
            var mounted = current.mount(created, target.orElseThrow());
            if (mounted.isEmpty()) return Optional.empty();
            SESSIONS.put(session.id, session);
            MOUNTS.put(binding, new Mounted(binding, session, mounted.orElseThrow(), target.orElseThrow()));
            transferred = true;
            return Optional.of(session.id);
        } catch (RuntimeException error) {
            failure = error;
            throw error;
        } finally {
            if (!transferred) {
                var abandoned = created;
                Runnable closeSession = () -> { if (abandoned != null) current.close(abandoned); };
                if (failure == null) TouchDisplayTerminalCleanup.runAll(target.orElseThrow()::close, closeSession, transport::close);
                else TouchDisplayTerminalCleanup.suppressOn(failure, target.orElseThrow()::close, closeSession, transport::close);
            }
        }
    }

    /** Connects another display to an existing SFM-owned session, never an arbitrary external PTY. */
    public static boolean connect(TouchDisplayTerminalBinding binding, UUID selected) {
        requireThread(); broker();
        var session = SESSIONS.get(selected);
        if (session == null || !session.session.owner().equals(binding.owner()) || !declared(binding, false)
                || MOUNTS.containsKey(binding) || !allowed(binding, TouchDisplayTerminalBroker.READ)) return false;
        var display = display(binding);
        if (display.isEmpty()) return false;
        var target = TouchDisplayTerminalRasterTarget.open(display.orElseThrow(), binding.owner());
        if (target.isEmpty()) return false;
        boolean transferred = false;
        RuntimeException failure = null;
        try {
            var mount = broker.mount(session.session, target.orElseThrow());
            if (mount.isEmpty()) return false;
            MOUNTS.put(binding, new Mounted(binding, session, mount.orElseThrow(), target.orElseThrow()));
            transferred = true;
            return true;
        } catch (RuntimeException error) {
            failure = error;
            throw error;
        } finally {
            if (!transferred) {
                if (failure == null) target.orElseThrow().close();
                else TouchDisplayTerminalCleanup.suppressOn(failure, target.orElseThrow()::close);
            }
        }
    }

    public static boolean enableInput(TouchDisplayTerminalBinding binding) {
        requireThread();
        Mounted mounted = MOUNTS.get(binding);
        Minecraft minecraft = Minecraft.getInstance();
        if (mounted == null || mounted.input != null || minecraft.player == null || !binding.input()
                || !declared(binding, true) || !allowed(binding, TouchDisplayTerminalBroker.INPUT)
                || !allowed(binding, ClientProgramInboxReadSurface.READ)) return false;
        var lease = broker.acquireInput(mounted.mount);
        if (lease.isEmpty()) return false;
        mounted.input = lease.orElseThrow();
        mounted.inbox = new ClientProgramInboxReadSurface(binding.owner(), ClientManagerFrameRuntime.consent(),
                ClientManagerFrameRuntime::policyBlockers, minecraft.player.getUUID(), Set.of(binding.channel()),
                ClientProgramInboxReadSurface.minecraftInbox(binding.owner()));
        mounted.queue = new TouchDisplayTerminalInputQueue(binding, cursor -> mounted.inbox.page(binding.channel(), cursor, 1).page());
        pumpInput(mounted); // Establish the future-events-only cursor now, without executing old touches.
        return true;
    }

    /** Display/status actions call only this read; no creation, subscription, input, or budget charge occurs here. */
    public static SFMValue status(TouchDisplayTerminalBinding binding) {
        requireThread();
        Mounted mounted = MOUNTS.get(binding);
        if (mounted != null && (mounted.mount.closed() || !mounted.target.current())) mounted = null;
        var queue = mounted == null ? null : mounted.queue;
        return SFMValue.object(Map.of("status", SFMValue.of(mounted == null ? "not_mounted" : mounted.session.transport.ready() ? "mounted" : "waiting"),
                "session", SFMValue.of(mounted == null ? "" : mounted.session.id.toString()),
                "ready", SFMValue.of(mounted != null && mounted.session.transport.ready()),
                "input", SFMValue.of(mounted != null && mounted.input != null),
                "attempted", SFMValue.of(queue == null ? 0 : queue.attempted()), "rejected", SFMValue.of(queue == null ? 0 : queue.rejected()),
                "acknowledged", SFMValue.of(mounted == null ? 0 : mounted.session.transport.acknowledgedTouches()),
                "inputState", SFMValue.of(queue == null ? "disabled" : queue.status())));
    }

    public static Optional<TouchDisplayRasterTextureCache.TextureInfo> textureInfo(TouchDisplayTerminalBinding binding) {
        requireThread(); var mounted = MOUNTS.get(binding);
        return mounted == null ? Optional.empty() : mounted.target.textureInfo();
    }

    public static void disconnect(TouchDisplayTerminalBinding binding) {
        requireThread(); var mounted = MOUNTS.remove(binding);
        if (mounted == null) return;
        TouchDisplayTerminalCleanup.runAll(mounted::closeInbox, () -> broker.unmount(mounted.mount),
                TouchDisplayTerminalRuntime::retireClosedAndEmptySessions);
    }

    public static void closeSession(UUID id) {
        requireThread();
        var owned = SESSIONS.remove(id);
        if (owned == null) return;
        List<Runnable> cleanup = new ArrayList<>();
        for (var mounted : List.copyOf(MOUNTS.values())) if (mounted.session == owned) {
            MOUNTS.remove(mounted.binding); cleanup.add(mounted::closeInbox);
        }
        cleanup.add(() -> broker.close(owned.session));
        TouchDisplayTerminalCleanup.runAll(cleanup);
    }

    /** Local configuration/file resolution is off-thread; publication rechecks the exact live declaration. */
    public static boolean requestCreate(TouchDisplayTerminalBinding binding, TouchDisplayTerminalServiceTransport.Mode mode, Consumer<String> result) {
        requireThread(); broker();
        if (preparing || !declared(binding, false) || !allowed(binding, TouchDisplayTerminalBroker.SESSION)) return false;
        preparing = true;
        Level expectedWorld = world;
        try {
            PREPARATION.execute(() -> {
                TouchDisplayTerminalServiceTransport prepared = null;
                try {
                    InetSocketAddress endpoint = localEndpoint();
                    prepared = TouchDisplayTerminalServiceTransport.local(endpoint, mode,
                            mode == TouchDisplayTerminalServiceTransport.Mode.STRUCTURED_WORKER ? configuredWorker() : null);
                } catch (Exception failure) { /* Stable user diagnostic below, never exception/path data. */ }
                var finalPrepared = prepared;
                Minecraft.getInstance().execute(() -> {
                    preparing = false;
                    if (Minecraft.getInstance().level != expectedWorld || finalPrepared == null) {
                        if (finalPrepared != null) finalPrepared.close();
                        result.accept("Preparation unavailable. Check the local endpoint and configured worker executable.");
                    } else result.accept(create(binding, finalPrepared).isPresent() ? "Created an SFM-owned terminal session. Input remains disabled."
                            : "Creation rejected: review exact consent, target, and session limits.");
                });
            });
            return true;
        } catch (RejectedExecutionException full) { preparing = false; return false; }
    }

    /** Explicit UI-only helper lifecycle; this shared server is not owned by any individual mount. */
    public static boolean requestStartServer(TouchDisplayTerminalBinding binding, Consumer<String> result) {
        requireThread();
        if (preparing || !declared(binding, false) || !allowed(binding, TouchDisplayTerminalBroker.SESSION)) return false;
        preparing = true;
        try {
            PREPARATION.execute(() -> {
                String message;
                try {
                    var endpoint = localEndpoint();
                    SFMTerminalServiceFactory.startRustServer(endpoint.getHostString() + ":" + endpoint.getPort());
                    message = "Shared local terminal server is ready. Choose Create session next.";
                } catch (Exception failure) { message = "Local terminal server could not start. Check its configured executable and endpoint."; }
                String completed = message;
                Minecraft.getInstance().execute(() -> { preparing = false; result.accept(completed); });
            });
            return true;
        } catch (RejectedExecutionException full) { preparing = false; return false; }
    }

    private static InetSocketAddress localEndpoint() {
        var endpoint = SFMTerminalServiceFactory.configuredEndpoint().orElseThrow();
        if (endpoint.isUnresolved() || !endpoint.getAddress().isLoopbackAddress()) throw new IllegalArgumentException("Loopback endpoint required");
        return endpoint;
    }
    private static Path configuredWorker() {
        String value = System.getProperty(WORKER_EXECUTABLE_PROPERTY, System.getProperty("sfm.controlCliExecutable", "")).trim();
        if (value.startsWith("\\\\?\\UNC\\")) value = "\\\\" + value.substring(8);
        else if (value.startsWith("\\\\?\\")) value = value.substring(4);
        if (value.isEmpty()) throw new IllegalArgumentException("No explicit local worker executable configured");
        return Path.of(value);
    }

    @SFMSubscribeEvent(SFMDist.CLIENT)
    public static void onRenderTick(TickEvent.RenderTickEvent event) { if (event.phase == TickEvent.Phase.START) pump(); }
    @SFMSubscribeEvent(SFMDist.CLIENT)
    public static void onClientTick(TickEvent.ClientTickEvent event) { if (event.phase == TickEvent.Phase.END) pump(); }
    @SFMSubscribeEvent(SFMDist.CLIENT)
    public static void onWorldUnload(LevelEvent.Unload event) {
        if (event.getLevel() == world) Minecraft.getInstance().execute(() -> { if (event.getLevel() == world) reset(); });
    }

    public static void pump() {
        requireThread(); observeWorld();
        if (broker == null) return;
        broker.pump();
        for (Mounted mounted : List.copyOf(MOUNTS.values())) {
            if (mounted.mount.closed()) { MOUNTS.remove(mounted.binding); mounted.closeInbox(); continue; }
            if (mounted.input != null) {
                if (!allowed(mounted.binding, ClientProgramInboxReadSurface.READ)) { closeSession(mounted.session.id); continue; }
                pumpInput(mounted);
            }
        }
        retireClosedAndEmptySessions();
        MANIFESTS.keySet().removeIf(identity -> ClientManagerFrameRuntime.liveIdentityFor(identity).isEmpty());
    }

    private static void pumpInput(Mounted mounted) {
        var display = display(mounted.binding);
        if (display.isEmpty() || mounted.queue == null || mounted.input == null) return;
        mounted.queue.pump(display.orElseThrow().getBlockState().getValue(TouchDisplayBlock.FACING), touch -> {
            var input = SFMValue.object(Map.of("binding", mounted.binding.value(), "u", SFMValue.of(touch.u()), "v", SFMValue.of(touch.v())));
            var manifest = manifest(mounted.binding.owner());
            var result = SFMClientActionAuthorizationService.shared().performProgram(SFMTerminalDisplayAction.INPUT_EFFECT, input,
                    mounted.binding.owner(), ClientManagerFrameRuntime.consent(), ClientManagerFrameRuntime::policyBlockers,
                    (identity, scope) -> manifest.isPresent() && manifest.orElseThrow().permits(SFMTerminalDisplayAction.INPUT_STATUS, scope),
                    () -> mounted.target.current() && mounted.input != null && !mounted.mount.closed(),
                    ignored -> broker.touch(mounted.input, touch.u(), touch.v()));
            return result.localTransportAccepted();
        });
        if (mounted.queue.closed()) closeSession(mounted.session.id);
    }

    private static boolean declared(TouchDisplayTerminalBinding binding, boolean input) {
        return manifest(binding.owner()).map(manifest -> manifest.permits(input ? SFMTerminalDisplayAction.INPUT_STATUS : SFMTerminalDisplayAction.DISPLAY,
                new SFMClientActionDescriptor.DataScope(TouchDisplayTerminalBroker.READ, binding.value()))).orElse(false);
    }
    private static Optional<ClientProgramActionManifest> manifest(ClientProgramIdentity identity) {
        if (ClientManagerFrameRuntime.liveIdentityFor(identity).isEmpty()) return Optional.empty();
        if (MANIFESTS.containsKey(identity)) return Optional.of(MANIFESTS.get(identity));
        if (MANIFESTS.size() >= 128) return Optional.empty();
        var minecraft = Minecraft.getInstance();
        if (!(minecraft.level.getBlockEntity(identity.managerPosition()) instanceof ClientManagerBlockEntity manager)) return Optional.empty();
        var built = new ProgramBuilder(manager.storedSource()).forExecutionSide(ProgramExecutionSide.CLIENT).build();
        if (!built.isBuildSuccessful() || built.program() == null) return Optional.empty();
        var manifest = ClientProgramActionManifest.compile(built.program(), SFMClientActions::programmaticBinding);
        if (!manifest.capabilities().equals(identity.requestedCapabilities())) return Optional.empty();
        MANIFESTS.put(identity, manifest); return Optional.of(manifest);
    }
    private static Optional<TouchDisplayBlockEntity> display(TouchDisplayTerminalBinding binding) {
        var level = Minecraft.getInstance().level;
        return level != null && level.hasChunkAt(binding.display()) && level.getBlockEntity(binding.display()) instanceof TouchDisplayBlockEntity display
                && ClientManagerFrameRuntime.presentationIdentity(display).filter(binding.owner()::equals).isPresent() ? Optional.of(display) : Optional.empty();
    }
    private static boolean allowed(TouchDisplayTerminalBinding binding, ResourceLocation capability) {
        return TouchDisplayTerminalRasterTarget.livePermissions().allowed(binding.owner(), capability);
    }
    private static void retireClosedAndEmptySessions() {
        for (OwnedSession owned : List.copyOf(SESSIONS.values())) if (owned.session.closed()
                || MOUNTS.values().stream().noneMatch(mounted -> mounted.session == owned)) closeSession(owned.id);
    }
    private static void observeWorld() { if (Minecraft.getInstance().level != world) { reset(); world = Minecraft.getInstance().level; } }
    private static void reset() {
        List<Runnable> cleanup = new ArrayList<>();
        for (Mounted mounted : MOUNTS.values()) cleanup.add(mounted::closeInbox);
        var ownedBroker = broker;
        if (ownedBroker != null) cleanup.add(ownedBroker::close);
        MOUNTS.clear(); SESSIONS.clear(); MANIFESTS.clear();
        // Detach stale world references even if a native/service cleanup fails.
        broker = null; world = null;
        TouchDisplayTerminalCleanup.runAll(cleanup);
    }
    private static void requireThread() { if (!Minecraft.getInstance().isSameThread()) throw new IllegalStateException("Terminal mounts require client thread"); }
}
