package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.client.screen.SFMScreenChangeHelpers;
import com.github.bsideup.jabel.Desugar;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import javax.annotation.Nullable;
import java.io.IOException;

@Desugar
public record ClientboundLabelInspectionResultsPacket(
        String results
) implements SFMPacket<ClientboundLabelInspectionResultsPacket> {
    public static final int MAX_RESULTS_LENGTH = 50_000;

    public static class Daddy implements SFMPacketDaddy<ClientboundLabelInspectionResultsPacket> {
        @Override
        public PacketDirection getPacketDirection() {
            return PacketDirection.CLIENTBOUND;
        }
        @Override
        public void encode(
                ClientboundLabelInspectionResultsPacket msg,
                FriendlyByteBuf friendlyByteBuf
        ) {
            friendlyByteBuf.writeString(SFMPacketDaddy.truncate(msg.results(), MAX_RESULTS_LENGTH));
        }

        @Override
        public ClientboundLabelInspectionResultsPacket decode(FriendlyByteBuf friendlyByteBuf) {
            return new ClientboundLabelInspectionResultsPacket(
                    friendlyByteBuf.readString(MAX_RESULTS_LENGTH)
            );

    }

        @Override
        public void handle(
                ClientboundLabelInspectionResultsPacket msg,
                SFMPacketHandlingContext context
        ) {
            SFMScreenChangeHelpers.showProgramEditScreen(msg.results());
        }

        @Override
        public Class<Packet> getPacketClass() {
            return Packet.class;
        }
    }

    public static final Daddy daddy = new Daddy();

    public static class Packet extends Wrapper<ClientboundLabelInspectionResultsPacket> {

        @Override
        SFMPacketDaddy<ClientboundLabelInspectionResultsPacket> getDaddy() {
            return daddy;
        }
    }


    @Override
    public Wrapper<ClientboundLabelInspectionResultsPacket> wrap() {
        var wrapper = new Packet();
        wrapper.ourRecord = this;
        return wrapper;
    }
}