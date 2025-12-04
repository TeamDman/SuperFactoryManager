package vswe.superfactory.blocks;

import net.minecraft.block.Block;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import vswe.superfactory.SuperFactoryManager;
import vswe.superfactory.tiles.TileEntityCamouflage;

public class ItemCamouflage extends ItemBlock {
	public ItemCamouflage(Block block) {
		super(block);
		setHasSubtypes(true);
		setMaxDamage(0);
	}

	@Override
	public String getTranslationKey(ItemStack item) {
		return "tile." + SuperFactoryManager.UNLOCALIZED_START + TileEntityCamouflage.CamouflageType.values()[BlockCableCamouflages.getId(item.getItemDamage())].getUnlocalized();
	}
}
