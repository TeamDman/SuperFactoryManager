package ca.teamdman.sfm.datagen;

{% case minecraft_version %}
{% when "26.1.2" %}
{% else %}
import ca.teamdman.sfm.SFM;
{% endcase %}
import ca.teamdman.sfm.common.registry.SFMRegistryObject;
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import ca.teamdman.sfm.datagen.version_plumbing.MCVersionAgnosticLootTablesDataGen;
{% case minecraft_version %}
{% when "26.1.2" %}
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.resources.ResourceKey;
{% else %}
{% endcase %}
import net.minecraft.world.level.block.Block;
{% case minecraft_version %}
{% when "26.1.2" %}
import net.minecraft.world.level.storage.loot.LootTable;
{% else %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1" %}
import net.minecraftforge.data.event.GatherDataEvent;
{% else %}
import net.neoforged.neoforge.data.event.GatherDataEvent;
{% endcase %}
{% endcase %}

import java.util.HashSet;
{% case minecraft_version %}
{% when "26.1.2" %}
import java.util.List;
{% else %}
{% endcase %}
import java.util.Set;
{% case minecraft_version %}
{% when "26.1.2" %}
import java.util.concurrent.CompletableFuture;
{% else %}
{% endcase %}

public class SFMLootTablesDatagen extends MCVersionAgnosticLootTablesDataGen {
{% case minecraft_version %}
{% when "26.1.2" %}
    public SFMLootTablesDatagen(
            PackOutput output,
            Set<ResourceKey<LootTable>> requiredTables,
            List<LootTableProvider.SubProviderEntry> subProviders,
            CompletableFuture<HolderLookup.Provider> registries
    ) {
        super(output, requiredTables, subProviders, registries);
{% else %}
    public SFMLootTablesDatagen(GatherDataEvent event) {
        super(event, SFM.MOD_ID);
{% endcase %}
    }

    @Override
    protected void populate(BlockLootWriter writer) {
        writer.dropSelf(SFMBlocks.MANAGER);
{% case minecraft_version %}
{% when "26.1.2" %}
{% else %}
{% if features.client_manager %}
        writer.dropSelf(SFMBlocks.CLIENT_MANAGER);
{% endif %}
{% endcase %}
        writer.dropSelf(SFMBlocks.TUNNELLED_MANAGER);
        writer.dropSelf(SFMBlocks.CABLE);
        writer.dropSelf(SFMBlocks.BUFFER_BLOCK);
        writer.dropOther(SFMBlocks.CABLE_FACADE, SFMBlocks.CABLE);

        // Tough cables
        writer.dropSelf(SFMBlocks.TOUGH_CABLE);
        writer.dropOther(SFMBlocks.TOUGH_CABLE_FACADE, SFMBlocks.TOUGH_CABLE);
        writer.dropSelf(SFMBlocks.TOUGH_FANCY_CABLE);
        writer.dropOther(SFMBlocks.TOUGH_FANCY_CABLE_FACADE, SFMBlocks.TOUGH_FANCY_CABLE);

        // Tunnelled cables
        writer.dropSelf(SFMBlocks.TUNNELLED_CABLE);
        writer.dropOther(SFMBlocks.TUNNELLED_CABLE_FACADE, SFMBlocks.TUNNELLED_CABLE);
        writer.dropSelf(SFMBlocks.TUNNELLED_FANCY_CABLE);
        writer.dropOther(SFMBlocks.TUNNELLED_FANCY_CABLE_FACADE, SFMBlocks.TUNNELLED_FANCY_CABLE);

        writer.dropSelf(SFMBlocks.FANCY_CABLE);
        writer.dropOther(SFMBlocks.FANCY_CABLE_FACADE, SFMBlocks.FANCY_CABLE);
        writer.dropSelf(SFMBlocks.PRINTING_PRESS);
{% case minecraft_version %}
{% when "26.1.2" %}
{% else %}
{% if features.touch_display %}
        writer.dropSelf(SFMBlocks.TOUCH_DISPLAY);
{% endif %}
{% endcase %}
        writer.dropSelf(SFMBlocks.WATER_TANK);
    }

    @Override
    protected Set<? extends SFMRegistryObject<Block, ? extends Block>> getExpectedBlocks() {
        Set<SFMRegistryObject<Block, ? extends Block>> exclude = Set.of(
                SFMBlocks.TEST_BARREL,
                SFMBlocks.TEST_BARREL_TANK
        );
        HashSet<SFMRegistryObject<Block, ? extends Block>> ourBlocks = new HashSet<>(SFMBlocks.REGISTERER.getOurEntries());
        ourBlocks.removeIf(exclude::contains);
        return ourBlocks;
    }
}
