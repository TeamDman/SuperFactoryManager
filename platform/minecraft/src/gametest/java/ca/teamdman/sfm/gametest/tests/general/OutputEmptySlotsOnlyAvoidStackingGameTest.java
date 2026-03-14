package ca.teamdman.sfm.gametest.tests.general;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.registry.SFMBlocks;
import ca.teamdman.sfm.common.registry.SFMItems;
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

import java.util.Objects;

import static ca.teamdman.sfm.gametest.SFMGameTestMethodHelpers.assertTrue;

@SuppressWarnings({"DataFlowIssue"})
@SFMGameTest
public class OutputEmptySlotsOnlyAvoidStackingGameTest extends SFMGameTestDefinition {
    @Override
    public String template() {
        return "3x2x1";
    }

    @Override
    public void run(SFMGameTestHelper helper) {
        // positions
        BlockPos managerPos = new BlockPos(1, 2, 0);
        BlockPos rightPos = new BlockPos(0, 2, 0);
        BlockPos leftPos = new BlockPos(2, 2, 0);

        // blocks
        helper.setBlock(managerPos, SFMBlocks.MANAGER_BLOCK.get());
        helper.setBlock(rightPos, SFMBlocks.TEST_BARREL_BLOCK.get());
        helper.setBlock(leftPos, SFMBlocks.TEST_BARREL_BLOCK.get());

        // item handlers
        var rightChest = helper.getItemHandler(rightPos);
        var leftChest = helper.getItemHandler(leftPos);

        // prepare: source has 20 dirt; destination has 10 dirt in slot 0 (partially filled stack)
        leftChest.insertItem(0, new ItemStack(Blocks.DIRT, 20), false);
        rightChest.insertItem(0, new ItemStack(Blocks.DIRT, 10), false);

        // manager & disk
        ManagerBlockEntity manager = (ManagerBlockEntity) helper.getBlockEntity(managerPos);
        manager.setItem(0, new ItemStack(SFMItems.DISK_ITEM.get()));

        // program: only output to empty slots (slot 1 specifically)
        manager.setProgram((
                                   """
                                           EVERY 20 TICKS DO
                                             INPUT FROM a
                                             OUTPUT TO EMPTY SLOTS IN b SLOTS 1
                                           END
                                           """.stripTrailing().stripIndent()
                           ));

        // labels
        LabelPositionHolder.empty()
                .add("a", helper.absolutePos(leftPos))
                .add("b", helper.absolutePos(rightPos))
                .save(Objects.requireNonNull(manager.getDisk()));

        // assert
        helper.succeedIfManagerDidThingWithoutLagging(
                manager, () -> {
                    // all 20 moved from left
                    assertTrue(leftChest.getStackInSlot(0).isEmpty(), "Source not emptied");
                    // destination slot 0 remains 10 (no stacking)
                    assertTrue(rightChest.getStackInSlot(0).getCount() == 10, "Destination slot 0 should remain 10");
                    // destination slot 1 received 20
                    assertTrue(rightChest.getStackInSlot(1).getCount() == 20, "Destination slot 1 should be 20");
                }
        );
    }
}
