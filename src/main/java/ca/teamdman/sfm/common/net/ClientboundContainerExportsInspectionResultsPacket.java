package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.client.screen.SFMScreenChangeHelpers;
import com.github.bsideup.jabel.Desugar;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.inventory.Container;

@Desugar
public record ClientboundContainerExportsInspectionResultsPacket(
        int windowId,
        String results
) implements SFMPacket<ClientboundContainerExportsInspectionResultsPacket> {
    public static final int MAX_RESULTS_LENGTH = 20480;

    public static class Daddy implements SFMPacketDaddy<ClientboundContainerExportsInspectionResultsPacket> {
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
                ClientboundContainerExportsInspectionResultsPacket msg,
                FriendlyByteBuf friendlyByteBuf
        ) {
            friendlyByteBuf.writeVarInt(msg.windowId());
            friendlyByteBuf.writeString(SFMPacketDaddy.truncate(msg.results(), MAX_RESULTS_LENGTH));
        }

        @Override
        public ClientboundContainerExportsInspectionResultsPacket decode(FriendlyByteBuf friendlyByteBuf) {
            return new ClientboundContainerExportsInspectionResultsPacket(
                    friendlyByteBuf.readVarInt(),
                    friendlyByteBuf.readString(MAX_RESULTS_LENGTH)
            );
        }

        @Override
        public void handle(
                ClientboundContainerExportsInspectionResultsPacket msg,
                SFMPacketHandlingContext context
        ) {
            EntityPlayerSP player = Minecraft.getMinecraft().player;
            if (player == null) return;
            Container container = player.openContainer;
            if (container.windowId != msg.windowId) return;
            SFMScreenChangeHelpers.showProgramEditScreen(msg.results);
        }
    }

    public static final Daddy daddy = new Daddy();

    public static class Packet extends Wrapper<ClientboundContainerExportsInspectionResultsPacket> {

        @Override
        SFMPacketDaddy<ClientboundContainerExportsInspectionResultsPacket> getDaddy() {
            return daddy;
        }
    }


    @Override
    public Wrapper<ClientboundContainerExportsInspectionResultsPacket> wrap() {
        var wrapper = new Packet();
        wrapper.ourRecord = this;
        return wrapper;
    }
}