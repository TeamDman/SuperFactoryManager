package ca.teamdman.sfm.datagen;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.registry.SFMBlocks;
import net.minecraft.data.tags.BlockTagsProvider;
import net.minecraft.tags.BlockTags;
import net.minecraftforge.data.event.GatherDataEvent;

public class SFMBlockTags extends BlockTagsProvider {
    public SFMBlockTags(GatherDataEvent event) {
        super(event.getGenerator(), SFM.MOD_ID, event.getExistingFileHelper());
    }

    @Override
    protected void addTags() {
        tag(BlockTags.MINEABLE_WITH_PICKAXE)
                .add(SFMBlocks.CABLE_BLOCK.get())
                .add(SFMBlocks.CABLE_BLOCK_BLOCK.get())
                .add(SFMBlocks.MANAGER_BLOCK.get())
                .add(SFMBlocks.PRINTING_PRESS_BLOCK.get());
        tag(BlockTags.MINEABLE_WITH_AXE)
                .add(SFMBlocks.PRINTING_PRESS_BLOCK.get());
    }

    @Override
    public String getName() {
        return "SuperFactoryManager Tags";
    }
}
