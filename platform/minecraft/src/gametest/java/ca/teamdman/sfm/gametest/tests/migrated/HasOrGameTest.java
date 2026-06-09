package ca.teamdman.sfm.gametest.tests.migrated;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.blockentity.TestBarrelBlockEntity;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import ca.teamdman.sfm.common.registry.registration.SFMItems;
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;



/**
 * Migrated from SFMIfStatementGameTests.has_or
 */
@SuppressWarnings({
        "RedundantSuppression",
        "DataFlowIssue",
        "OptionalGetWithoutIsPresent",
        "DuplicatedCode",
        "ArraysAsListWithZeroOrOneArgument"
})
@SFMGameTest
public class HasOrGameTest extends SFMGameTestDefinition {

    @Override
    public String template() {
        return "3x2x1";
    }

    @Override
    public void run(SFMGameTestHelper helper) {
        var leftPos = new BlockPos(2, 1, 0);
        var rightPos = new BlockPos(0, 1, 0);
        var managerPos = new BlockPos(1, 1, 0);
        helper.setBlock(leftPos, SFMBlocks.TEST_BARREL.get());
        helper.setBlock(rightPos, SFMBlocks.TEST_BARREL.get());
        helper.setBlock(managerPos, SFMBlocks.MANAGER.get());
        var left = helper.getBlockEntity(leftPos, TestBarrelBlockEntity.class);
        var right = helper.getBlockEntity(rightPos, TestBarrelBlockEntity.class);
        var manager = helper.getBlockEntity(managerPos, ManagerBlockEntity.class);
        left.setItem(0, new ItemStack(Items.DIAMOND, 64));
        left.setItem(1, new ItemStack(Items.DIAMOND, 64));
        left.setItem(2, new ItemStack(Items.IRON_INGOT, 12));
        manager.setItem(0, new ItemStack(SFMItems.DISK.get()));
        manager.setProgram("""
                                   NAME "has_or test"
                                   EVERY 20 TICKS DO
                                       IF left HAS LT 150 diamond OR iron_ingot THEN
                                            INPUT FROM left
                                            OUTPUT TO right
                                       END
                                   END
                                   """.stripTrailing().stripIndent());

        // set the labels
        LabelPositionHolder.empty()
                .add("left", helper.absolutePos(leftPos))
                .add("right", helper.absolutePos(rightPos))
                .save(manager.getDisk());

        helper.succeedIfManagerDidThingWithoutLagging(manager, () -> {
            // left should be empty
            helper.assertCount(left, Items.DIAMOND, 0, "left should have no diamonds", helper.getTick());
            helper.assertCount(left, Items.IRON_INGOT, 0, "left should have no iron ingots", helper.getTick());
            // right should have all the items
            helper.assertCount(right, Items.DIAMOND, 64 * 2, "right should have 128 diamonds", helper.getTick());
            helper.assertCount(right, Items.IRON_INGOT, 12, "right should have 12 iron ingots", helper.getTick());
        });
    }
}
