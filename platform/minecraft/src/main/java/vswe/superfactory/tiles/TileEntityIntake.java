package vswe.superfactory.tiles;

import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.wrapper.InvWrapper;
import vswe.superfactory.blocks.BlockCableIntake;
import vswe.superfactory.blocks.ClusterMethodRegistration;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;

public class TileEntityIntake extends TileEntityClusterElement implements IInventory {
	private static final int DISTANCE = 3;
	private List<EntityItem> items;

	@Override
	public int getSizeInventory() {
		updateInventory();
		return items.size() + 1; //always leave an empty slot
	}

	@Override
	public boolean isEmpty() {
		return false;
	}

	@Override
	public ItemStack getStackInSlot(int id) {
		updateInventory();
		id--;
		if (id < 0 || !canPickUp(items.get(id))) {
			return ItemStack.EMPTY;
		} else {
			return items.get(id).getItem();
		}
	}

	@Override
	public ItemStack decrStackSize(int id, int count) {
		ItemStack item = getStackInSlot(id);
		if (!item.isEmpty()) {
			if (item.getCount() <= count) {
				setInventorySlotContents(id, ItemStack.EMPTY);
				return item;
			}

			ItemStack ret = item.splitStack(count);

			if (item.getCount() == 0) {
				setInventorySlotContents(id, ItemStack.EMPTY);
			}

			return ret;
		} else {
			return ItemStack.EMPTY;
		}
	}

	@Override
	public ItemStack removeStackFromSlot(int i) {
		return ItemStack.EMPTY;
	}

	@Override
	public void setInventorySlotContents(int id, ItemStack itemstack) {
		updateInventory();
		id--;
		if (id < 0 || !canPickUp(items.get(id))) {
			if (!itemstack.isEmpty()) {
				EnumFacing direction = EnumFacing.byIndex(BlockCableIntake.getSideMeta(getBlockMetadata()) % EnumFacing.values().length);

				double posX = getPos().getX() + 0.5 + direction.getXOffset() * 0.75;
				double posY = getPos().getY() + 0.5 + direction.getYOffset() * 0.75;
				double posZ = getPos().getZ() + 0.5 + direction.getZOffset() * 0.75;

				if (direction.getYOffset() == 0) {
					posY -= 0.1;
				}

				EntityItem item = new EntityItem(world, posX, posY, posZ, itemstack);

				item.motionX = direction.getXOffset() * 0.2;
				item.motionY = direction.getYOffset() * 0.2;
				item.motionZ = direction.getZOffset() * 0.2;


				item.setPickupDelay(40);
				world.spawnEntity(item);


				if (id < 0) {
					items.add(item);
				} else {
					items.set(id, item);
				}
			}
		} else if (!itemstack.isEmpty()) {
			items.get(id).setItem(itemstack);
		} else {
			//seems to be an issue with setting it to null
			items.get(id).setItem(items.get(id).getItem().copy());
			items.get(id).getItem().setCount(0);
			items.get(id).setDead();
		}
	}

	@Override
	public int getInventoryStackLimit() {
		return 64;
	}

	@Override
	public boolean isUsableByPlayer(EntityPlayer entityplayer) {
		return false;
	}

	@Override
	public void openInventory(EntityPlayer player) {
	}

	@Override
	public void closeInventory(EntityPlayer player) {
	}

	@Override
	public boolean isItemValidForSlot(int i, ItemStack itemstack) {
		return true;
	}

	@Override
	public int getField(int id) {
		return 0;
	}

	@Override
	public void setField(int id, int value) {
	}

	@Override
	public int getFieldCount() {
		return 0;
	}

	@Override
	public void clear() {
		items.clear();
	}

	private void updateInventory() {
		if (items == null) {
			items = new ArrayList<EntityItem>();

			int lowX = getPos().getX() - DISTANCE;
			int lowY = getPos().getY() - DISTANCE;
			int lowZ = getPos().getZ() - DISTANCE;

			int highX = getPos().getX() + 1 + DISTANCE;
			int highY = getPos().getY() + 1 + DISTANCE;
			int highZ = getPos().getZ() + 1 + DISTANCE;

			items = world.getEntitiesWithinAABB(EntityItem.class, new AxisAlignedBB(lowX, lowY, lowZ, highX, highY, highZ));

			//remove items we can't use right away, this check is done when we interact with items too, to make sure it hasn't changed
			for (Iterator<EntityItem> iterator = items.iterator(); iterator.hasNext(); ) {
				EntityItem next = iterator.next();
				if (!canPickUp(next)) {
					iterator.remove();
				}
			}
		}
	}

	private boolean canPickUp(EntityItem item) {
		return !item.isDead && (!item.cannotPickup() || BlockCableIntake.isAdvanced(getBlockMetadata()));
	}

	@Override
	public String getName() {
		return SFMBlocks.CABLE_INTAKE.getLocalizedName();
	}

	@Override
	public boolean hasCustomName() {
		return true;
	}

	@Override
	public ITextComponent getDisplayName() {
		return new TextComponentString(SFMBlocks.CABLE_INTAKE.getLocalizedName());
	}

	@Override
	public boolean hasCapability(Capability<?> capability, EnumFacing facing) {
		if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
			return true;
		}
		return super.hasCapability(capability, facing);
	}

	@Override
	public <T> T getCapability(Capability<T> capability, EnumFacing facing) {
		if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
			return (T) new InvWrapper(this);
		}
		return super.getCapability(capability, facing);
	}

	@Override
	public void update() {
		items = null;
	}

	@Override
	protected EnumSet<ClusterMethodRegistration> getRegistrations() {
		return EnumSet.of(ClusterMethodRegistration.ON_BLOCK_PLACED_BY);
	}
}
