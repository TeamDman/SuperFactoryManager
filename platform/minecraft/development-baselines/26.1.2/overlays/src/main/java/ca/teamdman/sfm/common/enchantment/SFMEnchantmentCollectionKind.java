package ca.teamdman.sfm.common.enchantment;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.enchantment.ItemEnchantments;

public enum SFMEnchantmentCollectionKind {
    /// Enchanted books hold enchantments without them being active
    HoldingLikeABook,
    /// Tools can be enchanted with silk touch and stuff
    EnchantedLikeATool;

    public DataComponentType<ItemEnchantments> componentType() {
        return switch(this) {
            case HoldingLikeABook -> DataComponents.STORED_ENCHANTMENTS;
            case EnchantedLikeATool -> DataComponents.ENCHANTMENTS;
        };
    }
}
