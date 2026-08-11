package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.client.screen.SFMScreenChangeHelpers;
import net.minecraft.network.RegistryFriendlyByteBuf;

public record ClientboundBoolExprStatementInspectionResultsPacket(
        String results
) implements SFMPacket {
    public static final int MAX_RESULTS_LENGTH = 2048;

    public static class Daddy implements SFMPacketDaddy<ClientboundBoolExprStatementInspectionResultsPacket> {
        @Override
        public PacketDirection getPacketDirection() {
            return PacketDirection.CLIENTBOUND;
        }
        @Override
        public Class<ClientboundBoolExprStatementInspectionResultsPacket> getPacketClass() {
            return ClientboundBoolExprStatementInspectionResultsPacket.class;
        }

        @Override
        public void encode(
                ClientboundBoolExprStatementInspectionResultsPacket msg,
                RegistryFriendlyByteBuf friendlyByteBuf
        ) {
            friendlyByteBuf.writeUtf(msg.results(), MAX_RESULTS_LENGTH);
        }

        @Override
        public ClientboundBoolExprStatementInspectionResultsPacket decode(RegistryFriendlyByteBuf friendlyByteBuf) {
            return new ClientboundBoolExprStatementInspectionResultsPacket(
                    friendlyByteBuf.readUtf(MAX_RESULTS_LENGTH)
            );
        }

        @Override
        public void handle(
                ClientboundBoolExprStatementInspectionResultsPacket msg,
                SFMPacketHandlingContext context
        ) {
            SFMScreenChangeHelpers.showProgramEditScreen(msg.results);
        }
    }
}
