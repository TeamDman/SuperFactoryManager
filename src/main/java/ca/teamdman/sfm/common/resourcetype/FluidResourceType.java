package ca.teamdman.sfm.common.resourcetype;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fluids.capability.IFluidHandler;

import org.jetbrains.annotations.Nullable;

import ca.teamdman.sfm.common.block.BufferBlock;
import ca.teamdman.sfm.common.blockentity.BufferBlockEntityContents;
import ca.teamdman.sfm.common.capability.SFMWellKnownCapabilities;
import ca.teamdman.sfm.common.resourcetype.ResourceTypeContainer.ResourceType;
import ca.teamdman.sfm.common.util.Mth;

public class FluidResourceType extends ResourceType<Optional<FluidStack>, Optional<Fluid>, IFluidHandler> {
    public FluidResourceType(ResourceTypeContainer container) {
        super(container, SFMWellKnownCapabilities.FLUID_HANDLER);
    }

    @Override
    public Optional<Fluid> getItem(Optional<FluidStack> fluidStack) {
        return fluidStack.map(FluidStack::getFluid);
    }

    @Override
    public Optional<FluidStack> copy(Optional<FluidStack> fluidStack) {
        return fluidStack.map(FluidStack::copy);
    }

    @Override
    public Stream<ResourceLocation> getTagsForStack(Optional<FluidStack> fluidStack) {
        return Stream.empty();
        //noinspection deprecation
    }

    @Override
    public boolean registryKeyExists(ResourceLocation location) {
        return FluidRegistry.isFluidRegistered(location.getPath());
    }

    @Override
    public ResourceLocation getRegistryKeyForStack(Optional<FluidStack> fluidStack) {
        return getRegistryKeyForItem(getItem(fluidStack));
    }

    @Override
    public ResourceLocation getRegistryKeyForItem(Optional<Fluid> fluid) {
        return fluid.map((f) -> new ResourceLocation(FluidRegistry.getDefaultFluidName(f)))
                .orElse(new ResourceLocation("ERROR_FLUID"));
    }

    @Nullable
    @Override
    public Optional<Fluid> getItemFromRegistryKey(ResourceLocation location) {
        return Optional.of(FluidRegistry.getFluid(location.getPath()));
    }

    @Override
    public Set<ResourceLocation> getRegistryKeys() {
        return Collections.emptySet();
    }

    @Override
    public Iterable<Optional<Fluid>> getItems() {
        return FluidRegistry.getRegisteredFluids().values().stream().map(Optional::of).collect(Collectors.toList());
    }

    @Override
    protected Optional<FluidStack> setCount(Optional<FluidStack> fluidStack, long amount) {
        int finalAmount = amount > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) amount;
        return fluidStack.map(stack -> {
            stack.amount = finalAmount;
            return stack;
        });
    }

    @Override
    public Optional<Long> getMetaForStack(Optional<FluidStack> fluidStack) {
        return Optional.empty();
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
    public long getAmount(Optional<FluidStack> stack) {
        return stack.map(s -> s.amount).orElse(0);
    }

    @Override
    public Optional<FluidStack> getStackInSlot(IFluidHandler cap, int slot) {
        var stack = cap.getTankProperties()[slot].getContents();
        return Optional.ofNullable(stack);
    }

    @Override
    public Optional<FluidStack> extract(
                                        IFluidHandler handler,
                                        int slot,
                                        long amount_long,
                                        boolean simulate
    ) {
        var optional = getStackInSlot(handler, slot);
        if (!optional.isPresent()) {
            return optional;
        }
        var in = optional.get();
        var toExtract = new FluidStack(
                in.getFluid(),
                (int) Mth.clamp(amount_long, Integer.MIN_VALUE, Integer.MAX_VALUE),
                in.tag
        );
        return Optional.ofNullable(handler.drain(toExtract, !simulate));
    }

    @Override
    public boolean matchesStackType(Object o) {
        return o instanceof Optional<?>op && (op.isPresent() && (op.get() instanceof FluidStack));
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
    public long getMaxStackSize(Optional<FluidStack> fluidStack) {
        return Integer.MAX_VALUE;
    }

    @Override
    public long getMaxStackSizeForSlot(IFluidHandler iFluidHandler, int slot) {
        return iFluidHandler.getTankProperties()[slot].getCapacity();
    }

    @Override
    public Optional<FluidStack> insert(IFluidHandler handler, int slot, Optional<FluidStack> stack, boolean simulate) {
        return stack.map(
                theStack -> {
                    var inserted = handler.fill(theStack, !simulate);
                    int remainder = theStack.amount - inserted;
                    return new FluidStack(theStack.getFluid(), remainder, theStack.tag);
                });
        // fluid handlers return the amount moved, not the remainder, so we have to convert
    }

    @Override
    public boolean isEmpty(Optional<FluidStack> stack) {
        return stack.map(s -> s.amount <= 0).orElse(true);
    }

    @Override
    public Optional<FluidStack> getEmptyStack() {
        return Optional.empty();
    }
}
