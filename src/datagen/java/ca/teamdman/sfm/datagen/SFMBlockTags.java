package ca.teamdman.sfm.datagen;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.registry.SFMBlocks;
import net.minecraft.core.HolderLookup;
import net.minecraft.tags.BlockTags;
import net.neoforged.neoforge.common.data.BlockTagsProvider;
import net.neoforged.neoforge.data.event.GatherDataEvent;

public class SFMBlockTags extends BlockTagsProvider {
    public SFMBlockTags(GatherDataEvent event) {
        super(
                event.getGenerator().getPackOutput(),
                event.getLookupProvider(),
                SFM.MOD_ID,
                event.getExistingFileHelper()
        );
    }

    @Override
    public String getName() {
        return "SuperFactoryManager Tags";
    }

    @Override
    protected void addTags(HolderLookup.Provider pProvider) {

        tag(BlockTags.MINEABLE_WITH_PICKAXE)
                .add(SFMBlocks.CABLE_BLOCK.get())
                .add(SFMBlocks.MANAGER_BLOCK.get())
                .add(SFMBlocks.PRINTING_PRESS_BLOCK.get())
                .add(SFMBlocks.CABLE_BLOCK.get());
        tag(BlockTags.MINEABLE_WITH_AXE)
                .add(SFMBlocks.PRINTING_PRESS_BLOCK.get());
    }
}
