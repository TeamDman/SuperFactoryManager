package ca.teamdman.sfm.gametest.tests.cable.tunnelled;

import ca.teamdman.sfm.common.registry.SFMBlocks;
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.HopperBlockEntity;

import static ca.teamdman.sfm.gametest.SFMGameTestCountHelpers.assertCount;

@SuppressWarnings({"DataFlowIssue", "RedundantSuppression"})
@SFMGameTest
public class TunnelledManagerHopperGameTest extends SFMGameTestDefinition {
    private final int OPERATION_ASSESSMENT_COUNT = 5;
    @Override
    public String template() {

        return "3x2x1";
    }

    @Override
    public int maxTicks() {

        return OPERATION_ASSESSMENT_COUNT * HopperBlockEntity.MOVE_ITEM_SPEED;
    }

    @Override
    public void run(SFMGameTestHelper helper) {
        // declare positions
        BlockPos managerPos = new BlockPos(1, 2, 0);
        BlockPos invPos = new BlockPos(0, 2, 0);
        BlockPos hopperPos = new BlockPos(2, 2, 0);

        // set blocks
        helper.setBlock(managerPos, SFMBlocks.TUNNELLED_MANAGER_BLOCK.get());
        helper.setBlock(invPos, SFMBlocks.TEST_BARREL_BLOCK.get());
        helper.setBlock(hopperPos, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.WEST));

        // get handlers
        var inv = helper.getItemHandler(invPos);
        var hopper = helper.getItemHandler(hopperPos);

        // prepare resources
        hopper.insertItem(0, new ItemStack(Blocks.DIRT, 64), false);

        for (int ii = 0; ii < OPERATION_ASSESSMENT_COUNT; ii++) {
            final int i = ii;
            final boolean last = ii == OPERATION_ASSESSMENT_COUNT - 1;
            helper.runAfterDelay(
                    i * HopperBlockEntity.MOVE_ITEM_SPEED, () -> {
                        assertCount(hopper, Blocks.DIRT, 64 - i, 64 - i + " should be in hopper");
                        assertCount(inv, Blocks.DIRT, i, i + " should be in inventory");
                        if (last) helper.succeed();
                    }
            );
        }
    }

}
