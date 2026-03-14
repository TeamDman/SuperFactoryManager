package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.registry.SFMTextEditors;
import ca.teamdman.sfm.common.config.SFMConfig;
import ca.teamdman.sfm.common.config.SFMConfigReadWriter;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.FriendlyByteBuf;

public record ClientboundResetTextEditorPreferencesPacket() implements SFMPacket {
    public static class Daddy implements SFMPacketDaddy<ClientboundResetTextEditorPreferencesPacket> {
        @Override
        public PacketDirection getPacketDirection() {

            return PacketDirection.CLIENTBOUND;
        }

        @Override
        public void encode(
                ClientboundResetTextEditorPreferencesPacket msg,
                FriendlyByteBuf friendlyByteBuf
        ) {
            // No data to encode
        }

        @Override
        public ClientboundResetTextEditorPreferencesPacket decode(FriendlyByteBuf friendlyByteBuf) {

            return new ClientboundResetTextEditorPreferencesPacket();
        }

        @SuppressWarnings("OptionalGetWithoutIsPresent")
        @Override
        public void handle(
                ClientboundResetTextEditorPreferencesPacket msg,
                SFMPacketHandlingContext context
        ) {
            // Reset the preferred editor to V1
            String v1EditorId = SFMTextEditors.V1.getId().get().location().toString();
            SFMConfig.CLIENT_TEXT_EDITOR_CONFIG.preferredEditor.set(v1EditorId);

            // Give user feedback
            SFM.LOGGER.info("Reset text editor preferences to V1");
            LocalPlayer player = Minecraft.getInstance().player;
            if (player != null) {
                player.sendSystemMessage(
                        SFMConfigReadWriter.ConfigSyncResult.SUCCESS
                                .component()
                                .withStyle(ChatFormatting.GREEN)
                );
            }
        }

        @Override
        public Class<ClientboundResetTextEditorPreferencesPacket> getPacketClass() {

            return ClientboundResetTextEditorPreferencesPacket.class;
        }

    }

}