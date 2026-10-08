package ca.teamdman.sfm.gametest.tests.compat.mekanism;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import ca.teamdman.sfm.common.registry.registration.SFMItems;
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21' %}
import mekanism.api.chemical.infuse.InfusionStack;
{% when '1.21.1', '26.1.2' %}
import mekanism.api.chemical.ChemicalStack;
{% endcase %}
import mekanism.common.registries.MekanismBlocks;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21' %}
import mekanism.common.registries.MekanismInfuseTypes;
{% when '1.21.1', '26.1.2' %}
import mekanism.common.registries.MekanismChemicals;
{% endcase %}
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
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
        helper.setBlock(leftPos, MekanismBlocks.ULTIMATE_CHEMICAL_TANK.getBlock());
        var leftTank = helper.getBlockEntity(leftPos, TileEntityChemicalTank.class);
        helper.setBlock(rightPos, MekanismBlocks.ULTIMATE_CHEMICAL_TANK.getBlock());
        var rightTank = helper.getBlockEntity(rightPos, TileEntityChemicalTank.class);
{% when '1.21', '1.21.1' %}
        helper.setBlock(leftPos, MekanismBlocks.ULTIMATE_CHEMICAL_TANK.getBlock());
        TileEntityChemicalTank leftTank = helper.getAndPrepMekTile(leftPos);
        helper.setBlock(rightPos, MekanismBlocks.ULTIMATE_CHEMICAL_TANK.getBlock());
        TileEntityChemicalTank rightTank = helper.getAndPrepMekTile(rightPos);
{% when '26.1.2' %}
        helper.setBlock(leftPos, MekanismBlocks.ULTIMATE_CHEMICAL_TANK.get());
        TileEntityChemicalTank leftTank = helper.getAndPrepMekTile(leftPos);
        helper.setBlock(rightPos, MekanismBlocks.ULTIMATE_CHEMICAL_TANK.get());
        TileEntityChemicalTank rightTank = helper.getAndPrepMekTile(rightPos);
{% endcase %}
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
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21' %}
        leftTank.getInfusionTank().setStack(new InfusionStack(MekanismInfuseTypes.REDSTONE.get(), 2_000_000L));
{% when '1.21.1' %}
        leftTank.getChemicalTank().setStack(new ChemicalStack(MekanismChemicals.REDSTONE.get(), 2_000_000L));
{% when '26.1.2' %}
        leftTank.getChemicalTank().setStack(new ChemicalStack(MekanismChemicals.REDSTONE, 2_000_000L));
{% endcase %}
        rightTank
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21' %}
                .getInfusionTank()
                .setStack(new InfusionStack(
                        MekanismInfuseTypes.REDSTONE.get(),
{% when '1.21.1' %}
                .getChemicalTank()
                .setStack(new ChemicalStack(
                        MekanismChemicals.REDSTONE.get(),
{% when '26.1.2' %}
                .getChemicalTank()
                .setStack(new ChemicalStack(
                        MekanismChemicals.REDSTONE,
{% endcase %}
                        ChemicalTankTier.ULTIMATE.getStorage() - 1_000_000L
                ));
        helper.succeedIfManagerDidThingWithoutLagging(manager, () -> {
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '26.1.2' %}
{% when '1.21.1' %}
            helper.assertTrue(
                    leftTank.getChemicalTank().getStack().getAmount() == 1_000_000L,
                    "Contents did not depart"
            );
{% endcase %}
            helper.assertTrue(
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21' %}
                    leftTank.getInfusionTank().getStack().getAmount() == 1_000_000L,
                    "Contents did not depart"
            );
            helper.assertTrue(
                    rightTank.getInfusionTank().getStack().getAmount() == ChemicalTankTier.ULTIMATE.getStorage(),
{% when '1.21.1' %}
                    rightTank.getChemicalTank().getStack().getAmount() == ChemicalTankTier.ULTIMATE.getStorage(),
{% when '26.1.2' %}
                    leftTank.getChemicalTank().getStack().amount() == 1_000_000L,
                    "Contents did not depart"
            );
            helper.assertTrue(
                    rightTank.getChemicalTank().getStack().amount() == ChemicalTankTier.ULTIMATE.getStorage(),
{% endcase %}
                    "Contents did not arrive"
            );
        });
    }
}
