package ca.teamdman.sfm.gametest.tests.compat.multiple;

import ca.teamdman.sfm.common.capability.SFMBlockCapabilityDiscovery;
import ca.teamdman.sfm.common.capability.SFMWellKnownCapabilities;
import ca.teamdman.sfm.common.capability.energystorage.EnergyAcceptorEnergyStorageWrapper;
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import mekanism.common.registries.MekanismBlocks;
import mekanism.common.tile.TileEntityEnergyCube;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.energy.IEnergyStorage;

import static ca.teamdman.sfm.gametest.SFMGameTestMethodHelpers.getAndPrepMekTile;


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
        helper.setBlock(cubePos, MekanismBlocks.ULTIMATE_ENERGY_CUBE.getBlock());
        TileEntityEnergyCube cube = getAndPrepMekTile(helper, cubePos);

        IEnergyStorage found = SFMBlockCapabilityDiscovery.discoverCapabilityFromLevel(
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
