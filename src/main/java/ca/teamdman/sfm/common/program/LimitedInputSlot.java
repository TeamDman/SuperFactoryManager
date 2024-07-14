package ca.teamdman.sfm.common.program;

import ca.teamdman.sfm.common.resourcetype.ResourceType;
import ca.teamdman.sfml.ast.Label;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import javax.annotation.Nullable;

public class LimitedInputSlot<STACK, ITEM, CAP> {
    @SuppressWarnings("NotNullFieldNotInitialized") // done in init method in constructor
    public ResourceType<STACK, ITEM, CAP> type;
    @SuppressWarnings("NotNullFieldNotInitialized") // done in init method in constructor
    public CAP handler;
    @SuppressWarnings("NotNullFieldNotInitialized") // done in init method in constructor
    public BlockPos pos;
    @SuppressWarnings("NotNullFieldNotInitialized") // done in init method in constructor
    public Label label;
    @SuppressWarnings("NotNullFieldNotInitialized") // done in init method in constructor
    public Direction direction;
    public int slot;
    public boolean freed;
    @SuppressWarnings("NotNullFieldNotInitialized") // done in init method in constructor
    public InputResourceTracker<STACK, ITEM, CAP> tracker;
    private @Nullable STACK extractSimulateCache = null;
    private boolean done = false;

    public LimitedInputSlot(
            Label label,
            BlockPos pos,
            Direction direction,
            int slot,
            CAP handler,
            InputResourceTracker<STACK, ITEM, CAP> tracker,
            STACK stack
    ) {
        this.init(handler, label, pos, direction, slot, tracker, stack);
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

    public void init(
            CAP handler,
            Label label,
            BlockPos pos,
            Direction direction,
            int slot,
            InputResourceTracker<STACK, ITEM, CAP> tracker,
            STACK stack
    ) {
        this.done = false;
        this.extractSimulateCache = stack;

        this.handler = handler;
        this.tracker = tracker;
        this.slot = slot;
        this.pos = pos;
        this.label = label;
        this.direction = direction;
        this.freed = false;

        //noinspection DataFlowIssue
        this.type = tracker.getResourceLimit().resourceId().getResourceType();
        if (type == null) {
            throw new NullPointerException("type");
        }
    }

    @Override
    public String toString() {
        return "LimitedInputSlot{"
               + "label=" + label
               + ", pos=" + pos
               + ", direction=" + direction
               + ", slot=" + slot
               + ", cap=" + type.displayAsCapabilityClass()
               + ", tracker=" + tracker
               + '}';
    }
}
