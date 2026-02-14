package vswe.superfactory.network.packets;

import io.netty.buffer.ByteBufUtil;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.client.FMLClientHandler;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import vswe.superfactory.interfaces.ContainerBase;

public class PacketEventHandler {
	@SideOnly(Side.CLIENT)
	@SubscribeEvent
	public void onClientPacket(final FMLNetworkEvent.ClientCustomPacketEvent event) {
		FMLClientHandler.instance().getClient().addScheduledTask(() -> processClientPacket(event));
	}

	@SideOnly(Side.CLIENT)
	private void processClientPacket(FMLNetworkEvent.ClientCustomPacketEvent event) {
		DataReader   dr     = new DataReader(ByteBufUtil.getBytes(event.getPacket().payload()));//new DataReader(event.getPacket().payload().array().clone());
		EntityPlayer player = FMLClientHandler.instance().getClient().player;

		boolean useContainer = dr.readBoolean();

		if (useContainer) {
			int       containerId = dr.readByte();
			Container container   = player.openContainer;

			if (container != null && container.windowId == containerId && container instanceof ContainerBase) {
				if (dr.readBoolean()) {
					((ContainerBase) container).getTileEntity().readUpdatedData(dr, player);
				} else {
					((ContainerBase) container).getTileEntity().readAllData(dr, player);
				}

			}
		} else {
			int x = dr.readData(DataBitHelper.WORLD_COORDINATE);
			int y = dr.readData(DataBitHelper.WORLD_COORDINATE);
			int z = dr.readData(DataBitHelper.WORLD_COORDINATE);

			TileEntity te = player.world.getTileEntity(new BlockPos(x, y, z));
			if (te instanceof IPacketBlock) {
				int id = dr.readData(((IPacketBlock) te).infoBitLength(false));
				((IPacketBlock) te).readData(dr, player, false, id);
			}
		}

		dr.close();
	}

	@SubscribeEvent
	public void onServerPacket(final FMLNetworkEvent.ServerCustomPacketEvent event) {
		EntityPlayerMP player = ((NetHandlerPlayServer) event.getHandler()).player;
		player.getServerWorld().addScheduledTask(() -> processServerPacket(event));
	}

	private void processServerPacket(FMLNetworkEvent.ServerCustomPacketEvent event) {
		//        if(!event.getPacket().payload().hasArray())
		//        {
		//            return;
		//        }

		DataReader   dr     = new DataReader(ByteBufUtil.getBytes(event.getPacket().payload()));//new DataReader(event.getPacket().payload().array().clone());
		EntityPlayer player = ((NetHandlerPlayServer) event.getHandler()).player;

		boolean useContainer = dr.readBoolean();

		if (useContainer) {
			int       containerId = dr.readByte();
			Container container   = player.openContainer;

			if (container != null && container.windowId == containerId && container instanceof ContainerBase) {
				((ContainerBase) container).getTileEntity().readUpdatedData(dr, player);
				((TileEntity) ((ContainerBase) container).getTileEntity()).markDirty();
			}
		} else {
			int x = dr.readData(DataBitHelper.WORLD_COORDINATE);
			int y = dr.readData(DataBitHelper.WORLD_COORDINATE);
			int z = dr.readData(DataBitHelper.WORLD_COORDINATE);

			TileEntity te = player.world.getTileEntity(new BlockPos(x, y, z));
			if (te instanceof IPacketBlock) {
				int id = dr.readData(((IPacketBlock) te).infoBitLength(true));
				((IPacketBlock) te).readData(dr, player, true, id);
			}
		}

		dr.close();
	}
}
