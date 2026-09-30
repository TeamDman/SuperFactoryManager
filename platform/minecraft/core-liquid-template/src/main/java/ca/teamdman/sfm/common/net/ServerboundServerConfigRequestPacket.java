package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.command.ConfigCommandBehaviourInput;
import ca.teamdman.sfm.common.config.SFMConfig;
import ca.teamdman.sfm.common.config.SFMConfigReadWriter;
import ca.teamdman.sfm.common.registry.registration.SFMPackets;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
import net.minecraft.commands.Commands;
import net.minecraft.network.FriendlyByteBuf;
{% when '1.21', '1.21.1' %}
import net.minecraft.commands.Commands;
import net.minecraft.network.RegistryFriendlyByteBuf;
{% when '26.1.2' %}
import net.minecraft.network.RegistryFriendlyByteBuf;
{% endcase %}
import net.minecraft.server.level.ServerPlayer;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
{% when '26.1.2' %}
import net.minecraft.server.permissions.Permissions;
{% endcase %}

public record ServerboundServerConfigRequestPacket(
        ConfigCommandBehaviourInput requestingEditMode
) implements SFMPacket {
    public static class Daddy implements SFMPacketDaddy<ServerboundServerConfigRequestPacket> {
        @Override
        public PacketDirection getPacketDirection() {
            return PacketDirection.SERVERBOUND;
        }

        @Override
        public void encode(
                ServerboundServerConfigRequestPacket msg,
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
                FriendlyByteBuf friendlyByteBuf
{% when '1.21', '1.21.1', '26.1.2' %}
                RegistryFriendlyByteBuf friendlyByteBuf
{% endcase %}
        ) {
            friendlyByteBuf.writeEnum(msg.requestingEditMode());
        }

        @Override
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
        public ServerboundServerConfigRequestPacket decode(FriendlyByteBuf friendlyByteBuf) {
{% when '1.21', '1.21.1', '26.1.2' %}
        public ServerboundServerConfigRequestPacket decode(RegistryFriendlyByteBuf friendlyByteBuf) {
{% endcase %}
            return new ServerboundServerConfigRequestPacket(friendlyByteBuf.readEnum(ConfigCommandBehaviourInput.class));
        }

        @Override
        public void handle(
                ServerboundServerConfigRequestPacket msg,
                SFMPacketHandlingContext context
        ) {
            ServerPlayer player = context.sender();
            if (player == null) {
                SFM.LOGGER.error("Received {} from null player", this.getPacketClass().getName());
                return;
            }
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
            if (!player.hasPermissions(Commands.LEVEL_OWNERS)
{% when '26.1.2' %}
            if (!player.permissions().hasPermission(Permissions.COMMANDS_OWNER)
{% endcase %}
                && msg.requestingEditMode() == ConfigCommandBehaviourInput.EDIT) {
                SFM.LOGGER.warn(
                        "Player {} tried to request server config for editing but does not have the necessary permissions, this should never happen o-o",
                        player.getName().getString()
                );
                return;
            }
            String configToml = SFMConfigReadWriter.getConfigToml(SFMConfig.SERVER_CONFIG_SPEC);
            if (configToml == null) {
                SFM.LOGGER.warn("Unable to get server config for player {}", player.getName().getString());
                player.sendSystemMessage(SFMConfigReadWriter.ConfigSyncResult.INTERNAL_FAILURE.component());
                return;
            }
            configToml = configToml.replaceAll("(?m)^#", "--");
            configToml = configToml.replaceAll("\r", "");
            SFM.LOGGER.info("Sending config to player: {}", player.getName().getString());
            SFMPackets.sendToPlayer(
                    () -> player,
                    new ClientboundServerConfigCommandPacket(configToml, msg.requestingEditMode())
            );
        }

        @Override
        public Class<ServerboundServerConfigRequestPacket> getPacketClass() {
            return ServerboundServerConfigRequestPacket.class;
        }
    }
}
