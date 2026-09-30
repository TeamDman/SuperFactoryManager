package ca.teamdman.sfm.datagen.version_plumbing;

import ca.teamdman.sfm.common.registry.SFMRegistryObject;
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
{% case minecraft_version %}
{% when '1.19.2' %}
import com.google.common.collect.Lists;
import com.mojang.datafixers.util.Pair;
import net.minecraft.data.loot.BlockLoot;
{% when '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
{% when '1.21', '1.21.1' %}
import net.minecraft.core.HolderLookup;
{% when '26.1.2' %}
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
{% endcase %}
import net.minecraft.data.loot.LootTableProvider;
{% case minecraft_version %}
{% when '1.19.2' %}
import net.minecraft.resources.ResourceLocation;
{% when '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
import net.minecraft.data.loot.LootTableSubProvider;
import net.minecraft.resources.ResourceLocation;
{% when '1.21', '1.21.1', '26.1.2' %}
import net.minecraft.data.loot.LootTableSubProvider;
import net.minecraft.resources.ResourceKey;
{% endcase %}
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4' %}
import net.minecraft.world.level.storage.loot.LootTables;
import net.minecraft.world.level.storage.loot.ValidationContext;
{% when '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
{% endcase %}
import net.minecraft.world.level.storage.loot.entries.LootItem;
{% case minecraft_version %}
{% when '1.19.2' %}
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSet;
{% when '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
{% endcase %}
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.predicates.ExplosionCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1' %}
import net.minecraftforge.data.event.GatherDataEvent;
{% when '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
import net.neoforged.neoforge.data.event.GatherDataEvent;
{% when '26.1.2' %}
{% endcase %}

{% case minecraft_version %}
{% when '1.19.2' %}
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
{% when '1.19.4' %}
import java.util.*;
{% when '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
{% when '26.1.2' %}
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
{% endcase %}
import java.util.function.BiConsumer;
{% case minecraft_version %}
{% when '1.19.2' %}
import java.util.function.Consumer;
import java.util.function.Supplier;
{% when '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
{% endcase %}
import java.util.stream.Collectors;

public abstract class MCVersionAgnosticLootTablesDataGen extends LootTableProvider {
    @MCVersionDependentBehaviour
    public MCVersionAgnosticLootTablesDataGen(
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
            GatherDataEvent event,
            String modId
{% when '26.1.2' %}
            PackOutput output,
            Set<ResourceKey<LootTable>> requiredTables,
            List<LootTableProvider.SubProviderEntry> subProviders,
            CompletableFuture<HolderLookup.Provider> registries
{% endcase %}
    ) {
{% case minecraft_version %}
{% when '1.19.2' %}
        super(event.getGenerator());
{% when '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
        super(event.getGenerator().getPackOutput(), Collections.emptySet(), Collections.emptyList());
{% when '1.21', '1.21.1' %}
        super(event.getGenerator().getPackOutput(), Collections.emptySet(), Collections.emptyList(), event.getLookupProvider());
{% when '26.1.2' %}
        super(output, requiredTables, subProviders, registries);
{% endcase %}
    }


    protected abstract void populate(BlockLootWriter writer);

{% case minecraft_version %}
{% when '1.19.2' %}
    @MCVersionDependentBehaviour
{% when '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
{% endcase %}
    @Override
{% case minecraft_version %}
{% when '1.19.2' %}
    protected List<Pair<Supplier<Consumer<BiConsumer<ResourceLocation, LootTable.Builder>>>, LootContextParamSet>> getTables() {
        return Lists.newArrayList(Pair.of(this::createBlockLoot, LootContextParamSets.BLOCK));
{% when '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
    public List<SubProviderEntry> getTables() {
        return List.of(new SubProviderEntry(this::createBlockLoot, LootContextParamSets.BLOCK));
{% endcase %}
    }

    /// Blocks present here but not involved when populating the writer will cause a validation error
    protected abstract Set<? extends SFMRegistryObject<Block, ? extends Block>> getExpectedBlocks();

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
    private MyBlockLoot createBlockLoot() {
{% when '1.21', '1.21.1', '26.1.2' %}
    private MyBlockLoot createBlockLoot(HolderLookup.Provider provider) {
{% endcase %}
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

{% case minecraft_version %}
{% when '1.19.2', '1.19.4' %}
    @MCVersionDependentBehaviour
    @Override
    protected void validate(
            Map<ResourceLocation, LootTable> map,
            ValidationContext tracker
    ) {
        map.forEach((k, v) -> LootTables.validate(tracker, k, v));
    }
{% when '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
//    @MCVersionDependentBehaviour
//    @Override
//    protected void validate(
//            Map<ResourceLocation, LootTable> map,
//            ValidationContext tracker
//    ) {
//        map.forEach((k, v) -> LootTables.validate(tracker, k, v));
//    }
{% when '26.1.2' %}
//    @MCVersionDependentBehaviour
//    @Override
//    protected void validate(
//            Map<Identifier, LootTable> map,
//            ValidationContext tracker
//    ) {
//        map.forEach((k, v) -> LootTables.validate(tracker, k, v));
//    }
{% endcase %}

    private interface BlockLootBehaviour {
        List<SFMRegistryObject<Block, ? extends Block>> getInvolvedBlocks();

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
        void apply(BiConsumer<ResourceLocation, LootTable.Builder> writer);
{% when '1.21', '1.21.1', '26.1.2' %}
        void apply(BiConsumer<ResourceKey<LootTable>, LootTable.Builder> writer);
{% endcase %}
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
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
        public void apply(BiConsumer<ResourceLocation, LootTable.Builder> writer) {
{% when '1.21', '1.21.1', '26.1.2' %}
        public void apply(BiConsumer<ResourceKey<LootTable>, LootTable.Builder> writer) {
{% endcase %}
            var pool = LootPool.lootPool()
                    .setRolls(ConstantValue.exactly(1))
                    .add(LootItem.lootTableItem(other.get()))
                    .when(ExplosionCondition.survivesExplosion());
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
            writer.accept(block.get().getLootTable(), LootTable.lootTable().withPool(pool));
{% when '26.1.2' %}
            if (block.get().getLootTable().isPresent()) {
                writer.accept(block.get().getLootTable().get(), LootTable.lootTable().withPool(pool));

            }
{% endcase %}
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

{% case minecraft_version %}
{% when '1.19.2' %}
    @MCVersionDependentBehaviour
    private static class MyBlockLoot extends BlockLoot {
{% when '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
    private static class MyBlockLoot implements @MCVersionDependentBehaviour LootTableSubProvider {
{% endcase %}
        private final List<BlockLootBehaviour> behaviours;

        public MyBlockLoot(List<BlockLootBehaviour> behaviours) {
            this.behaviours = behaviours;
        }

{% case minecraft_version %}
{% when '1.19.2' %}
{% when '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
        @MCVersionDependentBehaviour
{% endcase %}
        @Override
{% case minecraft_version %}
{% when '1.19.2' %}
        public void accept(BiConsumer<ResourceLocation, LootTable.Builder> writer) {
            behaviours.forEach(behaviour -> behaviour.apply(writer));
{% when '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
        public void generate(BiConsumer<ResourceLocation, LootTable.Builder> writer) {
            behaviours.forEach(behaviour -> behaviour.apply(writer));
{% when '1.21', '1.21.1', '26.1.2' %}
        public void generate(BiConsumer<ResourceKey<LootTable>, LootTable.Builder> biConsumer) {
            behaviours.forEach(behaviours -> behaviours.apply(biConsumer));
{% endcase %}
        }
    }

}
