package ca.teamdman.sfm.common.program;

import ca.teamdman.sfm.common.resourcetype.ResourceType;
import ca.teamdman.sfml.ast.ResourceIdSet;
import ca.teamdman.sfml.ast.ResourceLimit;

public interface IOutputResourceTracker {
    ResourceLimit getResourceLimit();

    ResourceIdSet getExclusions();

    <STACK, CAP, ITEM> boolean isDone(
            ResourceType<STACK, ITEM, CAP> type,
            STACK stack
    );

    <STACK, ITEM, CAP> void updateRetentionObservation(
            ResourceType<STACK, ITEM, CAP> type,
            STACK observed
    );

    <STACK, ITEM, CAP> void trackTransfer(
            ResourceType<STACK, ITEM, CAP> resourceType,
            STACK key,
            long amount
    );

    <STACK, ITEM, CAP> long getMaxTransferable(
            ResourceType<STACK, ITEM, CAP> resourceType,
            STACK key
    );

    default boolean matchesCapabilityType(Object capability) {
        for (ResourceType<?, ?, ?> resourceType : getResourceLimit().resourceIds().getReferencedResourceTypes()) {
            if (resourceType.matchesCapabilityHandler(capability)) {
                return true;
            }
        }
        return false;
    }

    default boolean matchesStack(Object stack) {
        return getResourceLimit().matchesStack(stack) && getExclusions().noneMatchStack(stack);
    }
}
