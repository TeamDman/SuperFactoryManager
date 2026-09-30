package ca.teamdman.sfm.common.enchantment;

import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
{% when '1.21', '1.21.1', '26.1.2' %}
import net.minecraft.resources.ResourceKey;
{% endcase %}
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;

@MCVersionDependentBehaviour
public class SFMEnchantmentAliases {
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
    public static final Enchantment EFFICIENCY = Enchantments.BLOCK_EFFICIENCY;
    public static final Enchantment FORTUNE = Enchantments.BLOCK_FORTUNE;
{% when '1.21', '1.21.1', '26.1.2' %}
    public static final ResourceKey<Enchantment> EFFICIENCY = Enchantments.EFFICIENCY;
    public static final ResourceKey<Enchantment> FORTUNE = Enchantments.FORTUNE;
{% endcase %}
}
