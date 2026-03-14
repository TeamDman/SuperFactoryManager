package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.client.screen.SFMScreenChangeHelpers;
import ca.teamdman.sfm.client.text_editor.SFMTextEditScreenOpenContext;
import ca.teamdman.sfm.client.text_editor.TextEditScreenContentLanguage;
import net.minecraft.network.FriendlyByteBuf;

public record ClientboundIfStatementInspectionResultsPacket(
        String results
) implements SFMPacket {
    public static final int MAX_RESULTS_LENGTH = 2048;

    public static class Daddy implements SFMPacketDaddy<ClientboundIfStatementInspectionResultsPacket> {
        @Override
        public PacketDirection getPacketDirection() {
            return PacketDirection.CLIENTBOUND;
        }
        @Override
        public void encode(
                ClientboundIfStatementInspectionResultsPacket msg,
                FriendlyByteBuf friendlyByteBuf
        ) {
            friendlyByteBuf.writeUtf(msg.results(), MAX_RESULTS_LENGTH);
        }

        @Override
        public ClientboundIfStatementInspectionResultsPacket decode(FriendlyByteBuf friendlyByteBuf) {
            return new ClientboundIfStatementInspectionResultsPacket(
                    friendlyByteBuf.readUtf(MAX_RESULTS_LENGTH)
            );
        }

        @Override
        public void handle(
                ClientboundIfStatementInspectionResultsPacket msg,
                SFMPacketHandlingContext context
        ) {

            SFMScreenChangeHelpers.showPreferredTextEditScreen(new SFMTextEditScreenOpenContext(
                    msg.results(),
                    TextEditScreenContentLanguage.PLAINTEXT
            ));
        }

        @Override
        public Class<ClientboundIfStatementInspectionResultsPacket> getPacketClass() {
            return ClientboundIfStatementInspectionResultsPacket.class;
        }
    }

}
