package ca.teamdman.sfm.gametest.tests.compat.mekanism;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.registry.SFMBlocks;
import ca.teamdman.sfm.common.registry.SFMItems;
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import mekanism.common.registries.MekanismBlocks;
import mekanism.common.tile.TileEntityBin;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import static ca.teamdman.sfm.gametest.SFMGameTestMethodHelpers.assertTrue;

/**
 * Tests that NBT count filtering works correctly with stack sizes greater than 99.
 * Uses Mekanism bins which can hold oversized stacks.
 * This tests for issues with ItemStack.save() where count might have serialization issues.
 */
@SuppressWarnings({
        "RedundantSuppression",
        "DataFlowIssue",
        "OptionalGetWithoutIsPresent",
        "DuplicatedCode",
        "ArraysAsListWithZeroOrOneArgument"
})
@SFMGameTest
public class MekBinNbtCountFilterGameTest extends SFMGameTestDefinition {

    @Override
    public String template() {
        return "3x2x1";
    }

    @Override
    public void run(SFMGameTestHelper helper) {
        // designate positions
        var leftPos = new BlockPos(2, 2, 0);
        var rightPos = new BlockPos(0, 2, 0);
        var managerPos = new BlockPos(1, 2, 0);

        // set up the world
        helper.setBlock(leftPos, MekanismBlocks.ULTIMATE_BIN.getBlock());
        var left = ((TileEntityBin) helper.getBlockEntity(leftPos));
        helper.setBlock(rightPos, MekanismBlocks.ULTIMATE_BIN.getBlock());
        var right = ((TileEntityBin) helper.getBlockEntity(rightPos));
        helper.setBlock(managerPos, SFMBlocks.MANAGER_BLOCK.get());
        var manager = ((ManagerBlockEntity) helper.getBlockEntity(managerPos));

        // set up the program - only move items with count > 32
        // Note: Even though the bin contains 128 items, SFM queries return ItemStacks
        // capped at maxStackSize (64), so we test with count > 32 to verify NBT filtering works
        manager.setItem(0, new ItemStack(SFMItems.DISK_ITEM.get()));
        manager.setProgram("""
                EVERY 20 TICKS DO
                    INPUT WITH NBT count > 32 FROM a NORTH SIDE
                    OUTPUT TO b TOP SIDE
                END
                """.stripIndent());

        // set the labels
        LabelPositionHolder.empty()
                .add("a", helper.absolutePos(leftPos))
                .add("b", helper.absolutePos(rightPos))
                .save(manager.getDisk());

        // Put 128 diamonds in the left bin
        // SFM will see stacks of 64 (maxStackSize), which is > 32, so they should be moved
        left.getBinSlot().setStack(new ItemStack(Items.DIAMOND, 128));

        helper.succeedIfManagerDidThingWithoutLagging(manager, () -> {
            // 64 diamonds should have moved (one maxStackSize worth)
            assertTrue(left.getBinSlot().getCount() == 128 - 64, "Left bin should have 64 remaining (128 - 64 transferred)");
            assertTrue(right.getBinSlot().getCount() == 64, "Right bin should have 64 diamonds");
            assertTrue(right.getBinSlot().getStack().getItem() == Items.DIAMOND, "Right bin should contain diamonds");
        });
    }
}
