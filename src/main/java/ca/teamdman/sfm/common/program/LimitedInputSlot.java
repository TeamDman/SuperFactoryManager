package ca.teamdman.sfm.common.program;

import ca.teamdman.sfm.common.resourcetype.ResourceType;

import javax.annotation.Nullable;

public class LimitedInputSlot<STACK, ITEM, CAP> {
    @SuppressWarnings("NotNullFieldNotInitialized") // done in init method in constructor
    public ResourceType<STACK, ITEM, CAP> type;
    @SuppressWarnings("NotNullFieldNotInitialized") // done in init method in constructor
    public CAP handler;
    public int slot;
    @SuppressWarnings("NotNullFieldNotInitialized") // done in init method in constructor
    public InputResourceTracker<STACK, ITEM, CAP> tracker;
    private @Nullable STACK extractSimulateCache = null;
    private boolean done = false;

    public LimitedInputSlot(
            CAP handler, int slot, InputResourceTracker<STACK, ITEM, CAP> tracker, STACK stack
    ) {
        this.init(handler, slot, tracker, stack);
    }

    public boolean isDone() {
        if (done) return true;
        // we don't bother setting this.done because if this returns true it should be the last time this is called
        if (tracker.isDone()) {
            return true;
        }
        if (slot > type.getSlots(handler) - 1) {
            // composter block changes how many slots it has between insertions
            return true;
        }
        STACK stack = peekExtractPotential();
        if (type.isEmpty(stack)) {
            return true;
        }
        return !tracker.test(stack);
    }

    public void setDone() {
        this.done = true;
    }

    public STACK extract(long amount) {
        extractSimulateCache = null;
        return type.extract(handler, slot, amount, false);
    }

    /**
     * Checks how much could possibly be extracted from this slot.
     * We need to simulate since there are some types of slots we can't undo an extract from.
     * You can't put something back in the output slot of a furnace.
     * This value is cached for performance.
     */
    public STACK peekExtractPotential() {
        if (extractSimulateCache == null) {
            extractSimulateCache = type.extract(handler, slot, Long.MAX_VALUE, true);
        }
        return extractSimulateCache;
    }

    public void init(CAP handler, int slot, InputResourceTracker<STACK, ITEM, CAP> tracker, STACK stack) {
        this.done = false;
        this.extractSimulateCache = stack;
        this.handler = handler;
        this.tracker = tracker;
        this.slot = slot;
        //noinspection DataFlowIssue
        this.type = tracker.getResourceLimit().resourceId().getResourceType();
        if (type == null) {
            throw new NullPointerException("type");
        }
    }

    @Override
    public String toString() {
        return "LimitedInputSlot{" +
               "slot=" + slot +
               ", cap=" + type.CAPABILITY_KIND.name() +
//               ", extractSimulateCache=" + extractSimulateCache +
               ", tracker=" + tracker +
               '}';
    }
}
