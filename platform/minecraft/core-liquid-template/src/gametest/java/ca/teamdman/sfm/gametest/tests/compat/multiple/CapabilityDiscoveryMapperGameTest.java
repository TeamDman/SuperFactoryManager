package ca.teamdman.sfm.gametest.tests.compat.multiple;

import ca.teamdman.sfm.common.capability.SFMBlockCapabilityDiscovery;
import ca.teamdman.sfm.common.capability.SFMWellKnownCapabilities;
import ca.teamdman.sfm.common.capability.energystorage.EnergyAcceptorEnergyStorageWrapper;
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import mekanism.common.registries.MekanismBlocks;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21' %}
{% when '1.21.1', '26.1.2' %}
import mekanism.common.tile.TileEntityEnergyCube;
{% endcase %}
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21' %}
import net.minecraftforge.energy.IEnergyStorage;
{% when '1.21.1' %}
import net.neoforged.neoforge.energy.IEnergyStorage;

import static ca.teamdman.sfm.gametest.SFMGameTestMethodHelpers.getAndPrepMekTile;
{% when '26.1.2' %}
import net.neoforged.neoforge.transfer.energy.EnergyHandler;

import static ca.teamdman.sfm.gametest.SFMGameTestMethodHelpers.getAndPrepMekTile;
{% endcase %}


@SuppressWarnings({
        "RedundantSuppression",
        "DataFlowIssue",
        "OptionalGetWithoutIsPresent",
        "DuplicatedCode",
        "ArraysAsListWithZeroOrOneArgument"
})
@SFMGameTest
public class CapabilityDiscoveryMapperGameTest extends SFMGameTestDefinition {

    @Override
    public String template() {
        return "1x2x1";
    }


    @Override
    public void run(SFMGameTestHelper helper) {
        var cubePos = new BlockPos(0, 2, 0);
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21' %}
        helper.setBlock(cubePos, MekanismBlocks.ULTIMATE_ENERGY_CUBE.getBlock());
{% when '1.21.1' %}
        helper.setBlock(cubePos, MekanismBlocks.ULTIMATE_ENERGY_CUBE.getBlock());
        TileEntityEnergyCube cube = getAndPrepMekTile(helper, cubePos);
{% when '26.1.2' %}
        helper.setBlock(cubePos, MekanismBlocks.ULTIMATE_ENERGY_CUBE.get());
        TileEntityEnergyCube cube = getAndPrepMekTile(helper, cubePos);
{% endcase %}

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
        IEnergyStorage found = SFMBlockCapabilityDiscovery.discoverCapabilityFromLevel(
{% when '26.1.2' %}
        EnergyHandler found = SFMBlockCapabilityDiscovery.discoverCapabilityFromLevel(
{% endcase %}
                helper.getLevel(),
                SFMWellKnownCapabilities.ENERGY,
                helper.absolutePos(cubePos),
                Direction.EAST
        ).unwrap();

        if (found instanceof EnergyAcceptorEnergyStorageWrapper) {
            helper.fail("Should not have found EnergyAcceptorEnergyStorageWrapper for non-AE energy block");
        } else {
            helper.succeed();
        }
    }
}
