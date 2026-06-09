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

import java.util.Objects;



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
        helper.setBlock(new BlockPos(1, 1, 0), SFMBlocks.MANAGER.get());
        BlockPos rightPos = new BlockPos(0, 1, 0);
        helper.setBlock(rightPos, SFMBlocks.TEST_BARREL.get());
        BlockPos leftPos = new BlockPos(2, 1, 0);
        helper.setBlock(leftPos, SFMBlocks.TEST_BARREL.get());

        var rightChest = helper.getItemHandler(rightPos);
        var leftChest = helper.getItemHandler(leftPos);

        leftChest.insertItem(0, new ItemStack(Items.IRON_INGOT, 64), false);
        leftChest.insertItem(1, new ItemStack(Items.GOLD_INGOT, 64), false);
        leftChest.insertItem(2, new ItemStack(Items.NETHERITE_INGOT, 64), false);

        ManagerBlockEntity manager = helper.getBlockEntity(new BlockPos(1, 1, 0), ManagerBlockEntity.class);
        manager.setItem(0, new ItemStack(SFMItems.DISK.get()));
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
            helper.getTick();

            helper.assertCount(leftChest, Items.IRON_INGOT, 2, "Iron did not move");
            helper.getTick();

            helper.assertCount(leftChest, Items.GOLD_INGOT, 2, "Gold did not move");
            helper.getTick();

            helper.assertCount(leftChest, Items.NETHERITE_INGOT, 2, "Netherite did not move");
            // right should have 62 of each ingot
            helper.getTick();

            helper.assertCount(rightChest, Items.IRON_INGOT, 62, "Iron did not arrive");
            helper.getTick();

            helper.assertCount(rightChest, Items.GOLD_INGOT, 62, "Gold did not arrive");
            helper.getTick();

            helper.assertCount(rightChest, Items.NETHERITE_INGOT, 62, "Netherite did not arrive");

        });
    }
}
