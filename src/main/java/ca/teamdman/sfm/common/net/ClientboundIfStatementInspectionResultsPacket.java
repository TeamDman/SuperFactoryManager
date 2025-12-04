package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.client.screen.SFMScreenChangeHelpers;
import com.github.bsideup.jabel.Desugar;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

@Desugar
public record ClientboundIfStatementInspectionResultsPacket(
        String results
) implements SFMPacket<ClientboundIfStatementInspectionResultsPacket> {
    public static final int MAX_RESULTS_LENGTH = 2048;

    public static class Daddy implements SFMPacketDaddy<ClientboundIfStatementInspectionResultsPacket> {
        @Override

        public Class<Packet> getPacketClass() {

            return Packet.class;

        }

        @Override
        public PacketDirection getPacketDirection() {
            return PacketDirection.CLIENTBOUND;
        }

        @Override
        public void encode(
                ClientboundIfStatementInspectionResultsPacket msg,
                FriendlyByteBuf friendlyByteBuf
        ) {
            friendlyByteBuf.writeString(SFMPacketDaddy.truncate(msg.results(), MAX_RESULTS_LENGTH));
        }

        @Override
        public ClientboundIfStatementInspectionResultsPacket decode(FriendlyByteBuf friendlyByteBuf) {
            return new ClientboundIfStatementInspectionResultsPacket(
                    friendlyByteBuf.readString(MAX_RESULTS_LENGTH)
            );
        }

        @Override
        public void handle(
                ClientboundIfStatementInspectionResultsPacket msg,
                SFMPacketHandlingContext context
        ) {
            SFMScreenChangeHelpers.showProgramEditScreen(msg.results());
        }

    }

    public static final Daddy daddy = new Daddy();

    public static class Packet extends Wrapper<ClientboundIfStatementInspectionResultsPacket> {

        @Override
        SFMPacketDaddy<ClientboundIfStatementInspectionResultsPacket> getDaddy() {
            return daddy;
        }
    }


    @Override
    public Wrapper<ClientboundIfStatementInspectionResultsPacket> wrap() {
        var wrapper = new Packet();
        wrapper.ourRecord = this;
        return wrapper;
    }

}