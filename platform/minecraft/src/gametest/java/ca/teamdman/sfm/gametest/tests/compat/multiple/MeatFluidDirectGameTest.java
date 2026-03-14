package ca.teamdman.sfm.gametest.tests.compat.multiple;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.capability.SFMWellKnownCapabilities;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.registry.SFMBlocks;
import ca.teamdman.sfm.common.registry.SFMItems;
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import com.buuz135.industrial.block.resourceproduction.tile.WashingFactoryTile;
import com.buuz135.industrial.module.ModuleCore;
import com.buuz135.industrial.module.ModuleResourceProduction;
import mekanism.api.fluid.IExtendedFluidTank;
import mekanism.common.registries.MekanismBlocks;
import mekanism.common.tile.TileEntityFluidTank;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

import java.util.Objects;

/**
 * Migrated from SFMIndustrialForegoingMekanismGameTests.meat_fluid_direct
 */
@SuppressWarnings({
        "RedundantSuppression",
        "DataFlowIssue",
        "OptionalGetWithoutIsPresent",
        "DuplicatedCode",
        "ArraysAsListWithZeroOrOneArgument"
})
@SFMGameTest
public class MeatFluidDirectGameTest extends SFMGameTestDefinition {

    @Override
    public String template() {
        return "3x4x3";
    }

    @Override
    public int maxTicks() {
        return 200;
    }

    @Override
    public void run(SFMGameTestHelper helper) {
        BlockPos managerPos = new BlockPos(1, 2, 1);
        BlockPos rightTankPos = new BlockPos(0, 2, 1);
        BlockPos washingFactoryPos = new BlockPos(2, 2, 1);

        // set up the tanks
        helper.setBlock(rightTankPos, MekanismBlocks.BASIC_FLUID_TANK.getBlock());
        TileEntityFluidTank rightTank = (TileEntityFluidTank) helper.getBlockEntity(rightTankPos);
        assert rightTank != null;
        IExtendedFluidTank fluidTank = rightTank.getFluidTank(0, Direction.DOWN);
        assert fluidTank != null;

        // set up the washing factory
        helper.setBlock(washingFactoryPos, ModuleResourceProduction.WASHING_FACTORY.getLeft().get());
        WashingFactoryTile washingFactory = (WashingFactoryTile) helper.getBlockEntity(washingFactoryPos);
        assert washingFactory != null;

        // add some power
        washingFactory
                .getCapability(SFMWellKnownCapabilities.ENERGY.capabilityKind())
                .resolve()
                .get().receiveEnergy(Integer.MAX_VALUE, false);

        // add some meat
        washingFactory.getCapability(SFMWellKnownCapabilities.FLUID_HANDLER.capabilityKind())
                .resolve().get().fill(
                        new FluidStack(ModuleCore.MEAT.getSourceFluid().get(), Integer.MAX_VALUE),
                        IFluidHandler.FluidAction.EXECUTE
                );

        // add some iron
        washingFactory
                .getCapability(SFMWellKnownCapabilities.ITEM_HANDLER.capabilityKind(), Direction.UP)
                .resolve()
                .get()
                .insertItem(0, new ItemStack(Items.RAW_IRON, 64), false);

        // place the manager
        helper.setBlock(managerPos, SFMBlocks.MANAGER_BLOCK.get());
        ManagerBlockEntity manager = ((ManagerBlockEntity) helper.getBlockEntity(managerPos));
        assert manager != null;
        manager.setItem(0, new ItemStack(SFMItems.DISK_ITEM.get()));
        manager.setProgram("""
                                   NAME "bruh"
                                   EVERY 20 TICKS DO
                                    INPUT fluid:: FROM a BOTTOM SIDE
                                    OUTPUT fluid:: TO b TOP SIDE
                                   END
                                   """.stripTrailing().stripIndent());
        LabelPositionHolder.empty()
                .add("a", helper.absolutePos(washingFactoryPos))
                .add("b", helper.absolutePos(rightTankPos))
                .save(Objects.requireNonNull(manager.getDisk()));

        helper.succeedWhen(() -> {
            if (fluidTank.getFluidAmount() < 100) {
                helper.fail("Fluid tank did not receive fluid");
            }
        });
    }
}
