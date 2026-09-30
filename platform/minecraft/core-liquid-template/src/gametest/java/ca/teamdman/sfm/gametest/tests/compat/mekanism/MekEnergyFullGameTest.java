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
        var leftPos = new BlockPos(2, 2, 0);
        var rightPos = new BlockPos(0, 2, 0);
        var managerPos = new BlockPos(1, 2, 0);

        // set up the world
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
        helper.setBlock(leftPos, MekanismBlocks.ULTIMATE_ENERGY_CUBE.getBlock());
        var left = helper.getBlockEntity(leftPos, TileEntityEnergyCube.class);
        helper.setBlock(rightPos, MekanismBlocks.ULTIMATE_ENERGY_CUBE.getBlock());
        var right = helper.getBlockEntity(rightPos, TileEntityEnergyCube.class);
{% when '1.21', '1.21.1' %}
        helper.setBlock(leftPos, MekanismBlocks.ULTIMATE_ENERGY_CUBE.getBlock());
        TileEntityEnergyCube left = helper.getAndPrepMekTile(leftPos);
        helper.setBlock(rightPos, MekanismBlocks.ULTIMATE_ENERGY_CUBE.getBlock());
        TileEntityEnergyCube right = helper.getAndPrepMekTile(rightPos);
{% when '26.1.2' %}
        helper.setBlock(leftPos, MekanismBlocks.ULTIMATE_ENERGY_CUBE.get());
        TileEntityEnergyCube left = helper.getAndPrepMekTile(leftPos);
        helper.setBlock(rightPos, MekanismBlocks.ULTIMATE_ENERGY_CUBE.get());
        TileEntityEnergyCube right = helper.getAndPrepMekTile(rightPos);
{% endcase %}
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
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
        right.setEnergy(0, EnergyCubeTier.ULTIMATE.getMaxEnergy().subtract(1_000));
{% when '1.21', '1.21.1', '26.1.2' %}
        right.setEnergy(0, EnergyCubeTier.ULTIMATE.getMaxEnergy() - (1_000));
{% endcase %}
        helper.succeedIfManagerDidThingWithoutLagging(manager, () -> {
            helper.assertTrue(
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
                    left.getEnergy(0).equals(EnergyCubeTier.ULTIMATE.getMaxEnergy().subtract(1_000)),
{% when '1.21', '1.21.1', '26.1.2' %}
                    left.getEnergy(0) == (EnergyCubeTier.ULTIMATE.getMaxEnergy() - (1_000)),
{% endcase %}
                    "Contents did not depart"
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
            );
            helper.assertTrue(
                    right.getEnergy(0).equals(EnergyCubeTier.ULTIMATE.getMaxEnergy()),
                    "Contents did not arrive"
            );

{% when '1.21', '1.21.1' %}
            );
            helper.assertTrue(right.getEnergy(0) == EnergyCubeTier.ULTIMATE.getMaxEnergy(), "Contents did not arrive");
{% when '26.1.2' %}
            , helper.getTick());
            helper.assertTrue(right.getEnergy(0) == EnergyCubeTier.ULTIMATE.getMaxEnergy(), "Contents did not arrive", helper.getTick());
{% endcase %}
        });
    }
}
