package ca.teamdman.sfm.common.resourcetype;

import ca.teamdman.sfm.common.block.BufferBlock;
import ca.teamdman.sfm.common.blockentity.BufferBlockEntityContents;
import ca.teamdman.sfm.common.capability.SFMWellKnownCapabilities;
import ca.teamdman.sfm.common.registry.SFMRegistryWrapper;
import ca.teamdman.sfm.common.registry.SFMWellKnownRegistries;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
import net.minecraft.resources.ResourceLocation;
{% when '26.1.2' %}
import net.minecraft.resources.Identifier;
{% endcase %}
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.material.Fluid;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1' %}
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
{% when '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
{% when '26.1.2' %}
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
{% endcase %}

import java.util.stream.Stream;

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
public class FluidResourceType extends RegistryBackedResourceType<FluidStack, Fluid, IFluidHandler> {
{% when '26.1.2' %}
public class FluidResourceType extends RegistryBackedResourceType<FluidStack, Fluid, ResourceHandler<FluidResource>> {
{% endcase %}
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
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
    public Stream<ResourceLocation> getTagsForStack(FluidStack fluidStack) {
{% when '26.1.2' %}
    public Stream<Identifier> getTagsForStack(FluidStack fluidStack) {
{% endcase %}
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
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
    public IFluidHandler createHandlerForBufferBlock(BufferBlockEntityContents contents) {
        return new FluidTank(contents.tier.getIntMaxStackSize()) {
{% when '26.1.2' %}
    public ResourceHandler<FluidResource> createHandlerForBufferBlock(BufferBlockEntityContents contents) {
        return new FluidStacksResourceHandler(1, contents.tier.getIntMaxStackSize()) {
{% endcase %}
            @Override
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
            public boolean isFluidValid(FluidStack stack) {
                boolean isValid = this.getFluidAmount() > 0 || contents.isEmpty();
{% when '26.1.2' %}
            public boolean isValid(int index, FluidResource resource) {
                boolean isValid = this.getAmountAsInt(index) > 0 || contents.isEmpty();
{% endcase %}
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
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
    public FluidStack getStackInSlot(IFluidHandler cap, int slot) {
        return cap.getFluidInTank(slot);
{% when '26.1.2' %}
    public FluidStack getStackInSlot(ResourceHandler<FluidResource> handler, int slot) {
        return IFluidHandler.of(handler).getFluidInTank(slot);
{% endcase %}
    }

    @Override
    public FluidStack extract(
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
            IFluidHandler handler,
{% when '26.1.2' %}
            ResourceHandler<FluidResource> _handler,
{% endcase %}
            int slot,
            long amount_long,
            boolean simulate
    ) {
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
        var in = getStackInSlot(handler, slot);
{% when '26.1.2' %}
        IFluidHandler handler = IFluidHandler.of(_handler);
        var in = getStackInSlot(_handler, slot);
{% endcase %}
        var toExtract = new FluidStack(
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '26.1.2' %}
                in.getFluid(),
{% when '1.21', '1.21.1' %}
                in.getFluidHolder(),
{% endcase %}
                (int) Mth.clamp(amount_long, Integer.MIN_VALUE, Integer.MAX_VALUE),
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
                in.getTag()
{% when '1.21', '1.21.1', '26.1.2' %}
                in.getComponentsPatch()
{% endcase %}
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
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
        return o instanceof IFluidHandler;
{% when '26.1.2' %}
        return o instanceof ResourceHandler<?>;
{% endcase %}
    }

    @Override
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
    public int getSlots(IFluidHandler handler) {
        return handler.getTanks();
{% when '26.1.2' %}
    public int getSlots(ResourceHandler<FluidResource> _handler) {
        return IFluidHandler.of(_handler).getTanks();
{% endcase %}
    }

    @Override
    public long getMaxStackSize(FluidStack fluidStack) {
        return Integer.MAX_VALUE;
    }

    @Override
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
    public long getMaxStackSizeForSlot(IFluidHandler iFluidHandler, int slot) {
        return iFluidHandler.getTankCapacity(slot);
{% when '26.1.2' %}
    public long getMaxStackSizeForSlot(ResourceHandler<FluidResource> _handler, int slot) {
        return IFluidHandler.of(_handler).getTankCapacity(slot);
{% endcase %}
    }

    @Override
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
    public FluidStack insert(IFluidHandler handler, int slot, FluidStack stack, boolean simulate) {
{% when '26.1.2' %}
    public FluidStack insert(ResourceHandler<FluidResource> _handler, int slot, FluidStack stack, boolean simulate) {
        IFluidHandler handler = IFluidHandler.of(_handler);
{% endcase %}
        // fluid handlers return the amount moved, not the remainder, so we have to convert
        var inserted = handler.fill(stack, simulate ? IFluidHandler.FluidAction.SIMULATE : IFluidHandler.FluidAction.EXECUTE);
        int remainder = stack.getAmount() - inserted;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
        return new FluidStack(stack.getFluid(), remainder, stack.getTag());
{% when '1.21', '1.21.1' %}
        return new FluidStack(stack.getFluidHolder(), remainder, stack.getComponentsPatch());
{% when '26.1.2' %}
        return new FluidStack(stack.getFluid(), remainder, stack.getComponentsPatch());
{% endcase %}
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
