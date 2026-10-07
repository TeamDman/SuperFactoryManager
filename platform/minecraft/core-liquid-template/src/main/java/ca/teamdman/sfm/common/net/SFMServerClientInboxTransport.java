package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.common.event_bus.SFMSubscribeEvent;
import ca.teamdman.sfm.common.registry.registration.SFMPackets;
import ca.teamdman.sfm.common.value.SFMValue;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;

/** Private integrated-world delivery; a successful send is not a receipt acknowledgement. */
public final class SFMServerClientInboxTransport {
    private static final Map<MinecraftServer, SFMServerClientInboxSubscriptions> SUBSCRIPTIONS =
            new WeakHashMap<>();

    private SFMServerClientInboxTransport() {
    }

    public enum Result {
        SENT,
        EFFECTS_DISABLED,
        ADDRESS_REJECTED,
        NOT_SUBSCRIBED,
        PAYLOAD_REJECTED,
        BUDGET_EXHAUSTED
    }

    public static void receiveSubscription(
            ServerboundClientInboxSubscriptionPacket request,
            @Nullable ServerPlayer sender
    ) {
        if (sender == null || !SFMPacketEffectGate.allowsServerEffects(sender)) {
            return;
        }
        MinecraftServer server = sender.getServer();
        if (server == null || !sender.getLevel().dimension().location().equals(request.dimension())) {
            return;
        }
        SFMServerClientInboxSubscriptions state = state(server);
        if (request.subscribe()) {
            state.subscribe(sender.getUUID(), request.session(), request.dimension(), request.channel());
        } else {
            state.unsubscribe(sender.getUUID(), request.session(), request.dimension(), request.channel());
        }
    }

    public static Result publish(
            ServerPlayer recipient,
            SFMClientInboxAddress address,
            SFMValue value
    ) {
        return publish(null, recipient, address, value);
    }

    /** Remote delivery must carry a trusted server Manager as its publisher. */
    public static Result publish(
            @Nullable ca.teamdman.sfm.common.blockentity.ManagerBlockEntity publisher,
            ServerPlayer recipient,
            SFMClientInboxAddress address,
            SFMValue value
    ) {
        Objects.requireNonNull(recipient, "recipient");
        Objects.requireNonNull(address, "address");
        Objects.requireNonNull(value, "value");
        if (!SFMPacketEffectGate.allowsServerEffects(recipient)) {
{% if features.multiplayer_packets %}
            if (publisher == null) return Result.EFFECTS_DISABLED;
            return switch (ca.teamdman.sfm.common.net.multiplayer.SFMMultiplayerServerRuntime.publish(publisher, recipient, address, value)) {
                case DELIVERED_TO_TRANSPORT -> Result.SENT;
                case RATE_LIMITED -> Result.BUDGET_EXHAUSTED;
                case NOT_SUBSCRIBED -> Result.NOT_SUBSCRIBED;
                case FRAME_REJECTED, PAYLOAD_REJECTED, UNSUPPORTED_CODEC -> Result.PAYLOAD_REJECTED;
                default -> Result.ADDRESS_REJECTED;
            };
{% else %}
            return Result.EFFECTS_DISABLED;
{% endif %}
        }
        MinecraftServer server = recipient.getServer();
        if (server == null || !recipient.getUUID().equals(address.recipient())
            || !recipient.getLevel().dimension().location().equals(address.dimension())) {
            return Result.ADDRESS_REJECTED;
        }
        SFMServerClientInboxSubscriptions state = state(server);
        Optional<UUID> session = state.subscribedSession(address);
        if (session.isEmpty()) {
            return Result.NOT_SUBSCRIBED;
        }
        SFMPacketValueEnvelope envelope;
        try {
            envelope = SFMPacketValueEnvelope.fromValue(value);
        } catch (IllegalArgumentException invalidValue) {
            return Result.PAYLOAD_REJECTED;
        }
        int bytes = envelope.canonicalJson().getBytes(StandardCharsets.UTF_8).length;
        if (!state.reserveSend(recipient.getUUID(), recipient.getLevel().getGameTime(), bytes)) {
            return Result.BUDGET_EXHAUSTED;
        }
        SFMPackets.sendToPlayer(recipient, new ClientboundClientInboxValuePacket(session.orElseThrow(), address, envelope));
        return Result.SENT;
    }

    public static boolean isSubscribed(ServerPlayer recipient, SFMClientInboxAddress address) {
        MinecraftServer server = recipient.getServer();
        return server != null && state(server).subscribedSession(address).isPresent();
    }

    @SFMSubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.getServer() != null) {
            state(player.getServer()).remove(player.getUUID());
        }
    }

    @SFMSubscribeEvent
    public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.getServer() != null) {
            state(player.getServer()).remove(player.getUUID());
        }
    }

    private static synchronized SFMServerClientInboxSubscriptions state(MinecraftServer server) {
        return SUBSCRIPTIONS.computeIfAbsent(server, ignored -> new SFMServerClientInboxSubscriptions());
    }
}
