package ca.teamdman.sfm.gametest.tests.compat.mekanism;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import ca.teamdman.sfm.common.registry.registration.SFMItems;
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import mekanism.api.chemical.ChemicalStack;
import mekanism.common.registries.MekanismBlocks;
import mekanism.common.registries.MekanismChemicals;
import mekanism.common.tier.ChemicalTankTier;
import mekanism.common.tile.TileEntityChemicalTank;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;


/**
 * Migrated from SFMMekanismCompatGameTests.mek_chemtank_infusion_full
 */
@SuppressWarnings({
        "RedundantSuppression",
        "DataFlowIssue",
        "OptionalGetWithoutIsPresent",
        "DuplicatedCode",
        "ArraysAsListWithZeroOrOneArgument"
})
@SFMGameTest
public class MekChemtankInfusionFullGameTest extends SFMGameTestDefinition {

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
        helper.setBlock(leftPos, MekanismBlocks.ULTIMATE_CHEMICAL_TANK.get());
        TileEntityChemicalTank leftTank = helper.getAndPrepMekTile(leftPos);
        helper.setBlock(rightPos, MekanismBlocks.ULTIMATE_CHEMICAL_TANK.get());
        TileEntityChemicalTank rightTank = helper.getAndPrepMekTile(rightPos);
        helper.setBlock(managerPos, SFMBlocks.MANAGER.get());
        var manager = (helper.getBlockEntity(managerPos, ManagerBlockEntity.class));

        // set up the program
        manager.setItem(0, new ItemStack(SFMItems.DISK.get()));
        manager.setProgram("""
                                   EVERY 20 TICKS DO
                                     INPUT infusion:*:* FROM a NORTH SIDE -- mek can extract from front by default
                                     OUTPUT infusion:*:* TO b TOP SIDE -- mek can insert to top by default
                                   END
                                   """.stripIndent());

        // set the labels
        LabelPositionHolder.empty()
                .add("a", helper.absolutePos(leftPos))
                .add("b", helper.absolutePos(rightPos))
                .save(manager.getDisk());

        // ensure it can move into a nearly full tank
        leftTank.getChemicalTank().setStack(new ChemicalStack(MekanismChemicals.REDSTONE, 2_000_000L));
        rightTank
                .getChemicalTank()
                .setStack(new ChemicalStack(
                        MekanismChemicals.REDSTONE,
                        ChemicalTankTier.ULTIMATE.getStorage() - 1_000_000L
                ));
        helper.succeedIfManagerDidThingWithoutLagging(manager, () -> {
            helper.assertTrue(leftTank.getChemicalTank().getStack().amount() == 1_000_000L, "Contents did not depart", helper.getTick());
            helper.assertTrue(
                    rightTank.getChemicalTank().getStack().amount() == ChemicalTankTier.ULTIMATE.getStorage(),
                    "Contents did not arrive"
            , helper.getTick());
        });
    }
}
