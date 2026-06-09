package ca.teamdman.sfm.gametest.tests.migrated;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import ca.teamdman.sfm.common.registry.registration.SFMItems;
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Objects;
import java.util.stream.IntStream;



/**
 * Migrated from SFMCorrectnessGameTests.move_slots
 */
@SuppressWarnings({
        "RedundantSuppression",
        "DataFlowIssue",
        "OptionalGetWithoutIsPresent",
        "DuplicatedCode",
        "ArraysAsListWithZeroOrOneArgument"
})
@SFMGameTest
public class MoveSlotsGameTest extends SFMGameTestDefinition {

    @Override
    public String template() {
        return "3x2x1";
    }

    @Override
    public void run(SFMGameTestHelper helper) {
        helper.setBlock(new BlockPos(1, 1, 0), SFMBlocks.MANAGER.get());
        BlockPos rightPos = new BlockPos(0, 1, 0);
        helper.setBlock(rightPos, SFMBlocks.TEST_BARREL.get());
        BlockPos leftPos = new BlockPos(2, 1, 0);
        helper.setBlock(leftPos, SFMBlocks.TEST_BARREL.get());

        var rightChest = helper.getItemHandler(rightPos);
        var leftChest = helper.getItemHandler(leftPos);

        leftChest.insertItem(0, new ItemStack(Items.DIAMOND, 5), false);
        leftChest.insertItem(1, new ItemStack(Items.DIAMOND, 5), false);
        leftChest.insertItem(3, new ItemStack(Items.DIAMOND, 5), false);
        leftChest.insertItem(4, new ItemStack(Items.DIAMOND, 5), false);
        leftChest.insertItem(5, new ItemStack(Items.DIAMOND, 5), false);

        ManagerBlockEntity manager = helper.getBlockEntity(new BlockPos(1, 1, 0), ManagerBlockEntity.class);
        manager.setItem(0, new ItemStack(SFMItems.DISK.get()));
        manager.setProgram("""
                                       EVERY 20 TICKS DO
                                           INPUT FROM a TOP SIDE SLOTS 0,1,3-4,5
                                           OUTPUT TO a SLOTS 2
                                       END
                                   """.stripTrailing().stripIndent());

        // set the labels
        LabelPositionHolder.empty()
                .add("a", helper.absolutePos(leftPos))
                .add("b", helper.absolutePos(rightPos))
                .save(Objects.requireNonNull(manager.getDisk()));

        helper.succeedIfManagerDidThingWithoutLagging(manager, () -> {
            boolean success6 = leftChest.getStackInSlot(0).isEmpty();
            helper.getTick();
            helper.assertTrue(success6, "slot 0 did not leave");
            boolean success5 = leftChest.getStackInSlot(1).isEmpty();
            helper.getTick();
            helper.assertTrue(success5, "slot 1 did not leave");
            boolean success4 = leftChest.getStackInSlot(3).isEmpty();
            helper.getTick();
            helper.assertTrue(success4, "slot 3 did not leave");
            boolean success3 = leftChest.getStackInSlot(4).isEmpty();
            helper.getTick();
            helper.assertTrue(success3, "slot 4 did not leave");
            boolean success2 = leftChest.getStackInSlot(5).isEmpty();
            helper.getTick();
            helper.assertTrue(success2, "slot 5 did not leave");
            boolean success1 = leftChest.getStackInSlot(2).getCount() == 25;
            helper.getTick();
            helper.assertTrue(success1, "Items did not transfer to slot 2");
            boolean success = IntStream
                       .range(0, rightChest.getSlots())
                       .allMatch(slot -> rightChest.getStackInSlot(slot).isEmpty());
            helper.getTick();
            helper.assertTrue(success, "Chest b is not empty");

        });
    }
}
