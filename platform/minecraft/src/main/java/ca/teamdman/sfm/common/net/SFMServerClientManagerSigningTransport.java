package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.common.blockentity.ClientManagerBlockEntity;
import ca.teamdman.sfm.common.event_bus.SFMSubscribeEvent;
import ca.teamdman.sfm.common.program.signature.*;
import ca.teamdman.sfm.common.registry.registration.SFMPackets;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;

/** Common-side private-world authoring boundary. Declared client capabilities confer no server authority. */
public final class SFMServerClientManagerSigningTransport {
    private static final double MAX_DISTANCE_SQUARED = 8 * 8;
    private static final Map<MinecraftServer, State> STATES = new WeakHashMap<>();
    private static final class State {
        final ClientManagerSigningSession reviews = new ClientManagerSigningSession();
        final ClientManagerSigningAdmission budget = new ClientManagerSigningAdmission();
    }

    private SFMServerClientManagerSigningTransport() { }

    public static void receive(ServerboundClientManagerSigningRequestPacket request, @Nullable ServerPlayer sender) {
        if (sender == null || sender.getServer() == null || !sender.getServer().isSameThread()) return;
        State state = state(sender.getServer());
        long tick = sender.getServer().overworld().getGameTime();
        // Reserve both ingress and worst-case egress. Over-budget traffic is silently dropped;
        // replying to every rejected packet would itself provide an unbounded response path.
        if (!state.budget.reserve(sender.getUUID(), tick,
                request.chargedBytes() + ClientManagerSigningCodec.MAX_ACKNOWLEDGEMENT_BYTES)) return;
        ClientManagerBlockEntity manager = authorizedTarget(sender, request.dimension(), request.position());
        if (manager == null) {
            respond(sender, request.requestId(), request.dimension(), request.position(),
                    ClientManagerSigningState.Status.UNAUTHORIZED, Optional.empty());
            return;
        }
        ClientManagerSigningState.Review result;
        if (request.save().isPresent()) {
            var save = request.save().orElseThrow();
            result = manager.saveForSigning(state.reviews, sender.getUUID(), save.incarnation(), save.revision(),
                    save.source(), request.declaredCapabilities(), tick);
        } else {
            result = manager.reviewForSigning(state.reviews, sender.getUUID(), request.declaredCapabilities(), tick);
        }
        respond(sender, request.requestId(), request.dimension(), request.position(), result.status(), result.acknowledgement());
    }

    public static void receive(ServerboundClientManagerSignaturePacket request, @Nullable ServerPlayer sender) {
        if (sender == null || sender.getServer() == null || !sender.getServer().isSameThread()) return;
        State state = state(sender.getServer());
        long tick = sender.getServer().overworld().getGameTime();
        if (!state.budget.reserve(sender.getUUID(), tick, request.chargedBytes() + 512)) return;
        ClientManagerBlockEntity manager = authorizedTarget(sender, request.dimension(), request.position());
        ClientManagerSigningState.Status result;
        if (manager == null) {
            // Disallow replay after moving away, changing worlds or losing authority.
            state.reviews.take(sender.getUUID(), request.incarnation(), request.challenge(), tick,
                    request.dimension() + "/" + request.position().asLong());
            result = ClientManagerSigningState.Status.UNAUTHORIZED;
        } else {
            result = manager.submitSignature(state.reviews, sender.getUUID(), request.incarnation(), request.revision(),
                    request.challenge(), request.attestation(), tick);
        }
        respond(sender, request.requestId(), request.dimension(), request.position(), result, Optional.empty());
    }

    private static @Nullable ClientManagerBlockEntity authorizedTarget(ServerPlayer sender,
                                                                         ResourceLocation dimension, BlockPos position) {
        var level = sender.getLevel();
        if (!SFMPacketEffectGate.allowsServerEffects(sender) || sender.isSpectator() || !sender.isAlive()
            || !level.dimension().location().equals(dimension) || !level.hasChunkAt(position)
            || sender.distanceToSqr(position.getX() + 0.5, position.getY() + 0.5, position.getZ() + 0.5) > MAX_DISTANCE_SQUARED
            || !level.mayInteract(sender, position)) return null;
        return level.getBlockEntity(position) instanceof ClientManagerBlockEntity manager && !manager.isRemoved()
               ? manager : null;
    }

    private static void respond(ServerPlayer sender, UUID request, ResourceLocation dimension, BlockPos position,
                                ClientManagerSigningState.Status status,
                                Optional<ClientManagerSigningAcknowledgement> acknowledgement) {
        SFMPackets.sendToPlayer(sender, new ClientboundClientManagerSigningResponsePacket(request, dimension, position,
                status, acknowledgement.map(ClientManagerSigningCodec::encodeAcknowledgement)));
    }

    public static synchronized void invalidate(MinecraftServer server, UUID incarnation) {
        State state = STATES.get(server);
        if (state != null) state.reviews.invalidate(incarnation);
    }

    @SFMSubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) forgetReviews(player);
    }

    @SFMSubscribeEvent
    public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) forgetReviews(player);
    }

    @SFMSubscribeEvent
    public static synchronized void onServerStopped(ServerStoppedEvent event) {
        State removed = STATES.remove(event.getServer());
        if (removed != null) {
            removed.reviews.clear();
            removed.budget.clear();
        }
    }

    private static synchronized void forgetReviews(ServerPlayer player) {
        State state = STATES.get(player.getServer());
        if (state != null) state.reviews.forgetPlayer(player.getUUID());
    }

    private static synchronized State state(MinecraftServer server) {
        return STATES.computeIfAbsent(server, ignored -> new State());
    }
}
