package ca.teamdman.sfm.client.net;

import ca.teamdman.sfm.client.program.ClientManagerFrameRuntime;
import ca.teamdman.sfm.client.program.ClientProgramIdentity;
import ca.teamdman.sfm.client.program.ClientProgramWorldIdentity;
import ca.teamdman.sfm.common.blockentity.ClientManagerBlockEntity;
import ca.teamdman.sfm.common.event_bus.SFMSubscribeEvent;
import ca.teamdman.sfm.common.net.*;
import ca.teamdman.sfm.common.net.multiplayer.*;
import ca.teamdman.sfm.common.registry.registration.SFMPackets;
import ca.teamdman.sfm.common.util.SFMDist;
import ca.teamdman.sfm.common.value.SFMValue;
import net.minecraft.client.Minecraft;
import net.minecraftforge.event.TickEvent;

import java.util.*;

/** Negotiated remote adapter. Legacy private-world packets and their stricter gate remain separate. */
public final class SFMMultiplayerClientRuntime {
    private static final long ORIGIN = System.nanoTime();
    private static final SFMMultiplayerClientSession SESSION = new SFMMultiplayerClientSession(
            () -> (System.nanoTime() - ORIGIN) / 1_000_000);
    private static boolean initialized;
    private static Object connection;
    private static PendingOffer earlyOffer;
    private record PendingOffer(Object connection, SFMMultiplayerPacketWire.SessionOffer offer) {}
    private SFMMultiplayerClientRuntime() {}

    /** Called during physical-client setup, before a login offer can arrive. */
    public static synchronized void initialize() {
        if (initialized) return;
        initialized = true;
        SFMMultiplayerPacketReceivers.setClient((expectedConnection, frame) -> {
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.execute(() -> receive(expectedConnection, frame));
        });
    }

    private static boolean observe() {
        initialize();
        Minecraft minecraft = Minecraft.getInstance();
        if (!minecraft.isSameThread()) return false;
        var listener = minecraft.getConnection();
        Object current = listener == null ? null : listener.getConnection();
        if (current != connection) {
            SESSION.clear();
            earlyOffer = null;
            connection = current;
        }
        if (current == null || SFMClientPacketTransport.effectsAllowed(minecraft)) {
            SESSION.clear();
            earlyOffer = null;
            return false;
        }
        if (minecraft.level == null || minecraft.player == null) return false;
        var server = minecraft.getCurrentServer();
        String endpoint = server == null ? "integrated-published" : server.ip;
        try { SESSION.bind(current, endpoint, minecraft.player.getUUID(), minecraft.level.dimension().location()); }
        catch (RuntimeException invalid) { SESSION.clear(); return false; }
        return true;
    }

