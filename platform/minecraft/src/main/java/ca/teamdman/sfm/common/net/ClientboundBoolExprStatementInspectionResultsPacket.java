package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.client.screen.SFMScreenChangeHelpers;
import com.github.bsideup.jabel.Desugar;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

@Desugar
public record ClientboundBoolExprStatementInspectionResultsPacket(
        String results
) implements SFMPacket<ClientboundBoolExprStatementInspectionResultsPacket> {
    public static final int MAX_RESULTS_LENGTH = 2048;

    public static class Daddy implements SFMPacketDaddy<ClientboundBoolExprStatementInspectionResultsPacket> {
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
                ClientboundBoolExprStatementInspectionResultsPacket msg,
                FriendlyByteBuf friendlyByteBuf
        ) {
            friendlyByteBuf.writeString(SFMPacketDaddy.truncate(msg.results(), MAX_RESULTS_LENGTH));
        }

        @Override
        public ClientboundBoolExprStatementInspectionResultsPacket decode(FriendlyByteBuf friendlyByteBuf) {
            return new ClientboundBoolExprStatementInspectionResultsPacket(
                    friendlyByteBuf.readString(MAX_RESULTS_LENGTH)
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

    public static final Daddy daddy = new Daddy();

    public static class Packet extends SFMPacket.Wrapper<ClientboundBoolExprStatementInspectionResultsPacket> {

        @Override
        SFMPacketDaddy<ClientboundBoolExprStatementInspectionResultsPacket> getDaddy() {
            return daddy;
        }
    }

    @Override
    public Wrapper<ClientboundBoolExprStatementInspectionResultsPacket> wrap() {
        var wrapper = new Packet();
        wrapper.ourRecord = this;
        return wrapper;
    }


}