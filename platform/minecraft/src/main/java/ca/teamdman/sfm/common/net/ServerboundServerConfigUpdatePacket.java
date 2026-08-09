package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.config.SFMConfigReadWriter;
import net.minecraft.commands.Commands;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.HandshakeMessages;

public record ServerboundServerConfigUpdatePacket(
        String newConfig
) implements SFMPacket {
    /**
     * Value chosen to match {@link HandshakeMessages.S2CConfigData#decode(FriendlyByteBuf)}
     */
    public static final int MAX_CONFIG_LENGTH = 32767;
    public static class Daddy implements SFMPacketDaddy<ServerboundServerConfigUpdatePacket> {
        @Override
        public PacketDirection getPacketDirection() {
            return PacketDirection.SERVERBOUND;
        }
        @Override
        public void encode(
                ServerboundServerConfigUpdatePacket msg,
                FriendlyByteBuf friendlyByteBuf
        ) {
            friendlyByteBuf.writeUtf(msg.newConfig, MAX_CONFIG_LENGTH);
        }

        @Override
        public ServerboundServerConfigUpdatePacket decode(FriendlyByteBuf friendlyByteBuf) {
            return new ServerboundServerConfigUpdatePacket(friendlyByteBuf.readUtf(MAX_CONFIG_LENGTH));
        }

        @Override
        public void handle(
                ServerboundServerConfigUpdatePacket msg,
                SFMPacketHandlingContext context
        ) {
            ServerPlayer player = context.sender();
            if (player == null) {
                SFM.LOGGER.error("Received {} from null player", this.getPacketClass().getName());
                return;
            }
            if (!player.hasPermissions(Commands.LEVEL_OWNERS)) {
                SFM.LOGGER.fatal(
                        "Player {} tried to WRITE server config but does not have the necessary permissions, this should never happen o-o",
                        player.getName().getString()
                );
                return;
            }
            SFMConfigReadWriter.ConfigSyncResult result = SFMConfigReadWriter.updateAndSyncServerConfig(msg.newConfig);
            player.sendSystemMessage(result.component());
        }

        @Override
        public Class<ServerboundServerConfigUpdatePacket> getPacketClass() {
            return ServerboundServerConfigUpdatePacket.class;
        }
    }
}
