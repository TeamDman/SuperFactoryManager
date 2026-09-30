package ca.teamdman.sfm.common.registry.registration;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.recipe.PrintingPressRecipe;
import ca.teamdman.sfm.common.registry.SFMDeferredRegister;
import ca.teamdman.sfm.common.registry.SFMDeferredRegisterBuilder;
import ca.teamdman.sfm.common.registry.SFMRegistryObject;
import ca.teamdman.sfm.common.registry.SFMWellKnownRegistries;
import ca.teamdman.sfm.common.util.SFMResourceLocation;
import net.minecraft.world.item.crafting.RecipeType;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1' %}
import net.minecraftforge.eventbus.api.IEventBus;
{% when '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
import net.neoforged.bus.api.IEventBus;
{% endcase %}

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
