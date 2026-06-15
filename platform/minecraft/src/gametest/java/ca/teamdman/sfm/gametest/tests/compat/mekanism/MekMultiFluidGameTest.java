package ca.teamdman.sfm.gametest.tests.compat.mekanism;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
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
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;



/**
 * Migrated from SFMMekanismCompatGameTests.multi_fluid
 */
@SuppressWarnings({
        "RedundantSuppression",
        "DataFlowIssue",
        "OptionalGetWithoutIsPresent",
        "DuplicatedCode",
        "ArraysAsListWithZeroOrOneArgument"
})
@SFMGameTest
public class MekMultiFluidGameTest extends SFMGameTestDefinition {

    @Override
    public String template() {
        return "3x4x3";
    }


    @Override
    public void run(SFMGameTestHelper helper) {
        var a1Pos = new BlockPos(2, 1, 1);
        var a2Pos = new BlockPos(1, 1, 0);
        var b1Pos = new BlockPos(1, 1, 2);
        var b2Pos = new BlockPos(0, 1, 1);
        var managerPos = new BlockPos(1, 1, 1);
        helper.setBlock(a1Pos, MekanismBlocks.BASIC_FLUID_TANK.get());
        helper.setBlock(a2Pos, MekanismBlocks.BASIC_FLUID_TANK.get());
        helper.setBlock(b1Pos, MekanismBlocks.BASIC_FLUID_TANK.get());
        helper.setBlock(b2Pos, MekanismBlocks.BASIC_FLUID_TANK.get());
        var a1 = helper.getFluidHandler(a1Pos, Direction.NORTH);
        var a2 = helper.getFluidHandler(a2Pos, Direction.NORTH);
        var b1 = helper.getFluidHandler(b1Pos, Direction.NORTH);
        var b2 = helper.getFluidHandler(b2Pos, Direction.NORTH);

        a1.fill(new FluidStack(Fluids.WATER, 3000), IFluidHandler.FluidAction.EXECUTE);
        a2.fill(new FluidStack(Fluids.LAVA, 3000), IFluidHandler.FluidAction.EXECUTE);

        helper.setBlock(managerPos, SFMBlocks.MANAGER.get());
        var manager = helper.getBlockEntity(managerPos, ManagerBlockEntity.class);
        manager.setItem(0, new ItemStack(SFMItems.DISK.get()));
        manager.setProgram("""
                                   EVERY 20 TICKS DO
                                     INPUT fluid:: FROM a NORTH SIDE
                                     OUTPUT fluid::lava, fluid::water TO b TOP SIDE
                                   END
                                   """.stripIndent());

        // set the labels
        LabelPositionHolder.empty()
                .add("a", helper.absolutePos(a1Pos))
                .add("a", helper.absolutePos(a2Pos))
                .add("b", helper.absolutePos(b1Pos))
                .add("b", helper.absolutePos(b2Pos))
                .save(manager.getDisk());

        helper.succeedIfManagerDidThingWithoutLagging(
                manager, () -> {
                    helper.assertTrue(a1.getFluidInTank(0).isEmpty(), "a1 did not empty");
                    helper.assertTrue(a2.getFluidInTank(0).isEmpty(), "a2 did not empty");
                    helper.assertTrue(
                            (
                                    (b1.getFluidInTank(0).getFluid() == Fluids.WATER)
                                    && (b2.getFluidInTank(0).getFluid() == Fluids.LAVA)
                            ) ||
                            (
                                    (b1.getFluidInTank(0).getFluid() == Fluids.LAVA)
                                    && (b2.getFluidInTank(0).getFluid() == Fluids.WATER)
                            ),
                            "b1 and b2 did not fill with water and lava"
                    );
                }
        );
    }
}
