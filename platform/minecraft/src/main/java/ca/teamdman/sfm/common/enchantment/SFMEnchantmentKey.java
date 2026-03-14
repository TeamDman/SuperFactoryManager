package ca.teamdman.sfm.common.enchantment;

import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;

public record SFMEnchantmentKey(
        @MCVersionDependentBehaviour
        Enchantment inner
) {

    @MCVersionDependentBehaviour
    public int getMaxLevel() {

        return inner.getMaxLevel();
    }

    @MCVersionDependentBehaviour
    public boolean canEnchant(ItemStack checkStack) {

        return inner.canEnchant(checkStack);
    }

}
