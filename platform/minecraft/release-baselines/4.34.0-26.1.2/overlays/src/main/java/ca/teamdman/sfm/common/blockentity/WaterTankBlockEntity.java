package ca.teamdman.sfm.common.blockentity;

import ca.teamdman.sfm.common.block.WaterTankBlock;
import ca.teamdman.sfm.common.block_network.BlockNetwork;
import ca.teamdman.sfm.common.block_network.WaterNetworkManager;
import ca.teamdman.sfm.common.registry.registration.SFMBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

public class WaterTankBlockEntity extends BlockEntity {
    public static class WaterTankFluidHandler extends FluidStacksResourceHandler {
        public WaterTankFluidHandler() {
            super(1, 0);
        }

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
        FluidResource water = FluidResource.of(Fluids.WATER);
        if (active) {
            TANK.set(0, water, TANK.getCapacityAsInt(0, water));
        } else {
            TANK.set(0, FluidResource.EMPTY, 0);
        }
    }

}
