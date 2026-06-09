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
public class OutputDefaultStacksWhenNoEmptyModifierGameTest extends SFMGameTestDefinition {
    @Override
    public String template() {
        return "3x2x1";
    }

    @Override
    public void run(SFMGameTestHelper helper) {
        BlockPos managerPos = new BlockPos(1, 1, 0);
        BlockPos rightPos = new BlockPos(0, 1, 0);
        BlockPos leftPos = new BlockPos(2, 1, 0);

        helper.setBlock(managerPos, SFMBlocks.MANAGER.get());
        helper.setBlock(rightPos, SFMBlocks.TEST_BARREL.get());
        helper.setBlock(leftPos, SFMBlocks.TEST_BARREL.get());

        var rightChest = helper.getItemHandler(rightPos);
        var leftChest = helper.getItemHandler(leftPos);

        // Source: 20 dirt
        leftChest.insertItem(0, new ItemStack(Blocks.DIRT, 20), false);
        // Destination: slot 0 already has 10 dirt
        rightChest.insertItem(0, new ItemStack(Blocks.DIRT, 10), false);

        ManagerBlockEntity manager = helper.getBlockEntity(managerPos, ManagerBlockEntity.class);
        manager.setItem(0, new ItemStack(SFMItems.DISK.get()));

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
                    boolean success2 = leftChest.getStackInSlot(0).isEmpty();
                    helper.getTick();
                    helper.assertTrue(success2, "Source not emptied");
                    // Destination slot 0 received all 20 (10 -> 30)
                    boolean success1 = rightChest.getStackInSlot(0).getCount() == 30;
                    helper.getTick();
                    helper.assertTrue(success1, "Dest slot 0 should be 30 after stacking");
                    // Slot 1 remains empty
                    boolean success = rightChest.getStackInSlot(1).isEmpty();
                    helper.getTick();
                    helper.assertTrue(success, "Dest slot 1 should remain empty");
                }
        );
    }
}

