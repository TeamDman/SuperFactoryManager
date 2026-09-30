package ca.teamdman.sfm.common.enchantment;

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
import net.minecraft.world.item.EnchantedBookItem;
{% when '26.1.2' %}
{% endcase %}
import net.minecraft.world.item.ItemStack;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
{% when '26.1.2' %}
import net.minecraft.world.item.enchantment.EnchantmentHelper;
{% endcase %}
import net.minecraft.world.item.enchantment.EnchantmentInstance;

public record SFMEnchantmentEntry(
        SFMEnchantmentKey key,

        int level
) {

    public EnchantmentInstance createEnchantmentInstance() {

        return new EnchantmentInstance(this.key().inner(), this.level());
    }

    public ItemStack createEnchantedBook() {
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
        return EnchantedBookItem.createForEnchantment(this.createEnchantmentInstance());
{% when '26.1.2' %}
        return EnchantmentHelper.createBook(this.createEnchantmentInstance());
{% endcase %}
    }

}
