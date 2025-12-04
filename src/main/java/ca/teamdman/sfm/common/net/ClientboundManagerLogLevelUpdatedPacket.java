package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.containermenu.ManagerContainerMenu;
import com.github.bsideup.jabel.Desugar;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;

@Desugar
public record ClientboundManagerLogLevelUpdatedPacket(
        int windowId,
        String logLevel
) implements SFMPacket<ClientboundManagerLogLevelUpdatedPacket> {
    public static class Daddy implements SFMPacketDaddy<ClientboundManagerLogLevelUpdatedPacket> {
        @Override
        public PacketDirection getPacketDirection() {
            return PacketDirection.CLIENTBOUND;
        }
        @Override
        public void encode(
                ClientboundManagerLogLevelUpdatedPacket msg,
                FriendlyByteBuf friendlyByteBuf
        ) {
            friendlyByteBuf.writeVarInt(msg.windowId());
            friendlyByteBuf.writeString(SFMPacketDaddy.truncate(msg.logLevel(), ServerboundManagerSetLogLevelPacket.MAX_LOG_LEVEL_NAME_LENGTH));
        }

        @Override
        public ClientboundManagerLogLevelUpdatedPacket decode(FriendlyByteBuf friendlyByteBuf) {
            return new ClientboundManagerLogLevelUpdatedPacket(
                    friendlyByteBuf.readVarInt(),
                    friendlyByteBuf.readString(ServerboundManagerSetLogLevelPacket.MAX_LOG_LEVEL_NAME_LENGTH)
            );
        }

        @Override
        public void handle(
                ClientboundManagerLogLevelUpdatedPacket msg,
                SFMPacketHandlingContext context
        ) {
            EntityPlayerSP player = Minecraft.getMinecraft().player;
            if (player == null
                || !(player.openContainer instanceof ManagerContainerMenu menu)
                || menu.windowId != msg.windowId()) {
                SFM.LOGGER.error("Invalid log level packet received, ignoring.");
                return;
            }
            menu.logLevel = msg.logLevel;
        }

        @Override
        public Class<Packet> getPacketClass() {
            return Packet.class;
        }
    }

    public static final Daddy daddy = new Daddy();

    public static class Packet extends Wrapper<ClientboundManagerLogLevelUpdatedPacket> {

        @Override
        SFMPacketDaddy<ClientboundManagerLogLevelUpdatedPacket> getDaddy() {
            return daddy;
        }
    }


    @Override
    public Wrapper<ClientboundManagerLogLevelUpdatedPacket> wrap() {
        var wrapper = new Packet();
        wrapper.ourRecord = this;
        return wrapper;
    }
}