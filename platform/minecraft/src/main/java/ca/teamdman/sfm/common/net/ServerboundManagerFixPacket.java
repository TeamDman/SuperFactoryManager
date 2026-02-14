package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.containermenu.ManagerContainerMenu;
import ca.teamdman.sfm.common.program.linting.ProgramLinter;
import com.github.bsideup.jabel.Desugar;
import net.minecraft.util.math.BlockPos;

@Desugar
public record ServerboundManagerFixPacket(
        int windowId,
        BlockPos pos
) implements SFMPacket<ServerboundManagerFixPacket> {
    public static class Daddy implements SFMPacketDaddy<ServerboundManagerFixPacket> {
        @Override
        public PacketDirection getPacketDirection() {
            return PacketDirection.SERVERBOUND;
        }
        @Override
        public void encode(
                ServerboundManagerFixPacket msg,
                FriendlyByteBuf friendlyByteBuf
        ) {
            friendlyByteBuf.writeVarInt(msg.windowId());
            friendlyByteBuf.writeBlockPos(msg.pos());
        }

        @Override
        public ServerboundManagerFixPacket decode(FriendlyByteBuf friendlyByteBuf) {
            return new ServerboundManagerFixPacket(
                    friendlyByteBuf.readVarInt(),
                    friendlyByteBuf.readBlockPos()
            );
        }

        @Override
        public void handle(
                ServerboundManagerFixPacket msg,
                SFMPacketHandlingContext context
        ) {
            context.handleServerboundContainerPacket(
                    ManagerContainerMenu.class,
                    ManagerBlockEntity.class,
                    msg.pos,
                    msg.windowId,
                    (menu, manager) -> {
                        var disk = manager.getDisk();
                        if (disk != null) {
                            var program = manager.getProgram();
                            if (program != null) {
                                ProgramLinter.fixWarnings(
                                        manager,
                                        disk,
                                        program
                                );
                            }
                        }
                    }
            );
        }

        @Override
        public Class<Packet> getPacketClass() {
            return Packet.class;
        }
    }

    public static final Daddy daddy = new Daddy();

    public static class Packet extends Wrapper<ServerboundManagerFixPacket> {

        @Override
        SFMPacketDaddy<ServerboundManagerFixPacket> getDaddy() {
            return daddy;
        }
    }


    @Override
    public Wrapper<ServerboundManagerFixPacket> wrap() {
        var wrapper = new Packet();
        wrapper.ourRecord = this;
        return wrapper;
    }
}
