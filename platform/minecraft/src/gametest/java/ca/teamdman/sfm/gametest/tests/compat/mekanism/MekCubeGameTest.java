package ca.teamdman.sfm.gametest.tests.compat.mekanism;

import ca.teamdman.sfm.common.compat.SFMMekanismCompat;
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import ca.teamdman.sfm.gametest.declarative.SFMDeclarativeTestBuilder;
import ca.teamdman.sfm.gametest.declarative.SFMTestBlockEntitySpec;
import ca.teamdman.sfm.gametest.declarative.SFMTestSpec;
import mekanism.api.RelativeSide;
import mekanism.common.lib.transmitter.TransmissionType;
import mekanism.common.registries.MekanismBlocks;
import mekanism.common.tile.TileEntityEnergyCube;
import mekanism.common.tile.component.config.DataType;
import net.minecraft.core.BlockPos;

/**
 * Migrated from SFMMekanismCompatGameTests.mek_cube
 */
@SuppressWarnings({
        "RedundantSuppression",
        "DataFlowIssue",
        "OptionalGetWithoutIsPresent",
        "DuplicatedCode",
        "ArraysAsListWithZeroOrOneArgument"
})
@SFMGameTest
public class MekCubeGameTest extends SFMGameTestDefinition {

    @Override
    public String template() {
        return "3x2x1";
    }


    @Override
    public void run(SFMGameTestHelper helper) {
        SFMTestSpec spec = new SFMTestSpec()
                .setProgram("""
                                        EVERY 20 TICKS DO
                                            INPUT fe:: FROM a BOTTOM SIDE
                                            OUTPUT fe:: TO b TOP SIDE
                                        END
                                    """)
                .addBlock(SFMTestBlockEntitySpec.<TileEntityEnergyCube>of(
                        "a",
                        new BlockPos(1, 0, 0),
                        MekanismBlocks.BASIC_ENERGY_CUBE.getBlock(),
                        (tileEntityCube) -> {
                            tileEntityCube.setEnergy(0, SFMMekanismCompat.createForgeEnergy(1000));
                            SFMMekanismCompat.configureExclusiveIO(
                                    tileEntityCube,
                                    TransmissionType.ENERGY,
                                    RelativeSide.BOTTOM,
                                    DataType.OUTPUT
                            );
                        }
                ))
                .addBlock(SFMTestBlockEntitySpec.<TileEntityEnergyCube>of(
                        "b",
                        new BlockPos(-1, 0, 0),
                        MekanismBlocks.BASIC_ENERGY_CUBE.getBlock(),
                        (tileEntityCube) -> SFMMekanismCompat.configureExclusiveIO(
                                tileEntityCube,
                                TransmissionType.ENERGY,
                                RelativeSide.TOP,
                                DataType.INPUT
                        )
                ))
                .preCondition("ONE a HAS EQ 1000 fe::")
                .preCondition("ONE b HAS EQ 0 fe::")
                .postCondition("ONE a HAS EQ 0 fe::")
                .postCondition("ONE b HAS EQ 1000 fe::");
        new SFMDeclarativeTestBuilder(helper, spec).run();
    }
}
