package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.containermenu.ManagerContainerMenu;
import com.github.bsideup.jabel.Desugar;
import net.minecraft.util.math.BlockPos;

@Desugar
public record ServerboundManagerResetPacket(
        int windowId,
        BlockPos pos
) implements SFMPacket<ServerboundManagerResetPacket> {
    public static class Daddy implements SFMPacketDaddy<ServerboundManagerResetPacket> {
        @Override
        public PacketDirection getPacketDirection() {
            return PacketDirection.SERVERBOUND;
        }
        @Override
        public void encode(
                ServerboundManagerResetPacket msg,
                FriendlyByteBuf friendlyByteBuf
        ) {
            friendlyByteBuf.writeVarInt(msg.windowId());
            friendlyByteBuf.writeBlockPos(msg.pos());
        }

        @Override
        public ServerboundManagerResetPacket decode(FriendlyByteBuf friendlyByteBuf) {
            return new ServerboundManagerResetPacket(
                    friendlyByteBuf.readVarInt(),
                    friendlyByteBuf.readBlockPos()
            );
        }

        @Override
        public void handle(
                ServerboundManagerResetPacket msg,
                SFMPacketHandlingContext context
        ) {
            context.handleServerboundContainerPacket(
                    ManagerContainerMenu.class,
                    ManagerBlockEntity.class,
                    msg.pos,
                    msg.windowId,
                    (menu, manager) -> manager.reset()
            );
        }

        @Override
        public Class<Packet> getPacketClass() {
            return Packet.class;
        }
    }

    public static final Daddy daddy = new Daddy();

    public static class Packet extends Wrapper<ServerboundManagerResetPacket> {

        @Override
        SFMPacketDaddy<ServerboundManagerResetPacket> getDaddy() {
            return daddy;
        }
    }


    @Override
    public Wrapper<ServerboundManagerResetPacket> wrap() {
        var wrapper = new Packet();
        wrapper.ourRecord = this;
        return wrapper;
    }
}
