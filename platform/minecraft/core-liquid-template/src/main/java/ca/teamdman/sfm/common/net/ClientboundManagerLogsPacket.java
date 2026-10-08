package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.common.containermenu.ManagerContainerMenu;
import ca.teamdman.sfm.common.logging.TranslatableLogEvent;
import ca.teamdman.sfm.common.logging.TranslatableLogger;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.FriendlyByteBuf;

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
{% when '1.21', '1.21.1', '26.1.2' %}
import net.minecraft.network.RegistryFriendlyByteBuf;

{% endcase %}
import java.util.Collection;

public record ClientboundManagerLogsPacket(
        int windowId,
        FriendlyByteBuf logsBuf
) implements SFMPacket {
    public static ClientboundManagerLogsPacket drainToCreate(
            int windowId,
            Collection<TranslatableLogEvent> logs
    ) {
        var buf = new FriendlyByteBuf(Unpooled.buffer());
        TranslatableLogger.encodeAndDrain(logs, buf);
        return new ClientboundManagerLogsPacket(windowId, buf);
    }

    public static class Daddy implements SFMPacketDaddy<ClientboundManagerLogsPacket> {
        @Override
        public PacketDirection getPacketDirection() {
            return PacketDirection.CLIENTBOUND;
        }
        @Override
        public void encode(
                ClientboundManagerLogsPacket msg,
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
                FriendlyByteBuf friendlyByteBuf
{% when '1.21', '1.21.1', '26.1.2' %}
                RegistryFriendlyByteBuf friendlyByteBuf
{% endcase %}
        ) {
            friendlyByteBuf.writeVarInt(msg.windowId());
            friendlyByteBuf.writeVarInt(msg.logsBuf.readableBytes());
            friendlyByteBuf.writeBytes(msg.logsBuf, 0, msg.logsBuf.readableBytes()); // !!!IMPORTANT!!!
            // We use this write method specifically to NOT modify the reader index.
            // The encode method may be called multiple times, so we want to ensure it is idempotent.

        }

        @Override
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
        public ClientboundManagerLogsPacket decode(FriendlyByteBuf friendlyByteBuf) {
{% when '1.21', '1.21.1', '26.1.2' %}
        public ClientboundManagerLogsPacket decode(RegistryFriendlyByteBuf friendlyByteBuf) {
{% endcase %}
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

        @Override
        public void handle(
                ClientboundManagerLogsPacket msg,
                SFMPacketHandlingContext context
        ) {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player == null
                || !(player.containerMenu instanceof ManagerContainerMenu menu)
                || menu.containerId != msg.windowId()) {
                // We don't log here because this is a common occurrence when the player closes the menu
//                SFM.LOGGER.error("Invalid logs packet received, ignoring.");
                return;
            }
            var logs = TranslatableLogger.decode(msg.logsBuf);
            menu.logs.addAll(logs);
        }

        @Override
        public Class<ClientboundManagerLogsPacket> getPacketClass() {
            return ClientboundManagerLogsPacket.class;
        }
    }
}
