package ca.teamdman.sfm.gametest.tests.migrated;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import ca.teamdman.sfm.common.registry.registration.SFMItems;
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

import java.util.Objects;




/**
 * Migrated from SFMCorrectnessGameTests.round_robin_no_blocks_crash_regression
 */
@SuppressWarnings({
        "RedundantSuppression",
        "DataFlowIssue",
        "OptionalGetWithoutIsPresent",
        "DuplicatedCode",
        "ArraysAsListWithZeroOrOneArgument"
})
@SFMGameTest
public class RoundRobinNoBlocksCrashRegressionGameTest extends SFMGameTestDefinition {

    @Override
    public String template() {
        return "3x2x1";
    }

    @Override
    public void run(SFMGameTestHelper helper) {
        BlockPos leftPos = new BlockPos(2, 1, 0);
        BlockPos managerPos = new BlockPos(1, 1, 0);
        BlockPos rightPos = new BlockPos(0, 1, 0);

        helper.setBlock(rightPos, SFMBlocks.TEST_BARREL.get());
        helper.setBlock(managerPos, SFMBlocks.MANAGER.get());
        helper.setBlock(leftPos, SFMBlocks.TEST_BARREL.get());

        var leftChest = helper.getItemHandler(leftPos);
        leftChest.insertItem(0, new ItemStack(Blocks.DIRT, 64), false);

        ManagerBlockEntity manager = helper.getBlockEntity(managerPos, ManagerBlockEntity.class);
        manager.setItem(0, new ItemStack(SFMItems.DISK.get()));
        manager.setProgram("""
                                       EVERY 20 TICKS DO
                                           INPUT FROM d,e ROUND ROBIN BY BLOCK
                                           OUTPUT TO f,g,h ROUND ROBIN BY LABEL
                                       END
                                   """.stripTrailing().stripIndent());

        // set labels
        LabelPositionHolder.empty()
                .add("a", helper.absolutePos(leftPos))
                .save(Objects.requireNonNull(manager.getDisk()));

        // it should not crash
        helper.succeedIfManagerDidThingWithoutLagging(manager, helper::succeed);
    }
}
