package ca.teamdman.sfm.common.compat;

import ca.teamdman.sfm.common.registry.SFMWellKnownRegistries;
import net.minecraft.core.BlockPos;
{% case minecraft_version %}
{% when "26.1.2" %}
import net.minecraft.resources.Identifier;
{% else %}
import net.minecraft.resources.ResourceLocation;
{% endcase %}
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1" %}
import net.minecraftforge.fml.ModList;
{% else %}
import net.neoforged.fml.ModList;
{% endcase %}

public class SFMModCompat {
    public static boolean isMekanismLoaded() {
        return isModLoaded("mekanism");
    }

    public static boolean isAE2Loaded() {
        return isModLoaded("ae2");
    }

{% if features.computercraft %}
    public static boolean isComputerCraftLoaded() {
        return isModLoaded("computercraft");
    }

{% endif %}
    public static boolean isModLoaded(String modid) {
        return ModList.get().getModContainerById(modid).isPresent();
    }

    public static boolean isMekanismBlock(
            Level level,
            BlockPos pos
    ) {
        Block block = level.getBlockState(pos).getBlock();
{% case minecraft_version %}
{% when "26.1.2" %}
        Identifier blockId = SFMWellKnownRegistries.BLOCKS.getId(block);
{% else %}
        ResourceLocation blockId = SFMWellKnownRegistries.BLOCKS.getId(block);
{% endcase %}
        assert blockId != null;
        return blockId.getNamespace().equals("mekanism");
    }
}
