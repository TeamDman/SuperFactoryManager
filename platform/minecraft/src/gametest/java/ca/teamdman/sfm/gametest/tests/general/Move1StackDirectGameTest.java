package ca.teamdman.sfm.gametest.tests.general;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import ca.teamdman.sfm.common.registry.registration.SFMItems;
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

import java.util.Objects;



@SuppressWarnings({"DataFlowIssue"})
@SFMGameTest
public class Move1StackDirectGameTest extends SFMGameTestDefinition {
    @Override
    public String template() {
        return "3x2x1";
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
        var rightChest = helper.getItemHandler(rightPos);
        var leftChest = helper.getItemHandler(leftPos);

        // prepare resources
        leftChest.insertItem(0, new ItemStack(Blocks.DIRT, 64), false);

        // prepare manager
        ManagerBlockEntity manager = helper.getBlockEntity(managerPos, ManagerBlockEntity.class);
        manager.setItem(0, new ItemStack(SFMItems.DISK.get()));

        // set program
        manager.setProgram("""
                                       EVERY 20 TICKS DO
                                           INPUT FROM a
                                           OUTPUT TO b
                                       END
                                   """.stripTrailing().stripIndent());

        // set the labels
        LabelPositionHolder.empty()
                .add("a", helper.absolutePos(leftPos))
                .add("b", helper.absolutePos(rightPos))
                .save(Objects.requireNonNull(manager.getDisk()));

        // schedule success check
        helper.succeedIfManagerDidThingWithoutLagging(manager, () -> {
            boolean success1 = leftChest.getStackInSlot(0).isEmpty();
            helper.getTick();
            helper.assertTrue(success1, "Dirt did not move");
            boolean success = rightChest.getStackInSlot(0).getCount() == 64;
            helper.getTick();
            helper.assertTrue(success, "Dirt did not move");
        });
    }
}
