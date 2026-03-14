package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.client.screen.SFMScreenChangeHelpers;
import ca.teamdman.sfm.client.text_editor.SFMTextEditScreenOpenContext;
import ca.teamdman.sfm.client.text_editor.TextEditScreenContentLanguage;
import net.minecraft.network.FriendlyByteBuf;

public record ClientboundOutputInspectionResultsPacket(
        String results
) implements SFMPacket {
    public static final int MAX_RESULTS_LENGTH = 10240;

    public static class Daddy implements SFMPacketDaddy<ClientboundOutputInspectionResultsPacket> {
        @Override
        public PacketDirection getPacketDirection() {
            return PacketDirection.CLIENTBOUND;
        }
        @Override
        public void encode(
                ClientboundOutputInspectionResultsPacket msg,
                FriendlyByteBuf friendlyByteBuf
        ) {
            friendlyByteBuf.writeUtf(msg.results(), MAX_RESULTS_LENGTH);
        }

        @Override
        public ClientboundOutputInspectionResultsPacket decode(FriendlyByteBuf friendlyByteBuf) {
            return new ClientboundOutputInspectionResultsPacket(
                    friendlyByteBuf.readUtf(MAX_RESULTS_LENGTH)
            );
        }

        @Override
        public void handle(
                ClientboundOutputInspectionResultsPacket msg,
                SFMPacketHandlingContext context
        ) {

            SFMScreenChangeHelpers.showPreferredTextEditScreen(new SFMTextEditScreenOpenContext(
                    msg.results,
                    TextEditScreenContentLanguage.PLAINTEXT
            ));
        }

        @Override
        public Class<ClientboundOutputInspectionResultsPacket> getPacketClass() {
            return ClientboundOutputInspectionResultsPacket.class;
        }
    }

}
