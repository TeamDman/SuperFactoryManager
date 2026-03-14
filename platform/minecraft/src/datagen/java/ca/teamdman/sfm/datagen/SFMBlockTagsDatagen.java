package ca.teamdman.sfm.datagen;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.registry.SFMBlockTags;
import ca.teamdman.sfm.common.registry.SFMBlocks;
import ca.teamdman.sfm.datagen.version_plumbing.MCVersionAgnosticBlockTagsDataGen;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.data.event.GatherDataEvent;

public class SFMBlockTagsDatagen extends MCVersionAgnosticBlockTagsDataGen {
    public SFMBlockTagsDatagen(GatherDataEvent event) {
        super(event, SFM.MOD_ID);
    }

    @Override
    protected void addBlockTags() {
        tag(BlockTags.MINEABLE_WITH_PICKAXE)
                .add(SFMBlocks.CABLE_BLOCK.get())
                .add(SFMBlocks.CABLE_FACADE_BLOCK.get())
                .add(SFMBlocks.FANCY_CABLE_BLOCK.get())
                .add(SFMBlocks.MANAGER_BLOCK.get())
//                .add(SFMBlocks.TUNNELLED_MANAGER_BLOCK.get())
                .add(SFMBlocks.PRINTING_PRESS_BLOCK.get());
        tag(BlockTags.MINEABLE_WITH_AXE)
                .add(SFMBlocks.PRINTING_PRESS_BLOCK.get());
        tag(SFMBlockTags.ANVIL_DISENCHANTING)
                .add(Blocks.OBSIDIAN)
                .add(Blocks.CRYING_OBSIDIAN);
        tag(SFMBlockTags.ANVIL_PRINTING_PRESS_FORMING)
                .add(Blocks.IRON_BLOCK)
                .add(Blocks.COPPER_BLOCK);
    }
}
