package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.screen.SFMScreenChangeHelpers;
import ca.teamdman.sfm.client.text_editor.SFMTextEditScreenOpenContext;
import ca.teamdman.sfm.client.text_editor.TextEditScreenContentLanguage;
import ca.teamdman.sfm.common.command.ConfigCommandBehaviourInput;
import ca.teamdman.sfm.common.command.ConfigCommandVariantInput;
import ca.teamdman.sfm.common.config.SFMConfig;
import ca.teamdman.sfm.common.config.SFMConfigReadWriter;
import ca.teamdman.sfm.common.registry.SFMPackets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.FriendlyByteBuf;

public record ClientboundShowConfigScreenPacket(
        ConfigCommandVariantInput variant,

        ConfigCommandBehaviourInput requestingEditMode,

        String serverContent
) implements SFMPacket {

    public static class Daddy implements SFMPacketDaddy<ClientboundShowConfigScreenPacket> {
        public static final int MAX_LENGTH = 20480;

        @Override
        public PacketDirection getPacketDirection() {

            return PacketDirection.CLIENTBOUND;
        }

        @Override
        public void encode(
                ClientboundShowConfigScreenPacket msg,
                FriendlyByteBuf friendlyByteBuf
        ) {

            friendlyByteBuf.writeEnum(msg.variant());
            friendlyByteBuf.writeEnum(msg.requestingEditMode());
            friendlyByteBuf.writeUtf(msg.serverContent, MAX_LENGTH);
        }

        @Override
        public ClientboundShowConfigScreenPacket decode(FriendlyByteBuf friendlyByteBuf) {

            return new ClientboundShowConfigScreenPacket(
                    friendlyByteBuf.readEnum(ConfigCommandVariantInput.class),
                    friendlyByteBuf.readEnum(ConfigCommandBehaviourInput.class),
                    friendlyByteBuf.readUtf(MAX_LENGTH)
            );
        }

        @Override
        public void handle(
                ClientboundShowConfigScreenPacket msg,
                SFMPacketHandlingContext context
        ) {

            // Discover config
            String configTomlString = switch (msg.variant()) {
                case CLIENT -> SFMConfigReadWriter.getConfigToml(SFMConfig.CLIENT_CONFIG_SPEC);
                case SERVER -> msg.serverContent();
                case TEXT -> SFMConfigReadWriter.getConfigToml(SFMConfig.CLIENT_TEXT_EDITOR_CONFIG_SPEC);
            };

            // Ensure discovery was success
            if (configTomlString == null) {
                SFM.LOGGER.error("Unable to get config {} for {}", msg.variant(), msg.requestingEditMode());
                LocalPlayer player = Minecraft.getInstance().player;
                if (player != null) {
                    player.sendSystemMessage(SFMConfigReadWriter.ConfigSyncResult.FAILED_TO_FIND.component());
                }
                return;
            }

            // Line endings, strip carriage returns
            configTomlString = configTomlString.replaceAll("\r", "");


            // Show the screen
            SFMScreenChangeHelpers.showPreferredTextEditScreen(new SFMTextEditScreenOpenContext(
                    configTomlString,
                    TextEditScreenContentLanguage.TOML,
                    (newTextContent) -> {
                        switch (msg.requestingEditMode()) {
                            case SHOW -> {
                                // do nothing
                            }
                            case EDIT -> {
                                // Attempt to update the config
                                SFMConfigReadWriter.ConfigSyncResult result = switch (msg.variant()) {
                                    case CLIENT -> SFMConfigReadWriter.updateClientConfig(newTextContent);
                                    case SERVER -> {
                                        SFMPackets.sendToServer(new ServerboundServerConfigUpdatePacket(newTextContent));
                                        yield null; // the server handles the user feedback
                                    }
                                    case TEXT -> SFMConfigReadWriter.updateTextEditorConfig(newTextContent);
                                };

                                // Give user feedback
                                if (result != null) {
                                    LocalPlayer player = Minecraft.getInstance().player;
                                    if (player != null) {
                                        player.sendSystemMessage(result.component());
                                    }
                                }
                            }
                        };
                    }
            ));
        }

        @Override
        public Class<ClientboundShowConfigScreenPacket> getPacketClass() {

            return ClientboundShowConfigScreenPacket.class;
        }

    }

}
