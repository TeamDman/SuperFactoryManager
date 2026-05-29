package ca.teamdman.sfm.common.resourcetype;

import ca.teamdman.sfm.common.block.BufferBlock;
import ca.teamdman.sfm.common.blockentity.BufferBlockEntityContents;
import ca.teamdman.sfm.common.capability.SFMWellKnownCapabilities;
import ca.teamdman.sfm.common.registry.SFMRegistryWrapper;
import ca.teamdman.sfm.common.registry.SFMWellKnownRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;

import java.util.stream.Stream;

public class FluidResourceType extends RegistryBackedResourceType<FluidStack, Fluid, ResourceHandler<FluidResource>> {
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
    public Stream<Identifier> getTagsForStack(FluidStack fluidStack) {
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
    public ResourceHandler<FluidResource> createHandlerForBufferBlock(BufferBlockEntityContents contents) {
        return new FluidStacksResourceHandler(1, contents.tier.getIntMaxStackSize()) {
            @Override
            public boolean isValid(int index, FluidResource resource) {
                boolean isValid = this.getAmountAsInt(index) > 0 || contents.isEmpty();
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
    public FluidStack getStackInSlot(ResourceHandler<FluidResource> handler, int slot) {
        return IFluidHandler.of(handler).getFluidInTank(slot);
    }

    @Override
    public FluidStack extract(
            ResourceHandler<FluidResource> _handler,
            int slot,
            long amount_long,
            boolean simulate
    ) {
        IFluidHandler handler = IFluidHandler.of(_handler);
        var in = getStackInSlot(_handler, slot);
        var toExtract = new FluidStack(
                in.getFluid(),
                (int) Mth.clamp(amount_long, Integer.MIN_VALUE, Integer.MAX_VALUE),
                in.getComponentsPatch()
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
    public int getSlots(ResourceHandler<FluidResource> _handler) {
        return IFluidHandler.of(_handler).getTanks();
    }

    @Override
    public long getMaxStackSize(FluidStack fluidStack) {
        return Integer.MAX_VALUE;
    }

    @Override
    public long getMaxStackSizeForSlot(ResourceHandler<FluidResource> _handler, int slot) {
        return IFluidHandler.of(_handler).getTankCapacity(slot);
    }

    @Override
    public FluidStack insert(ResourceHandler<FluidResource> _handler, int slot, FluidStack stack, boolean simulate) {
        IFluidHandler handler = IFluidHandler.of(_handler);
        // fluid handlers return the amount moved, not the remainder, so we have to convert
        var inserted = handler.fill(stack, simulate ? IFluidHandler.FluidAction.SIMULATE : IFluidHandler.FluidAction.EXECUTE);
        int remainder = stack.getAmount() - inserted;
        return new FluidStack(stack.getFluid(), remainder, stack.getComponentsPatch());
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
