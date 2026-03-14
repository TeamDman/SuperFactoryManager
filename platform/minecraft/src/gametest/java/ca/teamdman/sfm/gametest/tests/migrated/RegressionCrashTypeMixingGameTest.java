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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;

import java.util.Objects;

import static ca.teamdman.sfm.gametest.SFMGameTestMethodHelpers.assertManagerRunning;
import static ca.teamdman.sfm.gametest.SFMGameTestMethodHelpers.assertTrue;

/**
 * Migrated from SFMCorrectnessGameTests.regression_crash_type_mixing
 */
@SuppressWarnings({
        "RedundantSuppression",
        "DataFlowIssue",
        "OptionalGetWithoutIsPresent",
        "DuplicatedCode",
        "ArraysAsListWithZeroOrOneArgument"
})
@SFMGameTest
public class RegressionCrashTypeMixingGameTest extends SFMGameTestDefinition {

    @Override
    public String template() {
        return "3x4x3";
    }

    @Override
    public void run(SFMGameTestHelper helper) {
        // fill in the blocks needed for the test
        BlockPos managerPos = new BlockPos(1, 2, 1);
        helper.setBlock(managerPos, SFMBlocks.MANAGER_BLOCK.get());

        BlockPos left = new BlockPos(2, 2, 1);
        helper.setBlock(left, SFMBlocks.TEST_BARREL_BLOCK.get());
        // add sticks to the chest
        Container chest = (Container) helper.getBlockEntity(left);
        chest.setItem(0, new ItemStack(Items.STICK, 64));

        BlockPos right = new BlockPos(0, 2, 1);
        helper.setBlock(right, SFMBlocks.TEST_BARREL_BLOCK.get());

        BlockPos front = new BlockPos(1, 2, 2);
        helper.setBlock(front, Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3));

        BlockPos back = new BlockPos(1, 2, 0);
        helper.setBlock(back, Blocks.CAULDRON);

        ManagerBlockEntity manager = (ManagerBlockEntity) helper.getBlockEntity(managerPos);
        manager.setItem(0, new ItemStack(SFMItems.DISK_ITEM.get()));

        // set the labels
        LabelPositionHolder.empty()
                .add("a", helper.absolutePos(left))
                .add("a", helper.absolutePos(front))
                .add("b", helper.absolutePos(right))
                .add("b", helper.absolutePos(back))
                .save(Objects.requireNonNull(manager.getDisk()));

        // load the program
        manager.setProgram("""
                                       NAME "water crash test"
                                                                      
                                       every 20 ticks do
                                           INPUT  item:minecraft:stick, fluid:minecraft:water FROM a
                                           OUTPUT stick, fluid:minecraft:water TO b
                                       end
                                   """.stripTrailing().stripIndent());

        assertManagerRunning(manager);
        helper.succeedIfManagerDidThingWithoutLagging(manager, () -> {
            helper.assertBlock(front, b -> b == Blocks.CAULDRON, "cauldron didn't empty");
            helper.assertBlockState(
                    back,
                    s -> s.getBlock() == Blocks.WATER_CAULDRON
                         && s.getValue(LayeredCauldronBlock.LEVEL) == 3,
                    () -> "cauldron didn't fill"
            );
            // ensure sticks departed
            assertTrue(chest.getItem(0).getCount() == 0, "Items did not move");
            // ensure sticks arrived
            Container rightChest = (Container) helper.getBlockEntity(right);
            assertTrue(rightChest.getItem(0).getCount() == 64, "Items did not move");


        });
    }
}
