package ca.teamdman.sfm.common.blockentity;

import ca.teamdman.sfm.common.block.WaterTankBlock;
import ca.teamdman.sfm.common.capability.SFMWellKnownCapabilities;
import ca.teamdman.sfm.common.registry.SFMBlockEntities;
import ca.teamdman.sfm.common.watertanknetwork.WaterNetworkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class WaterTankBlockEntity extends BlockEntity {
    // can't fill, only extract
    public final FluidTank TANK = new FluidTank(1000, fluidStack -> false) {
        @Override
        public @NotNull FluidStack drain(
                int maxDrain,
                FluidAction action
        ) {
            if (getFluidAmount() == 0) return FluidStack.EMPTY; // Return empty if inactive
            int drained = Math.min(maxDrain, getFluidAmount());
            FluidStack copy = getFluid().copy();
            copy.setAmount(drained);
            return copy;
        }
    };
    public final LazyOptional<IFluidHandler> TANK_CAPABILITY = LazyOptional.of(() -> TANK);
    private boolean active = false;

    public WaterTankBlockEntity(
            BlockPos pos,
            BlockState state
    ) {
        super(SFMBlockEntities.WATER_TANK_BLOCK_ENTITY.get(), pos, state);
        setActive(state.getOptionalValue(WaterTankBlock.IN_WATER).orElse(false));
    }

    public void setConnectedCount(int connectedCount) {
        int newCapacity = (int) Math.pow(2, connectedCount-1) * 1000;
        if (newCapacity < 0) newCapacity = Integer.MAX_VALUE;
        TANK.setCapacity(newCapacity);
        updateTank();
    }

    public void setActive(boolean active) {
        this.active = active;
        updateTank();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        WaterNetworkManager.onLoad(this);
    }

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(
            @NotNull Capability<T> cap,
            @Nullable Direction side
    ) {
        if (cap == SFMWellKnownCapabilities.FLUID_HANDLER.capabilityKind()) {
            return TANK_CAPABILITY.cast();
        } else {
            return super.getCapability(cap, side);
        }
    }

    @Override
    public void invalidateCaps() {
        TANK_CAPABILITY.invalidate();
        super.invalidateCaps();
    }

    private void updateTank() {
        if (active) {
            TANK.setFluid(new FluidStack(Fluids.WATER, TANK.getCapacity()));
        } else {
            TANK.setFluid(FluidStack.EMPTY);
        }
    }
}
