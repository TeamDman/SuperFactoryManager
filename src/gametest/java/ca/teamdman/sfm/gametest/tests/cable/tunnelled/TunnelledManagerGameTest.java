package ca.teamdman.sfm.gametest.tests.cable.tunnelled;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.registry.SFMBlocks;
import ca.teamdman.sfm.common.registry.SFMItems;
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import ca.teamdman.sfm.gametest.SequentialAssertionHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

import java.util.List;
import java.util.Objects;

import static ca.teamdman.sfm.gametest.SFMGameTestCountHelpers.assertCount;

@SuppressWarnings({"DataFlowIssue"})
@SFMGameTest
public class TunnelledManagerGameTest extends SFMGameTestDefinition {
    @Override
    public String template() {
        return "3x2x1";
    }

    @Override
    public void run(SFMGameTestHelper helper) {
        // declare positions
        BlockPos managerPos = new BlockPos(1, 2, 0);
        BlockPos rightPos = new BlockPos(0, 2, 0);
        BlockPos leftPos = new BlockPos(2, 2, 0);

        // set blocks
        helper.setBlock(managerPos, SFMBlocks.TUNNELLED_MANAGER_BLOCK.get());
        helper.setBlock(rightPos, SFMBlocks.TEST_BARREL_BLOCK.get());
        helper.setBlock(leftPos, SFMBlocks.TEST_BARREL_BLOCK.get());

        // get handlers
        var rightChest = helper.getItemHandler(rightPos);
        var leftChest = helper.getItemHandler(leftPos);

        // prepare resources
        leftChest.insertItem(0, new ItemStack(Blocks.DIRT, 64), false);

        // prepare manager
        ManagerBlockEntity manager = (ManagerBlockEntity) helper.getBlockEntity(managerPos);
        manager.setItem(0, new ItemStack(SFMItems.DISK_ITEM.get()));

        // set program
        manager.setProgram("""
                                       EVERY 20 TICKS DO
                                           INPUT 1 FROM manager WEST SIDE
                                           OUTPUT TO manager EAST SIDE
                                       END
                                   """.stripTrailing().stripIndent());

        // set the labels
        LabelPositionHolder.empty()
                .add("manager", helper.absolutePos(managerPos))
                .save(Objects.requireNonNull(manager.getDisk()));

        // schedule success check
        manager.addProgramHooks(new SequentialAssertionHooks(
                helper,
                List.of(
                        () -> {
                            assertCount(leftChest, 63, "One should have departed");
                            assertCount(rightChest, 1, "One should have arrived");
                        },
                        () -> {
                            assertCount(leftChest, 62, "Two should have departed");
                            assertCount(rightChest, 2, "Two should have arrived");
                        },
                        () -> {
                            assertCount(leftChest, 61, "Three should have departed");
                            assertCount(rightChest, 3, "Three should have arrived");
                        },
                        () -> {
                            assertCount(leftChest, 60, "Four should have departed");
                            assertCount(rightChest, 4, "Four should have arrived");
                            helper.succeed();
                        }
                )
        ));
    }
}
