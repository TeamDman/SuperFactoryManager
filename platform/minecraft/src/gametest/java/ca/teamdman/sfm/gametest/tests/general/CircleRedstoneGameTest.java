package ca.teamdman.sfm.gametest.tests.general;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.registry.SFMBlocks;
import ca.teamdman.sfm.common.registry.SFMItems;
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.RepeaterBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraftforge.items.IItemHandler;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

@SuppressWarnings({"DataFlowIssue"})
@SFMGameTest
public class CircleRedstoneGameTest extends SFMGameTestDefinition {
    @Override
    public String template() {

        return "5x2x5";
    }

    @Override
    public void run(SFMGameTestHelper helper) {

        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                helper.setBlock(new BlockPos(x, 1, z), SFMBlocks.CABLE_BLOCK.get());
            }
        }

        BlockPos barrelPos = new BlockPos(2, 2, 0);
        helper.setBlock(barrelPos, SFMBlocks.TEST_BARREL_BLOCK.get());
        IItemHandler barrelHandler = helper.getItemHandler(barrelPos);
        barrelHandler.insertItem(0, new ItemStack(Items.DIRT, 64), false);

        BlockPos manager1Pos = new BlockPos(1, 2, 1);
        helper.setBlock(manager1Pos, SFMBlocks.MANAGER_BLOCK.get());
        BlockPos manager2Pos = new BlockPos(3, 2, 3);
        helper.setBlock(manager2Pos, SFMBlocks.MANAGER_BLOCK.get());

        // Set up manager1
        ManagerBlockEntity manager1 = (ManagerBlockEntity) helper.getBlockEntity(manager1Pos);
        manager1.setItem(0, new ItemStack(SFMItems.DISK_ITEM.get()));
        manager1.setProgram("""
                                       EVERY REDSTONE PULSE DO
                                           INPUT FROM barrel SLOTS 0
                                           OUTPUT TO barrel SLOTS 1
                                       END
                                   """.stripTrailing().stripIndent());

        // Set up manager2
        ManagerBlockEntity manager2 = (ManagerBlockEntity) helper.getBlockEntity(manager2Pos);
        manager2.setItem(0, new ItemStack(SFMItems.DISK_ITEM.get()));
        manager2.setProgram("""
                                       EVERY REDSTONE PULSE DO
                                           INPUT FROM barrel SLOTS 1
                                           OUTPUT TO barrel SLOTS 0
                                       END
                                   """.stripTrailing().stripIndent());

        // Set labels
        LabelPositionHolder.empty()
                .add("barrel", helper.absolutePos(barrelPos))
                .save(Objects.requireNonNull(manager1.getDisk()));
        LabelPositionHolder.empty()
                .add("barrel", helper.absolutePos(barrelPos))
                .save(Objects.requireNonNull(manager2.getDisk()));

        BlockState repeaterFacingEast = Blocks.REPEATER.defaultBlockState()
                .setValue(RepeaterBlock.FACING, Direction.EAST)
                .setValue(RepeaterBlock.DELAY, 4);
        BlockState repeaterFacingSouth = Blocks.REPEATER.defaultBlockState()
                .setValue(RepeaterBlock.FACING, Direction.SOUTH)
                .setValue(RepeaterBlock.DELAY, 4);
        BlockState repeaterFacingWest = Blocks.REPEATER.defaultBlockState()
                .setValue(RepeaterBlock.FACING, Direction.WEST)
                .setValue(RepeaterBlock.DELAY, 4);
        BlockState repeaterFacingNorth = Blocks.REPEATER.defaultBlockState()
                .setValue(RepeaterBlock.FACING, Direction.NORTH)
                .setValue(RepeaterBlock.DELAY, 4);

        helper.setBlock(new BlockPos(2, 2, 1), repeaterFacingEast);
        helper.setBlock(new BlockPos(3, 2, 2), repeaterFacingSouth);
        helper.setBlock(new BlockPos(2, 2, 3), repeaterFacingWest);
        helper.setBlock(new BlockPos(1, 2, 2), repeaterFacingNorth);

        List<BlockPos> redstoneDustPositions = List.of(
                new BlockPos(3, 2, 1),
                new BlockPos(1, 2, 3)
        );
        for (BlockPos pos : redstoneDustPositions) {
            helper.setBlock(pos, Blocks.REDSTONE_WIRE.defaultBlockState());
        }

        BlockPos buttonPos = new BlockPos(1, 2, 4);
        BlockState buttonState = Blocks.STONE_BUTTON.defaultBlockState()
                .setValue(ButtonBlock.FACING, Direction.NORTH)
                .setValue(ButtonBlock.FACE, AttachFace.FLOOR);
        helper.setBlock(buttonPos, buttonState);
        helper.pressButton(buttonPos);

        // Check for alternation
        AtomicInteger countSlot0 = new AtomicInteger(0);
        AtomicInteger countSlot1 = new AtomicInteger(0);
        for (int i = 1; i <= 100; i++) {
            helper.runAfterDelay(i, () -> {
                boolean slot0Full = barrelHandler.getStackInSlot(0).getCount() == 64 && barrelHandler.getStackInSlot(1).isEmpty();
                boolean slot1Full = barrelHandler.getStackInSlot(1).getCount() == 64 && barrelHandler.getStackInSlot(0).isEmpty();
                if (slot0Full) {
                    countSlot0.incrementAndGet();
                }
                if (slot1Full) {
                    countSlot1.incrementAndGet();
                }
                if (slot0Full && slot1Full) {
                    helper.fail("Alternation failed: both slots full");
                }
                if (countSlot0.get() >= 20 && countSlot1.get() >= 20) {
                    helper.succeed();
                }
            });
        }
    }

}
