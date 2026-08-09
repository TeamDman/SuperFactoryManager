package ca.teamdman.sfm.datagen.version_plumbing;

import ca.teamdman.sfm.common.registry.SFMRegistryObject;
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import com.google.common.collect.Lists;
import com.mojang.datafixers.util.Pair;
import net.minecraft.data.loot.BlockLoot;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.LootTables;
import net.minecraft.world.level.storage.loot.ValidationContext;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSet;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.predicates.ExplosionCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraftforge.data.event.GatherDataEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public abstract class MCVersionAgnosticLootTablesDataGen extends LootTableProvider {
    @MCVersionDependentBehaviour
    public MCVersionAgnosticLootTablesDataGen(
            GatherDataEvent event,
            String modId
    ) {
        super(event.getGenerator());
    }


    protected abstract void populate(BlockLootWriter writer);

    @MCVersionDependentBehaviour
    @Override
    protected List<Pair<Supplier<Consumer<BiConsumer<ResourceLocation, LootTable.Builder>>>, LootContextParamSet>> getTables() {
        return Lists.newArrayList(Pair.of(this::createBlockLoot, LootContextParamSets.BLOCK));
    }

    /// Blocks present here but not involved when populating the writer will cause a validation error
    protected abstract Set<? extends SFMRegistryObject<Block, ? extends Block>> getExpectedBlocks();

    private MyBlockLoot createBlockLoot() {
        BlockLootWriter writer = new BlockLootWriter();
        populate(writer);
        ArrayList<BlockLootBehaviour> behaviours = writer.finish();
        Set<? extends SFMRegistryObject<Block, ? extends Block>> expectedBlocks = getExpectedBlocks();
        Set<SFMRegistryObject<Block, ? extends Block>> seenBlocks = behaviours.stream()
                .map(BlockLootBehaviour::getInvolvedBlocks)
                .flatMap(List::stream)
                .collect(Collectors.toSet());
        Set<SFMRegistryObject<Block, ? extends Block>> notSeen = expectedBlocks.stream()
                .filter(block -> !seenBlocks.contains(block))
                .collect(Collectors.toSet());
        Set<SFMRegistryObject<Block, ? extends Block>> notExpected = seenBlocks.stream()
                .filter(block -> !expectedBlocks.contains(block))
                .collect(Collectors.toSet());
        List<String> problems = new ArrayList<>();
        for (SFMRegistryObject<Block, ? extends Block> expected : notSeen) {
            problems.add("Expected block " + expected.getId() + " not seen");
        }
        for (SFMRegistryObject<Block, ? extends Block> unexpected : notExpected) {
            problems.add("Unexpected block " + unexpected.getId() + " seen");
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Loot table problems:\n" + String.join("\n", problems));
        }
        return new MyBlockLoot(behaviours);
    }

    @MCVersionDependentBehaviour
    @Override
    protected void validate(
            Map<ResourceLocation, LootTable> map,
            ValidationContext tracker
    ) {
        map.forEach((k, v) -> LootTables.validate(tracker, k, v));
    }

    private interface BlockLootBehaviour {
        List<SFMRegistryObject<Block, ? extends Block>> getInvolvedBlocks();

        void apply(BiConsumer<ResourceLocation, LootTable.Builder> writer);
    }

    private record DropOtherBlockLootBehaviour(
            SFMRegistryObject<Block, ? extends Block> block,
            SFMRegistryObject<Block, ? extends Block> other
    ) implements BlockLootBehaviour {
        @Override
        public List<SFMRegistryObject<Block, ? extends Block>> getInvolvedBlocks() {
            return List.of(block, other);
        }

        @Override
        public void apply(BiConsumer<ResourceLocation, LootTable.Builder> writer) {
            var pool = LootPool.lootPool()
                    .setRolls(ConstantValue.exactly(1))
                    .add(LootItem.lootTableItem(other.get()))
                    .when(ExplosionCondition.survivesExplosion());
            writer.accept(block.get().getLootTable(), LootTable.lootTable().withPool(pool));
        }
    }

    protected static class BlockLootWriter {
        private final ArrayList<BlockLootBehaviour> behaviours = new ArrayList<>();

        public void dropSelf(
                SFMRegistryObject<Block, ? extends Block> block
        ) {
            behaviours.add(new DropOtherBlockLootBehaviour(block, block));
        }

        public void dropOther(
                SFMRegistryObject<Block, ? extends Block> block,
                SFMRegistryObject<Block, ? extends Block> other
        ) {
            behaviours.add(new DropOtherBlockLootBehaviour(block, other));
        }

        private ArrayList<BlockLootBehaviour> finish() {
            return behaviours;
        }
    }

    @MCVersionDependentBehaviour
    private static class MyBlockLoot extends BlockLoot {
        private final List<BlockLootBehaviour> behaviours;

        public MyBlockLoot(List<BlockLootBehaviour> behaviours) {
            this.behaviours = behaviours;
        }

        @Override
        public void accept(BiConsumer<ResourceLocation, LootTable.Builder> writer) {
            behaviours.forEach(behaviour -> behaviour.apply(writer));
        }
    }

}
