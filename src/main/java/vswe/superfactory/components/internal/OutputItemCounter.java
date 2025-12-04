package vswe.superfactory.components.internal;

import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

import java.util.IdentityHashMap;
import java.util.List;

/**
 * Internal object used to keep track of the flow of items into output inventories
 */
public class OutputItemCounter {
	private int         currentBufferStackSize;
	private int         currentInventoryStackSize;
	private ItemSetting setting;
	private boolean     useWhiteList;

	public OutputItemCounter(List<ItemBufferElement> itemBuffer, List<SlotInventoryHolder> inventories, SlotInventoryHolder inventoryHolder, ItemSetting setting, boolean useWhiteList) {
		this.setting = setting;
		this.useWhiteList = useWhiteList;

		if (setting != null && ((ItemSetting) setting).getItem() != null && setting.isLimitedByAmount()) {
			if (useWhiteList) {
				if (inventories.get(0).isShared()) {
					for (SlotInventoryHolder slotInventoryHolder : inventories) {
						addInventory(slotInventoryHolder);
					}
				} else {
					addInventory(inventoryHolder);
				}
			} else {
				for (ItemBufferElement itemBufferElement : itemBuffer) {
					currentBufferStackSize += itemBufferElement.getBufferSize(setting);
				}
			}
		}
	}

	private void addInventory(SlotInventoryHolder inventoryHolder) {
		IdentityHashMap<ItemStack, Object> seenStacks = new IdentityHashMap<>();
		for (SideSlotTarget sideSlotTarget : inventoryHolder.getValidSlots().values()) {
			IItemHandler inventory = inventoryHolder.getInventory(sideSlotTarget.getSide());
			for (int slot : sideSlotTarget.getSlots()) {
				ItemStack item = inventory.getStackInSlot(slot);
				if (!seenStacks.containsKey(item) && setting.isEqualForCommandExecutor(item)) {
					seenStacks.put(item, null);
					currentInventoryStackSize += item.getCount();
				}
			}
		}
	}

	public boolean areSettingsSame(Setting setting) {
		return (this.setting == null && setting == null) || (this.setting != null && setting != null && this.setting.getId() == setting.getId());
	}

	public int retrieveItemCount(int desiredItemCount) {
		if (setting == null || !setting.isLimitedByAmount()) {
			return desiredItemCount;
		} else {
			int itemsAllowedToBeMoved;
			if (useWhiteList) {
//				itemsAllowedToBeMoved = setting.getItem().getCount() - currentInventoryStackSize;
				itemsAllowedToBeMoved = setting.getAmount() - currentInventoryStackSize;
			} else {
//				itemsAllowedToBeMoved = setting.getItem().getCount();
				itemsAllowedToBeMoved = currentBufferStackSize - setting.getAmount();
			}
			return Math.min(itemsAllowedToBeMoved, desiredItemCount);
		}
	}

	public void modifyStackSize(int itemsToMove) {
		if (useWhiteList) {
			currentInventoryStackSize += itemsToMove;
		} else {
			currentBufferStackSize -= itemsToMove;
		}
	}

	@Override
	public String toString() {
		return "stack=" + (setting == null ? "unknown" : setting.getItem().toString()) + ", bufferStack=" + currentBufferStackSize + ", invStack=" + currentInventoryStackSize + (useWhiteList ? ", whitelisted" : "");
	}
}
