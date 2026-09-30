package ca.teamdman.sfm.common.registry.registration;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.recipe.DiskResetRecipe;
import ca.teamdman.sfm.common.recipe.LabelGunResetRecipe;
import ca.teamdman.sfm.common.recipe.PrintingPressRecipe;
import ca.teamdman.sfm.common.registry.SFMDeferredRegister;
import ca.teamdman.sfm.common.registry.SFMDeferredRegisterBuilder;
import ca.teamdman.sfm.common.registry.SFMRegistryObject;
import ca.teamdman.sfm.common.registry.SFMWellKnownRegistries;
import net.minecraft.world.item.crafting.RecipeSerializer;
{% case minecraft_version %}
{% when '1.19.2' %}
import net.minecraft.world.item.crafting.SimpleRecipeSerializer;
import net.minecraftforge.eventbus.api.IEventBus;
{% when '1.19.4', '1.20', '1.20.1' %}
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.minecraftforge.eventbus.api.IEventBus;
{% when '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.neoforged.bus.api.IEventBus;
{% when '26.1.2' %}
import net.neoforged.bus.api.IEventBus;
{% endcase %}

public class SFMRecipeSerializers {
    private static final SFMDeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            new SFMDeferredRegisterBuilder<RecipeSerializer<?>>()
                    .namespace(SFM.MOD_ID)
                    .registry(SFMWellKnownRegistries.RECIPE_SERIALIZERS.registryKey())
                    .build();

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
    public static final SFMRegistryObject<RecipeSerializer<?>, PrintingPressRecipe.Serializer> PRINTING_PRESS
{% when '26.1.2' %}
    public static final SFMRegistryObject<RecipeSerializer<?>, RecipeSerializer<PrintingPressRecipe>> PRINTING_PRESS
{% endcase %}
            = RECIPE_SERIALIZERS.register(
            "printing_press",
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
            PrintingPressRecipe.Serializer::new
{% when '26.1.2' %}
            () -> new RecipeSerializer<>(PrintingPressRecipe.CODEC, PrintingPressRecipe.STREAM_CODEC)
{% endcase %}
    );

{% case minecraft_version %}
{% when '1.19.2' %}
    public static final SFMRegistryObject<RecipeSerializer<?>, SimpleRecipeSerializer<DiskResetRecipe>> DISK_RESET
{% when '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
    public static final SFMRegistryObject<RecipeSerializer<?>, SimpleCraftingRecipeSerializer<DiskResetRecipe>> DISK_RESET
{% when '26.1.2' %}
    public static final SFMRegistryObject<RecipeSerializer<?>, RecipeSerializer<DiskResetRecipe>> DISK_RESET
{% endcase %}
            = RECIPE_SERIALIZERS.register(
            "disk_reset",
{% case minecraft_version %}
{% when '1.19.2' %}
            () -> new SimpleRecipeSerializer<>(DiskResetRecipe::new)
{% when '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
            () -> new SimpleCraftingRecipeSerializer<>(DiskResetRecipe::new)
{% when '26.1.2' %}
            () -> new RecipeSerializer<>(DiskResetRecipe.CODEC, DiskResetRecipe.STREAM_CODEC)
{% endcase %}
    );

{% case minecraft_version %}
{% when '1.19.2' %}
    public static final SFMRegistryObject<RecipeSerializer<?>, SimpleRecipeSerializer<LabelGunResetRecipe>> LABEL_GUN_RESET
{% when '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
    public static final SFMRegistryObject<RecipeSerializer<?>, SimpleCraftingRecipeSerializer<LabelGunResetRecipe>> LABEL_GUN_RESET
{% when '26.1.2' %}
    public static final SFMRegistryObject<RecipeSerializer<?>, RecipeSerializer<LabelGunResetRecipe>> LABEL_GUN_RESET
{% endcase %}
            = RECIPE_SERIALIZERS.register(
            "label_gun_reset",
{% case minecraft_version %}
{% when '1.19.2' %}
            () -> new SimpleRecipeSerializer<>(LabelGunResetRecipe::new)
{% when '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
            () -> new SimpleCraftingRecipeSerializer<>(LabelGunResetRecipe::new)
{% when '26.1.2' %}
            () -> new RecipeSerializer<>(LabelGunResetRecipe.CODEC, LabelGunResetRecipe.STREAM_CODEC)
{% endcase %}
    );

    public static void register(IEventBus bus) {

        RECIPE_SERIALIZERS.register(bus);
    }

}
