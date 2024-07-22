package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.containermenu.ManagerContainerMenu;
import ca.teamdman.sfm.common.logging.TranslatableLogEvent;
import ca.teamdman.sfm.common.logging.TranslatableLogger;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;


import java.util.Collection;
import java.util.function.Supplier;

public record ClientboundManagerLogsPacket(
        int windowId,
        FriendlyByteBuf logsBuf
) implements CustomPacketPayload {
    public static final Type<ServerboundManagerProgramPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
            SFM.MOD_ID,
            "clientbound_manager_logs_packet"
    ));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static ClientboundManagerLogsPacket drainToCreate(
            int windowId,
            Collection<TranslatableLogEvent> logs
    ) {
        var buf = new FriendlyByteBuf(Unpooled.buffer());
        TranslatableLogger.encodeAndDrain(logs, buf);
        return new ClientboundManagerLogsPacket(windowId, buf);
    }

    public static void encode(
            ClientboundManagerLogsPacket msg,
            FriendlyByteBuf friendlyByteBuf
    ) {
        friendlyByteBuf.writeVarInt(msg.windowId());
        friendlyByteBuf.writeVarInt(msg.logsBuf.readableBytes());
        friendlyByteBuf.writeBytes(msg.logsBuf, 0, msg.logsBuf.readableBytes()); // !!!IMPORTANT!!!
        // We use this write method specifically to NOT modify the reader index.
        // The encode method may be called multiple times, so we want to ensure it is idempotent.
    }

    public static ClientboundManagerLogsPacket decode(FriendlyByteBuf friendlyByteBuf) {
        int windowId = friendlyByteBuf.readVarInt();

        int size = friendlyByteBuf.readVarInt(); // don't trust readableBytes
        // https://discord.com/channels/313125603924639766/1154167065519861831/1192251649398419506

        FriendlyByteBuf logsBuf = new FriendlyByteBuf(Unpooled.buffer(size));
        friendlyByteBuf.readBytes(logsBuf, size);
        return new ClientboundManagerLogsPacket(
                windowId,
                logsBuf
        );
    }

    public static void handle(
            ClientboundManagerLogsPacket msg,
            IPayloadContext context
    ) {
        msg.handleInner();

    }

    public void handleInner() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null
            || !(player.containerMenu instanceof ManagerContainerMenu menu)
            || menu.containerId != this.windowId()) {
            SFM.LOGGER.error("Invalid logs packet received, ignoring.");
            return;
        }
        var logs = TranslatableLogger.decode(this.logsBuf);
        menu.logs.addAll(logs);
    }
}
