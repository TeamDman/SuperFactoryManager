package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.containermenu.ManagerContainerMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
import net.minecraft.network.FriendlyByteBuf;
{% when '1.21', '1.21.1', '26.1.2' %}
import net.minecraft.network.RegistryFriendlyByteBuf;
{% endcase %}

public record ClientboundManagerLogLevelUpdatedPacket(
        int windowId,
        String logLevel
) implements SFMPacket {
    public static class Daddy implements SFMPacketDaddy<ClientboundManagerLogLevelUpdatedPacket> {
        @Override
        public PacketDirection getPacketDirection() {
            return PacketDirection.CLIENTBOUND;
        }
        @Override
        public void encode(
                ClientboundManagerLogLevelUpdatedPacket msg,
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
                FriendlyByteBuf friendlyByteBuf
{% when '1.21', '1.21.1', '26.1.2' %}
                RegistryFriendlyByteBuf friendlyByteBuf
{% endcase %}
        ) {
            friendlyByteBuf.writeVarInt(msg.windowId());
            friendlyByteBuf.writeUtf(msg.logLevel(), ServerboundManagerSetLogLevelPacket.MAX_LOG_LEVEL_NAME_LENGTH);
        }

        @Override
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
        public ClientboundManagerLogLevelUpdatedPacket decode(FriendlyByteBuf friendlyByteBuf) {
{% when '1.21', '1.21.1', '26.1.2' %}
        public ClientboundManagerLogLevelUpdatedPacket decode(RegistryFriendlyByteBuf friendlyByteBuf) {
{% endcase %}
            return new ClientboundManagerLogLevelUpdatedPacket(
                    friendlyByteBuf.readVarInt(),
                    friendlyByteBuf.readUtf(ServerboundManagerSetLogLevelPacket.MAX_LOG_LEVEL_NAME_LENGTH)
            );
        }

        @Override
        public void handle(
                ClientboundManagerLogLevelUpdatedPacket msg,
                SFMPacketHandlingContext context
        ) {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player == null
                || !(player.containerMenu instanceof ManagerContainerMenu menu)
                || menu.containerId != msg.windowId()) {
                SFM.LOGGER.error("Invalid log level packet received, ignoring.");
                return;
            }
            menu.logLevel = msg.logLevel;
        }

        @Override
        public Class<ClientboundManagerLogLevelUpdatedPacket> getPacketClass() {
            return ClientboundManagerLogLevelUpdatedPacket.class;
        }
    }
}
