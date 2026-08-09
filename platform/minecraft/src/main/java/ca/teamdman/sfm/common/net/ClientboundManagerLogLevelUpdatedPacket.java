package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.containermenu.ManagerContainerMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.FriendlyByteBuf;

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
                FriendlyByteBuf friendlyByteBuf
        ) {
            friendlyByteBuf.writeVarInt(msg.windowId());
            friendlyByteBuf.writeUtf(msg.logLevel(), ServerboundManagerSetLogLevelPacket.MAX_LOG_LEVEL_NAME_LENGTH);
        }

        @Override
        public ClientboundManagerLogLevelUpdatedPacket decode(FriendlyByteBuf friendlyByteBuf) {
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
