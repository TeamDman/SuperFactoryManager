package ca.teamdman.sfm.gametest.tests.compat.mekanism;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.21', '1.21.1', '26.1.2' %}
{% when '1.20.4' %}
import ca.teamdman.sfm.common.compat.SFMMekanismCompat;
{% endcase %}
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import ca.teamdman.sfm.common.registry.registration.SFMItems;
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
import mekanism.api.math.FloatingLong;
{% when '1.21', '1.21.1', '26.1.2' %}
{% endcase %}
import mekanism.common.registries.MekanismBlocks;
import mekanism.common.tier.EnergyCubeTier;
import mekanism.common.tile.TileEntityEnergyCube;
import mekanism.common.tile.multiblock.TileEntityInductionPort;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
{% when '1.21', '1.21.1', '26.1.2' %}
import mekanism.common.util.UnitDisplayUtils;
{% endcase %}
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestAssertException;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
{% when '26.1.2' %}
import net.minecraft.network.chat.Component;
{% endcase %}
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
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
                        block = MekanismBlocks.INDUCTION_CASING.getBlock();
{% when '26.1.2' %}
                        block = MekanismBlocks.INDUCTION_CASING.get();
{% endcase %}
                    } else {
                        if (y == 1) {
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
                            block = MekanismBlocks.ULTIMATE_INDUCTION_CELL.getBlock();
{% when '26.1.2' %}
                            block = MekanismBlocks.ULTIMATE_INDUCTION_CELL.get();
{% endcase %}
                        } else {
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
                            block = MekanismBlocks.ULTIMATE_INDUCTION_PROVIDER.getBlock();
{% when '26.1.2' %}
                            block = MekanismBlocks.ULTIMATE_INDUCTION_PROVIDER.get();
{% endcase %}
                        }
                    }
                    helper.setBlock(inductionBeginPos.offset(x, y, z), block);
                }
            }
        }
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
        helper.setBlock(inductionInput, MekanismBlocks.INDUCTION_PORT.getBlock());
{% when '26.1.2' %}
        helper.setBlock(inductionInput, MekanismBlocks.INDUCTION_PORT.get());
{% endcase %}
        var inductionPort = helper.getBlockEntity(inductionInput, TileEntityInductionPort.class);

        // set up the energy source
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
        helper.setBlock(powerCubePos, MekanismBlocks.CREATIVE_ENERGY_CUBE.getBlock());
{% when '26.1.2' %}
        helper.setBlock(powerCubePos, MekanismBlocks.CREATIVE_ENERGY_CUBE.get());
{% endcase %}

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
        TileEntityEnergyCube powerCube = helper.getBlockEntity(powerCubePos, TileEntityEnergyCube.class);
{% when '1.21', '1.21.1', '26.1.2' %}
        TileEntityEnergyCube powerCube = helper.getAndPrepMekTile(powerCubePos);
{% endcase %}
        powerCube.setEnergy(0, EnergyCubeTier.CREATIVE.getMaxEnergy());
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
{% when '1.21', '1.21.1', '26.1.2' %}
//        powerCube.getConfig().setupIOConfig(TransmissionType.ENERGY,powerCube.getEnergyContainer(), RelativeSide.TOP, true);
//        powerCube.getConfig().
{% endcase %}

        // set up the manager
        helper.setBlock(managerPos, SFMBlocks.MANAGER.get());
        ManagerBlockEntity manager = helper.getBlockEntity(managerPos, ManagerBlockEntity.class);
        manager.setItem(0, new ItemStack(SFMItems.DISK.get()));

        // create the program
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3' %}
        long incr = 10_000_000_000L;
        var startingAmount = FloatingLong.create(0L);
{% when '1.20.4' %}
        long incr = Integer.MAX_VALUE;
        var startingAmount = FloatingLong.create(0L);
{% when '1.21', '1.21.1', '26.1.2' %}
        long incr = Integer.MAX_VALUE - 1; // minus one to avoid a rounding error
        long startingAmount = 0L;
{% endcase %}
        var program = """
                    NAME "induction matrix test"
                    EVERY 20 TICKS DO
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3' %}
                        INPUT %d mekanism_energy:: FROM source NORTH SIDE
                        OUTPUT mekanism_energy:: TO dest NORTH SIDE
{% when '1.20.4', '1.21', '1.21.1', '26.1.2' %}
                        INPUT %d fe:: FROM source NORTH SIDE
                        OUTPUT fe:: TO dest NORTH SIDE
{% endcase %}
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
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
                throw new GameTestAssertException("Induction matrix did not form");
{% when '26.1.2' %}
                throw new GameTestAssertException(Component.literal("Induction matrix did not form"), 0);
{% endcase %}
            }

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3' %}
            var expected = startingAmount.add(incr);
            FloatingLong energy = inductionPort.getEnergy(0);
            boolean success = energy.equals(expected);
{% when '1.20.4' %}
            var expected = startingAmount.add(SFMMekanismCompat.createForgeEnergy(incr));
            FloatingLong energy = inductionPort.getEnergy(0);
            boolean success = energy.equals(expected);
{% when '1.21', '1.21.1', '26.1.2' %}
            var expected = startingAmount + incr;
            long joules = inductionPort.getEnergy(0);
            long energy = UnitDisplayUtils.EnergyUnit.FORGE_ENERGY.convertTo(joules);
            boolean success = energy == expected;
{% endcase %}
            helper.assertTrue(
                    success,
                    "Expected energy did not match"
            );
        });
    }
}
