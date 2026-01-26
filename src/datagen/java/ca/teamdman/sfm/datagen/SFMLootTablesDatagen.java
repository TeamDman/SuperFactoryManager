package ca.teamdman.sfm.datagen;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.registry.SFMBlocks;
import ca.teamdman.sfm.common.registry.SFMRegistryObject;
import ca.teamdman.sfm.datagen.version_plumbing.MCVersionAgnosticLootTablesDataGen;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.data.event.GatherDataEvent;

import java.util.HashSet;
import java.util.Set;

public class SFMLootTablesDatagen extends MCVersionAgnosticLootTablesDataGen {

    public SFMLootTablesDatagen(GatherDataEvent event) {
        super(event, SFM.MOD_ID);
    }

    @Override
    protected void populate(BlockLootWriter writer) {
        writer.dropSelf(SFMBlocks.MANAGER_BLOCK);
        writer.dropSelf(SFMBlocks.TUNNELLED_MANAGER_BLOCK);
        writer.dropSelf(SFMBlocks.CABLE_BLOCK);
        writer.dropSelf(SFMBlocks.BUFFER_BLOCK);
        writer.dropOther(SFMBlocks.CABLE_FACADE_BLOCK, SFMBlocks.CABLE_BLOCK);

        // Tough cables
        writer.dropSelf(SFMBlocks.TOUGH_CABLE_BLOCK);
        writer.dropOther(SFMBlocks.TOUGH_CABLE_FACADE_BLOCK, SFMBlocks.TOUGH_CABLE_BLOCK);
        writer.dropSelf(SFMBlocks.TOUGH_FANCY_CABLE_BLOCK);
        writer.dropOther(SFMBlocks.TOUGH_FANCY_CABLE_FACADE_BLOCK, SFMBlocks.TOUGH_FANCY_CABLE_BLOCK);

        // Tunnelled cables
        writer.dropSelf(SFMBlocks.TUNNELLED_CABLE_BLOCK);
        writer.dropOther(SFMBlocks.TUNNELLED_CABLE_FACADE_BLOCK, SFMBlocks.TUNNELLED_CABLE_BLOCK);
        writer.dropSelf(SFMBlocks.TUNNELLED_FANCY_CABLE_BLOCK);
        writer.dropOther(SFMBlocks.TUNNELLED_FANCY_CABLE_FACADE_BLOCK, SFMBlocks.TUNNELLED_FANCY_CABLE_BLOCK);

        writer.dropSelf(SFMBlocks.FANCY_CABLE_BLOCK);
        writer.dropOther(SFMBlocks.FANCY_CABLE_FACADE_BLOCK, SFMBlocks.FANCY_CABLE_BLOCK);
        writer.dropSelf(SFMBlocks.PRINTING_PRESS_BLOCK);
        writer.dropSelf(SFMBlocks.WATER_TANK_BLOCK);
    }

    @Override
    protected Set<? extends SFMRegistryObject<Block, ? extends Block>> getExpectedBlocks() {
        Set<SFMRegistryObject<Block, ? extends Block>> exclude = Set.of(
                SFMBlocks.TEST_BARREL_BLOCK,
                SFMBlocks.TEST_BARREL_TANK_BLOCK
        );
        HashSet<SFMRegistryObject<Block, ? extends Block>> ourBlocks = new HashSet<>(SFMBlocks.REGISTERER.getOurEntries());
        ourBlocks.removeIf(exclude::contains);
        return ourBlocks;
    }
}
