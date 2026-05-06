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
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
import net.neoforged.neoforge.transfer.resource.ResourceStack;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

import java.util.stream.Stream;

public class FluidResourceType extends RegistryBackedResourceType<ResourceStack<FluidResource>, Fluid, ResourceHandler<FluidResource>> {
    public FluidResourceType() {
        super(SFMWellKnownCapabilities.FLUID_HANDLER);
    }

    @Override
    public SFMRegistryWrapper<Fluid> getRegistry() {
        return SFMWellKnownRegistries.FLUIDS;
    }

    @Override
    public Fluid getItem(ResourceStack<FluidResource> fluidStack) {
        return fluidStack.resource().getFluid();
    }

    @Override
    public Stream<Identifier> getTagsForStack(Fluid fluidStack) {
        //noinspection deprecation
        return fluidStack.builtInRegistryHolder().tags().map(TagKey::location);
    }

    @Override
    public Fluid stackToItem(ResourceStack<FluidResource> fluidResourceResourceStack) {
        return fluidResourceResourceStack.resource().getFluid();
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
    public long getAmount(ResourceStack<FluidResource> stack) {
        return stack.amount();
    }

    @Override
    public ResourceStack<FluidResource> getStackInSlot(ResourceHandler<FluidResource> cap, int slot) {
        return new ResourceStack<>(cap.getResource(slot), cap.getAmountAsInt(slot));
    }

    @Override
    public ResourceStack<FluidResource> extract(
            ResourceHandler<FluidResource> handler,
            int slot,
            long amount_long,
            TransactionContext tx
    ) {
        try (var ctx = Transaction.open(tx)) {
            var in = getStackInSlot(handler, slot);

            int extracted = handler.extract(slot, in.resource(), (int) Mth.clamp(amount_long, 0, Integer.MAX_VALUE), ctx);
            ctx.commit();

            return new ResourceStack<>(in.resource(), extracted);
        }
    }

    @Override
    public boolean matchesStackType(Object o) {
        return o instanceof FluidStack;
    }

    @Override
    public boolean matchesCapabilityHandler(Object o) {
        return o instanceof ResourceHandler;
    }

    @Override
    public int getSlots(ResourceHandler<FluidResource> handler) {
        return handler.size();
    }

    @Override
    public long getMaxStackSize(ResourceStack<FluidResource> fluidStack) {
        return Integer.MAX_VALUE;
    }

    @Override
    public long getMaxStackSizeForSlot(ResourceHandler<FluidResource> iFluidHandler, int slot) {
        return iFluidHandler.getCapacityAsLong(slot, iFluidHandler.getResource(slot));
    }


    @Override
    public ResourceStack<FluidResource> insert(ResourceHandler<FluidResource> handler, int slot, ResourceStack<FluidResource> stack, TransactionContext tx) {
        try (var ctx = Transaction.openRoot()) {
            int inserted = handler.insert(slot, stack.resource(), stack.amount(), ctx);
            ctx.commit();

            return new ResourceStack<>(stack.resource(), stack.amount() - inserted);
        }
    }

    @Override
    public boolean isEmpty(ResourceStack<FluidResource> stack) {
        return stack.isEmpty();
    }
}
