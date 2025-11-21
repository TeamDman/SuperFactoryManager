package ca.teamdman.sfm.common.registry;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

public class SFMWellKnownRegistries {

    public static final SFMRegistryWrapper<Block> BLOCKS = new SFMRegistryWrapper<>(ForgeRegistries.BLOCKS);

    // public static final SFMRegistryWrapper<Fluid> FLUIDS
    // = new SFMRegistryWrapper<>(ForgeRegistries.FLUIDS);

    public static final SFMRegistryWrapper<Item> ITEMS = new SFMRegistryWrapper<>(ForgeRegistries.ITEMS);
}
