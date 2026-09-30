package ca.teamdman.sfm.common.enchantment;

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
{% when '1.21', '1.21.1', '26.1.2' %}
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.enchantment.ItemEnchantments;

{% endcase %}
public enum SFMEnchantmentCollectionKind {
    /// Enchanted books hold enchantments without them being active
    HoldingLikeABook,
    /// Tools can be enchanted with silk touch and stuff
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
    EnchantedLikeATool,
{% when '1.21', '1.21.1', '26.1.2' %}
    EnchantedLikeATool;

    public DataComponentType<ItemEnchantments> componentType() {
        return switch(this) {
            case HoldingLikeABook -> DataComponents.STORED_ENCHANTMENTS;
            case EnchantedLikeATool -> DataComponents.ENCHANTMENTS;
        };
    }
{% endcase %}
}
