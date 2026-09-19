package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.common.event_bus.SFMSubscribeEvent;
import ca.teamdman.sfm.common.value.SFMValue;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;
import java.util.WeakHashMap;

/** Server-thread handoff for validated packet insertion requests. */
public final class SFMServerPacketTransport {
    private static final int MAX_INSERTIONS_PER_TICK = 32;
    private static final int MAX_INSERTION_BYTES_PER_TICK = 64 * 1024;
    private static final WeakHashMap<MinecraftServer, SFMBoundedEffectBudget> INSERTION_BUDGETS = new WeakHashMap<>();

    private SFMServerPacketTransport() {
    }

    public record InsertionRequest(
            ServerPlayer sender,
            SFMPacketInventoryAddress target,
            SFMValue value,
            int chargedBytes
    ) {
    }

    public static SFMPacketInventoryInserter.Result receiveInsertionRequest(
            ServerboundPacketInsertionPacket packet,
            @Nullable ServerPlayer sender
    ) {
        if (sender == null || !SFMPacketEffectGate.allowsServerEffects(sender)) {
            return SFMPacketInventoryInserter.Result.EFFECTS_DISABLED;
        }
        MinecraftServer server = sender.getServer();
        if (server == null) return SFMPacketInventoryInserter.Result.EFFECTS_DISABLED;
        if (!server.isSameThread()) return SFMPacketInventoryInserter.Result.NOT_SERVER_THREAD;
        if (sender.isSpectator()) return SFMPacketInventoryInserter.Result.TARGET_UNAUTHORIZED;
        return packet.value().currentValue()
                .map(value -> acceptValidatedRequest(new InsertionRequest(
                        sender, packet.target(), value, chargedBytes(packet))))
                .orElse(SFMPacketInventoryInserter.Result.UNSUPPORTED_CODEC_VERSION);
    }

    private static SFMPacketInventoryInserter.Result acceptValidatedRequest(InsertionRequest request) {
        MinecraftServer server = request.sender().getServer();
        if (server == null) {
            return SFMPacketInventoryInserter.Result.EFFECTS_DISABLED;
        }
        // Client consent is never trusted from the wire. Until P8G defines a
        // multiplayer ACL, only the private integrated-world owner may target
        // an exact loaded inventory, and every transport path shares this cap.
        if (!SFMPacketEffectGate.allowsServerEffects(request.sender())) {
            return SFMPacketInventoryInserter.Result.TARGET_UNAUTHORIZED;
        }
        SFMBoundedEffectBudget.Result admission = budgetFor(server).reserve(
                request.sender().getUUID(), server.getTickCount(), request.chargedBytes());
        if (admission != SFMBoundedEffectBudget.Result.ALLOWED) {
            return SFMPacketInventoryInserter.Result.RATE_LIMITED;
        }
        return SFMPacketInventoryInserter.insert(server, request.target(), request.value());
    }

    private static int chargedBytes(ServerboundPacketInsertionPacket packet) {
        Objects.requireNonNull(packet, "packet");
        return packet.value().canonicalJson().getBytes(StandardCharsets.UTF_8).length
               + packet.target().dimension().toString().getBytes(StandardCharsets.UTF_8).length
               + 32; // coordinates, side, framing and envelope overhead
    }

    private static synchronized SFMBoundedEffectBudget budgetFor(MinecraftServer server) {
        return INSERTION_BUDGETS.computeIfAbsent(server,
                ignored -> new SFMBoundedEffectBudget(MAX_INSERTIONS_PER_TICK, MAX_INSERTION_BYTES_PER_TICK));
    }

    @SFMSubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.getServer() != null) {
            forgetPlayer(player.getServer(), player.getUUID());
        }
    }

    @SFMSubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        synchronized (SFMServerPacketTransport.class) {
            INSERTION_BUDGETS.remove(event.getServer());
        }
    }

    private static synchronized void forgetPlayer(MinecraftServer server, UUID playerId) {
        SFMBoundedEffectBudget budget = INSERTION_BUDGETS.get(server);
        if (budget != null) budget.remove(playerId);
    }
}
