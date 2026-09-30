package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.client.screen.SFMScreenChangeHelpers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
import net.minecraft.network.FriendlyByteBuf;
{% when '1.21', '1.21.1', '26.1.2' %}
import net.minecraft.network.RegistryFriendlyByteBuf;
{% endcase %}

public record ClientboundContainerExportsInspectionResultsPacket(
        int windowId,
        String results
) implements SFMPacket {
    public static final int MAX_RESULTS_LENGTH = 20480;

    public static class Daddy implements SFMPacketDaddy<ClientboundContainerExportsInspectionResultsPacket> {
        @Override
        public PacketDirection getPacketDirection() {
            return PacketDirection.CLIENTBOUND;
        }
        @Override
        public Class<ClientboundContainerExportsInspectionResultsPacket> getPacketClass() {
            return ClientboundContainerExportsInspectionResultsPacket.class;
        }

        @Override
        public void encode(
                ClientboundContainerExportsInspectionResultsPacket msg,
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
                FriendlyByteBuf friendlyByteBuf
{% when '1.21', '1.21.1', '26.1.2' %}
                RegistryFriendlyByteBuf friendlyByteBuf
{% endcase %}
        ) {
            friendlyByteBuf.writeVarInt(msg.windowId());
            friendlyByteBuf.writeUtf(msg.results(), MAX_RESULTS_LENGTH);
        }

        @Override
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
        public ClientboundContainerExportsInspectionResultsPacket decode(FriendlyByteBuf friendlyByteBuf) {
{% when '1.21', '1.21.1', '26.1.2' %}
        public ClientboundContainerExportsInspectionResultsPacket decode(RegistryFriendlyByteBuf friendlyByteBuf) {
{% endcase %}
            return new ClientboundContainerExportsInspectionResultsPacket(
                    friendlyByteBuf.readVarInt(),
                    friendlyByteBuf.readUtf(MAX_RESULTS_LENGTH)
            );
        }

        @Override
        public void handle(
                ClientboundContainerExportsInspectionResultsPacket msg,
                SFMPacketHandlingContext context
        ) {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player == null) return;
            var container = player.containerMenu;
            if (container.containerId != msg.windowId) return;
            SFMScreenChangeHelpers.showProgramEditScreen(msg.results);
        }
    }

}
