package ca.teamdman.sfm.common.registry.registration;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.recipe.PrintingPressRecipe;
import ca.teamdman.sfm.common.registry.SFMDeferredRegister;
import ca.teamdman.sfm.common.registry.SFMDeferredRegisterBuilder;
import ca.teamdman.sfm.common.registry.SFMRegistryObject;
import ca.teamdman.sfm.common.registry.SFMWellKnownRegistries;
import ca.teamdman.sfm.common.util.SFMResourceLocation;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.eventbus.api.IEventBus;

public class SFMRecipeTypes {
    private static final SFMDeferredRegister<RecipeType<?>> RECIPE_TYPES =
            new SFMDeferredRegisterBuilder<RecipeType<?>>()
                    .namespace(SFM.MOD_ID)
                    .registry(SFMWellKnownRegistries.RECIPE_TYPES.registryKey())
                    .build();

    public static final SFMRegistryObject<RecipeType<?>, RecipeType<PrintingPressRecipe>> PRINTING_PRESS
            = RECIPE_TYPES.register(
            "printing_press",
            () -> RecipeType.simple(SFMResourceLocation.fromSFMPath("printing_press"))
    );

    public static void register(IEventBus bus) {

        RECIPE_TYPES.register(bus);
    }

}
