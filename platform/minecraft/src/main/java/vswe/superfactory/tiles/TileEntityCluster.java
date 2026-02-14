package vswe.superfactory.tiles;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import vswe.superfactory.blocks.ClusterMethodRegistration;
import vswe.superfactory.blocks.ITileEntityInterface;
import vswe.superfactory.blocks.ItemCluster;
import vswe.superfactory.network.packets.*;
import vswe.superfactory.registry.ClusterRegistry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TileEntityCluster extends TileEntity implements ITileEntityInterface, IPacketBlock, ITickable {
	private static final String                                       NBT_SUB_BLOCKS     = "SubBlocks";
	private static final String                                                 NBT_SUB_BLOCK_ID   = "SubId";
	private static final String                                                 NBT_SUB_BLOCK_META = "SubMeta";
	private              TileEntityCamouflage                                   camouflageObject;
	private              List<TileEntityClusterElement>                         elements           = new ArrayList<>();
	private              ITileEntityInterface                                   interfaceObject;  //only the relay is currently having a interface
	private              Map<ClusterMethodRegistration, List<TileRegistryPair>> methodRegistration = new HashMap<>();
	private              List<ClusterRegistry.ClusterRegistryElement>           registryList       = new ArrayList<>();
	private              boolean                                                requestedInfo;

	public TileEntityCluster() {
		for (ClusterMethodRegistration clusterMethodRegistration : ClusterMethodRegistration.values()) {
			methodRegistration.put(clusterMethodRegistration, new ArrayList<>());
		}
	}

	public static <T> T getTileEntity(Class<? extends TileEntityClusterElement> clazz, IBlockAccess world, BlockPos pos) {
		TileEntity te = world.getTileEntity(pos);

		if (te != null) {
			if (clazz.isInstance(te)) {
				return (T) te;
			} else if (te instanceof TileEntityCluster) {
				for (TileEntityClusterElement element : ((TileEntityCluster) te).getElements()) {
					if (clazz.isInstance(element)) {
						return (T) element;
					}
				}
			}
		}

		return null;
	}

	public List<TileEntityClusterElement> getElements() {
		return elements;
	}

	public void loadElements(ItemStack itemStack) {
		NBTTagCompound compound = itemStack.getTagCompound();

		if (compound != null && compound.hasKey(ItemCluster.NBT_CABLE)) {
			NBTTagCompound cable = compound.getCompoundTag(ItemCluster.NBT_CABLE);
			byte[]         types = cable.getByteArray(ItemCluster.NBT_TYPES);
			loadElements(types);
		}
	}

	@Override
	public boolean shouldRefresh(World world, BlockPos pos, IBlockState oldState, IBlockState newState) {
		return oldState.getBlock() != newState.getBlock();
	}

	private void loadElements(byte[] types) {
		registryList.clear();
		elements.clear();

		for (byte type : types) {
			ClusterRegistry.ClusterRegistryElement block = ClusterRegistry.getRegistryList().get(type);
			registryList.add(block);
			TileEntityClusterElement element = (TileEntityClusterElement) block.getBlock().createNewTileEntity(getWorld(), 0);
			elements.add(element);
			if (element instanceof ITileEntityInterface) {
				interfaceObject = (ITileEntityInterface) element;
			} else if (element instanceof TileEntityCamouflage) {
				camouflageObject = (TileEntityCamouflage) element;
			}
			for (ClusterMethodRegistration clusterMethodRegistration : element.getRegistrations()) {
				methodRegistration.get(clusterMethodRegistration).add(new TileRegistryPair(block, element));
			}
			element.setPos(getPos());
			element.setWorld(world);
			element.setPartOfCluster(true);
		}
	}

	@Override
	public void update() {
		for (TileEntityClusterElement element : elements) {
			setWorldObject(element);
			element.update();
		}

		if (!requestedInfo && world.isRemote) {
			requestedInfo = true;
			requestData();
		}
	}

	public void setWorldObject(TileEntityClusterElement te) {
		if (!te.hasWorld()) {
			te.setWorld(this.world);
		}
	}

	@SideOnly(Side.CLIENT)
	private void requestData() {
		PacketHandler.sendBlockPacket(this, Minecraft.getMinecraft().player, 1);
	}

	public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state, EntityLivingBase entity, ItemStack itemStack) {
		for (TileRegistryPair blockContainer : getRegistrations(ClusterMethodRegistration.ON_BLOCK_PLACED_BY)) {
			setWorldObject(blockContainer.te);
			blockContainer.registry.getBlock().onBlockPlacedBy(world, pos, state, entity, blockContainer.registry.getItemStack());
		}
	}

	private List<TileRegistryPair> getRegistrations(ClusterMethodRegistration method) {
		return methodRegistration.get(method);
	}

	public void onNeighborBlockChange(IBlockAccess world, BlockPos pos, IBlockState state, Block block) {
		for (TileRegistryPair blockContainer : getRegistrations(ClusterMethodRegistration.ON_NEIGHBOR_BLOCK_CHANGED)) {
			setWorldObject(blockContainer.te);
			//            blockContainer.registry.getBlock().onNeighborBlockChange(world, pos, state, block);
		}
	}

	public boolean canConnectRedstone(IBlockState state, IBlockAccess blockAccess, BlockPos pos, EnumFacing side) {
		for (TileRegistryPair blockContainer : getRegistrations(ClusterMethodRegistration.CAN_CONNECT_REDSTONE)) {
			setWorldObject(blockContainer.te);
			if (blockContainer.registry.getBlock().canConnectRedstone(state, blockAccess, pos, side)) {
				return true;
			}
		}

		return false;
	}

	public void onBlockAdded(World world, BlockPos pos, IBlockState state) {
		for (TileRegistryPair blockContainer : getRegistrations(ClusterMethodRegistration.ON_BLOCK_ADDED)) {
			setWorldObject(blockContainer.te);
			blockContainer.registry.getBlock().onBlockAdded(world, pos, state);
		}
	}

	public boolean shouldCheckWeakPower(IBlockState state, IBlockAccess blockAccess, BlockPos pos, EnumFacing side) {
		for (TileRegistryPair blockContainer : getRegistrations(ClusterMethodRegistration.SHOULD_CHECK_WEAK_POWER)) {
			setWorldObject(blockContainer.te);
			if (blockContainer.registry.getBlock().shouldCheckWeakPower(state, blockAccess, pos, side)) {
				return true;
			}
		}

		return false;
	}

	public int isProvidingWeakPower(IBlockState state, IBlockAccess blockAccess, BlockPos pos, EnumFacing side) {
		int max = 0;

		for (TileRegistryPair blockContainer : getRegistrations(ClusterMethodRegistration.IS_PROVIDING_WEAK_POWER)) {
			setWorldObject(blockContainer.te);
			max = Math.max(max, blockContainer.registry.getBlock().getStrongPower(state, blockAccess, pos, side));
		}

		return max;
	}

	public int isProvidingStrongPower(IBlockState state, IBlockAccess blockAccess, BlockPos pos, EnumFacing side) {
		int max = 0;

		for (TileRegistryPair blockContainer : getRegistrations(ClusterMethodRegistration.IS_PROVIDING_STRONG_POWER)) {
			setWorldObject(blockContainer.te);
			max = Math.max(max, blockContainer.registry.getBlock().getWeakPower(state, blockAccess, pos, side));
		}

		return max;
	}

	public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand, ItemStack heldItem, EnumFacing side, float hitX, float hitY, float hitZ) {
		for (TileRegistryPair blockContainer : getRegistrations(ClusterMethodRegistration.ON_BLOCK_ACTIVATED)) {
			setWorldObject(blockContainer.te);
			if (blockContainer.registry.getBlock().onBlockActivated(world, pos, state, player, hand, side, hitX, hitY, hitZ)) {
				return true;
			}
		}

		return false;
	}

	@Override
	public Container getContainer(int id, InventoryPlayer inv) {
		return interfaceObject == null ? null : interfaceObject.getContainer(id, inv);
	}

	@Override
	public GuiScreen getGui(int id, InventoryPlayer inv) {
		return interfaceObject == null ? null : interfaceObject.getGui(id, inv);
	}

	@Override
	public void readAllData(DataReader dr, EntityPlayer player) {
		if (interfaceObject != null) {
			interfaceObject.readAllData(dr, player);
		}
	}

	@Override
	public void readUpdatedData(DataReader dr, EntityPlayer player) {
		if (interfaceObject != null) {
			interfaceObject.readUpdatedData(dr, player);
		}
	}

	@Override
	public void writeAllData(DataWriter dw) {
		if (interfaceObject != null) {
			interfaceObject.writeAllData(dw);
		}
	}

	@Override
	public void readFromNBT(NBTTagCompound tagCompound) {
		super.readFromNBT(tagCompound);

		NBTTagList subList = tagCompound.getTagList(NBT_SUB_BLOCKS, 10);
		List<Byte> bytes   = new ArrayList<Byte>();
		for (int i = 0; i < subList.tagCount(); i++) {
			NBTTagCompound sub = subList.getCompoundTagAt(i);
			bytes.add(sub.getByte(NBT_SUB_BLOCK_ID));
		}
		byte[] byteArr = new byte[bytes.size()];
		for (int i = 0; i < bytes.size(); i++) {
			byteArr[i] = bytes.get(i);
		}
		loadElements(byteArr);
		for (int i = 0; i < subList.tagCount(); i++) {
			NBTTagCompound           sub     = subList.getCompoundTagAt(i);
			TileEntityClusterElement element = elements.get(i);
			element.setMetaData(sub.getByte(NBT_SUB_BLOCK_META));
			element.readContentFromNBT(sub);
		}
	}

	@Override
	public NBTTagCompound writeToNBT(NBTTagCompound compound) {
		NBTTagList subList = new NBTTagList();
		for (int i = 0; i < elements.size(); i++) {
			TileEntityClusterElement               element         = elements.get(i);
			ClusterRegistry.ClusterRegistryElement registryElement = registryList.get(i);
			NBTTagCompound                         sub             = new NBTTagCompound();
			sub.setByte(NBT_SUB_BLOCK_ID, (byte) registryElement.getId());
			sub.setByte(NBT_SUB_BLOCK_META, (byte) element.getBlockMetadata());
			element.writeContentToNBT(sub);

			subList.appendTag(sub);
		}
		compound.setTag(NBT_SUB_BLOCKS, subList);
		return super.writeToNBT(compound);
	}

	@Override
	public void writeData(DataWriter dw, EntityPlayer player, boolean onServer, int id) {
		if (id == 0) {
			if (camouflageObject != null) {
				camouflageObject.writeData(dw, player, onServer, id);
			}
		} else {

			if (onServer) {
				dw.writeData(elements.size(), DataBitHelper.CLUSTER_SUB_ID);
				for (int i = 0; i < elements.size(); i++) {
					dw.writeData((byte) registryList.get(i).getId(), DataBitHelper.CLUSTER_SUB_ID);
				}
				for (int i = 0; i < elements.size(); i++) {
					dw.writeData((byte) elements.get(i).getBlockMetadata(), DataBitHelper.BLOCK_META);
				}
			} else {
				//nothing to write, empty packet
			}
		}
	}

	@Override
	public void readData(DataReader dr, EntityPlayer player, boolean onServer, int id) {
		if (id == 0) {
			if (camouflageObject != null) {
				camouflageObject.readData(dr, player, onServer, id);
			}
		} else {

			if (onServer) {
				//respond by sending the data to the client that required it
				PacketHandler.sendBlockPacket(this, player, 1);
			} else {
				int    length = dr.readData(DataBitHelper.CLUSTER_SUB_ID);
				byte[] types  = new byte[length];
				for (int i = 0; i < length; i++) {
					types[i] = (byte) dr.readData(DataBitHelper.CLUSTER_SUB_ID);
				}
				loadElements(types);
				for (int i = 0; i < length; i++) {
					elements.get(i).setMetaData(dr.readData(DataBitHelper.BLOCK_META));
				}
			}
		}
	}

	@Override
	public int infoBitLength(boolean onServer) {
		return 1;
	}

	public byte[] getTypes() {
		byte[] bytes = new byte[registryList.size()];
		for (int i = 0; i < bytes.length; i++) {
			bytes[i] = (byte) registryList.get(i).getId();
		}

		return bytes;
	}

	private class TileRegistryPair {
		private ClusterRegistry.ClusterRegistryElement registry;
		private TileEntityClusterElement               te;

		private TileRegistryPair(ClusterRegistry.ClusterRegistryElement registry, TileEntityClusterElement te) {
			this.registry = registry;
			this.te = te;
		}
	}
}
