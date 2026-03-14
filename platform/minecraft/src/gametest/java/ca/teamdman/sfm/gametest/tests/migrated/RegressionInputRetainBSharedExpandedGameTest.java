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

import java.util.Objects;

import static ca.teamdman.sfm.gametest.SFMGameTestCountHelpers.assertCount;
import static ca.teamdman.sfm.gametest.SFMGameTestCountHelpers.count;
import static ca.teamdman.sfm.gametest.SFMGameTestMethodHelpers.assertTrue;

/**
 * Migrated from SFMCorrectnessGameTests.regression_input_retain_b_shared_expanded
 */
@SuppressWarnings({
        "RedundantSuppression",
        "DataFlowIssue",
        "OptionalGetWithoutIsPresent",
        "DuplicatedCode",
        "ArraysAsListWithZeroOrOneArgument"
})
@SFMGameTest
public class RegressionInputRetainBSharedExpandedGameTest extends SFMGameTestDefinition {

    @Override
    public String template() {
        return "7x3x3";
    }

    @Override
    public void run(SFMGameTestHelper helper) {
        BlockPos managerPos = new BlockPos(1, 2, 1);
        BlockPos aPos = new BlockPos(2, 2, 1);
        BlockPos b1Pos = new BlockPos(4, 2, 1);
        BlockPos b2Pos = new BlockPos(5, 2, 1);
        BlockPos b3Pos = new BlockPos(6, 2, 1);

        helper.setBlock(managerPos, SFMBlocks.MANAGER_BLOCK.get());
        helper.setBlock(aPos, SFMBlocks.TEST_BARREL_BLOCK.get());
        helper.setBlock(b1Pos, SFMBlocks.TEST_BARREL_BLOCK.get());
        helper.setBlock(b2Pos, SFMBlocks.TEST_BARREL_BLOCK.get());
        helper.setBlock(b3Pos, SFMBlocks.TEST_BARREL_BLOCK.get());

        for (int i = 0; i < 6; i++) {
            helper.setBlock(new BlockPos(1 + i, 2, 2), SFMBlocks.CABLE_BLOCK.get());
        }

        var a = helper.getItemHandler(aPos);
        var b1 = helper.getItemHandler(b1Pos);
        var b2 = helper.getItemHandler(b2Pos);
        var b3 = helper.getItemHandler(b3Pos);

        for (int i = 0; i < 5; i++) {
            b1.insertItem(i, new ItemStack(Items.DIRT, 64), false);
            b2.insertItem(i, new ItemStack(Items.DIRT, 64), false);
            b3.insertItem(i, new ItemStack(Items.DIRT, 64), false);
        }

        ManagerBlockEntity manager = (ManagerBlockEntity) helper.getBlockEntity(managerPos);
        manager.setItem(0, new ItemStack(SFMItems.DISK_ITEM.get()));
        manager.setProgram("""
                                       EVERY 20 TICKS DO
                                           INPUT RETAIN 5 EACH FROM b
                                           OUTPUT TO a
                                       END
                                   """.stripTrailing().stripIndent());

        // set the labels
        LabelPositionHolder.empty()
                .add("a", helper.absolutePos(aPos))
                .add("b", helper.absolutePos(b1Pos))
                .add("b", helper.absolutePos(b2Pos))
                .add("b", helper.absolutePos(b3Pos))
                .save(Objects.requireNonNull(manager.getDisk()));

        helper.succeedIfManagerDidThingWithoutLagging(manager, () -> {
            // There should be exactly 5 dirt across all b
            // The rest should be in a
            assertCount(a, Items.DIRT, 64 * 3 * 5 - 5, "dirt should arrive in a");
            int bDirt = count(b1, Items.DIRT) + count(b2, Items.DIRT) + count(b3, Items.DIRT);
            assertTrue(bDirt == 5, "dirt should depart from b");
        });
    }
}
