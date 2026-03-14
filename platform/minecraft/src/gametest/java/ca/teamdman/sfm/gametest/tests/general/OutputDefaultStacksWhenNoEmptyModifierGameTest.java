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
public class OutputDefaultStacksWhenNoEmptyModifierGameTest extends SFMGameTestDefinition {
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

        // Source: 20 dirt
        leftChest.insertItem(0, new ItemStack(Blocks.DIRT, 20), false);
        // Destination: slot 0 already has 10 dirt
        rightChest.insertItem(0, new ItemStack(Blocks.DIRT, 10), false);

        ManagerBlockEntity manager = (ManagerBlockEntity) helper.getBlockEntity(managerPos);
        manager.setItem(0, new ItemStack(SFMItems.DISK_ITEM.get()));

        // No EMPTY SLOTS IN modifier -> should stack into slot 0 first
        manager.setProgram((
                                   """
                                           EVERY 20 TICKS DO
                                             INPUT FROM a
                                             OUTPUT TO b
                                           END
                                           """.stripTrailing().stripIndent()
                           ));

        LabelPositionHolder.empty()
                .add("a", helper.absolutePos(leftPos))
                .add("b", helper.absolutePos(rightPos))
                .save(Objects.requireNonNull(manager.getDisk()));

        helper.succeedIfManagerDidThingWithoutLagging(
                manager, () -> {
                    // Source emptied
                    assertTrue(leftChest.getStackInSlot(0).isEmpty(), "Source not emptied");
                    // Destination slot 0 received all 20 (10 -> 30)
                    assertTrue(
                            rightChest.getStackInSlot(0).getCount() == 30,
                            "Dest slot 0 should be 30 after stacking"
                    );
                    // Slot 1 remains empty
                    assertTrue(rightChest.getStackInSlot(1).isEmpty(), "Dest slot 1 should remain empty");
                }
        );
    }
}

