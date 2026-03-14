package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.client.screen.SFMScreenChangeHelpers;
import ca.teamdman.sfm.client.text_editor.SFMTextEditScreenOpenContext;
import ca.teamdman.sfm.client.text_editor.TextEditScreenContentLanguage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.FriendlyByteBuf;

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
                FriendlyByteBuf friendlyByteBuf
        ) {
            friendlyByteBuf.writeVarInt(msg.windowId());
            friendlyByteBuf.writeUtf(msg.results(), MAX_RESULTS_LENGTH);
        }

        @Override
        public ClientboundContainerExportsInspectionResultsPacket decode(FriendlyByteBuf friendlyByteBuf) {
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

            SFMScreenChangeHelpers.showPreferredTextEditScreen(new SFMTextEditScreenOpenContext(
                    msg.results,
                    TextEditScreenContentLanguage.PLAINTEXT
            ));
        }
    }

}
