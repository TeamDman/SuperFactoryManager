package ca.teamdman.sfm.common.blockentity;

import ca.teamdman.sfm.common.block.WaterTankBlock;
import ca.teamdman.sfm.common.block_network.BlockNetwork;
import ca.teamdman.sfm.common.block_network.WaterNetworkManager;
import ca.teamdman.sfm.common.registry.registration.SFMBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public class WaterTankBlockEntity extends BlockEntity {

    public final FluidTank TANK = new FluidTank(
            0,
            fluidStack -> false // The tank cannot be filled, only drained.
    ) {
        @Override
        public FluidStack drain(
                int maxDrain,
                FluidAction action
        ) {

            // Return empty if inactive
            if (getFluidAmount() == 0) return FluidStack.EMPTY;

            // Return fluid stack without draining the tank
            int drained = Math.min(maxDrain, getFluidAmount());
            FluidStack copy = getFluid().copy();
            copy.setAmount(drained);
            return copy;
        }
    };
    private boolean active = false;


    public WaterTankBlockEntity(
            BlockPos pos,
            BlockState state
    ) {

        super(SFMBlockEntities.WATER_TANK.get(), pos, state);
    }

    public void updateActiveFromBlockState() {

        updateActiveFromBlockState(getBlockState());
    }

    public void updateActiveFromBlockState(BlockState blockState) {

        this.active = isActiveFromBlockState(blockState);
    }

    public boolean isActiveFromBlockState(BlockState blockState) {

        return blockState.getOptionalValue(WaterTankBlock.IN_WATER).orElse(false);
    }

    /// The capacity of the tank is determined by the count of members in the [BlockNetwork]
    public void updateTankCapacity(int activeMemberCount) {

        int newCapacity;
        if (activeMemberCount == 0) {
            // Make the tank empty
            newCapacity = 0;
        } else {
            // Update the capacity using $ 2^(n-1) $
            newCapacity = (int) Math.pow(2, activeMemberCount - 1) * 1000;
        }

        // Handle integer overflows
        if (newCapacity < 0) newCapacity = Integer.MAX_VALUE;

        // Update the tank capacity
        TANK.setCapacity(newCapacity);

        // Update the tank contents
        updateTank();
    }

    /// The [WaterTankBlock] handles updating the block state according to neighbouring water sources.
    public boolean isActive() {

        return active;
    }

    @Override
    public void onLoad() {

        super.onLoad();
        WaterNetworkManager.onLoad(this);
    }

    private void updateTank() {
        if (active) {
            TANK.setFluid(new FluidStack(Fluids.WATER, TANK.getCapacity()));
        } else {
            TANK.setFluid(FluidStack.EMPTY);
        }
    }

}
