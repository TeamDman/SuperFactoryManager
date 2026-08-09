package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.containermenu.ManagerContainerMenu;
import net.minecraft.core.BlockPos;
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
        public Class<ServerboundManagerResetPacket> getPacketClass() {
            return ServerboundManagerResetPacket.class;
        }
    }
}
