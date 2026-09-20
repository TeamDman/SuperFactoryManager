package ca.teamdman.sfm.common.net.multiplayer;

import ca.teamdman.sfm.common.blockentity.ClientManagerBlockEntity;
import ca.teamdman.sfm.common.blockentity.ClientManagerWorldIdentitySavedData;
import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.event_bus.SFMSubscribeEvent;
import ca.teamdman.sfm.common.net.*;
import ca.teamdman.sfm.common.program.signature.ClientManagerSigningSnapshot;
import ca.teamdman.sfm.common.registry.registration.SFMPackets;
import ca.teamdman.sfm.common.value.SFMValue;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.*;

import static ca.teamdman.sfm.common.net.multiplayer.SFMMultiplayerPacketProtocol.*;

/** Authenticated sender and loaded-world adapter. No request can create an ACL or a connection handle. */
public final class SFMMultiplayerServerRuntime {
    private static final Map<MinecraftServer, State> STATES = new WeakHashMap<>();
    private record CachedProgram(ClientManagerSigningSnapshot snapshot, Optional<ObservedProgram> program) {}
    private static final class Entry {
        final SFMMultiplayerPacketTransport.Connection connection;
        final Map<InboxScope, UUID> inboxEpochs = new HashMap<>();
        Entry(SFMMultiplayerPacketTransport.Connection connection) { this.connection = connection; }
    }
    private static final class State {
        final Map<Object, Entry> entries = new IdentityHashMap<>();
        final Map<ClientManagerBlockEntity, CachedProgram> programs = new WeakHashMap<>();
        final SFMMultiplayerPacketTransport transport;
        final SFMMultiplayerPacketBudgets replies = new SFMMultiplayerPacketBudgets(SFMMultiplayerPacketBudgets.Limits.defaults());
        State(MinecraftServer server) {
            transport = new SFMMultiplayerPacketTransport(SFMMultiplayerPacketPolicySavedData.forServer(server).policy(),
                    new SFMMultiplayerPacketBudgets(SFMMultiplayerPacketBudgets.Limits.defaults()),
                    (player, address) -> observed(server, this, player, address),
                    () -> server.overworld().getGameTime(), System::currentTimeMillis, UUID.randomUUID());
        }
    }
    private SFMMultiplayerServerRuntime() {}

    private static State state(MinecraftServer server) {
        if (!server.isSameThread()) throw new IllegalStateException("Multiplayer packet state requires the server thread");
        return STATES.computeIfAbsent(server, State::new);
    }
    public static SFMMultiplayerPacketTransport.Diagnostics diagnostics(MinecraftServer server) {
        return state(server).transport.diagnostics();
    }
    public static boolean negotiatedPathAvailable(ServerPlayer player) {
        var server = player.getServer();
        if (server == null || !server.isSameThread()) return false;
        var state = state(server);
        var entry = state.entries.get(player.connection);
        return entry != null && state.transport.isNegotiated(entry.connection);
    }

    /** Only authenticated server lifecycle events call this; packet receive never creates a handle. */
    private static void offer(ServerPlayer player) {
        var server = player.getServer();
        if (server == null || SFMPacketEffectGate.allowsServerEffects(player)) return;
        var state = state(server);
        var existing = state.entries.get(player.connection);
        if (existing != null) {
            if (state.transport.isActive(existing.connection)) return;
            state.entries.remove(player.connection);
        }
        state.transport.openConnection(player.getUUID(), player.connection).ifPresent(connection -> {
            state.entries.put(player.connection, new Entry(connection));
            if (!reply(player, state, SFMMultiplayerPacketWire.offer(connection.offer(), ClientManagerWorldIdentitySavedData.forLevel(player.getLevel())))) {
                // A rejected local send must not strand a peer that never received its offer.
                state.entries.remove(player.connection);
                state.transport.close(connection);
            }
        });
    }

    public static void receive(@Nullable ServerPlayer sender, byte[] frame) {
        if (sender == null || sender.getServer() == null || !sender.getServer().isSameThread()) return;
        var state = state(sender.getServer());
        var entry = state.entries.get(sender.connection);
        if (entry == null || !entry.connection.player().equals(sender.getUUID())) return;
        if (!sender.isAlive() || sender.isSpectator()) {
            state.transport.rejectMalformedFrame(entry.connection, frame.length);
            return;
        }
        SFMMultiplayerPacketWire.ClientMessage decoded;
        try { decoded = SFMMultiplayerPacketWire.decodeClient(frame); }
        catch (RuntimeException rejected) { state.transport.rejectMalformedFrame(entry.connection, frame.length); return; }
        if (decoded instanceof SFMMultiplayerPacketWire.Negotiate negotiate) {
            Status result = state.transport.negotiate(entry.connection, negotiate.version(), negotiate.session());
            reply(sender, state, SFMMultiplayerPacketWire.result(new Acknowledgement(entry.connection.session(), 0, result, Optional.empty())));
        } else if (decoded instanceof SFMMultiplayerPacketWire.Insert insert) {
            var result = state.transport.insert(entry.connection, insert.request(), insert.target(), insert.program(), insert.payload(),
                    (target, value) -> {
                        if (!sender.getLevel().dimension().location().equals(target.dimension())
                            || !sender.getLevel().mayInteract(sender, target.position())) return SFMPacketInventoryInserter.Result.TARGET_UNAUTHORIZED;
                        return SFMPacketInventoryInserter.insert(sender.getServer(), target, value);
                    });
            reply(sender, state, SFMMultiplayerPacketWire.result(result));
        } else if (decoded instanceof SFMMultiplayerPacketWire.Subscription subscription) {
            if (!sender.getLevel().dimension().location().equals(subscription.inbox().dimension())) {
                state.transport.rejectMalformedFrame(entry.connection, frame.length);
                return;
            }
            var result = subscription.subscribe()
                    ? state.transport.subscribe(entry.connection, subscription.request(), subscription.recipient(), subscription.inbox(), subscription.program())
                    : state.transport.unsubscribe(entry.connection, subscription.request(), subscription.recipient(), subscription.inbox());
            entry.inboxEpochs.keySet().retainAll(state.transport.subscribedInboxes(entry.connection));
            if (result.status() == Status.SUBSCRIBED) entry.inboxEpochs.put(subscription.inbox(), subscription.localInboxSession());
            if (result.status() == Status.UNSUBSCRIBED) entry.inboxEpochs.remove(subscription.inbox());
            reply(sender, state, SFMMultiplayerPacketWire.result(result));
        }
    }

