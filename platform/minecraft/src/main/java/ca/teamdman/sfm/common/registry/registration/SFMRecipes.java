package ca.teamdman.sfm.common.registry.registration;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.CommonProxy;
import ca.teamdman.sfm.common.recipe.DiskDuplicateRecipe;
import ca.teamdman.sfm.common.recipe.DiskResetRecipe;
import ca.teamdman.sfm.common.recipe.LabelGunResetRecipe;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.ResourceLocation;

public class SFMRecipes {

    public static IRecipe DISK_RESET;
    public static IRecipe LABEL_GUN_RESET;
    public static IRecipe DISK_DUPLICATE;

    public static void initialize() {
        DISK_RESET = prepareRegister(new DiskResetRecipe(), "disk_reset");
        LABEL_GUN_RESET = prepareRegister(new LabelGunResetRecipe(), "label_gun_reset");
        DISK_DUPLICATE = prepareRegister(new DiskDuplicateRecipe(), "disk_duplicate");
    }

    private static <T extends IRecipe> T prepareRegister(T recipe, String name) {
        recipe.setRegistryName(new ResourceLocation(SFM.MOD_ID, name));
        return register(recipe);
    }

    private static <T extends IRecipe> T register(T recipe) {
        CommonProxy.registryPrimer.register(recipe);
        return recipe;
    }
}