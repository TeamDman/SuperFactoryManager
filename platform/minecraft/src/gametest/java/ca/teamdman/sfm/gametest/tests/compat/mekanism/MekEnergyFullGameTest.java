package ca.teamdman.sfm.gametest.tests.compat.mekanism;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import ca.teamdman.sfm.common.registry.registration.SFMItems;
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import mekanism.common.registries.MekanismBlocks;
import mekanism.common.tier.EnergyCubeTier;
import mekanism.common.tile.TileEntityEnergyCube;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;




/**
 * Migrated from SFMMekanismCompatGameTests.mek_energy_full
 */
@SuppressWarnings({
        "RedundantSuppression",
        "DataFlowIssue",
        "OptionalGetWithoutIsPresent",
        "DuplicatedCode",
        "ArraysAsListWithZeroOrOneArgument"
})
@SFMGameTest
public class MekEnergyFullGameTest extends SFMGameTestDefinition {

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
        helper.setBlock(leftPos, MekanismBlocks.ULTIMATE_ENERGY_CUBE.get());
        TileEntityEnergyCube left = helper.getAndPrepMekTile(leftPos);
        helper.setBlock(rightPos, MekanismBlocks.ULTIMATE_ENERGY_CUBE.get());
        TileEntityEnergyCube right = helper.getAndPrepMekTile(rightPos);
        helper.setBlock(managerPos, SFMBlocks.MANAGER.get());
        var manager = helper.getBlockEntity(managerPos, ManagerBlockEntity.class);

        // set up the program
        manager.setItem(0, new ItemStack(SFMItems.DISK.get()));
        manager.setProgram("""
                                   EVERY 20 TICKS DO
                                     INPUT forge_energy:forge:energy FROM a NORTH SIDE
                                     OUTPUT forge_energy:forge:energy TO b TOP SIDE
                                   END
                                   """.stripIndent());

        // set the labels
        LabelPositionHolder.empty()
                .add("a", helper.absolutePos(leftPos))
                .add("b", helper.absolutePos(rightPos))
                .save(manager.getDisk());

        left.setEnergy(0, EnergyCubeTier.ULTIMATE.getMaxEnergy());
        right.setEnergy(0, EnergyCubeTier.ULTIMATE.getMaxEnergy() - (1_000));
        helper.succeedIfManagerDidThingWithoutLagging(manager, () -> {
            helper.assertTrue(
                    left.getEnergy(0) == (EnergyCubeTier.ULTIMATE.getMaxEnergy() - (1_000)),
                    "Contents did not depart"
            , helper.getTick());
            helper.assertTrue(right.getEnergy(0) == EnergyCubeTier.ULTIMATE.getMaxEnergy(), "Contents did not arrive", helper.getTick());
        });
    }
}
