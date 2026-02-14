package ca.teamdman.sfm.common.registry;

import ca.teamdman.sfm.common.program.linting.IProgramLinter;
import ca.teamdman.sfm.common.resourcetype.ResourceTypeContainer;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

public class SFMWellKnownRegistries {
    public static final SFMRegistryWrapper<Block> BLOCKS
            = new SFMRegistryWrapper<>(ForgeRegistries.BLOCKS);

//    public static final SFMRegistryWrapper<Fluid> FLUIDS
//            = new SFMRegistryWrapper<>(ForgeRegistries.FLUIDS);


    public static final SFMRegistryWrapper<Item> ITEMS
            = new SFMRegistryWrapper<>(ForgeRegistries.ITEMS);

    public static SFMRegistryWrapper<IProgramLinter> PROGRAM_LINTERS;

    public static SFMRegistryWrapper<ResourceTypeContainer> RESOURCE_TYPES;
}