package vswe.superfactory.blocks;

import net.minecraft.block.Block;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import vswe.superfactory.SuperFactoryManager;

public class ItemRelay extends ItemBlock {
	public ItemRelay(Block block) {
		super(block);
		setHasSubtypes(true);
		setMaxDamage(0);
	}

	@Override
	public String getTranslationKey(ItemStack item) {
		return "tile." + SuperFactoryManager.UNLOCALIZED_START + (BlockCableDirectionAdvanced.isAdvanced(item.getItemDamage()) ? "cable_relay_advanced" : "cable_relay");
	}
}
