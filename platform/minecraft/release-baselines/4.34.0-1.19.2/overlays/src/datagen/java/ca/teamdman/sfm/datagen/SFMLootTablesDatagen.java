package ca.teamdman.sfm.datagen;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.registry.SFMRegistryObject;
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
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
        writer.dropSelf(SFMBlocks.MANAGER);
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
