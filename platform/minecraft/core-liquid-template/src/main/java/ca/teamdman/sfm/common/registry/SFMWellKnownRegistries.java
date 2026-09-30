package ca.teamdman.sfm.common.registry;

import ca.teamdman.sfm.client.registry.SFMTextEditorActions;
import ca.teamdman.sfm.client.registry.SFMTextEditors;
import ca.teamdman.sfm.client.text_editor.ISFMTextEditorRegistration;
import ca.teamdman.sfm.client.text_editor.action.ITextEditAction;
import ca.teamdman.sfm.common.capability.SFMBlockCapabilityProvider;
import ca.teamdman.sfm.common.program.linting.IProgramLinter;
import ca.teamdman.sfm.common.registry.registration.SFMGlobalBlockCapabilityProviders;
import ca.teamdman.sfm.common.registry.registration.SFMProgramLinters;
import ca.teamdman.sfm.common.registry.registration.SFMResourceTypes;
import ca.teamdman.sfm.common.resourcetype.ResourceType;
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1' %}
{% when '1.20.2', '1.20.3', '1.20.4' %}
import net.minecraft.core.registries.BuiltInRegistries;
{% when '1.21', '1.21.1', '26.1.2' %}
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
{% endcase %}
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.Fluid;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1' %}
import net.minecraftforge.registries.ForgeRegistries;
{% when '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
{% endcase %}

/// Helps reduce {@link MCVersionDependentBehaviour}
@SuppressWarnings("unused")
@MCVersionDependentBehaviour
public class SFMWellKnownRegistries {
    public static final SFMRegistryWrapper<Block> BLOCKS
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1' %}
            = new SFMRegistryWrapper<>(ForgeRegistries.BLOCKS);
{% when '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
            = new SFMRegistryWrapper<>(BuiltInRegistries.BLOCK);
{% endcase %}

    public static final SFMRegistryWrapper<Fluid> FLUIDS
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1' %}
            = new SFMRegistryWrapper<>(ForgeRegistries.FLUIDS);
{% when '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
            = new SFMRegistryWrapper<>(BuiltInRegistries.FLUID);
{% endcase %}

    public static final SFMRegistryWrapper<BlockEntityType<?>> BLOCK_ENTITY_TYPES
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1' %}
            = new SFMRegistryWrapper<>(ForgeRegistries.BLOCK_ENTITY_TYPES);
{% when '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
            = new SFMRegistryWrapper<>(BuiltInRegistries.BLOCK_ENTITY_TYPE);
{% endcase %}

    public static final SFMRegistryWrapper<Item> ITEMS
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1' %}
            = new SFMRegistryWrapper<>(ForgeRegistries.ITEMS);
{% when '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
            = new SFMRegistryWrapper<>(BuiltInRegistries.ITEM);
{% endcase %}

    public static final SFMRegistryWrapper<Enchantment> ENCHANTMENTS
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1' %}
            = new SFMRegistryWrapper<>(ForgeRegistries.ENCHANTMENTS);
{% when '1.20.2', '1.20.3', '1.20.4' %}
            = new SFMRegistryWrapper<>(BuiltInRegistries.ENCHANTMENT);
{% when '1.21', '1.21.1', '26.1.2' %}
            = new SFMRegistryWrapper<>(Registries.ENCHANTMENT);
{% endcase %}

    public static final SFMRegistryWrapper<MenuType<?>> MENU_TYPES
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1' %}
            = new SFMRegistryWrapper<>(ForgeRegistries.MENU_TYPES);
{% when '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
            = new SFMRegistryWrapper<>(BuiltInRegistries.MENU);
{% endcase %}

    public static final SFMRegistryWrapper<RecipeSerializer<?>> RECIPE_SERIALIZERS
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1' %}
            = new SFMRegistryWrapper<>(ForgeRegistries.RECIPE_SERIALIZERS);
{% when '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
            = new SFMRegistryWrapper<>(BuiltInRegistries.RECIPE_SERIALIZER);
{% endcase %}

    public static final SFMRegistryWrapper<RecipeType<?>> RECIPE_TYPES
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1' %}
            = new SFMRegistryWrapper<>(ForgeRegistries.RECIPE_TYPES);
{% when '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
            = new SFMRegistryWrapper<>(BuiltInRegistries.RECIPE_TYPE);
{% endcase %}

    public static final SFMRegistryWrapper<IProgramLinter> SFM_PROGRAM_LINTERS
            = new SFMRegistryWrapper<>(SFMProgramLinters.REGISTRY_ID);

    public static final SFMRegistryWrapper<ResourceType<?, ?, ?>> SFM_RESOURCE_TYPES
            = new SFMRegistryWrapper<>(SFMResourceTypes.REGISTRY_ID);

    public static final SFMRegistryWrapper<SFMBlockCapabilityProvider<?>> SFM_GLOBAL_BLOCK_CAPABILITY_PROVIDERS
            = new SFMRegistryWrapper<>(SFMGlobalBlockCapabilityProviders.REGISTRY_ID);

    public static final SFMRegistryWrapper<ITextEditAction> SFM_TEXT_EDITOR_ACTIONS
            = new SFMRegistryWrapper<>(SFMTextEditorActions.REGISTRY_ID);

    public static final SFMRegistryWrapper<ISFMTextEditorRegistration> SFM_TEXT_EDITORS
            = new SFMRegistryWrapper<>(SFMTextEditors.REGISTRY_ID);
}
