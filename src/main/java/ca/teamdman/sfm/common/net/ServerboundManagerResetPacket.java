package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.containermenu.ManagerContainerMenu;
import io.netty.buffer.ByteBuf;
import net.minecraft.util.math.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

public record ServerboundManagerResetPacket(
        int windowId,
        BlockPos pos
) implements SFMPacket {
    public static class Daddy implements SFMPacketDaddy<ServerboundManagerResetPacket> {
        @Override
        public PacketDirection getPacketDirection() {
            return PacketDirection.SERVERBOUND;
        }
        @Override
        public void encode(
                ServerboundManagerResetPacket msg,
                ByteBuf friendlyByteBuf
        ) {
            friendlyByteBuf.writeVarInt(msg.windowId());
            friendlyByteBuf.writeBlockPos(msg.pos());
        }

        @Override
        public ServerboundManagerResetPacket decode(ByteBuf friendlyByteBuf) {
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
        public Class<ServerboundManagerResetPacket> getPacketClass() {
            return ServerboundManagerResetPacket.class;
        }
    }
}
