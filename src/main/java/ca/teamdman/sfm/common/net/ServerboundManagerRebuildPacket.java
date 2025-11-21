package ca.teamdman.sfm.common.net;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.cablenetwork.CableNetworkManager;
import ca.teamdman.sfm.common.containermenu.ManagerContainerMenu;
import ca.teamdman.sfm.common.localization.LocalizationKeys;
import io.netty.buffer.ByteBuf;

public class ServerboundManagerRebuildPacket extends SFMAdvancedPacket<ServerboundManagerRebuildPacket> {

    private int windowId;
    private BlockPos pos;

    public ServerboundManagerRebuildPacket(int windowId, BlockPos pos) {
        this.windowId = windowId;
        this.pos = pos;
    }

    public ServerboundManagerRebuildPacket() {}

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
                       ServerboundManagerRebuildPacket msg,
                       SFMPacketHandlingContext context) {
        context.handleServerboundContainerPacket(
                ManagerContainerMenu.class,
                ManagerBlockEntity.class,
                msg.pos,
                msg.windowId,
                (menu, manager) -> {
                    EntityPlayerMP player = context.serverPlayer();
                    // perform rebuild by unregistering the cable network
                    CableNetworkManager.purgeCableNetworkForManager(manager);
                    manager.logger.warn(x -> x.accept(LocalizationKeys.LOG_MANAGER_CABLE_NETWORK_REBUILD.get()));

                    // log it
                    SFM.LOGGER.debug(
                            "{} performed rebuild for manager {} {}",
                            player.getName(),
                            msg.pos,
                            manager.getWorld());
                });
    }
}
