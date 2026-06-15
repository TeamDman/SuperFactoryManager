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
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Objects;


/**
 * Migrated from SFMCorrectnessGameTests.cable_spiral
 */
@SuppressWarnings({
        "RedundantSuppression",
        "DataFlowIssue",
        "OptionalGetWithoutIsPresent",
        "DuplicatedCode",
        "ArraysAsListWithZeroOrOneArgument"
})
@SFMGameTest
public class CableSpiralGameTest extends SFMGameTestDefinition {

    @Override
    public String template() {
        return "25x4x25";
    }

    @Override
    public void run(SFMGameTestHelper helper) {
        BlockPos start = new BlockPos(0, 1, 0);
        BlockPos end = new BlockPos(12, 1, 12);

        var len = 24;
        var dir = Direction.EAST;
        var current = start;
        while (len > 0) {
            // fill len blocks
            for (int i = 0; i < len; i++) {
                helper.setBlock(current, SFMBlocks.CABLE.get());
                current = current.relative(dir);
            }
            // turn right
            dir = dir.getClockWise();
            len -= 1;
        }

        // fill in the blocks needed for the test
        helper.setBlock(new BlockPos(1, 1, 0), SFMBlocks.MANAGER.get());
        helper.setBlock(start, SFMBlocks.TEST_BARREL.get());
        helper.setBlock(end, SFMBlocks.TEST_BARREL.get());

        // add some items
        Container startChest = helper.getBlockEntity(start, TestBarrelBlockEntity.class);
        startChest.setItem(0, new ItemStack(Items.IRON_INGOT, 64));
        Container endChest = helper.getBlockEntity(end, TestBarrelBlockEntity.class);


        ManagerBlockEntity manager = helper.getBlockEntity(new BlockPos(1, 1, 0), ManagerBlockEntity.class);
        manager.setItem(0, new ItemStack(SFMItems.DISK.get()));

        // set the labels
        LabelPositionHolder.empty()
                .add("a", helper.absolutePos(start))
                .add("b", helper.absolutePos(end))
                .save(Objects.requireNonNull(manager.getDisk()));

        // load the program
        manager.setProgram("""
                                       NAME "long cable test"
                                                                      
                                       EVERY 20 TICKS DO
                                           INPUT FROM a
                                           OUTPUT TO b
                                       END
                                   """.stripTrailing().stripIndent());

        helper.assertManagerRunning(manager);
        helper.succeedIfManagerDidThingWithoutLagging(manager, () -> {
            // ensure item arrived
            helper.assertTrue(endChest.getItem(0).getCount() == 64, "Items did not move");
            // ensure item left
            helper.assertTrue(startChest.getItem(0).isEmpty(), "Items did not leave");

        });
    }
}
