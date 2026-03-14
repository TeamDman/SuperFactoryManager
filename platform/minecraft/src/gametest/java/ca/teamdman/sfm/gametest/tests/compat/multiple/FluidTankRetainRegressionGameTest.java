package ca.teamdman.sfm.gametest.tests.compat.multiple;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import static ca.teamdman.sfm.common.registry.SFMBlocks.MANAGER_BLOCK;
import static ca.teamdman.sfm.common.registry.SFMItems.DISK_ITEM;
import static ca.teamdman.sfm.gametest.SFMGameTestMethodHelpers.assertTrue;
import static com.buuz135.industrial.module.ModuleCore.LATEX;
import static com.buuz135.industrial.module.ModuleCore.LATEX_PROCESSING;
import static mekanism.common.registries.MekanismBlocks.BASIC_FLUID_TANK;
import static net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;

@SuppressWarnings({
        "RedundantSuppression",
        "DataFlowIssue",
        "OptionalGetWithoutIsPresent",
        "DuplicatedCode",
        "ArraysAsListWithZeroOrOneArgument"
})
@SFMGameTest
public class FluidTankRetainRegressionGameTest extends SFMGameTestDefinition {

    @Override
    public String template() {
        return "3x2x1";
    }


    @Override
    public void run(SFMGameTestHelper helper) {
        // Positions: A (tank), B (manager), C (processing unit)
        BlockPos tankPos = new BlockPos(0, 2, 0);
        BlockPos managerPos = new BlockPos(1, 2, 0);
        BlockPos machinePos = new BlockPos(2, 2, 0);

        // Place Mekanism basic fluid tank at A
        helper.setBlock(tankPos, BASIC_FLUID_TANK.getBlock());
        var tank = helper.getFluidHandler(tankPos, Direction.UP);

        // Place manager at B
        helper.setBlock(managerPos, MANAGER_BLOCK.get());
        ManagerBlockEntity manager = (ManagerBlockEntity) helper.getBlockEntity(managerPos);
        manager.setItem(0, new ItemStack(DISK_ITEM.get()));

        // Place Industrial Foregoing latex processing unit at C
        helper.setBlock(machinePos, LATEX_PROCESSING.getKey().get());
        var machine = helper.getFluidHandler(machinePos, Direction.UP);

        // Fill tank with latex (simulate 40,000 mB, retain 30,000)
        FluidStack latexStack = new FluidStack(LATEX.getSourceFluid().get(), 32000);
        assertTrue(tank.fill(latexStack, EXECUTE) == 32000, "Tank failed to accept all latex");

        // Set program
        manager.setProgram("""
                                   EVERY 20 TICKS DO
                                     INPUT RETAIN 30000 fluid::latex FROM tank BOTTOM SIDE
                                     OUTPUT fluid:: TO processing TOP SIDE
                                   END
                                   """.stripTrailing().stripIndent());

        // Set labels
        LabelPositionHolder.empty()
                .add("tank", helper.absolutePos(tankPos))
                .add("processing", helper.absolutePos(machinePos))
                .save(manager.getDisk());

        // Success check
        helper.succeedIfManagerDidThingWithoutLagging(manager, () -> {
            assertTrue(
                    tank.getFluidInTank(0).getAmount() == 30000,
                    "Tank did not retain 30,000 mB latex"
            );
            assertTrue(
                    machine.getFluidInTank(0).getAmount() == 2000,
                    "Processing unit did not receive 2,000 latex"
            );
        });
    }
}
