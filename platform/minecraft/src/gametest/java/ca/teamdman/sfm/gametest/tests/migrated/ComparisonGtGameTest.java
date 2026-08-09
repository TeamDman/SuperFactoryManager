package ca.teamdman.sfm.gametest.tests.migrated;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import ca.teamdman.sfm.common.registry.registration.SFMItems;
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;


/**
 * Migrated from SFMIfStatementGameTests.comparison_gt
 */
@SuppressWarnings({
        "RedundantSuppression",
        "DataFlowIssue",
        "OptionalGetWithoutIsPresent",
        "DuplicatedCode",
        "ArraysAsListWithZeroOrOneArgument"
})
@SFMGameTest
public class ComparisonGtGameTest extends SFMGameTestDefinition {

    @Override
    public String template() {
        return "3x2x1";
    }

    @Override
    public void run(SFMGameTestHelper helper) {
        var leftPos = new BlockPos(2, 2, 0);
        var rightPos = new BlockPos(0, 2, 0);
        var managerPos = new BlockPos(1, 2, 0);
        helper.setBlock(leftPos, SFMBlocks.TEST_BARREL.get());
        helper.setBlock(rightPos, SFMBlocks.TEST_BARREL.get());
        helper.setBlock(managerPos, SFMBlocks.MANAGER.get());
        var left = (Container) helper.getBlockEntity(leftPos);
        var right = (Container) helper.getBlockEntity(rightPos);
        var manager = helper.getBlockEntity(managerPos, ManagerBlockEntity.class);
        left.setItem(0, new ItemStack(Items.DIAMOND, 64));
        left.setItem(1, new ItemStack(Items.DIAMOND, 64));
        left.setItem(2, new ItemStack(Items.IRON_INGOT, 12));
        right.setItem(0, new ItemStack(Items.STICK, 13));
        right.setItem(1, new ItemStack(Items.STICK, 64));
        right.setItem(2, new ItemStack(Items.DIRT, 1));
        manager.setItem(0, new ItemStack(SFMItems.DISK.get()));
        manager.setProgram("""
                                   NAME "comparison_gt test"
                                   EVERY 20 TICKS DO
                                       IF left HAS GT 100 diamond THEN
                                           -- should happen
                                           INPUT diamond FROM left
                                           OUTPUT diamond TO right
                                       END
                                       IF left HAS GT 300 iron_ingot THEN
                                           -- should not happen
                                           INPUT iron_ingot FROM left
                                           OUTPUT iron_ingot TO right
                                       END
                                       IF right HAS > 10 stick THEN
                                           -- should happen
                                           INPUT stick FROM right
                                           OUTPUT stick TO left
                                       END
                                       if right has > 0 dirt then
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
            int leftDiamondCount = helper.count(left, Items.DIAMOND);
            int leftIronCount = helper.count(left, Items.IRON_INGOT);
            int leftStickCount = helper.count(left, Items.STICK);
            int leftDirtCount = helper.count(left, Items.DIRT);
            int rightDiamondCount = helper.count(right, Items.DIAMOND);
            int rightIronCount = helper.count(right, Items.IRON_INGOT);
            int rightStickCount = helper.count(right, Items.STICK);
            int rightDirtCount = helper.count(right, Items.DIRT);
            // the diamonds should have moved from left to right
            helper.assertTrue(leftDiamondCount == 0, "left should have no diamonds");
            helper.assertTrue(rightDiamondCount == 64 * 2, "right should have 100 diamonds");
            // the iron should have stayed in left
            helper.assertTrue(leftIronCount == 12, "left should have 12 iron ingots");
            helper.assertTrue(rightIronCount == 0, "right should have no iron ingots");
            // the sticks should have moved from right to left
            helper.assertTrue(rightStickCount == 0, "right should have no sticks");
            helper.assertTrue(leftStickCount == 77, "left should have 77 sticks");
            // the dirt should have moved from right to left
            helper.assertTrue(rightDirtCount == 0, "right should have no dirt");
            helper.assertTrue(leftDirtCount == 1, "left should have 1 dirt");
        });
    }
}
