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
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.registries.ForgeRegistries;

/// Helps reduce {@link MCVersionDependentBehaviour}
@SuppressWarnings("unused")
@MCVersionDependentBehaviour
public class SFMWellKnownRegistries {
    public static final SFMRegistryWrapper<Block> BLOCKS
            = new SFMRegistryWrapper<>(ForgeRegistries.BLOCKS);

    public static final SFMRegistryWrapper<Fluid> FLUIDS
            = new SFMRegistryWrapper<>(ForgeRegistries.FLUIDS);

    public static final SFMRegistryWrapper<BlockEntityType<?>> BLOCK_ENTITY_TYPES
            = new SFMRegistryWrapper<>(ForgeRegistries.BLOCK_ENTITY_TYPES);

    public static final SFMRegistryWrapper<Item> ITEMS
            = new SFMRegistryWrapper<>(ForgeRegistries.ITEMS);

    public static final SFMRegistryWrapper<Enchantment> ENCHANTMENTS
            = new SFMRegistryWrapper<>(ForgeRegistries.ENCHANTMENTS);

    public static final SFMRegistryWrapper<MenuType<?>> MENU_TYPES
            = new SFMRegistryWrapper<>(ForgeRegistries.MENU_TYPES);

    public static final SFMRegistryWrapper<RecipeSerializer<?>> RECIPE_SERIALIZERS
            = new SFMRegistryWrapper<>(ForgeRegistries.RECIPE_SERIALIZERS);

    public static final SFMRegistryWrapper<RecipeType<?>> RECIPE_TYPES
            = new SFMRegistryWrapper<>(ForgeRegistries.RECIPE_TYPES);

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
