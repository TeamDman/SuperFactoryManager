package ca.teamdman.sfm.common.resourcetype;

import ca.teamdman.sfm.common.block.BufferBlock;
import ca.teamdman.sfm.common.blockentity.BufferBlockEntityContents;
import ca.teamdman.sfm.common.capability.SFMWellKnownCapabilities;
import ca.teamdman.sfm.common.registry.SFMRegistryWrapper;
import ca.teamdman.sfm.common.registry.SFMWellKnownRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

import java.util.stream.Stream;

public class FluidResourceType extends RegistryBackedResourceType<FluidStack, Fluid, IFluidHandler> {
    public FluidResourceType() {
        super(SFMWellKnownCapabilities.FLUID_HANDLER);
    }

    @Override
    public SFMRegistryWrapper<Fluid> getRegistry() {
        return SFMWellKnownRegistries.FLUIDS;
    }

    @Override
    public Fluid getItem(FluidStack fluidStack) {
        return fluidStack.getFluid();
    }

    @Override
    public FluidStack copy(FluidStack fluidStack) {
        return fluidStack.copy();
    }

    @Override
    public Stream<ResourceLocation> getTagsForStack(FluidStack fluidStack) {
        //noinspection deprecation
        return fluidStack.getFluid().builtInRegistryHolder().tags().map(TagKey::location);
    }

    @Override
    protected FluidStack setCount(FluidStack fluidStack, long amount) {
        int finalAmount = amount > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) amount;
        fluidStack.setAmount(finalAmount);
        return fluidStack;
    }

    @Override
    public IFluidHandler createHandlerForBufferBlock(BufferBlockEntityContents contents) {
        return new FluidTank(contents.tier.getIntMaxStackSize()) {
            @Override
            public boolean isFluidValid(FluidStack stack) {
                boolean isValid = this.getFluidAmount() > 0 || contents.isEmpty();
                if (isValid) {
                    contents.lastUsedResource = BufferBlock.ContainedResource.Fluid;
                }
                return isValid;
            }
        };
    }

    @Override
    public long getAmount(FluidStack stack) {
        return stack.getAmount();
    }

    @Override
    public FluidStack getStackInSlot(IFluidHandler cap, int slot) {
        return cap.getFluidInTank(slot);
    }

    @Override
    public FluidStack extract(
            IFluidHandler handler,
            int slot,
            long amount_long,
            boolean simulate
    ) {
        var in = getStackInSlot(handler, slot);
        var toExtract = new FluidStack(
                in.getFluid(),
                (int) Mth.clamp(amount_long, Integer.MIN_VALUE, Integer.MAX_VALUE),
                in.getTag()
        );
        return handler.drain(
                toExtract,
                simulate ? IFluidHandler.FluidAction.SIMULATE : IFluidHandler.FluidAction.EXECUTE
        );
    }

    @Override
    public boolean matchesStackType(Object o) {
        return o instanceof FluidStack;
    }

    @Override
    public boolean matchesCapabilityHandler(Object o) {
        return o instanceof IFluidHandler;
    }

    @Override
    public int getSlots(IFluidHandler handler) {
        return handler.getTanks();
    }

    @Override
    public long getMaxStackSize(FluidStack fluidStack) {
        return Integer.MAX_VALUE;
    }

    @Override
    public long getMaxStackSizeForSlot(IFluidHandler iFluidHandler, int slot) {
        return iFluidHandler.getTankCapacity(slot);
    }

    @Override
    public FluidStack insert(IFluidHandler handler, int slot, FluidStack stack, boolean simulate) {
        // fluid handlers return the amount moved, not the remainder, so we have to convert
        var inserted = handler.fill(stack, simulate ? IFluidHandler.FluidAction.SIMULATE : IFluidHandler.FluidAction.EXECUTE);
        int remainder = stack.getAmount() - inserted;
        return new FluidStack(stack.getFluid(), remainder, stack.getTag());
    }

    @Override
    public boolean isEmpty(FluidStack stack) {
        return stack.isEmpty();
    }

    @Override
    public FluidStack getEmptyStack() {
        return FluidStack.EMPTY;
    }
}
