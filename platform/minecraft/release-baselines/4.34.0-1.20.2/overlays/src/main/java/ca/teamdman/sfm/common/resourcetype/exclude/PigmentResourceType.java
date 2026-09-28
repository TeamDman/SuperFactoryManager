package ca.teamdman.sfm.common.resourcetype.exclude;

import ca.teamdman.sfm.common.block.BufferBlock;
import ca.teamdman.sfm.common.blockentity.BufferBlockEntityContents;
import ca.teamdman.sfm.common.capability.SFMBlockCapabilityKind;
import ca.teamdman.sfm.common.compat.SFMMekanismCompat;
import ca.teamdman.sfm.common.registry.SFMRegistryWrapper;
import mekanism.api.Action;
import mekanism.api.MekanismAPI;
import mekanism.api.chemical.ChemicalTankBuilder;
import mekanism.api.chemical.pigment.IPigmentHandler;
import mekanism.api.chemical.pigment.Pigment;
import mekanism.api.chemical.pigment.PigmentStack;
import mekanism.common.lib.transmitter.TransmissionType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;

import java.util.stream.Stream;

public class PigmentResourceType extends RegistryBackedResourceType<PigmentStack, Pigment, IPigmentHandler> {
    public static final SFMBlockCapabilityKind<IPigmentHandler> CAP = new SFMBlockCapabilityKind<>(
            CapabilityManager.get(new CapabilityToken<>() {
            })
    );

    public PigmentResourceType() {
        super(CAP);
    }

    @Override
    public IPigmentHandler createHandlerForBufferBlock(BufferBlockEntityContents contents) {
        return (ChemicalTankBuilder.BasicPigmentTank) ChemicalTankBuilder.PIGMENT.create(
                contents.tier.getLongScalarMaxStackSize(),
                extracting -> {
                    ResourceType<?, ?, ?> resourceType = SFMMekanismCompat.getResourceType(TransmissionType.PIGMENT);
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
    public long getAmount(PigmentStack stack) {
        return stack.getAmount();
    }

    @Override
    public PigmentStack getStackInSlot(
            IPigmentHandler handler,
            int slot
    ) {
        return handler.getChemicalInTank(slot);
    }

    @Override
    public PigmentStack extract(
            IPigmentHandler handler,
            int slot,
            long amount,
            boolean simulate
    ) {
        return handler.extractChemical(slot, amount, simulate ? Action.SIMULATE : Action.EXECUTE);
    }

    @Override
    public int getSlots(IPigmentHandler handler) {
        return handler.getTanks();
    }

    @Override
    public long getMaxStackSize(PigmentStack stack) {
        return Long.MAX_VALUE;
    }

    @Override
    public long getMaxStackSizeForSlot(
            IPigmentHandler handler,
            int slot
    ) {
        return handler.getTankCapacity(slot);
    }

    @Override
    public PigmentStack insert(
            IPigmentHandler handler,
            int slot,
            PigmentStack stack,
            boolean simulate
    ) {
        return handler.insertChemical(slot, stack, simulate ? Action.SIMULATE : Action.EXECUTE);
    }

    @Override
    public boolean isEmpty(PigmentStack stack) {
        return stack.isEmpty();
    }

    @Override
    public PigmentStack getEmptyStack() {
        return PigmentStack.EMPTY;
    }

    @Override
    public boolean matchesStackType(Object o) {
        return o instanceof PigmentStack;
    }

    @Override
    public boolean matchesCapabilityHandler(Object o) {
        return o instanceof IPigmentHandler;
    }

    @Override
    public Stream<ResourceLocation> getTagsForStack(PigmentStack pigmentStack) {
        return pigmentStack.getType().getTags().map(TagKey::location);
    }


    @Override
    public SFMRegistryWrapper<Pigment> getRegistry() {
        return new SFMRegistryWrapper<>(MekanismAPI.pigmentRegistry());
    }

    @Override
    public Pigment getItem(PigmentStack stack) {
        return stack.getType();
    }

    @Override
    public PigmentStack copy(PigmentStack stack) {
        return stack.copy();
    }

    @Override
    protected PigmentStack setCount(
            PigmentStack stack,
            long amount
    ) {
        stack.setAmount(amount);
        return stack;
    }
}
