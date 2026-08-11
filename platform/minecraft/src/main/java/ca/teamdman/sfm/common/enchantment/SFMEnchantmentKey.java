package ca.teamdman.sfm.common.enchantment;

import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;

public record SFMEnchantmentKey(
        @MCVersionDependentBehaviour
        Holder<Enchantment> inner
) {


    @SuppressWarnings("OptionalGetWithoutIsPresent")
    public SFMEnchantmentKey(
            RegistryAccess registryAccess,
            ResourceKey<Enchantment> enchantmentId
    ) {

        this(registryAccess.registry(Registries.ENCHANTMENT).get().getHolderOrThrow(enchantmentId));

    }

    @MCVersionDependentBehaviour
    public int getMaxLevel() {

        return inner.value().getMaxLevel();
    }

    @MCVersionDependentBehaviour
    public boolean canEnchant(ItemStack checkStack) {

        return inner.value().canEnchant(checkStack);
    }

}
