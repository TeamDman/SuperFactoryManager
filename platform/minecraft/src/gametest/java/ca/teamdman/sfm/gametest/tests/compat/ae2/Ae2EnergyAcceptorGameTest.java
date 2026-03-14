package ca.teamdman.sfm.gametest.tests.compat.ae2;

import appeng.core.definitions.AEBlocks;
import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.compat.SFMMekanismCompat;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.registry.SFMBlocks;
import ca.teamdman.sfm.common.registry.SFMItems;
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import mekanism.api.RelativeSide;
import mekanism.common.lib.transmitter.TransmissionType;
import mekanism.common.registries.MekanismBlocks;
import mekanism.common.tier.EnergyCubeTier;
import mekanism.common.tile.TileEntityEnergyCube;
import mekanism.common.tile.component.config.DataType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

import static ca.teamdman.sfm.gametest.SFMGameTestMethodHelpers.assertTrue;

@SuppressWarnings("DataFlowIssue")
@SFMGameTest
public class Ae2EnergyAcceptorGameTest extends SFMGameTestDefinition {
    @Override
    public String template() {
        return "1x2x5";
    }

    @Override
    public void run(SFMGameTestHelper helper) {
        // Layout along Z: [Z=4 Cube] [Z=3 Manager] [Z=2 Energy Acceptor] [Z=1 Dense Cell] [Z=0 Dense Cell]
        var cubePos = new BlockPos(0, 2, 4);
        var managerPos = new BlockPos(0, 2, 3);
        var acceptorPos = new BlockPos(0, 2, 2);
        var dense1Pos = new BlockPos(0, 2, 1);
        var dense2Pos = new BlockPos(0, 2, 0);

        // Place blocks
        helper.setBlock(cubePos, MekanismBlocks.ULTIMATE_ENERGY_CUBE.getBlock());
        var cube = (TileEntityEnergyCube) helper.getBlockEntity(cubePos);
        SFMMekanismCompat.configureExclusiveIO(
                cube,
                TransmissionType.ENERGY,
                RelativeSide.BOTTOM,
                DataType.OUTPUT
        );

        helper.setBlock(managerPos, SFMBlocks.MANAGER_BLOCK.get());
        var manager = (ManagerBlockEntity) helper.getBlockEntity(managerPos);

        helper.setBlock(acceptorPos, AEBlocks.ENERGY_ACCEPTOR.block());
        helper.setBlock(dense1Pos, AEBlocks.DENSE_ENERGY_CELL.block());
        helper.setBlock(dense2Pos, AEBlocks.DENSE_ENERGY_CELL.block());

        // Program: move FE from cube to energy acceptor
        manager.setItem(0, new ItemStack(SFMItems.DISK_ITEM.get()));
        manager.setProgram(
                """
                        EVERY 20 TICKS DO
                          INPUT fe:: FROM a BOTTOM SIDE
                          OUTPUT fe:: TO acceptor TOP SIDE
                        END
                        """.stripIndent()
        );

        // Labels
        LabelPositionHolder.empty()
                .add("a", helper.absolutePos(cubePos))
                .add("acceptor", helper.absolutePos(acceptorPos))
                .save(manager.getDisk());

        // Initialize energy: source full
        cube.setEnergy(0, EnergyCubeTier.ULTIMATE.getMaxEnergy());

        helper.succeedIfManagerDidThingWithoutLagging(
                manager, () -> {
                    assertTrue(
                            cube.getEnergy(0).smallerThan(EnergyCubeTier.ULTIMATE.getMaxEnergy()),
                            "Mekanism cube did not output FE to AE2 acceptor"
                    );
                }
        );
    }
}
