package ca.teamdman.sfm.common.compat;

import ca.teamdman.sfm.common.registry.SFMWellKnownRegistries;
import ca.teamdman.sfm.common.util.NotStored;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
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

}
