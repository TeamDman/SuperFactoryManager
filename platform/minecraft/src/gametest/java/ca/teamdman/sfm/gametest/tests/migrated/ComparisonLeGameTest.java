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

import static ca.teamdman.sfm.gametest.SFMGameTestCountHelpers.count;
import static ca.teamdman.sfm.gametest.SFMGameTestMethodHelpers.assertTrue;

/**
 * Migrated from SFMIfStatementGameTests.comparison_le
 */
@SuppressWarnings({
        "RedundantSuppression",
        "DataFlowIssue",
        "OptionalGetWithoutIsPresent",
        "DuplicatedCode",
        "ArraysAsListWithZeroOrOneArgument"
})
@SFMGameTest
public class ComparisonLeGameTest extends SFMGameTestDefinition {

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
        right.setItem(0, new ItemStack(Items.STICK, 13));
        right.setItem(1, new ItemStack(Items.STICK, 64));
        right.setItem(2, new ItemStack(Items.DIRT, 1));
        manager.setItem(0, new ItemStack(SFMItems.DISK_ITEM.get()));
        manager.setProgram("""
                                   NAME "comparison_le test"
                                   EVERY 20 TICKS DO
                                       IF left HAS le 10 diamond THEN
                                           -- should not happen
                                           INPUT diamond FROM left
                                           OUTPUT diamond TO right
                                       END
                                       IF left HAS <= 12 iron_ingot THEN
                                           -- should happen
                                           INPUT iron_ingot FROM left
                                           OUTPUT iron_ingot TO right
                                       END
                                       IF right HAS le 77 stick THEN
                                           -- should happen
                                           INPUT stick FROM right
                                           OUTPUT stick TO left
                                       END
                                       if right has <= 1 dirt then
                                           -- should happen
                                           input dirt from right
                                           output dirt to left
                                       end
                                   END
                                   """.stripTrailing().stripIndent());

        // set the labels
        LabelPositionHolder.empty()
                .add("left", helper.absolutePos(leftPos))
                .add("right", helper.absolutePos(rightPos))
                .save(manager.getDisk());

        helper.succeedIfManagerDidThingWithoutLagging(manager, () -> {
            int leftDiamondCount = count(left, Items.DIAMOND);
            int leftIronCount = count(left, Items.IRON_INGOT);
            int leftStickCount = count(left, Items.STICK);
            int leftDirtCount = count(left, Items.DIRT);
            int rightDiamondCount = count(right, Items.DIAMOND);
            int rightIronCount = count(right, Items.IRON_INGOT);
            int rightStickCount = count(right, Items.STICK);
            int rightDirtCount = count(right, Items.DIRT);
            // the diamonds should have moved from left to right
            assertTrue(leftDiamondCount == 64 * 2, "left should have 128 diamonds");
            assertTrue(rightDiamondCount == 0, "right should have no diamonds");
            // the iron should have moved from left to right
            assertTrue(leftIronCount == 0, "left should have no iron ingots");
            assertTrue(rightIronCount == 12, "right should have 12 iron ingots");
            // the sticks should have moved from right to left
            assertTrue(rightStickCount == 0, "right should have no sticks");
            assertTrue(leftStickCount == 77, "left should have 77 sticks");
            // the dirt should have moved from right to left
            assertTrue(rightDirtCount == 0, "right should have no dirt");
            assertTrue(leftDirtCount == 1, "left should have 1 dirt");
        });
    }
}
