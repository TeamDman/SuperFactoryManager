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


/**
 * Migrated from SFMIfStatementGameTests.move_if_powered
 */
@SuppressWarnings({
        "RedundantSuppression",
        "DataFlowIssue",
        "OptionalGetWithoutIsPresent",
        "DuplicatedCode",
        "ArraysAsListWithZeroOrOneArgument"
})
@SFMGameTest
public class MoveIfPoweredGameTest extends SFMGameTestDefinition {

    @Override
    public String template() {
        return "3x4x3";
    }

    @Override
    public void run(SFMGameTestHelper helper) {
        BlockPos managerPos = new BlockPos(1, 1, 1);
        helper.setBlock(managerPos, SFMBlocks.MANAGER.get());
        BlockPos leftPos = managerPos.east();
        helper.setBlock(leftPos, SFMBlocks.TEST_BARREL.get());
        BlockPos rightPos = managerPos.west();
        helper.setBlock(rightPos, SFMBlocks.TEST_BARREL.get());
        BlockPos topPos = managerPos.above();
        helper.setBlock(topPos, SFMBlocks.TEST_BARREL.get());

        BlockPos leverPos = managerPos.north();
        helper.setBlock(leverPos, Blocks.LEVER);
        helper.pullLever(leverPos);

        var rightChest = helper.getItemHandler(rightPos);
        var leftChest = helper.getItemHandler(leftPos);

        leftChest.insertItem(0, new ItemStack(Items.DIRT, 64), false);
        leftChest.insertItem(1, new ItemStack(Items.DIRT, 64), false);
        leftChest.insertItem(2, new ItemStack(Items.STONE, 64), false);
        leftChest.insertItem(3, new ItemStack(Items.IRON_INGOT, 64), false);
        leftChest.insertItem(4, new ItemStack(Items.GOLD_INGOT, 64), false);
        leftChest.insertItem(5, new ItemStack(Items.GOLD_NUGGET, 64), false);

        ManagerBlockEntity manager = helper.getBlockEntity(managerPos, ManagerBlockEntity.class);
        manager.setItem(0, new ItemStack(SFMItems.DISK.get()));
        manager.setProgram("""
                                       EVERY 20 TICKS DO
                                           IF redstone GT 0 THEN
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
            helper.getTick();

            helper.assertCount(leftChest, 0, "everything should depart");
            helper.getTick();

            helper.assertCount(rightChest, Items.GOLD_NUGGET, 64, "gold nuggets should arrive");
            helper.getTick();

            helper.assertCount(rightChest, Items.IRON_INGOT, 64, "iron ingots should arrive");
            helper.getTick();

            helper.assertCount(rightChest, Items.GOLD_INGOT, 64, "gold ingots should arrive");
            helper.getTick();

            helper.assertCount(rightChest, Items.DIRT, 64 * 2, "dirt should arrive");
            helper.getTick();

            helper.assertCount(rightChest, Items.STONE, 64, "stone should arrive");
        });
    }
}
