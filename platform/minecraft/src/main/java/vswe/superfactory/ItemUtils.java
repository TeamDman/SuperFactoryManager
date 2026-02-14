package vswe.superfactory;

import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

/**
 * Created by Gigabit101 on 13/11/2016.
 */
public class ItemUtils {
	public static boolean isItemEqual(ItemStack a, ItemStack b, boolean matchDamage, boolean matchNBT, boolean useOreDic) {
		if (isItemEqual(a, b, matchDamage, matchNBT)) {
			return true;
		}
		if (a.isEmpty() || b.isEmpty())
			return false;
		if (useOreDic) {
			for (int inta : OreDictionary.getOreIDs(a)) {
				for (int intb : OreDictionary.getOreIDs(b)) {
					if (inta == intb) {
						return true;
					}
				}
			}
		}
		return false;
	}

	public static boolean isItemEqual(final ItemStack a, final ItemStack b, final boolean matchDamage, final boolean matchNBT) {
		if (a.isEmpty() || b.isEmpty())
			return false;
		if (a.getItem() != b.getItem())
			return false;
		if (matchNBT && !ItemStack.areItemStackTagsEqual(a, b))
			return false;
		if (matchDamage && a.getHasSubtypes()) {
			if (isWildcard(a) || isWildcard(b))
				return true;
			return a.getItemDamage() == b.getItemDamage();
		}
		return true;
	}

	public static boolean isWildcard(ItemStack stack) {
		return isWildcard(stack.getItemDamage());
	}

	public static boolean isWildcard(int damage) {
		return damage == -1 || damage == OreDictionary.WILDCARD_VALUE;
	}
}