    /** Publisher identity comes from a real server Manager, never from a client request. */
    public static Status publish(ManagerBlockEntity publisher, ServerPlayer recipient, SFMClientInboxAddress address, SFMValue value) {
        var server = recipient.getServer();
        if (server == null || !server.isSameThread() || publisher.isRemoved() || publisher.getLevel() == null
            || publisher.getLevel().getServer() != server || !publisher.getLevel().hasChunkAt(publisher.getBlockPos())
            || publisher.getLevel().getBlockEntity(publisher.getBlockPos()) != publisher
            || !recipient.getUUID().equals(address.recipient())
            || !recipient.getLevel().dimension().location().equals(address.dimension())) return Status.AUTHORITY_DENIED;
        var state = state(server);
        var entry = state.entries.get(recipient.connection);
        if (entry == null) return Status.NOT_NEGOTIATED;
        var inbox = new InboxScope(address.dimension(), address.channel());
        UUID localEpoch = entry.inboxEpochs.get(inbox);
        if (localEpoch == null) return Status.NOT_SUBSCRIBED;
        if (!publisher.getLevel().dimension().location().equals(inbox.dimension())) return Status.AUTHORITY_DENIED;
        try {
            var envelope = SFMPacketValueEnvelope.fromValue(value);
            byte[] payload = envelope.canonicalJson().getBytes(StandardCharsets.UTF_8);
            byte[] encoded = SFMMultiplayerPacketWire.inbox(entry.connection.session(), localEpoch, address, value);
            var result = state.transport.publish(entry.connection, entry.connection.session(),
                    new DeliveryScope(new ManagerAddress(address.dimension(), publisher.getBlockPos()), inbox), encoded.length,
                    new PayloadSource(envelope.codecVersion(), payload.length, ignored -> payload.clone()),
                    delivery -> reply(recipient, state, encoded));
            entry.inboxEpochs.keySet().retainAll(state.transport.subscribedInboxes(entry.connection));
            return result;
        } catch (RuntimeException rejected) { return Status.PAYLOAD_REJECTED; }
    }

    private static boolean reply(ServerPlayer recipient, State state, byte[] frame) {
        if (!state.replies.reserveAttempt(recipient.getUUID(), recipient.getServer().overworld().getGameTime(), frame.length)) return false;
        SFMPackets.sendToPlayer(recipient, new ClientboundMultiplayerPacket(frame));
        return true;
    }
    private static Optional<ObservedProgram> observed(MinecraftServer server, State state, UUID playerId, ManagerAddress address) {
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player == null || !player.isAlive() || player.isSpectator()
            || !player.getLevel().dimension().location().equals(address.dimension())
            || !player.getLevel().hasChunkAt(address.position())
            || !(player.getLevel().getBlockEntity(address.position()) instanceof ClientManagerBlockEntity manager)
            || manager.isRemoved()) return Optional.empty();
        var snapshot = manager.signingSnapshot();
        if (snapshot == null) return Optional.empty();
        var cached = state.programs.get(manager);
        if (cached != null && cached.snapshot() == snapshot) return cached.program();
        Optional<ObservedProgram> result;
        try {
            var body = snapshot.body();
            var claim = new ProgramClaim(address, snapshot.incarnation(), snapshot.revision(), body.sourceSha256(), body.bindingSha256());
            result = Optional.of(new ObservedProgram(claim, SFMMultiplayerPacketProgramOperations.extract(body.source(), address.dimension())));
        } catch (RuntimeException rejected) { result = Optional.empty(); }
        state.programs.put(manager, new CachedProgram(snapshot, result));
        return result;
    }
    private static void close(ServerPlayer player) {
        var server = player.getServer();
        if (server == null) return;
        var state = STATES.get(server);
        if (state == null) return;
        var removed = state.entries.remove(player.connection);
        if (removed != null) state.transport.close(removed.connection);
    }
    @SFMSubscribeEvent public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) offer(player);
    }
    @SFMSubscribeEvent public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) close(player);
    }
    @SFMSubscribeEvent public static void onDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) { close(player); offer(player); }
    }
    @SFMSubscribeEvent public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) { close(player); offer(player); }
    }
    @SFMSubscribeEvent public static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.getServer().getTickCount() % 20 == 0) {
            event.getServer().getPlayerList().getPlayers().forEach(SFMMultiplayerServerRuntime::offer);
        }
    }
    @SFMSubscribeEvent public static void onStop(ServerStoppingEvent event) { STATES.remove(event.getServer()); }
}
