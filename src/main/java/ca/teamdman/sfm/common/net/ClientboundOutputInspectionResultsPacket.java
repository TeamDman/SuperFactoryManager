package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.client.screen.SFMScreenChangeHelpers;
import com.github.bsideup.jabel.Desugar;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import javax.annotation.Nullable;
import java.io.IOException;

@Desugar
public record ClientboundOutputInspectionResultsPacket(
        String results
) implements SFMPacket<ClientboundOutputInspectionResultsPacket> {
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
            friendlyByteBuf.writeString(SFMPacketDaddy.truncate(msg.results(), MAX_RESULTS_LENGTH));
        }

        @Override
        public ClientboundOutputInspectionResultsPacket decode(FriendlyByteBuf friendlyByteBuf) {
            return new ClientboundOutputInspectionResultsPacket(
                    friendlyByteBuf.readString(MAX_RESULTS_LENGTH)
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
        public Class<Packet> getPacketClass() {
            return Packet.class;
        }
    }

    public static final Daddy daddy = new Daddy();

    public static class Packet extends Wrapper<ClientboundOutputInspectionResultsPacket> {

        @Override
        SFMPacketDaddy<ClientboundOutputInspectionResultsPacket> getDaddy() {
            return daddy;
        }
    }


    @Override
    public Wrapper<ClientboundOutputInspectionResultsPacket> wrap() {
        var wrapper = new Packet();
        wrapper.ourRecord = this;
        return wrapper;
    }
}