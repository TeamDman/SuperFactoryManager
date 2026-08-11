package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.client.screen.SFMScreenChangeHelpers;
import net.minecraft.network.RegistryFriendlyByteBuf;

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
                RegistryFriendlyByteBuf friendlyByteBuf
        ) {
            friendlyByteBuf.writeUtf(msg.results(), MAX_RESULTS_LENGTH);
        }

        @Override
        public ClientboundOutputInspectionResultsPacket decode(RegistryFriendlyByteBuf friendlyByteBuf) {
            return new ClientboundOutputInspectionResultsPacket(
                    friendlyByteBuf.readUtf(MAX_RESULTS_LENGTH)
            );
        }

        @Override
        public void handle(
                ClientboundOutputInspectionResultsPacket msg,
                SFMPacketHandlingContext context
        ) {
            SFMScreenChangeHelpers.showProgramEditScreen(msg.results);
        }

        @Override
        public Class<ClientboundOutputInspectionResultsPacket> getPacketClass() {
            return ClientboundOutputInspectionResultsPacket.class;
        }
    }

}
