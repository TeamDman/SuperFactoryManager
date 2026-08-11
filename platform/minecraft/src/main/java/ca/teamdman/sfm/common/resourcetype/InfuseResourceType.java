package ca.teamdman.sfm.common.resourcetype;

import ca.teamdman.sfm.common.block.BufferBlock;
import ca.teamdman.sfm.common.blockentity.BufferBlockEntityContents;
import ca.teamdman.sfm.common.capability.SFMBlockCapabilityKind;
import ca.teamdman.sfm.common.compat.SFMMekanismCompat;
import ca.teamdman.sfm.common.registry.SFMRegistryWrapper;
import mekanism.api.Action;
import mekanism.api.MekanismAPI;
import mekanism.api.chemical.ChemicalTankBuilder;
import mekanism.api.chemical.infuse.IInfusionHandler;
import mekanism.api.chemical.infuse.InfuseType;
import mekanism.api.chemical.infuse.InfusionStack;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.lib.transmitter.TransmissionType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;

import java.util.stream.Stream;

public class InfuseResourceType extends RegistryBackedResourceType<InfusionStack, InfuseType, IInfusionHandler> {
    public static final SFMBlockCapabilityKind<IInfusionHandler> CAP = new SFMBlockCapabilityKind<>(
            Capabilities.INFUSION.block()
    );

    public InfuseResourceType() {
        super(CAP);
    }

    @Override
    public IInfusionHandler createHandlerForBufferBlock(BufferBlockEntityContents contents) {
        return (ChemicalTankBuilder.BasicInfusionTank) ChemicalTankBuilder.INFUSION.create(
                contents.tier.getIntScalarMaxStackSize(),
                extracting -> {
                    ResourceType<?, ?, ?> resourceType = SFMMekanismCompat.getResourceType(TransmissionType.INFUSION);
                    boolean isValid = resourceType != null && contents.allowInsertion(resourceType);
                    if (isValid) {
                        contents.lastUsedResource = BufferBlock.ContainedResource.Chemical;
                    }
                    return isValid;
                },
                null
        );
    }

    @Override
    public long getAmount(InfusionStack stack) {
        return stack.getAmount();
    }

    @Override
    public InfusionStack getStackInSlot(
            IInfusionHandler handler,
            int slot
    ) {
        return handler.getChemicalInTank(slot);
    }

    @Override
    public InfusionStack extract(
            IInfusionHandler handler,
            int slot,
            long amount,
            boolean simulate
    ) {
        return handler.extractChemical(slot, amount, simulate ? Action.SIMULATE : Action.EXECUTE);
    }

    @Override
    public int getSlots(IInfusionHandler handler) {
        return handler.getTanks();
    }

    @Override
    public long getMaxStackSize(InfusionStack stack) {
        return Long.MAX_VALUE;
    }

    @Override
    public long getMaxStackSizeForSlot(
            IInfusionHandler handler,
            int slot
    ) {
        return handler.getTankCapacity(slot);
    }

    @Override
    public InfusionStack insert(
            IInfusionHandler handler,
            int slot,
            InfusionStack stack,
            boolean simulate
    ) {
        return handler.insertChemical(slot, stack, simulate ? Action.SIMULATE : Action.EXECUTE);
    }

    @Override
    public boolean isEmpty(InfusionStack stack) {
        return stack.isEmpty();
    }

    @Override
    public InfusionStack getEmptyStack() {
        return InfusionStack.EMPTY;
    }

    @Override
    public boolean matchesStackType(Object o) {
        return o instanceof InfusionStack;
    }

    @Override
    public boolean matchesCapabilityHandler(Object o) {
        return o instanceof IInfusionHandler;
    }

    @Override
    public Stream<ResourceLocation> getTagsForStack(InfusionStack infusionStack) {
        return infusionStack.getChemical().getTags().map(TagKey::location);
    }


    @Override
    public SFMRegistryWrapper<InfuseType> getRegistry() {
        return new SFMRegistryWrapper<>(MekanismAPI.INFUSE_TYPE_REGISTRY);
    }

    @Override
    public InfuseType getItem(InfusionStack stack) {
        return stack.getChemical();
    }

    @Override
    public InfusionStack copy(InfusionStack stack) {
        return stack.copy();
    }

    @Override
    protected InfusionStack setCount(
            InfusionStack stack,
            long amount
    ) {
        stack.setAmount(amount);
        return stack;
    }
}
