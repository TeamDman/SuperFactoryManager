package vswe.superfactory.wrappers;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

public class InventoryWrapperPlayer extends InventoryWrapper {
	private EntityPlayer player;

	public InventoryWrapperPlayer(EntityPlayer player) {
		super(player.inventory);
		this.player = player;
	}

	@Override
	public boolean isEmpty() {
		return false;
	}

	@Override
	public boolean isItemValidForSlot(int i, ItemStack itemstack) {
		if (i >= 36 && i <= 39 && !itemstack.isEmpty()) {
			EntityEquipmentSlot armorType = EntityEquipmentSlot.values()[39 - i];
			Item                item      = itemstack.getItem();
			return item != null && item.isValidArmor(itemstack, armorType, player);
		} else {
			return super.isItemValidForSlot(i, itemstack);
		}
	}
}
