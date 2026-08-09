package ca.teamdman.sfm.gametest.tests.compat.mekanism;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import ca.teamdman.sfm.common.registry.registration.SFMItems;
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import mekanism.api.chemical.infuse.InfusionStack;
import mekanism.common.registries.MekanismBlocks;
import mekanism.common.registries.MekanismInfuseTypes;
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
        var leftPos = new BlockPos(2, 2, 0);
        var rightPos = new BlockPos(0, 2, 0);
        var managerPos = new BlockPos(1, 2, 0);

        // set up the world
        helper.setBlock(leftPos, MekanismBlocks.ULTIMATE_CHEMICAL_TANK.getBlock());
        var leftTank = helper.getBlockEntity(leftPos, TileEntityChemicalTank.class);
        helper.setBlock(rightPos, MekanismBlocks.ULTIMATE_CHEMICAL_TANK.getBlock());
        var rightTank = helper.getBlockEntity(rightPos, TileEntityChemicalTank.class);
        helper.setBlock(managerPos, SFMBlocks.MANAGER.get());
        var manager = helper.getBlockEntity(managerPos, ManagerBlockEntity.class);

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
        leftTank.getInfusionTank().setStack(new InfusionStack(MekanismInfuseTypes.REDSTONE.get(), 2_000_000L));
        rightTank
                .getInfusionTank()
                .setStack(new InfusionStack(
                        MekanismInfuseTypes.REDSTONE.get(),
                        ChemicalTankTier.ULTIMATE.getStorage() - 1_000_000L
                ));
        helper.succeedIfManagerDidThingWithoutLagging(manager, () -> {
            helper.assertTrue(
                    leftTank.getInfusionTank().getStack().getAmount() == 1_000_000L,
                    "Contents did not depart"
            );
            helper.assertTrue(
                    rightTank.getInfusionTank().getStack().getAmount() == ChemicalTankTier.ULTIMATE.getStorage(),
                    "Contents did not arrive"
            );
        });
    }
}
