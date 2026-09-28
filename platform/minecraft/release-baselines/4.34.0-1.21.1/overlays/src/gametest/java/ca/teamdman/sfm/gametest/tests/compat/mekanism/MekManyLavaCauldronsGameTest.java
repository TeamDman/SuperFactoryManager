package ca.teamdman.sfm.gametest.tests.compat.mekanism;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.capability.SFMWellKnownCapabilities;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import ca.teamdman.sfm.common.registry.registration.SFMItems;
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import mekanism.common.registries.MekanismBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.Objects;




/**
 * Migrated from SFMMekanismCompatGameTests.many_lava_cauldrons
 */
@SuppressWarnings({
        "RedundantSuppression",
        "DataFlowIssue",
        "OptionalGetWithoutIsPresent",
        "DuplicatedCode",
        "ArraysAsListWithZeroOrOneArgument"
})
@SFMGameTest
public class MekManyLavaCauldronsGameTest extends SFMGameTestDefinition {

    @Override
    public String template() {
        return "25x3x25";
    }


    @Override
    public void run(SFMGameTestHelper helper) {
        // designate positions
        var sourceBlocks = new ArrayList<BlockPos>();
        var destBlocks = new ArrayList<BlockPos>();
        var managerPos = new BlockPos(0, 2, 0);

        // set up cauldrons
        for (int x = 0; x < 25; x++) {
            for (int z = 1; z < 25; z++) {
                helper.setBlock(new BlockPos(x, 2, z), SFMBlocks.CABLE.get());
                helper.setBlock(new BlockPos(x, 3, z), Blocks.LAVA_CAULDRON);
                sourceBlocks.add(new BlockPos(x, 3, z));
            }
        }

        // set up tanks
        for (int i = 1; i < 25; i++) {
            BlockPos tankPos = new BlockPos(i, 2, 0);
            helper.setBlock(tankPos, MekanismBlocks.BASIC_FLUID_TANK.getBlock());
            destBlocks.add(tankPos);
        }

        // set up the manager
        helper.setBlock(managerPos, SFMBlocks.MANAGER.get());
        ManagerBlockEntity manager = helper.getBlockEntity(managerPos, ManagerBlockEntity.class);
        manager.setItem(0, new ItemStack(SFMItems.DISK.get()));

        // create the program
        var program = """
                    NAME "many inventory lag test"
                                
                    EVERY 20 TICKS DO
                        INPUT fluid:*:* FROM source
                        OUTPUT fluid:*:* TO dest TOP SIDE
                    END
                """;

        // set the labels
        LabelPositionHolder.empty()
                .addAll("source", sourceBlocks.stream().map(helper::absolutePos).toList())
                .addAll("dest", destBlocks.stream().map(helper::absolutePos).toList())
                .save(manager.getDisk());

        // load the program
        manager.setProgram(program);
        helper.succeedIfManagerDidThingWithoutLagging(manager, () -> {
            sourceBlocks.forEach(pos -> helper.assertBlock(
                    pos,
                    Blocks.CAULDRON::equals,
                    () -> "Cauldron did not empty"
            ));
            int found = destBlocks
                    .stream()
                    .map(helper::absolutePos)
                    .map(pos -> helper.getLevel().getCapability(SFMWellKnownCapabilities.FLUID_HANDLER.capabilityKind(), pos, Direction.DOWN))
                    .peek(Objects::requireNonNull)
                    .map(x -> x.getFluidInTank(0))
                    .mapToInt(FluidStack::getAmount)
                    .sum();
            helper.assertTrue(found == 1000 * 25 * 24, "Not all fluids were moved (found " + found + ")");


        });
    }
}
