package vswe.superfactory.tiles;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import vswe.superfactory.SuperFactoryManager;
import vswe.superfactory.blocks.ClusterMethodRegistration;
import vswe.superfactory.blocks.ISystemListener;
import vswe.superfactory.blocks.ITriggerNode;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

public class TileEntityBUD extends TileEntityClusterElement implements ISystemListener, ITriggerNode {
	private static final String NBT_DATA  = "Data";
	private static final String NBT_SIDES = "Sides";
	private int[]                   data        = new int[EnumFacing.values().length];
	private List<TileEntityManager> managerList = new ArrayList<TileEntityManager>();
	private int[]                   oldData     = new int[EnumFacing.values().length];

	@Override
	public void added(TileEntityManager owner) {
		if (!managerList.contains(owner)) {
			managerList.add(owner);
		}
	}

	@Override
	public void removed(TileEntityManager owner) {
		managerList.remove(owner);
	}

	public void onTrigger() {
		updateData();
		for (int i = managerList.size() - 1; i >= 0; i--) {
			managerList.get(i).triggerBUD(this);
		}
		makeOld();
	}

	public void updateData() {
		if (world != null) {
			data = new int[data.length];
			for (int i = 0; i < data.length; i++) {
				EnumFacing direction = EnumFacing.byIndex(i);
				int        x         = direction.getXOffset() + this.getPos().getX();
				int        y         = direction.getYOffset() + this.getPos().getY();
				int        z         = direction.getZOffset() + this.getPos().getZ();

				IBlockState state = world.getBlockState(new BlockPos(x, y, z));

				data[i] = (Block.getIdFromBlock(state.getBlock()) << 4) | (state.getBlock().getMetaFromState(state) & 15);
			}
		}
	}

	public void makeOld() {
		oldData = data;
	}

	@Override
	public int[] getData() {
		return data;
	}

	@Override
	public int[] getOldData() {
		return oldData;
	}

	@Override
	public void readContentFromNBT(NBTTagCompound nbtTagCompound) {
		int version = nbtTagCompound.getByte(SuperFactoryManager.NBT_PROTOCOL_VERSION);


		NBTTagList sidesTag = nbtTagCompound.getTagList(NBT_SIDES, 10);
		for (int i = 0; i < sidesTag.tagCount(); i++) {

			NBTTagCompound sideTag = sidesTag.getCompoundTagAt(i);

			oldData[i] = data[i] = sideTag.getShort(NBT_DATA);
		}
	}


	@Override
	public void writeContentToNBT(NBTTagCompound nbtTagCompound) {
		nbtTagCompound.setByte(SuperFactoryManager.NBT_PROTOCOL_VERSION, SuperFactoryManager.NBT_CURRENT_PROTOCOL_VERSION);

		NBTTagList sidesTag = new NBTTagList();
		for (int i = 0; i < data.length; i++) {
			NBTTagCompound sideTag = new NBTTagCompound();

			sideTag.setShort(NBT_DATA, (short) data[i]);

			sidesTag.appendTag(sideTag);
		}


		nbtTagCompound.setTag(NBT_SIDES, sidesTag);
	}

	@Override
	protected EnumSet<ClusterMethodRegistration> getRegistrations() {
		return EnumSet.of(ClusterMethodRegistration.ON_NEIGHBOR_BLOCK_CHANGED);
	}
}
