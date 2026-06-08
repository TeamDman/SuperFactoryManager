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
import mekanism.common.tile.multiblock.TileEntityInductionPort;
import mekanism.common.util.UnitDisplayUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import java.util.List;



/**
 * Migrated from SFMMekanismCompatGameTests.mek_induction
 */
@SuppressWarnings({
        "RedundantSuppression",
        "DataFlowIssue",
        "OptionalGetWithoutIsPresent",
        "DuplicatedCode",
        "ArraysAsListWithZeroOrOneArgument"
})
@SFMGameTest
public class MekInductionGameTest extends SFMGameTestDefinition {

    @Override
    public String template() {
        return "25x3x25";
    }

    @Override
    public void run(SFMGameTestHelper helper) {
        // designate positions
        var managerPos = new BlockPos(1, 3, 0);
        var powerCubePos = new BlockPos(1, 2, 0);
        var inductionBeginPos = new BlockPos(0, 2, 1);
        var inductionInput = new BlockPos(1, 3, 1);

        // set up induction matrix
        for (int x = 0; x < 18; x++) {
            for (int z = 0; z < 18; z++) {
                for (int y = 0; y < 18; y++) {
                    //noinspection ExtractMethodRecommender
                    boolean isOutside = x == 0 || x == 17 || z == 0 || z == 17 || y == 0 || y == 17;
                    Block block;
                    if (isOutside) {
                        block = MekanismBlocks.INDUCTION_CASING.getBlock();
                    } else {
                        if (y == 1) {
                            block = MekanismBlocks.ULTIMATE_INDUCTION_CELL.getBlock();
                        } else {
                            block = MekanismBlocks.ULTIMATE_INDUCTION_PROVIDER.getBlock();
                        }
                    }
                    helper.setBlock(inductionBeginPos.offset(x, y, z), block);
                }
            }
        }
        helper.setBlock(inductionInput, MekanismBlocks.INDUCTION_PORT.getBlock());
        var inductionPort = (TileEntityInductionPort) helper.getBlockEntity(inductionInput);

        // set up the energy source
        helper.setBlock(powerCubePos, MekanismBlocks.CREATIVE_ENERGY_CUBE.getBlock());

        TileEntityEnergyCube powerCube = helper.getAndPrepMekTile(powerCubePos);
        powerCube.setEnergy(0, EnergyCubeTier.CREATIVE.getMaxEnergy());
//        powerCube.getConfig().setupIOConfig(TransmissionType.ENERGY,powerCube.getEnergyContainer(), RelativeSide.TOP, true);
//        powerCube.getConfig().

        // set up the manager
        helper.setBlock(managerPos, SFMBlocks.MANAGER.get());
        ManagerBlockEntity manager = (ManagerBlockEntity) helper.getBlockEntity(managerPos);
        manager.setItem(0, new ItemStack(SFMItems.DISK.get()));

        // create the program
        long incr = Integer.MAX_VALUE - 1; // minus one to avoid a rounding error
        long startingAmount = 0L;
        var program = """
                    NAME "induction matrix test"
                    EVERY 20 TICKS DO
                        INPUT %d fe:: FROM source NORTH SIDE
                        OUTPUT fe:: TO dest NORTH SIDE
                    END
                """.formatted(incr);

        // set the labels
        LabelPositionHolder.empty()
                .addAll("source", List.of(helper.absolutePos(powerCubePos)))
                .addAll("dest", List.of(helper.absolutePos(inductionInput)))
                .save(manager.getDisk());

        // we can't prefill since we can't wait a delay AND use succeedIfManagerDidThing
        // pre-fill the matrix by a little bit
        // we want to make sure SFM doesn't have problems inserting beyond MAX_INT
//        var startingAmount = FloatingLong.create(Integer.MAX_VALUE + incr);
//            inductionPort.insertEnergy(startingAmount, Action.EXECUTE);

        // launch the program
        manager.setProgram(program);
        helper.succeedIfManagerDidThingWithoutLagging(manager, () -> {
            if (!inductionPort.getMultiblock().isFormed()) {
                throw new GameTestAssertException("Induction matrix did not form");
            }

            var expected = startingAmount + incr;
            long joules = inductionPort.getEnergy(0);
            long energy = UnitDisplayUtils.EnergyUnit.FORGE_ENERGY.convertTo(joules);
            boolean success = energy == expected;
            helper.assertTrue(
                    success,
                    "Expected energy did not match"
            );
        });
    }
}
