package ca.teamdman.sfm.common.registry;

import ca.teamdman.sfm.common.program.linting.IProgramLinter;
import ca.teamdman.sfm.common.resourcetype.ResourceType;
import net.minecraft.block.Block;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.Item;
import net.minecraft.tileentity.TileEntityType;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

public class SFMWellKnownRegistries {
    public static final SFMRegistryWrapper<Block> BLOCKS
            = new SFMRegistryWrapper<>(ForgeRegistries.BLOCKS);

    public static final SFMRegistryWrapper<Fluid> FLUIDS
            = new SFMRegistryWrapper<>(ForgeRegistries.FLUIDS);


    public static final SFMRegistryWrapper<Item> ITEMS
            = new SFMRegistryWrapper<>(ForgeRegistries.ITEMS);

    public static final SFMRegistryWrapper<IProgramLinter> SFM_PROGRAM_LINTERS
            = new SFMRegistryWrapper<>(SFMProgramLinters.REGISTRY_ID);

    public static SFMRegistryWrapper<ResourceType<?, ?, ?>> SFM_RESOURCE_TYPES;

    public static final SFMRegistryWrapper<SFMBlockCapabilityProvider<?>> SFM_GLOBAL_BLOCK_CAPABILITY_PROVIDERS
            = new SFMRegistryWrapper<>(SFMGlobalBlockCapabilityProviders.REGISTRY_ID);

    public static IForgeRegistry<ResourceType> SFM_RESOURCE_TYPES;
}