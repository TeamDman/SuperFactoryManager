package ca.teamdman.sfm.common.resourcetype;

import ca.teamdman.sfm.common.block.BufferBlock;
import ca.teamdman.sfm.common.blockentity.BufferBlockEntityContents;
import ca.teamdman.sfm.common.capability.SFMBlockCapabilityKind;
import ca.teamdman.sfm.common.compat.SFMMekanismCompat;
import ca.teamdman.sfm.common.registry.SFMRegistryWrapper;
import mekanism.api.Action;
import mekanism.api.MekanismAPI;
import mekanism.api.chemical.ChemicalTankBuilder;
import mekanism.api.chemical.gas.Gas;
import mekanism.api.chemical.gas.GasStack;
import mekanism.api.chemical.gas.IGasHandler;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.lib.transmitter.TransmissionType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;

import java.util.stream.Stream;

public class GasResourceType extends RegistryBackedResourceType<GasStack, Gas, IGasHandler> {
    public static final SFMBlockCapabilityKind<IGasHandler> CAP = new SFMBlockCapabilityKind<>(
            Capabilities.GAS.block()
    );


    public GasResourceType() {
        super(CAP);
    }

    @Override
    public IGasHandler createHandlerForBufferBlock(BufferBlockEntityContents contents) {
        return (ChemicalTankBuilder.BasicGasTank) ChemicalTankBuilder.GAS.create(
                contents.tier.getIntScalarMaxStackSize(),
                extracting -> {
                    ResourceType<?, ?, ?> resourceType = SFMMekanismCompat.getResourceType(TransmissionType.GAS);
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
    public long getAmount(GasStack gasStack) {
        return gasStack.getAmount();
    }

    @Override
    public GasStack getStackInSlot(
            IGasHandler iGasHandler,
            int slot
    ) {
        return iGasHandler.getChemicalInTank(slot);
    }

    @Override
    public Stream<ResourceLocation> getTagsForStack(GasStack gasStack) {
        return gasStack.getChemical().getTags().map(TagKey::location);
    }

    @Override
    public GasStack extract(
            IGasHandler handler,
            int slot,
            long amount,
            boolean simulate
    ) {
        return handler.extractChemical(slot, amount, simulate ? Action.SIMULATE : Action.EXECUTE);
    }

    @Override
    public int getSlots(IGasHandler handler) {
        return handler.getTanks();
    }

    @Override
    public long getMaxStackSize(GasStack gasStack) {
        return Long.MAX_VALUE;
    }

    @Override
    public long getMaxStackSizeForSlot(
            IGasHandler handler,
            int slot
    ) {
        return handler.getTankCapacity(slot);
    }

    @Override
    public GasStack insert(
            IGasHandler handler,
            int slot,
            GasStack gasStack,
            boolean simulate
    ) {
        return handler.insertChemical(slot, gasStack, simulate ? Action.SIMULATE : Action.EXECUTE);
    }

    @Override
    public boolean isEmpty(GasStack gasStack) {
        return gasStack.isEmpty();
    }

    @Override
    public GasStack getEmptyStack() {
        return GasStack.EMPTY;
    }

    @Override
    public boolean matchesStackType(Object o) {
        return o instanceof GasStack;
    }

    @Override
    public boolean matchesCapabilityHandler(Object o) {
        return o instanceof IGasHandler;
    }


    @Override
    public SFMRegistryWrapper<Gas> getRegistry() {
        return new SFMRegistryWrapper<>(MekanismAPI.GAS_REGISTRY);
    }

    @Override
    public Gas getItem(GasStack gasStack) {
        return gasStack.getChemical();
    }

    @Override
    public GasStack copy(GasStack gasStack) {
        return gasStack.copy();
    }

    @Override
    protected GasStack setCount(
            GasStack gasStack,
            long amount
    ) {
        gasStack.setAmount(amount);
        return gasStack;
    }
}