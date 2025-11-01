package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.cablenetwork.CableNetworkManager;
import ca.teamdman.sfm.common.containermenu.ManagerContainerMenu;
import ca.teamdman.sfm.common.localization.LocalizationKeys;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class ServerboundManagerRebuildPacket extends SFMPacket<ServerboundManagerRebuildPacket> {
    private int windowId;
    private BlockPos pos;

    public ServerboundManagerRebuildPacket(int windowId, BlockPos pos) {
        this.windowId = windowId;
        this.pos = pos;
    }

    public ServerboundManagerRebuildPacket() {
    }

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
    public IMessage onMessage(ServerboundManagerRebuildPacket message, MessageContext ctx) {
        EntityPlayerMP player = ctx.getServerHandler().player;
        player.getServerWorld().addScheduledTask(() -> {
            if (player.openContainer instanceof ManagerContainerMenu && player.openContainer.windowId == message.windowId) {
                TileEntity te = player.world.getTileEntity(message.pos);
                if (te instanceof ManagerBlockEntity) {
                    ManagerBlockEntity manager = (ManagerBlockEntity) te;
                    CableNetworkManager.purgeCableNetworkForManager(manager);
                    manager.logger.warn(x -> x.accept(LocalizationKeys.LOG_MANAGER_CABLE_NETWORK_REBUILD.get()));

                    SFM.LOGGER.debug(
                            "{} performed rebuild for manager {} {}",
                            player.getName(),
                            message.pos,
                            manager.getWorld()
                    );
                }
            }
        });
        return null;
    }
}