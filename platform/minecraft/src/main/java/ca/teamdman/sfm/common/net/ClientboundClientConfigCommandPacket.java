package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.screen.SFMScreenChangeHelpers;
import ca.teamdman.sfm.client.screen.TomlEditScreenOpenContext;
import ca.teamdman.sfm.common.command.ConfigCommandBehaviourInput;
import ca.teamdman.sfm.common.config.SFMConfig;
import ca.teamdman.sfm.common.config.SFMConfigReadWriter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.FriendlyByteBuf;

public record ClientboundClientConfigCommandPacket(
        ConfigCommandBehaviourInput requestingEditMode
) implements SFMPacket {
    public static class Daddy implements SFMPacketDaddy<ClientboundClientConfigCommandPacket> {
        @Override
        public PacketDirection getPacketDirection() {
            return PacketDirection.CLIENTBOUND;
        }

        @Override
        public void encode(
                ClientboundClientConfigCommandPacket msg,
                FriendlyByteBuf friendlyByteBuf
        ) {
            friendlyByteBuf.writeEnum(msg.requestingEditMode());
        }

        @Override
        public ClientboundClientConfigCommandPacket decode(FriendlyByteBuf friendlyByteBuf) {
            return new ClientboundClientConfigCommandPacket(
                    friendlyByteBuf.readEnum(ConfigCommandBehaviourInput.class)
            );
        }

        @Override
        public void handle(
                ClientboundClientConfigCommandPacket msg,
                SFMPacketHandlingContext context
        ) {
            String configTomlString = SFMConfigReadWriter.getConfigToml(SFMConfig.CLIENT_CONFIG_SPEC);
            if (configTomlString == null) {
                SFM.LOGGER.error("Unable to get client config");
                return;
            }
            configTomlString = configTomlString.replaceAll("\r", "");
            switch (msg.requestingEditMode()) {
                case SHOW -> SFMScreenChangeHelpers.showTomlEditScreen(new TomlEditScreenOpenContext(
                        configTomlString,
                        $ -> {
                        }
                ));
                case EDIT -> SFMScreenChangeHelpers.showTomlEditScreen(new TomlEditScreenOpenContext(
                        configTomlString,
                        Daddy::handleNewClientConfig
                ));
            }
        }

        @Override
        public Class<ClientboundClientConfigCommandPacket> getPacketClass() {
            return ClientboundClientConfigCommandPacket.class;
        }

        public static void handleNewClientConfig(String newConfigToml) {
            SFMConfigReadWriter.ConfigSyncResult configSyncResult = SFMConfigReadWriter.updateClientConfig(newConfigToml);
            LocalPlayer player = Minecraft.getInstance().player;
            if (player != null) {
                player.sendSystemMessage(configSyncResult.component());
            }
        }
    }
}
