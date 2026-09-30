package ca.teamdman.sfm.common.enchantment;

import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
{% when '1.21', '1.21.1' %}
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
{% when '26.1.2' %}
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
{% endcase %}
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;

public record SFMEnchantmentKey(
        @MCVersionDependentBehaviour
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
        Enchantment inner
{% when '1.21', '1.21.1', '26.1.2' %}
        Holder<Enchantment> inner
{% endcase %}
) {

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
{% when '1.21', '1.21.1' %}

    @SuppressWarnings("OptionalGetWithoutIsPresent")
    public SFMEnchantmentKey(
            RegistryAccess registryAccess,
            ResourceKey<Enchantment> enchantmentId
    ) {

        this(registryAccess.registry(Registries.ENCHANTMENT).get().getHolderOrThrow(enchantmentId));

    }

{% when '26.1.2' %}

    @SuppressWarnings("OptionalGetWithoutIsPresent")
    public SFMEnchantmentKey(
            RegistryAccess registryAccess,
            ResourceKey<Enchantment> enchantmentId
    ) {

        this(registryAccess.holderOrThrow(enchantmentId));

    }

{% endcase %}
    @MCVersionDependentBehaviour
    public int getMaxLevel() {

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
        return inner.getMaxLevel();
{% when '1.21', '1.21.1', '26.1.2' %}
        return inner.value().getMaxLevel();
{% endcase %}
    }

    @MCVersionDependentBehaviour
    public boolean canEnchant(ItemStack checkStack) {

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
        return inner.canEnchant(checkStack);
{% when '1.21', '1.21.1' %}
        return inner.value().canEnchant(checkStack);
{% when '26.1.2' %}
        return checkStack.supportsEnchantment(inner);
{% endcase %}
    }

}
