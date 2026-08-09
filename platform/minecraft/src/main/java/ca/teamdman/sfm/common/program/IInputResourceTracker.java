package ca.teamdman.sfm.common.program;

import ca.teamdman.sfm.common.resourcetype.ResourceType;
import ca.teamdman.sfml.ast.ResourceIdSet;
import ca.teamdman.sfml.ast.ResourceLimit;
import net.minecraft.core.BlockPos;

public interface IInputResourceTracker {
    ResourceLimit getResourceLimit();

    ResourceIdSet getExclusions();

    <STACK, CAP, ITEM> boolean isDone(
            ResourceType<STACK, ITEM, CAP> type,
            STACK stack
    );

    <STACK, ITEM, CAP> long getRetentionObligationForSlot(
            ResourceType<STACK, ITEM, CAP> resourceType,
            STACK key,
            BlockPos pos,
            int slot
    );

    <STACK, ITEM, CAP> long getRemainingRetentionObligation(
            ResourceType<STACK, ITEM, CAP> resourceType,
            STACK key
    );

    <STACK, ITEM, CAP> void trackRetentionObligation(
            ResourceType<STACK, ITEM, CAP> resourceType,
            STACK key,
            int slot,
            BlockPos pos,
            long dedicatingToObligation
    );

    /**
     * Get the maximum amount of a resource that can be transferred.
     * This does not account for any retention obligations on the input side, since that is accounted for using the slot-level checks in moveTo.
     */
    <STACK, ITEM, CAP> long getMaxTransferable(
            ResourceType<STACK, ITEM, CAP> resourceType,
            STACK stack
    );

    <STACK, ITEM, CAP> void trackTransfer(
            ResourceType<STACK, ITEM, CAP> resourceType,
            STACK stack,
            long amount
    );

    default boolean matchesStack(Object stack) {
        return getResourceLimit().matchesStack(stack) && getExclusions().noneMatchStack(stack);
    }

    default boolean matchesCapabilityType(Object capability) {
        for (ResourceType<?, ?, ?> resourceType : getResourceLimit().resourceIds().getReferencedResourceTypes()) {
            if (resourceType.matchesCapabilityHandler(capability)) {
                return true;
            }
        }
        return false;
    }
}
