package ca.teamdman.sfm.common.enchantment;

import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;

@MCVersionDependentBehaviour
public class SFMEnchantmentAliases {
    public static final ResourceKey<Enchantment> EFFICIENCY = Enchantments.EFFICIENCY;
    public static final ResourceKey<Enchantment> FORTUNE = Enchantments.FORTUNE;
}
