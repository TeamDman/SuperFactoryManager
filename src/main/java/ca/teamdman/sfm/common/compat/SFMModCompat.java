package ca.teamdman.sfm.common.compat;

import ca.teamdman.sfm.common.registry.SFMWellKnownRegistries;
import ca.teamdman.sfm.common.util.NotStored;
import net.minecraft.util.math.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.block.Block;
import net.minecraftforge.fml.ModList;

public class SFMModCompat {
    public static boolean isMekanismLoaded() {
        return isModLoaded("mekanism");
    }

    public static boolean isAE2Loaded() {
        return isModLoaded("ae2");
    }

    public static boolean isModLoaded(String modid) {
        return ModList.get().getModContainerById(modid).isPresent();
    }

    public static boolean isMekanismBlock(
            Level level,
            @NotStored BlockPos pos
    ) {
        Block block = level.getBlockState(pos).getBlock();
        ResourceLocation blockId = SFMWellKnownRegistries.BLOCKS.getId(block);
        assert blockId != null;
        return blockId.getNamespace().equals("mekanism");
    }
}
