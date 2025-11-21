package ca.teamdman.sfm.common.net;

import net.minecraft.util.math.BlockPos;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.containermenu.ManagerContainerMenu;
import ca.teamdman.sfm.common.program.linting.ProgramLinter;
import io.netty.buffer.ByteBuf;

public class ServerboundManagerFixPacket extends SFMAdvancedPacket<ServerboundManagerFixPacket> {

    private int windowId;
    private BlockPos pos;

    public ServerboundManagerFixPacket(int windowId, BlockPos pos) {
        this.windowId = windowId;
        this.pos = pos;
    }

    public ServerboundManagerFixPacket() {}

    @Override
    public void fromBytes(ByteBuf buf) {
        windowId = buf.readInt();
        pos = new BlockPos(buf.readInt(), buf.readInt(), buf.readInt());
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(windowId);
        buf.writeInt(pos.getX());
        buf.writeInt(pos.getY());
        buf.writeInt(pos.getZ());
    }

    @Override
    public void handle(
                       ServerboundManagerFixPacket msg,
                       SFMPacketHandlingContext context) {
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
                                    program);
                        }
                    }
                });
    }
}
