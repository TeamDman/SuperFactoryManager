package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.containermenu.ManagerContainerMenu;
import ca.teamdman.sfml.ast.Program;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

public record ServerboundManagerProgramPacket(
        int windowId,
        BlockPos pos,
        String program
) implements SFMPacket {
    public static class Daddy implements SFMPacketDaddy<ServerboundManagerProgramPacket> {
        @Override
        public PacketDirection getPacketDirection() {
            return PacketDirection.SERVERBOUND;
        }
        @Override
        public void encode(
                ServerboundManagerProgramPacket msg,
                FriendlyByteBuf friendlyByteBuf
        ) {
            friendlyByteBuf.writeVarInt(msg.windowId());
            friendlyByteBuf.writeBlockPos(msg.pos());
            friendlyByteBuf.writeUtf(msg.program(), Program.MAX_PROGRAM_LENGTH);
        }

        @Override
        public ServerboundManagerProgramPacket decode(FriendlyByteBuf friendlyByteBuf) {
            return new ServerboundManagerProgramPacket(
                    friendlyByteBuf.readVarInt(),
                    friendlyByteBuf.readBlockPos(),
                    friendlyByteBuf.readUtf(Program.MAX_PROGRAM_LENGTH)
            );
        }

        @Override
        public void handle(
                ServerboundManagerProgramPacket msg,
                SFMPacketHandlingContext context
        ) {
            context.handleServerboundContainerPacket(
                    ManagerContainerMenu.class,
                    ManagerBlockEntity.class,
                    msg.pos,
                    msg.windowId,
                    (menu, manager) -> manager.setProgram(msg.program())
            );
        }

        @Override
        public Class<ServerboundManagerProgramPacket> getPacketClass() {
            return ServerboundManagerProgramPacket.class;
        }
    }
}
