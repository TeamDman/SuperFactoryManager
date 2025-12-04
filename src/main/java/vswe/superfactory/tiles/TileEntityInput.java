package vswe.superfactory.tiles;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import vswe.superfactory.SuperFactoryManager;
import vswe.superfactory.blocks.ClusterMethodRegistration;
import vswe.superfactory.blocks.IRedstoneNode;
import vswe.superfactory.blocks.ISystemListener;
import vswe.superfactory.blocks.ITriggerNode;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

public class TileEntityInput extends TileEntityClusterElement implements IRedstoneNode, ISystemListener, ITriggerNode {
	private static final String                  NBT_POWER   = "Power";
	private static final String                  NBT_SIDES   = "Sides";
	private              int[]                   isPowered   = new int[EnumFacing.values().length];
	private              List<TileEntityManager> managerList = new ArrayList<TileEntityManager>();
	private              int[]                   oldPowered  = new int[EnumFacing.values().length];

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

	public void triggerRedstone() {
		isPowered = new int[isPowered.length];
		for (int i = 0; i < isPowered.length; i++) {
			EnumFacing direction = EnumFacing.byIndex(i);
			BlockPos   pos       = getPos().offset(direction);
			isPowered[i] = world.getRedstonePower(pos, direction);
		}

		managerList.forEach((m) -> m.triggerRedstone(this));

		oldPowered = isPowered;
		//        System.out.print(world.getRedstonePower(pos, null));
	}

	@Override
	public int[] getPower() {
		return isPowered;
	}

	@Override
	public void writeContentToNBT(NBTTagCompound nbtTagCompound) {
		nbtTagCompound.setByte(SuperFactoryManager.NBT_PROTOCOL_VERSION, SuperFactoryManager.NBT_CURRENT_PROTOCOL_VERSION);

		NBTTagList sidesTag = new NBTTagList();
		for (int power : isPowered) {
			NBTTagCompound sideTag = new NBTTagCompound();

			sideTag.setByte(NBT_POWER, (byte) power);

			sidesTag.appendTag(sideTag);
		}


		nbtTagCompound.setTag(NBT_SIDES, sidesTag);
	}

	@Override
	public void readContentFromNBT(NBTTagCompound nbtTagCompound) {
		int version = nbtTagCompound.getByte(SuperFactoryManager.NBT_PROTOCOL_VERSION);


		NBTTagList sidesTag = nbtTagCompound.getTagList(NBT_SIDES, 10);
		for (int i = 0; i < sidesTag.tagCount(); i++) {

			NBTTagCompound sideTag = sidesTag.getCompoundTagAt(i);

			oldPowered[i] = isPowered[i] = sideTag.getByte(NBT_POWER);
		}
	}

	@Override
	protected EnumSet<ClusterMethodRegistration> getRegistrations() {
		return EnumSet.of(ClusterMethodRegistration.CAN_CONNECT_REDSTONE, ClusterMethodRegistration.ON_NEIGHBOR_BLOCK_CHANGED, ClusterMethodRegistration.ON_BLOCK_ADDED);
	}

	@Override
	public int[] getData() {
		return isPowered;
	}

	@Override
	public int[] getOldData() {
		return oldPowered;
	}
}
