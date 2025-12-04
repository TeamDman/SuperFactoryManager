package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.client.screen.SFMScreenChangeHelpers;
import com.github.bsideup.jabel.Desugar;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import javax.annotation.Nullable;
import java.io.IOException;

@Desugar
public record ClientboundInputInspectionResultsPacket(
        String results
) implements SFMPacket<ClientboundInputInspectionResultsPacket> {
    public static final int MAX_RESULTS_LENGTH = 20480;

    public static class Daddy implements SFMPacketDaddy<ClientboundInputInspectionResultsPacket> {
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
                ClientboundInputInspectionResultsPacket msg,
                FriendlyByteBuf friendlyByteBuf
        ) {
            friendlyByteBuf.writeString(SFMPacketDaddy.truncate(msg.results(), MAX_RESULTS_LENGTH));
        }

        @Override
        public ClientboundInputInspectionResultsPacket decode(FriendlyByteBuf friendlyByteBuf) {
            return new ClientboundInputInspectionResultsPacket(
                    friendlyByteBuf.readString(MAX_RESULTS_LENGTH)
            );
        }

        @Override
        public void handle(
                ClientboundInputInspectionResultsPacket msg,
                SFMPacketHandlingContext context
        ) {
            SFMScreenChangeHelpers.showProgramEditScreen(msg.results());
        }

    }

    public static final Daddy daddy = new Daddy();

    public static class Packet extends Wrapper<ClientboundInputInspectionResultsPacket> {

        @Override
        SFMPacketDaddy<ClientboundInputInspectionResultsPacket> getDaddy() {
            return daddy;
        }
    }


    @Override
    public Wrapper<ClientboundInputInspectionResultsPacket> wrap() {
        var wrapper = new Packet();
        wrapper.ourRecord = this;
        return wrapper;
    }

}