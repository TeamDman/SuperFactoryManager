package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.config.SFMConfigReadWriter;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
import net.minecraft.commands.Commands;
{% when '26.1.2' %}
{% endcase %}
import net.minecraft.network.FriendlyByteBuf;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
{% when '1.21', '1.21.1', '26.1.2' %}
import net.minecraft.network.RegistryFriendlyByteBuf;
{% endcase %}
import net.minecraft.server.level.ServerPlayer;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1' %}
import net.minecraftforge.network.HandshakeMessages;
{% when '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
{% when '26.1.2' %}
import net.minecraft.server.permissions.Permissions;
{% endcase %}

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
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
                FriendlyByteBuf friendlyByteBuf
{% when '1.21', '1.21.1', '26.1.2' %}
                RegistryFriendlyByteBuf friendlyByteBuf
{% endcase %}
        ) {
            friendlyByteBuf.writeUtf(msg.newConfig, MAX_CONFIG_LENGTH);
        }

        @Override
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
        public ServerboundServerConfigUpdatePacket decode(FriendlyByteBuf friendlyByteBuf) {
{% when '1.21', '1.21.1', '26.1.2' %}
        public ServerboundServerConfigUpdatePacket decode(RegistryFriendlyByteBuf friendlyByteBuf) {
{% endcase %}
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
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
            if (!player.hasPermissions(Commands.LEVEL_OWNERS)) {
{% when '26.1.2' %}
            if (!player.permissions().hasPermission(Permissions.COMMANDS_OWNER)) {
{% endcase %}
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
