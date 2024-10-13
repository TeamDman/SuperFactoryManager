package ca.teamdman.sfm.common.resourcetype;

import mekanism.api.Action;
import mekanism.api.chemical.infuse.IInfusionHandler;
import mekanism.api.energy.IStrictEnergyHandler;
import mekanism.api.math.FloatingLong;
import mekanism.common.capabilities.Capabilities;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.capabilities.BlockCapability;
import org.apache.commons.lang3.NotImplementedException;
import org.jetbrains.annotations.Nullable;

import java.util.stream.Stream;

public class MekanismEnergyResourceType extends ResourceType<FloatingLong, Class<FloatingLong>, IStrictEnergyHandler> {
    public static final BlockCapability<IStrictEnergyHandler, @Nullable Direction> CAP = Capabilities.STRICT_ENERGY.block();

    public static final ResourceLocation REGISTRY_KEY = new ResourceLocation("mekanism", "energy");

    public MekanismEnergyResourceType() {
        super(CAP);
    }

    @Override
    public long getAmount(FloatingLong amount) {
        return amount.longValue();
    }

    @Override
    public long getAmountDifference(
            FloatingLong stack1,
            FloatingLong stack2
    ) {
        return stack1.subtract(stack2).longValue();
    }

    @Override
    public FloatingLong getStackInSlot(
            IStrictEnergyHandler storage,
            int slot
    ) {
        return storage.getEnergy(slot);
    }

    @Override
    public FloatingLong extract(
            IStrictEnergyHandler storage,
            int slot,
            long amount,
            boolean simulate
    ) {
        return storage.extractEnergy(FloatingLong.create(amount), simulate ? Action.SIMULATE : Action.EXECUTE);
    }

    @Override
    public int getSlots(IStrictEnergyHandler handler) {
        return 1;
    }

    @Override
    public long getMaxStackSize(FloatingLong stack) {
        return Long.MAX_VALUE;
    }

    @Override
    public long getMaxStackSizeForSlot(
            IStrictEnergyHandler storage,
            int slot
    ) {
        return storage.getMaxEnergy(slot).longValue();
    }

    @Override
    public FloatingLong insert(
            IStrictEnergyHandler storage,
            int slot,
            FloatingLong amount,
            boolean simulate
    ) {
        // note that mekanism returns the remainder, while forge IEnergyStorage returns the accepted amount
        //noinspection UnnecessaryLocalVariable
        FloatingLong remainder = storage.insertEnergy(amount, simulate ? Action.SIMULATE : Action.EXECUTE);
        return remainder;
    }

    @Override
    public boolean isEmpty(FloatingLong stack) {
        return stack.isZero();
    }

    @Override
    public boolean matchesStackType(Object o) {
        return o instanceof FloatingLong;
    }

    @Override
    public boolean matchesCapabilityType(Object o) {
        return o instanceof IStrictEnergyHandler;
    }

    @Override
    public Stream<ResourceLocation> getTagsForStack(FloatingLong floatingLong) {
        return Stream.empty();
    }

    @Override
    public FloatingLong getEmptyStack() {
        return FloatingLong.ZERO;
    }

    @Override
    public ResourceLocation getRegistryKey(FloatingLong stack) {
        return REGISTRY_KEY;
    }

    @Override
    public Registry<Class<FloatingLong>> getRegistry() {
        throw new NotImplementedException();
    }

    @Override
    public boolean registryKeyExists(ResourceLocation location) {
        return location.equals(REGISTRY_KEY);
    }

    @Override
    public Class<FloatingLong> getItem(FloatingLong stack) {
        return FloatingLong.class;
    }

    @Override
    public FloatingLong copy(FloatingLong stack) {
        return stack;
    }

    @Override
    protected FloatingLong setCount(
            FloatingLong stack,
            long amount
    ) {
        return FloatingLong.create(amount);
    }
}
