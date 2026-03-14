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
public class OutputEmptySlotsOnlyNoEmptySpaceGameTest extends SFMGameTestDefinition {
    @Override
    public String template() {
        return "3x2x1";
    }

    @Override
    public void run(SFMGameTestHelper helper) {
        BlockPos managerPos = new BlockPos(1, 2, 0);
        BlockPos rightPos = new BlockPos(0, 2, 0);
        BlockPos leftPos = new BlockPos(2, 2, 0);

        helper.setBlock(managerPos, SFMBlocks.MANAGER_BLOCK.get());
        helper.setBlock(rightPos, SFMBlocks.TEST_BARREL_BLOCK.get());
        helper.setBlock(leftPos, SFMBlocks.TEST_BARREL_BLOCK.get());

        var rightChest = helper.getItemHandler(rightPos);
        var leftChest = helper.getItemHandler(leftPos);

        // Source: 16 cobblestone
        leftChest.insertItem(0, new ItemStack(Blocks.COBBLESTONE, 16), false);
        // Destination: fill the only targeted slots so there are no empty slots in that set
        rightChest.insertItem(0, new ItemStack(Blocks.COBBLESTONE, 1), false);
        rightChest.insertItem(1, new ItemStack(Blocks.STONE, 1), false);
        rightChest.insertItem(2, new ItemStack(Blocks.GRAVEL, 1), false);
        rightChest.insertItem(3, new ItemStack(Blocks.SAND, 1), false);
        rightChest.insertItem(4, new ItemStack(Blocks.NETHERRACK, 1), false);
        rightChest.insertItem(10, new ItemStack(Blocks.ANDESITE, 1), false);

        ManagerBlockEntity manager = (ManagerBlockEntity) helper.getBlockEntity(managerPos);
        manager.setItem(0, new ItemStack(SFMItems.DISK_ITEM.get()));

        manager.setProgram((
                                   """
                                           EVERY 20 TICKS DO
                                             INPUT FROM a
                                             OUTPUT TO EMPTY SLOTS IN b SLOTS 0-4,10
                                           END
                                           """.stripTrailing().stripIndent()
                           ));

        LabelPositionHolder.empty()
                .add("a", helper.absolutePos(leftPos))
                .add("b", helper.absolutePos(rightPos))
                .save(Objects.requireNonNull(manager.getDisk()));

        helper.succeedIfManagerDidThingWithoutLagging(
                manager, () -> {
                    // Since there are no empty slots among the targeted set, nothing should have moved.
                    assertTrue(leftChest.getStackInSlot(0).getCount() == 16, "Source should be unchanged");
                    assertTrue(rightChest.getStackInSlot(0).getCount() == 1, "Dest slot 0 should be unchanged");
                    assertTrue(rightChest.getStackInSlot(1).getCount() == 1, "Dest slot 1 should be unchanged");
                    assertTrue(rightChest.getStackInSlot(2).getCount() == 1, "Dest slot 2 should be unchanged");
                    assertTrue(rightChest.getStackInSlot(3).getCount() == 1, "Dest slot 3 should be unchanged");
                    assertTrue(rightChest.getStackInSlot(4).getCount() == 1, "Dest slot 4 should be unchanged");
                    assertTrue(rightChest.getStackInSlot(10).getCount() == 1, "Dest slot 10 should be unchanged");
                }
        );
    }
}
