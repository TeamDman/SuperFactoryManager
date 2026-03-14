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
import net.minecraft.world.level.block.Blocks;

import java.util.Objects;
import java.util.stream.IntStream;

import static ca.teamdman.sfm.gametest.SFMGameTestMethodHelpers.assertTrue;

/**
 * Migrated from SFMCorrectnessGameTests.many_outputs
 */
@SuppressWarnings({
        "RedundantSuppression",
        "DataFlowIssue",
        "OptionalGetWithoutIsPresent",
        "DuplicatedCode",
        "ArraysAsListWithZeroOrOneArgument"
})
@SFMGameTest
public class ManyOutputsGameTest extends SFMGameTestDefinition {

    @Override
    public String template() {
        return "3x4x3";
    }

    @Override
    public void run(SFMGameTestHelper helper) {
        BlockPos managerPos = new BlockPos(1, 2, 1);
        BlockPos sourcePos = new BlockPos(1, 3, 1);
        BlockPos dest1Pos = new BlockPos(2, 2, 1);
        BlockPos dest2Pos = new BlockPos(0, 2, 1);

        // set up inventories
        helper.setBlock(sourcePos, SFMBlocks.TEST_BARREL_BLOCK.get());
        helper.setBlock(dest1Pos, SFMBlocks.TEST_BARREL_BLOCK.get());
        helper.setBlock(dest2Pos, SFMBlocks.TEST_BARREL_BLOCK.get());


        var sourceInv = helper.getItemHandler(sourcePos);

        var dest1Inv = helper.getItemHandler(dest1Pos);

        var dest2Inv = helper.getItemHandler(dest2Pos);

        for (int i = 0; i < sourceInv.getSlots(); i++) {
            sourceInv.insertItem(i, new ItemStack(Blocks.DIRT, 64), false);
        }

        // set up manager
        helper.setBlock(managerPos, SFMBlocks.MANAGER_BLOCK.get());
        ManagerBlockEntity manager = (ManagerBlockEntity) helper.getBlockEntity(managerPos);
        manager.setItem(0, new ItemStack(SFMItems.DISK_ITEM.get()));
        manager.setProgram("""
                                       EVERY 20 TICKS DO
                                           INPUT FROM source
                                           OUTPUT 64 dirt TO EACH dest
                                       END
                                   """.stripTrailing().stripIndent());
        // set the labels
        LabelPositionHolder.empty()
                .add("source", helper.absolutePos(sourcePos))
                .add("dest", helper.absolutePos(dest1Pos))
                .add("dest", helper.absolutePos(dest2Pos))
                .save(Objects.requireNonNull(manager.getDisk()));

        helper.succeedIfManagerDidThingWithoutLagging(manager, () -> {
            int found = IntStream
                    .range(0, sourceInv.getSlots())
                    .mapToObj(sourceInv::getStackInSlot)
                    .mapToInt(ItemStack::getCount)
                    .sum();
            assertTrue(found == 64 * (sourceInv.getSlots() - 2), "Dirt did not leave (found " + found + " (" + (
                    found > 64 ? found / 64 + "x stacks + " + found % 64 : found
            ) + " dirt))");
            int total;
            total = 0;
            for (int i = 0; i < dest1Inv.getSlots(); i++) {
                ItemStack x = dest1Inv.getStackInSlot(i);
                if (x.is(Items.DIRT)) {
                    total += dest1Inv.getStackInSlot(i).getCount();
                }
            }
            assertTrue(total == 64, "Dirt did not arrive properly 1");
            total = 0;
            for (int i = 0; i < dest2Inv.getSlots(); i++) {
                ItemStack x = dest2Inv.getStackInSlot(i);
                if (x.is(Items.DIRT)) {
                    total += dest2Inv.getStackInSlot(i).getCount();
                }
            }
            assertTrue(total == 64, "Dirt did not arrive properly 2");
        });
    }
}