    private static void receive(Object expectedConnection, byte[] frame) {
        var minecraft = Minecraft.getInstance();
        var listener = minecraft.getConnection();
        if (!minecraft.isSameThread() || listener == null || listener.getConnection() != expectedConnection
                || SFMClientPacketTransport.effectsAllowed(minecraft)) return;
        SFMMultiplayerPacketWire.ServerMessage message;
        try { message = SFMMultiplayerPacketWire.decodeServer(frame); }
        catch (RuntimeException rejected) { return; }
        boolean ready = observe();
        if (message instanceof SFMMultiplayerPacketWire.SessionOffer offer) {
            if (!ready) { earlyOffer = new PendingOffer(expectedConnection, offer); return; }
            acceptOffer(expectedConnection, offer);
        } else if (ready && message instanceof SFMMultiplayerPacketWire.Result result) {
            SESSION.acceptResult(expectedConnection, result.acknowledgement());
        } else if (ready && message instanceof SFMMultiplayerPacketWire.InboxValue value) {
            SFMClientInboxTransport.observeCurrentWorld();
            var localEpoch = SFMClientInboxRuntime.get().session();
            if (localEpoch.isPresent() && SESSION.acceptsInbox(expectedConnection, value, localEpoch.orElseThrow())) {
                SFMClientInboxRuntime.get().receive(new ClientboundClientInboxValuePacket(
                        value.localInboxSession(), value.address(), value.value()));
            }
        }
    }
    private static void acceptOffer(Object expectedConnection, SFMMultiplayerPacketWire.SessionOffer offer) {
        if (SESSION.acceptOffer(expectedConnection, offer)) SESSION.negotiation().ifPresent(SFMMultiplayerClientRuntime::send);
    }
    private static boolean send(SFMMultiplayerClientSession.Outbound outbound) {
        try {
            SFMPackets.sendToServer(new ServerboundMultiplayerPacket(outbound.frame()));
            return true;
        } catch (RuntimeException failed) {
            SESSION.transportFailed(outbound.sequence());
            return false;
        }
    }
    public static boolean available() { return observe() && SESSION.state() == SFMMultiplayerClientSession.State.READY; }
    public static Optional<UUID> session() { return available() ? SESSION.session() : Optional.empty(); }
    public static Optional<ClientProgramWorldIdentity> worldIdentity(UUID persistedWorldId) {
        return available() ? SESSION.worldIdentity(persistedWorldId) : Optional.empty();
    }
    public static List<SFMMultiplayerClientSession.Receipt> acknowledgements() { observe(); return SESSION.acknowledgements(); }
    public static String diagnostics() { observe(); return SESSION.diagnostic(); }
    public static int pendingAcknowledgements() { observe(); return SESSION.pendingCount(); }

    public static boolean sendInsertion(SFMPacketInventoryAddress target, SFMValue value, Optional<ClientProgramIdentity> caller) {
        if (!available()) return false;
        Optional<SFMMultiplayerPacketProtocol.ProgramClaim> claim = claim(caller);
        if (caller.isPresent() && claim.isEmpty()) return false;
        return SESSION.insertion(target, value, claim).map(SFMMultiplayerClientRuntime::send).orElse(false);
    }

    public static boolean sendSubscription(UUID localInboxSession, SFMClientInboxAddress address,
                                           Optional<ClientProgramIdentity> caller, boolean subscribe) {
        if (!available()) return false;
        Optional<SFMMultiplayerPacketProtocol.ProgramClaim> claim = claim(caller);
        // Releasing an own channel is allowed after its program changed; it never grants authority.
        if (subscribe && caller.isPresent() && claim.isEmpty()) return false;
        return SESSION.subscription(localInboxSession, address, claim, subscribe)
                .map(SFMMultiplayerClientRuntime::send).orElse(false);
    }

    private static Optional<SFMMultiplayerPacketProtocol.ProgramClaim> claim(Optional<ClientProgramIdentity> caller) {
        Objects.requireNonNull(caller);
        if (caller.isEmpty()) return Optional.empty();
        var expected = caller.orElseThrow();
        var minecraft = Minecraft.getInstance();
        if (ClientManagerFrameRuntime.liveIdentityFor(expected).isEmpty() || minecraft.level == null
                || !(minecraft.level.getBlockEntity(expected.managerPosition()) instanceof ClientManagerBlockEntity manager)) return Optional.empty();
        var snapshot = manager.signingSnapshot();
        if (snapshot == null || !snapshot.body().sourceSha256().equals(expected.sourceSha256())) return Optional.empty();
        return Optional.of(SFMMultiplayerClientSession.claimFromSnapshot(expected.dimension(), expected.managerPosition(), snapshot));
    }

    @SFMSubscribeEvent(SFMDist.CLIENT)
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !observe()) return;
        if (earlyOffer != null) {
            PendingOffer offer = earlyOffer;
            earlyOffer = null;
            if (connection == offer.connection()) acceptOffer(offer.connection(), offer.offer());
        }
        SESSION.state(); // Expire abandoned acknowledgements even when no program or UI is polling.
    }
}
