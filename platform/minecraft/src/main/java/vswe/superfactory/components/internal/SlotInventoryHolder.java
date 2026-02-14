package vswe.superfactory.components.internal;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;
import vswe.superfactory.blocks.IRedstoneNode;
import vswe.superfactory.blocks.ITriggerNode;
import vswe.superfactory.tiles.*;
import vswe.superfactory.util.CapabilityUtils;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.Map;

public class SlotInventoryHolder {
	private int                             id;
	private TileEntity                      inventory;
	private int                             sharedOption;
	private Map<EnumFacing, SideSlotTarget> validSlots;

	public SlotInventoryHolder(int id, TileEntity inventory, int sharedOption) {
		this.id = id;
		this.inventory = inventory;
		this.sharedOption = sharedOption;
	}

	public int getId() {
		return id;
	}

	@Nullable
	public IItemHandler getInventory(EnumFacing facing) {
		return CapabilityUtils.getItemHandler(inventory, facing);
	}

	@Nullable
	public IFluidHandler getTank(EnumFacing facing) {
		return CapabilityUtils.getFluidHandler(inventory, facing);
	}

	public TileEntityOutput getEmitter() {
		return (TileEntityOutput) inventory;
	}

	public IRedstoneNode getNode() {
		return (IRedstoneNode) inventory;
	}

	public TileEntityInput getReceiver() {
		return (TileEntityInput) inventory;
	}

	public TileEntityBUD getBUD() {
		return (TileEntityBUD) inventory;
	}

	public TileEntityCamouflage getCamouflage() {
		return (TileEntityCamouflage) inventory;
	}

	public TileEntitySignUpdater getSign() {
		return (TileEntitySignUpdater) inventory;
	}

	public Map<EnumFacing, SideSlotTarget> getValidSlots() {
		if (validSlots == null) {
			validSlots = new LinkedHashMap<>();
		}
		return validSlots;
	}

	public boolean isShared() {
		return sharedOption == 0;
	}

	public int getSharedOption() {
		return sharedOption;
	}

	@Override
	public int hashCode() {
		return inventory.hashCode();
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (o == null || getClass() != o.getClass())
			return false;

		SlotInventoryHolder that = (SlotInventoryHolder) o;

		return inventory.getPos().getX() == that.inventory.getPos().getX() && inventory.getPos().getY() == that.inventory.getPos().getY() && inventory.getPos().getZ() == that.inventory.getPos().getY();
	}

	public TileEntity getTile() {
		return inventory;
	}

	public ITriggerNode getTrigger() {
		return (ITriggerNode) inventory;
	}
}
