package ca.teamdman.sfm.common.registry;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.recipe.DiskResetRecipe;
import ca.teamdman.sfm.common.recipe.LabelGunResetRecipe;
import ca.teamdman.sfm.common.recipe.PrintingPressRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleRecipeSerializer;
import net.minecraftforge.eventbus.api.IEventBus;

public class SFMRecipeSerializers {
    private static final SFMDeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            new SFMDeferredRegisterBuilder<RecipeSerializer<?>>()
                    .namespace(SFM.MOD_ID)
                    .registry(SFMWellKnownRegistries.RECIPE_SERIALIZERS.registryKey())
                    .build();

    public static final SFMRegistryObject<RecipeSerializer<?>, PrintingPressRecipe.Serializer> PRINTING_PRESS
            = RECIPE_SERIALIZERS.register(
            "printing_press",
            PrintingPressRecipe.Serializer::new
    );

    public static final SFMRegistryObject<RecipeSerializer<?>, SimpleRecipeSerializer<DiskResetRecipe>> DISK_RESET
            = RECIPE_SERIALIZERS.register(
            "disk_reset",
            () -> new SimpleRecipeSerializer<>(DiskResetRecipe::new)
    );

    public static final SFMRegistryObject<RecipeSerializer<?>, SimpleRecipeSerializer<LabelGunResetRecipe>> LABEL_GUN_RESET
            = RECIPE_SERIALIZERS.register(
            "label_gun_reset",
            () -> new SimpleRecipeSerializer<>(LabelGunResetRecipe::new)
    );

    public static void register(IEventBus bus) {

        RECIPE_SERIALIZERS.register(bus);
    }

}
