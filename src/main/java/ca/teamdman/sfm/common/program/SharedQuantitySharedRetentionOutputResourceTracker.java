package ca.teamdman.sfm.common.program;

import ca.teamdman.sfm.common.resourcetype.ResourceType;
import ca.teamdman.sfml.ast.ResourceIdSet;
import ca.teamdman.sfml.ast.ResourceLimit;


public class SharedQuantitySharedRetentionOutputResourceTracker implements IOutputResourceTracker {
    private final ResourceLimit resource_limit;
    private final ResourceIdSet exclusions;
    private long transferred = 0;
    private long retention_obligation_progress = 0;

    public SharedQuantitySharedRetentionOutputResourceTracker(
            ResourceLimit resourceLimit,
            ResourceIdSet exclusions
    ) {
        this.resource_limit = resourceLimit;
        this.exclusions = exclusions;
    }

    @Override
    public ResourceLimit getResourceLimit() {
        return resource_limit;
    }

    @Override
    public ResourceIdSet getExclusions() {
        return exclusions;
    }

    /**
     * Done when we have reached the transfer limit, or when the retention is satisfied
     */
    @SuppressWarnings("RedundantIfStatement")
    @Override
    public <STACK, CAP, ITEM> boolean isDone(
            ResourceType<STACK, ITEM, CAP> type,
            STACK stack
    ) {
        long max_transfer = resource_limit.limit().quantity().number().value();
        if (transferred >= max_transfer) {
            return true;
        }
        long max_put = resource_limit.limit().retention().number().value();
        if (retention_obligation_progress >= max_put) {
            return true;
        }
        return false;
    }

    /**
     * Update obligation progress as new limited slots are prepared
     */
    @Override
    public <STACK, ITEM, CAP> void updateRetentionObservation(
            ResourceType<STACK, ITEM, CAP> type,
            STACK observed
    ) {
        if (matchesStack(observed)) {
            retention_obligation_progress += type.getAmount(observed);
        }
    }

    @Override
    public <STACK, ITEM, CAP> void trackTransfer(
            ResourceType<STACK, ITEM, CAP> resourceType,
            STACK key,
            long amount
    ) {
        transferred += amount;
        retention_obligation_progress += amount;
    }

    /**
     * How much more are we allowed to move
     */
    @Override
    public <STACK, ITEM, CAP> long getMaxTransferable(
            ResourceType<STACK, ITEM, CAP> resourceType,
            STACK key
    ) {
        long max_transfer = resource_limit.limit().quantity().number().value();
        long unusedQuantity = max_transfer - transferred;

        long max_retain = resource_limit.limit().retention().number().value();
        long remainingRetentionRoom = max_retain - retention_obligation_progress;

        return Math.min(unusedQuantity, remainingRetentionRoom);
    }

    @Override
    public String toString() {
        return "SharedQuantitySharedRetentionOutputResourceTracker@" + Integer.toHexString(System.identityHashCode(this)) + "{" +
               "TRANSFERRED=" + transferred +
               ", RETENTION_OBLIGATION_PROGRESS=" + retention_obligation_progress
               +
               ", RESOURCE_LIMIT=" + resource_limit
               +
               ", EXCLUSIONS=" + exclusions +
               "}";
    }
}
