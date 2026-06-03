package ca.teamdman.sfm.gametest.tests.compat.mekanism;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import ca.teamdman.sfm.common.registry.registration.SFMItems;
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import mekanism.common.registries.MekanismBlocks;
import mekanism.common.tier.BinTier;
import mekanism.common.tile.TileEntityBin;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import static ca.teamdman.sfm.gametest.SFMGameTestMethodHelpers.assertTrue;


/**
 * Migrated from SFMMekanismCompatGameTests.mek_bin_empty
 */
@SuppressWarnings({
        "RedundantSuppression",
        "DataFlowIssue",
        "OptionalGetWithoutIsPresent",
        "DuplicatedCode",
        "ArraysAsListWithZeroOrOneArgument"
})
@SFMGameTest
public class MekBinEmptyGameTest extends SFMGameTestDefinition {
    @Override
    public String template() {
        return "3x2x1";
    }

    @Override
    public void run(SFMGameTestHelper helper) {
        // designate positions
        var leftPos = new BlockPos(2, 1, 0);
        var rightPos = new BlockPos(0, 1, 0);
        var managerPos = new BlockPos(1, 1, 0);

        // set up the world
        helper.setBlock(leftPos, MekanismBlocks.ULTIMATE_BIN.get());
        var left = (helper.getBlockEntity(leftPos, TileEntityBin.class));
        helper.setBlock(rightPos, MekanismBlocks.ULTIMATE_BIN.get());
        var right = (helper.getBlockEntity(rightPos, TileEntityBin.class));
        helper.setBlock(managerPos, SFMBlocks.MANAGER.get());
        var manager = (helper.getBlockEntity(managerPos, ManagerBlockEntity.class));

        // set up the program
        manager.setItem(0, new ItemStack(SFMItems.DISK.get()));
        manager.setProgram("""
                                   EVERY 20 TICKS DO
                                     INPUT FROM a NORTH SIDE
                                     OUTPUT TO b TOP SIDE
                                   END
                                   """.stripIndent());

        // set the labels
        LabelPositionHolder.empty()
                .add("a", helper.absolutePos(leftPos))
                .add("b", helper.absolutePos(rightPos))
                .save(manager.getDisk());

        left.getBinSlot().setStack(new ItemStack(Items.COAL, BinTier.ULTIMATE.getStorage()));
        right.getBinSlot().setEmpty();
        helper.succeedIfManagerDidThingWithoutLagging(manager, () -> {
            assertTrue(left.getBinSlot().getCount() == BinTier.ULTIMATE.getStorage() - 64, "Contents did not depart", helper.getTick());
            assertTrue(right.getBinSlot().getCount() == 64, "Contents did not arrive", helper.getTick());
            assertTrue(right.getBinSlot().getStack().getItem() == Items.COAL, "Contents wrong type", helper.getTick());

        });
    }
}
