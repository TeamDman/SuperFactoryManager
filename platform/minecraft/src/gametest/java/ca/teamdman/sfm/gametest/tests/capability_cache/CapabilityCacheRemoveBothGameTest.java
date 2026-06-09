package ca.teamdman.sfm.gametest.tests.capability_cache;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import ca.teamdman.sfm.common.registry.registration.SFMItems;
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import ca.teamdman.sfm.gametest.SequentialAssertionHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;



@SuppressWarnings({"DataFlowIssue"})
@SFMGameTest
public class CapabilityCacheRemoveBothGameTest extends SFMGameTestDefinition {
    @Override
    public String template() {

        return "3x2x1";
    }

    @Override
    public int maxTicks() {

        return 220;
    }

    @Override
    public void run(SFMGameTestHelper helper) {

        // declare positions
        BlockPos managerPos = new BlockPos(1, 1, 0);
        BlockPos rightPos = new BlockPos(0, 1, 0);
        BlockPos leftPos = new BlockPos(2, 1, 0);

        // set blocks
        helper.setBlock(managerPos, SFMBlocks.MANAGER.get());
        helper.setBlock(rightPos, SFMBlocks.TEST_BARREL.get());
        helper.setBlock(leftPos, SFMBlocks.TEST_BARREL.get());

        // get handlers
        AtomicReference<IItemHandler> rightChest = new AtomicReference<>(helper.getItemHandler(rightPos));
        AtomicReference<IItemHandler> leftChest = new AtomicReference<>(helper.getItemHandler(leftPos));

        // prepare resources
        leftChest.get().insertItem(0, new ItemStack(Blocks.DIRT, 64), false);

        // prepare manager
        ManagerBlockEntity manager = helper.getBlockEntity(managerPos, ManagerBlockEntity.class);
        manager.setItem(0, new ItemStack(SFMItems.DISK.get()));

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
                            // validate first move
                            helper.getTick();

                            helper.assertCount(leftChest, 63, "One should have departed");
                            helper.getTick();

                            helper.assertCount(rightChest, 1, "One should have arrived");
                        },
                        () -> {
                            // validate second move
                            helper.getTick();

                            helper.assertCount(leftChest, 62, "Another should have departed");
                            helper.getTick();

                            helper.assertCount(rightChest, 2, "Another should have arrived");

                            // break the source block
                            helper.setBlock(leftPos, Blocks.AIR);
                        },
                        () -> {
                            // validate no move after source broken
                            helper.getTick();

                            helper.assertCount(leftChest, 62, "None should depart after source is broken");
                            helper.getTick();

                            helper.assertCount(rightChest, 2, "None should arrive after source is broken");
                        },
                        () -> {
                            // validate still no move
                            helper.getTick();

                            helper.assertCount(leftChest, 62, "Still none should depart after source is broken");
                            helper.getTick();

                            helper.assertCount(rightChest, 2, "Still none should arrive after source is broken");

                            // restore source block
                            helper.setBlock(leftPos, SFMBlocks.TEST_BARREL.get());
                            leftChest.set(helper.getItemHandler(leftPos));
                            leftChest.get().insertItem(0, new ItemStack(Blocks.DIRT, 64), false);
                        },
                        () -> {
                            // validate move after source restored
                            helper.getTick();

                            helper.assertCount(leftChest, 63, "Another departs after source restored");
                            helper.getTick();

                            helper.assertCount(rightChest, 3, "Another arrives after source restored");
                        },
                        () -> {
                            // validate another move
                            helper.getTick();

                            helper.assertCount(leftChest, 62, "Another departs after source restored");
                            helper.getTick();

                            helper.assertCount(rightChest, 4, "Another arrives after source restored");

                            // break the destination block
                            helper.setBlock(rightPos, Blocks.AIR);
                        },
                        () -> {
                            // validate no move after dest broken
                            helper.getTick();

                            helper.assertCount(leftChest, 62, "None should depart after dest is broken");
                            helper.getTick();

                            helper.assertCount(rightChest, 4, "None should arrive after dest is broken");
                        },
                        () -> {
                            // validate still no move
                            helper.getTick();

                            helper.assertCount(leftChest, 62, "Still none should depart after dest is broken");
                            helper.getTick();

                            helper.assertCount(rightChest, 4, "Still none should arrive after dest is broken");

                            // restore destination block
                            helper.setBlock(rightPos, SFMBlocks.TEST_BARREL.get());
                            rightChest.set(helper.getItemHandler(rightPos));
                        },
                        () -> {
                            // validate move after dest restored
                            helper.getTick();

                            helper.assertCount(leftChest, 61, "Another departs after dest restored");
                            helper.getTick();

                            helper.assertCount(rightChest, 1, "Another arrives after dest restored");
                        },
                        () -> {
                            // validate another move
                            helper.getTick();

                            helper.assertCount(leftChest, 60, "Another departs after dest restored");
                            helper.getTick();

                            helper.assertCount(rightChest, 2, "Another arrives after dest restored");

                            // enqueue success
                            helper.succeed();
                        }
                )
        ));
    }

}
