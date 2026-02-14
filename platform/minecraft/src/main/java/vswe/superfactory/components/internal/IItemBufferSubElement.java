package vswe.superfactory.components.internal;

import net.minecraft.item.ItemStack;

public interface IItemBufferSubElement {
	void remove();

	void onUpdate();

	int getSizeRemaining();

	void reduceAmount(int amount);

	ItemStack getItemStack();
}
