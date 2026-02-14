package vswe.superfactory.components.internal;

import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;

public class SlotStackInventoryHolder implements IItemBufferSubElement {
	private IItemHandler inventory;
	private ItemStack    itemStack;
	private int          sizeRemaining;
	private int          slot;

	public SlotStackInventoryHolder(ItemStack itemStack, IItemHandler inventory, int slot) {
		this.itemStack = itemStack;
		this.inventory = inventory;
		this.slot = slot;
		this.sizeRemaining = itemStack.getCount();
	}

	@Override
	public void remove() {
		if (itemStack.getCount() == 0) {
			IItemHandler handler = getInventory();
			if (handler instanceof IItemHandlerModifiable)
				((IItemHandlerModifiable) handler).setStackInSlot(getSlot(), ItemStack.EMPTY);
			else
				getInventory().extractItem(getSlot(), Integer.MAX_VALUE, false);
		}
	}

	public IItemHandler getInventory() {
		return inventory;
	}


	public int getSlot() {
		return slot;
	}

	@Override
	public void onUpdate() {
	}

	public int getSizeRemaining() {
		return Math.min(itemStack.getCount(), sizeRemaining);
	}

	public void reduceAmount(int amount) {
		//todo: fix ``` .isEmpty() ? ``` for count adjustment
		//todo: adjust stack size?
		if (amount == 0 || getSizeRemaining() < amount)
			return;
		inventory.extractItem(getSlot(), amount, false);
		sizeRemaining -= amount;
	}

	public ItemStack getItemStack() {
		return itemStack;
	}

	public SlotStackInventoryHolder getSplitElement(int elementAmount, int id, boolean fair) {
		SlotStackInventoryHolder element   = new SlotStackInventoryHolder(this.itemStack, this.inventory, this.slot);
		int                      oldAmount = getSizeRemaining();
		int                      amount    = oldAmount / elementAmount;
		if (!fair) {
			int amountLeft = oldAmount % elementAmount;
			if (id < amountLeft) {
				amount++;
			}
		}

		element.sizeRemaining = amount;
		return element;
	}

	@Override
	public String toString() {
		return  "itemStack=" + itemStack +
				", sizeRemaining=" + getSizeRemaining() +
				", slot=" + slot;
	}
}
