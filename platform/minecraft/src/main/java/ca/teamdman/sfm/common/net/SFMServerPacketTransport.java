package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.common.value.SFMValue;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/** Server-thread handoff for validated packet insertion requests. */
public final class SFMServerPacketTransport {
    private SFMServerPacketTransport() {
    }

    public record InsertionRequest(
            ServerPlayer sender,
            SFMPacketInventoryAddress target,
            SFMValue value
    ) {
    }

    public static SFMPacketInventoryInserter.Result receiveInsertionRequest(
            ServerboundPacketInsertionPacket packet,
            @Nullable ServerPlayer sender
    ) {
        if (sender == null || !SFMPacketEffectGate.allowsServerEffects(sender)) {
            return SFMPacketInventoryInserter.Result.EFFECTS_DISABLED;
        }
        return packet.value().currentValue()
                .map(value -> acceptValidatedRequest(new InsertionRequest(sender, packet.target(), value)))
                .orElse(SFMPacketInventoryInserter.Result.UNSUPPORTED_CODEC_VERSION);
    }

    private static SFMPacketInventoryInserter.Result acceptValidatedRequest(InsertionRequest request) {
        MinecraftServer server = request.sender().getServer();
        if (server == null) {
            return SFMPacketInventoryInserter.Result.EFFECTS_DISABLED;
        }
        return SFMPacketInventoryInserter.insert(server, request.target(), request.value());
    }
}
