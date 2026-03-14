package ca.teamdman.sfm.gametest.tests.general;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.item.DiskItem;
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

@SFMGameTest
@SuppressWarnings("DataFlowIssue")
public class ManagerSwapProgramGameTest extends SFMGameTestDefinition {
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
        var leftChest = helper.getItemHandler(leftPos);

        // prepare resources
        leftChest.insertItem(0, new ItemStack(Blocks.DIRT, 64), false);
        leftChest.insertItem(1, new ItemStack(Blocks.SAND, 64), false);

        // assign labels
        LabelPositionHolder labels = LabelPositionHolder.empty();
        labels.add("left", helper.absolutePos(leftPos));
        labels.add("right", helper.absolutePos(rightPos));
        labels.add("manager", helper.absolutePos(managerPos));

        // create disk 1
        ItemStack moveSandDisk = new ItemStack(SFMItems.DISK_ITEM.get());
        DiskItem.setProgram(
                moveSandDisk, """
                        NAME "move sand"
                        EVERY 20 TICKS DO
                            INPUT 1 sand FROM left
                            OUTPUT TO right
                        forget
                            input from manager
                            output to right
                        forget
                            input disk from left
                            output to manager
                        END
                        """.stripTrailing().stripIndent()
        );
        labels.save(moveSandDisk);

        // create disk 2
        ItemStack moveDirtDisk = new ItemStack(SFMItems.DISK_ITEM.get());
        DiskItem.setProgram(
                moveDirtDisk, """
                        NAME "move dirt"
                        EVERY 20 TICKS DO
                            INPUT 1 dirt FROM left
                            OUTPUT TO right
                        forget
                            input from manager
                            output to left
                        forget
                            input disk from right
                            output to manager
                        END
                        """.stripTrailing().stripIndent()
        );
        labels.save(moveDirtDisk);

        // prepare manager with dirt disk
        ManagerBlockEntity manager = (ManagerBlockEntity) helper.getBlockEntity(managerPos);
        manager.setItem(0, moveDirtDisk);

        // prepare a chest with the other disk
        rightChest.insertItem(2, moveSandDisk, false);


        // add outcome hooks
        manager.addProgramHooks(new SequentialAssertionHooks(
                helper,
                List.of(
                        () -> {
                            helper.assertExpr(manager, "left has eq 64 sand");
                            helper.assertExpr(manager, "left has eq 63 dirt");
                            helper.assertExpr(manager, "right has eq 1 dirt");
                            helper.assertExpr(manager, "manager has eq 1 disk");
                            helper.assertExpr(manager, "left has eq 1 disk");
                            helper.assertExpr(manager, "right has eq 0 disk");
                        },
                        () -> {
                            helper.assertExpr(manager, "left has eq 63 sand");
                            helper.assertExpr(manager, "left has eq 63 dirt");
                            helper.assertExpr(manager, "right has eq 1 dirt");
                            helper.assertExpr(manager, "right has eq 1 sand");
                            helper.assertExpr(manager, "manager has eq 1 disk");
                            helper.assertExpr(manager, "left has eq 0 disk");
                            helper.assertExpr(manager, "right has eq 1 disk");
                        },
                        () -> {
                            helper.assertExpr(manager, "left has eq 63 sand");
                            helper.assertExpr(manager, "left has eq 62 dirt");
                            helper.assertExpr(manager, "right has eq 2 dirt");
                            helper.assertExpr(manager, "right has eq 1 sand");
                            helper.assertExpr(manager, "manager has eq 1 disk");
                            helper.assertExpr(manager, "left has eq 1 disk");
                            helper.assertExpr(manager, "right has eq 0 disk");
                            helper.succeed();
                        }
                )
        ));
    }

}
