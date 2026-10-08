package ca.teamdman.sfm.datagen;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.registry.registration.SFMBlockTags;
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import ca.teamdman.sfm.datagen.version_plumbing.MCVersionAgnosticBlockTagsDataGen;
{% case minecraft_version %}
{% when "26.1.2" %}
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
{% else %}
{% endcase %}
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
{% case minecraft_version %}
{% when "26.1.2" %}
{% else %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1" %}
import net.minecraftforge.data.event.GatherDataEvent;
{% else %}
import net.neoforged.neoforge.data.event.GatherDataEvent;
{% endcase %}
{% endcase %}
{% case minecraft_version %}
{% when "26.1.2" %}
import java.util.concurrent.CompletableFuture;
{% else %}
{% endcase %}

public class SFMBlockTagsDatagen extends MCVersionAgnosticBlockTagsDataGen {
{% case minecraft_version %}
{% when "26.1.2" %}
    public SFMBlockTagsDatagen(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider) {
        super(output, lookupProvider, SFM.MOD_ID);
{% else %}
    public SFMBlockTagsDatagen(GatherDataEvent event) {
        super(event, SFM.MOD_ID);
{% endcase %}
    }

    @Override
    protected void addBlockTags() {
        // TODO: add assertion requiring all blocks to have at least one preferred tool tag
{% case minecraft_version %}
{% when "26.1.2" %}
        this.tag(BlockTags.MINEABLE_WITH_PICKAXE)
{% else %}
        tag(BlockTags.MINEABLE_WITH_PICKAXE)
{% endcase %}
                .add(SFMBlocks.CABLE.get())
                .add(SFMBlocks.CABLE_FACADE.get())
                .add(SFMBlocks.FANCY_CABLE.get())
                .add(SFMBlocks.FANCY_CABLE_FACADE.get())
                .add(SFMBlocks.TOUGH_CABLE.get())
                .add(SFMBlocks.TOUGH_CABLE_FACADE.get())
                .add(SFMBlocks.TOUGH_FANCY_CABLE.get())
                .add(SFMBlocks.TOUGH_FANCY_CABLE_FACADE.get())
                .add(SFMBlocks.TUNNELLED_CABLE.get())
                .add(SFMBlocks.TUNNELLED_CABLE_FACADE.get())
                .add(SFMBlocks.TUNNELLED_FANCY_CABLE.get())
                .add(SFMBlocks.TUNNELLED_FANCY_CABLE_FACADE.get())
                .add(SFMBlocks.MANAGER.get())
{% case minecraft_version %}
{% when "26.1.2" %}
{% else %}
{% if features.client_manager %}
                .add(SFMBlocks.CLIENT_MANAGER.get())
{% endif %}
{% endcase %}
                .add(SFMBlocks.TUNNELLED_MANAGER.get())
{% case minecraft_version %}
{% when "26.1.2" %}
{% else %}
{% if features.touch_display %}
                .add(SFMBlocks.TOUCH_DISPLAY.get())
{% endif %}
{% endcase %}
                .add(SFMBlocks.PRINTING_PRESS.get());
{% case minecraft_version %}
{% when "26.1.2" %}

        this.tag(BlockTags.MINEABLE_WITH_AXE)
{% else %}
        tag(BlockTags.MINEABLE_WITH_AXE)
{% endcase %}
                .add(SFMBlocks.PRINTING_PRESS.get());
{% case minecraft_version %}
{% when "26.1.2" %}

        this.tag(SFMBlockTags.ANVIL_DISENCHANTING)
{% else %}
        tag(SFMBlockTags.ANVIL_DISENCHANTING)
{% endcase %}
                .add(Blocks.OBSIDIAN)
                .add(Blocks.CRYING_OBSIDIAN);
{% case minecraft_version %}
{% when "26.1.2" %}

        this.tag(SFMBlockTags.ANVIL_PRINTING_PRESS_FORMING)
{% else %}
        tag(SFMBlockTags.ANVIL_PRINTING_PRESS_FORMING)
{% endcase %}
                .add(Blocks.IRON_BLOCK)
                .add(Blocks.COPPER_BLOCK);
    }
}
