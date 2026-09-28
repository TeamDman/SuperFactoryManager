package ca.teamdman.sfm.common.resourcetype.exclude;

import ca.teamdman.sfm.common.block.BufferBlock;
import ca.teamdman.sfm.common.blockentity.BufferBlockEntityContents;
import ca.teamdman.sfm.common.capability.SFMBlockCapabilityKind;
import ca.teamdman.sfm.common.util.SFMResourceLocation;
import mekanism.api.Action;
import mekanism.api.energy.IEnergyContainer;
import mekanism.api.energy.IMekanismStrictEnergyHandler;
import mekanism.api.energy.IStrictEnergyHandler;
import mekanism.api.math.FloatingLong;
import mekanism.common.capabilities.energy.BasicEnergyContainer;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.stream.Stream;

public class MekanismEnergyResourceType extends ScalarResourceType<FloatingLong, IStrictEnergyHandler> {
    public static final SFMBlockCapabilityKind<IStrictEnergyHandler> CAP = new SFMBlockCapabilityKind<>(
            CapabilityManager.get(new CapabilityToken<>() {
            })
    );

    public MekanismEnergyResourceType() {
        super(CAP, SFMResourceLocation.fromNamespaceAndPath("mekanism", "energy"), FloatingLong.class);
    }

    @Override
    public IStrictEnergyHandler createHandlerForBufferBlock(BufferBlockEntityContents contents) {
        return new IMekanismStrictEnergyHandler() {
            private final List<IEnergyContainer> containers = List.of(BasicEnergyContainer.create(
                    FloatingLong.create(contents.tier.getLongScalarMaxStackSize()),
                    null
            ));

            @Override
            public List<IEnergyContainer> getEnergyContainers(@Nullable Direction direction) {
                return containers;
            }

            @Override
            public FloatingLong insertEnergy(
                    int container,
                    FloatingLong amount,
                    @Nullable Direction side,
                    Action action
            ) {
                boolean canReceive = !this.getEnergy(0).isZero() || contents.isEmpty();
                if (!canReceive) {
                    return amount;
                }
                if (action.execute() && contents.isEmpty()) {
                    contents.lastUsedResource = BufferBlock.ContainedResource.Energy;
                }
                return IMekanismStrictEnergyHandler.super.insertEnergy(container, amount, side, action);
            }

            @Override
            public void onContentsChanged() {}
        };
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
        // Note that mekanism returns the remainder, while forge IEnergyStorage returns the accepted amount.
        // This is fine because ResourceType expects the return value from insert to be the remainder.
        //noinspection UnnecessaryLocalVariable
        FloatingLong remainder = storage.insertEnergy(amount, simulate ? Action.SIMULATE : Action.EXECUTE);
        return remainder;
    }

    @Override
    public boolean isEmpty(FloatingLong stack) {
        return stack.isZero();
    }

    @Override
    public boolean matchesCapabilityHandler(@NotNull Object o) {
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
    public FloatingLong copy(FloatingLong stack) {
        return stack;
    }

    @Override
    public FloatingLong withCount(
            FloatingLong floatingLong,
            long count
    ) {
        return FloatingLong.create(count);
    }

    @Override
    protected FloatingLong setCount(
            FloatingLong stack,
            long amount
    ) {
        return FloatingLong.create(amount);
    }
}
