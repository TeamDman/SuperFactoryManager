package ca.teamdman.sfm.common.resourcetype;

import mekanism.api.Action;
import mekanism.api.MekanismAPI;
import mekanism.api.chemical.Chemical;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalHandler;
import mekanism.common.capabilities.Capabilities;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.neoforged.neoforge.capabilities.BlockCapability;
import org.jetbrains.annotations.Nullable;

import java.util.stream.Stream;

public class ChemicalResourceType extends ResourceType<ChemicalStack, Chemical, IChemicalHandler> {
    public static final BlockCapability<IChemicalHandler, @Nullable Direction> CAP = Capabilities.CHEMICAL.block();

    public ChemicalResourceType() {
        super(CAP);
    }

    @Override
    public long getAmount(ChemicalStack ChemicalStack) {
        return ChemicalStack.getAmount();
    }

    @Override
    public ChemicalStack getStackInSlot(IChemicalHandler IChemicalHandler, int slot) {
        return IChemicalHandler.getChemicalInTank(slot);
    }

    @Override
    public Stream<ResourceLocation> getTagsForStack(ChemicalStack ChemicalStack) {
        return ChemicalStack.getChemical().getTags().map(TagKey::location);
    }

    @Override
    public ChemicalStack extract(IChemicalHandler handler, int slot, long amount, boolean simulate) {
        return handler.extractChemical(slot, amount, simulate ? Action.SIMULATE : Action.EXECUTE);
    }

    @Override
    public int getSlots(IChemicalHandler handler) {
        return handler.getChemicalTanks();
    }

    @Override
    public long getMaxStackSize(ChemicalStack ChemicalStack) {
        return Long.MAX_VALUE;
    }

    @Override
    public long getMaxStackSizeForSlot(IChemicalHandler handler, int slot) {
        return handler.getChemicalTankCapacity(slot);
    }

    @Override
    public ChemicalStack insert(IChemicalHandler handler, int slot, ChemicalStack ChemicalStack, boolean simulate) {
        return handler.insertChemical(slot, ChemicalStack, simulate ? Action.SIMULATE : Action.EXECUTE);
    }

    @Override
    public boolean isEmpty(ChemicalStack ChemicalStack) {
        return ChemicalStack.isEmpty();
    }

    @Override
    public ChemicalStack getEmptyStack() {
        return ChemicalStack.EMPTY;
    }

    @Override
    public boolean matchesStackType(Object o) {
        return o instanceof ChemicalStack;
    }

    @Override
    public boolean matchesCapabilityType(Object o) {
        return o instanceof IChemicalHandler;
    }


    @Override
    public Registry<Chemical> getRegistry() {
        return MekanismAPI.CHEMICAL_REGISTRY;
    }

    @Override
    public Chemical getItem(ChemicalStack ChemicalStack) {
        return ChemicalStack.getChemical();
    }

    @Override
    public ChemicalStack copy(ChemicalStack ChemicalStack) {
        return ChemicalStack.copy();
    }

    @Override
    protected ChemicalStack setCount(ChemicalStack ChemicalStack, long amount) {
        ChemicalStack.setAmount(amount);
        return ChemicalStack;
    }
}
