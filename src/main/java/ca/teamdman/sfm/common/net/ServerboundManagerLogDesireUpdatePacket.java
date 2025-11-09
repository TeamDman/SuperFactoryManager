package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.containermenu.ManagerContainerMenu;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class ServerboundManagerLogDesireUpdatePacket extends SFMAdvancedPacket<ServerboundManagerLogDesireUpdatePacket> {
    private int windowId;
    private BlockPos pos;
    private boolean isLogScreenOpen;

    public ServerboundManagerLogDesireUpdatePacket(int windowId, BlockPos pos, boolean isLogScreenOpen) {
        this.windowId = windowId;
        this.pos = pos;
        this.isLogScreenOpen = isLogScreenOpen;
    }

    public ServerboundManagerLogDesireUpdatePacket() {
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        windowId = buf.readInt();
        pos = new BlockPos(buf.readInt(), buf.readInt(), buf.readInt());
        isLogScreenOpen = buf.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(windowId);
        buf.writeInt(pos.getX());
        buf.writeInt(pos.getY());
        buf.writeInt(pos.getZ());
        buf.writeBoolean(isLogScreenOpen);
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
                    menu.isLogScreenOpen = msg.isLogScreenOpen;
                    manager.sendUpdatePacket();
                }
        );
    }
}