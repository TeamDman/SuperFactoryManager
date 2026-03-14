package ca.teamdman.sfm.gametest.tests.migrated;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.registry.SFMBlocks;
import ca.teamdman.sfm.common.registry.SFMItems;
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.util.Objects;

import static ca.teamdman.sfm.gametest.SFMGameTestCountHelpers.assertCount;
import static ca.teamdman.sfm.gametest.SFMGameTestCountHelpers.count;
import static ca.teamdman.sfm.gametest.SFMGameTestMethodHelpers.assertTrue;

/**
 * Migrated from SFMCorrectnessGameTests.round_robin_by_block_1
 */
@SuppressWarnings({
        "RedundantSuppression",
        "DataFlowIssue",
        "OptionalGetWithoutIsPresent",
        "DuplicatedCode",
        "ArraysAsListWithZeroOrOneArgument"
})
@SFMGameTest
public class RoundRobinByBlock1GameTest extends SFMGameTestDefinition {

    @Override
    public String template() {
        return "3x4x3";
    }

    @Override
    public void run(SFMGameTestHelper helper) {
        BlockPos managerPos = new BlockPos(1, 2, 1);
        BlockPos sourcePos = new BlockPos(1, 3, 1);
        BlockPos dest1Pos = new BlockPos(2, 2, 1);
        BlockPos dest2Pos = new BlockPos(0, 2, 1);

        // set up inventories
        helper.setBlock(sourcePos, SFMBlocks.TEST_BARREL_BLOCK.get());
        helper.setBlock(dest1Pos, SFMBlocks.TEST_BARREL_BLOCK.get());
        helper.setBlock(dest2Pos, SFMBlocks.TEST_BARREL_BLOCK.get());


        var sourceInv = helper.getItemHandler(sourcePos);

        var dest1Inv = helper.getItemHandler(dest1Pos);

        var dest2Inv = helper.getItemHandler(dest2Pos);

        for (int i = 0; i < sourceInv.getSlots(); i++) {
            sourceInv.insertItem(i, new ItemStack(Blocks.DIRT, 64), false);
        }

        // set up manager
        helper.setBlock(managerPos, SFMBlocks.MANAGER_BLOCK.get());
        ManagerBlockEntity manager = (ManagerBlockEntity) helper.getBlockEntity(managerPos);
        manager.setItem(0, new ItemStack(SFMItems.DISK_ITEM.get()));
        manager.setProgram("""
                                       EVERY 20 TICKS DO
                                           INPUT FROM source
                                           OUTPUT 128 dirt TO dest ROUND ROBIN BY BLOCK
                                       END
                                   """.stripTrailing().stripIndent());
        // set the labels
        LabelPositionHolder.empty()
                .add("source", helper.absolutePos(sourcePos))
                .add("dest", helper.absolutePos(dest1Pos))
                .add("dest", helper.absolutePos(dest2Pos))
                .save(Objects.requireNonNull(manager.getDisk()));

        helper.succeedIfManagerDidThingWithoutLagging(manager, () -> {
            assertCount(sourceInv, Items.DIRT, 64 * (27 - 2), "source count bad");
            int count1 = count(dest1Inv, Items.DIRT);
            int count2 = count(dest2Inv, Items.DIRT);
            assertTrue(count1 == 128 && count2 == 0 || count1 == 0 && count2 == 128, "first tick arrival count bad");


        });
    }
}
