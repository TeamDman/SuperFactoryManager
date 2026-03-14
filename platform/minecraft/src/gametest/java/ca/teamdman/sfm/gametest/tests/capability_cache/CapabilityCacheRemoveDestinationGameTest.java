package ca.teamdman.sfm.gametest.tests.capability_cache;

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
import net.minecraftforge.items.IItemHandler;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import static ca.teamdman.sfm.gametest.SFMGameTestCountHelpers.assertCount;

@SuppressWarnings({"DataFlowIssue"})
@SFMGameTest
public class CapabilityCacheRemoveDestinationGameTest extends SFMGameTestDefinition {
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
        helper.setBlock(managerPos, SFMBlocks.MANAGER_BLOCK.get());
        helper.setBlock(rightPos, SFMBlocks.TEST_BARREL_BLOCK.get());
        helper.setBlock(leftPos, SFMBlocks.TEST_BARREL_BLOCK.get());

        // get handlers
        AtomicReference<IItemHandler> rightChest = new AtomicReference<>(helper.getItemHandler(rightPos));
        var leftChest = helper.getItemHandler(leftPos);

        // prepare resources
        leftChest.insertItem(0, new ItemStack(Blocks.DIRT, 64), false);

        // prepare manager
        ManagerBlockEntity manager = (ManagerBlockEntity) helper.getBlockEntity(managerPos);
        manager.setItem(0, new ItemStack(SFMItems.DISK_ITEM.get()));

        // set program
        manager.setProgram("""
                                       EVERY 20 TICKS DO
                                           INPUT 1 FROM a
                                           OUTPUT TO b
                                       END
                                   """.stripTrailing().stripIndent());

        // set the labels
        LabelPositionHolder.empty()
                .add("a", helper.absolutePos(leftPos))
                .add("b", helper.absolutePos(rightPos))
                .save(Objects.requireNonNull(manager.getDisk()));

        // add outcome hooks
        manager.addProgramHooks(new SequentialAssertionHooks(
                helper,
                List.of(
                        () -> {
                            // validate one item has moved
                            assertCount(leftChest, 63, "One should have departed");
                            assertCount(rightChest, 1, "One should have arrived");

                            // break the destination block
                            helper.setBlock(rightPos, Blocks.AIR);
                        },
                        () -> {
                            // validate things aren't moving
                            assertCount(leftChest, 63, "None should depart after destination is broken");

                            // restore destination block
                            helper.setBlock(rightPos, SFMBlocks.TEST_BARREL_BLOCK.get());
                            rightChest.set(helper.getItemHandler(rightPos));
                            rightChest.get().insertItem(0, new ItemStack(Blocks.DIRT, 1), false);
                        },
                        () -> {
                            // validate that items have resumed moving
                            assertCount(leftChest, 62, "Another departs after dest restored");
                            assertCount(rightChest, 2, "Another arrives after dest restored");

                            // enqueue success
                            helper.succeed();
                        }
                )
        ));
    }

}
