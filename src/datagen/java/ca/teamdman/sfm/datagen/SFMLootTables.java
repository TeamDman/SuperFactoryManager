package ca.teamdman.sfm.datagen;

import ca.teamdman.sfm.common.registry.SFMBlocks;
import com.google.common.collect.Lists;
import com.mojang.datafixers.util.Pair;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.LootTables;
import net.minecraft.world.level.storage.loot.ValidationContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSet;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraftforge.data.event.GatherDataEvent;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class SFMLootTables extends LootTableProvider {

    public SFMLootTables(GatherDataEvent event) {
        super(event.getGenerator());
    }

    @Override
    protected List<Pair<Supplier<Consumer<BiConsumer<ResourceLocation, LootTable.Builder>>>, LootContextParamSet>> getTables() {
        return Lists.newArrayList(Pair.of(BlockLoot::new, LootContextParamSets.BLOCK));
    }

    @Override
    protected void validate(Map<ResourceLocation, LootTable> map, ValidationContext tracker) {
        map.forEach((k, v) -> LootTables.validate(tracker, k, v));
    }


    private static class BlockLoot extends net.minecraft.data.loot.BlockLoot {

        @Override
        protected void addTables() {
            dropSelf(SFMBlocks.MANAGER_BLOCK.get());
            dropSelf(SFMBlocks.CABLE_BLOCK.get());
            dropSelf(SFMBlocks.CABLE_BLOCK_BLOCK.get());
            dropSelf(SFMBlocks.PRINTING_PRESS_BLOCK.get());
            dropSelf(SFMBlocks.WATER_TANK_BLOCK.get());
        }

        @Override
        protected Iterable<Block> getKnownBlocks() {
            return Arrays.asList(
                    SFMBlocks.MANAGER_BLOCK.get(),
                    SFMBlocks.CABLE_BLOCK.get(),
                    SFMBlocks.CABLE_BLOCK_BLOCK.get(),
                    SFMBlocks.WATER_TANK_BLOCK.get(),
                    SFMBlocks.PRINTING_PRESS_BLOCK.get()
            );
        }
    }
}
