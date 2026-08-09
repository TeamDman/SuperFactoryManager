package ca.teamdman.sfm.common.enchantment;

import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentInstance;

public record SFMEnchantmentEntry(
        SFMEnchantmentKey key,

        int level
) {

    public EnchantmentInstance createEnchantmentInstance() {

        return new EnchantmentInstance(this.key().inner(), this.level());
    }

    public ItemStack createEnchantedBook() {
        return EnchantedBookItem.createForEnchantment(this.createEnchantmentInstance());
    }

}
