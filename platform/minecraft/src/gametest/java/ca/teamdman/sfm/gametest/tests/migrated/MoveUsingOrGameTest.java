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
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.util.Objects;


/**
 * Migrated from SFMCorrectnessGameTests.move_using_or
 */
@SuppressWarnings({
        "RedundantSuppression",
        "DataFlowIssue",
        "OptionalGetWithoutIsPresent",
        "DuplicatedCode",
        "ArraysAsListWithZeroOrOneArgument"
})
@SFMGameTest
public class MoveUsingOrGameTest extends SFMGameTestDefinition {

    @Override
    public String template() {
        return "3x2x1";
    }

    @Override
    public void run(SFMGameTestHelper helper) {
        helper.setBlock(new BlockPos(1, 1, 0), SFMBlocks.MANAGER.get());
        BlockPos rightPos = new BlockPos(0, 1, 0);
        helper.setBlock(rightPos, SFMBlocks.TEST_BARREL.get());
        BlockPos leftPos = new BlockPos(2, 1, 0);
        helper.setBlock(leftPos, SFMBlocks.TEST_BARREL.get());

        var rightChest = helper.getItemHandler(rightPos);
        var leftChest = helper.getItemHandler(leftPos);

        leftChest.insertItem(0, new ItemStack(Blocks.DIRT, 64), false);
        leftChest.insertItem(1, new ItemStack(Blocks.STONE, 64), false);
        leftChest.insertItem(2, new ItemStack(Blocks.COBBLESTONE, 64), false);
        leftChest.insertItem(3, new ItemStack(Blocks.COBBLESTONE, 64), false);

        ManagerBlockEntity manager = helper.getBlockEntity(new BlockPos(1, 1, 0), ManagerBlockEntity.class);
        manager.setItem(0, new ItemStack(SFMItems.DISK.get()));
        manager.setProgram("""
                                       EVERY 20 TICKS DO
                                           INPUT
                                               5 stone or dirt,
                                               cobblestone FROM a
                                           OUTPUT TO b
                                       END
                                   """.stripTrailing().stripIndent());

        // set the labels
        LabelPositionHolder.empty()
                .add("a", helper.absolutePos(leftPos))
                .add("b", helper.absolutePos(rightPos))
                .save(Objects.requireNonNull(manager.getDisk()));

        helper.succeedIfManagerDidThingWithoutLagging(manager, () -> {
            // count of stone + dirt in left must be 64*2-5
            int leftStoneDirt = helper.count(leftChest, Items.STONE) + helper.count(leftChest, Items.DIRT);
            helper.getTick();
            helper.assertTrue(leftStoneDirt == 64 * 2 - 5, "stone and dirt should depart");
            // count of stone + dirt in right must be 5
            int rightStoneDirt = helper.count(rightChest, Items.STONE) + helper.count(rightChest, Items.DIRT);
            helper.getTick();
            helper.assertTrue(rightStoneDirt == 5, "stone and dirt should arrive");
            // left cobblestone count = 0
            helper.getTick();

            helper.assertCount(leftChest, Items.COBBLESTONE, 0, "no cobblestone should remain");
            // right cobblestone count = 64*2
            helper.getTick();

            helper.assertCount(rightChest, Items.COBBLESTONE, 64 * 2, "cobblestone should arrive");
        });
    }
}
