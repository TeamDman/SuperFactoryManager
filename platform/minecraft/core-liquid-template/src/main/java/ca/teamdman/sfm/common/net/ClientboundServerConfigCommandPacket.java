package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.client.screen.SFMScreenChangeHelpers;
import ca.teamdman.sfm.client.screen.TomlEditScreenOpenContext;
import ca.teamdman.sfm.common.command.ConfigCommandBehaviourInput;
import ca.teamdman.sfm.common.registry.registration.SFMPackets;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
import net.minecraft.network.FriendlyByteBuf;
{% when '1.21', '1.21.1', '26.1.2' %}
import net.minecraft.network.RegistryFriendlyByteBuf;
{% endcase %}

public record ClientboundServerConfigCommandPacket(
        String configToml,
        ConfigCommandBehaviourInput requestingEditMode
) implements SFMPacket {
    public static final int MAX_LENGTH = 20480;

    public static class Daddy implements SFMPacketDaddy<ClientboundServerConfigCommandPacket> {
        @Override
        public PacketDirection getPacketDirection() {
            return PacketDirection.CLIENTBOUND;
        }

        @Override
        public void encode(
                ClientboundServerConfigCommandPacket msg,
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
                FriendlyByteBuf friendlyByteBuf
{% when '1.21', '1.21.1', '26.1.2' %}
                RegistryFriendlyByteBuf friendlyByteBuf
{% endcase %}
        ) {
            friendlyByteBuf.writeUtf(msg.configToml(), MAX_LENGTH);
            friendlyByteBuf.writeEnum(msg.requestingEditMode());
        }

        @Override
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
        public ClientboundServerConfigCommandPacket decode(FriendlyByteBuf friendlyByteBuf) {
{% when '1.21', '1.21.1', '26.1.2' %}
        public ClientboundServerConfigCommandPacket decode(RegistryFriendlyByteBuf friendlyByteBuf) {
{% endcase %}
            return new ClientboundServerConfigCommandPacket(
                    friendlyByteBuf.readUtf(MAX_LENGTH),
                    friendlyByteBuf.readEnum(ConfigCommandBehaviourInput.class)
            );
        }

        @Override
        public void handle(
                ClientboundServerConfigCommandPacket msg,
                SFMPacketHandlingContext context
        ) {
            String configTomlString = msg.configToml();
            configTomlString = configTomlString.replaceAll("\r", "");
            switch (msg.requestingEditMode()) {
                case SHOW -> SFMScreenChangeHelpers.showTomlEditScreen(new TomlEditScreenOpenContext(
                        configTomlString,
                        $ -> {
                        }
                ));
                case EDIT -> SFMScreenChangeHelpers.showTomlEditScreen(new TomlEditScreenOpenContext(
                        configTomlString,
                        (newContent) -> SFMPackets.sendToServer(new ServerboundServerConfigUpdatePacket(newContent))
                ));
            }
        }

        @Override
        public Class<ClientboundServerConfigCommandPacket> getPacketClass() {
            return ClientboundServerConfigCommandPacket.class;
        }
    }
}
