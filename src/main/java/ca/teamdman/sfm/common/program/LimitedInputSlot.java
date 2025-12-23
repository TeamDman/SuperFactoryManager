package ca.teamdman.sfm.common.program;

import ca.teamdman.sfm.common.resourcetype.ResourceTypeContainer.ResourceType;
import ca.teamdman.sfml.ast.Label;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.EnumFacing;
import org.jetbrains.annotations.Nullable;

public class LimitedInputSlot<STACK, ITEM, CAP> implements LimitedSlot<STACK, ITEM, CAP> {
    @SuppressWarnings("NotNullFieldNotInitialized") // done in init method in constructor
    public ResourceType<STACK, ITEM, CAP> type;
    @SuppressWarnings("NotNullFieldNotInitialized") // done in init method in constructor
    public CAP handler;
    @SuppressWarnings("NotNullFieldNotInitialized") // done in init method in constructor
    public BlockPos pos;
    @SuppressWarnings("NotNullFieldNotInitialized") // done in init method in constructor
    public Label label;
    @SuppressWarnings("NotNullFieldNotInitialized") // done in init method in constructor
    public EnumFacing direction;
    public int slot;
    public boolean freed;
    @SuppressWarnings("NotNullFieldNotInitialized") // done in init method in constructor
    public IInputResourceTracker tracker;
    private @Nullable STACK extractSimulateCache = null;
    private boolean done = false;

    public LimitedInputSlot(
            Label label,
            BlockPos pos,
            EnumFacing direction,
            int slot,
            CAP handler,
            IInputResourceTracker tracker,
            STACK stackCache,
            ResourceType<STACK, ITEM, CAP> type
    ) {
        this.init(handler, label, pos, direction, slot, tracker, stackCache, type);
    }

    @SuppressWarnings("RedundantIfStatement")
    public boolean isDone() {
        if (done) return true;
        // we don't bother setting this.done because if this returns true it should be the last time this is called

        if (slot > type.getSlots(handler) - 1) {
            // composter block changes how many slots it has between insertions
            return true;
        }
        STACK stack = peekExtractPotential();
        if (type.isEmpty(stack)) {
            return true;
        }
        if (!tracker.matchesStack(stack)) {
            return true;
        }
        if (tracker.isDone(type, stack)) {
            return true;
        }
        return false;
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
            // We use getStackInSlot because it can return values greater than max-stack-size
            // For example, a dank storage dock can have 256 items in a slot but if we queried extraction it would say 64
            extractSimulateCache = type.getStackInSlot(handler, slot);
//            extractSimulateCache = type.extract(handler, slot, Long.MAX_VALUE, true);
        }
        return extractSimulateCache;
    }

    @SuppressWarnings("DuplicatedCode")
    public void init(
            CAP handler,
            Label label,
            BlockPos pos,
            EnumFacing direction,
            int slot,
            IInputResourceTracker tracker,
            STACK stackCache,
            ResourceType<STACK, ITEM, CAP> type
    ) {
        this.done = false;
        this.extractSimulateCache = stackCache;
        this.handler = handler;
        this.tracker = tracker;
        this.slot = slot;
        this.pos = pos;
        this.label = label;
        this.direction = direction;
        this.freed = false;
        this.type = type;
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


    @Override
    public ResourceType<STACK, ITEM, CAP> getType() {
        return type;
    }

    @Override
    public CAP getHandler() {
        return handler;
    }

    @Override
    public BlockPos getPos() {
        return pos;
    }

    @Override
    public Label getLabel() {
        return label;
    }

    @Override
    public EnumFacing getDirection() {
        return direction;
    }

    @Override
    public int getSlot() {
        return slot;
    }
}
