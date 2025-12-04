package vswe.superfactory.tiles;

import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;
import vswe.superfactory.blocks.ClusterMethodRegistration;
import vswe.superfactory.registry.ClusterRegistry;

import java.util.EnumSet;

public abstract class TileEntityClusterElement extends TileEntity implements ITickable {
	private boolean                                isPartOfCluster;
	private int                                    meta;
	private ClusterRegistry.ClusterRegistryElement registryElement;

	protected TileEntityClusterElement() {
		registryElement = ClusterRegistry.get(this);
	}

	public ItemStack getItemStackFromBlock() { // todo: fix cluster registry
		return registryElement != null ? registryElement.getItemStack(getBlockMetadata()) : new ItemStack(Blocks.BEDROCK);
	}

	public boolean isPartOfCluster() {
		return isPartOfCluster;
	}

	public void setPartOfCluster(boolean partOfCluster) {
		isPartOfCluster = partOfCluster;
	}

	public void setState(IBlockState state) {
		if (isPartOfCluster) {
			this.meta = state.getBlock().getMetaFromState(state);
		} else {
			world.setBlockState(pos, state, 2);
		}
	}

	public void setMetaData(int meta) {
		if (isPartOfCluster) {
			this.meta = meta;
		} else {
			world.setBlockState(pos, world.getBlockState(pos).getBlock().getStateFromMeta(meta), 2);
		}
	}

	@Override
	public final void readFromNBT(NBTTagCompound tagCompound) {
		super.readFromNBT(tagCompound);
		readContentFromNBT(tagCompound);
	}

	@Override
	public NBTTagCompound writeToNBT(NBTTagCompound compound) {
		writeContentToNBT(compound);
		return super.writeToNBT(compound);//todo: java.lang.RuntimeException: class vswe.superfactory.tiles.TileEntityBreaker is missing a mapping! This is a bug
	}

	@Override
	public int getBlockMetadata() {
		if (isPartOfCluster) {
			return meta;
		} else {
			return super.getBlockMetadata();
		}
	}

	protected void writeContentToNBT(NBTTagCompound tagCompound) {
	}

	protected void readContentFromNBT(NBTTagCompound tagCompound) {
	}

	@Override
	public void update() {

	}

	protected abstract EnumSet<ClusterMethodRegistration> getRegistrations();
}
