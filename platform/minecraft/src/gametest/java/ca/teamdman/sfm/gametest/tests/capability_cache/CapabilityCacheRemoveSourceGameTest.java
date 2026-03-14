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
public class CapabilityCacheRemoveSourceGameTest extends SFMGameTestDefinition {
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
        var rightChest = helper.getItemHandler(rightPos);
        AtomicReference<IItemHandler> leftChest = new AtomicReference<>(helper.getItemHandler(leftPos));

        // prepare resources
        leftChest.get().insertItem(0, new ItemStack(Blocks.DIRT, 64), false);

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

                            // break the source block
                            helper.setBlock(leftPos, Blocks.AIR);
                        },
                        () -> {
                            // validate things aren't moving
                            assertCount(leftChest, 63, "None should depart after source is broken");

                            // restore source block
                            helper.setBlock(leftPos, SFMBlocks.TEST_BARREL_BLOCK.get());
                            leftChest.set(helper.getItemHandler(leftPos));
                            leftChest.get().insertItem(0, new ItemStack(Blocks.DIRT, 64), false);
                        },
                        () -> {
                            // validate that items have resumed moving
                            assertCount(leftChest, 63, "Another departs after source restored");
                            assertCount(rightChest, 2, "Another arrives after source restored");

                            // enqueue success
                            helper.succeed();
                        }
                )
        ));
    }

}
