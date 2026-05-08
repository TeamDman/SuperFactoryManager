package ca.teamdman.sfm.common.resourcetype;

import ca.teamdman.sfm.common.blockentity.BufferBlockEntityContents;
import ca.teamdman.sfm.common.capability.SFMBlockCapabilityKind;
import ca.teamdman.sfm.common.registry.SFMRegistryWrapper;
import mekanism.api.MekanismAPI;
import mekanism.api.chemical.*;
import mekanism.common.capabilities.Capabilities;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.resource.ResourceStack;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

import java.util.stream.Stream;

public class ChemicalResourceType extends RegistryBackedResourceType<ResourceStack<ChemicalResource>, Chemical, ResourceHandler<ChemicalResource>> {
    public static final SFMBlockCapabilityKind<ResourceHandler<ChemicalResource>> CAP = new SFMBlockCapabilityKind<>(
            Capabilities.CHEMICAL.block()
    );

    public ChemicalResourceType() {
        super(CAP);
    }

    @Override
    public ResourceHandler<ChemicalResource> createHandlerForBufferBlock(BufferBlockEntityContents contents) {
        return BasicChemicalTank.create(
                contents.tier.getLongScalarMaxStackSize(),
                null
        );
    }

    @Override
    public long getAmount(ResourceStack<ChemicalResource> gasStack) {
        return gasStack.amount();
    }

    @Override
    public ResourceStack<ChemicalResource> getStackInSlot(
            ResourceHandler<ChemicalResource> iChemicalHandler,
            int slot
    ) {
        return new ResourceStack<>(iChemicalHandler.getResource(slot), iChemicalHandler.getAmountAsInt(slot));
    }

    @Override
    public Stream<Identifier> getTagsForStack(Chemical gasStack) {
//        //noinspected depreciation
//        return gasStack.getTags().map(TagKey::location);
        // TODO: Apparently Mekanism stores tags on a ChemicalStack
        return Stream.empty();
    }

    @Override
    public Chemical stackToItem(ResourceStack<ChemicalResource> chemicalResourceResourceStack) {
        return chemicalResourceResourceStack.resource().getChemical();
    }

    @Override
    public ResourceStack<ChemicalResource> extract(
            ResourceHandler<ChemicalResource> handler,
            int slot,
            long amount,
            TransactionContext tx
    ) {
        try (var ctx = Transaction.open(tx)) {
            ChemicalResource resource = handler.getResource(slot);
            int extracted = handler.extract(slot, resource, (int)amount, ctx);
            ctx.commit();

            return new ResourceStack<>(resource, extracted);
        }
    }

    @Override
    public int getSlots(ResourceHandler<ChemicalResource> handler) {
        return handler.size();
    }

    @Override
    public long getMaxStackSize(ResourceStack<ChemicalResource> gasStack) {
        return Long.MAX_VALUE;
    }

    @Override
    public long getMaxStackSizeForSlot(
            ResourceHandler<ChemicalResource> handler,
            int slot
    ) {
        return handler.getCapacityAsLong(slot, handler.getResource(slot));
    }

    @Override
    public ResourceStack<ChemicalResource> insert(
            ResourceHandler<ChemicalResource> handler,
            int slot,
            ResourceStack<ChemicalResource> gasStack,
            TransactionContext tx
    ) {
        try (var ctx = Transaction.open(tx)) {
            ChemicalResource resource = handler.getResource(slot);
            int amount = gasStack.amount();
            int inserted = handler.extract(slot, resource, amount, ctx);
            ctx.commit();

            return new ResourceStack<>(resource, amount - inserted);
        }
    }

    @Override
    public boolean isEmpty(ResourceStack<ChemicalResource> gasStack) {
        return gasStack.isEmpty();
    }

    @Override
    public boolean matchesStackType(Object o) {
        return o instanceof ChemicalStack;
    }

    @Override
    public boolean matchesCapabilityHandler(Object o) {
        return o instanceof IChemicalHandler;
    }

    @Override
    public SFMRegistryWrapper<Chemical> getRegistry() {
        return new SFMRegistryWrapper<>(MekanismAPI.CHEMICAL_REGISTRY);
    }

    @Override
    public Chemical getItem(ResourceStack<ChemicalResource> gasStack) {
        return gasStack.resource().getChemical();
    }

    @Override
    public ResourceStack<ChemicalResource> withCount(ResourceStack<ChemicalResource> stack, long amount) {
        return new ResourceStack<>(stack.resource(), (int) Math.min(amount, Integer.MAX_VALUE));
    }
}
