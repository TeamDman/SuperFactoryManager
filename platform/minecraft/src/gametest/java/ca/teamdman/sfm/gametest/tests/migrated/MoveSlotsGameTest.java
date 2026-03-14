package ca.teamdman.sfm.gametest.tests.migrated;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.registry.SFMBlocks;
import ca.teamdman.sfm.common.registry.SFMItems;
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Objects;
import java.util.stream.IntStream;

import static ca.teamdman.sfm.gametest.SFMGameTestMethodHelpers.assertTrue;

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
        helper.setBlock(new BlockPos(1, 2, 0), SFMBlocks.MANAGER_BLOCK.get());
        BlockPos rightPos = new BlockPos(0, 2, 0);
        helper.setBlock(rightPos, SFMBlocks.TEST_BARREL_BLOCK.get());
        BlockPos leftPos = new BlockPos(2, 2, 0);
        helper.setBlock(leftPos, SFMBlocks.TEST_BARREL_BLOCK.get());

        var rightChest = helper.getItemHandler(rightPos);
        var leftChest = helper.getItemHandler(leftPos);

        leftChest.insertItem(0, new ItemStack(Items.DIAMOND, 5), false);
        leftChest.insertItem(1, new ItemStack(Items.DIAMOND, 5), false);
        leftChest.insertItem(3, new ItemStack(Items.DIAMOND, 5), false);
        leftChest.insertItem(4, new ItemStack(Items.DIAMOND, 5), false);
        leftChest.insertItem(5, new ItemStack(Items.DIAMOND, 5), false);

        ManagerBlockEntity manager = (ManagerBlockEntity) helper.getBlockEntity(new BlockPos(1, 2, 0));
        manager.setItem(0, new ItemStack(SFMItems.DISK_ITEM.get()));
        manager.setProgram("""
                                       EVERY 20 TICKS DO
                                           INPUT FROM a TOP SIDE SLOTS 0,1,3-4,5
                                           OUTPUT TO a SLOTS 2 -- note this isn't moving to b, which is fine cause slot 2 isn't one of the input slots we are checking
                                       END
                                   """.stripTrailing().stripIndent());

        // set the labels
        LabelPositionHolder.empty()
                .add("a", helper.absolutePos(leftPos))
                .add("b", helper.absolutePos(rightPos))
                .save(Objects.requireNonNull(manager.getDisk()));

        helper.succeedIfManagerDidThingWithoutLagging(manager, () -> {
            assertTrue(leftChest.getStackInSlot(0).isEmpty(), "slot 0 should leave");
            assertTrue(leftChest.getStackInSlot(1).isEmpty(), "slot 1 should leave");
            assertTrue(leftChest.getStackInSlot(3).isEmpty(), "slot 3 should leave");
            assertTrue(leftChest.getStackInSlot(4).isEmpty(), "slot 4 should leave");
            assertTrue(leftChest.getStackInSlot(5).isEmpty(), "slot 5 should leave");
            assertTrue(leftChest.getStackInSlot(2).getCount() == 25, "Items should transfer to slot 2");
            assertTrue(IntStream
                               .range(0, rightChest.getSlots())
                               .allMatch(slot -> rightChest.getStackInSlot(slot).isEmpty()), "Chest b should be empty");

        });
    }
}
