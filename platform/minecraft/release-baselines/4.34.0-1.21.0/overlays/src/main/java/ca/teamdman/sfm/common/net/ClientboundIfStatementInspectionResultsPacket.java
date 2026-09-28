package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.client.screen.SFMScreenChangeHelpers;
import net.minecraft.network.RegistryFriendlyByteBuf;

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
                RegistryFriendlyByteBuf friendlyByteBuf
        ) {
            friendlyByteBuf.writeUtf(msg.results(), MAX_RESULTS_LENGTH);
        }

        @Override
        public ClientboundIfStatementInspectionResultsPacket decode(RegistryFriendlyByteBuf friendlyByteBuf) {
            return new ClientboundIfStatementInspectionResultsPacket(
                    friendlyByteBuf.readUtf(MAX_RESULTS_LENGTH)
            );
        }

        @Override
        public void handle(
                ClientboundIfStatementInspectionResultsPacket msg,
                SFMPacketHandlingContext context
        ) {
            SFMScreenChangeHelpers.showProgramEditScreen(msg.results());
        }

        @Override
        public Class<ClientboundIfStatementInspectionResultsPacket> getPacketClass() {
            return ClientboundIfStatementInspectionResultsPacket.class;
        }
    }

}
