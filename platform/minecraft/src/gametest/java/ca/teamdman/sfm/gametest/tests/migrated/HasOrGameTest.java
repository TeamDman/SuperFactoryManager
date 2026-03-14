package ca.teamdman.sfm.gametest.tests.migrated;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.registry.SFMBlocks;
import ca.teamdman.sfm.common.registry.SFMItems;
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import static ca.teamdman.sfm.gametest.SFMGameTestCountHelpers.assertCount;

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
        var leftPos = new BlockPos(2, 2, 0);
        var rightPos = new BlockPos(0, 2, 0);
        var managerPos = new BlockPos(1, 2, 0);
        helper.setBlock(leftPos, SFMBlocks.TEST_BARREL_BLOCK.get());
        helper.setBlock(rightPos, SFMBlocks.TEST_BARREL_BLOCK.get());
        helper.setBlock(managerPos, SFMBlocks.MANAGER_BLOCK.get());
        var left = (Container) helper.getBlockEntity(leftPos);
        var right = (Container) helper.getBlockEntity(rightPos);
        var manager = (ManagerBlockEntity) helper.getBlockEntity(managerPos);
        left.setItem(0, new ItemStack(Items.DIAMOND, 64));
        left.setItem(1, new ItemStack(Items.DIAMOND, 64));
        left.setItem(2, new ItemStack(Items.IRON_INGOT, 12));
        manager.setItem(0, new ItemStack(SFMItems.DISK_ITEM.get()));
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
            assertCount(left, Items.DIAMOND, 0, "left should have no diamonds");
            assertCount(left, Items.IRON_INGOT, 0, "left should have no iron ingots");
            // right should have all the items
            assertCount(right, Items.DIAMOND, 64 * 2, "right should have 128 diamonds");
            assertCount(right, Items.IRON_INGOT, 12, "right should have 12 iron ingots");
        });
    }
}
