package ca.teamdman.sfm.common.net;

import net.minecraft.util.math.BlockPos;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.containermenu.ManagerContainerMenu;
import io.netty.buffer.ByteBuf;

public class ServerboundManagerResetPacket extends SFMAdvancedPacket<ServerboundManagerResetPacket> {

    private int windowId;
    private BlockPos pos;

    public ServerboundManagerResetPacket(int windowId, BlockPos pos) {
        this.windowId = windowId;
        this.pos = pos;
    }

    public ServerboundManagerResetPacket() {}

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
                       ServerboundManagerResetPacket msg,
                       SFMPacketHandlingContext context) {
        context.handleServerboundContainerPacket(
                ManagerContainerMenu.class,
                ManagerBlockEntity.class,
                msg.pos,
                msg.windowId,
                (menu, manager) -> manager.reset());
    }
}
