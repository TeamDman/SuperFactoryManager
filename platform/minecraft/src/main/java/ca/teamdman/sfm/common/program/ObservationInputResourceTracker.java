package ca.teamdman.sfm.common.program;

import ca.teamdman.sfm.common.resourcetype.ResourceType;
import ca.teamdman.sfml.ast.ResourceIdSet;
import ca.teamdman.sfml.ast.ResourceLimit;
import ca.teamdman.sfml.ast.ResourceQuantity;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A non-mutating transactional view of an input resource tracker.
 *
 * <p>Queries include progress already recorded by the live tracker. New
 * hypothetical retention and transfer progress is held only by this view, so
 * an observation can apply the same limits as movement without spending the
 * live input's output budget.</p>
 */
final class ObservationInputResourceTracker implements IInputResourceTracker {
    private static final Object SHARED_GROUP = new Object();

    private final IInputResourceTracker delegate;
    private final Map<Object, Long> retainedByGroup = new HashMap<>();
    private final Map<SlotGroup, Long> retainedBySlot = new HashMap<>();
    private final Map<Object, Long> transferredByGroup = new HashMap<>();

    ObservationInputResourceTracker(IInputResourceTracker delegate) {
        this.delegate = Objects.requireNonNull(delegate);
    }

    @Override
    public ResourceLimit getResourceLimit() {
        return delegate.getResourceLimit();
    }

    @Override
    public ResourceIdSet getExclusions() {
        return delegate.getExclusions();
    }

    @Override
    public boolean matchesStack(Object stack) {
        return delegate.matchesStack(stack);
    }

    @Override
    public boolean matchesCapabilityType(Object capability) {
        return delegate.matchesCapabilityType(capability);
    }

    @Override
    public <STACK, CAP, ITEM> boolean isDone(
            ResourceType<STACK, ITEM, CAP> type,
            STACK stack
    ) {
        return delegate.isDone(type, stack) || getMaxTransferable(type, stack) <= 0;
    }

    @Override
    public <STACK, ITEM, CAP> long getRetentionObligationForSlot(
            ResourceType<STACK, ITEM, CAP> resourceType,
            STACK key,
            BlockPos pos,
            int slot
    ) {
        long existing = delegate.getRetentionObligationForSlot(resourceType, key, pos, slot);
        Object group = retentionGroup(resourceType, key);
        return existing + retainedBySlot.getOrDefault(new SlotGroup(pos, slot, group), 0L);
    }

    @Override
    public <STACK, ITEM, CAP> long getRemainingRetentionObligation(
            ResourceType<STACK, ITEM, CAP> resourceType,
            STACK key
    ) {
        long existing = delegate.getRemainingRetentionObligation(resourceType, key);
        return Math.max(0, existing - retainedByGroup.getOrDefault(retentionGroup(resourceType, key), 0L));
    }

    @Override
    public <STACK, ITEM, CAP> void trackRetentionObligation(
            ResourceType<STACK, ITEM, CAP> resourceType,
            STACK key,
            int slot,
            BlockPos pos,
            long dedicatingToObligation
    ) {
        if (dedicatingToObligation < 0) {
            throw new IllegalArgumentException("Retention observation cannot be negative");
        }
        Object group = retentionGroup(resourceType, key);
        retainedByGroup.merge(group, dedicatingToObligation, Long::sum);
        retainedBySlot.merge(new SlotGroup(pos, slot, group), dedicatingToObligation, Long::sum);
    }

    @Override
    public <STACK, ITEM, CAP> long getMaxTransferable(
            ResourceType<STACK, ITEM, CAP> resourceType,
            STACK stack
    ) {
        long existing = delegate.getMaxTransferable(resourceType, stack);
        return Math.max(0, existing - transferredByGroup.getOrDefault(quantityGroup(resourceType, stack), 0L));
    }

    @Override
    public <STACK, ITEM, CAP> void trackTransfer(
            ResourceType<STACK, ITEM, CAP> resourceType,
            STACK stack,
            long amount
    ) {
        if (amount < 0) {
            throw new IllegalArgumentException("Transfer observation cannot be negative");
        }
        transferredByGroup.merge(quantityGroup(resourceType, stack), amount, Long::sum);
    }

    private <STACK, ITEM, CAP> Object retentionGroup(
            ResourceType<STACK, ITEM, CAP> resourceType,
            STACK stack
    ) {
        return groupFor(
                getResourceLimit().limit().retention().idExpansionBehaviour(),
                resourceType,
                stack
        );
    }

    private <STACK, ITEM, CAP> Object quantityGroup(
            ResourceType<STACK, ITEM, CAP> resourceType,
            STACK stack
    ) {
        return groupFor(
                getResourceLimit().limit().quantity().idExpansionBehaviour(),
                resourceType,
                stack
        );
    }

    private static <STACK, ITEM, CAP> Object groupFor(
            ResourceQuantity.IdExpansionBehaviour expansion,
            ResourceType<STACK, ITEM, CAP> resourceType,
            STACK stack
    ) {
        if (expansion == ResourceQuantity.IdExpansionBehaviour.NO_EXPAND) {
            return SHARED_GROUP;
        }
        return new ExpandedGroup(resourceType, resourceType.getRegistryKeyForStack(stack));
    }

    private record ExpandedGroup(
            ResourceType<?, ?, ?> resourceType,
            ResourceLocation resourceId
    ) {
    }

    private record SlotGroup(
            @Nullable BlockPos position,
            int slot,
            Object group
    ) {
    }
}
