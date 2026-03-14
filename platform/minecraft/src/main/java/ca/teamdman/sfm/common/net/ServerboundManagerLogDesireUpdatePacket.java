package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.containermenu.ManagerContainerMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

public record ServerboundManagerLogDesireUpdatePacket(
        int windowId,
        BlockPos pos,
        boolean isLogScreenOpen
) implements SFMPacket {
    public static class Daddy implements SFMPacketDaddy<ServerboundManagerLogDesireUpdatePacket> {
        @Override
        public PacketDirection getPacketDirection() {
            return PacketDirection.SERVERBOUND;
        }
        @Override
        public void encode(
                ServerboundManagerLogDesireUpdatePacket msg,
                FriendlyByteBuf friendlyByteBuf
        ) {
            friendlyByteBuf.writeVarInt(msg.windowId());
            friendlyByteBuf.writeBlockPos(msg.pos());
            friendlyByteBuf.writeBoolean(msg.isLogScreenOpen());
        }

        @Override
        public ServerboundManagerLogDesireUpdatePacket decode(FriendlyByteBuf friendlyByteBuf) {
            return new ServerboundManagerLogDesireUpdatePacket(
                    friendlyByteBuf.readVarInt(),
                    friendlyByteBuf.readBlockPos(),
                    friendlyByteBuf.readBoolean()
            );
        }

        @Override
        public void handle(
                ServerboundManagerLogDesireUpdatePacket msg,
                SFMPacketHandlingContext context
        ) {
            context.handleServerboundContainerPacket(
                    ManagerContainerMenu.class,
                    ManagerBlockEntity.class,
                    msg.pos,
                    msg.windowId,
                    (menu, manager) -> {
                        menu.isLogScreenOpen = msg.isLogScreenOpen();
                        manager.sendUpdatePacket();
                    }
            );
        }

        @Override
        public Class<ServerboundManagerLogDesireUpdatePacket> getPacketClass() {
            return ServerboundManagerLogDesireUpdatePacket.class;
        }
    }
}
