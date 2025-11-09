package ca.teamdman.sfm.common.resourcetype;

import ca.teamdman.sfm.common.block.BufferBlock;
import ca.teamdman.sfm.common.blockentity.BufferBlockEntityContents;
import ca.teamdman.sfm.common.capability.SFMWellKnownCapabilities;
import ca.teamdman.sfm.common.resourcetype.ResourceTypeContainer.ResourceType;
import ca.teamdman.sfm.common.util.Mth;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Set;
import java.util.stream.Stream;

public class FluidResourceType extends ResourceType<FluidStack, Fluid, IFluidHandler> {
    public FluidResourceType(ResourceTypeContainer container) {
        super(container, SFMWellKnownCapabilities.FLUID_HANDLER);
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
        return Stream.empty();
        //noinspection deprecation
    }

    @Override
    public boolean registryKeyExists(ResourceLocation location) {
        return FluidRegistry.isFluidRegistered(location.toString());
    }

    @Override
    public ResourceLocation getRegistryKeyForStack(FluidStack fluidStack) {
        return getRegistryKeyForItem(getItem(fluidStack));
    }

    @Override
    public ResourceLocation getRegistryKeyForItem(Fluid fluid) {
        return new ResourceLocation(FluidRegistry.getDefaultFluidName(fluid));
    }

    @Nullable
    @Override
    public Fluid getItemFromRegistryKey(ResourceLocation location) {
        return FluidRegistry.getFluid(location.getPath());
    }

    @Override
    public Set<ResourceLocation> getRegistryKeys() {
        return Collections.emptySet();
    }

    @Override
    public Iterable<Fluid> getItems() {
        return FluidRegistry.getRegisteredFluids().values();
    }

    @Override
    protected FluidStack setCount(FluidStack fluidStack, long amount) {
        int finalAmount = amount > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) amount;
        fluidStack.amount = finalAmount;
        return fluidStack;
    }

    @Override
    public IFluidHandler createHandlerForBufferBlock(BufferBlockEntityContents contents) {
        return new FluidTank(contents.tier.getIntMaxStackSize()) {
            @Override
            public int fillInternal(FluidStack resource, boolean doFill) {
                int ret = super.fillInternal(resource, doFill);
                if (ret > 0) {
                    contents.lastUsedResource = BufferBlock.ContainedResource.Fluid;
                }
                return ret;
            }
        };
    }

    @Override
    public long getAmount(FluidStack stack) {
        return stack.amount;
    }

    @Override
    public FluidStack getStackInSlot(IFluidHandler cap, int slot) {
        return cap.getTankProperties()[slot].getContents();
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
                in.tag
        );
        return handler.drain(toExtract, !simulate);
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
        return handler.getTankProperties().length;
    }

    @Override
    public long getMaxStackSize(FluidStack fluidStack) {
        return Integer.MAX_VALUE;
    }

    @Override
    public long getMaxStackSizeForSlot(IFluidHandler iFluidHandler, int slot) {
        return iFluidHandler.getTankProperties()[slot].getCapacity();
    }

    @Override
    public FluidStack insert(IFluidHandler handler, int slot, FluidStack stack, boolean simulate) {
        // fluid handlers return the amount moved, not the remainder, so we have to convert
        var inserted = handler.fill(stack, !simulate);
        int remainder = stack.amount - inserted;
        return new FluidStack(stack.getFluid(), remainder, stack.tag);
    }

    @Override
    public boolean isEmpty(FluidStack stack) {
        return stack != null ? stack.amount == 0 : true;
    }

    @Override
    public FluidStack getEmptyStack() {
        return null;
    }
}
