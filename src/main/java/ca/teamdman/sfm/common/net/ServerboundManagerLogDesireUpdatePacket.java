package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.containermenu.ManagerContainerMenu;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class ServerboundManagerLogDesireUpdatePacket extends SFMPacket<ServerboundManagerLogDesireUpdatePacket> {
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
    public IMessage onMessage(ServerboundManagerLogDesireUpdatePacket message, MessageContext ctx) {
        EntityPlayerMP player = ctx.getServerHandler().player;
        player.getServerWorld().addScheduledTask(() -> {
            if (player.openContainer instanceof ManagerContainerMenu && player.openContainer.windowId == message.windowId) {
                ManagerContainerMenu menu = (ManagerContainerMenu) player.openContainer;
                menu.isLogScreenOpen = message.isLogScreenOpen;
                TileEntity te = player.world.getTileEntity(message.pos);
                if (te instanceof ManagerBlockEntity) {
                    ((ManagerBlockEntity) te).sendUpdatePacket();
                }
            }
        });
        return null;
    }
}