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

/**
 * Migrated from SFMCorrectnessGameTests.each_src_retain
 */
@SuppressWarnings({
        "RedundantSuppression",
        "DataFlowIssue",
        "OptionalGetWithoutIsPresent",
        "DuplicatedCode",
        "ArraysAsListWithZeroOrOneArgument"
})
@SFMGameTest
public class EachSrcRetainGameTest extends SFMGameTestDefinition {

    @Override
    public String template() {
        return "3x2x1";
    }

    @Override
    public void run(SFMGameTestHelper helper) {
        helper.setBlock(new BlockPos(1, 2, 0), SFMBlocks.MANAGER_BLOCK.get());
        BlockPos rightPos = new BlockPos(0, 2, 0);
        helper.setBlock(rightPos, SFMBlocks.TEST_BARREL_BLOCK.get());
        BlockPos leftPos = new BlockPos(2, 2, 0);
        helper.setBlock(leftPos, SFMBlocks.TEST_BARREL_BLOCK.get());

        var rightChest = helper.getItemHandler(rightPos);
        var leftChest = helper.getItemHandler(leftPos);

        leftChest.insertItem(0, new ItemStack(Items.IRON_INGOT, 64), false);
        leftChest.insertItem(1, new ItemStack(Items.GOLD_INGOT, 64), false);
        leftChest.insertItem(2, new ItemStack(Items.NETHERITE_INGOT, 64), false);

        ManagerBlockEntity manager = (ManagerBlockEntity) helper.getBlockEntity(new BlockPos(1, 2, 0));
        manager.setItem(0, new ItemStack(SFMItems.DISK_ITEM.get()));
        manager.setProgram("""
                                       EVERY 20 TICKS DO
                                           -- move all ingots
                                           -- keep 2 of each ingot type
                                           INPUT RETAIN 2 EACH *ingot* FROM a
                                           OUTPUT TO b
                                       END
                                   """.stripTrailing().stripIndent());

        // set the labels
        LabelPositionHolder.empty()
                .add("a", helper.absolutePos(leftPos))
                .add("b", helper.absolutePos(rightPos))
                .save(Objects.requireNonNull(manager.getDisk()));

        helper.succeedIfManagerDidThingWithoutLagging(manager, () -> {
            // left should have 2 of each ingot
            assertCount(leftChest, Items.IRON_INGOT, 2, "Iron did not move");
            assertCount(leftChest, Items.GOLD_INGOT, 2, "Gold did not move");
            assertCount(leftChest, Items.NETHERITE_INGOT, 2, "Netherite did not move");
            // right should have 62 of each ingot
            assertCount(rightChest, Items.IRON_INGOT, 62, "Iron did not arrive");
            assertCount(rightChest, Items.GOLD_INGOT, 62, "Gold did not arrive");
            assertCount(rightChest, Items.NETHERITE_INGOT, 62, "Netherite did not arrive");

        });
    }
}
