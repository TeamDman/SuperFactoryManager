package ca.teamdman.sfm.common.blockentity;

import ca.teamdman.sfm.common.block.WaterTankBlock;
import ca.teamdman.sfm.common.block_network.BlockNetwork;
import ca.teamdman.sfm.common.block_network.WaterNetworkManager;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2' %}
import ca.teamdman.sfm.common.capability.SFMWellKnownCapabilities;
{% when '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
{% endcase %}
import ca.teamdman.sfm.common.registry.registration.SFMBlockEntities;
import net.minecraft.core.BlockPos;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2' %}
import net.minecraft.core.Direction;
{% when '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
{% endcase %}
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1' %}
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.Nullable;
{% when '1.20.2' %}
import net.neoforged.neoforge.common.capabilities.Capability;
import net.neoforged.neoforge.common.util.LazyOptional;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.Nullable;
{% when '1.20.3', '1.20.4', '1.21', '1.21.1' %}
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
{% when '26.1.2' %}
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
{% endcase %}

public class WaterTankBlockEntity extends BlockEntity {
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}

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
{% when '26.1.2' %}
    public static class WaterTankFluidHandler extends FluidStacksResourceHandler {
        public WaterTankFluidHandler() {
            super(1, 0);
{% endcase %}
        }
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2' %}
    };

    public final LazyOptional<IFluidHandler> tankCapability = LazyOptional.of(() -> TANK);

{% when '1.20.3', '1.20.4', '1.21', '1.21.1' %}
    };

{% when '26.1.2' %}

        public void setCapacity(int capacity) {
            this.capacity = capacity;
        }

        public int getCapacity() {
            return this.capacity;
        }

        @Override
        public boolean isValid(int index, FluidResource resource) {
            return false;
        }

        @Override
        public int insert(FluidResource resource, int amount, TransactionContext tx) {
            return 0;
        }

        @Override
        public int extract(FluidResource resource, int amount, TransactionContext tx) {
            return resource.equals(FluidResource.of(Fluids.WATER)) ? getAmountAsInt(0) : 0;
        }

        @Override
        public FluidResource getResource(int index) {
            return FluidResource.of(Fluids.WATER);
        }

        @Override
        public long getAmountAsLong(int index) {
            return this.capacity;
        }
    }

    public final WaterTankFluidHandler TANK = new WaterTankFluidHandler();

{% endcase %}
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
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2' %}
        updateTankContents();
{% when '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
        updateTank();
{% endcase %}
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

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2' %}
    @Override
    public <T> LazyOptional<T> getCapability(
            Capability<T> cap,
            @Nullable Direction side
    ) {

        if (cap == SFMWellKnownCapabilities.FLUID_HANDLER.capabilityKind()) {
            return tankCapability.cast();
        } else {
            return super.getCapability(cap, side);
        }
    }

    @Override
    public void invalidateCaps() {

        tankCapability.invalidate();
        super.invalidateCaps();
    }

    private void updateTankContents() {

        if (isActive()) {
            TANK.setFluid(new FluidStack(Fluids.WATER, TANK.getCapacity()));
        } else {
            TANK.setFluid(FluidStack.EMPTY);
{% when '1.20.3', '1.20.4', '1.21', '1.21.1' %}
    private void updateTank() {

        if (active) {
            TANK.setFluid(new FluidStack(Fluids.WATER, TANK.getCapacity()));
        } else {
            TANK.setFluid(FluidStack.EMPTY);
{% when '26.1.2' %}
    private void updateTank() {

        FluidResource water = FluidResource.of(Fluids.WATER);
        if (active) {
            TANK.set(0, water, TANK.getCapacityAsInt(0, water));
        } else {
            TANK.set(0, FluidResource.EMPTY, 0);
{% endcase %}
        }
    }

}
