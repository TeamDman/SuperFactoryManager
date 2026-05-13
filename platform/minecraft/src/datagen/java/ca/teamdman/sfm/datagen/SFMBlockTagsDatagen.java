package ca.teamdman.sfm.datagen;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.registry.registration.SFMBlockTags;
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import ca.teamdman.sfm.datagen.version_plumbing.MCVersionAgnosticBlockTagsDataGen;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.data.event.GatherDataEvent;

import java.util.concurrent.CompletableFuture;

public class SFMBlockTagsDatagen extends MCVersionAgnosticBlockTagsDataGen {
    public SFMBlockTagsDatagen(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider) {
        super(output, lookupProvider, SFM.MOD_ID);
    }

    @Override
    protected void addBlockTags() {
        // TODO: add assertion requiring all blocks to have at least one preferred tool tag
        this.tag(BlockTags.MINEABLE_WITH_PICKAXE)
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
                .add(SFMBlocks.TUNNELLED_MANAGER.get())
                .add(SFMBlocks.PRINTING_PRESS.get());

        this.tag(BlockTags.MINEABLE_WITH_AXE)
                .add(SFMBlocks.PRINTING_PRESS.get());

        this.tag(SFMBlockTags.ANVIL_DISENCHANTING)
                .add(Blocks.OBSIDIAN)
                .add(Blocks.CRYING_OBSIDIAN);

        this.tag(SFMBlockTags.ANVIL_PRINTING_PRESS_FORMING)
                .add(Blocks.IRON_BLOCK)
                .add(Blocks.COPPER_BLOCK);
    }
}
