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
 * Migrated from SFMCorrectnessGameTests.round_robin_by_label
 */
@SuppressWarnings({
        "RedundantSuppression",
        "DataFlowIssue",
        "OptionalGetWithoutIsPresent",
        "DuplicatedCode",
        "ArraysAsListWithZeroOrOneArgument"
})
@SFMGameTest
public class RoundRobinByLabelGameTest extends SFMGameTestDefinition {

    @Override
    public String template() {
        return "3x4x3";
    }

    @Override
    public void run(SFMGameTestHelper helper) {
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) {
                helper.setBlock(x, 1, z, SFMBlocks.CABLE_BLOCK.get());
            }
        }
        BlockPos managerPos = new BlockPos(0, 2, 2);
        BlockPos sourcePos = new BlockPos(2, 2, 0);
        BlockPos a1Pos = new BlockPos(0, 2, 0);
        BlockPos a2Pos = new BlockPos(0, 2, 1);
        BlockPos b1Pos = new BlockPos(1, 2, 2);
        BlockPos b2Pos = new BlockPos(2, 2, 2);

        // set up inventories
        helper.setBlock(sourcePos, SFMBlocks.TEST_BARREL_BLOCK.get());
        helper.setBlock(a1Pos, SFMBlocks.TEST_BARREL_BLOCK.get());
        helper.setBlock(a2Pos, SFMBlocks.TEST_BARREL_BLOCK.get());
        helper.setBlock(b1Pos, SFMBlocks.TEST_BARREL_BLOCK.get());
        helper.setBlock(b2Pos, SFMBlocks.TEST_BARREL_BLOCK.get());


        var sourceInv = helper.getItemHandler(sourcePos);

        var a1 = helper.getItemHandler(a1Pos);
        var a2 = helper.getItemHandler(a2Pos);
        var b1 = helper.getItemHandler(b1Pos);
        var b2 = helper.getItemHandler(b2Pos);

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
                                           OUTPUT 128 dirt TO EACH a,b ROUND ROBIN BY LABEL
                                       END
                                   """.stripTrailing().stripIndent());
        // set the labels
        LabelPositionHolder.empty()
                .add("source", helper.absolutePos(sourcePos))
                .add("a", helper.absolutePos(a1Pos))
                .add("a", helper.absolutePos(a2Pos))
                .add("b", helper.absolutePos(b1Pos))
                .add("b", helper.absolutePos(b2Pos))
                .save(Objects.requireNonNull(manager.getDisk()));

        helper.succeedIfManagerDidThingWithoutLagging(manager, () -> {
            assertCount(sourceInv, Items.DIRT, 64 * (27 - 4), "source count bad");
            // we make no guarantees about which one ticks first
            // we guarantee only one of a or b receives on the first tick
            boolean condition1 = count(a1, Items.DIRT) == 128 && count(a2, Items.DIRT) == 128
                                 && count(b1, Items.DIRT) == 0 && count(b2, Items.DIRT) == 0;
            boolean condition2 = count(b1, Items.DIRT) == 128 && count(b2, Items.DIRT) == 128
                                 && count(a1, Items.DIRT) == 0 && count(a2, Items.DIRT) == 0;
            assertTrue(condition1 || condition2, "Arrival counts bad");
        });
    }
}
